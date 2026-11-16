using System.Text.Json;
using Microsoft.Data.Sqlite;

namespace PhantomSemanticStudio.Core;

public sealed record CorpusCampaignStatus(string Id, string State, string Desired, long Attempts, long Issued, long SchemaValid,
    long Rejected, long Pending, long Reviewed, long Approved, long RejectedBatches, string Stage);
internal sealed record CorpusCampaignTask(string Id, int Ordinal, string QuotaId, string RunId, string State);

/// <summary>Short journal transactions and controls remain available while the worker owns workspace.lock.</summary>
public static class CorpusCampaignControl
{
    internal static string PathFor(string root, string source, string id, string hash)
    {
        root = PathSafety.Canonical(root); source = PathSafety.RepositoryBoundary(PathSafety.Canonical(source));
        PathSafety.AssertDisjoint(source, root);
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
        using var command = Sql(db, null, """
            SELECT state,desired,stage,(SELECT count(*) FROM tasks),(SELECT coalesce(sum(issued),0) FROM tasks),
            (SELECT count(*) FROM records),(SELECT count(*) FROM records WHERE state='REJECTED'),
            (SELECT count(*) FROM records WHERE state='PENDING'),(SELECT count(*) FROM tasks WHERE rejected_batch=1)
            FROM campaign WHERE singleton=1
            """);
        using var reader = command.ExecuteReader(); reader.Read();
        return new(id, reader.GetString(0), reader.GetString(1), reader.GetInt64(3), reader.GetInt64(4), reader.GetInt64(5),
            reader.GetInt64(6), reader.GetInt64(7), 0, 0, reader.GetInt64(8), reader.GetString(2));
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

    public CorpusCampaignTask? Reserve(CorpusCampaignPlan plan)
    {
        using var db = Open(true); using var transaction = db.BeginTransaction();
        using (var active = CorpusCampaignControl.Sql(db, transaction, "SELECT id,ordinal,quota_id,run_id,state FROM tasks WHERE state<>'DONE' ORDER BY ordinal LIMIT 1"))
        using (var reader = active.ExecuteReader()) if (reader.Read()) return new(reader.GetString(0), reader.GetInt32(1), reader.GetString(2), reader.GetString(3), reader.GetString(4));
        int count; using (var total = CorpusCampaignControl.Sql(db, transaction, "SELECT count(*) FROM tasks")) count = (int)(long)total.ExecuteScalar()!;
        if (count >= plan.Spec.MaxRequests) return null;
        var task = new CorpusCampaignTask(Guid.NewGuid().ToString("N"), count + 1, plan.Spec.Quotas[count % plan.Spec.Quotas.Count].Id, "", "RESERVED");
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
