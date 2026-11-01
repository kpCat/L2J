# PHANTOM-LIVING-WORLD-112

Дата: 02.11.2026, Europe/Chisinau. **PARTIAL112 — архитектурный slice и единственная native кампания завершены; SUCCESS112 не заявлен.**
World/native-context handoff и sameDB STORE/restart подтверждены; положительная обычная цепочка с final population admission и current local demand у одного actor не доказана. TASK113 не начинается.

## Причины и результат

Baseline/local/upstream/live перед созданием кандидата: `d04cb6025da3081e6c7f764abe1e10816393d282`, `feature/phantom-world`.
Работа выполнена во внешнем physical `git archive` кандидате; WIP109 в него не переносился.

Idle storm: `HistoricalService.advance` проверял `idle.background.history` через farm-only `remainsSuitable`;
отказ вызывал `planOrIdle` → `idleFromState` → `replacePlan`, новый revision/planOrdinal перед каждой минутой.
TEST111 подтверждает revisions 58→1081 и 64→1072, но не содержит переходных receipts каждой записи.
Behavioral RED на исходном planner: `CANONICAL_IDLE_REJECTED_AS_FARM`; это подтверждение причины по production predicate, не новая live-трасса TEST111.

Теперь canonical idle проверяется отдельно по profile/status/anchor и authority hashes; visible farm parser по-прежнему его отвергает.
Historical planning stage возвращает прежний goal и planIdentity, когда отсутствие цели не изменилось; этот результат не входит в `replacePlan`.
Cursor/interval transaction не заменены: время, native attestation, rewards и replay остаются у прежних BackgroundService/Transaction owners.
Каждые 60 абсолютных игровых минут, со сдвигом по profileId, выполняется ограниченный штатный поиск.
Restart и новый catch-up request не сбрасывают этот период. Смена anchor запускает поиск через suitability;
progress/class/loadout/availability без version event обнаруживаются конечной coarse-перепроверкой, без отдельного таймера или кеша.
Knowledge/topology generation по-прежнему требуют штатного CAS renewal; существующий ранний recovery SUCCESS исправлен:
одного равенства authority hashes больше недостаточно. Generation-only mismatch воспроизведён RED→GREEN.
Reload race до/во время поиска возвращает RETRY без durable terminal failure и без продвижения cursor.
Чужой/неканонический idle root не переприсваивается текущему profile: центральный replaceFromState отказывает до планирования.

## Writer audit и admission

GoalStateStore/Decision и CatchupStore — существующие writers goal.runtime/background.catchup;
`replacePlan` сохраняет атомарный CAS обеих component versions. Renewal, stale/known recovery и visible publication сохранены.
Baseline/interval/native STORE writes остаются у BackgroundService/Transaction и native materialization с прежними claim/epoch fences.
Accepted journey проверяется до нового поиска, чужие Party/Store/Combat owners не отменяются.
Новых таблиц, codec versions, SQL writers, LLM/runtime dependencies, per-profile timers или rewards нет.

Причинная цепочка: catalog evaluate → desired calendar; ecology metadata/cursor/request fence → permitted;
Population regional/cap admission → effective state; processReadySchedule публикует ACTIVE как BACKGROUND;
physical human.local/accepted journey → bounded ecology due → ReconcileFirstActivityPort → штатная materialization → World identity/epoch.
Сам календарный ACTIVE не создаёт World. Ноль World без demand законен.
OperatorAdmissionProfile/LocalityTarget/HumanLocality уже дают адресный read-only diagnostic path; новый parallel decision engine не добавлен.

TEST111 nonsecret samples1/2, final-sql и G0 receipts прочитаны без записи/подключения к старой DB:
396 sampled rows READY/SLEEPING/World=false, observer отсутствовал; calendar/ecology/admission/current due reasons в этих receipts **UNKNOWN**.
Offline реконструкция нового birth cohort по нынешнему production generator при UTC 01.11.2026 20:53:51 даёт 8 calendar ACTIVE из12.
Это conditional census generation=1, ordinal=profileId, no replacements; не доказательство persisted schedule/phase или ecology permit TEST111.
Из этих данных нельзя объявлять отсутствие World scheduler defect.

## Exact delivery scope

Пути относительно `L2J_Mobius_CT_2.6_HighFive/`:

