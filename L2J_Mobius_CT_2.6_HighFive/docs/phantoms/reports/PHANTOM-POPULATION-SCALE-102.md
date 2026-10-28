# PHANTOM-POPULATION-SCALE-102 — PARTIAL, STOP ON THIRD BLOCKER

28.10.2026. **FULL102=PARTIAL; CORE102=PARTIAL; WORLD102=SUCCESS (W128); OWNER102=SUCCESS.** Реально получены5000 distinct ordinary durable READY/native character IDs и128 одновременно настоящих World Player в двух выборках с тем же cohort через30с. Затем graceful shutdown **FAILED**: retained native cleanup, собственный JVM и SQL connections остались. Это третий независимый блокер; sameDB/sameJAR final restart **NOT_RUN**, S/R не пройдены. OWNER_CLIENT_QA_PENDING; клиентский вход и PLAYABLE не объявляются. TASK103 не запускалась.

## Authority, baseline и сохранность

Исходный live/local baseline: `9245f95aa6c4dd7c50df47c71cd68f2aa4b0b9d0`, feature/phantom-world. Первая безопасная PARTIAL-поставка опубликована ordinary push в commit `86743e2d11b16736565a36cd43fa59078ae29757`. Continuation начата именно с86743 и свежей проверки live, без возврата к старому ancestor. Чужие untracked task packages, Semantic Pack/OwnerConfig/Rate/QoL/WIP и worktrees сохранены. Master/default branch не менялись.

Первоначальный лимит campaign завершился вторым независимым блокером. Прямое последующее разрешение владельца расширило **только TASK102**: доказанный ecology/locality defect, focused RED→GREEN и **один дополнительный retry** на той же OWN102; третий независимый блокер означает STOP без нового запроса. Это не новая fresh DB/campaign и не неявное разрешение бесконечных повторов. Изначально сохранено фактически500 ordinary READY, а не5000; все500 проверены перед продолжением.

Единственная external physical owner+SYSTEM ACL shadow, без reparse; JDK Temurin25.0.4.1 и Ant1.10.17. Source/runtime disjoint. Native cwd — shadow HighFive/dist/game. Runner использует production composition, без LoginServer/GameServer listeners. Общая MariaDB loopback3308, её настройки/grants/сервис не менялись.

До первого DB/World metadata-only G0: полная visibility161 schemas, OWN102 ABSENT, foreignSessions0. Аудит121 stock SQL-файла:203 statements,18 исторических DROP исключены **до DDL**. Создана только `l2jmobiush5_phantom_test_102`; без DROP/reset/reseed/adopt/grants/foreign writes. Original own102_authority и source-origin inputs.tsv после CREATE неизменны. При continuation новые JAR/harness/phase inputs отдельно sealed; исходные manifests, RED receipts и побайтовые SHA preimages сохранены. Продлена только private authority expiry на3ч согласно прямому разрешению; UUID/sourceHead/inputSHA и DB origin прежние.

## Owner gate

Source OwnerConfig уже содержал четыре overrides; source Save не выполнялся. Канонический Source Verify:68 public INI,287 overrides,38 rules, conflicts0/changed0. Проверены именно сохранённые tuples: EnablePhantomSystem boolean False→True; PopulationTarget long0→5000; ActiveTarget long0→128; MaxMaterialized long32→128. Остальные owner overrides, burst0/scheduled10000/creation2/boundaries64/pulse100ms и starter lifetime0 сохранены.

В отдельной disposable baseline-копии четыре INI-ключа возвращены к base: Apply changed1→Verify0→repeat Apply changed0→Verify0, byte drift0. Invalid-type ActiveTarget отклонён с conflicts1/changed0; disposable INI восстановлен. Actual stage Apply→Verify до build и continuation прошёл changed0. Финальные Source Verify и stage Apply→Verify→repeat Apply→Verify: каждый exit0/changed0,68 файлов/287 overrides. Phase4 реально загрузила True/5000/128/128, burst0; runtime snapshot target5000/activeTarget128/materializedCap128,128 actual World. [Parity TSV](PHANTOM-POPULATION-SCALE-102/owner-config-parity.tsv) отличает saved/compiled/loaded runtime. Source OwnerConfig/PhantomPlayers.ini не переписывались.

