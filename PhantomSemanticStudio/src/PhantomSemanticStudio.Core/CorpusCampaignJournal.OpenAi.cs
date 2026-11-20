using System.Text.Json;
using Microsoft.Data.Sqlite;
using static PhantomSemanticStudio.Core.CorpusCampaignControl;

namespace PhantomSemanticStudio.Core;

internal sealed record OpenAiWork(string Id, string LaneId, int Batch, int BatchTarget, int Requested, string PromptHash,
    string SceneHash, string RequestedModel, long Held, DateTimeOffset AtUtc, string State = "PREPARED",
    OpenAiReply? Reply = null, long? Cost = null);

internal sealed record OpenAiCheckedRow(int Number, string Text, string JsonLine, long TextId, string Diagnostic, bool Unique);
internal sealed record OpenAiLaneCounts(int Batch, int InBatch, int Unique, int Attempts);

internal sealed partial class CorpusCampaignJournal
{
    private void EnsureOpenAi()
    {
        using (var read = Open(false))
        using (var exists = Sql(read, null, "SELECT count(*) FROM sqlite_schema WHERE type='table' AND name='openai_grades'"))
            if ((long)exists.ExecuteScalar()! == 1) return;

        using var db = Open(true);
        using var transaction = db.BeginTransaction();
        using var schema = Sql(db, transaction, """
            CREATE TABLE IF NOT EXISTS openai_attempts(
                task_id TEXT PRIMARY KEY REFERENCES tasks(id), batch INTEGER NOT NULL, batch_target INTEGER NOT NULL,
                requested INTEGER NOT NULL, prompt_hash TEXT NOT NULL, scene_hash TEXT NOT NULL,
                model TEXT NOT NULL, held INTEGER NOT NULL, at_utc TEXT NOT NULL, state TEXT NOT NULL,
                reply TEXT, cost INTEGER, generated INTEGER NOT NULL DEFAULT 0, valid INTEGER NOT NULL DEFAULT 0,
                unique_rows INTEGER NOT NULL DEFAULT 0);
            CREATE INDEX IF NOT EXISTS ix_openai_quota ON tasks(quota_id,ordinal);
            CREATE TABLE IF NOT EXISTS openai_grades(
                task_id TEXT NOT NULL, number INTEGER NOT NULL, grade TEXT NOT NULL,
                PRIMARY KEY(task_id,number), FOREIGN KEY(task_id,number) REFERENCES records(task_id,number));
            """);
        schema.ExecuteNonQuery();
        transaction.Commit();
    }

    internal bool PrepareOpenAiWork(OpenAiWork work)
    {
        EnsureOpenAi();
        using var db = Open(true);
        using var transaction = db.BeginTransaction();
        using (var allowed = Sql(db, transaction, "SELECT desired='RUN' AND state='RUNNING' FROM campaign"))
            if ((long)allowed.ExecuteScalar()! != 1) return false;
        using (var task = Sql(db, transaction, """
            INSERT INTO tasks(id,ordinal,quota_id,run_id,state,issued)
                VALUES($id,(SELECT coalesce(max(ordinal),0)+1 FROM tasks),$lane,$id,'RESERVED',0)
            """, ("$id", work.Id), ("$lane", work.LaneId))) task.ExecuteNonQuery();
        using (var attempt = Sql(db, transaction, """
            INSERT INTO openai_attempts(task_id,batch,batch_target,requested,prompt_hash,scene_hash,model,held,at_utc,state)
                VALUES($id,$batch,$target,$n,$prompt,$scene,$model,$held,$at,'PREPARED')
            """, ("$id", work.Id), ("$batch", work.Batch), ("$target", work.BatchTarget), ("$n", work.Requested),
            ("$prompt", work.PromptHash), ("$scene", work.SceneHash), ("$model", work.RequestedModel),
            ("$held", work.Held), ("$at", work.AtUtc.ToString("O")))) attempt.ExecuteNonQuery();
        transaction.Commit();
        return true;
    }

    internal bool IssueOpenAiWork(string taskId)
    {
        using var db = Open(true);
        using var transaction = db.BeginTransaction();
        using (var issue = Sql(db, transaction, """
            UPDATE openai_attempts SET state='ISSUED' WHERE task_id=$id AND state='PREPARED'
                AND EXISTS(SELECT 1 FROM campaign WHERE desired='RUN' AND state='RUNNING')
            """, ("$id", taskId)))
            if (issue.ExecuteNonQuery() != 1) return false;
        using (var task = Sql(db, transaction, "UPDATE tasks SET state='BOUND',issued=1 WHERE id=$id", ("$id", taskId)))
            task.ExecuteNonQuery();
        transaction.Commit();
        return true;
    }

