/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.rift;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.l2jmobius.gameserver.model.groups.PartyDistributionType;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.PartySnapshot;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyRoleMatcher;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberCapability;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberKind;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberSnapshot;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.ObjectiveMode;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.RoleAssignment;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.RoleMatchResult;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.RoleRequirement;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.VacancyStatus;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftBackend.MemberFacts;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftBackend.RelationshipEvidence;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftBackend.ShotSupply;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftCatalog.EntryFacts;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftCatalog.TierFact;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.CandidateScore;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.CanonicalRoster;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.DimensionEvidence;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.MemberReadiness;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.PartyReadiness;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.ReadinessDimension;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.SemanticFact;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.SemanticFactType;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.Status;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.SupplyDeficit;

public final class PhantomRiftReadinessService
{
	private static final String ZERO_HASH = "0".repeat(64);
	private final PhantomRiftBackend _backend;
	private final PhantomRiftCatalog _catalog;
	private final PhantomRiftPolicy _policy;
	private final PhantomPartyRoleMatcher _roles;

	public PhantomRiftReadinessService(PhantomRiftBackend backend, PhantomRiftCatalog catalog, PhantomRiftPolicy policy, PhantomPartyRoleMatcher roles)
	{
		_backend = Objects.requireNonNull(backend);
		_catalog = Objects.requireNonNull(catalog);
		_policy = Objects.requireNonNull(policy);
		_roles = Objects.requireNonNull(roles);
	}

