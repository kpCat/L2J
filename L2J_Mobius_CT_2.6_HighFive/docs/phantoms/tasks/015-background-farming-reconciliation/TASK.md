# Goal 015 — Background farming baseline and active/background reconciliation
## 1. Git and progress contract
```text
branch: feature/phantom-world
required parent: 9c9412bc4a05a520a83b5187054d6c8a8c12db3c
test DB only: l2jmobiush5_phantom_test
production DB forbidden: l2jmobiush5
seed: 15001501
subject: feat(phantoms): add background farming reconciliation
success token: GOAL_015_BACKGROUND_FARMING_RECONCILIATION_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
```
Create one ordinary child and push to `origin/feature/phantom-world`. No amend/rebase/squash/
merge/force push. Commit and push an honest SUCCESS/BLOCKED/FAILED result.
Record independent review truth:
```text
Goal 013/013A: ACCEPT after Goal 013B
Goal 013B: ACCEPT_WITH_ACTIVATION_GATE
Goal 014: ACCEPT after Goal 014A
Goal 014A + completion: ACCEPT
Goal 015: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Goal 016/017/025: NOT_STARTED
```
Create `docs/phantoms/reviews/014a-commerce-completion-review.md`. Preserve all
accepted progression, commerce, combat, knowledge, topology and lifecycle
contracts.
Follow `docs/phantoms/PHANTOM_CODEX_EFFICIENCY_STANDARD.md`. This is one
architecturally complete Goal; do not split it into 015A/015B merely to reduce
the current run.
## 2. Exact scope and supported gameplay
Implement one causal bounded baseline for:
```text
explicit persisted farm.background goal
→ optional background-eligible topology travel
→ bounded solo normal-monster encounters
→ exact resource consumption
→ authoritative EXP/SP and drop rolls
→ HP/MP attrition and deterministic death
→ atomic canonical DB commit
→ duplicate/restart reconciliation
→ promotion to materialized Player with no reset or duplication
```
Supported:
- one profile and exact linked character;
- main or exact active subclass;
- normal solo monster only, instance `0`;
- single-target physical melee/ranged, direct magic or summon-primary baseline;
- exact explicit NPC ID and topology farm anchor;
- soulshot/spiritshot and exact summon-resource consumption when the captured
  model requires them;
- ordinary death drops from the target, including bounded stackable and
  non-stackable inventory objects;
- passive HP/MP regeneration;
- PvE death, EXP loss and materialized recovery;
- background-eligible topology edges and exact committed anchors;
- competition among background profiles using configured spawn capacity.
Excluded:
- party/command channel, spoil/sweep/manor, quests, crafting, champion mobs,
  raids/epics, instances, PvP/PK/Olympiad, tactical potion decisions, buffs
  expiring in background, private stores, enchant and population creation;
- arbitrary target selection or one script per class;
- `progression.learn_skill` candidates;
- fabricated vitality, premium, PC-cafe, quest or event rewards.
If an excluded condition is observed, fail closed before mutation and request
replan/materialization where appropriate.
## 3. READ_SET and audit
Initial exact READ_SET, bounded symbol/range reads only:
1. this task, efficiency standard and Goal 015 roadmap/master-plan sections;
2. `PhantomActivityState`, `PhantomActivityWorkItem`,
   `PhantomSchedulerPolicy`, scheduler work/transition ranges;
3. `PhantomDecisionEngine` work claim/context propagation and decision records;
4. `PhantomMaterializationService`, `PhantomMaterializedPlayer`,
   `PhantomMaterializationServiceActivityPort`;
