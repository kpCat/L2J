# Read-first PHANTOM-M1-NATIVE-LIFECYCLE-CLOSEOUT-006

Дата: 03.10.2026. Рабочая копия задачи: отдельный managed worktree
`C:/Users/ZBook/.codex/worktrees/m1-native-closeout-006/L2J_Mobius`.
Baseline, исходный HEAD этого worktree и проверенный remote feature/phantom-world:
`461a4abe32be4aa08532b8417a6147684a8889c6`.
Исходный пользовательский checkout сохранён на
`b80cdf78560c06a9d88b8ec761e1582f0e55afba`; staged diff пуст.
Пользователь разрешил отдельный worktree и обычный push HEAD в целевую ветку.

## Прочитано и переиспользуется

- High Five AGENTS.md, PHANTOM_DEVELOPMENT_MASTER_PLAN.md,
  CODEX_WORKFLOW_CONTRACT.md, TASK_PACKAGE_STANDARD.md, корневой README.md.
- TASK, ROOT_CAUSES, DESIGN, STATE_MACHINE, PATCH_GUIDE, PLAN, SCENARIOS,
  ACCEPTANCE, VERIFY, SOURCE_MAP, READ_ONLY_EVIDENCE, RUNTIME_GATES и proposals.
- Локальный `continuation-observer-closeout/audit-closeout-20261002/SUMMARY-current-20261003.md`.
- build.xml; настоящие AutoPlay/AutoUse run/start/stop, Player owned store/resume,
  PhantomVisibleAutoPlay и PhantomMaterializedPlayer cleanup/materialize/record.
- PhantomBackgroundSuite native pool fault, linked production executor,
  owned store selectors; PhantomProductionMaterializationSuite; guarded TEST
  bootstrap/schema validator и PhantomHeadlessPlayerTestEnvironment.

Локальные аналоги: TEST-only reflection для исполнения настоящих pool Runnable;
exact identity/action leases; beforeStore forward / afterStore reverse lifecycle;
guarded TEST fixtures и штатная очистка. Shell reflection harness не создаётся.
AGENTS.md в корне checkout и выше, в docs/phantoms/tasks и папке TASK не найдены.
В java/test/docs вложенных AGENTS.md нет. README.md модуля отсутствует.
SOURCE_MAP и PATCH_GUIDE выполняют роль code-map/pattern; отдельные файлы не нужны.

## Ограничения и provenance

High Five, Java 25.0.4.1, Ant 1.10.17, существующие HikariCP 7.0.2,
MySQL connector 9.5.0, SLF4J 2.0.17. Новые зависимости и schema запрещены.
TEST config указывает точный `l2jmobiush5_phantom_test` на 127.0.0.1:3308;
полная валидация существующим Java guard ещё предстоит.
PLAY и текущие JVM не изменяются; deploy, synthetic №5, real arm, M2 запрещены.
Источник сохранённого b4f protocol — предок baseline
`b4f1f03d7407897f50bd3131f40d6e19011116e1`; усиления ea56bef сохранены.
Более 10 путей разрешены bounded allowlist самого TASK/SOURCE_MAP, без broad refactor.

Локальный SUMMARY найден и прочитан. Его 11-file cumulative local delta и
опубликованные 8 путей diagnostic commit — разные объекты; самостоятельно
подтверждены 8 точных путей commit 461a4abe. Исторические утверждения о 23/23,
7/7, owned C/D и clean JAR считаются сообщёнными до новых запусков.
Точные selectors C/D: background-position focus `owned-store` и
`owned-store-transaction`, actual cases 08/09 в PhantomBackgroundSuite.
Первое исключение current historical epochs 110/142/175 не найдено в прошлом
аудите: `HISTORICAL_FIRST_EXCEPTION=UNAVAILABLE`. Новые TEST не устанавливают его.

JVM read-only inventory: Login PID 24248 start 2026-10-03 12:23:13 local;
Game PID 33368 start 12:23:14 local. Хэш JAR в исходном checkout
`3AB3E0F94AF06AE0D70D7176CF23C6D8FCAC850B3B240956FD6917E5BED9D0F0`
не выдаётся за загруженный Game JAR: прошлый SUMMARY сообщает иной deployed SHA256.
Фактический runtime directory/manifest ещё подлежит безопасной сверке.

## План выполнения

0. Зафиксировать приватный before/after inventory и команды.
1. Actual native RED P01–P09; минимальные ownership/session fixes и GREEN.
2. Actual E01–E06 RED; first/suppressed bounded detached evidence и GREEN.
3. Actual terminal T01–T05 RED; typed bounded resolution и GREEN.
4. Native Q01–Q05; producer fix только после доказанного RED, b4f guards сохранить.
5. Actual/offline A01–A08 RED; truthful selected/cohort evidence и GREEN.
6. Focused/affected regression, performance, review; exact-path commit/push;
   чистая committed-SHA сборка, remote RESULT/HANDOFF и финальная граница.

Непроверено: actual RED/GREEN, bounded native callback drain, passive attribution,
полнота runtime manifest, remote publication и final artifact. M1 остаётся OPEN.

Ruling: использовать отдельный разрешённый worktree от baseline, не приводить
исходный checkout к remote. Это сохраняет чужие изменения и b4f ancestry;
цена неверного решения — дополнительный изолированный checkout, без изменения PLAY.

