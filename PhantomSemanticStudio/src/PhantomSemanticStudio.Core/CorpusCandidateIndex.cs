using System.Globalization;
using System.Text.RegularExpressions;
using Microsoft.Data.Sqlite;

namespace PhantomSemanticStudio.Core;

public sealed record CorpusIndexRecord(string SourceId, string SourceHash, int Number, string Category, string Mode, string Text, bool QaOnly = false);
public sealed record CorpusIndexItem(string SourceId, int Number, long TextId, string Disposition);
public sealed record CorpusIndexAppend(int NewTexts, int NewOccurrences, int ExactDuplicates, int Replayed, IReadOnlyList<CorpusIndexItem> Items);
public sealed record CorpusIndexStats(long Texts, long Occurrences, long Features, string RegistryHash);
public sealed record CorpusIndexNeighbor(long TextId, CorpusIndexRecord Source, bool Exact, bool LexicalNearClone, bool RepeatedOpening, int SharedFeatures)
{
    public string Coverage => "COVERAGE_LIMITED_NOT_SEMANTIC_APPROVAL";
}

/// <summary>Owned advisory retrieval index. Occurrences are provenance, never approval or an import.</summary>
public sealed class CorpusCandidateIndex
{
    private const string Algorithm = "nfkc-lower-c32-t24-w6-p2-sha256-i64hex-v1";
    private readonly WorkspaceStore workspace;
    private readonly V4Registry registry;
    private readonly HashSet<string> categories;
    private readonly string path;

    public CorpusCandidateIndex(WorkspaceStore workspace, V4Registry? registry = null)
    {
        this.workspace = workspace; this.registry = registry ?? V4Registry.Default;
        categories = this.registry.Categories.Select(c => c.Key).ToHashSet(StringComparer.Ordinal);
        workspace.Check(); var root = PathSafety.ResolveRelative(workspace.Root, "labs/prompt-lab/corpus-index");
        Directory.CreateDirectory(root); PathSafety.AssertNoReparsePoints(root);
        path = PathSafety.ResolveRelative(root, this.registry.Hash + ".sqlite");
        Guard();
        if (!File.Exists(path))
        {
            var temporary = PathSafety.ResolveRelative(root, "new-" + Guid.NewGuid().ToString("N") + ".tmp");
            try
            {
                using (var db = CorpusStore.Connect(temporary, true))
                {
                    using (var settings = Command(db, null, "PRAGMA page_size=4096; PRAGMA journal_mode=DELETE; PRAGMA synchronous=FULL; PRAGMA max_page_count=262144;")) settings.ExecuteNonQuery();
                    using var transaction = db.BeginTransaction();
                    using var schema = Command(db, transaction, """
                        PRAGMA user_version=1;
                        CREATE TABLE identity(singleton INTEGER PRIMARY KEY CHECK(singleton=1), registry TEXT NOT NULL, algorithm TEXT NOT NULL);
                        CREATE TABLE sources(id TEXT PRIMARY KEY, fingerprint TEXT NOT NULL);
                        CREATE TABLE texts(id INTEGER PRIMARY KEY CHECK(id BETWEEN 1 AND 1000000), normalized TEXT NOT NULL UNIQUE);
                        CREATE TABLE occurrences(source_id TEXT NOT NULL REFERENCES sources(id), number INTEGER NOT NULL,
                            text_id INTEGER NOT NULL REFERENCES texts(id), category TEXT NOT NULL, mode TEXT NOT NULL, original TEXT NOT NULL, qa_only INTEGER NOT NULL,
                            PRIMARY KEY(source_id,number));
                        CREATE INDEX ix_occurrence_text ON occurrences(text_id);
                        CREATE TABLE features(feature INTEGER NOT NULL, text_id INTEGER NOT NULL REFERENCES texts(id), PRIMARY KEY(feature,text_id));
                        """);
                    schema.ExecuteNonQuery();
                    using var identity = Command(db, transaction, "INSERT INTO identity VALUES(1,$registry,$algorithm)", ("$registry", this.registry.Hash), ("$algorithm", Algorithm));
                    identity.ExecuteNonQuery(); transaction.Commit();
                }
                Guard(); File.Move(temporary, path, false);
            }
            finally { if (File.Exists(temporary)) { PathSafety.AssertNoReparsePoints(temporary); File.Delete(temporary); } }
        }
        using var verified = Open(false, CancellationToken.None);
    }

