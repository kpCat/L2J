using System.Text;
using System.Text.Json;

namespace PhantomSemanticStudio.Core;

/// <summary>One durable editorial operation at a time; uncertain POSTs are never retried automatically.</summary>
public sealed partial class PromptLabSession : IDisposable
{
    private readonly PromptLabStore store;
    private readonly Func<int, HttpClient> httpFactory;
    private readonly SemaphoreSlim gate = new(1, 1);
    private readonly CancellationTokenSource lifetime = new();
    private bool disposed;

    public PromptLabSession(PromptLabStore store, Func<int, HttpClient>? httpFactory = null)
    { this.store = store; this.httpFactory = httpFactory ?? LmStudioClient.CreateHttpClient; }

    public PromptLabVersion CreateProfile(string name, string strategy, V4GenerationRequest scenario)
    {
        V4Rules.Require(PromptProfiles.Strategies.Contains(strategy), "UNKNOWN_PROMPT_STRATEGY");
        return store.SaveVersion(new() { Name = name, System = PromptProfiles.Default.Resources[strategy + ".v1.md"],
            User = PromptProfiles.Default.GenerationUserTask,
            RefinerSystem = PromptProfiles.Default.Resources["refiner.v1.md"], Scenario = scenario });
    }

    public PromptLabVersion CreateSpeechProfile(string styleId)
    {
        var name = styleId switch { "strong_profanity" => "Крепкий мат", "vulgar_humor" => "Вульгарный юмор", "double_meanings" => "Двусмысленности",
            "adult_flirt" => "Взрослый флирт", "intimate_conversation" => "Личный интимный разговор", _ => throw new InvalidDataException("UNKNOWN_SPEECH_PROFILE") };
        var preset = PromptProfiles.Default.Styles.Single(s => s.Id == styleId); var mature = preset.AllowsMature;
        var category = styleId switch { "strong_profanity" or "vulgar_humor" => "social.banter.rough", "adult_flirt" => "social.flirt.bold", "intimate_conversation" => "life.love.intimacy", _ => "social.banter.double_meanings" };
        var situation = styleId == "intimate_conversation"
            ? "Вымышленные совершеннолетние знакомые согласны прямо поговорить о близости и предпочтениях. Это предметный личный разговор, не шутка и не эротическая сцена."
            : "Вымышленные совершеннолетние знакомые обсуждают заданную ситуацию. Используй только выбранный речевой профиль, учитывая взаимность и доверие.";
        var suggestions = styleId == "intimate_conversation" && !store.EffectiveRegistry().Categories.Any(c => c.Key == category)
            ? new[] { new PromptLabTaxonomySuggestion("life.love", category, "разговоры о близости и интимных предпочтениях", category + ".reply", category + ".observe",
                "Прямой личный разговор отличается от знакомств, флирта и шуток; нельзя подменять им существующую категорию dating.") } : [];
        return store.SaveVersion(new() { Name = name, ReadyForGeneration = !mature, CategorySuggestions = suggestions,
            System = PromptProfiles.Default.Resources["dialogue-context.v1.md"] + "\n\nОтдельный речевой профиль: " + name + ". " + preset.Instruction,
            User = PromptProfiles.Default.GenerationUserTask + "\nИсполни именно выбранный речевой профиль и не пересказывай его описание.",
            RefinerSystem = PromptProfiles.Default.Resources["refiner.v1.md"],
            Scenario = new() { Category = category, ProfanityLimit = styleId is "strong_profanity" or "vulgar_humor" ? "STRONG" : "NONE",
                Style = new() { Category = category, StyleId = styleId, Voice = styleId switch { "strong_profanity" or "vulgar_humor" => "rough_joker", "double_meanings" => "dry_wit", "intimate_conversation" => "warm_friend", _ => "playful_tease" },
                    Relation = mature ? "TRUSTED" : "FAMILIAR", Channel = "BOT_PRIVATE", MatureRequested = mature,
                    StrongProfanityRequested = styleId is "strong_profanity" or "vulgar_humor", Situation = situation,
                    Dialogue = ["Игрок: Давай обсудим это прямо, без общих обещаний поболтать."] } } });
    }

