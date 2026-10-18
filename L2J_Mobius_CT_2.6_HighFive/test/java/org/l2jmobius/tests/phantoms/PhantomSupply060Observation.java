/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.util.concurrent.TimeUnit;

import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceipt;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceiptStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateCodec;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;

/** Observes existing native consequences; no next goal, route, target or reward assignment. */
final class PhantomSupply060Observation
{
	static void bindingModels(PhantomTestContext context)
	{
		final var binding = new org.l2jmobius.gameserver.phantoms.commerce.PhantomSupplyIntentStore.Binding(3, 123, 0, 0, 456, new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(-88000, 240000, -3600, 0), 0);
		final var root = modelGoal("farm.background", "farm.background", 3, null);
		final var child = modelGoal("maintain.supplies", "supplies.autonomous", 0, binding.encode());
		final var codec = new PhantomGoalStateCodec();
		PhantomAssertions.assertEquals(child, codec.decode(codec.encode(child)), "Supply child did not roundtrip the existing bounded Goal codec.");
		PhantomAssertions.assertTrue(org.l2jmobius.gameserver.phantoms.commerce.PhantomSupplyIntentStore.bound(child, root), "Exact supply child lost its farm root.");
		PhantomAssertions.assertFalse(org.l2jmobius.gameserver.phantoms.commerce.PhantomSupplyIntentStore.bound(child, modelGoal("farm.background", "farm.background", 4, null)), "Old child authorized a newer root revision.");
		PhantomAssertions.assertFalse(org.l2jmobius.gameserver.phantoms.commerce.PhantomSupplyIntentStore.bound(child, modelGoal("private.store.buy", "private.store.buy", 3, null)), "Supply child authorized a different root family.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> org.l2jmobius.gameserver.phantoms.commerce.PhantomSupplyIntentStore.Binding.parse(modelGoal("maintain.supplies", "supplies.autonomous", 0, binding.encode().replace("s1:", "s2:"))), "Unknown binding version accepted.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new org.l2jmobius.gameserver.phantoms.commerce.PhantomSupplyIntentStore.Binding(3, 123, 0, 0, 0, binding.field(), 0), "Missing owned weapon accepted.");
		context.record("supply060.binding", "existing Goal codec; exact root revision; root family; unknown binding version; owned weapon bound; no Player/DB mutation");
	}

	static void policyModels(PhantomTestContext context)
	{
		final var policy = org.l2jmobius.gameserver.phantoms.commerce.PhantomAutonomousSupplyPolicy.load(java.nio.file.Path.of("data/phantoms/life/high-five-autonomous-supply-v1.xml"));
		PhantomAssertions.assertTrue(policy.enabled() && policy.lowWaterHits() == 3 && policy.targetHits() == 60 && policy.minimumAdenaReserve() == 500 && policy.maximumProfiles() == 128, "Supply policy differs from the committed bounds.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new org.l2jmobius.gameserver.phantoms.commerce.PhantomAutonomousSupplyPolicy(true, 3, 3, 500, 30, 8, 128), "Target stock below hysteresis accepted.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new org.l2jmobius.gameserver.phantoms.commerce.PhantomAutonomousSupplyPolicy(true, 3, 60, -1, 30, 8, 128), "Negative cash reserve accepted.");
		context.record("supply060.policy", "strict versioned XML; low3/target60 hits; reserve500; retry30s; journey8min; bounded128; no rewards/mutation");
	}

	private static PhantomGoal modelGoal(String type, String purpose, long revision, String payload)
	{
		return new PhantomGoal(1, type, PhantomGoalStatus.ACTIVE, new org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef("profile", "1"), null, 1, 0, null, java.util.List.of(), null, purpose, 500, 0, 1000, System.currentTimeMillis() + 60_000, java.util.Map.of(), "test.binding", revision, payload);
	}

	private final PhantomProfileRepository _profiles;
	private final PhantomCommerceReceiptStore _receipts;
	private final long _profileId, _startExp;
	private final int _itemId;
	private final PlayerNativeEvidence.Snapshot _baseline;
	private long _earnedAt, _returnedExp, _returnedStock;
	private PlayerNativeEvidence.Snapshot _returned;
	private PhantomGoal _completed;
	private PhantomCommerceReceipt _purchase;
	private boolean _done;