    // A claim follows a durable editorial decision. It is not made by Append/Find and never approves research by itself.
    internal IReadOnlyList<int> ReserveApprovals(string sourceId, string sourceHash, string decisionHash, string approvalHash,
        IReadOnlyList<int> numbers, CancellationToken token)
    {
        V4Rules.Require(Hex(sourceId, 32, 64) && numbers is { Count: <= 20 } && numbers.All(n => n is >= 1 and <= 20)
            && numbers.Distinct().Count() == numbers.Count && new[] { sourceHash, decisionHash, approvalHash }.All(h => h is { Length: 64 } && h.All(char.IsAsciiHexDigit)), "CORPUS_APPROVAL_CLAIM");
        if (numbers.Count == 0) return [];
        using var db = Open(true, token); using var transaction = db.BeginTransaction();
        using (var schema = Command(db, transaction, """
            CREATE TABLE IF NOT EXISTS approved_texts(text_id INTEGER PRIMARY KEY REFERENCES texts(id),source_id TEXT NOT NULL,number INTEGER NOT NULL,
                decision_hash TEXT NOT NULL,approval_hash TEXT NOT NULL);
            """)) schema.ExecuteNonQuery();
        var reserved = new List<int>();
        foreach (var number in numbers)
        {
            token.ThrowIfCancellationRequested(); long textId;
            using (var lookup = Command(db, transaction, "SELECT o.text_id FROM occurrences o JOIN sources s ON s.id=o.source_id WHERE o.source_id=$source AND o.number=$number AND s.fingerprint=$hash AND o.qa_only=0",
                ("$source", sourceId), ("$number", number), ("$hash", sourceHash)))
                textId = lookup.ExecuteScalar() is long found ? found : throw new InvalidDataException("CORPUS_APPROVAL_SOURCE_DRIFT");
            using (var insert = Command(db, transaction, "INSERT OR IGNORE INTO approved_texts VALUES($text,$source,$number,$decision,$approval)",
                ("$text", textId), ("$source", sourceId), ("$number", number), ("$decision", decisionHash), ("$approval", approvalHash))) insert.ExecuteNonQuery();
            using var owner = Command(db, transaction, "SELECT source_id,number,decision_hash,approval_hash FROM approved_texts WHERE text_id=$text", ("$text", textId));
            using var reader = owner.ExecuteReader();
            V4Rules.Require(reader.Read(), "CORPUS_APPROVAL_RESERVATION_MISSING");
            if (reader.GetString(0) == sourceId && reader.GetInt32(1) == number && reader.GetString(2) == decisionHash && reader.GetString(3) == approvalHash) reserved.Add(number);
        }
        transaction.Commit(); return reserved;
    }
    internal bool HasApproval(string sourceId, int number, string decisionHash, string approvalHash)
    {
        using var db = Open(false, CancellationToken.None);
        using (var feature = Command(db, null, "SELECT count(*) FROM sqlite_master WHERE type='table' AND name='approved_texts'"))
            if ((long)feature.ExecuteScalar()! == 0) return false;
        using var lookup = Command(db, null, "SELECT count(*) FROM approved_texts WHERE source_id=$source AND number=$number AND decision_hash=$decision AND approval_hash=$approval",
            ("$source", sourceId), ("$number", number), ("$decision", decisionHash), ("$approval", approvalHash));
        return (long)lookup.ExecuteScalar()! == 1;
    }

    private void Guard()
    {
        workspace.Check(); PathSafety.AssertNoReparsePoints(path);
        foreach (var suffix in new[] { "-journal", "-wal", "-shm" }) PathSafety.AssertNoReparsePoints(path + suffix);
        V4Rules.Require(!File.Exists(path) || new FileInfo(path).Length <= 1024L * 1024 * 1024, "CORPUS_INDEX_RESOURCE_LIMIT");
    }

    private SqliteConnection Open(bool write, CancellationToken token)
    {
        token.ThrowIfCancellationRequested(); Guard(); var db = CorpusStore.Connect(path, write, token);
        try
        {
            using (var revision = Command(db, null, "PRAGMA user_version"))
                V4Rules.Require((long)revision.ExecuteScalar()! == 1, "CORPUS_INDEX_SCHEMA_DRIFT");
            using (var identity = Command(db, null, "SELECT registry,algorithm FROM identity WHERE singleton=1"))
            using (var reader = identity.ExecuteReader())
                V4Rules.Require(reader.Read() && reader.GetString(0) == registry.Hash && reader.GetString(1) == Algorithm, "CORPUS_INDEX_REVISION_DRIFT");
            using var settings = Command(db, null, write ? "PRAGMA foreign_keys=ON; PRAGMA synchronous=FULL; PRAGMA max_page_count=262144;" : "PRAGMA foreign_keys=ON;");
            settings.ExecuteNonQuery(); return db;
        }
        catch { db.Dispose(); throw; }
    }

    private static SqliteCommand Command(SqliteConnection db, SqliteTransaction? transaction, string sql, params (string Name, object Value)[] values)
    {
        var command = db.CreateCommand(); command.Transaction = transaction; command.CommandText = sql;
        foreach (var value in values) command.Parameters.AddWithValue(value.Name, value.Value);
        return command;
    }

