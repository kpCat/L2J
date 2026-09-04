/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig;
import org.l2jmobius.gameserver.phantoms.PhantomPopulationResetService.FaultPoint;
import org.l2jmobius.gameserver.phantoms.PhantomPopulationResetService.Lifecycle;
import org.l2jmobius.gameserver.phantoms.PhantomPopulationResetService.ResetCode;
import org.l2jmobius.gameserver.phantoms.PhantomPopulationResetService.ResetPreview;
import org.l2jmobius.gameserver.phantoms.PhantomSystem.OperatorControlCode;
import org.l2jmobius.gameserver.phantoms.PhantomSystem.OperatorControlResult;
import org.l2jmobius.gameserver.phantoms.PhantomSystem.OperatorMode;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationState;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStateCodec;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository.ManagedProfile;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomHeadlessPlayerTestEnvironment;
import org.l2jmobius.tests.phantoms.PhantomTestContext;
import org.l2jmobius.tests.phantoms.PhantomTestRegistry;
import org.l2jmobius.tests.phantoms.PhantomTestSuite;

public final class PhantomPopulationResetOwnershipGoal032Suite implements PhantomTestSuite
{
	private static final long SEED = 32003201L;
	private static final PhantomPlayersConfig.Settings SETTINGS = new PhantomPlayersConfig.Settings(true, true, 4, 16, 100, 4, 2, 0, 2, 8, 16, 32, ZoneOffset.UTC);

	private final PhantomHeadlessPlayerTestEnvironment _environment = new PhantomHeadlessPlayerTestEnvironment();
	private final PhantomPopulationStateCodec _codec = new PhantomPopulationStateCodec();
	private PhantomProfileRepository _profiles;
	private boolean _environmentInitialized;

	@Override
	public String id()
	{
		return "phantom-population-reset-ownership-goal032";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Goal032 ownership suite used the wrong seed.");
		resetOperatorState();
		_environment.initialize(context);
		_environmentInitialized = true;
		_profiles = PhantomProfileRepository.open();
		PhantomAssertions.assertEquals(0L, scalar("SELECT COUNT(*) FROM phantom_profiles"), "Goal032 ownership suite requires a clean Phantom profile table.");
		seedHumanPrivateState();
		ensurePopulation();
		context.record("goal032.ownership.database", "127.0.0.1:3308/l2jmobiush5_phantom_test");
		context.record("goal032.ownership.settings", "population=2,active=0");
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-preview-token-expiry-cancel-stale", this::testConfirmationEnvelope);
		registry.add("02-drain-and-transaction-failure-rollback", this::testFailureRollback);
		registry.add("03-human-sentinel-shared-state-and-idempotence", this::testOwnershipSafety);
	}

