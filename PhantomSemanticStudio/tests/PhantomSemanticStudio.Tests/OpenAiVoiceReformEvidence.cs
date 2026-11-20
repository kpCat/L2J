using System.Text.Json;
using PhantomSemanticStudio.Core;

internal static partial class Program
{
    private const string HistoricTerraPilot = "147490c39ea0406ca0d8e9e4589f7cc8";
    private const string PilotModel = OpenAiTariff.SuggestedModel;

    private static async Task<int> OpenAiVoiceReformEvidence(string source, string privateRoot, string mode)
    {
        if (!OperatingSystem.IsWindows()) throw new PlatformNotSupportedException("OPENAI_VOICE_REFORM_WINDOWS");
        V4Rules.Require(mode is "offline" or "paid-confirmed", "OPENAI_VOICE_REFORM_ARGS");
        var original = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "PhantomSemanticStudio", "workspace");
        PathSafety.AssertDisjoint(PathSafety.Canonical(privateRoot), original);
        using var workspace = new WorkspaceStore(privateRoot, source);
        var store = new PromptLabStore(workspace);
        var evidence = PathSafety.ResolveRelative(workspace.Root, "voice-reform-evidence");
        Directory.CreateDirectory(evidence);
        if (mode == "offline")
        {
            WriteRouteCensus(evidence);
            CheckHistoricalCopy(workspace, store, original, evidence);
            return 0;
        }

        // Load the owner's monetary limits into the isolated lab. The API key is only read later through DPAPI.
        var settings = "labs/prompt-lab/openai-settings.json";
        var isolatedSettings = PathSafety.ResolveRelative(workspace.Root, settings);
        Directory.CreateDirectory(Path.GetDirectoryName(isolatedSettings)!);
        File.Copy(PathSafety.ResolveRelative(original, settings), isolatedSettings, false);
        var saved = store.LoadOpenAiSettings();
        saved.Validate();
        var tariff = OpenAiTariff.Known(PilotModel)!;
        tariff.Validate();
        var budget = saved.Budget with { PilotUsd = Math.Min(saved.Budget.PilotUsd, .25m), RequestUsd = Math.Min(saved.Budget.RequestUsd, .05m) };
        budget.Validate();
        var pairs = OpenAiCampaignPrompts.EditorialComparison(PilotModel).ToArray();
        V4Rules.Require(pairs.Length == 6 && pairs.Count(pair => pair.Lane.Holdout) >= 2, "OPENAI_VOICE_REFORM_SCENES");
        var schedule = new List<(OpenAiCampaignLane Lane, OpenAiPromptBatch Batch, string Version)>();
        foreach (var (pair, index) in pairs.Select((pair, index) => (pair, index)))
        {
            var baseline = (pair.Lane, pair.Baseline, OpenAiCampaignPrompts.LegacyVersionHash);
            var candidate = (pair.Lane, pair.Candidate, OpenAiCampaignPrompts.VersionHash);
            schedule.AddRange(index % 2 == 0 ? [baseline, candidate] : [candidate, baseline]);
        }
        var experiment = V4Rules.Hash(V4Rules.Utf8.GetBytes("PSS-OPENAI-VOICE-REFORM-20261120"))[..32];
        var blind = schedule.SelectMany((_, arm) => Enumerable.Range(1, 3).Select(row =>
            new { Arm = arm, Row = row, Code = Guid.NewGuid().ToString("N")[..10] })).ToArray();
        SaveEvidence(evidence, "preregistered.json", new
        {
            experiment, Model = PilotModel, Tariff = tariff, Budget = budget, MaxPosts = 12, OwnerGate = "REQUIRED",
            AtUtc = DateTimeOffset.UtcNow, BlindKey = blind,
            Order = schedule.Select(item => new { item.Lane, item.Version, item.Batch.SceneHash,
                item.Batch.Post.Hash, ReserveUsd = tariff.Reserve(item.Batch.Post.MaxInput, item.Batch.Post.MaxOutput),
                Payload = ParsePayload(item.Batch.Post) })
        });

