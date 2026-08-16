# Goal 025 acceptance gates

## A. Prior acceptance

Documentation accurately records:

```text
Goal024A: ACCEPT
R024A-01: CLOSED
R024A-02: CLOSED
R024A-03: CLOSED
Goal024 overall: ACCEPT
Accepted parent: 922f72c0d422904dcbdc6215a5cc1167a1bb84fb
Goal025: current task
Goal026+: NOT_STARTED
```

Historical decisions on their original commits remain intact.

## B. Architecture

PASS only if:

- Goal012/012A remains the only combat execution owner;
- Goal025 is bounded orchestration/policy, not a second combat engine;
- explicit PvP API is separate from existing normal-monster attack/cast path;
- physical PvP uses canonical forced-attack server seam;
- skill PvP uses canonical Player skill seam;
- no copied damage/CP/karma/drop engine;
- warning/help chat is Goal020-owned;
- party help is Goal017-owned and bounded;
- retreat uses existing navigation/action ownership;
- Goal018 remains social/revenge memory owner;
- Goal024 remains farming escalation authority owner.

## C. Aggression safety

PASS only if:

- no cold random Player hunting;
- no candidate from visibility/PvP flag/karma/low HP alone;
- every encounter has one exact causal source;
- proactive forced aggression requires stronger authority + persisted warning;
- reactive/party defense is source-backed;
- same Party/self/friendly safety is enforced;
- local scans are bounded and context-only;
- per-pair proactive budget/cooldown prevents meatgrinder/corpse camping.

## D. Canonical consequences

PASS only if dynamic tests prove canonical Player PvP paths and production scans
prove absence of direct Phantom mutation for:

- HP/CP;
- PvP flag;
- PvP/PK kills;
- karma;
- inventory death drops.

## E. CP

PASS only if:

- CP-before-HP truth comes from server mechanics;
- natural regen remains server-owned;
- real item stock/reuse is used;
- ItemSkills owns CP potion execution/consumption;
- Olympiad restriction is canonical;
- no free/synthetic CP or potion.

## F. Restart/lifecycle

- one active encounter/profile;
- bounded persisted state;
- no new worker/timer/Future/thread;
- stale authority fail closed;
- cooldown and delivery idempotency survive reload;
- no blind restart aggression against stale human object ID;
- disabled mode inert.

## G. Verification

Required dynamic focused modes and affected regressions PASS. One final Goal025
aggregate after freeze. One final plain `ant verify` and one `ant jar` are
appropriate for this new VERY_HIGH-risk Goal, but are not to be repeated after
process-only changes.

Pinned verifier must reject at minimum:

- other chronicles;
- production DB configuration/use;
- `.l2j` staging;
- global `World.getPlayers()` / list-all profile scan in Goal025;
- direct Phantom `setCurrentHp/setCurrentCp/setKarma/updatePvPStatus` etc;
- direct Player death-drop invocation;
- ClientPacket construction/dispatch;
- direct ChatHandler/chat packet use from PvP package;
- weakening legacy normal-monster target acceptance;
- new thread/executor/timer/Future in Goal025 package.

Static checks supplement, never replace, dynamic proof.

## H. Delivery

Final status is `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW` on successful
implementation. Codex does not self-ACCEPT.

Whatever truthful terminal verdict occurs, safe reviewable result is ordinary
committed and pushed to `origin feature/phantom-world`.

No amend/rebase/squash/reset/force push. No review ZIP.
