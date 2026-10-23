/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import org.l2jmobius.gameserver.data.xml.MultisellData;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.instance.Folk;
import org.l2jmobius.gameserver.model.item.type.CrystalType;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.multisell.Entry;
import org.l2jmobius.gameserver.network.clientpackets.MultiSellChoose;

/** Native packet writer against a real nearby NPC. All source gear/funds are declared TEST fixtures. */
final class PhantomMammon071Native
{
	static void run(Player player, StringBuilder report)
	{
		if (!PhantomTask071Lane.enabled()) { throw new IllegalArgumentException("Own071 TEST required."); }
		final var pushkin = new Folk(NpcData.getInstance().getTemplate(30300));
		final var original = new Folk(NpcData.getInstance().getTemplate(31126));
		try
		{
			pushkin.spawnMe(77458,148169,-3595); original.spawnMe(77510,148169,-3595);
			player.teleToLocation(77460,148170,-3595); player.setLastFolkNPC(pushkin);
			player.addItem(ItemProcessType.REWARD,5575,5_000_000,player,false);
			player.addAdena(ItemProcessType.REWARD,100_000,player,false);
			report.append("mammonSetup\tFIXTURE: source gear/materials and5mAA/100kAdena; position set before each isolated trade; no earned claim\n");
			trade(player,pushkin,311132501,0,0,12374,0,report);
			check(count(player,12374)>=1,"Paid real Varnish Enhancer source unavailable.");
			trade(player,pushkin,311262509,4681,4,72,4,report);
			for (CrystalType grade : new CrystalType[] {CrystalType.D,CrystalType.B})
			{
				quote(player,pushkin,311262512,false);
				final Entry offer=player.getMultiSell().getEntries().stream().filter(entry -> entry.getIngredients().getFirst().getTemplate().getCrystalType()==grade).findFirst().orElseThrow();
				trade(player,pushkin,311262512,offer.getIngredients().getFirst().getItemId(),5,offer.getProducts().getFirst().getItemId(),5,report);
			}
			trade(player,pushkin,311262519,7894,6,150,6,report);
			trade(player,pushkin,311262504,9514,3,9416,3,report);
			trade(player,pushkin,311262513,12375,0,10870,0,report);
			check(count(player,12374)==0,"Foundation did not consume real paid Varnish Enhancer.");
			player.addItem(ItemProcessType.REWARD,4681,1,player,false);
			quote(player,pushkin,311262509,true); final int entry=player.getMultiSell().getEntries().getFirst().getEntryId();
			final long source=count(player,4681), product=count(player,72);
			player.teleToLocation(82698,148638,-3473); MultiSellChoose.executePreparedExchange(player,311262509,entry,1);
			check(count(player,4681)==source && count(player,72)==product && player.getMultiSell()==null,"Moved-away native quote accepted.");
			player.teleToLocation(77460,148170,-3595); quote(player,pushkin,311262509,true); final int foreign=player.getMultiSell().getEntries().getFirst().getEntryId();
			player.setLastFolkNPC(original); MultiSellChoose.executePreparedExchange(player,311262509,foreign,1);
			check(count(player,4681)==source && count(player,72)==product && player.getMultiSell()==null,"Different NPC accepted prepared quote.");
			player.teleToLocation(77512,148170,-3595); player.setLastFolkNPC(original);
			trade(player,original,311262509,0,0,72,0,report);
			report.append("mammonGuards\tactual range/NPC mismatch refusals; original31126 removeSA still works; materials/AA/native enchant consumed, no GameClient\n");
		}
		finally { player.setLastFolkNPC(null); pushkin.deleteMe(); original.deleteMe(); }
	}
	private static void trade(Player player,Folk npc,int list,int sourceId,int enchant,int productId,int expectedEnchant,StringBuilder report)
	{
		if(sourceId>0) { final Item source=player.addItem(ItemProcessType.REWARD,sourceId,1,player,false); source.setEnchantLevel(enchant); }
		quote(player,npc,list,list!=311132501 && list!=311262513);
		final Entry entry=player.getMultiSell().getEntries().stream().filter(e -> e.getProducts().stream().anyMatch(p -> p.getItemId()==productId) && (sourceId==0 || e.getIngredients().stream().anyMatch(i -> i.getItemId()==sourceId))).findFirst().orElseThrow();
		final var before=new java.util.HashMap<Integer,Long>();
		for(var ingredient:entry.getIngredients()) { before.put(ingredient.getItemId(),count(player,ingredient.getItemId())); }
		final long products=count(player,productId);
		MultiSellChoose.executePreparedExchange(player,list,entry.getEntryId(),1);
		check(count(player,productId)==products+1,"Native Mammon product missing list="+list+" product="+productId);
		for(var ingredient:entry.getIngredients()) { check(count(player,ingredient.getItemId())==before.get(ingredient.getItemId())-ingredient.getItemCount(),"Native Mammon input debit differs: "+ingredient.getItemId()); }
		if(expectedEnchant>0) { check(player.getInventory().getItems().stream().anyMatch(i->i.getId()==productId && i.getEnchantLevel()==expectedEnchant),"Native enchant preservation failed."); }
		report.append("mammonTrade\tlist=").append(list).append(";source=").append(sourceId).append(";product=").append(productId).append(";enchant=").append(expectedEnchant).append(";native input/product delta PASS\n");
	}
	private static void quote(Player player,Folk npc,int list,boolean inventoryOnly)
	{ player.setLastFolkNPC(npc); MultisellData.getInstance().separateAndSend(list,player,npc,inventoryOnly); check(player.getMultiSell()!=null,"Native Mammon quote refused: "+list); }
	private static long count(Player player,int id) { return player.getInventory().getInventoryItemCount(id,-1); }
	private static void check(boolean value,String message) { if(!value) { throw new AssertionError(message); } }
}
