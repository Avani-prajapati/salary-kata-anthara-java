import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { EmployeeDetail, EmployeeProfileUpdateRequest } from '../../core/models/employee.model';

@Injectable({ providedIn: 'root' })
export class EmployeeDetailService {
  private readonly http = inject(HttpClient);

  getById(id: number): Observable<EmployeeDetail> {
    return this.http.get<EmployeeDetail>(`/api/employees/${id}`);
  }

  updateProfile(id: number, request: EmployeeProfileUpdateRequest): Observable<EmployeeDetail> {
    return this.http.patch<EmployeeDetail>(`/api/employees/${id}`, request);
  }
}
