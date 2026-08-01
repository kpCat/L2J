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
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.l2jmobius.gameserver.phantoms.acquisition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionCatalog.Method;

/** Durable bounded acquisition state; authoritative item counts remain external. */
public record PhantomAcquisitionState(Hashes hashes, long goalId, long goalRevision, int targetItemId, long requiredAmount, long baselineCount, long lastObservedCount, long progress, Status status, Source selectedSource, List<Candidate> candidates, int sourceCursor, int switchCount, Phase phase, int targetObjectId, int targetNpcId, int targetInstanceId, RecipePlan recipePlan, List<Receipt> receipts, long logicalMinute)
{
	public static final String COMPONENT_TYPE = "acquisition.state";
	public static final int SCHEMA_VERSION = 1;
	public static final int MAX_CANDIDATES = 8;
	public static final int MAX_RECEIPTS = 8;
	public static final int MAX_FAILURES_PER_SOURCE = 8;
	public static final int MAX_SWITCHES = 4;
	public static final int MAX_RECIPE_NODES = 48;
	public static final int MAX_DEFICITS = 32;

	public PhantomAcquisitionState
	{
		Objects.requireNonNull(hashes, "hashes");
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(phase, "phase");
		candidates = List.copyOf(candidates);
		receipts = List.copyOf(receipts);
		if ((goalId <= 0) || (goalRevision < 0) || (targetItemId <= 0) || (requiredAmount <= 0) || (baselineCount < 0) || (lastObservedCount < 0) || (progress != observedProgress(baselineCount, lastObservedCount, requiredAmount)) || (candidates.size() > MAX_CANDIDATES) || (sourceCursor < 0) || (sourceCursor > candidates.size()) || (switchCount < 0) || (switchCount > MAX_SWITCHES) || (receipts.size() > MAX_RECEIPTS) || (logicalMinute < 0))
		{
			throw new IllegalArgumentException("Invalid acquisition state bounds.");
		}
		final Set<String> sourceIds = new HashSet<>();
		Candidate previous = null;
		for (Candidate candidate : candidates)
		{
			if (!sourceIds.add(candidate.sourceId()) || ((previous != null) && (Candidate.ORDER.compare(previous, candidate) > 0)))
			{
				throw new IllegalArgumentException("Acquisition candidates must be unique and canonically ranked.");
			}
			previous = candidate;
		}
		if ((selectedSource != null) && (!sourceIds.contains(selectedSource.sourceId()) || (selectedSource.itemId() != targetItemId) || (sourceCursor >= candidates.size()) || !candidates.get(sourceCursor).sourceId().equals(selectedSource.sourceId()) || (candidates.get(sourceCursor).method() != selectedSource.method())))
		{
			throw new IllegalArgumentException("Selected acquisition source is not a ranked target source.");
		}
		if ((targetObjectId == 0) != ((targetNpcId == 0) && (targetInstanceId == 0)))
		{
			throw new IllegalArgumentException("Acquisition target identity is partial.");
		}
		if ((targetObjectId < 0) || (targetNpcId < 0) || (targetInstanceId < 0) || ((targetObjectId > 0) && ((selectedSource == null) || (targetNpcId != selectedSource.npcId()) || (targetInstanceId != selectedSource.instanceId()))))
		{
			throw new IllegalArgumentException("Acquisition target identity is invalid.");
		}
		if (((phase == Phase.NONE) || (phase == Phase.TRAVEL_REQUIRED) || (phase == Phase.TARGET_REQUIRED)) && (targetObjectId != 0))
		{
			throw new IllegalArgumentException("Acquisition phase cannot retain a target claim.");
		}
		final boolean phaseRequiresTarget = (phase == Phase.SPOIL_PREPARED) || (phase == Phase.SPOIL_DISPATCHING) || (phase == Phase.SPOIL_OBSERVED) || (phase == Phase.COMBAT_SUBMITTED) || (phase == Phase.COMBAT_TERMINAL) || (phase == Phase.SWEEP_PREPARED) || (phase == Phase.SWEEP_DISPATCHING) || (phase == Phase.VERIFYING);
		if (phaseRequiresTarget != (targetObjectId > 0))
		{
			throw new IllegalArgumentException("Acquisition phase and exact target claim disagree.");
		}
		if (((phase == Phase.SPOIL_PREPARED) || (phase == Phase.SPOIL_DISPATCHING) || (phase == Phase.SPOIL_OBSERVED) || (phase == Phase.SWEEP_PREPARED) || (phase == Phase.SWEEP_DISPATCHING)) && ((selectedSource == null) || (selectedSource.method() != Method.SPOIL_SWEEP)))
		{
			throw new IllegalArgumentException("Spoil acquisition phase requires a spoil/sweep source.");
		}
		if (((status == Status.BLOCKED) || (status == Status.COMPLETED) || (status == Status.FAILED) || (status == Status.STALE_AUTHORITY) || (status == Status.DEFERRED_CHECKPOINT_2) || (status == Status.INCONSISTENT)) && (phase != Phase.NONE))
		{
			throw new IllegalArgumentException("Terminal or non-executable acquisition state retains an active phase.");
		}
		if ((status == Status.COMPLETED) != (progress == requiredAmount))
		{
			throw new IllegalArgumentException("Acquisition completion and progress disagree.");
		}
		if ((selectedSource != null) && (selectedSource.method() == Method.RECIPE_PREPARATION) != (recipePlan != null))
		{
			throw new IllegalArgumentException("Acquisition recipe source and plan disagree.");
		}
		if ((recipePlan != null) && (!receipts.isEmpty() || (progress != 0)))
		{
			throw new IllegalArgumentException("Planning-only recipe state cannot contain execution receipts or progress.");
		}
	}

