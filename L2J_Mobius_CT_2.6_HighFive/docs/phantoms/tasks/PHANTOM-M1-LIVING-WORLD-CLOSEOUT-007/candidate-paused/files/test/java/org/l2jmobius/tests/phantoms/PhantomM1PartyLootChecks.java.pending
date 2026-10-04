/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.model.actor.enums.npc.DropType;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.actor.templates.NpcTemplate;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.model.groups.PartyDistributionType;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.Response;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundInventoryHash;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundInventoryHash.CanonicalItem;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.ItemLocation;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ResultStatus;
import org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope;
import org.l2jmobius.gameserver.util.LocationUtil;
import org.l2jmobius.tests.phantoms.PhantomM1SharedRecipientsChecks.Managed;

/** Stock party pickup is held on its original ground Item before pickupMe mutates World or inventory. */
public final class PhantomM1PartyLootChecks
{
	private PhantomM1PartyLootChecks() { }

	public static void pickup(PhantomTestContext context, Managed picker, Managed recipient, Item originalGround) throws Exception
	{
		run(context, picker.player(), recipient.player(), picker, recipient, originalGround);
	}

	public static void ordinaryPickup(PhantomTestContext context, Player picker, Player recipient, Item originalGround) throws Exception
	{
		run(context, picker, recipient, null, null, originalGround);
	}

	public static void lateJoin(PhantomTestContext context, Managed picker, Managed recipient, Managed newcomer, Item originalGround, boolean sealNewcomer) throws Exception
	{
		run(context, picker.player(), recipient.player(), picker, recipient, originalGround, new LateJoin(newcomer.player(), newcomer, sealNewcomer));
	}

	public static void ordinaryLateJoin(PhantomTestContext context, Player picker, Player recipient, Player newcomer, Item originalGround) throws Exception
	{
		run(context, picker, recipient, null, null, originalGround, new LateJoin(newcomer, null, false));
	}

	private static void run(PhantomTestContext context, Player picker, Player recipient, Managed managedPicker, Managed managedRecipient, Item original) throws Exception
	{
		run(context, picker, recipient, managedPicker, managedRecipient, original, null);
	}

