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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionCatalog.Method;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Candidate;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Deficit;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Hashes;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Phase;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Receipt;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.ReceiptKind;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.RecipeNode;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.RecipePlan;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Source;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Status;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.TerminalResult;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;

/** Canonical binary codec for {@code acquisition.state} schema version 1. */
public final class PhantomAcquisitionStateCodec
{
	private static final int MAGIC = 0x50415131;
	private static final int FORMAT_VERSION = 1;
	private static final int MAX_FACT_KEY_BYTES = 160;
	private static final int MAX_TOPOLOGY_ID_BYTES = 96;
	private static final int MAX_REASON_BYTES = 64;
	private static final int DECLARED_WORST_CASE_BYTES = 3824;

	public int declaredWorstCaseBytes()
	{
		return DECLARED_WORST_CASE_BYTES;
	}

	public byte[] encode(PhantomAcquisitionState state)
	{
		try
		{
			final ByteArrayOutputStream bytes = new ByteArrayOutputStream(1024);
			try (DataOutputStream output = new DataOutputStream(bytes))
			{
				output.writeInt(MAGIC);
				output.writeShort(FORMAT_VERSION);
				output.writeShort(PhantomAcquisitionState.SCHEMA_VERSION);
				writeHashes(output, state.hashes());
				output.writeLong(state.goalId());
				output.writeLong(state.goalRevision());
				output.writeInt(state.targetItemId());
				output.writeLong(state.requiredAmount());
				output.writeLong(state.baselineCount());
				output.writeLong(state.lastObservedCount());
				output.writeLong(state.progress());
				output.writeByte(state.status().ordinal());
				output.writeBoolean(state.selectedSource() != null);
				if (state.selectedSource() != null)
				{
					writeSource(output, state.selectedSource());
				}
				output.writeByte(state.candidates().size());
				for (Candidate candidate : state.candidates())
				{
					writeHash(output, candidate.sourceId());
					output.writeByte(candidate.method().code());
					output.writeInt(candidate.score());
					output.writeByte(candidate.failures());
					output.writeLong(candidate.lastFailureMinute());
					writeString(output, candidate.lastFailureReason(), MAX_REASON_BYTES);
				}
				output.writeByte(state.sourceCursor());
				output.writeByte(state.switchCount());
				output.writeByte(state.phase().ordinal());
				output.writeInt(state.targetObjectId());
				output.writeInt(state.targetNpcId());
				output.writeInt(state.targetInstanceId());
				output.writeBoolean(state.recipePlan() != null);
				if (state.recipePlan() != null)
				{
					writeRecipe(output, state.recipePlan());
				}
				output.writeByte(state.receipts().size());
				for (Receipt receipt : state.receipts())
				{
					writeHash(output, receipt.operationId());
					writeHash(output, receipt.sourceId());
					output.writeByte(receipt.kind().ordinal());
					output.writeLong(receipt.beforeCount());
					output.writeLong(receipt.afterCount());
					output.writeByte(receipt.result().ordinal());
					output.writeLong(receipt.logicalMinute());
				}
				output.writeLong(state.logicalMinute());
			}
			final byte[] payload = bytes.toByteArray();
			if ((payload.length > DECLARED_WORST_CASE_BYTES) || (payload.length > PhantomProfileComponent.MAX_PAYLOAD_BYTES))
			{
				throw new IllegalArgumentException("Encoded acquisition.state payload exceeds its declared bound.");
			}
			return payload;
		}
		catch (IOException exception)
		{
			throw new IllegalStateException("Unexpected in-memory acquisition state encoding failure.", exception);
		}
	}

