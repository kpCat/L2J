# PHANTOM-LEARNING-002 — решения после read-only аудита

## Основание и границы

Запрос владельца прямо разрешает самостоятельную реализацию и изолированный TEST.
База: `b85dd2a3a5f6f153c77b02f1e4887c490eb08ea8`; независимый clone,
ветка `wip/phantom-learning-002`. Исходный пользовательский checkout read-only.
Три защищённых GitHub HEAD: feature `faec72161f9e4f6231764e17d6f2637a4db080af`,
coordination130 `c5b81f89ce02030eff8441395b0411137a110b2f`, learning001 — базовый SHA.
Приоритет текущего запроса над историческими абсолютными путями/обычной веткой публикации.

Прочитаны весь пакет 002, корневой AGENTS, модульный Agents, README,
CODEX_WORKFLOW_CONTRACT, TASK_PACKAGE_STANDARD, PHANTOM_CURRENT_STATUS,
релевантные архитектурные разделы master plan, актуальные HANDOFF/отчёт 001,
build.xml, LearningPolicy/Store/Features/CastEvidence/NeuralPolicy,
нативные fixture/actor/offline entrypoints и G0 launcher. Аналоги — существующие
Combat/Party owners, SQL batch writer, pre-CUT Player setup, PID/FILETIME/SID guards.
Вышестоящие AGENTS не найдены; отдельные актуальные pattern-файлы не найдены.
Карта исходников предоставлена в SOURCE_MAP.md; исторический Party code-map не является
источником новых API.

## Подход

1. Отдельный PowerShell supervisor владеет своими SQL/GameServer/worker процессами.
   START отделяет его от консоли; команды используют durable state и точную identity.
   START не должен создавать новую копию при живом или неподтверждённом старом owner.
2. Реальные эпизоды выполняет отдельный Java entrypoint на существующих
   Materialization, Progression, CombatService, Navigation и stock autonomous Party.
   Никакой альтернативной механики боя. Один bootstrap обслуживает несколько эпизодов.
3. Versioned config задаёт seed, покрытия, классы/уровни/экипировку, NPC, лимиты,
   размеры батча, обучение и retention. Генератор читает реальные игровые данные;
   неподдерживаемые классы и сцены получают явную причину SKIP.
4. SQL ledger хранит run/generation, PREPARED/ACTIVE/SETTLING/COMMITTED/INCOMPLETE,
   точные immutable event keys и структурные наблюдения с UNKNOWN masks.
   Только SQL-подтверждённые события COMMITTED эпизодов допускаются в обучение.
5. Dataset фиксирует состав train/tuning/audit до обучения. Audit не используется
   для выбора эпохи или повторного подбора seed. Каждая попытка сохраняется, включая
   отклонённый DRAFT. Принятие требует независимых per-scope метрик против постоянного
   TRAIN-прогноза и предыдущего ACCEPTED; исторические веса сохраняются.
6. Модель принимается только для собственной TEST-инсталляции. Изменение версии
   применяется на холодной границе; рабочая игра не получает автоматический import.

Другие рассмотренные пути: простой повтор 001 отвергнут, потому что его накопительные
квоты не определяют новые эпизоды; новый игровой симулятор отвергнут, потому что
нарушает требование native source of truth. Поэтому выбран штатный игровой runner
с отдельным долговременным supervisor и ledger.

## Подтверждённые ограничения

- Java25/Ant, JDK-only тесты; зависимости, игровой баланс и протокол неизменны.
- LearningFeatures V1, residual NeuralPolicy algorithm2 и точная HP-атрибуция сохраняются.
- Existing LearningStore требует loopback порт 33000..65535, account learning001 и
  schema phantom_learning001_*. Собственная schema будет в этом namespace;
  это не разрешение записи в историческую базу 001.
- Каталог сейчас подтверждает два атакующих действия для Archmage94. Остальные
  классы не получают придуманных возможностей; разнообразие V1 доказывается также
  реальными NPC/позициями/уровнями/экипировкой. Причины отказов видны в отчёте.
- Никакого потока на актёра, SQL/обучения в Combat tick, bypass Geo/Party/safety,
  синтетического NATIVE credit, production promotion или изменения TASK130 status.
- CPU/RAM/disk ограничены конфигом; logs, DATA и модели не стираются при STOP.

## Bounded exception и allowlist

Это одна инфраструктурная задача Scenario→Episode→Evidence→Dataset→Evaluation,
а не независимые изменения подсистем. Исключение к 8–10 файлам ограничено:

