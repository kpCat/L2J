/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.pvp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ActorSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.PvpConsequenceSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.PvpTargetSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatMode;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.PvpObservation;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.PvpObservedTarget;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingService;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingService.PvpEscalationEvidence;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyCoordinator;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyCoordinator.PvpProtectionEvidence;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberKind;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.Candidate;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.Counterpart;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.CounterpartKind;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.RiskSnapshot;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.Source;
import org.l2jmobius.gameserver.phantoms.social.PhantomPvpSocialBridge;
import org.l2jmobius.gameserver.phantoms.social.PhantomPvpSocialBridge.RevengeEvidence;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SubjectRef;

/**
 * Deterministic bounded causal-source join. It never scans World players or the
 * profile registry; all identities are exact lookups supplied by owner seams.
 */
public final class PhantomPvpContext
{
	public record Snapshot(Candidate candidate, RiskSnapshot risk, PhantomCombatMode mode, SubjectRef socialSubject, PhantomDomainRef conversationCounterpart, PhantomDomainRef helpCounterpart, String sourceEvidenceHash)
	{
		public Snapshot
		{
			Objects.requireNonNull(candidate);
			Objects.requireNonNull(risk);
			Objects.requireNonNull(mode);
			Objects.requireNonNull(socialSubject);
			Objects.requireNonNull(conversationCounterpart);
			sourceEvidenceHash = PhantomPvpModel.boundedHash(sourceEvidenceHash);
		}
	}

	private record SourceEvidence(Source source, int targetObjectId, String authorityHash, long expiryLogicalNanos, PhantomDomainRef helpCounterpart)
	{
	}

	private static final long NANOS_PER_MINUTE = 60_000_000_000L;
	private final PhantomPvpPolicy _policy;
	private final PhantomCombatService _combat;
	private final PhantomPartyCoordinator _party;
	private final PhantomFarmingService _farming;
	private final PhantomPvpSocialBridge _social;
	private final PhantomMaterializationService _materialization;

	public PhantomPvpContext(PhantomPvpPolicy policy, PhantomCombatService combat, PhantomPartyCoordinator party, PhantomFarmingService farming, PhantomPvpSocialBridge social, PhantomMaterializationService materialization)
	{
		_policy = Objects.requireNonNull(policy);
		_combat = Objects.requireNonNull(combat);
		_party = Objects.requireNonNull(party);
		_farming = Objects.requireNonNull(farming);
		_social = Objects.requireNonNull(social);
		_materialization = Objects.requireNonNull(materialization);
	}

