# ACCEPTANCE — Task 004A

## Git/scope

- [ ] Effective baseline resolved exactly.
- [ ] Revised roadmap preserved unchanged.
- [ ] One ordinary Task 004A commit.
- [ ] High Five only.
- [ ] No Goal 005/schema/config work.
- [ ] No amend/rebase/merge/force push.

## Disabled compatibility

- [ ] Disabled/no PHANTOM owner uses legacy path without lease.
- [ ] Disabled/PHANTOM owner remains protected.
- [ ] Canonical config defaults remain false.

## Race closure

- [ ] CharacterSelect and onDisconnection use same lock.
- [ ] CharacterSelect checks AUTHENTICATED in lock.
- [ ] DISCONNECTED set in lock.
- [ ] No load/bind can finish after disconnect wins.

## Fail-closed release

- [ ] Shared cleanup policy checks offline, World, autosave and client.
- [ ] Autosave gets only a narrow contains query.
- [ ] Incomplete real cleanup retains lease.
- [ ] No unconditional delayed/final release.
- [ ] Retention logging bounded.

## Phantom cleanup

- [ ] Store operation failure retains all ownership.
- [ ] Delete/incomplete failure retains ownership.
- [ ] Retry allowed.
- [ ] Successful cleanup detaches/releases last.
- [ ] Successful state is STORED.
- [ ] Repeated success no-op.
- [ ] Existing 11-point matrix passes.

## Tests/build

- [ ] New focused tests pass.
- [ ] All previous suites pass.
- [ ] ant verify PASS.
- [ ] ant jar PASS.
- [ ] Static verifier pre/final twice PASS.
- [ ] Outputs identical.
- [ ] Production JAR test entries zero.
- [ ] UTF-8/mojibake/escaped Cyrillic PASS.

## Documentation

- [ ] Task 004 immutable evidence recorded.
- [ ] Independent review record created.
- [ ] ADR remains Proposed.
- [ ] Task 004A report complete.
- [ ] Manual gate pending independent review.
- [ ] Task 005 not started.
