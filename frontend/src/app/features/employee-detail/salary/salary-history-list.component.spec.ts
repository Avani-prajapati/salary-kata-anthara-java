import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { SalaryHistoryListComponent } from './salary-history-list.component';
import { SalaryHistoryEntry } from '../../../core/models/employee.model';

describe('SalaryHistoryListComponent', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [SalaryHistoryListComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('loads single-entry history correctly', async () => {
    const singleEntry: SalaryHistoryEntry = {
      amount: 50000,
      currencyCode: 'USD',
      effectiveDate: '2024-01-01',
      recordedAt: '2024-01-01T00:00:00Z',
    };

    const fixture = TestBed.createComponent(SalaryHistoryListComponent);
    fixture.componentRef.setInput('employeeId', 1);
    await fixture.whenStable();

    const req = httpMock.expectOne('/api/employees/1/salary-history');
    req.flush([singleEntry]);
    await fixture.whenStable();

    expect(fixture.componentInstance.history()).toHaveLength(1);
    expect(fixture.componentInstance.history()[0].amount).toBe(50000);
    expect(fixture.componentInstance.history()[0].currencyCode).toBe('USD');
    fixture.destroy();
  });

  it('loads multiple history entries in newest-effective-date-first order', async () => {
    const entries: SalaryHistoryEntry[] = [
      {
        amount: 100000,
        currencyCode: 'USD',
        effectiveDate: '2024-06-01',
        recordedAt: '2024-06-01T00:00:00Z',
      },
      {
        amount: 80000,
        currencyCode: 'USD',
        effectiveDate: '2024-01-01',
        recordedAt: '2024-01-01T00:00:00Z',
      },
    ];

    const fixture = TestBed.createComponent(SalaryHistoryListComponent);
    fixture.componentRef.setInput('employeeId', 2);
    await fixture.whenStable();

    const req = httpMock.expectOne('/api/employees/2/salary-history');
    req.flush(entries);
    await fixture.whenStable();

    expect(fixture.componentInstance.history()).toHaveLength(2);
    expect(fixture.componentInstance.history()[0].effectiveDate).toBe('2024-06-01');
    expect(fixture.componentInstance.history()[1].effectiveDate).toBe('2024-01-01');
    fixture.destroy();
  });

  it('template does not include edit or delete controls', () => {
    const fixture = TestBed.createComponent(SalaryHistoryListComponent);
    const template = fixture.debugElement.nativeElement.innerHTML.toLowerCase();
    const hasDeleteButton = template.includes('delete');
    const hasEditButton = template.includes('edit') && template.includes('button');
    expect(hasDeleteButton || hasEditButton).toBeFalsy();
    fixture.destroy();
  });
});
