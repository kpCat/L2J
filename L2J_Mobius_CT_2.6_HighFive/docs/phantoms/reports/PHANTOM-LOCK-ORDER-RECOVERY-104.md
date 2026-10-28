# PHANTOM-LOCK-ORDER-RECOVERY-104

Итог: **FULL104=PARTIAL; LOCK104=PASS; полный STOP104=BLOCKED; RESTART104=NOT_RUN**. Выполнено только поколение A/cap16, actual стабильный World16/16. Новая OWN104 JVM retained после финального lease assertion. Ни B/C/retry, ни TASK105 не запускались. RELEASE_READY и NORMAL ACTIVE128 не заявляются.

## Authority и G0

Фактический root определён через rev-parse; feature/phantom-world и upstream origin/feature/phantom-world подтверждены. Свежий origin SHA равен local `20b791612e56df7de6b8e1d53b1c2ebd2c0f7971`; known ancestor не использован как неизменяемая authority. Origin соответствует kpCat/L2J. Tracked foreign dirt отсутствовал; untracked task packages, старые отчёты и private WIP сохранены.

Два независимых OS capture сверены с private OWN103 process receipt: creation UTC, owner SID, executable, полная command line, physical stage/UUID и JAR. Свежий read-only jcmd Thread.print -l, exit0, подтвердил Java-level Manager↔Ecology deadlock. SQL census с полным visibility: own sessions2, foreign0. Поддерживаемого graceful control для этой incarnation нет.

До force вне checkout сохранены owner+SYSTEM-only metadata, logs, старые и свежий thread dumps, TCP/SQL evidence и durable preimage; SHA readback выполнен. После предъявления redacted provenance владелец отдельно ответил: «Разрешаю однократный targeted force только этой exact OWN103 JVM». Перед командой повторены OS/Get-Process/creation/SID/stage/JAR/SQL и archive hashes. Выполнен ровно один Stop-Process -Id <verified-own103> -Force, exit0. Никаких agent/JMX/горячих исправлений, tree/all-java kill или MariaDB restart.

Результат **FORCED_UNCLEAN**, не graceful STOP. Мгновенная выборка ещё видела завершающийся процесс; следующий независимый postflight доказал JVM0, old TCP0, own SQL0, foreign SQL0. Все шесть durable slices совпали до/после force; исходные5000 ordinary READY, уникальные character IDs, account joins и initialization receipts совпали с original102 IDs.

## Read-first и scope

Прочитаны root/module AGENTS, TASK104 и AUTHORITY/CONTEXT/READ_SET/ARCHITECTURE/PROCESS_RECOVERY/SAFETY/TEST_PLAN/ACCEPTANCE/DELIVERY; bounded master plan, current status, workflow contract, package standard и один report103. Дополнительных вложенных AGENTS и отдельного code-map/pattern-файла в закрытом наборе не найдено.

Начальные12: Manager, Ecology, System, HumanLocalityControl, ReconcileFirstActivityPort, MaterializationService103, NativeWorkScope symbols, Readiness102, Native102, Task103Run, DatabaseProbe102, OwnerParity102. Дополнительные точные чтения нужны для SQL103 census, test doubles/Ecology033, launcher/seeds/DB-mode guards, state cursor, Prepare102, performance suite, Lane/Authority102, lease registry и Scheduler callback boundary. Исторические102/103 packages и полный Player не читались. До первой правки выполнено8 rg-команд: лимит6 превышен на2; два широких вывода были усечены. Это отклонение процесса, не скрытое успешной приёмкой.

Локальные аналоги: updateMaterializationDemand с native lookup вне monitor; readinessRevision и retry-action ownershipGeneration; guarded102/103 original-authority/native-inputs/checkpoint lane. Java25/Ant и существующий стек сохранены. Exact public scope8 файлов: два production owners, Lock104Checks, Native102, Lane102, Task104Census, Task104Run и этот отчёт. Player/ThreadPool/NativeEventWork/cleanup patch103, schema, INI/JSON, Semantic Pack и Studio не изменяются.

