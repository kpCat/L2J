/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.phantoms.actions.PhantomEngagementPlanner;
import org.l2jmobius.gameserver.config.ConfigLoader;
import org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig;
import org.l2jmobius.gameserver.data.xml.InitialEquipmentData;
import org.l2jmobius.gameserver.data.xml.InitialShortcutData;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.data.xml.SkillTreeData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerCreationInitializer;
import org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.type.ActionType;
import org.l2jmobius.gameserver.model.item.type.CrystalType;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundPlanner;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatCapabilityResolver;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatMode;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatSkillSafety;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStore.StoredGoal;
import org.l2jmobius.gameserver.phantoms.learning.NeuralPolicy;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.party.PhantomAutonomousFarmParty;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole;
import org.l2jmobius.gameserver.scripting.ScriptEngine;

/** Stock fixture initialization before CUT, without invoking GameServer. */
public final class PhantomLearning001ActorFixture
{
	private static final String SCHEMA = "phantom_learning001_native";
	private static final String ACCOUNT = "learn001fixture";
	private static final Set<Integer> SKILLS = Set.of(1230, 1339);
	private static final String ACTOR_COLUMNS = "split\tscope\tprofile\tactor\tclass\tlevel\tweapon\tskills\tx\ty\tz";
	private static final Set<String> SPLIT_SCOPES = Set.of("TRAIN/SOLO", "TRAIN/PARTY", "TUNING/SOLO", "TUNING/PARTY", "AUDIT/SOLO", "AUDIT/PARTY");
	private static final Location TRAIN_POINT = new Location(146876, -112049, -2344);
	private static final Location AUDIT_POINT = new Location(149374, -113643, -2040);

	public static void main(String[] args)
	{
		int exit = 1;
		try
		{
			if (args.length != 2 || !Set.of("actors", "scenes", "capacity").contains(args[0])) { throw new IllegalArgumentException("Expected actors|scenes|capacity and module path"); }
			final boolean scenes = !args[0].equals("actors");
			final boolean capacity = args[0].equals("capacity");
			final Path lane = Path.of(args[1]).toRealPath().getParent().resolve(".phantom-local/learning001/native").toRealPath();
			final Path game = lane.resolve("game").toRealPath();
			final Path config = game.resolve("config/Database.ini");
			final Path actorManifest = lane.resolve("actors.tsv");
			if (!Path.of(".").toRealPath().equals(game) || Files.mismatch(config, lane.resolve("Database.ini")) != -1
				|| !Files.readString(lane.resolve("owner.txt")).matches("LEARNING001:[0-9a-f]{32}")
				|| Files.exists(actorManifest) != scenes || Files.exists(lane.resolve("cut.properties")))
			{
				throw new IllegalStateException("Expected fresh owned pre-CUT actor fixture");
			}
			final Properties settings = new Properties();
			try (var reader = Files.newBufferedReader(config)) { settings.load(reader); }
			if (!("jdbc:mysql://127.0.0.1:33317/" + SCHEMA).equals(settings.getProperty("URL")) || !"learning001".equals(settings.getProperty("Login")))
			{
				throw new IllegalArgumentException("Unexpected fixture schema");
			}
			ConfigLoader.init();
			if (PhantomPlayersConfig.isEnabled()) { throw new IllegalStateException("Master must remain disabled before CUT"); }
			DatabaseFactory.initFromConfig(config.toString());
			try (var connection = DatabaseFactory.getConnection()) { PhantomLearning001FixtureSql.requireActorIdentity(connection, lane); }
			PhantomHeadlessPlayerTestEnvironment.initializeOwnedPlayerInfrastructure();
			InitialEquipmentData.getInstance();
			InitialShortcutData.getInstance();
			ScriptEngine.getInstance().executeScript(ScriptEngine.MASTER_HANDLER_FILE);
			try (var authority = PhantomBackgroundSuite.ProductionAuthorityFixture.start())
			{
				if (scenes) { planScenes(actorManifest, lane, authority, capacity); }
				else { prepare(actorManifest, authority, settings.getProperty("Password")); }
			}
			exit = 0;
		}
		catch (Throwable failure)
		{
			System.err.println("LEARNING001_ACTOR_PREFLIGHT_FAILED " + failure.getClass().getName());
			if (failure instanceof IllegalStateException) { System.err.println(failure.getMessage()); }
			for (StackTraceElement frame : failure.getStackTrace())
			{
				if (frame.getClassName().startsWith("org.l2jmobius.")) { System.err.println(frame); }
			}
		}
		finally
		{
			if (DatabaseFactory.isInitialized())
			{
				ThreadPool.shutdown();
				DatabaseFactory.close();
			}
		}
		System.out.println("LEARNING001_ACTOR_PREFLIGHT_EXIT=" + exit + " GAMESERVER_BOOT=0");
		System.exit(exit);
	}

