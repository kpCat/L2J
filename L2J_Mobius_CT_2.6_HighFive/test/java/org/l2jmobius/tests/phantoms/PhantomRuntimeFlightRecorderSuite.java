/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.l2jmobius.gameserver.phantoms.diagnostics.PhantomRuntimeFlightRecorder;

/** DB-free recorder contracts through the existing Phantom test runner. */
public final class PhantomRuntimeFlightRecorderSuite implements PhantomTestSuite
{
	public static void main(String[] args)
	{
		final var context = new PhantomTestContext(14001401, Path.of("."), Path.of("../build/phantom-test/reports"));
		System.exit(PhantomTestLauncher.runSuite("runtime-flight-recorder", new PhantomRuntimeFlightRecorderSuite(), context));
	}

	@Override
	public String id() { return "phantom-runtime-flight-recorder"; }

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("activation-and-fail-closed", _ ->
		{
			final AtomicBoolean diagnostics = new AtomicBoolean(false);
			final var recorder = new PhantomRuntimeFlightRecorder(diagnostics::get, 16);
			PhantomAssertions.assertFalse(recorder.begin("consent"), "Diagnostics off must reject BEGIN.");
			recorder.watch(1);
			record(recorder, 1);
			PhantomAssertions.assertEquals(0, recorder.snapshot("consent").events().size(), "Disabled retention.");
			diagnostics.set(true);
			recorder.watch(1);
			record(recorder, 1);
			PhantomAssertions.assertEquals(0, recorder.snapshot("consent").events().size(), "No Pilot session retention.");
			PhantomAssertions.assertTrue(recorder.begin("consent"), "Consented BEGIN.");
			PhantomAssertions.assertFalse(recorder.begin("other"), "Only one active session.");
			recorder.watch(1);
			record(recorder, 1);
			PhantomAssertions.assertEquals(1, recorder.snapshot("consent").events().size(), "Enabled retention.");
			PhantomAssertions.assertEquals(0, recorder.snapshot("other").events().size(), "Session ownership.");
			diagnostics.set(false);
			record(recorder, 1);
			PhantomAssertions.assertEquals(0, recorder.snapshot("consent").events().size(), "Disabled export is fail-closed.");
			final var broken = new PhantomRuntimeFlightRecorder(() -> { throw new IllegalStateException("diagnostic-only"); }, 16);
			PhantomAssertions.assertFalse(broken.begin("consent"), "Recorder exception must not escape.");
			broken.watch(1);
			record(broken, 1);
			broken.recordCurrent("MAT_ABORT", "", "", 0);
		});
		registry.add("bounds-reset-and-correlation", _ ->
		{
			final var recorder = new PhantomRuntimeFlightRecorder(() -> true, 16);
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new PhantomRuntimeFlightRecorder(() -> true, 8193), "Capacity bound.");
			PhantomAssertions.assertTrue(recorder.begin("first"), "BEGIN first.");
			for (long id = 1; id <= 9; id++) { recorder.watch(id); }
			for (int i = 0; i < 40; i++) { record(recorder, 1); }
			record(recorder, 9);
			recorder.record(0, "HUMAN_REFRESH_SUMMARY", "", "", "global", 1, 2, 3);
			recorder.record(1, "LOCAL_CANDIDATE", "ONLINE", "", "x".repeat(200), 1, 2, 3);
			PhantomAssertions.assertEquals(42, recorder.withProfile(1, () -> { recorder.recordCurrent("MAT_PLAYER_LOAD_OK", "LOADING", "loaded", 123); return 42; }), "Delegate result unchanged.");
			final var snapshot = recorder.end("first");
			PhantomAssertions.assertEquals(8, snapshot.watched().size(), "max8 watched.");
			PhantomAssertions.assertEquals(16, snapshot.events().size(), "Fixed ring bound.");
			PhantomAssertions.assertEquals(27L, snapshot.dropped(), "Explicit wrap loss count.");
			PhantomAssertions.assertTrue(snapshot.events().stream().noneMatch(event -> event.profileId() == 9), "Ninth profile excluded.");
			PhantomAssertions.assertTrue(snapshot.events().stream().allMatch(event -> event.reason().length() <= 96), "Reason bound.");
			PhantomAssertions.assertTrue(snapshot.events().stream().anyMatch(event -> event.event().equals("MAT_PLAYER_LOAD_OK") && event.profileId() == 1 && event.value1() == 123), "Lifecycle correlation.");
			record(recorder, 1);
			PhantomAssertions.assertEquals(snapshot.events(), recorder.snapshot("first").events(), "END freezes retention.");
			PhantomAssertions.assertTrue(recorder.begin("second"), "Reset BEGIN.");
			PhantomAssertions.assertEquals(0, recorder.snapshot("second").watched().size(), "Reset watched.");
			PhantomAssertions.assertEquals(0, recorder.snapshot("second").events().size(), "Reset events.");
			recorder.watch(9);
			record(recorder, 9);
			PhantomAssertions.assertEquals(1L, recorder.snapshot("second").events().getFirst().seq(), "Reset sequence.");
			recorder.withProfile(9, () ->
			{
				recorder.end("second");
				recorder.begin("third");
				recorder.watch(9);
				recorder.recordCurrent("MAT_ABORT", "", "previous-session", 1);
				return null;
			});
			PhantomAssertions.assertEquals(0, recorder.snapshot("third").events().size(), "Old lifecycle context cannot leak across reset.");
		});
		registry.add("concurrent-recording-and-delegate-exception", _ ->
		{
			final var recorder = new PhantomRuntimeFlightRecorder(() -> true, 8192);
			recorder.begin("parallel");
			try (var executor = Executors.newFixedThreadPool(4))
			{
				for (int worker = 0; worker < 4; worker++)
				{
					final long profile = worker + 1;
					executor.submit(() -> { recorder.watch(profile); for (int i = 0; i < 2500; i++) { record(recorder, profile); } });
				}
				executor.shutdown();
				PhantomAssertions.assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "Concurrent recording deadline.");
			}
			final var snapshot = recorder.end("parallel");
			PhantomAssertions.assertEquals(10000L, snapshot.attempts(), "All concurrent attempts counted.");
			PhantomAssertions.assertTrue(snapshot.events().size() <= 8192, "Concurrent ring bound.");
			PhantomAssertions.assertEquals(10000L, snapshot.events().size() + snapshot.dropped(), "Every unavailable event counted.");
			PhantomAssertions.assertEquals((long) snapshot.events().size(), snapshot.events().stream().map(event -> event.seq()).distinct().count(), "Unique sequence.");
			PhantomAssertions.assertTrue(snapshot.events().stream().allMatch(event -> event.profileId() >= 1 && event.profileId() <= 4), "Profile correlation.");
			recorder.begin("exception");
			recorder.watch(1);
			final var expected = new IllegalStateException("gameplay-delegate");
			PhantomAssertions.assertEquals(expected, PhantomAssertions.assertThrows(IllegalStateException.class,
				() -> recorder.withProfile(1, () -> { throw expected; }), "Delegate exception must propagate unchanged."), "Exception identity.");
			recorder.recordCurrent("MAT_ABORT", "", "", 1);
			PhantomAssertions.assertEquals(0, recorder.snapshot("exception").events().size(), "Thread context restored after delegate exception.");
		});
	}

	private static void record(PhantomRuntimeFlightRecorder recorder, long profile)
	{
		recorder.record(profile, "LOCAL_CANDIDATE", "", "", "test", 0, 0, 0);
	}
}
