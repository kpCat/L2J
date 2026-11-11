# Ledger — plan: PHANTOM-LEARNING-002/PLAN.md

- 2026-11-11: пакет13 распакован; base exact; новая независимая ветка; исходный checkout read-only.
- Read-only audit трёх доменов завершён. Geo203/protected207 verified0 mismatch; toolchain найден.
- Защищённые refs зафиксированы в DESIGN_DECISIONS. Новая ветка на GitHub ещё отсутствовала.
- Ruling: autonomous execution следует прямому запросу владельца; не вводить промежуточные
  approval gates из общих skill defaults. Цена неверного технического решения — scoped rework.
- Ruling: bounded exception к 10файлам — единый pipeline и обязательные CLI/docs/test артефакты;
  exact allowlist перед staging. Другие subsystem families не добавляются.
- Pre-flight: Task1 Collector.Observation → Task2 finish; Task2 Store→Task3 runner;
  Task3 CLI/files→Task4 supervisor; Task4 attestation→Task5 native proof. Сигнатуры в PLAN.
- Tasks1–5: pending. Новых native/training/SQL/detached доказательств пока нет.
- RED: ant compile-tests exit1, отсутствующие новые Training/Scenario symbols;
  исходный production compile прошёл,5 исторических предупреждений в test compile.
- RED: Test-Training -Action Test-Config отказал TRAIN002_OWNER_CONTRACT_MISSING.
- Task1 focused GREEN: javac новых contracts/observer exit0;
  PhantomTrainingContracts через существующий registry —3/3, exit0.
- Входные SHA256SUMS проверены12/12; ZIP SHA455c4e40c7673a36527dea2520a74cf6c9011145c0417d0a9fc7b8322aa074e7.
- Только в own .git/info/exclude добавлены private learning002 runtime и build logs.
- Integrated compile-tests: первый запуск exit1 (List.of(String[]) varargs inference),
  exact fix List.<String[]>of; второй запуск exit0,5 прежних предупреждений.
- Focused GREEN через штатный launcher: training-contracts3/3, training-scenarios2/2,
  learning0014/4, seed183. PhantomTrainingStoreChecks DB-free exit0.
- PowerShell RED→GREEN: case-insensitive argument shadowing; atomic Replace null binding.
  Test-Arguments и Test-Atomic exit0 после точечных исправлений.
- PowerShell5.1 source parsing: прямой UTF-8 без BOM был неверно декодирован;
  UTF-8 BOM применён атомарно к3 новым PS1, текст проверен неизменным.
- Preflight exit0: JDK25/MariaDB11.4/config/Geo203/protected207/free endpoints;
  SQL/GameServer не запускались.
- Task1 contracts реализованы; Tasks2–4 source готовы, native/SQL/detached proof pending.
- Existing targeted regression aggregate266/266: Learning4, Combat72, ownership37,
  Party13, coordination77, PvP14, Support9, Supply11, Commerce15,F814; F7 strictreceipt
  и itinerary отдельно PASS, SQL_REPLAY=NOT_RUN. Восемь suites без Learning/F8 дают248.
- Неуспешный diagnostic route combat-server-integration exit2 отказал before-all из-за
  отсутствия explicit test DB config; SQL не подключался. Подтверждён правильный
  DB-free combat-core72 из отчёта001 и исходников; он прошёл.
- Fresh read-only review review_safety: четыре concreteP1/P2, все приняты в исправление:
  1. Hard crash оставляет EnablePhantomSystem=True; следующий bootstrap требует OFF.
  2. Ordinal-only episodeSeed не меняет фактический audit cohort и не доказывает свежесть.
  3. External EXPORT может пересечь train.requireStopped и остановить кампанию.
  4. Pre-CUT actor crash до seal блокирует последующую подготовку.
- Review RED: Test-ColdBaseline exit1 TRAIN002_COLD_BASELINE_MISSING;
  PhantomTrainingStoreChecks exit1 Changing an ordinal-only seed made the same actual
  audit cohort fresh. Исправления supervisor и learning agent выполняются.
- Ruling: actual cohort связывается с actorGroup+реальной сценой, не меняющимся только
  в метаданных episodeSeed. Для следующих попыток обучения нужны новые genuine
  pre-CUT actor/catalog snapshots с bounded retention/capacity, без reset старых наград.
  Цена: дополнительная подготовка собственных актёров между батчами; сохраняется
  один штатный игровой engine. Native agent готовит durable immutable catalogs.
