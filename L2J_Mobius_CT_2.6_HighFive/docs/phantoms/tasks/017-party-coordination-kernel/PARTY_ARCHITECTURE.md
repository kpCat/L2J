# Party architecture contract

## 1. Dependency chain

```text
canonical Player/Party/current world facts
+ progression capability facts
+ topology/navigation facts
        ↓
immutable party/member/role/route snapshots
        ↓
party objective, vacancies and typed semantic acts
        ↓
Utility AI candidates / exact party steps
        ↓
shared canonical invitation/membership/action services
```

Facts never decide tactical desirability. Party doctrine never mutates the
progression catalog.

## 2. Canonical invitation service

Extract a responsibility-equivalent transport-neutral service under
`model/groups` and make both packet handlers delegate to it.

Required operations:

```text
invite(requestor, target, distribution, delivery context)
respond(invitee, response, exact invite identity)
cancel/expire(exact invite identity)
leave/expel/transfer leader
observe exact current request/party truth
```

The service owns all current validation and immediate revalidation before
membership mutation. It returns typed outcomes for every validation branch.

A small generic managed-invite delivery port is allowed:

```text
default/noop or client delivery
one installed Phantom sink before GameServer start
removed/drained on Phantom shutdown
```

Core code depends only on that generic port. It must not import Phantom party
classes.

Delivery behavior:

- real target: preserve `AskJoinParty`, request fields and ordinary packet flow;
- managed Phantom target: preserve canonical transaction/pending fields but
  deliver a typed inbound invite to the coordinator instead of requiring a
  client answer packet;
- Phantom inviting a real player uses the ordinary client prompt;
- canonical `Party` methods may emit ordinary party UI packets to real and
  headless sessions; no fake `GameClient` and no packet-handler invocation.

On accept, revalidate exact inviter, invitee, current request owner, deadline,
party leader, capacity, distribution, instance/event/Rift restrictions and
membership before `joinParty`. Cancellation and timeout clear both sides and
`Party.pendingInvitation` exactly once.

## 3. Durable party intent

Use existing components only:

```text
component: party.state
schema: 1
payload <=4096
```

At most nine roster entries.

Each Phantom claim stores bounded immutable facts:

```text
groupId SHA-256 and groupGeneration
membershipRevision
status
leader ref
own role assignment
leader manifest hash
exact Phantom member profile refs
ephemeral real-character refs
group objective ref
role requirements and assignments
route manifest/ref/generation
pending invite/operation identity and phase
current progression/topology hashes
last typed failure
```

Leader state contains the manifest; members contain matching claims. Real
players never receive a Phantom component.

States:

```text
SOLO
FORMING
INVITED_OUTBOUND
INVITED_INBOUND
JOINING
LEADER
MEMBER
LEAVING
RECOVERING
RETIRED
INCONSISTENT
```

Operation phases:

```text
PREPARED
CANONICAL_PENDING
CANONICAL_OBSERVED
COMMITTED
ABORTED
```

Stable operation key includes:

```text
groupId, generation, membershipRevision
operation kind
leader/member refs
leader goal ID/revision
manifest hash
```

One operation per group and one party claim per profile. No service monitor
across DB, World, Player, Party, combat or navigation calls.

## 4. Cross-profile saga and restart

Formation order:

```text
leader manifest PREPARED
→ Phantom member claim PREPARED, when applicable
→ exact canonical invite
→ exact response
→ observe canonical Party roster/leader/distribution
→ member claim COMMITTED
→ leader manifest COMMITTED
```

Every step is idempotent and optimistic. Partial states reconcile; unknown
claims/rows become INCONSISTENT. Do not claim cross-profile ACID.

Restart rules:

- canonical Party objects are assumed absent after GameServer restart;
- a Phantom-only committed group enters RECOVERING, materializes exact claims,
  elects/revalidates a leader, and rebuilds one canonical Party;
- real-player membership or consent is never recreated after restart;
- real refs are removed into vacancies and require a new invite/accept;
- stale generation, goal revision, manifest or roster cannot mutate;
- leader loss elects a deterministic eligible Phantom from committed claims,
  increments generation, or disbands to SOLO;
- canonical auto-leader change is observed and reconciled, not overwritten
  blindly;
- disconnect/dematerialization is not treated as voluntary refusal.

## 5. Explicit goals and consent

Supported goal types:

```text
party.form
party.join
party.lead
party.member
party.travel
party.leave
```

No global matchmaking or World scan.

- leader formation goal names exact candidate `profile` or
  `character.object` refs and explicit generic role requirements;
- a Phantom invitee accepts only with an exact matching current `party.join`
  goal; otherwise it refuses or expires;
- an unrelated active goal is never overwritten;
- after commit, exact matching goals transition optimistically to
  `party.lead`/`party.member`;
- on leave/disband they finish/fail with typed reasons; no hidden restoration of
  an old goal;
- real players consent only through canonical current invitation response.

## 6. Roles and vacancies

Create data-driven generic role semantics, not fixed class roles:

```text
frontline/tank       -> combat.tank and defensive/current-state evidence
heal                 -> combat.heal
resurrection         -> combat.resurrection
recharge             -> combat.recharge
buff                 -> combat.buff
song                 -> combat.song
dance                -> combat.dance
control              -> combat.crowd_control / combat.debuff
melee damage         -> combat.melee_damage
ranged physical      -> combat.ranged_physical_damage
magic damage         -> combat.ranged_magic_damage
area damage          -> combat.aoe_damage
summon support       -> combat.summon and controlled-actor facts
spoil/sweep          -> profession.spoil / profession.sweep
```

