/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.qol;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.l2jmobius.gameserver.config.custom.CommunityBoardConfig;
import org.l2jmobius.gameserver.model.actor.Player;

/** Immutable source-verified aliases; actual item/Fame transfers remain in native multisell. */
public final class Economy071OlympiadShop
{
	private Economy071OlympiadShop() { }
	public static boolean isAlias(int list) { return list == 91071 || list == 91072; }
	public static boolean eligible(Player player)
	{
		// These are the same non-level gates as OlympiadManagerLink.olympiadnoble7/8.
		return CommunityBoardConfig.CUSTOM_CB_ENABLED && CommunityBoardConfig.COMMUNITYBOARD_ENABLE_MULTISELLS && Economy070Shop.eligible(player)
			&& !player.isCursedWeaponEquipped() && player.getClassIndex() == 0 && player.isNoble() && player.getPlayerClass().level() >= 3;
	}
	public static boolean verified(int list)
	{
		if (!isAlias(list)) { return false; }
		return Verified.read();
	}
	private static final class Verified
	{
		private static boolean read()
		{
			try
			{
				return hash(102).equals("e551a2a3fa524607ea07c322e80f0ea9a77e96c5b8363a474d35b97ad810e22c")
					&& hash(103).equals("fbf5cd624b5af56918af3daad5fa3189c7e0579e704d1b4110d96a15faa3c3da")
					&& hash(91071).equals("0adea858530d0df09aed8a687892f29856ded3617e0ad94a205c717af51c09a9")
					&& hash(91072).equals("2064b6603a8ff17923d3b8982166bcdb250db61ce79ab2d7b28b910e9cea3458");
			}
			catch (Exception invalid) { return false; }
		}
		private static String hash(int list) throws Exception
		{
			final Path path = Path.of("data/multisell/" + list + ".xml");
			if (!Files.isRegularFile(path) || Files.size(path) > 131072) { throw new IllegalStateException("Olympiad source unavailable."); }
			// Git/archive line-ending conversion changes no offer; every other byte remains pinned.
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8)));
		}
	}
}
