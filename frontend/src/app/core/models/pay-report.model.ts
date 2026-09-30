import { CountryCode, Department, JobLevel } from './employee.model';

export type BreakdownDimension = 'department' | 'country' | 'jobLevel';

export interface PayReportCriteria {
  department: Department | null;
  countryCode: CountryCode | null;
  jobLevel: JobLevel | null;
}

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
