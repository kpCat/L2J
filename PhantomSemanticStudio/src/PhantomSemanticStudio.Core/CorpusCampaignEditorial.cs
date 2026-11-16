using System.Text.Json;
using System.Security.Cryptography;

namespace PhantomSemanticStudio.Core;

public sealed record CorpusEditorialReference(string Id, string Text, string Category = "", string Mode = "", string Context = "");
public sealed record CorpusEditorialCandidate(string Id, string Text, IReadOnlyList<string> Diagnostics, IReadOnlyList<CorpusEditorialReference> References);
public sealed record CorpusEditorialBatch(V4GenerationRequest Scope, IReadOnlyList<CorpusEditorialCandidate> Candidates, string GenerationSystem = "", string GenerationUser = "");
public sealed record CorpusEditorialVerdict(string Id, int Naturalness, int Context, int Character, int Grammar,
    bool Act, bool Profile, string Lore, string Maturity, string Profanity, string Duplicate, IReadOnlyList<string> DuplicateOf, bool Uncertain, string Reason);
internal sealed record CorpusEditorialJob(string CampaignId, string TaskId, string PolicyId, string ApprovalHash, string RunId,
    string RunHash, string ContentHash, string ModelId, string Endpoint, CorpusEditorialBatch Batch, PromptLabApiOptions Options, string System, DateTimeOffset CreatedAtUtc);
internal sealed record CorpusEditorialIssue(string JobHash, DateTimeOffset IssuedAtUtc);
internal sealed record CorpusEditorialReceipt(string JobHash, string ModelId, int ModelContext, string Endpoint, PromptLabResponse Reply);
internal sealed record CorpusEditorialRowDecision(int Number, string State, string Reason, CorpusEditorialVerdict? Verdict);
internal sealed record CorpusEditorialDecision(string JobHash, string ReceiptHash, IReadOnlyList<CorpusEditorialRowDecision> Rows);

internal sealed class CorpusCampaignEditorial(WorkspaceStore workspace, Func<int, HttpClient> httpFactory, Action<string>? checkpoint, int qualityInterval = 10000)
{
    private readonly PromptLabStore store = new(workspace);
    private static string Hash<T>(T value) => V4Rules.Hash(JsonSerializer.SerializeToUtf8Bytes(value, PromptProfiles.CompactJson));
    private static readonly string[] FatalDiagnostics = ["EXACT_DUPLICATE", "GENERATION_SCOPE", "RELATION_SCOPE", "VOICE_SCOPE", "MATURE_SCOPE", "PROFANITY_SCOPE",
        "JAVA_PUBLIC_PROFANITY_BLOCKED", "JAVA_STANCE_CONTEXT_BLOCKED", "ANATOMY_RUNTIME_UNFILTERED", "ANATOMY_CONTRADICTION", "PERSONAL_FACT_RUNTIME_UNFILTERED",
        "PERSONAL_FACT_CONTRADICTION", "QA_ONLY_NO_RUNTIME_GENDER_FILTER", "PROFANITY_MARKING", "STRONG_PROFILE_DENSITY_REVIEW"];

    private CorpusEditorialBatch Batch(PromptLabRun run, CorpusCampaignDraftSnapshot snapshot)
    {
        var version = store.GetVersion(run.VersionId); var scope = run.Scenario ?? version.Scenario;
        var references = new Dictionary<long, CorpusEditorialReference>();
        var rows = snapshot.Rows.Select(row => new CorpusEditorialCandidate("c" + row.Number, row.Text, row.Diagnostics,
            row.Neighbors.Take(2).Select(neighbor =>
            {
                if (references.TryGetValue(neighbor.TextId, out var found)) return found;
                var context = ""; var source = neighbor.Source;
                if (PromptLabStore.IsId(source.SourceId) && Directory.Exists(Path.Combine(store.Root, "runs", source.SourceId)))
                {
                    var original = store.GetRun(source.SourceId); var originalVersion = store.GetVersion(original.VersionId); var originalScope = original.Scenario ?? originalVersion.Scenario;
                    V4Rules.Require(original.Response != null && V4Rules.Hash(V4Rules.Utf8.GetBytes(original.Response.Content)) == source.SourceHash, "EDITORIAL_NEIGHBOR_SOURCE_DRIFT");
                    var full = JsonSerializer.Serialize(new { originalScope.Category, originalScope.Mode, originalScope.Style.Situation, originalScope.Style.Dialogue, User = original.EffectiveUser }, PromptProfiles.CompactJson);
                    if (full.Length <= 2000) context = full;
                }
                var reference = new CorpusEditorialReference("n" + (references.Count + 1), source.Text, source.Category, source.Mode, context);
                references.Add(neighbor.TextId, reference); return reference;
            }).ToArray())).ToArray();
        var batch = new CorpusEditorialBatch(scope, rows, run.EffectiveSystem, run.EffectiveUser); LmStudioClient.ValidateCorpusEditorialInput(batch); return batch;
    }

