# Goal 030 Checkpoint 3 — Release decision

## Решение

Checkpoint 3: `ACCEPT`.

Goal 030 overall: `ACCEPT`.

Оба новых CP3 production-composed target, CP1 progression regression, affected runtime regressions и единственный `ant jar` прошли. Финальный release decision — `ACCEPT`; coverage matrix содержит 20 covered domains и 0 pending.

Shipped runtime остаётся disabled by default / fail-closed: `EnablePhantomSystem=False`, population target `0`, ACTIVE target `0`.

## Baseline и среда

- Accepted parent и исходный local/remote HEAD: `2c2539ec29827bbe4880a3ab587e4f1f7d7c071a`.
- Branch: `feature/phantom-world`.
- Trusted origin: `https://github.com/kpCat/L2J`.
- Goal 030 CP2: `ACCEPT` по независимому review.
- Runtime: Temurin JDK 25.0.4.1, Apache Ant 1.10.17, MariaDB 11.4.13.
- Все DB-backed проверки использовали только `127.0.0.1:3308/l2jmobiush5_phantom_test`; provisioning не запускался, production DB не использовалась.
- `occurred_context_compaction: yes`.

## Реализованная CP3 архитектура

Новые suites используют существующие production owners: `PhantomSystem.startConfiguredForTesting(settings)`, Population, Scheduler, Materialization, Decision, canonical `shutdownIfStarted()` и operator controls. Альтернативный lifecycle, Player, rollback или test framework не создавались.

Для deterministic failure injection добавлен один package-private seam, который устанавливает существующий `_shutdownFailureForTesting` только у активного configured runtime. Production behavior seam не вызывает.

Production-composed evidence выявил реальный blocker в существующем `State.FAILED` recovery branch: ветка пыталась завершить часть owners без повторного `beginStop()` и не повторяла shutdown ранних economy owners. `PhantomSystem.shutdown()` теперь в FAILED recovery повторяет штатный порядок остановки для Scheduler, Decision, Combat, Progression, Commerce, GameKnowledge, Topology, Navigation, MultipartyEconomy, Store и EconomyReservation, после чего использует существующие finish/cleanup paths. Gameplay behavior и `Shutdown.java` не менялись.

Исторический CP1 matrix-validator bounded-расширен только до exact vocabulary `COVERED_PRIOR`, `COVERED_CP1`, `COVERED_CP2`, `COVERED_CP3`, `PENDING_GOAL030`. Он сохраняет exact 20-domain/header/lineage/owner/target/evidence проверки, фиксирует accepted classes остальных 17 domains и допускает только заданные CP2/CP3 переходы трёх release-specific rows. Final CP3 owner независимо требует exact final matrix `11/6/1/2/0` внутри существующего третьего case, поэтому suite остаётся `3/3`.

## Restart evidence

Target `phantom-restart-failure-recovery-goal030cp3-test`, seed `30003003`: `PASS 3/3`.

Финальный rerun создал ровно один durable Population profile и materialized Player:

- before restart: profile `154`, character object ID `268480994`, reserved account `p4a`, component identities `[population.state]`;
- after canonical shutdown: configured singleton отсутствовал, Scheduler и Materialization были `STOPPED`, World object, PHANTOM lease, autosave ownership и chat observer отсутствовали; durable profile/character/account/component остались;
- after restart в том же JVM: тот же profile `154`, character `268480994` и account `p4a`; counts `profiles=1, characters=1, accounts=1`; duplicate rows `0`; тот же character снова materialized, присутствовал в World и имел PHANTOM lease;
- post-restart runtime functional: `sameCharacter=true, world=true, lease=PHANTOM`.

## Injected failure и FAILED → STOPPED recovery

На full production-composed runtime следующий shutdown был deterministic переведён через существующий failure path:

- первый `operatorDrain()` вернул `SHUTDOWN_FAILED`;
- system state был `FAILED`, desired mode — `DRAINED`;
- configured owner, exact World actor, PHANTOM lease, Scheduler/Materialization owners и одна retained materialization entry оставались наблюдаемыми; false STOPPED claim не публиковался;
- после снятия только test injection повторный drain прошёл существующую FAILED recovery branch и достиг `STOPPED`;
- configured singleton, World actor, identity lease, chat observer, Scheduler registrations и materialization entries были освобождены;
- durable profile/character/components сохранились;
- следующий production-composed restart восстановил тот же profile/character и завершился canonical clean shutdown.

