# PHANTOM-ECONOMY-QOL-070 — итоговый отчёт

**ECONOMY_FUNCTIONS_VERIFIED=PASS; PLAYER_START_READY=PASS; OWNER_PLAY=READY_OWNER_PLAY в объявленных границах070. OWNER_VISUAL_PENDING.** M1 закрыт по фактическому native maintenance маршруту после рестарта. Полный farm recovery, завершение supply покупки и непрерывная автономия не заявляются.

Упаковка core-v1 не допущена к сборке runtime: Git archive преобразовал байты LF в CRLF в закреплённом pricing070.tsv (SHA DF463C8D… вместо6a2c9514…). До копирования и запуска обеих поставок процесс остановлен; оба runtime root отсутствовали. Исправлены .gitattributes только для TSV по аналогу069, ранняя проверка SHA в Build070 и Validate/Start. Immutable package070 не переиндексировался: автоматическая проверка отклонила предложенный renormalize, и эти пути исключены из staging. Исправление упаковки не меняло Java-классы. Дальнейшие ссылки на core-v1 относятся к сохранённым native-доказательствам. Обе поставки сначала собраны из core-v2 (4562/4562 production classes равны core-v1), затем из-за доказанного Windows mailbox defect обновлены на committed core-v3: изменился только writer, gameplay classes сохранились.

Экономика и услуги реализованы; focused native проверки перечислены ниже. Committed core-v3 собран и проверен на обычных TEST Main/sameDB/sameJAR рестартах и отдельном RELEASE Main. После них final-v3 retained economy/quest verify прошли без новых покупок или reseed. Обе среды штатно STOPPED/DB0, own JVM0 и шесть портов свободны. Queen Ant four-role native v4 proof сохранён через неизменность combat implementation/data; финальный повтор отдельно INCONCLUSIVE из-за законной смерти истощённых акторов. Серверная готовность не является клиентской визуальной приёмкой владельца.

Дата: 23.10.2026. Начало кампании:02:26:13UTC, Unix1792722373. Native проверки и упаковка завершены к05:46:40UTC —3ч20м27с; финальная документация и публикация выполняются в той же кампании, точное время фиксируется в FINAL_PUBLICATION070.json. Источник: `D:\Codex\repos\L2J-phantom-world`; единственный серверный модуль — `L2J_Mobius_CT_2.6_HighFive`. Ветка: `feature/phantom-world`. Baseline: `dd57479f28db8ed2cb6d824b645c57dcb49dfa18`. Initial core: `e8ca9328c39f071803b11980f3acb3ddd5c06326`; окончательный compiled code: `7779f4ddf104ae8b94f8bf7b399a391ce0a51c8d`.

## Что изменено и почему

1. Удалена ровно одна custom Queen Ant Curse Zone из `dist/game/data/zones/effect.xml`. Она давала Petrification4515 персонажам50+ и прерывала настоящее заклинание. Boss stats, fire resistance−83, regen, nurses и HP не ослаблялись. Приписывать исходную проблему одной регенерации или passive4045 было бы неверно.
2. Добавлен единый источник явно применённых цен `data/economy070/pricing070.tsv` с SHA-256. Startup читает цены, но не меняет XML. Новый CLI070 сохраняет canonical x1, строит Plan, применяет его только к собственной остановленной070 среде и восстанавливает точные preimages при Rollback. Цена не вычисляется из XP/SP или собственного premium.
3. Native Alt+B получил русские разделы расходников, квестов/клана, свитков, эпиков, Codex, Mammon/AA, эполетов, premium и Personal QoL. Убраны старые fixed-price premium кнопки. Старые ordinary gear multisell600010/600014/600016 остаются запрещены, включая прямой bypass. Проверки выполняются сервером.
4. `Economy070Shop` хранит общий запас12 эпиков, account savings и immutable receipts. Native Adena count и wallet/premium/receipt используют один Connection/commit; epic wallet/stock/native item row/receipt — также один commit. Повтор request проверяет владельца, персонажа, kind, amount и SKU. Проданный или переданный предмет не создаётся заново при replay.
5. `PremiumManager` блокирует account row и продлевает абсолютный epoch-ms срок от `max(now, persistedExpiry)`; cache и flags публикуются после commit. Дни1/3/7/15/20/30, все обычные персонажи аккаунта. Старый receipt не укорачивает более поздний оплаченный срок. Headless/native-managed phantoms не получают бесплатный premium.
6. Grouped/ungrouped ordinary, raid/GrandBoss и spoil используют общий premium policy: XP/SP2, drop/spoil chance2/amount1, Adena chance1/amount2. Premium плюс native chance bonus ограничен3 до global/champion rates. Base per-ID0, quest reward exclusions и native loot-owner policy сохранены. Premium участника не повышает rate непремиального native reward owner.
7. Paid subclass gateway заменяет конкретные raid/group acquisition этапы настоящими материалами в правильном STARTED/memo. Completion выполняют исходные Q234/Q235 NPC events. Сохранены75+, основной класс, non-Kamael,984 B-кристалла, B-оружие, Pure Silver, Star of Destiny, смешивание и финальное native потребление. Entitlement и каждая выдача материала имеют durable receipt. Blood Fire6318 имеет native price500/default sellable: выдача один раз предотвращает повторное получение денег через продажу/отмену квеста. Zero quest stack остаётся detached до commit, поскольку native hasQuestItems проверяет presence.
8. `Item` использует существующий per-item dbLock для count mutation. Выявленный review конфликт source-item→target-item monitors устранён. Paid material selection/commit/publication следует порядку inventory→material, без окна конкурентного создания второго стека.
9. Добавлены самостоятельные070 build/assembly/runtime/reprice инструменты с owner/root/schema/JAR/Geo/PID checks, общей блокировкой START/Apply и штатным graceful STOP. Старые runtime не являются целями этих инструментов.

Новые библиотеки, стек, серверный движок, клиентские IDs/ассеты и Studio не добавлялись. Локальные аналоги: native ItemContainer restore/transfer/destroy, account_premium lifecycle, PersonalPremiumQoL, native multisell writer, CLI068 и exact runtime069 guards. Прочитаны корневой AGENTS, status/workflow, весь пакет070 и ближайшие владельцы кода. На финальной сверке исправлена ошибочная отметка об отсутствии файлов: модульный Agents.md и корневой README.md существуют и дочитаны, вместе с master plan и TASK_PACKAGE_STANDARD; новых противоречий реализованному scope не найдено. Только модульный README.md отсутствует. Старые пути/БД в исторических инструкциях не применялись вместо явной AUTHORITY070. Private lane отчёты — поясняющие журналы, а не отдельные acceptance gates.

## Цены и доступность

