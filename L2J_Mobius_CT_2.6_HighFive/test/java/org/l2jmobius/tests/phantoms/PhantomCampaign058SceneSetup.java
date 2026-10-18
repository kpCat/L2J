/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.commons.util.HexUtil;
import org.l2jmobius.gameserver.data.xml.InitialEquipmentData;
import org.l2jmobius.gameserver.data.xml.InitialShortcutData;
import org.l2jmobius.gameserver.data.xml.MapRegionData;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeService;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationState;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.progression.L2jProgressionBackend;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionCatalog;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionCatalogBuilder;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionPolicy;
import org.l2jmobius.gameserver.taskmanagers.PlayerAutoSaveTaskManager;

/** One explicit persistent TEST preparation BEFORE measurement, not natural career or a product grant. */
public final class PhantomCampaign058SceneSetup
{
	private record Role(String name, int rootClass, int activeClass, int level, int weapon, List<Integer> armor) { }
	private static final List<Integer> HEAVY = List.of(6373, 6374, 6375, 6376, 6378);
	private static final List<Integer> ROBE = List.of(6383, 6384, 6385, 6386);
	private static final List<Role> ROLES = List.of(
		new Role("T058Scout", 0, 0, 1, 0, List.of()),
		new Role("T058Tank", 0, 90, 80, 2500, HEAVY),
		new Role("T058Mage", 10, 94, 80, 6579, ROBE),
		new Role("T058Heal", 10, 97, 80, 6579, ROBE),
		new Role("T058Buff", 18, 100, 80, 2500, HEAVY),
		new Role("T058Mana", 25, 105, 80, 6579, ROBE));

	private PhantomCampaign058SceneSetup() { }

