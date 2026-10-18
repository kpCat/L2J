/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundService;
import org.l2jmobius.gameserver.phantoms.background.L2jPhantomBackgroundAuthority;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundPlanner;
import org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundService;
import org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeQuery;
import org.l2jmobius.gameserver.phantoms.navigation.L2jNavigationBackend;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCancellationToken;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomTravelStrategy;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchor;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;

/** Bounded TEST input search only. It never applies the previewed next goal or travel. */
final class PhantomRelocation060Input
{
	private PhantomRelocation060Input() { }

	static PhantomHistoricalBackgroundPlanner.Result select(PhantomTestContext context, Player player, long profileId,
			PhantomGameKnowledgeQuery knowledge, PhantomTopologyQuery topology, L2jPhantomBackgroundAuthority authority,
			PhantomHistoricalBackgroundPlanner planner, PhantomNormalGatekeeperTravel travel)
	{
		final List<Source> sources = new ArrayList<>();
		knowledge.snapshot().spawnAreasByNpc().forEach((npcId, areas) ->
		{
			final var npc = NpcData.getInstance().getTemplate(npcId);
			if (npc == null || npc.getLevel() < 1 || npc.getLevel() > 30) { return; }
			for (var area : areas)
			{
				if (area.topologyNodeId() == null || area.instanceId() != 0) { continue; }
				for (var anchor : topology.snapshot().anchorsByNode().getOrDefault(area.topologyNodeId(), List.of()))
				{
					if (anchor.role() == PhantomTopologyAnchorRole.FARMING && anchor.point().instanceId() == 0 && (anchor.npcId() == null || anchor.npcId().equals(npcId))) { sources.add(new Source(anchor, npcId, npc.getLevel())); }
				}
			}
		});
		// Eight inputs per distinct native source region avoid spending the entire bound in one village.
		final var regions = new TreeMap<String, List<Source>>();
		for (var source : sources.stream().distinct().toList())
		{
			final var nearest = travel.legs().stream().min(Comparator.comparingDouble(leg -> Math.hypot(source.anchor().point().x() - leg.sourceX(), source.anchor().point().y() - leg.sourceY()))).orElseThrow();
			final String region = Math.hypot(source.anchor().point().x() - nearest.sourceX(), source.anchor().point().y() - nearest.sourceY()) <= 12_000 ? Integer.toString(nearest.teleporterNpcId()) : "walk-only";
			regions.computeIfAbsent(region, _ -> new ArrayList<>()).add(source);
		}
		final var ordered = regions.values().stream().flatMap(region -> region.stream().sorted(Comparator.comparing((Source value) -> value.anchor().id()).thenComparingInt(Source::npcId)).limit(8)).limit(64).toList();
		final var rejected = new TreeMap<String, Integer>();
		final var policy = PhantomNavigationPolicy.productionDefaults();
		final var backend = new L2jNavigationBackend();
		for (var source : ordered)
		{
			for (var from : PhantomLiving060Scenario.inputPoints(topology, source.anchor().id()))
			{
				try
				{
					final int oldLevel = source.level() + 10;
					player.getStat().setLevel((byte) oldLevel); player.getStat().setExp(player.getStat().getExpForLevel(oldLevel + 1) - 1); player.setSp(0);
					player.setXYZInvisible(from.x(), from.y(), from.z()); player.revalidateZone(true);
					final var seed = planner.planInitial(profileId, player, context.seed(), 0);
					if (!seed.ready()) { rejected.merge("seed", 1, Integer::sum); continue; }
					final var observed = authority.captureOwnedNative(profileId, player, seed.goal(), null).state();
					final var live = new PhantomTopologyPoint(from.x(), from.y(), from.z(), 0);
					final var initial = planner.replanVisibleLocal(profileId, observed, seed.goal(), context.seed(), 1, live, Set.of(), Set.of());
					if (!initial.ready() || initial.spec().npcId() != source.npcId()) { rejected.merge("initial", 1, Integer::sum); continue; }
					final var baseline = authority.captureOwnedNative(profileId, player, initial.goal(), null).state();
					final var grown = new PhantomBackgroundState(baseline.state(), baseline.identity(), new PhantomBackgroundState.Progress(oldLevel + 1, player.getStat().getExpForLevel(oldLevel + 1), 0, baseline.progress().experienceBeforeDeath()), baseline.vitals(), baseline.position(), baseline.combat(), baseline.loadout(), baseline.inventory(), baseline.autoGetSkills(), baseline.clock(), baseline.receipt(), baseline.hashes());
					final var excluded = Set.of(initial.spec().npcId() + "@" + initial.spec().anchorId());
					if (planner.remainsSuitable(grown, initial.goal()) || planner.replanVisibleLocal(profileId, grown, initial.goal(), context.seed(), 2, live, excluded, Set.of()).ready()) { rejected.merge("still_local", 1, Integer::sum); continue; }
					final var next = planner.replan(profileId, grown, initial.goal(), context.seed(), 2, excluded, Set.of());
					if (!next.ready() || next.spec().anchorId().equals(initial.spec().anchorId())) { rejected.merge("no_global", 1, Integer::sum); continue; }
					final var destination = topology.findAnchor(next.spec().anchorId()).orElseThrow().point();
					if (live.distanceSquared2D(destination) <= 4_000_000L) { rejected.merge("not_relocation", 1, Integer::sum); continue; }
					final var geo = GeoEngine.getInstance();
					if (!geo.hasGeo(destination.x(), destination.y())) { rejected.merge("destination.no_geo", 1, Integer::sum); continue; }
					final var endpoint = new PhantomNavigationPoint(destination.x(), destination.y(), geo.getHeight(destination.x(), destination.y(), destination.z()), 0);
					final var options = new PhantomTravelStrategy.Options(travel.legs(), player.getMoveSpeed(), player.getAdena(), 0, player.getClassIndex(), oldLevel + 1, System.currentTimeMillis() / 60_000, true, "fixture.no_return", null, 0, 110_000, Set.of());
					final long now = System.nanoTime();
					final var request = new PhantomNavigationRequest(profileId, from, endpoint, now, now + TimeUnit.MILLISECONDS.toNanos(policy.defaultRequestDeadlineMillis()), policy.maximumRouteDistance());
					final var route = backend.planTravel(request, new PhantomNavigationCancellationToken(), policy, System::nanoTime, options);
					if (route.status() != PhantomNavigationResult.Status.PATH_FOUND)
					{
						final int number = rejected.merge("native." + route.status(), 1, Integer::sum);
						if (number <= 16) { context.record("life060.relocationNativeRejected." + number, "old=" + initial.spec() + "; next=" + next.spec() + "; from=" + from + "; to=" + endpoint + "; native=" + route.status()); }
						continue;
					}
					player.rewardSkills();
					context.record("life060.relocationInput", "PREMEASUREMENT bounded64 factual sources; level=" + oldLevel + " near" + (oldLevel + 1) + "; old=" + initial.spec() + "; no suitable local target after native level threshold; global product preview=" + next.goal() + "; actual native itinerary=" + route.candidate() + "; rejection stages=" + rejected + "; preview not applied");
					System.out.println("LIFE060 RELOCATION_INPUT_GREEN old=" + initial.spec() + "; next=" + next.spec() + "; native=" + route.candidate().mode());
					return initial;
				}
				catch (IllegalArgumentException rejectedPoint)
				{
					if (!"Canonical Player position does not identify exactly one topology anchor.".equals(rejectedPoint.getMessage())) { throw rejectedPoint; }
					rejected.merge("ambiguous_anchor", 1, Integer::sum);
				}
			}
		}
		context.record("life060.relocationInputRejected", "bounded64 sources=" + ordered.size() + "; stages=" + rejected);
		throw new AssertionError("BLOCKED_NATIVE_RELOCATION_INPUT: no bounded factual outgrown/local-absent/global-reachable input; no scene enrolled.");
	}

