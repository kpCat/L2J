using System.Globalization;
using System.Text;
using System.Text.Json;
using System.Text.RegularExpressions;
using System.Xml;
using System.Xml.Linq;

namespace PhantomSemanticStudio.Core;

public sealed record IsolatedStage(string Root, string ModuleRoot, string Status);

public sealed class IsolatedPackStager
{
    private const string Social = "semantic/custom/my-social-topics.xml";
    private const string Phrases = "conversation/custom/my-phrases.xml";
    private static readonly UTF8Encoding Utf8 = new(false, true);

    // Только подготовка предложения. Java/shell/установка отсутствуют в Core и GUI.
    public IsolatedStage Create(WorkspaceStore store, PackSnapshot snapshot, IReadOnlyList<Candidate> allPeers,
        IReadOnlyList<string> selectedIds, bool selectionConfirmed, bool editorialConfirmed, CancellationToken cancellationToken = default)
    {
        if (!selectionConfirmed || !editorialConfirmed) Fail("CONSENT", "Нужно отдельное подтверждение списка и редакционной безопасности. НЕ ДЛЯ УСТАНОВКИ.");
        cancellationToken.ThrowIfCancellationRequested();
        CheckPaths(store, snapshot.ModuleRoot);
        var current = CurrentSource(snapshot, cancellationToken);
        if (selectedIds.Count is < 1 or > 5000 || selectedIds.Distinct(StringComparer.Ordinal).Count() != selectedIds.Count
            || allPeers.Select(c => c.Id).Distinct(StringComparer.Ordinal).Count() != allPeers.Count
            || selectedIds.Any(id => !allPeers.Any(c => c.Id == id)))
            Fail("SELECTION", "Нужен непустой точный список уникальных существующих ID.");
        var peers = allPeers.Select(c => c.Copy()).ToList();
        var selected = selectedIds.Order(StringComparer.Ordinal).Select(id => peers.Single(c => c.Id == id)).ToList();
        Validate(selected, current, peers);
        var proposals = PathSafety.ResolveRelative(store.Root, "proposals");
        var id = Guid.NewGuid().ToString("N");
        var partial = PathSafety.ResolveRelative(store.Root, "proposals/" + id + ".partial");
        var finished = PathSafety.ResolveRelative(store.Root, "proposals/" + id);
        // Все проверки содержимого и объёма — до создания частичного каталога.
        var targets = new Dictionary<string, byte[]>(StringComparer.Ordinal)
        {
            [Social] = Compile(current, Social, selected.Where(c => c.Kind == "PATTERN").ToList()),
            [Phrases] = Compile(current, Phrases, selected.Where(c => c.Kind == "TEMPLATE").ToList())
        };
        try
        {
            CheckPaths(store, current.ModuleRoot); Directory.CreateDirectory(proposals);
            PathSafety.AssertNoReparsePoints(proposals); Directory.CreateDirectory(partial);
            var module = PathSafety.ResolveRelative(partial, "module");
            var data = PathSafety.ResolveRelative(module, "dist/game/data/phantoms");
            foreach (var stamp in current.Files)
            {
                cancellationToken.ThrowIfCancellationRequested(); CheckPaths(store, current.ModuleRoot);
                var source = PathSafety.ResolveRelative(current.DataRoot, stamp.RelativePath);
                var bytes = ReadStamped(source, stamp);
                var destination = PathSafety.ResolveRelative(data, stamp.RelativePath);
                Directory.CreateDirectory(Path.GetDirectoryName(destination)!); PathSafety.AssertNoReparsePoints(destination);
                using (var file = new FileStream(destination, FileMode.CreateNew, FileAccess.Write, FileShare.None)) { file.Write(bytes); file.Flush(true); }
                CheckStamp(destination, stamp);
            }
            foreach (var target in targets)
            {
                cancellationToken.ThrowIfCancellationRequested();
                var path = PathSafety.ResolveRelative(data, target.Key);
                File.WriteAllBytes(path, target.Value);
            }
            var staged = new PackReader().Load(module, cancellationToken);
            foreach (var stamp in current.Files.Where(s => s.RelativePath != Social && s.RelativePath != Phrases))
                if (staged.Files.Single(s => s.RelativePath == stamp.RelativePath) != stamp) Fail("PRESERVATION", "Изменён нецелевой файл.");
            var patterns = current.Entries.Count(e => e.Kind == "PATTERN"); var templates = current.Entries.Count(e => e.Kind == "TEMPLATE");
            var addedPatterns = selected.Count(c => c.Kind == "PATTERN"); var addedTemplates = selected.Count(c => c.Kind == "TEMPLATE");
            if (staged.Entries.Count(e => e.Kind == "PATTERN") != patterns + addedPatterns
                || staged.Entries.Count(e => e.Kind == "TEMPLATE") != templates + addedTemplates
                || staged.Files.Count != current.Files.Count) Fail("PRESERVATION", "Неверные приросты или inventory staging.");
            CurrentSource(snapshot, cancellationToken);
            foreach (var c in selected)
            {
                var original = allPeers.SingleOrDefault(p => p.Id == c.Id);
                if (original == null || !CandidateReview.IsCurrent(original) || original.ApprovedFingerprint != c.ApprovedFingerprint)
                    Fail("APPROVAL", "Одобрение изменено во время подготовки.");
            }
            var receipt = new
            {
                Version = 1, Status = "STAGED_UNVALIDATED", JavaStatus = "NOT_RUN", NotForInstallation = true,
                SourceModule = current.ModuleRoot, SourceFingerprint = current.Fingerprint, CreatedAtUtc = DateTimeOffset.UtcNow,
                SelectionConfirmed = true, EditorialConfirmed = true, SourceFiles = current.Files, StagedFiles = staged.Files,
                BaselinePatterns = patterns, BaselineTemplates = templates, AddedPatterns = addedPatterns, AddedTemplates = addedTemplates,
                Candidates = selected.Select(c => new { c.Id, XmlId = XmlId(c), c.Kind, c.Act, c.Topic, ApprovalHash = c.ApprovedFingerprint, TextHash = TextRules.Hash(c.Text) }),
                JavaCommand = (string?)null, JavaExitCode = (int?)null
            };
            File.WriteAllBytes(PathSafety.ResolveRelative(partial, "receipt.json"), JsonSerializer.SerializeToUtf8Bytes(receipt, WorkspaceStore.JsonOptions));
            cancellationToken.ThrowIfCancellationRequested(); CheckPaths(store, current.ModuleRoot);
            PathSafety.AssertNoReparsePoints(partial); PathSafety.AssertNoReparsePoints(finished);
            Directory.Move(partial, finished);
            return new IsolatedStage(finished, Path.Combine(finished, "module"), "STAGED_UNVALIDATED");
        }
        catch
        {
            RemovePartial(store.Root, partial);
            throw;
        }
    }