1. `java/org/l2jmobius/gameserver/phantoms/background/PhantomHistoricalBackgroundPlanner.java`.
2. `java/org/l2jmobius/gameserver/phantoms/background/PhantomHistoricalBackgroundService.java`.
3. `java/org/l2jmobius/gameserver/phantoms/population/PhantomPopulationStore.java`.
4. `test/java/org/l2jmobius/gameserver/phantoms/background/PhantomLivingWorld112Checks.java`.
5. `test/java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomLivingWorld112Knowledge.java`.
6. `test/java/org/l2jmobius/tests/phantoms/PhantomLivingWorld112PresenceChecks.java`.
7. `docs/phantoms/reports/PHANTOM-LIVING-WORLD-112.md`.

PopulationStore: извлечена неизменная birth schedule/phase formula в `scheduleForShell`, используемую самим createShell и offline census;
это позволяет проверять source generator без копии формулы и без DB/bootstrap. Ни distribution, ни schedule XML не изменены.
Knowledge test fixture — только unit facts на существующем immutable query port; в server/native она не устанавливается, goal не инъецируется.

## Проверки и предел доказательств

`Focused112.ps1`: behavioral RED→GREEN canonical idle; generation-only recovery; transient reload terminalization; foreign idle owner.
Первые подготовительные ошибки: JDK sandbox ACL, неверный fixture enum/source/farming evidence, отсутствующий import; они не считались behavioral RED.
`Final112.ps1` на fresh Ant classes: PASS, exit0.
1000 виртуальных минут: cursor model0→1000, idle revision7→7, planOrdinal4→4, unchanged-goal replacements0,17 target searches.
Codec rebuild на500-й минуте и при ожидании wake не сбрасывает cadence; valid authority target даёт farm на1025-й минуте, тот же goalId, revision8.
Это реальный shared production planning stage + codecs; дополнительный фактический JDBC idle/STORE/restart результат приведён ниже.
Метрика goalWrites0 в DB-free test означает отсутствие replacement decision, а не инструментированный счётчик SQL writer.
LOAD_BOUND:5000 identities,121 виртуальная минута,605000 planning calls,10083 authority viability probes,goal changes0,738ms,threadDelta0.
Это planning-stage bound; не native soak5000, не SQL/memory benchmark и не новая production scan policy.

| Calendar | Current local demand | Production bridge result | Delegate attempts |
|---|---|---|---|
| SLEEPING | absent | DEFERRED / presence.no_current_local_demand | 0 |
| SLEEPING | present | DEFERRED / presence.no_current_local_demand | 0 |
| ACTIVE | absent | DEFERRED / presence.no_current_local_demand | 0 |
| ACTIVE | present | SUCCESS до test delegate | 1 |

Capacity admission=false, TTL expiry и human disappearance: DEFERRED, delegate не вызван.
Ecology-fenced/native-handoff/preparation-capacity negatives отдельно проверены production readiness102 и ecology runners.
Эта матрица использует test delegate и не является World evidence.
Source v3/UTC/LIVING census для текущего UTC 01.11.2026 22:18:55: ACTIVE 8/12,44/64,90/128,178/256,3500/5000;
Runtime counters TEST112 ниже отделены от этого offline census; полные materializeAttempt/SQL-write counters не инструментированы, это UNKNOWN.

Affected DB-free runners: scheduler36/36; population-schedule3/3; readiness1023 groups;
presence0964/4; calendar0653/3; historical-visible0686 groups; background-model10/10 — exit0.
Ecology runner:35/36,exit1. Единственный FAIL `01-strict-catalog-config-and-codec` ожидает shipped-disabled,
но baseline owner config уже содержит EnablePhantomSystem=True/EnablePhantomEcology=True. Оба файла byte-exact;
35 behavioral checks, включая CAS, orphan ownership, STOP и restart, PASS. Assertion не отключался и конфиг не переписывался.
Независимый read-only source review: P1=0/P2=0 для exact safe slice; найденные owner/transient P2 закрыты RED→GREEN.

Одна финальная команда: `ant -f <evidence>/readonly-build.xml -Dbuild=<evidence>/final-build compile-tests jar`,exit0,35s.
2380 production/456 test sources;5 прежних warnings; owner-profile38 PASS READ_ONLY.
Wrapper сохраняет archived config, stock owner validation работает; Apply на source не выполнялся.
Один final GameServer JAR SHA256: `bc4c3c55f72074aaa86444be03d6a6c4e722269f0054174b56df5d94d878e1f2`.
Ant verify0; дополнительные GameServer JAR builds0. Stock LoginServer/DatabaseInstaller outputs в delivery не входят.

## Native/G0 и сохранность

