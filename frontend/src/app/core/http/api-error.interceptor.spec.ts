import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { NotificationService } from '../notifications/notification.service';
import { ApiError } from './api-error';
import { apiErrorInterceptor } from './api-error.interceptor';

describe('api error interceptor', () => {
  let httpClient: HttpClient;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([apiErrorInterceptor])),
        provideHttpClientTesting(),
        { provide: NotificationService, useValue: { notifyError: () => {} } },
      ],
    });
    httpClient = TestBed.inject(HttpClient);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('carries the field named by a validation errors problem detail', async () => {
    const result = firstValueFrom(httpClient.get('/api/employees')).catch((error) => error as ApiError);

    httpTesting.expectOne('/api/employees').flush(
      {
        code: 'VALIDATION_FAILED',
        message: 'Request validation failed',
        errors: [{ field: 'managerId', message: 'Manager does not exist' }],
      },
      { status: 422, statusText: 'Unprocessable Entity' },
    );

    const error = await result;
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).field).toBe('managerId');
  });

  it('carries the field named directly on an invalid-reference problem detail', async () => {
    const result = firstValueFrom(httpClient.get('/api/employees')).catch((error) => error as ApiError);

    httpTesting.expectOne('/api/employees').flush(
      { code: 'INVALID_REFERENCE', message: 'Manager does not exist', field: 'managerId' },
      { status: 422, statusText: 'Unprocessable Entity' },
    );

    const error = await result;
    expect((error as ApiError).field).toBe('managerId');
  });

  it('leaves the field undefined when the problem detail names none', async () => {
    const result = firstValueFrom(httpClient.get('/api/employees')).catch((error) => error as ApiError);

    httpTesting
      .expectOne('/api/employees')
      .flush({ code: 'NOT_FOUND', message: 'Employee 404 was not found' }, { status: 404, statusText: 'Not Found' });

    const error = await result;
    expect((error as ApiError).field).toBeUndefined();
  });
});
