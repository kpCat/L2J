using System.Text.Json;
using Microsoft.Data.Sqlite;

namespace PhantomSemanticStudio.Core;

public sealed record CorpusCampaignStatus(string Id, string State, string Desired, long Attempts, long Issued, long SchemaValid,
    long Rejected, long Pending, long Reviewed, long Approved, long RejectedBatches, string Stage, long EditorialIssued = 0);
internal sealed record CorpusCampaignTask(string Id, int Ordinal, string QuotaId, string RunId, string State);
internal sealed record CorpusCampaignAcceptedRow(string TaskId, string RunId, string QuotaId, int Number, string Text, string JsonLine, string DecisionHash);

/// <summary>Short journal transactions and controls remain available while the worker owns workspace.lock.</summary>
public static class CorpusCampaignControl
{
    internal static string PathFor(string root, string source, string id, string hash)
    {
        root = PathSafety.Canonical(root); source = PathSafety.RepositoryBoundary(PathSafety.Canonical(source));
        PathSafety.AssertDisjoint(source, root);
        _ = new V4Workspace(root, source);
        V4Rules.Require(PromptLabStore.IsId(id) && hash is { Length: 64 } && hash.All(c => c is >= '0' and <= '9' or >= 'a' and <= 'f'), "CAMPAIGN_CONTROL_ID_HASH");
        var folder = "labs/prompt-lab/campaigns/" + id + "/";
        using var document = JsonDocument.Parse(V4Rules.ReadBytes(PathSafety.ResolveRelative(root, folder + "plan.json"), 32 * 1024 * 1024));
        var envelope = document.RootElement; V4Rules.JsonKeys(envelope, ["version", "kind", "sha256", "data"], []);
        var data = JsonSerializer.SerializeToUtf8Bytes(envelope.GetProperty("data"), PromptProfiles.CompactJson);
        V4Rules.Require(envelope.GetProperty("version").GetInt32() == 1 && V4Rules.String(envelope, "kind") == "CORPUS_CAMPAIGN_PLAN"
            && V4Rules.String(envelope, "sha256") == hash && V4Rules.Hash(data) == hash
            && envelope.GetProperty("data").GetProperty("Id").GetString() == id, "CAMPAIGN_CONTROL_PLAN_DRIFT");
        return PathSafety.ResolveRelative(root, folder + "journal.sqlite");
    }

    internal static SqliteCommand Sql(SqliteConnection db, SqliteTransaction? transaction, string text, params (string Name, object Value)[] values)
    {
        var command = db.CreateCommand(); command.Transaction = transaction; command.CommandText = text;
        foreach (var value in values) command.Parameters.AddWithValue(value.Name, value.Value);
        return command;
    }

    internal static SqliteConnection Open(string path, string id, string hash, bool write)
    {
        V4Rules.Require(File.Exists(path), "CAMPAIGN_JOURNAL_NOT_STARTED");
        foreach (var suffix in new[] { "", "-journal", "-wal", "-shm" }) PathSafety.AssertNoReparsePoints(path + suffix);
        V4Rules.Require(new FileInfo(path).Length <= 1024L * 1024 * 1024, "CAMPAIGN_JOURNAL_RESOURCE");
        var db = CorpusStore.Connect(path, write);
        try
        {
            using (var version = Sql(db, null, "PRAGMA user_version")) V4Rules.Require((long)version.ExecuteScalar()! == 1, "CAMPAIGN_JOURNAL_VERSION");
            using (var identity = Sql(db, null, "SELECT id,plan_hash FROM campaign WHERE singleton=1"))
            using (var reader = identity.ExecuteReader()) V4Rules.Require(reader.Read() && reader.GetString(0) == id && reader.GetString(1) == hash, "CAMPAIGN_JOURNAL_IDENTITY");
            if (write) { using var settings = Sql(db, null, "PRAGMA foreign_keys=ON; PRAGMA synchronous=FULL; PRAGMA max_page_count=262144;"); settings.ExecuteNonQuery(); }
            return db;
        }
        catch { db.Dispose(); throw; }
    }

