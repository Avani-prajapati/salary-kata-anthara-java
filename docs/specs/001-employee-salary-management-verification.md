# Browser verification report — 001-employee-salary-management

- **Date:** 2026-09-30
- **Mode:** first-pass, driven directly (chrome-devtools MCP tools loaded and used interactively)
- **Base URL:** http://localhost:4200/ (frontend), proxying `/api` to a locally-run backend on port 18080, seeded with 10,000 employees
- **Provider:** chrome-devtools MCP (`navigate_page`, `take_snapshot`, `click`, `fill`, `press_key`, `evaluate_script`, `list_console_messages`, `list_network_requests`)

An earlier attempt in a different session (see git history) reported this as skipped because `ToolSearch` did not surface the chrome-devtools tools from within a spawned subagent. Loading the tools directly in the orchestrating session worked, and the tools were genuinely reachable and used to drive the app end to end.

## Result

```
browser-verify: 2 HIGH found and fixed, 0 open. All 8 slices driven live.
```

Two real, reproducible defects were found — both invisible to the jsdom/Vitest component suite — diagnosed via Angular's `window.ng.getComponent()` debug API and Chrome's real focus/CSS model, fixed, and re-verified live. See commit `31f0400`.

### HIGH — Detail panel (slice 5.2) could not be closed at all

Clicking the Close button or pressing Escape visually closed the `MatDrawer` for under 30ms, then it silently reopened itself. Root cause: a standalone `effect()` in `employee-detail-panel.component.ts` called `drawer().open()` on every re-run where `isOpen()` was true, with no corresponding close branch, and re-ran shortly after any close attempt regardless of what triggered it. Confirmed via `window.ng.getComponent()` sampling that `isOpen()`/`state()`/`selectedEmployeeId` never changed across the relapse, i.e. nothing legitimate re-opened it.

**Fix:** removed the effect; `drawer().open()` now runs only inside the `employeeId`-change subscription, tied directly to a real state transition. Re-verified: Close button and Escape both now close the panel and it stays closed.

### HIGH — Focus did not return to the triggering row on close (AC 5.2.3/5.2.4), and skip link never moved focus (fix-pass regression check)

Two related focus-restoration failures, both found while re-verifying the fix above:

- **Detail panel:** `MatDrawer`'s own focus-restore captured `document.body` instead of the triggering row, because its internal "focus before open" capture is itself asynchronous (fires on the drawer's CSS transition) and raced against this component's focus-then-open sequence. Separately, the row element is a `<tr>` with computed `display: table-row` — calling `.focus()` on it while a CDK focus trap is still tearing down silently redirects focus elsewhere. **Fix:** explicit, deferred (`setTimeout`) focus restoration using the stored trigger-element reference in `onOpenedChange`, independent of `MatDrawer`'s internal timing.
- **Skip link:** activating `<a href="#main-content">` changed `location.hash` but Chrome did not move keyboard focus to the `tabindex="-1"` target — this SPA's routing intercepts enough of the navigation that the native browser fragment-focus algorithm doesn't reliably fire. **Fix:** explicit `(click)` handler that calls `.preventDefault()` and `document.getElementById('main-content')?.focus()` directly.

Both re-verified: Tab to the skip link → Enter moves focus to `<main>`; open a row → Close (or Escape) → focus lands back on the same `<tr role="button">` row, confirmed via `document.activeElement` inspection.

## Per-slice verification performed

### 5.1 Browse the employee directory
- Loaded `/employees`: 10,000 rows, `1 – 25 of 10000` paginator. ✅
- Search "Aarav Chen" → 6 results; search "Chen" → 384 results (network response verified directly — the backend result was always correct; an earlier apparent "0 results" reading during testing was my own polling matching a stale DOM string, not an app bug). ✅
- Zero-match search → "No employees match your search or filters." — confirmed via DOM, not inferred. ✅
- Sort by Salary → row order changed (reused across pages). ✅
- Pagination → Next page moved to `26 – 50 of 10000`. ✅
- Skip link: Tab (first stop) → visible "Skip to main content" link → Enter → focus on `<main>`. ✅ (fixed, see above)
- Console: only the expected `vite`/Angular dev-mode messages, plus a Chrome Issues-panel "No label associated with a form field" notice — investigated directly (see below) and confirmed to be a benign heuristic limitation, not a real missing label.

