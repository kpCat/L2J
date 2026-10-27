/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.conversation.humanized;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;

import org.l2jmobius.gameserver.model.chat.ChatObservationService.Origin;
import org.l2jmobius.gameserver.network.enums.ChatType;
import org.l2jmobius.gameserver.phantoms.activity.PhantomSchedulerControlPort;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog.Match;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog.ProfanityMode;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog.Register;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog.RelationshipBand;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog.RuntimeIdentity;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog.Selection;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog.Variation;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomPersonalConversationStore.PersonalState;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomPersonalConversationStore.StoredState;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialEventSink.Status;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SocialEvent;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SocialSnapshot;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SubjectKind;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SubjectRef;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialService;

/**
 * Caller-driven social planner. The existing Conversation pulse invokes this
 * only after the accepted functional semantic authority has rejected input.
 */
public final class PhantomHumanizedConversationService implements PhantomSchedulerControlPort
{
	public record Settings(boolean enabled, Register register, ProfanityMode profanity, Variation variation, boolean matureEnabled)
	{
		public Settings
		{
			Objects.requireNonNull(register);
			Objects.requireNonNull(profanity);
			Objects.requireNonNull(variation);
			matureEnabled = enabled && matureEnabled;
		}

		public static Settings disabled()
		{
			return new Settings(false, Register.CASUAL, ProfanityMode.CONTEXTUAL, Variation.HIGH, false);
		}
	}

	public record Request(long ownerProfileId, String ownerName, SubjectRef speaker, Origin origin, ChatType channel, String text, String observationHash, long nowMinute, RuntimeIdentity identity)
	{
		public Request
		{
			if ((ownerProfileId <= 0) || (ownerName == null) || ownerName.isBlank() || (ownerName.length() > 64) || (speaker == null) || ((origin != Origin.CLIENT_CHAT) && (origin != Origin.LOCALPLAY_PILOT) && (origin != Origin.PHANTOM_SOCIAL)) || (channel == null) || (text == null) || text.isBlank() || (text.length() > 1024) || (observationHash == null) || !observationHash.matches("[0-9A-Fa-f]{64}") || (nowMinute < 0) || (identity == null) || (identity.available() && (identity.profileId() != ownerProfileId)))
			{
				throw new IllegalArgumentException("Humanized conversation request is invalid.");
			}
		}

		public Request(long ownerProfileId, String ownerName, SubjectRef speaker, Origin origin, ChatType channel, String text, String observationHash, long nowMinute)
		{
			this(ownerProfileId, ownerName, speaker, origin, channel, text, observationHash, nowMinute, RuntimeIdentity.unavailable(ownerProfileId, ownerName));
		}
	}

	public record Decision(boolean eligible, boolean suppressed, String topic, String act, String text, String semanticHash, String templateId, String personaKey, RelationshipBand relationshipBand, boolean memoryUsed, boolean profanityUsed, boolean matureUsed, String socialEventKey, List<Evidence> evidence)
	{
		public Decision
		{
			evidence = List.copyOf(evidence);
		}

		public static Decision ineligible()
		{
			return new Decision(false, true, "", "", "", "", "", "", RelationshipBand.UNKNOWN, false, false, false, "", List.of());
		}
	}

	public record Evidence(String key, String value)
	{
		public Evidence
		{
			if ((key == null) || !key.matches("[a-z][a-z0-9_.-]{0,63}") || (value == null) || value.isBlank() || (value.length() > 96))
			{
				throw new IllegalArgumentException("Humanized evidence is invalid.");
			}
		}
	}

	/**
	 * Small functional bridge owned by the v3 understanding index. It is usable
	 * even when optional humanized social replies are disabled; relationship and
	 * native-cast authority remain in the execution port.
	 */
	public record SupportRequest(String capabilityKey, String topic, String act, String semanticHash, String patternId)
	{
	}

	public record InitiativeContext(String ownerName, RuntimeIdentity identity, long nowMinute)
	{
	}

