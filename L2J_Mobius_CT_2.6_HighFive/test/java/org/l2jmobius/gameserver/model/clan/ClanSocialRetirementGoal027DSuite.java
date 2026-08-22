/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.model.clan;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.model.clan.ClanAllianceService.AllianceIdentity;
import org.l2jmobius.gameserver.model.clan.ClanAllianceService.Actor;
import org.l2jmobius.gameserver.model.clan.ClanSocialDomainGoal027CSuite.AllianceStateAccess;
import org.l2jmobius.gameserver.model.clan.ClanSocialDomainGoal027CSuite.FakePersistence;
import org.l2jmobius.gameserver.model.clan.ClanSocialDomainGoal027CSuite.WarStateAccess;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomTestConfigurationException;
import org.l2jmobius.tests.phantoms.PhantomTestDatabaseBootstrap;
import org.l2jmobius.tests.phantoms.PhantomTestDatabaseGuard;
import org.l2jmobius.tests.phantoms.PhantomTestContext;
import org.l2jmobius.tests.phantoms.PhantomTestRegistry;
import org.l2jmobius.tests.phantoms.PhantomTestSuite;

public final class ClanSocialRetirementGoal027DSuite implements PhantomTestSuite
{
	private static final long SEED = 27002740L;
	private static final long NOW = 1_000_000L;
	private static final int RECYCLED_CLAN_ID = 190027401;
	private static final int FAILURE_CLAN_ID = 190027402;
	private static final String SEQUENCE_NAME = "alliance_incarnation";
	private static final String HELD_SEQUENCE_NAME = "alliance_incarnation_027d_hold";

	@Override
	public String id()
	{
		return "clan-social-retirement-goal027d";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Goal 027D suite used the wrong deterministic seed.");
		final String configProperty = System.getProperty("phantom.test.config");
		if ((configProperty == null) || configProperty.isBlank())
		{
			throw new PhantomTestConfigurationException("Explicit Phantom test database config path is missing.");
		}
		PhantomTestDatabaseBootstrap.initialize(context.moduleRoot(), Path.of(configProperty));
		context.record("database.name", PhantomTestDatabaseGuard.TARGET_DATABASE);
		context.record("race.mode", "latch-no-sleep");
		context.record("allocator.mode", "durable-single-row-high-water");
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-real-db-recycled-id-restart-stale-g1", this::recycledIdRestart);
		registry.add("02-real-db-allocator-failure-no-fake-success", this::allocatorFailure);
		registry.add("03-allied-retirement-cleanup-and-membership-aba", this::alliedRetirementCleanup);
		registry.add("04-zero-war-retirement-race-abort-and-reuse", this::retirementRaceAbortAndReuse);
		registry.add("05-direct-war-target-name-rebind", this::targetNameRebind);
		registry.add("06-manual-migration-high-water-and-source-contract", this::migrationAndSourceContract);
	}