	public PhantomAcquisitionState withPlan(Status nextStatus, Source source, List<Candidate> ranked, RecipePlan plan, Phase nextPhase, long minute)
	{
		return new PhantomAcquisitionState(hashes, goalId, goalRevision, targetItemId, requiredAmount, baselineCount, lastObservedCount, progress, nextStatus, source, ranked, source == null ? 0 : indexOf(ranked, source.sourceId()), switchCount, nextPhase, 0, 0, 0, plan, receipts, minute);
	}

	public PhantomAcquisitionState withPhase(Phase nextPhase, int objectId, int npcId, int instanceId, long minute)
	{
		return new PhantomAcquisitionState(hashes, goalId, goalRevision, targetItemId, requiredAmount, baselineCount, lastObservedCount, progress, status == Status.READY ? Status.ACTIVE : status, selectedSource, candidates, sourceCursor, switchCount, nextPhase, objectId, npcId, instanceId, recipePlan, receipts, minute);
	}

	public PhantomAcquisitionState observe(long authoritativeCount, Status nextStatus, Phase nextPhase, Receipt receipt, long minute)
	{
		final long nextProgress = observedProgress(baselineCount, authoritativeCount, requiredAmount);
		final Status effectiveStatus = nextProgress == requiredAmount ? Status.COMPLETED : nextStatus;
		final List<Receipt> nextReceipts = appendReceipt(receipts, receipt);
		return new PhantomAcquisitionState(hashes, goalId, goalRevision, targetItemId, requiredAmount, baselineCount, authoritativeCount, nextProgress, effectiveStatus, selectedSource, candidates, sourceCursor, switchCount, nextProgress == requiredAmount ? Phase.NONE : nextPhase, 0, 0, 0, recipePlan, nextReceipts, minute);
	}

	public PhantomAcquisitionState failSource(String reasonKey, long minute)
	{
		if (selectedSource == null)
		{
			throw new IllegalStateException("Acquisition source is not selected.");
		}
		final List<Candidate> next = new ArrayList<>(candidates.size());
		for (Candidate candidate : candidates)
		{
			next.add(candidate.sourceId().equals(selectedSource.sourceId()) ? candidate.failed(reasonKey, minute) : candidate);
		}
		return new PhantomAcquisitionState(hashes, goalId, goalRevision, targetItemId, requiredAmount, baselineCount, lastObservedCount, progress, Status.BLOCKED, selectedSource, next, sourceCursor, switchCount, Phase.NONE, 0, 0, 0, recipePlan, receipts, minute);
	}

