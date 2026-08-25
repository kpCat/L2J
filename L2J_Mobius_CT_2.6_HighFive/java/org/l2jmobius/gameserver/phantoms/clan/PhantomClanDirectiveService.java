/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.clan;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.LongSupplier;

import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.chat.ChatObservationService.DeliveredObservation;
import org.l2jmobius.gameserver.model.chat.ChatObservationService.Origin;
import org.l2jmobius.gameserver.model.clan.Clan;
import org.l2jmobius.gameserver.network.enums.ChatType;
import org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanDirectiveModel.Decision;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanDirectiveModel.Definition;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanDirectiveModel.Effect;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanDirectiveModel.Outcome;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.State;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialEventSink.Result;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.AffiliationKind;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SocialEvent;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SocialEventContext;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SubjectRef;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialService;
import org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort;
import org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort.SignalDelivery;

public final class PhantomClanDirectiveService implements PhantomClanDirectiveIngressPort, AutoCloseable
{
	public enum ServiceState
	{
		NEW,
		RUNNING,
		STOPPING,
		STOPPED
	}

	public record OwnedSignalSnapshot(long profileId, int clanId, String sourceKey, Effect effect, long ttlMillis, long dispatchId)
	{
	}

	public record Snapshot(ServiceState state, int operationClaims, int ownedSignals, long observations, long parsed, long unauthorized, long accepted, long deferred, long refused, long relevanceSubmitted, long relevanceWithdrawn, long generatedIgnored, long failures)
	{
	}

	private static final String MODIFIER_KEY = "clan.directive.obedience";
	private static final String ACCEPTED_EVENT = "clan.directive.accepted";
	private static final String REFUSED_EVENT = "clan.directive.refused";
	private final PhantomClanDirectiveCatalog _catalog;
	private final PhantomMaterializationService _materialization;
	private final PhantomSocialService _social;
	private final PhantomRelevanceSignalPort _signals;
	private final LongSupplier _clock;
	private final Object _lifecycle = new Object();
	private final Object _ownershipMonitor = new Object();
	private final Map<OwnershipKey, OwnedSignalSnapshot> _ownership = new HashMap<>();
	private final AtomicLong _sequence = new AtomicLong();
	private final LongAdder _observations = new LongAdder();
	private final LongAdder _parsed = new LongAdder();
	private final LongAdder _unauthorized = new LongAdder();
	private final LongAdder _accepted = new LongAdder();
	private final LongAdder _deferred = new LongAdder();
	private final LongAdder _refused = new LongAdder();
	private final LongAdder _relevanceSubmitted = new LongAdder();
	private final LongAdder _relevanceWithdrawn = new LongAdder();
	private final LongAdder _generatedIgnored = new LongAdder();
	private final LongAdder _failures = new LongAdder();
	private volatile ServiceState _state = ServiceState.NEW;
	private int _operationClaims;

	public PhantomClanDirectiveService(PhantomClanDirectiveCatalog catalog, PhantomMaterializationService materialization, PhantomSocialService social, PhantomRelevanceSignalPort signals)
	{
		this(catalog, materialization, social, signals, () -> System.currentTimeMillis() / 60000L);
	}

	public PhantomClanDirectiveService(PhantomClanDirectiveCatalog catalog, PhantomMaterializationService materialization, PhantomSocialService social, PhantomRelevanceSignalPort signals, LongSupplier clock)
	{
		_catalog = Objects.requireNonNull(catalog);
		_materialization = Objects.requireNonNull(materialization);
		_social = Objects.requireNonNull(social);
		_signals = Objects.requireNonNull(signals);
		_clock = Objects.requireNonNull(clock);
	}

	public boolean start()
	{
		synchronized (_lifecycle)
		{
			if (_state != ServiceState.NEW)
			{
				return false;
			}
			_state = ServiceState.RUNNING;
			return true;
		}
	}

	@Override
	public boolean onDelivered(DeliveredObservation observation)
	{
		if (!eligibleDelivery(observation))
		{
			if ((observation != null) && (observation.dispatch().origin() == Origin.PHANTOM_GENERATED))
			{
				_generatedIgnored.increment();
			}
			return true;
		}
		_observations.increment();
		final OperationClaim claim = beginOperation();
		if (claim == null)
		{
			return true;
		}
		try (claim)
		{
			process(observation);
		}
		catch (RuntimeException exception)
		{
			_failures.increment();
		}
		return true;
	}

