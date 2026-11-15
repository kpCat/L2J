# TASK013 — PHANTOM-LEARNING-013-AUTONOMOUS-LEARNING-LOOP

## 1. ID, цель, бюджет

Goal №7/13. Получить полноценную **автономную** цепочку в собственном TEST: действительно собранные законные боевые исходы → сохранённые выбранные оплаченные действия → однократное продолжение Adam → независимая **V2** оценка с победами/смертями → атомарная фиксация checkpoint → выбор `NEXT` или объяснимая автоматическая пауза. Оператор нажимает START/PAUSE/STOP/RESUME, а не вызывает Codex между фазами.

Не объявлять игрового превосходства, если его нет. `FULL013` относится к работоспособности автономного конвейера, не к силе модели, абсолютному максимуму навыка или готовности 24/7. `G0–G8` обязательны.

**Затраты разработки:** максимум 4 новых GameServer starts, максимум 80 минут суммарного native wall time (включая неудачи, boot, drain, held process exit). До native выполнить все дешёвые проверки. Собственный накопительный бюджет013; исторические009 (10/5803240мс),010 (4/715447мс),011 (1/328635мс),012 (1/284206мс) не сбрасывать и не исправлять. Никакого автоматического расширения бюджета.

## 2. Среда и зависимости

- Git root: `D:\Codex\repos\L2J-phantom-world` — основной checkout с пользовательскими изменениями, **не переписывать**.
- Существующий безопасный source checkout: `D:\Codex\repos\L2J-learning-004` (проверить, что он на feature и безопасен).
- Изолированный TEST с историческими приватными SQL и Adam: `D:\Codex\repos\L2J-learning-006`.
- Модуль: `L2J_Mobius_CT_2.6_HighFive`; JDK25, Ant, MariaDB TEST, Geo203/protected207. Не трогать чужой SQL, releases057–061, master, MAIN/PLAY.
- Прочитать root `AGENTS.md`, module `Agents.md`, `docs/phantoms/CODEX_WORKFLOW_CONTRACT.md`, `COMBAT-AI-ROADMAP.md`, `RESULT012/R1_RESULT`, `RESULT009` в узком объёме.

## 3. Факты, которые нельзя игнорировать

- 009 оставила DRAFT14: **Adam903 updates/223 epochs**, 48 applications/16 gradient episodes; selected epoch0; AUDIT не подтвердил улучшение; прежний completed-cycle pointer12. `selected weights` и `terminal optimizer weights/moments` различаются.
- 012 `FULL012_OUTCOME_AWARE_EVAL` технически имеет 4 independent terminal-comparable pairs (2 Solo +2 Party), включая Solo death/death, и реальный GameServer. Game verdict **INCONCLUSIVE**, один paid Solo divergence, Party 0. Ручная приёмка012 может оставаться `REQUIRED`; не объявлять её пройденной без ответа владельца.
- Ранее `Training.ps1` считал `improved` по `tuningMse < tuningInitialMse`, `stagnation` после трёх fits, хотя независимая игровая V2 EVAL ещё не входила в решение о лучшей модели.
- 012 V2 acceptance smoke был `EvaluationOnly`, не равен полноценному Director COLLECT/FIT/EVAL. Нельзя назвать уже пройденную EVAL012 частью новой13.

## 4. Привязанные интеграционные точки

Читать и переиспользовать, прежде чем создавать что-либо новое:
- `java/org/l2jmobius/gameserver/phantoms/learning/{TacticalTrainerV3,TacticalStoreV3,TacticalModelV3,ActionAdvantageV2,AutonomousTrainingV1,TrainingConfig,TrainingEvaluator}.java`;
- `test/java/org/l2jmobius/tests/phantoms/{PhantomAutonomousTrainingNative,PhantomEvaluationOutcomeV2,PhantomEvaluationCurriculumV2,PhantomEvaluationReplayV2}.java`;
- `tools/phantom-runtime/training/{Training.ps1,TrainingOwner.ps1,Test-Training.ps1,AutonomousTraining.ini,TrainingGui.cs,TrainingGui.ps1}`;
- `build.xml`, focused tests предыдущих выпусков.