    public async Task<bool> ReviewAsync(CorpusCampaignJournal journal, CorpusCampaignPlan plan, CorpusCampaignTask task,
        CorpusCampaignPolicy policy, string approvalHash, CancellationToken token, IProgress<PromptLabProgress>? progress)
    {
        if (PauseRequested(journal)) return false;
        var policies = new CorpusCampaignPolicies(store); _ = policies.CheckApproved(policy.Id, approvalHash);
        var run = store.GetRun(task.RunId); var snapshot = store.GetCampaignDraftSnapshot(plan.Id, task.Id) ?? throw new InvalidDataException("EDITORIAL_PREINDEX_REQUIRED");
        V4Rules.Require(snapshot.RunId == run.Id && snapshot.RunHash == Hash(run) && run.Response != null
            && snapshot.ContentHash == V4Rules.Hash(V4Rules.Utf8.GetBytes(run.Response.Content)), "EDITORIAL_RUN_DRIFT");
        var job = store.ReadCampaignEditorial<CorpusEditorialJob>(plan.Id, task.Id, policy.Id, "request", "EDITORIAL_REQUEST");
        if (job == null)
        {
            CorpusEditorialBatch batch;
            try { batch = Batch(run, snapshot); _ = LmStudioClient.ComposeCorpusEditorial(batch, policy.Spec.Critic, policy.Spec.CriticSystem); }
            catch (InvalidDataException error) when (error.Message.Contains("LIMIT", StringComparison.Ordinal) || error.Message.Contains("CONTEXT_BUDGET", StringComparison.Ordinal))
            { journal.State("PAUSED_EDITORIAL_CONTEXT_LIMIT", error.Message); return false; }
            job = new(plan.Id, task.Id, policy.Id, approvalHash, run.Id, snapshot.RunHash, snapshot.ContentHash, plan.Spec.ModelId,
                plan.Spec.Endpoint, batch, policy.Spec.Critic, policy.Spec.CriticSystem, DateTimeOffset.UtcNow);
            store.WriteCampaignEditorial(plan.Id, task.Id, policy.Id, "request", "EDITORIAL_REQUEST", job);
        }
        V4Rules.Require(job.CampaignId == plan.Id && job.TaskId == task.Id && job.PolicyId == policy.Id && job.ApprovalHash == approvalHash
            && job.RunId == run.Id && job.RunHash == snapshot.RunHash && job.ContentHash == snapshot.ContentHash && job.ModelId == plan.Spec.ModelId
            && job.Endpoint == plan.Spec.Endpoint && Hash(job.Options) == Hash(policy.Spec.Critic) && job.System == policy.Spec.CriticSystem
            && Hash(job.Batch.Scope) == Hash(run.Scenario ?? store.GetVersion(run.VersionId).Scenario), "EDITORIAL_JOB_DRIFT");
        V4Rules.Require(job.Batch.GenerationSystem == run.EffectiveSystem && job.Batch.GenerationUser == run.EffectiveUser, "EDITORIAL_SOURCE_INSTRUCTION_DRIFT");
        try { _ = LmStudioClient.ComposeCorpusEditorial(job.Batch, job.Options, job.System); }
        catch (InvalidDataException error) when (error.Message.Contains("LIMIT", StringComparison.Ordinal) || error.Message.Contains("CONTEXT_BUDGET", StringComparison.Ordinal))
        { journal.State("PAUSED_EDITORIAL_CONTEXT_LIMIT", error.Message); return false; }
        var jobHash = Hash(job);
        var issued = store.ReadCampaignEditorial<CorpusEditorialIssue>(plan.Id, task.Id, policy.Id, "issued", "EDITORIAL_ISSUED");
        var receipt = store.ReadCampaignEditorial<CorpusEditorialReceipt>(plan.Id, task.Id, policy.Id, "receipt", "EDITORIAL_RECEIPT");
        V4Rules.Require(issued == null || issued.JobHash == jobHash, "EDITORIAL_ISSUE_DRIFT");
        V4Rules.Require(receipt == null || issued != null && receipt.JobHash == jobHash && receipt.ModelId == job.ModelId
            && receipt.ModelContext == 65536 && receipt.Endpoint == job.Endpoint && receipt.Reply.EffectiveSystem == job.System, "EDITORIAL_RECEIPT_DRIFT");
        journal.Editorial(task, policy.Id, approvalHash, jobHash, issued != null);
        if (receipt == null)
        {
            if (issued != null) { journal.State("PAUSED_EDITORIAL_INDETERMINATE", "Неизвестный editorial POST не повторяется: " + task.Id); return false; }
            if (journal.Status().EditorialIssued >= policy.Spec.MaxEditorialRequests)
            { journal.State("EDITORIAL_BUDGET_EXHAUSTED", "Лимит редакционных запросов исчерпан."); return false; }
            using var overall = CancellationTokenSource.CreateLinkedTokenSource(token); overall.CancelAfter(TimeSpan.FromSeconds(job.Options.OverallTimeoutSeconds));
            using var http = httpFactory(job.Options.ConnectionTimeoutSeconds); var client = new LmStudioClient(http);
            var settings = new StudioSettings { HighFiveRoot = workspace.ProtectedRoot, ModelId = job.ModelId, Endpoint = job.Endpoint, TimeoutSeconds = job.Options.OverallTimeoutSeconds };
            var apiKey = Environment.GetEnvironmentVariable("LM_STUDIO_API_KEY") ?? "";
            try
            {
                var inventory = await client.ProbeCorpusModelsAsync(settings, apiKey, overall.Token).ConfigureAwait(false);
                var matches = inventory.Loaded.Where(m => m.InstanceId == job.ModelId).ToArray();
                V4Rules.Require(matches.Length == 1 && matches[0].ContextLength == 65536, "EDITORIAL_EXACT_LOADED_INSTANCE_REQUIRED");
                if (PauseRequested(journal)) return false;
                LmStudioClient.PreflightCorpusEditorial(settings, apiKey, matches[0], job.Batch, job.Options, job.System); overall.Token.ThrowIfCancellationRequested();
                issued = new(jobHash, DateTimeOffset.UtcNow); store.WriteCampaignEditorial(plan.Id, task.Id, policy.Id, "issued", "EDITORIAL_ISSUED", issued);
                journal.Editorial(task, policy.Id, approvalHash, jobHash, true); checkpoint?.Invoke("EDITORIAL_ISSUED");
                var reply = await client.SendCorpusEditorialAsync(settings, apiKey, matches[0], job.Batch,
                    job.Options, job.System, overall.Token, progress).ConfigureAwait(false);
                receipt = new(jobHash, matches[0].InstanceId, matches[0].ContextLength, job.Endpoint, reply);
                store.WriteCampaignEditorial(plan.Id, task.Id, policy.Id, "receipt", "EDITORIAL_RECEIPT", receipt);
            }
            catch (Exception error) when (error is HttpRequestException or LmStudioException or OperationCanceledException or InvalidDataException)
            {
                var key = Environment.GetEnvironmentVariable("LM_STUDIO_API_KEY"); var message = key is { Length: > 0 } ? error.Message.Replace(key, "[REDACTED_API_TOKEN]", StringComparison.Ordinal) : error.Message;
                journal.State(issued == null ? "PAUSED_MODEL_OFFLINE" : "PAUSED_EDITORIAL_INDETERMINATE", message); return false;
            }
        }
        checkpoint?.Invoke("EDITORIAL_SAVED");
        if (!receipt.Reply.TransportComplete || receipt.Reply.Response.Rejection.Length != 0)
        { journal.State("PAUSED_EDITORIAL_INDETERMINATE", receipt.Reply.Response.Rejection); return false; }
        CorpusEditorialDecision decision;
        try { decision = Decide(jobHash, Hash(receipt), snapshot, LmStudioClient.ParseCorpusEditorial(receipt.Reply.Response.Content, job.Batch), job.Batch.Scope); }
        catch (Exception error) when (error is JsonException or InvalidDataException or InvalidOperationException or FormatException or KeyNotFoundException)
        { decision = new(jobHash, Hash(receipt), snapshot.Rows.Select(r => new CorpusEditorialRowDecision(r.Number, "EDITORIAL_REJECTED", "INVALID_EDITORIAL_JSON: " + error.GetType().Name, null)).ToArray()); }
        var previous = store.ReadCampaignEditorial<CorpusEditorialDecision>(plan.Id, task.Id, policy.Id, "decision", "EDITORIAL_DECISION");
        if (previous == null) store.WriteCampaignEditorial(plan.Id, task.Id, policy.Id, "decision", "EDITORIAL_DECISION", decision);
        else V4Rules.Require(Hash(previous) == Hash(decision), "EDITORIAL_DECISION_DRIFT");
        var decisionHash = Hash(decision);
        var reservations = new CorpusCandidateIndex(workspace).ReserveApprovals(run.Id, snapshot.ContentHash, decisionHash, approvalHash,
            decision.Rows.Where(r => r.State == "APPROVED").Select(r => r.Number).ToArray(), token);
        checkpoint?.Invoke("APPROVAL_RESERVED"); journal.CommitEditorial(task, decision, decisionHash, reservations);
        return !CheckQuality(journal, plan, policy, approvalHash);
    }

