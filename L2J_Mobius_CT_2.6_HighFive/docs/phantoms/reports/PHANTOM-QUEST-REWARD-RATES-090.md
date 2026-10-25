# PHANTOM-QUEST-REWARD-RATES-090

25.10.2026. **FULL090=PARTIAL / NATIVE_NOT_RUN. IMPLEMENTED=PASS, FOCUSED_VERIFIED=PASS.** Исправлены99 конечных предметных prize-grants Q663/Q384/Q386; Q662 уже использовал native rewardItems и остался byte-identical. Единственная новая настройка — RateQuestReward1→50. Все271 прежних overrides сохранены; Save→Verify→изолированный Apply→Verify и обе идемпотентности прошли. Фактический Player выигрыш, debit ставки, RNG parity и replay090 не запускались. Клиентская QA отложена; новая поставка/сервер/БД не создавались.

## Authority и read-first

`R=D:\Codex\repos\L2J-phantom-world`, `M=R/L2J_Mobius_CT_2.6_HighFive`, ветка `feature/phantom-world`. До изменений local/upstream/live GitHub HEAD равны `0955c3ffc790c19de29575eee86b60424f91da0c`. Независимо прочитан GitHub commit: parent `48a5c7f1f1b14cd0b09c4a461e4da7e4b4549560`, subject `phantom(task-089): add opt-in corridors with open native gates`,17 changed paths без пересечения с090. Чат089 idle; его последний completed turn содержит прямое решение владельца «Остановиться на PARTIAL». Таким образом, исполнение089 закончено, его открытые native/navigation gates не объявлены принятыми.

Начальный tracked status пуст; untracked верхнего уровня: `.phantom-local/` и16 чужих task directories, включая входной090. Рекурсивный обход исторических private/untracked не выполнялся. Пакет090:8/8 SHA256 и sizes PASS; SHA самого MANIFEST_SHA256.json `82e61c28170cb1ee033352bff123195ad05d94a9dde71b5a91bfbf3a11ee5896`. Все6 blob из SOURCE_MAP для четырёх scripts, Rates.ini и OwnerConfig совпали с HEAD.

Прочитаны root AGENTS.md/README.md, module Agents.md, релевантные разделы master plan/current status, workflow/task-package contracts, owner final targets, config contract088, отчёты088/089/037, весь пакет090. Изучены четыре scripts, Quest reward/give/take mechanics, RatesConfig, OwnerConfig.ps1 и существующие OwnerConfigChecks, OwnerProfileVerifier, ScriptEngine/ScriptExecutor/ScriptFileManager/ScriptClassLoader, Goal037 auditor/suite/build targets. Дополнительные AGENTS в предках/целевых подкаталогах и module README не найдены; отдельного code-map/pattern-файла для этого scope нет, карта — SOURCE_MAP090. Исторические C:-пути module instructions не использованы.

Локальные аналоги: Q662 native rewardItems, штатный OwnerConfig transactional pipeline, Goal037 AST static suite/negative controls, штатные script compiler/file-manager/class-loader. Сохранены Java25/Ant, production API и архитектура. No new rate/helper/engine/schema/DI layer. Проверки не заменяют native gameplay.

## Призовой audit и причина

| Квест | Исходные grants →090 | Сохраняемые пути и количество sites |
|---|---|---|
| Q663 |46 ItemHolder prize giveItems→rewardItems: onTalk jackpot2, onEvent jackpot4, D/C scroll2, recipes18, parts18, B scroll2 |12 giveAdena(...,true) без изменений;3 onKill giveItems Spirit Bead2/1/1; stakes50/1; memo-state, streak, party и RNG прежние |
| Q384 |20 prize grants в reward(): обе ставки, winning3/0 lines, в том числе совместные boots/gloves |26 giveItemRandomly Iron Gate Medal;10/100 stakes у обоих NPC; board/selection/chance прежние |
| Q386 |33 prize grants в reward(), исходный count4/10 |24 giveItemRandomly Stolen Infernium Ore; stake100; board/selection/getRandom(33) прежние |
| Q662 |11 rewardItems уже canonical; Java не менялась |Red Gem stake50, REQUIRED_CHIP_COUNT50,1 objective giveItemRandomly; cards и state reset прежние |

Других прямых inventory mutations или payout owners в этих четырёх scripts не найдено. Root cause трёх scripts: giveItems для обычных non-quest prizes обходит RATE_QUEST_REWARD; его отдельный quest-item multiplier не является prize rate. Quest.rewardItems(ItemHolder) делегирует native itemId/count overload; при UseQuestRewardMultipliers=False non-Adena count умножается один раз на RATE_QUEST_REWARD. Adena57 использует RATE_QUEST_REWARD_ADENA; giveAdena(true) уже делегирует туда. В Q662 второго множителя не добавлено.

