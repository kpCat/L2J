/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig;
import org.l2jmobius.gameserver.model.World;
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

public final class PhantomPopulationResetReseedGoal032Suite implements PhantomTestSuite
{
	private static final long SEED = 32003202L;
	private static final PhantomPlayersConfig.Settings SETTINGS = new PhantomPlayersConfig.Settings(true, true, 32, 10_000, 100, 16, 10, 5, 2, 64, 64, 1024, ZoneOffset.UTC);

	private final PhantomHeadlessPlayerTestEnvironment _environment = new PhantomHeadlessPlayerTestEnvironment();
	private final PhantomPopulationStateCodec _codec = new PhantomPopulationStateCodec();
	private PhantomProfileRepository _profiles;
	private boolean _environmentInitialized;
	private Set<Identity> _reseeded = Set.of();

	@Override
	public String id()
	{
		return "phantom-population-reset-reseed-goal032";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Goal032 reseed suite used the wrong seed.");
		resetOperatorState();
		_environment.initialize(context);
		_environmentInitialized = true;
		_profiles = PhantomProfileRepository.open();
		PhantomAssertions.assertEquals(0L, scalar("SELECT COUNT(*) FROM phantom_profiles"), "Goal032 reseed suite requires a clean profile table.");
		context.record("goal032.reseed.database", "127.0.0.1:3308/l2jmobiush5_phantom_test");
		context.record("goal032.reseed.settings", "population=10,active=5");
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-reset-reseed-fresh-identities-and-restart", this::testResetReseedRestart);
		registry.add("02-config-disabled-reset-remains-success", this::testConfigDisabled);
	}

	private void testResetReseedRestart(PhantomTestContext context) throws Exception
	{
		startRuntime();
		awaitReadyPopulation();
		final Set<Identity> oldIdentities = identities();
		PhantomAssertions.assertEquals(10, oldIdentities.size(), "Initial 10/5 population identity cardinality drifted.");
		final PhantomPopulationResetService service = new PhantomPopulationResetService(System::currentTimeMillis, new SecureRandom(), reseedLifecycle(), _ -> { });
		final ResetPreview preview = service.preview();
		PhantomAssertions.assertTrue(preview.safe(), "10/5 reset preview was blocked: " + preview.blockers());
		PhantomAssertions.assertEquals(10, preview.identities(), "10/5 preview did not report ten identities.");
		final var result = service.confirm(preview.confirmationToken(), true);
		PhantomAssertions.assertEquals(ResetCode.RESET_RESEEDED, result.code(), "Reset + reseed did not start the existing PopulationManager path.");
		PhantomAssertions.assertTrue(result.resetCommitted() && result.reseeded(), "Reset + reseed result flags drifted.");

		awaitReadyPopulation();
		_reseeded = identities();
		PhantomAssertions.assertEquals(10, _reseeded.size(), "Reseed did not restore ten READY identities.");
		PhantomAssertions.assertTrue(disjointProfiles(oldIdentities, _reseeded), "Reseed reused an old Phantom profile ID.");
		PhantomAssertions.assertTrue(disjointCharacters(oldIdentities, _reseeded), "Reseed reused an old Phantom character ID.");
		PhantomAssertions.assertTrue(disjointAccounts(oldIdentities, _reseeded), "Reseed reused an old Phantom account.");
		for (Identity identity : oldIdentities)
		{
			PhantomAssertions.assertEquals(0L, scalar("SELECT COUNT(*) FROM phantom_profiles WHERE profile_id=?", identity.profileId()), "Old profile reappeared after reseed.");
			PhantomAssertions.assertEquals(0L, scalar("SELECT COUNT(*) FROM characters WHERE charId=? OR account_name=?", identity.characterObjectId(), identity.accountName()), "Old character/account identity reappeared after reseed.");
		}
		final PhantomSystem.OperatorStatus status = PhantomSystem.operatorStatus();
		PhantomAssertions.assertEquals(PhantomSystem.State.RUNNING, status.runtimeState(), "Reseed runtime is not RUNNING.");
		PhantomAssertions.assertEquals(PhantomScheduler.SchedulerState.RUNNING, status.schedulerState(), "Reseed Scheduler is not RUNNING.");
		PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine.State.RUNNING, status.decisionState(), "Reseed Decision Engine is not RUNNING.");