    public bool CheckQuality(CorpusCampaignJournal journal, CorpusCampaignPlan plan, CorpusCampaignPolicy policy, string approvalHash)
    {
        if (PauseRequested(journal)) return true;
        V4Rules.Require(qualityInterval is >= 1 and <= 10000, "CAMPAIGN_QUALITY_INTERVAL");
        var status = journal.Status(); var threshold = status.Approved / qualityInterval * qualityInterval;
        if (threshold > 0 && !store.CampaignMilestoneExists(plan.Id, (int)threshold, policy.Id, approvalHash))
        {
            var rows = journal.ApprovedRows();
            string Rank(CorpusCampaignAcceptedRow row) => Hash(new { plan.Id, Threshold = threshold, row.TaskId, row.Number });
            var balanced = rows.GroupBy(r => r.QuotaId).OrderBy(g => Hash(new { Quota = g.Key, Threshold = threshold }), StringComparer.Ordinal)
                .Take(80).Select(g => g.MinBy(Rank, StringComparer.Ordinal)!).ToList();
            var selected = balanced.Select(r => (r.TaskId, r.Number)).ToHashSet();
            balanced.AddRange(rows.Where(r => !selected.Contains((r.TaskId, r.Number))).OrderBy(Rank, StringComparer.Ordinal).Take(80 - balanced.Count));
            using var hash = IncrementalHash.CreateHash(HashAlgorithmName.SHA256);
            foreach (var row in rows) hash.AppendData(JsonSerializer.SerializeToUtf8Bytes(new { row.TaskId, row.Number, row.DecisionHash, TextHash = TextRules.Hash(row.Text) }, PromptProfiles.CompactJson));
            var sample = new
            {
                Threshold = threshold, ObservedApproved = status.Approved, PolicyId = policy.Id, ApprovalHash = approvalHash,
                plan.Id, plan.RegistryHash, plan.CoreHash, plan.Spec.ModelId, AtUtc = DateTimeOffset.UtcNow, State = "FROZEN_FOR_SPOT_REVIEW_NOT_OWNER_ACCEPTED",
                SourcesHash = Convert.ToHexString(hash.GetHashAndReset()).ToLowerInvariant(),
                Speech = CorpusQuality.MeasureSpeech(rows.Select(r => r.Text)),
                Quotas = rows.GroupBy(r => r.QuotaId).Select(g => new { Coverage = plan.Coverage.Single(c => c.QuotaId == g.Key), Approved = g.Count(), Speech = CorpusQuality.MeasureSpeech(g.Select(r => r.Text)) }).ToArray(),
                Samples = balanced.Select(r => new { r.RunId, r.Number, r.Text, r.QuotaId, r.DecisionHash, Scenario = store.GetRun(r.RunId).Scenario }).ToArray(),
                Note = "Детерминированная выборка до80 строк со стратификацией; при>80 квотах не все представлены. Model approval не доказывает художественное качество или semantic recall."
            };
            store.SaveCampaignMilestone(plan.Id, (int)threshold, sample);
        }
        if (!journal.EditorialQualityRegression()) return false;
        journal.State("PAUSED_QUALITY_REGRESSION", "В последних восьми редакционных партиях принято меньше25% строк; проверьте контекст, повторы, формат и профиль."); return true;
    }

