/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.conversation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import org.l2jmobius.gameserver.network.enums.ChatType;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.ConversationSession;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.ConversationState;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.PendingClarification;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.SlotType;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.SlotValue;

/** Strict compact conversation.state schema 1 codec. */
public final class PhantomConversationStateCodec
{
	public static final int DECLARED_WORST_CASE_BYTES = 3456;
	private static final int MAGIC = 0x434f4e56;
	private static final int FORMAT = 1;
	private static final List<String> INTENTS = List.of("party.invite", "party.accept", "party.refuse", "party.leave", "party.role.query", "party.travel", "party.support.request", "party.assist.request", "party.regroup.request", "entity.locate", "item.acquire.query", "item.source.query", "content.requirements.query", "unknown");
	private static final List<String> NAMESPACES = List.of("profile", "character.object", "party.role", "capability", "item", "npc", "content", "topology.node", "location", "party");

	public byte[] encode(ConversationState state)
	{
		try
		{
			final ByteArrayOutputStream bytes = new ByteArrayOutputStream(1024);
			final DataOutputStream output = new DataOutputStream(bytes);
			output.writeInt(MAGIC);
			output.writeByte(FORMAT);
			writeHash(output, state.catalogHash());
			writeHash(output, state.packHash());
			writeHash(output, state.corpusHash());
			writeHash(output, state.knowledgeHash());
			writeHash(output, state.topologyHash());
			writeHash(output, state.roleHash());
			writeHash(output, state.socialHash());
			output.writeLong(state.logicalMinute());
			output.writeByte(state.sessions().size());
			for (ConversationSession session : state.sessions())
			{
				writeSession(output, state, session);
			}
			output.writeByte(state.recentObservationHashes().size());
			for (String hash : state.recentObservationHashes())
			{
				writeHash(output, hash);
			}
			output.flush();
			final byte[] result = bytes.toByteArray();
			if (result.length > 4096)
			{
				throw new IllegalArgumentException("conversation.state exceeds 4096 bytes.");
			}
			return result;
		}
		catch (IllegalArgumentException exception)
		{
			throw exception;
		}
		catch (Exception exception)
		{
			throw new IllegalArgumentException("Could not encode conversation.state.", exception);
		}
	}

