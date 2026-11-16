# PHANTOM-LEARNING-020 — BLOCKED

**BLOCKED020_NO_LAWFUL_SCENE. Manual gate: REQUIRED. FULL020 не достигнут.** Единственный GameServer boot завершён; оба заранее зафиксированных Solo-кандидата отказали до CUT на `LEARNING001_AUTONOMOUS_NATURAL_RECOVERY_DEADLINE`. Новых paid actions, V4 samples и updates нет. Goal021 не начат.

## Фактический результат

SOURCE004 fast-forward с `9000245681e34712a9146dcd53c25af1d6ad59c1` до проверенного `75567cfe7c6e1c5a215045208c388902b9e6b3cd`. Независимый GitHub connector подтвердил этот baseline; две параллельные Studio additions сохранены. MAIN только прочитан, historical TEST006 overlay не заменялся.

Preflight020 за **307 мс**, GS0/SQL0: проверены sealed016 manifest и физические bindings, post-drain ledger завершённых Solo-эпизодов, сохранённые roster/source/inventory. Выбраны ровно два кандидата:

|Класс|NPC|Frozen shortlist|Источник|
|---|---|---|---|
|94|20966|1230:28, 1275:14|Существующий `encounter.local` из sealed016|
|110|20965|1148:13, 1234:28, 1239:28, 1267:14|Другой существующий `encounter.local` из sealed016|

Новый split `TRAIN` закреплён до boot и результата. Исторические события не переименовывались и не импортировались как новые. Это сохранённая согласованность, **не live admission**.

TEST020 — отдельная физическая копия game/SQL, loopback **33360/18020/19320**. Настоящий GS загрузился за24с. Оба сохранённых актёра дошли до `prepareAcquired` и естественного восстановления; оно не достигло штатной готовности за40с на кандидата. Первый Runtime корректно закрыт перед вторым. `PhantomEncounterLocalSource.realize`, создание нового NPC, skill eligibility, native encounter admission, CUT и V4 choice **не достигнуты**. Поэтому фактически `TEST_FIXTURE_SPAWN=false`, а не успех по одному созданию сцены.

|Факт|Значение020|
|---|---|
|MODEL_CONSULTED / MODEL_REORDERED|false / false|
|NATIVE_EXECUTED / PAID_EFFECT|false / false|
|stock/chosen, scores, paid HP/MP|NOT_OBSERVED|
|новые event/episode SHA|отсутствуют|
|NEW_TRAIN_SAMPLES|0|
|UPDATES_BEFORE / AFTER|161 / 161|
|NEW_CHECKPOINT / COLD_REPLAY020|NOT_CREATED / NOT_RUN|
|FALLBACK|production unchanged, default OFF; stock safety regression PASS|
|TRAINER_STATUS|BLOCKED_NOT_OPERATOR_READY; G6 не реализован по gate|

## Причина и ошибка подготовки

В019 `actionValue019Solo` искал ближайшего моба через `coldSoloOpeningTargets` около anchor и получил пустой набор до Combat/CUT/V4. Новый TEST020 использует канонический acquired-путь016: `prepareAcquired`, `PhantomEncounterLocalSource`, `acquiredEncounterAdmission`, `runLearning006Encounter`, `settleLearning004`. Production Combat/Party correctness не изменялась. Но реальный запуск не добрался до target admission, поэтому устранение прежнего отказа живым evidence **не доказано**.

Существенная собственная ошибка адаптера: конфиг работающего016 содержит `preparationSeconds=180`, тогда как020 выбрал40с, сохранив уже использованных актёров из persisted roster. В016 `collectEpisode` воспроизводит acquired source через `reproduceRoster`;020 эту часть не перенял. Проверка source/inventory не доказывает готовность текущих HP/MP. Сокращённый ресурсный budget не был обнаружен preflight до boot. Два отказа подтверждают незавершённую штатную подготовку; точное разделение HP/MP/movement-дефицита не сохранено отдельным live snapshot и остаётся **NOT_CAPTURED**. Нельзя утверждать, что одно увеличение40→180 гарантировало бы бой.

