# Рабочий процесс Codex

1. New Codex dialog. Read archive, inspect own repo, source HEAD, protected refs. Create independent clone and new WIP branch from b85dd2a3; user checkout read-only.
2. Read existing proven LEARNING001 report/HANDOFF and direct code seams. Document concise architectural decisions. Identify what cannot be implemented in 002 and preserve expandable schema.
3. Implement `RUN OWNER + CLI START/STATUS/STOP` and safety first. Unit + read-only preflight; no GameServer while environment not attested.
4. Implement seeded Scenario Generator and legal preflight + reasons for rejecting impossible scene. Test across multiple source conditions without weakening acceptance rules.
5. Implement separate native episode runner reusing Combat/Party/World, record exact NATIVE and versioned rich episodes, SQL idempotence. Test Solo and Party.
6. Implement independent detached Director loop and offline trainer/evaluator hook with checkpoint/stop/resume. Confirm work continues after Codex terminal exit and reports progress; no timers on each actor.
7. Complete native acceptance in separate disposable TEST: multiple distinct scenes, sequential episodes without reboot per kill, train/evaluate, cold restart only when needed, checked PID/SHA/Geo/SQL and safe STOP.
8. Regression, benchmark, no P1/P2 in touched scope, Russian report and user runbook. Commit/push only new WIP. Keep exact source + evidence lists. Provide shortened next phase LEARN003 roadmap.

**Engineering velocity:** no arbitrary 2 bootstraps, no arbitrary 2 JARs. Before each expensive run explicitly print one-sentence expected outcome/hypothesis, what changed, success/fail signal and bounded stop condition. If repeat produces no new evidence, inspect root cause instead of duplicating. Distinguish infra blocker from game mechanics; resolve ordinary infra yourself. No false FULL for mock-only or synthetic loops. Do not begin LEARN003 within this goal.

**Owner feedback path:** For material choices affecting gameplay risk, source of learning reward or behavior/personality, propose options in report and do not silently rewrite owner design. Technical details of running processes and SQL are Codex responsibility.