Authoritative canonical x1 — `dist/game/data/economy070/STORE_CATALOG070.tsv`:71 SKU/store rows. Runtime —15 quotes. Полные source IDs, валюты, provenance, native uses и numeric current/target находятся в [таблицах070](PHANTOM-ECONOMY-QOL-070-data/STORE_CATALOG070.tsv), [PRICING_PLAN070](PHANTOM-ECONOMY-QOL-070-data/PRICING_PLAN070.tsv), [EPIC_CATALOG070](PHANTOM-ECONOMY-QOL-070-data/EPIC_CATALOG070.tsv), [QUEST_CATALOG070](PHANTOM-ECONOMY-QOL-070-data/QUEST_CATALOG070.tsv).

| Предложение, x1 | Цена / правило |
|---|---|
| Mana Potion728 | 1 000 Adena; PvP разрешён, исходные effect/reuse |
| Premium | 1 000 000 Adena за реальные сутки; срок — точное целое число суток |
| Subclass gateway | 100 000 000 Adena, явный AUTHOR_FLOOR |
| Codex6622 / 9625 / 9626 / 9627 | 20 / 40 / 50 / 100 млн Ancient Adena |
| Olympiad6622 | Исходные550 Olympiad Tokens13722 сохранены |
| Fortress9625 / 9626 / 9627 | 200 / 540 / 1 800 Epaulettes9912 плюс1 / 1,26 / 1,8 млн Adena |
| Clan1419 / 3874 / 3870 | Единые10 / 25 / 50 млн Adena во всех Alt+B/QoL маршрутах |
| Splendor Key8056 | Обе исходные строки — единые10 млн Adena |
| Epic low / mid / high | Авторские floors20 / 60 / 200 млрд Adena,14 / 30 / 60 суток restock |
| Blessed Zaken21712 | 1 111 111 111 112 Adena, основание включает0,09% native source rarity |

Все28 quest и16 scroll offers сохранены. Дешёвые1/25 Adena пути устранены. Данные об усилии30/180 минут и monetary floors являются авторской политикой, **не измеренным Adena/час**. Для части эпиков paired Adena fair отсутствует; применение AUTHOR_RARITY_OPPORTUNITY обозначено прямо. Active Antharas —29068; Beleth использует scripted killer reward. Цена не выводится только из crystal grade.

Adena scaling использует общий source-weighted native expectedYield item57; Seal Stones/AA — отдельный expected yield6360/6361/6362×3/5/10. Рост только Adena не повышает AA Codex. Проверен повторяемый x1→x10→x20→x10, без умножения уже применённого snapshot. SHA runtime TSV: `6a2c9514e4bf64aa64e35b121c4bbcebd0bf71413a788efd2b412d94406f9924`.

**Фактический native MaxAdena:3 000 000 000 000 000**, а не предполагаемые2,1 млрд. Все12 текущих epic quotes ниже этого лимита; максимум1 111 111 111 112. Покупка SKU с ценой **выше native cap — NOT_APPLICABLE для настроенного каталога**, её успешное прохождение не заявляется. Savings deposit/withdraw, wallet debit, race и receipts проверены на текущих реальных ценах. Это не доказательство отдельного сценария >3×10^15.

Витрина Mammon не подделывает NPC bypass: real31126 остаётся владельцем операций и native условий. У реального Giran Pushkin30300,77458/148169/−3595, добавлен постоянный обмен камней3/5/10. Seven Signs event policy не изменена. Antharas stone3865 в текущем source проверяется possession-only; Baium cloth4295 расходуется1 — эти случаи не объявляются одинаковым consume proof.

## Runes и Personal QoL

Аудит [RUNE_AUDIT070.tsv](../tasks/PHANTOM-ECONOMY-QOL-070/RUNE_AUDIT070.tsv) содержит все41 RUNE/RUNE_SELECT templates: реальные ID, source name/icon/action, skill/effect, время, passive path и существующие shop sources. Drop/spoil effects у них не найдены: **NO_NATIVE_DROP_SPOIL_RUNE_ITEM**. XP/SP/Crystal/Feather/Eva runes не переименованы в drop/spoil. Skill8415 Ring Ability даёт native bonusDropRate, но не является обнаруженной timed rune. Новый fictitious rune SKU, client icon или asset не создан.

Старые720h rune int-time arithmetic и identical-skill remove/stack требуют отдельного аудита перед продажей таких rune;070 их не открывает. Client locale/render не проверялся. Personal QoL остаётся ограничен существующим нормальным access0/allowlist admission: source OFF/пустые allowlists не заменены всеобщим доступом. Это ограничение должно быть честно видно владельцу.

## Фактические gates

Журналы ниже находятся в `<repo>/.phantom-local/task070/reports/`; source snapshots — в `docs/phantoms/reports/PHANTOM-ECONOMY-QOL-070-data/`. Development/native PASS не означает автоматически final-JAR PASS: перенос доказательств ниже обоснован точной class/input parity, а финальные повторы названы отдельно.

