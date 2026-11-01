# PHANTOM-LIVING-WORLD-112

Дата: 02.11.2026, Europe/Chisinau. **PARTIAL112 — безопасный архитектурный slice; SUCCESS112 не заявлен.**
Native materialization/World и sameDB STORE/restart пока NOT_RUN. Следующая задача автоматически не начинается.

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
Это реальный shared production planning stage + codecs. Полный JDBC historical advance/STORE не выполнялся;
метрика goalWrites0 означает отсутствие replacement decision, а не измерение SQL writer.
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
ecologyPermitted/admitted/materializeAttempt/success/native identities/WorldObserved для TEST112: UNKNOWN/NOT_RUN.

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

Geo203 source повторно прочитан и сверен по immutable TEST098 receipt, найденному через TEST111:203/203 SHA PASS,
1063452308 bytes, source mutations0. Tiles/Database.ini/старые JAR/accounts/секреты не копировались.
TEST112 DB0; GameServer JVM0; native campaign0; SQL probes/DDL/DML0. Полный G0 не объявлен PASS.
Native scope требует уточнения: TASK §10 разрешает уменьшить population/active/cap,
но исходный PhantomMerchants.ini включает bootstrap200 merchants с capital/stock; TEST111 отключал его в shadow.
Без отдельного разрешения shadow-only `EnableOfflinePhantomMerchants=False` безопасная ограниченная кампания не запускается.
Уточнение отправлено пользователю; это не причина ослаблять guards или выдавать деньги/цели/ACTIVE.
При разрешении следующий gate — fresh lawful census + полный G0, та же одна final JAR и максимум main/restart; автоматических дополнительных серий нет.

Scope guard:3136 archive source/test/config/phantom-data inputs, exact7 delivery paths;548 preimage SHA совпали.
Все существующие source bytes основного mixed checkout сохранены, WIP109/PK/C2/social не перенесены в commit.
После продвижения HEAD новые production bytes находятся в commit и внешнем кандидате; существующие файлы mixed checkout не заменяются.
Поэтому локальный diff может содержать обратные hunks TASK112 вместе с прежним WIP — это сохранение исходных bytes, не проверенный runtime source.
TEST111/MAIN/PLAY/старые DB, protected releases и чужие worktrees не изменялись.
Mojibake-маркеры в изменённых файлах проверены: PASS, все27 markers.
Escaped Cyrillic в изменённых файлах проверены: PASS, все6 regex, включая XML формы.

## Evidence, telemetry и delivery

Private evidence: `%TEMP%/phantom-living-world-112/`: PLAN,baseline.tar,preimages,manifests,RED/GREEN/Ant/regression/census/Geo/guard/publication receipts.
Весь пакет112 прочитан:7 файлов; initial SOURCE_MAP12; обязательные root/module/workflow/efficiency/status/master bounded sections и package standard.
Расширения exact symbol reads: CatchupState/Store, GoalStateStore/Repository, BackgroundService/Transaction — all-writer/CAS;
PopulationCatalog/Store, knowledge/topology records/ports и focused existing tests — реальный generator и DB-free fixtures;
System/ReconcileFirst/PresenceRegistry и Task102Authority/observer102 — demand/native G0; build.xml — финальная isolated сборка.
Предковых AGENTS выше Git root и отдельного module README.md не найдено; отдельный code-map/pattern file не найден, SOURCE_MAP является картой задачи.
Лимит initial searches превышен: ранний широкий поиск ошибочно включил .phantom-local, затем поиск ограничен exact symbols;
полный historical corpus повторно не сканировался. Точное число searches не инструментировано; не выдаётся за <=6.
Focused attempts:15 до final, включая compile/setup failures, +2 final mains; affected runners8; full Ant invocations1,verify0,jar target1; final guards2.
Расход перед публикацией:377237 tokens/1281s; ориентир400k ещё не превышен, это не полный billing total.
Code commit: `f93ec3d24c97e468edea1c51a758d4c90bfdd532`. Отдельный report commit и результат обычного push — private publication.json и финальный ответ.
Git разрешён TASK112/SAFETY/root policy. Bounded inspection: `rev-parse --show-toplevel HEAD`, `rev-parse @{upstream}`,
`symbolic-ref --short HEAD`, `status --short --branch`, `status --short --untracked-files=no`,
`status --porcelain=v1 -z --untracked-files=all -- L2J_Mobius_CT_2.6_HighFive PhantomSemanticStudio AGENTS.md README.md .gitignore .gitattributes`,
`worktree list --porcelain`, `ls-remote origin refs/heads/feature/phantom-world`,
`archive --format=tar --output <private>/baseline.tar <baseline> AGENTS.md L2J_Mobius_CT_2.6_HighFive`.
Delivery использует отдельный GIT_INDEX_FILE: `read-tree <baseline>`, `hash-object -w --stdin --no-filters`,
`update-index --add --cacheinfo 100644,<blob>,<exact-path>`, `write-tree`, `commit-tree <tree> -p <parent>`;
scoped `diff --cached --check/--name-only <parent> -- <exact paths>`; normal `push origin <commit>:refs/heads/feature/phantom-world`;
expected-old-value `update-ref` только feature/phantom-world и tracking ref, exact owned index entries.
Force/reset/stash/clean/rebase/merge/worktree mutation/add-all не выполняются.

**Gate status:** A PASS с UNKNOWN live gates; B/C planning-stage PASS, JDBC interval/restart NOT_PROVEN;
D DB-free PASS, live NOT_PROVEN; E NOT_RUN; F source/build/focused PASS с указанным baseline config assertion; G preservation/delivery receipts.
**STOP после безопасной поставки. TASK113 не запускать.**
