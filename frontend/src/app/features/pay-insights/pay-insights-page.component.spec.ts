import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
  TestRequest,
} from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { MatSelectHarness } from '@angular/material/select/testing';
import { MatTableHarness } from '@angular/material/table/testing';
import { GroupBreakdown, PaySummary } from '../../core/models/pay-report.model';
import { PayInsightsPageComponent } from './pay-insights-page.component';

const SUMMARY: PaySummary = {
  totalHeadcount: 3,
  byCurrency: [
    { currencyCode: 'EUR', headcount: 1, averageSalary: 82000, medianSalary: 82000 },
    { currencyCode: 'USD', headcount: 2, averageSalary: 100000, medianSalary: 95000 },
  ],
};

const BY_DEPARTMENT: GroupBreakdown[] = [
  {
    groupKey: 'HUMAN_RESOURCES',
    byCurrency: [
      { currencyCode: 'EUR', headcount: 1, averageSalary: 82000, medianSalary: 82000 },
      { currencyCode: 'USD', headcount: 2, averageSalary: 100000, medianSalary: 95000 },
    ],
  },
];

const BY_COUNTRY: GroupBreakdown[] = [
  {
    groupKey: 'DE',
    byCurrency: [{ currencyCode: 'EUR', headcount: 1, averageSalary: 82000, medianSalary: 82000 }],
  },
  {
    groupKey: 'US',
    byCurrency: [{ currencyCode: 'USD', headcount: 2, averageSalary: 100000, medianSalary: 95000 }],
  },
];

const BY_JOB_LEVEL: GroupBreakdown[] = [
  {
    groupKey: 'VP',
    byCurrency: [{ currencyCode: 'USD', headcount: 2, averageSalary: 100000, medianSalary: 95000 }],
  },
];

const ENGINEERING_SUMMARY: PaySummary = {
  totalHeadcount: 3,
  byCurrency: [
    { currencyCode: 'EUR', headcount: 1, averageSalary: 95000, medianSalary: 95000 },
    { currencyCode: 'USD', headcount: 2, averageSalary: 130000, medianSalary: 130000 },
  ],
};

const NO_MATCH_SUMMARY: PaySummary = { totalHeadcount: 0, byCurrency: [] };