	private void testConfirmationEnvelope(PhantomTestContext context) throws Exception
	{
		final PopulationSnapshot baseline = populationSnapshot();
		final PhantomPopulationResetService service = service(System::currentTimeMillis, productionLifecycle(), _ -> { });
		final ResetPreview preview = service.preview();
		PhantomAssertions.assertTrue(preview.safe(), "Safe owned population preview was blocked: " + preview.blockers());
		PhantomAssertions.assertEquals(2, preview.identities(), "Preview identity count drifted.");
		PhantomAssertions.assertEquals(2, preview.characters(), "Preview character count drifted.");
		PhantomAssertions.assertEquals(2, preview.accounts(), "Preview account count drifted.");
		PhantomAssertions.assertTrue(preview.confirmationToken() != null, "Safe preview did not arm a confirmation token.");
		PhantomAssertions.assertTrue(preview.expiresAt() - preview.generatedAt() == PhantomPopulationResetService.CONFIRMATION_TTL_MILLIS, "Confirmation TTL drifted.");
		PhantomAssertions.assertEquals(baseline, populationSnapshot(), "Preview mutated durable population.");

		PhantomAssertions.assertEquals(ResetCode.INVALID_TOKEN, service.confirm("wrong-token", false).code(), "Wrong confirmation token was accepted.");
		PhantomAssertions.assertEquals(baseline, populationSnapshot(), "Wrong token mutated durable population.");
		PhantomAssertions.assertTrue(service.cancel(), "Armed reset could not be cancelled.");
		PhantomAssertions.assertEquals(ResetCode.NOT_ARMED, service.confirm(preview.confirmationToken(), false).code(), "Cancelled token remained usable.");

		final AtomicLong clock = new AtomicLong(10_000L);
		final PhantomPopulationResetService expiring = service(clock::get, productionLifecycle(), _ -> { });
		final ResetPreview expiringPreview = expiring.preview();
		clock.addAndGet(PhantomPopulationResetService.CONFIRMATION_TTL_MILLIS + 1);
		PhantomAssertions.assertEquals(ResetCode.EXPIRED_TOKEN, expiring.confirm(expiringPreview.confirmationToken(), false).code(), "Expired reset token was accepted.");
		PhantomAssertions.assertEquals(baseline, populationSnapshot(), "Expired token mutated durable population.");

		final PhantomPopulationResetService stale = service(System::currentTimeMillis, productionLifecycle(), _ -> { });
		final ResetPreview stalePreview = stale.preview();
		final long profileId = managed().getFirst().profile().profileId();
		execute("UPDATE phantom_profiles SET row_version=row_version+1 WHERE profile_id=?", profileId);
		try
		{
			PhantomAssertions.assertEquals(ResetCode.SNAPSHOT_CHANGED, stale.confirm(stalePreview.confirmationToken(), false).code(), "Stale preview was accepted.");
		}
		finally
		{
			execute("UPDATE phantom_profiles SET row_version=row_version-1 WHERE profile_id=?", profileId);
		}
		PhantomAssertions.assertEquals(baseline, populationSnapshot(), "Stale confirmation changed population cardinality.");
		context.record("goal032.ownership.confirmation", "preview-read-only,wrong-token,expiry,cancel,stale=PASS");
	}

	private void testFailureRollback(PhantomTestContext context) throws Exception
	{
		final PopulationSnapshot baseline = populationSnapshot();
		final Lifecycle failedDrain = fixedLifecycle(control(OperatorControlCode.SHUTDOWN_FAILED, true, PhantomSystem.State.FAILED), control(OperatorControlCode.STARTED, true, PhantomSystem.State.RUNNING));
		final PhantomPopulationResetService drainFailure = service(System::currentTimeMillis, failedDrain, _ -> { });
		final ResetPreview drainPreview = drainFailure.preview();
		PhantomAssertions.assertEquals(ResetCode.DRAIN_FAILED, drainFailure.confirm(drainPreview.confirmationToken(), false).code(), "Failed drain crossed the destructive boundary.");
		PhantomAssertions.assertEquals(baseline, populationSnapshot(), "Failed drain mutated durable population.");

		for (FaultPoint point : FaultPoint.values())
		{
			final PhantomPopulationResetService rollback = service(System::currentTimeMillis, productionLifecycle(), actual ->
			{
				if (actual == point)
				{
					throw new IllegalStateException("goal032.injected." + point);
				}
			});
			final ResetPreview rollbackPreview = rollback.preview();
			PhantomAssertions.assertEquals(ResetCode.RESET_FAILED, rollback.confirm(rollbackPreview.confirmationToken(), false).code(), "Injected " + point + " failure did not report rollback.");
			PhantomAssertions.assertEquals(baseline, populationSnapshot(), "Injected " + point + " failure left partial reset state.");
		}
		context.record("goal032.ownership.rollback", "drain,CLEANUP_BOUNDARY,BEFORE_COMMIT=PASS");
	}

