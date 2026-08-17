# Goal 026 Checkpoint 4 — raid assembly, preparation and gathering

## Identity
Branch: `feature/phantom-world`
Required parent: `88b7c031847c71abd4077423336caaa6bd179712`
Required subject: `feat(phantoms): assemble and gather raid forces`
Seed: `26002641`
Target verdict: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`

This task is deliberately larger than CP2/CP3. Its product result is one
complete capability:

`explicit raid goal -> multi-Party assembly -> separate consent -> canonical
CommandChannel -> physical gathering -> READY_AT_STAGING`

No raid combat is started here.

# 1. Accepted owners — reuse, do not redesign

## CP1
`PhantomRaidReadinessService.assess(actor, contentId)` owns target availability,
recommended min/max and exact current force readiness. Hard capability truth
remains exact key/rank + `intrinsic && learned && readyNow`.

## CP2
`CommandChannelInvitationService` owns generic MPCC invite/respond/dismiss.
`PhantomPartyBackend` exposes exact MemberRef MPCC seams. Actual mutation remains
canonical `CommandChannel`.

CP4 MAY add one missing generic operation:
`cancel(exact InvitationIdentity)` plus narrow Phantom backend delegation.
It exists only so assembly cancellation/deadline/shutdown does not leave a
requester busy until timeout. It must remove only the matching pending request;
a stale identity cannot cancel a newer invite.

## CP3
`PhantomRaidRecruitmentService` owns bounded (max16) candidate assessment and
deterministic selection:
hard deficit reduction -> useful bodies -> lower excess -> stable key.
`recruitNext` sends at most one MPCC invite. Reuse it; do not clone its scoring.

## Goal017 route owner
DO NOT put a CommandChannel into `PhantomPartyModel.PartyState`; its correct
MAX_ROSTER is 9.

Reuse a DEDICATED `PhantomPartyRouteCoordinator` for raid gathering:
one route group per canonical Party, roster exactly that Party <=9.
It already uses NavigationService and Combat `PARTY_ROUTE` action ownership,
moves PHANTOM members and only observes REAL members.

# 2. Goal contracts and bounded candidate source

Use existing `PhantomGoal`; do not create World/profile discovery.

Leader goal:
- `goalType = raid.prepare`
- ACTIVE
- subject, when present: `profile:<leaderProfileId>`
- target: `raid.content:<contentId>`
- `validSources`: max16 exact `profile:<id>` or `character.object:<id>` candidate
  Party leaders
- `deadlineEpochMillis`: required and future
- optional `selectedAnchor = topology.anchor:<anchorId>`

Phantom willingness goal:
- `goalType = raid.participate`
- ACTIVE
- target exactly same `raid.content:<contentId>`
- subject, when present, exact candidate profile
- not expired.

Resolve validSources only through accepted exact backend methods. Unsupported or
malformed source makes the leader goal invalid. No hidden expansion.

# 3. Bounded assembly service

Create a stateful no-worker service, preferably `PhantomRaidAssemblyService`.

One active leader assembly per leader profile; max64 active assemblies.
Identity includes profileId + goalId + goalRevision + contentId.
No DB raid saga. Restart discards transient state; the ACTIVE goal is restart
authority and fresh state is reconstructed from canonical server truth.

Typed states should cover:
- ASSEMBLING
- WAITING_CONSENT
- GATHERING
- FINAL_PREPARATION
- READY_AT_STAGING
- BLOCKED
- EXPIRED
- CANCELLED

Exact names may differ.

`advance(...)` does bounded work only. No service-owned Thread/Future/scheduler.
Keep bounded terminal/ready receipts for CP5 handoff.

Stale goal revision cannot advance/cancel a newer assembly.

# 4. Sequential recruitment

Every ASSEMBLING advance starts with fresh CP1.

If current force is GROUP_READY:
- stop recruiting;
- freeze STRUCTURAL force evidence;
- proceed to staging/gathering.

Otherwise call accepted CP3 `recruitNext` using validSources excluding:
- current force;
- candidates already terminally refused/lost for this assembly.

Rules:
- at most ONE new MPCC invitation per assembly advance;
- never multiple parallel pending invites;
- invite delivery != membership;
- a canonical reject never falls through to candidate #2 in the same advance.

Store exact candidate + exact CP2 InvitationIdentity, then WAITING_CONSENT.

# 5. Separate target-side consent

Never respond in the same advance that created the invite.

## PHANTOM target
On a later advance:
1. exact pending CP2 identity still matches;
2. target is still exact standalone Party leader;
3. candidate current goal is ACTIVE `raid.participate` for the same exact content;
4. candidate willingness goal is not expired;
5. fresh CP3 planning still says this candidate is eligible/useful.

Only then call exact target-side
`respondCommandChannel(candidate, ACCEPT, identity)`.

Missing/mismatched/expired willingness => exact REFUSE.

This is explicit bilateral policy, not "accept because Phantom".

## REAL target
Production CP4 NEVER calls respondCommandChannel for REAL target.
Ordinary client prompt remains authoritative.

Later advances inspect only:
- exact pending invitation;
- fresh canonical force.

If pending disappears and Party did not join, exclude candidate and continue on
a later advance. If the real player accepted, fresh force shows the Party.

# 6. Exact MPCC cancel extension

Generic cancel is authorized only as described above.

Assembly uses exact cancel when:
- leader plan/goal is cancelled;
- exact goal revision is superseded;
- assembly deadline expires;
- boss becomes terminally unavailable;
- assembly service shuts down.

No World scan, no persistence, no change to invite/respond/dismiss semantics.

# 7. Structural force evidence

Create a deterministic membership hash over:
- Party/CommandChannel identity;
- sorted exact Party leaders;
- exact sorted Party rosters.

Do NOT include transient `readyNow`, moving, casting or HP/MP in this structural
hash.

During GATHERING:
- structural force change -> cancel all raid routes and return to fresh
  ASSEMBLING/replan;
- transient casting/readyNow does not by itself destroy route ownership.

# 8. Read-only exact boss location

Extend `PhantomRaidAuthority` with a read-only location observation. Use a
default unsupported/empty implementation so unrelated stubs are not forced into
fake data.

Production `L2jPhantomRaidAuthority`:
- RAID: exact live RaidBoss x/y/z/instanceId;
- EPIC: exact live GrandBoss x/y/z/instanceId.

No manager mutation.

# 9. Staging centre authority

Priority:
1. content `topologyAnchorId` when explicitly present; it MUST resolve through
   current `PhantomTopologyQuery.findAnchor`, otherwise fail closed;
2. leader goal `selectedAnchor=topology.anchor:<id>` when content has no explicit
   anchor;
3. otherwise exact live boss location.

Current shipped raid.25001/epic.29001 have no topology anchor, so live fallback
must work.

Do not move Parties directly onto boss coordinates.

# 10. Deterministic per-Party staging slots

Canonical Parties are sorted by Party leader stable key.
Generate one slot per Party.

Initial bounded policy:
- live boss centre: stand-off ring radius 1800;
- explicit staging anchor: Party-separation ring radius 300;
- live-derived centre drift >500 during gathering invalidates old slots/routes.

No hardcoded boss coordinates.

For generated slot Z:
- if GeoEngine has geodata at x/y, normalize via current `getHeight(x,y,z)`;
- otherwise keep factual centre Z.
NavigationService remains the final path feasibility authority.

Epic entry/zone rules are NOT CP4. If ordinary navigation cannot reach an epic
slot, fail/replan with typed staging/entry-required reason. Never teleport.

# 11. Multi-Party physical gathering

Use a dedicated existing `PhantomPartyRouteCoordinator`.

For each canonical Party:
- route group id deterministic from assembly identity + Party leader;
- route roster is exactly the one Party, <=9;
- select stable first PHANTOM member in that Party as route actor;
- route destination = Party staging slot;
- use current topology canonical hash;
- route deadline <= assembly goal deadline;
- route advance receives the whole exact Party roster, including REAL members.

Mixed Party:
- PHANTOM members may move through existing PARTY_ROUTE ownership;
- REAL members are never moved and route waits for them.

All-REAL Party:
- issue no automatic route;
- observe positions only;
- mark arrived only when all real members are inside the bounded slot arrival
  radius.

Do not create a second low-level movement API. Do not directly set coordinates.
Do not teleport.

Busy Phantom combat/acquisition/other external owner:
- do not steal it;
- route stays pending/regrouping/retry until deadline.

# 12. Gathering and final preparation semantics

Once composition becomes GROUP_READY, structural roster is frozen for staging.

During gathering:
- target must remain AVAILABLE;
- canonical structural force must remain same;
- boss/centre drift rule applies;
- route each Party to its own slot.

After all Parties are physically staged -> FINAL_PREPARATION.

FINAL_PREPARATION requires:
- target still AVAILABLE;
- structural force hash unchanged;
- every exact current member alive;
- fresh CP1 is GROUP_READY.

If structural force changed -> cancel routes and return to ASSEMBLING.
If structure is unchanged but readiness is transiently not ready -> remain
FINAL_PREPARATION until deadline; do not invent readiness and do not start
combat.

Only then emit READY_AT_STAGING.

Ready receipt must include at least:
- leader goal/content identity;
- current command-channel/force structural evidence;
- boss/staging-centre evidence;
- deterministic Party staging slots;
- final CP1 status/evidence;
- completion timestamp.

CP5 will revalidate it. It is NOT entry, combat, victory or loot authority.

# 13. Decision engine wiring

Add `PhantomRaidDecision` following `PhantomRiftDecision` pattern.

## raid.prepare
Register candidate/action for ACTIVE/WARM.

Handler:
- cancellation token => exact assembly cleanup then CANCELLED;
- intermediate assembly => REPLAN;
- READY_AT_STAGING => COMPLETE_GOAL;
- BLOCKED/EXPIRED => FAIL_GOAL.

## raid.participate
Must NEVER create its own raid assembly.

Handler:
- matching pending/leader assembly => REPLAN while waiting;
- canonical join into matching leader force => COMPLETE_GOAL;
- expired/impossible => FAIL_GOAL.

Register candidate(s) and handlers in PhantomSystem before registry sealing.

# 14. Production lifecycle

Wire assembly using:
- production GoalStore;
- accepted raid readiness/recruitment;
- accepted Party backend;
- raid authority;
- topology query supplier;
- dedicated PartyRouteCoordinator over current Navigation + Combat.

Assembly cleanup must happen BEFORE Party/Combat/Navigation teardown on normal
shutdown and startup-failure cleanup.

Disabled Phantom system remains inert.

# 15. Out of scope — CP5

DO NOT implement:
- raid/epic entry mechanics or scripted entrance;
- boss attack/cast/assist;
- win probability/victory;
- retreat/wipe recovery;
- raid loot rights/distribution;
- Queen Ant/Zaken special execution profiles;
- clan/alliance strategy;
- global candidate discovery;
- social/chat negotiation;
- raid DB persistence;
- other chronicles.

These are the final substantive CP5.

# 16. Documentation

Minimal truth update:
- CP1+026A ACCEPT
- CP2 ACCEPT
- CP3+026B ACCEPT at `88b7c031847c71abd4077423336caaa6bd179712`
- CP4 IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
- Goal026 overall IN_PROGRESS
- CP5 NOT_STARTED

# 17. Focused acceptance matrix

Required scenario suites:

A. Assembly/consent
- invalid/stale leader goal fails closed;
- two+ Phantom Parties recruited sequentially, one pending invite at a time;
- no same-advance response;
- matching raid.participate accepts later;
- missing/mismatched willingness refuses;
- REAL candidate remains manual;
- real manual ACCEPT is observed through force;
- real refusal/expiry excludes candidate;
- exact cancel clears matching invite;
- stale cancel cannot clear newer invite.

B. Force/staging/gathering
- structural hash changes on roster change but not transient readyNow/casting;
- staging authority priority is exact;
- missing explicit anchor fails closed;
- deterministic slot order/hash independent of input order;
- no hardcoded boss coords;
- 2+ canonical Parties produce 2+ separate PartyRouteCoordinator groups;
- no route gets >9 roster or whole CommandChannel;
- mixed Party moves Phantom only and waits Real;
- all-real Party is observation-only;
- action ownership busy is not stolen;
- force drift cancels routes/re-enters ASSEMBLING;
- live centre drift >500 replans.

C. Final/Decision/lifecycle
- staged force without fresh GROUP_READY cannot finish;
- READY_AT_STAGING only after all staged + alive + fresh CP1 GROUP_READY;
- no combat/entry/loot happened;
- raid.prepare decision maps intermediate/ready/failure/cancel correctly;
- raid.participate never leads another assembly;
- enabled production wiring works;
- disabled remains inert;
- shutdown cleans pending invite/routes before lower owners.

Affected regressions:
- focused CP2 CommandChannel lifecycle because exact cancel is added;
- focused raid authority because location observation is added.

Negative source controls:
no World.getPlayers/global scan, no raid direct CommandChannel mutation, no direct
Player movement/teleport, no REAL movement, no new raid worker/thread/Future, no
boss-manager mutation, no raid attack/cast/retreat/loot, no other chronicle.

# 18. Execution / verification budget

This is intentionally a larger coherent task, but compulsory reading is SMALL:
1. this file + PRIOR_INDEPENDENT_REVIEW;
2. current raid model/readiness/recruitment/authority;
3. exact MPCC cancel insertion points;
4. PhantomGoal/GoalStore;
5. PhantomRiftDecision as wiring precedent;
6. public PhantomPartyRouteCoordinator request/poll/advance/cancel API;
7. PhantomSystem registration/lifecycle section;
8. focused fixtures.

Do NOT reread historical reports or whole Goal017/Combat/Navigation.

Implementation:
- one coherent production block;
- then compile;
- no test-after-every-edit.

Focused gates:
1. CP4 assembly/consent;
2. CP4 gathering/staging;
3. CP4 Decision wiring;
4. affected CP2 lifecycle;
5. affected raid authority;
6. ONE final CP4 aggregate containing only those;
7. ONE ant jar;
8. diff/scope/strict UTF-8/mojibake/escaped-Cyrillic checks.

Forbidden:
- plain ant verify;
- Goal025 aggregate;
- CP1 large aggregate;
- CP2 final aggregate beyond affected lifecycle target;
- CP3/026B aggregate unless CP3 semantics were actually changed;
- broad Goal017;
- all-Phantom;
- stress loops.

After specified matrix is green: STOP.

First automatic context compaction:
STOP new discovery; finish only already-started coherent block if safe; mandatory
remaining focused gates; factual handoff; ordinary commit/push.

# 19. Delivery

Ordinary commit exact subject:
`feat(phantoms): assemble and gather raid forces`

Ordinary push:
`git push origin feature/phantom-world`

Push safe coherent result even if PARTIAL/BLOCKED.
No amend/rebase/squash/reset/force-push.

Final report: branch, parent, SHA, remote HEAD, subject, verdict, goal contract,
sequential recruitment/consent, exact cancel, force drift, staging authority,
per-Party routes, REAL handling, READY_AT_STAGING evidence, lifecycle, tests,
unfinished findings and `occurred_context_compaction: yes|no`.

Success token:
`GOAL_026_CHECKPOINT_4_RAID_ASSEMBLY_GATHERING_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`