5. `PhantomIdentityLeaseRegistry` and current real-login lease integration;
6. `PhantomProfileRepository/Component`;
7. `PhantomGameKnowledgeQuery/Model` NPC/drop/spawn facts;
8. `PhantomTopologyQuery`, node/anchor/edge records;
9. progression actor snapshot/capability/equipment/summon facts;
10. commerce supply facts exact-query surface;
11. combat capability resolver/loadout and terminal session snapshot;
12. `Player` SQL constants, `store`, death-exp calculation and rewardSkills only;
13. `PlayerStat/PlayableStat` EXP/SP and `ExperienceData/ExperienceLossData`;
14. `Attackable.calculateExpAndSp` and normal drop/group semantics only;
15. `Item` persistence SQL, `Inventory` capacity/weight/item-count writers;
16. `IdManager` object-ID allocation;
17. current scheduler/materialization/decision integration suites;
18. `build.xml` current Phantom targets and cumulative verifier chain.
Before production edits, write an internal all-writer table for:
```text
identity ownership
main/subclass EXP/SP/level
cur HP/MP/CP and position
inventory item rows/object IDs
goal and background components
materialization/autosave/real login
```
Do not infer SQL/chance units or subclass semantics from memory. Up to eight
additional exact files are allowed for proven writer/formula/login seams. Report
every expansion. Do not read old task packages/reports or other chronicles.
## 4. Background identity and lifecycle ownership
Add a distinct typed background owner to the existing Phantom identity registry,
or an equivalently observable purpose that cannot be confused with a materialized
Player.
`BackgroundLease` acquisition order:
```text
profile character link
→ background identity claim
→ prove no REAL_LOGIN/PHANTOM materialization owner
→ prove no World Player/object and no autosave owner
→ exact current goal/activity identity
```
While held, real login and materialization must fail closed. If either already
owns the identity, background work returns typed retry with no DB mutation.
The lease is per operation, not permanent. Release only after commit/rollback
and fresh durable verification. Track current/peak operations, identity leases,
DB transactions and transition claims. `beginStop` rejects new work;
`finishStop` remains STOPPING until all counters are zero. No new thread,
executor, timer or per-profile task.
## 5. Scheduler and decision identity
Propagate these exact immutable fields from `PhantomActivityWorkItem` through
planning and step context:
```text
activityGeneration
tickSequence
effectiveState
logicalNowNanos
```
Existing active handlers must remain source-compatible and retain semantics.
Register:
```text
goal type: farm.background
source: background.farm
candidate: candidate.background.farm
actions: background.travel, background.farm, background.recover
```
Source key contains exact NPC ID and exact farm anchor ID; optional exact supply
item IDs are numeric goal constraints, not free-form names. The candidate:
- accepts only an ACTIVE persisted goal;
- runs farm/travel only in BACKGROUND;
- creates a route step before farm when current committed anchor differs;
- in WARM/ACTIVE selects recovery only when durable state is DEAD;
- never chooses targets, creates goals or invokes commerce/progression itself.
Operation key:
```text
profileId + characterObjectId + goalId + goalRevision
+ activityGeneration + tickSequence
+ action kind + target/anchor
+ modelVersion + knowledge/topology/progression/commerce hashes
```
Duplicate exact work is idempotent. Older generation/tick or changed goal/hash is
typed stale and cannot mutate.
## 6. Active baseline capture and transition protocol
Use one bounded component:
```text
componentType: background.state
schemaVersion: 1
payload <= 4096
```
States:
```text
MATERIALIZED
READY
VERIFY_PENDING
DEAD
INCONSISTENT
```
Persist compact facts:
- exact profile/character/class index/active class;
- level, EXP, SP, EXP-before-death;
- current/max HP, MP and CP;
- position/instance and committed topology anchor;
- offense/defense/speed/regen and EXP/SP multiplier facts;
- model kind: MELEE, RANGED, MAGIC or SUMMON_PRIMARY;
- exact selected skill/summon and per-encounter resource item IDs;
- tracked inventory objects/counts, load/capacity;
- RNG state, residual travel/encounter millis;
- last operation key/generation/tick and authoritative hashes.
Extend the accepted materialization lifecycle through a narrow injected port:
1. after canonical `Player.storeMe()` and before identity release, fresh-read the
   exact DB rows and capture/refresh READY baseline;
2. any capture/read mismatch retains lifecycle ownership and blocks demotion;
3. before materialization, drain background operations and resolve
   VERIFY_PENDING;
4. after `Player.load()` and before online/world spawn, compare runtime Player to
   committed background state; mismatch blocks materialization;
5. MATERIALIZED state rejects background work;
6. no transition may restore HP/MP/CP, supplies, EXP or position for free.
All direct materialization paths, scheduler transitions and real-login identity
arbitration must respect the same background lease/state. Do not create a second
lifecycle implementation.
## 7. Versioned encounter model
Create `BACKGROUND_MODEL_V1`; document it as a custom deterministic approximation,
not retail combat equivalence.
Baseline capture must derive numeric actor facts from the real canonical Player,
equipped gear, selected combat capability and controlled summon where present.
Production must not reconstruct class tactics from class names.
Target input comes from current `NpcData` plus Game Knowledge:
```text
level, normal-monster kind, HP/MP, PAtk/MAtk, PDef/MDef,
attack/cast speed, EXP/SP, exact drops, spawn area/capacity
```
Model requirements:
- choose one of MELEE/RANGED/MAGIC/SUMMON_PRIMARY from exact capability evidence;
- derive encounter duration from target effective HP versus captured offense and
  cycle speed, with fixed documented caps;