    private static string CheckedText(string text)
    {
        V4Rules.Require(text != null && V4Rules.Utf8.GetByteCount(text) <= 8000, "CORPUS_INDEX_TEXT");
        var clean = V4Rules.Clean(text!); V4Rules.SafeText(clean); return V4Rules.Normalize(clean);
    }

    private static bool Hex(string? text, params int[] sizes) => text != null && sizes.Contains(text.Length) && text.All(c => c is >= '0' and <= '9' or >= 'a' and <= 'f');
    private void Validate(CorpusIndexRecord record)
    {
        if (record == null || !Hex(record.SourceId, 32, 64) || !Hex(record.SourceHash, 64) || record.Number is < 1 or > 20
            || !categories.Contains(record.Category) || record.Mode is not ("TRIGGER" or "REPLY" or "INITIATIVE"))
            throw new InvalidDataException("CORPUS_INDEX_PROVENANCE");
        _ = CheckedText(record.Text);
        V4Rules.Require(record.Mode == "TRIGGER" || V4Rules.OutboundLength(V4Rules.Clean(record.Text)), "CORPUS_INDEX_OUTBOUND_LENGTH");
    }

    private static string[] Words(string text) => Regex.Matches(V4Rules.Normalize(text), @"[\p{L}\p{N}]+").Select(m => m.Value).ToArray();
    private static string Opening(string text) { var words = Words(text); return words.Length >= 3 ? string.Join(' ', words.Take(3)) : ""; }
    private static long Fingerprint(string text) => long.Parse(V4Rules.Hash(V4Rules.Utf8.GetBytes(text))[..16], NumberStyles.HexNumber, CultureInfo.InvariantCulture);
    private static long[] Features(string text)
    {
        var words = Words(text); var clean = string.Concat(words);
        IEnumerable<long> Take(IEnumerable<string> values, string kind, int count) => values.Distinct(StringComparer.Ordinal)
            .Select(value => Fingerprint(kind + value)).Distinct().Order().Take(count);
        return Take(Enumerable.Range(0, Math.Max(0, clean.Length - 2)).Select(i => clean.Substring(i, 3)), "c:", 32)
            .Concat(Take(words.Where(w => w.Length > 2), "t:", 24))
            .Concat(Take(Enumerable.Range(0, Math.Max(0, words.Length - 1)).Select(i => words[i] + " " + words[i + 1]), "w:", 6))
            .Concat(words.Length == 0 ? [] : new[] { Fingerprint("p:" + string.Join(' ', words.Take(3))), Fingerprint("p:" + string.Join(' ', words.Take(5))) })
            .Distinct().ToArray();
    }

    public CorpusIndexAppend Append(IReadOnlyList<CorpusIndexRecord> records, CancellationToken token = default)
    {
        token.ThrowIfCancellationRequested();
        V4Rules.Require(records is { Count: >= 1 and <= 1000 }, "CORPUS_INDEX_BATCH_LIMIT");
        foreach (var record in records) Validate(record);
        using var db = Open(true, token); using var transaction = db.BeginTransaction();
        var items = new List<CorpusIndexItem>(); var newTexts = 0; var newOccurrences = 0; var duplicates = 0; var replayed = 0;
        foreach (var record in records)
        {
            token.ThrowIfCancellationRequested();
            using (var source = Command(db, transaction, "SELECT fingerprint FROM sources WHERE id=$id", ("$id", record.SourceId)))
            {
                var fingerprint = source.ExecuteScalar() as string;
                V4Rules.Require(fingerprint == null || fingerprint == record.SourceHash, "CORPUS_INDEX_SOURCE_DRIFT");
                if (fingerprint == null)
                {
                    using var insert = Command(db, transaction, "INSERT INTO sources VALUES($id,$hash)", ("$id", record.SourceId), ("$hash", record.SourceHash)); insert.ExecuteNonQuery();
                }
            }
            using (var existing = Command(db, transaction, "SELECT text_id,category,mode,original,qa_only FROM occurrences WHERE source_id=$id AND number=$number", ("$id", record.SourceId), ("$number", record.Number)))
            using (var reader = existing.ExecuteReader())
                if (reader.Read())
                {
                    V4Rules.Require(reader.GetString(1) == record.Category && reader.GetString(2) == record.Mode && reader.GetString(3) == record.Text
                        && reader.GetInt32(4) == (record.QaOnly ? 1 : 0), "CORPUS_INDEX_ROW_DRIFT");
                    items.Add(new(record.SourceId, record.Number, reader.GetInt64(0), "REPLAY")); replayed++; continue;
                }
            var normalized = V4Rules.Normalize(record.Text); long textId; bool found;
            using (var lookup = Command(db, transaction, "SELECT id FROM texts WHERE normalized=$text", ("$text", normalized)))
            { var value = lookup.ExecuteScalar(); found = value != null; textId = value == null ? 0 : (long)value; }
            if (!found)
            {
                using (var insert = Command(db, transaction, "INSERT INTO texts(normalized) VALUES($text)", ("$text", normalized))) insert.ExecuteNonQuery();
                using (var id = Command(db, transaction, "SELECT id FROM texts WHERE normalized=$text", ("$text", normalized))) textId = (long)id.ExecuteScalar()!;
                using var feature = Command(db, transaction, "INSERT INTO features VALUES($feature,$id)", ("$feature", 0L), ("$id", textId));
                foreach (var value in Features(normalized)) { feature.Parameters["$feature"].Value = value; feature.ExecuteNonQuery(); }
                newTexts++;
            }
            else duplicates++;
            using (var insert = Command(db, transaction, "INSERT INTO occurrences VALUES($source,$number,$id,$category,$mode,$text,$qa)",
                ("$source", record.SourceId), ("$number", record.Number), ("$id", textId), ("$category", record.Category), ("$mode", record.Mode), ("$text", record.Text), ("$qa", record.QaOnly ? 1 : 0))) insert.ExecuteNonQuery();
            newOccurrences++; items.Add(new(record.SourceId, record.Number, textId, found ? "EXACT_DUPLICATE" : "NEW_TEXT"));
        }
        token.ThrowIfCancellationRequested(); Guard(); transaction.Commit();
        return new(newTexts, newOccurrences, duplicates, replayed, items.AsReadOnly());
    }

