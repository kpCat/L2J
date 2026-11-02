# PHANTOM-AUTONOMOUS-PVE-PROGRESSION-115

Результат: **NATIVE_VERIFIED A–E; FOCUSED_VERIFIED 213/213; source review P1=0/P2=0**. IMPLEMENTED — source в enclosing commit; точные SHA/non-force push receipt приведены в финальном ответе Codex. Required PARTIAL/BLOCKED/UNKNOWN отсутствуют; OWNER_VISUAL_PENDING остаётся вне scope115.
Дата: 02–03.11.2026. Baseline GitHub: `d493fffa101c388e9d005ad1a904ffa23f28038f`, parent `236b965a116bbc79cccbef78b04c628225a629cb`.
Живой HEAD проверен через GitHub API; перед каждым native start повторно проверяется `ls-remote`.
Работа выполняется из отдельного физического checkout от этого HEAD. Основной checkout с WIP109 не обновлялся.

## Read-first и scope

Полностью прочитаны TASK, ACCEPTANCE, EVIDENCE, GOAL, HANDOFF и SOURCE_MAP пакета TASK115.
Прочитаны root AGENTS.md, модульный Agents.md, CODEX_WORKFLOW_CONTRACT, PHANTOM_CODEX_EFFICIENCY_STANDARD,
TASK_PACKAGE_STANDARD, исторический PHANTOM_CURRENT_STATUS, bounded разделы master plan, отчёт TASK114, root README.md и build.xml.
Модульный README.md и AGENTS.md выше исходного Git root не найдены. Исторические абсолютные пути и старые credentials не использованы.
Обязательный пакет/контракты расширили обычный initial READ_SET; больших source-файлов целиком не читали.
Расширения чтения были связаны с native observation, maintenance starvation, receipt/CAS/restart и существующими тестами.
Аналоги: maintenance proposal/executor, существующие maintenance advance/rebind и supply071/commerce-receipt checks.
Стек сохранён: JDK25, Ant, MariaDB11.4.13, физическая Geo203. Новых зависимостей, schema и action engine нет.

Exact allowlist публикации относительно модуля:

- `java/org/l2jmobius/gameserver/phantoms/commerce/L2jPhantomSupplyJourney.java`;
- `java/org/l2jmobius/gameserver/phantoms/commerce/PhantomInventoryMaintenanceIntent.java`;
- `java/org/l2jmobius/gameserver/phantoms/commerce/PhantomAutonomousSupplyPolicy.java`;
- `java/org/l2jmobius/gameserver/phantoms/background/PhantomVisibleAutoPlay.java`;
- `java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatPolicy.java`;
- `test/java/org/l2jmobius/tests/phantoms/PhantomSupply071Suite.java`;
- `test/java/org/l2jmobius/tests/phantoms/PhantomPveReadiness093Suite.java`;
- этот отчёт.

## Safety / G0

Единственная новая схема: `l2jmobiush5_phantom_test_115`.
Installation ID: `e33757b3-996a-411a-bd99-31c7529f54b6`.
Полный one-click G0 перед созданием: privileged OS receipt, exact PID/FILETIME/binary/config MariaDB,
physical datadir/port/version, SQL grants/metadata visibility, absent owned schema, foreign sessions=0,
source/JAR/input seal, Geo203, physical/disjoint/private ACL и WIP preimages.
OS attestation отдельно не использовалась как SQL-разрешение.
Создание: 121 stock SQL-файл, 203 statement; 18 stock DROP TABLE пропущены. Reset/reseed/adopt отсутствовали.
Сохранены и перед каждым запуском сверяются 630 SHA-256 preimages исходного checkout, включая WIP109 и task package.
MAIN/PLAY, TEST109–114, защищённые release, Geo source, MariaDB service/firewall/UAC и чужие worktrees не изменялись.
Shadow: loopback17777, population24, ACTIVE/materialized cap8, merchant bootstrap OFF; игровые rates и календарь сохранены.
Один собственный LOCALPLAY_TEST_HUMAN создаёт только local demand; его позиция не считается phantom travel.

