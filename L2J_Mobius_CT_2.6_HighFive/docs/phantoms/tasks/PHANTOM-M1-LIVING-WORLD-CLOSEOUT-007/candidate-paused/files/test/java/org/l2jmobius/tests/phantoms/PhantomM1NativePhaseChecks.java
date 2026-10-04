/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence;
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence.Phase;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.actor.instance.Servitor;
import org.l2jmobius.gameserver.model.actor.status.CreatureStatus;
import org.l2jmobius.gameserver.model.events.EventType;
import org.l2jmobius.gameserver.model.events.holders.actor.creature.OnCreatureDamageDealt;
import org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.stats.Formulas;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope;

/** Original native writers first, phase assertions afterwards. No phase/vital/stat/rate writer is injected by TEST. */
public final class PhantomM1NativePhaseChecks
{
	private PhantomM1NativePhaseChecks() { }

	/** TEST setup uses the loaded stock effect, not a fabricated HP/stat/lifetime body or a learned-skill claim. */
	public static Servitor createSummon(Player owner)
	{
		PhantomAssertions.assertTrue(!owner.hasSummon() && (!owner.isNativeWorkManaged() || PlayerNativeWork.current(owner.getNativeWorkOwner()) != null), "INVALID SUMMON: fresh owner and admitted native setup required.");
		final Skill skill = SkillData.getInstance().getSkill(1225, 1);
		PhantomAssertions.assertTrue(skill != null, "INVALID SUMMON: stock1225/1 unavailable.");
		skill.activateSkill(owner, List.of(owner));
		PhantomAssertions.assertTrue(owner.getSummon() instanceof Servitor, "INVALID SUMMON: original Summon effect did not create Servitor.");
		final var summon = (Servitor) owner.getSummon();
		PhantomAssertions.assertTrue(summon.getId() == 14159 && summon.getOwner() == owner && summon.isSpawned() && !summon.isDead(), "INVALID SUMMON: stock1225 exact native body unavailable.");
		summon.setFollowStatus(false); summon.getAI().stopAITask();
		return summon;
	}

	/** Caller retains stock bootstrap/disposal inside its existing exact ActionLease and canonical fixture. */
	public static void summon(PhantomTestContext context, Player owner, PhantomMaterializationService service, long profileId, Servitor summon, Monster target, CompletionFence completion) throws Exception
	{
		PhantomAssertions.assertEquals(PhantomTestDatabaseGuard.TARGET_DATABASE, context.measurements().get("headless.database"), "Native summon proof requires guarded headless TEST.");
		PhantomAssertions.assertTrue(PlayerNativeWork.inheritedPlayer() == null && owner.getNativeWorkOwner() instanceof PhantomNativeWorkScope, "INVALID SUMMON: exact managed root lifetime required.");
		final var scope = (PhantomNativeWorkScope) owner.getNativeWorkOwner(); final var evidence = scope.evidence();
		PhantomAssertions.assertTrue(exact(owner, scope) && scope.open() && scope.outstanding() == 0 && evidence.snapshot().phase() == Phase.NONE && !evidence.snapshot().overflow()
			&& !owner.isAutoPlaying() && !owner.isAttackingOrCastingNow() && owner.getAI().getIntention() == Intention.IDLE, "INVALID SUMMON: fresh passive managed owner required.");
		final var initial = evidence.snapshot();
		final var observed = summonProbe(context, owner, summon, target, scope, completion, () ->
		{
			try (var action = service.tryAcquireAction(profileId).orElseThrow())
			{
				PhantomAssertions.assertTrue(action.player() == owner, "INVALID SUMMON: exact ActionLease changed.");
				summon.setTarget(target); summon.doAttack(target);
			}
		});
		context.record("A.SUMMON.frontend", observed.frontend()); context.record("A.SUMMON.nativeWriter", observed.writer()); context.record("A.SUMMON.quiescent", observed.terminal());
		PhantomAssertions.assertTrue(observed.frontend().phase() == Phase.NONE && !observed.frontend().overflow(), "Original Servitor frontend attributed COMBAT before successful native HP assignment.");
		PhantomAssertions.assertTrue(observed.queued() > 0 && observed.running() > 0, "Actual Servitor HitTask lost exact owner obligation before publication/body completion.");
		assertEpisode(observed.writer(), owner, scope, Phase.COMBAT);
		PhantomAssertions.assertTrue(observed.writer().damageSequence() > initial.damageSequence(), "Real positive Servitor HP writer was not attributed to exact owner damage evidence.");
		PhantomAssertions.assertTrue(observed.terminal().phase() == Phase.NONE && !observed.terminal().overflow() && exact(owner, scope) && scope.firstNativeIncident() == null,
			"Actual Servitor completion did not retire its exact passive COMBAT episode.");
	}

