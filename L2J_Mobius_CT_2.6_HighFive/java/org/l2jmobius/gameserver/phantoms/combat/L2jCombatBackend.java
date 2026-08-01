/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.AcquisitionSkillKind;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.AcquisitionActorPosition;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.AcquisitionTargetSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ActorSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ExternalOwnedAction;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.LootCandidate;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.LootObservation;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.PlayableSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.RespawnOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ShotOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.TargetSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ThreatObservation;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeQuery;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.ActionLease;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionCatalog;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.CapabilityRule;

public final class L2jCombatBackend implements PhantomCombatBackend
{
	private static final int MAXIMUM_ACQUISITION_DISTANCE = 2000;
	private static final int MAXIMUM_LOOT_DISTANCE = 300;
	private static final Set<String> PARTY_SUPPORT_CAPABILITIES = Set.of("combat.heal", "combat.recharge", "combat.resurrection", "combat.buff", "combat.song", "combat.dance");
	private static final Set<TargetType> PARTY_SUPPORT_TARGET_TYPES = Set.of(TargetType.ONE, TargetType.SELF, TargetType.PARTY, TargetType.PARTY_MEMBER, TargetType.PARTY_NOTME, TargetType.PARTY_OTHER, TargetType.TARGET_PARTY, TargetType.PC_BODY, TargetType.AURA_FRIENDLY, TargetType.AREA_FRIENDLY);
	private final PhantomMaterializationService _materializationService;
	private final Supplier<PhantomGameKnowledgeQuery> _knowledgeSupplier;
	private final Supplier<PhantomProgressionCatalog> _progressionCatalog;

	public L2jCombatBackend(PhantomMaterializationService materializationService, Supplier<PhantomGameKnowledgeQuery> knowledgeSupplier)
	{
		this(materializationService, knowledgeSupplier, () -> null);
	}

	public L2jCombatBackend(PhantomMaterializationService materializationService, Supplier<PhantomGameKnowledgeQuery> knowledgeSupplier, Supplier<PhantomProgressionCatalog> progressionCatalog)
	{
		_materializationService = Objects.requireNonNull(materializationService, "materializationService");
		_knowledgeSupplier = Objects.requireNonNull(knowledgeSupplier, "knowledgeSupplier");
		_progressionCatalog = Objects.requireNonNull(progressionCatalog, "progressionCatalog");
	}

	@Override
	public PhantomCombatActorLease tryAcquireActor(long profileId)
	{
		return _materializationService.tryAcquireAction(profileId).map(lease -> new L2jActorLease(lease, _knowledgeSupplier, _progressionCatalog)).orElse(null);
	}

	private static final class L2jActorLease implements PhantomCombatActorLease
	{
		private final ActionLease _materializationLease;
		private final Player _player;
		private final Supplier<PhantomGameKnowledgeQuery> _knowledgeSupplier;
		private final Supplier<PhantomProgressionCatalog> _progressionCatalog;
		private final AtomicBoolean _closed = new AtomicBoolean();

		private L2jActorLease(ActionLease materializationLease, Supplier<PhantomGameKnowledgeQuery> knowledgeSupplier, Supplier<PhantomProgressionCatalog> progressionCatalog)
		{
			_materializationLease = materializationLease;
			_player = materializationLease.player();
			_knowledgeSupplier = knowledgeSupplier;
			_progressionCatalog = progressionCatalog;
		}

