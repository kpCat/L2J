# EVIDENCE — PHANTOM-LIVE-M1-MATERIALIZATION-PROOF-002

Populate during execution. Do not replace exact measurements with prose.

## Baseline

- expected initial remote HEAD: `1388bd3566701c0faea84a3b129854e04509d686`
- actual initial remote HEAD: `1388bd3566701c0faea84a3b129854e04509d686` after `git fetch origin feature/phantom-world`; local HEAD and upstream matched.
- worktree/scope status: unrelated user dirt was present, including `PhantomMaterializationService.java` and other task/test files. The proof helper leaves that file untouched and stages exact task paths only.

## Source-backed client visibility contract

- files/classes/methods read: `World` region grid/`addVisibleObject`/`switchRegion`/`removeVisibleObject`/`forEachVisibleObject`; `WorldObject.spawnMe`/`setXYZ`/`isVisibleFor`; `WorldRegion.isSurroundingRegion`; `Player.sendInfo`; `GeoEngine.canMoveToTarget`; `PhantomHumanLocalityControl`.
- actual visibility/known-list/spawn rule: `World.SHIFT_BY=11` gives 2048-unit XY regions. World builds each region's 3x3x3 surrounding set. Player spawn/region crossing sends `CharInfo` through `sendInfo` only for objects in surrounding regions, same instance, passing `target.isVisibleFor(observer)`. Leaving that set sends `DeleteObject`. No circular 2048-unit client radius was inferred.
- exact predicate used in runtime measurement: `worldPresent && sameInstance && actorRegion.isSurroundingRegion(targetRegion) && target.isOnline() && target.isVisibleFor(actor)`. `regionCanKnow` is reported separately, using live target region when present and committed region when absent.
- why this predicate represents what the client can see: it mirrors the `World.forEachVisibleObject` region/instance filter and `World.addVisibleObject`/`switchRegion` `sendInfo` condition; `Player.sendInfo` emits `CharInfo`. A client renderer may cull farther objects, so this is the server's could-know/receive-spawn envelope.

## Route discovery before arm

- chosen area: Elven Territory outdoor farming corridor, away from the previous blocked Talking Island wall.
- target profileId: natural cohort at the fixed farm anchor; read-only PLAY `SELECT` found profile `25` among 112 profiles at the anchor. The Pilot selects one READY/AVAILABLE ordinary profile there and fixes that ID for the full connected run. Exact selected ID is pending the final arm.
- target committed XYZ / instance: candidate cohort `(39050,41882,-3592)`, instance 0; exact selected runtime topology point pending.
- route start/end/waypoints: fixed start `(45085,42001,-3496)` in native x-region 22; prewarm point `(44126,42751,-3488)` in region 21; visible traversal through `(42857,42534,-3514)`, `(41588,42316,-3540)`, `(40319,42099,-3566)` to target anchor `(39050,41882,-3592)` in region 19, then reverse exit and re-entry. The target's region 19 is not in the start region 22 surrounding set; region 21 is outside client visibility; region 20 first enters it.
- GeoEngine route proof: `ANCHOR_PROOF.tsv` reports `VALID` exact start Z and local move with `STATIC_XML_CLEAR`; `adaptive-geo-proof.tsv` reports `VALID_DIRECT` for all 42 directed legs/subhops, including return. Subhops are at most 400 XY units for `MOVE_SELF`; start→prewarm is 1218 units for guarded `TELEPORT_SELF`. Native 203-region geodata was loaded. `population.farming.elf.20534` contains the prewarm point and has a TARGETABILITY edge to `generated.farm.7451e61fe8e94f49afb1f9e2`, which contains the target anchor.
- Pilot helper required? yes.
- if yes, RED limitation and exact helper scope: existing `TELEPORT_SELF` allows origin, current candidate, or a GeoEngine-reachable point within 2000; TestAdmin's previous Talking Island position cannot reach the chosen lane. Existing `SNAPSHOT_PHANTOMS` selects only the nearest candidate, so it cannot track one fixed profile through dematerialization. Added two consent-gated, TestAdmin-only Pilot operations: one teleports solely to the fixed, normalized, geo-proven start after selecting a READY/AVAILABLE natural target at the exact cohort; the other reads only that target's materialization/locality/native region state. No general destination parameter or phantom gameplay change.

