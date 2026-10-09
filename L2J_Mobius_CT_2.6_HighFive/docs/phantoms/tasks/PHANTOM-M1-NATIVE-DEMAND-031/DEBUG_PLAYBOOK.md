# DEBUG_PLAYBOOK031 — runtime-first, без серии пересборок

## Почему используем именно это
На required base HighFive `build.xml` target `compile` содержит:
`debug="true" debuglevel="lines,vars,source" source="25" target="25"`.
Компилятор уже поставляет line/locals/source symbols. JDK 25 поставляет `jcmd`,
JFR и `jdb`. JVM запущена в *собственной* contract031 clone без REAL пользователей.
Цель — установить первый изменившийся predicate и source of failure на текущем
`GameServer.jar`; НЕ выполнить новый M1 closeout.

## A. Preboot, launch, граница разрешений (обязательно)
1. Прочитать current AGENTS, Control/Prepare030, точный GameServer launcher.
   Сверить required HEAD `e083f35d9b3b1c1441f484c8f760c8dc34bbdbc0`,
   protected launcher и preboot data snapshot, GAME JAR SHA и PID+start-time.
2. Запустить *только собственную* GameServer JVM и own LoginServer в изолированной
   clone. Если task-owned GameServer launcher допускает безопасный отдельный JVM
   аргумент, добавить **только ему**, на loopback:

   `-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:5005`

   Порт `5005` пример: выбрать незанятый localhost port, записать выбранный порт
   и точную собственную PID incarnation. Не разрешать `0.0.0.0` и `*`, не выставлять
   JDWP глобально для LoginServer/Ant/PLAY/чужих JVM. `suspend=n` исключает
   блокировку старта до подключения. Если перехват launch flags требует правки
   защищённого launcher — НЕ модифицировать общий файл: пользоваться task-owned
   ограниченным стартовым wrapper или перейти к `jcmd`/JFR без JDWP; указать blocker.
3. Стартовая JVM должна иметь тот же tested Jar (или одну baseline сборку,
   если он не существует/не совпадает). После старта НЕ собирать сервер ради
   каждого нового диагностического события. До любого gameplay mutator — exact
   preboot state manifest, baseline classification, hash исходных данных.

## B. Non-stopping diagnosis first — почти нулевое вмешательство
Исполнять **на своей GameServer PID, не LoginServer и не всех JVM сразу**.
Содержимое/путь файлов писать в собственные evidence; сохранить exit code.
Проверять `jcmd <PID> help` до неизвестных команд.

PowerShell-шаблон (подставить собственный JDK25 и PID, созданный каталог evidence):

```powershell
$jdk = 'C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot/bin'
$jcmd = Join-Path $jdk 'jcmd.exe'
$pidGame = <EXACT_OWN_GAME_PID>   # заменить числом, не использовать PowerShell $PID
$evidence = '<EXACT_OWN_TASK031_EVIDENCE_DIR>'
& $jcmd $pidGame VM.command_line | Set-Content "$evidence/vm-command.txt" -Encoding utf8
& $jcmd $pidGame Thread.print -l | Set-Content "$evidence/threads-before.txt" -Encoding utf8
& $jcmd $pidGame JFR.start name=M1_031 settings=profile duration=60s "filename=$evidence/first-60s.jfr"
# Во время записи выполнить только настоящий Synthetic approach из DESIGN031.
& $jcmd $pidGame Thread.print -l | Set-Content "$evidence/threads-after.txt" -Encoding utf8
```

`JFR.start` можно выполнить на УЖЕ работающей JVM. JFR даёт sampling,
lock/CPU/thread evidence, но по умолчанию не доказывает состояние Java boolean,
почему ecology решила `false`, и не гарантирует запись каждого исключения.
Если первая причина — **логический отказ без исключения**, следует проследить
конкретный predicate/decision, а не ждать stack trace, которого не существует.