    public PromptLabVersion CreateFamilyProfile(V4GenerationRequest scenario, string? name = null)
    {
        scenario = scenario with { Components = null };
        var snapshot = PromptHierarchy.Default.Freeze(scenario, PromptProfiles.Default, store.EffectiveRegistry());
        var family = PromptHierarchy.Default.FamilyFor(scenario.Category);
        return store.SaveVersion(new() { Name = name ?? family.Label, System = snapshot.FamilyInstruction,
            User = PromptProfiles.Default.GenerationUserTask,
            RefinerSystem = PromptProfiles.Default.Resources["refiner.v1.md"], Scenario = scenario with { Components = snapshot } });
    }

    public PromptLabVersion PrepareVersion(PromptLabVersion draft)
    {
        if (!draft.ReadyForGeneration) return draft;
        var scopes = new[] { draft.Scenario }.Concat(draft.AdditionalScenarios).ToArray();
        V4Rules.Require(draft.CategoryInstructions.Count == 0 || draft.CategoryInstructions.Count == scopes.Length, "PROMPT_LAB_CATEGORY_INSTRUCTIONS");
        var registry = store.EffectiveRegistry();
        string? Brief(V4GenerationRequest scope, int index)
        {
            var value = draft.CategoryInstructions.Count == 0 ? null : draft.CategoryInstructions[index];
            if (string.IsNullOrWhiteSpace(value) || scope.Components is { } old && old.Category != scope.Category && value == old.CategoryInstruction) return null;
            return value;
        }
        var rebound = scopes.Select((scope, index) => scope.Components == null ? scope : scope with
        {
            Components = PromptHierarchy.Default.Rebind(scope.Components, scope, PromptProfiles.Default, registry, draft.System,
                Brief(scope, index))
        }).ToArray();
        return draft with { Scenario = rebound[0], AdditionalScenarios = rebound.Skip(1).ToArray(),
            CategoryInstructions = rebound.Any(s => s.Components != null) ? rebound.Select(s => s.Components?.CategoryInstruction ?? "").ToArray() : draft.CategoryInstructions };
    }

    public PromptLabFamilyGroup SplitFamilyPlan(string versionId)
    {
        var source = store.GetVersion(versionId); var existing = store.FindFamilyGroup(source.Id); if (existing != null) return existing;
        var registry = store.EffectiveRegistry();
        var scopes = new[] { source.Scenario }.Concat(source.AdditionalScenarios).Select((scope, index) => (Scope: scope, Index: index)).ToArray();
        var groups = scopes.GroupBy(s => PromptHierarchy.Default.FamilyFor(s.Scope.Category).Id).ToArray();
        V4Rules.Require(source.FamilyPrompts.All(f => groups.Any(g => g.Key == f.FamilyId)), "PROMPT_LAB_FAMILY_PLAN_SCOPE");
        // Validate every child before writing any version. A failed write can be resumed from its immutable root versions.
        var drafts = groups.Select(group =>
        {
            var prompt = source.FamilyPrompts.SingleOrDefault(f => f.FamilyId == group.Key);
            var instruction = prompt?.System ?? (source.Scenario.Components?.FamilyId == group.Key ? source.System : PromptHierarchy.Default.FamilyFor(group.First().Scope.Category).Instruction);
            var bound = group.Select(item => item.Scope with { Components = PromptHierarchy.Default.Freeze(item.Scope with { Components = null }, PromptProfiles.Default, registry,
                instruction, source.CategoryInstructions.Count > item.Index && !string.IsNullOrWhiteSpace(source.CategoryInstructions[item.Index]) ? source.CategoryInstructions[item.Index] : null) }).ToArray();
            var name = source.Name + " · " + PromptHierarchy.Default.FamilyFor(bound[0].Category).Label;
            var draft = source with { Id = "", ParentId = "", ProfileId = "", Name = name.Length > 120 ? name[..120] : name,
                Origin = "COPY", ProposedFromRunId = "", ReadyForGeneration = true, FamilyGroupId = source.Id, FamilyPrompts = [], CategorySuggestions = [],
                System = instruction, User = prompt?.User ?? "Создай одну партию самостоятельных альтернатив по указанной категории и сцене.",
                Scenario = bound[0], AdditionalScenarios = bound.Skip(1).ToArray(), CategoryInstructions = bound.Select(s => s.Components!.CategoryInstruction).ToArray() };
            draft.Validate(false, registry); return draft;
        }).ToArray();
        var roots = store.ListProfiles().SelectMany(p => store.ListVersions(p.Id)).Where(v => v.FamilyGroupId == source.Id && v.ParentId.Length == 0).ToArray();
        var members = drafts.Select(draft => roots.SingleOrDefault(v => v.Scenario.Components?.FamilyId == draft.Scenario.Components!.FamilyId) ?? store.SaveVersion(draft)).ToArray();
        return store.SaveFamilyGroup(source.Id, members.Select(v => v.Id).ToArray());
    }

