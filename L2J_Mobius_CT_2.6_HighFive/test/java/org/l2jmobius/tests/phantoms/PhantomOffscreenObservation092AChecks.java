/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import org.l2jmobius.gameserver.phantoms.activity.PhantomMaterializationRetentionPolicy;
import org.l2jmobius.gameserver.phantoms.activity.PhantomMaterializationRetentionPolicy.Facts;
import org.l2jmobius.gameserver.phantoms.activity.PhantomMaterializationRetentionPolicy.Reason;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;

/** The same DB-free required gate runs before the new production API exists. */
public final class PhantomOffscreenObservation092AChecks
{
	private static final PhantomTopologyPoint SOURCE = new PhantomTopologyPoint(0, 0, 0, 0);
	private static final PhantomTopologyPoint DESTINATION = new PhantomTopologyPoint(100_000, 100_000, 0, 0);
	private static final PhantomTopologyPoint FAR = new PhantomTopologyPoint(-100_000, -100_000, 0, 0);
	private static int _failures;

	public static void main(String[] args)
	{
		if ((args.length > 0) && args[0].equals("retention-regression"))
		{
			check("existing-scheduler-retention", PhantomOffscreenObservation092AChecks::retentionRegression);
			if (_failures != 0) { throw new AssertionError("TASK092A_RETENTION_REGRESSION_RED"); }
			return;
		}
		check("stored-recent-human", PhantomOffscreenObservation092AChecks::stored);
		check("refresh-recent-human", PhantomOffscreenObservation092AChecks::refresh);
		check("257th-human-overflow", PhantomOffscreenObservation092AChecks::overflow);
		if ((args.length == 0) || !args[0].equals("required"))
		{
			check("fresh-source-destination-native-envelope", PhantomOffscreenObservation092AChecks::locality);
			check("unknown-warmup-and-live-failures", PhantomOffscreenObservation092AChecks::unknown);
			check("connected-human-classification", PhantomOffscreenObservation092AChecks::classification);
			check("concurrent-observation-keeps-newest-clock", PhantomOffscreenObservation092AChecks::concurrentObservation);
		}
		if ((args.length > 0) && args[0].equals("production")) { check("configured-production-facade", PhantomOffscreenObservation092AChecks::facade); }
		if (_failures != 0) { throw new AssertionError("TASK092A_REQUIRED_RED failures=" + _failures); }
		System.out.println("TASK092A_FOCUSED_PASS");
	}

	private static void stored() throws Exception
	{
		final var clock = new AtomicLong(1_000_000L);
		final var facts = new AtomicReference<>(new Facts(true, true, false, false, false));
		final var policy = new PhantomMaterializationRetentionPolicy(_ -> facts.get(), clock::get, 60_000);
		final long observed = policy.observe(1).lastHumanNanos();
		clock.addAndGet(1_000_000L);
		facts.set(new Facts(false, false, false, false, false));
		final var hold = policy.observe(1);
		equal(observed, hold.lastHumanNanos(), "STORED forgot an unexpired human observation");
		equal(false, hold.retained(), "STORED must not become a physical hold");
		equal(false, policy.permitsNewActions(1), "STORED must not admit native actions");
		final var admission = admission(policy, clock::get);
		clock.addAndGet(59_998_000_000L);
		equal("RECENT", recent(policy, 1), "59.999s is still recent after STORED");
		equal("DENY_RECENT_HUMAN", code(evaluate(admission, 1, SOURCE, DESTINATION, 0, List::of, () -> null)), "Recent STORED observer blocks admission");
		clock.addAndGet(1_000_000L);
		equal("CLEAR", recent(policy, 1), "60s expires exactly, without extending the grace");
		equal("ALLOW", code(evaluate(admission, 1, SOURCE, DESTINATION, 0, List::of, () -> null)), "Expired observation only clears the observation boundary");
		facts.set(new Facts(true, false, false, true, true));
		equal(true, policy.observe(1).hard(), "Party and admitted native action still retain the actor");
		equal(true, policy.permitsNewActions(1), "Real party admission survives recent expiry");
		facts.set(new Facts(true, false, false, false, true));
		equal(false, policy.permitsNewActions(1), "Active work alone cannot renew new-root permission");
	}

