# Progression Capability Contract

## Authority и source precedence

Runtime `Player` является источником текущего состояния. Class/skill/item/pet loader-ы являются источником статических фактов. Строгий XML parser используется только для полей и source identity, которых нет в публичном loader API. Curated metadata задаёт capability semantics, точные evidence skill ID и target scope, но не дублирует loader-owned facts.

Identity никогда не выводится из display name или локализованного текста.

## Immutable catalog и hashes

`PhantomProgressionCatalogBuilder` копирует и сортирует:

- class graph;
- CLASS/TRANSFER/SUBCLASS/NOBLE/COMMON/TRANSFORM learning facts;
- referenced skill mechanics;
- equippable items;
- summon/servitor/cubic и pet facts;
- capability rules.

Snapshot публикуется атомарно и не хранит mutable loader objects. Полное каноническое содержимое образует шесть component SHA-256 и combined SHA-256. Query использует immutable indexes, page cursor и лимит 256; loader/file/DB scan после build запрещён.

## Class graph и profession boundary

Enum parent и XML skill-tree parent — разные поля. Immediate children образуют profession target graph. Уровневые факты: 20/40/76. Production progression не вызывает `setPlayerClass`, не меняет quest state и не создаёт quest items.

Общего безопасного non-packet profession facade в текущем коде не найдено. Поэтому target остаётся `CANONICAL_QUEST_REQUIRED`; `progression.await_profession` только наблюдает реальный `PlayerClass`.

## Skill learning facts и readiness

Каждый learning fact хранит acquire kind, exact skill level, character level, base SP cost, required items, prerequisite skills и source. В Goal 013 исполняется только `CLASS`.

Capability имеет три независимые истины:

- `INTRINSIC` — explicit rule доступен для active class;
- `LEARNED` — exact evidence skill известен canonical `Player`;
- `READY_NOW` — actor жив, не transformed/mounted, target присутствует, equipment/resources подходят, а `Skill.checkCondition`, MP/HP и reuse допускают действие.

`TargetScope` хранится явно и не выводится из названия capability или skill.

## Actor progression snapshot

Под exact materialization `ActionLease` копируются base/active class, class index/tier, EXP/SP/level, Noble/Hero, subclasses, learned/certification skills, equipped и bounded owned equipment, referenced resources, controlled actors и live state flags. Snapshot несёт combined catalog hash.

## CLASS skill transaction

`progression.learn_skill` сериализует одну operation на профиль и повторно проверяет token после actor acquisition. Exact real trainer должен быть текущим `lastFolkNPC`, взаимодействовать с actor и уметь обучать active learning class.

Проверяются exact `Skill`, `SkillLearn`, previous level, level, calculated SP, prerequisites и все required items. После последнего ownership check штатные inventory methods расходуют items, canonical Player state расходует exact SP, `Player.addSkill(..., true)` сохраняет навык, shortcuts обновляются и существующее событие обучения dispatch-ится один раз. Итоговые skill/SP/items сверяются. Повтор exact level идемпотентен.

Packet handler, packet construction, bypass и batch/free learning не используются.

## Equipment evaluation и equip

Кандидаты берутся только из inventory/PAPERDOLL exact actor и ограничены 64. Рекомендация использует condition compatibility, grade, enchant, body part/family и стабильный item tie-break; market price и выдуманный DPS запрещены.

`progression.equip_item` принимает exact owned object, проверяет actor state, location, equippability и `ItemTemplate.checkCondition`, затем вызывает `Player.useEquippableItem`. Direct paperdoll mutation, покупка, создание, enchant и augmentation отсутствуют.

## Subclass, Noble и certification

Snapshot наблюдает `PlayerConfig.MAX_SUBCLASS`, active/base class, class index, subclasses, Noble/Hero и реально известные certification skills. Eligibility использует `CategoryData`, текущий `VillageMaster.getSubclasses` и реальные quest completion predicates. Goal 013 не добавляет, не удаляет и не переключает subclass и ничего не выдаёт.

## Summon и pet taxonomy

Каталог различает servitor, pet, baby pet, cubic, siege/quest summon. Summon effects дают lifetime/upkeep/EXP facts; `PetDataTable` и pet XML дают control item, food, levels, inventory/pickup и skills. Wyvern `itemId=-1` сохраняется без fabricated summon skill.

Current Mobius contradictions — зеркалирование servitor attributes вместо внешней 20/80 формулы и различное pet/servitor поведение в Olympiad — документируются как `CURRENT_SERVER_IMPLEMENTATION`, но не исправляются.

## Handlers, lifecycle, bounds и metrics

До registry seal регистрируются:

- `progression.observe`;
- `progression.await_level`;
- `progression.await_profession`;
- `progression.learn_skill`;
- `progression.equip_item`.

Production candidate не регистрируется. Handler проверяет exact namespace/arguments и возвращает typed success/retry/replan/cancelled.

Startup: materialization → decision registry → navigation → topology → Game Knowledge → progression catalog → combat → scheduler. Shutdown: scheduler/decision/combat → progression → knowledge/topology/navigation; materialization закрывается только после progression operations и actor leases.

Новых thread, executor, worker, task или future нет. Metrics агрегируют builds/failures, corpus counts, queries, snapshots, evaluations, operations, leases, skill/equip outcomes, conservation, quest-required, cancellation и stop failures без dynamic labels.

## Research normalization и exclusions

Нормализованные DR-01…DR-05 находятся в `docs/phantoms/research/high-five-behavior/`. Raw research, turn-citations, class rankings и scope будущих Goal исключены.

Goal 013 не реализует gameplay zoning, supply/restock, party, PvP/PK/Olympiad doctrine, raids, commerce, subclass/Noble mutation, profession quest, summon commands или attribute formula fix. Goal 014/015 не начаты.