## Доказанный blocker и production owner

В native2 Mage6 многократно продавал одну нестакаемую Tunic426 и возвращался на поле при оставшихся22 экземплярах.
Проверены COMMITTED receipts разных objectIds: каждый раз +56 Adena, без роста EXP/SP между поездками.
Причина: maintenanceProposal выбирал один SELL, executor после подтверждения отправлялся обратно.
Исправление продолжает свежие допустимые продажи у того же фактического NPC новыми bounded batches.
Native5 выявил неполноту первого варианта: cap41 → возврат на поле → shield18 у того же counter без нового боя.
Final continuation допускает разные itemId; first-item target обновляется согласованно с codec, root/actor/field сохраняются.
Только exact COMMITTED receipt предыдущего завершённого batch разрешает продолжение; prepared/foreign/replayed objects отвергаются.
Root, actor/class/field binding, исходный deadline и lifetime сохраняются; receipt не переписывается.
Фактические продажи остаются в прежнем `_commerce.execute`, quiescent checkpoint и disposal/ownership checks.
Requote ограничен256 inventory entries; новых workers, очередей, hot-path SQL или global scan нет.
Для следующего BUY сохраняется прежний слот: максимум3 новых SELL, общий Spec cap4 не изменён.
Между batches выполняются обычные Decision steps; бесконечный внутренний loop не добавлялся.
Mixed maintenance и широкая рыночная экономика этим изменением не переустраивались.

Native6 выявил следующий supply blocker: level6,17 spiritshots при low-water3,pressure0 и996A>reserve500,
но CP stock0 создавал need и очередной SELL-поход после каждого нового drop. Боевой SP/EXP почти не рос.
В соседнем коде healing-potions уже имели границу18; CP её не имел.
Существующий policy owner теперь задаёт общую optional-potion границу18; ammo/shots независимы.
Уровни18+ и native eligibility/цены/оплата CP не меняются. XML/config schema не менялась.

Native9 Fighter загрузился с законными историческими HP12/183 и восстанавливался настоящим regen.
Фиксированные45s истекли чуть раньше достижения80% HP; expired recovery/debt переносился дальше, farm не начался.
Этот отрицательный epoch имеет PHASE_DEADLINE/overflow и не включается в успешное native доказательство.
Final recovery budget рассчитывается один раз из actual native deficits/Formulas regen, включая один штатный3s tick.
Budget остаётся45–90s, переносится с exact Player/epoch и не сдвигается каждым pulse.
HP/MP, usefulSince, stall debt90s, reward counters, NativeEvidence и generic CreatureStatus не меняются.

Writer audit: action lease/native epoch, root equality/child CAS в saveIntent, отмена/STORE gates,
immutable commerce receipts и native inventory transactions сохранены. XP/SP/skills/equip owners не заменялись.
Negative audit: stale epoch/claim и stop barriers — focused ownership; no-path — navigation; no native expertise/gear — supply071; SP_TOO_LOW проверяется до transaction в L2jProgressionBackend; pending STORE — прежний quiescent/native owner gate. Live fault injection не выполнялся. Generic Player/CreatureStatus не менялись; canonical transient CP regression PASS.
Для skill learning изучены existing PlayerStat native-SP witness и class-learning SQL transaction;
совпадение monitor Player не использовано как самостоятельное доказательство all-writer atomicity.

## Проверки

