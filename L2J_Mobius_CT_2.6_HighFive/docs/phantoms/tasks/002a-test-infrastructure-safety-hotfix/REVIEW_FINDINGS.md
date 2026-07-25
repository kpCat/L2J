# REVIEW FINDINGS — Task 002

## P1 — foreign provisioning lock deletion

### Existing behavior

A contender that cannot acquire a live PID lock throws, but outer `finally` deletes the lock unconditionally.

### Risk

A third process can enter while the original owner is still recreating test DB.

### Required proof

- real cross-process contention;
- owner token/file unchanged by contender;
- no JDBC/destructive attempt by contender;
- acquisition succeeds after owner releases.

## P1 — no schema freshness proof

### Existing behavior

Schema manifest is stored under cleaned build reports.

`ant verify` only sees existing local config and DB tables.

### Risk

Repository SQL changes while test DB remains old; verify can be false green.

### Required proof

- durable `.phantom-local/schema-manifest.properties`;
- deterministic current inventory;
- pre-Hikari exact comparison;
- DB metadata exact comparison;
- stale manifest sentinel remains untouched.

## P2 — partial beforeAll cleanup gap

### Existing behavior

`afterAll` runs only after successful `beforeAll`.

### Risk

Partially acquired pool/resources can miss explicit cleanup.

### Required proof

A suite creates resource, fails in `beforeAll`, and `afterAll` removes resource.

## P2 — JDBC query auth properties

### Existing behavior

URI userinfo is rejected, query keys are not restricted.

### Risk

Authentication secrets can be supplied in URL and leak through URL/report/error paths.

### Required proof

Strict query allowlist and mixed/encoded auth key tests.

## Documentation

Task 002 report still has post-commit placeholders. Record immutable commit/push/verifier facts.