| Gate | Подтверждено | Финальный статус |
|---|---|---|
| P069_QUEEN_ANT | v4 native four-role PASS; неизменность combat code/data подтверждена до core-v3; на final-v1 JAR Prominence damage470 и4215/4515=false, затем сохранённые акторы законно погибли до Vortex | PASS по сохранённому native v4 proof; финальный повтор отдельно INCONCLUSIVE, не final-JAR four-role PASS |
| P069_M1 | SameDB/same-v3 actor4 после native восстановления продолжил finite native supply WALK к реальному NPC30138:14,1→2,6км; цель/epoch/deadline сохранены | PASS bounded useful-native continuation; убийства/покупка/полный farm recovery NOT_PROVEN, прежний PHASE_DEADLINE не стёрт |
| PRICING | CLI3/3: rates round-trip, AA independence, idempotent Apply, conflicts, stale lock, byte-exact8file rollback | PASS |
| PREMIUM | Native1+7 суток, account sibling, insufficient/replay; ordinary/RaidBoss/GrandBoss chance/amount policy, Adena17→34, spoil5, suppression21747=0; replay восстанавливает текущий8-day срок | PASS native; final-JAR retained expiry/replay verify PASS |
| RUNES |41 actual templates,0 drop/spoil effect rows | NO_NATIVE_DROP_SPOIL_RUNE_ITEM |
| CODEX |4 real AA debit/product native exchanges | PASS native; final source/input binding подтверждён |
| EPOLETTE |3 real dual9912+Adena native exchanges | PASS native; final source/input binding подтверждён |
| MAMMON | Pushkin3 colors3/5/10, range/insufficient/overflow controls; real31126 remove-SA4681→72 | PASS native; final source/input binding подтверждён |
| QUEST | Полный Q234→Q235 native event chain, реальные retained inputs/consumption, native VillageMaster.checkQuests=true; clan0→5 с consumed proofs/SP | PASS native; final-JAR sameDB loaded quest/clan verify PASS |
| EPIC_STOCK |2-buyer last-unit race ровно1 winner; persistent stock0; test-clock restock максимум1; receipts/replay и conservation | PASS native в настроенном каталоге; >native-cap SKU NOT_APPLICABLE |
| RECOVERY | Fault после SQL commit до native publication; exact object replay/no second debit; premium cache republish | PASS native; final retained replay PASS |
| CONCURRENCY | Два противоположных existing-stack native transfers завершаются под удержанными source monitors; counts conserved | PASS focused regression |
| UI | На финальном v2 JAR сформированы9 настоящих страниц; stale/OFF denied, деньги/склад неизменны; native multisell denials отдельно | PASS server HTML/semantics; OWNER_VISUAL_PENDING, личный allowlist OFF |
| SAFETY | Новые070 roots/DB, ownership/isolation guards,203 physical Geo; protected11/package16/older24+069both JAR SHA; оба runtime STOPPED/DB0/JVM0/sixports0 | PASS в проверенном scope; полная hash-проверка старых config/client trees NOT_RUN |
| DELIVERY | Committed core-v3 Ant jar+compile-tests2361 product/357 test,38с; самостоятельный RELEASE native Main200shops/16combat profiles; ZIP seal и Validate | PASS; файлы и существующая собственная БД на этом ПК, без DB backup/авторелокации |
| OWNER_PLAY | Все hard070 gates пройдены по объявленному native контракту; M1 materially resumes через конечный maintenance маршрут | READY_OWNER_PLAY; OWNER_VISUAL_PENDING; полный farm/trade completion NOT_PROVEN |

Chance assertions — точная source/policy математика плюс actual NpcTemplate boundary execution; изолированные100% drop tables проверяют количество и suppression. Они не являются настоящим убийством Queen Ant ради случайного ring drop и не выдают предмет игроку. Native combat Queen Ant подтверждён отдельно.

`core-v1-v2-class-parity.tsv` подтверждает4562/4562 неизменённых production classes. `native-archive-input-parity.tsv` подтверждает те же8 effective XML, Q234/Q235 source и точные bytes применённой цены. В91004 archive добавлен явный NPC30300; в обоих входах уже есть native allow−1, поэтому список разрешает тот же обмен, а Java range guard одинаков. Новая Board acceptance проверка скомпилирована отдельно против pinned v2 JAR в ui-native-bin;355 исходников основного test tree не заменялись. Её source/class/JAR SHA фиксирует BOARD_NATIVE_BUILD070.json. Клиент/H5 renderer не запускался.

Для Queen Ant отдельно `queen-data-core-v3-parity.tsv`: native input и committed core-v3 совпадают после нормализации только CRLF/LF для12 файлов — NPC29001/14176, skill1230/1339/1217/1068/239, zones/effect.xml и GrandBoss/NPC/Player/PVP/Rates.ini. Полный исходный v4 proof содержит melee31, Prominence582, Vortex1733 с effect, summon76, native heal сверх обычной regen и Might;4215/4515 отсутствуют у всех ролей. Reviewer подтвердил, что пакет требует реальные four-role evidence и не требует повторять всю матрицу на каждом побайтово эквивалентном combat implementation. Это сохранение доказательства, а не waiver или принятие OWNER_PLAY.

Native экономические fixtures имели явное TEST финансирование100 млрд Adena/actor; quest fixture —500 млн Adena/5 млн SP и retained inputs984 CrystalB, B Sword of Damascus79, Pure Silver6320. Это **не заработанная экономика**, не ship/release grants, не TestAdmin/kpCat и не признание phantom income. Проверка производства стоимости/потребления реальна; естественное время накопления не измерялось.

## Журналы и выполненные команды

Дополнительный bounded Main потребовался из-за нового доказанного дефекта измерения: на boot2 атомарная публикация observer.properties прекратилась с Windows sharing failure, оставив полный временный файл. Это не доказательство остановки экологии. Java writer теперь повторяет только atomic move до пяти раз с четырьмя паузами10мс, по существующему LocalPlay-AtomicRename.cs; постоянная блокировка остаётся fail-closed. PowerShell readers используют ReadWrite|Delete и сохраняют проверку64KiB/UTF-8/ACL/reparse. Реальный Windows тест старого writer дал RED AccessDenied, исправленного — GREEN transient release/PERSISTENT_LOCK_FAIL_CLOSED; отдельный reader contract подтверждает OLD snapshot и NEW pathname. Независимый reviewer не нашёл Important/Critical дефектов. Изменение production writer потребовало committed core-v3 и обновления двух собственных остановленных070 roots; оба действия выполнены, новые gameplay правила не добавлены.

