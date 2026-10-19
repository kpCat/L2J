/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.questinstance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.managers.InstanceManager;
import org.l2jmobius.gameserver.managers.ScriptManager;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.groups.Party;
import org.l2jmobius.gameserver.model.instancezone.InstanceWorld;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.script.Quest;
import org.l2jmobius.gameserver.model.script.QuestState;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.PageRequest;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SpawnFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeQuery;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.ActionLease;
import org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceCatalog.Content;
import org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceCatalog.Operation;
import org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceCatalog.Owner;
import org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceCatalog.Step;

/** L2J implementation that never owns QuestState, class or Instance mutation. */
public final class L2jPhantomQuestInstanceBackend implements PhantomQuestInstanceBackend
{
	private static final int SPAWN_FACT_LIMIT = 8;
	private static final int KAMALOKA_LEVEL = 23;
	private static final int KAMALOKA_LEVEL_DIFFERENCE = 5;
	private static final int KAMALOKA_MAX_PARTY = 6;
	private static final Map<Integer, List<Integer>> CAREER_GATEKEEPERS = Map.of(30320, List.of(0, 7), 30256, List.of(8, 15));
	private final PhantomMaterializationService _materialization;
	private final PhantomQuestInstanceCatalog _catalog;
	private final Supplier<PhantomGameKnowledgeQuery> _knowledge;
	private final Map<Long, String> _travelEvidence = new java.util.LinkedHashMap<>();
	private final Map<Long, org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.OwnedWalk> _issuedWalks = new java.util.LinkedHashMap<>();
	@Override public void cancelMove(long profileId)
	{
		final org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.OwnedWalk issued;
		synchronized (_issuedWalks) { issued = _issuedWalks.remove(profileId); }
		org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.cancelWalk(issued);
	}
	private java.util.function.ToLongBiFunction<Long, String> _avoidedCell = (_, _) -> Long.MIN_VALUE;
	@Override public void avoidCombatArea(java.util.function.ToLongBiFunction<Long, String> areas) { _avoidedCell = Objects.requireNonNull(areas); }

	public L2jPhantomQuestInstanceBackend(PhantomMaterializationService materialization, PhantomQuestInstanceCatalog catalog, Supplier<PhantomGameKnowledgeQuery> knowledge)
	{
		_materialization = Objects.requireNonNull(materialization, "materialization");
		_catalog = Objects.requireNonNull(catalog, "catalog");
		_knowledge = Objects.requireNonNull(knowledge, "knowledge");
	}

	@Override
	public Observation observe(long profileId, Content content)
	{
		if (!current(content))
		{
			return null;
		}
		final Optional<ActionLease> acquired = _materialization.tryAcquireAction(profileId);
		if (acquired.isEmpty())
		{
			return null;
		}
		try (ActionLease lease = acquired.orElseThrow())
		{
			return observe(lease.player(), content);
		}
	}

	@Override
	public org.l2jmobius.gameserver.phantoms.navigation.PhantomTravelStrategy.Options travelOptions(long profileId, Content content, long remainingMillis)
	{
		if (!current(content) || content.kind() != org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceCatalog.Kind.CLASS_PATH) { return null; }
		try (ActionLease lease = _materialization.tryAcquireAction(profileId).orElse(null))
		{
			if (lease == null || _knowledge.get() == null) { return null; }
			final var legs = new ArrayList<org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel.Leg>();
			for (var entry : CAREER_GATEKEEPERS.entrySet())
			{
				final var source = _knowledge.get().spawnFacts(entry.getKey(), PageRequest.first(SPAWN_FACT_LIMIT)).values().stream()
					.filter(fact -> fact.instanceId() == 0 && fact.amount() > 0).sorted(Comparator.comparing(SpawnFact::stableKey)).findFirst().orElse(null);
				if (source == null) { continue; }
				for (int index : entry.getValue())
				{
					org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel.observeNativeLeg(entry.getKey(), index, source.x(), source.y(), org.l2jmobius.gameserver.geoengine.GeoEngine.getInstance().getHeight(source.x(), source.y(), source.z()))
						.filter(org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions::available).ifPresent(legs::add);
				}
			}
			legs.sort(Comparator.comparing(org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel.Leg::id));
			final var nativeOptions = org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.snapshot(lease.player(), legs, remainingMillis);
			return new org.l2jmobius.gameserver.phantoms.navigation.PhantomTravelStrategy.Options(nativeOptions.legs(), nativeOptions.speed(), nativeOptions.adena(), 0,
				nativeOptions.classIndex(), nativeOptions.level(), nativeOptions.epochMinute(), nativeOptions.gatekeeperAllowed(), "questinstance.no_return_scroll", null, 0, remainingMillis, nativeOptions.blockedEdges());
		}
	}