	private void recycledIdRestart(PhantomTestContext context) throws Exception
	{
		deleteClanRow(RECYCLED_CLAN_ID);
		try
		{
			insertClanRow(RECYCLED_CLAN_ID, "RecycleClan");
			final ClanSocialRepository repository = ClanSocialRepository.getInstance();
			final AllianceStateAccess firstState = restoreAllianceState(RECYCLED_CLAN_ID);
			final ClanAllianceService first = allianceService(repository, firstState, new ClanSocialMutationFence(16));
			final Actor leader = new Actor(RECYCLED_CLAN_ID + 1, RECYCLED_CLAN_ID, true, false);
			final ClanAllianceService.Result createdOne = first.create(leader, "RecycleAlly");
			PhantomAssertions.assertTrue(createdOne.successful(), "Real repository did not allocate alliance G1.");
			final AllianceIdentity generationOne = createdOne.identity();
			PhantomAssertions.assertTrue(first.dissolve(leader, generationOne).successful(), "Real repository did not dissolve alliance G1.");
			deleteClanRow(RECYCLED_CLAN_ID);

			insertClanRow(RECYCLED_CLAN_ID, "RecycleClanNew");
			final AllianceStateAccess restartedState = restoreAllianceState(RECYCLED_CLAN_ID);
			final ClanAllianceService restarted = allianceService(repository, restartedState, new ClanSocialMutationFence(16));
			final ClanAllianceService.Result createdTwo = restarted.create(leader, "RecycleAlly");
			PhantomAssertions.assertTrue(createdTwo.successful(), "Fresh clan with the recycled numeric id could not allocate G2 after restore.");
			final AllianceIdentity generationTwo = createdTwo.identity();
			PhantomAssertions.assertTrue(generationTwo.generation() > generationOne.generation(), "Durable allocator reused G1 after clan row deletion/id reuse/restart.");
			PhantomAssertions.assertEquals(1L, restartedState.clan(RECYCLED_CLAN_ID).allianceGenerationCounter(), "Fresh clan membership ABA epoch did not restart independently at one.");
			PhantomAssertions.assertEquals(ClanAllianceService.Status.STALE, restarted.dissolve(leader, generationOne).status(), "Stale G1 operation was not harmless against recycled-id G2.");
			PhantomAssertions.assertEquals(generationTwo, restartedState.clan(RECYCLED_CLAN_ID).identity(), "Stale G1 changed recycled-id G2 memory.");
			PhantomAssertions.assertEquals(generationTwo.generation(), clanGeneration(RECYCLED_CLAN_ID), "Stale G1 changed recycled-id G2 durable state.");
			PhantomAssertions.assertTrue(restarted.dissolve(leader, generationTwo).successful(), "G2 cleanup failed.");
		}
		finally
		{
			deleteClanRow(RECYCLED_CLAN_ID);
		}
	}

	private void allocatorFailure(PhantomTestContext context) throws Exception
	{
		deleteClanRow(FAILURE_CLAN_ID);
		boolean sequenceHeld = false;
		try
		{
			insertClanRow(FAILURE_CLAN_ID, "AllocatorFailureClan");
			try (Connection connection = DatabaseFactory.getConnection();
				PreparedStatement statement = connection.prepareStatement("UPDATE clan_social_identity SET identity_name=? WHERE identity_name=?"))
			{
				statement.setString(1, HELD_SEQUENCE_NAME);
				statement.setString(2, SEQUENCE_NAME);
				PhantomAssertions.assertEquals(1, statement.executeUpdate(), "Allocator row could not be isolated for controlled failure.");
				sequenceHeld = true;
			}
			final AllianceStateAccess state = restoreAllianceState(FAILURE_CLAN_ID);
			final ClanAllianceService service = allianceService(ClanSocialRepository.getInstance(), state, new ClanSocialMutationFence(16));
			final ClanAllianceService.Result result = service.create(new Actor(FAILURE_CLAN_ID + 1, FAILURE_CLAN_ID, true, false), "AllocatorFail");
			PhantomAssertions.assertEquals(ClanAllianceService.Status.PERSISTENCE_FAILURE, result.status(), "Missing allocator row did not produce typed persistence failure.");
			PhantomAssertions.assertEquals(0, state.clan(FAILURE_CLAN_ID).allianceId(), "Allocator failure produced fake memory success.");
			PhantomAssertions.assertEquals(0L, clanGeneration(FAILURE_CLAN_ID), "Allocator failure changed durable alliance state.");
			PhantomAssertions.assertEquals(0L, clanGenerationCounter(FAILURE_CLAN_ID), "Allocator failure advanced the durable membership ABA epoch.");
		}
		finally
		{
			if (sequenceHeld)
			{
				try (Connection connection = DatabaseFactory.getConnection();
					PreparedStatement statement = connection.prepareStatement("UPDATE clan_social_identity SET identity_name=? WHERE identity_name=?"))
				{
					statement.setString(1, SEQUENCE_NAME);
					statement.setString(2, HELD_SEQUENCE_NAME);
					PhantomAssertions.assertEquals(1, statement.executeUpdate(), "Controlled allocator row was not restored.");
				}
			}
			deleteClanRow(FAILURE_CLAN_ID);
		}
	}

