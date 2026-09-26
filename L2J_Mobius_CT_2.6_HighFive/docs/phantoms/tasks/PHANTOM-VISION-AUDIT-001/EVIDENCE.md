# ACCEPTED BASELINE EVIDENCE

Current branch baseline:
`77672c071c1c69669dec6527e7f888bd9eb4c2c0`

Already accepted runtime facts:
- 10,000 managed / 10,000 linked / 10,000 READY
- 10,000 unique names/accounts
- zero duplicates
- functional 10k scale soak GREEN
- historical 45-minute 5000->10000 performance threshold NOT MET
- production human locality/live materialization was previously proven
- a short live observation showed several materialized phantoms, one sitting
- most visible phantoms appeared idle during that short observation
- no spontaneous chat was observed during that short observation

Important:
The last two observations are NOT proof that autonomous behavior is absent.
They are a user-visible symptom that must be mapped to current production behavior and missing live evidence.

Do not repeat 10k scale testing in this audit.

# PHANTOM-VISION-AUDIT-001: evidence ledger

## Scope and reading method

At entry, branch `feature/phantom-world` and HEAD `77672c071c1c69669dec6527e7f888bd9eb4c2c0` matched `TASK.md`. This was a source/evidence audit only: no LocalPlay, database read/write, build, test run, private config change, production/test edit or scale retest. Existing worktree modifications in production/test and unrelated untracked files predated this audit and were left untouched. In particular, current on-disk source was read with that caveat; inspected local diff of the materialization and multiparty test paths showed only line-ending warnings, not a semantic patch.

The read-first pass covered root/module `AGENTS.md`, `TASK.md`, `WISHLIST.md`, `ACCEPTANCE.md`, `GOAL.txt`, this baseline `EVIDENCE.md`, root/module README availability, relevant phantom roadmap/workflow/task-package docs, phantom `build.xml` targets, targeted production source under `java/org/l2jmobius/gameserver/phantoms/`, focused test source under `test/java/org/l2jmobius/tests/phantoms/` and `test/java/org/l2jmobius/gameserver/phantoms/`, market config and accepted LIVE-003 reports. No standalone phantom architecture doc, `docs/phantoms/CODE_MAP.md`, or module README was found; they were not searched repeatedly. Local analogues were existing Phantom decision adapters, party/raid coordinators and focused suite conventions. No code pattern was changed. The audit reuses exact source/test/live citations in the matrix, preserving the current architecture, configuration and task gate.

`VISION_MATRIX.tsv` contains one row for each of the 146 checklist bullets in sections 1–17 of `WISHLIST.md`, in order. Section 18 is an observed symptom analyzed in `VISION_SUMMARY.md` and `MATERIALIZATION_UX_GAPS.md`; section 19 requests the separate OPEN_WISHES list below. Evidence columns use these prefixes relative to the module root:

- `P/` = `java/org/l2jmobius/gameserver/phantoms/`; `#name` is the exact method in that class.
- `T/` = `test/java/org/l2jmobius/tests/phantoms/`; `G/` = `test/java/org/l2jmobius/gameserver/phantoms/`. A suite path indicates an existing focused automated test, **not** a test executed in this audit.
- `L/` = `docs/phantoms/tasks/`; the referenced report is pre-existing live evidence, not a new observation.
- `none` means no qualifying evidence found for that axis. `NEXT_VERTICAL_SCENARIO` refers to M1–M5 in `VERTICAL_MILESTONES.md`.

All 146 production/wiring method references and test/live paths in the matrix were checked for file and method existence. That static existence check cannot by itself prove runtime behavior; the status decisions also used the reachable call/wiring path and the narrowly worded accepted evidence. No `LIVE_PROVEN` value was inferred from a class, suite, Ant target or status counter alone.

## Witnesses and limits

