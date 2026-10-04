# M1 Living World — Implementation Plan

> Для Codex: использовать executing-plans либо доступные subagent-driven-development
> workflows. Исполнение и изолированный worktree уже разрешены пользователем.
> Spec: DESIGN.md; exact contract: TASK.md и ACCEPTANCE.md.

**Goal:** получить проверенный native M1 codeSHA/JAR и готовую честную runtime-приёмку.
**Architecture:** опциональное native ownership, reserve-before-submit/transitive drain,
existing owned-store checkpoint, stock gameplay. Никакого второго движка.
**Stack:** High Five / Java25 / Ant / MariaDB guarded TEST; Windows PowerShell observer.

## Global constraints

PLAY SELECT/read-only, current JVM untouched. 1280 READY сохраняется. b4f protocol и
чужой diff сохраняются. Source closure расширяется по TASK.md, не вызывает D5 stop.
Native TEST != control-flow model; positive controls обычных игроков обязательны.
Review и точный clean committed-SHA build обязательны до runtime-разрешения.

## Review focus

Callback начинает работу до возврата submit; cleanup ждёт собственный ActionLease;
cancel(true/false) завершает Future раньше тела; old objectId повторно принадлежит
другому Player; reward listener завершился ошибкой после частичного SQL side effect;
снятый из списка timer ещё пишет; active checkpoint открыт раньше FINALIZE;
«target существует» маскирует вечный idle; OBSERVE-only cohort скрывает ранний FAILED.
Эти случаи явно покрыты Q/P/E/L/A/W ниже, не остаются общими пожеланиями.

## Workstream 0 — read-first и перенос candidate

Files: CANDIDATE_REUSE, предыдущие RESULT/REVIEW/patch; SOURCE_MAP/ASYNC_PRODUCERS;
local AGENTS/branch/status/test environment.
- [ ] Зафиксировать base/remote ancestry, сохранить hashes чужого diff, PID/start/JAR/config
      read-only; не смешивать checkout-JAR с реально загруженным JVM артефактом.
- [ ] Создать изоляцию и перенести candidate006 безопасно, проверить 13-path ledger.
- [ ] Сверить TEST schema manifest и handlers/geodata initialization, прежде native RED.
- [ ] Заполнить producer coverage по конечному reachable M1 graph из ASYNC_PRODUCERS.tsv:
      enqueue → callback → writer → drain/cancel → checkpoint. Unread != safe.
- [ ] Записать SCOPE_EXTENSIONS до новых правок. Не запрашивать permission на каждый файл.

## Workstream 1 — сквозное native ownership и checkpoint (первый critical gate)

Files: Player, PhantomMaterializedPlayer/Service, proposed native work helpers,
EventDispatcher, Attackable, QuestTimer, TimerHolder, ThreadPool, необходимые native
hit/cast tasks, PhantomBackgroundService и вызывающие active-store paths.
Interfaces: ticket/owner из proposals/native-work-contract.java-snippet.
- [ ] Воспроизвести опубликованный Q01 delayed kill на реальном stock native TEST,
      сохранив сырой log; подтвердить именно order, не compilation/fixture failure.
- [ ] Добавить queued-not-running, parent-fork-after-close, cancel-running и
      active-checkpoint self-drain reproducer до реализации.
- [ ] Внедрить общий owner scope, roots/children, queue reservation, exact epoch,
      idempotent completion и scoped execution context. Нет нулевого bypass после detach.
- [ ] Провести scoped строгую постановку native owned callbacks через native pools;
      Q07 rejection/null/inline/shutdown outcomes доказаны. Legacy path не переписан.
- [ ] Включить QuestTimer/TimerHolder pending vs running и их cancel/post callbacks.
      Не отменять earned continuation как ambient; не держать lease час для обычного timer.
- [ ] Покрыть hit/cast scheduling, follow-up events, реальные progress/item writers,
      load/abort/death/teardown paths, найденные coverage audit. Не останавливаться на
      первом исправленном event при наличии другого writer той же closure.
- [ ] Active save/resume/arrival получает тот же seal до inventory flush/PREPARE,
      ожидание вынесено из counted caller и нужных native locks. Нет fake save success.
- [ ] Выполнить Q01–Q16, затем existing owned C/D/restart/mismatch. Все GREEN.
- [ ] Typed drain timeout → completion → один queued existing cleanup retry;
      без infinite retained cohorts и без reopen UNKNOWN.

## Workstream 2 — завершить pools/session и failure evidence

