/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.semantic.understanding;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticGrounding.Authority;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.EntityCandidate;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.EvidenceQuality;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.FragmentResult;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.InputContext;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.IntentCandidate;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.NormalizedText;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.PlayerReference;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.SlotType;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.SlotValue;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.Token;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.TokenKind;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.UnderstandingEvidence;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.UnderstandingResult;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.UnderstandingStatus;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticPack.ContextAlias;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticPack.ContextResolver;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticPack.EntityAlias;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticPack.IntentDefinition;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticPack.LiteralPart;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticPack.PatternDefinition;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticPack.PatternPart;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticPack.SlotPart;

/**
 * Deterministic understanding only. Accepted output is interpretation, never permission to act.
 */
public final class PhantomSemanticUnderstandingService
{
	private static final long STOP_WAIT_MILLIS = 5000L;
	private static final Comparator<IntentCandidate> CANDIDATE_ORDER = Comparator.comparingInt(IntentCandidate::score).reversed().thenComparing(Comparator.comparingInt(IntentCandidate::exactEvidence).reversed()).thenComparing(Comparator.comparingInt((IntentCandidate candidate) -> candidate.slots().size()).reversed()).thenComparingInt(IntentCandidate::fuzzyEvidence).thenComparing(IntentCandidate::intentKey).thenComparing(IntentCandidate::canonicalSlots);

	public enum State
	{
		NEW,
		RUNNING,
		STOPPING,
		STOPPED,
		FAILED
	}

	@FunctionalInterface
	public interface OperationProbe
	{
		void afterNormalization(NormalizedText normalized);

		static OperationProbe noop()
		{
			return _ ->
			{
			};
		}
	}

	private final Object _monitor = new Object();
	private final Supplier<PhantomSemanticPack> _loader;
	private final OperationProbe _operationProbe;
	private final Metrics _metrics = new Metrics();
	private State _state = State.NEW;
	private boolean _stopping;
	private boolean _startClaimed;
	private int _operationClaims;
	private PhantomSemanticPack _pack;
	private String _lastFailureCategory = "none";

	public PhantomSemanticUnderstandingService(Supplier<PhantomSemanticPack> loader)
	{
		this(loader, OperationProbe.noop());
	}

	public PhantomSemanticUnderstandingService(Supplier<PhantomSemanticPack> loader, OperationProbe operationProbe)
	{
		_loader = Objects.requireNonNull(loader, "Semantic pack loader must not be null.");
		_operationProbe = Objects.requireNonNull(operationProbe, "Semantic operation probe must not be null.");
	}

	public static PhantomSemanticUnderstandingService production(Path xmlPath, Path corpusPath, Authority authority)
	{
		return new PhantomSemanticUnderstandingService(() -> PhantomSemanticPack.load(xmlPath, corpusPath, authority));
	}

	public static PhantomSemanticUnderstandingService loaded(PhantomSemanticPack pack)
	{
		return new PhantomSemanticUnderstandingService(() -> Objects.requireNonNull(pack));
	}

	public boolean start()
	{
		synchronized (_monitor)
		{
			if ((_state != State.NEW) || _startClaimed)
			{
				return false;
			}
			_startClaimed = true;
		}
		final PhantomSemanticPack candidate;
		try
		{
			candidate = Objects.requireNonNull(_loader.get(), "Semantic pack loader returned null.");
		}
		catch (RuntimeException exception)
		{
			synchronized (_monitor)
			{
				_startClaimed = false;
				if (_state == State.NEW)
				{
					_state = State.FAILED;
				}
				_lastFailureCategory = "pack";
				_monitor.notifyAll();
			}
			_metrics._startsFailed.increment();
			throw exception;
		}
		synchronized (_monitor)
		{
			_startClaimed = false;
			if (_stopping || (_state != State.NEW))
			{
				_state = State.STOPPED;
				_monitor.notifyAll();
				return false;
			}
			_pack = candidate;
			_state = State.RUNNING;
			_monitor.notifyAll();
		}
		_metrics._startsCompleted.increment();
		return true;
	}

	public UnderstandingResult understand(String input, InputContext context)
	{
		Objects.requireNonNull(context, "Semantic input context must not be null.");
		final PhantomSemanticPack pack;
		synchronized (_monitor)
		{
			if ((_state != State.RUNNING) || _stopping || (_pack == null))
			{
				_metrics._rejectedOperations.increment();
				throw new IllegalStateException("Semantic understanding has no running immutable generation.");
			}
			_operationClaims++;
			pack = _pack;
		}
		try
		{
			final NormalizedText normalized;
			try
			{
				normalized = PhantomSemanticNormalizer.normalize(input, pack);
			}
			catch (PhantomSemanticNormalizer.Rejection rejection)
			{
				final UnderstandingResult result = rejected(pack, rejection.reasonKey());
				_metrics.record(result, 0, 0, 0);
				return result;
			}
			_operationProbe.afterNormalization(normalized);
			final ParseOutcome outcome = parse(normalized, context, pack);
			_metrics.record(outcome.result(), normalized.tokens().size(), outcome.candidatesConsidered(), outcome.fuzzyCandidates());
			return outcome.result();
		}
		finally
		{
			synchronized (_monitor)
			{
				_operationClaims--;
				_monitor.notifyAll();
			}
		}
	}

