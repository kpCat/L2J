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
package org.l2jmobius.gameserver.phantoms.background;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Clock;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.CombatFacts;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.AutoGetSkill;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Hashes;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Identity;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.InventoryFacts;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.ItemObject;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.ItemLocation;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Loadout;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.ModelKind;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Position;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Progress;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Receipt;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.State;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Vitals;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;

/**
 * Canonical binary codec for {@code background.state} schema version 2, with a
 * read-only schema-1 upgrade path.
 */
public final class PhantomBackgroundStateCodec
{
	private static final int MAGIC = 0x50424731;
	private static final int FORMAT_VERSION = 2;
	private static final int LEGACY_FORMAT_VERSION = 1;
	private static final int LEGACY_SCHEMA_VERSION = 1;

	public byte[] encode(PhantomBackgroundState state)
	{
		try
		{
			final ByteArrayOutputStream bytes = new ByteArrayOutputStream(1024);
			try (DataOutputStream output = new DataOutputStream(bytes))
			{
				output.writeInt(MAGIC);
				output.writeShort(FORMAT_VERSION);
				output.writeShort(PhantomBackgroundState.SCHEMA_VERSION);
				output.writeByte(state.state().ordinal());
				final Identity identity = state.identity();
				output.writeLong(identity.profileId());
				output.writeInt(identity.characterObjectId());
				output.writeByte(identity.classIndex());
				output.writeShort(identity.activeClassId());
				output.writeByte(identity.raceOrdinal());
				final Progress progress = state.progress();
				output.writeByte(progress.level());
				output.writeLong(progress.experience());
				output.writeLong(progress.skillPoints());
				output.writeLong(progress.experienceBeforeDeath());
				final Vitals vitals = state.vitals();
				writeVitals(output, vitals);
				final Position position = state.position();
				output.writeInt(position.instanceId());
				output.writeInt(position.x());
				output.writeInt(position.y());
				output.writeInt(position.z());
				output.writeInt(position.heading());
				writeString(output, position.committedAnchorId());
				final CombatFacts combat = state.combat();
				output.writeByte(combat.modelKind().ordinal());
				output.writeDouble(combat.physicalOffense());
				output.writeDouble(combat.magicOffense());
				output.writeDouble(combat.physicalDefense());
				output.writeDouble(combat.magicDefense());
				output.writeDouble(combat.attackSpeed());
				output.writeDouble(combat.castSpeed());
				output.writeDouble(combat.hpRegenPerSecond());
				output.writeDouble(combat.mpRegenPerSecond());
				output.writeDouble(combat.experienceMultiplier());
				output.writeDouble(combat.skillPointMultiplier());
				output.writeDouble(combat.servitorExperienceMultiplier());
				output.writeDouble(combat.dropChanceMultiplier());
				output.writeDouble(combat.dropAmountMultiplier());
				output.writeDouble(combat.adenaAmountMultiplier());
				output.writeDouble(combat.normalMonsterExperienceLossMultiplier());
				final Loadout loadout = state.loadout();
				output.writeInt(loadout.selectedSkillId());
				output.writeShort(loadout.selectedSkillLevel());
				output.writeInt(loadout.summonNpcId());
				output.writeInt(loadout.skillMpPerEncounter());
				output.writeInt(loadout.shotItemId());
				output.writeInt(loadout.shotsPerEncounter());
				output.writeInt(loadout.summonResourceItemId());
				output.writeInt(loadout.summonResourcesPerEncounter());
				final InventoryFacts inventory = state.inventory();
				output.writeByte(inventory.mutableItemIds().size());
				for (int itemId : inventory.mutableItemIds())
				{
					output.writeInt(itemId);
				}
				output.writeShort(inventory.objects().size());
				for (ItemObject object : inventory.objects())
				{
					output.writeInt(object.objectId());
					output.writeInt(object.itemId());
					output.writeLong(object.count());
					output.writeBoolean(object.stackable());
					output.writeByte(object.location().ordinal());
				}
				writeString(output, inventory.canonicalHash());
				output.writeLong(inventory.currentLoad());
				output.writeLong(inventory.maximumLoad());
				output.writeShort(inventory.usedSlots());
				output.writeShort(inventory.maximumSlots());
				output.writeByte(state.autoGetSkills().size());
				for (AutoGetSkill skill : state.autoGetSkills())
				{
					output.writeInt(skill.skillId());
					output.writeShort(skill.skillLevel());
				}
				final Clock clock = state.clock();
				output.writeLong(clock.rngState());
				output.writeLong(clock.residualTravelMillis());
				output.writeLong(clock.residualEncounterMillis());
				final Receipt receipt = state.receipt();
				writeString(output, receipt.operationKey());
				output.writeLong(receipt.activityGeneration());
				output.writeLong(receipt.tickSequence());
				writeString(output, receipt.expectedAfterHash());
				final Hashes hashes = state.hashes();
				writeString(output, hashes.knowledge());
				writeString(output, hashes.topology());
				writeString(output, hashes.progression());
				writeString(output, hashes.commerce());
			}
			final byte[] payload = bytes.toByteArray();
			if (payload.length > PhantomProfileComponent.MAX_PAYLOAD_BYTES)
			{
				throw new IllegalArgumentException("Encoded background.state payload exceeds 4096 bytes.");
			}
			return payload;
		}
		catch (IOException exception)
		{
			throw new IllegalStateException("Unexpected in-memory background state encoding failure.", exception);
		}
	}

