# ARCHITECTURE — Task 004A

```text
CharacterSelect
  -> playerLock
  -> require AUTHENTICATED
  -> optional REAL_LOGIN arbitration
  -> load/bind

onDisconnection
  -> same playerLock
  -> DISCONNECTED
  -> detach/store/delete
  -> release only if cleanup policy complete

Cleanup policy
  -> !player.isOnline
  -> World does not contain exact Player
  -> autosave does not contain Player
  -> player.getClient == null

Phantom cleanup
  -> close/drain admission
  -> restore baseline
  -> store (failure retains owner)
  -> delete (incomplete failure retains owner)
  -> verify policy
  -> detach output
  -> release lease last
  -> clear reference
  -> STORED
```

Disabled policy:

```text
system false + no PHANTOM owner -> legacy real login, no registry lease
system false + PHANTOM owner    -> arbitration required
system true                     -> arbitration required
```
