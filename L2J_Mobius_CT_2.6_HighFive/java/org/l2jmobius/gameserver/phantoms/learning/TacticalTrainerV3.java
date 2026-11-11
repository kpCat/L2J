/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.learning;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.l2jmobius.gameserver.phantoms.learning.TacticalStoreV3.Row;

/** Offline fixed-seed V3 fitting and honest held-out admission, only in the owned TEST installation. */
public final class TacticalTrainerV3
{
    public record Evaluation(String state, long modelVersion, String modelSha, String reason,
        long candidateVersion, String candidateSha, String metrics) { }
    private TacticalTrainerV3() { }

    public static void main(String[] args)
    {
        try
        {
            if (args.length == 4 && "apply-test".equals(args[1]))
            {
                System.out.println(json(applyTest(Path.of(args[0]),Long.parseLong(args[2]),args[3])));
                return;
            }
            if (args.length < 1 || args.length > 2) { throw new IllegalArgumentException("Expected owned installation [evaluate|report|export] or owned installation apply-test expectedId expectedSha"); }
            final Path root = Path.of(args[0]); final String action = args.length == 1 ? "evaluate" : args[1];
            switch (action)
            {
                case "evaluate" -> System.out.println(json(evaluate(root)));
                case "report" -> System.out.println(report(root));
                case "export" -> System.out.println(export(root));
                default -> throw new IllegalArgumentException("Unknown V3 offline action");
            }
        }
        catch (Exception failure)
        {
            // SQL/vendor messages may carry installation credentials: never print arbitrary exception text.
            System.err.println("TACTICAL_V3_FAILED " + failure.getClass().getSimpleName());
            System.exit(2);
        }
    }

