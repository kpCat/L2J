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

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Properties;
import java.util.SplittableRandom;

public final class PhantomScenarioSmokeSuite implements PhantomTestSuite
{
	public static final String EXPECTED_CHECKSUM = "A7D53E8FCBF889691310AAC61A45EFD461702FECE26BA292D73309A9FE357C45";

	@Override
	public String id()
	{
		return "scenario-smoke";
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("fixture-and-checksum", context ->
		{
			final Properties fixture = new Properties();
			try (InputStream input = PhantomScenarioSmokeSuite.class.getClassLoader().getResourceAsStream("phantoms/scenarios/harness-smoke.properties"))
			{
				PhantomAssertions.assertTrue(input != null, "Scenario fixture is missing.");
				fixture.load(input);
			}

			final long fixtureSeed = Long.parseLong(fixture.getProperty("seed"));
			final int count = Integer.parseInt(fixture.getProperty("count"));
			final int bound = Integer.parseInt(fixture.getProperty("bound"));
			final String expected = fixture.getProperty("checksum");
			PhantomAssertions.assertEquals(context.seed(), fixtureSeed, "Scenario fixture seed mismatch.");
			PhantomAssertions.assertEquals(64, count, "Scenario fixture count mismatch.");
			PhantomAssertions.assertEquals(1000, bound, "Scenario fixture bound mismatch.");
			PhantomAssertions.assertEquals(EXPECTED_CHECKSUM, expected, "Scenario fixture checksum mismatch.");
			final String actual = checksum(fixtureSeed, count, bound);
			PhantomAssertions.assertEquals(expected, actual, "Scenario checksum mismatch.");
			context.record("scenario.checksum", actual);
			context.record("scenario.count", count);
		});
	}

	static String checksum(long seed, int count, int bound) throws Exception
	{
		final SplittableRandom random = new SplittableRandom(seed);
		final MessageDigest digest = MessageDigest.getInstance("SHA-256");
		final ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
		for (int i = 0; i < count; i++)
		{
			buffer.clear();
			buffer.putInt(random.nextInt(bound));
			digest.update(buffer.array());
		}
		return HexFormat.of().withUpperCase().formatHex(digest.digest());
	}
}
