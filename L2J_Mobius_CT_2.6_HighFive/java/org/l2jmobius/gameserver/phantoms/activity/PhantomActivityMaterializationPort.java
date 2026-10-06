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

/**
 * Narrow scheduler-to-lifecycle bridge. It exposes neither Player nor an
 * arbitrary callback.
 */
public interface PhantomActivityMaterializationPort
{
	TransitionOutcome materialize(long profileId);

	default TransitionOutcome materialize(long profileId, MaterializationRequest request)
	{
		return request.kind() == MaterializationRequest.Kind.NORMAL ? materialize(profileId) : TransitionOutcome.deferred("materialization.intent_unsupported");
	}

	TransitionOutcome dematerialize(long profileId);

	TransitionOutcome retryCleanup(long profileId);

	boolean isMaterialized(long profileId);

	boolean hasLifecycleOwnership(long profileId);

	default long softReclaimCandidate(long requestingProfileId)
	{
		return 0;
	}

	default TransitionOutcome reclaimSoft(long profileId)
	{
		return TransitionOutcome.deferred();
	}

	enum Outcome
	{
		SUCCESS,
		DEFERRED,
		TRANSIENT_BLOCK,
		RETAINED_FAILURE
	}

	record MaterializationRequest(Kind kind, String ownerClaim)
	{
		public enum Kind { NORMAL, NATIVE_CONTEXT_HANDOFF }

		public MaterializationRequest
		{
			java.util.Objects.requireNonNull(kind, "kind");
			ownerClaim = java.util.Objects.requireNonNullElse(ownerClaim, "");
			if (((kind == Kind.NORMAL) && !ownerClaim.isEmpty()) || ((kind == Kind.NATIVE_CONTEXT_HANDOFF) && ownerClaim.isBlank()))
			{
				throw new IllegalArgumentException("Invalid materialization intent claim.");
			}
		}

		public static MaterializationRequest normal() { return new MaterializationRequest(Kind.NORMAL, ""); }
		public static MaterializationRequest nativeContextHandoff(String ownerClaim) { return new MaterializationRequest(Kind.NATIVE_CONTEXT_HANDOFF, ownerClaim); }
	}

	record TransitionOutcome(Outcome outcome, String reason, MaterializationRequest request)
	{
		public TransitionOutcome(Outcome outcome, String reason)
		{
			this(outcome, reason, MaterializationRequest.normal());
		}

		public TransitionOutcome(Outcome outcome)
		{
			this(outcome, "");
		}

		public static TransitionOutcome deferred(String reason)
		{
			return new TransitionOutcome(Outcome.DEFERRED, reason);
		}
		public static TransitionOutcome success()
		{
			return new TransitionOutcome(Outcome.SUCCESS);
		}

		public static TransitionOutcome success(MaterializationRequest request)
		{
			return new TransitionOutcome(Outcome.SUCCESS, "", request);
		}

		public static TransitionOutcome transientBlock()
		{
			return new TransitionOutcome(Outcome.TRANSIENT_BLOCK);
		}

		public static TransitionOutcome deferred()
		{
			return new TransitionOutcome(Outcome.DEFERRED);
		}

		public static TransitionOutcome retainedFailure()
		{
			return new TransitionOutcome(Outcome.RETAINED_FAILURE);
		}
	}

	static PhantomActivityMaterializationPort noop()
	{
		return new PhantomActivityMaterializationPort()
		{
			@Override
			public TransitionOutcome materialize(long profileId)
			{
				return TransitionOutcome.transientBlock();
			}

			@Override
			public TransitionOutcome dematerialize(long profileId)
			{
				return TransitionOutcome.success();
			}

			@Override
			public TransitionOutcome retryCleanup(long profileId)
			{
				return TransitionOutcome.success();
			}

			@Override
			public boolean isMaterialized(long profileId)
			{
				return false;
			}

			@Override
			public boolean hasLifecycleOwnership(long profileId)
			{
				return false;
			}
		};
	}
}
