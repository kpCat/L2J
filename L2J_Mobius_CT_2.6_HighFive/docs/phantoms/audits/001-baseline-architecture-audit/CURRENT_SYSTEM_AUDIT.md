# CURRENT SYSTEM AUDIT — Task 001

Unless stated otherwise, every `path:line` below refers to
`16d61833b3983a3976583d0e4813e0de9457a52f`.

## Built-in Fake Players

### Runtime model

The built-in feature is NPC-based, not a persistent player implementation.

- `FakePlayersConfig.load()` reads `./config/Custom/FakePlayers.ini`; the
  feature defaults disabled (`FakePlayersConfig.java:36-60`).
- `NpcData` parses `<fakeplayer>` inside an NPC template, sets
  `fakePlayer=true`, and constructs a `FakePlayerHolder`
  (`NpcData.java:350-389`).
- `FakePlayerHolder` contains presentation/class/equipment/clan/store-looking
  fields and registers name-to-NPC-ID mappings
  (`FakePlayerHolder.java:65-107`).
- `FakePlayerData` stores only concurrent name, NPC ID and talkability maps
  (`FakePlayerData.java:35-106`).
- The datapack spawns NPC ID `80000` in
  `dist/game/data/spawns/Others/FakePlayers.xml`.
- `FakePlayerInfo` serializes an NPC using player-shaped visual data. The
  runtime actor remains `Npc`/`Monster`; inventory, player persistence and
  account identity are not introduced by the packet.
- `GameServer` initializes `NpcData` and then `FakePlayerChatManager`
  (`GameServer.java:303-305`).

### Chat and scheduling

`FakePlayerChatManager.manageChat()` submits one `ThreadPool.schedule` per
response with a random 5–15 second delay (or caller-supplied random range)
(`FakePlayerChatManager.java:82-90`). Responses lowercase input and implement
English special-case/`EQUALS`/`STARTS_WITH`/substring `CONTAINS` matching
(`:97-163`). An answer is selected using `Rnd` and emitted as `CreatureSay`
from the NPC (`:171-182`).

This path is non-deterministic, template-driven, English-centric and creates
one scheduled future per requested response. It has no persisted conversation
state, cancellation owner, bounded response queue, identity collision guard,
or restart reconstruction.

### Reuse decision

Reusable only as fixtures/ideas:

- player-shaped NPC appearance data;
- spawn samples and presentation packet fields;
- chat corpus as negative/baseline fixtures;
- current config naming and startup placement as local conventions.

Not reusable as Phantom World domain core:

- NPC actor/AI/status/inventory rules;
- visual clan/private-store fields as substitutes for canonical state;
- name-to-NPC-ID identity;
- random scheduled chat path;
- substring response engine.

No canonical `PlayerInventory`, `Party`, `Clan`, `QuestState`, mail, direct
trade, siege registration, player DB lifecycle, or real account/character
online status exists in this model.

## Player creation, restoration and state

### Construction and persistence

- Both `Player` constructors are private. Construction creates AI and `Radar`
  and starts the vitality recurring task immediately
  (`Player.java:893-929`).
- `Player.create(...)` allocates an ID, initializes player state and calls
  private `createDb()` (`:1018-1046`, `:7032+`).
- `Player.load(objectId)` delegates to private `restore`
  (`:1209-1212`, `:7095-7354`).
- `restore` reads `characters`, constructs the full `Player`, restores
  subclasses, clan association/account character names, inventory, warehouse,
  freight, skills and secondary character data; then sets online in memory and
  registers with `PlayerAutoSaveTaskManager` (`:7095-7354`).
- `storeMe()` delegates to `store(true)` and writes character state plus
  inventory/warehouse/freight and associated scripts through several DB calls
  (`:7580-7629`).
- `deleteMe()` performs broad, best-effort cleanup: zones, online DB flag,
  events, party rooms, timers, crafting, combat/cast, zones, party, world,
  summons, clan member reference, active request/trade, instance, containers,
  friends/block list and autosave membership (`:11684-12084`).

`restore` is not a pure load: the constructor starts vitality scheduling and
the method adds autosave ownership before world spawn. A failed materialization
must therefore call the canonical cleanup path even if it failed before
`spawnMe`.

### Client and outbound packets

- `_client` is nullable; `getClient`/`setClient` are at
  `Player.java:4085-4098`.
- `getAccountName()` explicitly falls back to `_accountName` when client is
  null (`:1048-1051`).
- `sendPacket(ServerPacket)` silently does nothing when client is null
  (`:4397-4404`).
