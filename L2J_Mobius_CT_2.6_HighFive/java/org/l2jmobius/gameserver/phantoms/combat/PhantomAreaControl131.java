/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.combat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.effects.EffectType;
import org.l2jmobius.gameserver.model.skill.EffectScope;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.skill.holders.SkillUseHolder;
import org.l2jmobius.gameserver.model.skill.targets.TargetType;
import org.l2jmobius.gameserver.model.zone.ZoneId;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.taskmanagers.GameTimeTaskManager;
import org.l2jmobius.gameserver.util.LocationUtil;

/** An explicitly authorized cast borrows the existing Phantom owner and stock recipient/cast pipeline. */
public final class PhantomAreaControl131
{
	private static final int MAX_RECIPIENTS = 16;
	private static final int MAX_RADIUS = 600;
	private static final Set<String> AREA_EFFECTS = Set.of("handlers.skill.effects.MagicalDamage", "handlers.skill.effects.PhysicalDamage",
		"handlers.skill.effects.Sleep", "handlers.skill.effects.Root", "handlers.skill.effects.Stun", "handlers.skill.effects.Paralyze",
		"handlers.skill.effects.Mute", "handlers.skill.effects.Fear");
	private static final ThreadLocal<Attempt> DISPATCH = new ThreadLocal<>();
	private static final ThreadLocal<Attempt> IMPACT = new ThreadLocal<>();

	public enum AreaDomain { PVE, PVP }

	private PhantomAreaControl131()
	{
	}

	public static boolean supportsArea(Skill skill)
	{
		if (!active(skill) || !skill.hasNegativeEffect() || skill.isOverhit() || skill.getAffectRange() < 1
			|| skill.getAffectRange() > MAX_RADIUS || !(skill.getTargetType() == TargetType.AURA
				|| skill.getTargetType() == TargetType.AREA && skill.getCastRange() >= 0))
		{
			return false;
		}
		int count = 0;
		boolean primaryEffect = false;
		for (EffectScope scope : EffectScope.values())
		{
			final var effects = skill.getEffects(scope);
			if (effects == null || effects.isEmpty()) { continue; }
			if (scope != EffectScope.GENERAL) { return false; }
			for (var effect : effects)
			{
				if (++count > 2) { return false; }
				final String handler = effect.getClass().getName();
				if (AREA_EFFECTS.contains(handler)) { primaryEffect = true; }
				else if (!"handlers.skill.effects.DefenceTrait".equals(handler) || !skill.hasEffectType(EffectType.SLEEP)) { return false; }
			}
		}
		return primaryEffect;
	}

	private static boolean active(Skill skill)
	{
		return skill != null && skill.isActive() && !skill.isPassive() && !skill.isToggle() && !skill.isChanneling()
			&& !skill.isSimultaneousCast() && !skill.isSuicideAttack() && !skill.isHeroSkill() && !skill.isGMSkill() && !skill.is7Signs()
			&& !skill.isTransformation() && !skill.isStatic() && skill.getFlyType() == null
			&& skill.getFlyRadius() == 0 && skill.getReferenceItemId() == 0;
	}

	private static boolean hasDamageRedirect(Player player)
	{
		// Damage may also be reflected onto the caster before these native transfer paths run.
		return player != null && (player.getTransferingDamageTo() != null || player.hasServitor());
	}

	/**
	 * Deterministic opportunity count over the owner's bounded authorization set.
	 * The stock target list and its affect-limit RNG are evaluated only by the actual cast.
	 */
	public static int estimateArea(Player caster, Skill skill, Creature primary, List<? extends Creature> approved,
		AreaDomain domain, BooleanSupplier currentAuthority, Predicate<Creature> currentRecipient)
	{
		if (!supportsArea(skill) || domain == null) { return 0; }
		final var attempt = prepare(caster, skill, primary, approved, domain, currentAuthority, currentRecipient, null);
		if (attempt == null || !attempt.current() || !attempt.eligible(primary)) { return 0; }
		final int count = (int) attempt._approved.stream().filter(recipient -> attempt.eligible(recipient.target())).count();
		return attempt.current() ? count : 0;
	}

