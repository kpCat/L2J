using System.Text;
using System.Text.Json;
using System.Xml.Linq;
using PhantomSemanticStudio.Core;

internal static partial class Program
{
    private const string Social = "semantic/custom/my-social-topics.xml";
    private const string Phrases = "conversation/custom/my-phrases.xml";

    private static void Pss003()
    {
        Test("PSS-003 65-file stage preserves source session comments overrides and deterministic IDs", () =>
        {
            using var f = StageFixture();
            using var store = new WorkspaceStore(Path.Combine(f.Root, "studio"), f.Module);
            var pack = new PackReader().Load(f.Module); Equal(65, pack.Files.Count);
            var candidates = StageCandidates(pack);
            store.SaveSession(new SessionState { Candidates = candidates });
            var state = File.ReadAllBytes(Path.Combine(store.Root, "session.json"));
            var first = new IsolatedPackStager().Create(store, pack, candidates, candidates.Select(c => c.Id).ToArray(), true, true);
            var second = new IsolatedPackStager().Create(store, pack, candidates, candidates.Select(c => c.Id).ToArray(), true, true);
            True(first.Root != second.Root); Equal("STAGED_UNVALIDATED", first.Status);
            True(state.SequenceEqual(File.ReadAllBytes(Path.Combine(store.Root, "session.json"))));
            Equal(pack.Fingerprint, new PackReader().Load(f.Module).Fingerprint);
            var staged = new PackReader().Load(first.ModuleRoot);
            Equal(3, staged.Entries.Count(e => e.Kind == "PATTERN")); Equal(3, staged.Entries.Count(e => e.Kind == "TEMPLATE"));
            foreach (var stamp in pack.Files.Where(s => s.RelativePath != Social && s.RelativePath != Phrases))
                Equal(stamp, staged.Files.Single(s => s.RelativePath == stamp.RelativePath));
            var target = Path.Combine(staged.DataRoot, Phrases);
            var doc = XDocument.Load(target, LoadOptions.PreserveWhitespace);
            True(doc.DescendantNodes().OfType<XComment>().Any(c => c.Value == "keep comment"));
            Equal("true", (string?)doc.Descendants("template").Single(e => (string?)e.Attribute("id") == "greet.base").Attribute("override"));
            var added = doc.Descendants("template").Single(e => (string?)e.Attribute("id") == "pss.t.22222222222222222222222222222222");
            Equal("false", (string?)added.Attribute("override")); Equal("NONE", (string?)added.Attribute("profanity"));
            Equal("Тихая тропа & дальний огонёк.", (string?)added.Attribute("text"));
            var p = XDocument.Load(Path.Combine(staged.DataRoot, Social)).Root!.Elements("pattern").Single();
            Equal("pss.p.11111111111111111111111111111111", (string?)p.Attribute("id"));
            Equal("0", (string?)p.Attribute("salience")); Equal("0", (string?)p.Attribute("ttlMinutes")); Equal("500", (string?)p.Attribute("priority"));
            Equal(File.ReadAllText(target), File.ReadAllText(Path.Combine(second.ModuleRoot, "dist/game/data/phantoms", Phrases)));
            using var receipt = JsonDocument.Parse(File.ReadAllBytes(Path.Combine(first.Root, "receipt.json")));
            Equal("NOT_RUN", receipt.RootElement.GetProperty("JavaStatus").GetString());
            Equal(2, receipt.RootElement.GetProperty("Candidates").GetArrayLength());
            True(!File.ReadAllText(Path.Combine(first.Root, "receipt.json")).Contains(candidates[1].Text));
        });
        Test("PSS-003 exact selection consent and stale approval fail entire batch without output", () =>
        {
            using var f = StageFixture(); using var store = new WorkspaceStore(Path.Combine(f.Root, "studio"), f.Module);
            var pack = new PackReader().Load(f.Module); var original = StageCandidates(pack);
            foreach (var change in new Action<Candidate>[] { c => c.Status = "DRAFT", c => c.Status = "REJECTED", c => c.Text += "!", c => c.ReviewNote += "!", c => c.ReviewedAtUtc = null, c => c.ApprovedFingerprint = "bad" })
            {
                var batch = original.Select(c => c.Copy()).ToList(); change(batch[1]); BlockStage("APPROVAL", store, pack, batch);
            }
            BlockStage("CONSENT", store, pack, original, selection: false);
            BlockStage("CONSENT", store, pack, original, editorial: false);
            BlockStage("SELECTION", store, pack, original, ids: [original[0].Id, "missing"]);
            BlockStage("SELECTION", store, pack, original, ids: [original[0].Id, original[0].Id]);
            var one = new IsolatedPackStager().Create(store, pack, original, [original[0].Id], true, true);
            Equal(2, new PackReader().Load(one.ModuleRoot).Entries.Count(e => e.Kind == "TEMPLATE"));
        });
        Test("PSS-003 source drift blocks staging and preserves approval history", () =>
        {
            using var f = StageFixture(); using var store = new WorkspaceStore(Path.Combine(f.Root, "studio"), f.Module);
            var pack = new PackReader().Load(f.Module); var batch = StageCandidates(pack); var approved = batch[0].ApprovedFingerprint;
            File.AppendAllText(f.Segment, "\n");
            BlockStage("SOURCE_DRIFT", store, pack, batch); Equal(approved, batch[0].ApprovedFingerprint); True(CandidateReview.IsCurrent(batch[0]));
        });
        Test("PSS-003 unsupported gender placeholders markup controls gameplay and profanity blocked", () =>
        {
            using var f = StageFixture(); using var store = new WorkspaceStore(Path.Combine(f.Root, "studio"), f.Module);
            var pack = new PackReader().Load(f.Module);
            foreach (var pair in new (string Code, Action<Candidate> Change)[]
            {
                ("GENDER", c => c.Gender = "FEMALE"), ("GENDER", c => c.Gender = "MALE"),
                ("TEXT_SCOPE", c => c.Text = "Свет {name}"), ("TEXT_SCOPE", c => c.Text = "<template override='true'/>"),
                ("TEXT_SCOPE", c => c.Text = "Строка\nс переносом"), ("TEXT_SCOPE", c => c.Text = "Невидимая" + (char)0x200b + "метка"),
                ("EDITORIAL_RISK", c => c.Text = "Я бафнул тебя."), ("EDITORIAL_RISK", c => c.Text = "бранноеслово"),
                ("FUNCTIONAL_ACT", c => c.Act = "support.buff.request"), ("TOPIC_ACT", c => c.Topic = "missing"), ("KIND", c => c.Kind = "CODE")
            })
            {
                var batch = StageCandidates(pack); pair.Change(batch[1]); Seal(batch[1]); BlockStage(pair.Code, store, pack, batch);
            }
        });
        Test("PSS-003 normalized peer duplicates invalid candidate IDs and XML ID collisions blocked", () =>
        {
            using var f = StageFixture(); using var store = new WorkspaceStore(Path.Combine(f.Root, "studio"), f.Module);
            var pack = new PackReader().Load(f.Module); var batch = StageCandidates(pack);
            batch[1].Text = "САЛЮТ!!!"; Seal(batch[1]); BlockStage("EXACT_DUPLICATE", store, pack, batch);
            batch = StageCandidates(pack); var peer = batch[1].Copy(); peer.Id = new string('3', 32); peer.Status = "DRAFT"; batch.Add(peer);
            BlockStage("EXACT_DUPLICATE", store, pack, batch, ids: [batch[0].Id, batch[1].Id]);
            batch = StageCandidates(pack); batch[1].Id = "../../bad"; Seal(batch[1]); BlockStage("ID", store, pack, batch);
            var path = Path.Combine(pack.DataRoot, Phrases); var doc = XDocument.Load(path);
            doc.Root!.Add(new XElement("template", new XAttribute("id", "pss.t.22222222222222222222222222222222"), new XAttribute("act", "greet.reply"), new XAttribute("band", "UNKNOWN"), new XAttribute("register", "NEUTRAL"), new XAttribute("profanity", "NONE"), new XAttribute("text", "Другое существующее предложение."), new XAttribute("override", "false"))); doc.Save(path);
            pack = new PackReader().Load(f.Module); BlockStage("ID_COLLISION", store, pack, StageCandidates(pack));
        });
        Test("PSS-003 actual Java UTF8 UTF16 and custom schema file bounds fail closed", () =>
        {
            using var f = StageFixture(); using var store = new WorkspaceStore(Path.Combine(f.Root, "studio"), f.Module);
            var pack = new PackReader().Load(f.Module); var batch = StageCandidates(pack);
            batch[1].Text = new string('я', 130); Seal(batch[1]); BlockStage("JAVA_LENGTH", store, pack, batch);
            batch = StageCandidates(pack); batch[0].Text = new string('я', 161); Seal(batch[0]); BlockStage("JAVA_LENGTH", store, pack, batch);
            var path = Path.Combine(pack.DataRoot, Social);
            File.WriteAllText(path, "<socialTopics version=\"1\"><!--" + new string('a', 65300) + "--></socialTopics>");
            pack = new PackReader().Load(f.Module); BlockStage("FILE_SIZE", store, pack, StageCandidates(pack));
            File.WriteAllText(path, "<socialTopics version=\"1\" unknown=\"true\"/>");
            pack = new PackReader().Load(f.Module); BlockStage("CUSTOM_SCHEMA", store, pack, StageCandidates(pack));
        });
        Test("PSS-003 cancellation IO failure and reparse leave no finished or unrelated output", () =>
        {
            using var f = StageFixture(); using var store = new WorkspaceStore(Path.Combine(f.Root, "studio"), f.Module);
            var pack = new PackReader().Load(f.Module); var batch = StageCandidates(pack);
            using var cancel = new CancellationTokenSource(); cancel.Cancel();
            Throws(() => new IsolatedPackStager().Create(store, pack, batch, batch.Select(c => c.Id).ToArray(), true, true, cancel.Token));
            NoStages(store);
            var proposals = Path.Combine(store.Root, "proposals"); File.WriteAllText(proposals, "unrelated sentinel");
            Throws(() => new IsolatedPackStager().Create(store, pack, batch, batch.Select(c => c.Id).ToArray(), true, true));
            Equal("unrelated sentinel", File.ReadAllText(proposals)); File.Delete(proposals);
            var outside = Path.Combine(f.Root, "outside"); Directory.CreateDirectory(outside);
            if (OperatingSystem.IsWindows())
            {
                using var link = System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo("cmd.exe", $"/c mklink /J \"{proposals}\" \"{outside}\"") { UseShellExecute = false, CreateNoWindow = true })!;
                link.WaitForExit(); Equal(0, link.ExitCode);
                try { BlockStage("PATH", store, pack, batch); Equal(0, Directory.GetFileSystemEntries(outside).Length); }
                finally { Directory.Delete(proposals); }
            }
        });
        Test("PSS-003 target catalog capacity rejects whole batch at Java hard count", () =>
        {
            using var f = StageFixture(); using var store = new WorkspaceStore(Path.Combine(f.Root, "studio"), f.Module);
            var data = Path.Combine(f.Module, "dist/game/data/phantoms");
            for (var part = 0; part < 2; part++)
            {
                var root = new XElement("humanizedV3SemanticSegment", new XAttribute("version", "3"), new XElement("patterns"));
                for (var i = 0; i < 4095; i++) root.Element("patterns")!.Add(new XElement("pattern", new XAttribute("id", $"full.{part}.{i}"), new XAttribute("topic", "greeting"), new XAttribute("act", "greet.reply"), new XAttribute("phrase", $"полный каталог {part} {i}"), new XAttribute("salience", "0"), new XAttribute("ttlMinutes", "0"), new XAttribute("priority", "500")));
                root.Save(Path.Combine(data, $"semantic/humanized/v3/segments/empty-{part}.xml"));
            }
            var pack = new PackReader().Load(f.Module); Equal(8192, pack.Entries.Count(e => e.Kind == "PATTERN"));
            BlockStage("CAPACITY", store, pack, StageCandidates(pack, approve: false));
        });
        Test("PSS-003 Java template bucket limit rejects before finished stage", () =>
        {
            using var f = StageFixture(); using var store = new WorkspaceStore(Path.Combine(f.Root, "studio"), f.Module);
            var root = new XElement("humanizedV3ConversationSegment", new XAttribute("version", "3"), new XElement("templates"));
            for (var i = 0; i < 4094; i++) root.Element("templates")!.Add(new XElement("template", new XAttribute("id", "bucket." + i), new XAttribute("act", "greet.reply"), new XAttribute("band", "UNKNOWN"), new XAttribute("register", "NEUTRAL"), new XAttribute("profanity", "NONE"), new XAttribute("text", "Проверка отдельного bucket " + i)));
            root.Save(Path.Combine(f.Module, "dist/game/data/phantoms/conversation/humanized/v3/segments/test.xml"));
            // Existing greet.base plus the v3 fixture replaced above: 4095 clean entries.
            var path = Path.Combine(f.Module, "dist/game/data/phantoms", Phrases);
            var doc = XDocument.Load(path); doc.Root!.Add(new XElement("template", new XAttribute("id", "bucket.last"), new XAttribute("act", "greet.reply"), new XAttribute("band", "UNKNOWN"), new XAttribute("register", "NEUTRAL"), new XAttribute("profanity", "NONE"), new XAttribute("text", "Последняя строка полного bucket."), new XAttribute("override", "false"))); doc.Save(path);
            var pack = new PackReader().Load(f.Module); Equal(4096, pack.Entries.Count(e => e.Kind == "TEMPLATE"));
            BlockStage("JAVA_BUCKET", store, pack, StageCandidates(pack));
        });
        Test("PSS-003 mid-copy source drift and cancellation clean only own partial", () =>
        {
            foreach (var cancelDuringCopy in new[] { false, true })
            {
                using var f = StageFixture(); using var store = new WorkspaceStore(Path.Combine(f.Root, "studio"), f.Module);
                var pack = new PackReader().Load(f.Module); var batch = StageCandidates(pack);
                var proposals = Path.Combine(store.Root, "proposals"); Directory.CreateDirectory(proposals);
                var sentinel = Path.Combine(store.Root, "keep.txt"); File.WriteAllText(sentinel, "unrelated");
                using var token = new CancellationTokenSource();
                var observed = new TaskCompletionSource<bool>(TaskCreationOptions.RunContinuationsAsynchronously);
                using var watcher = new FileSystemWatcher(proposals) { NotifyFilter = NotifyFilters.DirectoryName };
                watcher.Created += (_, e) =>
                {
                    if (!e.FullPath.EndsWith(".partial", StringComparison.Ordinal)) return;
                    try
                    {
                        if (cancelDuringCopy) token.Cancel();
                        else File.AppendAllText(Path.Combine(pack.DataRoot, Phrases), "\n");
                        observed.TrySetResult(true);
                    }
                    catch (Exception ex) { observed.TrySetException(ex); }
                };
                watcher.EnableRaisingEvents = true;
                Exception? failure = null;
                try { new IsolatedPackStager().Create(store, pack, batch, batch.Select(c => c.Id).ToArray(), true, true, token.Token); }
                catch (Exception ex) { failure = ex; }
                True(observed.Task.Wait(TimeSpan.FromSeconds(5))); True(observed.Task.Result);
                True(cancelDuringCopy ? failure is OperationCanceledException : failure is InvalidDataException && failure.Message.Contains("SOURCE_DRIFT"));
                NoStages(store); Equal("unrelated", File.ReadAllText(sentinel)); True(batch.All(CandidateReview.IsCurrent));
                if (cancelDuringCopy) Equal(pack.Fingerprint, new PackReader().Load(f.Module).Fingerprint);
            }
        });
    }

    private static List<Candidate> StageCandidates(PackSnapshot pack, bool approve = true)
    {
        var pattern = CandidateOf(pack, "Ветер шелестит за старой башней"); pattern.Id = new string('1', 32); pattern.Kind = "PATTERN";
        var template = CandidateOf(pack, "Тихая тропа & дальний огонёк."); template.Id = new string('2', 32);
        var batch = new List<Candidate> { pattern, template };
        foreach (var c in batch) { if (approve) CandidateReview.Approve(c, pack, batch, "Искусственная fixture проверена вручную"); else Seal(c); }
        return batch;
    }
    private static void Seal(Candidate c) { c.Status = "APPROVED"; c.ReviewNote = "Test fixture"; c.ReviewedAtUtc = DateTimeOffset.UtcNow; c.ApprovedFingerprint = CandidateReview.Fingerprint(c); }
    private static void BlockStage(string code, WorkspaceStore store, PackSnapshot pack, List<Candidate> batch, bool selection = true, bool editorial = true, string[]? ids = null)
    {
        var before = new PackReader().Load(pack.ModuleRoot).Files.ToArray();
        try { new IsolatedPackStager().Create(store, pack, batch, ids ?? batch.Select(c => c.Id).ToArray(), selection, editorial); throw new AssertionFailure("Expected blocked " + code); }
        catch (InvalidDataException e) { True(e.Message.Contains(code, StringComparison.Ordinal)); }
        True(before.SequenceEqual(new PackReader().Load(pack.ModuleRoot).Files)); NoStages(store);
    }
    private static void NoStages(WorkspaceStore store)
    {
        var proposals = Path.Combine(store.Root, "proposals");
        if (Directory.Exists(proposals) && (File.GetAttributes(proposals) & FileAttributes.ReparsePoint) == 0) Equal(0, Directory.GetDirectories(proposals).Length);
    }
    private static Fixture StageFixture()
    {
        var f = new Fixture(); var data = Path.Combine(f.Module, "dist/game/data/phantoms");
        f.SetCustom("<phrases version=\"1\"><!--keep comment--><template id=\"greet.base\" act=\"greet.reply\" band=\"UNKNOWN\" register=\"NEUTRAL\" profanity=\"NONE\" text=\"Салют!\" override=\"true\"/></phrases>");
        foreach (var (path, root) in new[] { (Social, "socialTopics"), ("semantic/custom/my-ru-aliases.xml", "aliases"), ("semantic/custom/my-slang.xml", "slang"), ("conversation/custom/my-profanity.xml", "profanity"), ("conversation/custom/my-mature-dialogue.xml", "matureDialogue") })
        {
            var destination = Path.Combine(data, path); Directory.CreateDirectory(Path.GetDirectoryName(destination)!);
            File.WriteAllText(destination, root == "profanity" ? "<profanity version=\"1\"><entry id=\"test.bad\" level=\"MILD\" acts=\"greet.reply\" text=\"бранноеслово\"/></profanity>" : $"<{root} version=\"1\"/>");
        }
        var manifest = XDocument.Load(f.Manifest);
        for (var i = 0; i < 52; i++)
        {
            var path = $"semantic/humanized/v3/segments/empty-{i}.xml";
            manifest.Root!.Element("segments")!.Add(new XElement("segment", new XAttribute("kind", "SEMANTIC"), new XAttribute("path", path)));
            File.WriteAllText(Path.Combine(data, path), "<humanizedV3SemanticSegment version=\"3\"><patterns/></humanizedV3SemanticSegment>");
        }
        manifest.Save(f.Manifest); return f;
    }

    private static int Pss003Source(string[] args)
    {
        if (args.Length is < 3 or > 4 || args.Length == 4 && args[3] != "--pattern-only") throw new ArgumentException("--pss-003-source <HighFive root> <own workspace under Studio artifacts> [--pattern-only]");
        var patternOnly = args.Length == 4;
        var studio = Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "../../../../../"));
        if (!PathSafety.IsWithin(args[2], Path.Combine(studio, "artifacts"))) throw new InvalidDataException("Test workspace must be in Studio artifacts.");
        var pack = new PackReader().Load(args[1]);
        var scope = pack.Entries.First(e => e.Kind == "PATTERN" && e.Topic == "greeting" && e.Act == "greet.reply");
        var candidates = StageCandidates(pack); if (patternOnly) candidates = candidates.Where(c => c.Kind == "PATTERN").ToList();
        foreach (var c in candidates) { c.Topic = scope.Topic; c.Act = scope.Act; CandidateReview.Approve(c, pack, candidates, "Искусственная fixture PSS-003; не ответы модели"); }
        using var store = new WorkspaceStore(args[2], pack.ModuleRoot);
        var result = new IsolatedPackStager().Create(store, pack, candidates, candidates.Select(c => c.Id).ToArray(), true, true);
        var after = new PackReader().Load(pack.ModuleRoot); True(pack.Files.SequenceEqual(after.Files));
        var evidence = new { pack.ModuleRoot, pack.Fingerprint, Before = pack.Files, After = after.Files, SourceEqual = true, StageRoot = result.Root, result.Status, Candidates = candidates.Select(c => new { c.Id, c.Kind }), JavaStatus = "NOT_RUN" };
        File.WriteAllText(Path.Combine(studio, patternOnly ? "reports/PSS-003-source-pattern.json" : "reports/PSS-003-source.json"), JsonSerializer.Serialize(evidence, WorkspaceStore.JsonOptions), new UTF8Encoding(false));
        File.WriteAllText(Path.Combine(store.Root, patternOnly ? "test-stage-pattern-path.txt" : "test-stage-path.txt"), result.Root);
        Console.WriteLine($"PASS source before/after: {pack.Files.Count} SHA/bytes; fingerprint={pack.Fingerprint}");
        Console.WriteLine("Stage=" + result.Root + "; status=" + result.Status + "; Java=NOT_RUN"); return 0;
    }
}
