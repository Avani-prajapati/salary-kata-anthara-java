import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
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

  async function respondWithOrgWideReports(): Promise<void> {
    httpTesting.expectOne('/api/reports/pay/summary').flush(SUMMARY);
    httpTesting.expectOne('/api/reports/pay/breakdown?dimension=department').flush(BY_DEPARTMENT);
    httpTesting.expectOne('/api/reports/pay/breakdown?dimension=country').flush(BY_COUNTRY);
    httpTesting.expectOne('/api/reports/pay/breakdown?dimension=jobLevel').flush(BY_JOB_LEVEL);
    await fixture.whenStable();
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

  it('is read-only with no controls to edit anything', async () => {
    await respondWithOrgWideReports();

    expect(page().querySelectorAll('button, input, select, textarea, form')).toHaveLength(0);
  });

  it('reads only pre-aggregated reports and never the employee list', async () => {
    await respondWithOrgWideReports();

    httpTesting.expectNone((request) => request.url.startsWith('/api/employees'));
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
