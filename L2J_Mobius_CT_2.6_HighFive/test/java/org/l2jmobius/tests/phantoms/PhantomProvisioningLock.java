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
package org.l2jmobius.tests.phantoms;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

public final class PhantomProvisioningLock implements AutoCloseable
{
	private final FileChannel _channel;
	private final FileLock _lock;
	private final String _ownerToken;
	private boolean _closed;

	private PhantomProvisioningLock(FileChannel channel, FileLock lock, String ownerToken)
	{
		_channel = channel;
		_lock = lock;
		_ownerToken = ownerToken;
	}

	public static PhantomProvisioningLock acquire(Path lockFile) throws IOException, ProvisioningLockException
	{
		final Path parent = lockFile.toAbsolutePath().normalize().getParent();
		if (parent == null)
		{
			throw new IOException("Provisioning lock path has no parent directory.");
		}
		Files.createDirectories(parent);
		final FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
		FileLock lock = null;
		try
		{
			try
			{
				lock = channel.tryLock();
			}
			catch (OverlappingFileLockException e)
			{
				throw new ProvisioningLockException("Another Phantom test DB provisioning process is active.", e);
			}
			if (lock == null)
			{
				throw new ProvisioningLockException("Another Phantom test DB provisioning process is active.");
			}

			final String ownerToken = ProcessHandle.current().pid() + ":" + UUID.randomUUID();
			final ByteBuffer token = StandardCharsets.UTF_8.encode(ownerToken + System.lineSeparator());
			channel.truncate(0);
			channel.position(0);
			while (token.hasRemaining())
			{
				channel.write(token);
			}
			channel.force(true);
			return new PhantomProvisioningLock(channel, lock, ownerToken);
		}
		catch (Throwable throwable)
		{
			if (lock != null)
			{
				try
				{
					lock.release();
				}
				catch (IOException e)
				{
					throwable.addSuppressed(e);
				}
			}
			try
			{
				channel.close();
			}
			catch (IOException e)
			{
				throwable.addSuppressed(e);
			}
			throw throwable;
		}
	}

	public String ownerToken()
	{
		return _ownerToken;
	}

	@Override
	public void close() throws IOException
	{
		if (_closed)
		{
			return;
		}
		_closed = true;
		IOException failure = null;
		try
		{
			if (_lock.isValid())
			{
				_lock.release();
			}
		}
		catch (IOException e)
		{
			failure = e;
		}
		try
		{
			_channel.close();
		}
		catch (IOException e)
		{
			if (failure == null)
			{
				failure = e;
			}
			else
			{
				failure.addSuppressed(e);
			}
		}
		if (failure != null)
		{
			throw failure;
		}
	}

	public static final class ProvisioningLockException extends PhantomTestConfigurationException
	{
		private static final long serialVersionUID = 1L;

		public ProvisioningLockException(String message)
		{
			super(message);
		}

		public ProvisioningLockException(String message, Throwable cause)
		{
			super(message, cause);
		}
	}
}
