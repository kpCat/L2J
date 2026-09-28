# EVIDENCE — PHANTOM-LIVE-M1-SMART-CONTINUITY-004

## Read-first / root cause

- Прочитаны TASK, HANDOFF, DESIGN, PLAN, SCENARIOS, ACCEPTANCE; module AGENTS,
  CODEX_WORKFLOW_CONTRACT, TASK_PACKAGE_STANDARD, релевантные разделы master plan,
  repository README, module readme и Ant build.xml (Java 25, UTF-8).
- Вышестоящие AGENTS отсутствуют; вложенных AGENTS и локальных pattern-файлов нет.
- Remote/local HEAD подтверждён: `0c2d305ca1759fbdfadf487e584409db4c7ee71b`,
  ветка/upstream `feature/phantom-world` / `origin/feature/phantom-world`.
- Прочитаны M1-003 RESULT, финальные evidence и connected-world / visible-life TSV:
  inherited retry около 30 s; alive ordinary farm остаётся IDLE с unproven travel.
- Scheduler / reconcile port используют TRANSIENT_BLOCK и для locality admission,
  и для deliberate retention. Одинаковый requested state сохраняет retry deadline.
- HumanLocalityControl проверяет native surrounding regions и source-backed prewarm;
  materialization ownership / admittedActionCount доступны из immutable snapshot.
- Native Party.getMembers() уже используется L2jPhantomPartyBackend; coordinator
  claims не заменяют native membership. Party redesign не нужен.
- VisibleFarmTravel освежает presence до результата navigation, очищает request,
  но оставляет Journey после terminal failure. Deadline повторно начинается.
- Native NavigationService уже различает NO_PATH, NO_GEODATA, ROUTE_BUDGET_EXCEEDED,
  COOLDOWN и pressure. Backend/service не требуют изменения для caller fix.
- VisibleAutoPlay ограничивает stock combat exact NPC; отсутствует bounded no-target
  feedback. BackgroundDecision повторно стартует тот же goal после timeout.
- HistoricalBackgroundPlanner выбирает suitable target/anchor по knowledge/topology;
  HistoricalBackgroundService уже публикует same-goal revision через setGoal.
- Локальные аналоги: PhantomActivitySchedulerSuite (manual clock / lifecycle bridge),
  PhantomBackgroundSuite native travel cancellation и outgrown visible-goal handoff.
- Переиспользуется тот же scheduler boundary, native ownership и historical planner;
  временные route/target exclusions ограничены размером и TTL, без permanent blacklist.
- Bounded scope exception: task уже разрешает связанную vertical из >10 файлов.
  Дополнительный service activity adapter необходим для CAPACITY_REACHED → DEFERRED;
  backend, budgets, topology data, combat engine, death и PLAY DB вне mutation scope.
- Старые пользовательские изменения materialization service и двух чужих suites
  сохраняются и не попадут в task commit. Другие хроники не затрагиваются.
- Непроверено: final native connected latency/continuity/useful life; до final deploy
  TestAdmin arm не запрашивается. M2 и новые proof-only задачи не начинаются.

## Реализация / проверки

- Scope expansion до редактирования `build.xml`: добавить составной focused Ant
  target по локальному примеру `phantom-normal-gatekeeper-d2-focused-test`, чтобы
  четыре связанных suites выполнялись после одной compile-tests, без повторных
  очищений build для каждого отдельного top-level Ant target.
- Scheduler baseline RED: 22/24; policy admission ошибочно TRANSIENT_BLOCK,
  fresh human.local оставляет SLEEPING до старого retry. После fix — 26/26.
- Terminal native caller baseline RED: presence сохраняется после
  PATHFINDING_DISABLED (guarded TEST DB, existing native fixture). Backend неизменён.
- DEFERRED использует базовый policy retry; fresh higher-detail signal снимает этот
  deadline. Реальный TRANSIENT_BLOCK сохраняет exponential retry с максимумом 30 s.
- Retention reasons: NATIVE_VISIBLE, REAL_PARTY, ACTIVE_ACTION (native/admitted),
  RECENT_HUMAN. Последний контакт обновляется отдельно от retry, TTL 60 s.
  При полном cap актуальный human-local requester может вытеснить oldest soft-only;
  policy guard повторно проверяется непосредственно перед lifecycle dematerialize.
- Travel hold выдаётся только для pending/native movement. Terminal failure или
  три pressure retries/60 s step deadline очищают owned request/move и сигнал.
  Feedback использует существующий same-goal planner: 8 target/edge exclusions,
  TTL 120 s, общий cache максимум 1024 profiles; permanent topology не меняется.
