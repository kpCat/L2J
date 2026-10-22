/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.data.xml.InitialEquipmentData;
import org.l2jmobius.gameserver.data.xml.InitialShortcutData;
import org.l2jmobius.gameserver.data.xml.MapRegionData;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.managers.InstanceManager;
import org.l2jmobius.gameserver.managers.ScriptManager;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance;
import org.l2jmobius.gameserver.model.groups.CommandChannel;
import org.l2jmobius.gameserver.model.groups.Party;
import org.l2jmobius.gameserver.model.groups.PartyDistributionType;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.scripting.ScriptEngine;

/** Calls the actual protected admission method, without opening/completing an encounter. */
public final class PhantomPlayable069EpicNative
{
	public static void main(String[] args) throws Exception
	{
		if (args.length != 0 || !PhantomTask069Lane.enabled()) { throw new IllegalArgumentException("Own069 native epic admission required."); }
		final Path module = Path.of(System.getProperty("phantom.module.root")).toRealPath();
		final Path report = module.getParent().resolve(".phantom-local/task069/reports/epic-native-" + System.currentTimeMillis() + ".tsv");
		final var players = new ArrayList<Player>(); Npc mozella = null;
		final StringBuilder evidence = new StringBuilder("stage\tvalue\n");
		try
		{
			PhantomHeadlessPlayerTestEnvironment.initializeNativeInfrastructure(new PhantomTestContext(69006903L, module, report.getParent()));
			InitialEquipmentData.getInstance(); InitialShortcutData.getInstance(); MapRegionData.getInstance();
			ScriptEngine.getInstance().executeScript(ScriptEngine.MASTER_HANDLER_FILE);
			ScriptEngine.getInstance().executeScript(Path.of("ai/bosses/Frintezza/LastImperialTomb.java"));
			ScriptEngine.getInstance().executeScript(Path.of("ai/areas/CrumaTower/CrumaTower.java"));
			try (var connection = DatabaseFactory.getConnection(); var insert = connection.prepareStatement("INSERT INTO accounts(login,password,accessLevel,lastServer) VALUES('task069epic','DISABLED_NATIVE_TEST_ONLY',-1,1)")) { insert.executeUpdate(); }
			for (int index = 0; index < 9; index++)
			{
				final var player = Player.create(PlayerTemplateData.getInstance().getTemplate(0), "task069epic", "Entry069" + index, new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false));
				check(player != null && player.getLevel() == 1 && !player.isGM() && player.getClient() == null, "Level1 ordinary native admission fixture required.");
				players.add(player); player.setCurrentHp(player.getMaxHp()); player.setCurrentMp(player.getMaxMp()); player.setOnlineStatus(true, false); player.spawnMe(81700 + index * 10, 148500, -3470);
			}
			final var owner = players.getFirst();
			final var script = ScriptManager.getInstance().getScript("LastImperialTomb");
			check(script != null, "Actual Frintezza owner missing.");
			final var admission = script.getClass().getDeclaredMethod("checkConditions", Player.class); admission.setAccessible(true);
			check(!(boolean) admission.invoke(script, owner), "Party-less entry accepted.");
			final Party party = new Party(owner, PartyDistributionType.FINDERS_KEEPERS); owner.setParty(party);
			check(!(boolean) admission.invoke(script, owner), "Missing command channel accepted.");
			final CommandChannel channel = new CommandChannel(owner);
			owner.addItem(ItemProcessType.REWARD, 8073, 1, owner, false);
			check(!(boolean) admission.invoke(script, owner), "Population minimum bypassed.");
			for (int index = 1; index < players.size(); index++) { players.get(index).setParty(party); party.addPartyMember(players.get(index)); }
			check(channel.getMemberCount() == 9 && (boolean) admission.invoke(script, owner), "Lawful level1 command channel9 still refused.");
			check(!(boolean) admission.invoke(script, players.get(1)), "Foreign command-channel owner accepted.");
			InstanceManager.getInstance().setInstanceTime(players.getLast().getObjectId(), 136, System.currentTimeMillis() + 60000);
			check(!(boolean) admission.invoke(script, owner), "Native reuse gate bypassed.");
			evidence.append("frintezza\tactual checkConditions; level1x9 PASS; noParty/noChannel/minPopulation/foreignLeader/reuse REFUSED; scroll8073 is FIXTURE_PRECONDITION; no instance opened/reward granted\n");
			final var cruma = ScriptManager.getInstance().getScript("CrumaTower");
			check(cruma != null, "Actual Cruma shortcut missing.");
			mozella = new Npc(NpcData.getInstance().getTemplate(30483));
			owner.getStat().setLevel((byte) 80); owner.setInstanceId(0);
			cruma.onEvent("ENTER", mozella, owner); owner.onTeleported();
			check(owner.getInstanceId() == 0 && owner.getY() == 113968 && (owner.getX() == 17776 || owner.getX() == 17680), "Native Core/Cruma open-world shortcut still refuses80 or invents an instance.");
			evidence.append("core\tactual Mozella30483 ENTER at80; native stock coordinates; instance0; no fake Cruma instance\n");
			System.out.println("OWN069_EPIC_NATIVE_PASS " + report);
		}
		finally
		{
			Files.writeString(report, evidence);
			if (mozella != null) { mozella.deleteMe(); }
			for (var player : players) { player.setParty(null); player.stopAllTasks(); player.storeMe(); player.deleteMe(); }
			ThreadPool.shutdown(); DatabaseFactory.close();
		}
	}

	private static void check(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}
