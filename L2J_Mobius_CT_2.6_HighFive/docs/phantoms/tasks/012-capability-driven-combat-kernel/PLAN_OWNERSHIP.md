# PLAN OWNERSHIP — Goal 012

An asynchronous handler token belongs to the exact plan execution.

It remains valid while the same plan advances between steps.

It becomes cancelled when that plan is:

- completed;
- replanned;
- timed out;
- retry-exhausted;
- cancelled;
- made terminal;
- replaced by goal/activity/runtime ownership changes;
- stopped or detached.

This prevents a combat session from surviving after its decision plan no longer
exists.