	private void testOwnershipSafety(PhantomTestContext context) throws Exception
	{
		final HumanSnapshot humanBefore = humanSnapshot();
		final PhantomPopulationState partialTemplate = _codec.decode(managed().getFirst().component().payload());
		final OwnedIdentity phantom = ownedIdentities().getFirst();
		final int humanObjectId = _environment.primary().objectId();
		execute("INSERT INTO character_friends (charId,friendId) VALUES (?,?)", humanObjectId, phantom.characterObjectId());
		execute("INSERT INTO item_auction_bid (auctionId,playerObjId,playerBid) VALUES (?,?,?)", 32003201, phantom.characterObjectId(), 1L);

		final PhantomPopulationResetService blocked = service(System::currentTimeMillis, productionLifecycle(), _ -> { });
		final ResetPreview blockedPreview = blocked.preview();
		PhantomAssertions.assertFalse(blockedPreview.safe(), "Unsafe shared item-auction state did not block reset.");
		PhantomAssertions.assertTrue(blockedPreview.blockers().stream().anyMatch(value -> value.startsWith("shared.item_auction_bid:")), "Shared blocker was not explicit.");
		PhantomAssertions.assertEquals(null, blockedPreview.confirmationToken(), "Blocked preview armed a destructive token.");
		PhantomAssertions.assertEquals(humanBefore, humanSnapshot(), "Blocked preview changed human sentinel state.");
		execute("DELETE FROM item_auction_bid WHERE auctionId=? AND playerObjId=?", 32003201, phantom.characterObjectId());

		final List<OwnedIdentity> oldIdentities = ownedIdentities();
		final PhantomPopulationResetService service = service(System::currentTimeMillis, productionLifecycle(), _ -> { });
		final ResetPreview preview = service.preview();
		PhantomAssertions.assertTrue(preview.safe(), "Safe-detach fixture remained blocked: " + preview.blockers());
		PhantomAssertions.assertTrue(preview.deleteCounts().get("character_friends.safe_detach") > 0, "Preview omitted the safe-detach friend relation.");
		final var reset = service.confirm(preview.confirmationToken(), false);
		PhantomAssertions.assertEquals(ResetCode.RESET_COMPLETE, reset.code(), "Owned population reset did not commit.");
		PhantomAssertions.assertEquals(ResetCode.NOT_ARMED, service.confirm(preview.confirmationToken(), false).code(), "Consumed confirmation token remained reusable.");
		assertOldPopulationAbsent(oldIdentities);
		PhantomAssertions.assertEquals(0L, scalar("SELECT COUNT(*) FROM character_friends WHERE charId=? OR friendId=?", phantom.characterObjectId(), phantom.characterObjectId()), "Safe-detach friend relation survived reset.");
		PhantomAssertions.assertEquals(humanBefore, humanSnapshot(), "Human account/character/inventory/skills/quest changed during reset.");

		final var unproven = _profiles.create(null);
		final ResetPreview unprovenPreview = service.preview();
		PhantomAssertions.assertFalse(unprovenPreview.safe(), "Profile without population.state provenance did not block reset.");
		PhantomAssertions.assertTrue(unprovenPreview.blockers().stream().anyMatch(value -> value.startsWith("ownership.population_state_missing:")), "Missing provenance blocker was not explicit.");
		PhantomAssertions.assertEquals(null, unprovenPreview.confirmationToken(), "Unproven profile armed a destructive token.");
		_profiles.delete(unproven.profileId(), unproven.rowVersion());

		_profiles.createWithComponent(PhantomPopulationState.COMPONENT_TYPE, PhantomPopulationState.SCHEMA_VERSION, profileId -> _codec.encode(partialState(profileId, partialTemplate)));
		final ResetPreview partialPreview = service.preview();
		PhantomAssertions.assertTrue(partialPreview.safe(), "Owned shell-stage partial identity was blocked: " + partialPreview.blockers());
		PhantomAssertions.assertEquals(1, partialPreview.identities(), "Partial preview identity count drifted.");
		PhantomAssertions.assertEquals(0, partialPreview.accounts(), "Shell-stage partial preview reported an account.");
		PhantomAssertions.assertEquals(0, partialPreview.characters(), "Shell-stage partial preview reported a character.");
		PhantomAssertions.assertEquals(ResetCode.RESET_COMPLETE, service.confirm(partialPreview.confirmationToken(), false).code(), "Owned shell-stage partial identity was not reset.");

		final ResetPreview emptyPreview = service.preview();
		PhantomAssertions.assertTrue(emptyPreview.safe(), "Empty population preview was blocked.");
		PhantomAssertions.assertEquals(0, emptyPreview.identities(), "Empty preview reported identities.");
		PhantomAssertions.assertEquals(ResetCode.RESET_NOOP, service.confirm(emptyPreview.confirmationToken(), false).code(), "Second empty reset was not idempotent.");
		context.record("goal032.ownership.shared", "safe-detach=friend,blocked=item-auction,human-sentinel=unchanged");
		context.record("goal032.ownership.cleanup", "profiles/components/economy/characters/accounts/private=zero,partial-shell=PASS,empty-noop=PASS");
	}

