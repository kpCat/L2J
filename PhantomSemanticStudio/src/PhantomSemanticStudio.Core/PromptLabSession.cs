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
            RefinerSystem = PromptProfiles.Default.Resources["refiner.v1.md"], Scenario = scenario });
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
        V4Rules.Require(selected.ProfileId == original.ProfileId && new[] { selected.Scenario }.Concat(selected.AdditionalScenarios)
            .Any(s => s.Category == sourceScope.Category && s.Mode == sourceScope.Mode), "REFINEMENT_SCENARIO_MISMATCH_GENERATE_FIRST");
        var queued = store.BeginRun(selected.Id, "REFINEMENT", runId, context, apiOptions, source.Scenario);
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
        var queued = store.BeginRun(newVersionId, baselineRunId: baselineRunId, apiOptions: baseline.ApiOptions ?? old.Generation, scenario: baseline.Scenario ?? old.Scenario);
        return ResumeAsync(queued.Id, settings, apiKey, exactModel, token, progress);
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
                        CategorySuggestions = proposal.NewCategories, ProposalNote = proposal.Explanation, Description = queued.InputContext }).Id;
                }
                else if (queued.Purpose == "REFINEMENT")
                {
                    var proposal = LmStudioClient.ParsePromptLabRevision(response.Content);
                    proposedId = store.SaveVersion(version with { Id = "", ParentId = version.Id, Origin = "MODEL_PROPOSAL", ProposedFromRunId = queued.Id, System = proposal.System, User = proposal.User }).Id;
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
                        var issues = CorpusQuality.Check(report.Entries, request, []);
                        rows = rows.Select(row => row with { Diagnostics = issues.Where(i => i.Record == row.Number).Select(i => i.Code).ToArray() }).ToArray();
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
