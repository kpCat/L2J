/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundInventoryHash;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.InventoryFacts;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.ItemObject;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.ItemLocation;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundStateCodec;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;

/** Actual inherited actors, read-only capture; no item preload, SQL heal or fixture replacement. */
public final class PhantomInventoryCapture035Suite implements PhantomTestSuite
{
	private PhantomBackgroundSuite.ProductionAuthorityFixture _production;
	private PhantomHeadlessPlayerTestEnvironment _questEnvironment;

	public static void main(String[] args)
	{
		var context = new PhantomTestContext(35003501, Path.of(args[0]), Path.of(args[1]));
		System.exit(PhantomTestLauncher.runSuite("inventory-capture035", new PhantomInventoryCapture035Suite(), context));
	}

	@Override public String id() { return "inventory-capture035"; }

	@Override public void beforeAll(PhantomTestContext context) throws Exception
	{
		_questEnvironment = new PhantomHeadlessPlayerTestEnvironment();
		_questEnvironment.initialize(context);
		_production = PhantomBackgroundSuite.ProductionAuthorityFixture.start();
	}

	@Override public void afterAll(PhantomTestContext context) throws Exception
	{
		try { if (_production != null) { _production.close(); } }
		finally { if (_questEnvironment != null) { _questEnvironment.shutdown(); } else { ThreadPool.shutdown(); DatabaseFactory.close(); } }
	}

	@Override public void register(PhantomTestRegistry registry)
	{
		registry.add("C01-real-inherited36-native-capture-conservation", c -> capture(c, 36, 268484533));
		registry.add("C02-real-inherited45-native-capture-conservation", c -> capture(c, 45, 268484616));
		registry.add("C03-overfull-new-slot-no-earned-delta", c -> capacity(c, 249, 3, false));
		registry.add("C04-stock-limit-equipment-and-existing-stack", c ->
		{
			for (int[] counts : List.of(new int[] {247, 3}, new int[] {248, 2}, new int[] {250, 0}))
			{
				capacity(c, counts[0], counts[1], false);
				capacity(c, counts[0], counts[1], true);
			}
			capacity(c, 246, 3, false);
		});
		registry.add("C05-native-quest-and-overfull-stack-policy", c ->
		{
			var player = Player.load(268484533);
			try
			{
				PhantomAssertions.assertTrue(player.getInventory().validateCapacity(1, true), "Quest capacity incorrectly uses nonquest252.");
				PhantomAssertions.assertFalse(player.getInventory().validateCapacity(0, false), "Stock AutoLootSlotLimit=True accepted overfull stack.");
				capacity(c, 249, 3, true);
			}
			finally { player.stopAllTasks(); }
			// Actual quest object only on the ordinary owned TEST fixture; inherited36/45 stay read-only.
			var fixture = Player.load(_questEnvironment.primary().objectId());
			try
			{
				int nonquestBefore = fixture.getInventory().getNonQuestSize();
				int questBefore = fixture.getInventory().getQuestSize();
				var quest = Arrays.stream(org.l2jmobius.gameserver.data.xml.ItemData.getInstance().getAllItems()).filter(item -> item != null && item.isQuestItem()).findFirst().orElseThrow();
				var added = fixture.getInventory().addItem(org.l2jmobius.gameserver.model.item.enums.ItemProcessType.REWARD, quest.getId(), 1, fixture, this);
				PhantomAssertions.assertTrue(added != null, "Actual quest fixture item not created.");
				PhantomAssertions.assertEquals(nonquestBefore, fixture.getInventory().getNonQuestSize(), "Quest object consumed a nonquest stock slot.");
				PhantomAssertions.assertEquals(questBefore + 1, fixture.getInventory().getQuestSize(), "Quest object escaped its separate stock count.");
				var full = fixture.getInventory().getItems().stream().map(item -> new PhantomBackgroundInventoryHash.CanonicalItem(item.getObjectId(), item.getId(), item.getCount(), ItemLocation.valueOf(item.getItemLocation().name()))).toList();
				PhantomAssertions.assertTrue(full.stream().anyMatch(item -> item.objectId() == added.getObjectId()), "Full canonical multiset hid the quest object.");
				c.record("quest.actualFixture", "itemId=" + quest.getId() + ";fullCount=" + full.size() + ";quest=" + fixture.getInventory().getQuestSize() + ";nonquest=" + nonquestBefore + ";fullHash=" + PhantomBackgroundInventoryHash.compute(full));
			}
			finally { _questEnvironment.cleanupLoadedPlayer(fixture); }
		});
		registry.add("C06-compact-object-duplicate-and-codec-bounds", c -> bounds());
	}