- `docs/phantoms/tasks/LIVE-003-RUNTIME-SCALE-10000/EVIDENCE.md`, especially the client witness at lines 104–105: `effectiveACTIVE=7`, `worldMaterialized=7`, `localSignaled=20`; profile 201 READY, ACTIVE/STABLE/WORK_DELIVERED, `worldPresent=true`, `nativeFailure=null`. A real client saw several фантомов, one sitting. Most appeared idle, with no spontaneous chat in a short observation. That proves visible materialization, **not** a pop-in-free approach, sustained action or absence of autonomous chat.
- `docs/phantoms/tasks/LIVE-003E-FINISH-10000-PERSISTED/EVIDENCE.md`, lines 35–46: private active target 64 and materialized cap 128; 10 000 managed/linked/READY, unique names/accounts and zero duplicates; 17 samples over at least 15 minutes without fatal/OOM; background catchup 8 961→9 986 and ecology 9 352→10 000. The same report explicitly retains the earlier 45-minute ramp performance gate as NOT MET. This scale evidence cannot prove raid, support, conversation, market transactions or pop-in quality.
- `docs/phantoms/tasks/LIVE-003-RUNTIME-SCALE-10000/EVIDENCE.md`, lines 53–100, documents earlier locality/materialization gates and later repair before the successful client witness. The final accepted witness supersedes those intermediate blocked states for *materialization only*.

## Production path findings behind the sensitive classifications

### Materialization and idle

`PhantomSystem.start` supplies human positions from online non-headless `World.getPlayers()` (limit 256), registers `PhantomHumanLocalityControl`, scheduler control ports and `PhantomReconcileFirstActivityPort`. `PhantomHumanLocalityControl.onPulse` calls `PhantomTopologyService.perceptibleProfilesAt(..., TARGETABILITY, 1024)` every 1 000 ms and emits WARM signals for 3 000 ms. `PhantomSchedulerPolicy.productionDefaults` and `PhantomScheduler.transitionPlanLocked` provide a 2 000 ms temporal demotion grace. `PhantomMaterializationService.materialize` eventually reaches `PhantomMaterializedPlayer.materialize` and native `Player.spawnMe()`. There is no explicit client-visibility-distance prewarm in this checked path. `MATERIALIZATION_UX_GAPS.md` records the order and uncertainty.

`PhantomScheduler.prepareWorkLocked` delivers a work item into the sink registered in `PhantomSystem.start`; the sink reconciles background ecology and calls `PhantomDecisionEngine.accept`. The engine needs an actionable persisted `PhantomGoal`, a candidate and admission before an action handler runs. `PhantomHistoricalBackgroundPlanner.plan` can create `farm.background`, but `PhantomBackgroundService.directive` routes ordinary farm/travel only in BACKGROUND; WARM/ACTIVE without death returns a non-farm replan result. `PhantomFarmingDecision` concerns acquisition conflict/negotiation, while `PhantomAcquisitionService` can perform target/combat work for an explicit acquisition goal. A producer of general autonomous *visible* solo farming goals was not found. Other possible causes of the reported idle profile remain lack of a goal, candidate timing/cadence, historical admission/store fencing, route/target selection or intentional rest. The client witness lacks a per-profile Goal/candidate/handler trace, so none is asserted as the actual root cause.

### Conversation and REAL party

`PhantomConversationService.onDelivered` ingests addressed native chat; `L2jPhantomConversationContextPort` maps GENERAL/PARTY/WHISPER/TRADE; `PhantomConversationExecutionService.submitGoal` and `L2jPhantomConversationExecutionPort.dispatch` provide responses. Persona/memory and action proposal paths are present (`PhantomHumanizedConversationService.plan`, `L2jPhantomConversationExecutionPort.executeSupport`). `ChatObservationService.openGeneratedSocialDispatch` and `PHANTOM_SOCIAL` can continue generated social dispatch. No autonomous first-turn producer was found in the normal `PhantomSystem.start` control ports. Thus reactive, phantom-initiated, phantom-to-phantom spontaneous and action execution are separate rows/statuses; the short quiet observation is not proof that initiation can never happen.

