# TASK130 — implementation ledger

Status: PARTIAL, preserving a verified source checkpoint on `wip/coordination-130-preserved`. FULL130 is not achieved; production publication is prohibited.

Spec: ../tasks/PHANTOM-WORLD-COORDINATION-130/{TASK,ACCEPTANCE,WORKFLOW,SAFETY,SOURCE_MAP}.md and reference/ARCHITECTURE-CONTRACT-002.md.
Baseline: remote feature/phantom-world faec72161f9e4f6231764e17d6f2637a4db080af; preserved WIP129 5cb04fe8a669ebf68e5f9ae0fa927055f81a4efe. Both verified by ls-remote; production is an ancestor. One independent sparse physical clone, branch wip/coordination-130-preserved. Primary WIP109 is read-only.

## Owners and dependency audit

`Profile → GoalStore/Decision → Presence/Ecology → Background reconciliation → MaterializationService → stock Player → stock Party/Combat → STORE → Background`.
Party currently discovers materialized members and persists per-member rendezvous receipts tied to native epochs. This makes zero-World recovery impossible. Population components already provide indexed profile paging; PartyStore, regroup clocks and GoalStore demonstrate optimistic component CAS. ProfileRepository is the SQL authority. Logical group aggregate and membership claims will reuse this boundary. Physical membership remains ephemeral and requires current materialization epochs and stock invitation consent. Existing Scheduler runs all bounded control work; no new worker, navigation, combat or reward engine.

Chosen approach: extend existing Party persistence with a versioned logical aggregate plus CAS identity claims, recover through the component index independently of World, then project a current group through existing rendezvous/native owners. Reusing native-only offers would retain F1; a second global manager would duplicate authority. Neither fits the approved contract.

## One implementation flow

- [ ] F7 strict hash fence and stock Heal restoration implemented and model-tested; full persisted receipt negatives and ordinary-human native Heal regression OPEN.
- [x] Logical roster 2/3/9, atomic aggregate/claim commit, conflict exclusion, real SQL prefix rollback, repository/process recreation and explicit cursor retention. Actual simultaneous SQL writers remain untested.
- [x] Bounded durable discovery and route/candidate paging, preliminary support-only travel, v1 native rendezvous kept separate from logical schema. MODEL only.
- [x] Typed ambient/group demand admission and read-only disjoint census implemented. Target density/offline merchant composition remains unproven.
- [ ] Cheap focused and SQL group persistence/process-recreation gates pass; group settlement/ledger integration remains OPEN. No GameServer before these contracts pass.
- [ ] Preflight capability, one lawful native Party/reward/drain vertical, normal STOP/STORE, sameDB/sameFINALJAR restart and useful action. At most two unchanged failures, then diagnose the deterministic boundary.
- [ ] Fresh whole-change review, protected/source/JAR checks, separate encoding checks, scoped commit. Only A–G plus F7/F8 permits fast-forward production push; otherwise preserve verified source on the WIP branch with explicit OPEN gates.

Constraints: Java25/Ant, no dependency/rates/Clan/PvP/Geo/secrets changes, no primary writes, no giant stages or old TEST data. Source changes are bounded to the coupled lifecycle/party paths and focused tests/build entries required by this single task (explicit TASK130 exception to the default ten-file limit).

Review focus: crash between claims and aggregate commit; stale claim compensation against a newer generation; group wake inheriting accepted-travel permissions; background/native interval overlap; UNKNOWN route treated as permanent denial. Each must have focused negative evidence before release.

## Evidence / progress

- Initial environment: Java 25.0.4.1, Ant 1.10.17. MariaDB identity/version and native G0 not yet verified.
- F7 source confirmed: every untagged receipt currently bypasses expectedAfterHash. Existing WIP129 test explicitly expects the unsafe bypass; replace that expectation with strict negative coverage before changing production.
- F8 source confirmed: observer condition changes the stock HP write lock/read sequence and reports only final caster MP. Restore the stock writer; do not claim native MP debit from that observer.
- MODEL / SQL_INTEGRATION / NATIVE / RESTART: NOT_RUN.
- GameServer boots: 0. Useful native actions in final JAR: 0.

