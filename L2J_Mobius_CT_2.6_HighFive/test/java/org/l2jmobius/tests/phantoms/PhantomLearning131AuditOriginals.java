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
import org.l2jmobius.gameserver.phantoms.learning.LearningCastEvidence.NativeTerminal;
import org.l2jmobius.gameserver.phantoms.learning.LearningCastEvidence.PvpPoolWrite;
import org.l2jmobius.gameserver.phantoms.learning.LearningCastEvidence.PvpReceipt;
import org.l2jmobius.gameserver.phantoms.learning.LegalActionV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3.Field;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3.Role;
import org.l2jmobius.gameserver.phantoms.learning.TacticalStoreV3;
import org.l2jmobius.gameserver.phantoms.learning.TerminalRewardReceiptV1.TimedAction;
import org.l2jmobius.tests.phantoms.PhantomLearning131AuditDamage.DuelDeath;

/** Test-side paid rows inside a SHA-pinned NATIVE_PAID131_V2 episode document. */
final class PhantomLearning131AuditOriginals
{
	private static final int MAX_ROWS = 1024;
	private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
	private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

	record Fact(String eventKey, String operationId, int actor, long actorEpoch, int target, long targetEpoch, int skillId,
		int skillLevel, long atNanos, BigDecimal hp, BigDecimal cp, long lethalNanos, String lethalKey,
		boolean pvp, long capturedNanos, long lastNativeWriteNanos, long killCreditNanos, String killCreditKey, long lastWitnessNanos)
	{
		Fact(String eventKey, String operationId, int actor, long actorEpoch, int target, long targetEpoch, int skillId,
			int skillLevel, long atNanos, BigDecimal hp, BigDecimal cp, long lethalNanos, String lethalKey,
			boolean pvp, long capturedNanos, long lastNativeWriteNanos)
		{
			this(eventKey, operationId, actor, actorEpoch, target, targetEpoch, skillId, skillLevel, atNanos, hp, cp, lethalNanos, lethalKey,
				pvp, capturedNanos, lastNativeWriteNanos, 0, "", lethalNanos);
		}

		Fact withTerminal(NativeTerminal terminal, long lastWitness)
		{
			return new Fact(eventKey, operationId, actor, actorEpoch, target, targetEpoch, skillId, skillLevel, terminal.damageNanos(), hp, cp,
				terminal.deathNanos(), deathKey(eventKey, pvp ? terminal.corpseGeneration() : targetEpoch), pvp, capturedNanos,
				lastNativeWriteNanos, terminal.killCreditNanos(), terminal.killSequence() == 0 ? "" : PhantomLearning131AuditOriginals.killCreditKey(eventKey, terminal.killSequence()),
				lastWitness);
		}
	}

	private record Paid(Fact fact, int instanceId, long firstHpWrite, boolean lethal, double afterHp) { }
	private record KillCredit(int actor, long epoch, long sequence) { }

	private PhantomLearning131AuditOriginals() { }

