/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.progression;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.l2jmobius.gameserver.data.enums.CategoryType;
import org.l2jmobius.gameserver.data.xml.CategoryData;
import org.l2jmobius.gameserver.model.actor.enums.creature.Race;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog.CareerArchetype;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;

/** Immutable preference in the existing profile store; all edges come from PlayerClass. */
public record PhantomCareerChoice(int originClass, int terminalClass, boolean female, CareerArchetype archetype)
{
	public static final String COMPONENT = "career.choice";

	public PhantomCareerChoice
	{
		final PlayerClass origin = PlayerClass.getPlayerClass(originClass), terminal = PlayerClass.getPlayerClass(terminalClass);
		if (archetype == null || origin == null || terminal == null || !terminal.getNextClasses().isEmpty() || !descendant(terminal, origin)
			|| !sexCompatible(origin, female) || !sexCompatible(terminal, female) || terminal == PlayerClass.JUDICATOR && origin != PlayerClass.INSPECTOR && origin != PlayerClass.JUDICATOR)
		{
			throw new IllegalArgumentException("Invalid native career branch.");
		}
	}

	public static PhantomCareerChoice ensure(PhantomProfileRepository profiles, long profileId, int currentClass, boolean female, CareerArchetype preference)
	{
		final var component = profiles.findComponent(profileId, COMPONENT).orElse(null);
		if (component != null)
		{
			if (component.componentSchemaVersion() != 1) { throw new IllegalArgumentException("Unknown career choice version."); }
			final var choice = decode(component.payload());
			if (choice.female() != female || !descendant(PlayerClass.getPlayerClass(currentClass), PlayerClass.getPlayerClass(choice.originClass()))
				|| !descendant(PlayerClass.getPlayerClass(choice.terminalClass()), PlayerClass.getPlayerClass(currentClass))) { throw new IllegalArgumentException("Career choice conflicts with current identity."); }
			return choice;
		}
		final var choice = choose(profileId, currentClass, female, preference);
		profiles.insertComponent(profileId, COMPONENT, 1, (choice.originClass() + ":" + choice.terminalClass() + ":" + choice.female() + ":" + choice.archetype()).getBytes(StandardCharsets.US_ASCII));
		return choice;
	}
	public static PhantomCareerChoice decode(byte[] payload)
	{
		if (payload == null || payload.length > 128) { throw new IllegalArgumentException("Invalid career choice size."); }
		final String[] fields = new String(payload, StandardCharsets.US_ASCII).split(":", -1);
		if (fields.length != 4 || !List.of("true", "false").contains(fields[2])) { throw new IllegalArgumentException("Invalid career choice payload."); }
		return new PhantomCareerChoice(Integer.parseInt(fields[0]), Integer.parseInt(fields[1]), Boolean.parseBoolean(fields[2]), CareerArchetype.valueOf(fields[3]));
	}

	public static PhantomCareerChoice choose(long seed, int currentClass, boolean female, CareerArchetype preference)
	{
		final PlayerClass current = PlayerClass.getPlayerClass(currentClass);
		if (current == null || preference == null || !sexCompatible(current, female)) { throw new IllegalArgumentException("Career identity is incompatible."); }
		final var legal = Arrays.stream(PlayerClass.values()).filter(value -> value.getNextClasses().isEmpty() && descendant(value, current) && sexCompatible(value, female))
			.filter(value -> value != PlayerClass.JUDICATOR || current == PlayerClass.INSPECTOR || current == PlayerClass.JUDICATOR).sorted(Comparator.comparingInt(PlayerClass::getId)).toList();
		final var preferred = legal.stream().filter(value -> matches(value, preference)).toList();
		// An already specialized legacy actor keeps its legal branch even when its old preference cannot be fulfilled.
		final var candidates = preferred.isEmpty() ? legal : preferred;
		if (candidates.isEmpty()) { throw new IllegalArgumentException("No legal terminal profession."); }
		long hash = seed ^ (0x9e3779b97f4a7c15L * (currentClass + 1L)) ^ (preference.ordinal() * 0x632be59bd9b4e019L);
		hash = (hash ^ (hash >>> 30)) * 0xbf58476d1ce4e5b9L;
		hash = (hash ^ (hash >>> 27)) * 0x94d049bb133111ebL;
		return new PhantomCareerChoice(currentClass, candidates.get((int) Math.floorMod(hash ^ (hash >>> 31), candidates.size())).getId(), female, preference);
	}

	public int nextClass(int currentClass)
	{
		final PlayerClass current = PlayerClass.getPlayerClass(currentClass), terminal = PlayerClass.getPlayerClass(terminalClass);
		if (current == null || !descendant(terminal, current)) { return -1; }
		return current.getNextClasses().stream().filter(value -> descendant(terminal, value)).mapToInt(PlayerClass::getId).findFirst().orElse(-1);
	}

	public static int minimumLevel(PlayerClass target)
	{
		return switch (target.level()) { case 1 -> 20; case 2 -> 40; case 3 -> 76; default -> Integer.MAX_VALUE; };
	}

	public static boolean legalEdge(int fromId, int targetId, boolean female, int classIndex)
	{
		final PlayerClass from = PlayerClass.getPlayerClass(fromId), target = PlayerClass.getPlayerClass(targetId);
		return classIndex == 0 && from != null && target != null && from.getNextClasses().contains(target) && target.getParent() == from
			&& from.getRace() == target.getRace() && sexCompatible(from, female) && sexCompatible(target, female) && target != PlayerClass.INSPECTOR;
	}

	private static boolean descendant(PlayerClass child, PlayerClass parent) { return child != null && parent != null && (child == parent || child.childOf(parent)); }
	private static boolean sexCompatible(PlayerClass value, boolean female)
	{
		return value.getRace() != Race.KAMAEL || value.getRootClass() == (female ? PlayerClass.FEMALE_SOLDIER : PlayerClass.MALE_SOLDIER);
	}
	private static boolean category(PlayerClass value, CategoryType category) { return CategoryData.getInstance().isInCategory(category, value.getId()); }
	private static boolean matches(PlayerClass value, CareerArchetype preference)
	{
		return switch (preference)
		{
			case TANK -> category(value, CategoryType.TANKER_CATEGORY);
			case HEALER -> category(value, CategoryType.HEALER_CATEGORY);
			case ENHANCEMENT -> category(value, CategoryType.DIVISION_ENCHANTER) || category(value, CategoryType.HEALER_CATEGORY);
			case CONTROL -> category(value, CategoryType.WIZARD_CATEGORY) || category(value, CategoryType.DIVISION_ENCHANTER);
			case ECONOMY -> value.getRace() == Race.DWARF;
			case DAMAGE -> category(value, CategoryType.WARRIOR_CATEGORY) || category(value, CategoryType.ROGUE_CATEGORY) || category(value, CategoryType.ARCHER_CATEGORY) || category(value, CategoryType.WIZARD_CATEGORY) || category(value, CategoryType.SUMMONER_CATEGORY);
		};
	}
}
