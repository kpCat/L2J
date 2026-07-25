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

import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.player.HeadlessPlayerOutboundSession;
import org.l2jmobius.gameserver.phantoms.player.PhantomActionFacade;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomPlayerMaterializationSpike;

public final class PhantomHeadlessPlayerPerformanceSuite implements PhantomTestSuite
{
	private static final long MAX_ONE_FIXTURE_NANOS = 30_000_000_000L;
	private static final long MAX_TEN_FIXTURES_NANOS = 120_000_000_000L;
	private final PhantomHeadlessPlayerTestEnvironment _environment = new PhantomHeadlessPlayerTestEnvironment();

	@Override
	public String id()
	{
		return "headless-player-performance";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		_environment.initialize(context);
		runLifecycle(false);
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		_environment.shutdown();
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("one-fixture-measured", context ->
		{
			final long start = System.nanoTime();
			final HeadlessPlayerOutboundSession.Snapshot snapshot = runLifecycle(true);
			final long elapsed = System.nanoTime() - start;
			PhantomAssertions.assertTrue(elapsed <= MAX_ONE_FIXTURE_NANOS, "One-fixture lifecycle exceeded bounded latency.");
			PhantomAssertions.assertTrue(snapshot.recordedPacketClasses().size() <= snapshot.recordingCapacity(), "Packet recorder exceeded capacity.");
			context.record("headless.performance.oneFixtureNanos", elapsed);
			context.record("headless.performance.oneFixtureEffects", snapshot.effectCount());
			context.record("headless.performance.recordingCapacity", snapshot.recordingCapacity());
		});
		registry.add("ten-sequential-fixtures-measured", context ->
		{
			final long start = System.nanoTime();
			long effects = 0;
			long dropped = 0;
			for (int i = 0; i < 10; i++)
			{
				final HeadlessPlayerOutboundSession.Snapshot snapshot = runLifecycle(true);
				effects += snapshot.effectCount();
				dropped += snapshot.droppedRecordCount();
			}
			final long elapsed = System.nanoTime() - start;
			PhantomAssertions.assertTrue(elapsed <= MAX_TEN_FIXTURES_NANOS, "Ten sequential fixture lifecycles exceeded bounded latency.");
			_environment.assertClean(_environment.primary(), null);
			context.record("headless.performance.tenSequentialNanos", elapsed);
			context.record("headless.performance.tenSequentialEffects", effects);
			context.record("headless.performance.tenSequentialDroppedRecords", dropped);
		});
	}

	private HeadlessPlayerOutboundSession.Snapshot runLifecycle(boolean action) throws Exception
	{
		final HeadlessPlayerOutboundSession output = new HeadlessPlayerOutboundSession(16, 128, 16);
		final PhantomPlayerMaterializationSpike spike = new PhantomPlayerMaterializationSpike(_environment.primary().objectId(), PhantomIdentityLeaseRegistry.getInstance(), output, new PhantomActionFacade(), PhantomPlayerMaterializationSpike.FailureInjector.none());
		spike.materialize();
		final Player player = spike.getPlayer();
		try
		{
			if (action)
			{
				spike.performReversibleInventoryAction();
			}
		}
		finally
		{
			spike.cleanup();
		}
		_environment.assertClean(_environment.primary(), player);
		return output.snapshot();
	}
}