	private record Source(PhantomTopologyAnchor anchor, int npcId, int level) { }

	static final class Observation
	{
		private final PhantomGoal _initial;
		private final PhantomTopologyPoint _origin;
		private final int _threshold;
		private long _thresholdAt, _reportedRevision, _arrivalExp;
		private PhantomGoal _arrived;
		private PlayerNativeEvidence.Snapshot _arrival;
		private boolean _completed;
		private String _gatekeeper = "";

		Observation(PhantomGoal initial, Player player, int threshold)
		{
			_initial = initial; _threshold = threshold; _reportedRevision = initial.revision();
			_origin = new PhantomTopologyPoint(player.getX(), player.getY(), player.getZ(), player.getInstanceId());
		}

		boolean advance(PhantomTestContext context, long profileId, Player player, PhantomGoal root, PhantomBackgroundService background,
				PhantomHistoricalBackgroundService history, PhantomVisibleFarmTravel travel, PlayerNativeEvidence.Snapshot sample, long now)
		{
			if (player.getLevel() >= _threshold && _thresholdAt == 0) { _thresholdAt = now; }
			final String reason = travel.reason(profileId);
			if (reason.contains("travel.native_gatekeeper_arrived") && _gatekeeper.isEmpty()) { _gatekeeper = reason; context.record("life060.relocationGatekeeper", reason); }
			if (root.revision() != _reportedRevision)
			{
				context.record("life060.relocationPublished." + root.revision(), "product root=" + root + "; current=" + player.getX() + ',' + player.getY() + ',' + player.getZ());
				_reportedRevision = root.revision();
			}
			PhantomAssertions.assertTrue(root.revision() - _initial.revision() <= 8, "Relocation churned more than eight root revisions.");
			if (root.revision() <= _initial.revision() || root.selectedAnchor().equals(_initial.selectedAnchor()))
			{
				PhantomAssertions.assertTrue(_thresholdAt == 0 || now - _thresholdAt < TimeUnit.SECONDS.toNanos(45), "Earned threshold outgrew the old farm but production selected no distant root; reason=" + history.visibleRecoveryReason(profileId));
				return false;
			}
			final var state = background.acquisitionSnapshot(profileId).orElseThrow();
			final var live = new PhantomTopologyPoint(player.getX(), player.getY(), player.getZ(), player.getInstanceId());
			if (!state.position().committedAnchorId().equals(PhantomBackgroundGoalSpec.parse(root).anchorId()) || _origin.distanceSquared2D(live) <= 4_000_000L) { return false; }
			if (_arrived == null || !_arrived.equals(root))
			{
				_arrived = root; _arrival = sample; _arrivalExp = player.getExp();
				context.record("life060.relocationArrival", "native committed arrival=" + state.position() + "; product goal=" + root + "; sample=" + sample);
			}
			_completed = sample.killSequence() > _arrival.killSequence() && sample.damageSequence() > _arrival.damageSequence() && sample.expGained() > _arrival.expGained() && sample.spGained() > _arrival.spGained() && player.getExp() > _arrivalExp;
			return _completed;
		}

		void verify(PhantomTestContext context, Player player, PlayerNativeEvidence.Snapshot sample)
		{
			context.record("life060.relocationResult", "completed=" + _completed + "; initial=" + _initial + "; arrived=" + _arrived + "; arrivalEXP=" + _arrivalExp + "; actualEXP=" + player.getExp() + "; current sample=" + sample + "; gatekeeper=" + _gatekeeper);
			PhantomAssertions.assertTrue(_completed, "Production outgrown farm -> different suitable root -> native arrival -> NEW kill/EXP/SP did not complete.");
		}
	}
}
