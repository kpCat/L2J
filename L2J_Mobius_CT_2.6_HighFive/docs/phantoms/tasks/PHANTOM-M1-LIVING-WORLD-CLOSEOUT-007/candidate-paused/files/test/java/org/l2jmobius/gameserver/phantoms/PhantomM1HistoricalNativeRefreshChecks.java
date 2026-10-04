/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms;

import java.lang.ref.WeakReference;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundAuthority;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupStore;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundInventoryHash;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundInventoryHash.CanonicalItem;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundService;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.ItemLocation;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction.FaultPoint;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundService;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecyclePort;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.MaterializationPurpose;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomM1PopulationFixture.NativeSnapshot;
import org.l2jmobius.tests.phantoms.PhantomM1PopulationFixture;
import org.l2jmobius.tests.phantoms.PhantomTestContext;
import org.l2jmobius.tests.phantoms.PhantomTestDatabaseGuard;

/**
 * Focused native controls on factual imported old state. The caller takes the full private
 * NativeSnapshot before native initialization, composes this port before services start, and
 * retains it until all exact native owners stop and original snapshot/CAS restoration succeeds.
 * This helper neither imports data nor changes hashes, maxima, levels, rates, gear or Player pose.
 */
public final class PhantomM1HistoricalNativeRefreshChecks
{
	private PhantomM1HistoricalNativeRefreshChecks() { }
	public enum Mode { POSITIVE, NORMAL_OLD_HASH, WRONG_REQUEST, CLAIM_VERSION, CLAIM_PAYLOAD }
	public record Receipt(Mode mode, long profileId, int objectId, long epoch, int nativeLoads, int prepares, int nativeStores, int finalizes, String outcome, boolean fullSnapshotRestoreRequired) { }

	/**
	 * Construct after trusted import but before Player load/service start. importedSourceSha256
	 * names the caller's frozen factual source; it is recorded, never used to manufacture state.
	 * NativeSnapshot must be the original full guarded image for this exact profile/object.
	 */
	public static Probe probe(PhantomTestContext context, long profileId, Mode mode, PhantomProfileRepository profiles,
		PhantomGoalStateStore goals, PhantomBackgroundAuthority authority, NativeSnapshot nativeSnapshot, String importedSourceSha256)
	{
		return new Probe(context, profileId, mode, profiles, goals, authority, Objects.requireNonNull(nativeSnapshot), null, importedSourceSha256);
	}

	/** Borrow the original population fixture captured before native bootstrap; never take a nested snapshot. */
	public static Probe probeImported(PhantomTestContext context, long profileId, Mode mode, PhantomProfileRepository profiles,
		PhantomGoalStateStore goals, PhantomBackgroundAuthority authority, PhantomM1PopulationFixture fixture)
	{
		Objects.requireNonNull(fixture);
		PhantomAssertions.assertTrue(fixture.readyIdentities().stream().anyMatch(identity -> identity.profileId() == profileId), "Historical profile is outside the exact original imported READY roster.");
		return new Probe(context, profileId, mode, profiles, goals, authority, null, fixture, Objects.toString(context.measurements().get("w.fixture.sourceHash"), ""));
	}

	public static final class Probe implements PhantomMaterializationLifecyclePort
	{
		private final PhantomTestContext _context;
		private final long _id;
		private final Mode _mode;
		private final PhantomProfileRepository _profiles;
		private final PhantomBackgroundAuthority _authority;
		private final NativeSnapshot _nativeSnapshot;
		private final PhantomM1PopulationFixture _populationFixture;
		private final String _snapshotHash;
		private final PhantomBackgroundCatchupStore _catchups;
		private final Image _before;
		private final EnumMap<FaultPoint, Integer> _markers = new EnumMap<>(FaultPoint.class);
		private PhantomMaterializationLifecyclePort _delegate;
		private PhantomMaterializationService _materialization;
		private PhantomHistoricalBackgroundService _historical;
		private PhantomBackgroundTransaction _transactions;
		private Thread _runThread;
		private WeakReference<Player> _player = new WeakReference<>(null);
		private PlayerNativeWork.Owner _owner;
		private long _epoch;
		private int _loads;
		private int _succeeded;
		private String _loadFailure;
		private Image _atRefusal;
		private EnumMap<FaultPoint, Integer> _markersAtRefusal;
		private Component _mutatedClaim;
		private PhantomBackgroundState.Vitals _nativeVitals;
		private boolean _ran;

