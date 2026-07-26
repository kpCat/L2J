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
package org.l2jmobius.gameserver.phantoms.decision;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Domain-neutral reference whose interpretation belongs to a later integration.
 */
public record PhantomDomainRef(String namespace, String key) implements Comparable<PhantomDomainRef>
{
	private static final Pattern NAMESPACE_PATTERN = Pattern.compile("^[a-z][a-z0-9_.-]{0,31}$");

	public PhantomDomainRef
	{
		Objects.requireNonNull(namespace, "Domain namespace must not be null.");
		Objects.requireNonNull(key, "Domain key must not be null.");
		if (!NAMESPACE_PATTERN.matcher(namespace).matches())
		{
			throw new IllegalArgumentException("Domain namespace must match ^[a-z][a-z0-9_.-]{0,31}$.");
		}
		if ((key.length() < 1) || (key.length() > 128) || !key.equals(key.trim()))
		{
			throw new IllegalArgumentException("Domain key must contain 1..128 visible ASCII characters without surrounding whitespace.");
		}
		for (int index = 0; index < key.length(); index++)
		{
			final char character = key.charAt(index);
			if ((character < 0x21) || (character > 0x7e))
			{
				throw new IllegalArgumentException("Domain key must contain visible ASCII characters only.");
			}
		}
	}

	@Override
	public int compareTo(PhantomDomainRef other)
	{
		final int namespaceOrder = namespace.compareTo(other.namespace);
		return namespaceOrder != 0 ? namespaceOrder : key.compareTo(other.key);
	}
}