	public static Attempt castArea(Player caster, Skill skill, Creature primary, List<? extends Creature> approved,
		AreaDomain domain, boolean forceUse, BooleanSupplier currentAuthority, Predicate<Creature> currentRecipient, Runnable atPublication)
	{
		if (domain == null || !supportsArea(skill)) { return null; }
		final Attempt attempt = prepare(caster, skill, primary, approved, domain, currentAuthority, currentRecipient, atPublication);
		return dispatch(attempt, forceUse) ? attempt : null;
	}

	public static boolean castSingleSupport(Player caster, Skill skill, Creature recipient,
		BooleanSupplier currentAuthority, Predicate<Creature> currentRecipient)
	{
		return recipient instanceof Player && active(skill) && !skill.isDamage() && !skill.hasNegativeEffect() && !skill.isDebuff()
			&& skill.getAffectRange() == 0 && (skill.getTargetType() == TargetType.ONE || skill.getTargetType() == TargetType.SELF && caster == recipient)
			&& dispatch(prepare(caster, skill, recipient, List.of(recipient), null, currentAuthority, currentRecipient, null), false);
	}

	static boolean supportsSingleResurrection(Skill skill)
	{
		if (!active(skill) || skill.getTargetType() != TargetType.PC_BODY || skill.getAffectRange() != 0
			|| skill.isDamage() || skill.hasNegativeEffect() || skill.isDebuff()) { return false; }
		for (var scope : EffectScope.values())
		{
			final var effects = skill.getEffects(scope);
			if (scope == EffectScope.GENERAL)
			{
				if (effects == null || effects.size() != 1 || effects.getFirst().getEffectType() != EffectType.RESURRECTION
					|| !"handlers.skill.effects.Resurrection".equals(effects.getFirst().getClass().getName())) { return false; }
			}
			else if (effects != null && !effects.isEmpty()) { return false; }
		}
		return true;
	}

	public static boolean castSingleResurrection(Player caster, Skill skill, Player corpse, BooleanSupplier currentAuthority)
	{
		if (caster == null || caster == corpse || currentAuthority == null || !ownedHeadlessCorpse(corpse) || !supportsSingleResurrection(skill))
		{
			return false;
		}
		final var party = caster.getParty();
		if (party == null || corpse.getParty() != party) { return false; }
		return dispatch(prepare(caster, skill, corpse, List.of(corpse), null,
			() -> caster.getParty() == party && corpse.getParty() == party && currentAuthority.getAsBoolean(),
			target -> target == corpse && ownedHeadlessCorpse(corpse), null), false);
	}

	/** Called only at creation of the current native proposal, before any dialog can be delivered. */
	public static boolean permitsResurrectionAnswer(Player corpse, Player reviver)
	{
		final var attempt = IMPACT.get();
		return attempt != null && attempt._effectsEntered && attempt._skill.getTargetType() == TargetType.PC_BODY
			&& attempt._actor.target() == reviver && attempt._primary == corpse && ownedHeadlessCorpse(corpse)
			&& corpse.isReviveRequested() && !corpse.isRevivingPet() && attempt.currentTargets(attempt._pinned);
	}

	private static boolean ownedHeadlessCorpse(Player player)
	{
		return player != null && player.isDead() && !player.isResurrectionBlocked() && player.isNativeWorkManaged()
			&& player.hasHeadlessOutboundSession() && !player.isGM() && !player.isFakePlayer()
			&& PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(player.getObjectId()) == PhantomIdentityLeaseRegistry.OwnerKind.PHANTOM;
	}

	private static Attempt prepare(Player caster, Skill skill, Creature primary, List<? extends Creature> approved,
		AreaDomain domain, BooleanSupplier authority, Predicate<Creature> currentRecipient, Runnable atPublication)
	{
		if (caster == null || primary == null || approved == null || authority == null || currentRecipient == null
			|| domain != AreaDomain.PVP && skill.isPvPOnly()
			|| approved.size() < (domain == null ? 1 : 2) || approved.size() > MAX_RECIPIENTS
			|| !caster.isNativeWorkManaged() || !caster.hasHeadlessOutboundSession() || caster.isGM() || caster.isFakePlayer()
			|| PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(caster.getObjectId()) != PhantomIdentityLeaseRegistry.OwnerKind.PHANTOM)
		{
			return null;
		}
		final Lifetime actor = Lifetime.capture(caster);
		if (actor == null || actor.owner() == null) { return null; }

		final List<Lifetime> lifetimes = new ArrayList<>();
		final Set<Creature> distinct = Collections.newSetFromMap(new IdentityHashMap<>());
		boolean canReachPrimary = false;
		for (Creature target : approved)
		{
			if (!distinct.add(target) || !(domain == AreaDomain.PVE ? target instanceof Monster : target instanceof Player)) { return null; }
			final Lifetime lifetime = Lifetime.capture(target);
			if (lifetime == null) { return null; }
			lifetimes.add(lifetime);
			canReachPrimary |= target == primary;
		}
		return canReachPrimary ? new Attempt(actor, skill, primary, List.copyOf(lifetimes), domain, authority, currentRecipient, atPublication) : null;
	}

