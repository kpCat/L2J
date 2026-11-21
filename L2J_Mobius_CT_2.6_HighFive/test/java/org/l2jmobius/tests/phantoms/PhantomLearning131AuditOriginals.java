/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence.ItemDebit;
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence.NpcAggroCast;
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence.NpcAggroSwitch;
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence.Target;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.phantoms.learning.HealEvidenceV3.NativeHealReceipt;
import org.l2jmobius.gameserver.phantoms.learning.HealEvidenceV3.NativeRechargeReceipt;
import org.l2jmobius.gameserver.phantoms.learning.HealEvidenceV3.SourceOwner;
import org.l2jmobius.gameserver.phantoms.learning.LearningCastEvidence.NativeCastTrace;
import org.l2jmobius.gameserver.phantoms.learning.LearningCastEvidence.NativeTerminal;
import org.l2jmobius.gameserver.phantoms.learning.LearningCastEvidence.PvpPoolWrite;
import org.l2jmobius.gameserver.phantoms.learning.LearningCastEvidence.PvpReceipt;
import org.l2jmobius.gameserver.phantoms.learning.LegalActionV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3.Field;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3.Role;
import org.l2jmobius.gameserver.phantoms.learning.TacticalReceiptV3.Attempt;
import org.l2jmobius.gameserver.phantoms.learning.TacticalStoreV3;
import org.l2jmobius.gameserver.phantoms.learning.TerminalRewardReceiptV1.TimedAction;
import org.l2jmobius.tests.phantoms.PhantomLearning131AuditDamage.DuelDeath;

/** Test-side paid rows inside a SHA-pinned NATIVE_PAID131_V7 episode document. */
final class PhantomLearning131AuditOriginals
{
	static final int MAX_ITEM_DEBITS = 2048;
	static final int MAX_AGGRO_CASTS = 512;
	// 9 x 64 damage casts, up to 18 death/stock pairs, plus support, aggro and item ledgers.
	private static final int MAX_ROWS = 2 * 9 * 64 + 2 * 18 + 512 + MAX_AGGRO_CASTS + MAX_ITEM_DEBITS + 1;
	private static final double HEAL_WRITE_TOLERANCE = 1e-6;
	private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
	private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

	record Fact(String eventKey, String operationId, int actor, long actorEpoch, int target, long targetEpoch, int skillId,
		int skillLevel, long atNanos, BigDecimal hp, BigDecimal cp, long lethalNanos, String lethalKey,
		boolean pvp, long capturedNanos, long lastNativeWriteNanos, long killCreditNanos, String killCreditKey, long lastWitnessNanos,
		NativeCastTrace trace)
	{
		Fact(String eventKey, String operationId, int actor, long actorEpoch, int target, long targetEpoch, int skillId,
			int skillLevel, long atNanos, BigDecimal hp, BigDecimal cp, long lethalNanos, String lethalKey,
			boolean pvp, long capturedNanos, long lastNativeWriteNanos)
		{
			this(eventKey, operationId, actor, actorEpoch, target, targetEpoch, skillId, skillLevel, atNanos, hp, cp, lethalNanos, lethalKey,
				pvp, capturedNanos, lastNativeWriteNanos, 0, "", lethalNanos);
		}

		Fact(String eventKey, String operationId, int actor, long actorEpoch, int target, long targetEpoch, int skillId,
			int skillLevel, long atNanos, BigDecimal hp, BigDecimal cp, long lethalNanos, String lethalKey,
			boolean pvp, long capturedNanos, long lastNativeWriteNanos, long killCreditNanos, String killCreditKey, long lastWitnessNanos)
		{
			this(eventKey, operationId, actor, actorEpoch, target, targetEpoch, skillId, skillLevel, atNanos, hp, cp, lethalNanos, lethalKey,
				pvp, capturedNanos, lastNativeWriteNanos, killCreditNanos, killCreditKey, lastWitnessNanos, null);
		}

		Fact withTrace(NativeCastTrace nativeTrace)
		{
			return new Fact(eventKey, operationId, actor, actorEpoch, target, targetEpoch, skillId, skillLevel, atNanos, hp, cp, lethalNanos, lethalKey,
				pvp, capturedNanos, lastNativeWriteNanos, killCreditNanos, killCreditKey, lastWitnessNanos, nativeTrace);
		}

		Fact withTerminal(NativeTerminal terminal, long lastWitness)
		{
			return new Fact(eventKey, operationId, actor, actorEpoch, target, targetEpoch, skillId, skillLevel, terminal.damageNanos(), hp, cp,
				terminal.deathNanos(), deathKey(eventKey, pvp ? terminal.corpseGeneration() : targetEpoch), pvp, capturedNanos,
				lastNativeWriteNanos, terminal.killCreditNanos(), terminal.killSequence() == 0 ? "" : PhantomLearning131AuditOriginals.killCreditKey(eventKey, terminal.killSequence()),
				lastWitness, trace);
		}
	}

