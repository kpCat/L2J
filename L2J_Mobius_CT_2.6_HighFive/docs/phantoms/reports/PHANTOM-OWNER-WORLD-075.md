# PHANTOM-OWNER-WORLD-075

Итог исполнения: **TOTAL=PARTIAL**. A/B сохранены ранним опубликованным commit; последующие D/E/F реализованы в указанной ниже границе. Уточнение владельца по редактируемости HARD A выполнено после штатного STOP OWN075, без нового игрового запуска. Непройденные native/client gates не объявляются PASS.

Baseline: local и live origin feature/phantom-world = `8ac113257bea0cd3ea241b708889b42b42b5b961`.
Свежий OWN074 Status обеих сред: Game/Login STOPPED, DB connections=0; JVM0 и шесть listeners0.
TASK074 опубликована и закрыта BLOCKED/PARTIAL, не PASS. Её долги B/C/D и universal economy сохраняются отдельно; R2/076 не создаётся.
Начальный dirt: `.phantom-local/`, packages068V/073/075; чужие файлы не изменяются и не staged. Пакет075 публикуется без изменения байтов.

| Lane | Итог | Граница доказательства |
|---|---|---|
| A | PASS | SOURCE и copied game 38/38, XML/XSD, Ant/copy gate; mutable profile positive/negative checks; текущие значения не изменены уточнением |
| B | PASS focused | ordinary max25, 100/85/70/66, bonus100/default65535 rejected at25; special caps unchanged; 11 независимых Servitor коэффициентов; полный клиентский lifecycle pending |
| C | PARTIAL | исходники Goal037/Q298/Q186 сохранены; нет обнаруженного rate-регресса, но high-level native repeat replay NOT_RUN и старый AST gate stale1/2 |
| D | PARTIAL | обычный earned-surplus producer/native offline lifecycle реализован; в bounded Main не появился законный earned магазин; auto-reopen того же listing после restart не реализован |
| E | PARTIAL | DB-free36/36 и sameDB cursor continuation без дублей identity; все исторические claims не завершены, universal lawful progression не доказан |
| F | PARTIAL | native SELL/BUY/CRAFT board + fresh Radar marker реализованы; handlers загрузились в Main; клиентские поиск/категории/marker OWNER_QA_PENDING |

Решение A/B: штатные ConfigReader/ConfigLoader, EnchantScroll, EnchantItemGroupsData, native stores и Ant/copy pipeline. Новых библиотек/движков нет. Бounded exception: несколько config/code/test/build файлов одной задачи необходимы для контроля owner profile; отдельный cleanup/Studio/UI не включён. Worktree не создавался. Secret Database.ini/Network.ini/allowlists и старые candidates/DB не изменялись.

Servitor1557: исходные XML коэффициенты сохранены. Девять полей идут через share-map; maxRecoverableHp/Mp — две существующие native Mul-функции, не выдуманные share-поля. Каждая положительная доля масштабируется `1+(stock-1)*multiplier`; OFF/1.0 сохраняют stock. Родные onStart/onExit/add/remove и тип Servitor сохранены; pets и остальные skills не получают новый множитель. Native клиентская проверка полного lifecycle пока NOT_RUN.

20 строк: Player limits, merchant policy, ordinary surplus producer и durable plan encoder/decoder принимают20. Native SELL/BUY packets ограничиваются actual Player limits, скрытого packet cap8 нет. Client20/инвентари/warehouse остаются OWNER_QA_PENDING, не GUI PASS.

Сверка 38 правил: SOURCE_VALUE и EFFECTIVE_VALUE ниже совпали в source verifier и copied075 game; Player values дополнительно загружены настоящим PlayerConfig,11 Servitor — настоящим ConfigReader. GameServer с этими значениями прошёл Main/restart. Это не клиентская QA. Текущий `config/OwnerProfile38.tsv` редактируемый; его нынешний SHA256 `c289b3ac0051ccda5544967988aa0a1929abd1b0d18c2662117fd3f0ce2aa91c` информационный. Исторический input SHA остаётся неизменным.