| Source bytes | SHA256 |
|---|---|
| OwnerConfig.json |5776e83810689da1acf94d0a3d8ee54d204e87ca0bf7488e685034b57c61f4e8|
| Custom/PhantomPlayers.ini |55043b107c918d5232b838cb56583ab3dfda2b4530b2c5321fddf35c660180b2|
| OwnerProfile38.tsv |4f25d9419b1a01aff2792a29f09a6b12f6fc464314ace73419ea4590a6ad3244|
| OwnerProfilePreserved.properties |c49b6a7e3aa04f55c287dd28212ef9935b478aa78e31d74c2e6caa2d8712e9fc|
| Rates.ini |ba9c6fcbd364e09588f50a4d507d06fc9f21f0bfeaaba229977b44124db819f2|

## Исходные RED и подтверждённая причина

Первый phase500 остановился до ordinary births: NativeInfrastructure не загрузила canonical supported quest owner class.warrior-q401/profession. Existing069 явно вызывает InitialEquipment/InitialShortcut/MapRegion и PhantomSupportedContentScriptBootstrap.loadGoal036Owners. Те же calls добавлены только в TASK102 harness. Первый causal retry сохранил JAR/DB и создал500 реальных distinct READY/character IDs/registered, но устойчивый World0/0. Desired400, eligible0, admitted0, pendingCatchup499, localSignaled0, periodicDueCalls0; lastFailure=native_context.required:accepted. Оба исходных JVM завершены graceful, raw evidence не переписана.

На остановленной той же DB readonly diagnose установил:355 ACTIVE incomplete-catchup profiles находились в source-backed prewarm радиусе сохранённых6 test-human positions, всего445/500 рядом. Это **saved geometry replay, не live World evidence**. Фактический код замыкал цикл: locality candidates требовали ecology eligible; preparation — final admitted; readiness отклоняла !admitted прежде exact native-context handoff, который нужен ecology для завершения initial catchup. Законный current demand терялся ещё до native handoff.

Исправление только в3 production файлах:

- PhantomSystem: физический preparation candidate = READY+calendarOnline. Полная ecology eligibility/admission остаётся отдельной последующей проверкой.
- PhantomReconcileFirstActivityPort: ecology due проверяется прежде admission; только точный typed NATIVE_CONTEXT_HANDOFF может предшествовать normal admission. NORMAL сохраняет обязательный admission; calendar/locality/claim/lifecycle/Geo guards прежние.
- PhantomPopulationEcologyService: typed handoff в batch-mode также требует existing admittedPreparation, сохраняя budget8; нулевая preparation capacity не допускает обход.

Focused actual owners RED2→GREEN3/3. Отдельное возвращение старого ecology class в isolated test bin даёт RED на capacity0 negative. Негативы: NORMAL без admission, unavailable human, sleeping calendar, preparation0. MemoryStore checks — только regression contracts, **не native population evidence**. Spawn/teleport/setXYZ для фантомов, fake observer, cap/burst/scheduler/Geo/ownership/visibility bypass не добавлялись.

## Один owner-authorized continuation

Дополнительный retry — ResumePhase1 ровно один раз, remainingRetries0; следующие Phase2/3/4/5 являются плановыми ступенями, требуют предыдущий complete+drained STOP и запрещают повтор start.begun. Сохраняются существующие IDs/init receipts и stopped durable checkpoint, DB не пересоздавалась. Private native heap увеличен4→8GiB до continuation: первоначальный500 достигал4.07GB из4GiB при свободной памяти host≈90GiB. Это JVM flag shadow, не owner source setting.

По измеренным3000 creation≈2 identities/s перед **первым** Phase4 только harness observation bounds увеличены: ramp1800s, World phases4/5=1800s. Независимый no-progress bound120s не изменён. Production JAR не пересобирался, старый harness сохранён SHA-backed. Ограничения не ослаблялись, observers не добавлялись для обхода неудачи.

| Generation | READY / distinct ordinary characters / scheduler checkpoint | desired / eligible / admitted (last sample) | World first / second | Cap | STOP |
|---|---|---|---|---|---|
| Owner continuation1 |500 /500 /500|400 /0 /0|16 /16|16|PASS|
| Planned2 |1000 /1000 /1000|800 /0 /0|32 /32|32|PASS|
| Planned3 |3000 /3000 /3000|2400 /3 /3|24 /28|64|PASS; cap64 не заполнен|
| Planned4 |5000 /5000 /5000|4000 /0 /0|128 /128|128|FAILED; retained1|
| Final restart5 |NOT_RUN|NOT_RUN|NOT_RUN|128 owner setting|NOT_RUN|

