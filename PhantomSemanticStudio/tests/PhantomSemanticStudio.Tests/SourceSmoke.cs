using System.Text;
using System.Text.Json;
using System.Xml;
using System.Xml.Linq;
using PhantomSemanticStudio.Core;

// Targeted route существующего runner. Пишет доказательства только в reports Studio.
internal static class SourceSmoke
{
    public static async Task<int> RunAsync(string[] args)
    {
        if (args.Length is < 3 or > 4) throw new ArgumentException("--source-smoke <HighFive root> <Studio reports> [--live-lm]");
        var module = PathSafety.Canonical(args[1]); var output = PathSafety.Canonical(args[2]);
        var studio = PathSafety.Canonical(Path.Combine(AppContext.BaseDirectory, "../../../../../"));
        if (!PathSafety.IsWithin(output, Path.Combine(studio, "reports"))) throw new InvalidDataException("Доказательства только в reports приложения.");
        PathSafety.AssertDisjoint(PathSafety.RepositoryBoundary(module), output);
        Directory.CreateDirectory(output);
        var root = Path.Combine(module, "dist/game/data/phantoms");
        var before = Stamp(root, Paths(root));
        PackSnapshot? snapshot = null; string? importError = null;
        var lmStatus = "NOT_RUN"; string? lmDetail = null; List<DraftItem> drafts = [];
        try
        {
            snapshot = new PackReader().Load(module);
            if (args.Length == 4 && args[3] == "--live-lm")
            {
                var settings = new StudioSettings { HighFiveRoot = module, TimeoutSeconds = 180 };
                using var http = LmStudioClient.CreateHttpClient(); var client = new LmStudioClient(http);
                try
                {
                    var check = await client.CheckModelAsync(settings, "", CancellationToken.None);
                    if (check.Code != LmDiagnosticCode.CHECKED_MODEL_LIST)
                        throw new LmStudioException(check);
                    var scope = snapshot.Entries.FirstOrDefault(e => e.Kind == "PATTERN" && e.Topic == "greeting" && !TextRules.IsFunctionalAct(e.Act))
                        ?? throw new InvalidDataException("Нет разговорного greeting pattern для targeted запроса.");
                    var request = new GenerationRequest(scope.Topic, scope.Act, "UNKNOWN", "NEUTRAL", "ANY",
                        "Предложи две разные короткие разговорные реплики знакомства без игровых действий, условий, подстановок, матов и взрослого содержания.", "вечер, встреча", 2, GenerationMode.TEMPLATE);
                    drafts = await client.GenerateAsync(settings, "", snapshot, request, CancellationToken.None);
                    lmStatus = drafts.Count is >= 1 and <= 2 ? "PASS_STRICT_JSON_DRAFTS_ONLY" : "FAILED_CANDIDATE_COUNT";
                    lmDetail = $"Exact model {settings.ModelId}; один POST; {drafts.Count} JSON-кандидатов, никто не одобрен.";
                }
                catch (LmStudioException e) { lmStatus = "BLOCKED_LM"; lmDetail = e.Diagnostic.ToString(); }
            }
        }
        catch (Exception e) { importError = e.GetType().Name + ": " + e.Message; }
        finally
        {
            var after = Stamp(root, Paths(root));
            var equal = before.SequenceEqual(after) && (snapshot == null || snapshot.Files.OrderBy(f => f.RelativePath, StringComparer.Ordinal).SequenceEqual(before));
            var evidence = new
            {
                Source = module, Import = importError == null ? "PASS" : "FAILED", ImportError = importError,
                snapshot?.Fingerprint, Files = snapshot?.Files.Count,
                Patterns = snapshot?.Entries.Count(e => e.Kind == "PATTERN"), Templates = snapshot?.Entries.Count(e => e.Kind == "TEMPLATE"),
                Aliases = snapshot?.Entries.Count(e => e.Kind == "ALIAS"), Profanity = snapshot?.Entries.Count(e => e.Kind == "PROFANITY"),
                snapshot?.Topics, snapshot?.Acts, snapshot?.Warnings, SourceBeforeAfterEqual = equal,
                Before = before, After = after, LM = lmStatus, LMDetail = lmDetail, DraftCount = drafts.Count,
                Approval = "NONE", JavaValidator = "NOT_RUN", Export = "REVIEW_ONLY_NOT_SERVER_VALIDATED"
            };
            File.WriteAllText(Path.Combine(output, "PSS-002-source-smoke.json"), JsonSerializer.Serialize(evidence, WorkspaceStore.JsonOptions), new UTF8Encoding(false));
            Console.WriteLine($"Import={(importError == null ? "PASS" : "FAILED")}; files={snapshot?.Files.Count}; patterns={snapshot?.Entries.Count(e => e.Kind == "PATTERN")}; templates={snapshot?.Entries.Count(e => e.Kind == "TEMPLATE")}; fingerprint={snapshot?.Fingerprint}");
            Console.WriteLine($"Source before/after equal={equal}; LM={lmStatus}; {lmDetail}; {importError}");
            if (!equal) importError = "Source stamps differ";
        }
        return importError == null ? 0 : 1;
    }

