import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { CountryNamePipe } from '../../core/formatting/country-name.pipe';
import { EnumLabelPipe } from '../../core/formatting/enum-label.pipe';
import { ApiError } from '../../core/http/api-error';
import {
  COUNTRY_CODES,
  CountryCode,
  Department,
  DEPARTMENTS,
  EmployeeCreateRequest,
  EmployeeSummary,
  JOB_LEVELS,
  JobLevel,
} from '../../core/models/employee.model';
import { EmployeeOnboardingService } from './employee-onboarding.service';

const MINIMUM_STARTING_SALARY_AMOUNT = 0.01;

@Component({
  selector: 'app-add-employee-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    EnumLabelPipe,
    CountryNamePipe,
  ],
  templateUrl: './add-employee-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AddEmployeeDialogComponent {
  private readonly employeeOnboardingService = inject(EmployeeOnboardingService);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly dialogRef = inject(MatDialogRef<AddEmployeeDialogComponent, EmployeeSummary>);

  protected readonly departments = DEPARTMENTS;
  protected readonly jobLevels = JOB_LEVELS;
  protected readonly countryCodes = COUNTRY_CODES;
  protected readonly saving = signal(false);

  protected readonly form = this.formBuilder.group({
    name: this.formBuilder.control('', Validators.required),
    department: this.formBuilder.control<Department | null>(null, Validators.required),
    jobLevel: this.formBuilder.control<JobLevel | null>(null, Validators.required),
    countryCode: this.formBuilder.control<CountryCode | null>(null, Validators.required),
    hireDate: this.formBuilder.control('', Validators.required),
    managerId: this.formBuilder.control<number | null>(null),
    startingSalaryAmount: this.formBuilder.control<number | null>(null, [
      Validators.required,
      Validators.min(MINIMUM_STARTING_SALARY_AMOUNT),
    ]),
    startingSalaryCurrency: this.formBuilder.control('', [
      Validators.required,
      Validators.pattern(/^[A-Za-z]{3}$/),
    ]),
  });

  protected onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const value = this.form.getRawValue();
    const request: EmployeeCreateRequest = {
      name: value.name,
      department: value.department as Department,
      jobLevel: value.jobLevel as JobLevel,
      countryCode: value.countryCode as CountryCode,
      hireDate: value.hireDate,
      managerId: value.managerId,
      startingSalaryAmount: value.startingSalaryAmount as number,
      startingSalaryCurrency: value.startingSalaryCurrency.toUpperCase(),
    };
    this.employeeOnboardingService.create(request).subscribe({
      next: (created) => {
        this.saving.set(false);
        this.dialogRef.close(created);
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.applyServerFieldError(error);
      },
    });
  }

  protected onCancel(): void {
    this.dialogRef.close();
  }

  private applyServerFieldError(error: unknown): void {
    if (error instanceof ApiError && error.field === 'managerId') {
      this.form.controls.managerId.setErrors({ server: error.message });
    }
  }
}
