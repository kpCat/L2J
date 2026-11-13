# Execution ledger — PLAN.md / PHANTOM-LEARNING-009

`/goal PHANTOM-LEARNING-009-UNIVERSAL-AUTONOMOUS-TRAINER`

PLAN_APPROVED: владелец явно ответил «Подтверждаю PLAN — начать реализацию». Scope:38exactpaths. Final manual gate009:REQUIRED.008 остаётся PARTIAL / NO_TUNING_CHECKPOINT_IMPROVEMENT, её gate отдельно.

## Исходная точка и сохранность

- TEST exact00885b1c3558e35c1df0a7625fcf5a9e5c119c18b96, tracked clean/.phantom-local untracked.
- Создана только wip/phantom-learning-009 от этого SHA; основной feature checkout не изменялся.
- Existing independent TEST переиспользуется. Новых clones/worktrees нет.
- До изменений сделаны SHA-verified byte backups всех существующих exact allowlist source/docs, обоих dist JAR и прежних build/phantom-test/reports вне checkout в task artifact root. Никакой historical checkpoint/dataset/SQL не удалялся.
- Первая попытка preservation helper остановилась до копирования: .NET DirectoryInfo.Parent не имеет PowerShell PSIsContainer при StrictMode. Исправлена проверка типа DirectoryInfo; повторное сохранение exit0.

## Rulings и интерфейсы

Ruling: использовать этот PROGRESS.md как execution ledger и task-owned artifact root как workspace вместо .superpowers/sdd — explicit task layout/exact scope приоритетны, лишних Git-команд/папок нет; риск: важна регулярная запись прогресса перед compaction.

Pre-flight: inventory → shortlist/scenarios используют exact SkillRef(id,canonicalLevel), а не theoretical class ranks. Shortlist → Combat использует тот же bound4, comparator STOCK_ON_FROZEN_SHORTLIST_V1. Campaign → Store использует immutable split/context отдельно от comparisonCell; новые receipts не смешиваются с historical applications. Director → GUI остаётся единственным ownership/command contract. Конфликты baseline/splits разобраны builder review и внесены в approved PLAN.

Ruling: существующий TEST — normal Git repository, не linked worktree; он уже изолирован и прямо задан владельцем. Новый worktree не создавать, target branch009 разрешён явно.

## Текущий этап

1. Source/preservation/branch: выполнено. Baseline DB-free007/008 exit0,2m06s,5existing warnings; все4routes каждой задачи прошли, native NOT_RUN.
2. Universal inventory и bounded shortlist: DB-free contracts GREEN; actual Player/native proof REQUIRED.
3. Scenario generator/actual actor ingress: curriculum GREEN, saved-state ingress в реализации; native round-trip REQUIRED.
4. V2 targets/SQL/Adam recovery: NOT_STARTED.
5. Autonomous Director/GUI: NOT_STARTED.
6. Native/independent evaluation/performance/publication: NOT_STARTED.

GameServer starts009:0/6. Успешные SQL-only сеансы: preservation Export и read-only census; новая saved-image диагностика выполняется. Не объявлять G0–G8 PASS по historical evidence или одному compile.

RED inventory/shortlist: `ant ... phantom-autonomous-training-test`, exit1/46s. Новые tests написаны первыми;25compiler diagnostics указывают только на отсутствующие новые PhantomSkillInventory/AutonomousTrainingV1/types. Это RED отсутствия feature/API, не runtime assertion и не native proof. До GREEN никаких GameServer/SQL запусков.

Orchestration deviation: первая sync попытка File.Replace получила empty-path из-за PowerShell $null→string conversion; target остался исходным. Исправлено на существующий TrainingOwner pattern [NullString]::Value, собственный pending файл удалён только после SHA equality со stage. Несработавший первый Ant из-за отсутствующего ещё target не считается feature RED. Последующее exact deployment прошло.

Первый GREEN attempt скомпилировал новые production/test classes, но DB-free Skill fixture потребовал game cwd для native data/stats/statBonus.xml. Target dir исправлен на dist/game; direct existing runner inventory exit0, native XML SkillTreeData загрузился, без SQL/GS. В исходном коде API назван isAOE(), регистр сверён до deploy.

