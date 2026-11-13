/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.learning;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipFile;

/** Post-drain, TEST-only V3 durability. No SQL or training is called from native gameplay. */
public final class TacticalStoreV3
{
    public static final long SEED = 183;
    public static final String ACTION_SPACE = hash("LegalActionV3/" + LegalActionV3.VERSION + "/HEAL/" + Arrays.toString(LegalActionV3.Family.values()) + '/' + LegalActionV3.FINGERPRINT);
    public static final String NORMALIZATION = hash(TacticalObservationV3.SCHEMA + "/state128/action64/paired-mask/clip[-1,1]/reward[-1,1]");
    public static final String COMBAT_ACTION_SPACE = hash(ACTION_SPACE + "/receipt-v5/native-DAMAGE+HEAL/useful-hp-fraction-exact-mp-elapsed/v1");
    private static final int MAGIC = 0x54563352, ROW_VERSION = 4, MAX_ROW = 600000, MAX_DATASET = 64 * 1024 * 1024;
    private final TrainingStore _guard;
    private final Path _root;
    private final String _datasetContextPrefix;

    public record Row(String split, String contextKey, TacticalReceiptV3 receipt)
    {
        public Row(String split, String contextKey, HealEvidenceV3.Receipt receipt)
        { this(split, contextKey, TacticalReceiptV3.fromHeal(receipt)); }
        public Row
        {
            if (!Set.of("TRAIN", "TUNING", "AUDIT").contains(split) || contextKey == null || contextKey.isBlank()
                || contextKey.length() > 256 || contextKey.chars().anyMatch(v -> v < 32) || receipt == null)
            { throw new IllegalArgumentException("Invalid native split/context"); }
            validateReceipt(receipt);
        }
        public String eventKey() { return receipt.eventKey(); }
        public long actorProfile() { return receipt.attempt().observation().profileId(); }
        public String contextHash() { return hash(contextKey); }
        public String cohort() { return hash(actorProfile() + ":" + contextKey); }
        public TacticalModelV3.Example example()
        {
            final var attempt = receipt.attempt();
            return new TacticalModelV3.Example(attempt.observation().state(), attempt.selected().features(attempt.observation()), receipt.reward());
        }
        String actionKey()
        {
            final var a = receipt.attempt().selected();
            return hash(a.profileId() + "/" + a.actorObjectId() + "/" + a.actorEpoch() + "/" + a.generation()
                + "/" + a.targetObjectId() + "/" + a.targetEpoch() + "/" + a.skillId() + "/" + a.skillLevel());
        }
    }
    public record Frozen(String runId, String datasetSha, List<Row> rows)
    {
        public Frozen { rows = List.copyOf(rows); }
    }
    public record Optimization(long modelId, String runId, String sha, TacticalModelV3.Optimizer state) { }
    record Legacy003(TacticalModelV3 model, List<Row> rows, String datasetSha, String metrics) { }

    /** Portable003 is already accepted evidence, never fresh004 credit or a newly promoted candidate. */
    static Legacy003 readLegacy003(Path archive) throws IOException
    {
        if (!archive.toAbsolutePath().normalize().equals(archive.toRealPath()) || Files.isSymbolicLink(archive) || Files.size(archive) > 16 * 1024 * 1024
            || !TacticalObservationV3.hash(Files.readAllBytes(archive)).equals("04393f2cbc7ba8c4760e42c6f32ae93edd4b8bf848e50d25e97ee8b4348716b5"))
        { throw new IllegalArgumentException("Authenticated accepted003 archive required"); }
        try (var zip = new ZipFile(archive.toFile(), StandardCharsets.UTF_8))
        {
            final var names = zip.stream().map(entry -> entry.getName()).toList();
            if (names.size() != 4 || !Set.copyOf(names).equals(Set.of("weights.bin", "manifest.json", "events.bin", "dataset.jsonl")))
            { throw new IllegalArgumentException("Invalid accepted003 archive entries"); }
            final byte[] weights, events;
            final String manifest;
            try (var input = zip.getInputStream(zip.getEntry("weights.bin"))) { weights = input.readNBytes(TacticalModelV3.BYTE_SIZE + 1); }
            try (var input = zip.getInputStream(zip.getEntry("events.bin"))) { events = input.readNBytes(MAX_DATASET + 1); }
            try (var input = zip.getInputStream(zip.getEntry("manifest.json"))) { manifest = new String(input.readNBytes(65537), StandardCharsets.UTF_8); }
            final var model = TacticalModelV3.restore(weights, "6b91706463f4e24f89f1c4f8be3a82d9ee58eadb07a4254c79449665e3fa97dc");
            final var rows = unpack(events);
            final String sha = TacticalObservationV3.hash(events);
            if (!sha.equals("bf2039c39f18e4137593129deccf1781b5f4f73bf95d9b0eb7b252465079be6e") || rows.size() != 120
                || !manifest.contains("\"featureSchema\":\"" + TacticalObservationV3.SCHEMA + "\"")
                || !manifest.contains("\"actionSpace\":\"" + ACTION_SPACE + "\"")
                || !manifest.contains("\"normalization\":\"" + NORMALIZATION + "\"") || !manifest.contains("\"status\":\"ACCEPTED_TEST\""))
            { throw new IllegalArgumentException("Incompatible accepted003 provenance"); }
            // The whole archive SHA above authenticates the historical JSON; no permissive JSON parsing.
            final int metrics = manifest.indexOf("\"metrics\":");
            if (metrics < 0 || !manifest.endsWith("}}")) { throw new IllegalArgumentException("Missing accepted003 metrics"); }
            return new Legacy003(model, rows, sha, manifest.substring(metrics + 10, manifest.length() - 1));
        }
    }

    public void ensureLegacyParent() throws IOException, SQLException
    {
        requireStopped();
        if (latestAccepted() != null) { return; }
        final Path clone = _root.getParent().getParent().getParent();
        final var legacy = readLegacy003(clone.resolve("L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-003/artifacts/accepted-model-2/model-2.zip"));
        final String run = hash("PHANTOM-LEARNING-003/portable/model2").substring(0, 32);
        final double auditLoss = legacy.model().loss(legacy.rows().stream().filter(row -> row.split().equals("AUDIT")).map(Row::example).toList());
        final double constant = TacticalTrainerV3.constantLoss(legacy.rows());
        if (!TacticalTrainerV3.qualityPasses(auditLoss, constant, null)) { throw new IllegalArgumentException("Historical accepted003 quality changed"); }
        transaction(connection ->
        {
            try (var query = connection.createStatement(); var found = query.executeQuery("SELECT model_id FROM phantom_tactical_v3_models LIMIT 1 FOR UPDATE"))
            { if (found.next()) { throw new IllegalArgumentException("Conflicting model registry; legacy overwrite forbidden"); } }
            try (var insert = connection.prepareStatement("INSERT INTO phantom_tactical_v3_runs(run_id,dataset_sha,seed,snapshot_blob,state,reason) VALUES(?,?,?,?,'ACCEPTED_TEST','Authenticated historical003 import; no fresh004 credit')"))
            { insert.setString(1,run); insert.setString(2,legacy.datasetSha()); insert.setLong(3,SEED); insert.setBytes(4,pack(legacy.rows())); insert.executeUpdate(); }
            try (var insert = connection.prepareStatement("INSERT INTO phantom_tactical_v3_audit_used(cohort_hash,run_id) VALUES(?,?)"))
            {
                for (var row : legacy.rows().stream().filter(row -> row.split().equals("AUDIT")).toList())
                { insert.setString(1,row.cohort()); insert.setString(2,run); insert.addBatch(); }
                insert.executeBatch();
            }
            try (var insert = connection.prepareStatement("INSERT INTO phantom_tactical_v3_models(model_id,run_id,algorithm,algorithm_hash,feature_schema,action_space,normalization,origin,status,dataset_sha,weights_blob,sha256,epochs,train_count,tuning_count,audit_count,audit_contexts,audit_mse,constant_mse,prior_mse,metrics_json) VALUES(2,?,3,?,?,?,?,'NATIVE_HEAL','ACCEPTED_TEST',?,?,?,42,64,32,24,24,?,?,NULL,?)"))
            {
                insert.setString(1,run); insert.setString(2,TacticalModelV3.FINGERPRINT); insert.setString(3,TacticalObservationV3.SCHEMA);
                insert.setString(4,ACTION_SPACE); insert.setString(5,NORMALIZATION); insert.setString(6,legacy.datasetSha());
                insert.setBytes(7,legacy.model().bytes()); insert.setString(8,legacy.model().sha256()); insert.setDouble(9,auditLoss);
                insert.setDouble(10,constant); insert.setString(11,legacy.metrics()); insert.executeUpdate();
            }
            return null;
        });
        final var imported = accepted(2, legacy.model().sha256());
        if (imported == null || !Arrays.equals(imported.model().bytes(), legacy.model().bytes()))
        { throw new IllegalArgumentException("Historical003 cold readback failed"); }
    }
    public record Checkpoint(long id, TacticalModelV3 model, String sha, String status, String datasetSha,
        int trainCount, int tuningCount, int auditCount, int auditContexts, double auditLoss, double constantLoss,
        Double priorLoss, int epochs, String metrics, List<Row> rows, String origin, String runId)
    {
        public Checkpoint { rows = List.copyOf(rows); }
    }

    public TacticalStoreV3(Path installation) throws IOException
    {
        _guard = new TrainingStore(installation);
        _root = _guard.installation();
        final var config = _guard.config();
        _datasetContextPrefix = config.text("v3DatasetContextPrefix", "");
        if (_datasetContextPrefix.length() > 256 || _datasetContextPrefix.chars().anyMatch(value -> value < 32))
        { throw new IllegalArgumentException("Invalid V3 dataset context prefix"); }
        if (config.integer("modelAlgorithmV3", 3, 3, 3) != TacticalModelV3.ALGORITHM
            || !Set.of("TACTICAL_V3", TacticalObservationV3.SCHEMA).contains(config.text("featureSchemaV3", TacticalObservationV3.SCHEMA))
            || !"DAMAGE,HEAL".equals(config.text("learningActionFamilies", "DAMAGE,HEAL")) || config.seed() != SEED)
        { throw new IllegalArgumentException("V3 algorithm/schema/action families or fixed seed changed"); }
    }
    public Path installation() { return _root; }
    public void requireStopped() throws SQLException { _guard.requireStopped(); }

    public boolean terminalMode()
    {
        return _guard.config().text("configProfile","").equals("OUTCOME006") && _guard.config().text("outcome006Enabled","false").equals("true")
            && _guard.config().text("v3Terminal006","false").equals("true");
    }
    public boolean advantageMode()
    {return terminalMode()&&_guard.config().text("exploration007Enabled","false").equals("true")&&_guard.config().text("exploration007Campaign","").equals(TrainExplorationV1.CAMPAIGN)
        &&_guard.config().text("exploration007Dataset","").equals(TrainExplorationV1.DATASET);}
    public Path advantageDirectory(){return _root.resolve("outcome006/campaigns").resolve(TrainExplorationV1.CAMPAIGN);}

    public boolean residualMode()
    {return terminalMode()&&_guard.config().text("exploration008Enabled","false").equals("true")
        &&!_guard.config().text("exploration007Enabled","false").equals("true")
        &&_guard.config().text("exploration008Campaign","").equals(ActionAdvantage008.CAMPAIGN)
        &&_guard.config().text("exploration008Dataset","").equals(ActionAdvantage008.DATASET)
        &&_guard.config().text("outcome006DatasetId","").equals(ActionAdvantage008.DATASET);}
    public Path residualDirectory(){return _root.resolve("outcome006/campaigns").resolve(ActionAdvantage008.CAMPAIGN);}
    private List<ActionAdvantage008.Sample> residualSources() throws SQLException,IOException
    {
        if(!residualMode()){throw new IllegalArgumentException("Explicit owned008 objective required");}
        Path directory=residualDirectory().resolve("samples");if(!Files.exists(directory)){return List.of();}
        if(!directory.equals(directory.toRealPath())||Files.isSymbolicLink(directory)){throw new IOException("Physical008 sample directory required");}
        final List<Path> paths;try(var files=Files.list(directory)){paths=files.filter(p->p.getFileName().toString().endsWith(".selection.bin")).sorted().toList();}
        if(paths.size()>48){throw new IllegalArgumentException("008 sample quota exceeded");}
        var result=new ArrayList<ActionAdvantage008.Sample>();
        try(var connection=_guard.openTacticalConnection();var query=connection.prepareStatement("SELECT receipt_sha,receipt_blob FROM phantom_tactical_v3_terminal_rewards WHERE episode_id=? AND dataset_id=?"))
        {
            for(var path:paths)
            {
                if(!path.equals(path.toRealPath())||Files.isSymbolicLink(path)||Files.size(path)>1000000||!path.getFileName().toString().matches("[a-f0-9]{64}\\.selection\\.bin")){throw new IOException("Invalid008 selection path");}
                var selection=TrainExplorationV1.Selection.restore(Files.readAllBytes(path));
                if(!path.getFileName().toString().equals(selection.episode()+".selection.bin")){throw new IllegalArgumentException("008 selection identity changed");}
                if(!selection.fallback().isEmpty()){continue;}
                query.setString(1,selection.episode());query.setString(2,ActionAdvantage008.DATASET);
                try(var found=query.executeQuery())
                {
                    if(!found.next()){continue;}
                    var receipt=TerminalRewardReceiptV1.restore(found.getBytes(2),found.getString(1));
                    if(receipt.actions().stream().filter(a->selection.matches(a.row().receipt())).count()==1){result.add(new ActionAdvantage008.Sample(selection,receipt));}
                }
            }
        }
        return List.copyOf(result);
    }
    private static String residualRole(ActionAdvantage008.Frozen frozen,ActionAdvantage008.Sample sample)
    {
        if(!sample.selection().split().equals("TRAIN")){return "HELD_OUT";}
        if(!sample.selection().experimental()){return "BASELINE";}
        return frozen.targets().stream().anyMatch(t->t.sample().terminal().naturalKey().equals(sample.terminal().naturalKey()))?"GRADIENT":"EXCLUDED";
    }
    private static void requireResidualRows(Connection connection,ActionAdvantage008.Frozen frozen,Long model) throws SQLException
    {
        for(var sample:frozen.samples())
        {
            var receipt=sample.terminal();requireTerminalNative(connection,receipt);
            try(var query=connection.prepareStatement("SELECT before_json FROM phantom_tactical_v3_episodes WHERE episode_id=?"))
            {
                query.setString(1,receipt.episode().id());try(var found=query.executeQuery())
                {if(!found.next()){throw new IllegalArgumentException("008 native setup missing");}requireTerminalString(found.getString(1),"comparisonCellSha",sample.selection().cell());requireTerminalString(found.getString(1),"collectionPolicy","SHADOW");requireTerminalString(found.getString(1),"campaign",ActionAdvantage008.CAMPAIGN);}
            }
            try(var query=connection.prepareStatement("SELECT receipt_sha,receipt_blob,(SELECT COUNT(*) FROM phantom_tactical_v3_terminal_applications WHERE natural_key=?) FROM phantom_tactical_v3_terminal_rewards WHERE natural_key=?"))
            {
                query.setString(1,receipt.naturalKey());query.setString(2,receipt.naturalKey());try(var found=query.executeQuery())
                {if(!found.next()||!receipt.sha256().equals(found.getString(1))||!Arrays.equals(receipt.bytes(),found.getBytes(2))||found.getLong(3)!=0){throw new IllegalArgumentException("008 terminal source changed or already credited");}}
            }
            try(var query=connection.prepareStatement("SELECT a.action_key,a.run_id,a.model_id,a.target_sha,a.receipt_sha,a.selection_sha,a.optimizer_sha,a.split,a.fit_role,a.gradient_used,o.optimizer_sha FROM phantom_tactical_v3_advantage_applications a LEFT JOIN phantom_tactical_v3_optimizers o ON o.model_id=a.model_id WHERE a.natural_key=?"))
            {
                query.setString(1,receipt.naturalKey());try(var found=query.executeQuery())
                {
                    if(model==null){if(found.next()){throw new IllegalArgumentException("008 episode already reserved/consumed");}continue;}
                    String role=residualRole(frozen,sample);
                    if(!found.next()||!sample.actionKey().equals(found.getString(1))||!frozen.runId().equals(found.getString(2))||found.getLong(3)!=model
                        ||!frozen.datasetSha().equals(found.getString(4))||!receipt.sha256().equals(found.getString(5))||!sample.selection().sha256().equals(found.getString(6))
                        ||!(model==0?"0".repeat(64):found.getString(11)).equals(found.getString(7))||!sample.selection().split().equals(found.getString(8))||!role.equals(found.getString(9))
                        ||found.getBoolean(10)!=(model>0&&role.equals("GRADIENT")))
                    {throw new IllegalArgumentException("008 atomic episode/actions/Adam proof changed");}
                }
            }
        }
    }