Каждый cumulative ID+character+init manifest проверяет сохранение предыдущей ступени, lostOldIDs0/INCONSISTENT0. Исходные500 SHA неизменны: `af55ab2936b55a48b24fe6cc3afd79802bb184157f12796bc35f1bf1d009ae8f`. Final5000 ID manifest SHA256 `0c9859fed05be6b3a8a3c4fd39c90d996f034e0bfbe5322e344f250461f3e1a3`. Phase4 samples14:29:01–14:48:40 UTC, readyElapsed1141892ms, finalElapsed1227745ms; peak sampled ordinary World128. Полные aggregate поля — в [scale-results.tsv](PHANTOM-POPULATION-SCALE-102/scale-results.tsv), где initial NOT_RUN строки относятся к первоначальной поставке, а owner_1…5 — к разрешённому продолжению.

**World128 не означает admitted128.** Последний sample eligible/admitted0, pendingCatchup4986, localSignaled2651, periodicDueCalls80502/blocked80373, lastFailure=native_context.required:coalesced.128 Player находились в законных typed initial native-context handoff, а normal ecology ACTIVE readiness ещё не завершена. Нормальная долговременная ACTIVE/farm population128 не доказана. Visible farm NO_SELECTABLE_NATIVE_FARM_TARGET/NO_USEFUL_NATIVE_FARM_PROGRESS receipts сохранены; full PvE composition/работа экономики этим не доказана. Local demand реально присутствовал, отсутствие наблюдателя не использовалось как повод изменить policy.

Во всех native подсчётах ordinary membership берётся из production PopulationStore, READY отдельно сверяется с manager и scheduler; JDBC join проверяет настоящий character ObjectId/account. Dedicated merchant owners и test-human characters исключены. Шесть явно помеченных LOCALPLAY_TEST_HUMAN — настоящие native Player, disabled accounts/access0, отдельные leases, source-backed committed positions; это контролируемый спрос, не доказательство unattended density или клиентского QA. Фантомы материализуются только через production scheduler/locality/ecology/Geo/materialization. Raw merchant totals не выдаются за ordinary profiles.

schedulerRegistered в TSV — exact per-profile scheduler.find checkpoint при выходе из READY ramp, не ecology registeredProfiles. Например, ecology metadata на последнем3000 sample показывала2999, тогда как per-profile scheduler checkpoint уже проверил3000; эти разные счётчики не подменяются.

World census требует actual World.getPlayer, тот же service ACTIVE+worldPresent ObjectId, managed Player reference, headless outbound и PHANTOM identity lease. Повторный проход подтверждает тот же reference+epoch, исключая исторический union при churn; cap проверяется на stable cohort. Final phases требуют два128 cohort manifests через≥30s и сохранение всего того же cohort128. Occupied slots/retained entries и полная cap histogram отдельно UNAVAILABLE; measured World не заменяется admitted/desired.

**Третий блокер — native shutdown.** После успешного phase4.complete начался обычный shutdown.14:48:51 UTC cleanup incidents: PRE_STORE/NATIVE_TEARDOWN, PhantomNativeWorkScope.DrainTimeoutException, outstanding1 EVENT:ON_PLAYER_LOGOUT.14:48:52 deadline:materialization count109. Harness продолжил только уже предусмотренный bounded drain, затем записал drained=false: system/materialization FAILED, retainedMaterializationEntries1, population STOPPED, topologyRegisteredProfiles5000, navigation/progression STOPPING. Main завершился AssertionError «graceful STOP failed», но JVM удерживается не завершившимися threads. Read-only jcmd Thread.print сохранён; это диагностика, не исправление третьего дефекта. Более глубокая первопричина logout timeout не доказана и не заменяется предположением.