	/** Same stock native body/executor/formula for an ordinary unmanaged Player; no sensor is installed by TEST. */
	public static void ordinarySummon(PhantomTestContext context, Player owner, Servitor summon, Monster target, CompletionFence completion) throws Exception
	{
		PhantomAssertions.assertEquals(PhantomTestDatabaseGuard.TARGET_DATABASE, context.measurements().get("headless.database"), "Ordinary summon proof requires guarded headless TEST.");
		PhantomAssertions.assertTrue(!owner.isNativeWorkManaged() && owner.getNativeWorkOwner() == null && PlayerNativeWork.inheritedPlayer() == null, "INVALID ordinary SUMMON: unmanaged original owner required.");
		summonProbe(context, owner, summon, target, null, completion, () -> { summon.setTarget(target); summon.doAttack(target); });
		PhantomAssertions.assertTrue(owner.getNativeWorkOwner() == null && !owner.isNativeWorkManaged(), "Ordinary Servitor acquired a managed lifetime.");
	}

	private static SummonObservation summonProbe(PhantomTestContext context, Player owner, Servitor summon, Monster target, PhantomNativeWorkScope scope, CompletionFence completion, Runnable publish) throws Exception
	{
		PhantomAssertions.assertTrue(completion != null && completion.matches(owner, summon, scope), "INVALID SUMMON: exact caller completion fence required before publication.");
		PhantomAssertions.assertTrue(owner.getSummon() == summon && summon.getOwner() == owner && summon.isSpawned() && World.getInstance().findObject(summon.getObjectId()) == summon
			&& !summon.isDead() && !summon.isAttackingOrCastingNow() && target.isSpawned() && target.getId() == 20534 && !target.isDead() && !target.isInvul()
			&& target.isCoreAIDisabled() && summon.getInstanceId() == target.getInstanceId() && summon.isInSurroundingRegion(target), "INVALID SUMMON: exact native actor/target topology required.");
		final Field poolField = accessible(ThreadPool.class, "SCHEDULED_POOL"); final var original = (ScheduledThreadPoolExecutor) poolField.get(null);
		PhantomAssertions.assertTrue(original != null && !original.isShutdown(), "INVALID SUMMON: original scheduled executor unavailable.");
		for (int attempt = 1; attempt <= 3; attempt++)
		{
			final String key = "A.SUMMON." + (scope == null ? "ordinary" : "managed") + "." + attempt;
			context.record(key + ".fixture", "INVALID_UNTIL_ORIGINAL_POSITIVE_HP_WRITER_COMPLETION");
			final var gate = new SummonPool(original, owner, scope, completion); final var damage = new CountDownLatch(1); final var releaseBody = new CountDownLatch(1);
			final var atWriter = new AtomicReference<SummonWriter>(); final double beforeHp;
			synchronized (target.getStatus()) { beforeHp = target.getCurrentHp(); }
			final var listener = new ConsumerEventListener(summon, EventType.ON_CREATURE_DAMAGE_DEALT, (OnCreatureDamageDealt event) ->
			{
				if (event.getAttacker() != summon || event.getTarget() != target || event.getSkill() != null || event.isDamageOverTime() || !(event.getDamage() > 0)) { return; }
				final boolean nativeStack = Arrays.stream(Thread.currentThread().getStackTrace()).anyMatch(frame -> frame.getClassName().equals("org.l2jmobius.gameserver.model.actor.tasks.creature.HitTask") && frame.getMethodName().equals("run"));
				final double loss; synchronized (target.getStatus()) { loss = beforeHp - target.getCurrentHp(); }
				if (atWriter.compareAndSet(null, new SummonWriter(loss, nativeStack, summon.getOwner() == owner && owner.getSummon() == summon && (scope == null || exact(owner, scope)), scope == null ? null : scope.evidence().snapshot(), scope == null ? 0 : scope.outstanding())))
				{
					damage.countDown();
					try { if (!releaseBody.await(5, TimeUnit.SECONDS)) { gate.failure.compareAndSet(null, "Native positive writer hold expired."); } }
					catch (InterruptedException failure) { Thread.currentThread().interrupt(); gate.failure.compareAndSet(null, "Native writer interrupted."); }
				}
			}, summon);
			Throwable primary = null; boolean installed = false; PlayerNativeEvidence.Snapshot frontend = null; int queued = 0;
			try
			{
				summon.addListener(listener); poolField.set(null, gate); installed = true; publish.run();
				frontend = scope == null ? null : scope.evidence().snapshot(); queued = scope == null ? 0 : scope.outstanding();
				PhantomAssertions.assertTrue(gate.publications.get() == 1 && atWriter.get() == null && (scope == null || exact(owner, scope)), "INVALID SUMMON: original single-hit publisher/pre-entry barrier was not established.");
				gate.release.countDown();
				damage.await(4, TimeUnit.SECONDS); releaseBody.countDown();
				final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(7);
				while ((gate.completed.get() != 1 || summon.isAttackingOrCastingNow() || (scope != null && scope.outstanding() != 0)) && System.nanoTime() < deadline) { Thread.sleep(10); }
				PhantomAssertions.assertTrue(!summon.isAttackingOrCastingNow() && (scope == null || scope.outstanding() == 0) && gate.failure.get() == null && gate.completed.get() == 1,
					"INVALID SUMMON: original HitTask/reuse did not quiesce: " + gate.failure.get());
				final var writer = atWriter.get();
				if (writer != null)
				{
					PhantomAssertions.assertTrue(writer.loss() > 0 && Double.isFinite(writer.loss()) && writer.nativeStack() && writer.exact(), "INVALID SUMMON: no factual positive original HP writer for exact Servitor/owner.");
					context.record(key + ".fixture", "VALID_ORIGINAL_STOCK_SERVITOR_HIT_TASK_POSITIVE_HP_DELTA_AND_NATIVE_COMPLETION");
					context.record(key + ".native", "summonObject=" + summon.getObjectId() + " template=" + summon.getId() + " owner=" + owner.getObjectId() + " epoch=" + (scope == null ? -1 : scope.epoch())
						+ " actualHpLoss=" + writer.loss() + " queued=" + queued + " running=" + writer.running() + " originalBodyExecutorDelay=true ownerIntention=" + owner.getAI().getIntention() + " summonIntention=" + summon.getAI().getIntention());
					return new SummonObservation(frontend, writer.snapshot(), scope == null ? null : scope.evidence().snapshot(), queued, writer.running());
				}
				context.record(key + ".native", "ORIGINAL_PHYSICAL_MISS_NO_PHASE_ASSERTION");
			}
			catch (Exception | Error failure) { primary = failure; if (failure instanceof InterruptedException) { Thread.currentThread().interrupt(); } throw failure; }
			finally
			{
				gate.release.countDown(); releaseBody.countDown();
				Throwable cleanupFailure = null;
				try { if (installed) { PhantomAssertions.assertTrue(poolField.get(null) == gate, "SUMMON TEST foreign executor slot restore refused."); poolField.set(null, original); } }
				catch (Exception | Error cleanup) { cleanupFailure = cleanup; }
				try { completion.awaitOriginalBodies(context, key); }
				catch (Exception | Error cleanup) { if (cleanupFailure == null) { cleanupFailure = cleanup; } else if (cleanupFailure != cleanup) { cleanupFailure.addSuppressed(cleanup); } }
				try { if (!completion.retained()) { summon.removeListener(listener); } }
				catch (Exception | Error cleanup) { if (cleanupFailure == null) { cleanupFailure = cleanup; } else if (cleanupFailure != cleanup) { cleanupFailure.addSuppressed(cleanup); } }
				finally { gate.shutdownNow(); }
				if (cleanupFailure != null) { if (primary == null) { rethrowCleanup(cleanupFailure); } else if (primary != cleanupFailure) { primary.addSuppressed(cleanupFailure); } }
			}
		}
		throw new AssertionError("INVALID SUMMON: three original physical attacks produced no real positive HP writer.");
	}
	private record SummonWriter(double loss, boolean nativeStack, boolean exact, PlayerNativeEvidence.Snapshot snapshot, int running) { }
	private record SummonObservation(PlayerNativeEvidence.Snapshot frontend, PlayerNativeEvidence.Snapshot writer, PlayerNativeEvidence.Snapshot terminal, int queued, int running) { }
	private static void rethrowCleanup(Throwable failure) throws Exception { if (failure instanceof Error error) { throw error; } throw (Exception) failure; }

