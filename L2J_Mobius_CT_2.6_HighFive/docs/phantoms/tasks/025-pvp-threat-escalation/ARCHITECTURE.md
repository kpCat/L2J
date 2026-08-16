# Goal 025 architecture contract

## 1. Ownership map

Goal025 owns **conflict decision/orchestration**, not server PvP mechanics.

| Concern | Authoritative owner |
|---|---|
| actor materialization / exact Player ownership | existing Phantom materialization |
| attack/cast session, action lease, cleanup | Goal012/012A combat |
| physical PvP legality/execution | canonical `Creature.onForcedAttack` / Player AI |
| skill PvP legality/execution | canonical `Player.useMagic` + Skill |
| CP -> HP damage | canonical Player/PlayerStatus combat |
| natural CP regen | canonical PlayerStatus |
| CP potion use/reuse/consumption | item template + ItemHandler `ItemSkills` |
| PvP flag / PvP kills / PK kills / karma | canonical Player |
| death/drop consequences | canonical Player/death/inventory/config |
| current party and party tactics | Goal017 + canonical Party |
| social/revenge memory | Goal018 |
| warning/help language and dispatch | Goal020 conversation/chat owner |
| farming escalation authority | Goal024 accepted service |
| route planning / movement ownership | existing Goal009 navigation / existing action owner |
| Goal025 | encounter admission, risk policy, escalation stage, cooldown, orchestration |

No second combat, navigation, social, party or chat engine is allowed.

## 2. Required Goal025 kernel

Create one bounded worker-free PvP orchestration package, conceptually
`phantoms/pvp`. Local names may differ, but responsibility must remain exactly
this layer.

Required domain values:

- causal encounter source;
- exact counterpart identity;
- actor/target perceptible risk snapshot;
- canonical PvP legality/risk snapshot;
- escalation stage;
- persisted authority/TTL/cooldown receipt;
- deterministic decision/result reasons;
- bounded metrics without profile/player labels.

Suggested stages:

```text
OBSERVE
WARN
HELP
ENGAGE
RETREAT
DISENGAGE
COOLDOWN
TERMINAL
```

The implementation may collapse stages only if the same invariants and restart
truth remain explicit.

## 3. Causal candidate sources

No general visible-player candidate scan is permitted.

### ACTUAL_ATTACK

Read a bounded exact attacker set from the canonical actor/party-member attack
history. Extend the combat/backend snapshot with Player attackers explicitly;
do not change the meaning of the existing monster attacker list.

Require current exact World object, same instance and current attack/target
causality. Stale attack-list residue must not start a new encounter.

### FARMING_ESCALATION

Add/use a narrow read-only Goal024 evidence seam exposing an exact bilateral
FINAL escalation receipt: pair, resource/agreement identity, authority hash,
causal receipt and expiry. PvP must not read `PhantomFarmingStore` directly and
must not mutate the agreement.

The target must currently resolve to the exact Phantom actor identity before
combat can start. The old causal farming receipt may keep the conflict reason,
but it is not permission to teleport/hunt an absent target.

### PARTY_DEFENSE

Use Goal017 exact current Party. A member’s exact current Player attacker may
become a help target. Extend party tactics with a distinct PvP protection
identity/directive so existing `PROTECT_MEMBER` monster semantics remain pinned.
Max roster remains canonical 9; no recruitment or party creation.

### REVENGE

Goal018 remains the only durable social/revenge memory owner. Goal025 may read
existing exact social history/modifiers for a counterpart and persist only the
current encounter/cooldown receipt. Revenge does not create a search query.
The exact counterpart must already be available from a bounded current source
(e.g. current perceptibility or causal encounter identity).

## 4. Explicit non-sources

The following must never independently create an aggression candidate:

- merely visible Player;
- Player with low HP/CP;
- PvP flag alone;
- karma alone;
- valuable gear;
- occupying a farming room without Goal024 causal conflict;
- random local WorldRegion Player;
- social hostility without an exact currently resolvable counterpart;
- `World.getPlayers()` / global profile registry scan.

## 5. Existing monster combat must remain sealed

Do not broaden current `PhantomCombatBackend.TargetSnapshot` or existing
`attack(int)` / `cast(...)` semantics.

Add an explicit Player-target path, e.g.:

```text
PvpTargetSnapshot / PvpActionAuthority
startPvpSession(...) OR explicit target kind in a new request type
attackPvp(...)
castPvp(...)
```

It must share the **same** `PhantomCombatService` session, action lease, worker,
cancellation, owned-action cleanup and stop barriers.

Acquisition, farming, party-vs-monster and Goal012 tests must be unable to
accidentally route a Player into PvP.

## 6. Canonical physical PvP execution

At each issue boundary:

1. re-resolve exact World object and require `Player`;
2. require exact expected identity/object, actor ownership and same instance;
3. reject dead/alike-dead/invisible/non-targetable/stale/unmanaged event state;
4. require Goal025 current action authority;
5. set/select exact target only as needed by the canonical server seam;
6. invoke canonical target `onForcedAttack(actor)` rather than packet code;
7. observe resulting canonical AI/attack ownership before reporting ISSUED;
8. if canonical code rejects, return unavailable/rejected; never force direct
   damage or bypass the restriction.

