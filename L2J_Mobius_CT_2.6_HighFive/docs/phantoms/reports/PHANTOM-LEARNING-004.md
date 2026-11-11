# PHANTOM-LEARNING-004 — общее ядро обучения DAMAGE + HEAL

Статус: **CORE_VERIFIED — доставка в новую WIP required**. G1–G8 подтверждены ниже; окончательный FULL004_CORE фиксируется после scoped push и сверки GitHub HEAD. Это квалификация обучающего ядра, а не доказательство превосходства новой политики в боях.

Работа ведётся в независимом clone, в `wip/phantom-learning-004`, от exact base `06d42410fbfe1ca9b7f462b0816e0da1784630d6`. Пользовательский checkout не изменялся. Старые V1 DAMAGE и accepted V3 model2 сохранены. MAIN/PLAY, старые SQL, release-среды, Semantic Pack и защищённые refs не публикуются.

Combat и Party остаются владельцами действий. Одна immutable V3 оценивает готовые законные DAMAGE либо HEAL альтернативы внутри текущего владельца. Общего арбитража «атаковать или лечить» здесь нет. Runtime не вызывает SQL, обучение, LLM, сеть или GPU. После native drain сохраняются фактические эффекты и отдельные исходы эпизодов.

Реальные DAMAGE поступают из существующего LearningCastEvidence: конкретный cast/skill/target/spawn, точный HP writer, оплаченный MP и длительность. DAMAGE label обязан совпадать с before−after HP. HEAL сохраняет прежний v4 codec; новый DAMAGE использует явный v5 envelope. Старые V1-векторы не переименованы в V3. Неподтверждённый/отменённый/нулевой эффект сохраняется отдельно с `reward=null`, без превращения в обучающий ноль.

Adam сохраняет веса, m/v, число обновлений, seed и глобальную эпоху. Самые удачные TUNING-веса и терминальные веса с optimizer намеренно разделены: к ранним выбранным весам не приписываются поздние моменты. Первый переход от003 честно обозначен MOMENTS_INITIALIZED; последующие используют RESTORED. Rejected DRAFT продолжает рабочую обучающую историю; допущенная модель меняется только отдельным exact apply-test.

Replay ограничен4096 эффектами и отдельным bounded failure window; исторический native ledger не обрезается до4096. Стратификация включает family/class/level-band/Solo-Party/split/spot/outcome. В frozen run атомарно фиксируются точные keys/SHA, replay context и settlement hashes. AUDIT не участвует в fit и не используется повторно. Нет тихой очистки моделей/опыта; configured disk/profile/event caps останавливают сбор с причиной.

## Проверенные факты на текущем этапе

Первый полный сегмент `aa21693750fc4557b91f525576c54e73` собрал56 HEAL и обработал6 реальных PvE-попыток: **5 COMPLETED и1 INCOMPLETE**,0 pre-CUT SKIP. INCOMPLETE не назван победой. Общий V3 scorer вызывался для DAMAGE и HEAL в одном GameServer. Более ранний partial-сегмент дал ещё3 Solo+2 Party подтверждённых завершения, но полным обучающим сегментом не считается.

Пример реального Solo: эпизод `f81e363eed9d994dd732c2fb87763c4352e6f486d79d8aaa453e32ab970f3232`, NPC21799,34.505с, selected exact DAMAGE4632.5, оплаченный MP417, native credited EXP78396/SP13970. Остаток HP3494.588841/MP2139.123792. Это сумма выбранных точно наблюдавшихся действий, а не полный DPS боя.

После последнего drain фиксируются реальные участники, HP/MP/alive/dead, native kill/EXP/SP. Число kill receipts у членов Party не выдаётся за число убитых NPC: нужны distinct target/spawn/instance в пространстве native attempt. Полные смерти, whole-party damage, consumables, loot и adena остаются UNKNOWN, если независимого writer нет. Терминальная победа пока не используется как train reward.

