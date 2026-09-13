/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.qol;

import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.TimeUnit;

import org.l2jmobius.commons.network.Connection;
import org.l2jmobius.commons.network.ConnectionConfig;
import org.l2jmobius.commons.network.PacketExecutor;
import org.l2jmobius.commons.network.ReadHandler;
import org.l2jmobius.commons.network.WriteHandler;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.network.GameClient;

/** Real local socket-backed client used by the focused QOL packet suites. */
final class QoLTestClient implements AutoCloseable
{
	private final Player _player;
	private final Connection<GameClient> _connection;
	private final AsynchronousSocketChannel _peer;
	private final AsynchronousServerSocketChannel _server;
	private final GameClient _client;

	private QoLTestClient(Player player, Connection<GameClient> connection, AsynchronousSocketChannel peer, AsynchronousServerSocketChannel server, GameClient client)
	{
		_player = player;
		_connection = connection;
		_peer = peer;
		_server = server;
		_client = client;
	}

	static QoLTestClient attach(Player player) throws Exception
	{
		final AsynchronousServerSocketChannel server = AsynchronousServerSocketChannel.open().bind(new InetSocketAddress("127.0.0.1", 0));
		final var accepted = server.accept();
		final AsynchronousSocketChannel channel = AsynchronousSocketChannel.open();
		channel.connect(server.getLocalAddress()).get(5, TimeUnit.SECONDS);
		final AsynchronousSocketChannel peer = accepted.get(5, TimeUnit.SECONDS);
		final ConnectionConfig config = new ConnectionConfig(server.getLocalAddress());
		final PacketExecutor<GameClient> executor = new PacketExecutor<>(config);
		final ReadHandler<GameClient> reader = new ReadHandler<>((buffer, client) -> null, executor);
		final Connection<GameClient> connection = new Connection<>(channel, reader, new WriteHandler<>(), config);
		final GameClient client = new GameClient(connection);
		client.setAccountName(player.getAccountNamePlayer());
		connection.setClient(client);
		client.setPlayer(player);
		player.setClient(client);
		return new QoLTestClient(player, connection, peer, server, client);
	}

	GameClient client()
	{
		return _client;
	}

	@Override
	public void close() throws Exception
	{
		_player.setClient(null);
		_client.setPlayer(null);
		_connection.close();
		_peer.close();
		_server.close();
	}
}
