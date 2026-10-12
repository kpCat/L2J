# IMPLEMENTATION039 — текущие проверенные блоки

## A02: progress до раннего resource return

Cause: `PhantomVisibleAutoPlay.noTargetExpired` наблюдал exact native evidence после
`resourcePause`; stock rest мог вернуть управление до учёта уже завершённого damage.
Одна immutable owner/epoch snapshot теперь учитывается перед resource/controller ветвями.
Нет обновления useful debt от отдыха, stop/replan или отсутствующего progress.
Player/Creature/native counters, sticky faults,45/90/120 и архитектура не изменены.

RED_A02_039 был INVALID_PRECONDITION: у нового TEST Monster отсутствовал normal
pre-World HP/MP baseline. Сохранён, не объявлен причинным RED. RED_A02B_039 на base Jar
получил настоящий stock cast damage и failed на неизменённом liveUsefulSinceNanos.
GREEN_A02_DEV0391/1; GREEN_A02_DEBT_DEV0392/2; GREEN_A02_RESOURCE_DEV0392/2. Remaining native/action gates
и final committed Jar-first пока ожидаются; development PASS не является final acceptance.

TEST-only guard039t добавлен в существующий PhantomHeadlessPlayerTestEnvironment;
старые exact lane guards сохранены. Новый native suite039 переиспользует fixture028/038.
Данные/положение/Monster baseline в native TEST не подменяют natural scene.

J1 Jar-first initial FAIL was a fixture command-gate race: liveCommandGate=true,
so noTargetExpired returned PAUSED before reaching the tested boundary. Preserved.
Bounded fixture wait respects that existing gate; RED_A02_GATE039 on base remained RED,
GREEN_A02_GATE_J1_039 passed1/1. No production adjustment for this fixture race.

## B: optional owning commit witness

BOUNDARY_MAP039 was written before this patch. PhantomBackgroundTransaction now captures
immutable scalar state/PNC writes from existing owning reads and encoded payloads, publishing
only after JDBC commit returns. Every actual increment has component/version/hash identity.
PREPARE retains intended receipt but never claims canonical AFTER checked. FINALIZE/recovery
records actual selected completed state, receipt/epoch, locked full inventory/skills and
the existing canonical comparison. VERIFY promotion remains a separate real commit.
SQL, locks, writes, rounding, fault points and business results are unchanged.
Callback failures cannot roll back a known commit; queue/capture errors remain proof GAP.
Disabled observer captures no payload; private collector filter selects all born profiles
and their subsequent background writes, rather than all unrelated1280 history transitions.
No Player/Connection is retained by exporter; existing ring512/buffer/disk budgets remain.

RED_BOUNDARY_J1_0392/2 missing owning seam; GREEN_BOUNDARY_DEV0392/2 and
GREEN_BOUNDARY_FILTER_DEV0392/2. P01 uses real native FINALIZE followed by actual MATERIALIZED
state increment and late SQL. This does not yet prove the natural background P01 scenario.
P04 verifies the real BEFORE_OWNED_PREPARE_COMMIT rollback was reached, emits no witness,
and a throwing observer does not change successful native store. Current all-birth G3 pending.
Affected BOUNDARY_Q_DEV039 passed15/15 with observer disabled. Final Jar-first repeat pending.

## Read-first и scope

Прочитаны AGENTS, master plan, workflow/package standard, packet039, handoff/result038,
build.xml, action planner/controller, progress/resource/owned-store paths и fixtures.
README.md, общий code-map и pattern-файлы не найдены. Есть readme.txt и SOURCE_MAP039.
JDK25/Ant/MariaDB3308; без новых библиотек, провайдеров, scheduler или schema.
Предел16 production files — утверждённый bounded exception SOURCE_MAP_NOTES; пока1.
Task-family packet/docs не расширяет production scope.

## Historical038

Все16 исходных IDs/epochs/FAIL и UNKNOWN сохранены в read-only HISTORICAL039.json.
B437 начал stable window в ROUTE без continuation session; это не доказанная cause A02.
B515/576 также начали с travel/start_retry, затем получили native cycles.
C215/415/615 имеют реальные replacement epochs; C594 отдельный lineage вопрос.
Полная причинная атрибуция этих сцен остаётся открыта. Старые FULL_SAVE UNKNOWN не
переименованы в corruption или PASS. Archive038 verified byte-exact, не перепакован.

## Ledger rulings

Утверждённый PLAN исполняется inline; no-subagents имеет приоритет над skill reviewer.
CHECKPOINT039 — ledger вместо нового SDD workspace вне SOURCE_MAP.
TASK039 RUNBOOK/GIT/BUDGET имеют приоритет над generic feature/main branch defaults
и прежним STOP038. Exact detached HEAD push разрешён; branch/worktree creation запрещены.
Источник отчётов расположен в task-family039 по более узкому task scope.
