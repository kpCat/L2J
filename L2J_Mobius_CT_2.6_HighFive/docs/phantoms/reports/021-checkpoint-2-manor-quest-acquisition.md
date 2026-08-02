# Goal 021 Checkpoint 2 — manor/quest acquisition

## Status

`BLOCKED`

Checkpoint 1 зафиксирован как `ACCEPT` на baseline
`0045f60417f4605f46e3058b9a694278283b1456`; verifier 021c1 переведён в
historical/descendant-compatible режим и прошёл PowerShell 5.1/7.x.

Checkpoint 2 нельзя честно закрыть: два доказанно безопасных quest script-а не
имеют instance-0 topology-mapped spawn/anchor в текущем разрешённом topology
snapshot. Task запрещает менять topology data. Goal 022+ не начат.

## Summary

- MANOR_CROP реализован через canonical Seed/Sow → existing Combat →
  Harvester/Harvesting, schema-3 binding и persisted dispatch/recovery.
- Active/background manor используют одинаковые текущие формулы и manor rate;
  procurement/reward exchange отсутствуют.
- QUEST_COLLECTION ограничен двумя source-hashed rules, exact STARTED/cond/item
  evidence, delayed callback lifecycle и atomic background transaction.
- Source planner fail-closed отклоняет оба rules как `quest.target_unavailable`,
  потому что их territory spawns не связаны с текущим topology snapshot.
- Production остаётся компилируемым и безопасным: невозможный quest source не
  планируется и не создаёт item.

## Quest audit — ровно 12 scripts

Accepted:

1. `dist/game/data/scripts/quests/Q00102_SeaOfSporesFever/Q00102_SeaOfSporesFever.java` — exact targets `20013/20019`, item `966`, cond `2`, one bounded roll/grant; cap `9` исключает cond mutation branch.
2. `dist/game/data/scripts/quests/Q00152_ShardsOfGolem/Q00152_ShardsOfGolem.java` — exact target `20016`, item `1010`, cond `2`, one bounded roll/grant; cap `4` исключает cond mutation branch.

Rejected:

3. `dist/game/data/scripts/quests/Q00105_SkirmishWithOrcs/Q00105_SkirmishWithOrcs.java` — grant неотделим от `setCond` и order-item state.
4. `dist/game/data/scripts/quests/Q00107_MercilessPunishment/Q00107_MercilessPunishment.java` — kill branches меняют cond и зависят от нескольких orders.
5. `dist/game/data/scripts/quests/Q00113_StatusOfTheBeaconTower/Q00113_StatusOfTheBeaconTower.java` — отсутствует поддерживаемый kill-collection branch.
6. `dist/game/data/scripts/quests/Q00158_SeedOfEvil/Q00158_SeedOfEvil.java` — kill меняет cond и имеет NPC broadcast/script state.
7. `dist/game/data/scripts/quests/Q00354_ConquestOfAlligatorIsland/Q00354_ConquestOfAlligatorIsland.java` — party selection и multi-branch effects.
8. `dist/game/data/scripts/quests/Q00357_WarehouseKeepersAmbition/Q00357_WarehouseKeepersAmbition.java` — random party-member grant.
9. `dist/game/data/scripts/quests/Q00358_IllegitimateChildOfTheGoddess/Q00358_IllegitimateChildOfTheGoddess.java` — party selection и completion cond mutation.
10. `dist/game/data/scripts/quests/Q00360_PlunderTheirSupplies/Q00360_PlunderTheirSupplies.java` — независимые дополнительные item mutations в том же kill.
11. `dist/game/data/scripts/quests/Q00369_CollectorOfJewels/Q00369_CollectorOfJewels.java` — party/variable/cond side effects.
12. `dist/game/data/scripts/quests/Q00370_AnElderSowsSeeds/Q00370_AnElderSowsSeeds.java` — random party recipient и несколько random branches.

Accepted source hashes:

- Q00102: `cc3c1a893e6fe0763b806a17aa01e1d59a4c3f4743c3a577b2597bec07978d1f`;
- Q00152: `e086d06935b0515142f431486ded1f71b8caa4843f69605296e64a4e8ffdf378`.

## Дополнительный bounded read pass — 16 paths/symbols

1. `dist/game/data/scripts/handlers/items/Seed.java` — подтверждён штатный item-handler вход в посев.
2. `dist/game/data/scripts/handlers/items/Harvester.java` — подтверждены canonical harvest checks и exhausted-result semantics.
3. `dist/game/data/scripts/handlers/skill/effects/Sow.java` — подтверждены шанс посева, расход seed и manor state transition.
4. `dist/game/data/scripts/handlers/skill/effects/Harvesting.java` — подтверждены crop formula и штатный grant path.
5. `java/org/l2jmobius/gameserver/model/actor/Attackable.java` — подтверждены существующие seeded/harvest runtime fields; файл не менялся.
6. `java/org/l2jmobius/gameserver/managers/CastleManorManager.java` — подтверждён manor catalog/rate authority; файл не менялся.
7. `java/org/l2jmobius/gameserver/model/script/Quest.java` — подтверждён delayed `OnAttackableKill` dispatch contract.
8. `java/org/l2jmobius/gameserver/model/script/QuestState.java` — подтверждены STARTED/cond/item read semantics и запрещённые mutations.
9. `java/org/l2jmobius/gameserver/managers/ScriptManager.java` — подтверждена runtime identity загруженного quest script.
10. `java/org/l2jmobius/gameserver/model/events/holders/actor/npc/attackable/OnAttackableKill.java` — подтверждён реальный kill event payload.
11. `java/org/l2jmobius/gameserver/data/xml/MapRegionData.java` — подтверждена normal-world region authority.
12. `java/org/l2jmobius/gameserver/model/spawns/Spawn.java` — подтверждён runtime spawn location contract.
13. `java/org/l2jmobius/gameserver/data/SpawnTable.java` — проверено фактическое наличие target NPC spawns.
14. `java/org/l2jmobius/gameserver/model/zone/type/NpcSpawnTerritory.java` — подтверждена territory-based spawn модель без стабильной точки.
15. `java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomGameKnowledgeBuilder.java` — подтверждено намеренное unresolved topology evidence для territory spawns.
16. `dist/game/data/phantoms/topology/high-five-core.xml` — подтверждено отсутствие узлов/anchors для Elven Territory и Talking Island.