	PhantomSupply060Observation(PhantomProfileRepository profiles, long profileId, Player player, PlayerNativeEvidence.Snapshot baseline, int itemId)
	{
		_profiles = profiles; _profileId = profileId; _itemId = itemId; _baseline = baseline; _startExp = player.getExp();
		_receipts = new PhantomCommerceReceiptStore(profiles);
	}

	boolean advance(PhantomTestContext context, Player player, PlayerNativeEvidence.Snapshot sample, long now)
	{
		final var component = _profiles.findComponent(_profileId, "supply.intent").orElse(null);
		final var intent = component == null ? null : new PhantomGoalStateCodec().decode(component.payload());
		final long stock = player.getInventory().getInventoryItemCount(_itemId, -1);
		if (sample.killSequence() > _baseline.killSequence() && player.getExp() > _startExp && stock < 16 && _earnedAt == 0)
		{
			_earnedAt = now;
			context.record("life060.supplyEarned", "actual farm consumed initial finite16 charges; item=" + _itemId + "; stock=" + stock + "; EXP=" + _startExp + "->" + player.getExp() + "; sample=" + sample);
		}
		PhantomAssertions.assertTrue(_earnedAt == 0 || intent != null || now - _earnedAt < TimeUnit.SECONDS.toNanos(45), "Native farm consumed compatible charges and earned EXP/SP but production created no supply intent.");
		if (intent != null && intent.status() == PhantomGoalStatus.FAILED) { throw new AssertionError("Production supply intent failed: " + intent); }
		final var receipt = _receipts.find(_profileId).map(PhantomCommerceReceiptStore.VersionedReceipt::receipt).orElse(null);
		if (_purchase == null && receipt != null && receipt.state() == PhantomCommerceReceipt.State.COMMITTED)
		{
			_purchase = receipt;
			context.record("life060.supplyPaid", "receipt=" + receipt + "; actual EXP=" + player.getExp() + "; stock=" + stock);
			PhantomAssertions.assertTrue(_earnedAt != 0 && player.getExp() > _startExp, "Supply was paid before the measured native earned farm event.");
			PhantomAssertions.assertTrue(receipt.request().kind() == PhantomCommerceReceipt.OperationKind.BUY && receipt.request().itemId() == _itemId && receipt.request().amount() > 0 && receipt.before().primaryCount() - receipt.expectedAfter().primaryCount() == receipt.request().amount() && receipt.expectedAfter().secondaryCount() - receipt.before().secondaryCount() == receipt.request().count(), "Supply must preserve exact real NPC payment/item conservation.");
		}
		if (_completed == null && intent != null && intent.status() == PhantomGoalStatus.COMPLETED)
		{
			_completed = intent; _returned = sample; _returnedExp = player.getExp(); _returnedStock = stock;
			context.record("life060.supplyReturned", "intent=" + intent + "; stock=" + stock + "; actual XYZ=" + player.getX() + ',' + player.getY() + ',' + player.getZ());
		}
		_done = _purchase != null && _completed != null && sample.killSequence() > _returned.killSequence() && sample.damageSequence() > _returned.damageSequence() && sample.expGained() > _returned.expGained() && sample.spGained() > _returned.spGained() && player.getExp() > _returnedExp && stock < _returnedStock;
		return _done;
	}

	void verify(PhantomTestContext context, Player player, PlayerNativeEvidence.Snapshot sample)
	{
		context.record("life060.supplyResult", "completed=" + _done + "; purchase=" + _purchase + "; returned=" + _completed + "; stockAtReturn=" + _returnedStock + "; currentStock=" + player.getInventory().getInventoryItemCount(_itemId, -1) + "; EXP=" + _startExp + "->" + player.getExp() + "; sample=" + sample);
		PhantomAssertions.assertTrue(_done, "Production farm consumption -> paid compatible NPC restock -> return -> NEW kill/earned EXP/SP and purchased charge consumption did not complete.");
	}
}
