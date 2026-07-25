# Touchpoint audit — Task 004 headless Player feasibility spike

## Baseline and scope

Аудит выполнен до production-изменений на immutable baseline:

```text
Branch: feature/phantom-world
HEAD: eb008f2216b3e8381c0181d71ce200bbf4907ac7
origin/feature/phantom-world: eb008f2216b3e8381c0181d71ce200bbf4907ac7
Parent: 84f29a0002b25d2b1ff1a19fa9c92867479fd6a5
```

Expected untracked Task 004 package находится в scope. Независимый
`docs/agent-tasks/**` не читался, не менялся и исключён.

После only-read аудита пользователь одобрил documentation-only advancement:

```text
Effective Task 004 baseline: 1ca74a3d96e8fa51612ef3e5145c7398abf60f6d
Parent: eb008f2216b3e8381c0181d71ce200bbf4907ac7
Commit subject: PHANTOM_BOTS_ROADMAP
Only changed path: L2J_Mobius_CT_2.6_HighFive/docs/PHANTOM_BOTS_ROADMAP.md
Roadmap SHA-256: B049F85AE276906C969FE2FCC8A39F126B342AB1D8F256036E2EE6A60F1498D8
```

Проверка `git diff-tree` подтвердила единственный documentation path.
Roadmap не читался и не изменялся в Task 004; его требования не добавлены в
scope. Все audited production hashes ниже поэтому остаются точными. Task 004
должна дать один ordinary commit с parent `1ca74a3d...`.

## Source hashes and audited symbols

| File | Baseline SHA-256 | Audited symbols |
|---|---|---|
| `Player.java` | `FC569FF715B031E64B06BA6C7BD89D3934F1C5F81CE5766AB86D9CC9C75F2E54` | private constructors 893/923; `create` 1018; `load` 1209; `restore` 7095; `_client` 452; `getClient` 4085; `setClient` 4090; `sendPacket` 4397; `storeMe` 7629; `isOnline` 7897; `isOnlineInt` 7902; `isInOfflineMode` 7979; `deleteMe` 11684; `stopAllTasks` 15123 |
| `GameClient.java` | `5C7958A1ECBBA322791ABDCFBD6FB25C73C7BF486E6129CFFEE40E59819855B1` | `onDisconnection` 90; `sendPacket` 216; `load` 502 |
| `CharacterSelect.java` | `9C2D211C556EC9126CEDA6F62A40845F76D147546CDFD65D2C9671C986001236` | `runImpl`, `client.load`, Player/client bind, event failure cleanup |
| `Disconnection.java` | `D9FEBE2DDABA2C3416906DACB648DFC17C17CEBC8A2FCAC3AEED191C07F01D86` | constructor detach; `storeAndDelete` 110; `storeAndDeleteWith` 140; `onDisconnection` 155 |
| `World.java` | `4AE2C1614FE09A69FBCCF6DC6E3784F8320D9BC99F046CB632E69048D539D779` | `addObject` 167; `removeObject` 209; duplicate Player disconnect fallback |
| `ServerPacket.java` | `AA76DEC6F377B92047A2CEE2C1E33A8E568E6CE837E8827F0033CBDE575E72AC` | public `runImpl(Player)` 95 |
| `IdManager.java` | `9350BBB96EF08AF9F7D928A472FD78F15203C10FD9BDB3D14BCB85567821279D` | DB-backed used-ID initialization and release |
| `build.xml` | `16C1FCF2385D0987B826BCD9C6DD81F1C64B145C35BD170F5F6EB7BFC09BB9A2` | forked test launcher targets and cumulative `verify` |

Additional audited baseline hashes:

```text
AbstractHtmlPacket.java 4F1775641A7F80D8F90B7088842203D833F5F6EBA88E3BA2516D319E220B90D2
CreatureSay.java        1AD0EDA217BB08F42CE9B2BF1F3D97BA3B85389282267B737F6A4C450A1D12D
ItemList.java           7F5AE10941C52CE8336120B9AE308EE2B442F78B81CC569312237891D566518D
ExQuestItemList.java    3E7CE2B578D333009D4A6FC7755B1BC0794AE4F38650553886B06687E325BC68
TutorialCloseHtml.java  87641E69E10EFC2E83605730E1737851906C5E8E867E913E7EA07F89CACD950
OfflinePlayTable.java   9185AC5A4AABF12CEB34B76E19D629DB17DCCB1D2F5D87B5659635CD12704EC0
OfflineTraderTable.java 2B36A3AA5544D32C9041066056661834B4E7E35C85F35B49E4E28964A854003F
PlayerAutoSaveTaskManager.java
                        EEEDCE4C9D723EB237D18C0A4DE6EE42668252972BD6009327AE84D87919B82E
PhantomSystem.java      0BAE139A3506A598BEB14A5AFC1B68F61AA53293587785831B7791DD6A03D683
```

## Canonical lifecycle

- Оба private `Player` constructor-а создают canonical Player AI/Radar и
  запускают vitality task.
- `Player.create` создаёт canonical объект и пишет штатные character rows.
- `Player.load -> restore` восстанавливает character, inventory, warehouse,
  freight и skills; в конце выставляет online и регистрирует Player в
  `PlayerAutoSaveTaskManager`.
- `WorldObject.spawnMe` регистрирует object и visible object; `World.addObject`
  использует `putIfAbsent`. Duplicate Player disconnect обоих остаётся
  аварийным fallback, а не ownership-протоколом.
- `Player.storeMe` использует штатное persistence.
- `Player.deleteMe` очищает world/party/trade/request/instance/container,
  останавливает задачи, пишет DB online=false и удаляет autosave membership.
- Offline Play (`Player.load` около строки 94) и Offline Trade (`Player.load`
  около строки 232) подтверждают существующий canonical detached/null-client
  lifecycle без Player subclass.

## Packet dispatch and effects

Current real path:

```text
Player.sendPacket
  -> current GameClient.sendPacket
  -> writePacket(packet)
  -> packet.runImpl(player)
```

`Player.sendPacket` при null client сейчас no-op. `GameClient.sendPacket`
проверяет null packet, записывает packet и только затем исполняет effect.
Изменять `GameClient.sendPacket` не требуется.

Все текущие `runImpl(Player)`:

```text
ServerPacket.runImpl               default no-op
AbstractHtmlPacket.runImpl         HTML action-cache mutation
CreatureSay.runImpl                snoop observer packet dispatch
ItemList.runImpl                   nested ExQuestItemList dispatch
TutorialCloseHtml.runImpl          tutorial scope clear
```

В `Player.java` найдено 298 textual `sendPacket(` occurrences. Прямой
`sendPacket(null)` отсутствует. Конструкторные packet expressions non-null;
переменные packet/message формируются локально перед отправкой. Plausible null
источник в audited Player call sites не найден. Новый generic seam всё равно
зафиксирует явный fail-fast контракт для null.

## Online/session call sites

Все production call sites `isOnlineInt()`:

```text
AutoPotionTaskManager: требует ровно 1, поэтому headless=2 не получает task.
GameClient.load: ровно 1 означает существующую real session.
Player.broadcastCharInfo: 0 запрещает broadcast; 2 разрешает.
Player DB store paths: сохраняют deliberate online value.
EnterWorld: ровно 1 используется для real-online hardware counting.
PcCafePointsManager: первый path дополнительно исключает offline mode;
                     второй path не должен вызываться action facade.
```

Player-internal store call sites только сериализуют значение. Предпочтительная
политика `0/1/2` не меняет реальные и plain-null значения и допускает
observer visibility для active headless:

```text
_isOnline=false                    -> 0
real attached client               -> 1
detached real client               -> 2
active headless outbound session   -> 2
plain null client without headless -> 0
```

## Direct GameClient dependencies

Вне `network/clientpackets/**` найдены прямые dereference:

```text
AntiFeedManager.java:87
BotReportTable.java:452
VillageMaster.java:551, 612, 704
```

Остальные audited usages имеют explicit null guard (Offline Trade,
Olympiad, FameTask, PlayerStatus). Bounded Task 004 action facade не вызывает
AntiFeed, bot report или VillageMaster. Эти API не разрешены как headless
action routes.

## Player tasks and cancellation

Audited `ScheduledFuture` fields:

```text
_inventoryUpdateTask
_itemListTask
_skillListTask
_updateAndBroadcastStatusTask
_broadcastCharInfoTask
_broadcastStatusUpdateTask
_dismountTask
_fameTask
_vitalityTask
_teleportWatchdog
_taskForFish
_nevitHourglassTask
_recoGiveTask
_taskWarnUserTakeBreak
_chargeTask
_soulTask
_taskRentPet
_taskWater
```

`stopAllTasks` и `deleteMe` покрывают long-lived tasks; short coalescing packet
tasks проверяются test-only reflection после cleanup. Action facade использует
inventory APIs напрямую и не создаёт Player packet-coalescing tasks.

## Identity and login touchpoints

`GameClient.load` сначала проверяет existing World Player и сохраняет текущую
real-real double-login семантику, затем вызывает `Player.load`.
`CharacterSelect` после load привязывает `cha.setClient(client)` и
`client.setPlayer(cha)`; event failure вызывает `Disconnection`.
`Disconnection` централизует detach, store/delete и является минимальной точкой
final lease release.

Безопасный bounded hook возможен в approved envelope:

1. единый tokenized in-memory registry;
2. PHANTOM claim до `Player.load`, World checks до/после;
3. REAL_LOGIN reservation в `GameClient.load`;
4. transfer reservation lifetime к GameClient;
5. release в load/bind failure и `Disconnection` finally;
6. сохранение существующего real-real World path при отсутствии PHANTOM owner.

Fake `GameClient`/`Connection`, World duplicate arbitration и broad handler
changes для этого не нужны.

## Minimal test environment

Фактически доказанный минимальный порядок:

```text
working directory dist/game
ConfigLoader.init
PhantomTestDatabaseBootstrap (allowlist before Hikari)
ThreadPool.init once
IdManager -> World
CategoryData -> ExperienceData
EffectHandler.executeScript (effect master only; no script list)
EnchantSkillGroupsData -> SkillTreeData -> SkillData
ItemData -> EnchantItemOptionsData -> OptionData -> RecipeData
ClassListData -> PlayerTemplateData -> AdminData -> CharInfoTable
ClanTable -> CHSiegeManager -> ClanHallTable -> ClanHallAuctionManager
GeoEngine -> SkillLearnData -> NpcData
CastleManager.loadInstances -> InstanceManager -> ZoneManager
GrandBossManager.initZones -> TerritoryWarManager
Hero -> SevenSigns
PartyMatchWaitingList -> PartyMatchRoomList
CursedWeaponsManager -> RecipeManager -> OlympiadManager
fixture account -> Player.create -> store/delete -> Player.load
suite cleanup -> ThreadPool.shutdown -> DatabaseFactory.close
```

Это 39 явно перечисленных bootstrap-компонентов; исполняемый report печатает
их в том же порядке. Неявно наблюдались только узкие зависимости самого
canonical пути: `ScriptEngine` для effect master, `DatabaseIdManager` через
`IdManager`, `ForumsBBSManager` через `ClanTable`, `GrandBossManager` при
разборе zones и `WalkingManager` при загрузке territory data. Они
зафиксированы как transitive, а не скрыты. `GameServer`, LoginServer,
ConnectionManager, GameClient, network listener и общий script list не
инициализируются. Castle/territory/clan-hall data нужны потому, что canonical
`Player.create` строит `UserInfo`, а `Player.deleteMe` загружает полный
`ZoneManager`; siege activation и scripts не выполняются.

Текущие local test config и durable manifest присутствуют. Baseline manifest:

```text
schemaVersion=1
scriptCount=117
statementCount=205
aggregateSha256=A3C9FC62C662DC5E0E690D6E7D6E63B5B0268BAD3019348E75F565DA5C84453A
```

Freshness должна быть повторно доказана existing automated gate до любого
fixture mutation. Production DB запрещена.

## Audit conclusion

Fresh audit не обнаружил stop-rule condition. Минимальный seam, bounded
identity hook и explicit materialization могут быть реализованы внутри
approved touch envelope. Непроверенные риски перед verdict:

- фактический minimal singleton bootstrap;
- exactly-once recursive packet effects;
- cleanup всех canonical Player futures/autosave/world residues;
- failure matrix и one/ten fixture latency;
- CharacterSelect bind-failure lease lifetime.

Итоговый feasibility verdict будет дан только после executable gates.
