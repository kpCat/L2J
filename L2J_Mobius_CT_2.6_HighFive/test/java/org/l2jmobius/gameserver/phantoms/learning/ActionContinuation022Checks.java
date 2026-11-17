/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.learning;

import java.nio.file.*;
import java.util.*;

/** Synthetic capacity/fault fixtures are never native training evidence. */
public final class ActionContinuation022Checks
{
	private static void check(boolean value, String message) { if (!value) { throw new AssertionError(message); } }
	private static void reject(Throwing action) throws Exception
	{
		boolean rejected = false;
		try { action.run(); } catch (IllegalArgumentException | IllegalStateException | java.io.IOException expected) { rejected = true; }
		check(rejected, "022 unsafe continuation accepted");
	}
	@FunctionalInterface private interface Throwing { void run() throws Exception; }
	public static void run(ActionValueV4 accepted, List<ActionFeedbackV4> samples, Path cohort) throws Exception
	{
		Path directory = Files.createTempDirectory("continuation022-").toRealPath();
		var ledger = ActionContinuationV4.initialize(directory, accepted);
		check(ledger.globalUpdates() == 188 && ledger.events().equals(accepted.events()), "022 exact import188");
		check(ActionContinuationV4.load(directory).head().equals(ledger.head()), "022 cold import");
		var replay = samples.stream().filter(s -> accepted.events().containsKey(s.row().eventKey())).toList();
		check(!replay.isEmpty(), "022 genuine historical replay fixture missing");
		check(ActionContinuationV4.prepare(directory, ledger.head(), replay, "a".repeat(64)).head().equals(ledger.head()), "022 replay gained credit");
		reject(() -> ActionContinuationV4.prepare(directory, "f".repeat(64), replay, "a".repeat(64)));
		reject(() -> ActionContinuationV4.prepare(directory, ledger.head(), Collections.nCopies(257, samples.getFirst()), "a".repeat(64)));
		var wrongEvents = new TreeMap<>(accepted.events()); wrongEvents.put(samples.getFirst().row().eventKey(), "e".repeat(64));
		Path tampered = Files.createTempDirectory("source-conflict022-").toRealPath();
		var bad = ActionContinuationV4.initialize(tampered, new ActionValueV4(accepted.weights(), wrongEvents, accepted.episodes(), accepted.sha256(), "0".repeat(64), 188));
		reject(() -> ActionContinuationV4.prepare(tampered, bad.head(), List.of(samples.getFirst()), "a".repeat(64)));
		check(ActionContinuationV4.load(directory).model().sha256().equals(accepted.sha256()), "022 replay changed parent");
		String executable = Path.of(System.getProperty("java.home"), "bin", "java.exe").toString();
		Path incomplete = Files.createTempDirectory("partial-paid022-").toRealPath();
		var paidWriter = new ProcessBuilder(executable, "-cp", System.getProperty("java.class.path"), "org.l2jmobius.tests.phantoms.PhantomLearning022V4Trainer",
			"fault-paid", incomplete.toString(), cohort.toString()).inheritIO().start();
		long paidDeadline = System.nanoTime() + 30_000_000_000L;
		while (!Files.exists(incomplete.resolve("paid-ready022.txt")) && paidWriter.isAlive() && System.nanoTime() < paidDeadline) { Thread.sleep(10); }
		check(Files.exists(incomplete.resolve("paid-ready022.txt")), "022 paid-before-complete fault point unavailable");
		paidWriter.destroyForcibly(); check(paidWriter.waitFor(10, java.util.concurrent.TimeUnit.SECONDS), "022 paid fixture writer did not die");
		boolean missingComplete = false;
		try { org.l2jmobius.tests.phantoms.PhantomLearning022V4Trainer.main(new String[] { "prepare", directory.toString(), ledger.head(), incomplete.toString() }); }
		catch (NoSuchFileException expected) { missingComplete = expected.getFile().endsWith("native021.complete.json"); }
		check(missingComplete && ActionContinuationV4.load(directory).head().equals(ledger.head()), "022 incomplete paid source received credit");
		System.out.println("LEARNING022_IMPORT_REPLAY_PASS imported=" + ledger.events().size());

		// All following event keys and near-cap counts are explicitly synthetic.
		var events = new TreeMap<String, String>();
		for (int i = 0; i < 4095; i++) { events.put(String.format("%064x", i), "b".repeat(64)); }
		var near = new ActionValueV4(accepted.weights(), events, Map.of(), accepted.sha256(), "c".repeat(64), 4095);
		Path capacity = Files.createTempDirectory("rollover022-").toRealPath();
		var before = ActionContinuationV4.initialize(capacity, near);
		var one = List.of(samples.getFirst());
		String javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java.exe").toString();
		var crash = new ProcessBuilder(javaExecutable, "-cp", System.getProperty("java.class.path"), "org.l2jmobius.tests.phantoms.PhantomLearning022V4Trainer",
			"fault-prepare", capacity.toString(), before.head(), cohort.toString()).inheritIO().start();
		long deadline = System.nanoTime() + 30_000_000_000L;
		while (!Files.exists(capacity.resolve("fault-ready022.txt")) && crash.isAlive() && System.nanoTime() < deadline) { Thread.sleep(10); }
		check(Files.exists(capacity.resolve("fault-ready022.txt")), "022 child checkpoint fault point unavailable");
		String preparedHead = Files.readString(capacity.resolve("fault-ready022.txt"));
		crash.destroyForcibly(); check(crash.waitFor(10, java.util.concurrent.TimeUnit.SECONDS), "022 fault writer did not exit");
		var first = ActionContinuationV4.prepare(capacity, before.head(), one, "d".repeat(64));
		check(first.head().equals(preparedHead), "022 killed writer FIT was not reused");
		check(first.model().events().size() == 4096 && first.segment() == 0 && first.globalUpdates() == 4096, "022 capacity4096");
		check(ActionContinuationV4.load(capacity).head().equals(before.head()), "022 pre-HEAD crash changed committed state");
		check(ActionContinuationV4.prepare(capacity, before.head(), one, "d".repeat(64)).head().equals(first.head()), "022 interrupted FIT not deterministic");
		ActionContinuationV4.verifyPrepared(capacity, before.head(), first.head(), one, "d".repeat(64));
		reject(() -> ActionContinuationV4.verifyPrepared(capacity, before.head(), first.head(), one, "e".repeat(64)));
		reject(() -> ActionContinuationV4.commit(capacity, before.head(), first.head()));
		cold(capacity, first.head());
		ActionContinuationV4.commit(capacity, before.head(), first.head());
		ActionContinuationV4.verifyPrepared(capacity, before.head(), first.head(), one, "d".repeat(64));
		ActionContinuationV4.commit(capacity, before.head(), first.head());
		var next = samples.stream().filter(s -> !s.row().eventKey().equals(one.getFirst().row().eventKey()) && s.row().split().equals("TRAIN")).findFirst().orElseThrow();
		var second = ActionContinuationV4.prepare(capacity, first.head(), List.of(next), "e".repeat(64));
		check(second.segment() == 1 && second.model().updates() == 1 && second.globalUpdates() == 4097 && second.events().size() == 4097, "022 rollover lost global history");
		var rolled = ActionContinuationV4.segment(first.model());
		check(Arrays.equals(rolled.weights(), first.model().weights()) && rolled.updates() == 0 && rolled.events().isEmpty(), "022 rollover changed128weights");
		check(Files.isRegularFile(capacity.resolve("candidate-" + rolled.sha256() + ".bin")), "022 physical rollover base missing");
		check(Arrays.equals(ActionLearningV4.load(capacity.resolve("candidate-" + rolled.sha256() + ".bin"), rolled.sha256()).bytes(), rolled.bytes()), "022 physical rollover base changed");
		cold(capacity, second.head());
		ActionContinuationV4.commit(capacity, first.head(), second.head());
		check(ActionContinuationV4.prepare(capacity, second.head(), one, "d".repeat(64)).head().equals(second.head()), "022 cross-segment duplicate credit");
		var heldout = samples.stream().filter(s -> !s.row().split().equals("TRAIN")).findFirst().orElseThrow();
		var audit = ActionContinuationV4.prepare(capacity, second.head(), List.of(heldout), "f".repeat(64));
		check(audit.globalUpdates() == second.globalUpdates() && Arrays.equals(audit.model().weights(), second.model().weights()), "022 heldout received gradient");
		cold(capacity, audit.head()); ActionContinuationV4.commit(capacity, second.head(), audit.head());
		byte[] bytes = Files.readAllBytes(capacity.resolve("ledger-" + first.head() + ".bin"));
		bytes[bytes.length - 1] ^= 1;
		Files.write(capacity.resolve("ledger-" + first.head() + ".bin"), bytes);
		reject(() -> ActionContinuationV4.load(capacity));
		System.out.println("LEARNING022_ROLLOVER_FAULT_PASS synthetic=true boundary=4095,4096,4097 killedWriterReused=true heldoutCredit=0 nativeCredit=0");
	}
	private static void cold(Path root, String head) throws Exception
	{
		String javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java.exe").toString();
		var process = new ProcessBuilder(javaExecutable, "-cp", System.getProperty("java.class.path"),
			ActionContinuation022Checks.class.getName(), "cold", root.toString(), head).inheritIO().start();
		check(process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS) && process.exitValue() == 0, "022 separate-JVM cold failed");
	}
	public static void main(String[] args) throws Exception
	{
		if (args.length != 3 || !args[0].equals("cold")) { throw new IllegalArgumentException("022 cold arguments"); }
		ActionContinuationV4.cold(Path.of(args[1]), args[2]);
	}
}
