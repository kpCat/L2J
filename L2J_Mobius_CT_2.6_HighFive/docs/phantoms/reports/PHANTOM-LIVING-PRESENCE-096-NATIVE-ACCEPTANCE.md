# PHANTOM-LIVING-PRESENCE-096-NATIVE-ACCEPTANCE

27.10.2026. **PARTIAL / FIXTURE_WORLD_PASS / NO_ELIGIBLE_RESIDENT / LIFECYCLE_GATE_OPEN / OWNER_CLIENT_QA_DEFERRED**.
Baseline: `3352706b8799f92e3e243ab2890e179b8a35466f`, `feature/phantom-world`, origin `kpCat/L2J`.
TASK096 остаётся PARTIAL; клиентская QA — DEFERRED. Новая playable-поставка не создавалась.

## Scope и read-first

Прочитаны root AGENTS/README, High Five Agents, bounded CURRENT_STATUS/WORKFLOW/EFFICIENCY/master plan, TASK_PACKAGE_STANDARD, пакет 096N, отчёты 096 и релевантный 065.
Initial READ_SET: 12 заданных seams, большие классы только точечными диапазонами. Аналоги: strict cwd 065, physical/ACL/JDBC/schema guards 094, native cap-pressure 065, DB-free admission 096.
Additional reads: build.xml, TestContext, TestLauncher, Task094Native, SchemaManifest, StrictSqlScriptRunner; затем PopulationStore/TestDoubles, bounded BackgroundSuite/native scope, OwnerConfig и три stock SQL/TSV inputs для подтверждения creation API, SQL keys и missing-fixture причины.
Лимит additional exact files превышен: расширение ограничено перечисленными зависимостями безопасного bootstrap и native fixtures; полный файл >1000 строк не читался. Другие хроники не открывались. До первого patch — 4 repository search calls; далее только bounded searches выбранных seams.
Вложенный High Five `Agents.md` найден; отдельные code-map/pattern-файлы не искались повторно: точным code-map служит SOURCE_MAP096N.
JDK Temurin25.0.4.1, Ant1.10.17. Существующие стек, календарь v1/v2/v3, schema scripts и product policy сохранены.

Exact allowlist, пути относительно High Five:

1. `test/java/org/l2jmobius/tests/phantoms/PhantomTask096NativeLane.java` — отдельная test-only authority, seals, create-only bootstrap и postflight.
2. `test/java/org/l2jmobius/tests/phantoms/PhantomLivingPresence096Native.java` — единичная сцена с census и явно помеченными fixtures.
3. `test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java` — ранняя проверка 096N, собственная DB initialization, запрет generic mutable fixtures; default cwd guard сохранён.
4. `tools/phantom-runtime/Task096Native.ps1` — fresh external physical copy, source-owner SID, owner+SYSTEM ACL и input SHA inventory.
5. Этот отчёт.

Production `java/**`, build.xml, authoring/Semantic Pack/Studio, source config и stock data не изменялись. Пользовательские untracked packages/WIP не включаются в staging.
Preimage существующего Environment сохранён физической исходной копией вне checkout с SHA inventory до его изменения; новые файлы принадлежали этой задаче. Финальные exact SHA связаны с source/shadow и compiled manifest.

## G0, DB и lifecycle authority

Первый read-only probe: schema096N отсутствовала, foreign sessions0, fingerprint18560 metadata tables. Прямой global ALL grant текущего principal подтвердил полноту census и CREATE authority; исходный grant text/credentials не выводились.
Source JDBC проверен как mysql/127.0.0.1:3308/l2jmobiush5 без userinfo; owner options не используются для соединения. Все test connections используют фиксированный loopback3308 URL, bootstrap — только собственную schema096N.
Одна preliminary URL regex ошибочно отвергла допустимые owner options; corrected URI-origin probe PASS. Оба receipts сохранены; config не изменялся.
Первый stage был создан под CodexSandboxOffline: default JVM verifier получил AccessDenied, owner JVM не могла читать этот ACL. Он оставлен без Database.ini/DB/World для forensics; права не расширялись.
Prepare теперь отвергает execution SID, отличный от source owner. Рабочий fresh stage создан под фактическим owner, ACL только owner+SYSTEM. До bootstrap проверены physical ancestry, UUID/expiry, source/config/Geo/SQL manifests, compiled classes/resources, exact classpath/codeSource и caller owner SHA.
Финальные source inputs28999, Geo203; outputs и authority находятся в private external stage. Секреты и конкретные private paths не публикуются.
Пять actual negative probes: source cwd, foreign cwd, wrong UUID, foreign094 lane, own junction — exit1 с typed guards до Config/DB. Wrong schema и expired authority покрыты pure checks; пустой foreign manifest также запрещён кодом.
Bootstrap exit0: scripts123, отсутствующие DROP18 пропущены после absence proof; исполненных DROP/DELETE/TRUNCATE0. REPLACE11 audited:3689 уникальных guard primary keys и2 уникальных global-variable keys. Все statements проверяются до DDL; foreign metadata hash сохранился.
Единственная schema: `l2jmobiush5_phantom_test_096n`; task96/installation UUID/migration SHA и canonical metadata сохраняют ownership. Повторный bootstrap/adopt/reset/drop запрещён. Stage и DB сохраняются intact.
Writers сцены: штатная PopulationStore creation saga, repository/components, real materialization/native checkpoint/STORE, существующие ecology/background lifecycle и shared scheduler. Нет нового production writer/controller, per-profile timers, PvE runner или ручного World.remove.
Существующие lease/epoch/native-action monitors остаются границей materialization; test adapter не доказывает production ownership composition. Ошибки cleanup собираются с suppressed causes; failed drain не превращается в PASS.

