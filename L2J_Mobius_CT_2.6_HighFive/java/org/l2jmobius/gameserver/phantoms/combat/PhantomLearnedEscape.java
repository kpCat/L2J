/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.combat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.model.effects.EffectType;
import org.l2jmobius.gameserver.model.groups.Party;
import org.l2jmobius.gameserver.model.skill.EffectScope;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.skill.holders.SkillUseHolder;
import org.l2jmobius.gameserver.model.skill.targets.TargetType;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.taskmanagers.GameTimeTaskManager;

/** An escape request borrows the caller's action authority and the stock skill task. */
public final class PhantomLearnedEscape
{
	private static final int RETURN = 1050;
	private static final int PARTY_RECALL = 1255;
	private static final int MAX_PARTY = 9;
	private static final ThreadLocal<Attempt> DISPATCH = new ThreadLocal<>();
	private static final ThreadLocal<Attempt> IMPACT = new ThreadLocal<>();
	private static final AtomicReference<AuditObserver> AUDIT = new AtomicReference<>();

	private PhantomLearnedEscape()
	{
	}

	public enum Status { PENDING, ARRIVED, INTERRUPTED }

	public record Arrival(int objectId, long epoch, PhantomNavigationPoint before, PhantomNavigationPoint after, long arrivedNanos)
	{
	}

	// Native debit requests and observed balance changes are kept separately.
	public record MpDebit(double requested, double before, double after)
	{
	}

	public record Receipt(int skillId, int level, int casterObjectId, long casterEpoch, int nativeCastMillis,
		long startedNanos, long completedNanos, MpDebit initialMp, MpDebit hitMp, List<Arrival> arrivals, Status status)
	{
		public Receipt
		{
			arrivals = List.copyOf(arrivals);
		}
	}

	public record AuditReceipt(Receipt cast, int instanceId, Map<Integer, Long> approvedRecipients)
	{
		public AuditReceipt { approvedRecipients = Map.copyOf(approvedRecipients); }
	}

	private record AuditObserver(Consumer<AuditReceipt> arrived, Consumer<String> failed) implements AutoCloseable
	{
		@Override public void close() { AUDIT.compareAndSet(this, null); }
	}

	public static AutoCloseable observeNativeAudit(Consumer<AuditReceipt> arrived, Consumer<String> failed)
	{
		if (!System.getProperty("phantom.learning131.nonce", "").matches("[0-9a-f]{32}"))
		{
			throw new IllegalStateException("Missing scoped escape observation claim.");
		}
		final var observer = new AuditObserver(Objects.requireNonNull(arrived), Objects.requireNonNull(failed));
		if (!AUDIT.compareAndSet(null, observer)) { throw new IllegalStateException("Native escape observer already installed."); }
		return observer;
	}

	private record Recipient(Player player, PlayerNativeWork.Owner owner, long epoch, int instanceId)
	{
		boolean current()
		{
			final var world = World.getInstance();
			return player.getNativeWorkOwner() == owner && owner.player() == player && owner.epoch() == epoch
				&& owner.isCurrent() && !owner.sealed() && player.isOnline() && player.isSpawned() && !player.isAlikeDead()
				&& world.getPlayer(player.getObjectId()) == player && world.findObject(player.getObjectId()) == player;
		}
	}

	public static Attempt startSolo(Player caster, BooleanSupplier authority)
	{
		return caster == null ? null : start(caster, RETURN, List.of(caster), authority);
	}

	/** The caller supplies the complete currently authorized party, including the caster. */
	public static Attempt startParty(Player caster, List<Player> approvedRoster, BooleanSupplier authority)
	{
		return start(caster, PARTY_RECALL, approvedRoster, authority);
	}

	public static boolean knowsPartyRecall(Player caster)
	{
		return managedPhantom(caster) && stockEscape(caster.getKnownSkill(PARTY_RECALL), PARTY_RECALL);
	}

