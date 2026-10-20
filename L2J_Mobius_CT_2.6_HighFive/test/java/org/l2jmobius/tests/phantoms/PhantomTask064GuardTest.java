/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.util.Properties;

/** No JDBC or processes: the new fixture lane must not inherit historical authority. */
public final class PhantomTask064GuardTest
{
	public static void main(String[] args) throws Exception
	{
		final Path module = Path.of(System.getProperty("phantom.module.root")).toAbsolutePath().normalize();
		final Path lane = module.resolve(".phantom-local/task064");
		final Properties owner = new Properties();
		owner.setProperty("owner", "TASK064_CAMPAIGN");
		owner.setProperty("role", "MUTABLE_FIXTURE");
		owner.setProperty("database", "l2jmobiush5_phantom_test_064");
		final Properties settings = new Properties();
		settings.setProperty("URL", "jdbc:mysql://127.0.0.1:3308/l2jmobiush5_phantom_test_064?useSSL=false");
		settings.setProperty("MaximumDatabaseConnections", "4");
		PhantomCampaign058DatabaseLane.validateIdentity(module, lane.resolve("test/Database.test.ini"), lane.resolve("test/owned.properties"), lane.resolve("runtime/game"), owner, settings);
		System.out.println("PASS task064.exact-new-fixture");
		for (String key : new String[] {"owner", "role", "database"})
		{
			final Properties wrong = new Properties(); wrong.putAll(owner); wrong.setProperty(key, "FOREIGN");
			reject(() -> PhantomCampaign058DatabaseLane.validateIdentity(module, lane.resolve("test/Database.test.ini"), lane.resolve("test/owned.properties"), lane.resolve("runtime/game"), wrong, settings));
		}
		final Properties oldSchema = new Properties(); oldSchema.putAll(settings);
		oldSchema.setProperty("URL", "jdbc:mysql://127.0.0.1:3308/l2jmobiush5_phantom_test_058t?useSSL=false");
		reject(() -> PhantomCampaign058DatabaseLane.validateIdentity(module, lane.resolve("test/Database.test.ini"), lane.resolve("test/owned.properties"), lane.resolve("runtime/game"), owner, oldSchema));
		System.out.println("PASS task064.foreign-owner-role-schema");
		reject(() -> PhantomCampaign058DatabaseLane.validateIdentity(module, lane.resolve("test/Database.test.ini"), lane.resolve("test/owned.properties"), module.resolve("dist/game"), owner, settings));
		reject(() -> PhantomCampaign058DatabaseLane.validateIdentity(module, lane.resolve("foreign/Database.test.ini"), lane.resolve("test/owned.properties"), lane.resolve("runtime/game"), owner, settings));
		System.setProperty("phantom.task064.persistent", "true");
		try
		{
			boolean refused = false;
			try { new PhantomHeadlessPlayerTestEnvironment().initialize(new PhantomTestContext(64006401, module, lane.resolve("unused")), 0L); }
			catch (IllegalArgumentException expected) { refused = true; }
			if (!refused) { throw new AssertionError("Persistent064 inherited mutable fixture cleanup authority."); }
		}
		finally { System.clearProperty("phantom.task064.persistent"); }
		System.out.println("PASS task064.wrong-root-config-and-persistent-fence");
	}

	private static void reject(Runnable action)
	{
		try { action.run(); }
		catch (IllegalArgumentException expected) { return; }
		throw new AssertionError("Foreign TASK064 binding accepted.");
	}
}
