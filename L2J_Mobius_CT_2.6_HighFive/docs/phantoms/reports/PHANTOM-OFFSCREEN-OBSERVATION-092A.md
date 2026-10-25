# PHANTOM-OFFSCREEN-OBSERVATION-092A

25.10.2026. **SUCCESS_092A_OBSERVER_GUARD — production foundation; OFFSCREEN_TRANSIT_OPEN.**

Реализованы сохранение recent-human после STORED и доступный configured runtime typed observer admission. Focused и ближайшие DB-free регрессии GREEN, одна полная compile/JAR успешна. Точный commit SHA и результат normal push приводятся в финальном сообщении после создания этого отчёта. Это не FULL092, PLAYER_TRAVEL, SERVER_VERIFIED или earned farm. Следующая задача не запускалась.

## Authority и read-first

Git root фактически `D:\Codex\repos\L2J-phantom-world`, ветка `feature/phantom-world`. До изменений local HEAD, `origin/feature/phantom-world` и live branch совпали: `63f7c819c3ea51d7b1bbe83476e590bdb235180b`. Tracked dirt отсутствовал; чужие untracked `.phantom-local/` и task packages сохранены. Все шесть payload entries MANIFEST_SHA256.json совпали по размеру/SHA-256; manifest и пакет не редактировались. Исходный ZIP не передан: проверены распакованные файлы.

Прочитаны root/module AGENTS, релевантные разделы root README, master plan, PHANTOM_CURRENT_STATUS, RELEASE_CONTRACT, TASK_PACKAGE_STANDARD, весь CODEX_WORKFLOW_CONTRACT и весь пакет092A; reports091/092; production retention/System/native locality/point/World constants и topology registry; build.xml, OwnerConfig/OwnerProfileVerifier; ближайшие observer/locality/scheduler/soft-return tests. Дополнительные AGENTS в предках/целевых подкаталогах, module README.md и отдельный актуальный code-map/pattern не найдены. SOURCE_MAP092A использован как карта.

Переиспользованы monotonic clock injection и retention facts, существующие typed immutable records, lazy humanObserver classification, PhantomNativeLocalityEnvelope.prewarm и штатные DB-free suites. Сохранены Java25/Ant, native lifecycle и owners. Дополнительные production owners, зависимости, конфиги, workers, timers, SQL и schemas не добавлены.

## Production изменение и API

`PhantomMaterializationRetentionPolicy` сохраняет подтверждённую метку в пределах исходного recentHumanMillis, включая observe(false) и refresh(empty). STORED возвращает пустые physical reasons/materialized=false, поэтому не получает retained/native action admission. Новый read-only recentHumanObservation(profileId) возвращает RECENT/CLEAR/UNKNOWN. До полного grace после создания policy неизвестная история не считается отсутствием человека; регрессирующий clock даёт UNKNOWN. Production grace остаётся60000ms, тесты используют injected clock без sleeps.

Очистка — resumable iterator по ConcurrentHashMap, до256 записей за refresh, условное remove(key, timestamp) не стирает обновлённую метку. Чтение recentHumanObservation память не меняет. Атомарный merge сохраняет более позднее подтверждение при конкурентных observe; задержанный старый sample не сокращает grace.

Публичная production façade:

```java
PhantomSystem.offscreenObserverAdmission(
    long profileId, PhantomTopologyPoint source,
    PhantomTopologyPoint destination, int instanceId)
```

Результат — immutable PhantomOffscreenObserverAdmission.Verdict с Code, точными profile/source/destination/instance, checkedAtNanos и inspectedHumans. Фасад получает configured instance под коротким class monitor, затем отпускает его до census; проверяет RUNNING, известный topology profile и совпадение committed source. Остановка runtime во время проверки снимает положительный результат. Ошибки возвращают UNKNOWN, а не ALLOW.