Второй GS, пополнение ресурсов, выдача навыков, ослабление readiness и новый campaign не выполнялись. Новые fit/cold методы остаются **NOT_RUN_WITH_FRESH_NATIVE020_INPUT**; готовность всего end-to-end пути не заявляется. Публичные `RunOnce/Status/Stop` по условию G1–G5 не добавлены.

Максимум два варианта для решения владельца: (1) узко переиспользовать исходное016 воспроизведение sealed acquired source с exact physical/inventory checks; (2) сохранить persisted actors, но заранее согласовать подтверждённый ресурсный preflight и реалистичный bounded recovery budget одного кандидата. Оба требуют отдельного разрешения на следующий native запуск; сейчас ничего не запускается и успех не обещается.

## G0–G8 и сохранность

|Gate|Результат|
|---|---|
|G0|PASS для bounded source/physical preservation; fresh logical SQL183 NOT_RUN.|
|G1|FAIL: preflight сохранённых данных PASS, fresh lawful target/resource admission не достигнут.|
|G2|PASS_STATIC_AND_019_REGRESSION: подлинный161-update checkpoint, существующий TEST hook и default OFF сохранены.|
|G3|NOT_REACHED: V4 consult/execution/paid0.|
|G4|NOT_REACHED: original нового settled episode/sample нет.|
|G5|NOT_RUN: новый increment/cold/replay020 запрещено подменять019.|
|G6|BLOCKED_BY_G1_G5; operator-ready single step не поставлен.|
|G7|PASS: GS1, native boot/run/shutdown + SQL cleanup127510мс; OFF/own0/listeners free.|
|G8|Scoped publication и независимый GitHub readback выполняются после этого отчёта; итоговые SHA/receipt — в финальном сообщении. Manual REQUIRED.|

**38 473 historical файла** до/после без drift. Включены autonomous/outcome016–019, исходная SQL data, исходный JAR, manifests, config и весь physical train019. Отдельно штатный `Assert-TrainAssets` до/после проверил **203 Geo / 207 protected** физических inputs. Исходные V3/Adam/TEST2/DRAFT14/pointer12 сохранены этими pins; runtime не выполнял FIT/EVAL/promotion и migration. Исторический SQL183 SHA `9A9C2D0EE9A2B8F37DA5DD6BCF625334B76BE96FE9B76E22490ED20B4DA70E7D`; новый five-SELECT183 не выполнялся, исходная SQL не запускалась. Это preservation files, а не fresh SQL PASS.

Checkpoint019 физически сохранён: **161 updates**, SHA `569295cf4d8f4172eee0e8a7e530246534dd1cf24d34ab95e07ded65c6b2f061`. Regression019 в новом отдельном output повторно принял247 исторических actions/27episodes:161TRAIN,86heldout, восстановил тот же SHA; отдельная JVM — cold exact/replay0. Старый candidate не реконструировался взамен физической копии и не перезаписывался. Изменение019 score при stock=chosen1275 остаётся историческим фактом; нового score/choice change или преимущества020 нет.

## Проверки, review и время

