/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundService;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState;
import org.l2jmobius.gameserver.phantoms.background.PhantomNativeContext;
import org.l2jmobius.gameserver.phantoms.background.PhantomOwnedStoreIntent;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;

/** The existing cold recovery fixture verifies the confirmed return boundary, not a late actor sample. */
public final class PhantomRecoveryBoundary026Suite implements PhantomTestSuite
{
	private final PhantomNativeContextHandoffSuite _native = new PhantomNativeContextHandoffSuite();
	public static void main(String[] args)
	{
		System.exit(PhantomTestLauncher.runSuite("recovery-boundary026", new PhantomRecoveryBoundary026Suite(), new PhantomTestContext(26002603, Path.of(args[0]), Path.of(args[1]))));
	}
	@Override public String id() { return "recovery-boundary026"; }
	@Override public void beforeAll(PhantomTestContext context) throws Exception { _native.beforeAll(context); }
	@Override public void afterAll(PhantomTestContext context) throws Exception { _native.afterAll(context); }
	@Override public void register(PhantomTestRegistry registry)
	{
		registry.add("R03-confirmed-cold-result-before-ordinary-admission", context -> boundary(context, false));
		registry.add("R03-observer-failure-cannot-reverse-confirmed-recovery", context -> boundary(context, true));
	}
	private void boundary(PhantomTestContext context, boolean observerFailure) throws Exception
	{
		try (var f = _native.new Fixture())
		{
			final var goal = f.goals.load(f.id).orElseThrow().goal();
			final int points;
			try (var connection = DatabaseFactory.getConnection(); var statement = connection.prepareStatement("SELECT vitality_points FROM characters WHERE charId=?"))
			{
				statement.setInt(1, f.objectId);
				try (var rows = statement.executeQuery()) { PhantomAssertions.assertTrue(rows.next(), "Exact owned native fixture."); points = rows.getInt(1); }
			}
			final var capture = new PhantomNativeContext.Capture(points, points == 1 ? PhantomNativeContext.Eligibility.SUPPORTED : PhantomNativeContext.Eligibility.VITALITY_REQUIRES_NATIVE);
			PhantomAssertions.assertTrue(f.transactions.prepareOwnedStore(f.baseline, goal, 26002603, PhantomBackgroundState.State.MATERIALIZED, capture).successful(), "Existing cold prepared receipt.");
			final var hook = PhantomBackgroundService.class.getDeclaredField("_recoveryObserver"); hook.setAccessible(true);
			final var identitiesField = PhantomBackgroundService.class.getDeclaredField("_identities"); identitiesField.setAccessible(true);
			final var identities = (PhantomIdentityLeaseRegistry) identitiesField.get(f.background);
			final Object previous = hook.get(null); final var calls = new AtomicInteger();
			try
			{
				hook.set(null, (BiConsumer<Long, PhantomBackgroundState>) (profile, state) ->
				{
					PhantomAssertions.assertEquals(f.id, profile.longValue(), "Exact recovered argument identity.");
					PhantomAssertions.assertEquals(PhantomIdentityLeaseRegistry.OwnerKind.BACKGROUND, identities.getOwnerKind(f.objectId), "Absent ownership still fences ordinary admission.");
					PhantomAssertions.assertTrue(World.getInstance().findObject(f.objectId) == null && f.materialization.find(f.id).isEmpty(), "No ordinary Player admitted before observer.");
					PhantomAssertions.assertTrue(PhantomProfileRepository.open().findComponent(f.id, PhantomOwnedStoreIntent.COMPONENT_TYPE).isEmpty(), "A separate SQL connection sees the committed resolved receipt.");
					PhantomAssertions.assertEquals(state, f.transactions.load(f.id).state(), "Exact immutable argument equals confirmed durable result.");
					PhantomAssertions.assertEquals(f.baseline.position(), state.position(), "Native XYZ is preserved without anchor substitution.");
					PhantomAssertions.assertEquals(f.baseline.progress(), state.progress(), "Recovery awards nothing.");
					PhantomAssertions.assertEquals(f.baseline.inventory(), state.inventory(), "Full native inventory is preserved.");
					calls.incrementAndGet();
					if (observerFailure) { throw new IllegalStateException("TASK026_OBSERVER_FAILURE_AFTER_COMMIT"); }
				});
				PhantomAssertions.assertEquals(PhantomBackgroundService.OperationStatus.SUCCESS, f.background.recoverAbandonedMaterialization(f.id).status(), "Confirmed recovery remains successful.");
				PhantomAssertions.assertEquals(1, calls.get(), "Exact boundary observed once with all assertions completed.");
				context.record("R03.boundary." + observerFailure, "confirmed=true;receiptAbsent=true;admissionFenced=true;XYZ=exact;observerCalls=1");
			}
			finally { hook.set(null, previous); }
		}
	}
}
