import { CurrencyPipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDrawer, MatSidenavModule } from '@angular/material/sidenav';
import { catchError, map, Observable, of, startWith, switchMap } from 'rxjs';
import { CountryNamePipe } from '../../core/formatting/country-name.pipe';
import { EnumLabelPipe } from '../../core/formatting/enum-label.pipe';
import { EmployeeDetail } from '../../core/models/employee.model';
import { EmployeeDetailService } from './employee-detail.service';
import { EmployeeProfileFormComponent } from './employee-profile-form.component';

type PanelState =
  | { kind: 'idle' }
  | { kind: 'loading' }
  | { kind: 'loaded'; employee: EmployeeDetail }
  | { kind: 'not-found' };

type PanelMode = 'view' | 'edit';

@Component({
  selector: 'app-employee-detail-panel',
  imports: [
    CurrencyPipe,
    MatSidenavModule,
    MatButtonModule,
    EnumLabelPipe,
    CountryNamePipe,
    EmployeeProfileFormComponent,
  ],
  templateUrl: './employee-detail-panel.component.html',
  styleUrl: './employee-detail-panel.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmployeeDetailPanelComponent {
  private readonly employeeDetailService = inject(EmployeeDetailService);

  readonly employeeId = input<number | null>(null);
  readonly triggerElement = input<HTMLElement | null>(null);
  readonly closed = output<void>();
  readonly profileUpdated = output<EmployeeDetail>();

  protected readonly drawer = viewChild.required(MatDrawer);
  protected readonly panelTitleId = 'employee-detail-panel-title';

  protected readonly state = signal<PanelState>({ kind: 'idle' });
  protected readonly mode = signal<PanelMode>('view');

  protected readonly isOpen = computed(() => this.state().kind !== 'idle');
  protected readonly panelTitle = computed(() => panelTitleFor(this.state()));

  constructor() {
    toObservable(this.employeeId)
      .pipe(
        switchMap((id) => this.fetchOutcome(id)),
        takeUntilDestroyed(),
      )
      .subscribe((outcome) => {
        this.state.set(outcome);
        this.mode.set('view');
      });

    effect(() => {
      if (this.isOpen()) {
        this.triggerElement()?.focus();
        this.drawer().open();
      }
    });
  }

  protected onOpenedChange(opened: boolean): void {
    if (!opened) {
      this.closed.emit();
    }
  }

  protected startEditing(): void {
    this.mode.set('edit');
  }

  protected cancelEditing(): void {
    this.mode.set('view');
  }

  protected onProfileSaved(updated: EmployeeDetail): void {
    this.state.set({ kind: 'loaded', employee: updated });
    this.mode.set('view');
    this.profileUpdated.emit(updated);
  }

  private fetchOutcome(id: number | null): Observable<PanelState> {
    if (id === null) {
      return of<PanelState>({ kind: 'idle' });
    }
    return this.employeeDetailService.getById(id).pipe(
      map((employee): PanelState => ({ kind: 'loaded', employee })),
      catchError(() => of<PanelState>({ kind: 'not-found' })),
      startWith<PanelState>({ kind: 'loading' }),
    );
  }
}

function panelTitleFor(state: PanelState): string {
  switch (state.kind) {
    case 'loaded':
      return state.employee.name;
    case 'not-found':
      return 'Employee not found';
    default:
      return 'Employee detail';
  }
}