		private Probe(PhantomTestContext context, long profileId, Mode mode, PhantomProfileRepository profiles,
			PhantomGoalStateStore goals, PhantomBackgroundAuthority authority, NativeSnapshot nativeSnapshot, PhantomM1PopulationFixture populationFixture, String source)
		{
			_context = Objects.requireNonNull(context); _id = profileId; _mode = Objects.requireNonNull(mode);
			_profiles = Objects.requireNonNull(profiles); Objects.requireNonNull(goals); _authority = Objects.requireNonNull(authority);
			_nativeSnapshot = nativeSnapshot; _populationFixture = populationFixture;
			PhantomAssertions.assertTrue((nativeSnapshot == null) != (populationFixture == null), "Exactly one original private full fixture snapshot is required.");
			_snapshotHash = Objects.toString(context.measurements().get(populationFixture == null ? "q14.snapshot.beforeHash" : "w.fixture.beforeHash"), "");
			PhantomAssertions.assertEquals(PhantomTestDatabaseGuard.TARGET_DATABASE, context.measurements().get("headless.database"), "Historical refresh requires guarded native TEST.");
			PhantomAssertions.assertTrue(profileId > 0 && source != null && source.matches("[0-9A-Fa-f]{64}") && _snapshotHash.matches("[0-9A-Fa-f]{64}"), "INVALID historical fixture: frozen imported source and durable full native snapshot digests required.");
			_catchups = new PhantomBackgroundCatchupStore(profiles, goals); _before = image();
			final var state = _before.state(); final var claim = _catchups.load(_id).orElseThrow().state();
			PhantomAssertions.assertTrue((state.state() == PhantomBackgroundState.State.READY || state.state() == PhantomBackgroundState.State.DEAD)
				&& !state.hashes().equals(authority.hashes()) && claim.status() == PhantomBackgroundCatchupState.Status.COMPLETE,
				"INVALID historical fixture: original old-hash READY/DEAD and completed persisted claim required.");
			final var goal = goals.load(_id).orElseThrow().goal();
			PhantomAssertions.assertTrue(goal.goalId() == claim.goalId() && goal.revision() == claim.goalRevision() && goal.status() == PhantomGoalStatus.ACTIVE
				&& PhantomBackgroundGoalSpec.GOAL_TYPE.equals(goal.goalType()), "INVALID historical fixture: original completed claim/ACTIVE farm goal identity differs.");
			PhantomBackgroundGoalSpec.parse(goal);
			PhantomAssertions.assertEquals(profiles.find(_id).orElseThrow().characterObjectId(), state.identity().characterObjectId(), "INVALID historical fixture: exact imported character link differs.");
			PhantomAssertions.assertEquals(state.inventory().canonicalHash(), PhantomBackgroundInventoryHash.compute(_before.items().stream().map(ItemRow::item).toList()), "INVALID historical fixture: original canonical full inventory hash differs from imported native rows.");
			_context.record(key("source"), "sha256=" + source + " originalState=" + state + " components=" + _before.components() + " native=" + _before.nativeRow() + " fullItems=" + _before.items() + " privateSnapshotRequired=true");
		}

		/** Pass this original passive injector to the existing three-argument Transaction constructor. */
		public PhantomBackgroundTransaction.FaultInjector faults()
		{
			return point ->
			{
				if (Thread.currentThread() != _runThread) { return; }
				if (point == FaultPoint.AFTER_OWNED_PREPARE || point == FaultPoint.AFTER_OWNED_NATIVE_STORE || point == FaultPoint.AFTER_OWNED_FINALIZE_COMMIT)
				{
					_markers.merge(point, 1, Math::addExact);
						_context.record(key("marker." + point + "." + count(point)), "count=" + count(point) + " loads=" + _loads + " loadRefused=" + (_loadFailure != null) + " exactEpoch=" + _epoch);
				}
			};
		}

