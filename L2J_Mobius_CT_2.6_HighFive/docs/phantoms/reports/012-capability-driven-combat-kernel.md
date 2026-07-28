# Goal 012 — capability-driven combat kernel

## Status

```text
Status: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Baseline: 003604b4f7bda2a8d224d0adcf6349c088154e10
Parent: 003604b4f7bda2a8d224d0adcf6349c088154e10
Branch: feature/phantom-world
Subject: feat(phantoms): add capability driven combat kernel
Manual gate: PENDING_INDEPENDENT_REVIEW
Goal 013: NOT_STARTED
Goal 014: NOT_STARTED
```

Commit SHA, push/remote equality и финальный verifier SHA-256 передаются во
внешнем final handoff после ordinary commit/push.

## Goal 011A и Stage II

Goal 011A независимо принят на commit
`003604b4f7bda2a8d224d0adcf6349c088154e10`; immutable evidence и verdict
зафиксированы в отчёте и новом review-файле. Goal 011 имеет `ACCEPT after Goal
011A`, Stage II имеет `COMPLETE`, Goal 012 был разрешён.

## Factual audit

Переиспользованы подтверждённые локальные server seams:

- полный materialization `ActionLease` из
  `PhantomMaterializationService.tryAcquireAction`;
- canonical `PlayerAI` intentions `ATTACK`, `CAST`, `PICK_UP`;
- exact известный `Skill` и штатные cast preconditions;
- `Player.rechargeShots`, `ItemHandler` и существующие soulshot/spiritshot
  handlers;
- World/WorldRegion visibility для threat и ground item;
- `MapRegionData` + `TeleportWhereType.TOWN` и canonical teleport/revive
  lifecycle;
- immutable Game Knowledge class capability и `NpcKind` facts.

Не создавались packet handlers, fake client packets, damage/drop formulas,
прямые HP/MP/EXP/inventory mutations или новые core/server APIs.

## Package и архитектура

Создан bounded пакет `phantoms/combat` с service, policy, request/session/value
types, capability resolver, threat table, backend/opaque actor lease, L2j
adapter, handlers и fixed aggregate metrics.

Публичные combat values не раскрывают mutable server objects. Только
`L2jCombatBackend` удерживает canonical `ActionLease` и `Player`.

## Plan-scoped cancellation

Token теперь связан с exact `PhantomPlan` object и runtime generation:

- переход между steps того же plan сохраняет token;
- completion, replan, retry exhaustion, total/step timeout, handler
  `CANCELLED`, terminal goal, detach, stop, generation/goal replace и reload
  инвалидируют token;
- stale handler result не владеет новым plan;
- final `combat.start` self-cancels после completion plan.

Terminal combat result нельзя consume до завершения exact action cleanup.
In-flight start/respawn входят в combat stop barrier, поэтому stale cleanup и
materialization drain не пересекаются с новым session ownership.

## Session, worker и lease ownership

- один active или unconsumed terminal session на profile;
- один exact materialization action lease на весь async session;
- один transient shared pulse worker через существующий `ThreadPool`;
- без per-profile task/Future, raw thread или нового executor;
- до 64 sessions за pulse, один profile в queue не более одного раза;
- worker равен нулю при пустой queue;
- dispatch failure и stop/start ordering имеют exact reconciliation.

## Target, threat и loadouts

Target и threat observations принимают только normal `Monster`, подтверждённый
Game Knowledge как `NpcKind.MONSTER`. Player, RaidBoss, GrandBoss, event/fake,
raid/minion, invulnerable, peace/siege/event/Olympiad/duel, другой instance,
несоседний region и distance выше 2000 отклоняются.

Threat table ограничен 32 entries и использует supplied logical time,
deterministic decay, saturating addition, selection и eviction.

Loadout matrix:

```text
MELEE_PHYSICAL  -> combat.melee_damage, bounded selected skills, attack fallback
RANGED_PHYSICAL -> combat.ranged_physical_damage, bounded selected skills, attack fallback
RANGED_MAGIC    -> combat.ranged_magic_damage, exact supported known skill required
```

Class switch не выполняется; максимум четыре skill evidence проверяются через
Game Knowledge и canonical known-skill facts.

## Canonical combat, shots и observations

Physical attack выдаётся через `PlayerAI ATTACK`, magic/physical skill — через
`PlayerAI CAST` с exact `Skill`. Повторный pulse не сбрасывает уже принадлежащую
session intention.

Shots имеют только policy `USE_IF_AVAILABLE`: handler выбирает подходящий
owned shot по factual type/grade, canonical handler расходует exact count, а
attack/cast разряжает canonical charge. Отсутствие shot не создаёт item/charge
и не останавливает бой.

Service наблюдает HP/MP, actor death, target death/loss и canonical action
state. При HP не выше 15% session останавливается; magic skill не выдаётся при
MP reserve не выше 10%. Прямого лечения, retreat, damage или MP mutation нет.

