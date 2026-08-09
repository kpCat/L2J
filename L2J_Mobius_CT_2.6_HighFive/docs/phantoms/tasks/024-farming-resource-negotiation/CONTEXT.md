# Goal 024 context

## Roadmap scope

Goal 024 is **Farming spot negotiation and resource conflict**.

The roadmap requires:

```text
claims on mob groups / rooms
alternatives
real remaining amount
agreement history
share / wait / move / refuse / escalate semantic acts
perceptible-history protection
decisions explainable by both sides' actual goals and current world facts
```

Actual PvP/PK execution belongs to Goal 025 and is forbidden here.

## Existing authorities to reuse

### Goal 021 acquisition

`PhantomAcquisitionState` already owns the factual acquisition identity: goal ID/revision, target item, required amount, progress, selected source, ranked candidates and phase/status. Therefore `remainingAmount = requiredAmount - progress` must be derived from Goal 021. Goal 024 must not invent another farming goal, item counter or source planner.

`PhantomAcquisitionState.Source` already contains `sourceId`, method, `npcId`, `itemId`, `factKey`, `topologyNodeId`, `anchorId`, `instanceId` and exact spoil/sweep capability IDs. `PhantomAcquisitionService.switchSource(...)` already owns source mutation. Goal 024 MOVE delegates to it.

### Goal 010 topology/perception

Topology node kinds include `ROOM`, `CATACOMB`, `DUNGEON`, `FARMING_AREA`; anchor roles include `FARMING` and `ROOM_CENTER`. Existing perception computes bounded same/one-hop perceptibility from the current topology generation and profile registry.

The accepted topology scheduler signal ledger has exactly three provider-owned sources:

```text
topology.local_chat
topology.combat
topology.targetability
```

Do **not** add a fourth `resource_conflict` scheduler source. Add/reuse a narrow Goal010-owned bounded read-only `perceptibleProfiles(...)` query instead of `listProfiles()` or a global scan.

### Goal 018 social

Goal 018 owns personality, relationships, reputation, memories and agreement counters. Existing modifiers include `goal.persistence`, `risk.tolerance`, `conflict.escalation`, `conversation.warmth`. Existing generic social events include `agreement.fulfilled` and `agreement.broken`.

Goal 024 may add narrowly scoped farming offer/accept/refuse/escalation event definitions to the existing social catalog, but must not create another relationship/memory store.

### Goal 020 conversation

Goal 020 owns language, outbound chat and typed query/action execution. Goal 024 must not introduce a phrase bank or send packets/chat directly. Expose typed read-only conflict facts through a narrow conversation seam and let Goal020 verbalize them.

### Goal 017 Party

If two conflicting Phantoms are already in the same exact canonical Party, resource use is cooperative by default. Goal 024 may query Goal017 state read-only; it must not recreate Party membership or route ownership.

## File-scope rule

There is no numeric file-count budget. This task supplies a pre-audited read set and expected change set. Codex may read/change additional High Five files only when exact call-path necessity proves it; every expanded changed file must be explained in the report. No broad unrelated scan/refactor/other chronicle work.