		/** Bind exactly once before start, then install this port through the existing lifecycle bridge. */
		public void bind(PhantomMaterializationService materialization, PhantomHistoricalBackgroundService historical,
			PhantomBackgroundService background, PhantomBackgroundTransaction transactions)
		{
			PhantomAssertions.assertTrue(_delegate == null && _runThread == null, "Historical probe composition already bound/running.");
			_materialization = Objects.requireNonNull(materialization); _historical = Objects.requireNonNull(historical); _transactions = Objects.requireNonNull(transactions);
			PhantomAssertions.assertEquals(PhantomMaterializationService.ServiceState.NEW, materialization.snapshot().state(), "Historical lifecycle must be bound before fresh native materialization starts.");
			_delegate = PhantomMaterializationLifecyclePort.chain(historical, Objects.requireNonNull(background));
		}

		/** Does not start actors, AutoPlay, services or a historical interval; only original admission/renewal. */
		public Receipt run() throws Exception
		{
			PhantomAssertions.assertTrue(_delegate != null && !_ran && _materialization.find(_id).isEmpty(), "INVALID historical fixture: bound, fresh unmaterialized exact profile required.");
			PhantomAssertions.assertEquals(_before, image(), "INVALID historical fixture: frozen imported facts changed before the control.");
			_ran = true; _runThread = Thread.currentThread();
			String outcome = "NOT_RETURNED"; Throwable primary = null;
			try
			{
				if (_mode == Mode.NORMAL_OLD_HASH || _mode == Mode.WRONG_REQUEST)
				{
					final String request = _catchups.load(_id).orElseThrow().state().requestId();
					final String wrong = request.equals("0".repeat(64)) ? "1".repeat(64) : "0".repeat(64);
					final var result = _mode == Mode.NORMAL_OLD_HASH ? _materialization.materialize(_id) : _materialization.materialize(_id, MaterializationPurpose.HISTORICAL_BASELINE, wrong);
					outcome = result.status().name();
					PhantomAssertions.assertTrue(result.status() != PhantomMaterializationService.ResultStatus.SUCCESS, "Original negative historical admission succeeded.");
					if (_mode == Mode.WRONG_REQUEST)
					{
						PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.CATCHUP_FENCED, result.status(), "Wrong original owner claim was not rejected upstream.");
						PhantomAssertions.assertEquals(0, _loads, "Wrong request reached native Player.load.");
						PhantomAssertions.assertTrue(_succeeded == 0 && count(FaultPoint.AFTER_OWNED_PREPARE) == 0 && count(FaultPoint.AFTER_OWNED_NATIVE_STORE) == 0 && count(FaultPoint.AFTER_OWNED_FINALIZE_COMMIT) == 0, "Wrong request reached native PREPARE/STORE/finalize.");
					}
				}
				else
				{
					final var claim = _catchups.load(_id).orElseThrow().state();
					final long from = claim.targetEpochMinute();
					final var result = _historical.begin(_id, from, Math.addExact(from, 1), claim.deterministicSeed());
					outcome = result.status() + ":" + result.reason();
					if (_mode == Mode.POSITIVE) { PhantomAssertions.assertTrue(result.successful() && result.advancedIntervals() == 0, "Original historical renewal/refresh failed or awarded an interval: " + outcome); }
					else { PhantomAssertions.assertFalse(result.successful(), "Mutated persisted admission was accepted by original historical renewal."); }
				}
				_context.record(key("outcome"), outcome);
				final Image after = image(); recordAfter(after, outcome);
				if (_mode == Mode.POSITIVE) { assertPositive(after); }
				else { assertNegative(after); }
				PhantomAssertions.assertTrue(_materialization.find(_id).isEmpty(), "Original negative/positive native cleanup retained its exact owner; snapshot restore remains forbidden.");
				return new Receipt(_mode, _id, _before.state().identity().characterObjectId(), _epoch, _loads,
					count(FaultPoint.AFTER_OWNED_PREPARE), count(FaultPoint.AFTER_OWNED_NATIVE_STORE), count(FaultPoint.AFTER_OWNED_FINALIZE_COMMIT), outcome, true);
			}
			catch (Exception | Error failure) { primary = failure; throw failure; }
			finally
			{
				try { recordAfter(image(), outcome); }
				catch (RuntimeException | Error secondary) { if (primary == null) { throw secondary; } if (secondary != primary) { primary.addSuppressed(secondary); } }
				finally { _runThread = null; }
			}
		}

