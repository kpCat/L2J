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
package org.l2jmobius.gameserver.phantoms.commerce;

import java.util.Objects;
import java.util.Optional;

import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;

/**
 * Optimistic adapter over the existing bounded profile component.
 */
public final class PhantomCommerceReceiptStore implements PhantomCommerceService.ReceiptPersistence
{
	public static final long ABSENT_ROW_VERSION = -1;

	private final PhantomProfileRepository _repository;

	public PhantomCommerceReceiptStore(PhantomProfileRepository repository)
	{
		_repository = Objects.requireNonNull(repository);
	}

	@Override
	public Optional<VersionedReceipt> find(long profileId)
	{
		return _repository.findComponent(profileId, PhantomCommerceReceipt.COMPONENT_TYPE).map(this::decode);
	}

	@Override
	public VersionedReceipt save(long expectedRowVersion, PhantomCommerceReceipt receipt)
	{
		Objects.requireNonNull(receipt);
		final PhantomProfileComponent component;
		if (expectedRowVersion == ABSENT_ROW_VERSION)
		{
			component = _repository.insertComponent(receipt.profileId(), PhantomCommerceReceipt.COMPONENT_TYPE, PhantomCommerceReceipt.SCHEMA_VERSION, receipt.encode());
		}
		else
		{
			component = _repository.updateComponent(receipt.profileId(), PhantomCommerceReceipt.COMPONENT_TYPE, expectedRowVersion, PhantomCommerceReceipt.SCHEMA_VERSION, receipt.encode());
		}
		return decode(component);
	}

	private VersionedReceipt decode(PhantomProfileComponent component)
	{
		if (component.componentSchemaVersion() != PhantomCommerceReceipt.SCHEMA_VERSION)
		{
			throw new IllegalStateException("Unsupported commerce.operation schema " + component.componentSchemaVersion() + ".");
		}
		return new VersionedReceipt(component.rowVersion(), PhantomCommerceReceipt.decode(component.payload()));
	}

	public record VersionedReceipt(long rowVersion, PhantomCommerceReceipt receipt)
	{
		public VersionedReceipt
		{
			if (rowVersion < 0)
			{
				throw new IllegalArgumentException("Receipt row version must not be negative.");
			}
			Objects.requireNonNull(receipt);
		}
	}
}