	public PhantomAcquisitionState decode(byte[] payload)
	{
		if ((payload == null) || (payload.length > DECLARED_WORST_CASE_BYTES) || (payload.length > PhantomProfileComponent.MAX_PAYLOAD_BYTES))
		{
			throw new IllegalArgumentException("Invalid acquisition.state payload size.");
		}
		try
		{
			final ByteArrayInputStream bytes = new ByteArrayInputStream(payload);
			try (DataInputStream input = new DataInputStream(bytes))
			{
				if ((input.readInt() != MAGIC) || (input.readUnsignedShort() != FORMAT_VERSION) || (input.readUnsignedShort() != PhantomAcquisitionState.SCHEMA_VERSION))
				{
					throw new IllegalArgumentException("Unknown acquisition.state version.");
				}
				final Hashes hashes = readHashes(input);
				final long goalId = input.readLong();
				final long goalRevision = input.readLong();
				final int itemId = input.readInt();
				final long required = input.readLong();
				final long baseline = input.readLong();
				final long observed = input.readLong();
				final long progress = input.readLong();
				final Status status = enumValue(Status.values(), input.readUnsignedByte(), "status");
				final Source selected = input.readBoolean() ? readSource(input, bytes) : null;
				final int candidateCount = input.readUnsignedByte();
				if (candidateCount > PhantomAcquisitionState.MAX_CANDIDATES)
				{
					throw new IllegalArgumentException("Too many acquisition candidates.");
				}
				final List<Candidate> candidates = new ArrayList<>(candidateCount);
				for (int index = 0; index < candidateCount; index++)
				{
					candidates.add(new Candidate(readHash(input), Method.fromCode(input.readUnsignedByte()), input.readInt(), input.readUnsignedByte(), input.readLong(), readString(input, bytes, MAX_REASON_BYTES)));
				}
				final int cursor = input.readUnsignedByte();
				final int switches = input.readUnsignedByte();
				final Phase phase = enumValue(Phase.values(), input.readUnsignedByte(), "phase");
				final int targetObjectId = input.readInt();
				final int targetNpcId = input.readInt();
				final int targetInstanceId = input.readInt();
				final RecipePlan recipe = input.readBoolean() ? readRecipe(input, bytes) : null;
				final int receiptCount = input.readUnsignedByte();
				if (receiptCount > PhantomAcquisitionState.MAX_RECEIPTS)
				{
					throw new IllegalArgumentException("Too many acquisition receipts.");
				}
				final List<Receipt> receipts = new ArrayList<>(receiptCount);
				for (int index = 0; index < receiptCount; index++)
				{
					receipts.add(new Receipt(readHash(input), readHash(input), enumValue(ReceiptKind.values(), input.readUnsignedByte(), "receipt kind"), input.readLong(), input.readLong(), enumValue(TerminalResult.values(), input.readUnsignedByte(), "terminal result"), input.readLong()));
				}
				final PhantomAcquisitionState result = new PhantomAcquisitionState(hashes, goalId, goalRevision, itemId, required, baseline, observed, progress, status, selected, candidates, cursor, switches, phase, targetObjectId, targetNpcId, targetInstanceId, recipe, receipts, input.readLong());
				if ((bytes.available() != 0) || !Arrays.equals(payload, encode(result)))
				{
					throw new IllegalArgumentException("Non-canonical or trailing acquisition.state payload.");
				}
				return result;
			}
		}
		catch (EOFException exception)
		{
			throw new IllegalArgumentException("Truncated acquisition.state payload.", exception);
		}
		catch (IOException exception)
		{
			throw new IllegalArgumentException("Invalid acquisition.state payload.", exception);
		}
	}

	private static void writeHashes(DataOutputStream output, Hashes hashes) throws IOException
	{
		writeHash(output, hashes.catalog());
		writeHash(output, hashes.knowledge());
		writeHash(output, hashes.topology());
		writeHash(output, hashes.progression());
		writeHash(output, hashes.background());
	}

	private static Hashes readHashes(DataInputStream input) throws IOException
	{
		return new Hashes(readHash(input), readHash(input), readHash(input), readHash(input), readHash(input));
	}

	private static void writeSource(DataOutputStream output, Source source) throws IOException
	{
		writeHash(output, source.sourceId());
		output.writeByte(source.method().code());
		output.writeInt(source.npcId());
		output.writeInt(source.itemId());
		writeString(output, source.factKey(), MAX_FACT_KEY_BYTES);
		writeString(output, source.topologyNodeId(), MAX_TOPOLOGY_ID_BYTES);
		writeString(output, source.anchorId(), MAX_TOPOLOGY_ID_BYTES);
		output.writeInt(source.instanceId());
		output.writeInt(source.spoilSkillId());
		output.writeShort(source.spoilSkillLevel());
		output.writeInt(source.sweepSkillId());
		output.writeShort(source.sweepSkillLevel());
	}

	private static Source readSource(DataInputStream input, ByteArrayInputStream bytes) throws IOException
	{
		return new Source(readHash(input), Method.fromCode(input.readUnsignedByte()), input.readInt(), input.readInt(), readString(input, bytes, MAX_FACT_KEY_BYTES), readString(input, bytes, MAX_TOPOLOGY_ID_BYTES), readString(input, bytes, MAX_TOPOLOGY_ID_BYTES), input.readInt(), input.readInt(), input.readUnsignedShort(), input.readInt(), input.readUnsignedShort());
	}