describe('pay insights page', () => {
  let fixture: ComponentFixture<PayInsightsPageComponent>;
  let httpTesting: HttpTestingController;
  let loader: HarnessLoader;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [PayInsightsPageComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PayInsightsPageComponent);
    loader = TestbedHarnessEnvironment.loader(fixture);
    await fixture.whenStable();
  });

  afterEach(() => {
    httpTesting.verify();
  });

  function page(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function texts(selector: string): string[] {
    return Array.from(page().querySelectorAll(selector)).map(
      (element) => element.textContent?.trim() ?? '',
    );
  }

  function pendingSummaryRequest(): TestRequest {
    return httpTesting.expectOne((request) => request.url === '/api/reports/pay/summary'
      || request.url.startsWith('/api/reports/pay/summary?'));
  }

  function pendingBreakdownRequest(dimension: string): TestRequest {
    return httpTesting.expectOne(
      (request) =>
        request.url.startsWith('/api/reports/pay/breakdown')
        && request.params.get('dimension') === dimension,
    );
  }

  async function respondWithOrgWideReports(): Promise<void> {
    pendingSummaryRequest().flush(SUMMARY);
    pendingBreakdownRequest('department').flush(BY_DEPARTMENT);
    pendingBreakdownRequest('country').flush(BY_COUNTRY);
    pendingBreakdownRequest('jobLevel').flush(BY_JOB_LEVEL);
    await fixture.whenStable();
  }

  async function selectDepartment(label: string): Promise<void> {
    const departmentSelect = await loader.getHarness(
      MatSelectHarness.with({ selector: '[formControlName="department"]' }),
    );
    await departmentSelect.clickOptions({ text: label });
  }

  it('shows total headcount and a headcount, average and median tile for each currency', async () => {
    await respondWithOrgWideReports();

    expect(page().querySelector('[data-testid="total-headcount"]')?.textContent?.trim()).toBe('3');
    expect(texts('[data-testid="currency-tile"] .tile-title')).toEqual([
      'Paid in EUR',
      'Paid in USD',
    ]);
    expect(texts('[data-testid="headcount"]')).toEqual(['1', '2']);
    expect(texts('[data-testid="average-salary"]')).toEqual(['€82,000', '$100,000']);
    expect(texts('[data-testid="median-salary"]')).toEqual(['€82,000', '$95,000']);
  });

  it('never shows a salary figure combined or converted across currencies', async () => {
    await respondWithOrgWideReports();

    const pageText = page().textContent ?? '';
    expect(page().querySelectorAll('[data-testid="currency-tile"]')).toHaveLength(2);
    expect(pageText).not.toContain('182,000');
    expect(pageText).not.toContain('91,000');
    expect(pageText).not.toContain('Average salary across');
  });

  it('shows headcount and average salary per currency broken down by department, country and job level', async () => {
    await respondWithOrgWideReports();

    const [byDepartment, byCountry, byJobLevel] = await loader.getAllHarnesses(MatTableHarness);
    expect(await byDepartment.getCellTextByIndex()).toEqual([
      ['Human resources', 'EUR', '1', '€82,000', '€82,000'],
      ['Human resources', 'USD', '2', '$100,000', '$95,000'],
    ]);
    expect(await byCountry.getCellTextByIndex()).toEqual([
      ['Germany', 'EUR', '1', '€82,000', '€82,000'],
      ['United States', 'USD', '2', '$100,000', '$95,000'],
    ]);
    expect(await byJobLevel.getCellTextByIndex()).toEqual([
      ['VP', 'USD', '2', '$100,000', '$95,000'],
    ]);
    expect(texts('h2')).toEqual([
      'Organization summary',
      'By department',
      'By country',
      'By job level',
    ]);
  });

  it('labels every breakdown column with a header', async () => {
    await respondWithOrgWideReports();

    const [byDepartment] = await loader.getAllHarnesses(MatTableHarness);
    const [headerRow] = await byDepartment.getHeaderRows();
    expect(await headerRow.getCellTextByIndex()).toEqual([
      'Department',
      'Currency',
      'Headcount',
      'Average salary',
      'Median salary',
    ]);
  });

  it('is read-only with no controls to edit anything, only to filter what is shown', async () => {
    await respondWithOrgWideReports();

    const controlsOutsideTheFilterBar = Array.from(
      page().querySelectorAll('button, input, select, textarea, form'),
    ).filter((element) => element.getAttribute('role') !== 'search' && !element.closest('[role="search"]'));

    expect(controlsOutsideTheFilterBar).toHaveLength(0);
  });

  it('reads only pre-aggregated reports and never the employee list', async () => {
    await respondWithOrgWideReports();

    httpTesting.expectNone((request) => request.url.startsWith('/api/employees'));
  });

  it('narrows the summary and breakdown figures to a selected department', async () => {
    await respondWithOrgWideReports();

    await selectDepartment('Engineering');
    const summaryRequest = pendingSummaryRequest();
    expect(summaryRequest.request.params.get('department')).toBe('ENGINEERING');
    summaryRequest.flush(ENGINEERING_SUMMARY);
    const departmentRequest = pendingBreakdownRequest('department');
    expect(departmentRequest.request.params.get('department')).toBe('ENGINEERING');
    departmentRequest.flush(BY_DEPARTMENT);
    pendingBreakdownRequest('country').flush(BY_COUNTRY);
    pendingBreakdownRequest('jobLevel').flush(BY_JOB_LEVEL);
    await fixture.whenStable();

    expect(page().querySelector('[data-testid="total-headcount"]')?.textContent?.trim()).toBe('3');
    expect(texts('[data-testid="average-salary"]')).toEqual(['€95,000', '$130,000']);
  });

  it('shows an explicit "no employees match" state for a filter combination that matches nobody', async () => {
    await respondWithOrgWideReports();

    await selectDepartment('Legal');
    pendingSummaryRequest().flush(NO_MATCH_SUMMARY);
    pendingBreakdownRequest('department').flush([]);
    pendingBreakdownRequest('country').flush([]);
    pendingBreakdownRequest('jobLevel').flush([]);
    await fixture.whenStable();

    expect(page().querySelector('[role="status"]')?.textContent?.trim()).toBe(
      'No employees match the selected filters.',
    );
    expect(page().querySelectorAll('[data-testid="currency-tile"]')).toHaveLength(0);
    expect(page().querySelector('[data-testid="total-headcount"]')).toBeNull();
  });

  it('reproduces the exact org-wide figures when filters are cleared', async () => {
    await respondWithOrgWideReports();
    await selectDepartment('Engineering');
    pendingSummaryRequest().flush(ENGINEERING_SUMMARY);
    pendingBreakdownRequest('department').flush(BY_DEPARTMENT);
    pendingBreakdownRequest('country').flush(BY_COUNTRY);
    pendingBreakdownRequest('jobLevel').flush(BY_JOB_LEVEL);
    await fixture.whenStable();

    await selectDepartment('All departments');
    const clearedSummaryRequest = pendingSummaryRequest();
    expect(clearedSummaryRequest.request.params.has('department')).toBe(false);
    clearedSummaryRequest.flush(SUMMARY);
    pendingBreakdownRequest('department').flush(BY_DEPARTMENT);
    pendingBreakdownRequest('country').flush(BY_COUNTRY);
    pendingBreakdownRequest('jobLevel').flush(BY_JOB_LEVEL);
    await fixture.whenStable();

    expect(page().querySelector('[data-testid="total-headcount"]')?.textContent?.trim()).toBe('3');
    expect(texts('[data-testid="currency-tile"] .tile-title')).toEqual([
      'Paid in EUR',
      'Paid in USD',
    ]);
    expect(texts('[data-testid="average-salary"]')).toEqual(['€82,000', '$100,000']);
  });

  it('shows an error message when the reports cannot be loaded', async () => {
    httpTesting
      .expectOne('/api/reports/pay/summary')
      .flush(null, { status: 500, statusText: 'Server Error' });
    httpTesting.match((request) => request.url === '/api/reports/pay/breakdown');
    await fixture.whenStable();

    expect(page().querySelector('[role="status"]')?.textContent?.trim()).toBe(
      'Pay insights could not be loaded. Please try again.',
    );
  });
});
