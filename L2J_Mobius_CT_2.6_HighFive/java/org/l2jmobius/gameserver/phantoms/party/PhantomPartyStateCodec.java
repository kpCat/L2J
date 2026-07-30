/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.party;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.util.ArrayList;
import java.util.List;

import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.DirectiveKind;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberKind;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.ObjectiveMode;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.OperationKind;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.OperationPhase;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.PartyOperation;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.PartyState;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.RoleAssignment;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.RoleRequirement;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.RouteManifest;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.RouteStatus;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.StateStatus;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;

/**
 * Bounded binary codec for the durable party manifest/member claim.
 */
public final class PhantomPartyStateCodec
{
	private static final int MAGIC = 0x50545931; // PTY1

	public byte[] encode(PartyState state)
	{
		try
		{
			final ByteArrayOutputStream bytes = new ByteArrayOutputStream(1024);
			try (DataOutputStream output = new DataOutputStream(bytes))
			{
				output.writeInt(MAGIC);
				string(output, state.groupId());
				output.writeLong(state.groupGeneration());
				output.writeLong(state.membershipRevision());
				value(output, state.status());
				member(output, state.leader());
				string(output, state.ownRoleKey());
				string(output, state.leaderManifestHash());
				members(output, state.phantomMembers());
				members(output, state.realMembers());
				value(output, state.objectiveMode());
				domain(output, state.objectiveRef());
				output.writeByte(state.requirements().size());
				for (RoleRequirement requirement : state.requirements())
				{
					string(output, requirement.vacancyKey());
					string(output, requirement.roleKey());
					output.writeBoolean(requirement.required());
					output.writeInt(requirement.minimumScore());
				}
				output.writeByte(state.assignments().size());
				for (RoleAssignment assignment : state.assignments())
				{
					assignment(output, assignment);
				}
				output.writeBoolean(state.route() != null);
				if (state.route() != null)
				{
					route(output, state.route());
				}
				output.writeBoolean(state.operation() != null);
				if (state.operation() != null)
				{
					operation(output, state.operation());
				}
				string(output, state.progressionHash());
				string(output, state.topologyHash());
				string(output, state.lastFailureKey());
			}
			final byte[] result = bytes.toByteArray();
			if (result.length > PhantomProfileComponent.MAX_PAYLOAD_BYTES)
			{
				throw new IllegalArgumentException("Party state exceeds the component payload bound.");
			}
			return result;
		}
		catch (RuntimeException e)
		{
			throw e;
		}
		catch (Exception e)
		{
			throw new IllegalArgumentException("Could not encode party state.", e);
		}
	}

