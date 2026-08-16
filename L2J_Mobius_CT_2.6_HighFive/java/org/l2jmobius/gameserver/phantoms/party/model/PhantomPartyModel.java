/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.party.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;

public final class PhantomPartyModel
{
	public static final String COMPONENT_TYPE = "party.state";
	public static final int SCHEMA_VERSION = 1;
	public static final int MAX_ROSTER = 9;
	public static final int MAX_REQUIREMENTS = 12;
	public static final int MAX_ASSIGNMENTS = 12;
	public static final int MAX_ROUTE_WAYPOINTS = 64;
	private static final Pattern KEY = Pattern.compile("^[a-z][a-z0-9_.-]{0,63}$");
	private static final Pattern SHA256 = Pattern.compile("^[A-F0-9]{64}$");

	public enum StateStatus
	{
		SOLO,
		FORMING,
		INVITED_OUTBOUND,
		INVITED_INBOUND,
		JOINING,
		LEADER,
		MEMBER,
		LEAVING,
		RECOVERING,
		RETIRED,
		INCONSISTENT
	}

	public enum OperationPhase
	{
		PREPARED,
		CANONICAL_PENDING,
		CANONICAL_OBSERVED,
		COMMITTED,
		ABORTED
	}

	public enum OperationKind
	{
		FORM,
		JOIN,
		LEAVE,
		EXPEL,
		TRANSFER_LEADER,
		RECOVER,
		ROUTE,
		SUPPORT
	}

	public enum MemberKind
	{
		PHANTOM,
		REAL
	}

	public enum ObjectiveMode
	{
		GENERAL_PVE,
		AREA_PVE,
		RECOVERY,
		TRAVEL
	}

	public enum RouteStatus
	{
		PLANNING,
		MOVING,
		REGROUPING,
		ARRIVED,
		FAILED
	}

	public enum VacancyStatus
	{
		FILLED,
		MISSING,
		OPTIONAL,
		UNSUPPORTED
	}

	public enum DirectiveKind
	{
		ASSIST_TARGET,
		PROTECT_MEMBER,
		PROTECT_MEMBER_PVP,
		HEAL_MEMBER,
		RECHARGE_MEMBER,
		RESURRECT_MEMBER,
		PARTY_SUPPORT,
		HOLD,
		REGROUP,
		RETREAT
	}

	public record MemberRef(MemberKind kind, long profileId, int characterObjectId)
	{
		public MemberRef
		{
			Objects.requireNonNull(kind, "Party member kind must not be null.");
			if (((kind == MemberKind.PHANTOM) && (profileId <= 0)) || ((kind == MemberKind.REAL) && (profileId != 0)) || (characterObjectId < 0) || ((kind == MemberKind.REAL) && (characterObjectId <= 0)))
			{
				throw new IllegalArgumentException("Invalid party member reference.");
			}
		}

		public static MemberRef phantom(long profileId, int characterObjectId)
		{
			return new MemberRef(MemberKind.PHANTOM, profileId, characterObjectId);
		}

		public static MemberRef real(int characterObjectId)
		{
			return new MemberRef(MemberKind.REAL, 0, characterObjectId);
		}

		public String stableKey()
		{
			return kind.name() + ':' + profileId + ':' + characterObjectId;
		}
	}

	public record MemberCapability(String capabilityKey, String variantKey, int rank, int actionSkillId, int actionSkillLevel, String targetScope, boolean intrinsic, boolean learned, boolean readyNow, String readinessReason, int contextualScore, String provenance)
	{
		public MemberCapability
		{
			capabilityKey = requireKey(capabilityKey, "Capability key");
			variantKey = requireKey(variantKey, "Capability variant");
			targetScope = requireBounded(targetScope, 48, "Target scope");
			readinessReason = requireKey(readinessReason, "Readiness reason");
			provenance = requireBounded(provenance, 128, "Capability provenance");
			if ((rank < 1) || (rank > 1000) || (actionSkillId < 0) || (actionSkillLevel < 0) || (contextualScore < 0) || (contextualScore > 10000))
			{
				throw new IllegalArgumentException("Invalid party member capability.");
			}
		}

