/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver;

import java.net.InetSocketAddress;

public final class GameServerBindAddressTest
{
	public static void main(String[] args)
	{
		final int port = 7777;
		final InetSocketAddress wildcard = GameServer.gameBindAddress("0.0.0.0", port);
		if (!wildcard.equals(new InetSocketAddress(port)))
		{
			throw new AssertionError("Default game bind must preserve the platform wildcard address: " + wildcard);
		}

		final InetSocketAddress loopback = GameServer.gameBindAddress("127.0.0.1", port);
		if (loopback.isUnresolved() || !"127.0.0.1".equals(loopback.getAddress().getHostAddress()) || (loopback.getPort() != port))
		{
			throw new AssertionError("Explicit IPv4 loopback game bind was not preserved: " + loopback);
		}

		System.out.println("PASS game-bind.default-wildcard-and-explicit-loopback");
	}
}