	private record Paid(Fact fact, int instanceId, long firstEffectWrite, boolean lethal, double afterHp,
		TacticalObservationV3 observation, int baselineId, int baselineRank) { }
	private record KillCredit(int actor, long epoch, long sequence) { }

	record ItemLedger(long openedNanos, long closedNanos, long count, Map<String, ItemDebit> debits)
	{
		ItemLedger { debits = Map.copyOf(debits); }
	}

	record HealFact(NativeHealReceipt original, String operationId, BigDecimal effective, BigDecimal overheal) { }
	record RechargeFact(NativeRechargeReceipt original, String operationId, BigDecimal effective)
	{
		long effectNanos() { return original.targetWriteNanos() == 0 ? original.handlerFinishedNanos() : original.targetWriteNanos(); }
	}
	record AggroFact(NpcAggroCast cast, Map<String, NpcAggroSwitch> effects) { }
	private record AggroCastClock(int actor, long epoch, long nanos) { }
	record AuditRows(Map<String, Fact> damage, ItemLedger items, Map<String, HealFact> heals, Map<String, RechargeFact> recharges,
		Map<String, AggroFact> aggro) { }

	private PhantomLearning131AuditOriginals() { }

	static AuditRows decodeAuditRows(List<String[]> rows, String episodeId)
	{
		require(episodeId.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,95}") && rows.size() <= MAX_ROWS, "EPISODE_OR_ROW_LIMIT");
		final var damage = new ArrayList<String[]>();
		final var items = new ArrayList<String[]>();
		final var heals = new ArrayList<String[]>();
		final var recharges = new ArrayList<String[]>();
		final var aggro = new ArrayList<String[]>();
		String[] window = null;
		for (String[] cells : rows)
		{
			require(cells.length >= 2, "ROW_SHAPE");
			switch (cells[0])
			{
				case "item-window" ->
				{
					require(window == null && cells.length == 4, "ITEM_WINDOW_SHAPE_OR_DUPLICATE");
					window = cells;
				}
				case "item" -> items.add(cells);
				case "heal" -> heals.add(cells);
				case "recharge" -> recharges.add(cells);
				case "aggro" -> aggro.add(cells);
				default -> damage.add(cells);
			}
		}
		require(window != null, "ITEM_WINDOW_MISSING");
		final long opened = positive(window[1]), closed = positive(window[2]), count = nonnegative(window[3]);
		checkItemWindow(opened, closed, count);
		require(count == items.size(), "ITEM_WINDOW_COUNT");
		final var byKey = new HashMap<String, ItemDebit>();
		final var stacks = new HashMap<Integer, ItemDebit>();
		for (String[] cells : items)
		{
			final ItemDebit debit = readItem(cells);
			final String key = itemKey(episodeId, itemPayload(debit));
			require(key.equals(cells[1]) && debit.sequence() == byKey.size() + 1L
				&& debit.observedNanos() >= opened && debit.observedNanos() <= closed, "ITEM_KEY_SEQUENCE_OR_CLOCK");
			final ItemDebit previous = stacks.put(debit.itemObjectId(), debit);
			require(previous == null || previous.actorObjectId() == debit.actorObjectId() && previous.actorEpoch() == debit.actorEpoch()
				&& previous.itemId() == debit.itemId() && previous.after() == debit.before() && previous.observedNanos() <= debit.observedNanos(),
				"ITEM_STACK_CONTINUITY");
			require(byKey.putIfAbsent(key, debit) == null, "ITEM_DUPLICATE");
		}
		final var damageFacts = decode(damage, episodeId);
		final var keys = new HashSet<>(byKey.keySet());
		final var operations = new HashSet<>(byKey.keySet());
		for (Fact fact : damageFacts.values())
		{
			require(keys.add(fact.eventKey()) && operations.add(fact.operationId()), "REUSED_PAID_IDENTITY");
		}
		final var healFacts = new HashMap<String, HealFact>();
		for (String[] cells : heals)
		{
			final HealFact heal = healFact(episodeId, readHeal(cells));
			final String key = heal.original().cast().eventKey();
			require(keys.add(key) && operations.add(heal.operationId()), "REUSED_PAID_IDENTITY");
			healFacts.put(key, heal);
		}
		final var rechargeFacts = new HashMap<String, RechargeFact>();
		for (String[] cells : recharges)
		{
			final RechargeFact recharge = rechargeFact(episodeId, readRecharge(cells));
			final String key = recharge.original().cast().eventKey();
			require(keys.add(key) && operations.add(recharge.operationId()), "REUSED_PAID_IDENTITY");
			rechargeFacts.put(key, recharge);
		}
		final var aggroFacts = new HashMap<String, AggroFact>();
		final var aggroHits = new HashSet<AggroCastClock>();
		final var aggroDebits = new HashSet<AggroCastClock>();
		require(aggro.size() <= MAX_AGGRO_CASTS, "AGGRO_CAST_CAP");
		for (String[] cells : aggro)
		{
			final var cast = readAggro(cells);
			final String operation = eventKey(cells[1]);
			require(aggroRow(episodeId, cast).equals(String.join("\t", cells)) && operations.add(operation)
				&& aggroHits.add(new AggroCastClock(cast.actor(), cast.epoch(), cast.hitStartedNanos()))
				&& aggroDebits.add(new AggroCastClock(cast.actor(), cast.epoch(), cast.paidNanos())), "AGGRO_CAST_IDENTITY");
			final var effects = new HashMap<String, NpcAggroSwitch>();
			for (var change : cast.switches())
			{
				final String key = aggroEffectKey(operation, change);
				require(keys.add(key), "REUSED_AGGRO_EFFECT_IDENTITY");
				effects.put(key, change);
			}
			aggroFacts.put(operation, new AggroFact(cast, Map.copyOf(effects)));
		}
		return new AuditRows(damageFacts, new ItemLedger(opened, closed, count, byKey), Map.copyOf(healFacts), Map.copyOf(rechargeFacts), Map.copyOf(aggroFacts));
	}

