# Goal 026C — raid assembly lifecycle hardening

## Status

`SUCCESS`

Goal 026 Checkpoint 4 сохраняет verdict `CHANGES_REQUIRED` до независимого review corrective 026C. Corrective 026C имеет статус `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`. Goal 026 overall остаётся `IN_PROGRESS`; Checkpoint 5 — `NOT_STARTED`.

## Summary

Закрыты ровно три findings независимого review CP4:

- wall epoch milliseconds отделены от monotonic logical nanoseconds;
- terminal raid Assemblies освобождают live capacity и переходят в bounded history;
- exact joined participant может завершить `raid.participate` после READY лидера.

CP1/CP2/CP3 semantics, recruitment scoring, consent, staging priority/radii, per-Party routes, REAL handling, raid authority и combat/entry/retreat/loot scope не менялись.

## Changed files

Production:

- `java/org/l2jmobius/gameserver/phantoms/raid/PhantomRaidAssemblyService.java`
- `java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java`

Focused verification:

- `test/java/org/l2jmobius/tests/phantoms/PhantomRaidAssemblySuite.java`
- `build.xml`

Documentation/package:

- `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`
- `docs/PHANTOM_BOTS_ROADMAP.md`
- `docs/phantoms/tasks/026c-raid-assembly-lifecycle/CODEX_LAUNCHER.txt`
- `docs/phantoms/tasks/026c-raid-assembly-lifecycle/PACKAGE_MANIFEST.json`
- `docs/phantoms/tasks/026c-raid-assembly-lifecycle/PRIOR_INDEPENDENT_REVIEW.md`
- `docs/phantoms/tasks/026c-raid-assembly-lifecycle/TASK.md`
- этот отчёт.

Несвязанные untracked `docs/phantoms/tasks/026-checkpoint-2-command-channel-lifecycle/CODEX_LAUNCHER.txt` и `PACKAGE_MANIFEST.json` не изменялись и не включаются в commit.

## Architecture decisions

### Clock bridge

`PhantomRaidAssemblyService` получает два `LongSupplier`:

- `wallClock` для goal deadline validation, expiry и `ReadyReceipt.completedAtMillis`;
- `logicalClock` для `PhantomPartyRouteCoordinator.request/advance`, Navigation deadline и `PARTY_ROUTE` external-action deadline.

При создании route вычисляются:

```text
remainingMillis = deadlineEpochMillis - wallNow
durationNanos = remainingMillis * 1_000_000
logicalDeadline = logicalNow + durationNanos
```

`subtractExact`, `multiplyExact` и `addExact` дают fail-closed результат при overflow; неположительная duration и отрицательное logical value также fail closed. Production wiring: `System::currentTimeMillis` + `System::nanoTime`. Контракты `PhantomGoal`, `PhantomNavigationRequest` и `PhantomNavigationService` не менялись.

### Live/terminal lifecycle

`_active` содержит только nonterminal Assembly. READY/BLOCKED/EXPIRED/CANCELLED выполняют exact owned cleanup, удаляют exact live instance и записывают immutable `TerminalAssembly` в insertion-ordered history maximum 256. History хранит только identity, terminal result, bounded candidates и optional immutable READY receipt; pending invitations/routes не удерживаются.

Exact terminal `AssemblyIdentity` возвращает прежний `AdvanceResult`. Новая revision имеет новую identity и создаёт fresh live state. `cancel` остаётся exact revision-safe. `readyReceipt(leaderProfileId)` читает bounded terminal history и переживает release live state.

Focused evidence:

- после 64 distinct terminal leaders: `activeAssemblies=0`, `terminalAssemblies=64`;
- exact terminal identity повторно возвращает тот же result;
- revision 1 входит в `WAITING_CONSENT`, stale revision 0 cancel возвращает false;
- после exact cleanup новой revision 65-й distinct leader входит в `WAITING_CONSENT`, `activeAssemblies=1`;
- terminal history остаётся `<=256`;
- после READY: `activeAssemblies=0`, receipt доступен через `readyReceipt`.

### Late participation

`participation(profileId, goalId, revision)` сначала валидирует exact ACTIVE participant goal и wall deadline, затем проверяет bounded live state и bounded terminal READY history. JOINED требует одновременно matching content, membership в исходном bounded candidate set и membership в final canonical force из READY receipt. Поэтому exact joined candidate получает JOINED после release assembly, а unrelated same-content и mismatched-content profiles fail closed. EXPIRED проверяется до terminal evidence. `raid.participate` assembly не создаёт.

## DB, migrations and configs

DB/schema/migrations/config keys не добавлялись и не менялись. Production DB не использовалась.

## Commands and test results

Git/read-only scope:

- `git status --short --branch`
- `git rev-parse --show-toplevel`
- `git rev-parse HEAD`
- `git branch --show-current`
- `git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}'`
- bounded `git diff`, `git diff --stat`, `git diff --check`

Verification:

- `.phantom-local/apache-ant-1.10.17/bin/ant.bat compile`: `BUILD SUCCESSFUL` (единственный отдельный compile).
- `phantom-raid-gathering-goal026c-test`: 5/5 passed, seed `26002642`.
- `phantom-raid-decision-goal026c-test`: 4/4 passed, seed `26002642`.
- `phantom-raid-assembly-consent-goal026c-test`: 3/3 passed, seed `26002642`; запущен из-за constructor/Snapshot fixture API change.
- единственный `phantom-raid-assembly-goal026c-test`: 12/12 passed, seed `26002642`, `BUILD SUCCESSFUL`.
- единственный `.phantom-local/apache-ant-1.10.17/bin/ant.bat jar`: `BUILD SUCCESSFUL`; jars скопированы в рабочий `dist/libs`.
- strict UTF-8: 11/11 files decoded successfully.
- mojibake-маркеры в изменённых файлах проверены: совпадений нет.
- escaped Cyrillic в изменённых файлах проверены: совпадений нет.

Запрещённые CP2 lifecycle, raid-authority, CP3/026B, broad Goal017, plain `ant verify`, Goal025 и all-Phantom targets не запускались.

## Performance and lifecycle

Отдельный benchmark не запускался по task contract. Live capacity остаётся 64, terminal history ограничена 256, candidate list остаётся ограниченной `PhantomGoal.MAX_VALID_SOURCES=16`. Новые thread/task/DB query/global server scan не добавлены. Participation выполняет только bounded scan live/history.

## Deviations and diagnostics

Встроенный `apply_patch` не смог читать workspace из-за Windows sandbox ACL (`apply deny-read ACLs`), а packaged apply-patch wrapper получил WindowsApps `Access denied`. Поэтому exact replacements были применены внешним PowerShell с проверкой expected occurrence count и затем полностью проверены через Git diff.

`occurred_context_compaction: no`

## Limitations and risks

Terminal/READY history process-local и намеренно недолговечна после eviction/restart; durable raid saga остаётся вне scope. Реальный multi-Party raid runtime остаётся предметом independent review CP4/026C. Checkpoint 5 не начат.

## Git and delivery

- branch: `feature/phantom-world`
- required parent: `867ff96e1b19c4cd0435cb51bfd7c31d14cae762`
- commit subject: `fix(phantoms): harden raid assembly lifecycle`
- commit SHA: commit, содержащий этот отчёт; exact SHA фиксируется в final handoff
- push target: `origin feature/phantom-world`
- push result / remote HEAD: фиксируется в final handoff после ordinary push
- amend/rebase/squash/reset/force-push не выполнялись

## Next step

Независимый review corrective Goal 026C и повторная оценка CP4. Checkpoint 5 не начинать до принятия gate.
