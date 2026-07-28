# ARCHITECTURE — Goal 012

```text
Decision plan
  combat.start
      ↓ plan-scoped cancellation token
PhantomCombatService
      ↓ exact materialization ActionLease
L2jCombatBackend
      ↓ canonical server APIs
PlayerAI ATTACK / CAST / PICK_UP
```

One active session per profile. One shared transient pulse worker for all
sessions. No per-profile task or Future.

Sessions store IDs, immutable snapshots, threat/loadout state and an opaque actor
lease. They do not retain target Creature/WorldObject instances.
