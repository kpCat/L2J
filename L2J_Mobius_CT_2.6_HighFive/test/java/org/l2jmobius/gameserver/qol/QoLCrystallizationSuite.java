/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.qol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import org.l2jmobius.commons.network.ReadableBuffer;
import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.config.custom.PersonalCharacterQoLConfig.Settings;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.handler.CommunityBoardHandler;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.enums.player.PrivateStoreType;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.item.type.CrystalType;
import org.l2jmobius.gameserver.model.options.Augmentation;
import org.l2jmobius.gameserver.network.clientpackets.RequestCrystallizeItem;
import org.l2jmobius.gameserver.qol.CrystallizationService.Inspection;
import org.l2jmobius.gameserver.qol.CrystallizationService.Preview;
import org.l2jmobius.gameserver.qol.CrystallizationService.Result;
import org.l2jmobius.gameserver.qol.CrystallizationService.Status;
import org.l2jmobius.gameserver.qol.PersonalCrystallizationService.ConfirmationStatus;
import org.l2jmobius.gameserver.qol.PersonalCrystallizationService.ConfirmResult;
import org.l2jmobius.gameserver.qol.PersonalCrystallizationService.PendingConfirmation;
import org.l2jmobius.gameserver.qol.PersonalCrystallizationService.PrepareResult;
import org.l2jmobius.gameserver.scripting.ScriptEngine;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomHeadlessPlayerTestEnvironment;
import org.l2jmobius.tests.phantoms.PhantomTestContext;
import org.l2jmobius.tests.phantoms.PhantomTestRegistry;
import org.l2jmobius.tests.phantoms.PhantomTestSuite;

/** L2-QOL-002 native/common/Alt+B crystallization coverage. */
public final class QoLCrystallizationSuite implements PhantomTestSuite
{
	private static final long SEED = 1002002L;

	private final PhantomHeadlessPlayerTestEnvironment _environment = new PhantomHeadlessPlayerTestEnvironment();
	private Settings _previousSettings;
	private Player _player;
	private Player _observer;
	private QoLTestClient _network;
	private boolean _previousAugmentDestroy;