	static Map<String, Fact> decode(List<String[]> rows, String episodeId)
	{
		require(episodeId.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,95}") && rows.size() <= MAX_ROWS, "EPISODE_OR_ROW_LIMIT");
		final var paid = new HashMap<String, Paid>();
		final var operations = new HashSet<String>();
		final var terminals = new HashMap<String, NativeTerminal>();
		final var stock = new HashMap<String, DuelDeath>();
		for (String[] row : rows)
		{
			require(row.length >= 2, "ROW_SHAPE");
			final String key = eventKey(row[1]);
			if (row[0].equals("terminal"))
			{
				require(row.length == 12 && terminals.putIfAbsent(key, readTerminal(row, key)) == null, "TERMINAL_ROW_OR_DUPLICATE");
				continue;
			}
			if (row[0].equals("stock"))
			{
				require(row.length == 7 && stock.putIfAbsent(key, readStock(row)) == null, "STOCK_ROW_OR_DUPLICATE");
				continue;
			}
			final Paid original = switch (row[0])
			{
				case "pve" -> readPve(row, key, episodeId);
				case "pvp" -> readPvp(row, key, episodeId);
				default -> throw new IllegalArgumentException("AUDIT_ORIGINAL131_ROW_KIND");
			};
			require(paid.putIfAbsent(key, original) == null && operations.add(original.fact().operationId()), "DUPLICATE_PAID_ORIGINAL");
		}
		final var facts = new HashMap<String, Fact>();
		final var credits = new HashSet<KillCredit>();
		for (var entry : paid.entrySet())
		{
			final String key = entry.getKey();
			final Paid original = entry.getValue();
			final NativeTerminal terminal = terminals.remove(key);
			// Native NPC terminals are additive to the historical TimedAction flag.
			require((!original.lethal() || terminal != null) && (terminal == null || !original.fact().pvp() || original.lethal()),
				"LETHAL_ORIGINAL_TERMINAL_JOIN");
			if (terminal == null)
			{
				facts.put(key, original.fact());
				continue;
			}
			final Fact joined = join(original, terminal, stock.remove(key));
			if (terminal.killSequence() > 0)
			{
				require(credits.add(new KillCredit(joined.actor(), joined.actorEpoch(), terminal.killSequence())), "DUPLICATE_KILL_CREDIT");
			}
			facts.put(key, joined);
		}
		require(terminals.isEmpty() && stock.isEmpty(), "UNMATCHED_TERMINAL_OR_STOCK");
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
			&& (!original.linkedLethalWrite() || receipt.afterHp() < 0.5)
			&& original.hpStartedNanos() >= observation.capturedNanos() && original.mpStartedNanos() >= observation.capturedNanos(),
			"PVE_NATIVE_SCOPE");
		return new Paid(new Fact(key, operationId(episodeId, receipt.owner()), action.actorObjectId(), action.actorEpoch(), action.targetObjectId(),
			action.targetEpoch(), action.skillId(), action.skillLevel(), original.hpFinishedNanos(), wireDamage(receipt.actualHp()), BigDecimal.ZERO, 0, "",
			false, observation.capturedNanos(), Math.max(original.hpFinishedNanos(), original.mpFinishedNanos())),
			original.instanceId(), original.hpStartedNanos(), original.linkedLethalWrite(), receipt.afterHp());
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
		final int instance = nonnegativeInt(row[9]);
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
			skill, rank, damageNanos, delta(hp), delta(cp), 0, "", true, observation.capturedNanos(), Math.max(damageNanos, mpEnd)),
			instance, hp == null ? 0 : hp.firstWrite(), lethal, hp == null ? 0 : hp.after());
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

	private static Fact join(Paid original, NativeTerminal terminal, DuelDeath stock)
	{
		final Fact fact = original.fact();
		require(terminal.actorObjectId() == fact.actor() && terminal.actorEpoch() == fact.actorEpoch() && terminal.targetObjectId() == fact.target()
			&& terminal.targetEpoch() == fact.targetEpoch() && terminal.instanceId() == original.instanceId(), "TERMINAL_RECIPIENT_OR_INSTANCE");
		require(terminal.damageNanos() <= terminal.deathNanos(), "TERMINAL_BEFORE_NATIVE_DAMAGE");
		if (fact.pvp())
		{
			require(terminal.damageNanos() == fact.atNanos() && terminal.corpseGeneration() > 0 && terminal.killCreditNanos() == 0
				&& terminal.killSequence() == 0 && matchesStock(terminal, stock), "PVP_TERMINAL_OR_STOCK");
			return fact.withTerminal(terminal, stock.observedNanos());
		}
		require(stock == null && terminal.corpseGeneration() == 0 && original.afterHp() < 0.5 && terminal.damageNanos() >= original.firstHpWrite()
			&& terminal.deathNanos() <= fact.atNanos() && (terminal.killSequence() == 0
				? terminal.killCreditNanos() == 0 : terminal.killCreditNanos() >= terminal.deathNanos()), "PVE_TERMINAL_OR_KILL_CREDIT");
		return fact.withTerminal(terminal, Math.max(terminal.deathNanos(), terminal.killCreditNanos()));
	}

	static boolean matchesStock(NativeTerminal terminal, DuelDeath stock)
	{
		return stock != null && stock.killer() == terminal.actorObjectId() && stock.victim() == terminal.targetObjectId()
			&& stock.victimEpoch() == terminal.targetEpoch() && stock.corpseGeneration() == terminal.corpseGeneration()
			&& stock.observedNanos() >= terminal.deathNanos();
	}

	static String terminalRow(NativeTerminal terminal)
	{
		return row("terminal", terminal.eventKey(), terminal.actorObjectId(), terminal.actorEpoch(), terminal.targetObjectId(), terminal.targetEpoch(),
			terminal.instanceId(), terminal.corpseGeneration(), terminal.deathNanos(), terminal.killCreditNanos(), terminal.killSequence(), terminal.damageNanos());
	}

	private static NativeTerminal readTerminal(String[] row, String key)
	{
		return new NativeTerminal(key, object(row[2]), positive(row[3]), object(row[4]), positive(row[5]), nonnegativeInt(row[6]),
			nonnegative(row[7]), positive(row[8]), nonnegative(row[9]), nonnegative(row[10]), positive(row[11]));
	}

	static String stockRow(String eventKey, DuelDeath stock)
	{
		return row("stock", eventKey, stock.killer(), stock.victim(), stock.victimEpoch(), stock.corpseGeneration(), stock.observedNanos());
	}

	private static DuelDeath readStock(String[] row)
	{
		return new DuelDeath(object(row[2]), object(row[3]), positive(row[4]), positive(row[5]), positive(row[6]));
	}

	static String deathKey(String eventKey, long corpseGeneration)
	{
		return TacticalObservationV3.hash(("TASK131_AUDIT_NATIVE_DEATH_V1\n" + eventKey + "\n" + corpseGeneration).getBytes(StandardCharsets.UTF_8));
	}

	static String killCreditKey(String eventKey, long sequence)
	{
		return TacticalObservationV3.hash(("TASK131_AUDIT_NATIVE_KILL_CREDIT_V1\n" + eventKey + "\n" + sequence).getBytes(StandardCharsets.UTF_8));
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

	private static long nonnegative(String text)
	{
		final long value = Long.parseLong(text);
		require(value >= 0 && Long.toString(value).equals(text), "NONNEGATIVE_ID_OR_TIME");
		return value;
	}

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
