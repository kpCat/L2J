/*
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.l2jmobius.gameserver.phantoms.commerce;

import java.util.ConcurrentModificationException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BooleanSupplier;

import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceipt.ConservationFacts;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceipt.OperationKind;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceipt.OperationRequest;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceipt.Reconciliation;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceipt.State;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceiptStore.VersionedReceipt;

/**
 * No-worker lifecycle and conservative receipt coordinator.
 */
public final class PhantomCommerceService
{
	private static final int LOCK_STRIPES = 64;

	private final PhantomCommerceCatalogLoader.LoadResult _catalogResult;
	private final ReceiptPersistence _receiptStore;
	private final Backend _backend;
	private final Object[] _profileLocks = new Object[LOCK_STRIPES];
	private final LongAdder _successes = new LongAdder();
	private final LongAdder _idempotent = new LongAdder();
	private final LongAdder _retries = new LongAdder();
	private final LongAdder _replans = new LongAdder();
	private final LongAdder _inconsistent = new LongAdder();
	private StateSnapshot _state = StateSnapshot.NEW;

	public PhantomCommerceService(PhantomCommerceCatalogLoader.LoadResult catalogResult, ReceiptPersistence receiptStore, Backend backend)
	{
		_catalogResult = Objects.requireNonNull(catalogResult);
		_receiptStore = Objects.requireNonNull(receiptStore);
		_backend = Objects.requireNonNull(backend);
		for (int index = 0; index < _profileLocks.length; index++)
		{
			_profileLocks[index] = new Object();
		}
	}

	public synchronized boolean start()
	{
		if (_state != StateSnapshot.NEW)
		{
			return false;
		}
		_state = StateSnapshot.RUNNING;
		return true;
	}

	public synchronized boolean beginStop()
	{
		if (_state != StateSnapshot.RUNNING)
		{
			return false;
		}
		_state = StateSnapshot.STOPPING;
		return true;
	}

	public synchronized boolean finishStop()
	{
		if ((_state != StateSnapshot.STOPPING) && (_state != StateSnapshot.NEW))
		{
			return false;
		}
		_state = StateSnapshot.STOPPED;
		return true;
	}

	public PhantomCommerceCatalog catalog()
	{
		return _catalogResult.catalog();
	}

	public PhantomCommerceCatalogLoader.CommerceFixtures fixtures()
	{
		return _catalogResult.fixtures();
	}

	public OperationResult execute(long profileId, long goalId, long goalRevision, OperationIntent intent, BooleanSupplier cancelled)
	{
		Objects.requireNonNull(intent);
		Objects.requireNonNull(cancelled);
		if (state() != StateSnapshot.RUNNING)
		{
			return record(OperationResult.replan(Reason.SERVICE_NOT_RUNNING));
		}
		if (cancelled.getAsBoolean())
		{
			return OperationResult.cancelled();
		}
		synchronized (_profileLocks[Math.floorMod(Long.hashCode(profileId), LOCK_STRIPES)])
		{
			if (state() != StateSnapshot.RUNNING)
			{
				return record(OperationResult.replan(Reason.SERVICE_NOT_RUNNING));
			}
			try (ActorLease actor = _backend.tryAcquire(profileId).orElse(null))
			{
				if (actor == null)
				{
					return record(OperationResult.retry(Reason.ACTOR_NOT_MATERIALIZED));
				}
				final Optional<VersionedReceipt> stored = _receiptStore.find(profileId);
				if (stored.isPresent())
				{
					final VersionedReceipt versioned = stored.get();
					final PhantomCommerceReceipt receipt = versioned.receipt();
					if (matches(receipt, goalId, goalRevision, intent))
					{
						return record(reconcile(actor, versioned, cancelled));
					}
					if (!receipt.state().terminal())
					{
						return record(OperationResult.retry(Reason.OPERATION_BUSY));
					}
					if (receipt.state() == State.INCONSISTENT)
					{
						return record(OperationResult.inconsistent(Reason.PROFILE_FAIL_STOP));
					}
				}

				if (cancelled.getAsBoolean())
				{
					return OperationResult.cancelled();
				}
				final Quote quote = actor.quote(intent);
				if (!quote.accepted())
				{
					return record(OperationResult.replan(quote.reason()));
				}
				final PhantomCommerceReceipt prepared = PhantomCommerceReceipt.prepared(profileId, goalId, goalRevision, quote.request(), quote.before(), quote.expectedAfter());
				final long expectedVersion = stored.map(VersionedReceipt::rowVersion).orElse(PhantomCommerceReceiptStore.ABSENT_ROW_VERSION);
				VersionedReceipt durable = _receiptStore.save(expectedVersion, prepared);
				if (cancelled.getAsBoolean())
				{
					durable = _receiptStore.save(durable.rowVersion(), durable.receipt().withState(State.ABORTED));
					return OperationResult.cancelled();
				}
				durable = _receiptStore.save(durable.rowVersion(), durable.receipt().withState(State.COMMITTING));
				return record(applyFromBefore(actor, durable, cancelled));
			}
			catch (ConcurrentModificationException e)
			{
				return record(OperationResult.retry(Reason.RECEIPT_RACE));
			}
			catch (RuntimeException e)
			{
				return record(OperationResult.retry(Reason.BACKEND_FAILURE));
			}
		}
	}