	public ConversationState decode(byte[] payload)
	{
		if ((payload == null) || (payload.length < 239) || (payload.length > 4096))
		{
			throw new IllegalArgumentException("conversation.state payload length is invalid.");
		}
		try
		{
			final DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload.clone()));
			if ((input.readInt() != MAGIC) || (input.readUnsignedByte() != FORMAT))
			{
				throw new IllegalArgumentException("conversation.state header is invalid.");
			}
			final String catalogHash = readHash(input);
			final String packHash = readHash(input);
			final String corpusHash = readHash(input);
			final String knowledgeHash = readHash(input);
			final String topologyHash = readHash(input);
			final String roleHash = readHash(input);
			final String socialHash = readHash(input);
			final long logicalMinute = input.readLong();
			final int sessionCount = input.readUnsignedByte();
			if (sessionCount > PhantomConversationModel.MAX_SESSIONS)
			{
				throw new IllegalArgumentException("conversation.state session count is invalid.");
			}
			final List<ConversationSession> sessions = new ArrayList<>(sessionCount);
			for (int index = 0; index < sessionCount; index++)
			{
				sessions.add(readSession(input, packHash, corpusHash, knowledgeHash, topologyHash, roleHash));
			}
			final int recentCount = input.readUnsignedByte();
			if (recentCount > PhantomConversationModel.MAX_RECENT_HASHES)
			{
				throw new IllegalArgumentException("conversation.state recent hash count is invalid.");
			}
			final List<String> recent = new ArrayList<>(recentCount);
			for (int index = 0; index < recentCount; index++)
			{
				recent.add(readHash(input));
			}
			if (input.available() != 0)
			{
				throw new IllegalArgumentException("conversation.state has trailing bytes.");
			}
			return new ConversationState(catalogHash, packHash, corpusHash, knowledgeHash, topologyHash, roleHash, socialHash, logicalMinute, sessions, recent);
		}
		catch (IllegalArgumentException exception)
		{
			throw exception;
		}
		catch (Exception exception)
		{
			throw new IllegalArgumentException("Could not decode conversation.state.", exception);
		}
	}

	private static void writeSession(DataOutputStream output, ConversationState state, ConversationSession session) throws Exception
	{
		output.writeByte(channelCode(session.channel()));
		writeReference(output, session.counterpart());
		output.writeLong(session.lastObservedMinute());
		output.writeLong(session.cooldownUntilMinute());
		writeIntent(output, session.previousIntent());
		writeSlots(output, session.previousSlots());
		output.writeBoolean(session.pending() != null);
		if (session.pending() != null)
		{
			final PendingClarification pending = session.pending();
			if (!pending.packHash().equals(state.packHash()) || !pending.corpusHash().equals(state.corpusHash()) || !pending.knowledgeHash().equals(state.knowledgeHash()) || !pending.topologyHash().equals(state.topologyHash()) || !pending.roleHash().equals(state.roleHash()))
			{
				throw new IllegalArgumentException("Pending clarification authority differs from conversation.state.");
			}
			writeIntent(output, pending.intentKey());
			writeSlots(output, pending.knownSlots());
			int mask = 0;
			for (SlotType type : pending.missingSlots())
			{
				mask |= 1 << type.ordinal();
			}
			output.writeShort(mask);
			output.writeLong(pending.expiryMinute());
		}
		writeOptionalHash(output, session.lastResponseActHash());
		writeOptionalHash(output, session.lastStyleHash());
		writeOptionalHash(output, session.lastProposalHash());
	}

	private static ConversationSession readSession(DataInputStream input, String packHash, String corpusHash, String knowledgeHash, String topologyHash, String roleHash) throws Exception
	{
		final ChatType channel = channel(input.readUnsignedByte());
		final PhantomDomainRef counterpart = readReference(input);
		final long lastObserved = input.readLong();
		final long cooldown = input.readLong();
		final String previousIntent = readIntent(input, true);
		final List<SlotValue> previousSlots = readSlots(input);
		final PendingClarification pending;
		final int present = input.readUnsignedByte();
		if (present == 1)
		{
			final String intent = readIntent(input, false);
			final List<SlotValue> known = readSlots(input);
			final int mask = input.readUnsignedShort();
			final Set<SlotType> missing = EnumSet.noneOf(SlotType.class);
			for (SlotType type : SlotType.values())
			{
				if ((mask & (1 << type.ordinal())) != 0)
				{
					missing.add(type);
				}
			}
			if ((mask >>> SlotType.values().length) != 0)
			{
				throw new IllegalArgumentException("Pending clarification slot mask is invalid.");
			}
			pending = new PendingClarification(intent, known, missing, input.readLong(), packHash, corpusHash, knowledgeHash, topologyHash, roleHash);
		}
		else if (present == 0)
		{
			pending = null;
		}
		else
		{
			throw new IllegalArgumentException("Pending clarification flag is invalid.");
		}
		return new ConversationSession(channel, counterpart, lastObserved, cooldown, previousIntent, previousSlots, pending, readOptionalHash(input), readOptionalHash(input), readOptionalHash(input));
	}

	private static void writeSlots(DataOutputStream output, List<SlotValue> slots) throws Exception
	{
		output.writeByte(slots.size());
		for (SlotValue slot : slots)
		{
			output.writeByte(slot.type().ordinal());
			if (slot.domainReference() != null)
			{
				output.writeByte(0);
				writeReference(output, slot.domainReference());
			}
			else if (slot.numericValue() != null)
			{
				output.writeByte(1);
				output.writeLong(slot.numericValue());
			}
			else
			{
				output.writeByte(2);
				writeText(output, slot.textValue());
			}
		}
	}

	private static List<SlotValue> readSlots(DataInputStream input) throws Exception
	{
		final int count = input.readUnsignedByte();
		if (count > PhantomConversationModel.MAX_PENDING_SLOTS)
		{
			throw new IllegalArgumentException("Stored conversation slot count is invalid.");
		}
		final List<SlotValue> result = new ArrayList<>(count);
		for (int index = 0; index < count; index++)
		{
			final int typeCode = input.readUnsignedByte();
			if (typeCode >= SlotType.values().length)
			{
				throw new IllegalArgumentException("Stored conversation slot type is invalid.");
			}
			final SlotType type = SlotType.values()[typeCode];
			final int kind = input.readUnsignedByte();
			if (kind == 0)
			{
				result.add(SlotValue.domain(type, readReference(input), -1, -1));
			}
			else if ((kind == 1) && (type == SlotType.QUANTITY))
			{
				result.add(SlotValue.quantity(input.readLong(), -1, -1));
			}
			else if ((kind == 2) && (type == SlotType.RESPONSE))
			{
				result.add(SlotValue.response(readText(input), -1, -1));
			}
			else
			{
				throw new IllegalArgumentException("Stored conversation slot value kind is invalid for its type.");
			}
		}
		return result;
	}

	private static void writeReference(DataOutputStream output, PhantomDomainRef reference) throws Exception
	{
		final int namespace = NAMESPACES.indexOf(reference.namespace());
		if (namespace < 0)
		{
			throw new IllegalArgumentException("Stored conversation reference namespace is unsupported.");
		}
		output.writeByte(namespace);
		writeText(output, reference.key());
	}

	private static PhantomDomainRef readReference(DataInputStream input) throws Exception
	{
		final int namespace = input.readUnsignedByte();
		if (namespace >= NAMESPACES.size())
		{
			throw new IllegalArgumentException("Stored conversation reference namespace is invalid.");
		}
		return new PhantomDomainRef(NAMESPACES.get(namespace), readText(input));
	}

	private static void writeIntent(DataOutputStream output, String intent) throws Exception
	{
		if (intent == null)
		{
			output.writeByte(0);
			return;
		}
		final int code = INTENTS.indexOf(intent);
		if (code < 0)
		{
			throw new IllegalArgumentException("Stored conversation intent is unsupported.");
		}
		output.writeByte(code + 1);
	}

	private static String readIntent(DataInputStream input, boolean optional) throws Exception
	{
		final int code = input.readUnsignedByte();
		if ((code == 0) && optional)
		{
			return null;
		}
		if ((code < 1) || (code > INTENTS.size()))
		{
			throw new IllegalArgumentException("Stored conversation intent code is invalid.");
		}
		return INTENTS.get(code - 1);
	}

	private static int channelCode(ChatType channel)
	{
		return switch (channel)
		{
			case GENERAL -> 0;
			case WHISPER -> 1;
			case PARTY -> 2;
			case TRADE -> 3;
			default -> throw new IllegalArgumentException("Stored conversation channel is unsupported.");
		};
	}

	private static ChatType channel(int code)
	{
		return switch (code)
		{
			case 0 -> ChatType.GENERAL;
			case 1 -> ChatType.WHISPER;
			case 2 -> ChatType.PARTY;
			case 3 -> ChatType.TRADE;
			default -> throw new IllegalArgumentException("Stored conversation channel code is invalid.");
		};
	}

	private static void writeHash(DataOutputStream output, String value) throws Exception
	{
		output.write(HexFormat.of().parseHex(PhantomConversationModel.requireHash(value, "Stored conversation hash")));
	}

	private static String readHash(DataInputStream input) throws Exception
	{
		final byte[] bytes = new byte[32];
		input.readFully(bytes);
		return HexFormat.of().withUpperCase().formatHex(bytes);
	}

	private static void writeOptionalHash(DataOutputStream output, String value) throws Exception
	{
		output.writeBoolean((value != null) && !value.isEmpty());
		if ((value != null) && !value.isEmpty())
		{
			writeHash(output, value);
		}
	}

	private static String readOptionalHash(DataInputStream input) throws Exception
	{
		final int present = input.readUnsignedByte();
		if (present == 0)
		{
			return "";
		}
		if (present != 1)
		{
			throw new IllegalArgumentException("Stored optional conversation hash flag is invalid.");
		}
		return readHash(input);
	}

	private static void writeText(DataOutputStream output, String value) throws Exception
	{
		final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		if ((bytes.length < 1) || (bytes.length > 255))
		{
			throw new IllegalArgumentException("Stored conversation text length is invalid.");
		}
		output.writeByte(bytes.length);
		output.write(bytes);
	}

	private static String readText(DataInputStream input) throws Exception
	{
		final int length = input.readUnsignedByte();
		if (length == 0)
		{
			throw new IllegalArgumentException("Stored conversation text is empty.");
		}
		final byte[] bytes = new byte[length];
		input.readFully(bytes);
		return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();
	}
}