	private static void capacity(PhantomTestContext context, int slots, int equipped, boolean stack) throws Exception
	{
		var source = new PhantomBackgroundTransaction().load(36).state();
		var objects = new ArrayList<ItemObject>();
		objects.add(new ItemObject(100001, 57, 100, true, ItemLocation.INVENTORY));
		for (int n = 0; n < equipped; n++) { objects.add(new ItemObject(100002 + n, 10, 1, false, ItemLocation.PAPERDOLL)); }
		// Durable usedSlots also includes unknown/untracked INVENTORY rows; do not infer free slots from the subset.
		var inventory = new InventoryFacts(List.of(10, 57), objects, source.inventory().canonicalHash(), 0, 1000000, slots, 250);
		var state = new PhantomBackgroundState(PhantomBackgroundState.State.READY, source.identity(), source.progress(), new PhantomBackgroundState.Vitals(100, 100, 100, 100, 10, 10), source.position(), new PhantomBackgroundState.CombatFacts(PhantomBackgroundState.ModelKind.MELEE, 1000, 1000, 1000, 1000, 1000, 1000, 0, 0, 1, 1, 0, 1, 1, 1, 1), PhantomBackgroundState.Loadout.none(), inventory, source.autoGetSkills(), source.clock(), source.receipt(), source.hashes());
		int itemId = stack ? 57 : 10;
		var drop = new PhantomBackgroundModel.Drop(itemId, -1, 0, 100, 100, 1, 1, 1, null, 1, 100, stack, 0);
		var target = new PhantomBackgroundModel.Target(20001, state.progress().level(), true, 1, 1, 1, 1, 1, 1, 500, 500, 1, 1, List.of(drop), 1);
		var death = new PhantomBackgroundModel.DeathPolicy() { public double lossPercent(int level) { return 100; } public double normalMonsterReductionMultiplier() { return 1; } };
		var experience = new PhantomBackgroundModel.ExperienceTable() { public long experienceForLevel(int level) { return (long) (level - 1) * 100; } public int maximumLevel() { return 85; } };
		var result = new PhantomBackgroundModel().evaluate(new PhantomBackgroundModel.BatchRequest(state, target, new PhantomBackgroundModel.RewardPolicy(11, 1, 1), death, experience, ignored -> state.progress().level(), false, PhantomBackgroundModel.BatchMode.ORDINARY_DEATH_DROP, 0, 0, true, null, null, 1, true, 0));
		boolean permitted = slots + equipped + (stack ? 0 : 1) <= 250;
		PhantomAssertions.assertEquals(permitted, result.inventoryDelta().itemDeltas().getOrDefault(itemId, 0L) > 0, "Background/stock capacity mismatch.");
		if (!permitted)
		{
			PhantomAssertions.assertEquals(PhantomBackgroundModel.ResultReason.SLOT_CAPACITY, result.reason(), "Capacity rejection lost its reason.");
			PhantomAssertions.assertEquals(state.progress(), result.progress(), "Rejected pickup falsely earned EXP/SP.");
			PhantomAssertions.assertEquals(0, result.inventoryDelta().addedSlots(), "Rejected pickup minted a slot.");
		}
		context.record("capacity." + slots + "." + equipped + "." + stack, "permitted=" + permitted + ";result=" + result.reason() + ";deltas=" + result.inventoryDelta().itemDeltas());
	}

	private static void bounds() throws Exception
	{
		var source = new PhantomBackgroundTransaction().load(36).state();
		var objects = new ArrayList<ItemObject>();
		for (int n = 1; n <= 275; n++) { objects.add(new ItemObject(100000 + n, 10, 1, false, n <= 250 ? ItemLocation.INVENTORY : ItemLocation.PAPERDOLL)); }
		var inventory = new InventoryFacts(List.of(10), objects, "", 0, 1000000, 275, 250);
		var state = new PhantomBackgroundState(source.state(), source.identity(), source.progress(), source.vitals(), source.position(), source.combat(), source.loadout(), inventory, source.autoGetSkills(), source.clock(), source.receipt(), source.hashes());
		var codec = new PhantomBackgroundStateCodec();
		var bytes = codec.encode(state);
		PhantomAssertions.assertEquals(state, codec.decode(bytes), "Compact275 roundtrip changed objects.");
		rejected(() -> codec.decode(Arrays.copyOf(bytes, 4097)));
		var forged = bytes.clone(); java.nio.ByteBuffer.wrap(forged).putInt(8, 8193);
		rejected(() -> codec.decode(forged));
		objects.add(new ItemObject(100276, 10, 1, false, ItemLocation.PAPERDOLL));
		rejected(() -> new InventoryFacts(List.of(10), objects, "", 0, 1000000, 276, 250));
		rejected(() -> new InventoryFacts(List.of(10), List.of(objects.getFirst(), objects.getFirst()), "", 0, 1000000, 2, 250));
	}

