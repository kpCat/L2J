# Goal 023B — Rift route ownership and production managed-consent closure

## 1. Identifier

```text
Task ID: 023b-rift-route-consent-closure
Goal: 023B corrective
Branch: feature/phantom-world
Required parent: 563752f6844076fdbaeb3be7c5cae979c757960a
Required commit subject: fix(phantoms): close rift route and consent gaps
Seed: 23002312
Success token: GOAL_023B_RIFT_ROUTE_CONSENT_CLOSURE_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
```

One coherent corrective Goal 023B. Do not pre-create 023B1/023B2.
Goal 024+ must not start.

## 2. Goal

Close the two independently proven remaining Goal 023A blockers:

```text
R023B-01 active Goal017 shared-route ownership is absent from binding stability;
R023B-02 real Rift target-side managed-consent policy is not end-to-end proven
          and does not refresh full current candidate eligibility at response time.
```

Do not reopen accepted Goal 023A areas without a directly failing regression or
a necessary consequence of these findings.

## 3. Read first

In order:

```text
PHANTOM_DEVELOPMENT_MASTER_PLAN.md
docs/PHANTOM_BOTS_ROADMAP.md
docs/phantoms/CODEX_WORKFLOW_CONTRACT.md
docs/phantoms/TASK_PACKAGE_STANDARD.md
docs/phantoms/CODEX_REPORT_TEMPLATE.md

docs/phantoms/tasks/023-rift-advanced-party-recruitment/TASK.md
docs/phantoms/architecture/RIFT_RECRUITMENT_CONTRACT.md
docs/phantoms/reports/023-rift-advanced-party-recruitment.md
docs/phantoms/reviews/023-independent-review.md

docs/phantoms/tasks/023a-rift-production-integration-corrections/TASK.md
docs/phantoms/tasks/023a-rift-production-integration-corrections/ARCHITECTURE.md
docs/phantoms/reports/023a-rift-production-integration-corrections.md
docs/phantoms/reviews/023a-independent-review.md

this Goal 023B package
```

`REVIEW_FINDINGS.md`, `ARCHITECTURE.md`, `ACCEPTANCE.md` and `TEST_CASES.md`
are normative.

## 4. Pre-audited production read set

Read these exact files before changing architecture:

```text
java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java
java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyRouteCoordinator.java
java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyBackend.java
java/org/l2jmobius/gameserver/phantoms/party/L2jPhantomPartyBackend.java
java/org/l2jmobius/gameserver/phantoms/party/model/PhantomPartyModel.java

java/org/l2jmobius/gameserver/phantoms/rift/PhantomRiftService.java
java/org/l2jmobius/gameserver/phantoms/rift/L2jPhantomRiftPartyPort.java
java/org/l2jmobius/gameserver/phantoms/rift/PhantomRiftBackend.java
java/org/l2jmobius/gameserver/phantoms/rift/L2jPhantomRiftBackend.java
java/org/l2jmobius/gameserver/phantoms/rift/PhantomRiftReadinessService.java
java/org/l2jmobius/gameserver/phantoms/rift/PhantomRiftModel.java
java/org/l2jmobius/gameserver/phantoms/rift/PhantomRiftStateCodec.java

java/org/l2jmobius/gameserver/model/groups/PartyInvitationService.java
java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java
```

Read exact current route, party-server-integration, Rift 023 and Rift 023A test
suites registered by `PhantomTestLauncher`, and only corresponding Goal017 /
Goal023 / Goal023A portions of `build.xml`.

### Expansion rule — no artificial file-count limit

There is deliberately no maximum count of files.

If the exact call path proves another High Five file is required, read it. If a
correct fix genuinely requires changing it, change it. In the report, for every
changed file outside the expected change set, state:

```text
file
exact symbol/call path that required it
why expected set was insufficient
why it is still Goal 023B
```

Do not use this for broad exploration, unrelated cleanup, dependency updates or
other chronicles. Do not mark BLOCKED merely because the correct solution needs
another justified High Five file.

## 5. Baseline proof

Before edit:

```text
git status --short --branch
git rev-parse HEAD
git rev-parse --abbrev-ref HEAD
git show -s --format=%H%n%P%n%s HEAD
git diff --check
```

Required:

```text
HEAD = 563752f6844076fdbaeb3be7c5cae979c757960a
branch = feature/phantom-world
```

