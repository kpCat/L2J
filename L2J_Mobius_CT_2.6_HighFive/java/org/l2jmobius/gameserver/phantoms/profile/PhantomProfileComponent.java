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
import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable persistence snapshot of one bounded opaque profile component.
 */
public record PhantomProfileComponent(long profileId, String componentType, int componentSchemaVersion, long rowVersion, byte[] payload, Instant createdAt, Instant updatedAt)
{
	public static final int MAX_PAYLOAD_BYTES = 4096;
	public static final int MAX_SCHEMA_VERSION = 65535;
	private static final Pattern COMPONENT_TYPE_PATTERN = Pattern.compile("^[a-z][a-z0-9_.-]{0,63}$");

	public PhantomProfileComponent
	{
		if (profileId <= 0)
		{
			throw new IllegalArgumentException("Profile ID must be positive.");
		}
		requireValidComponentType(componentType);
		requireValidSchemaVersion(componentSchemaVersion);
		payload = copyPayload(payload);
		if (rowVersion < 0)
		{
			throw new IllegalArgumentException("Component row version must not be negative.");
		}
		Objects.requireNonNull(createdAt, "Component creation timestamp must not be null.");
		Objects.requireNonNull(updatedAt, "Component update timestamp must not be null.");
	}

	@Override
	public byte[] payload()
	{
		return payload.clone();
	}

	@Override
	public boolean equals(Object object)
	{
		if (this == object)
		{
			return true;
		}
		if (!(object instanceof PhantomProfileComponent other))
		{
			return false;
		}
		return (profileId == other.profileId) //
			&& (componentSchemaVersion == other.componentSchemaVersion) //
			&& (rowVersion == other.rowVersion) //
			&& componentType.equals(other.componentType) //
			&& Arrays.equals(payload, other.payload) //
			&& createdAt.equals(other.createdAt) //
			&& updatedAt.equals(other.updatedAt);
	}

	@Override
	public int hashCode()
	{
		int result = Objects.hash(profileId, componentType, componentSchemaVersion, rowVersion, createdAt, updatedAt);
		result = (31 * result) + Arrays.hashCode(payload);
		return result;
	}

	static void requireValidComponentType(String componentType)
	{
		if ((componentType == null) || !COMPONENT_TYPE_PATTERN.matcher(componentType).matches())
		{
			throw new IllegalArgumentException("Component type must match ^[a-z][a-z0-9_.-]{0,63}$.");
		}
	}

	static void requireValidSchemaVersion(int componentSchemaVersion)
	{
		if ((componentSchemaVersion < 1) || (componentSchemaVersion > MAX_SCHEMA_VERSION))
		{
			throw new IllegalArgumentException("Component schema version must be between 1 and 65535.");
		}
	}

	static byte[] copyPayload(byte[] payload)
	{
		Objects.requireNonNull(payload, "Component payload must not be null.");
		if (payload.length > MAX_PAYLOAD_BYTES)
		{
			throw new IllegalArgumentException("Component payload must not exceed 4096 bytes.");
		}
		return payload.clone();
	}
}