	private static void rejected(Runnable operation)
	{
		boolean rejected = false;
		try { operation.run(); } catch (IllegalArgumentException expected) { rejected = true; }
		PhantomAssertions.assertTrue(rejected, "Unsafe inventory/codec accepted.");
	}

	private void capture(PhantomTestContext context, long profileId, int objectId) throws Exception
	{
		final var transaction = new PhantomBackgroundTransaction();
		final var previous = transaction.load(profileId).state();
		final var goal = new PhantomGoalStateStore(PhantomProfileRepository.open()).load(profileId).orElseThrow().goal();
		final var player = Player.load(objectId);
		PhantomAssertions.assertTrue(player != null, "Actual inherited Player not loaded.");
		try
		{
			final var items = player.getInventory().getItems().stream().map(item -> new PhantomBackgroundInventoryHash.CanonicalItem(item.getObjectId(), item.getId(), item.getCount(), ItemLocation.valueOf(item.getItemLocation().name()))).toList();
			PhantomAssertions.assertEquals(252, items.size(), "Wrong causal item multiset.");
			PhantomAssertions.assertEquals(249L, items.stream().filter(item -> item.location() == ItemLocation.INVENTORY).count(), "Wrong inherited INVENTORY count.");
			PhantomAssertions.assertEquals(3L, items.stream().filter(item -> item.location() == ItemLocation.PAPERDOLL).count(), "Wrong inherited PAPERDOLL count.");
			PhantomAssertions.assertEquals(0, player.getInventory().getQuestSize(), "Wrong inherited quest count.");
			PhantomAssertions.assertEquals(252, player.getInventory().getNonQuestSize(), "Wrong stock occupancy.");
			PhantomAssertions.assertEquals(250, player.getInventoryLimit(), "Stock cap changed.");
			PhantomAssertions.assertFalse(player.getInventory().validateCapacity(1, false), "Stock accepted a new overfull slot.");
			final String hash = PhantomBackgroundInventoryHash.compute(items);
			PhantomAssertions.assertEquals(previous.inventory().canonicalHash(), hash, "Full inherited canonical hash differs from durable state.");
			context.record("profile." + profileId + ".before", "objectId=" + objectId + ";all=252;INVENTORY=249;PAPERDOLL=3;quest=0;nonquest=252;cap=250;hash=" + hash);
			final var captured = _production.authority().captureOwnedNative(profileId, player, goal, previous).state();
			PhantomAssertions.assertEquals(252, captured.inventory().usedSlots(), "Native capture understated stock occupancy.");
			PhantomAssertions.assertEquals(previous.inventory().objects(), captured.inventory().objects(), "Tracked object IDs/counts/locations changed.");
			final var codec = new PhantomBackgroundStateCodec();
			final byte[] bytes = codec.encode(captured);
			PhantomAssertions.assertEquals(captured, codec.decode(bytes), "Overfull capture lost facts in v2 codec.");
			PhantomAssertions.assertTrue(Arrays.equals(bytes, codec.encode(codec.decode(bytes))), "Overfull v2 bytes not canonical.");
			PhantomAssertions.assertEquals(hash, PhantomBackgroundInventoryHash.compute(player.getInventory().getItems().stream().map(item -> new PhantomBackgroundInventoryHash.CanonicalItem(item.getObjectId(), item.getId(), item.getCount(), ItemLocation.valueOf(item.getItemLocation().name()))).toList()), "Capture mutated an item.");
			final byte[] legacy = codec.encode(previous);
			PhantomAssertions.assertTrue(Arrays.equals(legacy, codec.encode(codec.decode(legacy))), "Healthy inherited payload bytes changed.");
			context.record("profile." + profileId + ".capture", "allPreserved=true;usedSlots=252;v2Bytes=" + bytes.length + ";hash=" + hash);
		}
		finally { player.stopAllTasks(); }
	}
}
