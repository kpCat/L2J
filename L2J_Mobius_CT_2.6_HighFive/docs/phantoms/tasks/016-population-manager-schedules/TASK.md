# Goal 016 — PopulationManager, level-1 creation and schedules
## 1. Git, baseline and progress
```text
branch: feature/phantom-world
required parent: a546dae868d93d54ec4bc6e1836080b90f810167
test DB only: l2jmobiush5_phantom_test
production DB forbidden: l2jmobiush5
seed: 16001601
subject: feat(phantoms): add population manager and schedules
success token: GOAL_016_POPULATION_MANAGER_SCHEDULES_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
```
Create one ordinary child and push to `origin/feature/phantom-world`. No amend/rebase/squash/
merge/force push. Commit and push an honest SUCCESS/BLOCKED/FAILED result.
Record independent review truth:
```text
Goal 013/013A: ACCEPT after Goal 013B
Goal 013B: ACCEPT_WITH_ACTIVATION_GATE
Goal 014: ACCEPT after Goal 014A
Goal 014A + completion: ACCEPT
Goal 015 including loot/position/tolerance chain: ACCEPT
Goal 016: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Goal 017/025: NOT_STARTED
```
Create `docs/phantoms/reviews/015-background-farming-final-review.md`. Goal 015
production activation remains disabled by the global feature flag; no corrective
Goal 015 suffix remains.
Follow `docs/phantoms/PHANTOM_CODEX_EFFICIENCY_STANDARD.md`. Goal 016 is one
coherent capability: do not split it into 016A/016B merely to reduce this run.
## 2. Product outcome
Implement:
```text
configured population target
→ durable managed profile shell
→ restart-safe canonical level-1 character creation
→ scheduler registration
→ data-driven weekly schedule
→ sleeping/wakeup and bounded ACTIVE admission
→ materialization/dematerialization
→ deterministic retirement/return
→ restart reconciliation and backpressure
```
The manager owns only profiles with `population.state`; existing manual/test
profiles remain unmanaged.
Production defaults are inert:
```text
EnablePhantomSystem=False
PhantomPopulationTarget=0
PhantomPopulationActiveTarget=0
```
No profile/account/character/component is created when the system is disabled or
when target is zero and no managed population already exists.
## 3. Pre-implementation architecture audit
Before editing, write a compact internal table covering:
```text
profile/component writers
goal writers
accounts and character creation writers
character name/account uniqueness
initial item/skill/shortcut writers and swallowed SQL exceptions
World/autosave/identity owners
scheduler registration/signal/unregister writers
materialization/background lifecycle
restart owner for every intermediate creation/retirement state
```
Do not assume `Player.create`, `addSkill`, `addItem`, `storeMe` or event dispatch
is durable merely because runtime state changed.
### Exact READ_SET
Initial bounded symbol/range reads only:
1. this task and efficiency standard;
2. Goal 016 sections of roadmap/master plan;
3. `PhantomSystem` construction/startup/shutdown;
4. `PhantomPlayersConfig` and `PhantomPlayers.ini`;
5. `PhantomScheduler` register/signal/pulse/unregister/stop;
6. `PhantomProfileRepository` profile/component SQL;
7. `PhantomGoalStateStore`, `PhantomGoal`, decision candidate/handler contracts;
8. `PhantomMaterializationService` and Goal 015 absent-state lifecycle paths;
9. `CharacterCreate` validation/init path;
10. `Player.create/createDb/restore`, not the whole file;
11. `InitialEquipmentData`, `InitialShortcutData`, start-class skill tree APIs;
12. current test DB schema for accounts/characters/items/skills/shortcuts.
Up to eight additional exact files are allowed only for a proven creation,
LoginServer account, event-listener or scheduler-control seam. Record each path,
symbol and reason. No other chronicle and no old task-package reread.
## 4. Accepted Goal 015 verifier compatibility
Before Goal 016 production edits, make `verify-task-015.ps1` historical and
descendant-compatible:
- pin accepted final Goal 015 commit `a546dae868d93d54ec4bc6e1836080b90f810167`;
- verify its parent `7037fe92ad930425a600d070bbaf6c2d0234ada0` and exact subject
  `fix(phantoms): resolve anchor tolerance data`;
- require `a546dae868d93d54ec4bc6e1836080b90f810167` to be an ancestor of current HEAD;
- verify accepted Goal 015 scope/blob evidence at the pinned commit, not by
  requiring current HEAD to be its direct child;