	private static Attempt start(Player caster, int skillId, List<Player> approved, BooleanSupplier authority)
	{
		if (!managedPhantom(caster) || approved == null || approved.isEmpty() || approved.size() > MAX_PARTY
			|| authority == null || !authority.getAsBoolean() || DISPATCH.get() != null)
		{
			return null;
		}
		final var skill = caster.getKnownSkill(skillId);
		final Party party = skillId == PARTY_RECALL ? caster.getParty() : null;
		if (!stockEscape(skill, skillId) || (skillId == PARTY_RECALL
			&& (party == null || approved.size() < 2 || !sameRecipients(approved, party.getMembers()))))
		{
			return null;
		}
		final var recipients = new ArrayList<Recipient>(approved.size());
		for (Player player : approved)
		{
			if (!managedPhantom(player) || player.getInstanceId() != caster.getInstanceId())
			{
				return null;
			}
			final var owner = player.getNativeWorkOwner();
			if (owner == null || owner.epoch() <= 0)
			{
				return null;
			}
			recipients.add(new Recipient(player, owner, owner.epoch(), player.getInstanceId()));
		}
		final var first = recipients.stream().filter(recipient -> recipient.player() == caster).findFirst().orElse(null);
		if (first == null)
		{
			return null;
		}
		recipients.remove(first);
		recipients.addFirst(first);
		final var attempt = new Attempt(skill, party, recipients, authority);
		PlayerNativeWork.run(caster, "phantom-learned-escape", () ->
		{
			if (!attempt.authorized() || !attempt.targetsEligible() || caster.isAttackingOrCastingNow() || caster.isMoving()
				|| caster.getQueuedSkill() != null || caster.getBowAttackEndTime() > GameTimeTaskManager.getInstance().getGameTicks()
				|| !caster.checkDoCastConditions(skill))
			{
				return;
			}
			DISPATCH.set(attempt);
			try
			{
				caster.useMagic(skill, false, true);
			}
			finally
			{
				DISPATCH.remove();
			}
		});
		return attempt._published ? attempt : null;
	}

	private static boolean managedPhantom(Player player)
	{
		return player != null && player.isNativeWorkManaged() && player.hasHeadlessOutboundSession() && !player.isGM() && !player.isFakePlayer()
			&& PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(player.getObjectId()) == PhantomIdentityLeaseRegistry.OwnerKind.PHANTOM;
	}

	private static boolean stockEscape(Skill skill, int skillId)
	{
		if (skill == null || skill.getId() != skillId || skill.getLevel() < 1 || skill.getLevel() > 2
			|| !skill.isActive() || !skill.isMagic() || skill.isStatic() || skill.isToggle() || skill.isChanneling() || !skill.hasCastPreconditions()
			|| skill.isSimultaneousCast() || skill.isDebuff() || skill.hasNegativeEffect() || skill.getHitTime() <= 0
			|| skill.getReferenceItemId() != 0 || skill.getItemConsumeCount() != 0 || skill.getHpConsume() != 0
			|| skill.getChargeConsumeCount() != 0 || skill.getMaxSoulConsumeCount() != 0 || skill.getFlyType() != null
			|| skill.nextActionIsAttack() || skill.useSoulShot()
			|| skill.getTargetType() != (skillId == RETURN ? TargetType.SELF : TargetType.PARTY)
			|| skill.getAffectRange() != (skillId == RETURN ? 0 : 1000))
		{
			return false;
		}
		for (EffectScope scope : EffectScope.values())
		{
			final var effects = skill.getEffects(scope);
			if (scope == EffectScope.GENERAL)
			{
				if (effects == null || effects.size() != 1 || effects.getFirst().getEffectType() != EffectType.TELEPORT
					|| !"handlers.skill.effects.Escape".equals(effects.getFirst().getClass().getName()))
				{
					return false;
				}
			}
			else if (effects != null && !effects.isEmpty())
			{
				return false;
			}
		}
		return true;
	}

	/** This hook does not affect connected players or other stock teleport skills. */
	public static boolean requiresAuthority(Creature actor, Skill skill)
	{
		return skill != null && (skill.getId() == RETURN || skill.getId() == PARTY_RECALL)
			&& actor instanceof Player player && player.isNativeWorkManaged() && player.hasHeadlessOutboundSession();
	}

	public static Attempt capture(Creature actor, Skill skill, boolean simultaneous)
	{
		final Attempt attempt = DISPATCH.get();
		if (attempt == null || simultaneous || attempt._caster.player() != actor || attempt._skill != skill || attempt._holder != null
			|| !attempt.authorized() || PlayerNativeWork.current(attempt._caster.owner()) == null)
		{
			return null;
		}
		final var holder = attempt._caster.player().getCurrentSkill();
		if (holder == null || holder.getSkill() != skill)
		{
			return null;
		}
		attempt._holder = holder;
		return attempt;
	}

	static <T> List<T> pinRecipients(List<T> approved, List<?> nativeTargets)
	{
		if (approved == null || nativeTargets == null || approved.isEmpty() || approved.size() > MAX_PARTY
			|| nativeTargets.size() > 2 * MAX_PARTY)
		{
			return List.of();
		}
		final Set<T> distinct = Collections.newSetFromMap(new IdentityHashMap<>());
		for (T target : approved)
		{
			if (target == null || !distinct.add(target) || nativeTargets.stream().filter(nativeTarget -> nativeTarget == target).count() != 1)
			{
				return List.of();
			}
		}
		return List.copyOf(approved);
	}