## Rollback и shipped barrier

Target `phantom-release-decision-rollback-goal030cp3-test`, seed `30003004`: `PASS 3/3`.

- Drain: `mode=DRAINED`, `runtimeConfigured=false`, повторный drain idempotent; в финальном rerun durable identity `155/268480994/p4b` сохранена.
- Disable: `mode=DISABLED`, `runtimeConfigured=false`, повторный disable idempotent; durable recovery сохранила тот же profile/character, duplicates `0`.
- В DRAINED/DISABLED `startConfigured()` не поднимал runtime.
- Реальный shipped config подтвердил `EnablePhantomSystem=False`, population `0`, ACTIVE `0`; `operatorEnable()` вернул `CONFIG_DISABLED`, runtime не появился.

Rollback остаётся существующим `bounded drain -> disable` и отменяет runtime activation, а не persistent world history.

## Shutdown.java handoff

Focused structural contract подтвердил exact порядок:

`initial PhantomSystem.shutdownIfStarted()` < `disconnectAllCharacters()` < final `PhantomSystem.shutdownIfStarted()` retry < `ThreadPool.shutdown()`.

В `Shutdown.java` ровно два server-level Phantom drain calls. Final incomplete drain публикует `LOGGER.severe` evidence с `Final subsystem drain is incomplete` и retained-ownership warning. Полный server shutdown в test JVM не запускался; файл `Shutdown.java` не менялся.

## Verification

- `ant compile-tests`: PASS; 2219 production sources и 125 test sources, только две существующие warning о deprecated `System.runFinalization()`.
- `ant phantom-restart-failure-recovery-goal030cp3-test`: final PASS, `3/3`, seed `30003003`.
- `ant phantom-release-decision-rollback-goal030cp3-test`: PASS, `3/3`, seed `30003004`.
- `ant phantom-operator-runtime-controls-goal028cp2-test`: PASS.
- `ant phantom-cross-domain-autonomous-alpha-goal030cp2-test`: PASS.
- `ant phantom-server-shutdown-handoff-test`: PASS.
- `ant phantom-production-materialization-test`: PASS, `20/20`.
- `ant phantom-release-baseline-goal030cp1-test`: PASS, `3/3`; matrix `rows=20`, counts `11/6/1/2/0`, pending domains отсутствуют, shipped disabled и DB no-mutation подтверждены.
- `ant jar`: PASS, единственный запуск; 2219 production sources, `LoginServer.jar`, `GameServer.jar` и `DatabaseInstaller.jar`, total time 17 seconds.

Diagnostic red runs restart suite сначала выявили ordering fixture issue, затем описанный production FAILED-recovery defect. После конкретных corrections финальный rerun прошёл. Временные строки удалялись только по exact test-owned profile/character/account из allowlisted test DB; durable production data не затрагивались.

## Coverage matrix и финальное закрытие

Matrix сохранена с неизменными 20 строками и domain set:

- `11 COVERED_PRIOR`;
- `6 COVERED_CP1`;
- `1 COVERED_CP2`;
- `2 COVERED_CP3`;
- `0 PENDING_GOAL030`.

`activity-materialization` указывает actual CP2 target и имеет `COVERED_CP2 / -`. `restart-failure-recovery` и `rollback-release-control` указывают actual CP3 targets и имеют `COVERED_CP3 / -`. `pending:` targets и `PENDING_GOAL030` отсутствуют.

Release blockers отсутствуют. CP2 — `ACCEPT`; CP3 — `ACCEPT`; Goal030 overall — `ACCEPT`; final release decision — `ACCEPT`.

Оставшиеся не блокирующие риски: failure-injection seam package-private и не меняет production behavior; shipped activation остаётся ручной и fail-closed; ACCEPT не включает автоматический startup после server restart. Следующий Goal/Slice не начат.