	/**
	 * Resolves only the explicitly supplied slot families. It deliberately has no
	 * intent selection path and therefore cannot turn a clarification fragment into
	 * a new command.
	 */
	public FragmentResult resolveFragment(String input, InputContext context, Set<SlotType> expectedSlots)
	{
		Objects.requireNonNull(context, "Semantic fragment context must not be null.");
		if ((expectedSlots == null) || expectedSlots.isEmpty() || (expectedSlots.size() > 4) || expectedSlots.contains(SlotType.RESPONSE))
		{
			throw new IllegalArgumentException("Semantic fragment expected slots must contain one to four resolvable slot types.");
		}
		final PhantomSemanticPack pack;
		synchronized (_monitor)
		{
			if ((_state != State.RUNNING) || _stopping || (_pack == null))
			{
				_metrics._rejectedOperations.increment();
				throw new IllegalStateException("Semantic understanding has no running immutable generation.");
			}
			_operationClaims++;
			pack = _pack;
		}
		try
		{
			final NormalizedText normalized;
			try
			{
				normalized = PhantomSemanticNormalizer.normalize(input, pack);
			}
			catch (PhantomSemanticNormalizer.Rejection rejection)
			{
				return fragment(pack, UnderstandingStatus.REJECTED, PhantomSemanticNormalizer.EMPTY_HASH, List.of(), rejection.reasonKey(), List.of());
			}
			final List<Token> words = normalized.tokens().stream().filter(token -> token.kind() != TokenKind.PUNCTUATION).filter(token -> !pack.isFiller(token.canonicalValue())).toList();
			if (words.isEmpty())
			{
				return fragment(pack, UnderstandingStatus.REJECTED, normalized.normalizedHash(), List.of(), "reject.unsupported", List.of());
			}
			final CandidateBudget budget = new CandidateBudget(pack.limits().maxCandidates());
			final List<SlotValue> slots = new ArrayList<>();
			final List<UnderstandingEvidence> evidence = new ArrayList<>();
			String clarification = null;
			for (SlotType type : expectedSlots.stream().sorted().toList())
			{
				final SlotResolution resolution = resolveSlot(type, words, context, pack, budget);
				if (resolution.value() != null)
				{
					slots.add(resolution.value());
					if ((resolution.evidence() != null) && (evidence.size() < pack.limits().maxEvidence()))
					{
						evidence.add(resolution.evidence());
					}
				}
				else if (clarification == null)
				{
					clarification = resolution.reason();
				}
			}
			if (budget.incomplete())
			{
				return fragment(pack, UnderstandingStatus.CLARIFICATION_REQUIRED, normalized.normalizedHash(), List.of(), "clarify.complexity", List.of());
			}
			return slots.isEmpty() ? fragment(pack, UnderstandingStatus.CLARIFICATION_REQUIRED, normalized.normalizedHash(), List.of(), clarification == null ? "clarify.entity" : clarification, evidence) : fragment(pack, UnderstandingStatus.ACCEPTED, normalized.normalizedHash(), slots, "accept.matched", evidence);
		}
		finally
		{
			synchronized (_monitor)
			{
				_operationClaims--;
				_monitor.notifyAll();
			}
		}
	}

	public boolean beginStop()
	{
		synchronized (_monitor)
		{
			if ((_state == State.STOPPED) || (_state == State.STOPPING) || _stopping)
			{
				return false;
			}
			_stopping = true;
			if ((_state == State.NEW) || (_state == State.RUNNING))
			{
				_state = State.STOPPING;
			}
			return true;
		}
	}

	public boolean finishStop()
	{
		synchronized (_monitor)
		{
			if (_state == State.STOPPED)
			{
				return true;
			}
			if (!_stopping && (_state != State.FAILED))
			{
				return false;
			}
			final long deadline = System.nanoTime() + (STOP_WAIT_MILLIS * 1_000_000L);
			while (_startClaimed || (_operationClaims > 0))
			{
				final long remainingNanos = deadline - System.nanoTime();
				if (remainingNanos <= 0)
				{
					_state = State.FAILED;
					_lastFailureCategory = "operation-drain";
					return false;
				}
				try
				{
					final long waitMillis = Math.max(1L, Math.min(STOP_WAIT_MILLIS, remainingNanos / 1_000_000L));
					_monitor.wait(waitMillis);
				}
				catch (InterruptedException exception)
				{
					Thread.currentThread().interrupt();
					_state = State.FAILED;
					_lastFailureCategory = "interrupted";
					return false;
				}
			}
			_pack = null;
			_stopping = true;
			_state = State.STOPPED;
			return true;
		}
	}