Ruling: дешёвый theoretical tree read напрямую делегирует canonical SkillTreeData.getCompleteClassSkillTree(PlayerClass), без второго parser/loader. Existing progression catalog остаётся источником runtime provenance. Это позволяет отдельно проверить наследование native parent trees без GameServer/SQL; риск: итоговый all-class coverage по-прежнему требует настоящего catalog census, а не только этого unit contract.

Отдельный theory RED: narrow javac existing runner,1expected diagnostic missing theoretical(int), exit1. Внедряется только read-only canonical delegation. Следующий canonical Ant GREEN ещё REQUIRED.

Canonical inventory/theory/shortlist GREEN +007/008 regressions: `ant ... phantom-autonomous-training-test phantom-learning008-test phantom-learning007-test`, exit0/3m01s. Оба новых groups GREEN; все старые4+4routes GREEN;5existing compiler warnings. Это DB-free contract proof, не actual actor/native proof.

Readiness RED: narrow javac,18missing new Resources/NativeFlags/Reason diagnostics, exit1. После implementation narrow compile прошёл; первая Java invocation ошибочно разобрала unquoted JVM -D property в PowerShell и не исполнила runner. Исправлен literal quoting, focused eligibility GREEN/exit0. Native Player/Geo bridge скомпилирован, его actual runtime proof остаётся NOT_RUN. Последний canonical target с третьим group ещё REQUIRED.

All-class DB-free census через PlayerClass/SkillTreeData:103classes,43749complete theoretical learn facts,7172unique theoretical skill versions. Actual acquired count=null, train readiness NOT_ASSESSED. Полный machine report вне checkout:class-discovery.json,SHA256ac699a85bdb599ab1215fba74298bc831eec7a050f2e15279f9d2d2268f1e43e. Не выдавать эти числа за actual learned inventory или охват TRAIN.

G0 existing Training Preflight: exit0, config/JDK25/MariaDB11.4/Geo203/protected207/ports PASS. SQL и GS этим действием не запускались.

До изменений Store выполнен штатный Training.ps1 -Action Export: exit0. Только существующий TEST SQL поднят и штатно закрыт; GS0, fit0. Cold ZIP проверен CRC и SHA, сохранён byte-copy вне checkout. Actual selected DRAFT12 SHA6f3dfa43…, raw Adam12 SHA85cea568…, embedded terminal weights7d3c8fc1…, binary887updates/207epochs;42applications/12gradient episodes. Export SHA25631be59ec5e877ed4480cf0cc9e14bb0c9f9eefc4b9cb0c758d87f602fc176eb6. После Export TEST listeners0. Full historical-table digest и new SQL contracts ещё REQUIRED.

Canonical foundation с inventory/shortlist/eligibility: exit0/58s, foundation-final.log. Curriculum RED:4missing ActorSeed diagnostics; исправлен compile lambda capture, focused GREEN. Mode-pool RED:13missing ActionPool/Proposal.pool diagnostics, exit1. После реализации focused curriculum GREEN/exit0. Это tests отсутствующего feature/API, не assertion RED. Отдельный canonical target со всеми новыми groups остаётся REQUIRED.

Ruling: один native Combat request имеет один mode. ActionPool разделяет DAMAGE по PhantomCombatMode, HEAL по Party owner. Deficit counters, tie и durable shortlist cursor включают mode, поэтому magic/physical actions не смешиваются и режим не теряет курсор. Production Combat/resolver bound4 сохранён; новый слой исполнения не добавлен.

Первый Report -Autonomous завершился compile failure до SQL/GS: TrainingStore.quote package-private в другом package. Исправлен TEST adapter на существующий PhantomLearning005Evaluation.q, без расширения production API. Повторный Report exit0:1006persisted actors,6classes,55668skill rows across all class indexes; actual Player.getAllSkills=null/native readiness NOT_RUN. CensusSHA bbeb18ebe0d534019e4f611676db424c51dc6228ca37cff3b4c119e881e391b6. TEST SQL штатно закрыта, listeners0; canonical private GameServer.jar SHA47e42fa229327b0407313fe0b41c6565c9f83b77b5254d85a5c5f4efd96df288 на тот момент. Текущая сборка может иметь новый SHA и фиксируется отдельно.

Persisted classes:10MAGE6actors/384skill rows;90PHOENIX_KNIGHT200/8000;94ARCHMAGE528/32396;103MYSTIC_MUSE48/2940;105EVA_SAINT200/10400;110STORM_SCREAMER24/1548. MAGE headers level1 содержат десятки skills: это диагностический риск старой QA fixture, не доказательство законного TRAIN ingress.

