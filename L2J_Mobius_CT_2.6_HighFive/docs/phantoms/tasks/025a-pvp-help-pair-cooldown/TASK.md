# Goal 025A — PARTY help delivery and pair-scoped cooldown

## Identity

Branch: `feature/phantom-world`
Required parent: `5656b9ce8c423f503d4a8b5d1046eb12929950d4`
Required commit subject: `fix(phantoms): correct pvp help and pair cooldown`
Seed: `25002511`
Target verdict: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`

Goal025 independent status: `CHANGES_REQUIRED`
Goal026+: `NOT_STARTED`

Read `PRIOR_INDEPENDENT_REVIEW.md` first. This is a narrow corrective task.
Do not reopen the full Goal025 audit and do not search for new acceptance gaps.

## Scope

Close exactly:
- R025A-01 — PARTY help is routed with the hostile counterpart.
- R025A-02 — persisted pair cooldown is applied profile-globally.

No other production redesign is authorized.

## R025A-01

Preserve owner boundaries:
- Goal017 owns Party membership / PvP protection evidence;
- Goal020 owns language, outbound persistence and chat dispatch;
- Goal012 owns combat;
- Goal025 only orchestrates.

Required:
1. PARTY_DEFENSE keeps the hostile Player as PvP combat target.
2. HELP_REQUEST uses the exact attacked/current Party member as its expected
   PARTY counterpart (`helpCounterpart` or an equivalent exact Goal017 seam).
3. Never address PARTY help to the hostile target.
4. Missing/stale/non-party help evidence fails closed.
5. Warning/disengage routing to the hostile counterpart remains unchanged.
6. No direct ChatHandler/chat packet/Player chat call from the pvp package.

## R025A-02

Correct cooldown semantics without adding a per-pair history/map.

Required:
1. cooldown(A) must not early-return before current causal observation.
2. cooldown(A) + exact ACTUAL_ATTACK B => B can be admitted immediately.
3. cooldown(A) + exact PARTY_DEFENSE B => B can be admitted immediately.
4. cooldown(A) + proactive FARMING_ESCALATION/REVENGE B => B may create a fresh
   encounter and follows normal OBSERVE/WARN/risk/budget.
5. same-pair proactive A remains blocked until pair cooldown expiry.
6. fresh same-pair ACTUAL_ATTACK/PARTY_DEFENSE remains reactive defense, not
   corpse-camping/proactive revenge.
7. preserve <=1 active encounter/profile, bounded persistence, and no new
   worker/timer/Future/thread.
8. do not solve this by removing cooldown or setting it to zero.

## Focused dynamic tests only

Prove production semantics:
- PARTY help uses exact current Party-member counterpart;
- Goal020 PARTY membership gate can accept that counterpart;
- hostile target is never the PARTY help expected counterpart;
- stale/non-party help evidence fails closed;
- cooldown(A) does not block exact reactive B;
- cooldown(A) does not block different proactive B from normal WARN path;
- cooldown(A) still blocks same-pair proactive A;
- fresh same-pair reactive attack remains defensible;
- warning hostile-counterpart flow remains intact;
- legacy Monster combat path remains untouched.

Reuse existing Goal017/020 fixtures where possible.

## Verification budget

Allowed:
- one compile/compile-tests check after production edit;
- focused 025A tests;
- only directly affected Goal017/Goal020 regressions if those production files
  are touched;
- one final `ant jar`;
- `git diff --check`;
- focused scope and encoding checks.

Forbidden:
- plain `ant verify`;
- rerunning the 214-test Goal025 aggregate;
- broad all-Phantom regressions;
- stress loops;
- finding/fixing additional Goal025 gaps;
- modifying historical `verify-task-014a.ps1`.

If a new unrelated issue appears, record it and stop.

## Status/docs

Minimum factual handoff only:
- Goal025: `CHANGES_REQUIRED` pending 025A independent review;
- Goal025A: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW` after delivery;
- Goal026+: `NOT_STARTED`;
- preserve Goal024/024A ACCEPT.

## Delivery

Ordinary commit exact subject:
`fix(phantoms): correct pvp help and pair cooldown`

Ordinary push:
`git push origin feature/phantom-world`

No amend/rebase/squash/reset/force push.

Push the safe result even if truthful verdict is PARTIAL/BLOCKED.

Final report:
- branch
- parent SHA
- commit SHA
- remote HEAD
- subject
- verdict
- R025A-01 evidence/result
- R025A-02 evidence/result
- exact tests run/results
- unfinished findings

Success token:
`GOAL_025A_PVP_HELP_COOLDOWN_FIXED_PENDING_INDEPENDENT_REVIEW`
