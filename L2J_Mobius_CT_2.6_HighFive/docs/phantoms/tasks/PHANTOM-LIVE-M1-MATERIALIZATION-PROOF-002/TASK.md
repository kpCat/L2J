# PHANTOM-LIVE-M1-MATERIALIZATION-PROOF-002

## Purpose

This is a **fresh Codex continuation chat** for M1 only. The previous task `PHANTOM-LIVE-M1-VISIBLE-WORLD-001` is functionally successful for visible native gameplay but is still BLOCKED on exactly one acceptance item:

`M1_CONNECTED_MATERIALIZATION_ENVELOPE_UNPROVEN`

The task is to close that blocker with a connected proof, not to build another subsystem.

## Repository

- GitHub: `https://github.com/kpCat/L2J`
- Repository: `kpCat/L2J`
- Branch: `feature/phantom-world`
- Git root: `C:\Users\ZBook\L2J_Mobius\`
- Module / Codex cwd: `C:\Users\ZBook\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\`
- Required initial remote HEAD: `1388bd3566701c0faea84a3b129854e04509d686`
- JDK: 25
- Build: Apache Ant

Do not touch other chronicles.

## Mandatory baseline gate

Before any change:

1. Fetch/read `origin/feature/phantom-world`.
2. Require HEAD exactly `1388bd3566701c0faea84a3b129854e04509d686` unless owner explicitly approves a newer verified continuation HEAD.
3. Read the previous M1 result/evidence/root-cause files listed in `HANDOFF.md`.
4. Inspect actual production code. Do not trust prose alone.
5. Preserve unrelated user dirt.

If HEAD differs, stop with `BLOCKED_BASELINE_MOVED` and report the actual SHA/diff relevant to this task.

## Scope

Only the connected materialization envelope proof:

- source-backed native visibility semantics;
- prewarm/materialization before client visibility;
- stability while visible;
- exit grace/hysteresis;
- dematerialization only after safely outside visibility;
- re-entry materialization before visibility;
- a bounded Pilot route that can actually traverse the required envelope.

A tiny Pilot/operator-only diagnostic capability is allowed **only if read-first proves the existing Pilot cannot execute the required route/measurement**. Keep it narrow and reusable. Do not pollute phantom gameplay logic just to make the proof easier.

## Non-goals

Do NOT:

- start M2/chat/party;
- redesign farming/combat;
- re-audit stock AutoPlay/AutoUse;
- change `PhantomVisibleAutoPlay`, `PhantomBackgroundDecision`, `AutoPlayTaskManager`, or `AutoUseTaskManager` unless a new RED regression directly requires it;
- change 10k scale budgets;
- lower acceptance thresholds to obtain GREEN;
- use PLAY DML/DDL/reset/reseed/delete/manual repair;
- repeat the old 10k scale soak;
- ask the owner to manually walk a route, spam commands, `/loc`, screenshots, or repeated relogs.

## PLAY DB rule

Current PLAY DB: `l2jmobiush5_localplay3`.

For PLAY: **read-only SELECT/SHOW only**.

DB-mutating tests are allowed only behind the already-existing guarded test DB gate for `l2jmobiush5_phantom_test`.

## Likely read-first production files

At minimum inspect the exact current versions of:

- `java/org/l2jmobius/gameserver/phantoms/topology/PhantomHumanLocalityControl.java`
- `java/org/l2jmobius/gameserver/localplay/LocalPlayPilotActions.java`
- `java/org/l2jmobius/gameserver/localplay/LocalPlayPilotProtocol.java`
- `java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java`
- the actual High Five `World` / region / visibility / known-list code used to decide what the client can see;
- the actual `GeoEngine` APIs used by `MOVE_SELF` / `TELEPORT_SELF`;
- materialization service/lifecycle code that owns spawn/store/world presence.

Also read the previous task files named in `HANDOFF.md`.

## Required method

### 1. Source-backed visibility contract

Do not assume `2048`, a topology region, or `Player.isVisibleFor()` automatically equals the exact client visibility envelope.

Trace the real High Five path that controls player visibility / known-list / spawn broadcast and document the relevant threshold/region behavior. Reuse the existing production mechanism; do not invent a radius from memory.

### 2. Find a proving lane before asking the user to arm

The previous starter-area route was blocked by geometry. Do **not** brute-force the same wall/corridor again.

Before any USER_CLIENT_ACTION, use server-side/read-only information to choose a traversable outdoor lane that can cover:

- a start point outside the target phantom's prewarm/visibility envelope;
- approach into prewarm;
- crossing into client visibility;
- continued traversal while visible;
- exit beyond visibility/prewarm;
- waiting through the configured grace;
- reverse re-entry.

Prefer an existing open-world area with a natural ordinary phantom and a clean GeoEngine corridor.

If the current Pilot API cannot set up or traverse such a lane, first prove that limitation. Then add the **smallest** Pilot/operator-only helper needed (for example: bounded route probing / validated scenario anchor / waypoint planning). It must remain consent-gated, TestAdmin-only, bounded, and must not grant general unrestricted teleport.

### 3. One fixed ordinary target

Use one ordinary, natural phantom profile for the connected envelope proof. Record its `profileId`, committed position, instance, presence/materialization state and any reason/pin that could keep it present.

Do not use a test-only fake profile. Do not modify scheduler budgets or force the target into a special state merely to pass.

If cap pressure or a legitimate presence reason prevents ordinary dematerialization, pick another natural target; do not disable the reason.

### 4. Connected measurement

Once the route and instrumentation are fully ready, request the minimum owner action.

Target: **1 USER_CLIENT_ACTION** total for this continuation: TestAdmin login/arm once.
Hard max: **2 USER_CLIENT_ACTION** only if a restart or stale lease makes the first technically unusable. A third request means this task is BLOCKED.

Pilot must then drive the route automatically.

At bounded samples (normally 0.5–1.0 s around transitions; slower away from transitions), record at least:

- UTC timestamp;
- TestAdmin XYZ / instance / native world region;
- target profileId and committed XYZ;
- target materialization state / `worldPresent` / objectId when present;
- the source-backed predicate that means the real client could see/know the target;
- target distance when meaningful;
- locality/prewarm signal/state and its age/TTL when available;
- materialization transition timestamps;
- exit-grace start/end timestamps;
- any presence reason/pin that explains continued materialization.

Keep the measurement in a TSV under this task folder.

### 5. Required transition sequence

Prove one continuous ordinary sequence:

A. **OUTSIDE** — target not client-visible and not materialized for HUMAN_NEARBY alone.

B. **PREWARM** — target becomes materialized while the source-backed client-visible predicate is still false. Capture positive margin (distance/region/timestamp), not just an edge-race sample.

C. **VISIBLE** — client-visible predicate becomes true while the target is already materialized. During the visible segment, there must be no materialize/store/materialize churn attributable to locality.

D. **EXIT/GRACE** — after leaving visibility/locality, target remains materialized through the configured grace/hysteresis and does not disappear while still client-visible.

E. **DEMATERIALIZED** — after grace, while safely not client-visible and with no other valid presence reason, target stores/dematerializes.

F. **RE-ENTRY** — on the reverse pass, target materializes again before the client-visible predicate becomes true.

### 6. Human visual check

The owner is not a QA robot. The only manual visual requirement allowed is passive observation while Pilot moves TestAdmin.

Do not ask the owner to travel, type extra commands, or take repeated screenshots. After the automated run, if server evidence is otherwise complete, ask at most one simple observation question: whether an obvious pop-in/flicker was seen during the pass.

If server evidence already proves a defect, do not ask for visual confirmation before fixing it.

## RED / fix rule

This may be a proof-only task. Do not manufacture a code change.

- If the current implementation passes the connected proof, commit only task evidence/docs if no production change was needed.
- If connected evidence exposes a real defect, first capture a precise RED reproducer/measurement, then make the smallest production fix, run focused tests, clean detached build, deploy, and repeat the same bounded connected proof.
- If only Pilot route instrumentation is missing, keep the change in Pilot/operator diagnostics; do not change materialization semantics without RED evidence.

## Build/deploy

If production or Pilot Java code changes:

1. focused tests GREEN;
2. exact-path stage only;
3. `git diff --check`;
4. exact-path commit + normal push;
5. clean detached checkout of the exact committed SHA;
6. `ant jar` there;
7. deploy that exact JAR with backup/manifest;
8. controlled LocalPlay restart;
9. only then request the final TestAdmin arm.

Do not deploy JARs built from a dirty main worktree.

If no code changes are needed and the currently deployed exact final M1 JAR is still verified, do not restart merely for ceremony.

## Git allowlist

Allowed:

- read-only git checks;
- exact-path `git add -- <task-owned paths>`;
- `git commit -m ...`;
- `git push origin feature/phantom-world`.

Forbidden:

- `git add .`
- `git reset`
- `git clean`
- `git stash`
- `git rebase`
- force push

Preserve unrelated user changes.

## Final status vocabulary

Only one of:

- `GREEN — M1 VISIBLE WORLD PROVEN`
- `BLOCKED — M1_CONNECTED_MATERIALIZATION_ENVELOPE_UNPROVEN`
- `BLOCKED_BASELINE_MOVED`
- `BLOCKED_ROUTE`
- `FAILED_REGRESSION`

Do not call M1 GREEN without satisfying every item in `ACCEPTANCE.md`.

M2 must not begin in this task.