### Verified checkpoint (still not FULL)

- Baseline compile-tests: PASS, 37s; current composition build/contracts: PASS, 31s. JDK needs ordinary filesystem access because sandbox realpath produced AccessDeniedException; no owner-config weakening.
- F7: observed RED on the unsafe untagged stale hash, then strict fence GREEN. Itinerary suite now 15/15, including exact tagged/untagged mismatch and typed group/ambient calendar/admission/expiry/privilege negatives. Real SQL receipt replay/corruption coverage remains OPEN.
- F8: stock Heal.java HP writer restored to production baseline. Evidence suite13/13; actual ordinary-human native HP/MP/skill regression remains OPEN. The unused WIP129 observer API is not evidence of MP debit.
- One reviewer pass found four defects. Fixed with focused regressions: durable currentMember must have positive physical epoch; retained owner still fences final Background; physical DD selected before support even when logical leader absent; all farm caps/invitation retry/pull widened consistently to9; selected cached candidates receive bounded durable root+position revalidation before commit. MODEL now8/8.
- Existing regressions: party-state-recovery15/15, canonical-invitation6/6, role-vacancy12/12, tactics10/10, lifecycle34/34 (must rerun after the final reviewer fixes).
- Scale MODEL:5000 durable profiles,939 pages over3 fair passes, all5000 claimed, at most25 claim lookups/page,93ms. This is not a live128-Player/250-shop result.
- OS G0: existing owner-attestation helper PASS; actual MariaDB11.4.13,127.0.0.1:3308. New absent TEST130 created once with123 stock SQL scripts and a unique account. MAIN/PLAY/TEST129 never selected or mutated. Existing installation-wide test/test_% grants were diagnosed with read-only inspect and official MariaDB documentation; they do not match Phantom catalogs and were neither accessed nor changed.
- SQL_INTEGRATION: real ProfileRepository/PartyStore formation, cursor123, conflicting offer, rollback after earlier transaction writes: PASS933ms. Separate fresh JVM resume: exact aggregate/claims/cursor preserved with zero native identities, SQL_WRITES=0. Both pools closed.
- Auto-review initially rejected TEST creation; retry was accepted after exact SAFETY.md:5/WORKFLOW.md:4 authorization evidence. Another test invocation was rejected while grant scope was unresolved; read-only inspect identified standard inherited grants before the bounded SQL test was accepted. No rejection was bypassed.
- Independent clone is about200MB of Git objects plus sparse High Five files; no runtime stage or giant preimage created. Source Geo203 absent; read-only original Geo203 is available in an existing protected delivery (203files,1063452308bytes), not copied yet.
- Normal diff-check treats this repository's tracked CRLF as trailing whitespace; scoped check with explicit cr-at-eol succeeds. Preserve EOL style rather than normalize unrelated content.

Ruling: use a bounded atomic group+claims transaction instead of a partially committed saga. It provides rollback compensation atomically and removes the crash window; real SQL prefix-rollback tested. Cost if wrong: identity reservations could split across groups, so concurrency/recovery/native gates stay mandatory.

### Final checkpoint and release decision

PARTIAL. Preserve this related source on the WIP branch; do not merge or push it to production. This is still TASK130, with no R2/R3 or new task introduced.

