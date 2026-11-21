/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.l2jmobius.gameserver.phantoms.learning.HealEvidenceV3.SourceOwner;
import org.l2jmobius.gameserver.phantoms.learning.LearningCastEvidence.PvpPoolWrite;
import org.l2jmobius.gameserver.phantoms.learning.LearningCastEvidence.PvpReceipt;
import org.l2jmobius.gameserver.phantoms.learning.LegalActionV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3.Field;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3.Role;
import org.l2jmobius.gameserver.phantoms.learning.TacticalStoreV3;
import org.l2jmobius.gameserver.phantoms.learning.TerminalRewardReceiptV1.TimedAction;
import org.l2jmobius.tests.phantoms.PhantomLearning131AuditDamage.DuelDeath;

/** Test-side paid rows inside a SHA-pinned NATIVE_PAID131_V1 episode document. */
final class PhantomLearning131AuditOriginals
{
	private static final int MAX_ROWS = 1024;
	private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
	private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

	record Fact(String eventKey, String operationId, int actor, long actorEpoch, int target, long targetEpoch, int skillId,
		int skillLevel, long atNanos, BigDecimal hp, BigDecimal cp, long lethalNanos, String lethalKey,
		boolean pvp, long capturedNanos, long lastNativeWriteNanos)
	{
		Fact withDeath(long nanos, String key)
		{
			return new Fact(eventKey, operationId, actor, actorEpoch, target, targetEpoch, skillId, skillLevel, atNanos, hp, cp, nanos, key,
				pvp, capturedNanos, lastNativeWriteNanos);
		}
	}

	private record Paid(Fact fact, boolean requiresStockDeath) { }

	private PhantomLearning131AuditOriginals() { }

	static Map<String, Fact> decode(List<String[]> rows, String episodeId)
	{
		require(episodeId.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,95}") && rows.size() <= MAX_ROWS, "EPISODE_OR_ROW_LIMIT");
		final var facts = new HashMap<String, Fact>();
		final var operations = new HashSet<String>();
		final var requiredDeaths = new HashSet<String>();
		final var deaths = new HashMap<String, String[]>();
		for (String[] row : rows)
		{
			require(row.length >= 2, "ROW_SHAPE");
			final String key = eventKey(row[1]);
			if (row[0].equals("death"))
			{
				require(row.length == 12 && deaths.putIfAbsent(key, row) == null, "DEATH_ROW_OR_DUPLICATE");
				continue;
			}
			final Paid paid = switch (row[0])
			{
				case "pve" -> readPve(row, key, episodeId);
				case "pvp" -> readPvp(row, key, episodeId);
				default -> throw new IllegalArgumentException("AUDIT_ORIGINAL131_ROW_KIND");
			};
			require(facts.putIfAbsent(key, paid.fact()) == null && operations.add(paid.fact().operationId()), "DUPLICATE_PAID_ORIGINAL");
			if (paid.requiresStockDeath()) { requiredDeaths.add(key); }
		}
		for (var entry : deaths.entrySet())
		{
			final String key = entry.getKey();
			require(requiredDeaths.remove(key), "DEATH_WITHOUT_LETHAL_PVP_ORIGINAL");
			final Fact fact = facts.get(key);
			final DuelDeath stock = death(entry.getValue(), 2), paid = death(entry.getValue(), 7);
			require(stock.killer() == fact.actor() && stock.victim() == fact.target() && stock.victimEpoch() == fact.targetEpoch(), "DEATH_RECIPIENT");
			final long observed;
			try { observed = PhantomLearning131AuditDamage.joinedDeathNanos(stock, paid, fact.atNanos()); }
			catch (IllegalStateException mismatch) { throw new IllegalArgumentException("AUDIT_ORIGINAL131_DEATH_JOIN", mismatch); }
			facts.put(key, fact.withDeath(observed, deathKey(key, stock.corpseGeneration())));
		}
		require(requiredDeaths.isEmpty(), "LETHAL_PVP_ORIGINAL_WITHOUT_DEATH");
		return Map.copyOf(facts);
	}

	/** event key, TacticalStoreV3 row, instance, two HP stamps, two MP stamps, lethal writer flag. */
	static String pveRow(TimedAction original)
	{
		return row("pve", original.row().eventKey(), ENCODER.encodeToString(TacticalStoreV3.encode(original.row())),
			original.instanceId(), original.hpStartedNanos(), original.hpFinishedNanos(), original.mpStartedNanos(),
			original.mpFinishedNanos(), original.linkedLethalWrite());
	}

