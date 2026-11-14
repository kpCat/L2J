# Покрытие сцен011

Итог: `BLOCKED_PRE_CUT_REQUIRED_SOLO_DAMAGE_MISSING`. Это недостаточность текущего bounded TEST source/world, а не глобальная невозможность PvE High Five.

## Фактические стадии

| Стадия | Результат011 |
|---|---:|
| Проверенные source records |19|
| Фактически acquired snapshots |10|
| Исключены TRAIN/reference provenance |6|
| Независимые EVAL source snapshots |4|
| PROPOSED cells |4|
| PROPOSED Solo DAMAGE |0|
| PROPOSED Party HEAL |4|
| Полный paired HEADLESS_READY |0|
| LIVE_ADMITTED / CUT / PAID / TERMINAL |0 /0 /0 /0|
| MATCHED_PAIR011 Solo /Party |0 /0|
| TRAIN_CREDIT / FIT / PROMOTION |0 /0 /0|

Четыре предложения не прошли mandatory coverage и не являются четырьмя законными боями. Paired passive admission не запускался; `admission.complete.json` отсутствует. `cells.tsv` и counters сохранены как PROPOSED evidence. AB/BA игровых011 исходов нет.

## Actual capability × target

Эти ID — наблюдённые данные census, не новый algorithm allowlist.

| Независимый source | Фактические данные | Допустимый target envelope |
|---|---|---|
|94/76, `d0597ae9…` |1230:101 magicLevel76 и1275:14 magicLevel74 |≥2 DAMAGE при NPC72–74|
|110/77, `92dd8c02…` |1148:13,1234:28,1239:28,1267:14 magicLevel74 |≥2 DAMAGE при NPC73–74|
|105/76, `667b7dab…` |HEAL1011:18/1015:15/1020:27/1217:33;1401:11 требует3031 |Party требует настоящего offense helper/risk/target admission|
|90/76, `623c2ade…` |49:26 magicLevel74 только UNDEAD;70:13 magicLevel32; физические skills обнаружены |Нельзя считать двух ready DAMAGE только по class или magicLevel|

Оба class103 источника остаются исключёнными TRAIN provenance, их навыки не возвращены в независимую EVAL. Full acquired inventory, включая passive/enchant/unknown, не сокращён до shortlist.

## Источники NPC

HotSprings21315/73 и21318/74 имеют source-bound topology, но clanALL/help300/drift300 и окружение требуют реального isolation proof. SilentValley20965/73,20966/74,20970/73 — обычные passive non-faction NPC с positive EXP/SP. Их territory/source отсутствует в текущем topology contract; `exactTerritoryNode` требует exact geometry/source/feasible anchor, а один anchor2000 не покрывает широкую территорию. Нельзя объявлять такие данные lawfully ready по одному template или ослаблять binding.

В011 выбраны только preliminary Party HEAL на21798/21799 уровня76: healer inventory не является доказательством готовности полного Party encounter. Для Solo два фактических DAMAGE skills с target76 не подтверждены. GameServer не запускается ради повторного поиска.

## Измеренные counters

Counters — суммы bounded подготовительных обходов, не количество уникальных NPC и не native результаты:

| Counter | Значение |
|---|---:|
|SAFE_TEMPLATE_SEEN|448838|
|LEVEL_REWARD_GEO_ACCEPTED|35891|
|SOURCE_SKILLS_OR_MAGIC_LEVEL|26998|
|CAPABILITY_TARGET_ACCEPTED|8893|
|CANONICAL_ANCHOR_ACCEPTED|4455|
|ANCHOR_AGGRO_REFUSED|2131|
|NO_ISOLATED_TARGET_POINT|280|
|ISOLATED_SCENE_ACCEPTED|12|
|OUT_OF_WORLD_TEMPLATE_REFUSED|52|

`TARGET_BOUND_ALTERNATIVES=26998`; в JSON только128 bounded samples, не128 total. CAPABILITY_TARGET_ACCEPTED включает HEAL proposals, для которых learner DAMAGE filter не применяется; это не число fully-ready target/Party pairs.

## Пределы

Source-first admission теперь отбрасывает incompatible ranks до placement и не расширяет координатную сетку. Активные target points — только фактические current/spawn, без переноса цели обратно после дрейфа. Это намеренно консервативный TEST fixture; отсутствие изолированной clone placement не доказывает невозможность ordinary gameplay со всеми NPC/группами. Для расширения source/topology или другого encounter realization нужен отдельный явно разрешённый scope; в011 этого изменения нет.

PVP_1V1/PVP_PARTY/RAID_WITH_GUARDS — зарезервированные значения schema с явным NOT_IMPLEMENTED, без adapters. GAME_ADVANTAGE=INCONCLUSIVE, игровых оценок011 нет.
