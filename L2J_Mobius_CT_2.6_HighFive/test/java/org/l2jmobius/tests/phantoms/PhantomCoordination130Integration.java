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
import java.util.Optional;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundAuthority;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundStateCodec;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundSimulationPolicy;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundOperationKey;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupStore;
import org.l2jmobius.gameserver.phantoms.background.PhantomNativeContext;
import org.l2jmobius.gameserver.phantoms.background.PhantomOffscreenOutdoorTravel;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionAdmission;
import org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionHandoff;
import org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionHandoffStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomJourneyLifetime;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;

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
        if (args.length < 2 || args.length > 3 || !List.of("check", "prepare", "inspect", "probe", "probe-live", "handoff", "ownership", "ownership-resume", "test", "resume", "seal", "fixture", "recover", "native", "restart").contains(args[0])) { throw new IllegalArgumentException("check|prepare|inspect|probe|probe-live|handoff|ownership|ownership-resume|test|resume|seal|fixture|recover|native|restart module [read-only admin config]"); }
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
            // The explicit read-only live probe may coexist only with the unique attested TEST130 principal.
            final String foreign = args[0].equals("probe-live") ? " AND USER<>'" + settings.getProperty("Login") + "'" : "";
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE DB='" + SCHEMA + "' AND ID<>CONNECTION_ID()" + foreign))
			{ if (!rows.next() || rows.getInt(1) != 0) { throw new IllegalStateException("TEST130 has foreign sessions."); } }
		}
		if (args[0].equals("inspect")) { System.out.println("SQL130_INSPECT_ONLY;SQL_WRITES=0"); return; }
        if (args[0].equals("fixture")) { PhantomCoordination130Native.prepareOnly(module); return; }
		if (args[0].equals("native") || args[0].equals("restart")) { PhantomCoordination130Native.run(module, args[0].equals("restart")); return; }
		final long start = System.nanoTime();
		DatabaseFactory.initFromConfig(config.toString());
		try
		{
			final var profiles = PhantomProfileRepository.open();
			if (args[0].equals("ownership")) { executionHandoff(profiles, module); PhantomCommerceSuite.verifyUnappliedAbortSql(profiles); return; }
			if (args[0].equals("ownership-resume"))
			{
				final var checkpoint = read(local.resolve("ownership-restart.properties"));
				final long id = Long.parseLong(checkpoint.getProperty("profileId"));
				final var store = new org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionHandoffStore(profiles);
				final var row = store.load(id).orElseThrow();
				PhantomAssertions.assertEquals(Long.parseLong(checkpoint.getProperty("rowVersion")), row.component().rowVersion(), "Process restart changed execution CAS version.");
				PhantomAssertions.assertTrue(java.util.Arrays.equals(row.component().payload(), java.util.Base64.getDecoder().decode(checkpoint.getProperty("payload"))), "Process restart lost source/recipient/checkpoint proof.");
				final var gate = new org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionAdmission(1); gate.publish(id, row.component().rowVersion(), row.state());
				PhantomAssertions.assertEquals(null, gate.tryAcquire(id, row.state().owner()), "Restart admitted a yielded source.");
				PhantomAssertions.assertEquals(null, gate.tryAcquire(id, row.state().recipient()), "Restart prematurely admitted the untransferred recipient.");
				PhantomAssertions.assertThrows(java.util.ConcurrentModificationException.class, () -> store.transfer(row, List.of(), 1500), "Restart ignored changed pending obligations.");
				PhantomAssertions.assertEquals(row, store.load(id).orElseThrow(), "Read-only pending recovery wrote ownership.");
				System.out.println("SQL130_EXECUTION_PROCESS_RESTART_PASS;PENDING_CHECKPOINT_FENCED=true;SOURCE_AND_RECIPIENT_DENIED=true;CHANGED_OBLIGATIONS_REJECTED=true;SQL_WRITES=0;NATIVE=false"); return;
			}
            if (args[0].equals("recover")) { PhantomCoordination130Native.recoverAgreement(module, profiles); return; }
            if (args[0].equals("handoff")) { initialRootBinding(profiles, module); return; }
            if (args[0].equals("probe") || args[0].equals("probe-live"))
            {
                try (var connection = DatabaseFactory.getConnection(); var statement = connection.createStatement();
                    var rows = statement.executeQuery("SELECT component_type,COUNT(*) FROM phantom_profile_components WHERE component_type IN ('party.logical_group','party.logical_claim') GROUP BY component_type"))
                { while (rows.next()) { System.out.println("SQL130_LOGICAL_ROW_COUNT type=" + rows.getString(1) + " rows=" + rows.getLong(2)); } }
                final var saved = read(PhantomCoordination130Native.scene(module.getParent().getParent().resolve("runtime")).resolve("fixture130.properties"));
                for (String text : saved.getProperty("profiles").split(","))
                {
                    final long id = Long.parseLong(text);
                    final var group = new PhantomLogicalPartyCoordinator(new PhantomPartyStore(profiles)).groupFor(id);
                    final var raw = profiles.findComponent(id, PhantomBackgroundState.COMPONENT_TYPE);
                    final var state = raw.map(value -> new PhantomBackgroundStateCodec().decode(value.payload()));
                    System.out.println("SQL130_PHYSICAL_BASELINE profile=" + id + " position=" + state.orElseThrow().position() + " vitals=" + state.orElseThrow().vitals());
                    System.out.println("SQL130_ROOT profile=" + id + " root=" + new PhantomGoalStateStore(profiles).load(id));
                    System.out.println("SQL130_PARTY_CLAIM profile=" + id + " claim=" + new PhantomPartyStore(profiles).load(id));
                    System.out.println("SQL130_SUPPLY_RECEIPT profile=" + id + " receipt=" + new org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceiptStore(profiles).find(id));
                    try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT COALESCE(SUM(count),0) FROM items WHERE owner_id=? AND item_id=57 AND loc='INVENTORY'"))
                    {
                        connection.setReadOnly(true); query.setInt(1, profiles.find(id).orElseThrow().characterObjectId());
                        try (var rows = query.executeQuery()) { if (!rows.next()) { throw new IllegalStateException("Own profile wallet unavailable."); } System.out.println("SQL130_WALLET profile=" + id + " adena=" + rows.getLong(1)); }
                    }
                    final var population = new org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStateCodec().decode(profiles.findComponent(id, org.l2jmobius.gameserver.phantoms.population.PhantomPopulationState.COMPONENT_TYPE).orElseThrow().payload());
                    final var zone = java.time.ZoneId.of(read(module.getParent().getParent().resolve("runtime/config/Custom/PhantomPlayers.ini")).getProperty("PhantomPopulationTimeZone", "UTC"));
                    final var catalog = org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog.load(module.resolve("dist/game/data/phantoms/population/high-five-population-v3.xml"), zone);
                    System.out.println("SQL130_CALENDAR profile=" + id + " template=" + population.scheduleTemplate() + " phase=" + population.schedulePhaseMinutes() + " now=" + java.time.Instant.now()
                        + " evaluation=" + catalog.evaluate(population.scheduleTemplate(), java.time.Instant.now(), zone, population.schedulePhaseMinutes()));
                    System.out.println("SQL130_SEEKING profile=" + id + " level=" + state.orElseThrow().progress().level() + " percentile=" + org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig.PartySeekingSettings.defaults().percentile(id)
                        + " seeks=" + org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig.PartySeekingSettings.defaults().seeks(id, state.orElseThrow().progress().level()));
                    System.out.println("SQL130_NATIVE_PROBE profile=" + id + " group=" + group.map(value -> value.group().phase() + ":" + value.group().waitReason() + ":expires=" + value.group().expiresAtMillis())
                        + " background=" + state.map(value -> value.state() + ":XP=" + value.progress().experience() + ":SP=" + value.progress().skillPoints() + ":hashes=" + value.hashes())
                        + " catchup=" + new PhantomBackgroundCatchupStore(profiles, new PhantomGoalStateStore(profiles)).load(id).map(value -> value.state().toString()));
                }
                return;
            }
			if (args[0].equals("seal"))
			{
				try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT COUNT(*) FROM phantom_profile_components WHERE component_type=?"))
				{
					query.setString(1, org.l2jmobius.gameserver.phantoms.population.PhantomPopulationState.COMPONENT_TYPE);
					try (var rows = query.executeQuery()) { if (!rows.next() || rows.getLong(1) != 0) { throw new IllegalStateException("SQL fixtures can be sealed only before managed native fixture creation."); } }
				}
				final var logical = new PhantomLogicalPartyCoordinator(new PhantomPartyStore(profiles)); long after = 0; int sealed = 0;
				while (true)
				{
					final var page = logical.recoverAfter(after, 16); if (page.isEmpty()) { break; }
					for (var row : page) { after = row.group().leaderProfileId(); if (row.group().active()) { logical.disband(row, System.currentTimeMillis(), "SQL_FIXTURE_COMPLETE"); sealed++; } }
				}
				System.out.println("SQL130_FIXTURES_SEALED=" + sealed + ";ATOMIC_RELEASE=true;GAMEPLAY_WRITES=0"); return;
			}
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
			final var background = coordinator.transition(saved, PhantomLogicalParty.Phase.BACKGROUND, 0, 2000, "WAIT_CALENDAR");
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
			final var recreatedGroup = new PhantomLogicalParty(group.groupId(), expired.group().generation() + 1, a, group.members(), group.objective(), group.anchorId(), PhantomLogicalParty.Phase.AGREED, 0, System.currentTimeMillis(), "");
			final var recreated = new PhantomLogicalPartyCoordinator(store).agree(recreatedGroup).orElseThrow();
			PhantomAssertions.assertThrows(java.util.ConcurrentModificationException.class, () -> coordinator.disband(background, 5000, "STALE"), "SQL stale release damaged a new generation.");
			concurrentLifecycle(profiles);
			backgroundReceipts(profiles, module);
			Files.writeString(local.resolve("restart.properties"), "rowVersion=" + recreated.rowVersion() + "\naggregate=" + java.util.Base64.getEncoder().encodeToString(recreated.group().encode()) + "\n");
			System.out.println("SQL130_PASS;ZERO_NATIVE=true;MEMBERS=3;CURSOR=0;LIFECYCLE=true;CONCURRENT_RACES=2;ATOMIC_ROLLBACK=true;MILLIS=" + (System.nanoTime() - start) / 1_000_000);
		}
		finally { DatabaseFactory.close(); }
	}
	private static PhantomLogicalParty agreement(long leader, long peer, long generation)
	{
		return new PhantomLogicalParty(org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.sha256("SQL130-RACE:" + leader + ":" + generation), generation, leader,
			List.of(new PhantomLogicalParty.Member(leader, "damage", leader + 1000, 0), new PhantomLogicalParty.Member(peer, "support", peer + 1000, 0)),
			new org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef("background.farm", "20101@farm.fixture"), "farm.fixture", PhantomLogicalParty.Phase.AGREED, 0, System.currentTimeMillis(), "");
	}
    /** Metadata-only CAS reproduction of the observed initial-root deadlock; no native profile mutation. */
    private static void initialRootBinding(PhantomProfileRepository profiles, Path module) throws Exception
    {
        final var fixture = read(module.getParent().getParent().resolve("runtime/fixture130.properties"));
        final long reference = Long.parseLong(fixture.getProperty("profiles").split(",")[0]);
        final var prior = new PhantomBackgroundStateCodec().decode(profiles.findComponent(reference, PhantomBackgroundState.COMPONENT_TYPE).orElseThrow().payload());
        final var profile = profiles.create(null); final long id = profile.profileId(); final int object = Math.toIntExact(700_000_000L + id);
        profiles.updateCharacterLink(id, profile.rowVersion(), object);
        final var state = new PhantomBackgroundState(PhantomBackgroundState.State.READY,
            new PhantomBackgroundState.Identity(id, object, prior.identity().classIndex(), prior.identity().activeClassId(), prior.identity().raceOrdinal()),
            prior.progress(), prior.vitals(), prior.position(), prior.combat(), prior.loadout(), prior.inventory(), prior.autoGetSkills(), prior.clock(), PhantomBackgroundState.Receipt.empty(), prior.hashes());
        final var original = new PhantomGoalStateStore(profiles).load(reference).orElseThrow().goal();
        final var root = new org.l2jmobius.gameserver.phantoms.decision.PhantomGoal(13_000_000 + id, original.goalType(), original.status(),
            new org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef("profile", Long.toString(id)), original.target(), original.requiredAmount(), original.currentAmount(),
            original.acquisitionMethod(), original.validSources(), original.selectedAnchor(), original.purposeKey(), original.priority(), original.riskBudget(), original.expenseBudget(), original.deadlineEpochMillis(), original.constraints(), original.reasonKey(), 0);
        final var goals = new PhantomGoalStateStore(profiles); final var storedGoal = goals.insert(id, root);
        final var rawState = profiles.insertComponent(id, PhantomBackgroundState.COMPONENT_TYPE, PhantomBackgroundState.SCHEMA_VERSION, new PhantomBackgroundStateCodec().encode(state));
        final var history = new PhantomBackgroundCatchupState(PhantomBackgroundCatchupState.Status.PENDING,
            org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.sha256("handoff130:" + id).toLowerCase(java.util.Locale.ROOT),
            130, 100, 101, 100, 0, 0, 1, 1, 1, 0, 0, "", PhantomBackgroundState.MODEL_VERSION,
            new PhantomBackgroundState.Hashes("old-knowledge130", state.hashes().topology(), state.hashes().progression(), state.hashes().commerce()), "");
        final var store = new PhantomBackgroundCatchupStore(profiles, goals); final var pending = store.claim(id, history);
        final var planned = history.withPlan(root.goalId(), root.revision(), 0, "a".repeat(64), 1, 1, state.hashes());
        final var fabricated = new org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStore.StoredGoal(storedGoal.goal(), storedGoal.rowVersion() + 1);
        PhantomAssertions.assertThrows(java.util.ConcurrentModificationException.class, () -> store.bindExistingInitialPlan(id, pending, planned, fabricated, state), "A stale root linked a new catch-up.");
        final var changedWindow = new PhantomBackgroundCatchupState(planned.status(), planned.requestId(), planned.deterministicSeed(), planned.fromEpochMinute(), planned.targetEpochMinute() + 1,
            planned.cursorEpochMinute(), planned.planOrdinal(), planned.intervalOrdinal(), planned.generation(), planned.knowledgeGeneration(), planned.topologyGeneration(), planned.goalId(), planned.goalRevision(),
            planned.planIdentity(), planned.modelVersion(), planned.authorityHashes(), planned.failureReason());
        PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> store.bindExistingInitialPlan(id, pending, changedWindow, storedGoal, state), "Initial binding changed its owned time window.");
        race(() -> store.bindExistingInitialPlan(id, pending, planned, storedGoal, state), () -> store.bindExistingInitialPlan(id, pending, planned, storedGoal, state));
        final var bound = store.load(id).orElseThrow();
        PhantomAssertions.assertEquals(planned, bound.state(), "Initial binding changed time/interval/plan.");
        PhantomAssertions.assertEquals(pending.rowVersion() + 1, bound.rowVersion(), "Concurrent binding applied twice.");
        PhantomAssertions.assertEquals(storedGoal, goals.load(id).orElseThrow(), "Binding rewrote the existing ordinary Goal.");
        PhantomAssertions.assertEquals(rawState, profiles.findComponent(id, PhantomBackgroundState.COMPONENT_TYPE).orElseThrow(), "Binding changed the Background/economic baseline.");
        PhantomAssertions.assertThrows(RuntimeException.class, () -> store.bindExistingInitialPlan(id, pending, planned, storedGoal, state), "Stale pending claim rebound a newer owner.");
        System.out.println("SQL130_INITIAL_ROOT_BIND_PASS;ONE_CAS_WINNER=true;STALE_ROOT_AND_CLAIM=REJECTED;CURSOR_EXP_SP_GOAL_BASELINE=UNCHANGED;NATIVE_ADMISSION=NOT_GRANTED");
    }
	/** Real JDBC settlement through the existing Background transaction; these are SQL fixtures, never native kill evidence. */
	private static void backgroundReceipts(PhantomProfileRepository profiles, Path module) throws Exception
	{
		org.l2jmobius.gameserver.config.ServerConfig.DATAPACK_ROOT = module.resolve("dist/game").toFile();
		org.l2jmobius.gameserver.config.PlayerConfig.load();
		final var tree = org.l2jmobius.gameserver.data.xml.SkillTreeData.getInstance().getCompleteClassSkillTree(org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass.getPlayerClass(0));
		final var levels = new java.util.TreeMap<Integer, Integer>();
		for (var skill : tree.values())
		{ if (skill.isAutoGet() && skill.getGetLevel() <= 1 && (skill.getRaces().isEmpty() || skill.getRaces().stream().anyMatch(race -> race.ordinal() == 0))) { levels.merge(skill.getSkillId(), skill.getSkillLevel(), Math::max); } }
		final var skills = levels.entrySet().stream().map(entry -> new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.AutoGetSkill(entry.getKey(), entry.getValue())).toList();
		final var source = new org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef("background.farm", "20101@farm.receipt130");
		final var fixtures = new java.util.ArrayList<ReceiptFixture>();
		for (int i = 0; i < 3; i++)
		{
			final var profile = profiles.create(null); final int objectId = Math.toIntExact(700_000_000L + profile.profileId());
			try (var connection = DatabaseFactory.getConnection(); var statement = connection.prepareStatement("INSERT INTO characters (charId,char_name,account_name,level,exp,sp,expBeforeDeath,curHp,maxHp,curMp,maxMp,curCp,maxCp,x,y,z,heading,classid,base_class,race,vitality_points,online) VALUES (?,?,?,1,0,0,0,100,100,100,100,100,100,0,0,0,0,0,0,0,1,0)"))
			{ statement.setInt(1, objectId); statement.setString(2, "SQL130_" + profile.profileId()); statement.setString(3, "task130_sql"); statement.executeUpdate(); }
			profiles.updateCharacterLink(profile.profileId(), profile.rowVersion(), objectId);
			try (var connection = DatabaseFactory.getConnection(); var statement = connection.prepareStatement("INSERT INTO character_skills (charId,skill_id,skill_level,class_index) VALUES (?,?,?,0)"))
			{ for (var skill : skills) { statement.setInt(1, objectId); statement.setInt(2, skill.skillId()); statement.setInt(3, skill.skillLevel()); statement.addBatch(); } statement.executeBatch(); }
			final var goal = new org.l2jmobius.gameserver.phantoms.decision.PhantomGoal(130000 + profile.profileId(), "farm.background", org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus.ACTIVE,
				new org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef("profile", Long.toString(profile.profileId())), new org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef("npc", "20101"), 1, 0, "background.farm", List.of(source),
				new org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef(org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec.ANCHOR_NAMESPACE, "farm.receipt130"), "farm.background", 500, 0, 0, 0, Map.of(), "background.plan", 0);
			new org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore(profiles).insert(profile.profileId(), goal);
			final var state = new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState(org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.State.MATERIALIZED,
				new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Identity(profile.profileId(), objectId, 0, 0, 0),
				new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Progress(1, 0, 0, 0), new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Vitals(100,100,100,100,100,100),
				new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Position(0,0,0,0,0,"farm.receipt130"),
				new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.CombatFacts(org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.ModelKind.MELEE,100,100,100,100,500,500,1,1,1,1,0,1,1,1,1),
				org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Loadout.none(), new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.InventoryFacts(List.of(),List.of(),"",0,1000,0,100), skills,
				new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Clock(130,0,0), org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Receipt.empty(),
				new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Hashes("knowledge130","topology130","progression130","commerce130"));
			final var transaction = new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction();
			final var capture = transaction.captureBaseline(state, goal, new org.l2jmobius.gameserver.phantoms.background.PhantomNativeContext.Capture(1, org.l2jmobius.gameserver.phantoms.background.PhantomNativeContext.Eligibility.SUPPORTED));
			PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction.Status.SUCCESS, capture.status(), "SQL receipt fixture canonical capture failed.");
			final var history = new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupState(org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupState.Status.RUNNING,
				org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.sha256("receipt130:" + profile.profileId()).toLowerCase(java.util.Locale.ROOT),130,100,101,100,0,0,1,1,1,goal.goalId(),0,"f".repeat(64),org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.MODEL_VERSION,state.hashes(),"");
			final var stored = new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupStore(profiles, new org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore(profiles)).claim(profile.profileId(), history);
			final var key = new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundOperationKey(profile.profileId(), objectId, goal.goalId(), 0,0,0,
				org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundOperationKey.ActionKind.HISTORICAL_FARM,20101,"farm.receipt130",org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.MODEL_VERSION,state.hashes(),null,
				new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundOperationKey.HistoricalIdentity(history.requestId(),1,0,100,101,history.planIdentity()));
			final var command = new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction.Command(capture.state(), goal, key,
				new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Progress(1,1,1,0),state.vitals(),state.position(),state.clock(),Map.of(),skills,List.of(),null,
				new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction.CatchupMutation(history,stored.rowVersion(),history.advanceTo(101)));
			fixtures.add(new ReceiptFixture(profile.profileId(),objectId,goal,transaction,command));
		}
		final var group = new PhantomLogicalParty(org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.sha256("receipt-group130:" + fixtures.getFirst().profileId()),1,fixtures.getFirst().profileId(),
			fixtures.stream().map(f -> new PhantomLogicalParty.Member(f.profileId(),"damage",f.goal().goalId(),0)).toList(),source,"farm.receipt130",PhantomLogicalParty.Phase.AGREED,0,System.currentTimeMillis(),"");
		final var coordinator = new PhantomLogicalPartyCoordinator(new PhantomPartyStore(profiles));
		var row = coordinator.agree(group).orElseThrow(); row = coordinator.transition(row,PhantomLogicalParty.Phase.BACKGROUND,0,System.currentTimeMillis(),"WAIT_RECEIPTS");
		final var first = fixtures.getFirst();
		race(() -> first.commitOnce(), () -> first.commitOnce());
		first.verify(); fixtures.get(1).commitOnce(); fixtures.get(1).verify();
		PhantomAssertions.assertEquals(row, coordinator.refreshBackground(row, System.currentTimeMillis()), "A partial member settlement advanced the group watermark.");
		fixtures.get(2).commitOnce();
		for (var fixture : fixtures) { fixture.verify(); }
		final var settled = coordinator.refreshBackground(row,System.currentTimeMillis());
		PhantomAssertions.assertEquals(101L,settled.group().backgroundCursor(),"Group watermark did not reflect real committed member receipts.");
		PhantomAssertions.assertEquals(settled,coordinator.refreshBackground(settled,System.currentTimeMillis()),"Receipt replay changed the group version/deadline.");
		final var revised = coordinator.leave(settled,fixtures.getLast().profileId(),System.currentTimeMillis());
		PhantomAssertions.assertEquals(0L,revised.group().backgroundCursor(),"A new roster inherited an unverified old-roster watermark.");
		PhantomAssertions.assertEquals(101L,coordinator.refreshBackground(revised,System.currentTimeMillis()).group().backgroundCursor(),"A new generation did not derive its own retained-member receipts.");
		PhantomAssertions.assertThrows(java.util.ConcurrentModificationException.class,()->coordinator.refreshBackground(settled,System.currentTimeMillis()),"A stale generation refreshed group time.");
		for (var fixture : fixtures)
		{ PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction.Status.IDEMPOTENT,fixture.transaction().execute(fixture.command()).status(),"Duplicate interval was not idempotent."); fixture.assertTotals(); }
		System.out.println("SQL130_RECEIPT_WATERMARK_PASS;MEMBERS=3;CURSOR=101;CONCURRENT_INTERVAL_EXACTLY_ONCE=true;REPLAY_EXP_SP=UNCHANGED;NATIVE_KILLS=0");
		final var normalAdmission = backgroundLogicalRestore(profiles, first);
		f7Receipts(profiles, fixtures);
		final var pending = fixtures.get(1);
		PhantomAssertions.assertThrows(org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecyclePort.AdmissionRejectedException.class,
			() -> normalAdmission.beforeMaterialize(pending.profileId(),pending.objectId()),"A new running historical claim inherited old COMPLETE/NORMAL admission.");
		System.out.println("SQL130_NORMAL_PREREQUISITE_LEDGER_GATE_PASS;OLD_COMPLETE=ADMISSIBLE;NEW_RUNNING=REJECTED;NATIVE_LOADS=0");
	}
	private static org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundService backgroundLogicalRestore(PhantomProfileRepository profiles, ReceiptFixture fixture)
	{
		final long id = fixture.profileId(); final var goals = new PhantomGoalStateStore(profiles);
		final var beforeGoal = goals.load(id); final var beforeHistory = new PhantomBackgroundCatchupStore(profiles, goals).load(id);
		final var beforeState = profiles.findComponent(id, PhantomBackgroundState.COMPONENT_TYPE);
		final var state = new PhantomBackgroundStateCodec().decode(beforeState.orElseThrow().payload());
		final var cold = new PhantomLogicalPartyCoordinator(new PhantomPartyStore(profiles));
		PhantomAssertions.assertTrue(cold.knownGroupFor(id).isEmpty(), "Cold SQL fixture already had a recovered memory group.");
		final var metrics = new org.l2jmobius.gameserver.phantoms.PhantomMetrics();
		final var materialization = new org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService(profiles, PhantomIdentityLeaseRegistry.getInstance(), metrics,
			new org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace(false,64,16,metrics),1);
		final var nativeBackend = new org.l2jmobius.gameserver.phantoms.party.L2jPhantomPartyBackend(profiles,materialization,null);
		for (int size : List.of(2,6,9))
		{
			final var roster = java.util.stream.LongStream.rangeClosed(1,size).mapToObj(profile -> org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef.phantom(profile,700_000_000 + (int) profile)).toList();
			final var party = new org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.PartySnapshot(roster.getFirst(),roster,org.l2jmobius.gameserver.model.groups.PartyDistributionType.FINDERS_KEEPERS);
			final var witness = new java.util.ArrayList<String>();
			PhantomAssertions.assertTrue(nativeBackend.autonomousFarmTarget(party,java.util.Set.of(roster.getLast()),witness::add).isEmpty(),"Absent native actors received a target.");
			PhantomAssertions.assertEquals(List.of("actor.unavailable=1"),witness,"A legal six/nine-member roster never reached the production native actor gate.");
			witness.clear();
			PhantomAssertions.assertTrue(nativeBackend.autonomousFarmTarget(party,java.util.Set.of(),witness::add).isEmpty(),"Absent DD-only native actors received a target.");
			PhantomAssertions.assertEquals(List.of("actor.unavailable=1"),witness,"A flexible DD-only objective acquired a universal backend healer requirement.");
		}
		final var phantom = org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef.phantom(id,fixture.objectId());
		final var human = org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef.real(799_999_999);
		final var mixed = new org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.PartySnapshot(phantom,List.of(phantom,human),org.l2jmobius.gameserver.model.groups.PartyDistributionType.FINDERS_KEEPERS);
		final var mixedWitness = new java.util.ArrayList<String>();
		PhantomAssertions.assertTrue(nativeBackend.autonomousFarmTarget(mixed,java.util.Set.of(phantom),mixedWitness::add).isEmpty() && mixedWitness.isEmpty(),"Autonomous target selection touched a mixed human Party.");
		System.out.println("SQL130_NATIVE_ROSTER_ADMISSION_PASS;LEGAL_2_6_9=ACTOR_GATE;MIXED=REJECTED;NATIVE_PLAYERS=0;ACTIONS=0");
		final var signals = new org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort()
		{
			@Override public SignalDelivery submit(long profile, org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal signal) { return SignalDelivery.ACCEPTED; }
			@Override public SignalDelivery withdraw(long profile, String source, long sequence) { return SignalDelivery.ACCEPTED; }
		};
		final var background = new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundService(profiles,goals,PhantomIdentityLeaseRegistry.getInstance(),new PhantomBackgroundTransaction(),
			new SqlTravelAuthority(state.hashes(),state.position()),new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCompetitionRegistry(),signals,() -> materialization);
		final var history = new org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundService(profiles,goals,
			org.l2jmobius.gameserver.phantoms.knowledge.PhantomFarm074Checks.planner(true),background,materialization);
		final var reads = new java.util.concurrent.atomic.AtomicInteger();
		history.bindLogicalGroups(profile -> cold.knownGroupFor(profile).map(row -> row.group()), profile -> { reads.incrementAndGet(); cold.groupFor(profile); });
		PhantomAssertions.assertTrue(history.advance(id,1,1).successful(), "Completed history did not retain its ordinary idempotent result.");
		PhantomAssertions.assertTrue(cold.knownGroupFor(id).isPresent(), "Background ran before durable logical claims were resolved into its cold cache.");
		PhantomAssertions.assertEquals(1,reads.get(),"A bounded Background call rescanned logical membership per interval.");
		final var coldNative = new PhantomLogicalPartyCoordinator(new PhantomPartyStore(profiles));
		history.bindLogicalGroups(profile -> coldNative.knownGroupFor(profile).map(row -> row.group()), profile -> { reads.incrementAndGet(); coldNative.groupFor(profile); });
		PhantomAssertions.assertTrue(coldNative.knownGroupFor(id).isEmpty(), "Native hydration fixture was already warm.");
		history.beforeMaterialize(id,fixture.objectId());
		PhantomAssertions.assertTrue(coldNative.knownGroupFor(id).isPresent(), "NORMAL materialization reached native planning before its durable logical group was hydrated.");
		PhantomAssertions.assertEquals(2,reads.get(),"Native materialization did not use one bounded durable membership read.");
		history.materializeAborted(id,fixture.objectId());
		history.bindLogicalGroups(profile -> cold.knownGroupFor(profile).map(row -> row.group()), _ -> { throw new IllegalStateException("TEST130_LOGICAL_READ_UNAVAILABLE"); });
		PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundService.ResultStatusCode.RETRY,history.advance(id,1,1).status(),"Unavailable logical authority fell through to Solo history.");
		PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundService.ResultStatusCode.RETRY,history.begin(id,101,102,130).status(),"A new historical window ignored unavailable logical authority.");
		PhantomAssertions.assertThrows(org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecyclePort.AdmissionRejectedException.class,
			() -> history.beforeMaterialize(id,fixture.objectId()),"Unavailable logical authority admitted a new native lifetime.");
		PhantomAssertions.assertEquals(beforeGoal,goals.load(id),"Logical hydration changed the root.");
		PhantomAssertions.assertEquals(beforeHistory,new PhantomBackgroundCatchupStore(profiles,goals).load(id),"Logical hydration changed interval/time ownership.");
		PhantomAssertions.assertEquals(beforeState,profiles.findComponent(id,PhantomBackgroundState.COMPONENT_TYPE),"Logical hydration changed rewards/economy.");
		System.out.println("SQL130_BACKGROUND_LOGICAL_RESTORE_PASS;BEFORE_SCHEDULER=true;DURABLE_POINT_READ=true;UNAVAILABLE=RETRY;GAMEPLAY_TIME_WRITES=0;PLAYERS=0");
		System.out.println("SQL130_NORMAL_LOGICAL_RESTORE_PASS;BEFORE_NATIVE_LOAD=true;COLD_POINT_READS=1;UNAVAILABLE=REJECTED;PLAYERS=0");
		history.bindLogicalGroups(profile -> coldNative.knownGroupFor(profile).map(row -> row.group()), profile -> coldNative.groupFor(profile));
		return history;
	}
	private static void f7Receipts(PhantomProfileRepository profiles, List<ReceiptFixture> fixtures) throws Exception
	{
		org.l2jmobius.gameserver.config.custom.AutoPlayConfig.load();
		org.l2jmobius.commons.threads.ThreadPool.init();
		try
		{
			for (int mode = 0; mode < 3; mode++)
			{
				final var fixture = fixtures.get(mode); final var oldGoal = fixture.goal();
				final var goal = new PhantomGoal(oldGoal.goalId(),oldGoal.goalType(),oldGoal.status(),oldGoal.subject(),oldGoal.target(),oldGoal.requiredAmount(),oldGoal.currentAmount(),oldGoal.acquisitionMethod(),oldGoal.validSources(),oldGoal.selectedAnchor(),oldGoal.purposeKey(),oldGoal.priority(),oldGoal.riskBudget(),oldGoal.expenseBudget(),oldGoal.deadlineEpochMillis(),oldGoal.constraints(),PhantomOffscreenOutdoorTravel.PENDING_REASON,oldGoal.revision()+1,oldGoal.payloadText());
				final var goals = new PhantomGoalStateStore(profiles); final var histories = new PhantomBackgroundCatchupStore(profiles,goals); final var prior = histories.load(fixture.profileId()).orElseThrow();
				final var next = new PhantomBackgroundCatchupState(PhantomBackgroundCatchupState.Status.PENDING,
					org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.sha256("offscreen130:"+fixture.profileId()).toLowerCase(java.util.Locale.ROOT),130,101,102,101,1,0,2,1,1,goal.goalId(),goal.revision(),"e".repeat(64),PhantomBackgroundState.MODEL_VERSION,prior.state().authorityHashes(),"");
				final var planned = histories.renewCompletedWithPlan(fixture.profileId(),prior,next,goals.load(fixture.profileId()).orElseThrow(),goal);
				final var running = histories.replace(fixture.profileId(),planned.catchup(),planned.catchup().state().running());
				final var stored = profiles.findComponent(fixture.profileId(),PhantomBackgroundState.COMPONENT_TYPE).orElseThrow();
				final var before = new PhantomBackgroundStateCodec().decode(stored.payload());
				final String hash = mode == 0 ? PhantomBackgroundTransaction.expectedAfterHash(before) : mode == 1 ? "a".repeat(64) : "";
				final var untagged = before.after(before.progress(),before.vitals(),before.position(),before.inventory(),before.autoGetSkills(),before.clock(),new PhantomBackgroundState.Receipt(before.receipt().operationKey(),0,0,hash));
				// Existing fixture capture writes the receipt and its policy binding together; never break that binding with a raw component edit.
				final var policy = new PhantomBackgroundSimulationPolicy(2,1,1,1,true,true,true,false,false,0,1,0,1,1,1,1,0,0,0,0,
					org.l2jmobius.gameserver.phantoms.background.L2jPhantomBackgroundAuthority.configuredSimulationFingerprint(),120,60000,0);
				final var captured = fixture.transaction().captureBaseline(untagged.withState(PhantomBackgroundState.State.MATERIALIZED),goal,new PhantomNativeContext.Capture(1,PhantomNativeContext.Eligibility.SUPPORTED,policy));
				PhantomAssertions.assertEquals(PhantomBackgroundTransaction.Status.SUCCESS,captured.status(),"F7 fixture recapture failed.");
				final var state = captured.state(); final var context = PhantomNativeContext.decode(profiles.findComponent(fixture.profileId(),PhantomNativeContext.COMPONENT_TYPE).orElseThrow().payload());
				final var destination = new PhantomBackgroundState.Position(0,state.position().x()+1,state.position().y(),state.position().z(),state.position().heading(),state.position().committedAnchorId());
				final var authority = new SqlTravelAuthority(state.hashes(),destination); final var calls = new java.util.concurrent.atomic.AtomicInteger();
				if (mode == 0)
				{
					org.l2jmobius.gameserver.phantoms.background.PhantomVisibleItinerary068Checks.logicalPreparationSignal(profiles, goal, state, authority);
					System.out.println("SQL130_LOGICAL_PREPARATION_SIGNAL_PASS;EXACT_BASELINE=true;STALE=REJECTED;CLEAR=WITHDRAWN;ECONOMY_TIME_WRITES=0");
				}
				final PhantomOffscreenOutdoorTravel.ObserverAdmission admission = (id,from,to) ->
				{
					calls.incrementAndGet(); return new org.l2jmobius.gameserver.phantoms.activity.PhantomOffscreenObserverAdmission.Verdict(org.l2jmobius.gameserver.phantoms.activity.PhantomOffscreenObserverAdmission.Code.ALLOW,id,
						new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(from.x(),from.y(),from.z(),0),new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(to.x(),to.y(),to.z(),0),0,System.nanoTime(),0);
				};
				try (var lease = PhantomIdentityLeaseRegistry.getInstance().tryAcquire(fixture.objectId(),PhantomIdentityLeaseRegistry.OwnerKind.BACKGROUND))
				{
					PhantomAssertions.assertTrue(lease != null,"F7 fixture could not acquire its own Background owner.");
					final var key = new PhantomBackgroundOperationKey(fixture.profileId(),fixture.objectId(),goal.goalId(),goal.revision(),0,PhantomOffscreenOutdoorTravel.RECEIPT_TICK,PhantomBackgroundOperationKey.ActionKind.HISTORICAL_OFFSCREEN_TRAVEL,20101,"farm.receipt130",PhantomBackgroundState.MODEL_VERSION,state.hashes(),null,
						new PhantomBackgroundOperationKey.HistoricalIdentity(next.requestId(),next.generation(),0,101,102,next.planIdentity()));
					final var command = new PhantomBackgroundTransaction.Command(state,goal,key,state.progress(),state.vitals(),destination,new PhantomBackgroundState.Clock(state.clock().rngState(),0,state.clock().residualEncounterMillis()),Map.of(),state.autoGetSkills(),List.of(),null,
						new PhantomBackgroundTransaction.CatchupMutation(running.state(),running.rowVersion(),running.state().advanceTo(102)),
						new PhantomBackgroundTransaction.PolicyMutation(context,PhantomBackgroundSimulationPolicy.Operation.TRAVEL,policy,lease,null,new PhantomBackgroundTransaction.OffscreenProof(admission,authority,1,1000,destination)));
					final var beforeComponent = profiles.findComponent(fixture.profileId(),PhantomBackgroundState.COMPONENT_TYPE).orElseThrow();
					final var result = fixture.transaction().execute(command);
					if (mode == 1)
					{
						PhantomAssertions.assertEquals(PhantomBackgroundTransaction.Status.OFFSCREEN_WAIT,result.status(),"F7 untagged nonempty mismatch was accepted.");
						PhantomAssertions.assertEquals(beforeComponent,profiles.findComponent(fixture.profileId(),PhantomBackgroundState.COMPONENT_TYPE).orElseThrow(),"F7 rejection changed the receipt/state row.");
						PhantomAssertions.assertEquals(running,histories.load(fixture.profileId()).orElseThrow(),"F7 rejection advanced the owned interval.");
						PhantomAssertions.assertEquals(0,calls.get(),"Hash mismatch reached later admission despite the strict fence.");
					}
					else
					{
						PhantomAssertions.assertEquals(PhantomBackgroundTransaction.Status.SUCCESS,result.status(),"F7 matched/empty migration control did not commit.");
						PhantomAssertions.assertEquals(PhantomBackgroundTransaction.Status.IDEMPOTENT,fixture.transaction().execute(command).status(),"F7 duplicate offscreen receipt was not idempotent.");
						PhantomAssertions.assertTrue(calls.get()>0,"Positive offscreen control did not reach admission.");
					}
					PhantomAssertions.assertFalse(fixture.transaction().execute(fixture.command()).successful(),"Stale root/receipt replay was accepted after a goal revision.");
					fixture.assertTotals();
				}
			}
			PhantomAssertions.assertEquals(0,PhantomIdentityLeaseRegistry.getInstance().getActiveLeaseCount(),"SQL offscreen tests retained identity leases.");
			System.out.println("SQL130_F7_PASS;MATCHED_AND_EMPTY=COMMITTED;UNTAGGED_MISMATCH=OFFSCREEN_WAIT;DUPLICATE=IDEMPOTENT;STALE_ROOT=REJECTED;EXP_SP=UNCHANGED;ROUTE_AUTHORITY=MODELED;NATIVE_PLAYERS=0");
		}
		finally { org.l2jmobius.commons.threads.ThreadPool.shutdown(); }
	}
	private record SqlTravelAuthority(PhantomBackgroundState.Hashes hashes, PhantomBackgroundState.Position destination) implements PhantomBackgroundAuthority
	{
		@Override public PhantomBackgroundState capture(long id,Player player,PhantomGoal goal,PhantomBackgroundState previous) { throw new UnsupportedOperationException("SQL-only authority."); }
		@Override public boolean matchesRuntime(Player player,PhantomBackgroundState state) { return false; }
		@Override public FarmInput farmInput(PhantomBackgroundState state,PhantomBackgroundGoalSpec goal) { throw new UnsupportedOperationException("SQL-only authority."); }
		@Override public TravelAdvance advanceTravel(PhantomBackgroundState state,PhantomBackgroundGoalSpec goal,long elapsed) { throw new UnsupportedOperationException("SQL-only authority."); }
		@Override public Optional<PhantomBackgroundState.Position> canonicalRecoveryPosition(int x,int y,int z,int instance,int heading) { return Optional.empty(); }
		@Override public List<PhantomBackgroundState.AutoGetSkill> autoGetSkills(PhantomBackgroundState.Identity identity,int level) { return List.of(); }
		@Override public long topologyGeneration() { return 1; }
		@Override public long offscreenAdmissionGeneration() { return 1; }
		@Override public boolean canFarmAt(PhantomBackgroundState.Position position,PhantomBackgroundGoalSpec goal) { return true; }
		@Override public Optional<PhantomBackgroundState.Position> offscreenDestination(PhantomBackgroundState state,PhantomBackgroundGoalSpec goal) { return Optional.of(destination); }
		@Override public TravelAdvance advanceOffscreenTravel(PhantomBackgroundState state,PhantomGoal goal,PhantomBackgroundOperationKey key,long elapsed,PhantomBackgroundSimulationPolicy policy)
		{ return new TravelAdvance(TravelAdvance.Status.ARRIVED,destination,new PhantomBackgroundState.Clock(state.clock().rngState(),0,state.clock().residualEncounterMillis()),"offscreen-outdoor"); }
	}
	private record ReceiptFixture(long profileId, int objectId, org.l2jmobius.gameserver.phantoms.decision.PhantomGoal goal,
		org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction transaction, org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction.Command command)
	{
		void commitOnce()
		{
			final var result = transaction.execute(command);
			if (result.status() == org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction.Status.IDEMPOTENT) { throw new java.util.ConcurrentModificationException("Interval already committed."); }
			PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction.Status.SUCCESS,result.status(),"Background JDBC interval commit failed.");
		}
		void verify() { PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction.Status.SUCCESS,transaction.reconcileVerifyPending(profileId,objectId).status(),"Background verification failed."); }
		void assertTotals() throws Exception
		{
			try (var connection = DatabaseFactory.getConnection();var statement = connection.prepareStatement("SELECT exp,sp FROM characters WHERE charId=?"))
			{ statement.setInt(1,objectId);try(var rows=statement.executeQuery()){PhantomAssertions.assertTrue(rows.next() && rows.getLong(1)==1 && rows.getLong(2)==1,"Duplicate interval changed actual EXP/SP.");} }
		}
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
	private static void executionHandoff(PhantomProfileRepository profiles, Path module) throws Exception
	{
		try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement(
			"SELECT COUNT(*) FROM phantom_profile_components e JOIN phantom_profile_components p ON p.profile_id=e.profile_id AND p.component_type=? WHERE e.component_type=? AND e.component_schema_version<>?"))
		{
			query.setString(1, org.l2jmobius.gameserver.phantoms.population.PhantomPopulationState.COMPONENT_TYPE);
			query.setString(2, org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionHandoffStore.COMPONENT_TYPE);
			query.setInt(3, org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionHandoff.SCHEMA_VERSION);
			try (var rows = query.executeQuery()) { if (!rows.next() || rows.getLong(1) != 0) { throw new IllegalStateException("Managed execution rows require an explicit migration; implicit grants are forbidden."); } }
		}
		System.out.println("SQL130_EXECUTION_OLD_MANAGED_ROWS=0;IMPLICIT_MIGRATION=DENIED");
		final long id = profiles.create(null).profileId(); // Dedicated SQL-only fixture: never a managed/native subject.
		final long wall = System.currentTimeMillis();
		final var solo = new org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef("execution.solo", Long.toString(id));
		final var party = new org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef("execution.party", "sql130.generation7");
		final var root = new org.l2jmobius.gameserver.phantoms.decision.PhantomGoal(13_000_000 + id, PhantomBackgroundGoalSpec.GOAL_TYPE,
			org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus.ACTIVE,
			new org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef("profile", Long.toString(id)),
			new org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef("target", "sql130"), 10, 2, "method.test", List.of(), null,
			"purpose.test", 500, 20, 30, wall + 100_000, Map.of(), "reason.test", 7);
		final var goals = new PhantomGoalStateStore(profiles); final var savedGoal = goals.insert(id, root);
		final var store = new org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionHandoffStore(profiles);
		final var initial = store.initialize(id, solo);
		final var intents = new org.l2jmobius.gameserver.phantoms.commerce.PhantomSupplyIntentStore(profiles, goals);
		final var binding = new org.l2jmobius.gameserver.phantoms.commerce.PhantomSupplyIntentStore.Binding(7, 111, 0, 0, 0,
			new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(10, 20, -30, 0), 0);
		final var child = org.l2jmobius.gameserver.phantoms.decision.PhantomJourneyLifetime.start(new org.l2jmobius.gameserver.phantoms.decision.PhantomGoal(root.goalId(), "maintain.supplies",
			root.status(), root.subject(), new org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef("item", "4"), 5, 0, "commerce.buy",
			List.of(new org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef("supply.npc", "0:1:2:3:4:5")), root.selectedAnchor(), "supplies.autonomous", 700, 0, 30,
			wall + 100_000, Map.of("source.index", 0L, "equipment.weapon", 123L, "equipment.reserve", 20L), "supplies.equipment.prepare", 11, binding.encode()), wall);
		final var savedChild = intents.saveIntent(id, root, -1, child);
		final var operation = new org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceipt.OperationRequest(
			org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceipt.OperationKind.BUY, 1, 2, 3, 4, 0, 5, 10, 0, 0, 0, "", 0, 0, 0);
		final var receipt = org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceipt.prepared(id, child.goalId(), child.revision(), operation,
			new org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceipt.ConservationFacts(100, 20, 5, 0, 0, 0, 0),
			new org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceipt.ConservationFacts(90, 25, 5, 0, 0, 0, 0));
		race(() -> store.request(initial, party, "left", 1000, 5000), () -> store.request(initial, party, "right", 1000, 5000));
		final var requested = store.load(id).orElseThrow();
		PhantomAssertions.assertThrows(java.util.ConcurrentModificationException.class,
			() -> new org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceiptStore(profiles).save(-1, receipt), "REQUEST admitted a new economic operation instead of only earned completion.");
		final var yielded = store.yielded(requested, 1001);
		final var paused = org.l2jmobius.gameserver.phantoms.decision.PhantomJourneyLifetime.pause(root, wall + 2);
		final var pausedChild = org.l2jmobius.gameserver.phantoms.decision.PhantomJourneyLifetime.pause(child, wall + 2);
		final String receiptType = org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceipt.COMPONENT_TYPE;
		final var checkpoint = store.checkpoint(yielded, List.of(goals.componentMutation(savedGoal.rowVersion(), paused),
			new PhantomProfileRepository.ComponentMutation("supply.intent", savedChild.rowVersion(), 1, new org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateCodec().encode(pausedChild))),
			List.of("goal.runtime", "supply.intent", receiptType), 1002);
		PhantomAssertions.assertEquals(7L, goals.load(id).orElseThrow().goal().revision(), "Checkpoint changed the accepted operation revision.");
		// Failure after CHECKPOINT: a fresh store must see its exact resumable source proof.
		final var fresh = new org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionHandoffStore(PhantomProfileRepository.open());
		PhantomAssertions.assertEquals(checkpoint, fresh.load(id).orElseThrow(), "Restart lost the durable execution checkpoint.");
		race(() -> store.transfer(checkpoint, List.of(), 1003), () -> fresh.transfer(checkpoint, List.of(), 1003));
		final var transferred = fresh.load(id).orElseThrow();
		PhantomAssertions.assertEquals(party, transferred.state().owner(), "SQL CAS did not grant the recipient.");
		PhantomAssertions.assertEquals(2L, transferred.state().generation(), "Two transfers advanced one generation twice.");
		PhantomAssertions.assertEquals(paused, goals.load(id).orElseThrow().goal(), "Transfer altered economic progress/resources.");
		PhantomAssertions.assertEquals(pausedChild, intents.intent(id).orElseThrow().goal(), "Transfer lost the accepted optional equipment child.");
		PhantomAssertions.assertTrue(intents.load(id).isEmpty(), "A suspended source child was still executable through its ordinary store after Party transfer.");
		final var resumeAttempt = org.l2jmobius.gameserver.phantoms.decision.PhantomJourneyLifetime.resume(pausedChild, wall + 300);
		PhantomAssertions.assertThrows(java.util.ConcurrentModificationException.class,
			() -> intents.saveIntent(id, paused, intents.intent(id).orElseThrow().rowVersion(), resumeAttempt), "A pulse/stale source resumed its child under the recipient's grant.");
		PhantomAssertions.assertThrows(RuntimeException.class, () -> store.recoverSource(requested), "Stale source reclaimed the winning recipient.");
		PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionHandoff.Phase.RECOVERING, transferred.state().phase(), "CAS transfer bypassed recipient recovery.");
		// A changed post-CAS obligation cannot be silently acknowledged by recovery, even after a no-op root resave.
		final var rootBeforeRecovery = goals.load(id).orElseThrow(); goals.replace(id, rootBeforeRecovery.rowVersion(), rootBeforeRecovery.goal());
		PhantomAssertions.assertThrows(java.util.ConcurrentModificationException.class, () -> store.recovered(transferred, List.of(), List.of()), "Recovery ignored an unacknowledged changed checkpoint.");
		PhantomAssertions.assertEquals(transferred, store.load(id).orElseThrow(), "Rejected recovery prematurely opened execution.");
		final var activeParty = store.recovered(transferred, List.of(), List.of(profiles.findComponent(id, "goal.runtime").orElseThrow()));
		// Return/cancellation use the same state machine, with a fresh generation and preserved operation identity.
		final var returning = store.request(activeParty, solo, "return", 1100, 6000);
		final var cancelled = store.recoverSource(returning);
		PhantomAssertions.assertEquals(party, cancelled.state().owner(), "Cancelling return transferred before checkpoint.");
		PhantomAssertions.assertEquals(3L, cancelled.state().generation(), "Cancellation reused stale execution grants.");
		final var returnYield = store.yielded(store.request(cancelled, solo, "return2", 1200, 6000), 1201);
		final var completedReturnCheckpoint = store.checkpoint(returnYield, List.of(), List.of("goal.runtime", "supply.intent", receiptType), 1202);
		final var returned = store.transfer(completedReturnCheckpoint, List.of(), 1203);
		PhantomAssertions.assertEquals(solo, returned.state().owner(), "Completed return did not restore the lawful source.");
		final long pausedChildVersion = intents.intent(id).orElseThrow().rowVersion();
		final var resumeMutation = List.of(new PhantomProfileRepository.ComponentMutation("supply.intent", pausedChildVersion, 1, new org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateCodec().encode(resumeAttempt)));
		race(() -> store.recovered(returned, resumeMutation, List.of()), () -> fresh.recovered(returned, resumeMutation, List.of()));
		final var returnedActive = store.load(id).orElseThrow();
		final var resumedChild = intents.intent(id).orElseThrow();
		PhantomAssertions.assertEquals(pausedChildVersion + 1, resumedChild.rowVersion(), "Two recovery contenders resumed the child twice.");
		PhantomAssertions.assertEquals(child.revision(), resumedChild.goal().revision(), "Resuming the child changed its Commerce operation identity.");
		PhantomAssertions.assertEquals(child.payloadText(), resumedChild.goal().payloadText(), "Resuming the child lost its native/class/weapon/root binding.");
		PhantomAssertions.assertEquals(child.expenseBudget(), resumedChild.goal().expenseBudget(), "Handoff spent or discarded the child's budget.");
		PhantomAssertions.assertEquals(child.currentAmount(), resumedChild.goal().currentAmount(), "Handoff fabricated child progress.");
		PhantomAssertions.assertTrue(intents.load(id).isPresent(), "The returned child remained permanently suspended.");
		final var secondYield = store.yielded(store.request(returnedActive, party, "party-again", 1300, 6000), 1301);
		final var secondCheckpoint = store.checkpoint(secondYield,
			List.of(new PhantomProfileRepository.ComponentMutation("supply.intent", resumedChild.rowVersion(), 1, new org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateCodec().encode(
				org.l2jmobius.gameserver.phantoms.decision.PhantomJourneyLifetime.pause(resumedChild.goal(), wall + 301)))), List.of("goal.runtime", "supply.intent", receiptType), 1302);
		final var secondParty = store.transfer(secondCheckpoint, List.of(), 1303);
		final var failureYield = store.yielded(store.request(secondParty, solo, "return-failure", 1400, 6000), 1401);
		final var returnCheckpoint = store.checkpoint(failureYield, List.of(), List.of("goal.runtime", "supply.intent", receiptType), 1402);
		// An absent receipt is not a waiver: appearance after checkpoint must reject transfer atomically.
		PhantomAssertions.assertThrows(java.util.ConcurrentModificationException.class,
			() -> new org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceiptStore(profiles).save(-1, receipt), "The previous economic owner wrote a new receipt after execution transferred.");
		// Model a foreign/stale unguarded writer to exercise the transaction's negative dependency itself.
		profiles.insertComponent(id, receiptType, org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceipt.SCHEMA_VERSION, receipt.encode());
		PhantomAssertions.assertThrows(java.util.ConcurrentModificationException.class, () -> profiles.mutateProfileComponentsAtomically(
			List.of(new PhantomProfileRepository.ProfileComponentMutation(id, new PhantomProfileRepository.ComponentMutation(
				org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionHandoffStore.COMPONENT_TYPE, returnCheckpoint.component().rowVersion(), returnCheckpoint.component().componentSchemaVersion(), returnCheckpoint.component().payload()))),
			List.of(returnCheckpoint.component()), List.of(new PhantomProfileRepository.ComponentAbsence(id, receiptType))), "SQL absent dependency was checked only outside its transaction.");
		PhantomAssertions.assertThrows(java.util.ConcurrentModificationException.class, () -> store.transfer(returnCheckpoint, List.of(), 1403), "A new Commerce obligation was ignored by transfer.");
		PhantomAssertions.assertEquals(returnCheckpoint, store.load(id).orElseThrow(), "Rejected transfer partially published an owner.");
		// A changed existing root also invalidates its proof; the protocol row and checkpoint remain untouched.
		final var currentGoal = goals.load(id).orElseThrow();
		goals.replace(id, currentGoal.rowVersion(), org.l2jmobius.gameserver.phantoms.decision.PhantomJourneyLifetime.resume(paused, wall + 300));
		PhantomAssertions.assertThrows(java.util.ConcurrentModificationException.class, () -> store.transfer(returnCheckpoint, List.of(), 1404), "Changed owner progress passed a stale checkpoint.");
		Files.writeString(module.resolve(".phantom-local/task130/ownership-restart.properties"), "profileId=" + id + "\nrowVersion=" + returnCheckpoint.component().rowVersion() + "\npayload=" + java.util.Base64.getEncoder().encodeToString(returnCheckpoint.component().payload()) + "\n");
		System.out.println("SQL130_EXECUTION_HANDOFF_PASS;REQUEST_AND_TRANSFER_ONE_WINNER=true;CHECKPOINT_STORE_RECREATION=true;OPTIONAL_CHILD_SUSPEND_AND_RESUME=true;STALE_SOURCE_ROOT_AND_NEW_RECEIPT_REJECTED=true;OPERATION_REVISIONS=7,11;NATIVE=false");
		executionDriver(profiles, root, child);
	}

	/** Real Goal/child/CAS stores; native leaf is explicit absence of an unlinked SQL-only profile. */
	private static void executionDriver(PhantomProfileRepository profiles, PhantomGoal rootTemplate, PhantomGoal childTemplate) throws Exception
	{
		final long id = profiles.create(null).profileId(), peer = profiles.create(null).profileId(), wall = System.currentTimeMillis();
		final var codec = new org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateCodec();
		final var goals = new PhantomGoalStateStore(profiles);
		final var intents = new org.l2jmobius.gameserver.phantoms.commerce.PhantomSupplyIntentStore(profiles, goals);
		final var root = executionFixtureGoal(rootTemplate, id);
		final var child = executionFixtureGoal(childTemplate, id);
		goals.insert(id, root); intents.saveIntent(id, root, -1, child);
		final var parties = new PhantomPartyStore(profiles);
		final var group = new PhantomLogicalPartyCoordinator(parties).agree(agreement(id, peer, 1)).orElseThrow();
		final var solo = PhantomExecutionHandoff.solo(id);
		final var party = new PhantomDomainRef("execution.party", group.group().groupId() + "." + group.group().generation());
		final var store = new PhantomExecutionHandoffStore(profiles); final var gate = new PhantomExecutionAdmission(4);
		final var nativeBoundaryAvailable = new java.util.concurrent.atomic.AtomicBoolean(true);
		final var lostAcknowledgement = new java.util.concurrent.atomic.AtomicBoolean();
		final var paused = new java.util.concurrent.atomic.AtomicBoolean();
		final var port = new PhantomDecisionEngine.ExecutionOwner()
		{
			@Override public PhantomDecisionEngine.ExecutionReadiness prepareYield(PhantomExecutionHandoffStore.Stored row, long now)
			{ return new PhantomDecisionEngine.ExecutionReadiness(true, "sql130.existing_owner_quiet", now); }
			@Override public PhantomDecisionEngine.ExecutionReadiness prepareRecovery(PhantomExecutionHandoffStore.Stored row, long now)
			{ return prepareYield(row, now); }
			@Override public boolean checkpointBoundary(PhantomExecutionHandoffStore.Stored row, Runnable callback)
			{
				if (!nativeBoundaryAvailable.get()) { return false; }
				if (profiles.find(id).orElseThrow().characterObjectId() != null) { throw new IllegalStateException("SQL-only absence leaf acquired a native identity."); }
				callback.run();
				if (lostAcknowledgement.getAndSet(false)) { throw new IllegalStateException("sql130.injected_ack_loss"); }
				return true;
			}
			@Override public PhantomDecisionEngine.ExecutionCheckpoint checkpoint(PhantomExecutionHandoffStore.Stored row, boolean recovery, long now)
			{
				final var raw = profiles.findComponent(id, "supply.intent").orElseThrow();
				final var current = codec.decode(raw.payload()); final var changes = new java.util.ArrayList<PhantomProfileRepository.ComponentMutation>();
				if (row.state().owner().equals(solo))
				{
					final var rootNow = goals.load(id).orElseThrow().goal();
					final var rebound = recovery ? org.l2jmobius.gameserver.phantoms.commerce.PhantomSupplyIntentStore.rebindSuspended(current, rootNow) : current;
					PhantomAssertions.assertTrue(org.l2jmobius.gameserver.phantoms.commerce.PhantomSupplyIntentStore.bound(rebound, rootNow), "A changed root silently discarded an accepted child.");
					final var next = recovery ? PhantomJourneyLifetime.resume(rebound, wall + now - 1000) : PhantomJourneyLifetime.pause(current, wall + now - 1000);
					if (!next.equals(current)) { changes.add(new PhantomProfileRepository.ComponentMutation("supply.intent", raw.rowVersion(), 1, codec.encode(next))); }
					paused.set(!recovery);
				}
				return new PhantomDecisionEngine.ExecutionCheckpoint(changes, List.of("goal.runtime", "supply.intent", org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceipt.COMPONENT_TYPE),
					List.of(raw, profiles.findComponent(id, "goal.runtime").orElseThrow()));
			}
			@Override public PhantomDecisionEngine.ExecutionAuthority authority(PhantomExecutionHandoffStore.Stored row, long now)
			{
				if (row.state().recipient().equals(solo)) { return new PhantomDecisionEngine.ExecutionAuthority(prepareYield(row, now), List.of(profiles.findComponent(id, "goal.runtime").orElseThrow())); }
				final var current = parties.loadLogicalGroup(id).orElseThrow();
				if (!current.equals(group)) { throw new java.util.ConcurrentModificationException("sql130.recipient_generation_changed"); }
				return new PhantomDecisionEngine.ExecutionAuthority(prepareYield(row, now), List.of(profiles.findComponent(id, PhantomLogicalParty.COMPONENT_TYPE).orElseThrow()));
			}
		};
		final var engine = executionDriverEngine(store, gate, port);
		final var initial = store.initialize(id, solo); gate.publish(id, initial.component().rowVersion(), initial.state());
		final var earned = gate.tryAcquire(id, solo);
		var row = engine.requestExecution(initial, party, "driver-party", 1000, 5000);
		PhantomAssertions.assertFalse(engine.advanceExecution(row, 1001, false).progressed(), "Driver ignored an earned lease."); earned.close();
		nativeBoundaryAvailable.set(false);
		PhantomAssertions.assertFalse(engine.advanceExecution(row, 1001, false).progressed(), "Death/STORE boundary refusal transferred execution.");
		PhantomAssertions.assertEquals(row, store.load(id).orElseThrow(), "Refused native boundary wrote a partial phase.");
		nativeBoundaryAvailable.set(true); row = engine.advanceExecution(row, 1002, false).stored();
		PhantomAssertions.assertTrue(paused.get() && PhantomJourneyLifetime.paused(intents.intent(id).orElseThrow().goal()), "Driver transferred without persisting the optional child pause.");
		PhantomAssertions.assertEquals(PhantomExecutionHandoff.Phase.CHECKPOINTED, row.state().phase(), "Driver skipped durable checkpoint.");
		lostAcknowledgement.set(true); final var checkpointed = row;
		PhantomAssertions.assertThrows(IllegalStateException.class, () -> engine.advanceExecution(checkpointed, 1003, false), "Injected transfer acknowledgement loss was swallowed.");
		PhantomAssertions.assertEquals(null, gate.tryAcquire(id, party), "Unknown transfer acknowledgement opened recipient execution.");
		row = engine.recoverExecutionAcknowledgement(store.load(id).orElseThrow());
		PhantomAssertions.assertEquals(PhantomExecutionHandoff.Phase.RECOVERING, row.state().phase(), "Unknown transfer skipped recovery.");
		final var recipientRecovery = row;
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> store.recovered(recipientRecovery, List.of(), List.of("goal.runtime"), List.of()), "Recovery discarded child/Commerce checkpoint dependencies.");
		row = engine.advanceExecution(row, 1004, false).stored();
		PhantomAssertions.assertEquals(party, row.state().owner(), "Recipient recovery restored the wrong owner.");
		try (var current = gate.tryAcquire(id, party)) { PhantomAssertions.assertTrue(current != null && current.valid(), "Recovered Party could not execute."); }
		final var partyRoot = new PhantomGoal(root.goalId(), root.goalType(), root.status(), root.subject(), root.target(), root.requiredAmount(), root.currentAmount(), root.acquisitionMethod(), root.validSources(), root.selectedAnchor(), root.purposeKey(), root.priority(), root.riskBudget(), root.expenseBudget(), root.deadlineEpochMillis(), root.constraints(), "sql130.party.root_preparation", root.revision() + 1, root.payloadText());
		goals.replace(id, goals.load(id).orElseThrow().rowVersion(), partyRoot);
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> org.l2jmobius.gameserver.phantoms.commerce.PhantomSupplyIntentStore.rebindSuspended(child, partyRoot), "An unpaused child silently changed its parent authority.");
		row = engine.requestExecution(row, solo, "driver-return", 1100, 5000);
		row = engine.advanceExecution(row, 1101, false).stored(); row = engine.advanceExecution(row, 1102, false).stored();
		row = engine.advanceExecution(row, 1103, false).stored();
		final var resumed = intents.intent(id).orElseThrow().goal();
		PhantomAssertions.assertFalse(PhantomJourneyLifetime.paused(resumed), "Returned child stayed permanently occupied.");
		PhantomAssertions.assertEquals(child.revision(), resumed.revision(), "Driver changed economic operation identity.");
		PhantomAssertions.assertEquals(child.currentAmount(), resumed.currentAmount(), "Driver changed child progress.");
		PhantomAssertions.assertEquals(child.expenseBudget(), resumed.expenseBudget(), "Driver changed child resources.");
		PhantomAssertions.assertEquals(partyRoot.revision(), org.l2jmobius.gameserver.phantoms.commerce.PhantomSupplyIntentStore.Binding.parse(resumed).rootRevision(), "Recovery did not adopt the current parent revision.");
		row = engine.requestExecution(row, party, "driver-timeout", 1200, 1300);
		row = engine.advanceExecution(row, 1300, false).stored();
		PhantomAssertions.assertEquals(PhantomExecutionHandoff.Phase.RECOVERING, row.state().phase(), "Timeout reopened a potentially paused source.");
		row = engine.advanceExecution(row, 1301, false).stored();
		final var restartedGate = new PhantomExecutionAdmission(4); final var restarted = executionDriverEngine(new PhantomExecutionHandoffStore(PhantomProfileRepository.open()), restartedGate, port);
		row = restarted.recoverExecutionAcknowledgement(row);
		PhantomAssertions.assertEquals(null, restartedGate.tryAcquire(id, solo), "Restart granted normal work from a persisted ACTIVE row.");
		row = restarted.advanceExecution(row, 1400, false).stored();
		try (var current = restartedGate.tryAcquire(id, solo)) { PhantomAssertions.assertTrue(current != null && current.valid(), "Restart recovery did not resume its child."); }
		System.out.println("SQL130_EXECUTION_DRIVER_PASS;EARNED_AND_NATIVE_BOUNDARY_WAIT=true;PAUSE_TRANSFER_RECOVER_RETURN=true;ACK_LOSS_TIMEOUT_RESTART=true;ECONOMIC_IDENTITY_PROGRESS_BUDGET_PRESERVED=true;NATIVE_LEAF=UNLINKED_PROFILE_ABSENCE");
	}
	private static PhantomDecisionEngine executionDriverEngine(PhantomExecutionHandoffStore store, PhantomExecutionAdmission gate, PhantomDecisionEngine.ExecutionOwner port)
	{
		final var candidates = new org.l2jmobius.gameserver.phantoms.decision.PhantomCandidateRegistry(); candidates.seal();
		final var handlers = new org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry(); handlers.seal();
		final var engine = new PhantomDecisionEngine(new PhantomGoalStateStore(PhantomProfileRepository.open()), candidates, handlers, new org.l2jmobius.gameserver.phantoms.PhantomMetrics(), 4);
		engine.installExecutionHandoff(store, gate, Map.of("execution.solo", port, "execution.party", port)); engine.start(); return engine;
	}
	private static PhantomGoal executionFixtureGoal(PhantomGoal value, long id)
	{
		return new PhantomGoal(13_000_000 + id, value.goalType(), value.status(), new PhantomDomainRef("profile", Long.toString(id)), value.target(), value.requiredAmount(), value.currentAmount(), value.acquisitionMethod(), value.validSources(), value.selectedAnchor(), value.purposeKey(), value.priority(), value.riskBudget(), value.expenseBudget(), value.deadlineEpochMillis(), value.constraints(), value.reasonKey(), value.revision(), value.payloadText());
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