	public PhantomAcquisitionState switchSource(int nextCursor, Source nextSource, RecipePlan nextRecipePlan, long minute)
	{
		if ((nextCursor < 0) || (nextCursor >= candidates.size()) || (nextCursor == sourceCursor) || (switchCount >= MAX_SWITCHES) || !candidates.get(nextCursor).sourceId().equals(nextSource.sourceId()))
		{
			throw new IllegalArgumentException("Invalid acquisition source switch.");
		}
		final Candidate next = candidates.get(nextCursor);
		return new PhantomAcquisitionState(hashes, goalId, goalRevision, targetItemId, requiredAmount, baselineCount, lastObservedCount, progress, Status.READY, nextSource, candidates, nextCursor, switchCount + 1, Phase.TRAVEL_REQUIRED, 0, 0, 0, next.method() == Method.RECIPE_PREPARATION ? nextRecipePlan : null, receipts, minute);
	}

	public static long observedProgress(long baseline, long current, long required)
	{
		if ((baseline < 0) || (current < 0) || (required <= 0))
		{
			throw new IllegalArgumentException("Invalid acquisition progress inputs.");
		}
		return Math.min(required, current <= baseline ? 0 : current - baseline);
	}

	private static int indexOf(List<Candidate> candidates, String sourceId)
	{
		for (int index = 0; index < candidates.size(); index++)
		{
			if (candidates.get(index).sourceId().equals(sourceId))
			{
				return index;
			}
		}
		throw new IllegalArgumentException("Selected acquisition source is absent.");
	}

	private static List<Receipt> appendReceipt(List<Receipt> receipts, Receipt receipt)
	{
		if (receipt == null)
		{
			return receipts;
		}
		final ArrayList<Receipt> result = new ArrayList<>(receipts);
		result.removeIf(existing -> existing.operationId().equals(receipt.operationId()));
		result.add(receipt);
		if (result.size() > MAX_RECEIPTS)
		{
			result.removeFirst();
		}
		return List.copyOf(result);
	}

	private static void requireHash(String value, String name)
	{
		if ((value == null) || !value.matches("[0-9a-f]{64}"))
		{
			throw new IllegalArgumentException("Invalid acquisition " + name + " hash.");
		}
	}

	private static void requireText(String value, int maximum, String name)
	{
		if ((value == null) || value.isBlank() || (value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > maximum))
		{
			throw new IllegalArgumentException("Invalid acquisition " + name + ".");
		}
	}

	public enum Status
	{
		PLANNING,
		READY,
		ACTIVE,
		BLOCKED,
		COMPLETED,
		FAILED,
		STALE_AUTHORITY,
		DEFERRED_CHECKPOINT_2,
		INCONSISTENT
	}

	public enum Phase
	{
		NONE,
		TRAVEL_REQUIRED,
		TARGET_REQUIRED,
		SPOIL_PREPARED,
		SPOIL_DISPATCHING,
		SPOIL_OBSERVED,
		COMBAT_SUBMITTED,
		COMBAT_TERMINAL,
		SWEEP_PREPARED,
		SWEEP_DISPATCHING,
		VERIFYING
	}

	public enum ReceiptKind
	{
		ACTIVE_DEATH_DROP,
		ACTIVE_SPOIL,
		ACTIVE_SWEEP,
		BACKGROUND_DEATH_DROP,
		BACKGROUND_SPOIL_SWEEP,
		VERIFY
	}

	public enum TerminalResult
	{
		COMMITTED,
		OBSERVED,
		NO_PROGRESS,
		UNCERTAIN,
		FAILED
	}

	public record Hashes(String catalog, String knowledge, String topology, String progression, String background)
	{
		public Hashes
		{
			requireHash(catalog, "catalog");
			requireHash(knowledge, "knowledge");
			requireHash(topology, "topology");
			requireHash(progression, "progression");
			requireHash(background, "background");
		}
	}

	public record Source(String sourceId, Method method, int npcId, int itemId, String factKey, String topologyNodeId, String anchorId, int instanceId, int spoilSkillId, int spoilSkillLevel, int sweepSkillId, int sweepSkillLevel)
	{
		public Source
		{
			requireHash(sourceId, "source ID");
			Objects.requireNonNull(method, "method");
			if ((npcId < 0) || (itemId <= 0) || (instanceId < 0) || (spoilSkillId < 0) || (spoilSkillLevel < 0) || (sweepSkillId < 0) || (sweepSkillLevel < 0))
			{
				throw new IllegalArgumentException("Invalid acquisition source identity.");
			}
			requireText(factKey, 160, "fact key");
			requireText(topologyNodeId, 96, "topology node");
			requireText(anchorId, 96, "anchor");
			if ((method == Method.SPOIL_SWEEP) != ((spoilSkillId > 0) && (spoilSkillLevel > 0) && (sweepSkillId > 0) && (sweepSkillLevel > 0)))
			{
				throw new IllegalArgumentException("Acquisition spoil capability identity is incomplete.");
			}
			if (((method == Method.DEATH_DROP) || (method == Method.SPOIL_SWEEP)) && ((npcId <= 0) || (instanceId != 0)))
			{
				throw new IllegalArgumentException("Executable acquisition source is not an instance-zero monster source.");
			}
			if ((method == Method.RECIPE_PREPARATION) && ((npcId != 0) || (instanceId != 0) || !factKey.startsWith("recipe:")))
			{
				throw new IllegalArgumentException("Recipe acquisition source identity is invalid.");
			}
		}
	}