Saved-image contract RED:2missing SavedTable diagnostics/exit1. GREEN narrow javac + source-contract/exit0: exact skill ranks/enchant/subclass preserved, source actor/item identities remapped, foreign source row и unmapped item FK rejected. SavedActor/SQL image хранит полный characters row, все class-index skills/subclasses/hennas, items/augments/elementals и явные неподдерживаемые timed/external rows. Новая incarnation вставляется одной transaction и проверяется полным SQL readback; original image SHA проверяется до/после. Player.load выполняется только для нового ID; persisted learned-state delta после rewardSkills вызывает отказ, без repair. Actual native round-trip ещё NOT_RUN.

Текущие счётчики009:GS starts0/6,native minutes0/90,новые fit/checkpoint0,GUI0. Продолжать к actual acquired actor ingress/scenario generator и V2/Director/GUI, не объявлять фундамент готовым автономным тренером.

## Native acquired ingress и новая targets revision

До первого SQL clone сохранена целиком остановленная own SQL:513files/460368518bytes, private ACL, source/copy/source SHA для каждого файла. ManifestSHA c6be117f9c49033d62f9c4af2abb6ab6f2b4facf816bf8aabf4f0cfc8088a68f. Backup command exit1 только на финальном console Measure-Object по dictionary после всех verified copies и записи manifest; receipt readback exit0/514files including manifest. Ошибка финального подсчёта исправлена, immutable backup не перезаписывался.

Saved-image Report exit0; payloadSHA cf14ec8eb3ccec19a4fa6497845dd2fa8ff7e3f65b6d7c6ba77df2d5c6e895d4. SQL shutdown/TEST listeners0. У healer обнаружен HOLY_POMANDER_<class>=true; canonical features/SkillTransfer подтверждает стабильный acquisition marker без object references/timer. Он копируется целиком; другие неизвестные variables по-прежнему отказывают. Это переменная состояния, не class→skill allowlist и не выдача предмета.

Два первых Prepare -Autonomous: CLI exit1/worker exit2. Шесть MAGE1 источников с HP0/null position отклонены до clone. На Phoenix Knight native Player clone/SQL readback/learned comparison прошли, но acquired capture отказал. Второй запуск с безопасными stack frame locations локализовал ошибку на capture argument guard. Root cause: canonical progression builder hash() использует HexFormat.withUpperCase(), новый inventory принимал только lowercase SHA. Принят подтверждённый native формат без переписывания значения; regression добавлен. Никаких grants/condition bypass не вводилось. Headless workers:42641ms/115.25CPU-s/4219949056peak bytes и47317ms/125.21875CPU-s/4529262592peak bytes; оба штатно закрыли pool/SQL, ports0.

Третий Prepare exit0: session9fa0afee24a7404e9af935c2d5334cb9, reportSHA679c158633bc0199b40c0dfa4254c412c90ffd61e4575d20fd7e125ad4a11bb2.10verified fresh incarnations/5classes/570actual getAllSkills entries:90PHOENIX_KNIGHT2×40;94ARCHMAGE2×67;103MYSTIC_MUSE2×61;105EVA_SAINT2×53;110STORM_SCREAMER2×64. Inherited1235:28 присутствует у Mystic Muse. Native derived skills могут давать +1 к persisted count: это не persisted tree grant, соответствие сохранённых skills проверено отдельно. Оригиналы не загружались; original image SHA проверен после clone. Шесть dead/incomplete MAGE и два timed-effect Storm Screamer sources сохранены как rejected, без repair. Native target/owner cast admission ещё REQUIRED.

Реально обнаруженные safe pools:Archmage9magic;Mystic Muse10magic;Storm Screamer11magic;Phoenix Knight2melee/2ranged-physical/2magic;Eva Saint3magic/4Party HEAL. Это adapter candidate support, а не доказательство ready на конкретном target/weapon. Full inventory сохраняет также AoE/control/passive/resurrection/mobility/unknown. Native useful HEAL/cast/terminal/trainer credit ещё NOT_RUN.

