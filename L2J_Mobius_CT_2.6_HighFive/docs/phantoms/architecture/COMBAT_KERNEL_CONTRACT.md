# Контракт capability-driven solo combat kernel

## Scope Goal 012

Combat kernel выполняет только явно запрошенный solo-сеанс против одного
обычного `Monster`. Он не выбирает production-цели, не регистрирует Utility AI
candidate и не реализует PvP, raid/epic, party, spoil, progression или commerce.

Поддерживаются три generic mode:

```text
MELEE_PHYSICAL   -> combat.melee_damage
RANGED_PHYSICAL  -> combat.ranged_physical_damage
RANGED_MAGIC     -> combat.ranged_magic_damage
```

Capability и skill evidence поступают только из активного immutable Game
Knowledge generation. Physical mode может использовать normal-attack fallback.
Magic mode без подтверждённого известного offensive skill отклоняется.

## Ownership

Один profile может иметь не более одного active или unconsumed terminal session.
Весь async-сеанс удерживает один opaque materialization `ActionLease`. Публичные
request/result/snapshot/loadout/threat types не раскрывают `Player`, `Creature`,
`WorldObject`, `Skill`, `Item` или AI objects.

Plan cancellation token принадлежит точному объекту `PhantomPlan` и generation:

- переход между steps того же plan сохраняет token;
- completion, replan, retry exhaustion, total/step timeout, handler cancellation,
  terminal goal, replace/reload, detach и stop инвалидируют token;
- terminal combat slot нельзя consume до завершения exact owned-action cleanup;
- новый session не может начаться, пока cleanup старого session не закончен.

In-flight start/respawn операции и actor leases входят в stop barrier. Combat
полностью останавливается до materialization drain.

## Target и threat

Явная цель должна быть:

- canonical `Monster`, но не `RaidBoss`, `GrandBoss`, event/fake/raid/minion;
- `NpcKind.MONSTER` в Game Knowledge;
- targetable, attackable, mortal, spawned и не invulnerable;
- в том же instance и surrounding World region;
- не дальше 2000 units;
- вне peace/siege/event/Olympiad/duel restrictions.

Threat table принимает только такие же normal-monster observations. Он хранит
не более 32 entries, использует saturating addition, supplied logical time,
deterministic decay и deterministic selection/eviction. В Goal 012 session
может оставаться закреплённым за explicit target.

## Canonical actions

Production adapter применяет только штатные server-side пути:

```text
PlayerAI ATTACK
PlayerAI CAST с exact известным Skill
PlayerAI PICK_UP
Player.rechargeShots / ItemHandler
MapRegionData TOWN + pending revive + teleToLocation
```

Combat package не создаёт client/server packets, не вызывает packet handlers и
`sendPacket`, не рассчитывает damage/drop и не изменяет напрямую HP, MP, EXP или
inventory. Shot используется только из уже имеющегося inventory с canonical
grade/type/count validation. Отсутствие shot не блокирует бой и ничего не
создаёт.

Cleanup отменяет только ATTACK/CAST, если target и selected skill всё ещё точно
принадлежат session. Foreign/newer action не затрагивается.

## Pulse, loot и respawn bounds

```text
sessions processed per pulse     64
threat entries                   32
selected skills                   4
observed attackers               16
loot candidates                  32
remembered loot object IDs       64
acquisition distance           2000
loot distance                   300
pulse interval                  250 ms
default / maximum timeout       30 s / 120 s
loot timeout                      5 s
low HP / MP reserve              15% / 10%
shared combat workers             1
```

Используется один transient worker через общий `ThreadPool`; per-profile tasks,
raw threads и отдельные executors запрещены. Worker отсутствует при пустой
очереди.

После target death optional loot сканирует только известные actor-у ground
`Item` в том же instance и выдаёт canonical `PICK_UP`. In-flight pickup
ожидается до фактического удаления item из World либо bounded timeout.

Player death завершает session как `PLAYER_DEAD`, освобождает lease и не
запускает auto-respawn. `combat.respawn_town` поддерживает только normal-world
town path и отклоняет fake death, jail, festival/event, Olympiad/duel/siege,
special instance и pending revive.

## Lifecycle и production inertness

Startup:

```text
repository -> materialization -> combat construction/handlers
-> decision -> navigation -> topology -> Game Knowledge
-> combat.start -> scheduler
```

Shutdown:

```text
scheduler.beginStop -> decision.beginStop -> combat.beginStop
-> knowledge/topology/navigation beginStop
-> combat.finishStop -> materialization drain
-> remaining finishStop
```

Goal 012 регистрирует handlers `combat.start`, `combat.await`, `combat.cancel`,
`combat.respawn_town`, но регистрирует ноль production combat candidates.
Поэтому startup имеет ноль sessions, target scans, respawns и combat workers.

## Ограничения

Goal 013 и Goal 014 не начаты. Полный class/equipment catalog, progression,
party/PvP/raid/spoil и economy остаются за пределами этого контракта.

## Goal 012A — истина владения действием

### Shared worker

Постановка worker считается принятой только после получения явного accepted
dispatch handle. `null`, rejected result, `false` и `Throwable` означают
непринятую постановку и не оставляют session в состоянии «worker запущен».
Проверка lifecycle, постановка и переход в `STOPPING` сериализованы одним
dispatch gate. `beginStop()` отменяет scheduled-but-not-started handle, а
top-level `finally` освобождает worker claim при любом `Throwable`. Stale
callback не может обработать session нового поколения.

### Canonical action cleanup

Session хранит exact descriptor только для принадлежащих ей действий
`ATTACK`, `CAST` и `PICK_UP`: generation, combat target, selected skill и
pickup object ID. Cleanup отменяет лишь совпадающее canonical действие и не
трогает foreign action.

Action lease закрывается только после подтверждённого cleanup. Ошибка cleanup
оставляет bounded retryable ownership в состояниях `PENDING`, `RETRYABLE` или
`FAILED`; максимум три попытки выполняются тем же shared worker. До состояния
`COMPLETE` session нельзя consume, а `finishStop()` не может объявить успешную
остановку.

### Loot truth

Loot считается acquired только по положительному evidence владения actor:
тот же inventory object либо положительный прирост количества точного item ID
после исчезновения ground object. Исчезновение, pickup другим игроком, despawn,
потеря дальности или eligibility сами по себе не являются успехом.

### Skill и mode safety

Selected skill обязан быть active, negative, `TargetType.ONE`, не PvP-only,
не suicide и не special skill. Перед canonical cast повторно проверяются
точный selected skill и точный mode текущей session; mode не выводится заново
из mutable actor state.

### Respawn ownership

Respawn request несёт exact plan cancellation token. Respawn запрещён при
active либо cleanup-pending combat session. После получения actor и перед
canonical side effect повторно сверяются lifecycle, exact operation/token и
отсутствие session. Операция, принятая до `STOPPING`, либо завершается внутри
stop barrier, либо отменяется до side effect; после `finishStop()` respawn
side effect невозможен.
