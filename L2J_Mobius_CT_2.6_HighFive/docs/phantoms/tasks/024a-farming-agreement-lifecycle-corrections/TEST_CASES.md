# Goal 024A mandatory dynamic tests

Seed: `24002402`.

All core cases are dynamic; source-token tests are supplementary only.

## 1. SHARE survives ordinary progress

Create exact bilateral final SHARE, then mutate actual Goal021 state with same goal/revision/source/ResourceKey but increasing progress/decreasing remaining. Both gates remain SHARE, same agreement ID, no new negotiation/social duplicate. Repeat more than once. Goal020 reports current reduced remaining.

## 2. WAIT survives holder progress

Create final WAIT. Increase holder progress multiple times without source/resource change. Holder remains ALLOW, loser WAIT, same agreement ID, no renegotiation. Then complete/release/move holder old resource: bilateral FULFILLED, waiter no stale WAIT, fulfilled social once/owner, no BROKEN.

## 3. MOVE executes real Goal021 switch and fulfills old agreement

Use production Goal021 fixture with at least two ranked sources. Drive bilateral MOVE. Execute real Goal021 Decision/SWITCH path, not fake source rewrite. Assert source changes exactly once under Goal021 ownership, old claim released, old MOVE -> bilateral FULFILLED, fulfilled social once/owner, new source gets fresh claim, replay no second switch.

## 4. Pre-final progress drift recomputes

Start OFFER with evidence A. Mutate Goal021 progress after OFFER and separately after RESPONSE. Old proposal must not finalize. Arbitration/evidence/holder are recomputed; new proposal uses current remaining/progress and deterministic new canonical identity/evidence.

## 5. Perceptibility disappears after OFFER

Start while current LOCAL_CHAT perceptibility true; persist causal receipt. Then change profile topology/door state so current one-hop false but same topology generation/hash + same source/resource + causal TTL live. Exact RESPONSE/FINAL may finish for same pair. Different pair/resource cannot use old receipt.

## 6. Perceptibility disappears after final

After final WAIT/SHARE/MOVE, remove current one-hop perceptibility while source/resource authority remains current. Live final gate remains valid until normal terminal condition/TTL. Causal TTL does not refresh on replay.

## 7. Loser-first restart rehydration

Persist final WAIT and separately MOVE. Restart with empty runtime claims. Advance only loser; do not pulse holder. Real Goal021 gate must not become ALLOW. Exact counterpart is lazily loaded/revalidated by ID; WAIT remains WAIT, MOVE remains MOVE. Then holder run is idempotent.

## 8. Legacy v1 restart

If schema v2: v1 state cannot directly authorize live agreement. Exact current pair may revalidate safely; stale/unknown causal history fails closed; no duplicate agreement/social event.

## 9. Topology authority drift

After OFFER and after FINAL, change topology generation/hash. No stale effect; proposal/final -> STALE/replan. No agreement.broken unless exact breach independently proven.

## 10. TTL -> EXPIRED

For WAIT and SHARE, pass TTL and reconcile. Persist bilateral EXPIRED; no generic broken event; new negotiation requires fresh current perceptibility.

## 11. Social transient failure retry

Inject Goal018 failure after bilateral final and after FULFILLED. Durable pending-social truth remains; later reconciliation retries same deterministic event ID; exactly one Goal018 effect after recovery; no duplicate agreement.

## 12. BROKEN evidence

If current Goal024 facts contain a truly objective breach predicate, prove it dynamically. If not, do not fabricate BROKEN; ambiguous drift uses STALE/EXPIRED. Caller boolean is not authority.

## 13. Same-Party SHARE progress

Same exact Goal017 Party SHARE survives Goal021 progress without rounds/social dispute.

## 14. Three-claimant regression

Stable pair order; no deadlock/cycle; at most one exclusive ALLOW; no profile in two active negotiations; bounded bucket.

## 15. Acquisition safe-boundary regressions

Preserve: uninstalled ALLOW; NEGOTIATE/WAIT block new TRAVEL/TARGET; dispatched action not aborted; SHARE/ALLOW proceed; MOVE delegates SWITCH. Add real source-switch proof from test 3.

## 16. Goal020 facts

After progress on live agreement, real L2j conversation query reports same agreement/act and current own/counterpart remaining. EXPIRED/STALE/one-sided -> suppressed.

## 17. Restart fault matrix for terminal resolution

Inject after first FULFILLED/EXPIRED/STALE bilateral write, before second mirror, before Goal018 terminal event. Restart converges exact terminal receipt + idempotent social.

## 18. Existing Goal024 regressions

Run all original Goal024 modes/acquisition gate unchanged, plus relevant Goal010/017/018/020/021/023C regressions.

## 19. Safety

No PvP/combat call from farming package, no World.getPlayers, no TopologyService.listProfiles in conflict path, no fourth Goal010 signal source, no worker/future/timer, no direct acquisition source rewrite.

## 20. Performance

Record bounded exact peer loads, perceptibility queries, bucket lookups, reconciliation ops and social retries. No latency hard gate.
