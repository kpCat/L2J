# PHANTOM-ECONOMY-QOL-070 — итоговый отчёт, черновик

**Статус: PARTIAL / DRAFT. OWNER_PLAY: NOT_READY до закрытия оставшихся gates.**

Упаковка core-v1 не допущена к сборке runtime: Git archive преобразовал байты LF в CRLF в закреплённом pricing070.tsv (SHA DF463C8D… вместо6a2c9514…). До копирования и запуска обеих поставок процесс остановлен; оба runtime root отсутствовали. Исправлены .gitattributes только для TSV по аналогу069, ранняя проверка SHA в Build070 и Validate/Start. Immutable package070 не переиндексировался: автоматическая проверка отклонила предложенный renormalize, и эти пути исключены из staging. Производственные Java-классы не менялись. Дальнейшие ссылки на core-v1 относятся к сохранённым native-доказательствам. Обе runtime поставки собраны из core-v2; exact hashes приведены ниже,4562/4562 production classes побайтово равны core-v1.

Экономика и услуги реализованы; focused native проверки перечислены ниже. Сборка committed core прошла. На финальном JAR прошли retained economy/quest verify, quote contracts и runtime guard. Обычный Main, полезная работа M1 после sameDB restart и окончательная остановка поставок в момент подготовки этого черновика ещё **PENDING**. Полный four-role Queen Ant gate ранее прошёл native на development JAR; финальный повтор не завершился из-за законной смерти истощённых сохранённых акторов, подробности ниже. Эти строки должен окончательно сверить основной исполнитель; данный документ не принимает manual gate за владельца.

Дата: 23.10.2026. Начало кампании: 02:26:13 UTC, Unix1792722373. Окончание и фактическая длительность: **PENDING_FINALIZE**. Источник: `D:\Codex\repos\L2J-phantom-world`; единственный серверный модуль — `L2J_Mobius_CT_2.6_HighFive`. Ветка: `feature/phantom-world`. Baseline: `dd57479f28db8ed2cb6d824b645c57dcb49dfa18`. Core commit: `e8ca9328c39f071803b11980f3acb3ddd5c06326`.

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

Новые библиотеки, стек, серверный движок, клиентские IDs/ассеты и Studio не добавлялись. Локальные аналоги: native ItemContainer restore/transfer/destroy, account_premium lifecycle, PersonalPremiumQoL, native multisell writer, CLI068 и exact runtime069 guards. Прочитаны корневой AGENTS, status/workflow, весь пакет070 и ближайшие владельцы кода. Module AGENTS/README и корневой README не найдены; повторных поисков не делалось. Private lane отчёты — поясняющие журналы, а не отдельные acceptance gates.

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

Журналы ниже находятся в `<repo>/.phantom-local/task070/reports/`; source snapshots — в `docs/phantoms/reports/PHANTOM-ECONOMY-QOL-070-data/`. PASS development/native проверки не заменяет повтор на конечном committed JAR.

| Gate | Подтверждено сейчас | Статус до финализации |
|---|---|---|
| P069_QUEEN_ANT | v4 native four-role PASS; combat classes byte-identical dev→final; на final JAR Prominence damage470 и4215/4515=false, затем сохранённые акторы законно погибли до Vortex | PARTIAL: полный финальный повтор INCONCLUSIVE, не PASS |
| P069_M1 | Диагноз horizon/resource/recency; Prepare/Configure/Observe готовы, изменение AI по догадке не внесено | PENDING: полезная работа того же saved actor после restart не доказана |
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
| SAFETY | Новые070 roots/DB, ownership/isolation guards,203 physical Geo manifest; старые данные не были целями записи | PARTIAL: final protected SHA/STOP/DB0/ports0 PENDING |
| DELIVERY | Committed Ant jar+compile-tests прошли,2361 product/355 test sources,36с; BUILD_INFO070.json получен | PARTIAL: assembly/Main/restart/STOP PENDING |
| OWNER_PLAY | Не принимает незакрытый M1 или непроверенную поставку | NOT_READY |

