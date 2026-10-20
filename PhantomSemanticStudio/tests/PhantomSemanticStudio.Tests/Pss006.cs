using System.Text;
using System.IO.Compression;
using System.Text.Json;
using PhantomSemanticStudio.Core;

internal static partial class Program
{
    private sealed class CorpusCallback(Action<CorpusProgress> action) : IProgress<CorpusProgress>
    { public void Report(CorpusProgress value) => action(value); }
    private static string LargeCorpusZip(string root)
    {
        var path = Path.Combine(root, "large-synthetic.zip");
        using var zip = ZipFile.Open(path, ZipArchiveMode.Create);
        using var writer = new StreamWriter(zip.CreateEntry("synthetic.log", CompressionLevel.Optimal).Open(), new UTF8Encoding(false));
        var random = new Random(006);
        for (var i = 0; i < 100_000; i++) writer.WriteLine($"[01.01.22 00:00:01] SHOUT [PlayerA] public synthetic {i:D6} {random.NextInt64():x} {random.NextInt64():x}");
        return path;
    }
    private static int Pss006Actual(string[] args)
    {
        // Explicit local operator route: no row, nickname, entry name or raw exception is printed.
        if (args.Length != 2) return 2;
        var studio = Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "../../../../../"));
        if (!Path.GetFullPath(args[1]).Equals(Path.GetFullPath(Path.Combine(studio, "artifacts/private-input/chat.zip")), StringComparison.OrdinalIgnoreCase)) return 2;
        try
        {
            using var ws = new WorkspaceStore(Path.Combine(studio, "artifacts/PSS-006/private-corpus-" + Guid.NewGuid().ToString("N")), new StudioSettings().HighFiveRoot);
            var timer = System.Diagnostics.Stopwatch.StartNew(); var process = System.Diagnostics.Process.GetCurrentProcess(); long peak = process.PrivateMemorySize64;
            var store = new CorpusStore(ws); var m = store.Import(args[1], progress: new CorpusCallback(_ => { process.Refresh(); peak = Math.Max(peak, process.PrivateMemorySize64); }));
            var page = store.Query(m.Id, new CorpusQuery()); Equal(Math.Min(100L, page.Total), (long)page.Rows.Count);
            Console.WriteLine($"ACTUAL_CHAT_IMPORT_PASS entries={m.Entries.Count}; bytes={m.Entries.Sum(e => e.Bytes)}; lines={m.Lines}; public={m.Public}; privateSkipped={m.PrivateSkipped}; malformed={m.MalformedSkipped}; duplicates={m.Duplicates}; noise={m.Noise}; filtered={m.Filtered}");
            Console.WriteLine($"ARCHIVE_SHA256={m.ArchiveHash}; elapsedMs={timer.ElapsedMilliseconds}; sampledPeakPrivateBytes={peak}; modelCalls=0; sourceWrites=0; rawOutput=0");
            Console.WriteLine("CHANNEL_COUNTS=" + JsonSerializer.Serialize(m.Channels));
            Console.WriteLine("LANGUAGE_COUNTS=" + JsonSerializer.Serialize(m.Languages)); return 0;
        }
        catch (Exception e) when (e is not AssertionFailure) { Console.WriteLine("ACTUAL_CHAT_IMPORT_BLOCKED; no raw diagnostics retained"); return 2; }
    }
    private static int Pss006Reopen(string[] args)
    {
        if (args.Length != 3) return 2;
        var studio = Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "../../../../../"));
        if (!PathSafety.IsWithin(args[1], Path.Combine(studio, "artifacts/PSS-006"))) return 2;
        try
        {
            using var workspace = new WorkspaceStore(args[1], new StudioSettings().HighFiveRoot); var store = new CorpusStore(workspace);
            var m = store.List().Single(c => c.Id == args[2]);
            var one = store.Query(m.Id, new CorpusQuery()); var two = store.Query(m.Id, new CorpusQuery { Page = 1 });
            Equal(100, one.Rows.Count); Equal(100, two.Rows.Count); True(!one.Rows.Select(r => r.Id).Intersect(two.Rows.Select(r => r.Id)).Any());
            Equal(0L, store.Query(m.Id, new CorpusQuery { Channel = "TELL", Noise = "ALL", Duplicates = "ALL" }).Total);
            Console.WriteLine($"REOPEN_NEW_PROCESS_PASS public={m.Public}; filtered={one.Total}; pages=100/100; privateQuery=0; rawOutput=0"); return 0;
        }
        catch (Exception e) when (e is not AssertionFailure) { Console.WriteLine("REOPEN_BLOCKED; no raw diagnostics retained"); return 2; }
    }
    private static string CorpusZip(string root, params (string Name, byte[] Bytes)[] entries)
    {
        var path = Path.Combine(root, Guid.NewGuid().ToString("N") + ".zip");
        using var zip = ZipFile.Open(path, ZipArchiveMode.Create);
        foreach (var e in entries) { using var stream = zip.CreateEntry(e.Name, CompressionLevel.Optimal).Open(); stream.Write(e.Bytes); }
        return path;
    }
    private static byte[] CorpusLines(params string[] messages) => Encoding.UTF8.GetBytes(string.Join("\n", messages));
    private static void Pss006B()
    {
        using var f = new Fixture(); using var ws = new WorkspaceStore(Path.Combine(f.Root, "corpus-workspace"), f.Module);
        var store = new CorpusStore(ws);
        Test("PSS-006 B cancellation inside native SQL execution", () =>
        {
            using var cancel = new CancellationTokenSource();
            using var db = CorpusStore.Connect(Path.Combine(f.Root, "sql-cancel.db"), true, cancel.Token);
            var callbacks = 0;
            db.CreateFunction("cancel_test", (long value) => { if (++callbacks == 50) cancel.Cancel(); return value; });
            using var command = db.CreateCommand();
            command.CommandText = "WITH RECURSIVE n(x) AS (VALUES(1) UNION ALL SELECT x+1 FROM n WHERE x<100000) SELECT sum(cancel_test(x)) FROM n";
            try { command.ExecuteScalar(); throw new AssertionFailure("SQL continued to completion after cancellation"); }
            catch (Microsoft.Data.Sqlite.SqliteException e) { Equal(9, e.SqliteErrorCode); }
            True(callbacks >= 50 && callbacks < 1000);
            Console.WriteLine($"SQL_CANCEL interruptedInsideExecution=true; callbacks={callbacks}; ceiling=1000");
        });
        Test("PSS-006 B central directory budget before entry materialization", () =>
        {
            var path = Path.Combine(f.Root, "many-empty-entries.zip");
            using (var zip = ZipFile.Open(path, ZipArchiveMode.Create))
                for (var i = 0; i < 20000; i++) zip.CreateEntry($"synthetic-{i}.log");
            var baseline = GC.GetTotalAllocatedBytes(true); Throws(() => store.Import(path));
            var allocated = GC.GetTotalAllocatedBytes(true) - baseline;
            True(allocated < 4L * 1024 * 1024);
            Console.WriteLine($"ZIP_PREFLIGHT entries=20000; rejectionAllocatedBytes={allocated}; ceilingBytes=4194304");
            var bytes = File.ReadAllBytes(path);
            BitConverter.GetBytes((ushort)1).CopyTo(bytes, bytes.Length - 22 + 8);
            BitConverter.GetBytes((ushort)1).CopyTo(bytes, bytes.Length - 22 + 10);
            File.WriteAllBytes(path, bytes); Throws(() => store.Import(path));
        });
        Test("PSS-006 B privacy skip before persistence UTF8 timestamp triage and reopen", () =>
        {
            var zip = CorpusZip(f.Root, ("chat.01-01-2022.log", CorpusLines(
                "[01.01.22 00:00:01] SHOUT [PlayerA] privet kak dela",
                "[01.01.22 00:00:02] PARTY [PlayerB] пошли на фарм",
                "[01.01.22 00:00:03] friendtell [PlayerA -> PlayerB] SECRET_FIXTURE_NOT_TO_PERSIST",
                "[01.01.22 00:00:04] tell [PlayerB -> PlayerA] SECRET_FIXTURE_NOT_TO_PERSIST",
                "[01.01.22 00:00:05] SHOUT [PlayerC] need party")));
            var bytes = File.ReadAllBytes(zip); var m = store.Import(zip);
            Equal(5L, m.Lines); Equal(3L, m.Public); Equal(2L, m.PrivateSkipped);
            var reopened = new CorpusStore(ws); Equal(m.Id, reopened.List().Single().Id);
            var page = reopened.Query(m.Id, new CorpusQuery()); Equal(3L, page.Total); Equal(3, page.Rows.Count);
            Equal("2022-01-01 00:00:01", page.Rows[0].Timestamp); True(page.Rows.All(r => r.Status == "SOURCE_MATERIAL_ONLY"));
            Equal("LATIN_TRANSLIT_CANDIDATE", page.Rows[0].Language); Equal("CYRILLIC", page.Rows[1].Language); Equal("EN_OR_OTHER", page.Rows[2].Language);
            foreach (var file in Directory.EnumerateFiles(Path.Combine(ws.Root, "corpora"), "*", SearchOption.AllDirectories))
            {
                var physical = Encoding.UTF8.GetString(File.ReadAllBytes(file));
                True(!physical.Contains("SECRET_FIXTURE_NOT_TO_PERSIST") && !physical.Contains("PlayerA") && !physical.Contains("PlayerB"));
            }
            True(bytes.SequenceEqual(File.ReadAllBytes(zip)));
        });
        Test("PSS-006 B language ambiguity and sensitive preview preserve original", () =>
        {
            Equal("UNKNOWN", CorpusLanguageTriage.Classify("ne znayu").Language);
            Equal("MIXED", CorpusLanguageTriage.Classify("Привет party").Language);
            Equal("EN_OR_OTHER", CorpusLanguageTriage.Classify("den exoume").Language);
            var original = "user@example.test https://example.test 192.168.1.1 +373 123 456 789";
            var scrubbed = CorpusLanguageTriage.Scrub(original);
            True(!scrubbed.Contains("example") && !scrubbed.Contains("192.168") && !scrubbed.Contains("456"));
        });
        Test("PSS-006 B malformed counters normalized dedup independent ZIP order paged filters", () =>
        {
            var a = CorpusLines("[01.01.22 00:00:01] SHOUT [PlayerA] Привет!", "[01.01.22 00:00:02] SHOUT [PlayerB] ПРИВЕТ!!!",
                "[01.01.22 00:00:03] PARTY [PlayerC] Привет!", "[99.01.22 00:00:04] SHOUT [PlayerA] bad", "[01.01.22 00:00:05] UNKNOWN [PlayerA] bad", "malformed");
            var b = CorpusLines("[02.01.22 00:00:01] SHOUT [PlayerA] !!!");
            var one = store.Import(CorpusZip(f.Root, ("a.log", a), ("b.log", b)));
            var two = store.Import(CorpusZip(f.Root, ("b.log", b), ("a.log", a)));
            Equal(one.DatasetHash, two.DatasetHash); True(one.Id != two.Id); Equal(3L, one.MalformedSkipped); Equal(1L, one.Duplicates); Equal(1L, one.Noise);
            Equal(2L, store.Query(one.Id, new CorpusQuery()).Total);
            Equal(4L, store.Query(one.Id, new CorpusQuery { Duplicates = "ALL", Noise = "ALL" }).Total);
            Equal(0L, store.Query(one.Id, new CorpusQuery { Channel = "UNKNOWN" }).Total);
            Equal(0L, store.Query(one.Id, new CorpusQuery { Search = "' OR 1=1 --" }).Total);
            Equal(1L, store.Query(one.Id, new CorpusQuery { Channel = "PARTY", Language = "CYRILLIC", MinLength = 3, MaxLength = 20 }).Total);
            Equal(0L, store.Query(one.Id, new CorpusQuery { FromDate = "2022-02-01" }).Total);
            Throws(() => store.Query(one.Id, new CorpusQuery { PageSize = 201 }));
        });
        Test("PSS-006 B archive negative controls and cancelled import atomic old corpus", () =>
        {
            var before = store.List().Count;
            var valid = CorpusLines("[01.01.22 00:00:01] SHOUT [PlayerA] Привет!");
            foreach (var name in new[] { "../bad.log", "/bad.log", "C:/bad.log", "folder/bad.log", "nested.zip", "run.exe" })
                Throws(() => store.Import(CorpusZip(f.Root, (name, valid))));
            foreach (var bytes in new[] { new byte[] { 0xff, 0x00 }, Encoding.UTF8.GetBytes(new string('x', 16385)), Encoding.UTF8.GetBytes(new string('x', 200000)) })
                Throws(() => store.Import(CorpusZip(f.Root, ("bad.log", bytes))));
            var duplicate = CorpusZip(f.Root, ("same.log", valid), ("same.log", valid)); Throws(() => store.Import(duplicate));
            var malformed = Path.Combine(f.Root, "broken.zip"); File.WriteAllText(malformed, "broken"); Throws(() => store.Import(malformed));
            using var cancel = new CancellationTokenSource(); cancel.Cancel();
            Throws(() => store.Import(CorpusZip(f.Root, ("chat.log", valid)), cancel.Token));
            Equal(before, store.List().Count); True(!Directory.EnumerateDirectories(Path.Combine(ws.Root, "corpora"), "*.partial").Any());
        });
        Test("PSS-006 B encrypted symlink metadata limits physical corruption", () =>
        {
            var data = CorpusLines("[01.01.22 00:00:01] SHOUT [PlayerA] Привет!");
            var path = CorpusZip(f.Root, ("safe.log", data));
            using (var zip = ZipFile.Open(path, ZipArchiveMode.Update)) zip.Entries[0].ExternalAttributes = unchecked((int)0xA0000000);
            Throws(() => store.Import(path));
            path = CorpusZip(f.Root, ("safe.log", data)); var bytes = File.ReadAllBytes(path);
            for (var i = 0; i < bytes.Length - 46; i++) if (BitConverter.ToUInt32(bytes, i) == 0x02014b50) { bytes[i + 8] |= 1; break; }
            File.WriteAllBytes(path, bytes); Throws(() => store.Import(path));
            path = CorpusZip(f.Root, ("safe.log", data)); bytes = File.ReadAllBytes(path);
            for (var i = 0; i < bytes.Length - 46; i++) if (BitConverter.ToUInt32(bytes, i) == 0x02014b50) { BitConverter.GetBytes(64 * 1024 * 1024 + 1).CopyTo(bytes, i + 24); break; }
            File.WriteAllBytes(path, bytes); Throws(() => store.Import(path));
            Throws(() => store.Import(CorpusZip(f.Root, Enumerable.Range(0, 501).Select(i => (i + ".log", data)).ToArray())));
            path = Path.Combine(f.Root, "line-limit.zip");
            using (var zip = ZipFile.Open(path, ZipArchiveMode.Create))
            { using var stream = zip.CreateEntry("lines.log", CompressionLevel.NoCompression).Open(); stream.Write(Enumerable.Repeat((byte)10, 3_000_001).ToArray()); }
            Throws(() => store.Import(path));
            var m = store.Import(CorpusZip(f.Root, ("safe.log", data))); var db = Path.Combine(ws.Root, "corpora", m.Id, "corpus.db"); var original = File.ReadAllBytes(db);
            try { File.WriteAllText(db, "bad index"); Throws(() => store.Query(m.Id, new CorpusQuery())); }
            finally { File.WriteAllBytes(db, original); }
            Equal(1L, store.Query(m.Id, new CorpusQuery()).Total);
        });
        Test("PSS-006 B CRC damaged archive cannot publish", () =>
        {
            var data = CorpusLines("[01.01.22 00:00:01] SHOUT [PlayerA] Привет!");
            var path = CorpusZip(f.Root, ("safe.log", data)); var bytes = File.ReadAllBytes(path);
            for (var i = 0; i < bytes.Length - 46; i++) if (BitConverter.ToUInt32(bytes, i) == 0x02014b50) { bytes[i + 16] ^= 255; break; }
            File.WriteAllBytes(path, bytes); var before = store.List().Count;
            Throws(() => store.Import(path)); Equal(before, store.List().Count);
        });
        Test("PSS-006 B 100k streaming cancel retry bounded pages private memory", () =>
        {
            var path = LargeCorpusZip(f.Root); var before = store.List().Count;
            using var cancel = new CancellationTokenSource();
            Throws(() => store.Import(path, cancel.Token, new CorpusCallback(p => { if (p.Lines >= 2000) cancel.Cancel(); })));
            Equal(before, store.List().Count);
            var timer = System.Diagnostics.Stopwatch.StartNew(); var process = System.Diagnostics.Process.GetCurrentProcess(); process.Refresh();
            var baseline = process.PrivateMemorySize64; var peak = baseline;
            var m = store.Import(path, progress: new CorpusCallback(_ => { process.Refresh(); peak = Math.Max(peak, process.PrivateMemorySize64); }));
            Equal(100_000L, m.Public); Equal(0L, m.PrivateSkipped); Equal(0L, m.Duplicates);
            var first = store.Query(m.Id, new CorpusQuery()); var second = store.Query(m.Id, new CorpusQuery { Page = 1 });
            Equal(100, first.Rows.Count); Equal(100, second.Rows.Count); True(!first.Rows.Select(r => r.Id).Intersect(second.Rows.Select(r => r.Id)).Any());
            True(peak - baseline < 256L * 1024 * 1024);
            Console.WriteLine($"SYNTHETIC_100K elapsedMs={timer.ElapsedMilliseconds}; sampledPeakPrivateBytes={peak}; privateDeltaBytes={peak - baseline}; archiveBytes={new FileInfo(path).Length}; pages=100/100");
        });
    }
    private static async Task Pss006A()
    {
        await TestAsync("PSS-006 A safe failure-stage classification strict single POST", async () =>
        {
            var c = ScoutCandidate(); var s = SemanticDuplicateScout.Search(ScoutPack([ScoutEntry("one", "Привет!")]), c, []);
            const string secret = "SECRET_RESPONSE_TOKEN";
            foreach (var (body, category) in new[] {
                ("{" + secret, "ENVELOPE_JSON"), ("{\"choices\":[]}", "ENVELOPE_SHAPE"),
                (SemanticEnvelope("{}" ).Replace("\"stop\"", "\"length\""), "FINISH_REASON"),
                (SemanticEnvelope("{}").Replace("\"message\":{", "\"message\":{\"refusal\":\"" + secret + "\","), "REFUSAL"),
                (SemanticEnvelope("{}").Replace("\"message\":{", "\"message\":{\"tool_calls\":[],"), "TOOLS"),
                (SemanticEnvelope(""), "EMPTY_CONTENT"), (SemanticEnvelope("{" + secret), "CONTENT_JSON"),
                (SemanticEnvelope("{}"), "CONTENT_SCHEMA"),
                (SemanticEnvelope(SemanticBody(s).Replace(s.Matches[0].RefKey, "wrong")), "CONTENT_SCHEMA") })
            {
                using var handler = new RecordingHandler(body); using var http = new HttpClient(handler);
                try { await new LmStudioClient(http).ReviewSemanticAsync(new StudioSettings(), secret, c, s, CancellationToken.None); throw new AssertionFailure("Invalid response accepted"); }
                catch (LmStudioException e)
                {
                    Equal(LmDiagnosticCode.BAD_RESPONSE, e.Diagnostic.Code);
                    True(e.Diagnostic.ToString().Contains(category)); True(!e.ToString().Contains(secret)); True(e.InnerException == null);
                }
                Equal(1, handler.Calls); True(c.SemanticReview == null);
            }
        });
    }
}