`L2jPhantomPartyBackend.acquire(MemberKind.REAL)` resolves a live `World` player; native `invite`, `respond`, `observe`, `currentForce` and `capabilities` paths accept REAL participants. `PhantomPartyCoordinator.processManagedInvitation` defers REAL-requester invitations without a matching JOIN/conversation goal; `processTerminal` has a REAL-led commit path. `PhantomPartyRouteCoordinator.request` requires PHANTOM leadership and `advance` moves PHANTOM members, so backend membership does not prove follow of a REAL leader. `PhantomPartyTactics.plan` and `L2jCombatBackend.castSupport` provide real learned-skill support, but a normal autonomous farm objective shared with a REAL player and a multi-minute live witness are missing.

### Support, raid and market

`PhantomPartyTactics.plan`/maintenance handle heal, recharge, resurrection, buff, song and dance; `L2jPhantomPartyBackend.capabilities` and `L2jCombatBackend.castSupport` gate learned skills, target/party/instance, distance, cooldown and MP before native casting. `L2jPhantomRaidAttemptRuntime.advanceSupport` reuses tactics in raid. `PhantomPost002LivingSupportSuite`, `PhantomPartyServerIntegrationSuite` and `PhantomRaidAttemptRuntimeGoal026DSuite` are focused/composed evidence; none is a REAL client ability witness in this audit.

For raid, `PhantomRaidReadinessService.assess`, `PhantomRaidRecruitmentService.recruitNext`, `PhantomRaidAssemblyService.advance`, `L2jPhantomRaidAttemptRuntime.advanceEngagement/advanceMechanic/advanceSupport/advanceRetreat` and `PhantomRaidAttemptService.advance` cover different stages. Recruitment consumes supplied candidate leaders; assembly uses native party/command channel; engagement uses native combat against an actual NPC path. The evidence does not establish autonomous candidate/target choice and an uninterrupted live client raid, so `V072–V084` are deliberately mixed rather than uniformly GREEN.

`PhantomStoreService.open/install/refresh/restore` supports native visible BUY/SELL/MANUFACTURE and transaction list reconciliation. `PhantomAutonomousMarketProducer.onPulse` produces BUY/SELL from need/surplus and is registered in `PhantomSystem.start`; `dist/game/config/Custom/PhantomMarket.ini` enables it by default with bounded concurrency/lifetime/cooldown. `PhantomStoreService.acquireExclusive` requires a materialized player/action lease. Generic server `Offline.java` and `OfflineTraderTable.restoreOfflineTraders` are not a Phantom offline lifecycle. Neither a human transaction nor continued Phantom store operation after owner disconnect/restart is live-proven.

## OPEN_WISHES — outside the accepted 146-row scope

These are candidates for user review, not silently accepted requirements:

1. A measured visibility contract for terrain, client draw distance and camera approach, including a numeric pop-in budget and repeatable route. The wishlist says «appear before notice» but does not define a measurable threshold.
2. A per-profile explanation trace for Goal selection, candidate rejection, admission and native action outcome, visible to operators without altering gameplay. This would disambiguate the observed idle symptom.
3. A bounded anti-echo and consent policy for self-initiated social interaction with REAL players. Existing anti-spam pieces do not define the desired unsolicited-chat frequency.
4. A clear product choice for offline market persistence: whether Phantom stores should use native offline traders or a distinct durable market representation. `V108` asks for the outcome but not the lifecycle contract.

## Audit checks

The matrix was statically parsed as TSV: 146 wishlist bullets matched 146 ordered, unique rows with zero classification/evidence-field errors. Counts: IMPLEMENTED 89/46/10/1; COMPONENT_TESTED 129/7/10/0; INTEGRATED 57/78/10/1 (YES/PARTIAL/NO/UNCLEAR); LIVE_PROVEN 12/6/128 (YES/PARTIAL/NO). Production references and suite/live paths were checked against files, and cited methods were checked in the referenced Java source: zero unresolved references. All six audit output paths exist and the milestone table has five entries. No gameplay tests, build or LocalPlay were run by this audit.

Encoding checks on the six changed audit outputs were separate: the complete prescribed mojibake-marker set returned no matches, and the prescribed escaped-Cyrillic/numbered-XML regex returned no matches. The Git staged-scope check and `git diff --cached --check` must still be performed immediately before commit; their result is reported with the commit/push outcome.