## C. Targeted JDWP attach и первое расхождение
Если JVM имеет JDWP — `jdb -attach 127.0.0.1:<OWN_PORT>` либо attach IntelliJ
IDEA / VS Code Debugger for Java. Codex без GUI вправе пользоваться штатным `jdb`.
Breakpoints делать ТОЛЬКО на 1–3 выбранных заранее профилях/переходах,
например `PhantomPopulationEcologyService.registerDue`, `dueSnapshotLocked`,
`processRequested`, `historicalFailure`, `PhantomBackgroundTransaction` отказ
по точно найденному stack. Эти имена проверять по source/class names.

Рекомендовано non-suspending trace/logpoints (если выбранный debugger поддерживает)
или короткий breakpoint *только affected thread*. CLI `jdb` может остановить
весь процесс; не использовать долгий `stop in` на горячей global method без фильтра.
При безусловных `SQLException` во всех 1280 — собирать первую foreground ошибку
через diagnostic-only bounded scalar hook из SOURCE_MAP, а не тысячи stops.
На попадании: `where`, `locals`, переменные profileId/requestId/epoch,
actual state, cause, SQLState, threadName, timestamp; затем сразу `cont`.
Не использовать evaluate/`print` с побочными эффектами: никогда не вызывать
materialize/store/save/farm/reset/release/DB write из debugger.

Никакая остановка на breakpoint и её watchdog/TTL side effects НЕ считаются
честным farm/timeout/callback proof. Тестировать тайминги только без debugger suspend.

## D. HotSwap: когда можно и когда нельзя
JDK HotSpot стандартно может заменить тела существующих методов после компиляции
нового `.class` при поддержке Debugger/VM, но не допускает добавления/удаления
полей и методов, изменения сигнатур, наследования и ряда class attributes.
И текущие stack frames могут доработать СТАРУЮ версию метода.
В TASK031 разрешены только **не меняющие семантику** наблюдательные edits
в `DIAGNOSTIC_ONLY` paths. Такой диагностический HotSwap допускается только в
изолированной own JVM и после proof of first cause. Отказ redefine — не
обходить JDK ограничение. Сохранять SHA класса до/после и точный change set.
HotSwap не изменяет JAR на диске и никогда не подтверждает production PASS.
Любая настоящая semantic fix — отдельное согласованное решение ПОСЛЕ причинной
диагностики, с чистым baseline, обычной сборкой и регрессиями. Не делать SQL-heal.

## E. Exit criteria и stopping rule
- Не более 2 GameServer starts/probes; первый до 35 минут, бюджет120 минут.
- До первого proof не создавать новые 5–10 диагностических wrappers и не пересобирать
  production без ошибки сборки. Native incident и SQL guards unchanged.
- Разделить 3 направления:
  (1) observation/setup исключал native-demand;
  (2) actual demand возник, но production admission/catchup запретил;
  (3) actual native entry появился, но фарм не продолжился.
- Для каждого сопоставить baseline state→event/stack→decision outcome, с exact
  PID/time/source. Cached error не первый current exception. Если sample показал
  logical false без throwable, записать конкретный predicate и его значения.
- Даже GREEN031 не означает GREEN M1. Оставить RESULT/HANDOFF с causal map:
  `first_failed_predicate`, `throw_origin_or_none`, `ownership`, `next_action`,
  `runtime_profile_findings`, `hot_swap_used`, `build_count`, `JVM_start_count`.
- В конце всё остановить graceful, PID+start-time absent, pending/retained/REALcount
  check, exact-path commit/normal push. Свой evidence с JFR/JDWP может содержать
  потенциально чувствительные данные: отчёт публиковать с redact/provenance;
  тяжёлый `.jfr`/heap dump не загружать в публичный Git без ревью.

Oracle JDK25 references:
- https://docs.oracle.com/en/java/javase/25/docs/specs/man/jcmd.html
- https://docs.oracle.com/en/java/javase/25/docs/specs/man/jdb.html
- https://docs.oracle.com/en/java/javase/25/docs/api/java.instrument/java/lang/instrument/Instrumentation.html