	private static void refresh() throws Exception
	{
		final var clock = new AtomicLong(1_000_000L);
		final var facts = new AtomicReference<>(new Facts(true, true, false, false, false));
		final var policy = new PhantomMaterializationRetentionPolicy(_ -> facts.get(), clock::get, 60_000);
		policy.observe(1);
		clock.addAndGet(1_000_000L);
		policy.refresh(List.of());
		facts.set(new Facts(true, false, false, false, false));
		equal(true, policy.observe(1).reasons().contains(Reason.RECENT_HUMAN), "Empty refresh forgot recent human");
		facts.set(new Facts(true, true, false, false, false));
		for (int id = 1; id <= 600; id++) { policy.observe(id); }
		final var field = policy.getClass().getDeclaredField("_lastHuman"); field.setAccessible(true);
		final var memory = (java.util.Map<?, ?>) field.get(policy);
		clock.addAndGet(60_000_000_000L);
		for (int sweep = 0; sweep < 4; sweep++)
		{
			final int before = memory.size();
			policy.refresh(List.of());
			equal(true, (before - memory.size()) <= 256, "One empty refresh exceeded the bounded expiry batch");
		}
		equal(0, memory.size(), "Inactive STORED entries must eventually leave memory");
	}

	private static void overflow() throws Exception
	{
		final var clock = new AtomicLong(1_000_000L);
		final var policy = new PhantomMaterializationRetentionPolicy(_ -> new Facts(false, false, false, false, false), clock::get, 60_000);
		final var admission = admission(policy, clock::get);
		clock.addAndGet(60_000_000_000L);
		final var humans = new ArrayList<PhantomTopologyPoint>();
		equal("ALLOW", code(evaluate(admission, 1, SOURCE, DESTINATION, 0, () -> humans, () -> null)), "Complete empty census after warmup");
		humans.add(FAR);
		equal("ALLOW", code(evaluate(admission, 1, SOURCE, DESTINATION, 0, () -> humans, () -> { throw new AssertionError("Fallback must not replace a real census"); })), "One far connected human");
		humans.clear();
		for (int i = 0; i < 256; i++) { humans.add(FAR); }
		equal("ALLOW", code(evaluate(admission, 1, SOURCE, DESTINATION, 0, () -> humans, () -> null)), "Exactly256 is complete");
		humans.add(DESTINATION);
		final Object verdict = evaluate(admission, 1, SOURCE, DESTINATION, 0, () -> humans, () -> null);
		equal("HUMAN_CENSUS_OVERFLOW", code(verdict), "The 257th human must never be truncated into ALLOW");
		equal(257, verdict.getClass().getMethod("inspectedHumans").invoke(verdict), "Overflow must be explicit after257");
		final var inspected = new java.util.concurrent.atomic.AtomicInteger();
		final Iterable<PhantomTopologyPoint> endless = () -> new java.util.Iterator<>()
		{
			@Override public boolean hasNext() { return true; }
			@Override public PhantomTopologyPoint next() { inspected.incrementAndGet(); return FAR; }
		};
		equal("HUMAN_CENSUS_OVERFLOW", code(evaluate(admission, 1, SOURCE, DESTINATION, 0, () -> endless, () -> null)), "Unbounded source stops at257");
		equal(257, inspected.get(), "No eager full census or258th inspection");
	}

	private static void locality() throws Exception
	{
		final var clock = new AtomicLong(0);
		final var policy = new PhantomMaterializationRetentionPolicy(_ -> new Facts(false, false, false, false, false), clock::get, 60_000);
		final var admission = admission(policy, clock::get);
		clock.set(60_000_000_000L);
		final var humans = new AtomicReference<>(List.of(FAR));
		equal("ALLOW", code(evaluate(admission, 1, SOURCE, DESTINATION, 0, humans::get, () -> null)), "Initial far census");
		humans.set(List.of(SOURCE));
		equal("DENY_SOURCE_HUMAN", code(evaluate(admission, 1, SOURCE, DESTINATION, 0, humans::get, () -> null)), "Fresh movement into source revokes old allowance");
		humans.set(List.of(DESTINATION));
		equal("DENY_DESTINATION_HUMAN", code(evaluate(admission, 1, SOURCE, DESTINATION, 0, humans::get, () -> null)), "Destination is guarded independently");
		equal("DENY_SOURCE_HUMAN", code(evaluate(admission, 1, FAR, DESTINATION, 0, () -> List.of(FAR), () -> null)), "Changed source is read on each call");
		equal("DENY_DESTINATION_HUMAN", code(evaluate(admission, 1, SOURCE, FAR, 0, () -> List.of(FAR), () -> null)), "Changed destination is read on each call");
		final List<PhantomTopologyPoint> points = List.of(new PhantomTopologyPoint(4096, 0, 0, 0), new PhantomTopologyPoint(6144, 0, 0, 0), new PhantomTopologyPoint(0, 0, 5999, 0), new PhantomTopologyPoint(0, 0, 6000, 0), new PhantomTopologyPoint(0, 0, 0, 1));
		final List<String> expected = List.of("DENY_SOURCE_HUMAN", "ALLOW", "DENY_SOURCE_HUMAN", "ALLOW", "ALLOW");
		for (int i = 0; i < points.size(); i++)
		{
			final PhantomTopologyPoint point = points.get(i);
			equal(expected.get(i), code(evaluate(admission, 1, SOURCE, DESTINATION, 0, () -> List.of(point), () -> null)), "Native XYZ/instance envelope fixture" + i);
		}
		equal("DENY_SOURCE_HUMAN", code(evaluate(admission, 1, SOURCE, DESTINATION, 0, List::of, () -> SOURCE)), "Autonomous source threat");
		equal("DENY_DESTINATION_HUMAN", code(evaluate(admission, 1, SOURCE, DESTINATION, 0, List::of, () -> DESTINATION)), "Autonomous destination threat");
		equal("UNKNOWN_AUTONOMOUS_OBSERVER", code(evaluate(admission, 1, SOURCE, DESTINATION, 0, List::of, () -> FAR)), "A simulation point never certifies safety");
	}