    internal void AbandonOpenAiWork(string taskId)
    {
        using var db = Open(true);
        using var transaction = db.BeginTransaction();
        using (var skip = Sql(db, transaction, """
            UPDATE openai_attempts SET state='NOT_DISPATCHED',cost=0 WHERE task_id=$id AND state='PREPARED'
            """, ("$id", taskId)))
            V4Rules.Require(skip.ExecuteNonQuery() == 1, "OPENAI_RECEIPT_STATE");
        using (var task = Sql(db, transaction, "UPDATE tasks SET state='DONE' WHERE id=$id", ("$id", taskId)))
            task.ExecuteNonQuery();
        transaction.Commit();
    }

    internal void ReceiveOpenAi(OpenAiWork work, OpenAiReply reply, long? cost)
    {
        using var db = Open(true);
        using var change = Sql(db, null, """
            UPDATE openai_attempts SET state='RECEIVED',reply=$reply,cost=$cost WHERE task_id=$id AND state='ISSUED'
            """, ("$id", work.Id), ("$reply", JsonSerializer.Serialize(reply, PromptProfiles.CompactJson)),
            ("$cost", (object?)cost ?? DBNull.Value));
        V4Rules.Require(change.ExecuteNonQuery() == 1, "OPENAI_RECEIPT_STATE");
    }

    internal void CommitOpenAi(OpenAiWork work, string outcome, int generated, IReadOnlyList<OpenAiCheckedRow> rows)
    {
        using var db = Open(true);
        using var transaction = db.BeginTransaction();
        using (var finish = Sql(db, transaction, """
            UPDATE openai_attempts SET state=$state,generated=$generated,valid=$valid,unique_rows=$unique
            WHERE task_id=$id AND state='RECEIVED'
            """, ("$id", work.Id), ("$state", outcome), ("$generated", generated), ("$valid", rows.Count),
            ("$unique", rows.Count(row => row.Unique))))
            V4Rules.Require(finish.ExecuteNonQuery() == 1, "OPENAI_RECEIPT_STATE");
        foreach (var row in rows)
        {
            using var insert = Sql(db, transaction, """
                INSERT INTO records(task_id,number,text_id,original,normalized,state,reason,json_line)
                VALUES($id,$n,$text_id,$text,$normalized,$state,$reason,$json)
                """, ("$id", work.Id), ("$n", row.Number), ("$text_id", row.TextId), ("$text", row.Text),
                ("$normalized", V4Rules.Normalize(row.Text)), ("$state", row.Unique ? "PENDING" : "REJECTED"),
                ("$reason", row.Diagnostic), ("$json", row.JsonLine));
            insert.ExecuteNonQuery();
        }
        using (var finishTask = Sql(db, transaction, "UPDATE tasks SET state='DONE',rejected_batch=$rejected WHERE id=$id",
            ("$id", work.Id), ("$rejected", outcome is "DRAFT_SAVED" or "RATE_LIMIT" ? 0 : 1))) finishTask.ExecuteNonQuery();
        transaction.Commit();
    }

    private const string WorkColumns = """
        SELECT t.id,t.quota_id,a.batch,a.batch_target,a.requested,a.prompt_hash,a.scene_hash,
               a.model,a.held,a.at_utc,a.state,a.reply,a.cost
        FROM openai_attempts a JOIN tasks t ON t.id=a.task_id
        """;

    private static OpenAiWork ReadWork(SqliteDataReader reader) => new(
        reader.GetString(0), reader.GetString(1), reader.GetInt32(2), reader.GetInt32(3), reader.GetInt32(4),
        reader.GetString(5), reader.GetString(6), reader.GetString(7), reader.GetInt64(8),
        DateTimeOffset.Parse(reader.GetString(9), System.Globalization.CultureInfo.InvariantCulture), reader.GetString(10),
        reader.IsDBNull(11) ? null : JsonSerializer.Deserialize<OpenAiReply>(reader.GetString(11), PromptProfiles.CompactJson),
        reader.IsDBNull(12) ? null : reader.GetInt64(12));

