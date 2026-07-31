/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.conversation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.atomic.LongAccumulator;

import org.l2jmobius.gameserver.model.chat.ChatObservationService;
import org.l2jmobius.gameserver.model.chat.ChatObservationService.DeliveryObserver;
import org.l2jmobius.gameserver.model.chat.ChatObservationService.Origin;
import org.l2jmobius.gameserver.network.enums.ChatType;
import org.l2jmobius.gameserver.phantoms.activity.PhantomSchedulerControlPort;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationCatalog.ProposalMapping;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.Authorization;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.ConversationActionProposal;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.ConversationEvidence;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.ConversationResponsePlan;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.ConversationSession;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.ConversationState;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.ConversationSubject;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.DeliveredObservation;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.ObservationBatch;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.PendingClarification;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationStore.StoredState;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerKind;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.FragmentResult;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.InputContext;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.SlotType;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.SlotValue;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.UnderstandingEvidence;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.UnderstandingResult;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.UnderstandingStatus;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticUnderstandingService;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialEventSink.Status;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.ModifierSnapshot;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SubjectRef;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialService;

/**
 * Bounded observer-only conversation planner driven by the existing shared
 * scheduler. It publishes immutable plans but never sends or executes them.
 */
public final class PhantomConversationService implements DeliveryObserver, PhantomSchedulerControlPort
{
	public interface ContextPort
	{
		OptionalLong profileIdForObject(int characterObjectId);

		Optional<ContextSnapshot> snapshot(long observerProfileId, DeliveredObservation observation, String previousIntent, List<SlotValue> previousSlots);
	}

	public record ContextSnapshot(long observerProfileId, String observerName, PhantomDomainRef speaker, PhantomDomainRef counterpart, long partyLeaderProfileId, InputContext input)
	{
		public ContextSnapshot
		{
			if ((observerProfileId <= 0) || (observerName == null) || observerName.isBlank() || (speaker == null) || (counterpart == null) || (partyLeaderProfileId < 0) || (input == null))
			{
				throw new IllegalArgumentException("Conversation context snapshot is invalid.");
			}
		}
	}

	public enum ServiceState
	{
		NEW,
		RUNNING,
		STOPPING,
		STOPPED,
		FAILED
	}

	public record Snapshot(ServiceState state, String catalogHash, int ingressSize, int openBatches, int cacheEntries, int operationClaims, int persistenceClaims, long ingressAccepted, long ingressIgnored, long backpressure, long batchesProcessed, long plansPublished, long proposalsPlanned, long duplicates, long overflows, long socialFailures, long planFailures, long failures, long maximumOperationsPerPulse)
	{
		public static Snapshot inactive()
		{
			return new Snapshot(ServiceState.STOPPED, "none", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
		}
	}

	private static final int STRIPES = 64;
	private static final int MAX_ATTEMPTS = 3;
	private final PhantomConversationCatalog _catalog;
	private final PhantomConversationStore _store;
	private final ContextPort _context;
	private final PhantomSemanticUnderstandingService _semantic;
	private final PhantomSocialService _social;
	private final PhantomConversationPlanSink _plans;
	private final PhantomIdentityLeaseRegistry _identities;
	private final ChatObservationService _observation;
	private final ArrayBlockingQueue<DeliveredObservation> _ingress;
	private final Object[] _stripes = new Object[STRIPES];
	private final Object _lifecycle = new Object();
	private final Object _pulseMonitor = new Object();
	private final LinkedHashMap<Long, MutableBatch> _batches = new LinkedHashMap<>();
	private final Map<Long, StoredState> _cache;
	private final AtomicInteger _operationClaims = new AtomicInteger();
	private final AtomicInteger _persistenceClaims = new AtomicInteger();
	private final LongAdder _ingressAccepted = new LongAdder();
	private final LongAdder _ingressIgnored = new LongAdder();
	private final LongAdder _backpressure = new LongAdder();
	private final LongAdder _batchesProcessed = new LongAdder();
	private final LongAdder _plansPublished = new LongAdder();
	private final LongAdder _proposalsPlanned = new LongAdder();
	private final LongAdder _duplicates = new LongAdder();
	private final LongAdder _overflows = new LongAdder();
	private final LongAdder _socialFailures = new LongAdder();
	private final LongAdder _planFailures = new LongAdder();
	private final LongAdder _failures = new LongAdder();
	private final LongAccumulator _maximumOperationsPerPulse = new LongAccumulator(Long::max, 0);
	private volatile ServiceState _state = ServiceState.NEW;
	private volatile AutoCloseable _registration;
	private volatile AuthorityGeneration _authority;
	private long _pulse;