    private static List<SourceFileStamp> Stamp(string root, IEnumerable<string> paths) => paths
        .Order(StringComparer.Ordinal).Select(p =>
        {
            var bytes = File.ReadAllBytes(PathSafety.ResolveRelative(root, p));
            return new SourceFileStamp(p, TextRules.Hash(bytes), bytes.Length);
        }).ToList();

    private static List<string> Paths(string root)
    {
        string[] known =
        [
            "semantic/humanized/high-five-ru-humanized-semantic-v1.xml", "conversation/humanized/high-five-ru-humanized-conversation-v1.xml",
            "conversation/humanized/high-five-ru-persona-v1.xml", "semantic/humanized/high-five-ru-humanized-corpus-v1.tsv",
            "semantic/humanized/high-five-ru-humanized-semantic-v2.xml", "conversation/humanized/high-five-ru-humanized-conversation-v2.xml",
            "semantic/humanized/v3/manifest.xml", "semantic/custom/my-ru-aliases.xml", "semantic/custom/my-slang.xml",
            "semantic/custom/my-social-topics.xml", "conversation/custom/my-phrases.xml", "conversation/custom/my-profanity.xml", "conversation/custom/my-mature-dialogue.xml"
        ];
        var paths = known.Where(p => File.Exists(PathSafety.ResolveRelative(root, p))).ToList();
        var manifest = PathSafety.ResolveRelative(root, "semantic/humanized/v3/manifest.xml");
        if (!File.Exists(manifest)) return paths;
        if (new FileInfo(manifest).Length > 262144) throw new InvalidDataException("Manifest превышает лимит.");
        using var reader = XmlReader.Create(manifest, new XmlReaderSettings { DtdProcessing = DtdProcessing.Prohibit, XmlResolver = null, MaxCharactersInDocument = 524288 });
        var doc = XDocument.Load(reader);
        var segments = doc.Root?.Element("segments")?.Elements("segment").ToList() ?? [];
        if (segments.Count > 64) throw new InvalidDataException("Более 64 сегментов.");
        foreach (var segment in segments)
        {
            var path = (string?)segment.Attribute("path") ?? ""; var kind = (string?)segment.Attribute("kind");
            if (!(kind == "SEMANTIC" && path.StartsWith("semantic/humanized/v3/segments/", StringComparison.Ordinal)
                || kind == "CONVERSATION" && path.StartsWith("conversation/humanized/v3/segments/", StringComparison.Ordinal)) || !path.EndsWith(".xml", StringComparison.Ordinal))
                throw new InvalidDataException("Неверный segment path.");
            var absolute = PathSafety.ResolveRelative(root, path);
            if (new FileInfo(absolute).Length > 1024 * 1024) throw new InvalidDataException("Сегмент превышает лимит.");
            paths.Add(path);
        }
        return paths;
    }
}
