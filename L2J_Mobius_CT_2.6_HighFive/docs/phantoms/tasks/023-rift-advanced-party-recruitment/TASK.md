# Goal 023 — Rift readiness and advanced party recruitment

## 1. Git and accepted baseline

```text
branch: feature/phantom-world
required parent: 1c8c99f83ebc9f32ac2c3bc670aec506b8efcccb
test DB only: l2jmobiush5_phantom_test
production DB forbidden: l2jmobiush5
deterministic Goal seed: 23002301
commit subject: feat(phantoms): add rift readiness and advanced party recruitment
success token: GOAL_023_RIFT_ADVANCED_PARTY_RECRUITMENT_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
```

Create exactly one ordinary child of `1c8c99f83ebc9f32ac2c3bc670aec506b8efcccb` and push it to
`origin/feature/phantom-world`.

Do not amend, rebase, squash, merge, reset, force push or force-with-lease.

Goal 023 is one coherent Goal. Do not pre-create 023A/023B or a second
checkpoint. A follow-up is allowed only after an independent review finds a
specific defect.

Goal 024+ must not start.

## 2. Historical closure before Goal 023 code

Before implementation:

1. create `docs/phantoms/reviews/022-final-review.md`;
2. pin `1c8c99f83ebc9f32ac2c3bc670aec506b8efcccb`;
3. record exactly:

```text
Goal 022 Checkpoint 1:
ACCEPT_WITH_EXPLICIT_UNRELATED_TIMING_FLAKE_WAIVER

Goal 022 Checkpoint 2:
ACCEPT

Goal 022 overall:
ACCEPT

Accepted Goal 022 baseline:
1c8c99f83ebc9f32ac2c3bc670aec506b8efcccb
```

4. retain the C1 waiver truth: do not claim its historical plain verify passed;
5. record that final Goal 022 terminal child passed its fourth and final
   independently authorized full `ant verify`;
6. make verifier 022c2 historical and descendant-compatible, reading accepted
   blobs from `1c8c99f83ebc9f32ac2c3bc670aec506b8efcccb`;
7. update roadmap/master-plan Goal 022 status to `ACCEPT` and Goal 023 to
   `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW` only after Goal 023 gates pass.

Dependencies accepted for Goal 023:

```text
Goal 010 topology/perception
Goal 013 progression/class capabilities
Goal 017 party coordination kernel
Goal 020 conversation/action execution
Goal 022 economy (accepted baseline above)
```

Goal 022 is not a functional dependency of Rift composition in the roadmap, but
its accepted baseline is the branch parent and its supply/economy truth may be
queried read-only.

## 3. Product result

A Phantom party can answer, from current server facts:

```text
Which Dimensional Rift tier are we preparing for?
Can the current canonical party enter/attempt it?
Who is actually in the party?
Is the party already full?
Which required/optional combat roles are filled?
Which roles are missing?
Which current member fills each role, and why?
Which members are not ready because of level, death, vitals, equipment,
supplies, location or travel?
Which exact nearby candidate can fill the highest-priority missing role?
Should we invite, wait, refuse, replan or declare READY?
```

The answer must derive from:

```text
current canonical Party roster
current member class/capability facts
current inventories/equipment/vitals
current topology/navigation facts
current Rift server configuration/data
current accepted role catalog
current invitation/refusal state
```

Never from a prewritten phrase or a guessed composition.

Terminal readiness states:

```text
NEEDS_PARTY
NEEDS_ROLE
NEEDS_MEMBER_READY
NEEDS_SUPPLIES
NEEDS_TRAVEL
INVITE_PENDING
READY_TO_ENTER
BLOCKED
STALE
```

Goal 023 does **not** implement Rift combat/room jumping, raid/epic
orchestration, PvP/PK, farming-spot ownership or a replacement Party kernel.

## 4. Current authority that remains canonical

### Party lifecycle

Goal 017 remains owner of:

```text
PartyInvitationService
invite / accept / refuse
leader/member lifecycle
durable party claims
role matching primitives
shared party routes
assist/protect/heal tactics
real + Phantom member representation
```

Reuse:

```text
PhantomPartyCoordinator
L2jPhantomPartyBackend
PhantomPartyRoleCatalog
PhantomPartyRoleMatcher
PhantomPartyRouteCoordinator
PhantomPartyModel
```