| № | Правило | SOURCE_VALUE | EFFECTIVE_VALUE | STATUS |
|---:|---|---|---|---|
|1|PLAYER AltWeightLimit|100|100|PASS|
|2|PLAYER MaxPvtStoreSellSlotsDwarf|20|20|PASS|
|3|PLAYER MaxPvtStoreSellSlotsOther|20|20|PASS|
|4|PLAYER MaxPvtStoreBuySlotsDwarf|20|20|PASS|
|5|PLAYER MaxPvtStoreBuySlotsOther|20|20|PASS|
|6|PLAYER MaximumSlotsForNoDwarf|200|200|PASS|
|7|PLAYER MaximumSlotsForDwarf|250|250|PASS|
|8|PLAYER MaximumSlotsForGMPlayer|250|250|PASS|
|9|PLAYER MaximumSlotsForQuestItems|200|200|PASS|
|10|PLAYER MaximumWarehouseSlotsForDwarf|250|250|PASS|
|11|PLAYER MaximumWarehouseSlotsForNoDwarf|250|250|PASS|
|12|PLAYER MaximumWarehouseSlotsForClan|250|250|PASS|
|13|PLAYER MaximumFreightSlots|200|200|PASS|
|14|PLAYER DwarfRecipeLimit|100|100|PASS|
|15|PLAYER CommonRecipeLimit|100|100|PASS|
|16|ENCHANT ARMOR/FIGHTER/MAGE.current|0-2:100;3-9:85;10-15:70;16-24:66;25-65535:0|то же|PASS|
|17|ENCHANT FULL_ARMOR.current|0-3:100;4-9:85;10-15:70;16-24:66;25-65535:0|то же|PASS|
|18|ENCHANT standard previously16|25 (36 обычных scrolls)|25|PASS|
|19|ENCHANT DisableOverEnchanting|True|True|PASS|
|20|ENCHANT OverEnchantProtection|True|True|PASS|
|21|SERVITOR EnableServitorShareBonusTuning|True|True|PASS|
|22|SERVITOR PAtkBonusMultiplier|2.0|2.0|PASS|
|23|SERVITOR PDefBonusMultiplier|2.0|2.0|PASS|
|24|SERVITOR MAtkBonusMultiplier|2.0|2.0|PASS|
|25|SERVITOR MDefBonusMultiplier|2.0|2.0|PASS|
|26|SERVITOR MaxHpBonusMultiplier|2.0|2.0|PASS|
|27|SERVITOR MaxMpBonusMultiplier|2.0|2.0|PASS|
|28|SERVITOR MaxRecoverableHpBonusMultiplier|2.0|2.0|PASS|
|29|SERVITOR MaxRecoverableMpBonusMultiplier|2.0|2.0|PASS|
|30|SERVITOR CritRateBonusMultiplier|2.0|2.0|PASS|
|31|SERVITOR PAtkSpdBonusMultiplier|2.0|2.0|PASS|
|32|SERVITOR MAtkSpdBonusMultiplier|2.0|2.0|PASS|
|33|STORE ShopMinRangeFromPlayer|50|50|PASS|
|34|STORE ShopMinRangeFromNpc|100|100|PASS|
|35|STORE MerchantLinesPerStore|20|20 policy/plan/producer; native20 client pending|PASS_SOURCE|
|36|CUBIC EnablePersonalWondrousCubicCooldown|False|False|PASS|
|37|CUBIC PersonalWondrousCubicCooldownMinutes|60|60|PASS|
|38|QUEST all keys|PRESERVE_EXISTING_VALUES|10 keys, normalized SHA85198821e051ef7e1720628df4e05f951c49e424b774ab73fa6c0fbce6c76351|PASS|

Воспроизводимые проверки checkpoint A/B (JDK25/Ant1.10.17):

