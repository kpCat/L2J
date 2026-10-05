# Адресные diff-фрагменты GOOD→BAD

Фрагменты для обзора; не доказательство runtime causality.

## 3 8609ca630df29d27dfe3b8b0c58b50ca20ed9c11 Prove M1 materialization with bounded LocalPlay Pilot route
-				case STATUS, CAPABILITIES -> Outcome.of("SUCCEEDED", request.operation() == LocalPlayPilotProtocol.Operation.CAPABILITIES ? "STATUS,SNAPSHOT_PHANTOMS,SELECT_VISIBLE_PHANTOM_TRACE,SNAPSHOT_SELECTED_PHANTOM_TRACE,REPLAY_SELECTED_PHANTOM_TRACE,SNAPSHOT_TARGETS,TELEPORT_SELF,MOVE_SELF,STOP_MOVE,SIT,STAND,SELECT_TARGET,SAY,PARTY_INVITE,PARTY_RESPOND,PARTY_LEAVE,ATTACK_NPC,CAST_LEARNED_SKILL" : "SNAPSHOT");
+				case STATUS, CAPABILITIES -> Outcome.of("SUCCEEDED", request.operation() == LocalPlayPilotProtocol.Operation.CAPABILITIES ? "STATUS,SNAPSHOT_PHANTOMS,PREPARE_M1_ENVELOPE,SNAPSHOT_M1_ENVELOPE,SELECT_VISIBLE_PHANTOM_TRACE,SNAPSHOT_SELECTED_PHANTOM_TRACE,REPLAY_SELECTED_PHANTOM_TRACE,SNAPSHOT_TARGETS,TELEPORT_SELF,MOVE_SELF,STOP_MOVE,SIT,STAND,SELECT_TARGET,SAY,PARTY_INVITE,PARTY_RESPOND,PARTY_LEAVE,ATTACK_NPC,CAST_LEARNED_SKILL" : "SNAPSHOT");
+			return Outcome.of("REJECTED", "ACTOR_BUSY");
+		final OperatorLocalityTarget target = PhantomSystem.operatorNearestLocalityTarget(M1_TARGET_ANCHOR).orElse(null);
+			return Outcome.of("REJECTED", "NO_ORDINARY_TARGET_AT_PROOF_ANCHOR");
+		final OperatorAdmissionProfile admission = PhantomSystem.operatorAdmissionProfile(target.profileId()).orElse(null);
+		if ((admission == null) || !admission.admission().admitted() || admission.admission().pendingRebalance() || !GeoEngine.getInstance().hasGeo(M1_ROUTE_START.getX(), M1_ROUTE_START.getY()) || (GeoEngine.getInstance().getHeight(M1_ROUTE_START.getX(), M1_ROUTE_START.getY(), M1_ROUTE_START.getZ()) != M1_ROUTE_START.getZ()))
+			return Outcome.of("REJECTED", "PROOF_TARGET_OR_ANCHOR_UNAVAILABLE");
+		return new Outcome("ACCEPTED", "M1_FIXED_GEO_PROVEN_ANCHOR", Map.of("profileId", Long.toString(_envelopeProfileId), "committedX", Integer.toString(target.committedPosition().x()), "committedY", Integer.toString(target.committedPosition().y()), "committedZ", Integer.toString(target.committedPosition().z()), "startX", Integer.toString(M1_ROUTE_START.getX()), "startY", Integer.toString(M1_ROUTE_START.getY()), "startZ", Integer.toString(M1_ROUTE_START.getZ())));
+			return Outcome.of("REJECTED", "ENVELOPE_NOT_PREPARED");
+		final OperatorLocalityTarget target = PhantomSystem.operatorLocalityTarget(_envelopeProfileId).orElse(null);
+		final OperatorAdmissionProfile profile = PhantomSystem.operatorAdmissionProfile(_envelopeProfileId).orElse(null);
+			return Outcome.of("REJECTED", "ENVELOPE_TARGET_UNAVAILABLE");
+		final var materialization = profile.materialization();
+		final int objectId = materialization == null ? 0 : materialization.characterObjectId();
+		final boolean worldPresent = (materialization != null) && materialization.worldPresent() && (player != null);
+		final boolean clientVisible = worldPresent && regionCanKnow && player.isOnline() && player.isVisibleFor(actor);
+		data.put("snapshotWorldPresent", Boolean.toString((materialization != null) && materialization.worldPresent()));
+		data.put("materializationState", materialization == null ? "STORED" : materialization.state().name());
+		data.put("materializedAgeMillis", (materialization == null) || (materialization.materializedAtNanos() <= 0) ? "-1" : Long.toString(Math.max(0, (System.nanoTime() - materialization.materializedAtNanos()) / 1_000_000L)));
+		data.put("localityCurrent", Boolean.toString(PhantomSystem.operatorHumanLocality(_envelopeProfileId)));
+		data.put("presenceReason", profile.busyReason());
+			data.put("activeSignalSources", Integer.toString(profile.scheduler().activeSignalSources()));
+		return new Outcome("SUCCEEDED", "M1_ENVELOPE_SNAPSHOT", Map.copyOf(data));

## 4 ad4c2394e180d27f7201e29323edb55b1496f7bc Allow READY local targets for M1 Pilot envelope proof
-		final OperatorLocalityTarget target = PhantomSystem.operatorNearestLocalityTarget(M1_TARGET_ANCHOR).orElse(null);
+		final OperatorLocalityTarget target = PhantomSystem.operatorNearestReadyLocalityTarget(M1_TARGET_ANCHOR, M1_ANCHOR_TOLERANCE * M1_ANCHOR_TOLERANCE).orElse(null);
-		if ((admission == null) || !admission.admission().admitted() || admission.admission().pendingRebalance() || !GeoEngine.getInstance().hasGeo(M1_ROUTE_START.getX(), M1_ROUTE_START.getY()) || (GeoEngine.getInstance().getHeight(M1_ROUTE_START.getX(), M1_ROUTE_START.getY(), M1_ROUTE_START.getZ()) != M1_ROUTE_START.getZ()))
+		if ((admission == null) || !"none".equals(admission.busyReason()) || !GeoEngine.getInstance().hasGeo(M1_ROUTE_START.getX(), M1_ROUTE_START.getY()) || (GeoEngine.getInstance().getHeight(M1_ROUTE_START.getX(), M1_ROUTE_START.getY(), M1_ROUTE_START.getZ()) != M1_ROUTE_START.getZ()))
+	/** Read-only READY target selection for a bounded human-locality envelope proof. */
+	public static synchronized java.util.Optional<OperatorLocalityTarget> operatorNearestReadyLocalityTarget(PhantomTopologyPoint human, long maxDistanceSquared2D)
+		Objects.requireNonNull(human, "Human point must not be null.");
+			return java.util.Optional.empty();
+		return PhantomLocalProofSelector.nearestReadyWithin(human, configured._topologyService.listProfiles(), configured._populationManager::admissionProfile, profileId -> configured._populationManager.presence().state(profileId) == PhantomPresenceRegistry.Presence.AVAILABLE, maxDistanceSquared2D)
+			.map(profile -> new OperatorLocalityTarget(profile.profileId(), profile.point(), profile.nodeId(), profile.topologyGeneration()));

## 5 686761a73f1abe9a0d06821d98e18009f0ccad6c Permit bounded M1 target fallback within proven lane
+			return Outcome.of("REJECTED", "INVALID_ARGUMENT");
-		final OperatorLocalityTarget target = PhantomSystem.operatorNearestReadyLocalityTarget(M1_TARGET_ANCHOR, M1_ANCHOR_TOLERANCE * M1_ANCHOR_TOLERANCE).orElse(null);
+		final OperatorLocalityTarget target = PhantomSystem.operatorNearestReadyLocalityTarget(M1_TARGET_ANCHOR, M1_ANCHOR_TOLERANCE * M1_ANCHOR_TOLERANCE, afterProfileId).orElse(null);
+		return operatorNearestReadyLocalityTarget(human, maxDistanceSquared2D, 0);
+	public static synchronized java.util.Optional<OperatorLocalityTarget> operatorNearestReadyLocalityTarget(PhantomTopologyPoint human, long maxDistanceSquared2D, long afterProfileId)
-		return PhantomLocalProofSelector.nearestReadyWithin(human, configured._topologyService.listProfiles(), configured._populationManager::admissionProfile, profileId -> configured._populationManager.presence().state(profileId) == PhantomPresenceRegistry.Presence.AVAILABLE, maxDistanceSquared2D)
+		return PhantomLocalProofSelector.nearestReadyWithin(human, configured._topologyService.listProfiles(), configured._populationManager::admissionProfile, profileId -> configured._populationManager.presence().state(profileId) == PhantomPresenceRegistry.Presence.AVAILABLE, maxDistanceSquared2D, afterProfileId)

