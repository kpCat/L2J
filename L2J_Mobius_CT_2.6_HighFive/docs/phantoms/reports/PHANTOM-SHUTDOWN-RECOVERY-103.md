# PHANTOM-SHUTDOWN-RECOVERY-103 — PARTIAL103 / BLOCKED

28.10.2026. Работа над native campaign остановлена на втором независимом product blocker. **JVM не остановлена: PROCESS_RETAINED.** Узкая правка cleanup dispatch имеет behavioral DB-free RED→GREEN, но native logout STOP не достигнут. Не запускались Probe32, финальный128, restart, targeted retry или TASK104.

## Результат по gates

| Gate | Факт |
|---|---|
| PROCESS103 | При G0 прежних Java-процессов нет, exact OWN102 SQL sessions0. После единственного native Probe16 retained OWN103 JVM1, own SQL sessions4, игровых listeners0 |
| CAUSE103 / ROOT103 | Source starvation mechanism подтверждён bounded actual-service/model-callback RED. Момент насыщения historical OWN102 не наблюдался напрямую; исключительная причина старого incident не доказана |
| PATCH103 | DBFREE_GREEN3/3; production compile/JAR PASS. Actual earned logout callback и native cleanup после исправления **NOT_REACHED** |
| OWN1025000 | До start verified5000 distinct ordinary READY и точное сохранение предыдущих IDs/init receipts. Нет нового создания5000 или reseed; post-STOP progress continuity **NOT_PROVEN** |
| STOP103 | **BLOCKED_BEFORE_STOP**, current process retained; нет clean receipt или stopped103 checkpoint |
| RESTART103 | **NOT_RUN**; sameDB/sameJAR сохранность earnings/stock/receipts после clean STOP не утверждается |
| WORLD103 | Actual World2 в последней завершённой выборке, peak3 — другой счётчик. W16 gate не достигнут; W32/W128 NOT_RUN; normal independently ACTIVE/farm не доказаны |
| OWNER103 | Source и отдельная physical parity-копия PASS,287 overrides и True/5000/128/128 сохранены. Final runtime128 NOT_REACHED: retained JVM работает на явно обозначенном private cap16 overlay |
| REVIEW103 | Independent read-only review production/test/tool scope: оставшихся P1/P2 нет. Native acceptance остаётся BLOCKED |

Исторический TASK102 остаётся PARTIAL/STOP_FAILED. Отсутствие старой JVM не превращено в CLEAN STOP102; продолжение явно обозначено **UNCLEAN_RECOVERY**.

## Provenance, read-first и scope

Start live/local `feature/phantom-world`: `575b5c8d1100101ed9d929241221b6735910cd31`. Fetch и свежий ls-remote подтвердили равенство. Tracked dirt отсутствовал; существующие untracked task packages/private материалы не staged и не переписывались. master/default branch не менялись.

Прочитаны TASK103 и все companion files/manifest; root/module AGENTS, README, bounded status/workflow/master-plan/package-standard; отчёт102. Причинный read-set: PhantomNativeWorkScope, PlayerNativeWork, NativeEventWork, Player cleanup, PhantomMaterializedPlayer, PhantomMaterializationService, PhantomSystem, ThreadPool; дополнительные EventDispatcher, actual Nevit logout listener, Native102/Task102Run/Lane и private evidence. Аналоги: requestCleanup, onQuiescent retry, queued-work/scope/dispatch tests, existing sealed TASK102 lane и OwnerParity.

AGENTS выше repo и в docs/phantoms не найдены; module README отсутствует; отдельные code-map/pattern файлы не найдены. Старые пути/статусы master-plan не использовались вместо текущего root/task authority.

**Процессное отклонение:** первоначальный лимит READ_SET был превышен: дополнительно прочитаны safety/authority/DB probe/owner-parity и тестовые fixture APIs для переиспользования существующего runner. Это не выдаётся за соблюдение лимита12+5. Production scope при этом остался одним файлом; новый engine/layer/dependency/worker не добавлялся.

Exact public allowlist — шесть файлов:

1. `java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializationService.java`.
2. `test/java/org/l2jmobius/gameserver/phantoms/player/PhantomShutdown103Checks.java`.
3. `test/java/org/l2jmobius/tests/phantoms/PhantomPopulation102Native.java`.
4. `test/java/org/l2jmobius/tests/phantoms/PhantomTask102Lane.java`.
5. `tools/phantom-runtime/Task103Run.ps1`.
6. Этот отчёт.

Все пути относительно High Five. Config/semantic/Studio/build.xml/DB schema и population/ecology/System production-файлы не изменены. До правки production побайтовый preimage имелся в прежнем external shadow; SHA сверена с текущим preimage. Новая physical external shadow создана copy-only штатным Prepare, owner+SYSTEM ACL, без reparse. Оригинальный OWN102 stage не изменялся.