- Важно для продолжения: на этот момент own Training runtime root ещё НЕ создан,
  MariaDB/GameServer002 НЕ запускались, JAR002 ещё не собран. Все source edits в ownclone.
  Следующее: GREEN четырёх reviewfixes, then TRAIN_START one-command automatic build,
  staging, SQL, actors, native. Не повторять read-only audit и уже зелёные266 без причины.
- Review fixes GREEN: compile-tests exit0; training-contracts3/3, scenarios2/2,
  catalogs2/2; StoreChecks actualcohort/quality/UNKNOWNcoverage exit0. PowerShell
  Config/Arguments/Atomic/ColdBaseline/ExportAdmission exit0. Cohorts теперь реальные:
  immutable snapshots + новые ordinary Player IDs по persisted attempt generation;
  physicalScene identity и split стабильны, metadata seed не обновляет audit.
- Task5 instrumentation: Native separate prep/encounter/settlement/SQL timers,
  SQLwriter batch persist timing; supervisor native/SQL CPU/RAM snapshots. Не считать
  это performance proof до фактического запуска.
- Дополнены русский operator RUNBOOK и промежуточный report (не финальный SUCCESS).
  Status/report получили bounded UTC coverage window и явные UNKNOWN категории.
- First TRAIN_START exit1 CommandNotFoundException до owner/JAR/SQL/JVM. Только пустой
  private root. Он проверен (exact path/private ACL/empty/portsfree) и сохранён как
  .phantom-local/learning002/train.bootstrap-incomplete.86078cbb14d84d1a9222da90e6b8c704.
- PrivateBootstrap direct WindowsPowerShell focused test GREEN. Повтор START с safe
  diagnostic снова exit1: Get-Acl, TrainingOwner.ps1:36, CouldNotAutoloadMatchingModule.
  Точный CMD→WindowsPowerShell route воспроизвёл тот же RED; direct powershell.exe
  из PowerShell7 GREEN. Supervisor проверяет inherited PSModulePath и launcher-local
  совместимость модулей. Глобальную среду пользователя не менять.
- Текущий runtime root снова существует ПУСТЫМ, без owner/receipts. Новые SQL/JVM/JAR
  ещё НЕ запускались. Не начинать новую read-first разведку; закончить CMD modulepath
  fix + guarded automatic empty-bootstrap recovery, затем запускать собственный START.
- Sources сейчас стабильны кроме supervisor modulepath/bootstrap fix. Learning coverage
  focused RED→GREEN завершён; Native catalog protocol/helpers generation(root) завершены.
- CMD inherited PS7 module path defect исправлен локально в7 wrappers; private empty
  bootstrap recovery focusedGREEN, без удаления данных. START generation1 detached:
  caller/launcher завершились, director82648 самостоятельно собрал compile-tests+jar,
  stagedGeo203 и125 stockSQL scripts; SQL probe PASS, manifest550D5180472A989C...
  Затем STOP_UNCONFIRMED до ACTORS: причина первого сбоя UNDETERMINED, native002=0.
  SQL/datadir сохранены; два штатных STOP завершили только ownSQL, порты0.
- Process exit tracking переиспользует3 проверенных helpers Task108R3, focusedGREEN
  exit0/7/null/invalidhandle. ErrorHistory GREEN: primary preserved, cleanup separate,
  secret excluded, nativeError retained. Pending initial catalog journal quarantine
  RED→GREEN; compile-tests25s exit0; contracts3/scenarios2/catalogs3 и StoreChecks0.
- START generation2 exit0, caller68168, launcher75288, director72356. Оба родителя
  отсутствуют; launcher-proof-generation2.json сохранён в privateTEST. Идёт автоматическая
  пересборка после recovery fixes; прежняя SQL установка будет переиспользована.
  До native proof никакой D1/D2/D5 PASS не заявляется.
- Generation2: compile-tests30s и jar PASS; SQL_PROBE_PASS на прежней БД. Durable
  primary теперь установлена: CIM GetOwnerSid HRESULT0x80041002 в момент окончания
  worker79444, TrainingOwner:220. Cleanup Win32 Error31 после выхода worker/SQL.
  Первый STOP выполнил SQL shutdown, второй STOP exit0 подтвердил absence/ports0.
  Следующий fixed run требует cheap real-process GREEN для этой точной гонки;
  повторный JAR build не нужен при изменении только supervisor lifecycle.