	/** Existing native context and Conversation execution remain the only outbound authority. */
	public interface InitiativePort
	{
		Optional<InitiativeContext> context(SocialEvent event);

		boolean submit(SocialEvent event, Decision decision);
	}

	private static final int MAX_ATTEMPTS = 3;
	private final PhantomHumanizedCatalog _catalog;
	private final PhantomPersonalConversationStore _personal;
	private final PhantomSocialService _social;
	private final Settings _settings;
	private final PhantomHumanizedV4Catalog _v4;
	private final ArrayBlockingQueue<SocialEvent> _initiativeEvents = new ArrayBlockingQueue<>(128);
	private volatile InitiativePort _initiatives;
	private final java.util.concurrent.atomic.AtomicBoolean _initiativePulseOwner = new java.util.concurrent.atomic.AtomicBoolean();
	private final java.util.concurrent.atomic.LongAdder _initiativeFailures = new java.util.concurrent.atomic.LongAdder();
	private volatile boolean _initiativesStopping;

	public PhantomHumanizedConversationService(PhantomHumanizedCatalog catalog, PhantomPersonalConversationStore personal, PhantomSocialService social, Settings settings)
	{
		this(catalog, personal, social, settings, null);
	}

	public PhantomHumanizedConversationService(PhantomHumanizedCatalog catalog, PhantomPersonalConversationStore personal, PhantomSocialService social, Settings settings, PhantomHumanizedV4Catalog v4)
	{
		_catalog = Objects.requireNonNull(catalog);
		_personal = Objects.requireNonNull(personal);
		_social = Objects.requireNonNull(social);
		_settings = Objects.requireNonNull(settings);
		_v4 = v4;
	}

	public boolean enabled()
	{
		return _settings.enabled();
	}

	public void initiatives(InitiativePort port)
	{
		if ((_initiatives != null) || _initiativesStopping) { throw new IllegalStateException("Initiative owner already installed or stopping."); }
		_initiatives = Objects.requireNonNull(port);
	}

	public void onSocialEvent(SocialEvent event)
	{
		if (!_initiativesStopping && (_v4 != null) && _settings.enabled() && (_initiatives != null) && !_v4.initiativeCategories(event.eventKey()).isEmpty()) { _initiativeEvents.offer(event); }
	}

	@Override
	public void onPulse()
	{
		final InitiativePort port = _initiatives;
		if ((port == null) || _initiativesStopping || !_initiativePulseOwner.compareAndSet(false, true)) { return; }
		try
		{
			for (int operation = 0; (operation < 2) && !_initiativesStopping; operation++)
			{
				final SocialEvent event = _initiativeEvents.poll();
				if (event == null) { return; }
				try
				{
					final Optional<InitiativeContext> context = port.context(event);
					if (context.isEmpty()) { continue; }
					final InitiativeContext owner = context.get();
					final Decision decision = planEvent(event, owner.ownerName(), owner.identity(), owner.nowMinute());
					if (decision.eligible() && !decision.suppressed() && !port.submit(event, decision)) { _initiativeFailures.increment(); }
				}
				catch (RuntimeException contentFailure) { _initiativeFailures.increment(); }
			}
		}
		finally { _initiativePulseOwner.set(false); }
	}

	public boolean stopInitiatives()
	{
		_initiativesStopping = true;
		_initiatives = null;
		_initiativeEvents.clear();
		return !_initiativePulseOwner.get();
	}

	public long initiativeFailures() { return _initiativeFailures.sum(); }

	public Decision planEvent(SocialEvent event, String ownerName, RuntimeIdentity identity, long nowMinute)
	{
		Objects.requireNonNull(event);
		if ((_v4 == null) || !_settings.enabled() || _v4.initiativeCategories(event.eventKey()).isEmpty()) { return Decision.ineligible(); }
		// The string here is an event correlation ID, never an observed or fabricated chat message.
		return planV4(new Request(event.ownerProfileId(), ownerName, event.subject(), Origin.PHANTOM_SOCIAL, ChatType.WHISPER, event.eventId(), event.eventId(), nowMinute, identity), event);
	}

