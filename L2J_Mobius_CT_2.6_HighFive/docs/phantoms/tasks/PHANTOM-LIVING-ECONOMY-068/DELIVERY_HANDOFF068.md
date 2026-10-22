# Delivery-tools068 — подготовка к финальной поставке

22.10.2026: tools и focused negative guards подготовлены. **Финальный committed build/assembly/ordinary Main/sameDB restart/STOP ещё NOT_RUN** в этом блоке; их выполняет основной исполнитель после финального codeSHA. Ни внешний candidate, ни standalone TEST runtime сейчас не созданы. Проверки существования обоих путей вернули False.

## Exact scope

Пути относительно `L2J_Mobius_CT_2.6_HighFive/`:

- `tools/phantom-runtime/Build-Task068.ps1`
- `tools/phantom-runtime/Assemble068.ps1`
- `tools/phantom-runtime/Runtime068.ps1`
- `tools/phantom-runtime/Task068Common.ps1`
- `tools/phantom-runtime/Task068DatabaseProbe.java`
- `tools/phantom-runtime/Test-Delivery068.ps1`
- `java/org/l2jmobius/commons/util/LocalRuntimeShutdown.java`
- `test/java/org/l2jmobius/commons/util/Task068RuntimeGuardChecks.java`
- Этот `DELIVERY_HANDOFF068.md`.

Исторические067 scripts/resources не менялись. Прочитаны verified Build/Assemble/Runtime067, LocalPlay-Ownership,067 Prepare-Geo/Run-Final/Verify-Delivery и068 Prepare-Inputs. Переиспользованы exact archive/tree/test/JAR/physical Geo/role/PID-incarnation contracts067 через отдельный common068, без замены IDs исторических файлов. Existing `RuntimeDatabaseCheck` привязан к058 и не ослаблялся; новый probe имеет только две exact068 schemas и read-only SQL.

Общие GameServer/Shutdown/Player/PhantomSystem/build.xml/launcher этот блок не менял. GameServer и LoginServer уже вызывают `LocalRuntimeShutdown.validateLaunch/start`: дополнительные hooks не нужны. В utility добавлены только owner/control068, exact schemas и exact roots; старые064–067 bindings сохранены.

## Контракты запуска

- `Build-Task068.ps1 -Label <unique-label> -SourceCommit <exact-HEAD>` требует feature/phantom-world, точный40-char HEAD и clean module. Делает git archive только committed build.xml/java/test/dist/tools, затем последовательный Ant `jar compile-tests` в новом owned build root. Без успешной сборки successful BUILD_INFO не создаётся; существующие outputs не перезаписываются.
- `Assemble068.ps1` сохраняет проверенный067 interface: ModuleRoot, CommittedModule, BuildRoot, OutputRoot, GeoManifest, DatabaseConfig, RegistrationFile, JavaHome, SourceCommit, Kind. Source архив/дерево/test tree/JAR hashes перепроверяются до создания output. Geo203 берутся только из protected067 physical source по own068 manifest, физически копируются с SHA. Секреты копируются в user-only DACL; новых секретов в Git нет. Signed HexID допустим: stock ServerConfig использует signed BigInteger.
- Release root вычисляется от фактического диска source module: `PhantomWorld/Living-Economy-068-candidate`. TEST root — **новый `.phantom-local/task068/runtime-test`**, чтобы не перезаписать уже используемый native-test. Это bounded path deviation для отдельного ordinary Main; он использует ту же существующую TEST068 schema. Новые schemas не добавлены.
- TEST ports:2249/7949/9249; release:2149/7849/9149. Game/listener/registration hosts проверяются как127.0.0.1. Занятые чужие endpoints сохраняются.
- Runtime содержит свои Runtime068/Common068/LocalPlay-Ownership/compiled read-only DB probe и cmd wrappers. Source/checkouts/archive не читаются при START/STATUS/STOP; source paths в receipts остаются только provenance metadata. Требуется существующий установленный JDK25 из runtime.local.ini, установка SDK не выполняется.
- Runtime сверяет root/role/schema, JAR hashes, source receipt, exact tool manifest и Geo manifest. Validate/Start проверяют все203 physical Geo hashes. PID записи проверяются по pid/startTimeUtcTicks/runtimeId/role/command line/java executable/expected ports. Foreign agents и смешанные task flags отклоняются.
- START требует обе роли stopped, свободные endpoints и0 других соединений с own schema. STOP публикует identity-checked request штатному shutdown; force fallback отсутствует. После остановки обеих ролей probe требует0 соединений с own schema, затем STATUS снова показывает число. Probe делает только SELECT/read-only connection и не выводит credentials.
- `START_HERE_068_RU.md` генерируется с реальными source/schema/ports и таблицей собственного профиля: Phantom master, merchant role и Custom Alt+B включены; population/active/cap8, starter shots0. Personal QoL/blue остаются shipped OFF. Итоговые A–G и TEST timezone/passive origin основной исполнитель фиксирует перед доставкой. Standalone price CLI/README входят в exact TOOLS manifest.

