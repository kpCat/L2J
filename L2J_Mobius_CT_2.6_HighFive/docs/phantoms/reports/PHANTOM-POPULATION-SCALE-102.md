# PHANTOM-POPULATION-SCALE-102 — PARTIAL, STOP

28.10.2026. **FULL102=PARTIAL; CORE102=PARTIAL; WORLD102=PARTIAL; OWNER102=PARTIAL.** Реально получены **500 различных ordinary durable READY profiles, 500 native character IDs и 500 registered**, но устойчивый ordinary World=0. Фазы1000/3000/5000, World128 и sameDB/sameJAR restart — **NOT_RUN**. Source OwnerConfig сохранён, fresh-stage Apply/Verify и effective compiled parser5000/128/128 подтверждены; финальная native generation5000/128/128 не запускалась. OWNER_CLIENT_QA_PENDING, PLAYABLE/финальный релиз не объявляется.

## Baseline и безопасность

Фактический Git root определён через rev-parse; ветка feature/phantom-world, upstream origin/feature/phantom-world. Starting HEAD и свежий live feature перед CREATE совпали: `9245f95aa6c4dd7c50df47c71cd68f2aa4b0b9d0`. Это проверенный текущий baseline, а не принудительное возвращение к ancestor. Существующий untracked WIP, task packages и старые отчёты не включались в задачу.

Единственная новая физическая external OWN102 shadow: owner SID/source owner совпадают, ACL только owner+SYSTEM, без reparse,203 Geo проверены по pinned SHA. JDK Temurin25.0.4.1, Ant1.10.17; native cwd — shadow HighFive/dist/game. Собственный integration runner использует реальную production composition, без запуска LoginServer/GameServer listeners. Порты2106/7777 свободны. Общая MariaDB3308 оставлена работающей, её настройки/grants не менялись.

Metadata-only G0: full visibility,161 существующая schema, **schema102 ABSENT, foreignSessions0**. После аудита121 stock SQL-файла dry-run получил203 statements;18 исторических DROP TABLE IF EXISTS пропущены до первого DDL. Создана только `l2jmobiush5_phantom_test_102`, create-only, без DROP/reset/reseed/grants. Authority UUID/source/input SHA записаны в own102_authority. Ни одна защищённая TEST101/100/099/096N/MAIN/PLAY или release DB для доказательств не использовалась; их payload не читался.

## Owner gate

Все287 overrides и68 публичных INI проверены каноническим OwnerConfig.ps1, включая его Java verifier38/38. Source Save намеренно пропущен: он необязателен, уже сохранённые значения не регенерировались. Существующие четыре file/key/kind/baseValue/value tuples сохранены ровно: EnablePhantomSystem boolean False→True, PopulationTarget long0→5000, ActiveTarget long0→128, MaxMaterialized long32→128. Другие overrides, burst0, scheduled10000, creation2, boundaries64, pulse100ms/profiles128 не менялись.

В отдельной owned baseline-копии только четыре INI-ключа возвращены к saved base. Канонический Apply changed1 → Verify changed0 → repeat Apply changed0 → Verify changed0. Все hashes staged config после повторного Apply совпали. Invalid-type ActiveTarget отклонён Apply с conflicts1/changed0; disposable INI восстановлен, Verify снова0. Source config hashes до/после parity неизменны.

Actual build stage получил Apply/Verify до Ant и до runtime overlays. Собранный JAR через PhantomPlayersConfig.read подтверждает True/5000/128/128, burst0/scheduled10000;129 ACTIVE и10001 population дают disabled/fail-closed. После native STOP stage возвращён к owner-effective5000/128/128, parser повторно PASS. **Это readback файла на final JAR, не loaded native runtime5000.** Отдельная [матрица parity](PHANTOM-POPULATION-SCALE-102/owner-config-parity.tsv) сохраняет эту границу.

SHA256 source bytes:

| Файл | SHA256 |
|---|---|
| OwnerConfig.json |5776e83810689da1acf94d0a3d8ee54d204e87ca0bf7488e685034b57c61f4e8|
| Custom/PhantomPlayers.ini |55043b107c918d5232b838cb56583ab3dfda2b4530b2c5321fddf35c660180b2|
| OwnerProfile38.tsv |4f25d9419b1a01aff2792a29f09a6b12f6fc464314ace73419ea4590a6ad3244|
| OwnerProfilePreserved.properties |c49b6a7e3aa04f55c287dd28212ef9935b478aa78e31d74c2e6caa2d8712e9fc|
| Rates.ini |ba9c6fcbd364e09588f50a4d507d06fc9f21f0bfeaaba229977b44124db819f2|