Не создавать второй обучающий Director, отдельную БД, новый GameServer owner или собственный Combat/Party engine. Реальный native Combat и legality остаются источником истины. При несовместимости V2 `EvaluationOnly` и рабочего TRAIN внедрить узкий версионированный **дочерний evaluation-only run/pin** с независимым состоянием и разрешённым mode, не ослабляя guard из012.

## 5. Конкретные изменения

1. **Протокол цикла.** Versioned, persisted `cycle-id` с source/JAR/Geo/config/feature/model/Adam hashes; фазы с отдельными entry/exit receipts, полностью повторяемая `STOP/RESUME` логика. Различать готовность к обучению, запуск обучения, actual optimizer updates, выбранную эпоху, V2 evaluation и завершённость checkpoint.
2. **Данные.** Новые cohort формируются из реально acquired персонажей и native scenes 011/012 без списков class/skill ID; TRAIN, TUNING, AUDIT и V2 EVAL изолированы. `TRAIN` exploration остаётся только opt-in TRAIN. Не включать EVAL012 или исторические holdouts в градиенты. Соблюдать минимум реальных controls, baseline и diversity; не снижать пороги `ActionAdvantageV2.sufficient()`.
3. **Fit.** Продолжать только exact cold `optimizationParent` с Adam moments. Сохранять selected DRAFT и terminal Adam раздельно; если `selected epoch=0` — это **нет доказанного улучшения**. FIT, metric, inputs и applications атомарны и exactly-once. Не подделывать reward/MP/HP/exp/kill.
4. **V2 evaluation.** Встроить в циклический Director именно терминальную независимую EVAL V2 (Solo+Party, lower+higher при наличии законного набора, сбалансированные AB/BA), а не использовать V1 `both must win`. V2 считывает native исходы, включая deaths; EVAL не обучает и не тратит credit. Если независимое покрытие отсутствует — PAUSED `EVAL_COVERAGE_GAP`, не `NO_IMPROVEMENT`.
5. **Качество.** Не считать уменьшение TRAIN loss или TUNING loss достаточным для принятия модели. Сопоставлять с неизменённой best TEST и stock при достаточной независимой V2 evidence; недостаточное n/неизвестные метрики честно давать `INCONCLUSIVE`. Никогда не auto promote в MAIN/PLAY или acceptedTEST2.
6. **Автоостановка.** Точные правила в AUTO_STOP.md: после трёх **достаточных и завершённых** новых fit+eval циклов без доказанного прогресса — `PAUSED_NO_MEASURABLE_PROGRESS`, не «достигнута оптимальность». `NO_DATA`, `INVALID_EVAL`, авария и resource cap — отдельные причины. Не включать их в patience.
7. **Оператор.** CLI/GUI START, STATUS, PAUSE, STOP, RESUME, EXPORT управляют одним имеющимся Director. STATUS: текущая фаза, лучший проверенный TEST, рабочий DRAFT, parent Adam, epochs/updates, законченные V2 пары/незаконченные, trainingCredit, причина паузы, время, starts, native/CPU/RAM/disk. GUI не должен быть жизненно необходимым процессом для тренинга. Не создавать новые UI frameworks.
8. **Независимость от Codex.** После запуска Director сам проходит fit→eval→checkpoint и выбирает NEXT/PAUSE; закрытие Codex/GUI не должно менять владельца native. Продемонстрировать работу и холодное восстановление в ограниченном тесте.

## 6. Source scope

Предлагаемый максимальный source allowlist из 15 путей; изменять только доказанно нужные (план с точной подвыборкой до кодирования):

