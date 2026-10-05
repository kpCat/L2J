# M1 critical path: exact High Five source

Source: `ccacd6c5bf8fa1234a5559ece708ee3a35efd536`. GOOD `561c84a2dc23d6dd953e755e2fafcfbddcd5d395`; BAD production `c23915df10239bfab15ae49276e14833268b9afc`. Все Java paths ниже относительно `java/org/l2jmobius/gameserver/`. Это кодовая карта, не вывод о live причине.

## A. REAL → World → human locality

| Edge и exact method | Thread/owner | Source of truth; read/write; DB | Fail/defer; TASK014 event | GOOD→BAD history area |
|---|---|---|---|---|
| `network/GameClient.load(int)` → `phantoms/player/PhantomIdentityLeaseRegistry.tryAcquire(objectId, REAL_LOGIN)` → `model/actor/Player.load(objectId)` | Network login execution; identity registry lease | Character objectId/account; lease owner REAL_LOGIN; Player restore читает DB | Busy identity/load failure. Recorder не инструментирует REAL login; Pilot STATUS доказывает owner | REAL_IDENTITY, WORLD_SPAWN |
| `network/clientpackets/EnterWorld.runImpl()` → `client.setConnectionState(IN_GAME)` → `player.spawnMe(x,y,z)` | Network packet execution; canonical Player/World | ConnectionState, online Player, World player/object maps; штатный login/store | IN_GAME отдельно проверяется реальным Pilot sessionValid; не выводится из DB online | REAL_IDENTITY, WORLD_SPAWN |
| `phantoms/PhantomSystem.onlineHumanPoints()` → supplier в `PhantomHumanLocalityControl.onPulse()` | Отдельный общий local scheduler pulse через ThreadPool | World Players, `isOnline && !hasHeadlessOutboundSession`, max256; snapshot xyz/instance; без DB writes | Нет REAL/human point → дальнейшего locality demand нет. HUMAN_REFRESH_BEGIN/SUMMARY | COMPOSITION, LOCALITY |

REAL actor — TestAdmin с IN_GAME/REAL_LOGIN. `onlineHumanPoints` сам не проверяет client/identity kind: его фактический predicate указан выше и не будет усилен диагностикой. GM flags не участвуют в этом predicate, но нужны для наблюдения stock visibility.

## B. Demand → relevance signal → scheduler promotion