		/** Selected exact owner only; the caller must also stop the native TEST environment/global writers. */
		public boolean selectedNativeOwnerStopped()
		{
			if (_materialization == null) { return false; }
			final var service = _materialization.snapshot(); final int objectId = _before.state().identity().characterObjectId();
			return service.state() == PhantomMaterializationService.ServiceState.STOPPED && service.retainedEntries() == 0 && !service.cleanupEvidenceIncomplete()
				&& _materialization.find(_id).isEmpty() && World.getInstance().getPlayer(objectId) == null && World.getInstance().findObject(objectId) == null
				&& PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(objectId) == null;
		}

		/** Preserve the original private journal when cleanup is retained/unknown; never force restore. */
		public void retainNativeSnapshot() throws Exception
		{
			PhantomAssertions.assertTrue(_nativeSnapshot != null, "Imported population fixture must be retained by its caller without close until all native owners stop.");
			_nativeSnapshot.retainJournal();
		}

		/** Call only after caller stopped all native owners and original NativeSnapshot.close succeeded. */
		public void assertRestored()
		{
			PhantomAssertions.assertTrue(_ran && selectedNativeOwnerStopped() && !PhantomSystem.hasConfiguredInstance(), "Native ownership must end before exact snapshot restoration verification.");
			PhantomAssertions.assertEquals(_snapshotHash, _context.measurements().get(_populationFixture == null ? "q14.snapshot.restoredHash" : "w.fixture.restoredHash"), "Original full native snapshot/CAS restore was not confirmed.");
			if (_populationFixture == null) { PhantomAssertions.assertEquals(_before, image(), "Original private full snapshot restore did not restore exact historical native/component facts."); }
			_context.record(key("restored"), "original NativeSnapshot/CAS full-image restore succeeded; exact native rows and all profile component versions/digests restored");
			_player.clear(); _owner = null;
		}