	public PhantomConversationService(PhantomConversationCatalog catalog, PhantomConversationStore store, ContextPort context, PhantomSemanticUnderstandingService semantic, PhantomSocialService social, PhantomConversationPlanSink plans, PhantomIdentityLeaseRegistry identities, ChatObservationService observation)
	{
		_catalog = Objects.requireNonNull(catalog);
		_store = Objects.requireNonNull(store);
		_context = Objects.requireNonNull(context);
		_semantic = Objects.requireNonNull(semantic);
		_social = Objects.requireNonNull(social);
		_plans = Objects.requireNonNull(plans);
		_identities = Objects.requireNonNull(identities);
		_observation = Objects.requireNonNull(observation);
		_ingress = new ArrayBlockingQueue<>(catalog.limits().ingressQueue());
		for (int index = 0; index < _stripes.length; index++)
		{
			_stripes[index] = new Object();
		}
		_cache = new LinkedHashMap<>(Math.min(256, catalog.limits().cacheEntries()), 0.75f, true)
		{
			private static final long serialVersionUID = 1L;

			@Override
			protected boolean removeEldestEntry(Map.Entry<Long, StoredState> eldest)
			{
				return size() > _catalog.limits().cacheEntries();
			}
		};
	}

	public boolean start()
	{
		synchronized (_lifecycle)
		{
			if (_state != ServiceState.NEW)
			{
				return false;
			}
			final var semantic = _semantic.snapshot();
			final var social = _social.snapshot();
			if ((semantic.state() != PhantomSemanticUnderstandingService.State.RUNNING) || (social.state() != PhantomSocialService.ServiceState.RUNNING))
			{
				_state = ServiceState.FAILED;
				return false;
			}
			_authority = new AuthorityGeneration(semantic.packHash(), semantic.corpusHash(), semantic.knowledgeHash(), semantic.topologyHash(), semantic.partyRoleHash(), social.catalogHash());
			try
			{
				_registration = _observation.register(this);
			}
			catch (RuntimeException exception)
			{
				_state = ServiceState.FAILED;
				throw exception;
			}
			_state = ServiceState.RUNNING;
			return true;
		}
	}

	@Override
	public boolean onDelivered(ChatObservationService.DeliveredObservation delivered)
	{
		final OperationClaim claim = beginOperation();
		if (claim == null)
		{
			_ingressIgnored.increment();
			return true;
		}
		try (claim)
		{
			final var dispatch = delivered.dispatch();
			if ((dispatch.origin() != Origin.CLIENT_CHAT) || !_catalog.supports(dispatch.chatType()) || (_identities.getOwnerKind(delivered.recipientObjectId()) != OwnerKind.PHANTOM))
			{
				_ingressIgnored.increment();
				return true;
			}
			final DeliveredObservation observation = new DeliveredObservation(dispatch.dispatchId(), dispatch.origin(), dispatch.speakerObjectId(), dispatch.speakerName(), dispatch.chatType(), dispatch.whisperTarget(), dispatch.finalText(), dispatch.epochMillis(), delivered.recipientObjectId(), delivered.recipientName());
			if (!_ingress.offer(observation))
			{
				_backpressure.increment();
				return false;
			}
			_ingressAccepted.increment();
			return true;
		}
	}

	@Override
	public void onPulse()
	{
		final OperationClaim claim = beginOperation();
		if (claim == null)
		{
			return;
		}
		synchronized (_pulseMonitor)
		{
			try
			{
				_pulse++;
				final PulseBudget budget = new PulseBudget(_catalog.limits().operationsPerPulse());
				drainIngress(budget);
				processReadyBatches(budget);
				_maximumOperationsPerPulse.accumulate(budget.used());
			}
			catch (RuntimeException exception)
			{
				_failures.increment();
			}
			finally
			{
				claim.close();
			}
		}
	}

