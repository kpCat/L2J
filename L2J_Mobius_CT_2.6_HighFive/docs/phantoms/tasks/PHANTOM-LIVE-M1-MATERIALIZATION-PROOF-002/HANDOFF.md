# HANDOFF — PHANTOM-LIVE-M1-MATERIALIZATION-PROOF-002

## Verified remote checkpoint

At task creation, independent GitHub verification showed:

- branch: `feature/phantom-world`
- remote HEAD: `1388bd3566701c0faea84a3b129854e04509d686`
- commit message: `docs(phantoms): record bounded region crossing without prewarm proof`
- compared with previous diagnostic checkpoint `9f687f997b535d5f678f4aeb147a7926bc9fcab2`: branch is ahead by 4 commits, behind by 0.
- compared with original M1 baseline `7872e18fbf37177cf08ab23f342a25e3742bb1d0`: branch is ahead by 7 commits, behind by 0.

Re-verify this yourself before work.

## Previous M1 task

Read first:

- `docs/phantoms/tasks/PHANTOM-LIVE-M1-VISIBLE-WORLD-001/RESULT.md`
- `docs/phantoms/tasks/PHANTOM-LIVE-M1-VISIBLE-WORLD-001/EVIDENCE.md`
- `docs/phantoms/tasks/PHANTOM-LIVE-M1-VISIBLE-WORLD-001/IDLE_ROOT_CAUSE.md`
- `docs/phantoms/tasks/PHANTOM-LIVE-M1-VISIBLE-WORLD-001/STATE.md`
- `docs/phantoms/tasks/PHANTOM-LIVE-M1-VISIBLE-WORLD-001/MATERIALIZATION_MEASUREMENT.tsv`
- `docs/phantoms/tasks/PHANTOM-LIVE-M1-VISIBLE-WORLD-001/M1_VISIBLE_SCENE.tsv`

Do not re-audit the whole project.

## Accepted facts — do not reopen without a new regression

### Visible native gameplay is connected-proven

The previous task established the original visible-idle gap and fixed it.

On deployed runtime, natural ordinary phantoms passed:

`farm.background ACTIVE → candidate.background.farm → background.visible.autoplay_started SUCCESS`

and performed native gameplay through the existing server AutoPlay/AutoUse core.

Connected runtime evidence included:

- natural profile 2941: native monster target and `visibleAttacking=true`;
- natural profile 1348 on the final JAR: `visibleAutoPlaying=true`, native `CAST` against monster targets, and live position change;
- owner independently observed multiple phantoms attacking Gremlins with starting magic and melee and killing several mobs.

Do not replace the current shared AutoPlay path with a second combat engine.

### Idle root cause is already addressed

The old production path could terminally fail `farm.background` after successful recovery and could leave a living materialized farm goal without a visible candidate. Focused REDs existed and the fix passed focused GREEN.

Do not reopen this unless the current exact HEAD reproduces a new failure.

### Self-heal admission gap is already fixed

`PhantomVisibleAutoPlay.configure()` previously admitted instant SELF skills into AutoUse buffs. The fix now admits only continuous SELF buffs.

Commit:
`561c84a2dc23d6dd953e755e2fafcfbddcd5d395`

Focused regression: 6/6 GREEN.

The specific visually observed old caster's exact skillId/HP was not captured, so do not claim that historical visual event was individually proven.

### Main visible-life code commit

`53047c4f4e57270b8cc4fb65f11f020ebbe263a3`

Do not reimplement it during this proof task.

## Current materialization implementation facts

Current `PhantomHumanLocalityControl` uses:

- `REFRESH_MILLIS = 1000`
- `SIGNAL_TTL_MILLIS = 10000`
- `REGION_SIZE = 1 << World.SHIFT_BY`
- edge probes into adjacent native regions
- `PREWARM_REGION_DISTANCE = 2`
- bounded humans/profiles per refresh.

The previous focused topology contract is GREEN 32/32.

These tests are useful but **do not substitute for connected client-facing proof**.

## Exact remaining blocker

Previous final state:

`BLOCKED — M1_CONNECTED_MATERIALIZATION_ENVELOPE_UNPROVEN`

What is missing:

- connected first prewarm/materialization timestamp while approaching from genuinely outside the native visibility envelope;
- proof target is already materialized before it can be client-visible;
- connected exit and 10-second grace behavior;
- connected dematerialization safely outside visibility;
- connected re-entry materialization before visibility;
- one bounded passive visual check for obvious pop-in/flicker.

## Why the previous run failed to prove it

The Pilot did not fail because visible gameplay was broken. It failed because the chosen TestAdmin route around the starter/Gremlin area was GeoEngine-limited.

The last run crossed one native x-region boundary and continued east, but still could not reach the outer area required to observe a full outside→prewarm→visible→exit→re-entry transition. Further waypoints were rejected by GeoEngine.

Do not waste time brute-forcing that exact corridor again. Pick a better proving lane first.

## Current LocalPlay configuration that must remain bounded

- Population = 10000
- ActiveTarget = 64
- MaxMaterialized = 128
- SchedulerPulse = 100 ms
- PopulationBoundariesPerPulse = 64

Do not auto-tune these to make acceptance easier.

The previous final report recorded LocalPlay healthy and RUNNING with these budgets and global user `EnableAutoPlay` still disabled.

## Pilot/manual QA policy

TestAdmin is a real non-GM client character.

The Pilot exists to avoid manual QA.

For this continuation:

- route discovery/instrumentation happens before owner action;
- target 1 arm/login action;
- hard max 2 only for a stale/restarted lease;
- no manual walking;
- no repeated `.phantomstatus`/`/loc`;
- no screenshot spam;
- no long manual route.

The owner may passively watch the final automated crossing once.