    public Task<IReadOnlyList<PromptLabRun>> GenerateFamilyExperimentAsync(IReadOnlyList<string> versionIds, StudioSettings settings, string apiKey,
        string exactModel, CancellationToken token, IProgress<PromptLabProgress>? progress = null)
    {
        token.ThrowIfCancellationRequested(); V4Rules.Require(versionIds.Count is >= 1 and <= 16 && versionIds.Distinct(StringComparer.Ordinal).Count() == versionIds.Count, "PROMPT_LAB_FAMILY_SELECTION");
        var versions = versionIds.Select(store.GetVersion).ToArray(); var group = store.GetFamilyGroup(versions[0].FamilyGroupId);
        var members = group.VersionIds.Select(store.GetVersion).ToArray();
        V4Rules.Require(versions.All(v => v.ReadyForGeneration && v.FamilyGroupId == group.Id && members.Any(m => m.ProfileId == v.ProfileId))
            && versions.Select(v => v.ProfileId).Distinct().Count() == versions.Length, "PROMPT_LAB_FAMILY_SELECTION");
        foreach (var version in versions) version.Validate(false, store.EffectiveRegistry());
        var experiment = Guid.NewGuid().ToString("N");
        var queued = versions.SelectMany(version => new[] { version.Scenario }.Concat(version.AdditionalScenarios)
            .Select(scope => store.BeginRun(version.Id, scenario: scope, experimentId: experiment))).ToArray();
        return ResumeExperimentAsync(queued[0].Id, settings, apiKey, exactModel, token, progress);
    }

    public Task<PromptLabRun> GenerateAsync(string versionId, StudioSettings settings, string apiKey, string exactModel, CancellationToken token,
        IProgress<PromptLabProgress>? progress = null)
    {
        token.ThrowIfCancellationRequested(); var run = store.BeginRun(versionId);
        return ResumeAsync(run.Id, settings, apiKey, exactModel, token, progress);
    }

    public Task<PromptLabRun> DescribeAsync(string versionId, string description, StudioSettings settings, string apiKey, string exactModel, CancellationToken token,
        IProgress<PromptLabProgress>? progress = null)
    {
        token.ThrowIfCancellationRequested(); V4Rules.Require(!string.IsNullOrWhiteSpace(description) && Encoding.UTF8.GetByteCount(description) <= 8192, "DESCRIPTION_INPUT_LIMIT");
        var run = store.BeginRun(versionId, "DESCRIPTION", inputContext: description);
        return ResumeAsync(run.Id, settings, apiKey, exactModel, token, progress);
    }

