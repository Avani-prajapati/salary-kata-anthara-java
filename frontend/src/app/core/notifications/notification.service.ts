import { inject, Injectable } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';

const ERROR_DISPLAY_MILLISECONDS = 6000;

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly snackBar = inject(MatSnackBar);

  notifyError(message: string): void {
    this.snackBar.open(message, 'Dismiss', {
      duration: ERROR_DISPLAY_MILLISECONDS,
      politeness: 'assertive',
    });
  }
}