## 9 033ee7aab7f493fed7a9e41618759fd04ce38e39 Complete M1 living-world lifecycle integration and native farm routes
+		<source path="data/spawns/Others/22_22.xml" />
+		<source path="data/spawns/Oren/OutlawForest.xml" />
+		<source path="data/spawns/Others/TreasureBoxes.xml" />
+		<source path="data/spawns/Aden/Cemetery.xml" />
+		<source path="data/spawns/Aden/Cemetery.xml" />
+		<source path="data/spawns/DwarvenTerritory/DwarvenStarting.xml" />
+		<source path="data/spawns/Aden/FieldsOfMassacre.xml" />
+		<source path="data/spawns/Aden/FieldsOfMassacre.xml" />
+		<source path="data/spawns/DwarvenTerritory/DwarvenStarting.xml" />
+		<source path="data/spawns/Goddard/GardenOfBeasts.xml" />
+		<source path="data/spawns/Aden/Cemetery.xml" />
+		<source path="data/spawns/Aden/Cemetery.xml" />
+		<source path="data/spawns/Giran/DevilsIsle.xml" />
+		<source path="data/spawns/Oren/OutlawForest.xml" />
+		<source path="data/spawns/DwarvenTerritory/DwarvenStarting.xml" />
+		<source path="data/spawns/Goddard/HotSprings.xml" />
+		<source path="data/spawns/Oren/IvoryTower.xml" />
+		<source path="data/spawns/Oren/SeaOfSpores.xml" />
+		<source path="data/spawns/DwarvenTerritory/DwarvenStarting.xml" />
+		<source path="data/spawns/Giran/DevilsIsle.xml" />
+		<source path="data/spawns/Aden/FieldsOfMassacre.xml" />
+		<source path="data/spawns/Others/18_22.xml" />
+		<source path="data/spawns/Aden/FieldsOfMassacre.xml" />
+		<source path="data/spawns/Others/19_22.xml" />

## 10 c28c5d9999f184aefb7092343548e00a27ca8246 Fix offline locality starvation and prepare natural M1 envelopes
-				case STATUS, CAPABILITIES -> Outcome.of("SUCCEEDED", request.operation() == LocalPlayPilotProtocol.Operation.CAPABILITIES ? "STATUS,SNAPSHOT_PHANTOMS,PREPARE_M1_ENVELOPE,SNAPSHOT_M1_ENVELOPE,SELECT_VISIBLE_PHANTOM_TRACE,SNAPSHOT_SELECTED_PHANTOM_TRACE,REPLAY_SELECTED_PHANTOM_TRACE,SNAPSHOT_TARGETS,TELEPORT_SELF,MOVE_SELF,STOP_MOVE,SIT,STAND,SELECT_TARGET,SAY,PARTY_INVITE,PARTY_RESPOND,PARTY_LEAVE,ATTACK_NPC,CAST_LEARNED_SKILL" : "SNAPSHOT");
-		final OperatorLocalityTarget target = PhantomSystem.operatorNearestReadyLocalityTarget(M1_TARGET_ANCHOR, M1_ANCHOR_TOLERANCE * M1_ANCHOR_TOLERANCE, afterProfileId).orElse(null);
-			return Outcome.of("REJECTED", "NO_ORDINARY_TARGET_AT_PROOF_ANCHOR");
+			return Outcome.of("REJECTED", "INVALID_ARGUMENT");
+			final OperatorLocalityTarget target = (selectedProfileId > 0 ? PhantomSystem.operatorLocalityTarget(selectedProfileId) : PhantomSystem.operatorNearestReadyLocalityTarget(here, (long) M1_TARGET_SEARCH_RADIUS * M1_TARGET_SEARCH_RADIUS, after)).orElse(null);
+				return Outcome.of("REJECTED", "NO_ORDINARY_READY_TARGET");
+			final OperatorAdmissionProfile admission = PhantomSystem.operatorAdmissionProfile(target.profileId()).orElse(null);
+			final EnvelopeRoute route = (admission != null) && "READY".equals(admission.admission().populationState().name()) && "none".equals(admission.busyReason()) ? envelopeRoute(target) : null;
+				return new Outcome("ACCEPTED", "M1_NATURAL_GEO_PROVEN_ENVELOPE", Map.copyOf(data));
+		return Outcome.of("REJECTED", "NO_NATIVE_PREWARM_ROUTE");
+	private static EnvelopeRoute envelopeRoute(OperatorLocalityTarget target)
+			return null;
-		final OperatorAdmissionProfile admission = PhantomSystem.operatorAdmissionProfile(target.profileId()).orElse(null);
-		if ((admission == null) || !"none".equals(admission.busyReason()) || !GeoEngine.getInstance().hasGeo(M1_ROUTE_START.getX(), M1_ROUTE_START.getY()) || (GeoEngine.getInstance().getHeight(M1_ROUTE_START.getX(), M1_ROUTE_START.getY(), M1_ROUTE_START.getZ()) != M1_ROUTE_START.getZ()))
-			return Outcome.of("REJECTED", "PROOF_TARGET_OR_ANCHOR_UNAVAILABLE");
+					if ((prewarm == null) || (outside == null) || (Math.hypot(prewarm.getX() - outside.getX(), prewarm.getY() - outside.getY()) > 2000) || couldKnow(prewarm, inside) || couldKnow(outside, inside) || !PhantomSystem.operatorCanPrewarmAt(target.profileId(), topologyPoint(prewarm)) || PhantomSystem.operatorCanPrewarmAt(target.profileId(), topologyPoint(outside)) || !nativeBothWays(outside, prewarm))
+					final List<Location> path = nativePath(prewarm, inside);
+						return new EnvelopeRoute(outside, prewarm, inside, path);
-		return new Outcome("ACCEPTED", "M1_FIXED_GEO_PROVEN_ANCHOR", Map.of("profileId", Long.toString(_envelopeProfileId), "committedX", Integer.toString(target.committedPosition().x()), "committedY", Integer.toString(target.committedPosition().y()), "committedZ", Integer.toString(target.committedPosition().z()), "startX", Integer.toString(M1_ROUTE_START.getX()), "startY", Integer.toString(M1_ROUTE_START.getY()), "startZ", Integer.toString(M1_ROUTE_START.getZ())));
+		return null;
+			return null;
+		return Math.abs(z - sourceZ) <= 200 ? new Location(x, y, z, 0) : null;
+	private static List<Location> nativePath(Location first, Location last)
+		if (!nativeBothWays(first, last))

## 12 de40c81411cf6501cb9446840aef98f7dd9369e2 Fix generic phantom smart continuity
+			if (signal.requiredState().requiresMaterialization() && signal.requiredState().isHigherDetailThan(slot._effectiveState) && (slot._transitionStatus == PhantomActivityTransitionStatus.DEFERRED))
+			if (slot._effectiveState.requiresMaterialization() && (slot._retainedFailureKind == RetainedFailureKind.NONE))
+				return new TransitionPlan(slot._profileId, slot._generation, requested.requiresMaterialization() ? PhantomActivityState.WARM : requested, BoundaryAction.RECLAIM_SOFT);
-				case MATERIALIZE -> _materializationPort.materialize(plan._profileTargetId);
+				case MATERIALIZE ->
+					final TransitionOutcome outcome = _materializationPort.materialize(plan._profileTargetId);
+				case RECLAIM_SOFT -> _materializationPort.reclaimSoft(plan._profileTargetId);
+		final long profileId = _materializationPort.softReclaimCandidate(requestingProfileId);
+			if ((_state == SchedulerState.RUNNING) && (candidate != null) && candidate._effectiveState.requiresMaterialization() && !candidate._processing && !candidate._localProcessing && !candidate._workInFlight && !candidate._boundaryInFlight && (candidate._retainedFailureKind == RetainedFailureKind.NONE) && reserveReadyLocked(candidate))
+			return;
+import org.l2jmobius.gameserver.phantoms.activity.PhantomMaterializationRetentionPolicy;
+	private PhantomMaterializationRetentionPolicy _materializationRetention;
-				_visibleFarmTravel = new PhantomVisibleFarmTravel(_materializationService, _backgroundService, backgroundAuthority.travelQuery(_topologyService.query()), _navigationService, profileId -> ((_partyCoordinator == null) || !_partyCoordinator.blocksBackground(profileId)) && ((_phantomStoreService == null) || !_phantomStoreService.blocksDecision(profileId)), new PhantomSchedulerRelevanceSignalPort(_scheduler));
+				_visibleFarmTravel = new PhantomVisibleFarmTravel(_materializationService, _backgroundService, backgroundAuthority.travelQuery(_topologyService.query()), _navigationService, profileId -> ((_partyCoordinator == null) || !_partyCoordinator.blocksBackground(profileId)) && ((_phantomStoreService == null) || !_phantomStoreService.blocksDecision(profileId)), new PhantomSchedulerRelevanceSignalPort(_scheduler), (profileId, failure) -> _historicalBackgroundService.recordVisibleFailure(profileId, failure.goal(), failure.stepId()), System::nanoTime);
-					_visibleAutoPlay.stop(profileId);
-					return _visibleFarmTravel.arrive(profileId, goal) && _visibleAutoPlay.start(profileId, goal);
-				}, _visibleAutoPlay::running, (profileId, goal) -> _historicalBackgroundService.replanVisibleFarmIfOutgrown(profileId, goal, _decisionEngine), _visibleAutoPlay::stop);
+						_visibleAutoPlay.stop(profileId);
+						return false;
+					return _visibleAutoPlay.start(profileId, goal);
+				}, _visibleAutoPlay::running, (profileId, goal) ->
+					if (_visibleAutoPlay.noTargetExpired(profileId, goal))
+					return _historicalBackgroundService.replanVisibleFarmIfOutgrown(profileId, goal, _decisionEngine);
+				}, _visibleAutoPlay::stop);

