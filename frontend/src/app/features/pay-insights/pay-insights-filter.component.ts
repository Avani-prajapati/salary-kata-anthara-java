import { ChangeDetectionStrategy, Component, inject, output } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { CountryNamePipe } from '../../core/formatting/country-name.pipe';
import { EnumLabelPipe } from '../../core/formatting/enum-label.pipe';
import {
  COUNTRY_CODES,
  CountryCode,
  Department,
  DEPARTMENTS,
  JOB_LEVELS,
  JobLevel,
} from '../../core/models/employee.model';
import { PayReportCriteria } from '../../core/models/pay-report.model';

@Component({
  selector: 'app-pay-insights-filter',
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatSelectModule,
    EnumLabelPipe,
    CountryNamePipe,
  ],
  templateUrl: './pay-insights-filter.component.html',
  styleUrl: './pay-insights-filter.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PayInsightsFilterComponent {
  private readonly formBuilder = inject(NonNullableFormBuilder);

  protected readonly departments = DEPARTMENTS;
  protected readonly jobLevels = JOB_LEVELS;
  protected readonly countryCodes = COUNTRY_CODES;

  protected readonly filters = this.formBuilder.group({
    department: this.formBuilder.control<Department | null>(null),
    countryCode: this.formBuilder.control<CountryCode | null>(null),
    jobLevel: this.formBuilder.control<JobLevel | null>(null),
  });

  readonly criteriaChange = output<PayReportCriteria>();

  constructor() {
    this.filters.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.criteriaChange.emit(this.filters.getRawValue()));
  }
}
