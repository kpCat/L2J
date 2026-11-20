/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.combat;

import java.util.List;

/** DB-free checks of the recipient fence used before publishing an owned escape cast. */
public final class PhantomLearnedEscapeChecks
{
	public static void main(String[] args)
	{
		final Object caster = new Object();
		final Recipient fighter = new Recipient(42);
		final Recipient replacement = new Recipient(42);
		final Object summon = new Object();
		final List<Object> approved = List.of(caster, fighter);

		expect(PhantomLearnedEscape.pinRecipients(approved, List.of(caster, summon, fighter)).equals(approved), "stock summon became an escape target");
		expect(PhantomLearnedEscape.pinRecipients(approved, List.of(caster)).isEmpty(), "recipient missing from stock range was admitted");
		expect(PhantomLearnedEscape.pinRecipients(approved, List.of(caster, replacement)).isEmpty(), "replacement with the same character id was admitted");
		expect(PhantomLearnedEscape.pinRecipients(approved, List.of(caster, fighter, fighter)).isEmpty(), "ambiguous stock duplicate was admitted");
		expect(PhantomLearnedEscape.pinRecipients(List.of(caster, caster), List.of(caster)).isEmpty(), "ambiguous approved duplicate was admitted");

		expect(PhantomLearnedEscape.sameRecipients(approved, List.of(fighter, caster)), "unchanged native roster was rejected");
		expect(!PhantomLearnedEscape.sameRecipients(approved, List.of(caster, fighter, summon)), "additional recipient passed the impact fence");
		expect(!PhantomLearnedEscape.sameRecipients(approved, List.of(caster, replacement)), "replacement passed the impact fence");
		expect(!PhantomLearnedEscape.sameRecipients(approved, List.of(caster, caster)), "duplicate passed the impact fence");
		System.out.println("TASK131_LEARNED_ESCAPE_CONTRACT_PASS");
	}

	private static void expect(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}

	private record Recipient(int objectId)
	{
	}
}