	private static void run(PhantomTestContext context, Player picker, Player recipient, Managed managedPicker, Managed managedRecipient, Item original, LateJoin change) throws Exception
	{
		PhantomAssertions.assertEquals(PhantomTestDatabaseGuard.TARGET_DATABASE, context.measurements().get("headless.database"), "Party loot requires guarded headless TEST.");
		PhantomAssertions.assertTrue(picker != recipient && picker.getObjectId() != recipient.getObjectId(), "INVALID Q12 party loot: two distinct original native Players required.");
		final PhantomNativeWorkScope pickerScope = scope(picker, managedPicker), recipientScope = scope(recipient, managedRecipient);
		final String key = "Q12.partyPickup." + (original == null ? "missing" : original.getId()) + (managedRecipient == null ? ".ordinary" : ".managed") + (change == null ? "" : change.seal ? ".lateSealed" : ".lateOpen");
		context.record(key + ".fixture", "INVALID_UNTIL_NATIVE_COMPLETION");
		PhantomAssertions.assertTrue(!picker.isInParty() && !recipient.isInParty() && !picker.hasSummon() && !recipient.hasSummon(), "INVALID Q12 party loot: fresh native party without additional recipients required.");
		PhantomAssertions.assertTrue(picker.getInstanceId() == recipient.getInstanceId() && picker.isInSurroundingRegion(recipient) && LocationUtil.checkIfInRange(PlayerConfig.ALT_PARTY_RANGE, picker, recipient, true), "INVALID Q12 party loot: recipient is outside native party reward range.");
		PhantomAssertions.assertTrue(original != null && World.getInstance().findObject(original.getObjectId()) == original && original.isSpawned() && !original.isProtected() && original.getOwnerId() == 0
			&& original.getInstanceId() == picker.getInstanceId() && picker.calculateDistance2D(original) <= 70 && !original.getTemplate().hasExImmediateEffect(), "INVALID Q12 party loot: exact lawful nearby original native ground Item required.");
		final int itemId = original.getId(); final long dropped = original.getCount();
		final PhantomNativeWorkScope newcomerScope = change == null ? null : scope(change.player, change.managed);
		if (change != null)
		{
			PhantomAssertions.assertTrue(itemId == 57 && change.player != picker && change.player != recipient && !change.player.isInParty() && !change.player.hasSummon()
				&& change.player.getInstanceId() == picker.getInstanceId() && picker.isInSurroundingRegion(change.player) && LocationUtil.checkIfInRange(PlayerConfig.ALT_PARTY_RANGE, picker, change.player, true), "INVALID late party loot: distinct nearby newcomer and even original Adena required.");
		}
		PhantomAssertions.assertTrue(itemId == 57 ? dropped >= 2 && dropped % 2 == 0 : itemId == 1866 && dropped == 1, "INVALID Q12 party loot: even stock Adena57 or one original non-herb stock Suede1866 required.");
		PhantomAssertions.assertTrue(!picker.isInvisible() && picker.getActiveTradeList() == null && recipient.getInventory().validateCapacityByItemId(itemId), "INVALID Q12 party loot: native picker/recipient eligibility failed.");
		final long pickerBefore = count(picker, itemId), recipientBefore = count(recipient, itemId);
		final long pickerExpected = Math.addExact(pickerBefore, itemId == 57 ? dropped / 2 : 0);
		final long recipientExpected = Math.addExact(recipientBefore, itemId == 57 ? dropped / 2 : dropped);
		final long newcomerBefore = change == null ? 0 : count(change.player, itemId);
		long pickerStored = pickerExpected, recipientStored = recipientExpected, newcomerStored = newcomerBefore;
		if (itemId == 57) { PhantomAssertions.assertTrue(pickerExpected <= PlayerConfig.MAX_ADENA && recipientExpected <= PlayerConfig.MAX_ADENA, "INVALID Q12 party loot: stock Adena maximum would clamp the expected share."); }
		Observation observation;
		Throwable primary = null;
		try
		{
			withMembers(managedPicker, managedRecipient, () ->
			{
				formParty(picker, recipient);
				picker.getStatus().stopHpMpRegeneration(); recipient.getStatus().stopHpMpRegeneration();
			});
			await(() -> (pickerScope == null || pickerScope.outstanding() == 0) && (recipientScope == null || recipientScope.outstanding() == 0), 3000, "INVALID Q12 party loot: pre-existing tickets hide recipient accounting.");
			context.record(key + ".baselineRecipientOutstanding", 0);
			observation = heldPickup(context, key, picker, recipient, managedPicker, recipientScope, original, pickerBefore, recipientBefore, change);
			// A missing ticket is a product RED only after this exact original Item produces its stock recipient write.
			PhantomAssertions.assertFalse(original.isSpawned(), "INVALID Q12 party loot: original ground Item was not consumed by stock pickupMe.");
			pickerStored = count(picker, itemId); recipientStored = count(recipient, itemId);
			if (change == null)
			{
				PhantomAssertions.assertEquals(pickerExpected, pickerStored, "INVALID Q12 party loot: stock picker Item/Adena count differs from the exact native distribution.");
				PhantomAssertions.assertEquals(recipientExpected, recipientStored, "INVALID Q12 party loot: no exact original stock recipient Item/Adena write.");
			}
			else
			{
				newcomerStored = count(change.player, itemId);
				PhantomAssertions.assertEquals(dropped, pickerStored - pickerBefore + recipientStored - recipientBefore + newcomerStored - newcomerBefore, "Q12 late membership lost or duplicated original consumed Adena.");
				context.record(key + ".nativeNewcomerDelta", newcomerStored - newcomerBefore);
			}
			context.record(key + ".nativeRecipientDelta", recipientStored - recipientBefore);
			context.record(key + ".nativePickerDelta", pickerStored - pickerBefore);
			context.record(key + ".fixture", "VALID_ACTUAL_NATIVE_COMPLETION");
		}
		catch (Exception | Error failure) { primary = failure; throw failure; }
		finally
		{
			try
			{
				if (change != null) { withMembers(managedPicker, change.managed, () -> PartyInvitationService.getInstance().leave(change.player)); }
				withMembers(managedPicker, managedRecipient, () -> { PartyInvitationService.getInstance().leave(recipient); PartyInvitationService.getInstance().leave(picker); });
			}
			catch (Exception | Error failure) { if (primary == null) { throw failure; } primary.addSuppressed(failure); }
		}
		PhantomAssertions.assertFalse(picker.isInParty() || recipient.isInParty(), "Native party leave must precede canonical supported-context storage.");
		if (managedPicker != null)
		{
			await(() -> pickerScope.outstanding() == 0 && recipientScope.outstanding() == 0, 3000, "Native party pickup callbacks did not drain before persistence.");
			final String pickerHash = PlayerNativeWork.checkpoint(picker, () -> fullHash(picker));
			final String recipientHash = PlayerNativeWork.checkpoint(recipient, () -> fullHash(recipient));
			rematerialize(context, key + ".picker", managedPicker, pickerScope, itemId, pickerStored, pickerHash);
			rematerialize(context, key + ".recipient", managedRecipient, recipientScope, itemId, recipientStored, recipientHash);
			if (change != null)
			{
				await(() -> newcomerScope.outstanding() == 0, 3000, "Late newcomer native work did not drain.");
				final String newcomerHash = PlayerNativeWork.checkpoint(change.player, () -> fullHash(change.player));
				rematerialize(context, key + ".newcomer", change.managed, newcomerScope, itemId, newcomerStored, newcomerHash);
			}
			PhantomAssertions.assertTrue(observation.recipientOutstanding() > 0, "Q12 native party loot recipient was not reserved before original ground Item removal.");
			PhantomAssertions.assertFalse(observation.checkpointCrossed(), "Q12 native receiver checkpoint crossed picker-owned party pickup before ground removal and stock recipient writer.");
		}
		else
		{
			PhantomAssertions.assertTrue(!picker.isNativeWorkManaged() && !recipient.isNativeWorkManaged() && World.getInstance().getPlayer(picker.getObjectId()) == picker && World.getInstance().getPlayer(recipient.getObjectId()) == recipient,
				"Ordinary stock pickup control changed original REAL identity or native ownership.");
			PhantomAssertions.assertTrue(observation.checkpointCrossed(), "INVALID ordinary control: stock generic checkpoint should run without managed admission.");
		}
		if (change != null)
		{
			PhantomAssertions.assertEquals(pickerExpected, pickerStored, "Q12 late membership changed captured picker's exact stock Adena share.");
			PhantomAssertions.assertEquals(recipientExpected, recipientStored, "Q12 late membership changed captured recipient's exact stock Adena share.");
			PhantomAssertions.assertEquals(newcomerBefore, newcomerStored, "Q12 native pickup discovered a new writer outside its admitted immutable roster.");
		}
	}

