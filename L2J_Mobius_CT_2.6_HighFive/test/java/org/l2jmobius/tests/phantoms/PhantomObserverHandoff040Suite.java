/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.util.List;

import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;

/** Focus fixtures are not a network REAL_LOGIN or a client acceptance. */
public final class PhantomObserverHandoff040Suite implements PhantomTestSuite
{
	public static void main(String[] args)
	{
		System.exit(PhantomTestLauncher.runSuite("observer-handoff040", new PhantomObserverHandoff040Suite(), new PhantomTestContext(40004001, Path.of(args[0]), Path.of(args[1]))));
	}
	@Override public String id() { return "observer-handoff040"; }
	@Override public void register(PhantomTestRegistry registry)
	{
		registry.add("O01-passive-focus-survives-absence-of-synthetic-heartbeat", _ -> autonomous());
		registry.add("O02-real-affinity-reconnect-and-stale-disconnect", _ -> reconnect());
		registry.add("O03-real-replaces-synthetic-and-tracks-instance", _ -> replacesSynthetic());
	}
	private static Class<?> focusType() throws Exception { return Class.forName("org.l2jmobius.gameserver.localplay.LocalPlayM1Observation$Focus"); }
	private static Object focus() throws Exception { return focusType().getConstructor().newInstance(); }
	private static Object candidate(int objectId, Object connection, PhantomTopologyPoint point) throws Exception
	{
		return Class.forName("org.l2jmobius.gameserver.localplay.LocalPlayM1Observation$FocusCandidate").getConstructor(int.class, Object.class, PhantomTopologyPoint.class).newInstance(objectId, connection, point);
	}
	private static Object refresh(Object focus, List<?> candidates, PhantomTopologyPoint synthetic, PhantomTopologyPoint autonomous) throws Exception
	{
		return focusType().getMethod("refresh", List.class, PhantomTopologyPoint.class, PhantomTopologyPoint.class).invoke(focus, candidates, synthetic, autonomous);
	}
	private static Object field(Object snapshot, String name) throws Exception { return snapshot.getClass().getMethod(name).invoke(snapshot); }
	private static void autonomous() throws Exception
	{
		final Object focus = focus(); final var origin = new PhantomTopologyPoint(1, 2, 3, 0);
		for (int tick = 0; tick < 3; tick++)
		{
			final var value = refresh(focus, List.of(), null, origin);
			PhantomAssertions.assertEquals("AUTONOMOUS", field(value, "mode").toString(), "Passive readiness has no Synthetic TTL/heartbeat.");
			PhantomAssertions.assertEquals(origin, field(value, "point"), "One autonomous locality point.");
			PhantomAssertions.assertEquals(0, field(value, "objectId"), "No invented REAL actor.");
		}
	}
	private static void reconnect() throws Exception
	{
		final Object focus = focus(), old = new Object(), current = new Object(), other = new Object();
		final var origin = new PhantomTopologyPoint(1, 2, 3, 0);
		refresh(focus, List.of(candidate(7, old, origin)), null, origin);
		var value = refresh(focus, List.of(candidate(3, other, origin), candidate(7, old, origin)), null, origin);
		PhantomAssertions.assertTrue(field(value, "connection") == old, "A later lower ID cannot steal current affinity.");
		value = refresh(focus, List.of(candidate(7, current, origin)), null, origin);
		final var generation = field(value, "generation");
		focusType().getMethod("disconnect", Object.class).invoke(focus, old);
		value = focusType().getMethod("snapshot").invoke(focus);
		PhantomAssertions.assertTrue(field(value, "connection") == current, "Stale disconnect cannot detach replacement connection.");
		PhantomAssertions.assertEquals(generation, field(value, "generation"), "Stale event does not change generation.");
		focusType().getMethod("disconnect", Object.class).invoke(focus, current);
		value = focusType().getMethod("snapshot").invoke(focus);
		PhantomAssertions.assertEquals("AUTONOMOUS", field(value, "mode").toString(), "Current logout returns to passive readiness.");
	}
	private static void replacesSynthetic() throws Exception
	{
		final Object focus = focus(), connection = new Object();
		final PhantomTopologyPoint autonomous = new PhantomTopologyPoint(1, 2, 3, 0), synthetic = new PhantomTopologyPoint(10, 20, 30, 0), real = new PhantomTopologyPoint(50, 60, 70, 2);
		PhantomAssertions.assertEquals("SYNTHETIC", field(refresh(focus, List.of(), synthetic, autonomous), "mode").toString(), "Truthful finite test actor mode.");
		var value = refresh(focus, List.of(candidate(7, connection, real)), synthetic, autonomous);
		PhantomAssertions.assertEquals("REAL", field(value, "mode").toString(), "REAL takes the only focus.");
		PhantomAssertions.assertEquals(real, field(value, "point"), "Current REAL instance and position.");
		final var moved = new PhantomTopologyPoint(51, 61, 71, 3);
		value = refresh(focus, List.of(candidate(7, connection, moved)), null, autonomous);
		PhantomAssertions.assertEquals(moved, field(value, "point"), "Same connection tracks instance switch.");
		PhantomAssertions.assertEquals("AUTONOMOUS", field(refresh(focus, List.of(), null, autonomous), "mode").toString(), "Logout with stopped test actor leaves one autonomous focus.");
	}
}
