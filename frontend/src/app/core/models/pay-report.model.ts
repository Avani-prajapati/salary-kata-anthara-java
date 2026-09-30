export type BreakdownDimension = 'department' | 'country' | 'jobLevel';

export interface CurrencyAggregate {
  currencyCode: string;
  headcount: number;
  averageSalary: number;
  medianSalary: number;
}

export interface PaySummary {
  totalHeadcount: number;
  byCurrency: CurrencyAggregate[];
}

export interface GroupBreakdown {
  groupKey: string;
  byCurrency: CurrencyAggregate[];
}
