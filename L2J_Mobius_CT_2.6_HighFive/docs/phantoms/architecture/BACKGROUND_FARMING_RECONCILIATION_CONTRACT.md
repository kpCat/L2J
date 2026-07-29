# Background farming reconciliation contract

## Статус и границы

Goal 015 production loot disposition unblock имеет статус
`IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`. Технические reconciliation gates
сохранены, а exact pair `22859@giran.farming.22859` поддержана при shipped
AutoLoot policy.
Контракт обслуживает только persisted ACTIVE goal `farm.background` с точными
NPC ID и topology anchor ID. Он не выбирает цель, не создаёт goal и не включает
party, spoil, manor, quest, craft, raid, instance, PvP или
`progression.learn_skill`.

`BACKGROUND_MODEL_V1` — детерминированная bounded approximation, а не заявление
о retail-equivalent combat. Не представимое точно условие отклоняется до
канонической мутации.

## Identity и activity authority

Одна операция владеет distinct `OwnerKind.BACKGROUND` lease. Порядок:

```text
profile/character link
→ BACKGROUND identity lease
→ отсутствие PHANTOM/REAL_LOGIN owner
→ отсутствие World Player/object и autosave owner
→ exact persisted ACTIVE goal
→ background.state и authority hashes
```

Lease удерживается до commit/rollback и fresh durable verification. Та же
registry блокирует real login и materialization. Service считает current/peak
operations, identity leases, transactions и transition claims; `STOPPING` не
принимает новую работу и не становится `STOPPED`, пока ownership не равен нулю.
Новых worker, thread, executor, timer или per-profile Future нет.

`activityGeneration`, `tickSequence`, `effectiveState` и `logicalNowNanos`
переносятся из scheduler work item в planning/step context. Operation key также
содержит profile/character, goal ID/revision, action, NPC/anchor,
`BACKGROUND_MODEL_V1` и knowledge/topology/progression/commerce hashes.

## Durable state и transitions

`background.state`, schema version `1`, payload не более 4096 bytes, имеет
состояния `MATERIALIZED`, `READY`, `VERIFY_PENDING`, `DEAD`, `INCONSISTENT`.
Сохраняются identity/class index, progression, vitals, canonical position и
anchor, combat/loadout, bounded inventory, auto-get skills, RNG/residual time,
последняя operation identity и authority hashes.

| Исходное состояние | Событие | Результат |
|---|---|---|
| отсутствует/MATERIALIZED | canonical `Player.storeMe()` | fresh DB capture → READY/DEAD |
| READY/DEAD | materialize | reconcile, `Player.load()`, byte/fact compare → MATERIALIZED |
| READY | accepted batch | one commit → VERIFY_PENDING → fresh proof → READY |
| READY | causal lethal batch | one commit → VERIFY_PENDING → fresh proof → DEAD |
| VERIFY_PENDING | restart, exact expected hash | READY или DEAD |
| VERIFY_PENDING | restart, mismatch | INCONSISTENT |
| INCONSISTENT | любое work/restart | fail-stop, auto-recovery запрещён |

Baseline capture повторно блокирует exact persisted goal в той же транзакции.
После `Player.storeMe()` state читается из canonical DB rows до identity release.
Перед spawn загруженный Player сравнивается с durable state. Ни один transition
не восстанавливает бесплатно EXP/SP, HP/MP/CP, предметы или позицию.

## BACKGROUND_MODEL_V1

Для actor выбирается один подтверждённый capability kind: `MELEE`, `RANGED`,
`MAGIC`, `SUMMON_PRIMARY`. Class-name tactics не реконструируются.

Для физического/магического выбранного канала:

```text
effectiveDamage = max(1, offense * 100 / (targetDefense + 100))
cyclesPerSecond = max(0.1, actorSpeed / 500)
durationMs = clamp(round(targetHP / effectiveDamage / cyclesPerSecond * 1000
                         * variance), 500, 20000)
incomingPerCycle = max(1, targetOffense * 100 / (actorDefense + 100))
incoming = incomingPerCycle * durationSeconds
           * max(0.1, targetSpeed / 500) * variance
variance = 0.9 + persistedRng.nextDouble() * 0.2
```

HP/MP passive regeneration применяется за фактическое время encounter; exact
skill MP, soulshot/spiritshot и summon-resource counts списываются до награды.
CP в PvE не расходуется и при смерти становится нулём. Batch ограничен 32
encounters, 60 000 logical ms, 16 changed item objects и 8 новыми
non-stackable objects.

EXP/SP повторяют текущий одиночный full-damage normal-monster путь:

```text
baseExp = max(0, (long)(npcExp * RATE_XP))
baseSp  = max(0, (int)(npcSp * RATE_SP))
high-level penalty = current Attackable table for actor > 84 and diff -3..-10
finalExp = round(baseExp * penalty * capturedExpMultiplier
                 * (1 - exactServitorExpMultiplier))
finalSp  = (long)(baseSp * penalty * capturedSpMultiplier)
```

