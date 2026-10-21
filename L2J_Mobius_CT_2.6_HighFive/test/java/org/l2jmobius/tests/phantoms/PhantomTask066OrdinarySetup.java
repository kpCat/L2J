/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.commons.util.HexUtil;
import org.l2jmobius.gameserver.data.xml.InitialEquipmentData;
import org.l2jmobius.gameserver.data.xml.InitialShortcutData;
import org.l2jmobius.gameserver.data.xml.MapRegionData;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance;

/** A native ordinary level1 character supplies a passive saved viewpoint, never a synthetic session. */
public final class PhantomTask066OrdinarySetup
{
	public static void main(String[] args) throws Exception
	{
		if (args.length != 0 || !PhantomTask066PersistentLane.enabled()) { throw new IllegalArgumentException("Explicit TASK066 persistent preparation required."); }
		final Path module = Path.of(System.getProperty("phantom.module.root")).toRealPath();
		final Path runtime = Path.of(System.getProperty("phantom.task066.runtime")).toRealPath();
		final Path config = Path.of(System.getProperty("phantom.test.config"));
		PhantomTask066PersistentLane.validate(module, config);
		final Path privateRoot = runtime.resolve("private");
		if (!Files.isDirectory(privateRoot, LinkOption.NOFOLLOW_LINKS) || !privateRoot.equals(privateRoot.toRealPath())) { throw new IllegalArgumentException("Own066 physical private directory required."); }
		final Path receipt = privateRoot.resolve("observer066.properties");
		final Path login = privateRoot.resolve("OWNER_LOGIN066.txt");
		PhantomAssertions.assertFalse(Files.exists(receipt, LinkOption.NOFOLLOW_LINKS) || Files.exists(login, LinkOption.NOFOLLOW_LINKS), "TASK066 observer already prepared or partly prepared; no overwrite.");
		Player view = null;
		try
		{
			PhantomHeadlessPlayerTestEnvironment.initializeNativeInfrastructure(new PhantomTestContext(66006602, module, module.getParent().resolve(".phantom-local/task066/reports/ordinary-setup")));
			for (String table : List.of("accounts", "characters", "phantom_profiles", "phantom_profile_components"))
			{
				try (var connection = DatabaseFactory.getConnection(); var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) { PhantomAssertions.assertTrue(rows.next() && rows.getLong(1) == 0, "New066 preparation refuses existing data: " + table); }
			}
			InitialEquipmentData.getInstance(); InitialShortcutData.getInstance(); MapRegionData.getInstance();
			final int x = -71350, y = 258050, z = GeoEngine.getInstance().getHeight(x, y, -3104);
			PhantomAssertions.assertTrue(GeoEngine.getInstance().hasGeo(x, y) && Math.abs(z + 3104) <= 64 && GeoEngine.getInstance().canMoveToTarget(x, y, z, x + 64, y, z, 0), "Declared066 passive viewpoint has no native geometry.");
			final String password = Base64.getUrlEncoder().withoutPadding().encodeToString(HexUtil.generateHexBytes(12));
			final String hash = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA").digest(password.getBytes(StandardCharsets.UTF_8)));
			try (var connection = DatabaseFactory.getConnection(); var insert = connection.prepareStatement("INSERT INTO accounts (login,password,accessLevel,lastServer) VALUES (?, ?, 0, 1)"))
			{
				insert.setString(1, "task066player"); insert.setString(2, hash); PhantomAssertions.assertEquals(1, insert.executeUpdate(), "New066 account was not created.");
			}
			Files.writeString(login, "account=task066player\npassword=" + password + "\ncharacter=Task066View\naccessLevel=0\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
			view = Player.create(PlayerTemplateData.getInstance().getTemplate(0), "task066player", "Task066View", new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false));
			PhantomAssertions.assertTrue(view != null && !view.isGM() && view.getLevel() == 1 && view.getClient() == null && !view.hasHeadlessOutboundSession(), "Native066 ordinary level1 observer creation failed.");
			view.setXYZInvisible(x, y, z);
			view.setOnlineStatus(true, false); view.stopAllTasks(); view.storeMe();
			final int objectId = view.getObjectId();
			view.deleteMe(); view = null;
			try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT online,accesslevel,level FROM characters WHERE charId=? AND account_name=? AND char_name=?"))
			{
				query.setInt(1, objectId); query.setString(2, "task066player"); query.setString(3, "Task066View");
				try (var rows = query.executeQuery()) { PhantomAssertions.assertTrue(rows.next() && rows.getInt(1) == 0 && rows.getInt(2) == 0 && rows.getInt(3) == 1 && !rows.next(), "Native066 passive observer persistence differs."); }
			}
			for (String table : List.of("phantom_profiles", "phantom_profile_components"))
			{
				try (var connection = DatabaseFactory.getConnection(); var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) { PhantomAssertions.assertTrue(rows.next() && rows.getLong(1) == 0, "Ordinary066 preparation granted Phantom state: " + table); }
			}
			Files.writeString(receipt, "objectId=" + objectId + "\nname=Task066View\nx=" + x + "\ny=" + y + "\nz=" + z + "\nmode=PREPARED_PASSIVE_VIEWPOINT\nactualNetworkSession=false\nsynthetic=false\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
			System.out.println("TASK066_OBSERVER_PREPARED objectId=" + objectId + "; level=1; access=0; no synthetic/network session; Phantom population not granted.");
		}
		finally
		{
			try { if (view != null) { view.stopAllTasks(); view.deleteMe(); } }
			finally { ThreadPool.shutdown(); DatabaseFactory.close(); }
		}
	}
}
