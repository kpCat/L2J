package org.l2jmobius.tests.phantoms;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.security.MessageDigest;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.config.ConfigLoader;
import org.l2jmobius.gameserver.data.xml.CategoryData;
import org.l2jmobius.gameserver.data.xml.EnchantSkillGroupsData;
import org.l2jmobius.gameserver.data.xml.ExperienceData;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.data.xml.SkillTreeData;
import org.l2jmobius.gameserver.handler.EffectHandler;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCompetitionRegistry;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundService;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.State;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundStateCodec;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction.FaultInjector;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction.FaultPoint;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction.ObjectIdAllocator;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction.Status;
import org.l2jmobius.gameserver.phantoms.background.PhantomOwnedStoreIntent;
import org.l2jmobius.gameserver.phantoms.background.PhantomNativeContext;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort;

/** Real isolated TEST process death. This helper never creates or resets a character fixture. */
public final class PhantomOwnedStoreProcessCrashChecks
{
	private static final int MAGIC = 0x51414331;
	public enum Boundary { PREPARE, NATIVE, FINALIZE, MISMATCH, LEGACY_ZERO_NATIVE, MISMATCH_VITALITY }
	public record Receipt(int crashExit, long crashPid, long reconcilePid, long reloadPid, String canonicalHash, String inventoryHash) { }
	private record Spec(PhantomTestContext context, Path config, Path manifest, long profileId, int objectId, String anchorId, Boundary boundary)
	{
		Path file(String suffix) { return manifest.resolveSibling(manifest.getFileName() + suffix); }
		int haltCode() { return boundary == Boundary.PREPARE ? 71 : boundary == Boundary.FINALIZE ? 73 : 72; }
		FaultPoint haltPoint() { return boundary == Boundary.PREPARE ? FaultPoint.AFTER_OWNED_PREPARE : boundary == Boundary.FINALIZE ? FaultPoint.AFTER_OWNED_FINALIZE_COMMIT : FaultPoint.AFTER_OWNED_NATIVE_STORE; }
	}
	private record Marker(long pid, int exit, boolean nativeCompleted, String canonicalHash, String inventoryHash, int beforePoints, int afterPoints, int canonicalPoints) { }
	private record PointFacts(long pid, int beforeReconcile, int afterReconcile, PhantomNativeContext.Phase phase, boolean supported) { }
	private record LockedScalar(long version, int schema, byte[] payload) { }
	private record RetainedCleanup(Path journal, PhantomM1PopulationFixture.NativeSnapshot snapshot, List<Process> children) { }
	private static volatile RetainedCleanup _retainedCleanup;

	/** Caller must skip fixture close, environment shutdown and subsequent fixtures until the marker is cleared. */
	public static final class RetainedCleanupException extends IllegalStateException
	{
		private final RetainedCleanup _retained;
		private RetainedCleanupException(RetainedCleanup retained, Throwable failure)
		{
			super("Q14_RETAINED_CLEANUP_REQUIRED", failure); _retained = retained;
		}
		public Path journal() { return _retained.journal(); }
		public List<Long> ownedChildPids() { return _retained.children().stream().map(Process::pid).toList(); }
		public boolean unsafeChildStillAlive() { return _retained.children().stream().anyMatch(Process::isAlive); }
	}

	/** This stays true after child death until the original full snapshot has been restored. */
	public static boolean retainedCleanupRequired() { return _retainedCleanup != null; }
	public static boolean unsafeChildStillAlive()
	{
		final RetainedCleanup retained = _retainedCleanup;
		return retained != null && retained.children().stream().anyMatch(Process::isAlive);
	}
	public static Optional<Path> retainedJournal()
	{
		final RetainedCleanup retained = _retainedCleanup;
		return retained == null ? Optional.empty() : Optional.of(retained.journal());
	}

	/** A fresh launcher cannot infer whether a previous journal's unknown child has stopped. */
	public static void requireNoRetainedNativeSnapshot(PhantomTestContext context) throws Exception
	{
		final RetainedCleanup retained = _retainedCleanup;
		if (retained != null) { throw new RetainedCleanupException(retained, null); }
		final Path root = context.moduleRoot().toAbsolutePath().normalize();
		final Path directory = root.resolve(".phantom-local");
		if (Files.notExists(directory, LinkOption.NOFOLLOW_LINKS)) { return; }
		require(Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(directory)
			&& directory.toRealPath().equals(root.toRealPath().resolve(".phantom-local")), "Q14_PRIVATE_JOURNAL_DIRECTORY_UNPROVEN");
		try (var files = Files.list(directory))
		{
			final var pending = files.filter(path ->
			{
				final String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
				return name.startsWith("m1-007-q14-") && name.endsWith(".bin");
			}).findFirst();
			if (pending.isPresent())
			{
				context.record("q14.retainedNativeSnapshot", pending.get().getFileName().toString());
				throw new IllegalStateException("Q14_DURABLE_NATIVE_SNAPSHOT_RETAINED");
			}
		}
	}

	/** Retry only the retained exact Process objects and original snapshot; no PID lookup or new fixture creation. */
	public static synchronized void retryRetainedCleanup(Path journal) throws Exception
	{
		final RetainedCleanup retained = _retainedCleanup;
		require(retained != null && retained.journal().equals(journal.toAbsolutePath().normalize()), "Q14_RETAINED_JOURNAL_IDENTITY_MISMATCH");
		final Throwable termination = terminateChildren(retained.children());
		if (retained.children().stream().anyMatch(Process::isAlive)) { throw retainFailure(retained, null, termination); }
		try { retained.snapshot().close(); }
		catch (Throwable failure) { throw retainFailure(retained, termination, failure); }
		_retainedCleanup = null;
		if (termination != null) { throw new IllegalStateException("Q14_OWNED_CHILD_TERMINATION_FAILED_AFTER_EXIT", termination); }
	}
	private PhantomOwnedStoreProcessCrashChecks() { }

