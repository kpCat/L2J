# Контракт Population Manager и расписаний

## Граница Goal 016

Goal 016 создаёт только управляемую целевым размером популяцию Phantom World:
atomic durable shell, канонического персонажа первого уровня, расписание,
ACTIVE admission и детерминированный retirement/return. Система не меняет
background, combat, commerce, progression, topology, schema или сетевой
протокол.

Production defaults:

```text
PhantomPopulationTarget = 0
PhantomPopulationActiveTarget = 0
```

При выключенном Phantom World каталог, manager и DB composition не создаются.
Target `0` при отсутствии managed state не создаёт profile, account или
character.

## Durable identity и authority

`population.state` schema version 2 хранит bounded canonical payload:

```text
generation + creation ordinal + seed + catalog hash
POPULATION_CREATION_AUTHORITY_V1 hash
reserved account + ownership token + bounded name attempt
class/appearance + schedule/phase + home map-region
fixed GeoEngine-normalized creation X/Y/Z
expected/actual character object ID + saga stage
exact durable projection hash + typed failure
```

Immutable `PopulationInitializationContract` строится до первого initialization
writer. В hash входят catalog, `ZoneId`, версия общего initializer, все
относящиеся к созданию настройки, location/title/vitality/vitals/level/SP/Adena,
exact multiset initial items, auto-get skills и shortcut/macro plan.

Startup сначала декодирует component и сравнивает persisted authority с текущей.
Catalog, timezone, config, equipment, skill или shortcut drift дают typed
fail-closed result до scheduler registration, goal repair, account/character
mutation и schedule signal. Schema-v1 читается bounded decoder, но managed v1
без доказуемой authority не принимается.

Profile и первый component создаются одной транзакцией. Startup читает
`profile_id` pages не более 256 строк и fail-closed останавливается при
превышении scheduler capacity. Pulse не читает DB или XML.

Reserved account имеет имя `p<profileId base36>`, случайный Base64URL ownership
token и disabled access level `-1`. Чужой account, лишний character или
несовпадающая identity переводят state в `INCONSISTENT`; автоматического
удаления или replacement loop нет.

## Restart-safe создание

Порядок durable saga:

```text
atomic SHELL
→ bootstrap registration/attach/signal
→ ACCOUNT_INTENT → owned disabled account → ACCOUNT_VERIFIED
→ CHARACTER_INTENT → Player.create → CHARACTER_CREATED
→ INITIALIZATION_INTENT → exact projection classification/repair
→ один explicit Player.storeMe → INITIALIZATION_STORED
→ read-only fresh Player.load verification → VERIFIED
→ optimistic profile link
→ LINKED/READY + bootstrap cleanup + schedule install
```

До `Player.load` durable projection классифицируется только как:

- pristine;
- exact canonical;
- strict subset доказанных creation-owned expected facts.

Unexpected item, count excess, unexpected skill/shortcut/macro, conflicting
equipped flag или character property дают `INCONSISTENT` до profile link.
Strict subset дополняется только отсутствующими expected facts. Item shortcut
сопоставляется с owned item template и его фактическим object ID.

Durable writer boundaries охватывают Adena, каждый initial item/equip, skills,
shortcuts, macros, character store, fresh verification, profile link и READY
component update. После fault restart продолжает ту же saga без duplicate
items, skills, shortcuts или macros. `INITIALIZATION_STORED` отделяет
единственный explicit store от read-only resume verification.

## Transport-neutral initializer и autosave

`PlayerCreationInitializer` является общей точкой для ordinary
`CharacterCreate` и population. Он применяет title, vitality, starting Adena,
`InitialEquipmentData`, auto-get skills и `InitialShortcutData`.

`InitialShortcutData` сначала формирует immutable logical shortcut/macro plan.
`CLIENT` сохраняет прежнюю durable регистрацию и `ShortcutRegister` delivery.
`POPULATION` применяет тот же plan без `sendPacket`, `GameClient`, packet
handler, serverpacket path или client-origin `OnPlayerCreate`.

Population всегда создаётся с level `1` и SP `0`. Создание использует
зафиксированную location, нормализует HP/MP к maxima и CP к нулю.

Guard `PlayerAutoSaveTaskManager.suppressPopulationLoad(objectId)` действует
только в текущем потоке и только для exact object ID. Он устанавливается до
каждого creation/verification `Player.load` и освобождается в `finally`.
Initialization owner выполняет один explicit store. Verification cleanup
только снимает runtime ownership/tasks; `storeMe` не вызывается. До и после
fresh verification relevant DB projection byte-identical.

## Target reconciliation и ownership retries

Target считает все managed states, кроме `RETIRED`. При росте сначала
возвращаются lowest-ID retired identities, затем создаются shells в пределах
creation и scheduler capacity. При уменьшении highest-ID profiles, включая
creation-pending stages, получают durable retirement request. Profile, account
и character не удаляются. Restart с target `0` не завершает лишнее создание.

Scheduler/decision ownership имеет explicit retry state machine с generation,
due ordering и bounded backoff:

```text
BOOTSTRAP_REGISTER  BOOTSTRAP_ATTACH  BOOTSTRAP_SIGNAL
READY_REGISTER      READY_ATTACH      READY_SCHEDULE
RETIRE_WITHDRAW     RETIRE_UNREGISTER RETIRE_COMPLETE
RETURN_REGISTER     RETURN_ATTACH     RETURN_SCHEDULE
```

Каждый action расходует `PhantomPopulationBoundariesPerPulse`. Retryable
statuses повторяются; permanent conflicts дают typed `INCONSISTENT`; success
продвигает только следующий action. Target reconciliation не создаёт новый
shell при unresolved ownership.

## Расписания, admission и bounded pulse

`high-five-population-v1.xml` — строгий bounded XML catalog с canonical starting
classes, weights и weekly windows. DOCTYPE/external entities, unknown
attributes, duplicate IDs, overlap и invalid ranges отклоняются.

Schedule evaluation использует injected `Clock` и configured `ZoneId`.
Midnight wrap, DST gap/overlap и phase обрабатываются через `ZoneRules`.
Forward jump применяет latest state; backward jump не replay’ит границы.

ACTIVE cap:

```text
min(PhantomPopulationActiveTarget, MaxMaterializedPhantoms)
```

Region quotas используют largest remainder. Внутри региона выбор стабилен в
один epoch day и детерминированно ротируется между днями. Overflow получает
WARM, SLEEPING withdraws schedule signal.

`PhantomSchedulerControlPort.onPulse()` — единственный control hook общего
scheduler и вызывается вне scheduler monitor. Population Manager не создаёт
Thread, Executor, timer, task или Future. Pulse работает через due/retry heaps,
ordered READY/region indexes и dirty flags; он не сканирует и не сортирует
полную `_entries`. Real-manager smoke подтверждает bound на 10 000 profiles и
100 000 pulses без DB writes или per-profile workers.

## Lifecycle и наблюдаемость

Manager использует lifecycle `NEW → RUNNING → STOPPING → STOPPED` и claims для
control, creation и persistence. Shell, committed в DB одновременно со
`STOPPING`, не публикуется в остановленный in-memory manager; restart
восстанавливает его один раз. `finishStop` разрешён только при нулевых claims и
очищает все indexes/queues.

`PhantomSystem.Snapshot` и configured shutdown evidence включают
`PhantomPopulationManager.Snapshot`: lifecycle, managed/ready/retired/
inconsistent counts, due/retry queues, control/creation/persistence claims и
bounded class/level/region histograms. Disabled snapshot остаётся inert и не
создаёт manager или DB access.