Do not create another persistent party roster or invitation state machine.

`PhantomPartyModel.MAX_ROSTER = 9` remains the upper roster bound.

### Member/class capability truth

Goal 013/017 current capability and member snapshot APIs remain authoritative.

Do not infer role from class name strings.

A member fills a Rift role only through the existing
`RoleRequirement → RoleMatcher → MemberCapability` path.

### Rift content

Current High Five source facts include:

```text
dist/game/data/DimensionalRift.xml
dist/game/data/xsd/DimensionalRift.xsd
GeneralConfig RIFT_* settings
current Rift entry/runtime manager or NPC/script owner
current NPC/item data referenced by Rift content
```

The XSD permits six areas and rooms 1..9. The current data has the six factual
Rift tiers beginning with RECRUITS, PRIVATES, OFFICERS, CAPTAINS, COMMANDERS and
the final tier represented by the current data.

Current default config includes:

```text
RiftMinPartySize = 5
MaxRiftJumps = 4
RiftSpawnDelay = 10000
AutoJumpsDelayMin = 480
AutoJumpsDelayMax = 600
BossRoomTimeMultiply = 1.5

RecruitCost = 18
SoldierCost = 21
OfficerCost = 24
CaptainCost = 27
CommanderCost = 30
HeroCost = 33
```

These defaults are not immutable gameplay constants. Runtime current config and
the canonical Rift entry code are authoritative.

Do not assume the entry item ID, exact entrance NPC, level range, room ownership
or entry bypass from memory. Discover them from current High Five code/data and
freeze their provenance/hash in the Goal 023 report.

## 5. Bounded current-code audit

Initial READ_SET:

1. this task package;
2. Goal 022 final report/review/verifier;
3. Goal 017 party architecture/report and current party package;
4. `PhantomPartyCoordinator`;
5. `L2jPhantomPartyBackend`;
6. `PhantomPartyRoleCatalog`, matcher and role XML;
7. `PhantomPartyModel`;
8. `PhantomPartyRouteCoordinator`;
9. `DimensionalRift.xml` and XSD;
10. `GeneralConfig` Rift fields and `General.ini` Rift section;
11. current canonical Rift entry/runtime owner(s), found by exact symbol search.

The Rift audit is permitted to locate exact current files using bounded source
search for:

```text
RIFT_MIN_PARTY_SIZE
RIFT_ENTER_COST_
DimensionalRift.xml
DimensionalRift
RiftRoom
isInDimensionalRift
getDimensionalRift
```

After discovery, list every opened additional production file in the report with
one sentence explaining why. Maximum 24 additional exact production files.

Do not scan every quest, instance, NPC script or other chronicle.

## 6. Strict Rift catalog

Create a strict immutable content layer under:

```text
java/org/l2jmobius/gameserver/phantoms/rift/**
```

Preferred responsibilities:

```text
PhantomRiftCatalog
PhantomRiftPolicy
PhantomRiftReadinessService
PhantomRiftRecruitmentDecision
```

Names may vary locally.

Create one Phantom-only policy file:

```text
dist/game/data/phantoms/rift/high-five-rift-policy-v1.xml
```

It may define **AI composition policy only**:

```text
Rift tier → role requirements
readiness thresholds
candidate search bounds
invite/refusal cooldowns
travel/regroup tolerances
supply thresholds
```

It must not duplicate factual spawn lists or canonical server entry costs.

### Factual catalog

Build immutable factual Rift facts from the current sources:

```text
tier/type ID
current source name/key
room IDs
mob IDs/counts
boss-room evidence where source supports it
NPC level range derived from current NPC templates
entry cost from current runtime config
exact entry item/currency ID from current entry authority
minimum party size
jump/timing config facts
canonical destination/entry identity where source supports it
source hashes
```

If a factual field cannot be proved from current source, represent it as
unsupported/unknown and fail closed. Do not invent it.

Strict parser requirements:

```text
ordered canonical serialization
XXE-safe
bounded XML bytes
closed-world elements/attributes
duplicate rejection
content-addressed source hash
current GeneralConfig authority hash
```

No per-profile full scan of `DimensionalRift.xml`.

## 7. Rift policy: composition