    internal OpenAiWork? LatestOpenAi(bool includeUndispatched = true)
    {
        EnsureOpenAi();
        using var db = Open(false);
        var filter = includeUndispatched ? "" : " WHERE t.issued=1";
        using var query = Sql(db, null, WorkColumns + filter + " ORDER BY t.ordinal DESC LIMIT 1");
        using var reader = query.ExecuteReader();
        return reader.Read() ? ReadWork(reader) : null;
    }

    internal OpenAiWork OpenAiRequest(string task, int row)
    {
        EnsureOpenAi();
        using var db = Open(false);
        using var query = Sql(db, null, WorkColumns + """
             WHERE t.id=$task AND EXISTS(SELECT 1 FROM records WHERE task_id=t.id AND number=$row)
            """, ("$task", task), ("$row", row));
        using var reader = query.ExecuteReader();
        V4Rules.Require(reader.Read(), "OPENAI_SAMPLE_NOT_FOUND");
        return ReadWork(reader);
    }

    internal IReadOnlyDictionary<string, OpenAiLaneCounts> OpenAiProgress()
    {
        EnsureOpenAi();
        using var db = Open(false);
        using var query = Sql(db, null, """
            WITH batches AS (
                SELECT t.quota_id,a.batch,sum(a.unique_rows) n,count(*) attempts
                FROM openai_attempts a JOIN tasks t ON t.id=a.task_id WHERE t.issued=1 GROUP BY t.quota_id,a.batch
            ), totals AS (SELECT quota_id,max(batch) last,sum(n) n,sum(attempts) attempts FROM batches GROUP BY quota_id)
            SELECT b.quota_id,b.batch,b.n,t.n,t.attempts FROM batches b JOIN totals t ON t.quota_id=b.quota_id AND t.last=b.batch
            """);
        using var reader = query.ExecuteReader();
        var result = new Dictionary<string, OpenAiLaneCounts>(StringComparer.Ordinal);
        while (reader.Read()) result.Add(reader.GetString(0), new(reader.GetInt32(1), reader.GetInt32(2), reader.GetInt32(3), reader.GetInt32(4)));
        return result;
    }

    internal string OpenAiResolvedModel()
    {
        using var db = Open(false);
        using var query = Sql(db, null, """
            SELECT json_extract(reply,'$.Model') FROM openai_attempts
            WHERE state IN ('DRAFT_SAVED','INVALID_OUTPUT') AND json_extract(reply,'$.Model')<>''
            ORDER BY at_utc LIMIT 1
            """);
        return query.ExecuteScalar() as string ?? "";
    }

    internal IReadOnlyList<string> OpenAiPriorPilotTexts(IEnumerable<string> lanes)
    {
        var matching = lanes.ToHashSet(StringComparer.Ordinal);
        using var db = Open(false);
        using var query = Sql(db, null, "SELECT t.quota_id,r.original FROM records r JOIN tasks t ON t.id=r.task_id");
        using var reader = query.ExecuteReader();
        var texts = new List<string>();
        while (reader.Read())
            if (matching.Contains(reader.GetString(0))) texts.Add(reader.GetString(1));
        return texts;
    }

    internal bool OpenAiRegressed(int minimum)
    {
        using var db = Open(false);
        using var query = Sql(db, null, """
            SELECT count(*),coalesce(sum(requested),0),coalesce(sum(unique_rows),0) FROM (
                SELECT a.requested,a.unique_rows FROM openai_attempts a JOIN tasks t ON t.id=a.task_id
                WHERE a.state IN ('DRAFT_SAVED','INVALID_OUTPUT') ORDER BY t.ordinal DESC LIMIT 5)
            """);
        using var reader = query.ExecuteReader();
        reader.Read();
        if (reader.GetInt32(0) == 5 && reader.GetInt32(2) * 100 < reader.GetInt32(1) * minimum) return true;
        reader.Close();

        using var grades = Sql(db, null, """
            SELECT g.grade FROM openai_grades g JOIN tasks t ON t.id=g.task_id ORDER BY t.ordinal DESC,g.number DESC LIMIT 20
            """);
        using var review = grades.ExecuteReader();
        var counts = new List<OpenAiGrade>();
        while (review.Read()) counts.Add(JsonSerializer.Deserialize<OpenAiGrade>(review.GetString(0), PromptProfiles.CompactJson)!);
        return counts.Count >= 12 && counts.Count(grade => grade.Pass) * 100 < counts.Count * minimum;
    }