При каждом вызове лениво читается World.getInstance().getPlayers(), применяется существующий фильтр online/non-headless и native client (!detached, client.player==player), либо LOCALPLAY_TEST_HUMAN. Узкий pure classifier вынесен в новый класс; прежний humanObserver сохраняет early return и short-circuit. Обычный onlineHumanPoints() с limit256 и fallback не менялся и не используется как полный census.

Проверяются максимум257 фактических human candidates, без накопления полного списка. Число257 возвращает HUMAN_CENSUS_OVERFLOW с inspectedHumans=257. При полной выборке до256 человек оба конца проверяются через prewarm (два native surrounding regions по XYZ/instance). Native-invalid Z boundary, null, ошибочные координаты/instance, недоступный census и незавершённый startup grace не дают ALLOW. При отсутствии людей autonomousObservationPoint остаётся угрозой: близкий блокирует endpoint; дальний возвращает UNKNOWN_AUTONOMOUS_OBSERVER, никогда не доказывает безопасность. Перед ALLOW recent-human проверяется повторно, включая обновление во время census.

Допуск только read-only/call-time; не вызывается на общем scheduler pulse. Он не удерживает World snapshot/позиции до будущего действия и не выполняет teleport, CAS, arrival, оплату или награду. 092B обязана снова вызвать допуск непосредственно внутри canonical commit admission и связать его с goal/revision/position/state identity. Такой binding в092A не реализован.

## RED → GREEN и review

Первый targeted javac компилировал неизменённый retention source, topology point и новый focused gate против существующих vendor/server classes, в собственный TEMP output. Запуск exit1 дал три независимые причины: STORED потерял timestamp (1000000→0); пустой refresh потерял RECENT_HUMAN; production overflow-aware API отсутствовал. Третья причина — RED отсутствующей возможности, а не утверждение о выполненном live census на baseline. После production fix тот же required gate проверяет настоящий257-й candidate и проходит.

Независимый read-only reviewer нашёл недоступность instance façade для закрытого configured runtime и гонку stale timestamp. Фасад сделан static. Дополнительный deterministic latch regression воспроизвёл actual RED: после нового подтверждения задержанный observe(t0) давал CLEAR вместо RECENT на60.001s. После merge — GREEN. Reviewer перепроверил исправления; незакрытых Critical/Important замечаний нет. Production edits завершены до единственного Ant jar.

Focused final —8 групп: STORED/grace/action holds; refresh/ограниченная очистка600 записей; census0/1/256/257 и остановка бесконечного iterable на257; свежие endpoints/движение/XYZ/instance/QA threat; unknown/errors/startup/renewal during census; connected/QA/offline/headless classifier; concurrent timestamp; static façade UNKNOWN_RUNTIME без startup. Boundary assertions сгруппированы внутри них.

На свежих production classes `DatabaseFactory.isInitialized=false`. Классификация проверена на pure production facts; реальные network Player/World enumeration и running native server не создавались. Это DB-free доказательство алгоритма и компиляции actual wiring, не native runtime proof.

## Команды и реальные результаты

Обозначения: `R=<repo>`, `M=R/L2J_Mobius_CT_2.6_HighFive`, `S=<TEMP>/phantom092a-a1f122b47ac74aa3b68d9ef6a5f25b5a`, `C=S/ant`. Все Java processes ограничены test/compiler/verifier, без Main/Login/GameServer/DB bootstrap. Test JVM heap256MiB, рабочая папка S/run, reports в S/reports.

Build lane содержит154 byte-exact файла: исходный build.xml, OwnerConfig.ps1, config Java sources для штатной валидации, public68 INI/3metadata, четыре enchant XML/XSD, vendor JAR. Production2366 Java читаются из M/java на месте; shadow из тысяч source/data файлов не создавался. Private INI, Geo, SQL/runtime не копировались. До изменений сохранены побайтовые preimages двух existing production owners, пяти config inputs и двух source JAR; SHA-256 проверены. Их безопасные копии остаются в S/preimage.