	public PartyReadiness evaluate(long leaderProfileId, int tierType)
	{
		final TierFact tier = _catalog.requireTier(tierType);
		final EntryFacts liveEntry = _backend.entry(tierType);
		final String liveConfigHash = _backend.config().hash();
		final MemberRef requestedLeader = _backend.currentMember(leaderProfileId).orElse(null);
		if (requestedLeader == null)
		{
			return unavailable(tierType, liveConfigHash, "rift.leader.unavailable");
		}
		final Optional<PartySnapshot> observed = _backend.canonicalParty(requestedLeader);
		final MemberRef leader = observed.map(PartySnapshot::leader).orElse(requestedLeader);
		final List<MemberRef> rosterMembers = observed.map(PartySnapshot::members).orElse(List.of(requestedLeader));
		final PartyDistributionType distribution = observed.map(PartySnapshot::distribution).orElse(PartyDistributionType.FINDERS_KEEPERS);
		final Set<Integer> objectIds = new HashSet<>();
		final boolean duplicate = rosterMembers.stream().anyMatch(member -> !objectIds.add(member.characterObjectId()));
		if (duplicate || rosterMembers.isEmpty() || (rosterMembers.size() > PhantomPartyModel.MAX_ROSTER) || !rosterMembers.contains(leader))
		{
			return unavailable(tierType, liveConfigHash, "rift.roster.invalid");
		}
		final Set<Integer> requestedItems = liveEntry.itemId() > 0 ? Set.of(liveEntry.itemId()) : Set.of();
		final Map<MemberRef, MemberFacts> facts = new LinkedHashMap<>();
		boolean stale = false;
		for (MemberRef member : rosterMembers.stream().sorted(Comparator.comparing(MemberRef::stableKey)).toList())
		{
			final MemberFacts value = _backend.memberFacts(member, requestedItems).orElse(null);
			if (value == null)
			{
				stale = true;
			}
			else
			{
				facts.put(member, value);
			}
		}
		final String rosterHash = PhantomPartyModel.sha256(leader.stableKey() + '|' + distribution + '|' + rosterMembers.stream().sorted(Comparator.comparing(MemberRef::stableKey)).map(member -> member.stableKey() + ':' + Optional.ofNullable(facts.get(member)).map(MemberFacts::evidenceHash).orElse(ZERO_HASH)).toList());
		final CanonicalRoster roster = new CanonicalRoster(leader, rosterMembers, distribution, observed.isPresent(), rosterMembers.size() == PhantomPartyModel.MAX_ROSTER, rosterHash);
		final List<MemberSnapshot> potential = facts.values().stream().map(value -> potential(value.member())).toList();
		final RoleMatchResult roleMatch = _roles.match(ObjectiveMode.AREA_PVE, _policy.requireTier(tierType).requirements(), potential);
		final List<String> requiredVacancies = roleMatch.vacancies().stream().filter(vacancy -> vacancy.required() && (vacancy.status() != VacancyStatus.FILLED)).map(value -> value.vacancyKey()).toList();
		final List<String> optionalVacancies = roleMatch.vacancies().stream().filter(vacancy -> !vacancy.required() && (vacancy.status() != VacancyStatus.FILLED)).map(value -> value.vacancyKey()).toList();
		final Map<MemberRef, List<RoleAssignment>> assignments = new HashMap<>();
		roleMatch.assignments().forEach(assignment -> assignments.computeIfAbsent(assignment.member(), _ -> new ArrayList<>()).add(assignment));
		final int canonicalInstanceId = Optional.ofNullable(facts.get(leader)).map(value -> value.member().instanceId()).orElse(Integer.MIN_VALUE);
		final List<MemberReadiness> memberReadiness = new ArrayList<>();
		for (MemberFacts member : facts.values())
		{
			memberReadiness.add(memberReadiness(member, assignments.getOrDefault(member.member().ref(), List.of()), tier, liveEntry, canonicalInstanceId));
		}
		final boolean minimumParty = liveEntry.supported() && (rosterMembers.size() >= liveEntry.minimumPartySize());
		final boolean entryReady = memberReadiness.stream().flatMap(value -> value.deficits().stream()).noneMatch(deficit -> deficit.familyKey().equals("entry.fragment"));
		final boolean travelReady = memberReadiness.stream().flatMap(value -> value.dimensions().stream()).filter(value -> value.dimension() == ReadinessDimension.TRAVEL).allMatch(DimensionEvidence::ready);
		final boolean nonSupplyTravelReady = memberReadiness.stream().flatMap(value -> value.dimensions().stream()).filter(value -> (value.dimension() != ReadinessDimension.SUPPLIES) && (value.dimension() != ReadinessDimension.TRAVEL)).allMatch(DimensionEvidence::ready);
		final List<String> reasons = new ArrayList<>();
		final Status status;
		if (stale || !liveConfigHash.equals(_catalog.config().hash()))
		{
			status = Status.STALE;
			reasons.add(stale ? "rift.member.snapshot.stale" : "rift.config.stale");
		}
		else if (!tier.supported() || !liveEntry.supported() || !liveEntry.capacityAvailable() || !leader.equals(requestedLeader))
		{
			status = Status.BLOCKED;
			reasons.add(!leader.equals(requestedLeader) ? "rift.leader.not_canonical" : !liveEntry.capacityAvailable() ? "rift.capacity.unavailable" : "rift.authority.unsupported");
		}
		else if (!minimumParty)
		{
			status = Status.NEEDS_PARTY;
			reasons.add("rift.party.minimum");
		}
		else if (!requiredVacancies.isEmpty())
		{
			status = Status.NEEDS_ROLE;
			reasons.add("rift.role.required_missing");
		}
		else if (!nonSupplyTravelReady)
		{
			status = Status.NEEDS_MEMBER_READY;
			reasons.add("rift.member.not_ready");
		}
		else if (!entryReady || memberReadiness.stream().anyMatch(value -> !value.deficits().isEmpty()))
		{
			status = Status.NEEDS_SUPPLIES;
			reasons.add("rift.supplies.missing");
		}
		else if (!travelReady)
		{
			status = Status.NEEDS_TRAVEL;
			reasons.add("rift.travel.required");
		}
		else
		{
			status = Status.READY_TO_ENTER;
			reasons.add("rift.ready.observed");
		}
		final String evidence = PhantomPartyModel.sha256(tier.type() + "|" + rosterHash + "|" + roleMatch.evidenceHash() + "|" + memberReadiness.stream().map(MemberReadiness::evidenceHash).toList() + "|" + liveConfigHash + "|" + liveEntry.capacityAvailable() + "|" + status);
		return new PartyReadiness(tierType, roster, minimumParty, roleMatch, memberReadiness, requiredVacancies, optionalVacancies, entryReady, travelReady, status, reasons, _catalog.catalogHash(), _policy.hash(), liveConfigHash, evidence);
	}

	public Optional<CandidateScore> candidate(MemberFacts candidate, RoleRequirement requirement, int tierType, int originX, int originY, int originZ)
	{
		return candidate(candidate, requirement, tierType, originX, originY, originZ, RelationshipEvidence.neutral("social.not_requested"));
	}