- derive incoming attrition from target offense versus captured defense;
- apply passive HP/MP regeneration over elapsed time;
- compute exact selected-skill MP and shot/summon-resource use;
- deterministic ±10% bounded variance from persisted RNG state;
- at most `32` encounters and `60_000` logical elapsed milliseconds per batch;
- stop before reserve, weight, capacity, HP/MP or unsupported-state violation;
- no healing/CP potion tactics; CP remains unchanged in PvE except death sets it
  to zero;
- death occurs causally when the encounter's bounded incoming damage exhausts
  HP, not as an unrelated arbitrary coin flip.
EXP/SP:
- reproduce current single-player, full-damage, normal-monster
  `Attackable.calculateExpAndSp` level-difference and rounding semantics;
- use current rate configuration and captured applicable actor multiplier;
- reject vitality/Nevit/event/party/premium cases not exactly represented;
- apply servitor EXP multiplier only from captured exact summon facts.
Drops:
- preserve current chance units;
- reproduce ungrouped-independent and grouped-cumulative semantics exactly;
- use one persisted deterministic RNG stream;
- never use expected-value fractional items;
- reserve object IDs through current `IdManager`;
- maximum `16` changed item objects and `8` new non-stackable objects per batch;
- check exact inventory weight/capacity before committing each encounter.
Competition:
- one bounded process-local reservation registry keyed by exact
  `(topologyNodeId, npcId)`;
- capacity derives from authoritative configured spawn amount, clamped `1..32`;
- reservation exists only during one operation and never itself grants rewards;
- no capacity means typed retry with zero mutation.
## 8. Atomic canonical transaction
Create one dedicated MariaDB transaction facade under `phantoms/background`.
Only it may mutate stored background farming state.
On one connection with `autoCommit=false`, fixed query timeout and stable locks:
```text
exact phantom profile/character link
→ exact persisted ACTIVE goal component
→ exact background.state component
→ characters row
→ exact active subclass row when classIndex > 0
→ exact character_skills auto-get rows when level crosses
→ exact existing item rows in ascending object ID
→ new reserved item object IDs
```
Guard and update together:
- main or subclass EXP/SP/level;
- `expBeforeDeath`;
- cur HP/MP/CP;
- exact x/y/z/heading/instance-compatible position fields;
- consumed supplies and awarded drops/Adena;
- auto-get-only skills for crossed levels, never paid/manual CLASS skills;
- background state as VERIFY_PENDING with exact expected-after hash.
Every write affects exactly the expected row count. Any pre-commit error rolls
back every canonical and component mutation. After commit, fresh connection
verification promotes VERIFY_PENDING to READY/DEAD with optimistic CAS.
Post-commit read failure fail-stops service; restart resolves VERIFY_PENDING from
the committed expected hash. INCONSISTENT never auto-recovers.
For subclass, mutate exact `character_subclasses` EXP/SP/level and do not
contaminate base-class EXP/SP. Auto-get skills use exact class index. Level
crossing must match `ExperienceData`; no fabricated skills.
## 9. Travel, death and recovery
Travel:
- only exact current topology route and edges with `backgroundEligible=true`;
- use authoritative `baseTravelMillis`;
- closed/nontraversable edge retries without position mutation;
- mid-edge state stores residual time while canonical position remains at the
  last committed anchor;
- completing an edge atomically updates canonical position and anchor;
- promotion mid-edge loads the last committed anchor, never snaps forward.
Death:
- set HP and CP to zero, preserve resulting MP/resources/drops, record DEAD;
- compute normal-mob EXP loss from current `ExperienceLossData`, captured
  `REDUCE_EXP_LOST_BY_MOB`, exact level span and current configuration;
- preserve `expBeforeDeath`; apply allowed delevel and exact auto-get skill
  reconciliation;
- emit a bounded scheduler relevance signal requesting WARM;
- no further farm batches while DEAD.
Recovery:
- materialize through the accepted lifecycle;
- `background.recover` uses exact ActionLease and canonical Player revive plus
  current to-town teleport semantics;
- no free supplies/EXP/CP restoration beyond canonical revive behavior;
- fresh runtime/DB/state verification;
- after recovery return FAIL_GOAL with a typed death reason so later policy may
  select restock/retry; do not silently resume the killed farm goal.