	private static boolean dispatch(Attempt attempt, boolean forceUse)
	{
		if (attempt == null || DISPATCH.get() != null || IMPACT.get() != null) { return false; }
		final Player caster = (Player) attempt._actor.target();
		PlayerNativeWork.runOriginalCombat(caster, attempt._approved.stream().map(Lifetime::target).toList(), "task131-authorized-cast", () ->
		{
			if (!attempt.current() || !attempt.eligible(attempt._primary) || !attempt.usesExpectedTarget()
				|| caster.isMoving() || caster.isAttackingOrCastingNow() || caster.isCastingSimultaneouslyNow() || caster.getQueuedSkill() != null
				|| caster.getBowAttackEndTime() > GameTimeTaskManager.getInstance().getGameTicks()
				|| caster.isSkillDisabled(attempt._skill) || !caster.checkDoCastConditions(attempt._skill))
			{
				return;
			}
			final SkillUseHolder prior = caster.getCurrentSkill();
			DISPATCH.set(attempt);
			try
			{
				if (!caster.bindScopedNativeCast(attempt, attempt._skill)) { return; }
				try
				{
					caster.useMagic(attempt._skill, forceUse, true);
				}
				finally
				{
					// A failed synchronous cast cannot leave an AI intention that will recast later.
					final SkillUseHolder current = caster.getCurrentSkill();
					try
					{
						if (!attempt._published && current != null && current != prior && current.getSkill() == attempt._skill
							&& caster.getNativeWorkOwner() == attempt._actor.owner() && caster.hasAI() && caster.getAI().getIntention() == Intention.CAST)
						{
							caster.getAI().setIntention(Intention.ACTIVE);
						}
					}
					finally { caster.releaseScopedNativeCast(attempt); }
				}
			}
			finally { DISPATCH.remove(); }
		});
		return attempt._published;
	}

	/** Capture only this stock cast before it starts creating native work. */
	public static Attempt pending(Creature actor, Skill skill)
	{
		final Attempt attempt = DISPATCH.get();
		return attempt != null && attempt._actor.target() == actor && attempt._skill == skill ? attempt : null;
	}

	/** Recheck after the canonical native-write boundary has admitted all captured participants. */
	public static boolean permitsImpact(Creature caster, Skill skill, List<WorldObject> targets)
	{
		final Attempt attempt = IMPACT.get();
		return attempt == null || attempt._actor.target() != caster || (attempt._skill == skill && attempt.enterEffects(targets));
	}

	public static boolean scopedSupportEffect(Creature caster, Creature recipient, Skill skill)
	{
		final Attempt attempt = IMPACT.get();
		return attempt != null && attempt._domain == null && attempt._actor.target() == caster
			&& attempt._primary == recipient && attempt._skill == skill;
	}

	static <T> List<T> pinNativeRecipients(List<T> stock, List<? extends T> approved, T primary, int minimum)
	{
		if (stock == null || approved == null || primary == null || minimum < 1 || stock.size() < minimum
			|| stock.size() > MAX_RECIPIENTS || approved.size() > MAX_RECIPIENTS)
		{
			return List.of();
		}
		final Set<T> exact = Collections.newSetFromMap(new IdentityHashMap<>());
		exact.addAll(approved);
		boolean hasPrimary = false;
		for (T target : stock)
		{
			if (target == null || !exact.remove(target)) { return List.of(); }
			hasPrimary |= target == primary;
		}
		return hasPrimary ? List.copyOf(stock) : List.of();
	}