	private void alliedRetirementCleanup(PhantomTestContext context)
	{
		final FakePersistence persistence = new FakePersistence();
		for (int clanId : List.of(1, 2, 3))
		{
			persistence.addClan(clanId);
		}
		final AllianceStateAccess state = AllianceStateAccess.standard(1, 2, 3);
		final ClanSocialMutationFence fence = new ClanSocialMutationFence(16);
		final ClanAllianceService service = allianceService(persistence, state, fence);
		final Actor leader = new Actor(101, 1, true, false);
		final Actor member = new Actor(102, 2, true, false);
		final AllianceIdentity identity = service.create(leader, "RetireAlly").identity();
		final ClanAllianceService.Result initialPermit = service.checkInvite(leader, member);
		PhantomAssertions.assertTrue(service.join(leader, member, identity, initialPermit.targetEpoch()).successful(), "Alliance member setup failed.");
		final long memberEpochBeforeRetirement = state.clan(2).allianceGenerationCounter();

		final ClanSocialMutationFence.Retirement memberRetirement = fence.beginRetirement(2);
		PhantomAssertions.assertEquals(ClanAllianceService.Reason.CLAN_RETIRING, service.leave(member, identity).reason(), "Normal alliance leave was not rejected after retirement publication.");
		PhantomAssertions.assertTrue(service.removeAllForClan(memberRetirement).successful(), "Retiring allied member cleanup failed.");
		PhantomAssertions.assertEquals(0, state.clan(2).allianceId(), "Retiring member remained allied in memory.");
		PhantomAssertions.assertTrue(state.clan(2).allianceGenerationCounter() > memberEpochBeforeRetirement, "Retiring member cleanup did not advance its ABA epoch.");
		PhantomAssertions.assertEquals(0, persistence.restoreAllianceState(Map.of(1, "AlphaClan", 2, "BetaClan", 3, "GammaClan")).clan(2).allianceId(), "Retiring member remained allied durably.");
		PhantomAssertions.assertEquals(identity, state.clan(1).identity(), "Member retirement destroyed the surviving leader alliance.");
		PhantomAssertions.assertTrue(fence.completeRetirement(memberRetirement), "Member retirement token did not complete.");
		PhantomAssertions.assertEquals(ClanAllianceService.Status.STALE, service.join(leader, member, identity, initialPermit.targetEpoch()).status(), "Pre-retirement detached membership epoch replay was not stale.");

		final ClanAllianceService.Result currentPermit = service.checkInvite(leader, member);
		PhantomAssertions.assertTrue(service.join(leader, member, identity, currentPermit.targetEpoch()).successful(), "Member could not rejoin with its current ABA epoch.");
		final long leaderEpoch = state.clan(1).allianceGenerationCounter();
		final long rejoinedMemberEpoch = state.clan(2).allianceGenerationCounter();
		final ClanSocialMutationFence.Retirement leaderRetirement = fence.beginRetirement(1);
		PhantomAssertions.assertTrue(service.removeAllForClan(leaderRetirement).successful(), "Retiring alliance leader cleanup failed.");
		PhantomAssertions.assertEquals(0, state.clan(1).allianceId(), "Retiring alliance leader remained allied.");
		PhantomAssertions.assertEquals(0, state.clan(2).allianceId(), "Leader retirement left a dangling member alliance reference.");
		PhantomAssertions.assertTrue(state.clan(1).allianceGenerationCounter() > leaderEpoch, "Leader retirement did not advance leader ABA epoch.");
		PhantomAssertions.assertTrue(state.clan(2).allianceGenerationCounter() > rejoinedMemberEpoch, "Leader retirement did not advance member ABA epoch.");
		PhantomAssertions.assertTrue(fence.completeRetirement(leaderRetirement), "Leader retirement token did not complete.");
	}