		shutdownRuntime();
		startRuntime();
		awaitReadyPopulation();
		PhantomAssertions.assertEquals(_reseeded, identities(), "Restart changed or duplicated the reseeded identity set.");
		PhantomAssertions.assertEquals(10L, scalar("SELECT COUNT(*) FROM phantom_profiles"), "Restart changed profile cardinality.");
		PhantomAssertions.assertEquals(10L, scalar("SELECT COUNT(DISTINCT character_object_id) FROM phantom_profiles"), "Restart duplicated a character link.");
		PhantomAssertions.assertEquals(10L, scalar("SELECT COUNT(DISTINCT account_name) FROM characters WHERE charId IN (SELECT character_object_id FROM phantom_profiles)"), "Restart duplicated a reserved account.");
		shutdownRuntime();
		context.record("goal032.reseed.fresh", "old=10,new=10,profile/character/account-disjoint=true");
		context.record("goal032.reseed.restart", "profiles=10,characters=10,accounts=10,scheduler/decision=RUNNING");
	}

	private void testConfigDisabled(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(10, _reseeded.size(), "Reseed case did not leave ten durable identities for config-disabled reset.");
		final Lifecycle disabled = new Lifecycle()
		{
			@Override
			public OperatorControlResult drain()
			{
				return control(OperatorControlCode.ALREADY_DRAINED, false, null);
			}

			@Override
			public OperatorControlResult reseed()
			{
				return control(OperatorControlCode.CONFIG_DISABLED, false, null);
			}
		};
		final PhantomPopulationResetService service = new PhantomPopulationResetService(System::currentTimeMillis, new SecureRandom(), disabled, _ -> { });
		final ResetPreview preview = service.preview();
		PhantomAssertions.assertTrue(preview.safe(), "Config-disabled reset preview was blocked: " + preview.blockers());
		final var result = service.confirm(preview.confirmationToken(), true);
		PhantomAssertions.assertEquals(ResetCode.RESET_CONFIG_DISABLED, result.code(), "Config-disabled reseed hid successful reset.");
		PhantomAssertions.assertTrue(result.resetCommitted(), "Config-disabled result did not retain reset success.");
		PhantomAssertions.assertFalse(result.reseeded(), "Config-disabled result claimed reseed.");
		PhantomAssertions.assertEquals(0L, scalar("SELECT COUNT(*) FROM phantom_profiles"), "Config-disabled reset retained profiles.");
		for (Identity identity : _reseeded)
		{
			PhantomAssertions.assertEquals(0L, scalar("SELECT COUNT(*) FROM characters WHERE charId=?", identity.characterObjectId()), "Config-disabled reset retained an old character.");
			PhantomAssertions.assertEquals(0L, scalar("SELECT COUNT(*) FROM accounts WHERE login=?", identity.accountName()), "Config-disabled reset retained an old account.");
		}
		context.record("goal032.reseed.configDisabled", "reset=committed,reseed=CONFIG_DISABLED");
	}

	private void startRuntime()
	{
		resetOperatorState();
		PhantomAssertions.assertTrue(PhantomSystem.startConfiguredForTesting(SETTINGS), "Goal032 10/5 production-composed runtime did not start.");
	}

	private void awaitReadyPopulation() throws Exception
	{
		await(60_000, () ->
		{
			final List<ManagedProfile> rows = managed();
			if (rows.size() != 10)
			{
				return false;
			}
			for (ManagedProfile row : rows)
			{
				final PhantomPopulationState state = _codec.decode(row.component().payload());
				if ((state.state() != PhantomPopulationState.State.READY) || (state.actualCharacterObjectId() == null) || !state.actualCharacterObjectId().equals(row.profile().characterObjectId()))
				{
					return false;
				}
			}
			return true;
		}, "Goal032 10/5 population did not reach ten READY identities.");
		final PhantomScheduler scheduler = Objects.requireNonNull(PhantomSystem.configuredScheduler());
		await(30_000, () -> scheduler.snapshot().registered() == 10, "Goal032 reseed Scheduler did not register ten profiles.");
	}

	private List<ManagedProfile> managed()
	{
		return _profiles.listManagedAfter(PhantomPopulationState.COMPONENT_TYPE, 0, 16);
	}

	private Set<Identity> identities()
	{
		final Set<Identity> identities = new HashSet<>();
		for (ManagedProfile row : managed())
		{
			final PhantomPopulationState state = _codec.decode(row.component().payload());
			identities.add(new Identity(row.profile().profileId(), Objects.requireNonNull(state.actualCharacterObjectId()), state.reservedAccount()));
		}
		return Set.copyOf(identities);
	}

	private static boolean disjointProfiles(Set<Identity> first, Set<Identity> second)
	{
		return first.stream().noneMatch(left -> second.stream().anyMatch(right -> left.profileId() == right.profileId()));
	}

	private static boolean disjointCharacters(Set<Identity> first, Set<Identity> second)
	{
		return first.stream().noneMatch(left -> second.stream().anyMatch(right -> left.characterObjectId() == right.characterObjectId()));
	}

	private static boolean disjointAccounts(Set<Identity> first, Set<Identity> second)
	{
		return first.stream().noneMatch(left -> second.stream().anyMatch(right -> left.accountName().equals(right.accountName())));
	}

	private static Lifecycle reseedLifecycle()
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
				if (PhantomSystem.startConfiguredForTesting(SETTINGS))
				{
					return control(OperatorControlCode.STARTED, true, PhantomSystem.State.RUNNING);
				}
				return control(OperatorControlCode.START_FAILED, PhantomSystem.hasConfiguredInstance(), PhantomSystem.hasConfiguredInstance() ? PhantomSystem.operatorStatus().runtimeState() : null);
			}
		};
	}

	private static OperatorControlResult control(OperatorControlCode code, boolean configured, PhantomSystem.State state)
	{
		return new OperatorControlResult(code, OperatorMode.DRAINED, configured, configured, state);
	}

	private static void shutdownRuntime() throws Exception
	{
		final Set<Integer> objectIds = new HashSet<>();
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("SELECT character_object_id FROM phantom_profiles WHERE character_object_id IS NOT NULL");
			ResultSet result = statement.executeQuery())
		{
			while (result.next())
			{
				objectIds.add(result.getInt(1));
			}
		}
		final long deadline = System.nanoTime() + 15_000_000_000L;
		while (PhantomSystem.hasConfiguredInstance() && (System.nanoTime() < deadline))
		{
			PhantomSystem.operatorDrain();
			if (PhantomSystem.hasConfiguredInstance())
			{
				Thread.sleep(20);
			}
		}
		PhantomAssertions.assertFalse(PhantomSystem.hasConfiguredInstance(), "Goal032 shutdown retained a configured runtime.");
		for (int objectId : objectIds)
		{
			PhantomAssertions.assertEquals(null, World.getInstance().getPlayer(objectId), "Goal032 shutdown retained an old World player.");
		}
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		Throwable failure = null;
		try
		{
			if (PhantomSystem.hasConfiguredInstance())
			{
				shutdownRuntime();
			}
			if (DatabaseFactory.isInitialized() && (scalar("SELECT COUNT(*) FROM phantom_profiles") > 0))
			{
				final Lifecycle cleanupLifecycle = new Lifecycle()
				{
					@Override
					public OperatorControlResult drain()
					{
						return control(OperatorControlCode.ALREADY_DRAINED, false, null);
					}

					@Override
					public OperatorControlResult reseed()
					{
						return control(OperatorControlCode.CONFIG_DISABLED, false, null);
					}
				};
				final PhantomPopulationResetService cleanup = new PhantomPopulationResetService(System::currentTimeMillis, new SecureRandom(), cleanupLifecycle, _ -> { });
				final ResetPreview preview = cleanup.preview();
				if (preview.safe())
				{
					cleanup.confirm(preview.confirmationToken(), false);
				}
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
		PhantomAssertions.assertFalse(PhantomSystem.hasConfiguredInstance(), "Operator reset retained a configured owner.");
		PhantomSystem.resetOperatorModeForTesting();
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
				PhantomAssertions.assertTrue(result.next(), "Goal032 reseed scalar query returned no row.");
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

	private record Identity(long profileId, int characterObjectId, String accountName)
	{
	}
}
