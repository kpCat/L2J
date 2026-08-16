/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.conversation;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import org.l2jmobius.gameserver.network.enums.ChatType;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.ActionState;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.ExecutionEntry;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.OutboundState;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionService.OutboundSubmission;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionService.OutboundSubmissionStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;

/**
 * Goal020-owned language and durable chat handoff for Goal025. Chat execution
 * remains in {@link L2jPhantomConversationExecutionPort}.
 */
public final class PhantomPvpConversationBridge
{
	public enum MessageKind
	{
		WARNING,
		HELP_REQUEST,
		DISENGAGE
	}

	public record Request(long ownerProfileId, PhantomDomainRef counterpart, MessageKind kind, String authorityHash, long createdMinute, long expiryMinute)
	{
		public Request
		{
			Objects.requireNonNull(counterpart);
			Objects.requireNonNull(kind);
			authorityHash = Objects.requireNonNull(authorityHash);
			if ((ownerProfileId <= 0) || !SetHolder.HASH.matcher(authorityHash).matches() || (createdMinute < 0) || (expiryMinute <= createdMinute))
			{
				throw new IllegalArgumentException("Invalid typed PvP outbound request.");
			}
		}
	}

	public record Submission(OutboundSubmissionStatus status, String planId)
	{
		public Submission
		{
			Objects.requireNonNull(status);
			planId = Objects.requireNonNull(planId);
		}

		public boolean durable()
		{
			return (status == OutboundSubmissionStatus.ACCEPTED) || (status == OutboundSubmissionStatus.IDEMPOTENT);
		}
	}

	public record Receipt(String planId, boolean delivered, OutboundState state, long terminalMinute)
	{
		public Receipt
		{
			Objects.requireNonNull(planId);
			Objects.requireNonNull(state);
			if (terminalMinute < 0)
			{
				throw new IllegalArgumentException("PvP outbound receipt is not terminal.");
			}
		}
	}

	private static final class SetHolder
	{
		private static final Pattern HASH = Pattern.compile("^[A-F0-9]{64}$");
	}

	private final PhantomConversationExecutionService _execution;

	public PhantomPvpConversationBridge(PhantomConversationExecutionService execution)
	{
		_execution = Objects.requireNonNull(execution);
	}

	public Submission submit(Request request)
	{
		Objects.requireNonNull(request);
		final String identity = request.ownerProfileId() + "|" + request.counterpart().namespace() + ':' + request.counterpart().key() + "|" + request.kind() + "|" + request.authorityHash();
		final String planId = PhantomConversationModel.sha256("pvp.outbound|" + identity);
		final String observationHash = PhantomConversationModel.sha256("pvp.outbound.observation|" + identity);
		final ChatType channel = request.kind() == MessageKind.HELP_REQUEST ? ChatType.PARTY : ChatType.WHISPER;
		final String responseAct = request.kind() == MessageKind.HELP_REQUEST ? "ack.action_proposed" : "ack.refused";
		final String style = request.kind() == MessageKind.WARNING ? "cold" : request.kind() == MessageKind.HELP_REQUEST ? "cautious" : "terse";
		final String text = switch (request.kind())
		{
			case WARNING -> "Предупреждаю: прекрати нападение, иначе я отвечу.";
			case HELP_REQUEST -> "Нужна помощь: на меня напали.";
			case DISENGAGE -> "Я прекращаю бой.";
		};
		final ExecutionEntry entry = new ExecutionEntry(planId, observationHash, channel, request.counterpart(), responseAct, style, text, null, null, List.of(), request.createdMinute(), request.expiryMinute(), OutboundState.PREPARED, ActionState.NONE, 0, 0, "execution.prepared", 0, 0, -1);
		final OutboundSubmission submitted = _execution.submitOutbound(request.ownerProfileId(), entry);
		return new Submission(submitted.status(), submitted.planId());
	}

	public Optional<Receipt> receipt(long ownerProfileId, String planId)
	{
		return _execution.outboundReceipt(ownerProfileId, planId).map(receipt -> new Receipt(receipt.planId(), receipt.outboundState() == OutboundState.SENT, receipt.outboundState(), receipt.terminalMinute()));
	}
}
