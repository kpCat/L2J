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
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.l2jmobius.gameserver.phantoms.economy;

import java.util.Base64;
import java.util.Objects;

/** Immutable authority for one bounded social economy offer. */
public record PhantomEconomyOffer(String offerId, long initiatingProfileId, int initiatingCharacterObjectId, PhantomEconomyOperation.Kind operationKind, CounterpartyKind counterpartyKind, long counterpartyProfileId, int counterpartyCharacterObjectId, State state, String contentHash, byte[] payload, int initiatorLines, int counterpartyLines, long goalId, long goalRevision, String operationId, String terminalReason, long createdEpochMillis, long updatedEpochMillis, long expiresEpochMillis, long rowVersion)
{
	public PhantomEconomyOffer
	{
		Objects.requireNonNull(operationKind);
		Objects.requireNonNull(counterpartyKind);
		Objects.requireNonNull(state);
		Objects.requireNonNull(payload);
		operationId = operationId == null ? "" : operationId;
		terminalReason = terminalReason == null ? "" : terminalReason;
		payload = payload.clone();
		if ((initiatingProfileId <= 0) || (initiatingCharacterObjectId <= 0) || (counterpartyProfileId < 0) || (counterpartyCharacterObjectId <= 0) || (initiatingCharacterObjectId == counterpartyCharacterObjectId) || (goalId <= 0) || (goalRevision < 0) || (payload.length > 4096) || (initiatorLines < 0) || (initiatorLines > 16) || (counterpartyLines < 0) || (counterpartyLines > 16) || (expiresEpochMillis <= createdEpochMillis) || (updatedEpochMillis < createdEpochMillis) || (rowVersion < 0))
		{
			throw new IllegalArgumentException("Invalid economy offer.");
		}
		if ((counterpartyKind == CounterpartyKind.PHANTOM) != (counterpartyProfileId > 0))
		{
			throw new IllegalArgumentException("Economy offer counterparty identity is inconsistent.");
		}
		if (!socialKind(operationKind) || !hex64(contentHash) || !hex64(offerId) || (!operationId.isEmpty() && !hex64(operationId)) || (terminalReason.length() > 96))
		{
			throw new IllegalArgumentException("Invalid economy offer authority.");
		}
	}

	@Override
	public byte[] payload()
	{
		return payload.clone();
	}

	public static PhantomEconomyOffer draft(long profileId, int characterObjectId, PhantomEconomyOperation.Kind kind, CounterpartyKind counterpartyKind, long counterpartyProfileId, int counterpartyCharacterObjectId, long goalId, long goalRevision, byte[] payload, int initiatorLines, int counterpartyLines, long nowEpochMillis, long expiresEpochMillis)
	{
		final String contentHash = contentHash(payload);
		final String identity = profileId + "|" + characterObjectId + "|" + kind + "|" + counterpartyKind + "|" + counterpartyProfileId + "|" + counterpartyCharacterObjectId + "|" + contentHash + "|" + initiatorLines + "|" + counterpartyLines + "|" + expiresEpochMillis + "|" + goalId + "|" + goalRevision;
		return new PhantomEconomyOffer(PhantomEconomyOperation.sha256(identity), profileId, characterObjectId, kind, counterpartyKind, counterpartyProfileId, counterpartyCharacterObjectId, State.DRAFT, contentHash, payload, initiatorLines, counterpartyLines, goalId, goalRevision, "", "", nowEpochMillis, nowEpochMillis, expiresEpochMillis, 0);
	}

	public static String contentHash(byte[] payload)
	{
		Objects.requireNonNull(payload);
		return PhantomEconomyOperation.sha256(Base64.getEncoder().encodeToString(payload));
	}

	private static boolean socialKind(PhantomEconomyOperation.Kind kind)
	{
		return (kind == PhantomEconomyOperation.Kind.DIRECT_TRADE) || (kind == PhantomEconomyOperation.Kind.PRIVATE_STORE_BUY) || (kind == PhantomEconomyOperation.Kind.PRIVATE_STORE_SELL) || (kind == PhantomEconomyOperation.Kind.PLAYER_MANUFACTURE);
	}

	private static boolean hex64(String value)
	{
		return (value != null) && value.matches("[0-9a-f]{64}");
	}

	public enum CounterpartyKind
	{
		PHANTOM,
		PLAYER,
		OFFLINE_STORE
	}

	public enum State
	{
		DRAFT,
		OFFERED,
		ACCEPTED,
		REJECTED,
		EXPIRED,
		CANCELLED,
		CONSUMED,
		INCONSISTENT;

		public boolean terminal()
		{
			return (this == REJECTED) || (this == EXPIRED) || (this == CANCELLED) || (this == CONSUMED) || (this == INCONSISTENT);
		}
	}
}
