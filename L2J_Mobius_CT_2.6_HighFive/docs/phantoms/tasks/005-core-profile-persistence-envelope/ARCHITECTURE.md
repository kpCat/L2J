# ARCHITECTURE — Goal 005

```text
PhantomProfileRepository
  -> DatabaseFactory connection per operation
  -> phantom_profiles
  -> phantom_profile_components

No GameServer/PhantomSystem wiring
No cache
No worker
No automatic load
```

Core identity is independent from Player materialization.

`schema_version` describes stored row format. `row_version` is the optimistic
concurrency token.

The component envelope stores opaque payloads up to 4096 bytes and never
interprets future domain state.

Two writers using expected version N produce exactly one N→N+1 success and one
explicit optimistic conflict.

Class presence and schema files alone cause no runtime DB access while Phantom
is disabled.
