# REVIEW FINDINGS — Goal 006A

## P1 — generic disconnect bypasses lifecycle owner

`disconnectAllCharacters()` invokes `Disconnection` for every World Player
before `PhantomSystem.shutdownIfStarted()`.

## P1 — tracked drain loses its executor

If Phantom shutdown is incomplete, `Shutdown` immediately stops the shared
ThreadPool that hosts the tracked drain, with no second explicit retry.

All local Goal 006A code is accepted and must remain.