| Команда / проверка | Exit / receipt |
|---|---|
| DB-free javac exporter с существующим GameServer.jar/libs; baseline/plan exporter |0; `pricing-catalog070-certified/` |
| `powershell -NoProfile -ExecutionPolicy Bypass -File tools/phantom-economy/Test-PricePhantomEconomy070.ps1 -GameRoot <source/dist/game> -OutputDirectory <repo>/.phantom-local/task070/pricing-smoke070-c -JavaClassPath <pricing-classes;existing GameServer.jar;libs/*> -SourceManifest <pricing-catalog070-certified/sources.tsv>` |0,3/3; pricing-report.md/pricing-encoding070.txt |
| OWN070 preflight, bootstrap-test, bootstrap-release |0; `preflight.log`, `bootstrap-test.log`, `bootstrap-release.log`; схемы отсутствовали до CREATE |
| Native Curse resume, dev JAR |0; `curse-native-zonefix-v4.log`, `curse-native-1792723739059.tsv` |
| Native retained economy continuation |0; `economy-native-retained.log`, `economy-native-resume-1792724419604.tsv` |
| Recovery fault/replay |0; `recovery-native-fixed.log`, `recovery-native-1792725395358.tsv` |
| Native quest resume after player-enter setup |0; `quest-native-player-enter.log`, `quest-native-resume-1792725877196.tsv` |
| Count-lock regression/retained verification |0; `economy-lock-review.log`, `economy-native-verify-1792726946140.tsv` |
| Final-JAR retained economy verify |0; `final-economy-verify.log`, ECONOMY070_NATIVE_VERIFY_PASS |
| Final-JAR sameDB quest/clan verify |0; `final-quest-verify.log`, ECONOMY070_QUEST_VERIFY_PASS |
| `java -cp <core-v1 GameServer.jar;libs/*;phantom-test/bin> org.l2jmobius.tests.phantoms.PhantomEconomy070Contracts <module>/dist/game/data/economy070/pricing070.tsv` и `org.l2jmobius.commons.util.Task070RuntimeGuardChecks` |0, ECONOMY070_QUOTE_CONTRACTS_PASS / OWN070_RUNTIME_GUARD_PASS; DB_WRITES0/PROCESS_ACTIONS0 |
| Final v2 JAR native Board check, `PhantomEconomy070BoardNative` |0; `final-board-native.log`,9 HTML в `native-board070/`, source/class/JAR hashes `BOARD_NATIVE_BUILD070.json` |
| Final-JAR Queen Ant resume |1; `final-queen-ant.log`, `curse-native-1792727212203.tsv`; истощённая retained fixture погибла, полный повтор INCONCLUSIVE |
| `ant -f <repo>/.phantom-local/task070/source-core-v1/L2J_Mobius_CT_2.6_HighFive/build.xml -Dbuild=<repo>/.phantom-local/task070/build-final-core-v1 jar compile-tests` |0; `core-v1-build.log`, BUILD SUCCESSFUL36с;2 прежних runFinalization removal warnings |
| `Build-Task070.ps1 -Label core-v2 -SourceCommit 89a2b9d3a300871cbbd5855d144db9ae5a809a69` |0; `core-v2-build.log`, Ant35с; исправлена только упаковка/tools,4562 production classes побайтово равны core-v1 |
| `Build-Task070.ps1 -Label core-v3 -SourceCommit 7779f4ddf104ae8b94f8bf7b399a391ce0a51c8d` |0; `core-v3-build.log`, Ant38с;2361 production/357 test sources;3 предупреждения Windows-specific test ExtendedOpenOption и2 прежних runFinalization |
| Runtime guard/PowerShell parser |0; DB_WRITES=0/PROCESS_ACTIONS=0,7 scripts parsed, closure-runtime-report.md |
| Windows mailbox writer на committed core-v3 JAR |0; `writer070-final-v3.log`, реальный transient reader и permanent lock fail-closed; случайно независимо повторён closure agent с тем же результатом в `writer070-committed-v3.log` |
| Production class parity v2→v3 |0; `CLASS_PARITY_CORE_V3_070.json`,4562 classes, отличается только LocalPlayPilotService;4561 остальных побайтово равны |
| Rune audit, real XML only |0;41 rows/0 drop-spoil; local premium-report.md |
| Actual Main/restart/assembly/stop |0; `assemble-{test,release}-core-v2.log`, `main-boot{1,2}-*.log`, `main-boot{3,4,5}-v3-*.log`, `release-main-v3-*.log`; exact Source/JAR и реальные PID ниже |
| `Test-Native070.ps1 -BuildRoot <task>/build-final-core-v3 -Check PhantomEconomy070Native -Mode verify -Label post-main-v3-economy-verify` |0; ECONOMY070_NATIVE_VERIFY_PASS после реальных Main рестартов |
| `Test-Native070.ps1 -BuildRoot <task>/build-final-core-v3 -Check PhantomEconomy070QuestNative -Mode verify -Label post-main-v3-quest-verify` |0; ECONOMY070_QUEST_VERIFY_PASS после реальных Main рестартов |
| Private `Package-Final070.ps1 -SourceCommit 7779f4ddf104ae8b94f8bf7b399a391ce0a51c8d -AcceptancePath <task>/ACCEPTANCE070.tsv -AcceptanceSha256 <exact-file-SHA> -ParentAccepted` |0; `package-final-runtime-helper070.log`, PACKAGED_STOPPED_DB0; это sealing фактической матрицы, не manual QA владельца |

У native запусков exact lane: `-Dphantom.task070.native=true -Dphantom.module.root=<module> -Dphantom.test.config=<repo>/.phantom-local/task070/native-test/game/config/Database.ini`; cwd — собственный `native-test/game`. Development overlay использовался только до финальной сборки. Ранние development Java invocation выполняли классы `PhantomEconomy070CurseNative resume`, `PhantomEconomy070Native resume`, `PhantomEconomy070RecoveryNative` и `PhantomEconomy070QuestNative resume` с GameServer.jar первым после явно указанного dev-overlay. Их фактические результаты и IDs перечислены в таблице; они не переименованы в final-v3 запуски. Штатный wrapper — `tools/phantom-runtime/Test-Native070.ps1`, который фиксирует Check/Mode/BuildRoot/Label; точные финальные команды после обычного Main приведены отдельно ниже.

## Неудачные проверки и исправленные причины