## Единственная bounded campaign

**Первый phase500 — RED startup.** Native infrastructure не загрузила supported owner class.warrior-q401/profession; PhantomQuestInstanceCatalog.validateRuntime остановил production start. Ordinary durable/characters0. Сохранилась одна nonordinary service metadata profile, она не удалялась и не засчитывалась. Shutdown/drain и pool close штатные, postflight ownSessions0/JVM0.

**Один causal retry использован полностью.** Причина локализована в harness: existing ordinary069 analogue явно вызывает InitialEquipment/InitialShortcut/MapRegion и PhantomSupportedContentScriptBootstrap.loadGoal036Owners. Добавлены те же source-backed bootstrap calls только в TASK102 harness. JAR и DB прежние, ordinary reset/выдач/fixtures нет. Старые source/class/runner preimages и RED receipts сохранены побайтово с SHA вне checkout; original receipts не перезаписаны. Новый generation имеет отдельные receipts и native input manifest; original DB authority/source-origin manifest после CREATE не менялись.

**Retry достиг500 READY, затем WORLD gate RED.** Canonical PopulationManager/Store/Scheduler самостоятельно создали и зарегистрировали500 identities. JDBC join population.state → phantom_profiles → characters подтвердил500 разных ordinary native IDs и владельцев; merchants/observers исключены. Создание занимало108531ms от начала native infrastructure. Durable ID+character+initialization hash manifest:500 строк, SHA256 `af55ab2936b55a48b24fe6cc3afd79802bb184157f12796bc35f1bf1d009ae8f`.

После census добавлены6 явных LOCALPLAY_TEST_HUMAN native Player на source-backed committed positions. У них собственные disabled accounts/access0 characters, leases, online World presence; нет fake GameClient/headless observer proxy или произвольных точек в predicate. Это **controlled human-demand**, не unattended density. Фантомы не перемещались setXYZ/teleport и не материализовывались прямыми вызовами harness.

Два bounded60s окна без World progress, затем две фактические выборки с интервалом30s: **World0/0**. Final desired400, eligible0, admitted0, registered500, inconsistent0, pendingCatchup499, localSignaled0, periodicDueCalls0. Ecology lastFailure=`native_context.required:accepted`,643 failure observations; это aggregate retry reason, не643 исключения Java. Source permitsScheduling требует initialCatchupComplete/no pending request; production locality фильтрует candidates по eligible, а native-context handoff требует current demand. Зафиксирована блокировка на этой границе, но новая product correction и её causal RED→GREEN **NOT_RUN**: после второго независимого блокера и исчерпанного retry задача требует STOP.

Structured21 samples все дали ordinary World0; stdout один раз видел краткий World1 во время birth/ramp между отдельными чтениями. Это не steady World proof и не основание пройти P500. Нельзя подменять500 saved READY,400 desired илиcap16 фактическим World. Поэтому checkpoint500 целиком не пройден, последующие phases не запускались. [Scale results](PHANTOM-POPULATION-SCALE-102/scale-results.tsv) содержат реальные числа и NOT_RUN.

В штатной composition параллельно работал неизменённый dedicated merchant owner: последняя выборка250 native owners/187 nonempty stores. Все они исключены из ordinary counts. Это не новая merchant-scale приёмка. После STOP SQL содержит756 characters =500 ordinary +250 dedicated +6 test witnesses,501 profile rows и187 offline store rows. Raw totals не выдаются за ordinary population.

## Изменения, read-first и scope

Прочитаны root AGENTS/README, HighFive Agents.md, bounded master-plan/workflow/efficiency/package standard, весь TASK102 package, owner contracts/profile/targets. Дополнительные AGENTS в целевых java/test/tools/docs каталогах не найдены. READ_SET.md служит найденной картой; повторных поисков отсутствующих pattern-файлов не было. Локальные аналоги:099 prepare/create-only SQL,101 authority/seal,096 native identity safety;069 supported-content bootstrap прочитан по конкретному startup RED.

