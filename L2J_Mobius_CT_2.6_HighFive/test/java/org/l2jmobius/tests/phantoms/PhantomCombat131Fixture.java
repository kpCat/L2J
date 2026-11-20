/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.config.ConfigLoader;
import org.l2jmobius.gameserver.config.NpcConfig;
import org.l2jmobius.gameserver.config.RatesConfig;
import org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig;
import org.l2jmobius.gameserver.data.enums.CategoryType;
import org.l2jmobius.gameserver.data.xml.CategoryData;
import org.l2jmobius.gameserver.data.xml.InitialEquipmentData;
import org.l2jmobius.gameserver.data.xml.InitialShortcutData;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.data.xml.SkillTreeData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.handler.ItemHandler;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.item.EtcItem;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.type.ActionType;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.phantoms.background.L2jPhantomBackgroundAuthority;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundPlanner;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatPolicy;
import org.l2jmobius.gameserver.phantoms.combat.PhantomNativeCombatCapability;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog.CareerArchetype;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationState;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.progression.PhantomCareerChoice;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;
import org.l2jmobius.gameserver.scripting.ScriptEngine;
import org.l2jmobius.gameserver.taskmanagers.PlayerAutoSaveTaskManager;

/** One-time owned TEST131 SETUP. Creates native identities and stores stock inputs with the master OFF. */
public final class PhantomCombat131Fixture
{
	private static final String SCHEMA = "l2jmobiush5_phantom_test_131";
	private static final String LOGIN = "task131_runtime";
	private static final String JDBC = "jdbc:mysql://127.0.0.1:33331/" + SCHEMA + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectTimeout=10000";
	private static final long SHOTS = 2000;
	private static final long ADENA = 50000;
	private static final int ANCHOR_RADIUS = 1200;
	private static final String HEADER = "status\tprofileId\tobjectId\taccount\trole\tclass\tlevel\tskillId\tskillLevel\tweapon\tshotItem\tshots\tadena\tanchor\tnpcId\tspawn\tpreflightObjectId\tx\ty\tz\texp\tsp";
	private static final String SCENE_HEADER = "profileId\tactorObjectId\tgoalId\tgoalRevision\tplanIdentity\tanchor\tx\ty\tz\tnpcId\tnpcLevel\tspawnKey\trespawnMinMs\trespawnMaxMs\tpreflightObjectId\tpreflightInstanceId\tpreflightGeneration\tqualifyingTargets\tknowledgeSha256\ttopologySha256\tprogressionSha256\tcommerceSha256";

	private record Admission(Path game, Path sql, Path ini, Instant deadline) { }
	private record Birth(long profile, long seed) { }
	private record Scene(String anchor, PhantomTopologyPoint point, int npcId, int npcLevel, String spawn, int respawnMin, int respawnMax,
		PlayerNativeEvidence.Target preflightTarget, Skill skill, PhantomHistoricalBackgroundPlanner.Result plan, int qualifyingTargets) { }
	private record Actor(long profile, int object, String account, PlayerClass profession, int level, Skill skill, int weapon, int shot, long shots, long adena, long exp, long sp, Scene scene) { }

	private enum Role
	{
		MAGE(CategoryType.WIZARD_CATEGORY, true, 210),
		PHYSICAL(CategoryType.WARRIOR_CATEGORY, false, 2626);

		final CategoryType category;
		final boolean magic;
		final int level52Weapon;

		Role(CategoryType category, boolean magic, int level52Weapon)
		{
			this.category = category;
			this.magic = magic;
			this.level52Weapon = level52Weapon;
		}
	}

	public static void main(String[] args)
	{
		int exit = 1;
		try
		{
			final Admission admission = admit(args);
			ConfigLoader.init();
			check(!PhantomPlayersConfig.isEnabled(), "MASTER_ENABLED");
			DatabaseFactory.initFromConfig(admission.ini().toString());
			verifySession(admission.sql());
			PhantomHeadlessPlayerTestEnvironment.initializeOwnedPlayerInfrastructure();
			InitialEquipmentData.getInstance();
			InitialShortcutData.getInstance();
			ScriptEngine.getInstance().executeScript(ScriptEngine.MASTER_HANDLER_FILE);
			try (var source = PhantomBackgroundSuite.ProductionAuthorityFixture.start(admission.game().resolve("data/phantoms/topology"), admission.game()))
			{
				prepare(admission, source);
			}
			exit = 0;
		}
		catch (Throwable failure)
		{
			System.err.println("TASK131_N01_FIXTURE_FAILED " + failure.getClass().getSimpleName());
			if (failure.getMessage() != null && failure.getMessage().matches("TASK131_N01_[A-Z0-9_]+")) { System.err.println(failure.getMessage()); }
			for (StackTraceElement frame : failure.getStackTrace())
			{
				if (frame.getClassName().startsWith("org.l2jmobius.")) { System.err.println(frame); }
			}
		}
		finally
		{
			if (DatabaseFactory.isInitialized())
			{
				try { ThreadPool.shutdown(); }
				finally { DatabaseFactory.close(); }
			}
		}
		System.out.println("TASK131_N01_FIXTURE_EXIT=" + exit + " GAMESERVER_BOOT=0 LOGINSERVER_BOOT=0 COMBAT=0");
		System.exit(exit);
	}

