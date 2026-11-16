using System.Text.Json;
using Microsoft.Data.Sqlite;

namespace PhantomSemanticStudio.Core;

internal sealed record CorpusCampaignDraftRow(int Number, string Text, string JsonLine, string[] Diagnostics, bool ExistingExact, IReadOnlyList<CorpusIndexNeighbor> Neighbors);
internal sealed record CorpusCampaignDraftSnapshot(string RunId, string RunHash, string ScopeHash, string ContentHash, IReadOnlyList<CorpusCampaignDraftRow> Rows);

/// <summary>Serial production. Approval requires a separate owner-approved policy; import and installation are never automatic.</summary>
public sealed class CorpusCampaignWorker : IDisposable
{
    private readonly WorkspaceStore workspace;
    private readonly PromptLabStore store;
    private readonly PromptLabSession session;
    private readonly Func<int, HttpClient> httpFactory;
    private readonly Action<string>? checkpoint;
    public CorpusCampaignWorker(WorkspaceStore workspace, Func<int, HttpClient>? httpFactory = null) : this(workspace, httpFactory, null) { }
    internal CorpusCampaignWorker(WorkspaceStore workspace, Func<int, HttpClient>? httpFactory, Action<string>? checkpoint)
    { this.workspace = workspace; store = new(workspace); this.httpFactory = httpFactory ?? LmStudioClient.CreateHttpClient; session = new(store, this.httpFactory); this.checkpoint = checkpoint; }
    private static string Hash<T>(T value) => V4Rules.Hash(JsonSerializer.SerializeToUtf8Bytes(value, PromptProfiles.CompactJson));
    public void Dispose() => session.Dispose();

