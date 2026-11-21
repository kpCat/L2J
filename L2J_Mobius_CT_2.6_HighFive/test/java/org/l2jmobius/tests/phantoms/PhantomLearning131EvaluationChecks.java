/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.l2jmobius.gameserver.phantoms.learning.ActionContinuationV4;
import org.l2jmobius.gameserver.phantoms.learning.ActionExperimentReceiptV4.Intent;
import org.l2jmobius.gameserver.phantoms.learning.ActionExplorationV4;
import org.l2jmobius.gameserver.phantoms.learning.ActionValueV4;
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence.ItemDebit;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.phantoms.learning.HealEvidenceV3.NativeHealReceipt;
import org.l2jmobius.gameserver.phantoms.learning.HealEvidenceV3.SourceOwner;
import org.l2jmobius.gameserver.phantoms.learning.LearningCastEvidence.NativeCastTrace;
import org.l2jmobius.gameserver.phantoms.learning.LearningCastEvidence.PvpPoolWrite;
import org.l2jmobius.gameserver.phantoms.learning.LearningCastEvidence.PvpReceipt;
import org.l2jmobius.gameserver.phantoms.learning.LearningCastEvidence.NativeTerminal;
import org.l2jmobius.gameserver.phantoms.learning.LegalActionV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalReceiptV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalStoreV3;
import org.l2jmobius.gameserver.phantoms.learning.TerminalRewardReceiptV1.TimedAction;
import org.l2jmobius.tests.phantoms.PhantomLearning131AuditDamage.DuelDeath;
import org.l2jmobius.tests.phantoms.PhantomLearning131AuditOriginals.Fact;
import org.l2jmobius.tests.phantoms.PhantomLearning131Evaluation.EpisodeWindow;
import org.l2jmobius.tests.phantoms.TrainingEvaluation131.Arm;
import org.l2jmobius.tests.phantoms.TrainingEvaluation131.BlockPlan;
import org.l2jmobius.tests.phantoms.TrainingEvaluation131.Episode;
import org.l2jmobius.tests.phantoms.TrainingEvaluation131.Objective;
import org.l2jmobius.tests.phantoms.TrainingEvaluation131.Outcome;
import org.l2jmobius.tests.phantoms.TrainingEvaluation131.Scene;
import org.l2jmobius.tests.phantoms.TrainingEvaluation131.Side;
import org.l2jmobius.tests.phantoms.TrainingEvaluation131.Slot;
import org.l2jmobius.tests.phantoms.TrainingEvaluation131.Stratum;

/** DB-free checks for the TASK131 score and matched-block statistics. */
public final class PhantomLearning131EvaluationChecks
{
	private static final String TEST_EVALUATOR_SHA = "e".repeat(64);
	private record Fixtures(List<BlockPlan> plans, List<Episode> episodes) { }

	public static void main(String[] args) throws Exception
	{
		scoreObjectives();
		matchedBootstrapAndStockControl();
		incompleteAndMisattributedBlocks();
		duelDeathJoin();
		combatAndSettlementHaveSeparateClocks();
		paidProjectionRejectsSubstitution();
		paidPveOriginalRoundTrip();
		paidPvpCpOriginalRoundTrip();
		paidItemsRequireNativeLedger();
		paidHealingRequiresItsNativeEffect();
		frozenAuditPolicyRequiresCommittedAction();
		untrustedEvidenceIsNeverAResult();
		System.out.println("TRAINING_EVALUATION131_MATH_CHECKS_PASS");
	}

	private static void combatAndSettlementHaveSeparateClocks()
	{
		final var bounds = new EpisodeWindow(100, 100, 140);
		check(bounds.combatEnd() == 200 && bounds.settledEnd() == 240, "The combat cutoff must survive the drain");
		check(bounds.offset("DEATH", 195) == 95 && bounds.offset("KILL_CREDIT", 224) == 124 && bounds.offset("CC_END", 227) == 127,
			"Already earned credit and closure can arrive after the native outcome was committed");
		expectOutsideCombat(bounds, "EXECUTED", 201);
		expectOutsideCombat(bounds, "DEATH", 201);
		expectOutsideCombat(bounds, "KILL_CREDIT", 241);
	}

	private static void expectOutsideCombat(EpisodeWindow bounds, String kind, long nanos)
	{
		try { bounds.offset(kind, nanos); }
		catch (IllegalArgumentException refused) { return; }
		throw new AssertionError("The episode accepted out-of-window " + kind);
	}

	private static void paidPvpCpOriginalRoundTrip()
	{
		final String episode = "3".repeat(64), event = "4".repeat(64);
		final var self = new TacticalObservationV3.Entity(TacticalObservationV3.Role.SELF, 42, 7, 0, 0, Map.of(),
			List.of(), List.of(), List.of(new TacticalObservationV3.Ability(1230, 4, 20, 4, 500, 0, 300, true),
				new TacticalObservationV3.Ability(1148, 4, 20, 4, 500, 0, 300, true)), List.of());
		final var enemy = new TacticalObservationV3.Entity(TacticalObservationV3.Role.ENEMY, 102, 91, 0, 0,
			Map.of(TacticalObservationV3.Field.MAX_CP, TacticalObservationV3.Fact.known(400),
				TacticalObservationV3.Field.MAX_HP, TacticalObservationV3.Fact.known(800)), List.of(), List.of(), List.of(), List.of());
		final var observation = new TacticalObservationV3(1, 1000, 1, 42, 7, "", List.of(self, enemy));
		final var selected = new LegalActionV3(LegalActionV3.Family.DAMAGE, "Combat", 1, 42, 7, 1, 102, 91, "", 1230, 4, 4, 500, 300, false, true);
		final var alternate = new LegalActionV3(LegalActionV3.Family.DAMAGE, "Combat", 1, 42, 7, 1, 102, 91, "", 1148, 4, 4, 500, 300, false, true);
		final var attempt = new TacticalReceiptV3.Attempt(observation, List.of(selected, alternate), selected, alternate, 0);
		final var paid = new PvpReceipt(event, attempt, null, new PvpPoolWrite(100, 60, 1040, 1050), 4, 1010, 1020, 50, 0, false,
			new SourceOwner(5, "Combat/1/5/1"));
		final String[] wire = PhantomLearning131AuditOriginals.pvpRow(paid).split("\t", -1);
		final var trace = new NativeCastTrace(event, 42, 7, 102, 91, 0, 1230, 4, 1148, 4, 1002, 1005, 1010,
			TacticalObservationV3.hash(observation.bytes()));
		final String[] cast = PhantomLearning131AuditOriginals.castRow(trace).split("\t", -1);
		final var fact = PhantomLearning131AuditOriginals.decode(List.of(wire, cast), episode).get(event);
		check(fact != null && fact.hp().signum() == 0 && fact.cp().compareTo(new BigDecimal("40")) == 0 && fact.atNanos() == 1050
			&& fact.actorEpoch() == 7 && fact.targetEpoch() == 91 && fact.lethalNanos() == 0 && fact.trace().equals(trace),
			"a CP-only cast must retain its native stages after replay");
		final String[] otherLearnedBaseline = cast.clone();
		otherLearnedBaseline[9] = "1230";
		rejectsPaidOriginals(List.of(wire, otherLearnedBaseline), episode);

		final var lethal = new PvpReceipt(event, attempt, new PvpPoolWrite(100, 0, 1040, 1050), null, 4, 1010, 1020, 70, 0, true,
			new SourceOwner(5, "Combat/1/5/1"));
		final String[] lethalRow = PhantomLearning131AuditOriginals.pvpRow(lethal).split("\t", -1);
		try
		{
			PhantomLearning131AuditOriginals.decode(List.of(lethalRow, cast), episode);
			throw new AssertionError("A known lethal cast was allowed to lose its stock death witness");
		}
		catch (IllegalArgumentException refused)
		{
			final String[] terminal = PhantomLearning131AuditOriginals.terminalRow(new NativeTerminal(event, 42, 7, 102, 91, 0, 2, 1060, 0, 0, 1050)).split("\t", -1);
			final String[] stock = PhantomLearning131AuditOriginals.stockRow(event, new DuelDeath(42, 102, 91, 2, 1080)).split("\t", -1);
			final var confirmed = PhantomLearning131AuditOriginals.decode(List.of(lethalRow, terminal, stock, cast), episode).get(event);
			check(confirmed != null && confirmed.lethalNanos() == 1060 && confirmed.lastWitnessNanos() == 1080 && confirmed.lastNativeWriteNanos() == 1050,
				"The late PvP callback must preserve the earlier verified death");
			final String[] damage = {"event", event, "1050", "DAMAGE", "42", "102", confirmed.operationId(), "100", "0"};
			final String[] death = {"event", confirmed.lethalKey(), "1060", "DEATH", "42", "102", confirmed.operationId()};
			PhantomLearning131Evaluation.verifyPaidProjection(paidEvents(confirmed, damage, death), Map.of(event, confirmed),
				Map.of(42, 7L, 102, 91L), new EpisodeWindow(1000, 70, 90), true);
		}
	}

