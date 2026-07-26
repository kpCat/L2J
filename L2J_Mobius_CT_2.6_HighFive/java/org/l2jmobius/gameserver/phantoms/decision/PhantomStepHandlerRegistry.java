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

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public final class PhantomStepHandlerRegistry
{
	public static final int MAX_HANDLERS = 256;
	private final TreeMap<String, PhantomStepHandler> _handlers = new TreeMap<>();
	private Map<String, PhantomStepHandler> _snapshot = Map.of();
	private boolean _sealed;

	public synchronized void register(String actionKey, PhantomStepHandler handler)
	{
		if (_sealed)
		{
			throw new IllegalStateException("Step-handler registry is sealed.");
		}
		actionKey = PhantomDecisionKey.require(actionKey, "Action key");
		Objects.requireNonNull(handler, "Step handler must not be null.");
		if (_handlers.size() >= MAX_HANDLERS)
		{
			throw new IllegalStateException("Step-handler registry capacity is 256.");
		}
		if (_handlers.putIfAbsent(actionKey, handler) != null)
		{
			throw new IllegalArgumentException("Duplicate action key: " + actionKey);
		}
	}

	public synchronized void seal()
	{
		if (!_sealed)
		{
			_snapshot = Collections.unmodifiableMap(new TreeMap<>(_handlers));
			_sealed = true;
		}
	}

	public synchronized boolean isSealed()
	{
		return _sealed;
	}

	public synchronized Map<String, PhantomStepHandler> snapshot()
	{
		if (!_sealed)
		{
			throw new IllegalStateException("Step-handler registry must be sealed before use.");
		}
		return _snapshot;
	}
}