    public ActionAdvantage008.Frozen freezeResidual() throws SQLException,IOException
    {
        requireStopped();if(!residualMode()){throw new IllegalArgumentException("008 objective opt-in required");}
        var samples=residualSources();
        return transaction(connection ->
        {
            try(var query=connection.prepareStatement("SELECT r.run_id,r.dataset_sha,r.snapshot_blob FROM phantom_tactical_v3_runs r JOIN phantom_tactical_v3_advantage_snapshots a ON a.run_id=r.run_id WHERE r.state='FROZEN' AND a.config_sha=? ORDER BY r.created_at,r.run_id LIMIT 1 FOR UPDATE"))
            {
                query.setString(1,ActionAdvantage008.CONFIG_SHA);try(var found=query.executeQuery())
                {if(found.next()){return residualSnapshot(connection,found.getString(1),found.getString(2),found.getBytes(3),0);}}
            }
            if(samples.isEmpty()){return null;}var unused=new ArrayList<ActionAdvantage008.Sample>();
            for(var sample:samples)
            {
                try(var query=connection.prepareStatement("SELECT COUNT(*) FROM phantom_tactical_v3_advantage_applications WHERE natural_key=?"))
                {query.setString(1,sample.terminal().naturalKey());try(var found=query.executeQuery()){found.next();if(found.getLong(1)==0){unused.add(sample);}}}
            }
            if(unused.isEmpty()){return null;}
            var frozen=ActionAdvantage008.freeze(UUID.randomUUID().toString().replace("-",""),unused);if(!frozen.sufficient()){return null;}
            requireResidualRows(connection,frozen,null);var rows=frozen.sourceRows();byte[] sourceBytes=pack(rows);String sourceSha=hashBytes(sourceBytes);
            try(var insert=connection.prepareStatement("INSERT INTO phantom_tactical_v3_runs(run_id,dataset_sha,seed,snapshot_blob,state) VALUES(?,?,?,?,'FROZEN')"))
            {insert.setString(1,frozen.runId());insert.setString(2,sourceSha);insert.setLong(3,SEED);insert.setBytes(4,sourceBytes);insert.executeUpdate();}
            try(var insert=connection.prepareStatement("INSERT INTO phantom_tactical_v3_advantage_snapshots(run_id,target_sha,config_sha,snapshot_blob) VALUES(?,?,?,?)"))
            {insert.setString(1,frozen.runId());insert.setString(2,frozen.datasetSha());insert.setString(3,ActionAdvantage008.CONFIG_SHA);insert.setBytes(4,frozen.bytes());insert.executeUpdate();}
            try(var reserve=connection.prepareStatement("INSERT INTO phantom_tactical_v3_advantage_applications(natural_key,action_key,run_id,model_id,target_sha,receipt_sha,selection_sha,optimizer_sha,split,fit_role,gradient_used) VALUES(?,?,?,0,?,?,?,?,?,?,FALSE)"))
            {
                for(var sample:frozen.samples())
                {reserve.setString(1,sample.terminal().naturalKey());reserve.setString(2,sample.actionKey());reserve.setString(3,frozen.runId());reserve.setString(4,frozen.datasetSha());reserve.setString(5,sample.terminal().sha256());reserve.setString(6,sample.selection().sha256());reserve.setString(7,"0".repeat(64));reserve.setString(8,sample.selection().split());reserve.setString(9,residualRole(frozen,sample));reserve.addBatch();}reserve.executeBatch();
            }
            var contexts=new ArrayList<TacticalReplayV3.ContextSample>();
            for(var sample:frozen.samples()){for(var action:sample.terminal().actions())
            {var row=action.row();contexts.add(new TacticalReplayV3.ContextSample(row.eventKey(),"EFFECT",sample.terminal().episode().outcome(),TacticalReplayV3.spot(row.contextKey()),hashBytes(encode(row)),sample.terminal().episode().id(),sample.terminal().episode().sha256()));}}
            byte[] contextBytes=TacticalReplayV3.packContexts(contexts,sourceSha);
            try(var insert=connection.prepareStatement("INSERT INTO phantom_tactical_v3_context_replay(run_id,dataset_sha,sample_sha,sample_blob) VALUES(?,?,?,?)"))
            {insert.setString(1,frozen.runId());insert.setString(2,sourceSha);insert.setString(3,hashBytes(contextBytes));insert.setBytes(4,contextBytes);insert.executeUpdate();}
            try(var insert=connection.prepareStatement("INSERT INTO phantom_tactical_v3_audit_used(cohort_hash,run_id) VALUES(?,?)"))
            {for(String cohort:rows.stream().filter(row->row.split().equals("AUDIT")).map(Row::cohort).distinct().toList()){insert.setString(1,cohort);insert.setString(2,frozen.runId());insert.addBatch();}insert.executeBatch();}
            return frozen;
        });
    }
    private static ActionAdvantage008.Frozen residualSnapshot(Connection connection,String run,String sourceSha,byte[] sourceBytes,long model) throws SQLException
    {
        try(var query=connection.prepareStatement("SELECT target_sha,config_sha,snapshot_blob FROM phantom_tactical_v3_advantage_snapshots WHERE run_id=?"))
        {
            query.setString(1,run);try(var found=query.executeQuery())
            {
                if(!found.next()||!ActionAdvantage008.CONFIG_SHA.equals(found.getString(2))){throw new IllegalArgumentException("008 frozen objective missing/changed");}
                var frozen=ActionAdvantage008.restore(run,found.getBytes(3),found.getString(1));
                if(!frozen.sufficient()||!sourceSha.equals(hashBytes(sourceBytes))||!Arrays.equals(sourceBytes,pack(frozen.sourceRows()))){throw new IllegalArgumentException("008 factual source/target dataset changed");}
                requireResidualRows(connection,frozen,model);return frozen;
            }
        }
    }
    public ActionAdvantage008.Frozen residualSnapshot(long model) throws SQLException
    {
        var checkpoint=candidate(model);if(checkpoint==null||!checkpoint.metrics().contains("\"objective\":\""+ActionAdvantage008.OBJECTIVE+"\"")){throw new IllegalArgumentException("008 checkpoint required");}
        try(var connection=_guard.openTacticalConnection();var query=connection.prepareStatement("SELECT snapshot_blob FROM phantom_tactical_v3_runs WHERE run_id=?"))
        {query.setString(1,checkpoint.runId());try(var found=query.executeQuery()){if(!found.next()){throw new IllegalArgumentException("008 raw replay missing");}return residualSnapshot(connection,checkpoint.runId(),checkpoint.datasetSha(),found.getBytes(1),model);}}
    }

