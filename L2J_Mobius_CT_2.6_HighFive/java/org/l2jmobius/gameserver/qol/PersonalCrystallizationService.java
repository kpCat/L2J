/*
 * This file is part of the L2J Mobius project.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.l2jmobius.gameserver.qol;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongSupplier;

import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.qol.CrystallizationService.Inspection;
import org.l2jmobius.gameserver.qol.CrystallizationService.Preview;
import org.l2jmobius.gameserver.qol.CrystallizationService.Result;

/**
 * Bounded two-step confirmation owner for Personal QoL crystallization.
 */
public final class PersonalCrystallizationService
{
	public static final int PAGE_SIZE = 10;
	private static final int MAX_INVENTORY_SCAN = 500;
	private static final long TOKEN_LIFETIME_MILLIS = 120_000;
	private static final int TOKEN_BYTES = 24;

	private final SecureRandom _random = new SecureRandom();
	private final ConcurrentMap<Integer, PendingConfirmation> _pending = new ConcurrentHashMap<>();
	private volatile LongSupplier _clock = System::currentTimeMillis;

	private PersonalCrystallizationService()
	{
	}

	public Page list(Player player, int requestedPage)
	{
		if (!PersonalCharacterQoLService.getInstance().isCrystallizationEnabled(player))
		{
			return new Page(List.of(), 1, 1);
		}
		final List<Preview> eligible = new ArrayList<>();
		player.getInventory().getItems().stream().sorted(Comparator.comparingInt(Item::getObjectId)).limit(MAX_INVENTORY_SCAN).forEach(item ->
		{
			final Inspection inspection = CrystallizationService.getInstance().inspect(player, item.getObjectId(), item.getCount());
			if (inspection.eligible())
			{
				eligible.add(inspection.preview());
			}
		});
		final int totalPages = Math.max(1, (eligible.size() + PAGE_SIZE - 1) / PAGE_SIZE);
		final int page = Math.max(1, Math.min(requestedPage, totalPages));
		final int from = Math.min((page - 1) * PAGE_SIZE, eligible.size());
		final int to = Math.min(from + PAGE_SIZE, eligible.size());
		return new Page(List.copyOf(eligible.subList(from, to)), page, totalPages);
	}

	public PrepareResult prepare(Player player, int itemObjectId)
	{
		if (player == null)
		{
			return new PrepareResult(ConfirmationStatus.NOT_AUTHORIZED, null);
		}
		_pending.remove(player.getObjectId());
		if (!PersonalCharacterQoLService.getInstance().isCrystallizationEnabled(player))
		{
			return new PrepareResult(ConfirmationStatus.NOT_AUTHORIZED, null);
		}
		final Item item = player.getInventory().getItemByObjectId(itemObjectId);
		if (item == null)
		{
			return new PrepareResult(ConfirmationStatus.STALE, null);
		}
		final Inspection inspection = CrystallizationService.getInstance().inspect(player, itemObjectId, item.getCount());
		if (!inspection.eligible())
		{
			return new PrepareResult(ConfirmationStatus.STALE, null);
		}

		final byte[] bytes = new byte[TOKEN_BYTES];
		_random.nextBytes(bytes);
		final PendingConfirmation pending = new PendingConfirmation(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes), inspection.preview(), _clock.getAsLong() + TOKEN_LIFETIME_MILLIS);
		_pending.put(player.getObjectId(), pending);
		return new PrepareResult(ConfirmationStatus.PREPARED, pending);
	}

	public ConfirmResult confirm(Player player, String token)
	{
		if ((player == null) || (token == null))
		{
			return new ConfirmResult(ConfirmationStatus.INVALID_TOKEN, null);
		}
		final PendingConfirmation pending = _pending.get(player.getObjectId());
		if ((pending == null) || !tokenEquals(pending.token(), token))
		{
			return new ConfirmResult(ConfirmationStatus.INVALID_TOKEN, null);
		}
		if (!_pending.remove(player.getObjectId(), pending))
		{
			return new ConfirmResult(ConfirmationStatus.INVALID_TOKEN, null);
		}
		if (pending.expiresAt() <= _clock.getAsLong())
		{
			return new ConfirmResult(ConfirmationStatus.EXPIRED, null);
		}
		if (!PersonalCharacterQoLService.getInstance().isCrystallizationEnabled(player))
		{
			return new ConfirmResult(ConfirmationStatus.NOT_AUTHORIZED, null);
		}

		final Inspection inspection = CrystallizationService.getInstance().inspect(player, pending.preview().itemObjectId(), pending.preview().count());
		if (!inspection.eligible() || !pending.preview().equals(inspection.preview()))
		{
			return new ConfirmResult(ConfirmationStatus.STALE, null);
		}
		final Result result = CrystallizationService.getInstance().crystallize(player, pending.preview().itemObjectId(), pending.preview().count(), pending.preview());
		return new ConfirmResult(result.success() ? ConfirmationStatus.SUCCESS : ConfirmationStatus.STALE, result);
	}

	private static boolean tokenEquals(String expected, String actual)
	{
		return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
	}

	LongSupplier installClockForTests(LongSupplier clock)
	{
		final LongSupplier previous = _clock;
		_clock = clock;
		return previous;
	}

	void clearForTests(Player player)
	{
		if (player != null)
		{
			_pending.remove(player.getObjectId());
		}
	}

	public enum ConfirmationStatus
	{
		PREPARED,
		SUCCESS,
		NOT_AUTHORIZED,
		INVALID_TOKEN,
		EXPIRED,
		STALE
	}

	public record PendingConfirmation(String token, Preview preview, long expiresAt)
	{
	}

	public record PrepareResult(ConfirmationStatus status, PendingConfirmation pending)
	{
	}

	public record ConfirmResult(ConfirmationStatus status, Result crystallizationResult)
	{
	}

	public record Page(List<Preview> items, int page, int totalPages)
	{
	}

	public static PersonalCrystallizationService getInstance()
	{
		return SingletonHolder.INSTANCE;
	}

	private static class SingletonHolder
	{
		private static final PersonalCrystallizationService INSTANCE = new PersonalCrystallizationService();
	}
}
