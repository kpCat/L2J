using System.Globalization;
using Microsoft.Data.Sqlite;
using static PhantomSemanticStudio.Core.CorpusCampaignControl;

namespace PhantomSemanticStudio.Core;

internal sealed record OpenAiSpent(decimal Campaign = 0, decimal Daily = 0, decimal Monthly = 0, decimal Account = 0);

/// <summary>A Windows user's shared OpenAI reservations, including unresolved requests from other Studio workspaces.</summary>
internal sealed class OpenAiSpendLedger
{
    private const decimal Units = 1_000_000_000m;
    private readonly string path;

    internal OpenAiSpendLedger(WorkspaceStore workspace, string? testDirectory = null)
    {
        var directory = testDirectory ?? Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "PhantomSemanticStudio", "openai-ledger");
        directory = PathSafety.Canonical(directory);
        PathSafety.AssertDisjoint(workspace.Root, directory);
        PathSafety.AssertDisjoint(workspace.ProtectedRoot, directory);
        _ = new V4Workspace(directory, workspace.ProtectedRoot);
        path = PathSafety.ResolveRelative(directory, "spend.sqlite");
    }

    internal static long Amount(decimal usd) => checked((long)decimal.Ceiling(usd * Units));
    internal static decimal Usd(long amount) => amount / Units;

    internal FileStream AcquireWorker()
    {
        var directory = Path.GetDirectoryName(path)!;
        var file = PathSafety.ResolveRelative(directory, "worker.lock");
        Directory.CreateDirectory(directory);
        try { return new(file, FileMode.OpenOrCreate, FileAccess.ReadWrite, FileShare.None); }
        catch (IOException) { throw new InvalidDataException("OPENAI_ACCOUNT_WORKER_BUSY"); }
    }

    private SqliteConnection Open(bool write)
    {
        foreach (var suffix in new[] { "", "-journal", "-wal", "-shm" }) PathSafety.AssertNoReparsePoints(path + suffix);
        if (write) Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        var db = CorpusStore.Connect(path, write);
        try
        {
            if (write)
            {
                using var setup = Sql(db, null, """
                    PRAGMA synchronous=FULL;
                    PRAGMA max_page_count=65536;
                    CREATE TABLE IF NOT EXISTS reservations(
                        id TEXT PRIMARY KEY,campaign TEXT NOT NULL,day TEXT NOT NULL,month TEXT NOT NULL,
                        held INTEGER NOT NULL CHECK(held>=0),cost INTEGER CHECK(cost>=0));
                    """);
                setup.ExecuteNonQuery();
            }
            return db;
        }
        catch { db.Dispose(); throw; }
    }

    internal OpenAiSpent Spent(string campaign, DateTimeOffset now)
    {
        if (!File.Exists(path)) return new();
        using var db = Open(false);
        return Spent(db, null, campaign, now);
    }

    private static OpenAiSpent Spent(SqliteConnection db, SqliteTransaction? transaction, string campaign, DateTimeOffset now)
    {
        using var query = Sql(db, transaction, """
            SELECT coalesce(sum(CASE WHEN campaign=$campaign THEN debit ELSE 0 END),0),
                coalesce(sum(CASE WHEN day=$day THEN debit ELSE 0 END),0),
                coalesce(sum(CASE WHEN month=$month THEN debit ELSE 0 END),0),coalesce(sum(debit),0)
            FROM (SELECT campaign,day,month,coalesce(cost,held) debit FROM reservations)
            """, ("$campaign", campaign), ("$day", Day(now)), ("$month", Month(now)));
        using var reader = query.ExecuteReader();
        reader.Read();
        return new(Usd(reader.GetInt64(0)), Usd(reader.GetInt64(1)), Usd(reader.GetInt64(2)), Usd(reader.GetInt64(3)));
    }

    internal bool Reserve(string campaign, OpenAiWork work, bool pilot, OpenAiBudget budget)
    {
        budget.Validate();
        using var db = Open(true);
        using var transaction = db.BeginTransaction();
        var usage = Spent(db, transaction, campaign, work.AtUtc);
        if (Usd(work.Held) > budget.Available(pilot, usage.Campaign, usage.Campaign, usage.Daily, usage.Account, usage.Monthly))
            return false;
        using var write = Sql(db, transaction, """
            INSERT INTO reservations(id,campaign,day,month,held) VALUES($id,$campaign,$day,$month,$held)
            """, ("$id", work.Id), ("$campaign", campaign), ("$day", Day(work.AtUtc)), ("$month", Month(work.AtUtc)), ("$held", work.Held));
        write.ExecuteNonQuery();
        transaction.Commit();
        return true;
    }

    internal void Settle(string campaign, string id, long cost)
    {
        using var db = Open(true);
        using var write = Sql(db, null, """
            UPDATE reservations SET cost=$cost WHERE id=$id AND campaign=$campaign AND (cost IS NULL OR cost=$cost)
            """, ("$id", id), ("$campaign", campaign), ("$cost", cost));
        V4Rules.Require(write.ExecuteNonQuery() == 1, "OPENAI_LEDGER_RECEIPT_DRIFT");
    }

    internal void ReleaseUndispatched(string campaign, string id)
    {
        if (!File.Exists(path)) return;
        using var db = Open(true);
        using var write = Sql(db, null, "UPDATE reservations SET cost=0 WHERE id=$id AND campaign=$campaign AND cost IS NULL",
            ("$id", id), ("$campaign", campaign));
        write.ExecuteNonQuery();
    }

    private static string Day(DateTimeOffset at) => at.UtcDateTime.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture);
    private static string Month(DateTimeOffset at) => at.UtcDateTime.ToString("yyyy-MM", CultureInfo.InvariantCulture);
}