## Причина, исправление и lock graph

RED на неизменном JAR103 воспроизвёл обе стороны через настоящие processReadySchedule и requestMaterializationDue, controlled monitors и bounded workers: Manager блокировал свой reader, ожидая Ecology; Ecology блокировала свой reader, вызывая demand→Manager. Exit1, два ожидаемых failure; тестовые JVM не оставлены зависшими.

Manager получает краткий scheduling snapshot вне своего monitor. Под monitor проверяются identity, ownership generation, lifecycle и volatile revision; stale означает defer/requeue, aggregate eligibility/admission тоже fail closed. Снимок создаётся при dirty/stale/day/rotation, а не на каждом pulse. Он не является новым worker или долгоживущим ecology cache. Non-periodic snapshot истекает при смене минуты; periodic horizon invalidates через существующего writer.

Ecology вызывает currentDemand вне monitor и повторно сверяет entry, readiness revision, demand epoch, scheduling revision, participation, STOP и target minute. Demand-only refresh не отзывает ordinary admission. Scheduling invalidation охватывает register/remove, stored/CAS publication, participation, startup plan, metadata draining, requested horizon, periodic mode и STOP. Native ownership capture для STOP также вынесен наружу; незавершённый capture блокирует finishStop.

| Owner / writer | Boundary и fencing |
|---|---|
| Manager entries/state/schedule/admission/presence | Собственный monitor; Ecology capture/registration/participation/archive вне него; под ним только volatile snapshot-current. READY проверяется повторно после topology callback. |
| Ecology stored/horizon/due/cursor/receipt/stop | Собственный monitor; demand, PopulationView/Events, metadata observer, persistence/history/native callbacks вне него; прежние request IDs, revision/CAS, claims, budgets и retry semantics сохранены. |
| System / Locality | System.class→Manager для census остаётся; обратного Manager-monitor→System callback нет. onPulse публикует физический demand отдельно от ordinary eligibility. |
| Scheduler | controlPort.onPulse вызывается после освобождения scheduler monitor; submit/register/reload из Manager остаются снаружи Manager monitor. |
| Materialization / Player / NativeWork / DB | Dispatch103, EARNED tickets, cleanup/store ownership и fail-closed lease release сохранены; DB callbacks не перенесены под population/ecology monitor. |

Существующие WakeScheduler/clock/cancel bookkeeping callbacks остаются локальными; production wake делегирует общему ThreadPool. Новых scheduler/timer/threads, tryLock/sleep в production или обхода ecology/Geo/observer/native ownership нет. Exceptions внешнего demand не превращаются в positive receipt; stale publication возвращает ecology.readiness_stale. Исходный jar103 logout patch не объявляется native-проверенным до кампании.

## Проверки и независимое review

| Gate / команда | Фактический результат |
|---|---|
| Old JAR Lock104Checks | RED exit1, две стороны actual owner методов |
| Focused javac current owners + Lock104Checks | GREEN4/4; два направления по8; stale/withdraw/pause/startup/pending/archive/STOP/minute negatives |
| Ecology-enabled5000 |1024 pulses, admitted128, population budget≤64, elapsed384ms на final JAR; synthetic DB-free, не World128 |
| Readiness102 / Shutdown103Checks |3/3 +3/3, JDBC0; cleanup callback модель, не native logout proof |
| Existing population-schedule / performance |3/3 +3/3, DB-free |
| Ecology033 / handoff subset |35/36: единственный historical config assertion ожидает enabled=false при сохранённом owner=true; targeted handoff6/6 PASS |
| Native102 config / lease negative | Bounds, ordinary admission, cohort churn, replaced lease token и checkpoint drift REJECTED; JDBC0 |
| OwnerParity102 |68INI/287overrides, source writes0; disposable Apply changed1→Verify0→repeat Apply0; invalid Apply expected rejection1 |
| External Ant compile-tests jar |exit0,47s; только external build/dist/libs, исходные JAR не перезаписаны |
| Final-JAR focused | Lock4/4, Readiness3/3, Shutdown3/3, config/lease contracts PASS |
| Bind fresh-shadow guard | RED disposable bound-metadata fixture mutates; GREEN expected OWN104_FRESH_PREPARED_SHADOW_ONLY,4 files byte drift0; реальный fresh Bind exit0 |

