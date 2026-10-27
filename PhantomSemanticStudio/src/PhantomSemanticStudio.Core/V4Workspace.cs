using System.Security.Cryptography;
using System.Text.Encodings.Web;
using System.Text.Json;

namespace PhantomSemanticStudio.Core;

/// <summary>Offline authoring only. Every write is guarded against the complete protected repository.</summary>
public sealed class V4Workspace
{
    private readonly string root;
    private readonly string source;
    private readonly string? hostRepository;
    private static readonly JsonSerializerOptions Json = new() { Encoder = JavaScriptEncoder.UnsafeRelaxedJsonEscaping, MaxDepth = 16 };
    public V4Workspace(string root, string protectedSource)
    {
        this.root = PathSafety.Canonical(root); source = PathSafety.RepositoryBoundary(PathSafety.Canonical(protectedSource));
        hostRepository = FindRepository(AppContext.BaseDirectory);
        Guard();
    }
    private static string? FindRepository(string path)
    {
        for (var directory = new DirectoryInfo(path); directory != null; directory = directory.Parent)
            if (Directory.Exists(Path.Combine(directory.FullName, ".git")) || File.Exists(Path.Combine(directory.FullName, ".git"))) return directory.FullName;
        return null;
    }
    private void Guard()
    {
        PathSafety.AssertDisjoint(source, root);
        if (hostRepository != null) PathSafety.AssertDisjoint(hostRepository, root);
        V4Rules.Require(FindRepository(root) == null, "WORKSPACE_IN_REPOSITORY");
    }
    private string Local(string relative) { Guard(); return PathSafety.ResolveRelative(root, relative); }
    private FileStream Lock()
    {
        Guard(); Directory.CreateDirectory(root); return new FileStream(Local("workspace.lock"), FileMode.OpenOrCreate, FileAccess.ReadWrite, FileShare.None);
    }
    private V4Registry Registry()
    {
        var file = Local("approved-registry.tsv");
        return File.Exists(file) ? V4Registry.Parse(ReadBounded(file, 4 * 1024 * 1024), V4Registry.Default.VoiceTsv(), V4Registry.Default.EventTsv()) : V4Registry.Default;
    }
    private static string ReadBounded(string file, long cap)
    {
        return V4Rules.Utf8.GetString(V4Rules.ReadBytes(file, cap));
    }
    private static string[] Inputs(string path)
    {
        path = PathSafety.Canonical(path); PathSafety.AssertNoReparsePoints(path);
        if (File.Exists(path)) { V4Rules.Require(Path.GetExtension(path).Equals(".jsonl", StringComparison.OrdinalIgnoreCase), "JSONL_EXTENSION"); return [path]; }
        V4Rules.Require(Directory.Exists(path), "INPUT_NOT_FOUND");
        var files = new List<string>(); var stack = new Stack<string>(); stack.Push(path);
        while (stack.TryPop(out var folder))
        {
            PathSafety.AssertNoReparsePoints(folder);
            foreach (var entry in Directory.EnumerateFileSystemEntries(folder).Order(StringComparer.Ordinal))
            {
                PathSafety.AssertNoReparsePoints(entry);
                if (Directory.Exists(entry)) { V4Rules.Require(Path.GetRelativePath(path, entry).Split(Path.DirectorySeparatorChar).Length <= 12, "INPUT_DEPTH"); stack.Push(entry); }
                else if (Path.GetExtension(entry).Equals(".jsonl", StringComparison.OrdinalIgnoreCase)) { files.Add(entry); V4Rules.Require(files.Count <= 10000, "INPUT_FILE_CAP"); }
            }
        }
        V4Rules.Require(files.Count > 0 && files.Distinct(StringComparer.OrdinalIgnoreCase).Count() == files.Count, "INPUT_FILES");
        return files.Order(StringComparer.Ordinal).ToArray();
    }
    private (V4Report Report, List<(string File, string Hash)> Sources) Prepare(string path, V4Registry registry)
    {
        var entries = new List<V4Entry>(); var diagnostics = new List<V4Diagnostic>(); var sources = new List<(string File, string Hash)>();
        var duplicates = 0; var ids = new Dictionary<string, V4Entry>(StringComparer.Ordinal); long bytes = 0;
        try
        {
            var files = Inputs(path); var basePath = Directory.Exists(path) ? PathSafety.Canonical(path) : Path.GetDirectoryName(PathSafety.Canonical(path))!;
            foreach (var file in files)
            {
                bytes += new FileInfo(file).Length; V4Rules.Require(bytes <= 256L * 1024 * 1024, "INPUT_BYTES_CAP");
                var report = V4BatchReader.Read(file, registry); diagnostics.AddRange(report.Diagnostics); sources.Add((file, report.InputHash));
                foreach (var entry in report.Entries)
                {
                    var normalized = V4Rules.Normalize(entry.Text);
                    if (ids.TryGetValue(normalized, out var prior))
                    {
                        duplicates++;
                        if (prior.Id != entry.Id && (diagnostics.Count < 1000 || !diagnostics.Any(d => d.Severity == "ERROR")))
                            diagnostics.Add(new("ERROR", "CROSS_FILE_DUPLICATE", file, 0, "Одинаковый текст имеет противоречивые метаданные; партия не принята."));
                    }
                    else { ids.Add(normalized, entry); entries.Add(entry); }
                    V4Rules.Require(entries.Count <= V4Rules.MaxRecords, "RECORD_CAP");
                }
            }
            var hash = V4Rules.Hash(V4Rules.Utf8.GetBytes(registry.Hash + "\n" + string.Concat(sources.Select(s => Path.GetRelativePath(basePath, s.File).Replace('\\', '/') + "\t" + s.Hash + "\n"))));
            return (new(entries.Count, duplicates, entries.AsReadOnly(), diagnostics.AsReadOnly()) { InputHash = hash }, sources);
        }
        catch (Exception ex) when (ex is IOException or InvalidDataException or UnauthorizedAccessException or ArgumentException)
        { diagnostics.Add(new("ERROR", "INPUT_REJECTED", path, 0, ex.Message)); return (new(0, duplicates, [], diagnostics.AsReadOnly()), sources); }
    }
    public V4Report Validate(string path) => Prepare(path, Registry()).Report;
    public string InputHash(string path) => Validate(path).InputHash;
    public V4Report Import(string path, bool dryRun = false, string? expectedInputHash = null)
    {
        if (dryRun) return ImportPrepared(path, true, expectedInputHash);
        using var held = Lock(); return ImportPrepared(path, false, expectedInputHash);
    }
    private V4Report ImportPrepared(string path, bool dryRun, string? expectedInputHash)
    {
        var prepared = Prepare(path, Registry()); var report = prepared.Report;
        if (!report.Success) return report with { Accepted = 0 };
        if (expectedInputHash != null && expectedInputHash != report.InputHash)
            return report with { Accepted = 0, Diagnostics = [new("ERROR", "STALE_REVIEW", path, 0, "Файлы или реестр изменились после проверки. Повторите просмотр.")] };
        var existing = ReadEntries(); var byText = existing.ToDictionary(e => V4Rules.Normalize(e.Text), StringComparer.Ordinal);
        var additions = new List<V4Entry>(); var diagnostics = report.Diagnostics.ToList(); var duplicates = report.Duplicates;
        // Bounded lexical shortlist. This is advisory, never a claim of semantic equivalence.
        var signatures = new Dictionary<string, V4Entry>(StringComparer.Ordinal);
        foreach (var e in existing) signatures.TryAdd(Signature(e.Text), e);
        foreach (var e in report.Entries)
        {
            var text = V4Rules.Normalize(e.Text);
            if (byText.TryGetValue(text, out var prior))
            {
                duplicates++;
                if (prior.Id != e.Id && (diagnostics.Count < 1000 || !diagnostics.Any(d => d.Severity == "ERROR")))
                    diagnostics.Add(new("ERROR", "WORKSPACE_DUPLICATE", path, 0, "Текст уже существует с другими метаданными; требуется отдельный review: " + prior.Id));
                continue;
            }
            var signature = Signature(e.Text);
            if (diagnostics.Count < 1000 && signatures.TryGetValue(signature, out var neighbor) && neighbor.Text != e.Text)
                diagnostics.Add(new("WARNING", "NEAR_DUPLICATE_REVIEW", path, 0, "Лексически похожая запись: " + neighbor.Id + ". Проверьте вручную; смысл не установлен автоматически."));
            signatures.TryAdd(signature, e); byText.Add(text, e); additions.Add(e);
        }
        V4Rules.Require(existing.Count + additions.Count <= V4Rules.MaxRecords, "RECORD_CAP");
        if (diagnostics.Any(d => d.Severity == "ERROR")) return report with { Accepted = 0, Duplicates = duplicates, Diagnostics = diagnostics.AsReadOnly() };
        if (!dryRun && additions.Count > 0)
        {
            // Preserve byte-exact input preimages before the single current-snapshot pointer changes.
            foreach (var item in prepared.Sources)
            {
                var data = V4Rules.ReadBytes(item.File, V4BatchReader.MaxFileBytes); V4Rules.Require(V4Rules.Hash(data) == item.Hash, "INPUT_CHANGED");
                Immutable("imports/" + item.Hash + ".jsonl", data);
            }
            existing.AddRange(additions); var dataBytes = JsonSerializer.SerializeToUtf8Bytes(existing.OrderBy(e => e.Id, StringComparer.Ordinal), Json);
            V4Rules.Require(dataBytes.Length <= 1_073_741_824, "SNAPSHOT_BYTES_CAP");
            var hash = V4Rules.Hash(dataBytes); Immutable("snapshots/" + hash + ".json", dataBytes); Atomic("current.txt", V4Rules.Utf8.GetBytes(hash + "\n"));
        }
        return report with { Accepted = additions.Count, Duplicates = duplicates, Diagnostics = diagnostics.AsReadOnly() };
    }
    private static string Signature(string text) => string.Join(' ', V4Rules.Normalize(text).Split(' ')
        .Select(s => new string(s.Where(char.IsLetter).ToArray())).Where(s => s.Length > 2).Distinct().Order(StringComparer.Ordinal));
    private List<V4Entry> ReadEntries()
    {
        var pointer = Local("current.txt"); if (!File.Exists(pointer)) return [];
        var hash = ReadBounded(pointer, 65).TrimEnd('\n'); V4Rules.Require(hash.Length == 64 && hash.All(c => c is >= '0' and <= '9' or >= 'a' and <= 'f'), "SNAPSHOT_POINTER");
        var bytes = V4Rules.Utf8.GetBytes(ReadBounded(Local("snapshots/" + hash + ".json"), 1_073_741_824)); V4Rules.Require(V4Rules.Hash(bytes) == hash, "SNAPSHOT_HASH");
        try
        {
            var entries = JsonSerializer.Deserialize<List<V4Entry>>(bytes, Json); V4Rules.Require(entries != null && entries.Count <= V4Rules.MaxRecords, "SNAPSHOT_RECORDS");
            var registry = Registry(); var ids = new HashSet<string>(StringComparer.Ordinal); var texts = new HashSet<string>(StringComparer.Ordinal); string previous = "";
            foreach (var e in entries!)
            {
                _ = V4Compiler.ParseWire(V4Rules.Wire(e), registry);
                V4Rules.Require(ids.Add(e.Id) && texts.Add(V4Rules.Normalize(e.Text)) && StringComparer.Ordinal.Compare(previous, e.Id) < 0, "SNAPSHOT_ORDER_OR_DUPLICATE"); previous = e.Id;
            }
            return entries!;
        }
        catch (Exception ex) when (ex is JsonException or NullReferenceException or ArgumentException or InvalidOperationException)
        { throw new InvalidDataException("SNAPSHOT_INVALID", ex); }
    }
    private void Immutable(string relative, byte[] data)
    {
        var target = Local(relative); Directory.CreateDirectory(Path.GetDirectoryName(target)!);
        if (File.Exists(target)) { V4Rules.Require(File.ReadAllBytes(target).AsSpan().SequenceEqual(data), "IMMUTABLE_COLLISION"); return; }
        var temp = target + ".tmp-" + Guid.NewGuid().ToString("N");
        try { File.WriteAllBytes(temp, data); Guard(); PathSafety.AssertNoReparsePoints(target); File.Move(temp, target); }
        finally { if (File.Exists(temp)) File.Delete(temp); }
    }
    private void Atomic(string relative, byte[] data)
    {
        var target = Local(relative); Directory.CreateDirectory(Path.GetDirectoryName(target)!); var temp = target + ".tmp-" + Guid.NewGuid().ToString("N");
        try { File.WriteAllBytes(temp, data); Guard(); PathSafety.AssertNoReparsePoints(target); File.Move(temp, target, true); }
        finally { if (File.Exists(temp)) File.Delete(temp); }
    }
    public V4BuildResult Build() { using var held = Lock(); return V4Compiler.Build(Local("stages"), ReadEntries(), Registry()); }
    public V4VerifyResult Verify(string stage) { Guard(); return V4Compiler.Verify(PathSafety.Canonical(stage)); }
    public V4Stats Stats()
    {
        var entries = ReadEntries(); return new(entries.Count, Categories().Select(c => new V4Coverage(c.Key,
            entries.Count(e => e.Category == c.Key && e.Mode == "TRIGGER"), entries.Count(e => e.Category == c.Key && e.Mode == "REPLY"),
            entries.Count(e => e.Category == c.Key && e.Mode == "INITIATIVE"))).ToArray());
    }
    public V4Entry? Preview(string category, string voice = "calm", string relation = "UNKNOWN", bool maturityAllowed = false)
    {
        var registry = Registry(); var profile = registry.Voices.SingleOrDefault(v => v.Key == voice); V4Rules.Require(profile != null && V4Registry.Relations.Contains(relation), "PREVIEW_CONTEXT");
        return ReadEntries().Where(e => e.Category == category && e.Mode == "REPLY" && e.Relations.Contains(relation)
            && (e.Voices.Contains("ANY") || e.Voices.Contains(voice)) && (e.Maturity == "GENERAL" || maturityAllowed && relation == "TRUSTED")
            && e.Profanity == "NONE" && e.Facets.All(f => profile!.Facets[f.Key] >= f.Value)).OrderBy(e => e.Id, StringComparer.Ordinal).FirstOrDefault();
    }
    public IReadOnlyList<V4Category> Categories() => Registry().Categories;
    public void ExportPassport(string path)
    {
        path = PathSafety.Canonical(path); V4Rules.Require(PathSafety.IsWithin(path, root), "EXPORT_WORKSPACE_ONLY");
        using var held = Lock(); Atomic(Path.GetRelativePath(root, path).Replace('\\', '/'), V4Rules.Utf8.GetBytes(V4Passport.Create(Registry())));
    }
    public V4ProposalResult Propose(string parent, string key, string label, string act, string intent)
    {
        using var held = Lock(); var registry = Registry(); var category = new V4Category(key, label, act, intent); CheckProposal(parent, category, registry);
        var proposal = new Proposal("pw-taxonomy-proposal/1", registry.Hash, parent, category); var bytes = JsonSerializer.SerializeToUtf8Bytes(proposal, Json); var hash = V4Rules.Hash(bytes);
        var relative = "proposals/" + hash + ".json"; Immutable(relative, bytes); return new(Local(relative), hash);
    }
    private static void CheckProposal(string parent, V4Category category, V4Registry registry)
    {
        V4Rules.Require((V4Rules.Key(parent) || V4Registry.Roots.Contains(parent)) && category.Key.StartsWith(parent + ".", StringComparison.Ordinal)
            && registry.Categories.Any(c => c.Key.StartsWith(parent + ".", StringComparison.Ordinal))
            && !registry.Categories.Any(c => c.Key == parent || c.Key == category.Key || c.Act == category.Act || c.Intent == category.Intent), "PROPOSAL_PARENT_OR_COLLISION");
        _ = new V4Registry(registry.Categories.Append(category), registry.Voices, registry.Events);
    }
    public void Approve(string proposalPath, string expectedHash, bool reviewed)
    {
        V4Rules.Require(reviewed, "EXPLICIT_REVIEW_REQUIRED"); using var held = Lock(); proposalPath = PathSafety.Canonical(proposalPath);
        V4Rules.Require(PathSafety.IsWithin(proposalPath, Local("proposals")), "PROPOSAL_PATH");
        var text = ReadBounded(proposalPath, 16 * 1024); var bytes = V4Rules.Utf8.GetBytes(text); V4Rules.Require(V4Rules.Hash(bytes) == expectedHash, "PROPOSAL_REVIEW_HASH");
        Proposal proposal;
        try
        {
            using var document = JsonDocument.Parse(text, new JsonDocumentOptions { MaxDepth = 4 });
            V4Rules.JsonKeys(document.RootElement, ["Schema", "BaseRegistryHash", "Parent", "Category"], []);
            var category = document.RootElement.GetProperty("Category"); V4Rules.JsonKeys(category, ["Key", "Label", "Act", "Intent"], []);
            proposal = new(V4Rules.String(document.RootElement, "Schema"), V4Rules.String(document.RootElement, "BaseRegistryHash"),
                V4Rules.String(document.RootElement, "Parent"), new(V4Rules.String(category, "Key"), V4Rules.String(category, "Label"), V4Rules.String(category, "Act"), V4Rules.String(category, "Intent")));
        }
        catch (JsonException ex) { throw new InvalidDataException("PROPOSAL_JSON", ex); }
        var registry = Registry();
        V4Rules.Require(proposal.Schema == "pw-taxonomy-proposal/1" && proposal.BaseRegistryHash == registry.Hash, "STALE_PROPOSAL");
        CheckProposal(proposal.Parent, proposal.Category, registry);
        var approved = new V4Registry(registry.Categories.Append(proposal.Category), registry.Voices, registry.Events);
        Immutable("approvals/" + expectedHash + ".json", bytes); Atomic("approved-registry.tsv", V4Rules.Utf8.GetBytes(approved.CategoryTsv()));
    }
    private sealed record Proposal(string Schema, string BaseRegistryHash, string Parent, V4Category Category);
}