- Read-only native review выявил seeded ±64 preparation point при actual anchor
  tolerance0. Canonical point-only fix + narrow local planner scene skip; RED missing
  helper, compile-tests26s0, training-scenarios3/3 на действительном Imperial XML и
  native anchor predicate. Это требует обновить testbin перед следующим native run;
  игровые anchors/правила/Geo не изменены. Catalog ещё не создавался.
- Test-CimExitRace RED exit1 helpermissing; готовится cheap actual-process proof
  точного stale CIM0x80041002 и held exit, без SQL/GameServer. D9 дополнение ограничено
  measured CPU boundary idle policy, не изменяет Combat/Party/native правила.
- Test-CimExitRace GREEN2.6s exit0: actual stale CIM1, own held exit1,
  live rejected1, security failures rejected4, private exit receipt1,
  tampered FILETIME rejected1. Exit receipts относятся к точному immutable identity
  frame и owner marker; Error31 не означает absence без held proof и CIM absence.
  Отдельное narrow read-only review проверяет новые lifecycle ветви.
- Narrow lifecycle review: no concreteP1/P2. D9 CPU-only boundary idle реализован;
  focusedGREEN low10/high34/cap20/UNKNOWNbase/bounds2. Config/Arguments/Atomic/
  ColdBaseline/ExportAdmission/ErrorHistory повторноGREEN. Старые v1 config получают
  только2 default keys; ни SQL schema, ни game rules не меняются.
- Guard acceptedempty BEFORE catalog seal сохраняет INCOMPLETE и TRAIN_NONE_FEASIBLE
  вместо повторных пустых GameServer boots; javacActors1s0. STARTgeneration3 exit0,
  parent76056/director77280; launcher-proof-generation3.json в privateTEST.
  Причина дорогого повторения: canonical scenario admission + доказанное lifecycle
  исправление. Native/SQL data ещё0; предыдущая полная SQL установка сохраняется.
- Generation3 самостоятельно прошёл SQL_PREPARE→ACTORS→NATIVE. START_ALREADY0/STATUS0,
  одинdirector77280. Каталог0 принял5 сцен (TRAIN4,TUNING1,AUDIT0), сохранил skips.
  Первый actualGameServer13668/attempt28731a09bd9a473785bd4b01f645559f:
  startup39304ms, Solo2 отказа NO_SAFE_SOLO_OPENING, Party1 WAIT_GROUP_DAMAGE.
  Следующий Party21799/S77 на штатном autonomous пути дал1 COMMITTED exactNATIVE,
  646HP, key844740d0c2f678bfb3ebeb424d2e5caa9e260714162c70862f609d9a19d93387.
  PAUSE0→PAUSED, затем первый STOP0→STOPPED/ownports0; данные/веса/логи сохранены.
- Optional SQL StoreChecks GREEN1.1s: exact realNATIVE replay declined, events1→1,
  committed1→1; interrupted recovery и synthetic refusal такжеPASS. Диагностические
  INCOMPLETE строки не являются NATIVE. D1(2 native episodes)/Solo/DRAFT ещё не доказаны.
- Actual perf первого batch: JVM215262ms/CPU119.609375s/peak4522860544bytes;
  Party fullfeatures+selection4 samples p50=161600ns,p95/p99/max1641500ns.
  BackgroundSQL1batch1offer dropped0 total/max13729800ns. Real adaptiveCPU decision
  KNOWN/base10s sufficient, predicted13.274% configured4cores. Это не128native proof.
- Real source audit объяснил0AUDIT: onlycanonicalImperial 8 actual combinations
  физически делятся7TRAIN+1TUNING. Candidate limit также съедали duplicateWorldNPC.
  GiantsCave AUDIT NPC82 не проходит helper76 stockrosterband. Добавляются реальные
  weaponEnchant0/2/4 variations с permanent physical split/legacy identity сохранением,
  uniqueanchor budget и полный rosterlevel prefilter; никаких seed-only новыхAUDIT.