Independent read-only reviewer выявил startup publish-before-register race, unchanged-demand invalidation, aggregate stale census, unconditional READY copying, temporal expiry и Bind protection. Для дефектов выполнены focused RED→GREEN в том же causal slice. Последнее source review: неразрешённых P1/P2 нет. Отдельный guard запуск сначала был отклонён parser Windows PowerShell5; это не засчитано guard PASS. После исправления continuation получено конкретное expected guard rejection и положительный fresh Bind.

Другие неуспешные orchestration попытки: неверный Goal016 seed отклонён before-all; generic population-reconciliation отклонён cwd guard до JDBC и больше не запускался; performance из root не нашёл relative catalog, затем из module cwd3/3. Полный ant verify и generic DB suites не выполнялись. Default diff --check отметил CRLF как trailing CR в существующем смешанном файле; CRLF-aware blank-at-eol/eof/space-before-tab check exit0 без массового переформатирования.

## Protected data и hashes

Исходные170 config/semantic/conversation hashes: drift0. Source OwnerConfig True/5000/128/128 и все287 overrides сохранены; source/private overlays не promoted. New shadow физически отдельная, owner+SYSTEM ACL,29039 input pins и203 physical Geo copies. Original102/103 stages, reports, authority и receipts read-only.

| Artifact | SHA256 |
|---|---|
| Retained103 JAR |98e5849527b6f73e4ecd28279ba962440d92f9888d771fd00660d0d4bdceecc5|
| Current external104 JAR |ea50825c01cb1267b78c10dae2eb48ad8c945e4840f6a842d639d13dd2709a70|
| Manager source |b1238bc3aaa06a9bd46319cc7bdd474e70fefb102b64a41f54495dc27bd0d859|
| Ecology source |2cdbd2e6d6a8291aec1b2a11e4698583e5a03f3a6ab3dcffc41c335df0ceb414|
| Preserved cleanup103 source |d5601812acc4c2b925cb3627eb527bcffeeccb1654b77db8841e3cb281c09c3e|
| Original5000 character/EXP/SP slice |6a8859b9a6767f4b21f07ed6ceaabf13fb9f38d3223e8800f78f5f62a28eaa89|
| Original52964 inventory rows |04fb0418139da39b79b58d7809c4cc9d1f78c36a5147c9809efed7ab4bb8f0fa|
| Original34490 component/receipt rows |7477d7bfd72f0a0e4620a6015970dcc6937c64663b94379e709a09e86891c000|

Recipebook0; merchant offline owners187/items1446 сохранены до/после force. Положительный recipebook scenario не заявляется. Fresh source/JAR equality, native commands, original UUID/PID/credentials/full dumps и before/after rows остаются только в private evidence.

## Native campaign checkpoint

План разрешает только104A(cap16)→полный clean STOP→104B(cap128)→полный clean STOP→104C(sameDB/sameJAR cap128)→полный clean STOP. Старые5000 используются без рождения/reseed/DROP. Native context handoff не равен admitted NORMAL ACTIVE; partial World обязательно количественно квалифицируется. Wrapper требует process/TCP0, full-visibility SQL0, durable authority, stopped checkpoint, successful exit и World/lease0 receipts между поколениями. Максимум один узкий retry только после clean STOP; автоматического force в lane нет. При новом retained runtime — STOP и bounded external diagnosis.

