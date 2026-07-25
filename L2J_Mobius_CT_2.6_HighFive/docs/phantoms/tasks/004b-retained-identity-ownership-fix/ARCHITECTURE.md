# ARCHITECTURE — Task 004B

## Arbitration

```text
enabled OR owner exists
→ registry arbitration

disabled AND no owner
→ exact legacy path
```

## Exact lease identity

```text
client retained lease A
cleanup Player B
A != B
→ retain A; never release
```

## Cleanup postconditions

```text
player offline
World.getPlayer(id) == null
World.findObject(id) == null
autosave containsObjectId(id) == false
player.client == null
```

## Release

```text
matching lease
+ no cleanup exception
+ complete object-ID postconditions
→ release

anything else
→ retain and bounded warning
```

## Future boundary

Task 006 may add explicit recovery orchestration for retained leases. Task 004B
must not add periodic retry tasks.