- `ant -q -f L2J_Mobius_CT_2.6_HighFive/build.xml compile-tests`: exit0;5 прежних предупреждений. Второй запуск `owner-profile-validate compile-tests`: exit0,38/38 и XML/XSD до javac.
- Existing `PhantomTestLauncher owner-world075 750075`: сначала RED3/3, после исправления GREEN3/3; real native enchant chance/protection и PlayerConfig, per-stat/OFF/other-skill parity.
- Existing `PhantomTestLauncher post001-enchant-cost 1001`:4/4 exit0; прежний загруженный chance expectation66 заменён85, синтетические cost tests66 сохранены.
- Launcher classpath из `dist/game`: `../../../build/bin;../../../build/phantom-test/bin;../../../build/phantom-test/resources;../libs/*`, property `phantom.module.root=<absolute-module>`, reports `.phantom-local/task075/checks`.
- `javac -encoding UTF-8 -cp ../../../build/bin;../libs/* -d ../../../build/phantom-test/bin data/scripts/handlers/skill/effects/ServitorShare.java` из `dist/game`: exit0.
- JDK-only `OwnerProfileVerifier <gameRoot>` на SOURCE и copied public profile: exit0. Copied AltWeight100→1 без изменения TSV: exit1; возврат100: exit0. Ранняя проверка hash-lock заменена mutable mechanism по уточнению владельца; новые positive/negative результаты ниже. Никаких source mutations verifier не делает.
- Независимый read-only review A/B: важных/критичных ошибок не найдено.

Git разрешён TASK075 AUTHORITY: read-only baseline/scope и exact staging/commit/обычный push. Выполнены `git symbolic-ref --short HEAD`, `git rev-parse HEAD`, `git status --short`, `git remote -v`, `git ls-remote origin refs/heads/feature/phantom-world`, exact-path `git diff --stat -- <A/B paths>`. Первый сетевой ls-remote отказал в sandbox, повтор с разрешённой сетью exit0. Полный scope diff/check/staging/commit/push записываются при завершении.

Commit/push A/B: `1b9200d29d6af3e3e7ceb2bfdffab0051e8136da`; normal non-force push exit0; live remote equality подтверждена. `git add --pathspec-from-file=.phantom-local/task075/scope075.txt`, cached exact-scope diff/check, `git commit -m 'phantom(task-075): persist owner profile and enchant share tuning'`, `git push origin HEAD:refs/heads/feature/phantom-world`;33 exact paths. Первый sandbox add отказал на index.lock, разрешённый повтор выполнился.

- mojibake-маркеры в изменённых файлах проверены:33 scoped UTF-8 files, весь заданный набор,0 matches.
- escaped Cyrillic в изменённых файлах проверены:33 files, все6regex,0 matches.

Precommit: plain `git diff --check -- <exact scope>` счёл сохранённые CRLF trailing whitespace; `git -c core.whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol diff --check -- <exact scope>` exit0. Это per-command трактовка CR, без изменения Git config и без массовой смены EOL. Полный A/B diff прочитан, новые файлы отдельно reviewed.

Аудит C: `037-full-quest-rates-audit.md` явно сохранял stock rates, conditions и raw chance. Current Rates.ini: QuestItemDropAmountMultiplier/RateQuestRewardXP/SP/Adena/Reward/Potion/Scroll/Recipe/Material=1, UseQuestRewardMultipliers=False; premium questXP/SP=1. Canonical Quest helpers применяют objective amount один раз; giveQuestItemsUpTo clamp и singleton giveItemsWithoutQuestRate остаются; terminal reward categories и fixed exceptions не заменялись общим drop rate. Отдельного global quest-chance multiplier нет. Неизвестные прежние untracked настройки владельца не восстановлены догадкой.

Q00298 SHA `a1707c4444815589b00e6427c3d7e7ae7e0247219ea7fa95011600c47459c0a3` совпал с Goal037 inventory/rate-site pins: minimum25, верхнего level check нет, cond3→addExpAndSp(0,42000)→exitQuest(true,true), repeat state очищается. Нормальный общий SP wallet cap2,000,000,000 сохранён; это не quest-level/repeat cap. Q00186 SHA `bbcc36d59926089a0c12af3178b0c6a55f5deefe0e4d742109d0af60aefe1cbd` тоже совпал: min41, stock EXP/SP<47 через прежний personal QoL seam, one-time exit false. Это другой контракт, правки не нужны.

