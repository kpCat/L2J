# Goal 029 Checkpoint 1 — scale envelope and deterministic budget contracts

## Status

- Delivery status: `SUCCESS`.
- Goal 028C: `ACCEPT`.
- Goal 028 Checkpoint 5: `ACCEPT after Goal 028C`.
- Goal 028 overall: `ACCEPT`.
- Goal 029 Checkpoint 1: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`.
- Goal 029 overall: `IN_PROGRESS`.
- Required parent: exact `b528ee762f79c1d1f35e8d9ff0b39aaa0184f618`.
- Branch: `feature/phantom-world`.
- Upstream: `origin/feature/phantom-world`.
- occurred_context_compaction: `no`.

## Summary

Формализован текущий structural scale envelope без новых targets и без включения Phantom World. Добавлен один immutable/read-only `PhantomScaleEnvelope`, который строится из уже валидированных `PhantomPlayersConfig.Settings` и `PhantomNavigationPolicy`. Его bounded assessment читает существующие Scheduler/Materialization/Navigation snapshots, возвращает `WITHIN_BOUNDS` / `AT_CAPACITY` / `VIOLATED` и typed violations, но не владеет counters, overload state, workers, timers, persistence или config parsing.

Существующий `PhantomActivityOverloadLevel.NORMAL/ELEVATED/HIGH/CRITICAL` остаётся единственной overload truth и копируется assessment без повторной классификации.

Production Scheduler, Materialization и Navigation owners не изменялись: deterministic scenarios не выявили concrete existing bound/recovery violation. Новых config keys, tuning, gameplay/domain work и automatic tuning нет.

## Exact changed files

1. `build.xml` — четыре focused Goal029 CP1 targets с fixed seed `29002901`.
2. `docs/PHANTOM_BOTS_ROADMAP.md` — exact truth: Goal028C/CP5/Goal028 `ACCEPT`; Goal029 CP1 `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`; Goal029 overall `IN_PROGRESS`.
3. `java/org/l2jmobius/gameserver/phantoms/PhantomScaleEnvelope.java` — pure immutable envelope и bounded snapshot assessment.
4. `test/java/org/l2jmobius/tests/phantoms/PhantomScaleEnvelopeGoal029Checkpoint1Suite.java` — шесть deterministic scenarios и exact subsystem modes.
5. `test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java` — четыре Goal029 CP1 launcher modes.
6. `docs/phantoms/reports/029-checkpoint-1-scale-envelope-budget-contracts.md` — этот отчёт.

User-owned untracked task packages не изменялись и не включаются в staging/commit.

## Baseline envelope

Envelope получен из `new PhantomPlayersConfig.Settings(true, false)` и `PhantomNavigationPolicy.productionDefaults()`, то есть из существующих defaults/policies, без повторного чтения INI:

- scheduled profiles: `10000`;
- materialized actors: `32`;
- scheduler pulse: `100 ms`;
- scheduler profiles per pulse: `128`;
- population target: `0`;
- population ACTIVE target: `0`;
- population creation in flight: `2`;
- population boundaries per pulse: `64`;
- party operations per pulse: `64`;
- social cache profiles: `1024`;
- navigation queue/workers/tracked/cache: `256/2/10000/1024`.

Production `PhantomPlayers.ini` не изменён: `EnablePhantomSystem=False`, оба population targets равны нулю. Disabled effective Settings намеренно имеют zero capacity и отвергаются как источник nonzero envelope. Invalid relationship fixture не нормализуется, а бросает `IllegalArgumentException`.

## 10k scheduler capacity and fairness proof

Deterministic manual-clock scheduler использовал exact cap `10000`, pulse `100 ms`, budget `128`, shared no-op pulse driver и WARM homogeneous cohort:

- registrations `1..10000`: `REGISTERED`;
- registration `10001`: `CAPACITY_REACHED`;
- registered/ready/due никогда не превышали `10000`;
- каждый productive pulse доставлял не больше `128`;
- `ceil(10000/128)=79` productive pulses дали каждому profile минимум одну opportunity;
- после второго productive sweep minimum delivery count >=2;
- measured second-sweep max-min skew: `1`;
- `Slot` не содержит `Future`, `Thread` или `Executor`;
- manual scheduler retained scheduled task count: `0`.

Это structural fairness proof, не wall-clock throughput benchmark.

## Overload transition and recovery proof

Pure multiplier contract подтверждён для каждого enum:

- `NORMAL`: ACTIVE/NEARBY/WARM/BACKGROUND = `1/1/1/1`;
- `ELEVATED`: `1/1/2/2`;
- `HIGH`: `1/1/4/4`;
- `CRITICAL`: `1/1/8/8`.

Existing scheduler algorithm был нагружен 100 homogeneous WARM profiles при capacity `100` и budget `10`. Собранные `WorkItem.overloadLevel` включили exact `[NORMAL, ELEVATED, HIGH, CRITICAL]`; peak = `CRITICAL`. Вторая pressure wave создана через existing accepted signal coalescing, после чего signals сняты и очередь deterministic pulses дренирована. Final overload = `NORMAL`; latch отсутствует. ACTIVE/NEARBY не деградируются pure multiplier contract.

## Materialization cap proof

CP1 не открывал DB и поэтому не запускал DB-backed `PhantomProductionMaterializationSuite`. Вместо этого focused pure fixture использует exact immutable `PhantomMaterializationService.ServiceSnapshot` / `MaterializationSnapshot` schema и accepted cap/release/identity semantics:

- canonical admissions `1..32` приняты;
- peak retained ownership = `32`;
- 33rd admission не увеличивает ownership;
- injected retained cleanup failure сохраняет занятый capacity/identity slot;
- duplicate profile и duplicate character identity отклоняются;
- успешный release освобождает ровно один slot;
- более поздний 33rd admission после release проходит;
- envelope assessment принимает valid cap snapshot как `AT_CAPACITY`.

Это structural cap contract CP1, а не повторная DB/Player materialization integration проверка.

## Navigation saturation and recovery proof

Использованы production defaults, deterministic manual dispatcher и fake path backend без geodata:

- два worker claims заняты, `currentWorkers=2`;
- queue заполнена до exact `256`;
- следующий запрос получил `QUEUE_BACKPRESSURE`;
- measured queue peak = `256`;
- measured worker peak = `2`;
- после release dispatcher queue/workers/active requests вернулись в `0/0/0`;
- sequential computed paths заполнили cache до exact `1024`, eviction удержал cap;
- terminal result retention достиг exact `10000` и не превысил tracked cap;
- после drain и saturation новый valid submission снова принят;
- queue/workers/cache/active/completed/cooldown/progress snapshots проверялись против существующих policy caps на каждом шаге.

## Pure assessment and retained structural bounds

Valid snapshots на границе возвращают `AT_CAPACITY`. Impossible snapshots с scheduler `10001`, queue `257`, workers `3`, cache `1025` и tracked `10001` возвращают `VIOLATED` с bounded typed violations. Assessment переносит exact scheduler overload и не создаёт второй overload registry.

Дополнительно сохранены:

- scheduler signal sources/profile <= `16`;
- selected trace = one selected profile x `64`;
- replay = one process bundle x `64`;
- нет новых per-profile workers/tasks/timers/futures;
- нет нового metrics registry, global scan, DB/file persistence, unbounded collection или poller.

## Commands and results

Baseline read-only Git:

- `git status --short --branch` — branch/upstream подтверждены; user-owned untracked packages обнаружены;
- `git rev-parse HEAD` — exact required parent PASS;
- `git branch --show-current` — `feature/phantom-world`;
- `git rev-parse --abbrev-ref --symbolic-full-name "@{u}"` — `origin/feature/phantom-world`.

Development:

1. `.phantom-local/apache-ant-1.10.17/bin/ant.bat compile-tests` — PASS, 2208 production sources и 110 test sources.
2. Первый CP1 run — 4/6: два fixture assumptions, production owner failure отсутствовал.
   - overload repressure через time advance не наполнил ready queue из-за уже применённых cadence multipliers;
   - navigation destination превысил existing `maximumLocalStraightDistance=12000` и корректно завершился `ROUTE_BUDGET_EXCEEDED`.
3. Второй CP1 run — 5/6: единственное падение было Java boxed expected `Long(0)` против actual `Integer(0)`; значения в сообщении были `0/0`.
4. После исправления только fixture/assertion CP1 — PASS `6/6`, seed `29002901`.

Final exact ordered invocation:

`.phantom-local/apache-ant-1.10.17/bin/ant.bat phantom-scale-envelope-goal029cp1-test phantom-scale-scheduler-goal029cp1-test phantom-scale-materialization-goal029cp1-test phantom-scale-navigation-goal029cp1-test jar`

Results:

1. CP1 focused — PASS `6/6`, seed `29002901`.
2. Exact Scheduler — PASS `2/2`, seed `29002901`.
3. Exact Materialization — PASS `1/1`, seed `29002901`.
4. Exact Navigation — PASS `1/1`, seed `29002901`.
5. Ровно один final `jar` после final Java/test changes — PASS; LoginServer/GameServer/DatabaseInstaller jars built, server jars copied в `dist/libs`.
6. Total final Ant time: `1 minute 14 seconds`.

Goal018–028 aggregates, production/test DB, real geodata, long stress/performance/soak и full world не запускались.

## DB, config, performance and architecture

Production/test DB не открывались и не изменялись. Schema/migration/config changes отсутствуют. `PhantomPlayers.ini` не изменён. Heap MB, GC distribution, DB QPS, final hardware sizing и long-running plateau в CP1 не измерялись и не заявляются.

Production bug fix: отсутствует. Изменения Scheduler/Materialization/Navigation owners не потребовались.

## Static, encoding and scope

- `apply_patch` не вызывался;
- изменения выполнены small unique exact-anchor UTF-8-no-BOM temp + same-directory atomic moves;
- UTF-8 BOM в изменённых файлах отсутствует;
- temporary `*.goal029cp1.tmp` отсутствуют;
- `git -c core.whitespace=cr-at-eol diff --check` — PASS;
- no-other-chronicle scope — PASS;
- production INI/config keys/caps — zero diff;
- user task packages — read-only;
- mojibake-маркеры в изменённых файлах проверены;
- escaped Cyrillic в изменённых файлах проверены.

## Deviations, limitations and risks

- Materialization CP1 доказывает structural cap/identity snapshot contract без DB-backed Player lifecycle. Existing production integration fixture требует DB и сознательно не запускался из-за hard out-of-scope.
- Navigation fake backend доказывает queue/worker/cache/tracked bounds и recovery, но не real geodata throughput.
- Scheduler proof измеряет deterministic productive opportunities, а не real-time CPU/latency.
- Long soak, heap/GC/DB QPS остаются последующими Goal029 checkpoints.
- Independent review обязателен; CP1 не повышается до `ACCEPT` самостоятельно.

## Git and delivery

Git-команды разрешены TASK для exact parent/branch/upstream, bounded diff/scope verification, ordinary atomic commit и push. Использованы baseline status/rev-parse, bounded diff/name/status/diff-check, exact allowlist staging verification, ordinary commit и push. Amend/rebase/reset/squash/merge/force push не используются.

Commit subject: `feat(phantoms): define scale budget envelope`.

Commit SHA и push result приводятся в финальном сообщении после ordinary commit/push: self-referential SHA невозможно записать внутрь того же atomic report-bearing commit.

## Next step

Independent review Goal 029 Checkpoint 1. До review CP1 остаётся `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`, Goal029 overall — `IN_PROGRESS`. Long soak/DB-rate checkpoint не начинается до принятия CP1.
