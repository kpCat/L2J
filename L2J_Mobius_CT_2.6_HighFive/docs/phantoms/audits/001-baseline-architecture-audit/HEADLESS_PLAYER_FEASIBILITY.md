# HEADLESS PLAYER FEASIBILITY — Task 001

## Gate verdict

`FEASIBLE_WITH_SEAM`

The current code proves that canonical `Player.load -> spawnMe -> operate ->
storeMe/deleteMe` can run with a null or detached client: offline play/trade
restore exactly that shape and use `Disconnection.of(Player)` for failure
cleanup. A large `Player` fork is not required.

It is not plain `FEASIBLE` because `Player.sendPacket` suppresses
`ServerPacket.runImpl` when `_client == null`, `GameClient` cannot safely be
constructed without a real `Connection`, connected `EnterWorld` mixes session
and domain initialization, and selected server paths dereference `getClient`.
Task 004 must validate a small output/session seam before the ADR can be
accepted.

## Minimal seam

Responsibility, not final naming, is fixed:

1. A `Player`-owned outbound/session interface accepts a non-null
   `ServerPacket`.
2. The default adapter delegates to the current bound `GameClient`.
3. The headless adapter performs no network write, runs mandatory packet
   server effects exactly once, and optionally records a fixed-size diagnostic
   summary.
4. `Player.sendPacket` delegates to this seam instead of using the null-client
   branch as semantics.
5. Broadcast APIs remain unchanged so real observers still receive visibility
   packets.
6. A lifecycle service owns identity claim, load, explicit initialization,
   spawn, action admission, store/delete, cleanup and release.
7. `PhantomActionFacade` exposes only validated server actions; client packet
   handlers are not its API.

The seam does not emulate connection state, LoginServer, HWID, flood
protectors, encryption, network close or TCP buffers.

## Probable Task 004 production touch points

No production file is changed by Task 001. The bounded spike should expect to
inspect/change only the smallest confirmed set:

- `java/org/l2jmobius/gameserver/model/actor/Player.java`
  (`_client`, `sendPacket`, session/output attachment and lifecycle-safe
  default);
- `java/org/l2jmobius/gameserver/network/GameClient.java`
  (real-client adapter behavior and current effect ordering; avoid constructor
  changes unless proven necessary);
- `java/org/l2jmobius/gameserver/network/serverpackets/ServerPacket.java`
  (effect dispatch contract/access only if the seam cannot invoke current
  `runImpl` without widening);
- new, narrowly scoped headless output/session and lifecycle spike classes in
  the future Phantom package;
- Task 002 test harness/fixtures, never production DB config.

`EnterWorld`, `CharacterSelect`, `Disconnection`, `World`, offline tables and
action handlers are evidence/test targets first. They are not pre-authorized
for modification. Any need to edit several handler families is a scope failure
and requires a separate task.

## Required invariants

- one persistent character has at most one materialization owner;
- one object ID has at most one `World` actor;
- real login and phantom ownership cannot overlap;
- headless output performs zero socket/network writes;
- null packet is an error;
- required `ServerPacket.runImpl(Player)` executes exactly once;
- visual self-output may be discarded, but broadcasts to real players remain;
- `Player` remains the canonical inventory/skills/party/clan/quest/trade actor;
- no client packet is used as Phantom internal API;
- cleanup is idempotent and bounded;
- failure leaves no world object, online flag, autosave/task, request, trade,
  party or instance residue;
- action admission closes before dematerialization/shutdown;
- scheduler/queues are shared and bounded; no per-phantom executor;
- diagnostics are bounded/off by default and never hot-path INFO/WARNING.

## Alternatives A–F

