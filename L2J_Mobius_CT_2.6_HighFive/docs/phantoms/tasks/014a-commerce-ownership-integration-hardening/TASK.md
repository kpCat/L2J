# Goal 014A — Commerce ownership and canonical integration hardening
## Contract
```text
branch: feature/phantom-world
parent: 696689987276137f6a7f3661329171c9ee65e6f9
test DB: l2jmobiush5_phantom_test
seed: 14001401
subject: fix(phantoms): harden commerce ownership and integration
token: GOAL_014A_COMMERCE_OWNERSHIP_INTEGRATION_HARDENED_PENDING_INDEPENDENT_REVIEW
```
One ordinary child and push to `origin/feature/phantom-world`. No amend/rebase/squash/merge/
force push. Follow `docs/phantoms/PHANTOM_CODEX_EFFICIENCY_STANDARD.md`.
Progress truth:
```text
Goal 013/013A: ACCEPT after Goal 013B
Goal 013B: ACCEPT_WITH_ACTIVATION_GATE
Goal 014: FIX_REQUIRED
Goal 014A: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Goal 015/017/025: NOT_STARTED
```
Retain Goal 014 catalog, CP/current-source facts, safe subset and conservative
non-ACID design. Do not rebuild them.
## Baseline cleanliness
Before Codex starts these obsolete untracked module-root files must be absent:
```text
CODEX_EXECUTION_BUDGET_BLOCK.md
MANIFEST.json
PHANTOM_CODEX_EFFICIENCY_STANDARD.md
```
The tracked standard is `docs/phantoms/PHANTOM_CODEX_EFFICIENCY_STANDARD.md`.
Remove their special whitelist from verifier 014.

## Findings to close

1. `finishStop()` reports STOPPED with in-flight commerce; snapshot hardcodes
   zero, so materialization may shut down across an ActionLease/persistence call.
2. A terminal receipt can be replaced by a different request at the same/older
   revision or by a stale goal.
3. Backend exact buy/teleport lookup scans only page 0/256.
4. `commerce-server-integration` never constructs `L2jCommerceBackend` and tests
   no real Player buy/sell/teleport.
5. Committed tree has no green cumulative `ant verify`.

## Exact READ_SET

1. this task + efficiency standard;
2. `PhantomCommerceService.java`;
3. `PhantomCommerceReceipt.java`;
4. `PhantomCommerceCatalog.java`;
5. `L2jCommerceBackend.java`;
6. `PhantomCommerceReceiptStore.java`;
7. `PhantomCommerceDecision.java`, handler only;
8. `PhantomSystem.java`, commerce/goal-store/shutdown ranges;
9. `PhantomGoalStore.java`, `PhantomGoalStateStore.java`;
10. `PhantomCommerceSuite.java`, receipt/lifecycle/integration ranges;
11. commerce targets in `build.xml`;
12. `verify-task-014.ps1`.

No old Goal package/report, full roadmap/master plan or other chronicle.
Maximum three extra exact files only for NPC construction/materialization helper;
report each expansion.

## A. Lifecycle ownership

Track real current/peak:

```text
operations
actor leases
persistence claims
```

Register after RUNNING check under the service monitor; release in `finally`.
Never hold that monitor across backend/repository/loader/actor calls.

`beginStop` rejects new work. Accepted work may finish. `finishStop` remains
STOPPING and returns false until all three current counters are zero; only then
STOPPED. Snapshot exposes real values.

Deterministic blocking test:

```text
operation blocked at controlled backend/persistence seam
beginStop
finishStop == false
release
all counters == 0
finishStop == true
```

Prove `PhantomSystem.shutdown()` never reaches materialization while commerce
owns a claim.

## B. Goal authority and receipt rollover

Use the same `PhantomGoalStateStore` instance for decision engine and commerce
goal authority. Before PREPARED or terminal replacement prove exact persisted
ACTIVE `profileId/goalId/revision`; recheck cancellation immediately before save.

Rules:

```text
same exact key/request       → reconcile/idempotent
same goal+revision, changed  → GOAL_REVISION_CONFLICT; no overwrite
same goal, lower revision    → STALE_GOAL_REVISION
same goal, higher current    → replace only COMMITTED/ABORTED
different current goal       → replace only COMMITTED/ABORTED
stale different goal         → reject
nonterminal                  → never overwrite
INCONSISTENT                 → permanent profile fail-stop
exact ABORTED retry          → typed aborted/cancelled, not INCONSISTENT
```

No schema and no cross-table ACID claim.

## C. Exact catalog identity

Add immutable exact queries:

```text
findBuyOffer(listId, itemId)
findTeleportRoute(npcId, listName, ordinal)
```

Duplicate exact identity fails catalog construction. Backend must not page/filter
for exact identity. Test target identities after more than 256 decoys.

## D. Real canonical integration

Add one `commerce-hardening` mode that constructs the real:

```text
PhantomProfileRepository + PhantomGoalStateStore
PhantomMaterializationService + materialized Player
PhantomCommerceCatalogLoader + L2jCommerceBackend
PhantomCommerceReceiptStore + PhantomCommerceService
real current Merchant and Teleporter NPCs
```

Preferred current fixtures:

```text
buy: list 382, item 1463, NPC 31380
teleport: NPC 30006, NORMAL ordinal 0
```

If data changed, use existing deterministic selectors and report IDs.

Through production service/backend:

1. persist current ACTIVE buy goal/source; fund Player; exact unlimited buy;
2. prove runtime and durable item/adena; reconstruct service; same-key idempotent;
3. persist current sell goal; sell exact owned object/count; prove DB/runtime;
4. persist current teleport goal; fund fee; execute NORMAL route; await target;
5. dematerialize/rematerialize; prove item/adena/position;
6. cleanup NPC/profile and prove zero claims.

No fake backend may satisfy this case.

Keep synthetic receipt tests and add:

```text
same-revision changed request
lower revision
stale different goal
current new-goal rollover
ABORTED exact retry
nonterminal mismatch
shutdown while blocked
```

## Scope

Production only:

```text
phantoms/commerce/**
phantoms/PhantomSystem.java
```

Tests/build/tools only:

```text
build.xml
PhantomCommerceSuite.java
PhantomTestLauncher.java
verify-task-014.ps1
verify-task-014a.ps1
```

Docs only: roadmap, commerce contract, Goal 014 report, Goal 014 review, Goal
014A report and this package.

Forbidden: server core/loaders/packets, progression/Game Knowledge, config/schema,
other chronicles/geodata, multisell execution, workers/Futures, Goal 015/017/025.

## Tests and completion

During implementation: compile + `commerce-hardening` only.

Final:

```text
commerce-hardening: 1 green
existing phantom-commerce-test: 1 green
ant verify: 1 green; max one repeat after exact failed-target fix
ant jar: 1 green
verify-task-014a.ps1: 2 byte-identical green
```

Verifier must enforce all findings, obsolete files absent, real backend
integration and no page-0 exact lookup. Report <=140 lines with READ_SET,
command and usage telemetry.

Do not self-accept. On blocker remove unsafe new production changes, preserve
evidence, ordinary commit/push and return honest BLOCKED. Print `GOAL_014A_COMMERCE_OWNERSHIP_INTEGRATION_HARDENED_PENDING_INDEPENDENT_REVIEW` only
after every gate.
