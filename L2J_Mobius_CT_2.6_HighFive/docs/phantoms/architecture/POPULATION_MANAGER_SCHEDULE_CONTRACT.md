# Контракт Population Manager и расписаний

## Граница Goal 016

Goal 016 создаёт только управляемую целевым размером популяцию Phantom World:
durable shell, канонического персонажа первого уровня, расписание активности,
ACTIVE admission и детерминированный retirement/return. Система не создаёт
игровые цели после bootstrap и не меняет background, combat, commerce,
progression, topology, schema или сетевой протокол.

Production defaults:

```text
PhantomPopulationTarget = 0
PhantomPopulationActiveTarget = 0
```

При выключенном Phantom World каталог, manager и DB composition не создаются.
Target `0` при отсутствии managed state не создаёт profile, account или
character.

## Durable identity и создание

`population.state` хранится как bounded canonical binary payload:

```text
generation + creation ordinal + seed + catalog hash
reserved account + ownership token + bounded name attempt
class/appearance + schedule/phase + home map-region
fixed GeoEngine-normalized creation X/Y/Z
expected/actual character object ID + saga stage
exact initialization hash + typed failure
```

Profile и первый component создаются одной транзакцией. Startup читает
`profile_id` pages не более 256 строк и fail-closed останавливается, если
durable managed population превышает scheduler capacity. Pulse не читает DB и
XML.

Reserved account имеет имя `p<profileId base36>` длиной не более 14 ASCII
символов, случайный 256-bit Base64URL ownership token и текущий доказанный
disabled access level `-1`. Чужой account, лишний character или несовпадающая
identity переводят state в `INCONSISTENT`; автоматического удаления или
replacement loop нет.

Порядок durable saga:

```text
atomic SHELL
→ bootstrap goal/scheduler WARM
→ ACCOUNT_INTENT → owned disabled account → ACCOUNT_VERIFIED
→ CHARACTER_INTENT → Player.create → CHARACTER_CREATED
→ INITIALIZATION_INTENT → shared canonical initializer
→ store offline → runtime cleanup → fresh Player.load verification
→ VERIFIED + exact hash
→ optimistic profile link
→ LINKED/READY + bootstrap cleanup + schedule install
```

Каждый переход повторяем после restart. Инициализация допускается только для
pristine, ещё не связанного и недоступного character. Manager не создаёт
`GameClient`, не вызывает client packet handler или client-origin
`OnPlayerCreate`, не добавляет character в World/autosave.

## Общий initializer

`PlayerCreationInitializer` является единственной общей точкой для
`CharacterCreate` и population. Он применяет текущие policies title, vitality,
starting Adena, `InitialEquipmentData`, auto-get skills и
`InitialShortcutData`, а также текущую start-location policy с World bounds и
GeoEngine height.

`CLIENT` сохраняет configurable starting level/SP. `POPULATION` всегда
сохраняет level `1` и SP `0`; выбранная start point фиксируется в durable state,
потому что штатный `PlayerTemplate.getCreationPoint()` выбирает случайный
вариант. После equipment initializer повторно нормализует HP/MP к новым maxima
и CP к нулю.

Fresh verification сравнивает identity, level/SP, exact X/Y/Z, full vitals,
Adena, initial equipment, learned skills, shortcuts и SHA-256 canonical state.
Profile link создаётся только после этой проверки.

## Target reconciliation и backpressure

Target считает все managed states, кроме `RETIRED`. При росте сначала
возвращаются `RETIRED` с наименьшими profile ID, затем создаётся не больше
доступного creation-in-flight и scheduler capacity. При уменьшении READY
profiles с наибольшими ID переходят:

```text
READY → RETIRE_REQUESTED
→ withdraw population.schedule
→ scheduler unregister/dematerialize
→ RETIRED
```

Character, account и profile не удаляются. Restart из `RETIRE_REQUESTED`
продолжает unregister. Return переводит тот же lowest-ID profile обратно в
`READY`; replacement identity не создаётся.

`INCONSISTENT` фиксирует deficit и блокирует дальнейшее пополнение до
restart/operator action. Scheduler backpressure не создаёт дополнительный
shell.

## Расписания и admission

`high-five-population-v1.xml` — строгий bounded XML catalog с ASCII name
fragments, всеми 11 canonical starting classes, weights и weekly windows.
DOCTYPE/external entities, неизвестные attributes, duplicate IDs, overlap и
невалидные ranges отклоняются.

Schedule evaluation использует injected `Clock` и configured `ZoneId`.
Midnight wrap, DST gap/overlap и phase учитываются через `ZoneRules`. Forward
clock jump вычисляет только latest state. Backward jump не воспроизводит старую
последовательность. Long windows обновляются heartbeat; TTL ограничен текущим
`PhantomRelevanceSignal` maximum.

ACTIVE admission ограничен:

```text
min(PhantomPopulationActiveTarget, MaxMaterializedPhantoms)
```

Региональные quotas рассчитываются largest remainder по всей home-region
популяции. Внутри региона выбор стабилен для одного epoch day и
детерминированно ротируется между днями. Не admitted ACTIVE profile получает
WARM, а SLEEPING withdraws schedule signal.

## Shared scheduler и lifecycle

`PhantomSchedulerControlPort` устанавливается один раз до scheduler start.
Его `onPulse()` вызывается ровно один раз за существующий общий pulse после
выхода из scheduler monitor. Population Manager не владеет Thread, Executor,
timer, task или Future.

В памяти manager держит bounded entry map, due heap и coalesced signal,
ready-transition и retirement queues. Один pulse ограничен
`PhantomPopulationBoundariesPerPulse`. Character creation выполняется только
через существующий decision work path `population.bootstrap` в WARM:

```text
candidate.population.bootstrap
→ population.create_character
```

Shutdown сначала запрещает новые control/creation operations, очищает pending
queues и ждёт нулевые control, creation и persistence claims. Затем допускается
завершение существующих scheduler/decision/materialization/background owners.

## Наблюдаемость

Snapshot содержит только bounded aggregate данные: lifecycle, targets,
ready/retired/inconsistent counts, queue sizes, current/peak claims и
class/level/region histograms. Per-profile INFO/WARNING и hot-path DB/XML scans
не добавлены.