	public Snapshot snapshot()
	{
		synchronized (_monitor)
		{
			final PhantomSemanticPack pack = _pack;
			return new Snapshot(_state, _stopping, _operationClaims, pack == null ? "none" : pack.id(), pack == null ? 0 : pack.version(), pack == null ? "none" : pack.packHash(), pack == null ? "none" : pack.corpusHash(), pack == null ? "none" : pack.authorityHashes().knowledgeHash(), pack == null ? "none" : pack.authorityHashes().topologyHash(), pack == null ? "none" : pack.authorityHashes().partyRoleHash(), pack == null ? 0 : pack.corpus().size(), pack == null ? 0 : pack.patternCount(), pack == null ? 0 : pack.entityAliasCount(), _lastFailureCategory, _metrics.snapshot());
		}
	}

	private static ParseOutcome parse(NormalizedText normalized, InputContext context, PhantomSemanticPack pack)
	{
		final List<Token> words = normalized.tokens().stream().filter(token -> token.kind() != TokenKind.PUNCTUATION).filter(token -> !pack.isFiller(token.canonicalValue())).toList();
		if (words.isEmpty())
		{
			return new ParseOutcome(rejected(pack, normalized.normalizedHash(), "reject.unsupported"), 0, 0);
		}
		final CandidateBudget budget = new CandidateBudget(pack.limits().maxCandidates());
		final LinkedHashMap<String, PatternDefinition> patterns = candidatePatterns(words.getFirst(), pack, budget);
		final ArrayList<IntentCandidate> candidates = new ArrayList<>();
		int fuzzyCandidates = 0;
		for (PatternDefinition pattern : patterns.values())
		{
			final ArrayList<RawMatch> matches = new ArrayList<>();
			match(pattern, words, 0, 0, new EnumMap<>(SlotType.class), new ArrayList<>(), new MatchCounts(), matches, pack, budget);
			for (RawMatch match : matches)
			{
				final IntentCandidate candidate = resolve(pattern, match, context, pack, budget);
				if (candidate == null)
				{
					continue;
				}
				fuzzyCandidates += candidate.fuzzyEvidence();
				candidates.add(candidate);
			}
		}
		if (budget.incomplete())
		{
			return new ParseOutcome(result(pack, UnderstandingStatus.CLARIFICATION_REQUIRED, normalized.normalizedHash(), "unknown", 0, List.of(), List.of(), "clarify.complexity", List.of(new UnderstandingEvidence("budget.incomplete", EvidenceQuality.EXACT, -1, -1, "candidate-budget"))), budget.count(), fuzzyCandidates);
		}
		if (candidates.isEmpty())
		{
			return new ParseOutcome(rejected(pack, normalized.normalizedHash(), "reject.unsupported"), budget.count(), fuzzyCandidates);
		}
		final Map<String, IntentCandidate> unique = new HashMap<>();
		for (IntentCandidate candidate : candidates)
		{
			final String key = candidate.intentKey() + '|' + candidate.reasonKey() + '|' + candidate.canonicalSlots();
			final IntentCandidate current = unique.get(key);
			if ((current == null) || (CANDIDATE_ORDER.compare(candidate, current) < 0))
			{
				unique.put(key, candidate);
			}
		}
		final List<IntentCandidate> ranked = unique.values().stream().sorted(CANDIDATE_ORDER).toList();
		final IntentCandidate selected = ranked.getFirst();
		String reason = selected.reasonKey();
		UnderstandingStatus status = "accept.matched".equals(reason) ? UnderstandingStatus.ACCEPTED : UnderstandingStatus.CLARIFICATION_REQUIRED;
		if ((status == UnderstandingStatus.ACCEPTED) && (ranked.size() > 1) && !ranked.get(1).intentKey().equals(selected.intentKey()) && ((selected.score() - ranked.get(1).score()) <= pack.limits().ambiguityMargin()))
		{
			status = UnderstandingStatus.CLARIFICATION_REQUIRED;
			reason = "clarify.intent";
		}
		if ((status == UnderstandingStatus.ACCEPTED) && (selected.score() < pack.limits().acceptanceThreshold()))
		{
			status = UnderstandingStatus.CLARIFICATION_REQUIRED;
			reason = "clarify.intent";
		}
		final List<IntentCandidate> alternatives = ranked.stream().skip(1).limit(pack.limits().maxAlternatives()).toList();
		final UnderstandingResult result = result(pack, status, normalized.normalizedHash(), selected.intentKey(), selected.score(), selected.slots(), alternatives, reason, selected.evidence());
		return new ParseOutcome(result, budget.count(), fuzzyCandidates);
	}

