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
package org.l2jmobius.gameserver.phantoms;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.l2jmobius.gameserver.phantoms.PhantomDecisionHealthModel.ProgressFingerprint;
import org.l2jmobius.gameserver.phantoms.PhantomSelectedDecisionTrace.DecisionView;
import org.l2jmobius.gameserver.phantoms.PhantomSelectedDecisionTrace.Health;
import org.l2jmobius.gameserver.phantoms.decision.PhantomUtilitySelector.CandidateEvaluation;
import org.l2jmobius.gameserver.phantoms.decision.PhantomUtilitySelector.EvaluationStatus;

/**
 * Pure deterministic replay of frozen selected-decision diagnostic evidence.
 */
public final class PhantomDecisionReplay
{
	public static final int SCHEMA_VERSION = 1;
	public static final int MAX_FRAMES = 64;
	public static final int MAX_EXPLANATIONS = 8;
	private static final int MAX_FAILURE_REASON_LENGTH = 160;

	private PhantomDecisionReplay()
	{
	}

	public static ReplayResult replay(Bundle bundle)
	{
		final String validationFailure = validate(bundle);
		if (validationFailure != null)
		{
			return ReplayResult.failure(bundle == null ? 0 : bundle.profileId(), bundle == null ? 0 : bundle.frames().size(), null, -1, validationFailure, 0, 0, 0, null, -1, -1, -1);
		}
		final String digest = digestValidated(bundle);
		long expectedAgeNanos = 0;
		long previousRelativeNanos = 0;
		ProgressFingerprint previousFingerprint = null;
		int verified = 0;
		int unverifiable = 0;
		int mismatch = 0;
		int firstSlow = -1;
		int firstStuck = -1;
		int firstAttention = -1;
		Health finalHealth = null;
		for (int index = 0; index < bundle.frames().size(); index++)
		{
			final Frame frame = bundle.frames().get(index);
			final ProgressFingerprint fingerprint = PhantomDecisionHealthModel.fingerprint(frame.decision());
			if (index == 0)
			{
				expectedAgeNanos = frame.capturedUnchangedAgeNanos();
			}
			else if (fingerprint.equals(previousFingerprint))
			{
				expectedAgeNanos = saturatingAdd(expectedAgeNanos, frame.relativeLogicalNanos() - previousRelativeNanos);
			}
			else
			{
				expectedAgeNanos = 0;
			}
			if (frame.capturedUnchangedAgeNanos() != expectedAgeNanos)
			{
				return ReplayResult.failure(bundle.profileId(), bundle.frames().size(), digest, index, "captured unchanged age differs from structural replay", verified, unverifiable, mismatch, finalHealth, firstSlow, firstStuck, firstAttention);
			}
			final Health expectedHealth = PhantomDecisionHealthModel.classify(frame.decision(), expectedAgeNanos, true, bundle.slowThresholdMillis(), bundle.stuckThresholdMillis());
			if (frame.capturedHealth() != expectedHealth)
			{
				return ReplayResult.failure(bundle.profileId(), bundle.frames().size(), digest, index, "captured health differs from shared health model", verified, unverifiable, mismatch, expectedHealth, firstSlow, firstStuck, firstAttention);
			}
			finalHealth = expectedHealth;
			if ((expectedHealth == Health.SLOW) && (firstSlow < 0))
			{
				firstSlow = index;
			}
			if ((expectedHealth == Health.STUCK) && (firstStuck < 0))
			{
				firstStuck = index;
			}
			if ((expectedHealth == Health.ATTENTION) && (firstAttention < 0))
			{
				firstAttention = index;
			}			final CandidateCheck candidate = checkCandidates(frame.decision());
			switch (candidate.verdict())
			{
				case VERIFIED -> verified++;
				case UNVERIFIABLE -> unverifiable++;
				case MISMATCH ->
				{
					mismatch++;
					return ReplayResult.failure(bundle.profileId(), bundle.frames().size(), digest, index, candidate.reason(), verified, unverifiable, mismatch, finalHealth, firstSlow, firstStuck, firstAttention);
				}
			}
			previousFingerprint = fingerprint;
			previousRelativeNanos = frame.relativeLogicalNanos();
		}
		return new ReplayResult(ReplayStatus.PASS, bundle.profileId(), bundle.frames().size(), digest, finalHealth, firstSlow, firstStuck, firstAttention, verified, unverifiable, mismatch, -1, null);
	}