		@Override public void beforeMaterialize(long id, int objectId) { beforeMaterialize(id, objectId, MaterializationPurpose.NORMAL, ""); }
		@Override public void beforeMaterialize(long id, int objectId, MaterializationPurpose purpose, String ownerClaim)
		{
			if (id == _id) { requireRun(); }
			_delegate.beforeMaterialize(id, objectId, purpose, ownerClaim);
		}
		@Override public void afterPlayerLoad(long id, Player player)
		{
			if (id != _id) { _delegate.afterPlayerLoad(id, player); return; }
			requireRun(); _loads++; _player = new WeakReference<>(player); _owner = player.getNativeWorkOwner(); _epoch = _owner == null ? -1 : _owner.epoch();
			PhantomAssertions.assertTrue(_owner != null && _owner.player() == player && _owner.isCurrent() && PlayerNativeWork.current(_owner) == null && !player.isAutoPlaying()
				&& World.getInstance().getPlayer(player.getObjectId()) == null && player.getObjectId() == _before.state().identity().characterObjectId(), "INVALID historical load: exact pre-World native owner/epoch required.");
			_nativeVitals = new PhantomBackgroundState.Vitals(player.getCurrentHp(), player.getMaxHp(), player.getCurrentMp(), player.getMaxMp(), player.getCurrentCp(), player.getMaxCp());
			final var state = _before.state(); final var vitals = state.vitals();
			final boolean nativeMaximaChanged = vitals.maximumHp() != player.getMaxHp() || vitals.maximumMp() != player.getMaxMp() || vitals.maximumCp() != player.getMaxCp();
			_context.record(key("nativeMaximaChanged"), nativeMaximaChanged);
			PhantomAssertions.assertTrue(nativeMaximaChanged, "INVALID historical refresh: actual native maxima already match; hash-only case does not reach refresh.");
			final double expectedMp = state.state() == PhantomBackgroundState.State.DEAD ? Math.min(vitals.currentMp(), player.getMaxMp()) : vitals.currentMp();
			PhantomAssertions.assertTrue(Math.abs(vitals.currentHp() - player.getCurrentHp()) <= 0.000001d && Math.abs(vitals.currentCp() - player.getCurrentCp()) <= 0.000001d
				&& Math.abs(expectedMp - player.getCurrentMp()) <= 0.000001d && vitals.currentCp() <= player.getMaxCp()
				&& (state.state() != PhantomBackgroundState.State.READY || (vitals.currentHp() <= player.getMaxHp() && vitals.currentMp() <= player.getMaxMp())),
				"INVALID historical refresh: original current vitals differ beyond native maxima/approved DEAD MP clamp.");
			final var normalized = new PhantomBackgroundState(state.state(), state.identity(), state.progress(), _nativeVitals, state.position(), state.combat(), state.loadout(), state.inventory(), state.autoGetSkills(), state.clock(), state.receipt(), state.hashes());
			PhantomAssertions.assertTrue(_authority.matchesRuntime(player, normalized) && liveItems(player).equals(_before.items()), "INVALID historical refresh: imported non-vital fields or full items differ from original native load.");
			_context.record(key("nativeLoaded"), "object=" + player.getObjectId() + " epoch=" + _epoch + " old=" + vitals + " native=" + _nativeVitals + " oldHashes=" + state.hashes() + " currentHashes=" + _authority.hashes());
			if (_mode == Mode.CLAIM_VERSION || _mode == Mode.CLAIM_PAYLOAD)
			{
				final var expected = _catchups.load(_id).orElseThrow();
				final var replacement = _mode == Mode.CLAIM_VERSION ? expected.state() : expected.state().failed("test.persisted_claim_changed");
				_catchups.replace(_id, expected, replacement);
				_mutatedClaim = component(PhantomBackgroundCatchupState.COMPONENT_TYPE);
				_context.record(key("claimMutation"), "original=" + expected + " exactCurrent=" + _mutatedClaim + " sameRequest=" + replacement.requestId().equals(expected.state().requestId()) + " payloadChanged=" + !replacement.equals(expected.state()));
			}
			try { _delegate.afterPlayerLoad(id, player); }
			catch (RuntimeException | Error failure)
			{
				_loadFailure = failure.getClass().getName() + ":" + failure.getMessage(); _markersAtRefusal = new EnumMap<>(_markers);
				try { _atRefusal = image(); }
				catch (RuntimeException | Error secondary) { if (secondary != failure) { failure.addSuppressed(secondary); } }
				_context.record(key("firstLoadRefusal"), "failure=" + _loadFailure + " markers=" + _markers + " durableAtRefusal=" + _atRefusal);
				throw failure;
			}
		}
		@Override public void materializeSucceeded(long id, int objectId)
		{
			if (id == _id)
			{
				_succeeded++; final Player player = _player.get(); requireExact(player);
				PhantomAssertions.assertEquals(Mode.POSITIVE, _mode, "Rejected historical mode reached native ACTIVE.");
				PhantomAssertions.assertTrue(count(FaultPoint.AFTER_OWNED_PREPARE) == 1 && count(FaultPoint.AFTER_OWNED_NATIVE_STORE) == 1 && count(FaultPoint.AFTER_OWNED_FINALIZE_COMMIT) == 1, "Refresh did not complete exactly one original owned STORE before native ACTIVE.");
				PlayerNativeWork.checkpoint(player, () -> { assertFixed(_transactions.load(_id).state()); PhantomAssertions.assertEquals(_before.items(), liveItems(player), "Native refresh changed live item objects/slots/enchant."); return null; });
			}
			_delegate.materializeSucceeded(id, objectId);
		}
		@Override public void materializeAborted(long id, int objectId) { _delegate.materializeAborted(id, objectId); }
		@Override public void beforeStore(long id, Player player) { _delegate.beforeStore(id, player); }
		@Override public void afterStore(long id, Player player) { _delegate.afterStore(id, player); }

