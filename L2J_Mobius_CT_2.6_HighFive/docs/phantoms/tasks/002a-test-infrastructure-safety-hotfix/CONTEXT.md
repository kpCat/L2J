# CONTEXT — Task 002A

## Accepted history

```text
Task 001: ACCEPT
Task 001A: ACCEPT
Task 002 commit: 36e5411e01e8e73f8a0fd4d9460e327c28a6798b
Task 002 review: FIX REQUIRED
Task 003: BLOCKED
```

## Task 002 successful evidence

- Unit: 41/41.
- DB integration: 8/8.
- Scenario: 1/1.
- Performance: 1/1.
- SQL files: 116.
- Statements: 204.
- Test DB/user/config isolated.
- Production DB reported untouched.
- `ant verify`: PASS.
- `ant jar`: PASS.
- verifier 70/70 × 3.

Hotfix must preserve these capabilities.

## Current lock defect

Provisioner calls:

```text
acquireLock(lockFile)
```

but common `finally` always calls:

```text
Files.deleteIfExists(lockFile)
```

If a contender sees a live owner and throws, it still deletes the owner's lock file.

## Current freshness defect

Provisioner writes schema manifest to:

```text
../build/phantom-test/reports/schema-manifest.txt
```

Every new Ant invocation runs `init-test`, which deletes `${build.test}`. Therefore later `ant verify` cannot compare test DB schema to current repository SQL.

## Current lifecycle defect

Runner sets `beforeCompleted=true` only after `beforeAll` returns.

If DB suite opens Hikari and then fails during the remaining `beforeAll`, `afterAll` is not called.

## Current URL defect

Guard rejects URI userinfo but accepts query properties such as:

```text
?user=root
?password=secret
```

Generated test URLs do not need these properties.

## Current report provenance

Original Task 002 handoff:

```text
Commit: 36e5411e01e8e73f8a0fd4d9460e327c28a6798b
Parent: 7aa24faf202567add0fa81561242d37453c6055f
Final verifier: 70/70 × 2
Outputs identical SHA-256:
863B235A99D686D99F8B1DA98762DCBD3A683D0E729F66CB88590954A609CE0C
Push: successful
```
