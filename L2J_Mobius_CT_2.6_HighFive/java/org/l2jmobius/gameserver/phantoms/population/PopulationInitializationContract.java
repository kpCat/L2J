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
package org.l2jmobius.gameserver.phantoms.population;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.l2jmobius.gameserver.data.xml.InitialShortcutData.InitialPlan;
import org.l2jmobius.gameserver.data.xml.InitialShortcutData.MacroCommandPlan;
import org.l2jmobius.gameserver.data.xml.InitialShortcutData.MacroPlan;
import org.l2jmobius.gameserver.data.xml.InitialShortcutData.ShortcutPlan;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.actor.PlayerCreationInitializer;
import org.l2jmobius.gameserver.model.actor.PlayerCreationInitializer.CreationPlan;
import org.l2jmobius.gameserver.model.actor.PlayerCreationInitializer.InitialItemPlan;
import org.l2jmobius.gameserver.model.actor.PlayerCreationInitializer.InitialSkillPlan;
import org.l2jmobius.gameserver.model.actor.PlayerCreationInitializer.Mode;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.model.itemcontainer.Inventory;

/**
 * Immutable logical authority fixed before the first population initialization
 * writer. Item shortcuts retain both the owned template identity and their
 * eventual exact object-ID binding rule.
 */
public record PopulationInitializationContract(
	String versionToken,
	String catalogHash,
	String zoneId,
	String initializerVersion,
	int classId,
	int configuredStartingLevel,
	long configuredStartingSp,
	int level,
	long sp,
	long adena,
	String title,
	boolean vitalityEnabled,
	int vitalityPoints,
	int creationX,
	int creationY,
	int creationZ,
	String hpAuthority,
	String mpAuthority,
	double cp,
	List<ItemFact> items,
	List<SkillFact> skills,
	InitialPlan initialPlan)
{
	public static final String VERSION_TOKEN = "POPULATION_CREATION_AUTHORITY_V1";
	private static final String FULL_MAXIMUM = "FULL_CURRENT_MAXIMUM";

	public PopulationInitializationContract
	{
		Objects.requireNonNull(versionToken, "Population authority version must not be null.");
		Objects.requireNonNull(catalogHash, "Population catalog hash must not be null.");
		Objects.requireNonNull(zoneId, "Population zone ID must not be null.");
		Objects.requireNonNull(initializerVersion, "Shared initializer version must not be null.");
		Objects.requireNonNull(title, "Starting title must not be null.");
		Objects.requireNonNull(hpAuthority, "HP authority must not be null.");
		Objects.requireNonNull(mpAuthority, "MP authority must not be null.");
		items = List.copyOf(items);
		skills = List.copyOf(skills);
		Objects.requireNonNull(initialPlan, "Initial shortcut plan must not be null.");
	}

	public static PopulationInitializationContract resolve(String catalogHash, ZoneId zoneId, int classId, Location creationLocation)
	{
		Objects.requireNonNull(catalogHash, "Population catalog hash must not be null.");
		Objects.requireNonNull(zoneId, "Population zone ID must not be null.");
		Objects.requireNonNull(creationLocation, "Population creation location must not be null.");
		final PlayerClass playerClass = PlayerClass.getPlayerClass(classId);
		if (playerClass == null)
		{
			throw new IllegalArgumentException("Population initialization class is unknown.");
		}
		final CreationPlan plan = PlayerCreationInitializer.resolvePlan(playerClass, Mode.POPULATION, creationLocation);
		final Map<ItemKey, Long> itemCounts = new LinkedHashMap<>();
		for (InitialItemPlan item : plan.items())
		{
			itemCounts.merge(new ItemKey(item.itemId(), item.equipped()), item.count(), Math::addExact);
		}
		if (plan.adena() > 0)
		{
			itemCounts.merge(new ItemKey(Inventory.ADENA_ID, false), plan.adena(), Math::addExact);
		}
		final List<ItemFact> items = itemCounts.entrySet().stream()
			.map(entry -> new ItemFact(entry.getKey().itemId(), entry.getValue(), entry.getKey().equipped()))
			.sorted(Comparator.comparingInt(ItemFact::itemId).thenComparing(ItemFact::equipped))
			.toList();
		final List<SkillFact> skills = plan.skills().stream()
			.map(PopulationInitializationContract::skillFact)
			.sorted(Comparator.comparingInt(SkillFact::skillId).thenComparingInt(SkillFact::skillLevel))
			.toList();
		return new PopulationInitializationContract(
			VERSION_TOKEN,
			catalogHash,
			zoneId.getId(),
			PlayerCreationInitializer.VERSION,
			classId,
			plan.configuredStartingLevel(),
			plan.configuredStartingSp(),
			plan.level(),
			plan.sp(),
			plan.adena(),
			plan.title(),
			plan.vitalityEnabled(),
			plan.vitalityPoints(),
			creationLocation.getX(),
			creationLocation.getY(),
			creationLocation.getZ(),
			FULL_MAXIMUM,
			FULL_MAXIMUM,
			0,
			items,
			skills,
			plan.shortcuts());
	}

	public String hash()
	{
		try
		{
			final ByteArrayOutputStream bytes = new ByteArrayOutputStream(4096);
			try (DataOutputStream output = new DataOutputStream(bytes))
			{
				writeText(output, versionToken);
				writeText(output, catalogHash);
				writeText(output, zoneId);
				writeText(output, initializerVersion);
				output.writeInt(classId);
				output.writeInt(configuredStartingLevel);
				output.writeLong(configuredStartingSp);
				output.writeInt(level);
				output.writeLong(sp);
				output.writeLong(adena);
				writeText(output, title);
				output.writeBoolean(vitalityEnabled);
				output.writeInt(vitalityPoints);
				output.writeInt(creationX);
				output.writeInt(creationY);
				output.writeInt(creationZ);
				writeText(output, hpAuthority);
				writeText(output, mpAuthority);
				output.writeLong(Double.doubleToLongBits(cp));
				output.writeInt(items.size());
				for (ItemFact item : items)
				{
					output.writeInt(item.itemId());
					output.writeLong(item.count());
					output.writeBoolean(item.equipped());
				}
				output.writeInt(skills.size());
				for (SkillFact skill : skills)
				{
					output.writeInt(skill.skillId());
					output.writeInt(skill.skillLevel());
				}
				output.writeInt(initialPlan.shortcuts().size());
				for (ShortcutPlan shortcut : initialPlan.shortcuts())
				{
					output.writeInt(shortcut.page());
					output.writeInt(shortcut.slot());
					output.writeInt(shortcut.type().ordinal());
					output.writeInt(shortcut.logicalId());
					output.writeInt(shortcut.level());
					output.writeInt(shortcut.characterType());
				}
				output.writeInt(initialPlan.macros().size());
				for (MacroPlan macro : initialPlan.macros())
				{
					output.writeInt(macro.id());
					output.writeInt(macro.icon());
					writeText(output, macro.name());
					writeText(output, macro.description());
					writeText(output, macro.acronym());
					output.writeInt(macro.commands().size());
					for (MacroCommandPlan command : macro.commands())
					{
						output.writeInt(command.entry());
						output.writeInt(command.type().ordinal());
						output.writeInt(command.parameterOne());
						output.writeInt(command.parameterTwo());
						writeText(output, command.command());
					}
				}
			}
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
		}
		catch (Exception e)
		{
			throw new IllegalStateException("Could not hash population initialization authority.", e);
		}
	}

	public Map<Integer, Long> itemCountsByTemplate()
	{
		final Map<Integer, Long> counts = new LinkedHashMap<>();
		items.forEach(item -> counts.merge(item.itemId(), item.count(), Math::addExact));
		return Map.copyOf(counts);
	}

	public CreationPlan creationPlan()
	{
		final List<InitialItemPlan> equipment = new ArrayList<>();
		for (ItemFact item : items)
		{
			if (item.itemId() != Inventory.ADENA_ID)
			{
				equipment.add(new InitialItemPlan(item.itemId(), item.count(), item.equipped()));
			}
		}
		final List<InitialSkillPlan> skillPlans = skills.stream().map(skill -> new InitialSkillPlan(skill.skillId(), skill.skillLevel())).toList();
		return new CreationPlan(Mode.POPULATION, level, sp, adena, new Location(creationX, creationY, creationZ), title, vitalityEnabled, vitalityPoints, configuredStartingLevel, configuredStartingSp, equipment, skillPlans, initialPlan);
	}

	private static SkillFact skillFact(InitialSkillPlan skill)
	{
		return new SkillFact(skill.skillId(), skill.skillLevel());
	}

	private static void writeText(DataOutputStream output, String value) throws Exception
	{
		final byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
		output.writeInt(encoded.length);
		output.write(encoded);
	}

	public record ItemFact(int itemId, long count, boolean equipped)
	{
	}

	public record SkillFact(int skillId, int skillLevel)
	{
	}

	private record ItemKey(int itemId, boolean equipped)
	{
	}
}