    public async Task<CorpusCampaignStatus> RunAsync(string planId, string expectedHash, int maxBatches, CancellationToken token,
        IProgress<PromptLabProgress>? progress = null, bool resume = false, string? policyId = null, string? approvalHash = null)
    {
        V4Rules.Require(maxBatches is >= 1 and <= 1000000, "CAMPAIGN_INVOCATION_LIMIT");
        token.ThrowIfCancellationRequested(); workspace.Check();
        using var singleWorker = new FileStream(PathSafety.ResolveRelative(workspace.Root, "campaign-worker.lock"), FileMode.OpenOrCreate, FileAccess.ReadWrite, FileShare.None);
        var campaign = new CorpusCampaign(store); var plan = campaign.Check(planId, expectedHash);
        V4Rules.Require((policyId == null) == (approvalHash == null), "CAMPAIGN_POLICY_AND_APPROVAL_HASH_REQUIRED");
        var policies = new CorpusCampaignPolicies(store); CorpusCampaignPolicy? policy = null;
        if (policyId != null)
        {
            _ = policies.CheckApproved(policyId, approvalHash!); policy = policies.Get(policyId);
            V4Rules.Require(policy.Spec.CampaignId == planId && policy.Spec.PlanHash == expectedHash, "CAMPAIGN_POLICY_PLAN_MISMATCH");
        }
        var journal = new CorpusCampaignJournal(workspace, planId, expectedHash);
        if (policyId != null) journal.BindPolicy(policyId, approvalHash!);
        journal.Start(resume);
        var editorial = new CorpusCampaignEditorial(workspace, httpFactory, checkpoint);
        var index = new CorpusCandidateIndex(workspace); var processed = 0;
        while (processed < maxBatches)
        {
            token.ThrowIfCancellationRequested();
            try { plan = campaign.Check(planId, expectedHash); if (policy != null) _ = policies.CheckApproved(policy.Id, approvalHash!); }
            catch (InvalidDataException error) { journal.State("PAUSED_POLICY_DRIFT", error.Message); return journal.Status(); }
            var status = journal.Status();
            if (status.Desired is "PAUSE" or "STOP") { journal.State(status.Desired == "STOP" ? "STOPPED" : "PAUSED", "Запрос управления подтверждён на границе партии."); return journal.Status(); }
            if (policy != null)
            {
                if (editorial.CheckQuality(journal, plan, policy, approvalHash!)) return journal.Status();
                if (journal.TargetsMet(plan)) { journal.State("TARGET_REACHED", "Целевые квоты приняты по закреплённой политике; экспорт/импорт отдельны."); return journal.Status(); }
                var pending = journal.PendingEditorialTask();
                if (pending != null)
                {
                    if (!await editorial.ReviewAsync(journal, plan, pending, policy, approvalHash!, token, new WorkerProgress(journal, pending, progress, false)).ConfigureAwait(false)) return journal.Status();
                    continue;
                }
            }
            if (status.Pending >= 300) { journal.State("PAUSED_REVIEW", "Накоплено 300 непроверенных строк; approved=0."); return journal.Status(); }
            var drive = new DriveInfo(Path.GetPathRoot(workspace.Root)!);
            if (!drive.IsReady || drive.AvailableFreeSpace < 256L * 1024 * 1024) { journal.State("PAUSED_RESOURCE", "Недостаточно свободного места: нужен резерв 256 MiB."); return journal.Status(); }
            var task = journal.Reserve(plan, policy != null);
            if (task == null) { journal.State("BUDGET_EXHAUSTED", "Лимит попыток исчерпан; цель accepted не подтверждена."); return journal.Status(); }
            var quota = plan.Spec.Quotas.Single(q => q.Id == task.QuotaId); var production = store.GetProduction(quota.ProductionId);
            var version = production.Version; var scope = new[] { version.Scenario }.Concat(version.AdditionalScenarios).ElementAt(quota.ScenarioIndex);
            if (policy != null) scope = policies.Scope(policy.Id, task.QuotaId, journal.QuotaAttempts(task.QuotaId) - 1);
            if (status.Pending + scope.Count > 300) { journal.State("PAUSED_REVIEW", "Следующая партия превысит предел непроверенных строк."); return journal.Status(); }
            PromptLabRun run;
            if (task.RunId.Length == 0)
            {
                var orphans = task.State == "NEW_RESERVED" ? [] : store.ListRuns(version.ProfileId).Where(r => r.ExperimentId == task.Id && r.Purpose == "GENERATION").ToArray();
                V4Rules.Require(orphans.Length <= 1, "CAMPAIGN_ORPHAN_RUN_AMBIGUOUS");
                run = orphans.SingleOrDefault() ?? store.BeginRun(version.Id, scenario: scope, experimentId: task.Id);
                checkpoint?.Invoke("RUN_CREATED"); journal.Bind(task, run.Id); task = task with { RunId = run.Id, State = "BOUND" };
            }
            else run = store.GetRun(task.RunId);
            V4Rules.Require(run.VersionId == version.Id && run.ExperimentId == task.Id && Hash(run.Scenario ?? version.Scenario) == Hash(scope), "CAMPAIGN_RUN_SCOPE_DRIFT");
            if (run.State is "ISSUED" or "INDETERMINATE" or "CANCELLED" || store.GetTransport(run.Id) != null) journal.MarkIssued(task);
            if (run.State is "INDETERMINATE" or "CANCELLED" || run.State == "ISSUED" && store.GetTransport(run.Id) == null)
            { journal.State("PAUSED_INDETERMINATE", "Неизвестный POST не повторяется: " + run.Id); return journal.Status(); }
            if (!run.CompletedAtUtc.HasValue)
            {
                run = await session.ResumeAsync(run.Id, new StudioSettings { HighFiveRoot = workspace.ProtectedRoot, Endpoint = plan.Spec.Endpoint, ModelId = plan.Spec.ModelId },
                    Environment.GetEnvironmentVariable("LM_STUDIO_API_KEY") ?? "", plan.Spec.ModelId, token, new WorkerProgress(journal, task, progress), requireExactInstance: true).ConfigureAwait(false);
            }
            if (run.State is "INDETERMINATE" or "CANCELLED" || store.GetTransport(run.Id) != null) journal.MarkIssued(task);
            checkpoint?.Invoke("GENERATION_SAVED");
            if (run.State is "INDETERMINATE" or "CANCELLED") { journal.State("PAUSED_INDETERMINATE", run.Failure); return journal.Status(); }
            if (run.State != "COMPLETE")
            {
                journal.Reject(task, store.GetTransport(run.Id) != null); processed++;
                if (run.Response == null || run.Failure.Contains("TIMEOUT", StringComparison.Ordinal))
                { journal.State("PAUSED_MODEL_OFFLINE", run.Failure); return journal.Status(); }
                if (policy == null ? journal.Status().RejectedBatches >= 5 : journal.RepeatedGenerationFailures())
                { journal.State("PAUSED_QUALITY_REGRESSION", "Повторяются отклонённые партии; нужен разбор промпта/формата."); return journal.Status(); }
                continue;
            }
            var runHash = Hash(run); var scopeHash = Hash(scope); var contentHash = V4Rules.Hash(V4Rules.Utf8.GetBytes(run.Response!.Content));
            var snapshot = store.GetCampaignDraftSnapshot(planId, task.Id);
            if (snapshot == null)
            {
                var wire = V4CorpusGeneration.ToJsonl(run.Response.Content, version.Generation.Apply(scope), store.EffectiveRegistry());
                var lines = wire.Split('\n', StringSplitOptions.RemoveEmptyEntries);
                var rows = run.Rows.Select(row =>
                {
                    var neighbors = index.Find(row.Text).Where(n => n.Source.SourceId != run.Id).ToArray();
                    return new CorpusCampaignDraftRow(row.Number, row.Text, lines[row.Number], row.Diagnostics, neighbors.Any(n => n.Exact), neighbors);
                }).ToArray();
                snapshot = new(run.Id, runHash, scopeHash, contentHash, rows); store.SaveCampaignDraftSnapshot(planId, task.Id, snapshot);
            }
            V4Rules.Require(snapshot.RunId == run.Id && snapshot.RunHash == runHash && snapshot.ScopeHash == scopeHash && snapshot.ContentHash == contentHash, "CAMPAIGN_PREINDEX_SNAPSHOT_DRIFT");
            var indexed = index.Append(snapshot.Rows.Select(row => new CorpusIndexRecord(run.Id, contentHash, row.Number, scope.Category, scope.Mode, row.Text)).ToArray(), token);
            checkpoint?.Invoke("INDEX_APPENDED"); journal.Commit(task, snapshot, indexed.Items); processed++;
            journal.Stage("Партия сохранена; ожидает редакционной проверки.");
            if (policy != null && !await editorial.ReviewAsync(journal, plan, task, policy, approvalHash!, token, new WorkerProgress(journal, task, progress, false)).ConfigureAwait(false)) return journal.Status();
        }
        var last = journal.Status();
        journal.State(last.Desired == "STOP" ? "STOPPED" : last.Desired == "PAUSE" ? "PAUSED" : policy != null && journal.TargetsMet(plan) ? "TARGET_REACHED" : last.Attempts >= plan.Spec.MaxRequests ? "BUDGET_EXHAUSTED" : "PAUSED",
            "Порция работы завершена; импорт не выполнялся.");
        return journal.Status();
    }

