/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.util.List;

import org.l2jmobius.gameserver.phantoms.background.L2jPhantomBackgroundAuthority;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Position;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchor;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;

/** The durable ordinal must advance real scene coverage, including after restart. */
public final class PhantomTrainingScenarioChecks implements PhantomTestSuite
{
	@Override public String id() { return "training-scenarios"; }
	@Override public void register(PhantomTestRegistry registry)
	{
		registry.add("seed-order-independent-of-world-enumeration", context ->
		{
			var first = PhantomTrainingScenarios.orderedKeys(List.of("npc21799/A", "npc21798/S", "npc21798/A"), 42, 0);
			var second = PhantomTrainingScenarios.orderedKeys(List.of("npc21798/A", "npc21799/A", "npc21798/S"), 42, 0);
			PhantomAssertions.assertEquals(first, second, "World discovery order must not change the seeded scene queue.");
		});
		registry.add("durable-ordinal-covers-next-scene", context ->
		{
			var keys = List.of("a", "b", "c");
			var initial = PhantomTrainingScenarios.orderedKeys(keys, 52, 0);
			for (int ordinal = 0; ordinal < keys.size(); ordinal++)
			{
				PhantomAssertions.assertEquals(initial.get(ordinal), PhantomTrainingScenarios.orderedKeys(keys, 52, ordinal).getFirst(), "Resume must continue coverage.");
			}
			PhantomAssertions.assertTrue(PhantomTrainingScenarios.orderedKeys(List.of(), 52, 99).isEmpty(), "No feasible scene must terminate boundedly.");
		});
		registry.add("preparation-keeps-real-zero-tolerance-anchor", context ->
		{
			var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
			factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
			factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
			factory.setXIncludeAware(false); factory.setExpandEntityReferences(false);
			var document = factory.newDocumentBuilder().parse(context.moduleRoot().resolve("dist/game/data/phantoms/topology/high-five-closed-imperial.xml").toFile());
			org.w3c.dom.Element actual = null;
			for (int index = 0; index < document.getElementsByTagName("anchor").getLength(); index++)
			{
				var element = (org.w3c.dom.Element) document.getElementsByTagName("anchor").item(index);
				if ("generated.farm.72007cc387f17e963e4fcf2c.anchor".equals(element.getAttribute("id"))) { actual = element; break; }
			}
			PhantomAssertions.assertTrue(actual != null, "Proven Imperial Tomb anchor is missing.");
			var point = new PhantomTopologyPoint(Integer.parseInt(actual.getAttribute("x")), Integer.parseInt(actual.getAttribute("y")), Integer.parseInt(actual.getAttribute("z")), 0);
			var anchor = new PhantomTopologyAnchor(actual.getAttribute("id"), PhantomTopologyAnchorRole.FARMING, actual.getAttribute("nodeId"), point,
				null, null, Integer.parseInt(actual.getAttribute("tolerance")), List.of("content-farming"), List.of("data/spawns/Goddard/ImperialTomb.xml"));
			PhantomAssertions.assertEquals(0, anchor.validationTolerance(), "Fixture must retain the actual zero-tolerance contract.");
			// Existing BackgroundSuite uses this same bounded reflection seam for native anchor contracts.
			var method = L2jPhantomBackgroundAuthority.class.getDeclaredMethod("nativeAnchorPositionAllowed", int.class, int.class, int.class, int.class,
				Position.class, PhantomTopologyAnchorRole.class, int.class, java.util.function.IntSupplier.class);
			method.setAccessible(true);
			var canonical = new Position(0, point.x(), point.y(), point.z(), 0, anchor.id());
			PhantomAssertions.assertFalse((Boolean) method.invoke(null, point.x() + 64, point.y(), point.z(), 0, canonical, anchor.role(), anchor.validationTolerance(),
				(java.util.function.IntSupplier) point::z), "Native owner must reject the invented 64-unit preparation offset.");
			PhantomAssertions.assertEquals(point, PhantomTrainingScenarios.preparationPoint(anchor), "Generator must keep the actual canonical anchor.");
		});
		registry.add("actual-weapon-enchant-defines-stable-scenario", context ->
		{
			String source = "21798@generated.farm.72007cc387f17e963e4fcf2c.anchor";
			String ordinary = PhantomTrainingScenarios.physicalId(source, 94, 78, "S", 180488, -75315, -2728, List.of(1230, 1339), 3, 0);
			String enchanted = PhantomTrainingScenarios.physicalId(source, 94, 78, "S", 180488, -75315, -2728, List.of(1230, 1339), 3, 4);
			PhantomAssertions.assertFalse(ordinary.equals(enchanted), "Genuinely different equipped weapon enchant must have a different physical scenario identity.");
			PhantomAssertions.assertEquals(enchanted, PhantomTrainingScenarios.physicalId(source, 94, 78, "S", 180488, -75315, -2728, List.of(1230, 1339), 3, 4), "Restart or generation must not relabel identical actual equipment.");
			PhantomAssertions.assertEquals(PhantomTrainingScenarios.sceneSplit(enchanted), PhantomTrainingScenarios.sceneSplit(PhantomTrainingScenarios.physicalId(source, 94, 78, "S", 180488, -75315, -2728, List.of(1230, 1339), 3, 4)), "Actual scenario split must stay fixed across cohorts.");
			var scene = new PhantomTrainingScenarios.Scene(enchanted, source, 21798, 94, 78, "S", 4, 180488, -75315, -2728, List.of(1230, 1339), PhantomTrainingScenarios.sceneSplit(enchanted));
			PhantomAssertions.assertEquals(scene, PhantomTrainingScenarios.Scene.parse(scene.row()), "Sealed scene round-trip must preserve actual weapon enchant.");
			PhantomAssertions.assertEquals("5f85f40a97bfd364c853111771c922b4202f1c7588dba14ca01e52f72cb9cd3d",
				PhantomTrainingScenarios.physicalId("21799@generated.farm.72007cc387f17e963e4fcf2c.anchor", 94, 77, "S", 180488, -75315, -2728, List.of(1230, 1339), 3, 4),
				"Existing native TUNING equipment must retain its exact physical identity.");
		});
	}
}
