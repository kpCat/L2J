# GOAL
Required base: `29f4b32509535bad5d73d93bbf7fac1daf6b6b53`
Branch: `experiment/m1-candidate007-observe008`
Model: GPT-6.1 Sol
Reasoning: High
Subagents: forbidden.

If current checkout is wrong/dirty/detached, create:
`C:\Users\ZBook\.codex\worktrees\m1-visible-decision-020\L2J_Mobius`
Do not switch/reset/stash/clean the main checkout.

Accepted facts:
- TASK018 restored a natural client-visible Phantom Player.
- TASK019 on unchanged production observed profile110/object268485779:
  alive, worldPresent, AI=IDLE, target=0, AutoPlay=false,
  farm.background ACTIVE, DecisionEngine attached,
  runtimeState=NEEDS_REPLAN, decisionSequence=0, reasonKey=goal.reloaded.
- TASK019 PREPARE moved TestAdmin and blocked deeper census. Do not use PREPARE in TASK020.

Source-grounded hypothesis:
PhantomSystem currently builds DecisionEngine admission with
`_historicalBackgroundService.permitsNormalOperation(profileId)`.
That method denies every non-COMPLETE catchup.
TASK018 deliberately leaves the exact NATIVE_CONTEXT_HANDOFF catchup non-COMPLETE while
the visible Player remains alive.
Therefore visible work is rejected before DecisionEngine increments decisionSequence.

First prove this with retained observe019 state + deterministic RED.
If false, STOP `BLOCKED_HYPOTHESIS_FALSE`.

Fix only the exact foreground handoff decision admission:
- ordinary non-COMPLETE catchup remains fenced;
- HISTORICAL_BASELINE remains fenced from ordinary decisions;
- no catchup completion/advance/reset;
- no Player/ThreadPool/AutoPlay/AutoUse/combat/schema changes.

Then run fresh observe020 and prove native visible farm:
>=5 same-epoch farm cycles, kills/rewards, EXP/SP gain.
M1 remains OPEN.
