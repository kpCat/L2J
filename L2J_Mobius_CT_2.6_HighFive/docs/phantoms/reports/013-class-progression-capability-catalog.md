# Goal 013 — Class progression capability catalog

## Статус и baseline

Status: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`

- baseline: `8dba87e9c1d5828376b80c1ea16c4578726d4947`;
- branch: `feature/phantom-world`;
- parent: `8dba87e9c1d5828376b80c1ea16c4578726d4947`;
- subject: `feat(phantoms): add class progression capability catalog`;
- manual gate: `PENDING_INDEPENDENT_REVIEW`;
- Goal 014: `NOT_STARTED`;
- Goal 015: `NOT_STARTED`.

## Закрытие Goal 012A

Goal 012 принят после Goal 012A. Goal 012A принят без revert.

Неизменяемая передача:

- commit: `8dba87e9c1d5828376b80c1ea16c4578726d4947`;
- parent: `74dd973c167adf0a74e7af78ed7944e2518c16cb`;
- combat core: `47/47 ×3`;
- ownership: `17/17 ×3`;
- action ownership: `33/33 ×3`;
- real integration: `19/19 ×2`;
- performance: `1/1 ×2`;
- `ant verify`: `PASS ×2`;
- `ant jar`: `PASS ×2`;
- verifier: `102/102 ×2`, byte-identical;
- verifier SHA-256: `7F5EFA1D3D506E73A5741010833DF82685A0530BBF24D0E7C9326F8514E81A16`;
- независимый verdict: `ACCEPT`.

Review зафиксирован в
`docs/phantoms/reviews/012a-combat-action-ownership-truth-review.md`.

## Аудит источников

Прочитаны и сопоставлены текущие High Five:

- `PlayerClass`, `ClassListData`, `CategoryData`;
- `SkillTreeData`, `SkillData`, `Skill`, `SkillLearn`,
  `AcquireSkillType`, `RequestAcquireSkill`;
- `Player`, `PlayerStat`, `Folk`, `VillageMaster`;
- `SubClassHolder` и текущие subclass/Noble predicates;
- `ItemData`, `ItemTemplate`, `Weapon`, `Armor`, `Item`, `Inventory`;
- `PetDataTable`, `PetData`, `Summon`, `Servitor`, `Pet`, `BabyPet`, `Cubic`;
- summon effects и High Five class/skill/item/pet/NPC datapack;
- Game Knowledge capability port;
- materialization ActionLease, decision registry и `PhantomSystem` lifecycle.

Общего canonical profession facade с полным quest/item/NPC authorization не
обнаружено. Production class mutation поэтому намеренно отсутствует.

## Catalog corpus

Loader-parity snapshot:

- class facts: `103`;
- terminal classes: `36`;
- complete skill-learning facts: `44 019`;
- referenced skill-mechanics facts: `7 414`;
- equippable items: `7 715`;
- summon/controlled-actor facts: `379`;
- pet facts: `49`;
- curated capability rules в изолированном loader parity corpus: `17`.

Все факты копируются один раз при startup и публикуются immutable. Индексы
включают class ID, children, terminal classes, class skills, classes by skill,
skill identity, item ID/body part/family, summon owner/skill/NPC, pet NPC и
capability class/key. Запросы не читают loader, XML или БД.

Male/Female Soul Hound сохранены как class IDs `132/133`. Inspector и Judicator
сохранены как отдельные стадии `135/136`; Judicator terminal.

## Hashes

- class graph:
  `B91E569EFE5886519A999C84215BAD432A747A11EB5943298A1EC652AF076C49`;
- skill learning:
  `6A22326996332FFA92B7075CF81F568471578DDFB40C57827CC0A1F9B40A54FC`;
- skill mechanics:
  `C57DA5056CD79D5CEDC95D9C4515377C4CC0FC63CB615DC22B15B5726895C607`;
- equipment:
  `CE415CFEE5ADE2A370BB8B2A64D7D9D9306403D229E2C28A37944CC72C5FED49`;
- summon/pet:
  `710FA33A89FA342327DD2145F37C796450917FC21016F61D4FC26AF4B3905CE2`;
- capability rules:
  `E5D8AC33564B337CA3FEA2267633979E7F5D6B0A4F6222DC4BBF6C18DF6AFF8A`;
- combined:
  `67531879638D7F7F4B8EA5D94BF4451B0A9A89A116E38CB3D65B00E7D78E722E`.

Hashing использует stable identities и полное содержимое immutable records;
локализованные display names не участвуют.

## Profession и canonical progression

Сервис наблюдает canonical `Player` level/EXP/SP, active/base class, class index,
subclasses, Noble/Hero и certification skills. Между snapshots возвращаются
точные `LEVEL_REACHED` или `PROGRESS_PENDING`.

Immediate profession targets строятся только из class graph. При достигнутом
уровне и отсутствии общего canonical authorization facade возвращается
`CANONICAL_QUEST_REQUIRED`; до уровня — `LEVEL_PENDING`. Production
`setPlayerClass`, выдача EXP/SP/level, quest-state mutation и подделка marks
отсутствуют. Реальное внешнее profession change только наблюдается и
reconciles.

Subclass eligibility остаётся read-only и использует текущие
`PlayerConfig.MAX_SUBCLASS`, `CategoryData`, race/class restrictions и actor
quest predicate. Переключение subclass, выдача certification/Noblesse не
реализованы.

## Runtime capability

Capability evaluation разделяет:

- `INTRINSIC`: catalog/class stage имеет явное evidence;
- `LEARNED`: actor знает required skill level;
- `READY_NOW`: canonical condition probe, equipment, resources, state, target и
  servitor requirements выполнены.

Target scope хранится явно. Class/skill names не используются для inference.
Причины неготовности typed и bounded.

Actor snapshot копируется под одним точным materialization lease. `Player`,
`Skill`, `Item`, `Npc` и mutable collections наружу не выходят. Candidate items
ограничены policy, страницы — `256`.

## Explicit operations

`progression.learn_skill` выполняет только один явно указанный
`AcquireSkillType.CLASS` skill:

- проверяет exact current plan token и один operation/profile;
- повторно проверяет actor state;
- использует exact `lastFolkNPC`, `Folk`, interaction/range;
- получает exact `SkillLearn` для active class;
- проверяет level, previous/prerequisite skills, SP и required items;
- списывает exact SP/items существующими `Player` methods;
- добавляет один skill и отправляет canonical `OnPlayerSkillLearn`;
- проверяет exact before/after conservation.

`progression.equip_item` принимает только object ID уже принадлежащего actor
item, проверяет compatibility/expertise/condition и вызывает
`Player.useEquippableItem`. Создание, покупка, enchant и direct paperdoll
mutation отсутствуют.

## Summon/pet и research normalization

Проверенные части DR-01…DR-05 нормализованы в
`docs/phantoms/research/high-five-behavior/`. Raw research, browser turn
citations и tier lists не копировались. Out-of-scope claims направлены в exact
future Goals без механического влияния на production.

Зафиксированы current Mobius contradictions:

- текущий `Servitor` прямо отражает owner attribute values, что расходится с
  внешним claim `20/80`;
- Olympiad cleanup удаляет pet, но сохраняет servitor;
- pet имеет inventory/pickup semantics, обычный servitor — нет;
- summon death/hate и mutual owner response следуют текущему server code.

Это observation facts, не исправление формул и не новая combat doctrine.

## Lifecycle, handlers и inertness

До seal зарегистрированы:

- `progression.observe`;
- `progression.await_level`;
- `progression.await_profession`;
- `progression.learn_skill`;
- `progression.equip_item`.

Production candidates: `0`. Progression workers/tasks/futures: `0`.
Автоматические class/skill/equipment/subclass/summon operations на startup:
`0`. Disabled/non-production path не создаёт production backend/service.
Shutdown останавливает progression до materialization и требует нулевые
operations/actor leases.

## Tests, performance и regressions

Focused suites:

- catalog: `60/60 ×3`;
- independent loader parity: `32/32 ×2`;
- capability runtime: `40/40 ×3`;
- operations: `36/36 ×3`;
- real server integration: `18/18 ×2`;
- performance: `2/2 ×2`.

Performance canonical summary:

```text
catalogBuilds=3
classQueries=100000
skillQueries=100000
capabilityEvaluations=100000
equipmentQueries=50000
summonPetQueries=50000
operations=10000
```

Structural result: workers/tasks/futures `0`, operations after run `0`, actor
leases after run `0`, max page `103`, max candidate set `1`. Elapsed — evidence
only: `22 567 ms` и `22 674 ms`. Оба canonical summaries byte-identical,
SHA-256:
`A2FC776AACBFD829695DDE670AEF3F14F328D499BF80C22A234D31D03B617BA6`.

Cumulative combat ×3, combat real ×2, combat performance ×2, all Goal 011A
knowledge routes, topology/navigation/decision/scheduler/materialization/
headless/profile/DB, ordinary tests, skeleton, negative controls, `ant verify`
и standalone `ant jar` выполнены. Pre-commit cumulative `ant verify`: `PASS`,
`3:57`.

Pre-change `ant verify` один раз воспроизвёл существующую flaky
`combat-server-integration.02`; остальные 18/19 cases прошли. Два последующих
targeted прогона дали `19/19 ×2`, cumulative verify также завершён успешно.

## DB, config и scope

- production DB `l2jmobiush5`: не использовалась;
- test DB: только `l2jmobiush5_phantom_test`;
- migrations/schema: нет;
- config: не изменён;
- server core: не изменён;
- class/skill/item/pet/NPC datapack: не изменён;
- `.gitignore`: не изменён;
- другие хроники: не изменены;
- Goal 014/015: `NOT_STARTED`.

Объём `50` text files является bounded exception, заранее заданным exact
allowlist Task 013: в число входят `11` файлов самого task package, `10`
нормализованных research documents, production catalog package, шесть focused
test suites, contract/report/review и минимальные lifecycle/build ports.
Независимые Goal или housekeeping в exception не включены.

## Verifier, JAR и ограничения

`tools/phantoms/verify-task-013.ps1` — deterministic read-only exact-scope
verifier. Pre-commit результат: `58/58`. Финальные два post-commit запуска и их
SHA-256 фиксируются во внешнем handoff.
`GameServer.jar` содержит production progression classes и не содержит tests.

Ограничения:

- profession change остаётся `CANONICAL_QUEST_REQUIRED`, пока не появится общий
  canonical facade с полным quest authorization;
- TRANSFER/SUBCLASS/NOBLE/COMMON/TRANSFORM facts queryable, но не executable в
  Goal 013;
- текущие Mobius summon/Olympiad contradictions только документированы;
- independent review Goal 013 не выполнен этим commit.

Commit SHA и push result предоставляются внешним final handoff, потому что этот
отчёт входит в сам ordinary commit.

## Gate

`CLASS_PROGRESSION_CAPABILITY_CATALOG_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`
