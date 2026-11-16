# Goal016 — PARTIAL016_NATIVE_COLLECTED_DIRECTOR_CLOSE_FAILED

Продолжение достигло настоящего GameServer TRAIN, но **FULL016 не заявляется: финальное закрытие director завершилось fail-closed**. Новая frozen generation `autonomous009-learning016-g2-v1` явно ссылается на исходную неудачную кампанию. Её старые 59 файлов, 10 файлов director, session1 issued/result, исходные SHA и расходы сохранены. Историческая первая попытка ниже остаётся PARTIAL. Final manual gate **REQUIRED**; независимая доставка — в READBACK.json.

После сохранения `checkpoint016.json` с `NO_FIT_INSUFFICIENT_VALID_DATA` код присвоил фазу `DONE016`. Общий `Save-TrainCycleState013` принимает `^[A-Z_]{1,32}$` и отверг цифры: `TRAIN013_STATE_VERSION_OR_PHASE`. Сохранённый director остался в `FIT`, `fits=0`, `completedCycles=0`, `pending=false`. Checkpoint и все 51 замыкание settled/raw сохранены. Повторного FIT/RESUME, новой generation или исправления runtime после freeze не было. Успешные offline suites не поймали этот финальный переход. Разрешённое продолжение закрывается PARTIAL, без нового цикла исправлений.

G3: ровно три новые полностью подтверждённые pre-CUT A/B-пары, три разные сцены, классы 94/103/110, Solo+Party, по 2/3/4 поддерживаемых навыка. Шесть свежих рук дали seal и расписание 51 = 33 TRAIN + 9 TUNING + 9 AUDIT. Четвёртая сцена и headless session3 не запускались. Сохранённая пара не разрешает будущий бой: перед реальными эпизодами создавались новые объекты и выполнялись действующие проверки owner, навыков, целей, MP, Geo/LOS, Party и ресурсов.

Native: **2 GS / 71,1598 native-минуты**, 51 reserved, 51 settled, 51 after-CUT и 399 оплаченных эффектов с 399 immutable native event files. Распределение TRAIN/TUNING/AUDIT: 33/9/9 эпизодов и 265/61/73 оплаченных эффекта.

Исходы: 29 `NATIVE_KILL`, 16 `NATIVE_ACTOR_DIED`, 5 `NO_LEGAL_TARGET:NO_SAFE_SOLO_OPENING`, 1 `REQUESTED_DRAIN`. Подтверждены 29 уникальных убийств NPC; 46 actor kill credits в Party не считаются 46 убийствами. Нижняя граница смертей — 16; полное число смертей и whole-party metrics неизвестны. Точно наблюдавшиеся расходы выбранных акторов: **24 820 MP**, урон — 226 664,5. Расход сосок, consumables, loot и экономика остаются UNKNOWN там, где writer не дал точного наблюдения.

Сохранены 27 terminal reward receipts: TRAIN/TUNING/AUDIT = 17/6/4. Все 17 Party-эпизодов завершились в игре, но допустимых Party terminal reward receipts нет; frozen writer отклонял actions с `IllegalArgumentException`. Всего 84 excluded terminal actions. Игровые исходы, расходы и ошибки сохранены; reward и правила допуска не менялись. Подготовка не засчитана в настоящий бой.

Незавершённых reservations — 0/51. Исходов вне COMPLETED/FAILED — 6/51 (11,7647%). Отсутствие reward receipt не переименовывает подтверждённый `NATIVE_KILL` в UNKNOWN. Всё расписание закрыто marker-ами settled; это не означает 51 успешный или пригодный для обучения terminal.

Train evaluator вызван один раз; optimizer FIT — **0**, `candidateVersion=0`, `NO_FIT_INSUFFICIENT_VALID_DATA`. Штатный отчёт не подтвердил достаточность новых независимых TRAIN controls, paid actions и held-out данных; отдельный не пройденный порог он не выделяет. Cold selected DRAFT14, terminal Adam14 и accepted TEST2 неизменны: **903 updates / 223 epochs**, новых applications/gradients/terminal credits — 0. Все 183 исторические five-SELECT строки совпадают побайтово.

|Объект|Сохранённый SHA|
|---|---|
|Selected DRAFT14|`7d3c8fc1f2ae5fc199cfb7517f71743189fdd3fbb4b959a318753637e2d3bc37`|
|Terminal Adam14 weights|`f830a1cc4ead23f36dd6153fd1d818bbb0ae3b830d9e666f1988a4c82f05e9bf`|
|Optimizer14|`6a34b7ee503f35dff75e61ba59892be823f72e45402e1a20f4036b9740ade844`|
|Accepted TEST2|`6b91706463f4e24f89f1c4f8be3a82d9ee58eadb07a4254c79449665e3fa97dc`|

