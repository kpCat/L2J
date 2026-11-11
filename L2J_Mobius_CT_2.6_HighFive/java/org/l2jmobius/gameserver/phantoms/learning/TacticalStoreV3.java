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
import java.util.Set;
import java.util.UUID;

/** Post-drain, TEST-only V3 durability. No SQL or training is called from native gameplay. */
public final class TacticalStoreV3
{
    public static final long SEED = 183;
    public static final String ACTION_SPACE = hash("LegalActionV3/" + LegalActionV3.VERSION + "/HEAL/" + Arrays.toString(LegalActionV3.Family.values()) + '/' + LegalActionV3.FINGERPRINT);
    public static final String NORMALIZATION = hash(TacticalObservationV3.SCHEMA + "/state128/action64/paired-mask/clip[-1,1]/reward[-1,1]");
    private static final int MAGIC = 0x54563352, ROW_VERSION = 4, MAX_ROW = 600000, MAX_DATASET = 64 * 1024 * 1024;
    private final TrainingStore _guard;
    private final Path _root;
    private final String _datasetContextPrefix;

    public record Row(String split, String contextKey, HealEvidenceV3.Receipt receipt)
    {
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
    public record Checkpoint(long id, TacticalModelV3 model, String sha, String status, String datasetSha,
        int trainCount, int tuningCount, int auditCount, int auditContexts, double auditLoss, double constantLoss,
        Double priorLoss, int epochs, String metrics, List<Row> rows)
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