If baseline differs, do not rebase/reset/force. Produce honest BLOCKED
report/commit/push per workflow.

## 6. Record review status

Before production changes, make repository review history state:

```text
Goal 023 baseline 840e159a989f6372da9c471c915413f1e4470daf:
CHANGES_REQUIRED

Goal 023A baseline 563752f6844076fdbaeb3be7c5cae979c757960a:
CHANGES_REQUIRED

Findings:
R023B-01
R023B-02

Goal 024+:
NOT_STARTED
```

Do not claim Goal 023A ACCEPT. Preserve exact Goal022 ACCEPT and its timing-flake
waiver truth.

## 7. Expected change set

The independent audit predicts production changes primarily in:

```text
java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java
java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyRouteCoordinator.java
java/org/l2jmobius/gameserver/phantoms/rift/PhantomRiftService.java
```

Possible exact seam changes if required:

```text
java/org/l2jmobius/gameserver/phantoms/rift/L2jPhantomRiftPartyPort.java
java/org/l2jmobius/gameserver/phantoms/party/model/PhantomPartyModel.java
java/org/l2jmobius/gameserver/phantoms/rift/PhantomRiftModel.java
```

Expected tests:

```text
test/java/org/l2jmobius/tests/phantoms/PhantomPartyServerIntegrationSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomRiftCorrectionsSuite.java
```

A dedicated focused suite is allowed if cleaner.

Expected test/build/tool wiring:

```text
test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java
build.xml
tools/phantoms/verify-task-023b.ps1
```

Expected docs:

```text
docs/phantoms/reviews/023a-independent-review.md
docs/phantoms/reviews/023b-independent-review.md
docs/phantoms/reports/023b-rift-route-consent-closure.md
docs/phantoms/architecture/RIFT_RECRUITMENT_CONTRACT.md
PHANTOM_DEVELOPMENT_MASTER_PLAN.md
docs/PHANTOM_BOTS_ROADMAP.md
```

Do not touch a file only because it is listed. Use the smallest correct change
set, with justified expansion when needed.

## 8. R023B-01 production correction

Implement `ARCHITECTURE.md` route-aware binding.

At minimum:

- expose exact bounded per-group route activity through Goal017;
- include route-coordinator planner-pending ownership;
- include persisted RouteManifest status;
- distinguish nonterminal vs terminal route state;
- prevent content binding from overwriting a nonterminal route;
- prevent second route request over a pre-existing route;
- perform terminal cleanup/reconciliation through Goal017 ownership;
- refresh binding identity if terminal route reconciliation changes its manifest;
- final READY fails closed while route work is live;
- preserve canonical membership and group generation unless membership changed.

No direct Rift navigation ownership.

## 9. R023B-02 production correction

Strengthen and dynamically prove actual
`PhantomRiftService.evaluateManagedInvitation(...)`.

Before ACCEPT for managed Phantom, refresh exact current candidate eligibility
for the persisted missing vacancy using existing read-only authorities.

Required current evidence:

```text
exact canonical invitation still pending
requester preparation goal/revision/tier
exact current party binding and canonical requester roster
same source hashes
same still-missing vacancy
candidate exact identity
visible/local/perceptible range and instance
current party membership
alive/current RoleMatcher capability evidence
current invitee goal precedence/conflict
current invitee->leader relationship policy
```

Use Goal017/canonical PartyInvitationService for response.
Do not create/replace invitee goals.

## 10. Tests

Use seed only:

```text
phantom.goal023b.seed=23002312
```

Do not override global seed.

Dynamic proof is mandatory. Source `contains(...)` checks may supplement but
cannot replace:

- active route planning/moving/regrouping binding tests;
- actual production `riftService::evaluateManagedInvitation` canonical test.

The managed-consent acceptance test must fail if provider is replaced by
`ignored -> ACCEPT`.

`TEST_CASES.md` gives exact scenarios.

## 11. Performance/bounds

Preserve:

- candidate discovery <=32;
- local visible/perceptible source;
- no global player scan;
- no full DB/profile/NPC/XML scan per pulse;
- route activity query O(1) or bounded current group state, not global route scan;
- managed policy one bounded candidate refresh, not new global discovery;
- no per-pulse social writes;
- fixed metric cardinality;
- no high-frequency INFO/WARNING.