		private void assertPositive(Image after)
		{
			PhantomAssertions.assertTrue(_loads == 1 && _succeeded == 1 && _loadFailure == null && _nativeVitals != null, "Positive actual native load/refresh proof is incomplete.");
			PhantomAssertions.assertTrue(count(FaultPoint.AFTER_OWNED_PREPARE) == 2 && count(FaultPoint.AFTER_OWNED_NATIVE_STORE) == 2 && count(FaultPoint.AFTER_OWNED_FINALIZE_COMMIT) == 2, "Original refresh plus original dematerialization STORE boundaries are incomplete.");
			assertFixed(after.state()); PhantomAssertions.assertEquals(_authority.hashes(), after.state().hashes(), "Native historical refresh did not commit pinned current authority hashes.");
			PhantomAssertions.assertEquals(_nativeVitals, after.state().vitals(), "Native historical refresh did not preserve actual loaded vitals/maxima.");
			PhantomAssertions.assertEquals(_before.items(), after.items(), "Native historical refresh changed canonical full item objects/slots/enchant.");
			final double[] expectedVitals = { _nativeVitals.currentHp(), _nativeVitals.maximumHp(), _nativeVitals.currentMp(), _nativeVitals.maximumMp(), _nativeVitals.currentCp(), _nativeVitals.maximumCp() };
			for (int column = 0; column < after.nativeRow().size(); column++)
			{
				if (column >= 4 && column <= 9)
				{
					PhantomAssertions.assertTrue(Math.abs(expectedVitals[column - 4] - Double.parseDouble(after.nativeRow().get(column))) <= 0.000001d, "Original native STORE differs from exact loaded vital column " + column);
				}
				else { PhantomAssertions.assertEquals(_before.nativeRow().get(column), after.nativeRow().get(column), "Native refresh changed original progress/identity/pose/vitality column " + column); }
			}
		}
		private void assertNegative(Image after)
		{
			if (_mode != Mode.WRONG_REQUEST)
			{
				PhantomAssertions.assertTrue(_loads == 1 && _succeeded == 0 && _loadFailure != null && _atRefusal != null, "Negative control did not reach/refuse the original native refresh path.");
				PhantomAssertions.assertTrue(_markersAtRefusal != null && _markersAtRefusal.isEmpty(), "Rejected native refresh already reached original PREPARE/native STORE/finalize before refusal.");
				if (_mode == Mode.NORMAL_OLD_HASH) { PhantomAssertions.assertTrue(_loadFailure.contains("hashMatch=false"), "NORMAL control was refused for another prerequisite before the original stale-hash refresh guard."); }
				if (_mode == Mode.CLAIM_VERSION || _mode == Mode.CLAIM_PAYLOAD) { PhantomAssertions.assertTrue(_loadFailure.contains("HISTORICAL_ADMISSION_STALE"), "Persisted claim change did not cause the exact admission failure."); }
				assertUnchangedExceptClaim(_atRefusal, "at original load refusal");
			}
			// Cleanup may attempt original native STORE. Record it first; a rejected admission must
			// not thereby publish fresh maxima/hash or alter the original committed/native facts.
			assertUnchangedExceptClaim(after, "after original abort/cleanup");
		}
		private void assertUnchangedExceptClaim(Image after, String boundary)
		{
			PhantomAssertions.assertEquals(_before.state(), after.state(), "Rejected historical control changed original committed state " + boundary);
			PhantomAssertions.assertEquals(_before.nativeRow(), after.nativeRow(), "Rejected historical control changed original native canonical row " + boundary);
			PhantomAssertions.assertEquals(_before.items(), after.items(), "Rejected historical control changed original native full inventory " + boundary);
			final var expected = new ArrayList<>(_before.components());
			if (_mutatedClaim != null) { expected.removeIf(row -> row.type().equals(PhantomBackgroundCatchupState.COMPONENT_TYPE)); expected.add(_mutatedClaim); expected.sort(Comparator.comparing(Component::type)); }
			PhantomAssertions.assertEquals(expected, after.components(), "Rejected historical control changed unrelated component/receipt or its exact mutated claim " + boundary);
		}
		private void assertFixed(PhantomBackgroundState after)
		{
			final var before = _before.state(); PhantomAssertions.assertTrue(after != null, "Native refresh canonical state is absent.");
			PhantomAssertions.assertEquals(before.identity(), after.identity(), "Refresh changed exact identity.");
			PhantomAssertions.assertEquals(before.progress(), after.progress(), "Refresh changed level/EXP/SP/expBeforeDeath.");
			PhantomAssertions.assertEquals(before.position(), after.position(), "Refresh changed pose/heading/anchor.");
			PhantomAssertions.assertEquals(before.clock(), after.clock(), "Refresh changed historical clock.");
			PhantomAssertions.assertEquals(before.receipt(), after.receipt(), "Refresh changed durable interval receipt.");
			PhantomAssertions.assertEquals(before.inventory().objects(), after.inventory().objects(), "Refresh changed tracked item objects.");
			PhantomAssertions.assertEquals(before.inventory().canonicalHash(), after.inventory().canonicalHash(), "Refresh changed full canonical inventory hash.");
			PhantomAssertions.assertEquals(before.autoGetSkills(), after.autoGetSkills(), "Refresh changed tracked auto-get skills.");
		}
		private void requireExact(Player player)
		{
			PhantomAssertions.assertTrue(player != null && player.getNativeWorkOwner() == _owner && _owner.player() == player && _owner.isCurrent() && _owner.epoch() == _epoch
				&& _materialization.find(_id).orElseThrow().materializedAtNanos() == _epoch && PlayerNativeWork.current(_owner) == null, "Historical observer lost exact native Player/owner/epoch outside ActionLease.");
		}
		private void requireRun() { PhantomAssertions.assertTrue(_runThread == Thread.currentThread(), "INVALID historical control: unrelated concurrent admission reached the selected profile."); }
		private int count(FaultPoint point) { return _markers.getOrDefault(point, 0); }
		private String key(String suffix) { return "historicalNative." + _mode + "." + _id + "." + suffix; }
		private void recordAfter(Image after, String outcome)
		{
			_context.record(key("after"), "outcome=" + outcome + " loads=" + _loads + " success=" + _succeeded + " markers=" + _markers + " loadFailure=" + _loadFailure + " materialization=" + _materialization.find(_id).orElse(null) + " nativeCanonical=" + after + " fullSnapshotRestoreStillRequired=true");
		}
		private Component component(String type)
		{
			final var row = _profiles.findComponent(_id, type).orElseThrow();
			return new Component(row.componentType(), row.componentSchemaVersion(), row.rowVersion(), PhantomBackgroundTransaction.payloadDigest(row.payload()));
		}
		private Image image()
		{
			final var loaded = new PhantomBackgroundTransaction().load(_id);
			PhantomAssertions.assertTrue(loaded.successful() && loaded.state() != null, "Imported historical canonical state is unavailable.");
			final var components = _profiles.listComponents(_id).stream().map(row -> new Component(row.componentType(), row.componentSchemaVersion(), row.rowVersion(), PhantomBackgroundTransaction.payloadDigest(row.payload()))).sorted(Comparator.comparing(Component::type)).toList();
			final int objectId = loaded.state().identity().characterObjectId();
			try (var connection = DatabaseFactory.getConnection(); var character = connection.prepareStatement("SELECT level,exp,expBeforeDeath,sp,curHp,maxHp,curMp,maxMp,curCp,maxCp,x,y,z,heading,classid,race,vitality_points FROM characters WHERE charId=?");
				var items = connection.prepareStatement("SELECT object_id,item_id,count,loc,loc_data,enchant_level FROM items WHERE owner_id=? AND count>0 AND loc IN ('INVENTORY','PAPERDOLL') ORDER BY object_id"))
			{
				character.setInt(1, objectId); character.setQueryTimeout(5); items.setInt(1, objectId); items.setQueryTimeout(5);
				final List<String> nativeRow = new ArrayList<>();
				try (var row = character.executeQuery()) { PhantomAssertions.assertTrue(row.next(), "Original imported character row is absent."); for (int column = 1; column <= 17; column++) { nativeRow.add(row.getString(column)); } PhantomAssertions.assertFalse(row.next(), "Duplicate imported native character row."); }
				final List<ItemRow> inventory = new ArrayList<>();
				try (var rows = items.executeQuery()) { while (rows.next()) { inventory.add(new ItemRow(new CanonicalItem(rows.getInt(1), rows.getInt(2), rows.getLong(3), ItemLocation.valueOf(rows.getString(4))), rows.getInt(5), rows.getInt(6))); } }
				return new Image(loaded.state(), components, List.copyOf(nativeRow), List.copyOf(inventory));
			}
			catch (SQLException failure) { throw new IllegalStateException("Guarded historical native facts read failed.", failure); }
		}
	}
	private record Component(String type, int schema, long version, String digest) { }
	private record ItemRow(CanonicalItem item, int slot, int enchant) { }
	private record Image(PhantomBackgroundState state, List<Component> components, List<String> nativeRow, List<ItemRow> items) { }
	private static List<ItemRow> liveItems(Player player)
	{
		return player.getInventory().getItems().stream().filter(item -> item.getCount() > 0 && (item.getItemLocation().name().equals("INVENTORY") || item.getItemLocation().name().equals("PAPERDOLL")))
			.map(item -> new ItemRow(new CanonicalItem(item.getObjectId(), item.getId(), item.getCount(), ItemLocation.valueOf(item.getItemLocation().name())), item.getLocationSlot(), item.getEnchantLevel())).sorted(Comparator.comparingInt(row -> row.item().objectId())).toList();
	}
}