	static boolean eligibleDelivery(DeliveredObservation observation)
	{
		return (observation != null) && (observation.dispatch().origin() == Origin.CLIENT_CHAT) && (observation.dispatch().chatType() == ChatType.CLAN);
	}

	private void process(DeliveredObservation observation)
	{
		final Authority authority = authorize(observation);
		if (authority == null)
		{
			_unauthorized.increment();
			return;
		}
		final Definition definition = _catalog.parse(observation.dispatch().finalText()).orElse(null);
		if (definition == null)
		{
			return;
		}
		_parsed.increment();
		final var modifier = _social.modifier(authority.profileId(), authority.leaderSubject(), MODIFIER_KEY, eventMinute(observation));
		if (!modifier.available() || (modifier.value() == null))
		{
			_failures.increment();
			return;
		}
		final Decision decision = PhantomClanDirectiveModel.decide(definition, modifier.value().deltaBasisPoints());
		switch (decision.outcome())
		{
			case ACCEPT:
				_accepted.increment();
				applyAccepted(observation, authority, definition);
				recordOutcome(observation, authority, definition, ACCEPTED_EVENT);
				break;
			case DEFER:
				_deferred.increment();
				break;
			case REFUSE:
				_refused.increment();
				recordOutcome(observation, authority, definition, REFUSED_EVENT);
				break;
		}
	}

	private Authority authorize(DeliveredObservation observation)
	{
		final var materialized = _materialization.findByCharacterObjectId(observation.recipientObjectId()).orElse(null);
		if ((materialized == null) || (materialized.state() != State.ACTIVE))
		{
			return null;
		}
		final Player speaker = World.getInstance().getPlayer(observation.dispatch().speakerObjectId());
		final Player recipient = World.getInstance().getPlayer(observation.recipientObjectId());
		if ((speaker == null) || (recipient == null) || (speaker == recipient))
		{
			return null;
		}
		final Clan speakerClan = speaker.getClan();
		final Clan recipientClan = recipient.getClan();
		if ((speakerClan == null) || (speakerClan != recipientClan) || (speakerClan.getId() <= 0) || (speakerClan.getLeaderId() != speaker.getObjectId()))
		{
			return null;
		}
		final var managedLeader = _materialization.findByCharacterObjectId(speaker.getObjectId()).orElse(null);
		final SubjectRef leaderSubject = (managedLeader != null) && (managedLeader.state() == State.ACTIVE) ? SubjectRef.phantom(managedLeader.profileId()) : SubjectRef.character(speaker.getObjectId());
		return new Authority(materialized.profileId(), speakerClan.getId(), speaker.getObjectId(), leaderSubject);
	}

	private void applyAccepted(DeliveredObservation observation, Authority authority, Definition definition)
	{
		if (definition.effect() == Effect.WITHDRAW)
		{
			withdraw(authority.profileId(), authority.clanId());
			return;
		}
		final String sourceKey = sourceKey(authority.clanId());
		final long sequence = nextSequence();
		final SignalDelivery delivery;
		synchronized (_ownershipMonitor)
		{
			delivery = _signals.submit(authority.profileId(), new PhantomRelevanceSignal(sourceKey, sequence, definition.effect().requiredState(), definition.ttlMillis()));
			if (successful(delivery))
			{
				_ownership.put(new OwnershipKey(authority.profileId(), authority.clanId()), new OwnedSignalSnapshot(authority.profileId(), authority.clanId(), sourceKey, definition.effect(), definition.ttlMillis(), observation.dispatch().dispatchId()));
			}
		}
		if (successful(delivery))
		{
			_relevanceSubmitted.increment();
		}
		else
		{
			_failures.increment();
		}
	}

	private void withdraw(long profileId, int clanId)
	{
		final SignalDelivery delivery;
		synchronized (_ownershipMonitor)
		{
			final OwnershipKey key = new OwnershipKey(profileId, clanId);
			final OwnedSignalSnapshot owned = _ownership.get(key);
			if (owned == null)
			{
				return;
			}
			delivery = _signals.withdraw(profileId, owned.sourceKey(), nextSequence());
			if (successful(delivery))
			{
				_ownership.remove(key);
			}
		}
		if (successful(delivery))
		{
			_relevanceWithdrawn.increment();
		}
		else
		{
			_failures.increment();
		}
	}

