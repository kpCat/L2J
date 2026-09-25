# EVIDENCE

Baseline:
`729a042fbde6beaf0748bbd2cb547797aa807a99`

Current LIVE-003 gate:
15/15 progression routes PROVEN; 0D live locality/materialization witness pending.

`LIVE002_AUTHORITY_NOT_LOADABLE`:
resolved by the guarded production-loaded TEST DB proof described below.

`MATERIALIZATION_LOCALITY_BLOCKED`:
current code is conservative:
READY with no committed BackgroundState has no topology node and cannot be locally materialized.
Need runtime witness that canonical position appears before first local materialization,
especially for newly-created profiles.

Already GREEN:
A presence incl party/store/action BUSY
B 5–15m shared cadence / ecology periodic owner
C pure idempotence
D ordinary spoil
E existing TopologyProfileRegistry + local query
F v1/v2 schedule 168/168
G nickname10k code
H observability
I pure10k.

Private runtime path:
`artifacts/local-play/runtime`.

PLAY DB:
`l2jmobiush5_localplay3`.

Guarded TEST DB:
`l2jmobiush5_phantom_test`.

0B-C1 established exact LocalPlay ownership scripts.
0C historical runtime had 1280 managed/linked profiles and recoverable catchup without reset/reseed.

Main worktree has had unrelated dirty tracked files in prior tasks.
Runtime JAR must therefore come from clean committed worktree.

Scale philosophy:
durable population scales; expensive ACTIVE/materialized target does not.
Only private PopulationTarget changes between 1280/5000/10000.

## 2026-09-25 current 1280 gate (pending client locality witness)

- Production-loaded guarded TEST DB proof: 15/15 progression rows `PROVEN`; PLAY was not used by the proof. Committed and pushed runtime revision: `1855a3c9700af45e04d12fc11bf215675b0f64ae`.
- Delivered GameServer.jar was built once from a clean detached worktree at that commit; SHA-256 `BC1BC22921A0568421FA4A066D885A3F429478BB4C0C9B97C1A225B6F4A42956`. The exact tracked phantom data bytes were copied into private runtime after Windows worktree line-ending conversion was found and corrected. Private configs were preserved.
- Fresh PLAY read-only baseline and after-client snapshots: managed/linked `1280/1280`, distinct names/accounts `1280/1280`, duplicate names/accounts `0/0`, population READY `1280`, BackgroundState and committed anchors `1280/1280`.
- User's first allowlisted client screenshot at `2026-09-25T20:37:55Z` proves runtime RUNNING, `admitted=64`, periodic owner ECOLOGY, registry `registered=resolved=1280`, buckets `18`; it also proves `effectiveACTIVE=0`, `worldMaterialized=0`, `localSignaled=0`, profile 18 admitted but `worldPresent=false`. **0D is not GREEN.** No scale or private target change has occurred.
- Code path: real non-headless player positions feed `PhantomHumanLocalityControl`; its topology query returns no profiles when the player is outside every topology node. Materialization additionally requires local signal and completed ecology due. Persisted human character position `(-79465,150789,-3040)` lies outside the committed topology nodes, but is not a live position witness. Profile 18 has canonical BackgroundState `(25720,11288,-3720)` at a route node of radius 1.
- A read-only after-client snapshot predicts READY/admitted profile 967, canonical BackgroundState `(-71081,254981,-3232)`, catchup COMPLETE, inside the Human Fighter farming topology node of radius 213. Admission is a schedule reconstruction, pending live client confirmation. Asked user to move a real client there by normal gameplay and send `.phantomstatus` plus `.phantomstatus 967` in this same chat.
- Exact owned LocalPlay pair remained RUNNING at sample m9: Login PID 47068 (ports 2106/9014), Game PID 26680 (port 7777). Heap `3494/4096 MiB` (85.3%), DB connections `13/151`, 236 Game threads, no fatal marker, names/accounts still unique. A single earlier heap sample exceeded 90%; no three consecutive minute samples did.

## 2026-09-26 next client locality attempt