### 5.2 View an employee's full detail
- Row click → drawer opens, `role="dialog"` `aria-modal="true"`, labelled by the employee's name. ✅
- Background (`mat-drawer-content`) carries `inert` while open — confirmed via `document.querySelectorAll('[inert]')`, and the accessibility snapshot shows the directory content entirely absent from the tree while the panel is open. ✅
- Tab from the first focusable element inside the drawer stays inside (focus trap). ✅
- Close via button and via Escape: both now close it (fixed) and restore focus to the triggering `<tr>` row (fixed). ✅

### 5.3 Edit an employee's profile
- Form has no salary field (structurally absent, not just hidden). ✅
- Submitted an unknown `managerId` (999999999) → field-level error ("Employee 999999999 was not found") rendered under the Manager id spinbutton with `invalid="true"` and a matching `aria-describedby`-linked message, in addition to the snackbar — confirmed the fix-pass's server-error-to-field-error wiring genuinely works end-to-end against the real backend. ✅
- Corrected the value and saved → 200, panel returned to view mode. ✅

### 5.4 Change an employee's salary
- Submitted amount `0` → client-side validation blocked it; confirmed via network log that no `POST` was sent for that submission. ✅
- Submitted a valid amount/currency/date → `201`, panel's "Current salary" updated immediately (₹4,992,800 → ₹5,500,000). ✅

### 5.5 View an employee's salary history
- History tab shows both entries, newest effective date first, each with amount/currency/effective-date/recorded-at. ✅
- No edit/delete control anywhere in the rendered list. ✅

### 5.6 Add a new employee
- Dialog opens labelled "Add employee", autofocuses the Name field. ✅
- Filled all fields (name, department, job level, country, hire date, starting salary + currency), submitted → `201`, dialog closed, focus returned to the "Add employee" button (Material's own `MatDialog` focus-restore works correctly here — no fix needed). ✅
- Directory count went from 10,000 → 10,001; searching for the new name found exactly one row; its detail panel showed the correct profile and exactly one salary-history entry matching the starting salary. ✅

### 5.7 View organization-wide pay insights
- `/insights` reachable via the nav link. ✅
- Headcount 10,001 (includes the just-added employee). Nine per-currency tiles (AUD/BRL/CAD/EUR/GBP/INR/JPY/SGD/USD), each with its own headcount/average/median — no combined or converted total anywhere on the page. ✅
- Three breakdown tables (department, country, job level), every row still split per currency. ✅

### 5.8 Filter pay insights
- Filtered to Department=Legal → headcount narrowed to 311, breakdown tables narrowed consistently. ✅
- Added Job level=Executive (no Legal+Executive employees exist) → "No employees match the selected filters." — explicit empty state, not a misleading `0`. ✅
- Cleared both filters → headcount reproduced exactly `10,001`, matching the original unfiltered load. ✅

## Investigated and resolved as non-issues

- **Chrome Issues-panel "No label associated with a form field"** (count 3–6 depending on how many forms were open): directly inspected every flagged `mat-select`/input via `evaluate_script`. Every one has a correctly-resolving `aria-labelledby` (Department/Country/Job level/paginator page-size) pointing at real, descriptive text. This is Chrome's built-in heuristic not fully recognizing Angular Material's custom ARIA combobox pattern — not a real missing-label defect. No action taken.

## Regression check

Full test suite re-run after the fixes: backend `105/105` (`BUILD SUCCESS`), frontend `8 test files / 45 tests` — both green. Neither of the two real bugs was ever caught by the existing test suite, which is expected: they are specifically the class of defect (async focus-timing races, CSS `display:table-row` focus semantics) that only a real browser reproduces.
