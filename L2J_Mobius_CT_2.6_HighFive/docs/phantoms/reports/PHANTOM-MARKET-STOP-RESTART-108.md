# PHANTOM-MARKET-STOP-RESTART-108

Статус: **FULL108=BLOCKED_PRE_NATIVE**. STOPA108=NOT_RUN/BLOCKED_PRELAUNCH; RESTARTB108=NOT_RUN; STOPB108=NOT_RUN. Native запусков0; wrapper Action A вызван1 раз, B0. Natural STOP, native EARNED и restart не доказаны. TASK109 не запускался. Исторический107 остаётся FORCED_UNCLEAN.

## Baseline и G0

- Ветка `feature/phantom-world`; local HEAD, tracking, FETCH_HEAD и fresh live origin до изменений: `0db9771923870222878b95bf7fe84dfb192606e2`. Known ancestor совпал с фактическим HEAD; ancestry exit0. Fetch и ls-remote после sandbox/network refusal выполнены с разрешённым расширенным доступом, exit0.
- Exact original OWN102 authority/schema `l2jmobiush5_phantom_test_102`, installation/source/inputs/DB-config hashes и owner SID подтверждены. Existing private roots: физические, без reparse, protected owner+SYSTEM ACL. Полная SQL visibility через SHOW GRANTS; own SQL0/foreign0. CIM, Get-Process и TCP census: JVM0/TCP3308 established0.
- Два одинаковых CLI preimage и два одинаковых штатных десятисрезовых `Task105Census snapshot`; original5000 READY/distinct IDs/init EXACT, creation/reseed0. Profiles5001, characters5256, items54720, components34880, recipebook150, accounts5256, offliners250, offlineItems2364.
- Offline `Task105Census diff`: merchant READY250, неизменные birth/lifetime payloads, роли SELL162/BUY63/MFG25. Это durable roles/offline rows, не новый native World census.
- Canonical market codec на captured G0: ledger+71 COMMITTED receipts EXACT; funding63×50000000=3150000000, stock8, из них enchanted4, stock value767112; incremental grants0.
- Source OwnerConfig Verify: files68, overrides287, conflicts0, changed0. Исходный JSON побайтово равен preimage107; original tuples сохранены. Source True/5000/128/128, Player.ini20; fixture291 не использовалась.
- Historical protected107 pins186:185 unchanged, одно ожидаемое post107 отличие Population102Native. Final protected allowlist184 исключает только два разрешённых изменяемых test-файла; drift0. Source Geo203: drift0. Production/config/semantic/merchant pricing не менялись.

## Реализация и изоляция

Изменены только пять разрешённых файлов:

1. `test/java/org/l2jmobius/tests/phantoms/PhantomPopulation102Native.java`: namespace recovery108A/B, receipts108, отдельная authority, reuse107 merchant evidence, current witness preparation receipt и terminal guard. Старые phase paths сохранены.
2. `test/java/org/l2jmobius/tests/phantoms/PhantomTask102Lane.java`: authority108, запрет смешения namespaces, private cap16 и полная session fence.
3. `test/java/org/l2jmobius/tests/phantoms/PhantomStop108Checks.java`: DB-free stale-first/replaced-token/all-stale, registration/cleanup/EARNED/exactly1/outstanding0 guards, B-before-clean/sameJAR negatives и offline canonical market audit.
4. `tools/phantom-runtime/Task108Run.ps1`: physical stage108, serial external build, private cap16, one seal/A/B gates, natural exit/postflight и fail-closed market comparison. После blocker добавлена узкая compile/seal/prelaunch проверка census helpers; см. ниже.
5. Этот отчёт. `PhantomMerchant107Native.java` не изменялся; immutable frozen pricing переиспользован.

Один `Task102 Prepare`:29055 inputs,203 Geo, Database.ini первоначально ABSENT; свежая preparation identity и отдельный private archive108. Source checkout/build/dist не использовались для сборки. Stage OwnerConfig Apply→Verify→Apply→Verify:68 files/287 overrides, conflicts0/changed0 во всех четырёх шагах. Затем Bind только к original102, private active16/cap16; source128 сохранён.

