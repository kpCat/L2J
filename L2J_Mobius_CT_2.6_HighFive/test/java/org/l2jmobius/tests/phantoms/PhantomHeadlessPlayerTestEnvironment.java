/*
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.l2jmobius.tests.phantoms;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.config.ThreadConfig;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.config.ConfigLoader;
import org.l2jmobius.gameserver.config.FeatureConfig;
import org.l2jmobius.gameserver.data.sql.ClanHallTable;
import org.l2jmobius.gameserver.data.sql.ClanTable;
import org.l2jmobius.gameserver.data.sql.CharInfoTable;
import org.l2jmobius.gameserver.data.xml.AdminData;
import org.l2jmobius.gameserver.data.xml.CategoryData;
import org.l2jmobius.gameserver.data.xml.ClassListData;
import org.l2jmobius.gameserver.data.xml.EnchantItemOptionsData;
import org.l2jmobius.gameserver.data.xml.EnchantSkillGroupsData;
import org.l2jmobius.gameserver.data.xml.ExperienceData;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.data.xml.OptionData;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.data.xml.RecipeData;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.data.xml.SkillLearnData;
import org.l2jmobius.gameserver.data.xml.SkillTreeData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.handler.EffectHandler;
import org.l2jmobius.gameserver.managers.CHSiegeManager;
import org.l2jmobius.gameserver.managers.CastleManager;
import org.l2jmobius.gameserver.managers.ClanHallAuctionManager;
import org.l2jmobius.gameserver.managers.CursedWeaponsManager;
import org.l2jmobius.gameserver.managers.GrandBossManager;
import org.l2jmobius.gameserver.managers.IdManager;
import org.l2jmobius.gameserver.managers.InstanceManager;
import org.l2jmobius.gameserver.managers.RecipeManager;
import org.l2jmobius.gameserver.managers.TerritoryWarManager;
import org.l2jmobius.gameserver.managers.ZoneManager;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance;
import org.l2jmobius.gameserver.model.actor.templates.PlayerTemplate;
import org.l2jmobius.gameserver.model.groups.matching.PartyMatchRoomList;
import org.l2jmobius.gameserver.model.groups.matching.PartyMatchWaitingList;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.olympiad.Hero;
import org.l2jmobius.gameserver.model.olympiad.OlympiadManager;
import org.l2jmobius.gameserver.model.sevensigns.SevenSigns;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.network.GameClient;
import org.l2jmobius.gameserver.phantoms.player.PhantomActionFacade;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.taskmanagers.PlayerAutoSaveTaskManager;
import org.l2jmobius.tests.phantoms.PhantomTestDatabaseBootstrap.BootstrapResult;

public final class PhantomHeadlessPlayerTestEnvironment
{
	private static final int PERSISTED_SKILL_ID = 194;
	private static final int PERSISTED_SKILL_LEVEL = 1;
	private static final long PRIMARY_ITEM_BASELINE = 7;
	private static final long OBSERVER_ITEM_BASELINE = 3;
	private static final List<String> INITIALIZED_SINGLETONS = List.of(
		"ConfigLoader",
		"PhantomTestDatabaseBootstrap",
		"DatabaseFactory",
		"ThreadPool",
		"IdManager",
		"World",
		"CategoryData",
		"ExperienceData",
		"EffectHandler",
		"EnchantSkillGroupsData",
		"SkillTreeData",
		"SkillData",
		"ItemData",
		"EnchantItemOptionsData",
		"OptionData",
		"RecipeData",
		"ClassListData",
		"PlayerTemplateData",
		"AdminData",
		"CharInfoTable",
		"ClanTable",
		"CHSiegeManager",
		"ClanHallTable",
		"ClanHallAuctionManager",
		"GeoEngine",
		"SkillLearnData",
		"NpcData",
		"CastleManager.loadInstances",
		"InstanceManager",
		"ZoneManager",
		"GrandBossManager.initZones",
		"TerritoryWarManager",
		"Hero",
		"SevenSigns",
		"PartyMatchWaitingList",
		"PartyMatchRoomList",
		"CursedWeaponsManager",
		"RecipeManager",
		"OlympiadManager");
	private static final List<String> TRANSITIVE_SINGLETONS = List.of(
		"ScriptEngine(effect-master-only)",
		"DatabaseIdManager(via-IdManager)",
		"ForumsBBSManager(via-ClanTable)",
		"GrandBossManager(via-ZoneManager)",
		"WalkingManager(via-TerritoryWarManager)");

	private PhantomHeadlessPlayerFixture _primary;
	private PhantomHeadlessPlayerFixture _observer;
	private String _accountName;
	private final Set<Integer> _ownedObjectIds = new HashSet<>();
	private final Set<Integer> _additional058FixtureSlots = new HashSet<>();
	private Set<Long> _environmentThreadIds = Set.of();

	public void initialize(PhantomTestContext context) throws Exception
	{
		initialize(context, Long.getLong("phantom.test.minimumFestivalStartMillis", 0L));
	}

	/** Native infrastructure only: never deletes, recreates or loads the persisted TEST fixture. */
	public static BootstrapResult initializeNativeInfrastructure(PhantomTestContext context) throws Exception
	{
		return initializeNativeInfrastructure(context, 0L);
	}

	private static BootstrapResult initializeNativeInfrastructure(PhantomTestContext context, long minimumSevenSignsFestivalManagerStartMillis) throws Exception
	{
		final Path workingDirectory = Path.of("").toAbsolutePath().normalize();
		final Path expectedWorkingDirectory = context.moduleRoot().resolve("dist/game").normalize();
		if (!PhantomTask066PersistentLane.enabled() && !PhantomTask065PersistentLane.enabled() && !PhantomTask064PersistentLane.enabled() && !PhantomCampaign058PersistentLane.enabled() && !PhantomCampaign058DatabaseLane.enabled() && !PhantomContracts024DatabaseLane.enabled() && !PhantomContracts025DatabaseLane.enabled() && !owned026Enabled())
		{
			PhantomAssertions.assertEquals(expectedWorkingDirectory, workingDirectory, "Headless integration JVM must run from dist/game.");
		}

		if (PhantomTask066PersistentLane.enabled())
		{
			PhantomTask066PersistentLane.validate(context.moduleRoot(), Path.of(System.getProperty("phantom.test.config", "")));
		}
		else if (PhantomTask065PersistentLane.enabled())
		{
			PhantomTask065PersistentLane.validate(context.moduleRoot(), Path.of(System.getProperty("phantom.test.config", "")));
		}
		else if (PhantomTask064PersistentLane.enabled())
		{
			PhantomTask064PersistentLane.validate(context.moduleRoot(), Path.of(System.getProperty("phantom.test.config", "")));
		}
		else if (PhantomCampaign058PersistentLane.enabled())
		{
			PhantomCampaign058PersistentLane.validateFiles(context.moduleRoot(), Path.of(System.getProperty("phantom.test.config", "")), true);
		}
		ConfigLoader.init();
		PhantomAssertions.assertTrue(minimumSevenSignsFestivalManagerStartMillis >= 0L, "Seven Signs Festival manager start override cannot be negative.");
		FeatureConfig.ALT_FESTIVAL_MANAGER_START = Math.max(FeatureConfig.ALT_FESTIVAL_MANAGER_START, minimumSevenSignsFestivalManagerStartMillis);

		final String configProperty = System.getProperty("phantom.test.config");
		if ((configProperty == null) || configProperty.isBlank())
		{
			throw new PhantomTestConfigurationException("Explicit Phantom test database config path is missing.");
		}
		final BootstrapResult bootstrap;
		if (PhantomTask066PersistentLane.enabled())
		{
			PhantomTask066PersistentLane.initialize(context.moduleRoot(), Path.of(configProperty));
			bootstrap = null;
		}
		else if (PhantomTask065PersistentLane.enabled())
		{
			PhantomTask065PersistentLane.initialize(context.moduleRoot(), Path.of(configProperty));
			bootstrap = null;
		}
		else if (PhantomTask064PersistentLane.enabled())
		{
			PhantomTask064PersistentLane.initialize(context.moduleRoot(), Path.of(configProperty));
			bootstrap = null;
		}
		else if (PhantomCampaign058PersistentLane.enabled())
		{
			PhantomCampaign058PersistentLane.initialize(context.moduleRoot(), Path.of(configProperty));
			bootstrap = null;
		}
		else if (PhantomCampaign058DatabaseLane.enabled())
		{
			PhantomCampaign058DatabaseLane.initialize(context.moduleRoot(), Path.of(configProperty));
			bootstrap = null;
		}
		else if (owned026Enabled())
		{
			initializeOwned026(context.moduleRoot(), Path.of(configProperty));
			bootstrap = null;
		}
		else if (PhantomContracts025DatabaseLane.enabled())
		{
			PhantomContracts025DatabaseLane.initialize(context.moduleRoot(), Path.of(configProperty));
			bootstrap = null;
		}
		else if (PhantomContracts024DatabaseLane.enabled())
		{
			PhantomContracts024DatabaseLane.initialize(context.moduleRoot(), Path.of(configProperty));
			bootstrap = null; // This explicit private clone lane has no shared TEST settings.
		}
		else
		{
			bootstrap = PhantomTestDatabaseBootstrap.initialize(context.moduleRoot(), Path.of(configProperty));
			PhantomAssertions.assertEquals(PhantomTestDatabaseGuard.TARGET_USER, bootstrap.settings().login(), "Headless suite does not use the dedicated allowlisted user.");
		}

		ThreadPool.init();
		IdManager.getInstance();
		World.getInstance();
		CategoryData.getInstance();
		ExperienceData.getInstance();
		EffectHandler.getInstance().executeScript();
		EnchantSkillGroupsData.getInstance();
		SkillTreeData.getInstance();
		SkillData.getInstance();
		ItemData.getInstance();
		EnchantItemOptionsData.getInstance();
		OptionData.getInstance();
		RecipeData.getInstance();
		ClassListData.getInstance();
		PlayerTemplateData.getInstance();
		AdminData.getInstance();
		CharInfoTable.getInstance();
		ClanTable.getInstance();
		CHSiegeManager.getInstance();
		ClanHallTable.getInstance();
		ClanHallAuctionManager.getInstance();
		GeoEngine.getInstance();
		SkillLearnData.getInstance();
		NpcData.getInstance();
		CastleManager.getInstance().loadInstances();
		InstanceManager.getInstance();
		ZoneManager.getInstance();
		GrandBossManager.getInstance().initZones();
		TerritoryWarManager.getInstance();
		Hero.getInstance();
		SevenSigns.getInstance();
		PartyMatchWaitingList.getInstance();
		PartyMatchRoomList.getInstance();
		CursedWeaponsManager.getInstance();
		RecipeManager.getInstance();
		OlympiadManager.getInstance();
		return bootstrap;
	}

	public void initialize(PhantomTestContext context, long minimumSevenSignsFestivalManagerStartMillis) throws Exception
	{
		if (PhantomTask066PersistentLane.enabled() || PhantomTask065PersistentLane.enabled() || PhantomTask064PersistentLane.enabled() || PhantomCampaign058PersistentLane.enabled())
		{
			throw new IllegalArgumentException("Persistent candidate forbids mutable fixture initialization/cleanup; use infrastructure only.");
		}
		final BootstrapResult bootstrap = initializeNativeInfrastructure(context, minimumSevenSignsFestivalManagerStartMillis);
		_accountName = "phantom_t004_" + context.seed();
		cleanupOwnedFixtures();
		if (Boolean.getBoolean("phantom.test.freshIdentities059")) { reserveFreshFixtureIds059(context); }
		insertOwnedAccount();
		_primary = createFixture("PhT004A" + stableSuffix(context.seed()), PRIMARY_ITEM_BASELINE);
		_observer = createFixture("PhT004B" + stableSuffix(context.seed()), OBSERVER_ITEM_BASELINE);

		stabilizeInfrastructureThreads();
		_environmentThreadIds = liveNonDaemonThreadIds();
		context.record("headless.database", owned026Enabled() ? System.getProperty("phantom.contract040.manifest") != null ? "l2jmobiush5_phantom_test_040t" : System.getProperty("phantom.contract039.manifest") != null ? "l2jmobiush5_localplay_contract039t" : System.getProperty("phantom.contract038.manifest") != null ? "l2jmobiush5_localplay_contract038t" : System.getProperty("phantom.contract037.manifest") != null ? "l2jmobiush5_localplay_contract037t" : System.getProperty("phantom.contract036.manifest") != null ? "l2jmobiush5_localplay_contract036t" : System.getProperty("phantom.contract035.manifest") != null ? "l2jmobiush5_localplay_contract035t" : System.getProperty("phantom.travel033r.manifest") != null ? "l2jmobiush5_localplay_travel033rt" : System.getProperty("phantom.contract032.manifest") != null ? "l2jmobiush5_localplay_contract032t" : System.getProperty("phantom.contract031.manifest") != null ? "l2jmobiush5_localplay_contract031t" : "l2jmobiush5_localplay_contract" + (System.getProperty("phantom.contract027.manifest") != null ? "027" : "026") + "a" : PhantomContracts025DatabaseLane.enabled() ? "l2jmobiush5_localplay_contract025b" : PhantomContracts024DatabaseLane.enabled()
			? "l2jmobiush5_localplay_contract024a" : PhantomTestDatabaseGuard.TARGET_DATABASE);
		if (System.getProperty("phantom.contract041.manifest") != null)
		{
			context.record("headless.database", "l2jmobiush5_phantom_test_041t");
		}
		if (System.getProperty("phantom.contract042.manifest") != null)
		{
			context.record("headless.database", "l2jmobiush5_phantom_test_042b");
		}
		if (System.getProperty("phantom.contract043.manifest") != null)
		{
			context.record("headless.database", "l2jmobiush5_phantom_test_043a");
		}
		if (System.getProperty("phantom.contract044.manifest") != null)
		{
			context.record("headless.database", "l2jmobiush5_phantom_test_044a");
		}
		if (System.getProperty("phantom.contract045.manifest") != null)
		{
			context.record("headless.database", "l2jmobiush5_phantom_test_045a");
		}
		if (System.getProperty("phantom.contract049.manifest") != null)
		{
			context.record("headless.database", "l2jmobiush5_phantom_test_049a");
		}
		if (System.getProperty("phantom.contract053.manifest") != null)
		{
			context.record("headless.database", "l2jmobiush5_phantom_test_053a");
		}
		if (PhantomCampaign058DatabaseLane.enabled())
		{
			context.record("headless.database", PhantomCampaign058DatabaseLane.databaseName());
		}
		context.record("headless.schemaAggregateSha256", ((PhantomCampaign058DatabaseLane.enabled() || PhantomContracts024DatabaseLane.enabled() || PhantomContracts025DatabaseLane.enabled() || owned026Enabled())
			? PhantomTestSchemaManifest.current(context.moduleRoot()) : bootstrap.schemaSnapshot()).aggregateSha256());
		context.record("headless.initializedSingletonCount", INITIALIZED_SINGLETONS.size());
		context.record("headless.initializedSingletons", String.join(",", INITIALIZED_SINGLETONS));
		context.record("headless.transitiveSingletonCount", TRANSITIVE_SINGLETONS.size());
		context.record("headless.transitiveSingletons", String.join(",", TRANSITIVE_SINGLETONS));
		context.record("headless.primaryObjectId", _primary.objectId());
		context.record("headless.observerObjectId", _observer.objectId());
	}

	/** New fixtures must not reuse identities still referenced by retained failed fixture profiles. */
	private static void reserveFreshFixtureIds059(PhantomTestContext context) throws Exception
	{
		PhantomCampaign058DatabaseLane.assertActiveFixture(context);
		final int highWater;
		try (Connection connection = DatabaseFactory.getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT COALESCE(MAX(character_object_id),0) FROM phantom_profiles"); ResultSet rows = statement.executeQuery())
		{
			PhantomAssertions.assertTrue(rows.next(), "Fixture profile identity high-water query returned no row.");
			highWater = rows.getInt(1);
		}
		if (highWater == 0) { return; }
		int count = 0; int last = 0;
		while (last <= highWater && count < 524288) { last = IdManager.getInstance().getNextId(); count++; }
		PhantomAssertions.assertTrue(last > highWater, "Fresh TEST identity reservation exceeded its declared bound; retained profiles stay untouched.");
		context.record("fixture059.newIdentityWindow", "stock IdManager allocations=" + count + "; last=" + last + "; retainedProfileHighWater=" + highWater + "; profile/database writes=0; no orphan deletion");
	}

	private static boolean owned026Enabled()
	{
		if (System.getProperty("phantom.contract053.manifest") != null) { return true; }
		if ((System.getProperty("phantom.contract049.manifest") != null) || (System.getProperty("phantom.contract041.manifest") != null) || (System.getProperty("phantom.contract042.manifest") != null) || (System.getProperty("phantom.contract043.manifest") != null) || (System.getProperty("phantom.contract044.manifest") != null) || (System.getProperty("phantom.contract045.manifest") != null))
		{
			return true;
		}
		return (System.getProperty("phantom.contract040.manifest") != null) || (System.getProperty("phantom.contract026.manifest") != null) || (System.getProperty("phantom.contract027.manifest") != null) || (System.getProperty("phantom.contract031.manifest") != null) || (System.getProperty("phantom.contract032.manifest") != null) || (System.getProperty("phantom.travel033r.manifest") != null) || (System.getProperty("phantom.contract035.manifest") != null) || (System.getProperty("phantom.contract036.manifest") != null) || (System.getProperty("phantom.contract037.manifest") != null) || (System.getProperty("phantom.contract038.manifest") != null) || (System.getProperty("phantom.contract039.manifest") != null);
	}

	/** Same exact export/config/catalog guard as TASK025; shared TEST metadata remains untouched. */
	private static void initializeOwned026(Path module, Path config) throws Exception
	{
		final Path root = module.toRealPath();
		final boolean task027 = System.getProperty("phantom.contract027.manifest") != null;
		PhantomAssertions.assertFalse(task027 && (System.getProperty("phantom.contract026.manifest") != null), "Only one exact owned lane may be selected.");
		final boolean task031 = System.getProperty("phantom.contract031.manifest") != null;
		PhantomAssertions.assertFalse(task031 && (task027 || System.getProperty("phantom.contract026.manifest") != null), "Only one exact owned lane may be selected.");
		final boolean task032 = System.getProperty("phantom.contract032.manifest") != null;
		PhantomAssertions.assertFalse(task032 && (task031 || task027 || System.getProperty("phantom.contract026.manifest") != null), "Only one exact owned lane may be selected.");
		final boolean task033r = System.getProperty("phantom.travel033r.manifest") != null;
		PhantomAssertions.assertFalse(task033r && (task032 || task031 || task027 || System.getProperty("phantom.contract026.manifest") != null), "Only one exact owned lane may be selected.");
		// TASK035 writes only its fresh exact clone; prior task lanes remain outside its authority.
		final boolean task035 = System.getProperty("phantom.contract035.manifest") != null;
		PhantomAssertions.assertFalse(task035 && (task033r || task032 || task031 || task027 || System.getProperty("phantom.contract026.manifest") != null), "Only one exact owned lane may be selected.");
		final boolean task036 = System.getProperty("phantom.contract036.manifest") != null;
		PhantomAssertions.assertFalse(task036 && (task035 || task033r || task032 || task031 || task027 || System.getProperty("phantom.contract026.manifest") != null), "Only one exact owned lane may be selected.");
		final boolean task037 = System.getProperty("phantom.contract037.manifest") != null;
		PhantomAssertions.assertFalse(task037 && (task036 || task035 || task033r || task032 || task031 || task027 || System.getProperty("phantom.contract026.manifest") != null), "Only one exact owned lane may be selected.");
		final boolean task038 = System.getProperty("phantom.contract038.manifest") != null;
		PhantomAssertions.assertFalse(task038 && (task037 || task036 || task035 || task033r || task032 || task031 || task027 || System.getProperty("phantom.contract026.manifest") != null), "Only one exact owned lane may be selected.");
		final boolean task039 = System.getProperty("phantom.contract039.manifest") != null;
		PhantomAssertions.assertFalse(task039 && (task038 || task037 || task036 || task035 || task033r || task032 || task031 || task027 || System.getProperty("phantom.contract026.manifest") != null), "Only one exact owned lane may be selected.");
		final boolean task040 = System.getProperty("phantom.contract040.manifest") != null;
		PhantomAssertions.assertFalse(task040 && (task039 || task038 || task037 || task036 || task035 || task033r || task032 || task031 || task027 || System.getProperty("phantom.contract026.manifest") != null), "Only one exact owned lane may be selected.");
		final boolean task041 = System.getProperty("phantom.contract041.manifest") != null;
		PhantomAssertions.assertFalse(task041 && (task040 || task039 || task038 || task037 || task036 || task035 || task033r || task032 || task031 || task027 || System.getProperty("phantom.contract026.manifest") != null), "Only one exact owned lane may be selected.");
		// TASK042 uses its fresh b clone for tests before the normal LIFE boot; old lanes stay read-only.
		final boolean task042 = System.getProperty("phantom.contract042.manifest") != null;
		PhantomAssertions.assertFalse(task042 && (task041 || task040 || task039 || task038 || task037 || task036 || task035 || task033r || task032 || task031 || task027 || System.getProperty("phantom.contract026.manifest") != null), "Only one exact owned lane may be selected.");
		// TASK043 runs current tests in its new a clone before the normal LIFE scene.
		final boolean task043 = System.getProperty("phantom.contract043.manifest") != null;
		PhantomAssertions.assertFalse(task043 && (task042 || task041 || task040 || task039 || task038 || task037 || task036 || task035 || task033r || task032 || task031 || task027 || System.getProperty("phantom.contract026.manifest") != null), "Only one exact owned lane may be selected.");
		final boolean task044 = System.getProperty("phantom.contract044.manifest") != null;
		PhantomAssertions.assertFalse(task044 && (task043 || task042 || task041 || task040 || task039 || task038 || task037 || task036 || task035 || task033r || task032 || task031 || task027 || System.getProperty("phantom.contract026.manifest") != null), "Only one exact owned lane may be selected.");
		final boolean task045 = System.getProperty("phantom.contract045.manifest") != null;
		PhantomAssertions.assertFalse(task045 && (task044 || task043 || task042 || task041 || task040 || task039 || task038 || task037 || task036 || task035 || task033r || task032 || task031 || task027 || System.getProperty("phantom.contract026.manifest") != null), "Only one exact owned lane may be selected.");
		final boolean task049 = System.getProperty("phantom.contract049.manifest") != null;
		PhantomAssertions.assertFalse(task049 && (task045 || task044 || task043 || task042 || task041 || task040 || task039 || task038 || task037 || task036 || task035 || task033r || task032 || task031 || task027 || System.getProperty("phantom.contract026.manifest") != null), "Only one exact owned lane may be selected.");
		final boolean task053 = System.getProperty("phantom.contract053.manifest") != null;
		PhantomAssertions.assertFalse(task053 && (task049 || task045 || task044 || task043 || task042 || task041 || task040 || task039 || task038 || task037 || task036 || task035 || task033r || task032 || task031 || task027 || System.getProperty("phantom.contract026.manifest") != null), "Only one exact owned lane may be selected.");
		final String contract = task053 ? "053" : task049 ? "049" : task045 ? "045" : task044 ? "044" : task043 ? "043" : task042 ? "042" : task041 ? "041" : task040 ? "040" : task039 ? "039" : task038 ? "038" : task037 ? "037" : task036 ? "036" : task035 ? "035" : task033r ? "033R" : task032 ? "032" : task031 ? "031" : task027 ? "027" : "026";
		final String episode = task053 || task049 || task045 || task044 || task043 ? "a" : task042 ? "b" : task041 || task040 || task039 || task038 || task037 || task036 || task035 || task033r || task032 || task031 ? "t" : "a";
		final String database = task053 || task049 || task045 || task044 || task043 || task042 || task041 || task040 ? "l2jmobiush5_phantom_test_" + contract + episode : "l2jmobiush5_localplay_" + (task033r ? "travel033rt" : "contract" + contract + episode);
		final String laneName = task033r ? "travel033rt" : "contract" + contract + episode;
		final Path lane = root.resolve(".phantom-local/" + laneName).toRealPath();
		final Path manifest = Path.of(System.getProperty(task033r ? "phantom.travel033r.manifest" : "phantom.contract" + contract + ".manifest")).toRealPath();
		PhantomAssertions.assertEquals(lane.resolve("test/owned.properties"), manifest, "Exact owned TASK026 manifest required.");
		PhantomAssertions.assertEquals(lane.resolve("test/Database.test.ini"), config.toRealPath(), "Exact owned TASK026 config required.");
		PhantomAssertions.assertEquals(lane.resolve("runtime/game"), Path.of("").toRealPath(), "Exact owned TASK026 runtime required.");
		final var ownership = new java.util.Properties();
		final var settings = new java.util.Properties();
		try (var reader = java.nio.file.Files.newBufferedReader(manifest)) { ownership.load(reader); }
		try (var reader = java.nio.file.Files.newBufferedReader(config)) { settings.load(reader); }
		PhantomAssertions.assertEquals("TASK" + contract + "_CONTRACT", ownership.getProperty("owner"), "Exact task authority required.");
		PhantomAssertions.assertEquals(database, ownership.getProperty("database"), "Exact task catalog required.");
		final var digest = java.security.MessageDigest.getInstance("SHA-256");
		try (var input = java.nio.file.Files.newInputStream(lane.resolve("play-snapshot.sql")))
		{
			final byte[] buffer = new byte[65536]; int count;
			while ((count = input.read(buffer)) > 0) { digest.update(buffer, 0, count); }
		}
		PhantomAssertions.assertEquals(ownership.getProperty("exportSha256"), java.util.HexFormat.of().formatHex(digest.digest()), "Immutable TASK026 export changed.");
		final String url = settings.getProperty("URL");
		PhantomAssertions.assertTrue(url != null && url.matches("jdbc:(mysql|mariadb)://127\\.0\\.0\\.1:3308/" + database + "\\?.*"), "Only the exact task TEST clone may be written.");
		PhantomAssertions.assertEquals("4", settings.getProperty("MaximumDatabaseConnections"), "Bounded TEST pool required.");
		DatabaseFactory.initFromConfig(config.toString());
		try (var connection = DatabaseFactory.getConnection(); var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT DATABASE()"))
		{
			PhantomAssertions.assertTrue(rows.next(), "Owned TASK026 catalog unavailable.");
			PhantomAssertions.assertEquals(ownership.getProperty("database"), rows.getString(1), "Connected TEST catalog differs.");
		}
	}

	private static String stableSuffix(long seed)
	{
		return String.format("%05d", Math.floorMod(seed, 100000));
	}

	private void insertOwnedAccount() throws Exception
	{
		if (PhantomCampaign058DatabaseLane.enabled())
		{
			try (Connection connection = DatabaseFactory.getConnection(); PreparedStatement query = connection.prepareStatement("SELECT password,accessLevel FROM accounts WHERE login=?"))
			{
				query.setString(1, _accountName);
				try (ResultSet row = query.executeQuery())
				{
					if (row.next())
					{
						PhantomAssertions.assertTrue((row.getString(1) == null) && (row.getInt(2) == 0), "Retained058t account differs from its ordinary TEST input.");
						return;
					}
				}
			}
		}
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("INSERT INTO accounts (login, password, accessLevel, lastServer) VALUES (?, NULL, 0, 1)"))
		{
			statement.setString(1, _accountName);
			PhantomAssertions.assertEquals(1, statement.executeUpdate(), "Could not insert the owned Task 004 account.");
		}
	}

	private PhantomHeadlessPlayerFixture createFixture(String characterName, long itemBaseline) throws Exception
	{
		return createFixture(characterName, itemBaseline, 0, false);
	}

	private PhantomHeadlessPlayerFixture createFixture(String characterName, long itemBaseline, int classId, boolean female) throws Exception
	{
		final PlayerTemplate template = PlayerTemplateData.getInstance().getTemplate(classId);
		PhantomAssertions.assertTrue(template != null, "Human Fighter template 0 is unavailable.");

		final Player player = Player.create(template, _accountName, characterName, new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, female));
		PhantomAssertions.assertTrue(player != null, "Canonical Player.create failed for " + characterName + ".");

		final Location creationPoint = template.getCreationPoint();
		player.setXYZInvisible(creationPoint.getX(), creationPoint.getY(), creationPoint.getZ());
		player.setCurrentHp(player.getMaxHp());
		player.setCurrentMp(player.getMaxMp());

		final Skill skill = SkillData.getInstance().getSkill(PERSISTED_SKILL_ID, PERSISTED_SKILL_LEVEL);
		PhantomAssertions.assertTrue(skill != null, "Persisted fixture skill is unavailable.");
		player.addSkill(skill, true);
		PhantomAssertions.assertTrue(player.getInventory().addItem(ItemProcessType.REWARD, PhantomActionFacade.FIXTURE_ITEM_ID, itemBaseline, null, this) != null, "Could not create persisted fixture inventory.");

		player.setOnlineStatus(true, false);
		player.stopAllTasks();
		player.storeMe();
		player.deleteMe();

		final PhantomHeadlessPlayerFixture fixture = new PhantomHeadlessPlayerFixture(_accountName, characterName, player.getObjectId(), PERSISTED_SKILL_ID, itemBaseline);
		_ownedObjectIds.add(fixture.objectId());
		assertDatabaseOnline(fixture.objectId(), 0);
		assertDatabaseFixtureItemCount(fixture.objectId(), itemBaseline);
		return fixture;
	}

	public void shutdown() throws Exception
	{
		Throwable cleanupFailure = null;
		try
		{
			if (DatabaseFactory.isInitialized())
			{
				cleanupOwnedFixtures();
				assertFinalFixtureResidueZero();
			}
		}
		catch (Throwable throwable)
		{
			cleanupFailure = throwable;
		}
		finally
		{
			ThreadPool.shutdown();
			DatabaseFactory.close();
		}

		final long deadline = System.nanoTime() + 5_000_000_000L;
		while ((System.nanoTime() < deadline) && hasInfrastructureThread())
		{
			Thread.sleep(25);
		}
		final boolean infrastructureRemaining = hasInfrastructureThread();
		PhantomAssertions.assertFalse(infrastructureRemaining, "Headless suite left Hikari or L2jMobius infrastructure threads." + (infrastructureRemaining ? infrastructureThreadDiagnostic() : ""));

		if (cleanupFailure != null)
		{
			if (cleanupFailure instanceof Exception exception)
			{
				throw exception;
			}
			throw new RuntimeException(cleanupFailure);
		}
	}

	public PhantomHeadlessPlayerFixture primary()
	{
		return _primary;
	}

	public PhantomHeadlessPlayerFixture observer()
	{
		return _observer;
	}

	/** Two predeclared TASK061 native race fixtures, allocated/cleaned by the existing account owner. */
	public PhantomHeadlessPlayerFixture createCampaign061CareerCompanion(PhantomTestContext context, int slot) throws Exception
	{
		if (context.seed() != 36003601 || !Boolean.getBoolean("phantom.test.autoProfessions061") || slot < 1 || slot > 2 || _primary == null
			|| !_accountName.equals("phantom_t004_" + context.seed()) || !_additional058FixtureSlots.add(400 + slot)) { throw new IllegalArgumentException("Exact TASK061 career fixture required."); }
		PhantomCampaign058DatabaseLane.assertActiveFixture(context);
		return createFixture("PhT061C" + slot + stableSuffix(context.seed()), PRIMARY_ITEM_BASELINE, slot == 1 ? 53 : 124, slot == 2);
	}

	/** At most two extra companions, under the same strictly owned058t account and cleanup. */
	public PhantomHeadlessPlayerFixture createCampaign058Companion(PhantomTestContext context, int slot) throws Exception
	{
		if ((slot < 1) || (slot > 2) || (_primary == null) || !_accountName.equals("phantom_t004_" + context.seed()) || _additional058FixtureSlots.contains(slot))
		{
			throw new IllegalArgumentException("Additional058 companion slot or account is invalid.");
		}
		PhantomCampaign058DatabaseLane.assertActiveFixture(context);
		_additional058FixtureSlots.add(slot);
		return createFixture("PhT058" + slot + stableSuffix(context.seed()), PRIMARY_ITEM_BASELINE);
	}

	/** Exact058t invitation-capacity fixture only; the companion limit remains two. */
	public PhantomHeadlessPlayerFixture createCampaign058InvitationMember(PhantomTestContext context, int slot) throws Exception
	{
		if ((context.seed() != 58005827L) || (slot < 1) || (slot > 8) || (_primary == null) || !_accountName.equals("phantom_t004_" + context.seed()) || _additional058FixtureSlots.contains(100 + slot))
		{
			throw new IllegalArgumentException("Exact058 invitation fixture identity/capacity required.");
		}
		PhantomCampaign058DatabaseLane.assertActiveFixture(context);
		_additional058FixtureSlots.add(100 + slot);
		return createFixture("PhT058I" + slot + stableSuffix(context.seed()), PRIMARY_ITEM_BASELINE);
	}

	/** Exact existing058t raid059 episode only; ordinary companion and invitation limits are unchanged. */
	public PhantomHeadlessPlayerFixture createCampaign059RaidCompanion(PhantomTestContext context, int slot) throws Exception
	{
		if ((context.seed() != 58005830L) || !Boolean.getBoolean("phantom.test.raidFlow059") || (slot < 1) || (slot > 7) || (_primary == null) || !_accountName.equals("phantom_t004_" + context.seed()) || _additional058FixtureSlots.contains(200 + slot))
		{
			throw new IllegalArgumentException("Exact059 raid fixture identity/capacity required.");
		}
		PhantomCampaign058DatabaseLane.assertActiveFixture(context);
		_additional058FixtureSlots.add(200 + slot);
		return createFixture("PhT058R" + slot + stableSuffix(context.seed()), PRIMARY_ITEM_BASELINE);
	}

	/** Exact six-member Kamaloka059 fixture; ordinary companion limits remain unchanged. */
	public PhantomHeadlessPlayerFixture createCampaign059InstanceCompanion(PhantomTestContext context, int slot) throws Exception
	{
		if (context.seed() != 58005831L || !Boolean.getBoolean("phantom.test.kamalokaFlow059") || slot < 1 || slot > 4 || _primary == null || !_accountName.equals("phantom_t004_" + context.seed()) || _additional058FixtureSlots.contains(300 + slot)) { throw new IllegalArgumentException("Exact059 Kamaloka fixture identity/capacity required."); }
		PhantomCampaign058DatabaseLane.assertActiveFixture(context);
		_additional058FixtureSlots.add(300 + slot);
		return createFixture("PhT058K" + slot + stableSuffix(context.seed()), PRIMARY_ITEM_BASELINE);
	}

	public void cleanupLoadedPlayer(Player player)
	{
		if (player == null)
		{
			return;
		}
		player.stopAllTasks();
		player.storeMe();
		player.deleteMe();
	}

	public void assertClean(PhantomHeadlessPlayerFixture fixture, Player retainedPlayer) throws Exception
	{
		PhantomAssertions.assertEquals(null, World.getInstance().getPlayer(fixture.objectId()), "World retained the fixture Player.");
		PhantomAssertions.assertEquals(null, PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(fixture.objectId()), "Identity registry retained the fixture lease.");
		assertDatabaseOnline(fixture.objectId(), 0);
		assertDatabaseFixtureItemCount(fixture.objectId(), fixture.fixtureItemBaseline());
		PhantomAssertions.assertFalse(isAutosaveMember(fixture.objectId()), "Autosave retained the fixture Player.");

		if (retainedPlayer != null)
		{
			PhantomAssertions.assertEquals(null, retainedPlayer.getClient(), "Cleanup retained a GameClient.");
			PhantomAssertions.assertFalse(retainedPlayer.hasHeadlessOutboundSession(), "Cleanup retained headless output.");
			PhantomAssertions.assertFalse(retainedPlayer.isInParty(), "Cleanup retained party membership.");
			PhantomAssertions.assertEquals(null, retainedPlayer.getActiveRequester(), "Cleanup retained an active requester.");
			PhantomAssertions.assertEquals(null, retainedPlayer.getActiveTradeList(), "Cleanup retained an active trade.");
			PhantomAssertions.assertEquals(0, retainedPlayer.getInstanceId(), "Cleanup retained instance ownership.");
			assertFutureResidueTerminal(retainedPlayer);
		}
		assertNoNewNonDaemonThreads();
	}

	public void assertDatabaseOnline(int objectId, int expected) throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("SELECT online FROM characters WHERE charId=?"))
		{
			statement.setInt(1, objectId);
			try (ResultSet result = statement.executeQuery())
			{
				PhantomAssertions.assertTrue(result.next(), "Fixture character row is missing.");
				PhantomAssertions.assertEquals(expected, result.getInt(1), "Unexpected fixture online value.");
			}
		}
	}

	public void assertDatabaseFixtureItemCount(int objectId, long expected) throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("SELECT COALESCE(SUM(count), 0) FROM items WHERE owner_id=? AND item_id=?"))
		{
			statement.setInt(1, objectId);
			statement.setInt(2, PhantomActionFacade.FIXTURE_ITEM_ID);
			try (ResultSet result = statement.executeQuery())
			{
				PhantomAssertions.assertTrue(result.next(), "Fixture item count query returned no row.");
				PhantomAssertions.assertEquals(expected, result.getLong(1), "Fixture item conservation failed in database.");
			}
		}
	}

	public static boolean isAutosaveMember(int objectId) throws Exception
	{
		final Field field = PlayerAutoSaveTaskManager.class.getDeclaredField("PLAYER_TIMES");
		field.setAccessible(true);
		final Map<?, ?> players = (Map<?, ?>) field.get(null);
		for (Object key : players.keySet())
		{
			if ((key instanceof Player player) && (player.getObjectId() == objectId))
			{
				return true;
			}
		}
		return false;
	}

	private static void assertFutureResidueTerminal(Player player) throws Exception
	{
		PhantomAssertions.assertEquals(List.of(), liveFutureFields(player), "Cleanup retained live Player futures.");
	}

	static List<String> liveFutureFields(Player player)
	{
		final List<String> liveFields = new ArrayList<>();
		try
		{
			for (Class<?> type = player.getClass(); type != null; type = type.getSuperclass())
			{
				for (Field field : type.getDeclaredFields())
				{
					if (!Future.class.isAssignableFrom(field.getType()))
					{
						continue;
					}
					field.setAccessible(true);
					final Future<?> future = (Future<?>) field.get(player);
					if ((future != null) && !future.isDone() && !future.isCancelled())
					{
						liveFields.add(type.getSimpleName() + "." + field.getName());
					}
				}
			}
		}
		catch (IllegalAccessException e)
		{
			throw new AssertionError("Could not inspect Player future ownership.", e);
		}
		return List.copyOf(liveFields);
	}

	private void assertNoNewNonDaemonThreads() throws Exception
	{
		final long deadline = System.nanoTime() + 2_000_000_000L;
		Set<Long> unexpected;
		do
		{
			unexpected = liveNonDaemonThreadIds();
			unexpected.removeAll(_environmentThreadIds);
			if (unexpected.isEmpty())
			{
				return;
			}
			Thread.sleep(25);
		}
		while (System.nanoTime() < deadline);
		PhantomAssertions.assertEquals(Set.of(), unexpected, "Lifecycle created a retained non-daemon thread.");
	}

	private static Set<Long> liveNonDaemonThreadIds()
	{
		final Set<Long> ids = new HashSet<>();
		for (Thread thread : Thread.getAllStackTraces().keySet())
		{
			if (thread.isAlive() && !thread.isDaemon())
			{
				ids.add(thread.threadId());
			}
		}
		return ids;
	}

	private static void stabilizeInfrastructureThreads() throws Exception
	{
		final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
		final CountDownLatch warmups = new CountDownLatch(2);
		ThreadPool.execute(warmups::countDown);
		final ScheduledFuture<?> scheduled = ThreadPool.schedule(warmups::countDown, 0);
		PhantomAssertions.assertTrue(scheduled != null, "Could not submit bounded scheduled ThreadPool warm-up work.");

		final int priorityWorkerCount = Math.max(1, Math.min(ThreadConfig.HIGH_PRIORITY_SCHEDULED_THREAD_POOL_SIZE, 64));
		final CountDownLatch priorityStarted = new CountDownLatch(priorityWorkerCount);
		final CountDownLatch priorityRelease = new CountDownLatch(1);
		final List<ScheduledFuture<?>> priorityWarmups = new ArrayList<>(priorityWorkerCount);
		for (int index = 0; index < priorityWorkerCount; index++)
		{
			final ScheduledFuture<?> future = ThreadPool.schedulePriorityTaskAtFixedRate(() ->
			{
				priorityStarted.countDown();
				try
				{
					priorityRelease.await();
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
				}
			}, 0, TimeUnit.MINUTES.toMillis(1));
			PhantomAssertions.assertTrue(future != null, "Could not submit bounded high-priority ThreadPool warm-up work.");
			priorityWarmups.add(future);
		}
		try
		{
			PhantomAssertions.assertTrue(awaitBeforeDeadline(warmups, deadline), "Instant/scheduled ThreadPool warm-up did not finish within the two-second stabilization budget.");
			PhantomAssertions.assertTrue(awaitBeforeDeadline(priorityStarted, deadline), "High-priority ThreadPool warm-up did not finish within the two-second stabilization budget.");
		}
		finally
		{
			priorityRelease.countDown();
			priorityWarmups.forEach(future -> future.cancel(false));
		}

		Set<InfrastructureThreadIdentity> previous = Set.of();
		int stableSamples = 0;
		while (System.nanoTime() < deadline)
		{
			final Set<InfrastructureThreadIdentity> current = liveInfrastructureThreadIdentities();
			stableSamples = current.equals(previous) ? stableSamples + 1 : 1;
			if (stableSamples >= 4)
			{
				return;
			}
			previous = current;
			Thread.sleep(25);
		}
		throw new AssertionError("Shared infrastructure thread names/IDs did not remain stable for four consecutive samples.");
	}

	private static boolean awaitBeforeDeadline(CountDownLatch latch, long deadline) throws InterruptedException
	{
		final long remaining = deadline - System.nanoTime();
		return (remaining > 0) && latch.await(remaining, TimeUnit.NANOSECONDS);
	}

	private static Set<InfrastructureThreadIdentity> liveInfrastructureThreadIdentities()
	{
		final Set<InfrastructureThreadIdentity> identities = new HashSet<>();
		for (Thread thread : Thread.getAllStackTraces().keySet())
		{
			if (thread.isAlive() && !thread.isDaemon() && (thread.getName().startsWith("L2jMobius ") || thread.getName().startsWith("L2JMobiusPool")))
			{
				identities.add(new InfrastructureThreadIdentity(thread.threadId(), thread.getName()));
			}
		}
		return identities;
	}

	private void cleanupOwnedFixtures() throws Exception
	{
		final List<Integer> objectIds = new ArrayList<>();
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement(PhantomCampaign058DatabaseLane.enabled() ? "SELECT charId FROM characters c WHERE account_name=? AND NOT EXISTS (SELECT 1 FROM phantom_profiles p WHERE p.character_object_id=c.charId)" : "SELECT charId FROM characters WHERE account_name=?"))
		{
			statement.setString(1, _accountName);
			try (ResultSet result = statement.executeQuery())
			{
				while (result.next())
				{
					final int objectId = result.getInt(1);
					objectIds.add(objectId);
					_ownedObjectIds.add(objectId);
				}
			}
		}

		for (int objectId : objectIds)
		{
			final Player worldPlayer = World.getInstance().getPlayer(objectId);
			if (worldPlayer != null)
			{
				cleanupLoadedPlayer(worldPlayer);
			}
			GameClient.deleteCharByObjId(objectId);
		}

		// AUTHORITY059 preserves058t accounts; only owned character rows are reusable fixtures.
		if (PhantomCampaign058DatabaseLane.enabled()) { return; }
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("DELETE FROM accounts WHERE login=?"))
		{
			statement.setString(1, _accountName);
			statement.executeUpdate();
		}
	}

	private void assertFinalFixtureResidueZero() throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection())
		{
			PhantomAssertions.assertEquals(PhantomCampaign058DatabaseLane.enabled() ? 1L : 0L, count(connection, "SELECT COUNT(*) FROM accounts WHERE login=?", _accountName), "Owned account retention/cleanup contract differs.");
			PhantomAssertions.assertEquals(0L, count(connection, PhantomCampaign058DatabaseLane.enabled() ? "SELECT COUNT(*) FROM characters c WHERE account_name=? AND NOT EXISTS (SELECT 1 FROM phantom_profiles p WHERE p.character_object_id=c.charId)" : "SELECT COUNT(*) FROM characters WHERE account_name=?", _accountName), "Owned character residue remains.");
			for (int objectId : _ownedObjectIds)
			{
				// A retained profile owns this character across JVMs; fixture cleanup must not orphan it.
				if (PhantomCampaign058DatabaseLane.enabled() && count(connection, "SELECT COUNT(*) FROM phantom_profiles WHERE character_object_id=?", objectId) > 0) { continue; }
				PhantomAssertions.assertEquals(0L, count(connection, "SELECT COUNT(*) FROM items WHERE owner_id=?", objectId), "Owned item residue remains for " + objectId + ".");
			}
		}
		PhantomAssertions.assertEquals(0, PhantomIdentityLeaseRegistry.getInstance().getActiveLeaseCount(), "Identity registry is not empty after final fixture cleanup.");
	}

	private static long count(Connection connection, String sql, Object value) throws Exception
	{
		try (PreparedStatement statement = connection.prepareStatement(sql))
		{
			statement.setObject(1, value);
			try (ResultSet result = statement.executeQuery())
			{
				PhantomAssertions.assertTrue(result.next(), "Final fixture residue query returned no row.");
				return result.getLong(1);
			}
		}
	}

	private static boolean hasInfrastructureThread()
	{
		for (Thread thread : Thread.getAllStackTraces().keySet())
		{
			if (!thread.isAlive() || thread.isDaemon())
			{
				continue;
			}
			final String name = thread.getName();
			if (name.startsWith("L2jMobius ") || name.startsWith("L2JMobiusPool"))
			{
				return true;
			}
		}
		return false;
	}

	private static String infrastructureThreadDiagnostic()
	{
		final var detail = new StringBuilder(); int count = 0;
		for (var entry : Thread.getAllStackTraces().entrySet())
		{
			final var thread = entry.getKey();
			if (!thread.isAlive() || thread.isDaemon() || !(thread.getName().startsWith("L2jMobius ") || thread.getName().startsWith("L2JMobiusPool"))) { continue; }
			detail.append(" thread=").append(thread.getName()).append('/').append(thread.getState());
			for (int index = 0; index < Math.min(6, entry.getValue().length); index++) { detail.append(" -> ").append(entry.getValue()[index]); }
			if (++count == 4) { break; }
		}
		return detail.toString();
	}

	private record InfrastructureThreadIdentity(long id, String name)
	{
	}
}