    public async Task<IReadOnlyList<PromptLabRun>> GenerateExperimentAsync(string versionId, StudioSettings settings, string apiKey, string exactModel,
        CancellationToken token, IProgress<PromptLabProgress>? progress = null)
    {
        token.ThrowIfCancellationRequested(); var version = store.GetVersion(versionId); V4Rules.Require(version.ReadyForGeneration, "PLAN_REVIEW_REQUIRED");
        version.Validate(false, store.EffectiveRegistry()); var experiment = Guid.NewGuid().ToString("N");
        var queued = new[] { version.Scenario }.Concat(version.AdditionalScenarios)
            .Select(scope => store.BeginRun(version.Id, scenario: scope, experimentId: experiment)).ToArray();
        var results = new List<PromptLabRun>();
        foreach (var run in queued)
        {
            if (token.IsCancellationRequested) break;
            var result = await ResumeAsync(run.Id, settings, apiKey, exactModel, token, progress).ConfigureAwait(false); results.Add(result);
            if (result.State is "CANCELLED" or "INDETERMINATE" || result.Failure.Contains("TIMEOUT", StringComparison.Ordinal) || result.Failure.Contains("MODEL_OFFLINE", StringComparison.Ordinal)) break;
        }
        return results.AsReadOnly();
    }

    public Task<PromptLabRun> RefineAsync(string runId, StudioSettings settings, string apiKey, string exactModel, CancellationToken token,
        IProgress<PromptLabProgress>? progress = null, PromptLabApiOptions? apiOptions = null, string? sourceVersionId = null)
    {
        token.ThrowIfCancellationRequested(); var source = store.GetRun(runId);
        var feedback = store.LatestFeedback(runId) ?? throw new InvalidDataException("REFINEMENT_REQUIRES_SAVED_FEEDBACK");
        V4Rules.Require(feedback.BatchComment.Length > 0 || feedback.Ratings.Any(r => r.Vote != "NONE" || r.Comment.Length > 0), "REFINEMENT_REQUIRES_FEEDBACK");
        var context = FeedbackContext(source, feedback);
        var selected = store.GetVersion(sourceVersionId ?? source.VersionId);
        var original = store.GetVersion(source.VersionId);
        var sourceScope = source.Scenario ?? original.Scenario;
        var bound = BindEvaluationScenario(original, selected, sourceScope, "REFINEMENT");
        var queued = store.BeginRun(selected.Id, "REFINEMENT", runId, context, apiOptions, bound);
        return ResumeAsync(queued.Id, settings, apiKey, exactModel, token, progress);
    }

    public async Task<IReadOnlyList<PromptLabRun>> ResumeExperimentAsync(string memberRunId, StudioSettings settings, string apiKey, string exactModel,
        CancellationToken token, IProgress<PromptLabProgress>? progress = null)
    {
        var member = store.GetRun(memberRunId);
        if (member.ExperimentId.Length == 0) return [await ResumeAsync(member.Id, settings, apiKey, exactModel, token, progress).ConfigureAwait(false)];
        var runs = store.ListRuns().Where(r => r.ExperimentId == member.ExperimentId).OrderBy(r => r.CreatedAtUtc).ThenBy(r => r.Id, StringComparer.Ordinal).ToArray();
        var results = new List<PromptLabRun>();
        foreach (var run in runs)
        {
            if (token.IsCancellationRequested) break;
            // Completed/rejected/cancelled members retain their exact outcome. Only untouched queued work may be issued.
            var result = await ResumeAsync(run.Id, settings, apiKey, exactModel, token, progress).ConfigureAwait(false); results.Add(result);
            if (result.State == "INDETERMINATE") break;
            if (!run.CompletedAtUtc.HasValue && (result.Failure.Contains("TIMEOUT", StringComparison.Ordinal) || result.Failure.Contains("MODEL_OFFLINE", StringComparison.Ordinal))) break;
        }
        return results.AsReadOnly();
    }

