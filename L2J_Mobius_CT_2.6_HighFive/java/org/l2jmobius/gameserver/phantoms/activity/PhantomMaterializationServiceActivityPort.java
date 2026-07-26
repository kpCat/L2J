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
package org.l2jmobius.gameserver.phantoms.activity;

import java.util.Objects;

import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ResultStatus;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.State;

/**
 * Production adapter over the accepted Goal 006 lifecycle API.
 */
public final class PhantomMaterializationServiceActivityPort implements PhantomActivityMaterializationPort
{
	private final PhantomMaterializationService _service;

	public PhantomMaterializationServiceActivityPort(PhantomMaterializationService service)
	{
		_service = Objects.requireNonNull(service, "service");
	}

	@Override
	public TransitionOutcome materialize(long profileId)
	{
		final PhantomMaterializationService.MaterializeResult result = _service.materialize(profileId);
		if (result.status() == ResultStatus.SUCCESS)
		{
			return TransitionOutcome.success();
		}
		if ((result.status() == ResultStatus.ALREADY_ACTIVE) && isMaterialized(profileId))
		{
			return TransitionOutcome.success();
		}
		return result.status() == ResultStatus.MATERIALIZATION_FAILED_RETAINED ? TransitionOutcome.retainedFailure() : TransitionOutcome.transientBlock();
	}

	@Override
	public TransitionOutcome dematerialize(long profileId)
	{
		return mapCleanup(profileId, _service.dematerialize(profileId));
	}

	@Override
	public TransitionOutcome retryCleanup(long profileId)
	{
		return mapCleanup(profileId, _service.retryCleanup(profileId));
	}

	private TransitionOutcome mapCleanup(long profileId, PhantomMaterializationService.DematerializeResult result)
	{
		if ((result.status() == ResultStatus.SUCCESS) || ((result.status() == ResultStatus.NOT_ACTIVE) && !isMaterialized(profileId)))
		{
			return TransitionOutcome.success();
		}
		return result.status() == ResultStatus.CLEANUP_FAILED_RETAINED ? TransitionOutcome.retainedFailure() : TransitionOutcome.transientBlock();
	}

	@Override
	public boolean isMaterialized(long profileId)
	{
		return _service.find(profileId).map(snapshot -> snapshot.state() == State.ACTIVE).orElse(false);
	}
}
