/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence.DamageReceipt;
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence.Target;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.skill.EffectScope;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.skill.targets.TargetType;
import org.l2jmobius.gameserver.model.stats.Formulas;
import org.l2jmobius.gameserver.phantoms.combat.PhantomTacticalCombat.Action;
import org.l2jmobius.gameserver.phantoms.combat.PhantomTacticalCombat.Effect;
import org.l2jmobius.gameserver.phantoms.combat.PhantomTacticalCombat.Kind;
import org.l2jmobius.gameserver.phantoms.combat.PhantomTacticalCombat.Scene;
import org.l2jmobius.gameserver.phantoms.combat.PhantomTacticalCombat.Source;

/** Read-only bridge from an admitted native actor to the shared individual planner. */
public final class PhantomTacticalDecision
{
	private static final Logger LOGGER = Logger.getLogger(PhantomTacticalDecision.class.getName());
	private static final long PLAN_NANOS = 2_000_000L;
	private static final AtomicLong NEXT_TRACE = new AtomicLong();
	private static final Calibration CALIBRATION = new Calibration(256);

	/** Exact actor, profile lifetime, class and NPC spawn. Skill identity is unknown for this aggregate receipt. */
	public record Encounter(int actor, long epoch, int classId, int classIndex, Target target) { }
	public static final class Calibration
	{
		private record Sample(Encounter encounter, long sequence, long time, double ratio, int samples) { }
		private final LinkedHashMap<Integer, Sample> _samples = new LinkedHashMap<>(32, 0.75f, true);
		private final int _limit;

		public Calibration(int limit) { _limit = Math.clamp(limit, 1, 256); }

		public synchronized double estimate(Encounter encounter, DamageReceipt receipt, double prior, long now)
		{
			final var last = _samples.get(encounter.actor());
			final boolean same = last != null && last.encounter().equals(encounter) && now - last.time() <= 8_000_000_000L;
			if (!same && last != null) { _samples.remove(encounter.actor()); }
			if (receipt == null || receipt.actorObjectId() != encounter.actor() || receipt.epoch() != encounter.epoch()
				|| !encounter.target().equals(receipt.target()) || receipt.sequence() <= 0 || receipt.observedNanos() > now
				|| now - receipt.observedNanos() > 3_000_000_000L || receipt.hp() <= 0 || !Double.isFinite(receipt.hp())
				|| same && receipt.sequence() <= last.sequence())
			{
				return same ? corrected(prior, last) : prior;
			}
			final double interval = same ? (receipt.observedNanos() - last.time()) / 1_000_000_000.0 : 0;
			final boolean contiguous = same && receipt.sequence() == last.sequence() + 1 && interval >= 0.2 && interval <= 6;
			final double ratio = contiguous ? Math.clamp(receipt.hp() / interval / Math.max(1, prior), 0.5, 2) : 1;
			final var sample = new Sample(encounter, receipt.sequence(), receipt.observedNanos(),
				contiguous ? last.ratio() * 0.75 + ratio * 0.25 : 1, contiguous ? Math.min(16, last.samples() + 1) : 0);
			_samples.put(encounter.actor(), sample);
			if (_samples.size() > _limit) { _samples.pollFirstEntry(); }
			return corrected(prior, sample);
		}

		private static double corrected(double prior, Sample sample)
		{
			return prior * (1 + (sample.ratio() - 1) * sample.samples() / (sample.samples() + 3));
		}
	}

	public record Choice(Skill skill, int classId, int classIndex, PlayerNativeWork.Owner owner,
		int instance, int targetObjectId, long spawnGeneration, PhantomTacticalCombat.Decision decision, long elapsedNanos)
	{
		public boolean attack() { return skill == null; }
		public boolean current(Player player, Creature target)
		{
			return owner != null && owner.player() == player && owner.isCurrent() && !owner.sealed()
				&& player.getNativeWorkOwner() == owner && player.getActiveClass() == classId && player.getClassIndex() == classIndex
				&& !player.isAlikeDead() && !player.isTeleporting() && !player.hasPendingOwnedStore()
				&& instance == player.getInstanceId() && instance == target.getInstanceId()
				&& targetObjectId == target.getObjectId() && World.getInstance().findObject(targetObjectId) == target
				&& !target.isAlikeDead() && target.isTargetable() && !target.isInvul() && spawnGeneration == generation(target)
				&& (skill == null || player.getKnownSkill(skill.getId()) == skill);
		}
	}

	private PhantomTacticalDecision() { }

	public static Choice choose(Player player, Creature target, boolean pvp, boolean normalAttack, Predicate<Skill> admitted)
	{
		final var owner = player.getNativeWorkOwner();
		if (owner == null || !owner.isCurrent() || owner.sealed() || owner.player() != player || player.isInParty()
			|| player.isAllSkillsDisabled() || player.isTransformed() || player.isAlikeDead() || target.isAlikeDead()
			|| player.getInstanceId() != target.getInstanceId() || player.getSkills().size() > PhantomNativeCombatCapability.MAXIMUM_SKILLS)
		{
			return null;
		}
		final long started = System.nanoTime();
		final long deadline = started + PLAN_NANOS;
		final int classId = player.getActiveClass(), classIndex = player.getClassIndex();
		final long spawn = generation(target);
		final var geo = GeoEngine.getInstance();
		final boolean visible = geo.hasGeo(player.getX(), player.getY()) && geo.hasGeo(target.getX(), target.getY()) && geo.canSeeTarget(player, target);
		if (!visible) { return null; }
		final double distance = player.calculateDistance3D(target);
		final double pDef = pvp ? Math.max(20, player.getPDef(null)) : Math.max(1, target.getPDef(player));
		final double mDef = pvp ? Math.max(20, player.getMDef(null, null)) : Math.max(1, target.getMDef(player, null));
		final double patk = player.getPAtk(target);
		final double hit = 70 * patk / pDef;
		final double normalSeconds = Math.clamp(500 / Math.max(1, player.getPAtkSpd()), 0.3, 2.5);
		final var context = scene(player, target, owner, pvp, hit / normalSeconds);
		final var actions = new ArrayList<Action>();
		if (normalAttack && !player.isPhysicalMuted() && distance <= player.getPhysicalAttackRange() + player.getCollisionRadius() + target.getTemplate().getCollisionRadius())
		{
			actions.add(new Action(0, 0, true, true, normalSeconds, normalSeconds, 0, 0, 0, 0, 0, false,
				List.of(new Effect(Kind.PHYSICAL, hit, 0, 0.85))));
		}
		final var skills = new ArrayList<>(player.getAllSkills());
		if (skills.size() > PhantomNativeCombatCapability.MAXIMUM_SKILLS) { return null; }
		skills.sort(Comparator.comparingInt(Skill::getId));
		for (Skill skill : skills)
		{
			if (System.nanoTime() - deadline >= 0) { return null; }
			final var facts = PhantomSkillInventory.composition(skill);
			if (!estimable(skill, facts) || !admitted.test(skill)) { continue; }
			final Action action = project(player, target, skill, facts, pvp, distance, patk, pDef, mDef);
			if (action != null) { actions.add(action); }
		}
		if (actions.isEmpty()) { return null; }
		final var decision = PhantomTacticalCombat.choose(context, actions, System::nanoTime, deadline);
		if (decision.first() == null) { return null; }
		final int id = decision.first().id();
		final Skill skill = id == 0 ? null : skills.stream().filter(known -> known.getId() == id).findFirst().orElse(null);
		if (id != 0 && skill == null) { return null; }
		final var choice = new Choice(skill, classId, classIndex, owner, target.getInstanceId(), target.getObjectId(), spawn,
			decision, System.nanoTime() - started);
		return choice.current(player, target) ? choice : null;
	}

	public static boolean selfSkill(Skill skill)
	{
		if (skill == null || skill.getTargetType() != TargetType.SELF || skill.hasNegativeEffect()) { return false; }
		final var composition = PhantomSkillInventory.composition(skill);
		return estimable(skill, composition) && composition.components().stream().allMatch(component ->
			component.handler().equals("Heal") || component.handler().equals("HealPercent") || component.handler().equals("FocusEnergy")
				|| component.handler().equals("Buff") && component.modifiers().stream().allMatch(modifier -> defensive(modifier.stat())));
	}

	/** A projection still needs a current caller-owned native readiness check before submission. */
	public static boolean hasSupportedProjection(Skill skill)
	{
		return skill != null && estimable(skill, PhantomSkillInventory.composition(skill));
	}

