# SIGNAL OWNERSHIP — Goal 010A

Provider-owned sources:

```text
topology.local_chat
topology.combat
topology.targetability
```

Coordinated unregister removes registry ownership and then withdraws all three
sources with newer overflow-safe sequences under the delivery ordering gate.
Inactive targetability withdraws even after unregister. Failed cleanup is
explicit and retryable.
