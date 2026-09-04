# Goal 033A — Causal historical Background catch-up prerequisite

Дата: 2026-09-04
Baseline: `76c9ce8a444bd8cf2d2a8d4916ff14c8a57f`
Результат: `BLOCKED_033A_CANONICAL_TOPOLOGY_INGRESS_REQUIRED`

## Решение

Обязательный read-first gate выявил фундаментально отсутствующий production seam до начала реализации. Causal catch-up не добавлен: half-working planner/cursor/fence без достижимого initial Background baseline создавал бы ложную готовность и нарушал fail-closed contract.

Managed Phantom создаётся через `PlayerCreationInitializer.resolveCreationLocation()` и сохраняет canonical стартовую позицию в `PhantomPopulationState`. `L2jPhantomBackgroundAuthority.capture()` принимает baseline только когда позиция `Player` однозначно совпадает ровно с одним committed topology anchor. Shipped `high-five-core.xml` не содержит anchors в authoritative population creation positions. Его единственный `backgroundEligible=true` edge соединяет только `giran.route.north` и `giran.farming.22859`; ни одна стартовая область population к этому graph не подключена.

Из этого следует, что требуемая последовательность

```text
linked profile
 -> maintenance materialize canonical Player
 -> planner persists farm.background goal
 -> dematerialize/store
 -> BackgroundService.afterStore() captures baseline
```

заканчивается fail-closed в `exactAnchor()` до `READY/DEAD`. Перемещение через direct teleport, выдуманный edge или второй Player lifecycle прямо запрещено task. Добавлять остальную production-реализацию до закрытия topology ingress нельзя.

## Read-first audit

Прочитаны:

- `Agents.md`, `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`, `docs/PHANTOM_BOTS_ROADMAP.md`, `docs/phantoms/PHANTOM_CURRENT_STATUS.md`;
- Goal033 report и полный Goal033A task package;
- workflow/task-package contracts;
- Background goal/authority/model/service/transaction/operation/state contracts;
- GameKnowledge target/spawn query model и Topology query/snapshot/anchor/role/edge model;
- Goal store, Materialization service/player/lifecycle bridge, Population manager/store/initialization;
- production composition в `PhantomSystem`;
- ближайшие Background production-composed tests и Goal030/031/032 harnesses.

Root `README.md`, `docs/phantoms/CONTEXT_INDEX.md` и `docs/phantoms/DEVELOPMENT_CHAT_HANDOFF.md` в этом модуле не найдены; повторный поиск не выполнялся.

Локальные аналоги:

- `PhantomAcquisitionSourcePlanner` — bounded read-only planning по immutable Knowledge/Topology с deterministic ordering и fail-closed source evidence;
- `PhantomBackgroundSuite` production corpus/position fixtures — authoritative NPC/spawn/anchor pair, exact committed anchor и real lifecycle;
- Goal030/031/032 suites — production composition, guarded DB, restart/rollback и reset cascade.

Паттерн для будущей реализации остаётся прежним: immutable real-data planning, exact topology evidence, canonical Materialization/store callback, durable component mutation в том же Background transaction и explicit admission ownership. Новый lifecycle, scheduler или formula copy не нужны.

## Ответы на семь обязательных вопросов

1. Attackable/targetable monster должен выбираться bounded страницами через `PhantomGameKnowledgeQuery.suitableTargets(TargetQuery)` с собственным canonical level Phantom, `NpcKind.MONSTER`, `attackable=true`, `targetable=true`, immutable generation и deterministic seed/ordering. Human level и production NPC literals не нужны.
2. Реальный spawn доказывается `spawnAreas(npcId, PageRequest)`/snapshot facts: instance `0`, positive configured amount, непустой `topologyNodeId`, existing node и точное совпадение node с выбранным anchor. Current Background authority уже повторно проверяет template, spawn capacity и hash.
3. Actual target anchor выбирается из `anchorsByNode()` только с role `FARMING`; `routeHint(currentAnchorId, targetAnchorId)` должен быть полным, а каждый edge — traversable и `backgroundEligible`. Этот шаг сейчас фундаментально заблокирован: canonical creation position не даёт `currentAnchorId`, а factual route от неё отсутствует.
4. Shot/summon constraints должны приходить из минимального read-only planning snapshot существующего `L2jPhantomBackgroundAuthority`, переиспользующего private `capability`, `validateShot` и `validateSummonResource`. Существующий `validateShotContract()` подтверждает правильную границу, но ему уже нужен сформированный goal; formulas в planner копировать нельзя.
5. Existing path — `PhantomMaterializationService.materialize()` → canonical `Player.load`/attach/world → persist exact goal → `PhantomMaterializedPlayer.cleanup()` → `player.storeMe()` → lifecycle `afterStore()` → `PhantomBackgroundService.afterStore()` → authority `capture()` → `captureBaseline()` → `READY/DEAD`. Новый load/spawn/store/delete stack не нужен. Сейчас path останавливается на exact-anchor precondition.
6. Historical interval должен расширить `PhantomBackgroundOperationKey.ActionKind` отдельными `HISTORICAL_FARM`/`HISTORICAL_TRAVEL` и включать request/session, operation generation/ordinal и interval cursor в digest. Cursor component mutation должна коммититься тем же `PhantomBackgroundTransaction.Command`, что rewards/background state.
7. Fence должен быть общим profile-level owner seam для `PhantomMaterializationService` и `PhantomDecisionEngine`: `NORMAL` отклоняется при `PENDING/RUNNING`, `HISTORICAL_BASELINE` допускается только под catch-up claim; existing materializer выполняет единственный Player lifecycle. Уже нормально materialized profile не может начать catch-up.

