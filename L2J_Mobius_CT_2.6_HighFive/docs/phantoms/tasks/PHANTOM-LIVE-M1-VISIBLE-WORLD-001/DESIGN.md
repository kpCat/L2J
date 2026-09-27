# DESIGN — Visible World M1

## Product intent

When a real player walks through the world, nearby phantom players should feel as though they were already there and doing something.

M1 fixes two related first-impression failures:
1. a phantom can become physically visible too late and appear/pop in near the player;
2. a materialized phantom can be scheduler-active yet visibly do nothing.

This is not a chat/party/raid/economy task.

## A. Measured materialization envelope

Topology remains the semantic/reachability authority. Do not replace it with a global all-profile distance scan.

Add the smallest bounded spatial policy necessary so a profile that may soon become client-visible is promoted/materialized before the real player reaches the visible boundary, and so tiny movement around an edge does not repeatedly unload/reload it.

Rules:
- derive relevant native High Five visibility/known-list/broadcast distance from actual repository source or datapack behavior;
- do not invent a magic number without source/evidence;
- entry/prewarm threshold must be conservative relative to native visibility;
- exit threshold/grace must be farther/longer than entry to provide hysteresis;
- no O(Humans × 10000) full scan each second;
- keep existing topology/perception bounding;
- preserve MaxMaterialized=128 and existing budgets;
- capacity pressure must fail safely.

If topology cannot expose an approaching candidate early enough, extend only its bounded candidate envelope.

## B. Visible-life root cause first

Before changing behavior, select at least one ordinary visible idle phantom and capture its existing selected-decision trace.

Classify exact cause using evidence: NO_GOAL, background-only goal, NO_CANDIDATE, candidate rejection, handler RETRY/REPLAN, route/navigation, target acquisition, admission mismatch or another proven reason.

Do not implement random movement as a band-aid.

## C. Minimal visible-life repair

Only after RED/live trace proves the gap, implement the smallest normal-production path that lets an ordinary WARM/ACTIVE visible phantom make sensible native progress.

Preferred newbie/Gremlin proof:
- eligible low-level phantom can obtain/use a normal solo farming objective;
- selects legal nearby monster;
- moves/attacks through existing navigation/combat;
- may sit/recover when normal policy calls for it;
- progression/inventory remain native/durable.

Do not create test-only goals, teleport phantoms as normal movement, or give free gear/skills/adena. Natural idle is allowed; all sampled phantoms permanently inert is not.

## D. Future pins

Do not build full REAL_PARTY/INSTANCE/RAID/STORE materialization pins here. M1 changes must preserve action leases/external-busy/critical-goal safety and leave a clean path for M2 presence reasons.

## Performance
- no per-profile thread/timer;
- no full 10k scan per human;
- no materialize-all-nearby;
- MaxMaterialized=128;
- ActiveTarget=64;
- scheduler budgets unchanged;
- diagnostics bounded/selected-profile only.
