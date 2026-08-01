/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.conversation;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import org.l2jmobius.gameserver.handler.ChatHandler;
import org.l2jmobius.gameserver.handler.IChatHandler;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.chat.ChatObservationService;
import org.l2jmobius.gameserver.model.chat.ChatObservationService.DispatchHandle;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.InvitationIdentity;
import org.l2jmobius.gameserver.network.enums.ChatType;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.Argument;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.ExecutionEntry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.PageRequest;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeQuery;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeService;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyCoordinator;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyCoordinator.PendingResponse;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyCoordinator.PendingResponseOutcome;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.ActionLease;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;

/** Production adapter over immutable knowledge and existing Goal/Party/chat seams. */
public final class L2jPhantomConversationExecutionPort implements PhantomConversationExecutionPort
{
	private static final Set<ChatType> CHANNELS = Set.of(ChatType.WHISPER, ChatType.PARTY, ChatType.GENERAL, ChatType.TRADE);
	private final PhantomConversationExecutionCatalog _catalog;
	private final PhantomGameKnowledgeService _knowledge;
	private final PhantomTopologyQuery _topology;
	private final PhantomPartyCoordinator _party;
	private final PhantomMaterializationService _materialization;
	private final ChatObservationService _observation;

	public L2jPhantomConversationExecutionPort(PhantomConversationExecutionCatalog catalog, PhantomGameKnowledgeService knowledge, PhantomTopologyQuery topology, PhantomPartyCoordinator party, PhantomMaterializationService materialization, ChatObservationService observation)
	{
		_catalog = Objects.requireNonNull(catalog);
		_knowledge = Objects.requireNonNull(knowledge);
		_topology = Objects.requireNonNull(topology);
		_party = Objects.requireNonNull(party);
		_materialization = Objects.requireNonNull(materialization);
		_observation = Objects.requireNonNull(observation);
	}

	@Override
	public QueryResult query(long profileId, ExecutionEntry entry)
	{
		final PhantomGameKnowledgeQuery knowledge = _knowledge.query();
		return switch (entry.proposalKey())
		{
			case "party.role.query" -> partyRole(profileId);
			case "entity.locate" -> locate(knowledge, entry);
			case "item.acquire", "item.source" -> itemSources(knowledge, entry);
			case "content.requirements" -> content(knowledge, entry);
			default -> new QueryResult(ResultStatus.REJECTED, "");
		};
	}

	@Override
	public GoalPreparation prepareGoal(long profileId, ExecutionEntry entry, long goalId, long nowMinute)
	{
		final PhantomConversationExecutionCatalog.ProposalPolicy policy = _catalog.proposal(entry.proposalKey());
		if ((policy == null) || (policy.goalType() == null))
		{
			return new GoalPreparation(ResultStatus.REJECTED, null);
		}
		final Map<String, Long> constraints = planEvidence(entry.planId());
		final String goalType = policy.goalType();
		PhantomDomainRef target = entry.target();
		PhantomDomainRef subject = new PhantomDomainRef("party", "general");
		switch (entry.proposalKey())
		{
			case "party.invite" ->
			{
				if ((target == null) || !Set.of("character.object", "profile").contains(target.namespace()))
				{
					return new GoalPreparation(ResultStatus.REJECTED, null);
				}
				constraints.put("party.objective", 0L);
			}
			case "party.leave" ->
			{
				final var claim = _party.claim(profileId).orElse(null);
				if (claim == null)
				{
					return new GoalPreparation(ResultStatus.REJECTED, null);
				}
				constraints.put("party.generation", claim.state().groupGeneration());
				target = null;
			}
			case "party.travel" ->
			{
				final var claim = _party.claim(profileId).orElse(null);
				final PhantomDomainRef destination = argumentReference(entry, "topology.node", "location");
				final Optional<org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyNode> node = destination == null ? Optional.empty() : _topology.findNode(destination.key());
				if ((claim == null) || node.isEmpty())
				{
					return new GoalPreparation(ResultStatus.REJECTED, null);
				}
				target = destination;
				final PhantomTopologyPoint point = node.get().area().representativePoint();
				constraints.put("party.generation", claim.state().groupGeneration());
				constraints.put("party.x", (long) point.x());
				constraints.put("party.y", (long) point.y());
				constraints.put("party.z", (long) point.z());
				constraints.put("party.instance", (long) point.instanceId());
			}
			case "party.accept" ->
			{
				final PendingInvitation invitation = pendingInvitation(profileId).orElse(null);
				if (invitation == null)
				{
					return new GoalPreparation(ResultStatus.STALE, null);
				}
				target = new PhantomDomainRef("character.object", Integer.toString(invitation.requesterObjectId()));
				constraints.put("party.invitation", invitation.sequence());
			}
			default ->
			{
				return new GoalPreparation(ResultStatus.REJECTED, null);
			}
		}
		final long deadline = entry.expiryMinute() > (Long.MAX_VALUE / 60000L) ? Long.MAX_VALUE : entry.expiryMinute() * 60000L;
		final PhantomGoal goal = new PhantomGoal(goalId, goalType, PhantomGoalStatus.ACTIVE, subject, target, 1, 0, null, List.of(), null, "conversation.action", 600, 0, 0, deadline, constraints, "conversation." + entry.proposalKey(), 0);
		return new GoalPreparation(ResultStatus.COMPLETED, goal);
	}

