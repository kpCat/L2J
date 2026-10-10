/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.phantoms.background.PhantomVisibleAutoPlay;
import org.l2jmobius.gameserver.taskmanagers.AutoPlayTaskManager;
import org.l2jmobius.gameserver.taskmanagers.AutoUseTaskManager;

/** Exact native owner, real pool invocations and an actual checkpoint control boundary. */
public final class PhantomAutoPlayOwnership022Suite implements PhantomTestSuite
{
	final PhantomNativeContextHandoffSuite handoff = new PhantomNativeContextHandoffSuite();
	public static void main(String[] args)
	{
		System.exit(PhantomTestLauncher.runSuite("autoplay-ownership022", new PhantomAutoPlayOwnership022Suite(), new PhantomTestContext(22002202, Path.of(args[0]), Path.of(args[1]))));
	}
	@Override public String id() { return "autoplay-ownership022"; }
	@Override public void beforeAll(PhantomTestContext context) throws Exception { handoff.beforeAll(context); }
	@Override public void afterAll(PhantomTestContext context) throws Exception { handoff.afterAll(context); }
	@Override public void register(PhantomTestRegistry registry)
	{
		if ("preference031".equals(System.getProperty("phantom.m1.native.focus")))
		{
			registry.add("S06-stock-selection-prefers-alternative-and-preserves-cooperative-fallback", this::preference); return;
		}
		registry.add("S01-live-state-is-independent-from-cached-decision", context ->
		{
			try (var f = handoff.new Fixture(true))
			{
				f.handoff(); final var engine = PhantomVisibleIntentRecoverySuite.engine(f);
				final var adapter = new PhantomVisibleAutoPlay(f.materialization, () -> engine, f.historical::permitsDecision);
				final var player = World.getInstance().getPlayer(f.objectId); player.setSitting(true);
				try
				{
					PhantomAssertions.assertTrue(adapter.start(f.id, f.goals.load(f.id).orElseThrow().goal()), "Native pair starts with current goal.");
					player.setAutoPlaying(false);
					final var sample = snapshot(adapter, f.id);
					PhantomAssertions.assertEquals("false", sample.get("liveAutoPlay"), "RED: fresh native flag cannot be inferred from cached reason.");
					PhantomAssertions.assertEquals("true", sample.get("liveAutoPlayRegistered"), "Exact registration is distinct from native flag.");
					context.record("S01.fresh", sample);
				}
				finally { adapter.stop(f.id); player.setSitting(false); PhantomVisibleIntentRecoverySuite.stop(engine); }
			}
		});
		registry.add("S03-real-checkpoint-pauses-and-reopens-same-registration", context ->
		{
			try (var f = handoff.new Fixture(true))
			{
				f.handoff(); final var engine = PhantomVisibleIntentRecoverySuite.engine(f);
				final var adapter = new PhantomVisibleAutoPlay(f.materialization, () -> engine, f.historical::permitsDecision);
				final var player = World.getInstance().getPlayer(f.objectId); player.setSitting(true);
				final var inside = new CountDownLatch(1); final var release = new CountDownLatch(1); final var done = new CountDownLatch(1); final var failure = new AtomicReference<Throwable>();
				final Thread checkpoint = new Thread(() ->
				{
					try { player.getNativeWorkOwner().checkpoint(() -> { inside.countDown(); try { if (!release.await(4, TimeUnit.SECONDS)) { throw new AssertionError("TEST checkpoint release timeout"); } } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); } return null; }); }
					catch (Throwable e) { failure.set(e); }
					finally { done.countDown(); }
				}, "TEST022-checkpoint-control");
				try
				{
					final var goal = f.goals.load(f.id).orElseThrow().goal();
					PhantomAssertions.assertTrue(adapter.start(f.id, goal), "Native pair starts.");
					final var policy = policy(adapter, f.id);
					checkpoint.start(); PhantomAssertions.assertTrue(inside.await(5, TimeUnit.SECONDS), "Actual checkpoint sealed admission.");
					PhantomAssertions.assertTrue(f.materialization.tryAcquireAction(f.id).isEmpty(), "No ordinary work admitted under checkpoint.");
					Thread.sleep(1600); // More than two real AutoPlay and five AutoUse pool periods.
					final boolean pairRetained = AutoPlayTaskManager.getInstance().hasPhantomRegistration(player, policy) && AutoUseTaskManager.getInstance().hasPhantomRegistration(player, policy);
					context.record("S03.paused", "samePair=" + pairRetained + ";owner=" + ((org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope) player.getNativeWorkOwner()).snapshot());
					release.countDown(); PhantomAssertions.assertTrue(done.await(5, TimeUnit.SECONDS) && failure.get() == null, "Checkpoint actually completed successfully: " + failure.get());
					PhantomAssertions.assertTrue(pairRetained && adapter.running(f.id, goal), "RED: temporary checkpoint must not revoke native registration; same owner continues without restart.");
					PhantomAssertions.assertEquals(f.loadedEpoch, player.getNativeWorkOwner().epoch(), "Checkpoint preserves lifetime.");
				}
				finally { release.countDown(); if (checkpoint.isAlive()) { checkpoint.join(5000); } adapter.stop(f.id); player.setSitting(false); PhantomVisibleIntentRecoverySuite.stop(engine); }
			}
		});
		registry.add("S04-stale-stop-cannot-remove-successor-and-revocation-is-final", _ ->
		{
			try (var f = handoff.new Fixture(true))
			{
				f.handoff(); final var engine = PhantomVisibleIntentRecoverySuite.engine(f);
				final var adapter = new PhantomVisibleAutoPlay(f.materialization, () -> engine, f.historical::permitsDecision);
				final var player = World.getInstance().getPlayer(f.objectId); player.setSitting(true);
				try
				{
					final var goal = f.goals.load(f.id).orElseThrow().goal();
					PhantomAssertions.assertTrue(adapter.start(f.id, goal), "Original pair starts."); final var old = policy(adapter, f.id);
					player.setAutoPlaying(false); PhantomAssertions.assertTrue(adapter.start(f.id, goal), "Replacement pair starts."); final var successor = policy(adapter, f.id);
					final var oldLease = old.acquire(player);
					if (oldLease != null) { oldLease.close(); }
					PhantomAssertions.assertTrue(oldLease == null, "RED: queued old policy must not acquire ordinary work for a successor session with the same goal.");
					PhantomAssertions.assertFalse(AutoPlayTaskManager.getInstance().stopPhantomAutoPlay(player, old), "Old expected policy cannot stop new AutoPlay.");
					PhantomAssertions.assertFalse(AutoUseTaskManager.getInstance().stopPhantomAutoUse(player, old), "Old expected policy cannot stop new AutoUse.");
					PhantomAssertions.assertTrue(AutoPlayTaskManager.getInstance().hasPhantomRegistration(player, successor) && AutoUseTaskManager.getInstance().hasPhantomRegistration(player, successor), "Both exact successor memberships survive.");
					f.historical.revokeForegroundDecisions(); Thread.sleep(1600);
					PhantomAssertions.assertFalse(AutoPlayTaskManager.getInstance().hasPhantomRegistration(player, successor) || AutoUseTaskManager.getInstance().hasPhantomRegistration(player, successor), "Revoked owner must not become a temporary pause.");
				}
				finally { adapter.stop(f.id); player.setSitting(false); PhantomVisibleIntentRecoverySuite.stop(engine); }
			}
		});
		registry.add("S05-live-peer-target-remains-eligible-for-cooperative-native-farm", context ->
		{
			try (var first = handoff.new Fixture(true); var second = handoff.new Fixture(true, false, 0, org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction.FaultInjector.none(), true))
			{
				first.handoff(); second.handoff();
				final var engine = PhantomVisibleIntentRecoverySuite.engine(first); final var peerEngine = PhantomVisibleIntentRecoverySuite.engine(second);
				final var adapter = new PhantomVisibleAutoPlay(first.materialization, () -> engine, first.historical::permitsDecision);
				final var peerAdapter = new PhantomVisibleAutoPlay(second.materialization, () -> peerEngine, second.historical::permitsDecision);
				final var player = World.getInstance().getPlayer(first.objectId); final var peer = World.getInstance().getPlayer(second.objectId);
				player.setSitting(true); peer.setSitting(true);
				final var goal = first.goals.load(first.id).orElseThrow().goal();
				final var target = new org.l2jmobius.gameserver.model.actor.instance.Monster(org.l2jmobius.gameserver.data.xml.NpcData.getInstance().getTemplate(org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec.parse(goal).npcId()));
				final var spawn = new org.l2jmobius.gameserver.model.spawns.Spawn(target.getTemplate()); spawn.setXYZ(player.getX() + 48, player.getY(), player.getZ()); target.setSpawn(spawn);
				target.setCurrentHpMp(target.getMaxHp(), target.getMaxMp()); target.spawnMe(spawn.getX(), spawn.getY(), spawn.getZ());
				try
				{
					PhantomAssertions.assertTrue(adapter.start(first.id, goal) && peerAdapter.start(second.id, second.goals.load(second.id).orElseThrow().goal()), "Both real owned native sessions start.");
					// Production has one adapter. Publish its second actually admitted session in the policy fixture.
					sessions(adapter).put(second.id, sessions(peerAdapter).get(second.id));
					final var policy = policy(adapter, first.id); final var owner = player.getNativeWorkOwner();
					player.setTarget(null); peer.setTarget(target); peer.setSitting(false);
					PhantomAssertions.assertTrue(policy.permitsTarget(target), "RED: a living current peer selection must preserve lawful cooperative native target eligibility.");
					player.setTarget(target);
					PhantomAssertions.assertTrue(policy.permitsTarget(target), "An existing selection is retained; earned native work is not cancelled by peer observation.");
					player.setTarget(null); peer.setSitting(true);
					PhantomAssertions.assertTrue(policy.permitsTarget(target), "A resting peer cannot indefinitely reserve a target.");
					peer.setSitting(false); peerAdapter.stop(second.id);
					PhantomAssertions.assertTrue(policy.permitsTarget(target), "A stopped or superseded session cannot reserve a target.");
					PhantomAssertions.assertEquals(owner, player.getNativeWorkOwner(), "Target preference never replaces native ownership.");
					context.record("S05.actual", "realOwners=true;cooperativeSelection=true;existingSelection=true;restingEligible=true;stoppedEligible=true");
				}
				finally { sessions(adapter).remove(second.id); adapter.stop(first.id); peerAdapter.stop(second.id); player.setSitting(false); peer.setSitting(false); target.deleteMe(); PhantomVisibleIntentRecoverySuite.stop(engine); PhantomVisibleIntentRecoverySuite.stop(peerEngine); }
			}
		});
	}
	private void preference(PhantomTestContext context) throws Exception
	{
		try (var first = handoff.new Fixture(true); var second = handoff.new Fixture(true, false, 0, org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction.FaultInjector.none(), true))
		{
			first.handoff(); second.handoff();
			final var engine = PhantomVisibleIntentRecoverySuite.engine(first); final var peerEngine = PhantomVisibleIntentRecoverySuite.engine(second);
			final var adapter = new PhantomVisibleAutoPlay(first.materialization, () -> engine, first.historical::permitsDecision);
			final var peerAdapter = new PhantomVisibleAutoPlay(second.materialization, () -> peerEngine, second.historical::permitsDecision);
			final var player = World.getInstance().getPlayer(first.objectId); final var peer = World.getInstance().getPlayer(second.objectId);
			player.setSitting(true); peer.setSitting(true);
			final var goal = first.goals.load(first.id).orElseThrow().goal();
			final int npcId = org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec.parse(goal).npcId();
			final int instance = org.l2jmobius.gameserver.managers.InstanceManager.getInstance().createDynamicInstance(0).getId();
			player.setInstanceId(instance); peer.setInstanceId(instance);
			final var nearby = preferenceMonster(player, npcId, 48); final var alternative = preferenceMonster(player, npcId, 160);
			try
			{
				PhantomAssertions.assertTrue(adapter.start(first.id, goal) && peerAdapter.start(second.id, second.goals.load(second.id).orElseThrow().goal()), "Both original owned native sessions start.");
				sessions(adapter).put(second.id, sessions(peerAdapter).get(second.id));
				final var policy = policy(adapter, first.id); final var owner = player.getNativeWorkOwner();
				player.getAutoUseSettings().getAutoSkills().clear(); peer.getAutoUseSettings().getAutoSkills().clear();
				peer.setTarget(nearby); peer.setSitting(false); player.setTarget(null); player.setSitting(false);
				stockSelection(player);
				context.record("S06.firstSelection", "selected=" + player.getTarget() + ";near=" + nearby.getObjectId() + ";alternative=" + alternative.getObjectId());
				PhantomAssertions.assertEquals(alternative, player.getTarget(), "RED: actual stock Phantom selection must prefer a reachable free alternative over a peer's current nearest target.");
				PhantomAssertions.assertTrue(policy.permitsTarget(nearby), "Preference must never revoke cooperative target admission.");
				player.setSitting(true); player.abortAttack(); player.abortCast(); player.getAI().setIntention(org.l2jmobius.gameserver.ai.Intention.IDLE); alternative.deleteMe();
				player.setTarget(null); player.setSitting(false); stockSelection(player);
				PhantomAssertions.assertEquals(nearby, player.getTarget(), "With no free alternative actual stock selection still permits cooperative native farm.");
				PhantomAssertions.assertEquals(owner, player.getNativeWorkOwner(), "Selection keeps the exact original native lifetime.");
				context.record("S06.actual", "stockAutoPlay=true;alternativeSelected=true;cooperativeFallback=true;originalOwner=true");
			}
			finally { sessions(adapter).remove(second.id); adapter.stop(first.id); peerAdapter.stop(second.id); player.setSitting(false); peer.setSitting(false); nearby.deleteMe(); alternative.deleteMe(); player.setInstanceId(0); peer.setInstanceId(0); org.l2jmobius.gameserver.managers.InstanceManager.getInstance().destroyInstance(instance); PhantomVisibleIntentRecoverySuite.stop(engine); PhantomVisibleIntentRecoverySuite.stop(peerEngine); }
		}
	}
	private static org.l2jmobius.gameserver.model.actor.instance.Monster preferenceMonster(org.l2jmobius.gameserver.model.actor.Player player, int npcId, int offset) throws Exception
	{
		final var target = new org.l2jmobius.gameserver.model.actor.instance.Monster(org.l2jmobius.gameserver.data.xml.NpcData.getInstance().getTemplate(npcId));
		final var spawn = new org.l2jmobius.gameserver.model.spawns.Spawn(target.getTemplate()); spawn.setXYZ(player.getX() + offset, player.getY(), player.getZ()); target.setSpawn(spawn);
		target.setInstanceId(player.getInstanceId()); target.setCurrentHpMp(target.getMaxHp(), target.getMaxMp()); target.spawnMe(spawn.getX(), spawn.getY(), spawn.getZ()); return target;
	}
	/** Runs the original stock pool body with the actual registered policy and its native lease. */
	private static void stockSelection(org.l2jmobius.gameserver.model.actor.Player player) throws Exception
	{
		final var type = java.util.Arrays.stream(AutoPlayTaskManager.class.getDeclaredClasses()).filter(value -> value.getSimpleName().equals("AutoPlay")).findFirst().orElseThrow();
		final var constructor = type.getDeclaredConstructor(AutoPlayTaskManager.class, java.util.Set.class); constructor.setAccessible(true);
		((Runnable) constructor.newInstance(AutoPlayTaskManager.getInstance(), java.util.Set.of(player))).run();
	}
	static AutoPlayTaskManager.PhantomPolicy policy(PhantomVisibleAutoPlay adapter, long id) throws Exception
	{
		final var sessionsField = adapter.getClass().getDeclaredField("_sessions"); sessionsField.setAccessible(true);
		final var session = ((java.util.Map<?, ?>) sessionsField.get(adapter)).get(id);
		final var policyField = session.getClass().getDeclaredField("_policy"); policyField.setAccessible(true);
		return (AutoPlayTaskManager.PhantomPolicy) policyField.get(session);
	}
	@SuppressWarnings("unchecked")
	static java.util.Map<Long, Object> sessions(PhantomVisibleAutoPlay adapter) throws Exception
	{
		final var field = adapter.getClass().getDeclaredField("_sessions"); field.setAccessible(true);
		return (java.util.Map<Long, Object>) field.get(adapter);
	}
	@SuppressWarnings("unchecked")
	static java.util.Map<String, String> snapshot(PhantomVisibleAutoPlay adapter, long id) throws Exception
	{
		try { final var sample = adapter.getClass().getMethod("snapshotContinuation", long.class).invoke(adapter, id); return (java.util.Map<String, String>) sample.getClass().getMethod("scalarMap").invoke(sample); }
		catch (NoSuchMethodException absent) { throw new AssertionError("RED: non-mutating continuation snapshot is absent."); }
	}
}
