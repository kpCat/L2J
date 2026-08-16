# Goal 026 Checkpoint 1 — raid/epic authority and readiness facts

## Identity
Branch: `feature/phantom-world`
Required parent: `5517081fb2bbf2aa9ad8295130714df2d4b45921`
Required commit subject: `feat(phantoms): add raid readiness authority`
Seed: `26002601`
Target verdict: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`

## Read set
Read first:
1. this entire small task package;
2. `docs/phantoms/CODEX_WORKFLOW_CONTRACT.md`;
3. only the Goal026 section of `docs/PHANTOM_BOTS_ROADMAP.md` plus the minimum
   status block needed to record Goal025/026 truth;
4. directly relevant production files named in `CONTEXT.md`.

Do NOT reread the historical review/report corpus. The supplied
`PRIOR_INDEPENDENT_REVIEW.md` is the dependency handoff.

## Result
Implement only Goal026 Checkpoint 1: passive, bounded, read-only raid/epic
authority + current Party/CommandChannel readiness facts.

It answers current target availability and current-force feasibility through
existing owners. It does not form the force and does not execute the raid.

Follow `ARCHITECTURE.md`, `TEST_CASES.md`, `ACCEPTANCE.md`.

## Expected production surface, not a numeric cap
Likely:
- small new `phantoms/raid/*` model/readiness/authority adapter family;
- minimal Goal011 paged content-kind query if needed;
- narrow Goal017 read-only Party/CommandChannel snapshot seam;
- minimal passive `PhantomSystem` construction only if it creates a useful
  production seam;
- build/test/docs/status.

There is no file-count cap. Extra production files require exact call-path
necessity and must be reported.

## Hard out of scope
No:
- CommandChannel creation/mutation;
- recruitment/invites;
- gathering;
- navigation/route requests;
- entry/teleport/access orchestration;
- raid/epic combat;
- retreat execution;
- persistence/saga;
- scheduler/worker;
- conversation;
- clan strategy;
- damage/DPS/victory simulation;
- broad content-script normalization.

If safe readiness requires one of these, report the boundary instead of
expanding the checkpoint.

## Execution/context budget
This checkpoint is sized for one uninterrupted Codex context.

- Do not reopen Goal025.
- Do not audit unrelated subsystems.
- Do not add acceptance criteria absent from this package.
- A newly suspected gap outside the package is a final-report finding, not a new
  implementation objective.
- Do not test after each edit/hunk.
- Compile only after a coherent implementation block.
- Run focused tests after the corresponding behavior is complete.
- If a focused test exposes a real CP1 defect, fix it and rerun only that affected gate.

**First automatic context compaction is a STOP signal.**
After it:
- no new discovery;
- finish only the coherent block already in progress;
- run remaining mandatory focused gates;
- factual handoff;
- ordinary commit/push.

Do not reconstruct the task after compaction.

## Verification budget
Authorized:
1. one compile/compile-tests after coherent production integration;
2. new CP1 focused tests;
3. Goal011 focused regression only if Goal011 production code changed;
4. Goal017 focused regression only if Goal017 production code changed;
5. one final CP1 focused aggregate after freeze;
6. one final `ant jar`;
7. diff/scope/encoding checks.

Not authorized:
- plain `ant verify`;
- Goal025 214-test aggregate;
- broad all-Phantom regression;
- stress loops;
- repeated green gates;
- test-after-every-edit.

When the specified matrix is green: STOP, document, commit and push.

## Status/docs
Minimal factual updates:
- Goal025A = ACCEPT;
- Goal025 overall = ACCEPT;
- accepted baseline before CP1 = `5517081fb2bbf2aa9ad8295130714df2d4b45921`;
- Goal026 CP1 = `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`;
- Goal026 overall = IN_PROGRESS / not accepted;
- Goal026 CP2+ = NOT_STARTED;
- Goal027+ unchanged.

These statuses come from independent review; this is not Codex self-acceptance.
Do not repair unrelated historical status/verifier inconsistencies.

## Delivery
Ordinary commit exact subject:
`feat(phantoms): add raid readiness authority`

Ordinary push:
`git push origin feature/phantom-world`

Push safe result even if PARTIAL/BLOCKED.

No amend/rebase/squash/reset/force push.

Final report:
- branch
- parent
- commit
- remote HEAD
- subject
- verdict
- exact changed production files and why
- target availability evidence
- group/CommandChannel evidence
- capability/readiness evidence
- exact tests/results
- unfinished findings
- `occurred_context_compaction: yes|no`

Success token:
`GOAL_026_CHECKPOINT_1_RAID_READINESS_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`