	private static void writeRecipe(DataOutputStream output, RecipePlan plan) throws IOException
	{
		output.writeInt(plan.recipeListId());
		output.writeInt(plan.productItemId());
		output.writeLong(plan.requestedOutput());
		output.writeLong(plan.batchCount());
		output.writeLong(plan.productOutput());
		output.writeByte(plan.successRate());
		output.writeBoolean(plan.dwarven());
		output.writeInt(plan.craftSkillId());
		output.writeShort(plan.craftSkillLevel());
		output.writeByte(plan.nodes().size());
		for (RecipeNode node : plan.nodes())
		{
			output.writeInt(node.itemId());
			output.writeLong(node.requestedCount());
			output.writeLong(node.inventoryUsed());
			output.writeLong(node.deficit());
			output.writeInt(node.recipeListId());
			output.writeByte(node.depth());
			output.writeBoolean(node.leaf());
		}
		output.writeByte(plan.deficits().size());
		for (Deficit deficit : plan.deficits())
		{
			output.writeInt(deficit.itemId());
			output.writeLong(deficit.count());
			output.writeBoolean(deficit.manorDeferred());
			output.writeBoolean(deficit.questDeferred());
		}
		writeString(output, plan.reasonKey(), MAX_REASON_BYTES);
	}

	private static RecipePlan readRecipe(DataInputStream input, ByteArrayInputStream bytes) throws IOException
	{
		final int recipeListId = input.readInt();
		final int productItemId = input.readInt();
		final long requestedOutput = input.readLong();
		final long batches = input.readLong();
		final long productOutput = input.readLong();
		final int successRate = input.readUnsignedByte();
		final boolean dwarven = input.readBoolean();
		final int craftSkillId = input.readInt();
		final int craftSkillLevel = input.readUnsignedShort();
		final int nodeCount = input.readUnsignedByte();
		if ((nodeCount == 0) || (nodeCount > PhantomAcquisitionState.MAX_RECIPE_NODES))
		{
			throw new IllegalArgumentException("Invalid acquisition recipe node count.");
		}
		final List<RecipeNode> nodes = new ArrayList<>(nodeCount);
		for (int index = 0; index < nodeCount; index++)
		{
			nodes.add(new RecipeNode(input.readInt(), input.readLong(), input.readLong(), input.readLong(), input.readInt(), input.readUnsignedByte(), input.readBoolean()));
		}
		final int deficitCount = input.readUnsignedByte();
		if (deficitCount > PhantomAcquisitionState.MAX_DEFICITS)
		{
			throw new IllegalArgumentException("Invalid acquisition recipe deficit count.");
		}
		final List<Deficit> deficits = new ArrayList<>(deficitCount);
		for (int index = 0; index < deficitCount; index++)
		{
			deficits.add(new Deficit(input.readInt(), input.readLong(), input.readBoolean(), input.readBoolean()));
		}
		return new RecipePlan(recipeListId, productItemId, requestedOutput, batches, productOutput, successRate, dwarven, craftSkillId, craftSkillLevel, nodes, deficits, readString(input, bytes, MAX_REASON_BYTES));
	}

	private static void writeHash(DataOutputStream output, String hash) throws IOException
	{
		output.write(HexFormat.of().parseHex(hash));
	}

	private static String readHash(DataInputStream input) throws IOException
	{
		final byte[] value = new byte[32];
		input.readFully(value);
		return HexFormat.of().formatHex(value);
	}

	private static void writeString(DataOutputStream output, String value, int maximum) throws IOException
	{
		final byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
		if (encoded.length > maximum)
		{
			throw new IllegalArgumentException("Acquisition state string exceeds its bound.");
		}
		output.writeShort(encoded.length);
		output.write(encoded);
	}

	private static String readString(DataInputStream input, ByteArrayInputStream bytes, int maximum) throws IOException
	{
		final int length = input.readUnsignedShort();
		if ((length > maximum) || (length > bytes.available()))
		{
			throw new IllegalArgumentException("Invalid acquisition state string length.");
		}
		final byte[] encoded = new byte[length];
		input.readFully(encoded);
		return new String(encoded, StandardCharsets.UTF_8);
	}

	private static <E> E enumValue(E[] values, int ordinal, String name)
	{
		if (ordinal >= values.length)
		{
			throw new IllegalArgumentException("Unknown acquisition " + name + ".");
		}
		return values[ordinal];
	}
}
