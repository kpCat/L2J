# Acquisition source and phase contract

## Source candidate

```text
sourceId
method
targetItemId
npcId optional
topologyNodeId optional
topologyAnchorId optional
instanceId
factKeys
capabilityEvidence
integerScore
status
reason
authorityHashes
```

Source ID is full SHA-256.

## Candidate status

```text
ELIGIBLE
INELIGIBLE
COOLDOWN
AMBIGUOUS
PLANNING_ONLY
DEFERRED_CHECKPOINT_2
STALE
```

Only eligible death-drop and spoil/sweep sources execute.

## Active spoil phases

| Phase | Durable truth | External boundary |
|---|---|---|
| TARGET_REQUIRED | selected source | bounded target lookup |
| SPOIL_PREPARED | target/source/skill | none |
| SPOIL_DISPATCHING | receipt before cast | canonical spoil once |
| SPOIL_OBSERVED | canonical spoil proof | Combat submit |
| COMBAT_SUBMITTED | Combat operation | observe only |
| COMBAT_TERMINAL | exact corpse | none |
| SWEEP_PREPARED | corpse/sweep skill | none |
| SWEEP_DISPATCHING | receipt before cast | canonical sweep once |
| VERIFYING | before count/operation | inventory/corpse observation |

Recovered DISPATCHING never automatically recasts.

## Background identity

```text
profile/character
Goal ID/revision
activity generation/tick
method/source ID/acquisition version
NPC/anchor/model version
authority hashes
```

## Switch policy

Replace a source only after zero Combat, external-action and background claims and
a durable terminal failure/cooldown. Baseline/progress never reset.
