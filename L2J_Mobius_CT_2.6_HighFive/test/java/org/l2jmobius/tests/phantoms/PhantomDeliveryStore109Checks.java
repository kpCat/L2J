/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundService;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.*;
/** Exact existing TASK109 STORE contracts, separated from excluded Party/PK tests. */
public final class PhantomDeliveryStore109Checks {
    public static void main(String[] args) throws Exception { storeLineage(); nativePersistenceContext(); }
	private static void storeLineage() throws Exception
	{
		final var select = PhantomBackgroundService.class.getDeclaredMethod("ownedStoreLineage", long.class, int.class, Hashes.class, PhantomBackgroundState.class, PhantomBackgroundState.class);
		select.setAccessible(true);
		final var hashes = new Hashes("knowledge", "topology", "progression", "commerce");
		final var admitted = new PhantomBackgroundState(State.MATERIALIZED, new Identity(109, 109, 0, 2, 0), new Progress(55, 0, 0, 0), new Vitals(1, 1, 1, 1, 1, 1),
			new Position(0, 10, 20, 30, 0, "farm.factual-source"), new CombatFacts(ModelKind.MELEE, 1, 1, 1, 1, 100, 100, 0, 0, 1, 1, 1, 1, 1, 1, 1), Loadout.none(),
			new InventoryFacts(List.of(), List.of(), "", 0, 1, 0, 1), List.of(), new Clock(109, 0, 0), Receipt.empty(), hashes);
		check(select.invoke(null, 109L, 109, hashes, null, admitted) == admitted, "first STORE must retain factual admitted source when durable baseline is absent");
		final var durable = admitted.after(new Progress(55, 200, 20, 0), admitted.vitals(), new Position(0, 40, 50, 60, 0, "farm.durable-source"), admitted.inventory(), admitted.autoGetSkills(), admitted.clock(), admitted.receipt());
		check(select.invoke(null, 109L, 109, hashes, durable, admitted) == durable, "current durable progress/source must win over initial lineage");
		check(select.invoke(null, 109L, 109, hashes, null, null) == null, "missing factual admission cannot be replaced by intended or nearest anchor");
		for (Object[] invalid : new Object[][] {{110L, 109, hashes, null, admitted}, {109L, 110, hashes, null, admitted}, {109L, 109, new Hashes("other", "topology", "progression", "commerce"), null, admitted}, {109L, 109, hashes, null, admitted.withState(State.READY)}})
		{
			try { select.invoke(null, invalid); throw new AssertionError("foreign or stale initial source accepted"); }
			catch (InvocationTargetException expected) { check(expected.getCause() instanceof IllegalStateException, "source rejection must fail closed"); }
		}
		check(admitted.position().equals(new Position(0, 10, 20, 30, 0, "farm.factual-source")) && durable.progress().experience() == 200, "lineage selection must mutate neither actual source nor durable earned progress");
		final var captureFirst = PhantomBackgroundService.class.getDeclaredMethod("optionalFirstStoreCapture", PhantomBackgroundState.class, java.util.function.Supplier.class);
		captureFirst.setAccessible(true);
		final java.util.function.Supplier<Object> offAnchor = () -> { throw new IllegalArgumentException("physical source is not exactly one anchor"); };
		check(captureFirst.invoke(null, null, offAnchor) == null, "strict first lineage refusal must leave lineage absent and allow mandatory factual STORE");
		for (Object[] input : new Object[][] {{durable, offAnchor}, {null, (java.util.function.Supplier<Object>) () -> { throw new IllegalStateException("SQL/CAS/owner failure"); }}})
		{
			try { captureFirst.invoke(null, input); throw new AssertionError("durable or mandatory failure suppressed"); }
			catch (InvocationTargetException expected) { check(expected.getCause() instanceof RuntimeException, "mandatory rejection propagates"); }
		}
		final var nativeVerifier = org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction.class.getMethod("verifyNativeOnlyStore", long.class, int.class, org.l2jmobius.gameserver.model.actor.Player.OwnedStoreSnapshot.class, String.class);
		final var transaction = new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction(() -> { throw new java.sql.SQLException("mandatory SQL unavailable", "08000"); }, org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction.ObjectIdAllocator.production(), point -> { });
		final var snapshot = new org.l2jmobius.gameserver.model.actor.Player.OwnedStoreSnapshot(1, 1, 1, 1, 1, 1, 41, 52, 63, 0, 55, 200, 20, 0, 2, 0, 0, 55, 200, 20, 1);
		try { nativeVerifier.invoke(transaction, 109L, 109, snapshot, "inventory-witness"); throw new AssertionError("native-only SQL failure accepted"); }
		catch (InvocationTargetException expected) { check(expected.getCause() instanceof IllegalStateException && expected.getCause().getCause() instanceof java.sql.SQLException, "native-only SQL failure must propagate"); }
		System.out.println("TASK109_STORE_LINEAGE_PASS positive=4 negative=7 DB=false nativeAcceptance=false");
	}

	private static void nativePersistenceContext() throws Exception
	{
		final var allowed = org.l2jmobius.gameserver.phantoms.background.L2jPhantomBackgroundAuthority.class.getDeclaredMethod("persistenceContextAllowed", boolean.class, boolean.class, int.class);
		allowed.setAccessible(true);
		check((boolean) allowed.invoke(null, true, true, 0), "native STORE must retain earned combat facts");
		check(!(boolean) allowed.invoke(null, true, true, 1234) && !(boolean) allowed.invoke(null, true, false, 1234), "partial delivery must preserve supported-goal karma refusal");

		check((boolean) allowed.invoke(null, false, false, 0), "ordinary simulation context preserved");
		check(!(boolean) allowed.invoke(null, false, true, 0), "combat cannot become scalar simulation");
		check(!(boolean) allowed.invoke(null, false, false, 1234), "PK karma cannot become scalar simulation");
		System.out.println("TASK109_NATIVE_PERSISTENCE_CONTEXT_PASS checks=5 DB=false nativeAcceptance=false");
	}


private static void check(boolean value, String reason) { if (!value) { throw new AssertionError(reason); } }
}