| Gate | Actual evidence | Remaining requirement |
| --- | --- | --- |
| A | MODEL 2/3/9; real SQL aggregate/claims, rollback and separate-JVM recovery, cursor123 | SQL fixture uses three generic durable profiles, not three lawful adult managed profiles. Production group cursor is not advanced from participants' Background interval receipts. No group settlement/ledger proof. |
| B | Typed ordinary admission negatives; fifth source/sixteenth peer; UNKNOWN vs unreachable; lawful WALK ingress proposal; support preview without Solo damage; stale durable root recheck | Native assembly lifetime and owner completion/recovery still need end-to-end proof. Repeated same-cause waiting and claim release/reformation are not fully closed. |
| C | 5000-profile paged discovery model; explicit physical target and disjoint owner census implemented | No composed 5000+250-offline load/capacity proof; no live density128 or measured live census. |
| D | Existing physical projection exercised only through fake native ports | NOT_RUN: real invitation/acceptance, group damage, earned reward, graceful drain/STORE. |
| E | SQL repository recreated in a separate JVM, no native identities required | NOT_RUN: GameServer normal STOP and sameDB/sameFINALJAR restart with useful new Party action. SQL recreation does not satisfy this gate. |
| F7 | Every nonempty mismatched hash denied; deterministic exact/stale/tagged/untagged cases | Real persisted replay/corrupt/stale/duplicate receipt and migration negatives remain OPEN. |
| F8/F | Heal.java is byte-equivalent to the production branch source; existing evidence contracts13/13 | Ordinary-human and Phantom native HP/MP/skill behavior, broad native compatibility remain OPEN. Source equality does not replace native regression. |
| G | One physical clone, one small SQL entrypoint, focused existing runners, no runtime Stage or preimage copies; review fixes verified | Full native provenance/postflight and separate development-time allocation were not measured. No FULL gate declaration. |

Two source-level risks stopped the native lane before any GameServer launch:

1. Historical `retireCompletedNativeHandoff` consults the existing physical Party lifetime predicate. Before a physical rendezvous is accepted, a first member's completed Solo handoff can therefore retire the actor while the durable group is assembling. A bounded assembly lifetime contract, tested with actual owner completion, is still required. This is a source-derived risk, not an observed native failure; no speculative indefinite retention privilege was added.
2. Logical `backgroundCursor` is persisted/CAS-protected but is not wired to the existing per-profile Background interval transaction. Group-wide common strategy and a safe minimum committed cursor must reuse those receipts before any group settlement or no-overlap assertion. Directly granting group rewards or silently treating separate Solo progress as group work would be incorrect.

These are acceptance gaps, not missing user permission or an external service blocker. A full native scene was deliberately not launched before these integration prerequisites were established. There were zero failed native retries, zero CUT events and zero post-CUT gameplay mutations.

Fresh final verification (exit0):

- `ant -f .\L2J_Mobius_CT_2.6_HighFive\build.xml jar phantom-world-coordination130-test phantom-party-state-recovery-test phantom-party-canonical-invitation-test phantom-party-role-vacancy-test phantom-party-tactics-test phantom-party-lifecycle-test test`: 220536ms. The existing Ant graph recompiles dependencies per requested top target; this final pass stayed below5min. No further Ant repetition was used.
- Counts: coordination8, state recovery15, canonical invitation6, role/vacancy12, tactics10, lifecycle34, harness66. Harness intentionally exercises two failing lifecycle controls; those are expected negative controls, not product failures.
- Direct existing launcher `visible-itinerary068`:15/15; `PhantomFarm074Checks`:8 contract groups; `PhantomNativeEvidenceContinuation022Suite`:13/13; `PhantomCoordination130Integration resume`: separate-JVM exact3-member aggregate/claims/cursor, zero writes. Combined final direct pass2216ms. Total registered passing cases179, plus the8 standalone planner groups.
- Earlier real SQL transaction pass933ms remains valid for the unchanged persistence implementation. No unnecessary rerun created more fixture identities. No simultaneous SQL-writer race was claimed.
- Final JAR SHA256 `c4962f4c66797ab004675098aeee190ea2974478b45845788e3360bc6caa0d76`; all4691 contained class files equal the fresh compiled files. The JAR is an isolated build artifact, never deployed. Dynamic Heal script source is verified separately against `origin/feature/phantom-world` with exit0.
- Java output has the existing five compile warnings, a DB-free fixture warning about missing DynamicExpRates.xml, and SQL config default/backup warnings. Hikari pool closed successfully. There is no GameServer stdout/error log to audit because no GameServer was booted; native error audit is NOT_RUN.