	private static Admission admit(String[] args) throws Exception
	{
		check(args.length == 7 && "SETUP".equals(args[0]), "ADMISSION_ARGUMENTS");
		final Path game = physical(Path.of("").toAbsolutePath().normalize(), false);
		final Path runtime = game.getParent(), task = runtime.getParent();
		check(game.endsWith(Path.of(".phantom-local/logs/TASK131/native-runtime/game")), "OWNED_RUNTIME_ROOT");
		physical(game.resolve("log"), false);
		check(args[1].matches("n01-setup(?:-[a-z0-9]+)?"), "OWNED_SQL_LANE");
		final Path sql = physical(task.resolve("sql-lanes").resolve(args[1]).resolve("sql-task131"), false);
		final Path ini = physical(sql.resolve("Database.ini"), true);
		final Path jar = physical(runtime.resolve("libs/GameServer.jar"), true);
		check(physical(Path.of(Player.class.getProtectionDomain().getCodeSource().getLocation().toURI()), true).equals(jar), "GS_CODE_SOURCE");
		check(sha256(jar).equals(args[2]) && sha256(ini).equals(args[3]) && sha256(physical(sql.resolve("attestation.json"), true)).equals(args[4]), "ADMISSION_HASH");
		final long pid = Long.parseLong(args[5]);
		final var process = ProcessHandle.of(pid).filter(ProcessHandle::isAlive).orElseThrow(() -> failure("SQL_PROCESS_ABSENT"));
		check(process.info().command().orElse("").toLowerCase(Locale.ROOT).endsWith("\\mariadbd.exe"), "SQL_PROCESS_BINARY");
		final var deadline = Instant.parse(args[6]);
		final Instant now = Instant.now();
		check(now.isBefore(deadline) && deadline.isBefore(now.plusSeconds(900)), "SETUP_DEADLINE");
		final var settings = new Properties();
		try (var reader = Files.newBufferedReader(ini)) { settings.load(reader); }
		check(JDBC.equals(settings.getProperty("URL")) && LOGIN.equals(settings.getProperty("Login")) && "com.mysql.cj.jdbc.Driver".equals(settings.getProperty("Driver"))
			&& settings.getProperty("Password", "").matches("[0-9a-f]{64}") && "false".equals(settings.getProperty("BackupDatabase")), "OWNED_DATABASE_CONFIG");
		return new Admission(game, sql, ini, deadline);
	}

	private static void verifySession(Path sql) throws Exception
	{
		try (var connection = DatabaseFactory.getConnection(); var query = connection.createStatement();
			var rows = query.executeQuery("SELECT DATABASE(), CURRENT_USER(), @@port, @@datadir, @@bind_address"))
		{
			check(rows.next() && SCHEMA.equals(rows.getString(1)) && (LOGIN + "@127.0.0.1").equals(rows.getString(2)) && rows.getInt(3) == 33331
				&& physical(Path.of(rows.getString(4)), false).equals(physical(sql.resolve("data"), false)) && "127.0.0.1".equals(rows.getString(5)), "OWNED_JDBC_IDENTITY");
		}
		try (var connection = DatabaseFactory.getConnection(); var query = connection.createStatement();
			var rows = query.executeQuery("SELECT (SELECT COUNT(*) FROM accounts), (SELECT COUNT(*) FROM characters), (SELECT COUNT(*) FROM phantom_profiles), (SELECT COUNT(*) FROM phantom_ai_training_examples)"))
		{
			check(rows.next() && rows.getLong(1) == 0 && rows.getLong(2) == 0 && rows.getLong(3) == 0 && rows.getLong(4) == 0, "FRESH_SCHEMA_REQUIRED");
		}
	}

