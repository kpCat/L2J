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
package org.l2jmobius.gameserver.phantoms.background;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Hashes;

public record PhantomBackgroundOperationKey(long profileId, int characterObjectId, long goalId, long goalRevision, long activityGeneration, long tickSequence, ActionKind actionKind, int targetNpcId, String anchorId, int modelVersion, Hashes hashes)
{
	public PhantomBackgroundOperationKey
	{
		if ((profileId <= 0) || (characterObjectId <= 0) || (goalId <= 0) || (goalRevision < 0) || (activityGeneration < 0) || (tickSequence < 0) || (targetNpcId < 0) || (modelVersion < 1))
		{
			throw new IllegalArgumentException("Invalid background operation identity.");
		}
		Objects.requireNonNull(actionKind, "actionKind");
		Objects.requireNonNull(anchorId, "anchorId");
		Objects.requireNonNull(hashes, "hashes");
	}

	public String digest()
	{
		try
		{
			final MessageDigest digest = MessageDigest.getInstance("SHA-256");
			final String canonical = profileId + "|" + characterObjectId + "|" + goalId + "|" + goalRevision + "|" + activityGeneration + "|" + tickSequence + "|" + actionKind + "|" + targetNpcId + "|" + anchorId + "|" + modelVersion + "|" + hashes.knowledge() + "|" + hashes.topology() + "|" + hashes.progression() + "|" + hashes.commerce();
			return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException exception)
		{
			throw new IllegalStateException("SHA-256 is unavailable.", exception);
		}
	}

	public enum ActionKind
	{
		TRAVEL,
		FARM,
		RECOVER,
		ACQUISITION_DEATH_DROP,
		ACQUISITION_SPOIL_SWEEP,
		ACQUISITION_TRAVEL
	}
}
