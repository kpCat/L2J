/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.config.ConfigLoader;
import org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig;
import org.l2jmobius.gameserver.data.xml.InitialEquipmentData;
import org.l2jmobius.gameserver.data.xml.InitialShortcutData;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.type.CrystalType;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundPlanner;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatCapabilityResolver;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.learning.TrainingConfig;
import org.l2jmobius.gameserver.phantoms.learning.TrainingStore;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.scripting.ScriptEngine;

/** Bounded ordinary Player setup before the campaign CUT; sealed restart is read-only. */
public final class PhantomTrainingActors
{
	private static final String HEADER = "scene\tsplit\tscope\tprofile\tactor\tclass\tlevel\tweapon\tskills\tx\ty\tz";
	public static void main(String[] args) throws Exception
	{
		int exit = 1;
		try { prepare(args); exit = 0; }
		catch (Exception failure) { System.err.println("TRAIN_ACTORS_FAILED " + failure.getClass().getSimpleName() + ": " + failure.getMessage()); }
		System.exit(exit);
	}
	private static void prepare(String[] args) throws Exception
	{
		if (args.length != 2) { throw new IllegalArgumentException("Expected installation and config path"); }
		var root = physicalRoot(Path.of(args[0]));
		var configPath = Path.of(args[1]).toAbsolutePath().normalize();
		if (!configPath.equals(root.resolve("TRAIN_CONFIG.ini")) || !configPath.toRealPath().equals(configPath)) { throw new IllegalArgumentException("Owned physical training config required"); }
		var config = TrainingConfig.load(configPath);
		if ("true".equals(config.text("v3Enabled", "false"))) { PhantomLearning003Native.prepareActors(root, config); return; }
		var credentials = settings(root);
		var ledger = new TrainingStore(root);
		ledger.initialize();
		long generation = ledger.counts().attempts();
		String selectors = selectorHash(config);
		verifyPreviousCatalogs(root);
		var reusable = findSealedCatalog(root, generation, selectors);
		if (reusable != null)
		{
			activate(root, reusable);
			verify(root, config);
			System.out.println("TRAIN_ACTORS_REUSED generation=" + generation + " scenes=" + scenes(root).size() + " rewardReset=false");
			return;
		}
		Files.createDirectories(root.resolve("catalogs"));
		String owner = Files.readString(root.resolve("owner.txt")).trim();
		for (var prior : catalogDirectories(root))
		{
			var journal = journal(prior);
			if ("PREPARING".equals(journal.getProperty("phase")))
			{
				journal.setProperty("phase", "INCOMPLETE"); journal.setProperty("reason", "Interrupted own pre-CUT cohort retained");
				writeJournal(prior, journal);
			}
		}
		if (catalogDirectories(root).size() >= config.integer("maxTrainingCohorts", 32, 1, 256)) { throw new IllegalStateException("TRAIN_CAPACITY: maxTrainingCohorts reached; old cohorts preserved"); }
		String attempt = UUID.randomUUID().toString().replace("-", "");
		Path catalog = root.resolve("catalogs").resolve(generation + "-" + attempt);
		Files.createDirectory(catalog);
		var preparation = new Properties();
		preparation.setProperty("owner", owner); preparation.setProperty("generation", Long.toString(generation));
		preparation.setProperty("attempt", attempt); preparation.setProperty("selectors", selectors); preparation.setProperty("phase", "PREPARING");
		writeJournal(catalog, preparation);
		ConfigLoader.init();
		if (PhantomPlayersConfig.isEnabled()) { throw new IllegalStateException("Training master must be OFF before CUT"); }
		DatabaseFactory.initFromConfig(root.resolve("game/config/Database.ini").toString());
		try
		{
			int next;
			int maxProfiles = config.integer("maxStoredProfiles", 4096, 64, 32768);
			int maxActors = config.integer("maxStoredActors", 4096, 32, 32768);
			try (var connection = DatabaseFactory.getConnection(); var query = connection.createStatement(); var rows = query.executeQuery("SELECT DATABASE(),@@port,(SELECT COUNT(*) FROM characters),(SELECT COALESCE(MAX(profile_id),0) FROM phantom_profiles),(SELECT COUNT(*) FROM characters WHERE account_name IS NULL OR account_name<>'learn001fixture')"))
			{
				if (!rows.next() || !"phantom_learning001_train002".equals(rows.getString(1)) || rows.getInt(2) != config.integer("sqlPort", 33327, 33000, 65535) || rows.getLong(5) != 0) { throw new IllegalStateException("Exact own schema and ordinary fixture account required"); }
				if (rows.getLong(3) >= maxActors || rows.getLong(4) >= maxProfiles) { throw new IllegalStateException("TRAIN_CAPACITY: stored actor/profile limit reached"); }
				if (rows.getLong(4) != 0 && !hasAllocationJournal(root, catalog)) { throw new IllegalStateException("Initial schema contains unowned profiles without a prior allocation journal"); }
				next = Math.toIntExact(rows.getLong(4) + 1);
			}
			preparation.setProperty("allocatedFromProfile", Integer.toString(next)); writeJournal(catalog, preparation);
			PhantomHeadlessPlayerTestEnvironment.initializeOwnedPlayerInfrastructure();
			InitialEquipmentData.getInstance(); InitialShortcutData.getInstance();
			ScriptEngine.getInstance().executeScript(ScriptEngine.MASTER_HANDLER_FILE);
			try (var authority = PhantomBackgroundSuite.ProductionAuthorityFixture.start())
			{
				long catalogSeed = config.seed() ^ Long.parseUnsignedLong(PhantomTrainingScenarios.hash("catalog:" + generation).substring(0, 16), 16);
				preparation.setProperty("catalogSeed", Long.toString(catalogSeed)); writeJournal(catalog, preparation);
				var candidates = PhantomTrainingScenarios.discover(config, authority, catalogSeed);
				var profiles = PhantomProfileRepository.open();
				var resolver = PhantomCombatCapabilityResolver.fromProgression(authority::progression);
				try (var connection = DatabaseFactory.getConnection(); var query = connection.createStatement())
				{ query.executeUpdate("INSERT IGNORE INTO accounts(login,password,accessLevel,lastServer) VALUES ('learn001fixture',NULL,0,1)"); }
				var actors = new ArrayList<String>(); actors.add(HEADER);
				var accepted = new ArrayList<PhantomTrainingScenarios.Scene>();
				String salt = PhantomTrainingScenarios.hash("learning001/actor/phantom_learning001_train002/" + credentials.getProperty("Password"));
				var acceptedPartitions = new java.util.HashMap<String, Integer>();
				int characterNames = 0;
				for (var candidate : candidates)
				{
					if (accepted.size() >= config.scenarioLimit()) { break; }
					String split = PhantomTrainingScenarios.sceneSplit(candidate.id());
					if (acceptedPartitions.getOrDefault(split, 0) >= (config.scenarioLimit() + 2) / 3) { continue; }
					var scene = candidate.withSplit(split);
					var rows = new ArrayList<String[]>();
					try
					{
						for (int learner = 0; learner < 2; learner++)
						{
							int searched = 0;
							while (!split.equals(split(salt, next)))
							{
								if (next > maxProfiles) { throw new IllegalStateException("TRAIN_CAPACITY: profile placeholders reached limit"); }
								if (++searched > 200 || profiles.create(null).profileId() != next++) { throw new IllegalStateException("Bounded actor split allocation failed"); }
							}
							checkActorCapacity(next, maxProfiles, maxActors);
							var row = PhantomLearning001ActorFixture.createTraining(profiles, resolver, next, split, learner == 0 ? "SOLO" : "PARTY",
								new Location(scene.x(), scene.y(), scene.z()), scene.level(), CrystalType.valueOf(scene.grade()), scene.classId(), Set.copyOf(scene.skills()), scene.weaponEnchant(),
								"Tr" + attempt.substring(0, 10) + Integer.toHexString(++characterNames)).split("\t", -1);
							rows.add(row);
							recordPrepared(catalog, scene.id(), row);
							next++;
						}
						int helpers = Math.min(3, config.maxActiveActors() - 2);
						for (int index = 0; index < helpers; index++)
						{
							String role = index == 0 ? "HELPER" : index == 1 ? "DAMAGE_HELPER" : "HEAL_HELPER";
							int type = index == 0 ? 90 : index == 1 ? 94 : 105;
							checkActorCapacity(next, maxProfiles, maxActors);
							rows.add(PhantomLearning001ActorFixture.createTraining(profiles, resolver, next, "FIXTURE", role,
								new Location(scene.x(), scene.y(), scene.z()), 76, CrystalType.S, type, Set.of(1230, 1339), -1,
								"Tr" + attempt.substring(0, 10) + Integer.toHexString(++characterNames)).split("\t", -1));
							recordPrepared(catalog, scene.id(), rows.getLast());
							next++;
						}
						for (var row : rows) { plan(authority, row, scene, catalogSeed); }
						for (var row : rows) { actors.add(scene.id() + "\t" + String.join("\t", row)); }
						accepted.add(scene);
						acceptedPartitions.merge(split, 1, Integer::sum);
						preparation.setProperty("lastAllocatedProfile", Integer.toString(next - 1)); writeJournal(catalog, preparation);
						System.out.println("TRAIN_SCENE_PREPARED id=" + scene.id() + " npc=" + scene.npc() + " class=" + scene.classId() + " level=" + scene.level() + " grade=" + scene.grade() + " weaponEnchant=" + scene.weaponEnchant() + " split=" + split);
					}
					catch (IllegalStateException unavailable)
					{
						if (unavailable.getMessage() != null && unavailable.getMessage().startsWith("TRAIN_CAPACITY:")) { throw unavailable; }
						PhantomTrainingScenarios.skip("PRE_CUT_PLAN_REFUSED", scene.id() + ":" + unavailable.getMessage());
						// A partially persisted Player/profile belongs to this cohort, never to the next guess.
						try (var connection = DatabaseFactory.getConnection(); var query = connection.createStatement(); var allocated = query.executeQuery("SELECT COALESCE(MAX(profile_id),0)+1 FROM phantom_profiles"))
						{ if (!allocated.next()) { throw new IllegalStateException("Cannot read owned profile allocation"); } next = Math.toIntExact(allocated.getLong(1)); }
					}
				}
				if (accepted.isEmpty()) { throw new IllegalStateException("TRAIN_NONE_FEASIBLE: bounded catalog search found no canonical legal scene; see recorded skip reasons"); }
				var lines = new ArrayList<String>(); lines.add(PhantomTrainingScenarios.HEADER); accepted.forEach(scene -> lines.add(scene.row()));
				Files.write(catalog.resolve("training-scenes.tsv"), lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
				Files.write(catalog.resolve("training-actors.tsv"), actors, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
				Files.writeString(catalog.resolve("training-manifest.sha256"), catalogSeal(selectors, catalog), StandardOpenOption.CREATE_NEW);
				preparation.setProperty("phase", "SEALED"); writeJournal(catalog, preparation);
				if (!catalog.equals(findSealedCatalog(root, generation, selectors))) { throw new IllegalStateException("New sealed cohort readback changed"); }
				activate(root, catalog);
				verify(root, config);
				System.out.println("TRAIN_ACTORS_READY generation=" + generation + " scenes=" + accepted.size() + " partitions=" + acceptedPartitions + " maxActive=" + config.maxActiveActors());
			}
		}
		catch (Exception failure)
		{
			if ("PREPARING".equals(preparation.getProperty("phase")))
			{
				preparation.setProperty("phase", "INCOMPLETE"); preparation.setProperty("reason", failure.getClass().getSimpleName()); writeJournal(catalog, preparation);
			}
			throw failure;
		}
		finally { ThreadPool.shutdown(); DatabaseFactory.close(); }
	}

	private static void plan(PhantomBackgroundSuite.ProductionAuthorityFixture authority, String[] row, PhantomTrainingScenarios.Scene scene, long seed)
	{
		long profile = Long.parseLong(row[2]);
		var player = Player.load(Integer.parseInt(row[3]));
		if (player == null) { throw new IllegalStateException("Prepared actor cannot be loaded"); }
		try
		{
			var planner = new PhantomHistoricalBackgroundPlanner(authority.knowledge(), authority.topology(), authority.authority());
			final PhantomHistoricalBackgroundPlanner.Result result;
			try
			{
				var initial = planner.planInitial(profile, player, seed, 1);
				var baseline = (initial.ready() ? initial : planner.idleInitial(profile, player, seed, 1)).goal();
				if (baseline == null) { throw new IllegalStateException("No ordinary farm root"); }
				var state = authority.authority().capture(profile, player, baseline, null);
				result = planner.replanLogicalRendezvous(profile, state, baseline, seed, 2, new PhantomDomainRef("background.farm", scene.source()));
				if (result.goal() == null) { throw new IllegalStateException(result.reasonKey()); }
			}
			catch (IllegalArgumentException sceneRefused) { throw new IllegalStateException("Local scene planner refused: " + sceneRefused.getMessage(), sceneRefused); }
			var store = new PhantomGoalStateStore(PhantomProfileRepository.open());
			store.insert(profile, result.goal());
			if (!store.load(profile).orElseThrow().goal().equals(result.goal())) { throw new IllegalStateException("Ordinary farm root readback changed"); }
		}
		finally { player.stopAllTasks(); player.deleteMe(); }
	}

	static Path physicalRoot(Path candidate) throws Exception
	{
		var root = candidate.toAbsolutePath().normalize();
		if (!root.toRealPath().equals(root) || !root.resolve("game").toRealPath().equals(root.resolve("game"))
			|| !Files.readString(root.resolve("owner.txt")).trim().matches("LEARNING002:[a-f0-9]{32}")) { throw new IllegalArgumentException("Physical owned LEARNING002 installation required"); }
		return root;
	}
	static Properties settings(Path root) throws Exception
	{
		if (!Path.of(".").toRealPath().equals(root.resolve("game"))) { throw new IllegalArgumentException("Training working directory must be owned game"); }
		var settings = new Properties();
		try (var reader = Files.newBufferedReader(root.resolve("game/config/Database.ini"))) { settings.load(reader); }
		var config = TrainingConfig.load(root.resolve("TRAIN_CONFIG.ini"));
		String url = "jdbc:mysql://127.0.0.1:" + config.integer("sqlPort", 33327, 33000, 65535) + "/phantom_learning001_train002";
		if (!url.equals(settings.getProperty("URL")) || !"learning001".equals(settings.getProperty("Login")) || settings.getProperty("Password", "").isBlank()) { throw new IllegalArgumentException("Exact training database required"); }
		return settings;
	}
	static List<PhantomTrainingScenarios.Scene> scenes(Path root) throws Exception
	{
		var lines = Files.readAllLines(activeCatalog(root).resolve("training-scenes.tsv"));
		if (lines.isEmpty() || !PhantomTrainingScenarios.HEADER.equals(lines.getFirst())) { throw new IllegalArgumentException("Invalid scene manifest"); }
		return lines.stream().skip(1).map(PhantomTrainingScenarios.Scene::parse).toList();
	}
	static List<String[]> actors(Path root, String scene) throws Exception
	{
		var lines = Files.readAllLines(activeCatalog(root).resolve("training-actors.tsv"));
		if (lines.isEmpty() || !HEADER.equals(lines.getFirst())) { throw new IllegalArgumentException("Invalid actor manifest"); }
		var result = new ArrayList<String[]>();
		for (var line : lines.stream().skip(1).toList())
		{
			var fields = line.split("\t", 2);
			if (fields.length != 2) { throw new IllegalArgumentException("Invalid actor row"); }
			var row = fields[1].split("\t", -1);
			if (row.length != 11) { throw new IllegalArgumentException("Invalid actor columns"); }
			if (scene.equals(fields[0])) { result.add(row); }
		}
		return result;
	}
	static void verify(Path root, TrainingConfig config) throws Exception
	{
		Path active = activeCatalog(root);
		var state = journal(active);
		if (!selectorHash(config).equals(state.getProperty("selectors"))) { throw new IllegalStateException("Active catalog selectors differ; stopped pre-CUT preparation required"); }
		var profiles = new HashSet<String>(); var characters = new HashSet<String>();
		for (var scene : scenes(root))
		{
			if (!PhantomTrainingScenarios.sceneSplit(scene.id()).equals(scene.split())) { throw new IllegalArgumentException("Physical scenario partition changed"); }
			var rows = actors(root, scene.id());
			if (rows.size() < 2 || rows.size() > config.maxActiveActors() || rows.stream().filter(row -> !"FIXTURE".equals(row[0])).count() != 2) { throw new IllegalArgumentException("Invalid bounded scene roster"); }
			for (var row : rows) { if (!profiles.add(row[2]) || !characters.add(row[3])) { throw new IllegalArgumentException("Duplicate scene actor"); } }
		}
	}
	static String selectorHash(TrainingConfig config)
	{
		return PhantomTrainingScenarios.hash(config.classes() + ":" + config.levels() + ":" + config.grades() + ":" + config.weaponEnchants() + ":" + config.npcIds() + ":" + config.scenarioLimit() + ":" + config.maxActiveActors());
	}
	static String catalogSeal(String selectors, Path catalog) throws Exception
	{
		for (String name : List.of("training-scenes.tsv", "training-actors.tsv"))
		{ if (!catalog.resolve(name).toRealPath().equals(catalog.resolve(name))) { throw new IllegalStateException("Catalog payload must remain physical"); } }
		Path prepared = catalog.resolve("prepared-actors.tsv");
		if (Files.exists(prepared) && !prepared.toRealPath().equals(prepared)) { throw new IllegalStateException("Prepared actor journal must be physical"); }
		return PhantomTrainingScenarios.hash(selectors + ":" + catalog.getFileName() + ":" + Files.readString(catalog.resolve("training-scenes.tsv")) + ":"
			+ Files.readString(catalog.resolve("training-actors.tsv")) + ":" + (Files.exists(prepared) ? Files.readString(prepared) : ""));
	}
	static Path findSealedCatalog(Path root, long generation, String selectors) throws Exception
	{
		Path found = null;
		String owner = Files.readString(root.resolve("owner.txt")).trim();
		for (Path catalog : catalogDirectories(root))
		{
			var state = journal(catalog);
			if (!owner.equals(state.getProperty("owner"))) { throw new IllegalStateException("Foreign catalog journal"); }
			if (!"SEALED".equals(state.getProperty("phase"))) { continue; }
			verifySeal(catalog, state);
			if (Long.parseLong(state.getProperty("generation")) == generation && selectors.equals(state.getProperty("selectors"))) { found = catalog; }
		}
		return found;
	}
	static long generation(Path root) throws Exception { return Long.parseLong(journal(activeCatalog(root)).getProperty("generation")); }
	private static Path activeCatalog(Path root) throws Exception
	{
		Path pointer = root.resolve("active-catalog.txt");
		if (!pointer.toRealPath().equals(pointer)) { throw new IllegalStateException("Catalog pointer must be physical"); }
		String relative = Files.readString(pointer).trim().replace('\\', '/');
		if (!relative.matches("catalogs/[0-9]{1,12}-[a-f0-9]{32}")) { throw new IllegalStateException("Invalid owned catalog pointer"); }
		Path catalog = root.resolve(relative).normalize();
		if (!catalog.toRealPath().equals(catalog)) { throw new IllegalStateException("Catalog must be physical"); }
		var state = journal(catalog);
		if (!Files.readString(root.resolve("owner.txt")).trim().equals(state.getProperty("owner")) || !"SEALED".equals(state.getProperty("phase"))) { throw new IllegalStateException("Unsealed or foreign active cohort"); }
		verifySeal(catalog, state);
		return catalog;
	}
	private static void verifySeal(Path catalog, Properties state) throws Exception
	{
		String selectors = state.getProperty("selectors", "");
		if (!catalog.getFileName().toString().startsWith(state.getProperty("generation", "invalid") + "-") || !selectors.matches("[a-f0-9]{64}")
			|| !catalog.resolve("training-manifest.sha256").toRealPath().equals(catalog.resolve("training-manifest.sha256"))
			|| !Files.readString(catalog.resolve("training-manifest.sha256")).trim().equals(catalogSeal(selectors, catalog)))
		{ throw new IllegalStateException("Sealed catalog payload changed"); }
	}
	private static List<Path> catalogDirectories(Path root) throws Exception
	{
		Path catalogs = root.resolve("catalogs");
		if (!Files.exists(catalogs)) { return List.of(); }
		if (!catalogs.toRealPath().equals(catalogs)) { throw new IllegalStateException("Catalog directory must be physical"); }
		try (var paths = Files.list(catalogs))
		{
			var result = paths.filter(Files::isDirectory).sorted().toList();
			for (Path path : result)
			{ if (!path.getFileName().toString().matches("[0-9]{1,12}-[a-f0-9]{32}") || !path.toRealPath().equals(path)) { throw new IllegalStateException("Unknown or redirected catalog"); } }
			return result;
		}
	}
	private static Properties journal(Path catalog) throws Exception
	{
		Path path = catalog.resolve("journal.properties");
		if (!path.toRealPath().equals(path)) { throw new IllegalStateException("Catalog journal must be physical"); }
		var state = new Properties();
		try (var reader = Files.newBufferedReader(path)) { state.load(reader); }
		return state;
	}
	private static void verifyPreviousCatalogs(Path root) throws Exception
	{
		String owner = Files.readString(root.resolve("owner.txt")).trim();
		for (Path catalog : catalogDirectories(root))
		{
			if (!Files.isRegularFile(catalog.resolve("journal.properties")))
			{
				recoverInitialJournal(catalog, owner);
			}
			var state = journal(catalog);
			if (!owner.equals(state.getProperty("owner"))) { throw new IllegalStateException("Foreign prior cohort"); }
			if ("SEALED".equals(state.getProperty("phase"))) { verifySeal(catalog, state); }
			else if (!List.of("PREPARING", "INCOMPLETE").contains(state.getProperty("phase"))) { throw new IllegalStateException("Unknown prior cohort phase"); }
		}
	}
	static void recoverCatalogJournals(Path root) throws Exception
	{
		if (!root.toRealPath().equals(root) || !root.resolve("owner.txt").toRealPath().equals(root.resolve("owner.txt"))
			|| !Files.readString(root.resolve("owner.txt")).trim().matches("LEARNING002:[a-f0-9]{32}")) { throw new IllegalStateException("Physical owned catalog root required"); }
		verifyPreviousCatalogs(root);
	}
	private static void recoverInitialJournal(Path catalog, String owner) throws Exception
	{
		final List<Path> pending;
		try (var files = Files.list(catalog))
		{
			var entries = files.toList();
			// A published/payload file cannot come from the initial, pre-allocation write window.
			for (Path entry : entries)
			{
				String name = entry.getFileName().toString();
				if (!name.matches("\\.journal\\.properties\\.pending-[a-f0-9]{32}") && !name.equals("quarantine")) { throw new IllegalStateException("Missing journal for nonempty historical cohort"); }
			}
			pending = entries.stream().filter(path -> path.getFileName().toString().startsWith(".journal.properties.pending-")).sorted().toList();
			if (pending.size() > 32 || pending.isEmpty() && !entries.isEmpty()) { throw new IllegalStateException("Unknown or excessive initial journal remnants"); }
		}
		var rawByPath = new java.util.LinkedHashMap<Path, byte[]>();
		for (Path path : pending)
		{
			if (!path.toRealPath().equals(path) || !Files.isRegularFile(path) || Files.size(path) > 65536) { throw new IllegalStateException("Initial journal remnant must be bounded and physical"); }
			byte[] raw = Files.readAllBytes(path);
			// Parse owner lines independently: a later truncated escape cannot hide a complete foreign owner.
			for (String line : new String(raw, StandardCharsets.UTF_8).split("\\R"))
			{
				if (!line.stripLeading().startsWith("owner")) { continue; }
				var parsed = new Properties();
				try { parsed.load(new java.io.StringReader(line + "\n")); }
				catch (IllegalArgumentException partialEscape) { continue; }
				String declared = parsed.getProperty("owner");
				if (declared != null && !owner.startsWith(declared)) { throw new IllegalStateException("Foreign pending cohort journal"); }
			}
			rawByPath.put(path, raw);
		}
		if (!pending.isEmpty())
		{
			Path quarantine = catalog.resolve("quarantine");
			Files.createDirectories(quarantine);
			if (!quarantine.toRealPath().equals(quarantine)) { throw new IllegalStateException("Journal quarantine must be physical"); }
			var allowed = new HashSet<String>();
			for (Path path : pending) { allowed.add(path.getFileName() + ".raw"); allowed.add(path.getFileName() + ".raw.sha256"); }
			try (var existing = Files.list(quarantine))
			{
				var entries = existing.toList();
				if (entries.size() > 128) { throw new IllegalStateException("Journal quarantine retention bound reached"); }
				for (Path path : entries)
				{
					String name = path.getFileName().toString();
					boolean ownCopy = pending.stream().anyMatch(source -> name.matches(java.util.regex.Pattern.quote(source.getFileName() + ".raw.copy-") + "[a-f0-9]{32}")
						|| name.matches(java.util.regex.Pattern.quote("." + source.getFileName() + ".raw.sha256.pending-") + "[a-f0-9]{32}"));
					if (!Files.isRegularFile(path) || !path.toRealPath().equals(path) || !allowed.contains(name) && !ownCopy) { throw new IllegalStateException("Unknown quarantine artifact"); }
				}
			}
			for (var entry : rawByPath.entrySet())
			{
				Path raw = quarantine.resolve(entry.getKey().getFileName() + ".raw");
				String sha = org.l2jmobius.gameserver.phantoms.learning.NeuralPolicy.sha256(entry.getValue());
				if (!Files.exists(raw))
				{
					Path copying = raw.resolveSibling(raw.getFileName() + ".copy-" + UUID.randomUUID().toString().replace("-", ""));
					Files.write(copying, entry.getValue(), StandardOpenOption.CREATE_NEW);
					if (!sha.equals(org.l2jmobius.gameserver.phantoms.learning.NeuralPolicy.sha256(Files.readAllBytes(copying)))) { throw new IllegalStateException("Journal quarantine copy SHA changed"); }
					Files.move(copying, raw, StandardCopyOption.ATOMIC_MOVE);
				}
				if (!raw.toRealPath().equals(raw) || Files.mismatch(entry.getKey(), raw) != -1) { throw new IllegalStateException("Pending journal quarantine bytes changed"); }
				Path hash = raw.resolveSibling(raw.getFileName() + ".sha256");
				if (!Files.exists(hash)) { atomicWrite(hash, sha); }
				if (!hash.toRealPath().equals(hash) || !sha.equals(Files.readString(hash).trim())) { throw new IllegalStateException("Pending journal quarantine SHA changed"); }
			}
		}
		var parts = catalog.getFileName().toString().split("-", 2);
		var state = new Properties();
		state.setProperty("owner", owner); state.setProperty("generation", parts[0]); state.setProperty("attempt", parts[1]);
		state.setProperty("phase", "INCOMPLETE"); state.setProperty("reason", "Interrupted initial journal bytes quarantined; no actors activated");
		writeJournal(catalog, state);
	}
	private static boolean hasAllocationJournal(Path root, Path current) throws Exception
	{
		for (Path prior : catalogDirectories(root))
		{ if (!prior.equals(current) && journal(prior).getProperty("allocatedFromProfile", "").matches("[0-9]{1,10}")) { return true; } }
		return false;
	}
	private static void activate(Path root, Path catalog) throws Exception
	{
		verifySeal(catalog, journal(catalog));
		atomicWrite(root.resolve("active-catalog.txt"), root.relativize(catalog).toString().replace('\\', '/') + "\n");
	}
	private static void writeJournal(Path catalog, Properties state) throws Exception
	{
		var writer = new java.io.StringWriter(); state.store(writer, "Owned pre-CUT catalog journal");
		atomicWrite(catalog.resolve("journal.properties"), writer.toString());
	}
	private static void atomicWrite(Path target, String text) throws Exception
	{
		Path pending = target.resolveSibling("." + target.getFileName() + ".pending-" + UUID.randomUUID().toString().replace("-", ""));
		Files.writeString(pending, text, StandardOpenOption.CREATE_NEW);
		Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
	}
	private static void recordPrepared(Path catalog, String scene, String[] row) throws Exception
	{
		Files.writeString(catalog.resolve("prepared-actors.tsv"), scene + "\t" + String.join("\t", row) + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
	}
	private static void checkActorCapacity(int next, int maxProfiles, int maxActors) throws Exception
	{
		if (next > maxProfiles) { throw new IllegalStateException("TRAIN_CAPACITY: profile limit reached"); }
		try (var connection = DatabaseFactory.getConnection(); var query = connection.createStatement(); var rows = query.executeQuery("SELECT COUNT(*) FROM characters"))
		{ if (!rows.next() || rows.getLong(1) >= maxActors) { throw new IllegalStateException("TRAIN_CAPACITY: stored actor limit reached"); } }
	}
	static String split(String salt, long profile)
	{
		int split = Math.floorMod(PhantomTrainingScenarios.hash(salt + ":" + profile).hashCode(), 5);
		return split == 0 ? "AUDIT" : split == 1 ? "TUNING" : "TRAIN";
	}
	private PhantomTrainingActors() { }
}
