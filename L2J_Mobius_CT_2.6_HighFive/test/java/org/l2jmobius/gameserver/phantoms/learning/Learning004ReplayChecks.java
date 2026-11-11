/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.learning;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3.*;

/** Synthetic codec/sampler contracts only. These rows are never inserted as native observations. */
public final class Learning004ReplayChecks
{
	public static void main(String[] args)
	{
		legacyImport();
		final var sampler = new TacticalReplayV3(Set.of(), "004.");
		final var repeat = new TacticalReplayV3(Set.of(), "004.");
		for (int i = 0; i < 5000; i++)
		{
			final String split = i % 5 == 0 ? "AUDIT" : i % 5 == 1 ? "TUNING" : "TRAIN";
			final var row = sample(i + 1000, split, "004.context-" + i);
			sampler.offer(row); repeat.offer(row);
		}
		final var rows = sampler.rows();
		check(rows.size() <= 4096 && TacticalStoreV3.sufficient(rows), "historical ledger did not yield bounded usable replay");
		check(rows.stream().map(TacticalStoreV3.Row::eventKey).toList().equals(repeat.rows().stream().map(TacticalStoreV3.Row::eventKey).toList()), "replay selection nondeterministic");
		check(rows.stream().anyMatch(row -> row.actorProfile() >= 5990), "fresh experience missing");
		check(rows.stream().anyMatch(row -> row.actorProfile() < 4000), "older replay memory missing");
		final var used = sample(8000, "AUDIT", "004.used");
		final var filter = new TacticalReplayV3(Set.of(used.cohort()), "004.");
		filter.offer(used); filter.offer(sample(8001, "TRAIN", "003.old-prefix"));
		check(filter.rows().isEmpty(), "consumed audit or unrelated prefix leaked into new fit");
		final byte[] bytes = TacticalStoreV3.pack(rows);
		check(TacticalStoreV3.unpack(bytes).size() == rows.size(), "frozen sample keys not recoverable");
		final var duplicate = new ArrayList<>(rows); duplicate.add(rows.getFirst());
		reject(() -> TacticalStoreV3.validateDataset(duplicate), "duplicate event credited");
		System.out.println("LEARNING004_REPLAY historical=5000 selected=" + rows.size() + " freshAndOld=PASS deterministic=PASS usedAudit=EXCLUDED DB_CONNECTIONS=0 origin=SYNTHETIC");
	}

