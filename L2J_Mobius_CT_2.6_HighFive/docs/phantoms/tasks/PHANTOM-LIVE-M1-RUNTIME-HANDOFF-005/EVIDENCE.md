# EVIDENCE — PHANTOM-LIVE-M1-RUNTIME-HANDOFF-005

## BASELINE / ROOT CAUSES
- Branch: feature/phantom-world; origin: https://github.com/kpCat/L2J.
- Baseline: 82fae37aff225e959213e1d0432b53d50360318a.
- Старый deployed source: de40c81411cf6501cb9446840aef98f7dd9369e2.
- Чужие tracked paths сохранены: PhantomMaterializationService.java,
  PhantomClanDirectiveIntegrationGoal030C2ASuite.java, PhantomMultipartyEconomySuite.java.
  Их SHA256 до/после реализации совпали; в code commit они не входят.
- profile 5079: точная историческая причина OFFLINE неизвестна. Старый TSV
  не содержал raw calendar/nextBoundary; история не реконструирована новым live-run.
- RED до исправления: ecology pending переводил calendar-online presence в OFFLINE;
  worker I/O блокировал population pulse; rejected nonzero navigation id создавал
  navigation_pending; native one-point endpoint отвергался только по размеру списка.

## CHANGED / EXACT CODE ALLOWLIST
Все пути ниже относительно L2J_Mobius_CT_2.6_HighFive; другие хроники не менялись.
Более 10 файлов разрешены TASK/SOURCE_MAP как один согласованный M1 handoff.
- build.xml — единый M1 aggregate с существующими guarded TEST routes.
- java/org/l2jmobius/gameserver/localplay/LocalPlayPilotActions.java — D6, calendar horizon, cohort preparation, census pages.
- java/org/l2jmobius/gameserver/phantoms/PhantomScheduler.java — typed transition reason.
- java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java — все async callers, receipt owner, stop, census.
- java/org/l2jmobius/gameserver/phantoms/activity/PhantomActivityMaterializationPort.java — additive reason.
- java/org/l2jmobius/gameserver/phantoms/activity/PhantomActivitySnapshot.java — reason snapshot.
- java/org/l2jmobius/gameserver/phantoms/activity/PhantomReconcileFirstActivityPort.java — production readiness/locality gate.
- java/org/l2jmobius/gameserver/phantoms/background/PhantomVisibleFarmTravel.java — immediate/async terminal, progress, failure evidence.
- java/org/l2jmobius/gameserver/phantoms/navigation/PhantomNavigationService.java — shared native contract.
- java/org/l2jmobius/gameserver/phantoms/navigation/PhantomNativeRouteContract.java — pure normalizer/segment validator.
- java/org/l2jmobius/gameserver/phantoms/population/PhantomPopulationEcologyService.java — request/view/receipt, single bounded worker, drain fence.
- java/org/l2jmobius/gameserver/phantoms/population/PhantomPopulationManager.java — calendar presence independent of effective readiness.
- java/org/l2jmobius/gameserver/phantoms/topology/PhantomHumanLocalityControl.java — atomic locality publication/current anchor recheck.
- test/java/org/l2jmobius/tests/phantoms/PhantomBackgroundSuite.java — real Player async travel/progress/AutoPlay and refusal.
- test/java/org/l2jmobius/tests/phantoms/PhantomGeoValidationRules.java — separate runtime-compatible probe.
- test/java/org/l2jmobius/tests/phantoms/PhantomNavigationCoreSuite.java — normalized path parity.
- test/java/org/l2jmobius/tests/phantoms/PhantomPopulationEcologyGoal033Suite.java — async fixture adaptation and focused due regressions.
- test/java/org/l2jmobius/tests/phantoms/PhantomM1RuntimeHandoffSuite.java — integrated coordinator/stop/stale dispatch tests.
- test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java — registration.
- test/java/org/l2jmobius/tests/phantoms/PhantomTravelGeoProbe.java — distinct runtime TSV; historical geo proof retained.
- docs/phantoms/tasks/PHANTOM-LIVE-M1-RUNTIME-HANDOFF-005/Run-M1RuntimeHandoff.ps1 — one connected runner reused from 004.

## AUTOMATED
- `ant -Dbuild=.phantom-local/m1-005-build phantom-m1-runtime-handoff-test`: PASS 102/102.
  Suites: coordinator 5, due ecology 6, navigation 51, scheduler 26,
  local-priority 2, native position/travel 4, background decision 8.