    public Task<PromptLabRun> CompareAsync(string baselineRunId, string newVersionId, StudioSettings settings, string apiKey, string exactModel,
        CancellationToken token, IProgress<PromptLabProgress>? progress = null)
    {
        token.ThrowIfCancellationRequested(); var baseline = store.GetRun(baselineRunId);
        V4Rules.Require(baseline.Purpose == "GENERATION" && baseline.CompletedAtUtc.HasValue, "COMPARISON_BASELINE_REQUIRED");
        var old = store.GetVersion(baseline.VersionId);
        var selected = store.GetVersion(newVersionId);
        var scenario = BindEvaluationScenario(old, selected, baseline.Scenario ?? old.Scenario, "COMPARISON");
        var queued = store.BeginRun(newVersionId, baselineRunId: baselineRunId, apiOptions: baseline.ApiOptions ?? old.Generation, scenario: scenario);
        return ResumeAsync(queued.Id, settings, apiKey, exactModel, token, progress);
    }

    private V4GenerationRequest BindEvaluationScenario(PromptLabVersion original, PromptLabVersion selected, V4GenerationRequest scenario, string operation)
    {
        V4Rules.Require(selected.ProfileId == original.ProfileId, operation + "_PROFILE_MISMATCH");
        var oldScopes = new[] { original.Scenario }.Concat(original.AdditionalScenarios).ToArray();
        var scopes = new[] { selected.Scenario }.Concat(selected.AdditionalScenarios).ToArray();
        var index = Array.FindIndex(oldScopes, scope => JsonSerializer.Serialize(scope, PromptProfiles.CompactJson) == JsonSerializer.Serialize(scenario, PromptProfiles.CompactJson));
        bool SamePurpose(V4GenerationRequest scope) => scope.Category == scenario.Category && scope.Mode == scenario.Mode;
        var candidates = scopes.Where(SamePurpose).ToArray();
        V4Rules.Require(index >= 0 && index < scopes.Length && SamePurpose(scopes[index]) || candidates.Length == 1, operation + "_SCENARIO_MISMATCH_OR_AMBIGUOUS");
        var selectedScope = index >= 0 && index < scopes.Length && SamePurpose(scopes[index]) ? scopes[index] : candidates[0];
        if (selectedScope.Components != null)
            scenario = scenario with { Components = PromptHierarchy.Default.Rebind(selectedScope.Components, scenario, PromptProfiles.Default,
                store.EffectiveRegistry(), selected.System) };
        else V4Rules.Require(scenario.Components == null, operation + "_COMPONENT_CONTRACT_MISMATCH");
        return scenario;
    }