- `isInOfflineMode()` is true for a null or detached client (`:7979-7982`).
- `isOnline()` is the in-memory boolean, while `isOnlineInt()` returns `0`
  unless both `_isOnline` and a client exist; a detached client maps to `2`
  (`:7897-7911`). A null-client headless player therefore needs an explicit
  online/session policy rather than relying on `isOnlineInt`.
- `startOfflinePlay()` requires a real client long enough to send
  `LeaveWorld` and mark it detached (`:7913-7950`); it is not a generic
  null-client materializer.

### ServerPacket effects

`GameClient.sendPacket` rejects null with a warning, writes bytes, then invokes
`packet.runImpl(_player)` (`GameClient.java:216-230`). `Player.sendPacket`
skips the whole call when no client exists, so it also skips `runImpl`.

The snapshot has four effect-bearing packet families:

1. `AbstractHtmlPacket.runImpl` clears and rebuilds HTML action-validation
   caches (`AbstractHtmlPacket.java:116-134`).
2. `CreatureSay.runImpl` propagates snoop chat
   (`CreatureSay.java:105-112`).
3. `ItemList.runImpl` sends `ExQuestItemList`
   (`ItemList.java:72-79`).
4. `TutorialCloseHtml.runImpl` clears tutorial HTML actions
   (`TutorialCloseHtml.java:37-44`).

HTML cache updates are server-side validation state and cannot be lost. Snoop
propagation is observable server behavior. `ItemList` is a chained visual
packet and must terminate safely in a headless sink. Therefore a sink cannot
be implemented as only “do nothing”; the effect policy must run once without
network serialization.

### Null-client coupling matrix

| Category | Evidence | Result |
|---|---|---|
| Safe at `client == null` | `getAccountName`, `sendPacket`, `isInOfflineMode`, `Disconnection.of(Player)`, most domain APIs | Existing explicit branches support a detached actor. |
| Outbound-only/discardable | status/user-info/list/visual packets where no `runImpl` override exists | Safe to discard after null validation and effect dispatch. Broadcast to other real players must still run through their clients. |
| Requires packet effect | HTML packets, `CreatureSay`, `ItemList`, `TutorialCloseHtml` | Headless output seam must call `runImpl` exactly once. |
| Requires real `GameClient` | login trace/HWID/session state, flood protectors in network handlers, socket close, LoginServer logout | Exclude from headless lifecycle or replace with explicit server-side policy/facade. |
| Unsafe/unclear | `BotReportTable:452`, `VillageMaster:551/612/704`, `AntiFeedManager:87`, and handlers that directly use `getClient()` | Gate each Phantom action; do not claim global null-client safety. |

Direct dereferences outside client packets are few but real. Network handlers
almost universally assume `getClient()` for flood protection/session state.
The safe conclusion is not “make client nullable everywhere”; it is “keep
headless actions out of network handlers and expose narrow domain facades.”

### Tasks and cancellation

`Player` owns scheduled futures for inventory/item/skill/status broadcasts,
dismount, fame, vitality, teleport watchdog, fishing, Nevit/recommendations,
break warnings, charge/soul, rent pet, water, mount feed and falling damage
(`Player.java:471-558`, `:600-724`, `:810-811`).

`stopAllTasks()` cancels many long-lived futures plus quest timers and generic
timer holders (`:15123-15220`). `deleteMe()` also calls the broader
`stopAllTimers()`. The audit found a lifecycle risk: short deferred
inventory/item/skill/broadcast tasks are not all explicitly enumerated by
`stopAllTasks`; Task 004 must assert no retained futures/references after
rollback instead of assuming complete cancellation.

Global managers reduce per-player task count:

- `PlayerAutoSaveTaskManager` uses one 1-second loop over a concurrent map and
  processes at most one due player per pass.
- `PvpFlagTaskManager` and `AttackStanceTaskManager` use shared concurrent
  collections and fixed-rate loops.
- `AutoPlayTaskManager` and `AutoUseTaskManager` create fixed-size player pools
  and one recurring task per pool, not one per player.

These pooled patterns are the local scheduler analog. They still need bounded
pool counts, shutdown ownership and latency metrics for Phantom scale.

## GameClient and network boundary

- `GameClient` extends `Client<Connection<GameClient>>`
  (`GameClient.java:55`).
- Its constructor dereferences `connection.getRemoteAddress()`
  (`:77-81`), so a null connection is immediately invalid.
- It owns flood protectors, session/account/HWID state, a player lock and a
  connection state machine (`:60-75`).