| Checkpoint | Selected weights SHA | Optimizer SHA | Updates / global epochs | Родитель |
|---|---|---|---|---|
|4, DRAFT|9519891660ec938cfc5d23abcabf897df459121840b3b491e03e2fce3bf30083|cf8773d330f85508ddc1623fa892c86bff5c506e04cdd6ae6ce6b7a4964eaa7e|114 /19|accepted2, MOMENTS_INITIALIZED|
|5, DRAFT|0e4dbb2b0907e4d96d2b5ea6cd27ac36e4332140e87673e6fa08f7622f078218|d5b223717e66d32cbbd72734d6a91033f696aacad9fa168d2b55902dd8ac5bec|300 /50|checkpoint4, RESTORED|

Checkpoint5 использует exact parent optimizer `cf877...`, а его parent weights `92d32fd05ba5d8d9f1d87dbd73cb9a0415f667e2f820f9fbbe23a349922ba9e6` — терминальные веса optimizer4. Они отличаются от selected candidate4 по описанному выше контракту.

| Audit model5 | Candidate MSE | TRAIN constant | Accepted prior2 | Решение |
|---|---:|---:|---:|---|
|Все выбранные audit rows|0.002451665|0.006043838|0.020371131|Общий выигрыш не заменяет локальные gates|
|HEAL105 /Party|0.002746707|0.004402833|0.002496060|REGRESSION против prior|
|HEAL97 /Party|0.003094146|0.002030812|0.003255665|REGRESSION против constant|
|DAMAGE94 /Solo|0.001334917|0.003350602|0.049027524|INSUFFICIENT_EVIDENCE:1 audit actor/context|
|DAMAGE94 /Party|0.003168561|0.001670088|0.045707706|INSUFFICIENT_EVIDENCE:1 audit row/actor/context|

Поэтому model5 осталась DRAFT; accepted2 не заменена. Это реальный пример защиты от красивого общего MSE при локальной регрессии. Доказательств улучшения побед/выживаемости относительно stock нет: сбор идёт SHADOW, реальные исходы известны только для выбранных действий. Counterfactual outcomes остаются UNKNOWN.

На основании фактического одного V3 Party эффекта за аудит-бой source V3 profile увеличен до12 native эпизодов: TRAIN+TUNING и4 independent AUDIT-пары. Пороги8TRAIN/4TUNING/4AUDIT,>=2audit actors/contexts,1% общего улучшения и отсутствие per-group деградации не снижались. Существующий bounded episode budget соблюдается; весь каталог16/8/4 не объявляется завершённым за один сегмент. Native admission refusal до CUT записывается как SKIP без награды/credit; SQL/source/drain дефекты остаются fatal.

## Производительность и ограничения измерения

В полном native-сегменте:125 policy invocations,69 настоящих neural evaluations; total decision p50=1.343мс,p95=1.932мс,p99=2.410мс,max=2.962мс, overBudget0. Snapshot p95=.617мс, encoder1.418мс, action scorer.033мс. Allocations p95=108408B/call. HEAL owner/native dispatch p95=8.716мс измерен отдельно и не называется inference. CPU timer отдельных вызовов имеет Windows granularity15.625мс; нулевой median не означает бесплатный CPU.

Измерены snapshot, проверка legal candidates внутри policy, encoder/scorer и owner dispatch. Это не обещание128 полноценных native ticks. Итоговые128logical4/8/16 benchmark, throughput и PROCESS CPU/RAM приведены ниже. В runtime одна модель4741580bytes (1185377 trainable parameters); optimizer14224664bytes хранится offline.

## Совместимость, сбои и процесс

V1 weights SHA `e0691f728847cebea7293d87e45acebbd3db5bc2d71b76c7c5e40549e13c43d2`, original ZIP `10fc9c6c8c450e4974ca7abed861805a7640e2c67c8474ac5a4c96e003b493da`. Accepted V3 weights SHA `6b91706463f4e24f89f1c4f8be3a82d9ee58eadb07a4254c79449665e3fa97dc`, ZIP `04393f2cbc7ba8c4760e42c6f32ae93edd4b8bf848e50d25e97ee8b4348716b5`. Проверены bytes/hash и пустой exact-path diff этих архивов.