	private static LinkedHashMap<String, PatternDefinition> candidatePatterns(Token first, PhantomSemanticPack pack, CandidateBudget budget)
	{
		final LinkedHashMap<String, PatternDefinition> result = new LinkedHashMap<>();
		final List<PatternDefinition> exact = pack.exactPatterns(first.canonicalValue());
		if (!exact.isEmpty())
		{
			for (PatternDefinition pattern : exact)
			{
				if (budget.claim())
				{
					result.put(pattern.id(), pattern);
				}
			}
			return result;
		}
		final int length = codePoints(first.canonicalValue());
		final int maximumDistance = pack.typoPolicy().maximumDistance(length);
		if (maximumDistance == 0)
		{
			return result;
		}
		for (int candidateLength = Math.max(pack.typoPolicy().minimumCodePoints(), length - maximumDistance); candidateLength <= (length + maximumDistance); candidateLength++)
		{
			for (PatternDefinition pattern : pack.patternsWithFirstLength(candidateLength))
			{
				if (!budget.claim())
				{
					return result;
				}
				if (sameScript(first.canonicalValue(), pattern.firstLiteral()) && (distance(first.canonicalValue(), pattern.firstLiteral(), maximumDistance) <= maximumDistance))
				{
					result.putIfAbsent(pattern.id(), pattern);
				}
			}
		}
		return result;
	}

	private static void match(PatternDefinition pattern, List<Token> words, int partIndex, int tokenIndex, EnumMap<SlotType, List<Token>> captures, ArrayList<UnderstandingEvidence> evidence, MatchCounts counts, List<RawMatch> matches, PhantomSemanticPack pack, CandidateBudget budget)
	{
		if ((matches.size() >= pack.limits().maxCandidates()) || budget.exhausted())
		{
			budget.skip();
			return;
		}
		if (partIndex == pattern.parts().size())
		{
			if ((tokenIndex == words.size()) && budget.claim())
			{
				matches.add(new RawMatch(Map.copyOf(captures), List.copyOf(evidence), counts.copy()));
			}
			return;
		}
		final PatternPart part = pattern.parts().get(partIndex);
		if (part instanceof LiteralPart literal)
		{
			if (tokenIndex >= words.size())
			{
				return;
			}
			final Token token = words.get(tokenIndex);
			final EvidenceQuality quality = literalQuality(token, literal.value(), pack);
			if (quality == null)
			{
				return;
			}
			final MatchCounts nextCounts = counts.with(quality);
			final ArrayList<UnderstandingEvidence> nextEvidence = new ArrayList<>(evidence);
			if (nextEvidence.size() < pack.limits().maxEvidence())
			{
				nextEvidence.add(new UnderstandingEvidence("pattern.literal", quality, token.originalStartCodePoint(), token.originalEndCodePoint(), pattern.id() + ':' + literal.value()));
			}
			match(pattern, words, partIndex + 1, tokenIndex + 1, captures, nextEvidence, nextCounts, matches, pack, budget);
			return;
		}
		final SlotType slot = ((SlotPart) part).type();
		final int minimumRemaining = pattern.parts().size() - partIndex - 1;
		final int maximumLength = Math.min(pack.slot(slot).maximumTokens(), words.size() - tokenIndex - minimumRemaining);
		for (int length = 1; length <= maximumLength; length++)
		{
			final EnumMap<SlotType, List<Token>> nextCaptures = new EnumMap<>(captures);
			nextCaptures.put(slot, List.copyOf(words.subList(tokenIndex, tokenIndex + length)));
			match(pattern, words, partIndex + 1, tokenIndex + length, nextCaptures, evidence, counts, matches, pack, budget);
		}
	}

	private static EvidenceQuality literalQuality(Token token, String literal, PhantomSemanticPack pack)
	{
		if (token.canonicalValue().equals(literal))
		{
			return token.lexicalQuality();
		}
		final int length = codePoints(token.canonicalValue());
		final int maximum = pack.typoPolicy().maximumDistance(length);
		if ((maximum == 0) || !sameScript(token.canonicalValue(), literal) || (Math.abs(length - codePoints(literal)) > maximum))
		{
			return null;
		}
		return distance(token.canonicalValue(), literal, maximum) <= maximum ? EvidenceQuality.FUZZY : null;
	}