	private static Observation heldPickup(PhantomTestContext context, String key, Player picker, Player recipient, Managed managedPicker, PhantomNativeWorkScope recipientScope, Item original, long pickerBefore, long recipientBefore, LateJoin change) throws Exception
	{
		final var pickupFailure = new AtomicReference<Throwable>(); final var checkpointFailure = new AtomicReference<Throwable>();
		final var enteredCheckpoint = new CountDownLatch(1); final var releaseCheckpoint = new CountDownLatch(1);
		final Thread pickup = Thread.ofPlatform().daemon().name("m1-native-party-pickup").unstarted(() ->
		{
			try
			{
				if (managedPicker == null) { picker.doPickupItem(original); }
				else
				{
					try (var action = managedPicker.materialization().tryAcquireAction(managedPicker.profileId()).orElseThrow())
					{
						PhantomAssertions.assertTrue(action.player() == picker, "INVALID Q12 party loot: picker publication lease changed exact Player."); picker.doPickupItem(original);
					}
				}
			}
			catch (Throwable failure) { pickupFailure.set(failure); }
		});
		final Thread checkpoint = Thread.ofPlatform().daemon().name("m1-native-party-recipient-checkpoint").unstarted(() ->
		{
			try
			{
				// Background canonical PREPARE rejects party state. This invokes the real generic owner drain instead.
				PlayerNativeWork.checkpoint(recipient, () ->
				{
					enteredCheckpoint.countDown();
					try { PhantomAssertions.assertTrue(releaseCheckpoint.await(6, TimeUnit.SECONDS), "Native party recipient checkpoint TEST gate timed out."); }
					catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
					return null;
				});
			}
			catch (Throwable failure) { checkpointFailure.set(failure); }
		});
		int outstanding = 0; boolean crossed = false;
		Throwable primary = null;
		try
		{
			synchronized (original)
			{
				pickup.start();
				await(() -> blockedOnOriginal(pickup, original), 1500, "INVALID Q12 party loot: actual stock doPickupItem did not block on its original ground Item before pickupMe.");
				final var frames = pickup.getStackTrace();
				PhantomAssertions.assertTrue(Arrays.stream(frames).anyMatch(frame -> frame.getClassName().equals(Player.class.getName()) && frame.getMethodName().equals("doPickupItem")), "INVALID Q12 party loot: original Item monitor gate lacks stock doPickupItem stack.");
				if (change != null) { change.join(picker, recipient, managedPicker); context.record(key + ".lateNewcomerCheckpointSealed", change.seal); }
				outstanding = recipientScope == null ? 0 : recipientScope.outstanding();
				context.record(key + ".recipientOutstandingBeforeGroundRemoval", outstanding);
				if (recipientScope != null)
				{
					context.record(key + ".recipientHeldSnapshot", recipientScope.snapshot());
					PhantomAssertions.assertEquals(null, PlayerNativeWork.current(recipientScope), "INVALID Q12 party loot: recipient setup lease is still active.");
				}
				context.record(key + ".nativePickupStack", Arrays.stream(frames).limit(20).map(frame -> frame.getClassName() + "." + frame.getMethodName()).reduce((left, right) -> left + ">" + right).orElse(""));
				checkpoint.start(); crossed = enteredCheckpoint.await(2, TimeUnit.SECONDS);
				context.record(key + ".checkpointCrossedBeforeGroundRemoval", crossed);
				PhantomAssertions.assertTrue(original.isSpawned() && World.getInstance().findObject(original.getObjectId()) == original && original.getWorldRegion() != null, "INVALID Q12 party loot: ground changed while its original monitor was held.");
				PhantomAssertions.assertEquals(pickerBefore, count(picker, original.getId()), "INVALID Q12 party loot: picker inventory changed before original ground removal.");
				PhantomAssertions.assertEquals(recipientBefore, count(recipient, original.getId()), "INVALID Q12 party loot: recipient inventory changed before original ground removal.");
				// Complete an unprotected RED checkpoint before releasing the actual stock writer; no fake writer is used.
				if (crossed) { releaseCheckpoint.countDown(); checkpoint.join(1000); PhantomAssertions.assertFalse(checkpoint.isAlive(), "Native RED checkpoint did not reopen before stock pickup release."); }
			}
			pickup.join(4000);
			PhantomAssertions.assertTrue(!pickup.isAlive() && pickupFailure.get() == null, "INVALID Q12 party loot: actual native pickup failed or did not finish: " + pickupFailure.get());
			PhantomAssertions.assertTrue(enteredCheckpoint.await(2, TimeUnit.SECONDS), "Native recipient checkpoint did not enter after full stock pickup completion.");
			releaseCheckpoint.countDown(); checkpoint.join(4000);
			PhantomAssertions.assertTrue(!checkpoint.isAlive() && checkpointFailure.get() == null, "Native party recipient checkpoint failed: " + checkpointFailure.get());
			return new Observation(outstanding, crossed);
		}
		catch (Exception | Error failure) { primary = failure; throw failure; }
		finally
		{
			releaseCheckpoint.countDown();
			try
			{
				if (change != null) { change.finish(); }
				if (pickup.getState() != Thread.State.NEW) { pickup.join(5000); }
				if (checkpoint.getState() != Thread.State.NEW) { checkpoint.join(5000); }
				PhantomAssertions.assertFalse(pickup.isAlive() || checkpoint.isAlive(), "Party loot fixture retained a native pickup/checkpoint thread.");
			}
			catch (Exception | Error failure) { if (primary == null) { throw failure; } primary.addSuppressed(failure); }
		}
	}