	static List<String[]> readActors(Path path) throws Exception
	{
		final List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
		if (lines.size() != SPLIT_SCOPES.size() + 1 || !ACTOR_COLUMNS.equals(lines.getFirst()))
		{
			throw new IllegalStateException("Expected six preregistered fixture actors");
		}
		final var profiles = new HashSet<Long>();
		final var actors = new HashSet<Integer>();
		final var groups = new HashSet<String>();
		final List<String[]> result = new ArrayList<>();
		for (String line : lines.subList(1, lines.size()))
		{
			final String[] fields = line.split("\t", -1);
			if (fields.length != 11 || !groups.add(fields[0] + '/' + fields[1]) || !profiles.add(Long.parseLong(fields[2])) || !actors.add(Integer.parseInt(fields[3])))
			{
				throw new IllegalStateException("Duplicate or invalid preregistered fixture actor");
			}
			result.add(fields);
		}
		if (!SPLIT_SCOPES.equals(groups)) { throw new IllegalStateException("Missing independent fixture split or scope"); }
		return result;
	}

	private record Scene(long profile, StoredGoal current, PhantomGoal goal, List<Monster> targets) { }

	private static void planScenes(Path actors, Path lane, PhantomBackgroundSuite.ProductionAuthorityFixture authority, boolean capacity) throws Exception
	{
		final Path sceneManifest = lane.resolve("rosters.tsv");
		if (Files.exists(sceneManifest) != capacity) { throw new IllegalStateException("Unexpected roster seal state"); }
		final var planner = new PhantomHistoricalBackgroundPlanner(authority.knowledge(), authority.topology(), authority.authority());
		final var profiles = PhantomProfileRepository.open();
		final var goals = new PhantomGoalStateStore(profiles);
		final var monsters = World.getInstance().getVisibleObjects().stream().filter(Monster.class::isInstance).map(Monster.class::cast)
			.filter(npc -> npc.isSpawned() && !npc.isDead() && !npc.isRaid() && !npc.isAggressive() && npc.getInstanceId() == 0).toList();
		final List<String> rows = new ArrayList<>();
		final List<String> budget = new ArrayList<>();
		budget.add("split\tscope\tprofile\tstockSpawns\tmaxRespawnMs\tminSamples\tplannedWindowMs");
		final List<Scene> plans = new ArrayList<>();
		final Map<String, Scene> firstBySplit = new LinkedHashMap<>();
		rows.add("split\tscope\tprofile\tsharedSource\tnativeTargetTemplates\tmaximumHp");
		for (String[] fields : readActors(actors).stream().sorted(Comparator.comparing(row -> row[1])).toList())
		{
			final long profile = Long.parseLong(fields[2]);
			final int actor = Integer.parseInt(fields[3]);
			if (profiles.find(profile).orElseThrow().characterObjectId() != actor)
			{
				throw new IllegalStateException("Unexpected fixture profile");
			}
			final Player player = Player.load(actor);
			if (player == null || !ACCOUNT.equals(player.getAccountName()) || player.getActiveClass() != 94)
			{
				throw new IllegalStateException("Expected registered native Archmage");
			}
			try
			{
				final var geo = GeoEngine.getInstance();
				if (!geo.hasGeo(player.getX(), player.getY())) { throw new IllegalStateException("No player Geo203 coverage"); }
				final List<Monster> visible = monsters.stream().filter(npc ->
				{
					final double distance = player.calculateDistance2D(npc);
					return distance >= 250 && distance <= 800 && Math.abs(npc.getLevel() - player.getLevel()) <= 4
						&& Math.abs(npc.getZ() - player.getZ()) <= 200 && npc.isTargetable() && !npc.isInvul() && npc.isAutoAttackable(player)
						&& PhantomEngagementPlanner.rewardingFarmTarget(player, npc)
						&& geo.hasGeo(npc.getX(), npc.getY()) && geo.canSeeTarget(player, npc)
						&& geo.canMoveToTarget(player, npc) && geo.canMoveToTarget(npc, player);
				}).toList();
				final long seed = 1001001L + profile;
				final var initial = planner.planInitial(profile, player, seed, 1);
				final var current = goals.load(profile).orElse(null);
				final var baseline = current != null ? current.goal() : (initial.ready() ? initial : planner.idleInitial(profile, player, seed, 1)).goal();
				if (baseline == null || !PhantomAutonomousFarmParty.ordinaryIntent(profile, baseline)) { throw new IllegalStateException("No canonical farm intent for " + profile); }
				final var state = authority.authority().capture(profile, player, baseline, null);
				final var peer = firstBySplit.get(fields[0]);
				final List<PhantomDomainRef> sources;
			if (capacity) { sources = List.of(PhantomAutonomousFarmParty.farmSource(baseline)); }
			else if (peer == null)
				{
					final var live = new PhantomTopologyPoint(player.getX(), player.getY(), player.getZ(), player.getInstanceId());
					final var anchors = authority.topology().nearestAnchors(live, PhantomTopologyAnchorRole.FARMING, 8, 1200);
					sources = visible.stream().sorted(Comparator.comparingDouble(Monster::getMaxHp).reversed())
						.flatMap(npc -> anchors.stream().map(anchor -> new PhantomDomainRef("background.farm", npc.getId() + "@" + anchor.id()))
							.filter(candidate -> matchesSource(planner, candidate, npc))).distinct().limit(4).toList();
				}
				else { sources = List.of(PhantomAutonomousFarmParty.farmSource(peer.goal())); }
				PhantomGoal goal = null;
				String reason = "fixture.no_attested_local_source";
				for (var candidate : sources)
				{
					if (candidate.equals(PhantomAutonomousFarmParty.farmSource(baseline)))
					{
						goal = baseline;
						reason = "planner.reused_verified_root";
					}
					else
					{
						final var plan = planner.replanLogicalRendezvous(profile, state, baseline, seed, 2, candidate);
						goal = plan.goal();
						reason = plan.reasonKey();
					}
					System.out.println("LEARNING001_EXACT_SOURCE profile=" + profile + " source=" + candidate.key() + " result=" + reason);
					if (goal != null) { break; }
				}
				final var source = goal == null ? null : PhantomAutonomousFarmParty.farmSource(goal);
				final var targets = visible.stream().filter(npc -> matchesSource(planner, source, npc) && (peer == null || peer.targets().contains(npc))).toList();
				final String templates = targets.stream().map(Monster::getId).distinct().sorted().map(String::valueOf).collect(Collectors.joining(","));
				final double hp = targets.stream().mapToDouble(Monster::getMaxHp).max().orElse(0);
				System.out.println("LEARNING001_SCENE profile=" + profile + " scope=" + fields[1] + " source=" + source + " nearby=" + visible.size() + " eligible=" + targets.size() + " templates=" + templates + " maxHp=" + hp);
				if (targets.isEmpty())
				{
					throw new IllegalStateException("No verified stock field for profile " + profile + " plan=" + reason);
				}
				final var scene = new Scene(profile, current, goal, targets);
				plans.add(scene);
				firstBySplit.putIfAbsent(fields[0], scene);
				rows.add(fields[0] + '\t' + fields[1] + '\t' + profile + '\t' + source.key() + '\t' + templates + '\t' + hp);
				if (capacity)
				{
					if (current == null || !current.goal().equals(goal) || peer != null && !source.equals(PhantomAutonomousFarmParty.farmSource(peer.goal()))
						|| SKILLS.stream().anyMatch(id -> !eligible(player.getKnownSkill(id))))
					{
						throw new IllegalStateException("Sealed scene or learned skills changed for " + profile);
					}
					final var available = fields[1].equals("SOLO") ? visible : targets;
					final var spawns = available.stream().map(Monster::getSpawn).filter(spawn -> spawn != null && spawn.getRespawnMaxDelay() > 0).toList();
					final int minimum = fields[0].equals("TRAIN") ? 8 : 4;
					final long respawn = spawns.stream().mapToLong(spawn -> spawn.getRespawnMaxDelay()).max().orElse(0);
					final long window = sampleWindow(minimum);
					if (spawns.isEmpty() || minimum * 60_000L + ((minimum - 1) / spawns.size()) * respawn > window)
					{
						throw new IllegalStateException("Too few stock respawn opportunities for " + fields[0] + "/" + fields[1] + " count=" + spawns.size());
					}
					final String estimate = fields[0] + '\t' + fields[1] + '\t' + profile + '\t' + spawns.size() + '\t' + respawn + '\t' + minimum + '\t' + window;
					budget.add(estimate);
					System.out.println("LEARNING001_CAPACITY " + estimate);
				}
			}
			finally
			{
				player.stopAllTasks();
				player.deleteMe();
			}
		}
		if (capacity)
		{
			Files.write(lane.resolve("capacity.tsv"), budget, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
			return;
		}
		for (var scene : plans)
		{
			if (scene.current() == null) { goals.insert(scene.profile(), scene.goal()); }
			else if (!scene.current().goal().equals(scene.goal())) { goals.replace(scene.profile(), scene.current().rowVersion(), scene.goal()); }
			if (!scene.goal().equals(goals.load(scene.profile()).orElseThrow().goal())) { throw new IllegalStateException("Farm intent readback changed"); }
		}
		Files.write(sceneManifest, rows, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
	}

	static long sampleWindow(int minimum)
	{
		return (minimum == 8 ? 22 : 10) * 60_000L;
	}

	private static boolean matchesSource(PhantomHistoricalBackgroundPlanner planner, PhantomDomainRef source, Monster npc)
	{
		return source != null && planner.partyNativeTarget(source, npc.getId(), new PhantomTopologyPoint(npc.getX(), npc.getY(), npc.getZ(), npc.getInstanceId()));
	}

	private static void prepare(Path manifest, PhantomBackgroundSuite.ProductionAuthorityFixture authority, String password) throws Exception
	{
		final GeoEngine geo = GeoEngine.getInstance();
		for (Location point : List.of(TRAIN_POINT, AUDIT_POINT))
		{
			if (!geo.hasGeo(point.getX(), point.getY())) { throw new IllegalStateException("Fixture point has no Geo203 coverage"); }
		}
		final String salt = hash("learning001/actor/" + SCHEMA + "/" + password);
		final int[] splitActors = new int[3];
		final List<Integer> selected = new ArrayList<>();
		for (int id = 1; id <= 40 && selected.size() < 6; id++)
		{
			final int split = split(salt, id);
			if (splitActors[split] < 2)
			{
				selected.add(id);
				splitActors[split]++;
			}
		}
		if (selected.size() != 6) { throw new IllegalStateException("Insufficient preregistered split identities"); }
		try (var connection = DatabaseFactory.getConnection(); var statement = connection.createStatement())
		{
			try (var rows = statement.executeQuery("SELECT DATABASE(), @@port, (SELECT COUNT(*) FROM characters), (SELECT COUNT(*) FROM phantom_profiles)"))
			{
				if (!rows.next() || !SCHEMA.equals(rows.getString(1)) || rows.getInt(2) != 33317 || rows.getInt(3) != 0 || rows.getInt(4) != 0)
				{
					throw new IllegalStateException("Actors may only be created in the fresh native fixture");
				}
			}
			statement.executeUpdate("INSERT INTO accounts(login,password,accessLevel,lastServer) VALUES ('" + ACCOUNT + "',NULL,0,1)");
		}
		final var profiles = PhantomProfileRepository.open();
		final var resolver = PhantomCombatCapabilityResolver.fromProgression(authority::progression);
		final int[] scopeBySplit = new int[3];
		final List<String> rows = new ArrayList<>();
		rows.add(ACTOR_COLUMNS);
		for (int next = 1; next <= selected.getLast(); next++)
		{
			if (!selected.contains(next))
			{
				if (profiles.create(null).profileId() != next) { throw new IllegalStateException("Unexpected profile sequence"); }
				continue;
			}
			final int split = split(salt, next);
			final String scope = scopeBySplit[split]++ == 0 ? "SOLO" : "PARTY";
			final boolean audit = split == 0;
			final Location point = audit ? AUDIT_POINT : TRAIN_POINT;
			rows.add(create(profiles, resolver, next, split == 0 ? "AUDIT" : split == 1 ? "TUNING" : "TRAIN", scope, point, audit ? 78 : 77, audit ? CrystalType.A : CrystalType.S));
		}
		Files.write(manifest, rows, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
		System.out.println("LEARNING001_ACTORS_READY train=2 tuning=2 audit=2 archmage=6 scopes=SOLO+PARTY");
	}

	private static String create(PhantomProfileRepository profiles, PhantomCombatCapabilityResolver resolver, int next, String split, String scope, Location point, int level, CrystalType grade)
	{
		final Player player = Player.create(PlayerTemplateData.getInstance().getTemplate(10), ACCOUNT, "LearnA" + next, new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false));
		if (player == null || player.isGM() || player.getClient() != null) { throw new IllegalStateException("Ordinary fixture actor required"); }
		try
		{
			player.setOnlineStatus(true, false);
			PlayerCreationInitializer.initialize(player, PlayerCreationInitializer.Mode.POPULATION, point);
			player.setPlayerClass(94);
			player.setBaseClass(94);
			player.getStat().setExp(player.getStat().getExpForLevel(level));
			player.getStat().setLevel((byte) level);
			for (Skill skill : SkillTreeData.getInstance().getAllAvailableSkills(player, player.getPlayerClass(), true, true, true))
			{
				player.addSkill(skill, true);
			}
			final var weapon = ItemData.getInstance().getAllWeapons().stream().filter(item -> item.isMagicWeapon() && item.getCrystalTypePlus() == grade
				&& item.getReferencePrice() > 0 && !item.isHeroItem() && !item.isPvpItem() && !item.useWeaponSkillsOnly())
				.min(Comparator.comparingInt(item -> item.getId())).orElseThrow();
			player.getInventory().equipItem(player.addItem(ItemProcessType.REWARD, weapon.getId(), 1, player, false));
			for (int id : new int[] {6383, 6384, 6385, 6386})
			{
				player.getInventory().equipItem(player.addItem(ItemProcessType.REWARD, id, 1, player, false));
			}
			for (var shot : ItemData.getInstance().getAllEtcItems())
			{
				if (shot.getCrystalType() == grade && shot.getDefaultAction() == ActionType.SPIRITSHOT && !shot.isBlessed())
				{
					player.addItem(ItemProcessType.REWARD, shot.getId(), 300, player, false);
				}
			}
			player.refreshExpertisePenalty();
			player.refreshOverloaded();
			player.setCurrentHp(player.getMaxHp());
			player.setCurrentMp(player.getMaxMp());
			player.setCurrentCp(player.getMaxCp());
			final var loadout = resolver.resolve(94, PhantomCombatMode.RANGED_MAGIC, id ->
			{
				final Skill skill = player.getKnownSkill(id);
				return skill == null ? 0 : skill.getLevel();
			}, selected -> eligible(player.getKnownSkill(selected.skillId())), false, 4).orElseThrow();
			final Set<Integer> skills = loadout.selectedSkills().stream().map(skill -> skill.skillId()).collect(Collectors.toSet());
			if (!skills.equals(SKILLS)) { throw new IllegalStateException("Native learned capability pair missing: " + skills); }
			final long profile = profiles.create(player.getObjectId()).profileId();
			if (profile != next) { throw new IllegalStateException("Unexpected registered actor sequence"); }
			player.getInventory().updateDatabase();
			player.storeMe();
			return split + '\t' + scope + '\t' + profile + '\t' + player.getObjectId() + "\t94\t" + level + '\t' + weapon.getId() + "\t1230,1339\t" + point.getX() + '\t' + point.getY() + '\t' + point.getZ();
		}
		finally
		{
			player.stopAllTasks();
			player.deleteMe();
		}
	}

	private static boolean eligible(Skill skill)
	{
		return skill != null && PhantomCombatSkillSafety.supports(skill, PhantomCombatMode.RANGED_MAGIC, false) && skill.isDamage()
			&& skill.getAffectRange() <= 0 && !skill.isChanneling() && !skill.isSimultaneousCast() && skill.getFlyType() == null && !skill.hasCastPreconditions();
	}

	private static int split(String salt, int id)
	{
		return Math.min(Math.floorMod(hash(salt + ":" + id).hashCode(), 5), 2);
	}

	private static String hash(String value)
	{
		return NeuralPolicy.sha256(value.getBytes(StandardCharsets.UTF_8));
	}
}
