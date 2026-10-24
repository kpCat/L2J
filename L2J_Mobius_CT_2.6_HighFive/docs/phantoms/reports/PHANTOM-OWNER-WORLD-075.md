# PHANTOM-OWNER-WORLD-075

Исполнение продолжается. Это один отчёт TASK075; ранний commit сохраняет A/B перед D/E.

Baseline: local и live origin feature/phantom-world = `8ac113257bea0cd3ea241b708889b42b42b5b961`.
Свежий OWN074 Status обеих сред: Game/Login STOPPED, DB connections=0; JVM0 и шесть listeners0.
TASK074 опубликована и закрыта BLOCKED/PARTIAL, не PASS. Её долги B/C/D и universal economy сохраняются отдельно; R2/076 не создаётся.
Начальный dirt: `.phantom-local/`, packages068V/073/075; чужие файлы не изменяются и не staged. Пакет075 публикуется без изменения байтов.

| Lane | Статус текущего checkpoint | Граница доказательства |
|---|---|---|
| A | SOURCE/COPY PASS, client QA pending | 38/38, XML/XSD, Ant gate, copied public profile GREEN/negative RED/GREEN; полного нового runtime candidate ещё нет |
| B | focused PASS | ordinary max25, 100/85/70/66, bonus100/default65535 rejected at25; special caps unchanged; 11 независимых Servitor коэффициентов |
| C | аудит без регрессии | Goal037 сохраняется; focused static gate ещё не запускался |
| D | NOT_RUN | ordinary ACTIVE stall ещё не shift offline; prepared merchants не подменяют обычных |
| E | NOT_RUN | существующий bounded historical cursor; controlled STOP/gap/restart не выполнен |
| F | NOT_RUN | native Radar существует; ItemAuction не меняется |

Решение A/B: штатные ConfigReader/ConfigLoader, EnchantScroll, EnchantItemGroupsData, native stores и Ant/copy pipeline. Новых библиотек/движков нет. Бounded exception: несколько config/code/test/build файлов одной задачи необходимы для контроля owner profile; отдельный cleanup/Studio/UI не включён. Worktree не создавался. Secret Database.ini/Network.ini/allowlists и старые candidates/DB не изменялись.

Servitor1557: исходные XML коэффициенты сохранены. Девять полей идут через share-map; maxRecoverableHp/Mp — две существующие native Mul-функции, не выдуманные share-поля. Каждая положительная доля масштабируется `1+(stock-1)*multiplier`; OFF/1.0 сохраняют stock. Родные onStart/onExit/add/remove и тип Servitor сохранены; pets и остальные skills не получают новый множитель. Native клиентская проверка полного lifecycle пока NOT_RUN.

20 строк: Player limits, merchant policy и durable plan encoder/decoder принимают20. Native SELL/BUY packets ограничиваются actual Player limits, скрытого packet cap8 нет. Ordinary producer пока формирует одну позицию; это отдельная D-работа. Client20/инвентари/warehouse остаются OWNER_QA_PENDING, не GUI PASS.

Сверка 38 правил: SOURCE_VALUE и EFFECTIVE_VALUE ниже совпали в source verifier и отдельной публичной copy, не в запущенном GameServer. Player values дополнительно загружены настоящим PlayerConfig;11 Servitor — настоящим ConfigReader. Путь каждого правила byte-pinned в `config/OwnerProfile38.tsv`, SHA256 `c289b3ac0051ccda5544967988aa0a1929abd1b0d18c2662117fd3f0ce2aa91c`.

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
|35|STORE MerchantLinesPerStore|20|20 policy/plan, ordinary producer pending|PARTIAL_D|
|36|CUBIC EnablePersonalWondrousCubicCooldown|False|False|PASS|
|37|CUBIC PersonalWondrousCubicCooldownMinutes|60|60|PASS|
|38|QUEST all keys|PRESERVE_EXISTING_VALUES|10 keys, normalized SHA85198821e051ef7e1720628df4e05f951c49e424b774ab73fa6c0fbce6c76351|PASS|

Воспроизводимые проверки checkpoint A/B (JDK25/Ant1.10.17):

