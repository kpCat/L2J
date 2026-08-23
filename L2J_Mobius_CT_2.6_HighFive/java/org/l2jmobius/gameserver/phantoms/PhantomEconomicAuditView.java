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
 */
package org.l2jmobius.gameserver.phantoms;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceipt;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceiptStore;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceiptStore.VersionedReceipt;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.Reservation;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.State;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyReservationService;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyReservationService.AuditRecord;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyReservationService.StoredOperation;

/**
 * Bounded read-only projection for one Phantom profile's retained economic evidence.
 */
public final class PhantomEconomicAuditView
{
	public static final int RETAINED_LIMIT = 256;
	public static final int RENDER_LIMIT = 8;

	private final EconomyReader _economy;
	private final ReceiptReader _receipts;

	public PhantomEconomicAuditView(PhantomEconomyReservationService economy, PhantomCommerceReceiptStore receipts)
	{
		Objects.requireNonNull(economy);
		Objects.requireNonNull(receipts);
		_economy = new EconomyReader()
		{
			@Override
			public Optional<StoredOperation> findActive(long profileId)
			{
				return economy.findActive(profileId);
			}

			@Override
			public List<Reservation> findReservations(String operationId)
			{
				return economy.findReservations(operationId);
			}

			@Override
			public List<AuditRecord> findAudit(long profileId, int limit)
			{
				return economy.findAudit(profileId, limit);
			}
		};
		_receipts = receipts::find;
	}

	PhantomEconomicAuditView(EconomyReader economy, ReceiptReader receipts)
	{
		_economy = Objects.requireNonNull(economy);
		_receipts = Objects.requireNonNull(receipts);
	}

	public Snapshot read(long profileId)
	{
		if (profileId <= 0)
		{
			throw new IllegalArgumentException("Profile ID must be positive.");
		}
		final CurrentOperation current = readCurrent(profileId);
		final List<AuditRecord> audit = _economy.findAudit(profileId, RETAINED_LIMIT);
		final RetainedSummary summary = summarize(audit);
		final ReceiptView receipt = _receipts.find(profileId).map(VersionedReceipt::receipt).map(PhantomEconomicAuditView::receipt).orElse(null);
		return new Snapshot(profileId, current, summary, audit, receipt);
	}

	private CurrentOperation readCurrent(long profileId)
	{
		final Optional<StoredOperation> initial = _economy.findActive(profileId);
		if (initial.isEmpty())
		{
			return CurrentOperation.none();
		}
		final StoredOperation first = initial.get();
		final int reservationCount = _economy.findReservations(first.operationId()).size();
		final Optional<StoredOperation> confirmed = _economy.findActive(profileId);
		if (confirmed.isEmpty() || !first.equals(confirmed.get()))
		{
			return CurrentOperation.changed();
		}
		return CurrentOperation.available(first, reservationCount);
	}
	private static RetainedSummary summarize(List<AuditRecord> audit)
	{
		if ((audit == null) || (audit.size() > RETAINED_LIMIT))
		{
			throw new IllegalArgumentException("Invalid retained economy audit window.");
		}
		final EnumMap<State, Integer> states = new EnumMap<>(State.class);
		for (State state : State.values())
		{
			if (state.terminal())
			{
				states.put(state, 0);
			}
		}
		final boolean[] saturated = new boolean[1];
		long itemsConsumed = 0;
		long itemsProduced = 0;
		long adenaSource = 0;
		long adenaSink = 0;
		long crystalsProduced = 0;
		long targetItemsDestroyed = 0;
		for (AuditRecord record : audit)
		{
			if (!record.state().terminal())
			{
				throw new IllegalArgumentException("Retained economy audit contains a nonterminal row.");
			}
			states.put(record.state(), states.get(record.state()) + 1);
			itemsConsumed = saturatingAdd(itemsConsumed, record.itemsConsumed(), saturated);
			itemsProduced = saturatingAdd(itemsProduced, record.itemsProduced(), saturated);
			adenaSource = saturatingAdd(adenaSource, record.adenaSource(), saturated);
			adenaSink = saturatingAdd(adenaSink, record.adenaSink(), saturated);
			crystalsProduced = saturatingAdd(crystalsProduced, record.crystalsProduced(), saturated);
			targetItemsDestroyed = saturatingAdd(targetItemsDestroyed, record.targetItemsDestroyed(), saturated);
		}
		return new RetainedSummary(audit.size(), states, itemsConsumed, itemsProduced, adenaSource, adenaSink, crystalsProduced, targetItemsDestroyed, saturated[0]);
	}

	private static long saturatingAdd(long current, long value, boolean[] saturated)
	{
		if ((current < 0) || (value < 0))
		{
			throw new IllegalArgumentException("Economy audit totals must not be negative.");
		}
		if (Long.MAX_VALUE - current < value)
		{
			saturated[0] = true;
			return Long.MAX_VALUE;
		}
		return current + value;
	}