Пользователь отдельно разрешил `EnableOfflinePhantomMerchants=False` только в TEST112 shadow; исходный конфиг byte-exact.
Stage: `%TEMP%/Phantom-Living-World-112-88e28401-2191-4b5f-a480-a57af7394acc/`, private owner+SYSTEM ACL, physical/disjoint.
Из старой verified TEST098/111 среды скопированы только203 `.l2j` tiles; source/target203 SHA повторно PASS после кампании,1063452308 bytes, source mutations0.
Старые Database.ini/accounts/JAR/секреты/approvals не переносились; новый DB config получен из текущего source с exact origin SHA.
Shadow-only: population12/active4/cap4, merchants False, game loopback17777, private DB112/pool4. Scheduler budget/production schedules/clock/rates не менялись.
Полный G0 PASS:29078 native pins/29073 module inputs, same JAR, Geo203, SID/ACL/cwd/physical/source, OS Java/TCP/SQLTCP0,
full metadata visibility и absent schema/global SQL sessions0, stock SQL dry-run121 files/203 statements/18 DROP omitted. До CREATE input/HEAD parity повторены.
Создана ровно одна absent-only `l2jmobiush5_phantom_test_112`; собственная authority с UUID/source9c5a3a6c/inputs pins, без adopt/reseed/drop/delete/truncate.
Private harness прошёл независимый P1/P2=0 review: закрыты partial-observer cleanup/stock Shutdown и destination guard;
pure negative replay/exit0-only/postflight/UUID/cleanup/destination tests PASS. Первый prepare format отказ PS5 возник до любой записи;
исправлена JSON-array упаковка,203 hashes проверены повторно в том же shadow. Реальных G0 failures0; новых stages/серий нет.
Две последовательные GameServer JVM, один JAR/DB: main180s samples → graceful STOP/exit0/postflight → restart90s samples → STOP/exit0/postflight.
Фактические от game.begun до exit0:276.152s/149.346s, включая boot/setup/stop. Оба boot загрузили203 regions.
Pre-cut observer268485155 — disabled account/native Player без GameClient, lease LOCALPLAY_TEST_HUMAN по102; только его XYZ устанавливались до измерений.
Profile1/object268484271 фактически World-present с exact native owner: epoch373784361941800 main и374157981058800 restart;
World/exact-owner rows17/8; server-visible-object + surrounding-region rows4/4. Это server-side visibility, не network/client known-list QA.
Фактическое движение и STORE: position profile1 после main(-117176,47853,456), после restart(-118944,45730,369); агент Phantom XYZ не менял.
Календарные ACTIVE sampled rows144/72; population eligible/admitted0/1; human.local10/7; один distinct native actor, два epochs, successful native loads минимум2.
Во всех World cuts profile1 входил через законный native-context handoff; final population admission для него оставался ecology_fenced.
Причина: initialCatchupComplete=false/requestPending=true, committed cursor29869725 против текущего горизонта≈29892925; текущий World owner исключается из background preparation.
Не объявляется самостоятельный scheduler defect: отдельные due reasons native_context.required/preparation_capacity/commit_pending сохраняют owner/fence.
Profile11 достиг ordinary admitted=true в конце restart, но humanLocal=false/World=false — законный отрицательный случай, не положительная вся цепочка.
Native idle9: revision1→1/planOrdinal1→1,cursor29887015→29890963; idle11:1→1/1→1,cursor29889772→29892931 за90s.
Это настоящий JDBC historical path без прежнего revision storm; те же idle до JVM restart не наблюдались, отдельный SQL idle-restart claim не делается.
Checkpoint/первые restart cuts:12/12 same profile/object/goalId,12/12 cursor не регрессирует; same request либо законная sequential renewal. Final identity sets совпали.
Оба drain receipts configured=false/retained materializations0; postflight:13 characters(12 ordinary+observer),12 profiles,86 components, online0/offline stores0, global foreign SQL0/JVM0/gameTCP0.
Новых ручных наград/целей/skills/levels/ACTIVE/Phantom teleport0; native combat/PvE earnings не доказаны, background canonical progress не подменяет их.
Heap max samples3861779408/4031854696 bytes, threads133/135; CPU/полный SQL query/write count UNKNOWN. Не native soak5000.
GameServer JVM2, new DB1, final JAR builds1; дополнительные native серии/кампании/схемы/сборки после этого не выполняются.

