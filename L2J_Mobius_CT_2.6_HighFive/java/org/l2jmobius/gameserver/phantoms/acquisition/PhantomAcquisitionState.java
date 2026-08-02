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
public record PhantomAcquisitionState(Hashes hashes, long goalId, long goalRevision, int targetItemId, long requiredAmount, long baselineCount, long lastObservedCount, long progress, Status status, Source selectedSource, List<Candidate> candidates, int sourceCursor, int switchCount, Phase phase, int targetObjectId, int targetNpcId, int targetInstanceId, RecipePlan recipePlan, MethodBinding methodBinding, List<Receipt> receipts, int phaseAttempt, long logicalMinute)
{
	public static final String COMPONENT_TYPE = "acquisition.state";
	public static final int SCHEMA_VERSION = 3;
	public static final int LEGACY_SCHEMA_VERSION = 1;
	public static final int DISPATCH_SCHEMA_VERSION = 2;
	public static final int MAX_CANDIDATES = 8;
	public static final int MAX_RECEIPTS = 8;
	public static final int MAX_FAILURES_PER_SOURCE = 8;
	public static final int MAX_SWITCHES = 4;
	public static final int MAX_RECIPE_NODES = 48;
	public static final int MAX_DEFICITS = 32;
	public static final int MAX_PHASE_ATTEMPTS = 3;

	public PhantomAcquisitionState(Hashes hashes, long goalId, long goalRevision, int targetItemId, long requiredAmount, long baselineCount, long lastObservedCount, long progress, Status status, Source selectedSource, List<Candidate> candidates, int sourceCursor, int switchCount, Phase phase, int targetObjectId, int targetNpcId, int targetInstanceId, RecipePlan recipePlan, List<Receipt> receipts, long logicalMinute)
	{
		this(hashes, goalId, goalRevision, targetItemId, requiredAmount, baselineCount, lastObservedCount, progress, status, selectedSource, candidates, sourceCursor, switchCount, phase, targetObjectId, targetNpcId, targetInstanceId, recipePlan, null, receipts, 0, logicalMinute);
	}

	public PhantomAcquisitionState(Hashes hashes, long goalId, long goalRevision, int targetItemId, long requiredAmount, long baselineCount, long lastObservedCount, long progress, Status status, Source selectedSource, List<Candidate> candidates, int sourceCursor, int switchCount, Phase phase, int targetObjectId, int targetNpcId, int targetInstanceId, RecipePlan recipePlan, List<Receipt> receipts, int phaseAttempt, long logicalMinute)
	{
		this(hashes, goalId, goalRevision, targetItemId, requiredAmount, baselineCount, lastObservedCount, progress, status, selectedSource, candidates, sourceCursor, switchCount, phase, targetObjectId, targetNpcId, targetInstanceId, recipePlan, null, receipts, phaseAttempt, logicalMinute);
	}

