import {
  ChangeDetectionStrategy,
  Component,
  effect,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { CountryNamePipe } from '../../core/formatting/country-name.pipe';
import { EnumLabelPipe } from '../../core/formatting/enum-label.pipe';
import {
  COUNTRY_CODES,
  CountryCode,
  Department,
  DEPARTMENTS,
  EmployeeDetail,
  EmployeeProfileUpdateRequest,
  JOB_LEVELS,
  JobLevel,
} from '../../core/models/employee.model';
import { EmployeeDetailService } from './employee-detail.service';

@Component({
  selector: 'app-employee-profile-form',
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    EnumLabelPipe,
    CountryNamePipe,
  ],
  templateUrl: './employee-profile-form.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmployeeProfileFormComponent {
  private readonly employeeDetailService = inject(EmployeeDetailService);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  readonly employee = input.required<EmployeeDetail>();
  readonly saved = output<EmployeeDetail>();
  readonly cancelled = output<void>();

  protected readonly departments = DEPARTMENTS;
  protected readonly jobLevels = JOB_LEVELS;
  protected readonly countryCodes = COUNTRY_CODES;
  protected readonly saving = signal(false);

  protected readonly form = this.formBuilder.group({
    department: this.formBuilder.control<Department | null>(null, Validators.required),
    jobLevel: this.formBuilder.control<JobLevel | null>(null, Validators.required),
    countryCode: this.formBuilder.control<CountryCode | null>(null, Validators.required),
    hireDate: this.formBuilder.control('', Validators.required),
    managerId: this.formBuilder.control<number | null>(null),
  });

  constructor() {
    effect(() => this.form.patchValue(this.employee()));
  }

  protected onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const value = this.form.getRawValue();
    const request: EmployeeProfileUpdateRequest = {
      department: value.department as Department,
      jobLevel: value.jobLevel as JobLevel,
      countryCode: value.countryCode as CountryCode,
      hireDate: value.hireDate,
      managerId: value.managerId,
    };
    this.employeeDetailService.updateProfile(this.employee().id, request).subscribe({
      next: (updated) => {
        this.saving.set(false);
        this.saved.emit(updated);
      },
      error: () => this.saving.set(false),
    });
  }

  protected onCancel(): void {
    this.cancelled.emit();
  }
}