Одна serial external JDK25/Ant команда `ant -f <shadow108>/build.xml compile-tests jar`: exit0, BUILD SUCCESSFUL,53 seconds. Generic `ant verify`/DB suites0, standalone jar invocations0. Один seal108, затем ни rebuild, ни reseal, ни запись frozen binaries/manifest не выполнялись.

Source JAR SHA256: `f56dabe46d09bfb65a03b7824abe6aeaa16255d4cd67abcd1fe3ef86fe57a471`.
Sealed108 JAR SHA256: `1b2d0353443019b3dcf53627c4de41a77ac1c481c80352668cf4e069e3877412`; final hash EXACT. До seal четыре изменённых code/tool source совпали с physical stage.

## Focused проверки

Все команды ниже использовали новые staged compiled classes и serial запуск; exit0, кроме отдельно описанного первоначального неверного104 invocation:

- `java -cp <stageCP> org.l2jmobius.tests.phantoms.PhantomStop108Checks`:3 focused groups PASS; JDBC0/World not initialized. Scalar registration/terminal guards не выданы за реальный native callback.
- `org.l2jmobius.gameserver.phantoms.player.PhantomShutdown103Checks`:3/3, actual dispatch service/model callback, JDBC0/World0.
- `org.l2jmobius.tests.phantoms.PhantomLock104Checks <shadowModule>`:4/4, directions2×8, ecology5000/1024 pulses/admitted128;471ms. Это DB-free fixture, не World128.
- `org.l2jmobius.tests.phantoms.PhantomLease105Checks`:3 groups, lawful residual merchants/unknown and stale ownership/guarded resource closure PASS.
- `org.l2jmobius.gameserver.phantoms.economy.PhantomLivingMarket107Checks`:BUY11 cases PASS; ROOT_BUY_63 GREEN. Новая paid/native торговля не заявлена.
- `org.l2jmobius.tests.phantoms.PhantomPopulation102Native logout107-check`:stale-first/current-second/all-stale PASS, JDBC0.
- Тот же class `config <shadowModule>` до cap16 overlay:True/5000/128/128, bounds/cohort/checkpoint negatives PASS, JDBC0/World0.
- `PhantomStop108Checks market <G0first> <G0second>`:canonical original71 grants/ledger EXACT, JDBC0.

Focused Java CLI invocations9:8 успешных и первоначальный104 без обязательного module argument, exit1 до эффектов. Повторён только104 с правильным аргументом; остальные невыполненные checks продолжены последовательно. Исходники тестов после зелёных checks не менялись.

## Pre-launch blocker и STOP

Единственный wrapper Action A отказал в `Census('beforeA')`: `ClassNotFoundException: Task105Census`. Ant `compile-tests` берёт `test/java`, но существующие `Task104Census.java` и `Task105Census.java` находятся в `tools/phantom-runtime`. Wrapper ошибочно не компилировал эти transitive CLI dependencies до seal. Это дефект новой orchestration, не доказанный production shutdown defect.

`launch6.begun=false`, `start6.begun=false`; native108 JVM не создавалась. До ошибки выполнен только read-only Clearance; SQL writes0. Native World16/merchant OPEN/EARNED/drain/checkpoint/natural exit108: **NOT_RUN**. `phase6.complete`, `exit6.clean` не создавались. Retained108 JVM отсутствует; force approval не требуется и не использовалось.

По NO RETRY после BLOCKED A runtime-часть остановлена: новой A, B, force, kill, CTRL-C, agent/JMX, MariaDB restart и изменений чужих процессов нет. Frozen stage сохранена как evidence; подмены manifest/exit.clean нет.

Узкая публичная правка после blocker: Build теперь serial компилирует два existing census helpers через `javac` в test bin и требует оба `.class`; Seal и prelaunch также отвергают их отсутствие. Final PowerShell AST parse exit0. Эта правка **NOT_STAGED / NOT_EXECUTED / NOT_NATIVE_VERIFIED**: сохранённая sealed108 содержит прежний wrapper; её не обновляли и не переиспользовали. Новая задача/authority необходима для следующей native попытки.