	/** Caller supplies a READY production baseline and goal, then releases its seed runtime before this call. */
	public static synchronized Receipt run(PhantomTestContext context, long profileId, int objectId, String anchorId, Boundary boundary, Path journal) throws Exception
	{
		if (_retainedCleanup != null) { throw new RetainedCleanupException(_retainedCleanup, null); }
		require(journal.getFileName() != null && journal.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".bin"), "Q14_NATIVE_SNAPSHOT_BIN_SUFFIX_REQUIRED");
		require(journal.toAbsolutePath().normalize().getParent().equals(context.moduleRoot().toAbsolutePath().normalize().resolve(".phantom-local")), "Q14_NATIVE_SNAPSHOT_DIRECT_PRIVATE_PATH_REQUIRED");
		final String property = System.getProperty("phantom.test.config");
		require(property != null && !property.isBlank(), "Q14_EXPLICIT_TEST_CONFIG_REQUIRED");
		final Path config = Path.of(property).toAbsolutePath().normalize();
		PhantomTestDatabaseGuard.validate(context.moduleRoot(), config);
		require(World.getInstance().findObject(objectId) == null && World.getInstance().getPlayer(objectId) == null && PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(objectId) == null, "Q14_PARENT_RUNTIME_NOT_RELEASED");
		final var spec = new Spec(context, config, journal.resolveSibling(journal.getFileName() + ".manifest").toAbsolutePath().normalize(), profileId, objectId, anchorId, boundary);
		final var snapshot = PhantomM1PopulationFixture.snapshotOnly(context, config, journal, profileId, objectId);
		final List<Process> children = new ArrayList<>(); Throwable primary = null;
		try
		{
			writeSpec(spec);
			final Process crash = launch(spec, "CRASH", children); await(crash, 60);
			require(crash.exitValue() == spec.haltCode(), "Q14_INVALID_CRASH_EXIT:" + crash.exitValue());
			final Marker marker = readMarker(spec.file(".crash"));
			require(marker.pid() == crash.pid() && marker.exit() == spec.haltCode() && marker.nativeCompleted() == (boundary != Boundary.PREPARE), "Q14_INVALID_CRASH_MARKER");
			final Process reconcile = launch(spec, "RECONCILE", children); await(reconcile, 45);
			require(reconcile.exitValue() == 0 && Files.exists(spec.file(".reconciled")), "Q14_RESTART_RECONCILIATION_FAILED");
			require(crash.pid() != reconcile.pid() && readPid(spec.file(".reconciled")) == reconcile.pid(), "Q14_RESTART_NOT_DISTINCT_PROCESS");
			long reloadPid = 0;
			if (!mismatch(boundary))
			{
				final Process reload = launch(spec, "RELOAD", children); await(reload, 60); reloadPid = reload.pid();
				require(reload.exitValue() == 0 && readPid(spec.file(".reloaded")) == reloadPid && reloadPid != crash.pid() && reloadPid != reconcile.pid(), "Q14_NATIVE_RELOAD_FAILED");
			}
			context.record("q14." + boundary + ".actualHalt", marker.exit()); context.record("q14." + boundary + ".nativeCompleted", marker.nativeCompleted());
			context.record("q14." + boundary + ".distinctProcessIds", crash.pid() + "/" + reconcile.pid() + "/" + reloadPid);
			context.record("q14." + boundary + ".canonicalHash", marker.canonicalHash()); context.record("q14." + boundary + ".inventoryHash", marker.inventoryHash());
			final var points = readPointFacts(spec.file(".context-facts")); require(points.pid() == reconcile.pid(), "Q14_CONTEXT_FACTS_PROCESS_MISMATCH");
			context.record("q14." + boundary + ".preparedBeforeAfterPoints", marker.beforePoints() + "/" + marker.afterPoints());
			context.record("q14." + boundary + ".canonicalAtHaltPoints", marker.canonicalPoints());
			context.record("q14." + boundary + ".beforeAfterReconcilePoints", points.beforeReconcile() + "/" + points.afterReconcile());
			context.record("q14." + boundary + ".contextAfterReconcile", points.phase() + "/supported=" + points.supported());
			if (boundary == Boundary.LEGACY_ZERO_NATIVE) { context.record("q14." + boundary + ".premise", "TEST_PRELOAD_LEGACY_ROW_1_TO_0;STOCK_NATIVE_CLAMP_1;NO_LOAD_PARITY_CLAIM"); }
			return new Receipt(marker.exit(), crash.pid(), reconcile.pid(), reloadPid, marker.canonicalHash(), marker.inventoryHash());
		}
		catch (Throwable failure) { primary = failure; throw failure; }
		finally
		{
			final RetainedCleanup retained = new RetainedCleanup(journal.toAbsolutePath().normalize(), snapshot, List.copyOf(children));
			final Throwable termination = terminateChildren(children);
			if (children.stream().anyMatch(Process::isAlive)) { throw retainFailure(retained, primary, termination); }
			try { snapshot.close(); }
			catch (Throwable failure)
			{
				final RetainedCleanupException result = retainFailure(retained, primary, failure);
				if (termination != null && termination != primary && termination != failure) { result.addSuppressed(termination); }
				throw result;
			}
			if (termination != null)
			{
				if (primary != null) { primary.addSuppressed(termination); }
				else { throw new IllegalStateException("Q14_OWNED_CHILD_TERMINATION_FAILED_AFTER_EXIT", termination); }
			}
		}
	}

	private static RetainedCleanupException retainFailure(RetainedCleanup retained, Throwable primary, Throwable failure)
	{
		_retainedCleanup = retained;
		final RetainedCleanupException result = new RetainedCleanupException(retained, failure);
		if (primary != null && primary != failure) { result.addSuppressed(primary); }
		try { retained.snapshot().retainJournal(); }
		catch (Throwable releaseFailure) { result.addSuppressed(releaseFailure); }
		return result;
	}

	private static Throwable terminateChildren(List<Process> children)
	{
		Throwable failure = null; boolean interrupted = false;
		for (Process child : children)
		{
			try
			{
				if (child.isAlive()) { child.destroyForcibly(); require(child.waitFor(10, TimeUnit.SECONDS) && !child.isAlive(), "Q14_OWNED_CHILD_TERMINATION_UNCONFIRMED"); }
			}
			catch (Throwable current)
			{
				interrupted |= current instanceof InterruptedException;
				if (failure == null) { failure = current; } else { failure.addSuppressed(current); }
			}
		}
		if (interrupted) { Thread.currentThread().interrupt(); }
		return failure;
	}

	public static void main(String[] args)
	{
		try
		{
			require(args.length == 2 && List.of("CRASH", "RECONCILE", "RELOAD").contains(args[0]), "Q14_CHILD_ARGUMENTS");
			final Spec spec = readSpec(Path.of(args[1]));
			PhantomTestDatabaseGuard.validate(spec.context().moduleRoot(), spec.config());
			require(Path.of("").toAbsolutePath().normalize().equals(spec.context().moduleRoot().resolve("dist/game").normalize()), "Q14_CHILD_NATIVE_WORKING_DIRECTORY");
			System.setProperty("phantom.test.config", spec.config().toString());
			if (args[0].equals("RECONCILE")) { reconcile(spec); }
			else { nativePlayer(spec, args[0].equals("CRASH")); }
			System.exit(0);
		}
		catch (Throwable failure)
		{
			System.err.println("Q14_CHILD_FAILURE:" + failure.getClass().getName());
			if (failure instanceof IllegalStateException && failure.getMessage() != null && failure.getMessage().startsWith("Q14_")) { System.err.println(failure.getMessage()); }
			if (failure.getStackTrace().length > 0) { System.err.println(failure.getStackTrace()[0]); }
			System.exit(3);
		}
	}

	private static void nativePlayer(Spec spec, boolean crash) throws Exception
	{
		PhantomHeadlessPlayerTestEnvironment.initializeNativeInfrastructure(spec.context());
		final var repository = PhantomProfileRepository.open(); final var goals = new PhantomGoalStateStore(repository);
		if (crash && spec.boundary() == Boundary.LEGACY_ZERO_NATIVE) { installLegacyZeroPremise(spec); }
		final var goal = goals.load(spec.profileId()).orElseThrow().goal(); final var armed = new AtomicBoolean(); final var completed = new AtomicBoolean();
		final var playerRef = new AtomicReference<Player>(); final var epochRef = new AtomicLong(); final var exactOwnerRef = new AtomicReference<Object>();
		final var transaction = new PhantomBackgroundTransaction(DatabaseFactory::getConnection, ObjectIdAllocator.production(), point ->
		{
			if (!armed.get()) { return; }
			try
			{
				if (point == FaultPoint.AFTER_OWNED_PREPARE)
				{
					final byte[] receipt = repository.findComponent(spec.profileId(), PhantomOwnedStoreIntent.COMPONENT_TYPE).orElseThrow().payload();
					final var intent = PhantomOwnedStoreIntent.decode(receipt);
					require(intent.after().identity().profileId() == spec.profileId() && intent.after().identity().characterObjectId() == spec.objectId() && intent.materializedAtNanos() > 0 && intent.materializedAtNanos() == epochRef.get() && playerRef.get().getNativeWorkOwner() == exactOwnerRef.get(), "Q14_PREPARE_EXACT_IDENTITY");
					require(!intent.before().vitals().equals(intent.after().vitals()) || intent.before().position().heading() != intent.after().position().heading(), "Q14_NATIVE_FACTS_NOT_DISTINCT");
					writeForced(spec.file(".intent"), receipt);
					final var scalar = boundContext(repository, spec);
					require(scalar.phase() == PhantomNativeContext.Phase.PENDING && scalar.matchesReceipt(intent.preparedRowVersion(), intent.materializedAtNanos(), receipt)
						&& scalar.beforePoints() == (spec.boundary() == Boundary.LEGACY_ZERO_NATIVE ? 0 : 1) && scalar.afterPoints() == 1
						&& scalar.matchesBefore(canonicalPoints(spec.objectId())) && playerRef.get().getVitalityPoints() == 1, "Q14_PREPARE_NATIVE_CONTEXT_NOT_EXACT");
					writeForced(spec.file(".prepared-context"), scalar.encode());
				}
				if (point == FaultPoint.AFTER_OWNED_NATIVE_STORE)
				{
					final var scalar = boundContext(repository, spec);
					require(scalar.phase() == PhantomNativeContext.Phase.PENDING && scalar.matchesAfter(canonicalPoints(spec.objectId())) && scalar.afterPoints() == 1, "Q14_NATIVE_STORE_SCALAR_NOT_EXACT");
					completed.set(true);
				}
				if (point == spec.haltPoint())
				{
					require(playerRef.get() != null && playerRef.get().getObjectId() == spec.objectId() && completed.get() == (spec.boundary() != Boundary.PREPARE), "Q14_NATIVE_BOUNDARY_NOT_REACHED");
					writeMarker(spec, completed.get()); Runtime.getRuntime().halt(spec.haltCode());
				}
			}
			catch (Exception failure) { throw new IllegalStateException("Q14_DURABLE_MARKER_FAILURE", failure); }
		});
		final var owner = new AtomicReference<PhantomMaterializationService>();
		try (var production = PhantomBackgroundSuite.ProductionAuthorityFixture.start())
		{
			final var background = new PhantomBackgroundService(repository, goals, PhantomIdentityLeaseRegistry.getInstance(), transaction, production.authority(), new PhantomBackgroundCompetitionRegistry(), noSignals(), owner::get);
			final var metrics = new PhantomMetrics();
			final var materialization = new PhantomMaterializationService(repository, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 64, 16, metrics), 1, _ -> { }, background, 5000, 10000);
			owner.set(materialization); require(background.start() && materialization.start(), "Q14_NATIVE_SERVICES_NOT_RUNNING");
			try
			{
				require(materialization.materialize(spec.profileId()).status() == PhantomMaterializationService.ResultStatus.SUCCESS, "Q14_ACTUAL_MATERIALIZATION_FAILED");
				final Player player; final Object exactOwner; final long epoch;
				try (var action = materialization.tryAcquireAction(spec.profileId()).orElseThrow())
				{
					player = action.player(); playerRef.set(player); exactOwner = player.getNativeWorkOwner(); epoch = materialization.find(spec.profileId()).orElseThrow().materializedAtNanos(); epochRef.set(epoch); exactOwnerRef.set(exactOwner);
					player.getStatus().stopHpMpRegeneration();
					require(player.getVitalityPoints() == 1, "Q14_ORIGINAL_NATIVE_MINIMUM_NOT_PRESENT");
					if (crash && spec.boundary() == Boundary.LEGACY_ZERO_NATIVE)
					{
						final var scalar = transaction.nativeContext(spec.profileId(), spec.objectId());
						require(scalar.status() == Status.NATIVE_CONTEXT_REQUIRED && scalar.canonicalPoints() == 0 && scalar.context().phase() == PhantomNativeContext.Phase.UNKNOWN
							&& !scalar.context().simulationEligible() && scalar.matchesNativeLoad(1), "Q14_LEGACY_ZERO_LOADED_WITHOUT_UNKNOWN_WITNESS");
					}
					if (crash)
					{
						player.setCurrentHp(Math.max(1, Math.min(player.getMaxHp(), player.getCurrentHp() / 2))); player.setCurrentMp(player.getCurrentMp() / 2); player.setCurrentCp(player.getCurrentCp() / 2);
						player.setHeading(player.getHeading() ^ 1);
					}
					else { assertNative(player, expected(spec)); }
				}
				require(player.getObjectId() == spec.objectId() && exactOwner != null && player.getNativeWorkOwner() == exactOwner && materialization.find(spec.profileId()).orElseThrow().materializedAtNanos() == epoch, "Q14_EXACT_LIFETIME_CHANGED");
				if (crash)
				{
					armed.set(true); background.captureVisibleArrival(spec.profileId(), player, goal, spec.anchorId());
					throw new IllegalStateException("Q14_INVALID_EXPECTED_PROCESS_HALT_NOT_REACHED");
				}
				final Marker marker = readMarker(spec.file(".crash"));
				require(canonicalPoints(spec.objectId()) == expectedPoints(spec, marker) && player.getVitalityPoints() == expectedPoints(spec, marker), "Q14_RELOADED_NATIVE_POINTS_CHANGED");
				final var loadedContext = boundContext(repository, spec);
				require(loadedContext.phase() == PhantomNativeContext.Phase.COMPLETED && loadedContext.matchesAfter(player.getVitalityPoints()) && loadedContext.simulationEligible(), "Q14_RELOADED_NATIVE_CONTEXT_CHANGED");
				require(inventoryDigest(spec.objectId()).equals(marker.inventoryHash()), "Q14_RELOADED_FULL_INVENTORY_CHANGED");
				require(materialization.dematerialize(spec.profileId()).status() == PhantomMaterializationService.ResultStatus.SUCCESS, "Q14_NATIVE_RELOAD_CLEANUP_FAILED");
				require(transaction.load(spec.profileId()).state().state() == State.READY && repository.findComponent(spec.profileId(), PhantomOwnedStoreIntent.COMPONENT_TYPE).isEmpty() && World.getInstance().getPlayer(spec.objectId()) == null && PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(spec.objectId()) == null, "Q14_NATIVE_RELOAD_OWNERSHIP_RETAINED");
				assertCanonical(spec.objectId(), expected(spec)); writePid(spec.file(".reloaded"));
				require(canonicalPoints(spec.objectId()) == expectedPoints(spec, marker) && boundContext(repository, spec).simulationEligible(), "Q14_RELOAD_CLEANUP_POINTS_CHANGED");
			}
			finally { armed.set(false); materialization.shutdown(); background.beginStop(); background.finishStop(); }
		}
		ThreadPool.shutdown(); DatabaseFactory.close();
	}

	// Recovery does not initialize IdManager, World, materialization, or any native Player until reconciliation has finished.
	private static void reconcile(Spec spec) throws Exception
	{
		ConfigLoader.init(); PhantomTestDatabaseBootstrap.initialize(spec.context().moduleRoot(), spec.config()); ThreadPool.init();
		CategoryData.getInstance(); ExperienceData.getInstance(); EffectHandler.getInstance().executeScript();
		EnchantSkillGroupsData.getInstance(); SkillTreeData.getInstance(); SkillData.getInstance(); ItemData.getInstance();
		final var repository = PhantomProfileRepository.open();
		final var transaction = new PhantomBackgroundTransaction(DatabaseFactory::getConnection, ObjectIdAllocator.production(), FaultInjector.none());
		final var intent = PhantomOwnedStoreIntent.decode(Files.readAllBytes(spec.file(".intent"))); final Marker marker = readMarker(spec.file(".crash"));
		require(intent.after().identity().profileId() == spec.profileId() && intent.after().identity().characterObjectId() == spec.objectId(), "Q14_RESTART_INTENT_IDENTITY_MISMATCH");
		require(canonicalDigest(spec.objectId()).equals(marker.canonicalHash()) && inventoryDigest(spec.objectId()).equals(marker.inventoryHash()), "Q14_CRASH_CANONICAL_CHANGED_BEFORE_RECONCILE");
		require(canonicalPoints(spec.objectId()) == marker.canonicalPoints(), "Q14_CRASH_NATIVE_POINTS_CHANGED_BEFORE_RECONCILE");
		final var pending = repository.findComponent(spec.profileId(), PhantomOwnedStoreIntent.COMPONENT_TYPE);
		require(pending.isPresent() == (spec.boundary() != Boundary.FINALIZE), "Q14_CRASH_RECEIPT_PRESENCE_MISMATCH");
		final var beforeRecovery = transaction.load(spec.profileId()).state();
		require(beforeRecovery != null && beforeRecovery.state() == (spec.boundary() == Boundary.FINALIZE ? State.MATERIALIZED : State.VERIFY_PENDING), "Q14_CRASH_LIFECYCLE_STATE_MISMATCH");
		if (pending.isPresent()) { require(Arrays.equals(pending.orElseThrow().payload(), Files.readAllBytes(spec.file(".intent"))), "Q14_CRASH_RECEIPT_CHANGED"); }
		final var contextBeforeRecovery = boundContext(repository, spec);
		require(contextBeforeRecovery.beforePoints() == (spec.boundary() == Boundary.FINALIZE ? marker.afterPoints() : marker.beforePoints())
			&& contextBeforeRecovery.afterPoints() == marker.afterPoints() && contextBeforeRecovery.phase() == (spec.boundary() == Boundary.FINALIZE ? PhantomNativeContext.Phase.COMPLETED : PhantomNativeContext.Phase.PENDING), "Q14_CRASH_CONTEXT_CHANGED");
		if (spec.boundary() == Boundary.MISMATCH)
		{
			try (Connection connection = DatabaseFactory.getConnection(); var statement = connection.prepareStatement("UPDATE characters SET heading=heading+2 WHERE charId=? AND heading=?"))
			{
				statement.setInt(1, spec.objectId()); statement.setInt(2, intent.after().position().heading()); require(statement.executeUpdate() == 1, "Q14_MISMATCH_ORIGINAL_NATIVE_AFTER_NOT_PRESENT");
			}
		}
		else if (spec.boundary() == Boundary.MISMATCH_VITALITY)
		{
			assertCanonical(spec.objectId(), expected(spec)); corruptVitality(spec, intent);
			require(canonicalPoints(spec.objectId()) == 2 && inventoryDigest(spec.objectId()).equals(marker.inventoryHash()), "Q14_VITALITY_ONLY_CORRUPTION_NOT_EXACT");
			assertCanonical(spec.objectId(), expected(spec));
		}
		else { assertCanonical(spec.objectId(), expected(spec)); }
		final int beforeReconcilePoints = canonicalPoints(spec.objectId());
		final String canonical = canonicalDigest(spec.objectId()); final String inventory = inventoryDigest(spec.objectId());
		final var recovered = transaction.abortMaterialization(spec.profileId(), spec.objectId());
		if (mismatch(spec.boundary()))
		{
			require(recovered.status() == Status.OWNED_STORE_CANONICAL_NEITHER && recovered.state().state() == State.INCONSISTENT, "Q14_MISMATCH_NOT_FAIL_CLOSED");
			require(repository.findComponent(spec.profileId(), PhantomOwnedStoreIntent.COMPONENT_TYPE).orElseThrow().equals(pending.orElseThrow()), "Q14_MISMATCH_RECEIPT_LOST");
			final var scalar = boundContext(repository, spec); final var stateComponent = repository.findComponent(spec.profileId(), PhantomBackgroundState.COMPONENT_TYPE).orElseThrow();
			require(scalar.equals(contextBeforeRecovery.rebind(stateComponent.rowVersion(), stateComponent.payload())), "Q14_MISMATCH_NATIVE_CONTEXT_LOST");
		}
		else
		{
			require(recovered.status() == Status.SUCCESS && recovered.state().state() == State.READY, "Q14_RESTART_NOT_READY:" + recovered.status());
			assertProjection(recovered.state(), expected(spec)); require(repository.findComponent(spec.profileId(), PhantomOwnedStoreIntent.COMPONENT_TYPE).isEmpty(), "Q14_RESTART_RECEIPT_NOT_RECONCILED");
			final var scalar = boundContext(repository, spec);
			require(scalar.phase() == PhantomNativeContext.Phase.COMPLETED && scalar.matchesAfter(expectedPoints(spec, marker)) && scalar.simulationEligible(), "Q14_RESTART_NATIVE_CONTEXT_NOT_SUPPORTED");
		}
		require(canonical.equals(canonicalDigest(spec.objectId())) && inventory.equals(inventoryDigest(spec.objectId())), "Q14_RECONCILIATION_REWROTE_CANONICAL");
		final var stable = repository.findComponent(spec.profileId(), PhantomBackgroundState.COMPONENT_TYPE).orElseThrow();
		final var stableContext = repository.findComponent(spec.profileId(), PhantomNativeContext.COMPONENT_TYPE).orElseThrow();
		final var second = transaction.abortMaterialization(spec.profileId(), spec.objectId());
		require((mismatch(spec.boundary()) ? !second.successful() : second.successful()) && stable.equals(repository.findComponent(spec.profileId(), PhantomBackgroundState.COMPONENT_TYPE).orElseThrow())
			&& stableContext.equals(repository.findComponent(spec.profileId(), PhantomNativeContext.COMPONENT_TYPE).orElseThrow()), "Q14_RESTART_REPEAT_NOT_IDEMPOTENT");
		if (mismatch(spec.boundary())) { require(repository.findComponent(spec.profileId(), PhantomOwnedStoreIntent.COMPONENT_TYPE).orElseThrow().equals(pending.orElseThrow()), "Q14_MISMATCH_REPEAT_RECEIPT_CHANGED"); }
		require(canonical.equals(canonicalDigest(spec.objectId())) && inventory.equals(inventoryDigest(spec.objectId())), "Q14_REPEAT_CANONICAL_CHANGED");
		final var scalar = boundContext(repository, spec);
		writePointFacts(spec, beforeReconcilePoints, canonicalPoints(spec.objectId()), scalar);
		writePid(spec.file(".reconciled")); ThreadPool.shutdown(); DatabaseFactory.close();
	}

	/** This is a raw legacy-row TEST premise before Player.load, not a live point reset or a load-parity claim. */
	private static void installLegacyZeroPremise(Spec spec) throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection())
		{
			connection.setAutoCommit(false);
			try
			{
				lockSyntheticProfile(connection, spec);
				final var stateRow = lockedScalar(connection, spec, PhantomBackgroundState.COMPONENT_TYPE);
				require(stateRow != null && stateRow.schema() == PhantomBackgroundState.SCHEMA_VERSION, "Q14_LEGACY_ZERO_STATE_ABSENT");
				final var state = new PhantomBackgroundStateCodec().decode(stateRow.payload());
				require(state.state() == State.READY && state.identity().profileId() == spec.profileId() && state.identity().characterObjectId() == spec.objectId()
					&& state.identity().classIndex() == 0, "Q14_LEGACY_ZERO_NOT_EXACT_MAIN_READY");
				require(lockedScalar(connection, spec, PhantomOwnedStoreIntent.COMPONENT_TYPE) == null, "Q14_LEGACY_ZERO_RECEIPT_PRESENT");
				final var old = lockedScalar(connection, spec, PhantomNativeContext.COMPONENT_TYPE);
				if (old != null)
				{
					final var context = PhantomNativeContext.decode(old.payload());
					require(old.schema() == PhantomNativeContext.SCHEMA_VERSION && context.binds(state.identity(), stateRow.version(), stateRow.payload())
						&& context.phase() != PhantomNativeContext.Phase.PENDING && context.matchesAfter(1), "Q14_LEGACY_ZERO_OLD_CONTEXT_UNPROVEN");
				}
				lockSyntheticCharacter(connection, spec, state);
				final var unknown = PhantomNativeContext.completed(state.identity(), 0, PhantomNativeContext.Eligibility.UNKNOWN, stateRow.version(), stateRow.payload());
				try (var statement = connection.prepareStatement("UPDATE characters SET vitality_points=0 WHERE charId=? AND vitality_points=1 AND account_name=?"))
				{
					statement.setInt(1, spec.objectId()); statement.setString(2, "phantom_t004_" + spec.context().seed()); require(statement.executeUpdate() == 1, "Q14_LEGACY_ZERO_BEFOREVALUE_CHANGED");
				}
				if (old == null)
				{
					try (var statement = connection.prepareStatement("INSERT INTO phantom_profile_components (profile_id,component_type,component_schema_version,payload) VALUES (?,?,?,?)"))
					{
						statement.setLong(1, spec.profileId()); statement.setString(2, PhantomNativeContext.COMPONENT_TYPE); statement.setInt(3, PhantomNativeContext.SCHEMA_VERSION); statement.setBytes(4, unknown.encode());
						require(statement.executeUpdate() == 1, "Q14_LEGACY_ZERO_CONTEXT_INSERT_FAILED");
					}
				}
				else
				{
					try (var statement = connection.prepareStatement("UPDATE phantom_profile_components SET component_schema_version=?,payload=?,row_version=row_version+1 WHERE profile_id=? AND component_type=? AND row_version=?"))
					{
						statement.setInt(1, PhantomNativeContext.SCHEMA_VERSION); statement.setBytes(2, unknown.encode()); statement.setLong(3, spec.profileId()); statement.setString(4, PhantomNativeContext.COMPONENT_TYPE); statement.setLong(5, old.version());
						require(statement.executeUpdate() == 1, "Q14_LEGACY_ZERO_CONTEXT_CAS_FAILED");
					}
				}
				connection.commit();
			}
			catch (Throwable failure) { connection.rollback(); throw failure; }
		}
	}

	/** Reconcile child has no Player; corrupt only the exact original native AFTER value. */
	private static void corruptVitality(Spec spec, PhantomOwnedStoreIntent intent) throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection())
		{
			connection.setAutoCommit(false);
			try
			{
				lockSyntheticProfile(connection, spec);
				final var stateRow = lockedScalar(connection, spec, PhantomBackgroundState.COMPONENT_TYPE);
				final var receipt = lockedScalar(connection, spec, PhantomOwnedStoreIntent.COMPONENT_TYPE);
				final var scalar = lockedScalar(connection, spec, PhantomNativeContext.COMPONENT_TYPE);
				require(stateRow != null && stateRow.schema() == PhantomBackgroundState.SCHEMA_VERSION && receipt != null && receipt.schema() == PhantomOwnedStoreIntent.SCHEMA_VERSION
					&& Arrays.equals(receipt.payload(), intent.encode()) && scalar != null && scalar.schema() == PhantomNativeContext.SCHEMA_VERSION, "Q14_VITALITY_MISMATCH_ORIGINAL_PROOF_ABSENT");
				final var state = new PhantomBackgroundStateCodec().decode(stateRow.payload()); final var context = PhantomNativeContext.decode(scalar.payload());
				require(state.state() == State.VERIFY_PENDING && state.identity().equals(intent.after().identity()) && context.binds(state.identity(), stateRow.version(), stateRow.payload())
					&& context.matchesReceipt(intent.preparedRowVersion(), intent.materializedAtNanos(), receipt.payload()) && context.beforePoints() == 1 && context.afterPoints() == 1, "Q14_VITALITY_MISMATCH_NOT_EXACT_PENDING");
				lockSyntheticCharacter(connection, spec, state);
				try (var statement = connection.prepareStatement("UPDATE characters SET vitality_points=2 WHERE charId=? AND vitality_points=1 AND account_name=?"))
				{
					statement.setInt(1, spec.objectId()); statement.setString(2, "phantom_t004_" + spec.context().seed()); require(statement.executeUpdate() == 1, "Q14_VITALITY_MISMATCH_ORIGINAL_AFTER_CHANGED");
				}
				connection.commit();
			}
			catch (Throwable failure) { connection.rollback(); throw failure; }
		}
	}

	private static void lockSyntheticProfile(Connection connection, Spec spec) throws Exception
	{
		try (var statement = connection.prepareStatement("SELECT character_object_id FROM phantom_profiles WHERE profile_id=? FOR UPDATE"))
		{
			statement.setLong(1, spec.profileId());
			try (var row = statement.executeQuery()) { require(row.next() && row.getInt(1) == spec.objectId() && !row.next(), "Q14_TEST_PROFILE_LINK_UNPROVEN"); }
		}
	}

	private static void lockSyntheticCharacter(Connection connection, Spec spec, PhantomBackgroundState state) throws Exception
	{
		try (var statement = connection.prepareStatement("SELECT account_name,vitality_points,classid,race FROM characters WHERE charId=? FOR UPDATE"))
		{
			statement.setInt(1, spec.objectId());
			try (var row = statement.executeQuery())
			{
				require(row.next() && ("phantom_t004_" + spec.context().seed()).equals(row.getString(1)) && row.getInt(2) == 1
					&& row.getInt(3) == state.identity().activeClassId() && row.getInt(4) == state.identity().raceOrdinal() && !row.next(), "Q14_TEST_CHARACTER_OR_BEFOREVALUE_UNPROVEN");
			}
		}
	}

	private static LockedScalar lockedScalar(Connection connection, Spec spec, String type) throws Exception
	{
		try (var statement = connection.prepareStatement("SELECT row_version,component_schema_version,payload FROM phantom_profile_components WHERE profile_id=? AND component_type=? FOR UPDATE"))
		{
			statement.setLong(1, spec.profileId()); statement.setString(2, type);
			try (var row = statement.executeQuery())
			{
				if (!row.next()) { return null; }
				final var result = new LockedScalar(row.getLong(1), row.getInt(2), row.getBytes(3)); require(!row.next(), "Q14_DUPLICATE_SCALAR"); return result;
			}
		}
	}

	private static PhantomNativeContext boundContext(PhantomProfileRepository repository, Spec spec)
	{
		final var row = repository.findComponent(spec.profileId(), PhantomBackgroundState.COMPONENT_TYPE).orElseThrow();
		final var state = new PhantomBackgroundStateCodec().decode(row.payload()); final var scalar = repository.findComponent(spec.profileId(), PhantomNativeContext.COMPONENT_TYPE).orElseThrow();
		final var context = PhantomNativeContext.decode(scalar.payload());
		require(row.componentSchemaVersion() == PhantomBackgroundState.SCHEMA_VERSION && scalar.componentSchemaVersion() == PhantomNativeContext.SCHEMA_VERSION
			&& state.identity().profileId() == spec.profileId() && state.identity().characterObjectId() == spec.objectId() && context.binds(state.identity(), row.rowVersion(), row.payload()), "Q14_NATIVE_CONTEXT_BINDING_CHANGED");
		return context;
	}

	private static int canonicalPoints(int objectId) throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection(); var statement = connection.prepareStatement("SELECT vitality_points FROM characters WHERE charId=?"))
		{
			statement.setInt(1, objectId);
			try (var row = statement.executeQuery())
			{
				require(row.next(), "Q14_CANONICAL_POINTS_ABSENT"); final int result = row.getInt(1);
				require(!row.wasNull() && result >= 0 && result <= 20000 && !row.next(), "Q14_CANONICAL_POINTS_UNPROVEN"); return result;
			}
		}
	}

	private static boolean mismatch(Boundary boundary) { return boundary == Boundary.MISMATCH || boundary == Boundary.MISMATCH_VITALITY; }
	private static int expectedPoints(Spec spec, Marker marker) { return spec.boundary() == Boundary.PREPARE ? marker.beforePoints() : marker.afterPoints(); }

	private static PhantomBackgroundState expected(Spec spec) throws Exception
	{
		final var intent = PhantomOwnedStoreIntent.decode(Files.readAllBytes(spec.file(".intent")));
		return spec.boundary() == Boundary.PREPARE ? intent.before() : intent.after();
	}

	private static void assertProjection(PhantomBackgroundState actual, PhantomBackgroundState expected)
	{
		require(actual.equals(expected.withState(expected.vitals().currentHp() == 0 ? State.DEAD : State.READY)), "Q14_RESTART_SELECTED_FACTS_CHANGED");
	}

	private static void assertNative(Player player, PhantomBackgroundState expected)
	{
		require(player.getObjectId() == expected.identity().characterObjectId() && player.getActiveClass() == expected.identity().activeClassId() && player.getClassIndex() == expected.identity().classIndex() && player.getRace().ordinal() == expected.identity().raceOrdinal() && player.getLevel() == expected.progress().level() && player.getExp() == expected.progress().experience() && player.getSp() == expected.progress().skillPoints() && player.getExpBeforeDeath() == expected.progress().experienceBeforeDeath(), "Q14_RELOAD_NATIVE_PROGRESS_CHANGED");
		require(player.getInstanceId() == expected.position().instanceId() && player.getX() == expected.position().x() && player.getY() == expected.position().y() && player.getZ() == expected.position().z() && player.getHeading() == expected.position().heading(), "Q14_RELOAD_NATIVE_POSITION_CHANGED");
		require(close(player.getCurrentHp(), expected.vitals().currentHp()) && close(player.getMaxHp(), expected.vitals().maximumHp()) && close(player.getCurrentMp(), expected.vitals().currentMp()) && close(player.getMaxMp(), expected.vitals().maximumMp()) && close(player.getCurrentCp(), expected.vitals().currentCp()) && close(player.getMaxCp(), expected.vitals().maximumCp()), "Q14_RELOAD_NATIVE_VITALS_CHANGED");
		for (var skill : expected.autoGetSkills()) { require(player.getSkillLevel(skill.skillId()) == skill.skillLevel(), "Q14_RELOAD_NATIVE_SKILL_CHANGED"); }
	}

	private static void assertCanonical(int objectId, PhantomBackgroundState expected) throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection(); var statement = connection.prepareStatement("SELECT level,exp,expBeforeDeath,sp,curHp,maxHp,curMp,maxMp,curCp,maxCp,x,y,z,heading,classid,race FROM characters WHERE charId=?"))
		{
			statement.setInt(1, objectId);
			try (var row = statement.executeQuery())
			{
				require(row.next() && row.getInt("level") == expected.progress().level() && row.getLong("exp") == expected.progress().experience() && row.getLong("sp") == expected.progress().skillPoints() && row.getLong("expBeforeDeath") == expected.progress().experienceBeforeDeath(), "Q14_CANONICAL_PROGRESS_CHANGED");
				require(row.getInt("x") == expected.position().x() && row.getInt("y") == expected.position().y() && row.getInt("z") == expected.position().z() && row.getInt("heading") == expected.position().heading() && row.getInt("classid") == expected.identity().activeClassId() && row.getInt("race") == expected.identity().raceOrdinal(), "Q14_CANONICAL_IDENTITY_POSITION_CHANGED");
				require(close(row.getDouble("curHp"), expected.vitals().currentHp()) && close(row.getDouble("maxHp"), expected.vitals().maximumHp()) && close(row.getDouble("curMp"), expected.vitals().currentMp()) && close(row.getDouble("maxMp"), expected.vitals().maximumMp()) && close(row.getDouble("curCp"), expected.vitals().currentCp()) && close(row.getDouble("maxCp"), expected.vitals().maximumCp()) && !row.next(), "Q14_CANONICAL_VITALS_CHANGED");
			}
		}
	}

	private static String canonicalDigest(int objectId) throws Exception { return digestQueries(objectId, List.of("SELECT * FROM characters WHERE charId=?", "SELECT * FROM character_skills WHERE charId=?")); }
	private static String inventoryDigest(int objectId) throws Exception
	{
		return digestQueries(objectId, List.of("SELECT * FROM items WHERE owner_id=?", "SELECT * FROM item_attributes WHERE itemId IN (SELECT object_id FROM items WHERE owner_id=?)", "SELECT * FROM item_elementals WHERE itemId IN (SELECT object_id FROM items WHERE owner_id=?)", "SELECT * FROM item_variables WHERE id IN (SELECT object_id FROM items WHERE owner_id=?)"));
	}

	private static String digestQueries(int objectId, List<String> queries) throws Exception
	{
		final var digest = MessageDigest.getInstance("SHA-256");
		try (Connection connection = DatabaseFactory.getConnection())
		{
			connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ); connection.setAutoCommit(false);
			for (String sql : queries)
			{
				try (var statement = connection.prepareStatement(sql))
				{
					statement.setInt(1, objectId);
					statement.setQueryTimeout(15);
					try (var rows = statement.executeQuery())
					{
						final var metadata = rows.getMetaData(); final var values = new ArrayList<byte[]>();
						for (int i = 1; i <= metadata.getColumnCount(); i++) { digest.update(metadata.getColumnName(i).getBytes(java.nio.charset.StandardCharsets.UTF_8)); digest.update((byte) 0); }
						while (rows.next())
						{
							require(values.size() < 10000, "Q14_ORACLE_ROW_BOUND");
							final var bytes = new ByteArrayOutputStream();
							try (var out = new DataOutputStream(bytes)) { for (int i = 1; i <= metadata.getColumnCount(); i++) { final byte[] value = rows.getBytes(i); require(value == null || value.length <= 4194304, "Q14_ORACLE_CELL_BOUND"); out.writeInt(value == null ? -1 : value.length); if (value != null) { out.write(value); } } }
							values.add(bytes.toByteArray());
						}
						values.sort(Arrays::compareUnsigned); digest.update(ByteBuffer.allocate(4).putInt(values.size()).array());
						for (byte[] value : values) { digest.update(ByteBuffer.allocate(4).putInt(value.length).array()); digest.update(value); }
					}
				}
			}
			connection.rollback();
		}
		return HexFormat.of().formatHex(digest.digest());
	}

	private static Process launch(Spec spec, String mode, List<Process> children) throws Exception
	{
		final Path javaExecutable = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
		final Path log = spec.file("." + mode.toLowerCase(java.util.Locale.ROOT) + ".log"); writeForced(log, new byte[0]);
		final Process process = new ProcessBuilder(javaExecutable.toString(), "-Xmx4096m", "-Dphantom.module.root=" + spec.context().moduleRoot(), "-Dphantom.test.config=" + spec.config(), "-cp", System.getProperty("java.class.path"), PhantomOwnedStoreProcessCrashChecks.class.getName(), mode, spec.manifest().toString()).directory(spec.context().moduleRoot().resolve("dist/game").toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
		children.add(process); return process;
	}

	private static void await(Process process, int seconds) throws Exception { require(process.waitFor(seconds, TimeUnit.SECONDS), "Q14_INVALID_CHILD_TIMEOUT"); }
	private static boolean close(double left, double right) { return Math.abs(left - right) <= 0.000001d; }
	private static void require(boolean condition, String reason) { if (!condition) { throw new IllegalStateException(reason); } }

	private static void writeSpec(Spec spec) throws Exception
	{
		final var bytes = new ByteArrayOutputStream();
		try (var out = new DataOutputStream(bytes)) { out.writeInt(MAGIC); out.writeLong(spec.context().seed()); out.writeUTF(spec.context().moduleRoot().toString()); out.writeUTF(spec.config().toString()); out.writeLong(spec.profileId()); out.writeInt(spec.objectId()); out.writeUTF(spec.anchorId()); out.writeUTF(spec.boundary().name()); }
		writeForced(spec.manifest(), bytes.toByteArray());
	}

	private static Spec readSpec(Path path) throws Exception
	{
		final Path manifest = path.toAbsolutePath().normalize(); require(Files.size(manifest) < 4096 && !Files.isSymbolicLink(manifest), "Q14_MANIFEST_BOUND");
		try (var in = new DataInputStream(new ByteArrayInputStream(Files.readAllBytes(manifest))))
		{
			require(in.readInt() == MAGIC, "Q14_MANIFEST_MAGIC"); final long seed = in.readLong(); final Path root = Path.of(in.readUTF()).toAbsolutePath().normalize();
			require(manifest.getParent().toRealPath().startsWith(root.resolve(".phantom-local").toRealPath()) && manifest.getFileName().toString().startsWith("m1-007-q14-"), "Q14_PRIVATE_MANIFEST_PATH");
			final var result = new Spec(new PhantomTestContext(seed, root, manifest.getParent()), Path.of(in.readUTF()), manifest, in.readLong(), in.readInt(), in.readUTF(), Boundary.valueOf(in.readUTF()));
			require(in.available() == 0 && result.profileId() > 0 && result.objectId() > 0 && !result.anchorId().isBlank() && result.anchorId().length() <= 128, "Q14_MANIFEST_IDENTITY"); return result;
		}
	}

	private static void writeMarker(Spec spec, boolean completed) throws Exception
	{
		final var scalar = PhantomNativeContext.decode(Files.readAllBytes(spec.file(".prepared-context")));
		final var bytes = new ByteArrayOutputStream();
		try (var out = new DataOutputStream(bytes)) { out.writeInt(MAGIC); out.writeLong(ProcessHandle.current().pid()); out.writeInt(spec.haltCode()); out.writeBoolean(completed); out.writeUTF(canonicalDigest(spec.objectId())); out.writeUTF(inventoryDigest(spec.objectId())); out.writeInt(scalar.beforePoints()); out.writeInt(scalar.afterPoints()); out.writeInt(canonicalPoints(spec.objectId())); }
		writeForced(spec.file(".crash"), bytes.toByteArray());
	}
	private static Marker readMarker(Path path) throws Exception
	{
		require(Files.size(path) < 1024, "Q14_MARKER_BOUND");
		try (var in = new DataInputStream(new ByteArrayInputStream(Files.readAllBytes(path)))) { require(in.readInt() == MAGIC, "Q14_MARKER_MAGIC"); final var marker = new Marker(in.readLong(), in.readInt(), in.readBoolean(), in.readUTF(), in.readUTF(), in.readInt(), in.readInt(), in.readInt()); require(in.available() == 0 && marker.canonicalHash().matches("[0-9a-f]{64}") && marker.inventoryHash().matches("[0-9a-f]{64}") && marker.beforePoints() >= 0 && marker.beforePoints() <= 20000 && marker.afterPoints() >= 1 && marker.afterPoints() <= 20000 && marker.canonicalPoints() >= 0 && marker.canonicalPoints() <= 20000, "Q14_MARKER_FACTS"); return marker; }
	}
	private static void writePointFacts(Spec spec, int before, int after, PhantomNativeContext scalar) throws Exception
	{
		final var bytes = new ByteArrayOutputStream();
		try (var out = new DataOutputStream(bytes)) { out.writeInt(MAGIC); out.writeLong(ProcessHandle.current().pid()); out.writeInt(before); out.writeInt(after); out.writeUTF(scalar.phase().name()); out.writeBoolean(scalar.simulationEligible()); }
		writeForced(spec.file(".context-facts"), bytes.toByteArray());
	}
	private static PointFacts readPointFacts(Path path) throws Exception
	{
		require(Files.size(path) < 128, "Q14_CONTEXT_FACTS_BOUND");
		try (var in = new DataInputStream(new ByteArrayInputStream(Files.readAllBytes(path))))
		{
			require(in.readInt() == MAGIC, "Q14_CONTEXT_FACTS_MAGIC"); final var result = new PointFacts(in.readLong(), in.readInt(), in.readInt(), PhantomNativeContext.Phase.valueOf(in.readUTF()), in.readBoolean());
			require(in.available() == 0 && result.pid() > 0 && result.beforeReconcile() >= 0 && result.beforeReconcile() <= 20000 && result.afterReconcile() >= 0 && result.afterReconcile() <= 20000, "Q14_CONTEXT_FACTS_INVALID"); return result;
		}
	}
	private static void writePid(Path path) throws Exception { final var bytes = new ByteArrayOutputStream(); try (var out = new DataOutputStream(bytes)) { out.writeLong(ProcessHandle.current().pid()); } writeForced(path, bytes.toByteArray()); }
	private static long readPid(Path path) throws Exception { require(Files.size(path) == Long.BYTES, "Q14_PID_MARKER_BOUND"); try (var in = new DataInputStream(new ByteArrayInputStream(Files.readAllBytes(path)))) { return in.readLong(); } }
	private static void writeForced(Path path, byte[] bytes) throws Exception
	{
		try (var channel = FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))
		{
			final var principal = path.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(System.getProperty("user.name"));
			Files.getFileAttributeView(path, AclFileAttributeView.class).setAcl(List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(principal).setPermissions(EnumSet.allOf(AclEntryPermission.class)).build()));
			final var buffer = ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) { channel.write(buffer); } channel.force(true);
		}
	}
	private static PhantomRelevanceSignalPort noSignals()
	{
		return new PhantomRelevanceSignalPort()
		{
			@Override public SignalDelivery submit(long profileId, org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal signal) { return SignalDelivery.ACCEPTED; }
			@Override public SignalDelivery withdraw(long profileId, String sourceKey, long sequence) { return SignalDelivery.ACCEPTED; }
		};
	}
}
