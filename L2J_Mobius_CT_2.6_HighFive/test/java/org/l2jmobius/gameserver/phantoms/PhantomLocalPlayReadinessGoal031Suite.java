/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.chat.ChatObservationService;
import org.l2jmobius.gameserver.network.GameClient;
import org.l2jmobius.gameserver.phantoms.PhantomSystem.OperatorControlCode;
import org.l2jmobius.gameserver.phantoms.PhantomSelectedDecisionTrace.DecisionView;
import org.l2jmobius.gameserver.phantoms.PhantomSelectedDecisionTrace.SelectionStatus;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerKind;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationState;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStateCodec;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfile;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository.ManagedProfile;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomHeadlessPlayerTestEnvironment;
import org.l2jmobius.tests.phantoms.PhantomTestContext;
import org.l2jmobius.tests.phantoms.PhantomTestRegistry;
import org.l2jmobius.tests.phantoms.PhantomTestSuite;

public final class PhantomLocalPlayReadinessGoal031Suite implements PhantomTestSuite
{
	private static final long SEED = 31003101L;
	private static final String ACTIVE_SOURCE = "goal031.local-play.active";
	private static final long ACTIVE_TTL_MILLIS = 600_000L;
	private final PhantomHeadlessPlayerTestEnvironment _environment = new PhantomHeadlessPlayerTestEnvironment();
	private final PhantomPopulationStateCodec _populationCodec = new PhantomPopulationStateCodec();
	private PhantomPlayersConfig.Settings _settings;
	private PhantomProfileRepository _profiles;
	private List<ManagedProfile> _managed = List.of();
	private List<PhantomPopulationState> _population = List.of();
	private Set<DurableIdentity> _identities = Set.of();
	private boolean _environmentInitialized;

	@Override
	public String id()
	{
		return "phantom-local-play-readiness-goal031";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Goal031 readiness suite used the wrong seed.");
		final Path preset = context.moduleRoot().resolve("docs/phantoms/examples/PhantomPlayers.local-play.ini");
		final PhantomPlayersConfig.Settings presetSettings = PhantomPlayersConfig.read(preset);
		PhantomAssertions.assertTrue(presetSettings.enabled(), "Local-play preset is not enabled under production parser semantics.");
		PhantomAssertions.assertEquals(10, presetSettings.populationTarget(), "Local-play population target changed.");
		PhantomAssertions.assertEquals(5, presetSettings.populationActiveTarget(), "Local-play ACTIVE target changed.");
		// Diagnostics is enabled only in this test process to observe an existing production decision; all preset limits remain exact.
		_settings = new PhantomPlayersConfig.Settings(true, true, presetSettings.maxMaterializedPhantoms(), presetSettings.maxScheduledPhantomProfiles(), presetSettings.schedulerPulseMillis(), presetSettings.schedulerProfilesPerPulse(), presetSettings.populationTarget(), presetSettings.populationActiveTarget(), presetSettings.populationCreationInFlight(), presetSettings.populationBoundariesPerPulse(), presetSettings.partyOperationsPerPulse(), presetSettings.socialCacheProfiles(), presetSettings.populationTimeZone());
		resetOperatorState();
		_environment.initialize(context);
		_environmentInitialized = true;
		_profiles = PhantomProfileRepository.open();
		final long recoveredProfiles = scalar("SELECT COUNT(*) FROM phantom_profiles");
		if (recoveredProfiles > 0)
		{
			cleanupOwnedPopulation();
		}
		PhantomAssertions.assertEquals(0L, scalar("SELECT COUNT(*) FROM phantom_profiles"), "Goal031 readiness requires a clean guarded Phantom profile table.");
		context.record("goal031.readiness.recoveredProfiles", recoveredProfiles);
		context.record("goal031.readiness.database", "127.0.0.1:3308/l2jmobiush5_phantom_test");
		context.record("goal031.readiness.settings", "enabled=true,maxMaterialized=32,maxScheduled=10000,population=10,active=5");
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-production-composed-local-play", this::testLocalPlay);
		registry.add("02-durable-restart-no-duplicates", this::testRestart);
		registry.add("03-drain-disable-rollback", this::testRollback);
	}

