# PHANTOM-M1-SHUTDOWN-049

Статус: PARTIAL; verdict=CAUSE_FOUND_NATIVE_NOT_RUN. Причинный дефект подтверждён и исправлен; обычный GameServer run049 не выполнялся. M1=OPEN; READY_FOR_M2=false; USER_ACCEPTANCE=REQUIRED.

Только High Five. OWN=`C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius`; BASE/initial remote=`578e4fe70d492578c0bd01d5692bb3a624acc964`; existing detached HEAD. Целевой remote ref=`experiment/m1-candidate007-observe008`. Без субагентов, новой ветки, G4, crash/client/attach и review ZIP.

## Причина048 и причинная проверка

Исходный witness: `.phantom-local/ops048/raw/STARTUP048a-8b07eb55e2aa4de3962cfdbb08afa1bf/lifecycle-progress.tsv`, строка2, SHA256 `31f72e8381755087b11fb19ae6e3209e77d07248b50e5832f5b0b3d24e701f62`.

`sampleNanos=725449485838500; phase=WAIT_ACCEPTED_CONTROL; outcome=FAILED; blocker=background.recovery:profile=10437:object=268487955:epoch=724846745006000:OperationResult[status=INCONSISTENT, reason=recovery.rematerialization_capacity_reached, encounters=0, elapsedMillis=0, dead=false]; deadlineNanos=725459390696300`.

На момент witness deadline оставался впереди на9.9048578s. First recorded cause — latched background recovery failure, а не истечение system stop deadline. Точный момент первого capacity отказа и конкурирующий профиль048 не сохранены; они остаются UNKNOWN.

`contract048a/runtime/game/log/java0.log:281–288`:20:01:23 local штатная stop-команда; через11ms initial systemState=FAILED/materialization=RUNNING/retained0; final log `completed, stopped=false`; затем pool stop. SHA256 журнала `56cb0bc9835530ac5137155676009122c055dfc6bab483a3760843044382edfc`.

Причинный путь: `PhantomBackgroundService.finishRecoveryStore` подтверждает store как READY, затем rematerialization получает штатный CAPACITY_REACHED. Общая ветвь возвращала INCONSISTENT; `finishRecovery` записывал sticky `_firstRecoveryFailure`; `PhantomSystem.shutdownClaimed` перед player drain превращал его в FAILED. Поздний legacy cleanup освобождал configured instance; final log без configured instance не доказывает typed DONE/COMPLETE.

Два focused tests на исходном immutable JAR `fc19dc90786947eff5ad427792ac4f3a491340c0e647feb194ecb088f29adec2` дали causal RED:0/2, exit1, exact rematerialization_capacity_reached и преждевременный terminal control. Свежий jar-first GREEN:2/2, exit0. S06 использует реальный native store/SQL и другого owner единственного fixture slot; S07 воспроизводит captured post-store control через существующий reflection fixture pattern и исполняет настоящий advanceRecovery→PhantomSystem shutdown.

GREEN S07: operations1/pending до stop; затем `StopProgress[phase=DONE, outcome=COMPLETE, blocker=]`, operations0/retained0 до остановки shared pool. Negative: неверный character owner остаётся INCONSISTENT; capacity не вытесняет другого owner и не объявляет rematerialization выполненной; persisted earned heading/checkpoint/receipt неизменны при retry и stop; после освобождения capacity создаётся реальный новый native epoch.

## Минимальная правка и границы

Один product owner: в `finishRecoveryStore` только CAPACITY_REACHED возвращает RETRY. Существующий accepted control сохраняет свой original deadline и продолжает штатный polling; closure заканчивает уже подтверждённый store без нового native lifetime. Проверка store и прочие terminal failures прежние; sticky failures не очищаются. Продолжительное насыщение по-прежнему может исчерпать original deadline.

Изменённые tracked пути (относительно High Five module):

- P=`java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundService.java`: три добавленные строки, включая объяснение причины.
- E=`test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java`: exact049a owner/root/CWD/JDBC/exportSHA guard по прежнему045 паттерну.
- T=`test/java/org/l2jmobius/tests/phantoms/PhantomLifecycleCompletion027Suite.java`: S06/S07 focus `capacity049`, прежняя acceptance11 без изменения assertions.
- R=`docs/phantoms/reports/PHANTOM-M1-SHUTDOWN-049.md`: этот единственный отчёт.

Read-first: пакет049 из MAIN read-only; module Agents/master/workflow/task standard/release contract; root README/module readme.txt; отчёты045–048, raw048, build.xml и actual stop/recovery owners. Локальные аналоги: recoveryControl/configure/callback в lifecycle027, native handoff fixture, exact045 DB lane. Root AGENTS.md и module README.md отсутствуют; отдельный актуальный shutdown code-map/pattern файл не найден. Архитектура/JDK25/Ant1.10.17/WinPS5.1 и scope сохранены.

