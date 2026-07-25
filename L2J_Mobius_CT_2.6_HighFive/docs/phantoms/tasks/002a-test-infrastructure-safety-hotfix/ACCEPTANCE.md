# ACCEPTANCE — Task 002A

## Git/scope

- [ ] Baseline `36e5411e...`.
- [ ] One ordinary commit.
- [ ] High Five only.
- [ ] No production Java.
- [ ] No old verifier change.
- [ ] No Task 003.
- [ ] No config/login/game SQL change.
- [ ] Migration 001 unchanged.
- [ ] No amend/rebase/force push.

## Lock

- [ ] FileChannel/FileLock.
- [ ] tryLock.
- [ ] Non-owner cannot delete.
- [ ] Non-owner cannot overwrite token.
- [ ] Non-owner no JDBC.
- [ ] Persistent stale file harmless.
- [ ] Owner release permits next process.
- [ ] Crash release tested.
- [ ] Cross-process Ant control PASS.

## Freshness

- [ ] Local manifest under `.phantom-local`.
- [ ] Atomic manifest.
- [ ] Deterministic no timestamp.
- [ ] Current SQL inventory includes login/game/all migrations.
- [ ] Manifest survives init-test.
- [ ] Pre-Hikari compare.
- [ ] Missing/stale/malformed reject.
- [ ] Sentinel untouched.
- [ ] DB metadata migration 002.
- [ ] DB metadata exact compare.
- [ ] Re-provision PASS twice.

## Lifecycle

- [ ] afterAll after partial beforeAll.
- [ ] Original failure preserved.
- [ ] Cleanup failure additional.
- [ ] Marker regression PASS.
- [ ] DB suite cleanup null-safe/idempotent.

## URL/secrets

- [ ] Strict query allowlist.
- [ ] Auth keys rejected.
- [ ] Encoded/mixed/duplicate rejected.
- [ ] Query secret redaction.
- [ ] IDENTIFIED BY redaction.
- [ ] No credential output.

## Ant/tests

- [ ] New three targets.
- [ ] Verify includes targets.
- [ ] All Java forked.
- [ ] Unit PASS.
- [ ] Old negatives PASS.
- [ ] Lock control PASS.
- [ ] Freshness negative PASS.
- [ ] Lifecycle negative PASS.
- [ ] DB integration PASS.
- [ ] Scenario PASS.
- [ ] Performance PASS.
- [ ] `ant verify` PASS.
- [ ] `ant jar` PASS.

## Reports/verifier

- [ ] Original Task 002 report actualized.
- [ ] Review record.
- [ ] Task 002A report.
- [ ] `verify-task-002a.ps1`.
- [ ] Pre-commit PASS.
- [ ] Final run 1 PASS.
- [ ] Final run 2 PASS.
- [ ] Outputs identical.
- [ ] UTF-8.
- [ ] Mojibake 0.
- [ ] Escaped Cyrillic 0.
- [ ] Push/remote SHA.
- [ ] Manual gate pending.
- [ ] Task 003 not started.
