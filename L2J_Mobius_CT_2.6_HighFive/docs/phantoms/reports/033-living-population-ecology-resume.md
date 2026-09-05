# Goal033 — Living population ecology — repeat report

## Status

`SUCCESS`

Goal033 historical blocker is closed by accepted Goal033A1 and Goal033A. The repeat reuses that causal Background path and adds a durable independent population ecology: FRESH/LIVING/MATURE virtual-age presets, schedule-aware productive windows, CASUAL/REGULAR/FAST/OUTLIER pace without reward multipliers, stable newcomer/age/exposure cohorts, safe MANAGED-to-ARCHIVED turnover, immutable Social-trait personalities and bounded operator tuning/status.

The guarded production-composed LIVING 10/5 gate passed 2/2 on `127.0.0.1:3308/l2jmobiush5_phantom_test`, including restart and cleanup. Goal032 reset/reseed, Goal031 readiness and Goal030 CP3 recovery/rollback DB regressions also passed on that same dedicated test schema. Production `l2jmobiush5` was not connected to, probed, modified or cleaned.

## Exact baseline and branch

- Module: `L2J_Mobius_CT_2.6_HighFive`.
- Branch: `feature/phantom-world`.
- Required and verified parent: `77d72eaff6858c6bc33741b380005d3e0fda7f81`.
- Parent subject: `phantom(goal-033a): complete causal historical background catchup`.
- Remote: `https://github.com/kpCat/L2J`.
- Start divergence: absent.
- Unrelated untracked task packages and launcher files were observed and excluded from the exact-path commit.

## Read-first audit

Read before changes:

- `Agents.md` and root `README.md`;
- `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`, roadmap, current status, operator tuning, quick-start and handoff;
- authoritative repeat `TASK.md`, `CONTEXT.md`, `PRESETS.md` and `TEST_MATRIX.md`;
- historical Goal033 blocker report, historical Goal033A blocker report, Goal033A1 SUCCESS report and Goal033A SUCCESS resume report;
- shipped and local-play Phantom config;
- population, topology, Background, Decision, Scheduler, Progression, Social, materialization and Goal032 ownership owners, codecs, catalogs, build targets and test doubles;
- versioned High Five population and accepted Goal033A/Goal033A1 topology data.

A module-local README, code-map or separate pattern file was not found. The root README and domain documents above were used; absent files were not searched repeatedly.

### Eight required audit answers

1. Weekly schedules already live in versioned population XML, are assigned in durable `population.state`, and are evaluated by `PhantomPopulationManager`; existing submit/withdraw signals remain admission owners.
2. The bounded shared cadence is `PhantomPopulationManager.controlPulse`; ecology adds no timer, executor or per-profile thread.
3. Ecology scans canonical schedule state in 15-minute blocks, selects only `ACTIVE`/`BACKGROUND` availability through durable pace policy, and submits productive windows through accepted Goal033A.
4. Initial and ongoing offline aging share one calendar reconciliation path. Materialized time advances the cursor without Background rewards; missed eligible offline blocks use Goal033A.
5. Retirement is durable `population.ecology.disposition=ARCHIVED` plus the existing population state transition. Archived profiles are excluded from managed target/return; Goal032 stays the only delete/reset owner.
6. Ecology is attached after a real population shell is durably created. The Social initializer uses its frozen traits only when `social.state` is absent; existing Social state never rerolls.
7. Profession policy remains `CANONICAL_QUEST_REQUIRED`. Goal033 neither sets class directly nor synthesizes quest completion; the fuller class-transfer path remains Goal036.
8. Default LIVING bounds are 21 virtual days, 15-minute blocks, four profiles and 16 intervals per pulse, at most 14 calendar days per reconciliation slice, and archive cap 1000. The measured LIVING 10/5 run converged in 118441 ms with final pending count zero.

## Reused architecture

- `PhantomPopulationManager` remains the sole create/target/return/retire owner and supplies the existing pulse.
- Profile component CAS persistence stores restart-safe `population.ecology`.
- `PhantomHistoricalBackgroundService` remains the sole historical progression engine.
- Scheduler and Decision retain normal runtime admission and goal ownership.
- `PhantomSocialService.createState` remains the new-state-only trait seam.
- Goal032 remains the only destructive Phantom ownership workflow.
- Strict XML catalogs, deterministic binary codecs, immutable state records, bounded pulses and typed fail-closed outcomes follow existing local patterns.

The task crosses runtime, one versioned data catalog, config, focused tests and mandatory documentation. This is the bounded multi-artifact exception required by Goal033, not a new parallel lifecycle or progression subsystem.

## Durable ecology and anti-rubber-band contract

Schema-1 component `population.ecology` stores preset/catalog identity, assignment generation/ordinal, virtual join and cursor times, pace/productive share/block offset, personality/schedule/frozen Social traits, MANAGED/ARCHIVED disposition and turnover/replacement/retry state. Immutable fields are assigned once with CAS; only cursor and lifecycle fields advance. The deterministic `PEC1` fixture is 216 bytes.

