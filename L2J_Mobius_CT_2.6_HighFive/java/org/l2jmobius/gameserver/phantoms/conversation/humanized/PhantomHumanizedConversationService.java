/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.conversation.humanized;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.l2jmobius.gameserver.model.chat.ChatObservationService.Origin;
import org.l2jmobius.gameserver.network.enums.ChatType;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog.Match;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog.ProfanityMode;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog.Register;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog.RelationshipBand;
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
public final class PhantomHumanizedConversationService
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

	public record Request(long ownerProfileId, String ownerName, SubjectRef speaker, Origin origin, ChatType channel, String text, String observationHash, long nowMinute)
	{
		public Request
		{
			if ((ownerProfileId <= 0) || (ownerName == null) || ownerName.isBlank() || (ownerName.length() > 64) || (speaker == null) || ((origin != Origin.CLIENT_CHAT) && (origin != Origin.PHANTOM_SOCIAL)) || (channel == null) || (text == null) || text.isBlank() || (text.length() > 1024) || (observationHash == null) || !observationHash.matches("[0-9A-Fa-f]{64}") || (nowMinute < 0))
			{
				throw new IllegalArgumentException("Humanized conversation request is invalid.");
			}
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

	private static final int MAX_ATTEMPTS = 3;
	private final PhantomHumanizedCatalog _catalog;
	private final PhantomPersonalConversationStore _personal;
	private final PhantomSocialService _social;
	private final Settings _settings;

	public PhantomHumanizedConversationService(PhantomHumanizedCatalog catalog, PhantomPersonalConversationStore personal, PhantomSocialService social, Settings settings)
	{
		_catalog = Objects.requireNonNull(catalog);
		_personal = Objects.requireNonNull(personal);
		_social = Objects.requireNonNull(social);
		_settings = Objects.requireNonNull(settings);
	}

	public boolean enabled()
	{
		return _settings.enabled();
	}

	public String authorityHash()
	{
		return _catalog.combinedHash();
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
		final Optional<Match> matched = _catalog.understand(request.text());
		if (matched.isEmpty())
		{
			return Decision.ineligible();
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
		final String subjectKey = request.speaker().kind() == SubjectKind.PHANTOM_PROFILE ? "profile:" + request.speaker().id() : "character.object:" + request.speaker().id();
		RuntimeException lastFailure = null;
		for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++)
		{
			final StoredState stored = _personal.load(request.ownerProfileId()).orElse(null);
			PersonalState state = stored == null ? PersonalState.empty(_catalog.combinedHash(), request.nowMinute()) : PhantomPersonalConversationStore.prepare(stored.state(), _catalog.combinedHash(), request.nowMinute());
			if ((request.origin() == Origin.PHANTOM_SOCIAL) && !PhantomPersonalConversationStore.generatedEligible(state, subjectKey, request.nowMinute(), _catalog.limits()))
			{
				return new Decision(true, true, match.topic(), match.act(), "", match.normalizedHash(), "", personaKey, band, false, false, false, "", List.of(new Evidence("humanized.loop_guard", "turn_budget_or_cooldown"), new Evidence("humanized.origin", "phantom_social")));
			}
			final Optional<String> memory = match.recall() == null ? Optional.empty() : PhantomPersonalConversationStore.recall(state, subjectKey, match.recall(), request.nowMinute());
			final Set<String> recent = PhantomPersonalConversationStore.recentResponses(state, subjectKey);
			final long responseSelector = selector(request.ownerProfileId() + "|" + request.observationHash() + "|" + personaKey + "|" + band + "|" + _catalog.combinedHash());
			final Selection selection = _catalog.select(match.act(), band, _settings.register(), _settings.profanity(), _settings.variation(), _settings.matureEnabled(), request.channel() == ChatType.WHISPER, request.ownerName(), match.value(), memory.orElse("не успел запомнить"), interest.label(), responseSelector, recent);
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
					new Evidence("humanized.origin", request.origin() == Origin.CLIENT_CHAT ? "client_chat" : "phantom_social"));
				return new Decision(true, false, match.topic(), match.act(), selection.text(), match.normalizedHash(), selection.templateId(), personaKey, band, memory.isPresent(), selection.profanityUsed(), selection.matureUsed(), eventKey, evidence);
			}
			catch (RuntimeException exception)
			{
				lastFailure = exception;
			}
		}
		throw new IllegalStateException("conversation.personal optimistic retries exhausted.", lastFailure);
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
