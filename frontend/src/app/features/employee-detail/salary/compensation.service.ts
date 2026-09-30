import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { SalaryChangeRequest, SalaryHistoryEntry } from '../../../core/models/employee.model';

@Injectable({ providedIn: 'root' })
export class CompensationService {
  private readonly http = inject(HttpClient);

  changeSalary(employeeId: number, request: SalaryChangeRequest): Observable<SalaryHistoryEntry> {
    return this.http.post<SalaryHistoryEntry>(`/api/employees/${employeeId}/salary-changes`, request);
  }
}
