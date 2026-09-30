import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { BreakdownDimension, GroupBreakdown, PaySummary } from '../../core/models/pay-report.model';

const PAY_REPORTS_URL = '/api/reports/pay';

@Injectable({ providedIn: 'root' })
export class PayInsightsService {
  private readonly http = inject(HttpClient);

  getSummary(): Observable<PaySummary> {
    return this.http.get<PaySummary>(`${PAY_REPORTS_URL}/summary`);
  }

  getBreakdown(dimension: BreakdownDimension): Observable<GroupBreakdown[]> {
    return this.http.get<GroupBreakdown[]>(`${PAY_REPORTS_URL}/breakdown`, {
      params: { dimension },
    });
  }
}