- `ant -q -f L2J_Mobius_CT_2.6_HighFive/build.xml compile-tests`: exit0;5 прежних предупреждений. Второй запуск `owner-profile-validate compile-tests`: exit0,38/38 и XML/XSD до javac.
- Existing `PhantomTestLauncher owner-world075 750075`: сначала RED3/3, после исправления GREEN3/3; real native enchant chance/protection и PlayerConfig, per-stat/OFF/other-skill parity.
- Existing `PhantomTestLauncher post001-enchant-cost 1001`:4/4 exit0; прежний загруженный chance expectation66 заменён85, синтетические cost tests66 сохранены.
- Launcher classpath из `dist/game`: `../../../build/bin;../../../build/phantom-test/bin;../../../build/phantom-test/resources;../libs/*`, property `phantom.module.root=<absolute-module>`, reports `.phantom-local/task075/checks`.
- `javac -encoding UTF-8 -cp ../../../build/bin;../libs/* -d ../../../build/phantom-test/bin data/scripts/handlers/skill/effects/ServitorShare.java` из `dist/game`: exit0.
- JDK-only `OwnerProfileVerifier <gameRoot>` на SOURCE и copied public profile: exit0. Copied AltWeight100→1: exit1; возврат100: exit0. Tampered profile SHA: exit1. Никаких source mutations verifier не делает.
- Независимый read-only review A/B: важных/критичных ошибок не найдено.

Git разрешён TASK075 AUTHORITY: read-only baseline/scope и exact staging/commit/обычный push. Выполнены `git symbolic-ref --short HEAD`, `git rev-parse HEAD`, `git status --short`, `git remote -v`, `git ls-remote origin refs/heads/feature/phantom-world`, exact-path `git diff --stat -- <A/B paths>`. Первый сетевой ls-remote отказал в sandbox, повтор с разрешённой сетью exit0. Полный scope diff/check/staging/commit/push записываются при завершении.

Commit/push A/B: `1b9200d29d6af3e3e7ceb2bfdffab0051e8136da`; normal non-force push exit0; live remote equality подтверждена. `git add --pathspec-from-file=.phantom-local/task075/scope075.txt`, cached exact-scope diff/check, `git commit -m 'phantom(task-075): persist owner profile and enchant share tuning'`, `git push origin HEAD:refs/heads/feature/phantom-world`;33 exact paths. Первый sandbox add отказал на index.lock, разрешённый повтор выполнился. Обычный product JAR build и финальная матрица pending.

- mojibake-маркеры в изменённых файлах проверены:33 scoped UTF-8 files, весь заданный набор,0 matches.
- escaped Cyrillic в изменённых файлах проверены:33 files, все6regex,0 matches.

Precommit: plain `git diff --check -- <exact scope>` счёл сохранённые CRLF trailing whitespace; `git -c core.whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol diff --check -- <exact scope>` exit0. Это per-command трактовка CR, без изменения Git config и без массовой смены EOL. Полный A/B diff прочитан, новые файлы отдельно reviewed.

Аудит C: `037-full-quest-rates-audit.md` явно сохранял stock rates, conditions и raw chance. Current Rates.ini: QuestItemDropAmountMultiplier/RateQuestRewardXP/SP/Adena/Reward/Potion/Scroll/Recipe/Material=1, UseQuestRewardMultipliers=False; premium questXP/SP=1. Canonical Quest helpers применяют objective amount один раз; giveQuestItemsUpTo clamp и singleton giveItemsWithoutQuestRate остаются; terminal reward categories и fixed exceptions не заменялись общим drop rate. Отдельного global quest-chance multiplier нет. Неизвестные прежние untracked настройки владельца не восстановлены догадкой.

Q00298 SHA `a1707c4444815589b00e6427c3d7e7ae7e0247219ea7fa95011600c47459c0a3` совпал с Goal037 inventory/rate-site pins: minimum25, верхнего level check нет, cond3→addExpAndSp(0,42000)→exitQuest(true,true), repeat state очищается. Нормальный общий SP wallet cap2,000,000,000 сохранён; это не quest-level/repeat cap. Q00186 SHA `bbcc36d59926089a0c12af3178b0c6a55f5deefe0e4d742109d0af60aefe1cbd` тоже совпал: min41, stock EXP/SP<47 через прежний personal QoL seam, one-time exit false. Это другой контракт, правки не нужны.