	public Optional<CandidateScore> candidate(MemberFacts candidate, RoleRequirement requirement, int tierType, int originX, int originY, int originZ, RelationshipEvidence relationship)
	{
		if (candidate.member().dead())
		{
			return Optional.empty();
		}
		final RoleMatchResult match = _roles.match(ObjectiveMode.AREA_PVE, List.of(requirement), List.of(potential(candidate.member())));
		if (match.assignments().isEmpty())
		{
			return Optional.empty();
		}
		final RoleAssignment assignment = match.assignments().getFirst();
		int readiness = 0;
		final TierFact tier = _catalog.requireTier(tierType);
		final PhantomRiftPolicy.Limits limits = _policy.limits();
		readiness += candidate.level() >= Math.max(1, tier.minimumNpcLevel() - limits.levelOffsetBelowMobMinimum()) ? 2000 : 0;
		readiness += candidate.member().hpPercent() >= limits.minimumHpPercent() ? 1500 : 0;
		readiness += candidate.member().mpPercent() >= limits.minimumMpPercent() ? 1000 : 0;
		readiness += (!limits.requireWeapon() || (candidate.activeWeaponItemId() > 0)) && (candidate.equipment().size() >= limits.minimumEquippedItems()) ? 2000 : 0;
		readiness += candidate.requestedItemCounts().getOrDefault(tier.entry().itemId(), 0L) >= tier.entry().itemCount() ? 2500 : 0;
		final String evidence = PhantomPartyModel.sha256(candidate.evidenceHash() + '|' + requirement + '|' + assignment.score() + '|' + readiness + '|' + relationship.modifierBasisPoints() + '|' + relationship.evidenceHash() + '|' + relationship.reasonKey());
		return Optional.of(new CandidateScore(candidate.member().ref(), requirement.vacancyKey(), assignment.score(), readiness, relationship.modifierBasisPoints(), candidate.distanceSquared(originX, originY, originZ), candidate.member().ref().kind() == MemberKind.REAL, relationship.evidenceHash(), evidence));
	}

	public List<SemanticFact> semanticFacts(PartyReadiness readiness)
	{
		final List<SemanticFact> facts = new ArrayList<>();
		facts.add(new SemanticFact(SemanticFactType.RIFT_PREP_STATUS, Map.of("tier", Integer.toString(readiness.tierType()), "status", readiness.status().name(), "partySize", Integer.toString(readiness.roster().members().size())), readiness.roster().evidenceHash()));
		for (String vacancy : readiness.requiredVacancies())
		{
			facts.add(new SemanticFact(SemanticFactType.RIFT_MISSING_ROLE, Map.of("tier", Integer.toString(readiness.tierType()), "missingRoleKey", vacancy), readiness.roster().evidenceHash()));
		}
		for (MemberReadiness member : readiness.members())
		{
			member.dimensions().stream().filter(value -> !value.ready()).findFirst().ifPresent(value -> facts.add(new SemanticFact(SemanticFactType.RIFT_MEMBER_NOT_READY, Map.of("memberCharacterId", Integer.toString(member.member().characterObjectId()), "reasonKey", value.reasonKey()), readiness.roster().evidenceHash())));
		}
		if (readiness.roster().fullParty())
		{
			facts.add(new SemanticFact(SemanticFactType.RIFT_PARTY_FULL, Map.of("partySize", "9"), readiness.roster().evidenceHash()));
		}
		if (readiness.status() == Status.READY_TO_ENTER)
		{
			facts.add(new SemanticFact(SemanticFactType.RIFT_READY, Map.of("tier", Integer.toString(readiness.tierType())), readiness.roster().evidenceHash()));
		}
		return List.copyOf(facts);
	}

