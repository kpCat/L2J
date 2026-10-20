using System.Net;
using System.Text;
using System.Text.Json;
using PhantomSemanticStudio.Core;

internal static partial class Program
{
    private static void Pss005Offline()
    {
        Test("PSS-005 offline kind cross-act exact provenance and source-peer namespaces", () =>
        {
            var c = ScoutCandidate();
            var pack = ScoutPack([
                ScoutEntry("same", "ПРИВЕТ!!!", "other.reply"),
                ScoutEntry("wrong", "Привет!", kind: "PATTERN"),
                ScoutEntry("close", "Привет, друг!"), ScoutEntry("far", "Зимние дороги")]);
            var peer = c.Copy(); peer.Id = "same";
            var rejected = c.Copy(); rejected.Id = "rejected"; rejected.Status = "REJECTED";
            var before = JsonSerializer.Serialize(new { c, pack, peer, rejected });
            var s = SemanticDuplicateScout.Search(pack, c, [c, peer, rejected]);
            Equal(4, s.TotalConsidered); Equal(3, s.Matches.Count);
            Equal("PEER|same,SOURCE|TEMPLATE|same,SOURCE|TEMPLATE|close", string.Join(",", s.Matches.Select(m => m.RefKey)));
            True(s.Matches.All(m => m.Kind == "TEMPLATE")); Equal("fixture.xml", s.Matches[1].SourceFile);
            Equal(7, s.Matches[1].SourceLine); Equal(TextRules.Hash("ПРИВЕТ!!!"), s.Matches[1].TextHash);
            Equal("COVERAGE_LIMITED", s.Coverage);
            Equal(before, JsonSerializer.Serialize(new { c, pack, peer, rejected }));
        });
        Test("PSS-005 stable shortlist bounded 5k patterns 20k templates and cancellation", () =>
        {
            var c = ScoutCandidate();
            var entries = Enumerable.Range(0, 20000).Select(i => ScoutEntry(i.ToString("D5"), "Привет, друг " + i))
                .Concat(Enumerable.Range(0, 5000).Select(i => ScoutEntry("p" + i, "Привет!", kind: "PATTERN"))).ToList();
            var pack = ScoutPack(entries); var s = SemanticDuplicateScout.Search(pack, c, []);
            Equal(20000, s.TotalConsidered); Equal(12, s.Matches.Count);
            var reverse = SemanticDuplicateScout.Search(pack with { Entries = entries.AsEnumerable().Reverse().ToList() }, c, []);
            Equal(s.ShortlistFingerprint, reverse.ShortlistFingerprint); Equal(s.PeersFingerprint, reverse.PeersFingerprint);
            True(s.Matches.Select(m => m.RefKey).SequenceEqual(reverse.Matches.Select(m => m.RefKey)));
            Throws(() => SemanticDuplicateScout.Search(pack, c, [], 13));
            using var cancel = new CancellationTokenSource(); cancel.Cancel();
            try { SemanticDuplicateScout.Search(pack, c, [], cancellationToken: cancel.Token); throw new AssertionFailure("Cancelled scout ran"); }
            catch (OperationCanceledException) { }
        });
        Test("PSS-005 synonym and empty lexical results never prove semantic coverage", () =>
        {
            var c = ScoutCandidate(); c.Text = "Как твои дела?";
            foreach (var pack in new[] { ScoutPack([ScoutEntry("synonym", "Как поживаешь?")]), ScoutPack([ScoutEntry("zero", "Зимний ветер")]) })
            {
                var s = SemanticDuplicateScout.Search(pack, c, []);
                Equal(1, s.TotalConsidered); Equal("COVERAGE_LIMITED", s.Coverage);
                True(CandidateValidator.Validate(c, pack, []).Any(i => i.Code == "SEMANTIC_NOT_CHECKED"));
            }
        });
    }
    private static async Task Pss005Http()
    {
        var c = ScoutCandidate(); var pack = ScoutPack([ScoutEntry("one", "Привет, друг!"), ScoutEntry("two", "Привет, товарищ!")]);
        var shortlist = SemanticDuplicateScout.Search(pack, c, []);
        var settings = new StudioSettings { ModelId = "exact-semantic-model" };
        var valid = SemanticBody(shortlist);
        await TestAsync("PSS-005 one semantic POST exact schema model temperature and advisory only", async () =>
        {
            var before = JsonSerializer.Serialize(c); using var handler = new RecordingHandler(SemanticEnvelope(valid));
            using var http = new HttpClient(handler);
            var evidence = await new LmStudioClient(http).ReviewSemanticAsync(settings, "", c, shortlist, CancellationToken.None);
            Equal(1, handler.Calls); Equal(2, evidence.Verdicts.Count); Equal("MODEL_ADVISORY_NOT_VERIFIED", evidence.Status);
            Equal("COVERAGE_LIMITED", evidence.Coverage); Equal(before, JsonSerializer.Serialize(c));
            using var payload = JsonDocument.Parse(handler.Payload!); var root = payload.RootElement;
            Equal("exact-semantic-model", root.GetProperty("model").GetString()); True(root.GetProperty("temperature").GetDouble() <= 0.2);
            Equal(0.7, settings.Temperature); True(!root.GetProperty("stream").GetBoolean());
            True(!root.TryGetProperty("tools", out _) && !root.TryGetProperty("functions", out _));
            Equal("json_schema", root.GetProperty("response_format").GetProperty("type").GetString());
            using var user = JsonDocument.Parse(root.GetProperty("messages")[1].GetProperty("content").GetString()!);
            Equal(2, user.RootElement.GetProperty("References").GetArrayLength());
            True(user.RootElement.GetProperty("References").EnumerateArray().Select(x => x.GetProperty("RefKey").GetString()).SequenceEqual(shortlist.Matches.Select(x => x.RefKey)));
        });
        Test("PSS-005 strict verdict set fields labels lengths and JSON failures", () =>
        {
            Equal(2, LmStudioClient.ParseSemanticVerdicts(valid, shortlist).Count);
            foreach (var bad in new[] { "", "null", "```json\n" + valid + "\n```", valid[..^1],
                valid.Replace("\"verdicts\":", "\"verdicts\":[],\"verdicts\":"),
                valid.Replace("\"verdicts\":", "\"extra\":1,\"verdicts\":"),
                valid.Replace("\"relation\":", "\"extra\":1,\"relation\":"),
                valid.Replace("SAME_MEANING", "UNKNOWN"), valid.Replace("Краткая причина", ""),
                valid.Replace("Краткая причина", new string('x', 241)),
                valid.Replace(shortlist.Matches[0].RefKey, "UNKNOWN"),
                valid.Replace(shortlist.Matches[1].RefKey, shortlist.Matches[0].RefKey),
                "{\"verdicts\":[]}", SemanticBody(shortlist with { Matches = shortlist.Matches.Take(1).ToArray() }),
                new string('x', 1024 * 1024 + 1) })
                Throws(() => LmStudioClient.ParseSemanticVerdicts(bad, shortlist));
        });
        await TestAsync("PSS-005 rejects tools refusal truncation multi-choice and malformed envelopes", async () =>
        {
            var good = SemanticEnvelope(valid);
            foreach (var body in new[] { good.Replace("\"stop\"", "\"length\""), good.Replace("\"message\":{", "\"message\":{\"tool_calls\":[],"),
                good.Replace("\"message\":{", "\"message\":{\"function_call\":{},"), good.Replace("\"message\":{", "\"message\":{\"refusal\":\"no\","),
                "{\"choices\":[]}", "{\"choices\":[{},{}]}", "{\"choices\":[],\"choices\":[]}", SemanticEnvelope("{}"), new string('x', 1024 * 1024 + 1) })
            {
                using var handler = new RecordingHandler(body); using var http = new HttpClient(handler);
                await ThrowsAsync(() => new LmStudioClient(http).ReviewSemanticAsync(settings, "", c, shortlist, CancellationToken.None));
                Equal(1, handler.Calls); True(c.SemanticReview == null);
            }
        });
        await TestAsync("PSS-005 HTTP failure codes sanitized no retries and no evidence", async () =>
        {
            const string secret = "semantic-private-token-prompt-body";
            foreach (var (status, code) in new[] { (401, LmDiagnosticCode.AUTH), (403, LmDiagnosticCode.AUTH), (400, LmDiagnosticCode.SCHEMA_REJECTED), (500, LmDiagnosticCode.HTTP_ERROR), (302, LmDiagnosticCode.HTTP_ERROR) })
            {
                using var handler = new RecordingHandler(secret, (HttpStatusCode)status); using var http = new HttpClient(handler);
                try { await new LmStudioClient(http).ReviewSemanticAsync(settings, secret, c, shortlist, CancellationToken.None); throw new AssertionFailure("HTTP failure accepted"); }
                catch (LmStudioException e) { Equal(code, e.Diagnostic.Code); True(!e.ToString().Contains(secret)); }
                Equal(1, handler.Calls); True(c.SemanticReview == null);
            }
            foreach (var (error, code) in new (Exception, LmDiagnosticCode)[] {
                (new HttpRequestException(secret, new System.Net.Sockets.SocketException(10061)), LmDiagnosticCode.SERVER_OFFLINE),
                (new TaskCanceledException(secret), LmDiagnosticCode.TIMEOUT) })
            {
                using var http = new HttpClient(new FaultHandler(error));
                try { await new LmStudioClient(http).ReviewSemanticAsync(settings, secret, c, shortlist, CancellationToken.None); throw new AssertionFailure("Failure accepted"); }
                catch (LmStudioException e) { Equal(code, e.Diagnostic.Code); True(!e.ToString().Contains(secret)); }
            }
            using var cancel = new CancellationTokenSource(); cancel.Cancel(); using var cancelled = new HttpClient(new RecordingHandler(SemanticEnvelope(valid)));
            try { await new LmStudioClient(cancelled).ReviewSemanticAsync(settings, "", c, shortlist, cancel.Token); throw new AssertionFailure("Cancelled result accepted"); }
            catch (LmStudioException e) { Equal(LmDiagnosticCode.CANCELLED, e.Diagnostic.Code); }
        });
    }
    private static async Task Pss005Persistence()
    {
        using var f = new Fixture(); var pack = new PackReader().Load(f.Module);
        using var store = new WorkspaceStore(Path.Combine(f.Root, "semantic-studio"), f.Module);
        var c = CandidateOf(pack, "Привет, товарищ!"); CandidateReview.Approve(c, pack, [], "Проверено вручную");
        var s = SemanticDuplicateScout.Search(pack, c, [c]);
        using var http = new HttpClient(new RecordingHandler(SemanticEnvelope(SemanticBody(s))));
        var evidence = await new LmStudioClient(http).ReviewSemanticAsync(new StudioSettings { HighFiveRoot = f.Module }, "", c, s, CancellationToken.None);
        var path = Path.Combine(store.Root, "session.json");
        Test("PSS-005 legacy session approval and atomic advisory round trip", () =>
        {
            File.WriteAllText(path, JsonSerializer.Serialize(new SessionState { Candidates = [c] }, new JsonSerializerOptions(WorkspaceStore.JsonOptions)
                { DefaultIgnoreCondition = System.Text.Json.Serialization.JsonIgnoreCondition.WhenWritingNull }));
            var old = store.LoadSession().Candidates.Single(); True(old.SemanticReview == null); True(CandidateReview.IsCurrent(old));
            var hash = old.ApprovedFingerprint;
            var updated = SemanticDuplicateScout.WithCurrentEvidence(old, pack, [old], evidence);
            True(old.SemanticReview == null); Equal(hash, updated.ApprovedFingerprint); True(CandidateReview.IsCurrent(updated));
            store.SaveSession(new SessionState { Candidates = [updated] });
            var loaded = store.LoadSession().Candidates.Single(); True(CandidateReview.IsCurrent(loaded));
            True(SemanticDuplicateScout.IsEvidenceCurrent(loaded, pack, [loaded], loaded.SemanticReview!));
            Equal("MODEL_ADVISORY_NOT_VERIFIED", loaded.SemanticReview!.Status);
            True(CandidateValidator.Validate(loaded, pack, [loaded]).Any(i => i.Code == "SEMANTIC_NOT_CHECKED"));
        });
        Test("PSS-005 evidence bounds reject corrupted state preserving old bytes", () =>
        {
            store.SaveSession(new SessionState { Candidates = [c] }); var bytes = File.ReadAllBytes(path);
            foreach (var bad in new[] { evidence with { Status = "PASS" }, evidence with { Coverage = "FULL" },
                evidence with { ModelId = new string('x', 257) }, evidence with { CandidateFingerprint = "bad" },
                evidence with { Verdicts = Enumerable.Repeat(evidence.Verdicts[0], 13).ToArray() },
                evidence with { Verdicts = [evidence.Verdicts[0] with { Reason = new string('x', 241) }] },
                evidence with { Verdicts = [evidence.Verdicts[0] with { Relation = "APPROVED" }] },
                evidence with { Verdicts = [evidence.Verdicts[0] with { ReferencedTextHash = "bad" }] },
                evidence with { Verdicts = null! } })
            {
                var badCandidate = c.Copy(); badCandidate.SemanticReview = bad;
                Throws(() => store.SaveSession(new SessionState { Candidates = [badCandidate] }));
                True(bytes.SequenceEqual(File.ReadAllBytes(path)));
            }
            var invalid = c.Copy(); invalid.SemanticReview = evidence with { Status = "PASS" };
            try { File.WriteAllText(path, JsonSerializer.Serialize(new SessionState { Candidates = [invalid] })); Throws(() => store.LoadSession()); }
            finally { File.WriteAllBytes(path, bytes); }
        });
        Test("PSS-005 candidate source stamp peer and referenced hash drift stale without approval changes", () =>
        {
            True(SemanticDuplicateScout.IsEvidenceCurrent(c, pack, [c], evidence));
            foreach (var mutate in new Action<Candidate>[] { x => x.Text += "!", x => x.Act = "other.reply", x => x.Kind = "PATTERN", x => x.Band = "TRUSTED", x => x.Register = "CASUAL", x => x.Gender = "FEMALE", x => x.SourceFingerprint = "changed" })
            {
                var changed = c.Copy(); mutate(changed); True(!SemanticDuplicateScout.IsEvidenceCurrent(changed, pack, [changed], evidence));
            }
            True(!SemanticDuplicateScout.IsEvidenceCurrent(c, pack with { Fingerprint = "changed" }, [c], evidence));
            True(!SemanticDuplicateScout.IsEvidenceCurrent(c, pack with { Files = [pack.Files[0] with { Sha256 = TextRules.Hash("changed") }] }, [c], evidence));
            var peer = c.Copy(); peer.Id = "new-peer"; True(!SemanticDuplicateScout.IsEvidenceCurrent(c, pack, [c, peer], evidence));
            var changedEntries = pack.Entries.Select(e => e with { Text = e.Text + "!" }).ToList();
            True(!SemanticDuplicateScout.IsEvidenceCurrent(c, pack with { Entries = changedEntries }, [c], evidence));
            True(!SemanticDuplicateScout.IsEvidenceCurrent(c, pack, [c], evidence with { Verdicts = evidence.Verdicts.Select(v => v with { ReferencedTextHash = TextRules.Hash("forged") }).ToArray() }));
            var edited = c.Copy(); edited.SemanticReview = evidence; CandidateReview.Edit(edited, "Новая редакция."); True(edited.SemanticReview == null);
        });
        await TestAsync("PSS-005 physical source race and cancellation reject before atomic persistence", async () =>
        {
            store.SaveSession(new SessionState { Candidates = [c] }); var bytes = File.ReadAllBytes(path);
            var original = File.ReadAllBytes(f.Segment);
            try
            {
                using var handler = new SemanticRaceHandler(SemanticEnvelope(SemanticBody(s)), () => File.AppendAllText(f.Segment, "\n<!-- drift -->"));
                using var raceHttp = new HttpClient(handler);
                var result = await new LmStudioClient(raceHttp).ReviewSemanticAsync(new StudioSettings { HighFiveRoot = f.Module }, "", c, s, CancellationToken.None);
                var fresh = new PackReader().Load(f.Module);
                Throws(() => SemanticDuplicateScout.WithCurrentEvidence(c, fresh, [c], result));
                using var cancel = new CancellationTokenSource(); cancel.Cancel();
                Throws(() => SemanticDuplicateScout.WithCurrentEvidence(c, pack, [c], result, cancel.Token));
                var peer = c.Copy(); peer.Id = "race-peer"; Throws(() => SemanticDuplicateScout.WithCurrentEvidence(c, pack, [c, peer], result));
                var changed = c.Copy(); changed.Text += "!"; Throws(() => SemanticDuplicateScout.WithCurrentEvidence(changed, pack, [changed], result));
                True(bytes.SequenceEqual(File.ReadAllBytes(path))); True(c.SemanticReview == null); True(CandidateReview.IsCurrent(c));
            }
            finally { File.WriteAllBytes(f.Segment, original); }
        });
    }
    private sealed class SemanticRaceHandler(string body, Action beforeResponse) : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            cancellationToken.ThrowIfCancellationRequested(); beforeResponse();
            return Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK) { Content = new StringContent(body, Encoding.UTF8, "application/json") });
        }
    }
    private static int Pss005Controls()
    {
        var studio = Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "../../../../../"));
        var assembly = System.Reflection.Assembly.LoadFrom(Path.Combine(studio, "src/PhantomSemanticStudio.WinForms/bin/Release/net10.0-windows/PhantomSemanticStudio.dll"));
        var type = assembly.GetType("PhantomSemanticStudio.WinForms.MainForm", throwOnError: true)!;
        var application = assembly.GetReferencedAssemblies().Single(a => a.Name == "System.Windows.Forms");
        var doEvents = System.Reflection.Assembly.Load(application).GetType("System.Windows.Forms.Application")!.GetMethod("DoEvents")!;
        var errorMode = doEvents.DeclaringType!.GetMethods().Single(m => m.Name == "SetUnhandledExceptionMode" && m.GetParameters().Length == 1);
        errorMode.Invoke(null, [Enum.Parse(errorMode.GetParameters()[0].ParameterType, "ThrowException")]);
        dynamic Control(dynamic ui, string name) => ui.Controls.Find(name, true)[0];
        void Pump(dynamic ui)
        {
            var timer = System.Diagnostics.Stopwatch.StartNew();
            do { doEvents.Invoke(null, null); Thread.Sleep(10); }
            while ((bool)Control(ui, "btnCancel").Enabled && timer.Elapsed < TimeSpan.FromSeconds(15));
            True(!Control(ui, "btnCancel").Enabled); doEvents.Invoke(null, null);
        }
        using var f = new Fixture(); var pack = new PackReader().Load(f.Module);
        var c = CandidateOf(pack, "Привет, товарищ!"); CandidateReview.Approve(c, pack, [], "Прочитано вручную");
        var s = SemanticDuplicateScout.Search(pack, c, [c]);
        using (var http = new HttpClient(new RecordingHandler(SemanticEnvelope(SemanticBody(s)))))
            c.SemanticReview = new LmStudioClient(http).ReviewSemanticAsync(new StudioSettings { HighFiveRoot = f.Module }, "", c, s, CancellationToken.None).GetAwaiter().GetResult();
        var root = Path.Combine(studio, "artifacts/PSS-005/controls-" + Guid.NewGuid().ToString("N"));
        using (var setup = new WorkspaceStore(root, f.Module))
        {
            setup.SaveSettings(new StudioSettings { HighFiveRoot = f.Module, Endpoint = "http://127.0.0.1:1/v1" });
            setup.SaveSession(new SessionState { Candidates = [c] });
        }
        var previous = Environment.GetEnvironmentVariable("PSS_UI_SMOKE_WORKSPACE");
        Environment.SetEnvironmentVariable("PSS_UI_SMOKE_WORKSPACE", root);
        using var form = (IDisposable)Activator.CreateInstance(type)!; dynamic ui = form;
        try
        {
            ui.Show(); Pump(ui); Control(ui, "btnImport").PerformClick(); Pump(ui);
            Control(ui, "tabs").SelectedTab = Control(ui, "tabCandidates");
            Test("PSS-005 actual controls semantic buttons nonoverlap at minimum and resized layout", () =>
            {
                foreach (var size in new[] { (object)ui.MinimumSize, (object)ui.Size })
                {
                    ui.Size = (dynamic)size; doEvents.Invoke(null, null);
                    dynamic left = Control(ui, "btnFindSimilar"); dynamic right = Control(ui, "btnSemanticReview"); dynamic panel = Control(ui, "txtValidation");
                    if (left.Bounds.IntersectsWith(right.Bounds) || panel.Bounds.IntersectsWith(left.Bounds) || panel.Bounds.IntersectsWith(right.Bounds)
                        || !left.Parent.ClientRectangle.Contains(left.Bounds) || !right.Parent.ClientRectangle.Contains(right.Bounds))
                        throw new AssertionFailure($"Semantic layout: left={left.Bounds}; right={right.Bounds}; validation={panel.Bounds}; parent={left.Parent.ClientRectangle}; form={ui.Size}");
                    Equal(6, (int)Control(ui, "tabs").TabPages.Count); True(panel.ReadOnly);
                }
            });
            Test("PSS-005 actual offline action saved advisory stale editor and edit clears approval", () =>
            {
                var sessionPath = Path.Combine(root, "session.json"); var before = File.ReadAllBytes(sessionPath);
                Control(ui, "btnFindSimilar").PerformClick(); Pump(ui);
                var report = (string)Control(ui, "txtValidation").Text;
                True(report.Contains("SOURCE|TEMPLATE|greet.base") && report.Contains("Привет!"));
                True(report.Contains("COVERAGE_LIMITED") && report.Contains("MODEL_ADVISORY_NOT_VERIFIED") && report.Contains("SAME_MEANING"));
                True(before.SequenceEqual(File.ReadAllBytes(sessionPath))); True(pack.Files.SequenceEqual(new PackReader().Load(f.Module).Files));
                Control(ui, "txtCandidateText").Text += " Изменение.";
                True(((string)Control(ui, "txtValidation").Text).StartsWith("STALE:"));
                Control(ui, "btnSaveCandidate").PerformClick(); Pump(ui);
                var saved = JsonSerializer.Deserialize<SessionState>(File.ReadAllBytes(sessionPath), WorkspaceStore.JsonOptions)!.Candidates.Single();
                True(saved.SemanticReview == null); Equal("DRAFT", saved.Status); Equal("", saved.ApprovedFingerprint);
                True(pack.Files.SequenceEqual(new PackReader().Load(f.Module).Files));
            });
            ui.Close();
            Test("PSS-005 actual saved report source drift and invalid UTF8 select as STALE without crash", () =>
            {
                var other = CandidateOf(pack, "Искусственная другая реплика.");
                var all = new List<Candidate> { c, other };
                var selection = SemanticDuplicateScout.Search(pack, c, all);
                using (var fake = new HttpClient(new RecordingHandler(SemanticEnvelope(SemanticBody(selection)))))
                    c.SemanticReview = new LmStudioClient(fake).ReviewSemanticAsync(new StudioSettings { HighFiveRoot = f.Module }, "", c, selection, CancellationToken.None).GetAwaiter().GetResult();
                using (var reset = new WorkspaceStore(root, f.Module)) reset.SaveSession(new SessionState { Candidates = all });
                using var second = (IDisposable)Activator.CreateInstance(type)!; dynamic next = second;
                var bytes = File.ReadAllBytes(f.Segment);
                try
                {
                    next.Show(); Pump(next); Control(next, "btnImport").PerformClick(); Pump(next);
                    Control(next, "tabs").SelectedTab = Control(next, "tabCandidates");
                    dynamic grid = Control(next, "gridCandidates"); grid.CurrentCell = grid.Rows[1].Cells[0]; Pump(next);
                    File.AppendAllText(f.Segment, "\n<!-- external -->");
                    grid.CurrentCell = grid.Rows[0].Cells[0]; Pump(next);
                    True(((string)Control(next, "txtValidation").Text).Contains("STALE:"));
                    grid.CurrentCell = grid.Rows[1].Cells[0]; Pump(next);
                    File.WriteAllBytes(f.Segment, [0xff, 0x00]);
                    grid.CurrentCell = grid.Rows[0].Cells[0]; Pump(next);
                    True(((string)Control(next, "txtValidation").Text).StartsWith("STALE:"));
                    using var saved = JsonDocument.Parse(File.ReadAllBytes(Path.Combine(root, "session.json")));
                    Equal("APPROVED", saved.RootElement.GetProperty("Candidates")[0].GetProperty("Status").GetString());
                    Equal(c.ApprovedFingerprint, saved.RootElement.GetProperty("Candidates")[0].GetProperty("ApprovedFingerprint").GetString());
                }
                finally { File.WriteAllBytes(f.Segment, bytes); next.Close(); }
            });
        }
        finally { Environment.SetEnvironmentVariable("PSS_UI_SMOKE_WORKSPACE", previous); }
        Console.WriteLine($"CONTROL_CONTRACT_RESULT: {passed} PASS; {failed} FAIL; interactive UI/DPI/VS NOT_TESTED");
        return failed == 0 ? 0 : 1;
    }
    // Explicit operator route only: invoke once after checking an already-running local server.
    // Synthetic quoted phrases only; no raw model output in the console/report.
    private static async Task<int> Pss005Live()
    {
        using var f = new Fixture(); var pack = new PackReader().Load(f.Module);
        var c = CandidateOf(pack, "Как проходит твой вечер?");
        var shortlist = SemanticDuplicateScout.Search(pack, c, [c], 2);
        var studio = Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "../../../../../"));
        var root = Path.Combine(studio, "artifacts/PSS-005/live-" + Guid.NewGuid().ToString("N"));
        using var store = new WorkspaceStore(root, f.Module); store.SaveSession(new SessionState { Candidates = [c] });
        var before = File.ReadAllBytes(Path.Combine(root, "session.json"));
        using var http = LmStudioClient.CreateHttpClient();
        var settings = new StudioSettings { HighFiveRoot = f.Module, MaxTokens = 1024, TimeoutSeconds = 180 };
        Console.WriteLine("LIVE_LM_ATTEMPT: one explicit POST; exact model=" + settings.ModelId + "; references=" + shortlist.Matches.Count);
        var timer = System.Diagnostics.Stopwatch.StartNew();
        try
        {
            var result = await new LmStudioClient(http).ReviewSemanticAsync(settings, "", c, shortlist, CancellationToken.None);
            var updated = SemanticDuplicateScout.WithCurrentEvidence(c, new PackReader().Load(f.Module), [c], result);
            store.SaveSession(new SessionState { Candidates = [updated] });
            Console.WriteLine("LIVE_LM_PASS: strict bounded advisory persisted only in isolated workspace; elapsedSeconds=" + (int)timer.Elapsed.TotalSeconds);
            Console.WriteLine("MODEL_ADVISORY_NOT_VERIFIED / COVERAGE_LIMITED; verdicts=" + result.Verdicts.Count);
            Console.WriteLine("SOURCE_UNCHANGED=" + pack.Files.SequenceEqual(new PackReader().Load(f.Module).Files) + "; candidateStatus=" + updated.Status + "; approvedHashUnchanged=" + (updated.ApprovedFingerprint == c.ApprovedFingerprint));
            return 0;
        }
        catch (LmStudioException ex)
        {
            Console.WriteLine("BLOCKED_LM: " + ex.Diagnostic.Code + "; no retry; elapsedSeconds=" + (int)timer.Elapsed.TotalSeconds);
            Console.WriteLine("SESSION_BYTES_UNCHANGED=" + before.SequenceEqual(File.ReadAllBytes(Path.Combine(root, "session.json"))));
            return 2;
        }
    }
    private static int Pss005Source(string[] args)
    {
        if (args.Length != 2) throw new ArgumentException("--pss-005-source <absolute module root>");
        var reader = new PackReader(); var before = reader.Load(args[1]);
        var c = CandidateOf(before, "Как твои дела сегодня?"); c.Id = "pss005-readonly-probe";
        var shortlist = SemanticDuplicateScout.Search(before, c, []);
        var after = reader.Load(args[1]);
        var same = before.Fingerprint == after.Fingerprint && before.Files.SequenceEqual(after.Files);
        Console.WriteLine("SOURCE_READONLY_SMOKE: files=" + before.Files.Count + "; patterns=" + before.Entries.Count(e => e.Kind == "PATTERN") + "; templates=" + before.Entries.Count(e => e.Kind == "TEMPLATE"));
        Console.WriteLine("SCOUT: considered=" + shortlist.TotalConsidered + "; scope=" + shortlist.ScopeConsidered + "; shortlist=" + shortlist.Matches.Count + "; excludedFromModel=" + (shortlist.TotalConsidered - shortlist.Matches.Count));
        Console.WriteLine("COVERAGE_LIMITED; model POST=0; source SHA/bytes unchanged=" + same + "; fingerprint=" + before.Fingerprint);
        return same ? 0 : 1;
    }
    private static int Pss005UiFixture(string[] args)
    {
        if (args.Length != 2) throw new ArgumentException("--pss-005-ui-fixture <new Studio artifacts/PSS-005 root>");
        var studio = Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "../../../../../"));
        var root = PathSafety.Canonical(args[1]);
        if (!PathSafety.IsWithin(root, Path.Combine(studio, "artifacts/PSS-005")) || Directory.Exists(root))
            throw new InvalidDataException("Нужна новая UI fixture внутри artifacts/PSS-005.");
        // Kept alive for the external UI process; source is an owned temp outside Git root.
        var f = new Fixture(); var pack = new PackReader().Load(f.Module);
        var c = CandidateOf(pack, "Привет, товарищ!"); c.Id = "synthetic-ui-candidate";
        CandidateReview.Approve(c, pack, [], "Synthetic UI fixture");
        using var store = new WorkspaceStore(Path.Combine(root, "workspace"), f.Module);
        store.SaveSettings(new StudioSettings { HighFiveRoot = f.Module }); store.SaveSession(new SessionState { Candidates = [c] });
        Console.WriteLine("SYNTHETIC_UI_FIXTURE=" + root + "; source=" + f.Module + "; candidates=1; actual L2J/model calls=0");
        return 0;
    }
    private static string SemanticBody(SemanticShortlist s) => JsonSerializer.Serialize(new { verdicts = s.Matches.Select(m => new { refKey = m.RefKey, relation = "SAME_MEANING", reason = "Краткая причина" }) }, WorkspaceStore.JsonOptions);
    private static string SemanticEnvelope(string content) => JsonSerializer.Serialize(new { choices = new[] { new { finish_reason = "stop", message = new { role = "assistant", content } } } });
    private static Candidate ScoutCandidate() => new() { Id = "selected", Text = "Привет!", Act = "greet.reply", Topic = "greeting", SourceFingerprint = "fixture-baseline" };
    private static PackEntry ScoutEntry(string id, string text, string act = "greet.reply", string kind = "TEMPLATE")
        => new() { Id = id, Text = text, Act = act, Topic = "greeting", Kind = kind, SourceFile = "fixture.xml", SourceLine = 7 };
    private static PackSnapshot ScoutPack(List<PackEntry> entries) => new() { Fingerprint = "fixture-baseline", Entries = entries,
        Files = [new("fixture.xml", TextRules.Hash("fixture"), 7)], Acts = ["greet.reply"], Topics = ["greeting"] };
}