	@Override
	public ActionResult gatekeeper(long profileId, Content content, org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel.Leg leg, String expectedFingerprint)
	{
		if (!current(content) || content.kind() != org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceCatalog.Kind.CLASS_PATH || leg == null
			|| !CAREER_GATEKEEPERS.getOrDefault(leg.teleporterNpcId(), List.of()).contains(leg.destinationIndex())) { return result(ActionStatus.REJECTED, null, "questinstance.gatekeeper.unsupported"); }
		try (ActionLease lease = _materialization.tryAcquireAction(profileId).orElse(null))
		{
			if (lease == null) { return result(ActionStatus.UNAVAILABLE, null, "questinstance.actor.unavailable"); }
			final Player player = lease.player();
			final var before = observe(player, content);
			if (!before.fingerprint().equals(expectedFingerprint)) { return result(ActionStatus.STALE, before, "questinstance.gatekeeper.stale"); }
			if (player.hasPendingOwnedStore() || player.isChangingClass() || player.isTransformed() || player.isMounted()) { return result(ActionStatus.UNAVAILABLE, before, "questinstance.gatekeeper.busy"); }
			final var owner = player.getNativeWorkOwner();
			final long epoch = owner == null ? 0 : owner.epoch();
			final var receipt = org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.gatekeeper(player, leg);
			synchronized (_travelEvidence)
			{
				if (_travelEvidence.size() >= 128 && !_travelEvidence.containsKey(profileId)) { _travelEvidence.remove(_travelEvidence.keySet().iterator().next()); }
				_travelEvidence.put(profileId, "object=" + player.getObjectId() + ";epoch=" + epoch + ";npc=" + leg.teleporterNpcId() + ";index=" + leg.destinationIndex() + ";" + receipt);
			}
			final ActionStatus status = switch (receipt.status())
			{
				case ARRIVED -> ActionStatus.ISSUED;
				case UNAVAILABLE, INSUFFICIENT_RESOURCES -> ActionStatus.UNAVAILABLE;
				case NATIVE_REJECTED -> ActionStatus.REJECTED;
				case PAID_WITHOUT_ARRIVAL -> ActionStatus.FAILURE;
			};
			return result(status, observe(player, content), "questinstance.gatekeeper." + receipt.status().name().toLowerCase(java.util.Locale.ROOT));
		}
	}

	@Override public String travelEvidence(long profileId) { synchronized (_travelEvidence) { return _travelEvidence.getOrDefault(profileId, ""); } }
	@Override public void clearTravelEvidence() { synchronized (_travelEvidence) { _travelEvidence.clear(); } }

	@Override
	public List<org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint> approaches(long profileId, Content content, Step step, Target target, double requiredDistance, int arrivalRadius)
	{
		if (!current(content)) { return List.of(); }
		if (content.kind() != org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceCatalog.Kind.CLASS_PATH) { return PhantomQuestInstanceBackend.super.approaches(profileId, content, step, target, requiredDistance, arrivalRadius); }
		final var geo = org.l2jmobius.gameserver.geoengine.GeoEngine.getInstance();
		if (step.operation() == Operation.COMBAT)
		{
			return geo.hasGeo(target.x(), target.y()) ? List.of(new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(target.x(), target.y(), geo.getHeight(target.x(), target.y(), target.z()), target.instanceId())) : List.of();
		}
		final double margin = Math.min(Npc.INTERACTION_DISTANCE, requiredDistance) - arrivalRadius - 16;
		if (margin < 0) { return List.of(); }
		try (ActionLease lease = _materialization.tryAcquireAction(profileId).orElse(null))
		{
			if (lease == null) { return List.of(); }
			final List<org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint> points = new ArrayList<>();
			for (int radius : new int[] {0, Math.min(64, (int) margin), Math.min(112, (int) margin)})
			{
				for (int direction = 0; direction < (radius == 0 ? 1 : 8); direction++)
				{
					final double angle = direction * Math.PI / 4;
					final int x = target.x() + (int) Math.round(radius * Math.cos(angle)), y = target.y() + (int) Math.round(radius * Math.sin(angle));
					if (!geo.hasGeo(x, y)) { continue; }
					final int z = geo.getHeight(x, y, target.z());
					if (Math.sqrt(Math.pow(x - target.x(), 2) + Math.pow(y - target.y(), 2) + Math.pow(z - target.z(), 2)) > margin) { continue; }
					final var point = new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(x, y, z, target.instanceId());
					if (!points.contains(point)) { points.add(point); }
				}
			}
			points.sort(Comparator.comparingDouble((org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint point) -> lease.player().calculateDistance3D(point.x(), point.y(), point.z())).thenComparingInt(org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint::x).thenComparingInt(org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint::y));
			return List.copyOf(points);
		}
	}

