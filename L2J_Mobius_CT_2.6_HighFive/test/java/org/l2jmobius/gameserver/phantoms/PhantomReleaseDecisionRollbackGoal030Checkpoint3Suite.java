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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

public final class PhantomReleaseDecisionRollbackGoal030Checkpoint3Suite implements PhantomTestSuite
{
	private static final long SEED = 30003004L;
	private static final String ACTIVE_SOURCE = "goal030.cp3.rollback.active";
	private static final long ACTIVE_TTL_MILLIS = 600_000L;
	private static final String MATRIX_RELATIVE_PATH = "test/resources/phantoms/release/goal030-release-coverage.tsv";
	private static final String MATRIX_HEADER = "domain_id\tgoal_lineage\tproduction_owner_paths\tant_targets\tevidence_type\tcp1_status\tgoal030_checkpoint";
	private static final Map<String, String> FINAL_COVERAGE = Map.ofEntries(
		Map.entry("fresh-bootstrap", "COVERED_CP1"),
		Map.entry("population", "COVERED_CP1"),
		Map.entry("progression", "COVERED_CP1"),
		Map.entry("activity-materialization", "COVERED_CP2"),
		Map.entry("topology-navigation-knowledge", "COVERED_PRIOR"),
		Map.entry("combat", "COVERED_PRIOR"),
		Map.entry("farming", "COVERED_PRIOR"),
		Map.entry("acquisition-spoil", "COVERED_PRIOR"),
		Map.entry("craft-trade-commerce-economy", "COVERED_PRIOR"),
		Map.entry("party", "COVERED_PRIOR"),
		Map.entry("rift", "COVERED_PRIOR"),
		Map.entry("pvp", "COVERED_PRIOR"),
		Map.entry("raid", "COVERED_PRIOR"),
		Map.entry("conversation-semantic-social", "COVERED_PRIOR"),
		Map.entry("clans-alliances-reputation-wars", "COVERED_PRIOR"),
		Map.entry("restart-failure-recovery", "COVERED_CP3"),
		Map.entry("operator-observability-replay", "COVERED_CP1"),
		Map.entry("scale-soak-overload", "COVERED_CP1"),
		Map.entry("disabled-regression", "COVERED_CP1"),
		Map.entry("rollback-release-control", "COVERED_CP3"));

	private final PhantomHeadlessPlayerTestEnvironment _environment = new PhantomHeadlessPlayerTestEnvironment();
	private final PhantomPopulationStateCodec _populationCodec = new PhantomPopulationStateCodec();
	private final PhantomPlayersConfig.Settings _settings = new PhantomPlayersConfig.Settings(true, true, 1, 4, 100, 4, 1, 0, 1, 8, 16, 32, ZoneOffset.UTC);
	private PhantomProfileRepository _profiles;
	private ManagedProfile _managed;
	private PhantomPopulationState _population;
	private DurableIdentity _identity;
	private long _profileCount;
	private long _componentCount;
	private Set<String> _componentTypes;
	private boolean _environmentInitialized;

	@Override
	public String id()
	{
		return "release-decision-rollback-goal030cp3";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Goal030 CP3 rollback suite used the wrong seed.");
		resetOperatorState();
		_environment.initialize(context);
		_environmentInitialized = true;
		_profiles = PhantomProfileRepository.open();
		PhantomAssertions.assertEquals(0L, scalar("SELECT COUNT(*) FROM phantom_profiles"), "CP3 rollback suite requires a clean Phantom profile table.");
		PhantomAssertions.assertEquals(0L, scalar("SELECT COUNT(*) FROM phantom_profile_components"), "CP3 rollback suite requires a clean Phantom component table.");
		context.record("goal030cp3.rollback.database", "127.0.0.1:3308/l2jmobiush5_phantom_test");
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-bounded-drain-preserves-durable-world", this::testDrain);
		registry.add("02-disable-preserves-recovery", this::testDisable);
		registry.add("03-shipped-fail-closed-release-barrier", this::testShippedBarrier);
	}

