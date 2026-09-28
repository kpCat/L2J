# Continuation 005 — M1 OPEN, connected gate REQUIRED

Продолжение той же задачи. M2 не начат. Новых proof-задач/runner нет.
Baseline: `50600cefa3da849f231e3883ecdda5a31800b699`; прежний runtime source: `e92d7d438641f3f13158021675bd99e2489a7042`.

## Причины и изменения

Logical node/TARGETABILITY и успешная доставка signal больше не определяют physical materialization demand.
Один helper повторяет native World X/Y/Z cells и instance; registry хранит native buckets даже для unresolved logical position.
Physical snapshot публикуется до delivery; BACKPRESSURE виден отдельно, прежняя local identity перепроверяется перед cap.
Ecology имеет один coalesced wake/worker, quantum 100 ms и общий batch 4 profiles/16 intervals; urgent не вытесняет всю ordinary очередь.
Completion продолжает работу без population pulse; generation защищает от поздних workers; stop ждёт настоящий commit.
Restored RUNNING/COMPLETE request проверяется по ID/window, inner cursor отделён от outer ecology cursor; partial RETRY расходует фактически committed intervals.
Semantic failures имеют typed reason; hot progress diagnostics читают cached state без SQL.
При native DEAD load обновляются только производные native vitals через существующий capture/store boundary; identity, XP/SP/EBD, position, receipt, clock и hashes проверяются точно.
Bounded exception к SOURCE_MAP: этот load blocker исправлен в существующем PhantomBackgroundService; отдельного lifecycle/authority слоя нет.
Runner использует общую native geometry и отличает MISSED_INITIAL_MATERIALIZATION от VISIBLE_DISAPPEARANCE; raw output сохраняется в ignored .phantom-local.

## TEST

Итоговый aggregate: 199/199 PASS (11 existing suites), ant -Dbuild=.phantom-local/m1-005-continuation-build phantom-m1-runtime-handoff-test, 3m34s.
Связанный native suite: 7/7 PASS в aggregate; последний TEST-only review follow-up также 7/7 PASS отдельным existing phantom-background-position-canonicalization-test.
Final RUNNING replay: первый claim 5 ms, ready 358 ms, 13 inner intervals; COMPLETE/outer-pending: claim 1 ms, ready 12 ms, 13 intervals без повторного advance.
Обе ветки: та же native identity 268435465, штатный travel 4691 координатных единиц до existing farm 20481, AutoPlay damage 4 HP, штатный capture/cleanup.
Cold queue: 10000 metadata entries, малая настоящая TEST cohort; ordinary получает работу; 4/16 проверены. Это не 10000 native Players.
Offline runner PASS: реальные saved snapshot types DateTime/ISO/DateTimeOffset/null; typed reasons; actual finally restore/stop с mocked Pilot calls.
Финальный reviewer проверил geometry/cap, общий budget, late worker/stop, pending rewards/cursors и hot diagnostics. TEST follow-up гарантирует shutdown при RED drain и не удаляет профиль с retained owner; arrival/movement/тот же Player проверяются assertions.

## Ограничения и NOT_OBSERVED

Текущее read-only PLAY чтение 1313 от 2026-09-28T19:12:28.564844400Z: RUNNING inner 29840583/29840595, outer 29840582, DEAD, level 3/class 18.
Это отличается от старого RED: outer 29839500, horizon 29843626. Старые inner payload неизвестны; replay создаёт согласованную isolated 13-minute TEST history.
TEST class/level отражают текущий probe; goal/anchor и identity из TEST builders, production account/payload не копируются. 4126 calendar minutes не считаются productive minutes.
Native death timer 45-second policy покрывает существующий contract target; это не заявление о новом wall-clock ожидании 45 s.
Hunters Village recovery anchor для high-level Giran corpse отсутствует в текущем corpus; XML anchors не добавлены. High-level TEST проверяет load/vitals/SP fence, не эту town route.
Два существующих store/capture writes при derived-vitals refresh не являются новой атомарной транзакцией; второй write может отказать и оставить штатный retained failure.
Broad planner 1..85, 10k native scale и новая live-location matrix не запускались. Старый broad 5/5 — прежнее evidence.
CONNECTED: новый run не выполнялся; свежий arm будет запрошен только после publication/clean build/deploy/health.

## Exact publication allowlist (относительно модуля)

- build.xml
- java/org/l2jmobius/gameserver/localplay/LocalPlayPilotActions.java
- java/org/l2jmobius/gameserver/phantoms/PhantomLocalProofSelector.java
- java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java
- java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundService.java
- java/org/l2jmobius/gameserver/phantoms/population/PhantomPopulationEcologyService.java
- java/org/l2jmobius/gameserver/phantoms/topology/PhantomHumanLocalityControl.java
- java/org/l2jmobius/gameserver/phantoms/topology/PhantomNativeLocalityEnvelope.java
- java/org/l2jmobius/gameserver/phantoms/topology/PhantomTopologyProfileRegistry.java
- java/org/l2jmobius/gameserver/phantoms/topology/PhantomTopologyService.java
- test/java/org/l2jmobius/tests/phantoms/PhantomBackgroundSuite.java
- test/java/org/l2jmobius/tests/phantoms/PhantomM1RuntimeHandoffSuite.java
- test/java/org/l2jmobius/tests/phantoms/PhantomOperatorObservabilitySuite.java
- test/java/org/l2jmobius/tests/phantoms/PhantomTopologyCoreSuite.java
- test/java/org/l2jmobius/tests/phantoms/PhantomTopologyPerceptionSuite.java
- docs/phantoms/tasks/PHANTOM-LIVE-M1-RUNTIME-HANDOFF-005/Run-M1RuntimeHandoff.ps1
- docs/phantoms/tasks/PHANTOM-LIVE-M1-RUNTIME-HANDOFF-005/STATE.md
- docs/phantoms/tasks/PHANTOM-LIVE-M1-RUNTIME-HANDOFF-005/continuation-after-stall/RESULT.md
- docs/phantoms/tasks/PHANTOM-LIVE-M1-RUNTIME-HANDOFF-005/continuation-after-stall/fixtures/README.md
- docs/phantoms/tasks/PHANTOM-LIVE-M1-RUNTIME-HANDOFF-005/continuation-after-stall/fixtures/native-envelope-expectations.tsv
- docs/phantoms/tasks/PHANTOM-LIVE-M1-RUNTIME-HANDOFF-005/continuation-after-stall/fixtures/observed-approach.tsv

Чужие tracked hunks и остальные untracked task inputs остаются вне публикации; .phantom-local/raw DB payload/credentials не stage.
mojibake-маркеры в изменённых файлах проверены: PASS, все заданные маркеры, exact 21 paths.
escaped Cyrillic в изменённых файлах проверены: PASS, все шесть regex patterns, exact 21 paths.
Publication/deployment SHA/hash и whitespace/scope guard будут записаны после controlled deployment.
