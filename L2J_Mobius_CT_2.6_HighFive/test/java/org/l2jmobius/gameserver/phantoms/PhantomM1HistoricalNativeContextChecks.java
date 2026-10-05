/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundAuthority;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupStateCodec;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupStore;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundStateCodec;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundService;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction.FaultPoint;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundService;
import org.l2jmobius.gameserver.phantoms.background.PhantomNativeContext;
import org.l2jmobius.gameserver.phantoms.background.PhantomOwnedStoreIntent;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecyclePort;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.taskmanagers.PlayerAutoSaveTaskManager;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomM1PopulationFixture;
import org.l2jmobius.tests.phantoms.PhantomTestContext;
import org.l2jmobius.tests.phantoms.PhantomTestDatabaseGuard;

/** TEST-first UNKNOWN handoff control; every production call uses an existing API. */
public final class PhantomM1HistoricalNativeContextChecks
{

	private PhantomM1HistoricalNativeContextChecks() { }

	public static Probe probeImported(PhantomTestContext context, PhantomM1PopulationFixture fixture, PhantomProfileRepository profiles,
		PhantomGoalStateStore goals, PhantomBackgroundAuthority authority) throws Exception
	{
		PhantomAssertions.assertEquals(PhantomTestDatabaseGuard.TARGET_DATABASE, context.measurements().get("headless.database"), "Historical context requires original guarded TEST.");
		PhantomAssertions.assertTrue(Objects.toString(context.measurements().get("w.fixture.beforeHash"), "").matches("[0-9A-Fa-f]{64}"), "INVALID historical context: original full population before-image is absent.");
		for (var identity : fixture.readyIdentities().stream().filter(identity -> identity.profileId() == 68L).toList())
		{
			final var loaded = new PhantomBackgroundTransaction().load(identity.profileId());
			final var claim = new PhantomBackgroundCatchupStore(profiles, goals).load(identity.profileId()).orElse(null);
			final var goal = goals.load(identity.profileId()).orElse(null);
			if (!loaded.successful() || loaded.state() == null || claim == null || goal == null) { continue; }
			if ((loaded.state().state() == PhantomBackgroundState.State.READY || loaded.state().state() == PhantomBackgroundState.State.DEAD) && claim.state().status() == PhantomBackgroundCatchupState.Status.COMPLETE
				&& goal.goal().status() == PhantomGoalStatus.ACTIVE && goal.goal().goalId() == claim.state().goalId() && goal.goal().revision() == claim.state().goalRevision()
				&& loaded.state().identity().characterObjectId() == identity.characterObjectId())
			{
				return new Probe(context, identity.profileId(), identity.characterObjectId(), profiles, goals, authority);
			}
		}
		throw new AssertionError("INVALID historical context: exact profile68 READY|DEAD/COMPLETE/ACTIVE absent; no substitute.");
	}

	public record Receipt(long profileId, int objectId, long epoch, int testNativeLoads, String beginStatus, String advanceStatus, boolean attestedBeforeRunning) { }

	public static final class Probe implements PhantomMaterializationLifecyclePort
	{
		private final PhantomTestContext _context;
		private final long _id;
		private final int _objectId;
		private final PhantomProfileRepository _profiles;
		private final PhantomGoalStateStore _goals;
		private final PhantomBackgroundAuthority _authority;
		private PhantomBackgroundState _beforeState;
		private List<InventoryRow> _beforeItems;
		private Object _beforeClaim, _beforeGoal;
		private Facts _testAdmission;
		private final Facts _imported;
		private final EnumMap<FaultPoint, Integer> _markers = new EnumMap<>(FaultPoint.class);
		private PhantomMaterializationService _materialization;
		private PhantomBackgroundService _background;
		private PhantomHistoricalBackgroundService _historical;
		private PhantomBackgroundTransaction _transactions;
		private PhantomMaterializationLifecyclePort _delegate;
		private Thread _thread;
		private boolean _testPhase, _ran;
		private int _setupLoads, _testLoads;
		private long _epoch;
		private long _testCursor;
		private String _testRequest;
		private boolean _finalizedBeforeRunning;
		private WeakReference<Player> _player = new WeakReference<>(null);
		private WeakReference<PlayerNativeWork.Owner> _owner = new WeakReference<>(null);

		private Probe(PhantomTestContext context, long id, int objectId, PhantomProfileRepository profiles, PhantomGoalStateStore goals, PhantomBackgroundAuthority authority) throws Exception
		{
			_context = context; _id = id; _objectId = objectId; _profiles = profiles; _goals = goals; _authority = authority;
			_imported = facts(false);
			PhantomAssertions.assertEquals(profiles.find(id).orElseThrow().characterObjectId(), objectId, "INVALID context fixture: imported link differs.");
			PhantomAssertions.assertTrue(goals.load(id).isPresent() && _imported.components().stream().noneMatch(row -> row.type().equals(PhantomOwnedStoreIntent.COMPONENT_TYPE)), "INVALID context fixture: goal absent or original owned receipt is pending.");
			_context.record(key("factualSelection"), "one profile=" + id + " object=" + objectId + " originalFullFixture=" + context.measurements().get("w.fixture.beforeHash") + " source=" + context.measurements().get("w.fixture.sourceHash") + " components=" + _imported.components());
		}

