using System.Diagnostics;
using System.Text.Json;
using System.Xml.Linq;
using PhantomSemanticStudio.Core;

internal static partial class Program
{
    private static void Pss008C()
    {
        Test("PSS-008 C missing fake proof consent cancellation no release or stage mutation", () =>
        {
            using var f = new Fixture(); using var store = new WorkspaceStore(Path.Combine(f.Root, "studio"), f.Module);
            var pack = new PackReader().Load(f.Module); var peers = StageCandidates(pack); var pair = V3ProposalContract.GetPairs(pack).Single();
            var stage = new IsolatedV3ProposalStager().Create(store, pack, peers, peers.Select(c => c.Id).ToArray(), pair, true, true);
            var receiptBytes = File.ReadAllBytes(Path.Combine(stage.Root, "receipt.json"));
            var oracle = Path.Combine(stage.Root, "oracle-" + Guid.NewGuid().ToString("N")); Directory.CreateDirectory(oracle);
            var proof = Path.Combine(oracle, "java-validation.json"); File.WriteAllText(proof, "{\"Version\":1,\"JavaStatus\":\"PASS_JAVA_STAGED_V3\"}");
            Throws(() => V3ReleaseHandoff.Prepare(store, stage.Root, proof, TextRules.Hash(File.ReadAllBytes(proof)), true));
            Throws(() => V3ReleaseHandoff.Prepare(store, stage.Root, proof, "", false));
            using var cancel = new CancellationTokenSource(); cancel.Cancel(); Throws(() => V3ReleaseHandoff.Prepare(store, stage.Root, proof, "", true, cancel.Token));
            True(!Directory.Exists(Path.Combine(store.Root, "release-candidates"))); True(receiptBytes.SequenceEqual(File.ReadAllBytes(Path.Combine(stage.Root, "receipt.json"))));
            // A metadata-only PASS is never a genuine proof.
            Throws(() => V3ProposalContract.ReadStage(store.Root, stage.Root + ".partial"));
        });
        Test("PSS-008 C exact append-only stage verification rejects modified receipt and extra staged XML", () =>
        {
            using var f = new Fixture(); using var store = new WorkspaceStore(Path.Combine(f.Root, "studio"), f.Module);
            var pack = new PackReader().Load(f.Module); var peers = StageCandidates(pack); var pair = V3ProposalContract.GetPairs(pack).Single();
            var stage = new IsolatedV3ProposalStager().Create(store, pack, peers, peers.Select(c => c.Id).ToArray(), pair, true, true);
            var receipt = V3ProposalContract.ReadStage(store.Root, stage.Root); Equal(2, receipt.Candidates.Count);
            File.WriteAllText(Path.Combine(stage.ModuleRoot, "extra.xml"), "<extra/>"); Throws(() => V3ProposalContract.ReadStage(store.Root, stage.Root)); File.Delete(Path.Combine(stage.ModuleRoot, "extra.xml"));
            var file = Path.Combine(stage.Root, "receipt.json"); var original = File.ReadAllBytes(file);
            File.WriteAllBytes(file, JsonSerializer.SerializeToUtf8Bytes(receipt with { AddedTemplates = 99 }, WorkspaceStore.JsonOptions)); Throws(() => V3ProposalContract.ReadStage(store.Root, stage.Root));
            File.WriteAllBytes(file, original); Equal(2, V3ProposalContract.ReadStage(store.Root, stage.Root).Candidates.Count);
        });
        Test("PSS-008 C forged full metadata with fake tooling fails even when local log hashes match", () =>
        {
            using var f = new Fixture(); using var store = new WorkspaceStore(Path.Combine(f.Root, "studio"), f.Module);
            var pack = new PackReader().Load(f.Module); var peers = StageCandidates(pack); var pair = V3ProposalContract.GetPairs(pack).Single();
            var stage = new IsolatedV3ProposalStager().Create(store, pack, peers, peers.Select(c => c.Id).ToArray(), pair, true, true);
            var proof = SyntheticV3Proof(stage, pack);
            Throws(() => V3ReleaseHandoff.InspectProof(store, stage.Root, proof));
            Throws(() => V3ReleaseHandoff.Prepare(store, stage.Root, proof, TextRules.Hash(File.ReadAllBytes(proof)), true));
            True(!Directory.Exists(Path.Combine(store.Root, "release-candidates")));
            Console.WriteLine("FORGED_METADATA_REJECTED; no release; successful packaging tested separately against genuine native proof");
        });
    }
    private static string SyntheticV3Proof(IsolatedStage stage, PackSnapshot pack)
    {
        var receipt = JsonSerializer.Deserialize<V3StageReceipt>(File.ReadAllBytes(Path.Combine(stage.Root, "receipt.json")))!;
        var oracle = Path.Combine(stage.Root, "oracle-" + Guid.NewGuid().ToString("N")); Directory.CreateDirectory(oracle);
        File.WriteAllText(Path.Combine(pack.ModuleRoot, "build.xml"), "synthetic proof contract fixture only");
        var input = new List<SourceFileStamp> { new("build.xml", TextRules.Hash(File.ReadAllBytes(Path.Combine(pack.ModuleRoot, "build.xml"))), new FileInfo(Path.Combine(pack.ModuleRoot, "build.xml")).Length) };
        File.WriteAllBytes(Path.Combine(oracle, "input-inventory.json"), JsonSerializer.SerializeToUtf8Bytes(input));
        File.WriteAllText(Path.Combine(oracle, "operator.ps1"), "synthetic contract evidence"); File.WriteAllText(Path.Combine(oracle, "Pss008V3CatalogProbe.java"), "synthetic contract evidence");
        var evidence = $"PASS_JAVA_STAGED_V3 baselinePatterns={receipt.BaselinePatterns} baselineTemplates={receipt.BaselineTemplates} stagedPatterns={receipt.BaselinePatterns + receipt.AddedPatterns} stagedTemplates={receipt.BaselineTemplates + receipt.AddedTemplates} checkedIds={receipt.Candidates.Count} baselineHash={new string('a', 64)} stagedHash={new string('b', 64)} negative=DUPLICATE_AND_SCHEMA_REJECTED";
        var logs = new List<SourceFileStamp>();
        foreach (var (name, text) in new[] { ("ant-content.txt", "BUILD SUCCESSFUL\n"), ("probe.txt", evidence + "\n"), ("negative.txt", "REJECTED_V3_NEGATIVE\n"),
            ("negative-schema.txt", "REJECTED_V3_NEGATIVE\n"), ("probe-compile.txt", "synthetic compile\n"), ("java-version.txt", "javac 25.synthetic\n"), ("ant-version.txt", "Apache Ant synthetic\n") })
        { File.WriteAllText(Path.Combine(oracle, name), text); var bytes = File.ReadAllBytes(Path.Combine(oracle, name)); logs.Add(new(name, TextRules.Hash(bytes), bytes.Length)); }
        string Hash(string name) => TextRules.Hash(File.ReadAllBytes(Path.Combine(oracle, name)));
        var proof = new { Version = 1, JavaStatus = "PASS_JAVA_STAGED_V3", Status = "JAVA_CONTENT_ONLY_NOT_RUNTIME_READY", receipt.StageId, ReceiptHash = TextRules.Hash(File.ReadAllBytes(Path.Combine(stage.Root, "receipt.json"))),
            receipt.SourceFingerprint, SourceEqual = true, SourceFiles = receipt.SourceFiles, StageFiles = receipt.StagedFiles, Scratch = oracle,
            AntExitCode = 0, ProbeExitCode = 0, CompileExitCode = 0, NegativeExitCode = 3, SchemaNegativeExitCode = 3,
            ScriptHash = Hash("operator.ps1"), BridgeHash = Hash("Pss008V3CatalogProbe.java"), InputInventoryHash = Hash("input-inventory.json"),
            Jdk = "javac 25.synthetic", Ant = "Apache Ant synthetic", CopyFiles = input.Count, CopyBytes = input.Sum(f => f.Bytes), Logs = logs, ProbeEvidence = evidence };
        var path = Path.Combine(oracle, "java-validation.json"); File.WriteAllBytes(path, JsonSerializer.SerializeToUtf8Bytes(proof)); return path;
    }
    private static void Pss008B()
    {
        Test("PSS-008 B cancellation during final peer recheck never publishes a finished stage", () =>
        {
            using var f = new Fixture(); using var store = new WorkspaceStore(Path.Combine(f.Root, "studio"), f.Module);
            var pack = new PackReader().Load(f.Module); var peers = StageCandidates(pack); using var cancel = new CancellationTokenSource();
            var changing = new FinalRecheckCancellation(peers, cancel);
            Throws(() => new IsolatedV3ProposalStager().Create(store, pack, changing, peers.Select(c => c.Id).ToArray(), V3ProposalContract.GetPairs(pack).Single(), true, true, cancel.Token));
            True(cancel.IsCancellationRequested); NoV3Stages(store); True(pack.Files.SequenceEqual(new PackReader().Load(f.Module).Files));
        });
        Test("PSS-008 B mid-copy source approval and cancellation preserve unrelated finished sentinel", () =>
        {
            foreach (var change in new[] { "source", "approval", "cancel" })
            {
                using var f = V3Fixture(); using var store = new WorkspaceStore(Path.Combine(f.Root, "studio"), f.Module);
                var pack = new PackReader().Load(f.Module); var peers = StageCandidates(pack); var pair = V3ProposalContract.GetPairs(pack).Single(p => p.Category == "test");
                var parent = Path.Combine(store.Root, "v3-proposals"); Directory.CreateDirectory(parent);
                var keep = Path.Combine(parent, new string('a', 32)); Directory.CreateDirectory(keep); File.WriteAllText(Path.Combine(keep, "keep.txt"), "unrelated finished sentinel");
                using var cancel = new CancellationTokenSource(); var observed = new TaskCompletionSource<bool>(TaskCreationOptions.RunContinuationsAsynchronously);
                using var watcher = new FileSystemWatcher(parent) { NotifyFilter = NotifyFilters.DirectoryName };
                watcher.Created += (_, e) =>
                {
                    if (!e.FullPath.EndsWith(".partial", StringComparison.Ordinal)) return;
                    try { if (change == "cancel") cancel.Cancel(); else if (change == "approval") peers[0].Text += "!"; else File.AppendAllText(f.Segment, "\n"); observed.TrySetResult(true); }
                    catch (Exception ex) { observed.TrySetException(ex); }
                };
                watcher.EnableRaisingEvents = true; Exception? failure = null;
                try { new IsolatedV3ProposalStager().Create(store, pack, peers, peers.Select(c => c.Id).ToArray(), pair, true, true, cancel.Token); }
                catch (Exception ex) { failure = ex; }
                True(observed.Task.Wait(TimeSpan.FromSeconds(5))); True(observed.Task.Result);
                True(change == "cancel" ? failure is OperationCanceledException : failure is InvalidDataException);
                Equal(1, Directory.GetDirectories(parent).Length); Equal("unrelated finished sentinel", File.ReadAllText(Path.Combine(keep, "keep.txt")));
                if (change != "source") True(pack.Files.SequenceEqual(new PackReader().Load(f.Module).Files));
            }
        });
        Test("PSS-008 B junction and parent file reject without touching unrelated targets", () =>
        {
            using var f = new Fixture(); using var store = new WorkspaceStore(Path.Combine(f.Root, "studio"), f.Module);
            var pack = new PackReader().Load(f.Module); var peers = StageCandidates(pack); var pair = V3ProposalContract.GetPairs(pack).Single();
            var parent = Path.Combine(store.Root, "v3-proposals"); File.WriteAllText(parent, "unrelated parent file");
            Throws(() => new IsolatedV3ProposalStager().Create(store, pack, peers, peers.Select(c => c.Id).ToArray(), pair, true, true));
            Equal("unrelated parent file", File.ReadAllText(parent)); File.Delete(parent);
            if (OperatingSystem.IsWindows())
            {
                var outside = Path.Combine(f.Root, "unrelated"); Directory.CreateDirectory(outside); File.WriteAllText(Path.Combine(outside, "keep.txt"), "keep");
                using var link = Process.Start(new ProcessStartInfo("cmd.exe", $"/c mklink /J \"{parent}\" \"{outside}\"") { UseShellExecute = false, CreateNoWindow = true })!;
                link.WaitForExit(); Equal(0, link.ExitCode);
                try { Throws(() => new IsolatedV3ProposalStager().Create(store, pack, peers, peers.Select(c => c.Id).ToArray(), pair, true, true)); Equal(1, Directory.GetFileSystemEntries(outside).Length); Equal("keep", File.ReadAllText(Path.Combine(outside, "keep.txt"))); }
                finally { Directory.Delete(parent); }
            }
        });
        Test("PSS-008 B preserves existing trailing whitespace while appending real-layout XML", () =>
        {
            using var f = new Fixture(); using var store = new WorkspaceStore(Path.Combine(f.Root, "studio"), f.Module);
            File.WriteAllText(f.Segment, File.ReadAllText(f.Segment).Replace("</patterns>", "\n\t</patterns>"));
            var pack = new PackReader().Load(f.Module); var peers = StageCandidates(pack); var pair = V3ProposalContract.GetPairs(pack).Single();
            var stage = new IsolatedV3ProposalStager().Create(store, pack, peers, [peers[0].Id], pair, true, true);
            Equal("STAGED_V3_UNVALIDATED", stage.Status); V3ProposalContract.ReadStage(store.Root, stage.Root);
        });
        Test("PSS-008 B XML ID global collision file and clean bucket caps fail before output", () =>
        {
            using var f = new Fixture(); using var store = new WorkspaceStore(Path.Combine(f.Root, "studio"), f.Module);
            var data = Path.Combine(f.Module, "dist/game/data/phantoms"); var path = Path.Combine(data, "conversation/humanized/v3/segments/test.xml");
            var old = File.ReadAllText(path);
            void BlockCurrent()
            {
                var pack = new PackReader().Load(f.Module); var batch = StageCandidates(pack); var pair = V3ProposalContract.GetPairs(pack).Single();
                Throws(() => new IsolatedV3ProposalStager().Create(store, pack, batch, batch.Select(c => c.Id).ToArray(), pair, true, true)); NoV3Stages(store);
            }
            File.WriteAllText(path, old.Replace("v3.test.reply.0001", "pss.v3.t." + new string('2', 32))); BlockCurrent();
            File.WriteAllText(path, old.Replace("</templates>", "<!--" + new string('a', 1048576 - System.Text.Encoding.UTF8.GetByteCount(old) - 10) + "--></templates>")); BlockCurrent();
            var doc = XDocument.Parse(old); doc.Root!.Element("templates")!.RemoveNodes();
            for (var i = 0; i < 4095; i++) doc.Root.Element("templates")!.Add(new XElement("template", new XAttribute("id", "full." + i), new XAttribute("act", "greet.reply"), new XAttribute("band", "UNKNOWN"), new XAttribute("register", "NEUTRAL"), new XAttribute("profanity", "NONE"), new XAttribute("text", "Строка " + i)));
            doc.Save(path); BlockCurrent();
        });
        Test("PSS-008 B exact mixed and single-kind v3 pair append preserve65 source session and manifest", () =>
        {
            using var f = V3Fixture(); using var store = new WorkspaceStore(Path.Combine(f.Root, "studio"), f.Module);
            var pack = new PackReader().Load(f.Module); Equal(65, pack.Files.Count); var peers = StageCandidates(pack);
            store.SaveSession(new SessionState { Candidates = peers }); var state = File.ReadAllBytes(Path.Combine(store.Root, "session.json"));
            var pair = V3ProposalContract.GetPairs(pack).Single(p => p.Category == "test");
            foreach (var ids in new[] { peers.Select(c => c.Id).ToArray(), new[] { peers[0].Id }, new[] { peers[1].Id } })
            {
                var result = new IsolatedV3ProposalStager().Create(store, pack, peers, ids, pair, true, true);
                Equal("STAGED_V3_UNVALIDATED", result.Status); True(result.Root.Contains("v3-proposals"));
                var stage = new PackReader().Load(result.ModuleRoot);
                Equal(pack.Entries.Count + ids.Length, stage.Entries.Count);
                foreach (var stamp in pack.Files.Where(s => s.RelativePath != pair.SemanticPath && s.RelativePath != pair.ConversationPath))
                    Equal(stamp, stage.Files.Single(s => s.RelativePath == stamp.RelativePath));
                foreach (var c in peers.Where(c => ids.Contains(c.Id)))
                {
                    var xmlId = "pss.v3." + (c.Kind == "PATTERN" ? "p." : "t.") + c.Id;
                    Equal(c.Text, stage.Entries.Single(e => e.Id == xmlId).Text);
                    var doc = XDocument.Load(Path.Combine(stage.DataRoot, c.Kind == "PATTERN" ? pair.SemanticPath : pair.ConversationPath));
                    True(doc.Descendants().Single(e => (string?)e.Attribute("id") == xmlId).Attribute("override") == null);
                }
                var receipt = JsonSerializer.Deserialize<V3StageReceipt>(File.ReadAllBytes(Path.Combine(result.Root, "receipt.json")))!;
                Equal("NOT_RUN", receipt.JavaStatus); True(receipt.NotForInstallation); Equal(ids.Length, receipt.Candidates.Count);
            }
            True(pack.Files.SequenceEqual(new PackReader().Load(f.Module).Files)); True(state.SequenceEqual(File.ReadAllBytes(Path.Combine(store.Root, "session.json"))));
        });
        Test("PSS-008 B selection scope editorial UTF duplicate collision and consent reject whole batch", () =>
        {
            using var f = new Fixture(); using var store = new WorkspaceStore(Path.Combine(f.Root, "studio"), f.Module);
            var pack = new PackReader().Load(f.Module); var pair = V3ProposalContract.GetPairs(pack).Single(); var peers = StageCandidates(pack);
            void Block(List<Candidate> batch, bool selection = true, bool editorial = true, V3SegmentPair? target = null, string[]? ids = null)
            {
                Throws(() => new IsolatedV3ProposalStager().Create(store, pack, batch, ids ?? batch.Select(c => c.Id).ToArray(), target ?? pair, selection, editorial));
                NoV3Stages(store); True(pack.Files.SequenceEqual(new PackReader().Load(f.Module).Files));
            }
            Block(peers, selection: false); Block(peers, editorial: false); Block(peers, ids: []); Block(peers, ids: [peers[0].Id, peers[0].Id]);
            Block(peers, target: pair with { Category = "wrong" }); Block(peers, target: pair with { ConversationPath = "conversation/custom/my-phrases.xml" });
            foreach (var change in new Action<Candidate>[] { c => c.Status = "DRAFT", c => c.Text += "!", c => c.Gender = "FEMALE",
                c => c.Text = "Ответ {name}", c => c.Text = "<xml>", c => c.Text = "Я бафнул тебя", c => c.Text = "Я рада встрече",
                c => c.Text = new string('я', 130), c => c.Text = "Привет!!!", c => c.Act = "support.buff.request", c => c.Topic = "missing" })
            { var batch = peers.Select(c => c.Copy()).ToList(); change(batch[1]); if (batch[1].Status == "APPROVED" && batch[1].Text != peers[1].Text + "!") Seal(batch[1]); Block(batch); }
            var longPattern = peers.Select(c => c.Copy()).ToList(); longPattern[0].Text = new string('я', 161); Seal(longPattern[0]); Block(longPattern);
            var duplicate = peers[1].Copy(); duplicate.Id = new string('3', 32); duplicate.Status = "DRAFT"; Block([.. peers, duplicate], ids: peers.Select(c => c.Id).ToArray());
            Block(peers, ids: Enumerable.Range(0, 21).Select(i => i.ToString()).ToArray());
        });
        Test("PSS-008 B source schema drift cancellation and partial guards have zero output", () =>
        {
            using var f = new Fixture(); using var store = new WorkspaceStore(Path.Combine(f.Root, "studio"), f.Module);
            var pack = new PackReader().Load(f.Module); var pair = V3ProposalContract.GetPairs(pack).Single(); var peers = StageCandidates(pack);
            using var cancel = new CancellationTokenSource(); cancel.Cancel();
            Throws(() => new IsolatedV3ProposalStager().Create(store, pack, peers, peers.Select(c => c.Id).ToArray(), pair, true, true, cancel.Token)); NoV3Stages(store);
            var old = File.ReadAllText(f.Segment); File.AppendAllText(f.Segment, "\n");
            Throws(() => new IsolatedV3ProposalStager().Create(store, pack, peers, peers.Select(c => c.Id).ToArray(), pair, true, true)); NoV3Stages(store);
            File.WriteAllText(f.Segment, old.Replace("<patterns>", "<patterns unsupported='true'>")); pack = new PackReader().Load(f.Module); peers = StageCandidates(pack);
            Throws(() => new IsolatedV3ProposalStager().Create(store, pack, peers, peers.Select(c => c.Id).ToArray(), pair, true, true)); NoV3Stages(store);
            File.WriteAllText(f.Segment, "<!DOCTYPE x><humanizedV3SemanticSegment/>"); Throws(() => new PackReader().Load(f.Module));
        });
    }
    private static int Pss008Source(string[] args)
    {
        if (args.Length != 3) throw new ArgumentException("--pss-008-source <HighFive> <own ignored workspace>");
        var studio = Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "../../../../../"));
        True(PathSafety.IsWithin(args[2], Path.Combine(studio, "artifacts")) && Path.GetFileName(args[2]) == "workspace");
        var pack = new PackReader().Load(args[1]); var report = PackCoverageAnalyzer.Analyze(pack);
        using var store = new WorkspaceStore(args[2], pack.ModuleRoot);
        var pair = V3ProposalContract.GetPairs(pack).Single(p => p.Category == "social.friendly");
        var scope = pack.Entries.First(e => e.Kind == "PATTERN" && e.SourceFile == pair.SemanticPath && e.Act == "social.friendly.warmth.reply");
        var peers = StageCandidates(pack);
        peers[0].Text = "проверка студии восемь тихий вечер без действий";
        peers[1].Text = "Пусть этот спокойный разговор добавит вечеру немного тепла.";
        foreach (var c in peers) { c.Act = scope.Act; c.Topic = scope.Topic; c.Register = "CASUAL"; CandidateReview.Approve(c, pack, peers, "Synthetic PSS008 operator fixture; not model output"); }
        var stage = new IsolatedV3ProposalStager().Create(store, pack, peers, peers.Select(c => c.Id).ToArray(), pair, true, true);
        var after = new PackReader().Load(pack.ModuleRoot); True(pack.Files.SequenceEqual(after.Files));
        File.WriteAllText(Path.Combine(store.Root, "test-v3-stage-path.txt"), stage.Root);
        File.WriteAllText(Path.Combine(studio, "reports/PSS-008-source.json"), JsonSerializer.Serialize(new
        {
            pack.Fingerprint, Before = pack.Files, After = after.Files, SourceEqual = true, Segments = report.Capacities.Single(c => c.Name == "V3_SEGMENTS").Used,
            Patterns = pack.Entries.Count(e => e.Kind == "PATTERN"), Templates = pack.Entries.Count(e => e.Kind == "TEMPLATE"),
            Aliases = pack.Entries.Count(e => e.Kind == "ALIAS"), Profanity = pack.Entries.Count(e => e.Kind == "PROFANITY"),
            Pairs = V3ProposalContract.GetPairs(pack).Count, Topics = pack.Topics.Count, Acts = pack.Acts.Count,
            WarningRows = report.Rows.Count(r => r.Warning.Length > 0), report.LexicalComparisons, JavaStatus = "NOT_RUN", StageStatus = stage.Status
        }, WorkspaceStore.JsonOptions), new System.Text.UTF8Encoding(false));
        Console.WriteLine($"PASS ACTUAL_SOURCE read-only files={pack.Files.Count} SHA/bytes equal; patterns={pack.Entries.Count(e => e.Kind == "PATTERN")}; templates={pack.Entries.Count(e => e.Kind == "TEMPLATE")}; pairs={V3ProposalContract.GetPairs(pack).Count}; fingerprint={pack.Fingerprint}");
        Console.WriteLine("SYNTHETIC selected=2; stage=STAGED_V3_UNVALIDATED; Java=NOT_RUN; NOT_INSTALLED; path stored only in own workspace"); return 0;
    }
    private static int Pss008Handoff(string[] args)
    {
        if (args.Length != 3 || args[2] is not ("--synthetic-operator-review" or "--synthetic-forgery-negative")) throw new ArgumentException("--pss-008-handoff <genuine proof> --synthetic-operator-review|--synthetic-forgery-negative");
        var studio = Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "../../../../../")); var proof = PathSafety.Canonical(args[1]);
        var workspace = Path.Combine(studio, "artifacts/PSS-008/workspace"); var stage = Path.GetDirectoryName(Path.GetDirectoryName(proof))!;
        True(PathSafety.IsWithin(stage, Path.Combine(workspace, "v3-proposals")));
        using var store = new WorkspaceStore(workspace, new StudioSettings().HighFiveRoot);
        var receipt = V3ProposalContract.ReadStage(store.Root, stage); var source = new PackReader().Load(receipt.SourceModule);
        var hash = V3ReleaseHandoff.InspectProof(store, stage, proof);
        // This route is only for the two hard-coded SYNTHETIC source fixture IDs/text hashes, never a user's batch.
        True(receipt.Candidates.Count == 2 && receipt.Candidates.Single(c => c.Kind == "PATTERN").TextHash == TextRules.Hash("проверка студии восемь тихий вечер без действий")
            && receipt.Candidates.Single(c => c.Kind == "TEMPLATE").TextHash == TextRules.Hash("Пусть этот спокойный разговор добавит вечеру немного тепла."));
        if (args[2] == "--synthetic-forgery-negative")
        {
            var saved = File.ReadAllBytes(proof); var oracle = Path.GetDirectoryName(proof)!;
            var attestationPath = Path.Combine(oracle, "java-attestation.json");
            var savedAttestation = File.Exists(attestationPath) ? File.ReadAllBytes(attestationPath) : null;
            var body = System.Text.Json.Nodes.JsonNode.Parse(saved)!;
            var logs = body["Logs"]!.AsArray(); var originals = logs.ToDictionary(l => l!["RelativePath"]!.GetValue<string>(), l => File.ReadAllBytes(Path.Combine(oracle, l!["RelativePath"]!.GetValue<string>())));
            try
            {
                foreach (var log in logs)
                {
                    var name = log!["RelativePath"]!.GetValue<string>();
                    var text = name switch { "probe.txt" => body["ProbeEvidence"]!.GetValue<string>() + "\n", "ant-content.txt" => "BUILD SUCCESSFUL\n",
                        "negative.txt" or "negative-schema.txt" => "REJECTED_V3_NEGATIVE\n", "java-version.txt" => "javac 25.forged\n", "ant-version.txt" => "Apache Ant forged\n", _ => "forged compile log\n" };
                    var bytes = System.Text.Encoding.UTF8.GetBytes(text); File.WriteAllBytes(Path.Combine(oracle, name), bytes);
                    log["Sha256"] = TextRules.Hash(bytes); log["Bytes"] = bytes.Length;
                }
                body["Jdk"] = "javac 25.forged"; body["Ant"] = "Apache Ant forged";
                File.WriteAllText(proof, body.ToJsonString());
                if (savedAttestation != null)
                {
                    var attestation = System.Text.Json.Nodes.JsonNode.Parse(savedAttestation)!;
                    attestation["ProofSha256"] = TextRules.Hash(File.ReadAllBytes(proof)); // Consistent public hashes, copied old MAC.
                    File.WriteAllText(attestationPath, attestation.ToJsonString());
                }
                Throws(() => V3ReleaseHandoff.InspectProof(store, stage, proof));
                Console.WriteLine("PASS public-tools/input-metadata forgery rejected despite matching self-declared log hashes; zero release output");
            }
            finally { foreach (var log in originals) File.WriteAllBytes(Path.Combine(oracle, log.Key), log.Value); File.WriteAllBytes(proof, saved); if (savedAttestation != null) File.WriteAllBytes(attestationPath, savedAttestation); }
            return 0;
        }
        Throws(() => V3ReleaseHandoff.Prepare(store, stage, proof, hash, false));
        Throws(() => V3ReleaseHandoff.Prepare(store, stage, proof, new string('f', 64), true));
        using var cancel = new CancellationTokenSource(); cancel.Cancel(); Throws(() => V3ReleaseHandoff.Prepare(store, stage, proof, hash, true, cancel.Token));
        var result = V3ReleaseHandoff.Prepare(store, stage, proof, hash, true);
        foreach (var file in receipt.SourceFiles.Where(f => receipt.StagedFiles.Single(s => s.RelativePath == f.RelativePath) != f))
        {
            True(File.ReadAllBytes(Path.Combine(source.DataRoot, file.RelativePath)).SequenceEqual(File.ReadAllBytes(Path.Combine(result, "backup", file.RelativePath))));
            Equal(file.Sha256, TextRules.Hash(File.ReadAllBytes(Path.Combine(result, "backup", file.RelativePath))));
            Equal(receipt.StagedFiles.Single(s => s.RelativePath == file.RelativePath).Sha256, TextRules.Hash(File.ReadAllBytes(Path.Combine(result, "proposed", file.RelativePath))));
        }
        Equal(6, Directory.GetFiles(result, "*", SearchOption.AllDirectories).Length);
        True(source.Files.SequenceEqual(new PackReader().Load(receipt.SourceModule).Files));
        Console.WriteLine("PASS genuine native proof inspected; explicit SYNTHETIC operator handoff; proposed=2 backup=2 manifest=1 checklist=1; NOT_INSTALLED; source SHA unchanged");
        Console.WriteLine("PASS release negatives: consent NO, wrong reviewed proof hash, cancelled token; no additional release output"); return 0;
    }
    private static void NoV3Stages(WorkspaceStore store)
    { var root = Path.Combine(store.Root, "v3-proposals"); if (Directory.Exists(root)) Equal(0, Directory.GetDirectories(root).Length); }
    private sealed class FinalRecheckCancellation(IReadOnlyList<Candidate> peers, CancellationTokenSource cancel) : IReadOnlyList<Candidate>
    {
        private int enumerations;
        public Candidate this[int index] => peers[index];
        public int Count => peers.Count;
        public IEnumerator<Candidate> GetEnumerator() { if (++enumerations == 2) cancel.Cancel(); return peers.GetEnumerator(); }
        System.Collections.IEnumerator System.Collections.IEnumerable.GetEnumerator() => GetEnumerator();
    }
    private static Fixture V3Fixture()
    {
        var f = new Fixture(); var data = Path.Combine(f.Module, "dist/game/data/phantoms"); var manifest = XDocument.Load(f.Manifest);
        foreach (var path in new[] { "semantic/humanized/high-five-ru-humanized-semantic-v2.xml", "conversation/humanized/high-five-ru-humanized-conversation-v2.xml" })
            File.WriteAllText(Path.Combine(data, path), path.StartsWith("semantic/", StringComparison.Ordinal) ? "<humanizedSemanticPack version='2'><patterns/></humanizedSemanticPack>" : "<humanizedConversationPack version='2'><templates/></humanizedConversationPack>");
        foreach (var (path, root) in new[] { (Social, "socialTopics"), ("semantic/custom/my-ru-aliases.xml", "aliases"), ("semantic/custom/my-slang.xml", "slang"), ("conversation/custom/my-profanity.xml", "profanity"), ("conversation/custom/my-mature-dialogue.xml", "matureDialogue") })
        { Directory.CreateDirectory(Path.GetDirectoryName(Path.Combine(data, path))!); File.WriteAllText(Path.Combine(data, path), $"<{root} version='1'/>"); }
        for (var i = 0; i < 25; i++)
            foreach (var semantic in new[] { true, false })
            {
                var path = (semantic ? "semantic/" : "conversation/") + $"humanized/v3/segments/empty-{i}.xml";
                manifest.Root!.Element("segments")!.Add(new XElement("segment", new XAttribute("kind", semantic ? "SEMANTIC" : "CONVERSATION"), new XAttribute("path", path)));
                File.WriteAllText(Path.Combine(data, path), semantic ? $"<humanizedV3SemanticSegment id='empty.{i}.s' version='3' category='empty.{i}'><patterns/></humanizedV3SemanticSegment>" : $"<humanizedV3ConversationSegment id='empty.{i}.c' version='3' category='empty.{i}' mature='false'><templates/></humanizedV3ConversationSegment>");
            }
        manifest.Save(f.Manifest); return f;
    }
    private static void Pss008A()
    {
        Test("PSS-008 A declared empty topic act and template-only act remain visible", () =>
        {
            using var f = new Fixture(); var pack = new PackReader().Load(f.Module);
            var orphan = pack.Entries.First(e => e.Kind == "TEMPLATE") with { Id = "orphan", Act = "orphan.reply" };
            var empty = pack with { Topics = ["empty.topic"], Acts = ["empty.reply", "orphan.reply"], Entries = [orphan] };
            var report = PackCoverageAnalyzer.Analyze(empty);
            True(report.Rows.Any(r => r.Kind == "TOPIC" && r.Topic == "empty.topic" && r.Count == 0 && r.Warning.Contains("MISSING_PATTERN")));
            True(report.Rows.Any(r => r.Kind == "ACT" && r.Act == "empty.reply" && r.Count == 0 && r.Warning.Contains("MISSING_TEMPLATE")));
            True(report.Rows.Any(r => r.Kind == "TEMPLATE" && r.Act == "orphan.reply" && r.Topic == "" && r.Warning.Contains("ORPHAN_TEMPLATE")));
        });
        Test("PSS-008 A missing responses clean diversity provenance and no template topic", () =>
        {
            var pack = new PackSnapshot { Fingerprint = "synthetic", Entries =
            [
                new() { Kind = "PATTERN", Id = "p.a", Topic = "hello", Act = "hello.reply", Text = "привет", SourceFile = "semantic/humanized/v3/segments/a.xml", SourceLine = 3 },
                new() { Kind = "PATTERN", Id = "p.b", Topic = "rest", Act = "rest.reply", Text = "отдых", SourceFile = "semantic/humanized/base.xml" },
                new() { Kind = "TEMPLATE", Id = "t.a", Act = "hello.reply", Topic = "editorial.not.runtime", Text = "Добрый день", SourceFile = "conversation/humanized/v3/segments/a.xml" },
                new() { Kind = "TEMPLATE", Id = "t.b", Act = "hello.reply", Text = "День", Band = "FAMILIAR", Register = "CASUAL", Mature = true },
                new() { Kind = "TEMPLATE", Id = "t.c", Act = "rest.reply", Text = "Брань", Profanity = "MILD" }
            ] };
            var bytes = JsonSerializer.Serialize(pack);
            var report = PackCoverageAnalyzer.Analyze(pack);
            True(report.Rows.Any(r => r.Kind == "PATTERN" && r.Act == "rest.reply" && r.Warning.Contains("MISSING_TEMPLATE")));
            True(report.Rows.Where(r => r.Kind == "TEMPLATE").All(r => r.Topic == ""));
            Equal(1, report.Rows.Where(r => r.Kind == "TEMPLATE").Sum(r => r.Clean));
            True(report.Rows.Any(r => r.Provenance.Contains("p.a") && r.Provenance.Contains(":3")));
            True(report.Status.Contains("NOT_RUNTIME_PARITY")); Equal(bytes, JsonSerializer.Serialize(pack));
        });
        Test("PSS-008 A actual segment capacity normalized duplicates deterministic ordering", () =>
        {
            var pack = new PackSnapshot { Fingerprint = "synthetic", Files = Enumerable.Range(0, 52)
                .Select(i => new SourceFileStamp("semantic/humanized/v3/segments/" + i + ".xml", "sha", i == 0 ? 1048500 : 40)).ToList(), Entries =
            [
                new() { Kind = "TEMPLATE", Id = "t.2", Act = "hello.reply", Text = "ЕЛКА!!!", SourceFile = "v2" },
                new() { Kind = "TEMPLATE", Id = "t.1", Act = "hello.reply", Text = "Ёлка", SourceFile = "v1" },
                new() { Kind = "TEMPLATE", Id = "t.3", Act = "hello.reply", Text = "Не елка", SourceFile = "v3" }
            ] };
            var result = PackCoverageAnalyzer.Analyze(pack);
            Equal(52L, result.Capacities.Single(c => c.Name == "V3_SEGMENTS").Used);
            Equal("CAPACITY_WARNING", result.Capacities.Single(c => c.Name.EndsWith("/0.xml", StringComparison.Ordinal)).Status);
            True(result.Rows.Any(r => r.Warning == "EXACT_NORMALIZED_DUPLICATE" && r.Count == 2));
            var reverse = pack with { Files = pack.Files.AsEnumerable().Reverse().ToList(), Entries = pack.Entries.AsEnumerable().Reverse().ToList() };
            Equal(JsonSerializer.Serialize(result), JsonSerializer.Serialize(PackCoverageAnalyzer.Analyze(reverse)));
        });
        Test("PSS-008 A 5k 20k bounded lexical sample cancellation and index caps", () =>
        {
            var pack = new PackSnapshot { Fingerprint = "synthetic", Entries = Enumerable.Range(0, 25000).Select(i => new PackEntry
                { Kind = i < 5000 ? "PATTERN" : "TEMPLATE", Id = "entry." + i, Act = "hello.reply", Topic = i < 5000 ? "hello" : "", Text = "Синтетическая строка " + i }).ToList() };
            var timer = Stopwatch.StartNew(); var report = PackCoverageAnalyzer.Analyze(pack);
            Equal(5000L, report.Capacities.Single(c => c.Name == "PATTERN").Used);
            Equal(20000L, report.Capacities.Single(c => c.Name == "TEMPLATE").Used);
            True(report.Capacities.Any(c => c.Name.StartsWith("TEMPLATE_BUCKET|", StringComparison.Ordinal) && c.Status == "CAPACITY_EXCEEDED"));
            True(report.LexicalComparisons is > 0 and <= 2048); True(timer.Elapsed < TimeSpan.FromSeconds(12));
            Console.WriteLine($"SYNTHETIC_ANALYSIS entries=25000 elapsedMs={timer.ElapsedMilliseconds} lexicalComparisons={report.LexicalComparisons}");
            using var cancel = new CancellationTokenSource(); cancel.Cancel(); Throws(() => PackCoverageAnalyzer.Analyze(pack, cancel.Token));
        });
    }
}
