/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.background;

import java.util.Objects;
import java.util.function.BiConsumer;

/** Shared production feedback; native rejections are not geometric assertions. */
public final class PhantomVisibleFarmFailureBinding
{
	private PhantomVisibleFarmFailureBinding() { }

	public static BiConsumer<Long, PhantomVisibleFarmTravel.Failure> bind(PhantomHistoricalBackgroundService history)
	{
		Objects.requireNonNull(history, "history");
		return history::recordVisibleTravelFailure;
	}
}