	private static void legacyImport()
	{
		try
		{
			final var archive = java.nio.file.Path.of(System.getProperty("phantom.module.root", "L2J_Mobius_CT_2.6_HighFive"))
				.resolve("docs/phantoms/tasks/PHANTOM-LEARNING-003/artifacts/accepted-model-2/model-2.zip");
			final var legacy = TacticalStoreV3.readLegacy003(archive);
			check(legacy.model().sha256().equals("6b91706463f4e24f89f1c4f8be3a82d9ee58eadb07a4254c79449665e3fa97dc"), "import changed accepted model");
			check(legacy.rows().size() == 120 && legacy.datasetSha().equals("bf2039c39f18e4137593129deccf1781b5f4f73bf95d9b0eb7b252465079be6e"), "historical dataset bytes changed");
			final var quality = TacticalTrainerV3.qualityGroups(legacy.rows(), legacy.model(), null);
			check(!quality.qualified() && quality.metrics().contains("HEAL/97/PARTY") && quality.metrics().contains("REGRESSION"), "Cardinal regression hidden by aggregate003 improvement");
			check(quality.metrics().contains("DAMAGE/94/SOLO") && quality.metrics().contains("DAMAGE/94/PARTY"), "missing core family/scope omitted from COMBAT admission");
			final var unsupported = TacticalTrainerV3.qualityGroups(List.of(sample(9000, "TRAIN", "004.unlearned")), legacy.model(), null);
			check(!unsupported.qualified() && unsupported.metrics().contains("INSUFFICIENT_EVIDENCE"), "unsupported class claimed learned");
			final var optimizer = TacticalModelV3.startOptimization(legacy.model(), 183);
			final var parent = new TacticalStoreV3.Optimization(2, "MOMENTS_INITIALIZED", optimizer.sha256(), optimizer);
			reject(() -> TacticalStoreV3.requireSuccessor(parent, optimizer), "unchanged optimizer received new completion credit");
			final var forged = new TacticalStoreV3.Optimization(2, "MOMENTS_INITIALIZED", "0".repeat(64), optimizer);
			reject(() -> TacticalStoreV3.requireSuccessor(forged, optimizer), "forged parent accepted");
			final var example = sample(9000, "TRAIN", "004.lineage-math").example();
			final var trained = TacticalModelV3.continueTraining(optimizer, java.util.Collections.nCopies(16, example), java.util.Collections.nCopies(8, example)).continuation();
			final var child = new TacticalStoreV3.Optimization(3, "a".repeat(32), trained.sha256(), trained);
			TacticalStoreV3.validateOptimizationLink(parent, child, 2, parent.sha(), parent.state().model().sha256(), "a".repeat(32));
			reject(() -> TacticalStoreV3.validateOptimizationLink(parent, child, 1, parent.sha(), parent.state().model().sha256(), "a".repeat(32)), "changed parent model ID accepted");
			reject(() -> TacticalStoreV3.validateOptimizationLink(parent, child, 2, "0".repeat(64), parent.state().model().sha256(), "a".repeat(32)), "changed parent optimizer SHA accepted");
			reject(() -> TacticalStoreV3.validateOptimizationLink(parent, child, 2, parent.sha(), "0".repeat(64), "a".repeat(32)), "changed parent weights accepted");
			reject(() -> TacticalStoreV3.validateOptimizationLink(parent, child, 2, parent.sha(), parent.state().model().sha256(), "b".repeat(32)), "optimizer attached to different committed run");
			System.out.println("LEARNING004_LEGACY archive=AUTHENTICATED model2=PRESERVED dataset=120 oldAudit=HISTORICAL newNativeCredit=0");
		}
		catch (java.io.IOException failure) { throw new AssertionError(failure); }
	}

	private static TacticalStoreV3.Row sample(long actor, String split, String context)
	{
		final var facts = Map.of(Field.HP, Fact.known(500), Field.MAX_HP, Fact.known(2000), Field.LEVEL, Fact.known(77));
		final var self = new Entity(Role.SELF, (int) actor, 100, 0, actor % 2 == 0 ? 97 : 105, facts, List.of(), List.of(), List.of(), List.of());
		final var first = new Entity(Role.ALLY, 200, 101, 0, 90, facts, List.of(), List.of(), List.of(), List.of());
		final var second = new Entity(Role.ALLY, 201, 102, 0, 94, facts, List.of(), List.of(), List.of(), List.of());
		final var observation = new TacticalObservationV3(1, 10, actor, (int) actor, 100, "party", List.of(self, first, second));
		final var a = new LegalActionV3(LegalActionV3.Family.HEAL, "PartyTactics", actor, (int) actor, 100, 1, 200, 101, "party", 1401, 11, 80, 1000, 600, false, true);
		final var b = new LegalActionV3(LegalActionV3.Family.HEAL, "PartyTactics", actor, (int) actor, 100, 1, 201, 102, "party", 1401, 11, 80, 1000, 600, false, true);
		final var attempt = new HealEvidenceV3.Attempt(observation, List.of(a, b), a, b, 0, _ -> { });
		final String key = TacticalObservationV3.hash(Long.toString(actor).getBytes(java.nio.charset.StandardCharsets.UTF_8));
		final var receipt = new HealEvidenceV3.Receipt(key, attempt, 500, 1000, 500, 80, 1000000000, .16f);
		return new TacticalStoreV3.Row(split, context, receipt);
	}
	private static void reject(Runnable action, String message)
	{
		try { action.run(); } catch (IllegalArgumentException expected) { return; }
		throw new AssertionError(message);
	}
	private static void check(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}