	@Override
	public Target locate(long profileId, Content content, Step step)
	{
		if (!current(content) || !content.step(step.id()).filter(step::equals).isPresent())
		{
			return null;
		}
		final Optional<ActionLease> acquired = _materialization.tryAcquireAction(profileId);
		if (acquired.isEmpty())
		{
			return null;
		}
		try (ActionLease lease = acquired.orElseThrow())
		{
			final Player player = lease.player();
			final boolean careerCombat = content.kind() == PhantomQuestInstanceCatalog.Kind.CLASS_PATH && step.operation() == Operation.COMBAT;
			final long avoided = careerCombat ? _avoidedCell.applyAsLong(profileId, content.id()) : Long.MIN_VALUE;
			final var localMonsters = careerCombat ? World.getInstance().getVisibleObjectsInRange(player, Monster.class, 2000) : List.<Monster>of();
			final Npc nearby = World.getInstance().getVisibleObjects(player, Npc.class).stream()
				.filter(npc -> step.npcIds().contains(npc.getId()) && (npc.getInstanceId() == player.getInstanceId()) && npc.isSpawned() && !npc.isAlikeDead() && ((step.operation() != Operation.COMBAT) || (npc instanceof Monster)))
				.filter(npc -> nativeQuestTargetUsable(player, content, step, npc))
				.filter(npc -> !careerCombat || PhantomAutonomousCareer.cell(npc.getX(), npc.getY()) != avoided && combatAreaDanger(player, npc.getX(), npc.getY(), npc.getZ(), npc, localMonsters) < 10_000)
				.min(Comparator.comparingInt((Npc npc) -> careerCombat ? npc.getLevel() + combatAreaDanger(player, npc.getX(), npc.getY(), npc.getZ(), npc, localMonsters) : 0)
					.thenComparingDouble(npc -> player.calculateDistance3D(npc)).thenComparingInt(Npc::getObjectId)).orElse(null);
			if (nearby != null)
			{
				return target(player, nearby, content);
			}
			if (player.getInstanceId() != 0)
			{
				return null;
			}
			final PhantomGameKnowledgeQuery knowledge = _knowledge.get();
			if (knowledge == null)
			{
				return null;
			}
			final List<SpawnFact> facts = new ArrayList<>();
			for (int npcId : step.npcIds())
			{
				// Career combat needs the nearest supported source, not an arbitrary ordinal page.
				final List<SpawnFact> sources = (content.kind() == PhantomQuestInstanceCatalog.Kind.CLASS_PATH) && (step.operation() == Operation.COMBAT)
					? knowledge.snapshot().spawnFactsByNpc().getOrDefault(npcId, List.of())
					: knowledge.spawnFacts(npcId, PageRequest.first(SPAWN_FACT_LIMIT)).values();
				facts.addAll(sources.stream().filter(fact -> (fact.instanceId() == 0) && (fact.amount() > 0))
					.filter(fact -> !careerCombat || PhantomAutonomousCareer.cell(fact.x(), fact.y()) != avoided && combatAreaDanger(player, fact.x(), fact.y(), fact.z(), null, localMonsters) < 10_000).toList());
			}
			final SpawnFact fact = facts.stream().min(Comparator.comparingDouble((SpawnFact value) -> player.calculateDistance3D(value.x(), value.y(), value.z())).thenComparing(SpawnFact::stableKey)).orElse(null);
			return fact == null ? null : new Target(0, fact.npcId(), fact.x(), fact.y(), fact.z(), fact.instanceId(), player.calculateDistance3D(fact.x(), fact.y(), fact.z()), false);
		}
	}