- `onDisconnection()` sends a LoginServer logout, conditionally starts player
  cleanup and marks the connection disconnected (`:90-100`).
- `close(packet)` optionally schedules socket disconnect after sending a
  packet (`:124-143`).
- `GameClient.load` detects an already materialized object through `World`,
  disconnects/deletes the old state, and returns null (`:500-541`).
- `CharacterSelect` acquires `client.getPlayerLock`, loads a player, binds both
  directions, writes online status and changes connection state to `ENTERING`
  (`CharacterSelect.java:190-228`).

Subclassing/faking `GameClient` would inherit network lifecycle, LoginServer
logout, flood/HWID/account assumptions and a constructor requiring a live
connection. A fake/null `Connection` is rejected for Task 004.

### Client handlers containing domain logic

The following are representative server rules currently embedded in request
handlers and must be reached later through server-side facades, not by
constructing packets:

- party: `RequestJoinParty`, `RequestAnswerJoinParty`,
  `RequestOustPartyMember`;
- direct trade: `AnswerTradeRequest`, `TradeDone`, with canonical operations in
  `Player.startTrade/cancelActiveTrade` and `TradeList`;
- private stores: `RequestPrivateStoreBuy/Sell` and the `SetPrivateStore*`
  family;
- NPC commerce: `RequestBuyItem`, `RequestSellItem`, `MultiSellChoose`;
- mail: `RequestSendPost` and attachment handlers;
- combat/actions: `Action`, `AttackRequest`, `RequestMagicSkillUse`, `UseItem`;
- movement: `MoveToLocation`, `ValidatePosition`;
- death/respawn: `RequestRestartPoint`;
- chat: `Say2`.

Handlers combine flood checks, client/session state, validation, mutation and
response packets. Task-specific facades must extract/call canonical domain
operations without bypassing validation or inventory/transaction rules.

## Enter and leave lifecycle

### Existing connected state machine

```text
AUTHENTICATED client
  -> CharacterSelect/client lock
  -> Player.load (online in memory, autosave and vitality task exist)
  -> bind Player <-> GameClient
  -> online DB flag
  -> ConnectionState.ENTERING
  -> EnterWorld (session/LoginServer work + domain initialization)
  -> spawnMe / World registration and visibility
  -> setEnteredWorld
  -> ACTIVE
  -> GameClient.onDisconnection
  -> Disconnection (detach, stopAllTasks)
  -> storeMe -> deleteMe -> world/party/trade/instance/container cleanup
  -> DISCONNECTED/STORED
```

`EnterWorld` is not a reusable server API. It sets client state, sends
LoginServer tracert, emits extensive packets and performs HWID enforcement,
while also doing essential domain work: instance restore, clan linkage, quest
enter notification, skill/item/shortcut setup, spawn, effects, mail notice,
offline-table cleanup and final entered-world flag
(`EnterWorld.java:159-790`).

### Proposed headless state machine

```text
STORED
  -> claim persistent identity
  -> LOADING (Player.load)
  -> MATERIALIZING (headless session/output seam attached)
  -> run explicit server-side materialization steps
  -> spawnMe (World duplicate guard)
  -> ACTIVE
  -> stop new actions
  -> DEMATERIALIZING
  -> detach/cancel/store/delete through canonical cleanup
  -> release identity claim
  -> STORED
```

Every transition is compare-and-set/idempotent. Failure after `Player.load`
must cancel tasks/autosave and clear online state even if spawn never happened.
Failure after spawn additionally removes visibility/world/party/trade/request
and instance ownership. Repeated cleanup must be harmless.

### Identity collision

`World.addObject()` uses `putIfAbsent` for objects and players; on duplicate
player object ID it disconnects/deletes both actors
(`World.java:163-198`). `GameClient.load` also checks `World` before restore.
This is a last-resort safety response, not an ownership protocol: a phantom
must atomically claim character identity before `Player.load`, and real login
must either reject or request bounded dematerialization before binding the
same character. Concurrent “disconnect both” is unacceptable as normal flow.

## Offline play and offline trade evidence

### Offline play

At startup, `GameServer` calls `OfflinePlayTable.restoreOfflinePlayers()` only
when command and restore flags are enabled (`GameServer.java:446-448`).
Restoration loads the canonical player, marks online, spawns, reconstructs
auto-use/autoplay state, marks offline play, persists online status, restores
effects, and later restores parties
(`OfflinePlayTable.java:78-242`). Per-player errors call
`Disconnection.of(player).storeAndDeleteWith(LeaveWorld.STATIC_PACKET)`
(`:181-185`).