    public static CorpusCampaignStatus Status(string root, string source, string id, string expectedPlanHash)
    {
        var path = PathFor(root, source, id, expectedPlanHash); using var db = Open(path, id, expectedPlanHash, false);
        using var feature = Sql(db, null, "SELECT count(*) FROM sqlite_master WHERE type='table' AND name='editorial'");
        var editorial = (long)feature.ExecuteScalar()! == 1;
        var reviewed = editorial ? "(SELECT count(*) FROM records r JOIN editorial e ON e.task_id=r.task_id WHERE e.state='DONE')" : "0";
        var issued = editorial ? "(SELECT coalesce(sum(issued),0) FROM editorial)" : "0";
        using var command = Sql(db, null, """
            SELECT state,desired,stage,(SELECT count(*) FROM tasks),(SELECT coalesce(sum(issued),0) FROM tasks),
            (SELECT count(*) FROM records),(SELECT count(*) FROM records WHERE state IN ('REJECTED','EDITORIAL_REJECTED')),
            (SELECT count(*) FROM records WHERE state='PENDING'),(SELECT count(*) FROM tasks WHERE rejected_batch=1),
            """ + reviewed + ", (SELECT count(*) FROM records WHERE state='APPROVED'), " + issued + " " + """
            FROM campaign WHERE singleton=1
            """);
        using var reader = command.ExecuteReader(); reader.Read();
        return new(id, reader.GetString(0), reader.GetString(1), reader.GetInt64(3), reader.GetInt64(4), reader.GetInt64(5),
            reader.GetInt64(6), reader.GetInt64(7), reader.GetInt64(9), reader.GetInt64(10), reader.GetInt64(8), reader.GetString(2), reader.GetInt64(11));
    }

    public static CorpusCampaignStatus Request(string root, string source, string id, string expectedPlanHash, string command)
    {
        V4Rules.Require(command is "PAUSE" or "STOP", "CAMPAIGN_CONTROL_COMMAND");
        var path = PathFor(root, source, id, expectedPlanHash); using (var db = Open(path, id, expectedPlanHash, true))
        { using var update = Sql(db, null, "UPDATE campaign SET desired=$command WHERE singleton=1", ("$command", command)); update.ExecuteNonQuery(); }
        return Status(root, source, id, expectedPlanHash);
    }
}

internal sealed class CorpusCampaignJournal
{
    private readonly WorkspaceStore workspace;
    private readonly string id, hash, path;
    public CorpusCampaignJournal(WorkspaceStore workspace, string id, string hash)
    {
        this.workspace = workspace; this.id = id; this.hash = hash;
        path = CorpusCampaignControl.PathFor(workspace.Root, workspace.ProtectedRoot, id, hash);
        if (!File.Exists(path))
        {
            var temporary = path + ".new-" + Guid.NewGuid().ToString("N");
            try
            {
                using (var db = CorpusStore.Connect(temporary, true))
                {
                    using (var config = CorpusCampaignControl.Sql(db, null, "PRAGMA page_size=4096; PRAGMA journal_mode=DELETE; PRAGMA synchronous=FULL; PRAGMA max_page_count=262144;")) config.ExecuteNonQuery();
                    using var transaction = db.BeginTransaction();
                    using (var schema = CorpusCampaignControl.Sql(db, transaction, """
                        PRAGMA user_version=1;
                        CREATE TABLE campaign(singleton INTEGER PRIMARY KEY CHECK(singleton=1),id TEXT NOT NULL,plan_hash TEXT NOT NULL,state TEXT NOT NULL,desired TEXT NOT NULL,stage TEXT NOT NULL);
                        CREATE TABLE tasks(id TEXT PRIMARY KEY,ordinal INTEGER NOT NULL UNIQUE,quota_id TEXT NOT NULL,run_id TEXT NOT NULL,state TEXT NOT NULL,issued INTEGER NOT NULL DEFAULT 0,rejected_batch INTEGER NOT NULL DEFAULT 0);
                        CREATE TABLE records(task_id TEXT NOT NULL REFERENCES tasks(id),number INTEGER NOT NULL,text_id INTEGER NOT NULL,original TEXT NOT NULL,normalized TEXT NOT NULL,state TEXT NOT NULL,reason TEXT NOT NULL,json_line TEXT NOT NULL,PRIMARY KEY(task_id,number));
                        CREATE INDEX ix_record_state ON records(state);
                        """)) schema.ExecuteNonQuery();
                    using var meta = CorpusCampaignControl.Sql(db, transaction, "INSERT INTO campaign VALUES(1,$id,$hash,'READY','RUN','')", ("$id", id), ("$hash", hash));
                    meta.ExecuteNonQuery(); transaction.Commit();
                }
                workspace.Check(); File.Move(temporary, path, false);
            }
            finally { if (File.Exists(temporary)) { PathSafety.AssertNoReparsePoints(temporary); File.Delete(temporary); } }
        }
        using var verified = Open(false);
    }