	public PhantomAcquisitionState
	{
		Objects.requireNonNull(hashes, "hashes");
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(phase, "phase");
		candidates = List.copyOf(candidates);
		receipts = List.copyOf(receipts);
		if ((goalId <= 0) || (goalRevision < 0) || (targetItemId <= 0) || (requiredAmount <= 0) || (baselineCount < 0) || (lastObservedCount < 0) || (progress != observedProgress(baselineCount, lastObservedCount, requiredAmount)) || (candidates.size() > MAX_CANDIDATES) || (sourceCursor < 0) || (sourceCursor > candidates.size()) || (switchCount < 0) || (switchCount > MAX_SWITCHES) || (receipts.size() > MAX_RECEIPTS) || (phaseAttempt < 0) || (phaseAttempt > MAX_PHASE_ATTEMPTS) || (logicalMinute < 0))
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
		final boolean phaseRequiresTarget = (phase == Phase.SPOIL_PREPARED) || (phase == Phase.SPOIL_DISPATCHING) || (phase == Phase.SPOIL_OBSERVED) || (phase == Phase.COMBAT_PREPARED) || (phase == Phase.COMBAT_SUBMITTED) || (phase == Phase.COMBAT_TERMINAL) || (phase == Phase.SWEEP_PREPARED) || (phase == Phase.SWEEP_DISPATCHING) || (phase == Phase.VERIFYING) || (phase == Phase.SOW_PREPARED) || (phase == Phase.SOW_DISPATCHING) || (phase == Phase.SOW_OBSERVED) || (phase == Phase.HARVEST_PREPARED) || (phase == Phase.HARVEST_DISPATCHING) || (phase == Phase.QUEST_COMBAT_PREPARED) || (phase == Phase.QUEST_COMBAT_SUBMITTED) || (phase == Phase.QUEST_COMBAT_TERMINAL) || (phase == Phase.QUEST_CALLBACK_WAIT);
		if (phaseRequiresTarget != (targetObjectId > 0))
		{
			throw new IllegalArgumentException("Acquisition phase and exact target claim disagree.");
		}
		if ((phaseAttempt > 0) && (phase != Phase.SPOIL_PREPARED) && (phase != Phase.SPOIL_DISPATCHING) && (phase != Phase.SWEEP_PREPARED) && (phase != Phase.SWEEP_DISPATCHING) && (phase != Phase.COMBAT_SUBMITTED) && (phase != Phase.SOW_PREPARED) && (phase != Phase.SOW_DISPATCHING) && (phase != Phase.HARVEST_PREPARED) && (phase != Phase.HARVEST_DISPATCHING) && (phase != Phase.QUEST_COMBAT_SUBMITTED) && (phase != Phase.QUEST_CALLBACK_WAIT))
		{
			throw new IllegalArgumentException("Acquisition phase attempt has no dispatch owner.");
		}
		if (((phase == Phase.SPOIL_PREPARED) || (phase == Phase.SPOIL_DISPATCHING) || (phase == Phase.SPOIL_OBSERVED) || (phase == Phase.SWEEP_PREPARED) || (phase == Phase.SWEEP_DISPATCHING)) && ((selectedSource == null) || (selectedSource.method() != Method.SPOIL_SWEEP)))
		{
			throw new IllegalArgumentException("Spoil acquisition phase requires a spoil/sweep source.");
		}
		final Method selectedMethod = selectedSource == null ? null : selectedSource.method();
		if ((selectedMethod == Method.MANOR_CROP) != (methodBinding instanceof ManorBinding))
		{
			throw new IllegalArgumentException("Manor acquisition source and binding disagree.");
		}
		if ((selectedMethod == Method.QUEST_COLLECTION) != (methodBinding instanceof QuestBinding))
		{
			throw new IllegalArgumentException("Quest acquisition source and binding disagree.");
		}
		if ((methodBinding != null) && (selectedSource == null))
		{
			throw new IllegalArgumentException("Acquisition binding has no selected source.");
		}
		if ((methodBinding instanceof ManorBinding manor) && ((selectedSource.itemId() != manor.cropItemId()) || (selectedSource.npcId() <= 0)))
		{
			throw new IllegalArgumentException("Manor source and binding identity disagree.");
		}
		if ((methodBinding instanceof QuestBinding quest) && ((selectedSource.itemId() != quest.questItemId()) || (selectedSource.npcId() != quest.targetNpcId())))
		{
			throw new IllegalArgumentException("Quest source and binding identity disagree.");
		}
		final boolean manorPhase = (phase == Phase.SOW_PREPARED) || (phase == Phase.SOW_DISPATCHING) || (phase == Phase.SOW_OBSERVED) || (phase == Phase.HARVEST_PREPARED) || (phase == Phase.HARVEST_DISPATCHING);
		final boolean questPhase = (phase == Phase.QUEST_COMBAT_PREPARED) || (phase == Phase.QUEST_COMBAT_SUBMITTED) || (phase == Phase.QUEST_COMBAT_TERMINAL) || (phase == Phase.QUEST_CALLBACK_WAIT);
		if (manorPhase && (selectedMethod != Method.MANOR_CROP))
		{
			throw new IllegalArgumentException("Manor acquisition phase requires a manor binding.");
		}
		if (questPhase && (selectedMethod != Method.QUEST_COLLECTION))
		{
			throw new IllegalArgumentException("Quest acquisition phase requires a quest binding.");
		}
		if (((status == Status.BLOCKED) || (status == Status.COMPLETED) || (status == Status.FAILED) || (status == Status.STALE_AUTHORITY) || (status == Status.DEFERRED_CHECKPOINT_2) || (status == Status.INCONSISTENT) || (status == Status.PLANNING_ONLY)) && (phase != Phase.NONE))
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
		if ((recipePlan != null) && (status == Status.PLANNING_ONLY) && (!receipts.isEmpty() || (progress != 0)))
		{
			throw new IllegalArgumentException("Planning-only recipe state cannot contain execution receipts or progress.");
		}
	}

	public PhantomAcquisitionState withPlan(Status nextStatus, Source source, List<Candidate> ranked, RecipePlan plan, Phase nextPhase, long minute)
	{
		return withPlan(nextStatus, source, ranked, plan, null, nextPhase, minute);
	}

	public PhantomAcquisitionState withPlan(Status nextStatus, Source source, List<Candidate> ranked, RecipePlan plan, MethodBinding binding, Phase nextPhase, long minute)
	{
		return new PhantomAcquisitionState(hashes, goalId, goalRevision, targetItemId, requiredAmount, baselineCount, lastObservedCount, progress, nextStatus, source, ranked, source == null ? 0 : indexOf(ranked, source.sourceId()), switchCount, nextPhase, 0, 0, 0, plan, binding, receipts, 0, minute);
	}