Persistence uses `character_offline_play` and
`character_offline_play_group`. Save first deletes existing rows and then
inserts multiple entries under Hikari autocommit
(`:264-347`); group save is similarly multi-statement (`:356-382`).
Crash between statements can leave partial state.

### Offline trade

At startup, `GameServer` calls `OfflineTraderTable.restoreOfflineTraders()`
under its flags (`GameServer.java:441-443`). It loads canonical players,
spawns, restores private-store lists/effects and online status
(`OfflineTraderTable.java:187-327`). Errors use the same `Disconnection`
cleanup path.

Persistence uses `character_offline_trade` and
`character_offline_trade_items`; save clears and reinserts rows with
autocommit (`:69-179`). Realtime updates are synchronized in
`onTransaction` (`:348-468`). Private store transfers are synchronized in
`TradeList.privateStoreBuy/privateStoreSell`, but DB row replacement and
inventory mutations are not one database transaction.

### Conclusion

Offline systems prove that a fully loaded canonical `Player` can remain
spawned and operate after its client is null/detached, and that
`Disconnection.of(Player)` is an established cleanup seam. They do not provide
general session semantics, packet-effect delivery, a materialization lock,
complete enter-world initialization, deterministic scheduling, or atomic
cross-table recovery.

## Gameplay subsystem audit

The detailed matrix is in `DEPENDENCY_MAP.md`. Cross-cutting conclusions are:

- Party, command channel, clan, world, inventory, quest, instance and siege
  objects are canonical server-side state and mostly reusable directly or with
  a thin adapter.
- Trade, stores, commerce, mail, actions, skills, movement and respawn have
  important validation/mutation inside client packet handlers and need
  explicit server facades.
- Persistence is generally repository/DAO-style SQL spread across objects and
  managers; many multi-step operations rely on synchronization and autocommit,
  not a single database transaction.
- Cleanup is distributed. The lifecycle service must call canonical cleanup,
  not duplicate it.

## Build and testability

`build.xml` has Java 25 `compile` and packaging targets only. `jar` copies both
server JARs to `dist/libs`. The observed build passes. There is no automated
test framework/target or CI workflow.

The current DB config is loaded from a fixed relative file by
`DatabaseConfig.load()`, and `DatabaseFactory` initializes Hikari from those
static values. Task 002 must introduce an explicit test bootstrap/config
boundary before factory initialization, plus:

- `test` and `verify`;
- `phantom-scenario-test`;
- `phantom-performance-smoke`;
- fail-fast production-DB name guard;
- negative control proving that guard;
- deterministic seed injection;
- isolated fixture cleanup.

Task 001 does not implement any of these.

## Performance, concurrency and logging risks

- Constructor-started vitality and autosave membership make “loaded but not
  active” non-free.
- Broadcasts iterate visible players and can amplify O(N); party/clan/world
  operations add fan-out. Phantom output-to-self can be discarded, but
  visibility broadcasts to real players cannot.
- `World.addVisibleObject` walks surrounding visible objects and exchanges
  info/AI state; dense materialization can approach quadratic fan-out.
- `Player.storeMe` and container stores produce multiple DB writes; mass
  dematerialization/shutdown requires batching/backpressure.
- Existing shared pools are preferable to per-phantom futures. No
  per-phantom executor is acceptable.
- Diagnostics must use bounded counters, sampled traces and slow-operation
  thresholds; no INFO/WARNING per decision/action/path is acceptable.
- Packet sink recording must have a fixed capacity/drop counter and be disabled
  by default.

## Static database and anti-dup audit

Relevant schemas include `characters`, `items`, `item_attributes`,
`item_elementals`, `item_variables`, character skills/shortcuts/quests/
variables/subclasses/recipe/timers, offline play/trade tables, clan data/
privileges/skills/subpledges/wars, `messages`, siege/fort/clanhall registrants
and `character_instance_time`.

Key risks:

- character load sets online in memory and starts tasks before spawn;
- online DB status and world ownership are separate writes/state transitions;
- item transfer is synchronized on inventories/items but persistence can occur
  later;
- direct/private trade validates under ordered locks, yet crash recovery has
  no Phantom reservation ledger;
- mail attachments and message metadata span item and message persistence;
- offline save/restore uses multiple autocommit statements;
- quest timers and instance membership need explicit cancellation/reconcile;
- shutdown store ordering can overlap active action queues.

Task 004 must use only the isolated Task 002 database and must assert that
failure leaves no online flag, world object, tasks, party/trade/request state or
duplicated item ownership.
