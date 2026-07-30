# Failure and ownership matrix

## Canonical invitation boundaries

Inject or race at:

```text
before leader PREPARED
after leader PREPARED
after member claim
after transaction request fields
after prompt/inbound delivery
accept versus cancel/timeout
leader change before accept
party becomes full before accept
invitee joins another party
inviter enters Rift/event/Olympiad/jail
canonical join before durable COMMITTED
durable commit before response delivery
```

Every case yields one exact canonical membership or none; no duplicate member,
stuck active requester, stuck pending invitation or orphan claim.

## Group persistence

Test failures for every optimistic leader/member update. Reconstruct the
coordinator after each phase. Same operation is idempotent; different operation
at same generation conflicts; stale generation cannot mutate.

No real-player consent is reconstructed.

## Leader/member loss

```text
leader disconnect
member disconnect
leader dematerializes
member dematerializes
canonical automatic leader change
all members absent
real leader leaves
simultaneous leave and invite accept
shutdown during recovery
```

Expected result is deterministic generation advance, recovery, vacancy or SOLO;
never two leaders or two canonical parties for one group.

## External action ownership

Race:

```text
combat start versus PARTY_SUPPORT
combat start versus PARTY_ROUTE
cancel versus cast/move issue
shutdown versus issue/await
actor dematerialization
skill/target/party changes between issue and await
```

Exactly one owner wins. All counters and ActionLeases return to zero.

## Route

```text
navigation queue backpressure
route hash drift
closed/unreachable path
leader moves to another instance
member exceeds max separation
member death
member enters combat
stuck/timeout
leader route generation changes
```

No snap, free teleport, stale movement or follower-selected destination.

## Background and restart

A canonical Party is materialized-only. Demotion/restart stores coordination
intent, not XP/drop or Party objects. Goal 015 background farming remains solo
and must reject a profile with committed live party intent unless the group is
explicitly dissolved/recovered.