- do not include Goal 016 paths in Goal 015 historical allowlist/diff;
- preserve Goal 015 semantic/data hashes or exact pinned blobs.
Run the corrected historical verifier once before continuing. This is part of
Goal 016, not a new Goal 015 suffix.
## 5. Configuration and population catalog
Extend `PhantomPlayersConfig.Settings` with backward-compatible constructors and
strict settings:
```text
PhantomPopulationTarget                 default 0, range 0..MaxScheduled
PhantomPopulationActiveTarget           default 0, range 0..min(target,MaxMaterialized)
PhantomPopulationCreationInFlight       default 2, range 1..64
PhantomPopulationBoundariesPerPulse     default 64, range 1..10000
PhantomPopulationTimeZone               default UTC, valid ZoneId
```
Invalid enabled configuration fails closed. Target and active target are
capacity/tuning data, never Java constants.
Create strict bounded:
```text
dist/game/data/phantoms/population/high-five-population-v1.xml
```
It contains:
- deterministic alphanumeric name prefix/suffix corpus;
- all canonically allowed `PlayerClass.level()==0` starting classes with
  positive weights and valid sex/race combinations;
- data-driven weekly schedule templates and weights;
- local-time windows with desired `ACTIVE`, `WARM` or `BACKGROUND`; gaps mean
  `SLEEPING`;
- optional bounded phase jitter.
Validate XXE safety, duplicates, names, complete starting-class coverage,
overlapping windows, midnight wrap, weights, zone compatibility, count/byte
bounds and deterministic SHA-256. No class switch in Java. Schedule values are
explicit tuning data, not claimed retail facts.
## 6. Persistent model and target reconciliation
Create `phantoms/population/**`.
Use one component:
```text
componentType: population.state
schemaVersion: 1
payload <=4096
```
States:
```text
SHELL
ACCOUNT_PREPARED
CHARACTER_PRESENT
INITIALIZING
READY
RETIRE_REQUESTED
RETIRED
INCONSISTENT
```
Persist only bounded identity/reconciliation facts:
```text
population generation and creation ordinal
catalog hash and deterministic seed
name attempt, reserved account, character name
start class/sex/face/hair
schedule template and phase
home map-region ID
expected/actual character object ID
creation stage and exact initialization hash
last typed failure
```
Do not duplicate Player EXP/inventory/runtime state.
Add bounded repository/store queries:
- page managed profiles by `profile_id`, page <=256;
- fetch exact population component;
- atomically create one shell profile plus `population.state` in one MariaDB
  transaction;
- no startup unbounded list and no per-pulse DB scan.
Target reconciliation:
- count only non-retired managed states toward target;
- return deterministic lowest-ID RETIRED profiles before creating new ones;
- reduce target by deterministic highest-ID retirement;
- never delete profiles/accounts/characters automatically;
- `INCONSISTENT` does not trigger an endless replacement loop; expose deficit and
  stop the creation pipeline until restart/operator action;
- at most `PhantomPopulationCreationInFlight` SHELL/creation operations;
- scheduler capacity/backpressure creates no extra shell.
## 7. Shared canonical character initialization
Do not invoke `CharacterCreate`, packets or bypass.
Extract a narrow shared canonical initializer used by both `CharacterCreate` and
population creation. Responsibilities:
```text
HP/MP max and CP zero
current start-location policy and GeoEngine-normalized position
title and vitality
ordinary configurable starting level/SP for client mode
forced exact level 1 for population mode
starting Adena
InitialEquipmentData and equipped flags
level-appropriate auto-get skills
InitialShortcutData
```
Packet validation, packets, CharSelection, online/disconnection and client event
remain in `CharacterCreate`; ordinary behavior must remain equivalent.
Population mode:
- never creates/fakes a `GameClient`;
- never invokes packet handlers;
- does not dispatch client-origin `OnPlayerCreate`; document this explicit
  boundary;
- never enters World or autosave during creation;
- uses `Player.create`, initializes, stops tasks, stores offline, deletes runtime,
  fresh-loads and verifies level/class/position/vitals/Adena/items/skills/
  shortcuts before linking the profile;
