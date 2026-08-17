# Goal 026 Checkpoint 2 — canonical CommandChannel lifecycle

## Identity
Branch: `feature/phantom-world`
Required parent: `e3f44333df659d3ba3f258739e1e0bba8bb6a53b`
Required subject: `feat(phantoms): add command channel lifecycle`
Seed: `26002621`
Target verdict: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`

Goal026 CP1 is independently ACCEPT. Goal026 overall stays IN_PROGRESS.

## Result
Provide one reusable authoritative High Five MPCC lifecycle for an
**already-selected exact pair of Party leaders**:
- invite;
- exact pending invitation identity;
- target-side ACCEPT/REFUSE;
- canonical create/join on acceptance;
- exact CC-leader dismissal of another Party.

This CP does not select recruitment candidates, gather, navigate or fight.

## Architecture

### Shared generic service
Create a small ordinary-server facade, preferably
`model.groups.CommandChannelInvitationService` (or repository-consistent name).
It owns MPCC invitation/response/dismiss validation and delegates mutations to
existing `CommandChannel`. Do not place this generic owner in `phantoms/*`.

Use typed outcomes/results and an exact invitation identity, following the
existing `PartyInvitationService` pattern where useful.

### Packet parity
Refactor exactly:
- `RequestExAskJoinMPCC`
- `RequestExAcceptJoinMPCC`
- `RequestExOustFromMPCC`

Packets keep wire decode and client-name -> exact Player lookup. Shared service
owns reusable lifecycle rules. Those packet handlers must no longer directly
own `new CommandChannel`, `addParty`, `removeParty` or duplicate eligibility.

Do not change opcode registration.

### Exact transient invitation
Use one bounded in-memory exact pending invitation per invitee (and prevent
conflicting requester ownership), or an equally bounded exact mechanism.

Invitation identity includes exact requester/invitee object IDs plus a
monotonic sequence/token. Ordinary Player request timeout remains time authority:
no scheduler/thread/Future.

On invite/respond/access, expire stale entries lazily. Old identity must never
accept a newer request.

REFUSE and terminal failure clear only the matching pending record and ordinary
Player request relation.

### Preserve exact High Five rules
Requester:
- must be in Party and exact Party leader;
- if Party already in CC, requester must be exact CC leader;
- formation authority is EXACTLY:
  - clan leader, clan level >= 5; OR
  - item 8871; OR
  - pledge class >= 5 AND skill 391.

Target:
- must be in a different Party;
- actual invitee = exact current target Party leader;
- target Party must not already be in CC;
- target Party leader must not be busy.

ACCEPT must revalidate current leadership, Party identity, CC state and
formation authority. Stale state fails closed.

### Canonical mutation
On valid ACCEPT:
- if requester Party has no CC: existing `new CommandChannel(requester)`;
- join invitee Party through existing `CommandChannel.addParty`;
- if requester already leads CC, add to that existing channel.

Never duplicate CC state. Phantom code must not call `Party#setCommandChannel`
or instantiate `CommandChannel`.

### Dismiss
Shared operation preserves current Oust authority:
- requester is exact current CC leader;
- target belongs to another Party in same CC;
- no own-Party/self dismissal loophole;
- use existing `CommandChannel.removeParty`;
- existing `<2 parties => disbandChannel()` remains authoritative.

No Phantom-only hard-disband primitive.

### Goal017 Phantom seam
Goal017 remains Party/CC membership owner. Extend `PhantomPartyBackend` and
`L2jPhantomPartyBackend` narrowly:
- invite exact selected Party leader;
- return exact invitation identity;
- target-side ACCEPT/REFUSE with expected identity;
- dismiss exact selected Party as CC leader;
- optional bounded exact pending-invite snapshot if needed for target-side
  policy later.

Use existing `MemberRef` and `acquire(...)`; mutable Player/Party/CC never
escapes backend.

**Consent:** never auto-ACCEPT because both endpoints are Phantoms. ACCEPT is a
separate target-side call with exact identity. Human target keeps ordinary
client invitation behavior.

## Hard out of scope
No candidate discovery/global scans, recruitment scoring, chat policy,
gathering, navigation, raid entry, combat, retreat, raid persistence/saga,
scheduler/worker/thread/Future, clan strategy, DB/schema/config changes,
other chronicles, second CommandChannel model.

`World.getInstance().getPlayer(name)` may remain only in existing client packet
transport. Shared service/Phantom backend operate on exact references.

## Read budget
Read only:
1. this small package;
2. the 3 named MPCC packet handlers;
3. `CommandChannel.java`;
4. relevant `Party.java` teardown fragment;
5. only structural portions of `PartyInvitationService.java`;
6. `PhantomPartyBackend.java`;
7. relevant `L2jPhantomPartyBackend.java`;
8. focused Goal017 fixture needed for tests.

No historical corpus/master-plan reread.

## Verification budget
No test-after-every-edit. Implement one coherent production block first.

Authorized:
1. one compile/compile-tests after coherent production block;
2. one focused CP2 service/parity suite;
3. one focused Goal017 backend regression because its seam changes;
4. one final CP2 aggregate after freeze;
5. one `ant jar`;
6. diff/scope/encoding checks.

A task-caused focused failure: fix and rerun only affected target once.

Forbidden:
- plain `ant verify`;
- Goal025 aggregate;
- Goal026 CP1 aggregate;
- broad all-Phantom suite;
- stress loops;
- rerunning green gates after docs/report edits;
- inventing new acceptance gaps after specified matrix is green.

**First automatic context compaction = STOP.** No new discovery afterward:
finish current coherent block, remaining mandatory focused gates, commit/push,
handoff.

## Status
After delivery:
- CP1 = ACCEPT;
- baseline before CP2 = `e3f44333df659d3ba3f258739e1e0bba8bb6a53b`;
- CP2 = IMPLEMENTED_PENDING_INDEPENDENT_REVIEW;
- Goal026 = IN_PROGRESS;
- CP3+ = NOT_STARTED.

## Delivery
Ordinary commit exact subject:
`feat(phantoms): add command channel lifecycle`

Ordinary push:
`git push origin feature/phantom-world`

Push safe result even PARTIAL/BLOCKED.
No amend/rebase/squash/reset/force.

Final report: branch, parent, commit, remote HEAD, subject, verdict, shared
service, packet delegation, Phantom seam, stale/expiry behavior,
accept/refuse/dismiss results, exact tests, unfinished findings,
`occurred_context_compaction: yes|no`.

Success token:
`GOAL_026_CHECKPOINT_2_COMMAND_CHANNEL_LIFECYCLE_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`