	public PhantomBackgroundState decode(byte[] payload)
	{
		if ((payload == null) || (payload.length > PhantomProfileComponent.MAX_PAYLOAD_BYTES))
		{
			throw new IllegalArgumentException("Invalid background.state payload size.");
		}
		try
		{
			final ByteArrayInputStream bytes = new ByteArrayInputStream(payload);
			try (DataInputStream input = new DataInputStream(bytes))
			{
				if (input.readInt() != MAGIC)
				{
					throw new IllegalArgumentException("Unknown background.state format.");
				}
				final int formatVersion = input.readUnsignedShort();
				final int schemaVersion = input.readUnsignedShort();
				final boolean legacy = (formatVersion == LEGACY_FORMAT_VERSION) && (schemaVersion == LEGACY_SCHEMA_VERSION);
				if (!legacy && ((formatVersion != FORMAT_VERSION) || (schemaVersion != PhantomBackgroundState.SCHEMA_VERSION)))
				{
					throw new IllegalArgumentException("Unknown background.state version.");
				}
				final State state = enumValue(State.values(), input.readUnsignedByte(), "state");
				final Identity identity = new Identity(input.readLong(), input.readInt(), input.readUnsignedByte(), input.readUnsignedShort(), input.readUnsignedByte());
				final Progress progress = new Progress(input.readUnsignedByte(), input.readLong(), input.readLong(), input.readLong());
				final Vitals vitals = readVitals(input);
				final Position position = new Position(input.readInt(), input.readInt(), input.readInt(), input.readInt(), input.readInt(), readString(input, bytes));
				final ModelKind modelKind = enumValue(ModelKind.values(), input.readUnsignedByte(), "model kind");
				final CombatFacts combat = new CombatFacts(modelKind, input.readDouble(), input.readDouble(), input.readDouble(), input.readDouble(), input.readDouble(), input.readDouble(), input.readDouble(), input.readDouble(), input.readDouble(), input.readDouble(), input.readDouble(), input.readDouble(), input.readDouble(), input.readDouble(), input.readDouble());
				final Loadout loadout = new Loadout(input.readInt(), input.readUnsignedShort(), input.readInt(), input.readInt(), input.readInt(), input.readInt(), input.readInt(), input.readInt());
				final List<Integer> mutableItemIds = new ArrayList<>();
				final int itemCount;
				if (legacy)
				{
					itemCount = input.readUnsignedByte();
				}
				else
				{
					final int mutableItemCount = input.readUnsignedByte();
					if (mutableItemCount > PhantomBackgroundState.MAX_MUTABLE_ITEM_IDS)
					{
						throw new IllegalArgumentException("Too many mutable background item IDs.");
					}
					for (int index = 0; index < mutableItemCount; index++)
					{
						mutableItemIds.add(input.readInt());
					}
					itemCount = input.readUnsignedShort();
				}
				if (itemCount > PhantomBackgroundState.MAX_TRACKED_ITEMS)
				{
					throw new IllegalArgumentException("Too many tracked background items.");
				}
				final List<ItemObject> objects = new ArrayList<>(itemCount);
				for (int index = 0; index < itemCount; index++)
				{
					objects.add(new ItemObject(input.readInt(), input.readInt(), input.readLong(), input.readBoolean(), enumValue(ItemLocation.values(), input.readUnsignedByte(), "item location")));
				}
				final String inventoryHash;
				if (legacy)
				{
					mutableItemIds.addAll(objects.stream().filter(object -> object.location() == ItemLocation.INVENTORY).map(ItemObject::itemId).distinct().sorted().toList());
					inventoryHash = PhantomBackgroundInventoryHash.compute(objects.stream().map(object -> new PhantomBackgroundInventoryHash.CanonicalItem(object.objectId(), object.itemId(), object.count(), object.location())).toList());
				}
				else
				{
					inventoryHash = readString(input, bytes);
				}
				final InventoryFacts inventory = new InventoryFacts(mutableItemIds, objects, inventoryHash, input.readLong(), input.readLong(), input.readUnsignedShort(), input.readUnsignedShort());
				final int autoSkillCount = input.readUnsignedByte();
				if (autoSkillCount > 64)
				{
					throw new IllegalArgumentException("Too many background auto-get skills.");
				}
				final List<AutoGetSkill> autoGetSkills = new ArrayList<>(autoSkillCount);
				for (int index = 0; index < autoSkillCount; index++)
				{
					autoGetSkills.add(new AutoGetSkill(input.readInt(), input.readUnsignedShort()));
				}
				final Clock clock = new Clock(input.readLong(), input.readLong(), input.readLong());
				final Receipt receipt = new Receipt(readString(input, bytes), input.readLong(), input.readLong(), readString(input, bytes));
				final Hashes hashes = new Hashes(readString(input, bytes), readString(input, bytes), readString(input, bytes), readString(input, bytes));
				if (bytes.available() != 0)
				{
					throw new IllegalArgumentException("Trailing bytes after background.state payload.");
				}
				final PhantomBackgroundState result = new PhantomBackgroundState(state, identity, progress, vitals, position, combat, loadout, inventory, autoGetSkills, clock, receipt, hashes);
				if (!legacy && !Arrays.equals(payload, encode(result)))
				{
					throw new IllegalArgumentException("Non-canonical background.state payload.");
				}
				return result;
			}
		}
		catch (EOFException exception)
		{
			throw new IllegalArgumentException("Truncated background.state payload.", exception);
		}
		catch (IOException exception)
		{
			throw new IllegalArgumentException("Invalid background.state payload.", exception);
		}
	}

