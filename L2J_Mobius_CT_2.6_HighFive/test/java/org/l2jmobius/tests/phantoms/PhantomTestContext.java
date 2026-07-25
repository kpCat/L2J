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
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public final class PhantomTestContext
{
	private final long _seed;
	private final Path _moduleRoot;
	private final Path _reportsDirectory;
	private final Map<String, String> _measurements = new TreeMap<>();

	public PhantomTestContext(long seed, Path moduleRoot, Path reportsDirectory)
	{
		_seed = seed;
		_moduleRoot = moduleRoot.toAbsolutePath().normalize();
		_reportsDirectory = reportsDirectory.toAbsolutePath().normalize();
	}

	public long seed()
	{
		return _seed;
	}

	public Path moduleRoot()
	{
		return _moduleRoot;
	}

	public Path reportsDirectory()
	{
		return _reportsDirectory;
	}

	public void record(String name, Object value)
	{
		_measurements.put(name, String.valueOf(value));
	}

	public Map<String, String> measurements()
	{
		return Collections.unmodifiableMap(_measurements);
	}
}
