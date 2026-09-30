import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SalaryHistoryEntry } from '../../../core/models/employee.model';
import { SalaryChangeFormComponent } from './salary-change-form.component';

describe('salary change form', () => {
  let fixture: ComponentFixture<SalaryChangeFormComponent>;
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [SalaryChangeFormComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(SalaryChangeFormComponent);
    fixture.componentRef.setInput('employeeId', 7);
    await fixture.whenStable();
  });

  afterEach(() => {
    httpTesting.verify();
  });

  function element(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function setControl(formControlName: string, value: string): void {
    const input = element().querySelector(
      `[formcontrolname="${formControlName}"]`,
    ) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new Event('blur'));
  }

  function submit(): void {
    const saveButton = element().querySelector('button[type="submit"]') as HTMLButtonElement;
    saveButton.click();
  }

  it('blocks a zero or negative amount before any request is sent', async () => {
    setControl('amount', '0');
    setControl('currencyCode', 'USD');
    setControl('effectiveDate', '2024-06-01');
    fixture.detectChanges();
    await fixture.whenStable();

    submit();
    fixture.detectChanges();
    await fixture.whenStable();

    const amountField = element().querySelector(
      '[formcontrolname="amount"]',
    )?.closest('mat-form-field') as HTMLElement;
    expect(amountField.querySelector('mat-error')).toBeTruthy();
    httpTesting.expectNone(() => true);
  });

  it('submits a valid salary change and emits the created entry on success', async () => {
    setControl('amount', '100000');
    setControl('currencyCode', 'USD');
    setControl('effectiveDate', '2024-06-01');
    fixture.detectChanges();
    await fixture.whenStable();

    const savedEntries: SalaryHistoryEntry[] = [];
    fixture.componentInstance.saved.subscribe((entry) => savedEntries.push(entry));

    submit();

    const request = httpTesting.expectOne('/api/employees/7/salary-changes');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      amount: 100000,
      currencyCode: 'USD',
      effectiveDate: '2024-06-01',
    });
    const created: SalaryHistoryEntry = {
      amount: 100000,
      currencyCode: 'USD',
      effectiveDate: '2024-06-01',
      recordedAt: '2024-06-01T00:00:00Z',
    };
    request.flush(created);
    await fixture.whenStable();

    expect(savedEntries).toEqual([created]);
  });

  it('does not emit a cancelled form as a save', async () => {
    const savedEntries: SalaryHistoryEntry[] = [];
    const cancelledCalls: void[] = [];
    fixture.componentInstance.saved.subscribe((entry) => savedEntries.push(entry));
    fixture.componentInstance.cancelled.subscribe(() => cancelledCalls.push(undefined));

    const cancelButton = element().querySelector('button[type="button"]') as HTMLButtonElement;
    cancelButton.click();
    await fixture.whenStable();

    expect(cancelledCalls).toHaveLength(1);
    expect(savedEntries).toHaveLength(0);
    httpTesting.expectNone(() => true);
  });
});