- Curse v1/v2: castStart/MP debit были, casting прерывался через1,2–1,8с, появлялся4515, damage0. Classpath hypothesis исключена по фактически загруженному pinned069 JAR. Fixture stack v3 указал `EffectZone.ApplySkill → Skill.applyEffects → Petrification`; custom zone удалена, v4 PASS. Ни урон, ни boss stats не подменялись.
- Final-JAR Queen Ant resume начат с retained tankHP1417 и mageHP182 после предыдущего боя. Prominence нанёс470,4215/4515 не появились; затем Queen Ant законно убила tank/mage до попадания Vortex. Assert1339 завершил процесс exit1. Revive/refill/reseed не применялись. Полный four-role v4 native proof остаётся ранее выполненным доказательством; `dev-final-class-delta.tsv` показывает изменения только Item/RestoreRow, MultiSellChoose, exporter, EconomyBoard/Shop и добавление PaidSubclass. Creature, Player, Skill, Formulas и остальные combat classes byte-identical. Это подтверждает неизменность проверенного combat implementation, но не превращает неоконченный финальный повтор в PASS.
- Первый economy native: missing pricing070.tsv из-за wildcard copy в ещё не существовавший destination. Ошибочный файл сохранён как preimage, каталог создан и заполнен070 input files; до этой ошибки мутаций economy не было.
- Второй economy native: проверки денег/premium/stock уже выполнились, затем test fixture неверно выбрала Codex entryId1 вместо actual PreparedEntry100000. Исправлен тест на native prepared ID; те же акторы/БД продолжены без reseed/refill, continuation PASS.
- Recovery replay: первый hydration SELECT i.* нарушал native positional restore contract — первый столбец owner_id ошибочно становился objectId. Исправлено i.object_id первым, добавлены exact container objectId/World Player identity assertions; `recovery-native-fixed.log` PASS. Также исправлен premium replay, который прежде не публиковал сохранённый account expiry.
- Quest fixture сначала не имела зарегистрированных Q234/Q235 constructors; добавлен exact native bootstrap. Сохранённый actor затем требовал штатный `Quest.playerEnter` после Player.load для native quest state hydration. Предшествующие assertion failures сохранены в `quest-native-dev.log`, `quest-native-retained.log`, `quest-native-current-data.log`; новые аккаунты/сброс quest state ради PASS не создавались. Продолжение `quest-native-player-enter.log` прошло полный путь.
- Final code review обнаружило ABBA count monitors и неправильный ownership manifest path в README reprice. Count locking переведён на dbLock, порядок paid inventory/material исправлен, README использует parent runtime root. Focused lock regression PASS. Итог review: конкретных unresolved Critical/Important в прочитанных областях нет; это не сертификат всех native callers или multi-JVM.
- Первая inline encoding-проверка не стартовала из-за PowerShell smart quote; исправленный scanner использует существующий ordinal/codepoint pattern. Pricing smoke потребовал чтение process inventory через одобренный require_escalated; первоначальный sandbox/CIM отказ произошёл до записи.
- Первая попытка private refresh v2→v3 через Windows PowerShell5 остановилась до создания backup/изменения runtime из-за другого culture sort. `HASH_ORDER_DIAGNOSIS070.json` подтвердил одинаковый набор28662 файлов, но разный порядок PowerShell Core/Framework (первое отличие auction_bid.sql/auction.sql). Сам ZIP SHA совпал с receipt. Повтор полной проверки в PowerShell7, создавшем receipt, прошёл без обхода source/test hash; refresh завершён STOPPED/DB0. Runtime START/STATUS используют individual SHA и не зависят от этого full-tree sort; формат provenance и его сортировка не перерабатывались вне задачи.
- Первый package preflight остановился до записей: private script сравнил рабочий LF helper с SHA архивного CRLF helper. Нормализованный текст совпал; переиспользован exact runtime helper903A21… по уже успешному Refresh pattern. Source/JAR/config не менялись. Следующая упаковка полностью прошла; ранний отказ сохранён в `package-final070.log`.
- Первая атомарная замена финального guide не выполнилась: PowerShell преобразовал null backup argument в пустой path. Guide SHA не изменился; первоначальный напечатанный PASS недостоверен. Ошибка устранена именованным same-volume backup, exact preimages сохранены вне checkout; только последующий `DELIVERY_GUIDE_FINAL070.json` со state ATOMIC_APPEND_PASS подтверждает успешную замену.

Полный старый verify/soak не запускался. Были только относящиеся к070 focused проверки, последовательные Ant/DB/native этапы и подготовка обычного Main. Fault injection подтверждает postcommit/prepublication recovery; неопределённый JDBC network commit отдельно не инжектировался, его fail-closed count guard проверялся кодом и не выдаётся за отдельный native PASS.

## Поставка, БД и сохранность

### Обычный Main: состояние и границы измерения

Boot1 core-v2 (Game14696/Login28444) выполнил45с TestHuman и120с после его выхода. Profile4 Baferdrag/268484299 дал native140EXP/8SP и сохранил level3/EXP385/SP22/HP36/MP46; profile7 Cobramind не дал полезного farm из-за LOCAL_FARM_UNAVAILABLE. После60с recent-human pin actor4 ушёл из World. STOP штатный, DB0. Ошибочно выбранный до запуска observation ID1 оказался merchant ledger, после read-only диагноза observer allowlist исправлен на реальные4,7; phantom state не менялся.

Boot2 core-v2 (Game29700/Login29000) восстановил тот же actor4/HP36 и200 offline owners/600 items. До TestHuman setup observer остановился с private I/O failure; полный temporary файл и thread dump сохранены. Это не свидетельство остановки экологии. После этого выполнены только доказанные writer/reader fixes и один дополнительный bounded same-v3 pair для корректного измерения. Повторение является исключением по новому причинному факту, не старым soak.

Boot3 final core-v3 (Game23780/Login14428) сохранил тот же actor4 и начал native epoch94529126741500. В45с actual native rest HP36 выросло до82/138, затем до138/138 в оставшемся коротком окне. Useful debt94529647207100 не обновлялся лечением; resource cap дал bounded_unavailable, затем REGEN законно закончился до sticky deadline. Kill/reward/loot0 и прежние EXP385/SP22/Adena8 сохранены — отдых не засчитан как убийства. STOP штатный, DB0, observer I/O failure не повторился. Source AI resource policy не менялась по догадке.

Boot4 same-v3 (Game31996/Login5932) сохранил identity и восстановленное здоровье. Короткий native background lease завершился до первого30с population sample; `Resume` TestHuman находился в Giran, поэтому unseen rewards не были засчитаны. После STOP сохранены тот же actor4, HP90/MP53, новая точка24496/9206/−3584,200 прежних offline owners/600items. Поздняя заявка дополнительного local45 окна не запускалась: до ранее назначенного STOP оставалось меньше55с. Это не было объявлено farm PASS.

Boot5 — последняя bounded fixture correction,251с runtime (05:21:38–05:25:49UTC), тот же v3 Game2156/Login5352. Read-only safe-share sampler запущен до START и собирал существующие1с World samples, без изменения source/config/JAR/actor. После законного BGREADY/noWorld/nolease выполнен один Setup4,45с local TestHuman и90с passive; затем немедленный штатный STOP/DB0. В `main-boot5-v3-actor4-timeline.tsv` тот же4/268484299, epoch95402728176200, finite goal1503805470514203338/revision7591, inventory.maintenance SELLitem18 у реального Minaless30138/object268466837, NPCpoint12009/15704/−4554. Native AI MOVE_TO прошёл от24496/9206 до14633/15887 с корректной Geo-высотой: расстояние к цели14,1→2,6км. HP9→87,6 восстанавливалось естественно. Journey start1792733001105, budget480000мс, deadline1792733481105 не сбрасывались. Старый REGEN PHASE_DEADLINE сохранился, но не остановил фактический native маршрут.

Именно значительный прогресс по конечной native maintenance цели закрывает контракт P069_M1 «proceeds to useful native activity». Пакет P069_CLOSURE допускает native retreat/real heal/other useful action и не требует завершить farm/trade как дополнительный gate. Независимый reviewer подтвердил bounded PASS; один старый diagnostic не означает operational stuck, когда действие продолжает приближаться к реальному назначению. Покупка/прибытие/currentAmount1, новые kill/reward receipts и полный farm/BG recovery **NOT_PROVEN**. EXP385→1182/SP56 до первого видимого среза не приписаны наблюдавшимся native убийствам. Безразмерное ожидание, refill, reset часов/incident/debt, extra actors и выдача игровых ресурсов не применялись.