    public async Task<PromptLabRun> ResumeAsync(string runId, StudioSettings settings, string apiKey, string exactModel, CancellationToken token,
        IProgress<PromptLabProgress>? progress = null)
    {
        if (disposed) throw new ObjectDisposedException(nameof(PromptLabSession));
        using var caller = CancellationTokenSource.CreateLinkedTokenSource(token, lifetime.Token);
        await gate.WaitAsync(caller.Token).ConfigureAwait(false);
        try
        {
            var run = store.GetRun(runId);
            if (run.CompletedAtUtc.HasValue) return run;
            var savedVersion = store.GetVersion(run.VersionId); var version = savedVersion;
            var registry = store.EffectiveRegistry();
            V4Rules.Require(run.RegistryHash.Length == 0 || run.RegistryHash == registry.Hash, "PROMPT_LAB_REGISTRY_DRIFT");
            var receipt = store.GetTransport(runId);
            if (receipt != null) return CompleteReceipt(run, savedVersion, receipt);
            V4Rules.Require(run.State == "QUEUED", "INDETERMINATE_REQUEST_REVIEW_REQUIRED");
            var options = run.ApiOptions ?? (run.Purpose == "GENERATION" ? version.Generation : version.Refinement);
            version = version with { Scenario = run.Scenario ?? version.Scenario,
                Generation = run.Purpose == "GENERATION" ? options : version.Generation,
                Refinement = run.Purpose != "GENERATION" ? options : version.Refinement };
            version.Validate(false, registry);
            using var overall = CancellationTokenSource.CreateLinkedTokenSource(caller.Token); overall.CancelAfter(TimeSpan.FromSeconds(options.OverallTimeoutSeconds));
            using var http = httpFactory(options.ConnectionTimeoutSeconds); var client = new LmStudioClient(http);
            var actualSettings = new StudioSettings { Endpoint = settings.Endpoint, HighFiveRoot = settings.HighFiveRoot,
                ModelId = exactModel, TimeoutSeconds = options.OverallTimeoutSeconds };
            CorpusLoadedModel loaded; PromptLabResponse response;
            try
            {
                var inventory = await ProbeWithSafeRetry(client, actualSettings, apiKey, overall.Token).ConfigureAwait(false);
                var matches = inventory.Loaded.Where(m => m.InstanceId == exactModel || m.ModelKey == exactModel).ToArray();
                V4Rules.Require(matches.Length == 1, inventory.Loaded.Count == 0 ? "MODEL_OFFLINE" : "EXACT_LOADED_INSTANCE_REQUIRED");
                loaded = matches[0]; V4Rules.Require(loaded.ContextLength == 65536, "OWNER_CONTEXT_MISMATCH_EXPECTED_65536");
                store.MarkIssued(run.Id);
                response = run.Purpose switch
                {
                    "REFINEMENT" => await client.RefinePromptLabAsync(actualSettings, apiKey, loaded, version, run.InputContext, overall.Token, progress, registry).ConfigureAwait(false),
                    "DESCRIPTION" => await client.DescribePromptLabAsync(actualSettings, apiKey, loaded, version, run.InputContext, registry, overall.Token, progress).ConfigureAwait(false),
                    _ => await client.GeneratePromptLabAsync(actualSettings, apiKey, loaded, version, overall.Token, progress, registry).ConfigureAwait(false)
                };
                if (overall.IsCancellationRequested && !caller.IsCancellationRequested)
                    response = response with { Response = response.Response with { Rejection = "OVERALL_TIMEOUT", Content = "" } };
            }
            catch (Exception error) when (error is InvalidDataException or JsonException or LmStudioException or HttpRequestException or OperationCanceledException)
            {
                var failure = error is OperationCanceledException ? caller.IsCancellationRequested ? "CANCELLED" : "OVERALL_TIMEOUT"
                    : error is LmStudioException lm ? lm.Diagnostic.ToString() : error.Message;
                if (apiKey.Length > 0) failure = failure.Replace(apiKey, "[REDACTED_API_TOKEN]", StringComparison.Ordinal);
                var issued = store.GetRun(run.Id).State == "ISSUED";
                var rejected = run with { State = error is OperationCanceledException ? "CANCELLED" : issued ? "INDETERMINATE" : "REJECTED", Failure = failure, CompletedAtUtc = DateTimeOffset.UtcNow };
                store.CompleteRun(rejected); return rejected;
            }
            // Save the HTTP result before parsing/committing. Recovery of a durable receipt needs no repeat POST.
            receipt = new(response, loaded.InstanceId, loaded.ContextLength, settings.Endpoint); store.SaveTransport(run.Id, receipt);
            return CompleteReceipt(run, savedVersion, receipt);
        }
        finally { gate.Release(); }
    }

    private static async Task<CorpusModelInventory> ProbeWithSafeRetry(LmStudioClient client, StudioSettings settings, string key, CancellationToken token)
    {
        try { return await client.ProbeCorpusModelsAsync(settings, key, token).ConfigureAwait(false); }
        catch (HttpRequestException) when (!token.IsCancellationRequested)
        { await Task.Delay(250, token).ConfigureAwait(false); return await client.ProbeCorpusModelsAsync(settings, key, token).ConfigureAwait(false); }
    }

