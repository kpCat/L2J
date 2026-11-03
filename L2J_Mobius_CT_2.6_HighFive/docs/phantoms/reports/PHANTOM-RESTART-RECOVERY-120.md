# TASK120 — restart recovery: причинный аудит и предел доказательства

Статус: **PARTIAL / NO_SAFE_FIX**. FULL_RECOVERY **NOT_PASSED**, OWNER_VISUAL_PENDING.
Выполнены source-review, разбор сохранённого raw119 и targeted DB-free проверки.
Причинный production RED не получен; безопасное исправление не доказано. Production-код не изменён.
Новый native120 не запускался. STOP120; TASK121 не начиналась.

## Baseline и сохранность

Live GitHub `kpCat/L2J`, `feature/phantom-world`: exact `982a7c993aa83a771e0cb442b0764dab4acf6da0`.
SHA независимо получен GitHub connector и `git ls-remote`; перед публикацией требуется повторная проверка.
Основной checkout: `236b965a116bbc79cccbef78b04c628225a629cb`, владелец ZBook, dirty WIP109 сохранён.
Сделан read-only status snapshot106 entries и SHA54 существующих dirty/runtime файлов.
Дополнительно проверены все1235 protected preimages из guard119: size/SHA совпали, changed0.
Основной checkout, прежние worktrees, runtime109, TEST109–119, MAIN/PLAY и release057–061 не изменялись.
К старым БД не подключались; SQL/DDL/DML не выполнялись. Geo, клиент и MariaDB не изменялись.

Создан новый физический sparse clone без hardlinks, только High Five и root files:
`D:/Codex/worktrees/phantom-restart-recovery120-source/L2J-phantom-world`, detached exact119.
Перед работой status чистый. Pinned commit отсутствовал в object store основного checkout;
fetch/checkout в защищённой основной `.git` не выполнялись.
Публичный allowlist состоит ровно из этого отчёта; task package, private helpers, logs и binaries не stage.

## Прочитанные материалы и bounded exception

Initial READ_SET12: все7 файлов120, root/module AGENTS, workflow, efficiency standard, report119.
Report119 отсутствует в историческом main checkout: прочитан из pinned GitHub commit, доступен в новом clone.
Root/module AGENTS, workflow и efficiency standard нового clone SHA-equal прочитанным main версиям.
Вышестоящие AGENTS и module README.md не найдены; readme.txt существует и прочитан bounded.
Использован SOURCE_MAP120; отдельный code-map не искался повторно.
Обязательные дополнительные документы: bounded master-plan, current-status, task-package-standard, readme.txt.
Mapped production ranges: SupplyJourney advance/maintenance/travel/alternative/terminal/acceptRoute;
HistoricalBackgroundService prepareVisibleDecision/publication/VisibleFailures; VisibleAutoPlay start/stop/yield;
VisibleFarmTravel admission/walk/retry/Attempt/NativeWalkBudget; CompositeTravelPlanner plan.
Дополнительные exact production files: HistoricalBackgroundPlanner, BackgroundGoalSpec,
SupplyIntentStore, DecisionEngine; прочитаны только нужные symbols/ranges.
Test/build reads: Supply071, Living111, Life116, EarnedMarket119, Economy118, HistoricalIdleVisible068,
VisibleItinerary068, TestLauncher, build.xml; bounded Admission/IntentRecovery показали DB/native dependency.
Private119 read-only: summary, selection2, life2-7, samples2, stop2, protected manifest, geo receipt,
Run119/Prepare-Native119/COST119. Старые helpers не исполнялись и не менялись.
Расширение сверх5 дополнительных файлов и6 rg invocations вызвано новым доказанным узлом
`travel.native_progress_stuck`, проверкой fresh Attempt и отличием DB-free fixtures от native fixtures.
Всего10 rg invocations; полных чтений source-файлов >1000 строк0; другие хроники0.
Избыточный initial `status --untracked-files=all` заменён компактным сохранённым `normal` snapshot.

## Уточнённая причинная последовательность119