Scope guard:3136 archive source/test/config/phantom-data inputs, exact7 delivery paths;548 preimage SHA совпали.
Все существующие source bytes основного mixed checkout сохранены, WIP109/PK/C2/social не перенесены в commit.
После продвижения HEAD новые production bytes находятся в commit и внешнем кандидате; существующие файлы mixed checkout не заменяются.
Поэтому локальный diff может содержать обратные hunks TASK112 вместе с прежним WIP — это сохранение исходных bytes, не проверенный runtime source.
TEST111/MAIN/PLAY/старые DB, protected releases и чужие worktrees не изменялись.
Mojibake-маркеры в изменённых файлах проверены: PASS, все27 markers.
Escaped Cyrillic в изменённых файлах проверены: PASS, все6 regex, включая XML формы.

## Evidence, telemetry и delivery

Private evidence: `%TEMP%/phantom-living-world-112/`: PLAN,baseline/preimages,RED/GREEN/Ant/regression,census,Geo/guards,native-analysis/native-final-guard/publication; native stage/private — sealed pins,G0/SQL/samples/checkpoints/stop/postflight.
Весь пакет112 прочитан:7 файлов; initial SOURCE_MAP12; обязательные root/module/workflow/efficiency/status/master bounded sections и package standard.
Расширения exact symbol reads: CatchupState/Store, GoalStateStore/Repository, BackgroundService/Transaction — all-writer/CAS;
PopulationCatalog/Store, knowledge/topology records/ports и focused existing tests — реальный generator и DB-free fixtures;
System/ReconcileFirst/PresenceRegistry, Task102Authority/probe/observer102, IdentityLeaseRegistry — native G0/cleanup; MerchantService/Bootstrap — проверка scope; build.xml — isolated сборка.
Предковых AGENTS выше Git root и отдельного module README.md не найдено; отдельный code-map/pattern file не найден, SOURCE_MAP является картой задачи.
Лимит initial searches превышен: ранний широкий поиск ошибочно включил .phantom-local, затем поиск ограничен exact symbols;
полный historical corpus повторно не сканировался. Точное число searches не инструментировано; не выдаётся за <=6.
Focused attempts:15 до final,+2 final mains; affected runners8; Ant1/verify0/jar1; native private javac3/pure phase1/destination1/positive stage1; final scope/text guards2 на каждую поставку.
Последний доступный Goal-counter после BLOCKED:400350 tokens/1540s; native продолжение после разрешения не учитывается этим остановленным счётчиком, полный usage UNKNOWN.
Code commit: `f93ec3d24c97e468edea1c51a758d4c90bfdd532`; initial report/push `9c5a3a6c3bee33b6b5374a6a34a29bb9de8b250a`. Native report-only commit/push — private native-publication.json и финальный ответ.
Git разрешён TASK112/SAFETY/root policy. Bounded inspection: `rev-parse --show-toplevel HEAD`, `rev-parse @{upstream}`,
`symbolic-ref --short HEAD`, `status --short --branch`, `status --short --untracked-files=no`,
`status --porcelain=v1 -z --untracked-files=all -- L2J_Mobius_CT_2.6_HighFive PhantomSemanticStudio AGENTS.md README.md .gitignore .gitattributes`,
`worktree list --porcelain`, `ls-remote origin refs/heads/feature/phantom-world`,
`archive --format=tar --output <private>/baseline.tar <baseline> AGENTS.md L2J_Mobius_CT_2.6_HighFive`.
Delivery использует отдельный GIT_INDEX_FILE: `read-tree <baseline>`, `hash-object -w --stdin --no-filters`,
Для report preimage: `ls-tree <parent> -- <exact report path>` и read-only `hash-object --stdin --no-filters`; backup вне checkout с SHA.
`update-index --add --cacheinfo 100644,<blob>,<exact-path>`, `write-tree`, `commit-tree <tree> -p <parent>`;
scoped `diff --cached --check/--name-only <parent> -- <exact paths>`; normal `push origin <commit>:refs/heads/feature/phantom-world`;
expected-old-value `update-ref` только feature/phantom-world и tracking ref, exact owned index entries.
Force/reset/stash/clean/rebase/merge/worktree mutation/add-all не выполняются.

**Gate status:** A PASS по source/live gates; B stable JDBC idle PASS, same-idle SQL restart отдельно NOT_PROVEN; C DB-free wake/generation PASS;
D DB-free PASS и live reasons доказаны, ordinary admitted+local-demand+World у одного actor NOT_PROVEN; E native handoff/World/STORE/restart identity PASS, полный PvE loop NOT_PROVEN;
F source/build/focused PASS с baseline config assertion; G сохранность и scoped delivery receipts. **FULL112 остаётся PARTIAL.**
**STOP после безопасной поставки. TASK113 не запускать.**