    private static void Validate(List<Candidate> selected, PackSnapshot pack, List<Candidate> peers)
    {
        var errors = new List<string>();
        foreach (var c in selected)
        {
            if (!CandidateReview.IsCurrent(c)) { errors.Add("APPROVAL: " + c.Id); continue; }
            if (!Regex.IsMatch(c.Id, "^[0-9a-f]{32}$", RegexOptions.CultureInvariant)) errors.Add("ID: недопустимый candidate ID");
            if (c.Gender != "ANY") errors.Add("GENDER: " + c.Id);
            if (c.Text.IndexOfAny(['{', '}', '<', '>']) >= 0 || c.Text.EnumerateRunes().Any(r => Rune.GetUnicodeCategory(r) is
                UnicodeCategory.Control or UnicodeCategory.Format or UnicodeCategory.LineSeparator or UnicodeCategory.ParagraphSeparator
                or UnicodeCategory.Surrogate or UnicodeCategory.OtherNotAssigned or UnicodeCategory.PrivateUse or UnicodeCategory.MathSymbol))
                errors.Add("TEXT_SCOPE: " + c.Id);
            var normalized = TextRules.Normalize(c.Text);
            if (normalized.Length == 0 || (c.Kind == "PATTERN" && normalized.Length > 160)
                || (c.Kind == "TEMPLATE" && Utf8.GetByteCount(c.Text) > 240)) errors.Add("JAVA_LENGTH: " + c.Id);
            if (pack.Entries.Any(e => e.Id == XmlId(c))) errors.Add("ID_COLLISION: " + c.Id);
            if (pack.Entries.Where(e => e.Kind == "PROFANITY").Any(e => TextRules.Normalize(e.Text) is { Length: > 0 } bad
                && (" " + normalized + " ").Contains(" " + bad + " ", StringComparison.Ordinal))) errors.Add("EDITORIAL_RISK: " + c.Id);
            var issues = CandidateValidator.Validate(c, pack, peers);
            errors.AddRange(issues.Where(i => i.Severity == IssueSeverity.Error).Select(i => i.Code + ": " + c.Id));
            if (issues.Any(i => i.Code == "ACTION_CLAIM")) errors.Add("EDITORIAL_RISK: " + c.Id);
            if (c.Kind == "PATTERN" && pack.Entries.Any(e => e.Kind == "PATTERN" && e.Act != c.Act && TextRules.Normalize(e.Text) == normalized))
                errors.Add("EXACT_DUPLICATE: " + c.Id);
        }
        foreach (var bucket in selected.Where(c => c.Kind == "TEMPLATE").GroupBy(c => (c.Act, c.Band, c.Register)))
            if (pack.Entries.Count(e => e.Kind == "TEMPLATE" && e.Profanity == "NONE" && !e.Mature
                && e.Act == bucket.Key.Act && e.Band == bucket.Key.Band && e.Register == bucket.Key.Register) + bucket.Count() > 4096)
                errors.Add("JAVA_BUCKET: предел 4096 clean templates на act/band/register.");
        if (errors.Count != 0) throw new InvalidDataException("Партия отклонена целиком:\n" + string.Join("\n", errors.Distinct()));
    }