Запущено **только A / recovery104A / phase6** после committed source checkpoint и Seal: прежняя OWN102, JAR ea50825c…, cap16. Preflight full SQL visibility/own0/foreign0; исходные5000 READY/IDs точны. Ramp creationClaims0/peakCreationClaims0. World0→16, затем два actual snapshots16/16 через30s; проверены одинаковые Player reference, objectId, epoch, exact lease token и native observer guards. Последняя population выборка: desired3422/eligible3/admitted3, cap16; World16 нельзя назвать16 NORMAL admitted ACTIVE.

PhantomSystem shutdown snapshot: configured=false, retained materialization0, population STOPPED; остальные опубликованные core owners отсутствуют либо STOPPED/0. Actual ON_PLAYER_LOGOUT callback имел RUNNING EARNED ticket/current owner; проверка calls==1 и outstanding==0 прошла. System drain занял1208ms; final instant executor active0/queued0. Это успешный subsystem drain, не полный process STOP.

После удаления controlled observers/закрытия их leases цикл проверил все5000 ordinary character IDs: World player absent и ownerKind null. Следующая проверка global getActiveLeaseCount()==0 завершилась **AssertionError: OWN104 STOP retained leases**. Точное положительное число global leases не записано; их owner/provenance не установлены. Это отдельный blocker; не приписывается автоматически merchant/background и не исправляется вторым production slice. Assertion случился до stopped6.checkpoint и ThreadPool.shutdown/DatabaseFactory.close; main failure оставил JVM живой. stop6-world-leases.txt и exit6.clean отсутствуют. phase6.complete/drained=true не использованы как разрешение следующего поколения.

Новый retained104 exact process повторно сверен с его собственным creation/executable/command/stage receipt. Read-only jcmd exit0, deadlock markers0. Первый retained census: own4/foreign0, JVM1, established MariaDB TCP4, game listeners0; финальный read-only SQL census позже: **own2/foreign0**, exact JVM1 остаётся. Согласие на force старой OWN103 не перенесено на новую104; нового force/agent/remote control нет. Shell nativeA не завершился, exit code недоступен. B/C не начинались; retry0.

Offline сравнение beforeA до infrastructure и первого retained snapshot:5000 character rows/52964 inventory rows, added0/removed0, identities/init receipts точны.75 character rows изменены: EXP у58, суммарно+18357; SP у58,+2110; level57,+57; отрицательных EXP/SP/level нет. Остальные изменения — HP/MP/CP, position/heading одного actor, online/onlinetime/lastAccess.58 item rows изменили только count: item57/Adena +2388, отрицательных quantities0; objectId/owner/item/gear attributes сохранены. Это наблюдённые durable runtime deltas; native/background causal split **NOT_PROVEN**. Ручной выдачи не выполнялось.

Различаются два baseline: read-only beforeA до infrastructure имеет character hash6a8859b9…; native before6.checkpoint после infrastructure, до PhantomSystem.start — `610ae43da6518cc6ce81b15f4e2ac346b6e64d7150536ce2b775246600dbf5a2`. Причина промежуточного character drift **NOT_PROVEN**; полное равенство этих baseline не заявляется. Inventory/components hashes этих baseline совпали. Original103 pre/post force шесть slices действительно совпали независимо от этой последующей границы.

Ordinary components34490→34503: deleted0,311 updated,13 новых supply.intent; updated background.catchup79/native-context96/background.state96/goal.runtime17/ecology21/supply.intent2. Row versions не уменьшились; initialization IDs неизменны. Merchant offline owners187 и items1446 сохранены, offline-items hash прежний; owner-row metadata hash изменился. Полная проверка merchant component quotas и отсутствие re-grant после restart **NOT_PROVEN**. Recipebook0; positive recipe scenario не заявлен. Private frame diff использует только уже снятые read-only snapshots и подтверждённый SQL column order, JDBC0.

