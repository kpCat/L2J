/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.progression;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionBackend.ActorLease;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.ActorProgressionSnapshot;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.ActorSnapshotResult;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.CapabilityEvaluation;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.EquipItemRequest;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.LearnSkillRequest;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.OperationResult;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.OperationStatus;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.OwnedEquipmentFact;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.OwnedEquipmentFilter;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.Page;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.PageRequest;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.ProfessionStatus;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.ProfessionTarget;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SnapshotStatus;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SubclassEligibility;

/**
 * Lifecycle, bounded actor leases and serialized progression mutations.
 */
public final class PhantomProgressionService
{
	private static final int MAX_OBSERVED_PROFILES = 10_000;
	private final PhantomProgressionBackend _backend;
	private final PhantomProgressionPolicy _policy;
	private final PhantomProgressionCatalogBuilder _builder;
	private final PhantomProgressionCapabilityEvaluator _evaluator;
	private final PhantomProgressionMetrics _metrics;
	private final Map<Long, OperationSlot> _operations = new LinkedHashMap<>();
	private final LinkedHashMap<Long, Integer> _observedClasses = new LinkedHashMap<>();
	private State _state = State.NEW;
	private PhantomProgressionCatalog _catalog;
	private String _failureCategory = "";
	private long _generation;
	private int _activeActorLeases;
	private int _peakActorLeases;
	private int _peakOperations;

	public PhantomProgressionService(PhantomProgressionBackend backend, PhantomProgressionPolicy policy)
	{
		this(backend, policy, new PhantomProgressionCatalogBuilder(), new PhantomProgressionCapabilityEvaluator(), new PhantomProgressionMetrics());
	}

	PhantomProgressionService(PhantomProgressionBackend backend, PhantomProgressionPolicy policy, PhantomProgressionCatalogBuilder builder, PhantomProgressionCapabilityEvaluator evaluator, PhantomProgressionMetrics metrics)
	{
		_backend = Objects.requireNonNull(backend);
		_policy = Objects.requireNonNull(policy);
		_builder = Objects.requireNonNull(builder);
		_evaluator = Objects.requireNonNull(evaluator);
		_metrics = Objects.requireNonNull(metrics);
	}

	public void start()
	{
		synchronized (this)
		{
			if (_state != State.NEW)
			{
				throw new IllegalStateException("Progression service can only start once.");
			}
			_state = State.BUILDING;
		}
		try
		{
			final PhantomProgressionCatalog catalog = _builder.build(_backend.load(_policy), _policy);
			synchronized (this)
			{
				if (_state != State.BUILDING)
				{
					throw new IllegalStateException("Progression service stopped while building.");
				}
				_catalog = catalog;
				_generation++;
				_state = State.RUNNING;
				_metrics.recordCatalogBuild(catalog.counts());
			}
		}
		catch (RuntimeException e)
		{
			synchronized (this)
			{
				_state = State.FAILED;
				_failureCategory = e.getClass().getSimpleName();
				_metrics.recordCatalogFailure();
			}
			throw e;
		}
	}

	public Observation observeActor(long profileId)
	{
		final LeaseClaim claim = claimActor(profileId);
		if (claim == null)
		{
			return new Observation(new ActorSnapshotResult(running() ? SnapshotStatus.ACTOR_NOT_MATERIALIZED : SnapshotStatus.SERVICE_NOT_RUNNING, null), null, false);
		}
		try (claim)
		{
			final ActorProgressionSnapshot actor = claim.lease().snapshot(claim.catalog().combinedHash(), claim.catalog().referencedResourceItemIds(), claim.catalog().certificationSkillIds());
			final Integer previous;
			synchronized (this)
			{
				previous = _observedClasses.put(profileId, actor.activeClassId());
				if ((_observedClasses.size() > MAX_OBSERVED_PROFILES) && !_observedClasses.isEmpty())
				{
					_observedClasses.remove(_observedClasses.keySet().iterator().next());
				}
			}
			_metrics.recordActorSnapshot(true);
			return new Observation(new ActorSnapshotResult(SnapshotStatus.FOUND, actor), previous, (previous != null) && (previous != actor.activeClassId()));
		}
		catch (RuntimeException e)
		{
			_metrics.recordActorSnapshot(false);
			return new Observation(new ActorSnapshotResult(SnapshotStatus.BACKEND_FAILURE, null), null, false);
		}
	}