## TEST preparation и первый RED

Existing schema guard отверг первые два запуска (exit 2), до fixtures. Это не RED
production дефекта. Изолированный Windows checkout содержит CRLF в шести SQL,
а подготовленный TEST manifest соответствует LF этих же текстов. Текст всех шести
после CRLF/LF normalization равен предыдущему native TEST worktree; остальных
SQL byte differences нет. Git archive подтвердил, что baseline export также
сохраняет эти CRLF: одного archive оказалось недостаточно.

Ruling: только в нашем worktree нормализовать CRLF→LF у
clan_social_identity.sql, phantom_profiles.sql, phantom_reservations.sql,
phantom_reservations_checkpoint2.sql и migrations 001/002. Это runtime byte
подготовка existing TEST inventory, без SQL/schema/manifest/guard изменения,
без source diff или включения SQL в commit. Исходный checkout не тронут.
Цена неверного решения — guard rejection; ослаблять guard запрещено.

Третий запуск подтвердил guarded schema hash
`394F26E9792EF56B77E1293DFCB7A336BEFE48F224140CCD7626475EDE1BE04E`.
Настоящий RED: P04/P05 обоих managers уничтожают P2 после P1 rejection/throw.
`total=6 passed=0 failed=6`, Ant exit 1. Отдельный отчёт сохранён в
`.phantom-local/m1-native-closeout-006/red-ownership-lf/phantom-test/reports/`.

Уточнение: начальная P01 AutoPlay fixture не spawn-ила observer; setTarget отвергал
его до tick. Её исходная assertion failure не засчитывается как валидный RED.
Добавлены spawn и отдельная проверка target до run. Исправленный ownership/session
run `green-session` дал 8/8 PASS, exit 0. Требуется повторный P01 RED на baseline
production classes с исправленной TEST fixture; GREEN сам по себе не заменяет RED.

Workstream 1: native P04/P05 RED→GREEN; P07 bounded Session-publication barrier и
P08 partial-pair RED→GREEN. P02/P03/P06/P09 ещё не закрыты. Вводится отдельный
AtomicBoolean token Session при native регистрации: это side-effect-free validation
под registration monitor, без вызова Player/DB/другого manager/striped lock под ним.
Ruling: native API overload с Session token нужен, чтобы поздний P1 start не мог
перепубликоваться после P2; цена неверного решения — неполная pair и repair retry.

Workstream 2: actual native E03 store/resume SQL primary маскируется afterStore B;
две корректные assertion failures в `red-errors-and-context`. Player store/resume
изменены по preserve-primary shape. GREEN и остальные E01/E02/E04/E05/E06 ещё required.

Workstream 4: stock native AutoPlay реально дошёл до lethal-hit/reward; TEST сообщил
EXP +70 / SP +2 в отдельных запусках. Cleanup retained с точным message
`Canonical Player is in an unsupported background context.`; flags до attack допустимы,
перед store единственное отклонение `combat=true`. Existing clientStopAutoAttack у
Player сохраняет flag через AttackStanceTaskManager; добавлен существующий stance
teardown. Это не объявлено safe drain: callback lease и PREPARE interleaving ещё RED gate.
Неудачная compilation из-за неверного имени cleanup API, Ant argument parsing и
неверное ожидание callback при удерживаемом Player-store monitor не являются RED.

## Финальная проверка и остановка

Existing guarded TEST validator после LF preparation подтвердил schema
l2jmobiush5_phantom_test, 121scripts/214statements, hash выше. E01–E06 native
assertions прошли в candidate; полный green-incident-mage run17/18, exit1.
Отдельный mage retest после reuse штатного MasterHandler bootstrap1/1, exit0,
stock1177:1, actual cast/kill и EXP70/SP2. Native hit/timeout тоже прошли.
Эти результаты не доказывают полное покрытие async writers.

Ignored native geodata отсутствовала в isolated checkout. Existing203files,
1063452308bytes, скопированы из original в isolated TEST; SHA256 каждого совпал.
Geodata не редактировалась/не публикуется. Первый travel без assets не RED;
повторный actual FarmTravel + mirrored callback RED. Production PhantomSystem
composition сам не исполнен fixture; raw report wording уточнён в REVIEW.

Native delayed ON_ATTACKABLE_KILL TEST доказал PREPARE/cleanup SUCCESS и ownership
release до Quest.giveItems. PLAN workstream4 / DESIGN D5 требуют BLOCKED: finite
producer drain вне SOURCE_MAP не внедряется скрытым broad rewrite. Доказанный
delayed event не покрывается Player callback leases.

Original five foreign hashes повторно совпали; HEAD b80cdf7 и empty staged
сохранены. Login24248/Game33368 start times те же. Свежий original dist/libs/
GameServer.jar hash равен read-first3AB3E0F9..., но не объявляется loaded JAR.
PhantomPlayers.ini/LocalPlayPilot.ini hashes совпали. Runtime deployment manifest
не переустановлен; PLAY JVM/config/JAR не менялись.

Независимый review подтвердил BLOCKED и два дополнительных candidate риска.
Production candidate не публикуется как source change: task docs, sanitised reports
и полный audit patch. Broad regression/artifact gate NOT_RUN.