| Edge и exact method | Thread/owner | Source of truth; read/write; DB | Fail/defer; event | History area |
|---|---|---|---|---|
| `PhantomHumanLocalityControl.onPulse()` → `topology/PhantomTopologyService.nativeProfilesAt(human,1024,_online)` | Shared local pulse; generation read lease и profile registry только при capture | Native region candidates текущей topology generation; prewarm и eligible predicate выполняются после topology lock; без DB | Running view отсутствует, нет spatial candidates, presence offline, cap overflow. LOCAL_CANDIDATE с ONLINE/OFFLINE на existing eligible evaluation; summary counts | TOPOLOGY, LOCALITY, PRESENCE |
| `nativeProfilesAt` → `PhantomTopologyProfileRegistry.nativeCandidates`; `PhantomNativeLocalityEnvelope.prewarm/couldKnow` | Topology generation/registry owner; eligibility вне lock | Committed topology point либо живой materialized point; native surrounding region envelope, не выдуманный radius | Locality query уже отбрасывает offline. Нельзя считать пустой returned query доказательством отсутствия raw candidates | TOPOLOGY, LOCALITY |
| `PhantomHumanLocalityControl.onPulse` → `canPrewarmAt` → `PhysicalDemand`, `_local` | Local pulse; immutable volatile snapshots | Query+live Players; сохраняет capped previously-serviced demand, удаляет не-current points; position revisions/DemandFact | Local candidate removed; LOCAL_CANDIDATE_REMOVED, HUMAN_REFRESH_SUMMARY | LOCALITY, ECOLOGY |
| `onPulse` → preparation callback `PhantomPopulationEcologyService.updateMaterializationDemand` | Same pulse; ecology owns queue/receipts | Existing bounded preparation slots: materialization capacity и scheduler active budget; `_physical.facts()` | Асинхронный preparation, не разрешение spawn; recorder не повторяет callback | ECOLOGY, COMPOSITION |
| `onPulse` → `PhantomSchedulerRelevanceSignalPort.submit` → `PhantomScheduler.submitSignal` | Local pulse; scheduler `_monitor` уже принадлежит вызываемому коду | Source `human.local`, monotonic sequence, NEARBY_PERCEPTIBLE, TTL10000ms; slots/source generations/readyQueue | NOT_RUNNING/NOT_REGISTERED/REJECTED/STALE/BACKPRESSURE/ACCEPTED/COALESCED. LOCAL_SIGNAL_RESULT и SCHED_SIGNAL_* | SCHEDULER, LOCALITY |
| `PhantomScheduler.localPulseSafely()` → `_localControlPort.onPulse()` → readyQueue scan | Отдельный local PulseDriver; `_monitor` только существующий | `_enqueued`, processing/work/boundary flags, effective/requested state, retainedFailure, retryDue; profile-per-pulse budget | Processing conflict, materialized effective state, no requested materialization, retained failure, transient retry. SCHED_LOCAL_SCAN фиксирует existing slot fields без дополнительных requestedStateLocked вызовов | SCHEDULER |
| `localPulseSafely` → `processLocalPromotion` → `transitionPlanLocked` | Local pulse; plan создаётся под `_monitor`, boundary исполняется вне него | RequestedState после expireSignalsLocked; activity generation, transitionStatus, blockedTarget/retryDue, demotion grace | Null plan: stable, retry/backoff, retained explicit retry. SCHED_LOCAL_PROMOTION_SELECTED, SCHED_BOUNDARY_PLAN | SCHEDULER |
| Обычный `pulseSafely/processSlot` → `transitionPlanLocked/executeBoundary` | Основной общий scheduler pulse | Тот же канонический transition plan, не отдельная materialization семантика | Наблюдается тем же boundary hook, чтобы local-only trace не потерял обычный путь | SCHEDULER |

## C. Readiness → ecology due → current locality

| Edge и exact method | Thread/owner | Source of truth; read/write; DB | Fail/defer; event | History area |
|---|---|---|---|---|
| `PhantomScheduler.executeBoundary(MATERIALIZE)` → `activity/PhantomReconcileFirstActivityPort.materialize` → installed readiness lambda | Calling scheduler boundary, вне scheduler `_monitor` | `_readiness` installed once, gate outcome сохраняется | SCHED_BOUNDARY_PLAN; result фиксируется перед applyTransitionOutcomeLocked | READINESS, SCHEDULER |
| `installPopulationReadiness` lambda → `presence.isOnline(profileId)`; только при true → `locality.isLocal(profileId)` | Boundary thread; existing presence/locality reads | Calendar presence registry и текущая locality; short-circuit порядок сохраняется | `presence.no_current_local_demand`, existing withdrawMaterializationDue. READY_PRESENCE_OFFLINE / READY_NOT_LOCAL | PRESENCE, LOCALITY, READINESS |
| Lambda → `ecology.requestMaterializationDue(profileId)` → `requestDue/registerDue` | Boundary + async ecology worker; ecology lock/receipt owner | Durable background cursor, due/ready receipts, current demand; existing scheduling permission publication | incomplete DueReconciliation с exact reason; READY_ECOLOGY_DUE/READY_ECOLOGY_DEFER; recorder не делает DB calls, ecology может делать свои штатные операции | ECOLOGY, BACKGROUND_ATTESTATION |
| Complete due → `locality.isCurrentLocal(profileId)` → delegate | Same boundary; locality predicate как в source | Повторная current locality после готовности, без изменения spawn point | `presence.committed_position_not_local`, READY_CURRENT_LOCAL_FALSE; иначе READY_PASS | READINESS, LOCALITY |

