/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.data.MerchantPriceConfigTable;
import org.l2jmobius.gameserver.data.xml.InitialEquipmentData;
import org.l2jmobius.gameserver.data.xml.InitialShortcutData;
import org.l2jmobius.gameserver.data.xml.MapRegionData;
import org.l2jmobius.gameserver.data.xml.SpawnData;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.instance.Merchant;
import org.l2jmobius.gameserver.phantoms.navigation.L2jNavigationBackend;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCancellationToken;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest;
import org.l2jmobius.gameserver.scripting.ScriptEngine;

/** One compact OWN074 native acceptance. Fixture stock is declared separately from measured rewards. */
public final class PhantomLivingSystems074Native
{
	public static void main(String[] args) throws Exception
	{
		if (!PhantomTask074Lane.enabled() || args.length != 1 || !Set.of("preflight", "bootstrap-test", "bootstrap-release", "route", "supply", "accept", "continue", "followup", "resume", "selection", "inspect").contains(args[0])) { throw new IllegalArgumentException("Explicit finite OWN074 action required."); }
		final var module = Path.of(System.getProperty("phantom.module.root")).toRealPath();
		if (args[0].equals("preflight") || args[0].startsWith("bootstrap-")) { PhantomTask074Lane.environment(module, args[0]); return; }
		if (args[0].equals("inspect"))
		{
			try
			{
				PhantomTask074Lane.initialize(module, Path.of(System.getProperty("phantom.test.config")));
				try (var connection = DatabaseFactory.getConnection())
				{
					connection.setReadOnly(true);
					try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT charId,char_name,level,exp,sp,curHp,x,y,z FROM characters WHERE account_name LIKE 'task074%' ORDER BY charId")) { while (rows.next()) { final var values = new java.util.ArrayList<String>(); for (int column = 1; column <= 9; column++) { values.add(rows.getString(column)); } System.out.println("OWN074_ACTOR " + values); } }
					try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT owner_id,object_id,item_id,count,loc FROM items WHERE owner_id IN(SELECT charId FROM characters WHERE account_name LIKE 'task074%') ORDER BY owner_id,object_id")) { while (rows.next()) { System.out.println("OWN074_ITEM " + rows.getInt(1) + ':' + rows.getInt(2) + ':' + rows.getInt(3) + ':' + rows.getLong(4) + ':' + rows.getString(5)); } }
				}
			}
			finally { DatabaseFactory.close(); }
			return;
		}
		final var task = PhantomTask074Lane.task(module); final var report = new StringBuilder("stage\tevidence\n");
		final var loaded = Path.of(org.l2jmobius.gameserver.phantoms.commerce.L2jPhantomSupplyJourney.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
		report.append("SOURCE074\tloadedProduction=").append(loaded).append(";sha256=").append(Files.isRegularFile(loaded) ? java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(loaded))) : "FOCUSED_CLASSES_NOT_JAR").append('\n');
		try
		{
			PhantomHeadlessPlayerTestEnvironment.initializeNativeInfrastructure(new PhantomTestContext(740074L, module, task.resolve("reports")));
			InitialEquipmentData.getInstance(); InitialShortcutData.getInstance(); MapRegionData.getInstance(); ScriptEngine.getInstance().executeScript(ScriptEngine.MASTER_HANDLER_FILE);
			MerchantPriceConfigTable.getInstance().loadInstances(); SpawnData.getInstance(); MerchantPriceConfigTable.getInstance().updateReferences();
			if (args[0].equals("route")) { route(report); }
			else
			{
				final var topologyBackend = new org.l2jmobius.gameserver.phantoms.topology.L2jTopologyValidationBackend();
				final var topology = new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery(new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyLoader(Path.of("data/phantoms/topology"), topologyBackend, org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPolicy.productionDefaults()).load(1), topologyBackend, new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyMetrics());
				final var knowledgePolicy = org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgePolicy.productionDefaults(); final var backend = new org.l2jmobius.gameserver.phantoms.knowledge.L2jGameKnowledgeBackend();
				final var knowledge = new org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeService(new org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeBuilder(backend, new org.l2jmobius.gameserver.phantoms.knowledge.PhantomStaticManorParser(Path.of("data/Seeds.xml"), knowledgePolicy), new org.l2jmobius.gameserver.phantoms.knowledge.PhantomCuratedKnowledgeParser(Path.of("data/phantoms/knowledge"), backend, knowledgePolicy), topology, knowledgePolicy));
				if (!knowledge.start()) { throw new IllegalStateException("OWN074 native knowledge unavailable."); }
				try { PhantomSupply074Native.run(knowledge.query(), topology, report, args[0].equals("followup") ? "resume" : args[0]); }
				catch (Throwable failure) { report.append("A_RESULT\tPARTIAL;").append(failure).append('\n'); failure.printStackTrace(); }
				if (args[0].equals("accept") || args[0].equals("continue") || args[0].equals("followup") || args[0].equals("resume"))
				{
					final var farmFile = task.resolve("farm074-owned.properties"); final var retained = new java.util.Properties();
					try
					{
						if (Files.exists(farmFile)) { try (var reader = Files.newBufferedReader(farmFile)) { retained.load(reader); } }
						final var profiles = org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository.open();
						final var progressionPolicy = org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionPolicy.productionDefaults();
						final var progression = new org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionCatalogBuilder().build(new org.l2jmobius.gameserver.phantoms.progression.L2jProgressionBackend(null, Path.of("."), knowledge::query).load(progressionPolicy), progressionPolicy);
						final var commerce = new org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalogLoader(Path.of(".")).load().catalog();
						final var routes = org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel.load(Path.of("data/phantoms/travel/high-five-normal-gk.xml"), topology);
						if (!retained.containsKey("farm074.ownedProfile"))
						{
							if (args[0].equals("resume")) { throw new IllegalStateException("OWN074 retained B fixture absent; no reseed."); }
							PhantomFarm074OwnedNative.prepare(profiles, knowledge.query(), topology, progression, commerce, routes, retained, report);
						}
						PhantomFarm074OwnedNative.run(profiles, knowledge.query(), topology, progression, commerce, routes, retained, report, args[0].equals("resume"));
					}
					catch (Throwable failure) { report.append("B_RESULT\tPARTIAL;").append(failure).append('\n'); failure.printStackTrace(); }
					finally { if (!retained.isEmpty()) { try (var writer = Files.newBufferedWriter(farmFile)) { retained.store(writer, "OWN074 retained B owner/cursor; never reseed"); } } }
				}
				if (args[0].equals("accept") || args[0].equals("continue"))
				{
					try { PhantomMarket074Native.run(knowledge.query(), topology, report); } catch (Throwable failure) { report.append("MARKET074_RESULT\tPARTIAL;").append(failure).append('\n'); }
					try { PhantomMarket074Native.runManufacture(knowledge.query(), topology, report); } catch (Throwable failure) { report.append("MANUFACTURE074_RESULT\tPARTIAL;").append(failure).append('\n'); }
					if (args[0].equals("accept")) { try { PhantomCrystal074Native.run(report); } catch (Throwable failure) { report.append("CRYST074_RESULT\tPARTIAL;").append(failure).append('\n'); } }
					try { PhantomPvp074Native.run(knowledge, topology, report); } catch (Throwable failure) { report.append("D_RESULT\tPARTIAL;").append(failure).append('\n'); }
				}
				if (args[0].equals("followup")) { PhantomPvp074Native.runRecovery(knowledge, topology, report); }
			}
		}
		catch (Throwable failure) { report.append("FAILURE\t").append(failure).append('\n'); throw failure; }
		finally
		{
			Files.writeString(task.resolve("reports/native074-" + args[0] + '-' + System.currentTimeMillis() + ".tsv"), report, java.nio.file.StandardOpenOption.CREATE_NEW);
			try { ThreadPool.shutdown(); } finally { DatabaseFactory.close(); }
		}
	}

	/** Native read-only reproduction of the last published073 location; no historical actor/DB is read. */
	private static void route(StringBuilder report)
	{
		final var paths = new L2jNavigationBackend(); final var policy = PhantomNavigationPolicy.productionDefaults();
		final var origin = new PhantomNavigationPoint(-64316, 77113, -3280, 0);
		final var merchant = World.getInstance().getVisibleObjects().stream().filter(value -> value instanceof Merchant npc && npc.getId() == 30420 && npc.isSpawned()).map(value -> (Merchant) value).findFirst().orElseThrow();
		final var target = paths.walkSurface(new PhantomNavigationPoint(merchant.getX(), merchant.getY(), merchant.getZ(), 0), 256).orElseThrow();
		final var checkpoints = PhantomNativeTravelActions.localWalkDestinations(origin, target, policy.maximumLocalStraightDistance());
		report.append("ROUTE_INPUT\torigin=").append(origin).append(";floor=").append(org.l2jmobius.gameserver.geoengine.GeoEngine.getInstance().getHeight(origin.x(), origin.y(), origin.z())).append(";merchant=").append(merchant.getId()).append('/').append(merchant.getObjectId()).append(";actual=").append(merchant.getLocation()).append(";target=").append(target).append(";checkpoints=").append(checkpoints.size()).append('\n');
		int index = -1;
		for (var destination : checkpoints)
		{
			final long now = System.nanoTime();
			final var result = paths.findWalkCorridor(new PhantomNavigationRequest(740074, origin, destination, now, now + policy.defaultRequestDeadlineNanos(), policy.maximumRouteDistance()), new PhantomNavigationCancellationToken(), policy, System::nanoTime);
			report.append("ROUTE_ATTEMPT\tindex=").append(++index).append(";from=").append(origin).append(";to=").append(destination).append(";status=").append(result.status()).append(";points=").append(result.points()).append('\n');
			System.out.println("OWN074_ROUTE index=" + index + ";status=" + result.status() + ";destination=" + destination);
			if (result.status() == org.l2jmobius.gameserver.phantoms.navigation.PhantomWalkCorridorPlanner.Status.FOUND) { break; }
		}
	}
}
