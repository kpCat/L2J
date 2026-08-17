# Goal 026 Checkpoint 3 — focused test matrix

Seed: `26002631`

## 1. Deficit truth

Assert exact member deficit, exact required-capability deficits, optional
requirements excluded, and capability truth remains exact key/rank plus
intrinsic && learned && readyNow.

## 2. Candidate exactness and bounds

Assert null/duplicate/over-16 input fails closed; exact standalone Party leader
is accepted; non-leader, candidate already in CC, actor-force member,
unavailable/stale candidate, and whole Party exceeding recommendedMaxParty are
rejected. No Party splitting.

## 3. Contribution

Use at least:
- candidate A fills a hard capability deficit;
- candidate B contributes useful bodies only;
- candidate C contributes neither.

Assert exact contribution and C is non-recruitable.

## 4. Deterministic selection

A outranks B because hard deficit reduction wins. Tie-breakers: useful member
contribution, lower excess, stable key. Shuffle input and assert identical
winner and evidence hash. No RNG.

## 5. No-action authority

GROUP_READY, target UNKNOWN/UNAVAILABLE, unavailable current force,
GROUP_ABSENT, and actor without exact Party/CC invitation authority all produce
no invite.

## 6. Canonical single invite

`recruitNext` sends exactly one invite through accepted CP2 and returns exact
identity. If the selected invite is canonically rejected after drift, candidate
#2 is not attempted in that call.

## 7. Invitation != membership

After delivered invite but before response:
- candidate Party is not in actor CC;
- fresh CP1 readiness has not gained its members/capabilities;
- production CP3 never calls respondCommandChannel.

Then TEST FIXTURE only performs exact CP2 target-side ACCEPT. Only after that
must fresh CP1 observe the enlarged canonical force and possibly shrink deficits
or become GROUP_READY.

## 8. Consent

REAL candidate preserves ordinary pending/client semantics.
PHANTOM candidate is not auto-accepted and retains exact pending identity for a
future policy.

## Negative controls

No World.getPlayers/global profile scan/name candidate search; no
navigation/gathering/combat/retreat/loot; no raid DB/store; no new
worker/thread/timer/Future; no direct new CommandChannel/addParty/removeParty/
setCommandChannel from raid code; no production respondCommandChannel call;
other chronicles untouched.

## Documentation gate

CP2 report decodes clean UTF-8 with no mojibake. Roadmap/master plan say:
CP1+026A ACCEPT, CP2 ACCEPT at `bbd29495a19a322c0629509c85c31fe508ae8d07`, CP3 pending independent review,
Goal026 overall IN_PROGRESS.

Docs edits do not cause CP2 product reruns.