	public PhantomAcquisitionState withPhase(Phase nextPhase, int objectId, int npcId, int instanceId, long minute)
	{
		return withPhase(nextPhase, objectId, npcId, instanceId, 0, minute);
	}

	public PhantomAcquisitionState withPhase(Phase nextPhase, int objectId, int npcId, int instanceId, int nextAttempt, long minute)
	{
		return new PhantomAcquisitionState(hashes, goalId, goalRevision, targetItemId, requiredAmount, baselineCount, lastObservedCount, progress, status == Status.READY ? Status.ACTIVE : status, selectedSource, candidates, sourceCursor, switchCount, nextPhase, objectId, npcId, instanceId, recipePlan, methodBinding, receipts, nextAttempt, minute);
	}

	public PhantomAcquisitionState withBinding(MethodBinding nextBinding, Phase nextPhase, int objectId, int npcId, int instanceId, int nextAttempt, long minute)
	{
		return new PhantomAcquisitionState(hashes, goalId, goalRevision, targetItemId, requiredAmount, baselineCount, lastObservedCount, progress, status == Status.READY ? Status.ACTIVE : status, selectedSource, candidates, sourceCursor, switchCount, nextPhase, objectId, npcId, instanceId, recipePlan, nextBinding, receipts, nextAttempt, minute);
	}

	public PhantomAcquisitionState withMethodBinding(MethodBinding nextBinding)
	{
		return new PhantomAcquisitionState(hashes, goalId, goalRevision, targetItemId, requiredAmount, baselineCount, lastObservedCount, progress, status, selectedSource, candidates, sourceCursor, switchCount, phase, targetObjectId, targetNpcId, targetInstanceId, recipePlan, nextBinding, receipts, phaseAttempt, logicalMinute);
	}

	public PhantomAcquisitionState observe(long authoritativeCount, Status nextStatus, Phase nextPhase, Receipt receipt, long minute)
	{
		final long nextProgress = observedProgress(baselineCount, authoritativeCount, requiredAmount);
		final Status effectiveStatus = nextProgress == requiredAmount ? Status.COMPLETED : nextStatus;
		final List<Receipt> nextReceipts = appendReceipt(receipts, receipt);
		return new PhantomAcquisitionState(hashes, goalId, goalRevision, targetItemId, requiredAmount, baselineCount, authoritativeCount, nextProgress, effectiveStatus, selectedSource, candidates, sourceCursor, switchCount, nextProgress == requiredAmount ? Phase.NONE : nextPhase, 0, 0, 0, recipePlan, methodBinding, nextReceipts, 0, minute);
	}

	/** Records one authoritative active observation while retaining its exact bound target when incomplete. */
	public PhantomAcquisitionState observeBound(long authoritativeCount, Status nextStatus, Phase nextPhase, int objectId, int npcId, int instanceId, MethodBinding nextBinding, int nextAttempt, Receipt receipt, long minute)
	{
		final long nextProgress = observedProgress(baselineCount, authoritativeCount, requiredAmount);
		final boolean completed = nextProgress == requiredAmount;
		return new PhantomAcquisitionState(hashes, goalId, goalRevision, targetItemId, requiredAmount, baselineCount, authoritativeCount, nextProgress, completed ? Status.COMPLETED : nextStatus, selectedSource, candidates, sourceCursor, switchCount, completed ? Phase.NONE : nextPhase, completed ? 0 : objectId, completed ? 0 : npcId, completed ? 0 : instanceId, recipePlan, nextBinding, appendReceipt(receipts, receipt), completed ? 0 : nextAttempt, minute);
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
		return new PhantomAcquisitionState(hashes, goalId, goalRevision, targetItemId, requiredAmount, baselineCount, lastObservedCount, progress, Status.BLOCKED, selectedSource, next, sourceCursor, switchCount, Phase.NONE, 0, 0, 0, recipePlan, methodBinding, receipts, 0, minute);
	}

	public PhantomAcquisitionState switchSource(int nextCursor, Source nextSource, RecipePlan nextRecipePlan, long minute)
	{
		return switchSource(nextCursor, nextSource, nextRecipePlan, null, minute);
	}