Исходный004 ZIP SHA `5be6f923cdef9ed7fcd5ebdbe80a13a5153044ab3dab8a324e2ac6995a8404a1`; все16 manifest-listed payload hashes совпали после сохранения структуры. Входные документы не переписываются ради результатов.

Конкретные исправленные дефекты: initialization canonical installation guard; stale pre-drain settlement; cold optimizer/candidate/ancestry validation; positive-only observations; неверный DAMAGE delta;14.2MB Adam packet escaping; игнорируемый episode budget; whole-batch retry после lawful pre-CUT refusal. Packet defect исправлен binary prepared protocol только в V3 connections pinned Connector/J9.5.0: actual14224664byte roundtrip при прежних16MiB PASS, temporary-table rollback/nativeCredit0. Серверные packet limits и V1 connections не менялись.

Реальный плохой случай `ce2b...` сохранён: Party WAIT_GROUP_DAMAGE, target0, durationInfinity. Правила игры не подгонялись. Первые watcher-попытки не дали controlled proof и честно записаны в PROGRESS. После исправления test-only вызова штатного initializer проверка99509 действительно прервала собственный offline worker; доказательство и cold resume приведены ниже. Win32/Job/G0 launcher не переписаны.

Изоляция, source read-first, bounded scope exception, review rulings, команды/exit codes и причины повторов записаны в task `PLAN.md`, `PROGRESS.md`, `COMMANDS.md`. Независимый reviewer нашёл4 Important; исправления и focused RED→GREEN зафиксированы. Final actual SQL faults/cold export/STOP выполнены, результаты ниже. Точный перечень файлов — SCOPE_ALLOWLIST.txt; проверенный GitHub HEAD — отдельная delivery receipt после scoped push, как в003.

Файлы evidence в task004 содержат настоящие native START/SETTLE/observations и отдельные rejected/partial cases, не credentials, datadir, Geo или private process configs. Текущие архивы: native-a-fixtures74entries/SHA42d43b290662bb22f28e650fd7345b39effc7c868f0332c0efdf16c760ff1009; native-b-fixtures80entries/SHA1453d30fb886dbf0ef22709203d948ec31fbb2658d0bc3a7cb6dd9e7f43af2ab.

PvP1×1/1×2/N×M, raids/sieges/Olympiad, массовый фарм, neural navigation и Semantic Pack не реализованы этой задачей. Enum/schema hooks не считаются такими возможностями. Улучшение MSE не выдаётся за боевое мастерство или изменение live политики.

## Финальная native-сессия и восстановление

Второй полный сегмент `ea24f28dd07546afa0a64705caa4363a` после отдельного STOP/RESUME:56 HEAL,20 DAMAGE,12 попыток —9 COMPLETED,2 INCOMPLETE/TIMEOUT,1 pre-CUT SKIP,0 FAILED. Это11 начатых эпизодов и9 подтверждённых убийств NPC, а не12 побед. Каталог28 пар целиком не исчерпан; пороги не снижались. Архив99 raw fixtures SHA `31166d5324d2e70422f0e66d93feeb7517c826247c232c233858eb91d2bbbfac`.

Реальный Solo `e8c57ea6cf6b28ff8a77f86d51019ca96234729a6b2d2cb538e3ff4707dfd39c`: Archmage94 level80, NPC21799,23.204с, selected DAMAGE7060, MP279, EXP78396/SP13970, remainingHP3734.263336/MP2440.231536. Первая точная запись `97ec53eacf684df896d6bfc9e7223e51919e148556c945dc9e94ab27648aaf72`: skill1230:28, offered2, HP12892→11819, DAMAGE1073, MP69; следующая1339:1 тоже offered2. Party `c80c1fc267fbf51b9a928a8a78bc3e60cc828470fa9085e8c2aedc5064dedcda` использовала4 штатных участника и те же2 законные offensive alternatives; точный event `bdb1a664ee9a1c9782ac0081dfc12aa6353b98d0935f85834fcd859b50593dae`: HP12892→11998, DAMAGE894, MP69. Все START/SETTLE и остальные event keys сохранены в native-c.json/txt/fixtures.

