/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.util.Properties;

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
