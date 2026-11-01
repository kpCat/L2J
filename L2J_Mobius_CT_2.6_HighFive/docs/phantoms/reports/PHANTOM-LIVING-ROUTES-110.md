# PHANTOM-LIVING-ROUTES-110

01.11.2026. **PARTIAL / FOCUSED_VERIFIED / NATIVE_NOT_RUN / OWNER_VISUAL_PENDING**.
Полный причинный жизненный цикл в World и SUCCESS110 **не доказаны**. Поставляется совместимый проверенный срез существующих Navigation/Market owners.

## Результат A–H

| Gate | Фактический статус и предел |
|---|---|
| G0 | Branch/local/origin/live baseline и WIP isolation проверены. Native G0 **REQUIRED / NOT_PASSED**: нет выбранного ordinary ACTIVE actor/root/мотивированного quote/кошелька и полной Geo/NPC дороги; SQL/authority/ACL/TCP clearance не запускался. |
| A — Motive | Существующие реальные farm/acquisition → BUY/SELL/MFG → retained RestJourney сохранены. Root/revision/child codec — FOCUSED_VERIFIED; фактический ordinary actor и earned motive — UNKNOWN. |
| B — Planner | Fast path теперь резервирует полный canonical maximumFee, как composite098; BUY reserve и текущий уровень перед каждым GK защищены. Два stock источника Gludio/Oren, composite098 и Navigation51/51 — FOCUSED_VERIFIED. Native Geo/WALK — NOT_PROVEN. |
| C — Travel | Native Player/Npc executor и платёжные writers не заменены. Фактических поездок, debit и paid arrival в110 — 0; NATIVE_NOT_RUN. |
| D — Purpose/continuity | Временные combat/cast/Party/чужое движение приостанавливают ephemeral route; root/finite child сохраняются, после освобождения — новый план от XYZ. Focused state/codec/cleanup проверены. Native rewarded activity/quote refresh/возврат к фарму в110 — NOT_PROVEN. |
| E — Restart | Codec/finite deadline/stale origin/payment guard — FOCUSED_VERIFIED. SameDB/new JVM, canonical SQL/STORE/дальнейшая полезная работа — NOT_RUN. |
| F — Presence/observer | READY/ACTIVE/WARM/materialized/World/known-list ordinary census — UNKNOWN, не нули. Observer102 аудит — OBSERVER_LIMIT / OBSERVER_MOVEMENT_NOT_PROVEN. OWNER_VISUAL_PENDING. |
| G — Negatives | Full fee/BUY/level40→41, stale origin, busy state, foreign token, retry15s×3, expiry/noGeo/NO_PATH vs search limit — focused PASS. Реальные PvP/Party/lease/STORE races и scale — NOT_RUN. |
| H — Delivery | Независимый archive candidate от verified HEAD; 2 production +2 tests +этот отчёт. Isolated build и affected regression PASS; один review, два P2 RED→GREEN. Точный source SHA и публикация — ниже. |

## Архитектура и scope

Decision/GoalStateStore владеет главным ACTIVE root/revision. Market остаётся единственным владельцем finite retained RestJourney и его ephemeral RestRoute.
VisibleAutoPlay по прежнему yieldAfterCurrentTarget завершает собственную текущую работу; глобальный quiet для PvP не добавлен.
NavigationService сохраняет shared queue/cancellation/deadline/cooldown; существующие backend/composite доказывают полный физический маршрут.
Native Npc.onBypassFeedback остаётся владельцем реального NORMAL debit; actual fee/stock discounts/free-level правила не изменены.
До платежа остаётся прежний StoreService terminal FAILED marker; затем canonical Background.captureVisibleArrival/STORE/CAS/PositionPublisher и fresh plan от сохранённых XYZ. Неоднозначный результат не получает replay.
Новый inventory/SQL/position writer, engine, timer/thread на Phantom, схема, конфиг, фиксированные цены и телепорты не добавлены. Общая atomicity всех native inventory writers не заявлена.

Exact allowlist, относительно High Five:

