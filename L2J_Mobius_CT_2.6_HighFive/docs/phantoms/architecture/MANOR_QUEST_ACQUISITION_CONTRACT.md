# Manor/quest acquisition contract

## Статус

Goal 021 Checkpoint 2 остаётся `BLOCKED` до появления разрешённого topology
coverage для строго аудированных quest targets. Реализованные расширения
остаются fail-closed: отсутствие допустимого source не создаёт предметы.

## MANOR_CROP

Active chain использует только текущие зарегистрированные `Seed` и `Harvester`
item handlers, их `Sow`/`Harvesting` effects и существующий Combat service.
Phantom-код не вызывает `setSeeded`, `takeHarvest`, `addItem` или `destroyItem`.

Schema-3 binding фиксирует seed/crop/mature/reward identities, castle, exact
item object IDs, формулы, исходные counts и manor authority hash. До каждого
внешнего side effect сохраняется owning phase. Recovery различает active cast,
seed/seeder evidence, crop delta и исчерпанный bounded retry.

Background chain повторяет текущие Sow/Harvesting формулы, отдельно учитывает
seed consumption, Combat/death-drop и crop payload. Item/background/Goal/
acquisition state изменяются одной существующей atomic transaction.

Procurement, crop exchange, castle production, treasury и reward items не входят
в direct acquisition product.

## QUEST_COLLECTION

Каталог содержит только два source-hashed правила:

- `Q00102_SeaOfSporesFever`: targets `20013`, `20019`, item `966`, cond `2`,
  roll `Rnd.get(10) < 3`, conservative cap `9`;
- `Q00152_ShardsOfGolem`: target `20016`, item `1010`, cond `2`, roll
  `Rnd.get(100) < 30`, conservative cap `4`.

Runtime authority проверяет полный SHA-256 source, точный Quest class/name/ID,
kill registration, quest-item registration и NPC/item references. Acquisition
не запускает и не завершает quest, не вызывает `Quest.onKill` и не меняет
state/cond/vars.

Active chain предназначен для существующего Combat и реального delayed
`OnAttackableKill`. Background chain читает под lock только exact
`character_quests` rows и quest item rows; успешная проекция коммитит item,
background, Goal и acquisition одной transaction, сохраняя quest rows
byte-identical.

## Blocker

Оба принятых script-а используют только territory spawns в Elven/Talking Island.
Текущий `high-five-core.xml` покрывает Giran и SSQ и не содержит node/anchor для
этих targets. Game Knowledge поэтому честно публикует их spawn areas без
`topologyNodeId`, а source planner возвращает `quest.target_unavailable`.

Task allowlist не разрешает менять topology data. Произвольный Giran anchor,
runtime-random territory point или generic quest interpreter запрещены как
недетерминированный/неавторитетный обход. Для продолжения нужен отдельный
разрешённый bounded topology slice для `20013`, `20019`, `20016` либо новый task
с другим заранее аудируемым набором scripts.

## Инварианты

- schema readers принимают версии 1/2/3, writer выдаёт только 3;
- method binding ровно один и входит в operation identity;
- uncertain active claim блокирует background replay;
- source/rule/handler/formula hash drift закрывает источник;
- Phantom World disabled не загружает C2 catalog и не создаёт worker;
- новых threads, executors, handlers, schema migrations и Goal 022 work нет.