Chance assertions — точная source/policy математика плюс actual NpcTemplate boundary execution; изолированные100% drop tables проверяют количество и suppression. Они не являются настоящим убийством Queen Ant ради случайного ring drop и не выдают предмет игроку. Native combat Queen Ant подтверждён отдельно.

`core-v1-v2-class-parity.tsv` подтверждает4562/4562 неизменённых production classes. `native-archive-input-parity.tsv` подтверждает те же8 effective XML, Q234/Q235 source и точные bytes применённой цены. В91004 archive добавлен явный NPC30300; в обоих входах уже есть native allow−1, поэтому список разрешает тот же обмен, а Java range guard одинаков. Новая Board acceptance проверка скомпилирована отдельно против pinned v2 JAR в ui-native-bin;355 исходников основного test tree не заменялись. Её source/class/JAR SHA фиксирует BOARD_NATIVE_BUILD070.json. Клиент/H5 renderer не запускался.

Native экономические fixtures имели явное TEST финансирование100 млрд Adena/actor; quest fixture —500 млн Adena/5 млн SP и retained inputs984 CrystalB, B Sword of Damascus79, Pure Silver6320. Это **не заработанная экономика**, не ship/release grants, не TestAdmin/kpCat и не признание phantom income. Проверка производства стоимости/потребления реальна; естественное время накопления не измерялось.

## Журналы и выполненные команды

Дополнительный bounded Main необходим из-за нового доказанного дефекта измерения: на boot2 атомарная публикация observer.properties прекратилась с Windows sharing failure, оставив полный временный файл. Это не доказательство остановки экологии. Java writer теперь повторяет только atomic move до пяти раз с четырьмя паузами10мс, по существующему LocalPlay-AtomicRename.cs; постоянная блокировка остаётся fail-closed. PowerShell readers используют ReadWrite|Delete и сохраняют проверку64KiB/UTF-8/ACL/reparse. Реальный Windows тест старого writer дал RED AccessDenied, исправленного — GREEN transient release/PERSISTENT_LOCK_FAIL_CLOSED; отдельный reader contract подтверждает OLD snapshot и NEW pathname. Независимый reviewer не нашёл Important/Critical дефектов. Изменение production writer требует новой committed сборки core-v3 и обновления только двух собственных остановленных070 roots; новые gameplay правила не добавлены.

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
| Runtime guard/PowerShell parser |0; DB_WRITES=0/PROCESS_ACTIONS=0,7 scripts parsed, closure-runtime-report.md |
| Rune audit, real XML only |0;41 rows/0 drop-spoil; local premium-report.md |
| Main/restart/assembly/stop commands |**PENDING_FINALIZE: добавить точные фактически исполненные команды, exits и receipts** |

У native запусков exact lane: `-Dphantom.task070.native=true -Dphantom.module.root=<module> -Dphantom.test.config=<repo>/.phantom-local/task070/native-test/game/config/Database.ini`; cwd — собственный `native-test/game`. Development overlay использовался только до финальной сборки. Штатный воспроизводимый wrapper — `tools/phantom-runtime/Test-Native070.ps1`, который фиксирует Check/Mode/BuildRoot/Label; **PENDING_FINALIZE: сверить и вписать literal outer command lines development запусков из журнала основного исполнителя**, не подставлять их как уже выполненные final-JAR команды.

## Неудачные проверки и исправленные причины

