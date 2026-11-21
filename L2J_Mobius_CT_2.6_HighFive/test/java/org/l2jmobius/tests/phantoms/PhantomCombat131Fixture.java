/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
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
import org.l2jmobius.gameserver.managers.ZoneManager;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.effects.EffectType;
import org.l2jmobius.gameserver.model.item.EtcItem;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.type.ActionType;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.zone.type.ArenaZone;
import org.l2jmobius.gameserver.model.zone.type.NoPvPZone;
import org.l2jmobius.gameserver.model.zone.type.PeaceZone;
import org.l2jmobius.gameserver.model.zone.type.WaterZone;
import org.l2jmobius.gameserver.phantoms.background.L2jPhantomBackgroundAuthority;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundPlanner;
import org.l2jmobius.gameserver.phantoms.combat.L2jCombatBackend;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatMode;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatPolicy;
import org.l2jmobius.gameserver.phantoms.combat.PhantomNativeCombatCapability;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3;
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
	static final String OFFENSE_HEADER = "profileId\tobjectId\tclass\tlevel\tskillId\tskillLevel\tprovenance\tfixtureSha\tsceneScopeSha";
	private static final String DUEL_ARENA = "gludin_pvp";
	private static final String DUEL_RECEIPT = "n06-duel-fixture.tsv";
	private static final int DUEL_RECEIPT_LIMIT = 64 * 1024;
	private static final int DUEL_LEVEL = 76;
	private static final int DUEL_WEST_X = -88000;
	private static final int DUEL_EAST_X = -87840;
	private static final int DUEL_Y = 142220;
	private static final String DUEL_PROOF_HEADER = "status\tarm\tside\topeningSide\tprofileId\tobjectId\taccount\trole\tclass\tlevel\tweapon\tshotItem\tshots\tadena\tarena\tinstanceId\tstartX\tstartY\tstartZ\tstoredX\tstoredY\tstoredZ\tlearnedPvpRanks\tcontrolRanks";

	private record Admission(Path game, Path sql, Path ini, Instant deadline, boolean duel) { }
	private record Birth(long profile, long seed) { }
	private record Scene(String anchor, PhantomTopologyPoint point, int npcId, int npcLevel, String spawn, int respawnMin, int respawnMax,
		PlayerNativeEvidence.Target preflightTarget, Skill skill, PhantomHistoricalBackgroundPlanner.Result plan, int qualifyingTargets) { }
	private record Actor(long profile, int object, String account, PlayerClass profession, int level, Skill skill, int weapon, int shot,
		long shots, long adena, long exp, long sp, Scene scene, PhantomTopologyPoint storedPoint, List<Skill> learnedOffense) { }
	static record DuelIdentity(int arm, String side, long profile, int object, String account, String role, int profession, List<SelectedSkill> skills)
	{
		DuelIdentity { skills = List.copyOf(skills); }
	}
	static record DuelSlot(DuelIdentity identity, String openingSide, int level, int weapon, int shotItem, long shots, long adena,
		PhantomTopologyPoint start, PhantomTopologyPoint stored, List<SelectedSkill> controlRanks)
	{
		DuelSlot { controlRanks = List.copyOf(controlRanks); }
	}
	static final class DuelFixture
	{
		private final String _sha256;
		private final List<DuelSlot> _slots;

		private DuelFixture(String sha256, List<DuelSlot> slots)
		{
			_sha256 = sha256;
			_slots = List.copyOf(slots);
		}

		String sha256() { return _sha256; }
		List<DuelSlot> slots() { return _slots; }
		List<DuelSlot> arm(int arm)
		{
			check(arm == 1 || arm == 2, "DUEL_ARM_NUMBER");
			return _slots.subList((arm - 1) * 2, arm * 2);
		}
	}

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
		final String scope = args.length > 0 && args[0].equals("SETUP_DUEL") ? "N06" : "N01";
		try
		{
			final Admission admission = admit(args);
			ConfigLoader.init();
			check(!PhantomPlayersConfig.isEnabled(), "MASTER_ENABLED");
			DatabaseFactory.initFromConfig(admission.ini().toString());
			verifySession(admission.sql());
			if (admission.duel())
			{
				check(Instant.now().isBefore(admission.deadline()), "DUEL_SETUP_DEADLINE");
				Files.writeString(admission.sql().resolve("n06-duel-fixture.claim"), "SETUP_DUEL " + Instant.now() + "\n",
					StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
			}
			PhantomHeadlessPlayerTestEnvironment.initializeOwnedPlayerInfrastructure();
			InitialEquipmentData.getInstance();
			InitialShortcutData.getInstance();
			ScriptEngine.getInstance().executeScript(ScriptEngine.MASTER_HANDLER_FILE);
			try (var source = PhantomBackgroundSuite.ProductionAuthorityFixture.start(admission.game().resolve("data/phantoms/topology"), admission.game()))
			{
				if (admission.duel()) { prepareDuel(admission, source); }
				else { prepare(admission, source); }
			}
			exit = 0;
		}
		catch (Throwable failure)
		{
			System.err.println("TASK131_" + scope + "_FIXTURE_FAILED " + failure.getClass().getSimpleName());
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
		System.out.println("TASK131_" + scope + "_FIXTURE_EXIT=" + exit + " GAMESERVER_BOOT=0 LOGINSERVER_BOOT=0 COMBAT=0");
		System.exit(exit);
	}

	private static Admission admit(String[] args) throws Exception
	{
		check(args.length == 7 && ("SETUP".equals(args[0]) || "SETUP_DUEL".equals(args[0])), "ADMISSION_ARGUMENTS");
		final boolean duel = "SETUP_DUEL".equals(args[0]);
		final Path game = physical(Path.of("").toAbsolutePath().normalize(), false);
		final Path runtime = game.getParent(), task = runtime.getParent();
		check(duel ? task.endsWith(Path.of(".phantom-local/logs/TASK131")) && "game".equals(game.getFileName().toString())
			&& runtime.getFileName().toString().matches("native-runtime-r[1-9][0-9]*") : game.endsWith(Path.of(".phantom-local/logs/TASK131/native-runtime/game")), "OWNED_RUNTIME_ROOT");
		physical(game.resolve("log"), false);
		check(args[1].matches(duel ? "n06-setup-r[1-9][0-9]*" : "n01-setup(?:-[a-z0-9]+)?"), "OWNED_SQL_LANE");
		final Path sql = physical(task.resolve("sql-lanes").resolve(args[1]).resolve("sql-task131"), false);
		final Path ini = physical(sql.resolve("Database.ini"), true);
		final Path jar = physical(runtime.resolve("libs/GameServer.jar"), true);
		check(physical(Path.of(Player.class.getProtectionDomain().getCodeSource().getLocation().toURI()), true).equals(jar), "GS_CODE_SOURCE");
		check(sha256(jar).equals(args[2]) && sha256(ini).equals(args[3]) && sha256(physical(sql.resolve("attestation.json"), true)).equals(args[4]), "ADMISSION_HASH");
		final long pid = Long.parseLong(args[5]);
		final var process = ProcessHandle.of(pid).filter(ProcessHandle::isAlive).orElseThrow(() -> failure("SQL_PROCESS_ABSENT"));
		check(process.info().command().orElse("").toLowerCase(Locale.ROOT).endsWith("\\mariadbd.exe"), "SQL_PROCESS_BINARY");
		if (duel)
		{
			final String ownedConfig = "--defaults-file=" + physical(sql.resolve("owned-server.ini"), true);
			check(Arrays.equals(process.info().arguments().orElse(new String[0]), new String[] {ownedConfig, "--console"}), "DUEL_OWNED_SQL_PROCESS");
		}
		final var deadline = Instant.parse(args[6]);
		final Instant now = Instant.now();
		check(now.isBefore(deadline) && deadline.isBefore(now.plusSeconds(900)), "SETUP_DEADLINE");
		final var settings = new Properties();
		try (var reader = Files.newBufferedReader(ini)) { settings.load(reader); }
		check(JDBC.equals(settings.getProperty("URL")) && LOGIN.equals(settings.getProperty("Login")) && "com.mysql.cj.jdbc.Driver".equals(settings.getProperty("Driver"))
			&& settings.getProperty("Password", "").matches("[0-9a-f]{64}") && "false".equals(settings.getProperty("BackupDatabase")), "OWNED_DATABASE_CONFIG");
		return new Admission(game, sql, ini, deadline, duel);
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

	static Path physical(Path path, boolean file) throws IOException
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
				final Actor actor = prepareActor(row, role, profession, level, birth.seed(), profiles, source, planner, goals, roster, false);
				verifyStored(actor, false);
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
			final Path scope = admission.sql().resolve("n01-scene-scope.properties");
			final String fixtureSha = sha256(receipt);
			Files.writeString(scope, "stage=SETUP_CANDIDATES\nfixtureSha256=" + fixtureSha
				+ "\nsceneSha256=" + sha256(scenes) + "\nprofiles=" + String.join(",", roster.stream().map(actor -> Long.toString(actor.profile())).toList())
				+ "\nnextBootMustBindNativeTargets=true\n", StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
			final String scopeSha = sha256(scope);
			final var known = new StringBuilder(OFFENSE_HEADER).append('\n');
			for (var actor : roster)
			{
				for (Skill skill : actor.learnedOffense())
				{
					final var fields = List.of(actor.profile(), actor.object(), actor.profession().getId(), actor.level(), skill.getId(), skill.getLevel(),
						"STORED_CLASS_DAMAGE", fixtureSha.toLowerCase(Locale.ROOT), scopeSha.toLowerCase(Locale.ROOT));
					known.append(String.join("\t", fields.stream().map(Object::toString).toList())).append('\n');
				}
			}
			Files.writeString(admission.sql().resolve("n01-learned-offense.tsv"), known.toString(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
		}
		System.out.println("TASK131_N01_SETUP_STORED actors=4 sceneCandidates=" + qualified + " worldPlayers=0");
	}

	private static void prepareDuel(Admission admission, PhantomBackgroundSuite.ProductionAuthorityFixture source) throws Exception
	{
		final var starts = duelArena();
		check(World.getInstance().getPlayers().isEmpty(), "DUEL_WORLD_NOT_EMPTY");
		final var profiles = PhantomProfileRepository.open();
		final ZoneId zone = PhantomPlayersConfig.settings().populationTimeZone();
		final var catalog = PhantomPopulationCatalog.load(admission.game().resolve("data/phantoms/population/high-five-population-v3.xml"), zone);
		final var population = new PhantomPopulationStore(profiles, catalog, zone);
		final var planner = new PhantomHistoricalBackgroundPlanner(source.knowledge(), source.topology(), source.authority());
		final var goals = new PhantomGoalStateStore(profiles);
		final var identities = new ArrayList<DuelIdentity>(4);
		final var proof = new StringBuilder(DUEL_PROOF_HEADER).append('\n');
		final int level = DUEL_LEVEL;
		boolean controlAvailable = false;
		int ordinal = 0;
		for (int arm = 1; arm <= 2; arm++)
		{
			for (int position = 0; position < starts.size(); position++)
			{
				check(Instant.now().isBefore(admission.deadline()) && !PhantomPlayersConfig.isEnabled(), "DUEL_SETUP_FENCE");
				final Role role = (arm == 1) == (position == 0) ? Role.MAGE : Role.PHYSICAL;
				final PlayerClass profession = minimumClass(role, level);
				final Birth birth = selectBirth(profiles, catalog, zone, ++ordinal, profession, level);
				var row = population.createShell(1, ordinal, birth.seed());
				check(row.profile().profileId() == birth.profile(), "EXCLUSIVE_PROFILE_SEQUENCE");
				for (int stage = 0; stage < 12 && row.state().state() != PhantomPopulationState.State.READY; stage++) { row = population.advanceCreation(row).snapshot(); }
				check(row.state().state() == PhantomPopulationState.State.READY, "PRODUCTION_CREATION_" + row.state().state());
				final Actor actor = prepareActor(row, role, profession, level, birth.seed(), profiles, source, planner, goals, List.of(), true);
				verifyStored(actor, true);
				final String side = position == 0 ? "A" : "B";
				final var skills = actor.learnedOffense().stream().map(skill -> new SelectedSkill(skill.getId(), skill.getLevel())).toList();
				controlAvailable |= actor.learnedOffense().stream().anyMatch(PhantomCombat131Fixture::duelControl);
				final var identity = new DuelIdentity(arm, side, actor.profile(), actor.object(), actor.account(), role.name(), profession.getId(), skills);
				identities.add(identity);
				proof.append(duelTsv(identity, actor, starts.get(position)));
			}
		}
		validateDuelRoster(identities);
		check(controlAvailable, "DUEL_LEARNED_CONTROL_ABSENT");
		verifyDuelAccounts(identities);
		check(Instant.now().isBefore(admission.deadline()) && !PhantomPlayersConfig.isEnabled() && World.getInstance().getPlayers().isEmpty()
			&& population.loadManagedAfter(0, 5).size() == identities.size(), "DUEL_ROSTER_AND_WORLD_CLOSURE");
		Files.writeString(admission.sql().resolve(DUEL_RECEIPT), proof, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
		System.out.println("TASK131_N06_SETUP_STORED actors=4 mirroredArms=2 worldPlayers=0");
	}

	private static void verifyDuelAccounts(List<DuelIdentity> roster) throws Exception
	{
		final var unverified = roster.stream().collect(Collectors.toMap(DuelIdentity::account, DuelIdentity::object));
		final String sql = "SELECT a.login,c.charId,c.online FROM accounts a LEFT JOIN characters c ON c.account_name=a.login";
		try (var connection = DatabaseFactory.getConnection(); var query = connection.createStatement(); var rows = query.executeQuery(sql))
		{
			while (rows.next())
			{
				final String account = rows.getString(1);
				check(Objects.equals(unverified.remove(account), rows.getInt(2)) && rows.getInt(3) == 0, "DUEL_ACCOUNT_OR_OBJECT_OUTSIDE_ROSTER");
			}
		}
		check(unverified.isEmpty(), "DUEL_ACCOUNT_ROSTER_INCOMPLETE");
	}

	static void validateDuelRoster(List<DuelIdentity> roster)
	{
		check(roster.size() == 4, "DUEL_ROSTER_SIZE");
		final var profiles = new java.util.HashSet<Long>();
		final var objects = new java.util.HashSet<Integer>();
		final var accounts = new java.util.HashSet<String>();
		for (int index = 0; index < roster.size(); index++)
		{
			final var identity = roster.get(index);
			final int arm = index / 2 + 1;
			final boolean west = index % 2 == 0;
			final String role = ((arm == 1) == west ? Role.MAGE : Role.PHYSICAL).name();
			check(identity.arm() == arm && identity.side().equals(west ? "A" : "B") && identity.role().equals(role)
				&& identity.profile() > 0 && profiles.add(identity.profile()) && identity.object() > 0 && objects.add(identity.object())
				&& identity.account().matches("[a-zA-Z0-9_-]{1,45}") && accounts.add(identity.account().toLowerCase(Locale.ROOT))
				&& identity.skills().size() >= 2, "DUEL_ROSTER_IDENTITY");
			final var mirrored = roster.get(roster.size() - 1 - index);
			check(identity.profession() >= 0 && identity.profession() == mirrored.profession() && identity.skills().equals(mirrored.skills()), "DUEL_MIRROR_CLASS_AND_RANKS");
		}
		check(roster.get(0).profession() != roster.get(1).profession(), "DUEL_DISTINCT_CLASSES");
	}

	private static List<PhantomTopologyPoint> duelArena()
	{
		final var geo = GeoEngine.getInstance();
		final var west = duelPoint(geo, DUEL_WEST_X);
		final var east = duelPoint(geo, DUEL_EAST_X);
		check(Math.abs(west.z() - east.z()) <= 32 && geo.canSeeTarget(west.x(), west.y(), west.z(), east.x(), east.y(), east.z(), 0)
			&& geo.canSeeTarget(east.x(), east.y(), east.z(), west.x(), west.y(), west.z(), 0)
			&& geo.canMoveToTarget(west.x(), west.y(), west.z(), east.x(), east.y(), east.z(), 0)
			&& geo.canMoveToTarget(east.x(), east.y(), east.z(), west.x(), west.y(), west.z(), 0), "DUEL_ARENA_TRAVERSAL");
		check(World.getInstance().getVisibleObjects().stream().filter(Monster.class::isInstance).map(Monster.class::cast).noneMatch(mob ->
			mob.getInstanceId() == 0 && mob.isSpawned() && !mob.isAlikeDead() && mob.isAggressive()
				&& (Math.abs((long) mob.getZ() - west.z()) <= 256 || Math.abs((long) mob.getZ() - east.z()) <= 256)
				&& Math.min(distance(west, mob), distance(east, mob)) <= mob.getAggroRange() + NpcConfig.MAX_DRIFT_RANGE), "DUEL_ARENA_FOREIGN_AGGRO");
		return List.of(west, east);
	}

	private static PhantomTopologyPoint duelPoint(GeoEngine geo, int x)
	{
		final int y = DUEL_Y;
		check(geo.hasGeo(x, y), "DUEL_ARENA_GEODATA");
		final int z = geo.getHeight(x, y, -3500);
		final var zones = ZoneManager.getInstance();
		final var arena = zones.getZone(x, y, z, ArenaZone.class);
		check(arena != null && DUEL_ARENA.equals(arena.getName()) && zones.getZone(x, y, z, PeaceZone.class) == null
			&& zones.getZone(x, y, z, NoPvPZone.class) == null && zones.getZone(x, y, z, WaterZone.class) == null, "DUEL_ARENA_ZONE");
		return new PhantomTopologyPoint(x, y, z, 0);
	}

	private static String duelTsv(DuelIdentity identity, Actor actor, PhantomTopologyPoint start)
	{
		final var stored = actor.storedPoint();
		final String ranks = identity.skills().stream().map(skill -> skill.skillId() + ":" + skill.skillLevel()).collect(Collectors.joining(","));
		final String controls = actor.learnedOffense().stream().filter(PhantomCombat131Fixture::duelControl)
			.map(skill -> skill.getId() + ":" + skill.getLevel()).collect(Collectors.joining(","));
		final var fields = List.of("SETUP_DUEL_STORED", identity.arm(), identity.side(), "A", identity.profile(), identity.object(), identity.account(),
			identity.role(), identity.profession(), actor.level(), actor.weapon(), actor.shot(), actor.shots(), actor.adena(), DUEL_ARENA, start.instanceId(),
			start.x(), start.y(), start.z(), stored.x(), stored.y(), stored.z(), ranks, controls);
		return String.join("\t", fields.stream().map(Object::toString).toList()) + "\n";
	}

	static DuelFixture readDuelFixture(Path receipt, String expectedSha) throws IOException
	{
		final Path file = physical(receipt, true);
		check(file.getFileName().toString().equals(DUEL_RECEIPT) && expectedSha.matches("(?i)[0-9a-f]{64}"), "DUEL_RECEIPT_SCOPE");
		final byte[] bytes;
		try (var stream = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) { bytes = stream.readNBytes(DUEL_RECEIPT_LIMIT + 1); }
		check(bytes.length <= DUEL_RECEIPT_LIMIT, "DUEL_RECEIPT_BOUND");
		final String sha = TacticalObservationV3.hash(bytes);
		check(sha.equalsIgnoreCase(expectedSha), "DUEL_RECEIPT_SHA");
		final String text = StandardCharsets.US_ASCII.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
		final String[] rows = text.split("\n", -1);
		check(rows.length == 6 && rows[0].equals(DUEL_PROOF_HEADER) && rows[5].isEmpty(), "DUEL_RECEIPT_SHAPE");
		final var slots = new ArrayList<DuelSlot>(4);
		for (int index = 1; index <= 4; index++) { slots.add(readDuelSlot(rows[index])); }
		validateDuelRoster(slots.stream().map(DuelSlot::identity).toList());
		for (int index = 0; index < slots.size(); index++)
		{
			final DuelSlot slot = slots.get(index), mirrored = slots.get(3 - index);
			check(slot.shotItem() == mirrored.shotItem() && slot.controlRanks().equals(mirrored.controlRanks())
				&& slot.start().equals(slots.get((index + 2) % 4).start()), "DUEL_MIRROR_EQUIPMENT_CONTROL_AND_PLACEMENT");
		}
		check(Math.abs(slots.get(0).start().z() - slots.get(1).start().z()) <= 32 && slots.stream().anyMatch(slot -> !slot.controlRanks().isEmpty()),
			"DUEL_RECEIPT_SLOPE_AND_CONTROL");
		return new DuelFixture(sha, slots);
	}

	private static DuelSlot readDuelSlot(String row)
	{
		final String[] cell = row.split("\t", -1);
		check(cell.length == 24 && cell[0].equals("SETUP_DUEL_STORED") && cell[3].equals("A") && cell[14].equals(DUEL_ARENA), "DUEL_RECEIPT_ROW");
		final var identity = new DuelIdentity(duelInt(cell[1]), cell[2], duelNumber(cell[4]), duelInt(cell[5]), cell[6], cell[7], duelInt(cell[8]), duelRanks(cell[22]));
		final Role role = Role.valueOf(identity.role());
		final var profession = PlayerClass.getPlayerClass(identity.profession());
		final int level = duelInt(cell[9]), weapon = duelInt(cell[10]), shot = duelInt(cell[11]);
		final long shots = duelNumber(cell[12]), adena = duelNumber(cell[13]);
		check(profession != null && profession.level() == 3 && profession.isMage() == role.magic && level == DUEL_LEVEL
			&& weapon == setupWeapon(role, level) && shot == duelShotItem(role) && shots == SHOTS && adena == ADENA, "DUEL_RECEIPT_STOCK");
		final int instance = duelInt(cell[15]);
		final var start = new PhantomTopologyPoint(duelInt(cell[16]), duelInt(cell[17]), duelInt(cell[18]), instance);
		final var stored = new PhantomTopologyPoint(duelInt(cell[19]), duelInt(cell[20]), duelInt(cell[21]), instance);
		check(instance == 0 && start.x() == (identity.side().equals("A") ? DUEL_WEST_X : DUEL_EAST_X) && start.y() == DUEL_Y
			&& start.z() >= -3752 && start.z() <= -352, "DUEL_RECEIPT_START");
		final var controlRanks = duelRanks(cell[23]);
		check(identity.skills().containsAll(controlRanks), "DUEL_RECEIPT_CONTROL_RANKS");
		return new DuelSlot(identity, cell[3], level, weapon, shot, shots, adena, start, stored, controlRanks);
	}

	private static List<SelectedSkill> duelRanks(String text)
	{
		if (text.isEmpty()) { return List.of(); }
		final var result = new ArrayList<SelectedSkill>();
		int previous = 0;
		for (String textRank : text.split(",", -1))
		{
			final String[] rank = textRank.split(":", -1);
			check(rank.length == 2 && result.size() < PhantomNativeCombatCapability.MAXIMUM_SKILLS, "DUEL_RECEIPT_RANK_SHAPE");
			final int id = duelInt(rank[0]);
			check(id > previous, "DUEL_RECEIPT_RANK_ORDER");
			result.add(new SelectedSkill(id, duelInt(rank[1])));
			previous = id;
		}
		return List.copyOf(result);
	}

	private static int duelInt(String text) { return Math.toIntExact(duelNumber(text)); }

	private static long duelNumber(String text)
	{
		final long value = Long.parseLong(text);
		check(Long.toString(value).equals(text), "DUEL_RECEIPT_CANONICAL_NUMBER");
		return value;
	}

	/** Read-only pre-cut check. The runner supplies the profile from its materialization binding. */
	static void verifyDuelMaterialized(DuelSlot slot, long profile, Player actor)
	{
		final var identity = slot.identity();
		final var owner = actor.getNativeWorkOwner();
		check(profile == identity.profile() && actor.getObjectId() == identity.object() && actor.getAccountName().equals(identity.account())
			&& actor.getClient() == null && !actor.isGM() && !actor.isAlikeDead() && !actor.isInParty()
			&& owner != null && owner.player() == actor && owner.isCurrent() && PlayerNativeWork.observationEvidence(actor) != null
			&& actor.getActiveClass() == identity.profession() && actor.getBaseClass() == identity.profession() && actor.getClassIndex() == 0
			&& actor.getLevel() == slot.level(), "DUEL_MATERIALIZED_IDENTITY");
		final Role role = Role.valueOf(identity.role());
		final var weapon = actor.getActiveWeaponItem();
		check(weapon != null && weapon.getId() == slot.weapon() && weapon.getCrystalType().getLevel() <= actor.getExpertiseLevel()
			&& shotItem(actor, role) == slot.shotItem() && actor.getInventory().getInventoryItemCount(slot.shotItem(), -1) == slot.shots()
			&& actor.getAdena() == slot.adena(), "DUEL_MATERIALIZED_WEAPON_AND_BUDGET");
		for (int itemId : setupEquipment(role, slot.weapon()))
		{
			check(actor.getInventory().getItems().stream().filter(item -> item.getId() == itemId && item.isEquipped() && item.getEnchantLevel() == 0).count() == 1,
				"DUEL_MATERIALIZED_EQUIPMENT");
		}
		final var learned = learnedDuelOffense(actor, role);
		check(learned.stream().map(skill -> new SelectedSkill(skill.getId(), skill.getLevel())).toList().equals(identity.skills())
			&& learned.stream().filter(PhantomCombat131Fixture::duelControl).map(skill -> new SelectedSkill(skill.getId(), skill.getLevel())).toList().equals(slot.controlRanks()),
			"DUEL_MATERIALIZED_LEARNED_RANKS");
	}

	static void verifyDuelArena(DuelFixture fixture)
	{
		final var points = duelArena();
		check(fixture.slots().stream().allMatch(slot -> slot.start().equals(points.get(slot.identity().side().equals("A") ? 0 : 1))), "DUEL_ARENA_CHANGED_SINCE_SETUP");
	}

	static void verifyDuelAtStart(DuelSlot slot, long profile, Player actor)
	{
		verifyDuelMaterialized(slot, profile, actor);
		final var start = slot.start();
		check(actor.getX() == start.x() && actor.getY() == start.y() && actor.getZ() == start.z() && actor.getInstanceId() == start.instanceId()
			&& actor.isInsideZone(org.l2jmobius.gameserver.model.zone.ZoneId.PVP) && !actor.isInvul()
			&& duelPoint(GeoEngine.getInstance(), start.x()).equals(start), "DUEL_MATERIALIZED_START");
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
		PhantomProfileRepository profiles, PhantomBackgroundSuite.ProductionAuthorityFixture source, PhantomHistoricalBackgroundPlanner planner, PhantomGoalStateStore goals,
		List<Actor> roster, boolean duel)
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
			final int weapon = setupWeapon(role, level);
			for (int idToEquip : setupEquipment(role, weapon))
			{
				final var owned = actor.getInventory().getItemByItemId(idToEquip);
				actor.getInventory().equipItem(owned == null ? actor.addItem(ItemProcessType.REWARD, idToEquip, 1, actor, false) : owned);
			}
			actor.refreshExpertisePenalty();
			actor.refreshOverloaded();
			check(actor.getActiveWeaponItem().getId() == weapon && actor.getActiveWeaponItem().getCrystalType().getLevel() <= actor.getExpertiseLevel(), "NATIVE_EQUIPMENT_GRADE");
			final int shot = shotItem(actor, role);
			check(!duel || shot == duelShotItem(role), "DUEL_NATIVE_SHOT_CATALOG_CHANGED");
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
			final Scene scene = duel ? null : scene(id, actor, role, seed, source, planner, roster);
			if (scene == null) { actor.setXYZInvisible(origin.getX(), origin.getY(), origin.getZ()); }
			final var plan = scene == null ? planner.idleInitial(id, actor, seed, 1) : scene.plan();
			check(plan.ready(), "PRODUCTION_BASELINE_PLAN");
			goals.insert(id, plan.goal());
			actor.getInventory().updateDatabase();
			actor.storeMe();
			final var capture = source.authority().captureOwnedNative(id, actor, plan.goal(), null);
			check(new PhantomBackgroundTransaction().captureBaseline(capture.state(), plan.goal(), capture.context()).status() == PhantomBackgroundTransaction.Status.SUCCESS, "PRODUCTION_BASELINE_CAPTURE");
			final List<Skill> duelSkills = duel ? learnedDuelOffense(actor, role) : List.of();
			final Skill skill = duel ? duelSkills.stream().filter(Skill::isDamage).findFirst().orElse(null) : scene == null ? minimumSkill(actor, role, null) : scene.skill();
			check(skill != null, "NATIVE_LEARNED_OFFENSIVE_SKILL");
			return new Actor(id, object, actor.getAccountName(), profession, level, skill, weapon, shot, actor.getInventory().getInventoryItemCount(shot, -1),
				actor.getAdena(), actor.getExp(), actor.getSp(), scene, new PhantomTopologyPoint(actor.getX(), actor.getY(), actor.getZ(), actor.getInstanceId()),
				duel ? duelSkills : learnedDamage(actor, role, skill));
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

	private static int setupWeapon(Role role, int level)
	{
		return level == 52 ? role.level52Weapon : role.magic ? 6366 : 6580;
	}

	private static int[] setupEquipment(Role role, int weapon)
	{
		return role.magic ? new int[] {weapon, 2406, 2415} : new int[] {weapon, 2381, 2417};
	}

	private static int duelShotItem(Role role)
	{
		return role.magic ? 2514 : 1467;
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

	private static List<Skill> learnedDamage(Player actor, Role role, Skill baseline)
	{
		final var mode = role.magic ? PhantomCombatMode.RANGED_MAGIC : PhantomCombatMode.MELEE_PHYSICAL;
		final var tree = SkillTreeData.getInstance();
		return actor.getAllSkills().stream().filter(skill -> skill.isDamage() && skill.getAffectRange() == 0 && skill.getFlyType() == null
			&& skill.getHpConsume() == 0 && skill.getItemConsumeCount() == 0 && skill.getChargeConsumeCount() == 0 && skill.getMaxSoulConsumeCount() == 0
			&& skill.getCastRange() > 0 && actor.getStat().getMpConsume(skill) > 0 && L2jCombatBackend.supportsPveSkill(actor, skill, mode))
			.filter(skill ->
			{
				final var learned = tree.getClassSkill(skill.getId(), skill.getLevel(), actor.getPlayerClass());
				return learned != null && learned.getGetLevel() <= actor.getLevel();
			})
			.sorted(Comparator.comparing((Skill skill) -> skill.getId() != baseline.getId()).thenComparingInt(Skill::getId))
			.limit(16).sorted(Comparator.comparingInt(Skill::getId)).toList();
	}

	private static List<Skill> learnedDuelOffense(Player actor, Role role)
	{
		final var mode = role.magic ? PhantomCombatMode.RANGED_MAGIC : PhantomCombatMode.MELEE_PHYSICAL;
		final var tree = SkillTreeData.getInstance();
		return L2jCombatBackend.nativePvpSkills(actor, mode, PhantomNativeCombatCapability.MAXIMUM_SKILLS).stream()
			.map(selected -> actor.getKnownSkill(selected.skillId()))
			.filter(skill -> skill.isDamage() || duelControl(skill))
			.filter(skill -> skill.getHpConsume() == 0 && skill.getItemConsumeCount() == 0 && skill.getChargeConsumeCount() == 0 && skill.getMaxSoulConsumeCount() == 0
				&& skill.getAffectRange() == 0 && skill.getFlyType() == null && skill.getCastRange() > 0 && actor.getStat().getMpConsume(skill) <= actor.getCurrentMp())
			.filter(skill ->
			{
				final var learned = tree.getClassSkill(skill.getId(), skill.getLevel(), actor.getPlayerClass());
				return learned != null && learned.getGetLevel() <= actor.getLevel();
			})
			.sorted(Comparator.comparingInt(Skill::getId)).toList();
	}

	private static boolean duelControl(Skill skill)
	{
		return skill.hasEffectType(EffectType.STUN, EffectType.PARALYZE, EffectType.ROOT, EffectType.MUTE);
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

	private static void verifyStored(Actor actor, boolean duel) throws Exception
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
			if (duel || actor.scene() != null)
			{
				final var point = duel ? actor.storedPoint() : actor.scene().point();
				check(rows.getInt(9) == point.x() && rows.getInt(10) == point.y() && rows.getInt(11) == point.z(), "DURABLE_SCENE_PLACEMENT");
			}
				check(!rows.next(), "DURABLE_ACTOR_SINGLE_ROW");
			}
		}
		try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement(
			"SELECT skill_id,skill_level FROM character_skills WHERE charId=? AND class_index=0"))
		{
			query.setInt(1, actor.object());
			try (var rows = query.executeQuery())
			{
				final var stored = new java.util.HashMap<Integer, Integer>();
				while (rows.next()) { stored.put(rows.getInt(1), rows.getInt(2)); }
				check(actor.learnedOffense().stream().allMatch(skill -> stored.getOrDefault(skill.getId(), 0) == skill.getLevel()), "DURABLE_LEARNED_DAMAGE_RANKS");
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