    private SqliteConnection Open(bool write) { workspace.Check(); return CorpusCampaignControl.Open(path, id, hash, write); }
    public CorpusCampaignStatus Status() => CorpusCampaignControl.Status(workspace.Root, workspace.ProtectedRoot, id, hash);
    public void Start(bool resume)
    {
        using var db = Open(true); using var update = CorpusCampaignControl.Sql(db, null, resume
            ? "UPDATE campaign SET state='RUNNING',desired='RUN',stage='' WHERE singleton=1"
            : "UPDATE campaign SET state='RUNNING',stage='' WHERE singleton=1 AND desired='RUN'"); update.ExecuteNonQuery();
    }
    public void State(string state, string stage = "")
    {
        using var db = Open(true); using var update = CorpusCampaignControl.Sql(db, null, "UPDATE campaign SET state=$state,stage=$stage WHERE singleton=1", ("$state", state), ("$stage", stage)); update.ExecuteNonQuery();
    }
    public void Stage(string stage)
    {
        using var db = Open(true); using var update = CorpusCampaignControl.Sql(db, null, "UPDATE campaign SET stage=$stage WHERE singleton=1", ("$stage", stage)); update.ExecuteNonQuery();
    }
    public void MarkIssued(CorpusCampaignTask task)
    {
        using var db = Open(true); using var update = CorpusCampaignControl.Sql(db, null, "UPDATE tasks SET issued=1 WHERE id=$id AND issued=0", ("$id", task.Id)); update.ExecuteNonQuery();
    }
    private static long Approved(string quota, SqliteConnection db, SqliteTransaction? transaction)
    {
        using var count = CorpusCampaignControl.Sql(db, transaction, "SELECT count(*) FROM records r JOIN tasks t ON t.id=r.task_id WHERE t.quota_id=$quota AND r.state='APPROVED'", ("$quota", quota));
        return (long)count.ExecuteScalar()!;
    }
    private static long Attempts(string quota, SqliteConnection db, SqliteTransaction? transaction)
    {
        using var count = CorpusCampaignControl.Sql(db, transaction, "SELECT count(*) FROM tasks WHERE quota_id=$quota", ("$quota", quota)); return (long)count.ExecuteScalar()!;
    }
    public long QuotaAttempts(string quota) { using var db = Open(false); return Attempts(quota, db, null); }
    public bool RepeatedGenerationFailures()
    {
        using var db = Open(false); using var query = CorpusCampaignControl.Sql(db, null, "SELECT coalesce(sum(rejected_batch),0) FROM (SELECT rejected_batch FROM tasks WHERE state='DONE' ORDER BY ordinal DESC LIMIT 5)");
        return (long)query.ExecuteScalar()! == 5;
    }
    public bool EditorialQualityRegression()
    {
        using var db = Open(false); using var query = CorpusCampaignControl.Sql(db, null, """
            SELECT count(DISTINCT r.task_id),count(*),coalesce(sum(CASE WHEN r.state='APPROVED' THEN 1 ELSE 0 END),0)
            FROM records r JOIN (SELECT e.task_id FROM editorial e JOIN tasks t ON t.id=e.task_id WHERE e.state='DONE' ORDER BY t.ordinal DESC LIMIT 8) recent ON recent.task_id=r.task_id
            """);
        using var reader = query.ExecuteReader(); reader.Read(); return reader.GetInt64(0) == 8 && reader.GetInt64(2) * 4 < reader.GetInt64(1);
    }
    public bool TargetsMet(CorpusCampaignPlan plan)
    { using var db = Open(false); return plan.Spec.Quotas.All(q => Approved(q.Id, db, null) >= q.AcceptedTarget); }