	public String authorityHash()
	{
		return _v4 == null ? _catalog.combinedHash() : PhantomHumanizedCatalog.sha256(_catalog.combinedHash() + "|v4|" + _v4.hash());
	}

	public Optional<SupportRequest> requestedSupport(Request request)
	{
		Objects.requireNonNull(request);
		if (((request.origin() != Origin.CLIENT_CHAT) && (request.origin() != Origin.LOCALPLAY_PILOT)) || ((request.speaker().kind() == SubjectKind.PHANTOM_PROFILE) && (request.speaker().id() == request.ownerProfileId())))
		{
			return Optional.empty();
		}
		Optional<Match> matched = _catalog.understandIdentity(request.text(), request.identity());
		if (matched.isEmpty())
		{
			matched = _catalog.understand(request.text());
		}
		if (matched.isEmpty())
		{
			return Optional.empty();
		}
		final Match match = matched.get();
		final String capabilityKey = switch (match.act())
		{
			case "support.buff.request" -> "combat.buff";
			case "support.song.request" -> "combat.song";
			case "support.dance.request" -> "combat.dance";
			default -> null;
		};
		return capabilityKey == null ? Optional.empty() : Optional.of(new SupportRequest(capabilityKey, match.topic(), match.act(), match.normalizedHash(), match.patternId()));
	}