		@Override
		public ActorSnapshot actorSnapshot()
		{
			final SkillUseHolder currentSkill = _player.getCurrentSkill();
			final WorldObject target = _player.getTarget();
			return new ActorSnapshot(_player.getObjectId(), _player.getActiveClass(), _player.getInstanceId(), _player.getCurrentHp(), _player.getMaxHp(), _player.getCurrentMp(), _player.getMaxMp(), _player.getCurrentCp(), _player.getMaxCp(), _player.isDead(), _player.isAlikeDead(), _player.isAttackingNow(), _player.isCastingNow(), _player.isMoving(), target == null ? 0 : target.getObjectId(), _player.hasAI() ? _player.getAI().getIntention().name() : Intention.IDLE.name(), currentSkill == null ? 0 : currentSkill.getSkillId(), currentSkill == null ? 0 : currentSkill.getSkillLevel());
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

		@Override
		public AcquisitionTargetSnapshot acquisitionTargetSnapshot(int targetObjectId)
		{
			final WorldObject object = World.getInstance().findObject(targetObjectId);
			return object instanceof Monster monster ? acquisitionSnapshot(monster) : null;
		}

		@Override
		public List<AcquisitionTargetSnapshot> acquisitionTargets(int npcId, int limit, int maximumDistance)
		{
			if ((npcId <= 0) || (limit < 1) || (limit > 8) || (maximumDistance < 1) || (maximumDistance > MAXIMUM_ACQUISITION_DISTANCE))
			{
				return List.of();
			}
			final WorldRegion region = _player.getWorldRegion();
			if (region == null)
			{
				return List.of();
			}
			final ActorSnapshot actor = actorSnapshot();
			final TreeMap<Integer, AcquisitionTargetSnapshot> result = new TreeMap<>();
			for (WorldRegion surrounding : region.getSurroundingRegions())
			{
				for (WorldObject object : surrounding.getVisibleObjects())
				{
					if (object instanceof Monster monster)
					{
						final AcquisitionTargetSnapshot snapshot = acquisitionSnapshot(monster);
						if ((snapshot != null) && snapshot.liveValidFor(actor, npcId, maximumDistance))
						{
							result.put(snapshot.objectId(), snapshot);
							if (result.size() > limit)
							{
								result.pollLastEntry();
							}
						}
					}
				}
			}
			return List.copyOf(result.values());
		}

		@Override
		public long acquisitionInventoryCount(int itemId)
		{
			return itemId > 0 ? _player.getInventory().getInventoryItemCount(itemId, -1) : -1;
		}

		@Override
		public int acquisitionLevel()
		{
			return _player.getLevel();
		}

		@Override
		public AcquisitionActorPosition acquisitionPosition()
		{
			return new AcquisitionActorPosition(_player.getX(), _player.getY(), _player.getZ(), _player.getInstanceId());
		}

		@Override
		public int knownSkillLevel(int skillId)
		{
			final Skill skill = skillId > 0 ? _player.getKnownSkill(skillId) : null;
			return skill == null ? 0 : skill.getLevel();
		}

		@Override
		public PlayableSnapshot playableSnapshot(int objectId)
		{
			final WorldObject object = World.getInstance().findObject(objectId);
			if (!(object instanceof Player player))
			{
				return null;
			}
			final WorldObject target = player.getTarget();
			final List<Integer> attackers = player.getAttackByList().stream().filter(Creature::isMonster).map(Creature::getObjectId).distinct().sorted().limit(32).toList();
			return new PlayableSnapshot(player.getObjectId(), player.getActiveClass(), player.getInstanceId(), player.getX(), player.getY(), player.getZ(), player.getCurrentHp(), player.getMaxHp(), player.getCurrentMp(), player.getMaxMp(), player.getCurrentCp(), player.getMaxCp(), player.isDead(), player.isAlikeDead(), player.isCastingNow(), player.isMoving(), target == null ? 0 : target.getObjectId(), attackers);
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

		private AcquisitionTargetSnapshot acquisitionSnapshot(Monster monster)
		{
			final TargetSnapshot target = targetSnapshot(monster);
			if (target == null)
			{
				return null;
			}
			return new AcquisitionTargetSnapshot(target.objectId(), target.npcId(), target.instanceId(), target.distance(), target.dead(), target.alikeDead(), target.targetable(), target.attackable(), target.invulnerable(), target.normalMonster(), target.knowledgeMonster(), target.peaceRestricted(), target.surroundingRegion(), monster.isSpoiled(), monster.getSpoilerObjectId(), monster.isSweepActive(), monster.checkSpoilOwner(_player, false));
		}

		@Override
		public boolean supportsSkill(SelectedSkill selected, PhantomCombatMode mode)
		{
			final Skill skill = _player.getKnownSkill(selected.skillId());
			if ((skill == null) || (skill.getLevel() != selected.skillLevel()))
			{
				return false;
			}
			return PhantomCombatSkillSafety.supports(skill, mode, _player.isTransformed());
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
		public List<ThreatObservation> observedAttackers(int protectedObjectId, int limit)
		{
			if ((limit < 1) || (limit > 32))
			{
				return List.of();
			}
			final WorldObject object = World.getInstance().findObject(protectedObjectId);
			if (!(object instanceof Player protectedPlayer) || (_player.getParty() == null) || (_player.getParty() != protectedPlayer.getParty()))
			{
				return List.of();
			}
			final TreeMap<Integer, ThreatObservation> observations = new TreeMap<>();
			final ActorSnapshot actor = actorSnapshot();
			for (Creature creature : protectedPlayer.getAttackByList())
			{
				if ((creature instanceof Monster monster) && (monster.getTarget() == protectedPlayer))
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
					candidates.put(item.getObjectId(), new LootCandidate(item.getObjectId(), item.getId(), item.getCount(), _player.getInventory().getInventoryItemCount(item.getId(), -1)));
					if (candidates.size() > limit)
					{
						candidates.pollLastEntry();
					}
				}
			}
			return List.copyOf(candidates.values());
		}

		@Override
		public LootObservation observeLoot(LootCandidate candidate)
		{
			final Item exactInventoryItem = _player.getInventory().getItemByObjectId(candidate.worldObjectId());
			if ((exactInventoryItem != null) && (exactInventoryItem.getOwnerId() == _player.getObjectId()) && ((exactInventoryItem.getItemLocation() == ItemLocation.INVENTORY) || (exactInventoryItem.getItemLocation() == ItemLocation.PAPERDOLL)))
			{
				return LootObservation.ACQUIRED_BY_ACTOR;
			}

			final long inventoryCount = _player.getInventory().getInventoryItemCount(candidate.itemId(), -1);
			final WorldObject object = World.getInstance().findObject(candidate.worldObjectId());
			if (object instanceof Item item)
			{
				if (item.isSpawned() && (item.getItemLocation() == ItemLocation.VOID))
				{
					return eligibleLoot(item, MAXIMUM_LOOT_DISTANCE) ? LootObservation.PENDING : LootObservation.INELIGIBLE;
				}
			}
			if ((inventoryCount >= candidate.actorInventoryCountBefore()) && ((inventoryCount - candidate.actorInventoryCountBefore()) >= candidate.groundCount()))
			{
				return LootObservation.ACQUIRED_BY_ACTOR;
			}
			return LootObservation.LOST_WITHOUT_ACQUISITION;
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
		public ActionOutcome cast(int targetObjectId, SelectedSkill selected, PhantomCombatMode mode)
		{
			final WorldObject object = World.getInstance().findObject(targetObjectId);
			if (!(object instanceof Monster target))
			{
				return ActionOutcome.REJECTED;
			}
			final TargetSnapshot snapshot = targetSnapshot(target);
			if (!snapshot.validFor(actorSnapshot(), MAXIMUM_ACQUISITION_DISTANCE) || !supportsSkill(selected, mode))
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

		@Override
		public ActionOutcome castAcquisition(int targetObjectId, SelectedSkill selected, AcquisitionSkillKind kind)
		{
			final WorldObject object = World.getInstance().findObject(targetObjectId);
			if (!(object instanceof Monster target) || (selected == null) || (kind == null))
			{
				return ActionOutcome.REJECTED;
			}
			final AcquisitionTargetSnapshot snapshot = acquisitionSnapshot(target);
			final ActorSnapshot actor = actorSnapshot();
			final boolean valid = (snapshot != null) && (kind == AcquisitionSkillKind.SPOIL ? snapshot.liveValidFor(actor, target.getId(), MAXIMUM_ACQUISITION_DISTANCE) && (!snapshot.spoiled() || (snapshot.spoilerObjectId() == actor.objectId())) : snapshot.sweepValidFor(actor, target.getId(), MAXIMUM_ACQUISITION_DISTANCE));
			final Skill skill = _player.getKnownSkill(selected.skillId());
			final String capabilityKey = kind == AcquisitionSkillKind.SPOIL ? "profession.spoil" : "profession.sweep";
			if (!valid || !supportsAcquisitionSkill(selected, capabilityKey) || (skill == null) || (skill.getLevel() < selected.skillLevel()) || skill.isPassive() || skill.isToggle())
			{
				return ActionOutcome.REJECTED;
			}
			if ((kind == AcquisitionSkillKind.SPOIL) && snapshot.spoiled() && (snapshot.spoilerObjectId() == actor.objectId()))
			{
				return ActionOutcome.ALREADY_OWNED;
			}
			_player.setTarget(target);
			if (_player.isSkillDisabled(skill) || !_player.checkDoCastConditions(skill) || !skill.checkCondition(_player, target, false))
			{
				return ActionOutcome.UNAVAILABLE;
			}
			final SkillUseHolder current = _player.getCurrentSkill();
			if (_player.hasAI() && (_player.getAI().getIntention() == Intention.CAST) && (_player.getAI().getCastTarget() == target) && (current != null) && (current.getSkillId() == selected.skillId()) && (current.getSkillLevel() == selected.skillLevel()))
			{
				return ActionOutcome.ALREADY_OWNED;
			}
			_player.getAI().setIntention(Intention.CAST, skill, target);
			return ActionOutcome.ISSUED;
		}

		private boolean supportsAcquisitionSkill(SelectedSkill selected, String capabilityKey)
		{
			final PhantomProgressionCatalog catalog = _progressionCatalog.get();
			return (catalog != null) && catalog.capabilities(_player.getActiveClass()).stream().filter(rule -> capabilityKey.equals(rule.capabilityKey()) && (rule.actionSkill().skillId() == selected.skillId()) && (rule.actionSkill().skillLevel() == selected.skillLevel()) && rule.requiredItems().isEmpty() && rule.requiredEquipmentFamilies().isEmpty()).anyMatch(rule -> rule.evidenceSkills().stream().allMatch(evidence -> knownSkillLevel(evidence.skillId()) >= evidence.skillLevel()));
		}

		@Override
		public ActionOutcome castSupport(PhantomPartySupportAction action)
		{
			if ((action == null) || !PARTY_SUPPORT_CAPABILITIES.contains(action.capabilityKey()))
			{
				return ActionOutcome.REJECTED;
			}
			final PhantomProgressionCatalog catalog = _progressionCatalog.get();
			final CapabilityRule rule = catalog == null ? null : catalog.capabilities(_player.getActiveClass()).stream().filter(candidate -> candidate.capabilityKey().equals(action.capabilityKey()) && candidate.variantKey().equals(action.variantKey()) && candidate.targetScope().name().equals(action.targetScope()) && (candidate.actionSkill().skillId() == action.skill().skillId()) && (candidate.actionSkill().skillLevel() == action.skill().skillLevel())).findFirst().orElse(null);
			if (rule == null)
			{
				return ActionOutcome.REJECTED;
			}
			final WorldObject object = World.getInstance().findObject(action.targetObjectId());
			if (!(object instanceof Player target) || (_player.getInstanceId() != target.getInstanceId()))
			{
				return ActionOutcome.REJECTED;
			}
			final boolean self = target == _player;
			final boolean targetScopeAllowed = switch (rule.targetScope())
			{
				case SELF -> self;
				case SINGLE_TARGET, PARTY, ALLY -> self || ((_player.getParty() != null) && (_player.getParty() == target.getParty()));
				default -> false;
			};
			if (!targetScopeAllowed || (!self && ((_player.getParty() == null) || (_player.getParty() != target.getParty()))))
			{
				return ActionOutcome.REJECTED;
			}
			final Skill skill = _player.getKnownSkill(action.skill().skillId());
			if ((skill == null) || (skill.getLevel() != action.skill().skillLevel()) || skill.isPassive() || skill.isToggle() || skill.isDebuff() || skill.hasNegativeEffect() || !PARTY_SUPPORT_TARGET_TYPES.contains(skill.getTargetType()))
			{
				return ActionOutcome.REJECTED;
			}
			final boolean resurrection = "combat.resurrection".equals(action.capabilityKey());
			if (resurrection != target.isDead())
			{
				return ActionOutcome.REJECTED;
			}
			if (!resurrection && target.isAlikeDead())
			{
				return ActionOutcome.REJECTED;
			}
			final int castRange = Math.max(0, skill.getCastRange());
			if ((target != _player) && (distance(_player, target) > Math.max(200, castRange + 100)))
			{
				return ActionOutcome.UNAVAILABLE;
			}
			if (_player.isSkillDisabled(skill) || !_player.checkDoCastConditions(skill) || !skill.checkCondition(_player, target, false))
			{
				return ActionOutcome.UNAVAILABLE;
			}
			final SkillUseHolder current = _player.getCurrentSkill();
			if (_player.hasAI() && (_player.getAI().getIntention() == Intention.CAST) && (_player.getAI().getCastTarget() == target) && (current != null) && (current.getSkillId() == action.skill().skillId()) && (current.getSkillLevel() == action.skill().skillLevel()))
			{
				return ActionOutcome.ALREADY_OWNED;
			}
			_player.setTarget(target);
			_player.getAI().setIntention(Intention.CAST, skill, target);
			return ActionOutcome.ISSUED;
		}

		@Override
		public ActionOutcome moveTo(int x, int y, int z, int instanceId)
		{
			final ActorSnapshot actor = actorSnapshot();
			if ((instanceId != actor.instanceId()) || actor.dead() || actor.casting() || actor.attacking())
			{
				return ActionOutcome.REJECTED;
			}
			final long dx = (long) _player.getX() - x;
			final long dy = (long) _player.getY() - y;
			if (((dx * dx) + (dy * dy)) <= 2500)
			{
				return ActionOutcome.ALREADY_OWNED;
			}
			_player.getAI().setIntention(Intention.MOVE_TO, new Location(x, y, z));
			return ActionOutcome.ISSUED;
		}

		@Override
		public ActionOutcome pickUp(int objectId)
		{
			final WorldObject object = World.getInstance().findObject(objectId);
			if (!(object instanceof Item item) || !eligibleLoot(item, MAXIMUM_LOOT_DISTANCE))
			{
				return ActionOutcome.REJECTED;
			}
			if (_player.hasAI() && (_player.getAI().getIntention() == Intention.PICK_UP) && (_player.getTarget() == item))
			{
				return ActionOutcome.ALREADY_OWNED;
			}
			_player.setTarget(item);
			_player.getAI().setIntention(Intention.PICK_UP, item);
			return ActionOutcome.ISSUED;
		}

		@Override
		public void cancelOwnedAction(PhantomOwnedAction action)
		{
			if (!_player.hasAI())
			{
				return;
			}
			boolean cancelledOwnedAction = false;
			final WorldObject selectedTarget = _player.getTarget();
			final int selectedTargetObjectId = selectedTarget == null ? 0 : selectedTarget.getObjectId();
			if ((_player.getAI().getIntention() == Intention.ATTACK) && (_player.getAI().getAttackTarget() != null) && (_player.getAI().getAttackTarget().getObjectId() == action.combatTargetObjectId()))
			{
				_player.abortAttack();
				_player.getAI().setIntention(Intention.IDLE);
				cancelledOwnedAction = true;
			}
			final SkillUseHolder current = _player.getCurrentSkill();
			final SelectedSkill selectedSkill = action.selectedSkill();
			if ((_player.getAI().getIntention() == Intention.CAST) && (_player.getAI().getCastTarget() != null) && (_player.getAI().getCastTarget().getObjectId() == action.combatTargetObjectId()) && (selectedSkill != null) && (current != null) && (current.getSkillId() == selectedSkill.skillId()) && (current.getSkillLevel() == selectedSkill.skillLevel()))
			{
				_player.abortCast();
				_player.getAI().setIntention(Intention.IDLE);
				cancelledOwnedAction = true;
			}
			if ((_player.getAI().getIntention() == Intention.PICK_UP) && (action.pickupObjectId() > 0) && (selectedTargetObjectId == action.pickupObjectId()))
			{
				_player.getAI().setIntention(Intention.IDLE);
				cancelledOwnedAction = true;
			}
			if (cancelledOwnedAction || ((_player.getAI().getIntention() == Intention.IDLE) && (selectedTargetObjectId == action.combatTargetObjectId())))
			{
				_player.setTarget(null);
			}
		}

		@Override
		public void cancelExternalAction(ExternalOwnedAction action)
		{
			if ((action == null) || !_player.hasAI())
			{
				return;
			}
			if ((action.kind() == PhantomCombatService.ExternalActionKind.PARTY_ROUTE) || ((action.kind() == PhantomCombatService.ExternalActionKind.ACQUISITION) && (action.targetObjectId() == 0)))
			{
				if (_player.getAI().getIntention() == Intention.MOVE_TO)
				{
					_player.getAI().setIntention(Intention.IDLE);
				}
				return;
			}
			if (action.kind() == PhantomCombatService.ExternalActionKind.PARTY_TACTIC)
			{
				if ((_player.getAI().getIntention() == Intention.ATTACK) && (_player.getAI().getAttackTarget() != null) && (_player.getAI().getAttackTarget().getObjectId() == action.targetObjectId()))
				{
					_player.getAI().setIntention(Intention.IDLE);
				}
				final WorldObject selectedTarget = _player.getTarget();
				if ((selectedTarget != null) && (selectedTarget.getObjectId() == action.targetObjectId()))
				{
					_player.setTarget(null);
				}
				return;
			}
			if (action.kind() == PhantomCombatService.ExternalActionKind.ACQUISITION)
			{
				boolean cancelled = false;
				final SkillUseHolder acquisitionSkill = _player.getCurrentSkill();
				final SelectedSkill selected = action.selectedSkill();
				if ((selected != null) && (acquisitionSkill != null) && (acquisitionSkill.getSkillId() == selected.skillId()) && (acquisitionSkill.getSkillLevel() == selected.skillLevel()) && (_player.getAI().getCastTarget() != null) && (_player.getAI().getCastTarget().getObjectId() == action.targetObjectId()))
				{
					_player.abortCast();
					cancelled = true;
				}
				if ((_player.getAI().getAttackTarget() != null) && (_player.getAI().getAttackTarget().getObjectId() == action.targetObjectId()))
				{
					_player.abortAttack();
					cancelled = true;
				}
				if (cancelled)
				{
					_player.getAI().setIntention(Intention.IDLE);
				}
			}
			final SkillUseHolder current = _player.getCurrentSkill();
			final SelectedSkill selected = action.selectedSkill();
			if ((_player.getAI().getIntention() == Intention.CAST) && (selected != null) && (current != null) && (current.getSkillId() == selected.skillId()) && (current.getSkillLevel() == selected.skillLevel()))
			{
				_player.abortCast();
				_player.getAI().setIntention(Intention.IDLE);
			}
			final WorldObject selectedTarget = _player.getTarget();
			if ((selectedTarget != null) && (selectedTarget.getObjectId() == action.targetObjectId()))
			{
				_player.setTarget(null);
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