	private static boolean estimable(Skill skill, PhantomSkillInventory.Composition facts)
	{
		return facts.interpretation() == PhantomSkillInventory.Interpretation.ESTIMATED && skill.isActive() && !facts.passive()
			&& !facts.toggle() && !facts.transformation() && facts.area() == 0 && !skill.isAOE()
			&& !skill.isChanneling() && !skill.isSimultaneousCast() && !skill.isSuicideAttack()
			&& !skill.isHeroSkill() && !skill.isGMSkill() && !skill.is7Signs() && skill.getFlyType() == null && skill.getFlyRadius() == 0
			&& (skill.getTargetType() == TargetType.ONE || skill.getTargetType() == TargetType.SELF)
			&& facts.components().stream().allMatch(PhantomTacticalDecision::supportedComponent);
	}

	private static boolean supportedComponent(PhantomSkillInventory.Component component)
	{
		if (component.scope() != EffectScope.GENERAL && component.scope() != EffectScope.SELF
			&& component.scope() != EffectScope.PVE && component.scope() != EffectScope.PVP) { return false; }
		return switch (component.kind())
		{
			case PHYSICAL -> component.handler().equals("PhysicalDamage");
			case MAGIC -> component.handler().equals("MagicalDamage");
			case DRAIN -> component.handler().equals("HpDrain");
			case PERIODIC, CONTROL, ROOT, INTERRUPT -> true;
			case HEAL -> component.handler().equals("Heal") || component.handler().equals("HealPercent");
			case DEFENSE, DEBUFF -> (component.handler().equals("Buff") || component.handler().equals("Debuff"))
				&& !component.modifiers().isEmpty() && component.modifiers().stream().allMatch(modifier -> defensive(modifier.stat()));
			case CHARGE -> component.handler().equals("FocusEnergy");
			default -> false;
		};
	}

	private static Action project(Player player, Creature enemy, Skill skill, PhantomSkillInventory.Composition facts,
		boolean pvp, double distance, double patk, double pDef, double mDef)
	{
		final boolean self = skill.getTargetType() == TargetType.SELF;
		final Creature receiver = self ? player : enemy;
		final double range = Math.max(40, Math.min(skill.getCastRange(), player.getMagicalAttackRange(skill)));
		if ((!self && distance > range) || skill.isMagic() && player.isMuted() || skill.isPhysical() && player.isPhysicalMuted()
			|| receiver.isInvulAgainst(skill.getId(), skill.getLevel()) || !pvp && skill.isPvPOnly()) { return null; }
		final double mp = player.getStat().getMpInitialConsume(skill) + player.getStat().getMpConsume(skill);
		if (!Double.isFinite(mp) || mp < 0 || facts.items() > 0 && (facts.itemId() <= 0
			|| player.getInventory().getInventoryItemCount(facts.itemId(), -1) < facts.items())) { return null; }
		final var effects = modelEffects(player, enemy, skill, facts, pvp, patk, pDef, mDef);
		if (effects.isEmpty()) { return null; }
		final double seconds = Math.clamp(Formulas.calcAtkSpd(player, skill, skill.getHitTime()) / 1000.0 + skill.getCoolTime() / 1000.0, 0.25, 6);
		final long reuse = player.getSkillRemainingReuseTime(facts.reuseGroup());
		final boolean ready = reuse <= 0 && player.getCurrentHp() > facts.hp() && player.getCurrentMp() >= mp
			&& player.getCharges() >= facts.charges() && player.getChargedSouls() >= facts.souls();
		final boolean redundant = effects.stream().noneMatch(effect -> effect.kind() == Kind.PHYSICAL || effect.kind() == Kind.MAGIC || effect.kind() == Kind.HEAL)
			&& (receiver.isAffectedBySkill(skill.getId()) || !skill.getAbnormalType().isNone()
				&& receiver.getEffectList().getBuffInfoByAbnormalType(skill.getAbnormalType()) != null
				|| effects.getFirst().kind() == Kind.CONTROL && (receiver.isStunned() || receiver.isSleeping() || receiver.isParalyzed()));
		return new Action(skill.getId(), facts.reuseGroup(), ready, reuse <= 0 && (ready || !facts.conditional() || facts.charges() > 0 || facts.souls() > 0),
			seconds, facts.reuseMillis() / 1000.0, mp, facts.hp(), Math.min(0.25, facts.items() * 0.025),
			facts.charges(), facts.souls(), redundant, effects);
	}

