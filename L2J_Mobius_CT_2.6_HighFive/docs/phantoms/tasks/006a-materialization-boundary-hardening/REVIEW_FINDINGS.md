# REVIEW FINDINGS — Goal 006

## P1 — split World identity

The actor checks only `World.getPlayer`. `World.addObject` can retain a non-Player
in the general map while adding the Player to the Player map. Reject both maps
and autosave before claim/load/spawn and verify exact identity after spawn.

## P1 — action after STOPPING

Service state read and actor admission are separate. Make them one bounded
critical section on `_stateMonitor`.

## P1/P2 — shutdown wall-clock contract

Current deadline does not bound entry-monitor acquisition or store/delete in the
caller. Use one tracked service-level drain command on existing ThreadPool and a
bounded caller wait without releasing ownership on timeout.

## Documentation

Task 004B verifier SHA is mislabeled as Goal 005 verifier SHA. Correct it without
inventing a missing full SHA.