	private static Path physical(Path path, boolean file) throws IOException
	{
		final Path normalized = path.toAbsolutePath().normalize();
		for (Path part = normalized; part != null; part = part.getParent())
		{
			check(!Files.isSymbolicLink(part) && part.toRealPath().equals(part), "NONPHYSICAL_PATH");
		}
		check(file ? Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS) : Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS), "NONPHYSICAL_ENTRY");
		return normalized;
	}

	private static String sha256(Path file) throws Exception
	{
		final var digest = MessageDigest.getInstance("SHA-256");
		try (var in = Files.newInputStream(file); var stream = new java.security.DigestInputStream(in, digest)) { stream.transferTo(java.io.OutputStream.nullOutputStream()); }
		return HexFormat.of().withUpperCase().formatHex(digest.digest());
	}

	private static void prepare(Admission admission, PhantomBackgroundSuite.ProductionAuthorityFixture source) throws Exception
	{
		final Path attempt = admission.sql().resolve("n01-fixture.claim");
		Files.writeString(attempt, "SETUP " + Instant.now() + "\n", StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
		final var profiles = PhantomProfileRepository.open();
		final ZoneId zone = PhantomPlayersConfig.settings().populationTimeZone();
		final var catalog = PhantomPopulationCatalog.load(admission.game().resolve("data/phantoms/population/high-five-population-v3.xml"), zone);
		final var population = new PhantomPopulationStore(profiles, catalog, zone);
		final var planner = new PhantomHistoricalBackgroundPlanner(source.knowledge(), source.topology(), source.authority());
		final var goals = new PhantomGoalStateStore(profiles);
		final List<Actor> roster = new ArrayList<>();
		final Path receipt = admission.sql().resolve("n01-fixture.tsv");
		final Path scenes = admission.sql().resolve("n01-scenes.tsv");
		Files.writeString(receipt, HEADER + "\n", StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
		Files.writeString(scenes, SCENE_HEADER + "\n", StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
		long ordinal = 0;
		for (int level : List.of(52, 76))
		{
			for (Role role : Role.values())
			{
				check(Instant.now().isBefore(admission.deadline()), "SETUP_DEADLINE");
				final PlayerClass profession = minimumClass(role, level);
				final Birth birth = selectBirth(profiles, catalog, zone, ++ordinal, profession, level);
				var row = population.createShell(1, ordinal, birth.seed());
				check(row.profile().profileId() == birth.profile(), "EXCLUSIVE_PROFILE_SEQUENCE");
				for (int stage = 0; stage < 12 && row.state().state() != PhantomPopulationState.State.READY; stage++) { row = population.advanceCreation(row).snapshot(); }
				check(row.state().state() == PhantomPopulationState.State.READY, "PRODUCTION_CREATION_" + row.state().state());
				final Actor actor = prepareActor(row, role, profession, level, birth.seed(), profiles, source, planner, goals, roster);
				verifyStored(actor);
				roster.add(actor);
				Files.writeString(receipt, tsv(actor, role), StandardOpenOption.APPEND);
				if (actor.scene() != null) { Files.writeString(scenes, sceneTsv(actor), StandardOpenOption.APPEND); }
				System.out.println("TASK131_N01_SETUP profile=" + actor.profile() + " actor=" + actor.object() + " class=" + profession.getId() + " level=" + level + " qualification=" + (actor.scene() == null ? "BLOCKED_STOCK_OPENING" : "SCENE_CANDIDATE"));
			}
		}
		check(World.getInstance().getPlayers().isEmpty() && population.loadManagedAfter(0, 5).size() == 4, "ROSTER_AND_WORLD_CLOSURE");
		final long qualified = roster.stream().filter(actor -> actor.scene() != null).count();
		if (qualified == roster.size())
		{
			Files.writeString(admission.sql().resolve("n01-scene-scope.properties"), "stage=SETUP_CANDIDATES\nfixtureSha256=" + sha256(receipt)
				+ "\nsceneSha256=" + sha256(scenes) + "\nprofiles=" + String.join(",", roster.stream().map(actor -> Long.toString(actor.profile())).toList())
				+ "\nnextBootMustBindNativeTargets=true\n", StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
		}
		System.out.println("TASK131_N01_SETUP_STORED actors=4 sceneCandidates=" + qualified + " worldPlayers=0");
	}

	private static PlayerClass minimumClass(Role role, int level)
	{
		return Arrays.stream(PlayerClass.values()).filter(profession -> profession.level() == (level == 76 ? 3 : 2))
			.filter(profession -> profession.isMage() == role.magic && categoryBranch(profession, role.category))
			.filter(profession -> SkillTreeData.getInstance().getCompleteClassSkillTree(profession).values().stream().anyMatch(learn ->
				learn.getGetLevel() <= level && offensive(SkillData.getInstance().getSkill(learn.getSkillId(), learn.getSkillLevel()), role)))
			.min(Comparator.comparingInt(PlayerClass::getId)).orElseThrow(() -> failure("NO_ELIGIBLE_CLASS"));
	}

	private static boolean categoryBranch(PlayerClass profession, CategoryType category)
	{
		return CategoryData.getInstance().isInCategory(category, profession.getId())
			|| profession.getNextClasses().stream().anyMatch(next -> categoryBranch(next, category));
	}

	private static boolean offensive(Skill skill, Role role)
	{
		return skill != null && skill.isMagic() == role.magic && PhantomNativeCombatCapability.mechanics(skill) != null;
	}

	private static Birth selectBirth(PhantomProfileRepository profiles, PhantomPopulationCatalog catalog, ZoneId zone, long ordinal, PlayerClass desired, int level) throws Exception
	{
		final int root = desired.getRootClass().getId();
		final Instant now = Instant.now();
		for (int reserve = 0; reserve < 64; reserve++)
		{
			final long id = nextProfile();
			final boolean branchPossible = Arrays.stream(CareerArchetype.values()).anyMatch(archetype ->
				atLevel(PhantomCareerChoice.choose(id, root, false, archetype), level) == desired.getId());
			if (branchPossible)
			{
				for (long seed = 131; seed < 100_131; seed++)
				{
					final long identity = mix(seed, id);
					final CareerArchetype archetype = catalog.chooseArchetype(seed, ordinal);
					final var birth = catalog.chooseClass(identity, archetype);
					if (birth.classId() != root || atLevel(PhantomCareerChoice.choose(id, root, birth.sex().female(identity >>> 7), archetype), level) != desired.getId()) { continue; }
					final var schedule = PhantomPopulationStore.scheduleForShell(catalog, id, seed, null);
					final var window = catalog.evaluate(schedule.template().id(), now, zone, schedule.phaseMinutes());
					if (window.state().requiresMaterialization() && window.nextBoundary().isAfter(now.plusSeconds(1800))) { return new Birth(id, seed); }
				}
			}
			// Advance the allocator with an unlinked reservation. No account, player or grants are made for a rejected branch.
			final var reservation = profiles.create(null);
			check(reservation.profileId() == id, "EXCLUSIVE_RESERVATION_SEQUENCE");
			profiles.delete(id, reservation.rowVersion());
		}
		throw failure("LAWFUL_BIRTH_AND_CALENDAR_UNAVAILABLE");
	}

	private static int atLevel(PhantomCareerChoice choice, int level)
	{
		int current = choice.originClass();
		for (int next = choice.nextClass(current); next >= 0 && PhantomCareerChoice.minimumLevel(PlayerClass.getPlayerClass(next)) <= level; next = choice.nextClass(current)) { current = next; }
		return current;
	}

	private static long nextProfile() throws Exception
	{
		try (var connection = DatabaseFactory.getConnection(); var query = connection.createStatement();
			var rows = query.executeQuery("SELECT AUTO_INCREMENT FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='phantom_profiles'"))
		{
			check(rows.next(), "PROFILE_SEQUENCE");
			final long result = rows.getLong(1);
			check(result > 0, "PROFILE_SEQUENCE");
			return result;
		}
	}

	// Same native shell identity mixer used in the historical R43 pre-CUT setup.
	private static long mix(long seed, long profile)
	{
		long mixed = seed ^ (profile + 0x9E3779B97F4A7C15L);
		mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
		mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
		return mixed ^ (mixed >>> 31);
	}

	private static Actor prepareActor(PhantomPopulationStore.ManagedSnapshot row, Role role, PlayerClass profession, int level, long seed,
		PhantomProfileRepository profiles, PhantomBackgroundSuite.ProductionAuthorityFixture source, PhantomHistoricalBackgroundPlanner planner, PhantomGoalStateStore goals, List<Actor> roster)
	{
		final long id = row.profile().profileId();
		final int object = row.profile().characterObjectId();
		final Player actor;
		try (var suppression = PlayerAutoSaveTaskManager.suppressPopulationLoad(object)) { actor = Player.load(object); }
		check(actor != null, "STOCK_PLAYER_LOAD");
		try
		{
			check(actor.getObjectId() == object && actor.getAccountName().equals(row.state().reservedAccount()) && actor.getClassIndex() == 0, "OWNED_ACTOR_IDENTITY");
			actor.stopAllTasks();
			final var choice = PhantomCareerChoice.decode(profiles.findComponent(id, PhantomCareerChoice.COMPONENT).orElseThrow().payload());
			actor.getStat().setExp(actor.getStat().getExpForLevel(level));
			actor.getStat().setLevel((byte) level);
			for (int index = 0; actor.getActiveClass() != profession.getId() && index < 3; index++)
			{
				final int target = choice.nextClass(actor.getActiveClass());
				check(PhantomCareerChoice.legalEdge(actor.getActiveClass(), target, actor.getAppearance().isFemale(), actor.getClassIndex())
					&& PhantomCareerChoice.minimumLevel(PlayerClass.getPlayerClass(target)) <= level, "NATIVE_CAREER_EDGE");
				actor.setPlayerClass(target);
				actor.setBaseClass(target);
				actor.setLearningClass(actor.getPlayerClass());
			}
			check(actor.getActiveClass() == profession.getId() && actor.getLevel() == level, "NATIVE_CLASS_LEVEL");
			learn(actor);
			final int weapon = level == 52 ? role.level52Weapon : role.magic ? 6366 : 6580;
			for (int idToEquip : role.magic ? new int[] {weapon, 2406, 2415} : new int[] {weapon, 2381, 2417})
			{
				final var owned = actor.getInventory().getItemByItemId(idToEquip);
				actor.getInventory().equipItem(owned == null ? actor.addItem(ItemProcessType.REWARD, idToEquip, 1, actor, false) : owned);
			}
			actor.refreshExpertisePenalty();
			actor.refreshOverloaded();
			check(actor.getActiveWeaponItem().getId() == weapon && actor.getActiveWeaponItem().getCrystalType().getLevel() <= actor.getExpertiseLevel(), "NATIVE_EQUIPMENT_GRADE");
			final int shot = shotItem(actor, role);
			final long existing = actor.getInventory().getInventoryItemCount(shot, -1);
			check(existing <= SHOTS && actor.getAdena() <= ADENA, "SETUP_BUDGET");
			if (existing < SHOTS) { actor.addItem(ItemProcessType.REWARD, shot, SHOTS - existing, actor, false); }
			if (actor.getAdena() < ADENA) { actor.addAdena(ItemProcessType.REWARD, ADENA - actor.getAdena(), actor, false); }
			actor.setCurrentHp(actor.getMaxHp());
			actor.setCurrentMp(actor.getMaxMp());
			actor.setCurrentCp(actor.getMaxCp());
			actor.setRunning();
			actor.standUp();
			final var origin = actor.getLocation();
			final Scene scene = scene(id, actor, role, seed, source, planner, roster);
			if (scene == null) { actor.setXYZInvisible(origin.getX(), origin.getY(), origin.getZ()); }
			final var plan = scene == null ? planner.idleInitial(id, actor, seed, 1) : scene.plan();
			check(plan.ready(), "PRODUCTION_BASELINE_PLAN");
			goals.insert(id, plan.goal());
			actor.getInventory().updateDatabase();
			actor.storeMe();
			final var capture = source.authority().captureOwnedNative(id, actor, plan.goal(), null);
			check(new PhantomBackgroundTransaction().captureBaseline(capture.state(), plan.goal(), capture.context()).status() == PhantomBackgroundTransaction.Status.SUCCESS, "PRODUCTION_BASELINE_CAPTURE");
			final Skill skill = scene == null ? minimumSkill(actor, role, null) : scene.skill();
			check(skill != null, "NATIVE_LEARNED_OFFENSIVE_SKILL");
			return new Actor(id, object, actor.getAccountName(), profession, level, skill, weapon, shot, actor.getInventory().getInventoryItemCount(shot, -1), actor.getAdena(), actor.getExp(), actor.getSp(), scene);
		}
		finally
		{
			actor.stopAllTasks();
			actor.storeMe();
			actor.setOnlineStatus(true, false);
			actor.deleteMe();
		}
	}

	private static void learn(Player actor)
	{
		int passes = 0;
		final var tree = SkillTreeData.getInstance();
		while (!tree.getAvailableAutoGetSkills(actor).isEmpty() && passes++ < 64) { actor.giveAvailableAutoGetSkills(); }
		check(tree.getAvailableAutoGetSkills(actor).isEmpty(), "NATIVE_AUTOGET_FIXED_POINT");
		long previous = -1;
		for (int pass = 0; pass < 64; pass++)
		{
			actor.giveAvailableSkills(false, true, false);
			actor.rewardSkills();
			final long levels = actor.getAllSkills().stream().mapToLong(Skill::getLevel).sum();
			if (levels == previous) { return; }
			previous = levels;
		}
		throw failure("NATIVE_LEARNING_FIXED_POINT");
	}

	private static int shotItem(Player actor, Role role)
	{
		final var weapon = actor.getActiveWeaponItem();
		final var action = role.magic ? ActionType.SPIRITSHOT : ActionType.SOULSHOT;
		return Arrays.stream(ItemData.getInstance().getAllItems()).filter(EtcItem.class::isInstance).map(EtcItem.class::cast)
			.filter(item -> item.isStackable() && !item.isBlessed() && item.getDefaultAction() == action && item.getCrystalType() == weapon.getCrystalTypePlus())
			.filter(item -> ItemHandler.getInstance().getHandler(item) != null).mapToInt(ItemTemplate::getId).min().orElseThrow(() -> failure("NATIVE_SHOT_ITEM"));
	}

	private static Skill minimumSkill(Player actor, Role role, Monster target)
	{
		final var tree = SkillTreeData.getInstance();
		return actor.getAllSkills().stream().filter(skill -> offensive(skill, role))
			.filter(skill -> { final var learned = tree.getClassSkill(skill.getId(), skill.getLevel(), actor.getPlayerClass()); return learned != null && learned.getGetLevel() <= actor.getLevel(); })
			.filter(skill -> skill.getHpConsume() == 0 && skill.getItemConsumeCount() == 0 && skill.getChargeConsumeCount() == 0 && skill.getMaxSoulConsumeCount() == 0
				&& PhantomNativeCombatCapability.magicLevelReady(PhantomNativeCombatCapability.mechanics(skill), actor.getLevel(), target == null ? 0 : target.getLevel())
				&& skill.checkCondition(actor, target, false) && (target == null || skill.checkCondition(actor, target, true)))
			.min(Comparator.comparingInt(Skill::getId)).orElse(null);
	}

	private static Scene scene(long profile, Player actor, Role role, long seed, PhantomBackgroundSuite.ProductionAuthorityFixture source,
		PhantomHistoricalBackgroundPlanner planner, List<Actor> roster)
	{
		final var geo = GeoEngine.getInstance();
		final List<Monster> mobs = World.getInstance().getVisibleObjects().stream().filter(Monster.class::isInstance).map(Monster.class::cast)
			.filter(target -> ordinary(target, actor.getLevel()) && geo.hasGeo(target.getX(), target.getY())).toList();
		final var anchors = source.topology().snapshot().anchors().stream().filter(anchor -> anchor.role() == PhantomTopologyAnchorRole.FARMING)
			.sorted(Comparator.comparing(anchor -> anchor.id())).toList();
		int nearbyAnchors = 0, legacyMultiplicity = 0, sameAnchorPlans = 0, matching = 0, geometry = 0, quiet = 0;
		final var rejections = new java.util.TreeMap<String, Integer>();
		for (var anchor : anchors)
		{
			final var position = L2jPhantomBackgroundAuthority.canonicalCommittedAnchorPosition(anchor, 0).orElse(null);
			if (position == null || !geo.hasGeo(position.x(), position.y())) { continue; }
			final var point = new PhantomTopologyPoint(position.x(), position.y(), position.z(), 0);
			if (roster.stream().anyMatch(entry -> entry.scene() != null && entry.scene().point().distance3D(point) <= 2 * ANCHOR_RADIUS)) { continue; }
			final var nearby = mobs.stream().filter(mob -> distance(point, mob) <= ANCHOR_RADIUS && Math.abs((long) point.z() - mob.getZ()) <= 150)
				.sorted(Comparator.comparingDouble((Monster mob) -> distance(point, mob)).thenComparingInt(Monster::getId)
					.thenComparing(PhantomCombat131Fixture::spawnKey).thenComparingInt(Monster::getObjectId)).toList();
			if (nearby.isEmpty()) { continue; }
			nearbyAnchors++;
			if (nearby.size() >= 4) { legacyMultiplicity++; }
			actor.setXYZInvisible(point.x(), point.y(), point.z());
			var plan = planner.planInitial(profile, actor, seed, 1);
			if (!plan.ready() || !anchor.id().equals(plan.spec().anchorId()))
			{
				rejections.merge(plan.ready() ? "planner.selected_other_anchor" : plan.reasonKey(), 1, Integer::sum);
				final var admissibleNpcIds = nearby.stream().filter(mob -> passable(point, mob, geo) && quiet(point, mob) && minimumSkill(actor, role, mob) != null)
					.map(Monster::getId).collect(Collectors.toUnmodifiableSet());
				if (admissibleNpcIds.isEmpty()) { rejections.merge("local.no_safe_learned_target", 1, Integer::sum); continue; }
				final var idle = planner.idleInitial(profile, actor, seed, 1);
				if (!idle.ready()) { rejections.merge(idle.reasonKey(), 1, Integer::sum); continue; }
				final var state = source.authority().captureOwnedNative(profile, actor, idle.goal(), null).state();
				plan = planner.replanVisibleLocal(profile, state, idle.goal(), seed, 2, point, Set.of(), Set.of(), candidate ->
				{
					final var spec = PhantomBackgroundGoalSpec.parse(candidate);
					return anchor.id().equals(spec.anchorId()) && admissibleNpcIds.contains(spec.npcId()) && planner.isVisibleLocal(candidate, point);
				});
				if (!plan.ready()) { rejections.merge("local." + plan.reasonKey(), 1, Integer::sum); continue; }
				System.out.println("TASK131_N01_LOCAL_PLAN profile=" + profile + " anchor=" + anchor.id() + " npc=" + plan.spec().npcId());
			}
			sameAnchorPlans++;
			final int npcId = plan.spec().npcId();
			final var stock = nearby.stream().filter(mob -> mob.getId() == npcId).toList();
			matching += stock.size();
			final var passable = stock.stream().filter(mob -> passable(point, mob, geo)).toList();
			geometry += passable.size();
			final var safe = passable.stream().filter(mob -> quiet(point, mob)).toList();
			quiet += safe.size();
			final var openings = safe.stream().filter(mob -> minimumSkill(actor, role, mob) != null).toList();
			// The stock timer can yield another qualified generation of the same spawn on a later cycle.
			if (!openings.isEmpty())
			{
				final Monster target = openings.getFirst();
				final var observed = target.getNativeEvidenceTarget();
				check(observed.spawnGeneration() > 0 && target.getSpawn().isRespawnEnabled(), "NATIVE_RESPAWN_IDENTITY");
				System.out.println("TASK131_N01_STOCK_OPENING profile=" + profile + " anchor=" + anchor.id() + " npc=" + target.getId() + " candidates=" + openings.size() + " nearby=" + nearby.size());
				return new Scene(anchor.id(), point, target.getId(), target.getLevel(), spawnKey(target), target.getSpawn().getRespawnMinDelay(), target.getSpawn().getRespawnMaxDelay(),
					observed, minimumSkill(actor, role, target), plan, openings.size());
			}
		}
		System.out.println("TASK131_N01_STOCK_OPENING_UNPROVED profile=" + profile + " level=" + actor.getLevel() + " ordinaryMobs=" + mobs.size() + " candidateAnchors=" + anchors.size()
			+ " nearbyAnchors=" + nearbyAnchors + " legacyMultiplicity=" + legacyMultiplicity + " sameAnchorPlans=" + sameAnchorPlans + " matching=" + matching
			+ " geometry=" + geometry + " quiet=" + quiet + " plannerRejections=" + rejections);
		return null;
	}

	private static boolean ordinary(Monster target, int level)
	{
		final var clans = target.getTemplate().getClans();
		final var drops = target.getTemplate().getDropList();
		final var dropGroups = target.getTemplate().getDropGroups();
		return target.isSpawned() && !target.isAlikeDead() && target.getInstanceId() == 0 && !target.isChampion() && !target.isAggressive() && !target.isRaid()
			&& !target.isQuestMonster() && !target.getTemplate().isUndying() && target.isTargetable() && !target.isInvul()
			&& (clans == null || clans.isEmpty()) && !target.hasMinions() && target.getLeader() == null && target.getSpawn() != null
			&& target.getSpawn().isRespawnEnabled() && target.getSpawn().getRespawnMinDelay() > 0
			&& Math.abs(level - target.getLevel()) <= 4 && PhantomCombatPolicy.farmRewardAvailable(level - target.getLevel(), RatesConfig.MONSTER_EXP_MAX_LEVEL_DIFFERENCE,
				target.getExpReward(level), target.getSpReward(level)) && ((drops != null && !drops.isEmpty()) || (dropGroups != null && !dropGroups.isEmpty()));
	}

	private static boolean passable(PhantomTopologyPoint point, Monster mob, GeoEngine geo)
	{
		return geo.canSeeTarget(point.x(), point.y(), point.z(), mob.getX(), mob.getY(), mob.getZ(), 0)
			&& geo.canMoveToTarget(point.x(), point.y(), point.z(), mob.getX(), mob.getY(), mob.getZ(), 0)
			&& geo.canMoveToTarget(mob.getX(), mob.getY(), mob.getZ(), point.x(), point.y(), point.z(), 0);
	}

	private static boolean quiet(PhantomTopologyPoint point, Monster target)
	{
		return World.getInstance().getVisibleObjectsInRange(target, Monster.class, 2500).stream().noneMatch(mob ->
			mob.isSpawned() && !mob.isAlikeDead() && mob.getInstanceId() == 0 && mob.isAggressive()
				&& (Math.abs((long) mob.getZ() - target.getZ()) <= 256 && mob.calculateDistance2D(target) <= mob.getAggroRange() + NpcConfig.MAX_DRIFT_RANGE
					|| Math.abs((long) mob.getZ() - point.z()) <= 256 && distance(point, mob) <= mob.getAggroRange() + NpcConfig.MAX_DRIFT_RANGE));
	}

	private static double distance(PhantomTopologyPoint point, Monster mob)
	{
		return Math.hypot((long) point.x() - mob.getX(), (long) point.y() - mob.getY());
	}

	private static String spawnKey(Monster mob)
	{
		final var spawn = mob.getSpawn();
		final String name = Objects.toString(spawn.getName(), "");
		final var territory = spawn.getSpawnTerritory();
		if (territory != null)
		{
			return "territory:" + territory.getName() + ":" + territory.geometrySnapshot().map(value -> value.hash()).orElse("NO_GEOMETRY_HASH") + ":" + name;
		}
		final var location = spawn.getSpawnLocation();
		return "fixed:" + name + ":" + location.getX() + ":" + location.getY() + ":" + location.getZ();
	}

	private static void verifyStored(Actor actor) throws Exception
	{
		check(World.getInstance().findObject(actor.object()) == null, "DETACHED_ACTOR");
		try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement(
			"SELECT c.account_name,c.classid,c.base_class,c.level,c.online,c.exp,c.sp,s.skill_level,c.x,c.y,c.z FROM characters c JOIN accounts a ON a.login=c.account_name JOIN character_skills s ON s.charId=c.charId AND s.class_index=0 AND s.skill_id=? WHERE c.charId=?"))
		{
			query.setInt(1, actor.skill().getId());
			query.setInt(2, actor.object());
			try (var rows = query.executeQuery())
			{
				check(rows.next() && actor.account().equals(rows.getString(1)) && actor.profession().getId() == rows.getInt(2) && actor.profession().getId() == rows.getInt(3)
					&& actor.level() == rows.getInt(4) && rows.getInt(5) == 0 && actor.exp() == rows.getLong(6) && actor.sp() == rows.getLong(7)
					&& rows.getInt(8) == actor.skill().getLevel(), "DURABLE_CLASS_SKILL_AND_OFF");
				if (actor.scene() != null)
				{
					final var point = actor.scene().point();
					check(rows.getInt(9) == point.x() && rows.getInt(10) == point.y() && rows.getInt(11) == point.z(), "DURABLE_SCENE_PLACEMENT");
				}
				check(!rows.next(), "DURABLE_ACTOR_SINGLE_ROW");
			}
		}
		try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement(
			"SELECT COUNT(CASE WHEN item_id=? AND loc='PAPERDOLL' THEN 1 END),SUM(CASE WHEN item_id=? THEN count ELSE 0 END),SUM(CASE WHEN item_id=57 THEN count ELSE 0 END) FROM items WHERE owner_id=?"))
		{
			query.setInt(1, actor.weapon());
			query.setInt(2, actor.shot());
			query.setInt(3, actor.object());
			try (var rows = query.executeQuery())
			{
				check(rows.next() && rows.getLong(1) == 1 && rows.getLong(2) == actor.shots() && rows.getLong(3) == actor.adena(), "DURABLE_STOCK_BUDGET");
			}
		}
	}

	private static String tsv(Actor actor, Role role)
	{
		final Scene scene = actor.scene();
		final var fields = List.of(scene == null ? "SETUP_BLOCKED_STOCK_OPENING" : "SETUP_STOCK_CANDIDATE", actor.profile(), actor.object(), actor.account(), role, actor.profession().getId(), actor.level(),
			actor.skill().getId(), actor.skill().getLevel(), actor.weapon(), actor.shot(), actor.shots(), actor.adena(), scene == null ? "" : scene.anchor(),
			scene == null ? "" : scene.npcId(), scene == null ? "" : scene.spawn(), scene == null ? "" : scene.preflightTarget().objectId(),
			scene == null ? "" : scene.point().x(), scene == null ? "" : scene.point().y(), scene == null ? "" : scene.point().z(), actor.exp(), actor.sp());
		return String.join("\t", fields.stream().map(Object::toString).toList()) + "\n";
	}

	private static String sceneTsv(Actor actor)
	{
		final Scene scene = actor.scene();
		final var hashes = scene.plan().generation().authorityHashes();
		final var observed = scene.preflightTarget();
		final var fields = List.of(actor.profile(), actor.object(), scene.plan().goal().goalId(), scene.plan().goal().revision(), scene.plan().planIdentity(),
			scene.anchor(), scene.point().x(), scene.point().y(), scene.point().z(), scene.npcId(), scene.npcLevel(), scene.spawn(), scene.respawnMin(), scene.respawnMax(),
			observed.objectId(), observed.instanceId(), observed.spawnGeneration(), scene.qualifyingTargets(), hashes.knowledge(), hashes.topology(), hashes.progression(), hashes.commerce());
		return String.join("\t", fields.stream().map(Object::toString).toList()) + "\n";
	}

	private static void check(boolean condition, String reason)
	{
		if (!condition) { throw failure(reason); }
	}

	private static IllegalStateException failure(String reason)
	{
		return new IllegalStateException("TASK131_N01_" + reason);
	}
}