| Команда / шаг | Exit / результат |
|---|---|
| Source `powershell -NoProfile -ExecutionPolicy Bypass -File M/tools/OwnerConfig.ps1 -Action Verify`, до/после |0/0;68 files,287 overrides,conflicts0,changed0 |
| Package sizes/SHA-256 |0;6/6 |
| Targeted `javac -encoding UTF-8 -source 25 -target 25 -implicit:none -sourcepath S/empty -cp M/dist/libs/* -d S/red <retention,point,new-test>`; required test main |0/1; initial RED выше |
| Targeted javac четырёх policy/topology sources + test; focused main |0/0; GREEN. Последующий test-only concurrent RED1 → retention merge javac0 → GREEN0 |
| TEMP OwnerConfig Verify, sandbox; direct Java OwnerProfileVerifier |1/1; AccessDeniedException до build. Host Verify0,68/287/changed0; ACL не менялись |
| `ant -f C/build.xml -Dsrc=M/java -Dbuild=S/build -Dlibs=C/dist/libs jar`, host |0;22s, один production javac2366, один Ant invocation, correction0; Apply только TEMP,changed0;38 rules PASS |
| `javac ... -cp S/build/bin;C/dist/libs/* -d S/tests @S/test-sources.args` |exit0,408 test sources,5 существующих warnings; при закрытии zipfs source-JAR — AccessDeniedException. Не принят как чистый compiler PASS |
| Targeted javac трёх selected test sources, host; classpath fresh bin/tests + binary vendor JAR, без server/source-JAR |0, diagnostics нет. Production повторно не компилировался |
| Первый production test launch с незакавыченным PowerShell `-Dfile.encoding=UTF-8` |1 до main: `.encoding=UTF-8` принят как class. Исправлена только quoting аргумента |
| `java -Xmx256m '-Dfile.encoding=UTF-8' -cp CP org.l2jmobius.tests.phantoms.PhantomOffscreenObservation092AChecks production` |0;8 focused groups PASS,DB uninitialized |
| Тот же main с `retention-regression` |0; existing scheduler testRetentionReasons PASS; scale/native routes не выполнялись |
| `java ... org.l2jmobius.tests.phantoms.PhantomObserverHandoff040Suite M S/reports` |0;3/3,seed40004001 |
| `java ... org.l2jmobius.tests.phantoms.PhantomHumanLocalityTraceSuite M S/reports` |0;3/3,seed17001701; временный config только S/run |

`CP=S/build/bin;S/tests;M/test/resources;<четыре binary vendor JAR>`; GameServer/LoginServer/source JAR не входят в runtime test classpath. Компиляция остальных test sources не означает их запуск. Общий ant verify, DB/native/Geo/5000/128/stores campaigns не запускались.

Final build GameServer.jar SHA256: `7df54b0551c85ead2125e5d5b96052b70176a1d14d042b52c609480de1256a9c`.
LoginServer.jar: `f85c2fb7b12af20efca5388ccc53aeb623fd6f4f0712a7da9d8d33950518ccdb`.
JAR находятся только в S/build и S/ant/dist/libs; бинарники не добавляются в Git и не заменяют поставку пользователя.

## Сохранность и ограничения

Повторные hashes подтверждают неизменность154 copied inputs и source OwnerConfig/Rates/PremiumSystem/PhantomPlayers/PhantomMerchants/GameServer.jar/LoginServer.jar. Source Save/Apply не выполнялись. Исходные overrides287 сохранены. PhantomBackgroundState/Codec/Transaction/Planner, VisibleFarmTravel, World/Player/Npc, topology geometry, navigation, quests, Studio, SQL и live Semantic Pack не менялись. Format2/compact3 не переписывались; новая codec corpus campaign не запускалась.

