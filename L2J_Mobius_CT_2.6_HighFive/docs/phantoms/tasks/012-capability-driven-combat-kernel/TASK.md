# GOAL 012 — Capability-driven combat kernel

## 1. Identifier

- **Goal ID:** `012-capability-driven-combat-kernel`
- **Roadmap stage:** III — Solo gameplay, progression and causal background
- **Branch:** `feature/phantom-world`
- **Accepted baseline:** `003604b4f7bda2a8d224d0adcf6349c088154e10`
- **Parent:** `dc4659fea3e76a78841dfee0429bc4ab1ed2b185`
- **Git root:** `C:\Users\endim\L2J_Mobius\`
- **Only module:** `C:\Users\endim\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\`
- **Test DB:** `l2jmobiush5_phantom_test`
- **Production DB:** `l2jmobiush5` — never use during Codex execution
- **Seed:** `20260725001`
- **Codex model:** Sol
- **Effort:** Very High

## 2. Accepted gates

```text
Stage I: COMPLETE
Stage II: COMPLETE

Goal 007 / 007A: ACCEPT
Goal 008 / 008A: ACCEPT
Goal 009 / 009A: ACCEPT
Goal 010 / 010A / 010B / 010C: ACCEPT
Goal 011: ACCEPT after Goal 011A
Goal 011A: ACCEPT
Goal 012: ALLOWED
Goal 013: NOT_STARTED
Goal 014: NOT_STARTED
```

Goal 011A accepted facts:

```text
Commit: 003604b4f7bda2a8d224d0adcf6349c088154e10
Parent: dc4659fea3e76a78841dfee0429bc4ab1ed2b185
Push/remote: exact
Core: 50/50 ×3
Parity: 21/21 ×2
Query truth: 13/13 ×3
Content: 18/18 ×3
Performance: 8/8 ×2
Performance summary SHA-256:
5567CA820C858419E5AFF418B4F893479916523FBEFB1F2E765434C1D77582B5
Verifier: 63/63 ×2, byte-identical
Verifier SHA-256:
6E7DF9745D070D83B48306C148EC58E08953C1894BC6B75842D9F46E962FBAA4
Independent verdict: ACCEPT
```

The 203 user-owned untracked geodata files are not task artifacts. Preserve
them exactly and never stage them.

## 3. Result

After Goal 012 the Phantom subsystem owns a bounded, server-side combat kernel
capable of a real solo combat lifecycle for a deliberately limited generic
archetype matrix:

```text
explicit normal-monster target
→ canonical attack or selected offensive skill
→ optional canonical shots if already owned
→ HP/MP and threat observation
→ target death
→ bounded canonical ground-item pickup
→ clean session completion