	private static int combatAreaDanger(Player player, int x, int y, int z, Npc target, List<Monster> nearby)
	{
		int danger = 0;
		for (var other : nearby)
		{
			if (other == target || other.isAlikeDead() || other.getInstanceId() != player.getInstanceId() || Math.abs((long) other.getZ() - z) > 256) { continue; }
			final double distance = Math.hypot((long) other.getX() - x, (long) other.getY() - y);
			if (distance < Math.max(500, other.getAggroRange() + 100) && other.getLevel() > player.getLevel() + 5) { return 10_000; }
			if (distance < Math.max(300, other.getTemplate().getClanHelpRange()) && (other.isAggressive() || target != null && target.isInMyClan(other))) { danger += 2; }
		}
		return danger;
	}

	/** Read the audited quest's native per-monster eligibility; never repair its flags or quest state. */
	private static boolean nativeQuestTargetUsable(Player player, Content content, Step step, Npc npc)
	{
		return content.questId() != 401 || !"collect.spider".equals(step.id())
			|| npc.isScriptValue(0) || npc.isScriptValue(1) && npc.getVariables().getInt("lastAttacker", 0) == player.getObjectId();
	}

	@Override
	public List<org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint> walkApproaches(long profileId, Content content, Step step, Target target, double requiredDistance, int arrivalRadius)
	{
		if (!current(content) || content.kind() != PhantomQuestInstanceCatalog.Kind.CLASS_PATH || target == null
			|| !content.step(step.id()).filter(step::equals).isPresent() || !step.npcIds().contains(target.npcId())) { return List.of(); }
		try (ActionLease lease = _materialization.tryAcquireAction(profileId).orElse(null))
		{
			if (lease == null || lease.player().getInstanceId() != target.instanceId()) { return List.of(); }
			final Player player = lease.player();
			final double dx = (double) target.x() - player.getX(), dy = (double) target.y() - player.getY(), distance = Math.hypot(dx, dy);
			if (distance <= 3000) { return List.of(); }
			final var geo = org.l2jmobius.gameserver.geoengine.GeoEngine.getInstance();
			final double bearing = Math.atan2(dy, dx);
			final List<org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint> points = new ArrayList<>();
			// A refused long projection must not keep selecting the same distant obstruction.
			// Every alternative remains a free forward step proved by the existing native corridor.
			for (int stride : new int[] {3000, 1500, 750, 375, 187})
			{
				for (double turn : stride >= 750 ? new double[] {0, -Math.PI / 6, Math.PI / 6, -Math.PI / 3, Math.PI / 3} : new double[] {0})
				{
					final int x = player.getX() + (int) Math.round(stride * Math.cos(bearing + turn)), y = player.getY() + (int) Math.round(stride * Math.sin(bearing + turn));
					if (!geo.hasGeo(x, y)) { continue; }
					final int z = geo.getHeight(x, y, player.getZ());
					if (player.calculateDistance3D(x, y, z) > 3200 || Math.sqrt(Math.pow((double) target.x() - x, 2) + Math.pow((double) target.y() - y, 2) + Math.pow((double) target.z() - z, 2)) >= player.calculateDistance3D(target.x(), target.y(), target.z())) { continue; }
					final var point = new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(x, y, z, target.instanceId());
					if (!points.contains(point)) { points.add(point); }
				}
			}
			return List.copyOf(points);
		}
	}