	public PhantomAcquisitionState switchSource(int nextCursor, Source nextSource, RecipePlan nextRecipePlan, MethodBinding nextBinding, long minute)
	{
		if ((nextCursor < 0) || (nextCursor >= candidates.size()) || (nextCursor == sourceCursor) || (switchCount >= MAX_SWITCHES) || !candidates.get(nextCursor).sourceId().equals(nextSource.sourceId()))
		{
			throw new IllegalArgumentException("Invalid acquisition source switch.");
		}
		final Candidate next = candidates.get(nextCursor);
		return new PhantomAcquisitionState(hashes, goalId, goalRevision, targetItemId, requiredAmount, baselineCount, lastObservedCount, progress, Status.READY, nextSource, candidates, nextCursor, switchCount + 1, Phase.TRAVEL_REQUIRED, 0, 0, 0, next.method() == Method.RECIPE_PREPARATION ? nextRecipePlan : null, nextBinding, receipts, 0, minute);
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
		INCONSISTENT,
		PLANNING_ONLY
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
		VERIFYING,
		COMBAT_PREPARED,
		SOW_PREPARED,
		SOW_DISPATCHING,
		SOW_OBSERVED,
		HARVEST_PREPARED,
		HARVEST_DISPATCHING,
		QUEST_COMBAT_PREPARED,
		QUEST_COMBAT_SUBMITTED,
		QUEST_COMBAT_TERMINAL,
		QUEST_CALLBACK_WAIT
	}

	public enum ReceiptKind
	{
		ACTIVE_DEATH_DROP,
		ACTIVE_SPOIL,
		ACTIVE_SWEEP,
		BACKGROUND_DEATH_DROP,
		BACKGROUND_SPOIL_SWEEP,
		VERIFY,
		ACTIVE_MANOR_SOW,
		ACTIVE_MANOR_HARVEST,
		BACKGROUND_MANOR_CROP,
		ACTIVE_QUEST_COLLECTION,
		BACKGROUND_QUEST_COLLECTION,
		ACTIVE_SELF_CRAFT,
		BACKGROUND_SELF_CRAFT
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
			if (((method == Method.DEATH_DROP) || (method == Method.SPOIL_SWEEP) || (method == Method.MANOR_CROP) || (method == Method.QUEST_COLLECTION)) && ((npcId <= 0) || (instanceId != 0)))
			{
				throw new IllegalArgumentException("Executable acquisition source is not an instance-zero monster source.");
			}
			if ((method == Method.MANOR_CROP) && !factKey.startsWith("manor:"))
			{
				throw new IllegalArgumentException("Manor acquisition source identity is invalid.");
			}
			if ((method == Method.QUEST_COLLECTION) && !factKey.startsWith("quest:"))
			{
				throw new IllegalArgumentException("Quest acquisition source identity is invalid.");
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

	public sealed interface MethodBinding permits ManorBinding, QuestBinding
	{
		Method method();
	}

	public record ManorBinding(int castleId, int seedItemId, int cropItemId, int matureItemId, int reward1ItemId, int reward2ItemId, int seedLevel, boolean alternative, int rawSeedLimit, int rawCropLimit, int seedObjectId, int harvesterObjectId, long seedCountBeforeDispatch, long cropCountBeforeDispatch, String authorityHash) implements MethodBinding
	{
		public ManorBinding
		{
			if ((castleId <= 0) || (seedItemId <= 0) || (cropItemId <= 0) || (matureItemId <= 0) || (reward1ItemId <= 0) || (reward2ItemId <= 0) || (seedLevel < 0) || (rawSeedLimit < 0) || (rawCropLimit < 0) || (seedObjectId < 0) || (harvesterObjectId < 0) || (seedCountBeforeDispatch < 0) || (cropCountBeforeDispatch < 0) || ((seedObjectId == 0) != (harvesterObjectId == 0)))
			{
				throw new IllegalArgumentException("Invalid manor acquisition binding.");
			}
			requireHash(authorityHash, "manor authority");
		}

		@Override
		public Method method()
		{
			return Method.MANOR_CROP;
		}
	}

	public record QuestBinding(String ruleId, String ruleHash, int questId, String questName, String scriptHash, String expectedState, int expectedCond, int questItemId, long itemCap, int targetNpcId, long itemCountBeforeKill, long callbackDeadlineMillis, String authorityHash) implements MethodBinding
	{
		public QuestBinding
		{
			requireText(ruleId, 64, "quest rule ID");
			requireHash(ruleHash, "quest rule");
			requireText(questName, 96, "quest name");
			requireHash(scriptHash, "quest script");
			if ((questId <= 0) || !"STARTED".equals(expectedState) || (expectedCond < 0) || (expectedCond > 255) || (questItemId <= 0) || (itemCap <= 0) || (targetNpcId <= 0) || (itemCountBeforeKill < 0) || (itemCountBeforeKill >= itemCap) || (callbackDeadlineMillis < 0))
			{
				throw new IllegalArgumentException("Invalid quest acquisition binding.");
			}
			requireHash(authorityHash, "quest authority");
		}

		@Override
		public Method method()
		{
			return Method.QUEST_COLLECTION;
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