	private static ReceiptView receipt(PhantomCommerceReceipt receipt)
	{
		final PhantomCommerceReceipt.ConservationFacts before = receipt.before();
		final PhantomCommerceReceipt.ConservationFacts after = receipt.expectedAfter();
		return new ReceiptView(
			receipt.operationKey(),
			receipt.goalId(),
			receipt.goalRevision(),
			receipt.request().kind(),
			receipt.state(),
			receipt.resumeCount(),
			delta(before.primaryCount(), after.primaryCount()),
			delta(before.secondaryCount(), after.secondaryCount()),
			delta(before.objectCount(), after.objectCount()),
			(before.instanceId() != after.instanceId()) || (before.x() != after.x()) || (before.y() != after.y()) || (before.z() != after.z()));
	}

	private static CountDelta delta(long before, long expectedAfter)
	{
		return new CountDelta(before, expectedAfter, expectedAfter - before);
	}

	interface EconomyReader
	{
		Optional<StoredOperation> findActive(long profileId);

		List<Reservation> findReservations(String operationId);

		List<AuditRecord> findAudit(long profileId, int limit);
	}

	@FunctionalInterface
	interface ReceiptReader
	{
		Optional<VersionedReceipt> find(long profileId);
	}
	public enum CurrentStatus
	{
		AVAILABLE,
		NONE,
		CHANGED
	}

	public record CurrentOperation(CurrentStatus status, String operationId, long goalId, long goalRevision, PhantomEconomyOperation.Kind kind, State state, int attempt, int reservationCount)
	{
		public CurrentOperation
		{
			Objects.requireNonNull(status);
			if (status == CurrentStatus.AVAILABLE)
			{
				if ((operationId == null) || (kind == null) || (state == null) || state.terminal() || (attempt < 1) || (reservationCount < 0))
				{
					throw new IllegalArgumentException("Invalid current economy operation.");
				}
			}
			else if ((operationId != null) || (kind != null) || (state != null) || (goalId != 0) || (goalRevision != 0) || (attempt != 0) || (reservationCount != 0))
			{
				throw new IllegalArgumentException("Unavailable current economy operation exposes details.");
			}
		}

		private static CurrentOperation available(StoredOperation operation, int reservationCount)
		{
			return new CurrentOperation(CurrentStatus.AVAILABLE, operation.operationId(), operation.goalId(), operation.goalRevision(), operation.kind(), operation.state(), operation.attempt(), reservationCount);
		}

		private static CurrentOperation none()
		{
			return new CurrentOperation(CurrentStatus.NONE, null, 0, 0, null, null, 0, 0);
		}

		private static CurrentOperation changed()
		{
			return new CurrentOperation(CurrentStatus.CHANGED, null, 0, 0, null, null, 0, 0);
		}
	}

	public record RetainedSummary(int retainedRows, Map<State, Integer> stateCounts, long itemsConsumed, long itemsProduced, long adenaSource, long adenaSink, long crystalsProduced, long targetItemsDestroyed, boolean totalsSaturated)
	{
		public RetainedSummary
		{
			if ((retainedRows < 0) || (retainedRows > RETAINED_LIMIT) || (stateCounts == null) || (itemsConsumed < 0) || (itemsProduced < 0) || (adenaSource < 0) || (adenaSink < 0) || (crystalsProduced < 0) || (targetItemsDestroyed < 0))
			{
				throw new IllegalArgumentException("Invalid retained economy summary.");
			}
			final EnumMap<State, Integer> copy = new EnumMap<>(State.class);
			copy.putAll(stateCounts);
			stateCounts = Collections.unmodifiableMap(copy);
		}
	}
	public record CountDelta(long before, long expectedAfter, long delta)
	{
		public CountDelta
		{
			if ((before < 0) || (expectedAfter < 0) || (delta != (expectedAfter - before)))
			{
				throw new IllegalArgumentException("Invalid commerce count delta.");
			}
		}
	}

	public record ReceiptView(String operationKey, long goalId, long goalRevision, PhantomCommerceReceipt.OperationKind kind, PhantomCommerceReceipt.State state, int resumeCount, CountDelta primary, CountDelta secondary, CountDelta object, boolean positionChanged)
	{
		public ReceiptView
		{
			if ((operationKey == null) || !operationKey.matches("[0-9a-f]{64}") || (goalId <= 0) || (goalRevision < 0) || (kind == null) || (state == null) || (resumeCount < 0) || (resumeCount > 1))
			{
				throw new IllegalArgumentException("Invalid commerce receipt view.");
			}
			Objects.requireNonNull(primary);
			Objects.requireNonNull(secondary);
			Objects.requireNonNull(object);
		}
	}

	public record Snapshot(long profileId, CurrentOperation current, RetainedSummary retainedSummary, List<AuditRecord> newestAudit, ReceiptView latestReceipt)
	{
		public Snapshot
		{
			if (profileId <= 0)
			{
				throw new IllegalArgumentException("Profile ID must be positive.");
			}
			Objects.requireNonNull(current);
			Objects.requireNonNull(retainedSummary);
			newestAudit = List.copyOf(newestAudit);
			if ((newestAudit.size() > RETAINED_LIMIT) || (newestAudit.size() != retainedSummary.retainedRows()))
			{
				throw new IllegalArgumentException("Invalid retained economy audit snapshot.");
			}
		}

		public boolean empty()
		{
			return (current.status() == CurrentStatus.NONE) && newestAudit.isEmpty() && (latestReceipt == null);
		}
	}
}