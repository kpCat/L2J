/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerCreationInitializer;
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence;
import org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.groups.PartyInvitationDelivery.PartyInvitation;
import org.l2jmobius.gameserver.model.groups.PartyInvitationDelivery.PreparationOutcome;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.InvitationIdentity;
import org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.PhantomScheduler;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityWorkSink;
import org.l2jmobius.gameserver.phantoms.activity.PhantomCompositeSchedulerControlPort;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundPlanner;
import org.l2jmobius.gameserver.phantoms.background.L2jPhantomBackgroundAuthority;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundService;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCompetitionRegistry;
import org.l2jmobius.gameserver.phantoms.background.PhantomOrdinaryDeathRecovery;
import org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel;
import org.l2jmobius.gameserver.phantoms.actions.PhantomEngagementPlanner;
import org.l2jmobius.gameserver.phantoms.combat.*;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService;
import org.l2jmobius.gameserver.phantoms.party.*;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecyclePort;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecycleBridge;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.progression.*;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole;

/** TEST094 only: once-declared fixtures, real shared owners, then read-only gameplay observation. */
public final class PhantomAutonomousParty094Native
{

	private PhantomAutonomousParty094Native() { }
	private static java.time.Instant admissionExpires;

	public static void main(String[] args)
	{
		int exit = 1;
		try
		{
			check(args.length == 1 && List.of("files", "native", "restart", "postflight", "route").contains(args[0]), "ACTION");
			final Path module = Path.of(System.getProperty("phantom.module.root"));
			if ("files".equals(args[0])) { PhantomTask094Lane.validateNative(module, Path.of(System.getProperty("phantom.test.config"))); }
			else if ("postflight".equals(args[0])) { PhantomTask094Lane.postflightNative(module, Path.of(System.getProperty("phantom.test.config"))); }
			else if ("route".equals(args[0])) { diagnoseNativeRoute(module, false); }
			else { run(module, "restart".equals(args[0])); }
			exit = 0;
		}
		catch (Throwable failure)
		{
			// SQL/driver messages can contain credentials. Preserve type, phase and source frames only.
			System.out.println("TASK094_NATIVE_FAIL type=" + failure.getClass().getName() + " key=" + (failure.getMessage() != null && failure.getMessage().matches("TASK094_[A-Z_]+") ? failure.getMessage() : "WITHHELD"));
			printFailure(failure, java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>()));
		}
		finally
		{
			if (DatabaseFactory.isInitialized()) { ThreadPool.shutdown(); DatabaseFactory.close(); }
		}
		System.exit(exit);
	}

	private static void printFailure(Throwable failure, java.util.Set<Throwable> seen)
	{
		if (failure == null || !seen.add(failure)) { return; }
		System.out.println("TASK094_CAUSE type=" + failure.getClass().getName() + " key=" + (failure.getMessage() != null && failure.getMessage().matches("TASK094_[A-Z_]+") ? failure.getMessage() : "WITHHELD"));
		for (var frame : failure.getStackTrace()) { if (frame.getClassName().startsWith("org.l2jmobius.")) { System.out.println("TASK094_FRAME " + frame); } }
		for (var suppressed : failure.getSuppressed()) { System.out.println("TASK094_SUPPRESSED"); printFailure(suppressed, seen); }
		printFailure(failure.getCause(), seen);
	}

	/** Exact runtime geometry only. No Phantom materialization, fixtures, claims or Player moves. */
	private static boolean diagnoseNativeRoute(Path module, boolean alreadyInitialized) throws Exception
	{
		final Path candidate = Path.of(System.getProperty("phantom.task094.candidate"));
		if (!alreadyInitialized) { PhantomHeadlessPlayerTestEnvironment.initializeNativeInfrastructure(new PhantomTestContext(940094, module, candidate.resolve("reports"))); }
		try (var source = PhantomBackgroundSuite.ProductionAuthorityFixture.start())
		{
			final var profiles = PhantomProfileRepository.open();
			final var fixture = read(PhantomTask094Lane.nativeRoot().resolve("fixture.properties"));
			final long id = Long.parseLong(fixture.getProperty("profiles").split(",")[2]);
			final var root = new PhantomGoalStateStore(profiles).load(id).orElseThrow().goal();
			check(org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec.canonicalHistoricalIdle(id, root), "ROUTE_DIAG_ORDINARY_ROOT");
			final var anchor = source.topology().findAnchor(root.selectedAnchor().key()).orElseThrow();
			org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint from;
			int actualLevel; double actualHp;
			try (var connection = DatabaseFactory.getConnection())
			{
				connection.setReadOnly(true); check(PhantomTask094Lane.SCHEMA.equals(connection.getCatalog()), "ROUTE_DIAG_SCHEMA");
				try (var query = connection.prepareStatement("SELECT x,y,z,level,curHp FROM characters WHERE charId=? AND account_name='task094farm'"))
				{
					query.setInt(1, profiles.find(id).orElseThrow().characterObjectId());
					try (var rows = query.executeQuery()) { check(rows.next(), "ROUTE_DIAG_ACTOR_ROW"); from = new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(rows.getInt(1), rows.getInt(2), rows.getInt(3), 0); actualLevel = rows.getInt(4); actualHp = rows.getDouble(5); check(!rows.next(), "ROUTE_DIAG_SINGLE_ROW"); }
				}
			}
			if (actualHp <= 0)
			{
				final var recovery = source.authority().canonicalRecoveryPosition(from.x(), from.y(), from.z(), from.instanceId(), 0).orElseThrow();
				from = new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(recovery.x(), recovery.y(), recovery.z(), recovery.instanceId());
				System.out.println("TASK094_ROUTE_VIRTUAL_RECOVERY_SOURCE point=" + from + " actualSqlLevel=" + actualLevel + " noPlayerCreated=true");
			}
			final var to = new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(anchor.point().x(), anchor.point().y(), anchor.point().z(), 0);
			final var backend = new org.l2jmobius.gameserver.phantoms.navigation.L2jNavigationBackend();
			final var policy = org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy.productionDefaults();
			final var output = new StringBuilder("mode\tepisode\tfrom\tstatus\tnodes\tedges\tqueries\tlabels\twaypoints\tlast\n");
			long now = System.nanoTime();
			var request = new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest(id, from, to, now, now + policy.defaultRequestDeadlineNanos(), 100_000, routeThreats(from, actualLevel));
			final var full = backend.findWalkCorridor(request, new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCancellationToken(), policy, System::nanoTime);
			output.append("full\t0\t").append(from).append('\t').append(full.status()).append('\t').append(full.nodes()).append('\t').append(full.edges()).append('\t').append(full.queries()).append('\t').append(full.labels()).append('\t').append(full.points().size()).append('\t').append(full.points().isEmpty() ? "none" : full.points().getLast()).append('\n');
			final var travel = source.authority().travelQuery(source.topology());
			final var ingress = PhantomVisibleFarmTravel.idleIngress(travel, new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(from.x(), from.y(), from.z(), from.instanceId()), anchor.id(), policy.maximumRouteDistance(), 0);
			final var targets = new ArrayList<org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint>();
			if (ingress != null)
			{
				final var routeAnchors = new ArrayList<String>(List.of(ingress.id()));
				routeAnchors.add(anchor.id());
				for (var targetId : routeAnchors)
				{
					final var position = L2jPhantomBackgroundAuthority.canonicalCommittedAnchorPosition(source.topology().findAnchor(targetId).orElseThrow(), 0).orElseThrow();
					targets.add(new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(position.x(), position.y(), position.z(), position.instanceId()));
				}
			}
			else { targets.add(to); }
			check(targets.getLast().equals(to), "ROUTE_DIAG_EXACT_FINAL");
			System.out.println("TASK094_ROUTE_DIAG_INGRESS anchor=" + (ingress == null ? "none" : ingress.id()) + " nativeItineraryTargets=" + targets.size() + " sourceEdgesUnchanged=true");
			final var visited = new java.util.HashSet<org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint>(); double distance = 0;
			int targetIndex = 0; boolean intakeReached = false;
			for (int index = 0; index < 64 && targetIndex < targets.size(); index++)
			{
				visited.add(from); now = System.nanoTime(); request = new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest(id, from, targets.get(targetIndex), now, now + policy.defaultRequestDeadlineNanos(), 100_000, routeThreats(from, actualLevel));
				final var result = backend.findWalkEpisode(request, new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCancellationToken(), policy, System::nanoTime, backend, visited);
				output.append("virtual_episode\t").append(index).append('\t').append(from).append('\t').append(result.status()).append('\t').append(result.nodes()).append('\t').append(result.edges()).append('\t').append(result.queries()).append('\t').append(result.labels()).append('\t').append(result.points().size()).append('\t').append(result.points().isEmpty() ? "none" : result.points().getLast()).append('\n');
				check(result.nodes() <= 128 && result.edges() <= 512 && result.queries() <= 32 && result.labels() <= 4096, "ROUTE_DIAG_SHARED_CAPS");
				if (result.status() != org.l2jmobius.gameserver.phantoms.navigation.PhantomWalkCorridorPlanner.Status.FOUND) { break; }
				double added = 0; var prior = from;
				for (var point : result.points()) { added += prior.distanceTo(point); prior = point; }
				if (distance + added > 100_000) { System.out.println("TASK094_ROUTE_DIAG_DISTANCE_REJECT beforeVirtualRelease=true"); break; }
				for (var point : result.points()) { distance += from.distanceTo(point); from = point; }
				if (from.distanceTo(to) <= 1200 && Math.abs((long) from.z() - to.z()) <= 200) { intakeReached = true; break; }
				if (from.equals(targets.get(targetIndex))) { targetIndex++; visited.clear(); }
			}
			writeNew(candidate.resolve(alreadyInitialized ? "native-route-preflight.tsv" : "route-native-geometry.tsv"), output.toString());
			System.out.println("TASK094_EXACT_RUNTIME_GEOMETRY_DIAGNOSTIC full=" + full.status() + " virtualGoalReached=" + from.equals(to) + " virtualPartyIntakeReached=" + intakeReached + " projectedDistance=" + Math.round(distance) + " PhantomPlayers=0 PlayerMoves=0 fixtureWrites=0 claimWrites=0 earnedWrites=0 nativeGameplayNotClaimed=true");
			return intakeReached || from.equals(to);
		}
	}
	private static List<org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest.PveThreat> routeThreats(org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint from, int level)
	{
		return PhantomVisibleFarmTravel.pveThreats(from, level, World.getInstance().getVisibleObjects().stream().filter(Monster.class::isInstance).map(Monster.class::cast).toList());
	}

	private static void run(Path module, boolean restart) throws Exception
	{
		final Path stage = PhantomTask094Lane.nativeRoot(), candidate = Path.of(System.getProperty("phantom.task094.candidate"));
		final Path fixture = stage.resolve("fixture.properties"), complete = candidate.resolve("native.complete");
		if (restart)
		{
			check(Files.isRegularFile(complete), "RESTART_REQUIRES_NATIVE_PASS");
			final var prior = read(complete);
			check(candidate.toString().equals(prior.getProperty("candidate")) && !Long.toString(ProcessHandle.current().pid()).equals(prior.getProperty("pid")), "RESTART_SAME_JAR_NEW_JVM");
		}
		PhantomHeadlessPlayerTestEnvironment.initializeNativeInfrastructure(new PhantomTestContext(940094, module, candidate.resolve("reports")));
		initializeTargetHandlers(candidate);
		admissionExpires = PhantomTask094Lane.nativeAdmissionExpiry();
		// Party intake now confirms a local core and a pending remote JOIN. A solo town-to-field
		// route is no longer a prerequisite; all native Geo/owner guards run at actual admission.
		checkContinue();
		try (var source = PhantomBackgroundSuite.ProductionAuthorityFixture.start();
			var previousSource = PhantomBackgroundSuite.ProductionAuthorityFixture.start(PhantomTask094Lane.previousTopology(module)))
		{
			final var profiles = PhantomProfileRepository.open();
			if (!Files.exists(fixture)) { check(!restart, "RESTART_NO_FIXTURE"); prepare(stage, source, profiles); }
			final var retained = read(fixture);
			final var ids = java.util.Arrays.stream(retained.getProperty("profiles").split(",")).map(Long::parseLong).toList();
			check(ids.size() == 4 && ids.stream().distinct().count() == 4, "FIXTURE_ROSTER");
			writeNew(candidate.resolve(restart ? "restart.before-load.tsv" : "native.before-load.tsv"), persisted(profiles, ids));
			if (restart)
			{
				check(Files.readString(candidate.resolve("native.persisted.tsv")).equals(persisted(profiles, ids)), "SAME_DB_PERSISTENCE_NO_DUPLICATE");
				check(Files.readString(candidate.resolve("native.party-components.tsv")).equals(persistedPartyComponents(profiles, ids)), "SAME_DB_PARTY_CLAIMS_AND_REGROUP_CLOCK_EXACT");
				System.out.println("TASK094_SAME_DB_PASS actors=4 exactDurableXP_SP_items_skills=true beforeMaterialization=true newJvm=true sameJar=true reseed=0");
				System.out.println("TASK094_SAME_DB_PARTY_CLOCK_PASS exactBytesAndRowVersions=true cooldownRetained=true beforeMaterialization=true");
			}
			if (!restart) { PhantomRawDeadStore094Checks.verify(source, profiles, ids, candidate); }
			try (var runtime = new Runtime(module, source, profiles, ids))
			{
				upgradeNativeAuthority(runtime, profiles, ids, previousSource, source, candidate.resolve(restart ? "restart.authority-upgrade.complete" : "native.authority-upgrade.complete"));
				final var corpses = new ArrayList<Long>();
				for (long id : ids) { if (storedVitals(profiles.find(id).orElseThrow().characterObjectId()).hp() == 0) { corpses.add(id); } }
				check(runtime.death.install(), "ORDINARY_DEATH_RECOVERY_INSTALL");
				for (long id : corpses)
				{
					loadActor(runtime, profiles, id);
					check(describeRecoveryMapping(source, World.getInstance().getPlayer(profiles.find(id).orElseThrow().characterObjectId())), "CANONICAL_NATIVE_TOWN_MISSING");
					rawDeadStoreProbe(runtime, profiles, id, candidate);
					// Establish the missing ordinary baseline through canonical STORE, preserving the saved corpse and loss.
					check(runtime.materialization.dematerialize(id).status() == PhantomMaterializationService.ResultStatus.SUCCESS, "CORPSE_BASELINE_STORE");
					checkContinue(); check(runtime.materialization.materialize(id).status() == PhantomMaterializationService.ResultStatus.SUCCESS, "CORPSE_BASELINE_RELOAD");
				}
				for (long id : corpses) { awaitRecovery(runtime, profiles, id); }
				final long supportId = ids.get(2);
				final var retainedClaims = ids.subList(0, 3).stream().map(runtime.party::claim).flatMap(java.util.Optional::stream).toList();
				final boolean retainedGroup = retainedClaims.size() == 3 && retainedClaims.stream().allMatch(claim -> java.util.Set.of(org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.StateStatus.LEADER, org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.StateStatus.MEMBER, org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.StateStatus.RECOVERING).contains(claim.state().status())
					&& claim.state().realMembers().isEmpty() && claim.state().groupId().equals(retainedClaims.getFirst().state().groupId()) && claim.state().groupGeneration() == retainedClaims.getFirst().state().groupGeneration() && claim.state().leaderManifestHash().equals(retainedClaims.getFirst().state().leaderManifestHash()) && PhantomAutonomousFarmParty.plannedProfiles(claim.state().objectiveRef()).containsAll(ids.subList(0, 3)));
				if (!restart && !retainedGroup)
				{
					// Probe each DD through the shared owner, then keep the field actors cold during rejoin.
					for (long id : List.of(ids.get(1), ids.get(0)))
					{
						if (runtime.materialization.find(id).isEmpty()) { loadActor(runtime, profiles, id); }
						awaitPartyIntakeReady(runtime, profiles, List.of(id), supportId, true);
						check(runtime.materialization.dematerialize(id).status() == PhantomMaterializationService.ResultStatus.SUCCESS, "INTAKE_PROBE_CANONICAL_STORE");
					}
					if (runtime.materialization.find(supportId).isEmpty()) { loadActor(runtime, profiles, supportId); }
					awaitPartyIntakeReady(runtime, profiles, List.of(supportId), supportId, true);
				}
				else
				{
					// Recovery may lawfully rebuild its native Party before this observer sees all peers.
					for (long id : ids.subList(0, 3)) { if (runtime.materialization.find(id).isEmpty()) { loadActor(runtime, profiles, id); } }
					if (restart) { restartSourcePresence(runtime, profiles, supportId, source); }
					runtime.startScheduler();
					awaitPartyIntakeReady(runtime, profiles, ids.subList(0, 3), supportId, false);
				}
				for (long id : ids.subList(0, 2)) { if (runtime.materialization.find(id).isEmpty()) { loadActor(runtime, profiles, id); } }
				awaitPartyIntakeReady(runtime, profiles, ids.subList(0, 3), supportId, false);
				final var players = new ArrayList<Player>();
				for (long id : ids) { players.add(World.getInstance().getPlayer(profiles.find(id).orElseThrow().characterObjectId())); }
				System.out.println("TASK094_PARTY_ACTIVE_REUSE_PASS actors=4 samePlayerOwnerEpoch=true repeatedAdmission=0 sealedRejected=true");
				final boolean guardianAvailable = players.get(3) != null && !players.get(3).isDead() && players.getFirst().calculateDistance2D(players.get(3)) <= 1200;
				final var scenePlayers = guardianAvailable ? players : players.subList(0, 3);
				final var sceneIds = guardianAvailable ? ids : ids.subList(0, 3);
				System.out.println("TASK094_NATIVE_SCENE_ROSTER requiredDD=2 requiredSupport=1 nativeActors=" + scenePlayers.size() + " optionalGuardianAvailable=" + guardianAvailable + " fixtureWrites=0");
				checkContinue(); writeNew(candidate.resolve(restart ? "restart.started" : "native.started"), "pid=" + ProcessHandle.current().pid() + "\nfixtureWritesAfterThis=0\n");
				observe(runtime, scenePlayers, sceneIds, restart);
				check(players.getFirst().getInstanceId() == players.get(2).getInstanceId() && players.getFirst().calculateDistance2D(players.get(2)) <= 1200, "PARTY_REQUIRED_PHYSICAL_REJOIN");
				writeNew(candidate.resolve(restart ? "restart.measurement.complete" : "native.measurement.complete"), "observedNativePartySharedKillEarnedRewards=true\nfixtureWritesDuringScene=0\n");
				// Persist the committed native party first; cold restart must exercise those claims.
				// Destructive-to-membership negative controls follow the restart's positive scene.
				if (restart) { controlledNegatives(runtime, profiles, scenePlayers, sceneIds, candidate); }
			}
			writeNew(candidate.resolve(restart ? "restart.persisted.tsv" : "native.persisted.tsv"), persisted(profiles, ids));
			writeNew(candidate.resolve(restart ? "restart.party-components.tsv" : "native.party-components.tsv"), persistedPartyComponents(profiles, ids));
			writeNew(candidate.resolve(restart ? "restart.complete" : "native.complete"), completionReceipt(candidate, ProcessHandle.current().pid()));
		}
		System.out.println("TASK094_" + (restart ? "RESTART" : "NATIVE") + "_PASS storeStop=true earnedOnly=true");
	}

	private static void prepare(Path stage, PhantomBackgroundSuite.ProductionAuthorityFixture source, PhantomProfileRepository profiles) throws Exception
	{
		try (var candidates = Files.list(stage))
		{
			check(candidates.filter(Files::isDirectory).noneMatch(path -> Files.exists(path.resolve("native.started")) || Files.exists(path.resolve("restart.started"))), "FIXTURE_ONLY_BEFORE_MEASUREMENT");
		}
		final var monsters = World.getInstance().getVisibleObjects().stream().filter(Monster.class::isInstance).map(Monster.class::cast)
			.filter(npc -> npc.isSpawned() && !npc.isRaid() && !npc.isDead() && npc.getInstanceId() == 0 && npc.getLevel() >= 50 && npc.getLevel() <= 55 && npc.getMaxHp() >= 3000 && npc.getMaxHp() <= 18000 && !npc.isAggressive() && GeoEngine.getInstance().hasGeo(npc.getX(), npc.getY()))
			.sorted(Comparator.comparingDouble(Monster::getMaxHp).thenComparingInt(Monster::getId)).toList();
		final var positions = source.topology().snapshot().anchors().stream().map(anchor -> Map.entry(anchor, L2jPhantomBackgroundAuthority.canonicalCommittedAnchorPosition(anchor, 0))).filter(entry -> entry.getValue().isPresent()).toList();
		final var fields = positions.stream().filter(entry -> entry.getKey().role() == PhantomTopologyAnchorRole.FARMING).filter(entry ->
		{
			final var point = entry.getValue().orElseThrow();
			if (!GeoEngine.getInstance().hasGeo(point.x(), point.y())) { return false; }
			if (positions.stream().filter(other -> { final var candidate = other.getValue().orElseThrow(); final int tolerance = other.getKey().validationTolerance(); return Math.hypot((long) point.x() - candidate.x(), (long) point.y() - candidate.y()) <= tolerance && Math.abs((long) point.z() - candidate.z()) <= tolerance; }).count() != 1) { return false; }
			return monsters.stream().anyMatch(npc -> Math.hypot((long) point.x() - npc.getX(), (long) point.y() - npc.getY()) <= 600 && Math.abs((long) point.z() - npc.getZ()) <= 150 && GeoEngine.getInstance().canMoveToTarget(point.x(), point.y(), point.z(), npc.getX(), npc.getY(), npc.getZ(), 0));
		}).map(entry ->
		{
			final var point = entry.getValue().orElseThrow();
			final var nearby = monsters.stream().filter(npc -> Math.hypot((long) point.x() - npc.getX(), (long) point.y() - npc.getY()) <= 600 && Math.abs((long) point.z() - npc.getZ()) <= 150 && GeoEngine.getInstance().canMoveToTarget(point.x(), point.y(), point.z(), npc.getX(), npc.getY(), npc.getZ(), 0)).findFirst().orElseThrow();
			return new FixtureField(entry.getKey().id(), point.x(), point.y(), point.z(), nearby.getMaxHp());
		}).sorted(Comparator.comparingDouble(FixtureField::localHp).thenComparing(FixtureField::anchorId)).limit(32).toList();
		check(!fields.isEmpty(), "SOURCE_CANONICAL_FIELD");
		final var initialField = fields.getFirst(); final int x = initialField.x(), y = initialField.y(), z = initialField.z();
		final boolean partial = Files.exists(stage.resolve("fixture.started"));
		if (partial)
		{
			final var expectedActors = new java.util.HashSet<Integer>();
			for (int classId : new int[] {2, 3, 17, 5})
			{
				final Path receipt = stage.resolve("fixture-actor-" + classId + ".properties");
				if (Files.exists(receipt)) { final var saved = read(receipt); final int actor = Integer.parseInt(saved.getProperty("actor")); check(profiles.find(Long.parseLong(saved.getProperty("profile"))).orElseThrow().characterObjectId() == actor && expectedActors.add(actor), "PARTIAL_PROFILE_RECEIPT"); }
			}
			try (var connection = DatabaseFactory.getConnection())
			{
				connection.setReadOnly(true);
				try (var query = connection.prepareStatement("SELECT accessLevel FROM accounts WHERE login='task094farm'"); var rows = query.executeQuery()) { check(rows.next() && rows.getInt(1) == -1 && !rows.next(), "PARTIAL_DISABLED_ACCOUNT"); }
				final var actualActors = new java.util.HashSet<Integer>();
				try (var query = connection.prepareStatement("SELECT charId FROM characters WHERE account_name='task094farm'"); var rows = query.executeQuery()) { while (rows.next()) { actualActors.add(rows.getInt(1)); } }
				check(actualActors.equals(expectedActors) && !expectedActors.isEmpty(), "PARTIAL_EXACT_ACTORS");
			}
			writeNew(Path.of(System.getProperty("phantom.task094.candidate")).resolve("fixture-completion.started"), "cause=canonical anchor rejected before measured scene; preserve existing actor and stock; no reseed\n");
		}
		else
		{
			writeNew(stage.resolve("fixture.started"), "TEST_FIXTURE once before measured gameplay; classes2,3,17,5 level55 B gear stock; native available class skills no FS/item-required grants\n");
			try (var connection = DatabaseFactory.getConnection(); var insert = connection.prepareStatement("INSERT INTO accounts(login,password,accessLevel,lastServer) VALUES('task094farm','DISABLED_NATIVE_TEST_ONLY',-1,1)")) { check(PhantomTask094Lane.SCHEMA.equals(connection.getCatalog()) && insert.executeUpdate() == 1, "FRESH_FIXTURE_ACCOUNT"); }
		}
		final var ids = new ArrayList<Long>(); final var goals = new PhantomGoalStateStore(profiles);
		final var planner = new PhantomHistoricalBackgroundPlanner(source.knowledge(), source.topology(), source.authority());
		FixtureField selectedField = null;
		for (int classId : new int[] {2, 3, 17, 5})
		{
			final var base = java.util.Arrays.stream(PlayerClass.values()).filter(value -> value.getId() == (classId == 17 ? 10 : 0)).findFirst().orElseThrow();
			final Path actorReceipt = stage.resolve("fixture-actor-" + classId + ".properties");
			final var saved = Files.exists(actorReceipt) ? read(actorReceipt) : null;
			if (saved != null) { writeNew(Path.of(System.getProperty("phantom.task094.candidate")).resolve("fixture-existing-" + classId + ".tsv"), persisted(profiles, List.of(Long.parseLong(saved.getProperty("profile"))))); }
			checkContinue(); final Player player = saved == null ? Player.create(PlayerTemplateData.getInstance().getTemplate(base), "task094farm", "Farm094C" + classId, new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false)) : Player.load(Integer.parseInt(saved.getProperty("actor")));
			check(player != null, "FIXTURE_CREATE");
			try
			{
				player.stopAllTasks();
				player.setOnlineStatus(true, false);
				final long id;
				if (saved != null)
				{
					id = Long.parseLong(saved.getProperty("profile"));
					final long expectedFixtureAdena = Math.max(0, PlayerConfig.STARTING_ADENA) + 5000;
					System.out.println("TASK094_PARTIAL_FIXTURE_FACTS actor=" + player.getObjectId() + " class=" + player.getActiveClass() + " level=" + player.getLevel() + " XP=" + player.getExp() + " expectedXP=" + player.getStat().getExpForLevel(55) + " SP=" + player.getSp() + " Adena=" + player.getAdena() + " expectedCreationPlusFixtureAdena=" + expectedFixtureAdena + " shots=" + player.getInventory().getInventoryItemCount(1465, -1));
					check(profiles.find(id).orElseThrow().characterObjectId() == player.getObjectId() && "task094farm".equals(player.getAccountName()) && player.getActiveClass() == classId && player.getLevel() == 55 && player.getExp() == player.getStat().getExpForLevel(55) && player.getSp() == 0 && player.getAdena() == expectedFixtureAdena && player.getInventory().getInventoryItemCount(1465, -1) == 2000, "PARTIAL_FIXTURE_EXACT_UNSPENT");
					player.setXYZInvisible(x, y, z);
				}
				else
				{
				PlayerCreationInitializer.initialize(player, PlayerCreationInitializer.Mode.POPULATION, new Location(x, y, z));
				player.setPlayerClass(classId); player.setBaseClass(classId); player.getStat().setExp(player.getStat().getExpForLevel(55)); player.getStat().setLevel((byte) 55);
				player.giveAvailableAutoGetSkills(); player.giveAvailableSkills(false, true, false); player.rewardSkills();
				if (classId == 17) { check(player.getAllSkills().stream().anyMatch(skill -> PhantomSupportEffectAuthority.nativeCapability(skill) != null && !"SELF".equals(PhantomSupportEffectAuthority.nativeScope(skill))), "FIXTURE_LEARNED_SUPPORT"); }
				if (classId == 5) { check(player.getAllSkills().stream().anyMatch(skill -> PhantomCombatSkillSafety.supportsNpcAggro(skill, player.isTransformed())), "FIXTURE_LEARNED_GUARDIAN"); }
				for (int item : new int[] {79, classId == 17 ? 2406 : 2381, 2412}) { player.getInventory().equipItem(player.addItem(ItemProcessType.REWARD, item, 1, player, false)); }
				player.addItem(ItemProcessType.REWARD, 1465, 2000, player, false); player.addAdena(ItemProcessType.REWARD, 5000, player, false);
				player.setRunning(); player.standUp(); player.refreshExpertisePenalty(); player.refreshOverloaded(); player.setCurrentHp(player.getMaxHp()); player.setCurrentMp(player.getMaxMp()); player.setCurrentCp(player.getMaxCp());
				id = profiles.create(player.getObjectId()).profileId();
				writeNew(actorReceipt, "profile=" + id + "\nactor=" + player.getObjectId() + "\nTEST_FIXTURE=true\n");
				}
				ids.add(id);
				PhantomHistoricalBackgroundPlanner.Result plan = null;
				for (var candidateField : selectedField == null ? fields : List.of(selectedField))
				{
					player.setXYZInvisible(candidateField.x(), candidateField.y(), candidateField.z());
					plan = planner.planInitial(id, player, 940094 + classId, 1);
					System.out.println("TASK094_FIXTURE_PLAN class=" + classId + " anchor=" + candidateField.anchorId() + " localHP=" + candidateField.localHp() + " ready=" + plan.ready() + " reason=" + plan.reasonKey() + " rejections=" + plan.candidateRejections().stream().limit(6).toList());
					// Exact existing HistoricalBackgroundService fallback, without fabricating solo viability.
					if (!plan.ready() && selectedField != null && "planner.target_or_route.absent".equals(plan.reasonKey()))
					{
						plan = planner.idleInitial(id, player, 940094 + classId, 1);
						System.out.println("TASK094_ORDINARY_IDLE_FALLBACK class=" + classId + " source=HistoricalBackgroundService idleInitial reason=" + plan.reasonKey());
					}
					if (plan.ready())
					{
						if (selectedField == null) { selectedField = candidateField; writeNew(Path.of(System.getProperty("phantom.task094.candidate")).resolve("fixture-field.properties"), "anchor=" + selectedField.anchorId() + "\nx=" + selectedField.x() + "\ny=" + selectedField.y() + "\nz=" + selectedField.z() + "\ncanonicalUnique=true\nordinaryPlannerAdmitted=true\n"); }
						break;
					}
				}
				check(plan != null && plan.ready(), "ORDINARY_FARM_PLAN"); if (goals.load(id).isEmpty()) { goals.insert(id, plan.goal()); }
				player.getInventory().updateDatabase(); player.storeMe();
				System.out.println("TASK094_TEST_FIXTURE profile=" + id + " actor=" + player.getObjectId() + " class=" + classId + " level=55 learned=" + player.getAllSkills().size() + " point=" + player.getX() + "," + player.getY() + "," + player.getZ() + " sourceAnchor=" + selectedField.anchorId() + " farm=" + plan.spec().anchorId() + " stockGranted=" + (saved == null));
			}
			finally { player.stopAllTasks(); player.storeMe(); player.deleteMe(); }
		}
		writeNew(stage.resolve("fixture.properties"), "profiles=" + ids.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")) + "\nTEST_FIXTURE=true\n");
	}

	static String completionReceipt(Path candidate, long pid) throws java.io.IOException
	{
		final var receipt = new Properties();
		receipt.setProperty("candidate", candidate.toString());
		receipt.setProperty("pid", Long.toString(pid));
		receipt.setProperty("scene", "PASS");
		final var writer = new java.io.StringWriter();
		receipt.store(writer, "TASK094 native completion");
		return writer.toString();
	}
	private static Properties read(Path path) throws Exception { final var result = new Properties(); try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { result.load(reader); } return result; }

	/** Read-only explanation of the exact production recovery mapping, before any revive or teleport. */
	private static boolean describeRecoveryMapping(PhantomBackgroundSuite.ProductionAuthorityFixture source, Player player)
	{
		final var regions = org.l2jmobius.gameserver.data.xml.MapRegionData.getInstance();
		final var town = regions.getTeleToLocation(player, org.l2jmobius.gameserver.model.actor.enums.player.TeleportWhereType.TOWN);
		if (town == null) { System.out.println("TASK094_RECOVERY_MAP actor=" + player.getObjectId() + " nativeTown=absent"); return false; }
		final int region = regions.getMapRegionLocId(town.getX(), town.getY());
		final var canonical = source.authority().canonicalRecoveryPosition(town.getX(), town.getY(), town.getZ(), town.getInstanceId(), player.getHeading());
		System.out.println("TASK094_RECOVERY_MAP actor=" + player.getObjectId() + " nativeTown=" + town.getX() + "," + town.getY() + "," + town.getZ() + " region=" + region + " canonical=" + canonical.isPresent());
		final var point = new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(town.getX(), town.getY(), town.getZ(), town.getInstanceId());
		for (var role : List.of(PhantomTopologyAnchorRole.RESPAWN, PhantomTopologyAnchorRole.CITY_CENTER, PhantomTopologyAnchorRole.ROUTE))
		{
			final var candidates = source.topology().nearestAnchors(point, role, 64, 100_000);
			final var sameRegion = candidates.stream().filter(anchor -> regions.getMapRegionLocId(anchor.point().x(), anchor.point().y()) == region).toList();
			final var valid = sameRegion.stream().filter(anchor -> L2jPhantomBackgroundAuthority.canonicalCommittedAnchorPosition(anchor, player.getHeading()).isPresent()).toList();
			System.out.println("TASK094_RECOVERY_ANCHORS role=" + role + " nearest=" + candidates.size() + " sameRegion=" + sameRegion.size() + " canonical=" + valid.size());
			for (var anchor : sameRegion.stream().limit(3).toList())
			{
				System.out.println("TASK094_RECOVERY_ANCHOR id=" + anchor.id() + " point=" + anchor.point() + " tolerance=" + anchor.validationTolerance() + " geoZ=" + GeoEngine.getInstance().getHeight(anchor.point().x(), anchor.point().y(), anchor.point().z()));
			}
		}
		return canonical.isPresent();
	}

	/** Existing source suppliers and native STORE promote one exact, drained actor at a time. */
	private static void upgradeNativeAuthority(Runtime runtime, PhantomProfileRepository profiles, List<Long> ids,
		PhantomBackgroundSuite.ProductionAuthorityFixture previous, PhantomBackgroundSuite.ProductionAuthorityFixture current, Path receipt) throws Exception
	{
		final var oldHashes = previous.authority().hashes();
		final var newHashes = current.authority().hashes();
		final var goals = new PhantomGoalStateStore(profiles);
		for (long id : ids)
		{
			checkOwnActorsAbsent(runtime, profiles, ids);
			final var baseline = runtime.transactions.load(id);
			check(baseline.successful() && baseline.state() != null, "AUTHORITY_UPGRADE_BASELINE");
			final var selected = baseline.state().hashes().equals(newHashes) ? current : baseline.state().hashes().equals(oldHashes) ? previous : null;
			check(selected != null, "AUTHORITY_UPGRADE_EXACT_OLD_OR_NEW");
			final var durable = persisted(profiles, List.of(id));
			final var root = goals.load(id).orElseThrow();
			runtime.authoritySource.set(selected);
			check(runtime.authority.hashes().equals(baseline.state().hashes()), "AUTHORITY_UPGRADE_SOURCE_EXACT");
			loadActor(runtime, profiles, id);
			final Player player = World.getInstance().getPlayer(profiles.find(id).orElseThrow().characterObjectId());
			final var owner = player.getNativeWorkOwner();
			final long epoch = owner.epoch();
			check(!player.isInParty() && !player.isMoving() && !player.isAttackingOrCastingNow() && runtime.materialization.list().size() == 1, "AUTHORITY_UPGRADE_NATIVE_IDLE");
			check(org.l2jmobius.gameserver.model.actor.PlayerNativeWork.checkpoint(player, () ->
			{
				final var live = captureLive(player);
				final var vitals = new StoredVitals(player.getCurrentHp(), player.getMaxHp(), player.getCurrentMp(), player.getMaxMp(), player.getCurrentCp(), player.getMaxCp());
				check(owner.sealed() && owner.isCurrent() && player.getNativeWorkOwner() == owner && owner.epoch() == epoch
					&& World.getInstance().getPlayer(player.getObjectId()) == player && World.getInstance().findObject(player.getObjectId()) == player
					&& !player.hasPendingOwnedStore() && runtime.materialization.list().size() == 1, "AUTHORITY_UPGRADE_SEALED_LIFETIME");
				runtime.authoritySource.set(current);
				check(runtime.authority.hashes().equals(newHashes), "AUTHORITY_UPGRADE_CURRENT_BUNDLE");
				player.storeMe();
				check(live.equals(captureLive(player)) && vitals.equals(new StoredVitals(player.getCurrentHp(), player.getMaxHp(), player.getCurrentMp(), player.getMaxMp(), player.getCurrentCp(), player.getMaxCp()))
					&& player.getNativeWorkOwner() == owner && owner.epoch() == epoch && !player.hasPendingOwnedStore(), "AUTHORITY_UPGRADE_NATIVE_FACTS_UNCHANGED");
				return true;
			}), "AUTHORITY_UPGRADE_CHECKPOINT");
			final var stored = runtime.transactions.load(id);
			final var context = runtime.transactions.nativeContext(id, player.getObjectId());
			check(stored.successful() && stored.state() != null && stored.state().hashes().equals(newHashes)
				&& context.status() == PhantomBackgroundTransaction.Status.SUCCESS && context.context() != null && context.context().phase() == org.l2jmobius.gameserver.phantoms.background.PhantomNativeContext.Phase.COMPLETED
				&& context.matchesNativeLoad(player.getVitalityPoints()) && durable.equals(persisted(profiles, List.of(id))) && root.equals(goals.load(id).orElseThrow()), "AUTHORITY_UPGRADE_COMPLETED_EXACT");
			check(runtime.materialization.dematerialize(id).status() == PhantomMaterializationService.ResultStatus.SUCCESS, "AUTHORITY_UPGRADE_CANONICAL_DEMATERIALIZE");
			checkOwnActorsAbsent(runtime, profiles, ids);
			check(runtime.transactions.load(id).state().hashes().equals(newHashes) && durable.equals(persisted(profiles, List.of(id))) && root.equals(goals.load(id).orElseThrow()), "AUTHORITY_UPGRADE_DURABLE_EXACT");
			System.out.println("TASK094_NATIVE_AUTHORITY_UPGRADE_PASS profile=" + id + " from=" + (selected == previous ? "exactOld" : "exactCurrent") + " sourceBundle=current nativeCheckpointStore=true samePlayerOwnerEpoch=true earnedItemsSkillsRootUnchanged=true canonicalDematerialized=true directMetadataWrites=0");
		}
		runtime.authoritySource.set(current);
		writeNew(receipt, "actors=4\noldOrNewHashesExact=true\nnativeSealStoreDematerialize=true\nfixtureReset=0\ndirectMetadataWrites=0\n");
	}

	private static void checkOwnActorsAbsent(Runtime runtime, PhantomProfileRepository profiles, List<Long> ids)
	{
		check(runtime.materialization.list().isEmpty() && runtime.party.snapshot().operationClaims() == 0 && runtime.party.snapshot().persistenceClaims() == 0 && runtime.party.snapshot().navigationClaims() == 0, "AUTHORITY_UPGRADE_NO_OTHER_CONTROL");
		for (long id : ids)
		{
			final int actor = profiles.find(id).orElseThrow().characterObjectId();
			check(runtime.materialization.find(id).isEmpty() && World.getInstance().getPlayer(actor) == null && World.getInstance().findObject(actor) == null
				&& PhantomIdentityLeaseRegistry.getInstance().getOwnerSnapshot(actor) == null
				&& !org.l2jmobius.gameserver.taskmanagers.PlayerAutoSaveTaskManager.getInstance().containsObjectId(actor), "AUTHORITY_UPGRADE_COLD_IDENTITY");
		}
	}

	private static void loadActor(Runtime runtime, PhantomProfileRepository profiles, long id) throws Exception
	{
		checkContinue();
		final var orphaned = runtime.transactions.load(id);
		if (orphaned.successful() && orphaned.state() != null && java.util.Set.of(org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.State.MATERIALIZED, org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.State.VERIFY_PENDING).contains(orphaned.state().state()))
		{
			final String durable = persisted(profiles, List.of(id)); final String partyComponents = persistedPartyComponents(profiles, List.of(id)); final var root = new PhantomGoalStateStore(profiles).load(id).orElseThrow();
			final int actor = profiles.find(id).orElseThrow().characterObjectId();
			check(runtime.materialization.find(id).isEmpty() && World.getInstance().getPlayer(actor) == null && World.getInstance().findObject(actor) == null && PhantomIdentityLeaseRegistry.getInstance().getOwnerSnapshot(actor) == null && !org.l2jmobius.gameserver.taskmanagers.PlayerAutoSaveTaskManager.getInstance().containsObjectId(actor), "ABANDONED_NATIVE_OWNER_ABSENT");
			final var recovered = runtime.background.recoverAbandonedMaterialization(id);
			final var after = runtime.transactions.load(id);
			check(recovered.status() == PhantomBackgroundService.OperationStatus.SUCCESS && after.successful() && after.state() != null && java.util.Set.of(org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.State.READY, org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.State.DEAD).contains(after.state().state())
				&& orphaned.state().identity().equals(after.state().identity()) && orphaned.state().hashes().equals(after.state().hashes()) && durable.equals(persisted(profiles, List.of(id))) && partyComponents.equals(persistedPartyComponents(profiles, List.of(id))) && root.equals(new PhantomGoalStateStore(profiles).load(id).orElseThrow()), "ABANDONED_NATIVE_CANONICAL_RECONCILIATION");
			System.out.println("TASK094_ABANDONED_NATIVE_RECONCILIATION_PASS profile=" + id + " from=" + orphaned.state().state() + " to=" + after.state().state() + " exclusiveAbsentOwnerLease=true canonicalSqlInventorySkillsVerified=true earnedRootPartyClockUnchanged=true fixtureWrites=0");
		}
		final var admitted = runtime.materialization.materialize(id);
		if (admitted.status() != PhantomMaterializationService.ResultStatus.SUCCESS) { System.out.println("TASK094_MATERIALIZE_REJECT profile=" + id + " status=" + admitted.status()); }
		check(admitted.status() == PhantomMaterializationService.ResultStatus.SUCCESS, "MATERIALIZATION");
		final Player player = World.getInstance().getPlayer(profiles.find(id).orElseThrow().characterObjectId());
		check(player != null && !player.isGM() && "task094farm".equals(player.getAccountName()) && player.getNativeWorkOwner() != null, "NATIVE_ACTOR_OWNER");
		if (runtime.players.stream().noneMatch(loaded -> loaded.getObjectId() == player.getObjectId())) { runtime.players.add(player); }
		final var owner = player.getNativeWorkOwner();
		final long epoch = owner.epoch();
		final int admissions = runtime.admissions.get();
		check(runtime.backend.materialize(id) && World.getInstance().getPlayer(player.getObjectId()) == player
			&& player.getNativeWorkOwner() == owner && owner.epoch() == epoch && runtime.admissions.get() == admissions, "PARTY_ACTIVE_REUSE_NO_ADMISSION");
		check(org.l2jmobius.gameserver.model.actor.PlayerNativeWork.checkpoint(player, () -> !runtime.backend.materialize(id))
			&& runtime.admissions.get() == admissions, "PARTY_SEALED_REUSE_REJECTED");
		final var before = runtime.transactions.load(id);
		if (before.successful() && before.state() != null && !before.state().hashes().equals(runtime.authority.hashes()))
		{
			final var durable = persisted(profiles, List.of(id));
			final var vitals = storedVitals(player.getObjectId());
			final var root = new PhantomGoalStateStore(profiles).load(id).orElseThrow();
			check(org.l2jmobius.gameserver.model.actor.PlayerNativeWork.checkpoint(player, () -> { player.storeMe(); return true; }), "NATIVE_AUTHORITY_RECAPTURE_STORE");
			final var after = runtime.transactions.load(id);
			check(after.successful() && after.state() != null && after.state().hashes().equals(runtime.authority.hashes())
				&& before.state().identity().equals(after.state().identity()) && before.state().progress().equals(after.state().progress()) && before.state().position().equals(after.state().position())
				&& durable.equals(persisted(profiles, List.of(id))) && vitals.equals(storedVitals(player.getObjectId())) && root.equals(new PhantomGoalStateStore(profiles).load(id).orElseThrow()), "NATIVE_AUTHORITY_RECAPTURE_EXACT_FACTS");
			System.out.println("TASK094_NATIVE_AUTHORITY_RECAPTURE_PASS profile=" + id + " actor=" + player.getObjectId() + " nativeCheckpointStore=true exactIdentityProgressPositionVitalsItemsSkillsRoot=true directMetadataWrites=0");
		}
	}

	private static void awaitRecovery(Runtime runtime, PhantomProfileRepository profiles, long id) throws Exception
	{
		final long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(100);
		long next = 0;
		while (true)
		{
			checkContinue();
			final var player = World.getInstance().getPlayer(profiles.find(id).orElseThrow().characterObjectId());
			final var observation = runtime.death.observation(id);
			if (player != null && !player.isDead() && runtime.death.drained() && observation.isEmpty())
			{
				System.out.println("TASK094_LOADED_CORPSE_RECOVERY_PASS profile=" + id + " actor=" + player.getObjectId() + " epoch=" + player.getNativeWorkOwner().epoch() + " XP=" + player.getExp() + " level=" + player.getLevel() + " point=" + player.getX() + "," + player.getY() + "," + player.getZ() + " nativeTownReturn=true fixtureReset=0");
				return;
			}
			if (System.nanoTime() >= next)
			{
				final var snapshot = runtime.death.snapshot();
				final String reason = observation.getOrDefault("nativeReason", "absent");
				System.out.println("TASK094_RECOVERY_OBSERVE profile=" + id + " observed=" + snapshot.observedDeaths() + " accepted=" + snapshot.acceptedReconciliations() + " running=" + snapshot.runningReconciliations() + " failurePresent=" + !snapshot.firstFailure().isEmpty()
					+ " reason=" + (reason.matches("[a-z0-9_.]+") ? reason : "WITHHELD") + " nativeRecovery=" + observation.get("nativeRecovery") + " ageMillis=" + observation.get("ageMillis")
					+ " player=" + (player != null) + " dead=" + (player != null && player.isDead()) + " party=" + (player != null && player.getParty() != null) + " pendingStore=" + (player != null && player.hasPendingOwnedStore())
					+ " ownerHealthy=" + (player != null && player.getNativeWorkOwner() != null && player.getNativeWorkOwner().nativeObservationHealthy()));
				next = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
			}
			check(runtime.death.snapshot().firstFailure().isEmpty() && System.nanoTime() < until, "LOADED_CORPSE_RECOVERY");
			Thread.sleep(200);
		}
	}

	/** Real pending BEFORE / retry / native AFTER on the retained corpse; no actor or SQL fixture edits. */
	private static void rawDeadStoreProbe(Runtime runtime, PhantomProfileRepository profiles, long id, Path candidate) throws Exception
	{
		final int actor = profiles.find(id).orElseThrow().characterObjectId();
		final var raw = storedVitals(actor);
		if (raw.mp() <= raw.maxMp()) { return; }
		final String durable = persisted(profiles, List.of(id));
		final Player player;
		try (var action = runtime.materialization.tryAcquireAction(id).orElseThrow()) { player = action.player(); }
		// A checkpoint drains native work; it must not retain its own ActionLease ticket.
		{
			final double mp = player.getCurrentMp(), cp = player.getCurrentCp();
			System.out.println("TASK094_RAW_PROBE_IDENTITY actor=" + actor + " class=" + player.getActiveClass() + " index=" + player.getClassIndex() + " race=" + player.getRace().ordinal() + " instance=" + player.getInstanceId() + " nativeMP=" + mp + " nativeCP=" + cp);
			check(player.isDead() && runtime.transactions.load(id).status() == PhantomBackgroundTransaction.Status.STATE_ABSENT, "RAW_PROBE_ORIGINAL_ABSENT_CORPSE");
			runtime.rawPrepareFault.set(true);
			try
			{
				org.l2jmobius.gameserver.model.actor.PlayerNativeWork.checkpoint(player, () -> { player.storeMe(); return true; });
				check(false, "RAW_PROBE_EXPECTED_PREPARE_FAULT");
			}
			catch (IllegalStateException expected) { if (!"TASK094_RAW_PROBE_AFTER_PREPARE".equals(expected.getMessage())) { throw new IllegalStateException("TASK094_RAW_PROBE_EXACT_FAULT", expected); } }
			finally { runtime.rawPrepareFault.remove(); }
			final var receipt = org.l2jmobius.gameserver.phantoms.background.PhantomOwnedStoreIntent.decode(profiles.findComponent(id, org.l2jmobius.gameserver.phantoms.background.PhantomOwnedStoreIntent.COMPONENT_TYPE).orElseThrow().payload());
			check(receipt.rawBeforeVitals() != null && receipt.rawBeforeVitals().currentMp() == raw.mp() && receipt.rawBeforeVitals().currentCp() == raw.cp(), "RAW_PROBE_EXACT_RECEIPT");
			check(receipt.after().autoGetSkills().stream().anyMatch(skill -> skill.skillId() == 1320 && skill.skillLevel() == 7), "RAW_PROBE_RETAINED_REAL_PREDEATH_RANK");
			check(runtime.transactions.resumeOwnedStore(receipt).status() == PhantomBackgroundTransaction.Status.POST_COMMIT_VERIFICATION_FAILED, "RAW_PROBE_WARM_BEFORE_PENDING");
			check(runtime.transactions.reconcileVerifyPending(id, actor).status() == PhantomBackgroundTransaction.Status.NATIVE_CONTEXT_REQUIRED, "RAW_PROBE_COLD_WITHOUT_LEASE_REJECTED");
			check(raw.equals(storedVitals(actor)) && durable.equals(persisted(profiles, List.of(id))), "RAW_PROBE_BEFORE_UNCHANGED");
			final var root = new PhantomGoalStateStore(profiles).load(id).orElseThrow().goal();
			check(player.resumePendingOwnedStore(runtime.background, root.goalId(), root.revision()), "RAW_PROBE_EXACT_PENDING_NATIVE_RESUME");
			final var after = storedVitals(actor);
			check(after.hp() == 0 && after.mp() <= after.maxMp() && after.cp() <= after.maxCp() && profiles.findComponent(id, org.l2jmobius.gameserver.phantoms.background.PhantomOwnedStoreIntent.COMPONENT_TYPE).isEmpty(), "RAW_PROBE_NATIVE_AFTER");
			check(player.isDead() && player.getCurrentMp() == mp && player.getCurrentCp() == cp && durable.equals(persisted(profiles, List.of(id))), "RAW_PROBE_NO_CORPSE_OR_EARNED_RESET");
			writeNew(candidate.resolve("raw-dead-store.complete"), "exactRawBefore=true\nwarmBeforePending=true\nnoLeaseColdRejected=true\nnativeAfter=true\ncorpseProgressItemsSkillsUnchanged=true\n");
			System.out.println("TASK094_RAW_DEAD_STORE_PASS actor=" + actor + " raw=" + raw + " nativeAfter=" + after + " warmBefore=PENDING coldWithoutLease=REJECTED corpseAndEarnedReset=0");
		}
	}
	private record StoredVitals(double hp, double maxHp, double mp, double maxMp, double cp, double maxCp) { }
	private static StoredVitals storedVitals(int actor) throws Exception
	{
		try (var connection = DatabaseFactory.getConnection())
		{
			connection.setReadOnly(true); check(PhantomTask094Lane.SCHEMA.equals(connection.getCatalog()), "RAW_PROBE_SCHEMA");
			try (var query = connection.prepareStatement("SELECT curHp,maxHp,curMp,maxMp,curCp,maxCp FROM characters WHERE charId=? AND account_name='task094farm'"))
			{
				query.setInt(1, actor);
				try (var rows = query.executeQuery()) { check(rows.next(), "RAW_PROBE_ACTOR"); final var result = new StoredVitals(rows.getDouble(1), rows.getDouble(2), rows.getDouble(3), rows.getDouble(4), rows.getDouble(5), rows.getDouble(6)); check(!rows.next(), "RAW_PROBE_SINGLE_ACTOR"); return result; }
			}
		}
	}
	private record FixtureField(String anchorId, int x, int y, int z, double localHp) { }

	/** Prove retained physical source presence before enabling restart gameplay; REGEN is legitimate. */
	private static void restartSourcePresence(Runtime runtime, PhantomProfileRepository profiles, long id, PhantomBackgroundSuite.ProductionAuthorityFixture source)
	{
		final var root = new PhantomGoalStateStore(profiles).load(id).orElseThrow().goal();
		check(org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec.canonicalHistoricalIdle(id, root), "RESTART_SOURCE_ROOT");
		final var anchor = source.topology().findAnchor(root.selectedAnchor().key()).orElseThrow();
		final var player = World.getInstance().getPlayer(profiles.find(id).orElseThrow().characterObjectId());
		final var entry = runtime.materialization.find(id).orElseThrow();
		final var state = runtime.background.acquisitionSnapshot(id).orElseThrow();
		check(player != null && !player.isDead() && !player.isMoving() && !player.hasPendingOwnedStore() && player.hasHeadlessOutboundSession()
			&& World.getInstance().findObject(player.getObjectId()) == player && player.getNativeWorkOwner() != null && player.getNativeWorkOwner().isCurrent()
			&& player.getNativeWorkOwner().nativeObservationHealthy() && player.getNativeWorkOwner().epoch() == entry.materializedAtNanos()
			&& state.state() == org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.State.MATERIALIZED && state.hashes().equals(runtime.authority.hashes())
			&& state.position().committedAnchorId().equals(anchor.id()) && L2jPhantomBackgroundAuthority.livePositionAllowed(source.topology(), player, anchor), "RESTART_ACTUAL_SOURCE_PRESENCE");
		System.out.println("TASK094_RESTART_SOURCE_PRESENCE_PASS profile=" + id + " point=" + player.getX() + "," + player.getY() + "," + player.getZ() + " committedAnchor=" + anchor.id() + " actualGeo=true currentOwnerEpoch=true beforeSharedGameplay=true fixtureWrites=0");
	}

	/** Passive native preconditions; only the running shared scheduler may reconcile or invite. */
	private static void awaitPartyIntakeReady(Runtime runtime, PhantomProfileRepository profiles, List<Long> ids, long supportId, boolean requireAbsence) throws Exception
	{
		final var goals = new PhantomGoalStateStore(profiles);
		final var actors = ids.stream().map(id -> profiles.find(id).orElseThrow().characterObjectId()).collect(java.util.stream.Collectors.toSet());
		final long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(300); long next = 0;
		final Map<Long, Double> previousMp = new LinkedHashMap<>(); final Map<Long, Long> mpProgress = new LinkedHashMap<>();
		final Map<Long, Double> previousHp = new LinkedHashMap<>(); final Map<Long, Long> hpProgress = new LinkedHashMap<>();
		while (true)
		{
			checkContinue(); int ready = 0; final boolean diagnostic = System.nanoTime() >= next;
			for (long id : ids)
			{
				final var player = World.getInstance().getPlayer(profiles.find(id).orElseThrow().characterObjectId());
				final var ref = runtime.backend.currentMember(id).orElse(null);
				final var member = ref == null ? null : runtime.backend.memberSnapshot(ref).orElse(null);
				final var claim = runtime.party.claim(id).map(value -> value.state()).orElse(null);
				final var root = goals.load(id).orElseThrow().goal();
				check(root.status() == PhantomGoalStatus.ACTIVE && (org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec.GOAL_TYPE.equals(root.goalType()) || org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec.canonicalHistoricalIdle(id, root)), "NATIVE_INTAKE_ORDINARY_ROOT");
				check(player != null && !player.isDead() && player.getNativeWorkOwner() != null && player.getNativeWorkOwner().isCurrent() && player.getNativeWorkOwner().nativeObservationHealthy(), "NATIVE_INTAKE_ACTOR_OWNER");
				check(player.getParty() == null || !requireAbsence && player.getParty().getMembers().stream().allMatch(value -> actors.contains(value.getObjectId()) && value.hasHeadlessOutboundSession()), "NATIVE_INTAKE_UNEXPECTED_PARTY");
				final boolean role = member != null && (id == supportId ? PhantomAutonomousFarmParty.support(member) : PhantomAutonomousFarmParty.damageDealer(member));
				final boolean healthReady = member != null && member.hpPercent() >= 50;
				final boolean learnedRole = id == supportId ? player.getAllSkills().stream().anyMatch(skill ->
				{
					final String capability = PhantomSupportEffectAuthority.nativeCapability(skill);
					return capability != null && java.util.Set.of("combat.heal", "combat.recharge", "combat.buff", "combat.song", "combat.dance").contains(capability) && !"SELF".equals(PhantomSupportEffectAuthority.nativeScope(skill));
				}) : member != null && member.capabilities().stream().anyMatch(capability -> java.util.Set.of("combat.melee_damage", "combat.ranged_physical_damage", "combat.ranged_magic_damage").contains(capability.capabilityKey()) && capability.intrinsic() && capability.learned());
				if (member != null) { check(learnedRole, "NATIVE_INTAKE_LEARNED_ROLE"); }
				final double mana = player.getCurrentMp(); final var previousMana = previousMp.put(id, mana);
				if (previousMana == null || mana > previousMana + 0.01) { mpProgress.put(id, System.nanoTime()); }
				if (!role && member != null && member.mpPercent() < 15) { check(System.nanoTime() - mpProgress.get(id) < TimeUnit.SECONDS.toNanos(15), "NATIVE_INTAKE_REGEN_NOT_PROGRESSING"); }
				final double health = player.getCurrentHp(); final var previousHealth = previousHp.put(id, health);
				if (previousHealth == null || health > previousHealth + 0.01) { hpProgress.put(id, System.nanoTime()); }
				if (!healthReady) { check(System.nanoTime() - hpProgress.get(id) < TimeUnit.SECONDS.toNanos(15), "NATIVE_INTAKE_HEALTH_NOT_PROGRESSING"); }
				final boolean fenced = claim != null && claim.status() == org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.StateStatus.INCONSISTENT;
				final boolean repairable = fenced && "restart.claim_conflict".equals(claim.lastFailureKey()) && claim.operation() != null && claim.operation().phase() == org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.OperationPhase.CANONICAL_OBSERVED;
				check(!fenced || repairable && runtime.controls.snapshot().pulses() < 100, "NATIVE_INTAKE_FENCED_CLAIM");
				final boolean pendingJoin = !requireAbsence && claim != null && claim.operation() != null && claim.operation().kind() == org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.OperationKind.JOIN
					&& java.util.Set.of(org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.OperationPhase.PREPARED, org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.OperationPhase.CANONICAL_PENDING, org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.OperationPhase.CANONICAL_OBSERVED).contains(claim.operation().phase())
					&& PhantomAutonomousFarmParty.plannedProfiles(claim.objectiveRef()).containsAll(ids);
				final boolean claimReady = claim == null || claim.status() == org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.StateStatus.SOLO || !requireAbsence && runtime.party.committed(id) || pendingJoin;
				final boolean available = ref != null && runtime.backend.autonomousFarmAvailable(ref);
				final boolean ownedWalk = id == supportId && player.isMoving() && player.getNativeWorkOwner().evidence().snapshot().phase() == PlayerNativeEvidence.Phase.ROUTE;
				final boolean forceReady = ref != null && runtime.backend.currentForce(ref).status() == PhantomPartyBackend.CurrentForceStatus.PARTY_ABSENT || !requireAbsence && player.getParty() != null;
				if (role && healthReady && claimReady && (forceReady || pendingJoin) && (available || ownedWalk || pendingJoin || !requireAbsence && player.getParty() != null)) { ready++; }
				if (diagnostic)
				{
					System.out.println("TASK094_NATIVE_INTAKE_CHECKPOINT profile=" + id + " learnedRole=" + learnedRole + " readyRole=" + role + " HP=" + health + "/" + player.getMaxHp() + " MP=" + mana + "/" + player.getMaxMp() + " nativeAvailable=" + available + " ownedWalk=" + ownedWalk + " claim=" + (claim == null ? "ABSENT" : claim.status()) + " generation=" + (claim == null ? 0 : claim.groupGeneration()) + " manifest=" + (claim == null ? "none" : claim.leaderManifestHash()) + " requireAbsence=" + requireAbsence + " forceReady=" + forceReady + " ordinaryRoot=" + root.goalType());
					if (id == supportId && member != null) { System.out.println("TASK094_NATIVE_SUPPORT_READINESS profile=" + id + " capabilities=" + member.capabilities().stream().filter(capability -> capability.learned() && capability.actionSkillId() > 0 && java.util.Set.of("combat.heal", "combat.recharge", "combat.buff", "combat.song", "combat.dance").contains(capability.capabilityKey())).sorted(Comparator.comparingInt((org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberCapability capability) -> capability.capabilityKey().equals("combat.heal") || capability.capabilityKey().equals("combat.recharge") ? 0 : 1).thenComparingInt(capability -> capability.actionSkillId())).limit(12).map(capability -> capability.capabilityKey() + ":" + capability.actionSkillId() + ":" + capability.actionSkillLevel() + ":" + capability.targetScope() + ":ready=" + capability.readyNow() + ":reason=" + capability.readinessReason() + ":nativeMP=" + (player.getKnownSkill(capability.actionSkillId()) == null ? -1 : player.getStat().getMpInitialConsume(player.getKnownSkill(capability.actionSkillId())) + player.getStat().getMpConsume(player.getKnownSkill(capability.actionSkillId())))).toList()); }
				}
			}
			if (ready == ids.size()) { System.out.println("TASK094_NATIVE_INTAKE_PRECONDITIONS_PASS actors=" + ready + " source=actualMaterializedPlayers sharedSchedulerInstalled=true controllerStarted=" + runtime.schedulerStarted + " fixtureWrites=0 partyKillRewardsNotClaimed=true"); return; }
			if (diagnostic) { next = System.nanoTime() + TimeUnit.SECONDS.toNanos(5); }
			check(System.nanoTime() < until, "NATIVE_INTAKE_NOT_READY"); Thread.sleep(100);
		}
	}

	/** Observe the actual shared-scheduler travel owner; no moves or fabricated farm goal from this test. */
	private static void awaitNativeRejoin(Runtime runtime, PhantomProfileRepository profiles, long id, PhantomBackgroundSuite.ProductionAuthorityFixture source, List<Long> peers) throws Exception
	{
		final var goals = new PhantomGoalStateStore(profiles);
		final var root = goals.load(id).orElseThrow();
		check(org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec.canonicalHistoricalIdle(id, root.goal()), "REJOIN_CANONICAL_IDLE_ROOT");
		final var anchor = source.topology().findAnchor(root.goal().selectedAnchor().key()).orElseThrow();
		final long until = System.nanoTime() + TimeUnit.MINUTES.toNanos(31);
		final var durable = persisted(profiles, List.of(id));
		long next = 0;
		while (true)
		{
			checkContinue();
			final var player = World.getInstance().getPlayer(profiles.find(id).orElseThrow().characterObjectId());
			final var state = runtime.background.acquisitionSnapshot(id).orElse(null);
			if (player != null && !player.isDead() && player.calculateDistance3D(anchor.point().x(), anchor.point().y(), anchor.point().z()) <= 2200 && Math.abs((long) player.getZ() - anchor.point().z()) <= 200)
			{
				for (long peer : peers) { if (runtime.materialization.find(peer).isEmpty()) { loadActor(runtime, profiles, peer); System.out.println("TASK094_NATIVE_REJOIN_PEER_MATERIALIZED profile=" + peer + " canonicalOwner=true fixtureWrites=0"); } }
			}
			final var nativeParty = player == null ? null : player.getParty();
			final boolean handoff = nativeParty != null && nativeParty.getMembers().size() == peers.size() + 1 && runtime.party.committed(id)
				&& peers.stream().allMatch(peer -> { final var actor = World.getInstance().getPlayer(profiles.find(peer).orElseThrow().characterObjectId()); return actor != null && actor.getParty() == nativeParty && runtime.party.committed(peer) && actor.getInstanceId() == player.getInstanceId() && actor.calculateDistance2D(player) <= 1200; });
			if (player != null && !player.isDead() && !player.isMoving() && !player.hasPendingOwnedStore() && state != null
				&& (handoff || state.position().committedAnchorId().equals(anchor.id()) && L2jPhantomBackgroundAuthority.livePositionAllowed(source.topology(), player, anchor)
				&& player.getNativeWorkOwner().evidence().snapshot().phase() == PlayerNativeEvidence.Phase.NONE))
			{
				check(root.equals(goals.load(id).orElseThrow()) && durable.equals(persisted(profiles, List.of(id))), "NATIVE_REJOIN_EARNED_AND_ROOT_UNCHANGED");
				System.out.println("TASK094_NATIVE_REJOIN_PASS profile=" + id + " actor=" + player.getObjectId() + " point=" + player.getX() + "," + player.getY() + "," + player.getZ() + " committedAnchor=" + state.position().committedAnchorId() + " nativePartyHandoff=" + handoff + " sourceAnchorCommitted=" + state.position().committedAnchorId().equals(anchor.id()) + " sharedScheduler=true physicalOnly=true fixturePositionWrites=0 earnedRootUnchanged=true");
				return;
			}
			if (System.nanoTime() >= next)
			{
				final var member = runtime.backend.currentMember(id).orElse(null);
				final var claim = runtime.party.claim(id).map(value -> value.state()).orElse(null);
				final var page = runtime.backend.materializedFarmMembers(0, PhantomAutonomousFarmParty.MAXIMUM_CANDIDATES);
				System.out.println("TASK094_REJOIN_FUNNEL profile=" + id + " nativeAvailable=" + (member != null && runtime.backend.autonomousFarmAvailable(member))
					+ " pageMembers=" + page.members().size() + " pageContains=" + page.members().stream().anyMatch(value -> value.ref().profileId() == id)
					+ " claim=" + (claim == null ? "ABSENT" : claim.status()) + " conflict=" + (claim != null && "restart.claim_conflict".equals(claim.lastFailureKey()))
					+ " blocksBackground=" + runtime.party.blocksBackground(id) + " support=" + (member != null && runtime.backend.memberSnapshot(member).map(PhantomAutonomousFarmParty::supportPresent).orElse(false))
					+ " force=" + (member == null ? "ACTOR_UNAVAILABLE" : runtime.backend.currentForce(member).status()) + " background=" + (state == null ? "ABSENT" : state.state()));
				System.out.println("TASK094_NATIVE_REJOIN_OBSERVE profile=" + id + " reason=" + runtime.travel.reason(id) + " point=" + (player == null ? "absent" : player.getX() + "," + player.getY() + "," + player.getZ()) + " moving=" + (player != null && player.isMoving()) + " pending=" + (player != null && player.hasPendingOwnedStore()) + " terminal=" + (runtime.travel.lastFailure(id) != null) + " controls=" + runtime.controls.snapshot());
				check(runtime.controls.snapshot().pulses() < 100 || claim == null || claim.status() != org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.StateStatus.INCONSISTENT, "REJOIN_PARTY_INTAKE_FENCED");
				next = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
			}
			check(System.nanoTime() < until && player != null && !player.isDead() && runtime.controls.snapshot().stageFailures() == 0 && runtime.travel.lastFailure(id) == null, "NATIVE_PHYSICAL_REJOIN");
			check(!runtime.travel.transientServiceExhausted(id, root.goal()), "REJOIN_SERVICE_ATTEMPTS_EXHAUSTED");
			Thread.sleep(100);
		}
	}

	private static void observe(Runtime runtime, List<Player> players, List<Long> ids, boolean restart) throws Exception
	{
		final Player first = players.get(0), second = players.get(1), support = players.get(2), tank = players.size() == 4 ? players.get(3) : null;
		final long[] exp = players.stream().mapToLong(Player::getExp).toArray(), sp = players.stream().mapToLong(Player::getSp).toArray(), adena = players.stream().mapToLong(Player::getAdena).toArray();
		final var before = players.stream().map(player -> player.getNativeWorkOwner().evidence().snapshot()).toList();
		final double supportMp = support.getCurrentMp(), tankMp = tank == null ? 0 : tank.getCurrentMp();
		final Map<PlayerNativeEvidence.Target, SharedTarget> shared = new LinkedHashMap<>();
		final var regroupStore = new PhantomPartyStore(PhantomProfileRepository.open());
		final java.util.Set<String> castAttempts = new java.util.HashSet<>();
		final java.util.Set<org.l2jmobius.gameserver.model.skill.BuffInfo> initialBuffs = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		final Object initialSupportHolder = support.getCurrentSkill();
		for (Player recipient : players) { initialBuffs.addAll(recipient.getEffectList().getBuffs()); }
		final List<SupportCastObservation> supportCasts = new ArrayList<>(); SupportCastObservation pendingSupport = null;
		boolean committed = false, cast = false, cost = false, tankCast = false, tankThreat = false, sharedKill = false, earned = false, loot = false, nativeLootCredit = false, extendedApproach = false;
		runtime.startScheduler();
		final long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(180); long next = 0;
		while (System.nanoTime() < end)
		{
			checkContinue();
			check(players.stream().noneMatch(actor -> actor.getNativeWorkOwner().evidence().snapshot().overflow()), "NATIVE_CONFIRMED_EVIDENCE_BLOCKER");
			check(runtime.controls.snapshot().stageFailures() == 0, "NATIVE_SHARED_CONTROL_FAILURE");
			final var party = first.getParty();
			committed = party != null && party.getMembers().size() == players.size() && players.stream().allMatch(player -> player.getParty() == party && party.getMembers().contains(player)) && ids.stream().allMatch(runtime.party::committed);
			if (committed && !extendedApproach)
			{
				for (int index = 0; index < players.size(); index++)
				{
					final var actor = players.get(index);
					if (actor.isMoving() && actor.getTarget() instanceof Monster target && actor.calculateDistance2D(target) > 1200
						&& runtime.combat.find(ids.get(index)).filter(session -> !session.result().terminal() && session.targetObjectId() == target.getObjectId()).isPresent())
					{
						System.out.println("TASK094_NATIVE_EXTENDED_APPROACH actor=" + actor.getObjectId() + " target=" + target.getNativeEvidenceTarget() + " distance=" + Math.round(actor.calculateDistance2D(target)) + " point=" + actor.getX() + "," + actor.getY() + "," + actor.getZ() + " targetPoint=" + target.getX() + "," + target.getY() + "," + target.getZ() + " nativeMove=true ownedCombat=true teleportIssuedByObserver=0");
						extendedApproach = true; break;
					}
				}
			}
			final var supportHolder = support.getCurrentSkill(); final var supportLast = support.getLastSkillCast(); final var supportTarget = support.getAI().getCastTarget();
			final double nativeSupportCost = supportLast == null ? 0 : Math.max(supportLast.getMpInitialConsume() + supportLast.getMpConsume(), support.getStat().getMpInitialConsume(supportLast) + support.getStat().getMpConsume(supportLast));
			if (pendingSupport != null && (supportHolder == pendingSupport.holder || supportHolder == null && !support.isCastingNow() && supportLast != null && supportLast.getId() == pendingSupport.skillId && supportLast.getLevel() == pendingSupport.skillLevel))
			{
				pendingSupport.debit = Math.max(pendingSupport.debit, pendingSupport.observedMp - support.getCurrentMp());
			}
			if (support.isCastingNow() && supportHolder != null && supportHolder != initialSupportHolder && supportLast != null && supportLast.getId() == supportHolder.getSkillId() && nativeSupportCost > 1 && supportTarget instanceof Player recipient && recipient != support && PhantomSupportEffectAuthority.nativeCapability(supportLast) != null && (pendingSupport == null || pendingSupport.holder != supportHolder) && supportCasts.size() < 64)
			{
				pendingSupport = new SupportCastObservation(supportHolder, recipient.getObjectId(), supportLast.getId(), supportLast.getLevel(), support.getCurrentMp(), nativeSupportCost); supportCasts.add(pendingSupport);
			}
			for (var actor : players)
			{
				final var current = actor.getCurrentSkill(); final var last = actor.getLastSkillCast();
				final String key = actor.getObjectId() + ":" + (current == null ? 0 : current.getSkillId()) + ":" + (last == null ? 0 : last.getId()) + ":" + actor.isCastingNow();
				if ((current != null || last != null || actor.isCastingNow()) && castAttempts.size() < 24 && castAttempts.add(key)) { System.out.println("TASK094_NATIVE_CAST_STATE actor=" + actor.getObjectId() + " current=" + (current == null ? "none" : current.getSkillId() + ":" + current.getSkillLevel()) + " last=" + (last == null ? "none" : last.getId() + ":" + last.getLevel()) + " casting=" + actor.isCastingNow() + " intention=" + actor.getAI().getIntention() + " castTarget=" + (actor.getAI().getCastTarget() == null ? 0 : actor.getAI().getCastTarget().getObjectId()) + " mp=" + actor.getCurrentMp()); }
			}
			for (Player recipient : players)
			{
				for (var buff : recipient.getEffectList().getBuffs())
				{
					if (buff.getEffector() == support && recipient != support && !initialBuffs.contains(buff) && support.getKnownSkill(buff.getSkill().getId()) != null && buff.getTime() > 0)
					{
						final var paid = supportCasts.stream().filter(attempt -> attempt.targetObjectId == recipient.getObjectId() && attempt.skillId == buff.getSkill().getId() && attempt.skillLevel == buff.getSkill().getLevel() && attempt.debit > 1).findFirst().orElse(null);
						if (paid == null) { continue; }
						if (!cast) { System.out.println("TASK094_NATIVE_SUPPORT effector=" + support.getObjectId() + " target=" + recipient.getObjectId() + " skill=" + buff.getSkill().getId() + ":" + buff.getSkill().getLevel() + " remaining=" + buff.getTime() + " MP=" + support.getCurrentMp() + "/" + supportMp + " observedNativeDebit=" + paid.debit + " canonicalCastCost=" + paid.canonicalCost + " freshCastAndEffect=true"); }
						cast = true; cost = true;
					}
				}
			}
			if (!support.isCastingNow()) { pendingSupport = null; }
			final var last = tank == null ? null : tank.getLastSkillCast();
			tankCast |= last != null && tank.getKnownSkill(last.getId()) != null && PhantomCombatSkillSafety.supportsNpcAggro(last, tank.isTransformed()) && tank.getCurrentMp() + 1 < tankMp;
			for (Monster npc : World.getInstance().getVisibleObjectsInRange(first, Monster.class, 1200))
			{
				final var firstDamage = npc.getAggroList().get(first); final var secondDamage = npc.getAggroList().get(second);
				if (committed && !npc.isDead() && firstDamage != null && secondDamage != null && firstDamage.getDamage() > 0 && secondDamage.getDamage() > 0
					&& first.getNativeWorkOwner().evidence().snapshot().damageSequence() > before.get(0).damageSequence()
					&& second.getNativeWorkOwner().evidence().snapshot().damageSequence() > before.get(1).damageSequence() && !shared.containsKey(npc.getNativeEvidenceTarget()))
				{
					shared.put(npc.getNativeEvidenceTarget(), new SharedTarget(npc, players.stream().map(player -> player.getNativeWorkOwner().evidence().snapshot()).toList()));
					System.out.println("TASK094_NATIVE_SHARED_DAMAGE target=" + npc.getNativeEvidenceTarget() + " npc=" + npc.getId() + " DD1=" + firstDamage.getDamage() + " DD2=" + secondDamage.getDamage());
				}
				final var tankDamage = tank == null ? null : npc.getAggroList().get(tank);
				tankThreat |= tankCast && npc.getMostHated() == tank && tankDamage != null && tankDamage.getDamage() > 0;
			}
			if (committed)
			{
				for (var entry : shared.entrySet())
				{
					final var proof = entry.getValue();
					if (!entry.getKey().equals(proof.npc().getNativeEvidenceTarget()) || !proof.npc().isDead()) { continue; }
					boolean exactReward = true;
					for (int index = 0; index < players.size(); index++)
					{
						final var prior = proof.before().get(index);
						final var sensor = players.get(index).getNativeWorkOwner().evidence();
						final var now = sensor.snapshot(); final var receipts = sensor.targetReceipts();
						// A single native settlement and single DD kill in this exact shared-target window.
						// Multiple settlements/kills make the source ambiguous and cannot pass this proof.
						exactReward &= !now.overflow() && now.rewardSequence() == prior.rewardSequence() + 1 && now.expGained() > prior.expGained() && now.spGained() > prior.spGained();
						exactReward &= receipts.rewardSequence() == now.rewardSequence() && receipts.settledRewardSequence() == now.rewardSequence()
							&& entry.getKey().equals(receipts.rewardTarget()) && entry.getKey().equals(receipts.settledTarget());
						if (index < 2) { exactReward &= now.killSequence() == prior.killSequence() + 1 && receipts.killSequence() == now.killSequence() && entry.getKey().equals(receipts.killTarget()); }
					}
					if (exactReward && !sharedKill) { System.out.println("TASK094_EXACT_SHARED_SETTLEMENT target=" + entry.getKey() + " nativeRoster=" + players.size() + " committedAtDeath=true rewardsPerActor=1 DDkills=1 ambiguousSettlements=0"); sharedKill = true; }
				}
			}
			earned = true; loot = false; nativeLootCredit = false;
			for (int index = 0; index < players.size(); index++)
			{
				final var player = players.get(index); final var evidence = player.getNativeWorkOwner().evidence().snapshot();
				earned &= player.getExp() > exp[index] && player.getSp() > sp[index] && evidence.rewardSequence() > before.get(index).rewardSequence() && evidence.expGained() > before.get(index).expGained() && evidence.spGained() > before.get(index).spGained();
				loot |= evidence.lootSequence() > before.get(index).lootSequence() && player.getAdena() > adena[index];
				nativeLootCredit |= evidence.lootSequence() > before.get(index).lootSequence() && evidence.lootCount() > before.get(index).lootCount();
			}
			if (System.nanoTime() >= next)
			{
				for (Player actor : players) { System.out.println("TASK094_NATIVE_BUFF_INPUTS actor=" + actor.getObjectId() + " count=" + actor.getEffectList().getBuffCount() + " limit=" + actor.getStat().getMaxBuffCount() + " last=" + (actor.getLastSkillCast() == null ? 0 : actor.getLastSkillCast().getId()) + " buffs=" + actor.getEffectList().getBuffs().stream().limit(24).map(buff -> buff.getSkill().getId() + ":" + buff.getTime() + ":from=" + (buff.getEffector() == null ? 0 : buff.getEffector().getObjectId())).toList()); }
				if (committed)
				{
					final var ref = runtime.backend.currentMember(ids.getFirst()).orElseThrow(); final var nativeParty = runtime.backend.observe(ref).orElseThrow(); final var authority = runtime.party.claim(ids.getFirst()).orElseThrow().state();
					final var supports = authority.assignments().stream().filter(assignment -> assignment.roleKey().startsWith("support.")).map(assignment -> assignment.member()).collect(java.util.stream.Collectors.toSet());
					final var snapshots = nativeParty.members().stream().map(runtime.backend::memberSnapshot).flatMap(java.util.Optional::stream).collect(java.util.stream.Collectors.toMap(value -> value.ref(), value -> value));
					final var directives = runtime.tactics.plan(nativeParty.leader(), nativeParty.members(), snapshots).stream().limit(3).map(value -> value.kind() + ":actor=" + value.actor().profileId() + ":target=" + value.targetObjectId() + ":skill=" + value.actionSkillId() + ":scope=" + value.targetScope()).toList();
					final var nearby = World.getInstance().getVisibleObjectsInRange(first, Monster.class, PhantomAutonomousFarmParty.MAXIMUM_TARGET_DISTANCE);
					System.out.println("TASK094_NATIVE_TARGET_INPUTS nearby=" + nearby.size() + " samples=" + nearby.stream().sorted(Comparator.comparingDouble(first::calculateDistance2D)).limit(3).map(npc -> npc.getId() + ":" + npc.getObjectId() + ":dead=" + npc.isAlikeDead() + ":point=" + npc.getX() + "," + npc.getY() + "," + npc.getZ() + ":geo=" + players.stream().allMatch(actor -> GeoEngine.getInstance().canSeeTarget(actor, npc) && GeoEngine.getInstance().canMoveToTarget(actor.getX(), actor.getY(), actor.getZ(), npc.getX(), npc.getY(), npc.getZ(), actor.getInstanceId())) + ":reward=" + players.stream().allMatch(actor -> PhantomEngagementPlanner.rewardingFarmTarget(actor, npc))).toList());
					final var admissions = new ArrayList<String>();
					final int nativeTarget = runtime.backend.autonomousFarmTarget(nativeParty, supports, admissions::add).orElse(0);
					System.out.println("TASK094_NATIVE_PLAN_INPUTS assignments=" + authority.assignments().stream().map(value -> value.roleKey() + ":" + value.member().profileId() + ":" + value.capabilityKey()).toList() + " assignedSupportPresent=" + supports.stream().anyMatch(value -> runtime.backend.memberSnapshot(value).filter(PhantomAutonomousFarmParty::supportPresent).isPresent()) + " backendReady=" + runtime.backend.autonomousFarmReady(nativeParty) + " nativeTarget=" + nativeTarget + " targetAdmission=" + admissions + " directives=" + directives);
				}
				for (int index = 0; index < ids.size(); index++)
				{
					final var claim = runtime.party.claim(ids.get(index)).map(value -> value.state()).orElse(null);
					check(claim == null || claim.status() != org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.StateStatus.INCONSISTENT, "NATIVE_PARTY_CONFIRMED_CLAIM_BLOCKER");
					final var clock = regroupStore.loadRegroupClock(ids.get(index)).map(value -> value.state()).orElse(null); final var actor = players.get(index);
					final boolean far = actor.getInstanceId() != first.getInstanceId() || actor.calculateDistance2D(first) > 1200 || Math.abs((long) actor.getZ() - first.getZ()) > 200;
					System.out.println("TASK094_REGROUP_CHECKPOINT profile=" + ids.get(index) + " claim=" + (claim == null ? "ABSENT" : claim.status()) + " far=" + far + " clock=" + (clock == null ? "ABSENT" : "firstFar=" + clock.farSinceMillis() + ",observed=" + clock.lastObservedMillis() + ",cooldownUntil=" + clock.cooldownUntilMillis())
						+ " protectedMove=" + actor.isMoving() + " cast=" + actor.isCastingNow() + " combat=" + actor.isInCombat() + " sit=" + actor.isSitting() + " pendingStore=" + actor.hasPendingOwnedStore());
					check(!far || clock == null || clock.cooldownUntilMillis() <= System.currentTimeMillis() || System.currentTimeMillis() - (clock.cooldownUntilMillis() - org.l2jmobius.gameserver.phantoms.party.PhantomPartyRegroupState.COOLDOWN_MILLIS) < 5000, "NATIVE_REGROUP_NOT_ARRIVED_AFTER_RESERVED_ATTEMPT");
				}
				System.out.println("TASK094_NATIVE_OBSERVE restart=" + restart + " committed=" + committed + " supportCast=" + cast + " supportCost=" + cost + " shared=" + shared.size() + " kill=" + sharedKill + " earnedAll=" + earned + " nativeLootCredit=" + nativeLootCredit + " nativeLootAdena=" + loot + " tankCast=" + tankCast + " tankThreatDamage=" + tankThreat + " chain=" + runtime.controls.snapshot() + " party=" + runtime.party.snapshot() + " combat=" + runtime.combat.snapshot());
				for (Player player : players) { System.out.println("TASK094_ACTOR actor=" + player.getObjectId() + " hp=" + player.getCurrentHp() + " mp=" + player.getCurrentMp() + " target=" + (player.getTarget() == null ? 0 : player.getTarget().getObjectId()) + " attacking=" + player.isAttackingNow() + " casting=" + player.isCastingNow() + " intention=" + player.getAI().getIntention() + " currentSkill=" + (player.getCurrentSkill() == null ? 0 : player.getCurrentSkill().getSkillId()) + " castTarget=" + (player.getAI().getCastTarget() == null ? 0 : player.getAI().getCastTarget().getObjectId()) + " evidence=" + player.getNativeWorkOwner().evidence().snapshot()); }
				next = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
			}
			// A restart may correctly retain healthy native buffs and therefore suppress their recast.
			// Fresh cast/cost/effect was required by the sealed first native.complete receipt.
			final boolean supportProof = restart ? support.getAllSkills().stream().anyMatch(skill -> PhantomSupportEffectAuthority.nativeCapability(skill) != null && !"SELF".equals(PhantomSupportEffectAuthority.nativeScope(skill))) && support.getCurrentMp() >= 15 : cast && cost;
			final boolean settledEpisodeClosed = ids.stream().map(runtime.combat::find).flatMap(java.util.Optional::stream).noneMatch(session -> !session.result().terminal()
				&& shared.entrySet().stream().anyMatch(entry -> entry.getKey().objectId() == session.targetObjectId() && entry.getValue().npc().isDead() && entry.getKey().equals(entry.getValue().npc().getNativeEvidenceTarget())));
			// Acceptance C requires earned native EXP/SP or loot credit. Currency drops are
			// probabilistic; keep their observation separate and let owned pickup finish.
			if (committed && supportProof && sharedKill && earned && settledEpisodeClosed && (tank == null || tankCast && tankThreat))
			{
				for (int index = 0; index < players.size(); index++) { final var player = players.get(index); System.out.println("TASK094_EARNED actor=" + player.getObjectId() + " XP=" + (player.getExp() - exp[index]) + " SP=" + (player.getSp() - sp[index]) + " Adena=" + (player.getAdena() - adena[index]) + " evidence=" + player.getNativeWorkOwner().evidence().snapshot()); }
				check(runtime.controls.snapshot().stageFailures() == 0, "SHARED_CONTROL_FAILURE");
				return;
			}
			check(players.stream().noneMatch(Player::isDead), "NATIVE_ACTOR_DEATH");
			Thread.sleep(100);
		}
		throw new IllegalStateException("TASK094_NATIVE_EVIDENCE_INCOMPLETE");
	}

	/** The infrastructure-only bootstrap deliberately omits MasterHandler. Load its canonical
	 * target implementations through the same script engine, without chat/admin/item handlers. */
	private static void initializeTargetHandlers(Path candidate) throws Exception
	{
		final var handlers = org.l2jmobius.gameserver.handler.TargetHandler.getInstance();
		System.out.println("TASK094_TARGET_HANDLERS_BEFORE size=" + handlers.size());
		final List<String> names = List.of("Area", "AreaCorpseMob", "AreaFriendly", "AreaSummon", "Aura", "AuraCorpseMob", "AuraFriendly", "BehindArea", "BehindAura", "Clan", "ClanMember", "CommandChannel", "CorpseClan", "CorpseMob", "EnemySummon", "FlagPole", "FrontArea", "FrontAura", "Ground", "Holy", "One", "OwnerPet", "Party", "PartyClan", "PartyMember", "PartyNotMe", "PartyOther", "PcBody", "Pet", "Self", "Servitor", "Summon", "TargetParty", "Unlockable");
		final StringBuilder script = new StringBuilder("import handlers.skill.targets.*;\nimport org.l2jmobius.gameserver.handler.TargetHandler;\npublic final class Task094TargetHandlers { public static void main(String[] args) {\n");
		for (String name : names) { script.append("TargetHandler.getInstance().registerHandler(new ").append(name).append("());\n"); }
		script.append("} }\n");
		final Path file = candidate.resolve("Task094TargetHandlers.java");
		if (Files.exists(file)) { check(Files.isRegularFile(file, java.nio.file.LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(file) && file.toRealPath().equals(candidate.toRealPath().resolve(file.getFileName())) && Files.readString(file).equals(script.toString()), "CANONICAL_TARGET_SCRIPT_EXACT_RESTART"); }
		else { writeNew(file, script.toString()); }
		org.l2jmobius.gameserver.scripting.ScriptEngine.getInstance().executeScript(file);
		check(handlers.size() == names.size(), "CANONICAL_TARGET_HANDLERS_COUNT");
		for (var type : List.of(org.l2jmobius.gameserver.model.skill.targets.TargetType.SELF, org.l2jmobius.gameserver.model.skill.targets.TargetType.ONE, org.l2jmobius.gameserver.model.skill.targets.TargetType.PARTY, org.l2jmobius.gameserver.model.skill.targets.TargetType.PARTY_MEMBER)) { check(handlers.getHandler(type) != null, "CANONICAL_TARGET_HANDLER_MISSING"); }
		System.out.println("TASK094_TARGET_HANDLERS_PASS count=" + handlers.size() + " canonicalScripts=true replacementEffects=0");
	}

	private static final class SupportCastObservation
	{
		final Object holder;
		final int targetObjectId, skillId, skillLevel;
		final double observedMp, canonicalCost;
		double debit;
		SupportCastObservation(Object holder, int targetObjectId, int skillId, int skillLevel, double observedMp, double canonicalCost)
		{
			this.holder = holder; this.targetObjectId = targetObjectId; this.skillId = skillId; this.skillLevel = skillLevel; this.observedMp = observedMp; this.canonicalCost = canonicalCost;
		}
	}

	private record SharedTarget(Monster npc, List<PlayerNativeEvidence.Snapshot> before) { }

	/** Explicit negative phase AFTER the read-only positive measurement; no native stat/stock mutation. */
	private static void controlledNegatives(Runtime runtime, PhantomProfileRepository profiles, List<Player> players, List<Long> ids, Path candidate) throws Exception
	{
		writeNew(candidate.resolve("controlled-negatives.started"), "positive measurement ended; stale prepare and canonical source abandonment; native stats/stock writes=0\n");
		final var authority = runtime.party.claim(ids.getFirst()).orElseThrow().state();
		final var operation = authority.operation();
		check(operation != null && operation.invitationSequence() > 0 && operation.member() != null, "NATIVE_STALE_INVITATION_RECEIPT");
		final var identity = new InvitationIdentity(operation.invitationSequence(), operation.leader().characterObjectId(), operation.member().characterObjectId());
		final var invitation = new PartyInvitation(identity, identity.requesterObjectId(), operation.leader().stableKey(), identity.inviteeObjectId(), operation.member().stableKey(), players.getFirst().getParty().getDistributionType(), identity.requesterObjectId(), Long.MAX_VALUE);
		check(runtime.party.prepare(invitation, OptionalLong.of(operation.leader().profileId()), OptionalLong.of(operation.member().profileId())) == PreparationOutcome.REJECTED, "NATIVE_STALE_PREPARE");
		final var goals = new PhantomGoalStateStore(profiles); final long support = ids.get(2);
		final var original = goals.load(support).orElseThrow();
		final var abandoned = goals.replace(support, original.rowVersion(), original.goal().withStatus(PhantomGoalStatus.ABANDONED));
		try
		{
			final var departure = runtime.materialization.dematerialize(support);
			System.out.println("TASK094_NATIVE_SUPPORT_DEPARTURE profile=" + support + " canonical=" + departure.status());
			check(departure.status() == PhantomMaterializationService.ResultStatus.SUCCESS && World.getInstance().getPlayer(players.get(2).getObjectId()) == null, "NATIVE_SUPPORT_DEPARTURE");
			final long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
			while (players.stream().anyMatch(player -> player.getParty() != null) || ids.stream().anyMatch(runtime.party::committed))
			{
				check(System.nanoTime() < end, "NATIVE_SOURCE_RELEASE"); Thread.sleep(100);
			}
			check(ids.stream().allMatch(id -> runtime.party.claim(id).map(claim -> claim.state().status() == org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.StateStatus.SOLO).orElse(false)), "NATIVE_RELEASE_EXACT_SOLO_CLAIMS");
			check(goals.load(support).orElseThrow().equals(abandoned), "NATIVE_REPLACEMENT_ROOT_PRESERVED");
		}
		finally
		{
			// Restore only our exact CAS transition, after shared scheduling has stopped, even on failure.
			runtime.scheduler.beginStop(); drain(() -> schedulerDrained(runtime.scheduler), "NEGATIVE_SCHEDULER_DRAIN");
			check(goals.load(support).orElseThrow().equals(abandoned), "NATIVE_NEGATIVE_RESTORE_CAS");
			goals.replace(support, abandoned.rowVersion(), abandoned.goal().withStatus(PhantomGoalStatus.ACTIVE));
		}
		System.out.println("TASK094_NATIVE_NEGATIVES_PASS staleCommittedPrepare=REJECTED inactiveSourceObserved=true actualSupportDeparture=true exactSoloClaims=" + ids.size() + " quietNativeRelease=true replacementRootPreserved=true restoredViaCAS=true statStockWrites=0");
	}
	private record LiveState(long exp, long sp, long adena, int level, int classId, int baseClass, int classIndex, List<String> items, Map<Integer, Integer> learned) { }

	/** Compare actual drained live facts to durable SQL; an unchanged stale DB cannot pass restart. */
	private static void verifyStored(Map<Integer, LiveState> expected) throws Exception
	{
		try (var connection = DatabaseFactory.getConnection())
		{
			connection.setReadOnly(true); check(PhantomTask094Lane.SCHEMA.equals(connection.getCatalog()), "STORE_SCHEMA");
			for (var entry : expected.entrySet())
			{
				final var live = entry.getValue();
				try (var query = connection.prepareStatement("SELECT level,exp,sp,classid,base_class FROM characters WHERE charId=?"))
				{
					query.setInt(1, entry.getKey());
					try (var rows = query.executeQuery()) { check(rows.next() && rows.getInt(1) == live.level() && rows.getLong(2) == live.exp() && rows.getLong(3) == live.sp() && rows.getInt(4) == live.classId() && rows.getInt(5) == live.baseClass() && !rows.next(), "LIVE_EARNED_STORE"); }
				}
				final var items = new ArrayList<String>(); long money = 0;
				try (var query = connection.prepareStatement("SELECT object_id,item_id,count,loc FROM items WHERE owner_id=? AND loc IN ('INVENTORY','PAPERDOLL') ORDER BY object_id"))
				{
					query.setInt(1, entry.getKey());
					try (var rows = query.executeQuery()) { while (rows.next()) { items.add(rows.getInt(1) + ":" + rows.getInt(2) + ":" + rows.getLong(3) + ":" + rows.getString(4)); if (rows.getInt(2) == 57) { money += rows.getLong(3); } } }
				}
				check(items.stream().sorted().toList().equals(live.items()) && money == live.adena(), "LIVE_INVENTORY_STORE");
				final Map<Integer, Integer> learned = new LinkedHashMap<>();
				try (var query = connection.prepareStatement("SELECT skill_id,skill_level FROM character_skills WHERE charId=? AND class_index=?"))
				{
					query.setInt(1, entry.getKey()); query.setInt(2, live.classIndex());
					try (var rows = query.executeQuery()) { while (rows.next()) { learned.put(rows.getInt(1), rows.getInt(2)); } }
				}
				check(live.learned().entrySet().stream().allMatch(skill -> skill.getValue().equals(learned.get(skill.getKey()))), "LIVE_LEARNED_STORE");
				System.out.println("TASK094_LIVE_TO_SQL_PASS actor=" + entry.getKey() + " XP=" + live.exp() + " SP=" + live.sp() + " Adena=" + money + " items=" + items.size() + " learned=" + live.learned().size());
			}
		}
	}

	private static String persistedPartyComponents(PhantomProfileRepository profiles, List<Long> ids)
	{
		final var text = new StringBuilder();
		for (long id : ids)
		{
			for (String type : List.of(org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.COMPONENT_TYPE, org.l2jmobius.gameserver.phantoms.party.PhantomPartyRegroupState.COMPONENT_TYPE))
			{
				final var component = profiles.findComponent(id, type).orElse(null);
				text.append(id).append('\t').append(type).append('\t').append(component == null ? "ABSENT" : component.rowVersion() + ":" + component.componentSchemaVersion() + ":" + java.util.Base64.getEncoder().encodeToString(component.payload())).append('\n');
			}
		}
		return text.toString();
	}

	private static String persisted(PhantomProfileRepository profiles, List<Long> ids) throws Exception
	{
		final var text = new StringBuilder();
		try (var connection = DatabaseFactory.getConnection())
		{
			connection.setReadOnly(true); check(PhantomTask094Lane.SCHEMA.equals(connection.getCatalog()), "PERSISTENCE_SCHEMA");
			for (long id : ids)
			{
				final int actor = profiles.find(id).orElseThrow().characterObjectId();
				text.append("profile=").append(id).append(" actor=").append(actor).append('\n');
				for (String sql : List.of("SELECT charId,level,exp,expBeforeDeath,sp,classid,base_class FROM characters WHERE charId=?", "SELECT object_id,item_id,count,loc,loc_data,enchant_level FROM items WHERE owner_id=? ORDER BY object_id", "SELECT skill_id,skill_level,class_index FROM character_skills WHERE charId=? ORDER BY class_index,skill_id"))
				{
					try (var query = connection.prepareStatement(sql))
					{
						query.setInt(1, actor);
						try (var rows = query.executeQuery())
						{
							while (rows.next()) { for (int index = 1; index <= rows.getMetaData().getColumnCount(); index++) { text.append(rows.getMetaData().getColumnLabel(index)).append('=').append(rows.getString(index)).append('\t'); } text.append('\n'); }
						}
					}
				}
			}
		}
		return text.toString();
	}

	private static final class Runtime implements AutoCloseable
	{
		private final java.util.concurrent.atomic.AtomicBoolean firstControlFailure = new java.util.concurrent.atomic.AtomicBoolean();
		final PhantomMaterializationService materialization;
		final PhantomProgressionService progression;
		final PhantomNavigationService navigation;
		final PhantomVisibleFarmTravel travel;
		final PhantomCombatService combat;
		final PhantomPartyCoordinator party;
		final PhantomPartyTactics tactics;
		final L2jPhantomPartyBackend backend;
		final PhantomScheduler scheduler;
		final PhantomCompositeSchedulerControlPort controls;
		final PhantomBackgroundService background;
		final PhantomOrdinaryDeathRecovery death;
		final PhantomBackgroundTransaction transactions;
		final L2jPhantomBackgroundAuthority authority;
		final java.util.concurrent.atomic.AtomicReference<PhantomBackgroundSuite.ProductionAuthorityFixture> authoritySource;
		final ThreadLocal<Boolean> rawPrepareFault = new ThreadLocal<>();
		final java.util.concurrent.atomic.AtomicInteger admissions = new java.util.concurrent.atomic.AtomicInteger();
		final List<Player> players = new ArrayList<>();
		final Map<Integer, LiveState> expectedBeforeStore = new java.util.concurrent.ConcurrentHashMap<>();
		private boolean schedulerStarted;

		Runtime(Path module, PhantomBackgroundSuite.ProductionAuthorityFixture source, PhantomProfileRepository profiles, List<Long> ids)
		{
			authoritySource = new java.util.concurrent.atomic.AtomicReference<>(source);
			authority = new L2jPhantomBackgroundAuthority(() -> authoritySource.get().knowledge(), () -> authoritySource.get().topology(), () -> authoritySource.get().progression(), () -> authoritySource.get().commerce());
			final var metrics = new PhantomMetrics(); final var trace = new PhantomDiagnosticTrace(false, 0, 0, metrics);
			final var lifecycle = new PhantomMaterializationLifecycleBridge();
			materialization = new PhantomMaterializationService(profiles, PhantomIdentityLeaseRegistry.getInstance(), metrics, trace, 4, PhantomMaterializationLifecyclePort.chain(new PhantomMaterializationLifecyclePort()
			{
				@Override public void beforeMaterialize(long id, int objectId) { admissions.incrementAndGet(); }
				@Override public void afterPlayerLoad(long id, Player player) { }
				@Override public void materializeSucceeded(long id, int objectId) { }
				@Override public void materializeAborted(long id, int objectId) { }
				@Override public void beforeStore(long id, Player player) { expectedBeforeStore.put(player.getObjectId(), captureLive(player)); }
				@Override public void afterStore(long id, Player player) { }
			}, lifecycle));
			progression = new PhantomProgressionService(new L2jProgressionBackend(materialization, Path.of("."), source::knowledge), PhantomProgressionPolicy.productionDefaults());
			progression.start(); navigation = new PhantomNavigationService(metrics);
			combat = new PhantomCombatService(new L2jCombatBackend(materialization, source::knowledge, () -> progression.findCatalog().orElse(null)), PhantomCombatCapabilityResolver.fromProgression(() -> progression.findCatalog().orElse(null)), PhantomCombatPolicy.productionDefaults(64)); combat.start();
			backend = new L2jPhantomPartyBackend(profiles, materialization, progression);
			final var catalog = PhantomPartyRoleCatalog.load(module.resolve("dist/game/data/phantoms/party/high-five-party-roles-v1.xml"));
			final var goals = new PhantomGoalStateStore(profiles);
			tactics = new PhantomPartyTactics(combat, backend, PhantomPartySupportPolicy.load(module.resolve("dist/game/data/phantoms/party/high-five-party-support-v1.xml")));
			party = new PhantomPartyCoordinator(new PhantomPartyStore(profiles), goals, backend, catalog, new PhantomPartyRouteCoordinator(navigation, combat), tactics, () -> source.topology().snapshot().canonicalHash(), System::nanoTime, 64);
			scheduler = new PhantomScheduler(64, 100, 32, metrics, trace, PhantomActivityMaterializationPort.noop(), PhantomActivityWorkSink.noop());
			transactions = new PhantomBackgroundTransaction(DatabaseFactory::getConnection, PhantomBackgroundTransaction.ObjectIdAllocator.production(), point ->
			{
				if (point == PhantomBackgroundTransaction.FaultPoint.AFTER_OWNED_PREPARE && Boolean.TRUE.equals(rawPrepareFault.get())) { rawPrepareFault.remove(); throw new IllegalStateException("TASK094_RAW_PROBE_AFTER_PREPARE"); }
			});
			background = new PhantomBackgroundService(profiles, goals, PhantomIdentityLeaseRegistry.getInstance(), transactions, authority, new PhantomBackgroundCompetitionRegistry(), new org.l2jmobius.gameserver.phantoms.topology.PhantomSchedulerRelevanceSignalPort(scheduler), () -> materialization, party);
			background.installPresencePolicy(ids::contains); check(background.start(), "BACKGROUND_RECOVERY_START");
			travel = new PhantomVisibleFarmTravel(materialization, background, source.authority().travelQuery(source.topology()), navigation, id -> !party.blocksBackground(id), new org.l2jmobius.gameserver.phantoms.topology.PhantomSchedulerRelevanceSignalPort(scheduler));
			final var autonomous = new PhantomAutonomousFarmParty(party, backend, goals, catalog, System::nanoTime, travel);
			controls = new PhantomCompositeSchedulerControlPort(List.of(() -> controlPulse("party", party), () -> controlPulse("autonomous", autonomous)));
			lifecycle.install(PhantomMaterializationLifecyclePort.chain(travel, background));
			death = new PhantomOrdinaryDeathRecovery(materialization, goals, background);
			check(materialization.start() && navigation.start() && party.start() && scheduler.installControlPort(controls), "NATIVE_OWNERS_START");
		}

		void startScheduler()
		{
			if (!schedulerStarted) { check(scheduler.start(), "SHARED_SCHEDULER_START"); schedulerStarted = true; }
		}

		private void controlPulse(String stage, org.l2jmobius.gameserver.phantoms.activity.PhantomSchedulerControlPort control)
		{
			try { control.onPulse(); }
			catch (RuntimeException failure)
			{
				if (firstControlFailure.compareAndSet(false, true)) { System.out.println("TASK094_CONTROL_FAILURE stage=" + stage); printFailure(failure, java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>())); }
				throw failure;
			}
		}

		@Override public void close() throws Exception
		{
			scheduler.beginStop(); drain(() -> schedulerDrained(scheduler), "SCHEDULER_DRAIN");
			death.close(); drain(death::drained, "DEATH_RECOVERY_DRAIN");
			party.beginStop(); combat.beginStop(); navigation.beginStop();
			drain(combat::finishStop, "COMBAT_DRAIN"); drain(party::finishStop, "PARTY_DRAIN"); drain(navigation::finishStop, "NAVIGATION_DRAIN");
			check(materialization.shutdown().state() == PhantomMaterializationService.ServiceState.STOPPED, "CANONICAL_STORE_STOP");
			check(expectedBeforeStore.size() == players.size(), "EXACT_BEFORE_STORE_OBSERVATIONS");
			verifyStored(Map.copyOf(expectedBeforeStore));
			background.beginStop(); drain(background::finishStop, "BACKGROUND_DRAIN");
			progression.beginStop(); drain(progression::finishStop, "PROGRESSION_DRAIN");
			System.out.println("TASK094_CANONICAL_STORE_STOP_PASS scheduler=" + scheduler.snapshot() + " party=" + party.snapshot() + " combat=" + combat.snapshot());
		}
	}

	private static LiveState captureLive(Player player)
	{
		final Map<Integer, Integer> learned = new LinkedHashMap<>();
		for (var skill : player.getAllSkills()) { learned.put(skill.getId(), skill.getLevel()); }
		return new LiveState(player.getExp(), player.getSp(), player.getAdena(), player.getLevel(), player.getActiveClass(), player.getBaseClass(), player.getClassIndex(), player.getInventory().getItems().stream().map(item -> item.getObjectId() + ":" + item.getId() + ":" + item.getCount() + ":" + item.getItemLocation()).sorted().toList(), Map.copyOf(learned));
	}

	static boolean schedulerDrained(PhantomScheduler scheduler)
	{
		return scheduler.snapshot().state() == PhantomScheduler.SchedulerState.STOPPED || scheduler.finishStop();
	}

	private static void drain(BooleanSupplier action, String key) throws Exception
	{
		final long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
		while (!action.getAsBoolean()) { check(System.nanoTime() < end, key); Thread.sleep(20); }
	}
	private static void checkContinue()
	{
		check(admissionExpires != null && java.time.Instant.now().isBefore(admissionExpires), "NATIVE_AUTHORITY_EXPIRED");
		final Path marker = Path.of(System.getProperty("phantom.task094.candidate")).resolve("stop.requested");
		check(!Files.exists(marker, java.nio.file.LinkOption.NOFOLLOW_LINKS), "OPERATOR_SAFE_STOP");
	}
	private static void writeNew(Path path, String text) throws Exception { Files.writeString(path, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW); }
	private static void check(boolean condition, String key) { if (!condition) { throw new IllegalStateException("TASK094_" + key); } }
}