## 10. Startup, shutdown and production composition
Production order:
```text
profile/identity/materialization
→ topology + Game Knowledge + progression + commerce
→ background service/coordinator
→ candidates/handlers and decision engine
→ scheduler
```
Shutdown:
```text
scheduler/decision stop admission
→ combat/progression/commerce/background drain
→ materialization drain
→ remaining services
```
Disabled mode creates no background component read, identity lease, transaction,
reservation, worker or log.
Goal 015 may close the Goal 013B activation gate only for its own stored
background EXP/SP/item/auto-get transaction. It must not register or enable
`progression.learn_skill`.
## 11. Allowed files
Production:
```text
java/org/l2jmobius/gameserver/phantoms/background/**
java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java
java/org/l2jmobius/gameserver/phantoms/PhantomMetrics.java
java/org/l2jmobius/gameserver/phantoms/PhantomScheduler.java
java/org/l2jmobius/gameserver/phantoms/activity/**
java/org/l2jmobius/gameserver/phantoms/decision/**
java/org/l2jmobius/gameserver/phantoms/player/**
```
Only an already existing real-login Phantom arbitration seam may be changed, and
only to honor background identity ownership/state. Ordinary combat, commerce,
progression, Game Knowledge, topology loaders, Player, Item, Inventory,
Attackable and database schema are read-only.
Tests/build/tools/docs:
```text
build.xml
test/java/org/l2jmobius/tests/phantoms/PhantomBackground*.java
targeted existing scheduler/decision/materialization integration suites
PhantomTestLauncher.java
tools/phantoms/verify-task-015.ps1
roadmap/master-plan status only
background architecture/report/review/task docs
```
No migrations/config changes, other chronicles, geodata or Goal 016/017/025.
## 12. Required tests
Focused modes:
```text
background-model
background-transaction
background-lifecycle
background-decision
background-server-integration
background-performance
```
Mandatory evidence:
- exact reward formula and drop-group parity against current loaders;
- deterministic RNG replay and no fractional drops;
- main/subclass isolation and level/auto-get crossing;
- shot/spiritshot/summon-resource consumption;
- stackable/new non-stackable/Adena drops, capacity and weight;
- every transaction fault point rollback;
- VERIFY_PENDING restart recovery and INCONSISTENT fail-stop;
- duplicate tick, stale generation, changed goal/hash;
- concurrent background/background, materialization and REAL_LOGIN ownership;
- 300+ result exact identities where applicable;
- travel partial/full/closed-edge and promotion mid-edge;
- competition capacity and release;
- causal attrition, passive regen, death EXP loss and WARM recovery;
- materialize → dematerialize → 100 background ticks → materialize;
- at least 50 repeated ACTIVE/BACKGROUND transitions with byte-exact conservation;
- server restart between every lifecycle/transaction phase;
- disabled mode and shutdown drain;
- no `progression.learn_skill`, no worker/task/Future, no high-frequency logging;
- 100k pure model evaluations, 10k duplicate reconciliations and bounded real
  DB batches without leaks.
Use real materialized Player and real current normal-monster/topology/drop
fixtures in integration. Fake ports are allowed for fault injection only.
## 13. Verification discipline
Before the first full cumulative run:
1. compile affected code;
2. run each new focused mode and affected existing scheduler/decision/
   materialization suites;
3. run `verify-task-014.ps1`, `verify-task-014a.ps1` and new
   `verify-task-015.ps1` on the working tree;
4. fix every targeted/static failure.
Then require:
```text
one final focused aggregate green
one green ant verify
one standalone ant jar
two post-commit byte-identical verify-task-015 runs
```
A failed full verify may be repeated after a concrete targeted fix; do not impose
a hard stop that prevents proving the final tree, but report every full run and
never use repeated cumulative runs for diagnosis.
The Goal 015 verifier must be cumulative-compatible by pinning accepted commits
as ancestors rather than requiring current HEAD to have their subject.
Create:
```text
docs/phantoms/architecture/BACKGROUND_FARMING_RECONCILIATION_CONTRACT.md
docs/phantoms/reports/015-background-farming-reconciliation.md
```
Report <=220 lines and include formulas/model version, exact SQL/locks,
fixtures, transition/receipt matrices, full READ_SET expansion, all test runs,
usage and limitations. Do not self-accept.
Print `GOAL_015_BACKGROUND_FARMING_RECONCILIATION_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW` only after every gate. On blocker remove unsafe production
mutation, preserve audit/tests/docs, ordinary commit/push and return an honest
BLOCKED token.