Existing launcher `quest-rates-goal037-static 37003700`: exit1,1/2. Negative controls PASS, full AST manifest stale начиная с Q00234_FatesWhisper (expected2aeb2c27…, current4ab288e6…, call lines240→242). Это не изменение075 и не доказательство регрессии quest rates. Исторический snapshot массово не переписан ради GREEN. Реальный высокоуровневый Q00298 replay в075 NOT_RUN; source-preservation подтверждён, полный native C gate не заявлен.

E focused RED0/3→GREEN3/3; полный существующий launcher `population-ecology-goal033 33003300`: exit0,36/36. Компиляция двух изменённых файлов в отдельном temporary output, исходный build/bin не подменялся. Первое beginStop фиксирует минуту/live owners; finishStop после worker drain выполняет bounded calendar-only fresh CAS, не поглощая downtime. Clock rollback/noop, retry, repeated stop, повторный restart/idempotency и pending/orphan ownership проверены. Pending history остаётся за прежним owner, без бесплатных наград/сделок. Реальный Main/restart ещё pending.

Product D source: обычный producer открывает до20 фактических surplus MATERIAL позиций; count≤8, половины stock и canonical LootDispositionPolicy count выше recipe reserve. Изученные recipe ingredients, key parts, стартовые субсидии, оборудование, quest/protected/needed/reserved items исключены. BUY остаётся из actual need+funds; общий exposure проверяется. MANUFACTURE — только известные native recipe+craft skill+positive canonical fee, без выдачи ингредиентов/skills/денег. Окно устанавливается из population admission около ближайшей границы/отдыха; точный unchanged farm.background root может ждать под существующим store fence, остальные ACTIVE roots не обходятся. Только Geo/peace/town в текущей позиции и зазор60; путешествие/телепорт в Гиран не добавлялись. Native Player остаётся под materialization lease; timestamp снимается при штатном close. Stock bulk offline save исключает headless outbound actor, как прежний realtime PrivateStoreService, предотвращая второй native restore owner. При штатном STOP лавка закрывается обычным lifecycle; автоматический reopen того же listing после restart не реализован.

Product F source: `MarketSearchBoard.java`, `HomeBoard.java`, `MasterHandler.java`. Native Alt+B поиск SELL/BUY/CRAFT, category/grade/name, qty/unit/total Adena и seller location; CRAFT fee за попытку, output/chance рецепта отдельно. Ограничения2048 owners/4096 rows/100 per-store/12 результатов, только по запросу, без нового индекса или scheduler. Radar marker проверяет текущую точную позицию/owner/count/price/recipe повторно; teleport/trade API отсутствуют. ItemAuction32320–32322 и World index074 не изменялись. Actual scripts + MasterHandler + ServitorShare отдельно скомпилированы javac, exit0; GUI pending.

Fresh product review нашёл2 Important: queued-craft close race и отсутствие recipe reserve в retail surplus. Исправлены одним bounded проходом: закрытие переиспользует SELL→BUY→manufacture monitors и повторный native busy check; retail переиспользует read-only LootDispositionPolicy. Два новых DB-free source-contract regression в прежнем owner-world075 runner: RED2 failures→GREEN5/5 всей suite. Это проверка архитектурных ограничений, не native replay гонки. Существующий native market suite дополнен2-line subset/window/reserve assertions и корректными isolated reserve+16 fixtures; скомпилирован, DB execution пока NOT_RUN. Эти fixture-ресурсы не выдаются за earned075 и не попадают в Main.

Общая компиляция после D/E/F: `ant -q -f L2J_Mobius_CT_2.6_HighFive/build.xml compile-tests` exit0;5 прежних warnings. Из её classes повторены owner-world0753/3 и ecology36/36; после review source-contract additions owner-world0755/5. Одна обычная product JAR сборка и native smoke ещё pending. Старые source dist JAR перед будущей заменой побайтово сохранены вне checkout в temporary075 backup, SHA проверены.
