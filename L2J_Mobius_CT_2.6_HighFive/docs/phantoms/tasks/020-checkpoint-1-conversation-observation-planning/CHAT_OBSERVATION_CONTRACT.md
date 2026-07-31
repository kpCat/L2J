# Actual-delivery chat observation contract

## Why Say2 plus CreatureSay

`OnPlayerChat` occurs before the final general say filter and describes the
sender event, not the actual recipients. Goal 020 must not infer local/trade
visibility.

The accepted seam is:

```text
Say2 after all filters
    opens one dispatch scope
chat handler creates one or more CreatureSay packets
each packet captures the dispatch descriptor
CreatureSay.runImpl confirms each actual recipient
```

## Dispatch identity

A process-local monotonic dispatch sequence is sufficient because client chat is
not replayed after restart. The persistent conversation state stores the
observation hash for in-process duplicate safety, not a durable chat event log.

Hash input:

```text
dispatch sequence
speaker object ID
channel ID
final filtered text hash
server epoch millis
```

Every recipient observation for one dispatch shares the same dispatch ID/hash.

## Delivery callback

The callback receives only immutable bounded primitives. It cannot:

- modify or suppress chat;
- access packet buffers;
- retain Player;
- block on DB/parser/social work;
- throw into ordinary chat behavior.

## Managed fast path

The Phantom delivery first checks the process-local identity lease:

```text
owner != PHANTOM → ignored without DB
owner == PHANTOM → bounded queue offer
```

Profile lookup happens later on the shared scheduler.

## Channel policy

- WHISPER: direct recipient.
- PARTY: one deterministic responder.
- GENERAL/TRADE: exact-name address only.
- unsupported channels: no observation plan.
- generated Phantom origin: reserved but ignored in Checkpoint 1.