Это **исторические raw119 evidence**, а не новый runtime120 и не доказательство физического пути.
Один NORMAL: profile7/object268485477/class38, restart epoch530703692810800.
Root1510518908355263494, target20529, anchor `population.farming.dark-elf.20529`.
18:59:29Z: root rev3 ACTIVE; supply rev49 ACTIVE, item426, actor268485477,
Binding.field `(25061,12665,-3632)`, original deadline1793732702185.
Receipt rev44 COMMITTED: BUY3947×124/4960A, operation69bd131800f55e682c1559ab2230b7172e78584b4346f05bdc688930f48edccd.
Сохранены EXP1260/SP120/cash738/stock118; это ранее заработанное/оплаченное, не новый income.
`ROUTE_BUDGET_EXCEEDED`: `(18554,19226,-3631)` → `(10935,18013,-4552)`.
Existing acceptRoute уже включает ограниченный NativeWalkBudget; натуральные detour moves продолжались.
К19:05:02Z actor достиг `(19269,21139,-3312)`. Это не merchant arrival и не признанный farm checkpoint.
После deadline supply стал FAILED/rev50; terminal сохранил child, снял demand и вернул REPLAN.
19:05:12Z: root rev4 ACTIVE с тем же20529; выбран `candidate.background.farm`,
WAITING_RETRY, `background.visible.start_retry`, фактическое положение `(19880,20340,-3312)`.
19:05:22Z: REPLAN `travel.native_progress_stuck`, затем `LOCAL_FARM_UNAVAILABLE`.
До19:10:34Z новых rewards0; EXP/SP/cash/stock прежние, HP137/137, MP132/132,
nativeFirstUnprovenReason=NONE, nativePhase=NONE, AutoPlay session/registrations отсутствуют.
Следовательно, supply terminal **не удерживает** root навечно: farm replan и попытка farm travel состоялись.
Непосредственный следующий блокер — native farm travel STUCK и последующий отказ replacement admission.
Физическая причина STUCK и отсутствие иной пригодной дороги/цели остаются **UNKNOWN**.

## Source-review и решение без спекулятивной правки

SupplyJourney879–882 сохраняет pending/INCONSISTENT maintenance receipt для reconciliation;
1288–1301 выполняет canonical cleanup/save FAILED/releaseDemand. Root остаётся ACTIVE;
NEEDS_REPLAN в raw — runtime Decision state, не persisted goal status.
Historical1361–1390 проверяет suitability/travel, route failure и local replan;
любой replacement.notReady отображается как LOCAL_FARM_UNAVAILABLE. Этот label не доказывает no-path.
Publication1398–1448 сохраняет atomic catchup/goal CAS и exact runtime reload.
VisibleFarmTravel908–926/1367–1369 создаёт новый Attempt при rev3→4:
старый шестиминутный supply timeout не объясняет fresh root4 STUCK.
Route/tracker cancellation1092–1105 и exact Player/epoch/root checks не заменялись.

Кандидат дефекта: retryOrFail877–894 очищает route/tracker без stopOwnedMove;
при сохранившемся isMoving новый MOVE_TO707–719 может не выдаваться. Локальный аналог replanEdge903
прекращает captured owned movement до retry. Но raw119 не содержит пары issued destination A/new route B
и isMoving=true в точный момент retry. Этот кандидат **не объявлен root cause119**.
Без такого witness/реального DB-free RED добавлять остановку движения было бы спекулятивно.
Существующие068/111 fixtures проверяют proposals/budgets/codec, не этот private Player transition.
Новый тест-проект, искусственный actor/reward и бессмысленный повтор11-минутного idle не создавались.

Writer map: root/child CAS и Goal codec; Commerce immutable receipt/reconcile и inventory;
materialization ActionLease/native owner/epoch; supply Trip и owned move cleanup;
historical scheduler/publication/checkpoint; VisibleFarmTravel Attempt/tracker; shared AutoPlay/AutoUse pools.
Новых writers/locks/timers, глобальных scans и hot-path logs нет. Exception swallowing в prepareVisibleDecision
сохраняет fail-closed; _visiblePreparationStages может быть перекрыт episode.reason.
Канонические оплаты, CAS, deadlines, proof debt и paid stock не менялись; количество production fixes0.

## Выполненные проверки