    private static bool PauseRequested(CorpusCampaignJournal journal)
    {
        var desired = journal.Status().Desired;
        if (desired is not ("PAUSE" or "STOP")) return false;
        journal.State(desired == "STOP" ? "STOPPED" : "PAUSED", "Запрос управления подтверждён до следующего editorial POST."); return true;
    }

    internal static CorpusEditorialDecision Decide(string jobHash, string receiptHash, CorpusCampaignDraftSnapshot snapshot, IReadOnlyList<CorpusEditorialVerdict> verdicts, V4GenerationRequest scope)
    {
        var rows = snapshot.Rows.Select(row =>
        {
            var verdict = verdicts.Single(v => v.Id == "c" + row.Number);
            var fatal = row.Diagnostics.FirstOrDefault(d => FatalDiagnostics.Contains(d, StringComparer.Ordinal));
            string state, reason;
            using var wire = JsonDocument.Parse(row.JsonLine);
            var maturity = wire.RootElement.TryGetProperty("maturity", out var mature) ? mature.GetString() : "GENERAL";
            var profanity = wire.RootElement.TryGetProperty("profanity", out var profane) ? profane.GetString() : "NONE";
            var allowedProfanity = verdict.Profanity == "NONE" || scope.Style.StrongProfanityRequested
                && (scope.ProfanityLimit is null or "STRONG" || scope.ProfanityLimit == "MILD" && verdict.Profanity == "MILD");
            if (row.ExistingExact || fatal != null) { state = "EDITORIAL_REJECTED"; reason = fatal ?? "EXACT_DUPLICATE"; }
            else if (verdict.Uncertain || verdict.Duplicate == "UNCERTAIN" || verdict.Lore != "NONE" || verdict.Maturity == "UNCERTAIN" || verdict.Profanity == "UNCERTAIN")
            { state = "PENDING"; reason = "EDITORIAL_UNCERTAIN_OR_UNVERIFIED: " + verdict.Reason; }
            else if (verdict.Duplicate == "SAME" || !verdict.Act || !verdict.Profile || verdict.Naturalness < 4 || verdict.Context < 4
                || verdict.Character < 3 || verdict.Grammar < 4 || !allowedProfanity || verdict.Maturity == "MATURE" && !scope.Style.MatureRequested
                || scope.Mode != "TRIGGER" && (verdict.Maturity != maturity || verdict.Profanity != profanity))
            { state = "EDITORIAL_REJECTED"; reason = "EDITORIAL_QUALITY_OR_SCOPE: " + verdict.Reason; }
            else { state = "APPROVED"; reason = verdict.Reason; }
            return new CorpusEditorialRowDecision(row.Number, state, reason, verdict);
        }).ToArray();
        return new(jobHash, receiptHash, rows);
    }
}