Побайтовый guard восстановил ожидаемый результат только99 заменами имён helper внутри разрешённых методов: каждый из трёх новых Java bytes равен этому результату, включая newline style. Остальные bytes — ставки, условия, RNG, state reset, drops, party logic — тождественны baseline. Q662 byte-identical. Rates.ini byte-identical baseline после ровно одной замены1→50. Source guards показывают сохранение reset/selected checks; фактический repeated bypass/Player payout не проверен. HTML с базовыми количествами не изменён и может показывать исходный размер приза при новом ×50.

| Source | SHA256 новых bytes |
|---|---|
| Q663 | `aeb1e31d6f552ed233bd3cbd8dfc60d297b8505f6ec79d570cb0288e8d185732` |
| Q384 | `03d1ab4d0d04c04c6538f196fa08c9805c708bb0d19c3985fc0c22535564cbe1` |
| Q386 | `80ca2a9b0de481e04a8e47994ad707a6b6d90329193b5d067d4e0b9dc9abde19` |
| Q662, unchanged | `644c05f99c89f74e34248dba33ad73a2bf7d3e27da7dd8515528dd4a210f97fa` |

## Owner preservation и независимый stage

До записи созданы85 byte-exact backups вне checkout в новом private TASK090 TEMP:68 public INI, ClassMaster.xml,3 metadata,5 XML/XSD inputs,2 manifests,4 scripts, existing test suite и contract. Сохранены SHA и полная native Read-OwnerIni map1521 keys. ACL новой копии ограничен host owner, sandbox identity и SYSTEM; reparse paths отвергаются. Database/Network/Server/private INI, credentials, старые runtimes и protected078 не читались/не копировались.

Полное pre/post сравнение каждого из1521 ключей всех68 public INI: ровно `Rates.ini / RateQuestReward: 1→50`. Остальные67 INI и ClassMaster byte-identical. В наборе19 approved088 изменился только Rates.ini; остальные18 файлов сохранены. XP25/SP35/Adena25, QuestItemDropAmountMultiplier50, UseQuestRewardMultipliers=False и category1 прежние; личные QoL/allowlists/Player/Olympiad/Alt+B и остальные ключи также проверены общей сверкой.

Штатный source Save:68 files,272 overrides, changed2 — OwnerConfig.json и OwnerProfilePreserved.properties. Все271 старых file/key/kind/baseValue/value семантически равны baseline; добавлена ровно native number override RateQuestReward base1/value50. Пересортировка/формат JSON штатного PowerShell Save не является потерей настроек. TSV изменён лишь в устаревшем QUEST note: `RateQuestReward=50; other quest keys preserved`. Повторный Save→Verify: changed0,3/3 metadata hashes стабильны.

Независимый `T/apply/game` создан из проверенных baseline public bytes. Перед Apply восстановлены все272 baseValue, включая новый rate1; нельзя получить ложный PASS простым копированием уже готовых overrides. Source profile Apply→Verify:272 overrides, conflicts0, changed21 paths (18 INI+3 metadata). Все68 public INI после Apply byte-identical source; все1521 keys совпали с ожидаемой map. ClassMaster сохранился отдельно. Repeat Apply→Verify changed0;77/77 staged files byte-identical. `OWNER_PROFILE_PASS rules=38`, XML_XSD=PASS. Quest digest обновлён штатно; остальные preserved values прежние.

| Файл | До090 SHA256 | После090 SHA256 |
|---|---|---|
| Rates.ini | `ac5a73283fc2f2f582dcbab38ac9128d7553b475dcf74700229ce13a90a67244` | `ba9c6fcbd364e09588f50a4d507d06fc9f21f0bfeaaba229977b44124db819f2` |
| OwnerConfig.json | `9cd3cd0cc68f7eab363aaf444e9f291a1f5af701f174b6fc493d4f4df7c47df0` | `cbb60daacc466518459faf536f4d088ad85ec160568d57f54f248de74fd704ac` |
| OwnerProfile38.tsv | `5670fa6bc0bd6457cfac849bd2c45e24dbd317d86d501015b3ab4624555312db` | `4f25d9419b1a01aff2792a29f09a6b12f6fc464314ace73419ea4590a6ad3244` |
| OwnerProfilePreserved.properties | `fed46ac78630cef11b617a8caf4e624aed88ac19bcff29adbc69de9ad1710529` | `c49b6a7e3aa04f55c287dd28212ef9935b478aa78e31d74c2e6caa2d8712e9fc` |

