package org.l2jmobius.gameserver.localplay;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundStateCodec;

/** Addressable read-only attestation for operator selection; never repairs or changes readiness. */
public final class LocalPlayM1LegacyQuarantine
{
	public enum Decision { ELIGIBLE, KNOWN_PREFIX_FAIL_CLOSED, UNKNOWN_INCONSISTENT }
	public record Witness(long profileId, int objectId, long rowVersion, String payloadSha256, String canonicalSha256) {}
	private static volatile Map<Long, Witness> _witnesses = Map.of();
	private static final String ATTESTED_SHA256 = "4ed5c029bd6ec495a2ed6910c7ff0f04ce6664121f30df13f396f0c6968a5847";

	private LocalPlayM1LegacyQuarantine() {}

	static void configure(Path runtime) throws Exception
	{
		_witnesses = Map.of();
		final Path root = runtime.resolve("playtest-quarantine");
		final Path file = root.resolve("witnesses.tsv");
		if (!Files.exists(file)) { return; }
		if (!LocalPlayPilotService.safeDirectory(root) || !LocalPlayPilotService.privateAcl(root) || !LocalPlayPilotService.privateAcl(file) || Files.isSymbolicLink(file) || !Files.isRegularFile(file) || (Files.size(file) > 16384)) { throw new IllegalArgumentException("M1_QUARANTINE_PRIVATE_GUARD"); }
		final byte[] bytes = Files.readAllBytes(file);
		if (!ATTESTED_SHA256.equals(hash(bytes))) { throw new IllegalArgumentException("M1_QUARANTINE_ATTESTATION_CHANGED"); }
		_witnesses = parseWitnesses(new String(bytes, StandardCharsets.UTF_8).lines().toList());
	}

	private static Map<Long, Witness> parseWitnesses(java.util.List<String> lines)
	{
		if (lines.isEmpty() || !"M1_KNOWN_PREFIX_FAIL_CLOSED_V2".equals(lines.get(0)) || (lines.size() > 43)) { throw new IllegalArgumentException("M1_QUARANTINE_HEADER_OR_CAP"); }
		final Map<Long, Witness> values = new HashMap<>();
		long previous = 0;
		for (String line : lines.subList(1, lines.size()))
		{
			final String[] p = line.split("\t", -1);
			if ((p.length != 5) || !p[3].matches("[0-9a-f]{64}") || !p[4].matches("[0-9a-f]{64}")) { throw new IllegalArgumentException("M1_QUARANTINE_WITNESS_INVALID"); }
			final Witness witness = new Witness(Long.parseLong(p[0]), Integer.parseInt(p[1]), Long.parseLong(p[2]), p[3], p[4]);
			if ((witness.profileId() <= previous) || (witness.objectId() <= 0) || (witness.rowVersion() <= 0) || (values.put(witness.profileId(), witness) != null)) { throw new IllegalArgumentException("M1_QUARANTINE_IDENTITY_INVALID"); }
			previous = witness.profileId();
		}
		return Map.copyOf(values);
	}

	public static Decision attested(Witness witness, long profileId, int objectId, long rowVersion, String payloadHash, String canonicalHash)
	{
		if ((witness == null) || (witness.profileId() != profileId) || (witness.objectId() != objectId) || (witness.rowVersion() != rowVersion) || !witness.payloadSha256().equals(payloadHash) || !witness.canonicalSha256().equals(canonicalHash)) { return Decision.UNKNOWN_INCONSISTENT; }
		if ((org.l2jmobius.gameserver.model.World.getInstance().findObject(objectId) != null) || (org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(objectId) != null) || org.l2jmobius.gameserver.taskmanagers.PlayerAutoSaveTaskManager.getInstance().containsObjectId(objectId)) { return Decision.UNKNOWN_INCONSISTENT; }
		return Decision.KNOWN_PREFIX_FAIL_CLOSED;
	}

	public static Decision inspect(long profileId)
	{
		try (Connection connection = DatabaseFactory.getConnection())
		{
			connection.setReadOnly(true); connection.setAutoCommit(false);
			try (PreparedStatement query = connection.prepareStatement("SELECT p.character_object_id,b.row_version,b.payload FROM phantom_profiles p LEFT JOIN phantom_profile_components b ON b.profile_id=p.profile_id AND b.component_type='background.state' WHERE p.profile_id=?"))
			{
				query.setLong(1, profileId);
				try (ResultSet row = query.executeQuery())
				{
					if (!row.next() || (row.getBytes(3) == null)) { return Decision.ELIGIBLE; }
					final byte[] payload = row.getBytes(3);
					final var state = new PhantomBackgroundStateCodec().decode(payload);
					if (state.state() != PhantomBackgroundState.State.INCONSISTENT) { return Decision.ELIGIBLE; }
					final Witness witness = _witnesses.get(profileId);
					if (witness == null) { return Decision.UNKNOWN_INCONSISTENT; }
					if ((org.l2jmobius.gameserver.model.World.getInstance().findObject(row.getInt(1)) != null) || (org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(row.getInt(1)) != null) || org.l2jmobius.gameserver.taskmanagers.PlayerAutoSaveTaskManager.getInstance().containsObjectId(row.getInt(1))) { return Decision.UNKNOWN_INCONSISTENT; }
					return attested(witness, profileId, row.getInt(1), row.getLong(2), hash(payload), canonicalHash(connection, profileId, row.getInt(1), state.identity().classIndex()));
				}
			}
			finally { connection.rollback(); }
		}
		catch (Exception failure) { throw new IllegalStateException("M1_QUARANTINE_READ_FAILED:" + profileId, failure); }
	}

	/** Same columns and ordering as the saved pre-fix SELECT audit, including items/skills. */
	private static String canonicalHash(Connection connection, long profileId, int objectId, int classIndex) throws Exception
	{
		final StringBuilder value = new StringBuilder();
		try (var query = connection.prepareStatement("SELECT c.charId,c.classid,c.race,c.level,c.exp,c.sp,c.expBeforeDeath,c.curHp,c.maxHp,c.curMp,c.maxMp,c.curCp,c.maxCp,c.x,c.y,c.z,c.heading,c.online,s.class_index,s.class_id,s.level,s.exp,s.sp FROM characters c LEFT JOIN character_subclasses s ON s.charId=c.charId AND s.class_index=? WHERE c.charId=?"))
		{
			query.setInt(1, classIndex); query.setInt(2, objectId);
			try (var row = query.executeQuery())
			{
				if (!row.next()) { return "ABSENT"; }
				for (int column = 1; column <= 23; column++) { if (column > 1) { value.append('\t'); } final String cell = row.getString(column); value.append(cell == null ? "NULL" : new java.math.BigDecimal(cell).stripTrailingZeros().toPlainString()); }
				value.append('\n');
			}
		}
		for (String sql : java.util.List.of("SELECT object_id,item_id,count,loc FROM items WHERE owner_id=? AND loc IN ('INVENTORY','PAPERDOLL') ORDER BY object_id", "SELECT skill_id,skill_level,class_index FROM character_skills WHERE charId=? AND class_index=" + classIndex + " ORDER BY skill_id"))
		{
			try (var query = connection.prepareStatement(sql))
			{
				query.setInt(1, objectId);
				try (var rows = query.executeQuery()) { while (rows.next()) { value.append(profileId); for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) { value.append('\t').append(rows.getString(i)); } value.append('\n'); } }
			}
		}
		return hash(value.toString().getBytes(StandardCharsets.UTF_8));
	}

	private static String hash(byte[] data) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)); }
}
