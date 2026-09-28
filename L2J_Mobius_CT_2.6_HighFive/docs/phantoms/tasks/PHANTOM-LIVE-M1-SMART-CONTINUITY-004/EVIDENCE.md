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
