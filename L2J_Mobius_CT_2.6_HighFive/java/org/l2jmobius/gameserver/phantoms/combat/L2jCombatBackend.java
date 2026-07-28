/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.data.xml.MapRegionData;
import org.l2jmobius.gameserver.handler.IItemHandler;
import org.l2jmobius.gameserver.handler.ItemHandler;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.WorldRegion;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.instance.EventMonster;
import org.l2jmobius.gameserver.model.actor.instance.GrandBoss;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.actor.instance.RaidBoss;
import org.l2jmobius.gameserver.model.actor.enums.player.TeleportWhereType;
import org.l2jmobius.gameserver.model.item.Weapon;
import org.l2jmobius.gameserver.model.item.enums.ItemLocation;
import org.l2jmobius.gameserver.model.item.enums.ShotType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.item.type.ActionType;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.skill.holders.SkillUseHolder;
import org.l2jmobius.gameserver.model.skill.targets.TargetType;
import org.l2jmobius.gameserver.model.zone.ZoneId;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ActionOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ActorSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.LootCandidate;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.RespawnOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ShotOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.TargetSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ThreatObservation;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeQuery;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.ActionLease;

public final class L2jCombatBackend implements PhantomCombatBackend
{
	private static final int MAXIMUM_ACQUISITION_DISTANCE = 2000;
	private static final int MAXIMUM_LOOT_DISTANCE = 300;
	private final PhantomMaterializationService _materializationService;
	private final Supplier<PhantomGameKnowledgeQuery> _knowledgeSupplier;

	public L2jCombatBackend(PhantomMaterializationService materializationService, Supplier<PhantomGameKnowledgeQuery> knowledgeSupplier)
	{
		_materializationService = Objects.requireNonNull(materializationService, "materializationService");
		_knowledgeSupplier = Objects.requireNonNull(knowledgeSupplier, "knowledgeSupplier");
	}

	@Override
	public PhantomCombatActorLease tryAcquireActor(long profileId)
	{
		return _materializationService.tryAcquireAction(profileId).map(lease -> new L2jActorLease(lease, _knowledgeSupplier)).orElse(null);
	}

	private static final class L2jActorLease implements PhantomCombatActorLease
	{
		private final ActionLease _materializationLease;
		private final Player _player;
		private final Supplier<PhantomGameKnowledgeQuery> _knowledgeSupplier;
		private final AtomicBoolean _closed = new AtomicBoolean();

		private L2jActorLease(ActionLease materializationLease, Supplier<PhantomGameKnowledgeQuery> knowledgeSupplier)
		{
			_materializationLease = materializationLease;
			_player = materializationLease.player();
			_knowledgeSupplier = knowledgeSupplier;
		}

		@Override
		public ActorSnapshot actorSnapshot()
		{
			final SkillUseHolder currentSkill = _player.getCurrentSkill();
			final WorldObject target = _player.getTarget();
			return new ActorSnapshot(_player.getObjectId(), _player.getActiveClass(), _player.getInstanceId(), _player.getCurrentHp(), _player.getMaxHp(), _player.getCurrentMp(), _player.getMaxMp(), _player.isDead(), _player.isAlikeDead(), _player.isAttackingNow(), _player.isCastingNow(), _player.isMoving(), target == null ? 0 : target.getObjectId(), _player.hasAI() ? _player.getAI().getIntention().name() : Intention.IDLE.name(), currentSkill == null ? 0 : currentSkill.getSkillId(), currentSkill == null ? 0 : currentSkill.getSkillLevel());
		}

		@Override
		public TargetSnapshot targetSnapshot(int targetObjectId)
		{
			final WorldObject object = World.getInstance().findObject(targetObjectId);
			if (!(object instanceof Monster monster))
			{
				return null;
			}
			return targetSnapshot(monster);
		}

