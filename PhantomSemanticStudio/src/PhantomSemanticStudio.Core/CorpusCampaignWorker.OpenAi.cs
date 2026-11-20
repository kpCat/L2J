using System.Text.Json;
using Microsoft.Data.Sqlite;

namespace PhantomSemanticStudio.Core;

public sealed partial class CorpusCampaignWorker
{
    public Task<OpenAiCampaignView> RunOpenAiAsync(string id, string hash, OpenAiResponsesClient client, string apiKey,
        OpenAiBudget budget, bool paidConfirmed, bool resume, CancellationToken token,
        IProgress<OpenAiCampaignView>? progress = null) =>
        RunOpenAiCore(id, hash, client, apiKey, budget, paidConfirmed, resume, token, progress);

    internal async Task<OpenAiCampaignView> RunOpenAiCore(string id, string hash, OpenAiResponsesClient client, string apiKey,
        OpenAiBudget budget, bool paidConfirmed, bool resume, CancellationToken token, IProgress<OpenAiCampaignView>? progress = null,
        int postLimit = int.MaxValue, string? testLedger = null)
    {
        token.ThrowIfCancellationRequested();
        V4Rules.Require(paidConfirmed && postLimit > 0, "OPENAI_PAID_CONFIRMATION_REQUIRED");
        OpenAiResponsesClient.ValidateKey(apiKey);
        workspace.Check();
        using var singleWorker = new FileStream(PathSafety.ResolveRelative(workspace.Root, "campaign-worker.lock"),
            FileMode.OpenOrCreate, FileAccess.ReadWrite, FileShare.None);
        var campaign = new CorpusCampaign(store);
        var plan = campaign.CheckOpenAi(id, hash);
        plan.Settings.Validate(paid: true);
        budget.Validate();
        var quality = !plan.Pilot && testLedger == null ? campaign.RequireOpenAiApproval(plan) : null;
        var journal = new CorpusCampaignJournal(workspace, id, hash);
        var ledger = new OpenAiSpendLedger(workspace, testLedger);
        using var sharedWorker = ledger.AcquireWorker();
        var index = new CorpusCandidateIndex(workspace, V4Registry.Default);
        var prior = journal.Status();
        var latest = journal.LatestOpenAi();
        if (latest?.State == "PREPARED")
        {
            ledger.ReleaseUndispatched(id, latest.Id);
            journal.AbandonOpenAiWork(latest.Id);
        }
        latest = journal.LatestOpenAi(includeUndispatched: false);
        if (latest?.Cost is long priorCost) ledger.Settle(id, latest.Id, priorCost);
        if (latest?.State is "ISSUED" or "INDETERMINATE" or "MODEL_OR_TIER_DRIFT" or "USAGE_DRIFT")
            return End("PAUSED_INDETERMINATE", "У запроса неопределённый результат или расход. Проверьте журнал и кабинет OpenAI.");
        if (latest?.State is "REFUSAL" or "SECURITY_REDACTED")
            return End("PAUSED_" + latest.State, "Запрос остановлен. Этот сценарий не будет отправлен повторно.");
        if (plan.Pilot && latest?.State is "INVALID_OUTPUT" or "INCOMPLETE" or "UNEXPECTED_OUTPUT")
            return End("CALIBRATION_INCOMPLETE_OUTPUT", "Сцену нужно разобрать перед созданием нового пилота.");
        if (prior.State is "TARGET_REACHED" or "CALIBRATION_COMPLETE") return View();
        if (!resume && prior.State != "READY") return View();
        if (!resume && prior.Desired == "STOP") return End("STOPPED", "Кампания остановлена владельцем.");
        journal.Start(resume);
        var posts = 0;
        var consecutiveRateLimits = 0;

        try
        {
            if (progress != null) progress.Report(View());
            while (true)
            {
                token.ThrowIfCancellationRequested();
                if (latest?.State == "RECEIVED")
                {
                    var outcome = FinishOpenAi(plan, latest, journal, index, token);
                    consecutiveRateLimits = outcome == "RATE_LIMIT" ? consecutiveRateLimits + 1 : 0;
                    if (plan.Pilot && outcome is "INVALID_OUTPUT" or "INCOMPLETE" or "UNEXPECTED_OUTPUT")
                        return End("CALIBRATION_INCOMPLETE_OUTPUT", "Сцену нужно разобрать перед созданием нового пилота.");
                    if (outcome is not ("DRAFT_SAVED" or "INVALID_OUTPUT" or "RATE_LIMIT"))
                    {
                        var uncertain = outcome is "INDETERMINATE" or "MODEL_OR_TIER_DRIFT" or "USAGE_DRIFT" ||
                            latest.Cost.HasValue && latest.Cost.Value > latest.Held;
                        return End(uncertain ? "PAUSED_INDETERMINATE" : "PAUSED_" + outcome, "Ответ зафиксирован в журнале.");
                    }
                    latest = journal.LatestOpenAi();
                    progress?.Report(View());
                }

                if (latest?.State == "RATE_LIMIT")
                {
                    if (consecutiveRateLimits >= 4) return End("PAUSED_RATE_LIMIT", "Четыре последовательных отказа по частоте.");
                    var waitUntil = latest.Reply!.RetryAtUtc ?? latest.AtUtc + TimeSpan.FromSeconds(2);
                    while (waitUntil > DateTimeOffset.UtcNow && journal.Status().Desired == "RUN")
                    {
                        var wait = waitUntil - DateTimeOffset.UtcNow;
                        if (wait > TimeSpan.Zero)
                            await Task.Delay(wait < TimeSpan.FromSeconds(1) ? wait : TimeSpan.FromSeconds(1), token).ConfigureAwait(false);
                    }
                }

                var state = journal.Status();
                if (state.Desired is "STOP" or "PAUSE")
                    return state.Desired == "STOP" ? End("STOPPED", "Кампания остановлена владельцем.")
                        : End("PAUSED", "Текущий запрос завершён и сохранён. Продолжение доступно.");
                if (!plan.Pilot && journal.OpenAiRegressed(plan.Settings.QualityPercent))
                    return End("PAUSED_QUALITY_REGRESSION", "Доля уникальных валидных строк или оценок ниже принятого порога.");
                if (quality != null)
                    V4Rules.Require(campaign.RequireOpenAiApproval(plan) == quality, "OPENAI_QUALITY_EVIDENCE_DRIFT");
                var counts = journal.OpenAiProgress();
                var next = NextOpenAi(plan, latest, counts);
                if (next == null)
                {
                    if (!plan.Pilot) return End("TARGET_REACHED", "Квоты уникальных валидных черновиков выполнены.");
                    var successful = counts.Count == plan.Lanes.Count && counts.Values.Sum(value => value.Unique) >= 30;
                    return successful ? End("CALIBRATION_COMPLETE", "Нужна слепая ручная оценка. Качество пока не подтверждено.")
                        : End("CALIBRATION_INCOMPLETE_OUTPUT", "Часть контрольных сцен осталась без валидных уникальных строк.");
                }
                if (plan.Pilot && state.Issued >= OpenAiCampaignPlan.PilotMaxPosts)
                    return End("CALIBRATION_INCOMPLETE_LIMIT", "Достигнут предел 20 запросов калибровки.");
                if (posts >= postLimit) return End("PAUSED", "Лимит запросов текущего запуска.");
                var drive = new DriveInfo(Path.GetPathRoot(workspace.Root)!);
                if (!drive.IsReady || drive.AvailableFreeSpace < 256L * 1024 * 1024)
                    return End("PAUSED_RESOURCE", "Нужен резерв свободного места 256 МиБ.");
                var (lane, batch, target, requested) = next.Value;
                var prepared = OpenAiCampaignPrompts.Prepare(lane, requested, batch, plan.Settings.Model, plan.Settings.MatureOptIn);
                V4Rules.Require(!OpenAiResponsesClient.HasSecret(V4Rules.Utf8.GetString(prepared.Post.Body), apiKey), "OPENAI_OUTBOUND_SECRET");
                var price = plan.Settings.Price!;
                var held = OpenAiSpendLedger.Amount(price.Reserve(prepared.Post.MaxInput, prepared.Post.MaxOutput));
                var work = new OpenAiWork(Guid.NewGuid().ToString("N"), lane.Id, batch, target, requested, prepared.Post.Hash,
                    prepared.SceneHash, plan.Settings.Model, held, DateTimeOffset.UtcNow);
                if (!journal.PrepareOpenAiWork(work)) continue;
                if (!ledger.Reserve(id, work, plan.Pilot, budget))
                {
                    journal.AbandonOpenAiWork(work.Id);
                    return End(plan.Pilot ? "CALIBRATION_INCOMPLETE_BUDGET" : "PAUSED_BUDGET",
                        "Следующий запрос не укладывается в выделенный лимит.");
                }
                checkpoint?.Invoke("OPENAI_RESERVED");
                if (token.IsCancellationRequested || !journal.IssueOpenAiWork(work.Id))
                {
                    ledger.ReleaseUndispatched(id, work.Id);
                    journal.AbandonOpenAiWork(work.Id);
                    continue;
                }
                posts++;
                checkpoint?.Invoke("OPENAI_ISSUED");
                var reply = await client.GenerateAsync(prepared.Post, apiKey, token).ConfigureAwait(false);
                var resolved = quality?.ResolvedModel ?? (plan.Pilot ? journal.OpenAiResolvedModel() : "");
                if (reply.Model.Length > 0 && resolved.Length > 0 && reply.Model != resolved)
                    reply = reply with { Status = "MODEL_OR_TIER_DRIFT", Text = "" };
                var cost = reply.Usage != null ? OpenAiSpendLedger.Amount(price.Cost(reply.Usage))
                    : reply.Status is "AUTH" or "BILLING" or "HTTP_400" or "RATE_LIMIT" or "RATE_LIMIT_STOP" or "REFUSAL" ? (long?)0 : null;
                if (cost > work.Held) reply = reply with { Status = "USAGE_DRIFT", Text = "" };
                journal.ReceiveOpenAi(work, reply, cost);
                checkpoint?.Invoke("OPENAI_RECEIVED");
                if (cost.HasValue) ledger.Settle(id, work.Id, cost.Value);
                latest = work with { Reply = reply, Cost = cost, State = "RECEIVED" };
            }
        }
        catch (OperationCanceledException)
        {
            return End("PAUSED", "Работа приостановлена. Журнал сохранён.");
        }
        catch (Exception error) when (error is IOException or UnauthorizedAccessException or SqliteException)
        {
            return End("PAUSED_RESOURCE", "Проверьте доступность и свободное место частного хранилища.");
        }

        OpenAiCampaignView View()
        {
            var status = journal.OpenAiView(plan);
            var used = ledger.Spent(id, DateTimeOffset.UtcNow);
            return status with { LocalAvailableUsd = budget.Remaining(plan.Pilot, used.Campaign, used.Campaign, used.Daily, used.Account, used.Monthly) };
        }

        OpenAiCampaignView End(string state, string message)
        {
            journal.State(state, message);
            var view = View();
            progress?.Report(view);
            return view;
        }
    }

