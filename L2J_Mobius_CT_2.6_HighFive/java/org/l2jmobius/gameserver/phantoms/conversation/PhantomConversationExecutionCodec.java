/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.conversation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import org.l2jmobius.gameserver.network.enums.ChatType;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.ActionState;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.Argument;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.ExecutionEntry;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.ExecutionReceipt;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.ExecutionState;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.InvitationBinding;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.InvitationResponse;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.OutboundState;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;

/** Fail-closed binary codec whose declared maximum is below one component row. */
public final class PhantomConversationExecutionCodec
{
	public static final int DECLARED_WORST_CASE_BYTES = 4076;
	private static final int LEGACY_MAGIC = 0x43584531;
	private static final int MAGIC = 0x43584532;
	private static final List<String> ARGUMENT_KEYS = List.of("capability", "content", "item", "location", "npc", "party.role", "quantity", "response", "target.player", "topology.node");
	private final PhantomConversationExecutionCatalog _catalog;

	public PhantomConversationExecutionCodec(PhantomConversationExecutionCatalog catalog)
	{
		_catalog = java.util.Objects.requireNonNull(catalog);
		if (DECLARED_WORST_CASE_BYTES > 4096)
		{
			throw new IllegalStateException("Declared execution payload exceeds 4096 bytes.");
		}
	}

	public byte[] encode(ExecutionState state)
	{
		try
		{
			final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (DataOutputStream output = new DataOutputStream(bytes))
			{
				output.writeInt(MAGIC);
				output.writeShort(PhantomConversationExecutionModel.SCHEMA_VERSION);
				writeHash(output, state.catalogHash());
				output.writeLong(state.logicalMinute());
				output.writeByte(state.entries().size());
				output.writeByte(state.receipts().size());
				for (ExecutionEntry entry : state.entries())
				{
					writeEntry(output, entry);
				}
				for (ExecutionReceipt receipt : state.receipts())
				{
					writeReceipt(output, receipt);
				}
			}
			final byte[] result = bytes.toByteArray();
			if (result.length > 4096)
			{
				throw new IllegalArgumentException("conversation.execution payload exceeds 4096 bytes.");
			}
			return result;
		}
		catch (RuntimeException exception)
		{
			throw exception;
		}
		catch (Exception exception)
		{
			throw new IllegalArgumentException("Could not encode conversation.execution.", exception);
		}
	}