		public String identity()
		{
			return capabilityKey + ':' + variantKey;
		}
	}

	public record MemberSnapshot(MemberRef ref, int classId, int instanceId, int x, int y, int z, int hpPercent, int mpPercent, int cpPercent, boolean dead, boolean casting, boolean attacking, boolean moving, int targetObjectId, List<Integer> attackerObjectIds, List<MemberCapability> capabilities, String progressionHash)
	{
		public MemberSnapshot
		{
			Objects.requireNonNull(ref, "Member reference must not be null.");
			if ((classId < 0) || (instanceId < 0) || (hpPercent < 0) || (hpPercent > 100) || (mpPercent < 0) || (mpPercent > 100) || (cpPercent < 0) || (cpPercent > 100) || (targetObjectId < 0) || (attackerObjectIds == null) || (attackerObjectIds.size() > 32) || (capabilities == null) || (capabilities.size() > 256))
			{
				throw new IllegalArgumentException("Invalid party member snapshot.");
			}
			attackerObjectIds = attackerObjectIds.stream().distinct().sorted().toList();
			capabilities = capabilities.stream().sorted(Comparator.comparing(MemberCapability::identity)).toList();
			progressionHash = requireHash(progressionHash, "Progression hash");
		}
	}

	public record RoleDefinition(String roleKey, Map<String, Integer> capabilityWeights, Map<ObjectiveMode, Integer> objectiveWeights, boolean support)
	{
		public RoleDefinition
		{
			roleKey = requireKey(roleKey, "Role key");
			if ((capabilityWeights == null) || capabilityWeights.isEmpty() || (capabilityWeights.size() > 16) || (objectiveWeights == null) || (objectiveWeights.size() > ObjectiveMode.values().length))
			{
				throw new IllegalArgumentException("Invalid role definition.");
			}
			final Map<String, Integer> sortedCapabilities = new TreeMap<>();
			capabilityWeights.forEach((key, weight) ->
			{
				requireKey(key, "Role capability key");
				if ((weight == null) || (weight < 1) || (weight > 1000))
				{
					throw new IllegalArgumentException("Invalid role capability weight.");
				}
				sortedCapabilities.put(key, weight);
			});
			capabilityWeights = Collections.unmodifiableMap(sortedCapabilities);
			final Map<ObjectiveMode, Integer> normalizedObjectiveWeights = new java.util.EnumMap<>(ObjectiveMode.class);
			normalizedObjectiveWeights.putAll(objectiveWeights);
			objectiveWeights = Collections.unmodifiableMap(normalizedObjectiveWeights);
		}
	}

	public record RoleRequirement(String vacancyKey, String roleKey, boolean required, int minimumScore)
	{
		public RoleRequirement
		{
			vacancyKey = requireKey(vacancyKey, "Vacancy key");
			roleKey = requireKey(roleKey, "Required role key");
			if ((minimumScore < 1) || (minimumScore > 10000))
			{
				throw new IllegalArgumentException("Invalid role minimum score.");
			}
		}
	}

	public record RoleAssignment(String vacancyKey, String roleKey, MemberRef member, String capabilityKey, String variantKey, int score, String provenance)
	{
		public RoleAssignment
		{
			vacancyKey = requireKey(vacancyKey, "Assignment vacancy key");
			roleKey = requireKey(roleKey, "Assignment role key");
			Objects.requireNonNull(member, "Assigned member must not be null.");
			capabilityKey = requireKey(capabilityKey, "Assignment capability key");
			variantKey = requireKey(variantKey, "Assignment capability variant");
			provenance = requireBounded(provenance, 128, "Assignment provenance");
			if ((score < 1) || (score > 10000))
			{
				throw new IllegalArgumentException("Invalid assignment score.");
			}
		}
	}

	public record Vacancy(String vacancyKey, String roleKey, boolean required, VacancyStatus status, RoleAssignment assignment, String provenance)
	{
		public Vacancy
		{
			vacancyKey = requireKey(vacancyKey, "Vacancy key");
			roleKey = requireKey(roleKey, "Vacancy role key");
			Objects.requireNonNull(status, "Vacancy status must not be null.");
			provenance = requireBounded(provenance, 128, "Vacancy provenance");
			if ((status == VacancyStatus.FILLED) != (assignment != null))
			{
				throw new IllegalArgumentException("Vacancy assignment and status disagree.");
			}
		}
	}

