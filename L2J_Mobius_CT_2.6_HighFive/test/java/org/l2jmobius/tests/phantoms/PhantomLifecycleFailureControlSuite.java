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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PhantomLifecycleFailureControlSuite implements PhantomTestSuite
{
	private final Path _explicitMarker;
	private final boolean _failCleanup;
	private Path _marker;

	public PhantomLifecycleFailureControlSuite()
	{
		this(null, false);
	}

	PhantomLifecycleFailureControlSuite(Path marker, boolean failCleanup)
	{
		_explicitMarker = marker;
		_failCleanup = failCleanup;
	}

	@Override
	public String id()
	{
		return "lifecycle-failure-control";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		final String configuredMarker = System.getProperty("phantom.lifecycle.marker");
		_marker = _explicitMarker != null ? _explicitMarker : ((configuredMarker == null) || configuredMarker.isBlank() ? context.reportsDirectory().resolve("lifecycle-control.marker") : Path.of(configuredMarker));
		Files.createDirectories(_marker.toAbsolutePath().normalize().getParent());
		Files.writeString(_marker, "partial-before-all-resource", StandardCharsets.UTF_8);
		throw new PhantomTestConfigurationException("Intentional partial beforeAll lifecycle failure.");
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		if (_marker != null)
		{
			Files.deleteIfExists(_marker);
		}
		if (_failCleanup)
		{
			throw new IllegalStateException("Intentional afterAll lifecycle failure.");
		}
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("must-not-run", _ -> PhantomAssertions.assertTrue(false, "Test body ran after beforeAll failure."));
	}
}
