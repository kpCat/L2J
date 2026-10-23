/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.sql.DriverManager;

/** Explicit OWN070 development migration; never runs at server startup or reseeds stock. */
public final class PhantomEconomy070Schema
{
	public static void main(String[] args) throws Exception
	{
		if (args.length != 0 || !PhantomTask070Lane.enabled()) { throw new IllegalArgumentException("Explicit070 migration only."); }
		final Path module = Path.of(System.getProperty("phantom.module.root")).toRealPath();
		final Path task = module.getParent().resolve(".phantom-local/task070").toRealPath();
		for (String role : new String[] {"test", "release"})
		{
			final String schema = "l2jmobiush5_phantom_" + role + "_070";
			final var config = PhantomTask070Lane.read(task.resolve("private/Database." + role + ".ini"));
			if (!config.getProperty("URL", "").startsWith("jdbc:mysql://127.0.0.1:3308/" + schema + "?")) { throw new IllegalArgumentException("Foreign migration refused."); }
			Class.forName(config.getProperty("Driver"));
			try (var connection = DriverManager.getConnection(config.getProperty("URL"), config.getProperty("Login"), config.getProperty("Password")))
			{
				if (!schema.equals(connection.getCatalog())) { throw new IllegalArgumentException("Catalog differs."); }
				try (var query = connection.createStatement(); var rows = query.executeQuery("SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE DB=DATABASE() AND ID<>CONNECTION_ID()"))
				{ rows.next(); if (rows.getInt(1) != 0) { throw new IllegalStateException("OWN070 must be STOPPED."); } }
				for (String[] column : new String[][] {{"max_stock", "INT NOT NULL DEFAULT 1"}, {"restock_days", "INT NOT NULL DEFAULT 0"}, {"source_price_revision", "CHAR(64) NOT NULL DEFAULT ''"}})
				{
					boolean exists;
					try (var rows = connection.getMetaData().getColumns(schema, null, "economy070_stock", column[0])) { exists = rows.next(); }
					if (!exists) { try (var query = connection.createStatement()) { query.execute("ALTER TABLE economy070_stock ADD COLUMN " + column[0] + " " + column[1]); } }
				}
				System.out.println("OWN070_MIGRATED_STOCK_METADATA " + schema + "; no reseed/count writes");
			}
		}
	}
}