|Gate|Фактический результат продолжения|
|---|---|
|G0|PASS: freshSOURCE, bytebackups/overlay, coldfiveSELECT и postflight183historical rows exact, pointer12/TEST2/DRAFT14/Adam903/223/Geo203/protected207|
|G1|PASS:51в Java/PowerShell, fixedg2 opt-in,3cells покрывают3classes/Solo+Party; legacy regression PASS|
|G2|PASS: linkedgenesis, exact counters/cursors/refusals/pending order, oldattempt/credit0; crash replay/tamper/atomic pair/seal RED→GREEN|
|G3|PASS LIVE:3законные новые matched pairs,6arms,marker-last manifest/files/cells/admission SHA verified|
|G4|PARTIAL: livePause/Stop/Resume и11settledexact/no redispatch PASS, но finalDONE016commit FAIL. Native/SQL/Worker heldexit0; Director identity absent, heldexit receipt отсутствует, exitcode не заявляется|
|G5|PASS LIVE: ненулевые настоящие TRAIN CUT/paid effects и settlement kill/death; negative/paused исходы не заменены|
|G6|PASS: FIT=0; reason=NO_FIT_INSUFFICIENT_VALID_DATA; historical183exact; lawful newSQLrows=0; EVAL0/promotion0, выбранная и terminal lineage отдельно в EVIDENCE|
|G7|PASS: headless2/3, исходная session1не сброшена; общий бюджет30мин сохранён, GS2/3 и 71.1598/75мин; targetedofflinegatesPASS|
|G8|PENDING: обычная scoped публикация и independent GitHub whole-body readback выполняются после формирования этого evidence|

STOP/RESUME: snapshot9settled доPause,11послеStop; все11marker/rawclosures побайтово неизменны послеGS2, dispatch возобновился соslot11. GS1 heldexit0 списал489573ms; общий native ledger не сбрасывался. Резервированный неоднозначный эпизод не переигрывался. В новом journal первый GENESIS импортировал sequence/visits/cursors/sceneCounts/rosterVisits/refusalCounts/refusalSamples/pendingIndex и порядок двух pendingrows; incomplete attempt и completed pairs/credits не переносились. Sequence1→5, TARGET_BOUND_ALTERNATIVES1672→5057, FULL_RECOVERY_HAS_NO_WOUNDED_HEAL_RECIPIENT1→1.

Архитектура TRAIN016_INCREMENTAL_PRECUT_V1 сохранена, изменения только6из7разрешённых исходников; build.xml в продолжении неизменён. Новый config ID фиксирован, generation3 запрещена. Совместимость включает все configkeys кроме campaign/dataset/lifecycle и exactordered sources+physical/inventory/owner/model/Adam/Geo. DescriptorSHA привязан к contract.parentLinkSha на append/replay/seal/read, coherent descriptor+counter tamper отвергается. Исторические015не перепечатаны в TRAIN.

Независимый review продолжения:1P2descriptorpin gap, исправлен одним RED→GREEN pass; повторныйreview не проводился. Все7targetedAnt и7PowerShell gategroupsPASS; compiler/fixture/orchestration исправления не объявлялись behavioralRED. Послеfreeze runtimeисходники/JAR/pins не менялись. Параллельные Studio commits сохранены; MAIN/PLAY и чужой dirt не использовались для реализации.

**TRAINED_MODEL=NO · GAME_ADVANTAGE=NOT_EVALUATED · PRODUCTION=OFF.** Это реальный native сбор с допустимыми отрицательными исходами, без optimizer FIT и без доказанного улучшения модели. FULL016 запрещён из-за G4 даже после доставки G8. Независимая EVAL/probe и promotion не выполнялись. Shots20+/обязательныеBlessed/покупки, память трёх смертей/адаптация, роли/баффы, PvP/raid/team/server bootstrapV3 остаются отдельными незакрытыми требованиями. Goal015 остаётся принятым PARTIAL. №10/13, осталось3до обязательного пересмотра; следующийGoal не начат.

- mojibake-маркеры в изменённых файлах проверены (полный набор маркеров);
- escaped Cyrillic в изменённых файлах проверены отдельной проверкой, включая XML.

Источники: EVIDENCE.json (priorAttempt плюс generation2), NEGATIVE_TESTS.md, PROFILING.md, COMMANDS.md, READBACK.json. Сырые игровые/SQL/owner receipts остаются в приватном внешнем evidence; публично агрегаты и SHA.

---

Исторический отчёт первой попытки016, опубликованный в1c8f15ed; его факты ниже сохранены. Продолжение выше разрешено владельцем отдельно.

# Goal016 — PARTIAL016_UNSEALED

**G3 и настоящий TRAIN не достигнуты.** Реализован накопительный016 journal/seal/director path в exact7, но первая живая headless-подготовка завершилась fail-closed. FULL016 не заявляется. Final manual gate **REQUIRED**.

