# How this was built with AI

This project was built end-to-end with Claude Code using the **Anthara** plugin — a structured multi-agent workflow rather than one long freeform chat. This doc records the actual process and the reasoning behind it, not just the output.

## The chain

```
/anthara:start  →  /anthara:spec-writer  →  /anthara:planner  →  /anthara:plan-implementer
   (triage)          (requirements +           (execution              (build + review)
                    architecture, reviewed       plan, reviewed
                       by a human)                by a human)
```

1. **`/anthara:start`** read `kata.md`, classified the work as a large greenfield full-stack build, checked which engineering-standards packs applied to this repo (Spring Boot / Angular / accessibility / testing / clean-code conventions — no regulated-data packs like HIPAA/PCI were active), and routed to spec-writing.
2. **`/anthara:spec-writer`** turned the brief into [`docs/specs/001-employee-salary-management-spec.md`](specs/001-employee-salary-management-spec.md): named types and invariants, 8 outside-in vertical slices in dependency order, a named architectural style (modular monolith) with justification, and NFRs pulled from the repo's actual standards packs rather than invented. Tech/architecture/UI-layout decisions were made explicitly, each with a stated rejected alternative — see the spec's "Decisions made" content and §9 (deferred/accepted limits). **This is the point where I (the human) reviewed and approved the spec before anything was built.**
3. **`/anthara:planner`** read the spec plus a fresh orientation pass (`code-orienteer`, confirming the repo was truly greenfield) and produced [`docs/plans/001-employee-salary-management-plan.md`](plans/001-employee-salary-management-plan.md): concrete package/class/method structure for every slice, a test strategy per slice, a model-tier assignment per slice (see below), and a hard "floor" marking slice 1 for unconditional security/architecture review because it creates the database schema.
4. **`/anthara:plan-implementer`** executed the plan slice by slice, then ran an end-of-spec review and a fix pass (all detailed below).

## Model tiers, assigned deliberately per slice

The plan assigned each of the 8 slices a model tier based on how hard its logic actually was — not "run everything on the biggest model" and not "run everything cheap":

| Slice | Tier | Why |
|---|---|---|
| 5.1 Employee directory + project scaffolding + seed generator | **best** (Opus) | Establishes the whole project shape; the seed generator's deterministic, acyclic-by-construction manager assignment is genuinely non-trivial logic. Also hard-floored (creates the DB schema). |
| 5.2 Employee detail panel | excellent (Sonnet) | Standard read path + one accessibility pattern (focus trap). |
| 5.3 Profile editing | excellent (Sonnet) | Standard form + validation. |
| 5.4 Salary change | excellent (Sonnet) | The append-only invariant matters a lot, but the logic itself is a well-understood transactional pattern. |
| 5.5 Salary history view | **fast** (Haiku) | Pure read passthrough of data slice 5.4 already computed correctly. |
| 5.6 Add employee | excellent (Sonnet) | Composite create, reuses slice 5.4's write shape. |
| 5.7 Org-wide pay insights | **best** (Opus) | The riskiest logic in the whole plan: grouped median across multiple dimensions *and* currencies, with no SQL median function to lean on (SQLite doesn't have one). |
| 5.8 Insights filtering | excellent (Sonnet) | Extends 5.7's already-built aggregation with real filter criteria; no new algorithm. |

Each slice ran in its own subagent, in plan order, one at a time — later slices could read what earlier ones had actually built rather than guessing from the spec alone.

## Review — one pass, seven reviewers, in parallel

After all 8 slices landed, the review step ran **once** over the whole diff (not once per slice), fanning out 7 reviewers in parallel:

- One always-on reviewer covering architecture, code quality, test quality, and accessibility/design.
- One reviewer per active compliance/engineering pack with rules for this diff: Spring Boot Backend Standards, Java Testing Standards, Angular Frontend Standards, Accessibility Checklist (WCAG 2.1 AA), Incubyte Engineering Standards, and SonarJava Violations.

Result: **0 high-severity, 13 medium, 11 low findings** (with some overlap where two reviewers independently caught the same thing). Two of the mediums were reviewers correctly noticing genuine architectural trade-offs that had already been reasoned through in the plan (documented transparently in spec §9.5/§9.6 rather than silently overridden or silently ignored). The rest were real, fixable issues — two accessibility gaps, a test that silently swallowed its own cleanup failures, non-business-language test names, and server-side validation errors that weren't reaching the right form field — all addressed in one follow-up commit, with the full test suite re-run to confirm nothing broke.

## Verification — what was and wasn't confirmed in a real browser

The plan-implementer's final step tries to drive the running app in an actual browser (via a browser MCP tool) to confirm the UI slices work the way their acceptance criteria claim — not just that jsdom/component-level tests pass. **In this session, no browser MCP was actually reachable**, and the verification step said so explicitly rather than reporting a fake pass: see [`docs/specs/001-employee-salary-management-verification.md`](specs/001-employee-salary-management-verification.md) for the full manual checklist that should be run against the live app before treating the UI as production-confirmed. The app itself was started and confirmed reachable (backend seeded with 10,000 employees and serving on port 18080; frontend serving on port 4200) — the gap is tooling access in this session, not the app under test.

## Artifacts this process produced (all committed, in order)

1. `docs/specs/001-employee-salary-management-spec.md` — the requirements/architecture spec, reviewed and approved before any code was written.
2. `docs/specs/001-employee-salary-management-context.md` — codebase orientation (confirmed greenfield; fixed naming/lexicon decisions before slice 1 ran).
3. `docs/plans/001-employee-salary-management-plan.md` — the execution plan: structure, decisions, per-slice test strategy, model tiers, hard floors.
4. Eight `feat:` commits, one per slice, each landing code + tests together and ticking that slice's acceptance criteria in the spec.
5. One `fix:` commit addressing the end-of-spec review's genuine findings.
6. `docs/specs/001-employee-salary-management-verification.md` — the browser-verification attempt and manual checklist.

The git history is the most literal record of how this was built — `git log --oneline` reads top-to-bottom as: brief → spec → plan → slice 1 → slice 2 → ... → slice 8 → review fixes → verification.