	@Override
	public ActionResult invoke(long profileId, Content content, Step step, String expectedFingerprint)
	{
		if (!current(content) || !content.step(step.id()).filter(step::equals).isPresent() || ((step.operation() != Operation.EVENT) && (step.operation() != Operation.TALK)))
		{
			return result(ActionStatus.REJECTED, null, "questinstance.invoke.unsupported");
		}
		final Optional<ActionLease> acquired = _materialization.tryAcquireAction(profileId);
		if (acquired.isEmpty())
		{
			return result(ActionStatus.UNAVAILABLE, null, "questinstance.actor.unavailable");
		}
		try (ActionLease lease = acquired.orElseThrow())
		{
			final Player player = lease.player();
			final Observation before = observe(player, content);
			if (!before.fingerprint().equals(expectedFingerprint))
			{
				return result(ActionStatus.STALE, before, "questinstance.invoke.stale");
			}
			final Npc npc = World.getInstance().getVisibleObjects(player, Npc.class).stream()
				.filter(value -> step.npcIds().contains(value.getId()) && (value.getInstanceId() == player.getInstanceId()) && value.isSpawned() && !value.isAlikeDead() && value.canInteract(player))
				.min(Comparator.comparingDouble((Npc value) -> player.calculateDistance3D(value)).thenComparingInt(Npc::getObjectId)).orElse(null);
			if (npc == null)
			{
				return result(ActionStatus.UNAVAILABLE, before, "questinstance.npc.unavailable");
			}
			final Owner owner = content.owner(step.ownerRole()).orElse(null);
			final Quest script = owner == null ? null : exactOwner(owner);
			if (script == null)
			{
				return result(ActionStatus.REJECTED, before, "questinstance.owner.stale");
			}
			if (step.operation() == Operation.EVENT)
			{
				script.onEvent(step.event(), npc, player);
			}
			else
			{
				script.onTalk(npc, player);
			}
			final Observation after = observe(player, content);
			return result(before.fingerprint().equals(after.fingerprint()) ? ActionStatus.IDEMPOTENT : ActionStatus.ISSUED, after, "questinstance.invoke.observed");
		}
		catch (RuntimeException exception)
		{
			return result(ActionStatus.FAILURE, null, "questinstance.invoke.failure");
		}
	}

	@Override
	public ActionResult prepareCombat(long profileId, Content content, Step step, String expectedFingerprint)
	{
		if (!current(content) || content.kind() != PhantomQuestInstanceCatalog.Kind.CLASS_PATH || step.operation() != Operation.COMBAT
			|| !content.step(step.id()).filter(step::equals).isPresent()) { return result(ActionStatus.REJECTED, null, "questinstance.combat.unsupported"); }
		try (ActionLease lease = _materialization.tryAcquireAction(profileId).orElse(null))
		{
			if (lease == null) { return result(ActionStatus.UNAVAILABLE, null, "questinstance.actor.unavailable"); }
			final Player player = lease.player();
			final Observation before = observe(player, content);
			if (!before.fingerprint().equals(expectedFingerprint)) { return result(ActionStatus.STALE, before, "questinstance.combat.stale"); }
			if (player.isAlikeDead() || player.isInParty() || player.isTeleporting() || player.isInStoreMode() || player.hasPendingOwnedStore()
				|| player.getInstanceId() != 0 || player.isTransformed() || player.isMounted()) { return result(ActionStatus.UNAVAILABLE, before, "questinstance.combat.actor"); }
			final boolean sitting = player.isSitting();
			final double castCost = player.getAllSkills().stream().filter(skill -> org.l2jmobius.gameserver.phantoms.combat.PhantomCombatSkillSafety.supports(skill, org.l2jmobius.gameserver.phantoms.combat.PhantomCombatMode.MELEE_PHYSICAL, player.isTransformed()))
				.mapToDouble(skill -> player.getStat().getMpInitialConsume(skill) + player.getStat().getMpConsume(skill)).filter(value -> value > 0).min().orElse(0);
			final boolean manaReady = player.getCurrentMp() >= Math.min(player.getMaxMp(), castCost * (sitting ? 4 : 2));
			final var threats = World.getInstance().getVisibleObjectsInRange(player, org.l2jmobius.gameserver.model.actor.Creature.class, 2000).stream()
				.filter(creature -> creature.isAttackable() && !creature.isAlikeDead() && creature.getInstanceId() == player.getInstanceId() && org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.targetsForCombat(creature, player)).toList();
			final boolean unsuitable = threats.stream().anyMatch(creature -> creature instanceof org.l2jmobius.gameserver.model.actor.Attackable npc && npc.getHating(player) > 0 && creature.getLevel() > player.getLevel() + 3);
			if (unsuitable)
			{
				if (sitting) { player.standUp(); return result(ActionStatus.ISSUED, before, "questinstance.combat.native_stand_pending"); }
				return result(ActionStatus.UNAVAILABLE, before, "questinstance.combat.retreat_required");
			}
			if (!sitting && player.getCurrentHp() >= player.getMaxHp() * 0.60 && manaReady) { return result(ActionStatus.IDEMPOTENT, before, "questinstance.combat.ready"); }
			final boolean threatened = !threats.isEmpty();
			if (threatened || player.getCurrentHp() >= player.getMaxHp() * 0.80 && manaReady)
			{
				if (!sitting) { return threatened ? result(ActionStatus.UNAVAILABLE, before, "questinstance.combat.retreat_required") : result(ActionStatus.IDEMPOTENT, before, "questinstance.combat.ready"); }
				player.standUp();
				return result(ActionStatus.ISSUED, before, "questinstance.combat.native_stand_pending");
			}
			if (player.isInCombat() || player.isAttackingNow() || player.isCastingNow() || player.isMoving()) { return result(ActionStatus.UNAVAILABLE, before, "questinstance.combat.native_settle"); }
			if (!sitting) { player.sitDown(); }
			return result(ActionStatus.ISSUED, before, "questinstance.combat.native_rest");
		}
	}