## Проверки и изоляция

S0=PASS: initial tracked clean, HEAD=BASE=remote, Java census0. Только новая `l2jmobiush5_phantom_test_049a`/contract049a из immutable stopped048 SQL; SHA256 `7ddf752822d3b163695c3f61ff3614c508faced47ae366a404b9ef6ad7d24c71`; read-before-create, один import, actual JDBC49 и pool4. SQL reset/heal/reimport и обычных Game/Login boots —0. Fixture writes только049a.

Первая matrix91/94, exit1: owned-store LIVE_PREPARE RETRY и sustained F01/F10 TERMINAL при `GeoEngine: Loaded 0 regions`. В подготовке049a был пропущен существующий geodata input. Восполнены203 immutable048 regions,203 SHA совпали; исходные configs/assertions/JAR не менялись. Targeted исправленной fixture: owned-store1/1, sustained3/3. Исходные FAIL-логи сохранены.

Итоговая jar-first matrix:94/94,18 routes, exit0 (исходные93 + добавленный ранее ecology046 test). Acceptance11/11, exit0, включая S05 original deadline/retained running native callback. Подтверждены original assertions, native stopped/negative routes; общий native24/P07 не запускался.

Ant `-Dbuild=<module>/.phantom-local/ops049/build jar compile-tests`: exit0; две старые runFinalization deprecation warnings. jar-first GameServer SHA256 `7662ee95f2286f32f9c7b366159006c8cde329d7db52c6dbaf1cbd7d10b9bfd8`. Runner переиспользован из Verify045; никаких новых collectors/product seams. Команды: `Verify049.ps1 -Action Test -Suite PhantomLifecycleCompletion027Suite -Focus capacity049`, `-Action Matrix`, `-Action Test -Suite PhantomLifecycleCompletion027Suite -Focus acceptance`; все из OWN, labels/logs в `.phantom-local/ops049/test-output`, classpath starts with frozen current JAR, без development-bin-first.

S1=VERIFIED; S2=VERIFIED; S3=NOT_RUN. Новый ordinary049 stock Start→Stop/all-birth SQL/RUN_END не выполнялся; fixture typed DONE не переносится на full native run. Original048 остаётся FAIL:1295 terminal births/119952 commits, FULL_SAVE=true, FINAL_STOPPED_CUT=true, GLOBAL_DRAIN=false, RUN_END dropped0/failure empty.

Финальные read-only checks:206 protected040a/047a файлов — unchanged; SHA original048 lifecycle/log/stopped SQL совпали с preimage; Java census0. Tracked scope — только P/E/T/R. Публикация не переписывает original048 FAIL.

TTL/caps1280/8/8/rates/clock/45/90/120/525/stop deadlines, CAS/epochs/identity/earned protocol и рабочие configs не менялись. MAIN/PLAY/040a/старые БД не изменялись; старые SQL schemas не использовались тестами. P07/Zariche/G4/M2 не выполнялись. Private049 fixtures/DB/JAR/logs остаются локально.

- mojibake-маркеры в изменённых файлах проверены.
- escaped Cyrillic в изменённых файлах проверены отдельно.

Git разрешён прямым GOAL/TASK049 для pin/scope/exact-path publication. Использованы: `git status --short`; `git status --porcelain=v1 --untracked-files=no`; `git rev-parse HEAD`; `git rev-parse --abbrev-ref HEAD`; `git ls-remote origin refs/heads/experiment/m1-candidate007-observe008`; `git diff --stat`; `git diff --numstat`; `git diff --name-only`; `git diff --check`; scoped `git diff -- P E` и `git diff -- T`. Publication из OWN root, P/E/T/R с prefix `L2J_Mobius_CT_2.6_HighFive/`: `git add -- P E T R`; `git diff --cached --name-only`; `git diff --cached --check`; `git diff --cached -- P E T R`; `git commit -m "phantom(task-049): retain recovery on rematerialization capacity" -- P E T R`; `git push origin HEAD:refs/heads/experiment/m1-candidate007-observe008`; затем `rev-parse HEAD`, `ls-remote`, tracked status. Force/reset/restore/clean/branch/merge/rebase не использовались; чужие untracked сохранены. Final SHA этого атомарного commit, push и HEAD=remote/clean подтверждаются в финальном ответе без самоссылки отчёта на будущий SHA.

Следующий минимальный evidence gap: отдельно разрешённый ordinary current49+ run с typed DONE/COMPLETE до pools и строгим all-birth/stopped SQL/RUN_END. Автоматически не запускать; M1 остаётся OPEN.