	@Override
	public Optional<PendingInvitation> pendingInvitation(long profileId)
	{
		return _party.pendingInvitation(profileId).map(invitation ->
		{
			final var managed = _party.managedIdentity(invitation.requesterObjectId());
			final PhantomDomainRef requester = managed.isPresent() ? new PhantomDomainRef("profile", Long.toString(managed.getAsLong())) : new PhantomDomainRef("character.object", Integer.toString(invitation.requesterObjectId()));
			return new PendingInvitation(invitation.identity().sequence(), invitation.requesterObjectId(), invitation.inviteeObjectId(), invitation.requesterName(), requester);
		});
	}

	@Override
	public ResultStatus respondToPending(long profileId, PendingInvitation invitation, boolean accept, String planId)
	{
		final InvitationIdentity identity = new InvitationIdentity(invitation.sequence(), invitation.requesterObjectId(), invitation.inviteeObjectId());
		return switch (_party.respondToPending(profileId, identity, accept ? PendingResponse.ACCEPT : PendingResponse.REFUSE, planId))
		{
			case COMPLETED -> ResultStatus.COMPLETED;
			case IDEMPOTENT -> ResultStatus.IDEMPOTENT;
			case STALE -> ResultStatus.STALE;
			default -> ResultStatus.REJECTED;
		};
	}

	@Override
	public OutboundResult dispatch(long profileId, ExecutionEntry entry)
	{
		if (!CHANNELS.contains(entry.channel()) || !validText(entry.text()))
		{
			return new OutboundResult(ResultStatus.REJECTED, 0);
		}
		final Optional<ActionLease> acquired = _materialization.tryAcquireAction(profileId);
		if (acquired.isEmpty())
		{
			return new OutboundResult(ResultStatus.STALE, 0);
		}
		try (ActionLease lease = acquired.get())
		{
			final Player sender = lease.player();
			final var snapshot = _materialization.find(profileId).orElse(null);
			if ((snapshot == null) || (snapshot.characterObjectId() != sender.getObjectId()) || (World.getInstance().getPlayer(sender.getObjectId()) != sender))
			{
				return new OutboundResult(ResultStatus.STALE, 0);
			}
			final Player counterpart = resolve(entry.counterpart());
			if ((counterpart == null) || ((entry.channel() == ChatType.PARTY) && !sender.isInParty()))
			{
				return new OutboundResult(ResultStatus.STALE, 0);
			}
			final IChatHandler handler = ChatHandler.getInstance().getHandler(entry.channel());
			if (handler == null)
			{
				return new OutboundResult(ResultStatus.REJECTED, 0);
			}
			final String target = entry.channel() == ChatType.WHISPER ? counterpart.getName() : "";
			try (DispatchHandle dispatch = _observation.openGeneratedDispatch(sender.getObjectId(), sender.getName(), entry.channel(), target, entry.text(), System.currentTimeMillis()))
			{
				if (dispatch.descriptor() == null)
				{
					return new OutboundResult(ResultStatus.REJECTED, 0);
				}
				handler.onChat(entry.channel(), sender, target, entry.text());
				return new OutboundResult(dispatch.deliveries() > 0 ? ResultStatus.COMPLETED : ResultStatus.REJECTED, dispatch.deliveries());
			}
		}
	}

