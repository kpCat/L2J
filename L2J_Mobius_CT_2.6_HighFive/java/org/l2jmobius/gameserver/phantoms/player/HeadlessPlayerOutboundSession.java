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
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.l2jmobius.gameserver.phantoms.player;

import java.util.List;
import java.util.Objects;

import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.network.PlayerOutboundSession;
import org.l2jmobius.gameserver.network.serverpackets.ServerPacket;

/**
 * Zero-transport packet effect dispatcher with bounded reentrant execution.
 */
public final class HeadlessPlayerOutboundSession implements PlayerOutboundSession
{
	private final int _maximumDepth;
	private final int _maximumPacketsPerRoot;
	private final String[] _recordedPacketClasses;
	private int _depth;
	private int _packetsInRoot;
	private int _maximumObservedDepth;
	private int _recordStart;
	private int _recordSize;
	private long _effectCount;
	private long _rejectedCount;
	private long _droppedRecordCount;

	public HeadlessPlayerOutboundSession(int maximumDepth, int maximumPacketsPerRoot)
	{
		this(maximumDepth, maximumPacketsPerRoot, 0);
	}

	public HeadlessPlayerOutboundSession(int maximumDepth, int maximumPacketsPerRoot, int recordingCapacity)
	{
		if ((maximumDepth < 1) || (maximumDepth > 256))
		{
			throw new IllegalArgumentException("maximumDepth must be between 1 and 256");
		}
		if ((maximumPacketsPerRoot < 1) || (maximumPacketsPerRoot > 4096))
		{
			throw new IllegalArgumentException("maximumPacketsPerRoot must be between 1 and 4096");
		}
		if ((recordingCapacity < 0) || (recordingCapacity > 1024))
		{
			throw new IllegalArgumentException("recordingCapacity must be between 0 and 1024");
		}

		_maximumDepth = maximumDepth;
		_maximumPacketsPerRoot = maximumPacketsPerRoot;
		_recordedPacketClasses = recordingCapacity == 0 ? null : new String[recordingCapacity];
	}

	@Override
	public SessionKind kind()
	{
		return SessionKind.HEADLESS;
	}

	@Override
	public synchronized void send(Player player, ServerPacket packet)
	{
		Objects.requireNonNull(player, "player");
		Objects.requireNonNull(packet, "packet");

		final boolean rootDispatch = _depth == 0;
		if (rootDispatch)
		{
			_packetsInRoot = 0;
		}

		if (_depth >= _maximumDepth)
		{
			_rejectedCount++;
			throw new IllegalStateException("Headless packet recursion depth exceeded");
		}
		if (_packetsInRoot >= _maximumPacketsPerRoot)
		{
			_rejectedCount++;
			throw new IllegalStateException("Headless packet root dispatch budget exceeded");
		}

		_depth++;
		_packetsInRoot++;
		_maximumObservedDepth = Math.max(_maximumObservedDepth, _depth);
		_effectCount++;
		record(packet.getClass().getSimpleName());
		try
		{
			packet.runImpl(player);
		}
		finally
		{
			_depth--;
			if (rootDispatch)
			{
				_packetsInRoot = 0;
			}
		}
	}

	private void record(String packetClass)
	{
		if (_recordedPacketClasses == null)
		{
			return;
		}

		if (_recordSize < _recordedPacketClasses.length)
		{
			_recordedPacketClasses[(_recordStart + _recordSize) % _recordedPacketClasses.length] = packetClass;
			_recordSize++;
			return;
		}

		_recordedPacketClasses[_recordStart] = packetClass;
		_recordStart = (_recordStart + 1) % _recordedPacketClasses.length;
		_droppedRecordCount++;
	}

	public synchronized Snapshot snapshot()
	{
		if (_recordedPacketClasses == null)
		{
			return new Snapshot(_maximumDepth, _maximumPacketsPerRoot, 0, _maximumObservedDepth, _effectCount, _rejectedCount, 0, List.of());
		}

		final String[] packetClasses = new String[_recordSize];
		for (int i = 0; i < _recordSize; i++)
		{
			packetClasses[i] = _recordedPacketClasses[(_recordStart + i) % _recordedPacketClasses.length];
		}
		return new Snapshot(_maximumDepth, _maximumPacketsPerRoot, _recordedPacketClasses.length, _maximumObservedDepth, _effectCount, _rejectedCount, _droppedRecordCount, List.of(packetClasses));
	}

	public record Snapshot(int maximumDepth, int maximumPacketsPerRoot, int recordingCapacity, int maximumObservedDepth, long effectCount, long rejectedCount, long droppedRecordCount, List<String> recordedPacketClasses)
	{
	}
}