- keeps current starting items/config except level is exactly 1;
- no `progression.learn_skill`.
Static parity tests must prove `CharacterCreate` delegates to the shared
initializer and no second copy of initialization loops remains.
## 8. Restart-safe creation saga
Use a deterministic reserved account namespace derived from profile ID, maximum
14 ASCII characters. Before account insertion, persist an unguessable generated
password hash/ownership token in `population.state`. Create an inaccessible
reserved account using the current proven disabled access-level semantics; audit
LoginServer behavior. Any pre-existing non-owned account is `INCONSISTENT`.
Creation stages are restart-reconciled:
1. atomic SHELL profile/state;
2. repair/insert exact `population.bootstrap` ACTIVE goal;
3. register scheduler slot and WARM bootstrap signal;
4. persist `ACCOUNT_PREPARED`, then insert/verify reserved account;
5. persist character intent, then `Player.create`;
6. find zero/one exact character by account+name after restart;
7. idempotently initialize only while profile remains unlinked and inaccessible;
8. fresh DB and `Player.load` verification;
9. optimistic profile character link;
10. state `READY`, remove bootstrap goal/signal, install schedule.
Failure boundaries must not duplicate profile/account/character/Adena/items/
skills/shortcuts. Unexpected extra rows or mismatched initialized state become
`INCONSISTENT`; do not guess compensation or delete unrelated data.
A manager-created character remains `online=0`, outside World/autosave and
unmaterialized until the scheduler later requests materialization.
## 9. Existing scheduler, schedule clock and admission
Add one optional no-op scheduler control port invoked once per shared scheduler
pulse **outside the scheduler monitor**. No second scheduled task, executor or
thread.
The PopulationManager owns an in-memory bounded due heap rebuilt at startup.
Control work per pulse is bounded by `PhantomPopulationBoundariesPerPulse` and
may:
- evaluate due schedules with injected `Clock` and configured `ZoneId`;
- submit/withdraw only source `population.schedule`;
- inspect bounded retirement completion;
- rebalance ACTIVE admission.
It must not scan XML/DB or initialize a character on a pulse.
Schedule requirements:
- correct midnight wrap and DST gap/overlap handling;
- forward clock jumps apply only the latest state, not every missed boundary;
- backward jumps cannot replay an older sequence;
- signal TTL never exceeds existing maximum; long windows use bounded heartbeat;
- restart recomputes current state and next boundary without persistent per-tick
  writes.
ACTIVE admission:
- never exceeds population active target or materialization cap;
- desired ACTIVE overflow is degraded to WARM;
- proportional home-region quotas use current READY population counts and
  largest remainder, not hardcoded regional counts;
- stable daily rotation avoids permanent starvation;
- class/level/region histograms are observable snapshots, not high-cardinality
  metric labels.