## 15 e92d7d438641f3f13158021675bd99e2489a7042 Separate phantom calendar presence from async ecology readiness and native travel handoff
+	private boolean _envelopeMaterialized;
+		OperatorLocalityTarget selected = null;
+		OperatorAdmissionProfile selectedAdmission = null;
-				return Outcome.of("REJECTED", "NO_ORDINARY_READY_TARGET");
-			final EnvelopeRoute route = (admission != null) && "READY".equals(admission.admission().populationState().name()) && "none".equals(admission.busyReason()) ? envelopeRoute(target) : null;
+			final EnvelopeRoute route = (admission != null) && "READY".equals(admission.admission().populationState().name()) && admission.admission().calendarOnline() && admission.admission().nextBoundary().isAfter(java.time.Instant.now().plusSeconds(180)) && "none".equals(admission.busyReason()) ? envelopeRoute(target) : null;
+					selected = target; selectedAdmission = admission; selectedRoute = route; selectedCohort = cohort;
+				final OperatorLocalityTarget target = selected;
+				final OperatorAdmissionProfile admission = selectedAdmission;
+				_envelopeMaterialized = false;
+				data.put("calendarState", admission.admission().desiredState().name());
+				data.put("nextBoundary", admission.admission().nextBoundary().toString());
+				data.put("readinessReason", admission.readiness() == null ? "ecology.disabled" : admission.readiness().reason());
+		if (!profile.admission().calendarOnline()) { return Outcome.of("REJECTED", "SCENE_INVALIDATED:CALENDAR_OFFLINE"); }
+		if (!"READY".equals(profile.admission().populationState().name())) { return Outcome.of("REJECTED", "SCENE_INVALIDATED:POPULATION_STATE"); }
+		if (!_envelopeMaterialized && !point.equals(_envelopePosition)) { return Outcome.of("REJECTED", "SCENE_INVALIDATED:COMMITTED_ANCHOR_CHANGED"); }
+		_envelopeMaterialized |= worldPresent;
-		data.put("localityCurrent", Boolean.toString(PhantomSystem.operatorHumanLocality(_envelopeProfileId)));
-		data.put("presenceReason", profile.busyReason());
+		data.put("localityCurrent", Boolean.toString(profile.humanLocality()));
+		data.put("presenceReason", profile.admission().calendarOnline() ? "calendar.online" : "calendar.offline");
+		data.put("calendarState", profile.admission().desiredState().name());
+		data.put("calendarOnline", Boolean.toString(profile.admission().calendarOnline()));
+		data.put("nextBoundary", profile.admission().nextBoundary().toString());

## 19 6f6dec73495657da0d09f8ced328eba6b5083ad2 fix(phantoms): complete pending handoff under native locality
-		return (region != null) && region.isSurroundingRegion(World.getInstance().getRegion(target.getX(), target.getY(), target.getZ()));
+		return org.l2jmobius.gameserver.phantoms.topology.PhantomNativeLocalityEnvelope.couldKnow(new PhantomTopologyPoint(human.getX(), human.getY(), human.getZ(), 0), new PhantomTopologyPoint(target.getX(), target.getY(), target.getZ(), 0));
+		final boolean regionCanKnow = sameInstance && org.l2jmobius.gameserver.phantoms.topology.PhantomNativeLocalityEnvelope.couldKnow(new PhantomTopologyPoint(actor.getX(), actor.getY(), actor.getZ(), actor.getInstanceId()), worldPresent ? new PhantomTopologyPoint(player.getX(), player.getY(), player.getZ(), player.getInstanceId()) : point);
+						return () -> future.cancel(false);
-				if (periodicEcology != null) { periodicEcology.installMaterializationDemand(profileId -> _populationManager.presence().isOnline(profileId) && _humanLocality.isLocal(profileId)); }
+					periodicEcology.installMaterializationDemand(profileId -> _populationManager.presence().isOnline(profileId) && _humanLocality.isCurrentLocal(profileId));
+					_humanLocality.installPhysicalDemand(periodicEcology::requestMaterializationDue);
+		if ((configured == null) || (configured._populationEcology == null)) { return Map.of(); }
+		if (configured._humanLocality != null)
+			result.put("signalDelivery", String.valueOf(configured._humanLocality.deliverySnapshot().get(profileId)));
+			result.put("localityOverflow", Boolean.toString(configured._humanLocality.physicalSnapshot().overflow()));
+		return Map.copyOf(result);
+			.filter(profile -> (profile.point() != null) && org.l2jmobius.gameserver.phantoms.topology.PhantomNativeLocalityEnvelope.couldKnow(human, profile.point()))
+			loaded = refreshDeadNativeVitals(profileId, player, loaded);
+	/** Native maxima are derived on DEAD Player load; preserve every durable reward/death fact. */
+	private PhantomBackgroundTransaction.Result refreshDeadNativeVitals(long profileId, Player player, PhantomBackgroundTransaction.Result loaded)
+		if ((state.state() != State.DEAD) || !state.hashes().equals(_authority.hashes())) { return loaded; }
+		if (state.vitals().currentCp() > player.getMaxCp()) { return loaded; }
+		if (!_authority.matchesRuntime(player, normalized)) { return loaded; }
+		if ((goal == null) || !PhantomBackgroundGoalSpec.GOAL_TYPE.equals(goal.goal().goalType()) || (goal.goal().status() != PhantomGoalStatus.ACTIVE)) { return loaded; }
+		if (!captured.progress().equals(progress) || !captured.identity().equals(state.identity()) || !captured.position().equals(state.position()) || !captured.receipt().equals(state.receipt()) || !captured.clock().equals(state.clock()) || !captured.hashes().equals(state.hashes())) { return loaded; }
+		// Existing native store/capture boundary under the materialization claim, with no historical replay.
+		return transaction(() -> _transactions.captureBaseline(captured, goal.goal()));
+			_monotonicMillis = Objects.requireNonNull(monotonicMillis);