Stopped read-only corroboration `main-boot5-v3-actor4-sql070-final.tsv`: тот же actor4, level4/class31, HP92/154 MP60/60, EXP1182/SP56/Adena33, конечная точка13672/16021/−4576, примерно1,7км от торговца; native-context COMPLETED/travelPosition=true/farmPosition=false, bg.state READY. Тот факт, что item18 имеется в SQL inventory, не выдаётся за sale completion без native receipt. Первый private probe не нашёл JDBC driver до DB connect; ошибочный receipt сохранён, исправленный probe переиспользовал существующий runtime classpath и выполнил только read-only SELECT. Delta797EXP/34SP/25Adena остаётся UNATTRIBUTED.

RELEASE Main: Game24304/Login1432, stock boot42с, Xms2g/Xmx4g и203Geo;16 combat identities и200 native shops/owners/600store rows, первоначальный capital20млн/stock value82093352 один раз. Самостоятельная свежая RELEASE DB не получала TEST accounts/funding. START вывел success/status, его Windows PowerShell process завершился, но внешний capture не возвращал управление; отдельных STOP request/log ещё не было. Выполнен тот же guarded inline `Runtime070.ps1 -Action Stop -RuntimeRoot D:\PhantomWorld\Economy-QoL-070-candidate`: Game/Login штатно завершены, DB0; затем внешний capture вернулся start0/stop0. Повторный STOP уже остановленных ролей не запускал новые процессы. Отдельные receipts `release-main-v3-explicit-stop070.log` и `release-main-v3-orchestration070.log`, native shutdown5с в `release-main-v3-java070.log`; никаких force fallback или ещё одного START.

Созданы только `l2jmobiush5_phantom_test_070` и `l2jmobiush5_phantom_release_070`, после проверки отсутствия. MariaDB11.4.13, loopback3308; bootstrap127 tables/122 scripts, aggregate `CBB4FFA79C61FA9840D6DB678CE0BB232B408D4343A632EF794FDC26AC04EAA6`. Economy migration070 создаёт собственные InnoDB wallet/stock/orders; stock initial1 задаётся явным installer INSERT IGNORE, ordinary restart запас не пересоздаёт. Targeted schema update metadata не пересеивает остатки.

Созданы собственные TEST `<repo>/.phantom-local/task070/runtime-test` и RELEASE `D:\PhantomWorld\Economy-QoL-070-candidate`. RELEASE2106/7777/control9150, TEST2250/7950/control9250; GameServer Xms2g/Xmx4g,203 physical GeoData. CB/premium flags включены только070 assembly profile. Старые кандидаты057–069, их БД, общая MariaDB service/datadir, клиент, kpCat/TestAdmin, Studio и чужие worktrees не являлись целями записи. Исторические входные JAR/config/owner файлы использованы read-only. Начальные11 protected/untracked файлов и16 original070 inputs совпали с исходными SHA;24 старых candidate JAR и оба069 JAR совпали с опубликованными SHA. В историческом069 manifest24 Database.ini вообще не имели исходного SHA: это NO_PRIOR_SHA, а не24 изменённых файла; их mtimes предшествуют070. Полная byte-identity старых config/client trees не заявляется. Точные результаты — `PROTECTED_FINAL070.json` и финальный scope guard.

| Обязательный финальный артефакт | Значение |
|---|---|
| Committed build root | `<repo>/.phantom-local/task070/build-final-core-v3` |
| Compiled source commit | `7779f4ddf104ae8b94f8bf7b399a391ce0a51c8d` |
| GameServer.jar SHA-256 | `4EA1B473274C0CA3778A7340ED386C78237FAC53B22822F8AE6C3D0BC37468D9` |
| LoginServer.jar SHA-256 | `D0AD42F22FC815E8FDAF61C936AB64987CB7C13721923CD41287F1AB31A1FDD3` |
| Source archive SHA-256 | `E5C1A115260EA9C2B7A02D488C2F9D953E87BF511D5C5C8BF9DFB9891312B2FE` |
| Committed tree SHA-256 | `1855C18A71EAAC60AFB5862F577F8BF87437E6A7DEB86BC37F963521F681E2F0` |
| Test tree SHA-256 | `713A2EB0BDCBC482E7608AB5B158E7EE20D121A16D9AF18FD46DA1DD61B8CFBF` |
| Build receipt time | `2026-10-23T04:55:39.5206375Z`, sourceState=COMMITTED |
| Source/JAR equality TEST↔RELEASE | PASS; обе Assemble070 v2 exit0; private refresh v3 exit0,15 exact file targets с external SHA preimages; config/data/catalog/Geo сохранены; оба Validate/Status после refresh STOPPED/DB0 |
| Обычный Main / тот же DB и JAR restart | PASS; TEST v3 epochs/PID/actor/полезный маршрут приведены выше; отдельный RELEASE Game24304/Login1432; post-Main economy/quest verify0 |
| TEST и RELEASE final status | PASS; `FINAL_STOP070.json`, `FINAL_STATUS_{Test,Release}070.json`; оба Game/Login STOPPED, DB0, own JVM0, порты2106/7777/9150/2250/7950/9250 свободны |
| Самостоятельная поставка | `D:\PhantomWorld\Economy-QoL-070-candidate\START_HERE_070_RU.md`, START.cmd/STATUS.cmd/STOP.cmd, BUILD/SOURCE_BUILD_INFO070.json,11 каталогов, ACCEPTANCE070.tsv, FINAL_DELIVERY070.json; Runtime Validate PASS |
| ZIP | `<repo>/.phantom-local/task070/delivery-d4430dc6f1e7492e973ce59c74252845/Economy-QoL-070.zip`;1204403020bytes,27039entries;SHA256 `092F30B91FD27DFFBD8BC4EEE729EB08CF8CB11F28277A3EC23924C98CA413B6` |
| ZIP sealing | `PACKAGE_RECEIPT070.json` state PACKAGED_STOPPED_DB0, completed05:45:59UTC; source/JAR/Geo/entry names+lengths/inventory metadata verified; private ACL; databaseBackupIncluded=false; existing own DB/path required |
| Acceptance / final delivery manifest SHA | `AC03B19E0BBC6DBC7EEDC9A61B8F7AEC42712DE944C62D50D03C3270CECF484C` / `58907820421009652FF72071E140C010AA344873DBA4D404BA65BD0931C8B7E6` |
| Protected input/package/untracked hashes после задачи |11 protected inputs/untracked совпали с начальными SHA;16 original070 input files неизменны;069 GameServer совпал с опубликованным SHA |

Логи могут содержать старое runtime stderr отображение кириллицы с replacement characters; исходники не переписываются по этим сериализованным/console артефактам. Credentials, private configs и TEST funding не публикуются.

## Scope, Git и окончательная фиксация

