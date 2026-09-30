# Browser verification report — 001-employee-salary-management

- **Date:** 2026-09-30
- **Mode:** first-pass
- **Base URL:** http://localhost:4200/ (frontend confirmed reachable, HTTP 200, Angular/Vite dev server responding) — backend API confirmed reachable at http://localhost:18080/api/employees (HTTP 200)
- **Provider:** SKIPPED — no browser MCP reachable

## Provider detection outcome

Per the `browser-verification` skill's provider-detection order:

1. `ToolSearch "+chrome-devtools"` → **no matching deferred tools found**.
2. `ToolSearch` retried with broader queries (`"chrome devtools browser"`, `"claude-in-chrome tabs_context"`, `"browser automation screenshot click navigate"`, `"mcp"`) → **no matching deferred tools found** for any query.
3. No `.mcp.json` declaring chrome-devtools-mcp or Claude-in-Chrome was found associated with this repo (searched the filesystem for `.mcp.json`; only unrelated projects' configs exist elsewhere on the machine).

Neither chrome-devtools-mcp nor Claude-in-Chrome is available in this session. Per the skill's degradation rule, automated in-browser verification could not run. This is **not** a pass — it is an explicit skip.

The app itself is confirmed running and reachable (frontend HTML served at `/`, backend `GET /api/employees` returns 200), so the blocker is tooling access in this session, not the app under test.

## Result

```
browser-verify first-pass: SKIPPED — no browser MCP
provider: none (chrome-devtools-mcp and claude-in-chrome both unreachable via ToolSearch)
app reachability: frontend 200 @ http://localhost:4200/, backend 200 @ http://localhost:18080/api/employees
0 of 8 UI slices' Verification blocks (steps 3-4) driven in-browser — all deferred to the manual checklist below.
```

## Manual checklist (to run by hand or re-invoke once a browser MCP provider is available)

For each item: perform the action, confirm the expected outcome, and watch the DevTools console for errors (an error/unhandled rejection = failure even if the visual state looks right).

### 5.1 Browse the employee directory — http://localhost:4200/employees
- [ ] Load `/employees`; table renders seeded rows (expect 10,000 total via paginator).
- [ ] Name search narrows results.
- [ ] Department / country / job-level filters narrow correctly (individually and combined).
- [ ] Sort toggles ascending/descending on each sortable column.
- [ ] Paginator moves through pages; page size respects the configured cap.
- [ ] An impossible filter combination shows the explicit "no employees match" empty state, not a blank table.
- [ ] **Skip link (fix-pass addition):** on a fresh page load, press Tab once — a visible "Skip to main content" link should receive focus first, before any other control. Activate it (Enter) and confirm focus moves to the page's `<main>` element.
- [ ] Tab through search box, each filter, each sortable column header, and the paginator controls — visible focus indicator on every stop, DOM tab order matches visual order.
- [ ] Console: no errors during load, search, filter, sort, or pagination.

### 5.2 View an employee's full detail — row click / Enter / Space
- [ ] Click a directory row → side-panel drawer (MatDrawer) opens with that employee's data; directory's page/sort/filter state underneath is unchanged.
- [ ] Focus a row via Tab and press **Enter** → panel opens (existing behavior).
- [ ] Focus a row via Tab and press **Space** (fix-pass addition) → panel opens identically.
- [ ] While the panel is open, confirm focus is trapped inside it (Tab cycles only through panel controls, never escapes to the page chrome).
- [ ] **`inert` background (fix-pass addition):** with the panel open, Tab repeatedly and confirm the directory table/filter bar/paginator underneath is never reached — none of its rows, headers, or controls receive focus while the panel is open.
- [ ] Close via the close button → focus returns to the row that opened the panel, and the background is no longer `inert` (Tab now reaches the table again).
- [ ] Reopen, close via **Escape** → same focus-return and `inert`-removal behavior.
- [ ] Panel has an accessible name (`aria-labelledby` pointing at a visible title).
- [ ] Request a non-existent id (if reachable via URL/direct route) → 404 ProblemDetail surfaces as a "not found" message in the UI, not a blank panel.
- [ ] Console: no errors on open/close, including the Escape-key path.

### 5.3 Edit an employee's profile
- [ ] "Edit profile" action in the panel opens a typed reactive form (department / jobLevel / country / hireDate / manager).
- [ ] Confirm **no salary field** appears anywhere in this form.
- [ ] Submit with a blank required field (e.g. clear department) → field-associated error appears (not a floating/toast-only message), tied via `aria-describedby`/`mat-error` to the specific control.
- [ ] Submit with an unknown manager id → **field-level error tied to the `managerId` input** (fix-pass addition — confirm this is not just a toast/snackbar).
- [ ] Submit a valid change → panel and the directory row update immediately, no full page reload.
- [ ] Tab through the form; every field has a visible, programmatically-associated label and, when invalid, associated error text.
- [ ] Console: no errors on validation failure or successful submit.

### 5.4 Change an employee's salary
- [ ] "Change salary" action opens a form (amount / currency / effective date).
- [ ] Submit a zero or negative amount → blocked **client-side** with a visible field error before any network request fires (check Network tab / request list — no POST should be sent).
- [ ] Submit a valid change → panel's displayed current salary and the directory row both update immediately.
- [ ] Tab through the form; labelled fields, associated error text.
- [ ] Console: no errors.

### 5.5 View an employee's salary history — "History" tab
- [ ] Open History tab for an employee with a single change (e.g. a freshly seeded/onboarded one) → renders that one entry normally, not as an empty state.
- [ ] Open History tab for an employee with several changes → entries ordered **newest-effective-date-first** (not by recordedAt/insertion order).
- [ ] Confirm **no edit or delete control** anywhere in the rendered history list.
- [ ] List/table has appropriate semantic structure (e.g. `<table>` with header associations, or an ARIA-equivalent list), no interactive elements masquerading as static content.
- [ ] Console: no errors.

### 5.6 Add a new employee — "Add employee" toolbar button
- [ ] Button opens a Material dialog (`role="dialog"`, labelled title, accessible name).
- [ ] Focus is trapped inside the dialog while open (same pattern as 5.2's panel).
- [ ] Required-field validation blocks submission with field-level errors when fields are blank.
- [ ] Unknown manager id → field-level error tied to the `managerId` input (same fix-pass behavior as 5.3).
- [ ] Successful create closes the dialog; the new row appears in the directory **under whatever filters/sort are currently active** (not a forced reset to page 1 / cleared filters).
- [ ] Newly created employee's detail panel shows exactly one salary-history entry matching the starting salary.
- [ ] Submit a non-positive starting salary → rejected before any row is written.
- [ ] Console: no errors.

### 5.7 View organization-wide pay insights — http://localhost:4200/insights
- [ ] Reachable via a nav link from the app shell (not just direct URL entry).
- [ ] Headcount + average/median salary tiles render **per currency** — confirm there is **no combined/converted total** across currencies anywhere on the page.
- [ ] Breakdown tables by department, by country, and by job-level all populate from the seeded data, each still split per currency (never merged across currencies).
- [ ] Page has a single `<main>` landmark; breakdown tables have proper header (`<th scope>` or equivalent) associations.
- [ ] No edit affordance anywhere on this page.
- [ ] Console: no errors.

### 5.8 Filter pay insights
- [ ] Department / country / job-level filter controls present on the Insights page, each a labelled `mat-select` (or equivalent) reachable via Tab with visible focus.
- [ ] Applying a single filter narrows headcount/avg/median figures and breakdown tables correctly.
- [ ] Applying a combination of filters narrows further, consistently.
- [ ] A filter combination matching **zero employees** shows an explicit "no employees match" state — not a misleading `0` tile and not a thrown error.
- [ ] Clearing all filters reproduces slice 5.7's org-wide figures **exactly** (same numbers as the unfiltered load, not a stale/cached different value).
- [ ] Console: no errors when applying/clearing filters, including the zero-match case.

## Summary

0 of 8 UI slices' acceptance criteria verified in-browser this run — automated verification skipped for lack of a reachable browser MCP provider (chrome-devtools-mcp and Claude-in-Chrome both absent from `ToolSearch`). The app is confirmed up and reachable at the given URLs. Re-run this agent once a browser MCP provider (chrome-devtools-mcp or Claude-in-Chrome) is configured and connected in the session, or execute the manual checklist above by hand.
