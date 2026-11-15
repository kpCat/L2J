# RESULT015 — PARTIAL, MP policy исправлена, TRAIN не закрыт

**Статус:** `PARTIAL015_MP_POLICY_FIXED_UNSEALED_TRAIN`. `FULL015=false`, `GAME_ADVANTAGE=NO_EVAL`, `INTELLIGENCE_IMPROVEMENT=NOT_PROVEN`. Задача№9/13, после выдачи остаётся4 до обязательного пересмотра бюджета. Следующий Goal не начат. Owner manual gate REQUIRED.

## Что изменено

Отдельно утверждённая production дельта находится только в общем `L2jPhantomPartyBackend.groupViable`: прогноз MP на весь бой становится риском и сам не запрещает initial pull/continuation. Native learned/current-cast MP/HP/item/target/owner/Geo/LOS и остальные действующие gates сохранены. Формулы в trainer не скопированы. Observer того же вызова сохраняет реальные MP/allowance/risk/reason отдельно от результата admission и реального боя.

Code commit `91109ae9268908a4e384763befe2dee2834ddec3`, parent Studio `5cff6b19efc140700af8950255c4dc9c13206c96`: ровно8 согласованных файлов,420insertions/36deletions. Предшествующие Studio43files/4067insertions/20deletions сохранены fast-forward, не входят в собственный code diff015. MAIN/PLAY не развёртывались. Модель в production не повышалась.

## Доказательство на исходном случае

S1 настоящий pre-CUT Party STOCK admission: admitted=true, target268525904, combatRequestValidated=true. У actual actor268525745 canonical estimate3060, currentMP=maxMP2806, allowance2244.8, FORECAST_SHORTFALL. MP не выдан/не увеличен; прежнего самостоятельного WAIT_GROUP_MP нет. Это реальный native admission, **не исполненный бой и не обучение**. Natural recovery этой руки134450.8376ms; ускорение самого восстановления не заявляется.

DB-free behavioral RED исходного veto → GREEN общего helper; настоящий Creature gate отклоняет cast2807 при2806MP и допускает cast100. Обычный допустимый member, HP-risk и invalid-profile отказы, equality/nonfinite, оба callers и lifecycle проверены. Финальный Java focused suite PASS: Party14/14, support9/9,011–015/catalog. JAR PASS; content7/7 PASS; PowerShell015/014/Headless013 PASS. Подробности — PROGRESS.md и REVIEW_RESULT015.md.

## Почему полного TRAIN нет

| Сеанс | Исход | Wall с startup/cleanup | Полные A/B cell preparations | GS/CUT/FIT |
|---|---|---:|---:|---:|
| S1 / cap480 | TIMEOUT на work deadline |274269ms|1 + один Party arm|0/0/0|
| S2 / cap720 | CANCELLED кооперативно |507396ms|3 /6arms|0/0/0|

Всего2/2 сеанса,781665/1200000ms. Unused time не возвращает третий слот. Оба worker/SQL exits proven; final own0/OFF/portsfree. Независимый EVAL не запускался.

S2 имеет полные matched/fresh A/B подготовки Solo94, Solo110, Party103, но **нет cells.tsv/training-admission013 seal**. Планировщик продолжал следующий Party STOCK и был остановлен после обнаружения review gap, способного вызвать запрещённый independent probe после успешного TRAIN admission. Probe не стартовал; обе Important находки исправлены offline с RED→GREEN.

Ошибка подготовки root: унаследован episodesPerBatch84 вместо PIPELINE51. Canonical cohortCellLimit84/17=4; planner пытался заполнить четвёртую cell до публикации seal, хотя обязательный minimum3 представлен сырыми парами. Два завершённых Party arms S2 потребовали134426.1752 и134426.2705ms; последующий прерван на94801.1266ms. Конфигурация проведённых сеансов не переписана под51. Финальный entry guard отвергает этот setup до SQL. Новый native сеанс после исправления не запускался.

Не доказана невозможность обучения класса или победы. Диагноз — незавершённый bounded planning/seal при ошибке batch-конфигурации и обнаруженной wrapper readiness integration. Ручное превращение CANCELLED/сырых arms в READY/G3, изменение configSha задним числом и продление времени не выполнялись. Без полного G3 GS запрещён; без настоящего TRAIN нет достаточных данных для FIT. Новых optimizer/model rows0.

## Сохранность и ограничения

Cold postflight:183строки пяти SELECT, SHA `9A9C2D0EE9A2B8F37DA5DD6BCF625334B76BE96FE9B76E22490ED20B4DA70E7D`, equalBefore=true. TEST2/DRAFT14/Adam903updates223epochs/apps48/grad16 и pointer12 сохранены; selected14 и terminal Adam раздельны, их bytes не менялись. Geo203/protected207 PASS. Приватные SQL/credentials/полные inventories не публикуются.

Minor review limitation: per-member selected loadout/skill levels и support-ready decomposition этого forecast не записаны полностью. Точный размер завышения3060/оптимальная ротация UNKNOWN; существующие inventory/learner proofs не объявляются их заменой. Future обязательные Soulshots/Blessed20+, экономика без бесплатной выдачи и адаптация после трёх сопоставимых смертей/поиск Party/поддержки остаются незакрытыми требованиями, вне реализации015.

Семантика приёмки после owner approval — ACCEPTANCE_AMENDMENT015.md. Финальные Gate результаты — ACCEPTANCE_RESULT.md. Git-команды — COMMANDS.md. Публикация и независимый GitHub readback выполняются после фиксации этого отчёта; точный конечный HEAD и remote hashes сохраняются отдельным внешним verification receipt и сообщаются владельцу, без самоссылочного commit SHA внутри его собственного содержимого.