    private static string XmlId(Candidate c) => (c.Kind == "PATTERN" ? "pss.p." : "pss.t.") + c.Id;

    private static byte[] Compile(PackSnapshot pack, string relative, List<Candidate> selected)
    {
        var stamp = pack.Files.SingleOrDefault(s => s.RelativePath == relative);
        if (stamp == null) { Fail("CUSTOM_SCHEMA", "Отсутствует обязательный target custom XML."); }
        var bytes = ReadStamped(PathSafety.ResolveRelative(pack.DataRoot, relative), stamp!);
        var doc = ReadCustom(bytes, relative); var original = new XDocument(doc);
        if (selected.Count == 0) return bytes;
        foreach (var c in selected)
        {
            var element = new XElement(c.Kind == "PATTERN" ? "pattern" : "template", new XAttribute("id", XmlId(c)));
            if (c.Kind == "PATTERN") element.Add(new XAttribute("topic", c.Topic));
            element.Add(new XAttribute("act", c.Act));
            if (c.Kind == "PATTERN") element.Add(new XAttribute("phrase", c.Text), new XAttribute("salience", "0"), new XAttribute("ttlMinutes", "0"), new XAttribute("priority", "500"));
            else element.Add(new XAttribute("band", c.Band), new XAttribute("register", c.Register), new XAttribute("profanity", "NONE"), new XAttribute("text", c.Text));
            element.Add(new XAttribute("override", "false")); doc.Root!.Add(element);
        }
        using var output = new MemoryStream();
        using (var writer = XmlWriter.Create(output, new XmlWriterSettings { Encoding = Utf8, Indent = false, NewLineHandling = NewLineHandling.None, CloseOutput = false })) doc.Save(writer);
        var result = output.ToArray();
        if (result.Length > 65536) Fail("FILE_SIZE", "Target custom XML превышает 65536 bytes.");
        var roundtrip = ReadCustom(result, relative);
        foreach (var element in roundtrip.Root!.Elements().TakeLast(selected.Count).ToList()) element.Remove();
        if (!XNode.DeepEquals(original, roundtrip)) Fail("PRESERVATION", "Существующие custom nodes/атрибуты/комментарии потеряны.");
        return result;
    }