- Actual report revealed UTC defect: firstepisode10:45:33Z прочитан JDBC как08:45:33Z
  из-за JVM EET; old throughput0.488/h неверен. Minimal query/getLong Unixepoch fix
  по локальному EconomyOfferService, optional SQL UTC assertion ожидает GREEN.
- Genuine variants/rosterband/uniqueanchor fixes frozen. Generation4 START→PAUSE
  перед native использован для единственной attested auto rebuild: compile-tests30s0,
  jar26s0. Focused GREEN contracts3/scenarios4/catalogs3/learning0014=14/14.
  SQL COVERAGE_UTC_PASS expected1794393933924→10:45:33.924Z и реальный replay1→1.
- RESUME10:59:14Z, controlparent71688 завершён. Unattended proof11:10:17Z:
  elapsed663250ms, parentabsent, SQL committed1→3/events1→9,2 новых COMMITTED.
  В интервале только readonly наблюдение, никаких управляющих команд.
  Директор сам EVALUATING→следующий NATIVE_BOOT, без nativeFailures.
  Private proof: unattended-proof-generation4.json; новый каталог9 сцен,
  TRAIN4/TUNING4/AUDIT1, фактические weaponEnchants0/2/4, oldlegacyID сохранён.
- Ещё2 настоящих Party на21798: final SQL status5 COMMITTED/13 exact events,
  9 learner actors/2skills/5 actual scenes, NPC21798=2,NPC21799=3. Solo0.
  LIVE TRAIN_STOP из активного Party FIRSTcall0, STOPPED11:15:41Z/ownports0,
  SQL/эпизоды/веса сохранены, никаких игровых правил/HP/formulas не менялось.
- Actualcollector показывает materialized learner maxHP4827 при startingHP2936.
  Existing recover(false) корректно REST до70/80%, но idleDeadline45s тратился на
  этот REST и давал NO_TARGET до окончания восстановления. Следующий exact fix:
  Solo-only bounded natural recovery BEFORE ACTIVE/collector, stricter90% readiness,
  отдельный preparationSeconds180 (15..600), cancellable; никаких setters HP/MP.
  Once existing read-only target inspector при NO_TARGET объяснит оставшиеся отказы.
  Config contract RED javac только отсутствующий preparationSeconds getter.
- Operator STATUS сокращён до русского snapshot/date/counts/двухscope/model/reason;
  полный JSON остаётся файлом. Actual status exit0, UTF8BOM EF-BB-BF сохранён.
- Solo natural-preparation fix frozen; PS optional range15..600 matchesJava.
  Generation5 STARTparent20164/director58384→PAUSE forattested auto build:
  compile-tests30s0/jar37s0; contracts4/scenarios4/catalogs3=11/11 GREEN.
  UTC SQL PASS и exact replay13→13 events,5→5 committed. Source PS/status и Java
  frozen; RESUME11:22:45Z (parent80740 exited), private proof generation5 saved.
- Native attempt6328bcecf0e04a2993a1937b66023570 independently reusedsealed9scene
  catalog (no actor/data reset). Party TUNING21799/S77 gave9 exactNATIVE.
  FIRST Solo AUDIT21798/S78/+4 episode6251a4bbcdc8433a8f48dc8bfc3e86f8:
  preparation161598528100ns naturalREST, encounter120063742600ns,
  settlement9533778900ns, SQLreadback211700ns/commit31800700ns,
  NATIVE_EVIDENCE exact3, elapsed291209ms. NoHP/MP/stat setters added.
  Both fresh Solo/Party native evidence now proven. Threshold/independentAUDIT4perScope
  unchanged; automatic DRAFT training stillpending, campaign continues withoutcontrol.
- Source config-only cold reload11:35Z: weaponEnchants расширен0,2,4→0,1,2,3,4
  для дополнительных реальных equipment/AUDIT вариантов. Existing Java getter/list
  validator0..25 и selectorhash используются без изменения кода/JAR. ReadTrainConfig
  по absolute path exit0; activeRoot snapshot всё ещё0,2,4, Native PID/FILETIME/SID/
  config hash attestation PASS. Новые значения применятся только после cold boundary;
  это не новый source edit JVM/PS и не понижение thresholds/игровых правил.
