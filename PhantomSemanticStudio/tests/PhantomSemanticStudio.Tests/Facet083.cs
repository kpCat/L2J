using System.Text.Json;
using System.Text.Json.Nodes;
using System.Reflection;
using System.Diagnostics;
using System.Security.Cryptography;
using System.Text;
using PhantomSemanticStudio.Core;

internal static partial class Program
{
    private static void SetFacet083(Candidate c, string? facet, int? threshold)
    {
        var field = typeof(Candidate).GetProperty("Facet");
        True(field != null); field!.SetValue(c, facet);
        typeof(Candidate).GetProperty("MinIntensity")!.SetValue(c, threshold);
    }

    private static void Facet083()
    {
        Test("083 rich reviewed selection remains readable and oversized receipt never finishes", () =>
        {
            using var f = new Fixture(); var pack = new PackReader().Load(f.Module);
            using var store = new WorkspaceStore(Path.Combine(f.Root, "rich083"), f.Module);
            var peers = Enumerable.Range(1, 20).Select(i =>
            {
                var c = CandidateOf(pack, $"За каменной башней затихает ветер номер {i}.");
                c.Instruction = new string('я', 7000); c.Facet = "humor"; c.MinIntensity = 70; return c;
            }).ToList();
            foreach (var c in peers) CandidateReview.Approve(c, pack, peers, "Synthetic rich083");
            var pair = V3ProposalContract.GetPairs(pack).Single();
            var stage = new IsolatedV3ProposalStager().Create(store, pack, peers, peers.Select(c => c.Id).ToArray(), pair, true, true);
            True(new FileInfo(Path.Combine(stage.Root, "receipt.json")).Length > 262144);
            Equal(20, V3ProposalContract.ReadStage(store.Root, stage.Root).Candidates.Count);
            foreach (var c in peers) { c.Instruction = new string('я', 40000); CandidateReview.Approve(c, pack, peers, "Synthetic oversize083"); }
            Throws(() => new IsolatedV3ProposalStager().Create(store, pack, peers, peers.Select(c => c.Id).ToArray(), pair, true, true));
            Equal(1, Directory.GetDirectories(Path.Combine(store.Root, "v3-proposals")).Length);
        });
        Test("083 explicit facet changes approval and advisory while absent fields preserve old hash", () =>
        {
            using var f = new Fixture(); var pack = new PackReader().Load(f.Module);
            var c = StageCandidates(pack)[1]; var old = c.ApprovedFingerprint;
            var advisory = SemanticDuplicateScout.CandidateFingerprint(c);
            SetFacet083(c, "", null); True(CandidateReview.IsCurrent(c)); Equal(old, CandidateReview.Fingerprint(c));
            SetFacet083(c, "humor", 70); True(!CandidateReview.IsCurrent(c));
            True(advisory != SemanticDuplicateScout.CandidateFingerprint(c));
            CandidateReview.Approve(c, pack, [c], "Ручная synthetic проверка083");
            var hash = c.ApprovedFingerprint; SetFacet083(c, "humor", 71); True(hash != CandidateReview.Fingerprint(c));
            CandidateReview.Edit(c, c.Text); Equal("DRAFT", c.Status); True(c.SemanticReview == null);
        });
        Test("083 strict workspace rejects null and invalid facet combinations on save and load", () =>
        {
            using var f = new Fixture(); var pack = new PackReader().Load(f.Module);
            using var store = new WorkspaceStore(Path.Combine(f.Root, "studio083"), f.Module);
            var c = CandidateOf(pack, "Свет фонаря отражается на каменной стене.");
            foreach (var (facet, threshold) in new (string?, int?)[] { (null, null), ("Humor", 70), ("unknown", 70), (" humor", 70), ("humor", null), ("", 0), ("humor", -1), ("humor", 101) })
            {
                SetFacet083(c, facet, threshold); Throws(() => store.SaveSession(new SessionState { Candidates = [c] }));
                File.WriteAllText(Path.Combine(store.Root, "session.json"), JsonSerializer.Serialize(new SessionState { Candidates = [c] }));
                Throws(() => store.LoadSession());
            }
            foreach (var facet in new[] { "aggression", "sarcasm", "humor", "reserved", "swagger", "timidity", "romance", "roughness", "cunning", "gossip" })
            {
                SetFacet083(c, facet, 0); store.SaveSession(new SessionState { Candidates = [c] });
                Equal(facet, typeof(Candidate).GetProperty("Facet")!.GetValue(store.LoadSession().Candidates[0])!);
            }
            c.Kind = "PATTERN"; Throws(() => store.SaveSession(new SessionState { Candidates = [c] }));
        });
        Test("083 V3 append-only reviewed facets bind receipt approval and legacy stage stays v2", () =>
        {
            using var f = new Fixture(); var pack = new PackReader().Load(f.Module);
            using var store = new WorkspaceStore(Path.Combine(f.Root, "studio083"), f.Module);
            var peers = StageCandidates(pack); var pair = V3ProposalContract.GetPairs(pack).Single();
            var legacy = new IsolatedV3ProposalStager().Create(store, pack, peers, [peers[1].Id], pair, true, true);
            Equal(2, V3ProposalContract.ReadStage(store.Root, legacy.Root).Version);
            SetFacet083(peers[1], "humor", 70); CandidateReview.Approve(peers[1], pack, peers, "Synthetic083");
            var zero = CandidateOf(pack, "Ветер листает страницы забытой книги."); zero.Id = new string('4', 32);
            SetFacet083(zero, "sarcasm", 0); peers.Add(zero); CandidateReview.Approve(zero, pack, peers, "Synthetic083");
            var generic = CandidateOf(pack, "Луна освещает пустую площадь."); generic.Id = new string('5', 32);
            peers.Add(generic); CandidateReview.Approve(generic, pack, peers, "Synthetic083");
            Throws(() => new IsolatedPackStager().Create(store, pack, peers, [peers[1].Id], true, true));
            var stage = new IsolatedV3ProposalStager().Create(store, pack, peers, peers.Select(c => c.Id).ToArray(), pair, true, true);
            Equal(3, V3ProposalContract.ReadStage(store.Root, stage.Root).Version);
            var staged = new PackReader().Load(stage.ModuleRoot); Equal(70, staged.Entries.Single(e => e.Facet == "humor").MinIntensity!.Value);
            Equal(0, staged.Entries.Single(e => e.Facet == "sarcasm").MinIntensity!.Value);
            var path = Path.Combine(stage.Root, "receipt.json"); var saved = File.ReadAllBytes(path);
            foreach (var field in new[] { "Facet", "MinIntensity", "ApprovalHash" })
            {
                var body = JsonNode.Parse(saved)!; var stamp = body["Candidates"]!.AsArray().Single(c => c!["Facet"]?.GetValue<string>() == "humor")!;
                stamp[field] = field == "MinIntensity" ? JsonValue.Create(69) : JsonValue.Create(field == "Facet" ? "gossip" : new string('f', 64));
                File.WriteAllText(path, body.ToJsonString()); Throws(() => V3ProposalContract.ReadStage(store.Root, stage.Root));
            }
            File.WriteAllBytes(path, saved); V3ProposalContract.ReadStage(store.Root, stage.Root);
            True(pack.Files.SequenceEqual(new PackReader().Load(f.Module).Files));
        });
    }