Existing launcher `quest-rates-goal037-static 37003700`: exit1,1/2. Negative controls PASS, full AST manifest stale начиная с Q00234_FatesWhisper (expected2aeb2c27…, current4ab288e6…, call lines240→242). Это не изменение075 и не доказательство регрессии quest rates. Исторический snapshot массово не переписан ради GREEN. Реальный высокоуровневый Q00298 replay в075 NOT_RUN; source-preservation подтверждён, полный native C gate не заявлен.

E focused RED0/3→GREEN3/3; полный существующий launcher `population-ecology-goal033 33003300`: exit0,36/36. Первоначальная компиляция двух изменённых файлов в отдельном temporary output, затем повтор после обычного Ant compile-tests. Первое beginStop фиксирует минуту/live owners; finishStop после worker drain выполняет bounded calendar-only fresh CAS, не поглощая downtime. Clock rollback/noop, retry, repeated stop, повторный restart/idempotency и pending/orphan ownership проверены. Pending history остаётся за прежним owner, без бесплатных наград/сделок. Реальный Main/restart приведён ниже.

Product D source: обычный producer открывает до20 фактических surplus MATERIAL позиций; count≤8, половины stock и canonical LootDispositionPolicy count выше recipe reserve. Изученные recipe ingredients, key parts, стартовые субсидии, оборудование, quest/protected/needed/reserved items исключены. BUY остаётся из actual need+funds; общий exposure проверяется. MANUFACTURE — только известные native recipe+craft skill+positive canonical fee, без выдачи ингредиентов/skills/денег. Окно устанавливается из population admission около ближайшей границы/отдыха; точный unchanged farm.background root может ждать под существующим store fence, остальные ACTIVE roots не обходятся. Только Geo/peace/town в текущей позиции и зазор60; путешествие/телепорт в Гиран не добавлялись. Native Player остаётся под materialization lease; timestamp снимается при штатном close. Stock bulk offline save исключает headless outbound actor, как прежний realtime PrivateStoreService, предотвращая второй native restore owner. При штатном STOP лавка закрывается обычным lifecycle; автоматический reopen того же listing после restart не реализован.

Product F source: `MarketSearchBoard.java`, `HomeBoard.java`, `MasterHandler.java`. Native Alt+B поиск SELL/BUY/CRAFT, category/grade/name, qty/unit/total Adena и seller location; CRAFT fee за попытку, output/chance рецепта отдельно. Ограничения2048 owners/4096 rows/100 per-store/12 результатов, только по запросу, без нового индекса или scheduler. Radar marker проверяет текущую точную позицию/owner/count/price/recipe повторно; teleport/trade API отсутствуют. ItemAuction32320–32322 и World index074 не изменялись. Actual scripts + MasterHandler + ServitorShare отдельно скомпилированы javac, exit0; GUI pending.

Fresh product review нашёл2 Important: queued-craft close race и отсутствие recipe reserve в retail surplus. Исправлены одним bounded проходом: закрытие переиспользует SELL→BUY→manufacture monitors и повторный native busy check; retail переиспользует read-only LootDispositionPolicy. Два новых DB-free source-contract regression в прежнем owner-world075 runner: RED2 failures→GREEN5/5 всей suite. Это проверка архитектурных ограничений, не native replay гонки. Существующий native market suite дополнен2-line subset/window/reserve assertions и корректными isolated reserve+16 fixtures; скомпилирован, DB execution пока NOT_RUN. Эти fixture-ресурсы не выдаются за earned075 и не попадают в Main.

Общая компиляция после D/E/F: `ant -q -f L2J_Mobius_CT_2.6_HighFive/build.xml compile-tests` exit0;5 прежних warnings. Из её classes повторены owner-world0753/3 и ecology36/36; после review source-contract additions owner-world0755/5. Старые generated source dist JAR перед заменой побайтово сохранены вне checkout в temporary075 backup, SHA проверены.