- Native continuation aftercoldreload reached60 COMMITTED exactkeys. Один реальный
  controlledcrash T08/T14: Test-Crash initially WINDOW_NOT_READY (noKill), затем
  probe14b2d4887e3b4d92b3591378a6a4533a at12:06:35Z, nativePID28120/created
  134388718465290114, episodecaa7c58b5e324963baf5ffa408fe6a99,3 genuine rawEFFECT
  keys notyetledgered. ExactG0 guards+oneKill, exit-1 observed. SQL/director stayedalive.
  Automaticretry within57s→epoch888691e280e0428d8687d7423f56db84. OldACTIVE became
  INCOMPLETE; pendingmembership0; all60 precrashcommitted retained; new5→65 total.
  Crash/recovery JSONproofs privateRoot. First sourcepatchreview rejected crashroute,
  same-toolretry withactualT08/T14/SAFETY explicitlyapproved, no bypass.
- Generator smoke RED outdatedrootmanifest; TEST-only fix resolvesphysical
  active-catalog.txt tosealedcatalog, notnewfakecopies. Generate/Native/Detached/Resume
  allGREEN0. Existingforeigntarget/hash/SID/flags/AccessDenied invariants retained.
- At12:12:54Z directorautomaticallytrainedfirstcandidate ce212360fa6d44b6b3149b70b28719c9
  duringstoppedbatchboundary afteroperatorSTOP, no directmanualtrain. Dataset74 real
  NATIVE: TRAIN22/TUNING37/AUDIT15, freshaudittrue, chosenstep55,33ms. DRAFTSHA
  bb61a8a0b98e730ca2012259ea8f2ddb590234bef07bb41e43c31201ad418fc9,12500B/algo2;
  frozenDataSHA c149426140cd3e7aff382384a5990958806c17b2f60c7a8e58be294f907f1b0c.
  SOLO modelMSE.139474657 > constant.129114258; PARTY.079316116 < .082035029.
  Rejectedquality, activeModel0, no fakeACCEPTED. Stillwantnaturaluncontrolledboundary
  nexttrain/evaluate proof. CurrentDRAFTbytes successfullyread aftercoldSQLrestart.
- ActualSQL leader3171845/4827HP (<50%) confirmsstockPartyrefusalafterSolo. Nextfix
  ALL-player nativeREST90 inoneboundedawait beforeinspect, helpernull1230 MPcost0,
  untouchedstockPartyMP/role/Geo gates. Logsactualbefore/afterHPmaxMP. Presetprep420,
  batch7200. Tinyabort4arg overload preservesINCOMPLETEafterfacts/elapsed; old2arg
  repeatcannotoverwrite; REDmissingoverload→GREENoptionalSQL. NoNATIVEcredit added.
- Generation6 STARTparent5692/director39536, auto compile-tests24s0/jar21s0,
  PAUSE→contracts4/scenarios4/catalogs3=11/11; SQL UTC +afterfacts137ms +replay74→74,
  committed18→18 GREEN. RESUME12:22:13Z parent49288. Source/JAR/PS frozen exceptTEST
  readonlyjobprobe beingprepared; sourceepisodesPerBatch2 isTARGETEDsmoke override,
  restore6 beforefinaldelivery. Actorcataloggeneration1 freshrealIDs12scenes.
- Firstgen6 ALL-playerprepactualready90% for5 actors confirmednativerawlogs; Party
  ACTIVE. NoHP/MP/statsetters. Candidate reviewartifact exactBLOB/SHA/algo2 savedunder
  task artifacts/draft-ce212... (weights+metadata+RUreadme), clearlyDRAFT/notapplicable.
  TRAIN_EXPORT queued36f927... returnedFAILED reasonnoaccepted atstoppedboundary,
  campaigncontinued. SourceRUNBOOKneedsasyncqueue/non-success wordingclarification.
- Mandatoryremaining: naturalboundaryautotrain/evaluate freshcohort; all-roster
  nativePartycredit (andpostSolohealcase) +cleanfinalSTOP; jobmembershipOSproof;
  finalregressions/protected207/Geo203; RUfinaldocs/scopeguard/encoding; scopedcommit/
  nonforcepush ownbranch +independentGitHubHEAD/protectedrefs. No commits/pushyet.
