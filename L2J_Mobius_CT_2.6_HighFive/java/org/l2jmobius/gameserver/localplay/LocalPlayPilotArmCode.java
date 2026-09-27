package org.l2jmobius.gameserver.localplay;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/** The eight characters typed by a player are separate from the private permit nonce. */
public final class LocalPlayPilotArmCode
{
	private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

	private LocalPlayPilotArmCode()
	{
	}

	public static boolean isValid(String code)
	{
		if ((code == null) || (code.length() != 8))
		{
			return false;
		}
		for (int index = 0; index < code.length(); index++)
		{
			if (ALPHABET.indexOf(Character.toUpperCase(code.charAt(index))) < 0)
			{
				return false;
			}
		}
		return true;
	}

	public static boolean matches(String code, String expectedHash)
	{
		if (!isValid(code) || (expectedHash == null) || !expectedHash.matches("[0-9a-f]{64}"))
		{
			return false;
		}
		try
		{
			final byte[] actual = MessageDigest.getInstance("SHA-256").digest(code.toUpperCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
			return MessageDigest.isEqual(actual, HexFormat.of().parseHex(expectedHash));
		}
		catch (NoSuchAlgorithmException exception)
		{
			throw new IllegalStateException("SHA-256 is unavailable.", exception);
		}
	}
}
