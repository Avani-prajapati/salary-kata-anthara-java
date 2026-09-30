import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { NotificationService } from '../notifications/notification.service';
import { ApiError } from './api-error';

interface ProblemDetailBody {
  code: string;
  message: string;
}

const UNREACHABLE_SERVER_MESSAGE = 'The server could not be reached. Please try again.';

export const apiErrorInterceptor: HttpInterceptorFn = (request, next) => {
  const notifications = inject(NotificationService);
  return next(request).pipe(
    catchError((response: HttpErrorResponse) => {
      const apiError = toApiError(response);
      notifications.notifyError(apiError.message);
      return throwError(() => apiError);
    }),
  );
};

function toApiError(response: HttpErrorResponse): ApiError {
  const body: unknown = response.error;
  return isProblemDetail(body)
    ? new ApiError(response.status, body.code, body.message)
    : new ApiError(response.status, 'UNEXPECTED_ERROR', UNREACHABLE_SERVER_MESSAGE);
}

function isProblemDetail(body: unknown): body is ProblemDetailBody {
  return (
    typeof body === 'object' &&
    body !== null &&
    typeof (body as Partial<ProblemDetailBody>).code === 'string' &&
    typeof (body as Partial<ProblemDetailBody>).message === 'string'
  );
}
