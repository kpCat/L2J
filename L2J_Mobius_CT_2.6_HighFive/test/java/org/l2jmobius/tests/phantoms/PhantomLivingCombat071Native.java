/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Properties;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.data.xml.InitialEquipmentData;
import org.l2jmobius.gameserver.data.xml.InitialShortcutData;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance;
import org.l2jmobius.gameserver.qol.Economy070Shop;

/** Bounded own071 native acceptance. Fixture setup is kept separate from earned Main evidence. */
public final class PhantomLivingCombat071Native
{
	public static void main(String[] args) throws Exception
	{
		if(args.length!=1 || !java.util.Set.of("prepare","verify","mammon","combat","combat-resume","combat-roles","combat-remaining","grade-resume","reserve","inspect").contains(args[0]) || !PhantomTask071Lane.enabled()) { throw new IllegalArgumentException("Explicit OWN071 native action required."); }
		final Path module=Path.of(System.getProperty("phantom.module.root")).toRealPath();
		final Path task=module.getParent().resolve(".phantom-local/task071");
		final Path receipt=task.resolve("native-acceptance071.properties");
		final var report=new StringBuilder("check\tevidence\n"); final var players=new ArrayList<Player>();
		try
		{
			PhantomHeadlessPlayerTestEnvironment.initializeNativeInfrastructure(new PhantomTestContext(71007101L,module,task.resolve("reports")));
			InitialEquipmentData.getInstance(); InitialShortcutData.getInstance();
			org.l2jmobius.gameserver.scripting.ScriptEngine.getInstance().executeScript(org.l2jmobius.gameserver.scripting.ScriptEngine.MASTER_HANDLER_FILE);
			report.append("source\t").append(PhantomLivingCombat071Native.class.getProtectionDomain().getCodeSource().getLocation()).append("; production=").append(Economy070Shop.class.getProtectionDomain().getCodeSource().getLocation()).append('\n');
			if(args[0].equals("combat")) { PhantomCombat071Native.run(report); }
			else if(args[0].equals("combat-resume")) { PhantomCombat071Native.resume(report); }
			else if(args[0].equals("combat-roles")) { PhantomCombat071Native.roles(report); }
			else if(args[0].equals("combat-remaining")) { PhantomCombat071Native.remainingRoles(report); }
			else if(args[0].equals("grade-resume")) { final Player grade=Player.load(268435468);check(grade!=null,"Retained grade fixture missing.");players.add(grade);PhantomSupply071Native.resumeGrades(grade,report); }
			else if(args[0].equals("reserve")) { PhantomCombat071Native.mixedReserve(report); }
			else if(args[0].equals("inspect")) { PhantomCombat071Native.inspect(report); PhantomSupply071Native.inspectStoredGrades(report); }
			else if(args[0].equals("prepare"))
			{
				check(!Files.exists(receipt),"Existing071 fixture must be verified, never reseeded.");
				for(String account:new String[]{"task071nativeone","task071nativepoor"})
				{
					try(var connection=DatabaseFactory.getConnection();var insert=connection.prepareStatement("INSERT INTO accounts(login,password,accessLevel,lastServer) VALUES(?,'DISABLED_NATIVE_TEST_ONLY',-1,1)")) { insert.setString(1,account); insert.executeUpdate(); }
				}
				final Player first=actor("task071nativeone","Native071One"),poor=actor("task071nativepoor","Native071Poor"); players.add(first);players.add(poor);
				report.append("fixtures\tordinary access0 third class88;disabled network accounts;not owner/earned/RELEASE characters\n");
				PhantomEconomy071Native.run(first,poor,report);
				PhantomMammon071Native.run(first,report);
				final var values=new Properties();values.setProperty("first",Integer.toString(first.getObjectId()));values.setProperty("poor",Integer.toString(poor.getObjectId()));
				for(Player player:players) { save(player);values.setProperty(player.getName()+".inventory",inventory(player));values.setProperty(player.getName()+".wallet",Long.toString(Economy070Shop.getInstance().balance(player))); }
				values.setProperty("stock",Integer.toString(Economy070Shop.getInstance().stock(6660).count()));values.setProperty("due",Long.toString(Economy070Shop.getInstance().stock(6660).nextRestockUtc()));
				try(var writer=Files.newBufferedWriter(receipt,StandardOpenOption.CREATE_NEW)) { values.store(writer,"OWN071 explicit native acceptance snapshots"); }
			}
			else
			{
				final var values=new Properties();try(var reader=Files.newBufferedReader(receipt)) { values.load(reader); }
				for(String key:new String[]{"first","poor"}) { final Player player=Player.load(Integer.parseInt(values.getProperty(key)));check(player!=null,"Retained071 player missing.");player.setOnlineStatus(true,false);player.spawnMe();players.add(player); }
				if(args[0].equals("mammon")) { PhantomMammon071Native.run(players.getFirst(),report); }
				else
				{
					for(Player player:players) { check(inventory(player).equals(values.getProperty(player.getName()+".inventory")),"Native inventory changed across own071 restart: "+player.getName());check(Economy070Shop.getInstance().balance(player)==Long.parseLong(values.getProperty(player.getName()+".wallet")),"Retained savings changed."); }
					check(Economy070Shop.getInstance().stock(6660).count()==Integer.parseInt(values.getProperty("stock")) && Economy070Shop.getInstance().stock(6660).nextRestockUtc()==Long.parseLong(values.getProperty("due")),"Restart reseeded stock or changed restock epoch.");
					PhantomEconomy071Native.verifyRetained(players.getFirst(),report);
					report.append("retention\tidentity/native item-object+count+enchant inventory hashes/savings/stock/restock exact;no reseed\n");
				}
			}
			System.out.println("LIVING_COMBAT071_NATIVE_"+args[0].toUpperCase(java.util.Locale.ROOT)+"_PASS");
		}
		catch(Throwable failure) { report.append("failure\t").append(failure).append('\n');throw failure; }
		finally
		{
			for(Player player:players) { save(player);player.stopAllTasks();player.deleteMe(); }
			Files.writeString(task.resolve("reports/native071-"+args[0]+"-"+System.currentTimeMillis()+".tsv"),report,StandardOpenOption.CREATE_NEW);
			ThreadPool.shutdown();DatabaseFactory.close();
		}
	}
	private static Player actor(String account,String name)
	{
		final Player player=Player.create(PlayerTemplateData.getInstance().getTemplate(88),account,name,new PlayerAppearance((byte)0,(byte)0,(byte)0,false));
		check(player!=null&&!player.isGM()&&!player.hasHeadlessOutboundSession(),"Ordinary071 fixture creation failed.");player.setOnlineStatus(true,false);player.setCurrentHp(player.getMaxHp());player.spawnMe(82698,148638,-3473);return player;
	}
	private static void save(Player player) { player.getInventory().updateDatabase();player.storeMe(); }
	private static String inventory(Player player) throws Exception
	{
		final var digest=java.security.MessageDigest.getInstance("SHA-256");
		for(var item:player.getInventory().getItems().stream().sorted(java.util.Comparator.comparingInt(org.l2jmobius.gameserver.model.item.instance.Item::getObjectId)).toList()) { digest.update((item.getObjectId()+":"+item.getId()+":"+item.getCount()+":"+item.getEnchantLevel()+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
		return java.util.HexFormat.of().formatHex(digest.digest());
	}
	private static void check(boolean value,String message) { if(!value) { throw new AssertionError(message); } }
}
