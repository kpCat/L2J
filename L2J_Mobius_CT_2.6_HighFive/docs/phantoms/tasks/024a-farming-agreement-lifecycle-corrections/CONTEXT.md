# Goal 024A context

## Baseline

```text
branch: feature/phantom-world
required parent: 2603776c6996007b147f93e4c7e79f145ceb8a89
parent subject: feat(phantoms): add farming resource negotiation

Goal 023 overall: ACCEPT
Goal 024: CHANGES_REQUIRED
Goal 025+: NOT_STARTED
```

Goal 024 implemented the intended architecture substantially:

- exact Goal021 Source-derived ROOM/MOB_GROUP resource identity;
- narrow default-ALLOW `PhantomFarmingConflictPort`;
- Goal021 gates before new TRAVEL_REQUIRED / TARGET_REQUIRED work;
- bounded Goal010-owned `perceptibleProfiles(...)`;
- no fourth Goal010 scheduler-signal source;
- same exact Party SHARE;
- typed SHARE / WAIT / MOVE / REFUSE / ESCALATE acts;
- profile-component persistence;
- Goal018 social event integration;
- Goal020 typed farming query;
- no direct PvP/combat execution from Goal024;
- worker-free, bounded runtime claim index.

Do not redesign or discard those accepted parts.

## Why 024A is required

Independent review found three production lifecycle defects after the Goal024 commit. They are related and must be corrected together because all three concern the truth and durability of an already-created agreement.

### R024A-01 — mutable progress is incorrectly part of live-agreement identity

Final `AgreementReceipt` stores `lowerRemaining` and `higherRemaining`. That is correct as **proposal/arbitration evidence**.

However `PhantomFarmingService.currentAgreement(...)` calls `exact(...)`, and `exact(...)` requires the current Goal021 `remainingAmount` to equal the remaining amount captured at finalization.

Normal successful acquisition progress changes remaining without changing goal id/revision, selected Source, ResourceKey, topology authority or counterpart. Therefore a valid WAIT/SHARE/MOVE agreement becomes invisible immediately after ordinary progress and the gate falls back to NEGOTIATE.

Correct contract:

```text
before bilateral FINAL:
  remaining/progress/social evidence are mutable arbitration evidence;
  material drift invalidates/recomputes the draft before finalization.

after bilateral FINAL:
  remaining/progress snapshots remain historical evidence only;
  ordinary monotonic progress does not invalidate the agreement;
  live binding is goal/revision/source/resource/authority/TTL + exact bilateral receipt.
```

### R024A-02 — persisted causal perceptibility is not used for continuation/restart

Conflict discovery correctly requires current Goal010 bounded perceptibility. But after an OFFER/final receipt exists, `counterpart(...)` still requires the peer to be present in the current runtime claim bucket and in current one-hop perceptibility.

Consequences:

1. moving one topology edge after negotiation began can erase the pair despite the required bounded perceptible-history causality;
2. after restart runtime buckets are empty. If only the losing side runs first, it refreshes only its own claim, sees no runtime counterpart, and can reach ALLOW before the holder receives a scheduler pulse despite a persisted bilateral WAIT/MOVE/SHARE agreement.

Correct contract:

- fresh current perceptibility is required to start a new negotiation;
- once exact OFFER/RESPONSE/final history exists, a bounded persisted causal perception receipt may continue that exact pair for its TTL;
- on restart exact counterpart ID from persisted state may be lazily reloaded and revalidated directly; no profile/global scan;
- losing side must never receive ALLOW merely because peer has not rehydrated runtime claim;
- long-stale authority/goal/source/resource/topology or expired causal receipt fails closed.

### R024A-03 — agreement fulfillment/break is a manual test API, not production reconciliation

`observeAgreementOutcome(profileId, agreementId, boolean violated)` exists, but there is no production call path using it. The current focused test calls that method manually.

Therefore actual Goal021 MOVE -> `switchSource(...)` does not automatically resolve the old agreement as fulfilled/stale; WAIT holder completion/release does not automatically resolve/wake the waiter through durable agreement lifecycle; generic Goal018 `agreement.fulfilled` / `agreement.broken` history is not driven by production observations; and passing an arbitrary boolean is not proof that an agreement was fulfilled or broken.

Correct contract:

- farming service reconciles lifecycle from current authoritative Goal021/topology state itself;
- MOVE fulfillment is observed from the losing profile's real Goal021 source change/completion after Goal021-owned switch;
- WAIT fulfillment is observed when holder completes/releases/moves old resource; expiry is EXPIRED, not BROKEN;
- SHARE remains active through normal progress and is fulfilled when bounded lifecycle ends normally;
- BROKEN requires explicit exact evidence of a violated final agreement; insufficient evidence -> STALE/EXPIRED, never invented breach;
- bilateral resolution + Goal018 events remain idempotent/restart-safe.

## File-scope workflow rule

There is deliberately **no artificial numeric file-count limit**.

The task provides a pre-audited exact read set and expected change set. Codex may read/change additional High Five files only when exact call-path necessity is demonstrated. Every additional changed file must be named and justified in the report.

Do not broaden into unrelated refactors.
