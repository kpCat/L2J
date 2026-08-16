# Goal 025 — PvP/PK, threat and bounded escalation

## Identifier

```text
Task ID: 025-pvp-threat-escalation
Goal: 025
Branch: feature/phantom-world
Required parent: 922f72c0d422904dcbdc6215a5cc1167a1bb84fb
Required commit subject: feat(phantoms): add pvp threat escalation
Seed: 25002501
Target verdict: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
```

## 1. User result

Implement the first safe autonomous PvP/PK vertical slice for Phantom World.
Phantoms must be able to recognize a causal Player threat/conflict, assess
bounded human-plausible risk, warn/help/retreat/engage, execute through canonical
High Five PvP mechanics, consume real CP potions, and observe real PvP/PK/karma
consequences without uncontrolled aggression.

This is not a sandbox combat simulator. The actual Player/Skill/damage/death/
karma/item systems remain authoritative.

## 2. Dependency gate

Before any implementation, verify exact branch/HEAD/status and read
`PRIOR_INDEPENDENT_REVIEW.md`.

Required truth:

```text
Goal024A = ACCEPT
R024A-01/02/03 = CLOSED
Goal024 overall = ACCEPT
Accepted baseline = 922f72c0d422904dcbdc6215a5cc1167a1bb84fb
Goal025 = AUTHORIZED
Goal026+ = NOT_STARTED
```

If HEAD contains user-owned commits after the required parent, do not reset or
overwrite them. Inspect and either prove compatibility or report the exact
source-backed blocker. Do not silently implement against an unknown lineage.

## 3. Read-first mandatory audit

Read completely before coding:

- `AGENTS.md` if present in module scope;
- `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`;
- `docs/PHANTOM_BOTS_ROADMAP.md`;
- `docs/phantoms/CODEX_WORKFLOW_CONTRACT.md`;
- `docs/phantoms/TASK_PACKAGE_STANDARD.md`;
- `docs/phantoms/CODEX_REPORT_TEMPLATE.md`;
- this entire `docs/phantoms/tasks/025-pvp-threat-escalation/` package.

Then audit exact current High Five call paths, at minimum:

### Combat / Player authority

- Goal012/012A reports/reviews;
- `phantoms/combat/PhantomCombatService.java`;
- `PhantomCombatBackend.java`;
- `L2jCombatBackend.java`;
- combat request/result/session/loadout/mode/owned-action/skill-safety/step handlers;
- `model/WorldObject.java`;
- `model/actor/Creature.java`;
- `model/actor/Player.java`;
- `model/actor/status/PlayerStatus.java`;
- `network/clientpackets/AttackRequest.java` only to map canonical call path;
- `network/clientpackets/RequestMagicSkillUse.java` only to map canonical call path;
- Player AI/Creature attack/cast implementation only where exact call-path proof
  requires it.

Do **not** use client packets as Phantom APIs.

### PvP/CP/config authority

- `config/PvpConfig.java`;
- `config/RatesConfig.java`;
- exact current High Five `PVP.ini`/`Rates.ini` keys if needed for parity tests;
- `data/xml/ItemData.java`;
- `dist/game/data/stats/items/05500-05599.xml` exact 5591/5592 entries;
- registered `ItemSkills.java` and `ItemSkillsTemplate.java`;
- exact skill 2166 data/effect path only as needed to prove canonical CP outcome.

### Dependencies

- Goal013 accepted capability/progression catalog and combat resolver;
- Goal017 party coordinator/backend/model/tactics/route code;
- Goal018 social service/model/policy and event catalog;
- Goal020 conversation execution/catalog/outbound dispatch/semantic owner;
- accepted Goal024/024A farming service/model and exact escalation evidence;
- Goal009 navigation request/service/progress/action execution path;
- Phantom materialization/identity registry;
- `PhantomSystem.java` startup/shutdown/candidate/handler wiring.

