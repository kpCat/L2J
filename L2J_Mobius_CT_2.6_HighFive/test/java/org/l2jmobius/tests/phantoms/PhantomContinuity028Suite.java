/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence.Phase;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.spawns.Spawn;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec;
import org.l2jmobius.gameserver.phantoms.background.PhantomVisibleAutoPlay;
import org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope;
import org.l2jmobius.gameserver.taskmanagers.AutoPlayTaskManager.PhantomPolicy;
import org.l2jmobius.gameserver.taskmanagers.AutoPlayTaskManager.TickStatus;

/** Controlled policy-clock fixture; damage and phase completion remain actual native callbacks. */
public final class PhantomContinuity028Suite implements PhantomTestSuite
{
	private final PhantomNativeContextHandoffSuite _handoff = new PhantomNativeContextHandoffSuite();
	public static void main(String[] args) { System.exit(PhantomTestLauncher.runSuite("continuity028", new PhantomContinuity028Suite(), new PhantomTestContext(28002803, Path.of(args[0]), Path.of(args[1])))); }
	@Override public String id() { return "continuity028"; }
	@Override public void beforeAll(PhantomTestContext context) throws Exception { _handoff.beforeAll(context); }
	@Override public void afterAll(PhantomTestContext context) throws Exception { _handoff.afterAll(context); }
	@Override public void register(PhantomTestRegistry registry)
	{
		registry.add("B05-native-combat-settles-before-new-roots-without-epoch-reset", context -> settle(context, false));
		registry.add("B05-already-earned-hit-completes-during-stock-idle-settlement", context -> settle(context, true));
	}
	private static Object pendingHit(PhantomNativeWorkScope scope) throws Exception
	{
		final var monitor = scope.getClass().getDeclaredField("_monitor"); monitor.setAccessible(true);
		final var outstanding = scope.getClass().getDeclaredField("_outstanding"); outstanding.setAccessible(true);
		synchronized (monitor.get(scope))
		{
			for (Object ticket : ((Map<?, ?>) outstanding.get(scope)).values())
			{
				final var kind = ticket.getClass().getDeclaredField("_kind"); kind.setAccessible(true);
				final var state = ticket.getClass().getDeclaredField("_state"); state.setAccessible(true);
				if ("attack-hit".equals(kind.get(ticket)) && "RESERVED".equals(state.get(ticket).toString())) { return ticket; }
			}
		}
		return null;
	}
	private void settle(PhantomTestContext context, boolean earned) throws Exception
	{
		try (var f = _handoff.new Fixture(true))
		{
			f.handoff();
			final var engine = PhantomVisibleIntentRecoverySuite.engine(f);
			final var clock = new AtomicLong(System.nanoTime());
			final var adapter = new PhantomVisibleAutoPlay(f.materialization, () -> engine, f.historical::permitsDecision, clock::get);
			final var player = World.getInstance().getPlayer(f.objectId);
			final var owner = player.getNativeWorkOwner();
			final var goal = f.goals.load(f.id).orElseThrow().goal();
			final var target = new Monster(NpcData.getInstance().getTemplate(PhantomBackgroundGoalSpec.parse(goal).npcId()));
			final var spawn = new Spawn(target.getTemplate()); spawn.setXYZ(player.getX() + 48, player.getY(), player.getZ()); target.setSpawn(spawn);
			target.setCurrentHpMp(target.getMaxHp(), target.getMaxMp()); target.spawnMe(spawn.getX(), spawn.getY(), spawn.getZ());
			try
			{
				PhantomAssertions.assertTrue(adapter.start(f.id, goal), "Original native AutoPlay starts.");
				final var sessions = adapter.getClass().getDeclaredField("_sessions"); sessions.setAccessible(true);
				final var session = ((Map<?, ?>) sessions.get(adapter)).get(f.id);
				final var policyField = session.getClass().getDeclaredField("_policy"); policyField.setAccessible(true);
				final var policy = (PhantomPolicy) policyField.get(session);
				final long deadline = System.nanoTime() + 15_000_000_000L;
				while (owner.evidence().snapshot().phase() != Phase.COMBAT && System.nanoTime() < deadline) { Thread.sleep(20); }
				final var before = owner.evidence().snapshot();
				PhantomAssertions.assertEquals(Phase.COMBAT, before.phase(), "Actual native HP damage must publish the combat phase.");
				Object hit = null;
				if (earned)
				{
					final long next = System.nanoTime() + 10_000_000_000L;
					while ((hit = pendingHit((PhantomNativeWorkScope) owner)) == null && System.nanoTime() < next) { Thread.sleep(1); }
					PhantomAssertions.assertTrue(hit != null, "A real already-earned native hit ticket must precede settlement.");
				}
				clock.set(before.phaseSinceNanos() + 90_000_000_000L);
				final var paused = policy.acquireTick(player, "AutoPlay");
				if (paused.lease() != null) { paused.lease().close(); }
				PhantomAssertions.assertEquals(TickStatus.PAUSED, paused.status(), "RED: old combat may complete, but another root must wait.");
				PhantomAssertions.assertEquals(owner, player.getNativeWorkOwner(), "Original owner and epoch remain current.");
				final long completion = System.nanoTime() + 15_000_000_000L;
				while (owner.evidence().snapshot().phase() == Phase.COMBAT && System.nanoTime() < completion) { Thread.sleep(20); }
				context.record("B05.settlement", Map.of("targetHp", target.getCurrentHp(), "phase", owner.evidence().snapshot().scalarMap(), "intention", player.getAI().getIntention(), "continuation", adapter.snapshotContinuation(f.id).scalarMap()));
				PhantomAssertions.assertTrue(owner.evidence().snapshot().phase() != Phase.COMBAT, "Earned native work must reach its original passive completion.");
				PhantomAssertions.assertFalse(owner.evidence().snapshot().overflow(), "No evidence reset or hidden phase fault.");
				if (hit != null)
				{
					final var state = hit.getClass().getDeclaredField("_state"); state.setAccessible(true);
					PhantomAssertions.assertEquals("COMPLETED", state.get(hit).toString(), "Stock IDLE must deliver the already-earned hit, never cancel it.");
				}
				clock.set(System.nanoTime());
				final var resumed = policy.acquireTick(player, "AutoPlay");
				try { PhantomAssertions.assertEquals(TickStatus.ACQUIRED, resumed.status(), "Same session admits its next root after native completion."); }
				finally { if (resumed.lease() != null) { resumed.lease().close(); } }
				context.record("B05.native", owner.evidence().snapshot().scalarMap());
			}
			finally { adapter.stop(f.id); target.deleteMe(); PhantomVisibleIntentRecoverySuite.stop(engine); }
		}
	}
}
