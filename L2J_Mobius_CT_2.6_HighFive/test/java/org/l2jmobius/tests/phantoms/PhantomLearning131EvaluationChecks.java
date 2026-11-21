/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.l2jmobius.gameserver.phantoms.learning.HealEvidenceV3.SourceOwner;
import org.l2jmobius.gameserver.phantoms.learning.LearningCastEvidence.PvpPoolWrite;
import org.l2jmobius.gameserver.phantoms.learning.LearningCastEvidence.PvpReceipt;
import org.l2jmobius.gameserver.phantoms.learning.LegalActionV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalReceiptV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalStoreV3;
import org.l2jmobius.gameserver.phantoms.learning.TerminalRewardReceiptV1.TimedAction;
import org.l2jmobius.tests.phantoms.PhantomLearning131AuditDamage.DuelDeath;
import org.l2jmobius.tests.phantoms.PhantomLearning131AuditOriginals.Fact;
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
		paidProjectionRejectsSubstitution();
		paidPveOriginalRoundTrip();
		paidPvpCpOriginalRoundTrip();
		untrustedEvidenceIsNeverAResult();
		System.out.println("TRAINING_EVALUATION131_MATH_CHECKS_PASS");
	}

	private static void paidPvpCpOriginalRoundTrip()
	{
		final String episode = "3".repeat(64), event = "4".repeat(64);
		final var self = new TacticalObservationV3.Entity(TacticalObservationV3.Role.SELF, 42, 7, 0, 0, Map.of(),
			List.of(), List.of(), List.of(new TacticalObservationV3.Ability(1230, 4, 20, 4, 500, 0, 300, true)), List.of());
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
		final var fact = PhantomLearning131AuditOriginals.decode(Collections.singletonList(wire), episode).get(event);
		check(fact != null && fact.hp().signum() == 0 && fact.cp().compareTo(new BigDecimal("40")) == 0 && fact.atNanos() == 1050
			&& fact.actorEpoch() == 7 && fact.targetEpoch() == 91 && fact.lethalNanos() == 0, "a CP-only cast must stay CP-only after replay");

		final var lethal = new PvpReceipt(event, attempt, new PvpPoolWrite(100, 0, 1040, 1050), null, 4, 1010, 1020, 70, 0, true,
			new SourceOwner(5, "Combat/1/5/1"));
		final String[] lethalRow = PhantomLearning131AuditOriginals.pvpRow(lethal).split("\t", -1);
		try
		{
			PhantomLearning131AuditOriginals.decode(Collections.singletonList(lethalRow), episode);
			throw new AssertionError("A known lethal cast was allowed to lose its stock death witness");
		}
		catch (IllegalArgumentException refused)
		{
			final String[] stock = PhantomLearning131AuditOriginals.deathRow(event, new DuelDeath(42, 102, 91, 2, 1080),
				new DuelDeath(42, 102, 91, 2, 1060)).split("\t", -1);
			final var confirmed = PhantomLearning131AuditOriginals.decode(List.of(lethalRow, stock), episode).get(event);
			check(confirmed != null && confirmed.lethalNanos() == 1080 && confirmed.lastNativeWriteNanos() == 1050,
				"The two death witnesses must survive replay alongside the separate native pool write");
		}
	}

	private static void paidPveOriginalRoundTrip()
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
		final var fact = PhantomLearning131AuditOriginals.decode(Collections.singletonList(wire), episode).get(event);
		check(fact != null && fact.hp().compareTo(new BigDecimal("60")) == 0 && fact.cp().signum() == 0 && fact.atNanos() == 1050
			&& fact.skillId() == 1230 && fact.skillLevel() == 4 && fact.targetEpoch() == 91 && fact.lethalNanos() == 0,
			"the canonical paid PvE original must recover exactly the witnessed action");
	}

	private static void paidProjectionRejectsSubstitution()
	{
		final String damage = "a".repeat(64), death = "b".repeat(64), operation = "c".repeat(64);
		final var fact = new Fact(damage, operation, 42, 7, 102, 91, 1230, 4, 110, new BigDecimal("60"), new BigDecimal("20"),
			118, death, true, 95, 110);
		final var roster = Map.of(42, 7L, 102, 91L, 103, 92L);
		final var originals = Map.of(damage, fact);
		final String[] executed = {"event", "d".repeat(64), "100", "EXECUTED", "42", "102", operation, "skill-1230-4"};
		final String[] damageRow = {"event", damage, "110", "DAMAGE", "42", "102", operation, "60", "20"};
		final String[] deathRow = {"event", death, "118", "DEATH", "42", "102", operation};
		final var complete = List.of(executed, damageRow, deathRow);
		PhantomLearning131Evaluation.verifyPaidProjection(complete, originals, roster, 90, 120, true);

		final String[] otherVictim = damageRow.clone();
		otherVictim[5] = "103";
		final String[] inflated = damageRow.clone();
		inflated[7] = "61";
		final String[] otherSkill = executed.clone();
		otherSkill[7] = "skill-1230-3";
		rejectsPaidProjection(List.of(executed, otherVictim, deathRow), originals, roster);
		rejectsPaidProjection(List.of(executed, inflated, deathRow), originals, roster);
		rejectsPaidProjection(List.of(otherSkill, damageRow, deathRow), originals, roster);
		rejectsPaidProjection(List.of(executed, damageRow, deathRow), originals, Map.of(42, 7L, 102, 92L));
		rejectsPaidProjection(List.of(executed, damageRow), originals, roster);
		rejectsPaidProjection(List.of(executed, damageRow, damageRow, deathRow), originals, roster);
		rejectsPaidProjection(List.of(executed, damageRow, deathRow), Map.of(), roster);
		for (var outOfScope : List.of(
			new Fact(damage, operation, 42, 7, 102, 91, 1230, 4, 110, fact.hp(), fact.cp(), 118, death, true, 89, 110),
			new Fact(damage, operation, 42, 7, 102, 91, 1230, 4, 110, fact.hp(), fact.cp(), 118, death, true, 95, 121),
			new Fact(damage, operation, 42, 7, 102, 91, 1230, 4, 110, fact.hp(), fact.cp(), 118, death, false, 95, 110)))
		{
			rejectsPaidProjection(complete, Map.of(damage, outOfScope), roster);
		}
	}

	private static void rejectsPaidProjection(List<String[]> events, Map<String, Fact> originals, Map<Integer, Long> roster)
	{
		try { PhantomLearning131Evaluation.verifyPaidProjection(events, originals, roster, 90, 120, true); }
		catch (IllegalArgumentException refused) { return; }
		throw new AssertionError("An unattributed paid projection entered the evaluator");
	}

	private static void duelDeathJoin()
	{
		final var paid = new DuelDeath(42, 102, 91, 2, 115);
		final var stock = new DuelDeath(42, 102, 91, 2, 118);
		check(PhantomLearning131AuditDamage.joinedDeathNanos(stock, paid, 110) == 118,
			"a native death becomes available only at the later exact witness");
		for (var mismatched : List.of(new DuelDeath(42, 102, 91, 3, 118), new DuelDeath(42, 102, 92, 2, 118), new DuelDeath(42, 102, 91, 2, 109)))
		{
			try
			{
				PhantomLearning131AuditDamage.joinedDeathNanos(mismatched, paid, 110);
				throw new AssertionError("An unrelated corpse, actor epoch or earlier notification cannot prove a death");
			}
			catch (IllegalStateException refused)
			{
				check(refused.getMessage().equals("AUDIT_DAMAGE131_UNBOUND_NATIVE_DEATH"), "a false death join is refused");
			}
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
			Files.writeString(boot, meta("schema", "NATIVE_BOOT131_V1", "bank", "bank-1", "manifestSha", manifestSha, "bootId", "boot-1",
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

	private static void checkFrozenSchedule(Path manifest, Path temporary) throws Exception
	{
		final var protocol = manifest.resolveSibling(manifest.getFileName() + ".protocol.tsv");
		final var plan = PhantomLearning131Evaluation.frozenSchedule(protocol, digest(protocol));
		check(plan.size() == 128 && plan.getFirst().block().index() == 0 && plan.getFirst().slot().equals(new Slot(Arm.C, Side.LEFT))
			&& plan.getLast().block().stratum() == Stratum.GROUP_PVP && plan.getLast().block().index() == 7 && plan.getLast().position() == 3,
			"producer can read all planned arms in stratum, block-index and position order before collecting evidence");
		checkSoloDamageBinding(plan.getFirst(), temporary);
		checkDuelDamageBinding(plan.stream().filter(slot -> slot.block().stratum() == Stratum.DUEL_PVP).findFirst().orElseThrow(), temporary);

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
		Files.writeString(manifest, meta("schema", "FROZEN_EVALUATION131_V1", "bank", "bank-1", "protocolSha", digest(protocol), "splitSha", digest(splits),
			"sourceSha", "a".repeat(40), "jarSha", "b".repeat(64), "configSha", "c".repeat(64), "policySha", "d".repeat(64), "evaluatorSha", TEST_EVALUATOR_SHA,
			"modelSchema", "a".repeat(64), "candidateSha", "3".repeat(64), "referenceSha", "4".repeat(64), "opponent.B0", "5".repeat(64),
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