		public PhantomBackgroundTransaction.FaultInjector faults()
		{
			return point ->
			{
				if (_testPhase && Thread.currentThread() == _thread && (point == FaultPoint.AFTER_OWNED_PREPARE || point == FaultPoint.AFTER_OWNED_NATIVE_STORE || point == FaultPoint.AFTER_OWNED_FINALIZE_COMMIT))
				{
					final Player player = _player.get(); final var owner = _owner.get();
					PhantomAssertions.assertTrue(player != null && owner != null && player.getObjectId() == _objectId && player.getNativeWorkOwner() == owner && owner.player() == player && owner.isCurrent()
						&& owner.epoch() == _epoch && _materialization.find(_id).orElseThrow().materializedAtNanos() == _epoch, "Historical context STORE marker lost original weak Player/owner/epoch.");
					if (point == FaultPoint.AFTER_OWNED_PREPARE)
					{
						final var row = _profiles.findComponent(_id, PhantomOwnedStoreIntent.COMPONENT_TYPE).orElseThrow();
						final var intent = PhantomOwnedStoreIntent.decode(row.payload());
						PhantomAssertions.assertTrue(row.componentSchemaVersion() == PhantomOwnedStoreIntent.SCHEMA_VERSION && intent.materializedAtNanos() == _epoch
							&& intent.after().identity().profileId() == _id && intent.after().identity().characterObjectId() == _objectId, "Historical context original PREPARE receipt changed identity/epoch.");
					}
					_markers.merge(point, 1, Integer::sum);
					if (point == FaultPoint.AFTER_OWNED_FINALIZE_COMMIT && !_finalizedBeforeRunning)
					{
						final var component = _profiles.findComponent(_id, PhantomBackgroundCatchupState.COMPONENT_TYPE).orElseThrow();
						final var claim = new PhantomBackgroundCatchupStateCodec().decode(component.payload());
						PhantomAssertions.assertTrue(claim.status() == PhantomBackgroundCatchupState.Status.PENDING && Objects.equals(_testRequest, claim.requestId())
							&& claim.cursorEpochMinute() == _testCursor && claim.intervalOrdinal() == 0, "Historical native attestation finalized after RUNNING/interval mutation or under another original claim.");
						_finalizedBeforeRunning = true;
						_context.record(key("finalizedBeforeRunning"), "status=" + claim.status() + " cursor=" + claim.cursorEpochMinute() + " request=" + claim.requestId() + " componentVersion=" + component.rowVersion());
					}
					_context.record(key("boundary." + point), "count=" + _markers.get(point) + " testLoads=" + _testLoads + " epoch=" + _epoch);
				}
			};
		}

		public void bind(PhantomMaterializationService materialization, PhantomHistoricalBackgroundService historical, PhantomBackgroundService background, PhantomBackgroundTransaction transactions)
		{
			PhantomAssertions.assertTrue(_delegate == null && !_ran && materialization.snapshot().state() == PhantomMaterializationService.ServiceState.NEW, "Historical context already bound/running.");
			_materialization = materialization; _historical = historical; _background = background; _transactions = transactions;
			_delegate = PhantomMaterializationLifecyclePort.chain(historical, background);
		}