First connected preflight on commit `8609ca630df` rejected `PREPARE_M1_ENVELOPE:NO_ORDINARY_TARGET_AT_PROOF_ANCHOR` before any movement or TSV. Read-first showed its selector required global ACTIVE admission (64 slots over 10000 profiles), although locality itself submits `NEARBY_PERCEPTIBLE` to any non-OFFLINE profile and that state requires materialization. Read-only PLAY `SELECT` confirmed 112 natural profiles at the exact farm anchor; `SHOW COLUMNS` and `SELECT` only, no PLAY mutation. The operator-only selector was narrowed to READY, AVAILABLE, resolved, same-instance profiles within 64 units of the fixed anchor. This removes an unrelated prerequisite without changing budgets or phantom scheduling/materialization behavior. Focused RED was missing `nearestReadyWithin` at compile; focused GREEN: operator observability 9/9, Pilot contract 6/6, guarded TEST DB native 4/4. A second exact-SHA build/restart and at most one further TestAdmin arm are required; no third arm will be requested.

Before the final arm, the same bounded selector gained an `afterProfileId` fallback inside this exact anchor cohort. It lets the automated Pilot retry the same proven lane with another READY/AVAILABLE natural target if the first target has a valid presence reason or cap pressure, without another restart or owner action. No target is forced active. Focused suites rerun after this final helper refinement: operator observability 9/9, Pilot contract 6/6, guarded TEST DB native 4/4.

## USER_CLIENT_ACTION count

1. TestAdmin login/arm on Game PID 23968. First Pilot preflight rejected before movement: no globally admitted target at the fixed cohort. Pilot run stopped; actor stayed at origin.
2. Final TestAdmin login/arm on Game PID 20952. Two automated measurements used the same lease. No third action was requested.

## Runtime measurement artifact

`MATERIALIZATION_ATTEMPT_1.tsv`: 19 samples for natural profile 59, saved before the same-lease repeat. `MATERIALIZATION_CONNECTED_PROOF.tsv`: 48 samples for natural profile 450, final extended observation. Both contain UTC, TestAdmin XYZ/instance/native regions, fixed target committed XYZ/native regions, world presence/objectId, native could-know predicate, distance, locality, materialization state/age, scheduler states/signals and presence reason. Neither is a completed A–F connected sequence.

## Required transition evidence

### OUTSIDE

- timestamp: profile 59 at `2026-09-27T20:05:42Z`; profile 450 at `2026-09-27T20:08:02Z`.
- target worldPresent: false for both; committed `(39050,41882,-3592)`, instance 0.
- clientVisible: false; TestAdmin at `(45085,42001)`, native x-region 22 versus target x-region 19.

### PREWARM / first materialization

- first materialization timestamp: **not observed**. Profile 59 remained `STORED` from `20:05:44.940Z` through `20:06:07.344Z`; profile 450 remained `STORED` from `20:08:04.939Z` through `20:09:11.940Z`.
- clientVisible during every prewarm sample: false. `regionCanKnow=false`, actor native region `(21,20,6)`, target `(19,20,6)`.
- positive prewarm geometry margin: actor x=44126, first client-visible native region begins at x=43008; 1118 units of X separation and target distance2D=5150. The region gap is real, but materialization never occurred inside it.
- `localityCurrent=true`, `activeSignalSources=2`, `presenceReason=none` for both. Last profile 59 snapshot was `activityState=BACKGROUND/requestedState=BACKGROUND`; last profile 450 snapshot `SLEEPING/SLEEPING`. The 67-second second observation rules out a mere 15-second probe window. The exact scheduler bottleneck is not yet measured; source shows signal acceptance enqueues slots while pulse processing is bounded by a 50 ms wall budget, so queue delay is a plausible mechanism, not a proven root cause.

### First VISIBLE

- not reached: prewarm materialization failed, so first visible transition and churn cannot be assessed.

### EXIT / grace

- not reached; no first materialization to carry through exit/grace.

### DEMATERIALIZED

- not reached as an after-grace transition. Both selected targets were already `STORED` during prewarm and reported `presenceReason=none`.

### RE-ENTRY

- not reached; no initial materialization/visible pass.

## Passive client observation

- obvious pop-in seen? not-observed; no passive question asked because server evidence already failed.
- obvious flicker seen? not-observed.
- note: owner was asked only for the two allowed arm actions, not for manual travel or screenshots.

## Regression/tests/build/deploy

