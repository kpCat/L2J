using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Text.RegularExpressions;
using System.Xml;
using System.Xml.Linq;

namespace PhantomSemanticStudio.Core;

public sealed record V3SegmentPair(string Category, string SemanticPath, string ConversationPath)
{
    public override string ToString() => Category;
}
public sealed record V3CandidateStamp(string Id, string XmlId, string Kind, string Act, string Topic, string Band,
    string Register, string ApprovalHash, string TextHash);
public sealed record V3StageReceipt
{
    public int Version { get; init; } = 1;
    public string StageId { get; init; } = "";
    public string Status { get; init; } = "STAGED_V3_UNVALIDATED";
    public string JavaStatus { get; init; } = "NOT_RUN";
    public bool NotForInstallation { get; init; } = true;
    public bool SelectionConfirmed { get; init; }
    public bool EditorialConfirmed { get; init; }
    public string SourceModule { get; init; } = "";
    public string SourceFingerprint { get; init; } = "";
    public V3SegmentPair Pair { get; init; } = new("", "", "");
    public List<SourceFileStamp> SourceFiles { get; init; } = [];
    public List<SourceFileStamp> StagedFiles { get; init; } = [];
    public List<V3CandidateStamp> Candidates { get; init; } = [];
    public int BaselinePatterns { get; init; }
    public int BaselineTemplates { get; init; }
    public int AddedPatterns { get; init; }
    public int AddedTemplates { get; init; }
}
public static class V3ProposalContract
{
    public const string Manifest = "semantic/humanized/v3/manifest.xml";
    internal static readonly UTF8Encoding Utf8 = new(false, true);
    internal static bool Key(string value) => Regex.IsMatch(value, "^[a-z][a-z0-9_.-]{0,63}$", RegexOptions.CultureInvariant);
    internal static bool Sha(string value) => Regex.IsMatch(value, "^[0-9a-f]{64}$", RegexOptions.CultureInvariant);
    public static IReadOnlyList<V3SegmentPair> GetPairs(PackSnapshot pack, CancellationToken token = default)
    {
        var manifest = ReadXml(ReadStamped(pack, Manifest)); var root = manifest.Root!;
        Require(root.Name == "humanizedV3Manifest", "MANIFEST_SCHEMA");
        Attributes(root, ["id", "version", "maxFiles", "maxFileBytes", "maxTotalBytes", "maxPatterns", "maxTemplates", "maxAliases", "maxProfanity"]);
        var bounds = new Dictionary<string, string> { ["id"] = "high-five-ru-humanized-v3", ["version"] = "3", ["maxFiles"] = "64", ["maxFileBytes"] = "1048576",
            ["maxTotalBytes"] = "33554432", ["maxPatterns"] = "8192", ["maxTemplates"] = "32768", ["maxAliases"] = "2048", ["maxProfanity"] = "1024" };
        Require(bounds.All(p => (string?)root.Attribute(p.Key) == p.Value), "MANIFEST_BOUNDS");
        Require(root.Elements().Select(e => e.Name.ToString()).SequenceEqual(["topics", "acts", "segments"]), "MANIFEST_SECTIONS");
        foreach (var (section, tag) in new[] { ("topics", "topic"), ("acts", "act") })
        {
            var parent = root.Element(section)!; Attributes(parent, []); var keys = new HashSet<string>(StringComparer.Ordinal);
            foreach (var item in parent.Elements()) { Attributes(item, ["key"]); Require(item.Name == tag && !item.Elements().Any() && Key((string)item.Attribute("key")!) && keys.Add((string)item.Attribute("key")!), "MANIFEST_SYMBOLS"); }
            Require(keys.Count > 0, "MANIFEST_SYMBOLS");
        }
        var declared = root.Element("segments")!; Attributes(declared, []);
        var segments = declared.Elements().ToArray(); Require(segments.Length is >= 1 and <= 64, "SEGMENT_CAP");
        var paths = new HashSet<string>(StringComparer.OrdinalIgnoreCase); long bytes = ReadStamped(pack, Manifest).Length;
        var semantic = new Dictionary<string, (string Path, string Category)>(StringComparer.Ordinal);
        var conversation = new Dictionary<string, (string Path, string Category)>(StringComparer.Ordinal);
        foreach (var segment in segments)
        {
            token.ThrowIfCancellationRequested(); Attributes(segment, ["kind", "path"]);
            var kind = (string)segment.Attribute("kind")!; var path = (string)segment.Attribute("path")!;
            var prefix = kind == "SEMANTIC" ? "semantic/" : "conversation/";
            Require(segment.Name == "segment" && !segment.Elements().Any() && kind is "SEMANTIC" or "CONVERSATION"
                && path.StartsWith(prefix + "humanized/v3/segments/", StringComparison.Ordinal) && path.EndsWith(".xml", StringComparison.Ordinal)
                && !path.Contains("..", StringComparison.Ordinal) && paths.Add(path), "MANIFEST_PATH");
            var raw = ReadStamped(pack, path); bytes += raw.Length; Require(bytes <= 33554432, "V3_TOTAL_BYTES");
            var doc = ReadSegment(raw, kind); var category = (string)doc.Root!.Attribute("category")!;
            (kind == "SEMANTIC" ? semantic : conversation).Add(path[prefix.Length..], (path, category));
        }
        var pairs = new List<V3SegmentPair>();
        foreach (var item in semantic.OrderBy(p => p.Key, StringComparer.Ordinal))
            if (conversation.TryGetValue(item.Key, out var other))
            { Require(item.Value.Category == other.Category, "PAIR_CATEGORY"); pairs.Add(new(item.Value.Category, item.Value.Path, other.Path)); }
        Require(pairs.Select(p => p.Category).Distinct(StringComparer.Ordinal).Count() == pairs.Count, "AMBIGUOUS_CATEGORY");
        return Array.AsReadOnly(pairs.ToArray());
    }
    internal static XDocument ReadXml(byte[] bytes)
    {
        using var reader = XmlReader.Create(new StringReader(Utf8.GetString(bytes).TrimStart('\uFEFF')), new XmlReaderSettings
            { DtdProcessing = DtdProcessing.Prohibit, XmlResolver = null, MaxCharactersInDocument = 2097152, MaxCharactersFromEntities = 0 });
        var doc = XDocument.Load(reader, LoadOptions.PreserveWhitespace);
        Require(doc.Root != null && !doc.DescendantNodes().OfType<XProcessingInstruction>().Any(), "XML_SCHEMA"); return doc;
    }
    internal static XDocument ReadSegment(byte[] bytes, string kind)
    {
        Require(bytes.Length is > 0 and <= 1048576, "FILE_SIZE"); var doc = ReadXml(bytes); var root = doc.Root!; var semantic = kind == "SEMANTIC";
        Require(root.Name == (semantic ? "humanizedV3SemanticSegment" : "humanizedV3ConversationSegment"), "SEGMENT_SCHEMA");
        Attributes(root, semantic ? ["id", "version", "category"] : ["id", "version", "category", "mature"]);
        Require((string?)root.Attribute("version") == "3" && Key((string)root.Attribute("id")!) && Key((string)root.Attribute("category")!), "SEGMENT_SCHEMA");
        if (!semantic) Require((string?)root.Attribute("mature") is "true" or "false", "SEGMENT_SCHEMA");
        var sections = new HashSet<string>(StringComparer.Ordinal);
        foreach (var section in root.Elements())
        {
            var name = section.Name.ToString(); Require(sections.Add(name) && (semantic ? name is "aliases" or "patterns" : name is "templates" or "profanity"), "SEGMENT_SCHEMA");
            Attributes(section, [], ["version"]);
            foreach (var e in section.Elements())
            {
                switch (name)
                {
                    case "patterns": Require(e.Name == "pattern", "SEGMENT_SCHEMA"); Attributes(e, ["id", "topic", "act", "phrase", "salience", "ttlMinutes", "priority"], ["fact", "recall"]); break;
                    case "templates": Require(e.Name == "template", "SEGMENT_SCHEMA"); Attributes(e, ["id", "act", "band", "register", "profanity", "text"], ["mature"]); break;
                    case "aliases": Require(e.Name == "alias", "SEGMENT_SCHEMA"); Attributes(e, ["from", "to"]); break;
                    case "profanity": Require(e.Name == "entry", "SEGMENT_SCHEMA"); Attributes(e, ["id", "level", "acts", "text"]); break;
                }
                Require(!e.Elements().Any() && e.Nodes().All(n => n is XComment || n is XText t && string.IsNullOrWhiteSpace(t.Value)), "SEGMENT_SCHEMA");
            }
            Require(section.Nodes().All(n => n is XElement or XComment || n is XText t && string.IsNullOrWhiteSpace(t.Value)), "SEGMENT_SCHEMA");
        }
        Require(root.Nodes().All(n => n is XElement or XComment || n is XText t && string.IsNullOrWhiteSpace(t.Value)), "SEGMENT_SCHEMA"); return doc;
    }
    private static void Attributes(XElement e, string[] required, string[]? optional = null)
    {
        Require(required.All(a => e.Attribute(a) != null) && e.Attributes().All(a => required.Contains(a.Name.ToString()) || (optional ?? []).Contains(a.Name.ToString())), "XML_ATTRIBUTES");
    }
    internal static byte[] ReadStamped(PackSnapshot pack, string relative)
    {
        var stamp = pack.Files.SingleOrDefault(s => s.RelativePath == relative); Require(stamp != null, "SOURCE_INVENTORY");
        return ReadStamped(PathSafety.ResolveRelative(pack.DataRoot, relative), stamp!);
    }
    internal static byte[] ReadStamped(string path, SourceFileStamp stamp)
    {
        PathSafety.AssertNoReparsePoints(path); using var file = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.Read);
        Require(file.Length == stamp.Bytes && file.Length is > 0 and <= 1048576, "SOURCE_DRIFT");
        var bytes = new byte[(int)file.Length]; file.ReadExactly(bytes); Require(TextRules.Hash(bytes) == stamp.Sha256, "SOURCE_DRIFT"); return bytes;
    }
    internal static PackSnapshot Current(PackSnapshot expected, CancellationToken token)
    { var current = new PackReader().Load(expected.ModuleRoot, token); Require(current.Fingerprint == expected.Fingerprint && current.Files.SequenceEqual(expected.Files), "SOURCE_DRIFT"); return current; }
    internal static void CheckPaths(WorkspaceStore store, string source)
    { store.Check(); PathSafety.AssertDisjoint(PathSafety.RepositoryBoundary(source), store.Root); PathSafety.AssertNoReparsePoints(Path.Combine(store.Root, "v3-proposals")); }
    internal static void WriteNew(string path, byte[] bytes)
    { Directory.CreateDirectory(Path.GetDirectoryName(path)!); PathSafety.AssertNoReparsePoints(path); using var file = new FileStream(path, FileMode.CreateNew, FileAccess.Write, FileShare.None); file.Write(bytes); file.Flush(true); }
    internal static void RemoveOwnPartial(string parent, string partial)
    {
        Require(PathSafety.IsWithin(partial, parent) && Regex.IsMatch(Path.GetFileName(partial), "^[0-9a-f]{32}\\.partial$"), "PARTIAL_PATH");
        if (!Directory.Exists(partial)) return; PathSafety.AssertNoReparsePoints(partial);
        var pending = new Stack<string>(); pending.Push(partial);
        while (pending.TryPop(out var directory)) foreach (var p in Directory.EnumerateFileSystemEntries(directory))
        { PathSafety.AssertNoReparsePoints(p); if (Directory.Exists(p)) pending.Push(p); }
        Directory.Delete(partial, true);
    }
    public static V3StageReceipt ReadStage(string workspaceRoot, string stageRoot, CancellationToken token = default)
    {
        var workspace = PathSafety.Canonical(workspaceRoot); var stage = PathSafety.Canonical(stageRoot);
        Require(Path.GetDirectoryName(stage) == Path.Combine(workspace, "v3-proposals") && Regex.IsMatch(Path.GetFileName(stage), "^[0-9a-f]{32}$"), "STAGE_PATH");
        PathSafety.AssertNoReparsePoints(stage); PathSafety.AssertNoReparsePoints(workspace);
        var receipt = ReadJson<V3StageReceipt>(PathSafety.ResolveRelative(stage, "receipt.json"));
        Require(receipt.Version == 1 && receipt.StageId == Path.GetFileName(stage) && receipt.Status == "STAGED_V3_UNVALIDATED" && receipt.JavaStatus == "NOT_RUN"
            && receipt.NotForInstallation && receipt.SelectionConfirmed && receipt.EditorialConfirmed && receipt.Candidates.Count is >= 1 and <= 20
            && receipt.SourceFiles.Count is >= 7 and <= 77 && receipt.StagedFiles.Count == receipt.SourceFiles.Count
            && receipt.Candidates.Select(c => c.Id).Distinct(StringComparer.Ordinal).Count() == receipt.Candidates.Count, "RECEIPT_SCHEMA");
        PathSafety.AssertDisjoint(PathSafety.RepositoryBoundary(receipt.SourceModule), workspace);
        var source = new PackReader().Load(receipt.SourceModule, token); var staged = new PackReader().Load(PathSafety.ResolveRelative(stage, "module"), token);
        Require(source.Fingerprint == receipt.SourceFingerprint && source.Files.SequenceEqual(receipt.SourceFiles) && staged.Files.SequenceEqual(receipt.StagedFiles), "STAGE_SOURCE_HASH");
        Require(GetPairs(source, token).Contains(receipt.Pair), "STAGE_PAIR");
        var selectedPaths = receipt.Candidates.Select(c => c.Kind == "PATTERN" ? receipt.Pair.SemanticPath : receipt.Pair.ConversationPath).Distinct(StringComparer.Ordinal).ToArray();
        foreach (var f in source.Files.Where(f => !selectedPaths.Contains(f.RelativePath))) Require(staged.Files.Single(s => s.RelativePath == f.RelativePath) == f, "NON_TARGET_DELTA");
        foreach (var c in receipt.Candidates)
        {
            Require(Regex.IsMatch(c.Id, "^[0-9a-f]{32}$") && c.Kind is "PATTERN" or "TEMPLATE" && c.XmlId == "pss.v3." + (c.Kind == "PATTERN" ? "p." : "t.") + c.Id
                && Key(c.Act) && Key(c.Topic) && Sha(c.ApprovalHash) && Sha(c.TextHash) && CandidateValidator.Bands.Contains(c.Band) && CandidateValidator.Registers.Contains(c.Register), "RECEIPT_CANDIDATE");
            Require(!source.Entries.Any(e => e.Id == c.XmlId), "RECEIPT_COLLISION");
            var e = staged.Entries.SingleOrDefault(e => e.Kind == c.Kind && e.Id == c.XmlId);
            Require(e != null && e.SourceFile == (c.Kind == "PATTERN" ? receipt.Pair.SemanticPath : receipt.Pair.ConversationPath)
                && e.Act == c.Act && (c.Kind != "PATTERN" || e.Topic == c.Topic) && TextRules.Hash(e.Text) == c.TextHash && e.Profanity == "NONE" && !e.Mature
                && (c.Kind != "TEMPLATE" || e.Band == c.Band && e.Register == c.Register), "RECEIPT_CONTENT");
        }
        foreach (var path in selectedPaths)
        {
            var kind = path == receipt.Pair.SemanticPath ? "SEMANTIC" : "CONVERSATION";
            var original = ReadSegment(ReadStamped(source, path), kind); var changed = ReadSegment(ReadStamped(staged, path), kind);
            foreach (var c in receipt.Candidates.Where(c => (c.Kind == "PATTERN" ? receipt.Pair.SemanticPath : receipt.Pair.ConversationPath) == path))
            {
                var node = changed.Descendants().Single(e => (string?)e.Attribute("id") == c.XmlId);
                Require(node.PreviousNode is XText t && t.Value == "\n\t\t", "APPEND_SHAPE"); node.PreviousNode!.Remove(); node.Remove();
            }
            Require(XNode.DeepEquals(original, changed), "APPEND_ONLY_DELTA");
        }
        Require(receipt.BaselinePatterns == source.Entries.Count(e => e.Kind == "PATTERN") && receipt.BaselineTemplates == source.Entries.Count(e => e.Kind == "TEMPLATE")
            && receipt.AddedPatterns == receipt.Candidates.Count(c => c.Kind == "PATTERN") && receipt.AddedTemplates == receipt.Candidates.Count(c => c.Kind == "TEMPLATE")
            && staged.Entries.Count == source.Entries.Count + receipt.Candidates.Count, "RECEIPT_COUNTS");
        var data = staged.DataRoot; var pending = new Stack<string>(); pending.Push(PathSafety.ResolveRelative(stage, "module")); var actualFiles = new List<string>();
        while (pending.TryPop(out var directory)) foreach (var p in Directory.EnumerateFileSystemEntries(directory))
        {
            token.ThrowIfCancellationRequested(); PathSafety.AssertNoReparsePoints(p);
            if (Directory.Exists(p)) pending.Push(p); else { Require(PathSafety.IsWithin(p, data), "EXTRA_STAGE_FILE"); actualFiles.Add(Path.GetRelativePath(data, p).Replace('\\', '/')); Require(actualFiles.Count <= receipt.StagedFiles.Count, "EXTRA_STAGE_FILE"); }
        }
        Require(actualFiles.Order(StringComparer.Ordinal).SequenceEqual(receipt.StagedFiles.Select(s => s.RelativePath).Order(StringComparer.Ordinal)), "EXTRA_STAGE_FILE"); return receipt;
    }
    internal static T ReadJson<T>(string path, int maximumBytes = 262144)
    {
        PathSafety.AssertNoReparsePoints(path); using var input = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.Read);
        Require(input.Length > 0 && input.Length <= maximumBytes && maximumBytes <= 1048576, "JSON_SIZE"); var bytes = new byte[(int)input.Length]; input.ReadExactly(bytes);
        using var json = JsonDocument.Parse(bytes, new JsonDocumentOptions { MaxDepth = 32 });
        static void Unique(JsonElement e)
        {
            if (e.ValueKind == JsonValueKind.Object) { var keys = new HashSet<string>(StringComparer.Ordinal); foreach (var p in e.EnumerateObject()) { Require(keys.Add(p.Name), "JSON_DUPLICATE_KEY"); Unique(p.Value); } }
            else if (e.ValueKind == JsonValueKind.Array) foreach (var item in e.EnumerateArray()) Unique(item);
        }
        Unique(json.RootElement);
        return JsonSerializer.Deserialize<T>(bytes, new JsonSerializerOptions { UnmappedMemberHandling = JsonUnmappedMemberHandling.Disallow, MaxDepth = 32 }) ?? throw new InvalidDataException("JSON_NULL");
    }
    internal static void Require(bool condition, string code)
    { if (!condition) throw new InvalidDataException(code + ": проверка v3 отклонена целиком. Нужен актуальный источник и ручное ревью."); }
}