- Native2: причинный RED — повторные single-item trips с22 оставшимися Tunic и остановкой прироста EXP/SP.
- Focused supply071: RED5/6 → GREEN6/6 для continuation/codec/deadline и receipt/object negatives.
- Независимый read-only review обнаружил P2: заполнение всех4 slots мешало существующему funded BUY.
- Этот P2 получил собственный RED5/6 → GREEN6/6; test проверяет резерв BUY, append четвёртой операции и прежний field/deadline.
- Review delta после исправления: P1=0/P2=0.
- Mixed-surplus: behavioural RED5/6 → GREEN6/6; exact-counter negative сохранён.
- Optional CP: extraction прежнего unconditional допуска → RED6/7; исправление → GREEN7/7.
- Recovery: behavioural combat-core RED68/69 → GREEN69/69; zero/unknown regen и hard cap закрыты тем же focused case.
- Final v6: supply0717/7, commerce-receipt9/9, progression-operations38/38, combat-core69/69, combat-debt0682/2;125/125, exit0.
- Final negatives: existing combat-action-ownership37/37 и navigation-core51/51, exit0 (stale claims/actions, stop barriers, no-path без fake arrival); итого213/213 в7 focused suites, новых tests не добавляли.
- Existing TASK114 DB-free contracts:6 sections PASS и на финальном v6.
- commerce-receipt первый setup отверг seed115115; единственный targeted retry с каноническим140014 прошёл9/9.
- Ant compile-tests/jar: initial/v2/v3/v4/v5/v6 — PASS; последовательно и только в isolated build/dist.
- 093 suite входит в combat-core; ошибочный отдельный mode получил setup exit2, не считается behavioural RED.
- `ant verify` NOT_RUN: включает DB routes вне разрешённой native campaign.

## Native facts — подтверждённая приёмка A–E

