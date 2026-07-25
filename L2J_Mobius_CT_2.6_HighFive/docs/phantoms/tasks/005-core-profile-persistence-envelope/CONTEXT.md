# CONTEXT — Goal 005

## Accepted baseline

```text
Branch: feature/phantom-world
Commit: f5b66c4edf1ddf18e044ef8c692d70ecea616485
Task 004/004A/004B: ACCEPT
ADR 0001: ready for Accepted
Goal 005: ALLOWED
Goal 006: NOT_STARTED
```

Task 004 proved the canonical active actor. Goal 005 creates only durable profile
identity and a versioned state envelope independent of a loaded `Player`.

The current test DB manifest becomes stale after adding the new game SQL script,
so explicit test-only re-provisioning is mandatory.

A known false-red thread-delta race is stabilized in the test environment only.
Production ThreadPool and Phantom lifecycle code remain frozen.

Future ownership:

- Goal 006: materialization and retained-lease recovery;
- Goal 007: scheduler/activity states;
- Goal 008: goals/Utility AI;
- Goal 016: schedules/population;
- Goal 018: personality/memory.
