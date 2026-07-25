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
package org.l2jmobius.gameserver.phantoms.profile;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable persistence snapshot of a Phantom profile identity.
 */
public record PhantomProfile(long profileId, Integer characterObjectId, int schemaVersion, long rowVersion, Instant createdAt, Instant updatedAt)
{
	public PhantomProfile
	{
		if (profileId <= 0)
		{
			throw new IllegalArgumentException("Profile ID must be positive.");
		}
		if ((characterObjectId != null) && (characterObjectId <= 0))
		{
			throw new IllegalArgumentException("Character object ID must be positive when present.");
		}
		if ((schemaVersion < 1) || (schemaVersion > 65535))
		{
			throw new IllegalArgumentException("Profile schema version must be between 1 and 65535.");
		}
		if (rowVersion < 0)
		{
			throw new IllegalArgumentException("Profile row version must not be negative.");
		}
		Objects.requireNonNull(createdAt, "Profile creation timestamp must not be null.");
		Objects.requireNonNull(updatedAt, "Profile update timestamp must not be null.");
	}
}