	private static PhantomPopulationState partialState(long profileId, PhantomPopulationState template)
	{
		return new PhantomPopulationState(
			PhantomPopulationState.State.SHELL,
			template.populationGeneration(),
			template.creationOrdinal(),
			template.catalogHash(),
			template.initializationAuthorityHash(),
			template.deterministicSeed(),
			template.nameAttempt(),
			"p" + Long.toString(profileId, 36),
			"goal032partialshelltoken0123456789",
			template.characterName(),
			template.classId(),
			template.female(),
			template.face(),
			template.hairColor(),
			template.hairStyle(),
			template.scheduleTemplate(),
			template.schedulePhaseMinutes(),
			template.homeMapRegionId(),
			template.creationX(),
			template.creationY(),
			template.creationZ(),
			null,
			null,
			PhantomPopulationState.CreationStage.SHELL_DURABLE,
			"",
			"");
	}

	private void ensurePopulation() throws Exception
	{
		resetOperatorState();
		PhantomAssertions.assertTrue(PhantomSystem.startConfiguredForTesting(SETTINGS), "Goal032 fixture population did not start.");
		await(45_000, () ->
		{
			final List<ManagedProfile> rows = managed();
			if (rows.size() != 2)
			{
				return false;
			}
			for (ManagedProfile row : rows)
			{
				final PhantomPopulationState state = _codec.decode(row.component().payload());
				if ((state.state() != PhantomPopulationState.State.READY) || (state.actualCharacterObjectId() == null))
				{
					return false;
				}
			}
			return true;
		}, "Goal032 fixture population did not reach two READY identities.");
		final long deadline = System.nanoTime() + 10_000_000_000L;
		while (PhantomSystem.hasConfiguredInstance() && (System.nanoTime() < deadline))
		{
			PhantomSystem.operatorDrain();
			if (PhantomSystem.hasConfiguredInstance())
			{
				Thread.sleep(20);
			}
		}
		PhantomAssertions.assertFalse(PhantomSystem.hasConfiguredInstance(), "Goal032 fixture drain retained a runtime owner.");
	}

	private List<ManagedProfile> managed()
	{
		return _profiles.listManagedAfter(PhantomPopulationState.COMPONENT_TYPE, 0, 32);
	}

	private List<OwnedIdentity> ownedIdentities()
	{
		final List<OwnedIdentity> identities = new ArrayList<>();
		for (ManagedProfile row : managed())
		{
			final PhantomPopulationState state = _codec.decode(row.component().payload());
			identities.add(new OwnedIdentity(row.profile().profileId(), state.reservedAccount(), state.characterName(), state.actualCharacterObjectId()));
		}
		return List.copyOf(identities);
	}

	private PopulationSnapshot populationSnapshot() throws Exception
	{
		return new PopulationSnapshot(
			scalar("SELECT COUNT(*) FROM phantom_profiles"),
			scalar("SELECT COUNT(*) FROM phantom_profile_components"),
			scalar("SELECT COUNT(*) FROM phantom_economy_operations"),
			scalar("SELECT COUNT(*) FROM phantom_economy_reservations"),
			scalar("SELECT COUNT(*) FROM phantom_economy_audit"),
			scalar("SELECT COUNT(*) FROM phantom_economy_offers"),
			scalar("SELECT COUNT(*) FROM characters WHERE account_name IN (SELECT CAST(CONCAT('p',LOWER(CONV(profile_id,10,36))) AS CHAR) FROM phantom_profiles)"),
			scalar("SELECT COALESCE(SUM(row_version),0) FROM phantom_profiles"),
			scalar("SELECT COALESCE(SUM(row_version),0) FROM phantom_profile_components"));
	}