Final read-only audit: две выгрузки побайтово равны G0; значит membership/EXP/SP/level/items/objectId/enchant/components/row versions/recipebook/offline data/accounts/market receipts не изменились, mutable deltas0. SQL writes0, JVM0/Get-Process0/own SQL0/foreign0/TCP0. Native perf CPU/heap/GC/threads/drain elapsed/p95:UNAVAILABLE, поскольку native не запускался.

## Review, scope и процесс

Independent read-only reviewer: pre-native lifecycle/orchestration P1=0/P2=0 и отдельный market-audit delta P1=0/P2=0. Он не проверял private artifacts или фактическую сборку и не обнаружил missing census dependency; это ограничение первоначального review. Final review относится только к безопасной BLOCKED поставке, не к native acceptance.

Прочитаны root/module AGENTS, весь package108, bounded status/master/workflow/efficiency, report107+PLAN и12 initial exact code/tool paths. Дополнительные5: `Task104Census.java` (ten-slice delegate), `build.xml` (build graph), `PhantomNativeWorkScope.java` (current root contract), `PhantomShutdown103Checks.java` (focused route), `PhantomMerchantMarketLedger.java` (canonical durable codec). Repository searches6. Большие source выдавались bounded ranges; один слишком широкий build-target rg дал truncated вывод и затем был сужен. Root README прочитан; module README.md и вложенные AGENTS в проверенных docs-папках не найдены; отдельный code-map/pattern file closed read set не задавал.

Промежуточные ошибки orchestration сохранены: SQL headers вызвали Duplicate frame slice (первые outputs сохранены; исправлены только CLI flags),104 argument guard, преждевременный Bind при ещё работающем OwnerConfig Java CLI (остановлен до эффектов; после завершения CLI Bind exit0), первый text-scan literal command не разобрался из-за shell encoding (повтор с теми же27 маркерами через codepoints). Они не считались native попытками или PASS.

- Mojibake-маркеры в изменённых файлах проверены:27 markers,5 files, совпадений0; два финальных одинаковых прохода.
- Escaped Cyrillic в изменённых файлах проверены:все6 case-sensitive patterns,5 files, совпадений0; два финальных одинаковых прохода.
- Protected source184/Geo203 drift0; tracked scope только два разрешённых test paths, новые files только allowlist. Чужой untracked dirt и исторические пакеты/отчёты сохранены.
- Native invocations0, A-wrapper1/B0, Ant graph1, Prepare1, Seal1, force0. На final hash audit elapsed от private index22.7min; финальная публикация выполняется позже. Goal API вернул старый blocked snapshot с tokensUsed0/time4s, поэтому фактические token usage и общий shell-command count UNAVAILABLE; нулевой расход не заявляется.

Private evidence pointer: `.phantom-local/logs/PHANTOM-MARKET-STOP-RESTART-108/index.private.json`; detailed authority/SQL/receipts и physical shadow находятся во внешнем owner+SYSTEM archive, вне Git. Public secrets/PID/session IDs/host paths отсутствуют.

Git разрешён TASK108/AUTHORITY/DELIVERY. Использованы bounded `git rev-parse`, `symbolic-ref --short HEAD`, `status --short`, `for-each-ref`, `worktree list --porcelain`, `fetch origin feature/phantom-world`, `ls-remote origin refs/heads/feature/phantom-world`, `merge-base --is-ancestor 0db9771923870222878b95bf7fe84dfb192606e2 HEAD`, exact-path `diff --numstat`, `diff`, `diff --check`; штатный Task102 Prepare дополнительно проверил ancestry102 и ветку. Публикация: exact-five-file `git add -- <allowlist>`, exact staged diff/check, `git commit`, обычный `git push origin HEAD:refs/heads/feature/phantom-world`. Финальные commit SHA/remote equality фиксируются в ответе и private delivery receipt, без самоссылочного изменения отчёта. Add-all/stash/reset/restore/force/amend/merge/rebase не использовались.

**STOP. FULL108 не достигнут; TASK109 не начат.**
