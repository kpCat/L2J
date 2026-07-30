# Goal 017 bounded completion — party lifecycle safety

## Git and status

```text
branch: feature/phantom-world
required parent: d731bf91b5f75cf733175bf57faf19c0354085c0
test DB only: l2jmobiush5_phantom_test
production DB forbidden: l2jmobiush5
seed: 17001702
commit subject: fix(phantoms): complete party lifecycle safety
success token: GOAL_017_PARTY_LIFECYCLE_SAFETY_COMPLETED_PENDING_INDEPENDENT_REVIEW
```

Continue Goal 017 in the same Codex conversation. Do not create Goal 017A/017B/
017C and do not start Goal 018/019/020/023/025. Create one ordinary child and
push to `origin/feature/phantom-world`. No amend/rebase/squash/merge/force push.

Independent review accepts the Goal 017 foundation:

- bounded `party.state` model/codec/store;
- generic role XML parser and no class-ID switch;
- typed language-independent semantic-act model;
- shared navigation manifest concept;
- combat external-action ownership concept;
- composite shared-scheduler port;
- disabled/default-safe configuration.

Goal 017 is not accepted because the first implementation omitted or broke
specific lifecycle, idempotency, target-truth, bounded-pulse and real-integration
contracts. Fix only the findings in this package. Do not redesign accepted
subsystems.

## Execution-efficiency contract

Do not reread the original Goal 017 task package, master plan, entire roadmap,
old reports, whole `Player.java`, whole `Party.java`, or unrelated subsystems.

Initial closed READ_SET:

1. this completion package;
2. `PartyInvitationDelivery`, `PartyInvitationService`, both party packet handlers;
3. exact Player request methods and exact Party add/remove/leader methods only;
4. `PhantomPartyCoordinator`, `Decision`, `Backend`, `RoleMatcher`, `Tactics`,
   `RouteCoordinator`, model operation/status records;
5. combat external-action request/lease and L2J support/move methods;
6. progression capability evaluator/service methods used by party;
7. background directive/acquire/lifecycle entry points;
8. `PhantomSystem` party/background construction and shutdown ranges;
9. Party suite, launcher, build party targets and verifier 017.

At most six additional exact symbol/file reads, each recorded with one sentence.
No broad repository searches after this audit.

Hard implementation limits:

```text
changed production files <= 18
new production files <= 3
changed total files <= 30
no schema migration
no new worker/thread/executor/Future/task
no per-profile/per-party scheduled task
report <= 190 lines
soft Goal usage target <= 650,000 tokens
```

If the architecture appears to require exceeding these limits, stop broad
implementation, preserve safe evidence and report the exact blocker. Do not
invent another suffix Goal.

## Required product result

At completion:

```text
ordinary client invitation parity
+ Phantom↔Phantom / Phantom→real / real→Phantom exact terminal lifecycle
+ idempotent durable party saga
+ leave / expel / transfer / travel commands
+ maximum contextual role assignment
+ target-specific support capability truth
+ bounded route and coordinator pulse
+ Goal 015 background exclusion for committed live party intent
+ real DB/materialized Party integration and restart evidence
```

## Mandatory corrections

### 1. Canonical invitation lifecycle

The current core expires only `_pendingByInvitee`, so an expired requester entry
can remain in `_pendingByRequester` and permanently return `REQUESTER_BUSY`.
Pending invitations also retain strong Player references after timeout.

Refactor the existing service without packet-handler simulation:

- expire/prune by both requester and invitee;
- every terminal path removes both exact indexes once;
- preserve existing ordinary packet/request-field/Party behavior;
- retain exact `InvitationIdentity`;
- concurrent accept/cancel/expire has exactly one winner;
- no service monitor is held across Player, Party, delivery or DB boundaries.

Track both optional managed identities:

```text
managedRequesterIdentity
managedInviteeIdentity
```

Extend the generic delivery boundary with two transport-neutral callbacks:

```text
prepare(invitation, managedRequester, managedInvitee)
terminal(invitation, managedRequester, managedInvitee, terminalOutcome, reasonKey)
```

`prepare` runs before any client prompt or managed enqueue becomes externally
actionable. It allows the coordinator to persist the exact sequence on every
managed side. A rejected preparation clears the reservation and publishes no
prompt.

Use explicit reservation/publication state so a response cannot observe an
invitation before request fields and durable managed preparation are complete.

Terminal outcomes must distinguish at least:

```text
ACCEPTED
REFUSED
DISABLED
EXPIRED
CANCELLED
DELIVERY_REJECTED
REVALIDATION_FAILED
REQUESTER_UNAVAILABLE
```

Terminal notification is required when either side is managed, including
Phantom→real. It is delivered exactly once.

`DeliveryRegistration.close()` must atomically detach and then outside the core
lock clear every invitation owned by that delivery, including outbound
Phantom→real invitations. It must not merely replace the delivery pointer.

### 2. Exact durable invitation saga and retry idempotency