        string key;
        try
        {
            var vault = new OpenAiKeyVault(workspace);
            if (!vault.Exists()) return PreflightFailure("VAULT_EMPTY");
            key = vault.Read();
        }
        catch (Exception error) when (error is IOException or InvalidDataException or UnauthorizedAccessException)
        { return PreflightFailure("VAULT_ACCESS_OR_DPAPI_FAILED"); }
        using var client = new OpenAiResponsesClient();
        using var timeout = new CancellationTokenSource(TimeSpan.FromMinutes(24));
        try
        {
            if (!(await client.ModelsAsync(key, timeout.Token)).Contains(PilotModel, StringComparer.Ordinal))
            {
                return PreflightFailure("MODEL_UNAVAILABLE");
            }
        }
        catch (Exception error) when (error is HttpRequestException or InvalidDataException or OperationCanceledException)
        {
            return PreflightFailure("MODEL_PREFLIGHT_FAILED");
        }
        var ledger = new OpenAiSpendLedger(workspace);
        using var worker = ledger.AcquireWorker();
        if (ledger.Spent(experiment, DateTimeOffset.UtcNow).CampaignReservations > 0)
            return PreflightFailure("EXPERIMENT_ALREADY_ISSUED");
        var posts = 0;
        var review = new List<(string Code, string Scene, string Facts, string Input, string Text)>();
        var refused = new HashSet<string>(StringComparer.Ordinal);
        var resolved = "";
        var stop = "COMPARISON_FINISHED";
        decimal settled = 0;
        for (var i = 0; i < schedule.Count; i++)
        {
            var (lane, batch, version) = schedule[i];
            if (refused.Contains(lane.Id)) continue;
            var held = OpenAiSpendLedger.Amount(tariff.Reserve(batch.Post.MaxInput, batch.Post.MaxOutput));
            var work = new OpenAiWork(Guid.NewGuid().ToString("N"), lane.Id, 1, 3, 3, batch.Post.Hash, batch.SceneHash,
                PilotModel, held, DateTimeOffset.UtcNow);
            var payload = ParsePayload(batch.Post);
            V4Rules.Require(!OpenAiResponsesClient.HasSecret(payload.GetRawText(), key), "OPENAI_OUTBOUND_SECRET");
            if (posts >= 12 || !ledger.Reserve(experiment, work, pilot: true, budget))
            {
                if (posts == 0) return PreflightFailure("BUDGET_BLOCKED");
                stop = "BUDGET_BLOCKED";
                break;
            }
            var prefix = (i + 1).ToString("D2", System.Globalization.CultureInfo.InvariantCulture);
            SaveEvidence(evidence, prefix + "-issued.json", new { experiment, Request = work, Version = version,
                IssueAtUtc = DateTimeOffset.UtcNow, ReserveUsd = OpenAiSpendLedger.Usd(held), Payload = payload });
            posts++;
            var reply = await client.GenerateAsync(batch.Post, key, timeout.Token);
            var cost = reply.Usage == null ? (long?)null : OpenAiSpendLedger.Amount(tariff.Cost(reply.Usage));
            var uncertain = reply.Status is "USAGE_DRIFT" or "MODEL_OR_TIER_DRIFT" or "INDETERMINATE"
                || !cost.HasValue || cost.Value > held || resolved.Length > 0 && reply.Model != resolved;
            if (resolved.Length == 0 && reply.Model.Length > 0) resolved = reply.Model;
            var lines = reply.Text.Length == 0 ? [] : reply.Text.ReplaceLineEndings("\n").TrimEnd('\n').Split('\n');
            string? canonical = null;
            string? validation = null;
            CorpusQualityIssue[] issues = [];
            if (reply.Status == "COMPLETE" && !uncertain)
            {
                try
                {
                    canonical = OpenAiCampaignPrompts.CanonicalJsonl(reply.Text, lane, batch.Scope);
                    var registry = V4Registry.Default;
                    var act = registry.Categories.Single(category => category.Key == lane.Category).Act;
                    var rows = canonical.Split('\n', StringSplitOptions.RemoveEmptyEntries).Skip(1).Select(line =>
                    {
                        using var row = JsonDocument.Parse(line);
                        return V4BatchReader.ReadRecord(row.RootElement, registry, lane.Category, act, lane.Mode);
                    }).ToArray();
                    issues = CorpusQuality.Check(rows, batch.Scope, []).Concat(OpenAiCampaignPrompts.SceneIssues(lane, batch.Scope, rows)).ToArray();
                }
                catch (Exception error) when (error is IOException or JsonException or InvalidDataException) { validation = error.Message; }
            }
            SaveEvidence(evidence, prefix + "-receipt.json", new
            {
                experiment, work.Id, Version = version, batch.Post.Hash, AtUtc = DateTimeOffset.UtcNow,
                reply, CostUsd = cost.HasValue ? OpenAiSpendLedger.Usd(cost.Value) : (decimal?)null, Uncertain = uncertain,
                CanonicalJsonl = canonical, Validation = validation, Issues = issues
            });
            if (!uncertain && cost.HasValue)
            {
                ledger.Settle(experiment, work.Id, cost.Value);
                settled += OpenAiSpendLedger.Usd(cost.Value);
            }
            if (reply.Status == "REFUSAL") refused.Add(lane.Id);
            foreach (var row in blind.Where(item => item.Arm == i))
            {
                var text = row.Row <= lines.Length ? lines[row.Row - 1] : "[" + reply.Status + " · строка отсутствует]";
                review.Add((row.Code, lane.Category, batch.Scope.Style.Situation, string.Join(" ", batch.Scope.Style.Dialogue), text));
            }
            if (uncertain || reply.Status == "SECURITY_REDACTED")
            {
                stop = reply.Status == "REFUSAL" ? "REFUSAL_COST_UNCONFIRMED"
                    : uncertain ? "UNKNOWN_ISSUED" : "SECURITY_REDACTED";
                break;
            }
        }
        SaveEvidence(evidence, "blind-review.json", review.OrderBy(row => row.Code, StringComparer.Ordinal)
            .Select(row => new { row.Code, row.Scene, row.Facts, row.Input, row.Text }));
        var debit = ledger.Spent(experiment, DateTimeOffset.UtcNow).Campaign;
        SaveEvidence(evidence, "live-summary.json", new { Status = stop, Posts = posts, CostUsd = settled, HeldUsd = debit - settled,
            ResolvedModel = resolved, Refused = refused.Count, ReviewRows = review.Count, OwnerGate = "REQUIRED", Bulk = "LOCKED" });
        Console.WriteLine($"{stop}: {posts} POST; {settled:F8} USD; held {debit - settled:F8} USD; review {review.Count} rows.");
        return stop == "COMPARISON_FINISHED" ? 0 : 3;

