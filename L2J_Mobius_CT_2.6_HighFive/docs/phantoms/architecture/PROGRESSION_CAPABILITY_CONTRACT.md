# Progression Capability Contract

## Authority и source precedence

Runtime `Player` является источником текущего состояния. Class/skill/item/pet loader-ы являются источниками статических фактов. Строгий XML parser используется только для полей и source identity, которых нет в публичном loader API. Curated metadata задаёт capability semantics, точный action/evidence skill и target scope, но не дублирует loader-owned facts.

Identity никогда не выводится из display name или локализованного текста. Accepted Game Knowledge остаётся неизменным входом; progression присваивает каждому импортированному факту собственный детерминированный `variantKey`.

## Immutable catalog и hashes

`PhantomProgressionCatalogBuilder` копирует и сортирует:

- class graph;
- CLASS/TRANSFER/SUBCLASS/NOBLE/COMMON/TRANSFORM learning facts;
- referenced skill mechanics;
- equippable items;
- summon/servitor/cubic и pet facts;
- capability variants.

Snapshot публикуется атомарно и не хранит mutable loader objects. Полное каноническое содержимое образует шесть component SHA-256 и combined SHA-256. Query использует immutable indexes, page cursor и лимит 256; loader/file/DB scan после build запрещён.

Production composition состоит из обычных Game Knowledge facts и progression capability seeds. Её exact source identity/provenance set проверяется независимо, а component/combined hashes повторяются детерминированно. Inert synthetic fixture не считается production corpus.

## Capability group и variant identity

`capabilityKey` — coarse factual group. Каждая исполняемая альтернатива имеет непустой стабильный `variantKey`, точный `actionSkill`, supporting evidence и provenance. Уникальность задаётся тройкой `(classId, capabilityKey, variantKey)`.

Один класс может иметь несколько групп и несколько одновременно существующих вариантов одной группы. Catalog и evaluator сохраняют и возвращают все варианты; learned/readiness одного варианта не заимствуются у соседа. `rank` остаётся metadata источника и не задаёт тактического победителя.

Combat resolver рассматривает все поддержанные варианты generic capability, не завершает поиск на первом неподдержанном варианте и не превращает static rank в doctrine. Future tactical desirability принадлежит отдельному provider/doctrine layer.

## Class graph и profession boundary

Enum parent и XML skill-tree parent — разные поля. Immediate children образуют profession target graph. Уровневые факты: 20/40/76. Production progression не вызывает `setPlayerClass`, не меняет quest state и не создаёт quest items.

Общего безопасного non-packet profession facade в текущем коде не найдено. Поэтому target остаётся `CANONICAL_QUEST_REQUIRED`; `progression.await_profession` только наблюдает реальный `PlayerClass`.

## Skill learning facts и READY_NOW

Каждый learning fact хранит acquire kind, exact skill level, character level, base SP cost, aggregated required items, prerequisite skills и source. В Goal 013/013A исполняется только `CLASS`.

Capability variant имеет три независимые истины:

- `INTRINSIC` — explicit variant доступен для active class;
- `LEARNED` — exact action skill известен canonical `Player`;
- `READY_NOW` — actor state, target, equipment, resources, `Skill.checkCondition`, MP/HP и reuse допускают exact action.

Effective item requirement объединяет curated requirement и `Skill.itemConsumeId/itemConsumeCount` по одному item ID через maximum, без двойного счёта. Все positive item IDs валидируются против полного `ItemData`, а referenced IDs копируются в actor snapshot. `chargeConsumeCount` сравнивается с exact current charges.

`maximumSoulConsumeCount` — authoritative верхняя граница расхода, а не выдуманный минимум. Current souls копируются отдельно; если skill задаёт обязательное условие, authoritative dynamic condition остаётся решающей. `TargetScope` хранится явно и не выводится из имени capability или skill.

## Actor snapshots и CP

Progression snapshot под exact materialization `ActionLease` копирует base/active class, class index/tier, EXP/SP/level, Noble/Hero, subclasses, learned/certification skills, equipped items, referenced item counts, charges, souls, controlled actors и live state flags. Snapshot immutable и несёт combined catalog hash.

Общий canonical Player combat snapshot рядом с HP и MP содержит отдельные `currentCp` и `maximumCp`. Они копируются только из exact `Player.getCurrentCp()` и `Player.getMaxCp()` под существующим lease. Snapshot не владеет ресурсами и не меняет `Player`; CP не сохраняется в `PhantomProfile`.

