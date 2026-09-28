# PLAN

## Phase A — read-first / architecture

Read the actual current HEAD before editing. At minimum:

- `java/org/l2jmobius/gameserver/phantoms/PhantomScheduler.java`
- `java/org/l2jmobius/gameserver/phantoms/activity/PhantomActivityMaterializationPort.java`
- `java/org/l2jmobius/gameserver/phantoms/activity/PhantomActivityTransitionStatus.java`
- `java/org/l2jmobius/gameserver/phantoms/activity/PhantomSchedulerPolicy.java`
- `java/org/l2jmobius/gameserver/phantoms/activity/PhantomReconcileFirstActivityPort.java`
- `java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java`
- `java/org/l2jmobius/gameserver/phantoms/topology/PhantomHumanLocalityControl.java`
- `java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializationService.java`
- `java/org/l2jmobius/gameserver/phantoms/background/PhantomVisibleFarmTravel.java`
- `java/org/l2jmobius/gameserver/phantoms/background/PhantomVisibleAutoPlay.java`
- `java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundDecision.java`
- `java/org/l2jmobius/gameserver/phantoms/background/PhantomHistoricalBackgroundPlanner.java`
- `java/org/l2jmobius/gameserver/phantoms/background/PhantomHistoricalBackgroundService.java`
- `java/org/l2jmobius/gameserver/phantoms/navigation/PhantomNavigationService.java`
- `java/org/l2jmobius/gameserver/phantoms/navigation/L2jNavigationBackend.java`
- `java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java`
- `java/org/l2jmobius/gameserver/phantoms/party/L2jPhantomPartyBackend.java`
- M1-003 `RESULT.md`, final section of `EVIDENCE.md`, and the two connected TSVs.

Write a short root-cause summary in this task's `EVIDENCE.md` before production changes. Maximum ~50 lines for the whole read-first summary.

## Phase B — implement generic fixes

Implement the semantic deferred transition, smart retention, adaptive travel failure/replan, and bounded no-target fallback described in DESIGN.md.

Do not add per-location production coordinates or per-level/race special cases.

## Phase C — focused automated checks

Add only focused regressions needed for changed semantics:

1. fresh human-local signal preempts prior policy defer and does not inherit exponential backoff;
2. genuine transient transition failure still uses bounded exponential backoff;
3. native-visible and REAL-party hard retention prevent ordinary dematerialization;
4. recent-human soft hold survives short leave/re-entry, expires around the configured window, and is reclaimable under cap pressure;
5. terminal navigation failure releases travel presence and leads to a different reachable farm candidate/replan rather than the same infinite retry;
6. no-target-at-arrival cannot remain unexplained IDLE indefinitely.

If planner selection changes, run the existing broad 1..85 data gate **once at final automated validation**, not after every edit.

Do not rerun the 10k scale suite. Do not rerun death suites unless touched or compilation dependencies require them.

## Phase D — one final build/deploy

After all focused checks are green:

- exact-path scope/diff check;
- one final mojibake + escaped-Cyrillic scan;
- exact-path commit/push;
- clean detached exact-SHA `ant jar`;
- controlled LocalPlay deployment;
- health check preserving `10000/64/128/100`.

Do not perform repeated clean builds during normal iteration unless compilation cannot otherwise be validated.

## Phase E — one representative connected acceptance

Ask for TestAdmin arm only after code, focused tests, clean build and deployment are final.

Expected USER_CLIENT_ACTION: 1. Hard max: 2 only if the first connected session reveals a genuine production defect that requires restart. No third action.

The Pilot owns travel and observations. User does not manually run across locations.

One scene is enough. Do not test races/level bands/regions one by one.
