import { CurrencyPipe, DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  inject,
  input,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { switchMap } from 'rxjs';
import { SalaryHistoryEntry } from '../../../core/models/employee.model';
import { CompensationService } from './compensation.service';

@Component({
  selector: 'app-salary-history-list',
  imports: [CurrencyPipe, DatePipe],
  templateUrl: './salary-history-list.component.html',
  styleUrl: './salary-history-list.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SalaryHistoryListComponent {
  private readonly compensationService = inject(CompensationService);

  readonly employeeId = input.required<number>();

  readonly history = signal<SalaryHistoryEntry[]>([]);

  constructor() {
    toObservable(this.employeeId)
      .pipe(
        switchMap((id) => this.compensationService.listHistory(id)),
        takeUntilDestroyed(),
      )
      .subscribe((entries) => {
        this.history.set(entries);
      });
  }
}