- The user's second client screenshot at `2026-09-25T21:14:36Z` supplies the current real player position `(-71150,254922,-3232)`. Do not use the earlier persisted PLAY character position as current. Runtime remains RUNNING, `desired=641`, `eligible=142`, `admitted=64`, `effectiveACTIVE=0`, `worldMaterialized=0`, periodic owner ECOLOGY, registry `registered=resolved=1280`, `queryLast=102`, `localSignaled=0`. The topology query now sees nearby profiles, but none is locally signaled. Profile 967 reports `eligible=false`, `admitted=false`, `reason=ecology_fenced`, `worldPresent=false`. **0D remains not GREEN.**
- A fresh `2026-09-25T21:22Z` PLAY SELECT/SHOW snapshot again found `1280/1280` managed/linked, unique names/accounts, all 1280 READY with committed BackgroundState; catchup COMPLETE 1130, FAILED_REPLAN_REQUIRED 97, RUNNING 53. Profile 967's background catchup is now `FAILED_REPLAN_REQUIRED`, reason `model.object_cap_indivisible`, and ecology has a pending request. It is not the next candidate.
- The current schedule/ecology reconstruction selects profile 353 among the nearest READY candidates: background catchup COMPLETE, ecology initial complete with no pending request, canonical committed position `(-90072,248328,-3568)`, node `population.route.human-mystic.b1`. Live client admission still requires `.phantomstatus 353` confirmation.
- That route node has radius 1. A one-hop TARGETABILITY background edge joins it to the broad generated farm polygon `generated.farm.432802d3c2901d9686cb1651`. Read-only geometry probes confirm `(-90236,248300,-3568)` and the safer interior point `(-90450,248300,-3568)` lie inside that polygon. The latter is about 20.4k game units from the user's current position; the exact radius-1 neighboring route point `(-89240,249912,-3568)` is about 18.8k units away. No safe non-GM server-side teleport route was found in the existing LocalPlay tools; `AdminTeleport` is an admin command. No PLAY direct DML/DDL or config edits performed.
- Exact owned LocalPlay remained RUNNING at samples m12/m13: Login PID 47068, Game PID 26680, owned ports 2106/9014/7777, latest heap `3406/4096 MiB` (83.15%), DB connections 13/151, no fatal marker, `1280/1280` unique. Game threads were 290 then 288; a read-only JVM dump showed bounded named pools (64 scheduled, 64 MMO server, 64 MMO packet, 32 ordinary, 16 high-priority scheduled). Continue observing the thread count before 1280 GREEN.

## 2026-09-26 reproducible 0D locality proof

- User's current real client position is approximately `(-71261,254936,-3240)`; the earlier coordinates and profile 353 travel request are superseded. A fresh PLAY SELECT snapshot confirms that player online at these coordinates, with 1280 READY profiles, 1280 committed BackgroundState positions, unique names/accounts, and no profile reset or reseed.
- The bounded read-only topology/admission probe found no READY + eligible + admitted profile in the player's current Human Fighter farming node or a one-hop TARGETABILITY neighbor. `queryLast>0` together with `localSignaled=0` is consistent with this actual candidate gap. The prior manual travel approach is withdrawn.
- Locality-only code adds a one-shot `.phantomlocalproof` command to the existing allowlisted personal voiced handler. It requires a real online non-headless client, the private LocalPlay manifest, and a task-owned file keyed to the exact GameServer PID and human character ID. It chooses the nearest currently READY + eligible + admitted + AVAILABLE profile in the same instance using the in-memory topology registry's canonical resolved point, rechecks admission, consumes the file, and teleports only the real player to that committed point. The command does not mutate phantom state or perform direct PLAY SQL; normal GameServer player-position persistence remains possible. Production semantics are unchanged without the LocalPlay marker.
- Focused selector regression suite `phantom-operator-observability-goal028cp1-test`: 8/8 PASS, including nearest canonical candidate and fail-closed invalid candidates. The modified voiced script compiled after the Ant build finished. No live 0D GREEN claim yet; client `.phantomstatus` and selected profile status remain required after clean committed build/redeploy.
- Exact locality-fix code commit `133c4f4b5e2bd53ab05a78b6f38f62f7ef7fa867` was pushed to `feature/phantom-world`. The task-owned old LocalPlay pair was stopped and ports verified closed. One `ant jar` succeeded in a clean detached worktree at exactly that commit; the temporary worktree was removed. Private GameServer.jar SHA-256 is `59D4FA96399A7414A25B3D3D287CC57AA77739AD4220CA8AE1E682DFC3C60E94`, voiced handler SHA-256 is `9AFDC1ED987F39726B74092280E813D6D05BC592A2DCBF42EEB4B3AD5B75471F`. The previous JAR and handler were backed up. The ten protected private config/login hashes were unchanged after delivery.
- Canonical `Start-LocalPlay.ps1 -Background` restarted Login PID 27208 on ports 2106/9014 and Game PID 4632 on 7777. Private population target remains 1280; ActiveTarget 64, materialized cap 128, and other private budgets unchanged. The task-owned one-shot marker is armed only for Game PID 4632 and real character ID 268486470.
- Fifteen-minute post-restart safety window: exact ownership and ports RUNNING, 1280 managed/linked READY with 1280 committed BackgroundState positions, 1280 distinct names/accounts and no duplicates, DB connections 13–14, bounded Game threads 160–163, fatal markers 0. Heap samples had 90.28% and 92.68% followed by 80.27%; the three-consecutive-minute >90% blocker did not occur. The final m15 heap was 84.08% of 4096 MiB.
- Real client was offline in the m15 PLAY SELECT snapshot after the server restart. Latest user-reported live position remains approximately `(-71261,254936,-3240)`; the saved offline coordinates are not substituted as current. A fresh geometry/admission reconstruction still found no READY + eligible + admitted profile within the current Human Fighter node or one-hop TARGETABILITY neighbor. The one-shot command will choose the nearest live admitted canonical target after client reconnection. **Pause: USER CLIENT ACTION. 0D and 1280 remain pending live client witness; target 5000 was not started.**
