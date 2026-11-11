/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.awt.Polygon;
import java.awt.geom.Line2D;
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
import org.l2jmobius.gameserver.config.NpcConfig;
import org.l2jmobius.gameserver.config.RatesConfig;
import org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig;
import org.l2jmobius.gameserver.data.xml.InitialEquipmentData;
import org.l2jmobius.gameserver.data.xml.InitialShortcutData;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.data.xml.EnchantSkillGroupsData;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.data.xml.SkillTreeData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerCreationInitializer;
import org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.model.actor.Attackable;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.type.CrystalType;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundPlanner;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState;
import org.l2jmobius.gameserver.phantoms.background.L2jPhantomBackgroundAuthority;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatCapabilityResolver;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatMode;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatPolicy;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatSkillSafety;
import org.l2jmobius.gameserver.phantoms.combat.PhantomNativeCombatCapability;
import org.l2jmobius.gameserver.phantoms.combat.PhantomSupportEffectAuthority;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStore.StoredGoal;
import org.l2jmobius.gameserver.phantoms.learning.NeuralPolicy;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.party.PhantomAutonomousFarmParty;
import org.l2jmobius.gameserver.phantoms.party.L2jPhantomPartyBackend;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyFarmEligibility;
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
	private static final Set<String> SPLIT_SCOPES = Set.of("TRAIN/SOLO", "TRAIN/PARTY", "TUNING/SOLO", "TUNING/PARTY", "AUDIT/SOLO", "AUDIT/PARTY",
		"FIXTURE/HELPER", "FIXTURE/DAMAGE_HELPER", "FIXTURE/HEAL_HELPER");
	private static final int ARCHMAGE = 94;
	private static final int PHOENIX_KNIGHT = 90;
	private static final int MAJOR_HEAL = 1401;
	private static final int PROMINENCE_POWER_PLUS_ONE = 101;
	private static final int CHANGED_CREATION = 20966;
	static final long COLD_SOLO_TIMEOUT_MILLIS = 12_000;
	private static final PhantomPartyFarmEligibility.NpcLevelBand PARTY_TARGET_BAND =
		PhantomPartyFarmEligibility.forRoster(List.of(76, 77, 78)).orElseThrow();
	private static final int PARTY_PREFLIGHT_RANGE = PhantomAutonomousFarmParty.MAXIMUM_TARGET_DISTANCE;
	private static final int MINIMUM_APPROACH_DISTANCE = 100;

	public static void main(String[] args)
	{
		int exit = 1;
		try
		{
			if (args.length < 3 || args.length > 4 || !Set.of("actors", "scenes", "capacity", "party", "recover-audit", "stock-heal", "survey").contains(args[0])) { throw new IllegalArgumentException("Expected actors|scenes|capacity|party|recover-audit|stock-heal|survey, module path, lane and optional proof scope"); }
			final boolean scenes = !args[0].equals("actors");
			final boolean capacity = args[0].equals("capacity");
			final boolean party = args[0].equals("party");
			final boolean recovery = args[0].equals("recover-audit");
			final boolean stockHeal = args[0].equals("stock-heal");
			final String proofScope = args.length == 4 ? args[3] : "BOTH";
			if (!(recovery ? Set.of("BOTH", "SOLO", "PARTY").contains(proofScope) : "BOTH".equals(proofScope))) { throw new IllegalArgumentException("Invalid fixture proof scope"); }
			final boolean survey = args[0].equals("survey");
			final Path lane = PhantomLearning001FixtureSql.resolveLane(args[1], args[2]);
			final Path game = lane.resolve("game").toRealPath();
			final Path config = game.resolve("config/Database.ini");
			final Path actorManifest = lane.resolve("actors.tsv");
			if (!Path.of(".").toRealPath().equals(game) || Files.mismatch(config, lane.resolve("Database.ini")) != -1
				|| !Files.readString(lane.resolve("owner.txt")).matches("LEARNING001:[0-9a-f]{32}")
				|| !survey && (Files.exists(actorManifest) != scenes || Files.exists(lane.resolve("cut.properties"))
					|| (party || recovery || stockHeal ? !Files.isRegularFile(lane.resolve("capacity.tsv")) : Files.exists(lane.resolve("attempts")))))
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
			if (stockHeal)
			{
				checkStockPlayerHeal(lane, readActors(actorManifest));
			}
			else
			{
				try (var authority = PhantomBackgroundSuite.ProductionAuthorityFixture.start())
				{
					if (survey)
					{
						inspectStockMagic(authority);
						fixturePoint(authority, true);
					}
					else if (party || recovery) { PhantomLearning001ShadowNative.preflightParty(authority, game, lane, readActors(actorManifest), recovery, proofScope); }
					else if (scenes) { planScenes(actorManifest, lane, authority, capacity); }
					else { prepare(actorManifest, authority, settings.getProperty("Password")); }
				}
			}
			exit = 0;
		}
		catch (Throwable failure)
		{
			System.err.println("LEARNING001_ACTOR_PREFLIGHT_FAILED " + failure.getClass().getName());
			if (failure instanceof IllegalStateException || failure instanceof IllegalArgumentException) { System.err.println(failure.getMessage()); }
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
			throw new IllegalStateException("Expected six learners and three preregistered Party helpers");
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

	private static void checkStockPlayerHeal(Path lane, List<String[]> actors) throws Exception
	{
		if (Files.exists(lane.resolve("stock-heal-cut.tsv"))) { throw new IllegalStateException("Stock Heal control already measured in this lane"); }
		final var helper = actors.stream().filter(row -> "HEAL_HELPER".equals(row[1])).findFirst().orElseThrow();
		final Player caster = Player.load(Integer.parseInt(helper[3]));
		final Player recipient = Player.create(PlayerTemplateData.getInstance().getTemplate(0), ACCOUNT,
			"LearnH" + Long.toUnsignedString(System.nanoTime(), 36), new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false));
		if (caster == null || recipient == null) { throw new IllegalStateException("Stock Heal actors could not load"); }
		try
		{
			if (caster.isNativeWorkManaged() || recipient.isNativeWorkManaged() || caster.isGM() || recipient.isGM()
				|| caster.getKnownSkill(MAJOR_HEAL) == null || !ACCOUNT.equals(caster.getAccountName()))
			{
				throw new IllegalStateException("Stock Heal requires ordinary unmanaged owned Players");
			}
			// Independent regression input, declared before its measurement boundary.
			recipient.setOnlineStatus(true, false);
			PlayerCreationInitializer.initialize(recipient, PlayerCreationInitializer.Mode.POPULATION,
				new Location(caster.getX() + 30, caster.getY(), caster.getZ()));
			recipient.getStat().setExp(recipient.getStat().getExpForLevel(78));
			recipient.getStat().setLevel((byte) 78);
			recipient.setCurrentHp(recipient.getMaxHp() * .4);
			caster.setOnlineStatus(true, false);
			caster.spawnMe();
			recipient.spawnMe();
			final double hp = recipient.getCurrentHp(), mp = caster.getCurrentMp();
			final long exp = recipient.getExp(), sp = recipient.getSp(), casterExp = caster.getExp(), casterSp = caster.getSp();
			final var skill = caster.getKnownSkill(MAJOR_HEAL);
			Files.writeString(lane.resolve("stock-heal-cut.tsv"), "caster\trecipient\tskill\thp\tmp\n" + caster.getObjectId() + "\t"
				+ recipient.getObjectId() + "\t" + skill.getId() + ":" + skill.getLevel() + "\t" + hp + "\t" + mp + "\n", StandardOpenOption.CREATE_NEW);
			caster.setTarget(recipient);
			if (!caster.useMagic(skill, false, false)) { throw new IllegalStateException("Ordinary stock Heal dispatch rejected"); }
			final long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(12);
			boolean healed = false, spent = false;
			while (System.nanoTime() < deadline)
			{
				healed |= recipient.getCurrentHp() > hp + recipient.getMaxHp() * .15;
				spent |= caster.getCurrentMp() < mp - 1;
				if (healed && spent && !caster.isCastingNow()) { break; }
				Thread.sleep(50);
			}
			if (!healed || !spent || recipient.getExp() != exp || recipient.getSp() != sp || caster.getExp() != casterExp || caster.getSp() != casterSp)
			{
				throw new IllegalStateException("Ordinary stock Heal effect, MP cost or conservation failed hp=" + hp + "->" + recipient.getCurrentHp()
					+ " mp=" + mp + "->" + caster.getCurrentMp());
			}
			System.out.println("LEARNING001_STOCK_PLAYER_HEAL_PASS caster=" + caster.getObjectId() + " recipient=" + recipient.getObjectId()
				+ " skill=" + skill.getId() + ":" + skill.getLevel() + " hp=" + hp + "->" + recipient.getCurrentHp() + " mp=" + mp + "->" + caster.getCurrentMp()
				+ " managed=false expSp=UNCHANGED GameServer=0");
		}
		finally
		{
			for (Player player : List.of(caster, recipient)) { player.stopAllTasks(); player.storeMe(); player.deleteMe(); }
		}
	}

	private record Scene(long profile, StoredGoal current, PhantomGoal goal, List<Monster> targets, PhantomBackgroundState state) { }

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
			if (player == null || !ACCOUNT.equals(player.getAccountName()) || player.getActiveClass() != actorClass(fields[1]))
			{
				throw new IllegalStateException("Unexpected registered actor class");
			}
			try
			{
				final var geo = GeoEngine.getInstance();
				if (!geo.hasGeo(player.getX(), player.getY())) { throw new IllegalStateException("No player Geo203 coverage"); }
				final List<Monster> visible = monsters.stream().filter(npc ->
				{
					final double distance = player.calculateDistance2D(npc);
					return !npc.isChampion() && distance >= MINIMUM_APPROACH_DISTANCE && distance <= PARTY_PREFLIGHT_RANGE && Math.abs(npc.getLevel() - player.getLevel()) <= 4
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
				else if (plans.isEmpty())
				{
					final var live = new PhantomTopologyPoint(player.getX(), player.getY(), player.getZ(), player.getInstanceId());
					final var anchors = authority.topology().nearestAnchors(live, PhantomTopologyAnchorRole.FARMING, 16, PARTY_PREFLIGHT_RANGE);
					sources = visible.stream().filter(PhantomLearning001ActorFixture::fixturePartyTarget)
						.sorted(Comparator.comparingDouble(Monster::getMaxHp))
						.flatMap(npc -> anchors.stream().map(anchor -> new PhantomDomainRef("background.farm", npc.getId() + "@" + anchor.id()))
							.filter(candidate -> matchesSource(planner, candidate, npc))).distinct().limit(4).toList();
				}
				else { sources = List.of(PhantomAutonomousFarmParty.farmSource(plans.getFirst().goal())); }
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
				final var targets = visible.stream().filter(npc -> matchesSource(planner, source, npc)
					&& (peer == null || peer.targets().contains(npc))).toList();
				if (peer != null)
				{
					System.out.println("LEARNING001_OPENING_RESERVE profile=" + profile + " strictSixtySecond="
						+ targets.stream().anyMatch(npc -> L2jPhantomPartyBackend.safeFarmOpening(npc, List.of(peer.state(), state))));
				}
				final String templates = targets.stream().map(Monster::getId).distinct().sorted().map(String::valueOf).collect(Collectors.joining(","));
				final double hp = targets.stream().mapToDouble(Monster::getMaxHp).max().orElse(0);
				System.out.println("LEARNING001_SCENE profile=" + profile + " scope=" + fields[1] + " source=" + source + " nearby=" + visible.size() + " eligible=" + targets.size() + " templates=" + templates + " maxHp=" + hp);
				if (targets.isEmpty())
				{
					for (Monster npc : monsters.stream().filter(npc -> fixturePartyTarget(npc) && player.calculateDistance2D(npc) <= 2000)
						.sorted(Comparator.comparingDouble(player::calculateDistance2D)).limit(6).toList())
					{
						System.out.println("LEARNING001_TARGET_PREFLIGHT profile=" + profile + " npc=" + npc.getId() + " object=" + npc.getObjectId()
							+ " xyz=" + npc.getX() + "," + npc.getY() + "," + npc.getZ() + " range=" + (int) player.calculateDistance2D(npc)
							+ " champion=" + npc.isChampion() + " see=" + geo.canSeeTarget(player, npc) + " direct=" + geo.canMoveToTarget(player, npc)
							+ " return=" + geo.canMoveToTarget(npc, player) + " viable=" + PhantomEngagementPlanner.viable(player, npc));
					}
					throw new IllegalStateException("No verified stock field for profile " + profile + " plan=" + reason);
				}
				final var scene = new Scene(profile, current, goal, targets, state);
				plans.add(scene);
				firstBySplit.putIfAbsent(fields[0], scene);
				rows.add(fields[0] + '\t' + fields[1] + '\t' + profile + '\t' + source.key() + '\t' + templates + '\t' + hp);
				if (capacity)
				{
					final boolean nativeSkills = switch (fields[1])
					{
						case "HELPER" -> guardianReady(player);
						case "DAMAGE_HELPER" -> damageHelperReady(player);
						case "HEAL_HELPER" -> healHelperReady(player);
						default -> SKILLS.stream().allMatch(id -> eligible(player.getKnownSkill(id)));
					};
					if (current == null || !current.goal().equals(goal) || peer != null && !source.equals(PhantomAutonomousFarmParty.farmSource(peer.goal()))
						|| !nativeSkills)
					{
						throw new IllegalStateException("Sealed scene or learned skills changed for " + profile);
					}
					final var available = fields[1].equals("SOLO") ? soloOpeningTargets(player) : targets;
					if (available.isEmpty() && fields[1].equals("SOLO"))
					{
						for (Monster npc : visible.stream().limit(4).toList())
						{
							final var facts = L2jPhantomBackgroundAuthority.nativePlanningCombat(player, npc, player.getKnownSkill(1230));
							System.out.println("LEARNING001_SOLO_OPENING_REFUSED profile=" + profile + " npc=" + npc.getId()
								+ " hp=" + player.getCurrentHp() + "/" + player.getMaxHp() + " mp=" + player.getCurrentMp()
								+ " requiredMp=" + facts.requiredMp() * 3 + " incoming12s=" + soloIncoming(facts.combat(), npc));
						}
					}
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

	private static boolean guardianReady(Player player)
	{
		final Skill aggression = player.getKnownSkill(28);
		return aggression != null && PhantomCombatSkillSafety.supportsNpcAggro(aggression, player.isTransformed());
	}

	static List<Monster> soloOpeningTargets(Player player)
	{
		final var geo = GeoEngine.getInstance();
		return World.getInstance().getVisibleObjectsInRange(player, Monster.class, 1700).stream()
			.filter(npc -> npc.isSpawned() && !npc.isChampion() && !npc.isAggressive() && !npc.isAlikeDead() && npc.isTargetable() && !npc.isInvul()
				&& npc.getInstanceId() == player.getInstanceId() && player.calculateDistance2D(npc) >= 100
				&& Math.abs(player.getZ() - npc.getZ()) <= 200 && npc.getLevel() <= player.getLevel() + 4
				&& npc.isAutoAttackable(player) && PhantomEngagementPlanner.rewardingFarmTarget(player, npc) && geo.hasGeo(npc.getX(), npc.getY())
				&& geo.canSeeTarget(player, npc) && geo.canMoveToTarget(player, npc))
			.sorted(Comparator.comparingDouble(Monster::getMaxHp).thenComparingDouble(npc -> -player.calculateDistance2D(npc))).limit(24)
			.filter(npc -> safeSoloOpening(player, npc)).toList();
	}

	static List<Monster> coldSoloOpeningTargets(Player player)
	{
		if (player.getCurrentHp() < player.getMaxHp() * .9) { return List.of(); }
		final int range = SKILLS.stream().map(player::getKnownSkill).filter(java.util.Objects::nonNull)
			.mapToInt(player::getMagicalAttackRange).min().orElse(0);
		return soloOpeningTargets(player).stream().filter(npc -> player.calculateDistance3D(npc) <= range).filter(npc ->
		{
			final var facts = L2jPhantomBackgroundAuthority.nativePlanningCombat(player, npc, player.getKnownSkill(1230));
			final double exposure = PhantomBackgroundModel.incomingDamage(facts.combat(), L2jPhantomBackgroundAuthority.nativePlanningTarget(npc, true),
				COLD_SOLO_TIMEOUT_MILLIS);
			return Double.isFinite(exposure) && exposure < player.getCurrentHp() * .65;
		}).toList();
	}

	static void inspectColdSoloOpening(Player player)
	{
		final int range = SKILLS.stream().map(player::getKnownSkill).filter(java.util.Objects::nonNull).mapToInt(player::getMagicalAttackRange).min().orElse(0);
		for (var npc : World.getInstance().getVisibleObjectsInRange(player, Monster.class, 1700).stream()
			.filter(npc -> npc.isSpawned() && !npc.isAlikeDead()).sorted(Comparator.comparingDouble(player::calculateDistance2D)).limit(12).toList())
		{
			final var facts = L2jPhantomBackgroundAuthority.nativePlanningCombat(player, npc, player.getKnownSkill(1230));
			final double exposure = PhantomBackgroundModel.incomingDamage(facts.combat(), L2jPhantomBackgroundAuthority.nativePlanningTarget(npc, true),
				COLD_SOLO_TIMEOUT_MILLIS);
			System.out.println("LEARNING001_COLD_SOLO_TARGET actor=" + player.getObjectId() + " npc=" + npc.getId() + " object=" + npc.getObjectId()
				+ " hp=" + player.getCurrentHp() + "/" + player.getMaxHp() + " distance=" + player.calculateDistance3D(npc) + " range=" + range
				+ " exposure=" + exposure + " ordinaryOpening=" + soloOpeningTargets(player).contains(npc) + " champion=" + npc.isChampion());
		}
	}

	private static boolean safeSoloOpening(Player player, Monster target)
	{
		final var template = target.getTemplate();
		if (template.getClans() != null && !template.getClans().isEmpty() && World.getInstance()
			.getVisibleObjectsInRange(target, Attackable.class, template.getClanHelpRange()).stream()
			.anyMatch(other -> other != target && !other.isAlikeDead() && other.getInstanceId() == target.getInstanceId()
				&& template.isClan(other.getTemplate().getClans())))
		{
			return false;
		}
		if (PhantomEngagementPlanner.viable(player, target)) { return true; }
		final var facts = L2jPhantomBackgroundAuthority.nativePlanningCombat(player, target, player.getKnownSkill(1230));
		return PhantomCombatPolicy.pveResourcesReady(player.getCurrentHp(), player.getMaxHp(),
			player.getCurrentMp(), facts.requiredMp() * 3, false)
			&& soloIncoming(facts.combat(), target) < player.getCurrentHp() * .65;
	}

	private static double soloIncoming(PhantomBackgroundState.CombatFacts combat, Monster target)
	{
		return PhantomBackgroundModel.incomingDamage(combat, L2jPhantomBackgroundAuthority.nativePlanningTarget(target, true), 12_000);
	}

	static long sampleWindow(int minimum)
	{
		return (minimum == 8 ? 22 : 10) * 60_000L;
	}

	private static boolean matchesSource(PhantomHistoricalBackgroundPlanner planner, PhantomDomainRef source, Monster npc)
	{
		return source != null && planner.partyNativeTarget(source, npc.getId(), new PhantomTopologyPoint(npc.getX(), npc.getY(), npc.getZ(), npc.getInstanceId()));
	}

	private static void inspectStockMagic(PhantomBackgroundSuite.ProductionAuthorityFixture authority)
	{
		final var resolver = PhantomCombatCapabilityResolver.fromProgression(authority::progression);
		for (PlayerClass playerClass : List.of(PlayerClass.SORCERER, PlayerClass.NECROMANCER, PlayerClass.SPELLSINGER, PlayerClass.SPELLHOWLER))
		{
			final var learned = new LinkedHashMap<Integer, Integer>();
			for (var entry : SkillTreeData.getInstance().getCompleteClassSkillTree(playerClass).values())
			{
				if (entry.getGetLevel() <= 70 && (entry.isLearnedByNpc() || entry.isAutoGet()))
				{
					learned.merge(entry.getSkillId(), entry.getSkillLevel(), Math::max);
				}
			}
			final var loadout = resolver.resolve(playerClass.getId(), PhantomCombatMode.RANGED_MAGIC, id -> learned.getOrDefault(id, 0), selected ->
			{
				if (learned.getOrDefault(selected.skillId(), 0) < selected.skillLevel()) { return false; }
				final var skill = SkillData.getInstance().getSkill(selected.skillId(), selected.skillLevel());
				return eligible(skill) && PhantomNativeCombatCapability.mechanics(skill) != null;
			}, false, 4);
			System.out.println("LEARNING001_STOCK_MAGIC_SURVEY class=" + playerClass.name() + " level=70 modeled="
				+ loadout.map(value -> value.selectedSkills().stream().map(skill -> skill.skillId()).toList()).orElse(List.of()));
		}
	}

	private static boolean fixturePartyTarget(Monster npc)
	{
		final var clans = npc.getTemplate().getClans();
		return PARTY_TARGET_BAND.contains(npc.getLevel()) && !npc.isChampion() && !npc.isAggressive()
			&& !npc.isRaid() && npc.isTargetable() && !npc.isInvul()
			&& (clans == null || clans.isEmpty()) && !npc.hasMinions() && npc.getLeader() == null
			&& npc.getSpawn() != null && npc.getSpawn().getRespawnMaxDelay() > 0
			&& java.util.stream.IntStream.rangeClosed(76, 78).allMatch(level -> PhantomCombatPolicy.farmRewardAvailable(
				level - npc.getLevel(), RatesConfig.MONSTER_EXP_MAX_LEVEL_DIFFERENCE, npc.getExpReward(level), npc.getSpReward(level)));
	}

	private record SpawnThreat(int npcId, int minZ, int maxZ, int reach, Location fixed, Polygon polygon)
	{
		private boolean reaches(PhantomTopologyPoint point)
		{
			final long dz = Math.max(0, Math.max((long) minZ - point.z(), (long) point.z() - maxZ));
			final long horizontalSquared = (long) reach * reach - dz * dz;
			if (horizontalSquared < 0) { return false; }
			if (fixed != null)
			{
				final long dx = (long) point.x() - fixed.getX();
				final long dy = (long) point.y() - fixed.getY();
				return dx * dx + dy * dy <= horizontalSquared;
			}
			if (polygon.contains(point.x(), point.y())) { return true; }
			for (int index = 0; index < polygon.npoints; index++)
			{
				final int next = (index + 1) % polygon.npoints;
				if (Line2D.ptSegDistSq(polygon.xpoints[index], polygon.ypoints[index], polygon.xpoints[next], polygon.ypoints[next], point.x(), point.y()) <= horizontalSquared)
				{
					return true;
				}
			}
			return false;
		}
	}

	private static SpawnThreat spawnThreat(Monster npc)
	{
		final var spawn = npc.getSpawn();
		final int reach = npc.getAggroRange() + (spawn == null || spawn.getRandomWalking() ? NpcConfig.MAX_DRIFT_RANGE : 0);
		final var territory = spawn == null ? null : spawn.getSpawnTerritory();
		if (territory != null)
		{
			final var geometry = territory.geometrySnapshot().orElse(null);
			if (geometry != null)
			{
				final var polygon = geometry.main();
				final var vertices = polygon.vertices();
				final var boundary = new Polygon(vertices.stream().mapToInt(vertex -> vertex.x()).toArray(),
					vertices.stream().mapToInt(vertex -> vertex.y()).toArray(), vertices.size());
				return new SpawnThreat(npc.getId(), polygon.lowZ(), polygon.highZ(), reach, null, boundary);
			}
		}
		final var fixed = spawn == null ? null : spawn.getSpawnLocation();
		final var position = fixed == null ? npc.getLocation() : fixed;
		final int reserve = fixed == null ? reach + PARTY_PREFLIGHT_RANGE : reach;
		return new SpawnThreat(npc.getId(), position.getZ(), position.getZ(), reserve, position, null);
	}

	private static Location fixturePoint(PhantomBackgroundSuite.ProductionAuthorityFixture authority, boolean surveyApproach)
	{
		final GeoEngine geo = GeoEngine.getInstance();
		final var planner = new PhantomHistoricalBackgroundPlanner(authority.knowledge(), authority.topology(), authority.authority());
		final var monsters = World.getInstance().getVisibleObjects().stream().filter(Monster.class::isInstance).map(Monster.class::cast)
			.filter(npc -> npc.isSpawned() && !npc.isAlikeDead() && npc.getInstanceId() == 0).toList();
		final var aggressors = monsters.stream().filter(Monster::isAggressive).map(PhantomLearning001ActorFixture::spawnThreat).distinct().toList();
		final var stockTargets = monsters.stream().filter(PhantomLearning001ActorFixture::fixturePartyTarget).toList();
		final var targets = stockTargets.stream().filter(npc -> geo.hasGeo(npc.getX(), npc.getY()))
			.sorted(Comparator.comparing((Monster npc) -> npc.getId() != CHANGED_CREATION)
				.thenComparingDouble(Monster::getMaxHp).thenComparingInt(Monster::getObjectId)).toList();
		if (surveyApproach)
		{
			final var changed = targets.stream().filter(npc -> npc.getId() == CHANGED_CREATION).toList();
			final long exactSource = changed.stream().filter(npc ->
			{
				final var point = new PhantomTopologyPoint(npc.getX(), npc.getY(), npc.getZ(), 0);
				return authority.topology().nearestAnchors(point, PhantomTopologyAnchorRole.FARMING, 12, PARTY_PREFLIGHT_RANGE).stream()
					.anyMatch(anchor -> matchesSource(planner, new PhantomDomainRef("background.farm", CHANGED_CREATION + "@" + anchor.id()), npc));
			}).count();
			System.out.println("LEARNING001_CHANGED_CREATION_SURVEY live=" + monsters.stream().filter(npc -> npc.getId() == CHANGED_CREATION).count()
				+ " eligibleWithGeo=" + changed.size() + " exactSource=" + exactSource + " allIsolatedWithGeo=" + targets.size());
		}
		final var rejected = new HashSet<String>();
		final var approached = new HashSet<String>();
		for (Monster target : targets)
		{
			final var position = new PhantomTopologyPoint(target.getX(), target.getY(), target.getZ(), 0);
			for (var anchor : authority.topology().nearestAnchors(position, PhantomTopologyAnchorRole.FARMING, 12, PARTY_PREFLIGHT_RANGE))
			{
				final var point = anchor.point();
				final var source = new PhantomDomainRef("background.farm", target.getId() + "@" + anchor.id());
				if (!matchesSource(planner, source, target)) { continue; }
				final var aggressor = aggressors.stream().filter(threat -> threat.reaches(point)).findFirst();
				if (aggressor.isPresent())
				{
					if (rejected.size() < 30 && rejected.add(source.key()))
					{
						System.out.println("LEARNING001_FIXTURE_ANCHOR_SPAWN_AGGRO source=" + source.key() + " npc=" + aggressor.orElseThrow().npcId());
					}
				}
				else if (geo.hasGeo(point.x(), point.y()) && geo.getHeight(point.x(), point.y(), point.z()) == point.z()
					&& usableFarmLine(point, position, geo))
				{
					System.out.println("LEARNING001_FIXTURE_ANCHOR source=" + source.key() + " point=" + point + " targetHP=" + target.getMaxHp());
					return new Location(point.x(), point.y(), point.z());
				}
				if (surveyApproach && approached.size() < 128 && approached.add(source.key()))
				{
					final var area = authority.topology().findNode(anchor.nodeId()).orElseThrow().area();
					final var approach = safeApproach(point, position, area, aggressors, geo);
					if (approach != null)
					{
						System.out.println("LEARNING001_FIXTURE_APPROACH_GEOMETRY source=" + source.key() + " anchor=" + point
							+ " point=" + approach + " npcObject=" + target.getObjectId() + " npcPosition=" + position + " targetHP=" + target.getMaxHp());
						return new Location(approach.x(), approach.y(), approach.z());
					}
				}
			}
		}
		throw new IllegalStateException("No safe ground for an eligible stock target, candidates=" + targets.size()
			+ " approachSources=" + approached.size());
	}

	private static PhantomTopologyPoint safeApproach(PhantomTopologyPoint anchor, PhantomTopologyPoint target,
		org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyArea area, List<SpawnThreat> aggressors, GeoEngine geo)
	{
		for (int dx = -256; dx <= 256; dx += 64)
		{
			for (int dy = -256; dy <= 256; dy += 64)
			{
				if ((dx == 0 && dy == 0) || dx * dx + dy * dy > 300 * 300) { continue; }
				final int x = anchor.x() + dx;
				final int y = anchor.y() + dy;
				if (!geo.hasGeo(x, y)) { continue; }
				final int z = geo.getHeight(x, y, anchor.z());
				final var point = new PhantomTopologyPoint(x, y, z, 0);
				if (!area.contains(point) || aggressors.stream().anyMatch(threat -> threat.reaches(point))
					|| org.l2jmobius.gameserver.managers.ZoneManager.getInstance().getZone(x, y, z,
						org.l2jmobius.gameserver.model.zone.type.WaterZone.class) != null) { continue; }
				if (usableFarmLine(point, target, geo)) { return point; }
			}
		}
		return null;
	}

	private static boolean usableFarmLine(PhantomTopologyPoint point, PhantomTopologyPoint target, GeoEngine geo)
	{
		final double distance = point.distance3D(target);
		return distance >= MINIMUM_APPROACH_DISTANCE && distance <= PARTY_PREFLIGHT_RANGE && Math.abs(point.z() - target.z()) <= 200
			&& geo.canSeeTarget(point.x(), point.y(), point.z(), 0, target.x(), target.y(), target.z(), 0)
			&& geo.canMoveToTarget(point.x(), point.y(), point.z(), target.x(), target.y(), target.z(), 0)
			&& geo.canMoveToTarget(target.x(), target.y(), target.z(), point.x(), point.y(), point.z(), 0);
	}

	static void requireSafePartySpawn(int x, int y, int z)
	{
		final var point = new PhantomTopologyPoint(x, y, z, 0);
		final var threat = World.getInstance().getVisibleObjects().stream().filter(Monster.class::isInstance).map(Monster.class::cast)
			.filter(npc -> npc.isAggressive() && npc.isSpawned() && npc.getInstanceId() == 0)
			.map(PhantomLearning001ActorFixture::spawnThreat).filter(spawn -> spawn.reaches(point)).findFirst();
		if (threat.isPresent())
		{
			throw new IllegalStateException("LEARNING001_PARTY_SPAWN_AGGRO at=" + point + " npc=" + threat.orElseThrow().npcId());
		}
	}

	private static void prepare(Path manifest, PhantomBackgroundSuite.ProductionAuthorityFixture authority, String password) throws Exception
	{
		final var point = fixturePoint(authority, false);
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
			rows.add(create(profiles, resolver, next, split == 0 ? "AUDIT" : split == 1 ? "TUNING" : "TRAIN", scope, point, audit ? 78 : 77, audit ? CrystalType.A : CrystalType.S));
		}
		rows.add(create(profiles, resolver, selected.getLast() + 1, "FIXTURE", "HELPER", point, 76, CrystalType.S));
		rows.add(create(profiles, resolver, selected.getLast() + 2, "FIXTURE", "DAMAGE_HELPER", point, 76, CrystalType.S));
		rows.add(create(profiles, resolver, selected.getLast() + 3, "FIXTURE", "HEAL_HELPER", point, 76, CrystalType.S));
		Files.write(manifest, rows, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
		System.out.println("LEARNING001_ACTORS_READY train=2 tuning=2 audit=2 archmage=6 stockHelper=3 scopes=SOLO+PARTY");
	}

	private static int actorClass(String scope)
	{
		return switch (scope)
		{
			case "HELPER" -> PHOENIX_KNIGHT;
			case "HEAL_HELPER" -> PlayerClass.EVA_SAINT.getId();
			default -> ARCHMAGE;
		};
	}

	private static boolean healHelperReady(Player player)
	{
		final Skill heal = player.getKnownSkill(MAJOR_HEAL);
		return "combat.heal".equals(PhantomSupportEffectAuthority.nativeCapability(heal))
			&& "SINGLE_TARGET".equals(PhantomSupportEffectAuthority.nativeScope(heal)) && player.checkDoCastConditions(heal);
	}

	private static String create(PhantomProfileRepository profiles, PhantomCombatCapabilityResolver resolver, int next, String split, String scope, Location point, int level, CrystalType grade)
	{
		return createTraining(profiles, resolver, next, split, scope, point, level, grade, actorClass(scope), SKILLS, -1);
	}

	/** Pre-CUT setup shared with the owned director; never called for an existing actor. */
	static String createTraining(PhantomProfileRepository profiles, PhantomCombatCapabilityResolver resolver, int next, String split, String scope,
		Location point, int level, CrystalType grade, int selectedClass, Set<Integer> selectedSkills, int configuredEnchant)
	{
		return createTraining(profiles, resolver, next, split, scope, point, level, grade, selectedClass, selectedSkills, configuredEnchant, "LearnA" + next);
	}

	static String createTraining(PhantomProfileRepository profiles, PhantomCombatCapabilityResolver resolver, int next, String split, String scope,
		Location point, int level, CrystalType grade, int selectedClass, Set<Integer> selectedSkills, int configuredEnchant, String characterName)
	{
		final boolean helper = "HELPER".equals(scope);
		final boolean damageHelper = "DAMAGE_HELPER".equals(scope);
		final boolean healHelper = "HEAL_HELPER".equals(scope);
		final int actorClass = selectedClass;
		final var type = PlayerClass.getPlayerClass(actorClass);
		if (type == null) { throw new IllegalArgumentException("Unknown ordinary actor class"); }
		final int baseTemplate = type.getRootClass().getId();
		final Player player = Player.create(PlayerTemplateData.getInstance().getTemplate(baseTemplate), ACCOUNT, characterName, new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false));
		if (player == null || player.isGM() || player.getClient() != null) { throw new IllegalStateException("Ordinary fixture actor required"); }
		try
		{
			player.setOnlineStatus(true, false);
			PlayerCreationInitializer.initialize(player, PlayerCreationInitializer.Mode.POPULATION, point);
			player.setPlayerClass(actorClass);
			player.setBaseClass(actorClass);
			player.getStat().setExp(player.getStat().getExpForLevel(level));
			player.getStat().setLevel((byte) level);
			for (Skill skill : SkillTreeData.getInstance().getAllAvailableSkills(player, player.getPlayerClass(), true, true, true))
			{
				final String support = PhantomSupportEffectAuthority.nativeCapability(skill);
				// The fixture studies offense and the required heal. Idle optional buffs
				// would consume the opening window before either damage action is observed.
				if ("combat.buff".equals(support)) { continue; }
				if (healHelper || !"combat.heal".equals(support)
					|| "SELF".equals(PhantomSupportEffectAuthority.nativeScope(skill)))
				{
					player.addSkill(skill, true);
				}
			}
			if (damageHelper) { provisionDamageHelperSkill(player); }
			final int weapon = helper ? 6364 : grade == CrystalType.S ? 6608 : 5643;
			final var casterWeapon = player.addItem(ItemProcessType.REWARD, weapon, 1, player, false);
			final int weaponEnchant = configuredEnchant >= 0 ? configuredEnchant : switch (split)
			{
				case "TRAIN" -> 0;
				case "TUNING" -> 4;
				case "AUDIT" -> 2;
				default -> 12;
			};
			casterWeapon.setEnchantLevel(weaponEnchant);
			player.getInventory().equipItem(casterWeapon);
			final int[] armorIds = helper ? new int[] {6373, 6374, 6375, 6376, 6377, 6378, 920, 858, 858, 889, 889}
				: new int[] {6383, 6384, 6385, 6386, 641, 920, 858, 858, 889, 889};
			for (int id : armorIds)
			{
				final var armor = player.addItem(ItemProcessType.REWARD, id, 1, player, false);
				armor.setEnchantLevel(10);
				player.getInventory().equipItem(armor);
			}
			final boolean ordinarySpiritshot = "PARTY".equals(scope);
			final int shot = helper ? 1467 : grade == CrystalType.S ? (ordinarySpiritshot ? 2514 : 3952) : (ordinarySpiritshot ? 2513 : 3951);
			player.addItem(ItemProcessType.REWARD, shot, 300, player, false);
			System.out.println("LEARNING001_AUTHORED_LOADOUT split=" + split + " scope=" + scope + " profile=" + next
				+ " weapon=" + weapon + " enchant=" + weaponEnchant + " shot=" + shot);
			player.addItem(ItemProcessType.REWARD, 1539, 24, player, false);
			if (healHelper) { player.addItem(ItemProcessType.REWARD, 3031, 100, player, false); }
			player.refreshExpertisePenalty();
			player.refreshOverloaded();
			player.setCurrentHp(player.getMaxHp());
			player.setCurrentMp(player.getMaxMp());
			player.setCurrentCp(player.getMaxCp());
			if (helper)
			{
				if (!guardianReady(player)) { throw new IllegalStateException("Native Aggression missing"); }
			}
			else if (healHelper)
			{
				if (!healHelperReady(player)) { throw new IllegalStateException("Native Major Heal missing or unavailable"); }
			}
			else
			{
				final var loadout = resolver.resolve(actorClass, PhantomCombatMode.RANGED_MAGIC, player::getSkillLevel,
					selected -> eligible(player.getKnownSkill(selected.skillId())), false, 4).orElseThrow();
				final var skills = loadout.selectedSkills().stream().map(skill -> skill.skillId()).collect(Collectors.toSet());
				if (!(damageHelper ? skills.equals(Set.of(1230)) && damageHelperReady(player) : skills.equals(selectedSkills)))
				{
					throw new IllegalStateException("Native learned capability missing: " + skills);
				}
			}
			final long profile = profiles.create(player.getObjectId()).profileId();
			if (profile != next) { throw new IllegalStateException("Unexpected registered actor sequence"); }
			player.getInventory().updateDatabase();
			player.storeMe();
			final String skills = switch (scope)
			{
				case "HELPER" -> "28";
				case "DAMAGE_HELPER" -> "1230:" + PROMINENCE_POWER_PLUS_ONE;
				case "HEAL_HELPER" -> Integer.toString(MAJOR_HEAL);
				default -> selectedSkills.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
			};
			return split + '\t' + scope + '\t' + profile + '\t' + player.getObjectId() + '\t' + actorClass + '\t' + level + '\t' + weapon + '\t'
				+ skills + '\t' + point.getX() + '\t' + point.getY() + '\t' + point.getZ();
		}
		finally
		{
			player.stopAllTasks();
			player.deleteMe();
		}
	}

	static boolean eligible(Skill skill)
	{
		return skill != null && PhantomCombatSkillSafety.supports(skill, PhantomCombatMode.RANGED_MAGIC, false) && skill.isDamage()
			&& skill.getAffectRange() <= 0 && !skill.isChanneling() && !skill.isSimultaneousCast() && skill.getFlyType() == null && !skill.hasCastPreconditions();
	}

	private static boolean damageHelperReady(Player player)
	{
		final var prominence = player.getKnownSkill(1230);
		return eligible(prominence) && player.getKnownSkill(1339) == null
			&& prominence.getLevel() == PROMINENCE_POWER_PLUS_ONE && prominence.getMagicLevel() == 76
			&& PhantomNativeCombatCapability.mechanics(prominence) != null;
	}

	private static void provisionDamageHelperSkill(Player player)
	{
		final var enchant = EnchantSkillGroupsData.getInstance().getSkillEnchantmentBySkillId(1230);
		final var skill = SkillData.getInstance().getSkill(1230, PROMINENCE_POWER_PLUS_ONE);
		if (player.getLevel() < 76 || player.getPlayerClass().level() < 3 || enchant == null
			|| enchant.getEnchantSkillHolder(PROMINENCE_POWER_PLUS_ONE) == null
			|| player.getSkillLevel(1230) != enchant.getMinSkillLevel(PROMINENCE_POWER_PLUS_ONE)
			|| skill == null || skill.getLevel() != PROMINENCE_POWER_PLUS_ONE)
		{
			throw new IllegalStateException("Stock Prominence enchant unavailable for damage helper");
		}
		player.addSkill(skill, true);
		if (!damageHelperReady(player)) { throw new IllegalStateException("Damage helper enchant did not retain modeled magic level 76"); }
		System.out.println("LEARNING001_DAMAGE_HELPER_ENCHANT skill=" + skill.getId() + " level=" + skill.getLevel()
			+ " magicLevel=" + skill.getMagicLevel());
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
