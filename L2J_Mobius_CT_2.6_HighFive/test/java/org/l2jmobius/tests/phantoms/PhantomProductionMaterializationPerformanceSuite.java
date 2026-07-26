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

import java.sql.Connection;
import java.sql.PreparedStatement;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ResultStatus;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ServiceState;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfile;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;

public final class PhantomProductionMaterializationPerformanceSuite implements PhantomTestSuite
{
	private final PhantomHeadlessPlayerTestEnvironment _environment = new PhantomHeadlessPlayerTestEnvironment();
	private PhantomProfileRepository _repository;
	private PhantomProfile _profile;
	private PhantomMaterializationService _service;

	@Override
	public String id()
	{
		return "production-materialization-performance";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		_environment.initialize(context);
		_repository = PhantomProfileRepository.open();
		_profile = _repository.create(_environment.primary().objectId());
		final PhantomMetrics metrics = new PhantomMetrics();
		_service = new PhantomMaterializationService(_repository, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), 1);
		PhantomAssertions.assertTrue(_service.start(), "Performance materialization service did not start.");
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		Throwable failure = null;
		try
		{
			if (_service != null)
			{
				final PhantomMaterializationService.ShutdownResult shutdown = _service.shutdown();
				PhantomAssertions.assertEquals(ServiceState.STOPPED, shutdown.state(), "Performance service did not stop.");
			}
			if ((_profile != null) && DatabaseFactory.isInitialized())
			{
				try (Connection connection = DatabaseFactory.getConnection();
					PreparedStatement statement = connection.prepareStatement("DELETE FROM phantom_profiles WHERE profile_id = ?"))
				{
					statement.setLong(1, _profile.profileId());
					statement.executeUpdate();
				}
			}
		}
		catch (Throwable throwable)
		{
			failure = throwable;
		}
		try
		{
			_environment.shutdown();
		}
		catch (Throwable throwable)
		{
			if (failure == null)
			{
				failure = throwable;
			}
			else
			{
				failure.addSuppressed(throwable);
			}
		}
		if (failure instanceof Exception exception)
		{
			throw exception;
		}
		if (failure != null)
		{
			throw new RuntimeException(failure);
		}
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("one-sequential-production-cycle", context -> runCycles(context, 1));
		registry.add("ten-sequential-production-cycles", context -> runCycles(context, 10));
	}

	private void runCycles(PhantomTestContext context, int cycles) throws Exception
	{
		final long started = System.nanoTime();
		for (int index = 0; index < cycles; index++)
		{
			PhantomAssertions.assertEquals(ResultStatus.SUCCESS, _service.materialize(_profile.profileId()).status(), "Performance materialization failed at cycle " + index + ".");
			final Player player = World.getInstance().getPlayer(_environment.primary().objectId());
			PhantomAssertions.assertTrue(player != null, "Performance cycle did not publish the canonical Player.");
			PhantomAssertions.assertEquals(ResultStatus.SUCCESS, _service.dematerialize(_profile.profileId()).status(), "Performance cleanup failed at cycle " + index + ".");
			_environment.assertClean(_environment.primary(), player);
		}
		final long elapsed = System.nanoTime() - started;
		context.record("productionMaterialization.cycles." + cycles, cycles);
		context.record("productionMaterialization.elapsedNanos." + cycles, elapsed);
		context.record("productionMaterialization.averageNanos." + cycles, elapsed / cycles);
		PhantomAssertions.assertEquals(0, _service.snapshot().retainedEntries(), "Performance cycles retained service entries.");
		PhantomAssertions.assertEquals(1, _service.snapshot().availablePermits(), "Performance cycles leaked the permit.");
	}
}