	private static boolean blockedOnOriginal(Thread pickup, Item original)
	{
		if (pickup.getState() != Thread.State.BLOCKED) { return false; }
		final var info = ManagementFactory.getThreadMXBean().getThreadInfo(new long[] { pickup.threadId() }, true, false)[0];
		return info != null && info.getLockInfo() != null && info.getLockOwnerId() == Thread.currentThread().threadId() && info.getLockInfo().getIdentityHashCode() == System.identityHashCode(original);
	}

	/** Caller supplies an isolated one-stock-drop native template and the already enabled guarded TEST AUTO_LOOT branch. */
	public static void autoLoot(PhantomTestContext context, Managed picker, Managed recipient, Monster nativeTarget, NpcTemplate nativeDrops, int itemId) throws Exception
	{
		autoLoot(context, picker.player(), recipient.player(), picker, recipient, nativeTarget, nativeDrops, itemId);
	}

	public static void ordinaryAutoLoot(PhantomTestContext context, Player picker, Player recipient, Monster nativeTarget, NpcTemplate nativeDrops, int itemId) throws Exception
	{
		autoLoot(context, picker, recipient, null, null, nativeTarget, nativeDrops, itemId);
	}

	private static void autoLoot(PhantomTestContext context, Player picker, Player recipient, Managed managedPicker, Managed managedRecipient, Monster target, NpcTemplate drops, int itemId) throws Exception
	{
		PhantomAssertions.assertEquals(PhantomTestDatabaseGuard.TARGET_DATABASE, context.measurements().get("headless.database"), "Party AutoLoot requires guarded native TEST.");
		final var pickerScope = scope(picker, managedPicker); final var recipientScope = scope(recipient, managedRecipient);
		final String key = "Q12.partyAutoLoot." + itemId + (managedPicker == null ? ".ordinary" : ".managed");
		PhantomAssertions.assertTrue(picker != recipient && !picker.isInParty() && !recipient.isInParty() && !picker.hasSummon() && !recipient.hasSummon()
			&& LocationUtil.checkIfInRange(PlayerConfig.ALT_PARTY_RANGE, picker, recipient, true), "INVALID party AutoLoot: fresh distinct native recipients in range required.");
		PhantomAssertions.assertTrue(target != null && target.getId() == 20534 && target.getInstanceId() == picker.getInstanceId() && picker.isInSurroundingRegion(target) && !target.isRaid()
			&& drops != null && drops.getId() == target.getId() && (drops.getDropGroups() == null || drops.getDropGroups().isEmpty()) && drops.getDropList() != null && drops.getDropList().size() == 1
			&& drops.getDropList().getFirst().getItemId() == itemId && drops.getDropList().getFirst().getDropType() == DropType.DROP && drops.getDropList().getFirst().getMin() > 0,
			"INVALID party AutoLoot: original stock NPC20534 and isolated confirmed native single drop required.");
		PhantomAssertions.assertTrue((itemId == 57 || itemId == 1866) && (PlayerConfig.AUTO_LOOT || PlayerConfig.AUTO_LOOT_ITEM_IDS.contains(itemId)) && recipient.getInventory().validateCapacityByItemId(itemId),
			"INVALID party AutoLoot: original stock Item/Adena must be eligible in the existing native AUTO_LOOT branch.");
		final long pickerBefore = count(picker, itemId), recipientBefore = count(recipient, itemId);
		final long pickerSequence = pickerScope == null ? 0 : pickerScope.evidence().snapshot().lootSequence();
		final long recipientSequence = recipientScope == null ? 0 : recipientScope.evidence().snapshot().lootSequence();
		Throwable primary = null;
		try
		{
			withMembers(managedPicker, managedRecipient, () -> formParty(picker, recipient));
			if (managedPicker == null) { target.doItemDrop(drops, picker); }
			else
			{
				try (var action = managedPicker.materialization().tryAcquireAction(managedPicker.profileId()).orElseThrow())
				{
					PhantomAssertions.assertTrue(action.player() == picker, "INVALID party AutoLoot: actor lease changed."); target.doItemDrop(drops, picker);
				}
			}
		}
		catch (Exception | Error failure) { primary = failure; throw failure; }
		finally
		{
			try { withMembers(managedPicker, managedRecipient, () -> { PartyInvitationService.getInstance().leave(recipient); PartyInvitationService.getInstance().leave(picker); }); }
			catch (Exception | Error failure) { if (primary == null) { throw failure; } primary.addSuppressed(failure); }
		}
		final long pickerAfter = count(picker, itemId), recipientAfter = count(recipient, itemId);
		final long pickerGain = pickerAfter - pickerBefore, recipientGain = recipientAfter - recipientBefore;
		PhantomAssertions.assertTrue(recipientGain > 0 && (itemId == 57 ? pickerGain > 0 && Math.abs(pickerGain - recipientGain) <= 1 : pickerGain == 0), "INVALID party AutoLoot: original native doItemDrop/ItemHolder distribution produced no stock recipient writer or wrong stock selection.");
		if (managedPicker != null)
		{
			PhantomAssertions.assertTrue(recipientScope.evidence().snapshot().lootSequence() > recipientSequence && (itemId != 57 || pickerScope.evidence().snapshot().lootSequence() > pickerSequence), "Actual native party AutoLoot lacks recipient scalar inventory evidence.");
			await(() -> pickerScope.outstanding() == 0 && recipientScope.outstanding() == 0, 3000, "Native AutoLoot descendants did not drain.");
			rematerialize(context, key + ".picker", managedPicker, pickerScope, itemId, pickerAfter, PlayerNativeWork.checkpoint(picker, () -> fullHash(picker)));
			rematerialize(context, key + ".recipient", managedRecipient, recipientScope, itemId, recipientAfter, PlayerNativeWork.checkpoint(recipient, () -> fullHash(recipient)));
		}
		else { PhantomAssertions.assertTrue(!picker.isNativeWorkManaged() && !recipient.isNativeWorkManaged(), "Ordinary native AutoLoot control changed ownership."); }
		context.record(key + ".fixture", "VALID_ACTUAL_ATTACKABLE_DO_ITEM_DROP_AUTOLOOT_ITEMHOLDER_NATIVE_WRITERS");
		context.record(key + ".nativeDeltas", "picker=" + pickerGain + " recipient=" + recipientGain + " stockRemainderMath=true");
	}