The ecology API accepts Phantom identity, generation/ordinal, catalog, schedule and clocks; it has no human player or human-level input. Human fixtures at levels 1, 20, 40, 60 and 85 produced identical age, pace, personality, schedule, turnover and productive-window decisions. Static fences reject direct XP, `setLevel`, reward multipliers, free resources and direct teleport in the ecology path.

## Presets and pace

Catalog: `dist/game/data/phantoms/population/high-five-ecology-v1.xml`.

Catalog SHA-256: `a723e1f2caa8016ee1fa13024257ae5873f2e86142ab90859ed85a9395cffd84`.

| Preset | Default age | Age buckets, days / weight | Pace CASUAL/REGULAR/FAST/OUTLIER | Productive shares | Newcomer window |
|---|---:|---|---|---|---:|
| FRESH | 7 days | 0-1/50, 2-3/35, 4-7/15 | 50/40/9/1 | 20%/40%/65%/90% | 2 days |
| LIVING | 21 days | 0-2/20, 3-7/30, 8-14/30, 15-21/20 | 30/45/20/5 | 25%/50%/75%/95% | 2 days |
| MATURE | 60 days | 0-3/12, 4-14/18, 15-35/35, 36-60/35 | 25/40/27/8 | 25%/50%/75%/95% | 3 days |

Personality weights in catalog order BALANCED/SOCIAL/COMPETITIVE/CAUTIOUS/GRINDER/TRADER are FRESH 30/20/15/20/10/5, LIVING 25/20/15/15/15/10 and MATURE 20/15/20/15/20/10. All presets use morning/evening/late schedule weights 30/50/20. Weighted dimensions use deterministic 100-slot low-discrepancy permutations.

Pace changes only how many eligible schedule blocks become productive. Per-action EXP, drop, resource cost and canonical encounter outcome are unchanged. A productive block becomes a Goal033A request; idle/sleeping blocks only advance calendar time.

## Goal033A reconciliation and exactly-once replay

The existing Goal033A entrypoint was extended to renew completed requests sequentially, reuse a valid `farm.background` plan, rebuild stale plans from exact durable state and commit cursor/goal/catch-up changes under expected row versions. Materialized time never receives Background rewards.

A final regression found and fixed a committed TRAVEL replay edge: after the first commit reached the farm anchor, a duplicate action key could enter the FARM branch and return REPLAN. `PhantomBackgroundTransaction.verifyCommittedHistoricalReplay` now locks and verifies canonical state, goal, catch-up and receipt digest; `PhantomBackgroundService` reconciles known historical action digests before branch selection. The Goal033A suite now proves the one-edge travel replay deterministically against factual topology.

Startup rollback and normal/failed-recovery shutdown ordering keep Progression and GameKnowledge alive until materialization/background recapture finishes. Test-only headless startup can defer the unrelated SevenSignsFestival timer; production scheduling was not changed.

## Turnover and personality

At a safe boundary an eligible managed veteran receives a durable archive request. PopulationManager withdraws Scheduler ownership, unregisters Decision runtime, detaches materialization, then atomically commits ecology ARCHIVED and population retired state. Materialized, leased, party/economy or critical-goal profiles fail closed. Cancellation restores managed state.

Archived identity, character and history remain durable, do not count toward managed target, and cannot refill their own slot. Replacement uses the existing PopulationManager and begins as a real level-1 shell with a new ecology assignment. Archive cap pauses churn; no automatic delete exists.

The ecology catalog maps six archetypes to existing Social traits and freezes the exact six values in the assignment. They seed only a missing Social state, so restart or later preset/catalog changes cannot reroll an existing personality.

## Operator/config contract

Validated keys are `EnablePhantomEcology`, `PhantomEcologyPreset`, `PhantomEcologyWorldAgeDays` and `PhantomEcologyArchiveLimit`. Shipped values stay fail-closed: system disabled, targets 0/0 and ecology disabled. The local-play example enables LIVING 10/5 with archive limit 1000.

`//phantom status` exposes catalog/preset hash, managed/archived/pending counts, pause reason, pace/personality/schedule distributions, read-only live level histogram, per-pulse work, causal minutes and failures. Level histogram reads managed object IDs in pages of at most 256 and reports `UNAVAILABLE` rather than guessing.

## Measurements

Deterministic 128-profile fixture, appropriate to the existing Goal029 bounded population envelope:

| Preset | Median virtual age, min | Median productive min | Total productive min | Newcomers |
|---|---:|---:|---:|---:|
| FRESH | 3068 | 165 | 33285 | 62 |
| LIVING | 12686 | 960 | 172860 | 12 |
| MATURE | 38031 | 2955 | 540900 | 13 |

This proves stable newcomer/low/mid/advanced virtual-age and productive-exposure cohorts, with FRESH younger than LIVING and LIVING younger than MATURE. It is deterministic ecology-capacity evidence, not a substitute for canonical live level results.

