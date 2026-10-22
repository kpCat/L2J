/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig;
import org.l2jmobius.gameserver.data.MerchantPriceConfigTable;
import org.l2jmobius.gameserver.data.xml.DoorData;
import org.l2jmobius.gameserver.data.xml.InitialEquipmentData;
import org.l2jmobius.gameserver.data.xml.InitialShortcutData;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.data.xml.MapRegionData;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.data.xml.SpawnData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.instance.Merchant;
import org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance;
import org.l2jmobius.gameserver.model.events.EventType;
import org.l2jmobius.gameserver.model.events.holders.actor.creature.OnCreatureAttack;
import org.l2jmobius.gameserver.model.events.holders.actor.creature.OnCreatureDamageDealt;
import org.l2jmobius.gameserver.model.events.holders.actor.player.inventory.OnPlayerItemPickup;
import org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.enums.ShotType;
import org.l2jmobius.gameserver.model.item.type.ActionType;
import org.l2jmobius.gameserver.model.item.type.CrystalType;
import org.l2jmobius.gameserver.phantoms.PhantomOrdinaryLife068Factory;
import org.l2jmobius.gameserver.phantoms.PhantomSystem;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupStateCodec;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundPlanner;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomAutonomousSupplyPolicy;
import org.l2jmobius.gameserver.phantoms.commerce.L2jPhantomSupplyJourney;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceipt;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceiptStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationManager;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyCatalog;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState.Preset;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationState;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStateCodec;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerKind;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialCatalog;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchor;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole;

/** Fresh ordinary births, explicit stopped TEST inputs, then unmodified production decision owners. */
public final class PhantomOrdinaryLife069Native
{
	private static final int MAXIMUM_BIRTHS = 24;
	private static PhantomIdentityLeaseRegistry.Lease _observerLease;
	private record Farm(int npcId, PhantomTopologyAnchor anchor, double serviceDistance, double spoilChance) { }
	private record Purchase(long threshold, long price, long count, int itemId, int npcId, int listId, double distance) { }
	private record BirthPlan(int target, ZoneId zone, String schedule) { }