    internal OpenAiCampaignView OpenAiView(OpenAiCampaignPlan plan)
    {
        EnsureOpenAi();
        var status = Status();
        using var db = Open(false);
        var byLane = new Dictionary<string, OpenAiCampaignTotals>(StringComparer.Ordinal);
        using (var progress = Sql(db, null, """
            SELECT t.quota_id,count(*),sum(a.generated),sum(a.valid),sum(a.unique_rows),coalesce(sum(a.cost),0),
                sum(CASE WHEN a.cost IS NULL THEN a.held ELSE 0 END)
            FROM openai_attempts a JOIN tasks t ON t.id=a.task_id GROUP BY t.quota_id
            """))
        using (var reader = progress.ExecuteReader())
            while (reader.Read()) byLane.Add(reader.GetString(0), new(Requests: reader.GetInt64(1), Generated: reader.GetInt64(2),
                Valid: reader.GetInt64(3), Unique: reader.GetInt64(4), CostUsd: OpenAiSpendLedger.Usd(reader.GetInt64(5)),
                HeldUsd: OpenAiSpendLedger.Usd(reader.GetInt64(6))));
        using (var reviews = Sql(db, null, """
            SELECT t.quota_id,count(*),sum(CASE WHEN r.state='APPROVED' THEN 1 ELSE 0 END)
            FROM openai_grades g JOIN tasks t ON t.id=g.task_id JOIN records r ON r.task_id=g.task_id AND r.number=g.number
            GROUP BY t.quota_id
            """))
        using (var reader = reviews.ExecuteReader())
            while (reader.Read())
            {
                var key = reader.GetString(0);
                byLane[key] = byLane[key] with { Reviewed = reader.GetInt64(1), Approved = reader.GetInt64(2) };
            }
        using var completed = Sql(db, null, """
            SELECT count(*) FROM (
                SELECT 1 FROM openai_attempts a JOIN tasks t ON t.id=a.task_id GROUP BY t.quota_id,a.batch
                HAVING sum(a.unique_rows)>=max(a.batch_target)
            )
            """);
        var totals = new OpenAiCampaignTotals(status.Issued, byLane.Values.Sum(l => l.Generated), byLane.Values.Sum(l => l.Valid),
            byLane.Values.Sum(l => l.Unique), byLane.Values.Sum(l => l.Reviewed), byLane.Values.Sum(l => l.Approved),
            (long)completed.ExecuteScalar()!, byLane.Values.Sum(l => l.CostUsd), byLane.Values.Sum(l => l.HeldUsd));
        var list = plan.Lanes.GroupBy(lane => lane.Category).Select(group =>
        {
            var same = group.Select(lane => byLane.GetValueOrDefault(lane.Id) ?? new()).ToArray();
            return new OpenAiQuotaProgress(group.Key, group.First().Label, group.First().Family,
                string.Join(", ", group.Select(lane => lane.Mode).Distinct()), group.Sum(lane => lane.Target),
                same.Sum(l => l.Generated), same.Sum(l => l.Valid), same.Sum(l => l.Unique),
                same.Sum(l => l.Reviewed), same.Sum(l => l.Approved));
        }).ToArray();
        var last = LatestOpenAi();
        var current = last == null ? null : OpenAiProgress().GetValueOrDefault(last.LaneId);
        var average = totals.Unique > 0 ? (totals.CostUsd + totals.HeldUsd) / totals.Unique : 0;
        var forecast = average > 0 ? totals.CostUsd + totals.HeldUsd + average * Math.Max(0, plan.Target - totals.Unique)
            : plan.Settings.Price == null ? 0 : plan.Settings.Price.Reserve(14000, 4096) * plan.MinimumPosts;
        return new(status.State, status.Stage, totals, list, last?.LaneId ?? "", last?.Batch ?? 0, current?.InBatch ?? 0,
            last?.BatchTarget ?? 0, forecast);
    }

    internal IReadOnlyList<OpenAiCampaignRequestLog> OpenAiLog(int limit)
    {
        EnsureOpenAi();
        using var db = Open(false);
        using var query = Sql(db, null, WorkColumns + " ORDER BY t.ordinal DESC LIMIT $limit", ("$limit", limit));
        using var reader = query.ExecuteReader();
        var result = new List<OpenAiCampaignRequestLog>();
        while (reader.Read())
        {
            var work = ReadWork(reader);
            result.Add(new(work.Id, work.LaneId, work.Batch, work.Requested, work.State, work.PromptHash, work.SceneHash,
                string.IsNullOrEmpty(work.Reply?.Model) ? work.RequestedModel : work.Reply.Model, work.Reply?.Usage, OpenAiSpendLedger.Usd(work.Held),
                work.Cost.HasValue ? OpenAiSpendLedger.Usd(work.Cost.Value) : null, work.AtUtc));
        }
        return result;
    }