	private void recordOutcome(DeliveredObservation observation, Authority authority, Definition definition, String eventKey)
	{
		final String identity = observation.dispatch().dispatchId() + "|" + authority.profileId() + "|" + definition.kind();
		final String evidence = identity + "|" + authority.leaderObjectId() + "|" + observation.recipientObjectId() + "|" + observation.dispatch().finalText();
		final SocialEvent event = new SocialEvent(
			authority.profileId(),
			PhantomSocialModel.sha256("clan.directive.event|" + identity),
			eventKey,
			authority.leaderSubject(),
			eventMinute(observation),
			1000,
			PhantomSocialModel.sha256("clan.directive.evidence|" + evidence),
			new SocialEventContext(AffiliationKind.SAME_CLAN));
		final Result result = _social.record(event);
		if (!result.durable())
		{
			_failures.increment();
		}
	}

	private long eventMinute(DeliveredObservation observation)
	{
		return Math.max(observation.dispatch().epochMillis() / 60000L, _clock.getAsLong());
	}

	private static boolean successful(SignalDelivery delivery)
	{
		return (delivery == SignalDelivery.ACCEPTED) || (delivery == SignalDelivery.COALESCED);
	}

	private static String sourceKey(int clanId)
	{
		return "clan.directive." + clanId;
	}

	private long nextSequence()
	{
		return _sequence.updateAndGet(value -> value == Long.MAX_VALUE ? Long.MAX_VALUE : value + 1);
	}

	public Optional<OwnedSignalSnapshot> ownedSignal(long profileId, int clanId)
	{
		synchronized (_ownershipMonitor)
		{
			return Optional.ofNullable(_ownership.get(new OwnershipKey(profileId, clanId)));
		}
	}

	public Snapshot snapshot()
	{
		final int ownedSignals;
		synchronized (_ownershipMonitor)
		{
			ownedSignals = _ownership.size();
		}
		final int claims;
		synchronized (_lifecycle)
		{
			claims = _operationClaims;
		}
		return new Snapshot(_state, claims, ownedSignals, _observations.sum(), _parsed.sum(), _unauthorized.sum(), _accepted.sum(), _deferred.sum(), _refused.sum(), _relevanceSubmitted.sum(), _relevanceWithdrawn.sum(), _generatedIgnored.sum(), _failures.sum());
	}

	@Override
	public void close()
	{
		boolean interrupted = false;
		synchronized (_lifecycle)
		{
			if (_state == ServiceState.STOPPED)
			{
				return;
			}
			if (_state == ServiceState.NEW)
			{
				_state = ServiceState.STOPPED;
				return;
			}
			_state = ServiceState.STOPPING;
			while (_operationClaims > 0)
			{
				try
				{
					_lifecycle.wait();
				}
				catch (InterruptedException exception)
				{
					interrupted = true;
				}
			}
		}
		withdrawAll();
		synchronized (_lifecycle)
		{
			_state = ServiceState.STOPPED;
		}
		if (interrupted)
		{
			Thread.currentThread().interrupt();
		}
	}

	private void withdrawAll()
	{
		synchronized (_ownershipMonitor)
		{
			for (OwnedSignalSnapshot owned : List.copyOf(_ownership.values()))
			{
				final SignalDelivery delivery = _signals.withdraw(owned.profileId(), owned.sourceKey(), nextSequence());
				if (successful(delivery))
				{
					_relevanceWithdrawn.increment();
				}
				else
				{
					_failures.increment();
				}
			}
			_ownership.clear();
		}
	}

	private OperationClaim beginOperation()
	{
		synchronized (_lifecycle)
		{
			if (_state != ServiceState.RUNNING)
			{
				return null;
			}
			_operationClaims++;
			return new OperationClaim();
		}
	}

	private void releaseOperation()
	{
		synchronized (_lifecycle)
		{
			_operationClaims--;
			_lifecycle.notifyAll();
		}
	}

	private record Authority(long profileId, int clanId, int leaderObjectId, SubjectRef leaderSubject)
	{
	}

	private record OwnershipKey(long profileId, int clanId)
	{
	}

	private final class OperationClaim implements AutoCloseable
	{
		private boolean _closed;

		@Override
		public void close()
		{
			if (!_closed)
			{
				_closed = true;
				releaseOperation();
			}
		}
	}
}