- Generation6 JARgreen and actualALL-rosterprep ready90% verifiedfor5actors, before/
  afterHPmax logs present. Nativecontinues naturally: at12:46 batches9/no failures,
  evaluatorCOLLECTING (newindependentAUDIT pending), historicalfirstDRAFT preserved.
  Itsworker parentwasoldDirector58384; firsttrain occurred12:12:54 atSTOP-requested
  cold boundary, notmanualtrainercommand. Naturaluncontrollednexttrainstillwanted.
- CRITICALD1audit: new readonlyTrainingJobProbe.cs +Test-Detached kernelquery actual
  ownedDirector39536 anyJob=TRUE (G0beforeaftervalid). ThisdoesNOTshowkill-on-close,
  butparentexit alone cannotguaranteeCodexUIlifetimeindependence. UIneverclosed.
  ProbeJSON detached-job.a8755fff91f743999ec28e5d506cb106.6285d1b1453b490bbafdf785da10c79c.json.
  NoJobclaim/completeNOTallowedyet. ProtectedWin32SqlIdentity.cs untouched.
- SupervisoragentreadprimaryMSdocs: LOCALWin32_Process.Create +inmemory
  Win32_ProcessStartup(CreateFlags16777216 CREATE_BREAKAWAY_FROM_JOB,ShowWindow0)
  documented, WMIproviderbroker outsidecallerjob. OrdinarySID/UAC+jobfalse gateBEFORE
  resources, envwhitelist(no secrets), staticbootstrapstreamlogs. Noexistingjoblimit
  tamper/noScheduler/service install/no undocumentedAPI/no fallbackinheritedjob.
  STAGE1AUTHORIZED: addsharedminimalbroker/newbootstrap/cheapTESTstub/Test-Broker,
  noTraining.ps/Owner runtimeeditswhile39536alive. ROOTwillruncheapstubwithrequire_escalated;
  onlyafterGREEN rootnormalSTOP thenSTAGE2STARTintegration. Agentmustnotlaunch.
  ReasonnewWindowsinterop isconcretemainGoal inheritedJob risk, boundedonepipeline.
- Generation6 natural boundary automatically accepted attempt
  d01e95f0233549acb85bf5c358189559 at12:54:16.685Z (worker exit0), without control
  request/manual trainer: model1/algo2/12500B, SHA
  e0691f728847cebea7293d87e45acebbd3db5bc2d71b76c7c5e40549e13c43d2.
  TRAIN33/TUNING45/AUDIT9 fresh=true, step47,33ms. SOLO5 audit rows modelMSE
  .012446571212712598 < constant.03944743309218234; PARTY4 rows .1025303627366636
  < .1397164244576262. Frozen dataset SHA202623c277aec76aadafdf3b203a48660461f0e0789b926ef37d102e121a2d77.
  First rejected DRAFT retained. No general DPS/PvP/survival claim.
- Cold-ON native loaded exact model1/SHA and actually reordered1230→1339 in BOTH
  Solo and Party, with exact committed SQL native keys. Snapshot13:29:52Z:
  31COMMITTED/114events/2attempts/1ACCEPTED, SOLO15episodes51events, PARTY16/63.
  New export requesta8b95d6158144be0bb4c94fad125c565 completed13:19:18.8495260Z.
  ZIP66089B SHA51fc661faaf36497e687d7332250aaefd0efb2cf11139100d0d65375ec644d07;
  exactly4entries (weights,manifest,dataset,episodes), weightsSHAexact, no credentials.
  Public byte-identical review copy artifacts/accepted-model-1/model-1.zip.
- Normal TRAIN_STOP generation6 confirmed13:34:02.0543824Z, first command exit0;
  own processes absent/endpoints free; history/model/logs preserved. NativeFailures0,
  batches15. Training.ps/Owner source unchanged during live generation6.
- Critical D1 evidence: caller immediate job0x2800(KILL+BREAKAWAY), WMI probe/relay
  immediate shared job0x1800(BREAKAWAY+SILENT,NO_KILL). Extra relay did not escape
  shared job, so noAnyJob test still RED and does not prove Codex lifetime dependency.
  Root approved ONLY new unnamed OWN kill-on-close TEST fixture, direct WMI final,
  bounded heartbeat, helper-only assignment/termination; never mutate existing Jobs.
  Test-JobLifetime captured RED13:38Z TRAIN002_OWNED_JOB_FIXTURE_MISSING before resources.