This is the predicted read set, not a numeric cap. Additional High Five reads are
allowed only for exact call-path/invariant necessity. No other chronicle.

## 4. Mandatory architecture

Follow `ARCHITECTURE.md` as normative.

Non-negotiable decisions:

1. one bounded Goal025 PvP orchestration layer;
2. no second combat engine;
3. existing monster attack/cast semantics stay exact;
4. explicit Player-target PvP combat path inside existing Goal012 combat owner;
5. physical execution delegates to canonical forced-attack server seam;
6. skill execution delegates to canonical `Player.useMagic` semantics;
7. Goal025 never mutates HP/CP/PvP flag/PK/PvP kills/karma/drop inventory;
8. CP potions are real owned items through canonical ItemSkills handler;
9. every aggression candidate is causally sourced;
10. proactive forced PK requires warning + bounded authority/budget;
11. Goal018 owns revenge/social memory;
12. Goal020 owns warning/help language/chat;
13. Goal017 owns party help membership/tactics;
14. navigation owns route/path decisions and movement integration;
15. no new worker/timer/Future/thread;
16. no global Player/profile scan.

## 5. Expected production change set

Expected, not numerically capped:

- new bounded `phantoms/pvp/*` orchestration/model/policy/store/decision package;
- explicit Player PvP additions to existing combat backend/service/owned action
  without altering legacy monster contract;
- narrow Goal024 read-only exact escalation-evidence seam if current public API
  cannot supply it;
- narrow Goal017 PvP protection distinction/help integration;
- narrow Goal020 typed outbound warning/help seam, preferably factoring/reusing
  existing dispatch ownership rather than duplicating chat code;
- narrow Goal018 PvP social event/policy additions if required for attack/kill/
  death/revenge evidence;
- navigation integration only if current action route lacks a typed retreat seam;
- `PhantomSystem` wiring in accepted lifecycle order;
- versioned High Five Phantom PvP policy data;
- build/tests/verifier/docs/report/review handoff.

Do not modify server core merely to make Phantom integration convenient. A core
change is allowed only if exact audit proves a missing authoritative reusable
seam, the change is generic and behavior-preserving for ordinary players, and
the report explains the exact call path/invariant. Prefer adapter-level reuse.

For every extra changed file beyond the predicted set, report:

- path;
- symbol/call path;
- why predicted set was insufficient;
- why change is in Goal025 scope.

## 6. Hard out of scope

- Goal026 raid/epic orchestration;
- Goal027 formal clan/alliance/war lifecycle;
- broad party redesign;
- new login/network protocol;
- packet simulation;
- production DB;
- `.l2j` mutation;
- other chronicles;
- general PvP AI based on scanning all Players;
- exact damage simulator;
- custom karma/drop engine;
- runtime LLM/phrase bank;
- unrelated refactor/flaky-test repair.

## 7. Persistence / DB

No SQL/schema migration is expected. Use existing generic profile component
persistence for bounded encounter/cooldown/idempotency state.

Test DB only when an existing integration fixture requires it:

```text
127.0.0.1:3308
l2jmobiush5_phantom_test
root / root
```

Production DB `l2jmobiush5` is forbidden.

Every destructive test-owned Player/inventory/config fixture must restore its
state in `finally`.

## 8. Performance / bounds

Policy/data must make all hot-path limits explicit. At minimum:

- attacker observations capped;
- local support/risk context capped;
- one encounter per profile;
- finite pair cooldown/engagement history;
- bounded social/chat receipts;
- party fanout <= canonical party size;
- no label-per-profile/player metrics;
- overload degrades to WAIT/RETREAT/DISENGAGE, not unbounded queue/history.

No `World.getPlayers()` and no full profile registry iteration.

## 9. Concurrency / lifecycle