	public void beginStop()
	{
		final AutoCloseable registration;
		synchronized (_lifecycle)
		{
			if (_state == ServiceState.NEW)
			{
				_state = ServiceState.STOPPED;
				return;
			}
			if (_state != ServiceState.RUNNING)
			{
				return;
			}
			_state = ServiceState.STOPPING;
			registration = _registration;
			_registration = null;
		}
		if (registration != null)
		{
			try
			{
				registration.close();
			}
			catch (Exception exception)
			{
				_failures.increment();
			}
		}
		_ingress.clear();
		synchronized (_pulseMonitor)
		{
			_batches.clear();
		}
	}

	public boolean finishStop()
	{
		if (_state == ServiceState.RUNNING)
		{
			beginStop();
		}
		if ((_operationClaims.get() != 0) || (_persistenceClaims.get() != 0))
		{
			return false;
		}
		synchronized (_lifecycle)
		{
			if ((_operationClaims.get() != 0) || (_persistenceClaims.get() != 0))
			{
				return false;
			}
			_state = ServiceState.STOPPED;
			return true;
		}
	}

	public Snapshot snapshot()
	{
		final int batches;
		synchronized (_pulseMonitor)
		{
			batches = _batches.size();
		}
		final int cache;
		synchronized (_cache)
		{
			cache = _cache.size();
		}
		return new Snapshot(_state, _catalog.hash(), _ingress.size(), batches, cache, _operationClaims.get(), _persistenceClaims.get(), _ingressAccepted.sum(), _ingressIgnored.sum(), _backpressure.sum(), _batchesProcessed.sum(), _plansPublished.sum(), _proposalsPlanned.sum(), _duplicates.sum(), _overflows.sum(), _socialFailures.sum(), _planFailures.sum(), _failures.sum(), _maximumOperationsPerPulse.get());
	}

	private void drainIngress(PulseBudget budget)
	{
		while (budget.remaining() >= 2)
		{
			budget.claim();
			final DeliveredObservation observation = _ingress.poll();
			if (observation == null)
			{
				return;
			}
			budget.claim();
			MutableBatch batch = _batches.get(observation.dispatchId());
			if (batch == null)
			{
				if (_batches.size() >= _catalog.limits().openBatches())
				{
					_overflows.increment();
					continue;
				}
				batch = new MutableBatch(observation, _pulse);
				_batches.put(observation.dispatchId(), batch);
			}
			batch.add(observation, _catalog.limits().observersPerMessage());
		}
	}

	private void processReadyBatches(PulseBudget budget)
	{
		final List<Long> ready = _batches.entrySet().stream().filter(entry -> (_pulse - entry.getValue()._firstPulse) >= _catalog.limits().aggregationPulses()).map(Map.Entry::getKey).toList();
		for (long dispatchId : ready)
		{
			if (!budget.claim())
			{
				return;
			}
			final MutableBatch mutable = _batches.remove(dispatchId);
			if (mutable == null)
			{
				continue;
			}
			final ObservationBatch batch = mutable.freeze();
			if (batch.overflow())
			{
				_overflows.increment();
				continue;
			}
			process(batch, budget);
			_batchesProcessed.increment();
		}
	}