public sealed partial class PromptLabStore
{
    private string CampaignMilestoneName(string campaignId, int threshold)
    { RequireId(campaignId); V4Rules.Require(threshold is >= 1 and <= 1000000, "CAMPAIGN_MILESTONE_THRESHOLD"); return "campaigns/" + campaignId + "/quality-milestones/" + threshold.ToString("D8") + ".json"; }
    internal bool CampaignMilestoneExists(string campaignId, int threshold, string policyId, string approvalHash)
    {
        var name = CampaignMilestoneName(campaignId, threshold); if (!File.Exists(Local(name))) return false;
        var data = Read<JsonElement>(name, "CAMPAIGN_QUALITY_MILESTONE");
        V4Rules.Require(data.GetProperty("Threshold").GetInt64() == threshold && data.GetProperty("PolicyId").GetString() == policyId
            && data.GetProperty("ApprovalHash").GetString() == approvalHash, "CAMPAIGN_MILESTONE_DRIFT"); return true;
    }
    internal void SaveCampaignMilestone<T>(string campaignId, int threshold, T value) => Write(CampaignMilestoneName(campaignId, threshold), "CAMPAIGN_QUALITY_MILESTONE", value, false);
    private string CampaignEditorialName(string campaignId, string taskId, string policyId, string leaf)
    {
        RequireId(campaignId); RequireId(taskId); RequireId(policyId);
        V4Rules.Require(leaf is "request" or "issued" or "receipt" or "decision", "EDITORIAL_HISTORY_LEAF");
        return "campaigns/" + campaignId + "/tasks/" + taskId + "/editorial/" + policyId + "/" + leaf + ".json";
    }
    internal T? ReadCampaignEditorial<T>(string campaignId, string taskId, string policyId, string leaf, string kind) where T : class
    {
        var relative = CampaignEditorialName(campaignId, taskId, policyId, leaf); return File.Exists(Local(relative)) ? Read<T>(relative, kind) : null;
    }
    internal void WriteCampaignEditorial<T>(string campaignId, string taskId, string policyId, string leaf, string kind, T value)
        => Write(CampaignEditorialName(campaignId, taskId, policyId, leaf), kind, value, false);
}