	private static void rematerialize(PhantomTestContext context, String key, Managed managed, PhantomNativeWorkScope oldScope, int itemId, long expectedCount, String expectedHash)
	{
		final Player old = managed.player();
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, managed.materialization().dematerialize(managed.profileId()).status(), "Native party acquired-loot cleanup failed after supported stock party leave.");
		PhantomAssertions.assertFalse(World.getInstance().getPlayer(old.getObjectId()) == old, "Native party loot cleanup retained the original Player in World.");
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, managed.materialization().materialize(managed.profileId()).status(), "Native party acquired-loot Player did not actually rematerialize.");
		final Player restored;
		try (var action = managed.materialization().tryAcquireAction(managed.profileId()).orElseThrow()) { restored = action.player(); }
		PhantomAssertions.assertTrue(restored != old && restored.getObjectId() == old.getObjectId() && restored.getNativeWorkOwner() instanceof PhantomNativeWorkScope
			&& restored.getNativeWorkOwner() != oldScope && restored.getNativeWorkOwner().epoch() != oldScope.epoch() && restored.getNativeWorkOwner().isCurrent() && !restored.isInParty(), "Native party loot persistence requires an exact new Player lifetime, not manual reload/restore.");
		PlayerNativeWork.checkpoint(restored, () ->
		{
			PhantomAssertions.assertEquals(expectedCount, count(restored, itemId), "Original native party Item/Adena count changed after actual rematerialization.");
			PhantomAssertions.assertEquals(expectedHash, fullHash(restored), "Complete native party inventory canonical digest changed after actual rematerialization.");
			return null;
		});
		context.record(key + ".rematerialized", "count=" + expectedCount + " canonicalHash=" + expectedHash + " oldEpoch=" + oldScope.epoch() + " newEpoch=" + restored.getNativeWorkOwner().epoch());
	}

	private static PhantomNativeWorkScope scope(Player player, Managed managed)
	{
		PhantomAssertions.assertTrue(World.getInstance().getPlayer(player.getObjectId()) == player && player.isOnline() && !player.isDead() && player.isSpawned(), "INVALID Q12 party loot: exact live native World Player required.");
		if (managed == null) { PhantomAssertions.assertFalse(player.isNativeWorkManaged(), "INVALID ordinary party loot: original REAL Player must remain ordinary."); return null; }
		PhantomAssertions.assertTrue(managed.player() == player && player.isNativeWorkManaged() && player.getNativeWorkOwner() instanceof PhantomNativeWorkScope, "INVALID Q12 party loot: exact managed scope required.");
		final var result = (PhantomNativeWorkScope) player.getNativeWorkOwner();
		PhantomAssertions.assertTrue(result.isCurrent() && result.player() == player && result.open(), "INVALID Q12 party loot: stale or closed managed lifetime.");
		return result;
	}

	private static void withMembers(Managed picker, Managed recipient, Runnable action)
	{
		if (picker == null) { action.run(); return; }
		try (var one = picker.materialization().tryAcquireAction(picker.profileId()).orElseThrow(); var two = recipient.materialization().tryAcquireAction(recipient.profileId()).orElseThrow())
		{
			PhantomAssertions.assertTrue(one.player() == picker.player() && two.player() == recipient.player(), "INVALID Q12 party loot: native member setup leases changed exact identity."); action.run();
		}
	}

	private static void formParty(Player picker, Player recipient)
	{
		final var service = PartyInvitationService.getInstance();
		final var invite = service.invite(picker, recipient, PartyDistributionType.BY_TURN.getId());
		try
		{
			PhantomAssertions.assertTrue(invite.delivered(), "INVALID Q12 party loot: actual native invitation failed: " + invite.outcome());
			final var accepted = service.respond(recipient, Response.ACCEPT, invite.identity());
			PhantomAssertions.assertTrue(accepted.accepted() && picker.getParty() == accepted.party() && recipient.getParty() == accepted.party() && accepted.party().getDistributionType() == PartyDistributionType.BY_TURN
				&& accepted.party().getMembers().equals(List.of(picker, recipient)), "INVALID Q12 party loot: native two-member BY_TURN order differs: " + accepted.outcome());
			// Native Party starts itemLastLoot=0 and increments before selection, so its first stock Item goes to member1.
		}
		finally { if (invite.identity() != null) { service.cancel(invite.identity()); } }
	}

	private static long count(Player player, int itemId) { return player.getInventory().getInventoryItemCount(itemId, -1); }
	private static String fullHash(Player player)
	{
		return PhantomBackgroundInventoryHash.compute(player.getInventory().getItems().stream().filter(item -> item.getCount() > 0 && (item.getItemLocation().name().equals("INVENTORY") || item.getItemLocation().name().equals("PAPERDOLL")))
			.map(item -> new CanonicalItem(item.getObjectId(), item.getId(), item.getCount(), ItemLocation.valueOf(item.getItemLocation().name()))).toList());
	}
	private static void await(BooleanSupplier condition, long milliseconds, String message) throws InterruptedException
	{
		final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(milliseconds);
		while (!condition.getAsBoolean() && System.nanoTime() < deadline) { Thread.sleep(5); }
		PhantomAssertions.assertTrue(condition.getAsBoolean(), message);
	}
	private static final class LateJoin
	{
		private final Player player;
		private final Managed managed;
		private final boolean seal;
		private final CountDownLatch checkpointEntered = new CountDownLatch(1), releaseCheckpoint = new CountDownLatch(1);
		private final AtomicReference<Throwable> checkpointFailure = new AtomicReference<>();
		private Thread checkpoint;
		LateJoin(Player player, Managed managed, boolean seal) { this.player = player; this.managed = managed; this.seal = seal; }
		void join(Player picker, Player recipient, Managed managedPicker) throws Exception
		{
			withMembers(managedPicker, managed, () ->
			{
				final var service = PartyInvitationService.getInstance(); final var invite = service.invite(picker, player, PartyDistributionType.BY_TURN.getId());
				try
				{
					PhantomAssertions.assertTrue(invite.delivered(), "INVALID late party loot: native invitation rejected: " + invite.outcome());
					final var accepted = service.respond(player, Response.ACCEPT, invite.identity());
					PhantomAssertions.assertTrue(accepted.accepted() && accepted.party() == picker.getParty() && accepted.party() == player.getParty()
						&& accepted.party().getMembers().equals(List.of(picker, recipient, player)), "INVALID late party loot: actual third-member acceptance/order failed.");
					player.getStatus().stopHpMpRegeneration();
				}
				finally { if (invite.identity() != null) { service.cancel(invite.identity()); } }
			});
			final var owner = managed == null ? null : scope(player, managed);
			await(() -> owner == null || owner.outstanding() == 0, 2000, "Late newcomer setup still owns native tickets.");
			if (!seal) { return; }
			PhantomAssertions.assertTrue(owner != null, "INVALID ordinary late party loot: no managed checkpoint exists.");
			checkpoint = Thread.ofPlatform().daemon().name("m1-late-party-recipient-checkpoint").unstarted(() ->
			{
				try
				{
					PlayerNativeWork.checkpoint(player, () ->
					{
						checkpointEntered.countDown();
						try { PhantomAssertions.assertTrue(releaseCheckpoint.await(6, TimeUnit.SECONDS), "Late newcomer checkpoint Hold timed out."); }
						catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
						return null;
					});
				}
				catch (Throwable failure) { checkpointFailure.set(failure); }
			});
			checkpoint.start(); PhantomAssertions.assertTrue(checkpointEntered.await(2, TimeUnit.SECONDS) && owner.sealed(), "INVALID late party loot: newcomer native checkpoint did not seal before original Item release.");
		}
		void finish() throws Exception
		{
			releaseCheckpoint.countDown(); if (checkpoint == null) { return; }
			checkpoint.join(5000);
			PhantomAssertions.assertTrue(!checkpoint.isAlive() && checkpointFailure.get() == null, "Late newcomer native checkpoint did not finish/reopen: " + checkpointFailure.get());
		}
	}
	private record Observation(int recipientOutstanding, boolean checkpointCrossed) { }
}
