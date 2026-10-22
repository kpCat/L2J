/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.data.xml.InitialEquipmentData;
import org.l2jmobius.gameserver.data.xml.InitialShortcutData;
import org.l2jmobius.gameserver.data.xml.MapRegionData;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.qol.QoLRift068Native;
import org.l2jmobius.gameserver.scripting.ScriptEngine;

/** Exact own068 service fixture. No GameClient, headless session or access escalation. */
public final class PhantomRift068Native
{
	public static void main(String[] args) throws Exception
	{
		if (args.length != 0 || !PhantomTask068Lane.enabled()) { throw new IllegalArgumentException("Explicit own068 native Rift lane required."); }
		final Path module = Path.of(System.getProperty("phantom.module.root")).toRealPath();
		final Path task = module.getParent().resolve(".phantom-local/task068").toRealPath();
		Player owner = null;
		Player peer = null;
		Npc npc = null;
		try
		{
			PhantomHeadlessPlayerTestEnvironment.initializeNativeInfrastructure(new PhantomTestContext(68006807L, module, task.resolve("reports/rift-native")));
			InitialEquipmentData.getInstance(); InitialShortcutData.getInstance(); MapRegionData.getInstance();
			ScriptEngine.getInstance().executeScript(ScriptEngine.MASTER_HANDLER_FILE);
			try (var connection = DatabaseFactory.getConnection(); var insert = connection.prepareStatement("INSERT IGNORE INTO accounts(login,password,accessLevel,lastServer) VALUES('task068rift','DISABLED_NATIVE_TEST_ONLY',0,1)")) { insert.executeUpdate(); }
			owner = fixture("Rift068Owner"); peer = fixture("Rift068Peer");
			npc = new Npc(NpcData.getInstance().getTemplate(31494));
			npc.spawnMe(owner.getX() + 20, owner.getY(), owner.getZ());
			QoLRift068Native.run(owner, peer, npc, task);
			System.out.println("OWN068_RIFT_NATIVE_PASS; production=" + Player.class.getProtectionDomain().getCodeSource().getLocation());
		}
		finally
		{
			try
			{
				if (npc != null) { npc.deleteMe(); }
				for (Player player : new Player[] {owner, peer})
				{
					if (player != null) { player.stopAllTasks(); player.storeMe(); player.deleteMe(); }
				}
			}
			finally { ThreadPool.shutdown(); DatabaseFactory.close(); }
		}
	}

	private static Player fixture(String name) throws Exception
	{
		Integer id = null;
		try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT charId FROM characters WHERE account_name='task068rift' AND char_name=? AND accesslevel=0"))
		{
			query.setString(1, name);
			try (var rows = query.executeQuery()) { if (rows.next()) { id = rows.getInt(1); if (rows.next()) { throw new IllegalStateException("Ambiguous own068 Rift actor."); } } }
		}
		final Player player = id == null ? Player.create(PlayerTemplateData.getInstance().getTemplate(0), "task068rift", name, new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false)) : Player.load(id);
		PhantomAssertions.assertTrue(player != null && !player.isGM() && player.getAccessLevel().getLevel() == 0 && player.getClient() == null && !player.hasHeadlessOutboundSession(), "Own068 Rift actor is not ordinary access0.");
		player.setOnlineStatus(true, false); player.stopAllTasks();
		player.setInvul(true); // Lifecycle fixture only; no combat/reward claim.
		if (player.isDead()) { player.doRevive(); }
		player.getStatus().setCurrentHp(player.getMaxHp()); player.setCurrentMp(player.getMaxMp());
		player.spawnMe(-71350, 258050, -3104);
		final long fragments = player.getInventory().getInventoryItemCount(7079, -1);
		if (fragments > 0) { player.destroyItemByItemId(ItemProcessType.DESTROY, 7079, fragments, null, false); }
		return player;
	}
}
