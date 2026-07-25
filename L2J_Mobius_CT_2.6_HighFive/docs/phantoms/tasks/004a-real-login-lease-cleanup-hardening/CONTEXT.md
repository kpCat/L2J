# CONTEXT — Task 004A

## Reviewed implementation

```text
Task 004 commit: 5b22b1ee9bab556cd5a14c2212dfa3f4119c4566
Parent: 1ca74a3d96e8fa51612ef3e5145c7398abf60f6d
Technical feasibility: ACCEPT
Commit integration: FIX_REQUIRED
```

Accepted evidence remains:

- canonical Player without TCP;
- Player-owned outbound seam;
- exactly-once packet effects;
- test-only canonical create/load/materialize/reload;
- observer visibility;
- test DB only;
- no fake GameClient/Connection or Player fork.

## Finding summary

1. `CharacterSelect` holds `_playerLock`; `GameClient.onDisconnection` does not.
   Disconnect may release a lease after `GameClient.load` but before Player bind.
2. REAL_LOGIN lease is acquired even when Phantom config is disabled, changing
   ordinary concurrent-login behavior.
3. `Disconnection.storeAndDelete` releases in `finally` after exceptions without
   proving World/offline/autosave cleanup.
4. Materializer releases/clears/marks finished after store/delete exceptions and
   never returns successful cleanup state to `STORED`.

## Roadmap

The revised roadmap may be a docs-only child commit after Task 004. It is not
Task 004A code and must remain unchanged by Codex.