- focused tests: `ant phantom-localplay-pilot-native-test` RED 3/4 (`PREPARE_M1_ENVELOPE` absent), then final `ant phantom-localplay-pilot-test` GREEN contract 6/6 and guarded TEST DB native 4/4 after fixed-anchor refinement. Initial unprivileged compile hit sandbox `AccessDeniedException` on a repository JAR; the required rerun with normal filesystem access compiled and passed.
- mojibake-маркеры в изменённых файлах проверены: совпадений нет.
- escaped Cyrillic в изменённых файлах проверены: совпадений нет.
- diff check: `git diff --cached --check` exit 0 before the exact-path code/task commit.
- code commit: `8609ca630df29d27dfe3b8b0c58b50ca20ed9c11`, normal push to `origin/feature/phantom-world`; no unrelated paths staged.
- clean detached JAR SHA-256: `5B99BDE587E7E398DC6CFA6EE5D242006DB6071EEE58B04BA88C483DC62F4E4A`; clean managed detached checkout at that exact SHA, `ant jar` BUILD SUCCESSFUL (2291 sources), clean `git status --porcelain=v1` before build.
- deployment: old JAR SHA-256 `C868989EB12C656861D4D85BABEC43BB136C1646041CFC753C5CA968A18C9467` and manifest saved under `artifacts/local-play/m1-materialization-backup-20260927`; controlled `Stop-LocalPlay.ps1`, copied exact new JAR to private runtime, updated manifest hash atomically, and `Start-LocalPlay.ps1 -Background` completed.
- final LocalPlay health before connected run: `CONFIG PASS`; LoginServer PID 15716 and GameServer PID 23968 `RUNNING`, owned ports 2106/9014/7777; population/active/materialized cap/pulse = `10000/64/128/100`, diagnostics True. Old Pilot lease stale after restart. New single arm requested only after route/build/deploy were ready.
- final revised code commits: `ad4c2394e18` (READY target selection) and `686761a73f1abe9a0d06821d98e18009f0ccad6c` (bounded same-lane fallback), both pushed normally. Exact staged scope and `git diff --cached --check` verified before each commit.
- final revised clean detached `ant jar`: `BUILD SUCCESSFUL`, 2291 sources, commit `686761a73f1abe9a0d06821d98e18009f0ccad6c`, clean status before build, SHA-256 `AFCC66D70E36AF93DED1AAC5DAE04BECF48C85E60F90C0815DB427062ADE3AC4`.
- final revised deployment: previous `5B99BDE5...2F4E4A` JAR and manifest saved under `artifacts/local-play/m1-materialization-final-backup-20260927`; controlled stop, exact JAR copy/hash check, atomic manifest hash update, background start. `CONFIG PASS`; Login PID 24592, Game PID 20952, owned ports 2106/9014/7777; `10000/64/128/100`, diagnostics True. Previous lease stale; second/final arm prepared only after health verification.
- final Pilot/actor state: the runner's claimed origin restoration failed because `LocalPlayPilotActions` stores `_origin = actor.getLocation()`, while `WorldObject.getLocation()` returns its mutable `_location`; the same object changes during the route. Exact former origin teleports were rejected by the guard. A guarded, GeoEngine-proven local teleport moved TestAdmin to the outside endpoint `(45085,42001,-3491)`, instance 0. `STATUS` confirmed REAL_LOGIN, online/worldPresent, stationary, not teleporting. Pilot `Stop-LocalPlayPilot.ps1` returned STOPPED; `Get-LocalPlayPilot.ps1` showed `ARMED_IDLE/runActive=false`. TestAdmin is not back at the former Talking Island origin. This separate Pilot defect remains unfixed because changing/deploying it would require a third arm for verification, which the task forbids.
- final LocalPlay health: `CONFIG PASS`; Login PID 24592/Game PID 20952 RUNNING, owned ports, unchanged `10000/64/128/100` and diagnostics True.

## Final status

- status: `BLOCKED — M1_CONNECTED_MATERIALIZATION_ENVELOPE_UNPROVEN`.
- exact blocker: source-backed prewarm locality is true for two natural READY/AVAILABLE profiles, but neither materializes before visibility even after 67 seconds at the prewarm point; hence visible stability, exit/grace, dematerialization and re-entry are unproven. Additional Pilot origin alias defect prevented full actor restoration.
- final remote HEAD before evidence-only report commit: `686761a73f1abe9a0d06821d98e18009f0ccad6c`.
