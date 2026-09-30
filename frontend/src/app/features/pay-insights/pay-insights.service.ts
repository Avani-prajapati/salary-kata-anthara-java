import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  BreakdownDimension,
  GroupBreakdown,
  PayReportCriteria,
  PaySummary,
} from '../../core/models/pay-report.model';

const PAY_REPORTS_URL = '/api/reports/pay';

@Injectable({ providedIn: 'root' })
export class PayInsightsService {
  private readonly http = inject(HttpClient);

  getSummary(criteria: PayReportCriteria): Observable<PaySummary> {
    const params = new HttpParams({ fromObject: activeFilters(criteria) });
    return this.http.get<PaySummary>(`${PAY_REPORTS_URL}/summary`, { params });
  }

  getBreakdown(
    dimension: BreakdownDimension,
    criteria: PayReportCriteria,
  ): Observable<GroupBreakdown[]> {
    const params = new HttpParams({ fromObject: { ...activeFilters(criteria), dimension } });
    return this.http.get<GroupBreakdown[]>(`${PAY_REPORTS_URL}/breakdown`, { params });
  }
}

function activeFilters(criteria: PayReportCriteria): Record<string, string> {
  return Object.fromEntries(
    Object.entries(criteria).filter((filter): filter is [string, string] => Boolean(filter[1])),
  );
}