	static Map<String, Fact> decode(List<String[]> rows, String episodeId)
	{
		require(episodeId.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,95}") && rows.size() <= MAX_ROWS, "EPISODE_OR_ROW_LIMIT");
		final var paid = new HashMap<String, Paid>();
		final var operations = new HashSet<String>();
		final var traces = new HashMap<String, NativeCastTrace>();
		final var terminals = new HashMap<String, NativeTerminal>();
		final var stock = new HashMap<String, DuelDeath>();
		for (String[] row : rows)
		{
			require(row.length >= 2, "ROW_SHAPE");
			final String key = eventKey(row[1]);
			if (row[0].equals("cast"))
			{
				require(row.length == 15 && traces.putIfAbsent(key, readCast(row, key)) == null, "CAST_ROW_OR_DUPLICATE");
				continue;
			}
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
			final Fact fact = original.fact();
			final NativeCastTrace trace = traces.remove(key);
			require(trace != null && traceMatches(trace, key, original.instanceId(), original.observation(), fact.target(), fact.targetEpoch(),
				fact.skillId(), fact.skillLevel(), original.firstEffectWrite())
				&& trace.baselineSkillId() == original.baselineId() && trace.baselineSkillLevel() == original.baselineRank(),
				"PAID_ORIGINAL_CAST_JOIN");
			final NativeTerminal terminal = terminals.remove(key);
			// Native NPC terminals are additive to the historical TimedAction flag.
			require((!original.lethal() || terminal != null) && (terminal == null || !original.fact().pvp() || original.lethal()),
				"LETHAL_ORIGINAL_TERMINAL_JOIN");
			if (terminal == null)
			{
				facts.put(key, fact.withTrace(trace));
				continue;
			}
			final Fact joined = join(original, terminal, stock.remove(key));
			if (terminal.killSequence() > 0)
			{
				require(credits.add(new KillCredit(joined.actor(), joined.actorEpoch(), terminal.killSequence())), "DUPLICATE_KILL_CREDIT");
			}
			facts.put(key, joined.withTrace(trace));
		}
		require(traces.isEmpty() && terminals.isEmpty() && stock.isEmpty(), "UNMATCHED_CAST_TERMINAL_OR_STOCK");
		return Map.copyOf(facts);
	}