## D. Materialization → exact canonical Player → action admission

| Edge и exact method | Thread/owner | Source of truth; read/write; DB | Fail/reject; event | History area |
|---|---|---|---|---|
| Delegate → `activity/PhantomMaterializationServiceActivityPort.materialize` → `player/PhantomMaterializationService.materialize(profileId)` | Calling boundary; service `_stateMonitor`/per-entry ownership | Profile→characterObjectId, service RUNNING, capacity, catchup/normal lifecycle guards; repository read | PROFILE_NOT_FOUND/READ_FAILED/UNLINKED, CATCHUP_FENCED, BACKGROUND_RECONCILIATION_BLOCKED, capacity/identity statuses. MATERIALIZE_CALL/RESULT фиксируют exact existing status и bounded scalar snapshot | MATERIALIZATION, BACKGROUND_ATTESTATION |
| Service lifecycle `beforeMaterialize` → `MaterializationLifecycleAttempt` → `PhantomMaterializedPlayer.materialize` | Same synchronous call; identity lease и owned actor | Canonical profile/character admission; NORMAL purpose; object identity/maps, retained recovery | AdmissionRejected→CATCHUP_FENCED; lifecycle RuntimeException→BACKGROUND_RECONCILIATION_BLOCKED | MATERIALIZATION, BACKGROUND_ATTESTATION |
| `PhantomMaterializedPlayer.materialize` → `_identityRegistry.tryAcquire(_objectId,PHANTOM)` | Same boundary; identity registry lease; existing actor monitor | `_state=CLAIMED`, exact identity lease; registries free checks | IDENTITY_BUSY/world/autosave collisions; MAT_IDENTITY_CLAIMED | REAL_IDENTITY, WORLD_SPAWN |
| Actor → `Player.load(_objectId, loadedConsumer)` | Same boundary; native restore owner | `_state=LOADING`; consumer retains exact Player and attaches PhantomNativeWorkScope before restore continues; reads native Player DB | null/load/object mismatch/autosave ownership; MAT_PLAYER_LOAD_BEGIN/OK | WORLD_SPAWN, NATIVE_WORK |
| RestoreEffects → `_lifecycleSupport.afterPlayerLoad(_player)` → service `_lifecyclePort.afterPlayerLoad(profileId,player)` | Same native work owner, canonical composite lifecycle | Background/native scalar and inventory reconciliation; existing lifecycle may touch DB | Lifecycle exception aborts; MAT_AFTER_PLAYER_LOAD_OK only after return | BACKGROUND_ATTESTATION, MATERIALIZATION |
| Output attachment/native action supplier/domain init → `setOnlineStatus(true,true)` | Same boundary, HeadlessPlayerOutboundSession; no fake GameClient | MATERIALIZING, headless output, running/standing/penalties; native online DB update | MAT_ONLINE only after return, не доказательство World membership | WORLD_SPAWN, NATIVE_WORK |
| `Player.spawnMe()` → World identity maps, exact instance verification | Same boundary; canonical World/WorldObject | World.getPlayer/findObject обязаны указывать на тот же `_player` | WORLD_REGISTRATION_MISMATCH; MAT_WORLD_SPAWN после exact verification | WORLD_SPAWN |
| Existing `_actionMonitor` block opens admission → `_state=ACTIVE` | Same boundary; existing action/native-work owner | `_actionGeneration++`, `_actionAdmissionOpen=true`; no recorder lock added | MAT_ACTION_ADMISSION_OPEN after block. Any existing catch RuntimeException/Error → MAT_ABORT, then unchanged cleanup/rethrow | NATIVE_WORK, MATERIALIZATION |

Hook correlation использует только scope вокруг синхронного service.materialize, profileId→objectId/epoch. Это не новый gameplay owner. Нельзя читать полные Player/snapshot serialization, вызывать identity/topology queries из recorder или изменять constructors ради профильной привязки.

## E. Visible farm: reuse stock engine