Quest digest: `e6ba4e5a07c537000fc8b5680e1276cd3f82112f6e19a1c69186c608e46b3a67`→`5f62ca25a25eb94773e58c2e43689fc8ed125d2923552e38ea085f639b2ae256`. Датированный addendum добавлен в contract088, исходный текст/исторические hashes не переписаны. Manifests088 совпали с исходными: INPUT `efa9dc709767d2fb877569e97bbc0ed090a2907a84a744523f2058335dba3694`, package `2b2ce8abf7bde377fa9cdd2c5563e4693d52161005891ee096f5ea5dfc87f9a8`.

## Goal037: исходный конфликт и разрешённое исключение

До production edits новый focused test дал RED: canonical prize sites Q663/Q384/Q386=0 вместо46/20/33; Q662=11 уже PASS. Одновременно исходный full static audit нашёл pre-existing stale manifests Q234/Q235/Q255/Q511. Проверены все543 inventory source SHA, added/removed0. Точный read-only diff088→089 по этим четырём sources и manifests пуст. Для113 исходных AST rows выражения/классы/rate authority/decision/evidence multiset равны; устарели source SHA/позиции/fingerprints.

Работа остановлена до prize/config writes. Владелец явно разрешил: «Разрешить обновление четырёх старых pins и продолжить090». Исключение касается только двух manifests, не Java этих четырёх квестов. Их фактические source hashes после090 равны pre-edit snapshot. Входной пакет090 не изменён.

Штатный `QuestRatesAstAuditor.generate(M)` вызван один раз после финальной prize правки. Changed inventory rows только7; changed sites принадлежат ровно этим7 sources: Q23434, Q2356, Q25567, Q5116, Q38483, Q38685, Q66375. У прочих536 inventory rows и463 source groups site manifest строки идентичны; Q662 без дельты. Число543 sources/7066 sites сохранено.99 grants перешли из FIXED_SCRIPT_MECHANIC в canonical terminal reward;8 getRandom sites штатный неизменённый auditor переклассифицировал из objective chance в NOT_REWARD после удаления giveItems из prize blocks. RNG выражения/шансы не менялись. Parse failures/unresolved/unsupported0.

| Manifest | До090 SHA256 | После090 SHA256 / bytes |
|---|---|---|
| goal037-quest-inventory.tsv | `0e730224f7ac1a109401a9bc45c3fbd468a08bbc53ca04c26fd58293a34b12f8` | `991cbd64dc1bf13ab39edbfe0472331ec9937c6268aa2001529c06d52b32da10` /119388 |
| goal037-rate-sites.tsv | `725527e0e1e270b7511c0141297a805f165318daa8d225cb9bdbea61ea0f7d1e` | `ec43ab94a6d374558679503cac9a7c551e9ac5c31fd2f2d1f91a3c14d22c4d9e` /3142788 |

## Проверки и границы

`T=<private TEMP>/phantom090-1dc420b9c49f4baa8f4767d500e80c1c`, `C=T/source`. Физический shadow содержит только необходимые Java/tests/resources/quest corpus/item XML/public owner inputs/vendor libs,17470 byte-attested inputs; Database.ini отсутствует. Никакого Git worktree или пользовательской поставки. Ant выполнялся последовательно, outputs/JAR только T; исходный dist/libs не использовался как output.

| Команда / evidence | Exit и результат |
|---|---|
| Snapshot.ps1, VerifyState.ps1 baseline |0;85 backups,68files/1521keys/271overrides, byte equality |
| OwnerConfig.ps1 -Action Verify baseline |0;68/271, changed0 |
| ant -f C/build.xml phantom-quest-rates-goal037-static-test, RED |1; existing stale-manifest failure и expected casino-prize failure; negative controls PASS |
| OwnerConfig.ps1 -Action Save, затем Verify source |0/0;272 overrides, changed2/0 |
| OwnerRoundTrip.ps1: repeat Save/Verify, independent Apply/Verify×2 |0; source changed0; staged changed21→0;77 hashes stable |
| QuestRatesAstAuditor M generate |0;543 sources/7066 sites, unresolved0 |
| ScopeCheck.py и VerifyState.ps1 -AllowRewardChange |0;99 prize-name-only replacements;1 public key delta;271 prior overrides preserved;7 manifest source groups only |
| ant -f C/build.xml jar phantom-quest-rates-goal037-static-test |0;2365 production+403 test sources; JAR GREEN; static3/3, seed37003700,7 manifest negative controls |
| java -cp CP org.l2jmobius.gameserver.scripting.engine.QuestRewards090CompileCheck C |0; четыре реальных scripts с production Java8 script options, ScriptFileManager/ScriptClassLoader; compile/type-load4/4, initialized=false |
| java -cp CP org.l2jmobius.tests.phantoms.QuestRatesAstAuditor M verify |0; actual source/manifests, parse/unresolved/unsupported0 |