	static String itemWindow(long opened, long closed, long count)
	{
		checkItemWindow(opened, closed, count);
		return row("item-window", opened, closed, count);
	}

	private static void checkItemWindow(long opened, long closed, long count)
	{
		require(opened > 0 && closed >= opened && count >= 0 && count <= MAX_ITEM_DEBITS, "ITEM_WINDOW_BOUNDS");
	}

	static String itemRow(String episodeId, ItemDebit debit)
	{
		final String payload = itemPayload(debit);
		return row("item", itemKey(episodeId, payload)) + "\t" + payload;
	}

	static String paymentRow(String episodeId, ItemDebit debit)
	{
		final String key = itemKey(episodeId, itemPayload(debit));
		return row("event", key, debit.observedNanos(), "PAY", debit.actorObjectId(), debit.actorObjectId(), key, debit.itemId(), debit.before() - debit.after());
	}

	private static String itemKey(String episodeId, String payload)
	{
		require(episodeId.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,95}"), "EPISODE_ID");
		return TacticalObservationV3.hash(("TASK131_AUDIT_NATIVE_ITEM_DEBIT_V1\n" + episodeId + "\n" + payload).getBytes(StandardCharsets.UTF_8));
	}

	private static String itemPayload(ItemDebit debit)
	{
		require(debit.sequence() > 0 && debit.sequence() <= MAX_ITEM_DEBITS && debit.actorObjectId() > 0 && debit.actorEpoch() > 0
			&& debit.itemObjectId() > 0 && debit.itemId() > 0 && debit.after() >= 0 && debit.before() > debit.after() && debit.observedNanos() > 0,
			"ITEM_NATIVE_DEBIT");
		final ItemProcessType process = debit.process();
		require(process == null || process == ItemProcessType.NONE, "ITEM_PROCESS");
		return row(debit.sequence(), debit.actorObjectId(), debit.actorEpoch(), debit.itemObjectId(), debit.itemId(),
			process == null ? "-" : process.name(), debit.before(), debit.after(), debit.observedNanos());
	}

	private static ItemDebit readItem(String[] cells)
	{
		require(cells.length == 11, "ITEM_ROW_SHAPE");
		final String process = cells[7];
		require(process.equals("-") || process.equals("NONE"), "ITEM_PROCESS");
		return new ItemDebit(object(cells[3]), positive(cells[4]), object(cells[5]), object(cells[6]), positive(cells[8]), nonnegative(cells[9]),
			null, process.equals("-") ? null : ItemProcessType.NONE, positive(cells[2]), positive(cells[10]));
	}

	static String healRow(NativeHealReceipt original)
	{
		checkHeal(original);
		final SourceOwner owner = original.owner();
		return castRow("heal", original.cast()) + "\t" + row(ENCODER.encodeToString(original.observation().bytes()), owner.generation(),
			ENCODER.encodeToString(owner.operationKey().getBytes(StandardCharsets.UTF_8)), original.offeredNanos(), original.hpWriteNanos(),
			original.mpStartedNanos(), original.mpFinishedNanos(), original.beforeHp(), original.afterHp(), original.offeredHp(),
			original.requestedHp(), original.paidMp());
	}

	private static NativeHealReceipt readHeal(String[] cells)
	{
		require(cells.length == 27, "HEAL_ROW_SHAPE");
		final byte[] observationBytes = bytes(cells[15], 524288);
		final var observation = TacticalObservationV3.restore(observationBytes);
		final byte[] ownerBytes = bytes(cells[17], 512);
		final var owner = new SourceOwner(positive(cells[16]), new String(ownerBytes, StandardCharsets.UTF_8));
		require(Arrays.equals(observationBytes, observation.bytes()) && Arrays.equals(ownerBytes, owner.operationKey().getBytes(StandardCharsets.UTF_8)),
			"HEAL_NATIVE_ENCODING");
		return new NativeHealReceipt(readCast(cells, eventKey(cells[1])), observation, owner, positive(cells[18]), nonnegative(cells[19]),
			positive(cells[20]), positive(cells[21]), scalar(cells[22]), scalar(cells[23]), scalar(cells[24]), scalar(cells[25]), scalar(cells[26]));
	}