	private OperationResult reconcile(ActorLease actor, VersionedReceipt versioned, BooleanSupplier cancelled)
	{
		PhantomCommerceReceipt receipt = versioned.receipt();
		final ConservationFacts current = actor.snapshot(receipt.request());
		final Reconciliation reconciliation = receipt.reconcile(current);
		if (reconciliation == Reconciliation.EXACT_AFTER)
		{
			if (receipt.state() != State.COMMITTED)
			{
				if (receipt.state() != State.COMMITTING)
				{
					return markInconsistent(versioned, Reason.INVALID_RECEIPT_STATE);
				}
				_receiptStore.save(versioned.rowVersion(), receipt.withState(State.COMMITTED));
			}
			return OperationResult.idempotent();
		}
		if ((receipt.state() == State.COMMITTED) || (receipt.state() == State.ABORTED) || (receipt.state() == State.INCONSISTENT) || (reconciliation == Reconciliation.INCONSISTENT))
		{
			return markInconsistent(versioned, Reason.AMBIGUOUS_DELTA);
		}
		if (cancelled.getAsBoolean())
		{
			return OperationResult.cancelled();
		}
		if (reconciliation == Reconciliation.FIRST_EFFECT_ONLY)
		{
			if (receipt.state() != State.COMMITTING)
			{
				return markInconsistent(versioned, Reason.INVALID_RECEIPT_STATE);
			}
			return applyMissingSecond(actor, versioned);
		}
		if (receipt.state() == State.PREPARED)
		{
			versioned = _receiptStore.save(versioned.rowVersion(), receipt.withState(State.COMMITTING));
			return applyFromBefore(actor, versioned, cancelled);
		}
		if (receipt.resumeCount() != 0)
		{
			return markInconsistent(versioned, Reason.RESUME_EXHAUSTED);
		}
		versioned = _receiptStore.save(versioned.rowVersion(), receipt.resumed());
		return applyFromBefore(actor, versioned, cancelled);
	}

	private OperationResult applyFromBefore(ActorLease actor, VersionedReceipt versioned, BooleanSupplier cancelled)
	{
		final PhantomCommerceReceipt receipt = versioned.receipt();
		if (!actor.snapshot(receipt.request()).equals(receipt.before()))
		{
			return markInconsistent(versioned, Reason.AMBIGUOUS_DELTA);
		}
		if (cancelled.getAsBoolean())
		{
			return OperationResult.cancelled();
		}
		if (!actor.applyFirst(receipt.request()))
		{
			return OperationResult.retry(Reason.FIRST_EFFECT_NOT_CONFIRMED);
		}
		final Reconciliation afterFirst = receipt.reconcile(actor.snapshot(receipt.request()));
		if (afterFirst == Reconciliation.EXACT_AFTER)
		{
			_receiptStore.save(versioned.rowVersion(), receipt.withState(State.COMMITTED));
			return OperationResult.success();
		}
		if (afterFirst != Reconciliation.FIRST_EFFECT_ONLY)
		{
			return markInconsistent(versioned, Reason.AMBIGUOUS_DELTA);
		}
		return applyMissingSecond(actor, versioned);
	}

