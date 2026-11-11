# Native Task 3 — передача реализации

Реализован source-контур; native SUCCESS ещё не заявлен. Сборки, SQL и GameServer
запускает основной исполнитель последовательно. Этот исполнитель не использовал Git.

## Файлы и команды

- `PhantomTrainingScenarioChecks.java`: два focused контракта seed/coverage/пустой очереди.
  RED до реализации: основной исполнитель получил отсутствующий `PhantomTrainingScenarios`.
- `PhantomTrainingScenarios.java`: каталог из реальных World/spawn/topology/progression
  и заданных selectors; hash-порядок независим от порядка входных ключей;
  durable ordinal вращает очередь, разные NPC распределяются перед вариантами.
- `PhantomTrainingActors.java`: CLI `<installation> <configPath>`, рабочая папка
  `<installation>/game`. Только stock Player/SkillTree/Inventory setup до CUT;
  два обучаемых актёра одного настоящего actor-hash split на сцену и до трёх
  штатных tank/DPS/heal helpers, ограниченных `maxActiveActors`.
- `PhantomTrainingNative.java`: CLI `<installation> <attemptId> <jarSha> <configPath>`.
  Один GameServer/source authority/Runtime на batch, несколько новых эпизодов.
- В 001 ActorFixture выделен `createTraining`; старый `create` делегирует с прежними
  классами/навыками/экипировкой. В 001 ShadowNative открыта package-private Runtime
  и добавлены отдельные `prepareTraining/runTrainingEpisode/settleTraining`.
  Старые накопительные `solo/party/finished` для 002 не используются.

Manifest: `catalogs/<generation>-<uuid>/training-scenes.tsv`, `training-actors.tsv`,
`prepared-actors.tsv`, `training-manifest.sha256` и `journal.properties`.
`active-catalog.txt` публикуется атомарно только после SEALED. Generation равен
числу persisted SQL training attempts. Следующий training attempt получает реальные
новые Player/profile IDs; повторный actor CLI для того же generation/selectors читает
seal, не сбрасывает HP/EXP/SP/gear/награды. Незавершённый собственный cohort сохраняется
как INCOMPLETE; новая подготовка начинает allocation после фактического MAX(profile_id).
Подробности и focused crash/seal проверки: `CATALOG_PROTOCOL.md`.
Незапечатанный initial pending journal без успевшей записаться owner-строки
сохраняется побайтово вместе с SHA в quarantine и переводится только в INCOMPLETE.
Исходный pending не переносится и не удаляется; complete foreign owner остаётся отказом.

## Граница эпизода

1. Materialize и штатный возврат пешком; сверить persisted farm root.
2. Все materialized участники проходят один общий bounded await естественного
   восстановления до 90% HP; без HP/MP/stat setters и отдельных потоков. Party:
   настоящие приглашения, exact stock target preflight, canonical release
   при закрытом `installLogicalFarmAdmission`.
3. `TrainingStore.begin`, затем `collector.begin`; admission открыт только для ACTIVE.
4. Solo SHADOW использует существующий ordinary opening/45 секунд; ON сохраняет
   cold opening/12-секундный forecast. Оба проверяют exact source.
   Party работает через штатные autonomous/coordinator pulses.
5. Квота новых exact collector observations, deadline, pause/stop либо native отказ.
6. Закрыть новые pulls, дождаться Combat terminal consume и Party drain, STORE,
   проверить отсутствие собственных Player в World; только затем `collector.end`.
7. Boundary SQL readback exact event keys; `TrainingStore.finish` проверяет их сам.
   Неуспешная/пустая/погибшая сцена получает INCOMPLETE через abort; ordinal потреблён.

Отказ canonical drain завершает batch ошибкой: следующая сцена поверх retained owner
не запускается. Policy/model устанавливаются один раз; смена модели только cold batch.
`batch.complete` содержит фактически проверенное число COMMITTED и причину выхода;
ноль feasible сцен — bounded NONE_FEASIBLE, а не синтетический успех.

## Конфигурация и известные ограничения

Текущий операторский preset: `preparationSeconds=420`, `batchTimeoutSeconds=7200`.
Java fallback подготовки остаётся 180 секунд для старых конфигов. Все пять участников
начинают штатный rest в одном pulse; STOP/PAUSE проверяется во время ожидания.
Helpers без Prominence используют required MP=0 только в этой подготовке, а штатные
Party MP/Heal/role/Geo/risk gates остаются обязательными. До/после подготовки журнал
показывает фактические current/max HP и MP. INCOMPLETE сохраняет after facts через
bounded overload abort; это не даёт native credit.

Seed, modes, quota/budgets, длина batch, model version/SHA и endpoints читаются при
новом холодном batch без Java rebuild. Seal включает только actor/scenario selectors.
Изменение classes/levels/grades/NPC/scenarioLimit/maxActiveActors создаёт новый
pre-CUT snapshot с новыми актёрами; старый пул не переписывается. Seed меняет порядок
существующего каталога, а catalog seed при новом generation влияет на выбор реальных
вариантов. Episode seed отдельно вычисляется из config seed и durable ordinal,
сохраняется перед началом и не считается доказательством новой audit когорты.
Physical Scene.id и его split остаются постоянными; новые actor IDs дают свежую когорту.
Лимиты: maxTrainingCohorts=32, maxStoredActors=4096, maxStoredProfiles=4096 по умолчанию.
Исчерпание останавливает подготовку с TRAIN_CAPACITY, сохраняя старые данные.

Каталог подтверждает два offensive actions для Archmage94; остальные классы
проверяются фактическим resolver и могут получить INSUFFICIENT_ATTACK_CAPABILITIES.
Подготовка экипировки V1 подтверждена только для A/S; прочие принимаемые config grades
получают UNSUPPORTED_STOCK_LOADOUT_GRADE, а не ложную метку поверх A-оружия.
Party 2..9 сохраняет native gates; V1 использует до пяти Player одновременно,
состав с меньшим cap может законно не пройти stock readiness.

21798 и 21799 берутся из действующих spawn данных, без обязательного hardcode;
21799 не считается доказанной сценой до настоящего target/Geo/Party gate.
Каталог ограничен 4096 combinations и шестью точками на template/class/level.
Для pre-CUT planner проверяется до `min(256, 4 * scenarioLimit)` кандидатов;
подготовка останавливается после `scenarioLimit` принятых сцен.
NPC reward/HP/формулы, Combat/Party safety и Geo не изменялись.

Rich before/after содержит фактические vitals/EXP/SP/адену/позиции/epoch; точная
атрибуция награды и spoil остаётся UNKNOWN. Offered actions и per-cast факты
поступают из штатного opt-in TrainingCollector, а не восстанавливаются по общему урону.

## Непроверенное

Нужны compile-tests, focused GREEN и regression 001; свежий native proof Solo/Party,
>=3 combinations, >=2 templates где допустимо, STOP/PAUSE/restart и SQL readback.
Реальное время startup/preparation/encounter и latency собирает основной контур.
Любые найденные compiler/runtime причины следует исправлять точечно без bypass gates.
