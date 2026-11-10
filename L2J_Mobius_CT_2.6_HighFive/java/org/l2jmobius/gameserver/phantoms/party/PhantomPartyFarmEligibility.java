/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.party;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.actor.Creature.NativeMagicUseObservation;
import org.l2jmobius.gameserver.model.actor.tasks.creature.MagicUseTask.Disposition;
import org.l2jmobius.gameserver.model.actor.tasks.creature.MagicUseTask.TargetStatus;

/** Pure FARM admission facts; never a native action, Solo policy or combat simulator. */
public final class PhantomPartyFarmEligibility
{
	private PhantomPartyFarmEligibility() { }
	public record NpcLevelBand(int minimum, int maximum)
	{
		public NpcLevelBand { if (minimum < 1 || maximum < minimum) { throw new IllegalArgumentException("Invalid Party NPC band."); } }
		public boolean contains(int npcLevel) { return npcLevel >= minimum && npcLevel <= maximum; }
	}
	public static NpcLevelBand forMember(int level)
	{
		if (level < 1 || level > 85) { throw new IllegalArgumentException("Invalid Party player level."); }
		return new NpcLevelBand(Math.max(1, level - 4), level + 4);
	}
	public static Optional<NpcLevelBand> forRoster(List<Integer> levels)
	{
		if (levels == null || levels.size() < 2 || levels.size() > 9 || levels.stream().anyMatch(level -> level == null || level < 1 || level > 85)) { return Optional.empty(); }
		final int minimum = Math.max(1, levels.stream().mapToInt(Integer::intValue).max().orElseThrow() - 4);
		final int maximum = levels.stream().mapToInt(Integer::intValue).min().orElseThrow() + 4;
		return minimum <= maximum ? Optional.of(new NpcLevelBand(minimum, maximum)) : Optional.empty();
	}
	/** Learned durable evidence means potential only, never readyNow or guaranteed protection. */
	public static String potentialRole(Set<String> learned, boolean weapon, boolean ranged)
	{
		if (weapon && learned.contains("combat.tank")) { return "frontline.guardian"; }
		if (learned.contains("combat.heal")) { return "support.healer"; }
		if (learned.contains("profession.spoil") && learned.contains("profession.sweep")) { return "utility.spoiler"; }
		if (learned.contains("combat.recharge")) { return "support.recharge"; }
		if (learned.stream().anyMatch(Set.of("combat.buff", "combat.song", "combat.dance")::contains)) { return "support.enhancement"; }
		if (weapon || learned.stream().anyMatch(key -> key.equals("combat.melee_damage") || key.startsWith("combat.ranged_"))) { return ranged ? "damage.ranged" : "damage.melee"; }
		return "pending.native_role";
	}
	/** Compatibility is explicit and read-only; legacy durable strings are never rewritten. */
	public static boolean compatibleRole(String stored, String potential)
	{
		return stored.equals(potential) || stored.equals("support") && potential.startsWith("support.")
			|| stored.equals("damage") && (potential.startsWith("damage.") || potential.equals("frontline.guardian") || potential.equals("utility.spoiler"));
	}
	/** Observed main victim plus the approved bounded aggro-shift reserve, in native time units. */
	public static double threatExposureMillis(double encounterMillis, boolean mainVictim)
	{ return threatExposureMillis(encounterMillis, mainVictim, 0); }

	/** An independently supported victim must survive through the verified cast and a reaction reserve. */
	public static double threatExposureMillis(double encounterMillis, boolean mainVictim, long supportCastMillis)
	{
		if (!Double.isFinite(encounterMillis) || encounterMillis <= 0) { return Double.NaN; }
		final int reserve = 6000;
		if (!mainVictim) { return Math.min(encounterMillis, reserve); }
		return supportCastMillis > 0 ? Math.min(encounterMillis, supportCastMillis + (double) reserve) : encounterMillis;
	}

	public record CastVictims(Set<Integer> recipients, boolean hostile, String refusal)
	{
		public CastVictims { recipients = Set.copyOf(recipients); }
	}

	/** Both current native slots contribute the actual Party recipients of pending hostile casts. */
	public static CastVictims observedCastVictims(NativeMagicUseObservation observed, List<? extends WorldObject> party)
	{
		final var recipients = new LinkedHashSet<Integer>();
		boolean hostile = false;
		String refusal = "";
		for (int slot = 0; slot < 2; slot++)
		{
			if (!(slot == 0 ? observed.casting() : observed.simultaneous())) { continue; }
			final var cast = slot == 0 ? observed.primary() : observed.secondary();
			if (cast == null)
			{
				refusal = "native.cast.task_unavailable";
				continue;
			}
			if (cast.status() == TargetStatus.FINISHED || cast.disposition() == Disposition.HARMLESS) { continue; }
			if (cast.disposition() == Disposition.UNKNOWN) { refusal = "native.cast.disposition_unknown"; continue; }
			hostile = true;
			if (cast.status() != TargetStatus.READY)
			{
				refusal = "native.cast." + cast.status().name().toLowerCase(Locale.ROOT);
				continue;
			}
			for (var member : party)
			{
				if (cast.targets().stream().noneMatch(target -> target == member)) { continue; }
				recipients.add(member.getObjectId());
				if (!cast.nativeLifetimeOwned()) { refusal = "native.cast.recipient_lifetime_unproven"; }
			}
		}
		if (!observed.isCurrent() && refusal.isEmpty()) { refusal = "native.cast.changed"; }
		return new CastVictims(recipients, hostile || !refusal.isEmpty(), refusal);
	}
	/** Source/level admission precedes the expensive native Geo/combat candidate budget. */
	public static <T> List<T> farmTargets(java.util.stream.Stream<T> ordered, java.util.function.Predicate<T> lawful)
	{ return ordered.filter(lawful).distinct().limit(8).toList(); }
	public static boolean withinThreatBudget(double incoming, double currentHp)
	{ return Double.isFinite(incoming) && incoming < currentHp * 0.6; }
	/** Every member's local threats, deduplicated before the four-threat overflow witness. */
	public static <A, T> List<T> farmThreats(List<A> actors, java.util.function.Function<A, java.util.stream.Stream<T>> nearby, java.util.function.Predicate<T> engaged)
	{ return actors.stream().flatMap(nearby).filter(engaged).distinct().limit(5).toList(); }
	/** Whole native casts/shots needed for the remaining encounter; unknown input cannot fit a budget. */
	public static double requiredActions(double encounterMillis, double intervalMillis)
	{ return Double.isFinite(encounterMillis) && encounterMillis > 0 && Double.isFinite(intervalMillis) && intervalMillis > 0 ? Math.ceil(encounterMillis / intervalMillis) : Double.POSITIVE_INFINITY; }
}