    private List<ActionAdvantageV1.Sample> advantageSources() throws SQLException,IOException
    {
        if(!advantageMode()){throw new IllegalArgumentException("Explicit owned007 objective required");}
        final Path directory=advantageDirectory().resolve("samples");
        if(!Files.exists(directory)){return List.of();}
        if(!directory.equals(directory.toRealPath())||Files.isSymbolicLink(directory)){throw new IOException("Physical007 sample directory required");}
        final List<Path> paths;try(var stream=Files.list(directory)){paths=stream.filter(p->p.getFileName().toString().endsWith(".selection.bin")).sorted().toList();}
        if(paths.size()>128){throw new IllegalArgumentException("007 sample capacity");}
        final var result=new ArrayList<ActionAdvantageV1.Sample>();
        try(var connection=_guard.openTacticalConnection();var query=connection.prepareStatement("SELECT receipt_sha,receipt_blob FROM phantom_tactical_v3_terminal_rewards WHERE episode_id=? AND dataset_id=?"))
        {
            for(var path:paths)
            {
                if(!path.equals(path.toRealPath())||Files.isSymbolicLink(path)||Files.size(path)>1000000||!path.getFileName().toString().matches("[a-f0-9]{64}\\.selection\\.bin")){throw new IOException("Invalid007 selection path");}
                var selection=TrainExplorationV1.Selection.restore(Files.readAllBytes(path));
                if(!path.getFileName().toString().equals(selection.episode()+".selection.bin")){throw new IllegalArgumentException("007 selection identity changed");}
                if(!selection.fallback().isEmpty()){continue;}
                query.setString(1,selection.episode());query.setString(2,TrainExplorationV1.DATASET);
                try(var found=query.executeQuery())
                {
                    if(!found.next()){continue;} // Kept as a rejected/no-paid-effect episode in the native campaign census.
                    var receipt=TerminalRewardReceiptV1.restore(found.getBytes(2),found.getString(1));
                    if(receipt.actions().stream().filter(a->selection.matches(a.row().receipt())).count()==1){result.add(new ActionAdvantageV1.Sample(selection,receipt));}
                }
            }
        }
        return List.copyOf(result);
    }
    private static String advantageRole(ActionAdvantageV1.Frozen frozen,ActionAdvantageV1.Sample sample)
    {
        if(!sample.selection().split().equals("TRAIN")){return "HELD_OUT";}
        if(!sample.selection().experimental()){return "BASELINE";}
        return frozen.targets().stream().anyMatch(t->t.sample().actionKey().equals(sample.actionKey()))?"GRADIENT":"EXCLUDED";
    }
    private static void requireAdvantageRows(Connection connection,ActionAdvantageV1.Frozen frozen,Long model) throws SQLException
    {
        for(var sample:frozen.samples())
        {
            var receipt=sample.terminal();requireTerminalNative(connection,receipt);
            try(var query=connection.prepareStatement("SELECT before_json FROM phantom_tactical_v3_episodes WHERE episode_id=?"))
            {query.setString(1,receipt.episode().id());try(var found=query.executeQuery()){if(!found.next()){throw new IllegalArgumentException("007 native setup missing");}requireTerminalString(found.getString(1),"comparisonCellSha",sample.selection().cell());}}
            try(var query=connection.prepareStatement("SELECT receipt_sha,receipt_blob,(SELECT COUNT(*) FROM phantom_tactical_v3_terminal_applications WHERE natural_key=?) FROM phantom_tactical_v3_terminal_rewards WHERE natural_key=?"))
            {
                query.setString(1,receipt.naturalKey());query.setString(2,receipt.naturalKey());try(var found=query.executeQuery())
                {if(!found.next()||!receipt.sha256().equals(found.getString(1))||!Arrays.equals(receipt.bytes(),found.getBytes(2))||found.getLong(3)!=0){throw new IllegalArgumentException("007 native target changed or terminal action already used by another objective");}}
            }
            try(var query=connection.prepareStatement("SELECT a.action_key,a.run_id,a.model_id,a.target_sha,a.receipt_sha,a.selection_sha,a.optimizer_sha,a.split,a.fit_role,a.gradient_used,o.optimizer_sha FROM phantom_tactical_v3_advantage_applications a LEFT JOIN phantom_tactical_v3_optimizers o ON o.model_id=a.model_id WHERE a.natural_key=?"))
            {
                query.setString(1,receipt.naturalKey());try(var found=query.executeQuery())
                {
                    if(model==null){if(found.next()){throw new IllegalArgumentException("007 action already reserved/consumed");}continue;}
                    final String role=advantageRole(frozen,sample);
                    if(!found.next()||!sample.actionKey().equals(found.getString(1))||!frozen.runId().equals(found.getString(2))||found.getLong(3)!=model
                        ||!frozen.datasetSha().equals(found.getString(4))||!receipt.sha256().equals(found.getString(5))||!sample.selection().sha256().equals(found.getString(6))
                        ||!(model==0?"0".repeat(64):found.getString(11)).equals(found.getString(7))||!sample.selection().split().equals(found.getString(8))||!role.equals(found.getString(9))
                        ||found.getBoolean(10)!=(model>0&&role.equals("GRADIENT")))
                    {throw new IllegalArgumentException("007 application/model/Adam proof changed");}
                }
            }
        }
    }
    public ActionAdvantageV1.Frozen freezeAdvantage() throws SQLException,IOException
    {
        requireStopped();if(!advantageMode()){throw new IllegalArgumentException("007 objective opt-in required");}
        final var samples=advantageSources();
        return transaction(connection ->
        {
            try(var query=connection.createStatement();var found=query.executeQuery("SELECT run_id,dataset_sha,snapshot_blob FROM phantom_tactical_v3_runs WHERE state='FROZEN' ORDER BY created_at,run_id LIMIT 1 FOR UPDATE"))
            {if(found.next()){return advantageSnapshot(connection,found.getString(1),found.getString(2),found.getBytes(3),0);}}
            if(samples.isEmpty()){return null;}
            final var unused=new ArrayList<ActionAdvantageV1.Sample>();
            for(var sample:samples)
            {
                try(var query=connection.prepareStatement("SELECT COUNT(*) FROM phantom_tactical_v3_advantage_applications WHERE natural_key=?"))
                {query.setString(1,sample.terminal().naturalKey());try(var found=query.executeQuery()){found.next();if(found.getLong(1)==0){unused.add(sample);}}}
            }
            if(unused.isEmpty()){return null;}
            final var frozen=ActionAdvantageV1.freeze(UUID.randomUUID().toString().replace("-",""),unused);
            if(!frozen.sufficient()){return null;}
            requireAdvantageRows(connection,frozen,null);
            final var rows=frozen.sourceRows();final byte[] sourceBytes=pack(rows);final String sourceSha=TacticalObservationV3.hash(sourceBytes);
            try(var insert=connection.prepareStatement("INSERT INTO phantom_tactical_v3_runs(run_id,dataset_sha,seed,snapshot_blob,state) VALUES(?,?,?,?,'FROZEN')"))
            {insert.setString(1,frozen.runId());insert.setString(2,sourceSha);insert.setLong(3,SEED);insert.setBytes(4,sourceBytes);insert.executeUpdate();}
            try(var insert=connection.prepareStatement("INSERT INTO phantom_tactical_v3_advantage_snapshots(run_id,target_sha,config_sha,snapshot_blob) VALUES(?,?,?,?)"))
            {insert.setString(1,frozen.runId());insert.setString(2,frozen.datasetSha());insert.setString(3,ActionAdvantageV1.CONFIG_SHA);insert.setBytes(4,frozen.bytes());insert.executeUpdate();}
            try(var reserve=connection.prepareStatement("INSERT INTO phantom_tactical_v3_advantage_applications(natural_key,action_key,run_id,model_id,target_sha,receipt_sha,selection_sha,optimizer_sha,split,fit_role,gradient_used) VALUES(?,?,?,0,?,?,?,?,?,?,FALSE)"))
            {
                for(var sample:frozen.samples())
                {reserve.setString(1,sample.terminal().naturalKey());reserve.setString(2,sample.actionKey());reserve.setString(3,frozen.runId());reserve.setString(4,frozen.datasetSha());reserve.setString(5,sample.terminal().sha256());reserve.setString(6,sample.selection().sha256());reserve.setString(7,"0".repeat(64));reserve.setString(8,sample.selection().split());reserve.setString(9,advantageRole(frozen,sample));reserve.addBatch();}
                reserve.executeBatch();
            }
            final var contexts=new ArrayList<TacticalReplayV3.ContextSample>();
            for(var sample:frozen.samples()){for(var action:sample.terminal().actions())
            {var row=action.row();contexts.add(new TacticalReplayV3.ContextSample(row.eventKey(),"EFFECT",sample.terminal().episode().outcome(),TacticalReplayV3.spot(row.contextKey()),hashBytes(encode(row)),sample.terminal().episode().id(),sample.terminal().episode().sha256()));}}
            final byte[] contextBytes=TacticalReplayV3.packContexts(contexts,sourceSha);
            try(var insert=connection.prepareStatement("INSERT INTO phantom_tactical_v3_context_replay(run_id,dataset_sha,sample_sha,sample_blob) VALUES(?,?,?,?)"))
            {insert.setString(1,frozen.runId());insert.setString(2,sourceSha);insert.setString(3,hashBytes(contextBytes));insert.setBytes(4,contextBytes);insert.executeUpdate();}
            try(var insert=connection.prepareStatement("INSERT INTO phantom_tactical_v3_audit_used(cohort_hash,run_id) VALUES(?,?)"))
            {for(String cohort:rows.stream().filter(row->row.split().equals("AUDIT")).map(Row::cohort).distinct().toList()){insert.setString(1,cohort);insert.setString(2,frozen.runId());insert.addBatch();}insert.executeBatch();}
            return frozen;
        });
    }
    private static ActionAdvantageV1.Frozen advantageSnapshot(Connection connection,String run,String sourceSha,byte[] sourceBytes,long model) throws SQLException
    {
        try(var query=connection.prepareStatement("SELECT target_sha,config_sha,snapshot_blob FROM phantom_tactical_v3_advantage_snapshots WHERE run_id=?"))
        {
            query.setString(1,run);try(var found=query.executeQuery())
            {
                if(!found.next()||!ActionAdvantageV1.CONFIG_SHA.equals(found.getString(2))){throw new IllegalArgumentException("007 frozen objective missing/changed");}
                var frozen=ActionAdvantageV1.restore(run,found.getBytes(3),found.getString(1));
                if(!frozen.sufficient()||!sourceSha.equals(hashBytes(sourceBytes))||!Arrays.equals(sourceBytes,pack(frozen.sourceRows()))){throw new IllegalArgumentException("007 source/target dataset changed");}
                requireAdvantageRows(connection,frozen,model);return frozen;
            }
        }
    }
    public ActionAdvantageV1.Frozen advantageSnapshot(long model) throws SQLException
    {
        try(var connection=_guard.openTacticalConnection();var query=connection.prepareStatement("SELECT m.run_id,m.dataset_sha,r.snapshot_blob FROM phantom_tactical_v3_models m JOIN phantom_tactical_v3_runs r ON r.run_id=m.run_id WHERE m.model_id=?"))
        {query.setLong(1,model);try(var found=query.executeQuery()){if(!found.next()){throw new IllegalArgumentException("007 model missing");}return advantageSnapshot(connection,found.getString(1),found.getString(2),found.getBytes(3),model);}}
    }
    public TerminalRewardReceiptV1.Config terminalConfig() throws IOException
    {
        if(!terminalMode()){throw new IllegalArgumentException("Explicit outcome006 installation required");}
        final Path path=_root.getParent().getParent().getParent().resolve("L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006-PVE-OUTCOME/reward-config.v1.json");
        final var config=TerminalRewardReceiptV1.Config.load(path);
        if(!config.sha256().equals(_guard.config().text("outcome006RewardConfigSha",""))){throw new IllegalArgumentException("Frozen terminal reward configuration changed");}
        return config;
    }
    public void beginTerminalEpisode(String id,String nonce,String scope,String facts,long generation,int instance,int target,long spawn,long cut) throws SQLException,IOException
    {
        terminalConfig();TrainingStore.validateObservation(facts);
        if(generation<=0||instance<0||target<=0||spawn<=0||cut<=0){throw new IllegalArgumentException("Native CUT identity required");}
        final String before="{\"installation\":"+TrainingStore.quote(hash(Files.readString(_root.resolve("owner.txt"))))+",\"ownerGeneration\":"+generation+",\"instanceId\":"+instance
            +",\"targetObjectId\":"+target+",\"spawnGeneration\":"+spawn+",\"nativeCutNanos\":"+cut+",\"facts\":"+facts+"}";
        beginEpisode(id,nonce,scope,before);
    }
    /** Called after native drain, never by an HP/MP writer or a Combat/Party hot callback. */
    public boolean stageTerminal(TerminalRewardReceiptV1 receipt) throws SQLException,IOException
    {
        final var config=terminalConfig();
        if(receipt==null||!config.sha256().equals(receipt.config().sha256())||!hash(Files.readString(_root.resolve("owner.txt"))).equals(receipt.boundary().installation())
            ||!receipt.dataset().equals(_guard.config().text("outcome006DatasetId","outcome006-v1"))){throw new IllegalArgumentException("Foreign terminal configuration/installation/dataset");}
        final Path attempt=_root.resolve("attempts").resolve(receipt.boundary().ownerNonce());
        if(!attempt.equals(attempt.toRealPath())||Files.isSymbolicLink(attempt)){throw new IOException("Physical native attempt required");}
        final Path nativePath=_root.resolve("native-process.json");
        if(!nativePath.equals(nativePath.toRealPath())||Files.size(nativePath)>65536){throw new IOException("Bounded native process proof required");}
        final String nativeRecord=Files.readString(nativePath);TrainingStore.validateObservation(nativeRecord);
        requireTerminalString(nativeRecord,"nonce",receipt.boundary().ownerNonce());requireTerminalString(nativeRecord,"kind","Native");requireTerminalNumber(nativeRecord,"pid",ProcessHandle.current().pid());
        return transaction(connection ->
        {
            requireTerminalNative(connection,receipt);
            try(var query=connection.prepareStatement("SELECT receipt_sha,receipt_blob FROM phantom_tactical_v3_terminal_rewards WHERE natural_key=? FOR UPDATE"))
            {
                query.setString(1,receipt.naturalKey());try(var found=query.executeQuery())
                {if(found.next()){if(!receipt.sha256().equals(found.getString(1))||!Arrays.equals(receipt.bytes(),found.getBytes(2))){throw new IllegalArgumentException("Native terminal credit was repackaged");}return false;}}
            }
            try(var query=connection.createStatement();var found=query.executeQuery("SELECT COUNT(*) FROM phantom_tactical_v3_terminal_rewards"))
            {if(!found.next()||found.getLong(1)>=_guard.config().integer("retentionEpisodes",10000,100,100000)){throw new IllegalArgumentException("Terminal ledger retention capacity reached; preserve export");}}
            try(var insert=connection.prepareStatement("INSERT INTO phantom_tactical_v3_terminal_rewards(natural_key,goal_key,episode_id,owner_nonce,split,dataset_id,config_sha,receipt_sha,receipt_blob) VALUES(?,?,?,?,?,?,?,?,?)"))
            {
                insert.setString(1,receipt.naturalKey());insert.setString(2,receipt.goalKey());insert.setString(3,receipt.episode().id());insert.setString(4,receipt.boundary().ownerNonce());insert.setString(5,receipt.split());
                insert.setString(6,receipt.dataset());insert.setString(7,receipt.config().sha256());insert.setString(8,receipt.sha256());insert.setBytes(9,receipt.bytes());
                if(insert.executeUpdate()!=1){throw new SQLException("Terminal receipt not staged");}
            }
            return true;
        });
    }
    private static void requireTerminalString(String json,String key,String value)
    {
        final var match=java.util.regex.Pattern.compile("\""+java.util.regex.Pattern.quote(key)+"\"\\s*:\\s*"+java.util.regex.Pattern.quote(TrainingStore.quote(value))).matcher(json);
        if(!match.find()||match.find()){throw new IllegalArgumentException("Native terminal string provenance: "+key);}
    }
    private static void requireTerminalNumber(String json,String key,long value)
    {
        final var match=java.util.regex.Pattern.compile("\""+java.util.regex.Pattern.quote(key)+"\"\\s*:\\s*([0-9]+)(?=\\s*[,}])").matcher(json);
        if(!match.find()||!Long.toString(value).equals(match.group(1))||match.find()){throw new IllegalArgumentException("Native terminal numeric provenance: "+key);}
    }
    private static void requireTerminalNative(Connection connection,TerminalRewardReceiptV1 receipt) throws SQLException
    {
        try(var query=connection.prepareStatement("SELECT owner_nonce,state,before_json,before_sha,settlement_json,settlement_sha FROM phantom_tactical_v3_episodes WHERE episode_id=?"))
        {
            query.setString(1,receipt.episode().id());try(var found=query.executeQuery())
            {
                if(!found.next()||!receipt.boundary().ownerNonce().equals(found.getString(1))||!receipt.episode().outcome().equals(found.getString(2))
                    ||!hash(found.getString(3)).equals(found.getString(4))||!receipt.episode().json().equals(found.getString(5))||!receipt.episode().sha256().equals(found.getString(6)))
                {throw new IllegalArgumentException("Canonical terminal settlement changed");}
                final String before=found.getString(3);final var boundary=receipt.boundary();
                requireTerminalString(before,"installation",boundary.installation());requireTerminalNumber(before,"ownerGeneration",boundary.ownerGeneration());
                requireTerminalNumber(before,"instanceId",boundary.instanceId());requireTerminalNumber(before,"targetObjectId",boundary.targetObjectId());requireTerminalNumber(before,"spawnGeneration",boundary.spawnGeneration());requireTerminalNumber(before,"nativeCutNanos",boundary.cutNanos());
            }
        }
        for(var action:receipt.actions())
        {
            final var row=action.row();final byte[] bytes=encode(row);
            try(var query=connection.prepareStatement("SELECT e.receipt_sha,e.receipt_blob,e.split,e.actor_profile,l.episode_id FROM phantom_tactical_v3_events e JOIN phantom_tactical_v3_event_episodes l ON l.event_key=e.event_key WHERE e.event_key=?"))
            {
                query.setString(1,row.eventKey());try(var found=query.executeQuery())
                {
                    if(!found.next()||!TacticalObservationV3.hash(bytes).equals(found.getString(1))||!Arrays.equals(bytes,found.getBytes(2))||!receipt.split().equals(found.getString(3))
                        ||row.actorProfile()!=found.getLong(4)||!receipt.episode().id().equals(found.getString(5))){throw new IllegalArgumentException("Terminal action lacks its exact committed native effect");}
                }
            }
        }
    }
    private static void requireTerminalRows(Connection connection,TerminalReplayV1.Frozen frozen,Long appliedModel) throws SQLException
    {
        for(var receipt:frozen.receipts())
        {
            requireTerminalNative(connection,receipt);
            try(var query=connection.prepareStatement("SELECT r.receipt_sha,r.receipt_blob,a.run_id,a.model_id,a.dataset_sha,a.receipt_sha,a.optimizer_sha,a.fit_role,a.terminal_reward_used,o.optimizer_sha FROM phantom_tactical_v3_terminal_rewards r LEFT JOIN phantom_tactical_v3_terminal_applications a ON a.natural_key=r.natural_key LEFT JOIN phantom_tactical_v3_optimizers o ON o.model_id=a.model_id WHERE r.natural_key=?"))
            {
                query.setString(1,receipt.naturalKey());try(var found=query.executeQuery())
                {
                    if(!found.next()||!receipt.sha256().equals(found.getString(1))||!Arrays.equals(receipt.bytes(),found.getBytes(2))){throw new IllegalArgumentException("Frozen terminal receipt changed");}
                    if(appliedModel==null){if(found.getString(3)!=null){throw new IllegalArgumentException("Terminal credit already reserved/consumed by a fit");}}
                    else if(appliedModel==0)
                    {
                        if(!frozen.runId().equals(found.getString(3))||found.getLong(4)!=0||!frozen.datasetSha().equals(found.getString(5))||!receipt.sha256().equals(found.getString(6))
                            ||!"0".repeat(64).equals(found.getString(7))||!receipt.split().equals(found.getString(8))||found.getBoolean(9)){throw new IllegalArgumentException("Unapplied terminal reservation changed");}
                    }
                    else if(!frozen.runId().equals(found.getString(3))||found.getLong(4)!=appliedModel||!frozen.datasetSha().equals(found.getString(5))||!receipt.sha256().equals(found.getString(6))
                        ||!Objects.equals(found.getString(7),found.getString(10))||!receipt.split().equals(found.getString(8))||found.getBoolean(9)!=receipt.split().equals("TRAIN"))
                    {throw new IllegalArgumentException("Terminal optimizer application proof changed");}
                }
            }
        }
    }
    public TerminalReplayV1.Frozen freezeTerminal() throws SQLException,IOException
    {
        requireStopped();final var config=terminalConfig();
        return transaction(connection ->
        {
            try(var query=connection.createStatement();var found=query.executeQuery("SELECT run_id,dataset_sha,snapshot_blob FROM phantom_tactical_v3_runs WHERE state='FROZEN' ORDER BY created_at,run_id LIMIT 1 FOR UPDATE"))
            {
                if(found.next())
                {
                    final var frozen=terminalSnapshot(connection,found.getString(1),found.getString(2),found.getBytes(3),0);
                    if(!frozen.configSha().equals(config.sha256())||!frozen.datasetId().equals(_guard.config().text("outcome006DatasetId","outcome006-v1"))){throw new IllegalArgumentException("Frozen terminal campaign pins changed");}
                    return frozen;
                }
            }
            final var receipts=new ArrayList<TerminalRewardReceiptV1>();int examples=0;
            try(var query=connection.prepareStatement("SELECT r.receipt_sha,r.receipt_blob FROM phantom_tactical_v3_terminal_rewards r LEFT JOIN phantom_tactical_v3_terminal_applications a ON a.natural_key=r.natural_key WHERE a.natural_key IS NULL AND r.config_sha=? AND r.dataset_id=? ORDER BY r.created_at,r.natural_key LIMIT 1024"))
            {
                query.setString(1,config.sha256());query.setString(2,_guard.config().text("outcome006DatasetId","outcome006-v1"));
                try(var found=query.executeQuery())
                {
                    while(found.next()){final var receipt=TerminalRewardReceiptV1.restore(found.getBytes(2),found.getString(1));if(examples+receipt.actions().size()>TacticalModelV3.MAX_EXAMPLES){break;}receipts.add(receipt);examples+=receipt.actions().size();}
                }
            }
            if(receipts.isEmpty()){return null;}
            final var frozen=TerminalReplayV1.freeze(UUID.randomUUID().toString().replace("-",""),receipts);
            if(!frozen.sufficient()){return null;}
            requireTerminalRows(connection,frozen,null);
            final var rows=frozen.sourceRows();final byte[] sourceBytes=pack(rows);final String sourceSha=TacticalObservationV3.hash(sourceBytes);
            try(var insert=connection.prepareStatement("INSERT INTO phantom_tactical_v3_runs(run_id,dataset_sha,seed,snapshot_blob,state) VALUES(?,?,?,?,'FROZEN')"))
            {insert.setString(1,frozen.runId());insert.setString(2,sourceSha);insert.setLong(3,SEED);insert.setBytes(4,sourceBytes);insert.executeUpdate();}
            try(var reserve=connection.prepareStatement("INSERT INTO phantom_tactical_v3_terminal_applications(natural_key,run_id,model_id,dataset_sha,receipt_sha,optimizer_sha,fit_role,terminal_reward_used) VALUES(?,?,0,?,?,?, ?,FALSE)"))
            {
                for(var receipt:frozen.receipts())
                {reserve.setString(1,receipt.naturalKey());reserve.setString(2,frozen.runId());reserve.setString(3,frozen.datasetSha());reserve.setString(4,receipt.sha256());reserve.setString(5,"0".repeat(64));reserve.setString(6,receipt.split());reserve.addBatch();}
                reserve.executeBatch();
            }
            final var contexts=new ArrayList<TacticalReplayV3.ContextSample>();
            for(var receipt:frozen.receipts()){for(var action:receipt.actions())
            {final var row=action.row();contexts.add(new TacticalReplayV3.ContextSample(row.eventKey(),"EFFECT",receipt.episode().outcome(),TacticalReplayV3.spot(row.contextKey()),TacticalObservationV3.hash(encode(row)),receipt.episode().id(),receipt.episode().sha256()));}}
            final byte[] contextBytes=TacticalReplayV3.packContexts(contexts,sourceSha);
            try(var insert=connection.prepareStatement("INSERT INTO phantom_tactical_v3_context_replay(run_id,dataset_sha,sample_sha,sample_blob) VALUES(?,?,?,?)"))
            {insert.setString(1,frozen.runId());insert.setString(2,sourceSha);insert.setString(3,TacticalObservationV3.hash(contextBytes));insert.setBytes(4,contextBytes);insert.executeUpdate();}
            try(var insert=connection.prepareStatement("INSERT INTO phantom_tactical_v3_audit_used(cohort_hash,run_id) VALUES(?,?)"))
            {for(String cohort:rows.stream().filter(row->row.split().equals("AUDIT")).map(Row::cohort).distinct().toList()){insert.setString(1,cohort);insert.setString(2,frozen.runId());insert.addBatch();}insert.executeBatch();}
            return frozen;
        });
    }
    private static TerminalReplayV1.Frozen terminalSnapshot(Connection connection,String run,String sourceSha,byte[] sourceBytes,long model) throws SQLException
    {
        final var receipts=new ArrayList<TerminalRewardReceiptV1>();String terminalSha=null;
        try(var query=connection.prepareStatement("SELECT r.receipt_sha,r.receipt_blob,a.dataset_sha FROM phantom_tactical_v3_terminal_applications a JOIN phantom_tactical_v3_terminal_rewards r ON r.natural_key=a.natural_key WHERE a.run_id=? ORDER BY a.natural_key"))
        {
            query.setString(1,run);try(var found=query.executeQuery())
            {
                while(found.next())
                {if(receipts.size()>=1024){throw new IllegalArgumentException("Terminal frozen receipt capacity");}if(terminalSha==null){terminalSha=found.getString(3);}else if(!terminalSha.equals(found.getString(3))){throw new IllegalArgumentException("Terminal target dataset identity changed");}receipts.add(TerminalRewardReceiptV1.restore(found.getBytes(2),found.getString(1)));}
            }
        }
        if(receipts.isEmpty()){throw new IllegalArgumentException("Frozen run has no terminal target reservations");}
        final var frozen=TerminalReplayV1.freeze(run,receipts);
        if(!frozen.datasetSha().equals(terminalSha)||!frozen.sufficient()||!Arrays.equals(pack(frozen.sourceRows()),sourceBytes)||!hashBytes(sourceBytes).equals(sourceSha))
        {throw new IllegalArgumentException("Frozen terminal targets/source observations changed");}
        requireTerminalRows(connection,frozen,model);return frozen;
    }
    private static String hashBytes(byte[] bytes){return TacticalObservationV3.hash(bytes);}
    public TerminalReplayV1.Frozen terminalSnapshot(long model) throws SQLException
    {
        try(var connection=_guard.openTacticalConnection();var query=connection.prepareStatement("SELECT m.run_id,m.dataset_sha,r.snapshot_blob FROM phantom_tactical_v3_models m JOIN phantom_tactical_v3_runs r ON r.run_id=m.run_id WHERE m.model_id=?"))
        {query.setLong(1,model);try(var found=query.executeQuery()){if(!found.next()){throw new IllegalArgumentException("Terminal model missing");}return terminalSnapshot(connection,found.getString(1),found.getString(2),found.getBytes(3),model);}}
    }