	public static void main(String[] args) throws Exception
	{
		if (args.length == 1 && args[0].equals("inspect-births"))
		{
			final Path module = Path.of(System.getProperty("phantom.module.root")).toRealPath();
			try
			{
				PhantomTask069Lane.initialize(module, Path.of(System.getProperty("phantom.test.config")).toRealPath());
				final var managed = PhantomProfileRepository.open().listManagedAfter(PhantomPopulationState.COMPONENT_TYPE, 0, MAXIMUM_BIRTHS);
				PhantomAssertions.assertTrue(managed.isEmpty(), "Existing population identities require preservation; pending preparation cannot be retried.");
				PhantomAssertions.assertFalse(Files.exists(module.getParent().resolve(".phantom-local/task069/reports/ordinary069-input.properties")), "Existing ordinary input cannot be prepared again.");
				System.out.println("OWN069_ORDINARY_PREFLIGHT managedPopulation=0; READY=0; ordinaryInput=false; DB_WRITES=0");
			}
			finally { DatabaseFactory.close(); }
			return;
		}
		if (args.length == 1 && args[0].equals("plan"))
		{
			final Path module = Path.of(System.getProperty("phantom.module.root")).toRealPath();
			System.out.println("OWN069_ORDINARY_PLAN " + birthPlan(module) + "; DB_WRITES=0; DB_CONNECTIONS=0"); return;
		}
		if (!PhantomTask069Lane.enabled() || args.length < 1 || args.length > 2 || !List.of("prepare", "prepare-resume", "measure", "run", "resume", "inspect-preparation").contains(args[0])) { throw new IllegalArgumentException("Exact own069 prepare, prepare-resume, measure, run, resume or inspect-preparation required."); }
		final boolean noMeasurement = List.of("prepare", "prepare-resume", "inspect-preparation").contains(args[0]);
		if (noMeasurement && args.length != 1) { throw new IllegalArgumentException("Preparation/inspection takes no measurement duration."); }
		final int seconds = noMeasurement ? 0 : args.length == 2 ? Integer.parseInt(args[1]) : 480;
		if (!noMeasurement && (seconds < 30 || seconds > 720)) { throw new IllegalArgumentException("One bounded ordinary scene requires30..720seconds."); }
		final Path module = Path.of(System.getProperty("phantom.module.root")).toRealPath();
		final Path reports = module.getParent().resolve(".phantom-local/task069/reports");
		final Path input = reports.resolve("ordinary069-input.properties");
		final StringBuilder trace = new StringBuilder("stage\tvalue\n");
		trace.append("production\t").append(Player.class.getProtectionDomain().getCodeSource().getLocation()).append(";mode=").append(args[0]).append('\n');
		Player observer = null;
		try
		{
			PhantomHeadlessPlayerTestEnvironment.initializeNativeInfrastructure(new PhantomTestContext(68006901L, module, reports));
			InitialEquipmentData.getInstance(); InitialShortcutData.getInstance(); MapRegionData.getInstance();
			PhantomSupportedContentScriptBootstrap.loadGoal036Owners(new PhantomTestContext(68006901L, module, reports));
			MerchantPriceConfigTable.getInstance().loadInstances(); SpawnData.getInstance(); DoorData.getInstance(); MerchantPriceConfigTable.getInstance().updateReferences();
			final var profiles = PhantomProfileRepository.open();
			if (args[0].equals("inspect-preparation")) { inspectPreparation(profiles, input, trace); return; }
			if (!args[0].equals("measure")) { prepare(module, input, profiles, trace, args[0].equals("resume") || args[0].equals("prepare-resume")); if (noMeasurement) { return; } }
			final Properties declared = read(input);
			final int target = Integer.parseInt(declared.getProperty("populationTarget"));
			final long id = Long.parseLong(declared.getProperty("profileId"));
			assertCanonical(profiles, id, Integer.parseInt(declared.getProperty("objectId")));
			observer = observer(Integer.parseInt(declared.getProperty("x")), Integer.parseInt(declared.getProperty("y")), Integer.parseInt(declared.getProperty("z")));
			PhantomAssertions.assertTrue(PhantomOrdinaryLife068Factory.start(settings(module, target, target, ZoneId.of(declared.getProperty("zone")))), "Ordinary production factory did not restart.");
			observe(profiles, id, seconds, declared, trace);
		}
		finally
		{
			try
			{
				if (PhantomSystem.hasConfiguredInstance()) { PhantomAssertions.assertTrue(PhantomSystem.shutdownIfStarted() && !PhantomSystem.hasConfiguredInstance(), "Ordinary configured owners did not drain; no DB teardown before their completion."); }
				if (observer != null) { observer.stopAllTasks(); observer.storeMe(); observer.deleteMe(); }
				if (_observerLease != null) { _observerLease.close(); _observerLease = null; }
				trace.append("shutdown\t").append(PhantomSystem.configuredShutdownSnapshot()).append('\n');
				Files.writeString(reports.resolve("ordinary069-" + args[0] + "-" + System.currentTimeMillis() + ".tsv"), trace, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
			}
			finally { ThreadPool.shutdown(); DatabaseFactory.close(); }
		}
	}

	private static void inspectPreparation(PhantomProfileRepository profiles, Path input, StringBuilder trace) throws Exception
	{
		PhantomAssertions.assertFalse(Files.exists(input), "Completed ordinary inputs cannot be inspected as pending preparation.");
		final var pending = read(input.resolveSibling("ordinary069-preparing.properties"));
		final var managed = profiles.listManagedAfter(PhantomPopulationState.COMPONENT_TYPE, 0, MAXIMUM_BIRTHS);
		PhantomAssertions.assertTrue(managed.size() == Integer.parseInt(pending.getProperty("populationTarget")) && managed.stream().allMatch(value -> new PhantomPopulationStateCodec().decode(value.component().payload()).state() == PhantomPopulationState.State.READY), "Exact existing READY cohort required.");
		final var selected = managed.stream().filter(value -> new PhantomPopulationStateCodec().decode(value.component().payload()).classId() == 53).findFirst().orElseThrow();
		final long id = selected.profile().profileId();
		PhantomAssertions.assertTrue(profiles.findComponent(id, PhantomBackgroundState.COMPONENT_TYPE).isEmpty() && profiles.findComponent(id, PhantomBackgroundCatchupState.COMPONENT_TYPE).isEmpty(), "Inspection requires the unchanged prebaseline episode.");
		final Player player = Player.load(selected.profile().characterObjectId());
		final int x = player.getX(), y = player.getY(), z = player.getZ();
		try (var production = PhantomBackgroundSuite.ProductionAuthorityFixture.start())
		{
			final var farms = farms(production); final var planner = new PhantomHistoricalBackgroundPlanner(production.knowledge(), production.topology(), production.authority());
			trace.append("inspectionIdentity\t").append(id).append(';').append(player.getObjectId()).append(";farms=").append(farms.size()).append(";combat=").append(production.authority().planningCombat(player)).append('\n');
			for (Farm farm : farms.stream().limit(2).toList())
			{
				final var point = farm.anchor().point(); player.setXYZInvisible(point.x(), point.y(), GeoEngine.getInstance().getHeight(point.x(), point.y(), point.z()));
				final var plan = planner.planInitial(id, player, 68006901L, 0);
				trace.append("inspectionGlobal\t").append(farm).append(";reason=").append(plan.reasonKey()).append(";spec=").append(plan.spec()).append('\n');
				if (plan.ready())
				{
					final var projected = production.authority().capture(id, player, plan.goal(), null);
					final var local = planner.replanVisibleLocal(id, projected, plan.goal(), 68006901L, 1, new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(player.getX(), player.getY(), player.getZ(), player.getInstanceId()), java.util.Set.of(), java.util.Set.of());
					trace.append("inspectionLocal\t").append(local.reasonKey()).append(";spec=").append(local.spec()).append(";spoil=").append(local.ready() ? NpcData.getInstance().getTemplate(local.spec().npcId()).getSpoilList() : null).append('\n');
				}
			}
			System.out.println("OWN069_ORDINARY_INSPECT " + trace);
		}
		finally { player.setXYZInvisible(x, y, z); player.stopAllTasks(); player.deleteMe(); }
	}

	private static void prepare(Path module, Path input, PhantomProfileRepository profiles, StringBuilder trace, boolean resume) throws Exception
	{
		PhantomAssertions.assertFalse(Files.exists(input), "Own069 ordinary input already exists; preserve identities instead of reseeding.");
		final Path pendingPath = input.resolveSibling("ordinary069-preparing.properties");
		final BirthPlan births;
		if (resume)
		{
			final var pending = read(pendingPath);
			PhantomAssertions.assertEquals("CANONICAL_MANAGER_FRESH", pending.getProperty("mode"), "Exact pending canonical episode required.");
			births = new BirthPlan(Integer.parseInt(pending.getProperty("populationTarget")), ZoneId.of(pending.getProperty("zone")), "preserved");
			PhantomAssertions.assertTrue(births.target() > 0 && births.target() <= MAXIMUM_BIRTHS && PhantomSystem.onlineHumanPoints().isEmpty(), "Pending cohort bound/human prerequisite differs.");
		}
		else
		{
			PhantomAssertions.assertFalse(Files.exists(pendingPath), "Pending preparation requires explicit same-identity resume; births cannot be repeated.");
			PhantomAssertions.assertTrue(PhantomSystem.onlineHumanPoints().isEmpty() && profiles.listManagedAfter(PhantomPopulationState.COMPONENT_TYPE, 0, 1).isEmpty(), "Fresh ordinary birth phase requires no human point or existing managed population; other role profiles are preserved.");
			births = birthPlan(module);
		}
		final int target = births.target(); final ZoneId zone = births.zone();
		for (String relative : List.of("population/high-five-population-v3.xml", "population/high-five-ecology-v1.xml", "social/high-five-social-v1.xml")) { PhantomAssertions.assertEquals(-1L, Files.mismatch(module.resolve("dist/game/data/phantoms").resolve(relative), Path.of("data/phantoms").resolve(relative)), "Runtime/source birth catalog differs: " + relative); }
		List<PhantomProfileRepository.ManagedProfile> managed;
		if (!resume)
		{
		Files.writeString(pendingPath, "mode=CANONICAL_MANAGER_FRESH\npopulationTarget=" + target + "\nzone=" + zone + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
		PhantomAssertions.assertTrue(PhantomOrdinaryLife068Factory.start(settings(module, target, 0, zone)), "Fresh production population factory did not start.");
		final long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
		do
		{
			managed = profiles.listManagedAfter(PhantomPopulationState.COMPONENT_TYPE, 0, MAXIMUM_BIRTHS);
			if (managed.size() == target && managed.stream().allMatch(value -> new PhantomPopulationStateCodec().decode(value.component().payload()).state() == PhantomPopulationState.State.READY)) { break; }
			Thread.sleep(100);
		} while (System.nanoTime() < end);
		trace.append("births\t").append(PhantomSystem.operatorStatus()).append('\n');
		PhantomAssertions.assertTrue(managed.size() == target && managed.stream().allMatch(value -> new PhantomPopulationStateCodec().decode(value.component().payload()).state() == PhantomPopulationState.State.READY), "Canonical bootstrap did not reach READY within90s.");
		PhantomAssertions.assertTrue(PhantomSystem.shutdownIfStarted() && !PhantomSystem.hasConfiguredInstance(), "Birth phase did not stop normally.");
		}
		else
		{
			managed = profiles.listManagedAfter(PhantomPopulationState.COMPONENT_TYPE, 0, MAXIMUM_BIRTHS);
			PhantomAssertions.assertTrue(managed.size() == target && managed.stream().allMatch(value -> new PhantomPopulationStateCodec().decode(value.component().payload()).state() == PhantomPopulationState.State.READY), "Pending same-identity cohort no longer matches READY births.");
			trace.append("resumeExistingBirths\t").append(managed.stream().map(value -> value.profile().profileId() + ":" + value.profile().characterObjectId()).toList()).append("; no births or gear/class/HP replay\n");
		}
		final var selected = managed.stream().filter(value -> new PhantomPopulationStateCodec().decode(value.component().payload()).classId() == 53).findFirst().orElseThrow(() -> new AssertionError("Canonical economy birth was not a Dwarf.") );
		final long id = selected.profile().profileId(); final int objectId = selected.profile().characterObjectId();
		assertCanonical(profiles, id, objectId);
		final var initialClaim = profiles.findComponent(id, PhantomBackgroundCatchupState.COMPONENT_TYPE).map(value -> new PhantomBackgroundCatchupStateCodec().decode(value.payload())).orElse(null);
		PhantomAssertions.assertTrue(profiles.findComponent(id, PhantomBackgroundState.COMPONENT_TYPE).isEmpty() && (initialClaim == null || initialClaim.status() == PhantomBackgroundCatchupState.Status.PENDING && initialClaim.goalId() == 0 && initialClaim.intervalOrdinal() == 0 && initialClaim.cursorEpochMinute() == initialClaim.fromEpochMinute()), "FRESH already produced a baseline/planned cursor: no input mutation or wipe permitted.");
		trace.append("premeasurementClaim\t").append(initialClaim).append("; preserved unchanged\n");
		final Player player = Player.load(objectId);
		try
		{
			PhantomAssertions.assertTrue(player != null && player.getLevel() == (resume ? 20 : 1) && (!resume || player.getActiveClass() == 54) && player.getClient() == null && !player.hasHeadlessOutboundSession() && !player.isGM() && !player.isInvul(), "Canonical ordinary input differs.");
			if (resume)
			{
				PhantomAssertions.assertTrue(player.getAdena() == 0 && player.getInventory().getItems().stream().map(item -> item.getId()).sorted().toList().equals(List.of(88, 393, 415, 850, 850, 881, 881, 913, 1060, 2411, 2450))
					&& player.getInventory().getItems().stream().allMatch(item -> item.getId() == 1060 ? item.getCount() == 20 && !item.isEquipped() : item.getCount() == 1 && item.isEquipped())
					&& player.getKnownSkill(254) != null && player.getKnownSkill(42) != null, "Already issued preimage differs or wallet/filler was partially issued; no repeated TEST inputs.");
			}
			if (!resume)
			{
			// Explicit legal TEST preimage, before any BG baseline, not earned progression or gear.
			player.setPlayerClass(54); player.setBaseClass(54); player.setLearningClass(player.getPlayerClass());
			player.getStat().setExp(player.getStat().getExpForLevel(20)); player.getStat().setLevel((byte) 20); player.setSp(0); player.rewardSkills(); player.removeSkill(194);
			player.addSkill(SkillData.getInstance().getSkill(254, 2), true); player.addSkill(SkillData.getInstance().getSkill(42, 1), true); player.addSkill(SkillData.getInstance().getSkill(239, 1), true);
			for (var item : List.copyOf(player.getInventory().getItems())) { if (!item.isEquipped()) { player.destroyItem(ItemProcessType.DESTROY, item, item.getCount(), player, false); } }
			for (int itemId : List.of(88, 393, 415, 2411, 2450, 913, 850, 850, 881, 881)) { var gear = player.getInventory().addItem(ItemProcessType.REWARD, itemId, 1, player, PhantomOrdinaryLife069Native.class); player.getInventory().equipItem(gear); }
			// Replaced birth gear is TEST input too; it must not later masquerade as earned sale income.
			for (var item : List.copyOf(player.getInventory().getItems())) { if (!item.isEquipped()) { player.destroyItem(ItemProcessType.DESTROY, item, item.getCount(), player, false); } }
			player.getInventory().addItem(ItemProcessType.REWARD, 1060, 20, player, PhantomOrdinaryLife069Native.class);
			player.setCurrentHp(player.getMaxHp()); player.setCurrentMp(player.getMaxMp()); player.refreshExpertisePenalty(); player.refreshOverloaded();
			}
			try (var production = PhantomBackgroundSuite.ProductionAuthorityFixture.start())
			{
				final var farms = farms(production);
				trace.append("fieldPrerequisites\t").append("count=").append(farms.size()).append(";primaryReserve=").append(farms.stream().limit(2).toList()).append("; sources=stock knowledge NPC/spawn + topology + Geo + native service templates\n");
				final var planner = new PhantomHistoricalBackgroundPlanner(production.knowledge(), production.topology(), production.authority());
				Farm chosen = null;
				for (Farm farm : farms.stream().limit(2).toList())
				{
					final var point = farm.anchor().point(); player.setXYZInvisible(point.x(), point.y(), GeoEngine.getInstance().getHeight(point.x(), point.y(), point.z()));
					final var plan = planner.planInitial(id, player, 68006901L, 0);
					trace.append("fieldGlobal\t").append(farm).append(';').append(plan.reasonKey()).append(';').append(plan.spec()).append('\n');
					if (!plan.ready()) { continue; }
					final var projected = production.authority().capture(id, player, plan.goal(), null);
					final var local = planner.replanVisibleLocal(id, projected, plan.goal(), 68006901L, 1, new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(player.getX(), player.getY(), player.getZ(), player.getInstanceId()), java.util.Set.of(), java.util.Set.of());
					trace.append("fieldLocal\t").append(local.reasonKey()).append(';').append(local.spec()).append("; no persistence\n");
					if (local.ready() && NpcData.getInstance().getTemplate(local.spec().npcId()).getSpoilList() != null && !NpcData.getInstance().getTemplate(local.spec().npcId()).getSpoilList().isEmpty()) { chosen = new Farm(local.spec().npcId(), farm.anchor(), farm.serviceDistance(), farm.spoilChance()); break; }
				}
				PhantomAssertions.assertTrue(chosen != null, "Primary/reserve source-backed field has no suitable ordinary planner spoil goal; no forced target.");
				final var point = chosen.anchor().point(); player.setXYZInvisible(point.x(), point.y(), GeoEngine.getInstance().getHeight(point.x(), point.y(), point.z()));
				final var supply = PhantomAutonomousSupplyPolicy.load(Path.of("data/phantoms/life/high-five-autonomous-supply-v1.xml"));
				final Purchase quote = purchase(player, production, supply);
				// A native same-grade quote is a separate economy precondition. Its absence
				// must not fabricate a seller or block the independent useful-Main check.
				final Purchase purchase = quote == null ? new Purchase(1, 0, 0, 0, 0, 0, 0) : quote;
				trace.append("purchasePrecondition\t").append(quote == null ? "UNAVAILABLE; ECON_PARTIAL; initial wallet0; no funded purchase or free shot grant" : quote).append('\n');
				final long wallet = purchase.threshold() - 1;
				player.reduceAdena(ItemProcessType.DESTROY, player.getAdena(), player, false); player.addAdena(ItemProcessType.REWARD, wallet, player, false);
				// Task069 observes useful play without repeating the old capacity-pressure preset.
				PhantomAssertions.assertTrue(player.getCurrentLoad() * 100L < player.getMaxLoad() * 50L && player.getKnownSkill(254) != null && player.getKnownSkill(42) != null && player.getSkillLevel(248) < 1, "Legal mobile Scavenger input invalid.");
				player.setOnlineStatus(true, false); player.getInventory().updateDatabase(); player.storeMe();
				final String declared = "mode=CANONICAL_MANAGER_FRESH_TEST_PREIMAGE_NOT_EARNED\nprofileId=" + id + "\nobjectId=" + objectId + "\npopulationTarget=" + target + "\nzone=" + zone + "\nx=" + player.getX() + "\ny=" + player.getY() + "\nz=" + player.getZ() + "\nnpcId=" + chosen.npcId() + "\nanchor=" + chosen.anchor().id() + "\nwallet=" + wallet + "\npurchaseThreshold=" + purchase.threshold() + "\nshotItemId=" + purchase.itemId() + "\nunitPrice=" + purchase.price() + "\npurchaseNpc=" + purchase.npcId() + "\npurchaseList=" + purchase.listId() + "\nminimumUsefulCount=" + purchase.count() + "\nmerchantDistance=" + purchase.distance() + "\nmandatoryFees=0\ninitialShotCount=0\nfixtureLevel=20\nfixtureClass=54\nfixtureGear=88,393,415,2411,2450,913,850,850,881,881\nslots=" + player.getInventory().getSize() + "\nfactory=PopulationManager_ecology_bootstrap_then_normal_catchup_Decision\nmanualReadyWrites=0\nmanualGoalWrites=0\nmanualCursorWrites=0\n";
				Files.writeString(input, declared, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW); trace.append("TEST_preimage\t").append(declared.replace('\n', ';')).append('\n');
			}
		}
		finally { if (player != null) { player.stopAllTasks(); player.storeMe(); player.deleteMe(); } }
		System.out.println("OWN069_ORDINARY_PREPARED profile=" + id + "; canonicalBirths=" + target + "; no READY/goal/cursor writes");
	}

	private static Purchase purchase(Player player, PhantomBackgroundSuite.ProductionAuthorityFixture production, PhantomAutonomousSupplyPolicy policy)
	{
		final var choices = new ArrayList<Purchase>();
		final int perHit = player.getActiveWeaponItem().getSoulShotCount();
		final double walkingBound = Math.min(org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy.productionDefaults().maximumRouteDistance(), player.getMoveSpeed() * ((policy.journeyMinutes() * 60_000L - 30_000) / 2) / 1000d);
		for (var item : ItemData.getInstance().getAllItems())
		{
			if (item == null || item.getDefaultAction() != ActionType.SOULSHOT || item.getCrystalType() != player.getActiveWeaponItem().getCrystalTypePlus()) { continue; }
			for (var offer : production.commerce().findBuyOffers(item.getId(), 0, 256).values())
			{
				if (offer.limitedStock() || offer.price() <= 0) { continue; }
				for (var object : World.getInstance().getVisibleObjects())
				{
					if (!(object instanceof Merchant merchant) || !offer.npcIds().contains(merchant.getId()) || merchant.getInstanceId() != 0 || !merchant.isSpawned() || merchant.getMpc() == null || merchant.getMpc().getCastleTaxRate() != 0) { continue; }
					final double distance = player.calculateDistance3D(merchant);
					if (distance > walkingBound) { continue; } // Existing route fee estimate is exactly0 inside this bound.
					long count = perHit;
					while (count <= (long) perHit * policy.targetHits() && !policy.usefulPurchase(true, 0, count, perHit, distance, player.getMoveSpeed())) { count++; }
					if (count > (long) perHit * policy.targetHits() || !L2jPhantomSupplyJourney.mobilePurchase(player, item, count)) { continue; }
					final long price = (long) (offer.price() * (1 + merchant.getMpc().getBaseTaxRate()));
					if (price <= 0) { continue; }
					final long needed = Math.multiplyExact(price, count);
					long low = needed, high = Math.addExact(needed, policy.minimumAdenaReserve());
					while (low < high) { final long middle = low + (high - low) / 2; if (policy.availableBudget(middle, 0, 0, true) >= needed) { high = middle; } else { low = middle + 1; } }
					PhantomAssertions.assertTrue(low > 0 && policy.availableBudget(low, 0, 0, true) >= needed && policy.availableBudget(low - 1, 0, 0, true) < needed, "Derived useful-purchase threshold is not minimal.");
					choices.add(new Purchase(low, price, count, item.getId(), merchant.getId(), offer.listId(), distance));
				}
			}
		}
		return choices.stream().min(Comparator.comparingLong(Purchase::threshold).thenComparingDouble(Purchase::distance).thenComparingInt(Purchase::itemId)).orElse(null);
	}

	private static List<Farm> farms(PhantomBackgroundSuite.ProductionAuthorityFixture production)
	{
		final var facts = production.knowledge().snapshot(); final var geo = GeoEngine.getInstance();
		final var services = facts.spawnFactsByNpc().values().stream().flatMap(List::stream).filter(spawn -> spawn.instanceId() == 0 && List.of("Merchant", "Warehouse").contains(NpcData.getInstance().getTemplate(spawn.npcId()).getType())).toList();
		final var farms = new ArrayList<Farm>();
		// Own069 has no old capacity-pressure/near-town preset requirement. Use a bounded
		// source-backed field within the actual native route policy, without changing that policy.
		final double serviceBound = Math.min(30000, org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy.productionDefaults().maximumRouteDistance());
		final int[] stages = new int[4]; double nearestServices = Double.MAX_VALUE;
		for (var npc : facts.npcById().values())
		{
			final var template = NpcData.getInstance().getTemplate(npc.npcId());
			if (template == null || !template.isType("Monster") || template.isAggressive() || npc.level() < 10 || npc.level() > 18 || template.getSpoilList() == null || template.getSpoilList().isEmpty()) { continue; }
			stages[0]++;
			for (var spawn : facts.spawnFactsByNpc().getOrDefault(npc.npcId(), List.of()))
			{
				if (spawn.topologyNodeId() == null) { continue; }
				for (var anchor : production.topology().snapshot().anchorsByNode().getOrDefault(spawn.topologyNodeId(), List.of()))
				{
					stages[1]++;
					final var point = anchor.point(); if (anchor.role() != PhantomTopologyAnchorRole.FARMING || point.instanceId() != 0 || !geo.hasGeo(point.x(), point.y())) { continue; }
					stages[2]++;
					final double distance = Math.max(services.stream().filter(service -> NpcData.getInstance().getTemplate(service.npcId()).isType("Merchant")).mapToDouble(service -> Math.hypot((long) service.x() - point.x(), (long) service.y() - point.y())).min().orElse(Double.MAX_VALUE), services.stream().filter(service -> NpcData.getInstance().getTemplate(service.npcId()).isType("Warehouse")).mapToDouble(service -> Math.hypot((long) service.x() - point.x(), (long) service.y() - point.y())).min().orElse(Double.MAX_VALUE));
					nearestServices = Math.min(nearestServices, distance); if (distance <= serviceBound) { stages[3]++; }
					if (distance <= serviceBound && geo.canMoveToTarget(point.x(), point.y(), point.z(), point.x() + 64, point.y(), point.z(), 0)) { farms.add(new Farm(npc.npcId(), anchor, distance, template.getSpoilList().stream().mapToDouble(drop -> drop.getChance()).max().orElse(0))); }
				}
			}
		}
		System.out.println("OWN069_FIELD_FILTER services=" + services.size() + ";eligibleNpc=" + stages[0] + ";mappedAnchors=" + stages[1] + ";geoAnchors=" + stages[2] + ";nearServices=" + stages[3] + ";minimumServiceDistance=" + nearestServices + ";farms=" + farms.size());
		return farms.stream().distinct().sorted(Comparator.comparingDouble(Farm::serviceDistance).thenComparing(Comparator.comparingDouble(Farm::spoilChance).reversed()).thenComparing(farm -> farm.anchor().id())).toList();
	}

	private static void observe(PhantomProfileRepository profiles, long id, int seconds, Properties declared, StringBuilder trace) throws Exception
	{
		final var receipts = new PhantomCommerceReceiptStore(profiles); final var goals = new PhantomGoalStateStore(profiles);
		final long initialWallet = Long.parseLong(declared.getProperty("wallet")); final int shotId = Integer.parseInt(declared.getProperty("shotItemId"));
		final AtomicInteger chargedTarget = new AtomicInteger(); final AtomicLong paid = new AtomicLong(); final AtomicBoolean chargedHit = new AtomicBoolean(), moneyPickup = new AtomicBoolean();
		Player observed = null; ConsumerEventListener attack = null, damage = null, pickup = null;
		boolean earned = false, bought = false, swept = false, banked = false, returned = false, failureSeen = false, replanned = false; long purchaseKills = 0, bankKills = 0, failureRevision = -1; String lastReceipt = ""; long nextPrint = 0;
		final long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
		try
		{
			while (System.nanoTime() < end)
			{
				final var nativeSnapshot = PhantomSystem.operatorM1TargetSnapshot(id).orElse(null);
				final Player player = nativeSnapshot == null ? null : World.getInstance().getPlayer(nativeSnapshot.objectId());
				if (player != null && player.getNativeWorkOwner() != null)
				{
					if (player != observed)
					{
						if (observed != null) { observed.removeListener(attack); observed.removeListener(damage); observed.removeListener(pickup); }
						observed = player;
						attack = new ConsumerEventListener(player, EventType.ON_CREATURE_ATTACK, (OnCreatureAttack event) -> chargedTarget.set(paid.get() > 0 && event.getAttacker() == player && player.isChargedShot(ShotType.SOULSHOTS) ? event.getTarget().getObjectId() : 0), PhantomOrdinaryLife069Native.class);
						damage = new ConsumerEventListener(player, EventType.ON_CREATURE_DAMAGE_DEALT, (OnCreatureDamageDealt event) -> { if (event.getAttacker() == player && event.getSkill() == null && event.getDamage() > 0 && event.getTarget().getObjectId() == chargedTarget.getAndSet(0)) { chargedHit.set(true); } }, PhantomOrdinaryLife069Native.class);
						pickup = new ConsumerEventListener(player, EventType.ON_PLAYER_ITEM_PICKUP, (OnPlayerItemPickup event) -> { if (event.getPlayer() == player && event.getItem().getId() == 57) { moneyPickup.set(true); } }, PhantomOrdinaryLife069Native.class);
						player.addListener(attack); player.addListener(damage); player.addListener(pickup);
					}
					final var sample = player.getNativeWorkOwner().evidence().snapshot();
					earned |= moneyPickup.get() && sample.killSequence() > 0 && sample.expGained() > 0 && sample.lootSequence() > 0 && player.getAdena() > initialWallet;
					final var receipt = receipts.find(id).map(PhantomCommerceReceiptStore.VersionedReceipt::receipt).orElse(null);
					if (receipt != null && receipt.state() == PhantomCommerceReceipt.State.COMMITTED && !receipt.operationKey().equals(lastReceipt))
					{
						lastReceipt = receipt.operationKey(); trace.append("COMMITTED\t").append(receipt).append('\n');
						if (receipt.request().kind() == PhantomCommerceReceipt.OperationKind.BUY && receipt.request().itemId() == shotId)
						{
							earned |= moneyPickup.get() && sample.killSequence() > 0 && sample.expGained() > 0 && sample.lootSequence() > 0 && receipt.before().primaryCount() > initialWallet;
							PhantomAssertions.assertTrue(earned && receipt.before().primaryCount() >= Long.parseLong(declared.getProperty("purchaseThreshold")) && receipt.before().primaryCount() - receipt.expectedAfter().primaryCount() > 0, "Purchase lacked earned threshold/payment causality.");
							bought = true; paid.set(receipt.before().primaryCount() - receipt.expectedAfter().primaryCount()); purchaseKills = sample.killSequence();
						}
						if (receipt.request().kind() == PhantomCommerceReceipt.OperationKind.WAREHOUSE_DEPOSIT && NpcData.getInstance().getTemplate(Integer.parseInt(declared.getProperty("npcId"))).getSpoilList().stream().anyMatch(drop -> drop.getItemId() == receipt.request().itemId()) && player.getWarehouse().getInventoryItemCount(receipt.request().itemId(), -1) >= receipt.request().count()) { banked = true; bankKills = sample.killSequence(); }
					}
					final var life = PhantomSystem.operatorLifeProgress(id);
					final var root = goals.load(id).orElse(null);
					if (!failureSeen && (player.isDead() || life.getOrDefault("native.autoplay", "").contains("actionOutcome=EXHAUSTED"))) { failureSeen = true; failureRevision = root == null ? -1 : root.goal().revision(); trace.append("FAILURE\t").append(life).append(";revision=").append(failureRevision).append('\n'); }
					if (failureSeen && root != null && root.goal().revision() > failureRevision) { replanned = true; }
					swept |= life.getOrDefault("native.autoplay", "").matches("(?s).*loot.swept=[1-9][0-9]*.*");
					returned |= banked && sample.killSequence() > bankKills;
					if (bought && chargedHit.get() && sample.killSequence() > purchaseKills && swept && returned)
					{
						trace.append("COMPLETE\t").append(life).append('\n'); System.out.println("OWN069_ORDINARY_NATIVE_PASS profile=" + id + "; earned/purchase/charged-hit/spoil/sweep/bank/next-kill"); return;
					}
					PhantomAssertions.assertTrue(!sample.overflow() && player.getNativeWorkOwner().nativeObservationHealthy(), "Native ordinary evidence unproven; preserve trace, no rescue.");
					if (System.nanoTime() >= nextPrint)
					{
						nextPrint = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
						trace.append("OBSERVE\t").append(life).append(";earned=").append(earned).append(";bought=").append(bought).append(";chargedHit=").append(chargedHit).append(";swept=").append(swept).append(";banked=").append(banked).append(";returned=").append(returned).append(";failureSeen=").append(failureSeen).append(";replanned=").append(replanned).append(";root=").append(root).append(";catchup=").append(profiles.findComponent(id, PhantomBackgroundCatchupState.COMPONENT_TYPE).map(value -> new PhantomBackgroundCatchupStateCodec().decode(value.payload()))).append('\n');
						System.out.println("OWN069_ORDINARY_OBSERVE profile=" + id + "; " + life);
					}
				}
				else if (System.nanoTime() >= nextPrint)
				{
					nextPrint = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
					final String waiting = "profile=" + id + "; status=" + PhantomSystem.operatorStatus() + "; readiness=" + PhantomSystem.operatorReadinessProgress(id) + ";life=" + PhantomSystem.operatorLifeProgress(id);
					trace.append("WAIT\t").append(waiting).append('\n'); System.out.println("OWN069_ORDINARY_WAIT " + waiting);
				}
				Thread.sleep(100);
			}
			trace.append("BOUND\t").append(PhantomSystem.operatorStatus()).append(';').append(PhantomSystem.operatorLifeProgress(id)).append(";earned=").append(earned).append(";bought=").append(bought).append(";chargedHit=").append(chargedHit).append(";swept=").append(swept).append(";banked=").append(banked).append(";returned=").append(returned).append('\n');
			throw new AssertionError("Bounded ordinary life incomplete; retain exact trace/identities, no wallet/HP/goal/cursor repair.");
		}
		finally { if (observed != null) { observed.removeListener(attack); observed.removeListener(damage); observed.removeListener(pickup); } }
	}

	private static void assertCanonical(PhantomProfileRepository profiles, long id, int objectId)
	{
		final var state = new PhantomPopulationStateCodec().decode(profiles.findComponent(id, PhantomPopulationState.COMPONENT_TYPE).orElseThrow().payload());
		PhantomAssertions.assertTrue(state.state() == PhantomPopulationState.State.READY && state.creationOrdinal() > 0 && state.deterministicSeed() == PhantomPopulationManager.DETERMINISTIC_SEED && profiles.find(id).orElseThrow().characterObjectId() == objectId && profiles.findComponent(id, "population.ecology").isPresent(), "Exact canonical Manager/ecology/bootstrap identity missing.");
	}

	private static PhantomPlayersConfig.Settings settings(Path module, int target, int active, ZoneId zone)
	{
		final var original = PhantomPlayersConfig.read(module.resolve("docs/phantoms/examples/PhantomPlayers.local-play.ini"));
		return new PhantomPlayersConfig.Settings(true, true, Math.max(2, target), 64, 100, 32, target, active, 2, 64, 64, 64, zone, true, "FRESH", 0, 64, original.conversation(), 0, 0);
	}

	private static BirthPlan birthPlan(Path module)
	{
		final var data = module.resolve("dist/game/data/phantoms");
		final var catalog = PhantomPopulationCatalog.load(data.resolve("population/high-five-population-v3.xml"), ZoneOffset.UTC);
		int target = 0;
		for (int ordinal = 1; ordinal <= MAXIMUM_BIRTHS; ordinal++) { if (catalog.chooseArchetype(PhantomPopulationManager.DETERMINISTIC_SEED, ordinal).name().equals("ECONOMY")) { target = ordinal; break; } }
		PhantomAssertions.assertTrue(target > 0, "Declared bounded canonical birth cycle contains no economy archetype; no reroll.");
		final var ecology = PhantomPopulationEcologyCatalog.load(data.resolve("population/high-five-ecology-v1.xml"), catalog, PhantomSocialCatalog.load(data.resolve("social/high-five-social-v1.xml")));
		final Instant now = Instant.now();
		final String schedule = ecology.assign(Preset.FRESH, 1, target, PhantomPopulationManager.DETERMINISTIC_SEED, now.getEpochSecond() / 60, 0, 0, null).scheduleTemplate();
		for (int quarter = -48; quarter <= 56; quarter++)
		{
			final ZoneId zone = ZoneOffset.ofTotalSeconds(quarter * 900);
			if (catalog.evaluate(schedule, now.minusSeconds(2700), zone, 0).state() == PhantomActivityState.ACTIVE && catalog.evaluate(schedule, now, zone, 0).state() == PhantomActivityState.ACTIVE && catalog.evaluate(schedule, now.plusSeconds(2700), zone, 0).state() == PhantomActivityState.ACTIVE) { return new BirthPlan(target, zone, schedule); }
		}
		throw new AssertionError("Stock canonical economy schedule has no bounded TEST timezone window; do not rewrite schedule components.");
	}

	private static Player observer(int x, int y, int z) throws Exception
	{
		try (var connection = DatabaseFactory.getConnection(); var insert = connection.prepareStatement("INSERT IGNORE INTO accounts(login,password,accessLevel,lastServer) VALUES('task069view','DISABLED_NATIVE_TEST_ONLY',0,1)")) { insert.executeUpdate(); }
		Integer objectId = null;
		try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT charId FROM characters WHERE account_name='task069view' AND char_name='Task069View' AND accesslevel=0"))
		{ try (var rows = query.executeQuery()) { if (rows.next()) { objectId = rows.getInt(1); PhantomAssertions.assertFalse(rows.next(), "Ambiguous own069 passive viewpoint."); } } }
		final Player player = objectId == null ? Player.create(org.l2jmobius.gameserver.data.xml.PlayerTemplateData.getInstance().getTemplate(0), "task069view", "Task069View", new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false)) : Player.load(objectId);
		PhantomAssertions.assertTrue(player != null && !player.isGM() && !player.hasHeadlessOutboundSession() && player.getClient() == null, "Passive own069 viewpoint differs.");
		_observerLease = PhantomIdentityLeaseRegistry.getInstance().tryAcquire(player.getObjectId(), OwnerKind.LOCALPLAY_TEST_HUMAN);
		PhantomAssertions.assertTrue(_observerLease != null, "Own069 native TestHuman identity has another owner.");
		player.setXYZInvisible(x, y, z); player.setOnlineStatus(true, false); player.stopAllTasks(); player.spawnMe();
		PhantomAssertions.assertTrue(!PhantomSystem.onlineHumanPoints().isEmpty(), "Native TestHuman did not enter the canonical locality supplier.");
		return player;
	}

	private static Properties read(Path path) throws Exception
	{
		final Properties result = new Properties(); try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { result.load(reader); } return result;
	}
}