## 22 ca2dbc753106d165dc73a1b03ba89aadc69f917e Complete bounded M1 population and readiness preparation
-					_humanLocality.installPhysicalDemand(periodicEcology::requestMaterializationDue);
+					_humanLocality.installPreparationDemand(periodicEcology::updateMaterializationDemand, () ->
+						final var capacity = _materializationService.snapshot();
+						final long soft = capacity.materializations().stream().filter(entry -> entry.worldPresent() && (_materializationRetention != null) && _materializationRetention.observe(entry.profileId()).reclaimable()).count();
+						return Math.min(8, capacity.availablePermits() + Math.toIntExact(soft));
+				_populationManager.installRetirementProtection(profileId -> _materializationRetention.observe(profileId).hard() || ((_partyCoordinator != null) && (_partyCoordinator.committed(profileId) || _partyCoordinator.blocksBackground(profileId))) || ((_phantomStoreService != null) && _phantomStoreService.blocksDecision(profileId)) || ((_economyReservations != null) && _economyReservations.findActive(profileId).isPresent()));
+	private boolean _batchAdmission;
+	/** Physical facts do not imply an expensive preparation lease. */
+	public void updateMaterializationDemand(List<DemandFact> facts, int availablePreparationSlots)
+		for (DemandFact fact : facts) { if (_materialized.test(fact.profileId())) { live.add(fact.profileId()); } }
+			_batchAdmission = true;
+				entry._materializationDemand = true;
+			for (long id : previous.keySet()) { if (!_demandFacts.containsKey(id)) { final Entry entry = _entries.get(id); if (entry != null) { entry._materializationDemand = false; } } }
+			rebuildAdmissionLocked();
+	private void rebuildAdmissionLocked()
+		_demandFacts.values().stream().filter(fact -> { final Entry entry = _entries.get(fact.profileId()); return entry != null && entry._participating && !entry._liveOwner && !entry._terminal && needsWorkLocked(entry) && progressClock() >= entry._nextRetryPulse; })
+		for (long id : List.copyOf(_materializationQueued))
+			if (!_admittedPreparation.contains(id)) { _materializationQueued.remove(id); _materializationDue.remove(id); queueLocked(id); }
+		for (long id : _admittedPreparation) { queueMaterializationLocked(id, false); }
-		if ((_wakeScheduler == null) || _stopping || (_wakeFailure != null) || (_cancelWake != null) || (_due.isEmpty() && _materializationDue.isEmpty())) { return; }
+		if ((_wakeScheduler == null) || _stopping || (_wakeFailure != null) || (_cancelWake != null)) { return; }
+		final boolean runnable = !_due.isEmpty() || !_materializationDue.isEmpty();
+		if (!runnable && _delayed.isEmpty() && !_periodicDueMode) { return; }
+			if (entry == null) { return; }

## 24 9e3576311af6ef8c53e662955e2330b7cee00143 Recover historical phantom background failures for M1
+		if (attempt.successful()) { return attempt.input(); }
+			return FarmInputAttempt.ready(currentFarmInput(state, goal, learnedSkills));
+			return FarmInputAttempt.failed(exception._failure, exception.getMessage());
+			return FarmInputAttempt.failed(FarmInputFailure.UNKNOWN, exception.getClass().getSimpleName());
-			throw new IllegalArgumentException("Background farm requires the exact committed instance-zero anchor.");
-			throw new IllegalArgumentException("Persisted target has no authoritative spawn capacity at the farm anchor.");
+			throw new FarmInputRejected(FarmInputFailure.TARGET_STALE, "farm.spawn_absent");
+			return new Tracking(List.of(), equipment);
+			return FarmInputAttempt.ready(farmInput(state, goal, learnedSkills));
+			return FarmInputAttempt.failed(FarmInputFailure.UNKNOWN, exception.getClass().getSimpleName());
+			Objects.requireNonNull(failure, "failure");
+			reason = Objects.requireNonNull(reason, "reason");
+		public static FarmInputAttempt ready(FarmInput input) { return new FarmInputAttempt(Objects.requireNonNull(input), FarmInputFailure.NONE, "farm.ready"); }
+		public static FarmInputAttempt failed(FarmInputFailure failure, String reason) { return new FarmInputAttempt(null, failure, reason); }
+		public boolean successful() { return failure == FarmInputFailure.NONE; }
+			return new PhantomBackgroundGoalSpec(0, goal.selectedAnchor().key(), 0, 0, 0, 0, 0);
+		if (npcId <= 0) { throw new IllegalArgumentException("Farm goal requires an authoritative NPC."); }
+				if ((drop.origin() != DropOrigin.ORDINARY) && (drop.origin() != DropOrigin.ORDINARY_SPOIL)) { return roll; }
+				if (java.util.stream.Stream.concat(priorLosses.keySet().stream(), losses.keySet().stream()).distinct().count() > MAX_GROUND_LOSS_ITEM_IDS) { return roll; }
+		return new DropRoll(Map.copyOf(accepted), roll.acquisitionCounts(), Map.copyOf(losses), roll.facts());
-				return OperationResult.replan("authority.unsupported");
+			if (!attempt.successful()) { return OperationResult.replan(farmFailureReason(attempt)); }
-		final OperationClaim claim = acquire(profileId, goal, expectedCatchup.generation(), Math.addExact(expectedCatchup.intervalOrdinal(), 1));
+		final OperationClaim claim = acquire(profileId, goal, expectedCatchup.generation(), Math.addExact(expectedCatchup.intervalOrdinal(), 1), true);

## 27 309e5ab3ab713e6e3e46035d7a2f60e01257a063 Implement M1 live observer and bounded handoff runner
+package org.l2jmobius.gameserver.localplay;
+import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.State;
+public final class LocalPlayM1Observation
+		RETURN
+	public record Ticket(String token, String runId, int actorObjectId, long profileId, int objectId, long materializedAtNanos, Purpose purpose, PhantomTopologyPoint destination, long expiresAtNanos)
+			return (nowNanos < expiresAtNanos) && Objects.equals(token, suppliedToken) && Objects.equals(runId, suppliedRunId) && (actorObjectId == suppliedActorObjectId) && (profileId == suppliedProfileId) && (objectId == suppliedObjectId) && (materializedAtNanos == suppliedEpoch) && (purpose == suppliedPurpose) && Objects.equals(destination, suppliedDestination);
+	private LocalPlayM1Observation()
+			return new PositionChoice(PositionSource.LIVE, live);
+			return committed == null ? new PositionChoice(PositionSource.UNAVAILABLE, null) : new PositionChoice(PositionSource.COMMITTED, committed);
+		if (state == State.STORED) { return new PositionChoice(PositionSource.TRANSITION, null); }
+		return new PositionChoice(state == null ? PositionSource.UNAVAILABLE : PositionSource.TRANSITION, null);
+		return worldPresent && clientVisible && (distance2D <= 900);
-	private boolean _envelopeMaterialized;
+	private LocalPlayM1Observation.Ticket _m1Ticket;
+			final LocalPlayM1Observation.Ticket ticket = _m1Ticket;
+			if ((ticket == null) || (current == null) || (current.positionSource() != LocalPlayM1Observation.PositionSource.LIVE) || !_envelopeRunId.equals(runId) || !ticket.valid(args.get("m1Token"), runId, actor.getObjectId(), _envelopeProfileId, current.objectId(), current.materializedAtNanos(), ticket.purpose(), destination, System.nanoTime()))
+				return Outcome.of("REJECTED", "M1_TICKET_INVALID");
+			final boolean validPurpose = ticket.purpose() == LocalPlayM1Observation.Purpose.LEAVE ? !org.l2jmobius.gameserver.phantoms.topology.PhantomNativeLocalityEnvelope.prewarm(destination, live) && !org.l2jmobius.gameserver.phantoms.topology.PhantomNativeLocalityEnvelope.couldKnow(destination, live) : (Math.hypot((long) x - live.x(), (long) y - live.y()) <= 900) && GeoEngine.getInstance().canSeeTarget(x, y, z, instanceId, live.x(), live.y(), live.z(), live.instanceId());
+				return Outcome.of("REJECTED", "M1_SCENE_INVALIDATED");
+			if (ticket.purpose() == LocalPlayM1Observation.Purpose.LEAVE) { _m1LeaveUsed = true; }
+			return Outcome.of("ACCEPTED", "TELEPORT_COMPLETION_PENDING");
+		if (!Set.of("INITIAL", "APPROACH", "LEAVE", "RETURN").contains(stage))
-			return Outcome.of("REJECTED", "INVALID_ARGUMENT");
+			return Outcome.of("REJECTED", "INVALID_STAGE");

## 31 7653282fccfec15a1d62a2d0ea6b5382f81073a5 Fix M1 observer approach envelope reuse and typed replans
+			Objects.requireNonNull(runId);
+			Objects.requireNonNull(outside);
+			Objects.requireNonNull(prewarm);
+			return List.copyOf(points);
+			return (reason == RouteReason.CACHED_ENVELOPE) || (reason == RouteReason.DIRECT_FORWARD) || (reason == RouteReason.PATHFIND_FORWARD);
+	public static RoutePlan planApproach(ApproachEnvelope envelope, String runId, long profileId, long committedSequence, PositionSource source, int objectId, long materializedAtNanos, int lockedObjectId, long lockedEpoch, PhantomTopologyPoint actor, PhantomTopologyPoint observed, int cursor, ApproachNavigation navigation)
+			return rejected(RouteReason.ENVELOPE_STALE);
+			return rejected(RouteReason.TARGET_TRANSITION);
+			return (envelope.committedSequence() == committedSequence) ? cachedRemainder(envelope, actor, cursor, navigation) : rejected(RouteReason.ENVELOPE_STALE);
+		if ((objectId <= 0) || (materializedAtNanos <= 0))
+			return rejected(RouteReason.TARGET_TRANSITION);
+		if ((lockedObjectId > 0) && ((lockedObjectId != objectId) || (lockedEpoch != materializedAtNanos)))
+			return rejected(RouteReason.TARGET_IDENTITY_CHANGED);
+		return liveRoute(actor, observed, navigation);
+			return rejected(RouteReason.ENVELOPE_OFF_ROUTE);
+			return rejected(RouteReason.FORWARD_SEGMENT_REJECTED);
+		return new RoutePlan(RouteReason.CACHED_ENVELOPE, remainder, nearestSegment);
+			return directDistance > 10_000 ? rejected(RouteReason.DISTANCE_LIMIT) : new RoutePlan(RouteReason.DIRECT_FORWARD, List.of(actor, observed), 0);
+		if (found == null) { return rejected(RouteReason.PATHFIND_NULL); }
+		if (found.size() > 62) { return rejected(RouteReason.PATH_TOO_LONG); }
+			if (distance > 10_000) { return rejected(RouteReason.DISTANCE_LIMIT); }
+			if (!navigation.forward(first, last)) { return rejected(RouteReason.FORWARD_SEGMENT_REJECTED); }
+		return new RoutePlan(RouteReason.PATHFIND_FORWARD, points, 0);
+		return new RoutePlan(reason, List.of(), 0);

## 34 f29142c598d7380ed5ac6afc7df4796772603435 Recover bounded M1 historical renewal baseline
+	public Snapshot renewCompletedUnplanned(long profileId, Snapshot expected, PhantomBackgroundCatchupState replacement)
+		requireSequentialRenewal(expected, replacement);
+			throw new IllegalArgumentException("Unplanned Background catch-up renewal requires an absent goal and next plan ordinal.");
+		return decode(_profiles.updateComponent(profileId, PhantomBackgroundCatchupState.COMPONENT_TYPE, expected.rowVersion(), PhantomBackgroundCatchupState.SCHEMA_VERSION, _codec.encode(replacement)));
+	/** Reconcile a durable MATERIALIZED marker only after proving that no Player owns the character. */
+	public OperationResult recoverAbandonedMaterialization(long profileId)
+		if (!claimTransition(profileId, TransitionKind.MATERIALIZING))
+			return retry("recovery.transition_busy");
+		Lease lease = null;
+				return OperationResult.replan("recovery.profile_unlinked");
+			lease = _identities.tryAcquire(characterObjectId, OwnerKind.BACKGROUND);
+			if (lease == null)
+				return retry("recovery.identity_busy");
+			increment(_currentIdentityLeases, _peakIdentityLeases);
+			if ((_materialization.get().find(profileId).isPresent()) || (World.getInstance().getPlayer(characterObjectId) != null) || (World.getInstance().findObject(characterObjectId) != null) || PlayerAutoSaveTaskManager.getInstance().containsObjectId(characterObjectId))
+				return retry("recovery.runtime_busy");
+			if (!loaded.successful() || (loaded.state() == null) || (loaded.state().state() != State.MATERIALIZED) || (loaded.state().identity().characterObjectId() != characterObjectId))
+				return OperationResult.replan("recovery.background_state_invalid");
+			final PhantomBackgroundTransaction.Result recovered = transaction(() -> _transactions.abortMaterialization(profileId, characterObjectId));
+				return OperationResult.success("recovery.abandoned_materialization_reconciled");
+			return mapTransactionFailure(recovered.status());
+			closeLease(lease);
+			releaseTransition(profileId, TransitionKind.MATERIALIZING);
+				if ((backgroundState != null) && (backgroundState.state() == PhantomBackgroundState.State.MATERIALIZED))

## 37 f9562c8002f4d2487baae6840c99001b743d0a83 Fix headless autosave projection drift and cover profile13 mismatch
+		// A headless materialization publishes its canonical Player state at the owned store/capture boundary.
+			return;

## 39 9a9dfb5167ef00ce16af88e01d0df7e9665b41c3 Recover attested legacy headless background drift
+		if (!claimTransition(profileId, TransitionKind.MATERIALIZING))
+			return retry("recovery.legacy.transition_busy");
+		Lease lease = null;
+				return OperationResult.replan("recovery.legacy.profile_unlinked");
+			lease = _identities.tryAcquire(characterObjectId, OwnerKind.BACKGROUND);
+			if (lease == null)
+				return retry("recovery.legacy.identity_busy");
+			increment(_currentIdentityLeases, _peakIdentityLeases);
+			if ((_materialization.get().find(profileId).isPresent()) || (World.getInstance().getPlayer(characterObjectId) != null) || (World.getInstance().findObject(characterObjectId) != null) || PlayerAutoSaveTaskManager.getInstance().containsObjectId(characterObjectId))
+				return retry("recovery.legacy.runtime_busy");
+				return OperationResult.success("recovery.legacy.attested_reconciled");
+			return OperationResult.inconsistent("recovery.legacy." + recovered.status().name().toLowerCase());
+			closeLease(lease);
+			releaseTransition(profileId, TransitionKind.MATERIALIZING);
+		Objects.requireNonNull(witness, "witness");
+				requireProfileLink(lockProfile(connection, witness.profileId()), witness.characterObjectId());
+				final LockedComponent component = requireStateComponent(lockComponent(connection, witness.profileId(), PhantomBackgroundState.COMPONENT_TYPE));
+					requireOne(statement.executeUpdate(), "attested legacy volatile repair");
+				return new Result(Status.SUCCESS, recovered);
+				return failureResult(failure);
+			return failureResult(failure);
+		return (left.x() == right.x()) && (left.y() == right.y()) && (left.z() == right.z()) && (left.heading() == right.heading());
+			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
+				return false;

## 40 0b8fdf1048161b3c66ff9b1c5b422012f0461210 Pin expanded attested inconsistent witness set


## 43 538cf3e9cbe22361bcaba778f6af01d2f802ac65 phantom(task-005): recover exact latent materialized cohort
+					PhantomLegacyHeadlessRecovery.applyMaterialized(new File(ServerConfig.DATAPACK_ROOT, PhantomLegacyHeadlessRecovery.MATERIALIZED_RELATIVE_PATH).toPath(), _backgroundService);
+		Objects.requireNonNull(witness, "witness");
+		return recoverAttestedLegacy(witness.profileId(), witness.characterObjectId(), () -> _transactions.recoverAttestedLegacyHeadlessDrift(witness));
+	public OperationResult recoverAttestedLegacyMaterializedDrift(PhantomBackgroundTransaction.LegacyMaterializedWitness witness)
+		Objects.requireNonNull(witness, "witness");
+		return recoverAttestedLegacy(witness.profileId(), witness.characterObjectId(), () -> _transactions.recoverAttestedLegacyMaterializedDrift(witness));
+		return recoverAttestedLegacyVolatile(witness, null);
+	/** The second pinned cohort may retain MATERIALIZED or its single fail-closed marker transition. */
+	public Result recoverAttestedLegacyMaterializedDrift(LegacyMaterializedWitness witness)
+		Objects.requireNonNull(witness, "witness");
+		final LegacyHeadlessWitness canonical = new LegacyHeadlessWitness(witness.profileId(), witness.characterObjectId(), witness.materializedRowVersion(), witness.materializedPayloadSha256(), witness.canonicalHp(), witness.canonicalMp(), witness.canonicalCp(), witness.canonicalX(), witness.canonicalY(), witness.canonicalZ(), witness.canonicalHeading());
+		return recoverAttestedLegacyVolatile(canonical, witness);
+	private Result recoverAttestedLegacyVolatile(LegacyHeadlessWitness witness, LegacyMaterializedWitness materialized)
+				final boolean markerMatch = (materialized != null) && (component.rowVersion() == (witness.rowVersion() + 1)) && payloadHash.equals(materialized.inconsistentPayloadSha256());
+				if ((materialized == null && !baseMatch) || (materialized != null && !baseMatch && !markerMatch))
+				final boolean expectedState = (materialized == null) ? (current.state() == State.INCONSISTENT) : ((baseMatch && (current.state() == State.MATERIALIZED)) || (markerMatch && (current.state() == State.INCONSISTENT)));
+	public record LegacyMaterializedWitness(long profileId, int characterObjectId, long materializedRowVersion, String materializedPayloadSha256, String inconsistentPayloadSha256, double canonicalHp, double canonicalMp, double canonicalCp, int canonicalX, int canonicalY, int canonicalZ, int canonicalHeading)
+		public LegacyMaterializedWitness
+			if ((materializedRowVersion >= Long.MAX_VALUE) || (inconsistentPayloadSha256 == null) || !inconsistentPayloadSha256.matches("[0-9a-f]{64}"))
+				throw new IllegalArgumentException("Invalid attested materialized witness.");
+			new LegacyHeadlessWitness(profileId, characterObjectId, materializedRowVersion, materializedPayloadSha256, canonicalHp, canonicalMp, canonicalCp, canonicalX, canonicalY, canonicalZ, canonicalHeading);
+import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction.LegacyMaterializedWitness;
+	public static final String MATERIALIZED_RELATIVE_PATH = "data/phantoms/recovery/m1-005-legacy-materialized37.tsv";
+	private static final String MATERIALIZED_HEADER = "M1_LEGACY_MATERIALIZED_37_V1";

## 46 af15a1c21ad0d178790a14f2892148f2ee386b9f Recover verifiable M1 historical pending state
+	public PhantomBackgroundCatchupState reopenUnplanned(long nextKnowledgeGeneration, long nextTopologyGeneration, Hashes nextHashes)
+		if (((status != Status.RUNNING) && (status != Status.FAILED_REPLAN_REQUIRED)) || (goalId <= 0) || (cursorEpochMinute != fromEpochMinute) || (intervalOrdinal != 0))
+		return new PhantomBackgroundCatchupState(Status.PENDING, requestId, deterministicSeed, fromEpochMinute, targetEpochMinute, cursorEpochMinute, Math.addExact(planOrdinal, 1), 0, generation, nextKnowledgeGeneration, nextTopologyGeneration, 0, 0, "", modelVersion, nextHashes, "");
+	/** Uses the ordinary historical identity lease and transaction receipt to finish a pending commit. */
+	public OperationResult reconcileHistoricalPending(long profileId, PhantomGoal goal, long generation, long nextOrdinal)
+		if (!claim.acquired()) { return claim.failure(); }
+			return ((claim.state().state() == State.READY) || (claim.state().state() == State.DEAD)) ? OperationResult.success("background.verify_pending_reconciled") : OperationResult.replan("background.state_invalid");
-				return fail(profileId, current, "catchup.runtime_state_or_goal_conflict");
+				if (!restored.successful()) { return restored; }
+				if (!remaining.isEmpty()) { return Result.rejected(ResultStatusCode.REPLAN_REQUIRED, remaining, current); }
+		if (state == null) { return "catchup.recovery.background_missing"; }
+		if ((profile == null) || (profile.characterObjectId() == null) || (state.identity().profileId() != profileId) || (state.identity().characterObjectId() != profile.characterObjectId())) { return "catchup.recovery.background_state_invalid"; }
+		if (goal == null) { return "catchup.recovery.goal_missing"; }
+		if (goal.goal().goalId() != current.state().goalId()) { return "catchup.recovery.goal_id_mismatch"; }
+		if (goal.goal().revision() != current.state().goalRevision()) { return "catchup.recovery.goal_revision_mismatch"; }
+		if (goal.goal().status() != PhantomGoalStatus.ACTIVE) { return "catchup.recovery.goal_status_invalid"; }
+		if (!PhantomBackgroundGoalSpec.GOAL_TYPE.equals(goal.goal().goalType()) && !PhantomBackgroundGoalSpec.HISTORICAL_IDLE_GOAL_TYPE.equals(goal.goal().goalType())) { return "catchup.recovery.goal_type_invalid"; }
+		if ((state.state() != PhantomBackgroundState.State.READY) && (state.state() != PhantomBackgroundState.State.DEAD)) { return "catchup.recovery.background_state_invalid"; }
+		return "";
+		if (missing.isEmpty()) { return Result.success(current, 0); }
+			case "catchup.recovery.background_missing" -> restored = refreshCanonicalBaseline(profileId, current);
+			case "catchup.recovery.goal_missing" -> restored = restoreMissingGoal(profileId, current, state);
+			case "catchup.recovery.goal_revision_mismatch" -> restored = replanStaleGoal(profileId, current, state, goal);
+			case "catchup.recovery.background_state_invalid" ->

## 50 0903733363514df5433808e8faf00b2f7a5a3078 Add owned LocalPlay synthetic M1 server lane and exact legacy quarantine
+# Explicit private LocalPlay test character; startup never spawns it.
+EnableLocalPlaySyntheticHuman = False
+LocalPlaySyntheticCharacterObjectId = 0
+LocalPlaySyntheticCharacterName =
-		final String value = new ConfigReader(CONFIG_FILE).getValue("EnableLocalPlayPilot");
+		final String value = reader.getValue("EnableLocalPlayPilot");
+			_syntheticObjectId = Integer.parseInt(reader.getValue("LocalPlaySyntheticCharacterObjectId"));
+			_syntheticName = reader.getValue("LocalPlaySyntheticCharacterName");
+			_syntheticEnabled = "True".equalsIgnoreCase(reader.getValue("EnableLocalPlaySyntheticHuman")) && (_syntheticObjectId > 0) && (_syntheticName != null) && !_syntheticName.isBlank();
+	public static boolean isSyntheticEnabled() { return _syntheticEnabled; }
+	public static int syntheticObjectId() { return _syntheticObjectId; }
+	public static String syntheticName() { return _syntheticName; }
+package org.l2jmobius.gameserver.localplay;
+public final class LocalPlayM1LegacyQuarantine
+	private LocalPlayM1LegacyQuarantine() {}
+		if (!Files.exists(file)) { return; }
+		if (!LocalPlayPilotService.safeDirectory(root) || !LocalPlayPilotService.privateAcl(root) || !LocalPlayPilotService.privateAcl(file) || Files.isSymbolicLink(file) || !Files.isRegularFile(file) || (Files.size(file) > 16384)) { throw new IllegalArgumentException("M1_QUARANTINE_PRIVATE_GUARD"); }
+		return (witness != null) && (witness.profileId() == profileId) && (witness.objectId() == objectId) && (witness.rowVersion() == rowVersion) && witness.payloadSha256().equals(payloadHash) && witness.canonicalSha256().equals(canonicalHash) ? Decision.KNOWN_LEGACY_FAIL_CLOSED : Decision.UNKNOWN_INCONSISTENT;
+					if (!row.next() || (row.getBytes(3) == null)) { return Decision.ELIGIBLE; }
+					if (state.state() != PhantomBackgroundState.State.INCONSISTENT) { return Decision.ELIGIBLE; }
+					if (witness == null) { return Decision.UNKNOWN_INCONSISTENT; }
+					if ((org.l2jmobius.gameserver.model.World.getInstance().findObject(row.getInt(1)) != null) || (org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(row.getInt(1)) != null) || org.l2jmobius.gameserver.taskmanagers.PlayerAutoSaveTaskManager.getInstance().containsObjectId(row.getInt(1))) { return Decision.UNKNOWN_INCONSISTENT; }
+					return attested(witness, profileId, row.getInt(1), row.getLong(2), hash(payload), canonicalHash(connection, profileId, row.getInt(1), state.identity().classIndex()));
+		try (var query = connection.prepareStatement("SELECT c.charId,c.classid,c.race,c.level,c.exp,c.sp,c.expBeforeDeath,c.curHp,c.maxHp,c.curMp,c.maxMp,c.curCp,c.maxCp,c.x,c.y,c.z,c.heading,c.online,s.class_index,s.class_id,s.level,s.exp,s.sp FROM characters c LEFT JOIN character_subclasses s ON s.charId=c.charId AND s.class_index=? WHERE c.charId=?"))

## 51 9050c5d9d910c004fd42cef961ad2d89e52aba45 Reject retired Player autosave callbacks after headless cleanup
+		// Check headless first: cleanup marks offline before detach, so a retired callback cannot pass both guards.
+		if (hasHeadlessOutboundSession() || !isOnline())

## 53 3f9d79a42436f488b3d9db9fb8898cf3e69abd66 Guard native maxima refresh after background level changes
-			loaded = refreshDeadNativeVitals(profileId, player, loaded);
+			loaded = refreshNativeVitals(profileId, player, loaded);
-	/** Native maxima are derived on DEAD Player load; preserve every durable reward/death fact. */
-	private PhantomBackgroundTransaction.Result refreshDeadNativeVitals(long profileId, Player player, PhantomBackgroundTransaction.Result loaded)
+	/** Native maxima are derived on Player load after background level changes; preserve durable facts. */
+	private PhantomBackgroundTransaction.Result refreshNativeVitals(long profileId, Player player, PhantomBackgroundTransaction.Result loaded)
+		// Native current-vitals setters share this monitor; regeneration cannot race capture/store.
+			return refreshNativeVitalsLocked(profileId, player, loaded);
+	private PhantomBackgroundTransaction.Result refreshNativeVitalsLocked(long profileId, Player player, PhantomBackgroundTransaction.Result loaded)
-		if ((state.state() != State.DEAD) || !state.hashes().equals(_authority.hashes())) { return loaded; }
+		if (((state.state() != State.DEAD) && (state.state() != State.READY)) || !state.hashes().equals(_authority.hashes())) { return loaded; }
+		if ((state.state() == State.READY) && ((state.vitals().currentHp() > player.getMaxHp()) || (state.vitals().currentMp() > player.getMaxMp()))) { return loaded; }
+		if (!captured.vitals().equals(vitals)) { return loaded; }
+		if (!captured.inventory().objects().equals(state.inventory().objects()) || !captured.autoGetSkills().equals(state.autoGetSkills())) { return loaded; }

## 55 ff5cb16467712f1e5d21c6188e2dff8e6f213476 Attest exact pre-9050 M1 observer prefix quarantine V2
+		return Map.copyOf(values);
-		return (witness != null) && (witness.profileId() == profileId) && (witness.objectId() == objectId) && (witness.rowVersion() == rowVersion) && witness.payloadSha256().equals(payloadHash) && witness.canonicalSha256().equals(canonicalHash) ? Decision.KNOWN_LEGACY_FAIL_CLOSED : Decision.UNKNOWN_INCONSISTENT;
+		if ((witness == null) || (witness.profileId() != profileId) || (witness.objectId() != objectId) || (witness.rowVersion() != rowVersion) || !witness.payloadSha256().equals(payloadHash) || !witness.canonicalSha256().equals(canonicalHash)) { return Decision.UNKNOWN_INCONSISTENT; }
+		if ((org.l2jmobius.gameserver.model.World.getInstance().findObject(objectId) != null) || (org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(objectId) != null) || org.l2jmobius.gameserver.taskmanagers.PlayerAutoSaveTaskManager.getInstance().containsObjectId(objectId)) { return Decision.UNKNOWN_INCONSISTENT; }
+		return Decision.KNOWN_PREFIX_FAIL_CLOSED;
-				if (quarantine == LocalPlayM1LegacyQuarantine.Decision.UNKNOWN_INCONSISTENT) { return new Outcome("REJECTED", "UNKNOWN_INCONSISTENT:" + candidate.profileId(), Map.of("legacySkips", String.join(";", legacySkips))); }
-				if (quarantine == LocalPlayM1LegacyQuarantine.Decision.KNOWN_LEGACY_FAIL_CLOSED)
-					if (legacySkips.size() >= 8) { return new Outcome("REJECTED", "KNOWN_LEGACY_SKIP_CAP", Map.of("legacySkips", String.join(";", legacySkips))); }
+					if ("REJECTED".equals(quarantineOutcome.status())) { return quarantineOutcome; }
+	private static Outcome quarantineCandidate(long profileId, LocalPlayM1LegacyQuarantine.Decision decision, List<String> skips)
+		if (decision == LocalPlayM1LegacyQuarantine.Decision.ELIGIBLE) { return null; }
+		if (decision == LocalPlayM1LegacyQuarantine.Decision.UNKNOWN_INCONSISTENT) { return new Outcome("REJECTED", "UNKNOWN_INCONSISTENT:" + profileId, Map.of("legacySkips", String.join(";", skips))); }
+		if (skips.size() >= 8) { return new Outcome("REJECTED", "KNOWN_PREFIX_SKIP_CAP", Map.of("legacySkips", String.join(";", skips))); }
+		return Outcome.of("SKIPPED", "KNOWN_PREFIX_FAIL_CLOSED");

## 57 b4f1f03d7407897f50bd3131f40d6e19011116e1 Make owned Phantom stores crash consistent with native snapshots
+package org.l2jmobius.gameserver.localplay;
+import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
+/** LocalPlay diagnostic only: bounded synchronous journal, with no gameplay authority. */
+public final class LocalPlayPhantomStoreJournal
+	private static final Logger LOGGER = Logger.getLogger(LocalPlayPhantomStoreJournal.class.getName());
+	private LocalPlayPhantomStoreJournal() {}
+		if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) { return; }
+		if (!LocalPlayPilotService.safeDirectory(root) || !LocalPlayPilotService.privateAcl(root)) { throw new IllegalArgumentException("OWNED_STORE_JOURNAL_PRIVATE_GUARD"); }
+			if (Files.exists(file, LinkOption.NOFOLLOW_LINKS) && (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || !LocalPlayPilotService.privateAcl(file) || (Files.size(file) > MAX_BYTES + 8192))) { throw new IllegalArgumentException("OWNED_STORE_JOURNAL_FILE_GUARD"); }
+		if (_root == null) { return 0; }
+		return sequence;
+		if ((_root == null) || (sequence == 0)) { return; }
+		if (_root == null) { return; }
+		if (state == null) { return "UNKNOWN"; }
+		return state.state() + "/" + state.progress() + "/" + state.vitals() + "/" + state.position() + "/inventory=" + state.inventory().canonicalHash() + "/payload=" + PhantomBackgroundTransaction.payloadDigest(new PhantomBackgroundStateCodec().encode(state));
+		return player.getCurrentHp() + "/" + player.getMaxHp() + "," + player.getCurrentMp() + "/" + player.getMaxMp() + "," + player.getCurrentCp() + "/" + player.getMaxCp() + "," + player.getX() + "," + player.getY() + "," + player.getZ() + "," + player.getHeading();
+			if (Files.exists(file, LinkOption.NOFOLLOW_LINKS) && (Files.isSymbolicLink(file) || !LocalPlayPilotService.privateAcl(file))) { throw new IllegalStateException("OWNED_STORE_JOURNAL_PATH_CHANGED"); }
+				for (Path previous : java.util.List.of(older, oldest)) { if (Files.exists(previous, LinkOption.NOFOLLOW_LINKS) && (Files.isSymbolicLink(previous) || !LocalPlayPilotService.privateAcl(previous))) { throw new IllegalStateException("OWNED_STORE_JOURNAL_ROTATION_CHANGED"); } }
+			final String receipt = intent == null ? "UNKNOWN" : "epoch=" + intent.materializedAtNanos() + ",background=" + intent.previousState() + "/" + (intent.preparedRowVersion() - 1) + "/" + intent.previousPayloadHash() + ",before=" + projection(intent.before()) + ",after=" + projection(intent.after()) + ",skills=" + intent.skillsHash();
+			final String line = Instant.now() + "\tpid=" + ProcessHandle.current().pid() + "\tseq=" + sequence + "\tprofile=" + profileId + "\tobject=" + player.getObjectId() + "\tkind=" + kind + "\tevent=" + event + "\tbefore=" + before + "\tafter=" + after + "\treceipt=" + receipt + "\tcompleted=" + projection(completed) + "\tstatus=" + status + "\tworld=" + (World.getInstance().getPlayer(player.getObjectId()) == player) + "\tidentity=" + PhantomIdentityLeaseRegistry.getInstance().getOwnerSnapshot(player.getObjectId()) + "\tautosave=" + PlayerAutoSaveTaskManager.getInstance().contains(player) + "\n";
+			LocalPlayPhantomStoreJournal.configure(_runtimeRoot);
+		default Object ownerKey() { return this; }
+		void afterStore(boolean nativeStoreCompleted);
+		if ((_ownedStoreBoundary != null) || (_client != null) || (org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(getObjectId()) != org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerKind.PHANTOM))