    private static (OpenAiCampaignLane Lane, int Batch, int Target, int Requested)? NextOpenAi(OpenAiCampaignPlan plan,
        OpenAiWork? latest, IReadOnlyDictionary<string, OpenAiLaneCounts> counts)
    {
        if (plan.Pilot)
        {
            var scene = plan.Lanes.FirstOrDefault(lane => (counts.GetValueOrDefault(lane.Id)?.Unique ?? 0) == 0);
            if (scene == null) return null;
            return (scene, 1, scene.Target, scene.Target);
        }

        var lanes = plan.Lanes.Where(lane => (counts.GetValueOrDefault(lane.Id)?.Unique ?? 0) < lane.Target).ToArray();
        if (lanes.Length == 0) return null;
        var active = lanes.FirstOrDefault(lane => lane.Id == latest?.LaneId
            && (counts.GetValueOrDefault(lane.Id)?.InBatch ?? 0) < latest.BatchTarget);
        var selected = active ?? lanes.OrderBy(lane => counts.GetValueOrDefault(lane.Id)?.Batch ?? 0).First();
        var stats = counts.GetValueOrDefault(selected.Id) ?? new(0, 0, 0, 0);
        var batch = active == null ? stats.Batch + 1 : stats.Batch;
        var inBatch = active == null ? 0 : stats.InBatch;
        var target = active == null ? Math.Min(OpenAiCampaignPlan.BatchSize, selected.Target - stats.Unique) : latest!.BatchTarget;
        return (selected with { Format = plan.Settings.Format }, batch, target, Math.Min(plan.Settings.Microbatch, target - inBatch));
    }

