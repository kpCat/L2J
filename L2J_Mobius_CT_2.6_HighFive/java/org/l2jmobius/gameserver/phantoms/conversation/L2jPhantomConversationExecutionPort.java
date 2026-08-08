/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.conversation;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.InvitationBinding;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.InvitationResponse;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.PageRequest;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeQuery;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeService;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyCoordinator;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyCoordinator.PendingResponse;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyCoordinator.PendingResponseOutcome;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.StateStatus;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.ActionLease;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftConversationFacts;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.SemanticFactType;
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
	private final PhantomRiftConversationFacts _riftFacts;

	public L2jPhantomConversationExecutionPort(PhantomConversationExecutionCatalog catalog, PhantomGameKnowledgeService knowledge, PhantomTopologyQuery topology, PhantomPartyCoordinator party, PhantomMaterializationService materialization, ChatObservationService observation)
	{
		this(catalog, knowledge, topology, party, materialization, observation, PhantomRiftConversationFacts.NONE);
	}

	public L2jPhantomConversationExecutionPort(PhantomConversationExecutionCatalog catalog, PhantomGameKnowledgeService knowledge, PhantomTopologyQuery topology, PhantomPartyCoordinator party, PhantomMaterializationService materialization, ChatObservationService observation, PhantomRiftConversationFacts riftFacts)
	{
		_catalog = Objects.requireNonNull(catalog);
		_knowledge = Objects.requireNonNull(knowledge);
		_topology = Objects.requireNonNull(topology);
		_party = Objects.requireNonNull(party);
		_materialization = Objects.requireNonNull(materialization);
		_observation = Objects.requireNonNull(observation);
		_riftFacts = Objects.requireNonNull(riftFacts);
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
			default -> new QueryResult(ResultStatus.REJECTED, List.of());
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
				if ((claim == null) || !Set.of(StateStatus.LEADER, StateStatus.MEMBER).contains(claim.state().status()))
				{
					return new GoalPreparation(ResultStatus.REJECTED, null);
				}
				constraints.put("party.generation", claim.state().groupGeneration());
				constraints.putAll(hashEvidence("party.group", claim.state().groupId()));
				target = null;
			}
			case "party.travel" ->
			{
				final var claim = _party.claim(profileId).orElse(null);
				final PhantomDomainRef destination = argumentReference(entry, "topology.node", "location");
				final Optional<org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyNode> node = destination == null ? Optional.empty() : _topology.findNode(destination.key());
				if ((claim == null) || (claim.state().status() != StateStatus.LEADER) || node.isEmpty())
				{
					return new GoalPreparation(ResultStatus.REJECTED, null);
				}
				target = destination;
				final PhantomTopologyPoint point = node.get().area().representativePoint();
				constraints.put("party.generation", claim.state().groupGeneration());
				constraints.putAll(hashEvidence("party.group", claim.state().groupId()));
				constraints.put("party.x", (long) point.x());
				constraints.put("party.y", (long) point.y());
				constraints.put("party.z", (long) point.z());
				constraints.put("party.instance", (long) point.instanceId());
			}
			case "party.accept" ->
			{
				final InvitationBinding invitation = entry.invitationBinding();
				if ((invitation == null) || (invitation.response() != InvitationResponse.ACCEPT))
				{
					return new GoalPreparation(ResultStatus.STALE, null);
				}
				target = new PhantomDomainRef("character.object", Integer.toString(invitation.requesterObjectId()));
				constraints.put("party.invitation", invitation.sequence());
				constraints.put("party.requester", (long) invitation.requesterObjectId());
				constraints.put("party.invitee", (long) invitation.inviteeObjectId());
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
	public boolean allowsGoalSupersession(long profileId, ExecutionEntry entry, PhantomGoal previousGoal)
	{
		if ((previousGoal == null) || (previousGoal.status() != PhantomGoalStatus.ACTIVE))
		{
			return false;
		}
		final var claim = _party.claim(profileId).orElse(null);
		if (claim == null)
		{
			return false;
		}
		final boolean leader = previousGoal.goalType().equals(PhantomPartyCoordinator.LEAD_GOAL) && (claim.state().status() == StateStatus.LEADER);
		final boolean member = previousGoal.goalType().equals(PhantomPartyCoordinator.MEMBER_GOAL) && (claim.state().status() == StateStatus.MEMBER);
		return entry.proposalKey().equals("party.leave") ? leader || member : entry.proposalKey().equals("party.travel") && leader;
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
		return switch (_party.respondToPending(profileId, identity, accept ? PendingResponse.ACCEPT : PendingResponse.REFUSE, planId, true))
		{
			case COMPLETED -> ResultStatus.COMPLETED;
			case IDEMPOTENT -> ResultStatus.IDEMPOTENT;
			case STALE -> ResultStatus.STALE;
			default -> ResultStatus.REJECTED;
		};
	}

	@Override
	public ResultStatus reconcileInvitation(long profileId, ExecutionEntry entry)
	{
		final InvitationBinding binding = entry.invitationBinding();
		if (binding == null)
		{
			return ResultStatus.REJECTED;
		}
		final InvitationIdentity identity = new InvitationIdentity(binding.sequence(), binding.requesterObjectId(), binding.inviteeObjectId());
		final PendingResponse response = binding.response() == InvitationResponse.ACCEPT ? PendingResponse.ACCEPT : PendingResponse.REFUSE;
		final PendingResponseOutcome replay = _party.conversationResponseOutcome(entry.planId(), identity, response).orElse(null);
		if (replay != null)
		{
			return switch (replay)
			{
				case COMPLETED, IDEMPOTENT -> ResultStatus.COMPLETED;
				case STALE -> ResultStatus.STALE;
				case REJECTED -> ResultStatus.REJECTED;
				case STOPPING -> ResultStatus.UNCERTAIN;
			};
		}
		final PendingInvitation current = pendingInvitation(profileId).orElse(null);
		if (current != null)
		{
			return exact(current, binding) ? ResultStatus.REJECTED : ResultStatus.STALE;
		}
		if (binding.response() == InvitationResponse.REFUSE)
		{
			return ResultStatus.UNCERTAIN;
		}
		final var claim = _party.claim(profileId).orElse(null);
		if ((claim == null) || ((claim.state().status() != StateStatus.MEMBER) && (claim.state().status() != StateStatus.LEADER)) || (claim.state().operation() == null) || (claim.state().operation().member() == null))
		{
			return ResultStatus.UNCERTAIN;
		}
		final var operation = claim.state().operation();
		return (operation.invitationSequence() == binding.sequence()) && (operation.leader().characterObjectId() == binding.requesterObjectId()) && (operation.member().characterObjectId() == binding.inviteeObjectId()) ? ResultStatus.COMPLETED : ResultStatus.UNCERTAIN;
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
			if ((counterpart == null) || ((entry.channel() == ChatType.PARTY) && ((sender.getParty() == null) || (sender.getParty() != counterpart.getParty()))))
			{
				return new OutboundResult(ResultStatus.STALE, 0);
			}
			final IChatHandler handler = ChatHandler.getInstance().getHandler(entry.channel());
			if (handler == null)
			{
				return new OutboundResult(ResultStatus.REJECTED, 0);
			}
			final String target = entry.channel() == ChatType.WHISPER ? counterpart.getName() : "";
			try (DispatchHandle dispatch = _observation.openGeneratedDispatch(sender.getObjectId(), sender.getName(), entry.channel(), target, entry.text(), System.currentTimeMillis(), counterpart.getObjectId()))
			{
				if (dispatch.descriptor() == null)
				{
					return new OutboundResult(ResultStatus.REJECTED, 0);
				}
				handler.onChat(entry.channel(), sender, target, entry.text());
				final ResultStatus status = dispatch.expectedCounterpartDelivered() ? ResultStatus.COMPLETED : dispatch.deliveries() > 0 ? ResultStatus.UNCERTAIN : ResultStatus.REJECTED;
				return new OutboundResult(status, dispatch.deliveries(), dispatch.expectedCounterpartDelivered());
			}
		}
	}

	private QueryResult partyRole(long profileId)
	{
		final var claim = _party.claim(profileId).orElse(null);
		if (claim == null)
		{
			return new QueryResult(ResultStatus.NOT_FOUND, List.of());
		}
		final Set<String> assigned = claim.state().assignments().stream().map(assignment -> assignment.vacancyKey()).collect(java.util.stream.Collectors.toSet());
		final List<QueryFact> facts = new ArrayList<>();
		facts.add(new QueryFact("party.role", null, null, claim.state().ownRoleKey().isEmpty() ? "unassigned" : claim.state().ownRoleKey(), "party.claim"));
		facts.add(new QueryFact("party.group_generation", null, claim.state().groupGeneration(), null, "party.claim"));
		facts.add(new QueryFact("party.vacancy", null, claim.state().requirements().stream().filter(requirement -> !assigned.contains(requirement.vacancyKey())).count(), null, "party.claim"));
		final int[] index =
		{
			0
		};
		claim.state().requirements().stream().filter(requirement -> !assigned.contains(requirement.vacancyKey())).limit(2).forEach(requirement -> facts.add(new QueryFact("party.vacancy." + index[0]++, null, null, requirement.roleKey(), "party.claim")));
		for (var fact : _riftFacts.latest(profileId))
		{
			if (fact.type() == SemanticFactType.RIFT_PREP_STATUS)
			{
				facts.add(new QueryFact("rift.status", null, null, fact.slots().get("status"), "rift.readiness"));
			}
			else if (fact.type() == SemanticFactType.RIFT_MISSING_ROLE)
			{
				facts.add(new QueryFact("rift.missing_role", null, null, fact.slots().get("missingRoleKey"), "rift.readiness"));
			}
			else if (fact.type() == SemanticFactType.RIFT_MEMBER_NOT_READY)
			{
				final String characterId = fact.slots().get("memberCharacterId");
				if (characterId != null)
				{
					facts.add(new QueryFact("rift.member_not_ready", new PhantomDomainRef("character.object", characterId), null, fact.slots().get("reasonKey"), "rift.readiness"));
				}
			}
			else if (fact.type() == SemanticFactType.RIFT_INVITE_REQUEST)
			{
				final String characterId = fact.slots().get("candidateCharacterId");
				if (characterId != null)
				{
					facts.add(new QueryFact("rift.invite_request", new PhantomDomainRef("character.object", characterId), Long.parseLong(fact.slots().getOrDefault("partySize", "0")), fact.slots().get("vacancy"), "rift.readiness"));
				}
			}
			else if (fact.type() == SemanticFactType.RIFT_INVITE_REFUSED)
			{
				final String characterId = fact.slots().get("candidateCharacterId");
				if (characterId != null)
				{
					facts.add(new QueryFact("rift.invite_refused", new PhantomDomainRef("character.object", characterId), null, fact.slots().get("reasonKey"), "rift.readiness"));
				}
			}			else if (fact.type() == SemanticFactType.RIFT_PARTY_FULL)
			{
				facts.add(new QueryFact("rift.party_full", null, Long.parseLong(fact.slots().get("partySize")), null, "rift.readiness"));
			}
			else if (fact.type() == SemanticFactType.RIFT_READY)
			{
				facts.add(new QueryFact("rift.ready", null, 1L, null, "rift.readiness"));
			}
		}
		return new QueryResult(ResultStatus.COMPLETED, facts);
	}

	private QueryResult locate(PhantomGameKnowledgeQuery knowledge, ExecutionEntry entry)
	{
		final PhantomDomainRef reference = argumentReference(entry, "npc", "topology.node", "content");
		if (reference == null)
		{
			return new QueryResult(ResultStatus.NOT_FOUND, List.of());
		}
		if (reference.namespace().equals("topology.node"))
		{
			return _topology.findNode(reference.key()).map(node -> new QueryResult(ResultStatus.COMPLETED, pointFacts(node.id(), node.area().representativePoint()))).orElse(new QueryResult(ResultStatus.NOT_FOUND, List.of()));
		}
		if (reference.namespace().equals("npc"))
		{
			final int npcId = positiveInt(reference.key());
			if ((npcId == 0) || knowledge.findNpc(npcId).isEmpty())
			{
				return new QueryResult(ResultStatus.NOT_FOUND, List.of());
			}
			final var areas = knowledge.spawnAreas(npcId, PageRequest.first(4)).values().stream().filter(area -> (area.topologyNodeId() != null) && _topology.findNode(area.topologyNodeId()).isPresent()).limit(2).toList();
			if (areas.isEmpty())
			{
				return new QueryResult(ResultStatus.NOT_FOUND, List.of());
			}
			final List<QueryFact> facts = new ArrayList<>();
			facts.add(new QueryFact("entity.reference", new PhantomDomainRef("npc", Integer.toString(npcId)), null, null, "game.knowledge.npc"));
			for (int index = 0; index < areas.size(); index++)
			{
				final var area = areas.get(index);
				facts.add(new QueryFact("topology.reference." + index, new PhantomDomainRef("topology.node", area.topologyNodeId()), null, null, "game.knowledge.npc"));
				facts.add(new QueryFact("topology.instance." + index, null, (long) area.instanceId(), null, "game.knowledge.npc"));
			}
			return new QueryResult(areas.size() > 1 ? ResultStatus.AMBIGUOUS : ResultStatus.COMPLETED, facts);
		}
		if (reference.namespace().equals("content"))
		{
			final var content = knowledge.content(reference.key()).orElse(null);
			if ((content == null) || (content.topologyNodeId() == null))
			{
				return new QueryResult(ResultStatus.NOT_FOUND, List.of());
			}
			return _topology.findNode(content.topologyNodeId()).map(node ->
			{
				final List<QueryFact> facts = new ArrayList<>();
				facts.add(new QueryFact("content.reference", new PhantomDomainRef("content", content.contentId()), null, null, "game.knowledge.content"));
				facts.addAll(pointFacts(node.id(), node.area().representativePoint()));
				return new QueryResult(ResultStatus.COMPLETED, facts);
			}).orElse(new QueryResult(ResultStatus.NOT_FOUND, List.of()));
		}
		return new QueryResult(ResultStatus.NOT_FOUND, List.of());
	}

	private static QueryResult itemSources(PhantomGameKnowledgeQuery knowledge, ExecutionEntry entry)
	{
		final PhantomDomainRef reference = argumentReference(entry, "item");
		final int itemId = (reference == null) || !reference.namespace().equals("item") ? 0 : positiveInt(reference.key());
		if ((itemId == 0) || knowledge.findItem(itemId).isEmpty())
		{
			return new QueryResult(ResultStatus.NOT_FOUND, List.of());
		}
		final List<String> sources = new ArrayList<>();
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
		if (sources.isEmpty())
		{
			return new QueryResult(ResultStatus.NOT_FOUND, List.of());
		}
		final List<QueryFact> facts = new ArrayList<>();
		facts.add(new QueryFact("item.reference", new PhantomDomainRef("item", Integer.toString(itemId)), null, null, "game.knowledge.item"));
		for (int index = 0; index < sources.size(); index++)
		{
			facts.add(new QueryFact("item.source." + index, null, null, sources.get(index), "game.knowledge.item"));
		}
		return new QueryResult(ResultStatus.COMPLETED, facts);
	}

	private static QueryResult content(PhantomGameKnowledgeQuery knowledge, ExecutionEntry entry)
	{
		final PhantomDomainRef reference = argumentReference(entry, "content");
		if ((reference == null) || !reference.namespace().equals("content"))
		{
			return new QueryResult(ResultStatus.NOT_FOUND, List.of());
		}
		return knowledge.content(reference.key()).map(value ->
		{
			final List<QueryFact> facts = new ArrayList<>();
			facts.add(new QueryFact("content.reference", new PhantomDomainRef("content", value.contentId()), null, null, "game.knowledge.content"));
			facts.add(new QueryFact("content.party_min", null, (long) value.recommendedMinParty(), null, "game.knowledge.content"));
			facts.add(new QueryFact("content.party_max", null, (long) value.recommendedMaxParty(), null, "game.knowledge.content"));
			final int[] index =
			{
				0
			};
			value.requirements().stream().limit(3).forEach(requirement -> facts.add(new QueryFact("content.capability." + index[0]++, null, null, requirement.capabilityKey() + ':' + requirement.minimumCount() + ':' + requirement.minimumRank() + ':' + (requirement.required() ? "required" : "preferred"), "game.knowledge.content")));
			return new QueryResult(ResultStatus.COMPLETED, facts);
		}).orElse(new QueryResult(ResultStatus.NOT_FOUND, List.of()));
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
		return hashEvidence("conversation.plan", planId);
	}

	private static Map<String, Long> hashEvidence(String prefix, String hash)
	{
		final Map<String, Long> result = new TreeMap<>();
		for (int index = 0; index < 4; index++)
		{
			result.put(prefix + "." + index, Long.parseUnsignedLong(hash.substring(index * 16, (index + 1) * 16), 16));
		}
		return result;
	}

	private static List<QueryFact> pointFacts(String id, PhantomTopologyPoint point)
	{
		return List.of( //
			new QueryFact("topology.instance", null, (long) point.instanceId(), null, "topology.snapshot"), //
			new QueryFact("topology.reference", new PhantomDomainRef("topology.node", id), null, null, "topology.snapshot"), //
			new QueryFact("topology.x", null, (long) point.x(), null, "topology.snapshot"), //
			new QueryFact("topology.y", null, (long) point.y(), null, "topology.snapshot"), //
			new QueryFact("topology.z", null, (long) point.z(), null, "topology.snapshot"));
	}

	private static boolean exact(PendingInvitation invitation, InvitationBinding binding)
	{
		return (invitation.sequence() == binding.sequence()) && (invitation.requesterObjectId() == binding.requesterObjectId()) && (invitation.inviteeObjectId() == binding.inviteeObjectId());
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