1. `java/org/l2jmobius/gameserver/phantoms/navigation/L2jNavigationBackend.java` — canonical full-tariff reserve на complete fast-path candidates, включая single GK.
2. `java/org/l2jmobius/gameserver/phantoms/economy/PhantomAutonomousMarketProducer.java` — временное прерывание с сохранением child/root, fresh replan, bounded retry, пересчёт всех оставшихся fees по текущему уровню, cleanup только своего движения; выбор CITY_CENTER по расстоянию/id вместо Giran-id priority.
3. `test/java/org/l2jmobius/tests/phantoms/PhantomLivingRoutes110Test.java` — package navigation, реальные stock identities и тарифы; geometry STRUCTURAL_FIXTURE.
4. `test/java/org/l2jmobius/tests/phantoms/PhantomLivingRoutes110ContinuationTest.java` — package economy, внутренний протокол реального producer/codec, level-change и ownership-cleanup regression; native Player/World не создаёт.
5. `docs/phantoms/reports/PHANTOM-LIVING-ROUTES-110.md` — этот отчёт.

Все остальные 2378 production sources и 450 существующих tests равны archive baseline побайтно. Всего guard проверяет2939 archive inputs: Java/tests, build.xml, config, topology/travel. Новых production API вне package или библиотек нет.
PhantomBackgroundService, NativeTravelActions, StoreService, VisibleFarmTravel, Party/Farming/PvP/Social, topology data, caps5000/128 и Studio не менялись. Непубликованные WIP109 PK/C2/social hunks в candidate отсутствуют.

## Фактические игровые входы и native blocker

Source и archived candidate имеют `PathFinding=2`, `GeoDataPath=./data/geodata/`, но в этом каталоге только `Readme.txt`852байта, **0 Geo tiles**. Старые внешние Geo/TEST/PLAY поставки не подключались.
Stock catalog содержит54 legs и5 incoming Giran. Для focused reserve использованы30256/NORMAL6 Gludio→Giran, maximum29000 Adena, и30177/NORMAL1 Oren→Giran, maximum9400 Adena.
Это source-backed prices, не actual debit. Реальные XYZ/Geo Z/NPC availability/canInteract на полной дороге **не измерены**. Existing097 source audit births11/possibleFirstMiles0 относится к single-GK bound и не доказывает невозможность composite098.
В topology только один настоящий CITY_CENTER — `giran.city.center` (82480,149087,-3350); `gludin.recovery.anchor` имеет роль RESPAWN. Вторую CITY не добавляли и не объявляли проверенной.
Конкретный ordinary profileId/objectId/root/inventory/wallet не выбран; census других БД не выполнялся. Это UNKNOWN, не NO_ELIGIBLE_RESIDENT и не искусственно населённый Giran.

Observer audit: `PhantomPopulation102Native.addObservers/spawnObserver` загружает labelled ordinary human, получает LOCALPLAY_TEST_HUMAN lease, выполняет **pre-cut** setXYZInvisible/spawnMe; далее currentObservers проверяет identity/World/online/instance.
Маршрутного controller/API для законного движения этого observer в найденном call path нет. `PhantomWorldCohort108R3Checks.capture` даёт read-only World/visibility receipts; `PhantomLivingPresence096Native` использует OBSERVER_PROXY, не человеческое путешествие.
Observer не создавался/не перемещался; перемещения наблюдателя не выдаются за Phantom navigation. Клиент владельца не запускался и сейчас не требуется.

Полный motive→route→paid GK→city activity→STORE→restart отсутствует, поэтому новая TEST110 БД и GameServer **не запускались**. Metadata/SQL/G0 нельзя обойти ради положительного сценария. Canonical paid persistence не пересматривался без этого proof.

## Проверки, review и затраты