- `ant -f .../build.xml compile-tests`: exit0,28с; `jar`: exit0,18с, только SOURCE004.
- `Test-Training.ps1 -ActionValue020`: RED на принятие proof без settlement → GREEN; затем RED на пропуск SQL cleanup после ошибки → GREEN.
- `PhantomLearning020V4Native contracts <cohort>`: RED raw tamper accepted; после raw fix RED duplicate boot accepted; затем GREEN. Использованы подлинные сохранённые016 rows только для проверки валидатора, новые samples из них не создавались.
- `ant -f .../build.xml '-Dphantom.learning020.cohort=<physical-cohort>' phantom-learning020-test`: exit0,31с; отдельный fork timeout120с, GS0/SQL0. Проверены raw action/owner/effect binding, mandatory pins, atomic one-use ingress и STOP до boot.
- `PhantomLearning019ActionValue offline .../regression019` + `cold`: exit0,2.813с весь shell; offline991мс. `Test-Training.ps1 -ActionValue019`: exit0.
- Native wrapper: exit1, потому что положительного `native020.json` нет. Process exit0 от shutdown не засчитан успехом. Причинные immutable `refused0/1.json` сохранены; сам GS завершился штатно. Safety receipt: GS1/127510мс,38473pins/drift0,OFF/own0/portsFree.
- Один read-only `jcmd <owned-pid> Thread.print`:0.538с, показал штатное ожидание natural recovery. Новых probes/stress/boot после отказа нет.
- Первое выполнение private-root helper в PowerShell7 отказало на .NET ACL overload до GS/SQL; штатный Windows PowerShell5.1 прошёл. Одна Ant-команда без кавычек вокруг `-D...` остановилась до тестов; исправленная команда выше прошла.
- Два Important safety-направления review: independent cleanup/retained ownership и authenticity/one-use ingress. Исправлены за один bounded review pass с RED→GREEN, reviewer не нашёл оставшихся Critical/Important. После исправлений **native не повторялся**, они подтверждены offline/static, а не новым live proof.
- Пять прежних compiler warnings: ExtendedOpenOption и System.runFinalization в unrelated tests. Полные suites/51/72 campaigns не запускались.

Измеренный полный native wrapper от создания родительского процесса до safety receipt — **213021мс**, включая physical pins/copies/cleanup. Сумма проверок консервативно **≤6мин**; самый длинный offline gate31с, каждый offline<120с, native<8мин, лимит всех проверок12мин не превышен. Старт работы **20:05:39 UTC 16.11.2026** (22:05:39 Europe/Chisinau); фактический wall до публикации и GitHub readback сообщается в финальном ответе, без будущего времени в доказательствах.

## Scope и публикация

Изменены только новый `PhantomLearning020V4Native.java`, три небольших additions в существующие `TrainingOwner.ps1`/`Test-Training.ps1`/`build.xml`, этот отчёт, одна новая строка `COMBAT-AI-ROADMAP.md` и разрешённые документы пакета020. Production Java/Combat/Party/TrainingConfig, schema, game data и старые016–019 документы не менялись. `Training.ps1` не менялся: G6 закрыт. Native adapter и fresh fit/cold — частичная, не operator-ready поставка.

Прочитаны root/module AGENTS, README, workflow/master-plan/task standard, полный пакет020, отчёт/evidence019 и ближайшие аналоги Runtime016, runner019, scene/source, ActionFeedback/Value/LearningV4, TacticalPolicyV3, owner/build routes. Отдельные code-map/pattern-файлы для этого bounded scope не найдены. MAIN/TEST dirt сохранён; сырые SQL/owner nonces/model weights/private native dumps/JAR в Git не включаются.

Git использован по прямому разрешению020 и PRESERVATION.md: `rev-parse HEAD`, `rev-parse HEAD origin/feature/phantom-world`, `symbolic-ref --short HEAD`, `status --short`, `worktree list`, `merge-base --is-ancestor`, `fetch origin feature/phantom-world`, initial `diff --stat HEAD origin/feature/phantom-world`, `merge --ff-only origin/feature/phantom-world`; exact-path `diff`/`diff --numstat`/`diff --stat`/`diff --check`; exact-path `add`, `commit`, ordinary `push origin HEAD:feature/phantom-world` и финальный `ls-tree` для GitHub blob readback. Команды публикации и финальные SHA сообщаются после реального выполнения. Без force/reset/clean/stash/rebase/new branch/new worktree. Первый sandbox fetch отказал на FETCH_HEAD; разрешённая эскалация выполнила штатный fetch. Mixed EOL build.xml восстановлен по GitHub baseline blob, diff ровно7 added/0 removed.

- mojibake-маркеры в изменённых файлах проверены: полный набор27, совпадений0;
- escaped Cyrillic в изменённых файлах проверены: все6 Unicode/XML regex, совпадений0.

Ночь/8h scheduler, PvP, HEAL/control/debuff/танки/Party expansion и рост эффективности не входят в выполненное. FULL020 и manual acceptance не объявляются.