Bounded exception больше10 файлов заранее ограничен одной поставкой070 и перечисленными product/data/test/runtime семействами. Ниже exact allowlist, предоставленная основным исполнителем для core scope; неизменённые входные файлы original070 package могли включаться в commit как source inputs, это не означает изменения их байтов. Этот отчёт добавляется отдельно после core commit.

Git использовал основной исполнитель по явной AUTHORITY070 пользователя. Subagents pricing/premium/runtime/review git-команд не выполняли. Build script выполняет следующие подтверждённые исходником команды внутри разрешённого core build: `git symbolic-ref --short HEAD`, `git rev-parse HEAD`, `git status --porcelain --untracked-files=all -- L2J_Mobius_CT_2.6_HighFive`, `git archive --format=zip --output=<repo>/.phantom-local/task070/core-v1.zip e8ca9328c39f071803b11980f3acb3ddd5c06326 -- L2J_Mobius_CT_2.6_HighFive/build.xml L2J_Mobius_CT_2.6_HighFive/java L2J_Mobius_CT_2.6_HighFive/test L2J_Mobius_CT_2.6_HighFive/dist L2J_Mobius_CT_2.6_HighFive/tools`.

Остальные фактически использованные команды inspection/verification: `git status --porcelain --untracked-files=all -- .gitattributes .gitignore L2J_Mobius_CT_2.6_HighFive`, `git ls-remote origin refs/heads/feature/phantom-world`, `git fetch --no-tags origin feature/phantom-world`, `git diff --name-only dd57479f28db8ed2cb6d824b645c57dcb49dfa18 -- .gitattributes .gitignore L2J_Mobius_CT_2.6_HighFive`, `git diff --check -- $scope070`, `git diff --cached --stat`, `git diff --cached --check`. `$scope070` содержит ровно97 paths из exact allowlist ниже. Отдельные `git diff -- <exact paths>` и `git add -- <exact paths>` использовали только эти task-owned пути;7 исходных untracked068V не staged. `git add .`, renormalize и broad restore не выполнялись.

Source commits выполнены обычными командами `git commit -m 'Implement PHANTOM-ECONOMY-QOL-070 transactional economy and native checks'` (e8ca9328c39f071803b11980f3acb3ddd5c06326), `git commit -m 'Preserve pinned economy payload bytes in committed archives'` (89a2b9d3a300871cbbd5855d144db9ae5a809a69), `git commit -m 'Retry private mailbox publication after transient Windows reader locks'` (7779f4ddf104ae8b94f8bf7b399a391ce0a51c8d). Build v2/v3 использовал тот же literal `git archive --format=zip --output=<task>/<label>.zip <codeSHA> -- <module>/build.xml <module>/java <module>/test <module>/dist <module>/tools` с соответствующими закреплёнными label/SHA; без dirty overlays в финальной сборке.

Заключительная фиксация затрагивает только два exact path: `git add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/reports/PHANTOM-ECONOMY-QOL-070.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/PHANTOM_CURRENT_STATUS.md`, затем `git commit -m 'Record PHANTOM-ECONOMY-QOL-070 final native acceptance and delivery'`. Публикация — `git fetch --no-tags origin feature/phantom-world`, `git ls-remote origin refs/heads/feature/phantom-world`, `git push origin HEAD:feature/phantom-world`, затем `git rev-parse HEAD` и повторный `git ls-remote origin refs/heads/feature/phantom-world` для exact equality. Полный финальный SHA/exit codes/время/remote equality записываются после успешной операции в private `<task>/reports/FINAL_PUBLICATION070.json` и финальном ответе, без циклической попытки вписать собственный commit SHA в его содержимое. Эти действия прямо разрешены AUTHORITY070. Master/default branches, чужая история, reset/clean/force/new worktree не менялись.

**Фактический push code: PASS.** 23.10.2026 в05:56:03UTC обычный `git push origin HEAD:feature/phantom-world` опубликовал `7779f4ddf104ae8b94f8bf7b399a391ce0a51c8d` поверх исходного dd57479f, exit0. Повторный `git ls-remote` вернул exact тот же SHA; `CODE_PUBLICATION070.json` фиксирует NORMAL_NON_FORCE_PASS/remoteEqual=true. Все три code/tool commits опубликованы. Следующий docs-only commit сохраняет этот уже фактический результат; финальная ветка и квитанция публикации фиксируются отдельно указанным способом.

Mojibake-маркеры в97 финальных изменённых файлах проверены отдельно: полный набор27, matches0; `Verify-Encoding070.ps1 -Mode Mojibake`, exit0.

Escaped Cyrillic в97 финальных изменённых файлах проверены отдельно: все шесть regex, matches0; `Verify-Encoding070.ps1 -Mode Escaped`, exit0. Две отдельные проверки22 доставляемых/сопровождающих текстов также дали matches0/exit0. Pricing/closure/premium lane ранее зафиксировали собственные scoped scans без совпадений.

<details>
<summary>Exact core scope — repository-relative paths</summary>

