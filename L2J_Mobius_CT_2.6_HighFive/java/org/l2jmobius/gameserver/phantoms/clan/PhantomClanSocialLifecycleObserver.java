/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.clan;

import java.util.Objects;

import org.l2jmobius.gameserver.model.events.Containers;
import org.l2jmobius.gameserver.model.events.EventType;
import org.l2jmobius.gameserver.model.events.holders.actor.player.clan.OnPlayerClanLeft;
import org.l2jmobius.gameserver.model.events.holders.actor.player.clan.OnPlayerClanLeft.DepartureKind;
import org.l2jmobius.gameserver.model.events.listeners.AbstractEventListener;
import org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialEventSink;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.AffiliationKind;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SocialEvent;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SocialEventContext;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SubjectRef;

/** Records one durable betrayal fact for a managed Phantom expulsion. */
public final class PhantomClanSocialLifecycleObserver implements AutoCloseable
{
	private final PhantomProfileRepository _profiles;
	private final PhantomSocialEventSink _social;
	private volatile AbstractEventListener _listener;

	public PhantomClanSocialLifecycleObserver(PhantomProfileRepository profiles, PhantomSocialEventSink social)
	{
		_profiles = Objects.requireNonNull(profiles);
		_social = Objects.requireNonNull(social);
	}

	public synchronized boolean install()
	{
		if (_listener != null)
		{
			return false;
		}
		_listener = Containers.Global().addListener(new ConsumerEventListener(Containers.Global(), EventType.ON_PLAYER_CLAN_LEFT, (java.util.function.Consumer<OnPlayerClanLeft>) this::onClanLeft, this));
		return true;
	}

	public boolean installed()
	{
		return _listener != null;
	}

	void onClanLeft(OnPlayerClanLeft event)
	{
		if ((_listener == null) || (event == null) || (event.getDepartureKind() != DepartureKind.EXPELLED) || (event.getInitiatorObjectId() <= 0))
		{
			return;
		}
		final int expelledObjectId = event.getClanMember().getObjectId();
		final var expelled = _profiles.findByCharacterObjectId(expelledObjectId).orElse(null);
		if (expelled == null)
		{
			return;
		}
		final int initiatorObjectId = event.getInitiatorObjectId();
		final var managedInitiator = _profiles.findByCharacterObjectId(initiatorObjectId).orElse(null);
		final SubjectRef subject = managedInitiator == null ? SubjectRef.character(initiatorObjectId) : SubjectRef.phantom(managedInitiator.profileId());
		final int clanId = event.getClan().getId();
		final long minute = event.getHappenedEpochMinute();
		final String identity = clanId + "|" + expelledObjectId + "|" + initiatorObjectId + "|" + minute;
		final String eventId = PhantomSocialModel.sha256("clan.member.expelled|" + identity);
		final String evidence = PhantomSocialModel.sha256("clan.member.expelled.evidence|" + identity);
		_social.record(new SocialEvent(expelled.profileId(), eventId, "clan.member.expelled", subject, minute, 1000, evidence, new SocialEventContext(AffiliationKind.SAME_CLAN)));
	}

	@Override
	public synchronized void close()
	{
		final AbstractEventListener listener = _listener;
		_listener = null;
		if (listener != null)
		{
			listener.unregisterMe();
		}
	}
}