	private void testDrain(PhantomTestContext context) throws Exception
	{
		startRuntime();
		awaitReadyPopulation();
		materialize();
		_identity = DurableIdentity.capture(_managed.profile(), _population);
		_profileCount = scalar("SELECT COUNT(*) FROM phantom_profiles");
		_componentCount = scalar("SELECT COUNT(*) FROM phantom_profile_components WHERE profile_id=?", _identity.profileId());
		_componentTypes = componentTypes(_identity.profileId());
		final PhantomScheduler scheduler = Objects.requireNonNull(PhantomSystem.configuredScheduler());
		final PhantomMaterializationService materialization = Objects.requireNonNull(PhantomSystem.configuredMaterializationService());

		final var drained = PhantomSystem.operatorDrain();
		PhantomAssertions.assertEquals(OperatorControlCode.DRAINED, drained.code(), "Rollback drain did not stop the production-composed runtime.");
		PhantomAssertions.assertEquals(OperatorMode.DRAINED, drained.desiredMode(), "Rollback drain did not retain DRAINED intent.");
		PhantomAssertions.assertFalse(drained.desiredRuntimeEnabled(), "DRAINED mode still desires a runtime.");
		PhantomAssertions.assertFalse(drained.runtimeConfigured(), "Successful drain retained the configured owner.");
		assertRuntimeStopped(scheduler, materialization);
		assertDurableState();
		PhantomAssertions.assertFalse(PhantomSystem.startConfigured(), "DRAINED mode allowed automatic configured startup.");
		PhantomAssertions.assertEquals(OperatorControlCode.ALREADY_DRAINED, PhantomSystem.operatorDrain().code(), "Repeated rollback drain is not idempotent.");
		assertDurableState();

		context.record("goal030cp3.rollback.drain", "mode=DRAINED,runtime=false,idempotent=true");
		context.record("goal030cp3.rollback.drainDurable", _identity.profileId() + "/" + _identity.characterObjectId() + "/" + _identity.reservedAccount());
	}

	private void testDisable(PhantomTestContext context) throws Exception
	{
		PhantomSystem.resetOperatorModeForTesting();
		startRuntime();
		awaitReadyPopulation();
		PhantomAssertions.assertEquals(_identity, DurableIdentity.capture(_managed.profile(), _population), "Drain recovery changed durable identity.");
		materialize();
		final PhantomScheduler scheduler = Objects.requireNonNull(PhantomSystem.configuredScheduler());
		final PhantomMaterializationService materialization = Objects.requireNonNull(PhantomSystem.configuredMaterializationService());

		final var disabled = PhantomSystem.operatorDisable();
		PhantomAssertions.assertEquals(OperatorControlCode.DISABLED, disabled.code(), "Rollback disable did not stop the production-composed runtime.");
		PhantomAssertions.assertEquals(OperatorMode.DISABLED, disabled.desiredMode(), "Rollback disable did not retain DISABLED intent.");
		PhantomAssertions.assertFalse(disabled.desiredRuntimeEnabled(), "DISABLED mode still desires a runtime.");
		PhantomAssertions.assertFalse(disabled.runtimeConfigured(), "Successful disable retained the configured owner.");
		assertRuntimeStopped(scheduler, materialization);
		assertDurableState();
		PhantomAssertions.assertFalse(PhantomSystem.startConfigured(), "DISABLED mode allowed automatic configured startup.");
		PhantomAssertions.assertEquals(OperatorControlCode.ALREADY_DISABLED, PhantomSystem.operatorDisable().code(), "Repeated rollback disable is not idempotent.");
		assertDurableState();

		context.record("goal030cp3.rollback.disable", "mode=DISABLED,runtime=false,idempotent=true");
		context.record("goal030cp3.rollback.recovery", "sameProfile=true,sameCharacter=true,duplicates=0");
	}

	private void testShippedBarrier(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertFalse(PhantomSystem.hasConfiguredInstance(), "Shipped barrier test began with a configured runtime.");
		assertFinalReleaseMatrix(context);
		final PhantomPlayersConfig.Settings shipped = PhantomPlayersConfig.read(context.moduleRoot().resolve("dist/game/config/Custom/PhantomPlayers.ini"));
		PhantomAssertions.assertFalse(shipped.enabled(), "Shipped Phantom configuration is enabled.");
		PhantomAssertions.assertEquals(0, shipped.populationTarget(), "Shipped Phantom population target is not zero.");
		PhantomAssertions.assertEquals(0, shipped.populationActiveTarget(), "Shipped Phantom ACTIVE target is not zero.");
		PhantomPlayersConfig.load();
		PhantomAssertions.assertFalse(PhantomPlayersConfig.isEnabled(), "Static shipped Phantom configuration is enabled.");
		final var enable = PhantomSystem.operatorEnable();
		PhantomAssertions.assertEquals(OperatorControlCode.CONFIG_DISABLED, enable.code(), "operatorEnable bypassed the shipped disabled configuration.");
		PhantomAssertions.assertEquals(OperatorMode.ENABLED, enable.desiredMode(), "Explicit enable did not release the process-local DISABLED intent.");
		PhantomAssertions.assertFalse(enable.desiredRuntimeEnabled(), "Config-disabled enable reported desired runtime activation.");
		PhantomAssertions.assertFalse(enable.runtimeConfigured(), "Config-disabled enable created a runtime.");
		PhantomAssertions.assertFalse(PhantomSystem.startConfigured(), "Canonical startup bypassed the shipped disabled configuration.");
		assertDurableState();
		context.record("goal030cp3.release.shipped", "EnablePhantomSystem=False,population=0,active=0");
		context.record("goal030cp3.release.enableBarrier", "CONFIG_DISABLED,runtime=false");
		PhantomSystem.resetOperatorModeForTesting();
	}