Начальный рекомендованный read/search budget превышен из-за mandatory package, узких API seams, двух review findings и startup diagnosis. Большие System/Player/World целиком не читались. Дополнительные bounded symbols: PopulationManager start/reconcile/control/admission, Store creation/native identity, Ecology scheduling/due, Scheduler snapshots, Materialization/MaterializedPlayer world identity, Locality onPulse/humanObserver, test authority/native infrastructure, StarterShotService/Budget для source policy0. Исторические большие campaigns не перечитывались.

Bounded exception **14 файлов**, одна population-scale capability:

- Production: PhantomSystem.java — только read-only operatorPopulationSnapshot, без нового scheduler/engine/writer.
- Existing tests: PhantomHeadlessPlayerTestEnvironment.java — отдельная OWN102 lane, запрет generic mutable fixture; PhantomPopulationPerformanceSuite.java — canonical admission IDs отдельно от physical ACTIVE signals.
- New tests: PhantomTask102Authority.java, PhantomTask102Lane.java, PhantomPopulation102Native.java — pre-JDBC authority, native composition, real census/cohort/checkpoints.
- Tools: Task102.ps1, Task102Run.ps1, Task102OwnerParity.ps1, Task102Preflight.java, Task102DatabaseProbe.java — own preparation/config parity/guarded campaign. Нет автоматического повторного reseed/adopt.
- Docs: этот report, scale-results.tsv, owner-config-parity.tsv.

Конфиги, Semantic Pack, rates/QoL, merchant production code, project structure, зависимости, Studio/UI и старые task packages не менялись. До CREATE только7 новых строк seam приведены кLF для diff-check в существующем mixed-EOL файле; остальные EOL сохранены. Java semantics/line count не изменились.

## Проверки и review

`M` — source module, `S` — private external stage, `SM=S/HighFive`. Полные команды с разрешёнными фактическими paths и raw output хранятся private; пароли/UUID/private paths в Git не включены.

| Команда/проверка | Exit / результат |
|---|---|
| java -version / ant -version |0/0; JDK25.0.4.1/Ant1.10.17|
| OwnerConfig.ps1 -Action Verify на source до/после |0;68/287/conflicts0/changed0|
| Task102OwnerParity.ps1 |0; Apply1, Verify0, repeatApply0; invalid Apply expected1/changed0|
| Task102Preflight.java M; Task102Run Plan |0; ABSENT/fullVisibility/sessions0;121/203/18 omitted|
| wrong cwd / foreign101 flag / wrongSID / inputSHA drift |expected1 каждый, до JDBC; authority bytes восстановлены|
| Первый shadow Ant task102-build (compile-tests,jar,population-performance) |1: compile/JAR PASS, performance2/3|
| Focused performance RED→GREEN |old test ожидал2000 physical ACTIVE signals; source намеренно отправляет calendar ACTIVE как BACKGROUND. Исправлен test, admission2000 и locality-owned physical0 отдельно;3/3|
| Второй full shadow graph после census seam |0;31s, production/test compile/JAR, performance3/3;5 прежних JDK warnings|
| Focused javac Native после review и bootstrap correction |0; прежний JAR, без нового production build|
| Native config/contract checks |0;5000/128/128; invalid129/10001; inactive snapshot noDB; churn/epoch/checkpoint drift rejected|
| CREATE / initial Phase1 / one RetryPhase1 |0 /1 /1; причины выше, оба JVM graceful STOP|
| Final DB-free population-performance launcher |0;3/3, без DB|
| Final Own102 postflight |0; ownSessions0, World0, JVM0, listeners0|
| Source protected hash guard / original pinned Geo guard |169 files drift0 /203 files drift0|

Production-materialization-performance suite зависит от generic DB fixture и поэтому не запускалась как DB-free test. Full ant verify, защищённые DB suites, soak,5000 concurrent Player, later phases/restart, stale foreign DB runtime, foreign-session conflict injection, positive lifetime starter-grant campaign и client known-list QA — NOT_RUN.

