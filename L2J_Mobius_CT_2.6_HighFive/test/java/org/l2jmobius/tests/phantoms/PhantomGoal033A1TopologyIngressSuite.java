/*
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.l2jmobius.tests.phantoms;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.l2jmobius.commons.database.DatabaseFactory;

import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.data.xml.SpawnData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerCreationInitializer;
import org.l2jmobius.gameserver.network.GameClient;
import org.l2jmobius.gameserver.model.stats.MoveType;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.background.L2jPhantomBackgroundAuthority;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundAuthority;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Clock;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.CombatFacts;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Identity;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.InventoryFacts;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Loadout;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.ModelKind;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Position;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Progress;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Receipt;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.State;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Vitals;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcKind;
import org.l2jmobius.gameserver.phantoms.navigation.L2jNavigationBackend;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRoute;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationState;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStateCodec;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore.CreationOutcome;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore.ManagedSnapshot;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfile;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.topology.L2jTopologyValidationBackend;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchor;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyEdge;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyEdgeMode;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologySnapshot;
import org.l2jmobius.gameserver.taskmanagers.PlayerAutoSaveTaskManager;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Goal033A1 production-data contract for canonical population ingress. */
public final class PhantomGoal033A1TopologyIngressSuite implements PhantomTestSuite
{
	private static final Path MANIFEST = Path.of("test/resources/phantoms/topology/goal033a1-population-ingress.tsv");
	private static final Path POPULATION_CATALOG = Path.of("data/phantoms/population/high-five-population-v1.xml");
	private static final int RESOLVER_SAMPLES_PER_CLASS = 512;
	private static final long TRAVEL_BUDGET_MILLIS = 60_000;

	private PhantomHeadlessPlayerTestEnvironment _environment;
	private PhantomBackgroundSuite.ProductionAuthorityFixture _production;
	private PhantomNavigationService _navigation;
	private PhantomPopulationCatalog _catalog;
	private PhantomTopologySnapshot _topology;
	private PhantomProfileRepository _profiles;
	private final List<Long> _createdProfileIds = new ArrayList<>();
	private final List<String> _nonDirectNavigation = new ArrayList<>();
	private List<EvidenceRow> _manifest;
	private long _navigationProfileId;