	private static void unknown() throws Exception
	{
		final var clock = new AtomicLong(100);
		final var facts = new AtomicReference<>(new Facts(false, false, false, false, false));
		final var policy = new PhantomMaterializationRetentionPolicy(_ -> facts.get(), clock::get, 60_000);
		final var admission = admission(policy, clock::get);
		equal("UNKNOWN_RECENT_HUMAN", code(evaluate(admission, 1, SOURCE, DESTINATION, 0, List::of, () -> null)), "Unknown restart history is not absence");
		clock.addAndGet(59_999_000_000L);
		equal("UNKNOWN_RECENT_HUMAN", code(evaluate(admission, 1, SOURCE, DESTINATION, 0, List::of, () -> null)), "Cold-start grace must finish");
		clock.addAndGet(1_000_000L);
		equal("ALLOW", code(evaluate(admission, 1, SOURCE, DESTINATION, 0, List::of, () -> null)), "Completed cold-start grace");
		equal("UNKNOWN_PROFILE", code(evaluate(admission, 0, SOURCE, DESTINATION, 0, List::of, () -> null)), "Invalid profile");
		equal("UNKNOWN_WORLD", code(evaluate(admission, 1, null, DESTINATION, 0, List::of, () -> null)), "Null endpoint");
		final var invalid = new PhantomTopologyPoint(0, 0, 16000, 0);
		equal("UNKNOWN_WORLD", code(evaluate(admission, 1, SOURCE, invalid, 0, List::of, () -> null)), "Native top Z boundary is outside the region array");
		equal("UNKNOWN_INSTANCE", code(evaluate(admission, 1, SOURCE, DESTINATION, 1, List::of, () -> null)), "Only instance0 admitted");
		equal("UNKNOWN_INSTANCE", code(evaluate(admission, 1, SOURCE, new PhantomTopologyPoint(1, 1, 1, 1), 0, List::of, () -> null)), "Endpoint instance mismatch");
		equal("UNKNOWN_HUMAN_CENSUS", code(evaluate(admission, 1, SOURCE, DESTINATION, 0, () -> null, () -> null)), "Null census");
		equal("UNKNOWN_HUMAN_CENSUS", code(evaluate(admission, 1, SOURCE, DESTINATION, 0, () -> java.util.Arrays.asList((PhantomTopologyPoint) null), () -> null)), "Unknown human point");
		equal("UNKNOWN_HUMAN_CENSUS", code(evaluate(admission, 1, SOURCE, DESTINATION, 0, () -> List.of(invalid), () -> null)), "Invalid live coordinates");
		equal("UNKNOWN_HUMAN_CENSUS", code(evaluate(admission, 1, SOURCE, DESTINATION, 0, () -> { throw new IllegalStateException("unavailable census"); }, () -> null)), "Live source failure");
		equal("UNKNOWN_HUMAN_CENSUS", code(evaluate(admission, 1, SOURCE, DESTINATION, 0, List::of, () -> { throw new IllegalStateException("unavailable QA"); })), "QA source failure");
		final Supplier<Iterable<PhantomTopologyPoint>> renewal = () -> { facts.set(new Facts(true, true, false, false, false)); policy.observe(1); return List.of(); };
		equal("DENY_RECENT_HUMAN", code(evaluate(admission, 1, SOURCE, DESTINATION, 0, renewal, () -> null)), "Observation renewed during census is rechecked");
		clock.set(0);
		equal("UNKNOWN", recent(policy, 1), "Regressing clock is uncertain");
	}

	private static void classification() throws Exception
	{
		final var method = Class.forName("org.l2jmobius.gameserver.phantoms.activity.PhantomOffscreenObserverAdmission").getMethod("humanObserver", boolean.class, boolean.class, boolean.class, boolean.class);
		final boolean[][] inputs = {{true, false, true, false}, {true, false, false, true}, {true, false, false, false}, {true, true, true, true}, {false, false, true, true}};
		final boolean[] expected = {true, true, false, false, false};
		for (int i = 0; i < inputs.length; i++) { equal(expected[i], method.invoke(null, inputs[i][0], inputs[i][1], inputs[i][2], inputs[i][3]), "Connected/QA/offline/headless classification" + i); }
	}