    private string FinishOpenAi(OpenAiCampaignPlan plan, OpenAiWork work, CorpusCampaignJournal journal,
        CorpusCandidateIndex index, CancellationToken token)
    {
        var reply = work.Reply!;
        if (reply.Status != "COMPLETE")
        {
            journal.CommitOpenAi(work, reply.Status, 0, []);
            return reply.Status;
        }
        var lane = plan.Lanes.Single(lane => lane.Id == work.LaneId);
        if (!plan.Pilot) lane = lane with { Format = plan.Settings.Format };
        var prepared = OpenAiCampaignPrompts.Prepare(lane, work.Requested, work.Batch, plan.Settings.Model, plan.Settings.MatureOptIn);
        V4Rules.Require(prepared.Post.Hash == work.PromptHash && prepared.SceneHash == work.SceneHash, "OPENAI_RECEIPT_SCOPE_DRIFT");
        var scope = prepared.Scope;
        var registry = V4Registry.Default;
        var act = registry.Categories.Single(entry => entry.Key == scope.Category).Act;
        string[] lines;
        var parsed = new List<(int Number, string Json, V4Entry Entry)>();
        try
        {
            var jsonl = OpenAiCampaignPrompts.CanonicalJsonl(reply.Text, lane, scope);
            lines = jsonl.Split('\n', StringSplitOptions.RemoveEmptyEntries);
            for (var row = 1; row < lines.Length; row++)
            {
                using var json = JsonDocument.Parse(lines[row]);
                try
                {
                    var entry = V4BatchReader.ReadRecord(json.RootElement, registry, scope.Category, act, scope.Mode);
                    parsed.Add((row, lines[row], entry));
                }
                catch (InvalidDataException) { }
            }
        }
        catch (Exception error) when (error is InvalidDataException or JsonException or FormatException or InvalidOperationException or KeyNotFoundException)
        {
            journal.CommitOpenAi(work, "INVALID_OUTPUT", 0, []);
            return "INVALID_OUTPUT";
        }

        if (parsed.Count == 0)
        {
            journal.CommitOpenAi(work, "INVALID_OUTPUT", lines.Length - 1, []);
            return "INVALID_OUTPUT";
        }

        var entries = parsed.Select(row => row.Entry).ToArray();
        var exampleEchoes = scope.Components == null ? [] : PromptHierarchy.CheckExampleEcho(scope.Components, entries);
        var findings = CorpusQuality.Check(entries, scope, []).Concat(OpenAiCampaignPrompts.SceneIssues(lane, scope, entries)).Concat(exampleEchoes)
            .Where(issue => issue.Record > 0).ToLookup(issue => issue.Record);
        var sameArm = plan.Pilot
            ? journal.OpenAiPriorPilotTexts(plan.Lanes.Where(other => other.Format == lane.Format).Select(other => other.Id))
            : [];
        var sourceHash = V4Rules.Hash(V4Rules.Utf8.GetBytes(reply.Text));
        // Look up previous sources before appending, and ignore this receipt on replay after a crash.
        var rows = parsed.Select((row, i) =>
        {
            var diagnostics = findings[i + 1].Select(issue => issue.Code).ToHashSet(StringComparer.Ordinal);
            var matches = plan.Pilot ? [] : index.FindProduction(row.Entry.Text, work.Id, token);
            if (matches.Any(item => item.Exact) || sameArm.Any(before => V4Rules.Normalize(before) == V4Rules.Normalize(row.Entry.Text)))
                diagnostics.Add("EXACT_DUPLICATE");
            else if (matches.Any(item => item.LexicalNearClone) || sameArm.Any(before => CorpusQuality.NearClone(before, row.Entry.Text)))
                diagnostics.Add("NEAR_DUPLICATE_REVIEW");
            if (matches.Any(item => item.RepeatedOpening)) diagnostics.Add("REPEATED_OPENING_REVIEW");
            var unique = !diagnostics.Any(StopsDraft);
            return new OpenAiCheckedRow(row.Number, row.Entry.Text, row.Json, 0, string.Join(',', diagnostics.Order()), unique);
        }).ToArray();
        if (!plan.Pilot)
        {
            var indexed = index.Append(rows.Select(row => new CorpusIndexRecord(work.Id, sourceHash, row.Number, scope.Category,
                scope.Mode, row.Text)).ToArray(), token).Items.ToDictionary(item => item.Number);
            for (var i = 0; i < rows.Length; i++)
                rows[i] = rows[i] with { TextId = indexed[rows[i].Number].TextId };
        }

        var draft = rows.Where(row => row.Unique).Select(row => row.JsonLine).ToArray();
        if (draft.Length > 0)
            store.SaveOpenAiDraft(plan.Id, work.Id, OpenAiJsonl(scope.Category, scope.Mode, draft));
        checkpoint?.Invoke("OPENAI_DRAFT_WRITTEN");
        var outcome = draft.Length == 0 ? "INVALID_OUTPUT" : "DRAFT_SAVED";
        journal.CommitOpenAi(work, outcome, lines.Length - 1, rows);
        return outcome;
    }

    private static bool StopsDraft(string code) =>
        code != "REPEATED_OPENING_REVIEW" && code != "MIXED_SCRIPT_TOKEN_REVIEW" && code != "MATURE_SUBSTANCE_REVIEW";

    internal static byte[] OpenAiJsonl(string category, string mode, IReadOnlyList<string> rows)
    {
        var header = JsonSerializer.Serialize(new
        {
            schema = "pw-semantic-batch/1", category, mode, act = V4Registry.Default.Categories.Single(item => item.Key == category).Act,
            language = "ru", count = rows.Count
        }, PromptProfiles.CompactJson);
        return V4Rules.Utf8.GetBytes(header + "\n" + string.Join('\n', rows) + "\n");
    }
}

public sealed partial class PromptLabStore
{
    internal void SaveOpenAiDraft(string id, string task, byte[] jsonl)
    {
        RequireId(id);
        RequireId(task);
        SaveCampaignFile(Local("campaigns/" + id + "/drafts/" + task + ".jsonl"), jsonl);
    }
}