JDK25.0.4.1, Ant1.10.17. Все команды выполнялись в новом clone; stdout/stderr сохранены private.
`ant -f .\L2J_Mobius_CT_2.6_HighFive\build.xml compile-tests`: exit0,26s.
Existing5 compiler warnings:3 ExtendedOpenOption и2 System.runFinalization; ошибки0.
До/после compile все source configs size/SHA equal: drift0. OwnerConfig288 не изменён.
Launcher с seed12012001: `life-scenarios116`32/32, `supply071`9/9, exit0.
`PhantomLivingCycle111Test`:8 focused groups PASS, exit0.
`PhantomEarnedMarket119Checks`:5 selector contrasts PASS, exit0.
`PhantomHistoricalIdleVisible068Checks` и `PhantomVisibleItinerary068Checks`: PASS, DB_CONNECTIONS0/ACTORS0.
`PhantomEconomy118Checks <own-game-root> <fresh-economy118.tsv>` из own dist/game: exit0,
DB_FREE_PASS; source ID57 amount5/chance1; NPC corridor и opportunity checks PASS.
Часть timestamp labels JVM в console имеет повреждённое отображение; исходные файлы не переписывались.
Old-policy causal RED=NOT_OBTAINED; new-policy GREEN=NOT_APPLICABLE, production policy не менялась.
Existing focused GREEN не подменяет regression RED→GREEN и не доказывает native recovery.

## Gate и ограничение результата

RESTART_REWARDS=NOT_RUN120 / NOT_PROVEN.
ROUTE_HANDLING=PARTIAL: existing bounded fallback и последующий STUCK подтверждены; physical cause UNKNOWN.
PAYMENT_REPLAY=NOT_RUN120; сохранность receipt/stock119 — только historical evidence.
WEALTH=UNKNOWN. Gate A causal safe fix NOT_PASSED; FULL_RECOVERY NOT_PASSED.
Fresh full G0=NOT_RUN; TEST120 stage/schema0, native starts0, restart0, new JAR0.
Без новой причинной гипотезы повтор native не обоснован; обязательный native gate — fresh full G0.
Protected TEST119 не переиспользовалась; до полного G0 ни один GameServer120 не запускался.
Общий ant verify0: содержит DB gates. Focused runs7, standalone jar0, native elapsed0.
Pre-report Goal snapshot391953 tokens/663s; USD не измерялись. Final usage — в delivery receipt.
Основные лишние расходы: большой initial git status output, получение sparse clone blobs и дробное чтение.

## Review и публикация

Независимый read-only source review подтвердил NO_SAFE_FIX и отсутствие доказательства inherited timeout.
Независимое review140 строк отчёта и exact scope: P1=0/P2=0 FOR REPORT-ONLY; уточнена причина skip native.
Final report/scope review, две отдельные encoding проверки и exact one-path guard обязательны перед commit.
Mojibake-маркеры в изменённых файлах проверены: полный набор27, совпадений0, exit0.
Escaped Cyrillic в изменённых файлах проверены отдельно: все6 regex, совпадений0, exit0.
Первый inline PowerShell mojibake-check завершился exit1 без stdout; заменён UTF-8 Python guard.
Публикация разрешена TASK120 только scoped non-force в feature/phantom-world; при drift STOP.
Git использован для pin/isolation/scope/delivery; разрешённые TASK120 формы:
rev-parse, symbolic-ref, status, worktree list --porcelain, for-each-ref, ls-tree -l, ls-remote exact ref;
reviewer: status --short --untracked-files=normal и diff --name-only -- <exact report>;
clone --no-hardlinks --single-branch --branch feature/phantom-world --filter=blob:none --no-checkout;
sparse-checkout set L2J_Mobius_CT_2.6_HighFive; checkout --detach exact119 в новом clone;
diff --check -- <report>; add -- <report>; diff --cached -- <report>; commit -m <task120 subject>;
push https://github.com/kpCat/L2J.git HEAD:refs/heads/feature/phantom-world и live HEAD check.
Plain sandbox ls-remote сначала exit1 из-за network restriction, privileged retry exit0; это не drift.
Local rev-parse --verify exact119 в main exit1 подтвердил отсутствие object; refs не менялись.
Commit SHA/push/final guard receipts публикуются в финальном ответе без self-reference SHA в этом файле.
STOP120. Следующее необходимое evidence — issued native destination/route/ownership при farm STUCK;
его получение требует отдельно обоснованного безопасного scope, а не повторов того же idle.