	private static Paid readPve(String[] row, String key, String episodeId)
	{
		require(row.length == 9, "PVE_ROW_SHAPE");
		final byte[] bytes = bytes(row[2], 600000);
		final var decoded = TacticalStoreV3.decode(bytes);
		require(Arrays.equals(bytes, TacticalStoreV3.encode(decoded)) && decoded.eventKey().equals(key)
			&& decoded.split().equals("AUDIT") && decoded.contextKey().equals("task131|AUDIT|" + episodeId), "PVE_ROW_IDENTITY");
		final var original = new TimedAction(decoded, nonnegativeInt(row[3]), positive(row[4]), positive(row[5]), positive(row[6]),
			positive(row[7]), bool(row[8]));
		final var receipt = decoded.receipt();
		final var action = receipt.attempt().selected();
		final var observation = receipt.attempt().observation();
		final var target = observation.entity(action.targetObjectId());
		require(action.family() == LegalActionV3.Family.DAMAGE && action.actorObjectId() != action.targetObjectId()
			&& target != null && target.role() == Role.ENEMY && target.fact(Field.NPC_ID).known() && target.fact(Field.NPC_ID).value() > 0
			&& original.hpStartedNanos() >= observation.capturedNanos() && original.mpStartedNanos() >= observation.capturedNanos(),
			"PVE_NATIVE_SCOPE");
		require(!original.linkedLethalWrite(), "LETHAL_PVE_ORIGINAL_WITHOUT_DEATH");
		return new Paid(new Fact(key, operationId(episodeId, receipt.owner()), action.actorObjectId(), action.actorEpoch(), action.targetObjectId(),
			action.targetEpoch(), action.skillId(), action.skillLevel(), original.hpFinishedNanos(), wireDamage(receipt.actualHp()), BigDecimal.ZERO, 0, "",
			false, observation.capturedNanos(), Math.max(original.hpFinishedNanos(), original.mpFinishedNanos())), false);
	}

	/** A canonical observation and selected native rank, owner, MP debit, and two nullable exact pool writes. */
	static String pvpRow(PvpReceipt original)
	{
		final var action = original.attempt().selected();
		return row("pvp", original.eventKey(), ENCODER.encodeToString(original.attempt().observation().bytes()), action.targetObjectId(),
			action.targetEpoch(), action.skillId(), action.skillLevel(), original.owner().generation(),
			ENCODER.encodeToString(original.owner().operationKey().getBytes(StandardCharsets.UTF_8)), original.instanceId(), original.elapsedNanos(),
			original.mp(), original.mpFirstWrite(), original.mpLastWrite())
			+ "\t" + pool(original.hp()) + "\t" + pool(original.cp()) + "\t" + original.lethal();
	}

	private static Paid readPvp(String[] row, String key, String episodeId)
	{
		require(row.length == 23, "PVP_ROW_SHAPE");
		final byte[] observationBytes = bytes(row[2], 524288);
		final var observation = TacticalObservationV3.restore(observationBytes);
		require(Arrays.equals(observation.bytes(), observationBytes), "PVP_OBSERVATION_ENCODING");
		final int targetId = object(row[3]), skill = object(row[5]), rank = object(row[6]);
		final long targetEpoch = positive(row[4]);
		final var target = observation.entity(targetId);
		require(target != null && observation.entities().size() == 2 && targetId != observation.actorObjectId() && target.role() == Role.ENEMY
			&& target.epoch() == targetEpoch && !target.fact(Field.NPC_ID).known()
			&& observation.self().abilities().stream().anyMatch(ability -> ability.skillId() == skill && ability.level() == rank), "PVP_LEARNED_IDENTITY");
		final byte[] ownerBytes = bytes(row[8], 512);
		final String ownerKey = new String(ownerBytes, StandardCharsets.UTF_8);
		final var owner = new SourceOwner(positive(row[7]), ownerKey);
		require(Arrays.equals(ownerBytes, ownerKey.getBytes(StandardCharsets.UTF_8))
			&& ownerKey.equals("Combat/" + observation.profileId() + "/" + owner.generation() + "/" + observation.decisionId()), "PVP_NATIVE_OWNER");
		nonnegativeInt(row[9]); // Instance remains in the original; the episode binding owns the scored roster.
		positive(row[10]); // Native elapsed cast clock.
		final long mpStart = positive(row[12]), mpEnd = positive(row[13]);
		require(scalar(row[11]) > 0 && mpStart >= observation.capturedNanos() && mpEnd >= mpStart, "PVP_NATIVE_MP");
		final PvpPoolWrite hp = readPool(row, 14), cp = readPool(row, 18);
		final boolean lethal = bool(row[22]);
		require((hp != null || cp != null) && (!lethal || hp != null && hp.after() < 0.5)
			&& poolMatches(hp, target, Field.MAX_HP, observation.capturedNanos())
			&& poolMatches(cp, target, Field.MAX_CP, observation.capturedNanos()), "PVP_NATIVE_POOL");
		final long damageNanos = Math.max(last(hp), last(cp));
		return new Paid(new Fact(key, operationId(episodeId, owner), observation.actorObjectId(), observation.actorEpoch(), targetId, targetEpoch,
			skill, rank, damageNanos, delta(hp), delta(cp), 0, "", true, observation.capturedNanos(), Math.max(damageNanos, mpEnd)), lethal);
	}