    private static XDocument ReadCustom(byte[] bytes, string relative)
    {
        using var reader = XmlReader.Create(new StringReader(Utf8.GetString(bytes).TrimStart('\uFEFF')), new XmlReaderSettings
        { DtdProcessing = DtdProcessing.Prohibit, XmlResolver = null, MaxCharactersInDocument = 131072, MaxCharactersFromEntities = 0 });
        var doc = XDocument.Load(reader, LoadOptions.PreserveWhitespace);
        var pattern = relative == Social; var root = doc.Root;
        if (root == null || root.Name != (pattern ? "socialTopics" : "phrases") || (string?)root.Attribute("version") != "1"
            || root.Attributes().Any(a => a.Name != "version") || doc.DescendantNodes().OfType<XProcessingInstruction>().Any())
            Fail("CUSTOM_SCHEMA", "Неверный custom root/version/атрибут.");
        string[] allowed = pattern ? ["id", "topic", "act", "phrase", "fact", "recall", "salience", "ttlMinutes", "priority", "override"]
            : ["id", "act", "band", "register", "profanity", "text", "mature", "override"];
        string[] required = pattern ? ["id", "topic", "act", "phrase", "salience", "ttlMinutes", "priority", "override"]
            : ["id", "act", "band", "register", "profanity", "text", "override"];
        foreach (var node in root!.Nodes())
        {
            if (node is XComment || node is XText text && string.IsNullOrWhiteSpace(text.Value)) continue;
            if (node is not XElement e || e.Name != (pattern ? "pattern" : "template") || e.Elements().Any()
                || e.Nodes().Any(n => n is not XComment && (n is not XText t || !string.IsNullOrWhiteSpace(t.Value)))
                || e.Attributes().Any(a => !allowed.Contains(a.Name.ToString(), StringComparer.Ordinal))
                || required.Any(a => e.Attribute(a) == null) || (string?)e.Attribute("override") is not ("true" or "false"))
                Fail("CUSTOM_SCHEMA", "Неизвестный custom элемент/атрибут или неполная запись.");
        }
        return doc;
    }

    private static PackSnapshot CurrentSource(PackSnapshot expected, CancellationToken token)
    {
        var current = new PackReader().Load(expected.ModuleRoot, token);
        if (current.Fingerprint != expected.Fingerprint || !current.Files.SequenceEqual(expected.Files))
            Fail("SOURCE_DRIFT", "Исходный fingerprint/inventory изменён. Нужен импорт и ручное ревью.");
        return current;
    }
    private static byte[] ReadStamped(string path, SourceFileStamp stamp)
    {
        using var stream = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.Read);
        if (stream.Length != stamp.Bytes || stream.Length > 1024 * 1024) Fail("SOURCE_DRIFT", "Неверный размер source файла.");
        var bytes = new byte[checked((int)stream.Length)]; stream.ReadExactly(bytes);
        if (TextRules.Hash(bytes) != stamp.Sha256) Fail("SOURCE_DRIFT", "Source SHA изменён во время копирования.");
        return bytes;
    }
    private static void CheckStamp(string path, SourceFileStamp stamp)
    {
        if (new FileInfo(path).Length != stamp.Bytes || TextRules.Hash(File.ReadAllBytes(path)) != stamp.Sha256)
            Fail("COPY_HASH", "Побайтная копия не совпала с source.");
    }
    private static void CheckPaths(WorkspaceStore store, string source)
    {
        try
        {
            store.Check(); PathSafety.AssertDisjoint(PathSafety.RepositoryBoundary(source), store.Root);
            PathSafety.AssertNoReparsePoints(Path.Combine(store.Root, "proposals"));
        }
        catch (InvalidDataException) { Fail("PATH", "Workspace/source пересекаются или путь содержит reparse point."); }
    }
    private static void RemovePartial(string root, string partial)
    {
        if (!Directory.Exists(partial)) return;
        if (!PathSafety.IsWithin(partial, Path.Combine(root, "proposals")) || !Path.GetFileName(partial).EndsWith(".partial", StringComparison.Ordinal))
            Fail("PATH", "Уборка разрешена только собственному partial stage.");
        PathSafety.AssertNoReparsePoints(partial);
        var pending = new Stack<string>(); pending.Push(partial);
        while (pending.TryPop(out var directory))
            foreach (var path in Directory.EnumerateFileSystemEntries(directory))
            {
                PathSafety.AssertNoReparsePoints(path);
                if (Directory.Exists(path)) pending.Push(path);
            }
        Directory.Delete(partial, true);
    }
    private static void Fail(string code, string message) => throw new InvalidDataException(code + ": " + message);
}