	private OperationResult applyMissingSecond(ActorLease actor, VersionedReceipt versioned)
	{
		final PhantomCommerceReceipt receipt = versioned.receipt();
		if (!actor.applySecond(receipt.request()))
		{
			return OperationResult.retry(Reason.SECOND_EFFECT_NOT_CONFIRMED);
		}
		final Reconciliation after = receipt.reconcile(actor.snapshot(receipt.request()));
		if (after == Reconciliation.EXACT_AFTER)
		{
			_receiptStore.save(versioned.rowVersion(), receipt.withState(State.COMMITTED));
			return OperationResult.success();
		}
		if ((receipt.request().kind() == OperationKind.TELEPORT) && (after == Reconciliation.FIRST_EFFECT_ONLY))
		{
			return OperationResult.retry(Reason.TELEPORT_PENDING);
		}
		return markInconsistent(versioned, Reason.AMBIGUOUS_DELTA);
	}

	private OperationResult markInconsistent(VersionedReceipt versioned, Reason reason)
	{
		final PhantomCommerceReceipt receipt = versioned.receipt();
		if ((receipt.state() == State.PREPARED) || (receipt.state() == State.COMMITTING) || (receipt.state() == State.COMMITTED))
		{
			_receiptStore.save(versioned.rowVersion(), receipt.withState(State.INCONSISTENT));
		}
		return OperationResult.inconsistent(reason);
	}

	private static boolean matches(PhantomCommerceReceipt receipt, long goalId, long goalRevision, OperationIntent intent)
	{
		final OperationRequest request = receipt.request();
		return (receipt.goalId() == goalId) //
			&& (receipt.goalRevision() == goalRevision) //
			&& (request.kind() == intent.kind()) //
			&& (request.npcTemplateId() == intent.npcTemplateId()) //
			&& (request.npcObjectId() == intent.npcObjectId()) //
			&& (request.listId() == intent.listId()) //
			&& (request.itemId() == intent.itemId()) //
			&& (request.itemObjectId() == intent.itemObjectId()) //
			&& (request.count() == intent.count()) //
			&& (request.ordinal() == intent.ordinal()) //
			&& request.listName().equals(intent.listName());
	}

	private synchronized StateSnapshot state()
	{
		return _state;
	}

	private OperationResult record(OperationResult result)
	{
		switch (result.status())
		{
			case SUCCESS -> _successes.increment();
			case IDEMPOTENT -> _idempotent.increment();
			case RETRY -> _retries.increment();
			case REPLAN -> _replans.increment();
			case INCONSISTENT -> _inconsistent.increment();
			case CANCELLED ->
			{
			}
		}
		return result;
	}

	public synchronized Snapshot snapshot()
	{
		return new Snapshot(_state, _successes.sum(), _idempotent.sum(), _retries.sum(), _replans.sum(), _inconsistent.sum(), 0);
	}

	public interface Backend
	{
		Optional<ActorLease> tryAcquire(long profileId);
	}

	public interface ReceiptPersistence
	{
		Optional<VersionedReceipt> find(long profileId);

		VersionedReceipt save(long expectedRowVersion, PhantomCommerceReceipt receipt);
	}

	public interface ActorLease extends AutoCloseable
	{
		Quote quote(OperationIntent intent);

		ConservationFacts snapshot(OperationRequest request);

		boolean applyFirst(OperationRequest request);

		boolean applySecond(OperationRequest request);

		@Override
		void close();
	}

	public record OperationIntent(OperationKind kind, int npcTemplateId, int npcObjectId, int listId, int itemId, int itemObjectId, long count, int ordinal, String listName, long expenseBudget)
	{
		public OperationIntent
		{
			Objects.requireNonNull(kind);
			if ((npcTemplateId <= 0) || (npcObjectId <= 0) || (listId < 0) || (itemId < 0) || (itemObjectId < 0) || (count < 0) || (ordinal < 0) || (expenseBudget < 0))
			{
				throw new IllegalArgumentException("Commerce intent numeric facts are invalid.");
			}
			listName = listName == null ? "" : listName;
		}
	}

