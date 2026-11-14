# ARCHITECTURE011 — предложенный минимальный контракт

## Слои и односторонняя зависимость

```
Canonical Game/TEST World + actual acquired Player/Party/NPC/Geo
                       |
        Capability and target-source census
                       |
      Encounter proposal (unsealed, bounded)
                       |
  Cheap validation / shared bootstrap + request construction
                       |
             HEADLESS_READY (не GAME_PASS)
                       |
      Native World incarnation + revalidate before CUT
                       |
           LIVE_ADMITTED → immutable physical seal
                       |
        STOCK / FROZEN DRAFT14 matched arms
                       |
      Ordinary native events + receipts + terminal state
                       |
  Independent EVAL ledger; no TRAIN gradients/credits
```

## Внешне наблюдаемые контракты

- `source_identity`: acquisition provenance, class/level/index, acquired skills including rank/enchant, gear/status SHA, role; отсутствие навыка никогда не исправлять искусственной выдачей.
- `context`: mode, SOLO/PARTY roster, action family, target NPC template/world/spawn, anchor/zone/Geo, buff/equipment baseline, stop/timeout, owner.
- `proposal_identity`: immutable catalog hash/seed/version and deterministic selection. Меняющийся live world допускает REPLAN до seal; после CUT immutable.
- `admission_receipt`: config/canonical loader version, source SHA, target identity+epoch, request-constructor result, candidate ready set, risk, source/target positions, faction, Geo and negative reason; timestamps and stage.
- `pair_identity`: physical facts equal, explicit order AB/BA, new incarnation IDs, no shared event keys; limited randomness не «замораживает» реальный native outcome.

## No-solution diagnostic

Отдельно выводить:
1. сколько source snapshots реально существует и сколько уникальных физически;
2. сколько имеют пригодные active skills для Solo/Party;
3. сколько подходящих NPC templates и live spawn в допустимых level/reward/AI bounds;
4. сколько проходит точный native capability-to-target (`magicLevel`, `Skill.checkCondition`, MP, range, equip, owner);
5. сколько проходит topology/Geo/LOS/faction/zone/area/isolation;
6. сколько готовых requests реально собирается без исключения;
7. сколько «только headless» и сколько реально live-admitted.

Отличать `NO_SUITABLE_SOURCE`, `NO_SUITABLE_NPC`, `GEOMETRIC_IMPOSSIBILITY`, `TEMPORARY_WORLD_DRIFT`, `BOOTSTRAP_MISMATCH`, `CODE_BUG`, `BUDGET_STOPPED`. Логи агрегированные и bounded; нет giant per-candidate console output.

## Разрешённые способы восстановить сцену

Допустимо до CUT выбрать другого проверенного NPC из bounded lawful catalog или новый ordinary штатный TEST instance через существующий native Spawn/Player fixture, если тот же exact canonical world/zone/owner и не выдуманы уровни/навыки/stats. Нельзя телепортировать уже сражающуюся цель, гасить агро и постфактум изменять HP ради equal A/B. Нельзя заменять реальный босc/NPC фальшивой статистической моделью и заявлять это за native.

## Контракты, которые 011 НЕ реализует

`PVP_1V1`, `PVP_PARTY`, `RAID_WITH_GUARDS`, `SIEGE`, `OLYMPIAD` — только расширяемые типы данных; без фиктивных PvP/raid native tests, новых owners, reward функций или learned Team Coordinator. Отдельные будущие задачи добавят законные adapters, сохраняя общую схему оценки.

## Производительность

Не делать общий перебор World на каждый тактический тик. Разрешён bounded census при preparation, read-only cache на immutable catalog с явной invalidation и повторное live-check перед admission. Следить за heap, размером объектов, кол-вом attempted candidates, CPU/search latency, а не только GameServer boot time.
