/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.conversation;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.l2jmobius.gameserver.network.enums.ChatType;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.ConversationActionProposal;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.ConversationResponsePlan;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.SlotValue;

/** Compact durable truth for conversation action and outbound execution. */
public final class PhantomConversationExecutionModel
{
	public static final String COMPONENT_TYPE = "conversation.execution";
	public static final int SCHEMA_VERSION = 1;
	public static final int MAX_ENTRIES = 4;
	public static final int MAX_RECEIPTS = 16;
	public static final int MAX_ARGUMENTS = 4;
	public static final int MAX_TEXT_BYTES = 240;
	public static final int MAX_REFERENCE_BYTES = 64;
	public static final int MAX_ARGUMENT_BYTES = 48;
	private static final Pattern HASH = Pattern.compile("^[A-F0-9]{64}$");
	private static final Pattern KEY = Pattern.compile("^[a-z][a-z0-9_.-]{0,63}$");

	private PhantomConversationExecutionModel()
	{
	}

	public enum OutboundState
	{
		NONE,
		PREPARED,
		DISPATCHING,
		SENT,
		FAILED,
		UNCERTAIN,
		EXPIRED
	}

	public enum ActionState
	{
		NONE,
		PREPARED,
		SUBMITTED,
		COMPLETED,
		REJECTED,
		DEFERRED,
		EXPIRED,
		UNCERTAIN
	}

	public record Argument(String key, String value) implements Comparable<Argument>
	{
		public Argument
		{
			key = requireKey(key, "Execution argument key");
			value = requireUtf8(value, MAX_ARGUMENT_BYTES, "Execution argument value");
		}

		@Override
		public int compareTo(Argument other)
		{
			return key.compareTo(other.key);
		}
	}

	public record ExecutionEntry(String planId, String observationHash, ChatType channel, PhantomDomainRef counterpart, String responseAct, String style, String text, String proposalKey, PhantomDomainRef target, List<Argument> arguments, long createdMinute, long expiryMinute, OutboundState outboundState, ActionState actionState, long goalId, long goalRevision, String reasonKey, int actionAttempts, int outboundAttempts, long terminalMinute)
	{
		public ExecutionEntry
		{
			planId = requireHash(planId, "Execution plan ID");
			observationHash = requireHash(observationHash, "Execution observation hash");
			Objects.requireNonNull(channel, "Execution channel must not be null.");
			counterpart = requireReference(counterpart, "Execution counterpart");
			responseAct = requireKey(responseAct, "Execution response act");
			style = requireKey(style, "Execution style");
			text = requireUtf8(text, MAX_TEXT_BYTES, "Execution text");
			proposalKey = proposalKey == null ? null : requireKey(proposalKey, "Execution proposal key");
			target = target == null ? null : requireReference(target, "Execution target");
			arguments = orderedArguments(arguments);
			Objects.requireNonNull(outboundState, "Execution outbound state must not be null.");
			Objects.requireNonNull(actionState, "Execution action state must not be null.");
			reasonKey = requireKey(reasonKey, "Execution reason key");
			if ((createdMinute < 0) || (expiryMinute <= createdMinute) || (goalId < 0) || (goalRevision < 0) || (actionAttempts < 0) || (actionAttempts > 255) || (outboundAttempts < 0) || (outboundAttempts > 255) || (terminalMinute < -1))
			{
				throw new IllegalArgumentException("Execution entry metadata is invalid.");
			}
			if (((proposalKey == null) != (actionState == ActionState.NONE)) || ((goalId == 0) && (goalRevision != 0)) || ((terminalMinute >= 0) != (terminalOutbound(outboundState) && terminalAction(actionState))))
			{
				throw new IllegalArgumentException("Execution entry state is inconsistent.");
			}
		}

