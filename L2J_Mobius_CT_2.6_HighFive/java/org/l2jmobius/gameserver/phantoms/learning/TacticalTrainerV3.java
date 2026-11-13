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
    record GroupQuality(boolean qualified, String metrics) { }
    static GroupQuality qualityGroups(List<Row> rows, TacticalModelV3 model, TacticalModelV3 prior)
    {
        TacticalStoreV3.validateDataset(rows);
        final var groups = new java.util.TreeMap<String,java.util.List<Row>>();
        for (var row : rows)
        {
            final var observation = row.receipt().attempt().observation();
            final String scope = observation.partyIdentity().startsWith("solo:") ? "SOLO" : "PARTY";
            final String key = row.receipt().attempt().selected().family() + "/" + observation.self().classId() + "/" + scope;
            groups.computeIfAbsent(key, _ -> new java.util.ArrayList<>()).add(row);
        }
        final var required = java.util.Set.of("DAMAGE/94/SOLO","DAMAGE/94/PARTY","HEAL/97/PARTY","HEAL/105/PARTY");
        boolean qualified = groups.keySet().containsAll(required);
        final var metrics = new java.util.ArrayList<String>();
        for (String missing : required.stream().filter(key -> !groups.containsKey(key)).sorted().toList())
        { metrics.add("{\"group\":" + quote(missing) + ",\"state\":\"INSUFFICIENT_EVIDENCE\",\"train\":0,\"tuning\":0,\"audit\":0}"); }
        for (var group : groups.entrySet())
        {
            final var train = group.getValue().stream().filter(row -> row.split().equals("TRAIN")).toList();
            final var tuning = group.getValue().stream().filter(row -> row.split().equals("TUNING")).toList();
            final var audit = group.getValue().stream().filter(row -> row.split().equals("AUDIT")).toList();
            final long actors = audit.stream().map(Row::actorProfile).distinct().count(), contexts = audit.stream().map(Row::contextKey).distinct().count();
            final boolean sufficient = train.size() >= 8 && tuning.size() >= 4 && audit.size() >= 4 && actors >= 2 && contexts >= 2;
            final double mean = train.stream().mapToDouble(row -> row.receipt().reward()).average().orElse(0);
            final Double constant = audit.isEmpty() ? null : audit.stream().mapToDouble(row -> Math.pow(row.receipt().reward() - mean,2)).average().orElseThrow();
            final Double candidate = audit.isEmpty() ? null : model.loss(audit.stream().map(Row::example).toList());
            final Double previous = audit.isEmpty() || prior == null ? null : prior.loss(audit.stream().map(Row::example).toList());
            final boolean passed = sufficient && Double.isFinite(candidate) && candidate <= constant && (previous == null || candidate <= previous);
            qualified &= passed;
            final String state = !sufficient ? "INSUFFICIENT_EVIDENCE" : passed ? "NO_REGRESSION" : "REGRESSION";
            final var quantized = java.nio.ByteBuffer.allocate(group.getValue().size() * TacticalModelV3.STATE_SIZE * Integer.BYTES);
            for (var row : group.getValue()) { for (float value : row.receipt().attempt().observation().state()) { quantized.putInt(Math.round(value * 10000)); } }
            metrics.add("{\"group\":" + quote(group.getKey()) + ",\"state\":" + quote(state) + ",\"train\":" + train.size() + ",\"tuning\":" + tuning.size()
                + ",\"audit\":" + audit.size() + ",\"auditActors\":" + actors + ",\"auditContexts\":" + contexts + ",\"candidateMse\":" + candidate
                + ",\"constantMse\":" + constant + ",\"priorMse\":" + previous + ",\"sourceFingerprint\":" + quote(TacticalObservationV3.hash(TacticalStoreV3.pack(group.getValue())))
                + ",\"quantizedStateFingerprint\":" + quote(TacticalObservationV3.hash(quantized.array())) + ",\"stateQuantum\":0.0001}");
        }
        return new GroupQuality(qualified,"[" + String.join(",",metrics) + "]");
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
        store.ensureLegacyParent();
        if(store.advantageMode()){return evaluateAdvantage(store);}
        if (store.terminalMode()) { return evaluateTerminal(store); }
        final var prior = store.latestAccepted();
        final var optimizerParent = store.optimizationParent();
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
            final var trained = TacticalModelV3.continueTraining(optimizerParent.state(),train,tuning);
            final var fit = trained.fit();
            final long elapsed = System.nanoTime() - started;
            final double auditLoss = fit.model().loss(audit), constant = constantLoss(rows);
            final Double previous = prior == null ? null : prior.model().loss(audit);
            final var groups = qualityGroups(rows, fit.model(), prior == null ? null : prior.model());
            final long stock = rows.stream().filter(row -> row.receipt().attempt().selected().equals(row.receipt().attempt().baseline())).count();
            final String metrics = "{\"seed\":183,\"datasetSha\":" + quote(frozen.datasetSha()) + ",\"origin\":\"NATIVE_COMBAT\",\"train\":" + train.size()
                + ",\"tuning\":" + tuning.size() + ",\"audit\":" + audit.size() + ",\"auditActors\":" + rows.stream().filter(row -> row.split().equals("AUDIT")).map(Row::actorProfile).distinct().count()
                + ",\"auditContexts\":" + rows.stream().filter(row -> row.split().equals("AUDIT")).map(Row::contextKey).distinct().count()
                + ",\"epochs\":" + fit.epochs() + ",\"trainingNanos\":" + elapsed + ",\"tuningInitialMse\":" + fit.initialLoss() + ",\"tuningMse\":" + fit.loss()
                + ",\"parentModelVersion\":" + optimizerParent.modelId() + ",\"parentOptimizerSha\":" + quote(optimizerParent.sha())
                + ",\"parentWeightsSha\":" + quote(optimizerParent.state().model().sha256()) + ",\"optimizerSha\":" + quote(trained.continuation().sha256())
                + ",\"optimizerUpdates\":" + trained.continuation().updates() + ",\"completedEpochs\":" + trained.continuation().completedEpochs()
                + ",\"optimizerMigration\":" + quote(optimizerParent.runId().equals("MOMENTS_INITIALIZED") ? "MOMENTS_INITIALIZED" : "RESTORED")
                + ",\"samplerRevision\":" + quote(TacticalReplayV3.REVISION)
                + ",\"groupsQualified\":" + groups.qualified() + ",\"groups\":" + groups.metrics()
                + ",\"auditMse\":" + auditLoss + ",\"constantMse\":" + constant + ",\"priorVersion\":" + (prior == null ? 0 : prior.id())
                + ",\"priorMse\":" + (previous == null ? "null" : previous) + ",\"stockMatchedEvents\":" + stock + ",\"differentSelectionEvents\":" + (rows.size() - stock)
                + ",\"stockCounterfactualMse\":null,\"nativeColdDecisionChange\":null,\"limitation\":\"Only chosen actions have native outcomes; MSE does not prove tactical superiority or cold behavior\"}";
            final var candidate = store.save(frozen,trained,optimizerParent,prior == null ? 0 : prior.id(),auditLoss,constant,previous,metrics);
            final boolean qualified = candidate.epochs() > 0 && groups.qualified() && qualityPasses(candidate.auditLoss(),candidate.constantLoss(),candidate.priorLoss());
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
    private static Evaluation evaluateTerminal(TacticalStoreV3 store) throws IOException, SQLException
    {
        final var prior=store.latestAccepted();
        final var parent=store.optimizationParent();
        final var frozen=store.freezeTerminal();
        if(frozen==null)
        {
            return publish(store,new Evaluation("COLLECTING",prior==null?0:prior.id(),prior==null?"":prior.sha(),
                "Need verified terminal TRAIN>=16, TUNING>=8 and fresh AUDIT actors/contexts; legacy cast rows are never fitted by006",0,"",
                "{\"objective\":\"TERMINAL_OUTCOME_V1\",\"terminalRewardUsedForTraining\":false}"));
        }
        final var train=frozen.examples("TRAIN");
        final var tuning=frozen.examples("TUNING");
        final var audit=frozen.examples("AUDIT");
        final long started=System.nanoTime();
        final var trained=TacticalModelV3.continueTraining(parent.state(),train,tuning);
        final long elapsed=System.nanoTime()-started;
        final var fit=trained.fit();
        final String metrics="{\"objective\":\"TERMINAL_OUTCOME_V1\",\"seed\":183,\"samplerRevision\":"+quote(TerminalReplayV1.REVISION)
            +",\"terminalDatasetSha\":"+quote(frozen.datasetSha())+",\"rewardConfigSha\":"+quote(frozen.configSha())+",\"datasetId\":"+quote(frozen.datasetId())
            +",\"terminalReceipts\":"+frozen.receipts().size()+",\"train\":"+train.size()+",\"tuning\":"+tuning.size()+",\"audit\":"+audit.size()
            +",\"epochs\":"+fit.epochs()+",\"trainingNanos\":"+elapsed+",\"tuningInitialMse\":"+fit.initialLoss()+",\"tuningMse\":"+fit.loss()
            +",\"terminalAuditMse\":"+fit.model().loss(audit)+",\"terminalConstantMse\":"+frozen.constantLoss()
            +",\"terminalPriorMse\":"+(prior==null?"null":prior.model().loss(audit))+",\"parentModelVersion\":"+parent.modelId()
            +",\"parentOptimizerSha\":"+quote(parent.sha())+",\"parentWeightsSha\":"+quote(parent.state().model().sha256())
            +",\"optimizerMigration\":\"RESTORED\",\"optimizerSha\":"+quote(trained.continuation().sha256())+",\"optimizerUpdates\":"+trained.continuation().updates()
            +",\"completedEpochs\":"+trained.continuation().completedEpochs()+",\"terminalRewardUsedForTraining\":true,\"productionPromotion\":false"
            +",\"limitation\":\"Registry audit columns validate unchanged source cast receipts; terminal losses are separate. Goal MSE does not prove native advantage.\"}";
        // A failed fit leaves the frozen reservation resumable, without marking any target applied.
        final var candidate=store.saveTerminal(frozen,trained,parent,prior,metrics);
        final var restored=store.terminalSnapshot(candidate.id());
        if(!restored.datasetSha().equals(frozen.datasetSha())){throw new IllegalArgumentException("Committed terminal dataset changed");}
        return publish(store,new Evaluation("DRAFT",prior==null?0:prior.id(),prior==null?"":prior.sha(),
            "Terminal targets fitted from exact optimizer parent; immutable TEST draft requires independent native evaluation",candidate.id(),candidate.sha(),metrics));
    }
    private static Evaluation evaluateAdvantage(TacticalStoreV3 store) throws IOException,SQLException
    {
        final var prior=store.latestAccepted();final var parent=store.optimizationParent();
        final var frozen=store.freezeAdvantage();
        if(frozen==null)
        {
            final var working=store.candidate(parent.modelId());
            if(working!=null&&working.metrics().contains("\"objective\":\""+ActionAdvantageV1.OBJECTIVE+"\""))
            {store.advantageSnapshot(working.id());return publish(store,new Evaluation("DRAFT",prior.id(),prior.sha(),"Сохранённый advantage DRAFT восстановлен без повторного credit.",working.id(),working.sha(),working.metrics()));}
            return publish(store,new Evaluation("COLLECTING",prior.id(),prior.sha(),"Недостаточно независимых TRAIN baseline/paid decisions для advantage007.",0,"","{\"objective\":\""+ActionAdvantageV1.OBJECTIVE+"\",\"advantageUsedForTraining\":false}"));
        }
        if(parent.modelId()!=9||!parent.sha().equals("f76ea960288f3cdb1ba85bb203a33cb327937fdffb99bc6bc4503b90cd54990c")
            ||!parent.state().model().sha256().equals("dc82041d52cb92918990a64d6d003931139e20d82e3a02cd3a973559994158ed"))
        {throw new IllegalArgumentException("007 requires exact terminal Adam9, never selected early-stopping weights");}
        final long started=System.nanoTime();
        final var trained=TacticalModelV3.continueTrainingWeighted(parent.state(),frozen.weightedExamples(),frozen.examples("TUNING"));
        final long elapsed=System.nanoTime()-started;final var fit=trained.fit();
        final String basic=frozen.metrics();
        final String metrics=basic.substring(0,basic.length()-1)+",\"epochs\":"+fit.epochs()+",\"trainingNanos\":"+elapsed
            +",\"tuningInitialMse\":"+fit.initialLoss()+",\"tuningMse\":"+fit.loss()+",\"advantageAuditMse\":"+fit.model().loss(frozen.examples("AUDIT"))
            +",\"advantageConstantMse\":"+frozen.constantLoss()+",\"parentModelVersion\":"+parent.modelId()+",\"parentOptimizerSha\":"+quote(parent.sha())
            +",\"parentWeightsSha\":"+quote(parent.state().model().sha256())+",\"optimizerSha\":"+quote(trained.continuation().sha256())
            +",\"optimizerUpdates\":"+trained.continuation().updates()+",\"completedEpochs\":"+trained.continuation().completedEpochs()
            +",\"optimizerWeightsSha\":"+quote(trained.continuation().model().sha256())
            +",\"advantageUsedForTraining\":true,\"optimizerMigration\":\"RESTORED\",\"limitation\":\"Stochastic outcomes, paid-selection bias and clipped IPS; score gaps do not prove causal superiority\"}";
        final var candidate=store.saveAdvantage(frozen,trained,parent,prior,metrics);
        if(!store.advantageSnapshot(candidate.id()).datasetSha().equals(frozen.datasetSha())){throw new IllegalArgumentException("Committed007 target changed");}
        return publish(store,new Evaluation("DRAFT",prior.id(),prior.sha(),"Advantage DRAFT обучен от terminal Adam9; требуется независимая проверка решений.",candidate.id(),candidate.sha(),metrics));
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
        final var optimizer = model == null ? null : store.optimizationParent();
        final var working = optimizer == null ? null : store.candidate(optimizer.modelId());
        final String json = "{\"state\":" + quote(model == null ? "COLLECTING" : working != null && working.id() != model.id() ? "DRAFT" : "ACCEPTED_TEST") + ",\"modelVersion\":" + (model == null ? 0 : model.id())
            + ",\"modelSha\":" + quote(model == null ? "" : model.sha()) + ",\"nativeSamples\":" + store.nativeSampleCount()
            + ",\"episodeCounts\":" + store.episodeCounts() + ",\"workingModelVersion\":" + (working == null ? 0 : working.id())
            + ",\"optimizerSha\":" + quote(optimizer == null ? "" : optimizer.sha()) + ",\"optimizerUpdates\":" + (optimizer == null ? 0 : optimizer.state().updates())
            + ",\"productionPromotion\":false,\"metrics\":" + (working == null ? "{}" : working.metrics()) + "}";
        write(store.installation().resolve("tactical-v3-report.json"),json + "\n");
        return json;
    }
    public static Path export(Path root) throws IOException, SQLException
    {
        final var store = new TacticalStoreV3(root); store.initialize(); store.requireStopped();
        final var model = store.latestAccepted();
        if (model == null) { throw new IllegalArgumentException("No accepted V3 TEST checkpoint"); }
        final var optimizer = store.optimizationParent();
        final var working = store.candidate(optimizer.modelId());
        if (working == null) { throw new IllegalArgumentException("Working V3 checkpoint missing"); }
        final byte[] replay = TacticalStoreV3.pack(working.rows());
        final var terminal=working.metrics().contains("\"objective\":\"TERMINAL_OUTCOME_V1\"")?store.terminalSnapshot(working.id()):null;
        final var advantage=working.metrics().contains("\"objective\":\""+ActionAdvantageV1.OBJECTIVE+"\"")?store.advantageSnapshot(working.id()):null;
        final byte[] contexts = working.origin().equals("NATIVE_COMBAT") ? store.replayContexts(working.runId(),working.datasetSha(),working.rows()) : null;
        final Path directory = store.installation().resolve("tactical-v3-exports");
        Files.createDirectories(directory);
        if (!directory.equals(directory.toRealPath()) || Files.isSymbolicLink(directory)) { throw new IOException("Physical own export directory required"); }
        final Path output = directory.resolve("model-" + model.id() + "-" + UUID.randomUUID().toString().replace("-","") + ".zip");
        final Path pending = output.resolveSibling(output.getFileName() + ".pending");
        final String manifest = "{\"format\":3,\"createdUtc\":" + quote(Instant.now().toString()) + ",\"algorithm\":3,\"algorithmHash\":" + quote(TacticalModelV3.FINGERPRINT)
            + ",\"featureSchema\":" + quote(TacticalObservationV3.SCHEMA) + ",\"actionSpace\":" + quote(TacticalStoreV3.registrySpace(model.origin())) + ",\"normalization\":" + quote(TacticalStoreV3.NORMALIZATION)
            + ",\"origin\":" + quote(model.origin()) + ",\"status\":\"ACCEPTED_TEST\",\"productionPromotion\":false,\"modelVersion\":" + model.id() + ",\"sha256\":" + quote(model.sha())
            + ",\"datasetSha\":" + quote(model.datasetSha()) + ",\"weightBytes\":" + TacticalModelV3.WEIGHT_BYTES + ",\"metrics\":" + model.metrics()
            + ",\"workingModelVersion\":" + working.id() + ",\"workingStatus\":" + quote(working.status()) + ",\"workingWeightsSha\":" + quote(working.sha())
            + ",\"optimizerSha\":" + quote(optimizer.sha()) + ",\"optimizerUpdates\":" + optimizer.state().updates() + ",\"completedEpochs\":" + optimizer.state().completedEpochs()
            + ",\"optimizerWeightsSha\":" + quote(optimizer.state().model().sha256())
            + ",\"replaySha\":" + quote(TacticalObservationV3.hash(replay)) + ",\"contextReplaySha\":" + (contexts == null ? "null" : quote(TacticalObservationV3.hash(contexts)))
            + ",\"terminalDatasetSha\":"+(terminal==null?"null":quote(terminal.datasetSha()))
            + ",\"advantageDatasetSha\":"+(advantage==null?"null":quote(advantage.datasetSha()))
            + ",\"advantageConfigSha\":"+(advantage==null?"null":quote(ActionAdvantageV1.CONFIG_SHA))
            + ",\"rewardConfigSha\":"+(terminal==null?"null":quote(terminal.configSha()))
            + ",\"workingRunId\":" + quote(optimizer.runId()) + ",\"episodeCounts\":" + store.episodeCounts() + "}";
        try
        {
            try (var zip = new ZipOutputStream(Files.newOutputStream(pending, StandardOpenOption.CREATE_NEW)))
            {
                entry(zip,"weights.bin",model.model().bytes()); entry(zip,"manifest.json",manifest.getBytes(StandardCharsets.UTF_8));
                entry(zip,"events.bin",TacticalStoreV3.pack(model.rows()));
                entry(zip,"optimizer.bin",optimizer.state().bytes());
                entry(zip,"replay.bin",replay);
                if(advantage!=null)
                {
                    entry(zip,"advantage-targets.bin",advantage.bytes());
                    final var targets=advantage.targets();
                    final String rows=advantage.samples().stream().map(s->"{\"naturalKey\":"+quote(s.terminal().naturalKey())+",\"actionKey\":"+quote(s.actionKey())
                        +",\"receiptSha\":"+quote(s.terminal().sha256())+",\"selectionSha\":"+quote(s.selection().sha256())+",\"split\":"+quote(s.selection().split())
                        +",\"gradientUsed\":"+(s.selection().split().equals("TRAIN")&&s.selection().experimental()&&targets.stream().anyMatch(t->t.sample().actionKey().equals(s.actionKey())))
                        +",\"modelVersion\":"+working.id()+",\"optimizerSha\":"+quote(optimizer.sha())+"}").collect(java.util.stream.Collectors.joining("\n"))+"\n";
                    entry(zip,"advantage-applications.jsonl",rows.getBytes(StandardCharsets.UTF_8));
                }
                if(terminal!=null)
                {
                    entry(zip,"terminal-targets.bin",terminal.bytes());
                    final String receipts=terminal.receipts().stream().map(r->"{\"naturalKey\":"+quote(r.naturalKey())+",\"receiptSha\":"+quote(r.sha256())
                        +",\"fitRole\":"+quote(r.split())+",\"modelVersion\":"+working.id()+",\"optimizerSha\":"+quote(optimizer.sha())
                        +",\"terminalRewardUsedForTraining\":"+r.split().equals("TRAIN")+"}\n").collect(java.util.stream.Collectors.joining());
                    entry(zip,"terminal-applications.jsonl",receipts.getBytes(StandardCharsets.UTF_8));
                }
                if (contexts != null)
                {
                    entry(zip,"context-replay.bin",contexts);
                    for (var failure : store.replayFailures(contexts,working.datasetSha()).entrySet()) { entry(zip,"failures/" + failure.getKey() + ".bin",failure.getValue()); }
                }
                entry(zip,"working-weights.bin",working.model().bytes());
                entry(zip,"working-metrics.json",working.metrics().getBytes(StandardCharsets.UTF_8));
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
        return "{\"origin\":" + quote(receipt.origin()) + ",\"split\":" + quote(row.split()) + ",\"context\":" + quote(row.contextKey()) + ",\"eventKey\":" + quote(row.eventKey())
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