Третий headless worker:52355ms/134.375CPU-s/4607107072peak bytes. Ruling: headless native Player probes учитываются консервативно в90native-min budget, хотя не являются GameServer starts. Completed native wall142313ms=2.371883min; GS0/6. Все6SQL-only sessions (Export,2Reports,3Prepare) закрыты, listeners0.

ActionAdvantageV2 RED:9missing-type diagnostics/exit1. Focused objective GREEN: generic model/class/data, exact-cell TRAIN controls, leave-own-block-out min3distinct controls, one paid action per episode, duplicate rejection, class-sum balancing with stored weight cap4, cold canonical codec. Existing V1/008 codecs не менялись. Новая revision ещё не интегрирована в Store/Trainer commit и не выполняла fit.

Canonical six groups +007/008:exit0/3m27s, six-groups-regression.log.5existing compiler warnings; все6новых routes и4+4старых routes GREEN. Это состояние до следующего acquired-scene refinement, поэтому последующие изменения потребуют targeted verification.

Acquired-scene identity RED:2missing method diagnostics; narrow GREEN. Отдельный runtime assertion RED: forged Proposal с неизученным skill999 обходил public record constructor. Исправление добавляет consistency guard ActorSeed/pool/cursor/frozen refs; narrow source-contract GREEN. Generic acquiredScenes использует только actual proposal, native world/reward/topology/Geo/LOS/safe anchors, без class tree grant и ручных class/skill ветвей. Native world execution этой ветки ещё NOT_RUN.

Текущие счётчики:GS0/6,completed native2.371883/90min,fit0,GUI0. Следующие обязательные части:Runtime acquired route, V2 atomic Store/Trainer integration, persistent cyclic Director/config/resources, UI, bounded native2cycles/fit/evaluation/recovery, final scope/encoding/archive/publication/manual gate. Фундамент не объявлять FULL009.

## Acquired Runtime / V2 Store и Trainer

Acquired Runtime seam реализован и narrow compile/source-contract GREEN: отдельный constructor принимает per-episode frozen resolver, старые constructor routes делегируют null. Новый prepareAcquired проверяет exact inventory fingerprint и уникальные class IDs roster (class-only CapabilitySource не становится ошибочным actor cache), читает native recovery costs из frozen refs, не вызывает fixed-primary helpers. Generic SOLO dispatch использует выбранный mode и attested target через существующий Combat; Party остаётся текущим autonomous/coordinator owner. Общий death observer вынесен без изменения legacy entrypoint. Actual acquired Runtime encounter ещё NOT_RUN.

Frozen resolver RED:1missing-method diagnostic/exit1; GREEN narrow compile/source-contract. Exact200:101/201:2 сохранены в fixture; это contract data, не native доказательство этих Skill ID. Live TEST source-contract/curriculum/objective/legacy007 selection и authority exit0 после deployment. Последний canonical all-six+007/008 PASS предшествует этим новым изменениям, финальный rerun REQUIRED.

Autonomous permit RED:4missing validateAutonomousPermit diagnostics. GREEN objective: explicit isolated opt-in COLLECT/campaign/dataset/model/profile, experimental only TRAIN; MAIN/PLAY, AUDIT experimental и profile0 rejected. Новый ownedAutonomous factory не меняет owned007/008 и Selection codec. Opportunity bind actorProfile, поэтому Party helper не тратит чужое окно; native Party proof этой привязки REQUIRED.

V2 Store/Trainer реализованы, но новые SQL freeze/commit/recovery/actual fit НЕ запускались. Переиспользуются существующие runs/context_replay/audit_used/advantage_snapshots/applications и та же transaction save; новых таблиц/DDL нет. CONFIG_SHA и sampler revision различают V1/008/V2. requireStopped/FIT/opt-in, source/context/native comparisonCell+comparator, immutable raw blobs, global natural/action key reservation, exact parent/selected teacher identity и real Adam continuation проверяются; selected weights не подставляются вместо terminal moments. Cold reader проверяет V2 snapshot/application/sampler proof и сохраняет local receipt loss units registry; V2 quality в отдельных metrics. V2 остаётся DRAFT, applyTest отказывает до writes. Добавлен gated own offline pre-COMMIT halt74 с immutable one-shot marker для будущего настоящего R11; fault пока NOT_RUN.