Private воспроизводимые scripts/логи: `%TEMP%/phantom-living-routes-110-cb0ee1b9/` — `SCOPE_AND_OWNER_PLAN.md`, `Run-Focused.ps1`, `ReviewFix110.ps1`, `readonly-build.xml`, `Build110.ps1`, `Regression110.ps1`, `Guard110.py`, `focused-bin/`, `regression/`, `ant-final.log`, `ant-before-review.log`, `delivery-files.json`, `guard.json`, publication receipts.
Raw proof: [Ant](C:/Users/ZBook/AppData/Local/Temp/phantom-living-routes-110-cb0ee1b9/ant-final.log), [affected results](C:/Users/ZBook/AppData/Local/Temp/phantom-living-routes-110-cb0ee1b9/regression/results.json), [fee RED](C:/Users/ZBook/AppData/Local/Temp/phantom-living-routes-110-cb0ee1b9/focused-bin/red-org.l2jmobius.gameserver.phantoms.navigation.PhantomLivingRoutes110Test.log), [final guard](C:/Users/ZBook/AppData/Local/Temp/phantom-living-routes-110-cb0ee1b9/guard-final-b.json), [publication](C:/Users/ZBook/AppData/Local/Temp/phantom-living-routes-110-cb0ee1b9/publication.json).
Code-only manifest SHA256: `18a373d4ac01b235519e205661a8360cec3e287c9996dcfb06c684619005521a` ([manifest](C:/Users/ZBook/AppData/Local/Temp/phantom-living-routes-110-cb0ee1b9/code-manifest.json)); final delivery manifest включает отдельно hash отчёта.
Публичный Git содержит тесты/код/отчёт, private evidence/классы/JAR/credentials не публикуются. Source SHA и точный JAR связывают эту проверку; это не PLAY-ready release.

Команды выполнялись в physical candidate; classpath — fresh test/resources/production classes + archived dist/libs, без старых GameServer/LoginServer в test classpath.

| Проверка | Exit / результат |
|---|---|
| `javac -encoding UTF-8 -source 25 -target 25 ...` + `PhantomLivingRoutes110Test` | Поведенческий RED1: discounted fast path30256 принимал неполный reserve; после исправления GREEN0 для30256/30177, BUY, free-level/subclass. |
| `PhantomLivingRoutes110ContinuationTest` | GREEN0: finite child/root/revision, factual replan, retry15s/3attempts, statuses, paid checkpoint priority. Первоначально API отсутствовал: compile RED, не behavioral proof. |
| `ReviewFix110.ps1 -Phase red/green` | Два отдельных RED1→GREEN0: level40→41 full remaining200 при150 свободных; foreign move не блокирует cleanup и не отменяется. |
| `ant -f <private>/readonly-build.xml compile-tests jar` | Два запуска exit0,36.70s и36.35s:2380production/452tests,5 прежних warnings. Второй только после двух конкретных review P2; тот же candidate. OwnerProfileVerifier38rules PASS/READ_ONLY, mutating Apply исключён private wrapper по локальному098 паттерну. |
| Финальный affected regression | Все7 runner exit0:110 reserve,110 continuation,098,097,NormalGatekeeperPure,Travel091,NavigationCore51/51 seed11011001. Около1.6s суммарного runner wall-time; нового server процесса нет. |
| Scope/text/preservation | Task manifest8SHA; archive2939inputs; WIP14preimages; source10pins; exact staged scope/whitespace и два отдельных encoding checks. Raw manifests в private workspace. |

Повторная affected проверка оправдана изменением production после review. На первом прогоне NormalGatekeeperPure получил NoSuchFileException из module cwd; повторён только этот failed runner и два ещё не запущенных из `dist/game`, exit0. Это ошибка оркестрации, не дефект продукта.
Ранние tooling failures: javac sandbox AccessDenied на внешнем JAR; elevated DB-free запуск; implicit compilation не находила package-private unit, исправлено explicit source list; stock selector ожидал отсутствующий в54-leg catalog Orc30576, заменён на подтверждённые Oren/Gludio. Native доказательства из них не выводились.
Всего standalone javac invocations8 (3 compile failures,5 successful), focused pre-final runner JVM8; affected runner executions15 (включая один cwd failure), всего23 test runner JVM. `ant verify`0, standalone `ant jar`0, Ant compile-tests+jar2; каждый Ant повторяет compile внутри своего invocation.
Собрано2 версии GameServer.jar; вместе с LoginServer/DatabaseInstaller —6 JAR outputs. Финальный GameServer SHA256: `16b809dc8b8d3fe8ef8546b05b70d00f07e5a40696e40d6c8e5487fe9b1a9d26`. Предыдущий, не публикуемый JAR: `b2b6df93f56c89525a195908fdc7764d3622c014655a19e3286948ef8bbbba91`.
Новых БД0, JDBC connections/SQL statements/DDL/DML0, GameServer JVM0, native episodes0, restart0, grants/forced goals/Phantom teleports0. Никакого global SQL/JVM clearance PASS не заявлено; свои native leases/listeners не создавались.
SHA-аудит ограничен task8, initial WIP14×2 hashes, archive2939 сравнений/pass, WIP14×2/pass, source10/pass и delivery manifest; точное число SHA256 evaluations каждого final guard записано в guard.json. Исторические150k inputs не сканировались.
Goal usage на pre-publication snapshot:367224tokens/1781s (0,495часа); финальные время/usage и Git receipts фиксируются после публикации в private workspace. Read-set расширен ради подтверждённых multi-owner/build/observer зависимостей; полного чтения классов>1000 строк и других хроник не было.

