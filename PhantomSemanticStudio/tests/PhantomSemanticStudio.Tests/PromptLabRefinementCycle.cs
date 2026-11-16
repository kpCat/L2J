using System.Text.Json;
using System.Text.Json.Serialization;
using PhantomSemanticStudio.Core;

internal static partial class Program
{
    [JsonUnmappedMemberHandling(JsonUnmappedMemberHandling.Disallow)]
    private sealed record RefinementCycleSpec(string Schema, string Reviewer, string BaselineSha256, string BatchComment,
        PromptLabRating[] Ratings, PromptLabApiOptions Refinement);
    private sealed record RefinementCyclePlan(string SpecHash, string Model, string Workspace, string Source, string CoreHash, string RunnerHash,
        string RegistryHash, string BaselineId, string BaselineHash, string VersionId, string VersionHash, string AcceptedId,
        string FeedbackId, string FeedbackHash, string RefinementId, string RequestHash);
    private sealed record RefinementCycleComparison(string RefinementId, string OutcomeHash, string ProposedId, string ProposedHash,
        string ComparisonId, string RequestHash);

    // A diagnostic route of the existing runner, never an approval or production-generation route.
    private static async Task<int> PromptLabRefinementCycle(string[] args, Func<int, HttpClient>? httpFactory = null)
    {
        if (args.Length != 7 || !int.TryParse(args[6], out var limit) || limit is < 0 or > 2)
            throw new InvalidDataException("Нужны owned workspace, source, exact model, baseline ID, feedback spec и limit0..2.");
        var bytes = V4Rules.ReadBytes(PathSafety.Canonical(args[5]), 64 * 1024); var specHash = V4Rules.Hash(bytes);
        var spec = JsonSerializer.Deserialize<RefinementCycleSpec>(bytes, WorkspaceStore.JsonOptions) ?? throw new InvalidDataException("REFINEMENT_SPEC_EMPTY");
        V4Rules.Require(spec.Schema == "pw-diagnostic-refinement/1" && spec.Reviewer == "CODEX_PROVISIONAL_NOT_OWNER"
            && !string.IsNullOrWhiteSpace(spec.BatchComment) && spec.BatchComment.Length <= 7000 && spec.Ratings != null
            && spec.Ratings.All(r => r != null && r.Vote == "NONE") && spec.Refinement != null, "REFINEMENT_DIAGNOSTIC_ONLY");
        spec.Refinement!.Validate(); httpFactory ??= LmStudioClient.CreateHttpClient;
        using var workspace = new WorkspaceStore(args[1], args[2]); var store = new PromptLabStore(workspace);
        using var lab = new PromptLabSession(store, httpFactory);
        string RunFile(string id, string name) => Path.Combine(store.Root, "runs", id, name + ".json");
        static string Hash<T>(T value) => V4Rules.Hash(JsonSerializer.SerializeToUtf8Bytes(value, PromptProfiles.CompactJson));
        var baseline = store.GetRun(args[4]); var original = store.GetVersion(baseline.VersionId);
        V4Rules.Require(baseline.State == "COMPLETE" && baseline.Purpose == "GENERATION" && baseline.ModelId == args[3]
            && baseline.ModelContext == 65536 && FileSha(RunFile(baseline.Id, "outcome")) == spec.BaselineSha256, "REFINEMENT_BASELINE_DRIFT");
        var folder = Path.Combine(store.Root, "diagnostic-cycles", specHash);
        string Local(string name) { workspace.Check(); var path = PathSafety.ResolveRelative(folder, name + ".json"); PathSafety.AssertNoReparsePoints(path); return path; }
        void Save<T>(string name, T value)
        {
            var path = Local(name); var data = JsonSerializer.SerializeToElement(value, PromptProfiles.CompactJson);
            var envelope = new { schema = "pw-diagnostic-cycle-record/1", sha256 = Hash(data), data };
            Directory.CreateDirectory(folder); var temporary = Local("write-" + Guid.NewGuid().ToString("N"));
            try
            {
                using (var sink = new FileStream(temporary, FileMode.CreateNew, FileAccess.Write, FileShare.None))
                { sink.Write(JsonSerializer.SerializeToUtf8Bytes(envelope, WorkspaceStore.JsonOptions)); sink.Flush(true); }
                workspace.Check(); PathSafety.AssertNoReparsePoints(path); File.Move(temporary, path, false);
            }
            finally { if (File.Exists(temporary)) File.Delete(temporary); }
        }
        T Read<T>(string name)
        {
            using var doc = JsonDocument.Parse(V4Rules.ReadBytes(Local(name), 128 * 1024)); var root = doc.RootElement; var data = root.GetProperty("data");
            V4Rules.Require(root.GetProperty("schema").GetString() == "pw-diagnostic-cycle-record/1" && root.GetProperty("sha256").GetString() == Hash(data), "REFINEMENT_CYCLE_HASH");
            return data.Deserialize<T>(WorkspaceStore.JsonOptions) ?? throw new InvalidDataException("REFINEMENT_CYCLE_EMPTY");
        }
        bool Uncertain() => store.ListRuns().Any(r => r.State is "INDETERMINATE" or "CANCELLED" || r.State == "ISSUED" && store.GetTransport(r.Id) == null);
        if (Uncertain()) { Console.WriteLine("STOP_REVIEW_REQUIRED uncertain request; no HTTP"); return 3; }
        var marker = "[CODEX_PROVISIONAL_NOT_OWNER:" + specHash + "] ";
        bool Diagnostic(PromptLabFeedback f) => f.BatchComment.StartsWith("[CODEX_PROVISIONAL_NOT_OWNER:", StringComparison.Ordinal) && f.Ratings.All(r => r.Vote == "NONE");
        RefinementCyclePlan plan;
        if (!File.Exists(Local("plan")))
        {
            var latest = store.LatestFeedback(baseline.Id);
            if (latest != null && !Diagnostic(latest)) { Console.WriteLine("STOP_OWNER_FEEDBACK_PRESERVED"); return 3; }
            var matches = store.ListFeedback(baseline.Id).Where(f => f.BatchComment == marker + spec.BatchComment && Hash(f.Ratings) == Hash(spec.Ratings)).ToArray();
            V4Rules.Require(matches.Length <= 1, "REFINEMENT_FEEDBACK_AMBIGUOUS");
            var feedback = matches.SingleOrDefault() ?? store.SaveFeedback(baseline.Id, spec.Ratings!, marker + spec.BatchComment);
            if (store.LatestFeedback(baseline.Id)!.Id != feedback.Id) { Console.WriteLine("STOP_NEWER_FEEDBACK_PRESERVED"); return 3; }
            var orphans = store.ListRuns().Where(r => r.Purpose == "REFINEMENT" && r.InputContext.Contains(marker, StringComparison.Ordinal)).ToArray();
            V4Rules.Require(orphans.Length <= 1, "REFINEMENT_ORPHAN_AMBIGUOUS");
            var run = orphans.SingleOrDefault() ?? lab.QueueRefinement(baseline.Id, spec.Refinement);
            plan = new(specHash, args[3], workspace.Root, PathSafety.Canonical(args[2]), FileSha(typeof(PromptLabStore).Assembly.Location), FileSha(typeof(Program).Assembly.Location),
                store.EffectiveRegistry().Hash, baseline.Id, spec.BaselineSha256, original.Id, store.VersionHash(original.Id), store.GetProfile(original.ProfileId).AcceptedVersionId,
                feedback.Id, FileSha(Path.Combine(store.Root, "runs", baseline.Id, "feedback", feedback.Id + ".json")), run.Id, FileSha(RunFile(run.Id, "request")));
            if (!File.Exists(Local("spec"))) Save("spec", spec);
            V4Rules.Require(Hash(Read<RefinementCycleSpec>("spec")) == Hash(spec), "REFINEMENT_SPEC_COPY_DRIFT"); Save("plan", plan);
        }
        else plan = Read<RefinementCyclePlan>("plan");
        V4Rules.Require(plan.SpecHash == specHash && plan.Model == args[3] && plan.Workspace == workspace.Root && plan.Source == PathSafety.Canonical(args[2])
            && plan.CoreHash == FileSha(typeof(PromptLabStore).Assembly.Location) && plan.RunnerHash == FileSha(typeof(Program).Assembly.Location)
            && plan.RegistryHash == store.EffectiveRegistry().Hash && plan.BaselineId == baseline.Id && plan.BaselineHash == spec.BaselineSha256
            && plan.VersionId == original.Id && plan.VersionHash == store.VersionHash(original.Id) && Hash(Read<RefinementCycleSpec>("spec")) == Hash(spec)
            && plan.FeedbackHash == FileSha(Path.Combine(store.Root, "runs", baseline.Id, "feedback", plan.FeedbackId + ".json")), "REFINEMENT_PLAN_DRIFT");
        var refinement = store.GetRun(plan.RefinementId);
        V4Rules.Require(FileSha(RunFile(refinement.Id, "request")) == plan.RequestHash && refinement.Purpose == "REFINEMENT" && refinement.VersionId == original.Id
            && refinement.BaselineRunId == baseline.Id && Hash(refinement.ApiOptions) == Hash(spec.Refinement) && refinement.InputContext.Contains(marker, StringComparison.Ordinal)
            && refinement.InputContext.Contains(plan.FeedbackId, StringComparison.Ordinal), "REFINEMENT_REQUEST_DRIFT");
        bool Unchanged() => store.GetProfile(original.ProfileId).AcceptedVersionId == plan.AcceptedId && store.LatestFeedback(baseline.Id)?.Id == plan.FeedbackId;
        var attempts = 0; var stopped = false;
        using var cancel = new CancellationTokenSource(); ConsoleCancelEventHandler onCancel = (_, e) => { e.Cancel = true; cancel.Cancel(); };
        Console.CancelKeyPress += onCancel;
        async Task<PromptLabRun> Execute(PromptLabRun run)
        {
            if (run.CompletedAtUtc.HasValue) return run;
            if (store.GetTransport(run.Id) == null)
            {
                if (run.State != "QUEUED" || !Unchanged() || Uncertain()) { stopped = true; Console.WriteLine("STOP_REVIEW_REQUIRED no HTTP"); return run; }
                if (attempts >= limit) return run;
                var settings = new StudioSettings { HighFiveRoot = args[2], ModelId = args[3] }; var key = Environment.GetEnvironmentVariable("LM_STUDIO_API_KEY") ?? "";
                using var timeout = CancellationTokenSource.CreateLinkedTokenSource(cancel.Token); timeout.CancelAfter(TimeSpan.FromSeconds(spec.Refinement.ConnectionTimeoutSeconds));
                try
                {
                    using var http = httpFactory(spec.Refinement.ConnectionTimeoutSeconds);
                    var inventory = await new LmStudioClient(http).ProbeCorpusModelsAsync(settings, key, timeout.Token);
                    V4Rules.Require(inventory.Loaded.Count(m => m.InstanceId == args[3] && m.ContextLength == 65536) == 1, "EXACT_LOADED_INSTANCE_CONTEXT_REQUIRED");
                }
                catch (Exception error) when (error is InvalidDataException or HttpRequestException or LmStudioException or OperationCanceledException)
                { Console.WriteLine("STOP_MODEL_PROBE " + error.GetType().Name + "; queued work preserved"); stopped = true; return run; }
                attempts++;
            }
            var outcome = await lab.ResumeAsync(run.Id, new StudioSettings { HighFiveRoot = args[2], ModelId = args[3] },
                Environment.GetEnvironmentVariable("LM_STUDIO_API_KEY") ?? "", args[3], cancel.Token, new PromptLabConsoleProgress(), requireExactInstance: true);
            Console.WriteLine("CYCLE " + outcome.Purpose + " " + outcome.Id + " " + outcome.State + " " + outcome.Failure);
            if (outcome.State != "COMPLETE") stopped = true;
            return outcome;
        }
        PromptLabRun? comparison = null;
        try
        {
            refinement = await Execute(refinement);
            if (refinement.State == "COMPLETE")
            {
                var proposal = store.GetVersion(refinement.ProposedVersionId);
                V4Rules.Require(proposal.ParentId == original.Id && proposal.ProfileId == original.ProfileId && proposal.ProposedFromRunId == refinement.Id, "REFINEMENT_PROPOSAL_DRIFT");
                RefinementCycleComparison binding;
                if (File.Exists(Local("comparison"))) binding = Read<RefinementCycleComparison>("comparison");
                else
                {
                    var orphans = store.ListRuns().Where(r => r.Purpose == "GENERATION" && r.BaselineRunId == baseline.Id && r.VersionId == proposal.Id).ToArray();
                    V4Rules.Require(orphans.Length <= 1, "COMPARISON_ORPHAN_AMBIGUOUS");
                    if (orphans.Length == 0 && !Unchanged()) { Console.WriteLine("STOP_OWNER_SELECTION_OR_FEEDBACK_CHANGED"); return 3; }
                    comparison = orphans.SingleOrDefault() ?? lab.QueueComparison(baseline.Id, proposal.Id);
                    binding = new(refinement.Id, FileSha(RunFile(refinement.Id, "outcome")), proposal.Id, store.VersionHash(proposal.Id), comparison.Id, FileSha(RunFile(comparison.Id, "request")));
                    Save("comparison", binding);
                }
                comparison = store.GetRun(binding.ComparisonId);
                V4Rules.Require(binding.RefinementId == refinement.Id && binding.OutcomeHash == FileSha(RunFile(refinement.Id, "outcome")) && binding.ProposedId == proposal.Id
                    && binding.ProposedHash == store.VersionHash(proposal.Id) && binding.RequestHash == FileSha(RunFile(comparison.Id, "request"))
                    && comparison.VersionId == proposal.Id && comparison.Purpose == "GENERATION" && comparison.BaselineRunId == baseline.Id
                    && Hash(comparison.ApiOptions) == Hash(baseline.ApiOptions ?? original.Generation)
                    && Hash(comparison.Scenario! with { Components = baseline.Scenario?.Components }) == Hash(baseline.Scenario), "COMPARISON_BINDING_DRIFT");
                comparison = await Execute(comparison);
            }
            else if (refinement.CompletedAtUtc.HasValue) stopped = true;
            Save("result-" + Guid.NewGuid().ToString("N"), new { plan.RefinementId, RefinementState = refinement.State, refinement.ProposedVersionId,
                ComparisonId = comparison?.Id, ComparisonState = comparison?.State, Rows = comparison?.Rows, NewAttempts = attempts, Approved = 0,
                Reviewer = spec.Reviewer, OwnerFeedback = "NOT_OWNER_RATING", AcceptedId = store.GetProfile(original.ProfileId).AcceptedVersionId });
            Console.WriteLine("CYCLE_STATE refinement=" + refinement.State + "; comparison=" + comparison?.State + "; newAttempts=" + attempts + "; approved=0");
            return stopped || cancel.IsCancellationRequested ? 3 : 0;
        }
        finally { Console.CancelKeyPress -= onCancel; }
    }