	@Override
	public String id()
	{
		return "qol-crystallization";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "L2-QOL-002 crystallization suite used the wrong deterministic seed.");
		_environment.initialize(context);
		_player = Player.load(_environment.primary().objectId());
		_observer = Player.load(_environment.observer().objectId());
		PhantomAssertions.assertTrue((_player != null) && (_observer != null), "L2-QOL-002 crystallization fixture Players did not load.");
		_previousAugmentDestroy = PlayerConfig.ALT_ALLOW_AUGMENT_DESTROY;
		PlayerConfig.ALT_ALLOW_AUGMENT_DESTROY = false;
		_previousSettings = PersonalCharacterQoLService.getInstance().installForTests(new Settings(true, false, true, Set.of(_player.getObjectId()), Set.of(), true, "Test configuration."));
		ScriptEngine.getInstance().executeScript(ScriptEngine.MASTER_HANDLER_FILE);
		PhantomAssertions.assertTrue(CommunityBoardHandler.getInstance().getHandler("_bbsqol") != null, "MasterHandler did not register the Personal QoL board fallback.");
		context.record("qol002.database", "l2jmobiush5_phantom_test");
		context.record("qol002.crystallizationOwner", CrystallizationService.class.getName());
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-baseline-item-skill-grade-and-state-characterization", this::baselineCharacterization);
		registry.add("02-common-service-exact-enchant-destruction-and-finally", this::commonServiceMutation);
		registry.add("03-native-packet-delegates-to-common-owner", this::nativePacket);
		registry.add("04-board-preview-confirm-replay-stale-auth-and-expiry", this::boardConfirmation);
		registry.add("05-concurrent-confirm-and-native-board-race-at-most-once", this::concurrency);
	}

	private void baselineCharacterization(PhantomTestContext context)
	{
		final CrystallizationService service = CrystallizationService.getInstance();
		final Item dItem = add(CrystalType.D, 0);
		PhantomAssertions.assertEquals(Status.SKILL_MISSING, service.inspect(_player, dItem.getObjectId(), 1).status(), "Missing CRYSTALLIZE skill was not denied.");
		PhantomAssertions.assertEquals(Status.SKILL_MISSING, service.crystallize(_player, Integer.MAX_VALUE, 1).status(), "Native validation did not check the missing skill before item ownership.");
		for (CrystalType grade : new CrystalType[] {CrystalType.D, CrystalType.C, CrystalType.B, CrystalType.A, CrystalType.S})
		{
			final Item item = grade == CrystalType.D ? dItem : add(grade, 0);
			final int requiredLevel = item.getTemplate().getCrystalTypePlus().getLevel();
			learnCrystallize(Math.max(1, requiredLevel - 1));
			if (requiredLevel > 1)
			{
				PhantomAssertions.assertEquals(Status.SKILL_TOO_LOW, service.inspect(_player, item.getObjectId(), 1).status(), grade + " grade accepted an insufficient skill level.");
			}
			learnCrystallize(requiredLevel);
			PhantomAssertions.assertTrue(service.inspect(_player, item.getObjectId(), 1).eligible(), grade + " grade rejected its exact required skill level.");
		}

		learnCrystallize(5);
		PhantomAssertions.assertEquals(Status.INVALID_COUNT, service.inspect(_player, dItem.getObjectId(), 0).status(), "Non-positive count was accepted.");
		final Item nonCrystallizable = _player.getInventory().addItem(ItemProcessType.REWARD, 57, 1, _player, this);
		PhantomAssertions.assertTrue(nonCrystallizable != null, "Could not add non-crystallizable control item.");
		PhantomAssertions.assertEquals(Status.NOT_CRYSTALLIZABLE, service.inspect(_player, nonCrystallizable.getObjectId(), 1).status(), "Non-crystallizable item was accepted.");
		_player.setPrivateStoreType(PrivateStoreType.SELL);
		PhantomAssertions.assertEquals(Status.BUSY, service.inspect(_player, dItem.getObjectId(), 1).status(), "Private store state was accepted.");
		_player.setPrivateStoreType(PrivateStoreType.NONE);
		_player.setInCrystallize(true);
		PhantomAssertions.assertEquals(Status.BUSY, service.inspect(_player, dItem.getObjectId(), 1).status(), "Compatibility inCrystallize state was accepted.");
		_player.setInCrystallize(false);
		_observer.addSkill(SkillData.getInstance().getSkill(248, 5), false);
		PhantomAssertions.assertEquals(Status.ITEM_NOT_FOUND, service.inspect(_observer, dItem.getObjectId(), 1).status(), "Foreign inventory object was accepted.");
		final Item blocked = add(CrystalType.D, 0);
		PhantomAssertions.assertEquals(blocked, _player.getInventory().getItemByObjectId(blocked.getObjectId()), "Blocked manipulation fixture was not in inventory.");
		_player.getInventory().setInventoryBlock(new int[] {blocked.getId()}, 0);
		try
		{
			PhantomAssertions.assertEquals(Status.NOT_OWNED, service.inspect(_player, blocked.getObjectId(), 1).status(), "Blocked inventory manipulation was accepted.");
		}
		finally
		{
			_player.getInventory().unblock();
		}

		final Item augmented = add(CrystalType.D, 0);
		PhantomAssertions.assertTrue(augmented.setAugmentation(new Augmentation(0)), "Could not configure augmented restriction fixture.");
		PhantomAssertions.assertEquals(Status.ITEM_RESTRICTED, service.inspect(_player, augmented.getObjectId(), 1).status(), "Augmented item was accepted while native destroy is disabled.");
		augmented.removeAugmentation();
		final Item hero = _player.getInventory().addItem(ItemProcessType.REWARD, 6611, 1, _player, this);
		PhantomAssertions.assertTrue((hero != null) && hero.isHeroItem(), "Could not configure hero restriction fixture.");
		PhantomAssertions.assertEquals(Status.ITEM_RESTRICTED, service.inspect(_player, hero.getObjectId(), 1).status(), "Hero item was accepted.");
		final Item shadow = addRestrictedTemplate(true);
		PhantomAssertions.assertTrue(shadow.isShadowItem(), "Shadow restriction fixture is not a shadow item.");
		PhantomAssertions.assertEquals(Status.ITEM_RESTRICTED_SILENT, service.inspect(_player, shadow.getObjectId(), 1).status(), "Shadow item was accepted.");
		final Item timed = addRestrictedTemplate(false);
		PhantomAssertions.assertTrue(timed.isTimeLimitedItem(), "Timed restriction fixture is not time-limited.");
		PhantomAssertions.assertEquals(Status.ITEM_RESTRICTED_SILENT, service.inspect(_player, timed.getObjectId(), 1).status(), "Time-limited item was accepted.");
		context.record("qol002.baseline", "skillRequired=true;grades=D1/C2/B3/A4/S5;count/store/inFlight/foreign/augment/hero/shadow/timed=false");
	}

	private void commonServiceMutation(PhantomTestContext context)
	{
		learnCrystallize(1);
		final Item item = addEquipable(CrystalType.D, 5);
		_player.getInventory().equipItem(item);
		PhantomAssertions.assertTrue(item.isEquipped(), "Could not equip the native crystallization fixture.");
		final int crystalId = item.getTemplate().getCrystalItemId();
		final int expectedCrystals = item.getCrystalCount();
		final long crystalsBefore = count(crystalId);
		final Result result = CrystallizationService.getInstance().crystallize(_player, item.getObjectId(), 1);
		PhantomAssertions.assertTrue(result.success(), "Common crystallization service rejected an eligible non-dwarf Player.");
		PhantomAssertions.assertEquals(null, _player.getInventory().getItemByObjectId(item.getObjectId()), "Common service did not destroy the exact item object.");
		PhantomAssertions.assertEquals(crystalsBefore + expectedCrystals, count(crystalId), "Common service did not use exact Item.getCrystalCount enchant output.");
		PhantomAssertions.assertFalse(_player.isInCrystallize(), "Common service leaked inCrystallize after success.");

		final Item staleItem = add(CrystalType.D, 0);
		final Inspection inspection = CrystallizationService.getInstance().inspect(_player, staleItem.getObjectId(), 1);
		final Preview stale = new Preview(inspection.preview().playerObjectId(), inspection.preview().itemObjectId(), inspection.preview().itemId(), inspection.preview().itemName(), inspection.preview().count(), inspection.preview().enchantLevel() + 1, inspection.preview().grade(), inspection.preview().crystalItemId(), inspection.preview().crystalCount());
		PhantomAssertions.assertEquals(Status.STALE_PREVIEW, CrystallizationService.getInstance().crystallize(_player, staleItem.getObjectId(), 1, stale).status(), "Changed preview fingerprint was accepted.");
		PhantomAssertions.assertFalse(_player.isInCrystallize(), "Common service leaked inCrystallize after stale failure.");
		PhantomAssertions.assertTrue(_player.getInventory().getItemByObjectId(staleItem.getObjectId()) != null, "Stale failure destroyed the item.");
		context.record("qol002.commonMutation", "nonDwarf=true;enchant=5;crystals=" + expectedCrystals + ";finally=true");
	}

	private void nativePacket(PhantomTestContext context) throws Exception
	{
		learnCrystallize(1);
		_network = QoLTestClient.attach(_player);
		final Item item = add(CrystalType.D, 0);
		final int crystalId = item.getTemplate().getCrystalItemId();
		final int expected = item.getCrystalCount();
		final long before = count(crystalId);
		crystallizePacket(item, 1).run();
		PhantomAssertions.assertEquals(null, _player.getInventory().getItemByObjectId(item.getObjectId()), "Native packet did not delegate exact destruction.");
		PhantomAssertions.assertEquals(before + expected, count(crystalId), "Native packet did not credit the common exact result.");
		PhantomAssertions.assertFalse(_player.isInCrystallize(), "Native packet leaked inCrystallize.");
		_network.close();
		_network = null;
		context.record("qol002.nativePacket", "commonOwner=true;debit=1;credit=" + expected);
	}

	private void boardConfirmation(PhantomTestContext context)
	{
		learnCrystallize(1);
		final PersonalCrystallizationService service = PersonalCrystallizationService.getInstance();
		PhantomAssertions.assertTrue(service.list(_observer, 1).items().isEmpty(), "Unauthorized Player received the crystallization inventory page.");
		PhantomAssertions.assertEquals(ConfirmationStatus.NOT_AUTHORIZED, service.prepare(_observer, 1).status(), "Unauthorized Player prepared a crystallization action.");
		PersonalCharacterQoLService.getInstance().installForTests(new Settings(true, false, false, Set.of(_player.getObjectId()), Set.of(), true, "Crystallization disabled test."));
		PhantomAssertions.assertEquals(ConfirmationStatus.NOT_AUTHORIZED, service.prepare(_player, 1).status(), "Disabled crystallization subfeature prepared an action.");
		PersonalCharacterQoLService.getInstance().installForTests(new Settings(true, false, true, Set.of(_player.getObjectId()), Set.of(), true, "Test configuration."));
		final Item item = add(CrystalType.D, 0);
		PhantomAssertions.assertTrue(service.list(_player, 1).items().stream().anyMatch(value -> value.itemObjectId() == item.getObjectId()), "Board inventory page omitted an eligible item.");
		final PrepareResult prepared = service.prepare(_player, item.getObjectId());
		PhantomAssertions.assertEquals(ConfirmationStatus.PREPARED, prepared.status(), "Board preview was not prepared.");
		PhantomAssertions.assertTrue((prepared.pending().token().length() >= 32) && ((prepared.pending().expiresAt() - System.currentTimeMillis()) <= 120_000), "Confirmation token is weak or lives longer than 120 seconds.");
		final int crystalId = item.getTemplate().getCrystalItemId();
		final int expected = item.getCrystalCount();
		final long before = count(crystalId);
		PhantomAssertions.assertEquals(ConfirmationStatus.SUCCESS, service.confirm(_player, prepared.pending().token()).status(), "Valid board confirmation failed.");
		PhantomAssertions.assertEquals(before + expected, count(crystalId), "Board confirmation credited the wrong amount.");
		PhantomAssertions.assertEquals(ConfirmationStatus.INVALID_TOKEN, service.confirm(_player, prepared.pending().token()).status(), "Replay token was accepted.");
		PhantomAssertions.assertEquals(before + expected, count(crystalId), "Replay changed crystal credit.");

		final Item staleItem = add(CrystalType.D, 0);
		final PendingConfirmation stale = service.prepare(_player, staleItem.getObjectId()).pending();
		staleItem.setEnchantLevel(1);
		PhantomAssertions.assertEquals(ConfirmationStatus.STALE, service.confirm(_player, stale.token()).status(), "Changed enchant fingerprint was accepted.");
		PhantomAssertions.assertTrue(_player.getInventory().getItemByObjectId(staleItem.getObjectId()) != null, "Stale confirmation destroyed the item.");
		final PendingConfirmation countChanged = service.prepare(_player, staleItem.getObjectId()).pending();
		staleItem.setCount(2);
		PhantomAssertions.assertEquals(ConfirmationStatus.STALE, service.confirm(_player, countChanged.token()).status(), "Changed item count was accepted.");
		staleItem.setCount(1);

		final PendingConfirmation revoked = service.prepare(_player, staleItem.getObjectId()).pending();
		PersonalCharacterQoLService.getInstance().installForTests(Settings.disabled("Authorization revoked."));
		PhantomAssertions.assertEquals(ConfirmationStatus.NOT_AUTHORIZED, service.confirm(_player, revoked.token()).status(), "Revoked authorization was accepted.");
		PersonalCharacterQoLService.getInstance().installForTests(new Settings(true, false, true, Set.of(_player.getObjectId()), Set.of(), true, "Test configuration."));

		final PendingConfirmation wrongPlayer = service.prepare(_player, staleItem.getObjectId()).pending();
		PhantomAssertions.assertEquals(ConfirmationStatus.INVALID_TOKEN, service.confirm(_observer, wrongPlayer.token()).status(), "Another Player used the confirmation token.");
		final PendingConfirmation replacement = service.prepare(_player, staleItem.getObjectId()).pending();
		PhantomAssertions.assertEquals(ConfirmationStatus.INVALID_TOKEN, service.confirm(_player, wrongPlayer.token()).status(), "A superseded per-Player confirmation token was accepted.");
		PhantomAssertions.assertTrue(replacement != null, "Replacement confirmation was not retained.");
		service.clearForTests(_player);

		final AtomicLong now = new AtomicLong(10_000);
		final LongSupplier previousClock = service.installClockForTests(now::get);
		try
		{
			final PendingConfirmation expiring = service.prepare(_player, staleItem.getObjectId()).pending();
			now.addAndGet(120_000);
			PhantomAssertions.assertEquals(ConfirmationStatus.EXPIRED, service.confirm(_player, expiring.token()).status(), "Expired confirmation token was accepted.");
		}
		finally
		{
			service.installClockForTests(previousClock);
		}
		for (int i = 0; i < (PersonalCrystallizationService.PAGE_SIZE + 1); i++)
		{
			add(CrystalType.D, 0);
		}
		final PersonalCrystallizationService.Page lastPage = service.list(_player, Integer.MAX_VALUE);
		PhantomAssertions.assertTrue((lastPage.totalPages() > 1) && (lastPage.page() == lastPage.totalPages()) && (lastPage.items().size() <= PersonalCrystallizationService.PAGE_SIZE), "Crystallization pagination is not bounded.");
		context.record("qol002.boardConfirmation", "preview=true;oneTime=true;stale/auth/wrongPlayer/expiry=false");
	}

	private void concurrency(PhantomTestContext context) throws Exception
	{
		learnCrystallize(1);
		final PersonalCrystallizationService service = PersonalCrystallizationService.getInstance();
		final Item confirmItem = add(CrystalType.D, 0);
		final PendingConfirmation pending = service.prepare(_player, confirmItem.getObjectId()).pending();
		final long beforeConfirm = count(confirmItem.getTemplate().getCrystalItemId());
		final int expectedConfirm = confirmItem.getCrystalCount();
		final AtomicInteger successes = new AtomicInteger();
		final CountDownLatch confirmStart = new CountDownLatch(1);
		final Thread first = new Thread(() -> confirmAfter(service, pending.token(), confirmStart, successes), "qol002-confirm-1");
		final Thread second = new Thread(() -> confirmAfter(service, pending.token(), confirmStart, successes), "qol002-confirm-2");
		first.start();
		second.start();
		confirmStart.countDown();
		first.join(5000);
		second.join(5000);
		PhantomAssertions.assertFalse(first.isAlive() || second.isAlive(), "Concurrent board confirmations did not terminate.");
		PhantomAssertions.assertEquals(1, successes.get(), "Concurrent confirmation succeeded more or less than once.");
		PhantomAssertions.assertEquals(beforeConfirm + expectedConfirm, count(confirmItem.getTemplate().getCrystalItemId()), "Concurrent confirmation credited duplicates.");

		_network = QoLTestClient.attach(_player);
		final Item raceItem = add(CrystalType.D, 0);
		final int crystalId = raceItem.getTemplate().getCrystalItemId();
		final int expectedRace = raceItem.getCrystalCount();
		final long beforeRace = count(crystalId);
		final PendingConfirmation racePending = service.prepare(_player, raceItem.getObjectId()).pending();
		final RequestCrystallizeItem packet = crystallizePacket(raceItem, 1);
		final CountDownLatch raceStart = new CountDownLatch(1);
		final Thread nativeThread = new Thread(() -> runAfter(raceStart, packet), "qol002-native-race");
		final Thread boardThread = new Thread(() -> confirmAfter(service, racePending.token(), raceStart, new AtomicInteger()), "qol002-board-race");
		nativeThread.start();
		boardThread.start();
		raceStart.countDown();
		nativeThread.join(5000);
		boardThread.join(5000);
		PhantomAssertions.assertFalse(nativeThread.isAlive() || boardThread.isAlive(), "Native/board crystallization race did not terminate.");
		PhantomAssertions.assertEquals(null, _player.getInventory().getItemByObjectId(raceItem.getObjectId()), "Native/board race produced no successful destruction.");
		PhantomAssertions.assertEquals(beforeRace + expectedRace, count(crystalId), "Native/board race duplicated or lost crystal credit.");
		PhantomAssertions.assertFalse(_player.isInCrystallize(), "Native/board race leaked inCrystallize.");
		_network.close();
		_network = null;
		context.record("qol002.concurrency", "doubleConfirm=one;nativeVsBoard=one;duplicateCredit=0");
	}

	private void confirmAfter(PersonalCrystallizationService service, String token, CountDownLatch start, AtomicInteger successes)
	{
		try
		{
			start.await(5, TimeUnit.SECONDS);
			final ConfirmResult result = service.confirm(_player, token);
			if (result.status() == ConfirmationStatus.SUCCESS)
			{
				successes.incrementAndGet();
			}
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}
	}

	private static void runAfter(CountDownLatch start, RequestCrystallizeItem packet)
	{
		try
		{
			start.await(5, TimeUnit.SECONDS);
			packet.run();
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}
	}

	private Item add(CrystalType grade, int enchant)
	{
		final ItemTemplate template = Arrays.stream(ItemData.getInstance().getAllItems()).filter(value -> (value != null) && value.isCrystallizable() && (value.getCrystalCount() > 0) && (value.getCrystalTypePlus() == grade) && !value.isHeroItem() && !value.isStackable() && (value.getDuration() <= 0) && (value.getTime() <= 0)).findFirst().orElseThrow(() -> new AssertionError("No safe crystallizable " + grade + " fixture template exists."));
		final Item item = _player.getInventory().addItem(ItemProcessType.REWARD, template.getId(), 1, _player, this);
		PhantomAssertions.assertTrue(item != null, "Could not add crystallization fixture " + template.getId() + ".");
		item.setEnchantLevel(enchant);
		return item;
	}

	private Item addEquipable(CrystalType grade, int enchant)
	{
		final ItemTemplate template = Arrays.stream(ItemData.getInstance().getAllItems()).filter(value -> (value != null) && value.isEquipable() && value.isCrystallizable() && (value.getCrystalCount() > 0) && (value.getCrystalTypePlus() == grade) && !value.isHeroItem() && (value.getDuration() <= 0) && (value.getTime() <= 0)).findFirst().orElseThrow(() -> new AssertionError("No safe equipable crystallizable " + grade + " fixture template exists."));
		final Item item = _player.getInventory().addItem(ItemProcessType.REWARD, template.getId(), 1, _player, this);
		PhantomAssertions.assertTrue(item != null, "Could not add equipable crystallization fixture " + template.getId() + ".");
		item.setEnchantLevel(enchant);
		return item;
	}

	private Item addRestrictedTemplate(boolean shadow)
	{
		final ItemTemplate template = Arrays.stream(ItemData.getInstance().getAllItems()).filter(value -> (value != null) && !value.isHeroItem() && (shadow ? (value.getDuration() > 0) : (value.getTime() > 0))).findFirst().orElseThrow(() -> new AssertionError("No " + (shadow ? "shadow" : "time-limited") + " fixture template exists."));
		final Item item = _player.getInventory().addItem(ItemProcessType.REWARD, template.getId(), 1, _player, this);
		PhantomAssertions.assertTrue(item != null, "Could not add restricted fixture " + template.getId() + ".");
		return item;
	}

	private void learnCrystallize(int level)
	{
		PhantomAssertions.assertTrue(SkillData.getInstance().getSkill(248, level) != null, "CRYSTALLIZE level " + level + " is absent.");
		_player.addSkill(SkillData.getInstance().getSkill(248, level), false);
	}

	private long count(int itemId)
	{
		return _player.getInventory().getInventoryItemCount(itemId, -1);
	}

	private RequestCrystallizeItem crystallizePacket(Item item, long count)
	{
		final ByteBuffer bytes = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
		bytes.putInt(item.getObjectId());
		bytes.putLong(count);
		bytes.flip();
		final RequestCrystallizeItem packet = new RequestCrystallizeItem();
		packet.init(_network.client(), ReadableBuffer.of(bytes));
		PhantomAssertions.assertTrue(packet.read(), "Native RequestCrystallizeItem packet did not parse.");
		return packet;
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		Throwable failure = null;
		try
		{
			PersonalCrystallizationService.getInstance().clearForTests(_player);
			if (_network != null)
			{
				_network.close();
				_network = null;
			}
			if (_player != null)
			{
				_player.setInCrystallize(false);
				_player.setPrivateStoreType(PrivateStoreType.NONE);
				_environment.cleanupLoadedPlayer(_player);
				_player = null;
			}
			if (_observer != null)
			{
				_environment.cleanupLoadedPlayer(_observer);
				_observer = null;
			}
		}
		catch (Throwable throwable)
		{
			failure = throwable;
		}
		finally
		{
			PlayerConfig.ALT_ALLOW_AUGMENT_DESTROY = _previousAugmentDestroy;
			PersonalCharacterQoLService.getInstance().installForTests(_previousSettings);
			_environment.shutdown();
		}
		if (failure instanceof Exception exception)
		{
			throw exception;
		}
		if (failure != null)
		{
			throw new RuntimeException(failure);
		}
	}
}