| Edge и exact method | Thread/owner; truth/state/DB | Fail/defer; diagnostic coverage | History area |
|---|---|---|---|
| `PhantomSystem` production work sink → `PhantomDecisionEngine` → `PhantomBackgroundDecision` candidate/plan → `startVisible` | Shared bounded decision worker; persisted `farm.background` goal, plan/step, activity generation | Existing selected decision trace показывает Goal→candidate→handler; task014 не расширяет gameplay | VISIBLE_AUTOPLAY, BACKGROUND_ATTESTATION |
| `PhantomBackgroundDecision.bindVisibleLife` → `PhantomVisibleFarmTravel.arrive(profileId,goal)` → `PhantomVisibleAutoPlay.start` | Owned materialized Player action lease; same goal, committed arrival/native navigation | Travel pending/retry/failure, policy/goal/owned-store guards; stock reason `background.visible.autoplay_started` | VISIBLE_AUTOPLAY, STORE_DEMAT |
| VisibleAutoPlay → `AutoPlayTaskManager`/`AutoUseTaskManager` → `PlayerAI` | Shared native managers; PhantomNativeWorkScope action admission/tick lease | Native target/movement/attack/cast/reward/pickup; native gameplay DB/state как у обычного Player. TASK014 лишь MAT_ACTION_ADMISSION_OPEN, не proof последующего боя | VISIBLE_AUTOPLAY, NATIVE_WORK |

GOOD connected evidence: profile2941 SUCCESS + monster target/visibleAttacking; final jar profile1348 visibleAutoPlaying + native CAST + live displacement. Архивный RESULT честно оставляет materialization-envelope acceptance unproven, но natural visible gameplay этим не отменяется.

## F. Return: loss of locality → store → background

| Edge и exact method | Thread/owner; truth/state/DB | Fail/defer; coverage | History area |
|---|---|---|---|
| Local demand/TTL loss → scheduler `expireSignalsLocked/transitionPlanLocked` → demotion | Shared scheduler; source TTL, demotion grace, retained/soft reclamation policy | Native-visible retention `PhantomReconcileFirstActivityPort.dematerialize`; boundary PLAN/RESULT сохраняют демат события | LOCALITY, SCHEDULER |
| Service `dematerialize` → actor `cleanup` → `closeActionAdmissionAndDrain` | Owned cleanup; existing native/action monitor and timeout, не recorder | Close native admission, drain pending work; failure retained for explicit recovery, force не добавляется | NATIVE_WORK, STORE_DEMAT |
| Actor cleanup → lifecycle `beforeStore` → native Player store → lifecycle `afterStore` | Existing owned PREPARE/NATIVE/FINALIZE protocol | Native character/items и durable background components; owned intent/receipt guards. Задача не меняет writers или persistence | STORE_DEMAT, BACKGROUND_ATTESTATION |
| Cleanup → `Player.deleteMe/stopAllTasks` → outbound/native owner detach → identity lease close → STORED/background | Canonical World removal, owned registration and cleanup postconditions | Failure не маскируется; World removal/identity release не заменяются диагностикой | WORLD_SPAWN, STORE_DEMAT |

## Commit linkage и границы

Exact commits для каждого area/edge перечислены в `REGRESSION_MATRIX.tsv` (files + reviewed semantic_change + diff evidence). Composition rows относятся к установке owners/gates в `PhantomSystem`, а не автоматически ко всем областям. Наличие file touch не доказывает причинность. До observe014 все runtime-break hypotheses UNPROVEN; shortlist допустим только после profile-correlated FIRST_LOST_EDGE.

Recorder: diagnostics + consented Pilot BEGIN, one session, max8 first spatial local-demand profiles (включая отвергнутые existing presence predicate), profile0 summaries, fixed ring8192, reason96. Hooks no I/O/wait/new gameplay lock; ring write exceptions fail closed. SNAPSHOT/END только после observation. Consent cancellation останавливает recorder через существующий Pilot actions cleanup path.