Sampler RED:3missing-method diagnostics; focused objective GREEN. Дополнительный runtime assertion RED: constant benchmark менялся при повторении доминирующего класса, хотя gradient weights class-balanced. Исправлен benchmark на weighted TRAIN mean; revision CONFIG_SHA закрепляет правило. Focused objective GREEN. Новых V2 native datasets/checkpoints до исправления не было;007/008 bytes/codecs не менялись. Trainer route продолжает parent.state(), использует только TUNING для выбора и сохраняет AUDIT/teacher/constant metrics отдельно. Export добавляет V2 targets/labels/applications с точными SHA и unknown counterfactual; actual V2 export round-trip REQUIRED.

Read-only cold-lineage Report attempt1:CLI exit1, worker CommunicationsException на TrainingStore.requireStopped ДО model read. SQL log:socket33357 создан, ready-for-connections появился примерно10sпозже, JDBC socketTimeout5s истёк. Не неисправность V2 data и не основание ослаблять ownership. SQL штатно закрыта/ports0. Добавлен readiness gate через installed mariadb-admin ping после PID/port/image/config checks; CLI flags подтверждены local --help. Start-TrainSql сохраняет own record до readiness, чтобы finally закрывал SQL и при отказе gate.

Первый readiness gate attempt2:CLI exit1/SQL_READY_TIMEOUT при private stdout «mysqld is alive». Причина — пропущенный existing held-handle pattern для redirect Start-Process в Windows PowerShell5.1:ExitCode остался null. Исправлено через Hold-ProcessHandle/Get-TrackedProcessExitCode, без cast null→0. Вторая SQL также штатно закрыта/ports0. Третий cold Report сейчас выполняется, результат ещё REQUIRED; не объявлять старую lineage доказанной новым кодом до его завершения.

Interim encoding scan на20modified staged files: mojibake — все27markers/exit0/0matches; escaped Cyrillic — все6regex/exit0/0matches. Это две отдельные проверки текущего source, не финальная проверка будущих изменений.

Cold Report attempt3 exit0 после held-handle fix. Новый Store cold reader подтвердил прежние DRAFT12selected6f3dfa43…,Adam1285cea568…,terminal7d3c8fc1…,887updates/207epochs. Никакого fit/model write не было. ReportSHA0e7db9886e2c3be84d28f7b4c8bc73e923f1352b5d779b9dc4feeab29b7d1d74, canonical private JarSHA0adcdc2c5d376bcbc1c15744e74ba696adc17fe154c75beaaaa4ce17d71e8447. Persisted actors1018=1006source+12новых AT incarnations (2отказавшие probes+10verified); snapshot source selector исключает AT. SQL shutdown/ports0. Всего9SQL-only sessions завершены; native budget по-прежнему142313ms/GS0. Новые SQL freeze/commit/fault и actual V2 export остаются NOT_RUN.

## Versioned OFF config и comparison projection

Java config RED:3missing methods/exit1. GREEN config route:classes=auto через весь PlayerClass enum, levels=auto через min/max1–85, optional class weights без второго class catalog, resource/phase/flag bounds. Negative interval, heap-without-headroom и unknown class weight отклонены. PowerShell existing Test-Config RED:CONFIG_LIST classes; после auto/config validator GREEN:7legacy invalid+6foreign identity+4autonomous invalid, SQL0/native0. Новый AutonomousTraining.ini по умолчанию OFF, без Skill ID и NPC allowlist; private TRAIN_CONFIG.ini пока НЕ переключался с008. Enabled COLLECT/FIT/EVAL требует valid campaign/context/terminal authority и positive model version/exact SHA до native bootstrap. Validation settings ещё не доказывают resource enforcement/loop.

Canonical seven groups +007/008:exit0/3m26s, seven-groups-regression.log,5existing warnings. Последующие source-index/projection/phase-pin refinements проверены narrow; финальный canonical rerun ещё REQUIRED.

SavedActor.physicalFingerprint RED:4missing-method diagnostics. GREEN source-contract: dispatch snapshot SHA различает incarnation identities, matching projection исключает actor/item IDs, но изменение item elemental value меняет physical hash. Это matching helper, не simulator/identity/eligibility. Native current pre-CUT stats/target/shortlist/comparator ещё должны дополнять comparison cell в collector.

