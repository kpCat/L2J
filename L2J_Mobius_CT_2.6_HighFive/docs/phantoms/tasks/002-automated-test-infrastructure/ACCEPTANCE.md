# ACCEPTANCE — Task 002

## A. Git/scope

- [ ] Baseline `7aa24faf...`.
- [ ] Branch `feature/phantom-world`.
- [ ] One ordinary task commit.
- [ ] High Five only.
- [ ] Exact allowed production files only.
- [ ] No Task 003.
- [ ] No Player/GameClient/GameServer/network changes.
- [ ] No master plan/Agents/ADR/old reports/verifiers changes.
- [ ] No amend/rebase/force push.
- [ ] No binary/test JAR.

## B. Build

- [ ] Existing compile/jar behavior preserved.
- [ ] JDK 25.
- [ ] Separate test bin/resources/reports.
- [ ] Test classes absent from production JAR.
- [ ] `test` target.
- [ ] `verify` target.
- [ ] `prepare-phantom-test-db`.
- [ ] `phantom-db-test`.
- [ ] `phantom-scenario-test`.
- [ ] `phantom-performance-smoke`.
- [ ] `phantom-negative-control`.
- [ ] `phantom-db-guard-negative-control`.
- [ ] `phantom-static-verify`.
- [ ] All Java test runs forked.
- [ ] Missing DB config fails, not skips.

## C. Test runtime

- [ ] JDK-only.
- [ ] No reflection discovery.
- [ ] Explicit suites.
- [ ] Stable ordinal order.
- [ ] Assertions.
- [ ] Exit 0/1/2/3 contract.
- [ ] Text report.
- [ ] XML report.
- [ ] Seed on failure.
- [ ] No hidden SKIP.
- [ ] Runner negative control proves failure detection.

## D. Production compatibility

- [ ] `DatabaseConfig.load()` unchanged externally.
- [ ] Explicit config overload.
- [ ] Production default path unchanged.
- [ ] `DatabaseFactory.init()` semantics preserved.
- [ ] Explicit fail-fast init.
- [ ] No test DB policy in production factory.
- [ ] Pool closes/reopens in test lifecycle.
- [ ] `ant jar` PASS.

## E. DB guard

- [ ] Path under `.phantom-local`.
- [ ] Production config rejected.
- [ ] Local host only.
- [ ] Port 3308.
- [ ] Exact case-sensitive test DB.
- [ ] Exact dedicated user.
- [ ] Production/empty/unknown rejected.
- [ ] URL credentials/multihost/extra path rejected.
- [ ] Guard before driver/Hikari.
- [ ] Sentinel marker absent.
- [ ] Connection attempts zero.
- [ ] Negative exit exact 2.

## F. Provisioning

- [ ] Admin credentials environment-only.
- [ ] No credentials output/report/Git.
- [ ] Exact target constants.
- [ ] Test DB recreate.
- [ ] Dedicated user recreate.
- [ ] Random local password.
- [ ] Grants test DB only.
- [ ] Existing login schema installed.
- [ ] Existing game schema installed.
- [ ] Stable script order.
- [ ] Script inventory/hashes.
- [ ] Strict fail-first SQL.
- [ ] Unsupported syntax handled or BLOCKED.
- [ ] Partial failure rollback/drop.
- [ ] Atomic local config.
- [ ] Lock.
- [ ] Re-run succeeds.

## G. Test DB

- [ ] `.phantom-local` ignored.
- [ ] Config not tracked.
- [ ] DB exact `l2jmobiush5_phantom_test`.
- [ ] User exact `l2j_phantom_test`.
- [ ] Max pool <= 4.
- [ ] Backup false.
- [ ] Connection fan-out false.
- [ ] Production DB not read/mutated.
- [ ] Core tables accounts/characters/items.
- [ ] Versioned test migration.
- [ ] Migration idempotent.
- [ ] Fixture owner explicit.

## H. DB integration

- [ ] Current DB exact.
- [ ] Current user dedicated.
- [ ] Grants no production/global privilege.
- [ ] Transaction rollback.
- [ ] Committed fixture.
- [ ] Cleanup once.
- [ ] Cleanup twice.
- [ ] Zero residue.
- [ ] Pool closed.
- [ ] No leaked non-daemon DB thread.

## I. Determinism/scenario

- [ ] Seed `20260725001`.
- [ ] Same seed repeatable.
- [ ] Scenario fixture.
- [ ] First ten values correct.
- [ ] SHA-256 exact.
- [ ] Machine-readable report includes seed/checksum.

## J. Performance smoke

- [ ] >=200000 operations.
- [ ] O(1) state.
- [ ] Fixed seed.
- [ ] Deterministic checksum.
- [ ] <30 sec.
- [ ] No executor/thread.
- [ ] Measurement recorded.

## K. Verification

- [ ] Unit suite PASS.
- [ ] Runner negative control PASS.
- [ ] DB guard negative PASS.
- [ ] DB integration PASS.
- [ ] Scenario PASS.
- [ ] Performance PASS.
- [ ] `ant verify` PASS.
- [ ] `ant jar` PASS.
- [ ] Static verifier pre-commit PASS.
- [ ] Static verifier final run 1 PASS.
- [ ] Static verifier final run 2 PASS.
- [ ] Final outputs identical.
- [ ] `git diff --check` PASS.
- [ ] UTF-8 PASS.
- [ ] Mojibake 0.
- [ ] Escaped Cyrillic 0.

## L. Report/push

- [ ] Report complete.
- [ ] Suite/test counts.
- [ ] SQL counts/hashes.
- [ ] DB guard ordering proof.
- [ ] Credentials redacted.
- [ ] Production access/mutation explicitly false.
- [ ] Performance measurement.
- [ ] Deviations/risks.
- [ ] Parent.
- [ ] Commit.
- [ ] Push.
- [ ] Remote ref.
- [ ] Clean tree.
- [ ] Manual gate `PENDING_INDEPENDENT_REVIEW`.
- [ ] Task 003 `NOT_STARTED`.
