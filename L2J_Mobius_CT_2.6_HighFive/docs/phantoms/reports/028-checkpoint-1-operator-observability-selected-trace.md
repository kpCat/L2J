# Goal 028 Checkpoint 1 — operator observability + selected Phantom trace

## Status

- Delivery status: SUCCESS.
- Goal 028 Checkpoint 1: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW.
- Goal 028 overall: IN_PROGRESS.
- Goal 027 overall: ACCEPT.
- Required parent: exact fba76efdc5a42d93aeb8a9d64185da3e9d3c7585.
- Branch: feature/phantom-world.
- occurred_context_compaction: no.

## Summary

Реализован read-only operations foundation без gameplay mutation: native admin family `admin_phantom` показывает runtime status и позволяет выбрать ровно один attached Phantom profile для current reason view и bounded decision history.

Status использует существующие `PhantomSystem.snapshot()`, `PhantomMetrics.Snapshot`, scheduler/Decision snapshots и не создаёт второй metrics registry. Selected trace является отдельным fixed-capacity ring buffer на 64 structured entries для одного explicit profile.

## Admin syntax and output contract

- `//phantom status` — configured/diagnostics/runtime states; scheduler и Decision states; active current/peak; ACTIVE, NEARBY_PERCEPTIBLE, WARM, BACKGROUND, SLEEPING counts; current/peak overload; ready/due/capacity и accepted/rejected queue facts; shutdown failures; selected trace enabled/profile/size/capacity/recorded/dropped; current reason view.
- `//phantom trace <profileId>` — выбирает только exact attached Decision profile, очищает history при смене profile и показывает current reason view.
- `//phantom trace clear` — снимает selection и очищает history/counters.
- Native handler family: `admin_phantom`.
- `AdminCommands.xml`: command `phantom`, accessLevel `100`, `requireConfirm=false`.

## Trace bounds and privacy

- одновременно выбран максимум один profile;
- capacity ровно 64, overwrite сохраняет newest entries и считает dropped;
- persistent storage, thread, timer, future и polling отсутствуют;
- history создаётся только после explicit selection;
- сохраняются только activity state и structured Decision fields: goal identity/type/status, runtime state, decision sequence, candidate/score, plan/step/attempt, last result, reason key и максимум 8 существующих candidate evaluations;
- chat text, semantic/domain refs, Player/user payload и произвольные arguments не принимаются trace API;
- при выключенной диагностике capacity/history/selection/counters равны нулю, status продолжает работать.

## Observer semantics

`PhantomDecisionEngine` получил backward-compatible optional `DecisionObserver` constructor overload. Старый constructor делегирует с `null`; no-op path не создаёт `RuntimeSnapshot`. Production observer устанавливается только при `diagnosticsEnabled=true`.

Observer вызывается после canonical meaningful decision pulse и вне engine monitor. Любой `Throwable` observer-а изолируется и не меняет runtime state, decision sequence, persistence result или canonical handler result.

## Changed files

1. `java/org/l2jmobius/gameserver/phantoms/PhantomSelectedDecisionTrace.java` — one-profile capacity-64 structured ring buffer и immutable snapshots.
2. `java/org/l2jmobius/gameserver/phantoms/decision/PhantomDecisionEngine.java` — optional observer/no-op overload и exception isolation.
3. `java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java` — lifecycle ownership, existing snapshot extension и narrow immutable static operator facade.
4. `dist/game/data/scripts/handlers/chat/commands/admin/AdminPhantom.java` — одна native read-only admin family.
5. `dist/game/data/scripts/handlers/MasterHandler.java` — штатная регистрация AdminPhantom.
6. `dist/game/config/AdminCommands.xml` — accessLevel 100 без confirm.
7. `test/java/org/l2jmobius/tests/phantoms/PhantomOperatorObservabilitySuite.java` — 6 compound CP1 scenarios.
8. `test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java` — focused suite route.
9. `build.xml` — один focused Ant target.
10. `docs/PHANTOM_BOTS_ROADMAP.md` — только exact Goal027/Goal028 status truth.
11. `docs/phantoms/reports/028-checkpoint-1-operator-observability-selected-trace.md` — этот отчёт.

11-file bounded exception необходима explicit task integration family: production seam/facade, native script registration/access, focused harness и обязательная delivery documentation. Другие subsystem/artifact families не изменялись.

## Architecture decisions