## Loot

После normal-monster victory optional loot видит не более 32 известных actor-у
ground items в том же instance и в 300 units, помнит не более 64 object IDs и
выдаёт canonical `PlayerAI PICK_UP`. In-flight pickup ожидает удаления item из
World, не повторяется и завершается не позже пяти секунд. Результаты различают
обычную victory, full/partial loot и blocked loot.

Real fixture подтвердил World → inventory conservation; test baseline
восстанавливается даже при assertion failure.

## Death и normal-town respawn

Actor death создаёт `PLAYER_DEAD`, отменяет только owned action, освобождает
lease и не вызывает auto-respawn.

Explicit `combat.respawn_town` проверяет dead/canRevive, fake death, jail,
festival/event, Olympiad/duel/siege, special instance и pending revive. Затем
использует canonical TOWN location, instance 0, Seven Signs flag, pending revive
и teleport. Headless actor завершает canonical `onTeleported` lifecycle без
packet simulation.

## Handlers и production inertness

До seal зарегистрированы:

```text
combat.start
combat.await
combat.cancel
combat.respawn_town
```

Production combat candidate не добавлен. Startup не создаёт session, target
scan, respawn или combat worker. Disabled path combat service не создаёт;
enabled inert test path имеет нулевые sessions/workers/leases.

## Startup, shutdown, metrics

Combat конструируется после materialization и до decision handler registry
seal, запускается после Game Knowledge и до scheduler.

Shutdown invalidates decision plan tokens, останавливает combat до
materialization drain и публикует только aggregate:

```text
combatState
combatActiveSessions
combatTerminalSessions
combatQueuedSessions
combatWorkers
combatActorLeases
```

Metrics используют только fixed counters: sessions/leases/targets, pulses,
workers, threat, attacks/casts/shots, deaths, loot, cancellation/timeouts,
backend failure, respawn и stop failure. Profile/NPC/skill labels отсутствуют.

## Tests

Seed: `20260725001`.

Focused:

```text
combat core:               47/47 ×3
combat ownership:          17/17 ×3
real server integration:   12/12 ×2
combat performance:         1/1 ×2
```

Performance canonical summary:

```text
sessionsCompleted=10000
pulses=100000
threatOperations=100000
cancellations=10000
maximumWorkers=1
actorLeasesAfterRun=0
terminalSlotsAfterConsume=0
```

Real integration использовала только `l2jmobiush5_phantom_test`, существующий
headless/materialization environment и shared `ThreadPool`. Проверены exact
World Player lease, normal-monster attack/death, selected skill CAST, shot
consumption/discharge, no-shot path, ground pickup, forbidden targets, exact
cancellation, player death, town respawn, dematerialization drain и отсутствие
packet route.

Cumulative `ant verify` включает все ordinary/headless/profile/materialization,
scheduler/decision/navigation/topology/Game Knowledge, DB/negative/static и
Goal 012 routes. Goal 011A focused routes повторены отдельно по task matrix.
`ant jar` собирает production combat classes без test classes.

Первый cumulative запуск дошёл до исторического Goal 011A static verifier и
ожидаемо отклонил предусмотренные Goal 012 изменения/закрытие gate. Build chain
исправлен по существующему cumulative pattern: исторический verifier сохранён
без изменений, а target Goal 011A делегирует актуальному read-only Goal 012
verifier. Production/runtime finding отсутствовал.

## DB, config и migrations

- production DB `l2jmobiush5` не использовалась;
- test DB: только `l2jmobiush5_phantom_test`;
- schema/migrations отсутствуют;
- config keys не добавлялись;
- datapack, curated knowledge и geodata не менялись.

## Scope

Изменены только разрешённые production integration files, новый combat package,
разрешённые tests/build/verifier и документация Goal 011A/012. `Player`,
`Creature`, AI, Skill, Item, Inventory, World, loaders, materialization,
knowledge semantics, config/schema/datapack и другие хроники не изменялись.

203 user-owned untracked geodata files сохранены и исключены из staging.

## Deviations и limitations

- Добавлены четыре regression cases сверх минимального core count: canonical
  `PICK_UP` может оставаться in-flight один pulse; cancel ждёт завершения
  владеющего actor-ом pulse; dispatch failure не публикует одновременно
  резервируемую terminal session и не искажает session/lease metrics; cancel
  ждёт завершения ещё резервируемого start и закрытия его временного lease.
- Goal 012 остаётся намеренно pinned к explicit target; threat retarget policy
  отложена.
- Нет production candidate, PvP/raid/party/spoil/progression/commerce.
- Goal 013/014 не начаты.

## Verification handoff

До commit выполняются scope, encoding, `git diff --check`, cumulative verify,
jar и Task 012 verifier. После ordinary commit повторяются verify/jar и два
byte-identical запуска verifier; точные SHA-256, commit SHA и remote equality
передаются во внешнем final handoff.
