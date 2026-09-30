# Plan: 001-employee-salary-management

> Spec: `docs/specs/001-employee-salary-management-spec.md` · Context: `docs/specs/001-employee-salary-management-context.md` · Authored: 2026-09-30
> Risk: MODERATE (internal HR tool, sensitive salary/PII data, no regulated pack active, greenfield so easy to iterate) · Hard floors: slice 1 (migrations) · Fabric standards: reachable

---

# Part 1 — Review this

*Everything a reviewer needs to approve or redirect the approach. If you only read one part, read this one.*

## What we're doing

Building the salary-kata app end-to-end per [spec 001](../specs/001-employee-salary-management-spec.md): a greenfield Spring Boot 3 + Angular v17+ application across 8 slices — an employee directory, per-employee salary management with an append-only change history, new-hire onboarding, and an org-wide pay-insights page — backed by a deterministic 10,000-employee SQLite seed.

The core approach is a **modular monolith** ([spec §7.2](../specs/001-employee-salary-management-spec.md#72-architectural-style)) with `employee`, `compensation`, and `reporting` domain modules, forced by two constraints: a single HR-manager persona with no auth means independently-deployable services buy nothing, and SQLite (the kata's own example database) has no portable median aggregate, which pushes the insights slice's grouped-median computation into the service layer rather than SQL (Decision 3).

One deliberate exception to the obvious module split: the **`compensation` module owns employee creation**, not `employee` — because onboarding and a salary change are the same category of "write to `SalaryHistory`" operation, and giving `employee` a dependency back on `compensation` would create a module cycle (Decision 1).

Slice 1 is unusually large because nothing precedes it: it carries every one-time scaffolding concern (backend project, Angular workspace, schema migrations, the seed script) alongside the first user-visible feature. Every later slice is purely additive on top of it.

## How it breaks down

Slices follow [spec §5](../specs/001-employee-salary-management-spec.md#5-slices)'s DAG order exactly: slice 1 establishes the directory (`root`) and all shared infrastructure; slices 2–5 build one employee's detail/edit/salary/history behind that directory's row click; slice 6 adds onboarding from the directory's own button; slices 7–8 build the independent Insights surface from `root`'s nav.

| # | Slice | Delivers | Why this tier | Tier |
|---|-------|----------|---------------|------|
| 1 | [Browse the employee directory](../specs/001-employee-salary-management-spec.md#51-browse-the-employee-directory) | Project scaffolding, schema + Flyway migrations, deterministic 10k seed, paginated/searchable/filterable table | Non-trivial seed-generation algorithm (weighted distributions, acyclic manager assignment) plus first-slice architectural scaffolding; also hard-floored (migrations) | `best` |
| 2 | [View an employee's full detail](../specs/001-employee-salary-management-spec.md#52-view-an-employees-full-detail) | Focus-trapping side-panel showing one employee's profile | Standard read-path CRUD + one non-trivial a11y wiring (focus trap) | `excellent` |
| 3 | [Edit an employee's profile](../specs/001-employee-salary-management-spec.md#53-edit-an-employees-profile) | Update non-salary fields with validation | Standard form + validation service work | `excellent` |
| 4 | [Change an employee's salary](../specs/001-employee-salary-management-spec.md#54-change-an-employees-salary) | Append-only salary-history write + current-salary cache update | Anchors the plan's core invariants ([4.1](../specs/001-employee-salary-management-spec.md#4-invariants)/[4.2](../specs/001-employee-salary-management-spec.md#4-invariants)/[4.4](../specs/001-employee-salary-management-spec.md#4-invariants)) under one transaction; every later slice trusts this is correct | `excellent` |
| 5 | [View an employee's salary history](../specs/001-employee-salary-management-spec.md#55-view-an-employees-salary-history) | Read-only reverse-chronological history list | Pure read passthrough of data slice 4 already writes | `fast` |
| 6 | [Add a new employee](../specs/001-employee-salary-management-spec.md#56-add-a-new-employee) | Composite create (Employee + initial SalaryHistory) | Standard transactional create, reuses slice 4's write path | `excellent` |
| 7 | [View organization-wide pay insights](../specs/001-employee-salary-management-spec.md#57-view-organization-wide-pay-insights) | Headcount + avg/median salary by dept/country/level, per currency | In-memory grouped-median computation across multiple dimensions and currencies, no native SQL support to lean on | `best` |
| 8 | [Filter pay insights](../specs/001-employee-salary-management-spec.md#58-filter-pay-insights-by-department-country-or-role) | Same insights, scoped to a filter combination | Extends slice 7's service/endpoint with optional criteria; no new algorithm | `excellent` |

Slices 2–5 all sit behind the same detail side-panel Entry; slice 5 is deliberately the simplest of the four since it only renders data slice 4 already computed. Slice 8 reuses slice 7's `PayReportCriteria` and aggregation code verbatim rather than inventing a second reporting path.

## Decisions made

**1. The `compensation` module owns employee creation (`hireEmployee`), not `employee`.** "Hire with a starting salary" and "change salary" are the same category of append-only-history write, so `CompensationService` — which already needs `EmployeeRepository` to update the current-salary cache — also creates the `Employee` row. This keeps the dependency direction one-way (`compensation → employee`) for the whole plan. Lands in slice 6. *Rejected: `EmployeeService.create()` calling into `CompensationService`, which would create an `employee ↔ compensation` cycle.*

**2. `Money` is a shared embeddable value object (amount + ISO currency code), never a bare `BigDecimal`/`String` pair.** It encapsulates [invariant 4.3](../specs/001-employee-salary-management-spec.md#4-invariants) ("positive, exact decimal with currency") in one place instead of repeating validation across `Employee.currentSalary` and every `SalaryHistory` row. Lands in slices 1, 4, 6. *Rejected: plain `BigDecimal amount` + `String currencyCode` fields duplicated per entity, which would let one of the three copies drift out of validation.*

**3. Insights aggregates (headcount, average, median — grouped by department/country/job-level, split by currency) are computed in the Java service layer over one bulk projection query, not via SQL `GROUP BY` + window functions.** SQLite has no portable median aggregate; at 10,000 total rows, one `SELECT` plus an in-memory `Stream` grouping is simple, unit-testable, and fast enough, and the browser still only ever receives pre-aggregated JSON — [AC 5.7.5](../specs/001-employee-salary-management-spec.md#57-view-organization-wide-pay-insights)'s "server-side aggregation, never raw rows to the client" NFR is unaffected. Lands in slices 7, 8. *Rejected: SQL percentile workarounds (`ROW_NUMBER` window-function tricks), which SQLite supports only partially and which are far harder to unit-test than a Java `Stream` pipeline.*

**4. The seed generator's manager assignment is acyclic by construction.** Employees are generated in descending job-level order; each employee's `managerId` may only reference an already-generated (necessarily equal-or-more-senior) employee. This satisfies [spec §9.4](../specs/001-employee-salary-management-spec.md#9-deferred-follow-ups-and-accepted-limits)'s accepted limit — no runtime cycle detection — by making cycles structurally impossible in the data the spec deliberately chose not to validate against. Lands in slice 1. *Rejected: generating employees in arbitrary order and validating acyclicity afterward, which needs the very cycle-detection logic the spec deferred.*

**5. `CountryCode` and `Money.currencyCode` are independent types, even though the seed generator derives one from the other for realism.** A real HR manager can record a salary in any currency for any employee (spec's multi-currency decision) — the seed's country→currency mapping is a generation heuristic, not a system constraint. Lands in slice 1 (seed), slices 4/6 (write paths leave currency free-form). *Rejected: hard-coding country→currency as a validated pair at the API boundary, which would block legitimate edge cases (e.g., an expatriate paid in a different currency) the spec never asked to exclude.*

**6. The spec's "job title/level" field is modeled as one closed-set `JobLevel` enum, not a free-text `jobTitle` string.** [Context §4b](../specs/001-employee-salary-management-context.md) flagged this naming ambiguity (spec [§3.2](../specs/001-employee-salary-management-spec.md#32-kinds-of-data) vs. [AC 5.7.4](../specs/001-employee-salary-management-spec.md#57-view-organization-wide-pay-insights)) as unresolved; this plan resolves it here, before slice 1, rather than leaving slice 1's builder to guess. A closed set is required for [slice 5.7](../specs/001-employee-salary-management-spec.md#57-view-organization-wide-pay-insights)'s "breakdown by job level" to group meaningfully — free text would fragment into thousands of near-duplicate values across 10,000 employees. It is still a single field (per the context file's "do not introduce a second field" note), just typed as an enum. Lands in slice 1; every later slice inherits the name `jobLevel`. *Rejected: free-text `jobTitle`, which reporting could not group on without fuzzy matching.*

## Where this could go wrong

- **The median-across-currency-and-dimension computation (Decision 3) is the single riskiest logic in the plan** — an off-by-one in the sort/middle-index math silently produces a plausible-looking wrong number. Guarded by slice 7's Quality checks and a dedicated parameterized unit-test table over odd/even group sizes.
- **Slice 4's "exactly one current salary" invariant ([4.1](../specs/001-employee-salary-management-spec.md#4-invariants)/[4.4](../specs/001-employee-salary-management-spec.md#4-invariants)) could be violated by two concurrent salary changes for the same employee** racing on the read-modify-write of the cached `currentSalary` field. Guarded by slice 4's Quality checks (single-transaction assertion); accepted as a low-likelihood risk for a single-HR-manager tool (spec risk hint is MODERATE, no concurrent-writer scenario in scope) rather than eliminated with optimistic locking.
- **[Spec §9.4](../specs/001-employee-salary-management-spec.md#9-deferred-follow-ups-and-accepted-limits) (no manager-cycle detection) sits directly next to slice 3's manager-reference validation** ([AC 5.3.3](../specs/001-employee-salary-management-spec.md#53-edit-an-employees-profile)) — a builder "helpfully" adding cycle detection while implementing the existence check would be scope creep into a spec-accepted limit. Slice 3's Quality checks explicitly assert this stays untouched.
- **[Spec §9.2](../specs/001-employee-salary-management-spec.md#9-deferred-follow-ups-and-accepted-limits) (no FX conversion) sits directly next to slices 7/8's aggregation logic** — a builder summing across currencies "to give one number" would violate the spec's explicit multi-currency, no-conversion decision. Both slices' Quality checks assert results stay grouped per currency.

## What's out of scope

Per [spec §1](../specs/001-employee-salary-management-spec.md#1-overview--business-context) and [§9](../specs/001-employee-salary-management-spec.md#9-deferred-follow-ups-and-accepted-limits): no authentication/login ([§9.1](../specs/001-employee-salary-management-spec.md#9-deferred-follow-ups-and-accepted-limits)), no cross-currency FX conversion ([§9.2](../specs/001-employee-salary-management-spec.md#9-deferred-follow-ups-and-accepted-limits)), no employee lifecycle/termination workflow ([§9.3](../specs/001-employee-salary-management-spec.md#9-deferred-follow-ups-and-accepted-limits)), no manager-hierarchy cycle detection ([§9.4](../specs/001-employee-salary-management-spec.md#9-deferred-follow-ups-and-accepted-limits)). A builder on slice 3 (manager validation) or slice 6 (onboarding) could plausibly reach for cycle detection or a status field in passing — neither is built here.

---

# Part 2 — Build detail

*Prescriptive detail for the build agent. Reviewers can skim or skip.*

**Confidence markers used below:** `[verified]` = the planner read the code or docs and confirmed it. `[inferred]` = pattern-matched, not confirmed — the build agent should check before assuming. Structural decisions (package layout, class/method names) below are the plan's own authorial decisions, not external facts, and carry no marker — they are to be followed as decided.

## Shared conventions (all slices)

- **Architecture:** Modular monolith ([spec §7.2](../specs/001-employee-salary-management-spec.md#72-architectural-style)). Backend: Spring Boot 3 (Java 21), Maven build (single reviewer-friendly `./mvnw test` command; [inferred — Maven vs. Gradle was unpinned per context §8, this plan picks Maven for its ubiquity in Spring Boot assessments]). Frontend: Angular v17+ standalone app, Angular CLI-scaffolded.
- **Dependency direction:** `compensation → employee` (one-way); `reporting → employee`, `reporting → compensation` (one-way, through their service interfaces only, never their repositories — [context §4c](../specs/001-employee-salary-management-context.md) trap). `common` depends on nothing in the app. No module imports another's repository directly.
- **Module map:**
  - `backend/src/main/java/com/acme/salary/common/` — `Money` embeddable, `ProblemDetail` error handling, `PagedResponse<T>` wrapper, `EntityNotFoundException`, `InvalidReferenceException`. Depends on nothing else in the app.
  - `backend/src/main/java/com/acme/salary/employee/` — `Employee` entity, `Department`/`JobLevel`/`CountryCode`, `EmployeeRepository`, `EmployeeService` (read/search/profile-update only — no create), `EmployeeController`. Depends on `common`.
  - `backend/src/main/java/com/acme/salary/compensation/` — `SalaryHistory` entity, `SalaryHistoryRepository`, `CompensationService` (hire, changeSalary, listHistory), `CompensationController`. Depends on `employee`, `common`.
  - `backend/src/main/java/com/acme/salary/reporting/` — `ReportingService`, `ReportingController`, aggregation DTOs. Depends on `employee`, `common`.
  - `backend/src/main/java/com/acme/salary/seed/` — `SeedDataGenerator` (pure, no Spring annotations), `SeedDataRunner` (`@Profile("seed")`). Depends on `employee`, `compensation`.
  - `backend/src/main/resources/db/migration/` — Flyway `V1`/`V2`/`V3` SQL. Flyway's default scan location is `classpath:db/migration` [verified — Flyway reference documentation; matches Spring Boot's `spring.flyway.locations` default, no override needed].
  - `frontend/src/app/core/` — `api-error.interceptor.ts` (functional `HttpInterceptorFn`), shared TypeScript models mirroring the backend DTOs.
  - `frontend/src/app/features/employee-directory/`, `employee-detail/` (with a `salary/` sub-area), `employee-onboarding/`, `pay-insights/` — one lazy-loaded (`loadComponent`) feature area per slice group.
- **Lexicon (supersedes [context §4b](../specs/001-employee-salary-management-context.md)'s tentative names — do not rename):** `Employee`, `SalaryHistory`, `Money` (amount + currencyCode), `Department` (enum), `JobLevel` (enum — see Decision 6, not `jobTitle`), `CountryCode` (validated ISO alpha-2), `PagedResponse<T>`, `ProblemDetail` (RFC 9457, `code`+`message`, per [context §4b](../specs/001-employee-salary-management-context.md) slice 5.2's fixed error-body shape), `PayReportCriteria`, `CurrencyAggregate`. Route `POST /api/employees/{id}/salary-changes` is fixed verbatim by [spec §5.4](../specs/001-employee-salary-management-spec.md#54-change-an-employees-salary)'s sequence diagram; every other route below is this plan's own fixed convention.
- **Tests:** JUnit Jupiter + AssertJ + Mockito; unit tests for services constructed via `new` (no Spring context), passing fakes/mocks only for real boundaries (repositories); `@WebMvcTest` + `@MockitoBean` for controllers; `@DataJpaTest` for repository/specification queries run against a **real temporary-file SQLite database**, not H2 — `@AutoConfigureTestDatabase(replace = Replace.NONE)` with a per-test-class `spring.datasource.url=jdbc:sqlite:${java.io.tmpdir}/<random>.db`, so repository tests exercise the same database engine as production. A fixed `java.time.Clock` bean is injected everywhere "now" matters. Frontend: whatever `ng test` scaffolds for the installed Angular CLI version (Jasmine assertions either way) [inferred — Karma vs. a newer default test runner depends on the exact CLI minor version, not verifiable without web access this session; genuine coder latitude, see slice 1's "Open at build time"], `provideHttpClientTesting()` for service tests, `TestBed`-rendered component tests for form validity and table rendering.

---

## Slice 1 — Browse the employee directory

**Key decision:** Establishes the whole backend/frontend skeleton in one slice since nothing precedes it: Maven project, Flyway schema, the deterministic seed generator (Decision 4, Decision 6), and the first controller/service/repository/Angular-feature quadruple that every later slice imitates. `Money` (Decision 2) is defined here for its first use as a display column.

**Structure**

- `backend/pom.xml` (new) — Spring Boot 3.3.x parent; dependencies: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `flyway-core`, `org.xerial:sqlite-jdbc` [verified — the standard actively-maintained SQLite JDBC driver], Hibernate's SQLite community dialect, `spring-boot-starter-test`. **Open at build time:** confirm the exact Hibernate community-dialects artifact (`org.hibernate.orm:hibernate-community-dialects`) and Flyway-SQLite compatibility versions for the Spring Boot 3.3.x/Hibernate 6.x BOM in use — this session has no web access to verify current version numbers; probe by running `./mvnw dependency:tree` after adding the dependency and confirm the dialect class resolves.
- `backend/src/main/java/com/acme/salary/SalaryKataApplication.java` (new) — `@SpringBootApplication` main class.
- `backend/src/main/resources/application.yml` (new) — `spring.jpa.open-in-view: false`; `spring.data.web.pageable.max-page-size: 100`; `spring.data.web.pageable.default-page-size: 25`; datasource pointing at a local SQLite file.
- `backend/src/main/resources/db/migration/V1__create_employees_table.sql`, `V2__create_salary_history_table.sql`, `V3__add_indexes.sql` (new) — `employees` table (id, name, department, job_level, country_code, hire_date, manager_id nullable FK, salary_amount, salary_currency, created_at, updated_at); `salary_history` table (id, employee_id FK, amount, currency, effective_date, recorded_at); indexes on `department`, `country_code`, `job_level`, `manager_id`, `salary_history.employee_id`.
- `backend/src/main/java/com/acme/salary/common/money/Money.java` (new) — `@Embeddable record`-like value class: `BigDecimal amount`, `String currencyCode`. Static factory `Money.of(BigDecimal amount, String currencyCode): Money`, throws `IllegalArgumentException` when `amount <= 0` or `currencyCode` is not a 3-letter code (defense-in-depth per [invariant 4.3](../specs/001-employee-salary-management-spec.md#4-invariants); the DTO-level `@DecimalMin`/`@Size` constraints produce the client-facing `422`).
- `backend/src/main/java/com/acme/salary/common/error/{ProblemDetailAdvice,EntityNotFoundException,InvalidReferenceException}.java` (new) — `ProblemDetailAdvice extends ResponseEntityExceptionHandler`, `@RestControllerAdvice`; handles `MethodArgumentNotValidException` (→ 422, field errors), `EntityNotFoundException` (→ 404), `InvalidReferenceException` (→ 422, named field), and `IllegalArgumentException` from `Money`/value-object validation (→ 422). Every body carries `code` + `message` (fixed by [context §4b](../specs/001-employee-salary-management-context.md)).
- `backend/src/main/java/com/acme/salary/common/paging/PagedResponse.java` (new) — `record PagedResponse<T>(List<T> items, int page, int pageSize, long totalElements, int totalPages)`; static `from(Page<T> page): PagedResponse<T>`.
- `backend/src/main/java/com/acme/salary/common/time/ClockConfig.java` (new) — `@Configuration` bean `Clock clock() { return Clock.systemUTC(); }`, so every "now"-dependent collaborator takes an injected `Clock` and tests substitute `Clock.fixed(...)`.
- `backend/src/main/java/com/acme/salary/employee/{Department,JobLevel}.java` (new) — enums. `Department`: `ENGINEERING, PRODUCT, DESIGN, SALES, MARKETING, FINANCE, HUMAN_RESOURCES, OPERATIONS, LEGAL, CUSTOMER_SUPPORT`. `JobLevel`: `INTERN, ENTRY, MID, SENIOR, LEAD, MANAGER, DIRECTOR, VP, EXECUTIVE` (ordered ascending — the seed generator relies on enum ordinal order for "descending seniority" iteration, see Control flow below).
- `backend/src/main/java/com/acme/salary/employee/CountryCode.java` (new) — value class wrapping a validated ISO 3166-1 alpha-2 string against a fixed supported-country allowlist (shared with the seed generator's country-weight table). Static factory `CountryCode.of(String code): CountryCode`, throws `IllegalArgumentException` if unsupported.
- `backend/src/main/java/com/acme/salary/employee/Employee.java` (new) — `@Entity @Table(name = "employees")`: `Long id`, `String name`, `Department department` (`@Enumerated(STRING)`), `JobLevel jobLevel` (`@Enumerated(STRING)`), `String countryCode`, `LocalDate hireDate`, `Long managerId` (nullable), `Money currentSalary` (`@Embedded`), `Instant createdAt`, `Instant updatedAt`.
- `backend/src/main/java/com/acme/salary/employee/dto/{EmployeeSummaryResponse,EmployeeSearchCriteria}.java` (new) — `record EmployeeSummaryResponse(Long id, String name, Department department, JobLevel jobLevel, String countryCode, BigDecimal salaryAmount, String salaryCurrency)`; `record EmployeeSearchCriteria(Optional<String> name, Optional<Department> department, Optional<String> countryCode, Optional<JobLevel> jobLevel)`.
- `backend/src/main/java/com/acme/salary/employee/{EmployeeRepository,EmployeeSpecifications}.java` (new) — `interface EmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee>`. `EmployeeSpecifications`: static methods `nameContains(String): Specification<Employee>`, `hasDepartment(Department)`, `hasCountry(String)`, `hasJobLevel(JobLevel)`, `matching(EmployeeSearchCriteria): Specification<Employee>` (composes the above with `Specification.allOf`).
- `backend/src/main/java/com/acme/salary/employee/EmployeeService.java` (new) — constructor-injected `EmployeeRepository`. Method: `PagedResponse<EmployeeSummaryResponse> search(EmployeeSearchCriteria criteria, Pageable pageable): PagedResponse<EmployeeSummaryResponse>`.
- `backend/src/main/java/com/acme/salary/employee/EmployeeController.java` (new) — `@RestController @RequestMapping("/api/employees")`. `GET /api/employees` → `search(criteria bound from @RequestParam, Pageable pageable)`, `Pageable` capped by `spring.data.web.pageable.max-page-size`.
- `backend/src/main/java/com/acme/salary/seed/{SeedDataGenerator,NamePool,SalaryBandTable,SeedDataRunner}.java` (new) — see Control flow.
- `backend/src/main/resources/application-seed.yml` (new) — `spring.main.web-application-type: none` (seed profile runs as a batch job, not a web server).
- `frontend/` (new) — Angular CLI-scaffolded standalone workspace. `src/app/app.config.ts` (`provideRouter`, `provideHttpClient(withInterceptors([apiErrorInterceptor]))`, Angular Material theme provider), `src/app/app.routes.ts` (`/employees` default, `/insights`), `src/app/app.component.ts` (toolbar shell + nav).
- `frontend/src/app/core/http/api-error.interceptor.ts` (new) — functional `HttpInterceptorFn`; maps a `ProblemDetail` error body to a typed `ApiError` and surfaces it via a shared notification service, so components never parse HTTP errors themselves.
- `frontend/src/app/core/models/employee.model.ts` (new) — `EmployeeSummary`, `Department`, `JobLevel` TS types mirroring the backend DTOs/enums.
- `frontend/src/app/features/employee-directory/` (new) — `employee-directory.routes.ts` (`loadComponent`), `employee-directory-page.component.ts` (+ `.html`/`.scss`, `ChangeDetectionStrategy.OnPush`) hosting a `mat-table` + `mat-paginator` + `mat-sort` and a filter bar (name search, department/country/jobLevel `mat-select`s) built on a typed reactive form (`NonNullableFormBuilder`), `employee-directory.service.ts` — `search(criteria, pageIndex, pageSize, sort): Observable<PagedResponse<EmployeeSummary>>` calling `HttpClient.get('/api/employees', { params })`.

**Collaborators:** `EmployeeController` constructs nothing, calls `EmployeeService`. `EmployeeService` calls `EmployeeRepository` via `JpaSpecificationExecutor.findAll(Specification, Pageable)`. `SeedDataRunner` constructs `SeedDataGenerator` (pure) and calls `EmployeeRepository`/`SalaryHistoryRepository` (batch saves). Frontend: `EmployeeDirectoryPageComponent` calls `EmployeeDirectoryService`, which calls `HttpClient`.

**Control flow:**
- `EmployeeService.search`: build a `Specification<Employee>` from `EmployeeSearchCriteria` via `EmployeeSpecifications.matching`; call `employeeRepository.findAll(spec, pageable)`; map each `Employee` to `EmployeeSummaryResponse`; wrap in `PagedResponse.from`.
- `SeedDataGenerator.generate(count, seed)`: deterministic `java.util.SplittableRandom(seed)`. Iterate `JobLevel` values from most senior (`EXECUTIVE`) to least senior (`INTERN`) in enum-ordinal-reversed order, generating each level's share of `count` per a fixed weight table (skewed toward mid-levels). For each employee: pick name from `NamePool` (weighted index draw), country from a fixed weighted country table, currency implied by that country (Decision 5 — a generation heuristic only), salary from `SalaryBandTable.rangeFor(jobLevel, currencyCode)` with jitter, hire date uniformly within the last N years up to "today" (from the injected `Clock`). Manager assignment: for any level except the most senior, pick `managerId` uniformly from the already-generated employees whose level is strictly more senior (or, if none exist yet at generation start, leave `managerId` null) — this is what makes the hierarchy acyclic by construction (Decision 4), since a manager reference can only ever point to an employee generated in an earlier iteration.
- `SeedDataRunner.run`: guard — if `employeeRepository.count() > 0`, log and exit without inserting (idempotency, [context §4c](../specs/001-employee-salary-management-context.md) trap); else generate 10,000 `Employee` + paired initial `SalaryHistory` records and batch-save (batch size ~500) via both repositories, in the same order the manager-acyclic generation produced (so `managerId` FKs always resolve).
- `EmployeeDirectoryPageComponent`: filter-form `valueChanges` (debounced) and paginator/sort events all funnel into one `search()` call to the service; renders an explicit "no employees match" empty state when `items` is empty ([AC 5.1.7](../specs/001-employee-salary-management-spec.md#51-browse-the-employee-directory)) rather than a blank table.

**Tests:**
- New file `EmployeeServiceTest` (plain unit, no Spring context, `Mockito.mock(EmployeeRepository.class)`) — covers AC 5.1.2/5.1.3/5.1.4 (search/filter/sort delegate correctly to the Specification), 5.1.5 (page size never exceeds the configured cap, asserted via a `Pageable` argument captor).
- New file `EmployeeControllerWebMvcTest` (`@WebMvcTest(EmployeeController.class)`, `@MockitoBean EmployeeService`) — asserts the `GET /api/employees` JSON shape and status.
- New file `EmployeeRepositorySpecificationDataJpaTest` (`@DataJpaTest` against a temp-file SQLite) — asserts `EmployeeSpecifications` produce correct filtered/sorted results against a handful of seeded rows (not the full 10k).
- New file `SeedDataGeneratorTest` (plain unit, pure function) — asserts: generates exactly N records for a given count; identical output across two runs with the same seed (determinism); no `managerId` ever references an employee generated later in the same run (acyclic-by-construction assertion, covers Decision 4); every generated `countryCode`/`department`/`jobLevel` is one of the allowed enum/allowlist values.
- New file `SeedDataRunnerTest` (`@DataJpaTest` or a thin integration test) — running it twice against the same fresh database inserts exactly 10,000 rows once, not 20,000 (AC 5.1.6 idempotency).
- Frontend: new spec file `employee-directory-page.component.spec.ts` — empty-result state renders the "no employees match" message (AC 5.1.7); filter form emits a search call with the right criteria.

**Standards that bite here:** `Clean Code: SRP` (SeedDataGenerator stays pure, no Spring/DB concerns); [spec §6.1.7](../specs/001-employee-salary-management-spec.md#61-spring-boot-backend-standards) (paginate + cap list endpoints); [spec §6.1.9](../specs/001-employee-salary-management-spec.md#61-spring-boot-backend-standards) (Flyway, no `ddl-auto=update`); [spec §6.6.1](../specs/001-employee-salary-management-spec.md#66-sonarjava-violations-java) (Specification API, never string-concatenated JPQL); Accessibility Checklist: *"All inputs in a form are associated with a corresponding `<label>` element"*, *"Ensure that controls have `:focus` states"*, *"Use landmark elements to indicate important content regions"* (one `<main>` on the directory page).

**Quality checks**

- No unbounded list response: every `GET /api/employees` response's `items.size()` is ≤ the configured max page size, regardless of the 10,000-row dataset. (AC 5.1.5)
- **Seed determinism and acyclicity: two `SeedDataGenerator.generate(10_000, sameSeed)` calls produce byte-identical output, and no employee's `managerId` references an employee generated after it.** (AC 5.1.6, Decision 4 — highest-risk assertion in this slice)
- Re-running the seed against a non-empty database is a no-op (row count unchanged). (AC 5.1.6)
- Empty search/filter results render the explicit empty state, not a blank table or a thrown error. (AC 5.1.7)
- Directory table, filter controls, sort headers, and paginator are all reachable via Tab with a visible focus style. (AC 5.1.8)

**Verification (slice complete when these pass)**

1. **Tests.** Run the full test suite — all tests green.
2. **Migrations.** Apply `V1`–`V3` against a fresh SQLite file; they apply without error, and `SeedDataRunner` under the `seed` profile populates exactly 10,000 rows.
3. **User-facing flow.** Load `/employees`; confirm the table renders seeded rows, search narrows by name, department/country/jobLevel filters narrow correctly, sort toggles ascending/descending, pagination moves through pages, and an impossible filter combination shows the empty state.
4. **Accessibility.** Tab through the filter bar, table headers, and paginator; confirm a visible focus indicator on each and that DOM tab order matches visual order.

**Open at build time (pom.xml dependency versions):** the exact Hibernate community-dialects artifact coordinates and Flyway/SQLite compatibility versions for the chosen Spring Boot 3.3.x BOM — resolve via `./mvnw dependency:tree` and the dependency's own POM once the project is scaffolded, since this session had no web access to verify current released versions.

---

## Slice 2 — View an employee's full detail

**Key decision:** A straightforward read-path extension of slice 1's `EmployeeController`/`Service`, plus the plan's one focus-trap UI pattern that slices 3–6 all reuse for their own panel content.

**Structure**

- `backend/src/main/java/com/acme/salary/employee/dto/EmployeeDetailResponse.java` (new) — `record EmployeeDetailResponse(Long id, String name, Department department, JobLevel jobLevel, String countryCode, LocalDate hireDate, Long managerId, String managerName, BigDecimal salaryAmount, String salaryCurrency)`.
- `backend/src/main/java/com/acme/salary/employee/EmployeeService.java` (modify) — add `EmployeeDetailResponse getById(Long id): EmployeeDetailResponse`, throws `EntityNotFoundException` when absent.
- `backend/src/main/java/com/acme/salary/employee/EmployeeController.java` (modify) — add `GET /api/employees/{id}` → `getById`.
- `frontend/src/app/features/employee-detail/employee-detail-panel.component.ts` (new, + `.html`/`.scss`) — `MatSidenav`/`MatDrawer`-based side panel, `cdkTrapFocus` directive while open, `OnPush`. Receives the selected employee id as an `@Input()` and closes via an `@Output() closed`. On close, restores focus to the triggering row (owned by the parent directory page, which stores a reference to the clicked row element before opening).
- `frontend/src/app/features/employee-detail/employee-detail.service.ts` (new) — `getById(id: number): Observable<EmployeeDetail>`.
- `frontend/src/app/core/models/employee.model.ts` (modify) — add `EmployeeDetail` interface.
- `frontend/src/app/features/employee-directory/employee-directory-page.component.ts` (modify) — add `selectedEmployeeId` state, row-click handler opening the panel, and the focus-restoration hook.

**Collaborators:** `EmployeeController` calls `EmployeeService.getById`, which calls `EmployeeRepository.findById` twice at most (the employee, then optionally the manager by `managerId` — not a loop, so no N+1 concern). `EmployeeDetailPanelComponent` calls `EmployeeDetailService`, which calls `HttpClient`.

**Control flow:** `EmployeeService.getById`: fetch employee or throw `EntityNotFoundException`; if `managerId` present, fetch the manager for `managerName`, else leave it null; map to `EmployeeDetailResponse`. Panel component: on `selectedEmployeeId` becoming non-null, fetch detail and open the drawer; on close (button or `Escape` keydown), emit `closed` and return focus to the stored trigger element reference.

**Tests:**
- Extend `EmployeeServiceTest` (new cases in the existing file) — `getById` returns full detail for an existing id; throws `EntityNotFoundException` for a missing id.
- Extend `EmployeeControllerWebMvcTest` — `GET /api/employees/{id}` returns 200 with the detail shape for a found id; returns 404 `ProblemDetail` with `code`+`message` for a missing id (AC 5.2.5).
- New file `employee-detail-panel.component.spec.ts` — opening sets focus inside the panel; closing (via close button and via simulated `Escape`) restores focus to a stubbed trigger element (AC 5.2.3/5.2.4).

**Standards that bite here:** [spec §6.1.6](../specs/001-employee-salary-management-spec.md#61-spring-boot-backend-standards) (reuse the one `ProblemDetailAdvice` from slice 1, don't add a second); Accessibility Checklist: *"Trap focus within active modals and dialogs"*, *"Elements with role=\"dialog\" ... have accessible names"* (the drawer needs an `aria-labelledby` pointing at its visible title).

**Quality checks**

- Opening the detail panel does not reset the directory's current page, sort, or filter state underneath it. (AC 5.2.1)
- **Closing the panel (button or Escape) always returns keyboard focus to the row that opened it — verified by the browser-verification step, not just a component-level focus-service assertion.** (AC 5.2.3/5.2.4, highest-risk assertion in this slice)
- A non-existent employee id returns a 404 `ProblemDetail` with `code` and `message`, and the UI shows a "not found" message rather than a blank panel. (AC 5.2.5)

**Verification (slice complete when these pass)**

1. **Tests.** Full suite green.
3. **User-facing flow.** Click a directory row; confirm the panel opens with correct data and the table underneath keeps its page/sort/filter state; close via the close control and via Escape; confirm both return focus to the row.
4. **Accessibility.** Confirm focus is trapped inside the open panel (Tab cycles only through panel controls) and the panel announces via `aria-labelledby`.

---

## Slice 3 — Edit an employee's profile

**Key decision:** A standard typed-reactive-form edit flow on top of slice 2's detail panel; the first slice to exercise the shared `ProblemDetailAdvice` for a `422` (slice 2 exercised it for `404`).

**Structure**

- `backend/src/main/java/com/acme/salary/employee/dto/EmployeeProfileUpdateRequest.java` (new) — `record EmployeeProfileUpdateRequest(@NotNull Department department, @NotNull JobLevel jobLevel, @NotBlank String countryCode, @NotNull @PastOrPresent LocalDate hireDate, Long managerId)`.
- `backend/src/main/java/com/acme/salary/employee/EmployeeService.java` (modify) — add `EmployeeDetailResponse updateProfile(Long id, EmployeeProfileUpdateRequest request): EmployeeDetailResponse`, throws `EntityNotFoundException` (id) or `InvalidReferenceException` (bad `managerId`).
- `backend/src/main/java/com/acme/salary/employee/EmployeeController.java` (modify) — add `PATCH /api/employees/{id}` (`@Valid @RequestBody`) → `updateProfile`.
- `frontend/src/app/features/employee-detail/employee-profile-form.component.ts` (new) — typed reactive form (`NonNullableFormBuilder`) for department/jobLevel/country/hireDate/manager, rendered inside `EmployeeDetailPanelComponent`'s edit mode; field-level error display tied to each control via `mat-error`.
- `frontend/src/app/features/employee-detail/employee-detail.service.ts` (modify) — add `updateProfile(id: number, request: EmployeeProfileUpdateRequest): Observable<EmployeeDetail>`.
- `frontend/src/app/features/employee-detail/employee-detail-panel.component.ts` (modify) — add edit-mode toggle; on save success, re-fetch/patch the directory row in place (no full reload).

**Collaborators:** `EmployeeController` calls `EmployeeService.updateProfile`, which calls `EmployeeRepository.findById` (subject employee), `EmployeeRepository.existsById` (manager reference check), then `EmployeeRepository.save`.

**Control flow:** `EmployeeService.updateProfile`: load subject employee or throw `EntityNotFoundException`; if `request.managerId()` non-null, verify `employeeRepository.existsById(managerId)`, else throw `InvalidReferenceException("managerId", ...)`; apply the four/five field updates; set `updatedAt` from the injected `Clock`; save; map to `EmployeeDetailResponse`. **Explicitly does not touch `currentSalary`** — the field is absent from `EmployeeProfileUpdateRequest` entirely, not merely ignored, so there is no code path here that could write it (AC 5.3.5). **Explicitly does not validate the manager reference for cycles** — existence-only check, per [spec §9.4](../specs/001-employee-salary-management-spec.md#9-deferred-follow-ups-and-accepted-limits) (see Quality checks).

**Tests:**
- Extend `EmployeeServiceTest` — valid update persists all five fields; blank/null required field is rejected (surfaced via Bean Validation before the service is even called, so this AC is actually asserted at the controller level — see below); non-existent `managerId` throws `InvalidReferenceException` naming the `managerId` field.
- Extend `EmployeeControllerWebMvcTest` — `PATCH` with a blank required field returns 422 with field-level errors (AC 5.3.2); `PATCH` with an unknown `managerId` returns 422 naming that field (AC 5.3.3); valid `PATCH` returns 200 with the updated detail.
- New spec file `employee-profile-form.component.spec.ts` — required-field validation errors are associated with their inputs (`aria-describedby` / `mat-error` under the control, not a floating message); the salary field never appears as an editable control in this form (structural assertion that the form's field list excludes salary — AC 5.3.5).

**Standards that bite here:** [spec §6.1.4](../specs/001-employee-salary-management-spec.md#61-spring-boot-backend-standards) (Bean Validation at the boundary); Accessibility Checklist: *"All inputs in a form are associated with a corresponding `<label>` element"*, *"Display form input errors in a list above the form after submission"*.

**Quality checks**

- Required-field validation (department, jobLevel, country, hireDate) rejects blanks with field-level errors before any write. (AC 5.3.2)
- An unknown `managerId` is rejected with 422 naming that field; a valid `managerId` is accepted with no cycle check performed. (AC 5.3.3)
- **No code path in this slice's `EmployeeService.updateProfile` or `EmployeeProfileUpdateRequest` can write `currentSalary` — the field is structurally absent from the request DTO, not merely unused.** (AC 5.3.5, highest-risk assertion — this is the boundary between slice 3 and slice 4)
- Saving reflects in the directory table and detail panel without a full page reload. (AC 5.3.4)
- **Untouched: no manager-hierarchy cycle detection is added anywhere in this slice**, consistent with [spec §9.4](../specs/001-employee-salary-management-spec.md#9-deferred-follow-ups-and-accepted-limits) — existence-only check.

**Verification (slice complete when these pass)**

1. **Tests.** Full suite green.
3. **User-facing flow.** Open edit mode from the detail panel, submit a valid change, confirm it reflects immediately in the table and panel; submit with a blank required field and an invalid manager id, confirm both surface field-level errors.
4. **Accessibility.** Tab through the edit form; confirm every field has a visible, programmatically-associated label and error text.

---

## Slice 4 — Change an employee's salary

**Key decision:** The plan's invariant-anchoring slice (Decisions 1, 2). A salary change is always an **insert** into `SalaryHistory`, never an update — the `Employee.currentSalary` field is a denormalized read cache written in the same transaction, per [spec §5.4](../specs/001-employee-salary-management-spec.md#54-change-an-employees-salary)'s sequence diagram.

**Structure**

- `backend/src/main/java/com/acme/salary/compensation/SalaryHistory.java` (new) — `@Entity @Table(name = "salary_history")`: `Long id`, `Long employeeId`, `BigDecimal amount`, `String currencyCode`, `LocalDate effectiveDate`, `Instant recordedAt`.
- `backend/src/main/java/com/acme/salary/compensation/SalaryHistoryRepository.java` (new) — `interface extends JpaRepository<SalaryHistory, Long>`: `List<SalaryHistory> findByEmployeeIdOrderByEffectiveDateDesc(Long employeeId)`; `Optional<SalaryHistory> findTopByEmployeeIdOrderByEffectiveDateDesc(Long employeeId)`.
- `backend/src/main/java/com/acme/salary/compensation/dto/{SalaryChangeRequest,SalaryHistoryEntryResponse}.java` (new) — `record SalaryChangeRequest(@NotNull @DecimalMin(value = "0.01") BigDecimal amount, @NotBlank @Size(min = 3, max = 3) String currencyCode, @NotNull LocalDate effectiveDate)`; `record SalaryHistoryEntryResponse(BigDecimal amount, String currencyCode, LocalDate effectiveDate, Instant recordedAt)`.
- `backend/src/main/java/com/acme/salary/compensation/CompensationService.java` (new) — constructor-injected `SalaryHistoryRepository`, `EmployeeRepository`, `Clock`. Method: `SalaryHistoryEntryResponse changeSalary(Long employeeId, SalaryChangeRequest request): SalaryHistoryEntryResponse`, `@Transactional`.
- `backend/src/main/java/com/acme/salary/compensation/CompensationController.java` (new) — `@RestController @RequestMapping("/api/employees/{employeeId}")`. `POST /api/employees/{employeeId}/salary-changes` (`@Valid @RequestBody`) → `changeSalary`, returns `201 Created`.
- `frontend/src/app/features/employee-detail/salary/salary-change-form.component.ts` (new) — typed reactive form (amount, currency, effective date).
- `frontend/src/app/features/employee-detail/salary/compensation.service.ts` (new) — `changeSalary(employeeId: number, request: SalaryChangeRequest): Observable<SalaryHistoryEntry>`.
- `frontend/src/app/features/employee-detail/employee-detail-panel.component.ts` (modify) — add the "Change salary" action, wired to `salary-change-form`; on success, refreshes the panel's displayed current salary and the directory row.

**Collaborators:** `CompensationController` calls `CompensationService.changeSalary`, which calls `EmployeeRepository.findById` (existence + read), `SalaryHistoryRepository.save` (the insert), then `EmployeeRepository.save` (the denormalized-pointer update) — both writes inside one `@Transactional` method, so they commit or roll back together.

**Control flow:** `CompensationService.changeSalary`: load the employee or throw `EntityNotFoundException`; `Money.of(request.amount(), request.currencyCode())` — throws before any write if amount ≤ 0 (AC 5.4.2, defense-in-depth alongside the DTO's `@DecimalMin`); insert a new `SalaryHistory` row with `recordedAt = clock.instant()`; re-derive the "current" salary as the `SalaryHistory` row with the latest `effectiveDate` across *all* of this employee's history (not necessarily the just-inserted row — effective dates can be backdated, per [invariant 4.4](../specs/001-employee-salary-management-spec.md#4-invariants) and [context §4c](../specs/001-employee-salary-management-context.md)'s trap) via `findTopByEmployeeIdOrderByEffectiveDateDesc`; write that value into `Employee.currentSalary`; save.

**Tests:**
- New file `CompensationServiceTest` (plain unit, mocked repositories, fixed `Clock`) — a valid change inserts a `SalaryHistory` row and updates `Employee.currentSalary` to match; a zero/negative amount throws before any repository interaction (AC 5.4.2); a backdated `effectiveDate` older than an existing later-effective row does **not** become the new current salary (AC 5.4.4/invariant 4.4 — the "latest effective date wins" case).
- Extend `CompensationControllerWebMvcTest` (new file) — valid `POST` returns 201 with the created entry; invalid amount returns 422.
- New file `SalaryHistoryRepositoryDataJpaTest` (`@DataJpaTest`, temp-file SQLite) — `findTopByEmployeeIdOrderByEffectiveDateDesc` picks the latest-effective row, not the latest-inserted one, given rows inserted out of effective-date order.
- Frontend: new spec file `salary-change-form.component.spec.ts` — submitting a non-positive amount is blocked client-side with a visible field error before any HTTP call.

**Standards that bite here:** [spec §6.1.1](../specs/001-employee-salary-management-spec.md#61-spring-boot-backend-standards) (`BigDecimal`, never `double`); [invariant 4.2](../specs/001-employee-salary-management-spec.md#4-invariants) (append-only — no `SalaryHistory` update/delete method exists anywhere in this module); `Clean Code: single responsibility` (the two writes are one transaction, one method, not scattered).

**Quality checks**

- A zero or negative salary amount is rejected with 422 before any `SalaryHistory` row is written. (AC 5.4.2)
- **`SalaryHistoryRepository` exposes no update or delete path for an existing row anywhere in this module — every salary change is an insert.** (AC 5.4.3, invariant 4.2 — highest-risk assertion in this slice, guards the append-only invariant structurally, not just by convention)
- `Employee.currentSalary` always equals the `SalaryHistory` row with the latest `effectiveDate` for that employee, even when changes are recorded out of effective-date order. (AC 5.4.4, invariant 4.4)
- Both the `SalaryHistory` insert and the `Employee.currentSalary` update happen inside one transaction. (invariant 4.1)
- The updated salary reflects immediately in the directory table and detail panel after a successful change. (AC 5.4.6)

**Verification (slice complete when these pass)**

1. **Tests.** Full suite green.
3. **User-facing flow.** From the detail panel, submit a new salary; confirm it appears immediately in the panel and the directory row; submit a zero/negative amount and confirm it's rejected before submission completes.
4. **Accessibility.** Tab through the salary-change form; confirm labelled fields and associated error text.

---

## Slice 5 — View an employee's salary history

**Key decision:** The read-side counterpart to slice 4's write endpoint — pure passthrough of data slice 4 already computed correctly, so this is the plan's simplest slice.

**Structure**

- `backend/src/main/java/com/acme/salary/compensation/CompensationService.java` (modify) — add `List<SalaryHistoryEntryResponse> listHistory(Long employeeId): List<SalaryHistoryEntryResponse>`.
- `backend/src/main/java/com/acme/salary/compensation/CompensationController.java` (modify) — add `GET /api/employees/{employeeId}/salary-history` → `listHistory`.
- `frontend/src/app/features/employee-detail/salary/salary-history-list.component.ts` (new) — renders a reverse-chronological list/table of history entries.
- `frontend/src/app/features/employee-detail/salary/compensation.service.ts` (modify) — add `listHistory(employeeId: number): Observable<SalaryHistoryEntry[]>`.
- `frontend/src/app/features/employee-detail/employee-detail-panel.component.ts` (modify) — add a "History" tab hosting `salary-history-list`.

**Collaborators:** `CompensationController` calls `CompensationService.listHistory`, which calls `SalaryHistoryRepository.findByEmployeeIdOrderByEffectiveDateDesc` (reused verbatim from slice 4, no new query method).

**Control flow:** `CompensationService.listHistory`: fetch all history rows for the employee, already ordered by `effectiveDate` descending by the repository method; map to `SalaryHistoryEntryResponse` list; no filtering, no mutation.

**Tests:**
- Extend `CompensationServiceTest` — a single-record history (a brand-new employee's seed record) returns that one record, not an empty list or an error (AC 5.5.3); multiple records return newest-effective-date-first.
- Extend `CompensationControllerWebMvcTest` — `GET .../salary-history` returns 200 with the ordered list shape.
- Extend `salary-history-list.component.spec.ts`-style frontend spec — renders a single-entry history normally; no edit/delete control exists anywhere in the rendered output (AC 5.5.4, structural check).

**Standards that bite here:** `Clean Code: no reinventing utilities` — reuses slice 4's repository query rather than adding a parallel one.

**Quality checks**

- A single-record history (new employee) renders normally, not as an empty or error state. (AC 5.5.3)
- History is strictly read-only: no edit or delete affordance appears in the rendered UI or is exposed by `CompensationController`. (AC 5.5.4)
- Entries are ordered newest-effective-date-first, consistent with invariant 4.4 — not by `recordedAt`. ([context §4c](../specs/001-employee-salary-management-context.md) trap)

**Verification (slice complete when these pass)**

1. **Tests.** Full suite green.
3. **User-facing flow.** Open the History tab for an employee with one change and for one with several; confirm correct ordering and that the single-entry case isn't mistaken for empty.
4. **Accessibility.** Confirm the history list/table has appropriate semantic structure and no interactive controls masquerading as static content.

---

## Slice 6 — Add a new employee

**Key decision:** The composite create (Decision 1) — `CompensationService`, not `EmployeeService`, owns this endpoint's logic, since it creates both the `Employee` row and the first `SalaryHistory` row in one transaction, reusing slice 4's insert shape.

**Structure**

- `backend/src/main/java/com/acme/salary/compensation/dto/EmployeeCreateRequest.java` (new) — `record EmployeeCreateRequest(@NotBlank String name, @NotNull Department department, @NotNull JobLevel jobLevel, @NotBlank String countryCode, @NotNull @PastOrPresent LocalDate hireDate, Long managerId, @NotNull @DecimalMin("0.01") BigDecimal startingSalaryAmount, @NotBlank @Size(min = 3, max = 3) String startingSalaryCurrency)`.
- `backend/src/main/java/com/acme/salary/compensation/CompensationService.java` (modify) — add `EmployeeSummaryResponse hireEmployee(EmployeeCreateRequest request): EmployeeSummaryResponse`, `@Transactional`. Depends on `EmployeeRepository` (already a constructor dependency) — no new collaborator.
- `backend/src/main/java/com/acme/salary/employee/EmployeeController.java` (modify) — constructor now also takes `CompensationService`; add `POST /api/employees` (`@Valid @RequestBody EmployeeCreateRequest`) → delegates to `compensationService.hireEmployee(request)`, returns `201 Created`. (The REST resource stays `/api/employees` for a coherent client-facing API even though the service-layer owner is `compensation` — see Decision 1.)
- `frontend/src/app/features/employee-onboarding/add-employee-dialog.component.ts` (new) — `MatDialog`-hosted form (typed reactive form) covering all `EmployeeCreateRequest` fields, structurally mirroring slice 3's profile-edit form plus slice 4's salary fields.
- `frontend/src/app/features/employee-onboarding/employee-onboarding.service.ts` (new) — `create(request: EmployeeCreateRequest): Observable<EmployeeSummary>`.
- `frontend/src/app/features/employee-directory/employee-directory-page.component.ts` (modify) — add the "Add employee" toolbar button opening the dialog; on success, re-run the current search so the new row appears under whatever filters/sort are active (not a forced reset).

**Collaborators:** `EmployeeController` now also calls `CompensationService`. `CompensationService.hireEmployee` calls `EmployeeRepository.save` (new `Employee` row) then `SalaryHistoryRepository.save` (initial history row) — both inside one `@Transactional` method, mirroring slice 4's shape.

**Control flow:** `CompensationService.hireEmployee`: `Money.of(startingSalaryAmount, startingSalaryCurrency)` — throws before any write if non-positive (AC 5.6.5, same guard as slice 4's AC 5.4.2); if `managerId` present, verify existence (`InvalidReferenceException` otherwise, reusing slice 3's exception type); build and save a new `Employee` with `currentSalary` set from the validated `Money`; save one `SalaryHistory` row with `effectiveDate = hireDate`, `amount`/`currencyCode` matching the starting salary exactly, `recordedAt = clock.instant()` (AC 5.6.3 — the two rows share amount/currency/effective-date by construction, not by a follow-up call).

**Tests:**
- Extend `CompensationServiceTest` — a valid create produces exactly one `Employee` and one `SalaryHistory` row with matching amount/currency/effective-date (AC 5.6.3); a non-positive starting salary throws before any write (AC 5.6.5); an invalid `managerId` throws `InvalidReferenceException`.
- Extend `EmployeeControllerWebMvcTest` (or a new `EmployeeCreateWebMvcTest`) — valid `POST /api/employees` returns 201 with the summary shape; missing required fields return 422.
- Frontend: new spec file `add-employee-dialog.component.spec.ts` — required-field validation mirrors slice 3's pattern; successful submit closes the dialog and triggers a directory refresh that preserves active filters (AC 5.6.4).

**Standards that bite here:** Reuses slice 4's `Money`/transactional-write pattern (`Clean Code: no reinventing utilities`); Accessibility Checklist: *"Elements with role=\"dialog\" ... have accessible names"* (the onboarding dialog needs a labelled title, same pattern as slice 2's panel).

**Quality checks**

- **Creating an employee always produces exactly one `Employee` row and exactly one `SalaryHistory` row sharing amount, currency, and effective date — never an `Employee` created without its paired history row.** (AC 5.6.3, highest-risk assertion — this is the composite-transaction boundary)
- A non-positive starting salary is rejected with 422 before any row is written, consistent with slice 4's AC 5.4.2. (AC 5.6.5)
- The new employee appears in the directory under whatever filters/sort are currently active, without a forced reset. (AC 5.6.4)

**Verification (slice complete when these pass)**

1. **Tests.** Full suite green.
3. **User-facing flow.** Open "Add employee", submit a valid new hire, confirm it appears in the directory (with active filters preserved) and that its detail panel shows one salary-history entry matching the starting salary; submit with a non-positive salary and confirm rejection.
4. **Accessibility.** Tab through the onboarding dialog; confirm it's announced as a labelled dialog and focus is trapped within it while open (same pattern as slice 2).

---

## Slice 7 — View organization-wide pay insights

**Key decision:** Greenfield for the `reporting` module (Decision 3) — the plan's second-riskiest slice. Aggregates are computed in Java over one bulk projection query rather than SQL `GROUP BY`/median, because SQLite has no portable median.

**Structure**

- `backend/src/main/java/com/acme/salary/reporting/dto/{PayReportCriteria,BreakdownDimension,CurrencyAggregate,PaySummaryResponse,GroupBreakdownResponse}.java` (new) — `record PayReportCriteria(Optional<Department> department, Optional<String> countryCode, Optional<JobLevel> jobLevel)` (all-empty for this slice; slice 8 populates it from query params); `enum BreakdownDimension { DEPARTMENT, COUNTRY, JOB_LEVEL }`; `record CurrencyAggregate(String currencyCode, long headcount, BigDecimal averageSalary, BigDecimal medianSalary)`; `record PaySummaryResponse(long totalHeadcount, List<CurrencyAggregate> byCurrency)`; `record GroupBreakdownResponse(String groupKey, List<CurrencyAggregate> byCurrency)`.
- `backend/src/main/java/com/acme/salary/employee/EmployeeRepository.java` (modify) — add a projection query: `interface EmployeeSalaryProjection { Department getDepartment(); String getCountryCode(); JobLevel getJobLevel(); String getSalaryCurrencyCode(); BigDecimal getSalaryAmount(); }`; `List<EmployeeSalaryProjection> findAllProjections(): List<EmployeeSalaryProjection>` (`@Query` selecting only the five columns reporting needs, avoiding a full-entity load).
- `backend/src/main/java/com/acme/salary/reporting/PayAggregator.java` (new, pure) — no Spring annotations. `List<CurrencyAggregate> aggregate(List<EmployeeSalaryProjection> rows): List<CurrencyAggregate>` (groups by currency, computes headcount/average/median per group); `BigDecimal median(List<BigDecimal> sortedAmounts): BigDecimal` (sorted-list middle-element/average-of-two-middles).
- `backend/src/main/java/com/acme/salary/reporting/ReportingService.java` (new) — constructor-injected `EmployeeService` **not** `EmployeeRepository` (service-interface-only read rule, [context §4c](../specs/001-employee-salary-management-context.md)), plus `PayAggregator`. **Open at build time note:** since `EmployeeService` currently exposes no raw-projection method, add `EmployeeService.findAllForReporting(): List<EmployeeSalaryProjection>` as a thin pass-through to `EmployeeRepository.findAllProjections()` — this keeps `ReportingService` off `EmployeeRepository` directly while still reaching the data, per the module boundary this plan fixes in Shared conventions. Methods: `PaySummaryResponse summarize(PayReportCriteria criteria): PaySummaryResponse`; `List<GroupBreakdownResponse> breakdownBy(BreakdownDimension dimension, PayReportCriteria criteria): List<GroupBreakdownResponse>`.
- `backend/src/main/java/com/acme/salary/reporting/ReportingController.java` (new) — `@RestController @RequestMapping("/api/reports/pay")`. `GET /api/reports/pay/summary` → `summarize` (criteria all-empty in this slice); `GET /api/reports/pay/breakdown?dimension={department|country|jobLevel}` → `breakdownBy`.
- `frontend/src/app/features/pay-insights/pay-insights-page.component.ts` (new) — summary tiles (headcount, avg/median per currency) + one breakdown table per dimension.
- `frontend/src/app/features/pay-insights/pay-insights.service.ts` (new) — `getSummary(criteria): Observable<PaySummary>`, `getBreakdown(dimension, criteria): Observable<GroupBreakdown[]>`.
- `frontend/src/app/app.routes.ts` (modify) — add `/insights` (`loadComponent`).

**Collaborators:** `ReportingController` calls `ReportingService`. `ReportingService` calls `EmployeeService.findAllForReporting()` (never `EmployeeRepository` directly) and `PayAggregator` (constructed once, stateless, called per request).

**Control flow:**
- `ReportingService.summarize`: fetch all projections via `EmployeeService.findAllForReporting()`; if `criteria` has any non-empty field, filter in-memory first (this slice always passes all-empty criteria, so filtering is a no-op — slice 8 is what actually exercises it); group the (filtered) rows by currency; for each currency group, delegate to `PayAggregator` for headcount/average/median; wrap in `PaySummaryResponse`.
- `ReportingService.breakdownBy`: same fetch + criteria-filter; group by the requested dimension's key (department name / country code / job level name) **and then** by currency within each group (never across currencies — [spec §9.2](../specs/001-employee-salary-management-spec.md#9-deferred-follow-ups-and-accepted-limits)); for each (group, currency) bucket, delegate to `PayAggregator`.
- `PayAggregator.median`: sort amounts ascending; if odd count, return the middle element; if even, return the average of the two middle elements (as a `BigDecimal`, using `RoundingMode.HALF_UP` for the division).

**Tests:**
- New file `PayAggregatorTest` (plain unit, pure, `@ParameterizedTest` table over group sizes 1/2/3/4/many) — median is correct for odd- and even-sized groups; average is correct; headcount matches input size. This is the plan's dedicated defense for its highest-risk computation.
- New file `ReportingServiceTest` (plain unit, mocked `EmployeeService`) — summary and breakdown never merge two different currencies into one `CurrencyAggregate` (AC 5.7.1/[spec §9.2](../specs/001-employee-salary-management-spec.md#9-deferred-follow-ups-and-accepted-limits) guard); breakdown groups are keyed correctly per dimension.
- New file `ReportingControllerWebMvcTest` — `GET /api/reports/pay/summary` and `/breakdown` return the expected JSON shape.
- Frontend: new spec file `pay-insights-page.component.spec.ts` — renders headcount/avg/median tiles per currency without a combined/converted total.

**Standards that bite here:** [spec §6.1.8](../specs/001-employee-salary-management-spec.md#61-spring-boot-backend-standards) (avoid N+1 — one bulk projection query, not per-group queries); `architecture-craft`: dependency direction (`reporting → employee`'s service, never its repository); Accessibility Checklist: *"Use landmark elements"*, contrast/focus rules inherited from slice 1's shared theme.

**Quality checks**

- `ReportingService` never imports or calls `EmployeeRepository` or `SalaryHistoryRepository` directly — only `EmployeeService`. (module-boundary check, [spec §7.2](../specs/001-employee-salary-management-spec.md#72-architectural-style))
- **`PayAggregator.median` is correct for both odd- and even-sized groups, verified by the parameterized test table — this is the single highest-risk assertion in the plan.** (AC 5.7.1–5.7.4)
- No `CurrencyAggregate` ever mixes amounts from two different currencies; results stay grouped per currency with no FX conversion anywhere in `reporting`. (AC 5.7.1, [spec §9.2](../specs/001-employee-salary-management-spec.md#9-deferred-follow-ups-and-accepted-limits) — untouched)
- Aggregates are computed from one bulk query plus in-memory grouping — the browser never receives raw per-employee rows. (AC 5.7.5)
- The Insights page has no edit affordance anywhere. (AC 5.7.6)

**Verification (slice complete when these pass)**

1. **Tests.** Full suite green.
3. **User-facing flow.** Load `/insights`; confirm headcount and avg/median tiles per currency, and breakdown tables by department/country/job-level, all populated from the seeded data.
4. **Accessibility.** Confirm the page has a single `<main>` landmark and the breakdown tables have proper header associations.

---

## Slice 8 — Filter pay insights by department, country, or role

**Key decision:** Extends slice 7's endpoint and service with real `PayReportCriteria` values instead of always-empty ones — no new aggregation logic, no parallel query path.

**Structure**

- `backend/src/main/java/com/acme/salary/reporting/ReportingController.java` (modify) — `summary`/`breakdown` endpoints now bind `department`, `countryCode`, `jobLevel` as optional `@RequestParam`s into a real `PayReportCriteria` (previously always empty).
- `backend/src/main/java/com/acme/salary/reporting/ReportingService.java` (modify) — the in-memory criteria-filter step written in slice 7 (previously a no-op) now actually filters; no signature change.
- `frontend/src/app/features/pay-insights/pay-insights-filter.component.ts` (new) — typed reactive form (department/country/jobLevel `mat-select`s, all optional).
- `frontend/src/app/features/pay-insights/pay-insights-page.component.ts` (modify) — hosts the filter component; filter `valueChanges` feed `PayInsightsService.getSummary`/`getBreakdown` with the current criteria; a "no employees match" empty state when a filter combination is empty (mirrors slice 1's AC 5.1.7 pattern).
- `frontend/src/app/features/pay-insights/pay-insights.service.ts` (modify) — `getSummary`/`getBreakdown` now pass criteria as query params (previously omitted).

**Collaborators:** No new collaborators — reuses slice 7's `ReportingService`/`PayAggregator` untouched.

**Control flow:** `ReportingController`: bind optional `department`/`countryCode`/`jobLevel` request params into `PayReportCriteria` (all still individually optional, combinable); pass to the unchanged `ReportingService` methods. Clearing all filters in the UI sends an all-empty criteria, which is byte-for-byte the same call slice 7 already made, so it reproduces slice 7's org-wide figures exactly (AC 5.8.4) rather than a separately cached "unfiltered" state.

**Tests:**
- Extend `ReportingServiceTest` — a populated `PayReportCriteria` narrows both `summarize` and `breakdownBy` to matching rows only; a filter combination matching zero employees returns a `PaySummaryResponse` with `totalHeadcount = 0` and an empty `byCurrency` list (not an error).
- Extend `ReportingControllerWebMvcTest` — query params bind correctly into `PayReportCriteria`, independently and combined.
- Frontend: extend `pay-insights-page.component.spec.ts` — applying filters narrows displayed figures; a zero-match combination shows the explicit empty state (not a misleading `0` tile or a thrown error); clearing filters reproduces the exact org-wide figures from an unfiltered call.

**Standards that bite here:** `Clean Code: no reinventing utilities` — reuses slice 7's service/endpoint rather than a second reporting path (the plan's cross-slice note in Part 1).

**Quality checks**

- A filter combination matching zero employees shows the explicit "no employees match" state, not a misleading `0` or an error. (AC 5.8.3)
- Clearing all filters reproduces slice 7's org-wide figures exactly (same call shape, all-empty criteria) rather than a separately cached filtered state. (AC 5.8.4)
- No second aggregation code path exists — `ReportingService`'s method signatures are unchanged from slice 7, only their criteria argument is now populated from real input. ([context §4c](../specs/001-employee-salary-management-context.md) trap — "reuse 5.7's endpoint, no parallel query path")

**Verification (slice complete when these pass)**

1. **Tests.** Full suite green.
3. **User-facing flow.** Apply department/country/job-level filters individually and combined; confirm figures narrow correctly; apply a combination matching nobody and confirm the empty state; clear filters and confirm the org-wide figures return exactly.
4. **Accessibility.** Tab through the new filter controls; confirm labelled `mat-select`s with visible focus states.

---

## Hard floors

- **Slice 1** — triggering category: **migrations** (creates `V1`–`V3` Flyway schema migrations). Floor tier `≥ excellent`. Slice 1's assigned tier is `best`, which already satisfies the floor; the floor may never be lowered below `excellent` regardless of any later cost consideration.
- No other slice touches a designated high-risk category (auth, authorization/guard, payment, security) — the spec has no auth surface by design ([spec §9.1](../specs/001-employee-salary-management-spec.md#9-deferred-follow-ups-and-accepted-limits)).

## Escalation log

*Empty at authoring. `/anthara:plan-implementer` appends one-way tier bumps here: date, slice, `<from> → <to>`, reason.*
