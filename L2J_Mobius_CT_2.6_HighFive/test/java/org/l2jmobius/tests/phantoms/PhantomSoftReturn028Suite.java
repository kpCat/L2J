/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.util.Map;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongPredicate;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomMaterializationRetentionPolicy;
import org.l2jmobius.gameserver.phantoms.activity.PhantomMaterializationRetentionPolicy.Facts;
import org.l2jmobius.gameserver.phantoms.background.PhantomVisibleAutoPlay;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundService;
import org.l2jmobius.gameserver.phantoms.topology.PhantomHumanLocalityControl;
import org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyService;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.taskmanagers.AutoPlayTaskManager.PhantomPolicy;
import org.l2jmobius.gameserver.taskmanagers.AutoPlayTaskManager.TickStatus;

/** Root admission uses the existing native pools; current leases and retention remain protected. */
public final class PhantomSoftReturn028Suite implements PhantomTestSuite
{
	private final PhantomNativeContextHandoffSuite _handoff = new PhantomNativeContextHandoffSuite();
	public static void main(String[] args)
	{
		System.exit(PhantomTestLauncher.runSuite("soft-return028", new PhantomSoftReturn028Suite(), new PhantomTestContext(28002802, Path.of(args[0]), Path.of(args[1]))));
	}
	@Override public String id() { return "soft-return028"; }
	@Override public void beforeAll(PhantomTestContext context) throws Exception { _handoff.beforeAll(context); }
	@Override public void afterAll(PhantomTestContext context) throws Exception { _handoff.afterAll(context); }
	@Override public void register(PhantomTestRegistry registry)
	{
		registry.add("B08-locality-grace-bounds-new-roots-without-removing-hard-holds", this::retention);
		registry.add("L02-native-pool-pauses-only-new-root-and-keeps-admitted-lease", this::nativePool);
		registry.add("L02-native-context-relevance-withdraw-keeps-simulation-gate", this::nativeContextDemand);
	}
	private void nativeContextDemand(PhantomTestContext context) throws Exception
	{
		try (var f = _handoff.new Fixture(true))
		{
			final var goal = f.goals.load(f.id).orElseThrow().goal();
			final var before = f.transactions.nativeContext(f.id, f.objectId);
			PhantomAssertions.assertFalse(before.simulationEligible(), "Actual fixture needs native context.");
			PhantomAssertions.assertEquals(PhantomBackgroundService.DirectiveKind.NATIVE_REQUIRED, f.background.directive(f.id, goal, PhantomActivityState.BACKGROUND).kind(), "Native simulation guard remains closed.");
			final var signals = f.background.getClass().getDeclaredField("_nativeContextSignals"); signals.setAccessible(true);
			final var initial = ((Map<?, ?>) signals.get(f.background)).get(f.id);
			final var requested = initial.getClass().getDeclaredMethod("requested"); requested.setAccessible(true);
			PhantomAssertions.assertTrue((boolean) requested.invoke(initial), "Original native relevance is requested by default.");
			final var retiring = f.background.getClass().getMethod("nativeContextRetiring", long.class);
			PhantomAssertions.assertFalse((boolean) retiring.invoke(f.background, f.id), "Initial native-context bootstrap must remain eligible.");
			final var install = f.background.getClass().getMethod("installNativeContextDemand", LongPredicate.class);
			final var demand = new AtomicBoolean(false);
			install.invoke(f.background, (LongPredicate) _ -> demand.get());
			PhantomAssertions.assertEquals(PhantomBackgroundService.DirectiveKind.NATIVE_REQUIRED, f.background.directive(f.id, goal, PhantomActivityState.BACKGROUND).kind(), "Withdrawal never permits unsupported simulation.");
			final var retired = ((Map<?, ?>) signals.get(f.background)).get(f.id);
			PhantomAssertions.assertFalse((boolean) requested.invoke(retired), "RED: no demand withdraws the self-pinning ACTIVE source.");
			PhantomAssertions.assertTrue((boolean) retiring.invoke(f.background, f.id), "Retire intent survives native removal in the existing signal binding.");
			PhantomAssertions.assertEquals(before, f.transactions.nativeContext(f.id, f.objectId), "Relevance withdrawal changes no native context, reward or canonical state.");
			demand.set(true);
			f.background.directive(f.id, goal, PhantomActivityState.BACKGROUND);
			PhantomAssertions.assertTrue((boolean) requested.invoke(((Map<?, ?>) signals.get(f.background)).get(f.id)), "New human/hard demand admits the same source again.");
			PhantomAssertions.assertFalse((boolean) retiring.invoke(f.background, f.id), "Human return closes the finite retire intent.");
			context.record("L02.native_context", f.background.nativeContextSignalDelivery(f.id));
		}
	}
	private void retention(PhantomTestContext context) throws Exception
	{
		final var clock = new AtomicLong(1_000_000_000L);
		final var local = new AtomicBoolean(true);
		final var hard = new AtomicBoolean(false);
		final var policy = new PhantomMaterializationRetentionPolicy(_ -> new Facts(true, local.get(), false, hard.get(), true), clock::get, 60_000);
		final var admission = policy.getClass().getMethod("permitsNewActions", long.class);
		PhantomAssertions.assertTrue((boolean) admission.invoke(policy, 1L), "Human demand admits original roots.");
		local.set(false); clock.addAndGet(59_000_000_000L);
		PhantomAssertions.assertTrue((boolean) admission.invoke(policy, 1L), "Original recent-human grace remains60s.");
		clock.addAndGet(1_000_000_000L);
		PhantomAssertions.assertFalse((boolean) admission.invoke(policy, 1L), "RED: self-generated ACTIVE_ACTION cannot renew new root admission.");
		PhantomAssertions.assertTrue(policy.observe(1).hard(), "Existing active native work still retains its actor.");
		hard.set(true);
		PhantomAssertions.assertTrue((boolean) admission.invoke(policy, 1L), "Real-party hard hold remains protected.");
		context.record("B08.retention", policy.observe(1));
		final var backend = new PhantomTopologyCoreSuite.TestBackend();
		final var signals = new PhantomRelevanceSignalPort()
		{
			@Override public SignalDelivery submit(long id, org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal signal) { throw new AssertionError("Physical fact must not submit."); }
			@Override public SignalDelivery withdraw(long id, String source, long sequence) { throw new AssertionError("Physical fact must not withdraw."); }
		};
		final var topology = PhantomTopologyService.fromSnapshotForTesting(PhantomTopologyCoreSuite.snapshot(backend), backend, PhantomTopologyCoreSuite.POLICY, signals);
		PhantomAssertions.assertTrue(topology.start(), "Existing topology fixture starts.");
		try
		{
			topology.registerProfile(1); topology.updateProfile(1, PhantomTopologyCoreSuite.LEFT_POINT, 1);
			final var humans = new AtomicReference<List<PhantomTopologyPoint>>(List.of(PhantomTopologyCoreSuite.LEFT_POINT));
			final var locality = new PhantomHumanLocalityControl(topology, signals, humans::get, System::currentTimeMillis, _ -> false);
			PhantomAssertions.assertFalse(locality.isLocal(1), "Online readiness remains fenced.");
			final var physical = locality.getClass().getMethod("hasPhysicalDemand", long.class);
			PhantomAssertions.assertTrue((boolean) physical.invoke(locality, 1L), "RED: native human geometry survives online/readiness dependency.");
			humans.set(List.of());
			PhantomAssertions.assertFalse((boolean) physical.invoke(locality, 1L), "Actual human departure removes physical demand.");
			PhantomAssertions.assertFalse((boolean) physical.invoke(locality, 2L), "Unknown identity is never invented.");
		}
		finally { topology.beginStop(); topology.finishStop(); }
	}
	private void nativePool(PhantomTestContext context) throws Exception
	{
		try (var f = _handoff.new Fixture(true))
		{
			f.handoff();
			final var engine = PhantomVisibleIntentRecoverySuite.engine(f);
			final var roots = new AtomicBoolean(true);
			final var constructor = PhantomVisibleAutoPlay.class.getConstructor(PhantomMaterializationService.class, Supplier.class, LongPredicate.class, LongPredicate.class, LongSupplier.class);
			final var adapter = constructor.newInstance(f.materialization, (Supplier<?>) () -> engine, (LongPredicate) f.historical::permitsDecision, (LongPredicate) _ -> roots.get(), (LongSupplier) System::nanoTime);
			final var player = World.getInstance().getPlayer(f.objectId);
			final var owner = player.getNativeWorkOwner();
			final var goal = f.goals.load(f.id).orElseThrow().goal();
			try
			{
				PhantomAssertions.assertTrue(adapter.start(f.id, goal), "Original native session starts.");
				final var sessionsField = adapter.getClass().getDeclaredField("_sessions"); sessionsField.setAccessible(true);
				final var session = ((Map<?, ?>) sessionsField.get(adapter)).get(f.id);
				final var policyField = session.getClass().getDeclaredField("_policy"); policyField.setAccessible(true);
				final var policy = (PhantomPolicy) policyField.get(session);
				final var admitted = policy.acquireTick(player, "AutoPlay");
				PhantomAssertions.assertEquals(TickStatus.ACQUIRED, admitted.status(), "Native original tick admitted before retire intent.");
				try
				{
					roots.set(false);
					final var paused = policy.acquireTick(player, "AutoPlay");
					PhantomAssertions.assertEquals(TickStatus.PAUSED, paused.status(), "RED: new root pauses without revoking original policy.");
					PhantomAssertions.assertEquals(null, paused.lease(), "Paused tick publishes no native root.");
					PhantomAssertions.assertTrue(adapter.running(f.id, goal), "Exact admitted session remains current during earned completion.");
					PhantomAssertions.assertEquals(owner, player.getNativeWorkOwner(), "No replacement owner or epoch.");
					PhantomAssertions.assertFalse(adapter.noTargetExpired(f.id, goal), "Retirement does not trigger another offscreen repair/replan.");
				}
				finally { admitted.lease().close(); }
				context.record("L02.paused", adapter.snapshotContinuation(f.id).scalarMap());
			}
			finally { adapter.stop(f.id); PhantomVisibleIntentRecoverySuite.stop(engine); }
		}
	}
}