Independent review: один pass + один targeted follow-up. Первые2 P2: sequential World union и отсутствие durable restart checkpoint. Harness теперь revalidates тот же Player reference/objectId/materialization epoch второй проходкой, сохраняет два manifests и требует неизменный cohort128 через30s. Stopped checkpoint хеширует все ordinary characters columns (EXP/SP), inventory и component payload/versions; phase5 сравнивает до production start. Follow-up также выявил false cap violation по историческому union; cap проверяется после stable cohort, contract turnover test PASS. Дополнительный reviewer pass не запускался. P1 не найден; найденные code P2 исправлены и focused checks PASS, это не независимый native SUCCESS.

Review limit сохранён: checkpoint до start сам по себе не доказывает отсутствие последующих duplicate grants. Текущий source starter lifetime0; harness phase5 отдельно требует0 ordinary starter ledgers до/после и creationClaims0. **Phase5 не выполнялась, фактическое R/no-replay остаётся NOT_PROVEN.** Positive lifetime policy fail-closed требует отдельного фактического accounting, не выдаётся за покрытую.

## Performance и сохранность

Native flags Xms512m/Xmx4g. До запуска host total133873280KiB/free94666184KiB.21 process samples за210.369s: CPU delta76.234375s ≈0.362385 среднего ядра, не процент всех CPU; включает обычных merchants и observers. Heap sampled peak4072030752 bytes, committed4294967296; threads peak126; final GC71/809ms. Это почти заполненный4GiB heap даже при500 и не доказательство headroom5000. OOM/forced kill не было.

В sampled manager snapshots max lastPulseOperations6, retryActions max2/final0, dueBoundaries final500. Это максимум наблюдённых значений, не полная pulse histogram. Ecology persistenceWrites976 относится только к этому счётчику; exclusive SQL query/write counts и p95 UNAVAILABLE/NOT_MEASURED. Устойчивость128, full retained/occupied-slot census и known-list eviction matrix не измерены. PERF102=PARTIAL.

Same JAR обоих native invocations SHA256 `f484a763c2912818b4bef8f01815e653566f4f84107b1551c367960a6b5e8c5c`. Runtime input manifests включают actual JAR, classes, config/data/Geo; raw SHA/source delta receipts сохранены private. После последнего STOP ordinary checkpoint: characters500/items5401/components3496; identity/receipt preimages сохранены, повторный boot не проводился.

Final source OwnerConfig Verify68/287/changed0; protected169 SHA drift0, original203 Geo drift0. Test-human leases освобождены и Player удалены штатно, собственные JVM/listeners/SQL sessions0. Общая MariaDB, чужие worktrees/процессы, MAIN/PLAY, клиент и owner release не запускались/не останавливались. OWN102 и private receipts сохранены stopped; не удалять и не переиспользовать их как fresh schema.

Mojibake-маркеры в изменённых файлах проверены: полный список,14 файлов,0 совпадений.

Escaped Cyrillic в изменённых файлах проверены отдельно: все6 regex,14 файлов,0 совпадений.

## Git и STOP

Git разрешён root bounded exception и явной TASK102 publication authority. Выполнены rev-parse --show-toplevel / HEAD / --abbrev-ref @{upstream}, symbolic-ref --short HEAD, status --short (также --untracked-files=no), worktree list --porcelain, merge-base --is-ancestor9245...HEAD, ls-remote origin refs/heads/feature/phantom-world, diff --numstat / --check / -- с точными тремя существующими source paths. Первое sandbox network обращение ls-remote не прошло, owner-context read-only проверка succeeded. Reviewer использовал только exact-path diff.

Публикация после проверки: git add -- exact14-path allowlist; git diff --cached --name-only / --stat / --check; commit subject `phantom(task-102): bound population scale proof and record native blocker`; ordinary non-force `git push origin HEAD:feature/phantom-world`; fresh rev-parse/ls-remote equality. Foreign staged files запрещены. Literal delivery commit SHA и фактический push result фиксируются в private PUBLICATION102.json и финальном сообщении: собственный hash нельзя включить в содержимое этого же commit. Этот текст не объявляет push выполненным заранее.

Никаких branch/rebase/merge/reset/restore/clean/stash/force/StageAll. После safe exact-file публикации — **STOP**.5000/128 не достигнуты; оставшийся product blocker не чинится в обход retry/gate и не маскируется изменением OwnerConfig. **TASK103 не начиналась.**
