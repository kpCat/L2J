# PHANTOM-CITY-JOURNEYS-097

27.10.2026. **PARTIAL / TEST_ONLY / G0_NATIVE_BLOCKED**.
Самостоятельное native прибытие в Гиран, STORE/restart и клиентская видимость **не доказаны**.
G0 остановлен до создания БД/Player: не установлен ordinary actor с существующим мотивом и полным допустимым маршрутом.

## Baseline и scope

Local HEAD, upstream `origin/feature/phantom-world` и live branch при первоначальной проверке: `4d9a9d4c7f817d6d693579b822b80dc215686e65`.
Ветка `feature/phantom-world`, origin `https://github.com/kpCat/L2J`. Owner semantic commits сохраняются; live повторно проверяется перед публикацией.
Exact allowlist: этот отчёт и `test/java/org/l2jmobius/tests/phantoms/PhantomCityJourneys097Test.java` (пути от High Five).
Production Java, XML/data, topology hashes, schema, source configs, OwnerConfig, Semantic Pack и Studio не изменяются.
Чужие untracked packages, `.phantom-local/` и protected runtime/evidence не включаются в commit.

## G0: источники, мотивы и маршруты

`giran.city.center`: CITY_CENTER, исходные XYZ `(82480,149087,-3350)`, source `data/mapregion/giran_castle_town.xml`.
Это catalog XYZ; фактический Geo Z/native arrival не измерены. `giran.gatekeeper.30080`: GATEKEEPER `(83396,147904,-3404)`.
`gludin.recovery.anchor`: RESPAWN `(-83520,150560,-3000)`, не второй CITY_CENTER. **SECOND_CITY_AUDIT_ONLY**.
Catalog содержит 54 legs. Пять входящих в `giran.gatekeeper.30080` сверяются с реальными NORMAL choice index, XYZ, fee и destination castle:

| Source anchor | Native NPC/index | Базовая Adena | Первый/последний WALK |
|---|---|---:|---|
| generated.route.live002.gludio.arrival.anchor | 30256/6 | 29000 | требуют настоящего Geo/owner proof |
| generated.route.live002.oren.arrival.anchor | 30177/1 | 9400 | требуют настоящего Geo/owner proof |
| generated.route.live002.aden.arrival.anchor | 30848/2 | 13000 | требуют настоящего Geo/owner proof |
| generated.route.live002.goddard.arrival.anchor | 31275/1 | 63000 | требуют настоящего Geo/owner proof |
| generated.route.schuttgart.40.arrival.anchor | 31964/5 | 87000 | требуют настоящего Geo/owner proof |

StartingAdena source config = 5000; main class до MaxFreeTeleportLevel=40 имеет штатную льготу fee=0.
Поэтому fresh actor **не объявлен NO_FUNDS** на основании сравнения с base price. После льготы/скидок проверяется настоящая цена и остаток после BUY reserve.
Market принимает существующий ACTIVE farm/acquisition root; SELL требует настоящий свободный MATERIAL stack >=4 и canonical disposition/recipe policy, BUY — acquisition deficit, MANUFACTURE — выученный рецепт/skill.
`consider` открывает допустимый локальный магазин в town; `requestRestJourney` вызывается при `!legalLocation`. Newborn на безопасном старте сам по себе не является мотивом ехать в Гиран.
Supply выбирает реальную доступную покупку с purchase/return budget; Learning выбирает ближайшие три trainer/WALK. Ни один не обещает Giran destination для newborn.

Детерминированный допустимый predicate: smallest durable ordinary profileId с текущим ACTIVE root/calendar, quiescent non-town Player, подлинным Market quote/остатком BUY reserve, unchanged identity/epoch, безопасным свободным CITY landing и полным WALK/одним stock GK/WALK plan. Обязательны action lease, Geo, no Party/Store/Combat/Siege и unobserved endpoints. **Такой actor не установлен; hard-coded profile/object ID нет.**

Source audit всех creation points 11 StartingClass XML при CustomStartingLocation=False и EnableFactionSystem=False, XY lower bounds до ближайшего из пяти incoming GK (целая часть):

| Class | Минимальный ingress XY |
|---|---:|
| 0 / 10 | 147588 / 147600 |
| 18 / 25 | 38708 / 38708 |
| 31 / 38 | 68908 / 68908 |
| 44 / 49 | 146770 / 146770 |
| 53 | 37304 |
| 123 / 124 | 140808 / 140808 |

Все >12000 (`PhantomNavigationPolicy.productionDefaults`); минимальный direct Giran XY тоже >113000.
Это **DB_FREE SOURCE AUDIT**, не измеренный census097 и не census чужого live мира. Height/Geo не могут сократить XY lower bound.
`L2jNavigationBackend.planTravel` строит direct WALK либо WALK/один GK/WALK. Dwarf → GK30540 → Schuttgart → Giran требует двух GK; текущий foreground planner его целиком не составляет.
Причина: **NO_COMPLETE_NATIVE_GIRAN_ROUTE_FROM_STOCK_BIRTHS**. Это не общий NO_ROUTE: развитый field actor возле Gludio/Oren может пройти существующий путь; его наличие и полезный native визит здесь не доказаны.
092B offscreen CITY не используется: FARMING-only destination guard сохранён; **CITY_OFFSCREEN_UNSUPPORTED**.