	private static void paidPveOriginalRoundTrip() throws Exception
	{
		final String episode = "1".repeat(64), event = "2".repeat(64);
		final var self = new TacticalObservationV3.Entity(TacticalObservationV3.Role.SELF, 42, 7, 0, 0, Map.of(),
			List.of(), List.of(), List.of(), List.of());
		final var enemy = new TacticalObservationV3.Entity(TacticalObservationV3.Role.ENEMY, 102, 91, 0, 0,
			Map.of(TacticalObservationV3.Field.MAX_HP, TacticalObservationV3.Fact.known(800),
				TacticalObservationV3.Field.NPC_ID, TacticalObservationV3.Fact.known(20003)), List.of(), List.of(), List.of(), List.of());
		final var observation = new TacticalObservationV3(1, 1000, 1, 42, 7, "", List.of(self, enemy));
		final var selected = new LegalActionV3(LegalActionV3.Family.DAMAGE, "Combat", 1, 42, 7, 1, 102, 91, "", 1230, 4, 4, 500, 300, false, true);
		final var alternate = new LegalActionV3(LegalActionV3.Family.DAMAGE, "Combat", 1, 42, 7, 1, 102, 91, "", 1148, 4, 4, 500, 300, false, true);
		final var attempt = new TacticalReceiptV3.Attempt(observation, List.of(selected, alternate), selected, alternate, 0);
		final var receipt = new TacticalReceiptV3(event, attempt, 800, 740, 60, 4, 50, TacticalReceiptV3.reward(60, 800, 4, 50),
			new SourceOwner(5, "Combat/1/5/1"));
		final var original = new TimedAction(new TacticalStoreV3.Row("AUDIT", "task131|AUDIT|" + episode, receipt), 0, 1040, 1050,
			1010, 1020, false);
		final String[] wire = PhantomLearning131AuditOriginals.pveRow(original).split("\t", -1);
		final var trace = new NativeCastTrace(event, 42, 7, 102, 91, 0, 1230, 4, 1148, 4, 1002, 1005, 1010,
			TacticalObservationV3.hash(observation.bytes()));
		final String[] cast = PhantomLearning131AuditOriginals.castRow(trace).split("\t", -1);
		final var fact = PhantomLearning131AuditOriginals.decode(List.of(wire, cast), episode).get(event);
		check(fact != null && fact.hp().compareTo(new BigDecimal("60")) == 0 && fact.cp().signum() == 0 && fact.atNanos() == 1050
			&& fact.skillId() == 1230 && fact.skillLevel() == 4 && fact.targetEpoch() == 91 && fact.lethalNanos() == 0 && fact.trace().equals(trace),
			"the canonical paid PvE original must recover exactly the witnessed action");
		rejectsPaidOriginals(Collections.singletonList(wire), episode);
		rejectsPaidOriginals(List.of(wire, cast, cast), episode);
		final String[] otherObservation = cast.clone();
		otherObservation[14] = "9".repeat(64);
		rejectsPaidOriginals(List.of(wire, otherObservation), episode);
		final String[] impossibleExecution = cast.clone();
		impossibleExecution[13] = "1041";
		rejectsPaidOriginals(List.of(wire, impossibleExecution), episode);

		final var fatal = new TacticalReceiptV3(event, attempt, 800, 0, 800, 4, 60, TacticalReceiptV3.reward(800, 800, 4, 60),
			new SourceOwner(5, "Combat/1/5/1"));
		final var beforeRewardReturn = new TimedAction(new TacticalStoreV3.Row("AUDIT", "task131|AUDIT|" + episode, fatal),
			0, 1040, 1075, 1010, 1020, false);
		final String[] fatalRow = PhantomLearning131AuditOriginals.pveRow(beforeRewardReturn).split("\t", -1);
		final String[] terminal = PhantomLearning131AuditOriginals.terminalRow(new NativeTerminal(event, 42, 7, 102, 91, 0, 0, 1050, 1060, 2, 1045)).split("\t", -1);
		final var npc = PhantomLearning131AuditOriginals.decode(List.of(fatalRow, terminal, cast), episode).get(event);
		check(npc != null && npc.atNanos() == 1045 && npc.lethalNanos() == 1050 && npc.killCreditNanos() == 1060 && npc.lastNativeWriteNanos() == 1075,
			"NPC damage, terminal transition, credit and wrapping cast clocks must survive separately");
		final String[] damage = {"event", event, "1045", "DAMAGE", "42", "102", npc.operationId(), "800", "0"};
		final String[] death = {"event", npc.lethalKey(), "1050", "DEATH", "42", "102", npc.operationId()};
		final String[] credit = {"event", npc.killCreditKey(), "1060", "KILL_CREDIT", "42", "102", npc.operationId()};
		final var events = paidEvents(npc, damage, death, credit);
		PhantomLearning131Evaluation.verifyPaidProjection(events, Map.of(event, npc),
			Map.of(42, 7L, 102, 91L), new EpisodeWindow(1000, 54, 90), false);
		final var item = new ItemDebit(42, 7, 601, 1463, 5, 3, null, ItemProcessType.NONE, 1, 1001);
		final var eventRows = new ArrayList<>(events.stream().map(row -> String.join("\t", row)).toList());
		eventRows.add(PhantomLearning131AuditOriginals.paymentRow(episode, item));
		closedDamageSeal(episode, eventRows, List.of(String.join("\t", fatalRow), String.join("\t", terminal), String.join("\t", cast),
			PhantomLearning131AuditOriginals.itemWindow(1000, 1089, 1), PhantomLearning131AuditOriginals.itemRow(episode, item)));
	}