    public void beginEpisode(String id, String nonce, String scope, String before) throws SQLException, IOException
    {
        final Path attempt = _root.resolve("attempts").resolve(nonce);
        if (!id.matches("[a-f0-9]{64}") || !nonce.matches("[a-f0-9]{32}") || !Set.of("SOLO", "PARTY").contains(scope)
            || before == null || before.length() > 262144 || !attempt.equals(attempt.toRealPath()) || Files.isSymbolicLink(attempt))
        { throw new IllegalArgumentException("Physical own native episode required"); }
        final String sha = hash(before);
        transaction(connection ->
        {
            try (var insert = connection.prepareStatement("INSERT INTO phantom_tactical_v3_episodes(episode_id,owner_nonce,scope,state,before_json,before_sha) VALUES(?,?,?,'STARTED',?,?)"))
            { insert.setString(1,id); insert.setString(2,nonce); insert.setString(3,scope); insert.setString(4,before); insert.setString(5,sha); insert.executeUpdate(); }
            return null;
        });
    }
    public boolean settleEpisode(TacticalEpisodeV3 episode) throws SQLException
    {
        return transaction(connection ->
        {
            try (var query = connection.prepareStatement("SELECT owner_nonce,scope,state,settlement_sha,before_json,before_sha FROM phantom_tactical_v3_episodes WHERE episode_id=? FOR UPDATE"))
            {
                query.setString(1,episode.id());
                try (var found = query.executeQuery())
                {
                    if (!found.next() || !episode.ownerNonce().equals(found.getString(1)) || !episode.scope().equals(found.getString(2))
                        || !hash(found.getString(5)).equals(found.getString(6))) { throw new IllegalArgumentException("Native episode START authority changed"); }
                    if (!found.getString(3).equals("STARTED"))
                    {
                        if (episode.sha256().equals(found.getString(4))) { return false; }
                        throw new IllegalArgumentException("Episode settlement changed; double credit forbidden");
                    }
                }
            }
            for (String event : episode.eventKeys())
            {
                try (var query = connection.prepareStatement("SELECT actor_profile FROM phantom_tactical_v3_events WHERE event_key=?"))
                {
                    query.setString(1,event);
                    try (var found = query.executeQuery())
                    {
                        if (!found.next()) { throw new IllegalArgumentException("Episode event is not committed"); }
                        final long profile = found.getLong(1);
                        if (episode.actors().stream().noneMatch(actor -> actor.profile() == profile))
                        { throw new IllegalArgumentException("Episode event lacks its committed native participant receipt"); }
                    }
                }
                try (var insert = connection.prepareStatement("INSERT INTO phantom_tactical_v3_event_episodes(event_key,episode_id) VALUES(?,?)"))
                { insert.setString(1,event); insert.setString(2,episode.id()); insert.executeUpdate(); }
            }
            try (var update = connection.prepareStatement("UPDATE phantom_tactical_v3_episodes SET state=?,settlement_json=?,settlement_sha=? WHERE episode_id=? AND state='STARTED'"))
            {
                update.setString(1,episode.outcome()); update.setString(2,episode.json()); update.setString(3,episode.sha256()); update.setString(4,episode.id());
                if (update.executeUpdate() != 1) { throw new SQLException("Native episode settlement race"); }
            }
            return true;
        });
    }
    public void recoverEpisodes(String currentNonce) throws SQLException
    {
        if (!currentNonce.matches("[a-f0-9]{32}")) { throw new IllegalArgumentException("Own current native nonce required"); }
        transaction(connection ->
        {
            final String unknown = "{\"reason\":\"Interrupted before native SETTLE; terminal outcome UNKNOWN\"}";
            try (var update = connection.prepareStatement("UPDATE phantom_tactical_v3_episodes SET state='INCOMPLETE',settlement_json=?,settlement_sha=? WHERE state='STARTED' AND owner_nonce<>?"))
            { update.setString(1,unknown); update.setString(2,hash(unknown)); update.setString(3,currentNonce); update.executeUpdate(); }
            return null;
        });
    }

    /** The canonical fresh installer includes this same migration; reapplying it is idempotent. */
    public void initialize() throws IOException, SQLException
    {
        _guard.initialize();
        final Path clone = _root.getParent().getParent().getParent();
        final Path script = clone.resolve("L2J_Mobius_CT_2.6_HighFive/dist/db_installer/sql/game/phantom_tactical_v3.sql");
        if (!script.equals(script.toRealPath()) || Files.isSymbolicLink(script)) { throw new IOException("Physical V3 migration required"); }
        final String sql = Files.readString(script, StandardCharsets.UTF_8);
        try (var connection = _guard.openTacticalConnection(); var statement = connection.createStatement())
        {
            for (String part : sql.split(";"))
            {
                if (part.isBlank()) { continue; }
                if (!part.stripLeading().startsWith("CREATE TABLE IF NOT EXISTS `phantom_tactical_v3_") || part.matches("(?is).*\\b(?:DATABASE|GRANT|USE|USER)\\b.*"))
                { throw new IllegalArgumentException("Unexpected V3 migration statement"); }
                statement.executeUpdate(part);
            }
        }
    }

    private static void validateReceipt(TacticalReceiptV3 receipt)
    {
        final var attempt = receipt.attempt();
        final var observation = attempt.observation();
        if (observation.capturedNanos() <= 0 || attempt.modelVersion() < 0 || attempt.offered().size() < 2
            || receipt.owner() == null || receipt.owner().generation() <= 0 || receipt.owner().operationKey() == null
            || receipt.owner().operationKey().isBlank() || receipt.owner().operationKey().length() > 128
            || Math.abs(receipt.reward()) > 1 || receipt.beforeHp() < 0
            || Math.abs(receipt.actualHp() - (attempt.selected().family() == LegalActionV3.Family.HEAL
                ? receipt.afterHp() - receipt.beforeHp() : receipt.beforeHp() - receipt.afterHp())) > 1e-6)
        { throw new IllegalArgumentException("Incomplete native HEAL receipt"); }
        final var identities = new HashSet<LegalActionV3>();
        for (var action : attempt.offered())
        {
            if (action.family() != attempt.selected().family() || !action.trainable() || action.criticalSafety()
                || action.skillId() <= 0 || action.skillLevel() <= 0 || action.targetEpoch() <= 0 || !identities.add(action))
            { throw new IllegalArgumentException("HEAL alternatives are not distinct lawful nonemergency actions"); }
            action.features(observation);
        }
        final var maximum = observation.entity(attempt.selected().targetObjectId()).fact(TacticalObservationV3.Field.MAX_HP);
        if (!maximum.known() || maximum.value() <= 0 || receipt.afterHp() > maximum.value() + 1e-6
            || Math.abs(receipt.reward() - HealEvidenceV3.reward(receipt.actualHp(), maximum.value(), receipt.actualMp(), receipt.elapsedNanos())) > 1e-6)
        { throw new IllegalArgumentException("Native reward/HP does not match its preserved outcome"); }
    }