	private QueryResult partyRole(long profileId)
	{
		final var claim = _party.claim(profileId).orElse(null);
		if (claim == null)
		{
			return new QueryResult(ResultStatus.NOT_FOUND, "");
		}
		final Set<String> assigned = claim.state().assignments().stream().map(assignment -> assignment.vacancyKey()).collect(java.util.stream.Collectors.toSet());
		final List<String> factsValues = new ArrayList<>();
		factsValues.add("роль=" + (claim.state().ownRoleKey().isEmpty() ? "не назначена" : claim.state().ownRoleKey()));
		factsValues.add("группа=" + claim.state().groupId().substring(0, 8));
		factsValues.add("поколение=" + claim.state().groupGeneration());
		factsValues.add("вакансий=" + claim.state().requirements().stream().filter(requirement -> !assigned.contains(requirement.vacancyKey())).count());
		claim.state().requirements().stream().filter(requirement -> !assigned.contains(requirement.vacancyKey())).limit(2).forEach(requirement -> factsValues.add("нужна=" + requirement.roleKey()));
		final String facts = boundedFacts(factsValues);
		return new QueryResult(ResultStatus.COMPLETED, facts);
	}

	private QueryResult locate(PhantomGameKnowledgeQuery knowledge, ExecutionEntry entry)
	{
		final PhantomDomainRef reference = argumentReference(entry, "npc", "topology.node", "content");
		if (reference == null)
		{
			return new QueryResult(ResultStatus.NOT_FOUND, "");
		}
		if (reference.namespace().equals("topology.node"))
		{
			return _topology.findNode(reference.key()).map(node -> new QueryResult(ResultStatus.COMPLETED, pointFact(node.id(), node.area().representativePoint()))).orElse(new QueryResult(ResultStatus.NOT_FOUND, ""));
		}
		if (reference.namespace().equals("npc"))
		{
			final int npcId = positiveInt(reference.key());
			if ((npcId == 0) || knowledge.findNpc(npcId).isEmpty())
			{
				return new QueryResult(ResultStatus.NOT_FOUND, "");
			}
			final var areas = knowledge.spawnAreas(npcId, PageRequest.first(4)).values().stream().filter(area -> (area.topologyNodeId() != null) && _topology.findNode(area.topologyNodeId()).isPresent()).limit(2).toList();
			if (areas.isEmpty())
			{
				return new QueryResult(ResultStatus.NOT_FOUND, "");
			}
			final String facts = boundedFacts(areas.stream().map(area -> "npc=" + npcId + ",узел=" + area.topologyNodeId() + ",instance=" + area.instanceId()).toList());
			return new QueryResult(areas.size() > 1 ? ResultStatus.AMBIGUOUS : ResultStatus.COMPLETED, facts);
		}
		if (reference.namespace().equals("content"))
		{
			final var content = knowledge.content(reference.key()).orElse(null);
			if ((content == null) || (content.topologyNodeId() == null))
			{
				return new QueryResult(ResultStatus.NOT_FOUND, "");
			}
			return _topology.findNode(content.topologyNodeId()).map(node -> new QueryResult(ResultStatus.COMPLETED, pointFact(node.id(), node.area().representativePoint()))).orElse(new QueryResult(ResultStatus.NOT_FOUND, ""));
		}
		return new QueryResult(ResultStatus.NOT_FOUND, "");
	}

	private static QueryResult itemSources(PhantomGameKnowledgeQuery knowledge, ExecutionEntry entry)
	{
		final PhantomDomainRef reference = argumentReference(entry, "item");
		final int itemId = (reference == null) || !reference.namespace().equals("item") ? 0 : positiveInt(reference.key());
		if ((itemId == 0) || knowledge.findItem(itemId).isEmpty())
		{
			return new QueryResult(ResultStatus.NOT_FOUND, "");
		}
		final Set<String> sources = new LinkedHashSet<>();
		if (!knowledge.dropSources(itemId, PageRequest.first(1)).values().isEmpty())
		{
			sources.add("drop");
		}
		if (!knowledge.spoilSources(itemId, PageRequest.first(1)).values().isEmpty())
		{
			sources.add("spoil");
		}
		if (!knowledge.manorSources(itemId, PageRequest.first(1)).values().isEmpty())
		{
			sources.add("manor");
		}
		if (!knowledge.recipesProducing(itemId, PageRequest.first(1)).values().isEmpty())
		{
			sources.add("recipe");
		}
		return sources.isEmpty() ? new QueryResult(ResultStatus.NOT_FOUND, "") : new QueryResult(ResultStatus.COMPLETED, "item=" + itemId + ";источники=" + String.join(",", sources));
	}

