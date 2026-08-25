/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.clan;

import org.l2jmobius.gameserver.model.chat.ChatObservationService.DeliveredObservation;

@FunctionalInterface
public interface PhantomClanDirectiveIngressPort
{
	boolean onDelivered(DeliveredObservation observation);

	static PhantomClanDirectiveIngressPort noop()
	{
		return observation -> true;
	}
}