	static List<String> healEvents(String episodeId, NativeHealReceipt original)
	{
		final HealFact fact = healFact(episodeId, original);
		final NativeCastTrace cast = original.cast();
		final var events = new ArrayList<>(castEvents(cast, fact.operationId(), "HEAL"));
		final long effectNanos = original.hpWriteNanos() == 0 ? original.offeredNanos() : original.hpWriteNanos();
		events.add(row("event", cast.eventKey(), effectNanos, "HEAL", cast.actorObjectId(), cast.targetObjectId(), fact.operationId(),
			fact.effective().toPlainString(), fact.overheal().toPlainString()));
		return List.copyOf(events);
	}

	private static HealFact healFact(String episodeId, NativeHealReceipt original)
	{
		require(episodeId.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,95}"), "EPISODE_ID");
		checkHeal(original);
		final BigDecimal effective = quantizeHp(original.afterHp() - original.beforeHp());
		final BigDecimal overheal = quantizeHp(original.offeredHp() - original.requestedHp());
		require((original.requestedHp() == 0 || effective.signum() > 0) && effective.signum() + overheal.signum() > 0
			&& wireHpBounded(effective) && wireHpBounded(overheal), "HEAL_WIRE_PRECISION");
		return new HealFact(original, operationId(episodeId, original.owner()), effective, overheal);
	}

	private static void checkHeal(NativeHealReceipt original)
	{
		final NativeCastTrace cast = original.cast();
		final TacticalObservationV3 observation = original.observation();
		final var ally = observation.entity(cast.targetObjectId());
		require(ally != null && ally.role() == Role.ALLY && !observation.partyIdentity().isBlank()
			&& traceMatches(cast, cast.eventKey(), cast.instanceId(), observation, ally.objectId(), ally.epoch(),
				cast.skillId(), cast.skillLevel(), original.offeredNanos())
			&& learned(observation, cast.skillId(), cast.skillLevel()) && learned(observation, cast.baselineSkillId(), cast.baselineSkillLevel()),
			"HEAL_CAST_OR_PARTY");
		require(original.mpStartedNanos() >= cast.decisionNanos() && original.mpFinishedNanos() >= original.mpStartedNanos()
			&& Double.isFinite(original.paidMp()) && original.paidMp() > 0 && Double.isFinite(original.offeredHp()) && original.offeredHp() > 0
			&& Double.compare(original.requestedHp(), 0) >= 0 && original.requestedHp() <= original.offeredHp(), "HEAL_CLAMP_OR_MP");
		if (original.requestedHp() == 0)
		{
			require(original.hpWriteNanos() == 0 && Double.compare(original.beforeHp(), 0) == 0 && Double.compare(original.afterHp(), 0) == 0,
				"HEAL_ZERO_CLAMP");
			return;
		}
		final var maxHp = ally.fact(Field.MAX_HP);
		require(original.hpWriteNanos() >= original.offeredNanos() && maxHp.known() && maxHp.value() > 0
			&& Double.compare(original.beforeHp(), 0) >= 0 && original.afterHp() > original.beforeHp() && original.afterHp() <= maxHp.value() + HEAL_WRITE_TOLERANCE
			&& Math.abs((original.afterHp() - original.beforeHp()) - original.requestedHp()) <= HEAL_WRITE_TOLERANCE, "HEAL_NATIVE_HP_WRITE");
	}

	static String rechargeRow(NativeRechargeReceipt original)
	{
		checkRecharge(original);
		return castRow("recharge", original.cast()) + "\t" + row(ENCODER.encodeToString(original.observation().bytes()), original.owner().generation(),
			ENCODER.encodeToString(original.owner().operationKey().getBytes(StandardCharsets.UTF_8)), original.handlerFinishedNanos(), original.targetWriteNanos(),
			original.mpStartedNanos(), original.mpFinishedNanos(), original.beforeMp(), original.afterMp(), original.paidMp());
	}

