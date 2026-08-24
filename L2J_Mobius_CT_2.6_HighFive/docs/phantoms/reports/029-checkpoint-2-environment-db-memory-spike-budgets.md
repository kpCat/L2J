# Goal 029 Checkpoint 2 — environment DB/memory spike budgets

## Status

- Delivery status: SUCCESS.
- Goal029 Checkpoint 1: ACCEPT after Goal029A/Goal029B.
- Goal029A: ACCEPT after Goal029B.
- Goal029B: ACCEPT.
- Goal029 Checkpoint 2: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW.
- Goal029 overall: IN_PROGRESS.
- Required parent: exact ffd5e8db11344ecdb1e82b5995c053a73e2d2f51.
- Branch: feature/phantom-world.
- Upstream: origin/feature/phantom-world.
- occurred_context_compaction: no.
- Goal token usage at report creation: 282924; elapsed goal time: 1548 seconds.

## Summary

Добавлен short environment-dependent CP2 suite для exact 10 000 durable SHELL profiles. Suite использует real PhantomPopulationStore, PhantomProfileRepository и PhantomPopulationManager; PhantomPopulationTestDoubles.Ownership заменяет только внешний ownership port. Seed находится вне measured bootstrap window, advanceCreation не вызывается, accounts/characters/Players для 10k fixture не создаются.

Первый CP2 run доказал concrete single-owner defect в PhantomPopulationManager.start(): termination condition повторно вычислял page limit после публикации последней неполной страницы и выполнял лишний empty-page SELECT. Выполнена focused correction: requested pageSize фиксируется до запроса и используется для termination comparison. После correction measured bootstrap дал exact Com_select 40 и zero DML.

Реальный PhantomScheduler с manual monotonic clock выдержал две однородные WARM pressure waves: обе достигли CRITICAL через существующий overload algorithm и восстановились до NORMAL после снятия pressure, без DB traffic и memory ratchet.

## Exact changed files

1. build.xml — seed 29002902 и forked phantom-scale-environment-goal029cp2-test, cwd dist/game, Xmx4096m, timeout 1200000, без provisioning.
2. docs/PHANTOM_BOTS_ROADMAP.md — accepted CP1/029A/029B truth, CP2 pending independent review, Goal029 overall IN_PROGRESS.
3. java/org/l2jmobius/gameserver/phantoms/population/PhantomPopulationManager.java — focused single-owner last-page termination fix.
4. test/java/org/l2jmobius/tests/phantoms/PhantomScaleEnvironmentGoal029Checkpoint2Suite.java — guarded DB/Hikari/JVM/population/scheduler environment suite.
5. test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java — новый CP2 launcher mode.
6. docs/phantoms/reports/029-checkpoint-2-environment-db-memory-spike-budgets.md — этот отчёт.

Production config, schema, DatabaseFactory, Hikari config и другие хроники не изменялись. User-owned untracked task packages оставались read-only и не включаются в staging.

## Architecture and production correction

Сохранён production persistence path:

PhantomPopulationManager
→ PhantomPopulationStore.loadManagedAfter
→ PhantomProfileRepository.listManagedAfter.

Новых config keys, metrics registry, overload classifier, worker pools или per-profile tasks нет. Scheduler использует существующие NORMAL/ELEVATED/HIGH/CRITICAL и caps 10000/128. Population использует caps target 10000, activeTarget 0, maximumScheduled 10000, maximumMaterialized 32, creationLimit 2, boundaryBudget 64.

Разрешённая production correction ограничена одним owner и двумя локальными строками start(): pageSize теперь вычисляется перед loadManagedAfter и тот же requested pageSize завершает scan при неполной странице. Это устраняет 41-й empty-page SELECT и защищено exact CP2 regression evidence.

## DB and admin status safety

Guarded DB identity:

- host: 127.0.0.1;
- port: 3308;
- database: l2jmobiush5_phantom_test;
- dedicated user: l2j_phantom_test;
- pool maximum: 4;
- schema aggregate SHA-256: 394F26E9792EF56B77E1293DFCB7A336BEFE48F224140CCD7626475EDE1BE04E.

Production DB l2jmobiush5 не использовалась и не изменялась. Read-only admin URL принят только как credential-free jdbc:mysql localhost/127.0.0.1:3308 без schema/query/fragment/userinfo. Admin SQL hardcoded только для SHOW GLOBAL STATUS и bounded information_schema.PROCESSLIST isolation evidence. Credential values не печатались и не записывались в source/report.

Seed exact 10000 canonical SHELL profiles:

- diagnostic seed time: 17701 ms;
- diagnostic seed DB delta: select 20000, insert 20000, update 0, delete 0;
- measured window начинается только после seed;
- exact owned profile IDs сохранены;
- contiguous owned range подтверждён;
- account/character residue для exact reserved accounts: 0;
- cleanup удалил только exact CP2 profile IDs;
- post-cleanup owned profiles/components/accounts/characters: 0.

Measured manager start:

- restored profiles: 10000;
- page size: 256;
- Com_select delta: 40 exact;
- Com_insert/update/delete delta: 0/0/0;
- queue drain DB delta: 0/0/0/0.

## Hikari, population queue and JVM observations

Hikari L2JMobiusPool:

- initial: active 0, idle 2, total 2, awaiting 0;
- manager scan peak: active 1, idle 2, total 2, awaiting 0;
- after drain: active 0, idle 2, total 2, awaiting 0;
- sampler threads: one bounded daemon sampler, joined before scenario completion;
- per-profile threads/futures/executors: absent in manager Entry and scheduler Slot.

