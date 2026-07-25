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
	private Set<Long> _environmentThreadIds = Set.of();

	public void initialize(PhantomTestContext context) throws Exception
	{
		final Path workingDirectory = Path.of("").toAbsolutePath().normalize();
		final Path expectedWorkingDirectory = context.moduleRoot().resolve("dist/game").normalize();
		PhantomAssertions.assertEquals(expectedWorkingDirectory, workingDirectory, "Headless integration JVM must run from dist/game.");

		ConfigLoader.init();

		final String configProperty = System.getProperty("phantom.test.config");
		if ((configProperty == null) || configProperty.isBlank())
		{
			throw new PhantomTestConfigurationException("Explicit Phantom test database config path is missing.");
		}
		final BootstrapResult bootstrap = PhantomTestDatabaseBootstrap.initialize(context.moduleRoot(), Path.of(configProperty));
		PhantomAssertions.assertEquals(PhantomTestDatabaseGuard.TARGET_USER, bootstrap.settings().login(), "Headless suite does not use the dedicated allowlisted user.");

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

		_accountName = "phantom_t004_" + context.seed();
		cleanupOwnedFixtures();
		insertOwnedAccount();
		_primary = createFixture("PhT004A" + stableSuffix(context.seed()), PRIMARY_ITEM_BASELINE);
		_observer = createFixture("PhT004B" + stableSuffix(context.seed()), OBSERVER_ITEM_BASELINE);

		stabilizeInfrastructureThreads();
		_environmentThreadIds = liveNonDaemonThreadIds();
		context.record("headless.database", PhantomTestDatabaseGuard.TARGET_DATABASE);
		context.record("headless.schemaAggregateSha256", bootstrap.schemaSnapshot().aggregateSha256());
		context.record("headless.initializedSingletonCount", INITIALIZED_SINGLETONS.size());
		context.record("headless.initializedSingletons", String.join(",", INITIALIZED_SINGLETONS));
		context.record("headless.transitiveSingletonCount", TRANSITIVE_SINGLETONS.size());
		context.record("headless.transitiveSingletons", String.join(",", TRANSITIVE_SINGLETONS));
		context.record("headless.primaryObjectId", _primary.objectId());
		context.record("headless.observerObjectId", _observer.objectId());
	}

	private static String stableSuffix(long seed)
	{
		return String.format("%05d", Math.floorMod(seed, 100000));
	}

	private void insertOwnedAccount() throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("INSERT INTO accounts (login, password, accessLevel, lastServer) VALUES (?, NULL, 0, 1)"))
		{
			statement.setString(1, _accountName);
			PhantomAssertions.assertEquals(1, statement.executeUpdate(), "Could not insert the owned Task 004 account.");
		}
	}

	private PhantomHeadlessPlayerFixture createFixture(String characterName, long itemBaseline) throws Exception
	{
		final PlayerTemplate template = PlayerTemplateData.getInstance().getTemplate(0);
		PhantomAssertions.assertTrue(template != null, "Human Fighter template 0 is unavailable.");

		final Player player = Player.create(template, _accountName, characterName, new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false));
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
		PhantomAssertions.assertFalse(hasInfrastructureThread(), "Headless suite left Hikari or L2jMobius infrastructure threads.");

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
		final List<String> liveFields = new ArrayList<>();
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
		PhantomAssertions.assertEquals(List.of(), liveFields, "Cleanup retained live Player futures.");
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
			PreparedStatement statement = connection.prepareStatement("SELECT charId FROM characters WHERE account_name=?"))
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
			PhantomAssertions.assertEquals(0L, count(connection, "SELECT COUNT(*) FROM accounts WHERE login=?", _accountName), "Owned account residue remains.");
			PhantomAssertions.assertEquals(0L, count(connection, "SELECT COUNT(*) FROM characters WHERE account_name=?", _accountName), "Owned character residue remains.");
			for (int objectId : _ownedObjectIds)
			{
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

	private record InfrastructureThreadIdentity(long id, String name)
	{
	}
}
