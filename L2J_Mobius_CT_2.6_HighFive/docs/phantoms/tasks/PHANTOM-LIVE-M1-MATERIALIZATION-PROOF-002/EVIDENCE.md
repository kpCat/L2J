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

## USER_CLIENT_ACTION count

1.
2. (only if technically required)

## Runtime measurement artifact

Create/update:

`MATERIALIZATION_CONNECTED_PROOF.tsv`

Recommended columns:

`utc\tphase\thumanX\thumanY\thumanZ\thumanRegionX\thumanRegionY\tprofileId\tphantomCommittedX\tphantomCommittedY\tphantomCommittedZ\tobjectId\tworldPresent\tclientVisible\tdistance\tlocalityState\tmaterializationState\tpresenceReason\ttransition`

## Required transition evidence

### OUTSIDE

- timestamp:
- target worldPresent:
- clientVisible:

### PREWARM / first materialization

- first materialization timestamp:
- clientVisible at that moment:
- positive margin:

### First VISIBLE

- timestamp:
- already worldPresent since:
- no churn interval:

### EXIT / grace

- last visible timestamp:
- locality exit timestamp:
- grace deadline:
- worldPresent during grace samples:

### DEMATERIALIZED

- timestamp:
- clientVisible:
- presence reason/pin:

### RE-ENTRY

- rematerialization timestamp:
- clientVisible at rematerialization:
- first visible timestamp after re-entry:

## Passive client observation

- obvious pop-in seen? yes/no/not-observed
- obvious flicker seen? yes/no/not-observed
- note:

## Regression/tests/build/deploy

- focused tests: `ant phantom-localplay-pilot-native-test` RED 3/4 (`PREPARE_M1_ENVELOPE` absent), then final `ant phantom-localplay-pilot-test` GREEN contract 6/6 and guarded TEST DB native 4/4 after fixed-anchor refinement. Initial unprivileged compile hit sandbox `AccessDeniedException` on a repository JAR; the required rerun with normal filesystem access compiled and passed.
- mojibake-маркеры в изменённых файлах проверены: совпадений нет.
- escaped Cyrillic в изменённых файлах проверены: совпадений нет.
- diff check: `git diff --cached --check` exit 0 before the exact-path code/task commit.
- code commit: `8609ca630df29d27dfe3b8b0c58b50ca20ed9c11`, normal push to `origin/feature/phantom-world`; no unrelated paths staged.
- clean detached JAR SHA-256: `5B99BDE587E7E398DC6CFA6EE5D242006DB6071EEE58B04BA88C483DC62F4E4A`; clean managed detached checkout at that exact SHA, `ant jar` BUILD SUCCESSFUL (2291 sources), clean `git status --porcelain=v1` before build.
- deployment: old JAR SHA-256 `C868989EB12C656861D4D85BABEC43BB136C1646041CFC753C5CA968A18C9467` and manifest saved under `artifacts/local-play/m1-materialization-backup-20260927`; controlled `Stop-LocalPlay.ps1`, copied exact new JAR to private runtime, updated manifest hash atomically, and `Start-LocalPlay.ps1 -Background` completed.
- final LocalPlay health before connected run: `CONFIG PASS`; LoginServer PID 15716 and GameServer PID 23968 `RUNNING`, owned ports 2106/9014/7777; population/active/materialized cap/pulse = `10000/64/128/100`, diagnostics True. Old Pilot lease stale after restart. New single arm requested only after route/build/deploy were ready.

## Final status

- status:
- exact blocker if not GREEN:
- final remote HEAD:
