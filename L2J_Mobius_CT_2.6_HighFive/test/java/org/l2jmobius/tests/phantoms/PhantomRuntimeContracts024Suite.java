/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundAuthority;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState;
import org.l2jmobius.gameserver.phantoms.background.PhantomNativeContext;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchor;

/** Composed TASK024 contracts use actual production authority and exact owned clone fixtures. */
public final class PhantomRuntimeContracts024Suite implements PhantomTestSuite
{
	private final PhantomHeadlessPlayerTestEnvironment _environment = new PhantomHeadlessPlayerTestEnvironment();
	private PhantomBackgroundSuite.ProductionAuthorityFixture _production;
	public static void main(String[] args)
	{
		System.exit(PhantomTestLauncher.runSuite("runtime-contracts024", new PhantomRuntimeContracts024Suite(),
			new PhantomTestContext(24002401, Path.of(args[0]), Path.of(args[1]))));
	}
	@Override public String id() { return "runtime-contracts024"; }
	@Override public void beforeAll(PhantomTestContext context) throws Exception
	{
		_environment.initialize(context);
		_production = PhantomBackgroundSuite.ProductionAuthorityFixture.start();
	}
	@Override public void afterAll(PhantomTestContext context) throws Exception
	{
		try { if (_production != null) { _production.close(); } }
		finally { _environment.shutdown(); }
	}
	@Override public void register(PhantomTestRegistry registry)
	{
		if ("locality024".equals(System.getProperty("phantom.m1.native.focus")))
		{
			registry.add("D01-factual-large-farm-area-is-local-away-from-center", this::localArea);
			return;
		}
		registry.add("B01-owned-off-area-capture-keeps-native-facts", context -> positionCapture(context, false));
		registry.add("B03-valid-position-keeps-vitality-fence", context -> positionCapture(context, true));
		registry.add("B05-native-context-legacy-ordinals-and-roundtrip", this::contextCompatibility);
	}
	private void localArea(PhantomTestContext context)
	{
		final var planner = new org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundPlanner(_production.knowledge(), _production.topology(), _production.authority());
		for (var anchor : _production.topology().snapshot().anchorsByNode().values().stream().flatMap(List::stream).sorted(java.util.Comparator.comparing(PhantomTopologyAnchor::id)).toList())
		{
			if (anchor.role() != org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole.FARMING) { continue; }
			final var area = _production.topology().findNode(anchor.nodeId()).orElseThrow().area();
			for (var vertex : area.vertices())
			{
				final var point = new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(vertex.x(), vertex.y(), anchor.point().z(), anchor.point().instanceId());
				if (!area.contains(point) || point.distanceSquared2D(anchor.point()) <= 4_000_000L) { continue; }
				context.record("D01.factualGeometry", anchor.id() + ";point=" + point + ";centerDistanceSquared=" + point.distanceSquared2D(anchor.point()));
				PhantomAssertions.assertTrue(planner.isVisibleLocal(farmGoal(anchor), point), "D01 actual FARM area membership survives representative center distance >2000.");
				final var otherInstance = new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(point.x(), point.y(), point.z(), point.instanceId() + 1);
				PhantomAssertions.assertFalse(planner.isVisibleLocal(farmGoal(anchor), otherInstance), "D01 instance identity remains exact.");
				return;
			}
		}
		throw new AssertionError("INVALID D01: no production large FARM polygon witness.");
	}
	private static PhantomGoal farmGoal(PhantomTopologyAnchor anchor)
	{
		return new PhantomGoal(24, "farm.background", PhantomGoalStatus.ACTIVE,
			new PhantomDomainRef("profile", "self"), new PhantomDomainRef("npc", "20534"), 1, 0, null,
			List.of(new PhantomDomainRef("background.farm", "20534@" + anchor.id())),
			new PhantomDomainRef("topology.anchor", anchor.id()), "farm", 1, 0, 0, 0, Map.of(), "contract024", 1);
	}
	private void positionCapture(PhantomTestContext context, boolean valid) throws Exception
	{
		final Player player = Player.load(_environment.primary().objectId());
		final var anchor = _production.topology().findAnchor("population.farming.elf.20534").orElseThrow();
		try
		{
			player.stopAllTasks(); player.setVitalityPoints(1, false);
			player.setXYZInvisible(anchor.point().x(), anchor.point().y(), anchor.point().z());
			final var goal = farmGoal(anchor);
			final var before = _production.authority().captureOwnedNative(24, player, goal, null);
			PhantomAssertions.assertEquals("SUPPORTED", before.context().eligibility().name(), "Controlled TEST baseline position/vitality must be eligible.");
			if (!valid) { player.setXYZInvisible(anchor.point().x() + 100000, anchor.point().y(), anchor.point().z()); }
			else { player.setVitalityPoints(20000, false); }
			final var captured = _production.authority().captureOwnedNative(24, player, goal, before.state());
			PhantomAssertions.assertEquals(player.getX(), captured.state().position().x(), "B01 native X must not be snapped to anchor.");
			PhantomAssertions.assertEquals(player.getY(), captured.state().position().y(), "B01 exact Y.");
			PhantomAssertions.assertEquals(player.getZ(), captured.state().position().z(), "B01 exact Z.");
			PhantomAssertions.assertEquals(player.getExp(), captured.state().progress().experience(), "B01 exact current EXP.");
			PhantomAssertions.assertEquals(player.getSp(), captured.state().progress().skillPoints(), "B01 exact current SP.");
			PhantomAssertions.assertEquals(anchor.id(), captured.state().position().committedAnchorId(), "B01 previous anchor is lineage only.");
			PhantomAssertions.assertEquals(valid ? "VITALITY_REQUIRES_NATIVE" : "POSITION_REQUIRES_NATIVE", captured.context().eligibility().name(), "B01/B03 positional and vitality fences are independent.");
			final var proof = PhantomNativeContext.completed(captured.state().identity(), captured.context().vitalityPoints(), captured.context().eligibility(), 1, new byte[]{24});
			PhantomAssertions.assertFalse(proof.simulationEligible(), "Native-only position/vitality must not authorize virtual rewards.");
			context.record("B.nativeXYZ", captured.state().position().toString());
		}
		finally { _environment.cleanupLoadedPlayer(player); }
	}
	private void contextCompatibility(PhantomTestContext context)
	{
		final var values = PhantomNativeContext.Eligibility.values();
		PhantomAssertions.assertEquals(List.of("UNKNOWN", "SUPPORTED", "VITALITY_REQUIRES_NATIVE", "POSITION_REQUIRES_NATIVE"),
			java.util.Arrays.stream(values).map(Enum::name).toList(), "B05 appended position ordinal preserves legacy 0/1/2.");
		for (var eligibility : values)
		{
			final var proof = PhantomNativeContext.completed(new PhantomBackgroundState.Identity(24, 48, 0, 0, 0), 1, eligibility, 3, new byte[]{1, 2, 3});
			PhantomAssertions.assertTrue(java.util.Arrays.equals(proof.encode(), PhantomNativeContext.decode(proof.encode()).encode()), "B05 legacy/new packet byte roundtrip.");
			PhantomAssertions.assertEquals(eligibility.name().equals("SUPPORTED"), proof.simulationEligible(), "B05 exact eligibility required.");
		}
	}
}
