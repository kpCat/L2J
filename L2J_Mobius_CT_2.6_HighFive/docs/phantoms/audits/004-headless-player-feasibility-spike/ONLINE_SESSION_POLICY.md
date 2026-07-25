# Online/session policy — Task 004

## Decision

Task 004 reuses the existing integer states and does not add a new value:

| Player state | `isOnline()` | `isOnlineInt()` |
|---|---:|---:|
| real client attached | `_isOnline` | `1` |
| detached real client | `_isOnline` | `2` |
| active headless outbound attached, client null | `_isOnline` | `2` |
| plain null client without headless outbound | `_isOnline` | `0` |
| any session with `_isOnline=false` | `false` | `0` |

Value `2` means a canonical Player that is World-visible but has no active
client transport. This already existed for detached/offline sessions. Task 004
only makes that meaning explicit for an active headless session.

The lifecycle order is deliberate:

```text
attach PHANTOM identity
attach headless outbound
setOnlineStatus(true)
spawn
open action admission
...
close action admission and drain
stop/store/delete
detach outbound
release identity last
```

The default outbound remains client-bound. A plain null-client Player therefore
retains the old no-output and online value `0` behavior.

## Complete `isOnlineInt()` call-site audit

The Task 004 baseline has these production consumers:

| Consumer | Condition | Headless result |
|---|---|---|
| `Player.broadcastUserInfo` | returns only for `== 0` | allowed to broadcast to observers |
| `Player.storeMe` and character store/update paths | persist the integer | active headless is stored as `2`; cleanup writes `0` |
| `AutoPotionTaskManager` | requires `== 1` | excluded |
| `GameClient.load` | `== 1` identifies a real double login | headless never misclassified as real |
| `EnterWorld` HWID loop | requires `== 1`, then dereferences client | headless excluded before dereference |
| `PcCafePointsManager` retail path | excludes `== 0` and offline mode | not reachable from the bounded action facade |
| `PcCafePointsManager` non-retail path | excludes `== 0` | not reachable from the bounded action facade |

The two PcCafe paths are not authorized headless actions. A later task must
either keep them outside its action surface or add a dedicated policy before
calling them. This is a bounded limitation, not silently changed behavior.

## Observer evidence

The headless suite materializes two canonical Players in `World`, verifies
mutual known-list visibility, broadcasts a real `CreatureSay`, and verifies the
packet reaches the observer's headless sink. It also exercises the
`CreatureSay.runImpl` snoop path. The fixture returns to `World`-absent,
autosave-absent and DB online `0` after cleanup.

## Session ownership

- `Player.attachOutboundSession` accepts only `HEADLESS`, only while client is
  null and the default client-bound adapter owns output.
- `Player.setClient(non-null)` rejects a Player with headless output.
- the tokenized attachment closes at most once and a stale token cannot detach
  a newer attachment;
- the real adapter resolves the current `GameClient` at send time and delegates
  to unchanged `GameClient.sendPacket`;
- headless dispatch never constructs `GameClient` or `Connection`, never
  serializes a packet and never writes game-network bytes.

## Gate result

No audited caller is incompatible with value `2` inside the Task 004 lifecycle
and action envelope. Broader gameplay remains unavailable pending later tasks.
