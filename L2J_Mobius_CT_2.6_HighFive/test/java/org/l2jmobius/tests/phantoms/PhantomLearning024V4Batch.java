/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.l2jmobius.gameserver.phantoms.learning.*;
import static org.l2jmobius.tests.phantoms.PhantomEvaluationReplayV2.*;
import static org.l2jmobius.tests.phantoms.PhantomLearning019ActionValue.check;
import static org.l2jmobius.tests.phantoms.PhantomLearning020V4Native.*;

/** TEST-only two-scene boundary. There is no GameServer entrypoint or optimizer in the episode loop. */
public final class PhantomLearning024V4Batch
{
    static final boolean ENABLED = Boolean.getBoolean("phantom.learning.batch024");
    static Path batch(Path root) { return root.getFileName().toString().startsWith("episode") ? root.getParent().getParent() : root; }
    static boolean stop(Path root) { return Files.exists(root.resolve("stop.request")) || Files.exists(batch(root).resolve("stop.request")); }
    static Path game(Path root) { return batch(root).resolve("game"); }
    static void ownedEpisode(Path root, boolean committed) throws Exception
    {
        check(ENABLED && AUTONOMOUS022 && root.getFileName().toString().matches("episode[01]") && root.getParent().getFileName().toString().equals("episodes"), "024_EPISODE_SCOPE");
        PhantomLearning020V4Native.owned(batch(root), committed);
        var plan = object(batch(root).resolve("batch024.json"));
        int index = Integer.parseInt(root.getFileName().toString().substring(7));
        var e = map(list(plan.get("episodes")).get(index));
        check(sha(root.resolve("preflight020.json")).equals(string(e, "preflightSha")) && Files.readString(root.resolve("owner.txt")).equals(Files.readString(batch(root).resolve("owner.txt"))), "024_FROZEN_EPISODE_BINDING");
    }
    static void preflight(Path root, Map<String, Object> frozen) throws Exception
    {
        check(ENABLED && AUTONOMOUS022, "024_EXPLICIT_TEST_REQUIRED");
        var first = map(list(frozen.get("candidates")).getFirst());
        var reservation = object(root.resolve("reservation022.json"));
        check(string(reservation, "batchSchema").equals("NATIVE_BATCH024_V1") && number(reservation, "episodeCount") == 2, "024_BATCH_RESERVATION_REQUIRED");
        var episodes = new ArrayList<Object>(); long cursor = number(first, "cursor022");
        for (int index = 0; index < 2; index++)
        {
            Path ep = root.resolve("episodes/episode" + index); Files.createDirectories(ep);
            for (String name : List.of("owner.txt", "candidate.bin", "TRAIN_CONFIG.ini")) { Files.copy(root.resolve(name), ep.resolve(name)); }
            var candidate = PhantomLearning022V4Trainer.curriculum(first, cursor);
            candidate.put("policy022", SPLIT022.equals("TRAIN") && index == 0 ? "COVERAGE_TEST_V1" : "BASELINE_V1");
            cursor = number(candidate, "nextCursor022");
            String nonce = TacticalObservationV3.hash((string(reservation, "nonce") + "/episode" + index).getBytes(StandardCharsets.UTF_8)).substring(0, 32);
            String id = TacticalObservationV3.hash((nonce + "/0").getBytes(StandardCharsets.UTF_8));
            var per = new LinkedHashMap<>(frozen); per.put("nonce", nonce); per.put("candidates", List.of(candidate));
            write(ep.resolve("preflight020.json"), json(per));
            episodes.add(Map.of("ordinal", index, "episodeId", id, "nonce", nonce, "cursor", number(candidate, "cursor022"), "nextCursor", cursor, "policy", candidate.get("policy022"), "preflightSha", sha(ep.resolve("preflight020.json"))));
        }
        write(root.resolve("batch024.json"), json(Map.of("schema", "NATIVE_BATCH024_V1", "modelSha", PARENT, "parentHead", string(reservation, "head"), "split", SPLIT022,
            "reservationSha", sha(root.resolve("reservation022.json")), "episodes", episodes, "nextCursor", cursor)));
    }
    static void run(Path root, PhantomBackgroundSuite.ProductionAuthorityFixture authority, LearningPolicy policy, ActionValueV4 model) throws Exception
    {
        var plan = object(root.resolve("batch024.json")); String frozenSha = sha(root.resolve("batch024.json"));
        check(frozenSha.equals(string(object(root.resolve("preflight020.json")), "batchSha024")), "024_PRE_BOOT_MANIFEST_PIN");
        var seals = new ArrayList<Object>(); var actors = new HashSet<Long>(); var targets = new HashSet<Long>();
        for (int index = 0; index < 2; index++)
        {
            check(!stop(root) && sha(root.resolve("batch024.json")).equals(frozenSha) && model.sha256().equals(PARENT), "024_BATCH_DRIFT_OR_STOP");
            Path ep = root.resolve("episodes/episode" + index); ownedEpisode(ep, false);
            Files.copy(root.resolve("boot-reserved020.json"), ep.resolve("boot-reserved020.json"));
            Files.copy(root.resolve("native-entered020.marker"), ep.resolve("native-entered020.marker"));
            PhantomLearning020V4Native.resetEpisode024();
            var receipts = new ConcurrentLinkedQueue<TacticalReceiptV3>(); var decisions = new ConcurrentLinkedQueue<TacticalPolicyV3.ActionValueDecision>();
            var candidate = map(list(object(ep.resolve("preflight020.json")).get("candidates")).getFirst());
            var settled = encounter(ep, candidate, 0, authority, policy, model, receipts, decisions);
            check(settled.id().equals(string(map(list(plan.get("episodes")).get(index)), "episodeId")), "024_EPISODE_ID_CHANGED");
            var released = object(ep.resolve("released024.json"));
            check(Boolean.TRUE.equals(released.get("closed")) && actors.add(number(released, "actor")) && targets.add(number(released, "target")), "024_REUSED_OR_UNRELEASED_NATIVE_OBJECT");
            publishEpisode(ep, PARENT, settled, receipts, decisions);
            seals.add(Map.of("episodeId", settled.id(), "completeSha", sha(ep.resolve("native021.complete.json")), "releaseSha", sha(ep.resolve("released024.json"))));
            // A real death is preserved and requires review; never silently launch a replacement scene.
            check(settled.observedDeaths() == 0 || index == 1, "024_FIRST_EPISODE_DEATH_SAFETY_PAUSE");
        }
        write(root.resolve("batch-complete024.json"), json(Map.of("schema", "BATCH_COMPLETE024_V1", "batchSha", frozenSha, "pid", ProcessHandle.current().pid(),
            "processStart", ProcessHandle.current().info().startInstant().orElseThrow().toString(), "gsMainInvocations", 1, "modelSha", PARENT, "episodes", seals)));
        long paid = 0;
        for (int i = 0; i < 2; i++) { paid += number(object(root.resolve("episodes/episode" + i + "/native020.json")), "nativePaid"); }
        // Compatibility acknowledgement for the unchanged owned-process supervisor.
        // This is explicitly a batch summary, never an episode or an optimizer input.
        write(root.resolve("native020.json"), json(Map.of("schema", "BATCH_OWNER_ACK024_V1", "modelSha", PARENT, "MODEL_CONSULTED", true,
            "NATIVE_EXECUTED", true, "EFFECT_PAID", true, "nativePaid", paid, "settled", true, "episodeSha", sha(root.resolve("batch-complete024.json")), "trainingCredit", 0)));
        write(root.resolve("native021.complete.json"), json(Map.of("schema", "BATCH_OWNER_ACK_COMPLETE024_V1", "batchCompleteSha", sha(root.resolve("batch-complete024.json")), "nativeSha", sha(root.resolve("native020.json")))));
    }
    static List<ActionFeedbackV4> samples(Path root, boolean committed) throws Exception
    {
        PhantomLearning020V4Native.owned(root, committed);
        var seal = object(root.resolve("batch-complete024.json")); var plan = object(root.resolve("batch024.json"));
        validatePlan(plan);
        Path original = root.getParent().getParent().resolve("reservations/000000.json");
        check(sha(original).equals(sha(root.resolve("reservation022.json"))) && sha(original).equals(string(object(root.resolve("preflight020.json")), "reservationSha022"))
            && string(plan, "parentHead").equals(string(object(original), "head")), "024_ORIGINAL_RESERVATION_CHANGED");
        check(string(object(root.resolve("preflight020.json")), "batchSha024").equals(sha(root.resolve("batch024.json"))) && string(seal, "schema").equals("BATCH_COMPLETE024_V1") && string(seal, "batchSha").equals(sha(root.resolve("batch024.json"))) && number(seal, "gsMainInvocations") == 1
            && string(seal, "modelSha").equals(PARENT) && string(plan, "modelSha").equals(PARENT) && string(plan, "split").equals(SPLIT022)
            && string(plan, "reservationSha").equals(sha(root.resolve("reservation022.json"))) && list(seal.get("episodes")).size() == 2 && list(plan.get("episodes")).size() == 2, "024_COMPLETE_FROZEN_BATCH_REQUIRED");
        var keys = new HashSet<String>(); var ids = new HashSet<String>(); var actors = new HashSet<Long>(); var targets = new HashSet<Long>(); var all = new ArrayList<ActionFeedbackV4>();
        for (int index = 0; index < 2; index++)
        {
            Path ep = root.resolve("episodes/episode" + index); var e = map(list(seal.get("episodes")).get(index)); var p = map(list(plan.get("episodes")).get(index));
            check(ids.add(string(e, "episodeId")) && string(e, "episodeId").equals(string(p, "episodeId")) && number(p, "ordinal") == index
                && string(e, "completeSha").equals(sha(ep.resolve("native021.complete.json"))) && string(e, "releaseSha").equals(sha(ep.resolve("released024.json"))), "024_SEALED_EPISODE_SOURCE");
            var released = object(ep.resolve("released024.json"));
            check(Boolean.TRUE.equals(released.get("closed")) && number(released, "pid") == number(seal, "pid") && actors.add(number(released, "actor")) && targets.add(number(released, "target")), "024_RELEASE_AND_FRESH_OBJECTS_REQUIRED");
            var complete = object(ep.resolve("native021.complete.json"));
            check(string(complete, "nativeSha").equals(sha(ep.resolve("native020.json"))) && string(complete, "manifestSha").equals(sha(ep.resolve("manifest021.json"))), "024_ORIGINAL_COMPLETE_REQUIRED");
            check(sha(ep.resolve("boot-reserved020.json")).equals(sha(root.resolve("boot-reserved020.json"))) && sha(ep.resolve("native-entered020.marker")).equals(sha(root.resolve("native-entered020.marker"))), "024_ONE_BOOT_BINDING");
            var settled = episode(object(ep.resolve("episode020.json")), number(object(ep.resolve("cut020.json")), "startedNanos"));
            check(settled.actors().size() == 1 && settled.actors().getFirst().objectId() == number(released, "actor") && settled.actors().getFirst().epoch() == number(released, "actorEpoch"), "024_RELEASE_ACTOR_BINDING");
            for (var sample : PhantomLearning020V4Native.samples(ep, committed))
            {
                var selected = sample.row().receipt().attempt().selected();
                check(selected.targetObjectId() == number(released, "target") && selected.targetEpoch() == number(released, "targetEpoch"), "024_RELEASE_TARGET_BINDING");
                check(sample.episodeId().equals(string(e, "episodeId")) && keys.add(sample.row().eventKey()), "024_CROSS_EPISODE_OR_DUPLICATE_EVENT"); all.add(sample);
            }
        }
        check(!all.isEmpty() && all.size() <= 256, "024_BATCH_RECEIPT_BOUND"); return List.copyOf(all);
    }
    public static void main(String[] args) throws Exception
    {
        if (args.length == 1 && args[0].equals("contracts")) { contracts(); return; }
        check(args.length >= 3 && ENABLED, "024_EXPLICIT_OFFLINE_ARGUMENTS"); Path ledger = Path.of(args[1]), root = Path.of(args[2]);
        requireClosed(root, args[0].equals("partial"));
        if (args[0].equals("partial")) { partial(root); return; }
        var all = samples(root, true); String nativeSha = sha(root.resolve("batch-complete024.json"));
        switch (args[0])
        {
            case "verify" -> System.out.println(json(Map.of("episodes", 2, "paid", all.size(), "nativeSha", nativeSha, "GS", 0, "SQL", 0)));
            case "history" -> {
                var current = ActionContinuationV4.load(ledger);
                for (var sample : all) { check(sample.fingerprint().equals(current.events().get(sample.row().eventKey())) && sample.row().split().equals(current.episodes().get(sample.episodeId())), "024_HISTORY_MEMBERSHIP"); }
                System.out.println(json(Map.of("episodes", 2, "authenticatedEvents", all.size(), "quality", "QUALITY_UNPROVEN")));
            }
            case "prepare" -> {
                check(args.length == 4, "024_EXPECTED_HEAD_REQUIRED");
                check(args[3].equals(string(object(root.resolve("batch024.json")), "parentHead")) && ActionContinuationV4.load(ledger).model().sha256().equals(PARENT), "024_FROZEN_FIT_PARENT");
                System.out.println(json(PhantomLearning022V4Trainer.summary(ActionContinuationV4.prepare(ledger, args[3], all, nativeSha))));
            }
            case "verify-prepared" -> { check(args.length == 5 && args[3].equals(string(object(root.resolve("batch024.json")), "parentHead")), "024_PREPARED_ARGUMENTS"); ActionContinuationV4.verifyPrepared(ledger, args[3], args[4], all, nativeSha); }
            case "replay" -> {
                var before = ActionContinuationV4.load(ledger); var after = ActionContinuationV4.prepare(ledger, before.head(), all, nativeSha);
                check(after.head().equals(before.head()) && after.globalUpdates() == before.globalUpdates() && ActionLearningV4.fit(before.model(), all).sha256().equals(before.model().sha256()), "024_REPLAY_CREDIT");
                for (int i = 0; i < 2; i++) {
                    var one = PhantomLearning020V4Native.samples(root.resolve("episodes/episode" + i), true);
                    check(ActionLearningV4.fit(before.model(), one).sha256().equals(before.model().sha256()) && ActionContinuationV4.prepare(ledger, before.head(), one, nativeSha).head().equals(before.head()), "024_EPISODE_REPLAY_CREDIT");
                }
                System.out.println(json(Map.of("replayAdditionalUpdates", 0, "episodes", 2, "paid", all.size())));
            }
            default -> throw new IllegalArgumentException("024_UNKNOWN_COMMAND");
        }
    }
    static void requireClosed(Path root, boolean partial) throws Exception
    {
        var safety = object(root.resolve("safety020.json"));
        var original = object(root.getParent().getParent().resolve("reservations/000000.json"));
        check(string(original, "configSha").equalsIgnoreCase(sha(root.getParent().getParent().resolve("campaign022.json"))), "024_FROZEN_CONFIG_CHANGED");
        check(string(safety, "test").equals("OFF") && number(safety, "ownedProcesses") == 0 && Boolean.TRUE.equals(safety.get("portsFree")), "024_OFFLINE_ONLY");
        var identity = object(root.resolve("native-process.json")); String nonce = string(identity, "nonce");
        check(nonce.matches("[a-f0-9]{32}") && ProcessHandle.of(number(identity, "pid")).isEmpty(), "024_NATIVE_PROCESS_NOT_PROVEN_ABSENT");
        var exit = object(root.resolve("attempts/" + nonce + "/exit.json"));
        check(string(exit, "proof").equals("OWN_HELD_HANDLE_EXIT") && (number(exit, "exitCode") == 0 || partial && number(exit, "exitCode") == 1) && string(exit, "installationOwner").equals(Files.readString(root.resolve("owner.txt"))), "024_ORIGINAL_EXIT_REQUIRED");
        for (String key : List.of("pid", "created", "image", "imageHash", "sid", "nonce", "kind", "commandHash", "configPath", "configHash")) { check(Objects.equals(identity.get(key), exit.get(key)), "024_NATIVE_EXIT_IDENTITY"); }
        if (!partial) { check(number(object(root.resolve("batch-complete024.json")), "pid") == number(identity, "pid"), "024_BATCH_NATIVE_PID"); }
        var sql = object(root.resolve("sql/owned-process.json"));
        check(ProcessHandle.of(number(sql, "pid")).isEmpty(), "024_SQL_NOT_PROVEN_ABSENT");
    }
    static void partial(Path root) throws Exception
    {
        check(!Files.exists(root.resolve("batch-complete024.json")), "024_COMPLETE_BATCH_IS_NOT_PARTIAL");
        var plan = object(root.resolve("batch024.json")); validatePlan(plan);
        check(string(object(root.resolve("preflight020.json")), "batchSha024").equals(sha(root.resolve("batch024.json")))
            && sha(root.getParent().getParent().resolve("reservations/000000.json")).equals(string(plan, "reservationSha")), "024_PARTIAL_ORIGINAL_RESERVATION");
        int episodes = 0, paid = 0, deaths = 0;
        long pid = number(object(root.resolve("native-process.json")), "pid");
        for (int i = 0; i < 2; i++) {
            Path ep = root.resolve("episodes/episode" + i);
            if (!Files.exists(ep.resolve("native021.complete.json"))) { continue; }
            var complete = object(ep.resolve("native021.complete.json"));
            check(string(complete, "nativeSha").equals(sha(ep.resolve("native020.json"))) && string(complete, "manifestSha").equals(sha(ep.resolve("manifest021.json")))
                && sha(ep.resolve("boot-reserved020.json")).equals(sha(root.resolve("boot-reserved020.json"))) && sha(ep.resolve("native-entered020.marker")).equals(sha(root.resolve("native-entered020.marker"))), "024_PARTIAL_ORIGINAL_SEAL");
            var release = object(ep.resolve("released024.json")); check(number(release, "pid") == pid && Boolean.TRUE.equals(release.get("closed")), "024_PARTIAL_RELEASE");
            var original = PhantomLearning020V4Native.samples(ep, true);
            var settled = episode(object(ep.resolve("episode020.json")), number(object(ep.resolve("cut020.json")), "startedNanos"));
            check(settled.id().equals(string(map(list(plan.get("episodes")).get(i)), "episodeId")), "024_PARTIAL_EPISODE_ID");
            episodes++; paid += original.size(); deaths += settled.observedDeaths();
        }
        System.out.println(json(Map.of("schema", "PARTIAL_VERIFIED024_V1", "sealedEpisodes", episodes, "paid", paid, "deaths", deaths, "trainingCredit", 0, "GS", 0, "SQL", 0)));
    }
    static void validatePlan(Map<String, Object> plan)
    {
        check(string(plan, "schema").equals("NATIVE_BATCH024_V1") && string(plan, "modelSha").equals(PARENT) && string(plan, "split").equals(SPLIT022)
            && list(plan.get("episodes")).size() == 2, "024_FROZEN_PLAN_SCHEMA");
        var ids = new HashSet<String>(); var nonces = new HashSet<String>(); long next = -1;
        for (int i = 0; i < 2; i++) {
            var e = map(list(plan.get("episodes")).get(i)); String nonce = string(e, "nonce");
            check(number(e, "ordinal") == i && nonce.matches("[a-f0-9]{32}") && nonces.add(nonce) && ids.add(string(e, "episodeId"))
                && string(e, "episodeId").equals(TacticalObservationV3.hash((nonce + "/0").getBytes(StandardCharsets.UTF_8)))
                && number(e, "cursor") >= 0 && (next < 0 || number(e, "cursor") == next) && number(e, "nextCursor") > number(e, "cursor"), "024_ORDER_ID_NONCE_CURSOR");
            check(string(e, "policy").equals(SPLIT022.equals("TRAIN") && i == 0 ? "COVERAGE_TEST_V1" : "BASELINE_V1"), "024_FROZEN_POLICY_SPLIT");
            next = number(e, "nextCursor");
        }
        check(number(plan, "nextCursor") == next, "024_FINAL_CURSOR");
    }
    static void contracts() throws Exception
    {
        var episodes = new ArrayList<Object>();
        for (int i = 0; i < 2; i++) {
            String nonce = (i == 0 ? "a" : "b").repeat(32);
            episodes.add(Map.of("ordinal", (long)i, "nonce", nonce, "episodeId", TacticalObservationV3.hash((nonce + "/0").getBytes(StandardCharsets.UTF_8)), "cursor", 4L + i * 2, "nextCursor", 6L + i * 2, "policy", SPLIT022.equals("TRAIN") && i == 0 ? "COVERAGE_TEST_V1" : "BASELINE_V1"));
        }
        var plan = new LinkedHashMap<String, Object>(Map.of("schema", "NATIVE_BATCH024_V1", "modelSha", PARENT, "split", SPLIT022, "episodes", episodes, "nextCursor", 8L));
        validatePlan(plan);
        for (String key : List.of("schema", "modelSha", "split", "nextCursor")) {
            var bad = new LinkedHashMap<>(plan); bad.put(key, key.equals("nextCursor") ? 9 : "FORGED");
            rejects024(() -> validatePlan(bad));
        }
        var bad = new LinkedHashMap<>(plan); bad.put("episodes", List.of(episodes.get(1), episodes.get(0))); rejects024(() -> validatePlan(bad));
        bad.put("episodes", List.of(episodes.get(0), episodes.get(0))); rejects024(() -> validatePlan(bad));
        bad.put("episodes", List.of(episodes.get(0))); rejects024(() -> validatePlan(bad));
        Path empty = Files.createTempDirectory("incomplete024-");
        boolean refused = false; try { samples(empty, true); } catch (Exception | AssertionError expected) { refused = true; }
        check(refused, "024_INCOMPLETE_BATCH_ACCEPTED"); Files.delete(empty);
        System.out.println("LEARNING024_MANIFEST_CONTRACTS_PASS negativeCases=8 GS=0 SQL=0 nativeEvidence=false");
    }
    static void rejects024(Runnable action)
    {
        boolean refused = false; try { action.run(); } catch (RuntimeException | AssertionError expected) { refused = true; }
        check(refused, "024_INVALID_PLAN_ACCEPTED");
    }
}