## Решение и writer audit

Production edits не обоснованы: в Market уже есть admission, paid terminal marker, checkpoint/resume и canonical arrival. Native multi-hop — дополнительная travel capability, не точечный найденный stage/capture дефект; её не добавляли ради положительного теста.
Market владеет retained child/plan. Native GK владеет действительным списанием; после неоднозначного результата terminal marker не разрешает повторную оплату. Materialization lease проверяет текущий Player/epoch.
`captureVisibleArrival` проверяет goal/owner, выполняет native store и durable transaction; PositionPublisher публикует committed position. Farm/Supply/Learning/Party/autosave/owned-store writers сохраняют штатные guards. Общая atomicity всех inventory writers по одному producer monitor не заявляется.
Тест проверяет только существующие pure contracts: полный endpoint, отсутствие route, wrong origin, BUY reserve, deadline, запрет RETURN_GK и принятие только ARRIVED receipt. Synthetic geometry не доказывает оплату, Geo, World или restart.

## Приёмка и проверки

| Gate | Результат |
|---|---|
| A | PARTIAL: source-backed CITY/stock matrix и fresh-origin blocker; eligible ordinary cohort не установлен |
| B | PARTIAL: существующий production intent прочитан; сквозной ordinary native need/claim не измерен |
| C | PARTIAL: DB_FREE route/budget/fee/receipt contracts; native busy/observer/retry matrix NOT_RUN |
| D | NOT_RUN_G0: ordinary profile/object/epoch/World/native arrival отсутствуют |
| E | NOT_RUN: нет positive, поэтому нет STORE/STOP/sameDB restart или повторного provisioning |
| F | Build/focused/review/scope PASS для TEST_ONLY; публикационный receipt отдельно, feature acceptance не объявлен |

Native attempts=0; restart=0; DB connections/DDL/DML=0; test schema097 не создавалась. Клиент, MAIN/PLAY, TEST094/096N и их evidence не изменялись.
Read-only owner-context census: существующий mysqld PID8956; Java/JVM в полученном census нет. Общий сервис не запускался/останавливался. Sandbox process census был отвергнут, read-only owner-context повтор состоялся.
JDK Temurin25.0.4.1, Ant1.10.17. Сборка выполняется в новом physical shadow под собственным task097; штатный owner-profile-validate сохранён, Apply mutator исключён по локальному read-only build аналогу096. Production guards не отключались, native authority не создавалась.
Проверки из корня фактического checkout; shadow paths получаются через Resolve-Path, без привязки к диску/пользователю:

| Команда | Exit / результат |
|---|---|
| `powershell -File .phantom-local/task097/Prepare-Build.ps1` | 0; physical copy 28797 inputs |
| `ant -f .phantom-local/task097/readonly-build.xml compile-tests` | 0; 26s; source/tests compile, 5 прежних warnings |
| `java -Dphantom.module.root=<shadow-module> -cp <shadow-build/test/libs> org.l2jmobius.tests.phantoms.PhantomCityJourneys097Test` | 0; три DB_FREE группы, 54 legs/5 Giran legs/11 classes/possibleFirstMiles=0 |
| faction override negative до исправления audit-теста | Java exit0 ошибочно; контроль exit1 RED |
| `javac -encoding UTF-8 -source 25 -target 25 -cp <shadow-bin/libs> -d <shadow-test-bin> <owned-test>` | 0; единственная focused перекомпиляция после review fix |
| `powershell -File .phantom-local/task097/Negative-Control.ps1 -Case faction/custom/price` (три запуска) | 0 каждый; inner Java exit1 с соответствующей typed причиной; shadow bytes/SHA восстановлены |
| `java ... org.l2jmobius.tests.phantoms.PhantomTestLauncher living-presence096 960097` | 0; 4/4 admission/retention/calendar |
| `ant -f .phantom-local/task097/readonly-build.xml jar` | 0; 19s; JAR только в собственном shadow |
| `pwsh -File .phantom-local/task097/Finalize-Checks.ps1` | 0; два финальных byte-identical run, protected manifest и encoding PASS |