	private void retirementRaceAbortAndReuse(PhantomTestContext context) throws Exception
	{
		final FakePersistence persistence = new FakePersistence();
		for (int clanId : List.of(1, 2, 3))
		{
			persistence.addClan(clanId);
		}
		final AllianceStateAccess allianceState = AllianceStateAccess.standard(1, 2, 3);
		final WarStateAccess warState = WarStateAccess.standard();
		final ClanSocialMutationFence fence = new ClanSocialMutationFence(16);
		final ClanAllianceService allianceService = allianceService(persistence, allianceState, fence);
		final ClanWarService warService = warService(persistence, warState, fence);
		final CountDownLatch retirementPublished = new CountDownLatch(1);
		final CountDownLatch finishDestroy = new CountDownLatch(1);
		final AtomicReference<ClanSocialMutationFence.Retirement> retirementRef = new AtomicReference<>();
		final AtomicReference<ClanAllianceService.Result> allianceCleanupRef = new AtomicReference<>();
		final AtomicReference<ClanWarService.Result> warCleanupRef = new AtomicReference<>();
		final AtomicReference<Throwable> destroyFailure = new AtomicReference<>();

		final Thread destroy = new Thread(() ->
		{
			try
			{
				final ClanSocialMutationFence.Retirement retirement = fence.beginRetirement(1);
				retirementRef.set(retirement);
				retirementPublished.countDown();
				if (!finishDestroy.await(5, TimeUnit.SECONDS))
				{
					throw new AssertionError("Timed out waiting to finish deterministic destroy.");
				}
				allianceCleanupRef.set(allianceService.removeAllForClan(retirement));
				warCleanupRef.set(warService.removeAllForClan(retirement));
				if (allianceCleanupRef.get().successful() && warCleanupRef.get().successful())
				{
					fence.completeRetirement(retirement);
				}
				else
				{
					fence.abortRetirement(retirement);
				}
			}
			catch (Throwable failure)
			{
				destroyFailure.set(failure);
			}
		}, "goal027d-destroy");
		destroy.start();
		await(retirementPublished, "Retirement publication did not become observable.");

		final ClanWarService.Result directDeclare = warService.declare(new ClanWarService.Actor(101, 1, true), "BetaClan");
		PhantomAssertions.assertEquals(ClanWarService.Reason.CLAN_RETIRING, directDeclare.reason(), "Zero-war direct declaration passed after retirement publication.");
		PhantomAssertions.assertEquals(ClanWarService.Reason.CLAN_RETIRING, warService.declareAcceptedReply(1, 2).reason(), "Accepted declaration passed after retirement publication.");
		PhantomAssertions.assertEquals(ClanAllianceService.Reason.CLAN_RETIRING, allianceService.create(new Actor(101, 1, true, false), "RaceAlly").reason(), "Alliance mutation passed after retirement publication.");
		PhantomAssertions.assertTrue(warService.currentWar(1, 2).isEmpty(), "Rejected destroy/declare race created a registry war.");
		PhantomAssertions.assertFalse(warState.sourceAtWarWith(1, 2), "Rejected destroy/declare race changed the legacy war view.");
		PhantomAssertions.assertEquals(null, persistence.war(1, 2), "Rejected destroy/declare race created a durable war.");

		finishDestroy.countDown();
		destroy.join(5000);
		PhantomAssertions.assertFalse(destroy.isAlive(), "Deterministic destroy thread did not finish.");
		if (destroyFailure.get() != null)
		{
			throw new AssertionError("Deterministic destroy thread failed.", destroyFailure.get());
		}
		PhantomAssertions.assertTrue(allianceCleanupRef.get().successful() && warCleanupRef.get().successful(), "Retirement cleanup did not finish successfully.");

		final ClanSocialMutationFence.Retirement oldRetirement = retirementRef.get();
		final ClanSocialMutationFence.Retirement reusedIdRetirement = fence.beginRetirement(1);
		PhantomAssertions.assertTrue(reusedIdRetirement != null, "Completed retirement poisoned numeric id reuse.");
		PhantomAssertions.assertFalse(fence.abortRetirement(oldRetirement), "Old retirement token cleared a new reused-id retirement.");
		PhantomAssertions.assertEquals(ClanAllianceService.Reason.CLAN_RETIRING, allianceService.create(new Actor(101, 1, true, false), "StillFenced").reason(), "Old token incorrectly released the new retirement.");
		PhantomAssertions.assertTrue(fence.abortRetirement(reusedIdRetirement), "Retirement abort did not clear the live reused-id clan.");
		final ClanAllianceService.Result afterAbort = allianceService.create(new Actor(101, 1, true, false), "AfterAbort");
		PhantomAssertions.assertTrue(afterAbort.successful(), "A live/reused-id clan remained poisoned after retirement abort.");
		PhantomAssertions.assertTrue(allianceService.dissolve(new Actor(101, 1, true, false), afterAbort.identity()).successful(), "Post-abort alliance cleanup failed.");
	}