После неуспешного drain **Phase5 не вызвана**: start5.begun и stopped4.checkpoint отсутствуют. Поэтому CORE102 остаётся PARTIAL, R/progress continuity/no replay **NOT_RUN/NOT_PROVEN**, несмотря на сохранение identity при предыдущих ramp boots. Stopped checkpoint включает все ordinary character columns (EXP/SP), inventory, component payload/versions; harness phase5 должен сравнить его до production start и отдельно проверить creation saga/ordinary starter.shots.v1 при owner lifetime0. Эта логика не выдаётся за выполненный финальный restart. Positive lifetime subsidies/loot economy также не проверены.

## Проверки, scope и независимое review

Read-first: root/module instructions/README, status/workflow/master-plan/efficiency/package standard, TASK102 и все его companion docs, owner contracts/profile/targets. READ_SET — локальная карта. Дополнительные AGENTS в целевых java/test/tools/docs не найдены. Аналоги099 create-only SQL,101 authority/seal,096 identity safety,069 native bootstrap; System/Player/World целиком не читались. Extra reads bounded exact manager/ecology/locality/scheduler/materialization symbols и causal RED. Budget exception обусловлен scale/authority, diagnosis и review.

Первая поставка14 exact files. Continuation exception **10 exact paths**, суммарный TASK102 union17:3 production owners; existing Native harness; new PhantomPopulation102ReadinessChecks; Task102Run.ps1/Task102DatabaseProbe.java; этот report и2 TSV. Одна population-scale capability, без новых layers, dependencies, engine, project structure или unrelated cleanup. Конфиги/Semantic Pack/rates/QoL/Studio/merchant code не менялись. В mixed-EOL production files только новые строки приведены кLF для diff-check, без semantic изменений.

| Проверка | Exit / результат |
|---|---|
| Initial full shadow Ant graph |RED только stale performance assertion; compile/JAR PASS|
| Population performance focused RED→GREEN |3/3; canonical admission IDs отдельно от physical ACTIVE при отсутствии locality|
| Initial census seam build |0;31s, compile-tests/JAR/performance3/3|
| Continuation causal full shadow graph |0;43s, compile-tests/JAR/performance3/3|
| Readiness focused |0;3/3, baseline2 RED; old-ecology substitution negative RED|
| Ecology typed-handoff focused / presence |0;6/6 /4/4|
| Broader DB-free ecology |35/36; **не PASS**: существующее assertion shipped OFF против обязательного source ownerTrue. Source ради теста не менялся|
| Config/cohort/checkpoint contracts |0; compiledTrue/5000/128/128, invalid129/10001 fail-closed, churn/epoch/checkpoint drift rejected, JDBC0/World0|
| Owner negative stage Apply |expected1, changed0; baseline restored|
| Wrong cwd / foreign101 flag / wrong SID / input SHA |expected1 каждый, pre-JDBC; original bytes restored|
| Continuation ResumePhase1 / Phase2 / Phase3 |0/0/0; cumulative READY, real World и graceful STOP|
| Planned Phase4 |READY/W128/cohort PASS; main AssertionError graceful STOP, OS exit отсутствует: JVM retained|
| Final source/stage owner Verify/Apply |0; все changed0,68/287, effective5000/128/128|
| Final protected source / original pinned Geo / JAR |169/203 drift0, candidate JAR6435… неизменён|
| Final preservation orchestration / SQL postflight |2 /expected1; OWN102_POST_SESSIONS_REMAIN, остановка не выдана за PASS|

Continuation independent review (read-only): P1=0/P2=0/minor0. Exact typed claim, NORMAL admission, preparation budget и lifecycle/cap/identity fences проверены. Рассмотренный reverse manager/ecology lock acquisition существовал в baseline; speculative locking rewrite не выполнялся. Первоначальные2 P2 harness (World historical union и missing stopped progress checkpoint) исправлены в первой поставке, focused contracts PASS. Финальный targeted follow-up: P1=0/P2=0; независимо сверены оба128-row manifests, unique profiles/characters и exact row/ObjectId/epoch equality через30с (14:48:10/14:48:40 UTC), SHA256 обоих `08a3d2b4811108c25ae2ea55a024d2536779c81bc10672fafa8a986c8bec2d9b`. Reviewer сверил итоговые gate claims и STOP_FAILED с raw receipts, protected config/semantic diffs пусты; build/native/DB не запускал.