Первая сессия создала две native pre-CUT Solo руки с совпадающим physical SHA и разными свежими объектами. Новая проверка ошибочно сравнила NPC spawnGeneration как глобальный epoch: у двух разных новых NPC значение1. Отказ `TRAIN016_PRECUT_MATCH_OR_FRESHNESS` оставил0подтверждённых пар,2pending candidates, sequence1 и5atomic transitions. Две руки не являются действующим разрешением на бой, не являются TRAIN и не получают credit.

Причина исправлена в опубликованном исходнике по штатной семантике `TacticalSnapshotV3`; точный отказ воспроизведён отдельно для A/B helper и journal, затем GREEN. Повторный objectId и actor owner epoch по-прежнему отвергаются. Исправленный вариант проверен **offline**, в frozen TEST не установлен: замена build/source pins потребовала бы отдельно согласованного нового идентификатора подготовки. Существующие59файлов не перепечатаны, внешняя приватная копия сверена по SHA. Сессии2/3 не выдавались.

|Gate|Фактический результат|
|---|---|
|G0|PASS: Source актуализирован, TEST overlay защищён byte backups; cold пять SELECT183строки; TEST2/DRAFT14/Adam903/223/apps48/grad16/pointer12 сохранены|
|G1|PASS offline и config:51; три distinct cells/≥2classes/Solo+Party завершают выбор; legacy режимы сохранены|
|G2|PASS focused contracts: atomic pair+selection, cold replay, interrupted scan, partial seal, tamper/reparse, reservation/conditional FIT; реальные completed pairs0|
|G3|FAIL:0из3подтверждённых пар, seal отсутствует|
|G4|NOT_RUN: GS запрещён безG3; no-EVAL/director/budget recovery проверены DB-free|
|G5|NOT_RUN/FAIL:0настоящих TRAIN CUT, paid effects и терминалов|
|G6|PRESERVED / NO_FIT_NO_LEGAL_DATA: FIT0, новые gradient/terminal credits0; холодная lineage неизменна|
|G7|PASS с ограничением измерений: headless1/3,72,430s issued /172,590s весь Prepare; GS0/3,0native-min; targeted checks PASS, CPU/RAM только последний status sample, не peak|
|G8|Публикация и независимый GitHub readback отражены в READBACK.json; manual REQUIRED|

Source прошёл два проверенных fast-forward до `591c5261ed5`; параллельные Studio commits сохранены. MAIN не использовался для реализации; его чужой dirt не менялся. Exact7 code allowlist, отдельные task outputs/report/новый roadmap section. Git-команды использовались с прямого разрешения владельца; точные формы и цели — [COMMANDS.md](COMMANDS.md).

Изменения: явный opt-in016 и общие Java/PowerShell config checks51; immutable hash-chain journal с pair commit до возврата следующему кандидату; восстановление последней подтверждённой selection boundary; marker-last seal с closure validation; свежий native recheck и exactly-once reservation; отдельные cumulative budgets и один conditional FIT, завершение без EVAL/promotion. Незавершённый read-only scan повторяется от последней committed boundary; его временные счётчики не выдаются за committed history, cost telemetry остаётся сохранённой. Это ограничение транзакционной границы, а не доказательство полного live STOP/RESUME016.

Независимый review:0Critical/3Important/1Minor; все четыре исправлены с RED/GREEN, foreign-root finding повышен доImportant по task contract. Второй review не проводился. Поздний реальный epoch-дефект выявил пробел synthetic fixtures; он исправлен focused regression, но **не получил живого подтверждения**. Нет утверждения, что новая ветка прошла весь live recovery маршрут.

**TRAINED_MODEL=NO · GAME_ADVANTAGE=NOT_EVALUATED · PRODUCTION=OFF.** Новая EVAL/probe, promotion и следующий Goal не выполнялись. Goal015 остаётся принятым владельцем PARTIAL; его история не изменена. Shots20+/Blessed/покупки, память трёх смертей/адаптация, роли/баффы, PvP/raid/team и server bootstrap V3 остаются отдельными незакрытыми требованиями. Задача№10/13, осталось3до обязательного пересмотра бюджета; PARTIAL не сбрасывает счётчик.

- mojibake-маркеры в изменённых файлах проверены;
- escaped Cyrillic в изменённых файлах проверены.

Факты и SHA — [EVIDENCE.json](EVIDENCE.json), проверки — [NEGATIVE_TESTS.md](NEGATIVE_TESTS.md), стоимость — [PROFILING.md](PROFILING.md). Продолжение требует отдельного согласования frozen generation/перехода, описанного в [IMPLEMENTATION_PLAN016.md](IMPLEMENTATION_PLAN016.md); repin/reset автоматически не выполняется.