	private void process(ObservationBatch batch, PulseBudget budget)
	{
		final TreeMap<Long, DeliveredObservation> observers = new TreeMap<>();
		for (DeliveredObservation observation : batch.observers())
		{
			if (!budget.claim())
			{
				_overflows.increment();
				return;
			}
			final OptionalLong profile = _context.profileIdForObject(observation.recipientObjectId());
			if (profile.isPresent())
			{
				observers.putIfAbsent(profile.getAsLong(), observation);
			}
		}
		if (observers.isEmpty())
		{
			return;
		}

		final Election election = elect(batch.descriptor(), observers);
		if (election == null)
		{
			return;
		}
		long electedProfile = election.profileId();
		DeliveredObservation electedObservation = observers.get(electedProfile);
		if (!budget.claim())
		{
			return;
		}
		StoredState loaded = load(electedProfile);
		ConversationSession previousSession = null;
		if (!budget.claim())
		{
			return;
		}
		Optional<ContextSnapshot> context = _context.snapshot(electedProfile, electedObservation, null, List.of());
		if (context.isEmpty())
		{
			return;
		}
		if ((electedObservation.channel() == ChatType.PARTY) && (context.get().partyLeaderProfileId() > 0) && observers.containsKey(context.get().partyLeaderProfileId()) && (context.get().partyLeaderProfileId() != electedProfile))
		{
			electedProfile = context.get().partyLeaderProfileId();
			electedObservation = observers.get(electedProfile);
			if (!budget.claim())
			{
				return;
			}
			loaded = load(electedProfile);
			if (!budget.claim())
			{
				return;
			}
			context = _context.snapshot(electedProfile, electedObservation, null, List.of());
			if (context.isEmpty())
			{
				return;
			}
		}
		final ContextSnapshot snapshot = context.get();
		if (snapshot.speaker().namespace().equals("profile") && (Long.parseLong(snapshot.speaker().key()) == electedProfile))
		{
			return;
		}
		final long nowMinute = Math.max(0, electedObservation.epochMillis() / 60000L);
		final ConversationState base = currentAuthorityState(loaded == null ? null : loaded.state(), nowMinute);
		previousSession = findSession(base, electedObservation.channel(), snapshot.counterpart());
		if (base.recentObservationHashes().contains(batch.observationHash()))
		{
			_duplicates.increment();
			return;
		}
		final InputContext semanticContext = withPrevious(snapshot.input(), previousSession);
		if ((previousSession != null) && (nowMinute < previousSession.cooldownUntilMinute()))
		{
			final Planned planned = noResponse(electedProfile, batch, snapshot, previousSession, nowMinute, "no_response.cooldown", semanticContext);
			persistThenPublish(loaded, base, planned, budget);
			return;
		}
		if (!budget.claim())
		{
			return;
		}
		UnderstandingResult understanding = _semantic.understand(election.text(), semanticContext);
		PendingClarification pending = livePending(previousSession, understanding, nowMinute);
		if ((pending != null) && (understanding.status() != UnderstandingStatus.ACCEPTED))
		{
			if (!budget.claim())
			{
				return;
			}
			final FragmentResult fragment = _semantic.resolveFragment(election.text(), semanticContext, pending.missingSlots());
			understanding = continuePending(pending, fragment);
		}
		final SocialStyle socialStyle = socialStyle(electedProfile, snapshot.speaker(), nowMinute, budget);
		final Planned planned = plan(electedProfile, batch, snapshot, previousSession, understanding, socialStyle, nowMinute);
		persistThenPublish(loaded, base, planned, budget);
	}

	private Election elect(DeliveredObservation descriptor, TreeMap<Long, DeliveredObservation> observers)
	{
		return switch (descriptor.channel())
		{
			case WHISPER -> observers.size() == 1 ? new Election(observers.firstKey(), descriptor.text()) : null;
			case PARTY -> new Election(observers.firstKey(), descriptor.text());
			case GENERAL, TRADE -> exactAddress(descriptor.text(), observers);
			default -> null;
		};
	}

	private static Election exactAddress(String text, TreeMap<Long, DeliveredObservation> observers)
	{
		final List<Election> matches = new ArrayList<>();
		for (var entry : observers.entrySet())
		{
			final String stripped = stripVocative(text, entry.getValue().recipientName());
			if (stripped != null)
			{
				matches.add(new Election(entry.getKey(), stripped));
			}
		}
		return matches.size() == 1 ? matches.getFirst() : null;
	}

	private static String stripVocative(String text, String exactName)
	{
		if (text.startsWith(exactName) && (text.length() > exactName.length()))
		{
			int index = exactName.length();
			if (isVocativePunctuation(text.charAt(index++)))
			{
				while ((index < text.length()) && Character.isWhitespace(text.charAt(index)))
				{
					index++;
				}
				return index < text.length() ? text.substring(index) : null;
			}
		}
		if (text.endsWith(exactName) && (text.length() > exactName.length()))
		{
			int index = text.length() - exactName.length() - 1;
			while ((index >= 0) && Character.isWhitespace(text.charAt(index)))
			{
				index--;
			}
			if ((index >= 0) && isVocativePunctuation(text.charAt(index)))
			{
				return text.substring(0, index).stripTrailing();
			}
		}
		return null;
	}

	private static boolean isVocativePunctuation(char value)
	{
		return (value == ',') || (value == ':') || (value == ';') || (value == '!') || (value == '?');
	}