	@Override
	public ActionResult move(long profileId, Content content, Step step, Target expectedTarget)
	{
		if (!current(content) || !content.step(step.id()).filter(step::equals).isPresent() || (expectedTarget == null) || !step.npcIds().contains(expectedTarget.npcId()))
		{
			return result(ActionStatus.REJECTED, null, "questinstance.move.unsupported");
		}
		final Optional<ActionLease> acquired = _materialization.tryAcquireAction(profileId);
		if (acquired.isEmpty())
		{
			return result(ActionStatus.UNAVAILABLE, null, "questinstance.actor.unavailable");
		}
		try (ActionLease lease = acquired.orElseThrow())
		{
			final Player player = lease.player();
			final Observation before = observe(player, content);
			if ((player.getInstanceId() != expectedTarget.instanceId()) || player.isAlikeDead() || player.isCastingNow() || player.isAttackingNow())
			{
				return result(ActionStatus.REJECTED, before, "questinstance.move.actor");
			}
			if (expectedTarget.liveObject())
			{
				final WorldObject object = World.getInstance().findObject(expectedTarget.objectId());
				if (!(object instanceof Npc npc) || (npc.getId() != expectedTarget.npcId()) || (npc.getInstanceId() != expectedTarget.instanceId()) || !npc.isSpawned() || npc.isAlikeDead())
				{
					return result(ActionStatus.STALE, before, "questinstance.move.target_stale");
				}
			}
			if (player.calculateDistance3D(expectedTarget.x(), expectedTarget.y(), expectedTarget.z()) <= 50)
			{
				return result(ActionStatus.IDEMPOTENT, before, "questinstance.move.arrived");
			}
			synchronized (_issuedWalks)
			{
				final var previous = _issuedWalks.get(profileId);
				if (player.isMoving())
				{
					final var current = org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.captureWalk(player);
					if (previous == null || !previous.equals(current)) { return result(ActionStatus.UNAVAILABLE, before, "questinstance.move.foreign"); }
					if (previous.destination().x() == expectedTarget.x() && previous.destination().y() == expectedTarget.y() && previous.destination().z() == expectedTarget.z()) { return result(ActionStatus.IDEMPOTENT, before, "questinstance.move.active"); }
					org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.cancelWalk(previous);
				}
				if (_issuedWalks.size() >= 128 && !_issuedWalks.containsKey(profileId)) { return result(ActionStatus.UNAVAILABLE, before, "questinstance.move.capacity"); }
				player.getAI().setIntention(Intention.MOVE_TO, new Location(expectedTarget.x(), expectedTarget.y(), expectedTarget.z()));
				final var issued = org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.captureWalk(player);
				if (issued != null) { _issuedWalks.put(profileId, issued); }
			}
			return result(ActionStatus.ISSUED, before, "questinstance.move.issued");
		}
		catch (RuntimeException exception)
		{
			return result(ActionStatus.FAILURE, null, "questinstance.move.failure");
		}
	}