	private static void writeVitals(DataOutputStream output, Vitals vitals) throws IOException
	{
		output.writeDouble(vitals.currentHp());
		output.writeDouble(vitals.maximumHp());
		output.writeDouble(vitals.currentMp());
		output.writeDouble(vitals.maximumMp());
		output.writeDouble(vitals.currentCp());
		output.writeDouble(vitals.maximumCp());
	}

	private static Vitals readVitals(DataInputStream input) throws IOException
	{
		return new Vitals(input.readDouble(), input.readDouble(), input.readDouble(), input.readDouble(), input.readDouble(), input.readDouble());
	}

	private static void writeString(DataOutputStream output, String value) throws IOException
	{
		final byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
		if (encoded.length > 255)
		{
			throw new IllegalArgumentException("Background state string exceeds 255 bytes.");
		}
		output.writeByte(encoded.length);
		output.write(encoded);
	}

	private static String readString(DataInputStream input, ByteArrayInputStream bytes) throws IOException
	{
		final int length = input.readUnsignedByte();
		if (length > bytes.available())
		{
			throw new IllegalArgumentException("Invalid background state string length.");
		}
		final byte[] encoded = new byte[length];
		input.readFully(encoded);
		return new String(encoded, StandardCharsets.UTF_8);
	}

	private static <E> E enumValue(E[] values, int ordinal, String name)
	{
		if (ordinal >= values.length)
		{
			throw new IllegalArgumentException("Unknown background " + name + ".");
		}
		return values[ordinal];
	}
}
