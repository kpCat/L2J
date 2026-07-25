# ARCHITECTURE — Task 004

## 1. Component graph

```text
Player
  -> PlayerOutboundSession
       -> ClientBound adapter
            -> current GameClient.sendPacket
       -> Headless adapter
            -> bounded diagnostic summary
            -> ServerPacket.runImpl(Player), exactly once

GameClient/CharacterSelect/Disconnection
  -> shared character identity lease protocol

PhantomPlayerMaterializationSpike
  -> identity claim
  -> Player.load
  -> headless output attachment
  -> explicit domain initialization
  -> World spawn
  -> action admission
  -> PhantomActionFacade
  -> store/delete/detach/release
```

## 2. Real-client compatibility

```text
Player.sendPacket(packet)
  -> non-null check
  -> client-bound adapter
  -> current Player.getClient()
  -> current GameClient.sendPacket(packet)
  -> writePacket
  -> packet.runImpl
```

No network/effect code is duplicated in Player.

## 3. Headless dispatch

```text
Player.sendPacket(packet)
  -> attached headless adapter
  -> recursion budget
  -> optional bounded class-name record
  -> packet.runImpl(player)
  -> nested Player.sendPacket is handled by same adapter
```

No serialization/write/close/session emulation.

## 4. Ownership

```text
objectId -> tokenized owner lease

NONE -> REAL_LOGIN -> NONE
NONE -> PHANTOM    -> NONE
```

Stale tokens cannot release current owners. Phantom World activation must not
redesign ordinary real-real login behavior.

## 5. Spike lifecycle

```text
STORED -> CLAIMED -> LOADING -> MATERIALIZING -> ACTIVE
ACTIVE -> DEMATERIALIZING -> STORED
```

Failure reverses completed steps in reverse order.

## 6. Safe action

```text
inventory baseline
  -> canonical add one fixed fixture item
  -> canonical remove same item
  -> inventory baseline
```

No request packet and no direct SQL.

## 7. Success boundary

Task 004 succeeds only if the minimal seam works with bounded production
touchpoints. It does not implement profiles, final lifecycle orchestration,
activity levels, AI or gameplay.
