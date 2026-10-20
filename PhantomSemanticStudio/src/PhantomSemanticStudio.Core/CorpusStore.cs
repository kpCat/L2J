using System.Globalization;
using System.Security.Cryptography;
using System.Text.Json;
using Microsoft.Data.Sqlite;

namespace PhantomSemanticStudio.Core;

/// <summary>Immutable private DB, isolated from session.json. Each connection belongs to one worker.</summary>
public sealed class CorpusStore(WorkspaceStore workspace)
{
    private const long MaxDatabaseBytes = 1024L * 1024 * 1024;
    private sealed record Receipt(CorpusMetadata Metadata, string DatabaseHash);
    private string Root { get { workspace.Check(); return PathSafety.ResolveRelative(workspace.Root, "corpora"); } }
    private static string Id(string id) => Guid.TryParseExact(id, "N", out _) ? id : throw new CorpusException("INVALID_CORPUS_ID");
    internal static string HashStream(Stream stream, CancellationToken token)
    {
        using var hash = IncrementalHash.CreateHash(HashAlgorithmName.SHA256); var buffer = new byte[65536]; int read;
        while ((read = stream.Read(buffer)) > 0) { token.ThrowIfCancellationRequested(); hash.AppendData(buffer, 0, read); }
        return Convert.ToHexString(hash.GetHashAndReset()).ToLowerInvariant();
    }
    internal static SqliteConnection Connect(string path, bool write, CancellationToken token = default)
    {
        PathSafety.AssertNoReparsePoints(path);
        var db = new SqliteConnection(new SqliteConnectionStringBuilder { DataSource = path, Pooling = false,
            Mode = write ? SqliteOpenMode.ReadWriteCreate : SqliteOpenMode.ReadOnly, DefaultTimeout = 2 }.ToString());
        try
        {
            token.ThrowIfCancellationRequested(); db.Open();
            // SqliteCommand.Cancel is a documented no-op in 10.0.12. This callback
            // runs within prepare/step, checks only the token, and never touches the DB.
            SQLitePCL.raw.sqlite3_progress_handler(db.Handle, 1000,
                static state => ((CancellationToken)state).IsCancellationRequested ? 1 : 0, token);
            using var command = db.CreateCommand();
            command.CommandText = "PRAGMA cache_size=-8192; PRAGMA temp_store=MEMORY; PRAGMA trusted_schema=OFF;";
            command.ExecuteNonQuery(); return db;
        }
        catch { db.Dispose(); throw; }
    }
    public CorpusMetadata Import(string zipPath, CancellationToken token = default, IProgress<CorpusProgress>? progress = null)
    {
        token.ThrowIfCancellationRequested(); var root = Root;
        Directory.CreateDirectory(root); PathSafety.AssertNoReparsePoints(root);
        var inputPath = PathSafety.Canonical(zipPath); PathSafety.AssertNoReparsePoints(inputPath);
        var id = Guid.NewGuid().ToString("N"); var partial = PathSafety.ResolveRelative(root, id + ".partial");
        var destination = PathSafety.ResolveRelative(root, id); bool ownsPartial = false;
        try
        {
            using var singleFlight = new FileStream(PathSafety.ResolveRelative(root, "import.lock"), FileMode.OpenOrCreate, FileAccess.ReadWrite, FileShare.None);
            using var input = new FileStream(inputPath, FileMode.Open, FileAccess.Read, FileShare.Read);
            if (input.Length > ChatCorpusImporter.MaxBytes) throw new CorpusException("BLOCKED_RESOURCE");
            Directory.CreateDirectory(partial); ownsPartial = true;
            var dbPath = PathSafety.ResolveRelative(partial, "corpus.db"); CorpusMetadata metadata;
            using (var db = Connect(dbPath, true, token))
            {
                using var schema = db.CreateCommand();
                schema.CommandText = """
                    PRAGMA journal_mode=DELETE; PRAGMA synchronous=FULL; PRAGMA max_page_count=262144; PRAGMA user_version=1;
                    CREATE TABLE messages(id TEXT PRIMARY KEY, entry INTEGER NOT NULL, line INTEGER NOT NULL, timestamp TEXT NOT NULL,
                    channel TEXT NOT NULL CHECK(channel IN ('ALL','SHOUT','TRADE','PARTY','CLAN','ALLIANCE','HERO_VOICE','HERO','BATTLEFIELD','COMMANDCHANNEL_ALL','COMMANDCHANNEL_COMMANDER')),
                    original TEXT NOT NULL, normalized TEXT NOT NULL, fingerprint TEXT NOT NULL, language TEXT NOT NULL, reason TEXT NOT NULL,
                    pii INTEGER NOT NULL, noise INTEGER NOT NULL, duplicate INTEGER NOT NULL DEFAULT 0);
                    CREATE INDEX ix_fingerprint ON messages(fingerprint);
                    CREATE INDEX ix_scope ON messages(channel, language, timestamp);
                    CREATE INDEX ix_time ON messages(timestamp, entry, line, id);
                    """;
                schema.ExecuteNonQuery(); using var transaction = db.BeginTransaction();
                using var insert = db.CreateCommand(); insert.Transaction = transaction;
                insert.CommandText = "INSERT INTO messages VALUES($id,$entry,$line,$time,$channel,$original,$normalized,$hash,$language,$reason,$pii,$noise,0)";
                foreach (var key in new[] { "$id", "$entry", "$line", "$time", "$channel", "$original", "$normalized", "$hash", "$language", "$reason", "$pii", "$noise" }) insert.Parameters.Add(new SqliteParameter(key, ""));
                metadata = ChatCorpusImporter.Read(input, id, row =>
                {
                    token.ThrowIfCancellationRequested();
                    insert.Parameters["$id"].Value = row.Id; insert.Parameters["$entry"].Value = row.Entry; insert.Parameters["$line"].Value = row.Line;
                    insert.Parameters["$time"].Value = row.Timestamp; insert.Parameters["$channel"].Value = row.Channel;
                    insert.Parameters["$original"].Value = row.Original; insert.Parameters["$normalized"].Value = row.Normalized;
                    insert.Parameters["$hash"].Value = row.Fingerprint; insert.Parameters["$language"].Value = row.Language;
                    insert.Parameters["$reason"].Value = row.LanguageReason; insert.Parameters["$pii"].Value = row.PossiblePii ? 1 : 0;
                    insert.Parameters["$noise"].Value = row.Noise ? 1 : 0; insert.ExecuteNonQuery();
                }, token, progress);
                using var finish = db.CreateCommand(); finish.Transaction = transaction;
                finish.CommandText = "UPDATE messages SET duplicate=1 WHERE rowid <> (SELECT min(earlier.rowid) FROM messages earlier WHERE earlier.fingerprint=messages.fingerprint)";
                token.ThrowIfCancellationRequested(); finish.ExecuteNonQuery();
                finish.CommandText = "SELECT sum(duplicate),sum(CASE WHEN duplicate=0 AND noise=0 THEN 1 ELSE 0 END) FROM messages";
                using (var result = finish.ExecuteReader())
                { result.Read(); metadata = metadata with { Duplicates = result.IsDBNull(0) ? 0 : result.GetInt64(0), Filtered = result.IsDBNull(1) ? 0 : result.GetInt64(1) }; }
                token.ThrowIfCancellationRequested(); transaction.Commit();
            }
            if (new FileInfo(dbPath).Length > MaxDatabaseBytes) throw new CorpusException("BLOCKED_RESOURCE");
            using (var dbFile = File.OpenRead(dbPath))
            {
                var receipt = new Receipt(metadata, HashStream(dbFile, token));
                using var sink = new FileStream(PathSafety.ResolveRelative(partial, "receipt.json"), FileMode.CreateNew, FileAccess.Write, FileShare.None);
                sink.Write(JsonSerializer.SerializeToUtf8Bytes(receipt, WorkspaceStore.JsonOptions)); sink.Flush(true);
            }
            token.ThrowIfCancellationRequested(); workspace.Check(); PathSafety.AssertNoReparsePoints(partial);
            Directory.Move(partial, destination); ownsPartial = false; return metadata;
        }
        catch (Exception e) when (e is IOException or InvalidDataException or UnauthorizedAccessException or SqliteException or JsonException
            or System.Text.DecoderFallbackException or NotSupportedException or System.Text.RegularExpressions.RegexMatchTimeoutException)
        { token.ThrowIfCancellationRequested(); throw new CorpusException("IMPORT_FAILED"); }
        finally
        {
            // Only this call's exact GUID partial; never remove other imports or the source ZIP.
            if (ownsPartial && Directory.Exists(partial)) { PathSafety.AssertNoReparsePoints(partial); Directory.Delete(partial, true); }
        }
    }
    private Receipt ReadReceipt(string id, CancellationToken token)
    {
        var directory = PathSafety.ResolveRelative(Root, Id(id)); var receiptPath = PathSafety.ResolveRelative(directory, "receipt.json");
        if (!File.Exists(receiptPath) || new FileInfo(receiptPath).Length > 256 * 1024) throw new CorpusException("CORRUPT_INDEX");
        var receipt = JsonSerializer.Deserialize<Receipt>(File.ReadAllBytes(receiptPath), WorkspaceStore.JsonOptions);
        if (receipt?.Metadata is not { Version: 1 } m || m.Id != id || m.Entries == null || m.Entries.Count > 500
            || m.Lines < 0 || m.Lines > ChatCorpusImporter.MaxLines || m.Public + m.PrivateSkipped + m.MalformedSkipped != m.Lines
            || m.Filtered < 0 || m.Filtered > m.Public || m.Duplicates < 0 || m.Noise < 0) throw new CorpusException("CORRUPT_INDEX");
        using var input = File.OpenRead(PathSafety.ResolveRelative(directory, "corpus.db"));
        if (input.Length > MaxDatabaseBytes || HashStream(input, token) != receipt.DatabaseHash) throw new CorpusException("CORRUPT_INDEX");
        return receipt;
    }
    public IReadOnlyList<CorpusMetadata> List(CancellationToken token = default)
    {
        try
        {
            if (!Directory.Exists(Root)) return [];
            var results = new List<CorpusMetadata>();
            foreach (var directory in Directory.EnumerateDirectories(Root).Order(StringComparer.Ordinal))
            {
                token.ThrowIfCancellationRequested(); var name = Path.GetFileName(directory);
                if (name.EndsWith(".partial", StringComparison.Ordinal)) continue;
                results.Add(ReadReceipt(name, token).Metadata);
            }
            return results.AsReadOnly();
        }
        catch (Exception e) when (e is IOException or JsonException or SqliteException or UnauthorizedAccessException) { throw new CorpusException("CORRUPT_INDEX"); }
    }
    public CorpusPage Query(string id, CorpusQuery query, CancellationToken token = default)
    {
        if (query.PageSize is < 100 or > 200 || query.Page < 0 || query.Page > 30000 || query.Search.Length > 4000
            || query.MinLength < 0 || query.MaxLength > 4000 || query.MinLength > query.MaxLength
            || query.Duplicates is not ("ALL" or "ONLY" or "EXCLUDE") || query.Noise is not ("ALL" or "ONLY" or "EXCLUDE")) throw new CorpusException("INVALID_QUERY");
        static bool Date(string value) => value.Length == 0 || DateTime.TryParseExact(value, "yyyy-MM-dd", CultureInfo.InvariantCulture, DateTimeStyles.None, out _);
        if (!Date(query.FromDate) || !Date(query.ToDate)) throw new CorpusException("INVALID_QUERY");
        try
        {
            ReadReceipt(id, token);
            using var db = Connect(PathSafety.ResolveRelative(Root, Id(id) + "/corpus.db"), false, token);
            using var version = db.CreateCommand(); version.CommandText = "PRAGMA user_version";
            if (Convert.ToInt32(version.ExecuteScalar()) != 1) throw new CorpusException("CORRUPT_INDEX");
            using var command = db.CreateCommand();
            var where = """
                WHERE ($search='' OR instr(lower(original),lower($search))>0 OR ($normalized<>'' AND instr(normalized,$normalized)>0))
                AND ($channel='' OR channel=$channel) AND ($language='' OR language=$language)
                AND ($from='' OR timestamp >= $from) AND ($to='' OR timestamp <= $to)
                AND length(original) BETWEEN $min AND $max
                """;
            // Constant SQL fragments only; every user value is a bound parameter.
            if (query.Duplicates != "ALL") where += " AND duplicate=" + (query.Duplicates == "ONLY" ? "1" : "0");
            if (query.Noise != "ALL") where += " AND noise=" + (query.Noise == "ONLY" ? "1" : "0");
            command.Parameters.AddWithValue("$search", query.Search); command.Parameters.AddWithValue("$normalized", TextRules.Normalize(query.Search));
            command.Parameters.AddWithValue("$channel", query.Channel); command.Parameters.AddWithValue("$language", query.Language);
            command.Parameters.AddWithValue("$from", query.FromDate); command.Parameters.AddWithValue("$to", query.ToDate.Length == 0 ? "" : query.ToDate + " 23:59:59");
            command.Parameters.AddWithValue("$min", query.MinLength); command.Parameters.AddWithValue("$max", query.MaxLength);
            token.ThrowIfCancellationRequested();
            command.CommandText = "SELECT count(*) FROM messages " + where;
            var count = Convert.ToInt64(command.ExecuteScalar()); token.ThrowIfCancellationRequested();
            command.CommandText = "SELECT id,entry,line,timestamp,channel,original,normalized,fingerprint,language,reason,pii,noise,duplicate FROM messages "
                + where + " ORDER BY timestamp,entry,line,id LIMIT $limit OFFSET $offset";
            command.Parameters.AddWithValue("$limit", query.PageSize); command.Parameters.AddWithValue("$offset", checked(query.Page * query.PageSize));
            using var reader = command.ExecuteReader(); var rows = new List<CorpusRecord>(query.PageSize);
            while (reader.Read())
            {
                token.ThrowIfCancellationRequested();
                rows.Add(new(reader.GetString(0), reader.GetInt32(1), reader.GetInt64(2), reader.GetString(3), reader.GetString(4), reader.GetString(5),
                    reader.GetString(6), reader.GetString(7), reader.GetString(8), reader.GetString(9), reader.GetBoolean(10), reader.GetBoolean(11), reader.GetBoolean(12)));
            }
            return new(count, rows.AsReadOnly());
        }
        catch (Exception e) when (e is SqliteException or IOException or JsonException or UnauthorizedAccessException)
        { token.ThrowIfCancellationRequested(); throw new CorpusException("CORRUPT_INDEX"); }
    }
}
