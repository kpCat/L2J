/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import org.l2jmobius.gameserver.phantoms.party.PhantomRealPartyInvitationPolicy;

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
