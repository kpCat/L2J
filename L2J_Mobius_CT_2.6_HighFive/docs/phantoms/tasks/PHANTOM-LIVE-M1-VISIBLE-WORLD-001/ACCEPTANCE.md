# ACCEPTANCE — PHANTOM-LIVE-M1-VISIBLE-WORLD-001

Final GREEN vocabulary:
`GREEN — M1 VISIBLE WORLD PROVEN`

## 1. Baseline/scope
- branch `feature/phantom-world`;
- initial HEAD exactly `7872e18fbf37177cf08ab23f342a25e3742bb1d0`;
- 10k scale and pilot reused, not reimplemented;
- no unrelated refactor;
- no direct PLAY DML/DDL/reset/reseed/delete;
- budgets unchanged.

## 2. Human interaction budget
Target: ONE USER_CLIENT_ACTION total.
Hard max: TWO total.

USER_CLIENT_ACTION includes login/relogin, arm code, manual travel/command, screenshot/inspection.

Do all source analysis, tests, build and deployment before first user action. Reuse a valid binding if safe. After code-changing restart request the 8-char arm only when final candidate is ready. Third request => `M1_HUMAN_INTERACTION_BUDGET_BLOCKED`.

## 3. Root-cause evidence
Before visible-life fix:
- identify ordinary materialized idle phantom;
- capture selected decision trace;
- state exact Goal/candidate/handler/runtime condition;
- regression must fail before fix.

No root cause = no speculative fix.

## 4. Materialization envelope
Automated proof:
- promotion/materialization before source-backed client visibility boundary;
- spatial and/or temporal hysteresis prevents edge flicker;
- same-instance respected;
- topology/perception bounded;
- materialization cap respected;
- existing busy/action safety valid.

Connected proof records TestAdmin route/time, target durable position, locality transition, materialization time/distance, source-backed threshold/margin and return/dematerialization behavior.

If geometry alone cannot establish UX, one final 30–60 second visual check is allowed within the same human-action budget.

## 5. Visible autonomous life
Without `.phantom*`, test-only goals, direct SQL or pilot control of phantom:
- ordinary phantoms naturally materialize;
- selected phantom progresses through production decision chain;
- trace shows `Goal -> candidate -> plan/handler -> native action/result`;
- at least one real native world action beyond standing is proven: movement, ordinary monster engagement/attack, or policy-driven recovery/sit;
- `WORK_DELIVERED` alone is not success.

Prefer sample of 3 naturally materialized phantoms. If fewer exist, do not fabricate.

## 6. Safety
- no fatal/OOM;
- no identity duplicates;
- TestAdmin remains REAL_LOGIN/non-GM/no PersonalQoL;
- cap not exceeded;
- no tuning;
- no thread/timer per phantom.

## 7. Verification/deploy
Run focused targets only. No full verify.
Before deployment: focused GREEN, `git diff --check`, clean detached `ant jar` exit 0, SHA recorded. Deploy only after automated work complete.

## 8. Connected workflow
Default one final deployment + one TestAdmin session. If first connected run reveals one small concrete defect, one fix/rebuild and second/final binding allowed. Anything beyond that is BLOCKED.

## 9. Outputs
Task directory:
- `RESULT.md`
- `STATE.md`
- `EVIDENCE.md`
- `M1_VISIBLE_SCENE.tsv`
- `MATERIALIZATION_MEASUREMENT.tsv`
- `IDLE_ROOT_CAUSE.md`

Global product docs may be updated only to preserve newly proven facts.

## 10. Final
On GREEN stop pilot runner/actions and leave healthy LocalPlay RUNNING. Commit/push reports and STOP. Do not start M2.