## Проверки и промежуточные ошибки

Raw evidence: `.phantom-local/logs/PHANTOM-LIVING-PRESENCE-096-NATIVE-ACCEPTANCE/`; sealed inputs/compiled/private receipts — внешний own stage.
Основные команды: `Task096Native.ps1 -Action Prepare -SourceRoot <actual module> -StageRoot <fresh external> -InstallationId <own UUID>`; `ant -f <shadow>/build.xml -Dbuild=<stage>/build compile-tests`.
Первый sandbox build отказал в OwnerConfig; owner-context whole-source compile:2372 production+418 test, exit0,29s,5 прежних warnings. Staged OwnerConfig Apply:68files/287overrides/0conflicts/0writes; OwnerProfile38 PASS. Source Apply не запускался.
После review выполнялся только targeted javac двух новых test classes в собственный build; общего повторного Ant verify/jar не было.
Focused launcher: `java -Dphantom.module.root=<shadow> -Dphantom.test.reports=<own reports> -cp <own test-bin;resources;production-bin;shadow libs/*> org.l2jmobius.tests.phantoms.PhantomTestLauncher <mode> <seed>`.

| Suite / seed | Exit | Факты |
|---|---:|---|
| living-presence096 /96009601 | 0 | 4/4 |
| activity-scheduler /20260725001 | 1→0 | 35/36: missing stock LIVE003_CORE_SCALE_10000.tsv; точное копирование входного TSV, без изменения test/product, дало36/36 |
| population-schedule /16001601 | 0 | 3/3 |
| living-presence-calendar065 /65006500 | 0 | 3/3 |
| 096N authority/SQL guards | 1→0 | invalid schema/UUID/expiry RED→GREEN; destructive ALTER отдельно RED→GREEN |

Scheduler исполнен3 раза: первоначальный missing-input FAIL, targeted retry PASS и лишний повтор внутри финального sync helper PASS. Другие focused suites — по1. `population-ecology-goal033` NOT_RUN: его исторический owner-ON/shipped-OFF conflict не связан с этими test-only изменениями.
Full Ant compile1 PASS плюс один отказ до compile; full ant verify0; standalone jar0; native campaigns1; restart0. Защищённые release/TEST094/MAIN/PLAY, клиент и shared MariaDB service/datadir/users/grants не изменялись.
Independent read-only reviewer нашёл late seal validation, отсутствие executable manifest, blank foreign manifest и late latency timer; исправления проверены до native. Reviewer подтвердил current seals/ACL/expiry и допустимость bootstrap+одной попытки; это не объявление успешной приёмки.
Giran latency в текущем harness — время фазы переключения, end-to-end human-demand→World NOT_MEASURED. Observer=OBSERVER_PROXY, known-list NOT_RUN, population ownership=TEST_ADAPTER, ecology current-cursor clock фиксирован; real recent-human timer не ускоряется. Fairness покрывает только protected-DEFERRED case.
Repeated full input/reparse scans оказались дорогими: однократный jcmd snapshot собственной JVM показал main в `inputs/physical`, не gameplay/deadlock. Это overhead test harness; guards на ходу не менялись, повторной native кампании нет.

## Native outcome и postflight

| G0 | INVENTORY | GIRAN_WORLD | GLUDIN_WORLD | CAP_PRESSURE | STORE | OWNER_CLIENT_QA | overall |
|---|---|---|---|---|---|---|---|
| measured isolation/bootstrap/postflight PASS | READY24, eligible city0/0 | FIXTURE_PASS | FIXTURE_PASS | bounded exchange/holds PASS; full fairness OPEN | SQL PASS; native STORE/scheduler gate OPEN | DEFERRED | PARTIAL |

