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
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;

import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.clan.Clan;
import org.l2jmobius.gameserver.model.clan.ClanMember;
import org.l2jmobius.gameserver.model.clan.ClanWarService;
import org.l2jmobius.gameserver.model.clan.ClanWarService.WarIdentity;
import org.l2jmobius.gameserver.phantoms.navigation.L2jNavigationBackend;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;

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
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyCoordinator.PvpTacticalView;
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
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SubjectKind;

/**
 * Deterministic bounded causal-source join. It never scans World players or the
 * profile registry; all identities are exact lookups supplied by owner seams.
 */
public final class PhantomPvpContext
{
	public record Snapshot(Candidate candidate, RiskSnapshot risk, PhantomCombatMode mode, SubjectRef socialSubject, PhantomDomainRef conversationCounterpart, PhantomDomainRef helpCounterpart, String sourceEvidenceHash, ClanWarAuthority clanWarAuthority)
	{
		public Snapshot(Candidate candidate, RiskSnapshot risk, PhantomCombatMode mode, SubjectRef socialSubject, PhantomDomainRef conversationCounterpart, PhantomDomainRef helpCounterpart, String sourceEvidenceHash)
		{
			this(candidate, risk, mode, socialSubject, conversationCounterpart, helpCounterpart, sourceEvidenceHash, null);
		}

		public Snapshot
		{
			Objects.requireNonNull(candidate);
			Objects.requireNonNull(risk);
			Objects.requireNonNull(mode);
			Objects.requireNonNull(socialSubject);
			Objects.requireNonNull(conversationCounterpart);
			if (!socialSubject.equals(subject(candidate.counterpart()))) { throw new IllegalArgumentException("PvP counterpart and social identity differ."); }
			sourceEvidenceHash = PhantomPvpModel.boundedHash(sourceEvidenceHash);
		}
	}

	private record SourceEvidence(Source source, int targetObjectId, String authorityHash, long expiryLogicalNanos, PhantomDomainRef helpCounterpart, SubjectRef expectedSubject)
	{
	}
	/** Exact native evidence at successful tactical arrival, bounded by the existing profile owner. */
	record RetreatRecovery(Counterpart counterpart, long incomingDamageNanos, double threatDistance)
	{
	}