## G0 и старый logout incident

Elevated `Get-CimInstance Win32_Process` показал Java0. Historical PID использовался только как текст прежнего receipt; stale PID не был целью jcmd или process control. Прочитаны private retained-process, original stage reference, stop4 и сохранённый thread dump. Старый stop4: FAILED/retained1, PRE_STORE/NATIVE_TEARDOWN, outstanding EVENT:ON_PLAYER_LOGOUT; stopped4.checkpoint отсутствует.

Штатный read-only Task102DatabaseProbe diagnose подтвердил full metadata visibility, точный durable UUID/sourceHead/input SHA, ownSessions0, managed/READY/distinctCharacters5000. Pending catchup4986 — не normal ACTIVE readiness. Первый вызов из неправильного cwd был отклонён до JDBC; исправленный exact staged cwd прошёл exit0. Никакой guard не отключался.

Causal chain: requestShutdown массово вызывает requestCleanup → blocking cleanup tasks занимают instant pool → terminal native teardown резервирует EARNED logout → NativeEventWork/PlayerNativeWork отправляет callback в тот же instant pool → drain ждёт callback, которому не осталось worker. Unbounded queue не вызывает расширения executor сверх core32. Historical log содержит множество timeout около одного10s deadline; поздний dump уже показывает32 свободных workers. Diagnostics OFF: точные historical RESERVED/RUNNING, enqueue/start/bodyExit timestamps и queue size в момент отказа **UNAVAILABLE**; truncated incident этого не доказывает.

Focused regression использует настоящий service/dispatch и два test-owned instant workers с DB-free partial actors. Заблокированные cleanup bodies моделируют ожидание earned callback. На неизменном старом JAR: **RED**, active2/queue1, callback не входит. Это модель callback, не подмена actual native Player event proof.

## Минимальная правка и writer audit

Один общий dispatch slot охватывает обычный requestDematerialize и requestShutdown. Перед shutdown dispatch закрываются admissions всех доступных actors. Closing phase защищена от конкурентного pulse; stale Entry после подготовки повторно проверяется. Следующий cleanup публикуется сразу после завершения предыдущего, вне monitors, с первым monotonic deadline. Ошибка/неизвестный result фиксирует FAILED до освобождения slot; submission RuntimeException/Error освобождает slot. Deadline10s не увеличен.

EARNED reservation/start/completion, asynchronous EventDispatcher traversal, Nevit и остальные listener bodies не менялись. No-listener/listener-throws/stale-epoch actual-event негативы **NOT_RUN**. Native ticket нельзя отменить, списать или заменить fake completion. Обычные non-Phantom события сохраняют прежний dispatch.

Lock order новой dispatch-части: state→Entry; callbacks/Player/native waits выполняются вне этих monitors. NativeWorkScope monitor и его wait/notifyAll, Player/World/outbound/identity/Geo ownership, autosave removal, storeMe/JDBC и OfflineTrader writers сохраняют существующие владельцы. После cleanup по-прежнему нужны реальные canonical Player postconditions до lease release; fail-closed release не ослаблен.

Synchronous cleanup, legacy shutdownPass и scheduled onQuiescent retry обходят новый request slot, как раньше; это не новый универсальный concurrency contract. Single-worker instant pool данным решением не лечится; фактический OWN102 core32. Эти ограничения не скрыты за claim общего lifecycle PASS.

Independent review выявил гонки initial-close/detach, stale preparation и legacy STOPPING deadline; они исправлены в том же causal slice до native launch. Отдельный тест защищает отсутствие публикации при concurrent close. Новых timers/threads/pools в production нет.

## Единственная native campaign и второй blocker

План до первого launch: Probe16 → clean STOP → Probe32 → clean STOP → final128 → clean STOP → sameDB/sameJAR restart. Все phases используют прежние5000, small overlays меняют только private active/cap16/32; target5000 и source128 неизменны. Manifest поколения и source INI/JAR pins сохраняются; каждый следующий phase требует предыдущий completed+drained receipt. Используются только существующие labelled test humans; новые ordinary profiles/accounts не создаются.

Выполнен **только Probe16/recovery6**. Перед native повторно Java0/ownSessions0; lane дополнительно проверяет full PROCESSLIST0. В новом shadow сохранены исходные durable OWN102 UUID/sourceHead/input SHA; original input inventory отделён от current repaired native manifest. Изменились только location/expiry копии private owner metadata; исходная authority table/receipts неизменны.

Native census до producers подтвердил прежние5000 READY/IDs/init. Before6 checkpoint содержит5000 character rows,52925 inventory rows,34490 component rows,0 recipebook rows. Поля characters включают EXP/SP; components включают receipt versions/payload. Пустой recipebook не доказывает положительный рецепт-сценарий. Сравнение после clean STOP/restart не выполнялось.