Независимый read-only reviewer `/root/task110_review`: Critical/P1=0, P2=2. Оба приняты и исправлены одним RED→GREEN pass, затем fresh Ant+полный affected set. Повторного review не было.
Deferred minor: тест внутреннего RestRoute не доказывает внешний native busy/TTL/lease call path. Новая pure ownership-callback проверка защищает решение о cleanup; интеграционная часть остаётся NOT_RUN, не выдана за World PASS.

- mojibake-маркеры в изменённых файлах проверены: полный27-marker набор, отдельная проверка;
- escaped Cyrillic в изменённых файлах проверены: все6regex, отдельная проверка.

## Сохранность, Git и STOP

Baseline/local/origin/live перед работой: `cb0ee1b9fcff769891e5cf6c533d24b770efe084`, branch `feature/phantom-world`; GitHub ref проверен через connected GET, исходный shell ls-remote был недоступен из sandbox.
Физический candidate получен `git archive <baseline>` во внешний TEMP; worktree/clone/reset/clean/stash/rebase/force не использовались. Все14 tracked WIP109 сохранены побайтно с SHA вне checkout и проверены после работы; untracked пакеты/отчёты не удалялись и не включались.
Все ранее существовавшие main worktree файлы сохраняют свои байты, включая два ранее чистых source delivery paths. После обновления exact main index они показываются как дополнительные unstaged отличия от нового HEAD: **основной mixed checkout не является проверенной сборкой**. Проверенный source — committed tree/внешний candidate; WIP109 по прежнему не опубликован.
Два новых test paths и отчёт материализуются в main только на ранее отсутствующих путях; существующие source/build/dist/config не заменяются. Source pins и14WIP preimages остаются проверяемыми.
Для точного source SHA предусмотрены два bounded commits: основной code/tests, затем только отчёт с SHA первого. Это исключает невозможную самоссылку commit SHA внутри самого отчёта; второй commit не меняет собранный код.
**Проверенный source commit:** `e1878c15308f695b75873e3f7c42b2048ad80fff`, parent `cb0ee1b9fcff769891e5cf6c533d24b770efe084`.
**Публикация:** обычный non-force push двух scoped commits в `feature/phantom-world`; окончательный ref/remote parity — publication receipt и финальное сообщение. До receipt успешная публикация не предполагается.

Git-команды разрешены TASK110/текущим запросом и использованы только для baseline/isolation/exact delivery:
`rev-parse --show-toplevel`; `symbolic-ref --short HEAD`; `rev-parse HEAD refs/remotes/origin/feature/phantom-world`; `rev-parse --abbrev-ref --symbolic-full-name @{upstream}`; `rev-parse --git-dir --git-common-dir`; `status --porcelain=v1`; `remote get-url origin`; `ls-remote [--exit-code] origin refs/heads/feature/phantom-world`; `diff --name-only -- <module>/java <module>/test` для preimages; один авторский `diff --no-index -- <exact source> <exact candidate>` и два таких read-only review; `archive --format=tar --output=<private>/baseline.tar <baseline> AGENTS.md README.md <module>`.
Delivery: внешний `GIT_INDEX_FILE`, `read-tree <baseline>`, `--work-tree=<candidate> add -- <exact4>`, `diff --cached --name-only`, `diff --cached --numstat/check/raw/patch -- <exact paths>`, `commit --file <private message>`, `rev-parse HEAD HEAD^`, `ls-tree HEAD -- <exact paths>`, exact `update-index --add --cacheinfo <mode,blob,path>` для синхронизации main index, затем только отчёт и обычный `push origin HEAD:refs/heads/feature/phantom-world`. Полные exact argv/paths/exits — private publication receipts.

**STOP после TASK110.** TASK111/110b/новые native среды не запускать. Native цикл, paid STORE/restart, genuine второй CITY, ordinary population и OWNER_VISUAL_PENDING остаются открытыми; текущий PARTIAL их не закрывает.
