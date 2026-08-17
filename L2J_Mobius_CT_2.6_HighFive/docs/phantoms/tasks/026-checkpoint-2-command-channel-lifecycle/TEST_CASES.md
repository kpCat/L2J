# Goal 026 CP2 — focused tests

Seed: `26002621`

## A. Shared service and packet parity
1. Eligible Party leader invite resolves actual invitee to target Party leader,
   creates exact identity and ordinary Player request relation.
2. REFUSE: no CC mutation; exact pending/request state clears.
3. ACCEPT with no existing CC: canonical CommandChannel is created and target
   Party joins through addParty; both Parties share same canonical object.
4. ACCEPT with existing requester-led CC: target joins same CC; no second CC.
5. The three ordinary MPCC packet handlers delegate shared service; no direct
   `new CommandChannel` / `addParty` / `removeParty` remains there.

## B. Formation authority
Focused representatives:
- requester not Party leader => reject;
- requester in CC but not CC leader => reject;
- target no Party => reject;
- same Party => reject;
- target Party already in CC => reject;
- target leader busy => reject;
- no formation right => reject;
- preserve all three right families:
  clan leader lvl>=5 / item 8871 / pledge>=5+skill391.

Do not test unrelated clan behavior.

## C. Exact stale safety
1. Wrong/stale invitation identity cannot accept.
2. Party leader/membership/CC state drift before ACCEPT fails closed.
3. Expired Player request cannot accept and cleans matching pending state.
4. Old refused/expired request cannot clear or accept a newer exact request.

## D. Dismiss
1. Current CC leader can remove another Party through canonical removeParty.
2. Non-leader / unrelated CC / own Party or self target rejected.
3. Removal leaving one Party preserves existing canonical disband behavior.

## E. Goal017 Phantom seam
1. Exact managed MemberRef leader can invite and receive typed exact identity.
2. Exact target-side MemberRef ACCEPT/REFUSE requires matching identity.
3. Backend dismiss requires exact current CC leader and target Party.
4. Mutable Player/Party/CommandChannel never escapes backend.
5. No automatic Phantom acceptance.

## F. Negative controls
- shared service has no World/global target discovery;
- Phantom has no `new CommandChannel` and no direct `Party#setCommandChannel`;
- no `World.getPlayers()` / global profile scan;
- no recruitment/navigation/combat/persistence;
- no new worker/thread/timer/Future;
- disabled Phantom remains inert;
- no other chronicle.

## Final gates
Focused CP2 service/parity; focused Goal017 affected regression; one final CP2
aggregate; one `ant jar`; diff/scope/encoding. No plain verify or older large
aggregates.
