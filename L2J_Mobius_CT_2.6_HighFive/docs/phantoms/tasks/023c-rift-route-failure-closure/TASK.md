# Goal 023C — Rift route terminal-failure semantics closure

## Identifier

```text
Task ID: 023c-rift-route-failure-closure
Goal: 023C corrective
Branch: feature/phantom-world
Required parent: 041e23502e5701716bab77dbe73304dc375a157e
Required commit subject: fix(phantoms): close rift route failure semantics
Seed: 23002313
Success token: GOAL_023C_RIFT_ROUTE_FAILURE_SEMANTICS_CLOSED_PENDING_INDEPENDENT_REVIEW
```

Goal 024+ must not start.

## Independent review entering this task

Record before implementation:

```text
R023B-01: CLOSED
R023B-02: CLOSED
Goal 023B: ACCEPT_WITH_REQUIRED_023C_ROUTE_FAILURE_CLOSURE
Goal 023 overall: CHANGES_REQUIRED
Goal 024+: NOT_STARTED
```

Close only `R023C-01`. Read all files in this package; `REVIEW_FINDINGS.md`, `ARCHITECTURE.md`, `TEST_CASES.md` and `ACCEPTANCE.md` are normative.

## Pre-audited read set

```text
PHANTOM_DEVELOPMENT_MASTER_PLAN.md
docs/PHANTOM_BOTS_ROADMAP.md
docs/phantoms/CODEX_WORKFLOW_CONTRACT.md
docs/phantoms/TASK_PACKAGE_STANDARD.md
docs/phantoms/CODEX_REPORT_TEMPLATE.md
docs/phantoms/architecture/RIFT_RECRUITMENT_CONTRACT.md
docs/phantoms/reports/023b-rift-route-consent-closure.md
docs/phantoms/reviews/023b-independent-review.md
Goal 023B task package
this Goal 023C package

java/org/l2jmobius/gameserver/phantoms/navigation/PhantomNavigationService.java
java/org/l2jmobius/gameserver/phantoms/navigation/PhantomNavigationResult.java
java/org/l2jmobius/gameserver/phantoms/navigation/PhantomNavigationRoute.java
java/org/l2jmobius/gameserver/phantoms/navigation/PhantomNavigationPolicy.java
java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyRouteCoordinator.java
java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java
java/org/l2jmobius/gameserver/phantoms/party/model/PhantomPartyModel.java
java/org/l2jmobius/gameserver/phantoms/rift/L2jPhantomRiftPartyPort.java
java/org/l2jmobius/gameserver/phantoms/rift/PhantomRiftService.java

current exact Navigation, Party route, Rift 023/023A/023B tests registered by PhantomTestLauncher
build.xml Goal009/017/023/023A/023B sections
tools/phantoms/verify-task-023b.ps1
```

## No arbitrary file-count budget

There is deliberately no numerical file limit. Expected production changes are primarily `PhantomPartyRouteCoordinator.java`, `PhantomPartyCoordinator.java`, and `L2jPhantomRiftPartyPort.java`; `PhantomRiftService.java` may need a narrow status mapping adjustment. Prefer leaving Navigation production authority unchanged unless a real missing typed field makes the adapter fix impossible. Additional High Five files are allowed only when exact call-path necessity is documented in the report.

## Baseline guard

Before edits require HEAD `041e23502e5701716bab77dbe73304dc375a157e` on `feature/phantom-world`; run status/rev-parse/show/diff-check. Do not rebase/reset/amend/force to manufacture the baseline.

## Required implementation

`Optional.empty()` must no longer mean both still-pending and terminal failure. Preserve exact `PhantomNavigationResult.Status` upward as needed. At request time: ACCEPTED async=PENDING; COMPLETED+usable route=READY; COMPLETED+no route=FAILED; REJECTED=REJECTED/UNAVAILABLE. At poll time: no terminal result=PENDING; terminal+route=READY; terminal+no route=FAILED. Never populate `_routeByGroup`/deadline for terminal no-route. If a bounded terminal receipt is introduced, it needs exact identity, bounded lifecycle and deterministic cleanup.

Goal017 and Rift must preserve this distinction. `RouteActivity.NONE` means no ownership, not pending. Rift exits REQUEST/OBSERVE route on terminal failure to ordinary evaluation/replan without same-pulse resend.

## Tests and verification

Implement all materially distinct cases in `TEST_CASES.md`; dynamic proof must include synchronous rejected, synchronous completed-no-route, async accepted->NO_PATH/BACKEND_FAILURE, and successful immediate/async regressions. Preserve all Goal023B route and managed-consent tests.

Create focused Goal023C target and final aggregate including relevant Navigation, Party route/recovery/lifecycle, Goal023/023A/023B, Goal017/020 affected regressions, historical verifiers 023/023A/023B and working verifier 023C. Make verifier 023B descendant-compatible if its Ant target still uses `-WorkingTree`; do not weaken historical checks. Create `tools/phantoms/verify-task-023c.ps1` pinned to parent/branch/subject/seed and do not encode file-count ceilings.

## Documentation

Update `docs/phantoms/reviews/023b-independent-review.md` to factual review result above. Create `docs/phantoms/reports/023c-rift-route-failure-closure.md` and `docs/phantoms/reviews/023c-independent-review.md` as pending independent review only. On successful implementation update roadmap/master to 023C pending review; Goal024 remains NOT_STARTED.

## Safety

No Goal024+, other chronicles, production DB `l2jmobiush5`, SQL, `.l2j`, new workers, fake GameClient, global player scan, Rift entry/item/teleport/room/spawn/combat, unrelated Party/navigation rewrite, managed-consent redesign or schema-v2 redesign.

## Final discipline

After source/test/build/verifier freeze: one final Goal023C aggregate, one plain `ant verify`, one standalone `ant jar`, ordinary commit `fix(phantoms): close rift route failure semantics`, push, then verifier 023C on PS5.1 and existing verified PS7 with byte-identical stdout. Second full verify only after a real relevant correction and explain it.

Only after every mandatory gate print:

```text
GOAL_023C_RIFT_ROUTE_FAILURE_SEMANTICS_CLOSED_PENDING_INDEPENDENT_REVIEW
```
