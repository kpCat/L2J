/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.commerce;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Versioned birth entitlement; a reserved native mutation is never replayed after uncertainty. */
public record PhantomStarterShotBudget(int limit, int issued, int itemId, int pending, State state)
{
	public static final String COMPONENT = "starter.shots.v1";
	public static final Set<Integer> ITEMS = Set.of(1835, 2509, 3947);
	public enum State { READY, PENDING, INCONSISTENT }

	public PhantomStarterShotBudget
	{
		if (limit < 0 || limit > 5000 || issued < 0 || issued > limit || pending < 0 || pending > 1000 || pending > issued || state == null
			|| (issued == 0 ? itemId != 0 : !ITEMS.contains(itemId)) || (state == State.READY ? pending != 0 : pending == 0))
		{
			throw new IllegalArgumentException("Invalid starter shot lifetime ledger.");
		}
	}

	public static PhantomStarterShotBudget birth(int limit) { return new PhantomStarterShotBudget(limit, 0, 0, 0, State.READY); }
	public PhantomStarterShotBudget reserve(int selectedItem, long stock)
	{
		if (state != State.READY || stock < 0 || stock >= 100 || issued == limit || !ITEMS.contains(selectedItem)) { return this; }
		final int count = Math.min(1000, limit - issued);
		return new PhantomStarterShotBudget(limit, issued + count, selectedItem, count, State.PENDING);
	}
	public PhantomStarterShotBudget committed()
	{
		if (state != State.PENDING) { throw new IllegalStateException("Starter mutation is not pending."); }
		return new PhantomStarterShotBudget(limit, issued, itemId, 0, State.READY);
	}
	public PhantomStarterShotBudget inconsistent()
	{
		return state == State.PENDING ? new PhantomStarterShotBudget(limit, issued, itemId, pending, State.INCONSISTENT) : this;
	}
	public byte[] encode() { return (limit + ":" + issued + ":" + itemId + ":" + pending + ":" + state).getBytes(StandardCharsets.US_ASCII); }
	public static PhantomStarterShotBudget decode(byte[] payload)
	{
		if (payload == null || payload.length > 128) { throw new IllegalArgumentException("Invalid starter ledger size."); }
		final String[] fields = new String(payload, StandardCharsets.US_ASCII).split(":", -1);
		if (fields.length != 5) { throw new IllegalArgumentException("Invalid starter ledger shape."); }
		return new PhantomStarterShotBudget(Integer.parseInt(fields[0]), Integer.parseInt(fields[1]), Integer.parseInt(fields[2]), Integer.parseInt(fields[3]), State.valueOf(fields[4]));
	}
}