    /** The canonical fresh installer includes this same migration; reapplying it is idempotent. */
    public void initialize() throws IOException, SQLException
    {
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

    private static void validateReceipt(HealEvidenceV3.Receipt receipt)
    {
        final var attempt = receipt.attempt();
        final var observation = attempt.observation();
        if (observation.capturedNanos() <= 0 || attempt.modelVersion() < 0 || attempt.offered().size() < 2
            || receipt.owner() == null || receipt.owner().generation() <= 0 || receipt.owner().operationKey() == null
            || receipt.owner().operationKey().isBlank() || receipt.owner().operationKey().length() > 128
            || Math.abs(receipt.reward()) > 1 || receipt.beforeHp() < 0
            || Math.abs(receipt.actualHp() - (receipt.afterHp() - receipt.beforeHp())) > 1e-6)
        { throw new IllegalArgumentException("Incomplete native HEAL receipt"); }
        final var identities = new HashSet<LegalActionV3>();
        for (var action : attempt.offered())
        {
            if (action.family() != LegalActionV3.Family.HEAL || !action.trainable() || action.criticalSafety()
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
            out.writeInt(MAGIC); out.writeInt(ROW_VERSION); out.writeUTF(TacticalObservationV3.SCHEMA); out.writeUTF(ACTION_SPACE);
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
            if (in.readInt() != MAGIC || in.readInt() != ROW_VERSION || !TacticalObservationV3.SCHEMA.equals(in.readUTF()) || !ACTION_SPACE.equals(in.readUTF()))
            { throw new IllegalArgumentException("Native receipt schema"); }
            final String split = in.readUTF(), context = in.readUTF(), key = in.readUTF();
            final int length = in.readInt(); if (length <= 0 || length > 524288 || length > in.available()) { throw new IllegalArgumentException("Native observation size"); }
            final var observation = TacticalObservationV3.restore(in.readNBytes(length));
            final int count = in.readInt(); if (count < 2 || count > 16) { throw new IllegalArgumentException("Native action bound"); }
            final var offered = new ArrayList<LegalActionV3>();
            for (int i = 0; i < count; i++) { offered.add(LegalActionV3.read(in)); }
            final int chosen = in.readInt(), baseline = in.readInt();
            if (chosen < 0 || chosen >= count || baseline < 0 || baseline >= count) { throw new IllegalArgumentException("Native action index"); }
            final var attempt = new HealEvidenceV3.Attempt(observation, offered, offered.get(chosen), offered.get(baseline), in.readLong(), _ -> { });
            final double before = in.readDouble(), after = in.readDouble(), hp = in.readDouble(), mp = in.readDouble();
            final long nanos = in.readLong(); final float reward = in.readFloat();
            final var owner = new HealEvidenceV3.SourceOwner(in.readLong(), in.readUTF());
            final var receipt = new HealEvidenceV3.Receipt(key, attempt, before, after, hp, mp, nanos, reward, owner);
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
            try (var query = connection.prepareStatement("SELECT event_key FROM phantom_tactical_v3_events WHERE action_key=? FOR UPDATE"))
            {
                query.setString(1, row.actionKey());
                try (var found = query.executeQuery()) { if (found.next()) { throw new IllegalArgumentException("Native action already has a durable event"); } }
            }
            try (var insert = connection.prepareStatement("INSERT INTO phantom_tactical_v3_events(event_key,action_key,actor_profile,split,context_hash,origin,receipt_sha,receipt_blob) VALUES(?,?,?,?,?,'NATIVE_HEAL',?,?)"))
            {
                insert.setString(1,row.eventKey()); insert.setString(2,row.actionKey()); insert.setLong(3,row.actorProfile()); insert.setString(4,split);
                insert.setString(5,row.contextHash()); insert.setString(6,sha); insert.setBytes(7,bytes); insert.executeUpdate();
            }
            return true;
        });
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
        try (var connection = _guard.openTacticalConnection(); var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM phantom_tactical_v3_events WHERE origin='NATIVE_HEAL'"))
        { if (!rows.next()) { throw new SQLException("Native sample count missing"); } return rows.getLong(1); }
    }
    public List<Row> examples() throws SQLException { return dataset(); }
    public List<Row> dataset() throws SQLException
    { try (var connection = _guard.openTacticalConnection()) { return dataset(connection); } }
    private static List<Row> dataset(Connection connection) throws SQLException
    {
        final var result = new ArrayList<Row>();
        long totalBytes = 0;
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT event_key,action_key,actor_profile,split,context_hash,origin,receipt_sha,receipt_blob FROM phantom_tactical_v3_events ORDER BY event_key LIMIT 4097"))
        {
            while (rows.next())
            {
                final byte[] bytes = rows.getBytes(8);
                if (bytes == null || (totalBytes += bytes.length) > MAX_DATASET) { throw new IllegalArgumentException("Native dataset byte budget exceeded"); }
                final Row row = decode(bytes);
                if (!"NATIVE_HEAL".equals(rows.getString(6)) || !row.eventKey().equals(rows.getString(1)) || !row.actionKey().equals(rows.getString(2))
                    || row.actorProfile() != rows.getLong(3) || !row.split().equals(rows.getString(4)) || !row.contextHash().equals(rows.getString(5))
                    || !TacticalObservationV3.hash(bytes).equals(rows.getString(7)))
                { throw new IllegalArgumentException("Native SQL receipt/hash metadata changed"); }
                result.add(row);
            }
        }
        return validateDataset(result);
    }
    Frozen freeze() throws SQLException
    {
        requireStopped();
        return transaction(connection ->
        {
            final var used = new HashSet<String>();
            try (var query = connection.createStatement(); var found = query.executeQuery("SELECT cohort_hash FROM phantom_tactical_v3_audit_used"))
            { while (found.next()) { used.add(found.getString(1)); } }
            final var rows = dataset(connection).stream()
                .filter(row -> row.contextKey().startsWith(_datasetContextPrefix))
                .filter(row -> !row.split().equals("AUDIT") || !used.contains(row.cohort())).toList();
            if (!sufficient(rows)) { return null; }
            final byte[] snapshot = pack(rows); final String sha = TacticalObservationV3.hash(snapshot);
            final String id = UUID.randomUUID().toString().replace("-", "");
            try (var insert = connection.prepareStatement("INSERT INTO phantom_tactical_v3_runs(run_id,dataset_sha,seed,snapshot_blob,state) VALUES(?,?,?,?,'FROZEN')"))
            { insert.setString(1,id); insert.setString(2,sha); insert.setLong(3,SEED); insert.setBytes(4,snapshot); insert.executeUpdate(); }
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
    Checkpoint save(Frozen frozen, TacticalModelV3.Fit fit, double auditLoss, double constantLoss, Double priorLoss, String metrics) throws SQLException
    {
        final String status = "DRAFT";
        final byte[] weights = fit.model().bytes(); final String sha = fit.model().sha256();
        if (fit.epochs() < 0 || metrics == null || metrics.length() > 65536) { throw new IllegalArgumentException("Invalid V3 checkpoint/metrics"); }
        final long id = transaction(connection ->
        {
            try (var query = connection.prepareStatement("SELECT seed,dataset_sha,snapshot_blob,state FROM phantom_tactical_v3_runs WHERE run_id=? FOR UPDATE"))
            {
                query.setString(1,frozen.runId());
                try (var found = query.executeQuery())
                {
                    if (!found.next() || found.getLong(1) != SEED || !frozen.datasetSha().equals(found.getString(2)) || !Arrays.equals(pack(frozen.rows()),found.getBytes(3)) || !"FROZEN".equals(found.getString(4)))
                    { throw new IllegalArgumentException("Frozen native evaluation changed"); }
                }
            }
            try (var insert = connection.prepareStatement("INSERT INTO phantom_tactical_v3_models(run_id,algorithm,algorithm_hash,feature_schema,action_space,normalization,origin,status,dataset_sha,weights_blob,sha256,epochs,train_count,tuning_count,audit_count,audit_contexts,audit_mse,constant_mse,prior_mse,metrics_json) VALUES(?,?,?,?,?,?,'NATIVE_HEAL',?,?,?,?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS))
            {
                insert.setString(1,frozen.runId()); insert.setInt(2,TacticalModelV3.ALGORITHM); insert.setString(3,TacticalModelV3.FINGERPRINT);
                insert.setString(4,TacticalObservationV3.SCHEMA); insert.setString(5,ACTION_SPACE); insert.setString(6,NORMALIZATION); insert.setString(7,status);
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
                    try (var update = connection.prepareStatement("UPDATE phantom_tactical_v3_runs SET state=? WHERE run_id=? AND state='FROZEN'"))
                    { update.setString(1,status); update.setString(2,frozen.runId()); if (update.executeUpdate() != 1) { throw new SQLException("V3 frozen state changed"); } }
                    return model;
                }
            }
        });
        return read(id,sha,false);
    }
    public Checkpoint accepted(long version, String expectedSha) throws SQLException
    { return read(version,expectedSha,true); }

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
                        || !ACTION_SPACE.equals(found.getString(9)) || !NORMALIZATION.equals(found.getString(10))
                        || !"NATIVE_HEAL".equals(found.getString(11)) || found.getLong(12) != SEED || found.getInt(14) != candidate.epochs()
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
                    || !TacticalObservationV3.SCHEMA.equals(found.getString(3)) || !ACTION_SPACE.equals(found.getString(4)) || !NORMALIZATION.equals(found.getString(5))
                    || !"NATIVE_HEAL".equals(found.getString(6)) || !Set.of("DRAFT","ACCEPTED_TEST").contains(status)
                    || !expectedSha.equals(found.getString(10)) || found.getLong(20) != SEED || !status.equals(found.getString(22)))
                { throw new IllegalArgumentException("Incompatible V3 cold registry metadata"); }
                final var model = TacticalModelV3.restore(found.getBytes(9),expectedSha);
                final byte[] bytes = found.getBytes(21); final var rows = unpack(bytes);
                if (!TacticalObservationV3.hash(bytes).equals(found.getString(8)) || !sufficient(rows) || found.getInt(11) < 0 || status.equals("ACCEPTED_TEST") && found.getInt(11) == 0
                    || count(rows,"TRAIN") != found.getInt(12) || count(rows,"TUNING") != found.getInt(13) || count(rows,"AUDIT") != found.getInt(14))
                { throw new IllegalArgumentException("Frozen native V3 admission metadata changed"); }
                final var audit = rows.stream().filter(row -> row.split().equals("AUDIT")).toList();
                if (audit.stream().map(Row::cohort).distinct().count() != found.getInt(15)) { throw new IllegalArgumentException("Native audit context count changed"); }
                final double loss = model.loss(audit.stream().map(Row::example).toList());
                final double constant = TacticalTrainerV3.constantLoss(rows);
                final double priorValue = found.getDouble(18); final Double prior = found.wasNull() ? null : priorValue;
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
                return new Checkpoint(version,model,expectedSha,status,found.getString(8),found.getInt(12),found.getInt(13),found.getInt(14),
                    found.getInt(15),loss,constant,prior,found.getInt(11),found.getString(19),rows);
            }
        }
    }
    static int count(List<Row> rows, String split) { return (int) rows.stream().filter(row -> row.split().equals(split)).count(); }

    static String hash(String value) { return TacticalObservationV3.hash(value.getBytes(StandardCharsets.UTF_8)); }
}
