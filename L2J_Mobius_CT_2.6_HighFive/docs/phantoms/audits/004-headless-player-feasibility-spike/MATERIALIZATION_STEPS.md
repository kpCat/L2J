# Explicit materialization steps — Task 004

## Sources and boundary

The selected path is derived from canonical `Player.load`,
`GameClient.load`, `OfflinePlayTable`, `OfflineTraderTable`,
`WorldObject.spawnMe` and `Player.deleteMe`. `EnterWorld.runImpl` is not called,
copied or exposed. Its client-session work is classified below to make the
omission explicit.

Task 004 starts no production materializer. Tests construct
`PhantomPlayerMaterializationSpike` directly while the Task 003 feature remains
disabled by default.

## Selected lifecycle

| Order | Step | Classification | Source and reason |
|---:|---|---|---|
| 1 | reject an existing `World` Player | `REQUIRED_NOW` | `World.addObject` duplicate handling is destructive fallback, not ownership arbitration |
| 2 | acquire tokenized `PHANTOM` identity | `REQUIRED_NOW` | prevents real/phantom and phantom/phantom load races before `Player.load` |
| 3 | repeat the `World` check | `REQUIRED_NOW` | closes the pre-claim race |
| 4 | call canonical `Player.load(objectId)` | `REQUIRED_NOW` | restores real Player, inventory, skills and canonical persistence state |
| 5 | capture fixture item baseline | `REQUIRED_NOW` | makes action rollback and every failure point measurable |
| 6 | repeat the `World` check after load | `REQUIRED_NOW` | duplicate World cleanup must remain last-resort only |
| 7 | attach Player-owned headless outbound | `REQUIRED_NOW` | restores packet effects without a transport |
| 8 | `setRunning`, `standUp`, `refreshOverloaded`, `refreshExpertisePenalty` | `REQUIRED_NOW` | same minimal post-load normalization used by `GameClient.load` |
| 9 | `setOnlineStatus(true, true)` | `REQUIRED_NOW` | deliberate active detached/headless state; offline tables use the same canonical API |
| 10 | `spawnMe()` | `REQUIRED_NOW` | canonical World/region/known-list registration; offline tables use this path |
| 11 | open bounded action admission and enter `ACTIVE` | `REQUIRED_NOW` | prevents action/cleanup overlap from becoming unbounded |
| 12 | close admission and wait for admitted count zero | `REQUIRED_NOW` | cleanup cannot race an admitted inventory mutation |
| 13 | `stopAllTasks` and restore fixture baseline | `REQUIRED_NOW` | cancels constructor/coalescing futures and conserves inventory |
| 14 | `storeMe`, then `deleteMe` | `REQUIRED_NOW` | canonical persistence, online `0`, autosave/World/container cleanup |
| 15 | detach outbound and release identity last | `REQUIRED_NOW` | no new owner can load until cleanup is complete |
| 16 | clear retained Player/lease references | `REQUIRED_NOW` | makes repeated cleanup a no-op and residue observable |

Every completed step has an immediately following deterministic failure
injection point where applicable. The suite covers all eleven points from
`FAILURE_MATRIX.md`, calls cleanup twice, and asserts World, online, autosave,
future, party, trade, request, instance, item, output, lease and thread residue.

## Deferred domain work

| Work | Classification | Reason |
|---|---|---|
| party/clan relationship restoration beyond what `Player.load` already restores | `DEFERRED_SAFE` | not needed for the reversible action |
| quests and `Quest.playerEnter` | `DEFERRED_SAFE` | execution semantics belong to later lifecycle/gameplay work |
| mail, friends, petitions, private stores and trade | `DEFERRED_SAFE` | not needed and explicitly outside the action facade |
| shortcut, macro, henna, recipe UI packets | `DEFERRED_SAFE` | client presentation state |
| spawn protection, cursed-weapon login behavior, faction welcome | `DEFERRED_SAFE` | gameplay policy is outside this feasibility proof |
| autoplay/offline-play restoration | `DEFERRED_SAFE` | not part of Task 004 |
| instance restoration | `DEFERRED_SAFE` | fixture is deliberately in instance `0`; cleanup asserts it |
| `setEnteredWorld` | `DEFERRED_SAFE` | marks completion of the full client EnterWorld path, which is not run |

## Client-session-only work

| Work | Classification |
|---|---|
| `GameClient` connection-state transitions | `CLIENT_SESSION_ONLY` |
| LoginServer tracert and client tracert storage | `CLIENT_SESSION_ONLY` |
| HWID delayed validation and per-HWID limits | `CLIENT_SESSION_ONLY` |
| `UserInfo`, shortcut, action-list, bookmark, friend and welcome packets for the owning client | `CLIENT_SESSION_ONLY` |
| client news, server notices and client close packets | `CLIENT_SESSION_ONLY` |

Observer broadcasts are not client-session-only: they are exercised through
the World-visible headless Player and zero-transport sink.

## Forbidden in Task 004

| Work | Classification | Evidence |
|---|---|---|
| call or copy `EnterWorld.runImpl` | `FORBIDDEN` | materializer contains no `EnterWorld` dependency |
| construct fake/null-network `GameClient` or `Connection` | `FORBIDDEN` | tests instantiate neither type |
| use client packet handlers as Phantom actions | `FORBIDDEN` | `PhantomActionFacade` exposes one server-side inventory method |
| create a Player subclass/fork | `FORBIDDEN` | fixtures assert exact class `Player` |
| initialize `GameServer`, LoginServer, network listener or full script list | `FORBIDDEN` | bootstrap has a fixed 39-entry direct list and effect-master-only script load |
| access production DB | `FORBIDDEN` | bootstrap guards `l2jmobiush5_phantom_test` before Hikari |
| create per-phantom thread/executor | `FORBIDDEN` | lifecycle is synchronous and thread-delta checks are zero |

## Minimal bootstrap exception

Canonical `Player.create` constructs `UserInfo`, and canonical
`Player.deleteMe` requires the full zone registry. Their existing dependency
chain loads castle/territory/clan-hall/zone data plus narrow transitive
singletons. The test bootstrap lists these explicitly; it does not activate
sieges, load the GameServer script list or start any listener. This is still a
bounded subset of GameServer startup and is part of the feasibility evidence.