- `java/org/l2jmobius/gameserver/phantoms/learning/TacticalTrainerV3.java`
- `java/org/l2jmobius/gameserver/phantoms/learning/TacticalStoreV3.java`
- `java/org/l2jmobius/gameserver/phantoms/learning/TrainingConfig.java`
- `java/org/l2jmobius/gameserver/phantoms/learning/AutonomousTrainingV1.java`
- `test/java/org/l2jmobius/tests/phantoms/PhantomAutonomousTrainingNative.java`
- `test/java/org/l2jmobius/tests/phantoms/PhantomEvaluationOutcomeV2.java`
- `test/java/org/l2jmobius/tests/phantoms/PhantomEvaluationCurriculumV2.java`
- `test/java/org/l2jmobius/gameserver/phantoms/learning/AutonomousTrainingChecks.java`
- `test/java/org/l2jmobius/tests/phantoms/PhantomEvaluationOutcomeV2Checks.java`
- `tools/phantom-runtime/training/Training.ps1`
- `tools/phantom-runtime/training/TrainingOwner.ps1`
- `tools/phantom-runtime/training/Test-Training.ps1`
- `tools/phantom-runtime/training/AutonomousTraining.ini`
- `tools/phantom-runtime/training/TrainingGui.cs`
- `build.xml`

Новые небольшие TEST-only tests или один versioned protocol file допустимы после записи exact allowlist и reason в утверждённом PLAN; не создавать массовые новые Java layers. `TacticalModelV3.java`, `ActionAdvantageV2.java`, `TerminalRewardReceiptV1.java`, игровые Combat/Party/Player/SkillData/Spawn/Geo, reward/config/model schema — **read-only**. Если для исправления действительно необходима смена objective/schema или production owner, остановиться и согласовать архитектуру с владельцем. Документы в `docs/phantoms/tasks/PHANTOM-LEARNING-013-AUTONOMOUS-LEARNING-LOOP` и один общий report + roadmap исключены из source-count.

## 7. Не входит

Нет PvP, рейдов, командного обучаемого Team Coordinator, нового RL, долгого >80 native-minute прогона, искусственных побед, self-play, динамического изменения уровня/класса/скилла ради FIT, нового Keras/Python/LM Studio сервера, появления V3 в MAIN/PLAY или автоматического релизного включения. Не брать новые классы по очереди вручную.

## 8. Конфиги, ресурсы, производительность

Конфигурация versioned, defaults OFF. Сохранять текущие 5ms decision budget, margin .025, cooldown2000ms, owner/risk/MP/Geo/LOS/party safety. Никаких SQL/XML reads, обучения или большой переборной логики в горячем боевом тике. Bounded memory/CPU/disk, capped cohorts/retention, native starts ledger. Дешёвые offline тесты до первого GS, один GS обслуживает несколько эпизодов. Ночной soak не нужен для FULL013 и не заявляется выполненным.

## 9. Lifecycle/DB/transactions

Единственный Director installation-owner, один GameServer owned PID; process identity+held handle until exact exit, STOPPED/OFF/ports free; pause after atomic boundary; recovery interrupted `AFTER_COMMIT_BEFORE_JOURNAL` without duplicate gradient/credits. `checkpoint pointer` после полного V2 EVAL/checkpoint, не вручную во время ошибки. Все исторические SQL/cold blobs/pointers untouched. Read-only guard SQL while EVAL. 012 native death comparator untouched; historical V1 codecs immutable.

## 10. Исполнение

Следовать PLAN.md; сначала red/green на статусах, replay и stop, затем ограниченная реальная проверка. Не начинать следующий Goal автоматически. При блокере: честный PARTIAL с точным gate, никакой многочасовой перезапуск по кругу.

## 11. Git и отчёт

Разрешаю limited exact scoped commits и обычный **non-force push непосредственно `feature/phantom-world`**. До работы свежий `git fetch`, `status`, ветки/worktrees, сохранность грязного основного checkout; без WIP, reset --hard/clean/force/squash/rebase и массовых stash. По завершении независимый GitHub HEAD/parents/diff + полные source/evidence readback, включая проверку кириллицы. Итоговые RESULT.md, EVIDENCE.json, COMMANDS.md, ACCEPTANCE.md, HANDOFF.md, общий русский report, изменение счётчика roadmap.

## 12. Acceptance

G0–G8 в ACCEPTANCE.md. `FULL013_AUTONOMOUS_LOOP` только при техническом подтверждении цикла и сохранности, без лживого `ADVANTAGE_SUPPORTED`. Отсутствие реального native FIT/V2 EVAL — PARTIAL, независимо от зелёных unit или GUI. Ручное принятие принадлежит владельцу.