- existing Decision pulse owns Goal025 orchestration scheduling;
- existing Combat worker owns combat action execution;
- no new executor/thread/timer/Future;
- stable pair ordering for exact two-profile persistence;
- no locks across external subsystem/DB calls;
- stale token/authority cannot release current owner;
- stop closes new PvP admissions before dependent cleanup;
- no retained PvP action/encounter after final stop.

## 10. Required tests

Implement all behaviors in `TEST_CASES.md` with deterministic seed `25002501`.

Expected new target family, names may be adapted consistently:

```text
phantom-pvp-policy-test
phantom-pvp-admission-test
phantom-pvp-combat-integration-test
phantom-pvp-cp-test
phantom-pvp-party-help-test
phantom-pvp-warning-social-test
phantom-pvp-restart-test
phantom-pvp-performance-test
phantom-pvp-goal025-focused-test
phantom-pvp-goal025-affected-test
phantom-pvp-goal025-test
phantom-static-verify-025
```

Pin legacy monster/acquisition semantics as negative controls.

## 11. Verification discipline

Follow the project standard:

- no unrelated findings;
- no historical flake fixing without direct Goal025 relation;
- one targeted rerun of a suspected unrelated timing flake;
- no stress loops >2;
- PASS evidence is monotonic;
- docs/report/verifier-only changes do not invalidate production test evidence;
- rerun only affected gates after behavior-changing fix;
- one final Goal025 aggregate after production/test freeze;
- because this is a new VERY_HIGH-risk cross-system goal, one final plain
  `ant verify` and one `ant jar` are justified after freeze;
- do not repeat aggregate/verify after process-only edits.

If context growth/compaction becomes material: stop new investigation, create a
truthful report, preserve safe result, commit, push, handoff.

## 12. Documentation

Create/update at minimum:

- architecture contract for Goal025;
- `docs/phantoms/reports/025-pvp-threat-escalation.md`;
- `docs/phantoms/reviews/025-independent-review.md` with pending handoff only;
- master plan / roadmap current status;
- task verifier 025;
- this task package retained unchanged except manifest/process correction that
  is explicitly explained.

Record accepted Goal024A/Goal024 baseline before marking Goal025 implementation.
Do not self-ACCEPT Goal025.

## 13. Final report contract

Final report and terminal response must include:

```text
branch
parent SHA
commit SHA
remote HEAD
subject
verdict
exact unfinished gates/findings
```

Also include:

- actual read set and every expansion;
- actual changed files and reason;
- exact causal source matrix;
- legacy monster isolation proof;
- physical/skill canonical call paths;
- CP potion source/data/handler evidence;
- PvP/PK/karma/drop consequence evidence;
- warning/help ownership;
- retreat ownership;
- restart/cooldown proof;
- operation/bounds/performance evidence;
- test DB guard;
- all verification commands/results;
- historical unrelated flake classification if any.

## 14. Commit and push

After safe result is ready, ordinary commit with exact subject:

```text
feat(phantoms): add pvp threat escalation
```

Then ordinary push:

```text
git push origin feature/phantom-world
```

Push even if truthful verdict is PARTIAL, BLOCKED,
PENDING_INDEPENDENT_REVIEW or FAILED_EXTERNAL_GATE, after reverting only an
unsafe experimental production hunk if one exists.

Forbidden:

```text
amend
rebase
squash
force push
reset of user history/worktree
```

No review ZIP.

## 15. Stop rule

Stop with a source-backed BLOCKED/PARTIAL handoff instead of inventing a second
engine if exact audit proves that safe canonical PvP execution would require:

- bypassing Player PvP legality;
- direct damage/karma/drop mutation;
- packet simulation as the internal API;
- broad server-core redesign;
- global victim scans;
- an unrelated subsystem rewrite.

A blocker does not justify leaving correct task/docs/tests/evidence uncommitted.
Commit and push the safe reviewable result.

## Terminal success token

Only after successful implementation/delivery:

```text
GOAL_025_PVP_THREAT_ESCALATION_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
```
