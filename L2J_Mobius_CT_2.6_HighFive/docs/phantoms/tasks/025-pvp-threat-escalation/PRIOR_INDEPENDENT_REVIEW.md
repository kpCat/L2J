# Independent review entering Goal 025

## Reviewed corrective baseline

```text
Repository: kpCat/L2J
Module: L2J_Mobius_CT_2.6_HighFive
Branch: feature/phantom-world
Original Goal024 commit: 2603776c6996007b147f93e4c7e79f145ceb8a89
Goal024A commit: 922f72c0d422904dcbdc6215a5cc1167a1bb84fb
Goal024A parent: 2603776c6996007b147f93e4c7e79f145ceb8a89
Goal024A subject: fix(phantoms): harden farming agreement lifecycle
Remote HEAD reviewed: 922f72c0d422904dcbdc6215a5cc1167a1bb84fb
```

## Independent verdict

```text
R024A-01: CLOSED
R024A-02: CLOSED
R024A-03: CLOSED
Goal024A: ACCEPT
Goal024 overall: ACCEPT
Accepted baseline: 922f72c0d422904dcbdc6215a5cc1167a1bb84fb
Goal025: AUTHORIZED
```

### R024A-01 — post-FINAL progress survival

The committed service separates pre-FINAL mutable arbitration evidence from the
stable bilateral FINAL identity. Pre-FINAL changes to acquisition facts still
invalidate/recompute negotiation evidence. After exact bilateral FINAL,
normal monotonic Goal021 progress/remaining is no longer part of the live
agreement identity, so SHARE/WAIT/MOVE is not invalidated merely because work
continued.

### R024A-02 — causal perceptibility and restart

Schema v2 persists an exact bounded causal perception receipt. A new pair still
requires fresh bounded Goal010 perceptibility. An already causal pair can
continue inside the receipt TTL when current one-hop visibility disappears.
Restart rehydrates the exact persisted counterpart by ID; the loser-first path
cannot silently become ALLOW because the counterpart has not pulsed yet.
Current topology generation/canonical authority drift fails closed. No global
profile/Player scan is introduced.

### R024A-03 — authoritative lifecycle reconciliation

Farming consumes Goal021 lifecycle observations rather than a manual production
boolean. MOVE remains Goal021-owned: Goal024 does not call switchSource; the
agreement observes the actual source transition. WAIT/SHARE terminal truth is
reconciled from completion/release/move evidence. TTL becomes EXPIRED,
authority drift STALE, and BROKEN requires objective exact violation evidence;
ambiguous evidence is not fabricated as BROKEN. Goal018 delivery is durable,
idempotent and retryable.

### Test-only changes

The manor sow/Harvester and active-spoil-to-combat changes in
`PhantomCombatServerIntegrationSuite` are accepted as deterministic test-only
fixture sequencing. They do not change production combat/acquisition semantics.
They must not be reverted merely because they originated while diagnosing
historical timing-sensitive suites.

## Historical truth

Historical `CHANGES_REQUIRED` for the original Goal024 baseline remains factual
for that exact commit. Goal025 documentation may record the new accepted
baseline, but must not rewrite or erase the old independent-review decision.
