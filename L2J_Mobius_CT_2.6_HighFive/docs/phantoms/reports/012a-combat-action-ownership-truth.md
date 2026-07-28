# Goal 012A — combat action ownership truth

## Status

```text
Status: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Architecture result: bounded safety closure for accepted Goal 012 direction
Semantic production baseline: 8143cb7f89d348854fc469a0955b22405f23e9b6
Unrelated reviewed gitignore commit: 74dd973c167adf0a74e7af78ed7944e2518c16cb
Goal 012A parent: 74dd973c167adf0a74e7af78ed7944e2518c16cb
Branch: feature/phantom-world
Subject: fix(phantoms): harden combat action ownership
Manual gate: COMBAT_ACTION_OWNERSHIP_TRUTH_HARDENED_PENDING_INDEPENDENT_REVIEW
Goal 012: FIX_REQUIRED
Goal 012A: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Goal 013: NOT_STARTED / BLOCKED
Goal 014: NOT_STARTED
```

## Summary

Закрыты только findings независимого ревью Goal 012:

- shared worker получает явный accepted dispatch handle, единый
  dispatch/`STOPPING` gate, отмену scheduled-not-started работы и top-level
  `finally`;
- canonical cleanup хранит exact owned `ATTACK`/`CAST`/`PICK_UP` descriptor,
  сохраняет ActionLease при failure и выполняет bounded retry;
- loot success выводится только из положительного inventory/object evidence;
- selected skill ограничен hostile one-target route и повторно проверяется с
  exact mode session;
- respawn несёт exact plan token и повторно сверяет ownership после actor
  acquisition и на stop barrier.

Server core, `ThreadPool`, materialization/decision/knowledge semantics,
datapack, geodata, config, schema и Goal 013/014 не менялись.

## Changed files

Production:

```text
java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java
java/org/l2jmobius/gameserver/phantoms/combat/L2jCombatBackend.java
java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatActorLease.java
java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatBackend.java
java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatMetrics.java
java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatService.java
java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatSession.java
java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatSkillSafety.java
java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatStepHandlers.java
java/org/l2jmobius/gameserver/phantoms/combat/PhantomOwnedAction.java
java/org/l2jmobius/gameserver/phantoms/combat/PhantomRespawnRequest.java
```

Tests/build/verifier:

```text
build.xml
test/java/org/l2jmobius/tests/phantoms/PhantomCombatActionOwnershipSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomCombatCoreSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomCombatPerformanceSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomCombatServerIntegrationSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java
tools/phantoms/verify-task-012a.ps1
```

Documentation:

```text
docs/PHANTOM_BOTS_ROADMAP.md
docs/phantoms/architecture/COMBAT_KERNEL_CONTRACT.md
docs/phantoms/reports/012-capability-driven-combat-kernel.md
docs/phantoms/reports/012a-combat-action-ownership-truth.md
docs/phantoms/reviews/012-capability-driven-combat-kernel-review.md
docs/phantoms/tasks/012a-combat-action-ownership-truth/**
```

Это bounded exception к ориентиру 8–10 файлов: task заранее задаёт три
неразделимые artifact families — production ownership, focused/real tests и
обязательные report/review/roadmap/verifier. Независимые подсистемы не
затронуты.

## Architecture decisions

### Dispatch ownership

`DispatchResult` различает accepted и rejected постановку, а accepted result
обязан нести `DispatchHandle`. Один gate сериализует dispatch с переходом в
`STOPPING`. Worker claim принадлежит точной session generation и освобождается
top-level `finally` даже при `Throwable`.

### Cleanup ownership

`PhantomOwnedAction` описывает exact canonical action. Cleanup не использует
широкий `abortAttack/abortCast`, если текущая AI action уже foreign. Ошибка
cleanup переводит session в bounded retryable state и не закрывает
ActionLease. После трёх неудач состояние остаётся `FAILED`, видимо metrics и
shutdown contract; consume и успешный `finishStop()` запрещены.

### Causal loot truth

`LootCandidate` фиксирует world object, item ID, ground count и inventory count
до pickup. Успех подтверждается тем же inventory object либо положительным
дельта-evidence точного item ID. Все варианты исчезновения без такого evidence
завершаются как потеря, а не acquisition.

### Skill и respawn safety

`PhantomCombatSkillSafety` проверяет факты exact `Skill`: active, negative,
`TargetType.ONE`, не PvP-only, не suicide и не special. Backend получает exact
session mode для повторной проверки. `PhantomRespawnRequest` несёт exact plan
token; active и cleanup-pending session блокируют respawn, а после actor
acquisition повторно сверяются token, operation и lifecycle.

## DB, configs и migrations

- production DB `l2jmobiush5` не используется;
- real integration использует только `l2jmobiush5_phantom_test`;
- schema и migrations отсутствуют;
- config keys не добавляются;
- datapack, curated knowledge, geodata и другие хроники не меняются.

## Verification

Focused matrix:

```text
ant compile-tests: PASS, 2015 production + 54 test sources
ant phantom-combat-core-test: 47/47 ×3
ant phantom-combat-ownership-test: 17/17 ×3
ant phantom-combat-action-ownership-test: 33/33 ×3
ant phantom-combat-server-integration-test: 19/19 ×2
ant phantom-combat-performance-smoke: 1/1 ×2
```

Performance evidence каждого повторения:

```text
sessionsCompleted=10000
pulses=100000
threatOperations=100000
cancellations=10000
maximumWorkers=1
actorLeasesAfterRun=0
terminalSlotsAfterConsume=0
dispatchFailures=0
cleanupFailures=0
```

Все ordinary, headless, profile, materialization, scheduler, decision,
navigation, topology, Game Knowledge, combat, DB integration, scenario,
performance, negative-control и historical static routes выполнены единым
`ant verify`: `BUILD SUCCESSFUL`, 4 минуты 19 секунд. Intentional negative
controls вернули ожидаемые nonzero codes и были приняты harness.

```text
ant verify: PASS
ant jar: PASS
tools/phantoms/verify-task-012a.ps1: 102/102
GameServer.jar: required Goal 012A production entries present
GameServer.jar: test entries absent
```

После ordinary commit `verify`, `jar` и verifier повторяются дважды. Точные
SHA-256 verifier, commit SHA и remote equality передаются во внешнем handoff
без amend.

Mojibake-маркеры в изменённых файлах проверены отдельно. Escaped Cyrillic в
изменённых файлах проверен отдельно. Новых совпадений нет.

## Baseline deviation

Goal 012 commit — `8143cb7f...`. Отдельный unrelated commit `74dd973c...`
является reviewed baseline extension, непосредственным child Goal 012 и меняет
только корневой `.gitignore`, добавляя `*.l2j`. Он сохранён без повторного
включения в Goal 012A и без rewrite. Parent ordinary commit Goal 012A —
`74dd973c...`; production/config/schema drift в reviewed extension отсутствует.

## Limitations и risks

- Gate остаётся независимым: этот commit не принимает сам себя.
- Production combat candidates по-прежнему не зарегистрированы.
- PvP/party/raid/spoil/progression/commerce не входят в scope.
- Cleanup exhaustion остаётся явным `FAILED` ownership и требует operator
  reconciliation; lease не маскируется как освобождённый.
- Goal 013 не начата и заблокирована до независимого принятия Goal 012A.

## Git и next step

Разрешённые task git-команды используются только для baseline/scope guard,
exact diff, ordinary commit и push. История не переписывается, force push не
используется. Следующий шаг — независимое ревью exact Goal 012A commit; Goal
013/014 не начинать.