    private static async Task PromptLabDurableCycleTests()
    {
        await TestAsync("001 queued refinement and comparison persist before HTTP and preserve unvoted feedback and accepted version", async () =>
        {
            using var f = new Fixture(); using var workspace = new WorkspaceStore(Path.Combine(f.Root, "workspace"), f.Module);
            var store = new PromptLabStore(workspace); using var handler = new PromptLabWorkflowHandler();
            using var session = new PromptLabSession(store, _ => new HttpClient(handler, false));
            var version = store.SaveVersion(new() { Name = "MOCK_ONLY durable cycle", System = "Короткая конкретная реакция.", User = "Ответь на последний ход.",
                Generation = new() { Transport = "LM_NATIVE", Reasoning = "off", Temperature = .7 },
                Refinement = new() { Transport = "LM_NATIVE", Reasoning = "off", Temperature = .2, MaxOutputTokens = 4096 },
                Scenario = new() { Category = "social.friendship.support", Count = 1, Style = new() { Category = "social.friendship.support" } } });
            store.AcceptVersion(version.Id); var originalHash = store.VersionHash(version.Id);
            var baseline = await session.GenerateAsync(version.Id, new(), "", "instance-1", CancellationToken.None); Equal("COMPLETE", baseline.State);
            Throws(() => session.QueueRefinement(baseline.Id));
            var row = baseline.Rows.Single(); store.SaveFeedback(baseline.Id, [new(row.Number, row.TextHash, "NONE", "MOCK_ONLY замечание без оценки владельца.")], "MOCK_ONLY проверить формулировку.");
            var requests = handler.Requests;
            var queued = session.QueueRefinement(baseline.Id, version.Refinement);
            Equal(requests, handler.Requests); Equal("QUEUED", queued.State); Equal("REFINEMENT", queued.Purpose); Equal(baseline.Id, queued.BaselineRunId);
            Equal(.2, queued.ApiOptions!.Temperature); True(queued.InputContext.Contains("MOCK_ONLY", StringComparison.Ordinal));
            Equal(queued.Id, new PromptLabStore(workspace).GetRun(queued.Id).Id);
            session.Dispose(); Throws(() => session.QueueRefinement(baseline.Id)); Throws(() => session.QueueComparison(baseline.Id, version.Id));
            using var resumed = new PromptLabSession(store, _ => new HttpClient(handler, false));
            var refined = await resumed.ResumeAsync(queued.Id, new(), "", "instance-1", CancellationToken.None, requireExactInstance: true);
            Equal("COMPLETE", refined.State); True(refined.ProposedVersionId.Length > 0); Equal(2, handler.Posts);
            var proposal = store.GetVersion(refined.ProposedVersionId); Equal(version.Id, proposal.ParentId); Equal(version.ProfileId, proposal.ProfileId);
            var comparison = resumed.QueueComparison(baseline.Id, proposal.Id);
            Equal(2, handler.Posts); Equal("QUEUED", comparison.State); Equal(baseline.Id, comparison.BaselineRunId);
            Equal(baseline.ApiOptions!.Temperature, comparison.ApiOptions!.Temperature); Equal("GENERATION", comparison.Purpose);
            Equal(JsonSerializer.Serialize(baseline.Scenario, PromptProfiles.CompactJson), JsonSerializer.Serialize(comparison.Scenario, PromptProfiles.CompactJson));
            using var reopened = new PromptLabSession(new PromptLabStore(workspace), _ => new HttpClient(handler, false));
            Equal("COMPLETE", (await reopened.ResumeAsync(comparison.Id, new(), "", "instance-1", CancellationToken.None, requireExactInstance: true)).State);
            Equal(3, handler.Posts); _ = await reopened.ResumeAsync(queued.Id, new(), "", "instance-1", CancellationToken.None);
            _ = await reopened.ResumeAsync(comparison.Id, new(), "", "instance-1", CancellationToken.None); Equal(3, handler.Posts);
            Equal(version.Id, store.GetProfile(version.ProfileId).AcceptedVersionId); Equal(originalHash, store.VersionHash(version.Id));
            True(store.LatestFeedback(baseline.Id)!.Ratings.All(r => r.Vote == "NONE"));
        });
        await TestAsync("001 transport copied to another issued run cannot be committed as its response", async () =>
        {
            using var f = new Fixture(); using var workspace = new WorkspaceStore(Path.Combine(f.Root, "workspace"), f.Module);
            var store = new PromptLabStore(workspace); using var handler = new PromptLabWorkflowHandler();
            using var session = new PromptLabSession(store, _ => new HttpClient(handler, false));
            var version = store.SaveVersion(new() { Generation = new() { Transport = "LM_NATIVE" },
                Scenario = new() { Category = "social.friendship.support", Count = 1, Style = new() { Category = "social.friendship.support" } } });
            var first = await session.GenerateAsync(version.Id, new(), "", "instance-1", CancellationToken.None); Equal("COMPLETE", first.State);
            var other = store.BeginRun(version.Id); store.MarkIssued(other.Id);
            File.Copy(Path.Combine(store.Root, "runs", first.Id, "transport.json"), Path.Combine(store.Root, "runs", other.Id, "transport.json"));
            await ThrowsAsync(() => session.ResumeAsync(other.Id, new(), "", "instance-1", CancellationToken.None));
            True(!store.GetRun(other.Id).CompletedAtUtc.HasValue); Equal(1, handler.Posts);
            void RewriteLegacy(string path, params string[] removed)
            {
                var node = System.Text.Json.Nodes.JsonNode.Parse(File.ReadAllText(path))!;
                foreach (var name in removed) node["data"]!.AsObject().Remove(name);
                node["sha256"] = V4Rules.Hash(JsonSerializer.SerializeToUtf8Bytes(node["data"], PromptProfiles.CompactJson));
                File.WriteAllText(path, node.ToJsonString(WorkspaceStore.JsonOptions));
            }
            var otherReceipt = Path.Combine(store.Root, "runs", other.Id, "transport.json");
            RewriteLegacy(otherReceipt, "RunId", "VersionHash", "RequestHash");
            await ThrowsAsync(() => session.ResumeAsync(other.Id, new(), "", "instance-1", CancellationToken.None));
            var legacy = store.BeginRun(version.Id);
            RewriteLegacy(Path.Combine(store.Root, "runs", legacy.Id, "request.json"), "RequiresTransportBinding");
            store.MarkIssued(legacy.Id); File.Copy(otherReceipt, Path.Combine(store.Root, "runs", legacy.Id, "transport.json"));
            Equal("COMPLETE", (await session.ResumeAsync(legacy.Id, new(), "", "instance-1", CancellationToken.None)).State);
            var target = store.BeginRun(version.Id); store.MarkIssued(target.Id);
            Throws(() => store.SaveTransport(target.Id, store.GetTransport(first.Id)!));
            var legacyPath = Path.Combine(store.Root, "runs", legacy.Id, "transport.json"); var legacyHash = FileSha(legacyPath);
            Throws(() => store.SaveTransport(target.Id, store.GetTransport(legacy.Id)!));
            Equal(legacyHash, FileSha(legacyPath)); True(!File.Exists(Path.Combine(store.Root, "runs", target.Id, "transport.json")));
            Equal(1, handler.Posts);
        });
        await TestAsync("001 diagnostic feedback cycle is unvoted durable orphan-safe and never reissues uncertain work", async () =>
        {
            using var f = new Fixture(); var root = Path.Combine(f.Root, "workspace");
            using var handler = new PromptLabWorkflowHandler();
            PromptLabVersion version; PromptLabRun baseline; string storeRoot; var file = Path.Combine(f.Root, "feedback.json");
            using (var workspace = new WorkspaceStore(root, f.Module))
            {
                var store = new PromptLabStore(workspace); storeRoot = store.Root;
                using var session = new PromptLabSession(store, _ => new HttpClient(handler, false));
                version = store.SaveVersion(new() { Generation = new() { Transport = "LM_NATIVE" },
                    Refinement = new() { Transport = "LM_NATIVE", Reasoning = "off", Temperature = .2, MaxOutputTokens = 4096 },
                    Scenario = new() { Category = "social.friendship.support", Count = 1, Style = new() { Category = "social.friendship.support" } } });
                store.AcceptVersion(version.Id); baseline = await session.GenerateAsync(version.Id, new(), "", "instance-1", CancellationToken.None);
                var row = baseline.Rows[0];
                File.WriteAllBytes(file, JsonSerializer.SerializeToUtf8Bytes(new { Schema = "pw-diagnostic-refinement/1", Reviewer = "CODEX_PROVISIONAL_NOT_OWNER",
                    BaselineSha256 = FileSha(Path.Combine(store.Root, "runs", baseline.Id, "outcome.json")), BatchComment = "MOCK_ONLY проверить повторение формулы, не оценка владельца.",
                    Ratings = new[] { new PromptLabRating(row.Number, row.TextHash, "NONE", "MOCK_ONLY конкретное замечание.") }, Refinement = version.Refinement }, WorkspaceStore.JsonOptions));
            }
            T Inspect<T>(Func<PromptLabStore, T> action) { using var workspace = new WorkspaceStore(root, f.Module); return action(new PromptLabStore(workspace)); }
            string[] Args(int limit, string spec) => ["--prompt-lab-feedback-cycle", root, f.Module, "instance-1", baseline.Id, spec, limit.ToString()];
            var requests = handler.Requests;
            Equal(0, await PromptLabRefinementCycle(Args(0, file), _ => new HttpClient(handler, false))); Equal(requests, handler.Requests);
            var queued = Inspect(store => store.ListRuns().Single(r => r.Purpose == "REFINEMENT"));
            var cycle = Path.Combine(storeRoot, "diagnostic-cycles", FileSha(file));
            File.Delete(Path.Combine(cycle, "plan.json"));
            Equal(0, await PromptLabRefinementCycle(Args(0, file), _ => new HttpClient(handler, false)));
            Equal(queued.Id, Inspect(store => store.ListRuns().Single(r => r.Purpose == "REFINEMENT").Id)); Equal(1, Inspect(store => store.ListFeedback(baseline.Id).Count));
            Equal(0, await PromptLabRefinementCycle(Args(2, file), _ => new HttpClient(handler, false))); Equal(3, handler.Posts);
            File.Delete(Path.Combine(cycle, "comparison.json"));
            Equal(0, await PromptLabRefinementCycle(Args(2, file), _ => new HttpClient(handler, false))); Equal(3, handler.Posts);
            var next = System.Text.Json.Nodes.JsonNode.Parse(File.ReadAllText(file))!;
            next["BatchComment"] = "MOCK_ONLY второй цикл для проверки неопределённого issued.";
            var second = Path.Combine(f.Root, "feedback-second.json"); File.WriteAllText(second, next.ToJsonString(WorkspaceStore.JsonOptions));
            Equal(0, await PromptLabRefinementCycle(Args(0, second), _ => new HttpClient(handler, false)));
            var pending = Inspect(store => store.ListRuns().Single(r => r.Purpose == "REFINEMENT" && r.State == "QUEUED"));
            var owner = Inspect(store => store.SaveFeedback(baseline.Id, [], "MOCK_ONLY реальный отзыв оператора появился после prepare."));
            requests = handler.Requests; Equal(3, await PromptLabRefinementCycle(Args(2, second), _ => new HttpClient(handler, false)));
            Equal(requests, handler.Requests); Equal(owner.Id, Inspect(store => store.LatestFeedback(baseline.Id)!.Id));
            Inspect(store => { store.MarkIssued(pending.Id); return 0; }); requests = handler.Requests;
            Equal(3, await PromptLabRefinementCycle(Args(2, second), _ => new HttpClient(handler, false))); Equal(requests, handler.Requests);
            Equal(version.Id, Inspect(store => store.GetProfile(version.ProfileId).AcceptedVersionId));
            True(Inspect(store => store.ListFeedback(baseline.Id).SelectMany(f => f.Ratings).All(r => r.Vote == "NONE")));
        });
    }
}