Full ant verify, generic mutable DB suites/materialization-performance, чужие TEST101/100/099/096N/MAIN/PLAY,5000 full Player/soak, foreign-session conflict injection, explicit expired headless permit/duplicate lease adversarial native injection, positive starter grants и client known-list/eviction QA — **NOT_RUN**. Current native samples проверяют фактический lease каждого учтённого ordinary Player; это не подмена недостающих adversarial tests.

## Performance, delivery и STOP

PERF102=PARTIAL из-за shutdown failure. Для continuation500/1000/3000/5000 heap peaks bytes:3686204840 /4326308960 /4381176280 /4367845384; committed≈4.61–4.63GB при Xmx8GiB, threads peaks125/126/127/127. CPU average cores0.529672 /0.847881 /0.741168 /0.732650; final GC293/1730ms. Phase4 sampled max lastPulseOperations64, retry backlog0, dueBoundaries5000. CPU включает merchants и witnesses; sampled maxima не являются p95/histogram. Exclusive SQL writes/query latency/p95 UNAVAILABLE. Initial4GiB heap pressure сохранён в исторической TSV строке. OOM/forced kill не было; запуск5000 Player не выполнялся.

Первоначальный JAR SHA256 `f484a763c2912818b4bef8f01815e653566f4f84107b1551c367960a6b5e8c5c`; continuation JAR SHA256 `6435bd737e3619c62c97bbc750c69a8c40a971ec0b7e64960d9e9857c0afaa7c`. Continuation phases bound к одному JAR и source OwnerConfig SHA; manifests сохраняют actual classes/config/Geo bytes. Смена JAR для исправления подтверждённого дефекта явно отделена от требуемого sameJAR final restart.

Final protected source169 SHA drift0, original203 Geo drift0; canonical Source Verify68/287/changed0. **STOP_FAILED**: final census собственный JVM1, game listeners0, established MariaDB TCP connections4; собственные SQL sessions остаются, postflight отклонён fail-closed (точный SQL session count недоступен через этот fail-closed route). Точный retained PID/connection census и private paths сохранены в private retained-process.json/continuation-final-preservation.log. Cleanup owners, leases, witnesses и pool принудительно не очищались. Последнее доказанное5000 READY/ID census относится к моменту до failed shutdown; post-stop durable progress checkpoint не получен. OWN102/receipts сохранены, их нельзя выдавать за fresh schema или clean stopped runtime. Ни одна чужая среда или общая MariaDB не останавливалась. Автоматизации/новых задач/TASK103 нет; сохранённый failed JVM — явно сообщённый cleanup incident.

Правило [SAFETY.md](../tasks/PHANTOM-POPULATION-SCALE-102/SAFETY.md), Exit and failure: «Retained native cleanup incident => report PARTIAL, no kill». Поэтому OS kill, принудительная очистка World/leases/pool, новая correction и новый native retry **NOT_RUN**. Работы остановлены на третьем блокере согласно прямому owner gate.

Mojibake-маркеры в изменённых файлах проверены: полный набор,17 файлов TASK102 union,0 совпадений.

Escaped Cyrillic в изменённых файлах проверены отдельно: все6 regex,17 файлов TASK102 union,0 совпадений. Обе TSV имеют одинаковое число колонок во всех строках.

Git-команды разрешены root bounded scope и явной TASK102 delivery authority: rev-parse --show-toplevel / HEAD / --abbrev-ref @{upstream}; symbolic-ref --short HEAD; status --short и --untracked-files=no; worktree list --porcelain; merge-base --is-ancestor9245…HEAD; ls-remote origin refs/heads/feature/phantom-world; diff --numstat/--stat/--check/-- с exact source paths; reviewer только exact-path diff. Publication: add -- exact allowlist; diff --cached --name-only/--stat/--check; commit; ordinary non-force push origin HEAD:feature/phantom-world; fresh rev-parse/ls-remote equality. Не выполнялись branch/rebase/merge/reset/restore/clean/stash/force/StageAll. Literal final commit и push proof сохраняются в private PUBLICATION102.json и финальном сообщении, без невозможного self-hash внутри самого commit. Публикация не объявляется выполненной заранее.

**STOP развития и retry. Native graceful STOP FAILED; retained owned JVM/SQL требуют отдельного разбора. Финальный restart и TASK103 не запускались.**