	public Optional<Snapshot> observe(long profileId, long logicalNanos)
	{
		if ((profileId <= 0) || (logicalNanos < 0))
		{
			return Optional.empty();
		}
		final long minute = logicalNanos / NANOS_PER_MINUTE;
		final List<PvpProtectionEvidence> partyEvidence = _party.pvpProtection(profileId, _policy.limits().helpFanout());
		final PvpEscalationEvidence farmingEvidence = _farming.pvpEscalation(profileId, minute).orElse(null);
		final Map<Integer, SourceEvidence> exact = new HashMap<>();
		for (PvpProtectionEvidence evidence : partyEvidence)
		{
			final var member = evidence.directive().targetMember();
			final PhantomDomainRef help = member.kind() == MemberKind.PHANTOM ? new PhantomDomainRef("profile", Long.toString(member.profileId())) : new PhantomDomainRef("character.object", Integer.toString(member.characterObjectId()));
			exact.putIfAbsent(evidence.directive().targetObjectId(), new SourceEvidence(Source.PARTY_DEFENSE, evidence.directive().targetObjectId(), evidence.authorityHash(), expiry(logicalNanos, _policy.limits().encounterTtlSeconds()), help));
		}
		if (farmingEvidence != null)
		{
			final var counterpart = _materialization.find(farmingEvidence.counterpartProfileId()).orElse(null);
			if ((counterpart != null) && counterpart.worldPresent())
			{
				exact.putIfAbsent(counterpart.characterObjectId(), new SourceEvidence(Source.FARMING_ESCALATION, counterpart.characterObjectId(), farmingEvidence.authorityHash(), Math.min(expiry(logicalNanos, _policy.limits().encounterTtlSeconds()), minuteNanos(farmingEvidence.expiryMinute())), null));
			}
		}
		final List<Integer> exactTargets = exact.keySet().stream().sorted().limit(10).toList();
		final PvpObservation observation = _combat.observePvp(profileId, exactTargets, _policy.limits().observedAttackerLimit(), _policy.limits().localRiskPlayerLimit()).orElse(null);
		if ((observation == null) || observation.supportedModes().isEmpty())
		{
			return Optional.empty();
		}
		final List<ObservedEvidence> candidates = new ArrayList<>();
		for (PvpObservedTarget observed : observation.targets())
		{
			final int targetObjectId = observed.target().objectId();
			if (observed.actualAttacker())
			{
				final String attackAuthority = PhantomPvpModel.sha256("pvp.actual.attack", observation.actor().objectId(), targetObjectId, observation.actor().instanceId());
				candidates.add(new ObservedEvidence(new SourceEvidence(Source.ACTUAL_ATTACK, targetObjectId, attackAuthority, expiry(logicalNanos, _policy.limits().encounterTtlSeconds()), null), observed));
			}
			final SourceEvidence supplied = exact.get(targetObjectId);
			if (supplied != null)
			{
				candidates.add(new ObservedEvidence(supplied, observed));
			}
			if (observed.selectedTarget())
			{
				final SubjectRef subject = subject(targetObjectId);
				final RevengeEvidence revenge = _social.revenge(profileId, subject, minute).orElse(null);
				if (revenge != null)
				{
					candidates.add(new ObservedEvidence(new SourceEvidence(Source.REVENGE, targetObjectId, revenge.authorityHash(), Math.min(expiry(logicalNanos, _policy.limits().encounterTtlSeconds()), minuteNanos(revenge.expiryMinute())), null), observed));
				}
			}
		}
		candidates.sort(Comparator.comparingInt((ObservedEvidence item) -> priority(item._source.source())).thenComparingInt(item -> item._source.targetObjectId()).thenComparing(item -> item._source.authorityHash()));
		for (ObservedEvidence selected : candidates)
		{
			final Snapshot snapshot = snapshot(profileId, logicalNanos, observation, selected);
			if ((snapshot != null) && snapshot.candidate().currentAt(logicalNanos))
			{
				return Optional.of(snapshot);
			}
		}
		return Optional.empty();
	}

	private Snapshot snapshot(long profileId, long logicalNanos, PvpObservation observation, ObservedEvidence selected)
	{
		final PvpTargetSnapshot target = selected._target.target();
		final ActorSnapshot actor = observation.actor();
		final Counterpart counterpart = counterpart(target.objectId());
		final String authority = PhantomPvpModel.sha256("pvp.current.authority", _policy.hash(), selected._source.source(), selected._source.authorityHash(), actor.objectId(), counterpart.kind(), counterpart.identity(), target.objectId(), actor.instanceId());
		final long expires = Math.min(selected._source.expiryLogicalNanos(), expiry(logicalNanos, _policy.limits().encounterTtlSeconds()));
		if (expires <= logicalNanos)
		{
			return null;
		}
		final boolean resolvable = target.player() && target.exactKnowledge() && !target.dead() && !target.alikeDead() && (target.instanceId() == actor.instanceId());
		final boolean visible = !target.invisible() && target.surroundingRegion();
		final Candidate candidate = new Candidate(profileId, counterpart, selected._source.source(), authority, logicalNanos, expires, true, resolvable, visible);
		final RiskSnapshot risk = risk(observation, selected._target);
		final PhantomDomainRef conversation = counterpart.kind() == CounterpartKind.PHANTOM_PROFILE ? new PhantomDomainRef("profile", Long.toString(counterpart.identity())) : new PhantomDomainRef("character.object", Integer.toString(counterpart.currentObjectId()));
		return new Snapshot(candidate, risk, observation.supportedModes().getFirst(), subject(target.objectId()), conversation, selected._source.helpCounterpart(), selected._source.authorityHash());
	}