Production-composed LIVING 10/5 cold reseed/restart evidence:

- Ant wall time: 3 min 25 sec; causal convergence: 118441 ms; reseed control: 5396 ms.
- Productive historical minutes: 13362; Goal033A intervals: 13362.
- Maximum pulse work: 4 profiles / 16 intervals.
- Pending catch-up: maximum 8, partial-ready state observed, final 0.
- Recoverable conflicts: 6; final catch-up failures: 0; last retry reason `catchup.goal.conflict`.
- Restart: 10 profiles, 10 ecology components, assignments stable, pending 0.
- Runtime owners: Scheduler RUNNING with 10 registrations, Decision RUNNING, Background operational, Social RUNNING.
- Live level histogram after convergence/restart: `{01-19=10}`.
- Cleanup and Hikari/ThreadPool shutdown: clean.

The live 10-profile sample remains in 01-19 because ecology obeys current canonical High Five costs and profession constraints; it does not fake level targets. Goal033 accepts advanced cohorts where current progression/profession constraints allow. Full class-transfer realism and a broad endgame profession/level mix remain Goal036.

## Changed artifact families

Runtime/data/config:

- new `PhantomPopulationEcologyState`, codec, catalog, store and service;
- PopulationManager, population persistence/ownership/store integration;
- existing Background catch-up store/service/transaction and historical planner/service integration;
- Social initializer, PhantomSystem lifecycle, config parser and admin status;
- `high-five-ecology-v1.xml`, shipped safe config and local-play example.

Tests/build:

- focused DB-free and production-composed Goal033 suites;
- deterministic Goal033A replay regression;
- headless environment, population test doubles, Goal030A/Goal031/Goal032 compatibility and launcher registration;
- `build.xml` targets.

Documentation:

- master plan, roadmap, current status, operator tuning, quick-start, handoff and this resume report.

The canonical guard/config already targeted `l2jmobiush5_phantom_test`; temporary changes made only for the erroneous task literal `l2jmobius_phantom_test` were removed. No guard migration artifact is part of this diff. Historical reports and unrelated task packages remain unchanged.

## Automated evidence

Passed before final packaging:

- `ant compile-tests` — 2230 production and 135 test sources; only two pre-existing `System.runFinalization()` removal warnings.
- Goal033 DB-free focused suite — 7/7.
- Goal033 production-composed LIVING — 2/2.
- Goal033A focused — 4/4; Background transaction — 7/7; Background quiescence — 2/2.
- Goal033A1 and affected Decision/Navigation/Topology/Topology-perception/Scheduler/Background/Population/Social suites — PASS.
- Goal032 ownership — 3/3; reset/reseed — 2/2.
- Goal031 preflight — 8/8; readiness — 3/3.
- Goal030 CP3 restart recovery — 3/3; release rollback — 3/3.
- `ant test` — 66/66.
- `ant phantom-db-test` — 9/9 on `l2jmobiush5_phantom_test`, with cleanup.
- DB guard negative control — production target rejected before driver load/connection attempt, 0/0.
- Documentation validators — PASS.
- Final `ant jar` — PASS exactly once after all focused gates; LoginServer, GameServer and DatabaseInstaller jars built successfully.

## Database safety

The explicit user correction overrides the stale DB names in TASK.md:

- authorized test DB: `127.0.0.1:3308/l2jmobiush5_phantom_test`;
- dedicated test user: `l2j_phantom_test`;
- forbidden production DB: `l2jmobiush5`.

All DB-mutating suites asserted the guarded catalog before test-only coordinate setup, created only bounded Phantom fixtures and cleaned them. Production `l2jmobiush5` was not used, probed, mutated or cleaned. The guard negative control rejected production before loading the driver or attempting a connection.

## Known limitations

- The 10-profile live level histogram is all 01-19 under current canonical progression/profession boundaries; no hardcoded target or direct advancement was added to manufacture an advanced level sample.
- Full class-transfer, class-quest automation and endgame profession realism remain Goal036.
- Goal034 black-box LoginServer/GameServer process acceptance is the next separate goal and was not started.

## Git and release action

Task-authorized bounded Git inspection was used for baseline, branch, remote, scope and exact diff verification. Exact-path staging will include only the artifacts above. No reset, restore, rebase, merge, force push or history rewrite is used. SUCCESS subject:

`phantom(goal-033): implement living population ecology`

Push is non-force to `https://github.com/kpCat/L2J`, branch `feature/phantom-world`.

## Outcome block

```text
Human-level rubber band: ABSENT
Durable living age/exposure ecology: YES
Continuous newcomer floor: YES
Causal historical catch-up through Goal033A: YES
Personality archetype mix: YES
Safe MANAGED->ARCHIVED turnover: YES
Goal032 reset/reseed compatible: YES
Production DB used by tests: NO
Next Goal: 034 — Automated black-box local stack acceptance (not started)
```
