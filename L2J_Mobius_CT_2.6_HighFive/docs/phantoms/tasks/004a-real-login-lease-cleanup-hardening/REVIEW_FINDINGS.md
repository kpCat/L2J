# REVIEW FINDINGS — Task 004

## P1 — CharacterSelect/onDisconnection race

`GameClient.load` attaches the REAL_LOGIN lease before `CharacterSelect` binds
`Player` to the client. `CharacterSelect` owns `_playerLock`, but
`onDisconnection` does not.

Possible ordering:

```text
CharacterSelect: load Player, lease moved to GameClient, client.player still null
onDisconnection: sees null player, releases lease, sets DISCONNECTED
CharacterSelect: binds Player and sets online
```

The loaded/bound Player then has no identity lease. This violates unique
ownership.

## P1 — fail-open lease release

`Disconnection.storeAndDelete` releases the lease in `finally` even when
store/delete throws or leaves residue. A second owner may then load the same
identity.

## P1 — materializer fail-open cleanup

The spike records store/delete exceptions but still detaches output, releases
identity, clears Player and marks cleanup finished. Its failure matrix injects
after steps, not actual operation failures.

## P2 — disabled compatibility and terminal state

REAL_LOGIN arbitration currently runs even with Phantom system disabled, so the
ordinary login path is not byte-for-byte semantic legacy.

Successful materializer cleanup remains `DEMATERIALIZING` rather than returning
to terminal `STORED`.

## Decision

```text
Technical seam: ACCEPT
Task 004 commit: FIX_REQUIRED
Revert: NOT_REQUIRED
Task 004A: REQUIRED
Task 005: BLOCKED
```
