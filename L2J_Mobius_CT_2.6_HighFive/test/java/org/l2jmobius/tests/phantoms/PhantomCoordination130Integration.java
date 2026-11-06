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
		f7Receipts(profiles, fixtures);
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