Record operation counts and diagnostic wall-clock; no flaky nanosecond hard gate.

## 12. Concurrency/lifecycle

- no new worker/thread/executor/Future/task/timer;
- Goal017 remains single Party/route/invitation saga owner;
- route query must not create cross-service lock cycle;
- binding cannot hold a persistence lock across unbounded work;
- canonical one-pending-invite rules remain authoritative;
- save conflict after external action reconciles exact identity, never reissues;
- shutdown leaves zero route planner/navigation/movement ownership and no
  dangling policy callback.

## 13. Database

```text
test DB only: l2jmobiush5_phantom_test
production DB forbidden: l2jmobiush5
```

No SQL migration/table is expected or justified. Use existing DB guard for
DB-backed integration.

## 14. Out of scope

```text
Goal 024+
farming spot claims/negotiation
PvP/PK
raid/epic
clan
Rift entry execution/combat
entry item consumption
teleport fallback
room jump/spawn
new Party or navigation kernel
language phrase bank/runtime LLM
```

Other chronicles and `.l2j` files forbidden.

## 15. Verification

During development run exact focused modes and affected regressions after
relevant changes.

Before freeze:

```text
git diff --check
historical verifier 023
historical verifier 023A
working-tree verifier 023B
```

Final Goal023B aggregate includes at least current exact equivalents of:

```text
all Goal023B dynamic focused tests
phantom-party-route-test
phantom-party-state-recovery-test
phantom-party-lifecycle-test
phantom-party-server-integration-test
phantom-rift-goal023-test
phantom-rift-goal023a-test
phantom-conversation-party-actions-test
phantom-conversation-query-execution-test
historical verifier 023
historical verifier 023A
working-tree verifier 023B
```

If exact target names differ, use current `build.xml` names and document mapping.

After production/data/test/build/verifier freeze:

```text
one final Goal023B aggregate
one plain ant verify
one standalone ant jar
ordinary commit
git push origin feature/phantom-world
two post-commit verifier 023B runs: PS5.1 + already verified available PS7
```

Verifier stdout must be byte-identical.

A second full verify is allowed only after a real relevant correction to a
failed first run. Do not loop full verifies for unrelated timing noise.

## 16. Verifier 023B

Create descendant-compatible verifier pinning:

```text
parent = 563752f6844076fdbaeb3be7c5cae979c757960a
subject = fix(phantoms): close rift route and consent gaps
branch = feature/phantom-world
seed = 23002312
```

Do not encode artificial file-count ceilings.

Verify at least:

- Goal024+ not implemented;
- no other chronicle/geodata/SQL changes;
- no forbidden worker/client/global-scan APIs in relevant added code;
- route activity participates in content binding stability;
- nonterminal route cannot be silently cleared by binding;
- production managed-consent integration references actual Rift provider;
- historical 023/023A verifiers remain descendant-compatible;
- required compiled classes in GameServer.jar as applicable.

Static verifier never substitutes for dynamic tests.

## 17. Report/documentation

Create:

```text
docs/phantoms/reports/023b-rift-route-consent-closure.md
docs/phantoms/reviews/023b-independent-review.md
```

The latter is handoff only:
`IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`, never ACCEPT.

Report additionally includes:

```text
R023B-01/R023B-02 -> exact fix mapping
pre-audited files actually read
additional files read/changed by expansion rule + reason
route ownership before/after
actual managed-provider integration topology
dynamic matrix/results
DB guard
verifier outputs/hashes
full verify/jar
Git SHA/push/remote equality
limitations
```

Update master plan and roadmap only at the end. Correct a current-state summary
if it contradicts actual Goal022/023 history; do not rewrite historical facts.

## 18. Commit/push and BLOCKED behavior

Success:

```text
git add -- <exact changed Goal023B paths>
git commit -m "fix(phantoms): close rift route and consent gaps"
git push origin feature/phantom-world
```

No amend/rebase/squash/merge/reset/force push.

If BLOCKED:

- remove unsafe/uncompilable production experiments;
- preserve useful bounded audit/tests/docs;
- create honest BLOCKED report;
- commit/push it;
- do not start Goal024.

## 19. Terminal token

Print only after every mandatory gate:

```text
GOAL_023B_RIFT_ROUTE_CONSENT_CLOSURE_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
```