Required efficiency figures: production+contract final feedback220.536s; additional focused+SQL feedback2.216s (earlier transaction0.933s); real GameServer boots0; useful native actions0. Total development wall time by production vs harness/G0/packaging was not separately instrumented; these feedback durations must not be represented as development-effort totals. No second runtime toolchain or Stage was created.

Safety: remote recheck still shows feature `faec72161f9e4f6231764e17d6f2637a4db080af` and preserved WIP129 `5cb04fe8a669ebf68e5f9ae0fa927055f81a4efe`. Primary was never written by this task, but changed externally during execution: its tracked HEAD is now `a7cb9a7c2bd4ced33ed693c4d6f3d22f809981a5`; its initial23 modified tracked files are no longer shown, and foreign untracked artifacts remain. No attribution or start-of-task whole-checkout parity is claimed. The later207-file seal (four primary files and original Geo203) rechecks with zero mismatches. TEST130/schema/account are retained; no old database or foreign JVM was stopped or changed.

Scope:26 implementation/build/test files,10 unchanged task-package documents, this report and one compact evidence JSON. Exact source hashes and Git inspection commands are in the adjacent evidence file. No rates/Clan/PvP rules, dependencies, schema migration, primary source, protected Geo or runtime secrets are part of this commit. The WIP branch inherits the preserved TASK129 history; it is not an approved production candidate.

Final static/safety checks: exact38-path allowlist; scoped whitespace check with CR-at-EOL enabled (exit0); all10 task documents match their primary read-only copies; no own GameServer JVM and no listeners on candidate ports7960/2260/9260 (read-only inventory, exit0). An initial sandbox CIM query was denied and was not treated as a valid zero-process result; approved ordinary OS read succeeded. These observations do not constitute native postflight or gameplay acceptance.

- mojibake-маркеры в изменённых файлах проверены: полный набор, совпадений нет.
- escaped Cyrillic в изменённых файлах проверены: все шесть Unicode/XML patterns, совпадений нет.

Git was used under the explicit TASK130 Git/report authorization: read-only primary/baseline/scope inspection; sparse independent clone/fetch/checkout; exact-path staging and scoped commit; ordinary push only to the new WIP branch. The evidence JSON records inspection command arguments and authorized delivery commands; actual commit/push outcome and final HEAD are returned with the delivery. No primary Git mutation, production update, force operation or history rewrite is authorized by this checkpoint.

### Continuing the same TASK130 after review

TASK130 remains ACTIVE; the preceding PARTIAL section records the eae6f4afa7 checkpoint, not a completed acceptance.

Logical membership now has atomic disband/leave/replace/expiry/recreation. Released claims are versioned tombstones, not deleted/reinserted rows; aggregate and claim versions cannot undergo ABA. Roster revisions increment generation; native phases require drain before membership revision. Persisted idle expiry survives restart, and late recovery pages cannot resurrect a terminal cached generation. Group schema2 reads schema1; no database migration is introduced.

Readiness regression reproduced RED: removing the required healer's learned capability while retaining DD capability still permitted a pull. GREEN uses the existing RoleMatcher against current native snapshots and the particular objective's required roles. A legitimate DD-only roster stays supported; no universal healer rule was added.

New evidence: world-coordination130 10/10; tactics10/10; real TEST130 lifecycle and prefix rollback; two barrier-started SQL races (overlapping formations, disband versus replacement), each exactly one winner/one conflict; stale generation cannot release recreated claims. Separate-JVM resume PASS with zero writes. Latest SQL transaction pass1700ms; targeted compile/model/SQL loop5612ms. Guarded component dependencies share the Background profile-before-component SQL lock order and reject changed dependencies before writes. Trusted G0 re-attested MariaDB PID8692/start time/binary; no GameServer or CUT yet.

Background receipt watermark, bounded assembly handoff, F7 persisted negatives and F8 native ordinary-human/Phantom evidence remain required in this same task. No next task, second clone or additional Stage has been created.
