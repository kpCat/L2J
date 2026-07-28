# REVIEW FINDINGS — Goal 012

## P1 — worker dispatch can be silently rejected

The production void dispatcher discards `ThreadPool.schedule()`'s nullable
future. `_workerClaimed` may remain true forever. Claim-to-dispatch is also not
ordered with STOPPING.

## P1 — worker Throwable can strand ownership

Worker release is not in a top-level finally and session processing catches only
RuntimeException.

## P1 — cleanup failure is hidden

An exception from canonical action cancellation is swallowed, then the actor
lease is closed and cleanup is reported complete.

## P1 — PICK_UP is outside cleanup ownership

The session remembers a loot object, but cleanup receives only combat target and
skill. PlayerAI PICK_UP can outlive the combat lease.

## P1 — disappearance is treated as successful loot

A ground item leaving the candidate list is counted as actor acquisition without
inventory/object-ownership evidence.

## P1 — selected skill need not be hostile

A positive one-target active skill can pass the current shape/type checks.

## P1 — respawn is not plan/session owned

Respawn has no cancellation token or post-acquisition state/session
reconciliation and can overlap combat or STOPPING.