Level-difference cutoff берётся из текущей server policy. EXP округляется
`Math.round`, SP усекается приведением. Vitality, Nevit, premium, PC-cafe,
party, event и quest reward contexts fail closed.

Death loss:

```text
span = exp(level+1) - exp(level), для max level используется предыдущий span
loss = round(span * ExperienceLossData.percent(level)
             * capturedNormalMobReduction / 100)
applied = min(loss, currentExp, round(span * 0.10))
```

Drop stream сначала обрабатывает groups, затем ungrouped entries, как текущий
`NpcTemplate.calculateDrops`: обычная group имеет cumulative item chance и не
более одного выбранного entry; custom-rate entries независимы; grouped и
ungrouped occurrence budgets раздельны. Level-gap roll, chance roll и inclusive
count roll потребляют один persisted RNG stream. Fractional expected-value items
не создаются.

Каждый drop имеет disposition `ACQUIRE` или `LEAVE_ON_GROUND`. Оба disposition
остаются в полном ordered corpus и одинаково участвуют в group selection,
chance/count RNG и occurrence budgets. Только `ACQUIRE` входит в inventory
capacity, MariaDB item delta и object-ID reservation. `LEAVE_ON_GROUND`
публикуется как bounded immutable `groundLosses` evidence и затем теряется:
никаких Player inventory/effect/timer/variable/deferred grant или materialization
reconciliation для него нет.

Immediate-effect и time-limited item допускается как `LEAVE_ON_GROUND` только
если текущие `AutoLootHerbs`, `AutoLoot` и `AutoLootItemIds` не приобретают его.
Иначе вся target отклоняется до mutation. `LOOT_POLICY_V1` с этими значениями,
`AutoLootSlotLimit` и отсортированными item IDs входит в composite knowledge
authority hash; config drift делает старый READY state stale.

## Каноническая MariaDB-транзакция

Единственный writer — `PhantomBackgroundTransaction`. Connection использует
`autoCommit=false`, query timeout 5 секунд и стабильный lock order:

```text
phantom_profiles exact link
→ persisted goal component
→ background.state component
→ characters
→ exact character_subclasses row, если classIndex > 0
→ character_skills exact classIndex ORDER BY skill_id
→ items inventory/paperdoll ORDER BY object_id
→ reserved IdManager object IDs
```

В одном commit меняются main либо exact subclass EXP/SP/level, `expBeforeDeath`,
HP/MP/CP, x/y/z/heading, item rows, новые object IDs, exact auto-get-only skills
и `VERIFY_PENDING` receipt с expected-after SHA-256. Все update/delete/insert
имеют exact row-count guard. Level проверяется через `ExperienceData`, а
auto-get set независимо пересчитывается через current `SkillTreeData`.

| Исход транзакции | Durable результат | Повтор |
|---|---|---|
| fault до commit | полный rollback, reserved IDs released | безопасен |
| commit + fresh proof | READY/DEAD | exact key идемпотентен |
| commit, read outcome unknown | VERIFY_PENDING | restart reconciliation |
| expected hash/canonical mismatch | INCONSISTENT | mutation запрещена |
| stale goal/generation/tick/hash | zero mutation | typed stale/replan |

## Travel, competition и recovery

Travel использует только текущий route и `backgroundEligible=true` edge с
authoritative `baseTravelMillis`. Mid-edge сохраняет residual time, а canonical
position остаётся на последнем committed anchor. Закрытый edge не меняет ни
position, ни residual; завершение edge атомарно фиксирует anchor/coordinates.

Competition reservation ключуется `(topologyNodeId, npcId)`, capacity берётся
из configured spawn amount и clamp `1..32`. Reservation живёт только одну
операцию и не выдаёт reward.

Lethal encounter сохраняет resulting MP/resources/drops, `expBeforeDeath`,
нулевые HP/CP и `DEAD`, затем подаёт bounded WARM relevance signal.
`background.recover` материализует canonical Player, берёт ActionLease,
использует текущие `doRevive()` и to-town semantics, снова dematerialize/verify
и возвращает typed `FAIL_GOAL`; убитый farm goal молча не возобновляется.

## Production corpus

Полный deterministic audit текущего production corpus нашёл одну exact topology
farm fixture: `giran.farming.22859`, NPC `22859`. При shipped policy
`AutoLootHerbs=False`, `AutoLoot=False`, `AutoLootItemIds=0` её ordinary drops
имеют `ACQUIRE`, а immediate/time-limited IDs `8600–8614`, `10655–10657` и
`13028` — `LEAVE_ON_GROUND`. Supported production pair count равен `1`.

Seed `15001502` доказывает реальный успешный batch через canonical `Player`,
production authority/model и одну atomic MariaDB transaction: EXP/SP, HP/MP,
acquired item/resource deltas, receipt, RNG и hash совпадают; ground-loss rows
и object IDs отсутствуют; exact duplicate идемпотентен; materialization,
dematerialization и reload сохраняют committed state. Topology, datapack,
geodata, loaders, schema и config не изменялись.