- Curse v1/v2: castStart/MP debit были, casting прерывался через1,2–1,8с, появлялся4515, damage0. Classpath hypothesis исключена по фактически загруженному pinned069 JAR. Fixture stack v3 указал `EffectZone.ApplySkill → Skill.applyEffects → Petrification`; custom zone удалена, v4 PASS. Ни урон, ни boss stats не подменялись.
- Final-JAR Queen Ant resume начат с retained tankHP1417 и mageHP182 после предыдущего боя. Prominence нанёс470,4215/4515 не появились; затем Queen Ant законно убила tank/mage до попадания Vortex. Assert1339 завершил процесс exit1. Revive/refill/reseed не применялись. Полный four-role v4 native proof остаётся ранее выполненным доказательством; `dev-final-class-delta.tsv` показывает изменения только Item/RestoreRow, MultiSellChoose, exporter, EconomyBoard/Shop и добавление PaidSubclass. Creature, Player, Skill, Formulas и остальные combat classes byte-identical. Это подтверждает неизменность проверенного combat implementation, но не превращает неоконченный финальный повтор в PASS.
- Первый economy native: missing pricing070.tsv из-за wildcard copy в ещё не существовавший destination. Ошибочный файл сохранён как preimage, каталог создан и заполнен070 input files; до этой ошибки мутаций economy не было.
- Второй economy native: проверки денег/premium/stock уже выполнились, затем test fixture неверно выбрала Codex entryId1 вместо actual PreparedEntry100000. Исправлен тест на native prepared ID; те же акторы/БД продолжены без reseed/refill, continuation PASS.
- Recovery replay: первый hydration SELECT i.* нарушал native positional restore contract — первый столбец owner_id ошибочно становился objectId. Исправлено i.object_id первым, добавлены exact container objectId/World Player identity assertions; `recovery-native-fixed.log` PASS. Также исправлен premium replay, который прежде не публиковал сохранённый account expiry.
- Quest fixture сначала не имела зарегистрированных Q234/Q235 constructors; добавлен exact native bootstrap. Сохранённый actor затем требовал штатный `Quest.playerEnter` после Player.load для native quest state hydration. Предшествующие assertion failures сохранены в `quest-native-dev.log`, `quest-native-retained.log`, `quest-native-current-data.log`; новые аккаунты/сброс quest state ради PASS не создавались. Продолжение `quest-native-player-enter.log` прошло полный путь.
- Final code review обнаружило ABBA count monitors и неправильный ownership manifest path в README reprice. Count locking переведён на dbLock, порядок paid inventory/material исправлен, README использует parent runtime root. Focused lock regression PASS. Итог review: конкретных unresolved Critical/Important в прочитанных областях нет; это не сертификат всех native callers или multi-JVM.
- Первая inline encoding-проверка не стартовала из-за PowerShell smart quote; исправленный scanner использует существующий ordinal/codepoint pattern. Pricing smoke потребовал чтение process inventory через одобренный require_escalated; первоначальный sandbox/CIM отказ произошёл до записи.

Полный старый verify/soak не запускался. Были только относящиеся к070 focused проверки, последовательные Ant/DB/native этапы и подготовка обычного Main. Fault injection подтверждает postcommit/prepublication recovery; неопределённый JDBC network commit отдельно не инжектировался, его fail-closed count guard проверялся кодом и не выдаётся за отдельный native PASS.

## Поставка, БД и сохранность

Созданы только `l2jmobiush5_phantom_test_070` и `l2jmobiush5_phantom_release_070`, после проверки отсутствия. MariaDB11.4.13, loopback3308; bootstrap127 tables/122 scripts, aggregate `CBB4FFA79C61FA9840D6DB678CE0BB232B408D4343A632EF794FDC26AC04EAA6`. Economy migration070 создаёт собственные InnoDB wallet/stock/orders; stock initial1 задаётся явным installer INSERT IGNORE, ordinary restart запас не пересоздаёт. Targeted schema update metadata не пересеивает остатки.

Предусмотрены собственные TEST `<repo>/.phantom-local/task070/runtime-test` и RELEASE `D:\PhantomWorld\Economy-QoL-070-candidate`. RELEASE2106/7777/control9150, TEST2250/7950/control9250; GameServer Xms2g/Xmx4g,203 physical GeoData. CB/premium flags включаются только070 assembly profile. Старые кандидаты057–069, их БД, общая MariaDB service/datadir, клиент, kpCat/TestAdmin, Studio и чужие worktrees не изменялись как цели задачи. Исторические входные JAR/config/owner файлы использованы read-only; final SHA comparison ещё нужно приложить.