	private Planned plan(long profileId, ObservationBatch batch, ContextSnapshot context, ConversationSession previous, UnderstandingResult understanding, SocialStyle social, long nowMinute)
	{
		final String semanticHash = PhantomConversationModel.semanticResultHash(understanding.canonicalEncoding());
		String act;
		ConversationActionProposal proposal = null;
		PendingClarification pending = null;
		if (understanding.status() == UnderstandingStatus.ACCEPTED)
		{
			final ProposalMapping mapping = _catalog.mapping(understanding.selectedIntent());
			if (mapping == null)
			{
				act = "no_response.unsupported";
			}
			else
			{
				act = understanding.selectedIntent().equals("party.accept") ? "ack.accepted" : understanding.selectedIntent().equals("party.refuse") ? "ack.refused" : mapping.query() ? "ack.query_proposed" : "ack.action_proposed";
				final PhantomDomainRef target = understanding.slots().stream().filter(slot -> slot.type() == SlotType.TARGET_PLAYER).map(SlotValue::domainReference).findFirst().orElse(null);
				proposal = new ConversationActionProposal(mapping.proposalKey(), new PhantomDomainRef("profile", Long.toString(profileId)), target, understanding.slots(), semanticHash, batch.observationHash(), understanding.confidence(), nowMinute, nowMinute + mapping.ttlMinutes(), Authorization.CHECKPOINT_2_REQUIRED);
			}
		}
		else if (understanding.status() == UnderstandingStatus.CLARIFICATION_REQUIRED)
		{
			act = clarificationAct(understanding.reasonKey());
			final SlotType missing = missingSlot(understanding);
			if ((missing != null) && !understanding.selectedIntent().equals("unknown"))
			{
				pending = new PendingClarification(understanding.selectedIntent(), understanding.slots(), Set.of(missing), nowMinute + _catalog.limits().clarificationTtlMinutes(), understanding.packHash(), understanding.corpusHash(), understanding.knowledgeHash(), understanding.topologyHash(), understanding.partyRoleHash());
			}
		}
		else
		{
			act = "no_response.unsupported";
		}
		final String style = social.style();
		final boolean suppressed = proposal != null && act.startsWith("ack.") && social.suppressAcknowledgement();
		final String text = _catalog.template(act, style, selector(profileId, batch.observationHash(), act, style));
		final long cooldown = nowMinute + _catalog.channel(batch.descriptor().channel()).cooldownMinutes();
		final List<ConversationEvidence> evidence = understanding.evidence().stream().limit(_catalog.limits().evidence()).map(item -> new ConversationEvidence(item.key(), item.authorityKey())).toList();
		final ConversationResponsePlan response = suppressed ? null : new ConversationResponsePlan(profileId, batch.dispatchId(), batch.observationHash(), batch.descriptor().channel(), new ConversationSubject(context.counterpart()), semanticHash, act, style, text, proposal, cooldown, evidence);
		final ConversationSession session = new ConversationSession(batch.descriptor().channel(), context.counterpart(), nowMinute, cooldown, understanding.status() == UnderstandingStatus.ACCEPTED ? understanding.selectedIntent() : previous == null ? null : previous.previousIntent(), understanding.status() == UnderstandingStatus.ACCEPTED ? understanding.slots() : previous == null ? List.of() : previous.previousSlots(), pending, PhantomConversationModel.sha256(act), PhantomConversationModel.sha256(style), proposal == null ? "" : PhantomConversationModel.sha256(proposal.proposalKey() + '|' + semanticHash));
		return new Planned(profileId, session, response, batch.observationHash(), nowMinute);
	}

	private Planned noResponse(long profileId, ObservationBatch batch, ContextSnapshot context, ConversationSession previous, long nowMinute, String act, InputContext semanticContext)
	{
		final String semanticHash = PhantomConversationModel.sha256("no-semantic|" + batch.observationHash() + '|' + act);
		final String text = _catalog.template(act, "neutral", selector(profileId, batch.observationHash(), act, "neutral"));
		final ConversationResponsePlan response = new ConversationResponsePlan(profileId, batch.dispatchId(), batch.observationHash(), batch.descriptor().channel(), new ConversationSubject(context.counterpart()), semanticHash, act, "neutral", text, null, previous.cooldownUntilMinute(), List.of(new ConversationEvidence("conversation.cooldown", Long.toString(previous.cooldownUntilMinute()))));
		final ConversationSession session = new ConversationSession(previous.channel(), previous.counterpart(), nowMinute, previous.cooldownUntilMinute(), previous.previousIntent(), previous.previousSlots(), previous.pending(), PhantomConversationModel.sha256(act), PhantomConversationModel.sha256("neutral"), previous.lastProposalHash());
		return new Planned(profileId, session, response, batch.observationHash(), nowMinute);
	}