Ordinary census выполнен до fixture relocation:24 сохранённых READY через каноническую PopulationStore creation saga, Giran0/Gludin0 для ACTIVE calendar; topology position revision ещё UNPUBLISHED. Это **NO_ELIGIBLE_RESIDENT для измеренной fresh population**, не перепись чужого live мира. Начальные сохранённые позиции/region/identity/component revision находятся в ordinary-census.tsv; региональные назначения/календарь не переписывались.
После census подготовлены только два cold native test actors штатным LOAD/STORE до measurement; source=FIXTURE_PREPARED. Никаких XYZ после measurement.begun, EXP/items/earned state fixture-правок или автоматического переселения обычных жителей.
Gludin: profile2/object268480998/epoch478002381972800, (-83520,150560,-3120,instance0), matching World Player/native epoch/current owner; latency1157ms после старта demand. Giran: profile3/object268481007/epoch478064169369200, (82480,149087,-3464,instance0); phase latency60812ms, полная end-to-end latency не измерена.
Cap1: одновременно protected two-town demand, затем уход observer; wrong epoch DEFERRED, реальная recent-human60s и admitted action удержали старого actor. После завершения grace/action новый actor появился, старый World owner исчез, occupied1. Measurement receipt сообщает FIXTURE_WORLD_PASS; путь soft-reclaim отдельно от обычной demotion не трассировался, поэтому полная причинная cap/fairness приёмка остаётся OPEN. Private Store/Trade/Party/native STORE race matrix в новой сцене NOT_RUN.
Native attempt: exit1, PID66772, один запуск, примерно333s включая полные seal scans/bootstrap infrastructure и bounded scene. Исключение `TASK096N_SCHEDULER_DRAIN`; перед ним service.shutdown STOPPED не дал отказа, однако combined store.completed не создан. Отдельного успешного World0/drain receipt нет; скрытые suppressed cleanup failures не исключаются этим логом. JDBC pool закрылся штатно; own JVM0 подтверждён process census. Forced kill не применялся.
Read-only postflight exit0: own characters online0, foreign sessions0, exact DB owner/metadata PASS, foreign metadata hash неизменён, source input hashes совпали. Собственная схема и sealed stage оставлены intact. SQL postflight не доказывает quiescence уже завершившегося scheduler и не является побайтовой проверкой чужих payload.
Root cause harness: await повторно вызывал condition после положительного результата; Scheduler.finishStop возвращает false при уже STOPPED. Focused callback, успешный только на первом вызове, воспроизвёл RED exit1; helper сохраняет результат последнего вызова, GREEN exit0/callbacks1. Это единственное post-run causal исправление тестовой среды; production Scheduler не менялся.
Исправленный test class отдельно скомпилирован в evidence postfix-bin; DB/World не открывались. Native receipts привязаны к прежней sealed версии, stage не перепечатывался и native не повторялся. Нельзя ретроспективно объявить scheduler gate PASS по pure regression; итог остаётся PARTIAL. Native defect продукта не заявляется.

## Сохранность, Git и расходы

Mojibake-маркеры в изменённых файлах проверены: PASS, 0 совпадений полного набора маркеров.
Escaped Cyrillic / XML escaped Cyrillic в изменённых файлах проверены: PASS, 0 совпадений всех шести шаблонов.
Git разрешён TASK/DELIVERY и root scope contract. Выполнялись: `rev-parse --show-toplevel`, `rev-parse HEAD`, `rev-parse origin/feature/phantom-world`, `symbolic-ref --short HEAD`, `status --short [--untracked-files=no]`, `merge-base --is-ancestor 3352706b8799f92e3e243ab2890e179b8a35466f HEAD`, `for-each-ref --format=... <две exact refs>`, `config --get remote.origin.url`, `ls-remote --heads origin feature/phantom-world`, `diff --numstat -- <exact Environment>`, `-c core.whitespace=cr-at-eol diff --check -- <exact allowlist>`.
Final independent read-only review разрешил безопасную PARTIAL-публикацию5 файлов; blocker к публикации не найден. Это не закрывает native lifecycle или обычную city population.
Разрешённая публикация PARTIAL: `git add -- <5 exact paths>`, `git diff --cached --name-only/--numstat/--check -- <5 exact paths>`, `git commit -m <096N message> -- <5 exact paths>`, `git push origin HEAD:feature/phantom-world` после свежего remote comparison. Reviewer использовал только status и exact diff/stat. Commit — публикационный commit этого отчёта; полный SHA/push receipt сохраняются в private evidence и финальном ответе. Force/rebase/reset/clean/stash/branch не использовались.
Goal usage: pre-native snapshot525222tokens/2088s. Превышены ориентиры suffix250k и500k: разработка отдельного sealed runner, разбор двух execution SID contexts, missing TSV, несколько guard review/fix циклов и избыточные повторные filesystem проверки увеличили расходы. Это отклонение efficiency standard, а не разрешение расширять product scope; следующая кампания не начата.
Финальные usage/time сообщаются через goal tool и publication receipt. После отчёта и разрешённой безопасной публикации — STOP.
