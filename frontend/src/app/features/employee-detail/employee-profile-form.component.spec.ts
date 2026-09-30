import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { MatSelectHarness } from '@angular/material/select/testing';
import { EmployeeDetail } from '../../core/models/employee.model';
import { EmployeeProfileFormComponent } from './employee-profile-form.component';

const JANE_ITO: EmployeeDetail = {
  id: 7,
  name: 'Jane Ito',
  department: 'ENGINEERING',
  jobLevel: 'SENIOR',
  countryCode: 'JP',
  hireDate: '2021-03-15',
  managerId: null,
  managerName: null,
  salaryAmount: 9800000,
  salaryCurrency: 'JPY',
};

describe('employee profile form', () => {
  let fixture: ComponentFixture<EmployeeProfileFormComponent>;
  let httpTesting: HttpTestingController;
  let loader: HarnessLoader;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [EmployeeProfileFormComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(EmployeeProfileFormComponent);
    loader = TestbedHarnessEnvironment.loader(fixture);
    fixture.componentRef.setInput('employee', JANE_ITO);
    await fixture.whenStable();
  });

  afterEach(() => {
    httpTesting.verify();
  });

  function element(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  it('edits department, job level, country, hire date, and manager, with no salary control anywhere', () => {
    const labels = Array.from(element().querySelectorAll('mat-label'), (label) =>
      label.textContent?.trim(),
    );

    expect(labels).toEqual(['Department', 'Job level', 'Country', 'Hire date', 'Manager id']);
    expect(element().querySelectorAll('[formcontrolname*="salary" i]')).toHaveLength(0);
  });

  it('ties a blank hire-date validation error to the hire-date input', async () => {
    const dateInput = element().querySelector(
      'input[formcontrolname="hireDate"]',
    ) as HTMLInputElement;

    dateInput.value = '';
    dateInput.dispatchEvent(new Event('input'));
    dateInput.dispatchEvent(new Event('blur'));
    fixture.detectChanges();
    await fixture.whenStable();

    const formField = dateInput.closest('mat-form-field') as HTMLElement;
    const error = formField.querySelector('mat-error') as HTMLElement;

    expect(error?.textContent).toContain('required');
    expect(dateInput.getAttribute('aria-describedby')).toContain(error.id);
  });

  it('submits the edited profile and emits the saved employee on success', async () => {
    const savedEmployees: EmployeeDetail[] = [];
    fixture.componentInstance.saved.subscribe((employee) => savedEmployees.push(employee));

    const countrySelect = await loader.getHarness(
      MatSelectHarness.with({ selector: '[formControlName="countryCode"]' }),
    );
    await countrySelect.clickOptions({ text: 'United States' });

    const saveButton = element().querySelector('button[type="submit"]') as HTMLButtonElement;
    saveButton.click();

    const request = httpTesting.expectOne('/api/employees/7');
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body.countryCode).toBe('US');
    const updated: EmployeeDetail = { ...JANE_ITO, countryCode: 'US' };
    request.flush(updated);
    await fixture.whenStable();

    expect(savedEmployees).toEqual([updated]);
  });

  it('does not emit a cancelled profile update as a save', async () => {
    const savedEmployees: EmployeeDetail[] = [];
    const cancelledCalls: void[] = [];
    fixture.componentInstance.saved.subscribe((employee) => savedEmployees.push(employee));
    fixture.componentInstance.cancelled.subscribe(() => cancelledCalls.push(undefined));

    const cancelButton = element().querySelector('button[type="button"]') as HTMLButtonElement;
    cancelButton.click();
    await fixture.whenStable();

    expect(cancelledCalls).toHaveLength(1);
    expect(savedEmployees).toHaveLength(0);
  });
});
