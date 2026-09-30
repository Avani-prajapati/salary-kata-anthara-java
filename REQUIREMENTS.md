# Requirements — ACME Employee Salary Management

## Goal

Replace ACME HR's spreadsheet-based salary tracking with a web app so the HR Manager can browse, edit, and report on pay for 10,000 employees across multiple countries — including answering "how does the org pay people?" without manual pivot-table work.

## Scope & features

| Feature | What it does |
|---|---|
| Employee directory | Paginated, searchable (by name), filterable (department / country / job level), sortable table over all 10,000 employees. |
| Employee detail | Side-panel view of one employee's full profile plus their current salary. |
| Profile editing | Update department, job level, country, hire date, and manager — independent of salary. |
| Salary changes | Record a new salary as an **append-only** history entry (a raise is never an edit to the old value — it's a new row); the current salary is always the entry with the latest effective date. |
| Salary history | Read-only, reverse-chronological view of every past change for an employee. |
| Onboarding | Add a new employee with their starting salary, in one transaction. |
| Pay insights | Headcount and average/median salary, org-wide and broken down by department, country, and job level — the direct answer to "how do we pay people?" |
| Insights filtering | Narrow the same insights to one department/country/job-level, or a combination. |
| Seed data | A deterministic, idempotent script generating 10,000 realistic employees (weighted country/department/level distributions, an acyclic manager hierarchy). |

Full detail, acceptance criteria, architecture, and NFRs: [`docs/specs/001-employee-salary-management-spec.md`](docs/specs/001-employee-salary-management-spec.md).

## Deliberately left out — and why

| Excluded | Reasoning |
|---|---|
| **Authentication / login** | The persona is a single implicit HR-manager role; the brief never asks for multi-user access control. Building a real auth subsystem would spend the assessment's time budget on infrastructure instead of the salary domain. A production deployment would need OAuth2/OIDC + RBAC before touching real employee data. |
| **Cross-currency conversion** | Employees are paid in their local currency; figures are grouped and aggregated *per currency*, never summed or converted into one number. Inventing FX rates would misrepresent real purchasing power and can't be verified without a live rate feed. |
| **Employee lifecycle / termination workflow** | The brief is about managing *salary data*, not headcount lifecycle. Every employee is implicitly active — adding a status/offboarding flow would be scope creep beyond the stated goal. |
| **Manager-hierarchy cycle detection** | Editing a manager reference checks only that the id exists, not that the resulting chain is acyclic. Acceptable because the seed data is generated acyclic *by construction* (each employee's manager is always someone already generated, at an equal-or-more-senior level) — there's no real cycle to detect against in this dataset. |
| **CSV/BI export** | Pay insights are answered in-app (aggregate tiles + breakdown tables); a full BI/export pipeline is a different, larger project than "help the HR manager see how the org is paid." |

## Key trade-offs

- **SQLite**, per the kata's own suggestion — zero setup for a reviewer running the seed script, and trivially handles a 10,000-row, single-writer dataset. Its lack of a portable median aggregate is why pay-insights math runs in the Java service layer over one bulk query rather than SQL `GROUP BY` — simple, unit-testable, and fast enough at this scale (see spec §9.6 if that scale assumption ever needs revisiting).
- **Modular monolith** (Spring Boot: `employee` / `compensation` / `reporting` modules), not microservices — one HR-manager persona and no auth mean independently-deployable services buy nothing here.
- **A closed `JobLevel` enum**, not free-text job titles — a free-text field would fragment into thousands of near-unique values across 10,000 rows, making the "breakdown by job level" insight meaningless.

## Performance considerations

- List and history endpoints are paginated and capped server-side (`spring.data.web.pageable.max-page-size`), so a client can never force an unbounded 10,000-row response.
- Pay-insights reads never ship raw employee rows to the browser — the backend aggregates first.
- The seed script is idempotent (checks row count before inserting) so re-running it against a populated database is a safe no-op, not a duplicate-data bug.
