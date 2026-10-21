/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import org.l2jmobius.gameserver.phantoms.party.PhantomRealPartyInvitationPolicy;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyFollowPolicy;

public final class PhantomCampaign058Suite implements PhantomTestSuite
{
	@Override
	public String id()
	{
		return "campaign058-ownership";
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-exact-mutable-fixture-is-accepted", context -> validate(PhantomCampaign058DatabaseLane.MODULE, owner(), settings()));
		registry.add("13-task065-build-jar-is-exact-and-isolated", context ->
		{
			final String previous = System.getProperty("phantom.task065.manifest");
			System.setProperty("phantom.task065.manifest", context.moduleRoot().resolve(".phantom-local/task065/test/owned.properties").toString());
			try
			{
				final Path task = context.moduleRoot().resolve(".phantom-local/task065");
				for (String build : List.of("build", "build-final")) { PhantomAssertions.assertTrue(PhantomCombatServerIntegrationSuite.campaign058JarAllowed(task.resolve(build + "/dist/libs/GameServer.jar")), "Exact065 whole JAR rejected."); }
				for (Path foreign : List.of(task.resolve("build/dist/libs/Foreign.jar"), task.resolve("runtime/libs/GameServer.jar"), context.moduleRoot().resolve(".phantom-local/task064/build/dist/libs/GameServer.jar"))) { PhantomAssertions.assertFalse(PhantomCombatServerIntegrationSuite.campaign058JarAllowed(foreign), "Foreign065 whole JAR accepted."); }
			}
			finally { if (previous == null) { System.clearProperty("phantom.task065.manifest"); } else { System.setProperty("phantom.task065.manifest", previous); } }
		});
		registry.add("12-continuation060-ordinary-jar-is-exact", context ->
		{
			for (String path : List.of(".phantom-local/contract058a/task060/build/dist/libs/GameServer.jar", ".phantom-local/contract058a/build/release060/compiled/dist/libs/GameServer.jar"))
			{
				PhantomAssertions.assertTrue(PhantomCombatServerIntegrationSuite.campaign058JarAllowed(PhantomCampaign058DatabaseLane.MODULE.resolve(path)), "Exact060 continuation JAR rejected: " + path);
			}
			for (String path : List.of(".phantom-local/contract060a/runtime/libs/GameServer.jar", ".phantom-local/contract058a/task060/build/dist/libs/Foreign.jar", ".phantom-local/contract058a/build/release061/compiled/dist/libs/GameServer.jar"))
			{
				PhantomAssertions.assertFalse(PhantomCombatServerIntegrationSuite.campaign058JarAllowed(PhantomCampaign058DatabaseLane.MODULE.resolve(path)), "Foreign060 JAR accepted: " + path);
			}
		});
		registry.add("11-continuation059-ordinary-jar-is-exact", context ->
		{
			PhantomAssertions.assertTrue(PhantomCombatServerIntegrationSuite.campaign058JarAllowed(PhantomCampaign058DatabaseLane.MODULE.resolve(".phantom-local/contract058a/task059/build/dist/libs/GameServer.jar")), "Exact059 ordinary build rejected.");
			PhantomAssertions.assertTrue(PhantomCombatServerIntegrationSuite.campaign058JarAllowed(PhantomCampaign058DatabaseLane.MODULE.resolve(".phantom-local/contract058a/build/release059/compiled/dist/libs/GameServer.jar")), "Exact059 clean archive build rejected.");
			PhantomAssertions.assertFalse(PhantomCombatServerIntegrationSuite.campaign058JarAllowed(PhantomCampaign058DatabaseLane.MODULE.resolve(".phantom-local/contract058a/build/release059/compiled/dist/libs/Foreign.jar")), "Foreign clean archive JAR accepted.");
			PhantomAssertions.assertFalse(PhantomCombatServerIntegrationSuite.campaign058JarAllowed(PhantomCampaign058DatabaseLane.MODULE.resolve(".phantom-local/contract058a/build/release061/compiled/dist/libs/GameServer.jar")), "Foreign clean archive stage accepted.");
			for (Path foreign : List.of(Path.of("C:/Users/ZBook/L2J_Mobius/L2J_Mobius_CT_2.6_HighFive/dist/libs/GameServer.jar"), PhantomCampaign058DatabaseLane.MODULE.resolve("dist/libs/GameServer.jar"), PhantomCampaign058DatabaseLane.MODULE.resolve(".phantom-local/contract059a/runtime/libs/GameServer.jar"), PhantomCampaign058DatabaseLane.MODULE.resolve(".phantom-local/contract058a/task059/build/dist/libs/Foreign.jar")))
			{
				PhantomAssertions.assertFalse(PhantomCombatServerIntegrationSuite.campaign058JarAllowed(foreign), "Foreign ordinary JAR was accepted: " + foreign);
			}
		});
		registry.add("10-only-exact-stock-registration-seed-is-recognized", context ->
		{
			PhantomAssertions.assertTrue(PhantomCampaign058PersistentLane.stockRegistration(2, "-2ad66b3f483c22be097019f55c8abdf0", ""), "Tracked stock registration seed was rejected.");
			PhantomAssertions.assertFalse(PhantomCampaign058PersistentLane.stockRegistration(1, "-2ad66b3f483c22be097019f55c8abdf0", ""), "Another server identity was treated as stock.");
			PhantomAssertions.assertFalse(PhantomCampaign058PersistentLane.stockRegistration(2, "changed", ""), "Changed stock hex identity was accepted.");
			PhantomAssertions.assertFalse(PhantomCampaign058PersistentLane.stockRegistration(2, "-2ad66b3f483c22be097019f55c8abdf0", "foreign"), "Changed stock host was accepted.");
		});
		registry.add("08-persistent-infrastructure-is-exact-and-separate", context ->
		{
			validatePersistent(PhantomCampaign058PersistentLane.MODULE, persistentOwner(), persistentSettings(), false);
			validatePersistent(PhantomCampaign058PersistentLane.MODULE, persistentOwner(), persistentSettings(), true);
		});
		registry.add("09-persistent-rejects-other-root-schema-role-and-fixture-authority", context ->
		{
			final String prior = System.getProperty("phantom.campaign058.persistent");
			boolean fixtureRejected = false;
			System.setProperty("phantom.campaign058.persistent", "true");
			try { new PhantomHeadlessPlayerTestEnvironment().initialize(context); }
			catch (IllegalArgumentException expected) { fixtureRejected = true; }
			finally { if (prior == null) { System.clearProperty("phantom.campaign058.persistent"); } else { System.setProperty("phantom.campaign058.persistent", prior); } }
			PhantomAssertions.assertTrue(fixtureRejected, "Persistent lane reached mutable fixture initialization.");
			PhantomAssertions.assertFalse(org.l2jmobius.commons.database.DatabaseFactory.isInitialized(), "Persistent fixture rejection opened the database pool.");
			reject(() -> validatePersistent(Path.of("C:/Users/ZBook/L2J_Mobius"), persistentOwner(), persistentSettings(), false));
			for (String role : List.of("MUTABLE_FIXTURE", "RELEASE_CANDIDATE", ""))
			{
				final Properties owner = persistentOwner(); owner.setProperty("role", role);
				reject(() -> validatePersistent(PhantomCampaign058PersistentLane.MODULE, owner, persistentSettings(), false));
			}
			for (String schema : List.of("l2jmobiush5_phantom_test_058t", "l2jmobiush5_phantom_release_057a", "l2jmobiush5_phantom_test_053a"))
			{
				final Properties settings = persistentSettings(); settings.setProperty("URL", "jdbc:mysql://127.0.0.1:3308/" + schema + "?useSSL=false");
				reject(() -> validatePersistent(PhantomCampaign058PersistentLane.MODULE, persistentOwner(), settings, true));
			}
		});
		registry.add("02-wrong-root-and-role-are-rejected", context ->
		{
			reject(() -> validate(Path.of("C:/Users/ZBook/L2J_Mobius/L2J_Mobius_CT_2.6_HighFive"), owner(), settings()));
			for (String role : new String[] { "CUMULATIVE", "RELEASE", "" })
			{
				final Properties owner = owner();
				owner.setProperty("role", role);
				reject(() -> validate(PhantomCampaign058DatabaseLane.MODULE, owner, settings()));
			}
		});
		registry.add("03-foreign-schema-and-owner-are-rejected", context ->
		{
			for (String schema : new String[] { "l2jmobiush5", "l2jmobiush5_phantom_release_057a", "l2jmobiush5_phantom_test_053a", "l2jmobiush5_phantom_test_058a" })
			{
				final Properties settings = settings();
				settings.setProperty("URL", "jdbc:mysql://127.0.0.1:3308/" + schema + "?useSSL=false");
				reject(() -> validate(PhantomCampaign058DatabaseLane.MODULE, owner(), settings));
			}
			final Properties owner = owner();
			owner.setProperty("owner", "TASK053_CONTRACT");
			reject(() -> validate(PhantomCampaign058DatabaseLane.MODULE, owner, settings()));
		});
		registry.add("04-real-invitation-policy-strict-data-contract", context ->
		{
			final Path source = context.moduleRoot().resolve("dist/game/data/phantoms/party/high-five-real-party-invitation-v1.xml");
			PhantomAssertions.assertEquals(new PhantomRealPartyInvitationPolicy.Settings(true, 0), PhantomRealPartyInvitationPolicy.loadSettings(source), "Tracked real invitation policy changed unexpectedly.");
			final String xml = Files.readString(source);
			Files.createDirectories(context.reportsDirectory());
			final Path invalid = context.reportsDirectory().resolve("invalid-real-invitation-policy.xml");
			for (String mutation : new String[] { xml.replace("minimumPreference=\"0\"", "minimumPreference=\"3001\""), xml.replace("enabled=\"true\"", "enabled=\"yes\""), xml.replace("version=\"1\"", "version=\"2\""), xml.replace(" />", "><unexpected /></realPartyInvitationPolicy>"), xml.replace("minimumPreference=", "unknown=") })
			{
				Files.writeString(invalid, mutation);
				reject(() -> PhantomRealPartyInvitationPolicy.loadSettings(invalid));
			}
		});
		registry.add("05-real-invitation-preference-hard-bounds", context ->
		{
			reject(() -> new PhantomRealPartyInvitationPolicy.Settings(true, -3001));
			reject(() -> new PhantomRealPartyInvitationPolicy.Settings(true, 3001));
			new PhantomRealPartyInvitationPolicy.Settings(false, -3000);
			new PhantomRealPartyInvitationPolicy.Settings(true, 3000);
		});
		registry.add("06-native-geometry-root-and-resource-bounds", context ->
		{
			final Path runtime = PhantomCampaign058DatabaseLane.MODULE.resolve(".phantom-local/contract058t/runtime/game");
			final Path geo = runtime.resolve("data/geodata");
			PhantomCampaign058GeoSuite.validateInput(runtime, geo, List.of("17_25.l2j"), 5434560);
			reject(() -> PhantomCampaign058GeoSuite.validateInput(Path.of("C:/Users/ZBook/L2J_Mobius"), geo, List.of("17_25.l2j"), 1));
			reject(() -> PhantomCampaign058GeoSuite.validateInput(runtime, runtime.resolve("foreign"), List.of("17_25.l2j"), 1));
			reject(() -> PhantomCampaign058GeoSuite.validateInput(runtime, geo, List.of("../17_25.l2j"), 1));
			reject(() -> PhantomCampaign058GeoSuite.validateInput(runtime, geo, List.of("17_25.l2j", "17_25.l2j"), 1));
			reject(() -> PhantomCampaign058GeoSuite.validateInput(runtime, geo, List.of("17_25.l2j", "17_24.l2j", "18_25.l2j", "18_24.l2j", "19_25.l2j"), 1));
			reject(() -> PhantomCampaign058GeoSuite.validateInput(runtime, geo, List.of("17_25.l2j"), 64L * 1024 * 1024 + 1));
		});
		registry.add("07-follow-policy-is-explicit-and-bounded", context ->
		{
			PhantomAssertions.assertEquals(PhantomPartyFollowPolicy.defaults(), PhantomPartyFollowPolicy.load(context.moduleRoot().resolve("dist/game/data/phantoms/party/high-five-party-follow-v1.xml")), "Tracked follow policy changed unexpectedly.");
			reject(() -> new PhantomPartyFollowPolicy(true, 250, 250, 64, 16, 6000, 30000));
			reject(() -> new PhantomPartyFollowPolicy(true, 350, 250, 64, 0, 6000, 30000));
			reject(() -> new PhantomPartyFollowPolicy(true, 350, 250, 64, 16, 6000, 1));
			final String xml = Files.readString(context.moduleRoot().resolve("dist/game/data/phantoms/party/high-five-party-follow-v1.xml"));
			Files.createDirectories(context.reportsDirectory());
			final Path invalid = context.reportsDirectory().resolve("invalid-follow-policy.xml");
			for (String mutation : List.of(xml.replace("enabled=\"true\"", "enabled=\"yes\""), xml.replace("version=\"1\"", "version=\"2\""), xml.replace("waypointRadius=\"64\"", "waypointRadius=\"16\"")))
			{
				Files.writeString(invalid, mutation);
				reject(() -> PhantomPartyFollowPolicy.load(invalid));
			}
		});
	}