## 10. Retirement, return and lifecycle
Retirement flow:
```text
READY → RETIRE_REQUESTED
withdraw schedule signal
scheduler.unregister
wait until slot absent and actor non-materialized
→ RETIRED
```
Restart resumes every intermediate state. Return uses the same profile and
character, marks READY, registers and schedules it before any new shell is
created.
PopulationManager tracks current/peak operations, persistence claims, creation
claims and pulse-control calls.
Startup composition:
```text
profiles/materialization/scheduler object
→ topology/knowledge/progression/commerce/background
→ population catalog/manager and population decision registration
→ decision engine
→ scheduler start
→ population manager start/reconcile
```
Shutdown:
```text
population.beginStop (no new shells/signals)
→ scheduler/decision admission stop
→ existing subsystem drains
→ population.finishStop after pulse/creation/persistence claims are zero
→ materialization/background/scheduler final shutdown
```
Startup failure follows the same ownership order. Disabled Phantom system creates
no population catalog/manager/DB access. Target zero with no managed population
creates nothing.
## 11. Production decision integration
Register before seal:
```text
goal: population.bootstrap
candidate: candidate.population.bootstrap
action: population.create_character
```
The candidate runs only in WARM for an exact current SHELL/creation state and
creates at most one semantic step. It does not invent gameplay goals, farm,
commerce, progression, party or PvP actions.
Creation terminal callbacks replenish the bounded in-flight pipeline. Permanent
failure stops replenishment; no infinite account/name attempts.
## 12. Exact scope
Allowed production/data/config:
```text
java/org/l2jmobius/gameserver/phantoms/population/**
java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java
java/org/l2jmobius/gameserver/phantoms/PhantomScheduler.java
java/org/l2jmobius/gameserver/phantoms/activity/PhantomSchedulerControlPort.java
java/org/l2jmobius/gameserver/phantoms/profile/PhantomProfileRepository.java
java/org/l2jmobius/gameserver/config/custom/PhantomPlayersConfig.java
java/org/l2jmobius/gameserver/model/actor/PlayerCreationInitializer.java
java/org/l2jmobius/gameserver/network/clientpackets/CharacterCreate.java
dist/game/config/Custom/PhantomPlayers.ini
dist/game/data/phantoms/population/high-five-population-v1.xml
```
A different shared initializer path/name is allowed if responsibility-equivalent.
Allowed tests/build/tools/docs:
```text
build.xml
test/java/org/l2jmobius/tests/phantoms/PhantomPopulation*.java
targeted compile-only adaptations to existing config/scheduler/system tests
PhantomTestLauncher.java
tools/phantoms/verify-task-015.ps1
tools/phantoms/verify-task-016.ps1
roadmap/master-plan Goal 015/016 status only
population architecture/report/review/task docs
```
Forbidden:
- schema migrations;
- Player.java behavioral changes;
- LoginServer protocol/auth changes beyond read-only audit;
- background/combat/commerce/progression/Game Knowledge/topology semantics;
- population gameplay goal invention;
- deleting retired characters;
- per-profile tasks/threads/Futures;
- high-frequency logs;
- other chronicles/geodata;
- Goal 017/025.
If current account/character schema or ordinary creation semantics cannot support
the saga without a broader core rewrite, fail closed and report the exact
blocker before unsafe production code.
## 13. Required tests
Focused modes:
```text
population-catalog
population-schedule
population-creation
population-reconciliation
population-lifecycle
population-server-integration
population-performance
```
Mandatory evidence:
- parser/hash determinism, full start-class coverage and invalid corpus controls;
- exact level-1 real characters for at least two different start classes;
- ordinary CharacterCreate initializer parity;
- account/name collision and every creation-stage restart/fault boundary;
- no duplicate account/character/profile link/items/skills/shortcuts;
- target `0→3→1→3`: deterministic creation, retirement and return of the same
  profiles before new creation;
- scheduler capacity and creation-in-flight backpressure;
- schedule midnight, DST gap/overlap, forward/backward clock jumps and restart;
- ACTIVE cap, proportional home-region quotas, stable rotation and sleeping/
  wake materialization;
- materialize/dematerialize real created Player and restart conservation;
- crash during RETIRE_REQUESTED/unregister and recovery;
- disabled mode and target-zero inertness;
- startup/shutdown with blocked creation and pulse-control claims;
- no DB writes during 100,000 pure schedule pulses;
- 100,000 schedule evaluations, 10,000 admission rebalances, synthetic 10,000
  managed profiles within bounded memory/time;
- zero new workers/tasks/Futures.
Use real current loaders and test DB for creation/integration. Fake ports are
allowed only for fault/clock/backpressure injection.
## 14. Verification discipline
Before any full cumulative run:
1. compile affected code;
2. run all seven Goal 016 modes and affected config/scheduler/system suites;
3. run corrected historical verifier 015 and verifier 016;
4. inspect only exact failed reports and fix targeted causes.
Then:
```text
one final Goal 016 aggregate green
one full ant verify
one standalone ant jar
commit/push
two post-commit byte-identical verifier 016 runs
```
A second full verify is permitted only after an actual source/test/verifier fix.
For an unrelated historical flake: run its exact target once, record the result,
do not change out-of-scope code and do not perform repeated broad diagnostic
reads. Never run a third full verify.
Verifier 016 must check exact graph/scope, historical 015 ancestor compatibility,
target-zero inertness, no packet invocation, shared initializer delegation,
creation saga ordering, bounded scans/writes, schedule control outside scheduler
monitor, no worker/task/Future, lifecycle, tests, UTF-8 and JAR contents.
Create:
```text
docs/phantoms/architecture/POPULATION_MANAGER_SCHEDULE_CONTRACT.md
docs/phantoms/reports/016-population-manager-schedules.md
```
Report <=220 lines with actual READ_SET expansions, creation/schedule/retirement
matrices, selected real fixtures, every test/full-verify run, usage and remaining
limitations. Do not self-accept.
Print `GOAL_016_POPULATION_MANAGER_SCHEDULES_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW` only after every gate. On blocker remove unsafe production
changes, preserve safe audit/tests/docs, ordinary commit/push and return an
honest BLOCKED token.