| Событие | Проверенные факты |
|---|---|
| Selection | Stock24; physical priority `(archetype,classId,profileId)` выбрала5/class0; caster priority выбрала6/class10; READY/ACTIVE admission сохранён |
| Identity | Fighter5/object268484963; Mage6/object268484972; дополнительные Fighter19/object268485084 |
| NORMAL | Native3 FlightRecorder содержит точные NORMAL/WORLD success для5,6,19 с разными owner epochs |
| Contexts | Fighter: NPC20545, `population.farming.human-fighter.20545`; Mage: NPC20481, `population.farming.human-mystic.20481`; реальные Geo WALK |
| LEVEL | Native1 Mage: level3/609EXP → level4/1974EXP; counters +1365EXP/+130SP,4 kills/rewards,26 pickups |
| Physical native | Native2 Fighter5: 1 kill/reward, +70EXP/+6SP,6 loot events; epoch442308039850100, clean proof |
| Earlier LEVEL | Native4 Mage: level5/3864EXP/380SP → level6/6384EXP/620SP;6 native kills/rewards,+2520EXP/+240SP,50 loot events |
| Paid gear | Mage: BUY60 spiritshots2400A, BUY item7/object268536767 за678A, item426/object268549295 за129A; далее rewarded PvE |
| Further gear | Mage owned item462/object268555287; затем BUY item428/object268487629 за2150A. Fighter BUY item1/object268487171 за678A; native paperdoll подтверждён |
| Causal GREEN | Native3: stock42620→1, Adena2217→3281 (=19×56) у одного counter; затем5 rewarded kills/+525EXP/+50SP, clean proof |
| Earned career | Native8: level6/11004EXP/1060SP → level7/11319EXP/1090SP за3 native kills/rewards,+315EXP/+30SP |
| Paid skill | 21:12:18→21:12:28 UTC:1177:1→2,SP1090→850,EXP11319 и kills3 неизменны; Trainer30035/object268463454,epoch451402557941700 |
| Further lessons | Wind Strike3,Ice Bolt1→2: ещё3×240SP; затем при earned170SP Heal1011:1 за160; autoGet1320 не считается оплатой |
| Replan/farm | Starter20481/level1 → generated goals20544/level3 и20120 Wolf/level4; после paid damage skills2 rewarded Wolf kills,+846EXP/+40SP |
| STORE8 | SQL: Mage7/12165EXP/10SP;1177:3,1184:2,1011:1; Fighter4/1435EXP/136SP, отдельный законный historical baseline |
| Earlier restart | Native9 Mage: тот же object/skills/gear, новый epoch453594923455100; минимум4 NORMAL kills/rewards,+2001EXP/+150SP,10 loot events |
| Final v6 career | Native10 Mage: level7/17736EXP/180SP → level8/21516EXP/300SP за3 native kills; затем Anti Magic146:1 за240SP у Trainer30035/object268462814 (22:18:14→22:18:24 UTC,EXP/kills неизменны) |
| Further v6 farm | После урока native10 достиг12 kills/rewards,+9450EXP/+300SP,113 pickups; overflow=false,reason=NONE;146:2 сохранён позднее |
| Fighter earned | Native11: level4/2530EXP/256SP → level5/2917EXP/300SP;5 native kills/rewards,+387EXP/+44SP,4 pickups,epoch457572631696100,NORMAL/WORLD |
| Fighter paid | Trainer30029/object268462810: Power Strike3:1,2,3 по50SP;22:51:01,22:54:31,22:58:52 UTC;SP300→150,EXP2917/kills5 неизменны |
| STORE11 | Fighter5/2917EXP/150SP,3:3; Mage9/36006EXP/280SP,1177:3/1184:2/1011:3/146:2; exact identities и paperdoll сохранены |
| Restart12 baseline | Все24 profile/character identities совпали с checkpoint11. Fighter первый NORMAL-сэмпл5/4519EXP/225SP: lawful background catch-up +1602EXP/+75SP учитывается отдельно, native counters=0 |
| Upgrade12 | Fighter BUY/equip Short Leather Gloves1119/object268487532:1069→529A (=540),старые48 больше не PAPERDOLL;native item pDef9→11,EXP4519/SP225 неизменны |
| Paid receipt12 | BUY operationKey382b87dd25b71ad2463556ab6ec8c9715e22de711fd57ac8f9019500fb569595,root9174387612105286943,childRevision63,NPC30002/object268462798/list3000200;COMMITTED,1069→529A,objectCount0→1 |
| Fighter further farm12 | Native paid141:1 за160SP (225→65), затем level5/4519EXP→level6/7197EXP:5 kills/rewards,+2678EXP/+125SP,15 pickups;epoch459047491953400,NORMAL/WORLD,overflow=false/NONE |
| Actual second field | Giant Toad20121/level5,goal9174387612105286943:21,generated.farm.13946664ad91058ff84c1e41.anchor;Geo travel(-97038,247767,-3648)→Trainer→(-97878,246874,-3686),eligible living prey |
| Mage paid/farm12 | 23:22:58→23:23:08 UTC:244:1 за470SP,580→110,EXP42351/kills0 неизменны;Trainer30035/object268462814; затем3 Wolf kills/rewards,+1269EXP/+60SP,4 pickups,epoch459950288777100,NORMAL/WORLD,overflow=false/NONE |
| Final SQL12 | Fighter6/7197EXP/30SP,3:3/141:1/142:1; Mage9/43620EXP/170SP,+244:1; gloves1119 exact object268487532 count1 PAPERDOLL,старые48 count1 INVENTORY; identities24/24 |
| Conservation11→12 | Fighter EXP2917+historical1602+native2678=7197;SP150+historical75−160+125−160=30. Mage EXP36006+historical6345+native1269=43620;SP280+historical300−470+60=170 |
| Final pair | Процессы11/PID38480 и12/PID43080:~23min и~21min native observation,одна DB/один JAR; обе штатные STORE/exit0/postflight PASS; goals5=root9174387612105286943 rev20→21,6=root2043172855533440484 rev56→59; edits/reseed=0 |