Последняя завершённая выборка16:42:05 UTC: actualWorld2, source target5000/private active16/cap16, creationClaims0/peakCreationClaims0,250 merchant owners отдельно исключены,4 reused controlled humans. Это не green16 и не normal ACTIVE.

После отсутствия новых sample exact текущая JVM сверена по owner, creation time, command, stage UUID и JAR; лишь затем выполнен read-only jcmd Thread.print. Он прямо сообщил **Found1 Java-level deadlock**:

- ScheduledThread1 удерживает PopulationManager monitor через processReadySchedule и ждёт Ecology.permitsScheduling.
- High Priority ScheduledThread8 удерживает Ecology monitor в registerDue и через localityPreparationCandidate ждёт Manager.admissionProfile.
- Main census ждёт Manager, удерживая PhantomSystem.class; shutdownIfStarted также должен получить этот monitor.

Exact diff Manager/Ecology/System пуст. Это отдельная существующая lock inversion, возникшая **до** logout listener registration и до STOP. Её исправление вышло бы за один causal cleanup slice и второй-blocker gate; production продолжение и targeted retry не выполнялись.

Конечный read-only census с exact durable authority/full visibility: **own JVM1, own SQL sessions4, established own MariaDB TCP4, own game listeners0**. Поддерживаемого graceful control для этой incarnation нет. Старый RequestShutdown требует phantom.localplay identity и mutating agent; он не запускался. Нет kill/CTRL-C/agent/JMX/hot patch/service stop/lock removal. Shell session retained; отсутствие завершения не выдано за exit0.

## Проверки и команды

| Проверка | Exit / результат |
|---|---|
| Initial sandbox OS census / fetch | access denied; повторены через authorized elevation, не считались успешными |
| Fetch/remote parity/ancestor |0; live=local575b5c8d110 |
| Original read-only diagnose wrong cwd |1, pre-JDBC rejection |
| Correct original diagnose / new-shadow preflight |0; full visibility, exact authority, ownSessions0, READY5000 |
| Focused test на historical6435 JAR |1 expected RED, active2/queue1 |
| External Ant compile-tests jar |0; последовательные builds по42s, source dist/libs не затронут |
| Final focused dispatch |0,3/3: callback worker, expired no-dispatch, concurrent close/legacy STOPPING |
| Affected materialization compatibility |0,1/1 existing scale-materialization-goal029cp1 |
| Native config/contracts |0; effectiveTrue/5000/128/128, invalid bounds/cohort churn/checkpoint drift rejected, JDBC0/World0 |
| OwnerParity |0;68INI/287overrides; disposable Apply changed1→Verify0→repeat Apply0; invalid Apply expected1 |
| Seal |0; current JAR/current native manifest/original DB authority pinned |
| Native Probe16 |PROCESS_RETAINED, без shell exit; World2 then independent deadlock |
| jcmd текущей точно проверенной JVM |0; initial manifest-check dump и later deadlock dump |
| Retained SQL census |0; sessions4, full visibility, DDL/DML0 |
| Probe32 /128 / restart / actual logout |NOT_RUN / NOT_REACHED |

Операционные команды использовали реальные paths из private stage.json:

```text
powershell -File tools/phantom-runtime/Task102.ps1 -Action Prepare -SourceRoot <source> -StageRoot <new-shadow> -InstallationId <shadow-id>
ant -f <new-shadow>/L2J_Mobius_CT_2.6_HighFive/build.xml compile-tests jar
javac -encoding UTF-8 -cp <sealed-cp> -d <test-bin> <Native102.java> <Lane102.java>
java -cp <old-cp> org.l2jmobius.gameserver.phantoms.player.PhantomShutdown103Checks
java -cp <fixed-cp> org.l2jmobius.gameserver.phantoms.player.PhantomShutdown103Checks
java -cp <fixed-cp> org.l2jmobius.tests.phantoms.PhantomPopulation102Native config <shadow-module>
java -Dphantom.module.root=<shadow-module> -Dphantom.test.reports=<private> -cp <fixed-cp> org.l2jmobius.tests.phantoms.PhantomTestLauncher scale-materialization-goal029cp1 10310301
powershell -File tools/phantom-runtime/Task102OwnerParity.ps1 -SourceRoot <source> -StageRoot <new-shadow>
powershell -File tools/phantom-runtime/Task103Run.ps1 -Action Bind|Seal|Probe16 -SourceRoot <source> -StageRoot <new-shadow> -OriginStage <original>
jcmd <exact-validated-current-pid> Thread.print -l
```

Bind/Seal/Probe16 — три отдельных вызова. Полные literal shell commands/exits и private PID/port/JDBC evidence сохраняются приватно, без credentials в отчёте. Общий ant verify/чужие DB suites/PLAY/MAIN/release057–061/новый TEST103 не запускались.