	public static void main(String[] args) throws Exception
	{
		if ((args.length != 1) || !List.of("prepare", "continue-empty", "manual-human80", "continuation061-preview", "continuation061-career", "continuation061-career-finish-input").contains(args[0]) || !PhantomCampaign058PersistentLane.enabled()) { throw new IllegalArgumentException("Explicit persistent058a preparation required."); }
		final Path lane = PhantomCampaign058PersistentLane.LANE;
		final Path runtime = PhantomCampaign058PersistentLane.RUNTIME;
		PhantomCampaign058PersistentLane.validateFiles(PhantomCampaign058PersistentLane.MODULE, Path.of(System.getProperty("phantom.test.config", "")), true);
		PhantomAssertions.assertEquals(runtime.resolve("libs/GameServer.jar").toRealPath(), Path.of(Player.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath(), "Scene must use the actual current ordinary GameServer.jar.");
		if (args[0].equals("continuation061-preview")) { PhantomCareer061PreviewSetup.run(); return; }
		if (args[0].equals("continuation061-career")) { PhantomCareer061PreviewSetup.runCareer(); return; }
		if (args[0].equals("continuation061-career-finish-input")) { PhantomCareer061PreviewSetup.finishCareerInput(); return; }
		if (args[0].equals("manual-human80")) { prepareManualHuman80(lane); return; }
		PhantomAssertions.assertFalse(Files.exists(lane.resolve("scene-setup.complete")), "Persistent TEST scene has already been prepared.");
		if (args[0].equals("prepare")) { Files.createFile(lane.resolve("scene-setup.started")); }
		else { PhantomAssertions.assertTrue(Files.isRegularFile(lane.resolve("scene-setup.started")), "Empty-only continuation has no previous setup boundary."); }
		try
		{
			PhantomHeadlessPlayerTestEnvironment.initializeNativeInfrastructure(new PhantomTestContext(58005822, PhantomCampaign058PersistentLane.MODULE, lane.resolve("reports/scene-setup")));
			InitialEquipmentData.getInstance(); InitialShortcutData.getInstance(); MapRegionData.getInstance();
			for (String table : List.of("accounts", "characters", "phantom_profiles", "phantom_profile_components"))
			{
				try (var connection = DatabaseFactory.getConnection(); var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM " + table))
				{ PhantomAssertions.assertTrue(rows.next() && rows.getLong(1) == 0, "Scene refuses existing data: " + table); }
			}
			final Properties configuration = new Properties();
			try (var reader = Files.newBufferedReader(Path.of("config/Custom/PhantomPlayers.ini"), StandardCharsets.UTF_8)) { configuration.load(reader); }
			final ZoneId zone = ZoneId.of(configuration.getProperty("PhantomPopulationTimeZone"));
			final var catalog = PhantomPopulationCatalog.load(Path.of("data/phantoms/population/high-five-population-v2.xml"), zone);
			final var predecessor = PhantomPopulationCatalog.load(Path.of("data/phantoms/population/high-five-population-v1.xml"), zone);
			final var repository = PhantomProfileRepository.open();
			final var store = new PhantomPopulationStore(repository, catalog, predecessor, zone);
			long firstProfile;
			try (var connection = DatabaseFactory.getConnection(); var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT AUTO_INCREMENT FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='phantom_profiles'"))
			{ PhantomAssertions.assertTrue(rows.next(), "Profile identity sequence is absent."); firstProfile = rows.getLong(1); }
			PhantomAssertions.assertTrue(firstProfile > 0, "Invalid profile identity sequence.");
			long seed = 0;
			Map<Integer, Role> plan = Map.of();
			for (long candidate = 58005822; candidate < 58005838; candidate++)
			{
				final Map<Integer, Role> selected = new LinkedHashMap<>();
				for (Role role : ROLES)
				{
					for (int ordinal = 1; ordinal <= 32; ordinal++)
					{
						final int root = catalog.chooseClass(mix(candidate, firstProfile + ordinal - 1), catalog.chooseArchetype(candidate, ordinal)).classId();
						if (!selected.containsKey(ordinal) && root == role.rootClass()) { selected.put(ordinal, role); break; }
					}
				}
				if (selected.size() == ROLES.size()) { seed = candidate; plan = selected; break; }
			}
			PhantomAssertions.assertTrue(seed > 0, "Bounded preflight found no complete stock-family TEST role input; no characters created.");
			final List<String> declared = new ArrayList<>(List.of("FIXTURE_PRECONDITION; population32; native level1 birth first; roles80 explicitly prepared; no natural career claim; seed=" + seed, "ordinal\tname\trootClass\tactiveClass\tlevel"));
			for (var entry : plan.entrySet()) { final Role role = entry.getValue(); declared.add(entry.getKey() + "\t" + role.name() + "\t" + role.rootClass() + "\t" + role.activeClass() + "\t" + role.level()); }
			if (Files.exists(lane.resolve("scene-plan058.tsv"))) { PhantomAssertions.assertEquals(declared, Files.readAllLines(lane.resolve("scene-plan058.tsv"), StandardCharsets.UTF_8), "Continuation changed the frozen TEST input plan."); }
			else { Files.write(lane.resolve("scene-plan058.tsv"), declared, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW); }
			final var policy = PhantomProgressionPolicy.productionDefaults();
			// Skill-tree fixture preparation does not query tactical capabilities; use the existing inert authority.
			final var knowledge = PhantomGameKnowledgeService.inertForTesting("0".repeat(64));
			PhantomAssertions.assertTrue(knowledge.start(), "Fixture knowledge authority did not start.");
			final PhantomProgressionCatalog progression;
			try { progression = new PhantomProgressionCatalogBuilder().build(new L2jProgressionBackend(null, Path.of("."), knowledge::query).load(policy), policy); }
			finally { knowledge.beginStop(); PhantomAssertions.assertTrue(knowledge.finishStop(), "Fixture knowledge did not drain."); }
			final List<String> result = new ArrayList<>(List.of("role\tprofileId\tobjectId\tname\tclassId\tlevel\tx\ty\tz"));
			prepareHuman(lane, progression, result);
			int rolePosition = 1;
			for (int ordinal = 1; ordinal <= 32; ordinal++)
			{
				var snapshot = store.createShell(1, ordinal, seed);
				PhantomAssertions.assertEquals(firstProfile + ordinal - 1, snapshot.profile().profileId(), "Another writer changed prepared profile identities.");
				final Role role = plan.get(ordinal);
				if (role != null)
				{
					PhantomAssertions.assertEquals(role.rootClass(), snapshot.state().classId(), "Declared stock class family differs.");
					snapshot = store.updateState(snapshot, snapshot.state().withName(0, role.name()));
				}
				for (int stage = 0; stage < 12 && snapshot.state().state() != PhantomPopulationState.State.READY; stage++)
				{
					final var advanced = store.advanceCreation(snapshot);
					PhantomAssertions.assertTrue(advanced.outcome() != PhantomPopulationStore.CreationOutcome.INCONSISTENT, "Native stock birth failed: " + advanced.snapshot().state().lastFailure());
					snapshot = store.reload(snapshot.profile().profileId());
				}
				PhantomAssertions.assertEquals(PhantomPopulationState.State.READY, snapshot.state().state(), "Native birth did not finish in its bounded stage count.");
				if (role != null)
				{
					final int objectId = snapshot.state().actualCharacterObjectId();
					try (var suppression = PlayerAutoSaveTaskManager.suppressPopulationLoad(objectId))
					{
						final Player body = Player.load(objectId);
						PhantomAssertions.assertTrue(body != null && !body.isGM() && body.getName().equals(role.name()), "Prepared stock role identity differs.");
						prepareBody(body, role, progression, rolePosition++);
						result.add(row(role.name(), snapshot.profile().profileId(), body));
						body.stopAllTasks(); body.storeMe(); body.deleteMe();
					}
				}
			}
			Files.write(lane.resolve("scene-characters058.tsv"), result, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
			Files.writeString(lane.resolve("scene-setup.complete"), "population=32; preparedPhantoms=6; human=TestAdmin; nativeLevel1Birth=32; roles80=FIXTURE_NOT_CAREER", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
			System.out.println("TASK058_SCENE_PREPARED population=32; TEST roles=6; human=TestAdmin; receipt=" + lane.resolve("scene-characters058.tsv"));
		}
		finally { ThreadPool.shutdown(); DatabaseFactory.close(); }
	}

	/** One new declared manual-QA fixture boundary, preserving the original account and character identity. */
	private static void prepareManualHuman80(Path lane) throws Exception
	{
		PhantomAssertions.assertTrue(Files.isRegularFile(lane.resolve("scene-setup.complete")), "The original scene must already exist.");
		PhantomAssertions.assertFalse(Files.exists(lane.resolve("release-export.started")), "The frozen release export must not be changed.");
		Files.createFile(lane.resolve("manual-human80.started"));
		try
		{
			PhantomHeadlessPlayerTestEnvironment.initializeNativeInfrastructure(new PhantomTestContext(58005836, PhantomCampaign058PersistentLane.MODULE, lane.resolve("reports/manual-human80")));
			try (var connection = DatabaseFactory.getConnection(); var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT charId,account_name,char_name,classid,level,accesslevel,online FROM characters WHERE charId=268435465"))
			{
				PhantomAssertions.assertTrue(rows.next() && (rows.getInt(1) == 268435465) && "localplayer".equals(rows.getString(2)) && "TestAdmin".equals(rows.getString(3)) && (rows.getInt(4) == 10) && (rows.getInt(5) == 10) && (rows.getInt(6) == 0) && (rows.getInt(7) == 0) && !rows.next(), "Exact stopped original TEST human differs.");
			}
			PhantomAssertions.assertTrue(PhantomProfileRepository.open().findByCharacterObjectId(268435465).isEmpty(), "Human fixture must not own a Phantom profile.");
			final var policy = PhantomProgressionPolicy.productionDefaults();
			final var knowledge = PhantomGameKnowledgeService.inertForTesting("0".repeat(64));
			PhantomAssertions.assertTrue(knowledge.start(), "Fixture knowledge did not start.");
			final PhantomProgressionCatalog progression;
			try { progression = new PhantomProgressionCatalogBuilder().build(new L2jProgressionBackend(null, Path.of("."), knowledge::query).load(policy), policy); }
			finally { knowledge.beginStop(); PhantomAssertions.assertTrue(knowledge.finishStop(), "Fixture knowledge did not drain."); }
			try (var suppression = PlayerAutoSaveTaskManager.suppressPopulationLoad(268435465))
			{
				final Player body = Player.load(268435465);
				PhantomAssertions.assertTrue(body != null && !body.isGM() && body.getName().equals("TestAdmin"), "Manual TEST identity changed.");
				prepareBody(body, new Role("TestAdmin", 10, 94, 80, 6579, ROBE), progression, 0);
				body.stopAllTasks(); body.storeMe();
				Files.writeString(lane.resolve("manual-human80.complete"), "FIXTURE_NOT_CAREER; native stored; account/password/objectId unchanged\n" + row("TEST_HUMAN_MANUAL", 0, body), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
				body.deleteMe();
			}
			System.out.println("TASK058_MANUAL_HUMAN80_PREPARED exact TestAdmin268435465/class94/80; FIXTURE_NOT_CAREER");
		}
		finally { ThreadPool.shutdown(); DatabaseFactory.close(); }
	}

	private static void prepareHuman(Path lane, PhantomProgressionCatalog progression, List<String> result) throws Exception
	{
		final String password = Base64.getUrlEncoder().withoutPadding().encodeToString(HexUtil.generateHexBytes(12));
		final String hash = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA").digest(password.getBytes(StandardCharsets.UTF_8)));
		try (var connection = DatabaseFactory.getConnection(); var insert = connection.prepareStatement("INSERT INTO accounts (login,password,accessLevel,lastServer) VALUES (?, ?, 0, 1)"))
		{ insert.setString(1, "localplayer"); insert.setString(2, hash); PhantomAssertions.assertEquals(1, insert.executeUpdate(), "Owned TEST account was not created."); }
		Files.writeString(lane.resolve("config-input/game/config/owner-login058.txt"), "account=localplayer\npassword=" + password + "\ncharacter=TestAdmin\naccessLevel=0\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
		final Player human = Player.create(PlayerTemplateData.getInstance().getTemplate(10), "localplayer", "TestAdmin", new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false));
		PhantomAssertions.assertTrue(human != null && !human.isGM(), "Native TEST human creation failed.");
		prepareBody(human, new Role("TestAdmin", 10, 10, 10, 6, List.of()), progression, 0);
		result.add(row("TEST_HUMAN", 0, human));
		human.setOnlineStatus(true, false); human.stopAllTasks(); human.storeMe(); human.deleteMe();
	}

	private static void prepareBody(Player body, Role role, PhantomProgressionCatalog progression, int position)
	{
		if (role.level() > 1)
		{
			body.getStat().setExp(body.getStat().getExpForLevel(role.level())); body.setBaseClass(role.activeClass()); body.setPlayerClass(role.activeClass()); body.getStat().setLevel((byte) role.level());
			final Map<Integer, Integer> learned = new HashMap<>();
			for (var skill : progression.classSkillLearns(role.activeClass())) { if (skill.minimumCharacterLevel() <= role.level()) { learned.merge(skill.skillId(), skill.skillLevel(), Math::max); } }
			learned.forEach((id, rank) -> body.addSkill(SkillData.getInstance().getSkill(id, rank), true));
			if (role.level() == 80) { body.addSkill(SkillData.getInstance().getSkill(239, 6), true); }
			if (role.activeClass() == 94) { body.addSkill(SkillData.getInstance().getSkill(1339, 101), true); }
			if (role.activeClass() == 105) { body.addSkill(SkillData.getInstance().getSkill(1013, 101), true); }
			if (role.weapon() > 0) { body.getInventory().equipItem(body.getInventory().addItem(ItemProcessType.REWARD, role.weapon(), 1, null, PhantomCampaign058SceneSetup.class)); }
			for (int itemId : role.armor()) { body.getInventory().equipItem(body.getInventory().addItem(ItemProcessType.REWARD, itemId, 1, null, PhantomCampaign058SceneSetup.class)); }
			if (role.activeClass() == 90) { body.getInventory().equipItem(body.getInventory().addItem(ItemProcessType.REWARD, 6377, 1, null, PhantomCampaign058SceneSetup.class)); }
			body.refreshExpertisePenalty(); body.refreshOverloaded(); body.setCurrentHp(body.getMaxHp()); body.setCurrentMp(body.getMaxMp()); body.setCurrentCp(body.getMaxCp());
		}
		final int x = -71350, y = 258050 + position * 20;
		final int z = GeoEngine.getInstance().getHeight(x, y, -3104);
		PhantomAssertions.assertTrue(GeoEngine.getInstance().hasGeo(x, y) && Math.abs(z + 3104) <= 64 && GeoEngine.getInstance().canMoveToTarget(-71350, 258050, -3104, x, y, z, 0), "Prepared TEST placement is not on the verified native corridor.");
		body.setXYZInvisible(x, y, z);
	}

	private static String row(String role, long profile, Player body)
	{
		return role + '\t' + profile + '\t' + body.getObjectId() + '\t' + body.getName() + '\t' + body.getPlayerClass().getId() + '\t' + body.getLevel() + '\t' + body.getX() + '\t' + body.getY() + '\t' + body.getZ();
	}

	/** Same deterministic identity transform as native PhantomPopulationStore. */
	static long mix(long seed, long value)
	{
		long mixed = seed ^ (value + 0x9E3779B97F4A7C15L); mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L; mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL; return mixed ^ (mixed >>> 31);
	}
}
