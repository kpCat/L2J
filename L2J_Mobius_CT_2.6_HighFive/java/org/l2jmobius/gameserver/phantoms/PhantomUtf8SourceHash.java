/*
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 */
package org.l2jmobius.gameserver.phantoms;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Strict UTF-8 SHA-256 identity that canonicalizes line endings only. */
public final class PhantomUtf8SourceHash
{
	private PhantomUtf8SourceHash()
	{
	}

	public static String sha256(byte[] source)
	{
		final String text;
		try
		{
			text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(source)).toString();
		}
		catch (CharacterCodingException exception)
		{
			throw new IllegalArgumentException("Source is not strict UTF-8.", exception);
		}
		final byte[] canonical = text.replace("\r\n", "\n").replace('\r', '\n').getBytes(StandardCharsets.UTF_8);
		try
		{
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
		}
		catch (Exception exception)
		{
			throw new IllegalStateException("SHA-256 is unavailable.", exception);
		}
	}
}