    public CorpusIndexStats Stats(CancellationToken token = default)
    {
        using var db = Open(false, token); using var command = Command(db, null, "SELECT (SELECT count(*) FROM texts),(SELECT count(*) FROM occurrences),(SELECT count(*) FROM features)");
        using var reader = command.ExecuteReader(); reader.Read(); return new(reader.GetInt64(0), reader.GetInt64(1), reader.GetInt64(2), registry.Hash);
    }

    public IReadOnlyList<CorpusIndexNeighbor> Find(string text, int limit = 12, CancellationToken token = default)
    {
        var normalized = CheckedText(text); V4Rules.Require(limit is >= 1 and <= 64, "CORPUS_INDEX_SHORTLIST_LIMIT");
        var features = Features(normalized); using var db = Open(false, token); var candidates = new Dictionary<long, int>();
        if (features.Length > 0)
        {
            var values = features.Select((value, i) => ("$f" + i, (object)value)).ToArray();
            using var query = Command(db, null, "SELECT text_id,count(*) FROM features WHERE feature IN (" + string.Join(',', values.Select(v => v.Item1))
                + ") GROUP BY text_id ORDER BY count(*) DESC,text_id LIMIT 128", values);
            using var reader = query.ExecuteReader(); while (reader.Read()) candidates.Add(reader.GetInt64(0), reader.GetInt32(1));
        }
        using (var exact = Command(db, null, "SELECT id FROM texts WHERE normalized=$text", ("$text", normalized)))
        { if (exact.ExecuteScalar() is long id) candidates[id] = features.Length; }
        var matches = new List<CorpusIndexNeighbor>(); var opening = Opening(normalized);
        using var source = Command(db, null, """
            SELECT t.normalized,o.source_id,s.fingerprint,o.number,o.category,o.mode,o.original,o.qa_only FROM texts t
            JOIN occurrences o ON o.rowid=(SELECT min(rowid) FROM occurrences WHERE text_id=t.id)
            JOIN sources s ON s.id=o.source_id WHERE t.id=$id
            """, ("$id", 0L));
        foreach (var candidate in candidates)
        {
            token.ThrowIfCancellationRequested(); source.Parameters["$id"].Value = candidate.Key;
            using var reader = source.ExecuteReader(); V4Rules.Require(reader.Read(), "CORPUS_INDEX_DANGLING_TEXT");
            var record = new CorpusIndexRecord(reader.GetString(1), reader.GetString(2), reader.GetInt32(3), reader.GetString(4), reader.GetString(5), reader.GetString(6), reader.GetInt32(7) != 0);
            var exact = reader.GetString(0) == normalized;
            matches.Add(new(candidate.Key, record, exact, !exact && CorpusQuality.NearClone(text, record.Text), opening.Length > 0 && opening == Opening(record.Text), candidate.Value));
        }
        return matches.OrderByDescending(m => m.Exact).ThenByDescending(m => m.LexicalNearClone).ThenByDescending(m => m.RepeatedOpening)
            .ThenByDescending(m => m.SharedFeatures).ThenBy(m => m.TextId).Take(limit).ToArray();
    }
}