    private PromptLabRun CompleteReceipt(PromptLabRun queued, PromptLabVersion version, PromptLabTransportReceipt receipt)
    {
        var response = receipt.Reply.Response; var failure = response.Rejection; var rows = Array.Empty<PromptLabRow>(); var proposedId = "";
        if (failure.Length == 0)
        {
            try
            {
                if (queued.Purpose == "DESCRIPTION")
                {
                    var proposal = PromptLabAuthoring.ParseProposal(response.Content, store.EffectiveRegistry());
                    var scopes = proposal.Batches.Select(b => b.ToRequest()).ToArray();
                    proposedId = store.SaveVersion(version with { Id = "", ParentId = version.Id, Origin = "MODEL_PROPOSAL", ProposedFromRunId = queued.Id,
                        System = proposal.System, User = proposal.User, Scenario = scopes[0], AdditionalScenarios = scopes.Skip(1).ToArray(), ReadyForGeneration = false,
                        CategoryInstructions = [], FamilyPrompts = proposal.FamilyPrompts ?? [], FamilyGroupId = "",
                        CategorySuggestions = proposal.NewCategories, ProposalNote = proposal.Explanation, Description = queued.InputContext }).Id;
                }
                else if (queued.Purpose == "REFINEMENT")
                {
                    var proposal = LmStudioClient.ParsePromptLabRevision(response.Content);
                    V4GenerationRequest Revised(V4GenerationRequest scope) => scope.Components == null ? scope : scope with { Components = PromptHierarchy.Revise(scope.Components, proposal.System) };
                    proposedId = store.SaveVersion(version with { Id = "", ParentId = version.Id, Origin = "MODEL_PROPOSAL", ProposedFromRunId = queued.Id, System = proposal.System, User = proposal.User,
                        Scenario = Revised(version.Scenario), AdditionalScenarios = version.AdditionalScenarios.Select(Revised).ToArray() }).Id;
                }
                else
                {
                    var request = (queued.ApiOptions ?? version.Generation).Apply(queued.Scenario ?? version.Scenario);
                    var registry = store.EffectiveRegistry();
                    var wire = V4CorpusGeneration.ToJsonl(response.Content, request, registry); var file = store.SaveCandidateJsonl(queued.Id, wire);
                    var report = V4BatchReader.Read(file, registry);
                    if (!report.Success) failure = string.Join("; ", report.Diagnostics.Select(d => d.Message));
                    rows = ReadRows(response.Content, report.Success);
                    if (report.Success)
                    {
                        var issues = CorpusQuality.Check(report.Entries, request, []).Concat(request.Components == null ? [] : PromptHierarchy.CheckExampleEcho(request.Components, report.Entries)).ToArray();
                        rows = rows.Select(row => row with { Diagnostics = issues.Where(i => i.Record == 0 || i.Record == row.Number).Select(i => i.Code).ToArray() }).ToArray();
                    }
                }
            }
            catch (Exception error) when (error is InvalidDataException or JsonException or InvalidOperationException)
            { failure = error.Message; }
        }
        if (queued.Purpose == "GENERATION" && failure.Length > 0 && rows.Length == 0)
        {
            try { rows = ReadRows(response.Content, false); }
            catch (Exception error) when (error is InvalidDataException or JsonException or InvalidOperationException or KeyNotFoundException) { }
        }
        var state = failure.Length == 0 ? "COMPLETE" : failure == "CANCELLED" ? "CANCELLED" : !receipt.Reply.TransportComplete ? "INDETERMINATE" : "REJECTED";
        var completed = queued with { State = state, CompletedAtUtc = DateTimeOffset.UtcNow, ModelId = receipt.ModelId, ModelContext = receipt.ModelContext,
            Endpoint = receipt.Endpoint, EffectiveSystem = receipt.Reply.EffectiveSystem, EffectiveUser = receipt.Reply.EffectiveUser,
            RawStream = receipt.Reply.RawStream, Response = response, Rows = rows, Failure = failure, ProposedVersionId = proposedId };
        store.CompleteRun(completed); return completed;
    }