Найденный native NPE level80 на learned ground-channel skill1419 устранён минимальным guard в Creature по существующему Player-паттерну: без currentSkillWorldPosition навык unavailable. Последний GameServer подтвердил это на нескольких настоящих level80 actors, inputUnchanged=true. Formulas, cooldowns, Party admission и игровые параметры не менялись.

После native exit watcher удерживал handle собственного TrainingEvaluator PID69064 и проверил PID/creation/image/config/nonce, отсутствие GameServer и FROZEN run `a4ec5a930b3d4983bdf0c5da50954dd3`. Процесс завершён с exit−1; Director ожидаемо зафиксировал worker failure и очистил только свои процессы. Новый Director generation11/PID13844 восстановил **тот же run и dataset**, worker64724 exit0. Native ledger до/после509; двойного AUDIT или игрового credit нет.

| Рабочий checkpoint | Optimizer SHA | Updates / global epochs | Parent |
|---|---|---|---|
|6|2d60e60ff993dc64fcf5c0cb285d6bc83766fbe6240acf8c8c490885a8b2b541|414 /69|5|
|7|76cbf6c0ac6ef823ffe223542fc4c8fde0ed7ddf78678fca00cd701621edaddc|594 /99|6|
|8, DRAFT|56cc26c43a2b2edfba23bb2a6e56a207dd10ca7243af7796611a10d7b1f58c0f|769 /124|7, RESTORED|

Checkpoint8 selected weights SHA `ab62799a566c4cf52807978a12bea023be864d58915b3a72beaf7934d6a96016`; dataset `c8e4e783bde807f1c0acba9c50130609ef3e35cad088133febec64e6edc63c8e`; context replay SHA `ab1f753a71a8d04bb1b324111b4b7cd0c98fa11bc4488354950ae9bc48fdba2a`. Replay188 effects +2 nontrainable failure samples;97TRAIN/70TUNING/21AUDIT. Historical ledger452HEAL+57DAMAGE=509; старые120 строк accepted2 импортированы отдельно без нового native credit. Durable episode ledger26:23 completed,3 incomplete,0 dangling START; pre-CUT SKIP не превращены в эпизоды.

| Audit checkpoint8 | Candidate MSE | TRAIN constant | Accepted2 MSE | Итог |
|---|---:|---:|---:|---|
|Всего21 строка|0.008536508|0.011838194|0.073674821|Общий выигрыш27.89%/88.41% недостаточен для apply|
|DAMAGE94 Party,14/4/4|0.001660095|0.001286745|0.079797844|REGRESSION против constant;2 actors/contexts|
|DAMAGE94 Solo,19/2/9|0.001158585|0.000463926|0.083894205|INSUFFICIENT_EVIDENCE:2TUNING вместо4;3audit contexts|
|HEAL105 Party,32/32/4|0.024675950|0.027788599|0.060145655|NO_REGRESSION;4audit contexts|
|HEAL97 Party,32/32/4|0.015873804|0.016556230|0.058087350|NO_REGRESSION;4audit contexts|

DRAFT8 сохранён и продолжим, accepted2 неизменна; никаких утверждений об обученном Solo или превосходстве побед над stock. G7 прямо разрешает отвергнутый DRAFT. Оба полных сегмента SHADOW: counterfactual stock outcomes UNKNOWN; сравнение prior/constant проведено на тех же frozen chosen-action receipts. Source и quantized state fingerprints приведены для каждой группы в checkpoint-8.json.

## Финальная производительность и проверки

Native C:139 policy calls,83 neural evaluations; decision p50=1.389мс,p95=1.818мс,p99=2.885мс,max=2.946мс, overBudget0. Snapshot p95=.542мс, encode1.316мс, score.036мс; allocation p95=116016B. HEAL native dispatch отдельно p95=5.807мс,p99=12.948мс — это выполнение owner, не inference. Native includes snapshot/legal policy extraction; полный мировой scan128 actors не заявлен. Штатные bounded fallback/CPU_SLOT/cooldown остаются.

