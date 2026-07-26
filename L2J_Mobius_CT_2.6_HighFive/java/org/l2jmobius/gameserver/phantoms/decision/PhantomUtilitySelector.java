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
package org.l2jmobius.gameserver.phantoms.decision;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class PhantomUtilitySelector
{
	public static final int MAX_EXPLANATIONS = 8;

	public Selection select(List<PhantomDecisionCandidate> candidates, PhantomPlanningContext context)
	{
		final List<CandidateEvaluation> evaluations = new ArrayList<>(Math.min(candidates.size(), PhantomCandidateRegistry.MAX_CANDIDATES));
		PhantomDecisionCandidate winner = null;
		int winningScore = -1;
		int evaluated = 0;
		int blocked = 0;
		int failed = 0;
		for (PhantomDecisionCandidate candidate : candidates)
		{
			if (!candidate.supportedGoalTypes().contains(context.goal().goalType()) || !candidate.allowedActivityStates().contains(context.activityState()))
			{
				blocked++;
				evaluations.add(new CandidateEvaluation(candidate.key(), -1, EvaluationStatus.BLOCKED, "candidate.unsupported"));
				continue;
			}
			boolean requirementBlocked = false;
			for (PhantomCapabilityRequirement requirement : candidate.requirements())
			{
				if (!context.capabilities().satisfies(requirement))
				{
					requirementBlocked = true;
					break;
				}
			}
			if (requirementBlocked)
			{
				blocked++;
				evaluations.add(new CandidateEvaluation(candidate.key(), -1, EvaluationStatus.BLOCKED, "candidate.capability_missing"));
				continue;
			}

			long weightedScore = 0;
			long totalWeight = 0;
			String reasonKey = "candidate.scored";
			boolean candidateFailed = false;
			try
			{
				for (PhantomWeightedConsideration weighted : candidate.considerations())
				{
					final PhantomConsideration.Evaluation result = weighted.consideration().evaluate(context);
					if ((result == null) || (result.score() < 0) || (result.score() > 1000))
					{
						candidateFailed = true;
						break;
					}
					weightedScore += (long) result.score() * weighted.weight();
					totalWeight += weighted.weight();
					reasonKey = result.reasonKey();
				}
			}
			catch (Throwable throwable)
			{
				candidateFailed = true;
			}
			if (candidateFailed || (totalWeight == 0))
			{
				failed++;
				evaluations.add(new CandidateEvaluation(candidate.key(), -1, EvaluationStatus.FAILED, "candidate.evaluation_failed"));
				continue;
			}
			evaluated++;
			final int score = (int) (weightedScore / totalWeight);
			final EvaluationStatus status = score >= candidate.minimumAcceptedScore() ? EvaluationStatus.ELIGIBLE : EvaluationStatus.BELOW_THRESHOLD;
			evaluations.add(new CandidateEvaluation(candidate.key(), score, status, reasonKey));
			if ((status == EvaluationStatus.ELIGIBLE) && ((score > winningScore) || ((score == winningScore) && ((winner == null) || (candidate.key().compareTo(winner.key()) < 0)))))
			{
				winner = candidate;
				winningScore = score;
			}
		}
		evaluations.sort(Comparator.comparingInt(CandidateEvaluation::score).reversed().thenComparing(CandidateEvaluation::candidateKey));
		final List<CandidateEvaluation> bounded = List.copyOf(evaluations.subList(0, Math.min(MAX_EXPLANATIONS, evaluations.size())));
		return new Selection(winner, winningScore, bounded, evaluated, blocked, failed);
	}

	public enum EvaluationStatus
	{
		ELIGIBLE,
		BELOW_THRESHOLD,
		BLOCKED,
		FAILED
	}

	public record CandidateEvaluation(String candidateKey, int score, EvaluationStatus status, String reasonKey)
	{
		public CandidateEvaluation
		{
			candidateKey = PhantomDecisionKey.require(candidateKey, "Candidate key");
			reasonKey = PhantomDecisionKey.require(reasonKey, "Evaluation reason key");
		}
	}

	public record Selection(PhantomDecisionCandidate candidate, int score, List<CandidateEvaluation> explanations, int evaluated, int blocked, int failed)
	{
		public Selection
		{
			explanations = List.copyOf(explanations);
		}
	}
}
