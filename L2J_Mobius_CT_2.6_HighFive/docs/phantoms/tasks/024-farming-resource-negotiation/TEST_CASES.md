# Goal 024 test cases

Seed: `24002401`.

Core behavior must be dynamic; source-marker assertions are supplementary only.

## 1. Resource identity

- same outdoor `topologyNodeId + anchorId + npcId` conflicts even with different item/method;
- different outdoor anchor does not conflict;
- exact ROOM conflicts even for different NPC/item identities;
- recipe/planning-only source creates no claim;
- stale/missing topology node/anchor -> STALE, not XYZ fallback.

## 2. Goal021 snapshot truth

Create exact current acquisition states and prove remaining=`requiredAmount-progress`, source identity preserved, alternatives come from ranked current candidates, and goal/state mismatch returns no snapshot. No independent Goal024 remaining counter.

## 3. Bounded perceptibility

Using current Goal010 registry: same/perceptible node conflicts; one-hop LOCAL_CHAT-perceptible neighbor conflicts; non-perceptible neighbor does not negotiate; stale generation replans; result stable/bounded; hot path does not use listProfiles/global player scan; no new relevance source.

## 4. Claim lifecycle

First exact claimant ALLOW; second exact perceptible claimant NEGOTIATE; refresh idempotent; goal/source revision replaces old claim; lease expiry clears stale claim; completion/source switch releases old resource; shutdown clears runtime buckets; restart revalidates persisted receipt before live index.

## 5. Same Party SHARE

Two exact members of the same current canonical Party/group on same resource -> SHARE without negotiation rounds, Party mutation or invitations. Stale Party evidence must not auto-share.

## 6. Friendly bilateral SHARE

Two non-Party Phantoms with cooperative Goal018 evidence. Drive both sides through protocol: one stable agreement ID; both exact final SHARE receipts required before gates SHARE; replay idempotent; social events once; failure after first final write never authorizes SHARE.

## 7. MOVE with alternative

Loser has exact current alternative. Both sides derive same holder; final MOVE only after bilateral final receipt; Goal021 existing switchSource path invoked exactly once; Goal024 never rewrites selected Source; old claim released after switch; new source claims afresh.

## 8. WAIT without alternative

Loser has no usable alternative. WAIT has bounded expiry; no new travel/target on contested resource; holder continues; holder release/completion resumes re-evaluation; wait expiry replans; no tight retry loop.

## 9. REFUSE convergence

Policy evidence refuses cooperation below escalation threshold. REFUSE durable/idempotent; both sides still derive one holder; loser MOVE/WAIT prevents double exclusive farming; round/cooldown prevents ping-pong.

## 10. ESCALATE without PvP

High current `conflict.escalation`: persist ESCALATE and social memory; no CombatService request, attack, force attack, Player target mutation, PvP/PK action. Holder remains unique and loser only MOVE/WAIT.

## 11. Fulfilled/broken history

Honored SHARE/WAIT/MOVE -> existing `agreement.fulfilled` exactly once. Exact violation of final agreement -> `agreement.broken`; stale/expired receipt is not falsely broken; reconciliation idempotent.

## 12. Bilateral crash/restart matrix

Inject failures between proposer OFFER, responder RESPONSE, first FINAL, mirror FINAL and social event write. After restart: one-sided receipt grants no effect; exact pair converges or expires; no duplicate agreement ID/social event/permanent resource lock. Use test DB only through existing guard where real components are required.

## 13. Acquisition execution gate

Production-seam test: NEGOTIATE blocks TARGET_REQUIRED target acquisition; WAIT blocks new target/travel; MOVE maps to Goal021 SWITCH; SHARE/ALLOW permits prior path; already-dispatched canonical action is not aborted mid-action; next safe boundary rechecks gate.

## 14. Goal020 facts/query

Through real `L2jPhantomConversationExecutionPort`, query current resource/claim, counterpart, both remaining amounts, latest act, agreement status and reason/evidence; stale receipt suppressed. Farming service emits no Russian text.

## 15. Ordinary human safety

Include ordinary/headless real Player in perception/chat context: no PhantomGoal, no claim receipt, no auto agreement; no global scan.

## 16. Three-claimant convergence

Three Phantom claims on one resource: one pair per profile at a time, stable counterpart order, next conflict after first resolution, no deadlock, at most one exclusive holder ALLOW, all bucket/round bounds respected.

## 17. Performance

Deterministic bounded volume for claim refresh/lookup, resource bucket, perception lookup, scoring and receipt reconciliation. Record operation counts and wall-clock diagnostic only.

## 18. Affected regressions

Run exact current targets from build.xml for Goal010 topology/perception/signal ledger, Goal017 Party state/route queried seams, Goal018 social core/integration, Goal020 query/execution, Goal021 acquisition planning/active/background/restart, Goal023C aggregate and disabled Phantom regression. Document exact target names.