Do not instantiate or execute `AttackRequest`.

## 7. Canonical PvP skill execution

Keep monster `PhantomCombatSkillSafety.supports` unchanged.

Add a distinct PvP skill safety contract. At minimum reject:

- passive/toggle;
- unsupported target shapes;
- suicide/GM/7Signs/transformation mismatches;
- skills unknown at the exact required level;
- unsafe AoE/mass targets in Goal025 unless a bounded later source-backed
  extension proves ally safety.

One-target hostile PvP skills may include canonical PvP-only skills if their
real Skill conditions allow them. Do not simply remove the current `pvpOnly`
rejection from the monster route.

Execution uses canonical `Player.useMagic(skill, forceUse, false)`. `forceUse`
is true only when the current Goal025 authority explicitly allows forced
aggression. After call, observe exact cast ownership; do not treat the method
call alone as success.

Do not instantiate or replay `RequestMagicSkillUse`.

## 8. Legality and zone gate

Goal025 policy has a read-only immutable legality snapshot, but canonical action
APIs are the final authority.

Pre-admission must reject at least:

- self target;
- same exact Party;
- dead/alike-dead target;
- wrong instance;
- peace zone;
- unmanaged event/boat/airship/jail/festival constraints discovered by exact
  source audit;
- unrelated Olympiad/duel/siege state that Goal025 does not own;
- stale materialization/identity;
- target not currently resolvable for a new attack.

Do not maintain a copied “complete PvP legality table”. Read authoritative Player
state and let `onForcedAttack` / `useMagic` make the final server decision.

## 9. Aggression doctrine

### Reactive defense

An exact current ACTUAL_ATTACK may engage immediately if canonical gates allow.
No warning delay is required.

### Party defense

An exact current attacker of a same-Party member may engage immediately through
Goal017 -> Goal012 ownership. It must stop when party membership/attacker truth
is stale.

### Proactive farming escalation / revenge

A target that is not currently auto-attackable requires:

- exact stronger causal source (`FARMING_ESCALATION` or source-backed REVENGE);
- current target resolution/perceptibility;
- no friendly/same-party relation;
- current risk threshold permitting engagement;
- persisted WARN stage;
- at least one Goal020-owned warning dispatch attempt/receipt for human-visible
  confrontation when the target can receive that channel;
- policy warning delay elapsed via normal shared pulses (no timer);
- source/authority still current at issue time;
- per-pair engagement budget not exhausted.

A warning failure does not justify a blind forced PK. Fail closed to WAIT,
RETREAT or DISENGAGE unless a new ACTUAL_ATTACK source appears.

## 10. Bounded escalation and anti-meatgrinder

Policy must define explicit finite bounds, loaded from versioned High Five
Phantom data (not scattered literals), including:

```text
observedAttackerLimit <= 32
localRiskPlayerLimit <= 32
encounterTtlSeconds
warningDelaySeconds
pairCooldownSeconds
maxProactiveEngagementsPerPair
helpFanout <= 8
retreatHpPercent
retreatEffectivePoolPercent
engageMinimumStrengthBasisPoints
forcedPkMaximumRiskBasisPoints
```

Recommended initial policy defaults for deterministic tests:

```text
observedAttackerLimit = 16
localRiskPlayerLimit = 24
encounterTtlSeconds = 120
warningDelaySeconds = 5
pairCooldownSeconds = 300
maxProactiveEngagementsPerPair = 1
helpFanout = 8
retreatHpPercent = 25
retreatEffectivePoolPercent = 30
```

Strength/PK thresholds are policy values, not server-law constants; choose
conservative source-documented defaults and report them.

Death, target disappearance, source invalidation, successful retreat, timeout or
explicit disengage closes the encounter. Same-pair proactive re-engagement is
blocked by persisted cooldown. No corpse camping and no respawn chase.

## 11. Strength and perceptual fairness

Do not copy damage formulas or predict exact DPS.

Allowed self facts include exact own current/max HP/MP/CP, class/capabilities,
real potion stock/reuse and current party.

For another Player, the policy should consume human-plausible bounded facts:
current/max values may be used only to derive coarse percent/band observations;
do not expose/use hidden inventory, exact equipment stats, exact skill list or
server-only private information for a superhuman advantage.

Risk may combine:

- own HP/CP effective pool and MP reserve;
- coarse target HP/CP band;
- visible class/capability category;
- current nearby party/allied support count;
- exact recent attack source;
- Goal018 relation/escalation evidence;
- actor karma/PK/drop exposure.

Any local WorldRegion scan is capped and used only for risk/support context, not
victim discovery.

## 12. CP doctrine

Canonical CP is not a Phantom resource ledger.

- damage truth is observed from Player/PlayerStatus;
- natural regen is observed, never scheduled by Goal025;
- no production `setCurrentCp`, direct CP effect, free potion or synthetic
  inventory;