	public static String digest(Bundle bundle)
	{
		final String validationFailure = validate(bundle);
		if (validationFailure != null)
		{
			throw new IllegalArgumentException(validationFailure);
		}
		return digestValidated(bundle);
	}

	private static CandidateCheck checkCandidates(DecisionView decision)
	{
		final List<CandidateEvaluation> candidates = decision.topCandidates();
		final Set<String> keys = new HashSet<>();
		for (int index = 0; index < candidates.size(); index++)
		{
			final CandidateEvaluation current = candidates.get(index);
			if (!keys.add(current.candidateKey()))
			{
				return CandidateCheck.mismatch("duplicate candidate explanation key");
			}
			if (index > 0)
			{
				final CandidateEvaluation previous = candidates.get(index - 1);
				if ((previous.score() < current.score()) || ((previous.score() == current.score()) && (previous.candidateKey().compareTo(current.candidateKey()) > 0)))
				{
					return CandidateCheck.mismatch("candidate explanations are not score-desc/key-asc");
				}
			}
		}
		if (decision.candidateKey() == null)
		{
			return candidates.stream().anyMatch(candidate -> candidate.status() == EvaluationStatus.ELIGIBLE) ? CandidateCheck.mismatch("null selection contradicts visible eligible candidate") : CandidateCheck.unverifiable();
		}
		CandidateEvaluation selected = null;
		for (CandidateEvaluation candidate : candidates)
		{
			if (candidate.candidateKey().equals(decision.candidateKey()))
			{
				selected = candidate;
				break;
			}
		}
		if (selected == null)
		{
			return CandidateCheck.unverifiable();
		}
		if ((selected.status() != EvaluationStatus.ELIGIBLE) || (selected.score() != decision.score()))
		{
			return CandidateCheck.mismatch("selected candidate status or score contradicts visible explanation");
		}
		for (CandidateEvaluation candidate : candidates)
		{
			if ((candidate.status() == EvaluationStatus.ELIGIBLE) && ((candidate.score() > decision.score()) || ((candidate.score() == decision.score()) && (candidate.candidateKey().compareTo(decision.candidateKey()) < 0))))
			{
				return CandidateCheck.mismatch("visible eligible candidate outranks selected candidate");
			}
		}
		return CandidateCheck.verified();
	}

	private static String validate(Bundle bundle)
	{
		if (bundle == null)
		{
			return "bundle is null";
		}
		if (bundle.schemaVersion() != SCHEMA_VERSION)
		{
			return "unsupported replay schema";
		}
		if (bundle.profileId() <= 0)
		{
			return "profile id must be positive";
		}
		try
		{
			PhantomDecisionHealthModel.validateThresholds(bundle.slowThresholdMillis(), bundle.stuckThresholdMillis());
		}
		catch (IllegalArgumentException exception)
		{
			return "invalid health thresholds";
		}
		if ((bundle.frames().isEmpty()) || (bundle.frames().size() > MAX_FRAMES))
		{
			return "frame count must be between 1 and 64";
		}
		long previousRelativeNanos = 0;
		for (int index = 0; index < bundle.frames().size(); index++)
		{
			final Frame frame = bundle.frames().get(index);
			if (frame.decision().profileId() != bundle.profileId())
			{
				return "frame profile differs from bundle profile";
			}
			if ((frame.relativeLogicalNanos() < 0) || ((index > 0) && (frame.relativeLogicalNanos() < previousRelativeNanos)))
			{
				return "relative logical time must be nonnegative and nondecreasing";
			}
			if ((index == 0) && (frame.relativeLogicalNanos() != 0))
			{
				return "first retained frame time must be normalized to zero";
			}
			if (frame.capturedUnchangedAgeNanos() < 0)
			{
				return "captured unchanged age must be nonnegative";
			}
			if (frame.decision().topCandidates().size() > MAX_EXPLANATIONS)
			{
				return "candidate explanation count exceeds eight";
			}
			previousRelativeNanos = frame.relativeLogicalNanos();
		}
		return null;
	}
	private static String digestValidated(Bundle bundle)
	{
		try
		{
			final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (DataOutputStream output = new DataOutputStream(bytes))
			{
				output.writeInt(bundle.schemaVersion());
				output.writeLong(bundle.profileId());
				output.writeLong(bundle.slowThresholdMillis());
				output.writeLong(bundle.stuckThresholdMillis());
				output.writeInt(bundle.frames().size());
				for (Frame frame : bundle.frames())
				{
					output.writeLong(frame.relativeLogicalNanos());
					output.writeLong(frame.capturedUnchangedAgeNanos());
					writeEnum(output, frame.capturedHealth());
					writeDecision(output, frame.decision());
				}
			}
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
		}
		catch (IOException | NoSuchAlgorithmException exception)
		{
			throw new IllegalStateException("Canonical replay digest is unavailable.", exception);
		}
	}