	private static IntentCandidate resolve(PatternDefinition pattern, RawMatch match, InputContext context, PhantomSemanticPack pack, CandidateBudget budget)
	{
		final IntentDefinition intent = pack.intent(pattern.intentKey());
		final ArrayList<SlotValue> slots = new ArrayList<>();
		final ArrayList<UnderstandingEvidence> evidence = new ArrayList<>(match.evidence());
		String reason = null;
		int resolved = 0;
		int fuzzy = match.counts().fuzzy();
		for (var capture : match.captures().entrySet())
		{
			if (hasCrossSlotExactConflict(capture.getKey(), capture.getValue(), pack))
			{
				return null;
			}
			final SlotResolution resolution = resolveSlot(capture.getKey(), capture.getValue(), context, pack, budget);
			if (resolution.value() != null)
			{
				slots.add(resolution.value());
				resolved++;
			}
			if (resolution.evidence() != null && (evidence.size() < pack.limits().maxEvidence()))
			{
				evidence.add(resolution.evidence());
			}
			fuzzy += resolution.fuzzy() ? 1 : 0;
			if ((reason == null) && (resolution.reason() != null))
			{
				reason = resolution.reason();
			}
		}
		final Set<SlotType> resolvedTypes = slots.stream().map(SlotValue::type).collect(java.util.stream.Collectors.toSet());
		for (SlotType required : intent.requiredAll().stream().sorted().toList())
		{
			if (!resolvedTypes.contains(required) && (reason == null))
			{
				reason = reasonFor(required);
			}
		}
		if (!intent.requiredAny().isEmpty() && CollectionsDisjoint(intent.requiredAny(), resolvedTypes) && (reason == null))
		{
			reason = reasonFor(intent.requiredAny().stream().sorted().findFirst().orElseThrow());
		}
		if (reason == null)
		{
			reason = "accept.matched";
		}
		final MatchCounts counts = match.counts();
		final int missing = (int) intent.requiredAll().stream().filter(required -> !resolvedTypes.contains(required)).count() + ((!intent.requiredAny().isEmpty() && CollectionsDisjoint(intent.requiredAny(), resolvedTypes)) ? 1 : 0);
		int score = intent.baseScore() + Math.min(1200, counts.exact() * 180) + (resolved * 450) - (counts.transliteration() * 250) - (counts.abbreviation() * 300) - (fuzzy * 1000) - (missing * 800) - ("accept.matched".equals(reason) ? 0 : 700);
		score = Math.max(0, Math.min(10000, score));
		return new IntentCandidate(intent.key(), score, slots, reason, evidence.stream().limit(pack.limits().maxEvidence()).toList(), counts.exact(), fuzzy);
	}

	private static boolean hasCrossSlotExactConflict(SlotType slot, List<Token> capture, PhantomSemanticPack pack)
	{
		if ((slot == SlotType.TARGET_PLAYER) || (slot == SlotType.QUANTITY) || (slot == SlotType.RESPONSE))
		{
			return false;
		}
		final List<String> phrase = capture.stream().map(Token::value).toList();
		if (!pack.exactEntities(slot, phrase).isEmpty())
		{
			return false;
		}
		for (SlotType other : SlotType.values())
		{
			if ((other != slot) && !pack.exactEntities(other, phrase).isEmpty())
			{
				return true;
			}
		}
		return false;
	}

	private static boolean CollectionsDisjoint(Set<SlotType> left, Set<SlotType> right)
	{
		for (SlotType value : left)
		{
			if (right.contains(value))
			{
				return false;
			}
		}
		return true;
	}

	private static SlotResolution resolveSlot(SlotType slot, List<Token> capture, InputContext context, PhantomSemanticPack pack, CandidateBudget budget)
	{
		final int start = capture.getFirst().originalStartCodePoint();
		final int end = capture.getLast().originalEndCodePoint();
		if (slot == SlotType.QUANTITY)
		{
			if ((capture.size() == 1) && (capture.getFirst().kind() == TokenKind.NUMBER) && capture.getFirst().value().matches("[1-9][0-9]{0,8}"))
			{
				final long value = Long.parseLong(capture.getFirst().value());
				return new SlotResolution(SlotValue.quantity(value, start, end), new UnderstandingEvidence("slot.quantity", EvidenceQuality.EXACT, start, end, Long.toString(value)), null, false);
			}
			return new SlotResolution(null, null, "clarify.quantity", false);
		}
		if (slot == SlotType.RESPONSE)
		{
			final String response = String.join(" ", capture.stream().map(Token::canonicalValue).toList());
			return new SlotResolution(SlotValue.response(response, start, end), new UnderstandingEvidence("slot.response", EvidenceQuality.EXACT, start, end, response), null, false);
		}
		if (slot == SlotType.TARGET_PLAYER)
		{
			return resolvePlayer(capture, context, pack);
		}
		return resolveAuthority(slot, capture, context, pack, budget);
	}

	private static SlotResolution resolvePlayer(List<Token> capture, InputContext context, PhantomSemanticPack pack)
	{
		final int start = capture.getFirst().originalStartCodePoint();
		final int end = capture.getLast().originalEndCodePoint();
		final List<String> phrase = capture.stream().map(Token::value).toList();
		final LinkedHashSet<PhantomDomainRef> candidates = new LinkedHashSet<>();
		for (PlayerReference player : allPlayers(context, true))
		{
			if (playerNameTokens(player, pack).equals(phrase))
			{
				candidates.add(player.reference());
			}
		}
		for (ContextAlias alias : pack.contexts(SlotType.TARGET_PLAYER, phrase))
		{
			candidates.addAll(resolveContextPlayers(alias.resolver(), context));
		}
		if (candidates.size() == 1)
		{
			final PhantomDomainRef reference = candidates.getFirst();
			return new SlotResolution(SlotValue.domain(SlotType.TARGET_PLAYER, reference, start, end), new UnderstandingEvidence("slot.target_player", EvidenceQuality.CONTEXT, start, end, reference.namespace() + ':' + reference.key()), null, false);
		}
		return new SlotResolution(null, null, "clarify.target_player", false);
	}