`ant verify` NOT_RUN: включает DB-backed historical gates; DB-backed Market/Supply/094 native suites также NOT_RUN после G0. Это не полный release verify.
GameServer.jar SHA-256: `56557ef4a38498d168025006b82748cc887f5e5d35708c582db364aaff340119`.
Final focused logs SHA-256 (оба): `c6b37183f261a6272bfeadb13dafa1b198516ebe92d47e8a1d41f957717aa8de`.
Final test source SHA-256: `db8f704dd14a690d6c20ca474e480e57c6426f547d66e8dd0c3925821bdfbf49`.
28796 первоначальных source inputs побайтово подтверждены SHA без изменений; единственное исключение inventory — новый owned test, исправленный после review и совпадающий с финальной shadow копией. Этот новый отчёт проверяется отдельно. Source JAR/config/data/Java не менялись; чужие authoring файлы не редактировались.
Полные логи: `.phantom-local/logs/PHANTOM-CITY-JOURNEYS-097/`; private plan/manifests/shadow/negative orchestration: `.phantom-local/task097/`. В commit они не входят.

## Read-first, review и расходы

Initial code/data: 12 путей READ_SET. Прочитаны root/module AGENTS, README, TASK097 и шесть связанных документов/GOAL, bounded CURRENT_STATUS/master/workflow/efficiency/package standard, таблица gates/native outcome096N.
Дополнительные bounded reads: build.xml; Market/NormalGatekeeper/Travel089/091/LivingPresence096 tests и launcher; 096N safety runner и Prepare script; Navigation policy/strategy/route contract/service/backend; StorePlan; PlayerCreationInitializer; population-v3; Player.ini/StartingLocation.ini/initialEquipment.xml; stock30256; StartingClass templates (только class/creation facts); OwnerConfig build entrypoints для исключения source writes.
Лимит additional <=5 превышен ради actual first-mile и безопасного build/runner contract; изменения не расширены на эти файлы. Большие source файлы целиком не читались. Отдельные code-map/pattern файлы не искались: закрытый READ_SET задаёт нужную карту. Над корнем проекта AGENTS не найден.
До первого patch выполнено 7 rg invocations (file inventory и точные source searches): лимит6 превышен на1; повторного broad поиска после обнаружения task не требовалось. Это отклонение процесса.
Independent G0 review подтвердил 11-class source matrix, single-GK bound и отсутствие обоснованного production исправления. Свежий independent final review обнаружил один P2 в новом test-only аудите: не учтён Faction starting-location override. RED воспроизведён; оба override теперь fail-closed, постоянные negative controls добавлены. Повторное review: Critical0/Important(P1/P2)0/Minor0; разрешена безопасная PARTIAL-публикация, без D/E acceptance.
Cadence: full compile-tests1; focused javac1 после точного review дефекта; standalone jar1; full ant verify0; новые focused positive runs3 (первый + два финальных одинаковых), negative4 (один RED + три GREEN controls), existing096 suite1. Native/restart0.
Один private verification script имел PowerShell parser error на smart-quote mojibake-маркере; literal quoting исправлено, проверка затем прошла. Это tooling error до выполнения checks, не product/native failure.
Goal usage snapshot перед публикацией: 424521 tokens /1082s (18м02с); не финальная стоимость. Ориентир400k превышен: избыточные ранние outputs и read-set expansion плюс независимые source/final reviews; это отклонение efficiency, не основание расширять scope.
Mojibake-маркеры в изменённых файлах проверены: PASS, весь заданный набор, 0 совпадений.
Escaped Cyrillic в изменённых файлах проверены: PASS, все шесть regex, 0 совпадений.
Оба новых файла UTF-8/LF, final newline, bare CR отсутствуют. Synthetic GREEN не объявляется native успехом.

## Git и публикация

Git разрешён прямым запросом пользователя и TASK/SAFETY/DELIVERY. Initial commands: `git rev-parse --show-toplevel HEAD`, `git symbolic-ref --quiet --short HEAD`, `git status --short --branch`, `git worktree list --porcelain`, `git rev-parse origin/feature/phantom-world`, `git config --get remote.origin.url`, `git ls-remote --heads origin feature/phantom-world` (sandbox network failure, затем успешный owner-context read-only повтор).
Review использовал только bounded `symbolic-ref`, `rev-parse` и exact-path `status`; записи не выполнял.
Перед commit выполнен разрешённый `git fetch origin feature/phantom-world`; local/upstream/live снова равны исходному SHA. Final scope: `git -c core.whitespace=cr-at-eol diff --check -- <два exact paths>`, `git status --short --branch`, затем `git add -- <два exact paths>`, `git diff --cached --name-only/--numstat/--check -- <два exact paths>` и полный staged diff только этих файлов.
Публикация разрешена как `git commit -m "phantom(task-097): audit lawful city route blockers and contracts" -- <два exact paths>`, затем свежий live comparison и `git push origin HEAD:feature/phantom-world`. Commit SHA и actual push receipt сохраняются в private `publication.json` и финальном сообщении: самоссылочный SHA в содержащий его commit не вносится. При изменении remote — fail closed, без force.
Force/history rewrite/clean/stash/reset/branch/merge/rebase не выполняются. Git использован только для описанного baseline/scope/review и явно разрешённой публикации.
После безопасной публикации STOP; merchant scale/multi-city/new travel capability автоматически не начинать.
