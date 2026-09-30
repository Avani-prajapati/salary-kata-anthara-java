import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
  TestRequest,
} from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { MatInputHarness } from '@angular/material/input/testing';
import { MatPaginatorHarness } from '@angular/material/paginator/testing';
import { MatSelectHarness } from '@angular/material/select/testing';
import { MatSortHarness } from '@angular/material/sort/testing';
import { MatTableHarness } from '@angular/material/table/testing';
import { EmployeeSummary } from '../../core/models/employee.model';
import { PagedResponse } from '../../core/models/paged-response.model';
import { EmployeeDirectoryPageComponent } from './employee-directory-page.component';

const JANE_ITO: EmployeeSummary = {
  id: 7,
  name: 'Jane Ito',
  department: 'ENGINEERING',
  jobLevel: 'SENIOR',
  countryCode: 'JP',
  salaryAmount: 9800000,
  salaryCurrency: 'JPY',
};

const BEN_MILLER: EmployeeSummary = {
  id: 8,
  name: 'Ben Miller',
  department: 'HUMAN_RESOURCES',
  jobLevel: 'MID',
  countryCode: 'US',
  salaryAmount: 105000.5,
  salaryCurrency: 'USD',
};

function pageOf(
  items: EmployeeSummary[],
  totalElements = items.length,
): PagedResponse<EmployeeSummary> {
  return { items, page: 0, pageSize: 25, totalElements, totalPages: Math.ceil(totalElements / 25) };
}