	private static NativeRechargeReceipt readRecharge(String[] cells)
	{
		require(cells.length == 25, "RECHARGE_ROW_SHAPE");
		final byte[] observationBytes = bytes(cells[15], 524288);
		final var observation = TacticalObservationV3.restore(observationBytes);
		final byte[] ownerBytes = bytes(cells[17], 512);
		final var owner = new SourceOwner(positive(cells[16]), new String(ownerBytes, StandardCharsets.UTF_8));
		require(Arrays.equals(observationBytes, observation.bytes()) && Arrays.equals(ownerBytes, owner.operationKey().getBytes(StandardCharsets.UTF_8)),
			"RECHARGE_NATIVE_ENCODING");
		return new NativeRechargeReceipt(readCast(cells, eventKey(cells[1])), observation, owner, positive(cells[18]), nonnegative(cells[19]),
			positive(cells[20]), positive(cells[21]), scalar(cells[22]), scalar(cells[23]), scalar(cells[24]));
	}

	static List<String> rechargeEvents(String episodeId, NativeRechargeReceipt original)
	{
		final RechargeFact fact = rechargeFact(episodeId, original);
		final NativeCastTrace cast = original.cast();
		final var events = new ArrayList<>(castEvents(cast, fact.operationId(), "SUPPORT"));
		events.add(row("event", cast.eventKey(), fact.effectNanos(), "MP_TRANSFER", cast.actorObjectId(), cast.targetObjectId(), fact.operationId(),
			fact.effective().toPlainString()));
		return List.copyOf(events);
	}

	private static RechargeFact rechargeFact(String episodeId, NativeRechargeReceipt original)
	{
		require(episodeId.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,95}"), "EPISODE_ID");
		checkRecharge(original);
		final var effective = quantizeHp(original.afterMp() - original.beforeMp());
		require((original.targetWriteNanos() == 0 || effective.signum() > 0) && wireHpBounded(effective), "RECHARGE_WIRE_PRECISION");
		return new RechargeFact(original, operationId(episodeId, original.owner()), effective);
	}

	private static void checkRecharge(NativeRechargeReceipt original)
	{
		final var cast = original.cast();
		final var observation = original.observation();
		final var ally = observation.entity(cast.targetObjectId());
		final long effect = original.targetWriteNanos() == 0 ? original.handlerFinishedNanos() : original.targetWriteNanos();
		require(traceMatches(cast, cast.eventKey(), cast.instanceId(), observation, ally.objectId(), ally.epoch(), cast.skillId(), cast.skillLevel(), effect)
			&& cast.baselineSkillId() == cast.skillId() && cast.baselineSkillLevel() == cast.skillLevel()
			&& learned(observation, cast.skillId(), cast.skillLevel()), "RECHARGE_CAST_OR_BASELINE");
		final var maxMp = ally.fact(Field.MAX_MP);
		require(maxMp.known() && maxMp.value() > 0 && original.afterMp() <= maxMp.value() + HEAL_WRITE_TOLERANCE, "RECHARGE_NATIVE_MP_WRITE");
	}

	static String aggroRow(String episodeId, NpcAggroCast cast)
	{
		final String payload = aggroPayload(cast);
		return row("aggro", aggroOperation(episodeId, payload)) + "\t" + payload;
	}

	static List<String> aggroEvents(String episodeId, NpcAggroCast cast)
	{
		final String operation = aggroOperation(episodeId, aggroPayload(cast));
		return cast.switches().stream().map(change -> row("event", aggroEffectKey(operation, change), change.observedNanos(), "AGGRO",
			cast.actor(), change.target().objectId(), operation, 1)).toList();
	}

	private static String aggroPayload(NpcAggroCast cast)
	{
		final var payload = new StringBuilder(row(cast.actor(), cast.epoch(), cast.instance(), cast.skillId(), cast.skillLevel(), cast.hitStartedNanos(),
			cast.paidNanos(), cast.mpBefore(), cast.mpAfter()));
		for (var change : cast.switches())
		{
			payload.append('\t').append(row(change.target().objectId(), change.target().spawnGeneration(), change.formerAlly(), change.formerEpoch(), change.observedNanos()));
		}
		return payload.toString();
	}