```text
.gitignore
L2J_Mobius_CT_2.6_HighFive/dist/game/data/html/CommunityBoard/Custom/merchant/main.html
L2J_Mobius_CT_2.6_HighFive/dist/game/data/html/CommunityBoard/Custom/navigation.html
L2J_Mobius_CT_2.6_HighFive/dist/game/data/html/CommunityBoard/Custom/premium/main.html
L2J_Mobius_CT_2.6_HighFive/dist/game/data/html/trainer/30300.htm
L2J_Mobius_CT_2.6_HighFive/dist/game/data/multisell/91002.xml
L2J_Mobius_CT_2.6_HighFive/dist/game/data/multisell/91004.xml
L2J_Mobius_CT_2.6_HighFive/dist/game/data/multisell/custom/600024.xml
L2J_Mobius_CT_2.6_HighFive/dist/game/data/scripts/handlers/bypass/communityboard/HomeBoard.java
L2J_Mobius_CT_2.6_HighFive/dist/game/data/scripts/quests/Q00234_FatesWhisper/Q00234_FatesWhisper.java
L2J_Mobius_CT_2.6_HighFive/dist/game/data/scripts/quests/Q00235_MimirsElixir/Q00235_MimirsElixir.java
L2J_Mobius_CT_2.6_HighFive/dist/game/data/zones/effect.xml
L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/commons/util/LocalRuntimeShutdown.java
L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/data/xml/MultisellData.java
L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/managers/PremiumManager.java
L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/model/actor/templates/NpcTemplate.java
L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/model/item/instance/Item.java
L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/model/itemcontainer/PlayerInventory.java
L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/network/clientpackets/MultiSellChoose.java
L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/economy/PhantomRateAwareLootFairValue.java
L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java
L2J_Mobius_CT_2.6_HighFive/dist/db_installer/sql/game/economy070.sql
L2J_Mobius_CT_2.6_HighFive/dist/game/data/economy070/STORE_CATALOG070.tsv
L2J_Mobius_CT_2.6_HighFive/dist/game/data/economy070/price-policy070.properties
L2J_Mobius_CT_2.6_HighFive/dist/game/data/economy070/pricing070.sha256
L2J_Mobius_CT_2.6_HighFive/dist/game/data/economy070/pricing070.tsv
L2J_Mobius_CT_2.6_HighFive/dist/game/data/economy070/quest-contract070.tsv
L2J_Mobius_CT_2.6_HighFive/dist/game/data/multisell/91005.xml
L2J_Mobius_CT_2.6_HighFive/dist/game/data/multisell/91006.xml
L2J_Mobius_CT_2.6_HighFive/docs/phantoms/reports/PHANTOM-ECONOMY-QOL-070-data/CONFIG_RATE_SNAPSHOT.tsv
L2J_Mobius_CT_2.6_HighFive/docs/phantoms/reports/PHANTOM-ECONOMY-QOL-070-data/EPIC_CATALOG070.tsv
L2J_Mobius_CT_2.6_HighFive/docs/phantoms/reports/PHANTOM-ECONOMY-QOL-070-data/EPOLETTE_SHOP070.tsv
L2J_Mobius_CT_2.6_HighFive/docs/phantoms/reports/PHANTOM-ECONOMY-QOL-070-data/MAMMON_SERVICES070.tsv
L2J_Mobius_CT_2.6_HighFive/docs/phantoms/reports/PHANTOM-ECONOMY-QOL-070-data/PRICE_TABLE070.tsv
L2J_Mobius_CT_2.6_HighFive/docs/phantoms/reports/PHANTOM-ECONOMY-QOL-070-data/PRICING_PLAN070.tsv
L2J_Mobius_CT_2.6_HighFive/docs/phantoms/reports/PHANTOM-ECONOMY-QOL-070-data/QUEST_BYPASS_COVERAGE070.tsv
L2J_Mobius_CT_2.6_HighFive/docs/phantoms/reports/PHANTOM-ECONOMY-QOL-070-data/QUEST_CATALOG070.tsv
L2J_Mobius_CT_2.6_HighFive/docs/phantoms/reports/PHANTOM-ECONOMY-QOL-070-data/STORE_CATALOG070.tsv
L2J_Mobius_CT_2.6_HighFive/docs/phantoms/reports/PHANTOM-ECONOMY-QOL-070-data/UNPRICED.tsv
L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-ECONOMY-QOL-070/ACCEPTANCE.tsv
L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-ECONOMY-QOL-070/AUTHORITY.md
L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-ECONOMY-QOL-070/EPIC_STOCK.md
L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-ECONOMY-QOL-070/GOAL.txt
L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-ECONOMY-QOL-070/MAMMON_CURRENCIES.md
L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-ECONOMY-QOL-070/P069_CLOSURE.md
L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-ECONOMY-QOL-070/PACKAGE_SHA256.txt
L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-ECONOMY-QOL-070/PLAN070.md
L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-ECONOMY-QOL-070/PREMIUM_AUDIT070.md
L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-ECONOMY-QOL-070/PREMIUM_RUNES.md
L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-ECONOMY-QOL-070/PRICING_MODEL.md
L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-ECONOMY-QOL-070/PRODUCT_DECISIONS.md
L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-ECONOMY-QOL-070/QUEST_BYPASS.md
L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-ECONOMY-QOL-070/READ_SET.md
L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-ECONOMY-QOL-070/RELEASE.md
L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-ECONOMY-QOL-070/RUNE_AUDIT070.tsv
L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-ECONOMY-QOL-070/TASK.md
L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-ECONOMY-QOL-070/TEST_PLAN.md
L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/economy/PhantomEconomy070CatalogExporter.java
L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/qol/Economy070Board.java
L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/qol/Economy070Pricing.java
L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/qol/Economy070Shop.java
L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/qol/PaidSubclass070Service.java
L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/qol/PremiumLootPolicy.java
L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/commons/util/Task070RuntimeGuardChecks.java
L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomEconomy070Contracts.java
L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomEconomy070CurseNative.java
L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomEconomy070Native.java
L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomEconomy070QuestNative.java
L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomEconomy070RecoveryNative.java
L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomEconomy070Resume.java
L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomEconomy070Schema.java
L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomEconomy070Trades.java
L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomPaidSubclass070Checks.java
L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomPremium070Checks.java
L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomTask070Environment.java
L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomTask070Lane.java
L2J_Mobius_CT_2.6_HighFive/test/resources/phantoms/economy070/NativeQuestBootstrap070.java
L2J_Mobius_CT_2.6_HighFive/tools/phantom-economy/Price-PhantomEconomy070.ps1
L2J_Mobius_CT_2.6_HighFive/tools/phantom-economy/README070_RU.md
L2J_Mobius_CT_2.6_HighFive/tools/phantom-economy/Test-PricePhantomEconomy070.ps1
L2J_Mobius_CT_2.6_HighFive/tools/phantom-runtime/Assemble070.ps1
L2J_Mobius_CT_2.6_HighFive/tools/phantom-runtime/Build-Task070.ps1
L2J_Mobius_CT_2.6_HighFive/tools/phantom-runtime/Configure-Test070.ps1
L2J_Mobius_CT_2.6_HighFive/tools/phantom-runtime/Observe-Main070.ps1
L2J_Mobius_CT_2.6_HighFive/tools/phantom-runtime/Prepare-Inputs070.ps1
L2J_Mobius_CT_2.6_HighFive/tools/phantom-runtime/Runtime070.ps1
L2J_Mobius_CT_2.6_HighFive/tools/phantom-runtime/Task070Common.ps1
L2J_Mobius_CT_2.6_HighFive/tools/phantom-runtime/Task070DatabaseProbe.java
L2J_Mobius_CT_2.6_HighFive/tools/phantom-runtime/Test-Native070.ps1
.gitattributes
L2J_Mobius_CT_2.6_HighFive/docs/phantoms/reports/PHANTOM-ECONOMY-QOL-070.md
L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomEconomy070BoardNative.java
L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/localplay/LocalPlayPilotService.java
L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/gameserver/localplay/LocalPlayPilotWriter070Checks.java
L2J_Mobius_CT_2.6_HighFive/tools/phantom-local-play/LocalPlay-Pilot.ps1
L2J_Mobius_CT_2.6_HighFive/tools/phantom-local-play/Test-LocalPlayPilotReader.ps1
L2J_Mobius_CT_2.6_HighFive/docs/phantoms/PHANTOM_CURRENT_STATUS.md
```

</details>