## 58 ea56bef1381c05b70f6e5da72e07004d53655be7 fix(phantoms): close M1 owned recovery and visible executor handoff
+	/** Explicit diagnostic scope; never enables another materialization epoch. */
+		return (_root != null) && java.util.Objects.equals(_selected.get(profileId), epoch);
+	public static OperationCounts operationCounts() { return new OperationCounts(SNAPSHOTS.get(), ENCODES.get(), HASHES.get(), OPENS.get(), FORCES.get()); }
-		if (_root == null) { return 0; }
+		if ((intent == null) || !enabledFor(profileId, intent.materializedAtNanos())) { return 0; }
-		if ((_root == null) || (sequence == 0)) { return; }
+		if ((intent == null) || !enabledFor(profileId, intent.materializedAtNanos()) || (sequence == 0)) { return; }
-		if (_root == null) { return; }
+		if ((intent == null) || !enabledFor(profileId, intent.materializedAtNanos())) { return; }
+		LocalPlayPhantomStoreJournal.select(worldPresent && (target.materializedAtNanos() > 0) ? Map.of(_envelopeProfileId, target.materializedAtNanos()) : Map.of());
+		LocalPlayPhantomStoreJournal.select(Map.of());
+		default boolean hasPending() { return false; }
+	public boolean hasPendingOwnedStore() { final var boundary = _ownedStoreBoundary; return (boundary != null) && boundary.hasPending(); }
+		if ((boundary == null) || (boundary.ownerKey() != ownerKey)) { return false; }
+		if (!boundary.hasPending()) { return true; }
+		if (snapshot == null) { return !boundary.hasPending(); }
+		try { storeNative(false, snapshot); completed = true; }
+		return !boundary.hasPending();
+			if (snapshot != null) { throw new IllegalStateException("OWNED_STORE_NATIVE_BASE_FAILED", e); }
+			if (snapshot != null) { throw new IllegalStateException("OWNED_STORE_NATIVE_SUBCLASS_FAILED", e); }
-						_visibleAutoPlay.stop(profileId);
-						return false;
-					return _visibleAutoPlay.start(profileId, goal);
-				}, _visibleAutoPlay::running, (profileId, goal) ->

