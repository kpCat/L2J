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
	private final PhantomMaterializationService _materialization;
	private final PhantomQuestInstanceCatalog _catalog;
	private final Supplier<PhantomGameKnowledgeQuery> _knowledge;

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
			final Npc nearby = World.getInstance().getVisibleObjects(player, Npc.class).stream()
				.filter(npc -> step.npcIds().contains(npc.getId()) && (npc.getInstanceId() == player.getInstanceId()) && npc.isSpawned() && !npc.isAlikeDead() && ((step.operation() != Operation.COMBAT) || (npc instanceof Monster)))
				.min(Comparator.comparingDouble((Npc npc) -> player.calculateDistance3D(npc)).thenComparingInt(Npc::getObjectId)).orElse(null);
			if (nearby != null)
			{
				return target(player, nearby);
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
				facts.addAll(knowledge.spawnFacts(npcId, PageRequest.first(SPAWN_FACT_LIMIT)).values().stream().filter(fact -> (fact.instanceId() == 0) && (fact.amount() > 0)).toList());
			}
			final SpawnFact fact = facts.stream().min(Comparator.comparingDouble((SpawnFact value) -> player.calculateDistance3D(value.x(), value.y(), value.z())).thenComparing(SpawnFact::stableKey)).orElse(null);
			return fact == null ? null : new Target(0, fact.npcId(), fact.x(), fact.y(), fact.z(), fact.instanceId(), player.calculateDistance3D(fact.x(), fact.y(), fact.z()), false);
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
			player.getAI().setIntention(Intention.MOVE_TO, new Location(expectedTarget.x(), expectedTarget.y(), expectedTarget.z()));
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
		final String fingerprint = digest(_catalog.authorityHash(), content.contentHash(), player.getObjectId(), player.getLevel(), player.getRace().name(), player.getPlayerClass().getId(), player.getBaseClass(), player.isAlikeDead(), player.isCastingNow(), player.isAttackingNow(), player.isMoving(), questView, player.getInstanceId(), templateId, reuse, partySize, partyLeader, partyLevelEligible, weapon == null ? 0 : weapon.getId(), new java.util.TreeMap<>(counts), new java.util.TreeMap<>(objects));
		return new Observation(player.getObjectId(), player.getLevel(), player.getRace().name(), player.getPlayerClass().getId(), player.getBaseClass(), player.isAlikeDead(), player.isCastingNow() || player.isAttackingNow(), questView, player.getX(), player.getY(), player.getZ(), player.getInstanceId(), templateId, Math.max(0, reuse), partySize, partyLeader, partyLevelEligible, weapon == null ? 0 : weapon.getId(), counts, objects, fingerprint);
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

	private static Target target(Player player, Npc npc)
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