	private MemberReadiness memberReadiness(MemberFacts facts, List<RoleAssignment> assignments, TierFact tier, EntryFacts entry, int canonicalInstanceId)
	{
		final PhantomRiftPolicy.Limits limits = _policy.limits();
		final List<DimensionEvidence> dimensions = new ArrayList<>();
		final int minimumLevel = Math.max(1, tier.minimumNpcLevel() - limits.levelOffsetBelowMobMinimum());
		dimensions.add(dimension(ReadinessDimension.LEVEL, facts.level() >= minimumLevel, "rift.level", facts.level() + ">=" + minimumLevel));
		dimensions.add(dimension(ReadinessDimension.ALIVE, !facts.member().dead(), "rift.alive", Boolean.toString(!facts.member().dead())));
		final boolean vitals = (facts.member().hpPercent() >= limits.minimumHpPercent()) && (facts.member().mpPercent() >= limits.minimumMpPercent()) && (facts.member().cpPercent() >= limits.minimumCpPercent());
		dimensions.add(dimension(ReadinessDimension.VITALS, vitals, "rift.vitals", facts.member().hpPercent() + ":" + facts.member().mpPercent() + ":" + facts.member().cpPercent()));
		final boolean samePartyInstance = facts.member().instanceId() == canonicalInstanceId;
		dimensions.add(dimension(ReadinessDimension.INSTANCE, samePartyInstance, "rift.instance", Integer.toString(facts.member().instanceId())));
		final boolean equipment = (facts.equipment().size() >= limits.minimumEquippedItems()) && (!limits.requireWeapon() || (facts.activeWeaponItemId() > 0));
		dimensions.add(dimension(ReadinessDimension.EQUIPMENT, equipment, "rift.equipment", facts.activeWeaponItemId() + ":" + facts.equipment()));
		final boolean capabilities = assignments.stream().allMatch(assignment -> facts.member().capabilities().stream().anyMatch(capability -> capability.capabilityKey().equals(assignment.capabilityKey()) && capability.variantKey().equals(assignment.variantKey()) && capability.intrinsic() && capability.learned()));
		dimensions.add(dimension(ReadinessDimension.CAPABILITIES, capabilities, "rift.capabilities", assignments.toString()));
		final List<SupplyDeficit> deficits = new ArrayList<>();
		final long fragments = facts.requestedItemCounts().getOrDefault(entry.itemId(), 0L);
		if (entry.supported() && (fragments < entry.itemCount()))
		{
			deficits.add(new SupplyDeficit("entry.fragment", entry.itemId(), entry.itemCount(), fragments));
		}
		for (RoleAssignment assignment : assignments)
		{
			final boolean magic = assignment.capabilityKey().equals("combat.ranged_magic_damage");
			final boolean physical = Set.of("combat.melee_damage", "combat.ranged_physical_damage").contains(assignment.capabilityKey());
			final long required = magic ? limits.magicShotCount() : physical ? limits.physicalShotCount() : 0;
			if (required > 0)
			{
				final List<ShotSupply> compatible = facts.shotSupplies().stream().filter(supply -> supply.magic() == magic).sorted(Comparator.comparingInt(ShotSupply::itemId)).toList();
				final long actual = compatible.stream().mapToLong(ShotSupply::count).sum();
				if (actual < required)
				{
					deficits.add(new SupplyDeficit(magic ? "shot.magic" : "shot.physical", compatible.isEmpty() ? 0 : compatible.getFirst().itemId(), required, actual));
				}
			}
		}
		dimensions.add(dimension(ReadinessDimension.SUPPLIES, deficits.isEmpty(), "rift.supplies", deficits.toString()));
		final boolean travel = (facts.member().instanceId() == entry.destinationInstanceId()) && (facts.distanceSquared(entry.destinationX(), entry.destinationY(), entry.destinationZ()) <= ((long) limits.regroupDistance() * limits.regroupDistance()));
		dimensions.add(dimension(ReadinessDimension.TRAVEL, travel, "rift.travel", facts.member().instanceId() + ":" + facts.distanceSquared(entry.destinationX(), entry.destinationY(), entry.destinationZ())));
		final boolean ready = dimensions.stream().allMatch(DimensionEvidence::ready);
		final String evidence = PhantomPartyModel.sha256(facts.evidenceHash() + '|' + assignments + '|' + dimensions + '|' + deficits);
		return new MemberReadiness(facts.member().ref(), assignments.stream().map(RoleAssignment::vacancyKey).toList(), dimensions, deficits, ready, evidence);
	}

	private PartyReadiness unavailable(int tierType, String configHash, String reason)
	{
		final MemberRef unavailable = MemberRef.phantom(1, 0);
		final CanonicalRoster roster = new CanonicalRoster(unavailable, List.of(unavailable), PartyDistributionType.FINDERS_KEEPERS, false, false, ZERO_HASH);
		final RoleMatchResult roles = _roles.match(ObjectiveMode.AREA_PVE, List.of(), List.of());
		return new PartyReadiness(tierType, roster, false, roles, List.of(), List.of(), List.of(), false, false, Status.STALE, List.of(reason), _catalog.catalogHash(), _policy.hash(), configHash, PhantomPartyModel.sha256(reason + '|' + tierType));
	}

	private static DimensionEvidence dimension(ReadinessDimension dimension, boolean ready, String reason, String evidence)
	{
		return new DimensionEvidence(dimension, ready, ready ? reason + ".ready" : reason + ".not_ready", evidence);
	}

	private static MemberSnapshot potential(MemberSnapshot member)
	{
		final List<MemberCapability> capabilities = member.capabilities().stream().map(capability -> new MemberCapability(capability.capabilityKey(), capability.variantKey(), capability.rank(), capability.actionSkillId(), capability.actionSkillLevel(), capability.targetScope(), capability.intrinsic(), capability.learned(), true, "potential", capability.contextualScore(), capability.provenance())).toList();
		return new MemberSnapshot(member.ref(), member.classId(), member.instanceId(), member.x(), member.y(), member.z(), member.hpPercent(), member.mpPercent(), member.cpPercent(), false, member.casting(), member.attacking(), member.moving(), member.targetObjectId(), member.attackerObjectIds(), capabilities, member.progressionHash());
	}
}
