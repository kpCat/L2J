# Протокол подготовленных пар / Resume / Seal

## Версия и привязки

Новый additive TEST-only протокол `TRAIN016_INCREMENTAL_PRECUT_V1`, отдельная собственная namespace/path под текущую кампанию. Не переписывать V1/012/013/014/015 manifest, codecs и SQL. Список готовых пар хранить в **private TEST**, публичные документы содержат только агрегаты/хеши без секретов и полных SQL/actor snapshots.

При записи замораживать: campaign/nonce/goal, SQL actor snapshot SHA, classIndex/classId/level, точные **acquired** skill IDs+levels/enchant/ability inventory SHA, equipment/shots/active effect facts, Party roster source SHAs, scene identity+source NPC/target settings, target/planning location, valid ownership & native constraints, configSha, JAR SHA, model SHA, terminal parent Adam SHA, Geo+protected manifest identity, selection seed/cursor/visit counters и protocol version. Сохранять только реально доступные факты, UNKNOWN не превращать в PASS.

## Записываемые состояния

- `PROPOSED`: кандидат вычислен; никакой G3 credit.
- `STOCK_READY_PRECUT`: есть неизменяемая первая рука, но пара ещё не доказана.
- `PAIRED_PRECUT`: обе независимые свежие руки прошли native check, их physical hashes/skills/target equivalence совпали по действующему протоколу, objectId/epoch различны. Не training credit.
- `STALE_OR_REFUSED`: подтверждённая причина, неизменяемый отрицательный receipt, возвращаться к подбору с честным учётом попытки.
- `COHORT_SEALED`: ровно достаточный, проверенный набор законных разных `PAIRED_PRECUT`, ≥3 cells, ≥2 classes, Solo+Party; записанные `cells.tsv`, `training-admission013.json` и новый 016 manifest/complete marker взаимно совпадают по хешам.
- `NATIVE_RESERVED` / `NATIVE_SETTLED`: только после G3, в отдельном runtime-эпизоде; резерв записан атомарно до CUT. Не перезапускать уже зарезервированный episode при неясном исходе.

Выбирать согласованную существующую Atomic/immutable запись: tmp → flush (если штатно доступен) → safe atomic rename / immutable create. **Последний маркер готовности появляется только после валидации всех referenced files**; при частичной записи `INCOMPLETE`, не green. Не использовать «создали каталог» как доказательство успешного commit.

## Resume без двойных действий

1. Получить exclusive existing training owner lock и durable campaign identity, проверить process/SQL/ports и предыдущие held exits.
2. Проверить config/schema=016 и `episodesPerBatch=51` **до SQL**. Любое `84`/EVAL-only/некорректный campaign/повтор использования 015 artifacts → fail-closed.
3. Перечитать по хешам все complete `PAIRED_PRECUT` этой кампании. Если были только STOCK arm, сохранять её как исторический факт; повторное live admission для LEARNED не может ссылаться на живой объект из старого сеанса. Для сопоставимой пары в таком случае создавать **новую** независимую пару или законный холодный воспроизводимый snapshot, а старый receipt не превращать в complete.
4. Восстановить `visits`, shortlist cursor, source/scene/refusal counts, чтобы решения после resume шли по той же честной последовательности, что непрерывный запуск. Если восстановить нельзя — STOP с конкретной причиной, не начинать подбор снова и не накручивать счётчик.
5. Проверить stale source/config/JAR/Geo/ability/model. При дрейфе не использовать старый candidate; новые данные оформляются отдельным идентификатором и потребуют нового допуска. Не редактировать старые доказательства.
6. На каждом pre-CUT candidate при поиске требуется fresh owner/World/target/Geo/LOS/MP/current cast/Party/source/regen. При выполнении реального TRAIN — новый live recheck того же набора условий.
7. После завершения расписания не пересчитывать TRAIN/TUNING/AUDIT по результатам. Нет повторного credit, дублей nonce/slot, повторного dispatch уже зарезервированного эпизода.

## Инварианты для двух запусков подготовки

- Задать фиксированный seed и class/roster candidate order; проверить «прерванный + возобновлённый» эквивалентен непрерванному на synthetic determinism fixture, включая отказы, курсор, классы, номера slots, physical/source hashes.
- Контролируемое падение **после** первой и второй рук, **до** маркера пары, **после** маркера пары, **до** финального seal, **после** seal — в каждом случае точная законная cold реакция без fabricated PASS, duplicate credit и потери исторических файлов.
- Изменение хотя бы одного SHA, owner nonce или чужого файла, symlink/reparse, порчи пары/manifest → STOP, а не автомиграция/восстановление из догадок.
- Старые 015 raw6arms можно импортировать только как **отдельный read-only regression sample**, ни в новый completed counter, ни в новую campaign готовность.