128 logical actors ×4/8/16 actions,512 samples на вариант после2с warmup: p95=1.058/1.055/1.098мс; p99=1.129/1.134/1.178мс; wall508.3/502.9/518.9мс, threadCPU515.625/500/515.625мс, allocations11186176B на512 решений. Это сохранённые native observations с повторением action vectors; не128 одновременно играющих ботов. G1 young pause3.446мс записана отдельно в performance-gc.txt, не скрыта внутри обещания максимальной задержки.

GameServer C:925754мс от старта до exit,166.78125 CPUс, peak working set4684681216B (~4.36GiB) включая мир.76 committed effects =>295.54 effects/h;9 completed =>35.00 encounters/h,12 attempts =>46.66/h;2/12 timeout=16.67%,1/12 SKIP=8.33%. Это конкретная короткая сессия с boot/drain, не прогноз на ночь. B:679711мс,163.796875CPUс, peak4689707008B,67effects/5completed.

Отдельная paused SQL:4.359375CPUс, current236535808B, peak280162304B. Последний реальный fit wall9.514с. CPU/RAM завершившегося native-fit worker отдельно не снимались; вместо выдуманного числа проведён отдельный **MATH_ONLY** optimizer profile существующим runner: wall8.643с, sampledCPU≥9с (включает JVM/GC threads), sampledpeak1296052224B при100мс sampling, exit0. Это не native-fit memory claim. Director paused18.031CPUс/current129863680B. Все области измерения различены в JSON.

Canonical compile-tests и jar финального source exit0,5 прежних warnings. Финальный source fingerprint `e1ef851e25bb6ce92aad345b17512b766fa2aa96ff7cb4a2d709b7217320f949`; JAR SHA `9b0a388414d51f1e0c1f73bffecaab5fc40c5f4d65272b27aacf4f7d95cd8606`. Финальные143 affected assertions (4+72+38+14+6+9), F8 14/14 и F7 PASS; focused004 model/replay/damage/episode/failure/schema exit0. F7 SQL_REPLAY=NOT_RUN, это DB-free contract. Полный DB-зависимый ant verify не запускался по scope.

Actual own SQL faults: forged parent, deleted latest optimizer, orphan optimizer, missing context replay, missing raw native effect — все REJECTED; rollback PASS, commits0, checkpoint/native count неизменны. Actual retry первого настоящего receipt не добавил строку. Cold export побайтно восстановил optimizer, selected weights, champion weights, effect/context replay и failure blobs. ZIP `artifacts/working-checkpoint-8/checkpoint.zip` SHA `f689100ec7e20aebcefb91648c9b5109c8fb82c67fc0d5b30343923639bdc798`.

Final STOP exit0: STOPPED generation11, own ports33337/17981/19281 свободны, история сохранена. Geo203/protected207 проверены штатными exact hash guards при STOP; старые SQL/Main/Play не запускались. Автоматическая проверка ранее отклонила предложение временного профиля1/0/0 с абсолютным assetSourceRoot как ослабление требований/жёсткую привязку; оно не применялось. Работа продолжена на полноценном bounded профиле и относительном guard-поиске ассетов.

G1 COMPAT, G2 UNIFIED, G3 INCREMENTAL, G4 AUTO, G5 CURRICULUM, G6 EPISODE, G7 QUALITY, G8 PERF подтверждены приведёнными evidence. G0/G9 завершаются exact scope guard, encoding checks и scoped GitHub delivery; квитанция отдельно фиксирует итоговый HEAD без циклического самохеша.

Финальный scope:114 exact paths, включая36 source/config/test files; прочее — входной пакет004, отчёты, проверяемые evidence и архивы. Outside allowlist0, missing0. Source/TEST overlay100entries SHA `3be7dbfa88e33aee80f09d81b214d8fa72c24b814bf0349956b5a2ce9a49273b`, каждый entry прочитан обратно и SHA совпал. Полная сводка изменений по всем путям — SCOPE_ALLOWLIST.txt; private runtime не включён.

Исходный trailing space входного ARCHITECTURE.md:3 сохранён по byte-exact manifest. Full cached diff-check обнаружил его exit2; для остальных113 exact paths применяется обычный cached check. Это единственное whitespace исключение, не изменение входного пакета.

mojibake-маркеры в изменённых файлах проверены;

escaped Cyrillic в изменённых файлах проверены.