    public void BindPolicy(string policyId, string approvalHash)
    {
        using var db = Open(true); using var transaction = db.BeginTransaction();
        using (var schema = CorpusCampaignControl.Sql(db, transaction, """
            CREATE TABLE IF NOT EXISTS campaign_policy(singleton INTEGER PRIMARY KEY CHECK(singleton=1),policy_id TEXT NOT NULL,approval_hash TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS editorial(task_id TEXT PRIMARY KEY REFERENCES tasks(id),policy_id TEXT NOT NULL,approval_hash TEXT NOT NULL,job_hash TEXT NOT NULL,
                issued INTEGER NOT NULL DEFAULT 0,state TEXT NOT NULL,decision_hash TEXT NOT NULL DEFAULT '');
            """)) schema.ExecuteNonQuery();
        using (var insert = CorpusCampaignControl.Sql(db, transaction, "INSERT OR IGNORE INTO campaign_policy VALUES(1,$policy,$hash)", ("$policy", policyId), ("$hash", approvalHash))) insert.ExecuteNonQuery();
        using (var identity = CorpusCampaignControl.Sql(db, transaction, "SELECT policy_id,approval_hash FROM campaign_policy WHERE singleton=1"))
        using (var reader = identity.ExecuteReader()) V4Rules.Require(reader.Read() && reader.GetString(0) == policyId && reader.GetString(1) == approvalHash, "CAMPAIGN_BOUND_POLICY_DRIFT");
        transaction.Commit();
    }
    public CorpusCampaignTask? PendingEditorialTask()
    {
        using var db = Open(false); using var query = CorpusCampaignControl.Sql(db, null, """
            SELECT t.id,t.ordinal,t.quota_id,t.run_id,t.state FROM tasks t WHERE t.state='DONE'
            AND EXISTS(SELECT 1 FROM records r WHERE r.task_id=t.id AND r.state='PENDING')
            AND NOT EXISTS(SELECT 1 FROM editorial e WHERE e.task_id=t.id AND e.state='DONE') ORDER BY t.ordinal LIMIT 1
            """);
        using var reader = query.ExecuteReader(); return reader.Read() ? new(reader.GetString(0), reader.GetInt32(1), reader.GetString(2), reader.GetString(3), reader.GetString(4)) : null;
    }
    public void Editorial(CorpusCampaignTask task, string policyId, string approvalHash, string jobHash, bool issued)
    {
        using var db = Open(true); using var transaction = db.BeginTransaction();
        using (var insert = CorpusCampaignControl.Sql(db, transaction, "INSERT OR IGNORE INTO editorial(task_id,policy_id,approval_hash,job_hash,state) VALUES($task,$policy,$approval,$job,'PREPARED')",
            ("$task", task.Id), ("$policy", policyId), ("$approval", approvalHash), ("$job", jobHash))) insert.ExecuteNonQuery();
        using (var identity = CorpusCampaignControl.Sql(db, transaction, "SELECT policy_id,approval_hash,job_hash FROM editorial WHERE task_id=$task", ("$task", task.Id)))
        using (var reader = identity.ExecuteReader()) V4Rules.Require(reader.Read() && reader.GetString(0) == policyId && reader.GetString(1) == approvalHash && reader.GetString(2) == jobHash, "CAMPAIGN_EDITORIAL_IDENTITY");
        if (issued)
        { using var update = CorpusCampaignControl.Sql(db, transaction, "UPDATE editorial SET issued=1 WHERE task_id=$task", ("$task", task.Id)); update.ExecuteNonQuery(); }
        transaction.Commit();
    }
    public void CommitEditorial(CorpusCampaignTask task, CorpusEditorialDecision decision, string decisionHash, IReadOnlyList<int> reserved)
    {
        using var db = Open(true); using var transaction = db.BeginTransaction();
        using (var previous = CorpusCampaignControl.Sql(db, transaction, "SELECT state,decision_hash,job_hash FROM editorial WHERE task_id=$task", ("$task", task.Id)))
        using (var reader = previous.ExecuteReader())
        {
            V4Rules.Require(reader.Read() && reader.GetString(2) == decision.JobHash, "CAMPAIGN_EDITORIAL_JOB_MISMATCH");
            if (reader.GetString(0) == "DONE") { V4Rules.Require(reader.GetString(1) == decisionHash, "CAMPAIGN_EDITORIAL_DECISION_DRIFT"); return; }
        }
        long count; using (var total = CorpusCampaignControl.Sql(db, transaction, "SELECT count(*) FROM records WHERE task_id=$task", ("$task", task.Id))) count = (long)total.ExecuteScalar()!;
        V4Rules.Require(count == decision.Rows.Count && decision.Rows.Select(r => r.Number).Distinct().Count() == count, "CAMPAIGN_EDITORIAL_RECORD_COUNT");
        foreach (var row in decision.Rows)
        {
            var state = row.State == "APPROVED" && !reserved.Contains(row.Number) ? "EDITORIAL_REJECTED" : row.State;
            var reason = row.State == "APPROVED" && state != "APPROVED" ? "GLOBAL_APPROVED_DUPLICATE" : row.Reason;
            using var update = CorpusCampaignControl.Sql(db, transaction, "UPDATE records SET state=$state,reason=$reason WHERE task_id=$task AND number=$number AND state='PENDING'",
                ("$state", state), ("$reason", reason), ("$task", task.Id), ("$number", row.Number)); update.ExecuteNonQuery();
        }
        using var finish = CorpusCampaignControl.Sql(db, transaction, "UPDATE editorial SET state='DONE',decision_hash=$hash WHERE task_id=$task", ("$hash", decisionHash), ("$task", task.Id));
        finish.ExecuteNonQuery(); transaction.Commit();
    }
    public void CheckPolicy(string policyId, string approvalHash)
    {
        using var db = Open(false); using var query = CorpusCampaignControl.Sql(db, null, "SELECT policy_id,approval_hash FROM campaign_policy WHERE singleton=1");
        using var reader = query.ExecuteReader(); V4Rules.Require(reader.Read() && reader.GetString(0) == policyId && reader.GetString(1) == approvalHash, "CAMPAIGN_EXPORT_POLICY_DRIFT");
    }
    public IReadOnlyList<CorpusCampaignAcceptedRow> ApprovedRows()
    {
        using var db = Open(false); using var query = CorpusCampaignControl.Sql(db, null, """
            SELECT t.id,t.run_id,t.quota_id,r.number,r.original,r.json_line,e.decision_hash FROM records r
            JOIN tasks t ON t.id=r.task_id JOIN editorial e ON e.task_id=t.id WHERE r.state='APPROVED' AND e.state='DONE'
            ORDER BY t.ordinal,r.number
            """);
        using var reader = query.ExecuteReader(); var result = new List<CorpusCampaignAcceptedRow>();
        while (reader.Read())
        { V4Rules.Require(result.Count < V4Rules.MaxRecords, "CAMPAIGN_EXPORT_RECORD_CAP"); result.Add(new(reader.GetString(0), reader.GetString(1), reader.GetString(2), reader.GetInt32(3), reader.GetString(4), reader.GetString(5), reader.GetString(6))); }
        return result;
    }