	private static List<Effect> modelEffects(Player player, Creature enemy, Skill skill, PhantomSkillInventory.Composition facts,
		boolean pvp, double patk, double pDef, double mDef)
	{
		final var effects = new ArrayList<Effect>(facts.components().size() + 1);
		final double duration = Math.clamp(facts.duration(), 0, 6);
		final double power = Math.max(0, skill.getPower(pvp, !pvp));
		final double magic = 91 * Math.sqrt(Math.max(0, player.getMAtk(enemy, skill))) * power / mDef;
		final double physical = 70 * (patk + power) / pDef;
		final boolean self = skill.getTargetType() == TargetType.SELF;
		for (var component : facts.components())
		{
			if (component.scope() == EffectScope.PVE && pvp || component.scope() == EffectScope.PVP && !pvp) { continue; }
			final boolean selfEffect = self || component.scope() == EffectScope.SELF;
			final double likelihood = component.conditional() ? 0.35 : 0.8;
			switch (component.kind())
			{
				case PHYSICAL -> { if (!selfEffect) { effects.add(new Effect(Kind.PHYSICAL, physical, 0, likelihood)); } }
				case MAGIC -> { if (!selfEffect && power > 0) { effects.add(new Effect(Kind.MAGIC, magic, 0, likelihood)); } }
				case DRAIN ->
				{
					if (!selfEffect && power > 0)
					{
						effects.add(new Effect(Kind.MAGIC, magic, 0, likelihood));
						effects.add(new Effect(Kind.DRAIN, component.drainRatio(), 0, 1));
					}
				}
				case PERIODIC ->
				{
					if (!selfEffect && duration > 0)
					{
						// Handler-local tick power can be unavailable through native introspection. Keep a low prior.
						effects.add(new Effect(Kind.PERIODIC, power > 0 ? power / Math.max(1, duration) : enemy.getMaxHp() * 0.025, duration, 0.45));
					}
				}
				case HEAL ->
				{
					if (selfEffect && player.getCurrentHp() < player.getMaxHp() * 0.85)
					{
						final double healing = component.handler().equals("HealPercent") && power > 0 ? player.getMaxHp() * Math.min(100, power) / 100
							: power > 0 ? power : player.getMaxHp() * 0.15;
						effects.add(new Effect(Kind.HEAL, healing, 0, likelihood));
					}
				}
				case CONTROL, ROOT, INTERRUPT ->
				{
					if (!selfEffect && !enemy.isRaid() && !enemy.isStunned() && !enemy.isParalyzed() && !enemy.isSleeping())
					{
						final double chance = facts.activationRate() > 0 ? Math.clamp(facts.activationRate() / 100.0, 0.1, 0.7) : 0.35;
						effects.add(new Effect(component.kind(), 1, Math.max(0.5, duration), chance * likelihood));
					}
				}
				case DEFENSE, DEBUFF ->
				{
					if (duration > 0 && component.modifiers().stream().anyMatch(modifier -> defensive(modifier.stat())))
					{
						effects.add(new Effect(selfEffect ? Kind.DEFENSE : Kind.DEBUFF, 0.15, duration, likelihood));
					}
				}
				case CHARGE ->
				{
					if (selfEffect && component.handler().equals("FocusEnergy"))
					{
						effects.add(new Effect(Kind.CHARGE, 1, 0, 1));
					}
				}
				default -> { }
			}
		}
		return effects;
	}

	private static boolean defensive(String stat) { return "POWER_DEFENCE".equals(stat) || "MAGIC_DEFENCE".equals(stat); }