	public record RoleMatchResult(List<RoleAssignment> assignments, List<Vacancy> vacancies, String catalogHash, String evidenceHash)
	{
		public RoleMatchResult
		{
			assignments = boundedCopy(assignments, MAX_ASSIGNMENTS, "Role assignments");
			vacancies = boundedCopy(vacancies, MAX_REQUIREMENTS, "Role vacancies");
			catalogHash = requireHash(catalogHash, "Role catalog hash");
			evidenceHash = requireHash(evidenceHash, "Role evidence hash");
		}
	}

	public record RouteManifest(String routeId, long generation, PhantomDomainRef destination, List<PhantomNavigationPoint> waypoints, int currentWaypoint, int regroupRadius, int maximumSeparation, RouteStatus status, String topologyHash, String navigationHash)
	{
		public RouteManifest
		{
			routeId = requireHash(routeId, "Route ID");
			if ((generation < 1) || (destination == null) || (waypoints == null) || waypoints.isEmpty() || (waypoints.size() > MAX_ROUTE_WAYPOINTS) || (currentWaypoint < 0) || (currentWaypoint >= waypoints.size()) || (regroupRadius < 50) || (regroupRadius > 5000) || (maximumSeparation < regroupRadius) || (maximumSeparation > 20000))
			{
				throw new IllegalArgumentException("Invalid shared party route.");
			}
			waypoints = List.copyOf(waypoints);
			Objects.requireNonNull(status, "Route status must not be null.");
			topologyHash = requireHash(topologyHash, "Route topology hash");
			navigationHash = requireHash(navigationHash, "Route navigation hash");
		}

		public RouteManifest withProgress(int waypoint, RouteStatus replacementStatus)
		{
			return new RouteManifest(routeId, generation, destination, waypoints, waypoint, regroupRadius, maximumSeparation, replacementStatus, topologyHash, navigationHash);
		}
	}

	public record PartyOperation(String operationId, OperationKind kind, OperationPhase phase, MemberRef leader, MemberRef member, long leaderGoalId, long leaderGoalRevision, String manifestHash, long invitationSequence, long deadlineLogicalNanos, String failureKey)
	{
		public PartyOperation
		{
			operationId = requireHash(operationId, "Party operation ID");
			Objects.requireNonNull(kind, "Party operation kind must not be null.");
			Objects.requireNonNull(phase, "Party operation phase must not be null.");
			Objects.requireNonNull(leader, "Party operation leader must not be null.");
			if ((leaderGoalId <= 0) || (leaderGoalRevision < 0) || (invitationSequence < 0) || (deadlineLogicalNanos <= 0))
			{
				throw new IllegalArgumentException("Invalid party operation identity.");
			}
			manifestHash = requireHash(manifestHash, "Party operation manifest hash");
			failureKey = failureKey == null || failureKey.isEmpty() ? "" : requireKey(failureKey, "Party operation failure");
		}

		public PartyOperation withPhase(OperationPhase replacement, long invitation, String failure)
		{
			return new PartyOperation(operationId, kind, replacement, leader, member, leaderGoalId, leaderGoalRevision, manifestHash, invitation, deadlineLogicalNanos, failure);
		}
	}

