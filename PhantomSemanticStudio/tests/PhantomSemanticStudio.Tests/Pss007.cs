using System.Text.Json;
using PhantomSemanticStudio.Core;

internal static partial class Program
{
    // Executable RED without a compile-error scaffold; exercises the new public contract when present.
    private static dynamic NewLab(PackSnapshot pack)
    {
        var type = typeof(PackSnapshot).Assembly.GetType("PhantomSemanticStudio.Core.DialogueLabSession");
        True(type != null);
        return Activator.CreateInstance(type!, pack)!;
    }
    private static void Pss007A()
    {
        using var f = new Fixture(); var pack = new PackReader().Load(f.Module);
        Test("PSS-007 A catalog-only exact reply provenance unsupported and no mutation", () =>
        {
            using var ws = new WorkspaceStore(Path.Combine(f.Root, "lab-a"), f.Module);
            var c = CandidateOf(pack, "Сегодня есть время поговорить."); CandidateReview.Approve(c, pack, [], "Ручная проверка");
            ws.SaveSession(new SessionState { Candidates = [c] });
            var before = File.ReadAllBytes(Path.Combine(ws.Root, "session.json"));
            var source = JsonSerializer.Serialize(pack);
            dynamic lab = NewLab(pack);
            dynamic p = lab.PrepareTurn("привет", "UNKNOWN", "NEUTRAL", CancellationToken.None);
            dynamic t = lab.CommitTurn(p, pack, CancellationToken.None);
            Equal("PACK_CATALOG_APPROXIMATE", (string)t.PackStatus); Equal("greet.pattern", (string)t.PatternId);
            Equal("greet.base", (string)t.TemplateId); Equal("Привет!", (string)t.PackText);
            Equal(pack.Fingerprint, (string)t.PackFingerprint);
            t = lab.CommitTurn(lab.PrepareTurn("неизвестная реплика", "UNKNOWN", "NEUTRAL", CancellationToken.None), pack, CancellationToken.None);
            Equal("NO_PACK_MATCH", (string)t.PackStatus); Equal("", (string)t.PackText);
            var extended = pack with { Entries = pack.Entries.Concat(new[] {
                new PackEntry { Kind = "PATTERN", Id = "identity.test", Act = "identity.name", Topic = "greeting", Text = "твое имя" },
                new PackEntry { Kind = "PATTERN", Id = "memory.test", Act = "greet.reply", Topic = "greeting", Text = "вспомни", Recall = "fact" }
            }).ToList() };
            dynamic unsupported = NewLab(extended);
            foreach (var input in new[] { "твое имя", "вспомни" })
            {
                t = unsupported.CommitTurn(unsupported.PrepareTurn(input, "UNKNOWN", "NEUTRAL", CancellationToken.None), extended, CancellationToken.None);
                Equal("FUNCTIONAL_OR_MEMORY_UNSUPPORTED", (string)t.PackStatus); Equal("", (string)t.PackText);
                True(((string)t.PatternId).Length > 0);
            }
            Equal(source, JsonSerializer.Serialize(pack)); True(before.SequenceEqual(File.ReadAllBytes(Path.Combine(ws.Root, "session.json"))));
            True(CandidateReview.IsCurrent(c)); Equal(pack.Fingerprint, new PackReader().Load(f.Module).Fingerprint);
        });
        Test("PSS-007 A scope histories cancellation stale source and transactional repeat queue", () =>
        {
            dynamic lab = NewLab(pack);
            dynamic p = lab.PrepareTurn("привет", "UNKNOWN", "NEUTRAL", CancellationToken.None);
            using var cancel = new CancellationTokenSource(); cancel.Cancel();
            Throws(() => lab.CommitTurn(p, pack, cancel.Token)); Equal(0, (int)lab.Turns.Count);
            dynamic t = lab.CommitTurn(lab.PrepareTurn("привет", "UNKNOWN", "NEUTRAL", CancellationToken.None), pack, CancellationToken.None);
            Equal("greet.base", (string)t.TemplateId);
            lab.Clear();
            t = lab.CommitTurn(lab.PrepareTurn("босс", "UNKNOWN", "NEUTRAL", CancellationToken.None), pack, CancellationToken.None);
            Equal("UNKNOWN", (string)t.WorldHint); True((bool)t.Ambiguous);
            lab.Clear(); lab.CommitTurn(lab.PrepareTurn("дроп с рейд-босса", "UNKNOWN", "NEUTRAL", CancellationToken.None), pack, CancellationToken.None);
            t = lab.CommitTurn(lab.PrepareTurn("босс", "UNKNOWN", "NEUTRAL", CancellationToken.None), pack, CancellationToken.None);
            Equal("GAME", (string)t.WorldHint);
            lab.Clear(); lab.CommitTurn(lab.PrepareTurn("начальник на работе", "UNKNOWN", "NEUTRAL", CancellationToken.None), pack, CancellationToken.None);
            t = lab.CommitTurn(lab.PrepareTurn("босс", "UNKNOWN", "NEUTRAL", CancellationToken.None), pack, CancellationToken.None);
            Equal("REAL", (string)t.WorldHint); Equal("босс", (string)t.UserText);
            lab.SetWorld("GAME"); p = lab.PrepareTurn("босс", "UNKNOWN", "NEUTRAL", CancellationToken.None);
            lab.SetWorld("MIXED"); Throws(() => lab.CommitTurn(p, pack, CancellationToken.None));
            p = lab.PrepareTurn("привет", "UNKNOWN", "NEUTRAL", CancellationToken.None);
            Throws(() => lab.CommitTurn(p, pack with { Fingerprint = new string('a', 64) }, CancellationToken.None));
            Equal("STALE_SOURCE", (string)lab.SourceStatus); Throws(() => lab.PrepareTurn("привет", "UNKNOWN", "NEUTRAL", CancellationToken.None));
        });
        Test("PSS-007 A limits reset no truncation and bounded editor transcript", () =>
        {
            dynamic lab = NewLab(pack);
            dynamic t = lab.CommitTurn(lab.PrepareTurn(new string('я', 257), "UNKNOWN", "NEUTRAL", CancellationToken.None), pack, CancellationToken.None);
            Equal("NO_PACK_MATCH", (string)t.PackStatus); Equal(257, ((string)t.UserText).Length); Equal("", (string)t.PackText);
            Throws(() => lab.PrepareTurn(new string('я', 1025), "UNKNOWN", "NEUTRAL", CancellationToken.None));
            lab.AddEditorNote("Локальная редакционная заметка"); True(((string)lab.Transcript).Contains("EDITOR_NOTE"));
            for (var i = 1; i < 200; i++) lab.CommitTurn(lab.PrepareTurn("пустая фраза", "UNKNOWN", "NEUTRAL", CancellationToken.None), pack, CancellationToken.None);
            Throws(() => lab.PrepareTurn("привет", "UNKNOWN", "NEUTRAL", CancellationToken.None));
            lab.Clear(); Equal(0, (int)lab.Turns.Count); Equal("", (string)lab.Transcript);
            t = lab.CommitTurn(lab.PrepareTurn("привет", "UNKNOWN", "NEUTRAL", CancellationToken.None), pack, CancellationToken.None);
            Equal("greet.base", (string)t.TemplateId);
        });
    }
    private static string MentorBody(bool question = true) => JsonSerializer.Serialize(new
    {
        worldHint = "UNKNOWN", needsClarification = question, question = question ? "Рейд-босс или начальник на работе?" : "",
        interpretations = new[] { "GAME: рейд", "REAL: работа" }, reason = "Смысл слова неоднозначен.", editorialSuggestion = "Различать контекст, не менять пак автоматически."
    }, WorkspaceStore.JsonOptions);
    private static async Task<dynamic> AskMentor(HttpClient http, StudioSettings settings, object request, CancellationToken token = default)
    {
        var method = typeof(LmStudioClient).GetMethod("AdviseDialogueAsync"); True(method != null);
        dynamic task = method!.Invoke(new LmStudioClient(http), [settings, "", request, token])!;
        return await task;
    }
    private static async Task Pss007B()
    {
        using var f = new Fixture(); var pack = new PackReader().Load(f.Module); var settings = new StudioSettings { HighFiveRoot = f.Module };
        await TestAsync("PSS-007 B opt-in one separate mentor question explicit confirmation and stale ticket", async () =>
        {
            dynamic lab = NewLab(pack); True(typeof(DialogueLabSession).GetMethod("BeginMentor") != null);
            lab.CommitTurn(lab.PrepareTurn("босс", "UNKNOWN", "NEUTRAL", CancellationToken.None), pack, CancellationToken.None);
            using var handler = new RecordingHandler(SemanticEnvelope(MentorBody())); using var http = new HttpClient(handler);
            Throws(() => lab.BeginMentor("YOU: босс", true, true)); Equal(0, handler.Calls);
            lab.SetMentorEnabled(true); Throws(() => lab.BeginMentor("YOU: босс", false, true));
            dynamic request = lab.BeginMentor("YOU: босс", true, true);
            dynamic result = await AskMentor(http, settings, (object)request);
            Equal(1, handler.Calls); True(handler.Payload!.Contains("json_schema") && !handler.Payload.Contains("\"tools\""));
            lab.AcceptMentor(result, pack, CancellationToken.None);
            True(((string)lab.Transcript).Contains("MENTOR / НАСТАВНИК")); True(!((string)lab.Transcript).Contains("PACK / ПАК\r\nРейд-босс"));
            Throws(() => lab.BeginMentor("YOU: босс", true, true));
            var revision = (long)lab.Revision; var transcript = (string)lab.Transcript;
            Throws(() => lab.ConfirmClarification("REAL", new string('я', 4000), pack));
            Equal("AUTO", (string)lab.World); Equal(revision, (long)lab.Revision); Equal(transcript, (string)lab.Transcript);
            True(lab.PendingClarification != null);
            lab.ConfirmClarification("REAL", "Редактор подтвердил: о работе.", pack);
            Equal("REAL", (string)lab.World); Equal("босс", (string)lab.Turns[0].UserText);
            lab.CommitTurn(lab.PrepareTurn("привет", "UNKNOWN", "NEUTRAL", CancellationToken.None), pack, CancellationToken.None);
            request = lab.BeginMentor("YOU: привет", true, true); result = await AskMentor(http, settings, (object)request);
            lab.SetWorld("MIXED"); Throws(() => lab.AcceptMentor(result, pack, CancellationToken.None)); Equal("STALE", (string)lab.AdviceStatus);
            lab.Clear(); True(!(bool)lab.MentorEnabled); Equal(2, (int)lab.ModelCalls);
        });
        await TestAsync("PSS-007 B strict JSON envelope errors cancellation sanitized no retry", async () =>
        {
            True(typeof(LmStudioClient).GetMethod("AdviseDialogueAsync") != null);
            var good = MentorBody();
            foreach (var content in new[] { "{}", good[..^1] + ",\"code\":\"run\"}", good.Replace("\"worldHint\":", "\"worldHint\":\"GAME\",\"worldHint\":"),
                good.Replace("UNKNOWN", "AUTO"), good.Replace("true", "false"), good.Replace("Смысл слова неоднозначен.", new string('x', 241)),
                good.Replace("Рейд-босс или начальник на работе?", "<script>run</script>"), "null", "{broken" })
            {
                dynamic lab = NewLab(pack); lab.SetMentorEnabled(true);
                lab.CommitTurn(lab.PrepareTurn("босс", "UNKNOWN", "NEUTRAL", CancellationToken.None), pack, CancellationToken.None);
                dynamic request = lab.BeginMentor("YOU: босс", true, true);
                using var handler = new RecordingHandler(SemanticEnvelope(content)); using var http = new HttpClient(handler);
                await ThrowsAsync(async () => await AskMentor(http, settings, (object)request)); Equal(1, handler.Calls);
                True(!((string)lab.Transcript).Contains("MENTOR / НАСТАВНИК"));
            }
            foreach (var envelope in new[] { SemanticEnvelope(good).Replace("stop", "length"),
                "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"secret\",\"tool_calls\":[]}}]}",
                "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"secret\",\"refusal\":\"private\"}}]}", new string('x', 1024 * 1024 + 1) })
            {
                dynamic lab = NewLab(pack); lab.SetMentorEnabled(true); lab.CommitTurn(lab.PrepareTurn("босс", "UNKNOWN", "NEUTRAL", CancellationToken.None), pack, CancellationToken.None);
                using var handler = new RecordingHandler(envelope); using var http = new HttpClient(handler);
                try { await AskMentor(http, settings, (object)lab.BeginMentor("YOU: босс", true, true)); True(false); }
                catch (LmStudioException e) { Equal(LmDiagnosticCode.BAD_RESPONSE, e.Diagnostic.Code); True(!e.ToString().Contains("secret") && !e.ToString().Contains("private")); }
                Equal(1, handler.Calls);
            }
            foreach (var status in new[] { System.Net.HttpStatusCode.Unauthorized, System.Net.HttpStatusCode.BadRequest, System.Net.HttpStatusCode.InternalServerError })
            {
                dynamic lab = NewLab(pack); lab.SetMentorEnabled(true); lab.CommitTurn(lab.PrepareTurn("босс", "UNKNOWN", "NEUTRAL", CancellationToken.None), pack, CancellationToken.None);
                using var handler = new RecordingHandler("PRIVATE_ERROR_BODY", status); using var http = new HttpClient(handler);
                try { await AskMentor(http, settings, (object)lab.BeginMentor("YOU: босс", true, true)); True(false); }
                catch (LmStudioException e) { True(!e.ToString().Contains("PRIVATE_ERROR_BODY")); }
                Equal(1, handler.Calls);
            }
            dynamic cancelLab = NewLab(pack); cancelLab.SetMentorEnabled(true); cancelLab.CommitTurn(cancelLab.PrepareTurn("босс", "UNKNOWN", "NEUTRAL", CancellationToken.None), pack, CancellationToken.None);
            dynamic cancelledRequest = cancelLab.BeginMentor("YOU: босс", true, true);
            using var cancel = new CancellationTokenSource(); cancel.Cancel(); using var cancelled = new HttpClient(new RecordingHandler(SemanticEnvelope(good)));
            await ThrowsAsync(async () => await AskMentor(cancelled, settings, (object)cancelledRequest, cancel.Token));
            using var timeout = new HttpClient(new FaultHandler(new TaskCanceledException("PRIVATE_PROMPT")));
            try { await AskMentor(timeout, settings, (object)cancelledRequest); True(false); }
            catch (LmStudioException e) { Equal(LmDiagnosticCode.TIMEOUT, e.Diagnostic.Code); True(!e.ToString().Contains("PRIVATE_PROMPT")); }
        });
        Test("PSS-007 B manual scoped lesson legacy session private save and twenty-call budget", () =>
        {
            dynamic lab = NewLab(pack); True(typeof(DialogueLabSession).GetMethod("CreateLesson") != null);
            using var ws = new WorkspaceStore(Path.Combine(f.Root, "lab-b"), f.Module);
            var c = CandidateOf(pack, "Вечер оставил время на беседу."); CandidateReview.Approve(c, pack, [], "Прочитано");
            ws.SaveSession(new SessionState { Candidates = [c], Lessons = ["Legacy v1"] });
            var before = File.ReadAllBytes(Path.Combine(ws.Root, "session.json"));
            var lesson = (EditorialLesson)lab.CreateLesson("Босс бывает игровым и реальным.", "greeting", "greet.reply", "UNKNOWN", "NEUTRAL", "ANY", pack);
            Equal(pack.Fingerprint, lesson.SourceFingerprint); lesson.ToRequest(pack, false);
            Throws(() => lab.CreateLesson("Заметка", "unknown", "greet.reply", "UNKNOWN", "NEUTRAL", "ANY", pack));
            True(before.SequenceEqual(File.ReadAllBytes(Path.Combine(ws.Root, "session.json")))); Equal(1, ws.LoadSession().Version); True(CandidateReview.IsCurrent(c));
            lab.SetMentorEnabled(true);
            for (var i = 0; i < 20; i++)
            {
                lab.CommitTurn(lab.PrepareTurn("босс", "UNKNOWN", "NEUTRAL", CancellationToken.None), pack, CancellationToken.None);
                dynamic request = lab.BeginMentor("YOU: босс", true, true); lab.FailMentor(request);
            }
            lab.CommitTurn(lab.PrepareTurn("босс", "UNKNOWN", "NEUTRAL", CancellationToken.None), pack, CancellationToken.None);
            Throws(() => lab.BeginMentor("YOU: босс", true, true)); Equal(20, (int)lab.ModelCalls);
            var storeType = typeof(PackSnapshot).Assembly.GetType("PhantomSemanticStudio.Core.DialogueLabStore"); True(storeType != null);
            var save = storeType!.GetMethod("Save")!; var path = (string)save.Invoke(null, [ws, lab, pack, CancellationToken.None])!;
            True(PathSafety.IsWithin(path, Path.Combine(ws.Root, "labs"))); True(new FileInfo(path).Length < 2 * 1024 * 1024);
            True(before.SequenceEqual(File.ReadAllBytes(Path.Combine(ws.Root, "session.json"))));
            using var archive = JsonDocument.Parse(File.ReadAllBytes(path)); Equal(1, archive.RootElement.GetProperty("Version").GetInt32());
            True(!File.ReadAllText(path).Contains("ApiKey"));
        });
    }
    private static dynamic Bridge(string method, params object[] args)
    {
        var type = typeof(PackSnapshot).Assembly.GetType("PhantomSemanticStudio.Core.CorpusDialogueBridge"); True(type != null);
        var member = type!.GetMethod(method); True(member != null);
        try { return member!.Invoke(null, args)!; }
        catch (System.Reflection.TargetInvocationException e) when (e.InnerException != null)
        { System.Runtime.ExceptionServices.ExceptionDispatchInfo.Capture(e.InnerException).Throw(); throw; }
    }
    private static Array ReviewedArray(params object[] values)
    {
        var type = typeof(PackSnapshot).Assembly.GetType("PhantomSemanticStudio.Core.ReviewedCorpusText"); True(type != null);
        var array = Array.CreateInstance(type!, values.Length); for (var i = 0; i < values.Length; i++) array.SetValue(values[i], i); return array;
    }
    private static string CorpusAdviceBody(dynamic request) => JsonSerializer.Serialize(new
    {
        items = ((System.Collections.IEnumerable)request.Items).Cast<dynamic>().Select((e, i) => new
        {
            refKey = "e" + (i + 1), reviewedTextHash = (string)e.ReviewedHash,
            operation = "RESTORE_RU_TRANSLIT", proposedRussianText = "Привет, как дела?", languageAssessment = "RU_TRANSLIT", reason = "Возможный транслит.", confidence = 80
        }).ToArray()
    }, WorkspaceStore.JsonOptions);
    private static async Task<dynamic> Transform(HttpClient http, StudioSettings settings, object request, CancellationToken token = default)
    {
        var method = typeof(LmStudioClient).GetMethod("TransformCorpusAsync"); True(method != null);
        dynamic task = method!.Invoke(new LmStudioClient(http), [settings, "", request, token])!; return await task;
    }
    private static async Task Pss007C()
    {
        using var f = new Fixture(); var pack = new PackReader().Load(f.Module);
        using var ws = new WorkspaceStore(Path.Combine(f.Root, "lab-c"), f.Module);
        ws.SaveSession(new SessionState()); var sessionBytes = File.ReadAllBytes(Path.Combine(ws.Root, "session.json"));
        var store = new CorpusStore(ws);
        var metadata = store.Import(CorpusZip(f.Root, ("synthetic.log", CorpusLines(
            "[01.01.22 00:00:01] SHOUT [PlayerA] privet user@example.test",
            "[01.01.22 00:00:02] PARTY [PlayerB] kak dela",
            "[01.01.22 00:00:03] TELL [PlayerA -> PlayerB] PRIVATE_SENTINEL_C"))));
        var rows = store.Query(metadata.Id, new CorpusQuery()).Rows;
        var dbPath = Path.Combine(ws.Root, "corpora", metadata.Id, "corpus.db"); var dbHash = TextRules.Hash(File.ReadAllBytes(dbPath));
        Test("PSS-007 C explicit detached public sanitized max20 no original leakage", () =>
        {
            dynamic snippets = Bridge("Detach", metadata, rows, true);
            Equal(2, (int)snippets.Count); Equal("SOURCE_MATERIAL_ONLY", (string)snippets[0].Status);
            var serialized = JsonSerializer.Serialize((object)snippets, WorkspaceStore.JsonOptions);
            True(!serialized.Contains("Original") && !serialized.Contains("PlayerA") && !serialized.Contains("PRIVATE_SENTINEL_C") && !serialized.Contains("user@example.test"));
            Equal(TextRules.Hash((string)snippets[0].Preview), (string)snippets[0].PreviewHash);
            Throws(() => Bridge("Detach", metadata, rows, false)); Throws(() => Bridge("Detach", metadata, Array.Empty<CorpusRecord>(), true));
            Throws(() => Bridge("Detach", metadata, Enumerable.Range(0, 21).Select(i => rows[0] with { Id = i.ToString("x64") }).ToArray(), true));
            Throws(() => Bridge("Detach", metadata, new[] { rows[0] with { Channel = "TELL" } }, true));
            Throws(() => Bridge("Review", snippets[0], "privet", "LATIN_IS_ALWAYS_RUSSIAN"));
            Equal("privet user@example.test", store.Query(metadata.Id, new CorpusQuery()).Rows[0].Original);
        });
        await TestAsync("PSS-007 C separate reviewed consent exact outbound max3 and immutable corpus", async () =>
        {
            dynamic snippets = Bridge("Detach", metadata, rows, true); dynamic lab = NewLab(pack);
            var reviewed = ReviewedArray((object)Bridge("Review", snippets[0], "privet [маска редактора]", "RU_TRANSLIT"),
                (object)Bridge("Review", snippets[1], "kak dela", "UNKNOWN"));
            True(typeof(DialogueLabSession).GetMethod("BeginCorpusTransform") != null);
            Throws(() => lab.BeginCorpusTransform((dynamic)reviewed, false, true, "RESTORE_RU_TRANSLIT"));
            Throws(() => lab.BeginCorpusTransform((dynamic)reviewed, true, false, "RESTORE_RU_TRANSLIT"));
            Equal(0, (int)lab.ModelCalls);
            var many = ReviewedArray(reviewed.GetValue(0)!, reviewed.GetValue(1)!, reviewed.GetValue(0)!, reviewed.GetValue(1)!);
            Throws(() => lab.BeginCorpusTransform((dynamic)many, true, true, "RESTORE_RU_TRANSLIT"));
            dynamic request = lab.BeginCorpusTransform((dynamic)reviewed, true, true, "RESTORE_RU_TRANSLIT");
            using var handler = new RecordingHandler(SemanticEnvelope(CorpusAdviceBody(request))); using var http = new HttpClient(handler);
            dynamic result = await Transform(http, new StudioSettings { HighFiveRoot = f.Module }, (object)request);
            lab.AcceptCorpusTransform(result, pack, CancellationToken.None); Equal(1, handler.Calls); Equal(2, ((IReadOnlyList<CorpusSuggestion>)lab.CorpusSuggestions).Count);
            using var payload = JsonDocument.Parse(handler.Payload!);
            var userText = payload.RootElement.GetProperty("messages")[1].GetProperty("content").GetString()!;
            using var user = JsonDocument.Parse(userText);
            Equal("privet [маска редактора]", user.RootElement.GetProperty("excerpts")[0].GetProperty("text").GetString()!);
            True(!userText.Contains("user@example.test") && !userText.Contains("PlayerA") && !userText.Contains("PRIVATE_SENTINEL_C"));
            Equal("", (string)lab.Transcript); True(!(bool)lab.MentorEnabled);
            Equal(dbHash, TextRules.Hash(File.ReadAllBytes(dbPath))); True(sessionBytes.SequenceEqual(File.ReadAllBytes(Path.Combine(ws.Root, "session.json"))));
        });
        await TestAsync("PSS-007 C malformed keyed suggestions stale and cancel never applied", async () =>
        {
            dynamic snippets = Bridge("Detach", metadata, rows, true);
            var reviewed = ReviewedArray((object)Bridge("Review", snippets[0], "privet", "RU_TRANSLIT"), (object)Bridge("Review", snippets[1], "kak dela", "UNKNOWN"));
            var settings = new StudioSettings { HighFiveRoot = f.Module };
            foreach (var mutation in new Func<string, string>[] { s => "{}", s => s.Replace("e2", "e1"), s => s.Replace("e1", "missing"),
                s => s.Replace("\"confidence\": 80", "\"confidence\": 101"), s => s.Replace("\"operation\":", "\"operation\": \"UNKNOWN\", \"operation\":"),
                s => s.Replace("Привет, как дела?", "<script>run</script>"), s => s.Replace((string)((dynamic)reviewed.GetValue(0)!).ReviewedHash, new string('0', 64)) })
            {
                dynamic lab = NewLab(pack); dynamic request = lab.BeginCorpusTransform((dynamic)reviewed, true, true, "RESTORE_RU_TRANSLIT");
                using var handler = new RecordingHandler(SemanticEnvelope(mutation(CorpusAdviceBody(request)))); using var http = new HttpClient(handler);
                await ThrowsAsync(async () => await Transform(http, settings, (object)request)); Equal(1, handler.Calls); Equal(0, ((IReadOnlyList<CorpusSuggestion>)lab.CorpusSuggestions).Count);
            }
            dynamic stale = NewLab(pack); dynamic ticket = stale.BeginCorpusTransform((dynamic)reviewed, true, true, "RESTORE_RU_TRANSLIT");
            using var valid = new HttpClient(new RecordingHandler(SemanticEnvelope(CorpusAdviceBody(ticket)))); dynamic result = await Transform(valid, settings, (object)ticket);
            stale.SetWorld("REAL"); Throws(() => stale.AcceptCorpusTransform(result, pack, CancellationToken.None)); Equal(0, ((IReadOnlyList<CorpusSuggestion>)stale.CorpusSuggestions).Count);
            dynamic cancelled = NewLab(pack); ticket = cancelled.BeginCorpusTransform((dynamic)reviewed, true, true, "RESTORE_RU_TRANSLIT");
            using var cancel = new CancellationTokenSource(); cancel.Cancel();
            await ThrowsAsync(async () => await Transform(valid, settings, (object)ticket, cancel.Token)); Equal(0, ((IReadOnlyList<CorpusSuggestion>)cancelled.CorpusSuggestions).Count);
            foreach (var text in new[] { "pvp", "party", "bonjour", "den exoume", "hola" })
                Equal("EN_OR_OTHER", CorpusLanguageTriage.Classify(text).Language);
            Equal(dbHash, TextRules.Hash(File.ReadAllBytes(dbPath))); Equal(pack.Fingerprint, new PackReader().Load(f.Module).Fingerprint);
            True(sessionBytes.SequenceEqual(File.ReadAllBytes(Path.Combine(ws.Root, "session.json"))));
        });
    }
}
