/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.party;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberKind;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef;

/** A bounded pre-party reservation. It grants neither native membership nor movement authority. */
public record PhantomPartyRendezvousState(long profileId, String offerId, long leaderProfileId, List<Participant> roster,
	PhantomDomainRef source, long createdMillis, long expiresMillis, Phase phase, long adoptedRevision)
{
	public static final String COMPONENT_TYPE = "party.rendezvous";
	public static final int SCHEMA_VERSION = 1;
	public static final long MAXIMUM_LIFETIME_MILLIS = 600_000;
	private static final int MAGIC = 0x50525631;

	public enum Phase { PROPOSED, ACCEPTED, ASSEMBLING, ARRIVED, COMPLETE, CANCELLED }

	public record Participant(MemberRef actor, long epoch, long goalId, long goalRevision)
	{
		public Participant
		{
			if (actor == null || actor.kind() != MemberKind.PHANTOM || actor.characterObjectId() <= 0 || epoch <= 0 || goalId <= 0 || goalRevision < 0)
			{ throw new IllegalArgumentException("Invalid rendezvous participant identity."); }
		}
	}

	public PhantomPartyRendezvousState
	{
		PhantomPartyModel.requireHash(offerId, "Rendezvous offer");
		roster = List.copyOf(roster);
		if (profileId <= 0 || leaderProfileId <= 0 || roster.size() < 2 || roster.size() > 9
			|| roster.stream().map(member -> member.actor().profileId()).distinct().count() != roster.size()
			|| roster.stream().map(member -> member.actor().characterObjectId()).distinct().count() != roster.size()
			|| roster.getFirst().actor().profileId() != leaderProfileId || roster.stream().noneMatch(member -> member.actor().profileId() == profileId)
			|| source == null || !"background.farm".equals(source.namespace()) || !source.key().matches("[1-9][0-9]{0,8}@[^@\\s]{1,128}")
			|| createdMillis <= 0 || expiresMillis <= createdMillis || expiresMillis - createdMillis > MAXIMUM_LIFETIME_MILLIS || phase == null)
		{ throw new IllegalArgumentException("Invalid bounded rendezvous reservation."); }
		final long originalRevision = roster.stream().filter(member -> member.actor().profileId() == profileId).findFirst().orElseThrow().goalRevision();
		if (adoptedRevision < originalRevision || adoptedRevision - originalRevision > 1)
		{ throw new IllegalArgumentException("Rendezvous root is outside its exact adoption revision."); }
	}

	public Participant participant() { return roster.stream().filter(member -> member.actor().profileId() == profileId).findFirst().orElseThrow(); }
	public boolean active(long now) { return phase != Phase.CANCELLED && phase != Phase.COMPLETE && now >= createdMillis && now < expiresMillis; }

	public boolean current(MemberRef actor, long epoch, long goalId, long revision, PhantomDomainRef currentSource, long now)
	{
		final var own = participant();
		return active(now) && own.actor().equals(actor) && own.epoch() == epoch && own.goalId() == goalId
			&& (phase.ordinal() < Phase.ASSEMBLING.ordinal() ? revision == own.goalRevision() : revision == adoptedRevision && source.equals(currentSource));
	}

	public boolean sameOffer(PhantomPartyRendezvousState other)
	{
		return other != null && offerId.equals(other.offerId) && leaderProfileId == other.leaderProfileId && roster.equals(other.roster)
			&& source.equals(other.source) && createdMillis == other.createdMillis && expiresMillis == other.expiresMillis;
	}

	public PhantomPartyRendezvousState withPhase(Phase next, long revision)
	{
		if (next == null || next.ordinal() < phase.ordinal()) { throw new IllegalArgumentException("Rendezvous phase cannot regress."); }
		return new PhantomPartyRendezvousState(profileId, offerId, leaderProfileId, roster, source, createdMillis, expiresMillis, next, revision);
	}

	public byte[] encode()
	{
		try
		{
			final var bytes = new ByteArrayOutputStream();
			try (var out = new DataOutputStream(bytes))
			{
				out.writeInt(MAGIC); out.writeLong(profileId); out.writeUTF(offerId); out.writeLong(leaderProfileId); out.writeInt(roster.size());
				for (var member : roster)
				{ out.writeLong(member.actor().profileId()); out.writeInt(member.actor().characterObjectId()); out.writeLong(member.epoch()); out.writeLong(member.goalId()); out.writeLong(member.goalRevision()); }
				out.writeUTF(source.key()); out.writeLong(createdMillis); out.writeLong(expiresMillis); out.writeInt(phase.ordinal()); out.writeLong(adoptedRevision);
			}
			return bytes.toByteArray();
		}
		catch (IOException exception) { throw new IllegalArgumentException("Could not encode rendezvous reservation.", exception); }
	}

	public static PhantomPartyRendezvousState decode(byte[] payload)
	{
		if (payload == null || payload.length > 4096) { throw new IllegalArgumentException("Invalid rendezvous payload size."); }
		try (var in = new DataInputStream(new ByteArrayInputStream(payload)))
		{
			if (in.readInt() != MAGIC) { throw new IllegalArgumentException("Unknown rendezvous payload."); }
			final long id = in.readLong(); final String offer = in.readUTF(); final long leader = in.readLong(); final int count = in.readInt();
			if (count < 2 || count > 9) { throw new IllegalArgumentException("Invalid rendezvous roster size."); }
			final var members = new java.util.ArrayList<Participant>();
			for (int index = 0; index < count; index++)
			{ members.add(new Participant(MemberRef.phantom(in.readLong(), in.readInt()), in.readLong(), in.readLong(), in.readLong())); }
			final var source = new PhantomDomainRef("background.farm", in.readUTF()); final long created = in.readLong(); final long expires = in.readLong(); final int phase = in.readInt();
			if (phase < 0 || phase >= Phase.values().length) { throw new IllegalArgumentException("Unknown rendezvous phase."); }
			final var result = new PhantomPartyRendezvousState(id, offer, leader, members, source, created, expires, Phase.values()[phase], in.readLong());
			if (in.available() != 0) { throw new IllegalArgumentException("Trailing rendezvous payload."); }
			return result;
		}
		catch (IOException exception) { throw new IllegalArgumentException("Truncated rendezvous payload.", exception); }
	}
}
