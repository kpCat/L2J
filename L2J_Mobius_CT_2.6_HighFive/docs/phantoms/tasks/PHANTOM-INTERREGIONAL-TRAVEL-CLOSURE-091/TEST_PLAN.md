# TEST_PLAN — causal RED → native GREEN без бесконечной инфраструктуры

## 1. Read-first regression baseline

- Снять точные SHA source089 и commit090; confirm `OwnerConfig Verify` на source (без Save). Предыдущая 089 source-only Geo 3/4, Elf FAIL, native actions=0, DB/JVM restart=0. Не переписывать history.
- Воссоздать минимальный causal RED для: `candidate()` D1 graph0, `proposedStep()` route_absent, Elf Z frontier/search_budget, `submitWalkEpisode()` не используется в actual actor; missing durable leg. По возможности существующими 087/089 test owners, не начинать новые глобальные test frameworks.
- Короткая таблица impacted call sites + выбранное общее решение **до** производственных правок. При непонимании владельца atomic paid/persistence — STOP/architecture review вместо рискованного перехода.

## 2. Focused post-fix (один coherent set)

- Foreground-only admission для native real source, no background phantom fake edge; действительный NPC list/index and sourceRefs, affordable exact wallet, multi-leg stage selection.
- 3D native path: H/E/O, multi-layer Elf, blocked/uphill/return candidate; deadline, cancellation, visited, no-route-within-budget vs SEARCH_LIMIT, cycle/backtrack rejection, no jump over walls/water/missing Geo.
- Concurrent Player goal/epoch/lease, Party/Store/Combat claims, action-scope release before checkpoint; actual native move issued only after Geo proof.
- Typed durable intent: marker-free format2 and compact3 **old bytes unchanged**; state copy/transaction/goal CAS preservation; corrupted/unknown marker rejected; restart same identity with rebind, repeated same token no second payment.
- GK negative matrix: insufficient fee, siege/karma, unavailable NPC, stale index/native XML, paid_without_arrival, replay, changed wallet, stopped process. No side effects on rejection.
- Postpaid farm reward and unrelated 090 Rates/OwnerConfig checks. Existing 089 `PhantomWalkCorridor064Test`, `PhantomNormalGatekeeperPureTest`, `PhantomLocalFarmRecoverySuite`, 085 spot diversity as relevant. Avoid full ant verify unless focused fails implicate wider surface.

## 3. Native isolated campaign

- Only physically disjoint newly guarded TEST091 schema, dedicated shadow JDK25/Ant and Geo203. Provision test actors from actual native `Player` and stock quest/skills/gear where needed; mark fixture-before inventory and Adena. No shared test DB, release078 or test087/089 resurrection.
- Test 3 families (Human, Elf, Orc; level40/76/80 eligible) and Giran control. At least two paid NORMAL GK; repeat with a different legitimate destination. Per family record sourceNpcId/index, verified WALK checkpoints and actual arrival, real teleport, farm NPC and damage/reward. First successful natural journey triggers measured existing goal owner, not a separate test-only execution engine.
- For one journey, stop after *durable* confirmed paid leg with still unfinished travel, orderly JVM shutdown, sameDB/new JVM, verify wallet/items/goal/revision/position/marker before native resume; then finish subsequent leg/farm without paying previous leg again.
- Negative on ambiguous paid intent with intentional test boundary: don't use OS kill/kill-process on foreign JVM, don't emulate duplicate payout; fail closed/no automatic retry. Check old snapshot without journey marker normal start.

## 4. Performance / budget

- Record per family `episode_count`, native `PathFinding` queries, explored nodes/edges/labels, wall/CPU/heap/GC, max queue depth, p50/p95 request cost, changes vs 089. No infinite retries; no exponential cluster of background workers, no path query on every AI tick.
- Bounded staged test with one deliberate first failing fact and one final focused/native run after causal fix; a third unrelated blocker requires separate evidence, not a huge brute-force soak. Regression PASS at source-only scale may not be presented as SERVER_VERIFIED.

## 5. Delivery checks

- `git diff --check` (also CRLF-aware if applicable, report both), strict UTF-8/mojibake/escaped Cyrillic, XML/native provenance, 090 config parsed+SHA equality, exact file staging, no unrelated executable/library changes; compile + normal push on feature only.
- Evidence schema in `EVIDENCE.md`, failure/root-cause table, observed vs simulated distinctions, remaining NOT_RUN. Report own JVM stop, DB connections0, untouched protected data. No separate user-facing server release.