| Alternative | Blast radius and lifecycle | Packet effects / compatibility | Testability, performance and rollback | Decision |
|---|---|---|---|---|
| A. Fake `GameClient` / fake `Connection` | Inherits connection/session/LoginServer/HWID/flood/close behavior; constructor dereferences remote address; lifecycle pretends a socket exists | Preserves current effect order only by also entering unsafe network code; party/clan may work but network/session failures contaminate all actions | Hard to prove no bytes/close/logout side effects; extra object/state per phantom; rollback touches network boundary | `REJECT` |
| B. Nullable client everywhere | Requires auditing/patching many handlers and direct dereferences; null becomes an implicit session type | Current `Player.sendPacket` loses `runImpl`; handler compatibility remains incomplete and NPE-prone | Wide regression matrix and perpetual null checks; difficult rollback | `REJECT` |
| C. Small output/session seam | Local change around `Player.sendPacket` plus explicit lifecycle; preserves canonical player | Separates transport from effects; facade handles client-bound actions while party/clan/quest/world remain canonical | Narrow deterministic tests; no socket; bounded recording; seam can be reverted to direct client adapter | `ACCEPT` pending Task 004 |
| D. `PhantomPlayer extends Player` | Constructors are private; would require widening and inheritance hooks across a 15k-line stateful class | Subclass cannot safely override all client/persistence/task paths; risks rule divergence | Brittle overrides and large subclass regression surface | `REJECT` |
| E. Fork/copy `Player` | Massive duplicated actor, persistence and cleanup lifecycle | Long-term drift across inventory/party/clan/trade/quest/mail/siege; packet effects duplicated | Unsustainable testing/performance/merge cost; rollback becomes migration | `REJECT` |
| F. NPC Fake Players as final core | Small initial change but wrong identity/lifecycle model | Lacks real player inventory, persistence, party, clan, trade, quest, mail and siege semantics | Fast visual demo but cannot satisfy product goal; no migration-free rollback | `REJECT` |

## Packet effect policy

The default real adapter preserves current order: network write request,
followed by effect dispatch. The headless adapter skips serialization/write and
dispatches the same effect once. A failure in the effect must propagate to the
lifecycle/action result; it must not be logged as a successful send.

For Task 004:

- HTML action-cache mutation is the positive effect test;
- `ItemList -> ExQuestItemList` is the recursion/termination test;
- `CreatureSay` is the observer/snoop test;
- a packet without `runImpl` is the discard test;
- null packet and an effect throwing exception are negative tests.

Longer term, effect behavior should be explicitly classified rather than
inferred from packet names. New `runImpl` overrides require a test/review gate.

## PhantomActionFacade boundary

The facade owns Phantom-specific authorization, activity budget, cancellation
and mapping to canonical server operations. It does not own game rules or
write tables directly.

Initial safe spike surface should be one read/validation action with a
reversible server-side mutation, for example a controlled inventory
add/remove fixture through `Player`/`PlayerInventory` APIs. Trade, mail,
multisell, clan and siege remain unavailable until their individual facades
and conservation tests exist.

## Lifecycle and rollback

```text
STORED
  --claim/load--> MATERIALIZING
  --attach/init/spawn--> ACTIVE
  --stop admission--> DEMATERIALIZING
  --detach/cancel/store/delete/release--> STORED
```

Rollback maintains a stack of completed materialization steps and reverses
only registered steps. It first stops new actions, cancels lifecycle-owned
work, detaches output, clears domain request/trade/party/instance state through
canonical cleanup, removes world visibility/object, stores if safe, clears the
online flag, removes autosave/task ownership and releases identity. A second
cleanup call returns success/no-op.

The production seam must be disabled by default and removable by restoring the
real-client adapter as the only implementation. Task 004 fixture DB changes
must be disposable.

## Unresolved questions for Task 004

- Which exact enter-world domain steps are minimal for the first safe action,
  and which may be deferred?
- Does effect dispatch need a public method on `ServerPacket`, or can the
  package/adapter placement avoid widening?
- Which short `Player` scheduled futures survive current
  `stopAllTasks/deleteMe` in failure paths?
- How should a real login request ownership handoff without using
  “disconnect both” as normal control flow?
- Does `isOnlineInt()` require a session-kind abstraction, or can headless
  online persistence remain intentionally distinct?
- Are any packet `runImpl` overrides discovered dynamically/generated outside
  the four audited source files?

## Task 004 automated spike

With seed `20260725001` and only `l2jmobiush5_phantom_test`:

1. create/load a fixture;
2. claim identity and attach recording headless output;
3. materialize without `GameClient` or TCP;
4. assert inventory/skills and one canonical action;
5. assert no byte write and exactly-once packet effects;
6. assert world/observer visibility;
7. dematerialize and store;
8. repeat cleanup;
9. restart/restore;
10. inject failure after each materialization step;
11. assert no world/task/autosave/online/party/trade/request residue;
12. run real-login collision concurrently;
13. publish bounded task/queue/time measurements.

## Master plan reconsideration condition

Change the gate to `NOT_FEASIBLE_WITHOUT_PLAN_CHANGE` and formally revise the
master plan if Task 004 proves any of:

- canonical `Player` requires a real network connection for essential domain
  state;
- preserving packet effects requires broad packet or handler rewrites;
- idempotent cleanup cannot be achieved without a large `Player` fork;
- identity collision cannot be bounded safely;
- headless operation requires per-phantom threads/unbounded futures;
- the minimal spike cannot pass without production DB access.
