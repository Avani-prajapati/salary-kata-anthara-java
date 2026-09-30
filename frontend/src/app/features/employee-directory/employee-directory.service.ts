import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  CountryCode,
  Department,
  EmployeeSummary,
  JobLevel,
} from '../../core/models/employee.model';
import { PagedResponse } from '../../core/models/paged-response.model';

export interface EmployeeSearchCriteria {
  name: string;
  department: Department | null;
  countryCode: CountryCode | null;
  jobLevel: JobLevel | null;
}

export type DirectorySortColumn =
  'name' | 'department' | 'jobLevel' | 'countryCode' | 'salaryAmount';

export interface DirectorySort {
  column: DirectorySortColumn;
  direction: 'asc' | 'desc';
}

@Injectable({ providedIn: 'root' })
export class EmployeeDirectoryService {
  private readonly http = inject(HttpClient);

  search(
    criteria: EmployeeSearchCriteria,
    pageIndex: number,
    pageSize: number,
    sort: DirectorySort,
  ): Observable<PagedResponse<EmployeeSummary>> {
    const params = new HttpParams({
      fromObject: {
        ...activeFilters(criteria),
        page: pageIndex,
        size: pageSize,
        sort: `${sort.column},${sort.direction}`,
      },
    });
    return this.http.get<PagedResponse<EmployeeSummary>>('/api/employees', { params });
  }
}

function activeFilters(criteria: EmployeeSearchCriteria): Record<string, string> {
  const filters = { ...criteria, name: criteria.name.trim() };
  return Object.fromEntries(
    Object.entries(filters).filter((filter): filter is [string, string] => Boolean(filter[1])),
  );
}