	private static Scene scene(Player player, Creature target, PlayerNativeWork.Owner owner, boolean pvp, double priorDps)
	{
		final List<Source> sources = new ArrayList<>();
		final var seen = new HashSet<Integer>();
		final var attackers = player.getAttackByList();
		final var recent = player.recentPlayerAttackers(PhantomTacticalCombat.MAX_SOURCES);
		final boolean fighting = threatens(target, player) || attackers.contains(target)
			|| recent.contains(target.getObjectId()) || target.isAttackingNow() && target.getTarget() == player;
		sources.add(source(player, target, fighting, true));
		seen.add(target.getObjectId());
		int examined = 0;
		for (Creature attacker : attackers)
		{
			if (++examined >= PhantomTacticalCombat.MAX_SOURCES || sources.size() >= PhantomTacticalCombat.MAX_SOURCES) { break; }
			if (validSource(player, attacker) && seen.add(attacker.getObjectId())) { sources.add(source(player, attacker, true, false)); }
		}
		for (int objectId : recent)
		{
			if (sources.size() >= PhantomTacticalCombat.MAX_SOURCES) { break; }
			if (World.getInstance().findObject(objectId) instanceof Creature attacker && validSource(player, attacker) && seen.add(objectId))
			{
				sources.add(source(player, attacker, true, false));
			}
		}
		final boolean truncated = attackers.size() + recent.size() >= PhantomTacticalCombat.MAX_SOURCES;
		final double targetPool = target.getCurrentHp() + (pvp && target.isPlayer() ? target.getCurrentCp() : 0);
		final double maximumPool = target.getMaxHp() + (pvp && target.isPlayer() ? target.getMaxCp() : 0);
		final double regen = target.isNpc() ? Formulas.calcHpRegen(target) / 3 : maximumPool * 0.008;
		double outgoing = priorDps;
		if (!pvp && target instanceof Monster monster && owner.nativeObservationHealthy() && owner.evidence() != null)
		{
			final var identity = new Encounter(player.getObjectId(), owner.epoch(), player.getActiveClass(), player.getClassIndex(), monster.getNativeEvidenceTarget());
			outgoing = CALIBRATION.estimate(identity, owner.evidence().lastDamage(), priorDps, System.nanoTime());
		}
		return new Scene(player.getCurrentHp(), player.getMaxHp(), player.getCurrentCp(), player.getCurrentMp(), player.getMaxMp(),
			player.getCharges(), player.getChargedSouls(), targetPool, maximumPool, Math.max(0, regen), outgoing, pvp, truncated, sources);
	}

	private static boolean validSource(Player actor, Creature source)
	{
		return source != actor && !source.isAlikeDead() && source.getInstanceId() == actor.getInstanceId() && actor.calculateDistance3D(source) <= 1400;
	}

	private static boolean threatens(Creature source, Player actor)
	{
		return source.hasAI() && (source.getAI().getAttackTarget() == actor || source.isCastingNow() && source.getAI().getCastTarget() == actor);
	}

	private static Source source(Player actor, Creature enemy, boolean incoming, boolean primary)
	{
		final double distance = actor.calculateDistance3D(enemy);
		final boolean npc = enemy.isNpc();
		final boolean casting = enemy.isCastingNow() && (incoming || enemy.getTarget() == actor);
		final double range = casting ? 600 : npc ? Math.max(40, enemy.getPhysicalAttackRange()) : 100;
		final var geo = GeoEngine.getInstance();
		final boolean within = distance <= range + actor.getCollisionRadius() + enemy.getTemplate().getCollisionRadius();
		final boolean line = distance <= 1200 && geo.canSeeTarget(enemy, actor);
		final boolean reachable = line && (within || !enemy.isMovementDisabled());
		final double arrival = within ? 0 : Math.max(0, distance - range) / Math.max(1, enemy.getRunSpeed());
		final double dps = npc ? 70 * enemy.getPAtk(actor) / Math.max(1, actor.getPDef(enemy)) * Math.clamp(enemy.getPAtkSpd() / 500, 0.4, 3)
			: actor.getMaxHp() * 0.08;
		final double burst = casting ? npc ? Math.max(dps, 91 * Math.sqrt(Math.max(0, enemy.getMAtk(actor, null))) * 8 / Math.max(1, actor.getMDef(enemy, null)))
			: actor.getMaxHp() * 0.2 : 0;
		return new Source(dps, burst, incoming, reachable, casting, primary, arrival, npc ? 0.8 : 0.45, enemy.isPlayable());
	}

	private static long generation(Creature target)
	{
		return target instanceof Monster monster ? monster.getNativeEvidenceTarget().spawnGeneration() : 0;
	}

	public static void submitted(String route, long profile, Player actor, Creature target, Choice choice)
	{
		final long now = System.nanoTime();
		final long next = NEXT_TRACE.get();
		if (now - next < 0 || !NEXT_TRACE.compareAndSet(next, now + 20_000_000_000L)) { return; }
		final var plan = choice.decision();
		LOGGER.info(() -> "Tactical023 native submission route=" + route + " profile=" + profile + " actor=" + actor.getObjectId()
			+ " epoch=" + choice.owner().epoch() + " target=" + target.getObjectId() + " spawn=" + choice.spawnGeneration()
			+ " action=" + (choice.attack() ? "ATTACK" : choice.skill().getId() + ":" + choice.skill().getLevel())
			+ " threats=" + plan.threat().attackers() + " expansions=" + plan.expansions() + " limited=" + plan.budgetLimited()
			+ " nanos=" + choice.elapsedNanos() + " exp=" + actor.getExp() + " sp=" + actor.getSp());
	}
}