		private TargetSnapshot targetSnapshot(Monster monster)
		{
			final boolean normalMonster = isNormalMonster(monster) && monster.isMortal() && monster.isSpawned();
			final PhantomGameKnowledgeQuery knowledge = _knowledgeSupplier.get();
			final boolean knowledgeMonster = (knowledge != null) && knowledge.findNpc(monster.getId()).filter(fact -> fact.kind() == NpcKind.MONSTER).isPresent();
			final WorldRegion actorRegion = _player.getWorldRegion();
			final boolean surrounding = (actorRegion != null) && actorRegion.isSurroundingRegion(monster.getWorldRegion());
			final boolean restrictedActor = _player.isOnEvent() || _player.isInOlympiadMode() || _player.isInDuel() || _player.isInSiege() || _player.isInsideZone(ZoneId.SIEGE);
			final boolean peaceRestricted = restrictedActor || _player.isInsideZone(ZoneId.PEACE) || monster.isInsideZone(ZoneId.PEACE);
			return new TargetSnapshot(monster.getObjectId(), monster.getId(), monster.getInstanceId(), monster.getCurrentHp(), monster.getMaxHp(), monster.isDead(), monster.isAlikeDead(), monster.isTargetable(), monster.isAttackable() && monster.isAutoAttackable(_player), monster.isInvul(), normalMonster, knowledgeMonster, distance(_player, monster), peaceRestricted, surrounding);
		}

		@Override
		public boolean supportsSkill(SelectedSkill selected, PhantomCombatMode mode)
		{
			final Skill skill = _player.getKnownSkill(selected.skillId());
			if ((skill == null) || (skill.getLevel() != selected.skillLevel()) || skill.isPassive() || skill.isToggle() || (_player.isTransformed()) || (skill.getTargetType() != TargetType.ONE))
			{
				return false;
			}
			return mode.magic() ? skill.isMagic() : skill.isPhysical();
		}

		@Override
		public List<ThreatObservation> observedAttackers(int limit)
		{
			final TreeMap<Integer, ThreatObservation> observations = new TreeMap<>();
			final ActorSnapshot actor = actorSnapshot();
			for (Creature creature : _player.getAttackByList())
			{
				if ((creature instanceof Monster monster) && (monster.getTarget() == _player))
				{
					final TargetSnapshot target = targetSnapshot(monster);
					if ((target != null) && target.validFor(actor, MAXIMUM_ACQUISITION_DISTANCE))
					{
						observations.put(monster.getObjectId(), new ThreatObservation(monster.getObjectId(), 1));
						if (observations.size() > limit)
						{
							observations.pollLastEntry();
						}
					}
				}
			}
			return List.copyOf(observations.values());
		}

		@Override
		public List<LootCandidate> lootCandidates(int limit, int maximumDistance)
		{
			final TreeMap<Integer, LootCandidate> candidates = new TreeMap<>();
			final WorldRegion region = _player.getWorldRegion();
			if (region == null)
			{
				return List.of();
			}
			for (WorldRegion surrounding : region.getSurroundingRegions())
			{
				for (WorldObject object : surrounding.getVisibleObjects())
				{
					if (!(object instanceof Item item) || !eligibleLoot(item, maximumDistance))
					{
						continue;
					}
					candidates.put(item.getObjectId(), new LootCandidate(item.getObjectId()));
					if (candidates.size() > limit)
					{
						candidates.pollLastEntry();
					}
				}
			}
			return List.copyOf(candidates.values());
		}