Current Phantom member PREPARED claim keeps invitation sequence `0`; terminal
rollback therefore cannot match the real invitation identity.

Coordinator preparation must atomically-in-saga persist exact
`CANONICAL_PENDING` identity to:

- leader claim;
- Phantom member claim when applicable.

If either optimistic write fails, preparation rejects; core invitation is not
published; successfully written claims are safely rolled back or fail-stop
`INCONSISTENT`.

Terminal callback:

- ACCEPTED queues exact canonical observation/commit;
- all non-accepted terminal results mark the exact leader operation ABORTED and
  move the exact Phantom member claim to SOLO;
- stale invitation identity cannot alter a newer operation;
- Phantom→real refusal/timeout/cancel is visible to the leader claim;
- no explicit refusal is automatically re-invited.

Make the decision step idempotent:

- `form` with the same profile/goal ID/revision and live operation returns an
  idempotent accepted status without overwriting it;
- `inviteTarget` with the same exact pending target/operation does not issue a
  second canonical invite;
- a different goal/revision/target conflicts;
- retrying `party.form_invite` observes/awaits the existing operation;
- operation deadline expires the exact core invitation and terminalizes claims.

### 3. Operation claims and shutdown

Add bounded coordinator operation/control claims around every external boundary:

```text
form/invite/respond terminal processing
leave/expel/transfer
route request/advance
canonical observe/commit
persistence publication
```

`beginStop` closes admission, drains the delivery registration and cancels
route/tactical leases. `finishStop` returns false until:

```text
operation claims == 0
persistence claims == 0
terminal/inbound queues empty
core invitations owned by registration == 0
navigation requests == 0
route leases == 0
tactical leases == 0
```

A callback racing with stop either finishes under a claim or is rejected before
mutation. No callback publishes work after STOPPED.

### 4. Canonical membership commands

The current decision layer exposes only FORM and JOIN. Add exact bounded
coordinator commands and semantic actions for:

```text
party.leave
party.expel_member
party.transfer_leader
party.travel
```

Rules:

- exact goal ID/revision/generation and canonical leader/member checks;
- canonical service/backend mutation first;
- observe canonical Party postcondition;
- update every affected Phantom claim and goal;
- explicit leave/expel moves removed Phantom to SOLO;
- disband moves all managed claims to SOLO;
- canonical automatic leader change advances generation exactly once;
- stale departed claim is never used to re-invite a member;
- real-player action is never fabricated.

Do not add matchmaking, Rift policy, text, personality, clan or PvP behavior.

### 5. Background ownership gate

Goal 015 background currently has no party dependency and can farm/travel a
profile with committed live party intent.

Add a narrow optional no-op party-participation port/bridge. `PhantomSystem`
creates the bridge before BackgroundService and installs the coordinator after
party startup.

Background `directive`, `farm`, `travel` and the final acquire/commit boundary
must reject with typed reason `party.materialized_only` when the profile has live
party intent:

```text
LEADER
MEMBER
RECOVERING
or exact CANONICAL_PENDING/CANONICAL_OBSERVED operation
```

Recheck immediately before mutation. Do not duplicate party payload in
background state and do not change Goal 015 farming arithmetic.

### 6. Maximum role/vacancy matching

Replace the greedy requirement loop with deterministic bounded maximum matching
for at most nine members.

Optimization order:

1. maximize number of required vacancies filled;
2. maximize total contextual score;
3. maximize optional vacancies filled;
4. deterministic lexical tie-break by vacancy key, member stable key,
   capability identity.

A member fills at most one vacancy but retains all capability evidence. Do not
replace contextual roles with class IDs or fixed archetypes.

Add the counterexample:

```text
member A can fill HEAL and RECHARGE with highest HEAL score
member B can fill only HEAL
required HEAL + required RECHARGE
```

Both must be filled by the global assignment.

### 7. Target-specific capability truth and support execution

Current Phantom party snapshots call progression with `targetObjectId=null`.
Target-required heal/recharge/resurrection therefore become `TARGET_REQUIRED`,
while tactics selects only `readyNow=true`.

Add a bounded backend query:

```text
capabilities(actorMember, exactTargetObjectId)
```

For Phantom actors it must call the accepted progression evaluator with the exact
target. For real-player role observations, derive exact current skill,
equipment/resource/reuse facts without claiming target-specific readiness when
no target exists.

Tactics queries exact target-specific capabilities only for actual low-HP,
low-MP or dead targets. Respect catalog target scope:

- SELF only for self;
- PARTY/PARTY_MEMBER/ALLY-compatible scopes for another member;
- resurrection only for dead target;
- non-resurrection only for living target.

Pass a typed support action spec containing capability key, variant, target
scope and exact skill ID/level through combat external ownership. L2J backend
revalidates current party, instance, range, learned skill, equipment/resources,
reuse and skill conditions before issue. A capability key cannot relabel an
unrelated positive skill.