		public static ExecutionEntry prepared(ConversationResponsePlan plan)
		{
			final ConversationActionProposal proposal = plan.proposal();
			final List<Argument> arguments = proposal == null ? List.of() : proposal.slots().stream().limit(MAX_ARGUMENTS).map(PhantomConversationExecutionModel::argument).sorted().toList();
			final String canonical = plan.ownerProfileId() + "|" + plan.dispatchId() + "|" + plan.observationHash() + "|" + plan.semanticResultHash() + "|" + plan.channel() + "|" + plan.counterpart().reference().namespace() + ':' + plan.counterpart().reference().key() + "|" + plan.responseAct() + "|" + plan.style() + "|" + (proposal == null ? "none" : proposal.proposalKey() + '|' + proposal.semanticResultHash());
			return new ExecutionEntry(PhantomConversationModel.sha256(canonical), plan.observationHash(), plan.channel(), plan.counterpart().reference(), plan.responseAct(), plan.style(), plan.renderedText(), proposal == null ? null : proposal.proposalKey(), proposal == null ? null : proposal.target(), arguments, proposal == null ? Math.max(0, plan.cooldownUntilMinute() - 1) : proposal.createdMinute(), proposal == null ? Math.max(1, plan.cooldownUntilMinute() + 60) : proposal.expiryMinute(), OutboundState.PREPARED, proposal == null ? ActionState.NONE : ActionState.PREPARED, 0, 0, "execution.prepared", 0, 0, -1);
		}

		public ExecutionEntry withOutbound(OutboundState next, String reason, long nowMinute)
		{
			requireOutboundTransition(outboundState, next);
			return copy(next, actionState, goalId, goalRevision, reason, actionAttempts, outboundAttempts + (next == OutboundState.DISPATCHING ? 1 : 0), PhantomConversationExecutionModel.terminalMinute(next, actionState, nowMinute));
		}

		public ExecutionEntry withAction(ActionState next, long nextGoalId, long nextGoalRevision, String reason, long nowMinute)
		{
			requireActionTransition(actionState, next);
			return copy(outboundState, next, nextGoalId, nextGoalRevision, reason, actionAttempts + 1, outboundAttempts, PhantomConversationExecutionModel.terminalMinute(outboundState, next, nowMinute));
		}

		public ExecutionEntry withResult(String nextText, String reason)
		{
			return new ExecutionEntry(planId, observationHash, channel, counterpart, responseAct, style, nextText, proposalKey, target, arguments, createdMinute, expiryMinute, outboundState, actionState, goalId, goalRevision, reason, actionAttempts, outboundAttempts, terminalMinute);
		}

		public boolean terminal()
		{
			return terminalOutbound(outboundState) && terminalAction(actionState);
		}

		private ExecutionEntry copy(OutboundState outbound, ActionState action, long nextGoalId, long nextGoalRevision, String reason, int nextActionAttempts, int nextOutboundAttempts, long nextTerminalMinute)
		{
			return new ExecutionEntry(planId, observationHash, channel, counterpart, responseAct, style, text, proposalKey, target, arguments, createdMinute, expiryMinute, outbound, action, nextGoalId, nextGoalRevision, reason, nextActionAttempts, nextOutboundAttempts, nextTerminalMinute);
		}
	}

	public record ExecutionReceipt(String planId, String observationHash, OutboundState outboundState, ActionState actionState, long terminalMinute, String reasonKey) implements Comparable<ExecutionReceipt>
	{
		public ExecutionReceipt
		{
			planId = requireHash(planId, "Execution receipt plan ID");
			observationHash = requireHash(observationHash, "Execution receipt observation hash");
			Objects.requireNonNull(outboundState);
			Objects.requireNonNull(actionState);
			reasonKey = requireKey(reasonKey, "Execution receipt reason");
			if (!terminalOutbound(outboundState) || !terminalAction(actionState) || (terminalMinute < 0))
			{
				throw new IllegalArgumentException("Execution receipt is not terminal.");
			}
		}

		public static ExecutionReceipt from(ExecutionEntry entry)
		{
			if (!entry.terminal())
			{
				throw new IllegalArgumentException("Only a terminal execution entry can be compacted.");
			}
			return new ExecutionReceipt(entry.planId(), entry.observationHash(), entry.outboundState(), entry.actionState(), entry.terminalMinute(), entry.reasonKey());
		}