	public ExecutionState decode(byte[] payload)
	{
		if ((payload == null) || (payload.length < 1) || (payload.length > 4096))
		{
			throw new IllegalArgumentException("conversation.execution payload size is invalid.");
		}
		try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload)))
		{
			final int magic = input.readInt();
			if (((magic != MAGIC) && (magic != LEGACY_MAGIC)) || (input.readUnsignedShort() != PhantomConversationExecutionModel.SCHEMA_VERSION))
			{
				throw new IllegalArgumentException("Unknown conversation.execution version.");
			}
			final String catalogHash = readHash(input);
			final long logicalMinute = input.readLong();
			final int entryCount = input.readUnsignedByte();
			final int receiptCount = input.readUnsignedByte();
			if ((entryCount > PhantomConversationExecutionModel.MAX_ENTRIES) || (receiptCount > PhantomConversationExecutionModel.MAX_RECEIPTS))
			{
				throw new IllegalArgumentException("conversation.execution counts are invalid.");
			}
			final List<ExecutionEntry> entries = new ArrayList<>(entryCount);
			for (int index = 0; index < entryCount; index++)
			{
				entries.add(readEntry(input, magic == MAGIC));
			}
			final List<ExecutionReceipt> receipts = new ArrayList<>(receiptCount);
			for (int index = 0; index < receiptCount; index++)
			{
				receipts.add(readReceipt(input));
			}
			if (input.available() != 0)
			{
				throw new IllegalArgumentException("conversation.execution has trailing bytes.");
			}
			return new ExecutionState(catalogHash, logicalMinute, entries, receipts);
		}
		catch (RuntimeException exception)
		{
			throw exception;
		}
		catch (Exception exception)
		{
			throw new IllegalArgumentException("conversation.execution payload is invalid.", exception);
		}
	}

	private void writeEntry(DataOutputStream output, ExecutionEntry entry) throws Exception
	{
		writeHash(output, entry.planId());
		writeHash(output, entry.observationHash());
		output.writeByte(entry.channel().ordinal());
		writeReference(output, entry.counterpart());
		output.writeByte(_catalog.responseActId(entry.responseAct()));
		output.writeByte(_catalog.styleId(entry.style()));
		output.writeByte(_catalog.proposalId(entry.proposalKey()));
		writeString(output, entry.text(), PhantomConversationExecutionModel.MAX_TEXT_BYTES, true);
		output.writeBoolean(entry.target() != null);
		if (entry.target() != null)
		{
			writeReference(output, entry.target());
		}
		output.writeByte(entry.arguments().size());
		for (Argument argument : entry.arguments())
		{
			final int id = ARGUMENT_KEYS.indexOf(argument.key());
			if (id < 0)
			{
				throw new IllegalArgumentException("Unknown execution argument key.");
			}
			output.writeByte(id + 1);
			writeString(output, argument.value(), PhantomConversationExecutionModel.MAX_ARGUMENT_BYTES, false);
		}
		output.writeBoolean(entry.invitationBinding() != null);
		if (entry.invitationBinding() != null)
		{
			output.writeLong(entry.invitationBinding().sequence());
			output.writeInt(entry.invitationBinding().requesterObjectId());
			output.writeInt(entry.invitationBinding().inviteeObjectId());
			output.writeByte(entry.invitationBinding().response().ordinal());
		}
		output.writeLong(entry.createdMinute());
		output.writeLong(entry.expiryMinute());
		output.writeByte(entry.outboundState().ordinal());
		output.writeByte(entry.actionState().ordinal());
		output.writeLong(entry.goalId());
		output.writeLong(entry.goalRevision());
		output.writeByte(_catalog.reasonId(entry.reasonKey()));
		output.writeByte(entry.actionAttempts());
		output.writeByte(entry.outboundAttempts());
		output.writeLong(entry.terminalMinute());
	}

	private ExecutionEntry readEntry(DataInputStream input, boolean hasInvitationBinding) throws Exception
	{
		final String planId = readHash(input);
		final String observationHash = readHash(input);
		final int channelId = input.readUnsignedByte();
		if (channelId >= ChatType.values().length)
		{
			throw new IllegalArgumentException("Unknown execution chat channel.");
		}
		final PhantomDomainRef counterpart = readReference(input);
		final String responseAct = _catalog.responseAct(input.readUnsignedByte());
		final String style = _catalog.style(input.readUnsignedByte());
		final String proposal = _catalog.proposal(input.readUnsignedByte());
		final String text = readString(input, PhantomConversationExecutionModel.MAX_TEXT_BYTES, true);
		final PhantomDomainRef target = input.readBoolean() ? readReference(input) : null;
		final int argumentCount = input.readUnsignedByte();
		if (argumentCount > PhantomConversationExecutionModel.MAX_ARGUMENTS)
		{
			throw new IllegalArgumentException("Execution argument count is invalid.");
		}
		final List<Argument> arguments = new ArrayList<>(argumentCount);
		for (int index = 0; index < argumentCount; index++)
		{
			final int keyId = input.readUnsignedByte();
			if ((keyId < 1) || (keyId > ARGUMENT_KEYS.size()))
			{
				throw new IllegalArgumentException("Unknown execution argument ID.");
			}
			arguments.add(new Argument(ARGUMENT_KEYS.get(keyId - 1), readString(input, PhantomConversationExecutionModel.MAX_ARGUMENT_BYTES, false)));
		}
		final InvitationBinding binding;
		if (hasInvitationBinding && input.readBoolean())
		{
			binding = new InvitationBinding(input.readLong(), input.readInt(), input.readInt(), enumValue(InvitationResponse.values(), input.readUnsignedByte(), "invitation response"));
		}
		else
		{
			binding = null;
		}
		return new ExecutionEntry(planId, observationHash, ChatType.values()[channelId], counterpart, responseAct, style, text, proposal, target, arguments, binding, input.readLong(), input.readLong(), enumValue(OutboundState.values(), input.readUnsignedByte(), "outbound"), enumValue(ActionState.values(), input.readUnsignedByte(), "action"), input.readLong(), input.readLong(), _catalog.reason(input.readUnsignedByte()), input.readUnsignedByte(), input.readUnsignedByte(), input.readLong());
	}

	private void writeReceipt(DataOutputStream output, ExecutionReceipt receipt) throws Exception
	{
		writeHash(output, receipt.planId());
		writeHash(output, receipt.observationHash());
		output.writeByte(receipt.outboundState().ordinal());
		output.writeByte(receipt.actionState().ordinal());
		output.writeLong(receipt.terminalMinute());
		output.writeByte(_catalog.reasonId(receipt.reasonKey()));
	}

	private ExecutionReceipt readReceipt(DataInputStream input) throws Exception
	{
		return new ExecutionReceipt(readHash(input), readHash(input), enumValue(OutboundState.values(), input.readUnsignedByte(), "outbound"), enumValue(ActionState.values(), input.readUnsignedByte(), "action"), input.readLong(), _catalog.reason(input.readUnsignedByte()));
	}

	private static void writeReference(DataOutputStream output, PhantomDomainRef reference) throws Exception
	{
		writeString(output, reference.namespace() + ':' + reference.key(), PhantomConversationExecutionModel.MAX_REFERENCE_BYTES, false);
	}

	private static PhantomDomainRef readReference(DataInputStream input) throws Exception
	{
		final String value = readString(input, PhantomConversationExecutionModel.MAX_REFERENCE_BYTES, false);
		final int separator = value.indexOf(':');
		if ((separator < 1) || (separator == value.length() - 1))
		{
			throw new IllegalArgumentException("Execution reference is invalid.");
		}
		return new PhantomDomainRef(value.substring(0, separator), value.substring(separator + 1));
	}

	private static void writeHash(DataOutputStream output, String hash) throws Exception
	{
		output.write(HexFormat.of().parseHex(PhantomConversationExecutionModel.requireHash(hash, "Execution hash")));
	}

	private static String readHash(DataInputStream input) throws Exception
	{
		return HexFormat.of().withUpperCase().formatHex(input.readNBytes(32));
	}

	private static void writeString(DataOutputStream output, String value, int maximum, boolean wide) throws Exception
	{
		final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		if ((bytes.length < 1) || (bytes.length > maximum))
		{
			throw new IllegalArgumentException("Execution string size is invalid.");
		}
		if (wide)
		{
			output.writeShort(bytes.length);
		}
		else
		{
			output.writeByte(bytes.length);
		}
		output.write(bytes);
	}

	private static String readString(DataInputStream input, int maximum, boolean wide) throws Exception
	{
		final int length = wide ? input.readUnsignedShort() : input.readUnsignedByte();
		if ((length < 1) || (length > maximum))
		{
			throw new IllegalArgumentException("Execution string length is invalid.");
		}
		final byte[] bytes = input.readNBytes(length);
		if (bytes.length != length)
		{
			throw new IllegalArgumentException("Execution string is truncated.");
		}
		return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
	}

	private static <T> T enumValue(T[] values, int ordinal, String label)
	{
		if ((ordinal < 0) || (ordinal >= values.length))
		{
			throw new IllegalArgumentException("Unknown execution " + label + " state.");
		}
		return values[ordinal];
	}
}