No use-all song/dance/buff behavior.

### 8. Route authority and missing-member behavior

Before advancing:

- persisted topology hash must equal current topology hash;
- cancellation/deadline must still be owned;
- every canonical roster member must have a snapshot in the same instance;
- absent/cross-instance member causes HOLD/REGROUPING or typed FAILED, never
  waypoint advancement;
- dead/casting/combat member cannot be moved;
- expired movement lease is cancelled;
- route cancellation remains group-scoped;
- no snap, teleport or background travel.

A route cannot become ARRIVED while one canonical member is absent or in another
instance.

### 9. Truly bounded coordinator pulse

The current pulse sorts all groups and `claims(groupId)` scans all claims for
each group. Replace with maintained indexes:

```text
groupId -> sorted claims
bounded due/round-robin group queue
bounded terminal/inbound queue
bounded tactical-release queue
```

Every coordinator pulse must count all examined profile/group/action work,
including terminal events, group reconciliation, tactical cleanup and route
movement, and never exceed `PhantomPartyOperationsPerPulse`.

Forbidden in pulse paths:

```text
_groups.values().stream().sorted(...)
_claims.values().stream().filter(groupId...)
full tactical-action scan
full managed-profile scan
```

Startup may page DB by 256 and build indexes once.

### 10. Final cumulative verifier truth

The committed report states that a real source/test/verifier fix occurred after
the first green full verify, while the second full stopped on an unrelated
population flake. Therefore the current final tree has no completed cumulative
verify after the final source fix.

Completion must produce one final code-frozen cumulative run. See the verification
discipline below.

## Exact scope

Allowed production:

```text
java/org/l2jmobius/gameserver/model/groups/PartyInvitationDelivery.java
java/org/l2jmobius/gameserver/model/groups/PartyInvitationService.java
java/org/l2jmobius/gameserver/network/clientpackets/RequestJoinParty.java
java/org/l2jmobius/gameserver/network/clientpackets/RequestAnswerJoinParty.java
java/org/l2jmobius/gameserver/phantoms/party/**
java/org/l2jmobius/gameserver/phantoms/semantic/PhantomPartySemanticActs.java
java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatBackend.java
java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatActorLease.java
java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatService.java
java/org/l2jmobius/gameserver/phantoms/combat/L2jCombatBackend.java
java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundService.java
java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java
```

Up to three new production files are allowed only for:

```text
party participation port/bridge
typed party support action value
```

Allowed tests/build/tools/docs:

```text
test/java/org/l2jmobius/tests/phantoms/PhantomParty*.java
one shared test-only managed-player fixture if needed
targeted adaptations to existing combat/background/system suites
PhantomTestLauncher.java
build.xml
tools/phantoms/verify-task-017.ps1
docs/PHANTOM_BOTS_ROADMAP.md status only
docs/phantoms/architecture/PARTY_COORDINATION_CONTRACT.md
docs/phantoms/reports/017-party-coordination-kernel.md
docs/phantoms/reviews/017-party-coordination-kernel-review.md
docs/phantoms/tasks/017-party-lifecycle-safety-completion/**
```

Forbidden:

- `Player.java`, `Party.java`, schema/migrations;
- other chronicles/geodata;
- progression catalog/evaluator semantic changes;
- population, commerce, topology or navigation semantic rewrites;
- global matchmaking, Rift, background party rewards;
- Goal 018/019/020/023/025;
- packet-handler invocation or fake Phantom client;
- new worker/task/thread/Future.

## Verification discipline

Development uses only the exact edited focused target. Before cumulative work:

1. compile affected and tests;
2. run one final `phantom-party-test` aggregate containing all corrected dynamic
   and integration tests;
3. run only these affected regressions once:
   - combat action ownership;
   - combat server integration;
   - background focused gate;
   - population server integration;
   - materialization production integration;
   - activity scheduler/composite;
   - decision core;
4. verifier 016 and working verifier 017;
5. known combat and population flaky targets as one preflight each.

Do **not** run the old 27-target affected aggregate.

After every focused/static gate is green, freeze production, test, build and
verifier files. Then:

```text
one final full ant verify
one standalone ant jar
update report terminal-results section only
ordinary commit/push
two post-commit byte-identical verifier 017 runs
```

Never run a second full verify after a green full verify. If the one final full
fails:

- inspect only the exact failed report;
- an unrelated preflight-green flake gets one exact targeted retry and is
  reported honestly without broad rerun;
- a relevant defect unfreezes the tree, is fixed with targeted tests, then one
  replacement final full is allowed;
- maximum two full verify invocations total; no third.

Report commands as grouped counts, not a transcript. Explain usage above 650k
with exact cause. Print `GOAL_017_PARTY_LIFECYCLE_SAFETY_COMPLETED_PENDING_INDEPENDENT_REVIEW` only after every mandatory product and test
gate; otherwise commit/push safe evidence with honest BLOCKED/PARTIAL.
