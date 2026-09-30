import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { EmployeeCreateRequest, EmployeeSummary } from '../../core/models/employee.model';

@Injectable({ providedIn: 'root' })
export class EmployeeOnboardingService {
  private readonly http = inject(HttpClient);

  create(request: EmployeeCreateRequest): Observable<EmployeeSummary> {
    return this.http.post<EmployeeSummary>('/api/employees', request);
  }
}