Product commit `f6668d000c796f556e28305a981002624cb9dd0c`; единственная `ant -q -f L2J_Mobius_CT_2.6_HighFive/build.xml -Dbuild=<repo>/.phantom-local/task075/build jar`: exit0,18s. GameServer `BF27EECED220BA043698277DB9DF7674D1636E925D5671562BE02598B6D24796`, LoginServer `1F20A9E7177B7BBA0392AB7D72322C326E9CF2E2A6E0B7C101E4EC0BB0BE5953`.

Одна новая OWN075 local runtime/schema `l2jmobiush5_phantom_release_075`, UUID `fcbef5b4-8828-4c08-b1e7-bb9241ba2bf2`, ports2255/7955/9255, native population4/active2/cap4, prepared merchantsFalse, stock heaps. Canonical FreshLocalPlayDatabaseGuard/DatabaseInstaller/GameServerRegister/Runtime.ps1 переиспользованы; одна local-only authority INSERT075 поверх неизменённой economy071 migration, без новых DDL/source SQL. Source configs+SQL fingerprint `271433995DED235E010F24238E2A482D2B4C1743C286B21DD35A587755ED2E54` до/после совпал; private allowlists/Network.ini не изменены. Geo203 физически скопированы/проверены. Первый Prepare дошёл до готовой копии, затем отказал: reused16KiB reader не принимал17,205-byte public PhantomPlayers.ini. Local reader075 ограничен32KiB и четырьмя public paths; ни конфиги073, ни secrets reader не менялись. Existing copy/UUID использованы дальше, без второй среды/повторной установки.

Первый Start НЕ PASS: Main не открыл7955 за180s; Login16076 работал, Game32840 завершил main при живых stock workers. Один read-only jcmd dump; затем generic stock STOP обоих PID, DB0. До и после отказа snapshot identities/profiles/items/receipts/operations/offline=0. Post-STOP finalized log выявил первопричину: ScriptExecutor.java:60–66 компилирует scripts с source/target1.8, а новый broker использовал Java25 syntax. Это ошибка исходной standalone проверки075. Весь batch handlers не загрузился, из-за чего последующий supported owner class.warrior-q401/profession fail-closed; этот guard НЕ отключался.

Bounded correction `99075e0a78f5e32e9e254742e729ff6487c481f9` только MarketSearchBoard.java и этот отчёт: explicit types, classic switch и immutable Listing class,8-field equality сохранена. Native compiler flags RED exit1→GREEN exit0 для MarketSearchBoard/HomeBoard/MasterHandler/ServitorShare; javap major52/minor0. Script SHA `fb5e5f76cc2b1c601c27db8823ea240dcace9dc49b8b9aec009fdbce7edbda81`. Core gameplay Java/JAR не менялись. Перед заменой одного owned075 data script и metadata сохранены byte copies вне checkout с SHA; data commit указан отдельно от JAR code commit f666. SameDB/UUID, без reset/reseed. Первая atomic metadata Replace с null backup была отклонена .NET; validated temp заменён File.Move(overwrite), запуск до завершения guards не выполнялся.

Corrected native Main: Game PID16056/Login18808; Phantom World стартовал 24.10.2026 04:09:22 UTC, штатный STOP начат04:11:26, примерно124s. SameDB restart: Game31640/Login1560, native старт04:14:41, штатный STOP04:17:19, примерно158s. RUNNING отдельно зафиксирован в04:10:03 и04:16:47. Одна schema/UUID/JAR, без reset/reseed и без выдачи fixture inventory/recipe/skills/Adena. Второй STOP завершён до работ по уточнению HARD A. Новых игровых сценариев после него нет.