	private static String recent(PhantomMaterializationRetentionPolicy policy, long id) throws Exception
	{
		return policy.getClass().getMethod("recentHumanObservation", long.class).invoke(policy, id).toString();
	}

	private static void concurrentObservation() throws Exception
	{
		final var time = new AtomicLong(0);
		final var sampled = new java.util.concurrent.CountDownLatch(1);
		final var resume = new java.util.concurrent.CountDownLatch(1);
		final var delayed = new AtomicReference<Thread>();
		final LongSupplier clock = () ->
		{
			final long now = time.get();
			if (Thread.currentThread() == delayed.get())
			{
				sampled.countDown();
				try { if (!resume.await(5, java.util.concurrent.TimeUnit.SECONDS)) { throw new AssertionError("Concurrent fixture deadline"); } }
				catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
			}
			return now;
		};
		final var policy = new PhantomMaterializationRetentionPolicy(_ -> new Facts(true, true, false, false, false), clock, 60_000);
		final var failure = new AtomicReference<Throwable>();
		final var worker = new Thread(() -> { try { policy.observe(1); } catch (Throwable thrown) { failure.set(thrown); } }, "task092a-delayed-observer");
		delayed.set(worker);
		time.set(1_000_000L);
		worker.start();
		try
		{
			equal(true, sampled.await(5, java.util.concurrent.TimeUnit.SECONDS), "Delayed observation did not sample its clock");
			time.set(1_000_000_000L);
			policy.observe(1);
		}
		finally { resume.countDown(); worker.join(5000); }
		equal(false, worker.isAlive(), "Concurrent fixture thread did not finish");
		if (failure.get() != null) { throw new AssertionError(failure.get()); }
		time.set(60_001_000_000L);
		equal("RECENT", recent(policy, 1), "Older concurrent sample overwrote the later confirmed human observation");
	}

	private static void facade() throws Exception
	{
		final var method = Class.forName("org.l2jmobius.gameserver.phantoms.PhantomSystem").getMethod("offscreenObserverAdmission", long.class, PhantomTopologyPoint.class, PhantomTopologyPoint.class, int.class);
		equal(true, java.lang.reflect.Modifier.isStatic(method.getModifiers()), "Actual configured production runtime must be reachable");
		equal("UNKNOWN_RUNTIME", code(method.invoke(null, 1L, SOURCE, DESTINATION, 0)), "Unconfigured façade must fail closed without bootstrapping World or DB");
		equal(false, org.l2jmobius.commons.database.DatabaseFactory.isInitialized(), "DB-free observer checks initialized the database");
	}

	private static void retentionRegression() throws Exception
	{
		// Select the existing cheap retention contract without running the scheduler's scale/native routes.
		final var type = Class.forName("org.l2jmobius.gameserver.phantoms.PhantomActivitySchedulerSuite");
		final var method = type.getDeclaredMethod("testRetentionReasons");
		method.setAccessible(true);
		method.invoke(type.getConstructor().newInstance());
	}

	private static Object admission(PhantomMaterializationRetentionPolicy policy, LongSupplier clock) throws Exception
	{
		try
		{
			return Class.forName("org.l2jmobius.gameserver.phantoms.activity.PhantomOffscreenObserverAdmission")
				.getConstructor(PhantomMaterializationRetentionPolicy.class, LongSupplier.class).newInstance(policy, clock);
		}
		catch (ClassNotFoundException missing)
		{
			throw new AssertionError("No production overflow-aware admission; the 256-point locality list cannot certify a 257-human census", missing);
		}
	}

	private static Object evaluate(Object admission, long profileId, PhantomTopologyPoint source, PhantomTopologyPoint destination, int instanceId, Supplier<? extends Iterable<PhantomTopologyPoint>> humans, Supplier<PhantomTopologyPoint> autonomous) throws Exception
	{
		return admission.getClass().getMethod("evaluate", long.class, PhantomTopologyPoint.class, PhantomTopologyPoint.class, int.class, Supplier.class, Supplier.class)
			.invoke(admission, profileId, source, destination, instanceId, humans, autonomous);
	}

	private static String code(Object verdict) throws Exception { return verdict.getClass().getMethod("code").invoke(verdict).toString(); }
	private static void equal(Object expected, Object actual, String message)
	{
		if (!java.util.Objects.equals(expected, actual)) { throw new AssertionError(message + ": expected=" + expected + " actual=" + actual); }
	}
	private static void check(String name, Checked body)
	{
		try { body.run(); System.out.println("PASS " + name); }
		catch (Throwable failure) { _failures++; System.out.println("FAIL " + name + " " + failure); }
	}
	@FunctionalInterface private interface Checked { void run() throws Exception; }
}