		public Receipt run() throws Exception
		{
			PhantomAssertions.assertTrue(_delegate != null && !_ran && _materialization.find(_id).isEmpty(), "INVALID context control: fresh bound runtime required.");
			PhantomAssertions.assertEquals(_imported, facts(false), "INVALID context control: original imported facts changed before preflight.");
			_ran = true; _thread = Thread.currentThread();
			try
			{
				PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, _materialization.materialize(_id).status(), "INVALID context preflight: original native materialization failed.");
				PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, _materialization.dematerialize(_id).status(), "INVALID context preflight: original native STORE/drain failed.");
				requireNoOwner();
				final var baseline = facts(false);
				assertPreflight(baseline);
				final var proof = _transactions.nativeContext(_id, _objectId);
				_context.record(key("preflightProof"), "loads=" + _setupLoads + " status=" + proof.status() + " eligible=" + proof.simulationEligible()
					+ " context=" + proof.context() + " canonicalPoints=" + proof.canonicalPoints() + " stateEquals=" + proof.state().equals(baseline.state())
					+ " hashesEqual=" + baseline.state().hashes().equals(_authority.hashes()));
				// Historical native eligibility also applies to DEAD recovery; background work eligibility requires READY.
				PhantomAssertions.assertTrue(_setupLoads == 1 && proof.status() == PhantomBackgroundTransaction.Status.SUCCESS && proof.context().simulationEligible() && proof.context().phase() == PhantomNativeContext.Phase.COMPLETED && proof.canonicalPoints() == 1
					&& proof.state().equals(baseline.state()) && baseline.state().hashes().equals(_authority.hashes()), "INVALID context preflight: exact current supported native baseline was not obtained.");
				_context.record(key("preflight"), "originalNativeLoads=" + _setupLoads + " beforePoints=" + _imported.nativeRow().get(16) + " afterPoints=" + baseline.nativeRow().get(16) + " nativeClampWitnessOnly=true noOldLoadParityClaim=true zeroHistoricalIntervals=true");
				deleteExactFreshScalar(baseline);
				final var legacy = facts(false);
				final var unknown = _transactions.nativeContext(_id, _objectId);
				PhantomAssertions.assertTrue(unknown.status() == PhantomBackgroundTransaction.Status.NATIVE_CONTEXT_REQUIRED && unknown.context().phase() == PhantomNativeContext.Phase.UNKNOWN
					&& !unknown.simulationEligible() && unknown.state().equals(legacy.state()) && unknown.canonicalPoints() == 1, "INVALID context legacy premise: UNKNOWN exact baseline was not observed.");
				final var claim = legacy.catchup().state();
				_testCursor = claim.targetEpochMinute(); _testPhase = true;
				final var begun = _historical.begin(_id, claim.targetEpochMinute(), Math.addExact(claim.targetEpochMinute(), 1), claim.deterministicSeed());
				final var atRunning = facts(false);
				final var fresh = _transactions.nativeContext(_id, _objectId);
				final boolean attested = _testLoads == 1 && _epoch > 0 && _finalizedBeforeRunning && fresh.status() == PhantomBackgroundTransaction.Status.SUCCESS && fresh.context().simulationEligible() && fresh.context().phase() == PhantomNativeContext.Phase.COMPLETED
					&& fresh.state().equals(atRunning.state()) && "1".equals(atRunning.nativeRow().get(16))
					&& _markers.getOrDefault(FaultPoint.AFTER_OWNED_PREPARE, 0) > 0 && _markers.getOrDefault(FaultPoint.AFTER_OWNED_NATIVE_STORE, 0) > 0 && _markers.getOrDefault(FaultPoint.AFTER_OWNED_FINALIZE_COMMIT, 0) > 0;
				PhantomAssertions.assertTrue(begun.successful() && begun.advancedIntervals() == 0 && atRunning.catchup().state().status() == PhantomBackgroundCatchupState.Status.RUNNING
					&& atRunning.catchup().state().cursorEpochMinute() == claim.targetEpochMinute() && atRunning.catchup().state().intervalOrdinal() == 0, "INVALID context handoff: original begin did not reach unadvanced exact RUNNING: " + begun.status() + ":" + begun.reason());
				String advance = "NOT_NEEDED_FOR_ATTESTATION_CONTROL";
				if (!attested)
				{
					final var failed = _historical.advance(_id, 1, 1); advance = failed.status() + ":" + failed.reason();
					final var terminal = facts(false);
					PhantomAssertions.assertTrue(failed.status() == PhantomHistoricalBackgroundService.ResultStatusCode.REPLAN_REQUIRED && failed.reason().startsWith("native_context.required:")
						&& terminal.catchup().state().status() == PhantomBackgroundCatchupState.Status.FAILED_REPLAN_REQUIRED && terminal.catchup().state().cursorEpochMinute() == claim.targetEpochMinute()
						&& terminal.catchup().state().intervalOrdinal() == 0 && _testLoads == 0 && _markers.isEmpty(), "INVALID context RED: original producer did not reach exact terminal UNKNOWN without native attestation: " + advance);
					PhantomAssertions.assertEquals(legacy.withoutCatchup(), terminal.withoutCatchup(), "UNKNOWN refusal changed original protected B4/native/items/non-catchup components.");
				}
				else
				{
					assertAttestationPreserved(legacy, atRunning);
				}
				_context.record(key("actualHandoff"), "begin=" + begun.status() + ":" + begun.reason() + " advance=" + advance + " testLoads=" + _testLoads + " boundaries=" + _markers
					+ " cursor=" + atRunning.catchup().state().cursorEpochMinute() + " request=" + atRunning.catchup().state().requestId() + " attestedBeforeRunning=" + attested + " originalNormalFenced=" + !_historical.permitsNormalOperation(_id)
					+ " delivery=" + _background.nativeContextSignalDelivery(_id).orElse(null) + " deliveryIsNotLiveness=true originalFullRestoreRequired=true");
				PhantomAssertions.assertTrue(attested, "HISTORICAL_UNKNOWN_HANDOFF_RED: original RUNNING/terminal native_context.required lacked fresh HISTORICAL_BASELINE attestation before interval mutation.");
				return new Receipt(_id, _objectId, _epoch, _testLoads, begun.status().name(), advance, true);
			}
			finally { _testPhase = false; _thread = null; }
		}

		private void assertPreflight(Facts baseline)
		{
			PhantomAssertions.assertEquals(_imported.catchup(), baseline.catchup(), "Preflight changed original calendar/claim.");
			PhantomAssertions.assertEquals(_imported.state().identity(), baseline.state().identity(), "Preflight changed original identity.");
			PhantomAssertions.assertEquals(_imported.state().progress(), baseline.state().progress(), "Preflight changed original earned EXP/SP.");
			PhantomAssertions.assertEquals(_imported.state().position(), baseline.state().position(), "Preflight changed original pose/anchor.");
			PhantomAssertions.assertEquals(_imported.state().clock(), baseline.state().clock(), "Preflight changed historical clock.");
			PhantomAssertions.assertEquals(_imported.state().receipt(), baseline.state().receipt(), "Preflight changed interval receipt.");
			PhantomAssertions.assertEquals(_imported.items(), baseline.items(), "Preflight changed original full items/attributes/elements/variables.");
			PhantomAssertions.assertEquals(_imported.skills(), baseline.skills(), "Preflight changed original skills.");
			PhantomAssertions.assertEquals(_imported.components().stream().filter(Probe::fixedComponent).toList(), baseline.components().stream().filter(Probe::fixedComponent).toList(), "Preflight changed protected original non-B4/context components.");
		}

		private static boolean fixedComponent(Component row) { return !row.type().equals(PhantomBackgroundState.COMPONENT_TYPE) && !row.type().equals(PhantomNativeContext.COMPONENT_TYPE); }

		private void assertAttestationPreserved(Facts legacy, Facts atRunning)
		{
			PhantomAssertions.assertEquals(legacy.profile(), atRunning.profile(), "Historical attestation changed original profile/link.");
			PhantomAssertions.assertEquals(legacy.state().identity(), atRunning.state().identity(), "Historical attestation changed original identity.");
			PhantomAssertions.assertEquals(legacy.state().progress(), atRunning.state().progress(), "Historical attestation changed original earned EXP/SP.");
			PhantomAssertions.assertEquals(legacy.state().position(), atRunning.state().position(), "Historical attestation changed original pose/anchor.");
			PhantomAssertions.assertEquals(legacy.state().clock(), atRunning.state().clock(), "Historical attestation changed original historical clock.");
			PhantomAssertions.assertEquals(legacy.state().receipt(), atRunning.state().receipt(), "Historical attestation changed original interval receipt.");
			PhantomAssertions.assertEquals(legacy.state(), atRunning.state(), "Historical attestation changed original logical B4 facts beyond component version rebinding.");
			PhantomAssertions.assertEquals(legacy.nativeRow(), atRunning.nativeRow(), "Historical attestation changed original 17 canonical native scalar facts, including vitality points.");
			PhantomAssertions.assertEquals(legacy.items(), atRunning.items(), "Historical attestation changed original full items/attributes/elements/variables.");
			PhantomAssertions.assertEquals(legacy.skills(), atRunning.skills(), "Historical attestation changed original full skills.");
			PhantomAssertions.assertTrue(_testAdmission != null && _testAdmission.catchup().state().status() == PhantomBackgroundCatchupState.Status.PENDING, "Historical exact pre-load admission absent.");
			PhantomAssertions.assertEquals(legacy.components().stream().filter(Probe::attestationProtectedComponent).filter(row -> !row.type().equals(PhantomGoalStateStore.COMPONENT_TYPE)).toList(), atRunning.components().stream().filter(Probe::attestationProtectedComponent).filter(row -> !row.type().equals(PhantomGoalStateStore.COMPONENT_TYPE)).toList(), "Historical attestation changed original protected population components.");
			PhantomAssertions.assertEquals(_testAdmission.components().stream().filter(Probe::attestationProtectedComponent).toList(), atRunning.components().stream().filter(Probe::attestationProtectedComponent).toList(), "Native attestation changed exact admitted goal or protected components.");
			final var before = legacy.catchup().state(); final var renewed = atRunning.catchup().state();
			final var admitted = _testAdmission.catchup().state();
			final boolean samePlan = renewed.goalId() == before.goalId() && renewed.goalRevision() == before.goalRevision() && renewed.planOrdinal() == before.planOrdinal() && renewed.planIdentity().equals(before.planIdentity());
			final boolean staleRenewal = !before.authorityHashes().equals(_authority.hashes()) && renewed.goalId() == before.goalId()
				&& renewed.goalRevision() == Math.addExact(before.goalRevision(), 1) && renewed.planOrdinal() == Math.addExact(before.planOrdinal(), 1);
			PhantomAssertions.assertTrue(samePlan || staleRenewal, "Historical renewal changed plan outside exact existing stale-authority renewal.");
			PhantomAssertions.assertTrue(admitted.goalId() == renewed.goalId() && admitted.goalRevision() == renewed.goalRevision() && admitted.planOrdinal() == renewed.planOrdinal() && admitted.planIdentity().equals(renewed.planIdentity()), "Native attestation changed exact admitted plan binding.");
			_context.record(key("renewalPlan"), "samePlan=" + samePlan + " staleAuthorityRenewal=" + staleRenewal + " originalGoalRevision=" + before.goalRevision() + " admittedGoalRevision=" + admitted.goalRevision() + " nativeGoalUnchanged=true");
			PhantomAssertions.assertTrue(Objects.equals(_testRequest, renewed.requestId()) && renewed.deterministicSeed() == before.deterministicSeed()
				&& renewed.fromEpochMinute() == before.targetEpochMinute() && renewed.targetEpochMinute() == Math.addExact(before.targetEpochMinute(), 1)
				&& renewed.cursorEpochMinute() == before.targetEpochMinute() && renewed.intervalOrdinal() == 0
				&& admitted.requestId().equals(renewed.requestId()) && admitted.cursorEpochMinute() == renewed.cursorEpochMinute() && admitted.intervalOrdinal() == 0, "Historical attestation changed original requested calendar/seed or exact admitted unadvanced claim.");
		}

		private static boolean attestationProtectedComponent(Component row) { return fixedComponent(row) && !row.type().equals(PhantomBackgroundCatchupState.COMPONENT_TYPE); }

		private void deleteExactFreshScalar(Facts expected) throws Exception
		{
			requireNoOwner();
			final var scalar = _profiles.findComponent(_id, PhantomNativeContext.COMPONENT_TYPE).orElseThrow();
			try (var connection = DatabaseFactory.getConnection())
			{
				connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ); connection.setAutoCommit(false);
				try
				{
					PhantomAssertions.assertEquals(expected, facts(connection, true), "Legacy absence exact locked protected images changed.");
					PhantomAssertions.assertTrue(scalar.componentSchemaVersion() == PhantomNativeContext.SCHEMA_VERSION && PhantomNativeContext.decode(scalar.payload()).phase() == PhantomNativeContext.Phase.COMPLETED, "Legacy absence requires exact fresh completed scalar.");
					try (var statement = connection.prepareStatement("DELETE FROM phantom_profile_components WHERE profile_id=? AND component_type=? AND component_schema_version=? AND row_version=? AND payload=?"))
					{
						statement.setQueryTimeout(5); statement.setLong(1, _id); statement.setString(2, PhantomNativeContext.COMPONENT_TYPE); statement.setInt(3, scalar.componentSchemaVersion()); statement.setLong(4, scalar.rowVersion()); statement.setBytes(5, scalar.payload());
						PhantomAssertions.assertEquals(1, statement.executeUpdate(), "Legacy absence exact fresh scalar CAS failed.");
					}
					PhantomAssertions.assertEquals(expected.withoutScalar(), facts(connection, true), "Legacy absence mutated protected rows beyond exact scalar.");
					connection.commit();
				}
				catch (Throwable failure) { try { connection.rollback(); } catch (Throwable rollback) { if (rollback != failure) { failure.addSuppressed(rollback); } } throw failure; }
			}
			PhantomAssertions.assertEquals(expected.withoutScalar(), facts(false), "Legacy absence committed protected-image verification failed.");
			_context.record(key("legacyPremise"), "TEST_LEGACY_COMPONENT_ABSENCE profile=" + _id + " object=" + _objectId + " scalarVersion=" + scalar.rowVersion() + " payload=" + PhantomBackgroundTransaction.payloadDigest(scalar.payload()) + " originalNativeOwnerDrained=true onlyExactScalarDeleted=true noNaturalImportedUnknownClaim=true");
		}

		public boolean selectedNativeOwnerStopped()
		{
			return _materialization != null && _materialization.snapshot().state() == PhantomMaterializationService.ServiceState.STOPPED && _materialization.snapshot().retainedEntries() == 0
				&& !_materialization.snapshot().cleanupEvidenceIncomplete() && _materialization.find(_id).isEmpty() && World.getInstance().getPlayer(_objectId) == null && World.getInstance().findObject(_objectId) == null
				&& PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(_objectId) == null && !PlayerAutoSaveTaskManager.getInstance().containsObjectId(_objectId);
		}

		public void assertRestored()
		{
			PhantomAssertions.assertTrue(_ran && selectedNativeOwnerStopped() && !DatabaseFactory.isInitialized() && !PhantomSystem.hasConfiguredInstance(), "Context owner/infrastructure remains; full restore cannot be accepted.");
			PhantomAssertions.assertEquals(_context.measurements().get("w.fixture.beforeHash"), _context.measurements().get("w.fixture.restoredHash"), "Original complete PopulationFixture CAS restore was not confirmed.");
			_context.record(key("restored"), "original full pre-import TEST aggregate restored; all native owners stopped; no W cohort credit");
		}

		private void requireNoOwner()
		{
			PhantomAssertions.assertTrue(_materialization.find(_id).isEmpty() && _materialization.snapshot().retainedEntries() == 0 && World.getInstance().getPlayer(_objectId) == null && World.getInstance().findObject(_objectId) == null
				&& PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(_objectId) == null && !PlayerAutoSaveTaskManager.getInstance().containsObjectId(_objectId), "Context control original native owner/World/autosave remains; mutation or restore forbidden.");
		}

		@Override public void beforeMaterialize(long id, int objectId) { beforeMaterialize(id, objectId, PhantomMaterializationService.MaterializationPurpose.NORMAL, ""); }
		@Override public void beforeMaterialize(long id, int objectId, PhantomMaterializationService.MaterializationPurpose purpose, String claim)
		{
			PhantomAssertions.assertTrue(id == _id && objectId == _objectId && Thread.currentThread() == _thread, "INVALID context native admission: foreign profile/thread.");
			PhantomAssertions.assertTrue(!_testPhase || purpose == PhantomMaterializationService.MaterializationPurpose.HISTORICAL_BASELINE, "UNKNOWN control reached NORMAL instead of original historical claim.");
			if (_testPhase) { PhantomAssertions.assertTrue(_testRequest == null || _testRequest.equals(claim), "UNKNOWN handoff changed original request identity."); _testRequest = claim; }
			_delegate.beforeMaterialize(id, objectId, purpose, claim);
			_beforeState = _transactions.load(_id).state();
			_beforeItems = nativeInventory();
			_beforeClaim = _profiles.findComponent(_id, PhantomBackgroundCatchupState.COMPONENT_TYPE).orElseThrow();
			_beforeGoal = _goals.load(_id).orElseThrow();
			if (_testPhase)
			{
				try { _testAdmission = facts(false); }
				catch (Exception failure) { throw new IllegalStateException("Exact historical admission snapshot failed.", failure); }
			}
		}
		@Override public void afterPlayerLoad(long id, Player player)
		{
			PhantomAssertions.assertTrue(id == _id && player.getObjectId() == _objectId && Thread.currentThread() == _thread && World.getInstance().getPlayer(_objectId) == null, "Context observer lacks exact original pre-World load.");
			final var owner = player.getNativeWorkOwner();
			PhantomAssertions.assertTrue(owner != null && owner.player() == player && owner.isCurrent() && _materialization.find(id).orElseThrow().materializedAtNanos() == owner.epoch(), "Context observer lacks exact native owner/epoch.");
			_player = new WeakReference<>(player); _owner = new WeakReference<>(owner); _epoch = owner.epoch();
			if (_testPhase) { _testLoads++; } else { _setupLoads++; }
			_context.record(key("nativeLoad"), "phase=" + (_testPhase ? "UNKNOWN_HANDOFF" : "ORIGINAL_PREFLIGHT") + " object=" + _objectId + " epoch=" + _epoch + " points=" + player.getVitalityPoints() + " maxima=" + player.getMaxHp() + "/" + player.getMaxMp() + "/" + player.getMaxCp());
			triangulateInventory(player);
			_delegate.afterPlayerLoad(id, player);
		}

		private List<InventoryRow> nativeInventory()
		{
			try (var connection = DatabaseFactory.getConnection(); var statement = connection.prepareStatement("SELECT object_id,item_id,count,loc FROM items WHERE owner_id=? ORDER BY object_id"))
			{
				statement.setInt(1, _objectId); statement.setQueryTimeout(5);
				final var result = new ArrayList<InventoryRow>();
				try (var rows = statement.executeQuery())
				{
					while (rows.next()) { PhantomAssertions.assertTrue(result.size() < 10000, "Inventory probe row bound."); result.add(new InventoryRow(rows.getInt(1), rows.getInt(2), rows.getLong(3), rows.getString(4))); }
				}
				return List.copyOf(result);
			}
			catch (Exception failure) { throw new IllegalStateException("Inventory probe canonical SELECT failed.", failure); }
		}

		private void triangulateInventory(Player player)
		{
			PhantomAssertions.assertEquals(_beforeState, _transactions.load(_id).state(), "Inventory A changed during native load.");
			PhantomAssertions.assertEquals(_beforeClaim, _profiles.findComponent(_id, PhantomBackgroundCatchupState.COMPONENT_TYPE).orElseThrow(), "Inventory claim changed during native load.");
			PhantomAssertions.assertEquals(_beforeGoal, _goals.load(_id).orElseThrow(), "Inventory goal changed during native load.");
			final var background = _beforeState.inventory().objects().stream().map(item -> new InventoryRow(item.objectId(), item.itemId(), item.count(), item.location().name())).toList();
			final List<InventoryRow> runtime, projected;
			final PhantomBackgroundState captured;
			synchronized (player)
			{
				synchronized (player.getStatus())
				{
					runtime = player.getInventory().getItems().stream().map(item -> new InventoryRow(item.getObjectId(), item.getId(), item.getCount(), item.getItemLocation().name())).sorted(java.util.Comparator.comparingInt(InventoryRow::objectId)).toList();
					final var old = _beforeState.vitals();
					final var vitals = new PhantomBackgroundState.Vitals(old.currentHp(), player.getMaxHp(), _beforeState.state() == PhantomBackgroundState.State.DEAD ? Math.min(old.currentMp(), player.getMaxMp()) : old.currentMp(), player.getMaxMp(), old.currentCp(), player.getMaxCp());
					final var hint = new PhantomBackgroundState(_beforeState.state(), _beforeState.identity(), _beforeState.progress(), vitals, _beforeState.position(), _beforeState.combat(), _beforeState.loadout(), _beforeState.inventory(), _beforeState.autoGetSkills(), _beforeState.clock(), _beforeState.receipt(), _beforeState.hashes());
					captured = _authority.captureOwnedNative(_id, player, _goals.load(_id).orElseThrow().goal(), hint).state();
					projected = captured.inventory().objects().stream().map(item -> new InventoryRow(item.objectId(), item.itemId(), item.count(), item.location().name())).toList();
					// A conflicting count hint must never overwrite or conceal the actual native count.
					final var original = hint.inventory(); final var first = original.objects().stream().filter(PhantomBackgroundState.ItemObject::stackable).findFirst().orElseThrow();
					final var conflictObjects = original.objects().stream().map(item -> item.objectId() == first.objectId()
						? new PhantomBackgroundState.ItemObject(item.objectId(), item.itemId(), Math.addExact(item.count(), 1), item.stackable(), item.location()) : item).toList();
					final var conflictInventory = PhantomBackgroundState.InventoryFacts.sorted(original.mutableItemIds(), conflictObjects, original.canonicalHash(), original.currentLoad(), original.maximumLoad(), original.usedSlots(), original.maximumSlots());
					final var conflict = new PhantomBackgroundState(hint.state(), hint.identity(), hint.progress(), hint.vitals(), hint.position(), hint.combat(), hint.loadout(), conflictInventory, hint.autoGetSkills(), hint.clock(), hint.receipt(), hint.hashes());
					final var conflictCapture = _authority.captureOwnedNative(_id, player, _goals.load(_id).orElseThrow().goal(), conflict).state();
					PhantomAssertions.assertEquals(captured.inventory().objects(), conflictCapture.inventory().objects(), "Arrival capture must read actual Player item facts despite a conflicting hint.");
					PhantomAssertions.assertFalse(conflictObjects.equals(conflictCapture.inventory().objects()), "Arrival projection concealed a real item/count difference.");
					_context.record(key("inventoryCountConflictControl"), "conflictingHintRejectedByExactComparison=true nativeItemsNotMutated=true");
				}
			}
			final var afterItems = nativeInventory();
			final var nativeLocations = _beforeItems.stream().filter(item -> item.location().equals("INVENTORY") || item.location().equals("PAPERDOLL")).toList();
			final var baselineProjection = nativeLocations.stream().filter(item -> item.location().equals("PAPERDOLL") || _beforeState.inventory().mutableItemIds().contains(item.itemId())).toList();
			final boolean canonicalStable = _beforeItems.equals(afterItems);
			final boolean nativeExact = nativeLocations.equals(runtime);
			final boolean backgroundExact = background.equals(baselineProjection);
			final var expectedSkills = _authority.autoGetSkills(_beforeState.identity(), _beforeState.progress().level());
			final boolean autoGetExact = captured.autoGetSkills().equals(_beforeState.autoGetSkills()) && captured.autoGetSkills().equals(expectedSkills)
				&& expectedSkills.stream().allMatch(skill -> player.getKnownSkill(skill.skillId()) != null && player.getKnownSkill(skill.skillId()).getLevel() == skill.skillLevel());
			final var beforeMap = inventoryMap(background); final var projectionMap = inventoryMap(projected);
			final var ids = new java.util.TreeSet<Integer>(beforeMap.keySet()); ids.addAll(projectionMap.keySet());
			final var differences = ids.stream().filter(id -> !Objects.equals(beforeMap.get(id), projectionMap.get(id))).toList();
			final String classification = canonicalStable && nativeExact && backgroundExact ? "CAPTURE_PROJECTION_DRIFT" : (!backgroundExact && canonicalStable && nativeExact ? "TRUE_GAMEPLAY_CONFLICT" : "BLOCKED_ARCHITECTURE");
			_context.record(key("inventoryTriangulation"), "A_matches_B_under_committed_tracking=" + backgroundExact + " B_equals_D=" + canonicalStable + " C_equals_B_native_locations=" + nativeExact + " classification=" + classification + " diffs=" + differences.size() + " autoGetExact=" + autoGetExact);
			_context.record(key("inventoryTracking"), "committed=" + _beforeState.inventory().mutableItemIds() + " captured=" + captured.inventory().mutableItemIds());
			if (!_testPhase)
			{
				final var evidence = new ArrayList<String>(); evidence.add("view\tprofileId\tcharacterObjectId\tepoch\tobjectId\titemId\tcount\tItemLocation");
				appendInventory(evidence, "A_BACKGROUND", background); appendInventory(evidence, "B_DB_PRE_LOAD", _beforeItems);
				appendInventory(evidence, "C_PLAYER_LOADED", runtime); appendInventory(evidence, "D_DB_POST_LOAD", afterItems); appendInventory(evidence, "P_CAPTURE_PROJECTION", projected);
				evidence.add("# committedMutableItemIds=" + _beforeState.inventory().mutableItemIds()); evidence.add("# capturedMutableItemIds=" + captured.inventory().mutableItemIds());
				final var witness = java.util.Map.of(268496061, 1795, 268567849, 1868, 268568616, 1864, 270235841, 1833, 270570952, 1873);
				for (var entry : new java.util.TreeMap<>(witness).entrySet())
				{
					final int objectId = entry.getKey();
					evidence.add("# classification object=" + objectId + " item=" + entry.getValue() + " A=" + beforeMap.get(objectId) + " B=" + inventoryMap(_beforeItems).get(objectId) + " C=" + inventoryMap(runtime).get(objectId) + " D=" + inventoryMap(afterItems).get(objectId) + " P=" + projectionMap.get(objectId) + " class=" + classification);
				}
				for (var view : java.util.Map.of("A", background, "B", _beforeItems, "C", runtime, "D", afterItems, "P", projected).entrySet())
				{
					final var counts = new java.util.TreeMap<Integer, Long>(); view.getValue().forEach(item -> counts.merge(item.itemId(), item.count(), Math::addExact)); evidence.add("# itemIdTotals " + view.getKey() + "=" + counts);
				}
				evidence.add("# result=" + classification + " differences=" + differences + " autoGetExact=" + autoGetExact);
				try
				{
					final var output = _context.reportsDirectory().resolve("INVENTORY_TRIANGULATION.tsv"); Files.createDirectories(output.getParent()); Files.write(output, evidence, StandardCharsets.UTF_8);
				}
				catch (Exception failure) { throw new IllegalStateException("Inventory triangulation evidence write failed.", failure); }
				final int expected = Integer.getInteger("phantom.m1.inventory.expectedDiffs", 0);
				PhantomAssertions.assertEquals(expected, differences.size(), "Exact profile68 inventory difference count changed.");
				if (expected == 5) { PhantomAssertions.assertEquals(new java.util.TreeSet<>(witness.keySet()), new java.util.TreeSet<>(differences), "Prior exact five object IDs not reproduced."); }
			}
			PhantomAssertions.assertTrue(canonicalStable && nativeExact, "BLOCKED_ARCHITECTURE: native load changed canonical inventory semantics.");
			PhantomAssertions.assertTrue(backgroundExact, "BLOCKED_TRUE_INVENTORY_CONFLICT: committed background objects differ from canonical item/count/location.");
			PhantomAssertions.assertTrue(autoGetExact, "Inventory probe autoGet ceased to be exact.");
			PhantomAssertions.assertTrue(differences.isEmpty(), "INVENTORY_PROJECTION_RED: " + classification + " " + differences);
		}

		private void appendInventory(List<String> evidence, String view, List<InventoryRow> items)
		{
			for (var item : items) { evidence.add(view + "\t" + _id + "\t" + _objectId + "\t" + _epoch + "\t" + item.objectId() + "\t" + item.itemId() + "\t" + item.count() + "\t" + item.location()); }
		}

		private static java.util.Map<Integer, InventoryRow> inventoryMap(List<InventoryRow> items)
		{
			final var result = new java.util.TreeMap<Integer, InventoryRow>();
			for (var item : items) { PhantomAssertions.assertTrue(result.put(item.objectId(), item) == null, "Duplicate inventory object."); }
			return result;
		}
		@Override public void materializeSucceeded(long id, int objectId) { _delegate.materializeSucceeded(id, objectId); }
		@Override public void materializeAborted(long id, int objectId) { _delegate.materializeAborted(id, objectId); }
		@Override public void beforeStore(long id, Player player) { _delegate.beforeStore(id, player); }
		@Override public void afterStore(long id, Player player) { _delegate.afterStore(id, player); }

		private Facts facts(boolean locked) throws Exception
		{
			try (var connection = DatabaseFactory.getConnection()) { return facts(connection, locked); }
		}

		private Facts facts(Connection connection, boolean locked) throws Exception
		{
			final String lock = locked ? " FOR UPDATE" : "";
			String profile;
			try (var statement = connection.prepareStatement("SELECT * FROM phantom_profiles WHERE profile_id=?" + lock))
			{
				statement.setLong(1, _id); statement.setQueryTimeout(5);
				try (var rows = statement.executeQuery()) { PhantomAssertions.assertTrue(rows.next() && rows.getInt("character_object_id") == _objectId, "Original context profile link changed."); profile = rowDigest(rows); PhantomAssertions.assertFalse(rows.next(), "Duplicate context profile."); }
			}
			final var components = new ArrayList<Component>(); PhantomBackgroundState state = null; PhantomBackgroundCatchupStore.Snapshot catchup = null;
			try (var statement = connection.prepareStatement("SELECT * FROM phantom_profile_components WHERE profile_id=? ORDER BY component_type" + lock))
			{
				statement.setLong(1, _id); statement.setQueryTimeout(5);
				try (var rows = statement.executeQuery())
				{
					while (rows.next())
					{
						final String type = rows.getString("component_type"); final int schema = rows.getInt("component_schema_version"); final long version = rows.getLong("row_version"); final byte[] payload = rows.getBytes("payload");
						components.add(new Component(type, schema, version, PhantomBackgroundTransaction.payloadDigest(payload), rowDigest(rows)));
						if (type.equals(PhantomBackgroundState.COMPONENT_TYPE)) { PhantomAssertions.assertEquals(PhantomBackgroundState.SCHEMA_VERSION, schema, "Original B4 schema differs."); state = new PhantomBackgroundStateCodec().decode(payload); }
						if (type.equals(PhantomBackgroundCatchupState.COMPONENT_TYPE)) { PhantomAssertions.assertEquals(PhantomBackgroundCatchupState.SCHEMA_VERSION, schema, "Original catchup schema differs."); catchup = new PhantomBackgroundCatchupStore.Snapshot(new PhantomBackgroundCatchupStateCodec().decode(payload), version); }
					}
				}
			}
			final var nativeRow = new ArrayList<String>();
			try (var statement = connection.prepareStatement("SELECT level,exp,expBeforeDeath,sp,curHp,maxHp,curMp,maxMp,curCp,maxCp,x,y,z,heading,classid,race,vitality_points,account_name FROM characters WHERE charId=?" + lock))
			{
				statement.setInt(1, _objectId); statement.setQueryTimeout(5);
				try (var rows = statement.executeQuery())
				{
					PhantomAssertions.assertTrue(rows.next() && ("p" + Long.toString(_id, 36)).equals(rows.getString(18)), "Context control is not the original imported profile account.");
					for (int i = 1; i <= 17; i++) { nativeRow.add(rows.getString(i)); } PhantomAssertions.assertFalse(rows.next(), "Duplicate native context character.");
				}
			}
			PhantomAssertions.assertTrue(state != null && catchup != null && state.identity().profileId() == _id && state.identity().characterObjectId() == _objectId, "Original context B4/claim identity unavailable.");
			return new Facts(profile, state, catchup, List.copyOf(components), List.copyOf(nativeRow), digest(connection, List.of("SELECT * FROM characters WHERE charId=?"), lock),
				digest(connection, List.of("SELECT * FROM character_skills WHERE charId=?"), lock), digest(connection, List.of("SELECT * FROM items WHERE owner_id=?", "SELECT * FROM item_attributes WHERE itemId IN (SELECT object_id FROM items WHERE owner_id=?)", "SELECT * FROM item_elementals WHERE itemId IN (SELECT object_id FROM items WHERE owner_id=?)", "SELECT * FROM item_variables WHERE id IN (SELECT object_id FROM items WHERE owner_id=?)"), lock));
		}

		private String digest(Connection connection, List<String> queries, String lock) throws Exception
		{
			final var digest = MessageDigest.getInstance("SHA-256");
			for (String sql : queries)
			{
				final var values = new ArrayList<String>();
				try (var statement = connection.prepareStatement(sql + lock))
				{
					statement.setInt(1, _objectId); statement.setQueryTimeout(5);
					try (var rows = statement.executeQuery()) { while (rows.next()) { PhantomAssertions.assertTrue(values.size() < 10000, "Context protected image row bound."); values.add(rowDigest(rows)); } }
				}
				values.sort(String::compareTo); digest.update(sql.getBytes(java.nio.charset.StandardCharsets.UTF_8)); digest.update((byte) 0);
				for (String value : values) { digest.update(value.getBytes(java.nio.charset.StandardCharsets.US_ASCII)); } digest.update((byte) 0);
			}
			return HexFormat.of().formatHex(digest.digest());
		}

		private String key(String suffix) { return "historicalContext." + _id + "." + suffix; }
	}

	private static String rowDigest(ResultSet rows) throws Exception
	{
		final var bytes = new ByteArrayOutputStream(); final var metadata = rows.getMetaData();
		try (var out = new DataOutputStream(bytes))
		{
			out.writeInt(metadata.getColumnCount());
			for (int i = 1; i <= metadata.getColumnCount(); i++) { out.writeUTF(metadata.getColumnName(i)); final byte[] value = rows.getBytes(i); PhantomAssertions.assertTrue(value == null || value.length <= 4194304, "Context protected image cell bound."); out.writeInt(value == null ? -1 : value.length); if (value != null) { out.write(value); } }
		}
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
	}
	private record Component(String type, int schema, long version, String payload, String fullRow) { }
	private record InventoryRow(int objectId, int itemId, long count, String location) { }
	private record Facts(String profile, PhantomBackgroundState state, PhantomBackgroundCatchupStore.Snapshot catchup, List<Component> components, List<String> nativeRow, String character, String skills, String items)
	{
		Facts withoutScalar() { return new Facts(profile, state, catchup, components.stream().filter(row -> !row.type().equals(PhantomNativeContext.COMPONENT_TYPE)).toList(), nativeRow, character, skills, items); }
		Facts withoutCatchup() { return new Facts(profile, state, null, components.stream().filter(row -> !row.type().equals(PhantomBackgroundCatchupState.COMPONENT_TYPE)).toList(), nativeRow, character, skills, items); }
	}
}