	private SocialStyle socialStyle(long profileId, PhantomDomainRef speaker, long nowMinute, PulseBudget budget)
	{
		final SubjectRef subject;
		try
		{
			subject = speaker.namespace().equals("profile") ? SubjectRef.phantom(Long.parseLong(speaker.key())) : SubjectRef.character(Integer.parseInt(speaker.key()));
		}
		catch (RuntimeException exception)
		{
			_socialFailures.increment();
			return SocialStyle.neutral();
		}
		final Integer warmth = modifier(profileId, subject, "conversation.warmth", nowMinute, budget);
		final Integer escalation = modifier(profileId, subject, "conflict.escalation", nowMinute, budget);
		final Integer invite = modifier(profileId, subject, "party.invite.preference", nowMinute, budget);
		if ((warmth == null) || (escalation == null) || (invite == null))
		{
			_socialFailures.increment();
			return SocialStyle.neutral();
		}
		final String style = _catalog.style(warmth, escalation, invite);
		return new SocialStyle(style, _catalog.suppresses(style));
	}

	private Integer modifier(long profileId, SubjectRef subject, String key, long nowMinute, PulseBudget budget)
	{
		if (!budget.claim())
		{
			return null;
		}
		final var result = _social.modifier(profileId, subject, key, nowMinute);
		final ModifierSnapshot value = result.value();
		return (((result.status() == Status.READY) || (result.status() == Status.INITIALIZED)) && (value != null)) ? value.deltaBasisPoints() : null;
	}

	private void persistThenPublish(StoredState loaded, ConversationState base, Planned planned, PulseBudget budget)
	{
		if (!budget.claim())
		{
			return;
		}
		final StoredState saved = persist(loaded, base, planned);
		if ((saved == null) || (planned.response() == null) || !budget.claim())
		{
			return;
		}
		try
		{
			_plans.publish(planned.response());
			_plansPublished.increment();
			if (planned.response().proposal() != null)
			{
				_proposalsPlanned.increment();
			}
		}
		catch (RuntimeException exception)
		{
			_planFailures.increment();
		}
	}

	private StoredState persist(StoredState loaded, ConversationState base, Planned planned)
	{
		final long ownerProfileId = planned.ownerProfileId();
		if (ownerProfileId <= 0)
		{
			_failures.increment();
			return null;
		}
		synchronized (stripe(ownerProfileId))
		{
			StoredState current = loaded;
			for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++)
			{
				try
				{
					if (current == null)
					{
						current = load(ownerProfileId);
					}
					final ConversationState authorityBase = currentAuthorityState(current == null ? base : current.state(), planned.nowMinute());
					if (authorityBase.recentObservationHashes().contains(planned.observationHash()))
					{
						_duplicates.increment();
						return current;
					}
					final ConversationState next = mutate(authorityBase, planned);
					_persistenceClaims.incrementAndGet();
					try
					{
						final StoredState saved = _store.save(ownerProfileId, current == null ? -1 : current.rowVersion(), next);
						cache(saved);
						return saved;
					}
					finally
					{
						_persistenceClaims.decrementAndGet();
					}
				}
				catch (ConcurrentModificationException exception)
				{
					current = exact(ownerProfileId);
				}
				catch (RuntimeException exception)
				{
					_failures.increment();
					return null;
				}
			}
			_failures.increment();
			return null;
		}
	}

	private ConversationState mutate(ConversationState base, Planned planned)
	{
		final List<ConversationSession> sessions = new ArrayList<>(base.sessions());
		sessions.removeIf(session -> session.key().equals(planned.session().key()));
		sessions.add(planned.session());
		while (sessions.size() > _catalog.limits().sessionsPerProfile())
		{
			sessions.remove(sessions.stream().min(Comparator.comparingLong(ConversationSession::lastObservedMinute).thenComparing(ConversationSession::key)).orElseThrow());
		}
		final List<String> recent = new ArrayList<>(base.recentObservationHashes());
		while (recent.size() >= _catalog.limits().recentHashes())
		{
			recent.removeFirst();
		}
		recent.add(planned.observationHash());
		recent.sort(String::compareTo);
		return new ConversationState(base.catalogHash(), base.packHash(), base.corpusHash(), base.knowledgeHash(), base.topologyHash(), base.roleHash(), base.socialHash(), Math.max(base.logicalMinute(), planned.nowMinute()), sessions, recent);
	}