    public CorpusCampaignTask? Reserve(CorpusCampaignPlan plan, bool acceptedQuotas = false)
    {
        using var db = Open(true); using var transaction = db.BeginTransaction();
        using (var active = CorpusCampaignControl.Sql(db, transaction, "SELECT id,ordinal,quota_id,run_id,state FROM tasks WHERE state<>'DONE' ORDER BY ordinal LIMIT 1"))
        using (var reader = active.ExecuteReader()) if (reader.Read()) return new(reader.GetString(0), reader.GetInt32(1), reader.GetString(2), reader.GetString(3), reader.GetString(4));
        int count; using (var total = CorpusCampaignControl.Sql(db, transaction, "SELECT count(*) FROM tasks")) count = (int)(long)total.ExecuteScalar()!;
        if (count >= plan.Spec.MaxRequests) return null;
        var selected = plan.Spec.Quotas[count % plan.Spec.Quotas.Count];
        if (acceptedQuotas)
        {
            var choices = plan.Spec.Quotas.Where(q => Approved(q.Id, db, transaction) < q.AcceptedTarget).ToArray();
            if (choices.Length == 0) return null;
            selected = choices.OrderBy(q => Attempts(q.Id, db, transaction)).First();
        }
        var task = new CorpusCampaignTask(Guid.NewGuid().ToString("N"), count + 1, selected.Id, "", "NEW_RESERVED");
        using var insert = CorpusCampaignControl.Sql(db, transaction, "INSERT INTO tasks(id,ordinal,quota_id,run_id,state) VALUES($id,$n,$quota,'','RESERVED')", ("$id", task.Id), ("$n", task.Ordinal), ("$quota", task.QuotaId));
        insert.ExecuteNonQuery(); transaction.Commit(); return task;
    }
    public void Bind(CorpusCampaignTask task, string runId)
    {
        using var db = Open(true); using var update = CorpusCampaignControl.Sql(db, null, "UPDATE tasks SET run_id=$run,state='BOUND' WHERE id=$id AND (run_id='' OR run_id=$run)", ("$run", runId), ("$id", task.Id));
        V4Rules.Require(update.ExecuteNonQuery() == 1, "CAMPAIGN_TASK_RUN_DRIFT");
    }
    public void Reject(CorpusCampaignTask task, bool issued)
    {
        using var db = Open(true); using var update = CorpusCampaignControl.Sql(db, null, "UPDATE tasks SET state='DONE',issued=$issued,rejected_batch=1 WHERE id=$id", ("$issued", issued ? 1 : 0), ("$id", task.Id)); update.ExecuteNonQuery();
    }
    public void Commit(CorpusCampaignTask task, CorpusCampaignDraftSnapshot snapshot, IReadOnlyList<CorpusIndexItem> indexed)
    {
        using var db = Open(true); using var transaction = db.BeginTransaction();
        using (var current = CorpusCampaignControl.Sql(db, transaction, "SELECT state FROM tasks WHERE id=$id", ("$id", task.Id)))
            if ((string?)current.ExecuteScalar() == "DONE") return;
        foreach (var row in snapshot.Rows)
        {
            var duplicate = row.ExistingExact || row.Diagnostics.Contains("EXACT_DUPLICATE", StringComparer.Ordinal);
            using var insert = CorpusCampaignControl.Sql(db, transaction, "INSERT INTO records VALUES($task,$number,$index,$text,$normalized,$state,$reason,$json)",
                ("$task", task.Id), ("$number", row.Number), ("$index", indexed.Single(i => i.Number == row.Number).TextId), ("$text", row.Text), ("$normalized", V4Rules.Normalize(row.Text)),
                ("$state", duplicate ? "REJECTED" : "PENDING"), ("$reason", duplicate ? "EXACT_DUPLICATE" : string.Join(',', row.Diagnostics)), ("$json", row.JsonLine)); insert.ExecuteNonQuery();
        }
        using var finish = CorpusCampaignControl.Sql(db, transaction, "UPDATE tasks SET state='DONE',issued=1 WHERE id=$id", ("$id", task.Id)); finish.ExecuteNonQuery(); transaction.Commit();
    }
}
