import { CurrencyPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { catchError, debounceTime, map, Observable, of, switchMap } from 'rxjs';
import { CountryNamePipe } from '../../core/formatting/country-name.pipe';
import { EnumLabelPipe } from '../../core/formatting/enum-label.pipe';
import { EmployeeDetailPanelComponent } from '../employee-detail/employee-detail-panel.component';
import { AddEmployeeDialogComponent } from '../employee-onboarding/add-employee-dialog.component';
import {
  COUNTRY_CODES,
  CountryCode,
  Department,
  DEPARTMENTS,
  EmployeeDetail,
  EmployeeSummary,
  JOB_LEVELS,
  JobLevel,
} from '../../core/models/employee.model';
import { PagedResponse } from '../../core/models/paged-response.model';
import {
  DirectorySort,
  DirectorySortColumn,
  EmployeeDirectoryService,
  EmployeeSearchCriteria,
} from './employee-directory.service';

type SearchOutcome =
  { kind: 'loaded'; result: PagedResponse<EmployeeSummary> } | { kind: 'failed' };

interface DirectoryQuery {
  criteria: EmployeeSearchCriteria;
  pageIndex: number;
  pageSize: number;
  sort: DirectorySort;
}

const FILTER_DEBOUNCE_MILLISECONDS = 300;
const DEFAULT_PAGE_SIZE = 25;

const EMPTY_TABLE_MESSAGES: Record<SearchOutcome['kind'] | 'pending', string> = {
  pending: 'Loading employees…',
  loaded: 'No employees match your search or filters.',
  failed: 'Employees could not be loaded. Please try again.',
};

@Component({
  selector: 'app-employee-directory-page',
  imports: [
    ReactiveFormsModule,
    CurrencyPipe,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatTableModule,
    MatSortModule,
    MatPaginatorModule,
    EnumLabelPipe,
    CountryNamePipe,
    EmployeeDetailPanelComponent,
  ],
  templateUrl: './employee-directory-page.component.html',
  styleUrl: './employee-directory-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmployeeDirectoryPageComponent {
  private readonly directory = inject(EmployeeDirectoryService);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly dialog = inject(MatDialog);

  protected readonly departments = DEPARTMENTS;
  protected readonly jobLevels = JOB_LEVELS;
  protected readonly countryCodes = COUNTRY_CODES;
  protected readonly displayedColumns: DirectorySortColumn[] = [
    'name',
    'department',
    'jobLevel',
    'countryCode',
    'salaryAmount',
  ];
  protected readonly pageSizeOptions = [10, 25, 50, 100];

  protected readonly filters = this.formBuilder.group({
    name: this.formBuilder.control(''),
    department: this.formBuilder.control<Department | null>(null),
    countryCode: this.formBuilder.control<CountryCode | null>(null),
    jobLevel: this.formBuilder.control<JobLevel | null>(null),
  });

  private readonly query = signal<DirectoryQuery>({
    criteria: this.filters.getRawValue(),
    pageIndex: 0,
    pageSize: DEFAULT_PAGE_SIZE,
    sort: { column: 'name', direction: 'asc' },
  });

  private readonly outcome = toSignal(
    toObservable(this.query).pipe(switchMap((query) => this.search(query))),
  );

  private readonly rowOverrides = signal<ReadonlyMap<number, EmployeeSummary>>(new Map());

  protected readonly pageIndex = computed(() => this.query().pageIndex);
  protected readonly pageSize = computed(() => this.query().pageSize);
  protected readonly rows = computed(() => {
    const overrides = this.rowOverrides();
    return (this.loadedResult()?.items ?? []).map((row) => overrides.get(row.id) ?? row);
  });
  protected readonly totalElements = computed(() => this.loadedResult()?.totalElements ?? 0);
  protected readonly emptyTableMessage = computed(
    () => EMPTY_TABLE_MESSAGES[this.outcome()?.kind ?? 'pending'],
  );

  protected readonly selectedEmployeeId = signal<number | null>(null);
  protected readonly selectedRow = signal<HTMLElement | null>(null);

  constructor() {
    this.filters.valueChanges
      .pipe(debounceTime(FILTER_DEBOUNCE_MILLISECONDS), takeUntilDestroyed())
      .subscribe(() =>
        this.query.update((query) => ({
          ...query,
          criteria: this.filters.getRawValue(),
          pageIndex: 0,
        })),
      );
  }

  protected onSortChange(sort: Sort): void {
    const direction = sort.direction === 'desc' ? 'desc' : 'asc';
    this.query.update((query) => ({
      ...query,
      sort: { column: sort.active as DirectorySortColumn, direction },
      pageIndex: 0,
    }));
  }

  protected onPageChange(page: PageEvent): void {
    this.query.update((query) => ({
      ...query,
      pageIndex: page.pageIndex,
      pageSize: page.pageSize,
    }));
  }

  protected trackById(_: number, employee: EmployeeSummary): number {
    return employee.id;
  }

  protected onRowSelected(employee: EmployeeSummary, row: EventTarget | null): void {
    this.selectedRow.set(row instanceof HTMLElement ? row : null);
    this.selectedEmployeeId.set(employee.id);
  }

  protected onDetailPanelClosed(): void {
    this.selectedEmployeeId.set(null);
    this.selectedRow.set(null);
  }

  protected onProfileUpdated(updated: EmployeeDetail): void {
    this.overrideRow(updated);
  }

  protected onSalaryChanged(updated: EmployeeDetail): void {
    this.overrideRow(updated);
  }

  protected onAddEmployee(): void {
    this.dialog
      .open(AddEmployeeDialogComponent, { width: '480px' })
      .afterClosed()
      .subscribe((created) => {
        if (created) {
          this.refreshCurrentSearch();
        }
      });
  }

  private refreshCurrentSearch(): void {
    this.query.update((query) => ({ ...query }));
  }

  private overrideRow(updated: EmployeeDetail): void {
    const row: EmployeeSummary = {
      id: updated.id,
      name: updated.name,
      department: updated.department,
      jobLevel: updated.jobLevel,
      countryCode: updated.countryCode,
      salaryAmount: updated.salaryAmount,
      salaryCurrency: updated.salaryCurrency,
    };
    this.rowOverrides.update((overrides) => new Map(overrides).set(row.id, row));
  }

  private loadedResult(): PagedResponse<EmployeeSummary> | undefined {
    const outcome = this.outcome();
    return outcome?.kind === 'loaded' ? outcome.result : undefined;
  }

  private search(query: DirectoryQuery): Observable<SearchOutcome> {
    return this.directory.search(query.criteria, query.pageIndex, query.pageSize, query.sort).pipe(
      map((result): SearchOutcome => ({ kind: 'loaded', result })),
      catchError(() => of<SearchOutcome>({ kind: 'failed' })),
    );
  }
}
