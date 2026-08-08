# Goal 023A acceptance gates

All gates are mandatory unless the final status is honestly `PARTIAL` or `BLOCKED`.

## A. Baseline and review

- [ ] HEAD before work is exact `840e159a989f6372da9c471c915413f1e4470daf`.
- [ ] Branch is `feature/phantom-world`.
- [ ] `docs/phantoms/reviews/023-independent-review.md` records `CHANGES_REQUIRED` for exact baseline and all blocker families.
- [ ] Goal 022 exact ACCEPT + timing-flake waiver text is preserved.
- [ ] Goal 024+ remains `NOT_STARTED` and no Goal 024 production/test code exists.

## B. Production party integration

- [ ] Existing committed canonical party can bind to exact `rift.prepare` without recreation or membership mutation.
- [ ] No-claim and SOLO-claim live parties reconcile safely.
- [ ] Conflicting claims fail closed.
- [ ] Invite and route both require/use exact binding identity.
- [ ] No `bind/form + invite` in one advance.
- [ ] No blanket `CLAIM_EXISTS -> success` shortcut.

## C. Managed consent

- [ ] Eligible Phantom candidate can ACCEPT through target-side policy and canonical Goal 017 lifecycle.
- [ ] Phantom can REFUSE or DEFER.
- [ ] Explicit join/conversation consent remains compatible.
- [ ] Leader cannot force-replace candidate goal.
- [ ] Ordinary real Player remains exclusively client-controlled.
- [ ] No direct Party mutation, packets or fake GameClient.

## D. Exact mutation/restart identity

- [ ] Candidate is fully revalidated immediately before invite.
- [ ] One stale dimension yields zero invite and zero attempt/cooldown mutation.
- [ ] Preparation schema v2 persists party binding and full invitation identity.
- [ ] Existing v1 payloads decode and force safe replan before mutation.
- [ ] Source/policy/config/role/binding drift fails closed.
- [ ] Optimistic conflict/restart cannot duplicate invitation.
- [ ] REFUSED and EXPIRED are typed distinctly.
- [ ] Effective timeout cannot exceed canonical invitation expiry.
- [ ] `Player.REQUEST_TIMEOUT` unchanged.

## E. Readiness, semantics, discovery and metrics

- [ ] READY requires stable no-conflict Goal 017 binding and final revalidation.
- [ ] Exact pending state is `INVITE_PENDING`.
- [ ] `RIFT_INVITE_REQUEST` and `RIFT_INVITE_REFUSED` are emitted and mapped by Goal 020.
- [ ] Stale semantic receipts are suppressed.
- [ ] Managed Phantom source precedes ordinary real source before <=32 cap.
- [ ] Relationship modifier uses Goal 018 when exact data is available; unavailable is neutral.
- [ ] All required bounded metric families exist without IDs in labels.

## F. Side-effect and scope safety

- [ ] No Rift entry, item consumption, teleport, room jump, spawn or combat.
- [ ] No new worker/thread/executor/Future/task/timer.
- [ ] No global online-player scan.
- [ ] No changes outside High Five module.
- [ ] No `.l2j` change/add/delete.
- [ ] Production DB string/use absent from executable test path.
- [ ] No SQL migration/table.
- [ ] `Player.java`, `Party.java`, `PartyInvitationService.java`, `DimensionalRiftManager.java` unchanged unless TASK escalation rule was met and report proves why; default expected count is zero.

## G. Dynamic proof

- [ ] All nine new focused modes pass with seed `23002311`.
- [ ] Acceptance integration modes instantiate real `PhantomPartyCoordinator` + `L2jPhantomRiftPartyPort`.
- [ ] Canonical invitation path exercised.
- [ ] Original Goal 023 aggregate passes as affected/historical regression.
- [ ] Ant `phantom-static-verify-023` no longer passes `-WorkingTree`; original verifier script remains pinned and historical.
- [ ] Goal 017/020 affected targets pass.
- [ ] New static verifier 023A passes working tree before commit.
- [ ] Final Goal 023A aggregate runs once after freeze and passes.
- [ ] One plain `ant verify` passes after freeze.
- [ ] One standalone `ant jar` passes.
- [ ] Post-commit PS5.1 and available verified PS7 run of verifier 023A produce byte-identical stdout.

## H. Documentation and Git

- [ ] `RIFT_RECRUITMENT_CONTRACT.md` updated to binding/consent/schema-v2 contract.
- [ ] New report `023a-rift-production-integration-corrections.md` is complete and factual.
- [ ] New handoff `023a-independent-review.md` does not self-accept.
- [ ] Both master plan and roadmap show `CORRECTIVE_023A_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW` only after full success.
- [ ] Commit subject exact: `fix(phantoms): harden rift recruitment integration`.
- [ ] Commit is one ordinary direct child of required parent.
- [ ] Push completed; local HEAD equals remote branch head; worktree clean.

## Success status

Only after every gate:

```text
GOAL_023A_RIFT_RECRUITMENT_INTEGRATION_CORRECTED_PENDING_INDEPENDENT_REVIEW
```

This token is not Goal 023 ACCEPT.