	public Decision plan(Request request)
	{
		Objects.requireNonNull(request);
		if (!_settings.enabled())
		{
			return Decision.ineligible();
		}
		if ((request.speaker().kind() == SubjectKind.PHANTOM_PROFILE) && (request.speaker().id() == request.ownerProfileId()))
		{
			return Decision.ineligible();
		}
		Optional<Match> matched = _catalog.understandIdentity(request.text(), request.identity());
		if (matched.isEmpty())
		{
			matched = _catalog.understand(request.text());
		}
		if (matched.isEmpty())
		{
			return _v4 == null ? Decision.ineligible() : planV4(request, null);
		}
		final Match match = matched.get();
		final var socialResult = _social.snapshot(request.ownerProfileId(), request.speaker(), 4, request.nowMinute());
		if ((socialResult.value() == null) || ((socialResult.status() != Status.READY) && (socialResult.status() != Status.INITIALIZED)))
		{
			return Decision.ineligible();
		}
		final SocialSnapshot social = socialResult.value();
		final RelationshipBand band = relationshipBand(social.relationship().relationship());
		if (!relationshipActEligible(match.act(), band))
		{
			return new Decision(true, true, match.topic(), match.act(), "", match.normalizedHash(), "", "", band, false, false, false, "", List.of(new Evidence("humanized.relationship_gate", band.name().toLowerCase(java.util.Locale.ROOT))));
		}
		final long personaSelector = selector(request.ownerProfileId() + "|" + social.personality().seed() + "|" + social.authorityHash());
		final var interest = _catalog.interest(personaSelector);
		final String personaKey = personaKey(social.personality().traits(), interest.id());
		final String voice = personaKey.substring(0, personaKey.indexOf('.'));
		final Map<String, Integer> speechFacets = PhantomSpeechFacetProfile.from(social.personality()).scores();
		final String subjectKey = request.speaker().kind() == SubjectKind.PHANTOM_PROFILE ? "profile:" + request.speaker().id() : "character.object:" + request.speaker().id();
		RuntimeException lastFailure = null;
		for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++)
		{
			final StoredState stored = _personal.load(request.ownerProfileId()).orElse(null);
			PersonalState state = stored == null ? PersonalState.empty(authorityHash(), request.nowMinute()) : PhantomPersonalConversationStore.prepare(stored.state(), authorityHash(), request.nowMinute());
			if ((request.origin() == Origin.PHANTOM_SOCIAL) && !PhantomPersonalConversationStore.generatedEligible(state, subjectKey, request.nowMinute(), _catalog.limits()))
			{
				return new Decision(true, true, match.topic(), match.act(), "", match.normalizedHash(), "", personaKey, band, false, false, false, "", List.of(new Evidence("humanized.loop_guard", "turn_budget_or_cooldown"), new Evidence("humanized.origin", "phantom_social")));
			}
			final Optional<String> memory = match.recall() == null ? Optional.empty() : PhantomPersonalConversationStore.recall(state, subjectKey, match.recall(), request.nowMinute());
			final Set<String> recent = PhantomPersonalConversationStore.recentResponses(state, subjectKey);
			final long responseSelector = selector(request.ownerProfileId() + "|" + request.observationHash() + "|" + personaKey + "|" + band + "|" + _catalog.combinedHash());
			final String ownerName = request.identity().available() ? request.identity().displayName() : request.ownerName();
			final Selection selection = _catalog.select(match.act(), band, _settings.register(), _settings.profanity(), _settings.variation(), _settings.matureEnabled(), request.channel() == ChatType.WHISPER, ownerName, match.value(), memory.orElse("не успел запомнить"), interest.label(), voice, speechFacets, responseSelector, recent);
			state = PhantomPersonalConversationStore.apply(state, subjectKey, match, selection.responseHash(), request.origin(), request.nowMinute(), _catalog.limits());
			try
			{
				_personal.save(request.ownerProfileId(), stored == null ? -1 : stored.rowVersion(), state);
				final String eventKey = socialEvent(match.act());
				if (!eventKey.isEmpty())
				{
					_social.record(new SocialEvent(request.ownerProfileId(), PhantomHumanizedCatalog.sha256("humanized.event|" + request.observationHash() + '|' + eventKey), eventKey, request.speaker(), request.nowMinute(), 1000, request.observationHash()));
				}
				final List<Evidence> evidence = List.of(
					new Evidence("humanized.catalog", _catalog.combinedHash()),
					new Evidence("humanized.topic", match.topic()),
					new Evidence("humanized.act", match.act()),
					new Evidence("humanized.persona", personaKey),
					new Evidence("humanized.relationship", band.name().toLowerCase(java.util.Locale.ROOT)),
					new Evidence("humanized.origin", request.origin() == Origin.CLIENT_CHAT ? "client_chat" : request.origin() == Origin.LOCALPLAY_PILOT ? "localplay_pilot" : "phantom_social"));
				return new Decision(true, false, match.topic(), match.act(), selection.text(), match.normalizedHash(), selection.templateId(), personaKey, band, memory.isPresent(), selection.profanityUsed(), selection.matureUsed(), eventKey, evidence);
			}
			catch (RuntimeException exception)
			{
				lastFailure = exception;
			}
		}
		throw new IllegalStateException("conversation.personal optimistic retries exhausted.", lastFailure);
	}

	private Decision planV4(Request request, SocialEvent actualEvent)
	{
		final var result = _social.snapshot(request.ownerProfileId(), request.speaker(), 4, request.nowMinute());
		if (!result.available() || (result.value() == null)) { return Decision.ineligible(); }
		final SocialSnapshot social = result.value();
		final RelationshipBand band = relationshipBand(social.relationship().relationship());
		final var persona = _v4.persona(social.personality());
		final Optional<PhantomHumanizedV4Catalog.Trigger> trigger = actualEvent == null ? _v4.understand(request.text()) : Optional.empty();
		final var memory = actualEvent == null ? Optional.<org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.MemorySnapshot>empty() : social.memories().stream()
			.filter(item -> item.eventId().equals(actualEvent.eventId()) && item.eventKey().equals(actualEvent.eventKey()) && item.evidenceHash().equals(actualEvent.evidenceHash()) && item.subject().equals(request.speaker()) && (item.happenedMinute() <= request.nowMinute()) && (request.nowMinute() - item.happenedMinute() <= 1) && (item.expiryMinute() > request.nowMinute()) && !_v4.initiativeCategories(item.eventKey()).isEmpty())
			.max(java.util.Comparator.comparingLong(org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.MemorySnapshot::happenedMinute));
		if (trigger.isEmpty() && memory.isEmpty()) { return Decision.ineligible(); }
		final boolean initiative = memory.isPresent();
		final String event = initiative ? memory.get().eventKey() : "";
		final String semanticHash = initiative ? memory.get().evidenceHash() : trigger.get().normalizedHash();
		final String consumedEvent = initiative ? PhantomHumanizedCatalog.sha256("v4.initiative|" + memory.get().eventId()) : "";
		final String subject = request.speaker().kind() == SubjectKind.PHANTOM_PROFILE ? "profile:" + request.speaker().id() : "character.object:" + request.speaker().id();
		final List<String> categories = initiative ? _v4.initiativeCategories(event) : List.of(trigger.get().category());
		final List<String> stances = initiative ? switch (event)
		{
			case "pvp.attack.received" -> List.of("SET_BOUNDARY", "DISENGAGE", "NEUTRAL");
			case "pvp.death.suffered" -> List.of("SUPPORTIVE", "NEUTRAL", "DISENGAGE");
			case "pvp.kill.caused" -> List.of("AMUSED", "NEUTRAL", "SUPPORTIVE");
			default -> List.of("SUPPORTIVE", "AMUSED", "NEUTRAL", "CURIOUS");
		} : List.of(_v4.stance(trigger.get(), persona, band, social.memories(), request.speaker(), request.nowMinute()));
		RuntimeException lastFailure = null;
		for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++)
		{
			final StoredState stored = _personal.load(request.ownerProfileId()).orElse(null);
			PersonalState state = stored == null ? PersonalState.empty(authorityHash(), request.nowMinute()) : PhantomPersonalConversationStore.prepare(stored.state(), authorityHash(), request.nowMinute());
			final Set<String> recent = PhantomPersonalConversationStore.recentResponses(state, subject);
			if (((initiative || (request.origin() == Origin.PHANTOM_SOCIAL)) && !PhantomPersonalConversationStore.generatedEligible(state, subject, request.nowMinute(), _catalog.limits())) || (initiative && recent.contains(consumedEvent)))
			{
				return suppressedV4(categories.getFirst(), persona.key(), band, semanticHash, "cooldown_or_consumed_event");
			}
			Optional<Selection> selection = Optional.empty();
			String category = categories.getFirst();
			String stance = stances.getFirst();
			for (String candidate : categories)
			{
				for (String candidateStance : stances)
				{
					selection = _v4.select(candidate, initiative ? "INITIATIVE" : "REPLY", candidateStance, event, persona, band, _settings.register(), _settings.profanity(), _settings.matureEnabled(), request.channel() == ChatType.WHISPER, request.speaker().kind() == SubjectKind.PHANTOM_PROFILE, request.identity().available() ? request.identity().displayName() : request.ownerName(), selector(request.observationHash() + '|' + persona.key()), recent);
					if (selection.isPresent()) { stance = candidateStance; break; }
				}
				if (selection.isPresent()) { category = candidate; break; }
			}
			if (selection.isEmpty()) { return suppressedV4(category, persona.key(), band, semanticHash, "no_permitted_candidate"); }
			final Selection selected = selection.get();
			final String act = _v4.act(category);
			final Match match = new Match("v4." + semanticHash, category.length() <= 64 ? category : "v4." + PhantomHumanizedCatalog.sha256(category).substring(0, 32), act, null, null, "", 0, 0, semanticHash);
			state = PhantomPersonalConversationStore.apply(state, subject, match, initiative ? consumedEvent : selected.responseHash(), initiative ? Origin.PHANTOM_SOCIAL : request.origin(), request.nowMinute(), _catalog.limits());
			try
			{
				_personal.save(request.ownerProfileId(), stored == null ? -1 : stored.rowVersion(), state);
			}
			catch (RuntimeException exception) { lastFailure = exception; continue; }
			final String socialEvent = Set.of("OFFENDED", "SET_BOUNDARY", "DISENGAGE").contains(stance) ? "conversation.conflict" : stance.equals("SUPPORTIVE") ? "conversation.supportive" : "";
			if (!socialEvent.isEmpty()) { _social.record(new SocialEvent(request.ownerProfileId(), org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.sha256("v4.social|" + request.observationHash() + '|' + socialEvent), socialEvent, request.speaker(), request.nowMinute(), 1000, semanticHash.toUpperCase(java.util.Locale.ROOT))); }
			return new Decision(true, false, category, act, selected.text(), semanticHash, selected.templateId(), persona.key(), band, initiative, selected.profanityUsed(), selected.matureUsed(), socialEvent, List.of(new Evidence("humanized.v4", _v4.hash()), new Evidence("humanized.stance", stance.toLowerCase(java.util.Locale.ROOT)), new Evidence("humanized.persona", persona.key()), new Evidence("humanized.event", initiative ? event : "none")));
		}
		throw new IllegalStateException("conversation.personal v4 optimistic retries exhausted.", lastFailure);
	}

	private Decision suppressedV4(String category, String persona, RelationshipBand band, String semanticHash, String reason)
	{
		return new Decision(true, true, category, _v4.act(category), "", semanticHash, "", persona, band, false, false, false, "", List.of(new Evidence("humanized.v4_gate", reason)));
	}

	public static RelationshipBand relationshipBand(Map<String, Integer> relationship)
	{
		final int trust = relationship.getOrDefault("trust", 0);
		final int respect = relationship.getOrDefault("respect", 0);
		final int friendship = relationship.getOrDefault("friendship", 0);
		final int anger = relationship.getOrDefault("anger", 0);
		final int rivalry = relationship.getOrDefault("rivalry", 0);
		final int positive = trust + respect + friendship;
		final int negative = anger + rivalry;
		if ((positive == 0) && (negative == 0))
		{
			return RelationshipBand.UNKNOWN;
		}
		if (negative >= 3500)
		{
			return RelationshipBand.HOSTILE;
		}
		if ((rivalry >= 1200) && (rivalry > anger))
		{
			return RelationshipBand.RIVAL;
		}
		if (negative >= 1200)
		{
			return RelationshipBand.TENSE;
		}
		if (positive >= 4500)
		{
			return RelationshipBand.TRUSTED;
		}
		if (positive >= 1200)
		{
			return RelationshipBand.FAMILIAR;
		}
		return RelationshipBand.NEUTRAL;
	}

	public static boolean relationshipActEligible(String act, RelationshipBand band)
	{
		if ("teasing.reply".equals(act))
		{
			return band == RelationshipBand.TRUSTED;
		}
		if ("sarcasm.reply".equals(act))
		{
			return band == RelationshipBand.RIVAL;
		}
		return true;
	}

	public static String executionStyle(RelationshipBand band)
	{
		return switch (band)
		{
			case TRUSTED, FAMILIAR -> "warm";
			case RIVAL -> "cold";
			case TENSE -> "cautious";
			case HOSTILE -> "cold";
			case UNKNOWN, NEUTRAL -> "neutral";
		};
	}

	private static String personaKey(Map<String, Integer> traits, String interest)
	{
		final String voice;
		if (traits.getOrDefault("empathy", 0) >= 6500)
		{
			voice = "empathetic";
		}
		else if (traits.getOrDefault("assertiveness", 0) >= 6500)
		{
			voice = "direct";
		}
		else if (traits.getOrDefault("sociability", 0) >= 6500)
		{
			voice = "open";
		}
		else
		{
			voice = "calm";
		}
		return voice + '.' + interest.substring("interest.".length());
	}

	private static String socialEvent(String act)
	{
		return switch (act)
		{
			case "achievement.congratulate", "mood.empathy", "failure.empathy" -> "conversation.supportive";
			case "irritation.reply", "disagreement.reply" -> "conversation.conflict";
			case "apology.accept" -> "conversation.apology";
			case "reconcile.reply" -> "conversation.reconciled";
			default -> "";
		};
	}

	private static long selector(String value)
	{
		return Long.parseUnsignedLong(PhantomHumanizedCatalog.sha256(value).substring(0, 16), 16);
	}
}