	public record Candidate(String sourceId, Method method, int score, int failures, long lastFailureMinute, String lastFailureReason)
	{
		private static final Comparator<Candidate> ORDER = Comparator.comparingInt(Candidate::score).reversed().thenComparing(Candidate::sourceId);

		public Candidate
		{
			requireHash(sourceId, "candidate source ID");
			Objects.requireNonNull(method, "method");
			lastFailureReason = Objects.requireNonNullElse(lastFailureReason, "");
			if ((score < Integer.MIN_VALUE + 1) || (failures < 0) || (failures > MAX_FAILURES_PER_SOURCE) || (lastFailureMinute < 0) || ((failures == 0) != lastFailureReason.isEmpty()) || (!lastFailureReason.isEmpty() && (lastFailureReason.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 64)))
			{
				throw new IllegalArgumentException("Invalid acquisition candidate state.");
			}
		}

		private Candidate failed(String reasonKey, long minute)
		{
			return new Candidate(sourceId, method, score, Math.min(MAX_FAILURES_PER_SOURCE, failures + 1), minute, reasonKey);
		}
	}

	public record RecipeNode(int itemId, long requestedCount, long inventoryUsed, long deficit, int recipeListId, int depth, boolean leaf)
	{
		public RecipeNode
		{
			if ((itemId <= 0) || (requestedCount <= 0) || (inventoryUsed < 0) || (inventoryUsed > requestedCount) || (deficit != (requestedCount - inventoryUsed)) || (recipeListId < 0) || (depth < 0) || (depth > 6) || (leaf == (recipeListId > 0)))
			{
				throw new IllegalArgumentException("Invalid acquisition recipe node.");
			}
		}
	}

	public record Deficit(int itemId, long count, boolean manorDeferred, boolean questDeferred)
	{
		public Deficit
		{
			if ((itemId <= 0) || (count <= 0))
			{
				throw new IllegalArgumentException("Invalid acquisition recipe deficit.");
			}
		}
	}

	public record RecipePlan(int recipeListId, int productItemId, long requestedOutput, long batchCount, long productOutput, int successRate, boolean dwarven, int craftSkillId, int craftSkillLevel, List<RecipeNode> nodes, List<Deficit> deficits, String reasonKey)
	{
		public RecipePlan
		{
			nodes = List.copyOf(nodes);
			deficits = List.copyOf(deficits);
			reasonKey = Objects.requireNonNullElse(reasonKey, "");
			if ((recipeListId <= 0) || (productItemId <= 0) || (requestedOutput <= 0) || (batchCount <= 0) || (productOutput <= 0) || (successRate < 0) || (successRate > 100) || (craftSkillId < 0) || (craftSkillLevel < 0) || (nodes.isEmpty()) || (nodes.size() > MAX_RECIPE_NODES) || (deficits.size() > MAX_DEFICITS) || (nodes.stream().map(RecipeNode::itemId).distinct().count() != nodes.size()) || (deficits.stream().map(Deficit::itemId).distinct().count() != deficits.size()))
			{
				throw new IllegalArgumentException("Invalid acquisition recipe plan.");
			}
		}
	}

	public record Receipt(String operationId, String sourceId, ReceiptKind kind, long beforeCount, long afterCount, TerminalResult result, long logicalMinute)
	{
		public Receipt
		{
			requireHash(operationId, "operation ID");
			requireHash(sourceId, "receipt source ID");
			Objects.requireNonNull(kind, "kind");
			Objects.requireNonNull(result, "result");
			if ((beforeCount < 0) || (afterCount < 0) || (logicalMinute < 0))
			{
				throw new IllegalArgumentException("Invalid acquisition receipt.");
			}
		}
	}
}
