/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.chat.ChatObservationService;
import org.l2jmobius.gameserver.network.GameClient;
import org.l2jmobius.gameserver.phantoms.PhantomScheduler.SignalStatus;
import org.l2jmobius.gameserver.phantoms.PhantomSystem.OperatorControlCode;
import org.l2jmobius.gameserver.phantoms.PhantomSystem.OperatorMode;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerKind;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationState;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStateCodec;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfile;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository.ManagedProfile;
import org.l2jmobius.gameserver.taskmanagers.PlayerAutoSaveTaskManager;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomHeadlessPlayerTestEnvironment;
import org.l2jmobius.tests.phantoms.PhantomTestContext;
import org.l2jmobius.tests.phantoms.PhantomTestRegistry;
import org.l2jmobius.tests.phantoms.PhantomTestSuite;

public final class PhantomRestartFailureRecoveryGoal030Checkpoint3Suite implements PhantomTestSuite
{
	private static final long SEED = 30003003L;
	private static final String ACTIVE_SOURCE = "goal030.cp3.restart.active";
	private static final long ACTIVE_TTL_MILLIS = 600_000L;

	private final PhantomHeadlessPlayerTestEnvironment _environment = new PhantomHeadlessPlayerTestEnvironment();
	private final PhantomPopulationStateCodec _populationCodec = new PhantomPopulationStateCodec();
	private final PhantomPlayersConfig.Settings _settings = new PhantomPlayersConfig.Settings(true, true, 1, 4, 100, 4, 1, 0, 1, 8, 16, 32, ZoneOffset.UTC);
	private PhantomProfileRepository _profiles;
	private ManagedProfile _managed;
	private PhantomPopulationState _population;
	private DurableIdentity _identity;
	private boolean _environmentInitialized;

	@Override
	public String id()
	{
		return "restart-failure-recovery-goal030cp3";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Goal030 CP3 restart suite used the wrong seed.");
		resetOperatorState();
		PhantomAssertions.assertFalse(PhantomSystem.hasConfiguredInstance(), "A configured Phantom owner exists before the CP3 restart suite.");
		_environment.initialize(context);
		_environmentInitialized = true;
		_profiles = PhantomProfileRepository.open();
		PhantomAssertions.assertEquals(0L, scalar("SELECT COUNT(*) FROM phantom_profiles"), "CP3 restart suite requires a clean Phantom profile table.");
		PhantomAssertions.assertEquals(0L, scalar("SELECT COUNT(*) FROM phantom_profile_components"), "CP3 restart suite requires a clean Phantom component table.");
		context.record("goal030cp3.restart.database", "127.0.0.1:3308/l2jmobiush5_phantom_test");
		context.record("goal030cp3.restart.settings", "enabled=true,maxMaterialized=1,maxScheduled=4,population=1,active=0");
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-production-composed-durable-restart", this::testDurableRestart);
		registry.add("02-failed-shutdown-recovery-and-restart", this::testFailedShutdownRecovery);
		registry.add("03-server-shutdown-release-handoff", this::testServerShutdownHandoff);
	}