	private static String pool(PvpPoolWrite write)
	{
		return write == null ? "-\t-\t-\t-" : row(write.before(), write.after(), write.firstWrite(), write.lastWrite());
	}

	private static PvpPoolWrite readPool(String[] row, int column)
	{
		if (!row[column].equals("-"))
		{
			return new PvpPoolWrite(scalar(row[column]), scalar(row[column + 1]), positive(row[column + 2]), positive(row[column + 3]));
		}
		require(row[column + 1].equals("-") && row[column + 2].equals("-") && row[column + 3].equals("-"), "PVP_ABSENT_POOL");
		return null;
	}

	private static boolean poolMatches(PvpPoolWrite write, TacticalObservationV3.Entity target, Field maximum, long observed)
	{
		final var max = target.fact(maximum);
		return write == null || max.known() && max.value() > 0 && write.before() <= max.value() + 1e-6 && write.firstWrite() >= observed;
	}

	private static BigDecimal delta(PvpPoolWrite write) { return write == null ? BigDecimal.ZERO : wireDamage(write.delta()); }
	private static long last(PvpPoolWrite write) { return write == null ? 0 : write.lastWrite(); }

	/** Independent stock and paid post-commit witnesses. Either one alone is insufficient for a DEATH row. */
	static String deathRow(String eventKey, DuelDeath stock, DuelDeath paid)
	{
		return row("death", eventKey, stock.killer(), stock.victim(), stock.victimEpoch(), stock.corpseGeneration(), stock.observedNanos(),
			paid.killer(), paid.victim(), paid.victimEpoch(), paid.corpseGeneration(), paid.observedNanos());
	}

	private static DuelDeath death(String[] row, int column)
	{
		return new DuelDeath(object(row[column]), object(row[column + 1]), positive(row[column + 2]), positive(row[column + 3]), positive(row[column + 4]));
	}

	static String deathKey(String eventKey, long corpseGeneration)
	{
		return TacticalObservationV3.hash(("TASK131_AUDIT_NATIVE_DEATH_V1\n" + eventKey + "\n" + corpseGeneration).getBytes(StandardCharsets.UTF_8));
	}

	static BigDecimal wireDamage(double delta)
	{
		final var value = BigDecimal.valueOf(delta).setScale(9, RoundingMode.HALF_EVEN).stripTrailingZeros();
		require(value.signum() > 0 && value.toPlainString().matches("[0-9]{1,18}(\\.[0-9]{1,9})?"), "DAMAGE_WIRE_PRECISION");
		return value;
	}

	private static String operationId(String episode, SourceOwner owner) { return PhantomLearning131AuditDamage.operationId(episode, owner); }

	private static byte[] bytes(String wire, int limit)
	{
		require(!wire.isEmpty() && wire.length() <= (limit * 4 + 2) / 3, "BASE64_SIZE");
		final byte[] decoded = DECODER.decode(wire);
		require(decoded.length <= limit && ENCODER.encodeToString(decoded).equals(wire), "NONCANONICAL_BASE64");
		return decoded;
	}

	private static String row(Object... cells)
	{
		return String.join("\t", Arrays.stream(cells).map(Object::toString).toList());
	}

	private static String eventKey(String key)
	{
		require(key.matches("[a-f0-9]{64}"), "EVENT_KEY");
		return key;
	}

	private static long positive(String text)
	{
		final long value = Long.parseLong(text);
		require(value > 0 && Long.toString(value).equals(text), "POSITIVE_ID_OR_TIME");
		return value;
	}

	private static int object(String text) { return Math.toIntExact(positive(text)); }

	private static int nonnegativeInt(String text)
	{
		final int value = Integer.parseInt(text);
		require(value >= 0 && Integer.toString(value).equals(text), "NONNEGATIVE_INT");
		return value;
	}

	private static double scalar(String text)
	{
		final double value = Double.parseDouble(text);
		require(Double.isFinite(value) && value >= 0 && Double.toString(value).equals(text), "NONCANONICAL_SCALAR");
		return value;
	}

	private static boolean bool(String text)
	{
		require(text.equals("true") || text.equals("false"), "BOOLEAN");
		return text.equals("true");
	}

	private static void require(boolean condition, String reason)
	{
		if (!condition) { throw new IllegalArgumentException("AUDIT_ORIGINAL131_" + reason); }
	}
}