    private static int Facet083Controls()
    {
        var studio = Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "../../../../../"));
        var assembly = Assembly.LoadFrom(Path.Combine(studio, "src/PhantomSemanticStudio.WinForms/bin/Release/net10.0-windows/PhantomSemanticStudio.dll"));
        var forms = Assembly.Load(assembly.GetReferencedAssemblies().Single(a => a.Name == "System.Windows.Forms"));
        var context = forms.GetType("System.Windows.Forms.WindowsFormsSynchronizationContext")!;
        context.GetProperty("AutoInstall")!.SetValue(null, false);
        SynchronizationContext.SetSynchronizationContext((SynchronizationContext)Activator.CreateInstance(context)!);
        var events = forms.GetType("System.Windows.Forms.Application")!.GetMethod("DoEvents")!;
        dynamic Control(dynamic ui, string name) => ui.Controls.Find(name, true)[0];
        void Pump(dynamic ui)
        {
            var elapsed = Stopwatch.StartNew();
            do { events.Invoke(null, null); Thread.Sleep(5); }
            while ((bool)Control(ui, "btnCancel").Enabled && elapsed.Elapsed < TimeSpan.FromSeconds(20));
            True(!Control(ui, "btnCancel").Enabled); events.Invoke(null, null);
        }
        void Respond(dynamic ui, Action action, uint choice)
        {
            using var disposable = (IDisposable)Activator.CreateInstance(forms.GetType("System.Windows.Forms.Timer")!)!;
            dynamic timer = disposable; var prompted = false;
            EventHandler handler = (_, _) =>
            {
                var popup = GetLastActivePopup((IntPtr)ui.Handle);
                if (popup == IntPtr.Zero || popup == (IntPtr)ui.Handle) return;
                timer.Stop(); prompted = true; SendMessage(popup, 0x0111, (UIntPtr)choice, IntPtr.Zero);
            };
            timer.Tick += handler; timer.Interval = 50; timer.Start(); action(); timer.Stop(); True(prompted); Pump(ui);
        }
        Test("083 actual STA facet editor save discard cancel and PATTERN guards preserve batch", () =>
        {
            using var f = new Fixture(); var pack = new PackReader().Load(f.Module); var peers = StageCandidates(pack);
            peers[1].Facet = "humor"; peers[1].MinIntensity = 70; CandidateReview.Approve(peers[1], pack, peers, "Synthetic083 STA");
            var root = Path.Combine(studio, "artifacts/083/controls-" + Guid.NewGuid().ToString("N"));
            string job;
            using (var setup = new WorkspaceStore(root, f.Module))
            {
                setup.SaveSettings(BatchSettings(f)); var state = new SessionState { Candidates = peers };
                using var http = new HttpClient(new BatchHandler());
                new BatchGenerationService(setup, state, new LmStudioClient(http)).Start(pack, BatchSettings(f), BatchParameters(20));
                job = JsonSerializer.Serialize(setup.LoadSession().BatchJob);
            }
            var previous = Environment.GetEnvironmentVariable("PSS_UI_SMOKE_WORKSPACE");
            Environment.SetEnvironmentVariable("PSS_UI_SMOKE_WORKSPACE", root);
            using var disposable = (IDisposable)Activator.CreateInstance(assembly.GetType("PhantomSemanticStudio.WinForms.MainForm", true)!)!; dynamic ui = disposable;
            disposable.GetType().GetField("settings", BindingFlags.Instance | BindingFlags.NonPublic)!.SetValue(disposable, new StudioSettings { HighFiveRoot = f.Module });
            SessionState Saved() => JsonSerializer.Deserialize<SessionState>(File.ReadAllBytes(Path.Combine(root, "session.json")))!;
            try
            {
                ui.Show(); Pump(ui); Control(ui, "btnImport").PerformClick(); Pump(ui);
                var tabs = Control(ui, "tabs"); tabs.SelectedTab = Control(ui, "tabCandidates");
                var grid = Control(ui, "gridCandidates"); grid.CurrentCell = grid.Rows[1].Cells[0]; Pump(ui);
                var facet = Control(ui, "cmbCandidateFacet"); var intensity = Control(ui, "nudMinIntensity");
                Equal(11, (int)facet.Items.Count); Equal("humor", (string)facet.SelectedValue); Equal(70m, (decimal)intensity.Value);
                Equal(0m, (decimal)intensity.Minimum); Equal(100m, (decimal)intensity.Maximum);
                True(intensity.Enabled); intensity.Value = 71m;
                True(((string)Control(ui, "txtValidation").Text).StartsWith("STALE:"));
                Respond(ui, () => tabs.SelectedTab = Control(ui, "tabSettings"), 2); // Cancel
                True(tabs.SelectedTab == Control(ui, "tabCandidates")); Equal(71m, (decimal)intensity.Value);
                True(CandidateReview.IsCurrent(Saved().Candidates[1]));
                Respond(ui, () => tabs.SelectedTab = Control(ui, "tabSettings"), 7); // Discard
                tabs.SelectedTab = Control(ui, "tabCandidates"); Equal(70m, (decimal)intensity.Value);
                intensity.Value = 71m;
                Respond(ui, () => tabs.SelectedTab = Control(ui, "tabSettings"), 6); // Save
                Equal(71, Saved().Candidates[1].MinIntensity!.Value); Equal("DRAFT", Saved().Candidates[1].Status);
                tabs.SelectedTab = Control(ui, "tabCandidates"); facet.SelectedValue = "sarcasm"; intensity.Value = 0m;
                True(((string)Control(ui, "lblFacetWarning").Text).Contains("всегда"));
                Control(ui, "btnSaveCandidate").PerformClick(); Pump(ui); Equal("sarcasm", Saved().Candidates[1].Facet);
                facet.SelectedValue = ""; True(!intensity.Enabled); Control(ui, "btnSaveCandidate").PerformClick(); Pump(ui);
                True(Saved().Candidates[1].MinIntensity == null);
                grid.CurrentCell = grid.Rows[0].Cells[0]; Pump(ui); True(!facet.Enabled && !intensity.Enabled);
                Equal(job, JsonSerializer.Serialize(Saved().BatchJob));
                True(!facet.Bounds.IntersectsWith(Control(ui, "txtValidation").Bounds));
                ui.Close();
                using var previewDisposable = (IDisposable)Activator.CreateInstance(assembly.GetType("PhantomSemanticStudio.WinForms.StageSelectionForm", true)!)!; dynamic preview = previewDisposable;
                preview.SetCandidates(peers, pack.Fingerprint); preview.ConfirmExactSelection(new[] { peers[1].Id });
                True(((string)preview.SelectedSummary).Contains("facet=humor; minIntensity=70"));
            }
            finally { Environment.SetEnvironmentVariable("PSS_UI_SMOKE_WORKSPACE", previous); }
        });
        if (failed != 0) foreach (var failure in Failures) Console.WriteLine(failure);
        Console.WriteLine($"RESULT: {passed} PASS; {failed} FAIL"); return failed == 0 ? 0 : 1;
    }

    private static int Facet083Proof(string[] args)
    {
        if (args.Length != 3 || args[2] != "--synthetic-operator-review") throw new ArgumentException("--facet083-proof <genuine083 proof> --synthetic-operator-review");
        var proof = PathSafety.Canonical(args[1]); var oracle = Path.GetDirectoryName(proof)!; var stage = Path.GetDirectoryName(oracle)!;
        var workspace = Path.GetDirectoryName(Path.GetDirectoryName(stage))!;
        True(PathSafety.IsWithin(workspace, Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "PhantomSemanticStudio/workspace")));
        var receipt = V3ProposalContract.ReadStage(workspace, stage); Equal(3, receipt.Version);
        True(receipt.Candidates.Count == 4 && receipt.Candidates.Single(c => c.Facet == "humor").TextHash == TextRules.Hash("Пусть этот спокойный разговор добавит вечеру немного тепла.")
            && receipt.Candidates.Single(c => c.Facet == "sarcasm").TextHash == TextRules.Hash("Ветер листает страницы забытой книги.")
            && receipt.Candidates.Single(c => c.Kind == "PATTERN").TextHash == TextRules.Hash("проверка студии восемь тихий вечер без действий")
            && receipt.Candidates.Single(c => c.Kind == "TEMPLATE" && c.Facet == "").TextHash == TextRules.Hash("Обсудим риски спокойно, прежде чем выбирать следующий шаг."));
        using var store = new WorkspaceStore(workspace, receipt.SourceModule);
        var hash = V3ReleaseHandoff.InspectProof(store, stage, proof);
        void Mutate(string path, Action action)
        {
            var saved = File.ReadAllBytes(path);
            try { action(); }
            finally { File.WriteAllBytes(path, saved); }
        }
        void Resign(Action<JsonNode> change)
        {
            var attestation = Path.Combine(oracle, "java-attestation.json");
            Mutate(proof, () => Mutate(attestation, () =>
            {
                var body = JsonNode.Parse(File.ReadAllBytes(proof))!; change(body); File.WriteAllText(proof, body.ToJsonString());
                var signed = JsonNode.Parse(File.ReadAllBytes(attestation))!;
                var key = File.ReadAllBytes(Path.Combine(workspace, "java-attestations", receipt.StageId + ".key"));
                try
                {
                    var sha = TextRules.Hash(File.ReadAllBytes(proof)); signed["ProofSha256"] = sha;
                    signed["Mac"] = Convert.ToHexString(HMACSHA256.HashData(key, Encoding.UTF8.GetBytes("PSS008_NATIVE_JAVA_V3\n" + receipt.StageId + "\n" + sha))).ToLowerInvariant();
                    File.WriteAllText(attestation, signed.ToJsonString()); Throws(() => V3ReleaseHandoff.InspectProof(store, stage, proof));
                }
                finally { CryptographicOperations.ZeroMemory(key); }
            }));
        }
        Test("083 genuine native receipt stage spec logs proof and HMAC tampering fail closed", () =>
        {
            foreach (var name in new[] { "expected-ids.tsv", "probe.txt", "operator.ps1", "java-attestation.json", "java-validation.json", "module/java/org/l2jmobius/gameserver/model/actor/enums/player/PlayerClass.java" })
            {
                var path = Path.Combine(oracle, name); Mutate(path, () => { File.AppendAllText(path, "\n083 tamper"); Throws(() => V3ReleaseHandoff.InspectProof(store, stage, proof)); });
            }
            var spec = Path.Combine(oracle, "expected-ids.tsv");
            foreach (var replacement in new[] { "gossip\t70", "humor\t69" }) Mutate(spec, () =>
            {
                File.WriteAllText(spec, File.ReadAllText(spec).Replace("humor\t70", replacement));
                Resign(body => body["ExpectedIdsSha256"] = TextRules.Hash(File.ReadAllBytes(spec)));
            });
            var log = Path.Combine(oracle, "probe.txt"); Mutate(log, () =>
            {
                File.WriteAllText(log, File.ReadAllText(log).Replace("threshold=CHECKED", "threshold=FORGED"));
                Resign(body => { var stamp = body["Logs"]!.AsArray().Single(l => l!["RelativePath"]!.GetValue<string>() == "probe.txt")!;
                    stamp["Sha256"] = TextRules.Hash(File.ReadAllBytes(log)); stamp["Bytes"] = new FileInfo(log).Length; });
            });
            Resign(body => { body["Version"] = 2; body["ExecutionMode"] = "ISOLATED_CATALOG_V1"; });
            var receiptPath = Path.Combine(stage, "receipt.json");
            var xmlPath = Path.Combine(stage, "module/dist/game/data/phantoms", receipt.Pair.ConversationPath);
            foreach (var threshold in new[] { false, true }) Mutate(receiptPath, () => Mutate(xmlPath, () =>
            {
                var xml = System.Xml.Linq.XDocument.Load(xmlPath, System.Xml.Linq.LoadOptions.PreserveWhitespace);
                var node = xml.Descendants("template").Single(e => (string?)e.Attribute("id") == receipt.Candidates.Single(c => c.Facet == "humor").XmlId);
                node.SetAttributeValue(threshold ? "minIntensity" : "facet", threshold ? "69" : "gossip"); xml.Save(xmlPath);
                var body = JsonNode.Parse(File.ReadAllBytes(receiptPath))!;
                var candidate = body["Candidates"]!.AsArray().Single(c => c!["Facet"]?.GetValue<string>() == "humor")!;
                if (threshold) candidate["MinIntensity"] = 69; else candidate["Facet"] = "gossip";
                var stamp = body["StagedFiles"]!.AsArray().Single(s => s!["RelativePath"]!.GetValue<string>() == receipt.Pair.ConversationPath)!;
                stamp["Sha256"] = TextRules.Hash(File.ReadAllBytes(xmlPath)); stamp["Bytes"] = new FileInfo(xmlPath).Length;
                File.WriteAllText(receiptPath, body.ToJsonString()); Throws(() => V3ProposalContract.ReadStage(workspace, stage));
            }));
            Equal(hash, V3ReleaseHandoff.InspectProof(store, stage, proof));
        });
        Test("083 genuine HMAC proof handoff requires explicit consent reviewed hash and noncancelled token", () =>
        {
            Throws(() => V3ReleaseHandoff.Prepare(store, stage, proof, hash, false));
            Throws(() => V3ReleaseHandoff.Prepare(store, stage, proof, new string('f', 64), true));
            using var cancel = new CancellationTokenSource(); cancel.Cancel(); Throws(() => V3ReleaseHandoff.Prepare(store, stage, proof, hash, true, cancel.Token));
            var release = V3ReleaseHandoff.Prepare(store, stage, proof, hash, true);
            foreach (var stamp in receipt.SourceFiles.Where(f => receipt.StagedFiles.Single(s => s.RelativePath == f.RelativePath) != f))
            {
                Equal(stamp.Sha256, TextRules.Hash(File.ReadAllBytes(Path.Combine(release, "backup", stamp.RelativePath))));
                Equal(receipt.StagedFiles.Single(s => s.RelativePath == stamp.RelativePath).Sha256, TextRules.Hash(File.ReadAllBytes(Path.Combine(release, "proposed", stamp.RelativePath))));
            }
            Equal(6, Directory.GetFiles(release, "*", SearchOption.AllDirectories).Length);
            Console.WriteLine("OFFLINE_HANDOFF_NOT_INSTALLED " + release);
        });
        Console.WriteLine($"RESULT: {passed} PASS; {failed} FAIL"); return failed == 0 ? 0 : 1;
    }

    // Owner-authorized bounded live QA. Observes real transport; generation and acceptance use production clients.
    private sealed class Live083Observer : DelegatingHandler
    {
        public List<JsonNode> Exchanges { get; } = [];
        public Live083Observer() : base(new HttpClientHandler { AllowAutoRedirect = false, UseProxy = false }) { }
        protected override async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken token)
        {
            True(request.RequestUri!.Host == "127.0.0.1" && request.RequestUri.Port == 1234);
            var record = new JsonObject { ["Method"] = request.Method.Method, ["Path"] = request.RequestUri.AbsolutePath };
            if (request.Content != null) record["Request"] = JsonNode.Parse(await request.Content.ReadAsStringAsync(token));
            Exchanges.Add(record); var clock = Stopwatch.StartNew();
            try
            {
                var response = await base.SendAsync(request, token);
                await response.Content.LoadIntoBufferAsync(1048576, token);
                record["HttpStatus"] = (int)response.StatusCode;
                var bytes = await response.Content.ReadAsByteArrayAsync(token);
                try { record["Response"] = JsonNode.Parse(bytes); }
                catch (JsonException) { record["ResponseJson"] = "INVALID"; }
                return response;
            }
            catch (Exception error) { record["FailureType"] = error.GetType().Name; throw; }
            finally { record["ElapsedMs"] = clock.ElapsedMilliseconds; }
        }
    }

    private static async Task<int> Facet083Live(string[] args)
    {
        if (args.Length != 3) throw new ArgumentException("--facet083-live <HighFive> <new private workspace>");
        var root = PathSafety.Canonical(args[2]);
        True(!Directory.Exists(root) && PathSafety.IsWithin(root, Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "PhantomSemanticStudio/workspace")));
        var pack = new PackReader().Load(args[1]);
        using var store = new WorkspaceStore(root, pack.ModuleRoot);
        var settings = new StudioSettings { HighFiveRoot = pack.ModuleRoot, Endpoint = "http://127.0.0.1:1234/v1",
            ModelId = "gemma-4-26b-a4b-it-ultra-uncensored-heretic", Temperature = 0.65, MaxTokens = 4096, TimeoutSeconds = 60 };
        using var observer = new Live083Observer(); using var http = new HttpClient(observer) { Timeout = Timeout.InfiniteTimeSpan };
        var client = new LmStudioClient(http); var session = new SessionState();
        var engine = new BatchGenerationService(store, session, client);
        var runs = new List<object>(); string modelCheck = "NOT_RUN";
        void Save() => File.WriteAllText(Path.Combine(root, "LIVE_GEMMA_QUALITY_QA.json"), JsonSerializer.Serialize(new
        {
            settings, ModelCheck = modelCheck, Context = 16384, Thinking = "OWNER_REPORTED_ENABLED_UNCHANGED",
            SourceFingerprint = pack.Fingerprint, Runs = runs, Exchanges = observer.Exchanges,
            Drafts = session.Candidates, Approval = "NONE", NativeTagging = "NONE", Installation = "NONE"
        }, WorkspaceStore.JsonOptions), new UTF8Encoding(false));
        try
        {
            modelCheck = (await client.CheckModelAsync(settings, "", CancellationToken.None)).ToString(); Save();
            Console.WriteLine(modelCheck);
            if (!modelCheck.StartsWith("CHECKED_MODEL_LIST:", StringComparison.Ordinal)) return 2;
            var scenarios = new[]
            {
                ("A-baseline", "lineage.farming.spot", "direct", "UNKNOWN", new EditorialFacets { Humor = 80, Sarcasm = 65, Swagger = 40 }),
                ("B-instruction", "lineage.farming.spot", "direct", "UNKNOWN", new EditorialFacets { Humor = 80, Sarcasm = 65, Swagger = 40 }),
                ("PvP-risk", "lineage.pvp.risk", "calm", "FAMILIAR", new EditorialFacets { Reserved = 80, Timidity = 60, Cunning = 40 }),
                ("PvP-tense", "lineage.pvp.respect", "direct", "TENSE", new EditorialFacets { Aggression = 75, Roughness = 65, Swagger = 70 }),
                ("Friendly", "greeting", "open", "FAMILIAR", new EditorialFacets { Humor = 70, Gossip = 60, Romance = 25 }),
                ("Group-help", "lineage.party.coordination", "empathetic", "FAMILIAR", new EditorialFacets { Reserved = 55, Cunning = 60, Timidity = 25 }),
                ("Trade", "lineage.trade.bargain", "open", "NEUTRAL", new EditorialFacets { Cunning = 85, Gossip = 70, Sarcasm = 35 }),
                ("Defeat", "lineage.pvp.respect", "empathetic", "FAMILIAR", new EditorialFacets { Humor = 45, Romance = 30, Reserved = 60 })
            };
            var total = Stopwatch.StartNew(); var consecutiveFailures = 0;
            foreach (var (name, topic, voice, band, facets) in scenarios)
            {
                if (total.Elapsed > TimeSpan.FromMinutes(6) || consecutiveFailures >= 2) break;
                var scope = pack.Entries.FirstOrDefault(e => e.Kind == "PATTERN" && e.Topic == topic && !TextRules.IsFunctionalAct(e.Act));
                if (scope == null) { runs.Add(new { Scenario = name, Status = "MISSING_EXISTING_SCOPE" }); continue; }
                var instruction = "Предложи четыре разные короткие русские разговорные реплики фантома по указанному намерению. Соблюдай отношения и характер; без выполненных действий и вымышленных личных фактов.";
                if (name != "A-baseline") instruction += " Каждая реплика самостоятельная, 5–18 слов. Не начинай все ответы одинаково. Не объясняй правила и не давай списков советов. Характер выражай интонацией и выбором слов, без оскорблений, мата и adult. Не утверждай, что уже что-то куплено, убито, выдано или получено. Для ANY избегай родовых форм прошедшего времени. reason — одна короткая редакционная причина.";
                var parameters = new BatchGenerationParameters { Topic = scope.Topic, Act = scope.Act, Voice = voice, Band = band,
                    Register = "CASUAL", Gender = "ANY", Mode = GenerationMode.TEMPLATE, TargetCount = 4, Instruction = instruction, Facets = facets };
                var before = session.Candidates.Count; engine.Start(pack, settings, parameters, session.BatchJob != null);
                var clock = Stopwatch.StartNew(); var job = await engine.RunAsync(pack, settings, parameters, "");
                True(session.Candidates.All(c => c.Status == "DRAFT" && c.Facet == "" && c.MinIntensity == null && c.ApprovedFingerprint == ""));
                runs.Add(new { Scenario = name, parameters, job.Status, job.Attempted, job.Accepted, job.Rejected, job.Deduplicated, job.RequestCount,
                    job.LastDiagnostic, ElapsedMs = clock.ElapsedMilliseconds, CandidateIds = session.Candidates.Skip(before).Select(c => c.Id).ToArray() });
                consecutiveFailures = job.Accepted == 0 ? consecutiveFailures + 1 : 0;
                Save(); Console.WriteLine($"LIVE083 {name}: {job.Status}; attempted={job.Attempted} accepted={job.Accepted} rejected={job.Rejected} duplicates={job.Deduplicated}; ms={clock.ElapsedMilliseconds}; {job.LastDiagnostic}");
            }
            True(pack.Files.SequenceEqual(new PackReader().Load(pack.ModuleRoot).Files));
            Console.WriteLine("LIVE083 evidence=" + Path.Combine(root, "LIVE_GEMMA_QUALITY_QA.json"));
            return session.Candidates.Count > 0 ? 0 : 2;
        }
        finally { Save(); }
    }
}
