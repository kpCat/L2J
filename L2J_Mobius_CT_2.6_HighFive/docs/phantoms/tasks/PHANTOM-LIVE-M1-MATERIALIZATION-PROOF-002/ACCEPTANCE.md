# ACCEPTANCE — PHANTOM-LIVE-M1-MATERIALIZATION-PROOF-002

M1 can become GREEN only if all required items below are supported by exact runtime evidence from the final committed/deployed state.

## A. Baseline and scope

- [ ] Initial remote HEAD independently verified.
- [ ] Work stays on `feature/phantom-world` and High Five only.
- [ ] Previous visible-life/AutoPlay fix is treated as accepted unless a new RED appears.
- [ ] No M2/chat/party work is started.
- [ ] PLAY DB remains SELECT/SHOW only.
- [ ] Budgets remain 10000/64/128/100.

## B. Source-backed client visibility

- [ ] Actual High Five visibility / known-list / spawn path is traced from source.
- [ ] Evidence states the exact predicate/threshold/region behavior used to say `client-visible=true/false`.
- [ ] No guessed radius is used.

## C. Proving route

- [ ] A traversable route is selected before final arm.
- [ ] Route can cover outside → prewarm → visible → exit → post-grace → re-entry.
- [ ] If a Pilot helper was required, its need was first proven and its scope is consent-gated/operator-only/bounded.

## D. Connected prewarm

For one natural ordinary phantom profile:

- [ ] Initial OUTSIDE state is captured.
- [ ] First materialization timestamp is captured.
- [ ] At first materialization, the source-backed client-visible predicate is still false.
- [ ] A positive prewarm margin is recorded (not only an ambiguous boundary sample).
- [ ] When client-visible first becomes true, the same phantom is already world-present.

## E. Visible stability

- [ ] During the connected visible segment there is no locality-caused store/materialize churn.
- [ ] The phantom does not disappear while the source-backed client-visible predicate is true.
- [ ] No obvious pop-in/flicker is reported in the one passive client observation.

## F. Exit grace and dematerialization

For the same ordinary target, with no other valid presence reason:

- [ ] Exit from client visibility/locality is captured.
- [ ] Target remains materialized through the configured grace/hysteresis window.
- [ ] Target does not dematerialize while still client-visible.
- [ ] After grace and while safely outside visibility, target dematerializes/stores.
- [ ] Exact transition timestamps are recorded.

If another valid presence reason/pin keeps the target alive, this target does not satisfy F; choose another ordinary target rather than disabling the reason.

## G. Re-entry

- [ ] Reverse approach is captured.
- [ ] Target rematerializes before the source-backed client-visible predicate turns true.
- [ ] Target becomes visible without a visible spawn/flicker event during the passive client check.

## H. Regression/build/deploy

If code changed:

- [ ] Precise RED exists before materialization semantics are changed.
- [ ] Focused tests GREEN.
- [ ] `git diff --check` clean.
- [ ] Exact-path task-owned commit/push only.
- [ ] Clean detached exact-SHA `ant jar` succeeds.
- [ ] Exact JAR is deployed after backup.
- [ ] LocalPlay final health is PASS/RUNNING.

If no code changed:

- [ ] Current deployed JAR is verified to correspond to the intended committed state; no ceremonial restart is performed.

## I. Manual QA bound

- [ ] Target 1 USER_CLIENT_ACTION used.
- [ ] No more than 2 USER_CLIENT_ACTION used.
- [ ] Owner was not asked to manually walk/travel/repeatedly relog/spam commands.

## Final gate

Only if B–G are all proven:

`GREEN — M1 VISIBLE WORLD PROVEN`

Otherwise retain a precise blocker. Do not start M2 inside this task.