Store mappings and bounded weights in:

```text
dist/game/data/phantoms/party/high-five-party-roles-v1.xml
```

The XML is strict, XXE-safe, hashed and contains no universal mandatory party
composition. The explicit group objective supplies required/optional vacancies.

Matching:

- deterministic maximum-nine bipartite assignment;
- one actor exposes several capabilities simultaneously;
- assigning a primary role never erases secondary capabilities;
- READY_NOW, learned/intrinsic state, resources, equipment, HP/MP/death,
  distance and group objective affect suitability;
- active subclass is isolated from main-class capabilities;
- equal coarse archetype does not make actors identical;
- real-player capabilities are copied from exact current Player skills/equipment,
  never inferred from a class-name table;
- output includes filled, missing, optional and unsupported vacancies with
  provenance.

Generic objective modes:

```text
GENERAL_PVE
AREA_PVE
RECOVERY
TRAVEL
```

Rift, raid and epic requirements are forbidden here.

## 7. Semantic acts

Create a language-independent extensible record with a validated string act key,
not a growing central enum:

```text
actKey, actor ref, target ref, groupId/generation
reason key, confidence, bounded domain/numeric slots, provenance
```

Party keys include:

```text
party.invite
party.accept
party.refuse
party.leave
party.expel
party.transfer_leader
party.assign_role
party.set_objective
party.set_route
party.regroup
party.assist
party.protect
party.heal
party.recharge
party.resurrect
party.support
party.retreat
```

No generated text, parsing, LLM or phrase bank. A semantic act is intent, never
proof that a canonical action occurred.

## 8. Shared route and formation

Leader owns one immutable route manifest:

```text
routeId/generation
topology and navigation authority hashes
destination anchor/ref
bounded waypoints
current waypoint
regroup radius and maximum separation
status PLANNING/MOVING/REGROUPING/ARRIVED/FAILED
```

Followers never choose an independent final destination.

Use existing navigation service and progress tracker. Party backend may issue
canonical `MOVE_TO` under exact materialization ActionLease; no new pathfinder,
thread or timer.

Rules:

- leader route is planned first;
- member steps target the shared waypoint or current leader position;
- dead/casting/combat/teleporting/instance-mismatch members hold or recover;
- maximum separation triggers REGROUPING;
- no coordinate snap, free teleport or cross-instance following;
- partial progress remains at observed canonical position;
- topology/navigation hash drift invalidates the route;
- cancellation releases movement ownership;
- real leader route is observation-only: Phantoms follow current real leader,
  but never persist a claim that the real player accepted a route;
- no background party travel or party farming in Goal 017.

## 9. Tactical directives and action ownership

Party priority resolver produces exact directives:

```text
ASSIST_TARGET
PROTECT_MEMBER
HEAL_MEMBER
RECHARGE_MEMBER
RESURRECT_MEMBER
PARTY_SUPPORT
HOLD
REGROUP
RETREAT
```

Priority inputs include member HP/MP/CP/death, attackers, leader target,
distance, instance, role assignment and READY_NOW capability variants.

Execution:

- assist/protect may start existing combat only against an exact supported
  normal-monster target;
- no PvP or attacks against players;
- heal/recharge/resurrection/buff/song/dance use exact progression capability
  variant/action skill and target scope;
- do not “use every buff/song/dance”;
- resurrection requires exact dead party member;
- support actions revalidate Party membership, instance, range, skill,
  resources, reuse and conditions.

Extend combat ownership with a bounded external-action lease:

```text
PARTY_SUPPORT
PARTY_ROUTE
```

It shares the same per-profile exclusion as combat sessions and respawn.
Combat cannot start while an external party action is owned, and party action
cannot start during combat/respawn. Lease timeout, cancellation, shutdown and
failure release ownership. No new worker; issue/await is driven by ordinary
decision retries.

The combat backend may gain immutable playable-member snapshots and safe support
cast/move operations. Mutable Player/Party objects never escape an ActionLease.

## 10. Scheduler, lifecycle and observability

Compose existing scheduler control ports in one immutable bounded chain
(population then party), installed before scheduler start. Maximum eight ports;
each port failure is isolated and counted. No second scheduler task.

Party control uses a bounded due/retry heap. One pulse processes no more than
`PhantomPartyOperationsPerPulse` actions. Add strict config default `64`,
range `1..10000`; disabled system remains inert.

Startup:

```text
materialization/topology/knowledge/progression/combat/navigation/population
→ party stores/service/coordinator
→ register all candidates/handlers
→ decision engine
→ composed scheduler control
→ scheduler
→ population and party start
```

Shutdown:

```text
party.beginStop rejects invites/routes/support
→ scheduler/decision admission stop
→ party drains group ops, DB claims, actor leases, external actions
→ materialization/background shutdown
→ party.finishStop zero-only
```

System snapshot exposes groups, members, pending invites, vacancies, routes,
operations, DB claims, actor/external leases and bounded role/status histograms.

Production path with no party components/goals/invites does no party DB scan
after bounded startup and emits no periodic logs.