	private static SlotResolution resolveAuthority(SlotType slot, List<Token> capture, InputContext context, PhantomSemanticPack pack, CandidateBudget budget)
	{
		final int start = capture.getFirst().originalStartCodePoint();
		final int end = capture.getLast().originalEndCodePoint();
		final List<String> phrase = capture.stream().map(Token::value).toList();
		final LinkedHashMap<PhantomDomainRef, EvidenceQuality> candidates = new LinkedHashMap<>();
		for (EntityAlias alias : pack.exactEntities(slot, phrase))
		{
			if (budget.claim())
			{
				candidates.merge(alias.reference(), alias.quality(), PhantomSemanticUnderstandingService::betterQuality);
			}
		}
		for (ContextAlias alias : pack.contexts(slot, phrase))
		{
			final PhantomDomainRef reference = resolveContextDomain(alias.resolver(), context);
			if (reference != null)
			{
				candidates.put(reference, EvidenceQuality.CONTEXT);
			}
		}
		boolean fuzzy = false;
		if (candidates.isEmpty() && (phrase.size() == 1))
		{
			for (SlotType other : SlotType.values())
			{
				if ((other != slot) && !pack.exactEntities(other, phrase).isEmpty())
				{
					return new SlotResolution(null, null, reasonFor(slot), false);
				}
			}
			final String token = phrase.getFirst();
			final int length = codePoints(token);
			final int maximum = pack.typoPolicy().maximumDistance(length);
			int bestDistance = Integer.MAX_VALUE;
			if (maximum > 0)
			{
				for (int candidateLength = Math.max(pack.typoPolicy().minimumCodePoints(), length - maximum); candidateLength <= (length + maximum); candidateLength++)
				{
					for (EntityAlias alias : pack.fuzzyEntities(slot, candidateLength))
					{
						if (!budget.claim())
						{
							break;
						}
						final String aliasToken = alias.tokens().getFirst();
						if (!sameScript(token, aliasToken))
						{
							continue;
						}
						final int currentDistance = distance(token, aliasToken, maximum);
						if (currentDistance > maximum)
						{
							continue;
						}
						if (currentDistance < bestDistance)
						{
							candidates.clear();
							bestDistance = currentDistance;
						}
						if (currentDistance == bestDistance)
						{
							candidates.put(alias.reference(), EvidenceQuality.FUZZY);
						}
					}
				}
				fuzzy = !candidates.isEmpty();
			}
		}
		if (candidates.size() == 1)
		{
			final var entry = candidates.entrySet().iterator().next();
			final EntityCandidate entity = new EntityCandidate(slot, entry.getKey(), qualityScore(entry.getValue()), entry.getValue(), start, end);
			return new SlotResolution(SlotValue.domain(slot, entity.reference(), start, end), new UnderstandingEvidence("slot.entity", entity.quality(), start, end, entity.reference().namespace() + ':' + entity.reference().key()), null, fuzzy);
		}
		return new SlotResolution(null, null, reasonFor(slot), fuzzy);
	}

	private static EvidenceQuality betterQuality(EvidenceQuality left, EvidenceQuality right)
	{
		return qualityScore(left) >= qualityScore(right) ? left : right;
	}

	private static int qualityScore(EvidenceQuality quality)
	{
		return switch (quality)
		{
			case EXACT -> 10000;
			case CONTEXT -> 9800;
			case TRANSLITERATION -> 9000;
			case ABBREVIATION -> 8500;
			case FUZZY -> 7000;
		};
	}

	private static List<PlayerReference> allPlayers(InputContext context, boolean includeSpeaker)
	{
		final LinkedHashMap<PhantomDomainRef, PlayerReference> result = new LinkedHashMap<>();
		if (includeSpeaker && (context.speaker() != null))
		{
			result.put(context.speaker().reference(), context.speaker());
		}
		if (context.partyLeader() != null)
		{
			result.put(context.partyLeader().reference(), context.partyLeader());
		}
		context.partyMembers().forEach(player -> result.put(player.reference(), player));
		context.nearbyPlayers().forEach(player -> result.put(player.reference(), player));
		context.recentPlayers().forEach(player -> result.put(player.reference(), player));
		if (context.selectedTarget() != null)
		{
			result.put(context.selectedTarget().reference(), context.selectedTarget());
		}
		return List.copyOf(result.values());
	}