- Stock AutoPlay session сохраняется при повторном start того же goal/revision.
  Невыбираемая exact NPC цель через 30 s вызывает generic alternate replan;
  eligibility повторяет native Monster/range/geo/respectful-hunting gates.
- Fresh-context requesting-code-review: nonlocal reclamation и неполная target
  eligibility найдены и исправлены; global cache ограничен. Повторный read-only
  review не обнаружил новых подтверждённых defects. Tests reviewer не запускал.
- Native no-target regression initially RED из-за неверного fixture assumption:
  в production-loaded guarded scene 15 exact NPCs. Controlled fixture временно
  выставляет native invulnerability, останавливает autobuffs и восстанавливает
  NPC flags в finally; это проверяет недоступную цель без PLAY DB mutation.
- Alternate regression сохраняет existing outgrown-85 handoff; из этого ingress
  level-85 facts дают только одного routed candidate. Для самого alternate handoff
  native fixture использует уровень исходного NPC из production knowledge:
  otherwise-suitable destination исключается, другая suitable reachable цель
  сохраняет goal ID, увеличивает revision, а TTL expiry возвращает eligibility.
- Финальный focused `ant -Dbuild=.phantom-local/m1-smart-build
  phantom-smart-continuity-test`: scheduler 26/26, local priority 2/2, native
  position/travel 4/4, visible decision 8/8. BUILD SUCCESSFUL; raw focused-final.log.
- Task-owned Run-M1SmartContinuity.ps1 переиспользует M1-003 Pilot snapshots,
  consented self-teleport и captured-origin return. Все точки получены из native
  topology/Player snapshots; production coordinates и профиль не задаются.
  Синтаксическая проверка PowerShell Parser PASS. Один scene, без location matrix.
- Scope expansion до создания final report: module AGENTS требует отчёт в
  docs/phantoms/reports; разрешён ровно PHANTOM-LIVE-M1-SMART-CONTINUITY-004.md.
  Final exact allowlist: 15 production/build/test paths, 9 task docs/runner,
  один module report. Raw logs остаются файлами и не входят в commit.
- Пользователь временно отсутствует: build/deploy выполняются автономно. Arm
  запрашивается только при готовом connected acceptance; неиспользованный arm
  не считается USER_CLIENT_ACTION.

## Final automated / exact scope

- Единственный final `ant -Dbuild=.phantom-local/m1-smart-build
  phantom-live003-runtime-authority-proof-test`: 5/5, independent 85/85,
  carried progression 1..85, original canonical routes и outgrown/paging PASS.
  BUILD SUCCESSFUL (1m29s), raw broad-final.log.
- Exact-path `git diff --check -- <25-path allowlist>` PASS; index был пуст.
  Native navigation/backend, party implementation, budgets и other chronicles
  не менялись. Три исходных чужих dirty paths исключены; content hashes сохранены.
- Mojibake-маркеры в изменённых файлах проверены: 25 exact paths, совпадений нет.
- Escaped Cyrillic в изменённых файлах проверены отдельно: 25 exact paths,
  совпадений нет.
- Git разрешён TASK.md, раздел Git: read-only inspection, exact-path add,
  commit и normal push origin feature/phantom-world. Использованные формы:
  git branch --show-current; git rev-parse HEAD; git rev-parse --abbrev-ref
  --symbolic-full-name @{u}; git ls-remote origin refs/heads/feature/phantom-world;
  git status --short -- <exact paths>; git diff --stat/--numstat/--name-only/--check
  -- <exact allowlist>; git diff -- <exact paths>; git diff --cached --name-only.
  Review использовал только read-only exact-path diff. Mutation: git add --
  <перечисленный allowlist>, git commit -m "Fix generic phantom smart continuity",
  git push origin feature/phantom-world. broad add/history rewriting запрещены.

## Build / deployment / connected ready

- Code commit/remote: de40c81411cf6501cb9446840aef98f7dd9369e2. Exact staged guard
  подтвердил 25 task-owned paths. Normal push PASS, ls-remote совпал с source SHA.
- list_artifacts не нашёл suitable active checkout; native create_worktree(ref=SHA)
  создал C:/Users/ZBook/.codex/worktrees/m1-smart-continuity/L2J_Mobius.
  git rev-parse HEAD, git symbolic-ref -q HEAD и git status --porcelain подтвердили
  exact SHA / detached / clean. Единственный final ant jar PASS: 2294 sources,
  24 s. Post-build checkout clean; clean-build.log сохранён в task folder.