    internal IReadOnlyList<OpenAiSample> OpenAiSamples(OpenAiCampaignPlan plan, int page, bool approvedOnly = false)
    {
        EnsureOpenAi();
        V4Rules.Require(page is >= 0 and <= 20000, "OPENAI_RESULT_PAGE");
        if (plan.Pilot && page > 0) return [];
        var lanes = plan.Lanes.ToDictionary(lane => lane.Id, StringComparer.Ordinal);
        using var db = Open(false);
        var filter = approvedOnly ? " WHERE r.state='APPROVED' " : "";
        using var query = Sql(db, null, """
            SELECT t.id,r.number,t.quota_id,r.original,r.state,r.reason,g.grade,a.model,a.prompt_hash,a.scene_hash,
                   a.reply,a.cost,r.json_line
            FROM records r JOIN tasks t ON t.id=r.task_id JOIN openai_attempts a ON a.task_id=t.id
                LEFT JOIN openai_grades g ON g.task_id=t.id AND g.number=r.number
            """ + filter + " ORDER BY t.ordinal,r.number LIMIT $n OFFSET $skip",
            ("$n", plan.Pilot ? 60 : 100), ("$skip", 100L * page));
        using var reader = query.ExecuteReader();
        var result = new List<OpenAiSample>();
        while (reader.Read())
        {
            var lane = lanes[reader.GetString(2)];
            var reply = JsonSerializer.Deserialize<OpenAiReply>(reader.GetString(10), PromptProfiles.CompactJson)!;
            result.Add(new(reader.GetString(0), reader.GetInt32(1), lane.Category, lane.Mode, lane.Scene, lane.Holdout, reader.GetString(3),
                reader.GetString(4), reader.GetString(5), reader.IsDBNull(6) ? null :
                JsonSerializer.Deserialize<OpenAiGrade>(reader.GetString(6), PromptProfiles.CompactJson),
                plan.Pilot ? lane.Format : plan.Settings.Format, reply.Model.Length == 0 ? reader.GetString(7) : reply.Model, reader.GetString(8),
                reader.GetString(9), reply.Usage, reader.IsDBNull(11) ? null : OpenAiSpendLedger.Usd(reader.GetInt64(11)),
                reader.GetString(12)));
        }
        return plan.Pilot ? result.OrderBy(row => V4Rules.Hash(V4Rules.Utf8.GetBytes(row.Id + ":" + row.Number)), StringComparer.Ordinal).ToArray() : result;
    }

    internal void OpenAiGrade(string task, int row, OpenAiGrade grade)
    {
        EnsureOpenAi();
        using var db = Open(true);
        using var transaction = db.BeginTransaction();
        using (var save = Sql(db, transaction, """
            INSERT INTO openai_grades VALUES($id,$row,$grade)
            ON CONFLICT(task_id,number) DO UPDATE SET grade=excluded.grade
            """, ("$id", task), ("$row", row), ("$grade", JsonSerializer.Serialize(grade, PromptProfiles.CompactJson))))
            save.ExecuteNonQuery();
        using (var invalidate = Sql(db, transaction, """
            UPDATE records SET state='PENDING' WHERE task_id=$id AND number=$row AND state='APPROVED'
            """, ("$id", task), ("$row", row))) invalidate.ExecuteNonQuery();
        transaction.Commit();
    }

    internal void ApproveOpenAiRow(string task, int row)
    {
        EnsureOpenAi();
        using var db = Open(true);
        using var transaction = db.BeginTransaction();
        using (var lookup = Sql(db, transaction, "SELECT grade FROM openai_grades WHERE task_id=$id AND number=$n",
            ("$id", task), ("$n", row)))
        {
            var json = lookup.ExecuteScalar() as string;
            V4Rules.Require(json != null && JsonSerializer.Deserialize<OpenAiGrade>(json, PromptProfiles.CompactJson)!.Pass,
                "OPENAI_ROW_NEEDS_POSITIVE_REVIEW");
        }
        using (var approve = Sql(db, transaction, """
            UPDATE records SET state='APPROVED' WHERE task_id=$id AND number=$n AND state='PENDING'
            """, ("$id", task), ("$n", row)))
            V4Rules.Require(approve.ExecuteNonQuery() == 1, "OPENAI_ROW_NOT_APPROVABLE");
        transaction.Commit();
    }
}
