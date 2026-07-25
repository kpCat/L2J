# TASK 004 — Bounded feasibility spike canonical headless Player

## 1. Идентификатор

- **Task ID:** `004-headless-player-feasibility-spike`
- **Master plan stage:** `004. Feasibility spike headless Player`
- **Branch:** `feature/phantom-world`
- **Accepted baseline:** `1ca74a3d96e8fa51612ef3e5145c7398abf60f6d`
- **Baseline parent:** `eb008f2216b3e8381c0181d71ce200bbf4907ac7`
- **Git root:** `C:\Users\endim\L2J_Mobius\`
- **Only module:** `C:\Users\endim\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\`
- **Production DB:** `l2jmobiush5`
- **Allowed test DB:** `l2jmobiush5_phantom_test`
- **Seed:** `20260725001`
- **Codex model:** Sol
- **Effort:** Very High
- **Expected result:** evidence-backed feasibility verdict, not a broad bot implementation.

## 2. Accepted gates

```text
Task 001: ACCEPT
Task 001A: ACCEPT
Task 002 + 002A: ACCEPT
Task 003 implementation: ACCEPT
Task 004: ALLOWED
ADR 0001: Proposed
Task 005: NOT_STARTED
```

Task 003 commit:

```text
eb008f2216b3e8381c0181d71ce200bbf4907ac7
```

Task 003 independently confirmed:

- canonical config defaults false;
- disabled configured path creates no Phantom runtime instance;
- no queue worker, executor, scheduled task, Player, DB or network work;
- lifecycle is inserted after ThreadPool and stopped before ThreadPool shutdown;
- one bounded inert queue, fixed metrics and bounded optional trace;
- all Task 002/002A gates remained GREEN.

## 3. Goal

Prove or disprove that the existing canonical `Player` can be safely used as a
headless Phantom actor without:

- a real TCP connection;
- a fake/null-network `GameClient`;
- an NPC-based final core;
- `PhantomPlayer extends Player`;
- a copied/forked `Player`;
- broad client-packet-handler rewrites;
- production DB access;
- per-phantom thread/executor.

The bounded spike must implement and automatically verify:

1. a minimal Player-owned outbound/session seam;
2. unchanged real-client delegation and packet-effect ordering;
3. a zero-network headless output that invokes mandatory
   `ServerPacket.runImpl(Player)` effects exactly once;
4. bounded recursion and optional fixed-capacity diagnostics;
5. canonical test character create/load;
6. identity ownership and a real-login collision gate;
7. explicit headless materialization without calling `EnterWorld.runImpl`;
8. inventory/skills/world presence;
9. one safe reversible canonical server action;
10. observer visibility;
11. action admission closure before cleanup;
12. store/delete/dematerialize;
13. idempotent repeated cleanup;
14. reload/restart restoration;
15. failure injection after every completed lifecycle step;
16. no world/online/autosave/task/party/trade/request/instance/item residue;
17. bounded one- and ten-fixture measurements;
18. an evidence-based ADR recommendation.

Task 004 is a **feasibility spike**. It is not Task 006 final lifecycle and must
not expand into gameplay AI, Semantic Pack, goals, background simulation,
navigation, combat, population or conversation.

## 4. Mandatory reading

Before any code change, read completely:

1. `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`;
2. `Agents.md`;
3. workflow/package/report standards under `docs/phantoms`;
4. Task 001 audit artifacts:
   - `CURRENT_SYSTEM_AUDIT.md`;
   - `DEPENDENCY_MAP.md`;
   - `HEADLESS_PLAYER_FEASIBILITY.md`;
   - `NEXT_TASK_GATES.md`;
5. ADR `0001-headless-player-integration-seam.md`;
6. Task 002/002A packages, reports and review;
7. Task 003 package/report;
8. current:
   - `Player.java`;
   - `GameClient.java`;
   - `CharacterSelect.java`;
   - `EnterWorld.java`;
   - `Disconnection.java`;
   - `World.java`;
   - `ServerPacket.java`;
   - `AbstractHtmlPacket.java`;
   - `CreatureSay.java`;
   - `ItemList.java`;
   - `ExQuestItemList.java`;
   - `TutorialCloseHtml.java`;
   - `OfflinePlayTable.java`;
   - `OfflineTraderTable.java`;
   - `PlayerAutoSaveTaskManager.java`;
   - `IdManager.java`;
   - current Phantom skeleton;
   - current build/test harness;
9. all documents in this task package.

The newly supplied architectural requirements for Semantic Pack, world
knowledge, explicit goals and ACTIVE/NEARBY/WARM/BACKGROUND/SLEEPING are
mandatory future context, but **must not enter Task 004 code or scope**.

## 5. Initial Git and drift gate

Run:

```bat
git rev-parse --show-toplevel
git status --short --branch
git remote -v
git fetch origin --prune
git rev-parse HEAD
git rev-parse origin/feature/phantom-world
git show --stat --oneline 1ca74a3d96e8fa51612ef3e5145c7398abf60f6d
git diff --name-status eb008f2216b3e8381c0181d71ce200bbf4907ac7..1ca74a3d96e8fa51612ef3e5145c7398abf60f6d
```

Expected:

```text
HEAD == origin/feature/phantom-world == 1ca74a3d...
```

The extracted Task 004 package is expected untracked scope.

Unrelated `docs/agent-tasks/**`:

- do not read;
- do not edit;
- do not delete;
- do not stage;
- report as excluded pre-existing work.

Return `BLOCKED_BASELINE_DRIFT` if there is any unreviewed Player/GameClient,
Task 004, DB schema or unrelated production drift.

## 6. Close Task 003 documentation

Before implementation, update
`docs/phantoms/reports/003-disabled-skeleton-config-metrics.md` with immutable
handoff facts:

```text
Commit: eb008f2216b3e8381c0181d71ce200bbf4907ac7
Parent: 84f29a0002b25d2b1ff1a19fa9c92867479fd6a5
Push: successful
Remote ref: exact
Final verifier 1: 72/72
Final verifier 2: 72/72
Outputs identical SHA-256:
447FDBA9B5C2592C40250FF5026B5DB0E71C66520EF8E0F46CF9E3A252894F9D
Independent review: ACCEPT
```

Create:

```text
docs/phantoms/reviews/003-disabled-skeleton-config-metrics-review.md
```

Required states:

```text
Task 003 implementation: ACCEPT
Task 003 revert: NOT_REQUIRED
Accepted baseline: eb008f2216b3e8381c0181d71ce200bbf4907ac7
ADR 0001: PROPOSED
Task 004: ALLOWED
Task 005: NOT_STARTED
```

Preserve evidence and do not claim Task 004 accepted.

## 7. Mandatory fresh touchpoint audit

Before editing production code, create:

```text
docs/phantoms/audits/004-headless-player-feasibility-spike/TOUCHPOINT_AUDIT.md
```

Record current SHA-256 and exact symbols for:

- `Player` constructors/create/load/restore;
- `_client`, `getClient`, `setClient`, `sendPacket`;
- `isOnline`, `isOnlineInt`, `isInOfflineMode`;
- `spawnMe`, `storeMe`, `deleteMe`, `stopAllTasks`;
- `GameClient.sendPacket` and `load`;
- `CharacterSelect` load/bind failure paths;
- `Disconnection` detach/store/delete;
- `ServerPacket.runImpl`;
- all current `runImpl(Player)` overrides;
- `World` duplicate handling;
- offline play/trade restore;
- autosave membership;
- current Task 003 Phantom lifecycle.

Also record:

- all `isOnlineInt()` call sites and semantics;
- direct `getClient()` dereferences outside client packet handlers;
- all `Player.sendPacket` call sites that can plausibly pass null;
- existing task/future fields and cancellation methods;
- exact minimal test-environment initialization order;
- whether current repository SQL/test DB remains fresh.

### Touchpoint stop rule

If a safe solution requires any of the following, do not proceed with a broad
implementation:

- changing several request-handler families;
- fake `GameClient` or fake `Connection`;
- altering broad packet families;
- Player subclass/fork;
- production DB;
- World duplicate handling as normal ownership arbitration;
- per-phantom thread/executor;
- edits outside the approved production touchpoint envelope.

Commit a safe audit/tests/report and return:

```text
NOT_FEASIBLE_WITHOUT_PLAN_CHANGE_PENDING_INDEPENDENT_REVIEW
```

## 8. Fixed architecture

Detailed graph: `ARCHITECTURE.md`.

### 8.1. Generic Player outbound/session seam

Introduce a small generic, production-neutral interface. Exact naming may follow
local style, but responsibilities are fixed.

Conceptual contract:

```java
interface PlayerOutboundSession
{
    SessionKind kind();
    void send(Player player, ServerPacket packet);
}
```

Required session kinds:

```text
CLIENT_BOUND
HEADLESS
```

The seam is owned by `Player`.

Required behavior:

- every `Player` starts with the client-bound adapter;
- the client-bound adapter obtains the current `Player.getClient()` at send time;
- when a real client exists, delegate to unchanged `GameClient.sendPacket`;
- therefore real order remains:
  `writePacket(packet)` → `packet.runImpl(player)`;
- when no client exists and no headless attachment exists, preserve existing
  null-client/offline no-op behavior;
- `Player.sendPacket(null)` must fail explicitly;
- no packet serialization or effect duplication in `Player`;
- attach/detach is ownership-safe and compare-and-restore;
- a stale attachment handle cannot detach a newer session;
- headless detach restores the client-bound adapter;
- no public API that silently replaces another owner.

Do not modify `GameClient.sendPacket` unless fresh audit proves unavoidable.
`ServerPacket.runImpl(Player)` is already public; do not modify
`ServerPacket.java` merely for access.

### 8.2. Headless output

Create a headless implementation under the Phantom package.

Required:

- no `GameClient`, `Connection`, writable buffer or network call;
- reject null;
- invoke `packet.runImpl(player)` exactly once;
- effect exception propagates;
- actual recursive packet chains terminate;
- bounded recursion depth and bounded packets-per-root-dispatch;
- no retained `ThreadLocal`; if used temporarily, remove at root in `finally`;
- preferred implementation is reentrant synchronized depth accounting;
- optional packet-class recording:
  - disabled by default;
  - fixed capacity;
  - fixed counters;
  - overwrite/drop count;
  - no packet payload/player text/credentials;
- zero INFO/WARNING per packet.

Required effect tests:

1. actual HTML action-cache mutation;
2. actual `ItemList -> ExQuestItemList` recursion/termination;
3. actual `CreatureSay` snoop/observer effect where fixture support allows;
4. actual `TutorialCloseHtml` clear effect;
5. packet with no override is discarded safely;
6. custom counter packet executes once;
7. custom throwing effect propagates;
8. null packet fails;
9. artificial recursive packet cycle hits bounded guard.

Do not weaken or mock away actual packet classes for the four audited effect
families.

### 8.3. Online/session policy

Task 004 must produce:

```text
docs/phantoms/audits/004-headless-player-feasibility-spike/ONLINE_SESSION_POLICY.md
```

Do not change `isOnlineInt()` blindly.

Audit every call site and decide whether an ACTIVE headless player can safely map
to existing detached/offline value `2`.

Preferred policy if the audit proves it safe:

```text
_isOnline=false                      -> 0
real attached client                 -> 1
detached real client                 -> 2
active headless outbound session     -> 2
plain null client without headless   -> 0
```

Requirements:

- real-client values unchanged;
- plain offline/null-client behavior unchanged;
- headless visibility/broadcast behavior proven;
- any caller incompatible with headless value `2` is a gate failure;
- do not introduce arbitrary new numeric values.

If safe observer visibility cannot be achieved without broad changes, return
`NOT_FEASIBLE_WITHOUT_PLAN_CHANGE_PENDING_INDEPENDENT_REVIEW`.

### 8.4. Identity ownership

Create a bounded in-memory identity lease protocol with owner kinds equivalent
to:

```text
REAL_LOGIN
PHANTOM
```

Required invariants:

- at most one owner per character/object ID;
- tokenized idempotent leases;
- stale handle cannot release another owner;
- phantom claim occurs before `Player.load`;
- World is checked before and after phantom claim;
- real login cannot bind while PHANTOM owns the ID;
- phantom cannot load while a real login reservation exists;
- current real-real double-login behavior is not silently redesigned;
- `World.addObject` duplicate cleanup remains last-resort, never normal flow;
- failed load releases its reservation;
- disconnect/delete releases real-session ownership;
- phantom cleanup releases ownership last;
- no production DB lock/table for this spike.

### Real-login integration

A static registry-only test is insufficient by itself.

The actual real character load path must consult the same ownership protocol in
a minimal bounded hook. Fresh audit may authorize exact changes in:

- `GameClient.load`;
- `CharacterSelect` failure cleanup;
- `Disconnection` final release;
- small Player attachment/release support.

Do not construct a `GameClient` in tests and do not fake a network
`Connection`. Use:

- deterministic concurrent lease tests;
- static verification of actual GameClient/CharacterSelect/Disconnection hooks;
- existing real path delegated unchanged when no PHANTOM conflict exists.

If safe lifetime ownership requires broad login/session rewrites, stop the gate.

### 8.5. Bounded materialization spike

Create a testable production spike service under a narrow package, for example:

```text
org.l2jmobius.gameserver.phantoms.player
```

Responsibilities:

- claim identity;
- load canonical `Player`;
- attach identity lease;
- attach headless output;
- run explicit minimal domain initialization;
- set deliberate online/session state;
- spawn/register in World;
- open action admission;
- perform safe facade action;
- stop action admission;
- wait for admitted action count to reach zero with timeout;
- store/delete;
- detach output;
- verify cleanup;
- release identity;
- clear references.

Do not call:

```text
EnterWorld.runImpl
CharacterSelect.runImpl
client packets as internal actions
```

The service is **not started by GameServer in Task 004**. Task 003 default config
remains false. Tests instantiate the spike directly.

State model or behaviorally equivalent:

```text
STORED
CLAIMED
LOADING
MATERIALIZING
ACTIVE
DEMATERIALIZING
FAILED
```

Every transition is synchronized/CAS and observable through immutable snapshot.

### 8.6. Explicit domain initialization

Create:

```text
docs/phantoms/audits/004-headless-player-feasibility-spike/MATERIALIZATION_STEPS.md
```

For every selected step, cite the corresponding `EnterWorld`/offline-system
source and classify:

```text
REQUIRED_NOW
DEFERRED_SAFE
CLIENT_SESSION_ONLY
FORBIDDEN
```

Task 004 should initialize only what is required for:

- inventory;
- skills;
- position;
- world registration;
- observer visibility;
- one reversible action;
- store/delete/reload.

Do not initialize mail, siege, trade, private store, quest execution, autoplay,
party restoration, HWID, LoginServer tracert or network state unless required by
the bounded tests.

### 8.7. Action facade

Create a narrow `PhantomActionFacade` spike.

Only one action is allowed:

```text
reversible inventory add/remove fixture
```

Requirements:

- use canonical `Player`/inventory APIs;
- no direct SQL;
- no request packet;
- fixed test item and amount chosen after ItemData audit;
- before/add/remove/after conservation;
- baseline count restored;
- action admitted only in ACTIVE;
- dematerialization closes admission first;
- concurrent action-vs-cleanup test;
- action failure propagates and does not leave item residue.

Trade, mail, multisell, NPC commerce, movement, combat, skills, party and chat
actions remain unavailable.

## 9. Test environment and fixture

### 9.1. No server/network start

The automated target must not instantiate:

- `GameServer`;
- `LoginServer`;
- `ConnectionManager`;
- `GameClient`;
- network listeners.

Run the integration JVM with working directory:

```text
dist/game
```

Pass absolute module/test config/report paths through JVM properties.

### 9.2. Minimal environment

Create a test-only environment bootstrap that initializes only the exact
configuration, database, thread pool and data singletons required by
`Player.create/load`, inventory, skills, World and cleanup.

It must:

- document exact order;
- use `PhantomTestDatabaseBootstrap` before Hikari;
- use only `l2jmobiush5_phantom_test`;
- initialize `ThreadPool` once;
- shut down ThreadPool and DB in `afterAll`;
- avoid network and script-list startup;
- fail on missing dependency rather than silently expanding to full GameServer;
- report initialized singleton list.

If minimal initialization requires most of GameServer, return a feasibility
blocker instead of copying GameServer startup.

### 9.3. Fixture ownership

Use deterministic names derived from seed, for example:

```text
account: phantom_t004_<seed>
character: PhT004<stable suffix>
```

Preferred creation:

1. test-only insert/reset the dedicated account;
2. obtain real `PlayerTemplate`;
3. call canonical `Player.create`;
4. store/delete the created object safely;
5. use its object ID for `Player.load`.

Cleanup:

- use canonical character deletion where safe;
- delete only the exact owned account;
- verify zero fixture-owned rows/items after final suite cleanup;
- preserve one character only during the restart/reload phase;
- cleanup can run twice;
- no arbitrary account/character CLI argument;
- never touch production DB.

Use existing `phantom_test_harness` for test bookkeeping if needed. Do not add a
new schema migration unless a fresh audit proves it essential. Any schema change
forces explicit re-provisioning and must stay test-only.

## 10. Failure injection

The exact matrix is in `FAILURE_MATRIX.md`.

At minimum inject after:

1. identity claim;
2. Player load;
3. identity attachment;
4. headless output attachment;
5. minimal domain initialization;
6. online/session activation;
7. World spawn;
8. action admission;
9. reversible action mutation;
10. store before delete;
11. delete before identity release.

Every injected failure must leave:

- no World object/player;
- DB online flag false;
- no autosave membership;
- no retained lifecycle-owned action;
- no queue entry;
- no headless output attachment;
- no identity lease;
- no party;
- no active requester/trade;
- no instance ownership;
- no fixture item delta;
- no leaked non-daemon thread;
- no unbounded diagnostic state.

Cleanup is called twice for every case.

## 11. Task/future evidence

Because `Player` construction starts vitality work and `Player.load` registers
autosave, tests must measure actual residue.

Allowed techniques:

- test-only reflection over audited private Player future fields;
- test-only reflection over `PlayerAutoSaveTaskManager` membership;
- immutable test diagnostics added to new Phantom lifecycle classes.

Do not expose broad new production debugging APIs merely for tests.

Required measurements:

- autosave membership before/load/after cleanup;
- lifecycle-owned queue/action count;
- live non-daemon thread delta;
- audited Player scheduled-future non-null/done/cancelled state;
- one- and ten-fixture cleanup residue;
- materialize/dematerialize elapsed;
- packet effect/record/drop counts;
- DB statement count where practical without changing Hikari globally.

No claim of “zero tasks” may ignore canonical Player tasks; the gate is zero
**retained residue after cleanup** and no new per-phantom executor.

## 12. Automated targets

Add:

```text
phantom-headless-player-test
phantom-headless-player-performance-smoke
phantom-static-verify-004
```

`phantom-headless-player-test`:

- forked JVM;
- working dir `dist/game`;
- timeout no greater than 240 seconds;
- positive lifecycle, packet effects, collision and failure matrix.

`phantom-headless-player-performance-smoke`:

- one fixture warm-up;
- one fixture measured;
- ten sequential fixtures;
- no 10 concurrent DB load burst;
- bounded timeout;
- report latency, task/autosave/World residue.

Update cumulative `verify` to include these targets and all prior gates.

Historical verifier files remain unchanged. A cumulative Task 004 verifier may
replace execution of frozen historical static verifiers, as in Task 003.

## 13. Expected source areas

Final names may differ for responsibility, but expected new production family:

```text
java/org/l2jmobius/gameserver/phantoms/player/
  HeadlessPlayerOutboundSession.java
  PhantomIdentityLeaseRegistry.java
  PhantomPlayerMaterializationSpike.java
  PhantomActionFacade.java
```

Generic seam may live in a production-neutral package, for example:

```text
java/org/l2jmobius/gameserver/network/PlayerOutboundSession.java
```

Test family:

```text
test/java/org/l2jmobius/tests/phantoms/
  PhantomHeadlessPlayerSuite.java
  PhantomHeadlessPlayerPerformanceSuite.java
  PhantomHeadlessPlayerTestEnvironment.java
  PhantomHeadlessPlayerFixture.java
  packet/effect fixture helpers as narrowly required
```

Documentation:

```text
docs/phantoms/audits/004-headless-player-feasibility-spike/**
docs/phantoms/reports/004-headless-player-feasibility-spike.md
docs/phantoms/reviews/003-disabled-skeleton-config-metrics-review.md
tools/phantoms/verify-task-004.ps1
```

## 14. Approved production touch envelope

Allowed after the mandatory audit:

```text
build.xml

java/org/l2jmobius/gameserver/model/actor/Player.java
java/org/l2jmobius/gameserver/network/GameClient.java
java/org/l2jmobius/gameserver/network/Disconnection.java
java/org/l2jmobius/gameserver/network/clientpackets/CharacterSelect.java

java/org/l2jmobius/gameserver/network/PlayerOutboundSession.java
java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java
java/org/l2jmobius/gameserver/phantoms/player/**

test/java/org/l2jmobius/tests/phantoms/**
tools/phantoms/verify-task-004.ps1

docs/phantoms/tasks/004-headless-player-feasibility-spike/**
docs/phantoms/audits/004-headless-player-feasibility-spike/**
docs/phantoms/reports/003-disabled-skeleton-config-metrics.md
docs/phantoms/reports/004-headless-player-feasibility-spike.md
docs/phantoms/reviews/003-disabled-skeleton-config-metrics-review.md
docs/phantoms/adr/0001-headless-player-integration-seam.md
```

`PhantomSystem.java` may only expose/own the spike service without starting
materialization automatically. Default disabled behavior must remain unchanged.

### Conditional touch

The following are not pre-authorized, but may be added to exact scope only if
the audit proves a minimal one-symbol change is essential and the report
justifies it:

```text
ServerPacket.java
EnterWorld.java
World.java
PlayerAutoSaveTaskManager.java
```

Any conditional touch requires:

- before hash;
- exact symbol;
- why no allowed alternative exists;
- targeted regression;
- no unrelated diff.

More than one conditional production file is a gate stop unless the independent
architecture contract is revised.

## 15. Hard out of scope

Forbidden:

- other chronicles;
- master plan or `Agents.md`;
- Task 003 config defaults becoming true;
- broad GameServer/Shutdown changes;
- fake/null-network `GameClient`;
- fake `Connection`;
- subclass/fork/copy of Player;
- existing Fake Players/NPC changes;
- request packet handlers as Phantom API;
- broad packet-family edits;
- production DB/config/SQL;
- profile/personality persistence;
- Task 005 tables;
- Task 006 final lifecycle;
- scheduler activity levels;
- Utility AI/goals;
- Semantic Pack/game knowledge;
- conversation;
- navigation/geodata;
- combat/movement/trade/mail/party/raid actions;
- LLM;
- per-phantom thread/executor/timer;
- dependencies/JUnit/Maven/Gradle;
- CI;
- manual production server start;
- mass formatting;
- amend/rebase/force push.

## 16. Safety and rollback

Production feature remains disabled by config.

Real-client behavior must pass:

- client-bound adapter delegates unchanged;
- no double effect;
- no changed network write order;
- no real-session identity leak;
- no extra task/thread on disabled Phantom config.

Rollback must be possible by:

1. restoring `Player.sendPacket` client-bound adapter as sole active path;
2. removing Phantom headless attachment/lifecycle classes;
3. removing minimal login identity hooks;
4. leaving test DB disposable;
5. leaving no production migration.

## 17. Required tests

Full matrix: `TEST_CASES.md`.

Critical gates:

### Output/session

- default null-client no-op preserved;
- connected adapter call path statically delegates unchanged;
- headless no GameClient/network reference;
- null fails;
- exact once;
- effect exception propagates;
- actual effect families;
- bounded recursion;
- bounded recording.

### Identity

- phantom vs phantom;
- real reservation vs phantom;
- phantom vs real reservation;
- stale release token;
- load failure release;
- real login hook present;
- real-real existing behavior not broadly changed;
- cleanup release.

### Lifecycle

- create/load;
- inventory/skills;
- World registration;
- observer visibility;
- action add/remove conservation;
- store/delete;
- double cleanup;
- reload;
- failure matrix;
- online/autosave/task residue.

### Performance

- 1 and 10 sequential fixtures;
- bounded time;
- no World/lease/autosave growth;
- no thread growth;
- fixed packet recording.

## 18. Static verifier Task 004

Create `tools/phantoms/verify-task-004.ps1`.

It must check:

- base `1ca74a3d...`;
- branch/one-commit shape;
- exact/conditional scope;
- High Five only;
- no Task 005;
- no production DB/config/SQL;
- Task 003 defaults still false;
- prior lock/manifest/verifiers unchanged;
- `Player.sendPacket` delegates a non-null packet to session seam;
- client-bound adapter delegates to current GameClient;
- headless sink contains no network types and calls `runImpl` once;
- bounded recursion/recording tokens;
- GameClient/CharacterSelect/Disconnection identity hooks;
- no fake Connection/GameClient subclass;
- no Player subclass/fork;
- no client handler family changes;
- materialization service does not call `EnterWorld.runImpl`;
- action facade exposes only one reversible inventory action;
- failure injection enum/steps;
- test target working dir `dist/game`;
- test DB guard/bootstrap reused;
- all Java targets forked;
- Task 003 report closure;
- Task 004 report/audits/ADR evidence;
- UTF-8, mojibake, escaped Cyrillic;
- no credentials/binaries;
- stable ordinal output;
- verifier itself read-only/no DB/network.

## 19. Commands

### Environment and audit

```bat
java -version
ant -version
ant -p
```

Use existing external official Ant 1.10.15 if absent from PATH. Do not commit it.

### Targeted

```bat
ant compile-tests
ant test
ant phantom-skeleton-test
ant phantom-headless-player-test
ant phantom-headless-player-performance-smoke
ant phantom-negative-control
ant phantom-db-guard-negative-control
ant phantom-provisioning-lock-control
ant phantom-schema-freshness-negative-control
ant phantom-lifecycle-negative-control
ant phantom-db-test
ant phantom-scenario-test
ant phantom-performance-smoke
```

If the existing test DB/config/manifest is absent or stale, explicit test DB
re-provision is allowed using environment-only admin credentials. State why.
Production DB must never be connected/read/mutated.

### Full

```bat
ant verify
ant jar
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-004.ps1
git diff --check
git status --short --branch
```

### Post-commit

```bat
git rev-parse HEAD
git show --stat --oneline --decorate HEAD
git diff --check 1ca74a3d96e8fa51612ef3e5145c7398abf60f6d...HEAD
ant verify
ant jar
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-004.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-004.ps1
git push origin feature/phantom-world
git ls-remote --heads origin feature/phantom-world
git status --short --branch
```

Compare final verifier outputs byte-for-byte and SHA-256 outside the repository.

## 20. ADR behavior

Update ADR 0001 with Task 004 implementation evidence, but Codex must not
self-accept the independent gate.

On technical success:

```text
ADR status: Proposed
Task 004 implementation verdict:
FEASIBLE_WITH_SEAM_PENDING_INDEPENDENT_REVIEW
```

On a master-plan reconsideration condition:

```text
ADR status: Proposed
Task 004 implementation verdict:
NOT_FEASIBLE_WITHOUT_PLAN_CHANGE_PENDING_INDEPENDENT_REVIEW
```

Do not set final ADR `Accepted` or `Rejected` until independent review.

## 21. Report

Create:

```text
docs/phantoms/reports/004-headless-player-feasibility-spike.md
```

Required sections:

- Status and starting baseline;
- Task 003 review closure;
- touchpoint audit and hashes;
- architecture verdict;
- production changes;
- outbound/session seam;
- packet-effect evidence;
- online/session policy;
- identity ownership and real-login hook;
- test environment;
- fixture lifecycle;
- explicit materialization steps;
- action facade/conservation;
- observer visibility;
- cleanup/failure matrix;
- task/autosave/World residue;
- one/ten fixture measurements;
- DB/network safety;
- disabled production behavior;
- tests/counts/exit codes;
- Ant verify/jar;
- static verifier;
- scope/conditional touches;
- deviations/limitations/risks;
- branch/parent/subject;
- manual gate `PENDING_INDEPENDENT_REVIEW`;
- ADR still `Proposed`;
- Task 005 `NOT_STARTED`.

For self SHA/push use:

```text
Exact immutable commit SHA, push result and post-commit verifier outputs are
external final-handoff evidence generated after this report is committed.
```

Do not use stale `pending` placeholders.

## 22. Acceptance

Full checklist: `ACCEPTANCE.md`.

A SUCCESS report is not enough. Every critical gate must be GREEN.

## 23. Commit and push

Success subject:

```text
feat(phantoms): prove headless player feasibility
```

Safe blocked/audit subject:

```text
test(phantoms): record headless player feasibility blocker
```

One ordinary commit on top of `1ca74a3d...`.

Forbidden:

- amend;
- rebase;
- reset history;
- force push.

Push regardless of SUCCESS/BLOCKED, with only safe scoped artifacts.

## 24. Blocking behavior

Return `BLOCKED` or
`NOT_FEASIBLE_WITHOUT_PLAN_CHANGE_PENDING_INDEPENDENT_REVIEW` if any of:

- canonical Player requires a real network connection;
- packet effects require broad packet rewrites;
- null-client safety requires broad handlers;
- real-login collision cannot be bounded;
- cleanup leaves World/online/autosave/task/item residue;
- explicit materialization requires most of `EnterWorld`;
- fixture requires production DB;
- per-phantom executor/thread is required;
- `ant verify` or jar is not GREEN;
- scope exceeds bounded touch envelope;
- credentials leak;
- push fails.

When blocked:

1. remove unsafe/uncompilable production changes;
2. clean only exact test fixtures/test DB artifacts;
3. preserve safe audit, tests, verifier and report;
4. do not start Task 005;
5. commit and push the safe result.

## 25. Final Codex handoff

```text
Статус:
Architecture verdict:
Baseline:
Task 003 docs closure:
Production touchpoints:
Outbound/session seam:
Real-client compatibility:
Headless network writes:
Packet effect tests:
Identity collision:
Real-login hook:
Online/session policy:
Fixture create/load:
Materialization:
Inventory/skills:
Observer visibility:
Action conservation:
Failure injection:
Repeated cleanup:
Reload/restart:
Autosave/task/World residue:
One-fixture measurement:
Ten-fixture measurement:
Test DB access/mutation:
Production DB access/mutation:
All prior suites:
ant verify:
ant jar:
Static verifier pre:
Static verifier final 1:
Static verifier final 2:
Outputs identical:
Production JAR test entries:
Commit:
Parent:
Branch:
Push:
Remote ref:
Report:
ADR status:
Manual gate:
Task 005:
Limitations/blockers:
```
