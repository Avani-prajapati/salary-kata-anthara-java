export const DEPARTMENTS = [
  'ENGINEERING',
  'PRODUCT',
  'DESIGN',
  'SALES',
  'MARKETING',
  'FINANCE',
  'HUMAN_RESOURCES',
  'OPERATIONS',
  'LEGAL',
  'CUSTOMER_SUPPORT',
] as const;

export type Department = (typeof DEPARTMENTS)[number];

export const JOB_LEVELS = [
  'INTERN',
  'ENTRY',
  'MID',
  'SENIOR',
  'LEAD',
  'MANAGER',
  'DIRECTOR',
  'VP',
  'EXECUTIVE',
] as const;

export type JobLevel = (typeof JOB_LEVELS)[number];

export const COUNTRY_CODES = [
  'US',
  'IN',
  'GB',
  'DE',
  'FR',
  'CA',
  'JP',
  'AU',
  'NL',
  'SG',
  'BR',
] as const;

export type CountryCode = (typeof COUNTRY_CODES)[number];

export interface EmployeeSummary {
  id: number;
  name: string;
  department: Department;
  jobLevel: JobLevel;
  countryCode: CountryCode;
  salaryAmount: number;
  salaryCurrency: string;
}