	private void testLocalPlay(PhantomTestContext context) throws Exception
	{
		startRuntime();
		awaitReadyPopulation();
		_identities = captureIdentities();
		PhantomAssertions.assertEquals(SelectionStatus.SELECTED, PhantomSystem.selectOperatorTrace(_managed.getLast().profile().profileId()), "Selected production trace could not attach.");
		final Player player = awaitMaterializedPlayer();
		await(10_000, () -> autonomousDecision() != null, "No autonomous production decision was observed for the selected local-play profile.");
		final String autonomousDecision = autonomousDecision();
		final PhantomSystem.OperatorStatus status = PhantomSystem.operatorStatus();
		PhantomAssertions.assertEquals(PhantomSystem.State.RUNNING, status.runtimeState(), "Operator status does not expose RUNNING runtime.");
		PhantomAssertions.assertEquals(PhantomScheduler.SchedulerState.RUNNING, status.schedulerState(), "Operator status does not expose RUNNING Scheduler.");
		PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine.State.RUNNING, status.decisionState(), "Operator status does not expose RUNNING Decision Engine.");
		PhantomAssertions.assertTrue(status.activeCurrent() > 0, "Operator status reports zero ACTIVE Phantoms for the 10/5 preset.");
		PhantomAssertions.assertTrue(status.activityStateCounts().stream().mapToLong(Long::longValue).sum() > 0, "Operator status exposes no population activity.");
		PhantomAssertions.assertTrue(player.hasHeadlessOutboundSession(), "Materialized Phantom has no headless outbound session.");
		PhantomAssertions.assertEquals(OwnerKind.PHANTOM, PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(player.getObjectId()), "Materialized Player has no PHANTOM identity lease.");
		context.record("goal031.readiness.first", "profiles=10,active=" + status.activeCurrent() + ",decision=" + autonomousDecision + ",player=" + player.getObjectId());
		shutdownAndAssertReleased();
	}
	private void testRestart(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(10, _identities.size(), "Initial local-play case did not preserve ten durable identities.");
		startRuntime();
		awaitReadyPopulation();
		final Set<DurableIdentity> restarted = captureIdentities();
		PhantomAssertions.assertEquals(_identities, restarted, "Restart changed or duplicated durable Phantom identities.");
		PhantomAssertions.assertEquals(10L, scalar("SELECT COUNT(*) FROM phantom_profiles"), "Restart changed Phantom profile cardinality.");
		PhantomAssertions.assertEquals(10L, scalar("SELECT COUNT(DISTINCT character_object_id) FROM phantom_profiles"), "Restart duplicated a character link.");
		for (DurableIdentity identity : restarted)
		{
			PhantomAssertions.assertEquals(1L, scalar("SELECT COUNT(*) FROM characters WHERE charId=?", identity.characterObjectId()), "Restart duplicated or lost the canonical character.");
			PhantomAssertions.assertEquals(1L, scalar("SELECT COUNT(*) FROM accounts WHERE login=?", identity.reservedAccount()), "Restart duplicated or lost the reserved account.");
		}
		final Player player = awaitMaterializedPlayer();
		PhantomAssertions.assertTrue(restarted.stream().anyMatch(identity -> identity.characterObjectId() == player.getObjectId()), "Restart materialized an unknown identity.");
		context.record("goal031.readiness.restart", "sameProfiles=10,sameCharacters=10,sameAccounts=10,world=true");
		shutdownAndAssertReleased();
	}

	private void testRollback(PhantomTestContext context) throws Exception
	{
		startRuntime();
		awaitReadyPopulation();
		awaitMaterializedPlayer();
		final PhantomScheduler drainScheduler = Objects.requireNonNull(PhantomSystem.configuredScheduler());
		final PhantomMaterializationService drainMaterialization = Objects.requireNonNull(PhantomSystem.configuredMaterializationService());
		var drained = PhantomSystem.operatorDrain();
		final long drainDeadline = System.nanoTime() + 10_000_000_000L;
		while (PhantomSystem.hasConfiguredInstance() && (System.nanoTime() < drainDeadline))
		{
			Thread.sleep(20);
			drained = PhantomSystem.operatorDrain();
		}
		PhantomAssertions.assertEquals(OperatorControlCode.DRAINED, drained.code(), "Canonical bounded drain did not report DRAINED.");
		PhantomAssertions.assertEquals(OperatorControlCode.ALREADY_DRAINED, PhantomSystem.operatorDrain().code(), "Repeated drain is not idempotent.");
		assertReleased(drainScheduler, drainMaterialization);
		PhantomAssertions.assertEquals(10L, scalar("SELECT COUNT(*) FROM phantom_profiles"), "Drain removed durable identities.");

		PhantomSystem.resetOperatorModeForTesting();
		startRuntime();
		awaitReadyPopulation();
		awaitMaterializedPlayer();
		final PhantomScheduler disableScheduler = Objects.requireNonNull(PhantomSystem.configuredScheduler());
		final PhantomMaterializationService disableMaterialization = Objects.requireNonNull(PhantomSystem.configuredMaterializationService());
		var disabled = PhantomSystem.operatorDisable();
		final long disableDeadline = System.nanoTime() + 10_000_000_000L;
		while (PhantomSystem.hasConfiguredInstance() && (System.nanoTime() < disableDeadline))
		{
			Thread.sleep(20);
			disabled = PhantomSystem.operatorDisable();
		}
		PhantomAssertions.assertEquals(OperatorControlCode.DISABLED, disabled.code(), "Canonical bounded disable did not report DISABLED.");
		PhantomAssertions.assertEquals(OperatorControlCode.ALREADY_DISABLED, PhantomSystem.operatorDisable().code(), "Repeated disable is not idempotent.");
		assertReleased(disableScheduler, disableMaterialization);
		PhantomAssertions.assertEquals(_identities, capturePersistentIdentities(), "Drain/disable changed durable identities.");
		context.record("goal031.readiness.rollback", "drain=DRAINED/ALREADY_DRAINED,disable=DISABLED/ALREADY_DISABLED,durable=10");
	}

	private void startRuntime()
	{
		PhantomAssertions.assertFalse(PhantomSystem.hasConfiguredInstance(), "Configured Phantom owner exists before Goal031 startup.");
		PhantomAssertions.assertTrue(PhantomSystem.startConfiguredForTesting(_settings), "Production-composed PhantomSystem did not start from local-play settings.");
	}

	private void awaitReadyPopulation() throws Exception
	{
		await(45_000, this::refreshReadyPopulation, "Population did not create exactly ten READY durable identities.");
		final PhantomScheduler scheduler = Objects.requireNonNull(PhantomSystem.configuredScheduler());
		await(30_000, () -> scheduler.snapshot().registered() == 10, "Scheduler did not register all ten local-play profiles.");
	}

	private boolean refreshReadyPopulation()
	{
		final List<ManagedProfile> rows = _profiles.listManagedAfter(PhantomPopulationState.COMPONENT_TYPE, 0, 11);
		if (rows.size() > 10)
		{
			throw new AssertionError("Population exceeded the local-play target.");
		}
		if (rows.size() != 10)
		{
			return false;
		}
		final List<PhantomPopulationState> states = new ArrayList<>(10);
		for (ManagedProfile row : rows)
		{
			final PhantomPopulationState state = _populationCodec.decode(row.component().payload());
			if ((state.state() != PhantomPopulationState.State.READY) || (state.actualCharacterObjectId() == null) || !state.actualCharacterObjectId().equals(row.profile().characterObjectId()))
			{
				return false;
			}
			states.add(state);
		}
		_managed = List.copyOf(rows);
		_population = List.copyOf(states);
		return true;
	}
	private Player awaitMaterializedPlayer() throws Exception
	{
		final ManagedProfile row = _managed.getLast();
		final PhantomScheduler scheduler = Objects.requireNonNull(PhantomSystem.configuredScheduler());
		final PhantomMaterializationService materialization = Objects.requireNonNull(PhantomSystem.configuredMaterializationService());
		final PhantomScheduler.SignalStatus signal = scheduler.submitSignal(row.profile().profileId(), new PhantomRelevanceSignal(ACTIVE_SOURCE, 1, PhantomActivityState.ACTIVE, ACTIVE_TTL_MILLIS)).status();
		PhantomAssertions.assertTrue((signal == PhantomScheduler.SignalStatus.ACCEPTED) || (signal == PhantomScheduler.SignalStatus.COALESCED), "Local-play ACTIVE signal was not accepted by the production Scheduler.");
		await(20_000, () -> materialization.find(row.profile().profileId()).filter(value -> value.worldPresent() && value.outboundAttached() && value.identityLeaseRetained()).isPresent(), "Production Scheduler did not materialize the selected local-play profile.");
		final int objectId = Objects.requireNonNull(_population.getLast().actualCharacterObjectId());
		final Player player = World.getInstance().getPlayer(objectId);
		PhantomAssertions.assertTrue(player != null, "Materialized local-play Phantom is absent from World.");
		return player;
	}

	private String autonomousDecision()
	{
		final PhantomSelectedDecisionTrace trace = PhantomSystem.configuredSelectedTraceForTesting();
		if (trace == null)
		{
			return null;
		}
		final List<DecisionView> views = new ArrayList<>(trace.snapshot().history());
		if (trace.snapshot().current() != null)
		{
			views.add(trace.snapshot().current());
		}
		for (DecisionView view : views)
		{
			if ((view.candidateKey() != null) && !view.candidateKey().isBlank())
			{
				return view.candidateKey() + "/" + view.reasonKey();
			}
		}
		return null;
	}

	private Set<DurableIdentity> captureIdentities()
	{
		final Set<DurableIdentity> identities = new HashSet<>();
		for (int index = 0; index < _managed.size(); index++)
		{
			identities.add(DurableIdentity.capture(_managed.get(index).profile(), _population.get(index)));
		}
		PhantomAssertions.assertEquals(10, identities.size(), "Population did not create ten distinct durable identities.");
		return Set.copyOf(identities);
	}

	private Set<DurableIdentity> capturePersistentIdentities()
	{
		final List<ManagedProfile> rows = _profiles.listManagedAfter(PhantomPopulationState.COMPONENT_TYPE, 0, 11);
		final Set<DurableIdentity> identities = new HashSet<>();
		for (ManagedProfile row : rows)
		{
			identities.add(DurableIdentity.capture(row.profile(), _populationCodec.decode(row.component().payload())));
		}
		return Set.copyOf(identities);
	}

	private void shutdownAndAssertReleased() throws Exception
	{
		final PhantomScheduler scheduler = Objects.requireNonNull(PhantomSystem.configuredScheduler());
		final PhantomMaterializationService materialization = Objects.requireNonNull(PhantomSystem.configuredMaterializationService());
		PhantomSystem.shutdownIfStarted();
		await(10_000, () ->
		{
			if (PhantomSystem.hasConfiguredInstance())
			{
				PhantomSystem.operatorDrain();
			}
			return !PhantomSystem.hasConfiguredInstance();
		}, "Canonical bounded shutdown retry did not reach STOPPED.");
		assertReleased(scheduler, materialization);
		PhantomSystem.resetOperatorModeForTesting();
	}

	private void assertReleased(PhantomScheduler scheduler, PhantomMaterializationService materialization)
	{
		PhantomAssertions.assertFalse(PhantomSystem.hasConfiguredInstance(), "Terminal shutdown retained the configured singleton.");
		PhantomAssertions.assertEquals(PhantomScheduler.SchedulerState.STOPPED, scheduler.snapshot().state(), "Terminal shutdown retained Scheduler ownership.");
		PhantomAssertions.assertEquals(0, scheduler.snapshot().registered(), "Terminal shutdown retained Scheduler registrations.");
		PhantomAssertions.assertEquals(PhantomMaterializationService.ServiceState.STOPPED, materialization.snapshot().state(), "Terminal shutdown retained Materialization ownership.");
		PhantomAssertions.assertEquals(0, materialization.snapshot().retainedEntries(), "Terminal shutdown retained materialized entries.");
		for (DurableIdentity identity : _identities)
		{
			PhantomAssertions.assertEquals(null, World.getInstance().getPlayer(identity.characterObjectId()), "Terminal shutdown retained a Phantom Player in World.");
			PhantomAssertions.assertEquals(null, World.getInstance().findObject(identity.characterObjectId()), "Terminal shutdown retained a Phantom World object.");
			PhantomAssertions.assertEquals(null, PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(identity.characterObjectId()), "Terminal shutdown retained a PHANTOM identity lease.");
		}
		PhantomAssertions.assertFalse(ChatObservationService.getInstance().snapshot().observerRegistered(), "Terminal shutdown retained the chat observer.");
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		Throwable failure = null;
		try
		{
			final long deadline = System.nanoTime() + 10_000_000_000L;
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
				throw new AssertionError("Goal031 cleanup retained a configured PhantomSystem owner.");
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
	private void cleanupOwnedPopulation() throws Exception
	{
		if (_profiles == null)
		{
			return;
		}
		final List<ManagedProfile> rows = _profiles.listManagedAfter(PhantomPopulationState.COMPONENT_TYPE, 0, 32);
		for (ManagedProfile row : rows)
		{
			final PhantomPopulationState state = _populationCodec.decode(row.component().payload());
			final int objectId = Objects.requireNonNull(state.actualCharacterObjectId(), "Owned Goal031 character ID is absent.");
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
		PhantomAssertions.assertEquals(0L, scalar("SELECT COUNT(*) FROM phantom_profiles"), "Goal031 cleanup retained a Phantom profile.");
		PhantomAssertions.assertEquals(0L, scalar("SELECT COUNT(*) FROM phantom_profile_components"), "Goal031 cleanup retained a Phantom component.");
	}

	private static void resetOperatorState()
	{
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
				PhantomAssertions.assertTrue(result.next(), "Goal031 scalar query returned no row.");
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

	private record DurableIdentity(long profileId, int characterObjectId, String reservedAccount, String ownershipToken, String characterName)
	{
		private static DurableIdentity capture(PhantomProfile profile, PhantomPopulationState state)
		{
			return new DurableIdentity(profile.profileId(), Objects.requireNonNull(state.actualCharacterObjectId()), state.reservedAccount(), state.ownershipToken(), state.characterName());
		}
	}
}