- неизменёнными 13 файлами входного пакета 002;
- docs/phantoms/tasks/PHANTOM-LEARNING-002/{DESIGN_DECISIONS,PLAN,PROGRESS,RESULT,HANDOFF,IMPLEMENTATION_SUPERVISOR}.md;
- docs/phantoms/reports/PHANTOM-LEARNING-002.md;
- tools/phantom-runtime/training/* (config, owner scripts, семь cmd wrappers, smoke);
- java/org/l2jmobius/gameserver/phantoms/learning/Training*.java;
- LearningPolicy.java, LearningCastEvidence.java, LearningStore.java и точечным
  observation seam PhantomCombatService.java только при необходимости;
- test/java/org/l2jmobius/tests/phantoms/PhantomTraining*.java;
- точечными reuse seams PhantomLearning001ActorFixture.java и
  PhantomLearning001ShadowNative.java;
- dist/db_installer/sql/game/phantom_training.sql;
- build.xml и PhantomTestLauncher.java только для focused маршрута тестов.

До финального staging allowlist уточняется до exact paths по фактическим изменениям.
Geo, SQL datadir, credentials, большие бинарники и исходные runtime logs остаются
в собственном исключённом .phantom-local, не в Git. Никакие исторические артефакты
не переписываются.

## Непроверенное на момент решения

Native diversity, unattended parent-exit, SQL recovery, обучение/качество новых
данных, cold ON, full native latency и чистый STOP требуют реальных свежих проверок.
Geo203/protected207 проверены bounded read-only с доступом к известному cycle19:
203 exact physical файла, 1 063 452 308 байт, ноль SHA mismatch; 207 protected
hashes также совпали. Ordinary sandbox AccessDenied был ограничением доступа,
а не отсутствием assets. Свободные порты 33327/17971/19271 подтверждены TCP inventory;
перед каждым фактическим запуском проверка повторяется.

## Реализация supervisor

- Root связан с фактическим clone: `.phantom-local/learning002/train`. Конфиг source
  и snapshot root разделены; обновление только между остановленными native batches.
- `director.json` принадлежит supervisor; `status.json`, `evaluation.json`, `report.md`
  принадлежат evaluator. Последний outcome DRAFT не стирает prior active checkpoint:
  modelVersion/modelSha в evaluation всегда указывают проверенную ACCEPTED модель.
- Один initialization mutex закрывает гонку свежего root и команд; lifetime exclusive
  file lock закрывает второй director. PID/FILETIME/SID/image/config/command hash и nonce
  проверяются перед управлением. Parent PID/FILETIME сохраняются для parent-exit proof.
- SQL nonce находится в имени своего config, а не в неподдерживаемом daemon flag.
  Использованы существующие G0 physical/microseconds и Win32 queries; собственных
  P/Invoke/второй реализации TCP inventory нет. Сеть attested на реальный 127.0.0.1.
- Full installer inventory включает login, game, migrations и database metadata.
  Interrupted bootstrap может пересоздать ровно own schema только до публикации
  Database.ini, с точным owner/port/installer proof; существующая игровая база не сбрасывается.
- Native/worker receipts и логи сохраняются. При живом непогашенном native/worker SQL
  остаётся; force fallback отсутствует. Новый START сначала распознаёт старые incarnation,
  дренирует own native и дожидается own worker. После reboot committed ledger остаётся
  source of truth; START/RESUME создаёт новое поколение, а не повторяет credit.
- Два найденных PowerShell-дефекта защищены focused RED→GREEN: case-insensitive
  Arguments/arguments и null backup в File.Replace. Истинный null передаётся через
  `[NullString]::Value`. PS5.1 исходники сохраняются UTF-8 BOM; JVM stdout/stderr — UTF-8.
- Hard crash может оставить runtime Phantom master=True, пропустив Java finally.
  Supervisor восстанавливает пять OFF baseline полей только после stopped own
  native/worker и свободных game/login ports; исходные bytes/SHA архивируются.
  Это не runtime config mutation во время CUT. Pure baseline regression проверяет
  пять полей, остальные строки, идемпотентность, missing/duplicate keys.
- EXPORT при живом director не открывает SQL-клиент параллельно train. Он пишет
  отдельный durable export request, не заменяя PAUSE/STOP. Director обслуживает его
  только на stopped native boundary; отсутствие ACCEPTED отклоняет один запрос,
  не завершает кампанию. Offline EXPORT держит lifetime lock и stopped guards.
- Native и SQL process resources записываются отдельно: CPU, start/elapsed,
  working set/peak, timestamp последнего доступного sample и явный UNKNOWN.
  Счётчики процесса не являются per-decision Java latency или episode throughput.
- Семь CMD используют process-local PSModulePath только Windows PowerShell 5.1
  Modules под setlocal. Это устраняет подтверждённый выбор унаследованного Security7
  с дублированной ObjectSecurity type data. Аналог — scoped module path в OwnerConfig.
  Глобальная пользовательская среда не меняется; director наследует совместимую копию.
- Если bootstrap прерван до owner и root совершенно пуст, START/RESUME под initialization
  mutex проверяет exact Get-TrainLayout path, private SID/SYSTEM ACL, physical/no-reparse
  и свободные три порта. Каталог сохраняется exact move в одноимённый sibling
  train.bootstrap-incomplete.UUID, затем создаётся новая собственная установка.
  Непустой/чужой/reparse root и занятые порты не принимаются и не исправляются автоматически.
- Process exit использует точные Hold-ProcessHandle / Assert-ObservedExitCode /
  Get-TrackedProcessExitCode из Task108R3 и его существующий harmless process test.
  Worker различает живую attestation failure и завершение своего held process между
  WaitForExit(false) и process query. Image/config pins проверяются и после выхода.
- Первичная failure/phase сохраняется в immutable failures до finally; cleanup имеет
  отдельный record и не стирает её. Hidden Host streams не считаются надёжным журналом.
  Необычные process queries сохраняют PID/FILETIME/nativeError; AccessDenied остаётся
  неизвестностью. Допускается bounded retry либо доказанный выход через ранее удержанный
  own handle с точным PID/FILETIME и известным integer exit code.
- Внешний подтверждённый STOP сохраняет STOPPED, stopNonce, process/port proof и время,
  сохраняя предыдущую primary failure history. Старый STOP_UNCONFIRMED не остаётся
  пользовательским текущим статусом после успешной самостоятельной очистки.

## Windows Job lifetime

Фактический Windows job у вызывающего процесса: immediate job `0x2800` с
KILL_ON_JOB_CLOSE. У WMI bootstrap — общий immediate job `0x1800` без
KILL_ON_JOB_CLOSE, с BREAKAWAY_OK и SILENT_BREAKAWAY_OK. Один `IsProcessInJob=true`
не доказывает зависимость от вызывающего терминала. Пробная дополнительная
генерация процесса не изменила фактов и удалена из решения.

Выбран один documented local Win32_Process.Create launcher, обычный SID/token,
CREATE_BREAKAWAY_FROM_JOB, скрытое окно и whitelist environment без API credentials.
Bootstrap закрепляет исходники SHA и проверяет Job до входа в Director и ресурсов.
Неизвестная query, killing job и неподтверждённые breakaway flags дают отказ.
NULL job query относится только к immediate job; перечисление всех неназванных
предков этим API не заявляется. Существующие Job не открываются для изменения.

Test-JobLifetime прошёл: новый unnamed noninheritable TEST Job содержит только
собственный helper; final bootstrap подтверждён вне этого Job до закрытия handle.
В 13:41:53.7477083Z закрытие Job завершило helper26044, а final83376 продолжил
9 heartbeat и затем штатно вышел с кодом5. Это эксперимент закрытия конкретной
killing boundary, не буквальное закрытие интерфейса Codex. Final launcher дополнительно
проверяется настоящими новыми эпизодами после выхода команды START.

Microsoft API references: [WMI Create](https://learn.microsoft.com/en-us/windows/win32/cimwin32prov/create-method-in-class-win32-process),
[Job query](https://learn.microsoft.com/en-us/windows/win32/api/jobapi2/nf-jobapi2-queryinformationjobobject),
[nested jobs](https://learn.microsoft.com/en-us/windows/win32/procthread/nested-jobs),
[owned fixture limits](https://learn.microsoft.com/en-us/windows/win32/api/winnt/ns-winnt-jobobject_extended_limit_information).

Финальное независимое review выявило и закрыло гонку timeout START до публикации owner.
Production bootstrap теперь ждёт ограниченный ACK, закреплённый PID/FILETIME/nonce и
SHA proof. START сохраняет аттестованный pending Worker до ACK; START/STOP согласуют
эту запись, отменяют её и дожидаются точного собственного exit либо отказывают.
После публикации того же Director pending архивируется. Run проверяет отмену до
build и SQL. NO_ACK и ACK→cancel проверены настоящими bounded probes: Run не допущен,
два точных exit подтверждены, повторный pending отклонён. Слишком ранний незарегистрированный
child без ACK не получает права входа в Run и истекает без ресурсов; полного OS process
inventory это не обещает. Ни сервиса, ни scheduler, ни force-kill fallback не добавлено.