	private static void closedDamageSeal(String episode, List<String> events, List<String> originals) throws Exception
	{
		final Path reports = Path.of(System.getProperty("phantom.test.reports", ".")).toRealPath();
		final Path scratch = Files.createTempDirectory(reports, "task131-closed-damage-");
		try
		{
			final Path directory = Files.createDirectories(scratch.resolve(".phantom-local/logs/TASK131/episodes").resolve(episode));
			final String metadata = meta("schema", "NATIVE_BINDING131_V1", "manifestSha", "a".repeat(64), "bootId", "boot-01", "blockId", "solo-01",
				"episodeId", episode, "attempt", "0", "position", "0", "arm", "C", "side", "LEFT", "sceneId", "solo", "fixtureSha", "b".repeat(64),
				"opponentSha", "c".repeat(64), "policySha", "d".repeat(64), "modelSha", "e".repeat(64), "startNanos", "1000");
			final Path binding = Files.writeString(directory.resolve("binding131.tsv"), metadata + "actor\t42\t7\tLEFT\tDAMAGE\nactor\t102\t91\tRIGHT\tOPPONENT\n");
			final String bindingSha = digest(binding);
			try
			{
				PhantomLearning131AuditDamage.sealClosedDamage(binding, episode, bindingSha, false, 1049, 1090, events, originals);
				throw new AssertionError("A late death must prevent publishing completed evidence");
			}
			catch (IllegalArgumentException refused)
			{
				check(refused.getMessage().equals("EVAL131_PAID_ORIGIN_OR_NATIVE_WINDOW"), "the combat cutoff must govern paid completion");
			}
			final var unbound = new ArrayList<>(events);
			final String[] orphan = events.getFirst().split("\t", -1);
			orphan[6] = "f".repeat(64);
			unbound.add(String.join("\t", orphan));
			try
			{
				PhantomLearning131AuditDamage.sealClosedDamage(binding, episode, bindingSha, false, 1054, 1090, unbound, originals);
				throw new AssertionError("Unattributed actions cannot enter the damage-only seal");
			}
			catch (IllegalArgumentException refused)
			{
				check(refused.getMessage().equals("EVAL131_UNPROVEN_NATIVE_EVENT"), "only operations with paid originals may be sealed here");
			}
			final var unsupported = new ArrayList<>(events.stream().map(row -> row.split("\t", -1)).toList());
			final String[] unprovedHeal = unsupported.get(3).clone();
			unprovedHeal[1] = "f".repeat(64);
			unprovedHeal[3] = "HEAL";
			unprovedHeal[5] = unprovedHeal[4];
			unsupported.add(unprovedHeal);
			final var bundle = PhantomLearning131AuditOriginals.decodeAuditRows(originals.stream().map(row -> row.split("\t", -1)).toList(), episode);
			try
			{
				PhantomLearning131Evaluation.verifyNativeProjection(unsupported, bundle, Map.of(42, 7L, 102, 91L), new EpisodeWindow(1000, 54, 90), false);
				throw new AssertionError("An event-only heal must never contribute to a bank result");
			}
			catch (IllegalArgumentException refused)
			{
				check(refused.getMessage().equals("EVAL131_UNPROVEN_NATIVE_EVENT"), "all scored effects require a typed native source");
			}
			final Path seal = directory.resolve("complete131.tsv");
			check(Files.notExists(seal) && Files.notExists(directory.resolve("events131.tsv")), "a refused episode never writes originals or a seal");
			final String sealed = PhantomLearning131AuditDamage.sealClosedDamage(binding, episode, bindingSha, false, 1054, 1090, events.reversed(), originals);
			PhantomLearning131Evaluation.verifySealedDamage(binding, episode, sealed, false);
			check(digest(seal).equals(sealed) && digest(binding).equals(bindingSha) && Files.notExists(directory.resolve("complete131.pending"))
				&& Files.readString(seal).contains("meta\tcombatElapsedNanos\t54\n") && Files.readString(seal).contains("meta\tsettledElapsedNanos\t90\n"),
				"the published seal pins a complete sorted projection and both native clocks");
			try
			{
				PhantomLearning131AuditDamage.sealClosedDamage(binding, episode, bindingSha, false, 1054, 1090, events, originals);
				throw new AssertionError("A sealed episode must never be published again");
			}
			catch (java.nio.file.FileAlreadyExistsException refused)
			{
				check(digest(seal).equals(sealed), "a repeated publication preserves the original seal");
			}
			final Path paid = directory.resolve("paid131.tsv");
			Files.writeString(paid, Files.readString(paid).replace("meta\tschema\tNATIVE_PAID131_V5", "meta\tschema\tNATIVE_PAID131_V4"));
			try
			{
				PhantomLearning131Evaluation.verifySealedDamage(binding, episode, sealed, false);
				throw new AssertionError("Replacing the original after publication must break independent readback");
			}
			catch (IllegalArgumentException refused)
			{
				check(refused.getMessage().equals("EVAL131_SCHEMA_OR_FIELDS"), "the reader independently checks the paid originals");
			}
		}
		finally
		{
			try (var paths = Files.walk(scratch))
			{
				for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) { Files.delete(path); }
			}
		}
	}

	private static void paidItemsRequireNativeLedger()
	{
		final String episode = "9".repeat(64);
		final var first = new ItemDebit(42, 7, 601, 1463, 20, 18, null, ItemProcessType.NONE, 1, 1010);
		final var second = new ItemDebit(42, 7, 601, 1463, 18, 0, null, null, 2, 1040);
		final String[] header = PhantomLearning131AuditOriginals.itemWindow(1005, 1090, 2).split("\t", -1);
		final String[] original1 = PhantomLearning131AuditOriginals.itemRow(episode, first).split("\t", -1);
		final String[] original2 = PhantomLearning131AuditOriginals.itemRow(episode, second).split("\t", -1);
		final var ledger = PhantomLearning131AuditOriginals.decodeAuditRows(List.of(header, original1, original2), episode).items();
		final var events = List.of(PhantomLearning131AuditOriginals.paymentRow(episode, first).split("\t", -1),
			PhantomLearning131AuditOriginals.paymentRow(episode, second).split("\t", -1));
		final var window = new EpisodeWindow(1000, 60, 100);
		final var roster = Map.of(42, 7L);
		PhantomLearning131Evaluation.verifyPaymentProjection(events, ledger, roster, window);
		check(ledger.debits().get(events.getFirst()[1]).before() - ledger.debits().get(events.getFirst()[1]).after() == 2
			&& ledger.debits().get(events.getLast()[1]).after() == 0, "partial consumption and final stack removal retain their quantities without a cast");
		rejectsPayments(changedField(events, 0, 8, "1"), ledger, roster, window);
		rejectsPayments(events.subList(0, 1), ledger, roster, window);
		rejectsPayments(events, ledger, Map.of(42, 8L), window);
		rejectsPayments(events, ledger, roster, new EpisodeWindow(1000, 38, 100));
		rejectsItemLedger(List.of(header, original2), episode);
		final var refilled = new ItemDebit(42, 7, 601, 1463, 19, 0, null, null, 2, 1040);
		rejectsItemLedger(List.of(header, original1, PhantomLearning131AuditOriginals.itemRow(episode, refilled).split("\t", -1)), episode);
		final var emptyWindow = PhantomLearning131AuditOriginals.itemWindow(1005, 1090, 0).split("\t", -1);
		final var empty = PhantomLearning131AuditOriginals.decodeAuditRows(Collections.singletonList(emptyWindow), episode).items();
		PhantomLearning131Evaluation.verifyPaymentProjection(List.of(), empty, roster, window);
		rejectsItemLedger(List.of(), episode);
	}

	private static void paidHealingRequiresItsNativeEffect()
	{
		final String episode = "7".repeat(64), key = "8".repeat(64);
		final var self = new TacticalObservationV3.Entity(TacticalObservationV3.Role.SELF, 42, 7, 0, 0, Map.of(), List.of(), List.of(),
			List.of(new TacticalObservationV3.Ability(1011, 4, 10, 4, 500, 0, 300, true), new TacticalObservationV3.Ability(1015, 5, 10, 4, 500, 0, 300, true)), List.of());
		final var ally = new TacticalObservationV3.Entity(TacticalObservationV3.Role.ALLY, 103, 92, 0, 0,
			Map.of(TacticalObservationV3.Field.MAX_HP, TacticalObservationV3.Fact.known(800)), List.of(), List.of(), List.of(), List.of());
		final var observation = new TacticalObservationV3(1, 1000, 1, 42, 7, "42:42@7,103@92", List.of(self, ally));
		final var trace = new NativeCastTrace(key, 42, 7, 103, 92, 3, 1011, 4, 1015, 5, 1002, 1006, 1010, TacticalObservationV3.hash(observation.bytes()));
		final var owner = new SourceOwner(12, "support-12");
		final String[] items = PhantomLearning131AuditOriginals.itemWindow(1000, 1068, 0).split("\t", -1);
		final var roster = Map.of(42, 7L, 103, 92L);
		final var window = new EpisodeWindow(1000, 50, 70);
		final var apply = new NativeHealReceipt(trace, observation, owner, 1013, 1016, 1003, 1011, 720, 800, 150, 80, 4);
		final String[] wire = PhantomLearning131AuditOriginals.healRow(apply).split("\t", -1);
		final var originals = PhantomLearning131AuditOriginals.decodeAuditRows(List.of(items, wire), episode);
		final var events = PhantomLearning131AuditOriginals.healEvents(episode, apply).stream().map(row -> row.split("\t", -1)).toList();
		PhantomLearning131Evaluation.verifyNativeProjection(events, originals, roster, window, false);
		final var heal = originals.heals().get(key);
		check(heal.effective().compareTo(new BigDecimal("80")) == 0 && heal.overheal().compareTo(new BigDecimal("70")) == 0
			&& heal.original().paidMp() == 4, "native healing, clamped excess and paid MP survive replay");
		rejectsHealProjection(changedField(events, 3, 8, "71"), originals, roster, window);
		rejectsHealProjection(changedField(events, 1, 5, "42"), originals, roster, window);
		rejectsHealProjection(events, originals, Map.of(42, 7L, 103, 93L), window);
		rejectsHealProjection(events, originals, roster, new EpisodeWindow(1000, 14, 70));
		refuse(() -> new NativeHealReceipt(trace, observation, owner, 1013, 1016, 1003, 1011, 720, 790, 150, 80, 4),
			"a mismatched native HP write must not produce an exact heal receipt");
		final var clamped = new NativeHealReceipt(trace, observation, owner, 1013, 0, 1003, 1011, 0, 0, 200, 0, 4);
		final String[] noWrite = PhantomLearning131AuditOriginals.healRow(clamped).split("\t", -1);
		final var clampedOriginals = PhantomLearning131AuditOriginals.decodeAuditRows(List.of(items, noWrite), episode);
		PhantomLearning131Evaluation.verifyNativeProjection(PhantomLearning131AuditOriginals.healEvents(episode, clamped).stream().map(row -> row.split("\t", -1)).toList(),
			clampedOriginals, roster, window, false);
		check(clampedOriginals.heals().get(key).effective().signum() == 0
			&& clampedOriginals.heals().get(key).overheal().compareTo(new BigDecimal("200")) == 0, "a fully clamped paid heal must earn no useful HP");
	}

	private static void rejectsHealProjection(List<String[]> events, PhantomLearning131AuditOriginals.AuditRows originals, Map<Integer, Long> roster, EpisodeWindow window)
	{
		try { PhantomLearning131Evaluation.verifyNativeProjection(events, originals, roster, window, false); }
		catch (IllegalArgumentException refused) { return; }
		throw new AssertionError("An unproven heal entered the AUDIT result");
	}

	private static void rejectsItemLedger(List<String[]> rows, String episode)
	{
		try { PhantomLearning131AuditOriginals.decodeAuditRows(rows, episode); }
		catch (IllegalArgumentException refused) { return; }
		throw new AssertionError("A missing, incomplete or discontinuous item observation entered the evaluator");
	}

	private static void rejectsPayments(List<String[]> events, PhantomLearning131AuditOriginals.ItemLedger ledger, Map<Integer, Long> roster, EpisodeWindow window)
	{
		try { PhantomLearning131Evaluation.verifyPaymentProjection(events, ledger, roster, window); }
		catch (IllegalArgumentException refused) { return; }
		throw new AssertionError("An incomplete or substituted item payment entered the evaluator");
	}

	private static void rejectsPaidOriginals(List<String[]> rows, String episodeId)
	{
		try { PhantomLearning131AuditOriginals.decode(rows, episodeId); }
		catch (IllegalArgumentException refused) { return; }
		throw new AssertionError("An unbound native cast entered the paid originals");
	}

	private static List<String[]> paidEvents(Fact fact, String[]... effects)
	{
		final var events = new ArrayList<String[]>();
		for (String stage : PhantomLearning131AuditOriginals.castEvents(fact.trace(), fact.operationId(), "DAMAGE"))
		{
			events.add(stage.split("\t", -1));
		}
		Collections.addAll(events, effects);
		return events;
	}

	private static void paidProjectionRejectsSubstitution()
	{
		final String damage = "a".repeat(64), death = "b".repeat(64), operation = "c".repeat(64);
		final var trace = new NativeCastTrace(damage, 42, 7, 102, 91, 0, 1230, 4, 1148, 4, 96, 98, 100, "d".repeat(64));
		final var fact = new Fact(damage, operation, 42, 7, 102, 91, 1230, 4, 110, new BigDecimal("60"), new BigDecimal("20"),
			118, death, true, 95, 110).withTrace(trace);
		final var roster = Map.of(42, 7L, 102, 91L, 103, 92L);
		final var originals = Map.of(damage, fact);
		final String[] damageRow = {"event", damage, "110", "DAMAGE", "42", "102", operation, "60", "20"};
		final String[] deathRow = {"event", death, "118", "DEATH", "42", "102", operation};
		final var complete = paidEvents(fact, damageRow, deathRow);
		PhantomLearning131Evaluation.verifyPaidProjection(complete, originals, roster, new EpisodeWindow(90, 30, 30), true);

		rejectsPaidProjection(changedField(complete, 3, 5, "103"), originals, roster);
		rejectsPaidProjection(changedField(complete, 3, 7, "61"), originals, roster);
		rejectsPaidProjection(changedField(complete, 2, 7, "skill-1230-3"), originals, roster);
		rejectsPaidProjection(changedField(complete, 0, 9, "skill-1230-4"), originals, roster);
		rejectsPaidProjection(changedField(complete, 0, 7, "HEAL"), originals, roster);
		rejectsPaidProjection(changedField(complete, 1, 1, "f".repeat(64)), originals, roster);
		rejectsPaidProjection(changedField(complete, 1, 2, "99"), originals, roster);
		rejectsPaidProjection(complete.subList(1, complete.size()), originals, roster);
		rejectsPaidProjection(List.of(complete.get(0), complete.get(2), damageRow, deathRow), originals, roster);
		rejectsPaidProjection(List.of(complete.get(0), complete.get(1), complete.get(1), complete.get(2), damageRow, deathRow), originals, roster);
		rejectsPaidProjection(complete, originals, Map.of(42, 7L, 102, 92L));
		rejectsPaidProjection(paidEvents(fact, damageRow), originals, roster);
		rejectsPaidProjection(paidEvents(fact, damageRow, damageRow, deathRow), originals, roster);
		rejectsPaidProjection(complete, Map.of(), roster);
		for (var outOfScope : List.of(
			new Fact(damage, operation, 42, 7, 102, 91, 1230, 4, 110, fact.hp(), fact.cp(), 118, death, true, 89, 110),
			new Fact(damage, operation, 42, 7, 102, 91, 1230, 4, 110, fact.hp(), fact.cp(), 118, death, true, 95, 121),
			new Fact(damage, operation, 42, 7, 102, 91, 1230, 4, 110, fact.hp(), fact.cp(), 118, death, false, 95, 110)))
		{
			rejectsPaidProjection(complete, Map.of(damage, outOfScope.withTrace(trace)), roster);
		}
	}

	private static List<String[]> changedField(List<String[]> rows, int index, int column, String value)
	{
		final var changed = new ArrayList<>(rows);
		final String[] row = changed.get(index).clone();
		row[column] = value;
		changed.set(index, row);
		return changed;
	}

	private static void rejectsPaidProjection(List<String[]> events, Map<String, Fact> originals, Map<Integer, Long> roster)
	{
		try { PhantomLearning131Evaluation.verifyPaidProjection(events, originals, roster, new EpisodeWindow(90, 30, 30), true); }
		catch (IllegalArgumentException refused) { return; }
		throw new AssertionError("An unattributed paid projection entered the evaluator");
	}

	private static void duelDeathJoin()
	{
		final var terminal = new NativeTerminal("d".repeat(64), 42, 7, 102, 91, 0, 2, 115, 0, 0, 110);
		final var stock = new DuelDeath(42, 102, 91, 2, 118);
		check(PhantomLearning131AuditOriginals.matchesStock(terminal, stock), "the late callback confirms the correct native death");
		for (var mismatched : List.of(new DuelDeath(42, 102, 91, 3, 118), new DuelDeath(42, 102, 92, 2, 118), new DuelDeath(42, 102, 91, 2, 114)))
		{
			check(!PhantomLearning131AuditOriginals.matchesStock(terminal, mismatched),
				"An unrelated corpse, actor epoch or earlier notification cannot prove a death");
		}
	}

	private static void untrustedEvidenceIsNeverAResult() throws Exception
	{
		final Path reports = Path.of(System.getProperty("phantom.test.reports", ".")).toRealPath();
		final var temporary = Files.createTempDirectory(reports, "task131-evaluator-");
		try
		{
			final var evidence = Files.createDirectory(temporary.resolve("originals"));
			final var manifest = temporary.resolve("evaluation.tsv");
			final var absent = temporary.resolve("absent.tsv");
			check(PhantomLearning131Evaluation.run(manifest, evidence, absent, TEST_EVALUATOR_SHA) == 2 && Files.readString(absent).contains("UNKNOWN"),
				"an absent frozen plan must remain unknown");
			Files.writeString(manifest, "meta\tschema\tRUNNER_SUMMARY_ONLY\nmeta\tQ\tPASS\n");
			final var rejected = temporary.resolve("rejected.tsv");
			check(PhantomLearning131Evaluation.run(manifest, evidence, rejected, TEST_EVALUATOR_SHA) == 3 && Files.readString(rejected).contains("REJECTED_EVIDENCE"),
				"an invented runner summary is rejected");
			final var original = Files.writeString(evidence.resolve("original.tsv"), "owner bytes\n");
			try
			{
				PhantomLearning131Evaluation.run(manifest, evidence, original, TEST_EVALUATOR_SHA);
				throw new AssertionError("Report path cannot be an original");
			}
			catch (IllegalArgumentException expected)
			{
				check(expected.getMessage().contains("OUTPUT"), "output is refused before reading evidence");
			}
			check(Files.readString(original).equals("owner bytes\n"), "original report bytes survive an output collision");

			frozenPlan(manifest);
			checkFrozenSchedule(manifest, temporary);
			final var missingOrigin = temporary.resolve("no-origins.tsv");
			check(PhantomLearning131Evaluation.run(manifest, evidence, missingOrigin, TEST_EVALUATOR_SHA) == 2
				&& Files.readString(missingOrigin).contains("UNKNOWN"), "complete frozen plan without independent origin remains unknown");

			final String manifestSha = digest(manifest);
			final var boot = Files.createDirectories(evidence.resolve("boots/boot-1")).resolve("complete131.tsv");
			final String started = "2026-11-21T00:00:00Z";
			Files.writeString(boot, meta("schema", "NATIVE_BOOT131_V2", "bank", "bank-1", "manifestSha", manifestSha, "bootId", "boot-1",
				"pid", "131", "processStarted", started, "sourceSha", "a".repeat(40), "jarSha", "b".repeat(64), "configSha", "c".repeat(64), "policySha", "d".repeat(64)));
			Files.writeString(manifest.resolveSibling(manifest.getFileName() + ".origins.tsv"), meta("schema", "AUDIT_ORIGINS131_V1", "manifestSha", manifestSha)
				+ "boot\tboot-1\t131\t" + started + "\t" + "f".repeat(64) + "\n");
			final var substituted = temporary.resolve("substituted.tsv");
			check(PhantomLearning131Evaluation.run(manifest, evidence, substituted, TEST_EVALUATOR_SHA) == 3
				&& Files.readString(substituted).contains("OWNED_BOOT_PIN"), "original boot substitution fails against the independent origin pin");
		}
		finally
		{
			try (var paths = Files.walk(temporary))
			{
				for (var path : paths.sorted(Comparator.reverseOrder()).toList())
				{
					Files.delete(path);
				}
			}
		}
	}

	private static void frozenAuditPolicyRequiresCommittedAction() throws Exception
	{
		final Path reports = Path.of(System.getProperty("phantom.test.reports", ".")).toRealPath();
		final var root = Files.createTempDirectory(reports, "task131-audit-model-");
		try
		{
			final String episode = "7".repeat(64), scene = "8".repeat(64);
			final var model = PhantomLearning131FrozenChecks.checkpoint();
			final var state = ActionContinuationV4.initialize(root, model);
			final long started = System.nanoTime();
			final var self = new TacticalObservationV3.Entity(TacticalObservationV3.Role.SELF, 42, 7, 0, 0, Map.of(), List.of(), List.of(), List.of(), List.of());
			final var enemy = new TacticalObservationV3.Entity(TacticalObservationV3.Role.ENEMY, 102, 91, 0, 0, Map.of(), List.of(), List.of(), List.of(), List.of());
			final var observed = new TacticalObservationV3(1, started, 1, 42, 7, "", List.of(self, enemy));
			final var stock = new LegalActionV3(LegalActionV3.Family.DAMAGE, "Combat", 1, 42, 7, 1, 102, 91, "", 1000, 1, 4, 500, 300, false, true);
			final var other = new LegalActionV3(LegalActionV3.Family.DAMAGE, "Combat", 1, 42, 7, 1, 102, 91, "", 7000, 1, 4, 500, 300, false, true);
			final var plan = new ActionExplorationV4.Plan(2, "MODEL_V2", 0, 0, 0, "AUDIT", episode, scene, model.sha256(), List.of("1000:1", "7000:1"));
			final var owner = new SourceOwner(1, "Combat/1/1/1");
			final var replay = ActionExplorationV4.frozenCommitted(plan, root, state.head());
			final var decision = replay.choose(observed, List.of(stock, other), stock, true, Long.MAX_VALUE);
			final long dispatch = decision.capturedNanos() + 1;
			final var intent = new Intent(decision, owner, dispatch);
			final var encoder = Base64.getUrlEncoder().withoutPadding();
			final String[] choice = {"decision", encoder.encodeToString(decision.bytes())};
			final String[] dispatched = {"intent", encoder.encodeToString(intent.bytes())};
			final var trace = new NativeCastTrace("9".repeat(64), 42, 7, 102, 91, 0, decision.selected().skillId(), 1, 1000, 1, dispatch,
				dispatch + 1, dispatch + 2, TacticalObservationV3.hash(observed.bytes()));
			final String operation = PhantomLearning131AuditDamage.operationId(episode, owner);
			final var window = new EpisodeWindow(started, dispatch + 20 - started, dispatch + 30 - started);
			final var rows = List.of(choice, dispatched);
			PhantomLearning131Evaluation.verifyFrozenDispatch(root, state.head(), episode, scene, model.sha256(), rows, Map.of(operation, trace), window);
			try
			{
				PhantomLearning131Evaluation.verifyFrozenDispatch(root, state.head(), episode, scene, model.sha256(), Collections.singletonList(choice),
					Map.of(operation, trace), window);
				throw new AssertionError("A replayed choice without native dispatch cannot explain a paid effect");
			}
			catch (IllegalArgumentException refused) { check(refused.getMessage().equals("EVAL131_MODEL_NATIVE_DISPATCH"), "paid action needs its exact intent"); }
			final var otherTrace = new NativeCastTrace(trace.eventKey(), 42, 7, 102, 91, 0, decision.selected().equals(stock) ? other.skillId() : stock.skillId(),
				1, 1000, 1, dispatch, dispatch + 1, dispatch + 2, trace.observationSha());
			try
			{
				PhantomLearning131Evaluation.verifyFrozenDispatch(root, state.head(), episode, scene, model.sha256(), rows, Map.of(operation, otherTrace), window);
				throw new AssertionError("An unrelated skill cannot use the model's decision receipt");
			}
			catch (IllegalArgumentException refused) { check(refused.getMessage().equals("EVAL131_MODEL_NATIVE_DISPATCH"), "paid skill must match replay"); }
			try
			{
				PhantomLearning131Evaluation.verifyFrozenDispatch(root, "f".repeat(64), episode, scene, model.sha256(), rows, Map.of(operation, trace), window);
				throw new AssertionError("A cold but uncommitted archive cannot impersonate the pinned checkpoint");
			}
			catch (IllegalArgumentException refused) { check(refused.getMessage().contains("committed ancestor"), "checkpoint must be in committed lineage"); }
			check(ActionContinuationV4.load(root).head().equals(state.head()), "AUDIT verification is read-only");
		}
		finally
		{
			try (var paths = Files.walk(root))
			{
				for (var path : paths.sorted(Comparator.reverseOrder()).toList()) { Files.delete(path); }
			}
		}
	}

	private static void checkFrozenSchedule(Path manifest, Path temporary) throws Exception
	{
		final var protocol = manifest.resolveSibling(manifest.getFileName() + ".protocol.tsv");
		final var plan = PhantomLearning131Evaluation.frozenSchedule(protocol, digest(protocol));
		check(plan.size() == 128 && plan.getFirst().block().index() == 0 && plan.getFirst().slot().equals(new Slot(Arm.C, Side.LEFT))
			&& plan.getLast().block().stratum() == Stratum.GROUP_PVP && plan.getLast().block().index() == 7 && plan.getLast().position() == 3,
			"producer can read all planned arms in stratum, block-index and position order before collecting evidence");
		checkSoloDamageBinding(plan.getFirst(), temporary);
		checkDuelDamageBinding(plan.stream().filter(slot -> slot.block().stratum() == Stratum.DUEL_PVP).findFirst().orElseThrow(), temporary);
		checkHealBinding(plan.stream().filter(slot -> slot.block().stratum() == Stratum.PARTY_PVE).findFirst().orElseThrow(), temporary);
		checkHealBinding(plan.stream().filter(slot -> slot.block().stratum() == Stratum.GROUP_PVP).findFirst().orElseThrow(), temporary);

		final var lines = Files.readAllLines(protocol);
		final int firstBlock = 5;
		final var reversed = new ArrayList<>(lines);
		Collections.reverse(reversed.subList(firstBlock, reversed.size()));
		final var variant = Files.write(temporary.resolve("schedule-variant.tsv"), reversed);
		check(PhantomLearning131Evaluation.frozenSchedule(variant, digest(variant)).equals(plan), "physical row order cannot change the planned execution order");
		refuseSchedule(variant, digest(protocol), "PROTOCOL_PIN");

		final var changed = new ArrayList<>(lines);
		final String[] fields = lines.get(firstBlock).split("\t");
		fields[16] = "C:RIGHT";
		changed.set(firstBlock, String.join("\t", fields));
		Files.write(variant, changed);
		refuseSchedule(variant, digest(variant), "UNBALANCED_STARTING_SIDE");

		fields[16] = "H:LEFT";
		fields[17] = "C:LEFT";
		changed.set(firstBlock, String.join("\t", fields));
		Files.write(variant, changed);
		refuseSchedule(variant, digest(variant), "UNBALANCED_ARM_ORDER");

		changed.set(firstBlock, lines.get(firstBlock));
		final String[] second = lines.get(firstBlock + 1).split("\t");
		second[3] = "0";
		changed.set(firstBlock + 1, String.join("\t", second));
		Files.write(variant, changed);
		refuseSchedule(variant, digest(variant), "DUPLICATE_BLOCK");
	}

	private static void checkSoloDamageBinding(PhantomLearning131Evaluation.ScheduledArm slot, Path temporary) throws Exception
	{
		final Path path = temporary.resolve("binding131.tsv");
		final String metadata = meta("schema", "NATIVE_BINDING131_V1", "manifestSha", "a".repeat(64), "bootId", "00001", "blockId", slot.block().id(),
			"episodeId", "ep-01", "attempt", "0", "position", "0", "arm", "C", "side", "LEFT", "sceneId", slot.sceneId(), "fixtureSha", slot.fixtureSha(),
			"opponentSha", "b".repeat(64), "policySha", "c".repeat(64), "modelSha", "d".repeat(64), "startNanos", "100");
		Files.writeString(path, metadata + "actor\t42\t71\tLEFT\tDAMAGE\nactor\t102\t91\tRIGHT\tOPPONENT\n");
		final String trusted = digest(path);
		final var bound = PhantomLearning131Evaluation.soloDamageBinding(slot, path, trusted, 42, 102);
		check(bound.episodeId().equals("ep-01") && bound.started() == 100 && bound.actorEpoch() == 71 && bound.targetEpoch() == 91,
			"only the scheduled source and stock target epochs are made available to the damage capture");
		Files.writeString(path, metadata + "actor\t42\t71\tRIGHT\tDAMAGE\nactor\t102\t91\tLEFT\tDAMAGE\n");
		try
		{
			PhantomLearning131Evaluation.soloDamageBinding(slot, path, digest(path), 42, 102);
			throw new AssertionError("An inverted binding cannot attribute Solo damage");
		}
		catch (IllegalArgumentException refused)
		{
			check(refused.getMessage().equals("EVAL131_SOLO_DAMAGE_ROSTER"), "actor and target sides are checked independently of the binding hash");
		}
	}

	private static void checkDuelDamageBinding(PhantomLearning131Evaluation.ScheduledArm scheduled, Path temporary) throws Exception
	{
		final Path path = temporary.resolve("duel-binding131.tsv");
		final Slot slot = scheduled.slot();
		final String own = slot.side().name();
		final String opposing = (slot.side() == Side.LEFT ? Side.RIGHT : Side.LEFT).name();
		final String metadata = meta("schema", "NATIVE_BINDING131_V1", "manifestSha", "a".repeat(64), "bootId", "00002", "blockId", scheduled.block().id(),
			"episodeId", "ep-duel-01", "attempt", "0", "position", Integer.toString(scheduled.position()), "arm", slot.arm().name(), "side", own,
			"sceneId", scheduled.sceneId(), "fixtureSha", scheduled.fixtureSha(), "opponentSha", "b".repeat(64), "policySha", "c".repeat(64),
			"modelSha", slot.arm() == Arm.C || slot.arm() == Arm.R ? "d".repeat(64) : "NONE", "startNanos", "100");
		Files.writeString(path, metadata + "actor\t42\t71\t" + own + "\tDAMAGE\nactor\t102\t91\t" + opposing + "\tOPPONENT\n");
		final var binding = PhantomLearning131Evaluation.duelDamageBinding(scheduled, path, digest(path), 42, 102);
		check(binding.episodeId().equals("ep-duel-01") && binding.started() == 100 && binding.scoredEpoch() == 71 && binding.opponentEpoch() == 91,
			"both live participant epochs come from the exact scheduled duel binding");
		try
		{
			PhantomLearning131Evaluation.duelDamageBinding(scheduled, path, digest(path), 102, 42);
			throw new AssertionError("Swapping the scored and opposing world objects cannot bind the duel");
		}
		catch (IllegalArgumentException refused)
		{
			check(refused.getMessage().equals("EVAL131_DUEL_DAMAGE_ROSTER"), "duel participant sides are checked independently of the binding hash");
		}
	}

	private static void checkHealBinding(PhantomLearning131Evaluation.ScheduledArm scheduled, Path temporary) throws Exception
	{
		final Slot slot = scheduled.slot();
		final Side opposing = slot.side() == Side.LEFT ? Side.RIGHT : Side.LEFT;
		final var stratum = scheduled.block().stratum();
		final Path path = temporary.resolve("heal-" + stratum + ".tsv");
		final String episode = "heal-" + stratum;
		final var rows = new StringBuilder(meta("schema", "NATIVE_BINDING131_V1", "manifestSha", "a".repeat(64), "bootId", "00003",
			"blockId", scheduled.block().id(), "episodeId", episode, "attempt", "0", "position", Integer.toString(scheduled.position()),
			"arm", slot.arm().name(), "side", slot.side().name(), "sceneId", scheduled.sceneId(), "fixtureSha", scheduled.fixtureSha(),
			"opponentSha", "b".repeat(64), "policySha", "c".repeat(64), "modelSha", slot.arm() == Arm.C || slot.arm() == Arm.R ? "d".repeat(64) : "NONE",
			"startNanos", "100"));
		final int own = scheduled.block().scene().initialRosterSize();
		for (int index = 0; index < own; index++)
		{
			rows.append("actor\t").append(42 + index).append("\t71\t").append(slot.side()).append("\tHEAL\n");
		}
		for (int index = 0; index < scheduled.opponentSize(); index++)
		{
			rows.append("actor\t").append(102 + index).append("\t91\t").append(opposing).append("\tOPPONENT\n");
		}
		Files.writeString(path, rows);
		final var binding = PhantomLearning131Evaluation.healBinding(scheduled, path, digest(path));
		final boolean groupPvp = stratum == Stratum.GROUP_PVP;
		check(binding.episodeId().equals(episode) && binding.started() == 100 && binding.playerEpochs().get(42) == 71
			&& binding.playerEpochs().containsKey(102) == groupPvp && binding.playerEpochs().size() == own + (groupPvp ? scheduled.opponentSize() : 0),
			"Group healing binds the planned players and excludes NPC opponents");
	}

	private static void refuseSchedule(Path protocol, String sha, String reason) throws Exception
	{
		try
		{
			PhantomLearning131Evaluation.frozenSchedule(protocol, sha);
		}
		catch (IllegalArgumentException refused)
		{
			if (refused.getMessage().equals("EVAL131_" + reason)) { return; }
			throw refused;
		}
		throw new AssertionError("Accepted invalid frozen schedule: " + reason);
	}

	private static void frozenPlan(Path manifest) throws Exception
	{
		final var protocol = manifest.resolveSibling(manifest.getFileName() + ".protocol.tsv");
		final var rows = new StringBuilder(meta("schema", "AUDIT_PROTOCOL131_V1", "bank", "bank-1", "seed", "131", "frozenAt", "2026-11-20T00:00:00Z"));
		rows.append("price\t1463\t1\n");
		final String[] opponents = { "B0", "A1", "B1" };
		final int[] levels = { 52, 52, 64, 64, 76, 76, 76, 76 };
		final int[] groupAllies = { 1, 2, 3, 9 }, groupEnemies = { 2, 2, 5, 9 };
		for (BlockPlan plan : fixtures(false, false).plans())
		{
			final int index = plan.index();
			final boolean group = plan.stratum() == Stratum.GROUP_PVP;
			final int roster = group ? groupAllies[index % 4] : plan.scene().initialRosterSize();
			final int opposition = group ? groupEnemies[index % 4] : 1;
			final String roles = plan.stratum() == Stratum.PARTY_PVE ? "DAMAGE,HEAL,TANK,CONTROL,SUPPORT" : "DAMAGE";
			rows.append(String.join("\t", "block", plan.id(), plan.stratum().name(), Integer.toString(index), "scene-" + plan.id(), "9".repeat(64),
				String.format("%064x", 10 + index + 8 * plan.stratum().ordinal()), opponents[index % 3], Integer.toString(levels[index]), plan.scene().objective().name(),
				"1", Integer.toString(roster), Integer.toString(opposition), "100", "1000000000", roles));
			for (Slot slot : plan.order())
			{
				rows.append('\t').append(slot.arm()).append(':').append(slot.side());
			}
			rows.append('\n');
		}
		Files.writeString(protocol, rows);
		final var splits = manifest.resolveSibling(manifest.getFileName() + ".splits.tsv");
		Files.writeString(splits, meta("schema", "AUDIT_SPLITS131_V1", "bank", "bank-1", "protocolSha", digest(protocol))
			+ "source\tTRAIN\t" + "a".repeat(64) + "\t" + "1".repeat(64) + "\nsource\tTUNING\t" + "b".repeat(64) + "\t" + "2".repeat(64) + "\n");
		Files.writeString(manifest, meta("schema", "FROZEN_EVALUATION131_V2", "bank", "bank-1", "protocolSha", digest(protocol), "splitSha", digest(splits),
			"sourceSha", "a".repeat(40), "jarSha", "b".repeat(64), "configSha", "c".repeat(64), "policySha", "d".repeat(64), "evaluatorSha", TEST_EVALUATOR_SHA,
			"modelSchema", ActionValueV4.SCHEMA, "candidateSha", "3".repeat(64), "candidateHead", "7".repeat(64), "referenceSha", "4".repeat(64), "referenceHead", "8".repeat(64),
			"opponent.B0", "5".repeat(64),
			"opponent.A1", "4".repeat(64), "opponent.B1", "6".repeat(64), "trainStartedAt", "2026-11-20T06:00:00Z", "referenceAt", "2026-11-20T12:00:00Z",
			"selectedAt", "2026-11-20T18:00:00Z", "lockedAt", "2026-11-20T23:00:00Z"));
	}

	private static String meta(String... pairs)
	{
		final var text = new StringBuilder();
		for (int index = 0; index < pairs.length; index += 2)
		{
			text.append("meta\t").append(pairs[index]).append('\t').append(pairs[index + 1]).append('\n');
		}
		return text.toString();
	}

	private static String digest(Path path) throws Exception
	{
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
	}

	private static void scoreObjectives()
	{
		final var slot = new Slot(Arm.C, Side.LEFT);
		final var pve = new Scene(Objective.PVE_KILLS, 4, 2, 100, 100);
		final var pvp = new Scene(Objective.PVP_WIN, 1, 2, 100, 100);
		final var escape = new Scene(Objective.ESCAPE, 2, 2, 100, 100);
		near(0.4625, TrainingEvaluation131.score(pve, new Episode("b", "e", 0, slot, Outcome.TIMEOUT, 2, 1, 25, 100)), "PvE timeout retains partial kills");
		near(0.60, TrainingEvaluation131.score(pvp, new Episode("b", "e", 0, slot, Outcome.DRAW, 0, 0, 0, 20)), "PvP draw has no time bonus");
		near(0.35, TrainingEvaluation131.score(pvp, new Episode("b", "e", 0, slot, Outcome.TIMEOUT, 0, 0, 0, 100)), "PvP timeout is not a draw");
		near(0.8875, TrainingEvaluation131.score(escape, new Episode("b", "e", 0, slot, Outcome.ESCAPED, 2, 0, 50, 25)), "escape credits the declared objective");
		near(0.35, TrainingEvaluation131.score(escape, new Episode("b", "e", 0, slot, Outcome.WIN, 1, 0, 0, 10)), "a combat win does not complete the escape objective");
		refuse(() -> TrainingEvaluation131.score(pve, new Episode("b", "e", 0, slot, Outcome.WIN, 2, 0, 0, 25)), "unsubstantiated objective gain");
		refuse(() -> new Scene(Objective.PVE_KILLS, 0, 2, 100, 100), "invalid kill denominator");
		refuse(() -> new Scene(Objective.PVE_KILLS, 1, 0, 100, 100), "invalid roster denominator");
		refuse(() -> new Scene(Objective.PVP_WIN, 1, 2, 100, Double.POSITIVE_INFINITY), "nonfinite deadline");
		refuse(() -> new Scene(Objective.PVP_WIN, 1, 2, 0, 100), "invalid budget denominator");
		refuse(() -> TrainingEvaluation131.score(pvp, new Episode("b", "e", 0, slot, Outcome.LOSS, 0, 0, Double.NaN, 25)), "nonfinite resource value");
	}

	private static void matchedBootstrapAndStockControl()
	{
		final var stock = fixtures(false, false);
		final var nullResult = TrainingEvaluation131.compare(stock.plans(), stock.episodes());
		check(!nullResult.meetsNumericThresholds(), "identical stock policies cannot show improvement");
		for (var comparison : nullResult.comparisons().values())
		{
			near(0, comparison.mean(), "stock mean");
			near(0, comparison.lower(), "stock lower bound");
		}

		final var biased = fixtures(true, false);
		final var result = TrainingEvaluation131.compare(biased.plans(), biased.episodes());
		check(result.meetsNumericThresholds(), "positive matched contrast");
		final var comparison = result.comparisons().get(Arm.H);
		near(0.0625, comparison.mean(), "each of 32 blocks is one unit");
		near(0.015625, comparison.lower(), "2.5th percentile of within-stratum draws");
		near(0.25, comparison.meansByStratum().get(Stratum.SOLO_PVE), "effect belongs to solo PvE only");
		near(0, comparison.meansByStratum().get(Stratum.GROUP_PVP), "no effect in group PvP");
		for (Arm stronger : List.of(Arm.S, Arm.R))
		{
			final var rows = biased.episodes().stream().map(row -> (row.slot().arm() == stronger && row.blockId().startsWith("GROUP_PVP"))
				? new Episode(row.blockId(), row.episodeId(), row.position(), row.slot(), Outcome.WIN, 0, 0, 0, 100) : row).toList();
			final var inferior = TrainingEvaluation131.compare(biased.plans(), rows);
			near(-0.109375, inferior.comparisons().get(stronger).lower(), "lower bound for independent arm " + stronger);
			check(!inferior.meetsNumericThresholds(), "C must also be noninferior to " + stronger);
		}
		final var switched = fixtures(true, true);
		Collections.reverse(switched.plans());
		Collections.reverse(switched.episodes());
		check(result.comparisons().equals(TrainingEvaluation131.compare(switched.plans(), switched.episodes()).comparisons()),
			"relabelled sides and shuffled input cannot change paired differences");
	}

	private static void incompleteAndMisattributedBlocks()
	{
		final var fixtures = fixtures(true, false);
		refuse(() -> TrainingEvaluation131.compare(fixtures.plans().subList(0, 31), fixtures.episodes()), "missing block");
		final var rows = new ArrayList<>(fixtures.episodes());
		final var first = rows.removeFirst();
		refuse(() -> TrainingEvaluation131.compare(fixtures.plans(), rows), "missing arm");
		final var other = rows.getFirst();
		rows.add(new Episode(first.blockId(), other.episodeId(), first.position(), first.slot(), first.outcome(), first.objectiveProgress(), 0, 0, 100));
		refuse(() -> TrainingEvaluation131.compare(fixtures.plans(), rows), "duplicate physical episode");
		rows.set(rows.size() - 1, new Episode(first.blockId(), first.episodeId(), first.position(), new Slot(first.slot().arm(), Side.RIGHT), first.outcome(), first.objectiveProgress(), 0, 0, 100));
		refuse(() -> TrainingEvaluation131.compare(fixtures.plans(), rows), "side differs from frozen plan");
		rows.set(rows.size() - 1, new Episode(first.blockId(), first.episodeId(), first.position(), new Slot(other.slot().arm(), first.slot().side()), first.outcome(), first.objectiveProgress(), 0, 0, 100));
		refuse(() -> TrainingEvaluation131.compare(fixtures.plans(), rows), "arm order differs from frozen plan");
	}

	private static Fixtures fixtures(boolean improvement, boolean flipSides)
	{
		final List<BlockPlan> plans = new ArrayList<>();
		final List<Episode> episodes = new ArrayList<>();
		for (Stratum stratum : Stratum.values())
		{
			final var objective = (stratum == Stratum.SOLO_PVE || stratum == Stratum.PARTY_PVE) ? Objective.PVE_KILLS : Objective.PVP_WIN;
			final int rosterSize = (stratum == Stratum.SOLO_PVE || stratum == Stratum.DUEL_PVP) ? 1 : 2;
			final var scene = new Scene(objective, 1, rosterSize, 100, 100);
			for (int index = 0; index < 8; index++)
			{
				final String blockId = stratum.name() + "-" + index;
				final List<Slot> order = new ArrayList<>();
				for (int position = 0; position < 4; position++)
				{
					final Arm arm = Arm.values()[(position + index) % 4];
					final Side side = ((index < 4) != flipSides) ? Side.LEFT : Side.RIGHT;
					final var slot = new Slot(arm, side);
					order.add(slot);
					final boolean win = improvement && stratum == Stratum.SOLO_PVE && index < 4 && arm == Arm.C;
					episodes.add(new Episode(blockId, blockId + "-" + arm, position, slot, win ? Outcome.WIN : Outcome.LOSS, win ? 1 : 0, 0, 0, 100));
				}
				plans.add(new BlockPlan(blockId, stratum, index, scene, order));
			}
		}
		return new Fixtures(plans, episodes);
	}

	private static void near(double expected, double actual, String reason)
	{
		check(Double.isFinite(actual) && Math.abs(expected - actual) < 1e-12, reason + ": expected " + expected + " got " + actual);
	}

	private static void refuse(Runnable action, String reason)
	{
		try
		{
			action.run();
		}
		catch (IllegalArgumentException expected)
		{
			return;
		}
		throw new AssertionError("Expected refusal: " + reason);
	}

	private static void check(boolean condition, String reason)
	{
		if (!condition)
		{
			throw new AssertionError(reason);
		}
	}
}