	public record ActorFacts(long adena, long requestedItemCount, long requestedObjectCount, int currentLoad, int maximumLoad, int classIndex, boolean noble, int karma, boolean dead, boolean inCombat, boolean casting, boolean moving, boolean teleporting, int instanceId, int x, int y, int z, int targetObjectId, int lastFolkObjectId)
	{
	}

	public record Quote(OperationRequest request, ConservationFacts before, ConservationFacts expectedAfter, ActorFacts actorFacts, Reason reason)
	{
		public Quote
		{
			Objects.requireNonNull(reason);
			if (reason == Reason.ACCEPTED)
			{
				Objects.requireNonNull(request);
				Objects.requireNonNull(before);
				Objects.requireNonNull(expectedAfter);
				Objects.requireNonNull(actorFacts);
			}
		}

		public static Quote accepted(OperationRequest request, ConservationFacts before, ConservationFacts expectedAfter, ActorFacts actorFacts)
		{
			return new Quote(request, before, expectedAfter, actorFacts, Reason.ACCEPTED);
		}

		public static Quote rejected(Reason reason)
		{
			return new Quote(null, null, null, null, reason);
		}

		public boolean accepted()
		{
			return reason == Reason.ACCEPTED;
		}
	}

	public enum Reason
	{
		ACCEPTED,
		SERVICE_NOT_RUNNING,
		ACTOR_NOT_MATERIALIZED,
		CANCELLED,
		OPERATION_BUSY,
		PROFILE_FAIL_STOP,
		RECEIPT_RACE,
		BACKEND_FAILURE,
		INVALID_RECEIPT_STATE,
		AMBIGUOUS_DELTA,
		RESUME_EXHAUSTED,
		FIRST_EFFECT_NOT_CONFIRMED,
		SECOND_EFFECT_NOT_CONFIRMED,
		TELEPORT_PENDING,
		INVALID_REQUEST,
		INVALID_ACTOR_STATE,
		NPC_NOT_FOUND,
		NPC_TYPE_MISMATCH,
		NPC_IDENTITY_MISMATCH,
		NPC_OUT_OF_RANGE,
		INSTANCE_MISMATCH,
		OFFER_NOT_FOUND,
		LIMITED_STOCK_UNSUPPORTED,
		PRICE_CHANGED,
		BUDGET_EXCEEDED,
		INSUFFICIENT_FUNDS,
		WEIGHT_LIMIT,
		CAPACITY_LIMIT,
		CASTLE_TREASURY_UNSUPPORTED,
		ITEM_NOT_OWNED,
		ITEM_NOT_SELLABLE,
		REFUND_UNSUPPORTED,
		ZERO_SELL_PRICE_UNSUPPORTED,
		TELEPORT_NOT_FOUND,
		TELEPORT_TYPE_UNSUPPORTED,
		TELEPORT_RESTRICTED,
		TELEPORT_FEE_UNAVAILABLE
	}

	public enum OperationStatus
	{
		SUCCESS,
		IDEMPOTENT,
		RETRY,
		REPLAN,
		INCONSISTENT,
		CANCELLED
	}

	public record OperationResult(OperationStatus status, Reason reason)
	{
		public OperationResult
		{
			Objects.requireNonNull(status);
			Objects.requireNonNull(reason);
		}

		public static OperationResult success()
		{
			return new OperationResult(OperationStatus.SUCCESS, Reason.ACCEPTED);
		}

		public static OperationResult idempotent()
		{
			return new OperationResult(OperationStatus.IDEMPOTENT, Reason.ACCEPTED);
		}

		public static OperationResult retry(Reason reason)
		{
			return new OperationResult(OperationStatus.RETRY, reason);
		}

		public static OperationResult replan(Reason reason)
		{
			return new OperationResult(OperationStatus.REPLAN, reason);
		}

		public static OperationResult inconsistent(Reason reason)
		{
			return new OperationResult(OperationStatus.INCONSISTENT, reason);
		}

		public static OperationResult cancelled()
		{
			return new OperationResult(OperationStatus.CANCELLED, Reason.CANCELLED);
		}
	}

	public enum StateSnapshot
	{
		NEW,
		RUNNING,
		STOPPING,
		STOPPED
	}

	public record Snapshot(StateSnapshot state, long successes, long idempotent, long retries, long replans, long inconsistent, int workers)
	{
	}
}