- Controlled Stop-LocalPlay остановил только owned старые Game 21216 / Login 32904.
  Exact clean JARs скопированы после backup. Manifest hashes обновлены штатными
  existing fields; .NET Replace потребовал непустой backup path, orchestration
  исправлена без второй сборки и без изменения source/config/budgets.
- Start-LocalPlay -Background, Check-LocalPlay PASS: Login 21324 / Game 10036,
  owned 2106/9014/7777, 10000/64/128/100. Startup 47 s; Pilot enabled;
  registration Server 1/Bartz confirmed. Raw deploy.log, health-before-deploy.log.
- GameServer build/deploy SHA256 совпал:
  6C0932A3672E427B6ABC820A096DA65D12FC59B9B2078385004F91F954A1008F.
  LoginServer SHA256: 3B79A86276AA544E6886881F54814BE159FAC1412241852408781E86EF64A9E8.
  PhantomPlayers.ini не редактировался. Content SHA256 трёх чужих dirty files
  после deployment совпал с pre-commit snapshot.
- Connected runner final syntax PASS. Private mailbox read under owner SID:
  inbox/processing 0, journals 224 / results 226; cap 512, room для одного scene.
  Чтение под sandbox SID недостоверно из-за intentional private ACL, поэтому
  readiness подтверждена actual owner context.
- WAITING_CONNECTED, actual client actions 0. Fresh TestAdmin arm будет выдан
  после возвращения владельца. Connected invariants и origin return ещё не
  выполнены, поэтому M1 не закрыт; M2/new task/proof-only continuation отсутствуют.
- Evidence-only closeout: exact-path add трёх EVIDENCE/RESULT/report файлов,
  git diff --cached --name-only/--check; git commit -m
  "Record verified M1 continuity build and deployment"; normal push той же ветки.
  Deployed code SHA остаётся указанным выше.

## Единственный финальный connected run — RED

- По возвращении владельца выполнены только pre-arm Check-LocalPlay и bounded
  exact-SHA/JAR check: CONFIG/owned ports PASS, Login 21324 / Game 10036;
  detached HEAD de40c81411cf6501cb9446840aef98f7dd9369e2 и deployed GameServer
  SHA256 совпали с ранее проверенной чистой сборкой. Sandbox Git inspection
  отклонён ownership guard; тот же read-only rev-parse выполнен под owner SID,
  без изменения safe.directory.
- Свежий TestAdmin arm фактически активирован пользователем. Get-LocalPlayPilot:
  ARMED_IDLE, runActive=false, Game 10036, actor 268492939. USER_CLIENT_ACTION=1.
- Ровно один существующий Run-M1SmartContinuity.ps1 выполнен без изменений.
  Exit 1: PHASE_TIMEOUT:PREWARM. M1_CONNECTED_WORLD.tsv содержит OUTSIDE,
  PREWARM_APPROACH и PREWARM (2026-09-28T15:19:31Z..15:19:49Z): natural profile
  5079, objectId=0, STORED/SLEEPING, DEFERRED. В prewarm presenceReason=offline,
  admitted=false, localityCurrent=false. Причина по этим samples не установлена.
- connected-final.log: CONNECTED FAILED; ORIGIN RESTORED: REAL_LOGIN confirmed.
  Runner выполнил штатный finally/StopPilot. Visible/leave/return/native-life
  фазы не достигнуты; M1_VISIBLE_LIFE_CENSUS.tsv не создан.
- Повторного run, новых proof/audit, source edits, build или restart не было.
  Status CONNECTED RED, M1 открыт; GREEN и M2 не объявляются.
- Итоговый evidence scope: эти три docs и существующий output
  M1_CONNECTED_WORLD.tsv. Raw log остаётся вне commit. TASK Git разрешает
  exact-path add/commit и normal push текущей feature/phantom-world.
- Mojibake-маркеры в изменённых файлах проверены: четыре exact paths, PASS.
- Escaped Cyrillic в изменённых файлах проверены отдельно: четыре paths, PASS.
- Git closeout: git diff --cached --name-only (пустой index); git add --
  <четыре указанных paths>; git diff --cached --name-only/--check;
  git commit -m "Record single M1 connected acceptance failure";
  git push origin feature/phantom-world; git rev-parse HEAD и
  git ls-remote origin refs/heads/feature/phantom-world для remote SHA.