	private static String aggroOperation(String episodeId, String payload)
	{
		return TacticalObservationV3.hash(("TASK131_AGGRO_CAST_V1\n" + episodeId + '\n' + payload).getBytes(StandardCharsets.UTF_8));
	}

	private static String aggroEffectKey(String operation, NpcAggroSwitch change)
	{
		return TacticalObservationV3.hash(("TASK131_AGGRO_EFFECT_V1\n" + operation + '\n' + change.target().objectId() + ':' + change.target().spawnGeneration()).getBytes(StandardCharsets.UTF_8));
	}

	private static NpcAggroCast readAggro(String[] row)
	{
		require(row.length >= 16 && row.length <= 91 && (row.length - 11) % 5 == 0, "AGGRO_CAST_SHAPE");
		final int instance = nonnegativeInt(row[4]);
		final var switches = new ArrayList<NpcAggroSwitch>();
		for (int column = 11; column < row.length; column += 5)
		{
			final var target = new Target(object(row[column]), instance, positive(row[column + 1]));
			switches.add(new NpcAggroSwitch(target, object(row[column + 2]), positive(row[column + 3]), positive(row[column + 4])));
		}
		return new NpcAggroCast(object(row[2]), positive(row[3]), instance, object(row[5]), object(row[6]), positive(row[7]), positive(row[8]),
			scalar(row[9]), scalar(row[10]), switches);
	}

	static String castRow(NativeCastTrace trace)
	{
		return castRow("cast", trace);
	}

	private static String castRow(String kind, NativeCastTrace trace)
	{
		return row(kind, trace.eventKey(), trace.actorObjectId(), trace.actorEpoch(), trace.targetObjectId(), trace.targetEpoch(), trace.instanceId(),
			trace.skillId(), trace.skillLevel(), trace.baselineSkillId(), trace.baselineSkillLevel(), trace.decisionNanos(), trace.admittedNanos(),
			trace.executedNanos(), trace.observationSha());
	}

	private static NativeCastTrace readCast(String[] row, String key)
	{
		return new NativeCastTrace(key, object(row[2]), positive(row[3]), object(row[4]), positive(row[5]), nonnegativeInt(row[6]),
			object(row[7]), object(row[8]), object(row[9]), object(row[10]), positive(row[11]), positive(row[12]), positive(row[13]), eventKey(row[14]));
	}

	static String castStageKey(String eventKey, String stage)
	{
		require(stage.equals("DECISION") || stage.equals("ADMITTED") || stage.equals("EXECUTED"), "CAST_STAGE");
		return TacticalObservationV3.hash(("TASK131_AUDIT_NATIVE_CAST_STAGE_V1\n" + eventKey(eventKey) + "\n" + stage).getBytes(StandardCharsets.UTF_8));
	}

	static String skillToken(int id, int rank)
	{
		require(id > 0 && rank > 0, "SKILL_IDENTITY");
		return "skill-" + id + "-" + rank;
	}

	static List<String> castEvents(NativeCastTrace trace, String operationId, String role)
	{
		require(role.equals("DAMAGE") || role.equals("OPPONENT") || role.equals("HEAL") || role.equals("SUPPORT"), "CAST_ROLE");
		final String operation = eventKey(operationId);
		final int actor = trace.actorObjectId(), target = trace.targetObjectId();
		final String chosen = skillToken(trace.skillId(), trace.skillLevel());
		final String baseline = skillToken(trace.baselineSkillId(), trace.baselineSkillLevel());
		final String key = trace.eventKey();
		return List.of(row("event", castStageKey(key, "DECISION"), trace.decisionNanos(), "DECISION", actor, target, operation, role, chosen, baseline),
			row("event", castStageKey(key, "ADMITTED"), trace.admittedNanos(), "ADMITTED", actor, target, operation, chosen),
			row("event", castStageKey(key, "EXECUTED"), trace.executedNanos(), "EXECUTED", actor, target, operation, chosen));
	}

	static boolean matchesCast(NativeCastTrace trace, String key, int instance, Attempt attempt, long firstEffectWrite)
	{
		final var action = attempt.selected();
		return trace != null && traceMatches(trace, key, instance, attempt.observation(), action.targetObjectId(), action.targetEpoch(),
			action.skillId(), action.skillLevel(), firstEffectWrite)
			&& trace.baselineSkillId() == attempt.baseline().skillId() && trace.baselineSkillLevel() == attempt.baseline().skillLevel();
	}