## Architecture decisions

- Schema versions 1/2/3 читаются, writer выдаёт только schema 3; payload остаётся <=4096 bytes.
- Manor и quest имеют mutually-exclusive typed bindings, owning phases, bounded attempts и receipts.
- Canonical item handlers/skills и Quest runtime identity проверяются без reflection/interpreter.
- Active quest ждёт текущий delayed `OnAttackableKill`; production не вызывает `Quest.onKill`.
- Background quest lock/read ограничен exact quest name, `state`, `cond`, <=4 declared vars и item row.
- Background item/background/Goal/acquisition mutation выполняется одной transaction; quest rows не меняются.
- Runtime catalog validation выполняется при использовании после загрузки scripts, а disabled Phantom World его не загружает.

## Changed files

Production/data/config: 20 files, из них 3 новых (`manor` authority, quest catalog, quest XML). Изменены только разрешённые Phantom acquisition/background/combat/System paths и acquisition XML. Schema, handlers, quest scripts, `Player.java`, `Party.java`, `Attackable.java`, `CastleManorManager` и topology data не менялись.

Tests/build/tools/docs: task package, two focused suites, server integration/launcher/build extensions, historical verifier 021c1, C1 review, master/roadmap, architecture contract и этот report. Общий scope остаётся ниже лимитов 18/34/58.

## DB/migrations/config

- Только `l2jmobiush5_phantom_test`; production DB не использовалась.
- Миграций/schema changes нет.
- Seed: `21002102` через `phantom.goal021c2.seed`.
- Добавлены только C2 retry/formula/catalog limits; Phantom World остаётся disabled by default.

## Commands and test results

PASS:

- `verify-task-021c1.ps1` — PowerShell 5.1 и 7.x, byte-identical `GOAL_021C1_VERIFIED`.
- `phantom-acquisition-manor-catalog-source-test` — 3/3.
- `phantom-acquisition-manor-active-test` — 2/2 после исправления тестового ожидания canonical Harvester evidence.
- `phantom-acquisition-manor-background-test` — 2/2.
- `phantom-acquisition-manor-restart-transition-test` — 2/2.
- `phantom-acquisition-quest-catalog-source-test` — 3/3.
- `phantom-acquisition-quest-background-test` — 3/3.
- `phantom-acquisition-checkpoint2-lifecycle-performance-smoke` — 5/5.
- Production/test compilation: 2119/82 sources.
- Mojibake-маркеры в изменённых файлах проверены: совпадений нет.
- Escaped Cyrillic в изменённых файлах проверены: совпадений нет.

BLOCKED gate:

- `phantom-acquisition-quest-active-test` — before-all: `No curated quest target has a bounded normal-world topology anchor.`

Не запускались после blocker: affected aggregate, final checkpoint2 aggregate,
plain `ant verify`, `ant jar`, verifier 021c2. Freeze не объявлялся. Лимит full
verify не израсходован.

## Performance

Lifecycle smoke прошёл 100k manor planning formulas, 100k quest rule checks,
10k manor background batches и 10k quest background batches без новых workers.
Числа latency не используются как acceptance при BLOCKED status.

## Deviations, limitations, risks

- Required topology condition не выполнено для accepted scripts; это единственный terminal blocker.
- Не добавлялись произвольные anchors, runtime-random territory authority или третий checkpoint.
- Active real delayed quest path не достигнут, потому что fixture намеренно требует тот же topology precondition, что production source planning.
- Частичная C2 production поверхность должна пройти повторный full gate после разрешённого topology slice.

## Git

- Branch/upstream: `feature/phantom-world` / `origin/feature/phantom-world`.
- Required parent: `0045f60417f4605f46e3058b9a694278283b1456`.
- Commit subject: `feat(phantoms): add manor and quest acquisition chains`.
- Commit SHA и push result: фиксируются final handoff для commit, содержащего этот report; amend/force-push не используется.
- Разрешённые git-команды использовались только для baseline/scope/diff проверки и обязательного commit/push workflow.

## Next step

Нужно явное расширение scope на bounded topology nodes/anchors для natural spawn
areas targets `20013`, `20019`, `20016` (без изменения quest scripts), после
чего повторяются active quest, affected/final aggregates, freeze, plain verify,
jar и два byte-identical verifier 021c2. До этого Goal 022+ не начинать.