	private static final long NANOS_PER_MINUTE = 60_000_000_000L;
	private final PhantomPvpPolicy _policy;
	private final PhantomCombatService _combat;
	private final PhantomPartyCoordinator _party;
	private final PhantomFarmingService _farming;
	private final PhantomPvpSocialBridge _social;
	private final PhantomMaterializationService _materialization;
	private final LongAdder _clanAidObservations = new LongAdder();

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
		return observe(profileId, logicalNanos, null, _ -> true);
	}
	Optional<Snapshot> observeAvailable(long profileId, long logicalNanos, java.util.function.Predicate<Candidate> admissible)
	{
		return observe(profileId, logicalNanos, null, admissible);
	}

	Optional<Snapshot> observeOwned(PhantomPvpModel.Encounter encounter, long logicalNanos)
	{
		return observe(encounter.profileId(), logicalNanos, encounter, _ -> true);
	}

	/** Exact owner/counterpart lookup only; retreat must not choose a direction from a stale target. */
	Optional<PhantomNavigationPoint> retreatThreat(PhantomPvpModel.Encounter encounter)
	{
		final Player actor = retreatActor(encounter.profileId()); final var target = World.getInstance().getPlayer(encounter.counterpart().currentObjectId());
		if (actor == null || target == null || target == actor || !target.isSpawned() || target.isAlikeDead() || target.isInvisible() || target.getInstanceId() != actor.getInstanceId() || actor.getWorldRegion() == null || !actor.getWorldRegion().isSurroundingRegion(target.getWorldRegion())) { return Optional.empty(); }
		return Optional.of(PhantomNativeTravelActions.point(target));
	}
	Optional<RetreatRecovery> retreatRecovery(PhantomPvpModel.Encounter encounter)
	{
		final Player actor = retreatActor(encounter.profileId()); final var threat = retreatThreat(encounter).orElse(null);
		return actor == null || threat == null ? Optional.empty() : Optional.of(new RetreatRecovery(encounter.counterpart(), actor.recentPlayerAttackNanos(encounter.counterpart().currentObjectId()), PhantomNativeTravelActions.point(actor).distanceTo(threat)));
	}

	boolean retreatWaypointSafe(long profileId, PhantomNavigationPoint waypoint)
	{
		final Player actor = retreatActor(profileId);
		return actor != null && !actor.isInStoreMode() && !actor.hasPendingOwnedStore() && actor.getInstanceId() == waypoint.instanceId()
			&& new L2jNavigationBackend().canWalkDirect(PhantomNativeTravelActions.point(actor), waypoint)
			&& PhantomNativeTravelActions.safePveWalkSegment(actor, waypoint.x(), waypoint.y(), waypoint.z(), List.copyOf(World.getInstance().getVisibleObjectsInRange(actor, Monster.class, 1200)), Set.of());
	}

	boolean defeatedByCounterpart(PhantomPvpModel.Encounter encounter)
	{
		final var lifetime = _materialization.find(encounter.profileId()).orElse(null);
		final Player actor = lifetime == null || !lifetime.worldPresent() ? null : World.getInstance().getPlayer(lifetime.characterObjectId());
		return actor != null && actor.isDead() && actor.nativeDeathKillerObjectId() == encounter.counterpart().currentObjectId();
	}

	private Player retreatActor(long profileId)
	{
		final var lifetime = _materialization.find(profileId).orElse(null);
		if (lifetime == null || !lifetime.worldPresent() || !lifetime.actionAdmissionOpen()) { return null; }
		final var actor = World.getInstance().getPlayer(lifetime.characterObjectId());
		return actor != null && !actor.isAlikeDead() && actor.getNativeWorkOwner() != null && actor.getNativeWorkOwner().isCurrent() ? actor : null;
	}

	private Optional<Snapshot> observe(long profileId, long logicalNanos, PhantomPvpModel.Encounter owned, java.util.function.Predicate<Candidate> admissible)
	{
		if ((profileId <= 0) || (logicalNanos < 0))
		{
			return Optional.empty();
		}
		final long minute = logicalNanos / NANOS_PER_MINUTE;
		final var party = owned == null ? _party.pvpTacticalView(profileId, _policy.limits().helpFanout(), logicalNanos)
			: _party.pvpTacticalView(profileId, 1, logicalNanos, owned.counterpart().currentObjectId());
		final PvpEscalationEvidence farmingEvidence = _farming.pvpEscalation(profileId, minute).orElse(null);
		final Map<Integer, SourceEvidence> exact = new HashMap<>();
		final Map<Integer, List<SourceEvidence>> partySources = new HashMap<>();
		for (var evidence : party.protection())
		{
			final var member = evidence.directive().targetMember();
			final PhantomDomainRef help = member.kind() == MemberKind.PHANTOM ? new PhantomDomainRef("profile", Long.toString(member.profileId())) : new PhantomDomainRef("character.object", Integer.toString(member.characterObjectId()));
			final int attacker = evidence.directive().targetObjectId();
			final var source = new SourceEvidence(Source.PARTY_DEFENSE, attacker, evidence.authorityHash(), expiry(logicalNanos, _policy.limits().encounterTtlSeconds()), help, null);
			exact.putIfAbsent(attacker, source);
			partySources.computeIfAbsent(attacker, _ -> new ArrayList<>()).add(source);
		}
		if (farmingEvidence != null)
		{
			final var counterpart = _materialization.find(farmingEvidence.counterpartProfileId()).orElse(null);
			if ((counterpart != null) && counterpart.worldPresent())
			{
				exact.putIfAbsent(counterpart.characterObjectId(), new SourceEvidence(Source.FARMING_ESCALATION, counterpart.characterObjectId(), farmingEvidence.authorityHash(), Math.min(expiry(logicalNanos, _policy.limits().encounterTtlSeconds()), minuteNanos(farmingEvidence.expiryMinute())), null, null));
			}
		}
		// Existing party/farming owner inputs keep their slots; memory only fills the remaining bound.
		exact.keySet().retainAll(exact.keySet().stream().sorted().limit(10).toList());
		if (owned == null)
		{
			for (RevengeEvidence evidence : _social.revengeSources(profileId, minute))
			{
				if (exact.size() >= 10) { break; }
				final var remembered = evidence.subject();
				final var lifetime = remembered.kind() == SubjectKind.PHANTOM_PROFILE ? _materialization.find(remembered.id()).orElse(null) : null;
				final int objectId = remembered.kind() == SubjectKind.CHARACTER_OBJECT ? (int) remembered.id() : lifetime != null && lifetime.worldPresent() ? lifetime.characterObjectId() : 0;
				if ((objectId > 0) && remembered.equals(subject(objectId)))
				{
					exact.putIfAbsent(objectId, new SourceEvidence(Source.REVENGE, objectId, evidence.authorityHash(), Math.min(expiry(logicalNanos, _policy.limits().encounterTtlSeconds()), minuteNanos(evidence.expiryMinute())), null, remembered));
				}
			}
		}
		final List<Integer> exactTargets = exact.keySet().stream().sorted().limit(10).toList();
		final PvpObservation observation = (owned == null
			? _combat.observePvp(profileId, exactTargets, _policy.limits().observedAttackerLimit(), _policy.limits().localRiskPlayerLimit())
			: _combat.observeOwnedPvp(profileId, owned.counterpart().currentObjectId(), owned.authorityHash(), _policy.limits().observedAttackerLimit(), _policy.limits().localRiskPlayerLimit())).orElse(null);
		if ((observation == null) || observation.supportedModes().isEmpty())
		{
			return Optional.empty();
		}
		if (owned == null)
		{
			observeClanAid(profileId, observation);
		}
		final List<ObservedEvidence> candidates = new ArrayList<>();
		for (PvpObservedTarget observed : observation.targets())
		{
			final int targetObjectId = observed.target().objectId();
			if (observed.actualAttacker())
			{
				final String attackAuthority = PhantomPvpModel.sha256("pvp.actual.attack", observation.actor().objectId(), targetObjectId, observation.actor().instanceId());
				candidates.add(new ObservedEvidence(new SourceEvidence(Source.ACTUAL_ATTACK, targetObjectId, attackAuthority, expiry(logicalNanos, _policy.limits().encounterTtlSeconds()), null, null), observed));
			}
			if ((observed.declaredClanWar() || (owned != null && owned.source() == Source.CLAN_WAR))
				&& observed.target().autoAttackable() && observed.target().reachable() && !observed.target().invulnerable())
			{
				final var war = clanWarAuthority(observation.actor().objectId(), targetObjectId);
				if (war != null)
				{
					final String evidenceHash = clanWarEvidenceHash(war.outgoing(), war.incoming());
					candidates.add(new ObservedEvidence(new SourceEvidence(Source.CLAN_WAR, targetObjectId, evidenceHash, expiry(logicalNanos, _policy.limits().encounterTtlSeconds()), null, null), observed, war));
				}
			}
			final SourceEvidence supplied = exact.get(targetObjectId);
			if (supplied != null)
			{
				for (var source : partySources.getOrDefault(targetObjectId, List.of(supplied)))
				{
					candidates.add(new ObservedEvidence(source, observed));
				}
			}
			if (observed.selectedTarget())
			{
				final SubjectRef subject = subject(targetObjectId);
				final RevengeEvidence revenge = _social.revenge(profileId, subject, minute).orElse(null);
				if (revenge != null)
				{
					candidates.add(new ObservedEvidence(new SourceEvidence(Source.REVENGE, targetObjectId, revenge.authorityHash(), Math.min(expiry(logicalNanos, _policy.limits().encounterTtlSeconds()), minuteNanos(revenge.expiryMinute())), null, subject), observed));
				}
			}
		}
		candidates.sort(Comparator.comparingInt((ObservedEvidence item) -> tacticalPriority(party, item._source.source(), item._target.target(), item._target.canonicalContextAllowed()))
			.thenComparingInt(item -> item._source.targetObjectId()));
		for (ObservedEvidence selected : candidates)
		{
			final Snapshot snapshot = snapshot(profileId, logicalNanos, observation, selected);
			if ((snapshot != null) && snapshot.candidate().currentAt(logicalNanos) && snapshot.risk().legal() && admissible.test(snapshot.candidate()))
			{
				// Direct damage is a separate cause and cannot renew a Party or clan-war lease.
				if (owned != null && (owned.source() == Source.PARTY_DEFENSE || owned.source() == Source.CLAN_WAR) && !owned.authorityHash().equals(snapshot.candidate().authorityHash()))
				{
					continue;
				}
				// Already-owned warnings retain their admission. A new dispute needs real disposition/memory.
				if (owned == null && (snapshot.candidate().source() == Source.FARMING_ESCALATION || snapshot.candidate().source() == Source.REVENGE) && !_social.allowsProactiveDispute(profileId, snapshot.socialSubject(), minute, snapshot.candidate().source() == Source.FARMING_ESCALATION)) { continue; }
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
		final SubjectRef socialSubject = subject(counterpart);
		if ((selected._source.expectedSubject() != null) && !selected._source.expectedSubject().equals(socialSubject)) { return null; }
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
		return new Snapshot(candidate, risk, observation.supportedModes().getFirst(), socialSubject, conversation, selected._source.helpCounterpart(), selected._source.authorityHash(), selected._war);
	}

	private static ClanWarAuthority clanWarAuthority(int actorObjectId, int targetObjectId)
	{
		final var world = World.getInstance();
		final Player actor = world.getPlayer(actorObjectId);
		final Player target = world.getPlayer(targetObjectId);
		final Clan actorClan = actor == null ? null : actor.getClan();
		final Clan targetClan = target == null ? null : target.getClan();
		if (actorClan == null || targetClan == null || actorClan == targetClan)
		{
			return null;
		}
		final var wars = ClanWarService.getInstance();
		final var outgoing = wars.currentWar(actorClan, targetClan).orElse(null);
		final var incoming = wars.currentWar(targetClan, actorClan).orElse(null);
		final ClanMember actorMember = actorClan.getClanMember(actorObjectId);
		final ClanMember targetMember = targetClan.getClanMember(targetObjectId);
		if (outgoing == null || incoming == null || actorMember == null || targetMember == null)
		{
			return null;
		}
		final var authority = new ClanWarAuthority(actor, target, actorClan, targetClan, actorMember, targetMember, outgoing, incoming);
		return authority.current() ? authority : null;
	}

	record ClanWarAuthority(Player actor, Player target, Clan actorClan, Clan targetClan, ClanMember actorMember, ClanMember targetMember, WarIdentity outgoing, WarIdentity incoming)
	{
		boolean current()
		{
			final var world = World.getInstance();
			final var wars = ClanWarService.getInstance();
			return world.getPlayer(actor.getObjectId()) == actor && world.getPlayer(target.getObjectId()) == target
				&& actor.getClan() == actorClan && target.getClan() == targetClan
				&& actorClan.getClanMember(actor.getObjectId()) == actorMember && targetClan.getClanMember(target.getObjectId()) == targetMember
				&& actorClan.getDissolvingExpiryTime() == 0 && targetClan.getDissolvingExpiryTime() == 0
				&& !sameAlliance(actorClan, targetClan) && actor.getParty() == null
				&& !actor.isInStoreMode() && !actor.hasPendingOwnedStore() && !actor.isAcademyMember() && !target.isAcademyMember()
				&& actor.getWantsPeace() == 0 && target.getWantsPeace() == 0
				&& actorClan.isAtWarWith(targetClan) && targetClan.isAtWarWith(actorClan)
				&& wars.currentWar(actorClan, targetClan).filter(outgoing::equals).isPresent()
				&& wars.currentWar(targetClan, actorClan).filter(incoming::equals).isPresent();
		}
	}

	static String clanWarEvidenceHash(WarIdentity outgoing, WarIdentity incoming)
	{
		return PhantomPvpModel.sha256("pvp.native.clan-war", outgoing.sourceClanId(), outgoing.targetClanId(), outgoing.warId(), incoming.warId());
	}

	/** Observe exact local evidence from the existing target budget. Only native Party can dispatch protection. */
	private void observeClanAid(long profileId, PvpObservation observation)
	{
		if (observation.targets().isEmpty() || observation.targets().stream().anyMatch(PvpObservedTarget::actualAttacker) || _party.blocksBackground(profileId))
		{
			return;
		}
		final var world = World.getInstance();
		final Player actor = world.getPlayer(observation.actor().objectId());
		final Clan actorClan = actor == null ? null : actor.getClan();
		if (actorClan == null || actor.getParty() != null || actor.isInStoreMode() || actor.hasPendingOwnedStore()
			|| actorClan.getDissolvingExpiryTime() != 0 || actorClan.getClanMember(actor.getObjectId()) == null)
		{
			return;
		}
		for (PvpObservedTarget observed : observation.targets())
		{
			final var facts = observed.target();
			if (!observed.canonicalContextAllowed() || !facts.autoAttackable() || !facts.reachable() || facts.invulnerable())
			{
				continue;
			}
			final Player attacker = world.getPlayer(facts.objectId());
			if (attacker == null || !(attacker.getTarget() instanceof Player victim) || victim == actor || world.getPlayer(victim.getObjectId()) != victim
				|| !victim.isSpawned() || victim.isAlikeDead() || victim.isInvisible() || victim.getInstanceId() != actor.getInstanceId()
				|| actor.getWorldRegion() == null || victim.getWorldRegion() == null || !actor.getWorldRegion().isSurroundingRegion(victim.getWorldRegion())
				|| !PhantomNativeTravelActions.targetsForCombat(attacker, victim)
				|| victim.recentPlayerAttackNanos(attacker.getObjectId()) == 0)
			{
				continue;
			}
			final Clan victimClan = victim.getClan();
			final Clan attackerClan = attacker.getClan();
			if (victimClan == null || (victimClan != actorClan && !sameAlliance(actorClan, victimClan))
				|| victimClan.getDissolvingExpiryTime() != 0 || victimClan.getClanMember(victim.getObjectId()) == null
				|| (attackerClan != null && (attackerClan == actorClan || sameAlliance(actorClan, attackerClan))))
			{
				continue;
			}
			final RiskSnapshot risk = risk(observation, observed);
			if (risk.legal() && risk.actorHpPercent() > _policy.limits().retreatHpPercent()
				&& risk.actorEffectivePoolPercent() > _policy.limits().retreatEffectivePoolPercent()
				&& risk.relativeStrengthBasisPoints() >= _policy.limits().engageMinimumStrengthBasisPoints())
			{
				_clanAidObservations.increment();
				return;
			}
		}
	}

	long clanAidObservations()
	{
		return _clanAidObservations.sum();
	}

	private static boolean sameAlliance(Clan left, Clan right)
	{
		final int allianceId = left.getAllyId();
		return allianceId > 0 && allianceId == right.getAllyId();
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
		return subject(counterpart(objectId));
	}

	private static SubjectRef subject(Counterpart counterpart)
	{
		return counterpart.kind() == CounterpartKind.PHANTOM_PROFILE ? SubjectRef.phantom(counterpart.identity()) : SubjectRef.character(counterpart.currentObjectId());
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
			case CLAN_WAR -> 3;
			case REVENGE -> 4;
		};
	}

	static int tacticalPriority(PvpTacticalView party, Source source, PvpTargetSnapshot target, boolean allowed)
	{
		if (allowed && target.autoAttackable() && target.reachable() && !target.invulnerable()
			&& (source == Source.ACTUAL_ATTACK || source == Source.PARTY_DEFENSE))
		{
			if (party.focusObjectId() == target.objectId())
			{
				return -2;
			}
			if (source == Source.PARTY_DEFENSE && party.secondaryObjectIds().contains(target.objectId()))
			{
				return 11 + party.secondaryObjectIds().indexOf(target.objectId());
			}
		}
		return 10 + 10 * priority(source);
	}

	private static int percent(double current, double maximum)
	{
		return maximum <= 0 ? 0 : clamp((int) Math.round((current * 100) / maximum), 0, 100);
	}

	private static int clamp(int value, int minimum, int maximum)
	{
		return Math.max(minimum, Math.min(maximum, value));
	}

	private record ObservedEvidence(SourceEvidence _source, PvpObservedTarget _target, ClanWarAuthority _war)
	{
		private ObservedEvidence(SourceEvidence source, PvpObservedTarget target)
		{
			this(source, target, null);
		}
	}
}