`CP=T/build/bin;T/build/phantom-test/bin;C/dist/libs/*`. Smoke использует native compiler/file-manager/class-loader без reflection/private-method shell harness. Class initialization/quest registration и Player gameplay не выполняются. Новый focused static contract добавлен в существующий Goal037 runner, второй тест — только compile/type-load smoke. Synthetic count multiplication не выдана за инвентарную выплату.

Green Ant53s. Пять прежних test-source warnings: ExtendedOpenOption3 и deprecated System.runFinalization2; production compile ошибок нет. Shadow GameServer.jar SHA256 `7a2100f93a1fe581db1d9f18a6c2e1d81c1656470e2dc58b97655a5fa5cd7395`, LoginServer.jar `0560ecd8b7bf3a1073fe12a973457224b2d9d6a4cf9308e59a1d022687f435ed`; эти тестовые бинарники не входят в commit.

Первый sandbox Ant остановился до compile/tests: OwnerProfileVerifier AccessDeniedException на private TEMP. Первый host retry не видел sandbox-only ACL; две Set-Acl попытки отказали с SeSecurityPrivilege. Добавление только host-owner inheritable ACE через icacls к новой собственной папке решило доступ, без изменения чужих ACL. Неудачные команды до запуска Ant не приняты за behavioral RED. Все дальнейшие Java/OwnerConfig/Ant выполнены с host access исключительно в указанном scope. Первый URL GitHub branches с encoded slash был отклонён400; правильный read-only git/ref API подтвердил live HEAD.

**P9_NATIVE=NOT_RUN.** Fresh shadow не имеет Database.test.ini/schema manifest. Существующие Goal037 CORPUS/HELPERS/native suites используют PhantomTestDatabaseGuard с фиксированными общей `l2jmobiush5_phantom_test`, user и port; это не новая разрешённая уникальная090 schema. Shared DB credentials не читались, guard не обходился, новая DB-инфраструктура не добавлялась. Поэтому helpers1x/non1x, actual script registration, actual 1x/50x prize deltas, stake debit, RNG control, native nonstackable inventory и repeated bypass — NOT_RUN. Source guards/compile не доказывают эти native gates. Полный ant verify не запускался; DB, MAIN/PLAY, клиент, protected078 и следующая задача не затронуты.

| Acceptance gate | Итог |
|---|---|
| P0_GIT_SAFE |PASS;089 завершён владельцем на PARTIAL, источник090 не пересекается |
| P1_CONFIG |PASS; все1521 keys и19 approved files сохранены с единственным rate delta |
| P2_PRIZE / P3_Q662 |FOCUSED_PASS, source contract и compile; native awards NOT_RUN |
| P4_NEGATIVE |SOURCE_INVARIANTS_PASS; actual debit/RNG/replay NOT_RUN |
| P5_GOAL037 |PASS с явным owner exception на4 старых pins |
| P6_OWNER_SAVE / P7_OWNER_APPLY |PASS;272 overrides,271 prior equal,38 rules, idempotence |
| P8_COMPILE |PASS compile/JAR/type-load4; registration/gameplay NOT_RUN |
| P9_NATIVE |NOT_RUN; FULL090=PARTIAL |
| P10_REPORT_PUSH |Scoped publication; окончательный SHA и post-push receipt нижеописанным способом |

## Exact scope и публикация

Bounded exception13 files прямо следует из пакета090 (4 production +3 metadata +2 manifests +2 tests +2 docs); добавочных artifact families нет. Только следующие paths относительно M входят в commit:

1. `dist/game/config/Rates.ini`
2. `dist/game/config/OwnerConfig.json`
3. `dist/game/config/OwnerProfile38.tsv`
4. `dist/game/config/OwnerProfilePreserved.properties`
5. `dist/game/data/scripts/quests/Q00663_SeductiveWhispers/Q00663_SeductiveWhispers.java`
6. `dist/game/data/scripts/quests/Q00384_WarehouseKeepersPastime/Q00384_WarehouseKeepersPastime.java`
7. `dist/game/data/scripts/quests/Q00386_StolenDignity/Q00386_StolenDignity.java`
8. `test/resources/phantoms/quest-rates/goal037-quest-inventory.tsv`
9. `test/resources/phantoms/quest-rates/goal037-rate-sites.tsv`
10. `test/java/org/l2jmobius/tests/phantoms/PhantomQuestRatesGoal037Suite.java`
11. `test/java/org/l2jmobius/gameserver/scripting/engine/QuestRewards090CompileCheck.java`
12. `docs/phantoms/OWNER_GAME_CONFIG_CONTRACT_2026-10-25.md`
13. `docs/phantoms/reports/PHANTOM-QUEST-REWARD-RATES-090.md`

Git явно разрешён текущим /goal/TASK090. Использованы `git symbolic-ref --short HEAD`, `git rev-parse HEAD refs/remotes/origin/feature/phantom-world`, `git status --short --branch`, `git status --porcelain=v1 --untracked-files=no`, `git ls-tree HEAD --` по6 exact source/config paths; `git diff 48a5c7f1f1b14cd0b09c4a461e4da7e4b4549560 0955c3ffc790c19de29575eee86b60424f91da0c --` по4 old-pin sources и2 manifests; `git diff --stat --` и `git diff --` только allowlist. Штатный Save использует разрешённые `git -C M rev-parse --show-toplevel`, `git -C R rev-parse --verify HEAD^{commit}`, `git -C R show <HEAD>:<exact public config path>` для native managed baseline.

Перед публикацией выполняются strict UTF-8, два отдельных encoding scans, exact allowlist guard, package SHA, diff review и `git diff --check -- A` (A=13 точных путей выше). Затем `git add -- A`, `git diff --cached --check -- A`, `git diff --cached --name-only -- A`, `git diff --cached --stat -- A`, `git diff --cached -- A`, ровно один `git commit -m "phantom(task-090): apply native casino prize rates and preserve owner config"` и обычный `git push origin HEAD:refs/heads/feature/phantom-world`. CRLF-aware `git -c core.whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol diff --check -- A` допустим только при фактическом CRLF false-positive и с явным результатом стандартной команды; repository config не меняется.

Независимый read-only reviewer проверил production/test/config/manifests/evidence: Critical/Important0, исправлений не требуется; scoped PARTIAL delivery допускается. Actual native Player/replay/registration/UI reviewer также не проверял. Дополнительные Git-команды reviewer: `git status --short --untracked-files=no`, `git rev-parse HEAD`, `git diff --numstat --` по OwnerConfig.json и двум manifests, остальные exact diffs перечислены выше.

Staged13 paths проверены также `git diff --cached --raw --no-abbrev -- A`:11 Git blobs побайтово равны проверенным working bytes. В2 файлах Git выполнил только CRLF→LF: OwnerConfig.json (source SHA `cbb60daacc466518459faf536f4d088ad85ec160568d57f54f248de74fd704ac`, Git blob SHA256 `62fb313d5e59f5b703efc8af914806efa76ff9f8bc5f85b127eaef5fa121ff4e`) и PhantomQuestRatesGoal037Suite.java. Первая проверка строгого raw equality остановилась на JSON; уточняющий guard доказал equality после только CRLF→LF для обоих файлов. Три quest scripts, Rates.ini, два manifests и прочие файлы staged byte-exact. Ручной нормализации native JSON не выполнялось; overrides сохранены.

Стандартные pre-stage и cached `git diff --check -- A`: exit2, CR в сохранённых CRLF Rates.ini/Java трактуется как trailing whitespace. Оба CRLF-aware повтора: exit0; byte guard подтвердил сохранение исходных newline bytes production. Repository config/не относящиеся к задаче пробелы не менялись. Strict UTF-8 всех13 файлов PASS; allowlist13/outside tracked0, historical contract prefix и весь package090 сохранены.

- mojibake-маркеры в изменённых файлах проверены:13 файлов,27 маркеров,0 совпадений.
- escaped Cyrillic в изменённых файлах проверены:13 файлов,6 regex-паттернов,0 совпадений.

Собственный SHA нельзя включить в создающий его же commit: фактический commit SHA, post-push local/upstream/live equality, tracked-clean status и результат normal push фиксируются в финальном сообщении и private publication receipt T. Отчёт входит в этот единственный scoped commit. Прежние untracked task packages сохраняются без staging/clean; после публикации повторяется их верхнеуровневый status. No force/history/master/worktree changes. Следующую задачу не начинать.