		@Override
		public ShotOutcome activateShot(PhantomCombatMode mode)
		{
			final boolean magic = mode.magic();
			if (charged(magic))
			{
				return ShotOutcome.ACTIVATED;
			}
			try
			{
				_player.rechargeShots(!magic, magic);
				if (charged(magic))
				{
					return ShotOutcome.ACTIVATED;
				}

				final Weapon weapon = _player.getActiveWeaponItem();
				if (weapon == null)
				{
					return ShotOutcome.UNAVAILABLE;
				}
				final List<Item> inventory = new ArrayList<>(_player.getInventory().getItems());
				inventory.sort(Comparator.comparing((Item item) -> (item.getEtcItem() == null) || !item.getEtcItem().isBlessed()).thenComparingInt(Item::getId).thenComparingInt(Item::getObjectId));
				for (Item item : inventory)
				{
					if ((item.getCount() <= 0) || (item.getEtcItem() == null) || !_player.getInventory().canManipulateWithItemId(item.getId()) || (item.getTemplate().getCrystalType() != weapon.getCrystalTypePlus()))
					{
						continue;
					}
					final ActionType action = item.getTemplate().getDefaultAction();
					if ((magic && (action != ActionType.SPIRITSHOT)) || (!magic && (action != ActionType.SOULSHOT)))
					{
						continue;
					}
					final IItemHandler handler = ItemHandler.getInstance().getHandler(item.getEtcItem());
					if ((handler != null) && handler.onItemUse(_player, item, false) && charged(magic))
					{
						return ShotOutcome.ACTIVATED;
					}
				}
				return ShotOutcome.UNAVAILABLE;
			}
			catch (RuntimeException e)
			{
				return ShotOutcome.FAILED;
			}
		}

		@Override
		public ActionOutcome attack(int targetObjectId)
		{
			final WorldObject object = World.getInstance().findObject(targetObjectId);
			if (!(object instanceof Monster target))
			{
				return ActionOutcome.REJECTED;
			}
			final TargetSnapshot snapshot = targetSnapshot(target);
			if (!snapshot.validFor(actorSnapshot(), MAXIMUM_ACQUISITION_DISTANCE))
			{
				return ActionOutcome.REJECTED;
			}
			if (_player.hasAI() && (_player.getAI().getIntention() == Intention.ATTACK) && (_player.getAI().getAttackTarget() == target))
			{
				return ActionOutcome.ALREADY_OWNED;
			}
			_player.setTarget(target);
			_player.getAI().setIntention(Intention.ATTACK, target);
			return ActionOutcome.ISSUED;
		}

		@Override
		public ActionOutcome cast(int targetObjectId, SelectedSkill selected)
		{
			final WorldObject object = World.getInstance().findObject(targetObjectId);
			if (!(object instanceof Monster target))
			{
				return ActionOutcome.REJECTED;
			}
			final TargetSnapshot snapshot = targetSnapshot(target);
			if (!snapshot.validFor(actorSnapshot(), MAXIMUM_ACQUISITION_DISTANCE) || !supportsSkill(selected, selectedMode(selected)))
			{
				return ActionOutcome.REJECTED;
			}
			final Skill skill = _player.getKnownSkill(selected.skillId());
			if (_player.isSkillDisabled(skill) || !_player.checkDoCastConditions(skill))
			{
				return ActionOutcome.UNAVAILABLE;
			}
			final SkillUseHolder current = _player.getCurrentSkill();
			if (_player.hasAI() && (_player.getAI().getIntention() == Intention.CAST) && (_player.getAI().getCastTarget() == target) && (current != null) && (current.getSkillId() == selected.skillId()) && (current.getSkillLevel() == selected.skillLevel()))
			{
				return ActionOutcome.ALREADY_OWNED;
			}
			_player.setTarget(target);
			_player.getAI().setIntention(Intention.CAST, skill, target);
			return ActionOutcome.ISSUED;
		}

		private PhantomCombatMode selectedMode(SelectedSkill selected)
		{
			final Skill skill = _player.getKnownSkill(selected.skillId());
			return (skill != null) && skill.isMagic() ? PhantomCombatMode.RANGED_MAGIC : PhantomCombatMode.RANGED_PHYSICAL;
		}