## Выполненные проверки

1. Java regression RED на прежнем JAR: standalone `Task068RuntimeGuardChecks`, exit1, `reports/runtime-guard068-red.log`: flag068 молча обходил launch guard (`expected exception, no exception thrown`).
2. Узкий `javac -encoding UTF-8 -cp <dev testbin/JAR/libs> -d <task>/delivery-guard-bin <LocalRuntimeShutdown.java> <Task068RuntimeGuardChecks.java> <Task068DatabaseProbe.java>` — exit0. Это не full production build и не подмена JAR.
3. `java -cp <delivery-guard-bin;dev-testbin;dev-JAR;module-libs> org.l2jmobius.commons.util.Task068RuntimeGuardChecks` — exit0, `reports/runtime-guard068-green.log`. Проверены unbound launch, exact own JDBC, отказ protected067/localhost alias/mixed authority, exact candidate/test roots и historical root отказ. БД/listeners не открывались.
4. Та же команда с `org.l2jmobius.commons.util.Task067RuntimeGuardChecks` — exit0, `reports/runtime-guard067-regression.log`; historical067 guard не отключён.
5. `powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/phantom-runtime/Test-Delivery068.ps1` — exit0, `reports/delivery-tools068-guards.log`:3 focused cases (root/schema/role/JDBC binding; stale PID/foreign listener; parser всех5 PS files). DB_WRITES=0, runtime PROCESS_ACTIONS=0, externalWrites=0.

Все5 новых `.ps1` сохранены UTF-8 with BOM и проверены фактическим Windows PowerShell5.1.19041.6456 parser. Git-команды этим исполнителем в delivery-блоке не выполнялись: git вызовы находятся только в ещё не запущенном future build tool. Ant/native/ordinary Main также не запускались; existing068 schemas, private inputs, native-test и protected057–067 не изменялись.

- Mojibake-маркеры в изменённых файлах проверены отдельно,0 совпадений.
- Escaped Cyrillic/XML escaped Cyrillic в изменённых файлах проверены отдельно,0 совпадений.

## Осталось основному исполнителю

После code commit: запустить exact committed build, focused tests из этого build, assembly с canonical own private inputs и existing JDK25, Runtime Validate. Для external candidate применяется только необходимая platform escalation, пользовательская authority уже дана. Затем документированный enabled profile, ordinary START/STATUS/STOP, sameDB/sameJAR повторная загрузка и финальная проверка own JVM/listeners/connections0. Самостоятельность runtime без checkout и реальный graceful shutdown доказываются этими будущими runs; текущие pure guards их не подменяют. Если этап упадёт, partial output сохраняется, новая успешная receipt не выдумывается.

Уточнение владельца09:30UTC: итоговый candidate сохраняет исходные XML-цены Alt+B; тестовый Apply/native purchase/Rollback доказаны отдельно на TEST. Предыдущие формулировки про обязательный candidate Apply заменены этим решением. Guide называет starter0 измерительным профилем068; R2 birth-only lifetime5000/replay protection остаются в продукте, existing commerce-decision regression PASS. План цен и CLI доступны оператору без автоматического включения Alt+B shopping у обычных фантомов.