- Real coordinator: same-profile pending→commit→one identity, ready peer during I/O,
  OFFLINE boundary, changed anchor, stop during save, one-use farm receipt.
- Silent ThreadPool submission loss: unstarted claim expires after 256 population
  pulses; generation invalidates late runnable. Running JDBC claim never expires.
  Stop invalidates unstarted work and waits for started commit. No commons API changed.
- Native TEST: immediate movement/cancel, rejected queue id with no hold, actual
  native pathfinder async terminal→physical progress at controlled +65 s→arrival→stock AutoPlay.
- Runtime/probe: one point, repeated start/waypoint, appended endpoint, waypoint/range
  budgets and blocked segment. Existing cancellation/deadline/cache cases retained.
- Continuity: existing hard pins, recent-human 60 s, same identity and cap reclamation.
- `ant -Dbuild=.phantom-local/m1-005-build phantom-live003-runtime-authority-proof-test`:
  PASS 5/5, run once at the end. Canonical 1–85 coverage is not all-world execution proof.
- Raw logs: .phantom-local/m1-005-aggregate-verified.log, m1-005-broad-final.log;
  earlier RED/diagnostic logs remain ignored. PowerShell runner parsed successfully.
- Independent review: dispatch ownership, cleanup deadline, terminal failure evidence
  corrected; final review has no important findings. No new implementation agents.
- Scope guard: exact 21 code paths, staged paths match; foreign hashes preserved.
- Mojibake-маркеры в изменённых файлах проверены: PASS.
- Escaped Cyrillic в изменённых файлах проверены отдельно: PASS.
- `git diff --check` и `git diff --cached --check`: PASS.

## PUBLICATION / DEPLOYMENT / CONNECTED
- Code SHA: e92d7d438641f3f13158021675bd99e2489a7042; normal push confirmed by ls-remote.
- Docs SHA: commit containing this readiness evidence; actual SHA recorded in final chat.
- Clean exact-SHA build: managed detached worktree, clean status and exact HEAD checked;
  `ant -Dbuild=<owned .phantom-local/m1-005-clean-build> jar`: PASS, 22 seconds.
- Deployed GameServer SHA256: 83A2691BC9A417CE9B1D408A735A1E2DCE5423AB658B6E082AF06D39A67DE8D6.
  Build/runtime/manifest hashes agree; manifest gameSourceCodeSha equals code SHA.
- Backup: artifacts/local-play/m1-005-backup-e92d7d4-20260928-201942.
  Only GameServer.jar/manifest replaced; LoginServer.jar retained with its previous hash.
- Controlled hidden startup: Login PID=16840 / Game PID=29616; CONFIG PASS,
  owned ports 2106/9014/7777; native startup 50 s and LoginServer registration observed.
- Closed old-process journal/results preserved in owned Pilot archive (242/244).
  New inbox/processing/journal/results all 0; headroom 512, Pilot OFF before fresh arm.
- Preflight diagnostic PLAY SELECT (production codec/calendar, read-only connection):
  2026-09-28T17:12:35Z, UTC, READY=10000, calendar-online=1052, horizon180=1052.
- Post-deploy read-only calendar 17:21:28Z: READY=10000, online/horizon180=1760.
- Diagnostics and native Pilot mailbox enabled; no startup error in inspected final log.
- Connected actions for task 005: NOT OBSERVED. No consent-gated operation before fresh arm.
- M1 manual gate: REQUIRED. M2 not started. No M1 GREEN claim.

## LIMITS / PROCESS
- Budgets 10000/64/128/100 and navigation budgets/workers unchanged; no world data rewrite.
- PLAY: diagnostic SELECT only outside normal server runtime. Mutation fixtures use TEST.
- A stalled started DB operation stays pending; single worker does not promise recovery
  from hung DB. Scheduler/local pulse stays responsive; no fake readiness or forced online.
- Git commands used under TASK authorization: status --short --branch; rev-parse HEAD;
  diff --stat/--name-only/--check and scoped diff; remote -v; ls-remote origin
  refs/heads/feature/phantom-world; exact-path add; cached diff name/check;
  commit -m; push origin feature/phantom-world. No broad add or history rewrite.
- Final docs scope: task input package and this task STATE/EVIDENCE plus required
  docs/phantoms/reports/PHANTOM-LIVE-M1-RUNTIME-HANDOFF-005.md; raw artifacts ignored.