	private void seedHumanPrivateState() throws Exception
	{
		execute("INSERT INTO character_quests (charId,name,var,value) VALUES (?,?,?,?)", _environment.primary().objectId(), "Goal032Human", "sentinel", "unchanged");
	}

	private HumanSnapshot humanSnapshot() throws Exception
	{
		final int first = _environment.primary().objectId();
		final int second = _environment.observer().objectId();
		return new HumanSnapshot(
			scalar("SELECT COUNT(*) FROM accounts WHERE login=?", _environment.primary().accountName()),
			scalar("SELECT COUNT(*) FROM characters WHERE charId IN (?,?)", first, second),
			scalar("SELECT COALESCE(SUM(count),0) FROM items WHERE owner_id IN (?,?)", first, second),
			scalar("SELECT COUNT(*) FROM character_skills WHERE charId IN (?,?)", first, second),
			scalar("SELECT COUNT(*) FROM character_quests WHERE charId IN (?,?)", first, second),
			scalar("SELECT COUNT(*) FROM character_quests WHERE charId=? AND name=? AND var=? AND value=?", first, "Goal032Human", "sentinel", "unchanged"));
	}

	private static void assertOldPopulationAbsent(List<OwnedIdentity> identities) throws Exception
	{
		PhantomAssertions.assertEquals(0L, scalar("SELECT COUNT(*) FROM phantom_profiles"), "Reset retained a Phantom profile.");
		PhantomAssertions.assertEquals(0L, scalar("SELECT COUNT(*) FROM phantom_profile_components"), "Reset retained a Phantom component.");
		PhantomAssertions.assertEquals(0L, scalar("SELECT COUNT(*) FROM phantom_economy_operations"), "Reset retained an economy operation.");
		PhantomAssertions.assertEquals(0L, scalar("SELECT COUNT(*) FROM phantom_economy_reservations"), "Reset retained an economy reservation.");
		PhantomAssertions.assertEquals(0L, scalar("SELECT COUNT(*) FROM phantom_economy_audit"), "Reset retained an economy audit row.");
		PhantomAssertions.assertEquals(0L, scalar("SELECT COUNT(*) FROM phantom_economy_offers"), "Reset retained an economy offer.");
		for (OwnedIdentity identity : identities)
		{
			PhantomAssertions.assertEquals(0L, scalar("SELECT COUNT(*) FROM characters WHERE charId=? OR account_name=? OR char_name=?", identity.characterObjectId(), identity.accountName(), identity.characterName()), "Reset retained or aliased an old Phantom character.");
			PhantomAssertions.assertEquals(0L, scalar("SELECT COUNT(*) FROM accounts WHERE login=?", identity.accountName()), "Reset retained an old Phantom account.");
			PhantomAssertions.assertEquals(0L, scalar("SELECT COUNT(*) FROM items WHERE owner_id=?", identity.characterObjectId()), "Reset retained old Phantom inventory.");
			PhantomAssertions.assertEquals(0L, scalar("SELECT COUNT(*) FROM character_skills WHERE charId=?", identity.characterObjectId()), "Reset retained old Phantom skills.");
			PhantomAssertions.assertEquals(0L, scalar("SELECT COUNT(*) FROM character_quests WHERE charId=?", identity.characterObjectId()), "Reset retained old Phantom quests.");
		}
	}

	private static PhantomPopulationResetService service(java.util.function.LongSupplier clock, Lifecycle lifecycle, PhantomPopulationResetService.FailureInjector failureInjector)
	{
		return new PhantomPopulationResetService(clock, new SecureRandom(), lifecycle, failureInjector);
	}