- Final current-Java DB-free aggregate277/277 GREEN (266 prior routes+11 director)
  and standaloneF7 receipt/itinerary PASS (SQL_REPLAY=NOT_RUN). Invocation errors:
  party cwd missingstatBonus fixed dist/game; PvP rejectedseed183 fixed pinned25002501;
  F8 id is standalone main, not launcher mode; F7 moduleRoot cwd fixed. No code/test
  weakening for these errors. Five old compile warnings remain; no unrelated DB gates.
- Stage2 directWMI GREEN: Test-Broker query/ordinarySID/streams/exit5 plus6negative
  admission cases; Test-JobLifetime repeated againstfinalsharedlauncher: ownkillJob
  closed, helperexited, finalsurvived9additionalheartbeats, normalexit5. Evidence
  TEMP/Training002JobLifetime-24a851b565094640be74bed95fe87eb7/own. ExistingJobs untouched.
  Runtime admission NOJOB or queryOK immediate !KILL+BREAKAWAY+SILENT; all5sourceSHA
  pinned, nonce/PID/FILETIME proof before Run/build/SQL. No scheduler/service/elevation.
- Final launcher native START13:51:15Z exit0, caller57836 absent, director46584
  generation7 nonce df5b9f777c1246d08028158fdfde0908, parentWMI4812.
  SameJAR/actualcoldModel1 exactSHA, bootstrap33328ms, scenes12. Baseline31COMMITTED/
  114events. Test-Detached exit0 identity+admission+heartbeat (anyJobtrue sharednonkill;
  ancestorsNOT_ENUMERATED, literalUICloseNOT_RUN). No controlcommands sinceSTART.
- Read-only SQL current policy1: SOLO3 events7996HP, PARTY9 events14631HP;
  all114events/114unique; activeModel1 exactSHA. Newordinal64SoloACTIVE.
  Owninstallation disk13:57 snapshot26907files/1474474795bytes. Inputchecksums12/12
  reverified and originalZIPexactSHA. These are actualsamples, notload/scale promises.
- Mandatory Test-Training RED currentCOLLECTING hid completed history. TEST-only
  minimalfix now requiresSQLsnapshotattempts>0 plusacceptedcheckpointversion/SHA
  match whenactiveModel>0, while admitting nextauditCOLLECTING. GREEN2attempts/1accepted.
  Production5pinnedsources untouched whilegeneration7live. Config/Arguments/Atomic/
  ColdBaseline/ExportAdmission/PrivateBootstrap/ErrorHistory/CimExitRace/AdaptiveIdle/
  Generate/Native/Resume all0 onfinalscripts. EmptyBootstrapRecovery correctlyrefused
  ownSQLbusy33327; repeatonlyafterSTOP, no weakening.
- Final independent learning review (Collector/Store/Evaluator/migration/seams):
  no remaining concreteP1/P2. Separate final Windowslauncher review inprogress.
- Generation7 finalWMI actualunattended UTC13:51:15.177020Z→14:08:10.8920054Z,
  1015715ms, parent57836absent, controlsNONE. SQL31→33COMMITTED/114→116events;
  neword64SoloCOMMITTED,65PartyINCOMPLETE(noexact),66SoloCOMMITTED. Directorautonextbatch.
  Use unattended-proof-generation7-utc.json. Original -generation7.json retained:
  its diagnostic elapsed8214966 was wrong by2h duePS7 automaticJSONDateTime coercion
  thenParse; corrected artifact binds originalSHA and literalUTC DateTimeOffset math.
  SQLfacts/timestamps unchanged; nofalse min-duration claim aftercorrection.
- Generation7 normalSTOP firstcmdexit0 confirmed14:08:37.9427092Z,
  stopNonce4d5b000e55f74a2a98098d4c50b534fb, batches17/nativeFailures0, ownabsence/portsfree.
  PresetepisodesPerBatch restored2→6 beforefinaldelivery/restart; noJava/JARchange.