describe('employee directory page', () => {
  let fixture: ComponentFixture<EmployeeDirectoryPageComponent>;
  let httpTesting: HttpTestingController;
  let loader: HarnessLoader;

  beforeEach(async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'Date'] });
    TestBed.configureTestingModule({
      imports: [EmployeeDirectoryPageComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(EmployeeDirectoryPageComponent);
    loader = TestbedHarnessEnvironment.loader(fixture);
    await fixture.whenStable();
  });

  afterEach(() => {
    httpTesting.verify();
    vi.useRealTimers();
  });

  function pendingSearch(): TestRequest {
    return httpTesting.expectOne((request) => request.url === '/api/employees');
  }

  async function respondWith(page: PagedResponse<EmployeeSummary>): Promise<void> {
    pendingSearch().flush(page);
    await fixture.whenStable();
  }

  async function searchAfterFiltersSettle(): Promise<TestRequest> {
    vi.advanceTimersByTime(300);
    await fixture.whenStable();
    return pendingSearch();
  }

  function text(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  async function renderedRows(): Promise<string[][]> {
    return (await loader.getHarness(MatTableHarness)).getCellTextByIndex();
  }

  it('requests the first page of twenty-five employees sorted by name on load', () => {
    const request = pendingSearch();

    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe('25');
    expect(request.request.params.get('sort')).toBe('name,asc');
    request.flush(pageOf([]));
  });

  it('shows each employee with department, job level, country and salary in its currency', async () => {
    await respondWith(pageOf([JANE_ITO, BEN_MILLER]));

    expect(await renderedRows()).toEqual([
      ['Jane Ito', 'Engineering', 'Senior', 'Japan', '¥9,800,000'],
      ['Ben Miller', 'Human resources', 'Mid', 'United States', '$105,000.5'],
    ]);
  });

  it('shows a clear "no employees match" message when the search finds nobody', async () => {
    await respondWith(pageOf([]));

    expect(text()).toContain('No employees match your search or filters.');
    expect(await renderedRows()).toEqual([]);
  });

  it('searches by the typed name once typing pauses, starting again from the first page', async () => {
    await respondWith(pageOf([JANE_ITO], 400));
    const paginator = await loader.getHarness(MatPaginatorHarness);
    await paginator.goToNextPage();
    pendingSearch().flush(pageOf([JANE_ITO], 400));

    const nameSearch = await loader.getHarness(
      MatInputHarness.with({ selector: '[formControlName="name"]' }),
    );
    await nameSearch.setValue('  ito ');
    const request = await searchAfterFiltersSettle();

    expect(request.request.params.get('name')).toBe('ito');
    expect(request.request.params.get('page')).toBe('0');
    request.flush(pageOf([JANE_ITO]));
  });

  it('combines department, country and job level filters in one search', async () => {
    await respondWith(pageOf([JANE_ITO]));

    await (
      await loader.getHarness(MatSelectHarness.with({ selector: '[formControlName="department"]' }))
    ).clickOptions({ text: 'Engineering' });
    await (
      await loader.getHarness(
        MatSelectHarness.with({ selector: '[formControlName="countryCode"]' }),
      )
    ).clickOptions({ text: 'Japan' });
    await (
      await loader.getHarness(MatSelectHarness.with({ selector: '[formControlName="jobLevel"]' }))
    ).clickOptions({ text: 'Senior' });
    const request = await searchAfterFiltersSettle();

    expect(request.request.params.get('department')).toBe('ENGINEERING');
    expect(request.request.params.get('countryCode')).toBe('JP');
    expect(request.request.params.get('jobLevel')).toBe('SENIOR');
    expect(request.request.params.has('name')).toBe(false);
    request.flush(pageOf([JANE_ITO]));
  });

  it('sorts by salary ascending, then descending, when the salary header is activated twice', async () => {
    await respondWith(pageOf([JANE_ITO]));
    const salaryHeader = (
      await (await loader.getHarness(MatSortHarness)).getSortHeaders({ label: 'Salary' })
    )[0];

    await salaryHeader.click();
    const ascending = pendingSearch();
    ascending.flush(pageOf([JANE_ITO]));
    await salaryHeader.click();
    const descending = pendingSearch();
    descending.flush(pageOf([JANE_ITO]));

    expect(ascending.request.params.get('sort')).toBe('salaryAmount,asc');
    expect(descending.request.params.get('sort')).toBe('salaryAmount,desc');
  });

  it('moves to the next page and changes page size from the paginator', async () => {
    await respondWith(pageOf([JANE_ITO], 400));
    const paginator = await loader.getHarness(MatPaginatorHarness);

    await paginator.goToNextPage();
    const nextPage = pendingSearch();
    nextPage.flush(pageOf([JANE_ITO], 400));
    await paginator.setPageSize(50);
    const resized = pendingSearch();
    resized.flush(pageOf([JANE_ITO], 400));

    expect(nextPage.request.params.get('page')).toBe('1');
    expect(resized.request.params.get('size')).toBe('50');
  });

  it('keeps every sort header and enabled paginator control in the keyboard tab order', async () => {
    await respondWith(pageOf([JANE_ITO], 400));
    const element = fixture.nativeElement as HTMLElement;

    const sortHeaders = Array.from(
      element.querySelectorAll<HTMLElement>('.mat-sort-header-container'),
    );
    const enabledPageButtons = Array.from(
      element.querySelectorAll<HTMLButtonElement>(
        'mat-paginator button:not([disabled]):not([aria-disabled="true"])',
      ),
    );

    expect(sortHeaders.map((header) => header.tabIndex)).toEqual([0, 0, 0, 0, 0]);
    expect(enabledPageButtons.length).toBeGreaterThan(0);
    expect(enabledPageButtons.filter((button) => button.tabIndex < 0)).toEqual([]);
  });

  it('sorts from the keyboard when Enter is pressed on a focused sort header', async () => {
    await respondWith(pageOf([JANE_ITO]));
    const countryHeader = (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>(
      '.mat-column-countryCode .mat-sort-header-container',
    );

    countryHeader?.focus();
    countryHeader?.dispatchEvent(
      new KeyboardEvent('keydown', { key: 'Enter', keyCode: 13, bubbles: true }),
    );
    await fixture.whenStable();
    const request = pendingSearch();
    request.flush(pageOf([JANE_ITO]));

    expect(document.activeElement).toBe(countryHeader);
    expect(request.request.params.get('sort')).toBe('countryCode,asc');
  });

  it('labels every filter control for assistive technology', () => {
    pendingSearch().flush(pageOf([]));
    const labels = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('mat-label'),
      (label) => label.textContent?.trim(),
    );

    expect(labels).toEqual(['Search by name', 'Department', 'Country', 'Job level']);
  });
});