| Обязательный финальный артефакт | Значение |
|---|---|
| Committed build root | `<repo>/.phantom-local/task070/build-final-core-v2` |
| Compiled source commit | `89a2b9d3a300871cbbd5855d144db9ae5a809a69` |
| GameServer.jar SHA-256 | `478E3E295BCDC7E4D875453213105903E8F47B2FA83313B50245DA16A2088010` |
| LoginServer.jar SHA-256 | `4F74BE10EE85CE94DD5179417321DE4EEA5EA9E2945812B309EE2B737306A805` |
| Source archive SHA-256 | `15C0934E1E5D9B0B23633C1E1674829F434EE7D2D9B2E8F18C17F4785238AAD5` |
| Committed tree SHA-256 | `E41DF8AFA475EA9D15E969C287C80CB0305B802F0B659EAFD70DD460DB5D5C0C` |
| Test tree SHA-256 | `61A33E2777FF0FC0C0ABAD9783FB0876E09B108F35C06A98163F059995D75A66` |
| Build receipt time | `2026-10-23T04:03:21.3538000Z`, sourceState=COMMITTED |
| Source/JAR equality TEST↔RELEASE | PASS; обе Assemble070 v2 exit0, одинаковые source/JAR,203 физических региона, Validate price SHA PASS |
| Обычный Main / тот же DB и JAR restart | **PENDING_FINALIZE: run IDs, profile/actor, epoch, полезное действие** |
| TEST и RELEASE final status | **PENDING_FINALIZE: оба OWN070 JVM STOPPED, точные PID receipts, DB0/ports0** |
| Самостоятельный ZIP/README/START/STATUS/STOP | **PENDING_FINALIZE: exact пути, SHA, validate result** |
| Protected input/package/untracked hashes после задачи |11 protected inputs/untracked совпали с начальными SHA;16 original070 input files неизменны;069 GameServer совпал с опубликованным SHA |

Логи могут содержать старое runtime stderr отображение кириллицы с replacement characters; исходники не переписываются по этим сериализованным/console артефактам. Credentials, private configs и TEST funding не публикуются.

## Scope, Git и окончательная фиксация

Bounded exception больше10 файлов заранее ограничен одной поставкой070 и перечисленными product/data/test/runtime семействами. Ниже exact allowlist, предоставленная основным исполнителем для core scope; неизменённые входные файлы original070 package могли включаться в commit как source inputs, это не означает изменения их байтов. Этот отчёт добавляется отдельно после core commit.

Git использовал основной исполнитель по явной AUTHORITY070 пользователя. Subagents pricing/premium/runtime/review git-команд не выполняли. Build script выполняет следующие подтверждённые исходником команды внутри разрешённого core build: `git symbolic-ref --short HEAD`, `git rev-parse HEAD`, `git status --porcelain --untracked-files=all -- L2J_Mobius_CT_2.6_HighFive`, `git archive --format=zip --output=<repo>/.phantom-local/task070/core-v1.zip e8ca9328c39f071803b11980f3acb3ddd5c06326 -- L2J_Mobius_CT_2.6_HighFive/build.xml L2J_Mobius_CT_2.6_HighFive/java L2J_Mobius_CT_2.6_HighFive/test L2J_Mobius_CT_2.6_HighFive/dist L2J_Mobius_CT_2.6_HighFive/tools`.

**PENDING_FINALIZE:** вписать полный literal список остальных фактически использованных Git-команд основного исполнителя (bounded inspection, remote read/fetch, exact-path stage/diff/commit), почему они разрешены, окончательный docs commit SHA и результат обычного non-force push в feature/phantom-world. По live ledger baseline remote сверялся через git ls-remote; literal аргументы до финализации здесь не выдумываются. Master/default branches, history, reset/clean/force/new worktree не менялись. Готовность push этим черновиком не подтверждается.

Mojibake-маркеры в изменённом файле этого отчёта проверены отдельно: полный набор27 маркеров, `rg` exit1, совпадений нет.

Escaped Cyrillic в изменённом файле этого отчёта проверены отдельно: все шесть regex, `rg` exit1, совпадений нет.

Полная проверка всех финальных изменённых исходников основным исполнителем: **PENDING_FINALIZE**, с отдельными результатами двух scans. Pricing/closure/premium lane уже зафиксировали свои scoped проверки без совпадений.

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
```

</details>
