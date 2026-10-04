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

import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Arrays;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomActionFacade;
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
		registry.add("twenty-sequential-production-cycles", context -> runCycles(context, 20));
	}

	private void runCycles(PhantomTestContext context, int cycles) throws Exception
	{
		final long[] materializeNanos = new long[cycles]; final long[] actionNanos = new long[cycles]; final long[] cleanupNanos = new long[cycles];
		final var references = new ArrayList<WeakReference<Player>>();
		context.record("productionMaterialization.environment", "pid=" + ProcessHandle.current().pid() + " java=" + System.getProperty("java.version") + " vm=" + System.getProperty("java.vm.name") + " runtimeFlags=" + ManagementFactory.getRuntimeMXBean().getInputArguments().stream().filter(value -> value.startsWith("-X") || value.startsWith("-ea") || value.startsWith("-da")).toList() + " seed=" + context.seed());
		context.record("productionMaterialization.jvm.before." + cycles, jvm());
		final var cpuBefore = ProcessHandle.current().info().totalCpuDuration();
		final long started = System.nanoTime();
		for (int index = 0; index < cycles; index++)
		{
			final long materializeStart = System.nanoTime();
			final var materialized = _service.materialize(_profile.profileId());
			materializeNanos[index] = System.nanoTime() - materializeStart;
			PhantomAssertions.assertEquals(ResultStatus.SUCCESS, materialized.status(), "Performance materialization failed at cycle " + index + ".");
			final Player player = World.getInstance().getPlayer(_environment.primary().objectId());
			PhantomAssertions.assertTrue(player != null, "Performance cycle did not publish the canonical Player.");
			final long actionStart = System.nanoTime();
			try (var action = _service.tryAcquireAction(_profile.profileId()).orElseThrow())
			{
				PhantomAssertions.assertTrue(action.player() == player, "Performance ActionLease changed native identity.");
				new PhantomActionFacade().performReversibleInventoryFixture(player, () -> {});
			}
			actionNanos[index] = System.nanoTime() - actionStart;
			final long cleanupStart = System.nanoTime();
			final var cleanup = _service.dematerialize(_profile.profileId());
			cleanupNanos[index] = System.nanoTime() - cleanupStart;
			PhantomAssertions.assertEquals(ResultStatus.SUCCESS, cleanup.status(), "Performance cleanup failed at cycle " + index + ".");
			_environment.assertClean(_environment.primary(), player);
			references.add(new WeakReference<>(player));
			final var service = _service.snapshot();
			PhantomAssertions.assertEquals(0, service.retainedEntries(), "Performance cycle retained a service entry.");
			PhantomAssertions.assertEquals(1, service.availablePermits(), "Performance cycle retained its permit.");
			if (index == cycles / 2 || index == cycles - 1) { context.record("productionMaterialization.jvm." + cycles + "." + index, jvm() + " observedWeakPlayers=" + references.stream().filter(reference -> reference.get() != null).count() + " retainedEntries=" + service.retainedEntries() + " permits=" + service.availablePermits()); }
		}
		final long elapsed = System.nanoTime() - started;
		final var cpuAfter = ProcessHandle.current().info().totalCpuDuration();
		context.record("productionMaterialization.cycles." + cycles, cycles);
		context.record("productionMaterialization.elapsedNanos." + cycles, elapsed);
		context.record("productionMaterialization.averageNanos." + cycles, elapsed / cycles);
		context.record("productionMaterialization.cpuNanos." + cycles, cpuBefore.isPresent() && cpuAfter.isPresent() ? Long.toString(cpuAfter.get().minus(cpuBefore.get()).toNanos()) : "UNAVAILABLE_PROCESS_CPU_DURATION");
		context.record("productionMaterialization.materializeRawNanos." + cycles, Arrays.toString(materializeNanos));
		context.record("productionMaterialization.actionRawNanos." + cycles, Arrays.toString(actionNanos));
		context.record("productionMaterialization.cleanupRawNanos." + cycles, Arrays.toString(cleanupNanos));
		context.record("productionMaterialization.materializeP95Nanos." + cycles, p95(materializeNanos));
		context.record("productionMaterialization.actionP95Nanos." + cycles, p95(actionNanos));
		context.record("productionMaterialization.cleanupP95Nanos." + cycles, p95(cleanupNanos));
		context.record("productionMaterialization.p95Method", "nearest-rank ceil(N*0.95)-1; warmup1 excluded from measured20; bootstrap/compile excluded");
		context.record("productionMaterialization.allocations", "UNAVAILABLE_NO_ALLOCATION_AGENT_OR_JVM_FLAG_CHANGE");
		context.record("productionMaterialization.reachability", "weak-reference observations without forced GC are diagnostic, not a leak verdict");
		context.record("productionMaterialization.nativeOwnershipSupplement", "COMMON_BASELINE_LANE_NO_NEW_OWNER_API; candidate native scope/queue counters are reported by native lifecycle suite");
		PhantomAssertions.assertEquals(0, _service.snapshot().retainedEntries(), "Performance cycles retained service entries.");
		PhantomAssertions.assertEquals(1, _service.snapshot().availablePermits(), "Performance cycles leaked the permit.");
	}

	private static long p95(long[] values) { final long[] ordered = values.clone(); Arrays.sort(ordered); return ordered[(int) Math.ceil(ordered.length * 0.95) - 1]; }
	private static String jvm()
	{
		final var heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage(); long count = 0; long millis = 0;
		for (var gc : ManagementFactory.getGarbageCollectorMXBeans()) { count += Math.max(0, gc.getCollectionCount()); millis += Math.max(0, gc.getCollectionTime()); }
		return "heapUsed=" + heap.getUsed() + " heapCommitted=" + heap.getCommitted() + " heapMax=" + heap.getMax() + " gcCount=" + count + " gcMillis=" + millis + " liveThreads=" + ManagementFactory.getThreadMXBean().getThreadCount() + " peakThreads=" + ManagementFactory.getThreadMXBean().getPeakThreadCount();
	}
}
