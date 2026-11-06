/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.sql.DriverManager;
import java.util.Properties;
import java.util.List;
import java.util.Map;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.party.PhantomLogicalParty;
import org.l2jmobius.gameserver.phantoms.party.PhantomLogicalPartyCoordinator;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyStore;

/** One bounded TEST130 SQL entrypoint. No drop/reseed, no connection to a gameplay catalog. Run only after OS G0. */
public final class PhantomCoordination130Integration
{
	private static final String SCHEMA = "l2jmobiush5_phantom_test_130";
	private static final String URL = "jdbc:mysql://127.0.0.1:3308/" + SCHEMA + "?useSSL=false&serverTimezone=UTC";
	private static final String ADMIN_URL = "jdbc:mysql://127.0.0.1:3308/?useSSL=false&serverTimezone=UTC";
	private static Properties read(Path file) throws Exception
	{
		final var properties = new Properties();
		try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { properties.load(reader); }
		return properties;
	}
	public static void main(String[] args) throws Exception
	{
		if (args.length < 2 || args.length > 3 || !List.of("check", "prepare", "inspect", "test", "resume").contains(args[0])) { throw new IllegalArgumentException("check|prepare|inspect|test|resume module [read-only admin config]"); }
		final Path module = Path.of(args[1]).toRealPath();
		if (!module.getFileName().toString().equals("L2J_Mobius_CT_2.6_HighFive")) { throw new IllegalArgumentException("Exact High Five module required."); }
		final Path local = module.resolve(".phantom-local/task130"), config = local.resolve("Database.ini");
		Class.forName("com.mysql.cj.jdbc.Driver");
		if (args[0].equals("check") || args[0].equals("prepare"))
		{
			if (args.length != 3) { throw new IllegalArgumentException("Read-only admin configuration required."); }
			final var admin = read(Path.of(args[2]));
			if (!admin.getProperty("URL", "").matches("jdbc:(mysql|mariadb)://(127\\.0\\.0\\.1|localhost):3308/[^\\s]+"))
			{ throw new IllegalArgumentException("Admin input is not the attested loopback:3308 endpoint."); }
			try (var connection = DriverManager.getConnection(ADMIN_URL, admin.getProperty("Login"), admin.getProperty("Password")))
			{
				try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT VERSION(),@@port"))
				{
					if (!rows.next() || !rows.getString(1).startsWith("11.4.13-MariaDB") || rows.getInt(2) != 3308) { throw new IllegalStateException("Attested MariaDB version/port differs from TASK130 contract."); }
					System.out.println("SQL130_SERVER=" + rows.getString(1) + ";PORT=3308");
				}
				try (var statement = connection.prepareStatement("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME=?"))
				{
					statement.setString(1, SCHEMA);
					try (var rows = statement.executeQuery()) { if (!rows.next() || rows.getInt(1) != 0) { throw new IllegalStateException("TEST130 already exists; no create, drop or reseed permitted."); } }
				}
				System.out.println("SQL130_FRESH_SCHEMA_CONFIRMED;ADMIN_SECRET_OUTPUT=0");
				if (args[0].equals("check")) { return; }
				if (Files.exists(local)) { throw new IllegalStateException("Fresh TEST130 local ownership directory required."); }
				final var scripts = PhantomTestSchemaManifest.inventory(module);
				for (var script : scripts) { for (String sql : script.statements()) { if (sql.matches("(?is).*\\b(?:USE|DATABASE|GRANT|USER)\\b.*")) { throw new IllegalStateException("Cross-schema SQL is forbidden in TEST130 import: " + script.relativePath()); } } }
				final String token = java.util.UUID.randomUUID().toString().replace("-", "");
				final String user = "phantom130_" + token.substring(0, 12), password = java.util.UUID.randomUUID().toString().replace("-", "");
				Files.createDirectories(local);
				Files.writeString(local.resolve("owner.properties"), "owner=PHANTOM-WORLD-COORDINATION-130\nschema=" + SCHEMA + "\ninstallationId=" + token + "\n", StandardOpenOption.CREATE_NEW);
				try (var statement = connection.createStatement())
				{
					statement.executeUpdate("CREATE DATABASE `" + SCHEMA + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
					statement.executeUpdate("CREATE USER '" + user + "'@'127.0.0.1' IDENTIFIED BY '" + password + "'");
					statement.executeUpdate("GRANT ALL PRIVILEGES ON `" + SCHEMA + "`.* TO '" + user + "'@'127.0.0.1'");
				}
				Files.writeString(config, "Driver=com.mysql.cj.jdbc.Driver\nURL=" + URL + "\nLogin=" + user + "\nPassword=" + password + "\nMaximumDatabaseConnections=4\nTestDatabaseConnections=true\n", StandardOpenOption.CREATE_NEW);
				try (var target = DriverManager.getConnection(URL, user, password)) { StrictSqlScriptRunner.execute(target, scripts); }
				Files.writeString(local.resolve("schema.sha256"), PhantomTestSchemaManifest.current(module).aggregateSha256() + "\n", StandardOpenOption.CREATE_NEW);
				System.out.println("SQL130_PREPARED;SCHEMA=" + SCHEMA + ";SCRIPTS=" + scripts.size());
			}
			return;
		}
		if (args.length != 2 || !Files.readString(local.resolve("owner.properties")).contains("schema=" + SCHEMA + "\n")
			|| !Files.readString(local.resolve("schema.sha256")).trim().equals(PhantomTestSchemaManifest.current(module).aggregateSha256()))
		{ throw new IllegalStateException("TEST130 ownership/schema provenance mismatch."); }
		final var settings = read(config);
		if (!URL.equals(settings.getProperty("URL")) || !settings.getProperty("Login", "").matches("phantom130_[0-9a-f]{12}")) { throw new IllegalStateException("TEST130 endpoint/user mismatch."); }
		try (var connection = DriverManager.getConnection(URL, settings.getProperty("Login"), settings.getProperty("Password")))
		{
			connection.setReadOnly(true);
			try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT CURRENT_USER(),DATABASE()"))
			{
				if (!rows.next()) { throw new IllegalStateException("SQL identity unavailable."); }
				System.out.println("SQL130_EFFECTIVE_PRINCIPAL=" + rows.getString(1) + ";CATALOG=" + rows.getString(2));
				if (!rows.getString(1).equals(settings.getProperty("Login") + "@127.0.0.1") || !SCHEMA.equals(rows.getString(2)))
				{ throw new IllegalStateException("TEST130 effective SQL principal/catalog mismatch: " + rows.getString(1)); }
			}
			try (var statement = connection.createStatement(); var rows = statement.executeQuery("SHOW GRANTS"))
			{
				while (rows.next())
				{
					final String grant = rows.getString(1);
					final int on = grant.indexOf(" ON "), to = grant.indexOf(" TO ");
					if (on < 0 || to <= on) { throw new IllegalStateException("Unknown TEST130 grant format."); }
					// MariaDB SHOW GRANTS escapes literal underscores in schema privilege patterns.
					final String scope = grant.substring(on + 4, to).replace("\\_", "_");
					if (args[0].equals("inspect")) { System.out.println("SQL130_READ_ONLY_GRANT_SCOPE=" + scope); continue; }
					if (scope.equals("*.*") && grant.startsWith("GRANT USAGE ON ")) { continue; }
					// Existing MariaDB installation grants test/test_% to every newly created account.
					// These do not match any Phantom catalog; never access or mutate those schemas/grants here.
					if (scope.equals("`test`.*") || scope.equals("`test_%`.*")) { System.out.println("SQL130_EXISTING_INSTALLATION_TEST_GRANT=" + scope); continue; }
					if (!scope.equals("`" + SCHEMA + "`.*")) { throw new IllegalStateException("TEST130 has a foreign/global grant scope: " + scope); }
				}
			}
			try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE DB='" + SCHEMA + "' AND ID<>CONNECTION_ID()"))
			{ if (!rows.next() || rows.getInt(1) != 0) { throw new IllegalStateException("TEST130 has foreign sessions."); } }
		}
		if (args[0].equals("inspect")) { System.out.println("SQL130_INSPECT_ONLY;SQL_WRITES=0"); return; }
		final long start = System.nanoTime();
		DatabaseFactory.initFromConfig(config.toString());
		try
		{
			final var profiles = PhantomProfileRepository.open();
			if (args[0].equals("resume"))
			{
				final var checkpoint = read(local.resolve("restart.properties"));
				final var expected = PhantomLogicalParty.decode(java.util.Base64.getDecoder().decode(checkpoint.getProperty("aggregate")));
				final var fresh = new PhantomLogicalPartyCoordinator(new PhantomPartyStore(profiles));
				final var restored = fresh.groupFor(expected.leaderProfileId()).orElseThrow();
				PhantomAssertions.assertEquals(expected, restored.group(), "Fresh process changed durable roster/objective/cursor/phase.");
				PhantomAssertions.assertEquals(Long.parseLong(checkpoint.getProperty("rowVersion")), restored.rowVersion(), "Fresh process changed aggregate revision.");
				for (var member : expected.members()) { PhantomAssertions.assertEquals(restored, fresh.groupFor(member.profileId()).orElseThrow(), "Fresh process recovered a foreign member claim."); }
				System.out.println("SQL130_PROCESS_RESTART_PASS;MEMBERS=" + expected.members().size() + ";CURSOR=" + expected.backgroundCursor() + ";ZERO_NATIVE=true;SQL_WRITES=0");
				return;
			}
			// This unclaimed low-ID owner ensures the later duplicate claim fails AFTER earlier writes in the transaction.
			final long d = profiles.create(null).profileId();
			final long a = profiles.create(null).profileId(), b = profiles.create(null).profileId(), c = profiles.create(null).profileId();
			final var group = new PhantomLogicalParty(org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.sha256("SQL130:" + a), 1, a,
				List.of(new PhantomLogicalParty.Member(a, "damage", 1000 + a, 0), new PhantomLogicalParty.Member(b, "support", 1000 + b, 0), new PhantomLogicalParty.Member(c, "damage", 1000 + c, 0)),
				new org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef("background.farm", "20101@farm.fixture"), "farm.fixture", PhantomLogicalParty.Phase.AGREED, 0, 1000, "");
			final var store = new PhantomPartyStore(profiles); final var coordinator = new PhantomLogicalPartyCoordinator(store);
			final var saved = coordinator.agree(group).orElseThrow();
			final var background = coordinator.transition(saved, PhantomLogicalParty.Phase.BACKGROUND, 123, 2000, "WAIT_CALENDAR");
			final var restored = new PhantomLogicalPartyCoordinator(new PhantomPartyStore(PhantomProfileRepository.open())).groupFor(c).orElseThrow();
			PhantomAssertions.assertEquals(background, restored, "Real repository recreation changed the logical aggregate/cursor.");
			PhantomAssertions.assertTrue(profiles.find(a).orElseThrow().characterObjectId() == null, "SQL proof unexpectedly required a character/native identity.");
			final var conflict = new PhantomLogicalParty("B".repeat(64), 1, d, List.of(new PhantomLogicalParty.Member(d, "damage", 1000 + d, 0), new PhantomLogicalParty.Member(b, "support", 1000 + b, 0)), group.objective(), group.anchorId(), PhantomLogicalParty.Phase.AGREED, 0, 3000, "");
			PhantomAssertions.assertTrue(new PhantomLogicalPartyCoordinator(store).agree(conflict).isEmpty(), "SQL second agreement stole a claim.");
			try { store.commitLogicalGroup(conflict, -1, Map.of(d, -1L, b, -1L)); throw new AssertionError("Duplicate SQL claim unexpectedly committed."); }
			catch (org.l2jmobius.gameserver.phantoms.profile.PhantomProfilePersistenceException | java.util.ConcurrentModificationException expected) { }
			try
			{
				profiles.mutateProfileComponentsAtomically(List.of(
					new PhantomProfileRepository.ProfileComponentMutation(d, new PhantomProfileRepository.ComponentMutation("coordination130.atomic_probe", -1, 1, new byte[] {1})),
					new PhantomProfileRepository.ProfileComponentMutation(b, new PhantomProfileRepository.ComponentMutation(PhantomLogicalParty.CLAIM_COMPONENT_TYPE, -1, PhantomLogicalParty.SCHEMA_VERSION, profiles.findComponent(b, PhantomLogicalParty.CLAIM_COMPONENT_TYPE).orElseThrow().payload()))));
				throw new AssertionError("SQL prefix transaction unexpectedly committed.");
			}
			catch (org.l2jmobius.gameserver.phantoms.profile.PhantomProfilePersistenceException expected) { }
			PhantomAssertions.assertTrue(profiles.findComponent(d, "coordination130.atomic_probe").isEmpty(), "SQL rollback leaked the earlier prefix write.");
			PhantomAssertions.assertTrue(store.loadLogicalClaim(d).isEmpty() && store.loadLogicalGroup(d).isEmpty(), "Failed SQL formation leaked partial reservations/aggregate.");
			PhantomAssertions.assertEquals(group.groupId(), coordinator.groupFor(b).orElseThrow().group().groupId(), "Failed SQL transaction damaged the current group.");
			final var guardBefore = profiles.findComponent(a, PhantomLogicalParty.COMPONENT_TYPE).orElseThrow();
			final var left = coordinator.leave(background, c, 3000);
			PhantomAssertions.assertThrows(java.util.ConcurrentModificationException.class, () -> profiles.mutateProfileComponentsAtomically(List.of(
				new PhantomProfileRepository.ProfileComponentMutation(d, new PhantomProfileRepository.ComponentMutation("coordination130.guard_probe", -1, 1, new byte[] {1}))), List.of(guardBefore)), "A changed receipt dependency did not abort the group transaction.");
			PhantomAssertions.assertTrue(profiles.findComponent(d, "coordination130.guard_probe").isEmpty(), "A stale guarded transaction wrote state.");
			final var changed = coordinator.replace(left, List.of(group.members().getFirst(), new PhantomLogicalParty.Member(d, "support", 1000 + d, 0)), 4000);
			PhantomAssertions.assertTrue(coordinator.groupFor(b).isEmpty() && coordinator.groupFor(c).isEmpty(), "SQL leave/replace stranded old claims.");
			final var expired = coordinator.expire(changed, changed.group().expiresAtMillis()).orElseThrow();
			final var recreatedGroup = new PhantomLogicalParty(group.groupId(), expired.group().generation() + 1, a, group.members(), group.objective(), group.anchorId(), PhantomLogicalParty.Phase.AGREED, 123, System.currentTimeMillis(), "");
			final var recreated = new PhantomLogicalPartyCoordinator(store).agree(recreatedGroup).orElseThrow();
			PhantomAssertions.assertThrows(java.util.ConcurrentModificationException.class, () -> coordinator.disband(background, 5000, "STALE"), "SQL stale release damaged a new generation.");
			concurrentLifecycle(profiles);
			Files.writeString(local.resolve("restart.properties"), "rowVersion=" + recreated.rowVersion() + "\naggregate=" + java.util.Base64.getEncoder().encodeToString(recreated.group().encode()) + "\n");
			System.out.println("SQL130_PASS;ZERO_NATIVE=true;MEMBERS=3;CURSOR=123;LIFECYCLE=true;CONCURRENT_RACES=2;ATOMIC_ROLLBACK=true;MILLIS=" + (System.nanoTime() - start) / 1_000_000);
		}
		finally { DatabaseFactory.close(); }
	}
	private static PhantomLogicalParty agreement(long leader, long peer, long generation)
	{
		return new PhantomLogicalParty(org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.sha256("SQL130-RACE:" + leader + ":" + generation), generation, leader,
			List.of(new PhantomLogicalParty.Member(leader, "damage", leader + 1000, 0), new PhantomLogicalParty.Member(peer, "support", peer + 1000, 0)),
			new org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef("background.farm", "20101@farm.fixture"), "farm.fixture", PhantomLogicalParty.Phase.AGREED, 0, System.currentTimeMillis(), "");
	}
	private static void concurrentLifecycle(PhantomProfileRepository profiles) throws Exception
	{
		final long x = profiles.create(null).profileId(), y = profiles.create(null).profileId(), z = profiles.create(null).profileId(), w = profiles.create(null).profileId();
		final var first = new PhantomLogicalPartyCoordinator(new PhantomPartyStore(PhantomProfileRepository.open()));
		final var second = new PhantomLogicalPartyCoordinator(new PhantomPartyStore(PhantomProfileRepository.open()));
		race(() -> first.agree(agreement(x, z, 1)).orElseThrow(java.util.ConcurrentModificationException::new), () -> second.agree(agreement(y, z, 1)).orElseThrow(java.util.ConcurrentModificationException::new));
		final var current = first.groupFor(z).orElseThrow(); final long leader = current.group().leaderProfileId(), loser = leader == x ? y : x;
		PhantomAssertions.assertTrue(first.groupFor(loser).isEmpty() && new PhantomPartyStore(profiles).loadLogicalGroup(loser).isEmpty(), "Concurrent formation leaked the losing aggregate/claim.");
		race(() -> first.disband(current, System.currentTimeMillis(), "DISBANDED"), () -> second.replace(current,
			List.of(current.group().members().getFirst(), new PhantomLogicalParty.Member(w, "support", w + 1000, 0)), System.currentTimeMillis()));
		final var winner = new PhantomPartyStore(profiles).loadLogicalGroup(leader).orElseThrow();
		PhantomAssertions.assertTrue(first.groupFor(z).isEmpty(), "Concurrent release/replace retained a departed identity.");
		if (winner.group().active()) { first.disband(winner, System.currentTimeMillis(), "DISBANDED"); }
		final var terminal = new PhantomPartyStore(profiles).loadLogicalGroup(leader).orElseThrow();
		final var recreated = first.agree(agreement(leader, z, terminal.group().generation() + 1)).orElseThrow();
		PhantomAssertions.assertThrows(java.util.ConcurrentModificationException.class, () -> second.disband(current, System.currentTimeMillis(), "STALE"), "Concurrent old generation released new claims.");
		PhantomAssertions.assertEquals(recreated, second.groupFor(z).orElseThrow(), "Concurrent recovery lost the recreated group.");
		System.out.println("SQL130_CONCURRENT_LIFECYCLE_PASS;FORMATION_WINNERS=1;REVISION_WINNERS=1;STALE_RELEASE_REJECTED=true");
	}
	private static void race(Runnable left, Runnable right) throws Exception
	{
		final var ready = new java.util.concurrent.CountDownLatch(2); final var start = new java.util.concurrent.CountDownLatch(1);
		final var winners = new java.util.concurrent.atomic.AtomicInteger(); final var conflicts = new java.util.concurrent.atomic.AtomicInteger();
		final var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
		final var threads = new java.util.ArrayList<Thread>();
		for (var work : List.of(left, right))
		{
			threads.add(new Thread(() ->
			{
				ready.countDown();
				try { if (!start.await(3, java.util.concurrent.TimeUnit.SECONDS)) { throw new AssertionError("SQL race start timeout."); } work.run(); winners.incrementAndGet(); }
				catch (java.util.ConcurrentModificationException | org.l2jmobius.gameserver.phantoms.profile.PhantomProfilePersistenceException expected) { conflicts.incrementAndGet(); }
				catch (Throwable unexpected) { failure.compareAndSet(null, unexpected); }
			}, "coordination130-sql-race"));
		}
		threads.forEach(Thread::start); final boolean armed = ready.await(3, java.util.concurrent.TimeUnit.SECONDS); start.countDown();
		for (var thread : threads) { thread.join(6000); }
		PhantomAssertions.assertTrue(armed && threads.stream().noneMatch(Thread::isAlive), "SQL contenders did not terminate within their bounded window.");
		PhantomAssertions.assertEquals(null, failure.get(), "Unexpected SQL contender failure.");
		PhantomAssertions.assertTrue(winners.get() == 1 && conflicts.get() == 1, "SQL race did not produce exactly one winner and one conflict.");
	}
}