	private void targetNameRebind(PhantomTestContext context)
	{
		final FakePersistence persistence = new FakePersistence();
		final WarStateAccess state = WarStateAccess.standard();
		state.rebindOnSecondLookup("BetaClan", 3);
		final ClanWarService service = warService(persistence, state, new ClanSocialMutationFence(16));
		final ClanWarService.Result result = service.declare(new ClanWarService.Actor(101, 1, true), "BetaClan");
		PhantomAssertions.assertEquals(ClanWarService.Status.STALE, result.status(), "Target-name rebind did not return typed stale result.");
		PhantomAssertions.assertTrue(service.currentWar(1, 2).isEmpty() && service.currentWar(1, 3).isEmpty(), "Target-name rebind mutated a target outside the acquired lock set.");
		PhantomAssertions.assertEquals(null, persistence.war(1, 2), "Target-name rebind created the pre-lock target war.");
		PhantomAssertions.assertEquals(null, persistence.war(1, 3), "Target-name rebind created the rebound target war.");
		PhantomAssertions.assertFalse(state.sourceAtWarWith(1, 2) || state.sourceAtWarWith(1, 3), "Target-name rebind changed the legacy war view.");
	}

	private void migrationAndSourceContract(PhantomTestContext context) throws Exception
	{
		final Path migrationFile = context.moduleRoot().resolve("docs/phantoms/migrations/V027C__canonical_clan_social_domain.sql");
		boolean backupsCreated = false;
		try (Connection connection = DatabaseFactory.getConnection())
		{
			PhantomAssertions.assertEquals(PhantomTestDatabaseGuard.TARGET_DATABASE, ClanSocialDomainGoal027CSuite.scalarString(connection, "SELECT DATABASE()"), "Migration rehearsal escaped the allowlisted test database.");
			PhantomAssertions.assertFalse(ClanSocialDomainGoal027CSuite.tableExists(connection, "clan_data_027c_backup") || ClanSocialDomainGoal027CSuite.tableExists(connection, "clan_wars_027c_backup") || ClanSocialDomainGoal027CSuite.tableExists(connection, "clan_social_identity_027c_backup"), "Previous migration rehearsal backup tables remain.");
			final int originalClanRows = ClanSocialDomainGoal027CSuite.scalarInt(connection, "SELECT COUNT(*) FROM `clan_data`");
			final int originalWarRows = ClanSocialDomainGoal027CSuite.scalarInt(connection, "SELECT COUNT(*) FROM `clan_wars`");
			final long originalHighWater = ClanSocialDomainGoal027CSuite.scalarLong(connection, "SELECT `high_water` FROM `clan_social_identity` WHERE `identity_name`='alliance_incarnation'");
			try
			{
				ClanSocialDomainGoal027CSuite.execute(connection, "RENAME TABLE `clan_data` TO `clan_data_027c_backup`, `clan_wars` TO `clan_wars_027c_backup`, `clan_social_identity` TO `clan_social_identity_027c_backup`");
				backupsCreated = true;
				ClanSocialDomainGoal027CSuite.createOldSchemaFixture(connection, context.moduleRoot());
				ClanSocialDomainGoal027CSuite.seedOldSchemaFixture(connection);
				ClanSocialDomainGoal027CSuite.applyExactMigration(connection, context.moduleRoot(), migrationFile);

				PhantomAssertions.assertEquals(4, ClanSocialDomainGoal027CSuite.scalarInt(connection, "SELECT COUNT(*) FROM `clan_data`"), "Migration lost clan rows.");
				PhantomAssertions.assertEquals(3, ClanSocialDomainGoal027CSuite.scalarInt(connection, "SELECT COUNT(*) FROM `clan_wars`"), "Migration lost directed wars.");
				PhantomAssertions.assertEquals(3, ClanSocialDomainGoal027CSuite.scalarInt(connection, "SELECT COUNT(*) FROM `clan_data` WHERE `ally_id`<>0 AND `ally_generation`=1"), "Migration changed active alliance membership/identity.");
				PhantomAssertions.assertEquals(2, ClanSocialDomainGoal027CSuite.scalarInt(connection, "SELECT SUM(`wantspeace1`) FROM `clan_wars`"), "Migration changed wantspeace1 flags.");
				PhantomAssertions.assertEquals(2, ClanSocialDomainGoal027CSuite.scalarInt(connection, "SELECT SUM(`wantspeace2`) FROM `clan_wars`"), "Migration changed wantspeace2 flags.");
				PhantomAssertions.assertEquals(1L, ClanSocialDomainGoal027CSuite.scalarLong(connection, "SELECT `high_water` FROM `clan_social_identity` WHERE `identity_name`='alliance_incarnation'"), "Migration did not initialize allocator high-water from active identities.");

				final int migratedNewClanId = 190027403;
				insertClanRow(connection, migratedNewClanId, "MigratedNewClan");
				final AllianceStateAccess state = restoreAllianceState(migratedNewClanId);
				final ClanAllianceService service = allianceService(ClanSocialRepository.getInstance(), state, new ClanSocialMutationFence(16));
				final ClanAllianceService.Result allocated = service.create(new Actor(migratedNewClanId + 1, migratedNewClanId, true, false), "MigratedNewAlly");
				PhantomAssertions.assertTrue(allocated.successful(), "Post-migration real allocator could not create a new alliance.");
				PhantomAssertions.assertTrue(allocated.identity().generation() > 1, "Post-migration allocator collided with migrated high-water.");
				PhantomAssertions.assertEquals(allocated.identity().generation(), ClanSocialDomainGoal027CSuite.scalarLong(connection, "SELECT `high_water` FROM `clan_social_identity` WHERE `identity_name`='alliance_incarnation'"), "Allocator did not durably advance migration high-water.");
			}
			finally
			{
				if (backupsCreated)
				{
					ClanSocialDomainGoal027CSuite.restoreFreshSchemaTables(connection);
				}
			}
			PhantomAssertions.assertEquals(originalClanRows, ClanSocialDomainGoal027CSuite.scalarInt(connection, "SELECT COUNT(*) FROM `clan_data`"), "027D rehearsal did not restore fresh clan_data.");
			PhantomAssertions.assertEquals(originalWarRows, ClanSocialDomainGoal027CSuite.scalarInt(connection, "SELECT COUNT(*) FROM `clan_wars`"), "027D rehearsal did not restore fresh clan_wars.");
			PhantomAssertions.assertEquals(originalHighWater, ClanSocialDomainGoal027CSuite.scalarLong(connection, "SELECT `high_water` FROM `clan_social_identity` WHERE `identity_name`='alliance_incarnation'"), "027D rehearsal did not restore fresh allocator high-water.");
		}

		final Path root = context.moduleRoot();
		final String repository = read(root, "java/org/l2jmobius/gameserver/model/clan/ClanSocialRepository.java");
		final String fence = read(root, "java/org/l2jmobius/gameserver/model/clan/ClanSocialMutationFence.java");
		final String allianceService = read(root, "java/org/l2jmobius/gameserver/model/clan/ClanAllianceService.java");
		final String warService = read(root, "java/org/l2jmobius/gameserver/model/clan/ClanWarService.java");
		final String clanTable = read(root, "java/org/l2jmobius/gameserver/data/sql/ClanTable.java");
		final String freshSequence = read(root, "dist/db_installer/sql/game/clan_social_identity.sql");
		final String migration = read(root, "docs/phantoms/migrations/V027C__canonical_clan_social_domain.sql");
		PhantomAssertions.assertTrue(repository.contains("SELECT high_water FROM clan_social_identity WHERE identity_name=? FOR UPDATE") && repository.contains("UPDATE clan_social_identity SET high_water=?"), "Repository lacks transactional durable alliance high-water allocation.");
		PhantomAssertions.assertTrue(repository.indexOf("lockClanRow(connection, leaderClanId)") < repository.indexOf("allocateAllianceIncarnation(connection)"), "Create transaction allocates before exact detached-row revalidation.");
		PhantomAssertions.assertTrue(allianceService.contains("Math.addExact(clan.allianceGenerationCounter(), 1)") && !allianceService.contains("generation, generation, clan.allianceCrestId()"), "Alliance incarnation token is still conflated with the per-clan membership ABA epoch.");
		PhantomAssertions.assertTrue(fence.contains("beginRetirement") && fence.contains("abortRetirement") && fence.contains("completeRetirement") && fence.contains("isCurrentRetirement"), "Shared social fence lacks exact bounded retirement lifecycle.");
		PhantomAssertions.assertTrue(allianceService.contains("CLAN_RETIRING") && allianceService.contains("removeAllForClan(ClanSocialMutationFence.Retirement") && warService.contains("CLAN_RETIRING") && warService.contains("removeAllForClan(ClanSocialMutationFence.Retirement"), "Canonical services do not share the retirement guard/cleanup token.");
		PhantomAssertions.assertTrue(warService.contains("int expectedTargetClanId") && warService.contains("target.clanId() != expectedTargetClanId"), "Direct war declaration does not revalidate the exact target id used for lock selection.");
		final int begin = clanTable.indexOf("beginRetirement(clanId)");
		final int allianceCleanup = clanTable.indexOf("ClanAllianceService.getInstance().removeAllForClan(retirement)");
		final int warCleanup = clanTable.indexOf("ClanWarService.getInstance().removeAllForClan(retirement)");
		final int durableDelete = clanTable.indexOf("DELETE FROM clan_data WHERE clan_id=?");
		final int complete = clanTable.indexOf("completeRetirement(retirement)");
		final int release = clanTable.indexOf("releaseId(clanId)");
		PhantomAssertions.assertTrue((begin >= 0) && (begin < allianceCleanup) && (allianceCleanup < warCleanup) && (warCleanup < durableDelete) && (durableDelete < complete) && (complete < release), "Actual destroy path does not publish retirement before cleanup and retain it through durable delete/id release.");
		PhantomAssertions.assertTrue(freshSequence.contains("PRIMARY KEY (`identity_name`)") && freshSequence.contains("INSERT IGNORE") && migration.contains("COALESCE(MAX(`ally_generation`), 0)"), "Fresh/manual schema path lacks the bounded allocator high-water row.");
		for (String service : List.of(allianceService, warService))
		{
			PhantomAssertions.assertFalse(service.contains("gameserver.phantoms") || service.contains("ClientPacket"), "Native social service is not transport-neutral/Phantom-free.");
		}
	}

