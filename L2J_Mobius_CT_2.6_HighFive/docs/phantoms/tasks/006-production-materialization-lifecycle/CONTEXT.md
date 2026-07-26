# CONTEXT — Goal 006

```text
Branch: feature/phantom-world
Baseline: 9d0465eb62f9913644fab9f1d60feb2f4fd9a674
Goal 005: ACCEPT
ADR 0001: Accepted
Goal 006: ALLOWED
Goal 007: NOT_STARTED
```

Existing proven seams:

- canonical Player without TCP;
- exactly-once packet effects;
- tokenized PHANTOM/REAL_LOGIN ownership;
- retryable cleanup and terminal STORED;
- exact object-ID cleanup postconditions;
- persistent profile identity and optional unique character link;
- stateless repository with optimistic locking.

The Task 004 materializer is still a feasibility spike with fixture-item logic.
Goal 006 extracts one production lifecycle core and makes the spike a thin test
wrapper. Production service never knows the fixture item ID.

A failed real cleanup currently retains ownership but lacks explicit recoverable
state. Goal 006 adds RETAINED state and strict on-demand recovery. Live RESERVED
ownership remains unrecoverable.

Goal 006 persists no ACTIVE runtime state: after restart profiles remain, service
starts empty, and materialization remains explicit.