	private ConversationState currentAuthorityState(ConversationState state, long logicalMinute)
	{
		final AuthorityGeneration authority = _authority;
		if ((state == null) || !state.catalogHash().equals(_catalog.hash()) || !authority.matches(state))
		{
			return new ConversationState(_catalog.hash(), authority.packHash(), authority.corpusHash(), authority.knowledgeHash(), authority.topologyHash(), authority.roleHash(), authority.socialHash(), logicalMinute, List.of(), List.of());
		}
		return state;
	}

	private StoredState load(long profileId)
	{
		synchronized (_cache)
		{
			final StoredState cached = _cache.get(profileId);
			if (cached != null)
			{
				return cached;
			}
		}
		final StoredState loaded = _store.load(profileId).orElse(null);
		if (loaded != null)
		{
			cache(loaded);
		}
		return loaded;
	}

	private StoredState exact(long profileId)
	{
		final StoredState loaded = _store.load(profileId).orElse(null);
		if (loaded != null)
		{
			cache(loaded);
		}
		return loaded;
	}

	private void cache(StoredState state)
	{
		synchronized (_cache)
		{
			_cache.put(state.profileId(), state);
		}
	}

	private Object stripe(long profileId)
	{
		return _stripes[Math.floorMod(Long.hashCode(profileId), _stripes.length)];
	}

	private OperationClaim beginOperation()
	{
		synchronized (_lifecycle)
		{
			if (_state != ServiceState.RUNNING)
			{
				return null;
			}
			_operationClaims.incrementAndGet();
			return new OperationClaim();
		}
	}

	private static InputContext withPrevious(InputContext input, ConversationSession previous)
	{
		return new InputContext(input.speaker(), input.channel(), input.partyLeader(), input.partyMembers(), input.nearbyPlayers(), input.recentPlayers(), null, input.currentLocation(), input.currentTopology(), previous == null ? null : previous.previousIntent(), previous == null ? List.of() : previous.previousSlots());
	}

	private static ConversationSession findSession(ConversationState state, ChatType channel, PhantomDomainRef counterpart)
	{
		return state.sessions().stream().filter(session -> (session.channel() == channel) && session.counterpart().equals(counterpart)).findFirst().orElse(null);
	}

	private static PendingClarification livePending(ConversationSession previous, UnderstandingResult current, long nowMinute)
	{
		if ((previous == null) || (previous.pending() == null) || (nowMinute >= previous.pending().expiryMinute()))
		{
			return null;
		}
		final PendingClarification pending = previous.pending();
		return pending.packHash().equals(current.packHash()) && pending.corpusHash().equals(current.corpusHash()) && pending.knowledgeHash().equals(current.knowledgeHash()) && pending.topologyHash().equals(current.topologyHash()) && pending.roleHash().equals(current.partyRoleHash()) ? pending : null;
	}

	private static UnderstandingResult continuePending(PendingClarification pending, FragmentResult fragment)
	{
		final Map<SlotType, SlotValue> merged = new TreeMap<>();
		pending.knownSlots().forEach(slot -> merged.put(slot.type(), slot));
		fragment.slots().forEach(slot -> merged.putIfAbsent(slot.type(), slot));
		final Set<SlotType> remaining = new java.util.HashSet<>(pending.missingSlots());
		remaining.removeAll(merged.keySet());
		final boolean accepted = fragment.status() == UnderstandingStatus.ACCEPTED && remaining.isEmpty();
		final String reason = accepted ? "accept.matched" : remaining.isEmpty() ? fragment.reasonKey() : reasonFor(remaining.stream().sorted().findFirst().orElseThrow());
		return new UnderstandingResult(accepted ? UnderstandingStatus.ACCEPTED : UnderstandingStatus.CLARIFICATION_REQUIRED, fragment.normalizedHash(), pending.packHash(), pending.corpusHash(), pending.knowledgeHash(), pending.topologyHash(), pending.roleHash(), pending.intentKey(), accepted ? 8000 : 0, List.copyOf(merged.values()), List.of(), reason, fragment.evidence());
	}