    public static boolean qualityPasses(double modelLoss, double constantLoss, Double priorLoss)
    {
        return Double.isFinite(modelLoss) && modelLoss >= 0 && Double.isFinite(constantLoss) && constantLoss > 0
            && modelLoss < constantLoss * .99 && (priorLoss == null || Double.isFinite(priorLoss) && priorLoss > 0 && modelLoss < priorLoss * .99);
    }
    static double constantLoss(List<Row> rows)
    {
        final double mean = rows.stream().filter(row -> row.split().equals("TRAIN")).mapToDouble(row -> row.receipt().reward()).average().orElseThrow();
        return rows.stream().filter(row -> row.split().equals("AUDIT")).mapToDouble(row ->
        { final double error = row.receipt().reward() - mean; return error * error; }).average().orElseThrow();
    }
    public static Evaluation evaluate(Path root) throws IOException, SQLException
    {
        final var store = new TacticalStoreV3(root); store.initialize(); store.requireStopped();
        final var prior = store.latestAccepted();
        final var frozen = store.freeze();
        if (frozen == null)
        {
            return publish(store, new Evaluation("COLLECTING", prior == null ? 0 : prior.id(), prior == null ? "" : prior.sha(),
                "Need TRAIN>=16, TUNING>=8 and fresh AUDIT with >=2 actors and >=2 actual contexts; previous audit is never reused", 0, "", "{}"));
        }
        try
        {
            final var rows = frozen.rows();
            final var train = rows.stream().filter(row -> row.split().equals("TRAIN")).map(Row::example).toList();
            final var tuning = rows.stream().filter(row -> row.split().equals("TUNING")).map(Row::example).toList();
            final var audit = rows.stream().filter(row -> row.split().equals("AUDIT")).map(Row::example).toList();
            final long started = System.nanoTime();
            final var fit = TacticalModelV3.train(train,tuning,TacticalStoreV3.SEED);
            final long elapsed = System.nanoTime() - started;
            final double auditLoss = fit.model().loss(audit), constant = constantLoss(rows);
            final Double previous = prior == null ? null : prior.model().loss(audit);
            final long stock = rows.stream().filter(row -> row.receipt().attempt().selected().equals(row.receipt().attempt().baseline())).count();
            final String metrics = "{\"seed\":183,\"datasetSha\":" + quote(frozen.datasetSha()) + ",\"origin\":\"NATIVE_HEAL\",\"train\":" + train.size()
                + ",\"tuning\":" + tuning.size() + ",\"audit\":" + audit.size() + ",\"auditActors\":" + rows.stream().filter(row -> row.split().equals("AUDIT")).map(Row::actorProfile).distinct().count()
                + ",\"auditContexts\":" + rows.stream().filter(row -> row.split().equals("AUDIT")).map(Row::contextKey).distinct().count()
                + ",\"epochs\":" + fit.epochs() + ",\"trainingNanos\":" + elapsed + ",\"tuningInitialMse\":" + fit.initialLoss() + ",\"tuningMse\":" + fit.loss()
                + ",\"auditMse\":" + auditLoss + ",\"constantMse\":" + constant + ",\"priorVersion\":" + (prior == null ? 0 : prior.id())
                + ",\"priorMse\":" + (previous == null ? "null" : previous) + ",\"stockMatchedEvents\":" + stock + ",\"differentSelectionEvents\":" + (rows.size() - stock)
                + ",\"stockCounterfactualMse\":null,\"nativeColdDecisionChange\":null,\"limitation\":\"Only chosen actions have native outcomes; MSE does not prove tactical superiority or cold behavior\"}";
            final var candidate = store.save(frozen,fit,auditLoss,constant,previous,metrics);
            final boolean qualified = candidate.epochs() > 0 && qualityPasses(candidate.auditLoss(),candidate.constantLoss(),candidate.priorLoss());
            return publish(store, new Evaluation(qualified ? "QUALIFIED_TEST_CANDIDATE" : "DRAFT",prior == null ? 0 : prior.id(),prior == null ? "" : prior.sha(),
                qualified ? "Independent native audit passed; immutable DRAFT staged for review, explicit apply-test with exact id and SHA is required"
                    : "Trained draft retained; independent audit did not beat TRAIN constant and compatible accepted prior by 1%",
                candidate.id(),candidate.sha(),metrics));
        }
        catch (SQLException | RuntimeException | IOException failure)
        {
            store.failed(frozen);
            throw failure;
        }
    }
    private static Evaluation publish(TacticalStoreV3 store, Evaluation evaluation) throws IOException
    {
        final String json = json(evaluation) + "\n";
        write(store.installation().resolve("tactical-v3-evaluation.json"), json);
        write(store.installation().resolve("tactical-v3-report.json"), json);
        return evaluation;
    }
    /** Separate explicit apply command; fitting never calls this method or changes a source ON pin. */
    public static Evaluation applyTest(Path root, long expectedId, String expectedSha) throws IOException, SQLException
    {
        final var store = new TacticalStoreV3(root); store.initialize();
        final var applied = store.applyTest(expectedId,expectedSha);
        return publish(store,new Evaluation("ACCEPTED_TEST",applied.id(),applied.sha(),
            "Exact reviewed candidate explicitly applied to own TEST only; source ON pin remains a separate operator action",
            applied.id(),applied.sha(),applied.metrics()));
    }
    public static String report(Path root) throws IOException, SQLException
    {
        final var store = new TacticalStoreV3(root); store.initialize(); store.requireStopped();
        final var model = store.latestAccepted();
        final String json = "{\"state\":" + quote(model == null ? "COLLECTING" : "ACCEPTED_TEST") + ",\"modelVersion\":" + (model == null ? 0 : model.id())
            + ",\"modelSha\":" + quote(model == null ? "" : model.sha()) + ",\"nativeSamples\":" + store.nativeSampleCount()
            + ",\"productionPromotion\":false,\"metrics\":" + (model == null ? "{}" : model.metrics()) + "}";
        write(store.installation().resolve("tactical-v3-report.json"),json + "\n");
        return json;
    }
    public static Path export(Path root) throws IOException, SQLException
    {
        final var store = new TacticalStoreV3(root); store.initialize(); store.requireStopped();
        final var model = store.latestAccepted();
        if (model == null) { throw new IllegalArgumentException("No accepted V3 TEST checkpoint"); }
        final Path directory = store.installation().resolve("tactical-v3-exports");
        Files.createDirectories(directory);
        if (!directory.equals(directory.toRealPath()) || Files.isSymbolicLink(directory)) { throw new IOException("Physical own export directory required"); }
        final Path output = directory.resolve("model-" + model.id() + "-" + UUID.randomUUID().toString().replace("-","") + ".zip");
        final Path pending = output.resolveSibling(output.getFileName() + ".pending");
        final String manifest = "{\"format\":3,\"createdUtc\":" + quote(Instant.now().toString()) + ",\"algorithm\":3,\"algorithmHash\":" + quote(TacticalModelV3.FINGERPRINT)
            + ",\"featureSchema\":" + quote(TacticalObservationV3.SCHEMA) + ",\"actionSpace\":" + quote(TacticalStoreV3.ACTION_SPACE) + ",\"normalization\":" + quote(TacticalStoreV3.NORMALIZATION)
            + ",\"origin\":\"NATIVE_HEAL\",\"status\":\"ACCEPTED_TEST\",\"productionPromotion\":false,\"modelVersion\":" + model.id() + ",\"sha256\":" + quote(model.sha())
            + ",\"datasetSha\":" + quote(model.datasetSha()) + ",\"weightBytes\":" + TacticalModelV3.WEIGHT_BYTES + ",\"metrics\":" + model.metrics() + "}";
        try
        {
            try (var zip = new ZipOutputStream(Files.newOutputStream(pending, StandardOpenOption.CREATE_NEW)))
            {
                entry(zip,"weights.bin",model.model().bytes()); entry(zip,"manifest.json",manifest.getBytes(StandardCharsets.UTF_8));
                entry(zip,"events.bin",TacticalStoreV3.pack(model.rows()));
                zip.putNextEntry(new ZipEntry("dataset.jsonl"));
                for (Row row : model.rows()) { zip.write(datasetJson(row).getBytes(StandardCharsets.UTF_8)); }
                zip.closeEntry();
            }
            Files.move(pending,output,StandardCopyOption.ATOMIC_MOVE);
        }
        catch (IOException failure) { Files.deleteIfExists(pending); throw failure; }
        return output;
    }
    private static String datasetJson(Row row)
    {
        final var receipt = row.receipt(); final var attempt = receipt.attempt(); final var observation = attempt.observation();
        final String offered = attempt.offered().stream().map(TacticalTrainerV3::actionJson).collect(java.util.stream.Collectors.joining(","));
        return "{\"origin\":\"NATIVE_HEAL\",\"split\":" + quote(row.split()) + ",\"context\":" + quote(row.contextKey()) + ",\"eventKey\":" + quote(row.eventKey())
            + ",\"actorProfile\":" + row.actorProfile() + ",\"actorObjectId\":" + observation.actorObjectId() + ",\"actorEpoch\":" + observation.actorEpoch()
            + ",\"decisionId\":" + observation.decisionId() + ",\"capturedNanos\":" + observation.capturedNanos() + ",\"partyIdentity\":" + quote(observation.partyIdentity())
            + ",\"featureSchema\":" + quote(TacticalObservationV3.SCHEMA) + ",\"richSnapshotCodec\":\"TacticalObservationV3.bytes\",\"richSnapshotBase64\":" + quote(Base64.getEncoder().encodeToString(observation.bytes()))
            + ",\"offered\":[" + offered + "],\"selected\":" + actionJson(attempt.selected()) + ",\"baseline\":" + actionJson(attempt.baseline())
            + ",\"policyVersion\":" + attempt.modelVersion() + ",\"nativeBeforeHp\":" + receipt.beforeHp() + ",\"nativeAfterHp\":" + receipt.afterHp()
            + ",\"usefulHp\":" + receipt.actualHp() + ",\"actualMp\":" + receipt.actualMp() + ",\"elapsedNanos\":" + receipt.elapsedNanos() + ",\"reward\":" + receipt.reward() + "}\n";
    }
    private static String actionJson(LegalActionV3 action)
    {
        return "{\"family\":" + quote(action.family().name()) + ",\"owner\":" + quote(action.owner()) + ",\"generation\":" + action.generation()
            + ",\"actorProfile\":" + action.profileId() + ",\"actorObjectId\":" + action.actorObjectId() + ",\"actorEpoch\":" + action.actorEpoch()
            + ",\"targetObjectId\":" + action.targetObjectId() + ",\"targetEpoch\":" + action.targetEpoch() + ",\"partyIdentity\":" + quote(action.partyIdentity())
            + ",\"skillId\":" + action.skillId() + ",\"skillLevel\":" + action.skillLevel() + ",\"mpCost\":" + action.mpCost()
            + ",\"castMillis\":" + action.castMillis() + ",\"range\":" + action.range() + ",\"criticalSafety\":" + action.criticalSafety() + ",\"lawful\":" + action.lawful() + "}";
    }
    private static void entry(ZipOutputStream zip, String name, byte[] bytes) throws IOException
    { zip.putNextEntry(new ZipEntry(name)); zip.write(bytes); zip.closeEntry(); }
    private static String quote(String value) { return TrainingStore.quote(value); }
    private static String json(Evaluation evaluation)
    {
        return "{\"state\":" + quote(evaluation.state()) + ",\"modelVersion\":" + evaluation.modelVersion() + ",\"modelSha\":" + quote(evaluation.modelSha())
            + ",\"reason\":" + quote(evaluation.reason()) + ",\"candidateVersion\":" + evaluation.candidateVersion() + ",\"candidateSha\":" + quote(evaluation.candidateSha())
            + ",\"productionPromotion\":false,\"metrics\":" + evaluation.metrics() + "}";
    }
    private static void write(Path target, String contents) throws IOException
    {
        if (Files.exists(target) && (!target.equals(target.toRealPath()) || Files.isSymbolicLink(target))) { throw new IOException("Physical V3 report required"); }
        final Path pending = target.resolveSibling("." + target.getFileName() + "." + UUID.randomUUID());
        Files.writeString(pending,contents,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        try { Files.move(pending,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
        catch (IOException failure) { Files.deleteIfExists(pending); throw failure; }
    }
}