Servitor, pet и cubic не получают fabricated Player CP. Body-bearing controlled actor содержит только фактически существующие body resources HP/MP; cubic использует typed absent body. CP potion supplies, продавцы, ограничения, валюта и стоимость будут извлекаться Goal 014 из authoritative item/NPC/buylist/multisell data. Goal 015 reconciliation не должна бесплатно сбрасывать или восстанавливать CP. PvP порядок CP → HP, regeneration, potion reuse/economy и Olympiad restrictions относятся к Goal 025.

## CLASS skill transaction

`progression.learn_skill` сериализует одну operation на профиль и повторно проверяет token после actor acquisition. Exact real trainer должен быть текущим `lastFolkNPC`, взаимодействовать с actor и уметь обучать active learning class.

Проверяются exact `Skill`, `SkillLearn`, previous level, level, calculated SP, prerequisites и aggregated required items. Текущий canonical inventory API не предоставляет доказанную атомарную multi-distinct-item mutation. Поэтому более одного distinct required item отклоняется до side effects; один aggregated item списывается ровно один раз. После последней ownership check exact SP расходуется, `Player.addSkill(..., true)` сохраняет навык, shortcuts обновляются, а `OnPlayerSkillLearn` dispatch-ится только после успешной reconciliation. Повтор exact level идемпотентен.

Packet handler, packet construction, bypass, компенсационная выдача items и batch/free learning не используются.

## Equipment facts и candidate access

Actor snapshot не владеет глобальным top-N equipment list. Exact owned objects доступны отдельным lease-bound factual query с фильтрами body part, family и canonical compatibility. Page limit не больше 64; стабильный cursor основан на object identity, и любой matching object остаётся достижимым.

`OwnedEquipmentFact` сохраняет object ID, item ID, equipped, grade, enchant, body part, family, compatibility и reasons. Глобального grade/enchant/P.Atk/M.Atk preference score нет. Contextual combat/economic scoring остаётся future doctrine.

`progression.equip_item` принимает exact owned object, проверяет actor state, location, equippability и `ItemTemplate.checkCondition`, затем вызывает `Player.useEquippableItem`. Direct paperdoll mutation, покупка, создание, enchant и augmentation отсутствуют.

## Subclass, Noble и certification

Snapshot наблюдает `PlayerConfig.MAX_SUBCLASS`, active/base class, class index, subclasses, Noble/Hero и реально известные certification skills. Eligibility использует `CategoryData`, текущий `VillageMaster.getSubclasses` и реальные quest predicates. Main → subclass → main integration доказывает exact active skills без ordinary cross-index leakage; certification facts остаются отдельно от active class-tree evidence.

Goal 013A не добавляет, не удаляет и не переключает production subclass и ничего не выдаёт.

## Summon, pet и cubic taxonomy

Catalog различает `SERVITOR`, `PET`, `BABY_PET`, `CUBIC`, `SIEGE_SUMMON` и `QUEST_SUMMON`. Каждый variant сохраняет summon skill/level, NPC identity, own skill references и классифицированные heal/recharge/buff/damage/control mechanics. Lifetime, upkeep, EXP multiplier, summon item, shots, control item, food, inventory и pickup facts остаются самостоятельными.

Body-bearing runtime snapshot — immutable copy object identity, kind, instance, position, HP/MP, target, dead state и reference summon skill. Cubic не является `Playable` body, не имеет fabricated coordinates/HP/MP и не рекламирует follow/hold/move/attack body commands.

Goal 013A не исполняет summon commands, не выбирает summon, не создаёт master–summon controller и не реализует background reconciliation.

## Handlers, lifecycle, bounds и metrics

До registry seal регистрируются:

- `progression.observe`;
- `progression.await_level`;
- `progression.await_profession`;
- `progression.learn_skill`;
- `progression.equip_item`.

Production candidate не регистрируется. Handler проверяет exact namespace/arguments и возвращает typed success/retry/replan/cancelled.

Startup/shutdown dependency direction Goal 013 не изменена. Новых thread, executor, worker, task или future нет. Metrics остаются агрегированными без dynamic labels.

## Research normalization и exclusions

Нормализованные DR-01…DR-05 находятся в `docs/phantoms/research/high-five-behavior/` и используют stable claim IDs с authority, confidence и source paths. Raw research, turn-citations, class rankings и scope будущих Goal исключены.

Goal 013A не реализует gameplay zoning, supply/restock, party, PvP/PK/Olympiad doctrine, raids, commerce, subclass/Noble mutation, profession quest, summon commands, CP potion use или attribute formula fix. Goal 014/015/017/025 не начаты.