Финальный JAR SHA256: `ea4b03d09953a818f1f56fc6273f5e0da1ea6660aec0260c5fdee3f00c66d186`.
Career witness native8 использовал `eb853b488b28a32cc07bcb160272948378d7f0ec4cf0bc471bc83bdf39f4711c`;
SP-learning/equip owners с тех пор не менялись; final v6 добавил только bounded native recovery budget.
Установки использовали exact-path byte backups/SHA и native input seal; initial DB authority не переписывалась.
Final11/12 использовали только финальный SHA без source/JAR/harness edits между ними; Mage COMMITTED key611968a2132ee571898509b42392a11e978d387a0211a1af42ef4683dd5500ba сохранён без replay.

## Receipts и ограничения

Приватные raw receipts: `%TEMP%\phantom-pve-progression-115` и принадлежащий этой installation private TEST115 stage.
Там находятся OS/SQL G0, inputs, source/JAR preimages, native samples/flight/skills/resources, STORE и postflight.
Они не публикуются с credentials/полными SQL/PII; native факты для внешнего reviewer — по отчёту Codex.
Все12 own JVM штатно завершились exit0; final postflight foreign sessions0/online0/JVM0/game listeners0,630 protected preimages PASS. Наблюдался живой career loop с travel/rest/lessons, не непрерывная атака; counters привязаны к epoch, historical прирост не выдаётся за native.
Native4 Fighter завершил законный BUY/equip шести слотов, но на поле был WARM без human locality: отсутствие боя не объявлено combat-дефектом.
Native5 добавил обычный FOLLOW только LOCALPLAY_TEST_HUMAN; его движение не считается phantom Geo travel. Production JAR и DB не менялись.
Это bounded изменение существующего private observer, не новый action engine или наградный test fixture.
FOLLOW терял locality на геометрии; позднее добавлен отдельно помеченный catch-up только test-human, без phantom travel credit.
World-presence больше не является условием выбора второго READY класса: observer создаёт demand у read-only committed target.
Финальный itinerary11/12:15min Fighter, затем Mage; native admission, классы, позиции, stats и цели фантомов не задаются тестом.
Первый sandbox OS call отказал G0_FILE_OPEN_DENIED; тот же штатный механизм вне sandbox прошёл без ослабления guards.
Один native preflight до GameServer/SQL отказал из-за пропущенного fast seal; выполнен существующий preparatory sealer с повторной проверкой всех hashes.
Эти отказы не считаются успешными GameServer starts или игровыми наградами.
Первая попытка post11 под PowerShell7 остановилась на OS initialization до SQL; published Windows PowerShell5.1 entrypoint прошёл без изменения guards.

## Команды, стоимость и границы доказательства

