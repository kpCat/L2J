# PERCEPTION — Goal 010

Explicitly registered profile locations are mapped to the most specific topology
node.

Channels:

```text
LOCAL_CHAT
COMBAT
TARGETABILITY
```

One-hop perception only.

Signals:

```text
topology.local_chat     -> NEARBY_PERCEPTIBLE
topology.combat         -> participant ACTIVE, neighbor NEARBY_PERCEPTIBLE
topology.targetability  -> active target ACTIVE, inactive withdraw
```

A same-node or allowed-neighbor perceptible profile can never receive WARM,
BACKGROUND or SLEEPING from the event.

Providers submit through a narrow scheduler signal port and never invoke
materialization/navigation directly.