player death
→ combat cancellation/cleanup
→ explicit normal-town respawn command
```

The kernel exposes generic capability interfaces rather than one script per
class.

Production remains inert:

```text
registered combat candidates = 0
automatic combat sessions = 0
automatic target scans = 0
automatic respawns = 0
combat sessions at startup = 0
combat shared workers at startup = 0
```

Only explicit service calls or registered decision step handlers can start a
session. No production candidate is added in Goal 012.

## 4. Scope boundary

Goal 012 owns:

- bounded per-profile combat-session ownership;
- one opaque canonical actor lease per active session;
- explicit normal-monster target validation;
- a pure bounded threat table;
- generic melee/ranged-physical/ranged-magic loadout resolution;
- canonical PlayerAI attack/cast/pickup intentions;
- optional canonical shot use from inventory;
- HP/MP/death observations;
- target-death transition;
- bounded solo loot pickup;
- explicit normal-town respawn;
- async plan-cancellation ownership;
- decision step handlers;
- fixed metrics and diagnostics;
- real server integration evidence.

Goal 012 does not own:

- target farming policy or automatic target acquisition;
- a production Utility AI candidate;
- full High Five class catalog;
- class progression or skill learning;
- equipment selection;
- buying supplies;
- party combat or healing another player;
- spoil/manor/crafting;
- PvP, Olympiad, siege, event or raid policy;
- background combat simulation;
- natural-language/Semantic Pack;
- direct HP, damage, inventory or EXP mutation.

## 5. Mandatory reading

Read fully:

1. roadmap, master plan, `Agents.md`, workflow/package/report standards;
2. Goals 006B, 008A, 009A, 010C, 011A packages/reports/reviews/contracts;
3. current:
   - `PhantomMaterializationService`;
   - `PhantomMaterializedPlayer` and `ActionLease`;
   - decision engine/context/result/plan/handler registry;
   - accepted Game Knowledge query/model/service;
   - `Player`, `Creature`, `Attackable`, `Monster`;
   - `PlayerAI`, `CreatureAI`, `Intention`;
   - `SkillData`, `Skill`, skill target types;
   - shot item handlers and auto-use implementation;
   - `Inventory`, world `Item`, canonical pickup path;
   - death/revive and `RequestRestartPoint` normal-town path;
   - `World`, known-list APIs, zones and instances;
   - current PhantomSystem startup/shutdown;
4. every file in this package.

Do not modify another chronicle.

## 6. Initial Git audit

```bat
git rev-parse --show-toplevel
git status --short --branch
git remote -v
git fetch origin --prune
git rev-parse HEAD
git rev-parse origin/feature/phantom-world
git show --stat --oneline 003604b4f7bda2a8d224d0adcf6349c088154e10
git diff --name-status dc4659fea3e76a78841dfee0429bc4ab1ed2b185..003604b4f7bda2a8d224d0adcf6349c088154e10
```

Expected:

```text
HEAD == origin/feature/phantom-world == 003604b4...
```

The Goal 012 package is expected untracked. The user-owned
`dist/game/data/geodata/*.l2j` files are also expected untracked and must be
ignored, preserved and excluded from all staging.

Return `BLOCKED_BASELINE_DRIFT` for any other unreviewed production/config/schema
drift.

## 7. Close Goal 011A

Update:

```text
docs/phantoms/reports/011a-knowledge-parity-query-truth.md
```

Add immutable handoff:

```text
Commit: 003604b4f7bda2a8d224d0adcf6349c088154e10
Parent: dc4659fea3e76a78841dfee0429bc4ab1ed2b185
Push/remote: exact
Core: 50/50 ×3
Parity: 21/21 ×2
Query truth: 13/13 ×3
Content: 18/18 ×3
Performance: 8/8 ×2
Performance SHA-256:
5567CA820C858419E5AFF418B4F893479916523FBEFB1F2E765434C1D77582B5
Final verifier: 63/63 ×2, byte-identical
Verifier SHA-256:
6E7DF9745D070D83B48306C148EC58E08953C1894BC6B75842D9F46E962FBAA4
Independent review: ACCEPT
Goal 011: ACCEPT after Goal 011A
Stage II: COMPLETE
Goal 012: ALLOWED
```

Create:

```text
docs/phantoms/reviews/011a-knowledge-parity-query-truth-review.md
```

Verdict:

```text
Goal 011: ACCEPT after Goal 011A
Goal 011A: ACCEPT
Revert: NOT_REQUIRED
Stage II: COMPLETE
Goal 012: ALLOWED
Goal 013: NOT_STARTED
```

Roadmap progress only:

- accepted baseline becomes `003604b4...`;
- Goal 011/011A become `ACCEPT`;
- Stage II becomes `COMPLETE`;
- Goal 012 becomes `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`;
- Goal 013/014 remain `NOT_STARTED`;
- do not rewrite future Goal architecture.

## 8. Production package

Create:

```text
java/org/l2jmobius/gameserver/phantoms/combat/**
```

Responsibility-equivalent types:

```text
PhantomCombatService
PhantomCombatPolicy
PhantomCombatRequest
PhantomCombatSession
PhantomCombatSessionSnapshot
PhantomCombatResult
PhantomCombatMode
PhantomCombatPhase
PhantomCombatLoadout
PhantomCombatCapabilityResolver
PhantomCombatThreatTable
PhantomCombatBackend
L2jCombatBackend
PhantomCombatActorLease
PhantomCombatStepHandlers
PhantomCombatMetrics
```

Small immutable records/enums/helpers are allowed.

Only the production adapter/opaque actor lease may retain the canonical
materialization `ActionLease` and its `Player`. No public request, result,
snapshot, threat, loadout or session-value type may expose `Player`, `Creature`,
`WorldObject`, `Skill`, `Item`, AI objects, packets or mutable server
collections.

No global static combat API.

## 9. Fixed policy

Compile-time defaults, no new config keys:

```text
maximum sessions                    = max scheduled Phantom profiles
maximum sessions processed/pulse    = 64
maximum threat entries/session      = 32
maximum selected skills/loadout     = 4
maximum observed attackers/pulse    = 16
maximum loot candidates/pulse       = 32
maximum remembered loot IDs/session = 64
maximum acquisition distance        = 2000
maximum loot distance               = 300
pulse interval                      = 250 ms
default combat timeout              = 30000 ms
maximum combat timeout              = 120000 ms
loot timeout                         = 5000 ms
low-HP stop threshold                = 15%
minimum MP reserve                   = 10%
maximum terminal retention           = one slot/profile
maximum shared combat workers        = 1
```

Use overflow-safe distance/time arithmetic.

No internal authoritative collection is silently truncated except explicitly
documented bounded observations:

- attackers observed per pulse;
- loot candidates per pulse;
- public threat snapshot;
- remembered loot IDs;
- target area summaries inherited from Game Knowledge.

## 10. Combat modes and capability resolution

Supported production modes:

```text
MELEE_PHYSICAL
RANGED_PHYSICAL
RANGED_MAGIC
```

Mapped knowledge capabilities:

```text
MELEE_PHYSICAL          -> combat.melee_damage
RANGED_PHYSICAL         -> combat.ranged_physical_damage
RANGED_MAGIC            -> combat.ranged_magic_damage
```

No production class-ID switch statement.

At session start:

1. acquire canonical actor facts;
2. query accepted Game Knowledge for the actor class;
3. require the mapped curated capability;
4. copy at most four evidence skill IDs in deterministic rank/ID order;
5. intersect with skills actually known by the actor;
6. validate each actual `Skill` through the server adapter;
7. produce one immutable loadout.

Supported selected skill contract:

- actor owns the exact skill/level;
- active, non-passive, non-toggle;
- offensive;
- target is one hostile Creature;
- not ground, party, clan, corpse, item, summon-control or area-target skill;
- no force-use against a player;
- no transformation-only or special-event bypass;
- MP and reuse checks remain canonical;
- unsupported evidence is ignored, never force-cast.

Physical modes may fall back to canonical normal attack.

`RANGED_MAGIC` without a valid known supported offensive skill is rejected as
`UNSUPPORTED_LOADOUT`; it must not pretend that a weapon swing is a magic
capability.

Goal 013 later broadens class/equipment/skill coverage.

## 11. Explicit request contract

A combat start request contains only bounded immutable data equivalent to:

```text
profileId
targetObjectId
mode
useShotsIfAvailable
lootAfterVictory
timeoutMillis
planOwnershipToken
```

The decision handler decodes:

```text
actionKey: combat.start
target namespace: world.object
target key: positive decimal object ID

numeric arguments:
mode      1=MELEE_PHYSICAL, 2=RANGED_PHYSICAL, 3=RANGED_MAGIC
shots    0/1
loot     0/1
timeout  optional 1000..120000
```

Unknown argument, namespace, mode or extra semantic is rejected.

One active or unconsumed terminal session per profile. A new start:

- returns the existing identical session idempotently;
- rejects a different target/request while the old session is active;
- may replace only a consumed terminal session.

No implicit world scan chooses a target.

## 12. Plan-scoped asynchronous cancellation

Goal 012 introduces the first handler that continues work after its synchronous
`execute()` returns.

The existing cancellation token currently changes on detach/stop/goal
replacement, but ordinary replan, retry exhaustion, timeout and successful plan
completion can discard a plan without invalidating the token.

Fix this generically and narrowly.

Required invariant:

```text
a PhantomStepContext cancellation token is valid only while its exact plan
execution remains owned by the same runtime slot
```

It becomes cancelled on:

- detach;
- runtime stop;
- activity generation replacement;
- goal insert/replace/clear/reload ownership changes;
- plan timeout;
- step timeout;
- handler REPLAN;
- retry exhaustion;
- handler CANCELLED;
- terminal goal result;
- successful final plan completion;
- any replacement of the exact plan.

It remains current across successful advancement from one step to the next in
the same plan.

Implementation may use a separate plan-ownership generation or an exact safe
equivalent. Do not weaken persistence-operation generation truth.

Mandatory tests prove:

- same-plan next step token remains valid;
- final plan completion cancels;
- REPLAN cancels;
- retry exhaustion cancels;
- total timeout cancels;
- step timeout cancels;
- detach/stop/goal replacement still cancel;
- stale handler result cannot cancel a newer plan.

## 13. Session ownership and actor lease

`PhantomCombatService.startSession()`:

1. validates service/request/capacity under its monitor;
2. reserves one profile/session generation;
3. releases the monitor;
4. opens one opaque actor lease through `PhantomCombatBackend`;
5. resolves loadout and validates target;
6. reconciles exact session ownership;
7. publishes `ENGAGING` only when all ownership is current;
8. schedules the shared transient pulse worker.

The actor lease wraps exactly one:

```text
PhantomMaterializationService.tryAcquireAction(profileId)
```

and therefore holds canonical materialization action ownership for the full
session.

Properties:

- no backend/server call under combat service monitor;
- no session stores a mutable target object, only object ID;
- target is resolved fresh through the lease/backend when needed;
- lease closes exactly once on every terminal/failure/cancel/stop path;
- dematerialization waits while a combat lease is active;
- no session is published after cancellation/STOPPING;
- no actor lease can remain after terminal session cleanup;
- terminal outcome is retained as immutable data only.

## 14. Target validation

Goal 012 supports only explicit normal server monsters.

A valid target must be:

- an existing `Attackable`/normal Monster object;
- represented by Game Knowledge as `NpcKind.MONSTER`;
- alive, targetable, attackable and mortal;
- not a Player, summon, pet, trap, guard, door or fake player;
- not RaidBoss or GrandBoss;
- not an event/siege/Olympiad/duel special target;
- not invulnerable;
- same instance as actor;
- in a surrounding/known-valid server region;
- outside peace-zone restrictions;
- within maximum acquisition distance at start.

The production adapter must use canonical server predicates. It must not infer
validity from display name or class name.

If the target leaves the instance, is deleted, becomes forbidden or exceeds a
bounded lost-target distance, the session stops cleanly with a typed result.

PvP is always forbidden in Goal 012.

## 15. Threat table

Implement a pure bounded deterministic table.

Each entry:

```text
targetObjectId
threatValue
lastObservedLogicalNanos
explicitTarget
```

Rules:

- at most 32 entries;
- positive finite bounded threat;
- overflow-safe saturating addition;
- deterministic decay by supplied logical time;
- explicit target begins with fixed base threat;
- adapter may provide at most 16 already-known attackers per pulse;
- forbidden targets never enter;
- highest threat wins; tie by explicit target then object ID;
- no player/raid retarget;
- evict lowest decayed threat, then oldest, then highest object ID;
- no timer/task inside the threat table.

Goal 012 may remain pinned to the explicit target unless it becomes invalid.
The threat model is nevertheless real and reusable for Goal 013+.

## 16. Canonical combat actions

Detailed facade contract: `CANONICAL_FACADES.md`.

### Attack

Use canonical Player AI intention:

```text
PlayerAI / CreatureAI ATTACK intention
```

The server AI remains responsible for:

- moving into physical range;
- attack timing;
- geodata/LoS;
- peace-zone checks;
- weapon/arrows/MP checks;
- hit/miss/crit/damage;
- attack tasks and target death.

Do not call damage formulas or mutate HP.

Do not repeatedly reset the same already-owned attack intention every pulse.

### Skill

Use canonical CAST intention with the exact server `Skill`.

The adapter performs preflight observations only; canonical cast logic remains
authoritative for movement/range/MP/reuse/target validation.

Do not call skill effects directly and do not mutate MP.

### Cancellation

When a session ends, cancel only action ownership belonging to that session:

- set AI to IDLE/ACTIVE through canonical API as appropriate;
- abort owned attack/cast;
- clear attack/cast/current target only if it is the exact session target or
  selected skill;
- never cancel a foreign/newer target or unrelated action;
- then close the actor lease.

A session generation/reconciliation check is required around cleanup.

### No packets

Combat production code must not:

- import/instantiate client packets;
- import/instantiate server packets;
- call packet handlers;
- simulate `RequestActionUse`, `UseItem`, attack, skill, pickup or restart
  packets;
- call `sendPacket` directly.

Canonical server methods may internally broadcast packets to ordinary observers;
the headless actor's outbound session remains the accepted lifecycle boundary.

## 17. Shots

Shot policy is only:

```text
NONE
USE_IF_AVAILABLE
```

No purchase or creation.

For physical modes use the factual matching soulshot family; for magic use the
factual spiritshot/blessed-spiritshot route selected by current inventory and
grade.

Requirements:

- invoke the existing canonical server-side item handler/auto-use mechanism;
- validate grade/type/count through existing logic;
- consume the exact canonical inventory amount;
- never set charged-shot state directly without item consumption;
- never construct a client packet;
- no negative inventory or duplicate charge;
- no shot available → continue without shot and record a fixed outcome;
- unsupported canonical route → fail the Goal rather than inventing a shortcut.

Test inventory conservation before/after canonical activation and attack/cast
discharge.

## 18. Pulse service

One shared transient pulse owner:

- existing `ThreadPool` only;
- no raw thread/new executor;
- no per-profile ScheduledFuture/task;
- maximum one scheduled/running combat pulse worker;
- zero worker when there are no active sessions;
- each pass processes at most 64 due sessions;
- backend calls occur outside service monitor;
- one profile can be enqueued at most once;
- dispatch failure rolls back exact worker claim;
- beginStop and dispatch share an ordering gate;
- no dispatch begins after STOPPING.

A pulse:

1. verifies session generation and plan token;
2. obtains actor/target snapshots;
3. handles actor death;
4. handles target death/loss;
5. checks timeout and low HP;
6. updates bounded threat observations;
7. selects skill or normal attack;
8. optionally charges a shot canonically;
9. issues at most one new canonical intention/action transition;
10. records progress and next due time;
11. reconciles exact session generation.

No blocking sleep in a worker.

## 19. HP/MP and combat state

Actor snapshot contains bounded facts equivalent to:

```text
objectId
classId
instanceId
current/max HP
current/max MP
dead/alikeDead
attacking/casting/moving
current target object ID
AI intention
current skill ID/level
known selected skills
weapon family/grade
```

Target snapshot contains:

```text
objectId
npcId
instanceId
current/max HP
dead/alikeDead
targetable/attackable/invulnerable
normalMonster/raid/grandboss
distance
inside peace restriction
```

Service behavior:

- actor dead → `PLAYER_DEAD`;
- target dead → `LOOTING` or `VICTORY`;
- actor HP <=15% → `LOW_HP_STOPPED`;
- magic skill not usable due MP/reuse → deterministic next skill or wait/fallback
  according to mode;
- no direct healing, potion or retreat movement;
- target/actor progress is observed, never fabricated.

## 20. Loot

After a normal-monster victory and only when requested:

- scan only actor-known world `Item` objects;
- same instance;
- within 300 units;
- at most 32 candidates per pulse;
- only items canonically eligible for the solo actor;
- remember at most 64 attempted object IDs;
- issue canonical PlayerAI `PICK_UP` intention;
- never directly add/remove inventory items;
- never pick another player's protected item;
- canonical inventory capacity/weight/ownership checks remain authoritative;
- no party distribution;
- stop after 5 seconds or when no eligible item remains.

Results distinguish:

```text
VICTORY
VICTORY_LOOTED
VICTORY_LOOT_PARTIAL
VICTORY_LOOT_BLOCKED
```

A failed pickup cannot duplicate the item or loop unboundedly.

## 21. Death and normal-town respawn

Combat session detecting actor death:

- cancels owned combat intention;
- releases actor lease;
- retains `PLAYER_DEAD` terminal result;
- does not auto-respawn.

Register explicit handler/API:

```text
combat.respawn_town
```

Only the normal-world default town path is supported.

Preconditions:

- exact materialized actor;
- actually dead and `canRevive`;
- not fake death;
- not jailed;
- not festival/event participant;
- not Olympiad/duel;
- not siege special-respawn context;
- not in a special instance;
- not already pending revive.

Canonical behavior equivalent to the ordinary default restart point:

```text
MapRegionData.getTeleToLocation(player, TOWN)
set instance 0
clear Seven Signs dungeon flag
set pending revive
teleToLocation(location, true)
```

Do not instantiate/call `RequestRestartPoint`.

Special resurrection points, self-resurrection items, clan hall, castle, fort,
siege flag and event rules are rejected in Goal 012.

The handler returns typed retry/replan/success according to observed canonical
state, without direct HP restoration.

## 22. Decision step handlers

Register before the handler registry is sealed:

```text
combat.start
combat.await
combat.cancel
combat.respawn_town
```

No combat candidate is registered.

### combat.start

- decode request;
- use the exact context plan cancellation token;
- start or idempotently find the matching session;
- `SUCCESS` means session ownership accepted, not victory;
- rejection maps to fixed RETRY/REPLAN/CANCELLED reasons.

### combat.await

- ACTIVE/ENGAGING/FIGHTING/LOOTING → bounded RETRY;
- VICTORY/VICTORY_LOOTED/PARTIAL/BLOCKED → SUCCESS;
- PLAYER_DEAD/LOW_HP/TIMEOUT/TARGET_LOST/BACKEND_FAILURE → REPLAN;
- plan token cancelled → CANCELLED;
- consuming terminal result removes its retained slot.

Retry delay is computed so a plan step with up to ten attempts can cover its
bounded step timeout. Do not poll faster than 250 ms.

### combat.cancel

Cancels exact current profile session and returns only after ownership is
scheduled for cleanup.

### combat.respawn_town

Invokes the canonical restricted respawn facade under an action lease.

## 23. Service stop and PhantomSystem integration

Construction/start order:

```text
repository
→ materialization
→ construct combat service and register handlers
→ decision engine
→ navigation
→ topology
→ Game Knowledge
→ combat.start
→ scheduler
→ RUNNING
```

Combat construction must not acquire a Player, create a worker or query world.

Combat receives Game Knowledge through a supplier/port that becomes active only
after knowledge startup.

Nonproduction/inert Phantom path:

```text
empty/noop combat backend
zero sessions
zero worker
same handler keys may exist for regression wiring
```

Disabled Phantom path creates no combat service.

Shutdown order:

```text
scheduler.beginStop
→ decision.beginStop          (invalidates plan tokens)
→ combat.beginStop
→ knowledge.beginStop
→ topology.beginStop
→ navigation.beginStop
→ combat.finishStop
→ only after combat STOPPED: materialization shutdown/drain
→ scheduler.finishStop
→ knowledge.finishStop
→ topology.finishStop
→ decision.finishStop
→ navigation.finishStop
```

If combat has an action lease/session/worker still owned:

- `combat.finishStop()` returns false;
- PhantomSystem remains `FAILED`;
- materialization drain does not start yet;
- later server-level retry finishes combat first;
- no shared `ThreadPool.shutdown()` occurs while combat ownership is hidden.

Extend bounded configured shutdown diagnostics with:

```text
combatState
combatActiveSessions
combatTerminalSessions
combatQueuedSessions
combatWorkers
combatActorLeases
```

No profile IDs, target IDs, positions or skill IDs in aggregate logs.

## 24. Metrics

Fixed aggregate counters only:

- sessions requested/accepted/rejected/current/peak;
- actor lease acquired/rejected/released/current;
- target accepted/rejected/lost;
- pulses/worker dispatch/dispatch failure;
- threat observations/evictions;
- normal attacks issued;
- skill casts issued/rejected;
- shots activated/unavailable/failure;
- player deaths/target deaths;
- loot candidates/pickups/success/blocked;
- cancellations/timeouts/backend failures;
- respawn requested/accepted/rejected/completed;
- stop failures.

No dynamic profile/NPC/skill labels.

Session snapshot is bounded and contains IDs only when explicitly queried by the
internal service API; aggregate system diagnostics contain only counts.


## 25. Tests

Create:

```text
test/java/org/l2jmobius/tests/phantoms/PhantomCombatCoreSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomCombatOwnershipSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomCombatServerIntegrationSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomCombatPerformanceSuite.java
```

Launcher modes and Ant targets:

```text
combat-core
phantom-combat-core-test

combat-ownership
phantom-combat-ownership-test

combat-server-integration
phantom-combat-server-integration-test

combat-performance
phantom-combat-performance-smoke
```

### 25.1. Core: at least 38 cases

Use deterministic fake backend/clock/dispatcher.

Cover:

- request/mode/domain/argument validation;
- policy/session/queue bounds;
- one session/profile and idempotent start;
- actor lease acquired/released once;
- lease failure and reconciliation;
- target normal-monster validation;
- player/raid/grandboss/invulnerable/peace/different-instance rejection;
- acquisition distance;
- capability resolution without class switch;
- selected-skill ownership/type validation;
- physical fallback and unsupported magic loadout;
- HP/MP thresholds;
- attack/skill action coalescing;
- shot available/unavailable/failure;
- threat addition/decay/selection/eviction/overflow;
- actor death, target death, target loss;
- loot candidate/attempt bounds and terminal variants;
- timeout;
- cancellation token;
- terminal retention/consume;
- backend exception isolation;
- dispatch failure;
- one shared worker;
- stop quiescence and lease release.

### 25.2. Ownership: at least 16 cases

Cover generic decision cancellation semantics:

- same-plan step advancement preserves token;
- final plan completion cancels;
- handler REPLAN cancels;
- retry exhaustion cancels;
- total plan timeout cancels;
- step timeout cancels;
- handler CANCELLED cancels;
- terminal COMPLETE_GOAL/FAIL_GOAL cancels;
- detach cancels;
- runtime stop cancels;
- activity-generation replacement cancels;
- goal replace/reload/persistence ownership cancels;
- stale result cannot cancel a newer plan;
- combat start as final step self-cancels;
- decision stop before combat stop releases session;
- no regression in existing synchronous handler behavior.

### 25.3. Real server integration: at least 10 cases

Use only `l2jmobiush5_phantom_test`.

Initialize the existing headless/materialization test environment and existing
shared ThreadPool.

Create isolated test-owned world fixtures. Do not alter datapack sources.

Mandatory real cases:

1. materialized actor lease is the exact World Player;
2. normal Monster within immediate range is attacked through PlayerAI and dies
   through canonical hit/damage tasks;
3. one selected supported offensive skill is issued through canonical CAST and
   produces an observable canonical cast effect/state;
4. canonical shot activation consumes the exact inventory count and attack/cast
   discharges the charge;
5. no shot available does not fabricate charge or inventory;
6. a test-owned ground item is picked up through PlayerAI and world/inventory
   conservation holds;
7. player target and RaidBoss/GrandBoss targets are rejected;
8. cancellation during attack/cast stops only the owned target/action;
9. player death produces `PLAYER_DEAD`, releases lease and leaves no session
   worker ownership;
10. restricted normal-town respawn uses canonical teleport/revive behavior;
11. dematerialization cannot pass an active combat lease and succeeds after
    combat cancellation;
12. no client packet class is constructed by combat production code.

To avoid geodata dependency:

- place actor and target within attack/cast/pickup range;
- do not require pathfinding;
- do not depend on the user's untracked geodata;
- use bounded timeouts and deterministic low-HP test targets;
- test setup may alter only test-owned actor/monster HP/inventory, never
  production combat code.

If a deterministic real skill/shot fixture cannot be created without modifying
server core/datapack, return `BLOCKED_CANONICAL_COMBAT_FIXTURE`; do not replace
the proof with direct HP/MP/inventory mutation inside production code.

### 25.4. Static facade proof

Verifier and focused tests must prove combat production package:

- imports no client/server packet class;
- calls no `sendPacket`;
- calls no direct `setCurrentHp`, `reduceCurrentHp`, damage formula or drop
  calculator;
- calls no direct inventory add/remove/destroy for loot or shot fabrication;
- uses materialization `tryAcquireAction`;
- uses canonical AI ATTACK/CAST/PICK_UP;
- uses canonical shot handler/auto-use path;
- uses canonical restricted town teleport/revive path;
- stores no mutable target object in session values.

### 25.5. Performance

Run twice with byte-identical canonical summary:

```text
10000 fake session starts/completions
100000 combat pulses
100000 threat updates/selections
10000 cancellations
```

Structural gates:

```text
sessions <= configured capacity
queued profile IDs <= session capacity
shared workers <=1
threat entries/session <=32
selected skills <=4
loot observations/pulse <=32
remembered loot IDs/session <=64
actor leases after run =0
terminal slots after consume =0
no thread/Future/session task
```

Elapsed is evidence only. Focused timeout <=120 seconds.

### 25.6. Cumulative regressions

Repeat:

- combat core ×3;
- combat ownership ×3;
- combat server integration ×2;
- combat performance ×2;
- knowledge core 50/50 ×3;
- knowledge parity 21/21 ×2;
- knowledge query truth 13/13 ×3;
- knowledge content/performance;
- topology signal/generation/perception/core/corpus/performance;
- navigation core/performance;
- decision core/persistence/performance;
- activity scheduler/materialization/shutdown;
- headless/profile/DB/harness/skeleton;
- cumulative verify/jar.

## 26. Exact scope

Allowed production:

```text
java/org/l2jmobius/gameserver/phantoms/combat/**
java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java
java/org/l2jmobius/gameserver/phantoms/PhantomMetrics.java
java/org/l2jmobius/gameserver/Shutdown.java
java/org/l2jmobius/gameserver/phantoms/decision/PhantomDecisionEngine.java
```

One minimal compile-only change to:

```text
java/org/l2jmobius/gameserver/phantoms/decision/PhantomStepContext.java
java/org/l2jmobius/gameserver/phantoms/decision/PhantomCancellationToken.java
```

is allowed only if required for exact plan-ownership semantics. Prefer preserving
the existing public handler shape.

Do not modify Player, Creature, AI, Skill, Item, Inventory, World, loaders,
materialization lifecycle or Game Knowledge production semantics.

Allowed tests/build:

```text
build.xml
test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java
test/java/org/l2jmobius/tests/phantoms/PhantomCombatCoreSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomCombatOwnershipSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomCombatServerIntegrationSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomCombatPerformanceSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomDecisionCoreSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomServerShutdownHandoffSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomSkeletonSuite.java
tools/phantoms/verify-task-012.ps1
```

Minimal compile/regression adjustments to existing Phantom tests are allowed
only for decision-token/System snapshot changes.

Allowed documentation:

```text
docs/PHANTOM_BOTS_ROADMAP.md
docs/phantoms/architecture/COMBAT_KERNEL_CONTRACT.md
docs/phantoms/tasks/012-capability-driven-combat-kernel/**
docs/phantoms/reports/011a-knowledge-parity-query-truth.md
docs/phantoms/reports/012-capability-driven-combat-kernel.md
docs/phantoms/reviews/011a-knowledge-parity-query-truth-review.md
```

## 27. Hard out of scope

Forbidden:

- any datapack, curated knowledge, geodata or config change;
- DB schema/migration/profile persistence changes;
- Player/Creature/AI/Skill/Item/Inventory/World core changes;
- navigation/topology/knowledge behavior changes;
- production combat candidate or automatic target scan;
- PvP, party combat, raid/epic combat;
- healing other players, spoil, manor or crafting;
- progression/class/equipment catalog;
- commerce or shot purchasing;
- direct HP/MP/damage/EXP/inventory mutation in production combat;
- client packet simulation;
- full class switch/one script per class;
- new executor/raw thread/per-profile Future/task;
- production DB;
- Goal 013/014;
- other chronicles/dependencies/CI/mass formatting;
- amend/rebase/merge/force push.

## 28. Static verifier

Create deterministic read-only:

```text
tools/phantoms/verify-task-012.ps1
```

Verify:

- base `003604b4...`, one ordinary exact-scope commit;
- 203 user geodata files excluded;
- no datapack/config/schema/Goal 013/014;
- Player/Creature/AI/Skill/Item/Inventory/World/loaders/materialization/knowledge
  sources frozen;
- combat package and fixed policy bounds;
- no packet imports/construction/sendPacket;
- no direct damage/HP/MP/inventory/EXP mutation;
- materialization action lease is mandatory;
- no mutable target in session values;
- one session/profile;
- one shared worker and no per-profile task/Future;
- exact session/dispatch/stop reconciliation;
- plan token invalidation on every required plan terminal boundary;
- token preserved within same plan;
- normal-monster-only target restrictions;
- knowledge capability resolution with no class switch;
- canonical AI attack/cast/pickup;
- canonical shot handler and inventory conservation tests;
- town respawn restrictions and no restart packet;
- loot/threat/session bounds;
- combat handlers registered, candidate registry remains zero;
- production startup zero sessions/workers;
- shutdown before materialization drain and aggregate diagnostics;
- focused/real/performance/Ant tests;
- Goal 011A closure and roadmap progress;
- UTF-8, mojibake, escaped Cyrillic;
- no credentials/binaries;
- verifier deterministic/read-only.

## 29. Documentation

Create:

```text
docs/phantoms/architecture/COMBAT_KERNEL_CONTRACT.md
docs/phantoms/reports/012-capability-driven-combat-kernel.md
```

Contract sections:

- authority and canonical facade boundary;
- materialization/action lease ownership;
- plan-scoped cancellation;
- session/worker lifecycle;
- target restrictions;
- capability/loadout resolution;
- attack/cast/shots;
- threat and HP/MP;
- loot;
- death/town respawn;
- handlers;
- startup/shutdown;
- bounds/metrics;
- explicit exclusions/limitations.

Report sections:

- status/baseline;
- Goal 011A closure;
- factual Player/AI/skill/shot/pickup/respawn audit;
- package/architecture;
- decision token hardening;
- session and action lease ownership;
- target/threat;
- archetype/loadout matrix;
- canonical attacks/skills/shots;
- HP/MP/death;
- loot;
- town respawn;
- handler wiring/no candidates;
- system startup/shutdown;
- metrics/diagnostics;
- fake core/ownership/real integration/performance;
- all regressions;
- production DB safety;
- static verifier;
- scope/deviations/limitations;
- branch/parent/subject;
- manual gate `PENDING_INDEPENDENT_REVIEW`;
- Goal 013/014 `NOT_STARTED`.

Use external handoff wording for self commit/push evidence.

## 30. Commands

Pre-change:

```bat
ant verify
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-011a.ps1
```

Targeted:

```bat
ant compile-tests
ant phantom-combat-core-test
ant phantom-combat-ownership-test
ant phantom-combat-server-integration-test
ant phantom-combat-performance-smoke
ant phantom-game-knowledge-core-test
ant phantom-game-knowledge-parity-test
ant phantom-game-knowledge-query-truth-test
ant phantom-game-knowledge-content-test
ant phantom-game-knowledge-performance-smoke
ant phantom-topology-scheduler-signal-integration-test
ant phantom-topology-signal-ledger-test
ant phantom-topology-generation-test
ant phantom-topology-perception-test
ant phantom-topology-core-test
ant phantom-topology-production-corpus-test
ant phantom-topology-performance-smoke
ant phantom-navigation-core-test
ant phantom-navigation-performance-smoke
ant phantom-server-shutdown-handoff-test
ant phantom-decision-core-test
ant phantom-decision-persistence-test
ant phantom-decision-performance-smoke
ant phantom-activity-scheduler-test
ant phantom-production-materialization-test
ant phantom-headless-player-test
ant phantom-profile-persistence-test
ant phantom-db-test
ant test
ant phantom-skeleton-test
```

Repeat:

```bat
ant phantom-combat-core-test
ant phantom-combat-core-test
ant phantom-combat-core-test

ant phantom-combat-ownership-test
ant phantom-combat-ownership-test
ant phantom-combat-ownership-test

ant phantom-combat-server-integration-test
ant phantom-combat-server-integration-test

ant phantom-combat-performance-smoke
ant phantom-combat-performance-smoke
```

Then repeat all Goal 011A focused routes required by §25.6.

Full:

```bat
ant verify
ant jar
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-012.ps1
git diff --check
git status --short --branch
```

Post-commit:

```bat
git rev-parse HEAD
git show --stat --oneline --decorate HEAD
git diff --check 003604b4f7bda2a8d224d0adcf6349c088154e10...HEAD
ant verify
ant jar
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-012.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-012.ps1
git push origin feature/phantom-world
git ls-remote --heads origin feature/phantom-world
git status --short --branch
```

Compare final verifier outputs byte-for-byte/SHA-256 outside the repository.

## 31. Acceptance result

Successful result:

```text
CAPABILITY_DRIVEN_COMBAT_KERNEL_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
```

Do not self-accept Goal 012 and do not start Goal 013/014.

## 32. Commit/push

Commit subject:

```text
feat(phantoms): add capability driven combat kernel
```

One ordinary commit on top of `003604b4...`.

Push regardless of SUCCESS/BLOCKED, using only safe scoped artifacts.

## 33. Blocking behavior

Return `BLOCKED` if:

- canonical attack/cast/pickup/shot/respawn requires modifying server core;
- a real skill or shot integration fixture cannot be proven safely;
- action leases cannot remain exact through async session ownership;
- plan cancellation cannot be made exact without breaking accepted persistence
  semantics;
- combat shutdown can race materialization drain;
- Goal 013/config/schema/datapack changes are required;
- production DB is accessed;
- cumulative verify/jar fails.

On blocker:

- remove unsafe production wiring;
- preserve safe factual audit/model/tests/report/verifier;
- ordinary commit/push;
- keep Goal 013/014 not started.

## 34. Final handoff

```text
Status:
Architecture result:
Baseline:
Goal 011A closure:
Stage II:
Plan cancellation ownership:
Same-plan token preservation:
Plan terminal token invalidation:
Combat service state:
Session capacity:
Shared workers:
Production startup sessions/workers:
Action lease ownership:
Normal-monster target validation:
PvP/raid rejection:
Threat table:
Melee loadout:
Ranged physical loadout:
Ranged magic loadout:
Selected skill validation:
Canonical physical attack:
Canonical skill cast:
Canonical shots:
Shot inventory conservation:
HP/MP observation:
Player death:
Target death:
Loot pickup:
Loot conservation:
Combat cancellation:
Normal-town respawn:
Decision handlers:
Production candidates:
Combat shutdown/materialization ordering:
Aggregate shutdown diagnostics:
Core tests:
Ownership tests:
Real server integration:
Performance:
Two performance runs / summary SHA:
Knowledge/topology/navigation/decision/scheduler/lifecycle regressions:
All prior suites:
ant verify:
ant jar:
Static verifier pre:
Static verifier final 1:
Static verifier final 2:
Outputs identical:
Production DB:
Production JAR combat entries:
Production JAR test entries:
Commit:
Parent:
Branch:
Push:
Remote ref:
Report:
Manual gate:
Goal 013:
Goal 014:
Limitations/blockers:
```