	private static boolean traceMatches(NativeCastTrace trace, String key, int instance, TacticalObservationV3 observation, int targetId,
		long targetEpoch, int skillId, int rank, long firstEffectWrite)
	{
		return trace.eventKey().equals(key) && trace.actorObjectId() == observation.actorObjectId() && trace.actorEpoch() == observation.actorEpoch()
			&& trace.targetObjectId() == targetId && trace.targetEpoch() == targetEpoch && trace.instanceId() == instance
			&& trace.skillId() == skillId && trace.skillLevel() == rank && trace.observationSha().equals(TacticalObservationV3.hash(observation.bytes()))
			&& trace.decisionNanos() >= observation.capturedNanos() && trace.admittedNanos() >= trace.decisionNanos()
			&& trace.executedNanos() >= trace.admittedNanos() && trace.executedNanos() <= firstEffectWrite;
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
			original.instanceId(), original.hpStartedNanos(), original.linkedLethalWrite(), receipt.afterHp(), observation,
			receipt.attempt().baseline().skillId(), receipt.attempt().baseline().skillLevel());
	}

	/** Canonical observation, native ranks, owner, MP debit, and two nullable exact pool writes. */
	static String pvpRow(PvpReceipt original)
	{
		final var action = original.attempt().selected();
		final var baseline = original.attempt().baseline();
		return row("pvp", original.eventKey(), ENCODER.encodeToString(original.attempt().observation().bytes()), action.targetObjectId(),
			action.targetEpoch(), action.skillId(), action.skillLevel(), original.owner().generation(),
			ENCODER.encodeToString(original.owner().operationKey().getBytes(StandardCharsets.UTF_8)), original.instanceId(), original.elapsedNanos(),
			original.mp(), original.mpFirstWrite(), original.mpLastWrite())
			+ "\t" + pool(original.hp()) + "\t" + pool(original.cp()) + "\t" + row(original.lethal(), baseline.skillId(), baseline.skillLevel());
	}

	private static Paid readPvp(String[] row, String key, String episodeId)
	{
		require(row.length == 25, "PVP_ROW_SHAPE");
		final byte[] observationBytes = bytes(row[2], 524288);
		final var observation = TacticalObservationV3.restore(observationBytes);
		require(Arrays.equals(observation.bytes(), observationBytes), "PVP_OBSERVATION_ENCODING");
		final int targetId = object(row[3]), skill = object(row[5]), rank = object(row[6]);
		final int baselineId = object(row[23]), baselineRank = object(row[24]);
		final long targetEpoch = positive(row[4]);
		final var target = observation.entity(targetId);
		require(target != null && observation.entities().size() == 2 && targetId != observation.actorObjectId() && target.role() == Role.ENEMY
			&& target.epoch() == targetEpoch && !target.fact(Field.NPC_ID).known()
			&& learned(observation, skill, rank) && learned(observation, baselineId, baselineRank), "PVP_LEARNED_IDENTITY");
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
			instance, first(hp, cp), lethal, hp == null ? 0 : hp.after(), observation, baselineId, baselineRank);
	}

	private static boolean learned(TacticalObservationV3 observation, int skillId, int rank)
	{
		return observation.self().abilities().stream().anyMatch(ability -> ability.skillId() == skillId && ability.level() == rank);
	}

	static long first(PvpPoolWrite hp, PvpPoolWrite cp)
	{
		return hp == null ? cp.firstWrite() : cp == null ? hp.firstWrite() : Math.min(hp.firstWrite(), cp.firstWrite());
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
		require(stock == null && terminal.corpseGeneration() == 0 && original.afterHp() < 0.5 && terminal.damageNanos() >= original.firstEffectWrite()
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
		final var value = quantizeHp(delta);
		require(value.signum() > 0 && wireHpBounded(value), "DAMAGE_WIRE_PRECISION");
		return value;
	}

	private static BigDecimal quantizeHp(double amount)
	{
		return BigDecimal.valueOf(amount).setScale(9, RoundingMode.HALF_EVEN).stripTrailingZeros();
	}

	private static boolean wireHpBounded(BigDecimal value)
	{
		return value.toPlainString().matches("[0-9]{1,18}(\\.[0-9]{1,9})?");
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
