/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.conversation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.groups.Party;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.DeliveredObservation;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.MaterializationSnapshot;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.ActionLease;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.InputChannel;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.InputContext;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.PlayerReference;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.SlotValue;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;

/** Copies the one elected observer's live L2J context behind an action lease. */
public final class L2jPhantomConversationContextPort implements PhantomConversationService.ContextPort
{
	private final PhantomMaterializationService _materialization;
	private final PhantomTopologyQuery _topology;

	public L2jPhantomConversationContextPort(PhantomMaterializationService materialization, PhantomTopologyQuery topology)
	{
		_materialization = Objects.requireNonNull(materialization);
		_topology = Objects.requireNonNull(topology);
	}

	@Override
	public OptionalLong profileIdForObject(int characterObjectId)
	{
		return _materialization.findByCharacterObjectId(characterObjectId).map(snapshot -> OptionalLong.of(snapshot.profileId())).orElseGet(OptionalLong::empty);
	}

	@Override
	public Optional<PhantomConversationService.ContextSnapshot> snapshot(long observerProfileId, DeliveredObservation observation, String previousIntent, List<SlotValue> previousSlots)
	{
		final Optional<ActionLease> acquired = _materialization.tryAcquireAction(observerProfileId);
		if (acquired.isEmpty())
		{
			return Optional.empty();
		}
		try (ActionLease lease = acquired.get())
		{
			final Player observer = lease.player();
			if (observer.getObjectId() != observation.recipientObjectId())
			{
				return Optional.empty();
			}
			final PlayerReference speaker = playerReference(observation.speakerObjectId(), observation.speakerName());
			PlayerReference leader = null;
			final List<PlayerReference> members = new ArrayList<>();
			long leaderProfileId = 0;
			final Party party = observer.getParty();
			if (party != null)
			{
				final List<Player> liveMembers = List.copyOf(party.getMembers());
				if (liveMembers.size() <= 9)
				{
					for (Player member : liveMembers)
					{
						members.add(playerReference(member.getObjectId(), member.getName()));
					}
					final Player liveLeader = party.getLeader();
					if (liveLeader != null)
					{
						leader = playerReference(liveLeader.getObjectId(), liveLeader.getName());
						leaderProfileId = _materialization.findByCharacterObjectId(liveLeader.getObjectId()).map(MaterializationSnapshot::profileId).orElse(0L);
					}
				}
			}
			final PhantomDomainRef topology = _topology.mostSpecificNode(new PhantomTopologyPoint(observer.getX(), observer.getY(), observer.getZ(), observer.getInstanceId())).map(node -> new PhantomDomainRef("topology.node", node.id())).orElse(null);
			final InputContext input = new InputContext(speaker, channel(observation), leader, members, List.of(speaker), List.of(speaker), null, topology, topology, previousIntent, previousSlots);
			final PhantomDomainRef counterpart = observation.channel() == org.l2jmobius.gameserver.network.enums.ChatType.PARTY && (leader != null) ? leader.reference() : speaker.reference();
			return Optional.of(new PhantomConversationService.ContextSnapshot(observerProfileId, observer.getName(), speaker.reference(), counterpart, leaderProfileId, input));
		}
	}

	private PlayerReference playerReference(int objectId, String name)
	{
		final PhantomDomainRef reference = _materialization.findByCharacterObjectId(objectId).map(snapshot -> new PhantomDomainRef("profile", Long.toString(snapshot.profileId()))).orElseGet(() -> new PhantomDomainRef("character.object", Integer.toString(objectId)));
		return new PlayerReference(reference, name);
	}

	private static InputChannel channel(DeliveredObservation observation)
	{
		return switch (observation.channel())
		{
			case GENERAL -> InputChannel.LOCAL;
			case WHISPER -> InputChannel.PRIVATE;
			case PARTY -> InputChannel.PARTY;
			case TRADE -> InputChannel.TRADE;
			default -> InputChannel.NONE;
		};
	}
}