	public List<CapabilityEvaluation> capabilities(long profileId, Integer targetObjectId)
	{
		final LeaseClaim claim = claimActor(profileId);
		if (claim == null)
		{
			_metrics.recordQuery(true);
			return List.of();
		}
		try (claim)
		{
			final ActorProgressionSnapshot actor = claim.lease().snapshot(claim.catalog().combinedHash(), claim.catalog().referencedResourceItemIds(), claim.catalog().certificationSkillIds());
			final List<CapabilityEvaluation> result = _evaluator.evaluate(claim.catalog(), actor, claim.lease(), targetObjectId);
			_metrics.recordQuery(result.isEmpty());
			_metrics.recordCapabilityEvaluations(result.size());
			return result;
		}
		catch (RuntimeException e)
		{
			_metrics.recordQuery(true);
			return List.of();
		}
	}

	public Page<OwnedEquipmentFact> equipmentCandidates(long profileId, OwnedEquipmentFilter filter, PageRequest page)
	{
		if (page.limit() > _policy.maximumOwnedEquipmentPageSize())
		{
			throw new IllegalArgumentException("Owned equipment page exceeds progression policy.");
		}
		final LeaseClaim claim = claimActor(profileId);
		if (claim == null)
		{
			_metrics.recordQuery(true);
			return new Page<>(List.of(), null, false);
		}
		try (claim)
		{
			final Page<OwnedEquipmentFact> result = claim.lease().ownedEquipment(filter, page);
			_metrics.recordQuery(result.values().isEmpty());
			return result;
		}
		catch (RuntimeException e)
		{
			_metrics.recordQuery(true);
			return new Page<>(List.of(), null, false);
		}
	}

	public List<ProfessionTarget> professionTargets(long profileId)
	{
		final Observation observation = observeActor(profileId);
		if (observation.result().status() != SnapshotStatus.FOUND)
		{
			return List.of();
		}
		final ActorProgressionSnapshot actor = observation.result().snapshot();
		final PhantomProgressionCatalog catalog = catalog();
		final var current = catalog.classFact(actor.activeClassId());
		if (current == null)
		{
			return List.of();
		}
		final List<ProfessionTarget> result = new ArrayList<>();
		for (int targetId : current.nextClassIds())
		{
			final var target = catalog.classFact(targetId);
			final int requiredLevel = minimumProfessionLevel(target.tier());
			final ProfessionStatus status = actor.level() < requiredLevel ? ProfessionStatus.LEVEL_PENDING : ProfessionStatus.CANONICAL_QUEST_REQUIRED;
			result.add(new ProfessionTarget(current.classId(), targetId, target.tier(), requiredLevel, true, true, status));
		}
		_metrics.recordCanonicalQuestRequired(result.size());
		return List.copyOf(result);
	}

	public List<SubclassEligibility> subclassEligibility(long profileId)
	{
		final LeaseClaim claim = claimActor(profileId);
		if (claim == null)
		{
			return List.of();
		}
		try (claim)
		{
			return List.copyOf(claim.lease().subclassEligibility(claim.catalog().classes(org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.PageRequest.first(256)).values()));
		}
	}

	public OperationResult learnClassSkill(LearnSkillRequest request)
	{
		return executeOperation(request.profileId(), request.planOwnershipToken(), true, ownership ->
		{
			final LeaseClaim claim = claimActor(request.profileId());
			if (claim == null)
			{
				return OperationResult.rejected(OperationStatus.ACTOR_NOT_MATERIALIZED);
			}
			try (claim)
			{
				return claim.lease().learnClassSkill(request, ownership);
			}
		});
	}

	public OperationResult equipOwnedItem(EquipItemRequest request)
	{
		return executeOperation(request.profileId(), request.planOwnershipToken(), false, ownership ->
		{
			final LeaseClaim claim = claimActor(request.profileId());
			if (claim == null)
			{
				return OperationResult.rejected(OperationStatus.ACTOR_NOT_MATERIALIZED);
			}
			try (claim)
			{
				return claim.lease().equipOwnedItem(request, ownership);
			}
		});
	}

