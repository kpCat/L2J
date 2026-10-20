using System.Net;
using System.Text;
using System.Text.Json;
using PhantomSemanticStudio.Core;

// Независимый console runner: exit != 0 при любой неуспешной проверке.
internal static partial class Program
{
    private static int passed;
    private static int failed;
    private static readonly List<string> Failures = [];

    [STAThread]
    private static async Task<int> Main(string[] args)
    {
        if (args.Length > 0 && args[0] == "--pss-010-source") return Pss010Source(args);
        if (args.Length > 0 && args[0] == "--pss-010")
        { Pss010(); Console.WriteLine($"RESULT: {passed} PASS; {failed} FAIL"); return failed == 0 ? 0 : 1; }
        if (args.Length > 0 && args[0] == "--pss-009-layout") return Pss009Layout(args);
        if (args.Length > 0 && args[0] == "--pss-008-controls") return Pss008Controls();
        if (args.Length > 0 && args[0] == "--pss-008-c")
        { Pss008C(); Console.WriteLine($"RESULT: {passed} PASS; {failed} FAIL"); return failed == 0 ? 0 : 1; }
        if (args.Length > 0 && args[0] == "--pss-008-source") return Pss008Source(args);
        if (args.Length > 0 && args[0] == "--pss-008-handoff") return Pss008Handoff(args);
        if (args.Length == 2 && args[0] == "--pss-008-inspect-stage")
        {
            try
            { var stage = PathSafety.Canonical(args[1]); V3ProposalContract.ReadStage(Path.GetDirectoryName(Path.GetDirectoryName(stage))!, stage); Console.WriteLine("PASS_V3_STAGE_INTEGRITY"); return 0; }
            catch { Console.WriteLine("BLOCKED_V3_STAGE_INTEGRITY"); return 2; }
        }
        if (args.Length > 0 && args[0] == "--pss-008-b")
        { Pss008B(); Console.WriteLine($"RESULT: {passed} PASS; {failed} FAIL"); return failed == 0 ? 0 : 1; }
        if (args.Length > 0 && args[0] == "--pss-008-a")
        {
            Pss008A(); Console.WriteLine($"RESULT: {passed} PASS; {failed} FAIL"); return failed == 0 ? 0 : 1;
        }
        if (args.Length > 0 && args[0] == "--pss-007-controls") return Pss007Controls();
        if (args.Length > 0 && args[0] == "--pss-007-b")
        {
            await Pss007B(); Console.WriteLine($"RESULT: {passed} PASS; {failed} FAIL"); return failed == 0 ? 0 : 1;
        }
        if (args.Length > 0 && args[0] == "--pss-007-c")
        {
            await Pss007C(); Console.WriteLine($"RESULT: {passed} PASS; {failed} FAIL"); return failed == 0 ? 0 : 1;
        }
        if (args.Length > 0 && args[0] == "--pss-007-a")
        {
            Pss007A(); Console.WriteLine($"RESULT: {passed} PASS; {failed} FAIL"); return failed == 0 ? 0 : 1;
        }
        if (args.Length > 0 && args[0] == "--pss-006-controls")
        {
            var result = Pss006Controls();
            if (result != 0) foreach (var failure in Failures) Console.WriteLine(failure);
            return result;
        }
        if (args.Length > 0 && args[0] == "--pss-006-actual") return Pss006Actual(args);
        if (args.Length > 0 && args[0] == "--pss-006-reopen") return Pss006Reopen(args);
        if (args.Length > 0 && args[0] == "--pss-006-interactive") return Pss006Interactive();
        if (args.Length > 0 && args[0] == "--pss-006-b")
        {
            Pss006B(); Console.WriteLine($"RESULT: {passed} PASS; {failed} FAIL"); return failed == 0 ? 0 : 1;
        }
        if (args.Length > 0 && args[0] == "--pss-006-a")
        {
            await Pss006A();
            Console.WriteLine($"RESULT: {passed} PASS; {failed} FAIL");
            return failed == 0 ? 0 : 1;
        }
        if (args.Length > 0 && args[0] == "--pss-005-controls") return Pss005Controls();
        if (args.Length > 0 && args[0] == "--pss-005-live") return await Pss005Live();
        if (args.Length > 0 && args[0] == "--pss-005-source") return Pss005Source(args);
        if (args.Length > 0 && args[0] == "--pss-005-ui-fixture") return Pss005UiFixture(args);
        if (args.Length > 0 && args[0] == "--pss-005")
        {
            Pss005Offline();
            await Pss005Http();
            await Pss005Persistence();
            Console.WriteLine($"RESULT: {passed} PASS; {failed} FAIL");
            return failed == 0 ? 0 : 1;
        }
        if (args.Length > 0 && args[0] == "--source-smoke") return await SourceSmoke.RunAsync(args);
        if (args.Length > 0 && args[0] == "--pss-003-source") return Pss003Source(args);
        if (args.Length > 0 && args[0] == "--pss-004-ui-fixture") return Pss004UiFixture(args);
        if (args.Length > 0 && args[0] == "--pss-004-controls") return Pss004Controls();
        if (args.Length > 0 && args[0] == "--pss-004")
        {
            Pss004();
            Console.WriteLine($"RESULT: {passed} PASS; {failed} FAIL");
            return failed == 0 ? 0 : 1;
        }
        if (args.Length > 0 && args[0] == "--pss-003")
        {
            Pss003();
            Console.WriteLine($"RESULT: {passed} PASS; {failed} FAIL");
            return failed == 0 ? 0 : 1;
        }
        if (args.Length > 0 && args[0] == "--pss-002")
        {
            using var focused = new Fixture();
            using var workspace = new WorkspaceStore(Path.Combine(focused.Root, "studio"), focused.Module);
            await Pss002(new PackReader().Load(focused.Module), workspace);
            Console.WriteLine($"RESULT: {passed} PASS; {failed} FAIL");
            return failed == 0 ? 0 : 1;
        }
        if (args.Length > 0 && args[0] == "--negative-control")
        {
            Test("intentional assertion inside negative control must fail", () => Throws(() => True(false)));
            await TestAsync("intentional async assertion must fail", () => ThrowsAsync(() => Task.FromException(new AssertionFailure("Intentional assertion"))));
            Console.WriteLine($"RESULT: {passed} PASS; {failed} FAIL (intentional controls)");
            return failed == 0 ? 0 : 1;
        }
        Test("normalize Cyrillic and punctuation", () => Equal("привет елка", TextRules.Normalize(" ПРИВЕТ!!! Ёлка... ")));
        Test("normalize preserves placeholders", () => Equal("люблю {value}", TextRules.Normalize("Люблю {value}!")));
        Test("normalize compatibility form", () => Equal("abc 123", TextRules.Normalize("ＡＢＣ １２３")));
        Test("normalize distinguishes negation", () => True(TextRules.Normalize("люблю") != TextRules.Normalize("не люблю")));
        Test("endpoint accepts loopback", () => Equal("127.0.0.1", LmStudioClient.ValidateEndpoint("http://127.0.0.1:1234/v1").Host));
        Test("endpoint rejects remote", () => Throws(() => LmStudioClient.ValidateEndpoint("https://example.com/v1")));
        Test("endpoint rejects credentials", () => Throws(() => LmStudioClient.ValidateEndpoint("http://user@localhost:1234/v1")));
        Test("endpoint rejects query", () => Throws(() => LmStudioClient.ValidateEndpoint("http://localhost:1234/v1?redirect=x")));
        Test("strict JSON valid", () => Equal(1, LmStudioClient.ParseDrafts("{\"items\":[{\"kind\":\"TEMPLATE\",\"text\":\"Привет!\",\"reason\":\"Короткий ответ\"}]}", 2).Count));
        Test("strict JSON rejects unknown keys", () => Throws(() => LmStudioClient.ParseDrafts("{\"items\":[],\"code\":\"run\"}", 2)));
        Test("strict JSON rejects fenced output", () => Throws(() => LmStudioClient.ParseDrafts("```json\n{}\n```", 2)));
        Test("strict JSON rejects duplicate keys", () => Throws(() => LmStudioClient.ParseDrafts("{\"items\":[],\"items\":[]}", 2)));
        Test("strict JSON rejects null", () => Throws(() => LmStudioClient.ParseDrafts("null", 2)));
        Test("strict JSON rejects unknown kind", () => Throws(() => LmStudioClient.ParseDrafts("{\"items\":[{\"kind\":\"CODE\",\"text\":\"x\",\"reason\":\"x\"}]}", 2)));
        using var fixture = new Fixture();
        var reader = new PackReader();
        var snapshot = reader.Load(fixture.Module);
        Test("reader sees v3 plus base", () => True(snapshot.Entries.Any(x => x.Id == "v3.test.reply.0001")));
        Test("fingerprint deterministic", () => Equal(snapshot.Fingerprint, reader.Load(fixture.Module).Fingerprint));
        Test("reader accepts explicit custom override", () =>
        {
            fixture.SetCustom("<phrases version=\"1\"><template id=\"greet.base\" act=\"greet.reply\" band=\"UNKNOWN\" register=\"NEUTRAL\" profanity=\"NONE\" text=\"Салют!\" override=\"true\"/></phrases>");
            var read = reader.Load(fixture.Module);
            Equal("Салют!", read.Entries.Single(e => e.Id == "greet.base").Text);
            fixture.SetCustom("<phrases version=\"1\"/>");
        });
        Test("DTD/XXE rejected", () =>
        {
            var old = File.ReadAllText(fixture.Segment);
            try { File.WriteAllText(fixture.Segment, "<!DOCTYPE x [<!ENTITY z SYSTEM 'file:///etc/passwd'>]><humanizedV3SemanticSegment version=\"3\">&z;</humanizedV3SemanticSegment>"); Throws(() => reader.Load(fixture.Module)); }
            finally { File.WriteAllText(fixture.Segment, old); }
        });
        Test("manifest traversal rejected", () =>
        {
            var old = File.ReadAllText(fixture.Manifest);
            try { File.WriteAllText(fixture.Manifest, old.Replace("semantic/humanized/v3/segments/test.xml", "../outside.xml")); Throws(() => reader.Load(fixture.Module)); }
            finally { File.WriteAllText(fixture.Manifest, old); }
        });
        Test("duplicate exact normalized blocked", () => True(CandidateValidator.Validate(CandidateOf(snapshot, "ПРИВЕТ!!!"), snapshot, []).Any(x => x.Severity == IssueSeverity.Error && x.Code == "EXACT_DUPLICATE")));
        Test("unknown act blocked", () => { var c = CandidateOf(snapshot,"Новый ответ"); c.Act = "new.unknown"; True(CandidateValidator.Validate(c,snapshot,[]).Any(x => x.Code == "UNKNOWN_ACT")); });
        Test("action act blocked", () => { var c = CandidateOf(snapshot,"Баф уже выдан"); c.Act="support.buff.request"; True(CandidateValidator.Validate(c,snapshot,[]).Any(x => x.Code == "FUNCTIONAL_ACT")); });
        Test("invalid placeholder blocked", () => True(CandidateValidator.Validate(CandidateOf(snapshot,"Привет {execute}"),snapshot,[]).Any(x => x.Code == "PLACEHOLDER")));
        Test("gender-specific kept as runtime warning", () => { var c=CandidateOf(snapshot,"Я рада встрече."); c.Gender="FEMALE"; True(CandidateValidator.Validate(c,snapshot,[]).Any(x => x.Code=="GENDER_RUNTIME")); });
        Test("approval invalidated by edit", () => { var c=CandidateOf(snapshot,"Сегодня необычно тихо."); CandidateReview.Approve(c,snapshot,[],"Проверено вручную"); True(CandidateReview.IsCurrent(c)); c.Text+=" Еще фраза."; True(!CandidateReview.IsCurrent(c)); });
        Test("path excludes source", () => Throws(() => PathSafety.AssertDisjoint(fixture.Module,Path.Combine(fixture.Module,"workspace"))));
        Test("path excludes ancestor", () => Throws(() => PathSafety.AssertDisjoint(fixture.Module,fixture.Root)));
        Test("sibling path allowed", () => PathSafety.AssertDisjoint(fixture.Module,fixture.Module+"-studio"));
        var workspacePath = Path.Combine(fixture.Root, "studio");
        using var store = new WorkspaceStore(workspacePath,fixture.Module);
        Test("negative assertion controls reject a successful operation", () =>
        {
            var rejected = false;
            try { Throws(() => { }); } catch (AssertionFailure) { rejected = true; }
            True(rejected);
            rejected = false;
            try { Throws(() => True(false)); } catch (AssertionFailure) { rejected = true; }
            True(rejected);
        });
        Test("approval binds manual note and review timestamp", () =>
        {
            var c = CandidateOf(snapshot, "Пока есть минутка, расскажи о своём дне.");
            CandidateReview.Approve(c, snapshot, [], "Прочитано");
            c.ReviewNote = "";
            True(!CandidateReview.IsCurrent(c));
            Throws(() => new ReviewExporter().Export(store, snapshot, [c]));
        });
        Test("unverified profanity and conditions never produce safe XML", () =>
        {
            var c = CandidateOf(snapshot, "Чёрт, я бафнул тебя; отвечу только после оплаты.");
            CandidateReview.Approve(c, snapshot, [], "Только для ревью");
            using var zip = System.IO.Compression.ZipFile.OpenRead(new ReviewExporter().Export(store, snapshot, [c]));
            True(!zip.Entries.Any(e => e.FullName.EndsWith(".xml", StringComparison.Ordinal)));
        });
        Test("reader diagnoses unsupported schema without silent loss", () =>
        {
            var old = File.ReadAllText(fixture.Segment);
            try
            {
                File.WriteAllText(fixture.Segment, old.Replace("</patterns>", "</patterns><unknownContract><value/></unknownContract>"));
                True(reader.Load(fixture.Module).Warnings.Any(w => w.Contains("unknownContract", StringComparison.Ordinal)));
            }
            finally { File.WriteAllText(fixture.Segment, old); }
        });
        Test("invalid custom override is rejected", () =>
        {
            try
            {
                fixture.SetCustom("<phrases version=\"1\"><template id=\"new.id\" act=\"greet.reply\" text=\"Новая фраза\" override=\"maybe\"/></phrases>");
                Throws(() => reader.Load(fixture.Module));
            }
            finally { fixture.SetCustom("<phrases version=\"1\"/>"); }
        });
        Test("reader rejects malformed bytes, limits, missing segments and partial v2", () =>
        {
            var original = File.ReadAllBytes(fixture.Segment);
            try
            {
                File.WriteAllBytes(fixture.Segment, [0xff, 0xfe, 0x00]); Throws(() => reader.Load(fixture.Module));
                File.WriteAllBytes(fixture.Segment, new byte[1024 * 1024 + 1]); Throws(() => reader.Load(fixture.Module));
                File.Delete(fixture.Segment); Throws(() => reader.Load(fixture.Module));
            }
            finally { File.WriteAllBytes(fixture.Segment, original); }
            var v2 = Path.Combine(fixture.Module, "dist/game/data/phantoms/semantic/humanized/high-five-ru-humanized-semantic-v2.xml");
            try { File.WriteAllText(v2, "<humanizedSemanticPack version=\"2\"/>"); Throws(() => reader.Load(fixture.Module)); }
            finally { File.Delete(v2); }
        });
        Test("path rejects absolute traversal and directory junction", () =>
        {
            Throws(() => PathSafety.ResolveRelative(fixture.Root, "../outside.xml"));
            Throws(() => PathSafety.ResolveRelative(fixture.Root, fixture.Segment));
            var link = Path.Combine(fixture.Root, "junction");
            if (OperatingSystem.IsWindows())
            {
                var result = System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo("cmd.exe", $"/c mklink /J \"{link}\" \"{fixture.Module}\"") { UseShellExecute = false, CreateNoWindow = true });
                result!.WaitForExit(); Equal(0, result.ExitCode); result.Dispose();
            }
            else Directory.CreateSymbolicLink(link, fixture.Module);
            try { Throws(() => PathSafety.AssertDisjoint(fixture.Module, Path.Combine(link, "workspace"))); }
            finally { Directory.Delete(link); }
        });
        Test("batch duplicates conflict across acts and unknown scope is blocked", () =>
        {
            var first = CandidateOf(snapshot, "Ёж сегодня не спешит.");
            var second = CandidateOf(snapshot, "ЕЖ СЕГОДНЯ НЕ СПЕШИТ!!!"); second.Act = "other.reply";
            True(CandidateValidator.Validate(second, snapshot, [first]).Any(i => i.Code == "EXACT_DUPLICATE"));
            second.Topic = "missing"; True(CandidateValidator.Validate(second, snapshot, []).Any(i => i.Code == "UNKNOWN_TOPIC"));
            second.Act = "identity.gender.reply"; True(CandidateValidator.Validate(second, snapshot, []).Any(i => i.Code == "FUNCTIONAL_ACT"));
        });
        Test("failed save preserves valid state and interrupted temporary file is ignored", () =>
        {
            var saved = new SessionState { Lessons = ["Принятое замечание"] }; store.SaveSession(saved);
            var path = Path.Combine(store.Root, "session.json"); var old = File.ReadAllBytes(path);
            File.WriteAllText(path + ".tmp-interrupted", "broken"); Equal("Принятое замечание", store.LoadSession().Lessons.Single());
            using (var locked = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.Read))
                Throws(() => store.SaveSession(new SessionState { Lessons = ["Не должно заменить сохранённое"] }));
            True(old.SequenceEqual(File.ReadAllBytes(path))); Equal("Принятое замечание", store.LoadSession().Lessons.Single());
        });
        Test("conflicting aliases require exact explicit override", () =>
        {
            var aliases = Path.Combine(fixture.Module, "dist/game/data/phantoms/semantic/custom/my-ru-aliases.xml");
            Directory.CreateDirectory(Path.GetDirectoryName(aliases)!);
            try
            {
                File.WriteAllText(aliases, "<aliases version=\"1\"><alias from=\"КУ\" to=\"привет\"/><alias from=\"ку\" to=\"пока\"/></aliases>");
                Throws(() => reader.Load(fixture.Module));
                File.WriteAllText(aliases, "<aliases version=\"1\"><alias from=\"КУ\" to=\"привет\"/><alias from=\"ку\" to=\"пока\" override=\"true\"/></aliases>");
                Equal("пока", reader.Load(fixture.Module).Entries.Single(e => e.Kind == "ALIAS").Text);
            }
            finally { File.Delete(aliases); }
        });
        Test("workspace independent lock", () => Throws(() => { using var second=new WorkspaceStore(workspacePath,fixture.Module); }));
        Test("workspace persists session", () => { var state=new SessionState { Candidates=[CandidateOf(snapshot,"Как прошел твой вечер?")] }; store.SaveSession(state); Equal(1,store.LoadSession().Candidates.Count); });
        Test("saved scoped lesson survives restart without applying to every topic", () =>
        {
            var lesson = new EditorialLesson { Text = "Не обещать игрового действия.", Topic = "greeting", Act = "greet.reply", Gender = "FEMALE", Band = "FAMILIAR", SourceFingerprint = snapshot.Fingerprint };
            store.SaveSession(new SessionState { Lessons = ["Старое замечание без scope"], ScopedLessons = [lesson] });
            var state = store.LoadSession(); Equal("Старое замечание без scope", state.Lessons.Single());
            var request = state.ScopedLessons.Single().ToRequest(snapshot, false);
            Equal("greeting", request.Topic); Equal("greet.reply", request.Act); Equal("FEMALE", request.Gender); Equal("FAMILIAR", request.Band);
            var changed = snapshot with { Fingerprint = "changed" };
            Throws(() => lesson.ToRequest(changed, false)); Equal(lesson.Text, lesson.ToRequest(changed, true).Instruction);
            Equal(snapshot.Fingerprint, lesson.SourceFingerprint);
            Throws(() => (lesson with { Topic = "missing" }).ToRequest(snapshot, true));
        });
        Test("export refuses stale approval instead of silently dropping it", () =>
        {
            var c=CandidateOf(snapshot,"Небольшая передышка пойдёт на пользу."); CandidateReview.Approve(c,snapshot,[],"Прочитано");
            c.Text="Я изменил текст после одобрения.";
            Throws(()=>new ReviewExporter().Export(store,snapshot,[c]));
        });
        Test("export rejects stale source", () =>
        {
            var c=CandidateOf(snapshot,"Вечер располагает к разговору."); CandidateReview.Approve(c,snapshot,[],"Проверено");
            var old=File.ReadAllText(fixture.Segment);
            try { File.AppendAllText(fixture.Segment,"\n<!-- external edit -->"); Throws(() => new ReviewExporter().Export(store,snapshot,[c])); }
            finally { File.WriteAllText(fixture.Segment,old); }
        });
        Test("export writes only review ZIP outside source", () =>
        {
            var c=CandidateOf(snapshot,"Разговор сегодня особенно интересный."); CandidateReview.Approve(c,snapshot,[],"Прочитано");
            var before=reader.Load(fixture.Module).Fingerprint;
            var zip=new ReviewExporter().Export(store,snapshot,[c]);
            True(File.Exists(zip)); True(!PathSafety.IsWithin(zip,fixture.Module)); Equal(before,reader.Load(fixture.Module).Fingerprint);
            using var z=System.IO.Compression.ZipFile.OpenRead(zip); True(z.GetEntry("DO_NOT_INSTALL.txt")!=null);
        });
        await TestAsync("HTTP parses expected model response", async () =>
        {
            using var client=new HttpClient(new FakeHandler("{\"data\":[{\"id\":\"test-model\"}]}"));
            var lm=new LmStudioClient(client); var models=await lm.ListModelsAsync(new StudioSettings { HighFiveRoot = fixture.Module },"",CancellationToken.None); Equal("test-model",models.Single());
        });
        await TestAsync("HTTP errors fail closed", async () =>
        {
            using var client=new HttpClient(new FakeHandler("bad",HttpStatusCode.BadRequest));
            await ThrowsAsync(async ()=>await new LmStudioClient(client).ListModelsAsync(new StudioSettings { HighFiveRoot = fixture.Module },"",CancellationToken.None));
        });
        await TestAsync("HTTP cancellation respected", async () =>
        {
            using var client=new HttpClient(new FakeHandler("{}")); using var cancel=new CancellationTokenSource(); cancel.Cancel();
            await ThrowsAsync(async ()=>await new LmStudioClient(client).ListModelsAsync(new StudioSettings { HighFiveRoot = fixture.Module },"",cancel.Token));
        });
        await TestAsync("HTTP generation rejects length tools bad JSON errors and response limits", async () =>
        {
            var request = new GenerationRequest("greeting", "greet.reply", "UNKNOWN", "NEUTRAL", "ANY", "Новые короткие реплики", "", 4);
            var invalid = new[]
            {
                "{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"{}\"}}]}",
                "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"tool_calls\":[],\"content\":\"{}\"}}]}",
                "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"```json {} ```\"}}]}",
                "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"{\\\"items\\\":[]}\"}}]}",
                new string('x', 1024 * 1024 + 1)
            };
            foreach (var body in invalid)
            {
                using var client = new HttpClient(new FakeHandler(body));
                await ThrowsAsync(() => new LmStudioClient(client).GenerateAsync(new StudioSettings { HighFiveRoot = fixture.Module }, "", snapshot, request, CancellationToken.None));
            }
            using var error = new HttpClient(new FakeHandler("unavailable", HttpStatusCode.ServiceUnavailable));
            await ThrowsAsync(() => new LmStudioClient(error).GenerateAsync(new StudioSettings { HighFiveRoot = fixture.Module }, "", snapshot, request, CancellationToken.None));
            using var slow = new HttpClient(new SlowHandler());
            using var cancel = new CancellationTokenSource(TimeSpan.FromMilliseconds(100));
            await ThrowsAsync(() => new LmStudioClient(slow).GenerateAsync(new StudioSettings { HighFiveRoot = fixture.Module }, "", snapshot, request, cancel.Token));
        });
        await TestAsync("HTTP refuses legacy function calls even with valid candidate content", async () =>
        {
            var content = JsonSerializer.Serialize(new { items = new[] { new { kind = "TEMPLATE", text = "Сегодня тихий вечер.", reason = "Новая реплика" } } });
            var envelope = JsonSerializer.Serialize(new { choices = new[] { new { finish_reason = "stop", message = new { role = "assistant", content, function_call = new { name = "unsafe", arguments = "{}" } } } } });
            using var client = new HttpClient(new FakeHandler(envelope));
            await ThrowsAsync(() => new LmStudioClient(client).GenerateAsync(new StudioSettings { HighFiveRoot = fixture.Module }, "", snapshot,
                new GenerationRequest("greeting", "greet.reply", "UNKNOWN", "NEUTRAL", "ANY", "Новая реплика", "", 4), CancellationToken.None));
        });
        await TestAsync("HTTP timeout cancels a stalled request without retries", async () =>
        {
            using var http = new HttpClient(new SlowHandler()) { Timeout = Timeout.InfiniteTimeSpan };
            await ThrowsAsync(() => new LmStudioClient(http).ListModelsAsync(new StudioSettings { HighFiveRoot = fixture.Module, TimeoutSeconds = 15 }, "", CancellationToken.None));
        });
        await Pss002(snapshot, store);
        Pss003();
        Pss004();
        Pss005Offline();
        await Pss005Http();
        await Pss005Persistence();
        await Pss006A();
        Pss006B();
        Pss007A();
        await Pss007B();
        await Pss007C();
        Pss008A();
        Pss008B();
        Pss008C();
        Console.WriteLine($"RESULT: {passed} PASS; {failed} FAIL");
        foreach(var failure in Failures) Console.WriteLine(failure);
        return failed==0?0:1;
    }
    private static async Task Pss002(PackSnapshot snapshot, WorkspaceStore store)
    {
        var settings = new StudioSettings { HighFiveRoot = snapshot.ModuleRoot, ModelId = "exact-test-model" };
        GenerationRequest Request(int mode) => JsonSerializer.Deserialize<GenerationRequest>(JsonSerializer.Serialize(new
        {
            Topic = "greeting", Act = "greet.reply", Band = "FAMILIAR", Register = "CASUAL", Gender = "FEMALE",
            Instruction = "Две короткие реплики", Words = "вечер", Count = 2, Mode = mode
        }))!;
        await TestAsync("PSS-002 single kind schema and local whole batch rejection", async () =>
        {
            for (var mode = 0; mode < 2; mode++)
            {
                var kind = mode == 0 ? "TEMPLATE" : "PATTERN";
                var other = mode == 0 ? "PATTERN" : "TEMPLATE";
                using var handler = new RecordingHandler(Envelope(kind)); using var http = new HttpClient(handler);
                var drafts = await new LmStudioClient(http).GenerateAsync(settings, "", snapshot, Request(mode), CancellationToken.None);
                Equal(kind, drafts.Single().Kind); Equal(1, handler.Calls);
                using var payload = JsonDocument.Parse(handler.Payload!);
                var root = payload.RootElement;
                Equal("exact-test-model", root.GetProperty("model").GetString());
                True(!root.TryGetProperty("tools", out _) && !root.TryGetProperty("functions", out _));
                var kinds = root.GetProperty("response_format").GetProperty("json_schema").GetProperty("schema")
                    .GetProperty("properties").GetProperty("items").GetProperty("items").GetProperty("properties").GetProperty("kind").GetProperty("enum");
                Equal(1, kinds.GetArrayLength()); Equal(kind, kinds[0].GetString());
                using var user = JsonDocument.Parse(root.GetProperty("messages")[1].GetProperty("content").GetString()!);
                Equal("FEMALE", user.RootElement.GetProperty("Gender").GetString());
                Equal("FAMILIAR", user.RootElement.GetProperty("Band").GetString());
                Equal("CASUAL", user.RootElement.GetProperty("Register").GetString());
                using var mismatch = new HttpClient(new RecordingHandler(Envelope(kind, other)));
                await ThrowsAsync(() => new LmStudioClient(mismatch).GenerateAsync(settings, "", snapshot, Request(mode), CancellationToken.None));
            }
        });
        await TestAsync("PSS-002 MIXED legacy requests lessons and invalid enum", async () =>
        {
            var legacy = new GenerationRequest("greeting", "greet.reply", "UNKNOWN", "NEUTRAL", "ANY", "Новые реплики", "", 2);
            var lesson = new EditorialLesson { Topic = "greeting", Act = "greet.reply", Text = "Новое замечание", SourceFingerprint = snapshot.Fingerprint };
            foreach (var request in new[] { legacy, lesson.ToRequest(snapshot, false), Request(2) })
            {
                using var http = new HttpClient(new RecordingHandler(Envelope("TEMPLATE", "PATTERN")));
                Equal(2, (await new LmStudioClient(http).GenerateAsync(settings, "", snapshot, request, CancellationToken.None)).Count);
            }
            using var handler = new RecordingHandler(Envelope("TEMPLATE")); using var invalid = new HttpClient(handler);
            await ThrowsAsync(() => new LmStudioClient(invalid).GenerateAsync(settings, "", snapshot, Request(99), CancellationToken.None));
            Equal(0, handler.Calls);
        });
        await TestAsync("PSS-002 HTTP categories redact body token and exception", async () =>
        {
            const string secret = "test-secret-never-diagnostic";
            foreach (var (status, code) in new[] { (401, "AUTH"), (403, "AUTH"), (404, "ENDPOINT"), (500, "HTTP_ERROR"), (400, "SCHEMA_REJECTED"), (302, "HTTP_ERROR") })
            {
                using var handler = new RecordingHandler(secret, (HttpStatusCode)status); using var http = new HttpClient(handler);
                try
                {
                    if (status == 400) await new LmStudioClient(http).GenerateAsync(settings, secret, snapshot, Request(0), CancellationToken.None);
                    else await new LmStudioClient(http).ListModelsAsync(settings, secret, CancellationToken.None);
                    True(false);
                }
                catch (Exception e) when (e is not AssertionFailure) { True(e.Message.StartsWith(code + ":", StringComparison.Ordinal)); True(!e.ToString().Contains(secret, StringComparison.Ordinal)); }
                Equal(1, handler.Calls);
            }
            using var offline = new HttpClient(new FaultHandler(new HttpRequestException(secret, new System.Net.Sockets.SocketException(10061))));
            try { await new LmStudioClient(offline).ListModelsAsync(settings, secret, CancellationToken.None); True(false); }
            catch (Exception e) when (e is not AssertionFailure) { True(e.Message.StartsWith("SERVER_OFFLINE:", StringComparison.Ordinal)); True(!e.ToString().Contains(secret, StringComparison.Ordinal)); }
        });
        await TestAsync("PSS-002 catalogue rejects malformed duplicate fields and oversize", async () =>
        {
            foreach (var body in new[] { "null", "{\"data\":[{}]}", "{\"data\":[],\"data\":[]}", "{\"data\":[{\"id\":\"a\",\"id\":\"b\"}]}", "{\"data\":[],\"private-prompt\":", new string('x', 1024 * 1024 + 1) })
            {
                using var handler = new RecordingHandler(body); using var http = new HttpClient(handler);
                try { await new LmStudioClient(http).ListModelsAsync(settings, "", CancellationToken.None); True(false); }
                catch (Exception e) when (e is not AssertionFailure) { True(e.Message.StartsWith("BAD_RESPONSE:", StringComparison.Ordinal)); True(!e.ToString().Contains("private-prompt", StringComparison.Ordinal)); }
                Equal(1, handler.Calls);
            }
        });
        await TestAsync("PSS-002 exact catalogue check is advisory GET only and endpoint guards", async () =>
        {
            foreach (var host in new[] { "localhost", "127.0.0.1", "[::1]" }) LmStudioClient.ValidateEndpoint("http://" + host + ":1234/v1");
            foreach (var endpoint in new[] { "http://localhost.example.com/v1", "http://127.0.0.1:1234/v1#x", "http://localhost:1234/wrong" })
                Throws(() => LmStudioClient.ValidateEndpoint(endpoint));
            foreach (var found in new[] { true, false })
            {
                using var handler = new RecordingHandler(JsonSerializer.Serialize(new { data = new[] { new { id = found ? settings.ModelId : "other-model" } } }));
                using var http = new HttpClient(handler);
                var diagnostic = await new LmStudioClient(http).CheckModelAsync(settings, "", CancellationToken.None);
                Equal(found ? LmDiagnosticCode.CHECKED_MODEL_LIST : LmDiagnosticCode.MODEL_NOT_LISTED, diagnostic.Code);
                if (found) True(diagnostic.Message.Contains("JIT", StringComparison.Ordinal) && diagnostic.Message.Contains("ещё не проверены", StringComparison.Ordinal));
                Equal(1, handler.Calls); True(handler.Payload == null);
            }
        });
        await TestAsync("PSS-002 positive drafts duplicate warnings and JSON review only", async () =>
        {
            using var http = new HttpClient(new RecordingHandler(Envelope("TEMPLATE", "TEMPLATE")));
            var drafts = await new LmStudioClient(http).GenerateAsync(settings, "", snapshot, Request(0), CancellationToken.None);
            var candidates = drafts.Select(d => CandidateOf(snapshot, d.Text)).ToList();
            candidates[1].Text = candidates[0].Text.ToUpperInvariant();
            store.SaveSession(new SessionState { Candidates = candidates });
            True(store.LoadSession().Candidates.All(c => c.Status == "DRAFT" && !CandidateReview.IsCurrent(c)));
            var issues = CandidateValidator.Validate(candidates[1], snapshot, candidates);
            True(issues.Any(i => i.Code == "EXACT_DUPLICATE" && i.Severity == IssueSeverity.Error));
            True(issues.Any(i => i.Code == "SEMANTIC_NOT_CHECKED"));
            Throws(() => new ReviewExporter().Export(store, snapshot, candidates));
        });
        await TestAsync("PSS-002 envelope rejects duplicate refusal non-string and multi-choice", async () =>
        {
            var valid = Envelope("TEMPLATE");
            foreach (var body in new[] { valid.Replace("\"choices\":", "\"choices\":[],\"choices\":"),
                valid.Replace("\"finish_reason\":\"stop\"", "\"finish_reason\":\"length\",\"finish_reason\":\"stop\""),
                valid.Replace("\"content\":", "\"refusal\":\"private-refusal\",\"content\":"),
                "{\"choices\":[{\"finish_reason\":5,\"message\":{\"content\":{}}}]}", "{\"choices\":[{},{}]}" })
            {
                using var http = new HttpClient(new RecordingHandler(body));
                await ThrowsAsync(() => new LmStudioClient(http).GenerateAsync(settings, "", snapshot, Request(0), CancellationToken.None));
            }
        });
        await TestAsync("PSS-002 failed generation preserves saved session and approval", async () =>
        {
            var existing = CandidateOf(snapshot, "Пока вечер не закончился, расскажи что-нибудь.");
            CandidateReview.Approve(existing, snapshot, [], "Проверено для JSON ревью");
            store.SaveSession(new SessionState { Candidates = [existing], Lessons = ["Старое замечание"] });
            var path = Path.Combine(store.Root, "session.json"); var before = File.ReadAllBytes(path);
            using var http = new HttpClient(new RecordingHandler(Envelope("TEMPLATE", "PATTERN")));
            await ThrowsAsync(() => new LmStudioClient(http).GenerateAsync(settings, "", snapshot, Request(0), CancellationToken.None));
            True(before.SequenceEqual(File.ReadAllBytes(path))); True(CandidateReview.IsCurrent(store.LoadSession().Candidates.Single()));
            Equal("Старое замечание", store.LoadSession().Lessons.Single());
        });
        await TestAsync("PSS-002 external cancel and timeout are distinct without retry", async () =>
        {
            using var cancel = new CancellationTokenSource(); cancel.Cancel();
            using var http = new HttpClient(new SlowHandler()) { Timeout = Timeout.InfiniteTimeSpan };
            try { await new LmStudioClient(http).ListModelsAsync(settings, "", cancel.Token); True(false); }
            catch (Exception e) when (e is not AssertionFailure) { True(e.Message.StartsWith("CANCELLED:", StringComparison.Ordinal)); }
            using var timeoutHttp = new HttpClient(new FaultHandler(new TaskCanceledException("private-request")));
            try { await new LmStudioClient(timeoutHttp).ListModelsAsync(settings, "", CancellationToken.None); True(false); }
            catch (Exception e) when (e is not AssertionFailure) { True(e.Message.StartsWith("TIMEOUT:", StringComparison.Ordinal)); True(!e.ToString().Contains("private-request", StringComparison.Ordinal)); }
        });
    }
    private static string Envelope(params string[] kinds) => JsonSerializer.Serialize(new
    {
        choices = new[] { new { finish_reason = "stop", message = new { content = JsonSerializer.Serialize(new
        { items = kinds.Select((kind, i) => new { kind, text = "Вечер ещё оставил время на разговор " + i, reason = "Короткая новая фраза" }) }) } } }
    });
    private sealed class RecordingHandler(string body, HttpStatusCode status = HttpStatusCode.OK) : HttpMessageHandler
    {
        public int Calls { get; private set; }
        public string? Payload { get; private set; }
        protected override async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            cancellationToken.ThrowIfCancellationRequested(); Calls++;
            Payload = request.Content == null ? null : await request.Content.ReadAsStringAsync(cancellationToken);
            return new HttpResponseMessage(status) { Content = new StringContent(body, Encoding.UTF8, "application/json") };
        }
    }
    private sealed class FaultHandler(Exception error) : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken) => Task.FromException<HttpResponseMessage>(error);
    }
    private static Candidate CandidateOf(PackSnapshot s,string text)=>new() { Text=text, Topic="greeting", Act="greet.reply", Kind="TEMPLATE", Band="UNKNOWN", Register="NEUTRAL", SourceFingerprint=s.Fingerprint };
    private static void Test(string name,Action action) { try { action(); passed++; Console.WriteLine("PASS "+name); } catch(Exception e) { failed++; Failures.Add(name+": "+e); Console.WriteLine("FAIL "+name+": "+e.Message); } }
    private static async Task TestAsync(string name,Func<Task> action) { try { await action(); passed++; Console.WriteLine("PASS "+name); } catch(Exception e) { failed++; Failures.Add(name+": "+e); Console.WriteLine("FAIL "+name+": "+e.Message); } }
    private sealed class AssertionFailure(string message) : Exception(message);
    private static void Equal<T>(T expected,T actual) { if(!EqualityComparer<T>.Default.Equals(expected,actual)) throw new AssertionFailure($"Expected [{expected}], actual [{actual}]"); }
    private static void True(bool value) { if(!value) throw new AssertionFailure("Assertion failed"); }
    private static void Throws(Action action) { try { action(); } catch (Exception e) when (e is not AssertionFailure) { return; } throw new AssertionFailure("Expected failure, but operation succeeded"); }
    private static async Task ThrowsAsync(Func<Task> action) { try { await action(); } catch (Exception e) when (e is not AssertionFailure) { return; } throw new AssertionFailure("Expected async failure"); }
    private sealed class FakeHandler(string body,HttpStatusCode status=HttpStatusCode.OK):HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request,CancellationToken cancellationToken)
        { cancellationToken.ThrowIfCancellationRequested(); return Task.FromResult(new HttpResponseMessage(status){Content=new StringContent(body,Encoding.UTF8,"application/json")}); }
    }
    private sealed class SlowHandler : HttpMessageHandler
    {
        protected override async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        { await Task.Delay(Timeout.Infinite, cancellationToken); throw new AssertionFailure("Cancellation expected"); }
    }
    private sealed class Fixture:IDisposable
    {
        public string Root { get; }=Path.Combine(Path.GetTempPath(),"pss-tests-"+Guid.NewGuid().ToString("N"));
        public string Module=>Path.Combine(Root,"L2J_Mobius_CT_2.6_HighFive");
        private string Data=>Path.Combine(Module,"dist","game","data","phantoms");
        public string Manifest=>Path.Combine(Data,"semantic/humanized/v3/manifest.xml");
        public string Segment=>Path.Combine(Data,"semantic/humanized/v3/segments/test.xml");
        public Fixture()
        {
            Write("semantic/humanized/high-five-ru-humanized-semantic-v1.xml","<humanizedSemanticPack version=\"1\"><patterns><pattern id=\"greet.pattern\" topic=\"greeting\" act=\"greet.reply\" phrase=\"привет\" salience=\"0\" ttlMinutes=\"0\" priority=\"500\"/></patterns></humanizedSemanticPack>");
            Write("conversation/humanized/high-five-ru-humanized-conversation-v1.xml","<humanizedConversationPack version=\"1\"><templates><template id=\"greet.base\" act=\"greet.reply\" band=\"UNKNOWN\" register=\"NEUTRAL\" profanity=\"NONE\" text=\"Привет!\"/></templates></humanizedConversationPack>");
            Write("conversation/humanized/high-five-ru-persona-v1.xml","<humanizedPersonaPack version=\"1\"><interests/></humanizedPersonaPack>");
            Write("semantic/humanized/high-five-ru-humanized-corpus-v1.tsv","case\tinput\n");
            Write("semantic/humanized/v3/manifest.xml","<humanizedV3Manifest id=\"high-five-ru-humanized-v3\" version=\"3\" maxFiles=\"64\" maxFileBytes=\"1048576\" maxTotalBytes=\"33554432\" maxPatterns=\"8192\" maxTemplates=\"32768\" maxAliases=\"2048\" maxProfanity=\"1024\"><topics><topic key=\"greeting\"/></topics><acts><act key=\"greet.reply\"/></acts><segments><segment kind=\"SEMANTIC\" path=\"semantic/humanized/v3/segments/test.xml\"/><segment kind=\"CONVERSATION\" path=\"conversation/humanized/v3/segments/test.xml\"/></segments></humanizedV3Manifest>");
            Write("semantic/humanized/v3/segments/test.xml","<humanizedV3SemanticSegment id=\"v3.test.semantic\" version=\"3\" category=\"test\"><patterns><pattern id=\"v3.test.pattern.0001\" topic=\"greeting\" act=\"greet.reply\" phrase=\"доброго вечера\" salience=\"0\" ttlMinutes=\"0\" priority=\"500\"/></patterns></humanizedV3SemanticSegment>");
            Write("conversation/humanized/v3/segments/test.xml","<humanizedV3ConversationSegment id=\"v3.test.conversation\" version=\"3\" category=\"test\" mature=\"false\"><templates><template id=\"v3.test.reply.0001\" act=\"greet.reply\" band=\"UNKNOWN\" register=\"NEUTRAL\" profanity=\"NONE\" text=\"Добрый вечер, как дела?\"/></templates></humanizedV3ConversationSegment>");
            SetCustom("<phrases version=\"1\"/>");
        }
        public void SetCustom(string text)=>Write("conversation/custom/my-phrases.xml",text);
        private void Write(string relative,string text) { var path=Path.Combine(Data,relative); Directory.CreateDirectory(Path.GetDirectoryName(path)!); File.WriteAllText(path,text,new UTF8Encoding(false)); }
        public void Dispose() { try { Directory.Delete(Root,true); } catch { } }
    }
}
