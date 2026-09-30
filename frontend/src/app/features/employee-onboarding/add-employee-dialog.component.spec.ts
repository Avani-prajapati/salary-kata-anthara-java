import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { MatDialogRef } from '@angular/material/dialog';
import { MatSelectHarness } from '@angular/material/select/testing';
import { EmployeeSummary } from '../../core/models/employee.model';
import { AddEmployeeDialogComponent } from './add-employee-dialog.component';

describe('add employee dialog', () => {
  let fixture: ComponentFixture<AddEmployeeDialogComponent>;
  let httpTesting: HttpTestingController;
  let loader: HarnessLoader;
  let dialogRef: { close: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    dialogRef = { close: vi.fn() };
    TestBed.configureTestingModule({
      imports: [AddEmployeeDialogComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: dialogRef },
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AddEmployeeDialogComponent);
    loader = TestbedHarnessEnvironment.loader(fixture);
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

  async function fillRequiredFields(): Promise<void> {
    setControl('name', 'New Hire');
    setControl('hireDate', '2024-06-01');
    setControl('startingSalaryAmount', '70000');
    setControl('startingSalaryCurrency', 'USD');
    fixture.detectChanges();
    await fixture.whenStable();

    await (
      await loader.getHarness(MatSelectHarness.with({ selector: '[formControlName="department"]' }))
    ).clickOptions({ text: 'Engineering' });
    await (
      await loader.getHarness(MatSelectHarness.with({ selector: '[formControlName="jobLevel"]' }))
    ).clickOptions({ text: 'Entry' });
    await (
      await loader.getHarness(MatSelectHarness.with({ selector: '[formControlName="countryCode"]' }))
    ).clickOptions({ text: 'United States' });
  }

  it('collects name, department, job level, country, hire date, manager, and starting salary amount and currency', () => {
    const labels = Array.from(element().querySelectorAll('mat-label'), (label) =>
      label.textContent?.trim(),
    );

    expect(labels).toEqual([
      'Name',
      'Department',
      'Job level',
      'Country',
      'Hire date',
      'Manager id',
      'Starting salary amount',
      'Starting salary currency',
    ]);
  });

  it('ties a blank name validation error to the name input', async () => {
    const nameInput = element().querySelector('input[formcontrolname="name"]') as HTMLInputElement;

    nameInput.value = '';
    nameInput.dispatchEvent(new Event('input'));
    nameInput.dispatchEvent(new Event('blur'));
    fixture.detectChanges();
    await fixture.whenStable();

    const formField = nameInput.closest('mat-form-field') as HTMLElement;
    const error = formField.querySelector('mat-error') as HTMLElement;

    expect(error?.textContent).toContain('required');
    expect(nameInput.getAttribute('aria-describedby')).toContain(error.id);
  });

  it('blocks submission when required fields are blank', () => {
    submit();
    fixture.detectChanges();

    expect(element().querySelectorAll('mat-error').length).toBeGreaterThan(0);
    expect(dialogRef.close).not.toHaveBeenCalled();
    httpTesting.expectNone(() => true);
  });

  it('submits a valid new hire and closes the dialog with the created employee', async () => {
    await fillRequiredFields();

    submit();

    const request = httpTesting.expectOne('/api/employees');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      name: 'New Hire',
      department: 'ENGINEERING',
      jobLevel: 'ENTRY',
      countryCode: 'US',
      hireDate: '2024-06-01',
      managerId: null,
      startingSalaryAmount: 70000,
      startingSalaryCurrency: 'USD',
    });
    const created: EmployeeSummary = {
      id: 9,
      name: 'New Hire',
      department: 'ENGINEERING',
      jobLevel: 'ENTRY',
      countryCode: 'US',
      salaryAmount: 70000,
      salaryCurrency: 'USD',
    };
    request.flush(created);
    await fixture.whenStable();

    expect(dialogRef.close).toHaveBeenCalledWith(created);
  });

  it('closes the dialog with no result and sends no request when cancelled', () => {
    const cancelButton = element().querySelector('button[type="button"]') as HTMLButtonElement;
    cancelButton.click();

    expect(dialogRef.close).toHaveBeenCalledWith();
    httpTesting.expectNone(() => true);
  });
});