	private static List<String> playerNameTokens(PlayerReference player, PhantomSemanticPack pack)
	{
		try
		{
			return PhantomSemanticNormalizer.normalize(player.exactName(), pack).tokens().stream().filter(token -> token.kind() != TokenKind.PUNCTUATION).map(Token::value).toList();
		}
		catch (PhantomSemanticNormalizer.Rejection rejection)
		{
			return List.of();
		}
	}

	private static Set<PhantomDomainRef> resolveContextPlayers(ContextResolver resolver, InputContext context)
	{
		return switch (resolver)
		{
			case SPEAKER -> singleton(context.speaker());
			case PARTY_LEADER -> singleton(context.partyLeader());
			case PREVIOUS_TARGET -> context.previousSlots().stream().filter(slot -> slot.type() == SlotType.TARGET_PLAYER).map(SlotValue::domainReference).filter(Objects::nonNull).collect(java.util.stream.Collectors.toUnmodifiableSet());
			case SELECTED_OR_UNIQUE_PLAYER ->
			{
				if (context.selectedTarget() != null)
				{
					yield Set.of(context.selectedTarget().reference());
				}
				final Set<PhantomDomainRef> values = allPlayers(context, false).stream().map(PlayerReference::reference).collect(java.util.stream.Collectors.toUnmodifiableSet());
				yield values.size() == 1 ? values : Set.of();
			}
			default -> Set.of();
		};
	}

	private static Set<PhantomDomainRef> singleton(PlayerReference reference)
	{
		return reference == null ? Set.of() : Set.of(reference.reference());
	}

	private static PhantomDomainRef resolveContextDomain(ContextResolver resolver, InputContext context)
	{
		return switch (resolver)
		{
			case CURRENT_LOCATION -> context.currentLocation();
			case CURRENT_TOPOLOGY -> context.currentTopology();
			default -> null;
		};
	}

	private static String reasonFor(SlotType slot)
	{
		return switch (slot)
		{
			case TARGET_PLAYER -> "clarify.target_player";
			case PARTY_ROLE -> "clarify.party_role";
			case LOCATION, TOPOLOGY_NODE -> "clarify.location";
			case QUANTITY -> "clarify.quantity";
			default -> "clarify.entity";
		};
	}

	private static UnderstandingResult rejected(PhantomSemanticPack pack, String reason)
	{
		return rejected(pack, PhantomSemanticNormalizer.EMPTY_HASH, reason);
	}

	private static UnderstandingResult rejected(PhantomSemanticPack pack, String normalizedHash, String reason)
	{
		return result(pack, UnderstandingStatus.REJECTED, normalizedHash, "unknown", 0, List.of(), List.of(), reason, List.of(new UnderstandingEvidence("normalization.reject", EvidenceQuality.EXACT, -1, -1, reason)));
	}

	private static UnderstandingResult result(PhantomSemanticPack pack, UnderstandingStatus status, String normalizedHash, String intent, int confidence, List<SlotValue> slots, List<IntentCandidate> alternatives, String reason, List<UnderstandingEvidence> evidence)
	{
		return new UnderstandingResult(status, normalizedHash, pack.packHash(), pack.corpusHash(), pack.authorityHashes().knowledgeHash(), pack.authorityHashes().topologyHash(), pack.authorityHashes().partyRoleHash(), intent, confidence, slots, alternatives, reason, evidence.stream().limit(pack.limits().maxEvidence()).toList());
	}

	private static FragmentResult fragment(PhantomSemanticPack pack, UnderstandingStatus status, String normalizedHash, List<SlotValue> slots, String reason, List<UnderstandingEvidence> evidence)
	{
		return new FragmentResult(status, normalizedHash, pack.packHash(), pack.corpusHash(), pack.authorityHashes().knowledgeHash(), pack.authorityHashes().topologyHash(), pack.authorityHashes().partyRoleHash(), slots, reason, evidence.stream().limit(pack.limits().maxEvidence()).toList());
	}

	private static boolean sameScript(String left, String right)
	{
		return script(left) == script(right);
	}

	private static Character.UnicodeScript script(String value)
	{
		for (int codePoint : value.codePoints().toArray())
		{
			if (Character.isLetter(codePoint))
			{
				return Character.UnicodeScript.of(codePoint);
			}
		}
		return Character.UnicodeScript.COMMON;
	}

	private static int codePoints(String value)
	{
		return value.codePointCount(0, value.length());
	}