	private static void writeDecision(DataOutputStream output, DecisionView view) throws IOException
	{
		writeNullableEnum(output, view.activityState());
		output.writeLong(view.profileId());
		output.writeLong(view.goalId());
		writeNullableString(output, view.goalType());
		output.writeLong(view.goalRevision());
		writeNullableEnum(output, view.goalStatus());
		writeNullableEnum(output, view.runtimeState());
		output.writeLong(view.decisionSequence());
		writeNullableString(output, view.candidateKey());
		output.writeInt(view.score());
		output.writeLong(view.planId());
		output.writeInt(view.step());
		output.writeInt(view.attempt());
		writeNullableEnum(output, view.lastResult());
		writeNullableString(output, view.reasonKey());
		output.writeInt(view.topCandidates().size());
		for (CandidateEvaluation candidate : view.topCandidates())
		{
			writeString(output, candidate.candidateKey());
			output.writeInt(candidate.score());
			writeEnum(output, candidate.status());
			writeString(output, candidate.reasonKey());
		}
	}

	private static void writeNullableString(DataOutputStream output, String value) throws IOException
	{
		output.writeBoolean(value != null);
		if (value != null)
		{
			writeString(output, value);
		}
	}

	private static void writeString(DataOutputStream output, String value) throws IOException
	{
		final byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
		output.writeInt(encoded.length);
		output.write(encoded);
	}

	private static void writeNullableEnum(DataOutputStream output, Enum<?> value) throws IOException
	{
		output.writeBoolean(value != null);
		if (value != null)
		{
			writeString(output, value.name());
		}
	}

	private static void writeEnum(DataOutputStream output, Enum<?> value) throws IOException
	{
		writeString(output, Objects.requireNonNull(value).name());
	}

	private static long saturatingAdd(long left, long right)
	{
		return left > (Long.MAX_VALUE - right) ? Long.MAX_VALUE : left + right;
	}

	private static String bounded(String reason)
	{
		return reason.length() <= MAX_FAILURE_REASON_LENGTH ? reason : reason.substring(0, MAX_FAILURE_REASON_LENGTH);
	}
	public enum ReplayStatus
	{
		PASS,
		FAIL
	}

	public enum CandidateVerdict
	{
		VERIFIED,
		MISMATCH,
		UNVERIFIABLE
	}

	public record Bundle(int schemaVersion, long profileId, long slowThresholdMillis, long stuckThresholdMillis, List<Frame> frames)
	{
		public Bundle
		{
			frames = List.copyOf(frames);
		}
	}

	public record Frame(long relativeLogicalNanos, long capturedUnchangedAgeNanos, Health capturedHealth, DecisionView decision)
	{
		public Frame
		{
			Objects.requireNonNull(capturedHealth);
			Objects.requireNonNull(decision);
		}
	}

	public record ReplayResult(ReplayStatus status, long profileId, int frameCount, String digest, Health finalHealth, int firstSlowFrame, int firstStuckFrame, int firstAttentionFrame, int candidateVerified, int candidateUnverifiable, int candidateMismatch, int firstFailureFrame, String failureReason)
	{
		public ReplayResult
		{
			Objects.requireNonNull(status);
			if (failureReason != null)
			{
				failureReason = bounded(failureReason);
			}
		}

		private static ReplayResult failure(long profileId, int frameCount, String digest, int firstFailureFrame, String reason, int verified, int unverifiable, int mismatch, Health finalHealth, int firstSlow, int firstStuck, int firstAttention)
		{
			return new ReplayResult(ReplayStatus.FAIL, profileId, frameCount, digest, finalHealth, firstSlow, firstStuck, firstAttention, verified, unverifiable, mismatch, firstFailureFrame, reason);
		}
	}

	private record CandidateCheck(CandidateVerdict verdict, String reason)
	{
		private static CandidateCheck verified()
		{
			return new CandidateCheck(CandidateVerdict.VERIFIED, null);
		}

		private static CandidateCheck unverifiable()
		{
			return new CandidateCheck(CandidateVerdict.UNVERIFIABLE, null);
		}

		private static CandidateCheck mismatch(String reason)
		{
			return new CandidateCheck(CandidateVerdict.MISMATCH, reason);
		}
	}
}