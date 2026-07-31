/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.social;

import java.util.Objects;

import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SocialEvent;

/**
 * Downstream-only structured event seam. Canonical game facts never depend on
 * social persistence success.
 */
@FunctionalInterface
public interface PhantomSocialEventSink
{
	enum Status
	{
		READY,
		INITIALIZED,
		RECORDED,
		IDEMPOTENT,
		DISABLED,
		NOT_RUNNING,
		PROFILE_NOT_FOUND,
		AUTHORITY_STALE,
		INCONSISTENT,
		CAPACITY_REACHED,
		CONFLICT
	}

	record Result(Status status, String detail)
	{
		public Result
		{
			Objects.requireNonNull(status);
			detail = detail == null ? "" : detail;
			if (detail.length() > 128)
			{
				throw new IllegalArgumentException("Social sink result detail exceeds 128 characters.");
			}
		}

		public boolean durable()
		{
			return (status == Status.RECORDED) || (status == Status.IDEMPOTENT);
		}
	}

	Result record(SocialEvent event);

	static PhantomSocialEventSink noop()
	{
		return event -> new Result(Status.DISABLED, "social.disabled");
	}
}