Prepare adapter теперь пишет source-index.tsv с source actor/profile (nullable UNKNOWN), logical snapshot SHA, physical projection SHA, acquired inventory SHA, class/index/level; report закрепляет index SHA и exact current JAR. Эти новые index/provenance outputs ещё не создавались actual Prepare: следующий native prepare должен сделать их перед collection. Source capture по-прежнему нуждается в diversity/cursor вместо первых2accepted perclass.

Snapshot reproduction дополнительно отказывает external sponsor/apprentice/academy references и восстанавливает JDBC transaction isolation после own atomic clone. Narrow source-contract GREEN, actual round-trip после этих refinements REQUIRED. Dynamic eligibility MP cost согласован с canonical PlayerStat cost (как existing native admission), чтобы passive discount не давал ложный MP отказ; первая javac команда имела неверный cwd и не исполнила runner, исправленная compile+eligibility exit0. Native boundary proof с MP discount ещё NOT_RUN.

На этом шаге18из24source paths modified; collector/Director/GUI ещё не были соединены. Дальнейшее состояние уточняют следующие записи.

## Первый настоящий autonomous Director

Реализован new native route в том же PhantomTrainingNative:acquired source replicas, ordinary goals, per-episode frozen resolver, Runtime/Combat/Party, exact receipts/timing/terminal boundary. Ни auto-learning fixtures, ни новый Combat/trainer не используются. Cohort schedule48=4cells×(4TRAIN_A+4TRAIN_B+2TUNING+2AUDIT); focused source-contract compiler RED→GREEN, single-source SOLO band regression добавлена после actual native failure. Pool rotation меняется между cohorts; class weights учитываются в дефиците. Native offered skills вне supported frozen pool не допускаются к новой exploration/gradient.

Existing Director соединяет COLLECT→FIT→independent native EVAL→cold checkpoint→NEW. Persistent phase/teacher/dataset/stagnation/no-data/native budget, SQL recovery committed fit, resource checks, queued export, detached broker сохранены. До первого sealed cohort source revision разрешён только с immutable audit record и сохранением cumulative budget; после seal JAR pin не ослабляется. Результаты двух циклов/новый fit пока NOT_RUN.

Canonical `phantom-autonomous-training-test jar` exit0/1m28s, collector-build.log. Focused7groups GREEN,5existing warnings. Test-Config GREEN7legacy/6foreign/4auto. WinForms Add-Type compile сначала дал inherited Form.Text name conflict; helper переименован, CompileOnly exit0. PowerShell-host окно не предоставило UI; тот же штатный Add-Type теперь создаёт тонкий WinForms EXE в private runtime с source/binary SHA. EXE открыт, CLI status в настоящем окне прочитан. Действия кнопок НЕ подтверждены:Computer Use capture timeout/coordinate geometry unavailable. Пользователь затем попросил другие инструменты; дальнейший Computer Use прекращён, не заявлять button PASS.

До CUT создан acceptance-protocol.json:2cycles,30sec episodes,90sec prep,6GS/90native-min cumulative включая142313ms headless; native RNG/reward не менялись. Старая008конфигурация побайтно сохранена вне checkout. Первый Preflight refused missing protected006 pin в новом OFF profile, после добавления canonical246AC… exit0. Все protected203/207 guards сохранены.

CLI Start exit0 detached Director PID56732/generation41. Cold checkpoint подтвердил DRAFT12selected6f3dfa43…,Adam85cea568…,terminal7d3c8fc1…,887/207,acceptedTEST2unchanged. Первый GS attempt3095caf6366d436d9de6ec80f1d2263d завершён без CUT/receipts:AUTONOMOUS_ROSTER_LEVEL_BAND (SOLO ошибочно вызвал Party API size≥2). Штатный drain/SQL cleanup, native process72685ms/159.046875CPU-s/4689526784peak bytes; бюджет с boot+teardown75182ms, cumulative217495ms=3.624917min,GS1/6. Предыдущие9SQL-only sessions сохранены, новый Director использовал10-ю SQLsession.

Исправление использует canonical forMember для SOLO и forRoster дляParty; focused source-contract exit0. Resume exit0, новый Director PID58024/generation42; на этом историческом срезе BUILDING compile-tests. Последующие факты приведены ниже; эта запись не является финальным состоянием.

## Финальный native срез — PARTIAL009

