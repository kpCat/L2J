/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.farming;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionService.ConflictSnapshot;

/** Default-ALLOW bridge at Goal 021 safe resource boundaries. */
public final class PhantomFarmingConflictPort
{
	public enum Outcome
	{
		ALLOW,
		SHARE,
		NEGOTIATE,
		WAIT,
		MOVE,
		STALE
	}

	public record Gate(Outcome outcome, String reasonKey, String agreementId)
	{
		public Gate
		{
			Objects.requireNonNull(outcome);
			reasonKey = Objects.requireNonNull(reasonKey);
			agreementId = Objects.requireNonNullElse(agreementId, "");
		}

		public static Gate allow(String reasonKey)
		{
			return new Gate(Outcome.ALLOW, reasonKey, "");
		}
	}

	public interface Evaluator
	{
		Gate evaluate(long profileId, ConflictSnapshot snapshot);
	}

	private static final Evaluator EMPTY = (profileId, snapshot) -> Gate.allow("farming.conflict.uninstalled");
	private static final AtomicReference<Evaluator> PORT = new AtomicReference<>(EMPTY);

	private PhantomFarmingConflictPort()
	{
	}

	public static void install(Evaluator evaluator)
	{
		if (!PORT.compareAndSet(EMPTY, Objects.requireNonNull(evaluator)))
		{
			throw new IllegalStateException("Farming conflict port is already installed.");
		}
	}

	public static Gate evaluate(long profileId, ConflictSnapshot snapshot)
	{
		return PORT.get().evaluate(profileId, snapshot);
	}

	public static boolean isInstalled()
	{
		return PORT.get() != EMPTY;
	}

	public static void uninstall(Evaluator evaluator)
	{
		PORT.compareAndSet(Objects.requireNonNull(evaluator), EMPTY);
	}

	public static void resetForTesting()
	{
		PORT.set(EMPTY);
	}
}