    private static PromptLabRow[] ReadRows(string content, bool valid)
    {
        using var document = JsonDocument.Parse(content, new JsonDocumentOptions { MaxDepth = 16 });
        var records = document.RootElement.GetProperty("records"); V4Rules.Require(records.ValueKind == JsonValueKind.Array && records.GetArrayLength() <= 20, "PROMPT_LAB_PREVIEW_RECORDS");
        return records.EnumerateArray().Select((record, index) =>
        {
            var text = V4Rules.String(record, "text"); V4Rules.Require(text.Length <= 10000, "PROMPT_LAB_PREVIEW_TEXT_LIMIT");
            string Value(string key, string fallback) => record.TryGetProperty(key, out var value) && value.ValueKind == JsonValueKind.String ? value.GetString()! : fallback;
            return new PromptLabRow(index + 1, text, TextRules.Hash(text), valid, valid ? [] : ["BATCH_REJECTED"],
                Value("profanity", valid ? "NONE" : "UNKNOWN"), Value("maturity", valid ? "GENERAL" : "UNKNOWN"), Value("stance", ""), Value("tone", valid ? "CALM" : ""));
        }).ToArray();
    }

    private static string FeedbackContext(PromptLabRun source, PromptLabFeedback feedback)
    {
        var selected = feedback.Ratings.Where(r => r.Vote == "DISLIKE").Take(4)
            .Concat(feedback.Ratings.Where(r => r.Vote == "LIKE").Take(4))
            .Concat(feedback.Ratings.Where(r => r.Vote == "NONE" && r.Comment.Length > 0).Take(4)).ToArray();
        var examples = selected.Select(r => new { r.Number, r.TextHash, r.Vote,
            text = Summary(source.Rows.Single(row => row.Number == r.Number).Text, 1000), comment = Summary(r.Comment, 600) }).ToArray();
        return JsonSerializer.Serialize(new { sourceRunId = source.Id, feedback.Id, feedback.RunHash,
            batchFeedback = Summary(feedback.BatchComment, 2500), sourceFailure = source.Failure,
            totalRatings = feedback.Ratings.Count, includedRatings = examples.Length, examples }, PromptProfiles.CompactJson);
    }

    private static object Summary(string text, int byteLimit)
    {
        var result = new StringBuilder(); var bytes = 0;
        foreach (var rune in text.EnumerateRunes()) { if (bytes + rune.Utf8SequenceLength > byteLimit) break; result.Append(rune.ToString()); bytes += rune.Utf8SequenceLength; }
        return new { text = result.ToString(), originalHash = TextRules.Hash(text), originalBytes = Encoding.UTF8.GetByteCount(text), includedBytes = bytes, shortenedForContext = result.Length != text.Length };
    }

    public static PromptLabMetrics MeasureRows(IReadOnlyList<PromptLabRow> rows)
    {
        V4Rules.Require(rows.Count <= 40, "PROMPT_LAB_METRICS_LIMIT");
        var normalized = rows.Select(r => V4Rules.Normalize(r.Text)).ToArray(); var near = 0;
        for (var i = 0; i < rows.Count; i++)
            for (var j = i + 1; j < rows.Count; j++)
                if (normalized[i] != normalized[j] && CorpusQuality.NearClone(rows[i].Text, rows[j].Text)) near++;
        var tokens = normalized.SelectMany(text => System.Text.RegularExpressions.Regex.Matches(text, @"[\p{L}\p{N}]+")
            .Select(match => match.Value)).ToArray();
        return new(rows.Count, rows.Count - normalized.Distinct(StringComparer.Ordinal).Count(), near,
            tokens.Length == 0 ? 0 : (double)tokens.Distinct(StringComparer.Ordinal).Count() / tokens.Length);
    }

    public void Dispose()
    {
        if (disposed) return; disposed = true; lifetime.Cancel();
        // No wait handle is allocated by SemaphoreSlim.WaitAsync; cancellation may still be unwinding in UI.
    }
}