	private static QueryResult content(PhantomGameKnowledgeQuery knowledge, ExecutionEntry entry)
	{
		final PhantomDomainRef reference = argumentReference(entry, "content");
		if ((reference == null) || !reference.namespace().equals("content"))
		{
			return new QueryResult(ResultStatus.NOT_FOUND, "");
		}
		return knowledge.content(reference.key()).map(value ->
		{
			final List<String> facts = new ArrayList<>();
			facts.add("контент=" + value.contentId());
			facts.add("группа=" + value.recommendedMinParty() + "-" + value.recommendedMaxParty());
			value.requirements().stream().limit(3).forEach(requirement -> facts.add("требование=" + requirement.capabilityKey() + ':' + requirement.minimumCount() + ':' + requirement.minimumRank() + ':' + (requirement.required() ? "обязательно" : "желательно")));
			return new QueryResult(ResultStatus.COMPLETED, boundedFacts(facts));
		}).orElse(new QueryResult(ResultStatus.NOT_FOUND, ""));
	}

	private static String boundedFacts(List<String> facts)
	{
		final StringBuilder result = new StringBuilder();
		for (String fact : facts)
		{
			if ((fact == null) || fact.isBlank() || fact.codePoints().anyMatch(Character::isISOControl))
			{
				continue;
			}
			final String candidate = result.isEmpty() ? fact : result + ";" + fact;
			if (candidate.getBytes(StandardCharsets.UTF_8).length > 128)
			{
				break;
			}
			result.setLength(0);
			result.append(candidate);
		}
		return result.isEmpty() ? "нет" : result.toString();
	}

	private Player resolve(PhantomDomainRef reference)
	{
		try
		{
			if (reference.namespace().equals("character.object"))
			{
				return World.getInstance().getPlayer(Integer.parseInt(reference.key()));
			}
			if (reference.namespace().equals("profile"))
			{
				final var snapshot = _materialization.find(Long.parseLong(reference.key())).orElse(null);
				return snapshot == null ? null : World.getInstance().getPlayer(snapshot.characterObjectId());
			}
		}
		catch (NumberFormatException exception)
		{
			return null;
		}
		return null;
	}

	private static PhantomDomainRef argumentReference(ExecutionEntry entry, String... keys)
	{
		for (String key : keys)
		{
			final String value = entry.arguments().stream().filter(argument -> argument.key().equals(key)).map(Argument::value).findFirst().orElse(null);
			if (value == null)
			{
				continue;
			}
			final int separator = value.indexOf(':');
			if ((separator > 0) && (separator < value.length() - 1))
			{
				return new PhantomDomainRef(value.substring(0, separator), value.substring(separator + 1));
			}
		}
		return null;
	}

	private static Map<String, Long> planEvidence(String planId)
	{
		final Map<String, Long> result = new TreeMap<>();
		for (int index = 0; index < 4; index++)
		{
			result.put("conversation.plan." + index, Long.parseUnsignedLong(planId.substring(index * 16, (index + 1) * 16), 16));
		}
		return result;
	}

	private static String pointFact(String id, PhantomTopologyPoint point)
	{
		return boundedFacts(List.of("узел=" + id, "x=" + point.x(), "y=" + point.y(), "z=" + point.z(), "instance=" + point.instanceId()));
	}

	private static int positiveInt(String value)
	{
		try
		{
			final int result = Integer.parseInt(value);
			return result > 0 ? result : 0;
		}
		catch (NumberFormatException exception)
		{
			return 0;
		}
	}

	private static boolean validText(String text)
	{
		return (text != null) && !text.isBlank() && (text.codePointCount(0, text.length()) <= 100) && (text.getBytes(StandardCharsets.UTF_8).length <= PhantomConversationExecutionModel.MAX_TEXT_BYTES) && (text.indexOf(8) < 0) && text.codePoints().noneMatch(Character::isISOControl);
	}
}