	static int distance(String left, String right, int maximum)
	{
		final int[] a = left.codePoints().toArray();
		final int[] b = right.codePoints().toArray();
		if (Math.abs(a.length - b.length) > maximum)
		{
			return maximum + 1;
		}
		int[] previousPrevious = new int[b.length + 1];
		int[] previous = new int[b.length + 1];
		int[] current = new int[b.length + 1];
		for (int column = 0; column <= b.length; column++)
		{
			previous[column] = column;
		}
		for (int row = 1; row <= a.length; row++)
		{
			current[0] = row;
			int rowMinimum = current[0];
			for (int column = 1; column <= b.length; column++)
			{
				final int substitution = previous[column - 1] + (a[row - 1] == b[column - 1] ? 0 : 1);
				current[column] = Math.min(Math.min(previous[column] + 1, current[column - 1] + 1), substitution);
				if ((row > 1) && (column > 1) && (a[row - 1] == b[column - 2]) && (a[row - 2] == b[column - 1]))
				{
					current[column] = Math.min(current[column], previousPrevious[column - 2] + 1);
				}
				rowMinimum = Math.min(rowMinimum, current[column]);
			}
			if (rowMinimum > maximum)
			{
				return maximum + 1;
			}
			final int[] swap = previousPrevious;
			previousPrevious = previous;
			previous = current;
			current = swap;
		}
		return previous[b.length];
	}

	public record Snapshot(State state, boolean stopping, int operationClaims, String packId, int packVersion, String packHash, String corpusHash, String knowledgeHash, String topologyHash, String partyRoleHash, int corpusCases, int patterns, int entityAliases, String lastFailureCategory, MetricsSnapshot metrics)
	{
		public static Snapshot inactive()
		{
			return new Snapshot(State.STOPPED, true, 0, "none", 0, "none", "none", "none", "none", "none", 0, 0, 0, "none", MetricsSnapshot.zero());
		}
	}

	public record MetricsSnapshot(long startsCompleted, long startsFailed, long parses, long accepted, long clarifications, long rejected, long rejectedOperations, long fuzzyCandidates, long maximumTokensObserved, long maximumCandidatesObserved)
	{
		private static MetricsSnapshot zero()
		{
			return new MetricsSnapshot(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
		}
	}

	private static final class Metrics
	{
		private final LongAdder _startsCompleted = new LongAdder();
		private final LongAdder _startsFailed = new LongAdder();
		private final LongAdder _parses = new LongAdder();
		private final LongAdder _accepted = new LongAdder();
		private final LongAdder _clarifications = new LongAdder();
		private final LongAdder _rejected = new LongAdder();
		private final LongAdder _rejectedOperations = new LongAdder();
		private final LongAdder _fuzzyCandidates = new LongAdder();
		private final LongAccumulator _maximumTokens = new LongAccumulator(Long::max, 0);
		private final LongAccumulator _maximumCandidates = new LongAccumulator(Long::max, 0);

		private void record(UnderstandingResult result, int tokens, int candidates, int fuzzy)
		{
			_parses.increment();
			switch (result.status())
			{
				case ACCEPTED -> _accepted.increment();
				case CLARIFICATION_REQUIRED -> _clarifications.increment();
				case REJECTED -> _rejected.increment();
			}
			_fuzzyCandidates.add(fuzzy);
			_maximumTokens.accumulate(tokens);
			_maximumCandidates.accumulate(candidates);
		}

		private MetricsSnapshot snapshot()
		{
			return new MetricsSnapshot(_startsCompleted.sum(), _startsFailed.sum(), _parses.sum(), _accepted.sum(), _clarifications.sum(), _rejected.sum(), _rejectedOperations.sum(), _fuzzyCandidates.sum(), _maximumTokens.get(), _maximumCandidates.get());
		}
	}

	private static final class CandidateBudget
	{
		private final int _maximum;
		private int _count;
		private boolean _incomplete;

		private CandidateBudget(int maximum)
		{
			_maximum = maximum;
		}

		private boolean claim()
		{
			if (_count >= _maximum)
			{
				_incomplete = true;
				return false;
			}
			_count++;
			return true;
		}

		private boolean exhausted()
		{
			return _count >= _maximum;
		}

		private boolean incomplete()
		{
			return _incomplete;
		}

		private void skip()
		{
			_incomplete = true;
		}

		private int count()
		{
			return _count;
		}
	}

	private record MatchCounts(int exact, int transliteration, int abbreviation, int fuzzy)
	{
		private MatchCounts()
		{
			this(0, 0, 0, 0);
		}

		private MatchCounts with(EvidenceQuality quality)
		{
			return switch (quality)
			{
				case EXACT, CONTEXT -> new MatchCounts(exact + 1, transliteration, abbreviation, fuzzy);
				case TRANSLITERATION -> new MatchCounts(exact, transliteration + 1, abbreviation, fuzzy);
				case ABBREVIATION -> new MatchCounts(exact, transliteration, abbreviation + 1, fuzzy);
				case FUZZY -> new MatchCounts(exact, transliteration, abbreviation, fuzzy + 1);
			};
		}

		private MatchCounts copy()
		{
			return this;
		}
	}

	private record RawMatch(Map<SlotType, List<Token>> captures, List<UnderstandingEvidence> evidence, MatchCounts counts)
	{
	}

	private record SlotResolution(SlotValue value, UnderstandingEvidence evidence, String reason, boolean fuzzy)
	{
	}

	private record ParseOutcome(UnderstandingResult result, int candidatesConsidered, int fuzzyCandidates)
	{
	}
}
