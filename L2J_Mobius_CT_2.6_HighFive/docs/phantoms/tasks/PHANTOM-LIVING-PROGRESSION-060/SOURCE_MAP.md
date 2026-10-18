# SOURCE_MAP060 — проверенные исходники и дальнейший read-first

Remote branch проверена при подготовке18.10.2026: `afa843a9681b9026a38cc62ab9376114626b0a44`. Все пути ниже относительно High Five. Чтение кода не является выполнением native-теста. Фактические Windows PID/БД/JAR проверить исполнителю; текущий отчёт059 — источник достигнутого scope, не live state.

## Прочитано при подготовке пакета
| Source | Подтверждённый факт и вывод для060 |
|---|---|
| `docs/phantoms/reports/PHANTOM-M3-M7-CAMPAIGN-059.md`, строки25–62 | М2/М3 server scope; М4 retreat/M5 victory/Rift auto-entry/career producer открыты. 110 selected и30 native не все М7. Старый raid AoE тезис — гипотеза достаточности, не готовый fix |
| `docs/phantoms/tasks/PHANTOM-M3-M7-CAMPAIGN-059/AUTHORITY.md` | Реальные058t/058a, запрет старых удаления/attach, explicit layout scopes.060 добавляет новый кандидат и свежие разрешённые игровые зависимости |
| `java/org/l2jmobius/gameserver/phantoms/progression/L2jPhantomLearningJourney.java`,1–195 | Explicit goal `skill.learn`; существующие Decision candidates/handlers, class/index binding, paid progression, native trainer/canInteract, WALK. Это исполнитель, не самостоятельный producer; current party/busy ограничения учитывать |
| `java/org/l2jmobius/gameserver/phantoms/commerce/PhantomCommerceService.java`,1–190 | Без отдельного worker: versioned receipt, goal authority, bounded actor lease, quote/reconcile. Переиспользовать вместо второго transaction engine |
| `java/org/l2jmobius/gameserver/phantoms/commerce/L2jCommerceBackend.java`,25–220 | Реальные BUY/SELL/TELEPORT, BuyListData/TeleporterData, цены/налоги/вес/слоты. Limited stock/tax/refund имеют явные неподдержанные границы; не стирать guards ради закупки |
| `java/org/l2jmobius/gameserver/phantoms/economy/PhantomStoreService.java`,1–225 | Только visible materialized Phantom store lifecycle; quote/open/restore/close и native store refs. Не доказательство полного автоматического рынка |
| `java/org/l2jmobius/gameserver/phantoms/economy/PhantomMultipartyEconomyService.java`,1–190 | DirectTrade/StoreBuy/StoreSell/Manufacture, native PrivateStore/Manufacture services, counterparty OFFLINE_STORE для реального detached/без client. Не заменять World owner строкой SQL |
| `java/org/l2jmobius/gameserver/services/ManufactureService.java`,1–180 | Packet-independent native RecipeManager; exact manufacturer/instance/MANUFACTURE/crafting/range150 checks. STARTED не означает готовое изделие; нужен completion outcome |
| `java/org/l2jmobius/gameserver/model/actor/instance/BabyPet.java`,1–245 | OnSpawn берёт current available skills через PetDataTable, distinguishes HEAL/MANAHEAL_BY_LEVEL; own native CastTask, death/unSummon cleanup; castSkill ориентируется на владельца. Не добавлять поверх ещё один pet healer |

## Начальный exact read-set исполнителя (до12; расширять адресно)
1. `docs/phantoms/RELEASE_CONTRACT.md` и последний RESULT059 — актуальные authority/gaps (документы отдельно от product quota).
2. `phantoms/population/PhantomPopulationEcologyService.java` — symbols goal selection/creation/reconciliation; найти exact call chain производителя целей.
3. `phantoms/background/PhantomHistoricalBackgroundService.java` и существующий farm goal producer — выбрать нужный symbol, не загружать оба огромных файла целиком.
4. `phantoms/progression/L2jPhantomLearningJourney.java` — request/advance/registerCandidates.
5. `phantoms/progression/PhantomProgressionService.java` — текущие навыки/eligibility/learning.
6. `phantoms/commerce/PhantomCommerceDecision.java` и нужный shared travel handler.
7. `phantoms/commerce/L2jCommerceBackend.java` — quote/apply/reconcile, teleport legality.
8. `phantoms/economy/PhantomMultipartyEconomyService.java` — discover/exactCounterparty/commit.
9. `phantoms/economy/PhantomStoreService.java` и native offer discovery — нужные symbols.
10. `phantoms/PhantomSystem.java` — wiring выбранных producers/owners, не весь class.
11. `test/java/org/l2jmobius/tests/phantoms/PhantomCombatServerIntegrationSuite.java` — existing learned/learning-journey cases; related commerce/economy suites найти по imports/Launcher.
12. `build.xml` / `PhantomTestLauncher.java` — реальные modes/seeds/compile paths, существующий058 lane.

Сокращённые `phantoms/...` пути выше имеют префикс `java/org/l2jmobius/gameserver/`. Это read-first навигация; bodies неперечисленных функций автор пакета не объявляет полностью проверенными. Если точный класс/метод изменён — искать usages/imports в модуле и записать найденное, не придумывать API и не останавливать всю работу.

## Поздние направления читать лишь при переходе к ним
- Pet: `model/actor/instance/Pet.java`, `BabyPet.java`, PetDataTable/PetData XML, summoning item handlers, текущий progression controlled-actor/servitor code. Pet и Servitor не одно и то же; действующий permission на одно не автоматически реализует другое.
- Threat/PvP/raids: existing `PhantomPvpService`, `PhantomPartyTactics`, `L2jCombatBackend`/`PhantomCombatSkillSafety`, `PhantomRaidAssemblyService`/`L2jPhantomRaidAttemptRuntime`, native AI/hate/minion/skill XML. Имена классов уточнить по actual imports и report059.
- Rift: `PhantomRift*` services/Decision/consent, `DimensionalRift`/Party, native manager/scripts; М3 execution уже даёт адресный consent pattern, но ещё не означает consent на fee.
- Career: Class/SkillTree/QuestState, village_master и текущие audited quest/acquisition adapters; формат actual profession requirements читать из native data. Полный исторический каталог543 квестов не перепроверять для одной ветки.
- Offline: native PrivateStoreService/ManufactureService/RecipeManager и offline store manager/persistence; existing economy tests. Торговца со списком в памяти не объявлять offline-поставщиком без проверки native detached/store lifecycle.
- Delivery: `tools/phantom-runtime/Build-Package.ps1`, Runtime/exact059 guard/exporter, dist START_HERE_RU; новый explicit060 mapping, без копирования060 текста внутрь старого058 immutable owner.

## Primary source references (pinned)
- Repository head при подготовке: https://github.com/kpCat/L2J/tree/afa843a9681b9026a38cc62ab9376114626b0a44
- Источники в таблице доступны как `https://github.com/kpCat/L2J/blob/<BASE>/L2J_Mobius_CT_2.6_HighFive/<path>`.
- Пользовательское расширение18.10.2026 сохранено в AUTHORITY060§1. Предпочтения стратегии не представлены как утверждение о том, что каждый native boss имеет minions либо каждый pet лечит всю группу.

Никакие downloaded внешние сборки/DLL/непроверенные таблицы не требуются. Истина для условий игры — текущие native code/data и воспроизводимый результат. Framework/research отчёты не заменяют проверку данного кода.