	public record PartyState(String groupId, long groupGeneration, long membershipRevision, StateStatus status, MemberRef leader, String ownRoleKey, String leaderManifestHash, List<MemberRef> phantomMembers, List<MemberRef> realMembers, ObjectiveMode objectiveMode, PhantomDomainRef objectiveRef, List<RoleRequirement> requirements, List<RoleAssignment> assignments, RouteManifest route, PartyOperation operation, String progressionHash, String topologyHash, String lastFailureKey)
	{
		public PartyState
		{
			groupId = requireHash(groupId, "Party group ID");
			if ((groupGeneration < 1) || (membershipRevision < 0))
			{
				throw new IllegalArgumentException("Invalid party generation or membership revision.");
			}
			Objects.requireNonNull(status, "Party state status must not be null.");
			Objects.requireNonNull(leader, "Party leader must not be null.");
			ownRoleKey = ownRoleKey == null || ownRoleKey.isEmpty() ? "" : requireKey(ownRoleKey, "Own party role");
			leaderManifestHash = requireHash(leaderManifestHash, "Leader manifest hash");
			phantomMembers = sortedUniqueMembers(phantomMembers, MemberKind.PHANTOM);
			realMembers = sortedUniqueMembers(realMembers, MemberKind.REAL);
			if ((phantomMembers.size() + realMembers.size()) > MAX_ROSTER)
			{
				throw new IllegalArgumentException("Party roster exceeds nine members.");
			}
			Objects.requireNonNull(objectiveMode, "Party objective mode must not be null.");
			Objects.requireNonNull(objectiveRef, "Party objective ref must not be null.");
			requirements = boundedCopy(requirements, MAX_REQUIREMENTS, "Party role requirements");
			assignments = boundedCopy(assignments, MAX_ASSIGNMENTS, "Party role assignments");
			progressionHash = requireHash(progressionHash, "Party progression hash");
			topologyHash = requireHash(topologyHash, "Party topology hash");
			lastFailureKey = lastFailureKey == null || lastFailureKey.isEmpty() ? "" : requireKey(lastFailureKey, "Party failure key");
			if ((status == StateStatus.LEADER) && (leader.kind() != MemberKind.PHANTOM))
			{
				throw new IllegalArgumentException("A Phantom leader claim requires a Phantom leader.");
			}
		}

		public String canonicalManifestHash()
		{
			final StringBuilder value = new StringBuilder();
			value.append(groupId).append('|').append(groupGeneration).append('|').append(membershipRevision).append('|').append(leader.stableKey()).append('|').append(objectiveMode).append('|').append(objectiveRef.namespace()).append(':').append(objectiveRef.key());
			phantomMembers.forEach(member -> value.append("|p:").append(member.stableKey()));
			realMembers.forEach(member -> value.append("|r:").append(member.stableKey()));
			requirements.forEach(requirement -> value.append("|q:").append(requirement.vacancyKey()).append(':').append(requirement.roleKey()).append(':').append(requirement.required()).append(':').append(requirement.minimumScore()));
			assignments.forEach(assignment -> value.append("|a:").append(assignment.vacancyKey()).append(':').append(assignment.member().stableKey()).append(':').append(assignment.capabilityKey()).append(':').append(assignment.variantKey()).append(':').append(assignment.score()));
			if (route != null)
			{
				value.append("|route:").append(route.routeId()).append(':').append(route.generation());
			}
			return sha256(value.toString());
		}

		public PartyState withOperation(StateStatus replacementStatus, PartyOperation replacementOperation, String failureKey)
		{
			return new PartyState(groupId, groupGeneration, membershipRevision, replacementStatus, leader, ownRoleKey, leaderManifestHash, phantomMembers, realMembers, objectiveMode, objectiveRef, requirements, assignments, route, replacementOperation, progressionHash, topologyHash, failureKey);
		}

		public PartyState committed(StateStatus replacementStatus, List<MemberRef> replacementPhantoms, List<MemberRef> replacementReals, List<RoleAssignment> replacementAssignments, String replacementManifestHash)
		{
			return new PartyState(groupId, groupGeneration, Math.addExact(membershipRevision, 1), replacementStatus, leader, ownRoleKey, replacementManifestHash, replacementPhantoms, replacementReals, objectiveMode, objectiveRef, requirements, replacementAssignments, route, operation == null ? null : operation.withPhase(OperationPhase.COMMITTED, operation.invitationSequence(), ""), progressionHash, topologyHash, "");
		}
	}

