# Architecture correction — Goal 017 completion

## Frozen dependency direction

```text
canonical Party server facts
→ PartyInvitationService / Party backend
→ immutable snapshots
→ durable party claims and contextual role evidence
→ Party coordinator / semantic commands
→ navigation or combat external action lease
→ canonical Player/Party action
```

Core `model/groups` must not import `gameserver.phantoms`.

## Invitation publication protocol

```text
VALIDATED
→ RESERVED in private exact requester/invitee indexes
→ managed PREPARE callback persists exact identity
→ Player request fields + Party pending flag
→ PUBLISHED in observable pending indexes
→ client prompt or managed delivery
→ exactly one TERMINAL outcome
→ remove both indexes and clear exact request ownership
```

No response observes RESERVED. Preparation rejection never publishes a prompt.
Terminal callback occurs outside core lock.

The pending record owns:

```text
identity
requester/invitee object IDs and exact Player references
party-at-invite and distribution
expiry
delivery registration generation
optional managed requester identity
optional managed invitee identity
publication phase
terminal-once guard
```

Delivery registration close drains only records owned by that registration.

## Durable coordinator protocol

For a Phantom leader:

```text
FORMING/PREPARED
→ core prepare callback
→ leader CANONICAL_PENDING exact invitation
→ optional Phantom member CANONICAL_PENDING exact invitation
→ core terminal:
   ACCEPTED → canonical observe → all claims COMMITTED
   otherwise → leader ABORTED/FORMING and member SOLO
```

The coordinator never guesses consent from a Player/Party snapshot. It accepts
only an exact terminal identity or exact canonical observation belonging to the
same operation.

## Membership mutation protocol

```text
durable operation PREPARED
→ canonical leave/expel/transfer mutation
→ canonical Party observation
→ durable claims/goals update
```

If canonical mutation completes but durable publication fails, state enters
RECOVERING and restart reconciles from the canonical Party. Unknown claims are
not deleted; conflicting generations become INCONSISTENT.

## Background boundary

A read-only `PhantomPartyParticipationPort` exposes only:

```text
boolean blocksBackground(long profileId)
```

A bridge solves startup ordering. Background rechecks at directive and mutation
boundaries. No party object or party reward is stored in background state.

## Matching algorithm

With at most nine members, use dynamic programming over a 9-bit member mask.
Each requirement considers every eligible member/capability edge plus unfilled.
Comparison is lexicographic over:

```text
requiredFilled
totalScore
optionalFilled
canonicalAssignmentString (ascending tie-break)
```

This is bounded and proves maximum assignment rather than greedy priority.

## Target-specific tactics

Role snapshots may describe learned/intrinsic ability without a target.
Execution requires a fresh exact-target capability evaluation.

```text
need detected
→ capabilities(actor, targetObjectId)
→ choose exact ready variant compatible with target scope
→ typed support action spec
→ combat external-action lease
→ canonical current-state revalidation
→ cast issue/await/cancel
```

## Pulse indexes

Maintain indexes at every claim transition; never reconstruct them on a pulse:

```text
claimsByGroup
dueGroups
terminalEvents
inboundInvites
tacticalCompletions
```

A pulse takes at most the configured budget from these queues. A group that
still has work is requeued after other due groups for fairness.
