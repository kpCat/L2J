# ARCHITECTURE — Goal 006

```text
PhantomSystem
  -> PhantomProfileRepository
  -> PhantomMaterializationService
       -> fair bounded permits
       -> activeByProfile / activeByCharacter
       -> PhantomMaterializedPlayer
            -> PHANTOM identity lease
            -> canonical Player
            -> headless output
            -> ActionLease counter
            -> retryable cleanup
       -> PhantomRetainedIdentityRecovery
```

Materialization:

```text
profile lookup
→ linked character
→ reserve profile/character + cap
→ recover only safe RETAINED real owner
→ claim PHANTOM
→ Player.load
→ output/init/online/spawn
→ ACTIVE
```

Cleanup:

```text
close admission
→ drain
→ stop/store/delete
→ object-ID postconditions
→ detach output/release identity
→ remove maps/release permit
→ STORED
```

Failure before postconditions retains all ownership and capacity.

Restart starts with empty runtime maps and preserved profile rows.