После A hashes: character/EXP/SP `f5076f1b4e4a6bbf5dcab9816cfd32fbf7c63490c10be8334dc0e2d81c0b9118`; inventory `b2d868c489291440efcc40b09f03b4f75987a099b498d89dab074168c3beebe0`; ordinary components `54b07336746dfd91f4dab30e029cdc6559fa165b94e690dd2e87c3dac31c5d1e`. Полные rows/diffs/UUID/PID остаются private.

Actual final stable sample: CPU239437500000ns, heap used3531578656/committed4693426176 bytes, threads126, GC60/753ms. Это одна выборка; p95/max/SQL latency/long soak не измерены. Controller не применял auto-kill; внутренний native STOP deadline не заменяет external watchdog, если вызов сам блокируется.

| Outcome | Checkpoint |
|---|---|
| LOCK104 |PASS DB-free + independent source review |
| PROCESS104 |PARTIAL; recovery103 PASS/FORCED_UNCLEAN→0; новая104 retained JVM1/SQL2 final |
| DATA104 |PARTIAL;5000 identities сохранены, actual deltas проверены; clean checkpoint/restart/quota replay NOT_PROVEN |
| STOP104 |BLOCKED; subsystem drained, ordinary World/leases0; global leases>0, JVM1/SQL2 final |
| RESTART104 |NOT_RUN; полного prior clean STOP нет |
| WORLD104 |PARTIAL; actual16/16 при cap16, admission census3; NORMAL World composition NOT_PROVEN; cap128 NOT_RUN |
| OWNER104 |PARTIAL; source/stage parity PASS, loadedA True/5000/16/16 private overlay; loaded128 NOT_RUN |
| REVIEW104 |PASS в changed source/report scope; independent P1/P2=0; полный runtime STOP blocker сохранён |

## Delivery checkpoint

Git разрешён TASK104/AUTHORITY/DELIVERY для bounded inspection и exact-file publication. Использованы rev-parse root/HEAD/upstream, symbolic-ref --short HEAD, status --short/--untracked-files=no, worktree list --porcelain, remote -v, ls-remote origin refs/heads/feature/phantom-world, merge-base --is-ancestor known-ancestor HEAD, exact-path diff/stat/check. Sandbox network/CIM/private access сначала отказаны, затем authorized read-only escalation успешна.

Создан exact8-file source checkpoint `df484c2c6fe351f322876d9d49c74d392cf7ecaa`, subject phantom(task-104): repair population ecology lock order and recovery. Native запускался уже с committed source;7 code/tool source copies совпали по SHA со shadow. После кампании меняется только этот отчёт; для finalization используется report-only commit и обычный non-force push origin HEAD:refs/heads/feature/phantom-world после evidence review. Git add -- exact paths, staged name/stat и CRLF-aware staged diff --check выполнены. Ни add ., clean/reset/stash/restore/rebase/force/history rewrite, ни изменения master/default branch не выполнялись. Literal final commit/push outcome/remote equality и usage сохраняются в private PUBLICATION104.json вне собственного commit.

Mojibake-маркеры в изменённых файлах проверены:27 markers,8 exact files,0 совпадений; final report edit включён в delivery check.

Escaped Cyrillic в изменённых файлах проверены отдельно:6 regex,8 exact files,0 совпадений; final report edit включён в delivery check.

Private index: .phantom-local/logs/PHANTOM-LOCK-ORDER-RECOVERY-104/evidence104.private.json и retained104.private.json; полный внешний owner+SYSTEM archive, source preimages, manifests, RED/GREEN/build/parity/guard logs, native104A log, retained jcmd/SQL/data diffs и receipts104. Предварительный goal usage snapshot:420921 tokens/1947s; финальные usage/elapsed записываются в PUBLICATION104.json. **STOP: сохранить новую retained104 JVM; не запускать DB runtime/B/C/TASK105 и не применять старое force approval.**