- Independentnative reviewer foundconcreteP2 START15stimeout couldleave latebootstrap
  beforeownerrecord and STOP couldconfirmbeforeRun. Rootacceptednarrowfix: mandatory
  bounded ACK beforeRun; durableheldG0pendingWorker/configBootstrap beforeACK; START/STOP
  reconcilesregisteredpending, timeout writescancel+stop and waitsownexit. Mainchecks
  cancellationbeforebuild/SQL. Noforce/newservice/manager/PRELAUNCH phase. Unregistered
  noACKchildexpireswithoutRun/resources; no claimofenumeratingunknownOSprocesses.
  Test-StartupHandshake RED TRAIN002_STARTUP_HANDSHAKE_MISSING14:04 beforefixture.
  Rootauthorized4sourceeditsonlyafteractualSTOP; supervisoragentimplementing, no launches.
  Finalnewlauncher regression/START/STOP thenreport/scope/encoding/commit/push stillpending.
- FinalP2 fix GREEN: Test-StartupHandshake noACK expires; ACK→cancel beforeRun;
  pendingExitConfirmed2/duplicatePendingRejected2/SQL0. Broker6negative/JobLifetime
  +9heartbeats/Config6/EmptyBootstrapRecovery/Isolation203207 all0. Independentreview
  confirmed reportedraceclosed/noP1P2. Fivepinnedsources thenfrozen foractualgeneration8.
- Generation8 actualdoubleSTART0 keptsamePID63324/FILETIME/nonce38db0b7589fd43ce89ad435d4f9537ef,
  pendingarchived; Detached0. Native965dc6d440274cd3965ab7455043bf46 actualpreset6,
  sameJAR/coldmodel1 SHA, boot35080ms; newSoloordinal68 exact1339/1503HP,
  keyefcfd66647273091bc5430e055134e2d924caf283ac58502c05332e1f819ef41.
  JVM349567ms/CPU92.078125s/peak4654895104B, fullselection2334900ns(sample1).
- PAUSED confirmed/nativeabsent; SQL34COMMITTED/35INCOMPLETE/117unique117,
  INCOMPLETEcredited0, attempts2/accepted1. Policy1SOLO6/12406HP PARTY9/14631HP;
  originalDRAFT+ACCEPTED blobs12500B/algo2 retained. RESUME0 samegen8→ACTORS.
  FirstSTOP0 at14:29:29.4312167Z; stoppedREPORT0+Export0 coldreadback preserves117/model1.
  FinalIsolation203207/freeports0, idempotentSTOP0 at14:31:52.8466956Z,
  stopNoncee4272a169e844b98855888a7953ad2be. Allownresourcesabsent, nofailureleftcurrent.
- FinalcanonicalmodelZIP69127B SHA10fc9c6c8c450e4974ca7abed861805a7640e2c67c8474ac5a4c96e003b493da,
  weightsSHAunchanged. Priorreviewcopy backedup byteexact outsidecheckout withSHAcheck;
  originalprivateexports remain. Public PROOF.json assembled from exactsafe snapshots,
  omitscredentials/datadir/globalPIDinventory, retains literalUIClosefalse/manualREQUIRED.
- Boundedartifactexception68paths: module.gitattributes adds13exact -text ownerinputpaths,
  reusinglocalTASK065 byte-preservation pattern. Input13files themselvesunchanged.
  Java/SQL/buildsource unchanged sincegeneration6 successfulJAR; no extraJar neededforPS/docs.
  TechnicaldeliveryREADY; finalscope/encoding and authorizedownbranchGitpublication follow.
- Deliverychecks: all12ownerSHA entries reverified; 5/5PS1UTF8BOM forWinPS5.1;
  finalTestTrainingHistory0 and directTRAIN_STATUS0(Stopped117/model1).
  Full27mojibake markers0 and all6escapedCyrillic/XMLpatterns0 in65existingtextfiles;
  finalscope manifest isASCII and fullsetscan is repeated afteritscreation.
  ActualprivateSQLrootsecret absent fromexactdeliverytext+ZIPplaintext entries;
  no credentials or datadir/Geo/runtime payload staged. Scopeallowlist68 fixed.
  Finalmanualacceptance remainsREQUIRED. ExactGitHubHEAD is recorded aftercommit/push
  in the delivery reply and private publication proof, avoiding a self-referential SHA.
- Scopedstage68 completed. PlaincachedWhitespace2 reportedCR byte-ending in -text
  immutableinputs/Windowslines; exactsameguard withper-command cr-at-eol0, retaining
  trailing spaces/blankEOF/space-before-tab checks. NoSourceEOLrewrites/configmutation.
  Finaldoc/scopehashes refreshed beforecommit; finalGitHEAD followsordinarypush/readback.