	public PartyState decode(byte[] payload)
	{
		if ((payload == null) || (payload.length == 0) || (payload.length > PhantomProfileComponent.MAX_PAYLOAD_BYTES))
		{
			throw new IllegalArgumentException("Party state payload is outside bounds.");
		}
		try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload)))
		{
			if (input.readInt() != MAGIC)
			{
				throw new IllegalArgumentException("Unknown party state payload.");
			}
			final String groupId = string(input, 64);
			final long groupGeneration = input.readLong();
			final long membershipRevision = input.readLong();
			final StateStatus status = value(input, StateStatus.class);
			final MemberRef leader = member(input);
			final String ownRoleKey = string(input, 64);
			final String leaderManifestHash = string(input, 64);
			final List<MemberRef> phantomMembers = members(input, PhantomPartyModel.MAX_ROSTER);
			final List<MemberRef> realMembers = members(input, PhantomPartyModel.MAX_ROSTER);
			final ObjectiveMode objective = value(input, ObjectiveMode.class);
			final PhantomDomainRef objectiveRef = domain(input);
			final List<RoleRequirement> requirements = new ArrayList<>();
			for (int count = unsignedCount(input, PhantomPartyModel.MAX_REQUIREMENTS); count > 0; count--)
			{
				requirements.add(new RoleRequirement(string(input, 64), string(input, 64), input.readBoolean(), input.readInt()));
			}
			final List<RoleAssignment> assignments = new ArrayList<>();
			for (int count = unsignedCount(input, PhantomPartyModel.MAX_ASSIGNMENTS); count > 0; count--)
			{
				assignments.add(assignment(input));
			}
			final RouteManifest route = input.readBoolean() ? route(input) : null;
			final PartyOperation operation = input.readBoolean() ? operation(input) : null;
			final PartyState state = new PartyState(groupId, groupGeneration, membershipRevision, status, leader, ownRoleKey, leaderManifestHash, phantomMembers, realMembers, objective, objectiveRef, requirements, assignments, route, operation, string(input, 64), string(input, 64), string(input, 64));
			if (input.available() != 0)
			{
				throw new IllegalArgumentException("Party state payload has trailing data.");
			}
			return state;
		}
		catch (RuntimeException e)
		{
			throw e;
		}
		catch (EOFException e)
		{
			throw new IllegalArgumentException("Party state payload is truncated.", e);
		}
		catch (Exception e)
		{
			throw new IllegalArgumentException("Could not decode party state.", e);
		}
	}

	private static void route(DataOutputStream output, RouteManifest route) throws Exception
	{
		string(output, route.routeId());
		output.writeLong(route.generation());
		domain(output, route.destination());
		output.writeByte(route.waypoints().size());
		for (PhantomNavigationPoint point : route.waypoints())
		{
			output.writeInt(point.x());
			output.writeInt(point.y());
			output.writeInt(point.z());
			output.writeInt(point.instanceId());
		}
		output.writeInt(route.currentWaypoint());
		output.writeInt(route.regroupRadius());
		output.writeInt(route.maximumSeparation());
		value(output, route.status());
		string(output, route.topologyHash());
		string(output, route.navigationHash());
	}

	private static RouteManifest route(DataInputStream input) throws Exception
	{
		final String routeId = string(input, 64);
		final long generation = input.readLong();
		final PhantomDomainRef destination = domain(input);
		final List<PhantomNavigationPoint> waypoints = new ArrayList<>();
		for (int count = unsignedCount(input, PhantomPartyModel.MAX_ROUTE_WAYPOINTS); count > 0; count--)
		{
			waypoints.add(new PhantomNavigationPoint(input.readInt(), input.readInt(), input.readInt(), input.readInt()));
		}
		return new RouteManifest(routeId, generation, destination, waypoints, input.readInt(), input.readInt(), input.readInt(), value(input, RouteStatus.class), string(input, 64), string(input, 64));
	}

	private static void operation(DataOutputStream output, PartyOperation operation) throws Exception
	{
		string(output, operation.operationId());
		value(output, operation.kind());
		value(output, operation.phase());
		member(output, operation.leader());
		output.writeBoolean(operation.member() != null);
		if (operation.member() != null)
		{
			member(output, operation.member());
		}
		output.writeLong(operation.leaderGoalId());
		output.writeLong(operation.leaderGoalRevision());
		string(output, operation.manifestHash());
		output.writeLong(operation.invitationSequence());
		output.writeLong(operation.deadlineLogicalNanos());
		string(output, operation.failureKey());
	}

	private static PartyOperation operation(DataInputStream input) throws Exception
	{
		final String id = string(input, 64);
		final OperationKind kind = value(input, OperationKind.class);
		final OperationPhase phase = value(input, OperationPhase.class);
		final MemberRef leader = member(input);
		final MemberRef member = input.readBoolean() ? member(input) : null;
		return new PartyOperation(id, kind, phase, leader, member, input.readLong(), input.readLong(), string(input, 64), input.readLong(), input.readLong(), string(input, 64));
	}

	private static void assignment(DataOutputStream output, RoleAssignment assignment) throws Exception
	{
		string(output, assignment.vacancyKey());
		string(output, assignment.roleKey());
		member(output, assignment.member());
		string(output, assignment.capabilityKey());
		string(output, assignment.variantKey());
		output.writeInt(assignment.score());
		string(output, assignment.provenance());
	}

	private static RoleAssignment assignment(DataInputStream input) throws Exception
	{
		return new RoleAssignment(string(input, 64), string(input, 64), member(input), string(input, 64), string(input, 64), input.readInt(), string(input, 128));
	}

	private static void members(DataOutputStream output, List<MemberRef> members) throws Exception
	{
		output.writeByte(members.size());
		for (MemberRef member : members)
		{
			member(output, member);
		}
	}

	private static List<MemberRef> members(DataInputStream input, int maximum) throws Exception
	{
		final List<MemberRef> result = new ArrayList<>();
		for (int count = unsignedCount(input, maximum); count > 0; count--)
		{
			result.add(member(input));
		}
		return result;
	}

	private static void member(DataOutputStream output, MemberRef member) throws Exception
	{
		value(output, member.kind());
		output.writeLong(member.profileId());
		output.writeInt(member.characterObjectId());
	}

	private static MemberRef member(DataInputStream input) throws Exception
	{
		return new MemberRef(value(input, MemberKind.class), input.readLong(), input.readInt());
	}

	private static void domain(DataOutputStream output, PhantomDomainRef ref) throws Exception
	{
		string(output, ref.namespace());
		string(output, ref.key());
	}

	private static PhantomDomainRef domain(DataInputStream input) throws Exception
	{
		return new PhantomDomainRef(string(input, 32), string(input, 128));
	}

	private static void string(DataOutputStream output, String value) throws Exception
	{
		output.writeUTF(value);
	}

	private static String string(DataInputStream input, int maximum) throws Exception
	{
		final String value = input.readUTF();
		if (value.length() > maximum)
		{
			throw new IllegalArgumentException("Party state string exceeds its bound.");
		}
		return value;
	}

	private static void value(DataOutputStream output, Enum<?> value) throws Exception
	{
		output.writeByte(value.ordinal());
	}

	private static <E extends Enum<E>> E value(DataInputStream input, Class<E> type) throws Exception
	{
		final int ordinal = input.readUnsignedByte();
		final E[] values = type.getEnumConstants();
		if (ordinal >= values.length)
		{
			throw new IllegalArgumentException("Party state enum ordinal is invalid.");
		}
		return values[ordinal];
	}

	private static int unsignedCount(DataInputStream input, int maximum) throws Exception
	{
		final int count = input.readUnsignedByte();
		if (count > maximum)
		{
			throw new IllegalArgumentException("Party state collection exceeds its bound.");
		}
		return count;
	}
}