	private void testDurableRestart(PhantomTestContext context) throws Exception
	{
		startRuntime();
		awaitReadyPopulation();
		final Player firstPlayer = materialize();
		_identity = DurableIdentity.capture(_managed.profile(), _population);
		final PhantomProfileComponent populationBefore = _profiles.findComponent(_identity.profileId(), PhantomPopulationState.COMPONENT_TYPE).orElseThrow();
		final Set<String> componentTypesBefore = componentTypes(_identity.profileId());
		final long profileCountBefore = scalar("SELECT COUNT(*) FROM phantom_profiles");
		final long characterCountBefore = scalar("SELECT COUNT(*) FROM characters WHERE charId=?", _identity.characterObjectId());
		final long reservedCharacterCountBefore = scalar("SELECT COUNT(*) FROM characters WHERE account_name=?", _identity.reservedAccount());
		final long accountCountBefore = scalar("SELECT COUNT(*) FROM accounts WHERE login=?", _identity.reservedAccount());
		final PhantomScheduler firstScheduler = Objects.requireNonNull(PhantomSystem.configuredScheduler());
		final PhantomMaterializationService firstMaterialization = Objects.requireNonNull(PhantomSystem.configuredMaterializationService());

		PhantomAssertions.assertTrue(PhantomSystem.shutdownIfStarted(), "First production-composed CP3 shutdown did not reach STOPPED.");
		assertRuntimeStopped(firstScheduler, firstMaterialization, _identity.characterObjectId());
		final PhantomProfileComponent populationAfterShutdown = _profiles.findComponent(_identity.profileId(), PhantomPopulationState.COMPONENT_TYPE).orElseThrow();
		PhantomAssertions.assertEquals(populationBefore.componentSchemaVersion(), populationAfterShutdown.componentSchemaVersion(), "Shutdown changed the Population component schema.");
		PhantomAssertions.assertEquals(populationBefore.rowVersion(), populationAfterShutdown.rowVersion(), "Shutdown mutated the durable Population component.");
		PhantomAssertions.assertTrue(Arrays.equals(populationBefore.payload(), populationAfterShutdown.payload()), "Shutdown changed durable Population identity bytes.");
		PhantomAssertions.assertEquals(componentTypesBefore, componentTypes(_identity.profileId()), "Shutdown removed or added persistent component identities.");
		assertDurableCounts(profileCountBefore, characterCountBefore, reservedCharacterCountBefore, accountCountBefore);

		startRuntime();
		awaitReadyPopulation();
		assertSameIdentity(_identity, _managed.profile(), _population);
		PhantomAssertions.assertEquals(profileCountBefore, scalar("SELECT COUNT(*) FROM phantom_profiles"), "Restart created a duplicate Phantom profile.");
		PhantomAssertions.assertEquals(characterCountBefore, scalar("SELECT COUNT(*) FROM characters WHERE charId=?", _identity.characterObjectId()), "Restart created a duplicate canonical character.");
		PhantomAssertions.assertEquals(reservedCharacterCountBefore, scalar("SELECT COUNT(*) FROM characters WHERE account_name=?", _identity.reservedAccount()), "Restart duplicated the reserved-account character.");
		PhantomAssertions.assertEquals(accountCountBefore, scalar("SELECT COUNT(*) FROM accounts WHERE login=?", _identity.reservedAccount()), "Restart duplicated the reserved account.");
		final Player restartedPlayer = materialize();
		PhantomAssertions.assertEquals(firstPlayer.getObjectId(), restartedPlayer.getObjectId(), "Restart materialized a different character object ID.");
		PhantomAssertions.assertEquals(OwnerKind.PHANTOM, PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(_identity.characterObjectId()), "Restart did not reacquire exactly one PHANTOM identity lease.");

		context.record("goal030cp3.restart.identity", _identity.profileId() + "/" + _identity.characterObjectId() + "/" + _identity.reservedAccount());
		context.record("goal030cp3.restart.componentTypes", componentTypesBefore);
		context.record("goal030cp3.restart.counts", "profiles=" + profileCountBefore + ",characters=" + characterCountBefore + ",accounts=" + accountCountBefore);
		context.record("goal030cp3.restart.functional", "sameCharacter=true,world=true,lease=PHANTOM");
	}