- source-audit exact High Five CP Potion / Greater CP Potion data;
- consume only an owned inventory object;
- call the registered `ItemSkills` handler;
- handler owns reuse, Olympiad restriction, skill condition and item destruction;
- after use, observe inventory count/reuse/CP change before recording success;
- if unavailable/reuse/olympiad rejects, continue policy without fabrication.

Potion selection may prefer the smallest sufficient canonical potion or the
configured economic policy, but must account for actual stock and consumption.

## 13. Karma/PK/drop risk

Expose a read-only snapshot from canonical actor/config state. It may include:

- actor PvP flag;
- actor karma;
- PvP/PK kill counts;
- target current auto-attackable/karma/PvP relation;
- PVP normal/PvP flag durations;
- minimum PK required to become equipment-drop eligible;
- configured karma drop limit/chance buckets;
- actor inventory exposure only as a coarse count/value category if needed.

The snapshot is **decision risk only**. Actual outcome after a hit/kill is
observed from Player. Phantom code must not call direct `updatePvPStatus`,
`increasePkKillsAndKarma`, set karma, or invoke Player drop logic.

A probabilistic drop is never reported as certain before death.

## 14. Warning and help language

Add a narrow typed Goal020-owned outbound semantic seam for at least:

```text
PVP_WARNING
PVP_HELP_REQUEST
PVP_DISENGAGE
```

Goal025 supplies structured facts/reason/target, not arbitrary direct chat
packets. Goal020 owns text/catalog/channel and dispatches through the existing
`ChatHandler` + `ChatObservationService` path.

For Phantom-to-Phantom internal coordination, typed acts may be consumed without
rendering text unless a real Player can observe the channel.

No runtime LLM and no direct `ChatHandler` call from the PvP package.

## 15. Party help

Extend Goal017 only through an explicit PvP attacker/directive distinction.
Existing monster `PROTECT_MEMBER` behavior remains exact.

Help must require:

- exact current Party membership/generation;
- exact member under current Player attack;
- bounded roster/fanout;
- helper not already owned by a conflicting higher-priority action;
- Goal012 combat action lease;
- same canonical PvP action gates as solo defense.

No party creation, invite or formal alliance logic.

## 16. Retreat

Goal025 chooses **whether** to retreat; it does not implement pathfinding or raw
movement.

Use existing navigation/action ownership. Source-audit the currently accepted
route execution path and create only a narrow adapter if no typed retreat seam
exists. A retreat target must be a current safe topology/navigation point or
bounded direct point proven by existing navigation policy; no impossible
teleport and no direct XYZ mutation.

Before retreat, cancel/reconcile only Goal025-owned combat action via Goal012
cleanup. Foreign party/combat/navigation work is not cancelled.

## 17. Persistence and restart

Use the generic profile component store; no new SQL expected.

Persist only bounded encounter truth needed for restart/idempotency:

- exact causal source;
- exact counterpart identity type + id;
- source authority hash;
- warning/help receipt ids;
- escalation stage;
- proactive engagement count;
- created/expiry/cooldown time;
- last terminal reason;
- social delivery mask if needed.

Do not duplicate Goal018 relationship/revenge model in this component.

After GameServer restart:

- human object-only encounters do not blindly resume aggression; require a fresh
  current causal/perception source;
- Phantom profile counterpart may be re-resolved by exact profile ID, but no
  attack starts until current Player identity/materialization and action gates
  are proven;
- cooldown survives restart;
- stale authority fails closed.

## 18. Lifecycle and concurrency

No new executor, thread, per-profile Future or timer.

Goal025 orchestration runs through existing Decision/shared pulses. The existing
Combat worker remains the only combat worker. Navigation remains its existing
bounded owner.

At most one active Goal025 encounter per profile. Pair operations use stable ID
ordering if two persisted Phantom profiles must be updated. No lock may be held
across combat, social, conversation, navigation or DB boundary calls.

Shutdown order must close new Goal025 admissions before dependent combat/
conversation/navigation teardown and leave no runtime encounter/action residue.

## 19. Disabled behavior

With Phantom disabled:

- no Goal025 service/state/worker/DB query;
- no player scan;
- no chat warning;
- no PvP action;
- ordinary Player PvP/PK/karma behavior is byte-for-byte behaviorally unchanged.

## 20. Forbidden shortcuts

- no second combat engine;
- no copied damage/CP/karma/drop formulas;
- no direct HP/MP/CP/karma/PvPFlag/PK/PvP kill mutation;
- no Phantom inventory drop implementation;
- no direct item destruction for CP potion;
- no packet simulation / ClientPacket construction;
- no direct chat packet or direct `ChatHandler` from PvP package;
- no weakening of existing normal-monster target gate;
- no `World.getPlayers()` / global profile scan;
- no cold aggression from visibility/PvP flag/karma alone;
- no other chronicle;
- no production DB;
- no `.l2j` mutation;
- no Goal027 clan/alliance/war ownership.
