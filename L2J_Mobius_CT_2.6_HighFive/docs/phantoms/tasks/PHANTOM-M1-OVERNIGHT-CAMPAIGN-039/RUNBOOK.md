# RUNBOOK039 — согласованные runtime операции

OWN: `C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius`
MODULE: `OWN\L2J_Mobius_CT_2.6_HighFive`
MAIN: `C:\Users\ZBook\L2J_Mobius`, только чтение.
Repository: kpCat/L2J; remote: experiment/m1-candidate007-observe008.
Base:8bf4df23a23854b9ff57c13b83a8ab34f07aa4c2.

## Вход

Проверить реальные пути (не `C:\Users\ZBook.codex`), Git registration/current worktree,
HEAD, existing diff/untracked, процессы с cmdline/startTicks, порты и DB. Чужие файлы и
процессы не трогать. Пакет039 из MAIN импортировать в OWN по MANIFEST с проверкой хешей.
Старый отчёт сборки не является доказательством текущего classloader/Jar.

Проверить archive-receipt038 и выбранные entries. Не перепаковывать десятки тысяч старых
файлов. Нужны только уникальные current raw, source, Jar и manifest; никаких secrets и
whole DB в публичном Git. Дисковый бюджет оценить по actual dumps и свободному месту.

## Собственные базы

| Schema | Назначение |
|---|---|
|l2jmobiush5_localplay_contract039t|Native TEST|
|l2jmobiush5_localplay_contract039a|Диагностика|
|l2jmobiush5_localplay_contract039b /039c|Natural, away и sameDB проверки|
|l2jmobiush5_localplay_contract039x72 /039x73|Изолированные crash-копии|

Создавать только отсутствующие schemas либо возобновлять ресурсы с точным owner039.
Адрес строго127.0.0.1:3308. Это копии существующей схемы, не миграция.
До write проверить owner=TASK039_CONTRACT, cwd/config/export SHA, JDBC и SELECT DATABASE.
Старые038/037/035/PLAY/shared phantom_test — SELECT/read/export only. Никаких heal/restore/drop.
Занятое имя или порт не даёт права присвоить чужой ресурс. Сохранить BLOCKED_INPUT без ожидания.

На основе Verify038/ops038 сделать private adapter039: только owner/path/DB/output/lane
и согласованные исправления collector. Существующие SQL guards сохранить. Native TEST039t
добавить в existing environment. Пользоваться установленными JDK/Ant/Python/PowerShell,
не тратить ночь на обновление инструментов.

## Запуски

Настройки038:1280 READY/8720 RETIRED, active cap8, scheduled10000, inventory250, прежние
rates/geodata. Не уменьшать MaxScheduled и не увеличивать радиусы в этой кампании.
Synthetic — существующий native Player с pre-World read-only setup, не fake REAL_LOGIN.
Diagnostic probe до150 секунд; natural scenes360–420 секунд без breakpoints и target commands.
Каждая запись содержит profile/object/epoch/goal revision/target generation/instance.
Не вычитать счётчики разных эпох. Reused ID требует нового birth и корректного parent handoff.

Debugger только127.0.0.1, suspend=n; не замораживать весь сервер на длительное время.
HotSwap — лишь маркированный эксперимент. Финал: чистая committed Ant-сборка и известный Jar.
Для каждого старта сохранять source/Jar/classpath/scripts/config/catalog/DB identity.
Thread dumps/JFR использовать адресно, не как бесконечную запись всего мира.

## Естественная группа и полный цикл

До measurement выбрать целую локальную группу6–8 живых, календарно доступных участников,
не используя будущие результаты. После неудачи нельзя исключить437. Неподходящий whole
setup сохранить как INVALID_PRECONDITION; не переписывать HP/XYZ, чтобы он стал подходящим.
Cohort и primary фиксируются один раз. Два финальных окна — на одном freeze. Не выбирать
только заведомо выигрышный класс: один caster и другой подходящий primary.

Away: native MOVE Synthetic по проверенным рёбрам за актуальные prewarm/visibility,
затем дождаться существующей soft-retention. Force dematerialize запрещён. Hard demand
сохраняет значение; снять только собственное наблюдение законным действием, не отключать guard.
Background progress — настоящий committed TRAVEL/FARM с native policy и прошедшими
wall-clock intervals. Не ускорять часы и не выдавать награду из observer.

Вернуть того же Synthetic проверенным native путём. Если525-секундный TTL не совместим
со всем планом, заранее разделить на два наблюдения: stock STOP → отсутствие → background
→ новый START той же clone identity через existing resolver. Это обозначить как logout/return,
не как непрерывную прогулку. Период между ними связать producer receipts; не импортировать
свежий dump. Для непрерывного walk оба направления проверять отдельно. TTL/heartbeat/радиусы
ради помещения сценария не увеличивать.

Смерть: использовать natural событие, если оно произошло. Иначе отдельный существующий
controlled native fixture039t с настоящим doDie/stock resurrection workflow, без ручного
stat repair. Различать natural и controlled proof. Нет полезного возврата — G4 FAIL, не N/A.
Не делать глобальные GK-путешествия новым обязательным milestone этой ночи.

## Штатная остановка

Synthetic STOP/off, затем existing stock Game/Login stop, с poll одного исходного deadline.
Сохранить FULL receipts и typed COMPLETE/retained0/exporter drained, затем проверить OS0.
Запрещены generic force, taskkill и Stop-Process -Force. Если stock stop завис, сохранить
данные и точный собственный PID в CLEANUP_BLOCKED, не написать ложный JVM0.

## Разрешённое исключение для аварийных тестов

Existing fault injection72/73 допускается ТОЛЬКО на own039x72/x73 в точных owned-store
окнах. Обязательны PID/startTime/cwd/Jar/DB/manifest guards. Это не разрешение убить обычный
сервер или PLAY. Fault scope — один exact profile/epoch, без REAL; MariaDB не останавливать.
Проверить текущий код injector до arming. На recovery fault выключен, используется та же
БД без restore/import. После reconciliation провести повторный restart для idempotence.
Не начинать crash без минимум30 минут на recovery и сохранение доказательств.

## Диск

phantom-dev сохраняется, новые worktrees не создаются. Старая очистка остаётся PAUSED.
Ant может пересоздавать собственные build outputs; foreign raw не трогать. DB не удалять.
Selected review archive хранить вне worktree с SHA и защитой путей, не копируя весь runtime.
