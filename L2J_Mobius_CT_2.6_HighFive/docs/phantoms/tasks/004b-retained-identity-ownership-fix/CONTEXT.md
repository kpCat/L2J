# CONTEXT — Task 004B

## Accepted technical direction

Canonical `Player` headless feasibility is proven. Task 004A correctly hardened:

- selection/disconnection locking;
- cleanup postconditions;
- retryable Phantom cleanup;
- terminal STORED state.

## Remaining defect

The retained REAL_LOGIN lease is intended to block reuse after failed cleanup.
However current policy returns false for:

```text
phantom disabled + current owner REAL_LOGIN
```

`GameClient.load` then uses a legacy path that ignores the retained lease.

Additionally, `Disconnection` may release any client lease after cleaning a
different Player because it does not verify matching object ID.

Cleanup policy is also exact-instance based even though identity is object-ID
scoped.

## Correct invariant

```text
disabled + no owner
→ legacy path

any existing owner
→ arbitration/protection, regardless of feature flag
```

And:

```text
release lease
only if lease.objectId == cleanupPlayer.objectId
and object-ID cleanup postconditions are complete
```

## Current Git

```text
Task 004: 5b22b1ee9bab556cd5a14c2212dfa3f4119c4566
Roadmap update: 441877e75feed482b58c2b0647137739b5b07748
Task 004A: d36e10e24787edce3fe4f4d933fca4d0ac884d50
```

Roadmap must remain byte-identical.
