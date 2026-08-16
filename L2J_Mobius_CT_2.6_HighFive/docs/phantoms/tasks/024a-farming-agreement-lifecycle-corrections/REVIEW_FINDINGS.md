# Independent review findings for Goal 024

Reviewed remote baseline:

```text
commit: 2603776c6996007b147f93e4c7e79f145ceb8a89
parent: e67298697eaecc629a03b215a78ffa947233efd3
subject: feat(phantoms): add farming resource negotiation
branch: feature/phantom-world
```

The commit is an exact direct child of the accepted Goal023 baseline.

## Accepted Goal024 parts to preserve

Independent review confirms these are real production implementations, not report-only claims:

- resource claim derives from exact Goal021 acquisition Source/facts;
- ROOM identity is exact room node, outdoor identity is node+anchor+npc;
- `PhantomFarmingConflictPort` defaults to ALLOW;
- Goal021 directive and direct safe-boundary paths recheck farming gate;
- Goal010 `perceptibleProfiles(...)` is bounded/current-generation/node-indexed;
- no fourth topology signal-ledger source;
- exact Party evidence can resolve SHARE;
- bilateral persisted receipts are required before normal agreement gate effect;
- social and Goal020 typed query seams are wired;
- ESCALATE has no direct PvP/combat call path;
- profile-component persistence and policy bounds exist;
- tests cover many initial formation, restart-fault, safety and convergence cases.

These areas are frozen unless an exact 024A correction needs a compatibility change.

## R024A-01 — P1 — progress/remaining wrongly invalidates an active final agreement

Production path:

```text
evaluate(...)
  -> currentAgreement(...)
     -> exact(current ConflictSnapshot, AgreementReceipt, profileId)
```

Current `exact(...)` requires goal id, goal revision, source id and current `remainingAmount == receipt remainingAmount`.

This is wrong after finalization. A holder can farm one successful unit, Goal021 progress changes, source/resource remain identical, but WAIT/SHARE/MOVE disappears and peer becomes NEGOTIATE again.

The existing test checks captured final remaining amounts only immediately after finalization. It does not mutate Goal021 progress after finalization.

Also inspect pre-final behavior: current `refresh(...)` preserves an `ActiveNegotiation` whenever goal/source identity stays the same, even if remaining/progress/acquisition evidence changed. `resumeDraft(...)` can then reuse old arbitration evidence/holder. Before final, this drift must invalidate or recompute the draft.

### Required correction

Split semantics:

```text
sameProposalEvidence(...)
sameFinalBinding(...)
```

Proposal evidence may include remaining/progress/acquisition row/evidence/social inputs.

Final binding must **not** require remaining/progress equality. It must require exact stable agreement identity: pair, goal IDs/revisions, source IDs, ResourceKey, current source still maps to expected resource, current policy/topology authority compatible, bilateral exactPair receipt, TTL/live status.

Normal monotonic Goal021 progress must preserve SHARE/WAIT/MOVE. Material evidence drift before final must restart/recompute proposal.

## R024A-02 — P1 — causal perceptibility and loser-first restart are not protected

`counterpart(...)` currently only considers current runtime resource bucket AND current `topology.perceptibleProfiles(...)`.

`evaluate(...)` returns ALLOW when that returns no counterpart. That is valid only for a brand-new uncontested claim, not after persisted exact negotiation/agreement history exists.

### Failure A: perceptibility changes after negotiation begins

A pair can move out of current one-hop LOCAL_CHAT visibility after OFFER or after bilateral FINAL. Persisted state lacks pair-specific causal perception receipt sufficient to continue bounded agreement history, so pair can disappear merely because current perception changed.

### Failure B: restart loser-first

After restart runtime claim maps are empty. If loser refreshes first and holder has not run yet:

```text
loser runtime claim exists
holder runtime claim absent
counterpart() == 0
gate can become ALLOW
```

despite persisted bilateral WAIT/MOVE/SHARE receipt.

### Required correction

Persist exact causal pair-perception evidence in active negotiation/final agreement, or equivalent bounded receipt: topology generation/hash, both profile IDs, both topology node IDs, both profile topology sequences if available, observed/expiry minute, evidence hash.

Rules:

- fresh current perceptibility starts a new negotiation;
- exact persisted causal evidence can continue an existing pair within bounded TTL even if current one-hop disappears;
- exact current goal/source/resource/topology authority is still revalidated;
- restart may exact-load only counterpart named by persisted state and rehydrate/revalidate both claims; no scan;
- if exact counterpart evidence cannot be revalidated, return conservative NEGOTIATE/WAIT/STALE, never ALLOW solely due missing runtime peer;
- topology generation/hash drift invalidates or safely replans old causal evidence.

If state encoding changes, bump farming component schema. Existing v1 state must migrate or be treated as legacy-untrusted such that it never authorizes stale agreement.

## R024A-03 — P1 — fulfillment/break history is not production-observed

Current production defines:

```text
observeAgreementOutcome(profileId, agreementId, boolean violated)
```

but Goal024 commit has no production call site. The test invokes it directly. The boolean API also allows caller to claim fulfillment without proving actual Goal021 transition.

### Required correction

Make lifecycle reconciliation production-owned and evidence-based.

At bounded farming calls/gates, reconcile latest exact bilateral agreement against current Goal021/topology facts.

### MOVE

While loser remains on exact old source/resource: loser gate MOVE, holder ALLOW.

After existing Goal021 SWITCH really changes/completes losing source: old agreement -> FULFILLED (or STALE for authority drift), old runtime claim released, new selected source gets fresh claim, generic Goal018 `agreement.fulfilled` emitted bilaterally once. Goal024 must not mutate Source.

### WAIT

While holder owns same exact source/resource and TTL live: loser WAIT, holder ALLOW. Normal holder progress does not end WAIT. Holder completion/release/source move -> FULFILLED. TTL -> EXPIRED, not BROKEN.

### SHARE

Normal progress does not invalidate SHARE. Normal completion/release can resolve FULFILLED.

### BROKEN

Only record BROKEN from exact objective evidence that a final agreement was actually violated. Do not expose public arbitrary boolean as authoritative proof. If exact breach evidence does not exist, use STALE/EXPIRED rather than fabricate breach.

### Social delivery

Resolution social events are idempotent. If Goal018 recording is transiently unavailable, retain durable retry evidence rather than permanently losing history after agreement becomes terminal.

## Verdict

```text
Goal 024: CHANGES_REQUIRED
Goal 024 accepted parts: preserved
Goal 025+: NOT_STARTED
Corrective required: Goal 024A
```