	/** Local TEST receipt contains scalar identity, latches and private journal only; no Player/Scope/Actor references. */
	public static final class CompletionFence
	{
		private final int objectId, summonId; private final long epoch; private final Path journal; private final String journalText;
		private final List<NativeBody> bodies = new ArrayList<>(); private volatile String retained = ""; private boolean cleanupFinished;
		public CompletionFence(PhantomTestContext context, Player owner, String ownedBeforeImage) throws Exception
		{
			PhantomAssertions.assertEquals(PhantomTestDatabaseGuard.TARGET_DATABASE, context.measurements().get("headless.database"), "Summon completion journal requires guarded TEST.");
			PhantomAssertions.assertTrue(owner.getSummon() instanceof Servitor && ownedBeforeImage != null && ownedBeforeImage.length() <= 8192, "INVALID SUMMON: exact bootstrap/bounded owned before-image diagnostics required.");
			requireNoRetainedCompletion(context);
			objectId = owner.getObjectId(); summonId = owner.getSummon().getObjectId(); epoch = owner.getNativeWorkOwner() == null ? -1 : owner.getNativeWorkOwner().epoch();
			final Path root = context.moduleRoot().toRealPath(), directory = root.resolve(".phantom-local");
			Files.createDirectories(directory);
			PhantomAssertions.assertTrue(!Files.isSymbolicLink(directory) && directory.toRealPath().equals(directory), "SUMMON_PRIVATE_JOURNAL_DIRECTORY_UNPROVEN");
			journal = directory.resolve("m1-007-summon-" + objectId + "-" + Long.toUnsignedString(epoch) + ".journal");
			journalText = "SUMMON_TEST_ORIGINAL_BODY_AND_CLEANUP_PENDING\nobject=" + objectId + " summonObject=" + summonId + " epoch=" + epoch + "\nownedBeforeImage=" + ownedBeforeImage + "\nNO_AUTOMATIC_RESTORE_OR_RECOVERY\n";
			try (var channel = FileChannel.open(journal, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))
			{
				final var bytes = ByteBuffer.wrap(journalText.getBytes(StandardCharsets.UTF_8)); while (bytes.hasRemaining()) { channel.write(bytes); } channel.force(true);
			}
			context.record("A.SUMMON.completionJournal." + objectId, journal.getFileName().toString());
		}
		private boolean matches(Player owner, Servitor summon, PhantomNativeWorkScope scope)
		{
			return objectId == owner.getObjectId() && summonId == summon.getObjectId() && epoch == (scope == null ? -1 : scope.epoch()) && !retained() && !cleanupFinished;
		}
		private synchronized NativeBody register()
		{
			PhantomAssertions.assertTrue(!retained() && !cleanupFinished && bodies.size() < 16, "INVALID SUMMON: bounded original body publication exceeded or cleanup closed.");
			final var body = new NativeBody(); bodies.add(body); return body;
		}
		private void awaitOriginalBodies(PhantomTestContext context, String key) throws Exception
		{
			final List<NativeBody> captured; synchronized (this) { captured = List.copyOf(bodies); }
			boolean interrupted = Thread.interrupted(); final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
			try
			{
				for (var body : captured)
				{
					while (body.finished.getCount() != 0 && System.nanoTime() < deadline)
					{
						try { body.finished.await(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS); }
						catch (InterruptedException interruption) { interrupted = true; }
					}
				}
				if (captured.stream().anyMatch(body -> body.finished.getCount() != 0))
				{
					retained = "SUMMON_ORIGINAL_BODY_COMPLETION_UNKNOWN object=" + objectId + " summon=" + summonId + " epoch=" + epoch + " " + diagnostic();
					throw new IllegalStateException(retained + " journal=" + journal.getFileName());
				}
			}
			finally { try { context.record(key + ".originalCompletion", diagnostic()); } finally { if (interrupted) { Thread.currentThread().interrupt(); } } }
		}
		public boolean retained() { return !retained.isEmpty(); }
		public synchronized boolean cleanupSafe()
		{
			if (retained() || cleanupFinished) { return false; }
			for (var body : bodies) { if (body.finished.getCount() != 0) { return false; } }
			return true;
		}
		public synchronized String diagnostic()
		{
			return "captured=" + bodies.size() + " accepted=" + bodies.stream().filter(body -> body.accepted).count() + " nativeFinally=" + bodies.stream().filter(body -> body.completed).count()
				+ " rejected=" + bodies.stream().filter(body -> body.rejected).count() + " unresolved=" + bodies.stream().filter(body -> body.finished.getCount() != 0).count() + " journal=" + journal.getFileName();
		}
		public synchronized void requireSafeCleanup() { PhantomAssertions.assertTrue(cleanupSafe(), "SUMMON_TEST_DISPOSAL_AND_CANONICAL_RESTORE_REFUSED " + diagnostic()); }
		/** Caller invokes only after all original disposal and fixture canonical close succeeded. */
		public synchronized void cleanupFinished() throws Exception
		{
			requireSafeCleanup();
			PhantomAssertions.assertTrue(!cleanupFinished && !Files.isSymbolicLink(journal) && Files.isRegularFile(journal, LinkOption.NOFOLLOW_LINKS)
				&& Files.readString(journal, StandardCharsets.UTF_8).equals(journalText), "SUMMON_TEST_FOREIGN_JOURNAL_DELETE_REFUSED");
			Files.delete(journal); cleanupFinished = true;
		}
	}
	private static final class NativeBody
	{
		private final CountDownLatch finished = new CountDownLatch(1); private volatile boolean started, accepted, completed, rejected;
		private synchronized void start() { started = true; }
		private synchronized void accepted() { accepted = true; }
		private synchronized void rejected(boolean originalAbortPolicy)
		{
			if (originalAbortPolicy && !started && !accepted) { rejected = true; finished.countDown(); }
		}
		private void complete() { completed = true; finished.countDown(); }
	}
	/** Read-only exact private namespace guard, before any fresh fixture/bootstrap; never deletes or restores. */
	public static void requireNoRetainedCompletion(PhantomTestContext context) throws Exception
	{
		final Path root = context.moduleRoot().toRealPath(), directory = root.resolve(".phantom-local");
		if (Files.notExists(directory, LinkOption.NOFOLLOW_LINKS)) { return; }
		PhantomAssertions.assertTrue(Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(directory) && directory.toRealPath().equals(directory), "SUMMON_PRIVATE_JOURNAL_DIRECTORY_UNPROVEN");
		try (var files = Files.list(directory))
		{
			final var pending = files.filter(path -> { final String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT); return name.startsWith("m1-007-summon-") && name.endsWith(".journal"); }).findFirst();
			if (pending.isPresent()) { throw new IllegalStateException("SUMMON_TEST_RETAINED_JOURNAL_ADMISSION_REFUSED " + pending.get().getFileName()); }
		}
	}
	private static final class SummonPool extends ScheduledThreadPoolExecutor
	{
		private final ScheduledThreadPoolExecutor original; private final Player owner; private final PhantomNativeWorkScope scope; private final CompletionFence completion;
		private final CountDownLatch release = new CountDownLatch(1); private final AtomicInteger publications = new AtomicInteger(), completed = new AtomicInteger(); private final AtomicReference<String> failure = new AtomicReference<>();
		private SummonPool(ScheduledThreadPoolExecutor original, Player owner, PhantomNativeWorkScope scope, CompletionFence completion)
		{
			super(1, task -> { final var thread = new Thread(task, "TEST-original-summon-hit-observer"); thread.setDaemon(true); return thread; }); this.original = original; this.owner = owner; this.scope = scope; this.completion = completion;
		}
		@Override public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit)
		{
			final boolean source = StackWalker.getInstance().walk(frames -> frames.anyMatch(frame -> frame.getClassName().equals("org.l2jmobius.gameserver.model.actor.Creature") && frame.getMethodName().equals("doAttackHitSimple")));
			if (!source || (scope == null ? PlayerNativeWork.inheritedPlayer() != null : PlayerNativeWork.current(scope) == null || !exact(owner, scope))) { return original.schedule(task, delay, unit); }
			final var body = completion.register(); publications.incrementAndGet();
			final var rejectionHandler = original.getRejectedExecutionHandler();
			try
			{
				final var future = original.schedule(() ->
				{
					body.start();
					try
					{
						try { if (!release.await(5, TimeUnit.SECONDS)) { failure.compareAndSet(null, "Original queued hit release expired."); } }
						catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); failure.compareAndSet(null, "Original queued hit interrupted."); }
						task.run();
					}
					finally { completed.incrementAndGet(); body.complete(); }
				}, delay, unit);
				if (future != null) { body.accepted(); } return future;
			}
			catch (RejectedExecutionException rejection)
			{
				body.rejected(original.getClass() == ScheduledThreadPoolExecutor.class && rejectionHandler.getClass() == ThreadPoolExecutor.AbortPolicy.class
					&& original.getRejectedExecutionHandler() == rejectionHandler); throw rejection;
			}
		}
		@Override public <V> ScheduledFuture<V> schedule(Callable<V> task, long delay, TimeUnit unit) { return original.schedule(task, delay, unit); }
		@Override public void execute(Runnable task) { original.execute(task); }
		@Override public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long delay, long period, TimeUnit unit) { return original.scheduleAtFixedRate(task, delay, period, unit); }
		@Override public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, long delay, long period, TimeUnit unit) { return original.scheduleWithFixedDelay(task, delay, period, unit); }
	}

	/** Caller supplies a fresh non-AutoPlay managed mage, known stock1177 and a factual core-AI-disabled20534. */
	public static void regen(PhantomTestContext context, Player player, PhantomMaterializationService service, long profileId, Monster target, Skill skill) throws Exception
	{
		PhantomAssertions.assertEquals(PhantomTestDatabaseGuard.TARGET_DATABASE, context.measurements().get("headless.database"), "Native phase proof requires guarded headless TEST.");
		PhantomAssertions.assertTrue(PlayerNativeWork.inheritedPlayer() == null && player.getNativeWorkOwner() instanceof PhantomNativeWorkScope, "INVALID REGEN: exact managed root lifetime required.");
		final var scope = (PhantomNativeWorkScope) player.getNativeWorkOwner(); final var evidence = scope.evidence();
		PhantomAssertions.assertTrue(exact(player, scope) && scope.open() && scope.outstanding() == 0 && evidence != null && evidence.matches(player.getObjectId(), scope.epoch())
			&& !player.isAutoPlaying() && player.hasAI() && player.getAI().getIntention() == Intention.IDLE && !player.isAttackingOrCastingNow()
			&& player.getParty() == null && !player.hasSummon() && player.getEffectList().getEffects().isEmpty(), "INVALID REGEN: fresh passive exact solo native Player is required.");
		PhantomAssertions.assertTrue(skill != null && skill.getId() == 1177 && player.getKnownSkill(1177) == skill && !player.isSkillDisabled(skill)
			&& player.getStat().getMpInitialConsume(skill) > 0 && player.getStat().getMpConsume(skill) > 0, "INVALID REGEN: original learned usable Wind Strike MP costs required.");
		PhantomAssertions.assertTrue(target != null && target.getId() == 20534 && target.isSpawned() && !target.isDead() && !target.isRaid() && target.isCoreAIDisabled()
			&& target.getInstanceId() == player.getInstanceId(), "INVALID REGEN: caller's original factual20534 target is unavailable.");
		final Vitals initial = vitals(player); final var initialEvidence = evidence.snapshot();
		PhantomAssertions.assertTrue(initial.full() && initial.maxHp() == player.getMaxHp() && initial.maxMp() == player.getMaxMp() && regenerationTask(player) == null,
			"INVALID REGEN: original fully restored HP/MP with no previous regen task required.");
		PhantomAssertions.assertTrue(!initialEvidence.overflow() && initialEvidence.phase() == Phase.NONE, "INVALID REGEN: caller already has an attributed/expired phase.");
		final Field poolField = accessible(ThreadPool.class, "SCHEDULED_POOL"); final var original = (ScheduledThreadPoolExecutor) poolField.get(null);
		PhantomAssertions.assertTrue(original != null && !original.isShutdown(), "INVALID REGEN: original scheduled executor missing.");
		final var observer = new RegenPool(original, player, scope); final var nativeDamage = new AtomicInteger(); final var damageStack = new AtomicReference<String>();
		final var combatAtWriter = new AtomicReference<PlayerNativeEvidence.Snapshot>(); final var workAtWriter = new AtomicInteger();
		final var listener = new ConsumerEventListener(player, EventType.ON_CREATURE_DAMAGE_DEALT, (OnCreatureDamageDealt event) ->
		{
			if (event.getAttacker() == player && event.getTarget() == target && event.getDamage() > 0 && Double.isFinite(event.getDamage())
				&& Arrays.stream(Thread.currentThread().getStackTrace()).anyMatch(frame -> frame.getClassName().equals("org.l2jmobius.gameserver.model.actor.tasks.creature.MagicUseTask") && frame.getMethodName().equals("run")))
			{
				nativeDamage.incrementAndGet(); damageStack.compareAndSet(null, "ACTUAL_NATIVE_MAGIC_USE_TASK");
				if (exact(player, scope) && combatAtWriter.compareAndSet(null, evidence.snapshot())) { workAtWriter.set(scope.outstanding()); }
			}
		}, player);
		Throwable primary = null; boolean listenerInstalled = false; boolean poolInstalled = false;
		final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(35);
		try
		{
			player.addListener(listener); listenerInstalled = true; poolField.set(null, observer); poolInstalled = true;
			final double castBefore;
			final double castAfter;
			final PlayerNativeEvidence.Snapshot frontend;
			try (var action = service.tryAcquireAction(profileId).orElseThrow())
			{
				PhantomAssertions.assertTrue(action.player() == player, "INVALID REGEN: ActionLease changed original Player.");
				player.setTarget(target); PhantomAssertions.assertTrue(skill.getTargetList(player).contains(target), "INVALID REGEN: original target handler rejected20534.");
				castBefore = vitals(player).mp(); player.doCast(skill); castAfter = vitals(player).mp();
				frontend = evidence.snapshot();
				PhantomAssertions.assertTrue(nativeDamage.get() == 0, "INVALID COMBAT: original native writer already entered before its frontend observation.");
			}
			PhantomAssertions.assertTrue(castAfter < castBefore && castAfter >= 0, "INVALID REGEN: original doCast did not make a real native MP deficit.");
			while ((nativeDamage.get() == 0 || player.isCastingNow() || player.isCastingSimultaneouslyNow() || scope.outstanding() != 0) && System.nanoTime() < deadline) { Thread.sleep(10); }
			PhantomAssertions.assertTrue(nativeDamage.get() > 0 && !player.isCastingNow() && !player.isCastingSimultaneouslyNow() && scope.outstanding() == 0
				&& evidence.snapshot().damageSequence() > initialEvidence.damageSequence(), "INVALID REGEN: original1177 never completed its actual native damage/finalizer.");
			final long castCompleted = System.nanoTime(); final var afterCastEvidence = evidence.snapshot(); final Vitals afterCast = vitals(player);
			context.record("A.REGEN.nativeCast", "object=" + player.getObjectId() + " epoch=" + scope.epoch() + " skill=1177 npc=" + target.getId() + " writer=" + damageStack.get()
				+ " mpBefore=" + castBefore + " mpAfterOriginalFrontend=" + castAfter + " mpAfterFinalizer=" + afterCast.mp() + " nativeFormulaRegen=" + Formulas.calcMpRegen(player));
			boolean renewed = false;
			Tick first = null;
			while (System.nanoTime() < deadline)
			{
				final List<Tick> positive = observer.positiveAfter(castCompleted);
				if (first == null) { first = positive.stream().filter(tick -> !tick.after().full()).findFirst().orElse(null); }
				if (first != null && first.phase().phase() == Phase.REGEN)
				{
					final var sample = evidence.snapshot();
					if (sample.phase() == Phase.REGEN && (sample.phaseSinceNanos() != first.phase().phaseSinceNanos() || sample.phaseDeadlineNanos() != first.phase().phaseDeadlineNanos())) { renewed = true; }
				}
				if (positive.size() >= 2 && vitals(player).full() && regenerationTask(player) == null && scope.outstanding() == 0) { break; }
				Thread.sleep(20);
			}
			final List<Tick> positive = observer.positiveAfter(castCompleted); final var terminal = evidence.snapshot();
			context.record("A.REGEN.nativePublication", "originalSource=" + observer.source.get() + " publications=" + observer.publications.get() + " sameOriginalExecutor=true unchangedDelayPeriod=true");
			for (int index = 0; index < Math.min(8, positive.size()); index++) { context.record("A.REGEN.nativeTick." + index, positive.get(index)); }
			PhantomAssertions.assertTrue(observer.failure.get() == null && observer.publications.get() > 0 && first != null && positive.size() >= 2
				&& positive.stream().allMatch(Tick::exact) && vitals(player).full() && regenerationTask(player) == null && scope.outstanding() == 0 && scope.firstNativeIncident() == null,
				"INVALID REGEN: original positive periodic writers/full restore were not established: " + observer.failure.get());
			PhantomAssertions.assertTrue(positive.stream().allMatch(tick -> tick.phase().damageSequence() == afterCastEvidence.damageSequence()
				&& tick.phase().rewardSequence() == afterCastEvidence.rewardSequence() && tick.phase().lootSequence() == afterCastEvidence.lootSequence()
				&& tick.phase().usefulProgressNanos() == afterCastEvidence.usefulProgressNanos()), "REGEN observation changed useful native evidence without another original useful writer.");
			context.record("A.REGEN.fixture", "VALID_ORIGINAL_WIND_STRIKE_MP_COST_AND_AT_LEAST_TWO_POSITIVE_NATIVE_MP_REGEN_TICKS_FULL_RESTORE");
			context.record("A.COMBAT.frontend", frontend); context.record("A.COMBAT.nativeWriter", combatAtWriter.get());
			context.record("A.COMBAT.quiescent", afterCastEvidence);
			PhantomAssertions.assertTrue(combatAtWriter.get() != null && workAtWriter.get() > 0, "INVALID COMBAT: confirmed positive native writer had no exact running owned callback.");
			PhantomAssertions.assertTrue(frontend.phase() == Phase.NONE && !frontend.overflow(), "Original cast frontend attributed COMBAT before its first successful HP writer.");
			assertEpisode(combatAtWriter.get(), player, scope, Phase.COMBAT);
			PhantomAssertions.assertTrue(afterCastEvidence.phase() != Phase.COMBAT && !afterCastEvidence.overflow(), "Original cast completion retained COMBAT after all native work finished and actor became passive.");
			// Product assertions follow the successful original native writer/control, so NONE is a valid phase RED.
			assertEpisode(first.phase(), player, scope, Phase.REGEN);
			for (Tick tick : positive)
			{
				if (!tick.after().full())
				{
					assertEpisode(tick.phase(), player, scope, Phase.REGEN);
					PhantomAssertions.assertTrue(tick.phase().phaseSinceNanos() == first.phase().phaseSinceNanos() && tick.phase().phaseDeadlineNanos() == first.phase().phaseDeadlineNanos(), "Native repeated regeneration tick renewed its episode.");
				}
			}
			PhantomAssertions.assertFalse(renewed, "Native REGEN reads/ticks renewed their original episode deadline.");
			PhantomAssertions.assertTrue(exact(player, scope) && !terminal.overflow() && terminal.phase() == Phase.NONE && terminal.phaseSinceNanos() == 0 && terminal.phaseDeadlineNanos() == 0,
				"Original native full HP/MP restoration retained or cleared another REGEN episode.");
		}
		catch (Exception | Error failure) { primary = failure; throw failure; }
		finally
		{
			try
			{
				if (poolInstalled)
				{
					PhantomAssertions.assertTrue(poolField.get(null) == observer, "REGEN TEST original pool slot changed concurrently; foreign slot restore refused.");
					poolField.set(null, original);
				}
				if (listenerInstalled) { player.removeListener(listener); }
				try (var action = service.tryAcquireAction(profileId).orElseThrow())
				{
					PhantomAssertions.assertTrue(action.player() == player, "REGEN TEST cleanup changed native Player.");
					player.getStatus().stopHpMpRegeneration(); player.abortCast(); player.setTarget(null);
				}
			}
			catch (Exception | Error cleanup) { if (primary == null) { throw cleanup; } if (primary != cleanup) { primary.addSuppressed(cleanup); } }
			finally { observer.shutdownNow(); }
		}
	}

	private static void assertEpisode(PlayerNativeEvidence.Snapshot sample, Player player, PhantomNativeWorkScope scope, Phase phase)
	{
		PhantomAssertions.assertTrue(!sample.overflow() && sample.objectId() == player.getObjectId() && sample.epoch() == scope.epoch() && sample.phase() == phase,
			"Actual positive native writer lacks exact " + phase + " phase evidence.");
		PhantomAssertions.assertTrue(sample.phaseSinceNanos() >= scope.epoch() && sample.phaseSinceNanos() <= sample.sampleNanos() && sample.phaseDeadlineNanos() > sample.sampleNanos()
			&& sample.phaseDeadlineNanos() - sample.phaseSinceNanos() <= PlayerNativeEvidence.MAX_PHASE_NANOS, "Native episode is expired, renewed or unbounded.");
	}
	private static boolean exact(Player player, PhantomNativeWorkScope scope)
	{
		return scope.player() == player && player.getNativeWorkOwner() == scope && scope.isCurrent() && World.getInstance().getPlayer(player.getObjectId()) == player
			&& World.getInstance().findObject(player.getObjectId()) == player && scope.evidence().matches(player.getObjectId(), scope.epoch());
	}
	private static Vitals vitals(Player player)
	{
		synchronized (player.getStatus()) { return new Vitals(player.getCurrentHp(), player.getCurrentMp(), player.getMaxRecoverableHp(), player.getMaxRecoverableMp()); }
	}
	private static Object regenerationTask(Player player) throws ReflectiveOperationException
	{
		synchronized (player.getStatus()) { return accessible(CreatureStatus.class, "_regTask").get(player.getStatus()); }
	}
	private static Field accessible(Class<?> type, String name) throws NoSuchFieldException { final Field field = type.getDeclaredField(name); field.setAccessible(true); return field; }
	private record Vitals(double hp, double mp, double maxHp, double maxMp)
	{
		boolean full() { return Double.isFinite(hp) && Double.isFinite(mp) && hp >= maxHp && mp >= maxMp; }
	}
	private record Tick(int publication, long started, long finished, Vitals before, Vitals after, PlayerNativeEvidence.Snapshot phase, boolean exact)
	{
		boolean positiveMp() { return after.mp() > before.mp(); }
	}

	/** Transparent local analogue of RawProducerChecks targeted pool: original body/executor/rate remain unchanged. */
	private static final class RegenPool extends ScheduledThreadPoolExecutor
	{
		private final ScheduledThreadPoolExecutor original; private final Player player; private final PhantomNativeWorkScope scope;
		private final List<Tick> ticks = new ArrayList<>();
		private final AtomicInteger publications = new AtomicInteger(); private final AtomicReference<String> source = new AtomicReference<>(), failure = new AtomicReference<>();
		private RegenPool(ScheduledThreadPoolExecutor original, Player player, PhantomNativeWorkScope scope)
		{
			super(1, body -> { final var thread = new Thread(body, "TEST-original-native-regen-observer"); thread.setDaemon(true); return thread; });
			this.original = original; this.player = player; this.scope = scope;
		}
		@Override public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) { return original.schedule(task, delay, unit); }
		@Override public <V> ScheduledFuture<V> schedule(Callable<V> task, long delay, TimeUnit unit) { return original.schedule(task, delay, unit); }
		@Override public void execute(Runnable task) { original.execute(task); }
		@Override public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, long delay, long period, TimeUnit unit) { return original.scheduleWithFixedDelay(task, delay, period, unit); }
		@Override public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long delay, long period, TimeUnit unit)
		{
			final var frame = StackWalker.getInstance().walk(frames -> frames.filter(value -> value.getClassName().equals(CreatureStatus.class.getName()) && value.getMethodName().equals("startHpMpRegeneration")).findFirst().orElse(null));
			if (frame == null || !PlayerNativeWork.inheritedPlayers().contains(player) || PlayerNativeWork.current(scope) == null || !exact(player, scope)) { return original.scheduleAtFixedRate(task, delay, period, unit); }
			final int publication = publications.incrementAndGet(); source.compareAndSet(null, frame.toString());
			PhantomAssertions.assertTrue(publication <= 8 && unit.toMillis(delay) == Formulas.getRegeneratePeriod(player) && unit.toMillis(period) == Formulas.getRegeneratePeriod(player), "INVALID REGEN: original producer exceeded bounded publication/rate observation.");
			return original.scheduleAtFixedRate(() ->
			{
				final long started = System.nanoTime(); final Vitals before = vitals(player);
				try { task.run(); }
				finally
				{
					try
					{
						final Vitals after = vitals(player); final var snapshot = scope.evidence().snapshot();
						final var tick = new Tick(publication, started, System.nanoTime(), before, after, snapshot, exact(player, scope));
						synchronized (ticks) { if (ticks.size() < 32) { ticks.add(tick); } else { failure.compareAndSet(null, "NATIVE_REGEN_TICK_OBSERVATION_CAP"); } }
					}
					catch (RuntimeException | Error observationFailure) { failure.compareAndSet(null, observationFailure.getClass().getName()); }
				}
			}, delay, period, unit);
		}
		private List<Tick> positiveAfter(long since)
		{
			synchronized (ticks) { return ticks.stream().filter(tick -> tick.started() >= since && tick.positiveMp()).toList(); }
		}
	}
}