	static boolean sameNativeRecipients(List<?> pinned, List<?> observed)
	{
		if (pinned == null || observed == null || pinned.isEmpty() || pinned.size() != observed.size()) { return false; }
		int index = 0;
		for (Object recipient : observed)
		{
			if (recipient != pinned.get(index++)) { return false; }
		}
		return true;
	}

	static boolean sameMonsterSpawn(PlayerNativeEvidence.Target pinned, PlayerNativeEvidence.Target observed)
	{
		return pinned != null && pinned.spawnGeneration() > 0 && pinned.equals(observed);
	}

	private record Lifetime(Creature target, int instanceId, PlayerNativeWork.Owner owner, long epoch, PlayerNativeEvidence.Target monsterSpawn, long corpseGeneration)
	{
		static Lifetime capture(Creature creature)
		{
			final Player player = creature.asPlayer();
			final var owner = player == null ? null : player.getNativeWorkOwner();
			if (player != null && player.isNativeWorkManaged() && owner == null) { return null; }
			final var spawn = creature instanceof Monster monster ? monster.getNativeEvidenceTarget() : null;
			final long corpse = player != null && player.isDead() ? player.nativeCorpseGeneration() : 0;
			return new Lifetime(creature, creature.getInstanceId(), owner, owner == null ? 0 : owner.epoch(), spawn, corpse);
		}

		boolean current()
		{
			return current(false);
		}

		boolean current(boolean corpse)
		{
			final Player player = target.asPlayer();
			return target.isSpawned() && (corpse ? player != null && corpseGeneration > 0 && corpseGeneration == player.nativeCorpseGeneration()
				: !target.isAlikeDead()) && !target.isTeleporting() && target.getInstanceId() == instanceId
				&& World.getInstance().findObject(target.getObjectId()) == target
				&& (player == null || player.isOnline() && World.getInstance().getPlayer(player.getObjectId()) == player
					&& player.getNativeWorkOwner() == owner)
				&& (!(target instanceof Monster monster) || sameMonsterSpawn(monsterSpawn, monster.getNativeEvidenceTarget()))
				&& (owner == null || owner.player() == player && owner.epoch() == epoch && owner.isCurrent() && !owner.sealed());
		}

		boolean ticketed()
		{
			return owner == null || PlayerNativeWork.current(owner) != null;
		}
	}

	public static final class Attempt
	{
		private final Lifetime _actor;
		private final Skill _skill;
		private final Creature _primary;
		private final List<Lifetime> _approved;
		private final AreaDomain _domain;
		private final BooleanSupplier _authority;
		private final Predicate<Creature> _recipient;
		private Runnable _atPublication;
		private SkillUseHolder _holder;
		private List<WorldObject> _pinned = List.of();
		private boolean _published;
		private boolean _effectsEntered;

		private Attempt(Lifetime actor, Skill skill, Creature primary, List<Lifetime> approved, AreaDomain domain,
			BooleanSupplier authority, Predicate<Creature> recipient, Runnable atPublication)
		{
			_actor = actor;
			_skill = skill;
			_primary = primary;
			_approved = approved;
			_domain = domain;
			_authority = authority;
			_recipient = recipient;
			_atPublication = atPublication;
		}

		public List<WorldObject> selectNativeTargets(Creature origin, List<WorldObject> stock, boolean simultaneous)
		{
			final Player caster = (Player) _actor.target();
			final SkillUseHolder holder = caster.getCurrentSkill();
			if (simultaneous || origin != castOrigin() || _holder != null || holder == null || holder.getSkill() != _skill
				|| !_actor.ticketed() || !current())
			{
				return List.of();
			}
			_holder = holder;
			_pinned = pinNativeRecipients(stock, _approved.stream().map(Lifetime::target).toList(), _primary, _domain == null ? 1 : 2);
			return currentTargets(_pinned) ? _pinned : List.of();
		}

		public boolean currentTargets(List<WorldObject> targets)
		{
			return sameNativeRecipients(_pinned, targets) && current()
				&& _holder == ((Player) _actor.target()).getCurrentSkill() && _actor.ticketed()
				&& targets.stream().allMatch(target -> eligible(target.asCreature()) && lifetime(target.asCreature()).ticketed())
				&& _authority.getAsBoolean();
		}