Обозначения: `R` — исходный checkout, `S=%TEMP%\phantom-pve-progression-115\source`, `E=%TEMP%\phantom-pve-progression-115`, `M=L2J_Mobius_CT_2.6_HighFive`; `P` — exact8 allowlist выше с префиксом M.
Build: Ant entrypoint `E/readonly-build.xml`, targets `compile-tests jar`, output `E/build-v6`; exit0,40s; Owner38 rules PASS. Wrapper переопределяет только owner-config-apply, не compiler/validator.
Final focused: `java -Dphantom.module.root=S/M -Dphantom.test.reports=E/v6-reports -cp E/build-v6/phantom-test/bin;S/M/dist/libs/* org.l2jmobius.tests.phantoms.PhantomTestLauncher <suite> <seed>`.
Пары suite/seed: `supply071/115115`, `commerce-receipt/140014`, `progression-operations/13001302`, `combat-core/115115`, `combat-debt068/115115`, `combat-action-ownership/115115`, `navigation-core/115115`; все exit0.
Тот же classpath, main `org.l2jmobius.gameserver.phantoms.background.PhantomLivingWorld114Checks`: exit0,6 sections PASS.
Native orchestration: `powershell -NoProfile -ExecutionPolicy Bypass -File E/Run115.ps1 -Mode provision`; затем `-Mode run -Iteration N` и `-Mode post -Iteration N` последовательно; остановка только собственным `stopN.request`.
Git использован по bounded root exception и прямому разрешению TASK115 на clone/scoped commit/обычный push. Изменение истории/master/чужого WIP запрещено и не выполнялось.
Read-only команды: `git -C R rev-parse --show-toplevel`; `git -C R rev-parse HEAD`; `git -C R symbolic-ref --short HEAD`; `git -C R for-each-ref --format='%(refname:short) %(upstream:short)' refs/heads/feature/phantom-world`; `git -C R worktree list --porcelain`.
Также: `git -C R status --short`; `git -C R status --porcelain=v1 -z --untracked-files=no`; `git -C R ls-remote origin refs/heads/feature/phantom-world`; `git -C S rev-parse HEAD`; `git -C S rev-parse --show-toplevel HEAD`; `git -C S symbolic-ref --short HEAD`; `git -C S status --porcelain`; `git -C S status --porcelain --untracked-files=all`; `git -C S status --short`; `git -C S status --short --untracked-files=all`.
Создание: `git clone --single-branch --branch feature/phantom-world --no-hardlinks https://github.com/kpCat/L2J.git S`.
Review/scope: `git -C S diff -- P`; `git -C S diff --stat -- P` (также exact подмножества P); `git -C S diff --check -- P`.
Default whitespace check вернул2 на CR в смешанном LF/CRLF файле; `git -C S -c core.whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol diff --check -- P` прошёл0; тот же check выполняется с `--cached` после exact add. Runtime source/JAR bytes не менялись ради EOL.
Final scope/publication: `git -C S ls-tree -r --full-tree <baseline|HEAD>`; `git -C S rev-parse HEAD^`; `git -C S add -- P`; `git -C S commit --file E/commit115.txt`; `git -C S push origin HEAD:refs/heads/feature/phantom-world`; `git -C S ls-remote origin refs/heads/feature/phantom-world`. Один commit,parent d493fffa; exact SHA/server push receipt — финальный ответ и private publication receipt; force/history rewrite=0.

Bounded performance11/12:140/129 samples,heap2,723,714,032–4,065,760,112 /2,695,959,280–4,054,623,944 bytes,threads132–135;overload NORMAL,queueRejected0. Native12 ecology failures3,lastFailure=farm.inventory_projection_refreshed,стабильны в финальном окне; native overflow0. Это не soak на5000 actors.
Стоимость: production5 / tests2 (3 новых focused cases) / report1 / private harness15 файлов в ОДНОЙ адаптированной TASK114 family / compile6 / JAR6 / native starts12 / schemas1 / final suites7=213 checks +114 contracts.
Всего28 launcher invocations включая причинные RED и2 setup failures; ранние native epochs используются только с их собственными JAR hashes.
Снимок Goal перед публикацией:1,955,642 tokens /21,132s (5h52m12s). Финальный счётчик после push приведён в ответе; этот snapshot не включает последующие report/encoding/Git операции.
Целевой token budget сильно превышен. Причины: повторное длинное наблюдение и слишком частое перечитывание receipts/context, несколько неполных SELL исправлений, исправления observer locality и поздно найденный recovery timeout. Это не экономное выполнение.
При сохранённом product scope8 файлов процесс потребовал существенно больше времени/токенов, чем разумно; лишние наблюдения не выдаются за новые product outcomes.
Final artifact scope guard: exact8 allowlist, production source/JAR hashes совпадают со stage; protected630 сохранены; commit tree перед push ограничен тем же P.
mojibake-маркеры в изменённых файлах проверены: полный обязательный набор,совпадений0.
escaped Cyrillic в изменённых файлах проверены: все6 regex-вариантов,совпадений0;UTF-8 читается строго.
OWNER_VISUAL_PENDING: клиент и Visual QA не проводились. 5000/128+ soak, Party/PvP/PK/WIP109 и полный рынок Гирана не закрываются.
Next bottleneck: стоимость многократных Trainer trips и консервативный выбор prey у Mage9; наблюдаемые inventory projection refresh diagnostics. Новых работ по ним не начинали.
TASK116 не начиналась.