		@Override
		public ActionOutcome pickUp(int objectId)
		{
			final WorldObject object = World.getInstance().findObject(objectId);
			if (!(object instanceof Item item) || !eligibleLoot(item, MAXIMUM_LOOT_DISTANCE))
			{
				return ActionOutcome.REJECTED;
			}
			_player.getAI().setIntention(Intention.PICK_UP, item);
			return ActionOutcome.ISSUED;
		}

		@Override
		public void cancelOwnedAction(int targetObjectId, SelectedSkill selectedSkill)
		{
			if (!_player.hasAI())
			{
				return;
			}
			final WorldObject selectedTarget = _player.getTarget();
			final boolean ownsTarget = (selectedTarget != null) && (selectedTarget.getObjectId() == targetObjectId);
			if ((_player.getAI().getIntention() == Intention.ATTACK) && (_player.getAI().getAttackTarget() != null) && (_player.getAI().getAttackTarget().getObjectId() == targetObjectId))
			{
				_player.abortAttack();
				_player.getAI().setIntention(Intention.IDLE);
				if (ownsTarget)
				{
					_player.setTarget(null);
				}
				return;
			}
			final SkillUseHolder current = _player.getCurrentSkill();
			if ((_player.getAI().getIntention() == Intention.CAST) && (_player.getAI().getCastTarget() != null) && (_player.getAI().getCastTarget().getObjectId() == targetObjectId) && (selectedSkill != null) && (current != null) && (current.getSkillId() == selectedSkill.skillId()) && (current.getSkillLevel() == selectedSkill.skillLevel()))
			{
				_player.abortCast();
				_player.getAI().setIntention(Intention.IDLE);
				if (ownsTarget)
				{
					_player.setTarget(null);
				}
			}
		}

		@Override
		public RespawnOutcome respawnTown()
		{
			if (!_player.isDead() || !_player.canRevive() || _player.isFakeDeath() || _player.isJailed() || _player.isFestivalParticipant() || _player.isOnEvent() || _player.isInOlympiadMode() || _player.isInDuel() || _player.isInSiege() || _player.isInsideZone(ZoneId.SIEGE) || (_player.getInstanceId() != 0) || _player.isPendingRevive())
			{
				return RespawnOutcome.REJECTED;
			}
			final Location location = MapRegionData.getInstance().getTeleToLocation(_player, TeleportWhereType.TOWN);
			if (location == null)
			{
				return RespawnOutcome.RETRY;
			}
			_player.setInstanceId(0);
			_player.setIn7sDungeon(false);
			_player.setIsPendingRevive(true);
			_player.teleToLocation(location, true);
			if (_player.hasHeadlessOutboundSession() && _player.isTeleporting())
			{
				_player.onTeleported();
			}
			return RespawnOutcome.COMPLETED;
		}

		@Override
		public void close()
		{
			if (_closed.compareAndSet(false, true))
			{
				_materializationLease.close();
			}
		}

		private boolean eligibleLoot(Item item, int maximumDistance)
		{
			return item.isSpawned() && (item.getItemLocation() == ItemLocation.VOID) && (item.getInstanceId() == _player.getInstanceId()) && (distance(_player, item) <= maximumDistance) && (!item.isProtected() || (item.getOwnerId() == _player.getObjectId()));
		}

		private boolean charged(boolean magic)
		{
			return magic ? (_player.isChargedShot(ShotType.SPIRITSHOTS) || _player.isChargedShot(ShotType.BLESSED_SPIRITSHOTS)) : _player.isChargedShot(ShotType.SOULSHOTS);
		}
	}

	private static boolean isNormalMonster(Monster monster)
	{
		return !(monster instanceof RaidBoss) && !(monster instanceof GrandBoss) && !(monster instanceof EventMonster) && !monster.isRaid() && !monster.isRaidMinion() && !monster.isFakePlayer();
	}

	private static double distance(WorldObject left, WorldObject right)
	{
		return left.calculateDistance2D(right);
	}
}
