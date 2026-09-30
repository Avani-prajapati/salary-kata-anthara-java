import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { catchError, forkJoin, map, Observable, of } from 'rxjs';
import { CountryNamePipe } from '../../core/formatting/country-name.pipe';
import { EnumLabelPipe } from '../../core/formatting/enum-label.pipe';
import {
  BreakdownDimension,
  CurrencyAggregate,
  GroupBreakdown,
  PaySummary,
} from '../../core/models/pay-report.model';
import { PayInsightsService } from './pay-insights.service';

interface BreakdownRow extends CurrencyAggregate {
  groupLabel: string;
}

interface BreakdownSection {
  dimension: BreakdownDimension;
  heading: string;
  groupColumnHeader: string;
  rows: BreakdownRow[];
}

interface PayInsights {
  summary: PaySummary;
  breakdowns: BreakdownSection[];
}

type InsightsOutcome = { kind: 'loaded'; insights: PayInsights } | { kind: 'failed' };

interface DimensionPresentation {
  heading: string;
  groupColumnHeader: string;
  labelFor: (groupKey: string) => string;
}

const enumLabel = new EnumLabelPipe();
const countryName = new CountryNamePipe();

const DIMENSIONS: Record<BreakdownDimension, DimensionPresentation> = {
  department: {
    heading: 'By department',
    groupColumnHeader: 'Department',
    labelFor: (groupKey) => enumLabel.transform(groupKey),
  },
  country: {
    heading: 'By country',
    groupColumnHeader: 'Country',
    labelFor: (groupKey) => countryName.transform(groupKey),
  },
  jobLevel: {
    heading: 'By job level',
    groupColumnHeader: 'Job level',
    labelFor: (groupKey) => enumLabel.transform(groupKey),
  },
};

const STATUS_MESSAGES: Record<InsightsOutcome['kind'] | 'pending', string> = {
  pending: 'Loading pay insights…',
  loaded: '',
  failed: 'Pay insights could not be loaded. Please try again.',
};

@Component({
  selector: 'app-pay-insights-page',
  imports: [CurrencyPipe, DecimalPipe, MatCardModule, MatTableModule],
  templateUrl: './pay-insights-page.component.html',
  styleUrl: './pay-insights-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PayInsightsPageComponent {
  private readonly payInsights = inject(PayInsightsService);

  protected readonly breakdownColumns = [
    'group',
    'currencyCode',
    'headcount',
    'averageSalary',
    'medianSalary',
  ];

  private readonly outcome = toSignal(this.loadInsights());

  protected readonly insights = computed(() => {
    const outcome = this.outcome();
    return outcome?.kind === 'loaded' ? outcome.insights : undefined;
  });
  protected readonly statusMessage = computed(
    () => STATUS_MESSAGES[this.outcome()?.kind ?? 'pending'],
  );

  private loadInsights(): Observable<InsightsOutcome> {
    const dimensions = Object.keys(DIMENSIONS) as BreakdownDimension[];
    return forkJoin({
      summary: this.payInsights.getSummary(),
      breakdowns: forkJoin(
        dimensions.map((dimension) =>
          this.payInsights
            .getBreakdown(dimension)
            .pipe(map((groups) => toSection(dimension, groups))),
        ),
      ),
    }).pipe(
      map((insights): InsightsOutcome => ({ kind: 'loaded', insights })),
      catchError(() => of<InsightsOutcome>({ kind: 'failed' })),
    );
  }
}

function toSection(dimension: BreakdownDimension, groups: GroupBreakdown[]): BreakdownSection {
  const presentation = DIMENSIONS[dimension];
  return {
    dimension,
    heading: presentation.heading,
    groupColumnHeader: presentation.groupColumnHeader,
    rows: groups.flatMap((group) =>
      group.byCurrency.map((aggregate) => ({
        ...aggregate,
        groupLabel: presentation.labelFor(group.groupKey),
      })),
    ),
  };
}