	private RiskSnapshot risk(PvpObservation observation, PvpObservedTarget observed)
	{
		final ActorSnapshot actor = observation.actor();
		final PvpTargetSnapshot target = observed.target();
		final int hp = percent(actor.currentHp(), actor.maximumHp());
		final int effective = percent(actor.currentHp() + actor.currentCp(), actor.maximumHp() + actor.maximumCp());
		final int mp = percent(actor.currentMp(), actor.maximumMp());
		final int strength = clamp((int) Math.round((observation.actorLevel() * 10000.0) / target.level()), 0, 20000);
		final int forcedPk = forcedPkRisk(observed.consequences(), target.autoAttackable());
		return new RiskSnapshot(hp, effective, mp, target.hpBand(), target.effectivePoolBand(), strength, forcedPk, observed.localSupport().actorSupport(), observed.localSupport().targetSupport(), target.sameParty(), target.self(), target.peaceRestricted(), observed.canonicalContextAllowed(), target.autoAttackable());
	}

	private static int forcedPkRisk(PvpConsequenceSnapshot consequences, boolean autoAttackable)
	{
		if (autoAttackable)
		{
			return 0;
		}
		final int pkThreshold = Math.max(1, consequences.minimumPkForDrop());
		final int pkExposure = clamp((consequences.pkKills() * 2500) / pkThreshold, 0, 2500);
		final int karmaThreshold = Math.max(1, consequences.karmaDropLimit());
		final int karmaExposure = clamp((consequences.karma() * 2500) / karmaThreshold, 0, 2500);
		final int configuredDropExposure = clamp((int) Math.round(Math.max(consequences.weaponDropChance(), Math.max(consequences.equipmentDropChance(), consequences.inventoryDropChance())) * 100), 0, 2500);
		return clamp(1000 + pkExposure + karmaExposure + configuredDropExposure, 0, 10000);
	}

	private Counterpart counterpart(int objectId)
	{
		final var materialized = _materialization.findByCharacterObjectId(objectId).orElse(null);
		return (materialized != null) && materialized.worldPresent() ? new Counterpart(CounterpartKind.PHANTOM_PROFILE, materialized.profileId(), objectId) : new Counterpart(CounterpartKind.HUMAN_OBJECT, objectId, objectId);
	}

	private SubjectRef subject(int objectId)
	{
		final var materialized = _materialization.findByCharacterObjectId(objectId).orElse(null);
		return (materialized != null) && materialized.worldPresent() ? SubjectRef.phantom(materialized.profileId()) : SubjectRef.character(objectId);
	}

	private static long expiry(long now, int seconds)
	{
		try
		{
			return Math.addExact(now, Math.multiplyExact(seconds, 1_000_000_000L));
		}
		catch (ArithmeticException exception)
		{
			return Long.MAX_VALUE;
		}
	}

	private static long minuteNanos(long minute)
	{
		try
		{
			return Math.multiplyExact(minute, NANOS_PER_MINUTE);
		}
		catch (ArithmeticException exception)
		{
			return Long.MAX_VALUE;
		}
	}

	private static int priority(Source source)
	{
		return switch (source)
		{
			case ACTUAL_ATTACK -> 0;
			case PARTY_DEFENSE -> 1;
			case FARMING_ESCALATION -> 2;
			case REVENGE -> 3;
		};
	}

	private static int percent(double current, double maximum)
	{
		return maximum <= 0 ? 0 : clamp((int) Math.round((current * 100) / maximum), 0, 100);
	}

	private static int clamp(int value, int minimum, int maximum)
	{
		return Math.max(minimum, Math.min(maximum, value));
	}

	private record ObservedEvidence(SourceEvidence _source, PvpObservedTarget _target)
	{
	}
}