Files: AutoPlayTaskManager/AutoUseTaskManager, PhantomVisibleAutoPlay, Player,
PhantomMaterializedPlayer/Service, incident DTO, PhantomSystem census, TEST.
- [ ] Доработать candidate P/E по найденным review issues, не переносить их вслепую.
- [ ] P01 валидный baseline RED; P02–P09 race и positive REAL/offline/synthetic controls.
- [ ] Exact pair ownership/repair на том же Player; stale P1 не удаляет P2.
- [ ] Устранить/избежать ordinary SQL/follow TOCTOU, проверить настоящие side effects.
- [ ] E01–E06: first/latest, phase+hook, RuntimeException/Error primary, detached bounds.
- [ ] Нет nested manager locks, ожидания native callbacks под lifecycle monitor;
      доказательство lock order и bounded stress после deterministic latches.

## Workstream 3 — travel / anti-idle / geometry

Files: PhantomVisibleFarmTravel, PhantomHistoricalBackgroundService,
PhantomBackgroundDecision, PhantomVisibleAutoPlay, PhantomSystem production composition,
existing navigation/backend/topology/retention; tests и exact geometry data если доказано.
- [ ] Native T01–T05 RED и реальный production callback/factory, а не его TEST копия.
- [ ] Typed terminal dispositions и общий attempt ledger через Journey replacement;
      stale revisions отсекаются. Подтвердить terminal → replan → native farm.
- [ ] Progress watchdog отличает damage/route/limited regen от флагов/вечного self-heal.
- [ ] Исследовать путь Elven Village/Gremlin/Red Keltir и water-boundary cases:
      coordinates, instance, geo file coverage/hash, height, path segment, WaterZone.
      Ошибочная topology/standpoint правится точечно; чужую geodata не подменять.
- [ ] One representative spot на каждый реально населённый диапазон текущего population,
      bounded native TEST; это не sweep всех мобов и не проект генерации codemap.
- [ ] T06–T08/W matrix: нет массовой общей непроходимой/водной standpoint, нет infinite idle.

## Workstream 4 — loot и truthful evidence

Files: existing native pickup/drop/stat sinks, PhantomVisibleAutoPlay config,
LocalPlayM1NativeEvidence (new bounded observer helper), PhantomSystem, Pilot/runner.
- [ ] L01–L05 actual native stock pickup/autoloot/protection/capacity/save/reload TEST.
      Не писать альтернативный loot engine; bridge/settings исправлять по evidence.
- [ ] Добавить scalar exact-identity native completion evidence. Sensor расположен у
      successful native effect, не при выборе target/команде attack/send packet.
- [ ] Сохранить kill attribution после aggro clear; bounded sequence/ring и overflow flag.
- [ ] Переписать Get-NativeM1Grades на identity/time-bounded evidence, frozen cohort;
      new selected proof нельзя брать у другого actor или из старого sample.
- [ ] A01–A10 negatives + native end-to-end positives, затем offline observer TEST.

## Workstream 5 — production M1 scenario matrix

Files: existing TEST environment + production composition + focused suite/launcher/build.
- [ ] W01–W08: настоящая scheduler → locality/readiness → materialization → Decision
      handler → native travel → AutoPlay/AutoUse → reward/loot → next target цепь.
      TEST не вызывает manual attach Decision/startAutoPlay для подмены production wiring.
- [ ] Воин и маг из native baseline; естественный выбор cohort, минимум 4 участников.
- [ ] Death/recovery, soft return/reentry, native region pin/pop-in controls,
      bounded concentration/route/water и persistence/restart проверены.
- [ ] Existing actual-attacker path диагностируется/regresses; PvP milestone не строится.
- [ ] Подготовить runtime runner и acceptance payload без вызовов к PLAY.

## Workstream 6 — интеграция, review, опубликованный артефакт

- [ ] VERIFY: новые focused suites + affected broad targets одним запланированным
      Ant invocation где возможно; init/compile/reports не перетирать повторно.
- [ ] Измерить bounded stress/perf/retained refs на native baseline и candidate;
      записи точных команд/exit/counts. Не устраивать бессмысленный full historical verify.
- [ ] Independent review: ownership/queue cancellation/store snapshot/ordinary players/
      travel/loot/truthful acceptance. Исправить реальные Critical/Important; повторить
      affected tests, затем итоговый combined GREEN.
- [ ] Exact-path production commit + normal push; проверить remote tree и ancestry.
- [ ] Clean JAR из codeSHA в чистом export/build path; hashes/dependencies/JDK25/Ant
      зафиксировать в ARTIFACT.json. Ни staged/untracked candidate, ни старый checkout
      не участвуют в сборке принятого артефакта.
- [ ] RESULT/HANDOFF + minimal evidence/RED_GREEN/changed paths опубликовать; различать
      codeSHA/reportSHA. Сохранённые 006 evidence не дублировать десятками файлов.
- [ ] STOP ENGINEERING_GREEN / READY_FOR_CONTROLLED_DEPLOY / M1_OPEN.
      Координатор проверяет remote и переводит runtime/TASK в следующий новый чат.