	private OperationResult executeOperation(long profileId, org.l2jmobius.gameserver.phantoms.decision.PhantomCancellationToken token, boolean learn, java.util.function.Function<java.util.function.BooleanSupplier, OperationResult> action)
	{
		_metrics.recordOperationRequested(learn);
		final OperationSlot slot;
		synchronized (this)
		{
			if (_state != State.RUNNING)
			{
				_metrics.recordOperationRejected(learn);
				return OperationResult.rejected(OperationStatus.SERVICE_NOT_RUNNING);
			}
			if (token.isCancelled())
			{
				_metrics.recordCancellation();
				_metrics.recordOperationRejected(learn);
				return OperationResult.rejected(OperationStatus.CANCELLED);
			}
			slot = new OperationSlot(++_generation, token);
			if (_operations.putIfAbsent(profileId, slot) != null)
			{
				_metrics.recordOperationRejected(learn);
				return OperationResult.rejected(OperationStatus.OPERATION_IN_PROGRESS);
			}
			_peakOperations = Math.max(_peakOperations, _operations.size());
			_metrics.recordOperationAccepted();
		}
		final java.util.function.BooleanSupplier ownership = () ->
		{
			synchronized (PhantomProgressionService.this)
			{
				return (_state == State.RUNNING) && (_operations.get(profileId) == slot) && (slot.token() == token) && !token.isCancelled();
			}
		};
		try
		{
			final OperationResult result = action.apply(ownership);
			if (result.status() == OperationStatus.DURABLE_COMMIT_RUNTIME_RECONCILIATION_FAILED)
			{
				synchronized (this)
				{
					if (_state == State.RUNNING)
					{
						_state = State.FAILED;
						_failureCategory = result.status().name();
					}
				}
			}
			recordOperationResult(learn, result);
			return result;
		}
		catch (RuntimeException e)
		{
			_metrics.recordOperationFailure(learn);
			_metrics.recordOperationRejected(learn);
			return OperationResult.rejected(OperationStatus.BACKEND_FAILURE);
		}
		finally
		{
			synchronized (this)
			{
				_operations.remove(profileId, slot);
			}
		}
	}

	private void recordOperationResult(boolean learn, OperationResult result)
	{
		if (result.status() == OperationStatus.SUCCESS)
		{
			if (learn)
			{
				final long items = result.itemCountsBefore().entrySet().stream().mapToLong(entry -> Math.max(0, entry.getValue() - result.itemCountsAfter().getOrDefault(entry.getKey(), 0L))).sum();
				_metrics.recordLearnResult(false, result.spBefore() - result.spAfter(), items);
			}
			else
			{
				_metrics.recordEquipResult(false);
			}
		}
		else if (result.status() == OperationStatus.IDEMPOTENT)
		{
			if (learn)
			{
				_metrics.recordLearnResult(true, 0, 0);
			}
			else
			{
				_metrics.recordEquipResult(true);
			}
		}
		else
		{
			if (result.status() == OperationStatus.CANCELLED)
			{
				_metrics.recordCancellation();
			}
			if ((result.status() == OperationStatus.BACKEND_FAILURE) || (result.status() == OperationStatus.RECONCILIATION_FAILED) || (result.status() == OperationStatus.DURABLE_SKILL_STATE_CONFLICT) || (result.status() == OperationStatus.DURABLE_SP_STATE_CONFLICT) || (result.status() == OperationStatus.DURABLE_ITEM_STATE_CONFLICT) || (result.status() == OperationStatus.DURABLE_SCHEMA_OR_ROW_MISSING) || (result.status() == OperationStatus.DURABLE_COMMIT_RUNTIME_RECONCILIATION_FAILED) || (result.status() == OperationStatus.BLOCKED_CANONICAL_SKILL_LEARNING) || (result.status() == OperationStatus.BLOCKED_CANONICAL_EQUIP_FACADE))
			{
				_metrics.recordOperationFailure(learn);
			}
			_metrics.recordOperationRejected(learn);
		}
	}

	public synchronized PhantomProgressionCatalog catalog()
	{
		if ((_state != State.RUNNING) && (_state != State.STOPPING))
		{
			throw new IllegalStateException("Progression catalog is unavailable.");
		}
		return _catalog;
	}

	public synchronized Optional<PhantomProgressionCatalog> findCatalog()
	{
		return Optional.ofNullable(_catalog);
	}