	private static void assertFinalReleaseMatrix(PhantomTestContext context) throws Exception
	{
		final List<String> lines = Files.readAllLines(context.moduleRoot().resolve(MATRIX_RELATIVE_PATH), StandardCharsets.UTF_8);
		PhantomAssertions.assertEquals(FINAL_COVERAGE.size() + 1, lines.size(), "Final Goal030 release matrix does not contain exactly 20 domains.");
		PhantomAssertions.assertEquals(MATRIX_HEADER, lines.getFirst(), "Final Goal030 release matrix header drifted.");
		final Map<String, String[]> rows = new HashMap<>();
		final Map<String, Integer> statusCounts = new HashMap<>();
		for (int lineNumber = 2; lineNumber <= lines.size(); lineNumber++)
		{
			final String[] columns = lines.get(lineNumber - 1).split("\\t", -1);
			PhantomAssertions.assertEquals(7, columns.length, "Final release matrix row does not have seven columns at line " + lineNumber + ".");
			PhantomAssertions.assertEquals(null, rows.putIfAbsent(columns[0], columns), "Final release matrix contains duplicate domain: " + columns[0]);
			statusCounts.merge(columns[5], 1, Integer::sum);
			PhantomAssertions.assertEquals("-", columns[6], "Final covered release domain retained a pending checkpoint: " + columns[0]);
			PhantomAssertions.assertFalse(columns[3].startsWith("pending:"), "Final covered release domain retained a planned target: " + columns[0]);
		}
		PhantomAssertions.assertEquals(FINAL_COVERAGE.keySet(), rows.keySet(), "Final Goal030 release domain set drifted.");
		FINAL_COVERAGE.forEach((domainId, expectedStatus) -> PhantomAssertions.assertEquals(expectedStatus, rows.get(domainId)[5], "Final release status drifted for domain: " + domainId));
		PhantomAssertions.assertEquals(11, statusCounts.getOrDefault("COVERED_PRIOR", 0), "Final release matrix prior count drifted.");
		PhantomAssertions.assertEquals(6, statusCounts.getOrDefault("COVERED_CP1", 0), "Final release matrix CP1 count drifted.");
		PhantomAssertions.assertEquals(1, statusCounts.getOrDefault("COVERED_CP2", 0), "Final release matrix CP2 count drifted.");
		PhantomAssertions.assertEquals(2, statusCounts.getOrDefault("COVERED_CP3", 0), "Final release matrix CP3 count drifted.");
		PhantomAssertions.assertEquals(0, statusCounts.getOrDefault("PENDING_GOAL030", 0), "Final release matrix retained pending Goal030 domains.");
		PhantomAssertions.assertEquals("phantom-cross-domain-autonomous-alpha-goal030cp2-test", rows.get("activity-materialization")[3], "Final activity-materialization evidence target drifted.");
		PhantomAssertions.assertEquals("phantom-restart-failure-recovery-goal030cp3-test", rows.get("restart-failure-recovery")[3], "Final restart/failure evidence target drifted.");
		PhantomAssertions.assertEquals("phantom-release-decision-rollback-goal030cp3-test", rows.get("rollback-release-control")[3], "Final rollback/release evidence target drifted.");
		context.record("goal030cp3.release.matrix", "rows=20,coveredPrior=11,coveredCp1=6,coveredCp2=1,coveredCp3=2,pending=0");
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
				throw new AssertionError("CP3 rollback suite retained a configured PhantomSystem owner.");
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
		PhantomAssertions.assertFalse(PhantomSystem.hasConfiguredInstance(), "A configured owner already exists before rollback setup.");
		PhantomAssertions.assertTrue(PhantomSystem.startConfiguredForTesting(_settings), "Full production-composed PhantomSystem did not start for rollback.");
	}

	private void awaitReadyPopulation() throws Exception
	{
		await(30_000, this::refreshReadyPopulation, "Population did not recover exactly one READY rollback profile.");
		final PhantomScheduler scheduler = Objects.requireNonNull(PhantomSystem.configuredScheduler());
		await(30_000, () -> (scheduler.snapshot().registered() == 1) && (_managed != null) && scheduler.find(_managed.profile().profileId()).isPresent(), "Rollback setup did not restore exactly one Scheduler owner.");
	}