	private static Properties persistentOwner()
	{
		final Properties owner = new Properties();
		owner.setProperty("version", "1"); owner.setProperty("owner", "TASK058_CAMPAIGN"); owner.setProperty("role", "CUMULATIVE_INTEGRATION");
		owner.setProperty("schema", PhantomCampaign058PersistentLane.DATABASE); owner.setProperty("runtimeRoot", PhantomCampaign058PersistentLane.RUNTIME.toString());
		owner.setProperty("clientLoginPort", "2138"); owner.setProperty("gamePort", "7838"); owner.setProperty("controlPort", "9138");
		return owner;
	}

	private static Properties persistentSettings()
	{
		final Properties settings = new Properties();
		settings.setProperty("URL", "jdbc:mysql://127.0.0.1:3308/" + PhantomCampaign058PersistentLane.DATABASE + "?useSSL=false");
		settings.setProperty("MaximumDatabaseConnections", "8");
		return settings;
	}

	private static void validatePersistent(Path module, Properties owner, Properties settings, boolean runtime)
	{
		final Path root = PhantomCampaign058PersistentLane.LANE.resolve(runtime ? "runtime" : "config-input");
		PhantomCampaign058PersistentLane.validateIdentity(module, root.resolve("game/config/Database.ini"), root.resolve("ownership058.properties"), runtime ? root.resolve("game") : module, owner, settings, runtime);
	}

	private static Properties owner()
	{
		final Properties value = new Properties();
		value.setProperty("owner", "TASK058_CAMPAIGN");
		value.setProperty("role", "MUTABLE_FIXTURE");
		value.setProperty("database", PhantomCampaign058DatabaseLane.DATABASE);
		return value;
	}

	private static Properties settings()
	{
		final Properties value = new Properties();
		value.setProperty("URL", "jdbc:mysql://127.0.0.1:3308/" + PhantomCampaign058DatabaseLane.DATABASE + "?useSSL=false");
		value.setProperty("MaximumDatabaseConnections", "4");
		return value;
	}

	private static void validate(Path module, Properties owner, Properties settings)
	{
		final Path lane = module.resolve(".phantom-local/contract058t");
		PhantomCampaign058DatabaseLane.validateIdentity(module, lane.resolve("test/Database.test.ini"), lane.resolve("test/owned.properties"), lane.resolve("runtime/game"), owner, settings);
	}

	private static void reject(Runnable action)
	{
		boolean rejected = false;
		try
		{
			action.run();
		}
		catch (IllegalArgumentException expected)
		{
			rejected = true;
		}
		PhantomAssertions.assertTrue(rejected, "Foreign fixture identity was accepted.");
	}
}