        int PreflightFailure(string reason)
        {
            SaveEvidence(evidence, "live-summary.json", new { Status = "LIVE_NOT_RUN", Reason = reason, Posts = 0, CostUsd = 0 });
            Console.WriteLine($"LIVE_NOT_RUN: {reason} (0 POST).");
            return 3;
        }
    }

    private static void WriteRouteCensus(string evidence)
    {
        var v2 = OpenAiCampaignPrompts.PlanLanes(100_000, mature: true);
        var v3 = OpenAiCampaignPrompts.EditorialLanes(100_000, null, true);
        var rows = v2.Select(lane => Census(lane, "v2")).Concat(v3.Select(lane => Census(lane, "v3"))).ToArray();
        SaveEvidence(evidence, "route-matrix-142.json", rows);
        var mix = v3.GroupBy(lane => lane.Mode == "TRIGGER" ? "TRIGGER" : lane.Register)
            .ToDictionary(group => group.Key!, group => group.Sum(lane => lane.Target));
        SaveEvidence(evidence, "offline-mix.json", new
        {
            V2Hash = OpenAiCampaignPrompts.LegacyVersionHash, V3Hash = OpenAiCampaignPrompts.VersionHash, Mix = mix,
            Mature = v3.Where(lane => lane.Register == "MATURE").Select(lane => new { lane.Category, lane.Target }),
            V2Mature = v2.Where(lane => lane.Id.EndsWith(".mature", StringComparison.Ordinal)).Sum(lane => lane.Target)
        });
        SaveEvidence(evidence, "comparison-payload-hashes.json", OpenAiCampaignPrompts.EditorialComparison(PilotModel)
            .Select(pair => new { pair.Lane.Id, Baseline = pair.Baseline.Post.Hash, Candidate = pair.Candidate.Post.Hash }));
        Console.WriteLine(JsonSerializer.Serialize(mix));

        static object Census(OpenAiCampaignLane lane, string version)
        {
            var eligible = Enumerable.Range(1, V4Registry.Default.Voices.Count)
                .Select(batch => OpenAiCampaignPrompts.Prepare(lane, 1, batch, PilotModel, mature: true).Scope)
                .GroupBy(scope => (scope.Style.StyleId, scope.Style.Relation, scope.Style.Channel, scope.ProfanityLimit, scope.Style.MatureRequested))
                .Select(group => new { Style = group.Key.StyleId, group.Key.Relation, group.Key.Channel, group.Key.ProfanityLimit,
                    group.Key.MatureRequested, Voices = group.Select(scope => scope.Style.Voice).Distinct().Order() }).ToArray();
            var batches = (lane.Target + OpenAiCampaignPlan.BatchSize - 1) / OpenAiCampaignPlan.BatchSize;
            var scheduled = Enumerable.Range(1, batches).Select(batch =>
            {
                var scope = OpenAiCampaignPrompts.Prepare(lane, 1, batch, PilotModel, mature: true).Scope;
                var quota = Math.Min(OpenAiCampaignPlan.BatchSize, lane.Target - (batch - 1) * OpenAiCampaignPlan.BatchSize);
                return new { Batch = batch, Target = quota, scope.Style.Voice, scope.Style.StyleId,
                    scope.Style.Channel, scope.ProfanityLimit, scope.Style.MatureRequested };
            }).ToArray();
            return new { Version = version, lane.Id, lane.Category, lane.Family, lane.Mode, lane.Event, lane.Target, Eligible = eligible, Scheduled = scheduled };
        }
    }

    private static void CheckHistoricalCopy(WorkspaceStore workspace, PromptLabStore store, string original, string evidence)
    {
        var relative = "labs/prompt-lab/campaigns/" + HistoricTerraPilot;
        var from = PathSafety.ResolveRelative(original, relative);
        if (!Directory.Exists(from))
        {
            SaveEvidence(evidence, "history-summary.json", new { Status = "ORIGINAL_UNAVAILABLE" });
            return;
        }
        var destination = PathSafety.ResolveRelative(workspace.Root, relative);
        Directory.CreateDirectory(Path.Combine(destination, "drafts"));
        var names = Directory.GetFiles(PathSafety.ResolveRelative(from, "drafts"), "*.jsonl")
            .Select(path => "drafts/" + Path.GetFileName(path)).Append("plan.json").Append("journal.sqlite").ToArray();
        var hashes = names.ToDictionary(name => name, name => V4Rules.Hash(File.ReadAllBytes(PathSafety.ResolveRelative(from, name))));
        foreach (var name in names) File.Copy(PathSafety.ResolveRelative(from, name), PathSafety.ResolveRelative(destination, name), false);
        var campaign = new CorpusCampaign(store);
        var plan = campaign.LoadOpenAi(HistoricTerraPilot);
        var status = campaign.OpenAiStatus(plan.Id);
        Equal("HISTORICAL", status.State);
        Equal(48, campaign.OpenAiSamples(plan.Id, reveal: true).Count);
        Throws(() => campaign.CheckOpenAi(plan.Id, campaign.OpenAiHash(plan.Id)));
        Throws(() => CorpusCampaignControl.Request(workspace.Root, source: workspace.ProtectedRoot, plan.Id, campaign.OpenAiHash(plan.Id), "PAUSE"));
        var export = campaign.ExportOpenAi(plan.Id, 0, confirmed: true);
        var reconstructed = campaign.OpenAiLog(plan.Id).Select(receipt =>
        {
            var lane = plan.Lanes.Single(lane => lane.Id == receipt.Lane);
            var batch = OpenAiCampaignPrompts.Prepare(lane, receipt.Requested, receipt.Batch, plan.Settings.Model, plan.Settings.MatureOptIn);
            Equal(receipt.PromptHash, batch.Post.Hash);
            Equal(receipt.SceneHash, batch.SceneHash);
            return new { Receipt = receipt, Payload = ParsePayload(batch.Post) };
        }).ToArray();
        foreach (var name in names)
        {
            Equal(hashes[name], V4Rules.Hash(File.ReadAllBytes(PathSafety.ResolveRelative(from, name))));
            Equal(hashes[name], V4Rules.Hash(File.ReadAllBytes(PathSafety.ResolveRelative(destination, name))));
        }
        SaveEvidence(evidence, "history-reconstructed-not-capture.json", reconstructed);
        SaveEvidence(evidence, "history-summary.json", new { status.State, status.Totals, HashesVerified = names.Length,
            ReconstructedExactHash = reconstructed.Length, OriginalPayloadCapture = false, ReviewExport = export });
        Console.WriteLine($"HISTORICAL: 48 rows, {names.Length} SHA-256 matches, {reconstructed.Length} reconstructed payload hashes.");
    }

    private static JsonElement ParsePayload(OpenAiPost post)
    {
        using var document = JsonDocument.Parse(post.Body);
        return document.RootElement.Clone();
    }

    private static void SaveEvidence<T>(string evidence, string name, T value)
    {
        var bytes = JsonSerializer.SerializeToUtf8Bytes(value, PromptProfiles.ConfigJson);
        using var file = new FileStream(PathSafety.ResolveRelative(evidence, name), FileMode.CreateNew, FileAccess.Write, FileShare.Read);
        file.Write(bytes);
        file.Flush(flushToDisk: true);
    }
}