	private void testFailedShutdownRecovery(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertTrue((_identity != null) && PhantomSystem.hasConfiguredInstance(), "Durable restart case did not leave a running production-composed runtime.");
		final PhantomScheduler retainedScheduler = Objects.requireNonNull(PhantomSystem.configuredScheduler());
		final PhantomMaterializationService retainedMaterialization = Objects.requireNonNull(PhantomSystem.configuredMaterializationService());
		final long profilesBefore = scalar("SELECT COUNT(*) FROM phantom_profiles");
		final long charactersBefore = scalar("SELECT COUNT(*) FROM characters WHERE charId=?", _identity.characterObjectId());
		final long componentsBefore = scalar("SELECT COUNT(*) FROM phantom_profile_components WHERE profile_id=?", _identity.profileId());

		PhantomSystem.injectConfiguredShutdownFailureForTesting();
		try
		{
			final var failed = PhantomSystem.operatorDrain();
			PhantomAssertions.assertEquals(OperatorControlCode.SHUTDOWN_FAILED, failed.code(), "Injected shutdown failure was reported as a clean drain.");
			PhantomAssertions.assertEquals(OperatorMode.DRAINED, failed.desiredMode(), "Failed drain lost the requested DRAINED intent.");
			PhantomAssertions.assertEquals(PhantomSystem.State.FAILED, failed.runtimeState(), "Injected shutdown failure did not expose FAILED.");
			PhantomAssertions.assertTrue(failed.runtimeConfigured(), "Failed shutdown discarded the configured owner.");
			PhantomAssertions.assertFalse(failed.desiredRuntimeEnabled(), "Failed drain still reports a desired running state.");
			final PhantomSystem.ConfiguredShutdownSnapshot failedSnapshot = PhantomSystem.configuredShutdownSnapshot();
			PhantomAssertions.assertTrue(failedSnapshot.configured(), "FAILED runtime has no observable configured owner.");
			PhantomAssertions.assertEquals(PhantomSystem.State.FAILED, failedSnapshot.systemState(), "Configured shutdown evidence hid FAILED.");
			PhantomAssertions.assertTrue(failedSnapshot.materializationServiceState() != PhantomMaterializationService.ServiceState.STOPPED, "Injected pre-cleanup failure falsely published materialization STOPPED.");
			PhantomAssertions.assertEquals(1, failedSnapshot.retainedMaterializationEntries(), "FAILED runtime lost its exact materialized actor ownership.");
			PhantomAssertions.assertTrue(World.getInstance().getPlayer(_identity.characterObjectId()) != null, "Injected failure silently removed the World actor.");
			PhantomAssertions.assertEquals(OwnerKind.PHANTOM, PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(_identity.characterObjectId()), "Injected failure silently released the PHANTOM lease.");
			PhantomAssertions.assertTrue(retainedScheduler == PhantomSystem.configuredScheduler(), "FAILED recovery replaced the configured Scheduler owner.");
			PhantomAssertions.assertTrue(retainedMaterialization == PhantomSystem.configuredMaterializationService(), "FAILED recovery replaced the configured Materialization owner.");
		}
		finally
		{
			PhantomSystem.releaseOperatorShutdownFailureForTesting();
		}
		final var recovered = PhantomSystem.operatorDrain();
		PhantomAssertions.assertEquals(OperatorControlCode.DRAINED, recovered.code(), "State.FAILED recovery did not complete the retained drain.");
		PhantomAssertions.assertEquals(OperatorMode.DRAINED, recovered.desiredMode(), "Recovered drain lost DRAINED intent.");
		PhantomAssertions.assertFalse(recovered.runtimeConfigured(), "Recovered drain retained the configured owner.");
		assertRuntimeStopped(retainedScheduler, retainedMaterialization, _identity.characterObjectId());
		PhantomAssertions.assertEquals(profilesBefore, scalar("SELECT COUNT(*) FROM phantom_profiles"), "FAILED recovery deleted or duplicated the durable profile.");
		PhantomAssertions.assertEquals(charactersBefore, scalar("SELECT COUNT(*) FROM characters WHERE charId=?", _identity.characterObjectId()), "FAILED recovery deleted or duplicated the canonical character.");
		PhantomAssertions.assertEquals(componentsBefore, scalar("SELECT COUNT(*) FROM phantom_profile_components WHERE profile_id=?", _identity.profileId()), "FAILED recovery corrupted persistent component cardinality.");

		PhantomSystem.resetOperatorModeForTesting();
		startRuntime();
		awaitReadyPopulation();
		assertSameIdentity(_identity, _managed.profile(), _population);
		materialize();
		final PhantomScheduler recoveredScheduler = Objects.requireNonNull(PhantomSystem.configuredScheduler());
		final PhantomMaterializationService recoveredMaterialization = Objects.requireNonNull(PhantomSystem.configuredMaterializationService());
		PhantomAssertions.assertTrue(PhantomSystem.shutdownIfStarted(), "Runtime did not shut down after FAILED recovery restart.");
		assertRuntimeStopped(recoveredScheduler, recoveredMaterialization, _identity.characterObjectId());

		context.record("goal030cp3.failure.injected", "result=SHUTDOWN_FAILED,state=FAILED,owner=true,mode=DRAINED");
		context.record("goal030cp3.failure.recovery", "state=STOPPED,owner=false,world=false,lease=false");
		context.record("goal030cp3.failure.restart", "sameProfile=true,sameCharacter=true,functional=true");
	}