    public static byte[] encode(Row row)
    {
        try
        {
            final var buffer = new ByteArrayOutputStream(); final var out = new DataOutputStream(buffer);
            final boolean heal = row.receipt().attempt().selected().family() == LegalActionV3.Family.HEAL;
            out.writeInt(MAGIC); out.writeInt(heal ? ROW_VERSION : 5); out.writeUTF(TacticalObservationV3.SCHEMA); out.writeUTF(heal ? ACTION_SPACE : COMBAT_ACTION_SPACE);
            out.writeUTF(row.split()); out.writeUTF(row.contextKey()); out.writeUTF(row.eventKey());
            final var receipt = row.receipt(); final var attempt = receipt.attempt(); final byte[] observation = attempt.observation().bytes();
            out.writeInt(observation.length); out.write(observation); out.writeInt(attempt.offered().size());
            for (var action : attempt.offered()) { action.write(out); }
            out.writeInt(attempt.offered().indexOf(attempt.selected())); out.writeInt(attempt.offered().indexOf(attempt.baseline()));
            out.writeLong(attempt.modelVersion()); out.writeDouble(receipt.beforeHp()); out.writeDouble(receipt.afterHp());
            out.writeDouble(receipt.actualHp()); out.writeDouble(receipt.actualMp()); out.writeLong(receipt.elapsedNanos()); out.writeFloat(receipt.reward());
            out.writeLong(receipt.owner().generation()); out.writeUTF(receipt.owner().operationKey());
            out.flush(); final byte[] bytes = buffer.toByteArray();
            if (bytes.length > MAX_ROW) { throw new IllegalArgumentException("Native receipt exceeds bound"); }
            return bytes;
        }
        catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }
    public static Row decode(byte[] bytes)
    {
        if (bytes == null || bytes.length > MAX_ROW) { throw new IllegalArgumentException("Native receipt size"); }
        try
        {
            final var in = new DataInputStream(new ByteArrayInputStream(bytes));
            if (in.readInt() != MAGIC) { throw new IllegalArgumentException("Native receipt magic"); }
            final int version = in.readInt();
            if (version != ROW_VERSION && version != 5 || !TacticalObservationV3.SCHEMA.equals(in.readUTF()) || !(version == ROW_VERSION ? ACTION_SPACE : COMBAT_ACTION_SPACE).equals(in.readUTF()))
            { throw new IllegalArgumentException("Native receipt schema"); }
            final String split = in.readUTF(), context = in.readUTF(), key = in.readUTF();
            final int length = in.readInt(); if (length <= 0 || length > 524288 || length > in.available()) { throw new IllegalArgumentException("Native observation size"); }
            final var observation = TacticalObservationV3.restore(in.readNBytes(length));
            final int count = in.readInt(); if (count < 2 || count > 16) { throw new IllegalArgumentException("Native action bound"); }
            final var offered = new ArrayList<LegalActionV3>();
            for (int i = 0; i < count; i++) { offered.add(LegalActionV3.read(in)); }
            final int chosen = in.readInt(), baseline = in.readInt();
            if (chosen < 0 || chosen >= count || baseline < 0 || baseline >= count) { throw new IllegalArgumentException("Native action index"); }
            final var attempt = new TacticalReceiptV3.Attempt(observation, offered, offered.get(chosen), offered.get(baseline), in.readLong());
            if ((version == ROW_VERSION) != (attempt.selected().family() == LegalActionV3.Family.HEAL)) { throw new IllegalArgumentException("Native receipt family/version mismatch"); }
            final double before = in.readDouble(), after = in.readDouble(), hp = in.readDouble(), mp = in.readDouble();
            final long nanos = in.readLong(); final float reward = in.readFloat();
            final var owner = new HealEvidenceV3.SourceOwner(in.readLong(), in.readUTF());
            final var receipt = new TacticalReceiptV3(key, attempt, before, after, hp, mp, nanos, reward, owner);
            if (in.available() != 0) { throw new IllegalArgumentException("Trailing native receipt bytes"); }
            return new Row(split, context, receipt);
        }
        catch (IOException failure) { throw new IllegalArgumentException("Invalid native receipt", failure); }
    }
    public static List<Row> validateDataset(List<Row> rows)
    {
        if (rows == null || rows.size() > TacticalModelV3.MAX_EXAMPLES) { throw new IllegalArgumentException("Native dataset bound"); }
        final var actors = new HashMap<Long,String>(); final var contexts = new HashMap<String,String>();
        final var keys = new HashSet<String>(); final var actions = new HashSet<String>();
        for (Row row : rows)
        {
            if (row == null || !keys.add(row.eventKey()) || !actions.add(row.actionKey())) { throw new IllegalArgumentException("Duplicate native event/action"); }
            final String actorSplit = actors.putIfAbsent(row.actorProfile(), row.split()), contextSplit = contexts.putIfAbsent(row.contextKey(), row.split());
            if (actorSplit != null && !actorSplit.equals(row.split()) || contextSplit != null && !contextSplit.equals(row.split()))
            { throw new IllegalArgumentException("Native actor/context overlaps dataset splits"); }
        }
        return List.copyOf(rows);
    }
    static byte[] pack(List<Row> rows)
    {
        validateDataset(rows);
        try
        {
            final var buffer = new ByteArrayOutputStream(); final var out = new DataOutputStream(buffer);
            out.writeInt(rows.size());
            for (var row : rows.stream().sorted(java.util.Comparator.comparing(Row::eventKey)).toList())
            { final var bytes = encode(row); out.writeInt(bytes.length); out.write(bytes); if (buffer.size() > MAX_DATASET) { throw new IllegalArgumentException("Frozen dataset size"); } }
            out.flush(); return buffer.toByteArray();
        }
        catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }
    static List<Row> unpack(byte[] bytes)
    {
        if (bytes == null || bytes.length > MAX_DATASET) { throw new IllegalArgumentException("Frozen dataset size"); }
        try
        {
            final var in = new DataInputStream(new ByteArrayInputStream(bytes));
            final int count = in.readInt(); if (count < 0 || count > TacticalModelV3.MAX_EXAMPLES) { throw new IllegalArgumentException("Frozen dataset count"); }
            final var rows = new ArrayList<Row>();
            for (int i = 0; i < count; i++)
            {
                final int length = in.readInt(); if (length <= 0 || length > MAX_ROW || length > in.available()) { throw new IllegalArgumentException("Frozen row size"); }
                rows.add(decode(in.readNBytes(length)));
            }
            if (in.available() != 0) { throw new IllegalArgumentException("Trailing frozen dataset"); }
            return validateDataset(rows);
        }
        catch (IOException failure) { throw new IllegalArgumentException("Invalid frozen dataset", failure); }
    }
    @FunctionalInterface private interface SqlOperation<T> { T run(Connection connection) throws SQLException; }
    private <T> T transaction(SqlOperation<T> operation) throws SQLException
    {
        try (var connection = _guard.openTacticalConnection())
        {
            connection.setAutoCommit(false);
            try { final T result = operation.run(connection); connection.commit(); return result; }
            catch (SQLException | RuntimeException failure) { connection.rollback(); throw failure; }
        }
    }
    /** Called by the batch owner after native episode drain; identical retries receive no second credit. */
    public boolean record(HealEvidenceV3.Receipt receipt, String split, String contextKey) throws SQLException
    { return record(TacticalReceiptV3.fromHeal(receipt), split, contextKey); }
    public boolean record(TacticalReceiptV3 receipt, String split, String contextKey) throws SQLException
    {
        if (receipt != null && receipt.owner() != null && "CONTRACT_FIXTURE".equals(receipt.owner().operationKey()))
        { throw new IllegalArgumentException("Synthetic contract receipt cannot become NATIVE_HEAL"); }
        final Row row = new Row(split, contextKey, receipt); final byte[] bytes = encode(row); final String sha = TacticalObservationV3.hash(bytes);
        return transaction(connection ->
        {
            try (var query = connection.prepareStatement("SELECT receipt_sha FROM phantom_tactical_v3_events WHERE event_key=? FOR UPDATE"))
            {
                query.setString(1, row.eventKey());
                try (var found = query.executeQuery())
                {
                    if (found.next())
                    {
                        if (!sha.equals(found.getString(1))) { throw new IllegalArgumentException("Native event retry changed immutable bytes"); }
                        return false;
                    }
                }
            }
            bindSplit(connection, "ACTOR", hash(Long.toString(row.actorProfile())), split);
            bindSplit(connection, "CONTEXT", row.contextHash(), split);
            requireLedgerCapacity(connection);
            try (var query = connection.prepareStatement("SELECT event_key FROM phantom_tactical_v3_events WHERE action_key=? FOR UPDATE"))
            {
                query.setString(1, row.actionKey());
                try (var found = query.executeQuery()) { if (found.next()) { throw new IllegalArgumentException("Native action already has a durable event"); } }
            }
            try (var insert = connection.prepareStatement("INSERT INTO phantom_tactical_v3_events(event_key,action_key,actor_profile,split,context_hash,origin,receipt_sha,receipt_blob) VALUES(?,?,?,?,?,?,?,?)"))
            {
                insert.setString(1,row.eventKey()); insert.setString(2,row.actionKey()); insert.setLong(3,row.actorProfile()); insert.setString(4,split);
                insert.setString(5,row.contextHash()); insert.setString(6,receipt.origin()); insert.setString(7,sha); insert.setBytes(8,bytes); insert.executeUpdate();
            }
            return true;
        });
    }
    public boolean recordFailure(TacticalFailureV3 failure, String split, String contextKey, String episodeId) throws SQLException
    {
        final var row = new TacticalReplayV3.FailureRow(split,contextKey,failure);
        if (failure.owner() != null && failure.owner().operationKey().equals("CONTRACT_FIXTURE"))
        { throw new IllegalArgumentException("Synthetic failure cannot become a native observation"); }
        if (episodeId != null && !episodeId.matches("[a-f0-9]{64}")) { throw new IllegalArgumentException("Invalid native failure episode"); }
        final byte[] bytes = failure.bytes(); final String sha = failure.sha256();
        return transaction(connection ->
        {
            try (var query = connection.prepareStatement("SELECT receipt_sha,split,context_key,episode_id FROM phantom_tactical_v3_failures WHERE event_key=? FOR UPDATE"))
            {
                query.setString(1,failure.eventKey());
                try (var found = query.executeQuery())
                {
                    if (found.next())
                    {
                        if (!sha.equals(found.getString(1)) || !split.equals(found.getString(2)) || !contextKey.equals(found.getString(3)) || !java.util.Objects.equals(episodeId,found.getString(4)))
                        { throw new IllegalArgumentException("Immutable failure retry changed"); }
                        return false;
                    }
                }
            }
            bindSplit(connection,"ACTOR",hash(Long.toString(failure.choice().observation().profileId())),split);
            bindSplit(connection,"CONTEXT",hash(contextKey),split);
            requireLedgerCapacity(connection);
            if (episodeId != null)
            {
                try (var query = connection.prepareStatement("SELECT episode_id FROM phantom_tactical_v3_episodes WHERE episode_id=?"))
                { query.setString(1,episodeId); try (var found = query.executeQuery()) { if (!found.next()) { throw new IllegalArgumentException("Failure has no own episode START"); } } }
            }
            try (var insert = connection.prepareStatement("INSERT INTO phantom_tactical_v3_failures(event_key,actor_profile,split,context_key,family,reason,receipt_sha,receipt_blob,episode_id) VALUES(?,?,?,?,?,?,?,?,?)"))
            {
                insert.setString(1,failure.eventKey()); insert.setLong(2,failure.choice().observation().profileId()); insert.setString(3,row.split()); insert.setString(4,row.contextKey());
                insert.setString(5,failure.choice().selected().family().name()); insert.setString(6,failure.reason()); insert.setString(7,sha); insert.setBytes(8,bytes);
                insert.setString(9,episodeId); insert.executeUpdate();
            }
            return true;
        });
    }
    private void requireLedgerCapacity(Connection connection) throws SQLException
    {
        final int maximum = _guard.config().integer("maxLedgerEvents",262144,4096,1000000);
        try (var query = connection.createStatement(); var found = query.executeQuery("SELECT (SELECT COUNT(*) FROM phantom_tactical_v3_events)+(SELECT COUNT(*) FROM phantom_tactical_v3_failures)"))
        { if (!found.next() || found.getLong(1) >= maximum) { throw new IllegalArgumentException("V3 observation capacity reached; export retained ledger before continuing"); } }
    }
    private static void bindSplit(Connection connection, String kind, String identity, String split) throws SQLException
    {
        try (var insert = connection.prepareStatement("INSERT IGNORE INTO phantom_tactical_v3_splits(identity_kind,identity_hash,split) VALUES(?,?,?)"))
        { insert.setString(1,kind); insert.setString(2,identity); insert.setString(3,split); insert.executeUpdate(); }
        try (var query = connection.prepareStatement("SELECT split FROM phantom_tactical_v3_splits WHERE identity_kind=? AND identity_hash=? FOR UPDATE"))
        {
            query.setString(1,kind); query.setString(2,identity);
            try (var found = query.executeQuery())
            { if (!found.next() || !split.equals(found.getString(1))) { throw new IllegalArgumentException("Native actor/context split was already frozen"); } }
        }
    }
    public long nativeSampleCount() throws SQLException
    {
        try (var connection = _guard.openTacticalConnection(); var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM phantom_tactical_v3_events WHERE origin IN ('NATIVE_HEAL','NATIVE_DAMAGE')"))
        { if (!rows.next()) { throw new SQLException("Native sample count missing"); } return rows.getLong(1); }
    }
    public Set<String> recordedContextHashes() throws SQLException
    {
        final var result = new HashSet<String>();
        try (var connection = _guard.openTacticalConnection(); var query = connection.createStatement(); var found = query.executeQuery("SELECT identity_hash FROM phantom_tactical_v3_splits WHERE identity_kind='CONTEXT'"))
        { while (found.next()) { result.add(found.getString(1)); } }
        return Set.copyOf(result);
    }
    public Set<String> recordedSceneIds() throws SQLException
    {
        final var result=new HashSet<String>();
        try(var connection=_guard.openTacticalConnection();var query=connection.createStatement();var rows=query.executeQuery("SELECT receipt_blob,receipt_sha FROM phantom_tactical_v3_events"))
        {
            while(rows.next())
            {
                final byte[] bytes=rows.getBytes(1);
                if(!hashBytes(bytes).equals(rows.getString(2))){throw new IllegalArgumentException("Historical scene receipt changed");}
                final var match=java.util.regex.Pattern.compile("(?:^|\\|)scene=([a-f0-9]{64})(?:\\||$)").matcher(decode(bytes).contextKey());
                if(match.find()){result.add(match.group(1));}
            }
        }
        return Set.copyOf(result);
    }
    public List<Row> examples() throws SQLException { return dataset(); }
    public List<Row> dataset() throws SQLException
    { try (var connection = _guard.openTacticalConnection()) { return replay(connection, Set.of(), "").rows(); } }
    private static TacticalReplayV3 replay(Connection connection, Set<String> usedAudit, String prefix) throws SQLException
    {
        final var replay = new TacticalReplayV3(usedAudit, prefix);
        try (var statement = connection.createStatement())
        {
            statement.setFetchSize(128);
            try (var rows = statement.executeQuery("SELECT e.event_key,e.action_key,e.actor_profile,e.split,e.context_hash,e.origin,e.receipt_sha,e.receipt_blob,p.state,p.episode_id,p.settlement_json,p.settlement_sha,l.episode_id FROM phantom_tactical_v3_events e LEFT JOIN phantom_tactical_v3_event_episodes l ON l.event_key=e.event_key LEFT JOIN phantom_tactical_v3_episodes p ON p.episode_id=l.episode_id ORDER BY e.created_at,e.event_key"))
            {
            while (rows.next())
            {
                final byte[] bytes = rows.getBytes(8);
                if (bytes == null || bytes.length > MAX_ROW) { throw new IllegalArgumentException("Native row byte budget exceeded"); }
                final Row row = decode(bytes);
                if (!row.receipt().origin().equals(rows.getString(6)) || !row.eventKey().equals(rows.getString(1)) || !row.actionKey().equals(rows.getString(2))
                    || row.actorProfile() != rows.getLong(3) || !row.split().equals(rows.getString(4)) || !row.contextHash().equals(rows.getString(5))
                    || !TacticalObservationV3.hash(bytes).equals(rows.getString(7)))
                { throw new IllegalArgumentException("Native SQL receipt/hash metadata changed"); }
                if (rows.getString(13) != null && rows.getString(9) == null) { throw new IllegalArgumentException("Dangling native episode link"); }
                final boolean terminal = rows.getString(9) != null && !rows.getString(9).equals("STARTED");
                if (terminal && (rows.getString(11) == null || !hash(rows.getString(11)).equals(rows.getString(12))))
                { throw new IllegalArgumentException("Native episode settlement hash changed"); }
                replay.offer(row,new TacticalReplayV3.ContextSample(row.eventKey(),"EFFECT",terminal ? rows.getString(9) : "ACTION_ONLY_UNKNOWN",
                    TacticalReplayV3.spot(row.contextKey()),rows.getString(7),terminal ? rows.getString(10) : null,terminal ? rows.getString(12) : null));
            }
            }
        }
        return replay;
    }
    private static TacticalReplayV3.FailureWindow failureReplay(Connection connection, String prefix) throws SQLException
    {
        final var window = new TacticalReplayV3.FailureWindow();
        try (var query = connection.createStatement(); var found = query.executeQuery("SELECT f.event_key,f.actor_profile,f.split,f.context_key,f.family,f.reason,f.receipt_sha,f.receipt_blob,p.state,p.episode_id,p.settlement_json,p.settlement_sha,f.episode_id FROM phantom_tactical_v3_failures f LEFT JOIN phantom_tactical_v3_episodes p ON p.episode_id=f.episode_id ORDER BY f.created_at,f.event_key"))
        {
            while (found.next())
            {
                final var failure = TacticalFailureV3.restore(found.getBytes(8),found.getString(7));
                if (!failure.eventKey().equals(found.getString(1)) || failure.choice().observation().profileId() != found.getLong(2)
                    || !failure.choice().selected().family().name().equals(found.getString(5)) || !failure.reason().equals(found.getString(6)))
                { throw new IllegalArgumentException("Native failure ledger metadata changed"); }
                if (!found.getString(4).startsWith(prefix)) { continue; }
                if (found.getString(13) != null && found.getString(9) == null) { throw new IllegalArgumentException("Dangling failure episode link"); }
                final boolean terminal = found.getString(9) != null && !found.getString(9).equals("STARTED");
                if (terminal && (found.getString(11) == null || !hash(found.getString(11)).equals(found.getString(12))))
                { throw new IllegalArgumentException("Failure episode settlement changed"); }
                final var row = new TacticalReplayV3.FailureRow(found.getString(3),found.getString(4),failure);
                window.offer(row,new TacticalReplayV3.ContextSample(failure.eventKey(),"FAILURE",
                    (terminal ? found.getString(9) : "ACTION_ONLY_UNKNOWN") + '_' + failure.outcome(),TacticalReplayV3.spot(row.contextKey()),failure.sha256(),
                    terminal ? found.getString(10) : null,terminal ? found.getString(12) : null));
            }
        }
        return window;
    }
    byte[] replayContexts(String run, String datasetSha, List<Row> rows) throws SQLException
    { try (var connection = _guard.openTacticalConnection()) { return requireReplayContexts(connection,run,datasetSha,rows); } }
    static byte[] requireReplayContexts(Connection connection, String run, String datasetSha, List<Row> rows) throws SQLException
    {
        final byte[] bytes;
        try (var query = connection.prepareStatement("SELECT dataset_sha,sample_sha,sample_blob FROM phantom_tactical_v3_context_replay WHERE run_id=?"))
        {
            query.setString(1,run);
            try (var found = query.executeQuery())
            {
                if (!found.next() || !datasetSha.equals(found.getString(1))) { throw new IllegalArgumentException("Missing frozen replay context"); }
                bytes = found.getBytes(3);
                if (bytes == null || !TacticalObservationV3.hash(bytes).equals(found.getString(2))) { throw new IllegalArgumentException("Frozen replay context checksum changed"); }
            }
        }
        final var effects = new HashMap<String,Row>(); rows.forEach(row -> effects.put(row.eventKey(),row));
        for (var sample : TacticalReplayV3.unpackContexts(bytes,datasetSha))
        {
            if (sample.kind().equals("EFFECT"))
            {
                final var row = effects.remove(sample.key());
                if (row == null || !TacticalObservationV3.hash(encode(row)).equals(sample.receiptSha()) || !TacticalReplayV3.spot(row.contextKey()).equals(sample.spot()))
                { throw new IllegalArgumentException("Frozen effect replay provenance changed"); }
                try (var query = connection.prepareStatement("SELECT e.receipt_sha,e.receipt_blob,l.episode_id FROM phantom_tactical_v3_events e LEFT JOIN phantom_tactical_v3_event_episodes l ON l.event_key=e.event_key WHERE e.event_key=?"))
                {
                    query.setString(1,sample.key());
                    try (var found = query.executeQuery())
                    {
                        if (!found.next() || !sample.receiptSha().equals(found.getString(1)) || !Arrays.equals(encode(row),found.getBytes(2))
                            || !java.util.Objects.equals(sample.episodeId(),found.getString(3)))
                        { throw new IllegalArgumentException("Frozen raw native effect changed"); }
                    }
                }
            }
            else
            {
                try (var query = connection.prepareStatement("SELECT receipt_blob,receipt_sha,split,context_key FROM phantom_tactical_v3_failures WHERE event_key=?"))
                {
                    query.setString(1,sample.key());
                    try (var found = query.executeQuery())
                    {
                        if (!found.next() || !sample.receiptSha().equals(found.getString(2)) || found.getString(3).equals("AUDIT")
                            || !TacticalReplayV3.spot(found.getString(4)).equals(sample.spot())
                            || !TacticalFailureV3.restore(found.getBytes(1),sample.receiptSha()).eventKey().equals(sample.key()))
                        { throw new IllegalArgumentException("Frozen failure replay provenance changed"); }
                    }
                }
            }
            if (sample.episodeId() != null)
            {
                try (var query = connection.prepareStatement("SELECT state,settlement_json,settlement_sha FROM phantom_tactical_v3_episodes WHERE episode_id=?"))
                {
                    query.setString(1,sample.episodeId());
                    try (var found = query.executeQuery())
                    {
                        if (!found.next() || found.getString(1).equals("STARTED") || !sample.outcome().startsWith(found.getString(1))
                            || !sample.settlementSha().equals(found.getString(3)) || !hash(found.getString(2)).equals(sample.settlementSha()))
                        { throw new IllegalArgumentException("Frozen episode replay provenance changed"); }
                    }
                }
            }
        }
        if (!effects.isEmpty()) { throw new IllegalArgumentException("Incomplete frozen effect contexts"); }
        return bytes;
    }
    java.util.Map<String,byte[]> replayFailures(byte[] contextBytes, String datasetSha) throws SQLException
    {
        final var result = new java.util.TreeMap<String,byte[]>();
        try (var connection = _guard.openTacticalConnection(); var query = connection.prepareStatement("SELECT receipt_blob FROM phantom_tactical_v3_failures WHERE event_key=?"))
        {
            for (var sample : TacticalReplayV3.unpackContexts(contextBytes,datasetSha))
            {
                if (!sample.kind().equals("FAILURE")) { continue; }
                query.setString(1,sample.key());
                try (var found = query.executeQuery())
                {
                    if (!found.next()) { throw new IllegalArgumentException("Missing exported failure"); }
                    final byte[] bytes = found.getBytes(1); TacticalFailureV3.restore(bytes,sample.receiptSha()); result.put(sample.key(),bytes);
                }
            }
        }
        return result;
    }
    Frozen freeze() throws SQLException
    {
        requireStopped();
        return transaction(connection ->
        {
            // A terminated offline worker has no model/optimizer commit. Resume its exact frozen cohort.
            try (var query = connection.createStatement(); var found = query.executeQuery("SELECT run_id,dataset_sha,snapshot_blob,seed FROM phantom_tactical_v3_runs WHERE state='FROZEN' ORDER BY created_at,run_id LIMIT 1 FOR UPDATE"))
            {
                if (found.next())
                {
                    final byte[] snapshot = found.getBytes(3);
                    if (found.getLong(4) != SEED || !TacticalObservationV3.hash(snapshot).equals(found.getString(2)))
                    { throw new IllegalArgumentException("Interrupted frozen V3 run changed"); }
                    final var resumed = unpack(snapshot);
                    if (!sufficient(resumed)) { throw new IllegalArgumentException("Interrupted V3 run incomplete"); }
                    requireReplayContexts(connection,found.getString(1),found.getString(2),resumed);
                    return new Frozen(found.getString(1), found.getString(2), resumed);
                }
            }
            final var used = new HashSet<String>();
            try (var query = connection.createStatement(); var found = query.executeQuery("SELECT cohort_hash FROM phantom_tactical_v3_audit_used"))
            { while (found.next()) { used.add(found.getString(1)); } }
            final var replay = replay(connection, used, _datasetContextPrefix);
            final var rows = replay.rows();
            if (!sufficient(rows)) { return null; }
            final byte[] snapshot = pack(rows); final String sha = TacticalObservationV3.hash(snapshot);
            final String id = UUID.randomUUID().toString().replace("-", "");
            try (var insert = connection.prepareStatement("INSERT INTO phantom_tactical_v3_runs(run_id,dataset_sha,seed,snapshot_blob,state) VALUES(?,?,?,?,'FROZEN')"))
            { insert.setString(1,id); insert.setString(2,sha); insert.setLong(3,SEED); insert.setBytes(4,snapshot); insert.executeUpdate(); }
            final var contexts = new ArrayList<>(replay.contexts());
            contexts.addAll(failureReplay(connection,_datasetContextPrefix).samples());
            final byte[] contextBytes = TacticalReplayV3.packContexts(contexts,sha);
            try (var insert = connection.prepareStatement("INSERT INTO phantom_tactical_v3_context_replay(run_id,dataset_sha,sample_sha,sample_blob) VALUES(?,?,?,?)"))
            { insert.setString(1,id); insert.setString(2,sha); insert.setString(3,TacticalObservationV3.hash(contextBytes)); insert.setBytes(4,contextBytes); insert.executeUpdate(); }
            try (var insert = connection.prepareStatement("INSERT INTO phantom_tactical_v3_audit_used(cohort_hash,run_id) VALUES(?,?)"))
            {
                for (String cohort : rows.stream().filter(row -> row.split().equals("AUDIT")).map(Row::cohort).distinct().toList())
                { insert.setString(1,cohort); insert.setString(2,id); insert.addBatch(); }
                insert.executeBatch();
            }
            return new Frozen(id,sha,rows);
        });
    }
    public static boolean sufficient(List<Row> rows)
    {
        validateDataset(rows);
        final var audit = rows.stream().filter(row -> row.split().equals("AUDIT")).toList();
        return rows.stream().filter(row -> row.split().equals("TRAIN")).count() >= 16
            && rows.stream().filter(row -> row.split().equals("TUNING")).count() >= 8
            && audit.stream().map(Row::actorProfile).distinct().count() >= 2 && audit.stream().map(Row::contextKey).distinct().count() >= 2;
    }
    void failed(Frozen frozen) throws SQLException
    {
        transaction(connection ->
        {
            try (var update = connection.prepareStatement("UPDATE phantom_tactical_v3_runs SET state='FAILED',reason='Offline evaluation failed; audit retained' WHERE run_id=? AND state='FROZEN'"))
            { update.setString(1,frozen.runId()); update.executeUpdate(); }
            return null;
        });
    }
    Checkpoint save(Frozen frozen, TacticalModelV3.IncrementalFit trained, Optimization parent, long championId, double auditLoss, double constantLoss, Double priorLoss, String metrics) throws SQLException
    {return save(frozen,trained,parent,championId,auditLoss,constantLoss,priorLoss,metrics,null);}
    public Checkpoint saveTerminal(TerminalReplayV1.Frozen terminal,TacticalModelV3.IncrementalFit trained,Optimization parent,Checkpoint champion,String metrics) throws SQLException
    {
        final var rows=terminal.sourceRows();
        final var source=new Frozen(terminal.runId(),TacticalObservationV3.hash(pack(rows)),rows);
        final var localAudit=rows.stream().filter(row->row.split().equals("AUDIT")).map(Row::example).toList();
        // Registry validation continues to describe immutable source receipts. Goal losses are explicitly separate in metrics.
        final double localLoss=trained.fit().model().loss(localAudit),localConstant=TacticalTrainerV3.constantLoss(rows);
        final Double localPrior=champion==null?null:champion.model().loss(localAudit);
        return save(source,trained,parent,champion==null?0:champion.id(),localLoss,localConstant,localPrior,metrics,terminal);
    }
    public Checkpoint saveAdvantage(ActionAdvantageV1.Frozen advantage,TacticalModelV3.IncrementalFit trained,Optimization parent,Checkpoint champion,String metrics) throws SQLException
    {
        var rows=advantage.sourceRows();var source=new Frozen(advantage.runId(),hashBytes(pack(rows)),rows);
        var audit=rows.stream().filter(row->row.split().equals("AUDIT")).map(Row::example).toList();
        return save(source,trained,parent,champion==null?0:champion.id(),trained.fit().model().loss(audit),TacticalTrainerV3.constantLoss(rows),champion==null?null:champion.model().loss(audit),metrics,null,advantage);
    }
    private Checkpoint save(Frozen frozen,TacticalModelV3.IncrementalFit trained,Optimization parent,long championId,double auditLoss,double constantLoss,Double priorLoss,String metrics,TerminalReplayV1.Frozen terminal) throws SQLException
    {return save(frozen,trained,parent,championId,auditLoss,constantLoss,priorLoss,metrics,terminal,null);}
    public Checkpoint saveResidual(ActionAdvantage008.Frozen residual,TacticalModelV3.IncrementalFit trained,Optimization parent,Checkpoint teacher,Checkpoint champion,String metrics) throws SQLException
    {
        var rows=residual.sourceRows();var source=new Frozen(residual.runId(),hashBytes(pack(rows)),rows);var audit=residual.examples("AUDIT");
        return save(source,trained,parent,champion==null?0:champion.id(),trained.fit().model().loss(audit),residual.constantLoss("AUDIT"),teacher.model().loss(audit),metrics,null,null,residual);
    }
    private Checkpoint save(Frozen frozen,TacticalModelV3.IncrementalFit trained,Optimization parent,long championId,double auditLoss,double constantLoss,Double priorLoss,String metrics,TerminalReplayV1.Frozen terminal,ActionAdvantageV1.Frozen advantage) throws SQLException
    {return save(frozen,trained,parent,championId,auditLoss,constantLoss,priorLoss,metrics,terminal,advantage,null);}
    private Checkpoint save(Frozen frozen,TacticalModelV3.IncrementalFit trained,Optimization parent,long championId,double auditLoss,double constantLoss,Double priorLoss,String metrics,TerminalReplayV1.Frozen terminal,ActionAdvantageV1.Frozen advantage,ActionAdvantage008.Frozen residual) throws SQLException
    {
        final var fit = trained.fit();
        final var continuation = trained.continuation();
        requireSuccessor(parent, continuation);
        final String status = "DRAFT";
        final byte[] weights = fit.model().bytes(); final String sha = fit.model().sha256();
        if (fit.epochs() < 0 || metrics == null || metrics.length() > 65536) { throw new IllegalArgumentException("Invalid V3 checkpoint/metrics"); }
        final long id = transaction(connection ->
        {
            final var current = latestOptimization(connection, true);
            if (current != null && (current.modelId() != parent.modelId() || !current.sha().equals(parent.sha())))
            { throw new IllegalArgumentException("Stale V3 optimizer parent; no candidate commit"); }
            if (current == null)
            {
                try (var query = connection.prepareStatement("SELECT sha256 FROM phantom_tactical_v3_models WHERE model_id=? AND status='ACCEPTED_TEST' FOR UPDATE"))
                {
                    query.setLong(1, parent.modelId());
                    try (var found = query.executeQuery())
                    { if (!found.next() || !found.getString(1).equals(parent.state().model().sha256()) || parent.state().updates() != 0)
                      { throw new IllegalArgumentException("Missing authenticated legacy optimizer parent"); } }
                }
            }
            try (var query = connection.prepareStatement("SELECT seed,dataset_sha,snapshot_blob,state FROM phantom_tactical_v3_runs WHERE run_id=? FOR UPDATE"))
            {
                query.setString(1,frozen.runId());
                try (var found = query.executeQuery())
                {
                    if (!found.next() || found.getLong(1) != SEED || !frozen.datasetSha().equals(found.getString(2)) || !Arrays.equals(pack(frozen.rows()),found.getBytes(3)) || !"FROZEN".equals(found.getString(4)))
                    { throw new IllegalArgumentException("Frozen native evaluation changed"); }
                }
            }
            requireReplayContexts(connection,frozen.runId(),frozen.datasetSha(),frozen.rows());
            if(terminal!=null)
            {
                if(!terminalMode()||!terminal.runId().equals(frozen.runId())||!Arrays.equals(pack(terminal.sourceRows()),pack(frozen.rows()))){throw new IllegalArgumentException("Terminal fit source changed");}
                requireTerminalRows(connection,terminal,0L);
            }
            else if(advantage!=null)
            {
                if(!advantageMode()||!advantage.runId().equals(frozen.runId())||!Arrays.equals(pack(advantage.sourceRows()),pack(frozen.rows()))){throw new IllegalArgumentException("007 fit source changed");}
                requireAdvantageRows(connection,advantage,0L);
            }
            else if(residual!=null)
            {
                if(!residualMode()||!residual.runId().equals(frozen.runId())||!Arrays.equals(pack(residual.sourceRows()),pack(frozen.rows()))){throw new IllegalArgumentException("008 fit source changed");}
                requireResidualRows(connection,residual,0L);
            }
            else if(current!=null)
            {
                try(var query=connection.prepareStatement("SELECT COUNT(*) FROM phantom_tactical_v3_terminal_applications WHERE model_id=?"))
                {query.setLong(1,current.modelId());try(var found=query.executeQuery()){if(found.next()&&found.getLong(1)>0){throw new IllegalArgumentException("Terminal optimizer cannot silently return to legacy action targets");}}}
            }
            try (var insert = connection.prepareStatement("INSERT INTO phantom_tactical_v3_models(run_id,algorithm,algorithm_hash,feature_schema,action_space,normalization,origin,status,dataset_sha,weights_blob,sha256,epochs,train_count,tuning_count,audit_count,audit_contexts,audit_mse,constant_mse,prior_mse,metrics_json) VALUES(?,?,?,?,?,?,'NATIVE_COMBAT',?,?,?,?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS))
            {
                insert.setString(1,frozen.runId()); insert.setInt(2,TacticalModelV3.ALGORITHM); insert.setString(3,TacticalModelV3.FINGERPRINT);
                insert.setString(4,TacticalObservationV3.SCHEMA); insert.setString(5,COMBAT_ACTION_SPACE); insert.setString(6,NORMALIZATION); insert.setString(7,status);
                insert.setString(8,frozen.datasetSha()); insert.setBytes(9,weights); insert.setString(10,sha); insert.setInt(11,fit.epochs());
                insert.setInt(12,count(frozen.rows(),"TRAIN")); insert.setInt(13,count(frozen.rows(),"TUNING")); insert.setInt(14,count(frozen.rows(),"AUDIT"));
                insert.setInt(15,(int) frozen.rows().stream().filter(row -> row.split().equals("AUDIT")).map(Row::cohort).distinct().count());
                insert.setDouble(16,auditLoss); insert.setDouble(17,constantLoss);
                if (priorLoss == null) { insert.setNull(18,java.sql.Types.DOUBLE); } else { insert.setDouble(18,priorLoss); }
                insert.setString(19,metrics); insert.executeUpdate();
                try (var keys = insert.getGeneratedKeys())
                {
                    if (!keys.next()) { throw new SQLException("V3 model identity missing"); }
                    final long model = keys.getLong(1);
                    try (var optimizer = connection.prepareStatement("INSERT INTO phantom_tactical_v3_optimizers(model_id,run_id,parent_model_id,parent_optimizer_sha,parent_weights_sha,optimizer_sha,optimizer_blob,updates,completed_epochs,seed,feature_schema,normalization,sampler_revision,champion_model_id) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)"))
                    {
                        optimizer.setLong(1,model); optimizer.setString(2,frozen.runId()); optimizer.setLong(3,parent.modelId());
                        optimizer.setString(4,parent.sha()); optimizer.setString(5,parent.state().model().sha256());
                        optimizer.setString(6,continuation.sha256()); optimizer.setBytes(7,continuation.bytes());
                        optimizer.setLong(8,continuation.updates()); optimizer.setLong(9,continuation.completedEpochs()); optimizer.setLong(10,continuation.seed());
                        optimizer.setString(11,TacticalObservationV3.SCHEMA); optimizer.setString(12,NORMALIZATION); optimizer.setString(13,residual!=null?ActionAdvantage008.REVISION:advantage!=null?ActionAdvantageV1.REVISION:terminal==null?TacticalReplayV3.REVISION:TerminalReplayV1.REVISION);
                        optimizer.setLong(14,championId);
                        if (optimizer.executeUpdate() != 1) { throw new SQLException("V3 optimizer checkpoint not committed"); }
                    }
                    if(terminal!=null)
                    {
                        try(var update=connection.prepareStatement("UPDATE phantom_tactical_v3_terminal_applications SET model_id=?,optimizer_sha=?,terminal_reward_used=? WHERE natural_key=? AND run_id=? AND model_id=0 AND dataset_sha=? AND receipt_sha=?"))
                        {
                            for(var receipt:terminal.receipts())
                            {
                                update.setLong(1,model);update.setString(2,continuation.sha256());update.setBoolean(3,receipt.split().equals("TRAIN"));update.setString(4,receipt.naturalKey());update.setString(5,terminal.runId());update.setString(6,terminal.datasetSha());update.setString(7,receipt.sha256());
                                if(update.executeUpdate()!=1){throw new SQLException("Terminal credit already committed or reservation changed");}
                            }
                        }
                    }
                    if(advantage!=null)
                    {
                        try(var update=connection.prepareStatement("UPDATE phantom_tactical_v3_advantage_applications SET model_id=?,optimizer_sha=?,gradient_used=? WHERE natural_key=? AND run_id=? AND model_id=0 AND target_sha=? AND receipt_sha=? AND selection_sha=?"))
                        {
                            for(var sample:advantage.samples())
                            {update.setLong(1,model);update.setString(2,continuation.sha256());update.setBoolean(3,advantageRole(advantage,sample).equals("GRADIENT"));update.setString(4,sample.terminal().naturalKey());update.setString(5,advantage.runId());update.setString(6,advantage.datasetSha());update.setString(7,sample.terminal().sha256());update.setString(8,sample.selection().sha256());if(update.executeUpdate()!=1){throw new SQLException("007 credit already committed or changed");}}
                        }
                    }
                    if(residual!=null)
                    {
                        try(var update=connection.prepareStatement("UPDATE phantom_tactical_v3_advantage_applications SET model_id=?,optimizer_sha=?,gradient_used=? WHERE natural_key=? AND run_id=? AND model_id=0 AND target_sha=? AND receipt_sha=? AND selection_sha=?"))
                        {
                            for(var sample:residual.samples())
                            {update.setLong(1,model);update.setString(2,continuation.sha256());update.setBoolean(3,residualRole(residual,sample).equals("GRADIENT"));update.setString(4,sample.terminal().naturalKey());update.setString(5,residual.runId());update.setString(6,residual.datasetSha());update.setString(7,sample.terminal().sha256());update.setString(8,sample.selection().sha256());if(update.executeUpdate()!=1){throw new SQLException("008 episode credit already committed or changed");}}
                        }
                    }
                    try (var update = connection.prepareStatement("UPDATE phantom_tactical_v3_runs SET state=? WHERE run_id=? AND state='FROZEN'"))
                    { update.setString(1,status); update.setString(2,frozen.runId()); if (update.executeUpdate() != 1) { throw new SQLException("V3 frozen state changed"); } }
                    if(advantage!=null){advantagePreCommitFault(advantage,model);}
                    return model;
                }
            }
        });
        return read(id,sha,false);
    }
    /** TEST-only controlled process failure after all writes and before transaction COMMIT. Never entered by native combat. */
    private void advantagePreCommitFault(ActionAdvantageV1.Frozen frozen,long model)
    {
        if(!Boolean.getBoolean("phantom.learning007.preCommitFault")){return;}
        if(!Boolean.getBoolean("phantom.learning.train")||!advantageMode()||!_guard.config().text("exploration007Phase","").equals("FIT"))
        {throw new IllegalArgumentException("007 fault requires explicit own offline TEST FIT");}
        try
        {
            final Path ready=advantageDirectory().resolve("precommit-"+frozen.runId()+".json");
            if(Files.exists(ready)){throw new IllegalArgumentException("007 fault already consumed; explicit no-fault resume required");}
            Files.writeString(ready,"{\"runId\":"+TrainingStore.quote(frozen.runId())+",\"modelId\":"+model+",\"pid\":"+ProcessHandle.current().pid()+",\"phase\":\"AFTER_MODEL_ADAM_APPLICATIONS_BEFORE_COMMIT\"}\n",StandardCharsets.UTF_8,java.nio.file.StandardOpenOption.CREATE_NEW);
            Runtime.getRuntime().halt(73);
        }
        catch(IOException failure){throw new IllegalStateException("007 fault evidence publication failed",failure);}
    }
    static void requireSuccessor(Optimization parent, TacticalModelV3.Optimizer next)
    {
        if (parent == null || next == null || parent.state() == null || parent.modelId() <= 0 || !parent.sha().equals(parent.state().sha256())
            || next.seed() != parent.state().seed() || next.seed() != SEED || next.updates() <= parent.state().updates()
            || next.completedEpochs() <= parent.state().completedEpochs())
        { throw new IllegalArgumentException("Invalid V3 optimizer successor"); }
    }
    static void validateOptimizationLink(Optimization parent, Optimization child, long declaredParent, String parentSha, String parentWeightsSha, String candidateRun)
    {
        requireSuccessor(parent, child.state());
        if (declaredParent != parent.modelId() || child.modelId() <= parent.modelId() || !parent.sha().equals(parentSha)
            || !parent.state().model().sha256().equals(parentWeightsSha) || !child.sha().equals(child.state().sha256())
            || child.runId() == null || !child.runId().matches("[a-f0-9]{32}") || !child.runId().equals(candidateRun))
        { throw new IllegalArgumentException("Committed V3 optimizer lineage changed"); }
    }
    public Optimization optimizationParent() throws SQLException
    {
        try (var connection = _guard.openTacticalConnection())
        {
            final var current = latestOptimization(connection, false);
            if (current != null) { return current; }
        }
        final var accepted = latestAccepted();
        if (accepted == null) { throw new IllegalStateException("Authenticated compatible accepted V3 parent required; random restart forbidden"); }
        final var initial = TacticalModelV3.startOptimization(accepted.model(), SEED);
        return new Optimization(accepted.id(), "MOMENTS_INITIALIZED", initial.sha256(), initial);
    }
    private record OptimizationBoundary(Optimization optimizer, long parentId, String parentSha, String parentWeightsSha, String candidateRun) { }
    Optimization latestOptimization(Connection connection, boolean lock) throws SQLException
    {
        long latest = 0;
        // Do not hide an orphaned latest row behind an INNER JOIN or silently fall back to older weights.
        try (var query = connection.createStatement(); var found = query.executeQuery("SELECT model_id FROM phantom_tactical_v3_optimizers ORDER BY model_id DESC LIMIT 1" + (lock ? " FOR UPDATE" : "")))
        { if (found.next()) { latest = found.getLong(1); } }
        try (var query = connection.createStatement(); var found = query.executeQuery("SELECT COALESCE(MAX(model_id),0) FROM phantom_tactical_v3_models WHERE origin='NATIVE_COMBAT'"))
        {
            if (!found.next() || found.getLong(1) != latest) { throw new IllegalArgumentException("Missing or dangling latest V3 optimizer checkpoint"); }
        }
        if (latest == 0) { return null; }
        final long head = latest;
        Optimization result = null;
        OptimizationBoundary child = null;
        // Walk downward with bounded live state; do not retain a network/moments copy per historical checkpoint.
        while (latest > 0)
        {
            final var boundary = optimizationBoundary(connection, latest, lock);
            if (boundary == null)
            {
                final var legacy = candidate(latest);
                if (child == null || legacy == null || !legacy.status().equals("ACCEPTED_TEST") || !legacy.origin().equals("NATIVE_HEAL"))
                { throw new IllegalArgumentException("V3 parent optimizer was deleted or changed"); }
                final var initial = TacticalModelV3.startOptimization(legacy.model(), SEED);
                final var parent = new Optimization(legacy.id(), "MOMENTS_INITIALIZED", initial.sha256(), initial);
                validateOptimizationLink(parent, child.optimizer(), child.parentId(), child.parentSha(), child.parentWeightsSha(), child.candidateRun());
                return result;
            }
            if (child != null)
            { validateOptimizationLink(boundary.optimizer(), child.optimizer(), child.parentId(), child.parentSha(), child.parentWeightsSha(), child.candidateRun()); }
            if (latest == head) { result = boundary.optimizer(); }
            child = boundary;
            if (boundary.parentId() <= 0 || boundary.parentId() >= latest) { throw new IllegalArgumentException("V3 parent identity does not precede child"); }
            latest = boundary.parentId();
        }
        throw new IllegalArgumentException("V3 optimizer chain has no authenticated legacy root");
    }
    private OptimizationBoundary optimizationBoundary(Connection connection, long id, boolean lock) throws SQLException
    {
        try (var query = connection.prepareStatement("SELECT model_id,run_id,optimizer_sha,optimizer_blob,updates,completed_epochs,seed,feature_schema,normalization,sampler_revision,parent_model_id,parent_optimizer_sha,parent_weights_sha FROM phantom_tactical_v3_optimizers WHERE model_id=?" + (lock ? " FOR UPDATE" : "")))
        {
            query.setLong(1,id);
            try (var found = query.executeQuery())
            {
                if (!found.next()) { return null; }
                final var state = TacticalModelV3.Optimizer.restore(found.getBytes(4), found.getString(3));
                final var candidate = candidate(id); // Authenticates algorithm/actions/status, run+dataset SHA, raw replay and audit provenance.
                if (candidate == null || !candidate.origin().equals("NATIVE_COMBAT") || !candidate.runId().equals(found.getString(2))
                    || state.updates() != found.getLong(5) || state.completedEpochs() != found.getLong(6) || state.seed() != SEED || state.seed() != found.getLong(7)
                    || !TacticalObservationV3.SCHEMA.equals(found.getString(8)) || !NORMALIZATION.equals(found.getString(9))
                    || !(TacticalReplayV3.REVISION.equals(found.getString(10))||TerminalReplayV1.REVISION.equals(found.getString(10))||ActionAdvantageV1.REVISION.equals(found.getString(10))||ActionAdvantage008.REVISION.equals(found.getString(10))))
                { throw new IllegalArgumentException("Incompatible V3 optimizer committed provenance"); }
                final boolean terminalObjective=candidate.metrics().contains("\"objective\":\"TERMINAL_OUTCOME_V1\"");
                final boolean terminalSampler=TerminalReplayV1.REVISION.equals(found.getString(10));
                final boolean advantageSampler=ActionAdvantageV1.REVISION.equals(found.getString(10));
                if(candidate.metrics().contains("\"objective\":\""+ActionAdvantageV1.OBJECTIVE+"\"")!=advantageSampler){throw new IllegalArgumentException("007 objective/sampler mismatch");}
                final boolean residualSampler=ActionAdvantage008.REVISION.equals(found.getString(10));
                if(candidate.metrics().contains("\"objective\":\""+ActionAdvantage008.OBJECTIVE+"\"")!=residualSampler){throw new IllegalArgumentException("008 objective/sampler mismatch");}
                final boolean terminalApplications;
                try(var markers=connection.prepareStatement("SELECT COUNT(*) FROM phantom_tactical_v3_terminal_applications WHERE model_id=?"))
                {markers.setLong(1,id);try(var rows=markers.executeQuery()){rows.next();terminalApplications=rows.getLong(1)>0;}}
                if(terminalObjective!=terminalSampler||terminalApplications!=terminalSampler){throw new IllegalArgumentException("V3 terminal objective/sampler/application mismatch");}
                if(terminalSampler)
                {
                    try(var snapshot=connection.prepareStatement("SELECT snapshot_blob FROM phantom_tactical_v3_runs WHERE run_id=?"))
                    {snapshot.setString(1,candidate.runId());try(var rows=snapshot.executeQuery()){if(!rows.next()){throw new IllegalArgumentException("Terminal frozen source missing");}terminalSnapshot(connection,candidate.runId(),candidate.datasetSha(),rows.getBytes(1),id);}}
                }
                if(advantageSampler)
                {
                    try(var snapshot=connection.prepareStatement("SELECT snapshot_blob FROM phantom_tactical_v3_runs WHERE run_id=?"))
                    {snapshot.setString(1,candidate.runId());try(var rows=snapshot.executeQuery()){if(!rows.next()){throw new IllegalArgumentException("007 frozen source missing");}advantageSnapshot(connection,candidate.runId(),candidate.datasetSha(),rows.getBytes(1),id);}}
                }
                if(residualSampler)
                {
                    try(var snapshot=connection.prepareStatement("SELECT snapshot_blob FROM phantom_tactical_v3_runs WHERE run_id=?"))
                    {snapshot.setString(1,candidate.runId());try(var rows=snapshot.executeQuery()){if(!rows.next()){throw new IllegalArgumentException("008 frozen source missing");}residualSnapshot(connection,candidate.runId(),candidate.datasetSha(),rows.getBytes(1),id);}}
                }
                return new OptimizationBoundary(new Optimization(id,found.getString(2),found.getString(3),state),found.getLong(11),found.getString(12),found.getString(13),candidate.runId());
            }
        }
    }
    public Checkpoint accepted(long version, String expectedSha) throws SQLException
    { return read(version,expectedSha,true); }
    public Checkpoint candidate(long version) throws SQLException
    {
        try (var connection = _guard.openTacticalConnection(); var query = connection.prepareStatement("SELECT sha256 FROM phantom_tactical_v3_models WHERE model_id=?"))
        {
            query.setLong(1,version);
            try (var found = query.executeQuery()) { return found.next() ? read(version,found.getString(1),false) : null; }
        }
    }
    public String episodeCounts() throws SQLException
    {
        try (var connection = _guard.openTacticalConnection(); var query = connection.createStatement();
            var found = query.executeQuery("SELECT COUNT(*),COALESCE(SUM(state='COMPLETED'),0),COALESCE(SUM(state='FAILED'),0),COALESCE(SUM(state='INCOMPLETE'),0),COALESCE(SUM(state='STARTED'),0),MAX(created_at) FROM phantom_tactical_v3_episodes"))
        {
            if (!found.next()) { throw new SQLException("V3 episode status unavailable"); }
            return "{\"total\":" + found.getLong(1) + ",\"completed\":" + found.getLong(2) + ",\"failed\":" + found.getLong(3) + ",\"incomplete\":" + found.getLong(4)
                + ",\"started\":" + found.getLong(5) + ",\"lastCreatedUtc\":" + (found.getString(6) == null ? "null" : TrainingStore.quote(found.getString(6))) + "}";
        }
    }

    /** A fitted draft remains inert until this separately requested exact TEST apply. */
    public static boolean admissionPermitted(String status, String schema, String actualSha, String expectedSha,
        int epochs, double modelLoss, double constantLoss, Double priorLoss, boolean explicitApply)
    {
        return ("ACCEPTED_TEST".equals(status) || explicitApply && "DRAFT".equals(status))
            && TacticalObservationV3.SCHEMA.equals(schema) && expectedSha != null && expectedSha.matches("[a-f0-9]{64}")
            && expectedSha.equals(actualSha) && epochs > 0 && TacticalTrainerV3.qualityPasses(modelLoss,constantLoss,priorLoss);
    }

    /** Only changes admission status; immutable weight, dataset and provenance bytes are retained. */
    public Checkpoint applyTest(long version, String expectedSha) throws SQLException
    {
        requireStopped();
        final Checkpoint candidate = read(version,expectedSha,false);
        if (candidate == null || !admissionPermitted(candidate.status(),TacticalObservationV3.SCHEMA,candidate.sha(),expectedSha,
            candidate.epochs(),candidate.auditLoss(),candidate.constantLoss(),candidate.priorLoss(),true))
        { throw new IllegalArgumentException("Exact V3 TEST candidate did not pass independent admission"); }
        if (candidate.origin().equals("NATIVE_COMBAT") && !TacticalTrainerV3.qualityGroups(candidate.rows(), candidate.model(), championFor(version)).qualified())
        { throw new IllegalArgumentException("V3 family/class/scope quality did not pass"); }
        transaction(connection ->
        {
            try (var query = connection.createStatement(); var rows = query.executeQuery("SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE ID<>CONNECTION_ID() AND DB=DATABASE()"))
            { if (!rows.next() || rows.getLong(1) != 0) { throw new IllegalStateException("TEST apply requires the stopped SQL boundary"); } }
            try (var query = connection.prepareStatement("SELECT m.status,m.weights_blob,m.dataset_sha,r.snapshot_blob,r.run_id,m.feature_schema,m.algorithm,m.algorithm_hash,m.action_space,m.normalization,m.origin,r.seed,r.state,m.epochs,m.audit_mse,m.constant_mse,m.prior_mse,m.train_count,m.tuning_count,m.audit_count,m.audit_contexts FROM phantom_tactical_v3_models m JOIN phantom_tactical_v3_runs r ON r.run_id=m.run_id AND r.dataset_sha=m.dataset_sha WHERE m.model_id=? AND m.sha256=? FOR UPDATE"))
            {
                query.setLong(1,version); query.setString(2,expectedSha);
                try (var found = query.executeQuery())
                {
                    if (!found.next()) { throw new IllegalArgumentException("Exact V3 TEST candidate changed"); }
                    final String status = found.getString(1), run = found.getString(5);
                    final double priorValue = found.getDouble(17); final Double prior = found.wasNull() ? null : priorValue;
                    if (!admissionPermitted(status,found.getString(6),expectedSha,expectedSha,found.getInt(14),found.getDouble(15),found.getDouble(16),prior,true)
                        || !status.equals(found.getString(13)) || !candidate.datasetSha().equals(found.getString(3))
                        || !Arrays.equals(candidate.model().bytes(),found.getBytes(2)) || !Arrays.equals(pack(candidate.rows()),found.getBytes(4))
                        || found.getInt(7) != TacticalModelV3.ALGORITHM || !TacticalModelV3.FINGERPRINT.equals(found.getString(8))
                        || !registrySpace(found.getString(11)).equals(found.getString(9)) || !NORMALIZATION.equals(found.getString(10))
                        || found.getLong(12) != SEED || found.getInt(14) != candidate.epochs()
                        || Double.compare(found.getDouble(15),candidate.auditLoss()) != 0 || Double.compare(found.getDouble(16),candidate.constantLoss()) != 0
                        || !java.util.Objects.equals(prior,candidate.priorLoss()) || found.getInt(18) != candidate.trainCount()
                        || found.getInt(19) != candidate.tuningCount() || found.getInt(20) != candidate.auditCount() || found.getInt(21) != candidate.auditContexts())
                    { throw new IllegalArgumentException("Frozen V3 TEST candidate changed before explicit apply"); }
                    for (String cohort : candidate.rows().stream().filter(row -> row.split().equals("AUDIT")).map(Row::cohort).distinct().toList())
                    {
                        try (var proof = connection.prepareStatement("SELECT run_id FROM phantom_tactical_v3_audit_used WHERE cohort_hash=? FOR UPDATE"))
                        {
                            proof.setString(1,cohort);
                            try (var witness = proof.executeQuery())
                            { if (!witness.next() || !run.equals(witness.getString(1))) { throw new IllegalArgumentException("Frozen TEST audit authority changed"); } }
                        }
                    }
                    if ("ACCEPTED_TEST".equals(status)) { return null; }
                    try (var update = connection.prepareStatement("UPDATE phantom_tactical_v3_models SET status='ACCEPTED_TEST' WHERE model_id=? AND sha256=? AND status='DRAFT'"))
                    { update.setLong(1,version); update.setString(2,expectedSha); if (update.executeUpdate() != 1) { throw new SQLException("V3 TEST candidate admission race"); } }
                    try (var update = connection.prepareStatement("UPDATE phantom_tactical_v3_runs SET state='ACCEPTED_TEST' WHERE run_id=? AND state='DRAFT'"))
                    { update.setString(1,run); if (update.executeUpdate() != 1) { throw new SQLException("V3 TEST candidate run admission race"); } }
                }
            }
            return null;
        });
        return accepted(version,expectedSha);
    }
    public Checkpoint latestAccepted() throws SQLException
    {
        try (var connection = _guard.openTacticalConnection(); var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT model_id,sha256 FROM phantom_tactical_v3_models WHERE status='ACCEPTED_TEST' ORDER BY model_id DESC LIMIT 1"))
        { return rows.next() ? accepted(rows.getLong(1),rows.getString(2)) : null; }
    }
    private Checkpoint read(long version, String expectedSha, boolean acceptedOnly) throws SQLException
    {
        if (version <= 0 || expectedSha == null || !expectedSha.matches("[a-f0-9]{64}")) { throw new IllegalArgumentException("V3 cold model pin required"); }
        try (var connection = _guard.openTacticalConnection(); var query = connection.prepareStatement("SELECT m.algorithm,m.algorithm_hash,m.feature_schema,m.action_space,m.normalization,m.origin,m.status,m.dataset_sha,m.weights_blob,m.sha256,m.epochs,m.train_count,m.tuning_count,m.audit_count,m.audit_contexts,m.audit_mse,m.constant_mse,m.prior_mse,m.metrics_json,r.seed,r.snapshot_blob,r.state,r.run_id FROM phantom_tactical_v3_models m JOIN phantom_tactical_v3_runs r ON r.run_id=m.run_id AND r.dataset_sha=m.dataset_sha WHERE m.model_id=?"))
        {
            query.setLong(1,version);
            try (var found = query.executeQuery())
            {
                if (!found.next()) { return null; }
                final String status = found.getString(7);
                if (acceptedOnly && !"ACCEPTED_TEST".equals(status)) { return null; }
                if (found.getInt(1) != TacticalModelV3.ALGORITHM || !TacticalModelV3.FINGERPRINT.equals(found.getString(2))
                    || !TacticalObservationV3.SCHEMA.equals(found.getString(3)) || !registrySpace(found.getString(6)).equals(found.getString(4)) || !NORMALIZATION.equals(found.getString(5))
                    || !Set.of("DRAFT","ACCEPTED_TEST").contains(status)
                    || !expectedSha.equals(found.getString(10)) || found.getLong(20) != SEED || !status.equals(found.getString(22)))
                { throw new IllegalArgumentException("Incompatible V3 cold registry metadata"); }
                final var model = TacticalModelV3.restore(found.getBytes(9),expectedSha);
                final byte[] bytes = found.getBytes(21); final var rows = unpack(bytes);
                if (!TacticalObservationV3.hash(bytes).equals(found.getString(8)) || !sufficient(rows) || found.getInt(11) < 0 || status.equals("ACCEPTED_TEST") && found.getInt(11) == 0
                    || count(rows,"TRAIN") != found.getInt(12) || count(rows,"TUNING") != found.getInt(13) || count(rows,"AUDIT") != found.getInt(14))
                { throw new IllegalArgumentException("Frozen native V3 admission metadata changed"); }
                if (found.getString(6).equals("NATIVE_COMBAT")) { requireReplayContexts(connection,found.getString(23),found.getString(8),rows); }
                final var audit = rows.stream().filter(row -> row.split().equals("AUDIT")).toList();
                if (audit.stream().map(Row::cohort).distinct().count() != found.getInt(15)) { throw new IllegalArgumentException("Native audit context count changed"); }
                ActionAdvantage008.Frozen residual=null;
                if(found.getString(19).contains("\"objective\":\""+ActionAdvantage008.OBJECTIVE+"\""))
                {
                    if(!status.equals("DRAFT")){throw new IllegalArgumentException("008 remains an explicitly unpromoted DRAFT");}
                    residual=residualSnapshot(connection,found.getString(23),found.getString(8),bytes,version);
                }
                final var auditExamples=coldAuditExamples(rows,residual);
                final double loss = model.loss(auditExamples);
                final double constant = coldAuditConstant(rows,residual);
                final double priorValue = found.getDouble(18); final Double prior = found.wasNull() ? null : priorValue;
                if(residual!=null)
                {
                    var teacher=candidate(ActionAdvantage008.TEACHER_VERSION);
                    if(teacher==null||!teacher.sha().equals(ActionAdvantage008.TEACHER_SHA)||prior==null||Math.abs(prior-teacher.model().loss(auditExamples))>1e-9)
                    {throw new IllegalArgumentException("008 frozen DRAFT11 audit reference changed");}
                }
                if (!Double.isFinite(loss) || Math.abs(loss - found.getDouble(16)) > 1e-9 || !Double.isFinite(constant)
                    || Math.abs(constant - found.getDouble(17)) > 1e-9 || status.equals("ACCEPTED_TEST")
                        && !admissionPermitted(status,found.getString(3),found.getString(10),expectedSha,found.getInt(11),loss,constant,prior,false))
                { throw new IllegalArgumentException("V3 native audit admission failed cold verification"); }
                for (String cohort : audit.stream().map(Row::cohort).distinct().toList())
                {
                    try (var proof = connection.prepareStatement("SELECT run_id FROM phantom_tactical_v3_audit_used WHERE cohort_hash=?"))
                    {
                        proof.setString(1,cohort);
                        try (var witness = proof.executeQuery())
                        { if (!witness.next() || !found.getString(23).equals(witness.getString(1))) { throw new IllegalArgumentException("V3 audit provenance changed"); } }
                    }
                }
                if (status.equals("ACCEPTED_TEST") && found.getString(6).equals("NATIVE_COMBAT")
                    && !TacticalTrainerV3.qualityGroups(rows, model, championFor(version)).qualified())
                { throw new IllegalArgumentException("Cold V3 family/class/scope admission failed"); }
                return new Checkpoint(version,model,expectedSha,status,found.getString(8),found.getInt(12),found.getInt(13),found.getInt(14),
                    found.getInt(15),loss,constant,prior,found.getInt(11),found.getString(19),rows,found.getString(6),found.getString(23));
            }
        }
    }
    static List<TacticalModelV3.Example> coldAuditExamples(List<Row> rows,ActionAdvantage008.Frozen residual)
    {return residual==null?rows.stream().filter(row->row.split().equals("AUDIT")).map(Row::example).toList():residual.examples("AUDIT");}
    static double coldAuditConstant(List<Row> rows,ActionAdvantage008.Frozen residual)
    {return residual==null?TacticalTrainerV3.constantLoss(rows):residual.constantLoss("AUDIT");}
    static int count(List<Row> rows, String split) { return (int) rows.stream().filter(row -> row.split().equals(split)).count(); }
    private TacticalModelV3 championFor(long candidate) throws SQLException
    {
        try (var connection = _guard.openTacticalConnection(); var query = connection.prepareStatement("SELECT o.champion_model_id,m.weights_blob,m.sha256,m.status,m.feature_schema,m.action_space,m.origin FROM phantom_tactical_v3_optimizers o JOIN phantom_tactical_v3_models m ON m.model_id=o.champion_model_id WHERE o.model_id=?"))
        {
            query.setLong(1,candidate);
            try (var found = query.executeQuery())
            {
                if (!found.next() || found.getLong(1) >= candidate || !found.getString(4).equals("ACCEPTED_TEST")
                    || !TacticalObservationV3.SCHEMA.equals(found.getString(5)) || !registrySpace(found.getString(7)).equals(found.getString(6)))
                { throw new IllegalArgumentException("Missing immutable V3 audit champion"); }
                return TacticalModelV3.restore(found.getBytes(2),found.getString(3));
            }
        }
    }
    static String registrySpace(String origin)
    {
        return switch (origin)
        {
            case "NATIVE_HEAL" -> ACTION_SPACE;
            case "NATIVE_COMBAT" -> COMBAT_ACTION_SPACE;
            default -> throw new IllegalArgumentException("Unsupported V3 model origin");
        };
    }

    static String hash(String value) { return TacticalObservationV3.hash(value.getBytes(StandardCharsets.UTF_8)); }
}
