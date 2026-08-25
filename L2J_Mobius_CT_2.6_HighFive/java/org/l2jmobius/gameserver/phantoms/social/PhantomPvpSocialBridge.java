/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.social;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.l2jmobius.gameserver.phantoms.social.PhantomSocialEventSink.Status;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.MemorySnapshot;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SocialEvent;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SocialSnapshot;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SubjectRef;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialService.QueryResult;

/**
 * Goal018-owned adapter for typed PvP social events and exact revenge evidence.
 * It neither resolves Player identities nor chooses aggression candidates.
 */
public final class PhantomPvpSocialBridge
{
	public enum EventKind
	{
		ATTACK_RECEIVED("pvp.attack.received"),
		KILL_CAUSED("pvp.kill.caused"),
		DEATH_SUFFERED("pvp.death.suffered"),
		HELP_RECEIVED("pvp.help.received");

		private final String _eventKey;

		EventKind(String eventKey)
		{
			_eventKey = eventKey;
		}

		public String eventKey()
		{
			return _eventKey;
		}
	}

	public record Delivery(boolean durable, Status status, String eventId)
	{
		public Delivery
		{
			Objects.requireNonNull(status);
			eventId = Objects.requireNonNull(eventId);
		}
	}

	public record RevengeEvidence(SubjectRef subject, String eventId, String eventKey, String authorityHash, long expiryMinute)
	{
		public RevengeEvidence
		{
			Objects.requireNonNull(subject);
			eventId = Objects.requireNonNull(eventId);
			eventKey = Objects.requireNonNull(eventKey);
			authorityHash = Objects.requireNonNull(authorityHash);
			if (expiryMinute < 0)
			{
				throw new IllegalArgumentException("Invalid PvP revenge expiry.");
			}
		}
	}

	private static final Set<String> REVENGE_EVENTS = Set.of(EventKind.ATTACK_RECEIVED.eventKey(), EventKind.DEATH_SUFFERED.eventKey());
	private final PhantomSocialService _social;
	private final PhantomSocialAffiliationContextPort _affiliationContexts;

	public PhantomPvpSocialBridge(PhantomSocialService social)
	{
		this(social, PhantomSocialAffiliationContextPort.noop());
	}

	public PhantomPvpSocialBridge(PhantomSocialService social, PhantomSocialAffiliationContextPort affiliationContexts)
	{
		_social = Objects.requireNonNull(social);
		_affiliationContexts = Objects.requireNonNull(affiliationContexts);
	}

	public Delivery record(long ownerProfileId, SubjectRef subject, EventKind kind, String operationId, String evidenceHash, long minute, int magnitude)
	{
		Objects.requireNonNull(subject);
		Objects.requireNonNull(kind);
		operationId = Objects.requireNonNull(operationId);
		evidenceHash = Objects.requireNonNull(evidenceHash);
		if ((ownerProfileId <= 0) || operationId.isBlank() || (operationId.length() > 128) || (minute < 0) || (magnitude < 1) || (magnitude > PhantomSocialModel.MAX_VALUE))
		{
			throw new IllegalArgumentException("Invalid typed PvP social event.");
		}
		final String eventId = PhantomSocialModel.sha256("pvp.social|" + kind.name() + '|' + ownerProfileId + '|' + subject.stableKey() + '|' + operationId);
		final PhantomSocialEventSink.Result result = _social.record(new SocialEvent(ownerProfileId, eventId, kind.eventKey(), subject, minute, magnitude, evidenceHash, _affiliationContexts.resolve(ownerProfileId, subject)));
		return new Delivery(result.durable(), result.status(), eventId);
	}

	public Optional<RevengeEvidence> revenge(long ownerProfileId, SubjectRef exactSubject, long minute)
	{
		if ((ownerProfileId <= 0) || (exactSubject == null) || (minute < 0))
		{
			return Optional.empty();
		}
		final QueryResult<SocialSnapshot> result = _social.snapshot(ownerProfileId, exactSubject, 8, minute);
		if (!result.available())
		{
			return Optional.empty();
		}
		final MemorySnapshot memory = result.value().memories().stream().filter(item -> REVENGE_EVENTS.contains(item.eventKey()) && item.subject().equals(exactSubject) && (item.expiryMinute() > minute)).max(Comparator.comparingLong(MemorySnapshot::happenedMinute).thenComparing(MemorySnapshot::eventId)).orElse(null);
		if (memory == null)
		{
			return Optional.empty();
		}
		final String authority = PhantomSocialModel.sha256("pvp.revenge|" + result.value().authorityHash() + '|' + memory.eventId() + '|' + memory.evidenceHash() + '|' + memory.expiryMinute());
		return Optional.of(new RevengeEvidence(exactSubject, memory.eventId(), memory.eventKey(), authority, memory.expiryMinute()));
	}
}