Policy role keys must reference the accepted Goal 017 role catalog exactly.

Allowed existing keys include:

```text
frontline.guardian
damage.melee
damage.ranged
support.healer
support.recharge
support.enhancement
control.specialist
```

Do not create class names as roles.

The initial High Five policy must contain one explicit composition template per
factual Rift tier. Requirements can scale by factual mob level/threat but must
remain bounded by a nine-person canonical party.

Each policy requirement:

```text
vacancy key
role key
required/optional
minimum RoleMatcher score
minimum count
priority
```

Do not allow one member to satisfy two simultaneous mandatory seats unless the
existing RoleMatcher contract explicitly supports that assignment without
double-counting.

The policy should prefer a viable diverse group; it is not required to force
nine members for every tier.

## 8. Canonical roster snapshot

Add one Rift readiness snapshot constructed from the live canonical Party.

Inputs:

```text
leader/member MemberRef
PartySnapshot from Goal 017 backend
MemberSnapshot for every current member
party distribution
party size
leader identity
membership revision/evidence hash
```

Rules:

- canonical Party roster is the truth;
- Goal 017 durable claims are coordination state, not a substitute for the
  current Party;
- include both Phantom and real players;
- deduplicate by character object ID;
- reject more than 9;
- stale/missing member snapshot makes that member unready;
- party-size/full-party detection is based on current canonical roster;
- a full party never issues another invite.

Evidence hash includes ordered member IDs, class IDs, capability evidence,
instance/location and progression hashes.

Any roster change invalidates previous role/readiness/recruitment output.

## 9. Member readiness

For each member compute typed readiness without mutating them.

Required dimensions:

```text
LEVEL
ALIVE
VITALS
INSTANCE
EQUIPMENT
CAPABILITIES
SUPPLIES
TRAVEL
```

### Level

Use factual Rift tier level evidence derived from current mobs/entry code.

Do not invent a level bracket when only mob levels are known. The policy may
apply a documented conservative offset/range to the factual mob-level envelope.

### Alive/vitals

Policy thresholds are percentages and bounded.

Dead member is never READY.

### Equipment

Use Goal 013 current equipped-item facts/scoring. Do not directly equip here.

The check answers whether the member has a usable combat loadout for their
assigned role. It must be explainable by exact slots/items/class and accepted
progression/equipment authority.

### Capabilities

Use current MemberSnapshot/Goal 013 capability facts and RoleMatcher evidence.

### Supplies

Read-only current inventory facts.

Initial supply families may include only current factual supported consumables
already represented by accepted commerce/combat/economy capabilities, e.g.:

```text
shots required by equipped weapon/capability
HP/MP/CP consumables when current policy supports them
Rift entry item/currency
```

No purchase, crafting or transfer in Goal 023.

Return exact deficits so later accepted economy/acquisition goals can solve
them.

### Travel

Ready only when member is in the same canonical party instance and within the
policy regroup/travel envelope for the Rift entry destination.

Use topology/navigation read-only feasibility. Do not teleport directly.

## 10. Party readiness result

Immutable result:

```text
tier
canonical roster
full-party flag
minimum-party-size satisfied
role assignments
required vacancies
optional vacancies
per-member readiness
entry-cost readiness
travel readiness
overall status
reason keys
source/evidence hashes
```

Overall ordering:

```text
STALE
BLOCKED
NEEDS_PARTY
NEEDS_ROLE
NEEDS_MEMBER_READY
NEEDS_SUPPLIES
NEEDS_TRAVEL
INVITE_PENDING
READY_TO_ENTER
```

`READY_TO_ENTER` requires:

```text
canonical roster still exact
minimum party size met
all mandatory role seats filled
all assigned mandatory members ready
entry resources sufficient for the canonical entry contract
party not over max roster
no pending conflicting party operation
party gathered at the exact entry/travel readiness boundary
```

Goal 023 does not consume entry items or enter the Rift.

## 11. Advanced recruitment

Add one content-specific recruitment layer that delegates mutations to Goal 017.

It may:

```text
evaluate missing roles
discover bounded candidates
rank candidates
request one invite
observe acceptance/refusal/timeout
re-evaluate roster
declare READY
```

It may not:

```text
startTrade
startParty directly
mutate Party members directly
forge real-player acceptance
auto-accept for ordinary real players
send client packets
create another invitation service
```

### Candidate discovery

Candidate source order:

1. already known Phantom profiles in bounded perceptible/local topology;
2. real visible players already in World and perceptible to the leader;
3. no global online-player scan.

Maximum candidates evaluated per decision pulse:

```text
32
```

Candidate facts:

```text
exact character/profile identity
class/progression capability facts
current party membership
current instance/location
alive/vitals
current readiness for the missing role
relationship/refusal evidence when available
```

Exclude:

```text
already in another party
already claimed by incompatible Phantom party operation
dead/unavailable
wrong instance
outside perceptible/local candidate bounds
recently refused
candidate cannot meet missing role
party already full
```

### Ranking

Deterministic score:

```text
mandatory vacancy first
RoleMatcher score
readiness
travel proximity/feasibility
existing relationship/reputation modifier from Goal 018 if available
stable identity tie-break
```

Language text is not an eligibility input.

## 12. Invite/refuse policy

Only party leader can recruit.

One outstanding candidate invite per Rift recruitment state.

Phantom candidate:

- use Goal 017 managed invitation lifecycle;
- target may accept/refuse through existing Phantom policy;
- no direct canonical Party mutation.

Real player:

- send exactly one canonical party invitation through Goal 017;
- acceptance/refusal remains controlled by the real player;
- do not resend before cooldown;
- refusal/timeout creates bounded durable recruitment evidence.

Required cooldown defaults in Phantom policy:

```text
invite timeout: 15..60 seconds
same-candidate refusal cooldown: 5..30 minutes
maximum sequential candidate attempts per missing seat: 8
maximum total recruitment attempts per Rift preparation: 32
```

All exact values are policy data.

When a refusal occurs:

- record candidate + vacancy + timestamp/reason key;
- do not change the canonical roster;
- select another candidate or remain NEEDS_ROLE.

## 13. Conversation integration

Goal 020 owns language generation and outbound chat.

Goal 023 exposes typed semantic facts only:

```text
RIFT_PREP_STATUS
RIFT_MISSING_ROLE
RIFT_MEMBER_NOT_READY
RIFT_INVITE_REQUEST
RIFT_INVITE_REFUSED
RIFT_PARTY_FULL
RIFT_READY
```

Examples of structured payload facts:

```text
tier
missingRoleKey
candidate character ID
member character ID
reason key
party size
required size
```

Do not add phrase banks.

When asked “кого не хватает?” the answer must come from the latest exact
RoleMatchResult over the current canonical roster.

A roster change between understanding and execution invalidates the response and
forces re-evaluation.

## 14. Travel handoff

Goal 017 route coordinator remains route owner.

Rift readiness may request a route only after:

```text
minimum roster achieved
mandatory role composition achieved
no pending invite operation
```

The route destination is the canonical Rift entry destination/anchor resolved by
the factual Rift adapter.

Use one shared party route.

No member independently routes to a different guessed entrance.

On route failure:

```text
NEEDS_TRAVEL or BLOCKED
```

No direct teleport fallback unless current accepted navigation/commerce already
owns that exact travel action and the route planner selects it.

## 15. Persistence

Persist one bounded Rift preparation component per leader Phantom, unless the
existing PartyState can carry the exact content-specific state without schema
change.

Preferred separate component:

```text
rift.preparation
schema version 1
```

Fields:

```text
leader profile
goal ID/revision
tier
party group ID/generation
canonical roster evidence hash
role catalog hash
Rift source/policy/config hashes
status
current missing vacancy
candidate attempt sequence
pending candidate identity
invite sequence/identity where applicable
refusal/cooldown history (bounded)
route readiness hash
row version / updated epoch
```

Bounds:

```text
refusal history <=32
attempts <=32
payload <=4096 bytes
one active Rift preparation per leader
```

Restart:

- never trust persisted roster;
- reload canonical Party;
- re-run role/readiness evaluation;
- reconcile pending Goal 017 invitation by exact identity;
- stale source/policy/config hash → replan;
- no duplicate invitation after restart.

No new SQL table unless the existing profile-component persistence cannot meet
the contract. Prefer profile component schema.

## 16. Concurrency and ownership