	private void testServerShutdownHandoff(PhantomTestContext context) throws Exception
	{
		final String source = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/Shutdown.java"), StandardCharsets.UTF_8);
		final String shutdownCall = "PhantomSystem.shutdownIfStarted()";
		final int firstShutdown = source.indexOf(shutdownCall);
		final int disconnect = source.indexOf("disconnectAllCharacters();", firstShutdown + shutdownCall.length());
		final int secondShutdown = source.indexOf(shutdownCall, firstShutdown + shutdownCall.length());
		final int threadPool = source.indexOf("ThreadPool.shutdown();", secondShutdown + shutdownCall.length());
		PhantomAssertions.assertTrue((firstShutdown >= 0) && (firstShutdown < disconnect) && (disconnect < secondShutdown) && (secondShutdown < threadPool), "Shutdown handoff order is not initial drain < disconnect < final retry < ThreadPool.");
		PhantomAssertions.assertEquals(-1, source.indexOf(shutdownCall, secondShutdown + shutdownCall.length()), "Shutdown.java contains more than two server-level Phantom drain attempts.");
		final String finalWindow = source.substring(secondShutdown, threadPool);
		PhantomAssertions.assertTrue(finalWindow.contains("LOGGER.severe") && finalWindow.contains("Final subsystem drain is incomplete") && finalWindow.contains("Shared ThreadPool is about to stop with retained Phantom ownership"), "Final incomplete Phantom drain is not observable as severe retained-ownership evidence.");
		context.record("goal030cp3.shutdown.order", "initial-phantom-drain<disconnectAllCharacters<final-phantom-drain<ThreadPool.shutdown");
		context.record("goal030cp3.shutdown.failureEvidence", "LOGGER.severe/final-incomplete/retained-ownership");
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		Throwable failure = null;
		try
		{
			PhantomSystem.releaseOperatorShutdownFailureForTesting();
			final long deadline = System.nanoTime() + 5_000_000_000L;
			while (PhantomSystem.hasConfiguredInstance() && (System.nanoTime() < deadline))
			{
				PhantomSystem.operatorDisable();
				if (PhantomSystem.hasConfiguredInstance())
				{
					Thread.sleep(20);
				}
			}
			if (PhantomSystem.hasConfiguredInstance())
			{
				throw new AssertionError("CP3 restart suite retained a configured PhantomSystem owner.");
			}
			if (DatabaseFactory.isInitialized())
			{
				cleanupOwnedPopulation();
			}
			PhantomSystem.resetOperatorModeForTesting();
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

	private void startRuntime()
	{
		PhantomAssertions.assertFalse(PhantomSystem.hasConfiguredInstance(), "A configured owner already exists before production-composed startup.");
		PhantomAssertions.assertTrue(PhantomSystem.startConfiguredForTesting(_settings), "Full production-composed PhantomSystem did not start.");
	}

	private void awaitReadyPopulation() throws Exception
	{
		await(30_000, this::refreshReadyPopulation, "Population did not recover exactly one READY profile within 30 seconds.");
		final PhantomScheduler scheduler = Objects.requireNonNull(PhantomSystem.configuredScheduler());
		await(30_000, () -> (scheduler.snapshot().registered() == 1) && (_managed != null) && scheduler.find(_managed.profile().profileId()).isPresent(), "Population did not restore exactly one Scheduler owner.");
	}

	private boolean refreshReadyPopulation()
	{
		final List<ManagedProfile> rows = _profiles.listManagedAfter(PhantomPopulationState.COMPONENT_TYPE, 0, 2);
		if (rows.size() > 1)
		{
			throw new AssertionError("Population created a duplicate CP3 profile.");
		}
		if (rows.size() != 1)
		{
			return false;
		}
		final ManagedProfile row = rows.getFirst();
		final PhantomPopulationState state = _populationCodec.decode(row.component().payload());
		if ((state.state() != PhantomPopulationState.State.READY) || (state.actualCharacterObjectId() == null) || !state.actualCharacterObjectId().equals(row.profile().characterObjectId()))
		{
			return false;
		}
		_managed = row;
		_population = state;
		return true;
	}

	private Player materialize() throws Exception
	{
		final PhantomScheduler scheduler = Objects.requireNonNull(PhantomSystem.configuredScheduler());
		final PhantomMaterializationService materialization = Objects.requireNonNull(PhantomSystem.configuredMaterializationService());
		final SignalStatus status = scheduler.submitSignal(_managed.profile().profileId(), new PhantomRelevanceSignal(ACTIVE_SOURCE, 1, PhantomActivityState.ACTIVE, ACTIVE_TTL_MILLIS)).status();
		PhantomAssertions.assertTrue((status == SignalStatus.ACCEPTED) || (status == SignalStatus.COALESCED), "CP3 ACTIVE signal was not accepted.");
		await(15_000, () -> materialization.find(_managed.profile().profileId()).filter(value -> value.worldPresent() && value.outboundAttached() && value.identityLeaseRetained()).isPresent(), "Production Scheduler did not materialize the CP3 Phantom.");
		final Player player = World.getInstance().getPlayer(_population.actualCharacterObjectId());
		PhantomAssertions.assertTrue(player != null, "Materialized CP3 Phantom is absent from World.");
		PhantomAssertions.assertTrue(player.hasHeadlessOutboundSession(), "Materialized CP3 Phantom has no headless outbound session.");
		PhantomAssertions.assertEquals(OwnerKind.PHANTOM, PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(player.getObjectId()), "Materialized CP3 Phantom has no PHANTOM lease.");
		PhantomAssertions.assertEquals(1, materialization.snapshot().retainedEntries(), "Production runtime did not retain exactly one materialized entry.");
		return player;
	}

	private static void assertRuntimeStopped(PhantomScheduler scheduler, PhantomMaterializationService materialization, int characterObjectId)
	{
		PhantomAssertions.assertFalse(PhantomSystem.hasConfiguredInstance(), "Terminal shutdown retained the configured singleton.");
		PhantomAssertions.assertEquals(PhantomScheduler.SchedulerState.STOPPED, scheduler.snapshot().state(), "Terminal shutdown retained Scheduler ownership.");
		PhantomAssertions.assertEquals(0, scheduler.snapshot().registered(), "Terminal shutdown retained Scheduler registrations.");
		PhantomAssertions.assertEquals(PhantomMaterializationService.ServiceState.STOPPED, materialization.snapshot().state(), "Terminal shutdown retained MaterializationService ownership.");
		PhantomAssertions.assertEquals(0, materialization.snapshot().retainedEntries(), "Terminal shutdown retained materialization entries.");
		PhantomAssertions.assertEquals(null, World.getInstance().getPlayer(characterObjectId), "Terminal shutdown retained the Phantom Player in World.");
		PhantomAssertions.assertEquals(null, World.getInstance().findObject(characterObjectId), "Terminal shutdown retained the Phantom World object.");
		PhantomAssertions.assertEquals(null, PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(characterObjectId), "Terminal shutdown retained the PHANTOM identity lease.");
		PhantomAssertions.assertFalse(PlayerAutoSaveTaskManager.getInstance().containsObjectId(characterObjectId), "Terminal shutdown retained Player autosave ownership.");
		PhantomAssertions.assertFalse(ChatObservationService.getInstance().snapshot().observerRegistered(), "Terminal shutdown retained the chat observer.");
		PhantomAssertions.assertEquals(PhantomScheduler.SchedulerState.STOPPED, PhantomSystem.operatorStatus().schedulerState(), "Stopped operator status reports a Scheduler owner.");
		PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine.State.STOPPED, PhantomSystem.operatorStatus().decisionState(), "Stopped operator status reports a Decision owner.");
		PhantomAssertions.assertEquals(0L, PhantomSystem.operatorStatus().activeCurrent(), "Stopped operator status reports ACTIVE Phantoms.");
	}

	private void assertDurableCounts(long profiles, long characters, long reservedCharacters, long accounts) throws Exception
	{
		PhantomAssertions.assertEquals(profiles, scalar("SELECT COUNT(*) FROM phantom_profiles"), "Shutdown changed the durable profile count.");
		PhantomAssertions.assertEquals(characters, scalar("SELECT COUNT(*) FROM characters WHERE charId=?", _identity.characterObjectId()), "Shutdown changed the canonical character count.");
		PhantomAssertions.assertEquals(reservedCharacters, scalar("SELECT COUNT(*) FROM characters WHERE account_name=?", _identity.reservedAccount()), "Shutdown changed the reserved-account character count.");
		PhantomAssertions.assertEquals(accounts, scalar("SELECT COUNT(*) FROM accounts WHERE login=?", _identity.reservedAccount()), "Shutdown changed the reserved account count.");
	}

	private static void assertSameIdentity(DurableIdentity expected, PhantomProfile profile, PhantomPopulationState state)
	{
		PhantomAssertions.assertEquals(expected, DurableIdentity.capture(profile, state), "Restart changed the durable Population identity.");
	}

	private Set<String> componentTypes(long profileId)
	{
		return _profiles.listComponents(profileId).stream().map(PhantomProfileComponent::componentType).collect(Collectors.toUnmodifiableSet());
	}

	private void cleanupOwnedPopulation() throws Exception
	{
		final List<ManagedProfile> rows = _profiles.listManagedAfter(PhantomPopulationState.COMPONENT_TYPE, 0, 2);
		for (ManagedProfile row : rows)
		{
			final PhantomPopulationState state = _populationCodec.decode(row.component().payload());
			final int objectId = Objects.requireNonNull(state.actualCharacterObjectId(), "Owned CP3 character ID is absent.");
			final Player world = World.getInstance().getPlayer(objectId);
			if (world != null)
			{
				_environment.cleanupLoadedPlayer(world);
			}
			PhantomProfile profile = _profiles.find(row.profile().profileId()).orElse(null);
			if (profile != null)
			{
				if (profile.characterObjectId() != null)
				{
					profile = _profiles.updateCharacterLink(profile.profileId(), profile.rowVersion(), null);
				}
				_profiles.delete(profile.profileId(), profile.rowVersion());
			}
			GameClient.deleteCharByObjId(objectId);
			try (Connection connection = DatabaseFactory.getConnection();
				PreparedStatement statement = connection.prepareStatement("DELETE FROM accounts WHERE login=?"))
			{
				statement.setString(1, state.reservedAccount());
				statement.executeUpdate();
			}
		}
		PhantomAssertions.assertEquals(0L, scalar("SELECT COUNT(*) FROM phantom_profiles"), "CP3 restart cleanup retained a Phantom profile.");
		PhantomAssertions.assertEquals(0L, scalar("SELECT COUNT(*) FROM phantom_profile_components"), "CP3 restart cleanup retained a Phantom component.");
	}

	private static void resetOperatorState()
	{
		PhantomSystem.releaseOperatorShutdownFailureForTesting();
		if (PhantomSystem.hasConfiguredInstance())
		{
			PhantomSystem.operatorDisable();
		}
		if (PhantomSystem.hasConfiguredInstance())
		{
			throw new AssertionError("Operator reset retained a configured PhantomSystem owner.");
		}
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
				PhantomAssertions.assertTrue(result.next(), "CP3 scalar query returned no row.");
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

	private record DurableIdentity(long profileId, int characterObjectId, String reservedAccount, String ownershipToken, String characterName, int classId, boolean female, long populationGeneration, long creationOrdinal, long deterministicSeed, String catalogHash, String initializationAuthorityHash, String initializationHash)
	{
		private static DurableIdentity capture(PhantomProfile profile, PhantomPopulationState state)
		{
			return new DurableIdentity(profile.profileId(), Objects.requireNonNull(state.actualCharacterObjectId()), state.reservedAccount(), state.ownershipToken(), state.characterName(), state.classId(), state.female(), state.populationGeneration(), state.creationOrdinal(), state.deterministicSeed(), state.catalogHash(), state.initializationAuthorityHash(), state.initializationHash());
		}
	}
}