		@Override
		public int compareTo(ExecutionReceipt other)
		{
			return planId.compareTo(other.planId);
		}
	}

	public record ExecutionState(String catalogHash, long logicalMinute, List<ExecutionEntry> entries, List<ExecutionReceipt> receipts)
	{
		public ExecutionState
		{
			catalogHash = requireHash(catalogHash, "Execution catalog hash");
			if (logicalMinute < 0)
			{
				throw new IllegalArgumentException("Execution logical minute is invalid.");
			}
			entries = ordered(entries, MAX_ENTRIES, Comparator.comparing(ExecutionEntry::planId), "execution entries");
			receipts = ordered(receipts, MAX_RECEIPTS, Comparator.naturalOrder(), "execution receipts");
			final Set<String> plans = new HashSet<>();
			if (!entries.stream().map(ExecutionEntry::planId).allMatch(plans::add) || !receipts.stream().map(ExecutionReceipt::planId).allMatch(plans::add))
			{
				throw new IllegalArgumentException("Execution state contains a duplicate plan.");
			}
		}

		public static ExecutionState empty(String catalogHash, long logicalMinute)
		{
			return new ExecutionState(catalogHash, logicalMinute, List.of(), List.of());
		}

		public ExecutionEntry entry(String planId)
		{
			return entries.stream().filter(entry -> entry.planId().equals(planId)).findFirst().orElse(null);
		}

		public boolean contains(String planId)
		{
			return (entry(planId) != null) || receipts.stream().anyMatch(receipt -> receipt.planId().equals(planId));
		}

		public ExecutionState add(ExecutionEntry entry)
		{
			if (contains(entry.planId()) || (entries.size() >= MAX_ENTRIES))
			{
				throw new IllegalStateException(contains(entry.planId()) ? "DUPLICATE" : "CAPACITY_REACHED");
			}
			final List<ExecutionEntry> next = new ArrayList<>(entries);
			next.add(entry);
			next.sort(Comparator.comparing(ExecutionEntry::planId));
			return new ExecutionState(catalogHash, Math.max(logicalMinute, entry.createdMinute()), next, receipts);
		}

		public ExecutionState replace(ExecutionEntry entry)
		{
			final List<ExecutionEntry> next = new ArrayList<>(entries);
			final int index = next.indexOf(entry(entry.planId()));
			if (index < 0)
			{
				throw new IllegalArgumentException("Execution entry is absent.");
			}
			next.set(index, entry);
			return new ExecutionState(catalogHash, Math.max(logicalMinute, Math.max(entry.createdMinute(), entry.terminalMinute())), next, receipts);
		}

		public ExecutionState compact(String planId)
		{
			final ExecutionEntry entry = entry(planId);
			if ((entry == null) || !entry.terminal())
			{
				throw new IllegalArgumentException("Execution entry is not compactable.");
			}
			final List<ExecutionEntry> nextEntries = entries.stream().filter(item -> !item.planId().equals(planId)).toList();
			final List<ExecutionReceipt> nextReceipts = new ArrayList<>(receipts);
			if (nextReceipts.size() >= MAX_RECEIPTS)
			{
				throw new IllegalStateException("RECEIPT_CAPACITY_REACHED");
			}
			nextReceipts.add(ExecutionReceipt.from(entry));
			nextReceipts.sort(Comparator.naturalOrder());
			return new ExecutionState(catalogHash, Math.max(logicalMinute, entry.terminalMinute()), nextEntries, nextReceipts);
		}

		public ExecutionState pruneReceipts(long minimumTerminalMinute)
		{
			if (minimumTerminalMinute <= 0)
			{
				return this;
			}
			final List<ExecutionReceipt> retained = receipts.stream().filter(receipt -> receipt.terminalMinute() >= minimumTerminalMinute).toList();
			return retained.size() == receipts.size() ? this : new ExecutionState(catalogHash, logicalMinute, entries, retained);
		}
	}

	private static Argument argument(SlotValue slot)
	{
		return new Argument(slot.type().name().toLowerCase().replace('_', '.'), slot.canonicalValue());
	}