	private static Lifecycle productionLifecycle()
	{
		return new Lifecycle()
		{
			@Override
			public OperatorControlResult drain()
			{
				return PhantomSystem.operatorDrain();
			}

			@Override
			public OperatorControlResult reseed()
			{
				return PhantomSystem.operatorEnable();
			}
		};
	}

	private static Lifecycle fixedLifecycle(OperatorControlResult drain, OperatorControlResult reseed)
	{
		return new Lifecycle()
		{
			@Override
			public OperatorControlResult drain()
			{
				return drain;
			}

			@Override
			public OperatorControlResult reseed()
			{
				return reseed;
			}
		};
	}

	private static OperatorControlResult control(OperatorControlCode code, boolean configured, PhantomSystem.State state)
	{
		return new OperatorControlResult(code, OperatorMode.DRAINED, false, configured, state);
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		Throwable failure = null;
		try
		{
			if (DatabaseFactory.isInitialized())
			{
				execute("DELETE FROM item_auction_bid WHERE auctionId=?", 32003201);
				if (scalar("SELECT COUNT(*) FROM phantom_profiles") > 0)
				{
					final PhantomPopulationResetService cleanup = service(System::currentTimeMillis, productionLifecycle(), _ -> { });
					final ResetPreview preview = cleanup.preview();
					if (preview.safe())
					{
						cleanup.confirm(preview.confirmationToken(), false);
					}
				}
			}
			if (PhantomSystem.hasConfiguredInstance())
			{
				PhantomSystem.operatorDisable();
			}
			if (!PhantomSystem.hasConfiguredInstance())
			{
				PhantomSystem.resetOperatorModeForTesting();
			}
		}
		catch (Throwable throwable)
		{
			failure = throwable;
		}
		try
		{
			if (_environmentInitialized)
			{
				_environment.shutdown();
				_environmentInitialized = false;
			}
		}
		catch (Throwable throwable)
		{
			if (failure == null)
			{
				failure = throwable;
			}
			else
			{
				failure.addSuppressed(throwable);
			}
		}
		if (failure instanceof Exception exception)
		{
			throw exception;
		}
		if (failure != null)
		{
			throw new RuntimeException(failure);
		}
	}

	private static void resetOperatorState()
	{
		if (PhantomSystem.hasConfiguredInstance())
		{
			PhantomSystem.operatorDisable();
		}
		PhantomAssertions.assertFalse(PhantomSystem.hasConfiguredInstance(), "Operator reset retained a configured runtime.");
		PhantomSystem.resetOperatorModeForTesting();
	}

	private static void execute(String sql, Object... arguments) throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement(sql))
		{
			for (int index = 0; index < arguments.length; index++)
			{
				statement.setObject(index + 1, arguments[index]);
			}
			statement.executeUpdate();
		}
	}

	private static long scalar(String sql, Object... arguments) throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement(sql))
		{
			for (int index = 0; index < arguments.length; index++)
			{
				statement.setObject(index + 1, arguments[index]);
			}
			try (ResultSet result = statement.executeQuery())
			{
				PhantomAssertions.assertTrue(result.next(), "Goal032 scalar query returned no row.");
				return result.getLong(1);
			}
		}
	}

	private static void await(long timeoutMillis, BooleanSupplier condition, String failure) throws Exception
	{
		final long deadline = System.nanoTime() + (timeoutMillis * 1_000_000L);
		while (System.nanoTime() < deadline)
		{
			if (condition.getAsBoolean())
			{
				return;
			}
			Thread.sleep(20);
		}
		PhantomAssertions.assertTrue(condition.getAsBoolean(), failure);
	}

	private record OwnedIdentity(long profileId, String accountName, String characterName, int characterObjectId)
	{
	}

	private record PopulationSnapshot(long profiles, long components, long operations, long reservations, long audit, long offers, long characters, long profileVersions, long componentVersions)
	{
	}

	private record HumanSnapshot(long accounts, long characters, long items, long skills, long quests, long sentinelQuest)
	{
	}
}