Protected releases057–061/078, их DB/JVM/MAIN/PLAY, клиент, MariaDB и чужие worktrees не запускались и не менялись; полного hash audit этих поставок не проводилось. Собственные новые GameServer/native processes0; DB connection census не запускался и не выдаётся за DB0 proof. Нет новых native gameplay/earned/restart claims. Performance latency/CPU/heap/scale не измерены; дополнительный scan вызывается только явным admission, expiry batch ограничен256.

Mojibake-маркеры в изменённых файлах проверены отдельным полным набором; escaped Cyrillic в изменённых файлах проверены отдельно (Unicode и XML variants). Owner package содержит читаемую кириллицу и не исправлялся. Новые source/test strings — технический английский, отчёт — UTF-8 с прямой кириллицей.

## Exact allowlist и публикация

Ровно5 implementation/test/report paths относительно M:

1. `java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java` — production façade, live lazy census, narrow classifier reuse.
2. `java/org/l2jmobius/gameserver/phantoms/activity/PhantomMaterializationRetentionPolicy.java` — continuity/expiry/read-only observation/concurrent clock guard.
3. `java/org/l2jmobius/gameserver/phantoms/activity/PhantomOffscreenObserverAdmission.java` — typed conservative call-time check.
4. `test/java/org/l2jmobius/tests/phantoms/PhantomOffscreenObservation092AChecks.java` — actual classes RED/GREEN/negatives и точечный existing retention route.
5. `docs/phantoms/reports/PHANTOM-OFFSCREEN-OBSERVATION-092A.md` — этот отчёт.

Плюс7 неизменённых файлов только пакета `docs/phantoms/tasks/PHANTOM-OFFSCREEN-OBSERVATION-092A/`: ACCEPTANCE.md, DELIVERY.md, GOAL.txt, MANIFEST_SHA256.json, SOURCE_MAP.md, TASK.md, TEST_PLAN.md. Bounded exception для12 файлов явно задан DELIVERY §1/3; второй artifact family или новая подсистема не добавлены.

Git разрешён пользователем для exact-file commit/normal push и пакетом/root AGENTS для branch/scope guard. Использованы `git rev-parse --show-toplevel HEAD`, `git symbolic-ref --short HEAD`, `git status --short`, `git for-each-ref --format='%(refname) %(objectname)' refs/heads/feature/phantom-world refs/remotes/origin/feature/phantom-world refs/remotes/upstream/feature/phantom-world`, `git ls-remote origin refs/heads/feature/phantom-world`; `git diff -- <два exact existing production paths>`, `git diff --stat -- <те же paths>` и `git diff --check -- <те же paths>`.

Первый plain diff check пометил CR как trailing whitespace в смешанном CRLF/LF PhantomSystem (preimage2889CRLF/585LF); это существующий стиль файла. Проверка с `git -c core.whitespace=cr-at-eol diff --check -- <exact paths>` прошла; массовой нормализации строк и изменения Git config нет. Финальная staged проверка также использует CR-aware diff check и exact allowlist.

Публикация выполняется exact `git add -- <пять paths>`; package отдельно `git -c core.autocrlf=false add -- <семь exact paths>` для сохранения manifest bytes. Проверяются `git diff --cached --name-only -- <12 paths>`, `git -c core.whitespace=cr-at-eol diff --cached --check -- <12 paths>`, полный staged diff и `git status --short`. Единственный commit: `git commit -m "phantom(task-092a): preserve offscreen human observation and overflow guards"`; ordinary `git push origin HEAD:refs/heads/feature/phantom-world`. Финальные `git rev-parse HEAD refs/remotes/origin/feature/phantom-world`, `git ls-remote origin refs/heads/feature/phantom-world`, `git status --short` подтверждают публикацию и отсутствие tracked dirt. Force/history rewrite/branch/reset/restore/stash/clean/Stage All не применяются.

**Открыто:** 092B graph0 + canonical travel binding, actual offscreen transit и native earned outcome. Автоматического перехода к092B/093 нет; owner experience/visual acceptance остаётся отдельной работой.
