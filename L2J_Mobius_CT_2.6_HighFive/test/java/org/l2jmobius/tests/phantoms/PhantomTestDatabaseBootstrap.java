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

import java.io.IOException;
import java.nio.file.Path;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.tests.phantoms.PhantomTestDatabaseGuard.ValidatedSettings;
import org.l2jmobius.tests.phantoms.PhantomTestSchemaManifest.Snapshot;

public final class PhantomTestDatabaseBootstrap
{
	private PhantomTestDatabaseBootstrap()
	{
	}

	public static BootstrapResult initialize(Path moduleRoot, Path configFile) throws Exception
	{
		return initialize(moduleRoot, configFile, PhantomTestSchemaManifest.localPath(moduleRoot));
	}

	static BootstrapResult initialize(Path moduleRoot, Path configFile, Path manifestFile) throws Exception
	{
		final ValidatedSettings settings = PhantomTestDatabaseGuard.validate(moduleRoot, configFile);
		final Snapshot current;
		try
		{
			current = PhantomTestSchemaManifest.current(moduleRoot);
		}
		catch (IOException | IllegalArgumentException e)
		{
			throw new PhantomTestConfigurationException("Current repository schema inventory is invalid.", e);
		}
		final Snapshot local = PhantomTestSchemaManifest.read(manifestFile);
		PhantomTestSchemaManifest.requireExact(current, local);
		DatabaseFactory.initFromConfig(settings.configFile().toString());
		return new BootstrapResult(settings, current);
	}

	public record BootstrapResult(ValidatedSettings settings, Snapshot schemaSnapshot)
	{
	}
}
