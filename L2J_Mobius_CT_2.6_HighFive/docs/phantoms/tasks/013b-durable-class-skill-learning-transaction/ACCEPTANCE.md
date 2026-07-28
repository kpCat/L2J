# Acceptance — Goal 013B

## Git and scope

- [ ] Required parent `06929a2973ca2450688d413b4d58de034194053f`.
- [ ] Branch `feature/phantom-world`.
- [ ] One ordinary child.
- [ ] Subject `fix(phantoms): make class skill learning durable`.
- [ ] Remote exact.
- [ ] No amend/rebase/squash/merge/force push.
- [ ] High Five only.
- [ ] `.gitignore` unchanged.
- [ ] No `.l2j`.
- [ ] No config/schema/migration/production DB.
- [ ] Goal 014/015/017/025 not started.

## Durable transaction

- [ ] Dedicated transaction facade.
- [ ] One connection and one DB transaction.
- [ ] Exact row locks and deterministic order.
- [ ] Main and subclass SP paths.
- [ ] Exact class-index skill row.
- [ ] Exact item object row.
- [ ] Guarded affected-row counts.
- [ ] Rollback on every pre-commit failure.
- [ ] Runtime unchanged on rollback.
- [ ] Runtime apply only after commit.
- [ ] `Player.addSkill(skill, false)`.
- [ ] No progression `addSkill(skill, true)`.
- [ ] Fresh durable postcondition query.
- [ ] Fresh runtime postcondition query.
- [ ] Event only after both.
- [ ] No second event/cost on idempotency.
- [ ] Post-commit invariant failure is fail-stop, never false SUCCESS.

## Tests

- [ ] Real main-class DB + reload proof.
- [ ] Real subclass DB + reload proof.
- [ ] One-item success.
- [ ] Zero-item success.
- [ ] Pre-commit fault matrix.
- [ ] Post-commit fail-stop proof.
- [ ] Conflict matrix.
- [ ] Concurrent same-profile proof.
- [ ] Autosave race proof.
- [ ] Unsupported shape fail-closed.
- [ ] All operation/lease counts drain.
- [ ] Existing 013/013A regressions PASS.
- [ ] `ant verify` PASS.
- [ ] `ant jar` PASS.
- [ ] Verifier 2× byte-identical with same SHA-256.

## Documentation

- [ ] Contract updated.
- [ ] Stale lower roadmap snapshot corrected.
- [ ] Honest report.
- [ ] Goal 013/013A/013B not self-accepted.
- [ ] Success token `GOAL_013B_DURABLE_CLASS_SKILL_LEARNING_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW` only after all gates.