Population ownership:

- queue peak: 10000;
- ownership operations: exact 30000 register/attach/signal;
- productive pulses: 469;
- total pulses: 469;
- lastPulseOperations: always <=64;
- optional bookkeeping pulse: not required.

JVM max heap: 4294967296 bytes.

Population heap:

- settled baseline: 1426071880 bytes;
- loaded 10k manager: 1440107752 bytes;
- loaded delta: 14035872 bytes, below +256 MiB;
- recovered after stop/clear: 1426113152 bytes;
- recovered delta: 41272 bytes, below +64 MiB.

Population GC observations:

- baseline→loaded: count +2, time +300 ms;
- loaded→recovered: count +2, time +288 ms;
- GC values are observations only; CP2 defines no GC latency SLA.

## Scheduler two-wave spike/recovery

Registered baseline after exact 10000 registrations:

- heap used: 1428721912 bytes;
- registered/ready/due never exceeded 10000;
- delivered work per pulse never exceeded 128;
- scheduler DB select/insert/update/delete delta: 0/0/0/0.

Wave 1:

- reached CRITICAL: yes;
- recovered level: NORMAL;
- transient observed peak: 1451790584 bytes;
- peak delta from registered baseline: 23068672 bytes, below +128 MiB;
- recovered heap: 1430177408 bytes.

Wave 2:

- reached CRITICAL: yes;
- recovered level: NORMAL;
- transient observed peak: 1446954624 bytes;
- peak delta from registered baseline: 18232712 bytes, below +128 MiB;
- recovered heap: 1430178072 bytes;
- recovery2 minus recovery1: 664 bytes, below +32 MiB.

After scheduler stop:

- registered/ready/due: 0/0/0;
- final settled heap: 1426155440 bytes, below registered baseline +64 MiB;
- GC baseline→recovery1: count +2, time +290 ms;
- GC recovery1→recovery2: count +2, time +291 ms.

## Commands and results

Baseline Git inspection:

- git status --short --branch — feature/phantom-world, upstream confirmed; existing user-owned untracked task packages detected.
- git rev-parse HEAD — exact required parent PASS.
- git branch --show-current — feature/phantom-world.
- git rev-parse --abbrev-ref --symbolic-full-name @{upstream} — origin/feature/phantom-world.

Development checks:

1. ant compile-tests — initial FAIL only on checked exception declarations in new test-only admin helper; no production behavior executed.
2. after minimal signature correction, ant compile-tests — PASS, 2208 production sources and 111 test sources; one JDK deprecation warning for task-authorized System.runFinalization in bounded settleHeap.
3. first CP2 run — 2/4; raw Com_select 41 exposed the concrete manager last-page defect. Suite cleanup completed.
4. after focused PhantomPopulationManager correction, CP2 — PASS 4/4, seed 29002902, total time 55 s.

Final exact ordered gates after final Java/test changes:

1. phantom-scale-environment-goal029cp2-test — PASS 4/4.
2. phantom-production-materialization-performance-smoke — PASS 2/2, total time 31 s.
3. phantom-scale-envelope-goal029cp1-test — PASS 6/6, seed 29002901, total time 21 s.
4. exactly one final jar — PASS, total time 19 s; LoginServer/GameServer/DatabaseInstaller jars built and server jars copied to dist/libs.

Full verify, broad DB aggregates, Goal016 aggregate, Goal028 aggregate, navigation/geodata, stress/soak and full-world gates не запускались.

## Static, encoding and scope

- Новые/изменённые text files записаны UTF-8 without BOM через small exact anchors, temporary same-directory files и atomic Move-Item.
- Две попытки apply_patch были отклонены Windows ACL до любой mutation; effective edits выполнены только temp+atomic fallback.
- UTF-8 BOM отсутствует во всех шести изменённых text files; temporary goal029cp2 files отсутствуют.
- mojibake-маркеры в изменённых файлах проверены: совпадений нет.
- escaped Cyrillic в изменённых файлах проверены: совпадений нет.
- git diff --check PASS; exact tracked allowlist и no-other-chronicle scope PASS; staging allowlist проверяется непосредственно перед commit.
- Admin credentials не присутствуют в tracked files.

## Deviations, limitations and risks

- Единственное production изменение — доказанный лишний empty-page SELECT в PhantomPopulationManager; multi-owner redesign не потребовался.
- settleHeap использует JDK management API, bounded System.gc/System.runFinalization и deadline 1.9 s; наблюдения являются coarse boundedness gate, не throughput/latency SLA.
- Global MariaDB counters требуют изолированного локального measured window. PROCESSLIST isolation проверена до/после окна; exact raw delta 40 подтверждает отсутствие дополнительного SELECT traffic в окне.
- CP2 не является long soak и не доказывает длительное resource plateau.
- Independent review обязателен; Goal029 overall не повышается до ACCEPT.

## Git and delivery

TASK разрешает baseline Git inspection, bounded diff/scope verification, exact allowlist staging, ordinary commit и push. Amend/rebase/reset/squash/merge/force push не используются.

Preferred commit subject: test(phantoms): prove environment scale budgets.

Commit SHA и push result указываются в финальном сообщении после ordinary report-bearing commit/push; собственный SHA невозможно самоссылочно записать в этот же commit.

## Next step

Independent review Goal029 Checkpoint 2. До review CP2 остаётся IMPLEMENTED_PENDING_INDEPENDENT_REVIEW, Goal029 overall — IN_PROGRESS. Long soak относится к следующему checkpoint и здесь не начинался.