	public synchronized void beginStop()
	{
		if (_state == State.RUNNING)
		{
			_state = State.STOPPING;
		}
		else if ((_state == State.NEW) || (_state == State.FAILED))
		{
			_state = State.STOPPED;
		}
	}

	public synchronized boolean finishStop()
	{
		if (_state == State.STOPPED)
		{
			return true;
		}
		if ((_state != State.STOPPING) || !_operations.isEmpty() || (_activeActorLeases != 0))
		{
			_metrics.recordShutdownFailure();
			return false;
		}
		_state = State.STOPPED;
		_observedClasses.clear();
		return true;
	}

	public synchronized ServiceSnapshot snapshot()
	{
		final PhantomProgressionCatalog catalog = _catalog;
		return new ServiceSnapshot(_state, _generation, catalog == null ? "none" : catalog.combinedHash(), catalog == null ? new PhantomProgressionCatalog.Counts(0, 0, 0, 0, 0, 0, 0) : catalog.counts(), _operations.size(), _peakOperations, _activeActorLeases, _peakActorLeases, _failureCategory, _metrics.snapshot());
	}

	private LeaseClaim claimActor(long profileId)
	{
		final PhantomProgressionCatalog catalog;
		synchronized (this)
		{
			if (_state != State.RUNNING)
			{
				_metrics.recordLeaseRejected();
				return null;
			}
			catalog = _catalog;
		}
		final Optional<ActorLease> lease = _backend.tryAcquireActor(profileId);
		if (lease.isEmpty())
		{
			_metrics.recordLeaseRejected();
			_metrics.recordActorSnapshot(false);
			return null;
		}
		synchronized (this)
		{
			if (_state != State.RUNNING)
			{
				lease.orElseThrow().close();
				_metrics.recordLeaseRejected();
				return null;
			}
			_activeActorLeases++;
			_peakActorLeases = Math.max(_peakActorLeases, _activeActorLeases);
			_metrics.recordLeaseAcquired();
		}
		return new LeaseClaim(lease.orElseThrow(), catalog);
	}

	private synchronized boolean running()
	{
		return _state == State.RUNNING;
	}

	private static int minimumProfessionLevel(int tier)
	{
		return switch (tier)
		{
			case 0 -> 1;
			case 1 -> 20;
			case 2 -> 40;
			default -> 76;
		};
	}

	private record OperationSlot(long generation, org.l2jmobius.gameserver.phantoms.decision.PhantomCancellationToken token)
	{
	}

	private final class LeaseClaim implements AutoCloseable
	{
		private final ActorLease _lease;
		private final PhantomProgressionCatalog _catalog;
		private boolean _closed;

		private LeaseClaim(ActorLease lease, PhantomProgressionCatalog catalog)
		{
			_lease = lease;
			_catalog = catalog;
		}

		private ActorLease lease()
		{
			return _lease;
		}

		private PhantomProgressionCatalog catalog()
		{
			return _catalog;
		}

		@Override
		public void close()
		{
			if (!_closed)
			{
				_closed = true;
				try
				{
					_lease.close();
				}
				finally
				{
					synchronized (PhantomProgressionService.this)
					{
						_activeActorLeases--;
						_metrics.recordLeaseReleased();
					}
				}
			}
		}
	}

	public enum State
	{
		NEW,
		BUILDING,
		RUNNING,
		STOPPING,
		STOPPED,
		FAILED
	}

	public record Observation(ActorSnapshotResult result, Integer previousActiveClassId, boolean professionTransitionObserved)
	{
		public Observation
		{
			Objects.requireNonNull(result);
		}
	}

	public record ServiceSnapshot(State state, long generation, String combinedHash, PhantomProgressionCatalog.Counts counts, int currentOperations, int peakOperations, int currentActorLeases, int peakActorLeases, String failureCategory, PhantomProgressionMetrics.Snapshot metrics)
	{
		public static ServiceSnapshot inactive()
		{
			return new ServiceSnapshot(State.STOPPED, 0, "none", new PhantomProgressionCatalog.Counts(0, 0, 0, 0, 0, 0, 0), 0, 0, 0, 0, "", new PhantomProgressionMetrics.Snapshot(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
		}
	}
}