Владелец отдельно утвердил39-й exact path L2jPhantomPartyBackend.java только для shared acquired CapabilitySource в TEST, со старым3arg constructor/обычным поведением. Backend constructor ABI и opt-in refusal GREEN, PHANTOM snapshot/capabilities получают тот же source, что Combat; shared-source.txt подтверждает observed exact refs до CUT. Других paths вне согласованных39нет.

Fresh read-only reviewer выявил Important: источник был добавлен в REAL вместо PHANTOM пути и четыре clone sites обходили capacity guard; исправлены все sites. Minor: checkpoint interval не использовался и Status выдавал timestamp без stale-оценки; исправлены. Повторный review не нашёл Critical/Important, но runtime PASS не обещает. Последняя source-contract regression сохраняет старый constructor и проверяет production opt-in rejection.

GS2: paid useful HEAL993.627281365201HP/80MP, но terminal NO_LEGAL_TARGET/NOT_ADMITTED, selection0, training credit отсутствует. Pause/drain/new-process Resume проверены. B/GS3: collection1→offline insufficient fit→old cold checkpoint→collection2/GS4 без команды Codex между циклами; затем Pause. Это no-data autonomy, не optimizer update. C/GS5 завершила96slots:48PARTY DAMAGE+12PARTY HEAL rejected до CUT,36SOLO terminal results (30TIMEOUT/6NATIVE_ACTOR_DIED),23raw paid effects. Fits0; cold checkpoint старой12не является новым trained checkpoint. C создала18INFLIGHT_PROGRESS_REFERENCE; они не меняют Adam.

Exact C case: NPC21798level76;103learner refs1172:8/1175:8/1177:5/1235:28 имеютmagicLevel35/35/14/74, helpers94:1230:28/110:1148:13/90:49:26 имеютmagicLevel74. Party выбираетRANGED_MAGIC и canonical estimateCastCycle отказывает target76>magicLevel; rawDPS0 превращается в sentinelNaN и WAIT_GROUP_DAMAGE. Гипотеза «все actors healers» отвергнута actual inventories. Native safety guards не ослаблялись.

Исправлен только разрешённый TEST generator: actual target-bound full pool через existing eligibility/nativePlanningCombat.forTarget/estimateCastCycle до shortlist/seal; complete acquired denominator сохраняется. ДляHEAL helpercheck — необходимый magic envelope, не замена полного Party admission. Focused test RED2missingmethods→GREEN; initial fixture constructor/name errors исправлены доGREEN. Изолированный javac писал только в ART, не в live build bin. Final canonical Ant autonomous/008/007/jar exit0,185s; review Critical/Important не нашёл.

Fresh D/GS6 сохраняла prior5starts/2136280ms. Итог:0cells/0episodes;121planning refusals (111insufficient target-bound alternatives+10helper magic envelope), no fit. Бюджет закончился на6starts,2231818ms=37.196967native minutes из90;3headless probes142313ms включены. Седьмой GS, новый Goal и сброс counters не выполнялись. Нет отдельного native EVAL009 и actual pre/post-COMMIT fault recovery: sufficient fit не достигнут.

Targeted004/005/00610existing routes exit0; первый sandbox005отказал Temp ACL, authorized rerun всех10PASS. F7canonicalcwd exit0, F8existing suite exit0/14PASS/DB0/GS0. PSconfig7legacy/6foreign/6autonomous+3heartbeatGREEN. GUI WindowsEXECompileOnlyGREEN/настоящее окно прочитано; livebuttonoperations NOT_VERIFIED. ComputerUse прекращён по просьбе владельца.

Final Stop exit0. Cold Export worker7bc3c044…exit0: ZIP89bebd7480285c65299343d742a0fe109961981fc7bbb2bd06b6ae41fa2fe17e; selected12/rawAdam12/terminalweights побайтно прежние,887updates/207epochs,42applications/12gradient episodes008. CRC/readback/copy verified. No promotion.

Final owned postflight exit0: Director/native/worker/SQL0, listeners33357/18001/19301=0, Geo203/protected207exactPASS. Dconfig bytebackup сохранён, privateprofileOFF/faultsfalse, campaign/6starts/2231818ms сохранены. Новый overnight profile не подготовлен; nightReady=false. Финальные scope/encoding/archive/publication receipts фиксируются в EVIDENCE/COMMANDS. Goal009—PARTIAL, final manual gate REQUIRED;008остаётсяотдельнымPARTIAL.
