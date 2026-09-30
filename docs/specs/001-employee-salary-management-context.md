# Task context: 001-employee-salary-management

> Generated for spec `1e5d4eef-d84e-45d1-b413-cf3943dff80b` at HEAD `1000e45` · 2026-09-30
> Read first by every agent in /anthara:plan-implementer's slice loop.

**Repo state confirmed: true greenfield.** `git ls-files` / directory walk shows exactly two files: `kata.md` (assignment brief) and `docs/specs/001-employee-salary-management-spec.md`. No `pom.xml`/`build.gradle`, no `package.json`/`angular.json`, no `README.md`, no `CLAUDE.md`, no `src/`, no `.github/`. Every "pattern" below is *prescribed by the spec*, not *observed in code* — there is nothing yet to observe. The first slice's implementer establishes the actual shape; every later slice must imitate that first shape, not re-derive it from the spec each time.

## 1. Affected modules

| Module / file (all **to be created**) | Role | Notes |
|---|---|---|
| `employee/` (backend) | Employee CRUD, directory query/search/filter/sort, seed script | [spec §7.3](001-employee-salary-management-spec.md#73-module-decomposition): `EmployeeController` → `EmployeeService` → `EmployeeRepository` |
| `compensation/` (backend) | Salary-change command, salary-history query | `CompensationController` → `CompensationService` → `SalaryHistoryRepository` (+ writes back to `EmployeeRepository`'s denormalized pointer, per [spec §5.4](001-employee-salary-management-spec.md#54-change-an-employees-salary) sequence diagram) |
| `reporting/` (backend) | Org-wide and filtered pay insights, aggregation only | Reads `employee`/`compensation` **through their service interfaces only** — never their repositories — per [spec §7.2](001-employee-salary-management-spec.md#72-architectural-style) |
| `employee-directory/`, `employee-detail/`, `insights/` (Angular, lazy-loaded) | Directory table, detail side-panel + edit/salary/history, insights page | [spec §7.3](001-employee-salary-management-spec.md#73-module-decomposition); standalone components per [spec §6.3](001-employee-salary-management-spec.md#63-angular-frontend-standards) |
| Flyway migrations + seed script | Schema + 10,000-row idempotent seed | [spec §5.1 AC 5.1.6](001-employee-salary-management-spec.md#51-browse-the-employee-directory); no `ddl-auto=update` ([spec §6.1.9](001-employee-salary-management-spec.md#61-spring-boot-backend-standards)) |

## 2. Patterns to follow

No code exists to extract patterns from; these are the spec-mandated shapes slice 1 must instantiate and every later slice must then match:

- **Layered call direction** — controller → service → repository, never skipped either way. [spec §6.1.5](001-employee-salary-management-spec.md#61-spring-boot-backend-standards).
- **DTOs at the API boundary** — controllers accept/return Java records, never JPA entities. [spec §6.1.3](001-employee-salary-management-spec.md#61-spring-boot-backend-standards).
- **Money** — `BigDecimal` + a separate currency code field, never `double`/`float`. [spec §6.1.1](001-employee-salary-management-spec.md#61-spring-boot-backend-standards), [invariant 4.3](001-employee-salary-management-spec.md#4-invariants).
- **Time** — `java.time` (`LocalDate`/`Instant`) via an injected `Clock`, never legacy `Date`. [spec §6.1.2](001-employee-salary-management-spec.md#61-spring-boot-backend-standards), [§6.2.3](001-employee-salary-management-spec.md#62-java-testing-standards).
- **One `@ControllerAdvice`** returning `ProblemDetail` (RFC 9457) for all errors. [spec §6.1.6](001-employee-salary-management-spec.md#61-spring-boot-backend-standards).
- **Constructor injection, typed config, `open-in-view=false`.** [spec §6.1.10](001-employee-salary-management-spec.md#61-spring-boot-backend-standards).
- **Angular:** standalone components, `OnPush`, `inject()`, strictly-typed reactive forms (no `any`), functional HTTP interceptors for error handling, `loadComponent` lazy routes. [spec §6.3](001-employee-salary-management-spec.md#63-angular-frontend-standards).
- **Domain-based folders** — `employee/`, `compensation/`, `reporting/`, not layer-based (`controllers/`, `services/` across domains). [spec §6.5.1](001-employee-salary-management-spec.md#65-incubyte-engineering-standards--clean-code).

## 3. Reusable utilities

None exist yet. The following are shared concerns the *first* slice that needs them must build once, for reuse by all later slices — do not let a later slice quietly duplicate one of these:

- Global `ProblemDetail` mapper (single `@ControllerAdvice`) — first needed by [slice 5.2](001-employee-salary-management-spec.md#52-view-an-employees-full-detail)'s 404, then reused by 5.3/5.4/5.6's 422s.
- Injected `Clock` bean — first needed by [slice 5.4](001-employee-salary-management-spec.md#54-change-an-employees-salary)/[5.6](001-employee-salary-management-spec.md#56-add-a-new-employee) for `recordedAt`/effective-date defaults; must be fixed in tests per [§6.2.3](001-employee-salary-management-spec.md#62-java-testing-standards).
- Seed script (10,000-row, idempotent against a fresh DB) — built for [slice 5.1 AC 5.1.6](001-employee-salary-management-spec.md#51-browse-the-employee-directory); later slices (5.6) must not bypass its generation conventions when adding employees.
- Money/currency value shape (`BigDecimal` + ISO currency code) — first defined for [slice 5.1](001-employee-salary-management-spec.md#51-browse-the-employee-directory)'s display column, must be the *same* shape used by 5.4/5.6's write paths.

## 4. Adjacent code that must coordinate (per slice)

- **Slice 5.1:** seed script; `EmployeeController`/`Service`/`Repository`; `employee-directory` Angular feature.
- **Slice 5.2:** `EmployeeController` (new get-by-id route); `employee-detail` feature must reuse 5.1's row-click without disturbing the directory's page/sort/filter state.
- **Slice 5.3:** `EmployeeController` edit route, `EmployeeService`; must NOT touch the salary field/`CompensationController` boundary.
- **Slice 5.4:** `CompensationController`/`Service`/`SalaryHistoryRepository`; also writes `EmployeeRepository`'s denormalized current-salary pointer (per its own sequence diagram); directory (5.1) and detail panel (5.2) must reflect the update immediately.
- **Slice 5.5:** `CompensationController`/`Service`, reusing 5.4's `SalaryHistoryRepository`.
- **Slice 5.6:** `EmployeeController` create route + `CompensationService`'s initial-history-insert logic from 5.4; directory (5.1) must show the new row under current filters.
- **Slice 5.7:** `ReportingController`/`Service`, reading `EmployeeService`/`CompensationService` only (never their repositories); `insights` Angular feature.
- **Slice 5.8:** extends 5.7's `ReportingController`/`Service` and `insights` feature with filter params — no parallel query path.

## 4a. Slice imitation targets

- **Slice 5.1:** greenfield; no imitation target. This is the first controller/service/repository triad and first Angular feature — establish the shape carefully, it anchors everything after it.
- **Slice 5.2:** mirror 5.1's `EmployeeController`/DTO-mapping conventions for the new get-by-id route.
- **Slice 5.3:** mirror 5.2's response/DTO shape; also the first slice to exercise the `@ControllerAdvice` pattern for a `422` (5.2 established it for `404`).
- **Slice 5.4:** greenfield for the `compensation` module functionally, but must structurally mirror `employee`'s controller/service/repository layering from 5.1–5.3.
- **Slice 5.5:** mirror 5.4's `CompensationController`/`Service` shape — this is the read-side counterpart to 5.4's write endpoint.
- **Slice 5.6:** mirror 5.3's Employee-persistence save path, combined with 5.4's SalaryHistory-insert path, for the "create employee + first history row" transaction.
- **Slice 5.7:** greenfield for `reporting`; no adjacent example. Must respect the service-interface-only read rule from [spec §7.2](001-employee-salary-management-spec.md#72-architectural-style).
- **Slice 5.8:** mirror 5.7's `ReportingController`/`Service` query-with-params shape.

## 4b. Slice lexicon

- **Slice 5.1:** entity=`Employee`; fields=`name`,`department`,`jobTitle`,`country`,`currentSalary`; list route/params (not pinned by spec — first slice to fix them; keep the shape consistent for 5.5/5.7/5.8, which reuse the same query-param style).
- **Slice 5.2:** Problem Details body carries `code` + `message` fields ([AC 5.2.5](001-employee-salary-management-spec.md#52-view-an-employees-full-detail)) — this is the fixed error-body shape for every later 4xx.
- **Slice 5.3:** event=`EmployeeProfileUpdated` ([spec §3.3](001-employee-salary-management-spec.md#33-kinds-of-events)); editable fields=`department`,`jobTitle`,`country`,`hireDate`,`manager`.
- **Slice 5.4:** route=`POST /api/employees/{id}/salary-changes` (fixed verbatim by the spec's own sequence diagram); entity=`SalaryHistory`; fields=`amount`,`currency`,`effectiveDate`; service method=`CompensationService.changeSalary(employeeId, request)`; event=`SalaryChanged`.
- **Slice 5.5:** reuses `SalaryHistory` fields from 5.4 plus `recordedAt` (when the row was written, distinct from `effectiveDate`).
- **Slice 5.6:** route not pinned — likely `POST /api/employees`, matching 5.1's collection resource; event=`EmployeeCreated` ([spec §3.3](001-employee-salary-management-spec.md#33-kinds-of-events)).
- **Slices 5.7/5.8:** grouping dimensions are named `department`, `country`, `job title/level` interchangeably in the spec ([§3.2](001-employee-salary-management-spec.md#32-kinds-of-data) vs. [AC 5.7.4/5.8.1](001-employee-salary-management-spec.md#57-view-organization-wide-pay-insights)) — pin this to the **same** `jobTitle` field already established in 5.1, do not introduce a second "job level" field.

## 4c. Slice traps

- **Slice 5.1:** (1) enforce the page-size cap server-side regardless of client-requested size ([5.1.5](001-employee-salary-management-spec.md#51-browse-the-employee-directory)). (2) seed script must be idempotent against the *same* fresh DB — check for existing rows, don't assume empty ([5.1.6](001-employee-salary-management-spec.md#51-browse-the-employee-directory)). (3) empty search/filter result renders an explicit "no employees match" state, not a blank table or error ([5.1.7](001-employee-salary-management-spec.md#51-browse-the-employee-directory)). (4) do NOT let `reporting` (5.7/5.8) later import `EmployeeRepository` directly — keep repositories package-private to `employee`.
- **Slice 5.2:** (1) closing the panel (button or Escape) must restore keyboard focus to the triggering row ([5.2.3–5.2.4](001-employee-salary-management-spec.md#52-view-an-employees-full-detail)). (2) opening the panel must not reset the directory's page/sort/filter state underneath it ([5.2.1](001-employee-salary-management-spec.md#52-view-an-employees-full-detail)).
- **Slice 5.3:** (1) do NOT allow this flow to change `currentSalary` — it is read-only here, salary changes only via 5.4 ([5.3.5](001-employee-salary-management-spec.md#53-edit-an-employees-profile)). (2) do NOT add manager-hierarchy cycle detection — deliberately out of scope; only existence of the referenced id is checked ([spec §9.4](001-employee-salary-management-spec.md#9-deferred-follow-ups-and-accepted-limits)).
- **Slice 5.4:** (1) a salary change must INSERT a new `SalaryHistory` row — never UPDATE/DELETE an existing one; the diagram's "update `Employee.currentSalary`" refers only to the denormalized pointer, not the history table ([invariant 4.2](001-employee-salary-management-spec.md#4-invariants)). (2) reject zero/negative amounts with `422` *before* any row is written ([5.4.2](001-employee-salary-management-spec.md#54-change-an-employees-salary)). (3) "current salary" = the history row with the latest **effective date**, not the latest insert/recorded-at order — effective dates can be backdated ([invariant 4.4](001-employee-salary-management-spec.md#4-invariants)).
- **Slice 5.5:** (1) strictly read-only — no edit/delete affordance ([5.5.4](001-employee-salary-management-spec.md#55-view-an-employees-salary-history)). (2) a brand-new employee's single seed-record must render normally, not as an empty/error state ([5.5.3](001-employee-salary-management-spec.md#55-view-an-employees-salary-history)). (3) sort by effective date descending, consistent with invariant 4.4 — not by recorded-at.
- **Slice 5.6:** (1) must create exactly one `Employee` row and exactly one initial `SalaryHistory` row sharing amount/currency/effective-date(=hire date) — do NOT create the employee alone and expect a follow-up call to 5.4's endpoint ([5.6.3](001-employee-salary-management-spec.md#56-add-a-new-employee)). (2) zero/negative starting salary → `422`, consistent with 5.4.2 ([5.6.5](001-employee-salary-management-spec.md#56-add-a-new-employee)). (3) new row must respect currently-applied directory filters/sort — do NOT force-reset them ([5.6.4](001-employee-salary-management-spec.md#56-add-a-new-employee)).
- **Slice 5.7:** (1) aggregates computed server-side; never fetch all 10,000 rows to the browser to compute client-side ([5.7.5](001-employee-salary-management-spec.md#57-view-organization-wide-pay-insights)). (2) never sum/convert across currencies — group per currency, do NOT invent an FX rate ([spec §9.2](001-employee-salary-management-spec.md#9-deferred-follow-ups-and-accepted-limits)). (3) read-only page — no edit affordance ([5.7.6](001-employee-salary-management-spec.md#57-view-organization-wide-pay-insights)). (4) `ReportingService` must call `EmployeeService`/`CompensationService`, never their repositories directly ([spec §7.2](001-employee-salary-management-spec.md#72-architectural-style)).
- **Slice 5.8:** (1) a zero-match filter combination must show an explicit "no employees match", not a misleading `0` or an error ([5.8.3](001-employee-salary-management-spec.md#58-filter-pay-insights-by-department-country-or-role)). (2) clearing filters must return exactly to 5.7's org-wide figures, not a stale cached filtered state ([5.8.4](001-employee-salary-management-spec.md#58-filter-pay-insights-by-department-country-or-role)). (3) reuse 5.7's endpoint/service with added params rather than a parallel query path.

No slice deletes, archives, or revokes data (history is append-only; no employee-termination workflow per [spec §9.3](001-employee-salary-management-spec.md#9-deferred-follow-ups-and-accepted-limits)) — §4c destructive-surface guidance (confirmation pattern, impact-count query) does not apply to this spec.

## 5. Known dragons

- No `TODO`/`FIXME`/`LEGACY` markers exist — there is no code yet. Forward-looking dragons flagged *by the spec itself*, so later slices don't "fix" them:
  - **No authentication** — sensitive PII with zero access control, by design ([spec §9.1](001-employee-salary-management-spec.md#9-deferred-follow-ups-and-accepted-limits)). Do not add ad hoc auth; also do not treat its absence as a bug to silently patch.
  - **No manager-cycle detection** — relies on the seed generator producing an acyclic hierarchy by construction ([spec §9.4](001-employee-salary-management-spec.md#9-deferred-follow-ups-and-accepted-limits)); if the seed generator (5.1) is ever changed, the acyclic guarantee must be preserved or 5.3's validation silently allows cycles.
  - **Route paths for 5.5/5.6/5.7/5.8 are not pinned by the spec** (only 5.4's is, verbatim). The first implementer fixes the convention; every later slice must reuse it rather than inventing a divergent shape.

## 6. Test conventions

- **Backend unit/slice:** JUnit Jupiter + AssertJ + Mockito. `@WebMvcTest` for controllers, `@DataJpaTest` for repositories, plain constructor-built unit tests for service/domain logic, a few `@SpringBootTest` integration tests. `@MockitoBean`, not `@MockBean`. [spec §6.2](001-employee-salary-management-spec.md#62-java-testing-standards).
- **Determinism:** any "now"-dependent logic takes an injected `java.time.Clock`; tests use a fixed `Clock`. [spec §6.2.3](001-employee-salary-management-spec.md#62-java-testing-standards).
- **Naming:** business-language test names (e.g. `rejects_salary_change_below_zero`), not `testChangeSalary_invalid`. [spec §6.2.4](001-employee-salary-management-spec.md#62-java-testing-standards), [§6.5.2](001-employee-salary-management-spec.md#65-incubyte-engineering-standards--clean-code). Use `@ParameterizedTest` sparingly — only where assertion logic and a readable name both survive. [spec §6.5.3](001-employee-salary-management-spec.md#65-incubyte-engineering-standards--clean-code).
- **Frontend:** Jasmine/Karma + Angular Testing Library. [spec §7.1](001-employee-salary-management-spec.md#71-tech-stack).
- **Fixtures:** none exist; slice 1 should keep test datasets small and deterministic, distinct from the 10,000-row seed script (which is for manual/demo verification, not test speed).
- **Build tooling (Maven vs. Gradle) and exact test-run commands are not pinned by the spec** — slice 1's implementer must choose, document (e.g. root README), and keep consistent thereafter.

## 7. Hot paths and performance landmines

- Directory listing over 10,000 rows ([slice 5.1](001-employee-salary-management-spec.md#51-browse-the-employee-directory)) — pagination/filtering/sorting must happen DB-side; never load all rows then paginate in memory ([5.1.5](001-employee-salary-management-spec.md#51-browse-the-employee-directory), [§6.1.7](001-employee-salary-management-spec.md#61-spring-boot-backend-standards)).
- Reporting aggregates ([slices 5.7](001-employee-salary-management-spec.md#57-view-organization-wide-pay-insights)/[5.8](001-employee-salary-management-spec.md#58-filter-pay-insights-by-department-country-or-role)) — must be DB-side GROUP BY/aggregate queries, never a full 10k-row fetch to compute in the browser or app layer ([5.7.5](001-employee-salary-management-spec.md#57-view-organization-wide-pay-insights)); watch for N+1 across department/country/job-level breakdowns ([§6.1.8](001-employee-salary-management-spec.md#61-spring-boot-backend-standards)).
- **Median** is not a plain `AVG` — SQLite has no built-in `PERCENTILE_CONT`; slice 5.7's implementer must decide the computation strategy (window function emulation vs. app-level on an already DB-filtered/sorted set) and document it — this is an open design decision, not a fixed convention yet.
- `Employee.currentSalary`'s denormalized pointer must be updated in the *same transaction* as the `SalaryHistory` insert ([slice 5.4](001-employee-salary-management-spec.md#54-change-an-employees-salary)) to protect [invariant 4.4](001-employee-salary-management-spec.md#4-invariants).

## 8. Open uncertainties resolved during orientation

- Confirmed the repo is truly greenfield (only `kata.md` + the spec file exist) — every convention in this document is spec-prescribed, none is code-observed.
- Confirmed the *only* API route the spec pins verbatim is `POST /api/employees/{id}/salary-changes` ([slice 5.4](001-employee-salary-management-spec.md#54-change-an-employees-salary)'s sequence diagram); all other routes are left to the first implementing slice.
- Backend build tool (Maven vs. Gradle) is unspecified by [spec §7.1](001-employee-salary-management-spec.md#71-tech-stack) — a slice-1 decision to make and document, not to leave implicit.

## 8a. Design system

- **Source of truth:** none detected in-repo (greenfield). Direction is pinned by [spec §7.1](001-employee-salary-management-spec.md#71-tech-stack) (Angular Material + Angular CDK) and the inline ASCII layout mockup in [slice 5.1](001-employee-salary-management-spec.md#51-browse-the-employee-directory) (search/filter bar above table, paginator below; side-panel drawer per [slice 5.2](001-employee-salary-management-spec.md#52-view-an-employees-full-detail)).
- **Tokens/theme:** none exist. The first UI slice (5.1) must establish an Angular Material theme (palette, typography scale) and that becomes the source of truth for every later slice — do not let each slice pick its own.
- **Component library:** Angular Material + CDK ([spec §7.1](001-employee-salary-management-spec.md#71-tech-stack)) — use its table, paginator, form-field, and CDK `FocusTrap` (needed for [5.2](001-employee-salary-management-spec.md#52-view-an-employees-full-detail)'s panel per [§6.4.2](001-employee-salary-management-spec.md#64-accessibility-checklist-wcag-21-aa)) rather than hand-rolling equivalents.
- **Conventions:** WCAG 2.1 AA — contrast ≥ 4.5:1 ([§6.4.4](001-employee-salary-management-spec.md#64-accessibility-checklist-wcag-21-aa)), full keyboard operability with visible focus ([§6.4.1](001-employee-salary-management-spec.md#64-accessibility-checklist-wcag-21-aa)), `prefers-reduced-motion` respected ([§6.4.5](001-employee-salary-management-spec.md#64-accessibility-checklist-wcag-21-aa)). Density/palette not otherwise specified — slice 1 picks and documents one.
- **Per-slice UI note:** Slice 5.2's detail panel should reuse whatever row/typography treatment 5.1 establishes for its table. Slice 5.6's "Add employee" form should mirror 5.3's edit-profile form structure (both are reactive-form Employee CRUD surfaces).

---
## Changelog
- 2026-09-30 — generated by code-orienteer from spec `1e5d4eef-d84e-45d1-b413-cf3943dff80b` at HEAD `1000e45`.
