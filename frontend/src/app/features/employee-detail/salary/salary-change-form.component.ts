import {
  ChangeDetectionStrategy,
  Component,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { SalaryChangeRequest, SalaryHistoryEntry } from '../../../core/models/employee.model';
import { CompensationService } from './compensation.service';

const MINIMUM_SALARY_AMOUNT = 0.01;

@Component({
  selector: 'app-salary-change-form',
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  templateUrl: './salary-change-form.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SalaryChangeFormComponent {
  private readonly compensationService = inject(CompensationService);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  readonly employeeId = input.required<number>();
  readonly saved = output<SalaryHistoryEntry>();
  readonly cancelled = output<void>();

  protected readonly saving = signal(false);

  protected readonly form = this.formBuilder.group({
    amount: this.formBuilder.control<number | null>(null, [
      Validators.required,
      Validators.min(MINIMUM_SALARY_AMOUNT),
    ]),
    currencyCode: this.formBuilder.control('', [Validators.required, Validators.pattern(/^[A-Za-z]{3}$/)]),
    effectiveDate: this.formBuilder.control('', Validators.required),
  });

  protected onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const value = this.form.getRawValue();
    const request: SalaryChangeRequest = {
      amount: value.amount as number,
      currencyCode: value.currencyCode.toUpperCase(),
      effectiveDate: value.effectiveDate,
    };
    this.compensationService.changeSalary(this.employeeId(), request).subscribe({
      next: (entry) => {
        this.saving.set(false);
        this.saved.emit(entry);
      },
      error: () => this.saving.set(false),
    });
  }

  protected onCancel(): void {
    this.cancelled.emit();
  }
}
