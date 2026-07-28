# TEST CASES — Goal 012A

Worker:

- false/null/throwing/silent dispatch rejection;
- claim-to-STOPPING race;
- cancel scheduled-not-started handle;
- inline dispatcher;
- stale claim;
- backend Error with top-level finally.

Cleanup:

- canonical cleanup failure retains lease;
- bounded retry and exhaustion;
- finishStop/consume blocked;
- exact ATTACK/CAST/PICK_UP cancellation;
- foreign action preservation.

Loot:

- positive actor acquisition;
- another-player pickup;
- despawn/delete;
- actor/item out of radius;
- partial requires at least one proven acquisition.

Skills:

- hostile skill accepted;
- positive one-target buff rejected;
- mode mismatch and PvP/suicide/special rejected.

Respawn:

- cancelled token before/after blocked acquisition;
- active session and cleanup-pending gates;
- STOPPING rejection and in-flight stop barrier.