	static boolean sameRecipients(List<?> expected, List<?> observed)
	{
		return observed != null && expected.size() == observed.size() && !pinRecipients(expected, observed).isEmpty();
	}

	/** Stock Escape remains the sole location writer. Scoped casts also acknowledge the headless teleport. */
	public static void applyEffect(Creature caster, Creature recipient, Skill skill, boolean town, Runnable nativeEffect)
	{
		if (!requiresAuthority(caster, skill))
		{
			nativeEffect.run();
			return;
		}
		final var attempt = IMPACT.get();
		if (attempt != null && town && attempt._caster.player() == caster && attempt._skill == skill)
		{
			attempt.applyRecipient(recipient, nativeEffect);
		}
	}

	public static final class Attempt
	{
		private final Skill _skill;
		private final Party _party;
		private final Recipient _caster;
		private final List<Recipient> _recipients;
		private final List<WorldObject> _targets;
		private final BooleanSupplier _authority;
		private final Set<Player> _effects = Collections.newSetFromMap(new IdentityHashMap<>());
		private final List<Arrival> _arrivals = new ArrayList<>();
		private final AuditObserver _audit = AUDIT.get();
		private volatile SkillUseHolder _holder;
		private volatile boolean _published;
		private volatile boolean _cancelRequested;
		private volatile boolean _resolving;
		private volatile Receipt _receipt;
		private PhantomNavigationPoint _impactOrigin;
		private Player _effectRecipient;
		private MpDebit _initialMp;
		private MpDebit _hitMp;
		private long _startedNanos;
		private int _nativeCastMillis;

		private Attempt(Skill skill, Party party, List<Recipient> recipients, BooleanSupplier authority)
		{
			_skill = skill;
			_party = party;
			_caster = recipients.getFirst();
			_recipients = List.copyOf(recipients);
			_targets = recipients.stream().map(recipient -> (WorldObject) recipient.player()).toList();
			_authority = authority;
		}

		private boolean authorized()
		{
			final Player caster = _caster.player();
			return !_cancelRequested && _receipt == null && _authority.getAsBoolean()
				&& caster.getKnownSkill(_skill.getId()) == _skill && _caster.current()
				&& (_party == null || caster.getParty() == _party && sameRecipients(_targets, _party.getMembers()));
		}

		private boolean targetsEligible()
		{
			final Player caster = _caster.player();
			return _recipients.stream().allMatch(recipient ->
			{
				final Player player = recipient.player();
				return eligible(recipient) && (player == caster || Skill.addCharacter(caster, player, _skill.getAffectRange(), false));
			});
		}

		private boolean eligible(Recipient recipient)
		{
			final Player player = recipient.player();
			return recipient.current() && player.getInstanceId() == recipient.instanceId() && !player.isTeleporting()
				&& player.getKrateiArena() == null && player.getUCState() == Player.UC_STATE_NONE
				&& (_party == null || player.getParty() == _party) && _skill.checkCondition(player, player, false)
				&& !player.isCastingSimultaneouslyNow() && player.getQueuedSkill() == null
				&& (player == _caster.player() || !player.isAttackingOrCastingNow());
		}

		public List<WorldObject> selectNativeTargets(List<WorldObject> stockTargets)
		{
			return authorized() && targetsEligible() ? pinRecipients(_targets, stockTargets) : List.of();
		}

		public boolean mayPublish(List<WorldObject> nativeTargets)
		{
			return authorized() && _caster.player().getCurrentSkill() == _holder
				&& sameRecipients(_targets, nativeTargets) && targetsEligible()
				&& _recipients.stream().allMatch(recipient -> PlayerNativeWork.current(recipient.owner()) != null);
		}

		public void initialMp(double requested, double before, double after)
		{
			_initialMp = new MpDebit(requested, before, after);
		}

		public void hitMp(double requested, double before, double after)
		{
			_hitMp = new MpDebit(requested, before, after);
		}

		public void published(int nativeCastMillis)
		{
			_startedNanos = System.nanoTime();
			_nativeCastMillis = nativeCastMillis;
			_published = true;
		}

		public synchronized boolean beginHit(List<WorldObject> nativeTargets)
		{
			if (!_published || _impactOrigin != null || !mayPublish(nativeTargets) || _caster.player().isCastingSimultaneouslyNow())
			{
				return false;
			}
			final Player caster = _caster.player();
			if (_party != null && _recipients.stream().anyMatch(recipient -> recipient.player() != caster
				&& !GeoEngine.getInstance().canSeeTarget(caster, recipient.player())))
			{
				return false;
			}
			_impactOrigin = PhantomNativeTravelActions.point(caster);
			_resolving = true;
			return true;
		}

