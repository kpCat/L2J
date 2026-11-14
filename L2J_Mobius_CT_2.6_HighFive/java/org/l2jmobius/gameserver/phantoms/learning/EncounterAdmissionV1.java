/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.learning;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatPolicy;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillRef;

/** Portable admission facts only. Canonical native owners retain every game rule and lease. */
public final class EncounterAdmissionV1
{
	public static final String VERSION = "ENCOUNTER_ADMISSION_V1";
	public enum Mode { SOLO_PVE, PARTY_PVE, PVP_1V1, PVP_PARTY, RAID_WITH_GUARDS }
	public enum Stage { PROPOSED, HEADLESS_READY, LIVE_ADMITTED }
	public enum Reason { SOURCE_SKILLS, MAGIC_LEVEL, PARTY_ROLE, TARGET_LEVEL_REWARD, FACTION, WORLD_AI_MOVEMENT,
		GEO_LOS, TARGET_PLACEMENT, RISK, REQUEST_TIMEOUT, CAPACITY, FRESHNESS, CONFIG_PARITY, OWNER, NATIVE_ADMISSION }

	public record Entity(String role, String sourceSha, String capabilitySha)
	{
		public Entity
		{
			if (role == null || !role.matches("[A-Z_]{1,32}")) { throw new IllegalArgumentException("AUTONOMOUS_ENCOUNTER_ENTITY_ROLE"); }
			requireSha(sourceSha); requireSha(capabilitySha);
		}
	}

	/** Source images and acquired inventories are independent identities; the shortlist never replaces either. */
	public record Proposal(Mode mode, LegalActionV3.Family family, List<Entity> entities, String targetSource,
		String placementSha, String configSha, long observationMillis, List<SkillRef> shortlist)
	{
		public Proposal
		{
			Objects.requireNonNull(mode); Objects.requireNonNull(family);
			entities = List.copyOf(entities); shortlist = List.copyOf(shortlist);
			if (mode != Mode.SOLO_PVE && mode != Mode.PARTY_PVE) { throw new IllegalArgumentException("AUTONOMOUS_ENCOUNTER_MODE_NOT_IMPLEMENTED"); }
			if (entities.size() < (mode == Mode.SOLO_PVE ? 1 : 2) || entities.size() > (mode == Mode.SOLO_PVE ? 1 : 9)
				|| entities.stream().map(Entity::sourceSha).distinct().count() != entities.size()
				|| entities.stream().filter(entity -> entity.role().equals("LEARNER")).count() != 1)
			{ throw new IllegalArgumentException("AUTONOMOUS_ENCOUNTER_ROSTER"); }
			if (family != LegalActionV3.Family.DAMAGE && family != LegalActionV3.Family.HEAL || family == LegalActionV3.Family.HEAL && mode != Mode.PARTY_PVE
				|| shortlist.size() < (family == LegalActionV3.Family.DAMAGE ? 2 : 1) || shortlist.size() > 4
				|| shortlist.stream().map(SkillRef::skillId).distinct().count() != shortlist.size())
			{ throw new IllegalArgumentException("AUTONOMOUS_ENCOUNTER_ACTIONS"); }
			if (targetSource == null || !targetSource.matches("[0-9]+@[^\\t\\r\\n]{1,180}")) { throw new IllegalArgumentException("AUTONOMOUS_ENCOUNTER_TARGET_SOURCE"); }
			requireSha(placementSha); requireSha(configSha);
			if (observationMillis < 1000 || observationMillis > PhantomCombatPolicy.MAXIMUM_TIMEOUT_MILLIS)
			{ throw new IllegalArgumentException("AUTONOMOUS_ENCOUNTER_REQUEST_TIMEOUT"); }
		}
		/** Party's ordinary owner uses a 60s request inside the separate observation window. */
		public long requestTimeoutMillis() { return mode == Mode.PARTY_PVE ? 60000 : observationMillis; }
		public String fingerprint()
		{
			return hash(VERSION + "/" + mode + "/" + family + "/" + entities + "/" + targetSource + "/" + placementSha
				+ "/" + configSha + "/" + observationMillis + "/" + requestTimeoutMillis() + "/" + shortlist);
		}
	}

	/** Even a native-Player headless witness cannot attest a running GameServer World. */
	public record Receipt(Proposal proposal, Stage stage, String physicalSha, List<Reason> reasons)
	{
		public Receipt
		{
			Objects.requireNonNull(proposal); Objects.requireNonNull(stage); requireSha(physicalSha);
			reasons = List.copyOf(reasons);
		}
		public boolean sealable() { return stage == Stage.LIVE_ADMITTED && reasons.isEmpty(); }
	}

	public static List<String> parity(Receipt prepared, Receipt current)
	{
		final var differences = new ArrayList<String>();
		if (!prepared.proposal().fingerprint().equals(current.proposal().fingerprint())) { differences.add("PROPOSAL"); }
		if (!prepared.physicalSha().equals(current.physicalSha())) { differences.add("PHYSICAL_FACTS"); }
		for (var reason : current.reasons()) { differences.add(reason.name()); }
		return List.copyOf(differences);
	}

	public static String catalogFingerprint(String configSha, String cellsBody, List<String> capabilityBindings)
	{
		requireSha(configSha);
		return hash(VERSION + "/" + configSha + "/" + hash(cellsBody) + "/" + capabilityBindings.stream().sorted().toList());
	}
	private static String hash(String value) { return NeuralPolicy.sha256(value.getBytes(StandardCharsets.UTF_8)); }
	private static void requireSha(String value)
	{ if (value == null || !value.matches("[a-f0-9]{64}")) { throw new IllegalArgumentException("AUTONOMOUS_ENCOUNTER_FINGERPRINT"); } }
	private EncounterAdmissionV1() { }
}
