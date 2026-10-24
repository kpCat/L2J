using System.Net;
using System.Text;
using System.Text.Json;
using PhantomSemanticStudio.Core;

internal static partial class Program
{
    private static async Task Batch081()
    {
        await TestAsync("081 durable 100 drafts pause after two chunks and reopen resumes without duplicate commit", async () =>
        {
            using var f = new Fixture(); var pack = new PackReader().Load(f.Module); var settings = BatchSettings(f);
            var parameters = BatchParameters(100) with { Voice = "direct", Facets = new() { Sarcasm = 75, Swagger = 70, Humor = 40, Roughness = 100 } };
            var path = Path.Combine(f.Root, "batch"); var handler = new BatchHandler();
            using var http = new HttpClient(handler); var client = new LmStudioClient(http);
            string jobId;
            using (var store = new WorkspaceStore(path, f.Module))
            {
                var session = store.LoadSession(); var engine = new BatchGenerationService(store, session, client);
                jobId = engine.Start(pack, settings, parameters).Id;
                var paused = await engine.RunAsync(pack, settings, parameters, "memory-only-secret", new BatchProgress(j => { if (j.NextChunk == 2) engine.RequestPause(); }));
                Equal("PAUSED", paused.Status); Equal(40, paused.Accepted); Equal(40, paused.Attempted); Equal(2, paused.RequestCount);
                Equal(40, store.LoadSession().Candidates.Count); Equal(2, handler.Calls);
            }
            using (var store = new WorkspaceStore(path, f.Module))
            {
                var session = store.LoadSession(); var saved = session.Candidates.Select(c => c.Id).ToArray();
                var engine = new BatchGenerationService(store, session, client); Equal(jobId, engine.Current!.Id);
                var done = await engine.RunAsync(pack, settings, parameters, "memory-only-secret");
                Equal("COMPLETED", done.Status); Equal(100, done.Accepted); Equal(100, done.Attempted); Equal(0, done.Rejected); Equal(5, done.NextChunk);
                Equal(5, handler.Calls); Equal(1, handler.MaximumConcurrent); Equal("20,20,20,20,20", string.Join(',', handler.Counts));
                True(saved.All(id => session.Candidates.Any(c => c.Id == id))); Equal(100, session.Candidates.Select(c => c.Id).Distinct().Count());
                True(session.Candidates.All(c => c.Status == "DRAFT" && c.ApprovedFingerprint == "" && c.ReviewedAtUtc == null
                    && c.SourceFingerprint == pack.Fingerprint && c.ModelId == settings.ModelId && c.Voice == "direct"
                    && c.EditorialMetadata!.Scope == "EDITORIAL_ONLY" && c.EditorialMetadata.Facets.Sarcasm == 75));
                True(handler.Instructions.All(s => s.Contains("EDITORIAL_ONLY", StringComparison.Ordinal) && s.Contains("\"sarcasm\":75", StringComparison.Ordinal)
                    && s.Contains("direct", StringComparison.Ordinal)));
                Equal("COMPLETED", (await engine.RunAsync(pack, settings, parameters, "")).Status); Equal(5, handler.Calls);
                var candidate = session.Candidates[0]; CandidateReview.Approve(candidate, pack, session.Candidates, "Проверено вручную");
                True(CandidateReview.IsCurrent(candidate)); var advice = SemanticDuplicateScout.CandidateFingerprint(candidate);
                candidate.EditorialMetadata = candidate.EditorialMetadata! with { Facets = candidate.EditorialMetadata.Facets with { Humor = 41 } };
                True(!CandidateReview.IsCurrent(candidate)); True(advice != SemanticDuplicateScout.CandidateFingerprint(candidate));
                CandidateReview.Edit(candidate, candidate.Text); Equal("DRAFT", candidate.Status);
                True(!File.ReadAllText(Path.Combine(path, "session.json")).Contains("memory-only-secret", StringComparison.Ordinal));
                True(pack.Files.SequenceEqual(new PackReader().Load(f.Module).Files));
            }
        });
        await TestAsync("081 exact Unicode dedupe and HTTP failures stop with honest bounded counters", async () =>
        {
            using var f = new Fixture(); var pack = new PackReader().Load(f.Module); var settings = BatchSettings(f);
            var texts = new[] { "ПРИВЕТ!!!", "abc 123", "Очередная ёлка", "ОЧЕРЕДНАЯ ЕЛКА!!!", new string('я', 181), "Ветер донёс далёкий звон." };
            using (var store = new WorkspaceStore(Path.Combine(f.Root, "dedupe"), f.Module))
            {
                var prior = CandidateOf(pack, "ＡＢＣ １２３"); prior.Status = "REJECTED";
                var session = new SessionState { Candidates = [prior] }; store.SaveSession(session);
                using var http = new HttpClient(new BatchHandler((_, _) => Task.FromResult(BatchEnvelope(texts))));
                var engine = new BatchGenerationService(store, session, new LmStudioClient(http)); var p = BatchParameters(6);
                engine.Start(pack, settings, p); var done = await engine.RunAsync(pack, settings, p, "");
                Equal("SHORTAGE", done.Status); Equal(6, done.Attempted); Equal(2, done.Accepted); Equal(4, done.Rejected); Equal(3, done.Deduplicated);
                Equal(3, store.LoadSession().Candidates.Count); Equal(1, done.RequestCount); True(session.Candidates.Skip(1).All(c => c.Status == "DRAFT"));
            }
            using (var mixedStore = new WorkspaceStore(Path.Combine(f.Root, "mixed"), f.Module))
            {
                var mixed = new SessionState(); using var mixedHttp = new HttpClient(new BatchHandler((_, _) => Task.FromResult(BatchEnvelope(
                    ["Есть время поболтать у ворот?", "У ворот сегодня необычно тихо."], "PATTERN", "TEMPLATE"))));
                var mixedEngine = new BatchGenerationService(mixedStore, mixed, new LmStudioClient(mixedHttp));
                var p = BatchParameters(2) with { Mode = GenerationMode.MIXED, Voice = "direct" }; mixedEngine.Start(pack, settings, p);
                Equal("COMPLETED", (await mixedEngine.RunAsync(pack, settings, p, "")).Status);
                Equal("", mixed.Candidates.Single(c => c.Kind == "PATTERN").Voice); Equal("direct", mixed.Candidates.Single(c => c.Kind == "TEMPLATE").Voice);
            }
            foreach (var status in new[] { HttpStatusCode.BadRequest, HttpStatusCode.Unauthorized, HttpStatusCode.TooManyRequests, HttpStatusCode.ServiceUnavailable })
                await BatchFailure(f, pack, settings, new BatchHandler((_, _) => Task.FromResult(new HttpResponseMessage(status) { Content = new StringContent("private-response-secret") })), "FAILED_JOB");
            foreach (var body in new[] { "{", "{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"{}\"}}]}",
                "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"refusal\":\"no\",\"content\":null}}]}" })
                await BatchFailure(f, pack, settings, new BatchHandler((_, _) => Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK) { Content = new StringContent(body) })), "FAILED_JOB");
            await BatchFailure(f, pack, settings, new BatchHandler((_, _) => Task.FromException<HttpResponseMessage>(new TaskCanceledException("private-response-secret"))), "FAILED_JOB");
            var entered = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
            var delayed = new BatchHandler(async (_, token) => { entered.SetResult(); await Task.Delay(Timeout.Infinite, token); throw new AssertionFailure("Cancellation expected"); });
            using var cancelStore = new WorkspaceStore(Path.Combine(f.Root, "cancel"), f.Module); var cancelSession = new SessionState();
            using var cancelHttp = new HttpClient(delayed); var cancelEngine = new BatchGenerationService(cancelStore, cancelSession, new LmStudioClient(cancelHttp));
            var parameters = BatchParameters(100); cancelEngine.Start(pack, settings, parameters);
            var running = cancelEngine.RunAsync(pack, settings, parameters, ""); await entered.Task.WaitAsync(TimeSpan.FromSeconds(5)); cancelEngine.RequestCancel();
            var cancelled = await running.WaitAsync(TimeSpan.FromSeconds(5)); Equal("CANCELLED", cancelled.Status); Equal(0, cancelled.Accepted); Equal(0, cancelled.Attempted);
            Equal(0, cancelStore.LoadSession().Candidates.Count); Equal(1, cancelled.RequestCount); Equal(1, delayed.Calls);
            cancelEngine.Start(pack, settings, parameters, true); cancelEngine.RequestPause();
            var earlyPause = await cancelEngine.RunAsync(pack, settings, parameters, ""); Equal("PAUSED", earlyPause.Status); Equal(0, earlyPause.RequestCount); Equal(1, delayed.Calls);
            True(pack.Files.SequenceEqual(new PackReader().Load(f.Module).Files));
        });
        await TestAsync("081 invalid parameters drift corrupted jobs and failed commit cannot advance accepted state", async () =>
        {
            using var f = new Fixture(); var pack = new PackReader().Load(f.Module); var settings = BatchSettings(f);
            using var store = new WorkspaceStore(Path.Combine(f.Root, "guard"), f.Module); var session = new SessionState();
            using var http = new HttpClient(new BatchHandler()); var engine = new BatchGenerationService(store, session, new LmStudioClient(http)); var p = BatchParameters(1);
            foreach (var bad in new[] { p with { TargetCount = 0 }, p with { TargetCount = 501 }, p with { Act = "unknown.reply" }, p with { Voice = "DIRECT" },
                p with { Mode = GenerationMode.PATTERN, Voice = "direct" }, p with { Facets = new() { Aggression = -1 } }, p with { Facets = new() { Gossip = 101 } } })
                Throws(() => engine.Start(pack, settings, bad));
            Throws(() => JsonSerializer.Deserialize<EditorialFacets>("{\"unknown\":1}", WorkspaceStore.JsonOptions));
            Throws(() => new WorkspaceStore(Path.Combine(f.Module, "unsafe"), f.Module));
            foreach (var change in new[] { "model", "endpoint", "temperature", "tokens", "timeout", "parameters", "source" })
            {
                engine.Start(pack, settings, p, true); var changed = BatchSettings(f); var requested = p;
                switch (change)
                {
                    case "model": changed.ModelId = "other-model"; break;
                    case "endpoint": changed.Endpoint = "http://localhost:1235/v1"; break;
                    case "temperature": changed.Temperature = 0.9; break;
                    case "tokens": changed.MaxTokens = 2048; break;
                    case "timeout": changed.TimeoutSeconds = 60; break;
                    case "parameters": requested = p with { Facets = new() { Humor = 1 } }; break;
                    case "source": File.AppendAllText(f.Segment, "\n"); break;
                }
                var blocked = await engine.RunAsync(pack, changed, requested, ""); Equal("BLOCKED_JOB", blocked.Status); Equal(0, blocked.Accepted);
                if (change == "source") { File.WriteAllText(f.Segment, File.ReadAllText(f.Segment).TrimEnd()); pack = new PackReader().Load(f.Module); }
            }
            engine.Start(pack, settings, p, true);
            var extra = Path.Combine(f.Module, "dist/game/data/phantoms/semantic/custom/my-slang.xml"); Directory.CreateDirectory(Path.GetDirectoryName(extra)!);
            File.WriteAllText(extra, "<slang version=\"1\"/>");
            Equal("BLOCKED_JOB", (await engine.RunAsync(pack, settings, p, "")).Status); File.Delete(extra);
            engine.Start(pack, settings, p, true); var valid = File.ReadAllBytes(Path.Combine(store.Root, "session.json"));
            var corrupted = Encoding.UTF8.GetString(valid).Replace("\"Accepted\": 0", "\"Accepted\": 501");
            File.WriteAllText(Path.Combine(store.Root, "session.json"), corrupted); Throws(() => store.LoadSession()); File.WriteAllBytes(Path.Combine(store.Root, "session.json"), valid);
            var original = CandidateOf(pack, "До начала партии всё спокойно."); session.Candidates.Add(original); store.SaveSession(session);
            using var failureHttp = new HttpClient(new BatchHandler((_, _) => { original.Rationale = new string('r', 16 * 1024 * 1024); return Task.FromResult(BatchEnvelope(["За холмом показалась луна."])); }));
            var failedCommit = new BatchGenerationService(store, session, new LmStudioClient(failureHttp));
            await ThrowsAsync(() => failedCommit.RunAsync(pack, settings, p, "")); Equal(1, session.Candidates.Count); Equal(0, session.BatchJob!.Accepted); Equal(0, session.BatchJob.NextChunk);
            Equal(1, store.LoadSession().Candidates.Count); Equal(0, store.LoadSession().BatchJob!.Accepted); True(!Directory.GetFiles(store.Root, "*.tmp-*").Any());
            True(pack.Files.SequenceEqual(new PackReader().Load(f.Module).Files));
        });
    }

    private static StudioSettings BatchSettings(Fixture f) => new() { HighFiveRoot = f.Module, ModelId = "batch-test-model", TimeoutSeconds = 30 };
    private static BatchGenerationParameters BatchParameters(int target) => new() { Topic = "greeting", Act = "greet.reply", Instruction = "Короткие разнообразные разговорные ответы.", TargetCount = target };
    private static async Task BatchFailure(Fixture f, PackSnapshot pack, StudioSettings settings, BatchHandler handler, string status)
    {
        using var store = new WorkspaceStore(Path.Combine(f.Root, "failure-" + Guid.NewGuid().ToString("N")), f.Module); var session = new SessionState();
        using var http = new HttpClient(handler); var engine = new BatchGenerationService(store, session, new LmStudioClient(http)); var p = BatchParameters(100);
        engine.Start(pack, settings, p); var stopped = await engine.RunAsync(pack, settings, p, "private-request-secret");
        Equal(status, stopped.Status); Equal(0, stopped.Accepted); Equal(0, stopped.Attempted); Equal(1, stopped.RequestCount); Equal(1, handler.Calls);
        Equal(0, store.LoadSession().Candidates.Count); var saved = File.ReadAllText(Path.Combine(store.Root, "session.json"));
        True(!saved.Contains("private-response-secret", StringComparison.Ordinal) && !saved.Contains("private-request-secret", StringComparison.Ordinal));
        Equal(status, (await engine.RunAsync(pack, settings, p, "")).Status); Equal(1, handler.Calls);
    }
    private static HttpResponseMessage BatchEnvelope(IEnumerable<string> texts, params string[] kinds) => new(HttpStatusCode.OK)
    {
        Content = new StringContent(JsonSerializer.Serialize(new { choices = new[] { new { finish_reason = "stop", message = new { content = JsonSerializer.Serialize(new
        { items = texts.Select((text, i) => new { kind = kinds.Length == 0 ? "TEMPLATE" : kinds[i], text, reason = "Новый редакционный черновик" }) }) } } } }), Encoding.UTF8, "application/json")
    };
    private sealed class BatchProgress(Action<BatchJobState> report) : IProgress<BatchJobState> { public void Report(BatchJobState value) => report(value); }
    private sealed class BatchHandler(Func<int, CancellationToken, Task<HttpResponseMessage>>? respond = null) : HttpMessageHandler
    {
        public int Calls { get; private set; }
        public int MaximumConcurrent { get; private set; }
        public List<int> Counts { get; } = [];
        public List<string> Instructions { get; } = [];
        private int concurrent;
        protected override async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            var active = Interlocked.Increment(ref concurrent); MaximumConcurrent = Math.Max(MaximumConcurrent, active);
            try
            {
                var payload = await request.Content!.ReadAsStringAsync(cancellationToken); using var doc = JsonDocument.Parse(payload);
                using var user = JsonDocument.Parse(doc.RootElement.GetProperty("messages")[1].GetProperty("content").GetString()!);
                var count = user.RootElement.GetProperty("RequestedCount").GetInt32(); Counts.Add(count); True(count is >= 1 and <= 20);
                Instructions.Add(user.RootElement.GetProperty("Instruction").GetString()!); Calls++;
                return respond == null ? BatchEnvelope(Enumerable.Range((Calls - 1) * 20 + 1, count).Select(i => "Дорожная заметка номер " + i))
                    : await respond(Calls, cancellationToken);
            }
            finally { Interlocked.Decrement(ref concurrent); }
        }
    }
}