	@Override
	public String id()
	{
		return "goal033a1-topology-ingress";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		_environment = new PhantomHeadlessPlayerTestEnvironment();
		_environment.initialize(context);
		verifyNpcAnchorSpawns(Path.of("data/phantoms/topology/high-five-core.xml"));
		_production = PhantomBackgroundSuite.ProductionAuthorityFixture.start();
		_topology = _production.topology().snapshot();
		_catalog = PhantomPopulationCatalog.load(POPULATION_CATALOG, ZoneId.of("UTC"));
		_profiles = PhantomProfileRepository.open();
		_manifest = loadManifest(context.moduleRoot().resolve(MANIFEST));
		_navigation = new PhantomNavigationService(PhantomNavigationPolicy.productionDefaults(), new L2jNavigationBackend(), worker ->
		{
			worker.run();
			return true;
		}, () -> 0, new PhantomMetrics());
		PhantomAssertions.assertTrue(_navigation.start(), "Goal033A1 Navigation service did not start.");
		context.record("goal033a1.manifestRows", _manifest.size());
		context.record("goal033a1.topologyHash", _topology.canonicalHash());
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		try
		{
			if (_navigation != null)
			{
				_navigation.beginStop();
				PhantomAssertions.assertTrue(_navigation.finishStop(), "Goal033A1 Navigation service did not stop.");
			}
		}
		finally
		{
			try
			{
				cleanupManaged();
			}
			finally
			{
				try
				{
					if (_production != null)
					{
						_production.close();
					}
				}
				finally
				{
					if (_environment != null)
					{
						_environment.shutdown();
					}
				}
			}
		}
	}
	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-authoritative-creation-inventory-parity", this::testCreationInventoryParity);
		registry.add("02-canonical-ingress-farm-route-evidence", this::testCanonicalRoutes);
		registry.add("03-production-exact-anchor-travel", this::testExactAnchorAndTravel);
		registry.add("04-fail-closed-evidence-fixtures", this::testNegativeFixtures);
	}

	private void testCreationInventoryParity(PhantomTestContext context) throws Exception
	{
		requireManifest(_manifest);
		final Set<Integer> catalogClassIds = _catalog.classes().stream().map(PhantomPopulationCatalog.ClassEntry::classId).collect(Collectors.toCollection(LinkedHashSet::new));
		final Set<Integer> manifestClassIds = _manifest.stream().flatMap(row -> row.classIds().stream()).collect(Collectors.toCollection(LinkedHashSet::new));
		PhantomAssertions.assertEquals(catalogClassIds, manifestClassIds, "Population catalog and Goal033A1 manifest class IDs differ.");
		PhantomAssertions.assertEquals(11, catalogClassIds.size(), "Managed population starting-class cardinality changed.");

		for (int classId : catalogClassIds)
		{
			final List<EvidenceRow> rows = _manifest.stream().filter(row -> row.classIds().contains(classId)).sorted(Comparator.comparingInt(EvidenceRow::ordinal)).toList();
			PhantomAssertions.assertFalse(rows.isEmpty(), "Manifest omitted population class " + classId + ".");
			final Set<String> sources = rows.stream().flatMap(row -> row.creationSources().stream()).collect(Collectors.toCollection(LinkedHashSet::new));
			final Set<Integer> sourceClassIds = new LinkedHashSet<>();
			final Set<RawPoint> rawPoints = new LinkedHashSet<>();
			for (String source : sources)
			{
				final TemplateEvidence evidence = templateEvidence(Path.of(source));
				sourceClassIds.add(evidence.classId());
				rawPoints.addAll(evidence.points());
			}
			PhantomAssertions.assertEquals(new LinkedHashSet<>(rows.getFirst().classIds()), sourceClassIds, "Creation source identity differs for manifest group " + rows.getFirst().group() + ".");
			PhantomAssertions.assertEquals(rawPoints, rows.stream().map(EvidenceRow::rawPoint).collect(Collectors.toCollection(LinkedHashSet::new)), "Raw creation-point inventory differs for class " + classId + ".");

			final Set<CanonicalPoint> expected = rows.stream().map(EvidenceRow::canonicalPoint).collect(Collectors.toCollection(LinkedHashSet::new));
			final Set<CanonicalPoint> canonicalFromSources = rawPoints.stream().map(raw -> new CanonicalPoint(raw.x(), raw.y(), GeoEngine.getInstance().getHeight(raw.x(), raw.y(), raw.z()))).collect(Collectors.toCollection(LinkedHashSet::new));
			PhantomAssertions.assertEquals(expected, canonicalFromSources, "GeoEngine canonical creation points differ for class " + classId + ".");
			final var template = PlayerTemplateData.getInstance().getTemplate(classId);
			PhantomAssertions.assertTrue(template != null, "PlayerTemplate is absent for population class " + classId + ".");
			final Set<CanonicalPoint> resolved = new LinkedHashSet<>();
			for (int sample = 0; sample < RESOLVER_SAMPLES_PER_CLASS; sample++)
			{
				final Location location = PlayerCreationInitializer.resolveCreationLocation(template);
				resolved.add(new CanonicalPoint(location.getX(), location.getY(), location.getZ()));
			}
			PhantomAssertions.assertEquals(expected, resolved, "Production creation resolver inventory differs for class " + classId + ".");
		}
		context.record("goal033a1.populationClassIds", catalogClassIds);
		context.record("goal033a1.distinctIngress", _manifest.size());
	}

	private void testCanonicalRoutes(PhantomTestContext context) throws Exception
	{
		_nonDirectNavigation.clear();
		requireManifest(_manifest);
		PhantomAssertions.assertEquals(3, _topology.datasetVersion(), "Goal033A1 topology dataset version changed.");
		PhantomAssertions.assertEquals(110, _topology.nodes().size(), "Goal033A1 topology node count changed.");
		PhantomAssertions.assertEquals(110, _topology.anchors().size(), "Goal033A1 topology anchor count changed.");
		PhantomAssertions.assertEquals(83, _topology.edges().size(), "Goal033A1 topology edge count changed.");
		final var knowledge = _production.knowledge().snapshot();
		final Set<String> validatedEdgeIds = new HashSet<>();

		for (EvidenceRow row : _manifest)
		{
			final CanonicalPoint point = row.canonicalPoint();
			final List<PhantomTopologyAnchor> matches = _topology.anchors().stream().filter(anchor -> anchor.tags().contains("population-ingress") && samePoint(anchor, point)).toList();
			PhantomAssertions.assertEquals(1, matches.size(), "Canonical position must identify exactly one population-ingress anchor for " + row.key() + ".");
			final PhantomTopologyAnchor ingress = matches.getFirst();
			PhantomAssertions.assertEquals(row.ingressAnchorId(), ingress.id(), "Manifest ingress identity differs for " + row.key() + ".");
			PhantomAssertions.assertEquals(PhantomTopologyAnchorRole.ROUTE, ingress.role(), "Population ingress must reuse ROUTE role.");
			PhantomAssertions.assertEquals(0, ingress.validationTolerance(), "Population ingress tolerance must remain exact.");
			PhantomAssertions.assertEquals(row.creationSources(), new LinkedHashSet<>(ingress.sourceRefs()), "Population ingress source evidence differs for " + row.key() + ".");

			final PhantomTopologyAnchor farm = _topology.anchorById().get(row.farmAnchorId());
			PhantomAssertions.assertTrue(farm != null, "Farming anchor is absent for " + row.key() + ".");
			PhantomAssertions.assertEquals(PhantomTopologyAnchorRole.FARMING, farm.role(), "Selected destination is not a FARMING anchor for " + row.key() + ".");
			PhantomAssertions.assertTrue((farm.npcId() == null) || (farm.npcId() == row.farmingNpcId()), "Farming NPC identity differs for " + row.key() + ".");
			final var npc = knowledge.npcById().get(row.farmingNpcId());
			PhantomAssertions.assertTrue((npc != null) && (npc.kind() == NpcKind.MONSTER) && npc.attackable() && npc.targetable() && (npc.level() <= 2), "Selected destination is not an authoritative low-level normal monster for " + row.key() + ".");
			PhantomAssertions.assertTrue(knowledge.spawnAreasByNpc().getOrDefault(row.farmingNpcId(), List.of()).stream().anyMatch(area -> (area.instanceId() == 0) && (area.totalConfiguredAmount() > 0) && farm.nodeId().equals(area.topologyNodeId())), "Selected monster lacks positive instance-zero spawn capacity for " + row.key() + ".");
			PhantomAssertions.assertTrue(farm.sourceRefs().contains(row.spawnSource()), "Farming anchor omits authoritative spawn source for " + row.key() + ".");

			final List<String> route = _production.topology().routeHint(ingress.id(), farm.id()).orElseThrow(() -> new AssertionError("No routeHint for " + row.key() + ".")).edgeIds();
			PhantomAssertions.assertEquals(row.routeEdgeIds(), route, "Deterministic routeHint differs for " + row.key() + ".");
			final double baseRunSpeed = row.classIds().stream().map(PlayerTemplateData.getInstance()::getTemplate).mapToDouble(template -> template.getBaseMoveSpeed(MoveType.RUN)).min().orElseThrow();
			final List<String> evidence = new ArrayList<>();
			String currentAnchorId = ingress.id();
			for (String edgeId : route)
			{
				final PhantomTopologyEdge edge = _topology.edgeById().get(edgeId);
				PhantomAssertions.assertTrue(edge != null, "Manifest route edge is absent: " + edgeId + ".");
				PhantomAssertions.assertEquals(currentAnchorId, edge.fromAnchorId(), "Route segment is not contiguous: " + edgeId + ".");
				PhantomAssertions.assertTrue(edge.backgroundEligible() && !edge.bidirectional() && (edge.mode() == PhantomTopologyEdgeMode.BACKGROUND), "Route segment is not a one-way validated BACKGROUND edge: " + edgeId + ".");
				PhantomAssertions.assertFalse(edge.sourceRefs().isEmpty(), "Route segment has no source evidence: " + edgeId + ".");
				PhantomAssertions.assertTrue(_production.topology().isTraversable(edgeId), "Route segment is not currently traversable: " + edgeId + ".");
				final PhantomTopologyAnchor from = _topology.anchorById().get(edge.fromAnchorId());
				final PhantomTopologyAnchor to = _topology.anchorById().get(edge.toAnchorId());
				final double distance = distance(from, to);
				PhantomAssertions.assertEquals(Math.max(1, (int) Math.ceil(distance / 1000d)), edge.baseCost(), "Route cost rule differs for " + edgeId + ".");
				PhantomAssertions.assertEquals(Math.max(1L, (long) Math.ceil((distance / baseRunSpeed) * 1000d)), edge.baseTravelMillis(), "Route travel-time rule differs for " + edgeId + ".");
				if (validatedEdgeIds.add(edgeId))
				{
					validateNavigationSegment(from, to, distance, edgeId);
				}
				evidence.add(segmentEvidence(edgeId, from, to, distance));
				currentAnchorId = edge.toAnchorId();
			}
			PhantomAssertions.assertEquals(farm.id(), currentAnchorId, "Route did not terminate at selected farm for " + row.key() + ".");
			PhantomAssertions.assertEquals(row.routeHash(), sha256(String.join("|", evidence)), "Navigation evidence hash differs for " + row.key() + ".");
		}
		PhantomAssertions.assertTrue(_nonDirectNavigation.isEmpty(), "Topology edges requiring factual waypoint split: " + _nonDirectNavigation);
		context.record("goal033a1.validatedNavigationSegments", validatedEdgeIds.size());
		context.record("goal033a1.farmingNpcIds", _manifest.stream().map(EvidenceRow::farmingNpcId).distinct().sorted().toList());
	}

	private void testExactAnchorAndTravel(PhantomTestContext context) throws Exception
	{
		final Set<String> requiredGroups = _manifest.stream().map(EvidenceRow::group).collect(Collectors.toCollection(LinkedHashSet::new));
		final Set<String> coveredGroups = new LinkedHashSet<>();
		final List<String> resolvedAnchors = new ArrayList<>();
		final PhantomPopulationStore store = new PhantomPopulationStore(_profiles, _catalog);
		for (long ordinal = 1; (ordinal <= 128) && !coveredGroups.equals(requiredGroups); ordinal++)
		{
			final ManagedSnapshot snapshot = completeCreation(store, ordinal, context.seed());
			final List<EvidenceRow> classRows = _manifest.stream().filter(row -> row.classIds().contains(snapshot.state().classId())).toList();
			PhantomAssertions.assertFalse(classRows.isEmpty(), "Normal population saga selected a class absent from the Goal033A1 manifest.");
			final Set<String> classGroups = classRows.stream().map(EvidenceRow::group).collect(Collectors.toCollection(LinkedHashSet::new));
			PhantomAssertions.assertEquals(1, classGroups.size(), "One managed starting class maps to multiple creation groups.");
			final String group = classGroups.iterator().next();
			if (coveredGroups.contains(group))
			{
				continue;
			}
			final List<EvidenceRow> matchingRows = classRows.stream().filter(row -> (row.canonicalX() == snapshot.state().creationX()) && (row.canonicalY() == snapshot.state().creationY()) && (row.canonicalZ() == snapshot.state().creationZ())).toList();
			PhantomAssertions.assertEquals(1, matchingRows.size(), "Normal population saga creation position is not represented exactly once for " + group + ".");
			final EvidenceRow row = matchingRows.getFirst();
			Player player = null;
			try (PlayerAutoSaveTaskManager.PopulationLoadSuppression ignored = PlayerAutoSaveTaskManager.suppressPopulationLoad(snapshot.state().actualCharacterObjectId()))
			{
				try
				{
					player = Player.load(snapshot.state().actualCharacterObjectId());
					PhantomAssertions.assertTrue(player != null, "Normal population saga Player.load failed for " + group + ".");
					PhantomAssertions.assertEquals(1, player.getLevel(), "Goal033A1 saga fixture is not canonical level 1.");
					PhantomAssertions.assertFalse(PlayerAutoSaveTaskManager.getInstance().containsObjectId(player.getObjectId()), "Goal033A1 saga fixture entered autosave.");
					final int beforeX = player.getX();
					final int beforeY = player.getY();
					final int beforeZ = player.getZ();
					PhantomAssertions.assertEquals(snapshot.state().creationX(), beforeX, "Materialized saga Player changed creation X for " + group + ".");
					PhantomAssertions.assertEquals(snapshot.state().creationY(), beforeY, "Materialized saga Player changed creation Y for " + group + ".");
					PhantomAssertions.assertEquals(snapshot.state().creationZ(), beforeZ, "Materialized saga Player changed creation Z for " + group + ".");

					final PhantomTopologyAnchor resolved = exactAnchor(player);
					PhantomAssertions.assertEquals(row.ingressAnchorId(), resolved.id(), "Production exactAnchor did not resolve unchanged saga creation position for " + group + ".");
					PhantomAssertions.assertEquals(beforeX, player.getX(), "Exact-anchor proof moved saga Player X for " + group + ".");
					PhantomAssertions.assertEquals(beforeY, player.getY(), "Exact-anchor proof moved saga Player Y for " + group + ".");
					PhantomAssertions.assertEquals(beforeZ, player.getZ(), "Exact-anchor proof moved saga Player Z for " + group + ".");
					assertTravel(snapshot.profile().profileId(), player, row, resolved);
					coveredGroups.add(group);
					resolvedAnchors.add(group + "=" + resolved.id());
				}
				finally
				{
					_environment.cleanupLoadedPlayer(player);
				}
			}
		}
		PhantomAssertions.assertEquals(requiredGroups, coveredGroups, "Bounded deterministic normal population saga did not cover every creation group.");
		context.record("goal033a1.sagaGroups", coveredGroups.size());
		context.record("goal033a1.exactAnchors", resolvedAnchors);
	}
	private void assertTravel(long profileId, Player player, EvidenceRow row, PhantomTopologyAnchor resolved)
	{
		final Progress progress = new Progress(player.getLevel(), player.getExp(), player.getSp(), player.getExpBeforeDeath());
		final Vitals vitals = new Vitals(player.getCurrentHp(), player.getMaxHp(), player.getCurrentMp(), player.getMaxMp(), player.getCurrentCp(), player.getMaxCp());
		final InventoryFacts inventory = new InventoryFacts(List.of(), List.of(), "", player.getCurrentLoad(), player.getMaxLoad(), player.getInventory().getSize(), player.getInventoryLimit());
		final CombatFacts combat = new CombatFacts(ModelKind.MELEE, 1, 1, 1, 1, 1, 1, 0, 0, 1, 1, 0, 1, 1, 1, 1);
		PhantomBackgroundState state = new PhantomBackgroundState(State.READY, new Identity(profileId, player.getObjectId(), player.getClassIndex(), player.getActiveClass(), player.getRace().ordinal()), progress, vitals, new Position(0, player.getX(), player.getY(), player.getZ(), player.getHeading(), resolved.id()), combat, Loadout.none(), inventory, List.of(), new Clock(profileId, 0, 0), Receipt.empty(), _production.authority().hashes());
		final PhantomGoal goal = goal(row.farmingNpcId(), row.farmAnchorId());
		for (String expectedEdgeId : row.routeEdgeIds())
		{
			final PhantomBackgroundAuthority.TravelAdvance advance = _production.authority().advanceTravel(state, PhantomBackgroundGoalSpec.parse(goal), TRAVEL_BUDGET_MILLIS);
			final PhantomBackgroundAuthority.TravelAdvance restarted = _production.authority().advanceTravel(state, PhantomBackgroundGoalSpec.parse(goal), TRAVEL_BUDGET_MILLIS);
			PhantomAssertions.assertEquals(advance, restarted, "Production background travel changed after deterministic restart at " + expectedEdgeId + ".");
			PhantomAssertions.assertEquals(PhantomBackgroundAuthority.TravelAdvance.Status.ARRIVED, advance.status(), "Production background travel did not finish factual segment " + expectedEdgeId + ".");
			PhantomAssertions.assertEquals(expectedEdgeId, advance.edgeId(), "Production background travel selected an unexpected segment.");
			state = state.after(state.progress(), state.vitals(), advance.position(), state.inventory(), state.autoGetSkills(), advance.clock(), state.receipt());
		}
		PhantomAssertions.assertEquals(row.farmAnchorId(), state.position().committedAnchorId(), "Production background travel did not reach the factual low-level farm.");
		PhantomAssertions.assertEquals(progress, state.progress(), "Travel smoke mutated progression/rewards.");
		PhantomAssertions.assertEquals(vitals, state.vitals(), "Travel smoke mutated vitals.");
		PhantomAssertions.assertEquals(inventory, state.inventory(), "Travel smoke mutated inventory/rewards.");
	}
	private ManagedSnapshot completeCreation(PhantomPopulationStore store, long ordinal, long seed)
	{
		ManagedSnapshot snapshot = store.createShell(1, ordinal, seed);
		_createdProfileIds.add(snapshot.profile().profileId());
		for (int step = 0; (step < 20) && (snapshot.state().state() != PhantomPopulationState.State.READY) && (snapshot.state().state() != PhantomPopulationState.State.INCONSISTENT); step++)
		{
			final var result = store.advanceCreation(snapshot);
			PhantomAssertions.assertTrue(result.outcome() != CreationOutcome.INCONSISTENT, "Normal population saga became inconsistent at " + snapshot.state().creationStage() + ": " + result.snapshot().state().lastFailure() + ".");
			snapshot = result.snapshot();
		}
		PhantomAssertions.assertEquals(PhantomPopulationState.State.READY, snapshot.state().state(), "Normal population saga did not reach READY.");
		PhantomAssertions.assertEquals(snapshot.profile().characterObjectId(), snapshot.state().actualCharacterObjectId(), "Normal saga profile link and verified object ID differ.");
		return snapshot;
	}

	private PhantomTopologyAnchor exactAnchor(Player player) throws Exception
	{
		final Method exactAnchor = L2jPhantomBackgroundAuthority.class.getDeclaredMethod("exactAnchor", Player.class, PhantomBackgroundState.class);
		exactAnchor.setAccessible(true);
		try
		{
			return (PhantomTopologyAnchor) exactAnchor.invoke(_production.authority(), player, null);
		}
		catch (InvocationTargetException exception)
		{
			if (exception.getCause() instanceof Exception cause)
			{
				throw cause;
			}
			throw exception;
		}
	}
	private void testNegativeFixtures(PhantomTestContext context)
	{
		final String removedGroup = _manifest.getLast().group();
		final List<EvidenceRow> missing = _manifest.stream().filter(row -> !removedGroup.equals(row.group())).toList();
		assertInvalid(missing, Integer.toString(_manifest.getLast().classIds().getFirst()), "Missing ingress fixture was admitted.");

		final List<EvidenceRow> duplicate = new ArrayList<>(_manifest);
		duplicate.set(1, duplicate.get(1).withIngress(duplicate.getFirst().ingressAnchorId()));
		assertInvalid(duplicate, "Duplicate ingress", "Duplicate ingress fixture was admitted.");

		final List<EvidenceRow> fake = new ArrayList<>(_manifest);
		fake.set(0, fake.getFirst().withRoute(List.of("population.travel.missing")));
		assertInvalid(fake, "population.travel.missing", "Fake route fixture was admitted.");

		final List<EvidenceRow> fakeSpawn = new ArrayList<>(_manifest);
		fakeSpawn.set(0, fakeSpawn.getFirst().withSpawnSource("data/spawnlist/goal033a1-missing.xml"));
		assertInvalid(fakeSpawn, "Unsupported spawn source", "Fake spawn source fixture was admitted.");

		final List<EvidenceRow> changedLocation = new ArrayList<>(_manifest);
		changedLocation.set(0, changedLocation.getFirst().withCanonicalPoint(changedLocation.getFirst().canonicalX() + 1, changedLocation.getFirst().canonicalY(), changedLocation.getFirst().canonicalZ()));
		assertInvalid(changedLocation, "Missing canonical ingress", "Changed creation location fixture was admitted.");

		final List<EvidenceRow> nonBackground = new ArrayList<>(_manifest);
		nonBackground.set(0, nonBackground.getFirst().withRoute(List.of("giran.city.shop.walk")));
		assertInvalid(nonBackground, "giran.city.shop.walk", "Non-background route fixture was admitted.");
	}

	private void requireManifest(List<EvidenceRow> rows)
	{
		if (rows.size() > 128)
		{
			throw new IllegalArgumentException("Evidence manifest exceeds its bounded row limit.");
		}
		final Set<Integer> expectedClasses = _catalog.classes().stream().map(PhantomPopulationCatalog.ClassEntry::classId).collect(Collectors.toCollection(LinkedHashSet::new));
		final Set<Integer> actualClasses = rows.stream().flatMap(row -> row.classIds().stream()).collect(Collectors.toCollection(LinkedHashSet::new));
		if (!expectedClasses.equals(actualClasses))
		{
			final Set<Integer> missing = new LinkedHashSet<>(expectedClasses);
			missing.removeAll(actualClasses);
			throw new IllegalArgumentException("Missing population classes: " + missing + ".");
		}
		final Set<String> ingressIds = new HashSet<>();
		final Set<CanonicalPoint> positions = new HashSet<>();
		for (EvidenceRow row : rows)
		{
			if (row.creationSources().isEmpty() || row.spawnSource().isBlank() || row.routeEdgeIds().isEmpty())
			{
				throw new IllegalArgumentException("Incomplete evidence for " + row.key() + ".");
			}
			if (!ingressIds.add(row.ingressAnchorId()))
			{
				throw new IllegalArgumentException("Duplicate ingress " + row.ingressAnchorId() + ".");
			}
			if (!positions.add(row.canonicalPoint()))
			{
				throw new IllegalArgumentException("Duplicate canonical ingress position for " + row.key() + ".");
			}
			final PhantomTopologyAnchor ingress = _topology.anchorById().get(row.ingressAnchorId());
			if ((ingress == null) || !ingress.tags().contains("population-ingress") || !samePoint(ingress, row.canonicalPoint()))
			{
				throw new IllegalArgumentException("Missing canonical ingress for " + row.key() + ".");
			}
			final PhantomTopologyAnchor farm = _topology.anchorById().get(row.farmAnchorId());
			if ((farm == null) || (farm.role() != PhantomTopologyAnchorRole.FARMING) || !farm.sourceRefs().contains(row.spawnSource()))
			{
				throw new IllegalArgumentException("Unsupported spawn source " + row.spawnSource() + " for " + row.key() + ".");
			}
			String current = row.ingressAnchorId();
			for (String edgeId : row.routeEdgeIds())
			{
				final PhantomTopologyEdge edge = _topology.edgeById().get(edgeId);
				if ((edge == null) || (edge.mode() != PhantomTopologyEdgeMode.BACKGROUND) || !edge.backgroundEligible() || edge.sourceRefs().isEmpty() || !current.equals(edge.fromAnchorId()))
				{
					throw new IllegalArgumentException("Invalid or non-evidenced route edge " + edgeId + " for " + row.key() + ".");
				}
				current = edge.toAnchorId();
			}
			if (!row.farmAnchorId().equals(current))
			{
				throw new IllegalArgumentException("Route does not reach factual farm for " + row.key() + ".");
			}
		}
	}

	private void assertInvalid(List<EvidenceRow> rows, String expectedMessagePart, String failureMessage)
	{
		try
		{
			requireManifest(rows);
		}
		catch (IllegalArgumentException expected)
		{
			PhantomAssertions.assertTrue(expected.getMessage().contains(expectedMessagePart), "Fail-closed evidence diagnostic did not name the offending class/group/edge.");
			return;
		}
		throw new AssertionError(failureMessage);
	}

	private void validateNavigationSegment(PhantomTopologyAnchor from, PhantomTopologyAnchor to, double expectedDistance, String edgeId)
	{
		final PhantomNavigationPoint origin = new PhantomNavigationPoint(from.point().x(), from.point().y(), from.point().z(), 0);
		final PhantomNavigationPoint destination = new PhantomNavigationPoint(to.point().x(), to.point().y(), to.point().z(), 0);
		final var submission = _navigation.submit(new PhantomNavigationRequest(++_navigationProfileId, origin, destination, 0, 1_000_000_000L, 100_000));
		final PhantomNavigationResult result = submission.immediateResult() != null ? submission.immediateResult() : _navigation.consume(submission.requestId()).orElseThrow(() -> new AssertionError("Navigation result is absent for " + edgeId + "."));
		if ((result.status() != PhantomNavigationResult.Status.DIRECT_VALIDATED) || (result.route().mode() != PhantomNavigationRoute.Mode.DIRECT_VALIDATED))
		{
			_nonDirectNavigation.add(edgeId + " status=" + result.status() + " mode=" + result.route().mode() + " waypoints=" + result.route().waypoints());
			return;
		}
		PhantomAssertions.assertTrue(Math.abs(result.route().totalDistance() - expectedDistance) < 0.000001d, "Navigation distance differs for " + edgeId + ".");
		PhantomAssertions.assertEquals(destination, result.route().waypoints().getLast(), "Navigation route does not end at exact factual anchor for " + edgeId + ".");
	}

	private static boolean samePoint(PhantomTopologyAnchor anchor, CanonicalPoint point)
	{
		return (anchor.point().instanceId() == 0) && (anchor.point().x() == point.x()) && (anchor.point().y() == point.y()) && (anchor.point().z() == point.z());
	}

	private static double distance(PhantomTopologyAnchor from, PhantomTopologyAnchor to)
	{
		final double dx = to.point().x() - from.point().x();
		final double dy = to.point().y() - from.point().y();
		final double dz = to.point().z() - from.point().z();
		return Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
	}

	private static String segmentEvidence(String edgeId, PhantomTopologyAnchor from, PhantomTopologyAnchor to, double distance)
	{
		return edgeId + ":" + from.point().x() + "," + from.point().y() + "," + from.point().z() + ">" + to.point().x() + "," + to.point().y() + "," + to.point().z() + ":DIRECT_VALIDATED:" + (long) Math.ceil(distance);
	}

	private static PhantomGoal goal(int npcId, String anchorId)
	{
		return new PhantomGoal(33, PhantomBackgroundGoalSpec.GOAL_TYPE, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", "self"), new PhantomDomainRef("npc", Integer.toString(npcId)), 1, 0, "background.farm", List.of(new PhantomDomainRef(PhantomBackgroundGoalSpec.SOURCE_NAMESPACE, npcId + "@" + anchorId)), new PhantomDomainRef(PhantomBackgroundGoalSpec.ANCHOR_NAMESPACE, anchorId), "farm.background", 500, 0, 0, 0, Map.of(), "goal033a1.topology", 0);
	}

	private static List<EvidenceRow> loadManifest(Path path) throws Exception
	{
		final List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8).stream().filter(line -> !line.isBlank() && !line.startsWith("#")).toList();
		if (lines.isEmpty() || !lines.getFirst().startsWith("group\tclassIds\tcreationSources\t"))
		{
			throw new IllegalArgumentException("Goal033A1 evidence manifest header is invalid.");
		}
		final List<EvidenceRow> rows = new ArrayList<>();
		for (int index = 1; index < lines.size(); index++)
		{
			final String[] columns = lines.get(index).split("\\t", -1);
			if (columns.length != 19)
			{
				throw new IllegalArgumentException("Goal033A1 evidence row must contain exactly 19 columns at line " + (index + 1) + ".");
			}
			final List<Integer> classIds = split(columns[1], ",").stream().map(Integer::parseInt).sorted().toList();
			final Set<String> creationSources = new LinkedHashSet<>(split(columns[2], ";"));
			if (!"DIRECT_VALIDATED_SEGMENTS".equals(columns[15]) || !"ceil(distance/1000)".equals(columns[16]) || !"ceil(distance/minGroupBaseRunSpeed*1000)".equals(columns[17]))
			{
				throw new IllegalArgumentException("Goal033A1 navigation/travel evidence rule changed at line " + (index + 1) + ".");
			}
			rows.add(new EvidenceRow(columns[0], classIds, Set.copyOf(creationSources), Integer.parseInt(columns[3]), Integer.parseInt(columns[4]), Integer.parseInt(columns[5]), Integer.parseInt(columns[6]), Integer.parseInt(columns[7]), Integer.parseInt(columns[8]), Integer.parseInt(columns[9]), columns[10], Integer.parseInt(columns[11]), columns[12], columns[13], split(columns[14], ";"), columns[18]));
		}
		if (rows.size() != 38)
		{
			throw new IllegalArgumentException("Goal033A1 evidence manifest must contain exactly 38 canonical creation positions.");
		}
		return List.copyOf(rows);
	}

	private static void verifyNpcAnchorSpawns(Path path) throws Exception
	{
		SpawnData.getInstance();
		final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
		factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
		factory.setXIncludeAware(false);
		factory.setExpandEntityReferences(false);
		final var document = factory.newDocumentBuilder().parse(path.toFile());
		final L2jTopologyValidationBackend backend = new L2jTopologyValidationBackend();
		for (int index = 0; index < document.getElementsByTagName("anchor").getLength(); index++)
		{
			final Element anchor = (Element) document.getElementsByTagName("anchor").item(index);
			if (!anchor.hasAttribute("npcId"))
			{
				continue;
			}
			final int npcId = Integer.parseInt(anchor.getAttribute("npcId"));
			final int x = Integer.parseInt(anchor.getAttribute("x"));
			final int y = Integer.parseInt(anchor.getAttribute("y"));
			final int z = Integer.parseInt(anchor.getAttribute("z"));
			final int instanceId = Integer.parseInt(anchor.getAttribute("instanceId"));
			final int tolerance = Integer.parseInt(anchor.getAttribute("tolerance"));
			double nearest = Double.POSITIVE_INFINITY;
			String nearestPoint = "none";
			for (var spawn : backend.spawns(npcId, 4096))
			{
				final double dx = spawn.point().x() - x;
				final double dy = spawn.point().y() - y;
				final double dz = spawn.point().z() - z;
				final double distance = Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
				if (distance < nearest)
				{
					nearest = distance;
					nearestPoint = spawn.point().x() + "," + spawn.point().y() + "," + spawn.point().z() + "@" + spawn.point().instanceId();
				}
				if ((spawn.point().instanceId() == instanceId) && (distance <= tolerance))
				{
					nearest = -1;
					break;
				}
			}
			if (nearest >= 0)
			{
				throw new IllegalArgumentException("Topology NPC anchor " + anchor.getAttribute("id") + " has no factual spawn within tolerance " + tolerance + "; nearest=" + nearestPoint + " distance=" + nearest + ".");
			}
		}
	}
	private static TemplateEvidence templateEvidence(Path path) throws Exception
	{
		final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
		factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
		factory.setXIncludeAware(false);
		factory.setExpandEntityReferences(false);
		final var document = factory.newDocumentBuilder().parse(path.toFile());
		final int classId = Integer.parseInt(document.getElementsByTagName("classId").item(0).getTextContent().trim());
		final Node creationPoints = document.getElementsByTagName("creationPoints").item(0);
		final List<RawPoint> points = new ArrayList<>();
		for (int index = 0; index < creationPoints.getChildNodes().getLength(); index++)
		{
			final Node child = creationPoints.getChildNodes().item(index);
			if ((child instanceof Element element) && "node".equals(element.getTagName()))
			{
				points.add(new RawPoint(Integer.parseInt(element.getAttribute("x")), Integer.parseInt(element.getAttribute("y")), Integer.parseInt(element.getAttribute("z"))));
			}
		}
		return new TemplateEvidence(classId, List.copyOf(points));
	}

	private void cleanupManaged() throws Exception
	{
		if ((_profiles == null) || _createdProfileIds.isEmpty())
		{
			return;
		}
		final PhantomPopulationStateCodec codec = new PhantomPopulationStateCodec();
		for (int index = _createdProfileIds.size() - 1; index >= 0; index--)
		{
			final long profileId = _createdProfileIds.get(index);
			PhantomProfile profile = _profiles.find(profileId).orElse(null);
			if (profile == null)
			{
				_createdProfileIds.remove(index);
				continue;
			}
			final var component = _profiles.findComponent(profileId, PhantomPopulationState.COMPONENT_TYPE).orElse(null);
			final PhantomPopulationState state = component == null ? null : codec.decode(component.payload());
			Integer objectId = profile.characterObjectId();
			if ((objectId == null) && (state != null))
			{
				objectId = state.actualCharacterObjectId() != null ? state.actualCharacterObjectId() : state.expectedCharacterObjectId();
			}
			if (profile.characterObjectId() != null)
			{
				profile = _profiles.updateCharacterLink(profile.profileId(), profile.rowVersion(), null);
			}
			_profiles.delete(profile.profileId(), profile.rowVersion());
			if (objectId != null)
			{
				final Player world = World.getInstance().getPlayer(objectId);
				if (world != null)
				{
					_environment.cleanupLoadedPlayer(world);
				}
				GameClient.deleteCharByObjId(objectId);
			}
			if (state != null)
			{
				try (Connection connection = DatabaseFactory.getConnection();
					PreparedStatement statement = connection.prepareStatement("DELETE FROM accounts WHERE login=?"))
				{
					statement.setString(1, state.reservedAccount());
					statement.executeUpdate();
				}
			}
			_createdProfileIds.remove(index);
		}
	}
	private static List<String> split(String value, String separator)
	{
		return List.of(value.split(java.util.regex.Pattern.quote(separator), -1)).stream().filter(part -> !part.isBlank()).toList();
	}

	private static String sha256(String value) throws Exception
	{
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
	}

	private record EvidenceRow(String group, List<Integer> classIds, Set<String> creationSources, int ordinal, int rawX, int rawY, int rawZ, int canonicalX, int canonicalY, int canonicalZ, String ingressAnchorId, int farmingNpcId, String spawnSource, String farmAnchorId, List<String> routeEdgeIds, String routeHash)
	{
		private String key()
		{
			return group + "." + ordinal;
		}

		private RawPoint rawPoint()
		{
			return new RawPoint(rawX, rawY, rawZ);
		}

		private CanonicalPoint canonicalPoint()
		{
			return new CanonicalPoint(canonicalX, canonicalY, canonicalZ);
		}

		private EvidenceRow withIngress(String value)
		{
			return new EvidenceRow(group, classIds, creationSources, ordinal, rawX, rawY, rawZ, canonicalX, canonicalY, canonicalZ, value, farmingNpcId, spawnSource, farmAnchorId, routeEdgeIds, routeHash);
		}

		private EvidenceRow withSpawnSource(String value)
		{
			return new EvidenceRow(group, classIds, creationSources, ordinal, rawX, rawY, rawZ, canonicalX, canonicalY, canonicalZ, ingressAnchorId, farmingNpcId, value, farmAnchorId, routeEdgeIds, routeHash);
		}

		private EvidenceRow withCanonicalPoint(int x, int y, int z)
		{
			return new EvidenceRow(group, classIds, creationSources, ordinal, rawX, rawY, rawZ, x, y, z, ingressAnchorId, farmingNpcId, spawnSource, farmAnchorId, routeEdgeIds, routeHash);
		}
		private EvidenceRow withRoute(List<String> value)
		{
			return new EvidenceRow(group, classIds, creationSources, ordinal, rawX, rawY, rawZ, canonicalX, canonicalY, canonicalZ, ingressAnchorId, farmingNpcId, spawnSource, farmAnchorId, List.copyOf(value), routeHash);
		}
	}

	private record TemplateEvidence(int classId, List<RawPoint> points)
	{
	}

	private record RawPoint(int x, int y, int z)
	{
	}

	private record CanonicalPoint(int x, int y, int z)
	{
	}
}