## 60 461a4abe32be4aa08532b8417a6147684a8889c6 fix(phantoms): expose bounded cleanup failure diagnostics
+			result.put(prefix + "identityLeaseRetained", Boolean.toString(entry.identityLeaseRetained()));
+			if (bytes > 48 * 1024) { return true; }
+		return false;
-			actor.dematerializedAtNanos());
+			actor.dematerializedAtNanos(),
-	public record MaterializationSnapshot(long profileId, int characterObjectId, State state, boolean playerRetained, boolean identityLeaseRetained, boolean outboundAttached, boolean actionAdmissionOpen, int admittedActionCount, boolean worldPresent, long materializedAtNanos, long dematerializedAtNanos)
+	public record MaterializationSnapshot(long profileId, int characterObjectId, State state, boolean playerRetained, boolean identityLeaseRetained, boolean outboundAttached, boolean actionAdmissionOpen, int admittedActionCount, boolean worldPresent, long materializedAtNanos, long dematerializedAtNanos,
+		PhantomMaterializedPlayer.CleanupPhase cleanupPhase, PhantomMaterializedPlayer.CleanupPhase cleanupFailurePhase, String cleanupFailureClass, String cleanupFailureMessage, long cleanupFailureSequence, int cleanupFailureAdmittedActionCount, String cleanupFailureCause)
+		NONE, ACTION_DRAIN, PRE_STORE, NATIVE_STORE, POST_STORE, PRE_DELETE, DELETE, POST_DELETE,
+		RELEASE_OUTBOUND, RELEASE_IDENTITY, POSTCONDITION, COMPLETE
+					_cleanupPhase = CleanupPhase.NATIVE_STORE;
+				_cleanupPhase = CleanupPhase.RELEASE_OUTBOUND;
+				_cleanupPhase = CleanupPhase.RELEASE_IDENTITY;
+		if (value == null) { return ""; }
+		return result.toString();
-			return new Snapshot(_objectId, _state, snapshotPlayer != null, _identityLease != null, _identityAttached, _outboundAttachment != null, _actionAdmissionOpen, _admittedActionCount, (snapshotPlayer != null) && (World.getInstance().getPlayer(_objectId) == snapshotPlayer), _materializedAtNanos, _dematerializedAtNanos);
+			return new Snapshot(_objectId, _state, snapshotPlayer != null, _identityLease != null, _identityAttached, _outboundAttachment != null, _actionAdmissionOpen, _admittedActionCount, (snapshotPlayer != null) && (World.getInstance().getPlayer(_objectId) == snapshotPlayer), _materializedAtNanos, _dematerializedAtNanos,
-	public record Snapshot(int objectId, State state, boolean playerRetained, boolean identityLeaseRetained, boolean identityAttached, boolean outboundAttached, boolean actionAdmissionOpen, int admittedActionCount, boolean worldPresent, long materializedAtNanos, long dematerializedAtNanos)
+	public record Snapshot(int objectId, State state, boolean playerRetained, boolean identityLeaseRetained, boolean identityAttached, boolean outboundAttached, boolean actionAdmissionOpen, int admittedActionCount, boolean worldPresent, long materializedAtNanos, long dematerializedAtNanos,

## 64 dd58a512c4cb9c6a5318d7320633a35ae849dbf0 experiment(phantoms): UNACCEPTED candidate007 / observation008 frozen snapshot
+	/** Observable submission for lifetime-owned native work; legacy wrappers retain their contract. */
+		return strictSubmission(() -> SCHEDULED_POOL.schedule(new StrictRunnableWrapper(runnable), validateDelay(delay), TimeUnit.MILLISECONDS));
+		strictSubmission(() -> { INSTANT_POOL.execute(new StrictRunnableWrapper(runnable)); return null; });
+		return strictSubmission(() -> SCHEDULED_POOL.scheduleAtFixedRate(new StrictRunnableWrapper(runnable), validateDelay(initialDelay), validateDelay(period), TimeUnit.MILLISECONDS));
+	private static final ThreadLocal<Integer> STRICT_SUBMISSION = new ThreadLocal<>();
+		try { return submission.get(); }
+				throw new RejectedExecutionException("Native executor is shut down");
+import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
+			PlayerNativeWork.schedule(_actor, target == null ? List.of() : List.of(target), "cast-bow-reuse", PlayerNativeWork.Semantics.CANCELLABLE, new CastTask(_actor, skill, target), (bowAttackEndTime - gameTime) * GameTimeTaskManager.MILLIS_IN_TICK);
+import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
+			PlayerNativeWork.probeCombat(_actor.asPlayer());
+			PlayerNativeWork.probeCombat(_actor.asPlayer());
+		PlayerNativeWork.probeCombat(_actor.asPlayer());
+		PlayerNativeWork.probeCombat(_actor.asPlayer());
+		PlayerNativeWork.probeCombat(_actor.asPlayer());
+		PlayerNativeWork.probeCombat(_actor.asPlayer());
+		if (args.containsKey("excludePreviouslySelectedProfileIds") && !"INITIAL".equals(stage)) { return Outcome.of("REJECTED", "INVALID_ARGUMENT"); }
+		if (reprepareInitial && !selectorExclusions.equals(_m1ExcludedProfileIds)) { return Outcome.of("REJECTED", "M1_SELECTOR_INPUT_CHANGED"); }
+		if (remainingNanos <= 0) { return Outcome.of("REJECTED", "M1_SCENE_DEADLINE"); }
-			if ((selected == null) || (selected.observedPosition() == null) || (selectedAdmission == null) || !selectedAdmission.admission().calendarOnline() || !selectedAdmission.admission().nextBoundary().isAfter(java.time.Instant.now().plusSeconds(240))) { return Outcome.of("REJECTED", "M1_SAME_TARGET_INVALIDATED"); }
+			if ((selected == null) || (selected.observedPosition() == null) || (selectedAdmission == null) || !selectedAdmission.admission().calendarOnline() || !selectedAdmission.admission().nextBoundary().isAfter(calendarHorizon)) { return Outcome.of("REJECTED", "M1_SAME_TARGET_INVALIDATED"); }
-				if ((admission == null) || !admission.admission().calendarOnline() || !admission.admission().nextBoundary().isAfter(java.time.Instant.now().plusSeconds(240)) || ((candidate.positionSource() == LocalPlayM1Observation.PositionSource.COMMITTED) && !"none".equals(admission.busyReason()))) { continue; }
+				if (selectorExclusions.contains(candidate.profileId()) || (admission == null) || !admission.admission().calendarOnline() || !admission.admission().nextBoundary().isAfter(calendarHorizon) || ((candidate.positionSource() == LocalPlayM1Observation.PositionSource.COMMITTED) && !"none".equals(admission.busyReason()))) { continue; }
+		if (selectorExclusions.contains(selected.profileId())) { return Outcome.of("REJECTED", "M1_PREVIOUS_TARGET_EXCLUDED"); }

## 67 ffc0b97e2eeccc42b362418524f5ba4829e65f46 phantom(task-010): allowlisted real-client pilot auto-attach
+EnableLocalPlayPilotAutoAttach = False
+LocalPlayPilotAutoAttachCharacters =
+import java.util.Locale;
+		loadAutoAttach(reader.getValue("EnableLocalPlayPilotAutoAttach"), reader.getValue("LocalPlayPilotAutoAttachCharacters"));
+			return;
+				return;
+			characters.add(name.toLowerCase(Locale.ROOT));
+		return _autoAttachEnabled;
+		return _autoAttachEnabled && (name != null) && _autoAttachCharacters.contains(name.toLowerCase(Locale.ROOT));
+			if (!LocalPlayPilotConfig.isEnabled() || !LocalPlayPilotConfig.isAutoAttachEnabled() || (player == null) || !LocalPlayPilotConfig.isAutoAttachCharacter(player.getName()) || (_poller == null) || _poller.isCancelled() || _poller.isDone() || !mailboxSafe() || !realClient(player))
+				return false;
+				return false;
+			if ((_lease != null) && (_lease.state() != LocalPlayPilotLease.State.OFF))
+				return (_player == player) && (_client == player.getClient()) && sessionValid();
+			if (_lease != null)
+			final LocalPlayPilotLease lease = new LocalPlayPilotLease(nonce, player.getName(), _pid, _startTicks, System.nanoTime() + CONSENT_NANOS, System::nanoTime);
+			if (!lease.arm(nonce, player.getName(), player.getAccountName(), player.getObjectId(), player.getClient(), _pid, _startTicks, CONSENT_NANOS))
+				return false;
+			_lease = lease;
+			_actions = new LocalPlayPilotActions(player);
+			return true;
+			return false;
+		org.l2jmobius.gameserver.localplay.LocalPlayPilotService.getInstance().onRealClientEntered(player);

## 69 8457b90723e3c8ff6b419080bb2f84bfd80d6638 phantom(task-011): unblock native-context materialization demand
-			if (isNativeContextFailure(claimed.state().failureReason())) { return recoverNativeContext(profileId, claimed); }
+			if (requiresNativeMaterialization(claimed.state().failureReason())) { return recoverNativeContext(profileId, claimed); }
-		if ((current.state().status() == Status.FAILED_REPLAN_REQUIRED) && isNativeContextFailure(current.state().failureReason()))
+		if ((current.state().status() == Status.FAILED_REPLAN_REQUIRED) && requiresNativeMaterialization(current.state().failureReason()))
-			if (isNativeContextFailure(operation.reason())) { return Result.rejected(ResultStatusCode.RETRY, operation.reason(), observed); }
+			if (requiresNativeMaterialization(operation.reason())) { return Result.rejected(ResultStatusCode.RETRY, operation.reason(), observed); }
-	private static boolean isNativeContextFailure(String reason)
+	public static boolean requiresNativeMaterialization(String reason)
-		if ((current.state().status() != Status.FAILED_REPLAN_REQUIRED) || !isNativeContextFailure(current.state().failureReason())) { return Result.rejected(ResultStatusCode.REPLAN_REQUIRED, "catchup.native_context.failure_not_owned", current); }
+		if ((current.state().status() != Status.FAILED_REPLAN_REQUIRED) || !requiresNativeMaterialization(current.state().failureReason())) { return Result.rejected(ResultStatusCode.REPLAN_REQUIRED, "catchup.native_context.failure_not_owned", current); }
+			// Foreground supplies the missing native context; the pending history remains fenced.
+			if (materializationDue && _currentDemand.test(profileId) && !entry._terminal && (_wakeFailure == null) && _populationPlanApplied && _inventoryReady && !_metadataDraining
+				&& (historical.status() != Status.COMPLETE) && PhantomHistoricalBackgroundService.requiresNativeMaterialization(entry._lastReportedFailure))
+				entry._materializationDemand = true;
+				return new DueReconciliation(true, 0, "ecology.native_materialization_required");
+		if ((result.status() == ResultStatusCode.REPLAN_REQUIRED) && PhantomHistoricalBackgroundService.requiresNativeMaterialization(reason)) { deferRetry(profileId); return; }

## 73 c23915df10239bfab15ae49276e14833268b9afc Fix committed inventory projection at Phantom native arrival
+		// Read actual native counts/locations; the caller still verifies objects and full inventory hash.
+		final Tracking tracking = nativePersistence && (previous != null) && ((previous.state() == State.READY) || (previous.state() == State.DEAD))
+		return tracking(player, List.copyOf(mutableItemIds));
-		return new Tracking(List.copyOf(mutableItemIds), objects);
+		return new Tracking(mutableItemIds, objects);