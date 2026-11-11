# Task 4 — handoff supervisor

Реализованы только разрешённые Task4 paths: `tools/phantom-runtime/training/*`,
`test/java/org/l2jmobius/tests/phantoms/PhantomTrainingSql.java`, PLAN/DESIGN_DECISIONS
и этот handoff. Исходный пользовательский checkout не изменялся. Git-команды не использованы.

## Entry points

Семь `TRAIN_*.cmd` вызывают `Training.ps1` независимо от cwd. START возвращает управление,
оставляя hidden PowerShell Run. Root выводится из script path, единственный допустимый
runtime — `<clone>/.phantom-local/learning002/train`. Default endpoints 33327/17971/19271.

Read-only preflight:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/phantom-runtime/training/Training.ps1 -Action Preflight
```

Он проверяет JDK25, MariaDB11.4.13, Ant, config, Geo203/protected207 и свободные порты;
не собирает и не запускает Java/SQL. START автоматически сравнивает source fingerprint,
выполняет последовательно compile-tests/jar при отсутствии собственного build proof,
stages physical GameServer/data/Geo и создаёт private MariaDB с полным installer.

SQL CLI: `PhantomTrainingSql <prepare|probe> <root> <module>`. Schema
`phantom_learning001_train002`, user `learning001`; JDBC game config публикуется только
после полного SQL installer и metadata readback. Secrets не передаются в Java argv.
MariaDB installer получает secret только внутри private orchestration; его args не печатаются.

Actor CLI: `PhantomTrainingActors <root> <configPath>`; native CLI:
`PhantomTrainingNative <root> <attemptId> <jarSha> <configPath>`. Оба cwd `<root>/game`,
classpath own fresh test bin плюс own runtime libs. Root snapshot содержит learningMode,
modelVersion/modelSha. После stopped boundary evaluator train/report выполняются автоматически.

## Управление и evidence

- `owner-process.json`, `native-process.json`, `worker-process.json`, SQL own receipt:
  PID, exact FILETIME, SID, image/hash, exact command hash, nonce, config/hash и parent identity.
- `director.json`: run/generation/phase/heartbeat/batch count; `command.json` с nonce
  и immutable `commands/<id>.json` исключает stale pause/stop из предыдущего поколения.
- STOP/PAUSE создают только own native request после identity attestation. STOP ожидает
  native и worker, затем делает mariadb-admin shutdown и проверяет три свободных порта.
  Неподтверждённая остановка сохраняет SQL/receipts и возвращает non-success; force kill нет.
- RESUME действующего paused director заменяет command request; повторный START
  подтверждает текущий owner. Новый START после crash/reboot сохраняет run id, увеличивает
  generation, пишет recovery.json и не запускает новый native поверх старого.
- Три последовательных failed native batches дают безопасный FAILED с причиной;
  отдельные непригодные сцены должен пропускать native queue. Все attempt logs сохраняются.
- REPORT при живом director показывает durable status и последний независимый report.
  EXPORT сохраняет отдельный durable запрос и возвращает его id; director обслуживает
  его после stopped batch или при PAUSED. Готовность/отказ видны в TRAIN_STATUS,
  export-result.json и immutable export-requests history. Пауза/STOP не заменяются.
  Параллельного SQL worker во время train больше нет. Без director EXPORT держит
  lifetime lock и требует остановленных native/worker и свободных игровых портов.
- После hard crash runtime Phantom config восстанавливается к пяти canonical OFF
  полям только после stopped guards. До изменения сохраняются bytes/SHA и receipt
  в archives; действующий GameServer не получает такого вмешательства.
- attempts/<id>/native-resources.json и sql/sql-resources.<FILETIME>.json содержат
  CPU seconds, start/elapsed, working set/peak, timestamps и доступность metadata.
  При невозможности финального query остаётся последний live sample, а не нули.

## Проверки и границы

Родительский процесс выполнил RED→GREEN Config (7 negatives, 6 identity negatives),
Arguments (реальное воспроизведение прежнего shadowing), Atomic (реальное падение
File.Replace с null и исправление), Preflight. Full compile-tests также прошёл.
Последнее добавление UTF-8 JVM flags требует повторного Arguments smoke.

После независимого review добавлены Test-ColdBaseline (root подтвердил RED→GREEN)
и Test-ExportAdmission (RED подтверждён; queued route реализован, GREEN ожидается
у основного исполнителя). Test-Export требует реально появившийся ZIP и не выдаёт
принятую в очередь команду за законченный экспорт.

`Test-Training.ps1` содержит Test-Config, Test-Arguments, Test-Atomic и восемь требуемых
smoke action names. Generate/Native/Resume/Detached — наблюдательные checks сохранённых
evidence, их stdout явно не заявляет полный acceptance. Test-Detached heartbeat **не**
заменяет закрытие parent с последующими двумя реальными эпизодами/SQL proof.

Runtime, SQL, настоящий parent-exit, pause/resume/crash и clean STOP этим исполнителем
не запускались; основной процесс сериализует Task5. После завершения редактирования
проверить BOM всех PS5.1 исходников и два отдельных поиска Cyrillic corruption/escapes.

Практическая граница восстановления: настройки endpoint закреплены installation;
смена портов у существующей установки fail-closed. Runtime logs/datadir/Geo не коммитить;
root владеет точным exclude `.phantom-local/learning002/`.

## Совместимость CMD и восстановление раннего bootstrap

CMD задаёт PSModulePath только для дочернего Windows PowerShell 5.1 под setlocal.
Read-only проверка подтвердила прежнюю причину Get-Acl/CouldNotAutoloadMatchingModule:
CMD наследовал Security7 из Codex PowerShell runtime впереди native Security3 и получал
FormatXmlUpdateException на дублированных ObjectSecurity members. Native module import
и CMD PrivateBootstrap с локальным совместимым path прошли. Глобальная среда сохранена.

Outer catch выводит безопасный JSON с exceptionType, commandName, scriptName/line и
fullyQualifiedErrorId, без Message, source line, Definition или аргументов/реквизитов.
Test-PrivateBootstrap проверяет actual NewRoot, ACL, owner и отказ при повторном создании.

Отдельный Repair-TrainEmptyBootstrap вызывается START/RESUME только внутри initialization
mutex для root без owner. Он требует Root=ExpectedRoot из Get-TrainLayout, protected
current-SID/SYSTEM ACL, физические пути, zero entries и свободные SQL/Game/Login порты.
Exact move сохраняет пустой каталог рядом под train.bootstrap-incomplete.UUID; NewRoot
создаёт свежую установку, а собственный archives получает recovery receipt. Непустой
или чужой каталог не принимается. Test-EmptyBootstrapRecovery после RED ожидает root GREEN;
этот исполнитель не перемещал реальный незавершённый runtime и не повторял START.

## Process exit и первичная диагностика

Первый реальный director успел продолжить работу без parent, собрать свои classes/JAR
и установить 125 SQL scripts с PROBE_PASS; native ещё не стартовал. Исходная причина
его последующего отказа **не установлена**: старые hidden streams пусты, exit.json
worker не появился, а finally заменил фазу на STOP_UNCONFIRMED. Raw ExitCode и гонка
query после WaitForExit(false) остаются гипотезами, а не установленным результатом.

Переиспользованы точные три process функции Task108R3; основной исполнитель подтвердил
их существующим Test-Task108R3Process.ps1 -WrapperPath TrainingOwner.ps1 (0/7,
null/unknown/invalid handle rejected). Worker сохраняет cached handle, проверяет
HasExited при исчезновении процесса между poll и attestation, пишет exit receipt
и освобождает handle в finally. Hash/config failure не подавляются.

До finally сохраняются immutable failures/{id}.json с безопасными полями, known code,
phase, run/nonce и PID/nativeError, без Message/expanded args/SQL secrets. PRIMARY id/code/
phase остаются в director.json при cleanup failure. Cleanup/worker/control записи
отдельны. Test-ErrorHistory защищает primary SQL_PREPARE от перезаписи cleanup и утечки
произвольного password-like текста; GREEN ожидается у основного исполнителя.

Process visibility unknown логируется в process-observations и повторяется не более
10 раз по 100 мс. Error5 не является отсутствием. Ранее удержанный own process handle
может доказать выход только при точном PID/FILETIME, HasExited и valid integer exit.
SQL/Native/Director stop затем отдельно требуют свободных портов. Внешний STOP после
полной проверки receipts/ports сохраняет STOPPED, stopNonce и commands/stop-confirmed proof.