	private Observation observe(Player player, Content content)
	{
		final Quest quest = content.questId() > 0 ? ScriptManager.getInstance().getQuest(content.questId()) : null;
		final QuestState state = quest == null ? null : quest.getQuestState(player, false);
		final QuestView questView;
		if (state == null)
		{
			questView = new QuestView(QuestStatus.ABSENT, 0);
		}
		else if (state.isCreated())
		{
			questView = new QuestView(QuestStatus.CREATED, 0);
		}
		else if (state.isStarted())
		{
			questView = new QuestView(QuestStatus.STARTED, Math.max(0, state.getCond()));
		}
		else
		{
			questView = new QuestView(QuestStatus.COMPLETED, 0);
		}
		final Map<Integer, Long> counts = new HashMap<>();
		final Map<Integer, Integer> objects = new HashMap<>();
		for (Item item : player.getInventory().getItems())
		{
			if (content.itemIds().contains(item.getId()))
			{
				counts.merge(item.getId(), item.getCount(), Math::addExact);
				objects.merge(item.getId(), item.getObjectId(), Math::min);
			}
		}
		final InstanceWorld world = InstanceManager.getInstance().getPlayerWorld(player);
		final int templateId = world == null ? 0 : world.getTemplateId();
		final long reuse = content.instanceTemplateId() == 0 ? 0 : InstanceManager.getInstance().getAllInstanceTimes(player.getObjectId()).getOrDefault(content.instanceTemplateId(), 0L);
		final Party party = player.getParty();
		final int partySize = party == null ? 0 : party.getMemberCount();
		final boolean partyLeader = (party != null) && (party.getLeader() == player);
		final boolean partyLevelEligible = (party != null) && (partySize <= KAMALOKA_MAX_PARTY) && party.getMembers().stream().allMatch(member -> Math.abs(member.getLevel() - KAMALOKA_LEVEL) <= KAMALOKA_LEVEL_DIFFERENCE);
		final Item weapon = player.getActiveWeaponInstance();
		final String fingerprint = digest(_catalog.authorityHash(), content.contentHash(), player.getObjectId(), player.getLevel(), player.getRace().name(), player.getPlayerClass().getId(), player.getBaseClass(), player.getClassIndex(), player.isAlikeDead(), player.isCastingNow(), player.isAttackingNow(), player.isMoving(), questView, player.getInstanceId(), templateId, reuse, partySize, partyLeader, partyLevelEligible, weapon == null ? 0 : weapon.getId(), new java.util.TreeMap<>(counts), new java.util.TreeMap<>(objects));
		return new Observation(player.getObjectId(), player.getLevel(), player.getRace().name(), player.getPlayerClass().getId(), player.getBaseClass(), player.getClassIndex(), player.isAlikeDead(), player.isCastingNow() || player.isAttackingNow(), questView, player.getX(), player.getY(), player.getZ(), player.getInstanceId(), templateId, Math.max(0, reuse), partySize, partyLeader, partyLevelEligible, weapon == null ? 0 : weapon.getId(), counts, objects, fingerprint);
	}

	private boolean current(Content content)
	{
		return (content != null) && _catalog.content(content.id()).filter(content::equals).isPresent();
	}

	private static Quest exactOwner(Owner owner)
	{
		final Quest script = ScriptManager.getInstance().getScript(owner.scriptName());
		if ((script == null) || !owner.className().equals(script.getClass().getName()) || ((owner.questId() > 0) && ((script.getId() != owner.questId()) || (ScriptManager.getInstance().getQuest(owner.questId()) != script))))
		{
			return null;
		}
		return script;
	}

	private static Target target(Player player, Npc npc, Content content)
	{
		return new Target(npc.getObjectId(), npc.getId(), npc.getX(), npc.getY(), npc.getZ(), npc.getInstanceId(), player.calculateDistance3D(npc), true);
	}

	private static ActionResult result(ActionStatus status, Observation after, String reasonKey)
	{
		return new ActionResult(status, after, reasonKey);
	}

	private static String digest(Object... values)
	{
		try
		{
			final MessageDigest digest = MessageDigest.getInstance("SHA-256");
			for (Object value : values)
			{
				digest.update(Objects.toString(value).getBytes(StandardCharsets.UTF_8));
				digest.update((byte) 0);
			}
			return HexFormat.of().formatHex(digest.digest());
		}
		catch (Exception exception)
		{
			throw new IllegalStateException("SHA-256 is unavailable.", exception);
		}
	}
}