Read-only snapshots после обоих STOP: DB_OTHER_CONNECTIONS=0; identities4→4 и profiles4→4 с одинаковыми SHA (`165602ca…e55194`, `01f17135…649f1d`); items29→30, components28→28. Это обычное продолжение жизни, не утверждение о нулевом движении inventory. Actor1 EXP87→174/SP6→14 и умер с HP0; actor2 level2→3,EXP87→367/SP6→14. Два других остались level1. Текущий historical cursor actor2 сохранил interval29871007..29871015 и продвинулся29871008→29871012, ordinal1→5. Actor1/3 получили COMPLETE по bounded новым interval; actor4 всё ещё RUNNING с прежним cursor29877345/ordinal0. Pending lawful context не обходился. Economy operations/native receipts/owner orders/offline owners/items остались0; фиктивные downtime-продажи не засчитывались.

В этом коротком Main нет подтверждённого earned native SELL/BUY/MANUFACTURE, полного возврата магазина в жизнь, auto-reopen того же listing и клиентского spacing/marker QA. Для D это конкретный оставшийся долг; для E подтверждена bounded restart-continuation, но не полнота всех causal histories. Наличие кода и отсутствие дублей identity не заменяют эти доказательства. Приёмка D/E/F остаётся в полном исходном scope TASK075; уточнение HARD A её не сокращает.

Финальная проверка остановки: marked OWN075 JVM=0, listeners2255/7955/9255=0; read-only snapshot DB0. Generic Status показывает `STALE_RECORD`,pid0 для обеих ролей, поскольку dead PID receipts сохранены; это не RUNNING и не буквальная запись STOPPED. Первый повтор process/port чтения был запрещён sandbox; подтверждённый повтор с нужным доступом exit0. OWN074 и старые candidates не изменялись.

Уточнение HARD A: `PROFILE_HASH`/`PRESERVED_HASH` удалены. Текущий TSV задаёт ожидаемые значения, Java задаёт ровно38 известных identity/path/type и проверяемые runtime bounds. Проверка читает фактические INI/XML, проверяет XSD, типы, finite numbers, no duplicates/path escape, zero chance выше ordinary cap. Печатные SHA информационные. Ни изменение только INI, ни одинаковое недопустимое значение в INI+TSV не становится PASS. Settings bounds не объявляются гарантией безопасности любых экстремальных игровых значений.

Quest baseline остаётся явным tracked `quest.keys`/`quest.sha256` в `OwnerProfilePreserved.properties`. Read-only `--quest-hash` только печатает строки для ручной проверки; verifier не обновляет baseline автоматически. Текущие SOURCE hashes после уточнения неизменны: profile `c289b3ac0051ccda5544967988aa0a1929abd1b0d18c2662117fd3f0ce2aa91c`; preserved bytes `e0c986eb011d1657f899dbe8013aad4d986b8cea70b46ac21fbc6ab2ffbec609`; Rates.ini `0df75545cd4f7b331f96a49f0d683221253e757e60a0c7976104e5f45225ee39`. Исторические11 manifest entries, исходный TSV и SHA не меняются. Короткая инструкция ручного изменения/Codex: [OWNER_PROFILE.md](../OWNER_PROFILE.md).

Узкие реальные copied CLI checks механизма: baseline и SOURCE38/38 exit0; парное MAtk2→3 exit0; только INI exit1; парные NaN/−1/101 exit1 каждый; quest-rate-only exit1; read-only quest digest exit0 и осознанное обновление copied baseline exit0; duplicate rule/unknown path/XML NaN exit1; согласованный enchant cap24 с TSV/XML exit0. Это только публичные копии, SOURCE значения не менялись. Независимый read-only review mutable mechanism: Important/Critical не найдено. Existing owner-world075 остаётся5/5; loaded expectations теперь берутся из текущего TSV, engine defaults/parity/overcap checks сохранены.

Preserved special/support пределы тоже управляются baseline: обязательны4 именных ключа, остальные только `scroll.preserved.N`/`support.preserved.N`,N1..65535 по XSD. Exact ID→max maps сравниваются с XML, duplicates и неизвестные имена отклоняются. Copy-only special XML6→7 без baseline:exit1; paired XML+baseline7:exit0. Support XML9→7 отдельно:exit1, paired:exit0; baseline limit0:exit1. Текущие XML/properties остались байтово прежними. Проверяемые runtime ceilings не маскируются как freely editable source values.