## Сохранность, hashes и performance

Protected source169 config/semantic/conversation hashes: drift0. Original Geo203: drift0. Original JAR6435 неизменён. Exact public source/stage пять code paths равны. Source OwnerConfig/INI сохранены; runtime overlay16 не promoted. Финальные Apply→Verify→repeat Apply выполняются на отдельной parity/game physical-копии, а не на live retained runtime.

| Artifact | SHA256 |
|---|---|
| Original JAR |6435bd737e3619c62c97bbc750c69a8c40a971ec0b7e64960d9e9857c0afaa7c|
| Fixed external JAR |98e5849527b6f73e4ecd28279ba962440d92f9888d771fd00660d0d4bdceecc5|
| Service preimage |5a8446c5ebdb934f8f00076341e62ab7b270cb72d3816652e507a7c491451917|
| Service changed source |d5601812acc4c2b925cb3627eb527bcffeeccb1654b77db8841e3cb281c09c3e|
| Source OwnerConfig |5776e83810689da1acf94d0a3d8ee54d204e87ca0bf7488e685034b57c61f4e8|
| Source PhantomPlayers.ini |55043b107c918d5232b838cb56583ab3dfda2b4530b2c5321fddf35c660180b2|

Native last completed sample: heapUsed2950377296, heapCommitted4622123008 bytes, threads125, GC55/730ms. Это единичная выборка, не тренд/p95/max. Logout queue latency, cleanup duration/stop p95 и SQL latency **UNAVAILABLE**, поскольку STOP не достигнут. Новых production per-actor timers/workers нет; throughput не улучшался отключением callback или увеличением timeout.

Mojibake-маркеры в изменённых файлах проверены: все27 маркеров, шесть exact public paths,0 совпадений.

Escaped Cyrillic в изменённых файлах проверены отдельно: все6 regex, шесть exact public paths,0 совпадений.

Exact-file scope guard: foreign tracked changes0; diff --check PASS. Существующие untracked материалы не включаются в staging. Private text-check helper сначала имел PowerShell parse error на smart-quote внутри marker list; исправлен literal here-string, обе реальные проверки затем прошли exit0. Parser failure не выдаётся за успешную проверку.

Финальный independent read-only code/report review: P1=0/P2=0 в представленном scope. Reviewer отдельно сверил deadlock, численные receipts и ACL canonical private index; отсутствие замечаний к patch не превращает retained runtime в healthy acceptance.

Goal usage snapshot16:48:04 UTC:489097 tokens,1880 elapsed seconds; это снимок до final review/publication, не обещание финального usage. Финальный usage/elapsed и commit/remote SHA записываются в private PUBLICATION103.json и финальном ответе.

## Delivery и безопасный handoff

Private evidence index: `.phantom-local/logs/PHANTOM-SHUTDOWN-RECOVERY-103/stage.json`, retained103.private.json, raw jcmd, source/build/RED/GREEN/parity/seal logs и receipts103. Они не входят в commit. Рабочий scratch `.phantom-local/task103` также private/untracked. Public report не содержит точного PID/UUID/command lines/credentials.

Git разрешён текущей task authority для bounded inspection и exact-file delivery. Использованы: `rev-parse --show-toplevel`, `rev-parse HEAD`, `symbolic-ref --short HEAD`, `status --short`/`--untracked-files=no`, `worktree list --porcelain`, `for-each-ref` exact feature refs, `merge-base --is-ancestor 575b5c8d110… HEAD`, `fetch origin feature/phantom-world`, `rev-parse HEAD refs/remotes/origin/feature/phantom-world`, `merge-base --is-ancestor origin/feature/phantom-world HEAD`, `ls-remote origin refs/heads/feature/phantom-world`, exact-path `diff --stat/--numstat/--check/--`. Reviewer применял только exact-path diff.

Publication: только `git add -- <шесть exact paths>`, staged name/stat/check inspection, `git commit -m "phantom(task-103): recover native logout shutdown"`, `git push origin HEAD:refs/heads/feature/phantom-world`, свежий local/remote SHA proof. Literal commit/push outcome хранится вне собственного commit, чтобы не создавать невозможный self-hash. Ни force/reset/rebase/restore/stash/clean/StageAll, ни branch/history changes не выполняются.

**Handoff:** сохранить exact OWN103 JVM и четыре SQL sessions; не запускать на этой DB другой native runtime и не применять RequestShutdown/kill. Следующая отдельная owner-authorized recovery должна сначала сверить private identity/creation/JAR и разобрать Manager↔Ecology deadlock и поддерживаемый graceful path. До этого нет clean checkpoint, native logout acceptance или restart proof. Оригинальный OWN102 stage/receipts сохраняются read-only. В этой задаче дальнейших fixes/campaign/retry/TASK104 нет.