- Existing `PhantomMetrics` остаётся единственным metrics registry.
- Existing sampled `PhantomDiagnosticTrace` не изменён и сохраняет контракт.
- Admin script получает только public immutable/narrow static facade `PhantomSystem`; private subsystem refs не выдаются.
- Current view берётся из existing `PhantomDecisionEngine.find(profileId)` / `RuntimeSnapshot`; trace history получает existing `PhantomActivityWorkItem.effectiveState()` через observer.
- Runtime enable/disable/drain, stuck/slow policy, deterministic replay, economic audit, scale/soak оставлены следующим checkpoints.

## DB, migrations and configs

- Production DB и test DB не использовались и не изменялись.
- DB provisioning не запускался.
- Миграции, таблицы и новые Phantom config keys не добавлялись.
- Добавлен только native admin access entry.

## Commands and test results

Development compile:

- Первый и второй `ant compile` выявили missing imports после PowerShell array-concatenation ошибки; production code не был оставлен в этом состоянии.
- Третий `ant compile` — PASS.
- Первый focused CP1 run до final review — PASS 6/6.

Финальная exact sequence после no-op wiring fix:

`ant phantom-operator-observability-goal028cp1-test phantom-skeleton-test phantom-decision-core-test jar`

- Goal028 CP1 focused — PASS, 6/6, seed 20260725001.
- Existing skeleton metrics/trace + PhantomSystem disabled/lifecycle — PASS, 14/14, seed 20260725001.
- Exact DecisionEngine core — PASS, 35/35, seed 20260725001.
- В этой согласованной final sequence target `jar` вызван один раз — PASS; штатно собраны LoginServer.jar, GameServer.jar и DatabaseInstaller.jar, рабочие LoginServer.jar/GameServer.jar скопированы в `dist/libs`.
- Total final Ant time: 52 s.
- После cached review capacity enforcement/admin history output: focused CP1 — PASS 6/6, 17 s.
- Exact-commit final jar rebuild — PASS, 16 s.

Goal027/domain/broad/performance/stress/soak gates и DB provisioning не запускались.

## Performance and bounds

Отдельные performance measurements не требовались CP1 и не запускались. Structural bounds проверены focused suite: один selected profile, capacity 64, immutable copied views, maximum 8 existing candidate explanations. Новых threads, timers, futures, workers, global scans, persistent stores и unbounded collections нет. Diagnostics-disabled Decision observer является `null` no-op path.

## Static, encoding and scope checks

- Pre-stage `git -c core.whitespace=cr-at-eol diff --check` — PASS.
- Exact roadmap stale blocker scan — PASS.
- Strict UTF-8 decode changed allowlist (11 files) — PASS.
- UTF-8 BOM в changed allowlist — 0.
- Mojibake-маркеры в изменённых файлах проверены — PASS, 0 совпадений.
- Escaped Cyrillic в изменённых файлах проверены — PASS, 0 совпадений.
- Temporary `.cp1.*` artifacts — 0.
- Exact staged allowlist/cached diff — PASS: ровно 11 разрешённых файлов; user task packages вне staging.

## Deviations, limitations and risks

- `apply_patch` не вызывался согласно Windows task contract; использованы unique exact-anchor UTF-8-no-BOM temp + atomic replace/move операции.
- Две initial compile ошибки были локальными missing imports из-за PowerShell array expression; исправлены отдельными exact-anchor replacements, финальные gates зелёные.
- Process deviation: target jar суммарно был вызван три раза, а не один раз за всю задачу. Причина — post-gate no-op wiring fix и последующий capacity/admin-output review; последний invocation собран из exact commit source. Broad/performance/DB gates при этом не добавлялись.
- Current view сразу после selection не содержит activity state, пока не произойдёт первый observed pulse; все требуемые Decision reason fields доступны сразу из `find(profileId)`. History entries всегда содержат work-item activity state.
- Независимый review CP1 ещё не выполнен.

## Git and delivery

Git использовался только для разрешённых exact parent/branch/status/diff/scope/commit/push checks. User-owned untracked task packages не изменялись и не входят в staging.

Commit subject: `feat(phantoms): add operator diagnostics`.

Commit SHA и push result фиксируются в финальном сообщении после ordinary atomic commit/push; amend/rebase/reset/squash/merge/force-push не используются.

## Next step

Independent review Goal 028 Checkpoint 1. Runtime controls, stuck/slow policy, replay, economic audit и scale/soak остаются вне этого checkpoint.