		public void published()
		{
			_published = true;
			final Runnable preparation = _atPublication;
			_atPublication = null;
			if (preparation != null)
			{
				preparation.run();
			}
		}

		public void cancel()
		{
			final Player caster = (Player) _actor.target();
			if (_published && !_effectsEntered && _actor.current() && caster.getCurrentSkill() == _holder && caster.hasAI()
				&& caster.getAI().getIntention() == Intention.CAST && caster.getAI().getCastTarget() == castOrigin())
			{
				caster.abortCast();
				caster.getAI().setIntention(Intention.IDLE);
			}
		}

		public void impact(Runnable effect)
		{
			if (!_published || IMPACT.get() != null) { return; }
			IMPACT.set(this);
			try { effect.run(); }
			finally { IMPACT.remove(); }
		}

		private boolean enterEffects(List<WorldObject> targets)
		{
			if (!_published || _effectsEntered || !currentTargets(targets)) { return false; }
			_effectsEntered = true;
			return true;
		}

		private Creature castOrigin()
		{
			return _skill.getTargetType() == TargetType.AURA || _skill.getTargetType() == TargetType.SELF ? _actor.target() : _primary;
		}

		private boolean usesExpectedTarget()
		{
			// Stock useMagic resolves AURA/SELF to the caster without a current target.
			return castOrigin() == _actor.target() || _actor.target().getTarget() == _primary;
		}

		private boolean current()
		{
			final Player caster = (Player) _actor.target();
			final var weapon = caster.getActiveWeaponItem();
			return _actor.current() && _authority.getAsBoolean() && caster.getKnownSkill(_skill.getId()) == _skill
				&& !caster.isTransformed() && (weapon == null || !weapon.hasTriggeredSkills()) && caster.getTriggerSkills().isEmpty()
				&& (_domain == null || !_skill.isDamage() || !hasDamageRedirect(caster));
		}

		private Lifetime lifetime(Creature candidate)
		{
			for (Lifetime lifetime : _approved)
			{
				if (lifetime.target() == candidate) { return lifetime; }
			}
			return null;
		}

		private boolean eligible(Creature target)
		{
			final Player caster = (Player) _actor.target();
			final Lifetime lifetime = lifetime(target);
			final var geo = GeoEngine.getInstance();
			final boolean resurrection = _skill.getTargetType() == TargetType.PC_BODY;
			if (lifetime == null || !lifetime.current(resurrection) || target.getInstanceId() != _actor.instanceId()
				|| resurrection && !_effectsEntered && target.asPlayer().isReviveRequested()
				|| !caster.isInSurroundingRegion(target) || !_recipient.test(target)
				|| !geo.hasGeo(caster.getX(), caster.getY()) || !geo.hasGeo(target.getX(), target.getY())
				|| !geo.canSeeTarget(caster, target)
				|| target == _primary && castOrigin() != caster
					&& !LocationUtil.checkIfInRange(Math.max(40, caster.getMagicalAttackRange(_skill)), caster, target, true))
			{
				return false;
			}
			if (_domain == null) { return _skill.checkCondition(caster, target, false); }

			// Native area checks do not include distance and do allow some foreign domains.
			// The caller's authorization and pinned lifetimes above are required independently.
			final boolean arena = caster.isInsideZone(ZoneId.PVP) && !caster.isInsideZone(ZoneId.SIEGE);
			return target != caster && !target.isInvul() && !target.isInvulAgainst(_skill.getId(), _skill.getLevel())
				&& !target.isInsidePeaceZone(caster) && !target.isSleeping()
				&& (target == castOrigin() || LocationUtil.checkIfInRange(_skill.getAffectRange(), castOrigin(), target, true))
				&& Skill.checkForAreaOffensiveSkills(caster, target, _skill, arena) && _skill.checkCondition(caster, target, false)
				&& (!_skill.isDamage() || !hasDamageRedirect(target.asPlayer()))
				&& (!_skill.isDamage() || target.getEffectList().getEffects().stream().noneMatch(info -> info.isInUse() && info.getSkill().isRemovedOnDamage()))
				&& !target.isAffectedBySkill(_skill.getId());
		}
	}
}
