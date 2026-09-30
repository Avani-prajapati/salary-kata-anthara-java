import { CurrencyPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { catchError, debounceTime, map, Observable, of, switchMap } from 'rxjs';
import { CountryNamePipe } from '../../core/formatting/country-name.pipe';
import { EnumLabelPipe } from '../../core/formatting/enum-label.pipe';
import {
  COUNTRY_CODES,
  CountryCode,
  Department,
  DEPARTMENTS,
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
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatTableModule,
    MatSortModule,
    MatPaginatorModule,
    EnumLabelPipe,
    CountryNamePipe,
  ],
  templateUrl: './employee-directory-page.component.html',
  styleUrl: './employee-directory-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmployeeDirectoryPageComponent {
  private readonly directory = inject(EmployeeDirectoryService);
  private readonly formBuilder = inject(NonNullableFormBuilder);

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

  protected readonly pageIndex = computed(() => this.query().pageIndex);
  protected readonly pageSize = computed(() => this.query().pageSize);
  protected readonly rows = computed(() => this.loadedResult()?.items ?? []);
  protected readonly totalElements = computed(() => this.loadedResult()?.totalElements ?? 0);
  protected readonly emptyTableMessage = computed(
    () => EMPTY_TABLE_MESSAGES[this.outcome()?.kind ?? 'pending'],
  );

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