	private static String reasonFor(SlotType type)
	{
		return switch (type)
		{
			case TARGET_PLAYER -> "clarify.target_player";
			case PARTY_ROLE -> "clarify.party_role";
			case TOPOLOGY_NODE, LOCATION -> "clarify.location";
			case QUANTITY -> "clarify.quantity";
			default -> "clarify.entity";
		};
	}

	private static String clarificationAct(String reason)
	{
		return Set.of("clarify.intent", "clarify.target_player", "clarify.entity", "clarify.party_role", "clarify.location", "clarify.quantity", "clarify.complexity").contains(reason) ? reason : "clarify.entity";
	}

	private static SlotType missingSlot(UnderstandingResult understanding)
	{
		return switch (understanding.reasonKey())
		{
			case "clarify.target_player" -> SlotType.TARGET_PLAYER;
			case "clarify.party_role" -> SlotType.PARTY_ROLE;
			case "clarify.location" -> SlotType.TOPOLOGY_NODE;
			case "clarify.quantity" -> SlotType.QUANTITY;
			case "clarify.entity" -> switch (understanding.selectedIntent())
			{
				case "item.acquire.query", "item.source.query" -> SlotType.ITEM;
				case "content.requirements.query" -> SlotType.CONTENT;
				case "party.support.request" -> SlotType.CAPABILITY;
				default -> null;
			};
			default -> null;
		};
	}

	private long selector(long profileId, String observationHash, String act, String style)
	{
		return Long.parseUnsignedLong(PhantomConversationModel.sha256(profileId + "|" + observationHash + '|' + act + '|' + style + '|' + _catalog.hash()).substring(0, 16), 16);
	}

	private record Election(long profileId, String text)
	{
	}

	private record Planned(long ownerProfileId, ConversationSession session, ConversationResponsePlan response, String observationHash, long nowMinute)
	{
	}

	private record SocialStyle(String style, boolean suppressAcknowledgement)
	{
		private static SocialStyle neutral()
		{
			return new SocialStyle("neutral", false);
		}
	}

	private record AuthorityGeneration(String packHash, String corpusHash, String knowledgeHash, String topologyHash, String roleHash, String socialHash)
	{
		private boolean matches(ConversationState state)
		{
			return packHash.equals(state.packHash()) && corpusHash.equals(state.corpusHash()) && knowledgeHash.equals(state.knowledgeHash()) && topologyHash.equals(state.topologyHash()) && roleHash.equals(state.roleHash()) && socialHash.equals(state.socialHash());
		}
	}

	private static final class MutableBatch
	{
		private final DeliveredObservation _descriptor;
		private final String _hash;
		private final long _firstPulse;
		private final Map<Integer, DeliveredObservation> _observers = new HashMap<>();
		private boolean _overflow;

		private MutableBatch(DeliveredObservation descriptor, long firstPulse)
		{
			_descriptor = descriptor;
			_hash = descriptor.observationHash();
			_firstPulse = firstPulse;
		}

		private void add(DeliveredObservation observation, int maximum)
		{
			if (!observation.observationHash().equals(_hash))
			{
				_overflow = true;
				return;
			}
			if (!_observers.containsKey(observation.recipientObjectId()) && (_observers.size() >= maximum))
			{
				_overflow = true;
				return;
			}
			_observers.putIfAbsent(observation.recipientObjectId(), observation);
		}

		private ObservationBatch freeze()
		{
			return new ObservationBatch(_descriptor.dispatchId(), _hash, _descriptor, _observers.values().stream().sorted(Comparator.comparingInt(DeliveredObservation::recipientObjectId)).toList(), _firstPulse, _overflow);
		}
	}

	private static final class PulseBudget
	{
		private final int _maximum;
		private int _used;

		private PulseBudget(int maximum)
		{
			_maximum = maximum;
		}

		private boolean claim()
		{
			if (_used >= _maximum)
			{
				return false;
			}
			_used++;
			return true;
		}

		private int used()
		{
			return _used;
		}

		private int remaining()
		{
			return _maximum - _used;
		}
	}

	private final class OperationClaim implements AutoCloseable
	{
		private final AtomicBoolean _closed = new AtomicBoolean();

		@Override
		public void close()
		{
			if (_closed.compareAndSet(false, true))
			{
				_operationClaims.decrementAndGet();
			}
		}
	}
}