	public record TacticalDirective(DirectiveKind kind, MemberRef actor, MemberRef targetMember, int targetObjectId, String capabilityKey, String variantKey, String targetScope, int actionSkillId, int actionSkillLevel, String reasonKey, int priority)
	{
		public TacticalDirective
		{
			Objects.requireNonNull(kind, "Party directive kind must not be null.");
			Objects.requireNonNull(actor, "Party directive actor must not be null.");
			capabilityKey = capabilityKey == null || capabilityKey.isEmpty() ? "" : requireKey(capabilityKey, "Directive capability");
			variantKey = variantKey == null || variantKey.isEmpty() ? "" : requireKey(variantKey, "Directive variant");
			targetScope = targetScope == null ? "" : targetScope;
			if (targetScope.length() > 48)
			{
				throw new IllegalArgumentException("Directive target scope is too long.");
			}
			reasonKey = requireKey(reasonKey, "Directive reason");
			if ((targetObjectId < 0) || (actionSkillId < 0) || (actionSkillLevel < 0) || (priority < 0) || (priority > 10000))
			{
				throw new IllegalArgumentException("Invalid tactical directive.");
			}
		}
	}

	public static String stableGroupId(long leaderProfileId, long goalId, long goalRevision)
	{
		if ((leaderProfileId <= 0) || (goalId <= 0) || (goalRevision < 0))
		{
			throw new IllegalArgumentException("Invalid group identity inputs.");
		}
		return sha256("party.group|" + leaderProfileId + '|' + goalId + '|' + goalRevision);
	}

	public static String stableOperationId(String groupId, long generation, long membershipRevision, OperationKind kind, MemberRef leader, MemberRef member, long goalId, long goalRevision, String manifestHash)
	{
		return sha256(requireHash(groupId, "Operation group ID") + '|' + generation + '|' + membershipRevision + '|' + kind + '|' + leader.stableKey() + '|' + (member == null ? "none" : member.stableKey()) + '|' + goalId + '|' + goalRevision + '|' + requireHash(manifestHash, "Operation manifest hash"));
	}

	public static String sha256(String value)
	{
		try
		{
			final byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			final StringBuilder result = new StringBuilder(64);
			for (byte octet : digest)
			{
				result.append(String.format(java.util.Locale.ROOT, "%02X", octet & 0xff));
			}
			return result.toString();
		}
		catch (NoSuchAlgorithmException e)
		{
			throw new IllegalStateException("SHA-256 is unavailable.", e);
		}
	}

	public static String requireKey(String value, String label)
	{
		Objects.requireNonNull(value, label + " must not be null.");
		if (!KEY.matcher(value).matches())
		{
			throw new IllegalArgumentException(label + " is not a canonical key.");
		}
		return value;
	}

	public static String requireHash(String value, String label)
	{
		Objects.requireNonNull(value, label + " must not be null.");
		if (!SHA256.matcher(value).matches())
		{
			throw new IllegalArgumentException(label + " must be an uppercase SHA-256.");
		}
		return value;
	}

	private static String requireBounded(String value, int maximum, String label)
	{
		Objects.requireNonNull(value, label + " must not be null.");
		if (value.isBlank() || (value.length() > maximum) || !value.equals(value.trim()))
		{
			throw new IllegalArgumentException(label + " is invalid.");
		}
		return value;
	}

	private static List<MemberRef> sortedUniqueMembers(List<MemberRef> members, MemberKind kind)
	{
		final List<MemberRef> copy = boundedCopy(members, MAX_ROSTER, "Party members").stream().sorted(Comparator.comparing(MemberRef::stableKey)).toList();
		if ((copy.stream().anyMatch(member -> member.kind() != kind)) || (new HashSet<>(copy).size() != copy.size()))
		{
			throw new IllegalArgumentException("Party member list has wrong kind or duplicates.");
		}
		return copy;
	}

	private static <T> List<T> boundedCopy(List<T> values, int maximum, String label)
	{
		Objects.requireNonNull(values, label + " must not be null.");
		if (values.size() > maximum)
		{
			throw new IllegalArgumentException(label + " exceeds its bound.");
		}
		final ArrayList<T> copy = new ArrayList<>(values.size());
		for (T value : values)
		{
			copy.add(Objects.requireNonNull(value, label + " contains null."));
		}
		return List.copyOf(copy);
	}

	private PhantomPartyModel()
	{
	}
}