		public void impact(Runnable nativeSkill)
		{
			if (_impactOrigin == null || IMPACT.get() != null)
			{
				return;
			}
			IMPACT.set(this);
			try
			{
				nativeSkill.run();
			}
			finally
			{
				IMPACT.remove();
			}
		}

		public void endHit()
		{
			final Receipt receipt;
			synchronized (this)
			{
				_resolving = false;
				receipt = finish(!_cancelRequested && _arrivals.size() == _recipients.size() ? Status.ARRIVED : Status.INTERRUPTED);
			}
			if (_audit != null && _audit == AUDIT.get() && receipt != null && !receipt.arrivals().isEmpty())
			{
				publishArrivals(receipt);
			}
		}

		private void applyRecipient(Creature target, Runnable nativeEffect)
		{
			final var recipient = _recipients.stream().filter(candidate -> candidate.player() == target).findFirst().orElse(null);
			if (recipient == null || !authorized() || !eligible(recipient) || _effectRecipient != null
				|| PlayerNativeWork.current(recipient.owner()) == null || !_effects.add(recipient.player()))
			{
				return;
			}
			final Player player = recipient.player();
			if (_party != null && !player.isInsideRadius3D(_impactOrigin.x(), _impactOrigin.y(), _impactOrigin.z(), _skill.getAffectRange()))
			{
				return;
			}
			final var before = PhantomNativeTravelActions.point(player);
			_effectRecipient = player;
			try
			{
				nativeEffect.run();
				if (!player.isTeleporting()) { return; }
				player.onTeleported();
				final var after = PhantomNativeTravelActions.point(player);
				if (recipient.current() && !player.isTeleporting() && after.instanceId() == 0 && !before.equals(after))
				{
					_arrivals.add(new Arrival(player.getObjectId(), recipient.epoch(), before, after, System.nanoTime()));
				}
			}
			finally
			{
				_effectRecipient = null;
			}
		}

		public Status status()
		{
			if (_receipt == null) { reconcileCancelledSlot(); }
			final var receipt = _receipt;
			return receipt == null ? Status.PENDING : receipt.status();
		}

		private synchronized void reconcileCancelledSlot()
		{
			final Player caster = _caster.player();
			if (_receipt == null && _published && !_resolving
				&& (!_caster.current() || caster.getCurrentSkill() != _holder || !caster.isCastingNow()))
			{
				nativeCancelled();
			}
		}

		public Receipt receipt()
		{
			return _receipt;
		}

		/** Only interrupt this cast. A parallel native slot must drain under its own owner. */
		public void cancel()
		{
			if (_receipt != null)
			{
				return;
			}
			_cancelRequested = true;
			final Player caster = _caster.player();
			PlayerNativeWork.run(caster, "phantom-learned-escape-cancel", () ->
			{
				if (!_resolving && _caster.current() && caster.getCurrentSkill() == _holder && caster.isCastingNow()
					&& !caster.isCastingSimultaneouslyNow() && caster.getQueuedSkill() == null)
				{
					caster.abortCast();
				}
			});
		}

		public synchronized void nativeCancelled()
		{
			// Native teleport clears the caster's own cast slot. The same scoped effect
			// still owns the arrival and must finish before its receipt becomes terminal.
			if (IMPACT.get() == this && _effectRecipient == _caster.player())
			{
				return;
			}
			_cancelRequested = true;
			if (!_resolving)
			{
				finish(Status.INTERRUPTED);
			}
		}

		private Receipt finish(Status status)
		{
			if (_receipt != null) { return null; }
			_receipt = new Receipt(_skill.getId(), _skill.getLevel(), _caster.player().getObjectId(), _caster.epoch(),
				_nativeCastMillis, _startedNanos, System.nanoTime(), _initialMp, _hitMp, _arrivals, status);
			return _receipt;
		}

		private void publishArrivals(Receipt receipt)
		{
			try
			{
				final var approved = new LinkedHashMap<Integer, Long>();
				for (Recipient recipient : _recipients)
				{
					approved.put(recipient.player().getObjectId(), recipient.epoch());
				}
				_audit.arrived().accept(new AuditReceipt(receipt, _caster.instanceId(), approved));
			}
			catch (RuntimeException | Error failure)
			{
				try { _audit.failed().accept("NATIVE_ESCAPE_OBSERVER_FAILED"); }
				catch (RuntimeException | Error ignored) { }
			}
		}
	}
}
