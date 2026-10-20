/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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

/** A new native ordinary level1 character supplies a passive saved viewpoint, never a synthetic session. */
public final class PhantomTask064OrdinarySetup
{
	public static void main(String[] args) throws Exception
	{
		if (args.length != 0 || !PhantomTask064PersistentLane.enabled()) { throw new IllegalArgumentException("Explicit TASK064 persistent preparation required."); }
		final Path module = Path.of(System.getProperty("phantom.module.root")).toRealPath();
		final Path runtime = Path.of(System.getProperty("phantom.task064.runtime")).toRealPath();
		final Path receipt = runtime.resolve("private/observer064.properties");
		PhantomAssertions.assertFalse(Files.exists(receipt), "TASK064 observer already prepared; no overwrite.");
		try
		{
			PhantomHeadlessPlayerTestEnvironment.initializeNativeInfrastructure(new PhantomTestContext(64006401, module, module.resolve(".phantom-local/task064/reports/ordinary-setup")));
			for (String table : List.of("accounts", "characters", "phantom_profiles", "phantom_profile_components"))
			{
				try (var connection = DatabaseFactory.getConnection(); var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) { PhantomAssertions.assertTrue(rows.next() && rows.getLong(1) == 0, "New064 preparation refuses existing data: " + table); }
			}
			InitialEquipmentData.getInstance(); InitialShortcutData.getInstance(); MapRegionData.getInstance();
			final String password = Base64.getUrlEncoder().withoutPadding().encodeToString(HexUtil.generateHexBytes(12));
			final String hash = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA").digest(password.getBytes(StandardCharsets.UTF_8)));
			try (var connection = DatabaseFactory.getConnection(); var insert = connection.prepareStatement("INSERT INTO accounts (login,password,accessLevel,lastServer) VALUES (?, ?, 0, 1)")) { insert.setString(1, "task064player"); insert.setString(2, hash); PhantomAssertions.assertEquals(1, insert.executeUpdate(), "New064 account was not created."); }
			Files.writeString(runtime.resolve("private/OWNER_LOGIN064.txt"), "account=task064player\npassword=" + password + "\ncharacter=Task064View\naccessLevel=0\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
			final Player view = Player.create(PlayerTemplateData.getInstance().getTemplate(0), "task064player", "Task064View", new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false));
			PhantomAssertions.assertTrue(view != null && !view.isGM() && view.getLevel() == 1, "Native064 level1 observer creation failed.");
			final int x = -71350, y = 258050, z = GeoEngine.getInstance().getHeight(x, y, -3104);
			PhantomAssertions.assertTrue(GeoEngine.getInstance().hasGeo(x, y) && Math.abs(z + 3104) <= 64 && GeoEngine.getInstance().canMoveToTarget(x, y, z, x + 64, y, z, 0), "Declared064 passive viewpoint has no native geometry.");
			view.setXYZInvisible(x, y, z);
			view.setOnlineStatus(true, false); view.stopAllTasks(); view.storeMe(); view.deleteMe();
			Files.writeString(receipt, "objectId=" + view.getObjectId() + "\nname=Task064View\nx=" + x + "\ny=" + y + "\nz=" + z + "\nmode=PREPARED_PASSIVE_VIEWPOINT\nactualNetworkSession=false\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
			System.out.println("TASK064_OBSERVER_PREPARED objectId=" + view.getObjectId() + "; level=1; access=0; no synthetic/network session; Phantom population not granted.");
		}
		finally { ThreadPool.shutdown(); DatabaseFactory.close(); }
	}
}