	private boolean refreshReadyPopulation()
	{
		final List<ManagedProfile> rows = _profiles.listManagedAfter(PhantomPopulationState.COMPONENT_TYPE, 0, 2);
		if (rows.size() > 1)
		{
			throw new AssertionError("Rollback setup created a duplicate Population profile.");
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

	private void materialize() throws Exception
	{
		final PhantomScheduler scheduler = Objects.requireNonNull(PhantomSystem.configuredScheduler());
		final PhantomMaterializationService materialization = Objects.requireNonNull(PhantomSystem.configuredMaterializationService());
		final SignalStatus status = scheduler.submitSignal(_managed.profile().profileId(), new PhantomRelevanceSignal(ACTIVE_SOURCE, 1, PhantomActivityState.ACTIVE, ACTIVE_TTL_MILLIS)).status();
		PhantomAssertions.assertTrue((status == SignalStatus.ACCEPTED) || (status == SignalStatus.COALESCED), "Rollback ACTIVE signal was not accepted.");
		await(15_000, () -> materialization.find(_managed.profile().profileId()).filter(value -> value.worldPresent() && value.outboundAttached() && value.identityLeaseRetained()).isPresent(), "Rollback setup did not materialize the production Phantom.");
		PhantomAssertions.assertTrue(World.getInstance().getPlayer(_population.actualCharacterObjectId()) != null, "Rollback setup Phantom is absent from World.");
		PhantomAssertions.assertEquals(OwnerKind.PHANTOM, PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(_population.actualCharacterObjectId()), "Rollback setup Phantom has no PHANTOM lease.");
	}

	private void assertRuntimeStopped(PhantomScheduler scheduler, PhantomMaterializationService materialization)
	{
		final int objectId = _identity.characterObjectId();
		PhantomAssertions.assertFalse(PhantomSystem.hasConfiguredInstance(), "Rollback retained the configured singleton.");
		PhantomAssertions.assertEquals(PhantomScheduler.SchedulerState.STOPPED, scheduler.snapshot().state(), "Rollback retained Scheduler ownership.");
		PhantomAssertions.assertEquals(0, scheduler.snapshot().registered(), "Rollback retained Scheduler registrations.");
		PhantomAssertions.assertEquals(PhantomMaterializationService.ServiceState.STOPPED, materialization.snapshot().state(), "Rollback retained MaterializationService ownership.");
		PhantomAssertions.assertEquals(0, materialization.snapshot().retainedEntries(), "Rollback retained materialization entries.");
		PhantomAssertions.assertEquals(null, World.getInstance().getPlayer(objectId), "Rollback retained the Phantom Player in World.");
		PhantomAssertions.assertEquals(null, World.getInstance().findObject(objectId), "Rollback retained the Phantom World object.");
		PhantomAssertions.assertEquals(null, PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(objectId), "Rollback retained the PHANTOM identity lease.");
		PhantomAssertions.assertFalse(PlayerAutoSaveTaskManager.getInstance().containsObjectId(objectId), "Rollback retained autosave ownership.");
		PhantomAssertions.assertFalse(ChatObservationService.getInstance().snapshot().observerRegistered(), "Rollback retained the chat observer.");
	}

	private void assertDurableState() throws Exception
	{
		PhantomAssertions.assertEquals(_profileCount, scalar("SELECT COUNT(*) FROM phantom_profiles"), "Rollback changed the durable profile count.");
		PhantomAssertions.assertEquals(_componentCount, scalar("SELECT COUNT(*) FROM phantom_profile_components WHERE profile_id=?", _identity.profileId()), "Rollback changed the persistent component count.");
		PhantomAssertions.assertEquals(_componentTypes, componentTypes(_identity.profileId()), "Rollback changed persistent component identities.");
		PhantomAssertions.assertEquals(1L, scalar("SELECT COUNT(*) FROM characters WHERE charId=?", _identity.characterObjectId()), "Rollback deleted or duplicated the canonical character.");
		PhantomAssertions.assertEquals(1L, scalar("SELECT COUNT(*) FROM characters WHERE account_name=?", _identity.reservedAccount()), "Rollback deleted or duplicated the reserved-account character.");
		PhantomAssertions.assertEquals(1L, scalar("SELECT COUNT(*) FROM accounts WHERE login=?", _identity.reservedAccount()), "Rollback deleted or duplicated the reserved account.");
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
			final int objectId = Objects.requireNonNull(state.actualCharacterObjectId(), "Owned rollback character ID is absent.");
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
		PhantomAssertions.assertEquals(0L, scalar("SELECT COUNT(*) FROM phantom_profiles"), "Rollback cleanup retained a Phantom profile.");
		PhantomAssertions.assertEquals(0L, scalar("SELECT COUNT(*) FROM phantom_profile_components"), "Rollback cleanup retained a Phantom component.");
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
				PhantomAssertions.assertTrue(result.next(), "CP3 rollback scalar query returned no row.");
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