## Blocker evidence

- Population stores exact canonical creation coordinates resolved from current player templates; они не являются topology anchors.
- Shipped topology содержит 16 `FARMING` anchors, но production Background corpus audit признаёт поддержанным только один exact pair: `22859@giran.farming.22859`.
- Shipped topology содержит только один `BACKGROUND` edge: `giran.route.north -> giran.farming.22859`.
- `L2jPhantomBackgroundAuthority.exactAnchor()` требует ровно одно совпадение; отсутствие anchor отклоняется до baseline mutation.
- `advanceTravel()` начинает только от already committed anchor и не может создать ingress из произвольной canonical position.

Это не повод ослаблять exact-anchor invariant. Без factual ingress implementation не сможет выполнить initial baseline для новой managed population и не закроет blocker Goal033.

## Минимальный следующий unblock

Один отдельный bounded topology-ingress slice:

1. Получить authoritative набор population creation positions из existing `PlayerCreationInitializer`/player templates, без дублирования координат в Java.
2. Представить каждую position как validated instance-zero topology ingress anchor либо иной существующий topology-owned canonical ingress contract.
3. Добавить только evidence-backed routes от ingress к real-spawn `FARMING` anchors; route coordinates/modes/times должны подтверждаться existing navigation/geodata/runtime authority, а не ручным предположением.
4. Доказать production-corpus tests для каждой population archetype/class entry: unique exact ingress, non-empty valid background route и доступный real-data target вокруг собственного level.
5. После ACCEPT повторить Goal033A полностью. Goal033 и Goal034 до этого не начинать.

## Scope

Production Java/XML/schema/build targets не изменялись. Изменены только Roadmap, current status, handoff и этот report. Другие хроники, task packages, shipped config и accepted Goal030 semantics не затронуты.

- strict UTF-8 decode без BOM: PASS;
- mojibake-маркеры в изменённых файлах проверены: PASS;
- escaped Cyrillic в изменённых файлах проверены: PASS;
- временные `*.goal033a.tmp` отсутствуют.

## Проверки

Focused Ant gates — `BUILD SUCCESSFUL`, 7 минут 25 секунд:

1. `compile-tests` — PASS: 2220 production и 131 test source; только два прежних Goal029 warning о deprecated `System.runFinalization()`.
2. `phantom-topology-production-corpus-test` — PASS 7/7, seed `20260725001`.
3. `phantom-background-production-audit-test` — PASS 1/1, seed `15001501`.
4. `phantom-background-position-canonicalization-test` — PASS 2/2, seed `15001502`.
5. `phantom-population-reset-ownership-goal032-test` — PASS 3/3, seed `32003201`.
6. `phantom-population-reset-reseed-goal032-test` — PASS 2/2, seed `32003202`.
7. `phantom-local-play-readiness-test` — PASS 3/3, seed `31003101`.
8. `phantom-restart-failure-recovery-goal030cp3-test` — PASS 3/3, seed `30003003`.
9. `phantom-release-decision-rollback-goal030cp3-test` — PASS 3/3, seed `30003004`.

Final `ant jar` запущен ровно один раз после focused gates — `BUILD SUCCESSFUL`, 19 секунд.

Production DB `l2jmobiush5` не использовалась. DB-mutating suites направлены только на allowlisted `127.0.0.1:3308/l2jmobiush5_phantom_test` и выполняют cleanup.

## Git/process

Разрешены и использованы только TASK-bounded baseline inspection, final diff/scope verification, exact-path staging, commit и push. Reset/rebase/force не используются. Unrelated untracked task packages не включаются.

Commit subject: `phantom(goal-033a): add causal historical background catchup`.

## Итоговый блок

```text
Canonical target/anchor planner: NO
Initial canonical Background baseline: NO
Exactly-once historical interval cursor: NO
Normal materialization fence: NO
Direct XP/free resources used: NO
Production DB used: NO
Goal033 blocker closed: NO
Next: BLOCKED
```