	private static long terminalMinute(OutboundState outbound, ActionState action, long nowMinute)
	{
		return terminalOutbound(outbound) && terminalAction(action) ? Math.max(0, nowMinute) : -1;
	}

	public static boolean terminalOutbound(OutboundState state)
	{
		return Set.of(OutboundState.SENT, OutboundState.FAILED, OutboundState.UNCERTAIN, OutboundState.EXPIRED).contains(state);
	}

	public static boolean terminalAction(ActionState state)
	{
		return Set.of(ActionState.NONE, ActionState.COMPLETED, ActionState.REJECTED, ActionState.DEFERRED, ActionState.EXPIRED, ActionState.UNCERTAIN).contains(state);
	}

	private static void requireOutboundTransition(OutboundState current, OutboundState next)
	{
		final boolean valid = (current == OutboundState.NONE) && (next == OutboundState.PREPARED) //
			|| (current == OutboundState.PREPARED) && Set.of(OutboundState.DISPATCHING, OutboundState.FAILED, OutboundState.EXPIRED).contains(next) //
			|| (current == OutboundState.DISPATCHING) && Set.of(OutboundState.SENT, OutboundState.FAILED, OutboundState.UNCERTAIN).contains(next);
		if (!valid)
		{
			throw new IllegalArgumentException("Invalid outbound transition: " + current + " -> " + next);
		}
	}

	private static void requireActionTransition(ActionState current, ActionState next)
	{
		final boolean valid = (current == ActionState.NONE) && (next == ActionState.PREPARED) //
			|| (current == ActionState.PREPARED) && Set.of(ActionState.SUBMITTED, ActionState.COMPLETED, ActionState.REJECTED, ActionState.DEFERRED, ActionState.EXPIRED).contains(next) //
			|| (current == ActionState.SUBMITTED) && Set.of(ActionState.COMPLETED, ActionState.REJECTED, ActionState.EXPIRED, ActionState.UNCERTAIN).contains(next);
		if (!valid)
		{
			throw new IllegalArgumentException("Invalid action transition: " + current + " -> " + next);
		}
	}

	private static PhantomDomainRef requireReference(PhantomDomainRef reference, String label)
	{
		Objects.requireNonNull(reference, label + " must not be null.");
		requireUtf8(reference.namespace() + ':' + reference.key(), MAX_REFERENCE_BYTES, label);
		return reference;
	}

	static String requireHash(String value, String label)
	{
		if ((value == null) || !HASH.matcher(value).matches())
		{
			throw new IllegalArgumentException(label + " must be an uppercase SHA-256 hash.");
		}
		return value;
	}

	static String requireKey(String value, String label)
	{
		if ((value == null) || !KEY.matcher(value).matches())
		{
			throw new IllegalArgumentException(label + " is invalid.");
		}
		return value;
	}

	static String requireUtf8(String value, int maximumBytes, String label)
	{
		if ((value == null) || value.isBlank() || (value.getBytes(StandardCharsets.UTF_8).length > maximumBytes) || value.codePoints().anyMatch(Character::isISOControl))
		{
			throw new IllegalArgumentException(label + " is invalid.");
		}
		return value;
	}

	private static List<Argument> orderedArguments(List<Argument> values)
	{
		return ordered(values, MAX_ARGUMENTS, Comparator.naturalOrder(), "execution arguments");
	}

	private static <T> List<T> ordered(List<T> values, int maximum, Comparator<T> comparator, String label)
	{
		final List<T> copy = new ArrayList<>(Objects.requireNonNull(values, label + " must not be null."));
		if (copy.size() > maximum)
		{
			throw new IllegalArgumentException(label + " exceed the hard bound.");
		}
		final List<T> sorted = copy.stream().map(Objects::requireNonNull).sorted(comparator).toList();
		if (!copy.equals(sorted) || (new HashSet<>(copy).size() != copy.size()))
		{
			throw new IllegalArgumentException(label + " must be strictly ordered and unique.");
		}
		return List.copyOf(copy);
	}
}