Финальная сборка уточнения A: code commit `44c796d0380ddfe57ac66abdfff41d855344e18e`; `ant -q -f L2J_Mobius_CT_2.6_HighFive/build.xml compile-tests` exit0,26s,5 прежних warnings; повтор existing `owner-world075 750075` exit0,5/5. Повторный обычный `jar` с тем же owned build root exit0,16s нужен только для обновлённого verifier; игровые проверки/сцены не повторялись. Финальные GameServer SHA `1A10A548437C5CBE87D86C079E3A015115392C32173728534458398C07B1928C`, LoginServer SHA `8CA324C27F7201CC0C52AD77FCD5A48316292650ECACA35D1494504C6AF31170`. CLI из `.phantom-local/task075/build/dist/libs/GameServer.jar` на SOURCE и stopped OWN075 game:exit0,38/38. Первый CLI вызов указал неверный путь без `dist/libs`, исправлен без пересборки; sandbox read copied runtime был AccessDenied, authorized read-only повтор прошёл. Новые JAR находятся в build output и generated source dist/libs; stopped runtime не обновлялся и сохраняет JAR f666/native evidence99075, а не выдаётся за запущенный44c796.

Публикация кода: обычный `git push origin HEAD:refs/heads/feature/phantom-world` exit0, затем `git ls-remote origin refs/heads/feature/phantom-world` подтвердил `44c796d0380ddfe57ac66abdfff41d855344e18e`. В неё входят product f666, Java8 correction99075 и mutable-profile44c796. Последующий commit содержит только окончание этого единого отчёта и публикуется тем же non-force маршрутом; его фактический SHA и remote equality фиксируются в финальном ответе исполнения.

Git использовался по прямому разрешению TASK075 и владельца. Bounded inspection: `git symbolic-ref --short HEAD`, `git rev-parse HEAD`, `git status --short`, `git remote -v`, `git ls-remote origin refs/heads/feature/phantom-world`; `git diff -- <exact scoped paths>`, `git diff --stat -- <exact scoped paths>`, `git diff --stat 8ac113257bea0cd3ea241b708889b42b42b5b961 HEAD -- <44 scoped paths>`, `git diff --cached --stat -- <exact staged paths>`, per-command CRLF-aware `git -c core.whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol diff [--cached] --check -- <exact paths>`. Staging: `git add --pathspec-from-file=.phantom-local/task075/scope075.txt` для раннего allowlist; затем только явные имена трёх файлов уточнения и отдельного отчёта. Commit messages: `phantom(task-075): persist owner profile and enchant share tuning`, `phantom(task-075): add rest stores downtime settlement and native market search`, `phantom(task-075): keep native market script compatible with source8`, `phantom(task-075): allow explicit versioned owner profile updates`, `phantom(task-075): record final bounded acceptance and stop evidence`. Для уточнения применён `git commit --only -m <message> -- <OwnerProfileVerifier.java, PhantomOwnerWorld075Suite.java, OWNER_PROFILE.md exact paths>`; для финального отчёта — тот же `--only` с единственным report path. Ни branch/worktree/reset/restore/stash/clean/rebase/force, ни изменение Git config не выполнялись.

Финальный scope:44 tracked paths от baseline, из них12 файлов исходного пакета075 без изменения байтов; последний commit меняет только этот отчёт. Чужие untracked `.phantom-local/`,068V/073 сохранены. TASK075 manifest11/11 повторно совпал; секретные source configs/allowlists, исторические candidates/DB, master и чужие worktrees не менялись. Final exact diff check exit0. Автоматических R2/076, следующей задачи или расписания нет. Долг PARTIAL074 остаётся самостоятельным.

- mojibake-маркеры в изменённых файлах проверены:44 scoped files, весь заданный набор,0 matches.
- escaped Cyrillic в изменённых файлах проверены:44 scoped files, все6 regex,0 matches.
