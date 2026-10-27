using System.Buffers.Binary;
using System.Security.Cryptography;
using System.Text;

namespace PhantomSemanticStudio.Core;

internal static class V4Compiler
{
    private static readonly string[] Files = ["events.tsv", "records.bin", "registry.tsv", "replies.idx", "triggers.idx", "voices.tsv"];
    private sealed record IndexRow(byte[] Digest, string Id, long Offset, int Length, int Weight);
    private sealed class IndexComparer : IComparer<IndexRow>
    {
        public int Compare(IndexRow? a, IndexRow? b) { var n = a!.Digest.AsSpan().SequenceCompareTo(b!.Digest); return n == 0 ? StringComparer.Ordinal.Compare(a.Id, b.Id) : n; }
    }
    internal static V4BuildResult Build(string stageRoot, IReadOnlyList<V4Entry> entries, V4Registry registry)
    {
        V4Rules.Require(entries.Count > 0, "EMPTY_PACK: сначала импортируйте содержимое, затем создайте runtime stage.");
        PathSafety.AssertNoReparsePoints(stageRoot); Directory.CreateDirectory(stageRoot);
        var temporary = Path.Combine(stageRoot, ".building-" + Guid.NewGuid().ToString("N")); Directory.CreateDirectory(temporary);
        try
        {
            File.WriteAllText(Path.Combine(temporary, "registry.tsv"), registry.CategoryTsv(), V4Rules.Utf8);
            File.WriteAllText(Path.Combine(temporary, "voices.tsv"), registry.VoiceTsv(), V4Rules.Utf8);
            File.WriteAllText(Path.Combine(temporary, "events.tsv"), registry.EventTsv(), V4Rules.Utf8);
            var triggers = new List<IndexRow>(); var replies = new List<IndexRow>();
            using (var output = new FileStream(Path.Combine(temporary, "records.bin"), FileMode.CreateNew, FileAccess.Write))
            {
                foreach (var e in entries.OrderBy(e => e.Id, StringComparer.Ordinal))
                {
                    var raw = V4Rules.Utf8.GetBytes(V4Rules.Wire(e)); V4Rules.Require(raw.Length <= 16 * 1024, "RECORD_BYTES");
                    var row = new IndexRow(e.Mode == "TRIGGER" ? V4Rules.TriggerDigest(e) : V4Rules.ReplyDigest(e), e.Id, output.Position, raw.Length, e.Weight);
                    (e.Mode == "TRIGGER" ? triggers : replies).Add(row); output.Write(raw); output.WriteByte(10);
                    V4Rules.Require(output.Position <= 2_147_483_648L, "RECORDS_BYTES_CAP");
                }
            }
            WriteIndex(Path.Combine(temporary, "triggers.idx"), triggers); WriteIndex(Path.Combine(temporary, "replies.idx"), replies);
            var manifest = "PWV4\t1\n" + string.Concat(Files.Select(name => { var info = new FileInfo(Path.Combine(temporary, name)); return $"file\t{name}\t{info.Length}\t{HashFile(info.FullName)}\n"; }));
            File.WriteAllText(Path.Combine(temporary, "manifest.tsv"), manifest, V4Rules.Utf8); var hash = V4Rules.Hash(V4Rules.Utf8.GetBytes(manifest));
            var verified = Verify(temporary); V4Rules.Require(verified.Success, "BUILD_VERIFY: " + string.Join("; ", verified.Diagnostics.Select(d => d.Message)));
            var target = Path.Combine(stageRoot, hash);
            if (Directory.Exists(target)) { var old = Verify(target); V4Rules.Require(old.Success && old.ManifestHash == hash, "IMMUTABLE_STAGE_CORRUPTED"); }
            else { PathSafety.AssertNoReparsePoints(target); Directory.Move(temporary, target); }
            return new(target, hash, entries.Count);
        }
        finally { if (Directory.Exists(temporary)) Directory.Delete(temporary, true); }
    }
    private static string HashFile(string path)
    { using var input = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.Read); return Convert.ToHexStringLower(SHA256.HashData(input)); }
    private static void WriteIndex(string path, List<IndexRow> rows)
    {
        rows.Sort(new IndexComparer()); using var stream = new FileStream(path, FileMode.CreateNew, FileAccess.Write); var bytes = new byte[48];
        foreach (var row in rows) { Encode(row, bytes); stream.Write(bytes); }
    }
    private static void Encode(IndexRow row, byte[] bytes)
    {
        row.Digest.CopyTo(bytes, 0); BinaryPrimitives.WriteInt64BigEndian(bytes.AsSpan(32), row.Offset);
        BinaryPrimitives.WriteInt32BigEndian(bytes.AsSpan(40), row.Length); BinaryPrimitives.WriteInt32BigEndian(bytes.AsSpan(44), row.Weight);
    }
    internal static V4VerifyResult Verify(string stage)
    {
        var count = 0; var manifestHash = "";
        try
        {
            PathSafety.AssertNoReparsePoints(stage);
            V4Rules.Require(Directory.Exists(stage), "STAGE_NOT_FOUND");
            var names = Directory.GetFileSystemEntries(stage).Select(Path.GetFileName).Order(StringComparer.Ordinal).ToArray();
            V4Rules.Require(names.SequenceEqual(Files.Append("manifest.tsv").Order(StringComparer.Ordinal)), "STAGE_EXACT_FILES");
            foreach (var name in names) { var path = Path.Combine(stage, name!); PathSafety.AssertNoReparsePoints(path); V4Rules.Require(File.Exists(path), "STAGE_FILE_TYPE"); }
            var manifest = ReadText(stage, "manifest.tsv", 4096); manifestHash = V4Rules.Hash(V4Rules.Utf8.GetBytes(manifest));
            var lines = manifest.Split('\n'); V4Rules.Require(lines.Length == 8 && lines[0] == "PWV4\t1" && lines[^1] == "", "MANIFEST_SCHEMA");
            for (var i = 0; i < Files.Length; i++)
            {
                var f = lines[i + 1].Split('\t'); V4Rules.Require(f.Length == 4 && f[0] == "file" && f[1] == Files[i], "MANIFEST_FILES");
                var file = Path.Combine(stage, f[1]); var cap = f[1] == "records.bin" ? 2_147_483_648L : f[1].EndsWith(".idx", StringComparison.Ordinal) ? 48_000_000L : 4 * 1024 * 1024L;
                V4Rules.Require(long.TryParse(f[2], out var declared) && declared >= 0 && declared <= cap && declared.ToString(System.Globalization.CultureInfo.InvariantCulture) == f[2]
                    && new FileInfo(file).Length == declared && f[3].Length == 64 && HashFile(file) == f[3], "MANIFEST_HASH_OR_BYTES");
            }
            var registry = V4Registry.Parse(ReadText(stage, "registry.tsv", 4 * 1024 * 1024), ReadText(stage, "voices.tsv", 4 * 1024 * 1024), ReadText(stage, "events.tsv", 4 * 1024 * 1024));
            V4Rules.Require(registry.Events.All(e => V4Registry.Default.Events.Any(known => known.Code == e.Code)), "UNBACKED_EVENT_REGISTRY");
            V4Rules.Require(ReadText(stage, "registry.tsv", 4 * 1024 * 1024) == registry.CategoryTsv()
                && ReadText(stage, "voices.tsv", 4 * 1024 * 1024) == registry.VoiceTsv() && ReadText(stage, "events.tsv", 4 * 1024 * 1024) == registry.EventTsv(), "REGISTRY_CANONICAL");
            var triggers = new List<IndexRow>(); var replies = new List<IndexRow>(); string previous = ""; var texts = new HashSet<string>(StringComparer.Ordinal);
            var requiredFallbacks = new HashSet<string>(StringComparer.Ordinal);
            var availableFallbacks = new HashSet<string>(StringComparer.Ordinal);
            foreach (var (offset, bytes) in Records(Path.Combine(stage, "records.bin")))
            {
                V4Rules.Require(++count <= V4Rules.MaxRecords, "RECORD_CAP"); var entry = ParseWire(V4Rules.Utf8.GetString(bytes), registry);
                V4Rules.Require(StringComparer.Ordinal.Compare(previous, entry.Id) < 0 && texts.Add(V4Rules.Hash(V4Rules.Utf8.GetBytes(V4Rules.Normalize(entry.Text)))), "RECORD_ORDER_OR_DUPLICATE"); previous = entry.Id;
                var row = new IndexRow(entry.Mode == "TRIGGER" ? V4Rules.TriggerDigest(entry) : V4Rules.ReplyDigest(entry), entry.Id, offset, bytes.Length, entry.Weight);
                (entry.Mode == "TRIGGER" ? triggers : replies).Add(row);
                if (entry.Mode != "TRIGGER")
                {
                    var safe = entry.Voices.Contains("ANY") && entry.Facets.Count == 0 && entry.Profanity == "NONE" && entry.Maturity == "GENERAL"
                        && entry.Tone is not ("ROUGH" or "SARCASTIC" or "PLAYFUL");
                    foreach (var relation in entry.Relations)
                    {
                        var key = $"{entry.Mode}\t{entry.Category}\t{entry.Stance}\t{entry.RequiredEvent}\t{relation}";
                        requiredFallbacks.Add(key);
                        if (safe) availableFallbacks.Add(key);
                    }
                    V4Rules.Require(requiredFallbacks.Count <= 32768 && availableFallbacks.Count <= 32768, "CONTEXT_BUCKET_CAP");
                }
            }
            V4Rules.Require(count > 0, "EMPTY_PACK");
            V4Rules.Require(requiredFallbacks.IsSubsetOf(availableFallbacks),
                "CONTEXT_FALLBACK_REQUIRED: для каждой category/mode/stance/event и допустимых relations добавьте ответ ANY, без facets, NONE/GENERAL, tone CALM или DRY/WARM/SHY/FORMAL/DIRECT.");
            CheckIndex(Path.Combine(stage, "triggers.idx"), triggers); CheckIndex(Path.Combine(stage, "replies.idx"), replies);
            return new(true, manifestHash, count, []);
        }
        catch (Exception ex) when (ex is IOException or InvalidDataException or UnauthorizedAccessException or ArgumentException or FormatException or OverflowException or InvalidOperationException)
        { return new(false, manifestHash, count, [new("ERROR", "V4_STAGE_REJECTED", stage, 0, ex.Message)]); }
    }
    private static string ReadText(string stage, string name, int cap)
    {
        var path = Path.Combine(stage, name); V4Rules.Require(new FileInfo(path).Length <= cap, "TEXT_BYTES"); var bytes = File.ReadAllBytes(path);
        V4Rules.Require(!(bytes.Length >= 3 && bytes[0] == 0xef && bytes[1] == 0xbb && bytes[2] == 0xbf), "UTF8_BOM");
        var text = V4Rules.Utf8.GetString(bytes); V4Rules.Require(!text.Contains('\r') && text.EndsWith('\n'), "TEXT_LF"); return text;
    }
    private static IEnumerable<(long Offset, byte[] Bytes)> Records(string path)
    {
        using var input = new BufferedStream(new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.Read), 64 * 1024);
        var bytes = new byte[16 * 1024]; var length = 0; long offset = 0;
        int b;
        while ((b = input.ReadByte()) != -1)
        {
            if (b == 10) { V4Rules.Require(length > 0, "EMPTY_RECORD"); yield return (offset, bytes.AsSpan(0, length).ToArray()); offset += length + 1; length = 0; }
            else { V4Rules.Require(length < bytes.Length && b is not (0 or 13), "RECORD_BYTES_OR_CONTROL"); bytes[length++] = (byte)b; }
        }
        V4Rules.Require(length == 0, "RECORD_FINAL_LF");
    }
    private static void CheckIndex(string file, List<IndexRow> expected)
    {
        V4Rules.Require(new FileInfo(file).Length == 48L * expected.Count, "INDEX_COUNT"); expected.Sort(new IndexComparer());
        using var input = new FileStream(file, FileMode.Open, FileAccess.Read, FileShare.Read); var actual = new byte[48]; var bytes = new byte[48];
        foreach (var row in expected) { input.ReadExactly(actual); Encode(row, bytes); V4Rules.Require(actual.AsSpan().SequenceEqual(bytes), "INDEX_REFERENCE_OR_ORDER"); }
    }
    internal static V4Entry ParseWire(string line, V4Registry registry)
    {
        var f = line.Split('\t'); V4Rules.Require(f.Length == 17, "RECORD_FIELDS");
        var trigger = f[1] == "TRIGGER"; var category = registry.Categories.SingleOrDefault(c => c.Key == f[2]);
        V4Rules.Require(V4Registry.Modes.Contains(f[1]) && category != null && category.Act == f[3], "RECORD_REFERENCE");
        V4Rules.SafeText(f[4]); V4Rules.Require(V4Rules.Clean(f[4]) == f[4], "TEXT_CANONICAL");
        var e = new V4Entry(f[0], f[1], f[2], f[3], f[4], f[5], f[6], f[7], f[8], Split(f[9]), Split(f[10]), V4Rules.ParseFacets(f[11]), f[12], f[13], Split(f[14]), V4Rules.Integer(f[15], 1, 10), f[16]);
        V4Rules.Require(e.Id == V4Rules.Id(e) && line == V4Rules.Wire(e), "RECORD_ID_OR_CANONICAL");
        V4Rules.Require(e.Relations.SequenceEqual(e.Relations.Distinct().Order(StringComparer.Ordinal)) && e.Voices.SequenceEqual(e.Voices.Distinct().Order(StringComparer.Ordinal))
            && e.Tags.SequenceEqual(e.Tags.Distinct().Order(StringComparer.Ordinal)) && e.Tags.Length <= 16 && e.Tags.All(V4Rules.Tag), "ARRAY_CANONICAL");
        if (trigger) V4Rules.Require(!e.Text.Contains('{') && e.Intent == category!.Intent && V4Registry.Ambiguities.Contains(e.Ambiguity) && e.Stance == "" && e.Tone == "" && e.Relations.Length == 0
            && e.Voices.Length == 0 && e.Facets.Count == 0 && e.Profanity == "NONE" && e.Maturity == "GENERAL" && e.Weight == 1 && e.RequiredEvent == "", "TRIGGER_CONTRACT");
        else V4Rules.Require(e.Intent == "" && e.Ambiguity == "" && V4Registry.Stances.Contains(e.Stance) && V4Registry.Tones.Contains(e.Tone)
            && e.Relations.Length is >= 1 and <= 16 && e.Relations.All(V4Registry.Relations.Contains) && e.Voices.Length is >= 1 and <= 16
            && e.Voices.All(v => v == "ANY" || registry.Voices.Any(voice => voice.Key == v)) && (!e.Voices.Contains("ANY") || e.Voices.Length == 1)
            && V4Registry.Profanities.Contains(e.Profanity) && V4Registry.Maturities.Contains(e.Maturity) && V4Rules.OutboundLength(e.Text)
            && (e.Mode == "REPLY" ? e.RequiredEvent == "" : registry.Events.Any(ev => ev.Code == e.RequiredEvent)), "REPLY_CONTRACT");
        return e;
    }
    private static string[] Split(string value) => value.Length == 0 ? [] : value.Split(',');
}
