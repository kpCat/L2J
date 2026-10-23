/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.util.List;
import java.util.UUID;

import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.managers.ScriptManager;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.instance.VillageMaster;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.qol.Economy070Pricing;
import org.l2jmobius.gameserver.qol.PaidSubclass070Service;

/** Full native quest event chain with declared fixture inputs for retained, non-bypassed materials. */
public final class PhantomPaidSubclass070Checks
{
	private PhantomPaidSubclass070Checks()
	{
	}

	public static void nativeProgress(Player player, StringBuilder report) throws Exception
	{
		if (!PhantomTask070Lane.enabled()) { throw new IllegalArgumentException("Exact own070 native lane required."); }
		final var pricing = Economy070Pricing.getInstance();
		final var fate = ScriptManager.getInstance().getQuest(234);
		final var mimir = ScriptManager.getInstance().getQuest(235);
		check(fate != null && mimir != null, "Loaded native quest constructors required.");
		final boolean prepaid = PaidSubclass070Service.hasEntitlement(player);
		final var leorin = new Npc(31002); final var cliff = new Npc(30182); final var ferris = new Npc(30847);
		final var zenkin = new Npc(30178); final var kaspar = new Npc(30833);
		final var ladd = new Npc(30721); final var joan = new Npc(30718); final var urn = new Npc(31149);
		final var validator = new QuestValidator();
		final List<Npc> actors = List.of(leorin, cliff, ferris, zenkin, kaspar, ladd, joan, urn, validator);
		try
		{
			check(!validator.accepts(player), "Native subclass quest validator accepted an unqualified actor.");
			mimir.onTalk(ladd, player);
			check(player.getQuestState("Q00235_MimirsElixir").isCreated(), "Mimir started without native Star of Destiny.");
			if (!prepaid) { fate.onTalk(leorin, player); fate.onEvent("QUEST_ACCEPTED", leorin, player); }
			final var state = player.getQuestState("Q00234_FatesWhisper");
			if (!prepaid) { fate.onTalk(leorin, player); check(count(player, 4666) == 0 && state.isMemoState(1), "Unpaid character received raid material."); }
			else { check(state != null && state.isMemoState(1), "Retained paid actor must remain at the unadvanced native first stage."); }
			final int oldLevel = player.getLevel();
			try
			{
				player.getStat().setLevel((byte) 74);
				try { PaidSubclass070Service.purchase(player, UUID.randomUUID().toString(), pricing.revision()); throw new AssertionError("Low level purchase accepted."); }
				catch (IllegalArgumentException expected) { }
			}
			finally { player.getStat().setLevel((byte) oldLevel); }
			final long before = player.getAdena(); final String request = UUID.randomUUID().toString();
			PaidSubclass070Service.purchase(player, request, pricing.revision());
			PaidSubclass070Service.purchase(player, request, pricing.revision());
			PaidSubclass070Service.purchase(player, UUID.randomUUID().toString(), pricing.revision());
			check(player.getAdena() == before - (prepaid ? 0 : pricing.price("subclass.gateway")), "Entitlement was not paid exactly once.");
			fate.onTalk(leorin, player); fate.onTalk(leorin, player);
			check(count(player, 4666) == 1 && state.isMemoState(1), "Paid material duplicated or quest memo was skipped.");
			fate.onEvent("5", leorin, player);
			fate.onTalk(leorin, player); fate.onEvent("6", leorin, player);
			check(state.isMemoState(4) && count(player, 4666) == 0 && count(player, 4667) == 0 && count(player, 4668) == 0 && count(player, 4669) == 0, "Native raid-item consumption failed.");
			cliff.setTarget(player); fate.onEvent("3", cliff, player); fate.onEvent("7", leorin, player);
			fate.onTalk(ferris, player); fate.onEvent("8", leorin, player);
			fate.onTalk(zenkin, player); fate.onEvent("1", zenkin, player);
			fate.onEvent("3", kaspar, player);
			check(state.isMemoState(8) && count(player, 14362) == 30, "Native white-fabric choice missing.");
			fate.onTalk(kaspar, player);
			check(state.isMemoState(9) && count(player, 14361) == 0 && count(player, 4671) == 1, "Paid fabric did not enter native Kaspar exchange.");
			fate.onEvent("9", leorin, player); fate.onEvent("10", leorin, player);
			check(state.isMemoState(10) && count(player, 1460) == 0 && count(player, 79) == 0, "Paid acquisition bypass supplied retained crystals/weapon.");
			// Explicit component fixture inputs, not service rewards: original984 CrystalB and topB Sword of Damascus.
			player.addItem(ItemProcessType.REWARD, 1460, 984, player, false);
			fate.onEvent("10", leorin, player); fate.onEvent("21", leorin, player);
			check(state.isMemoState(11) && !state.isCompleted() && count(player, 1460) == 0, "Missing B weapon did not block native completion.");
			player.addItem(ItemProcessType.REWARD, 79, 1, player, false);
			fate.onEvent("21", leorin, player);
			check(state.isCompleted() && count(player, 79) == 0 && count(player, 5011) == 1 && count(player, 80) == 1, "Native Fate completion/weapon exchange failed.");
			check(!validator.accepts(player), "One completed quest bypassed native Mimir requirement.");
			mimir.onTalk(ladd, player); mimir.onEvent("30721-06.htm", ladd, player);
			final var elixir = player.getQuestState("Q00235_MimirsElixir");
			mimir.onTalk(ladd, player);
			check(elixir.isMemoState(1) && count(player, 6320) == 0, "Paid service supplied retained Pure Silver.");
			// Pure Silver remains a real retained acquisition prerequisite; declared native fixture input here.
			player.addItem(ItemProcessType.REWARD, 6320, 1, player, false);
			mimir.onEvent("30721-12.html", ladd, player); mimir.onEvent("30718-03.html", joan, player);
			mimir.onTalk(joan, player);
			check(elixir.isMemoState(4) && count(player, 6322) == 1, "Actual paid Sage Stone failed native memo3->4 transition.");
			mimir.onEvent("30718-06.html", joan, player); mimir.onEvent("30721-15.html", ladd, player);
			check(elixir.isMemoState(6) && count(player, 5905) == 1 && count(player, 6322) == 0, "Native mixing stone/True Gold progression failed.");
			mimir.onTalk(urn, player);
			check(elixir.isMemoState(7) && count(player, 6318) == 1, "Actual paid Blood Fire failed native memo6->7 transition.");
			mimir.onEvent("31149-11.html", urn, player);
			check(elixir.isMemoState(8) && count(player, 6319) == 1 && count(player, 6318) == 0 && count(player, 6320) == 0 && count(player, 6321) == 0, "Native mixing did not consume all real ingredients.");
			mimir.onEvent("30721-19.html", ladd, player);
			check(elixir.isCompleted() && count(player, 5011) == 0 && count(player, 729) == 1 && validator.accepts(player), "Native final completion/consumption/subclass validator failed.");
			report.append("subclass070.native\tpaidOnce=").append(pricing.price("subclass.gateway")).append(";nativeQ234+Q235completed;raidItemsConsumed;984CrystalB+topB79+PureSilver6320 retained and supplied as explicit component fixture inputs;normalNPCeventchain;nativeVillageMaster.checkQuests=true;actualclientSubclassChange=NOT_RUN\n");
		}
		finally { for (Npc actor : actors) { actor.deleteMe(); } }
	}

	public static void afterRestart(Player reloaded, StringBuilder report) throws Exception
	{
		if (!PhantomTask070Lane.enabled()) { throw new IllegalArgumentException("Exact own070 native lane required."); }
		final var validator = new QuestValidator();
		try
		{
			check(PaidSubclass070Service.hasEntitlement(reloaded) && validator.accepts(reloaded), "Native restored actor lost paid receipt or completed quests.");
			report.append("subclass070.restart\tchar=").append(reloaded.getObjectId()).append(";durableReceipt=true;nativeQuestValidator=true;noAutoReoffer\n");
		}
		finally { validator.deleteMe(); }
	}

	private static final class QuestValidator extends VillageMaster
	{
		private QuestValidator() { super(NpcData.getInstance().getTemplate(31002)); }
		private boolean accepts(Player player) { return checkQuests(player); }
	}

	private static long count(Player player, int id) { return player.getInventory().getInventoryItemCount(id, -1); }
	private static void check(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}
