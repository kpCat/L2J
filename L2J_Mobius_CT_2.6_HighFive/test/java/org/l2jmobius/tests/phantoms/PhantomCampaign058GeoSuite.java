/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;

import org.l2jmobius.gameserver.config.GeoEngineConfig;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;

/** Verifies actual local native geometry; this does not claim companion movement. */
public final class PhantomCampaign058GeoSuite implements PhantomTestSuite
{
	private PhantomHeadlessPlayerTestEnvironment _environment;

	@Override
	public String id()
	{
		return "campaign058-native-geo";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(58005811L, context.seed(), "Native geometry setup used the wrong seed.");
		PhantomAssertions.assertTrue(PhantomCampaign058DatabaseLane.enabled(), "Exact own058t native geometry lane required.");
		final Path runtime = Path.of("").toRealPath();
		final Path geo = runtime.resolve("data/geodata");
		final List<Path> regions;
		try (var files = Files.list(geo))
		{
			regions = files.filter(path -> path.getFileName().toString().endsWith(".l2j")).limit(5).toList();
		}
		long bytes = 0;
		for (Path region : regions)
		{
			PhantomAssertions.assertTrue(Files.isRegularFile(region) && !Files.isSymbolicLink(region) && region.toRealPath().startsWith(runtime), "Native geometry input escapes own058t runtime.");
			bytes = Math.addExact(bytes, Files.size(region));
		}
		validateInput(runtime, geo.toRealPath(), regions.stream().map(path -> path.getFileName().toString()).toList(), bytes);
		context.record("geo058.input", regions.stream().map(path -> path.getFileName().toString()).toList() + "; bytes=" + bytes);
		_environment = new PhantomHeadlessPlayerTestEnvironment();
		_environment.initialize(context);
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-owned-region-is-loaded-and-height-is-native", context ->
		{
			PhantomAssertions.assertEquals(2, GeoEngineConfig.PATHFINDING, "Native fixture did not load its actual pathfinding configuration.");
			PhantomAssertions.assertEquals(Path.of("").toRealPath().resolve("data/geodata"), GeoEngineConfig.GEODATA_PATH.toRealPath(), "Native geometry used a different resource root.");
			final var point = PlayerTemplateData.getInstance().getTemplate(0).getCreationPoint();
			final GeoEngine geo = GeoEngine.getInstance();
			PhantomAssertions.assertTrue(geo.hasGeoPos(GeoEngine.getGeoX(point.getX()), GeoEngine.getGeoY(point.getY())), "Native fixture did not load the actual stock creation-point region.");
			final int height = geo.getHeight(point.getX(), point.getY(), point.getZ());
			PhantomAssertions.assertEquals(height, geo.getHeight(point.getX(), point.getY(), height), "Native height is not stable.");
			context.record("geo058.loadedPoint", point.getX() + "," + point.getY() + "," + height);
			context.record("geo058.source", GeoEngineConfig.GEODATA_PATH.toRealPath());
		});
	}

	static void validateInput(Path runtime, Path geo, List<String> regions, long bytes)
	{
		final Path expected = PhantomCampaign058DatabaseLane.MODULE.resolve(".phantom-local/contract058t/runtime/game");
		if (!expected.equals(runtime) || !expected.resolve("data/geodata").equals(geo) || regions.isEmpty() || (regions.size() > 4) || (new HashSet<>(regions).size() != regions.size()) || regions.stream().anyMatch(name -> !name.matches("(?:1[1-9]|2[0-6])_(?:1[0-9]|2[0-6])\\.l2j")) || (bytes <= 0) || (bytes > 64L * 1024 * 1024))
		{
			throw new IllegalArgumentException("Exact own058t bounded native geometry input required.");
		}
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		if (_environment != null)
		{
			_environment.shutdown();
		}
	}
}