Goal 023 itself owns no worker/timer.

All progress occurs through:

```text
existing shared scheduler
Decision pulse
Goal 017 invitation callback/state
existing navigation/party route callbacks
```

Stable ownership:

```text
Goal 023 Rift preparation component
→ Goal 017 party operation
→ canonical PartyInvitationService / Party
```

Before an invite mutation:

1. re-read Rift preparation row/component;
2. re-read Goal ID/revision;
3. re-read canonical Party roster;
4. verify leader;
5. verify roster not full;
6. verify vacancy still missing;
7. verify candidate still eligible;
8. delegate exactly one Goal 017 invite.

If any check changes, no invitation.

No per-Phantom Future, ThreadPool task or timer.

## 17. Decision integration

Add one Goal:

```text
rift.prepare
```

Strict target identifies factual Rift tier by stable type ID.

One plan step performs at most one durable transition:

```text
DISCOVER_CONTENT
SNAPSHOT_ROSTER
EVALUATE_READINESS
SELECT_CANDIDATE
REQUEST_INVITE
OBSERVE_INVITE
REQUEST_PARTY_ROUTE
OBSERVE_ROUTE
DECLARE_READY
```

Do not form a party, invite multiple people and travel in one Decision call.

Candidate registration must not suppress existing generic Goal 017 party
candidates.

## 18. Factual Rift entry seam

During bounded audit locate the exact current High Five entry/runtime owner.

If a reusable query seam is absent, add a read-only packet-independent service
or immutable snapshot seam near the current owner.

Allowed query only:

```text
entry requirement facts
tier availability
current room/tier capacity if canonical runtime exposes it
current leader/party entry eligibility without consuming resources
```

Do not:

- consume entry items;
- create Rift runtime instance;
- jump room;
- teleport party;
- spawn mobs;
- change the existing Rift manager scheduler/timers.

If the current owner cannot expose a side-effect-free eligibility check without
large invasive changes, implement the factual static/config readiness subset and
mark live-entry eligibility `UNSUPPORTED`; do not fake it.

## 19. Metrics and diagnostics

Bounded metrics:

```text
rift preparations
ready
needs party
needs role
needs supplies
needs travel
invite requested/accepted/refused/expired
candidate rejected by reason family
roster stale
source stale
```

No profile/character/candidate IDs in labels.

Selected-Phantom trace may include bounded IDs/reason keys.

No per-pulse log spam.

## 20. Performance budgets

Focused performance proof:

```text
100000 Rift tier lookups
100000 roster readiness evaluations over 9 members
100000 RoleMatcher vacancy evaluations
10000 bounded candidate searches of <=32 candidates
10000 refusal/cooldown checks
10000 restart reconciliation evaluations
```

No XML parse, NPC full scan, World full-player scan, DB full table scan or class
catalog scan per decision.

## 21. Exact scope

Allowed new production/data:

```text
java/org/l2jmobius/gameserver/phantoms/rift/**
dist/game/data/phantoms/rift/high-five-rift-policy-v1.xml
```

Allowed existing production:

```text
PhantomSystem.java
phantoms/party/**
phantoms/decision registration
phantoms/conversation typed-action bridge only if a new semantic fact enum/port
is required
current canonical Rift runtime/entry owner only for a narrow read-only query seam
```

Allowed tests/build/tools/docs:

```text
build.xml
new PhantomRiftSuite
targeted Goal 017 party regression suite
targeted Goal 020 conversation-action regression
PhantomTestLauncher.java
tools/phantoms/verify-task-022c2.ps1 historical adaptation if required
tools/phantoms/verify-task-023.ps1
Goal 022 final review
Goal 023 architecture/report/review/task docs
roadmap/master-plan final status
```

Hard limits:

```text
new production/data files <=18
changed production/data/config files <=28
changed total files <=48
new SQL files = 0 unless profile component cannot satisfy persistence; if proven,
maximum 1 additive migration
Player.java changes = 0
Party.java core changes = 0
PartyInvitationService changes = 0 unless an immutable read-only snapshot seam
is demonstrably impossible otherwise
DimensionalRift runtime mutation changes = 0
new worker/thread/executor/Future/task = 0
```

Forbidden:

```text
Goal 024+
PvP/PK
raid/epic orchestration
clans
farming spot claims
Rift combat
Rift room jumps
entry item consumption
direct teleport/room spawn
new party kernel
fake GameClient
packet invocation
global online-player scan
language phrase bank
```

## 22. Mandatory focused modes

```text
rift-catalog-authority
rift-roster-readiness
rift-role-composition
rift-recruitment
rift-real-player-invite
rift-travel-readiness
rift-restart-reconciliation
rift-performance
```

## 23. Mandatory dynamic evidence

### Catalog

- all six factual types from current `DimensionalRift.xml`;
- rooms/spawns source parity;
- NPC-level envelope parity;
- current RIFT config values;
- current entry cost/item authority;
- source/config hash drift;
- strict XML negative controls;
- unknown factual field fails closed.

### Roster

- solo;
- party below minimum;
- exactly minimum;
- full 9;
- real + Phantom mixed roster;
- leader change;
- member joins/leaves between evaluation and action;
- stale member snapshot;
- duplicate evidence rejected.

### Composition

Use real Goal 013 class/capability facts.

At minimum prove:

- required healer missing;
- healer present;
- tank/frontline missing/present;
- damage vacancy;
- support optional vacancy;
- one member cannot double-fill two mandatory seats;
- class changes invalidate assignment;
- dead/unready role holder causes NEEDS_MEMBER_READY rather than false FILLED.

### Supplies

- exact entry resource deficit;
- sufficient entry resource;
- shot deficit for assigned damage member where current weapon requires it;
- no guessed consumable;
- no inventory mutation.

### Recruitment

- leader selects highest-priority missing seat;
- exact capable nearby Phantom chosen;
- incapable candidate excluded;
- candidate already in party excluded;
- party-full blocks invite;
- one pending invite only;
- Phantom accept;
- Phantom refuse;
- real Player invite;
- real Player acceptance not forged;
- real refusal/timeout;
- cooldown prevents spam;
- retry chooses next candidate;
- roster change before invite cancels stale action.

### Travel

- composition-ready but remote → NEEDS_TRAVEL;
- route request through Goal 017;
- regroup;
- route failure;
- canonical entry arrival → READY_TO_ENTER;
- no direct teleport.

### Restart

Restart at:

```text
needs role
candidate selected
invite pending
invite accepted before persistence refresh
route pending
ready
```

No duplicate invite; canonical roster always wins.

### Conversation

Typed “missing role” answer uses exact latest readiness snapshot.
Roster mutation invalidates stale answer/action.

## 24. Verification discipline

Development order:

1. freeze Goal 022 final ACCEPT review;
2. bounded Rift runtime/data audit;
3. strict factual catalog and policy;
4. canonical roster/readiness snapshot;
5. role composition;
6. candidate discovery/ranking;
7. invite/refusal lifecycle;
8. travel handoff;
9. restart/concurrency;
10. typed conversation facts;
11. performance;
12. verifier 023;
13. one final `phantom-rift-goal023-test`.

Use only:

```text
phantom.goal023.seed=23002301
```

Do not override the global Phantom seed.

After focused/static/affected gates are green, freeze production/data/test/build/
verifier:

```text
one final Goal 023 aggregate
one plain ant verify
one standalone ant jar
ordinary commit/push
two post-commit byte-identical verifier 023 runs
```

A second full verify is allowed only after a real relevant code/test/build/
verifier fix. A third is forbidden.

Verifier 023 must pin `1c8c99f83ebc9f32ac2c3bc670aec506b8efcccb`, exact direct child/subject/scope, source hashes,
six factual Rift types, no runtime Rift mutation, no Party core mutation,
canonical roster evidence, RoleMatcher use, one-invite policy, real-player
consent, restart, no global player scan, disabled behavior, UTF-8 and JAR
classes. It must remain descendant-compatible in PS5.1 and PS7.

Create:

```text
docs/phantoms/architecture/RIFT_RECRUITMENT_CONTRACT.md
docs/phantoms/reports/023-rift-advanced-party-recruitment.md
docs/phantoms/reviews/023-independent-review.md
```

Print `GOAL_023_RIFT_ADVANCED_PARTY_RECRUITMENT_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW` only after every mandatory gate. Otherwise commit/push one honest
bounded result and stop without Goal 024.