    private sealed class WorkerProgress(CorpusCampaignJournal journal, CorpusCampaignTask task, IProgress<PromptLabProgress>? observer, bool generation = true) : IProgress<PromptLabProgress>
    {
        private bool observedIssued;
        public void Report(PromptLabProgress value)
        {
            try
            {
                // Session emits transport progress only after durable Lab MarkIssued. This is an upper bound, not proof of server processing.
                if (generation && !observedIssued) { journal.MarkIssued(task); observedIssued = true; }
                journal.Stage(value.Stage);
            }
            catch (Exception error) when (error is SqliteException or IOException) { }
            observer?.Report(value);
        }
    }
}

public sealed partial class PromptLabStore
{
    private string CampaignSnapshotName(string planId, string taskId)
    { RequireId(planId); RequireId(taskId); return "campaigns/" + planId + "/tasks/" + taskId + "/preindex.json"; }
    internal CorpusCampaignDraftSnapshot? GetCampaignDraftSnapshot(string planId, string taskId)
    {
        var name = CampaignSnapshotName(planId, taskId); return File.Exists(Local(name)) ? Read<CorpusCampaignDraftSnapshot>(name, "CAMPAIGN_PREINDEX") : null;
    }
    internal void SaveCampaignDraftSnapshot(string planId, string taskId, CorpusCampaignDraftSnapshot snapshot)
        => Write(CampaignSnapshotName(planId, taskId), "CAMPAIGN_PREINDEX", snapshot, false);
}