	private static ClanAllianceService allianceService(ClanSocialPersistence persistence, ClanAllianceService.StateAccess state, ClanSocialMutationFence fence)
	{
		return new ClanAllianceService(persistence, state, fence, () -> NOW, true);
	}

	private static ClanWarService warService(ClanSocialPersistence persistence, ClanWarService.StateAccess state, ClanSocialMutationFence fence)
	{
		return new ClanWarService(persistence, state, fence, () -> NOW, true);
	}

	private static AllianceStateAccess restoreAllianceState(int clanId) throws SQLException
	{
		final AllianceStateAccess state = new AllianceStateAccess();
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("SELECT clan_id, clan_name, clan_level, ally_id, ally_name, ally_generation, ally_generation_counter, ally_crest_id, ally_penalty_expiry_time, ally_penalty_type, dissolving_expiry_time FROM clan_data WHERE clan_id=?"))
		{
			statement.setInt(1, clanId);
			try (ResultSet result = statement.executeQuery())
			{
				if (!result.next())
				{
					throw new SQLException("Missing Goal 027D clan fixture " + clanId + '.');
				}
				state.putClan(result.getInt("clan_id"), result.getString("clan_name"), result.getInt("clan_level"), result.getInt("ally_id"), result.getString("ally_name"), result.getLong("ally_generation"), result.getInt("ally_crest_id"), result.getLong("ally_penalty_expiry_time"), result.getInt("ally_penalty_type"), result.getLong("dissolving_expiry_time"), result.getLong("ally_generation_counter"));
			}
		}
		return state;
	}

	private static void insertClanRow(int clanId, String clanName) throws SQLException
	{
		try (Connection connection = DatabaseFactory.getConnection())
		{
			insertClanRow(connection, clanId, clanName);
		}
	}

	private static void insertClanRow(Connection connection, int clanId, String clanName) throws SQLException
	{
		try (PreparedStatement statement = connection.prepareStatement("INSERT INTO clan_data (clan_id, clan_name, clan_level, ally_id, ally_name, ally_generation, ally_generation_counter, leader_id) VALUES (?, ?, 5, 0, NULL, 0, 0, ?)"))
		{
			statement.setInt(1, clanId);
			statement.setString(2, clanName);
			statement.setInt(3, clanId + 1);
			PhantomAssertions.assertEquals(1, statement.executeUpdate(), "Goal 027D clan fixture insert failed.");
		}
	}

	private static void deleteClanRow(int clanId) throws SQLException
	{
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement wars = connection.prepareStatement("DELETE FROM clan_wars WHERE clan1=? OR clan2=?");
			PreparedStatement clan = connection.prepareStatement("DELETE FROM clan_data WHERE clan_id=?"))
		{
			wars.setInt(1, clanId);
			wars.setInt(2, clanId);
			wars.executeUpdate();
			clan.setInt(1, clanId);
			clan.executeUpdate();
		}
	}

	private static long clanGeneration(int clanId) throws SQLException
	{
		return clanScalar(clanId, "ally_generation");
	}

	private static long clanGenerationCounter(int clanId) throws SQLException
	{
		return clanScalar(clanId, "ally_generation_counter");
	}

	private static long clanScalar(int clanId, String column) throws SQLException
	{
		if (!List.of("ally_generation", "ally_generation_counter").contains(column))
		{
			throw new IllegalArgumentException("Unsupported Goal 027D clan scalar.");
		}
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("SELECT " + column + " FROM clan_data WHERE clan_id=?"))
		{
			statement.setInt(1, clanId);
			try (ResultSet result = statement.executeQuery())
			{
				if (!result.next())
				{
					throw new SQLException("Missing Goal 027D clan fixture " + clanId + '.');
				}
				return result.getLong(1);
			}
		}
	}

	private static void await(CountDownLatch latch, String message) throws InterruptedException
	{
		if (!latch.await(5, TimeUnit.SECONDS))
		{
			throw new AssertionError(message);
		}
	}

	private static String read(Path root, String relative) throws Exception
	{
		return Files.readString(root.resolve(relative));
	}
}
