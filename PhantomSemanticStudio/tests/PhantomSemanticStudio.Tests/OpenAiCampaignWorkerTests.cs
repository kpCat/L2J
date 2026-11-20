using System.Text.Json;
using PhantomSemanticStudio.Core;

internal static partial class Program
{
    private static async Task OpenAiCampaignWorkerTests()
    {
        await TestAsync("OpenAI dry plan covers all categories and voices and pins matched pilot conditions", () =>
        {
            using var fixture = new Fixture();
            using var workspace = new WorkspaceStore(Path.Combine(fixture.Root, "dry"), fixture.Module);
            var campaign = new CorpusCampaign(new PromptLabStore(workspace));
            var settings = WorkerOpenAiSettings();
            var plan = campaign.PrepareOpenAi(settings, 100_000);
            var status = campaign.OpenAiStatus(plan.Id);
            Equal(100_000, plan.Target);
            Equal(142, status.Quotas.Count);
            Equal(0L, status.Totals.Requests);
            True(!campaign.IsOpenAiQualityAccepted(plan.Id));
            Assert(V4Registry.Default.Categories.Select(category => category.Key).SequenceEqual(status.Quotas.Select(quota => quota.Category)),
                "Dry-plan counts must use the canonical category keys and order");
            Assert(V4Registry.Modes.All(mode => plan.Lanes.Any(lane => lane.Mode == mode)), "A canonical role is missing");
            Assert(V4Registry.Default.Events.All(item => plan.Lanes.Any(lane => lane.Mode == "INITIATIVE" && lane.Event == item.Code)),
                "A canonical event is missing");

            var requested = status.Quotas.ToDictionary(quota => quota.Category, quota => quota.Target, StringComparer.Ordinal);
            var first = status.Quotas[0].Category;
            var second = status.Quotas[1].Category;
            requested[first] += 20;
            requested[second] -= 20;
            var custom = campaign.PrepareOpenAi(settings, 100_000, requested);
            Equal(requested[first], custom.Lanes.Where(lane => lane.Category == first).Sum(lane => lane.Target));
            requested[second]--;
            Throws(() => campaign.PrepareOpenAi(settings, 100_000, requested));

            var firstReply = plan.Lanes.First(lane => lane.Mode == "REPLY");
            var rotated = Enumerable.Range(0, 24).Select(batch =>
                OpenAiCampaignPrompts.Prepare(firstReply, 20, batch, TestModel).Scope.Style.Voice).ToHashSet(StringComparer.Ordinal);
            Assert(rotated.SetEquals(V4Registry.Default.Voices.Select(voice => voice.Key)), "Voice rotation must cover all 24 voices");

            var pilot = campaign.PrepareOpenAi(settings, 0, pilot: true);
            Equal(48, pilot.Target);
            Equal(16, pilot.MinimumPosts);
            var pairs = pilot.Lanes.GroupBy(lane => lane.Scene).ToArray();
            Equal(8, pairs.Length);
            Assert(pairs.Count(pair => pair.All(lane => lane.Holdout)) >= 2, "Holdout scenes were omitted");
            Assert(pilot.Lanes.Select(lane => lane.Family).Distinct().Count() >= 6, "Pilot family coverage is too narrow");
            foreach (var pair in pairs)
            {
                var jsonLane = pair.Single(lane => lane.Format == "JSON");
                var textLane = pair.Single(lane => lane.Format == "TEXT");
                var structured = OpenAiCampaignPrompts.Prepare(jsonLane, 3, 1, TestModel);
                var plain = OpenAiCampaignPrompts.Prepare(textLane, 3, 1, TestModel);
                Equal(structured.SceneHash, plain.SceneHash);
                Equal(structured.Post.MaxOutput, plain.Post.MaxOutput);
                Assert(structured.Post.Hash != plain.Post.Hash, "A and B must send distinct output instructions");
                using var wireA = JsonDocument.Parse(structured.Post.Body);
                using var wireB = JsonDocument.Parse(plain.Post.Body);
                var format = wireA.RootElement.GetProperty("text").GetProperty("format");
                Assert(format.GetProperty("strict").GetBoolean() && !wireB.RootElement.TryGetProperty("text", out _),
                    "Only A should carry a strict response schema");
                CheckRequiredOpenAiFields(format.GetProperty("schema"));
            }

            var adult = OpenAiCampaignPrompts.PilotLanes(true)
                .Select(lane => OpenAiCampaignPrompts.Prepare(lane, 3, 1, TestModel, mature: true).Scope.Style)
                .Where(style => style.MatureRequested).ToArray();
            Equal(2, adult.Length);
            Assert(adult.All(style => style.AdultsConsenting && style.OwnerMatureOptIn && style.Relation == "TRUSTED"
                && style.Channel is "BOT_PRIVATE" or "TELL"), "Adult opt-in must remain private and scoped to a single scene pair");
            var optedIn = campaign.PrepareOpenAi(settings with { MatureOptIn = true }, 100_000);
            Equal(plan.Target, optedIn.Target);
            var matureLane = optedIn.Lanes.First(lane => lane.Id.EndsWith(".mature", StringComparison.Ordinal));
            var matureScope = OpenAiCampaignPrompts.Prepare(matureLane, 1, 1, TestModel, mature: true).Scope;
            True(matureScope.Style.MatureRequested && matureScope.Style.AdultsConsenting && matureScope.Style.Channel == "BOT_PRIVATE");
            return Task.CompletedTask;
        });

        await TestAsync("OpenAI routes strong nonsexual speech and scales private mature quotas", () =>
        {
            using var fixture = new Fixture();
            using var workspace = new WorkspaceStore(Path.Combine(fixture.Root, "editorial"), fixture.Module);
            var campaign = new CorpusCampaign(new PromptLabStore(workspace));
            var settings = WorkerOpenAiSettings() with { MatureOptIn = true };
            var plan = campaign.PrepareOpenAi(settings, 100_000);
            var adult = plan.Lanes.Where(lane => lane.Id.EndsWith(".mature", StringComparison.Ordinal)).ToArray();
            var tradeStrong = plan.Lanes.Where(lane => lane.Category == "lineage.commerce.bargaining" && lane.Mode == "REPLY")
                .Any(lane => OpenAiCampaignPrompts.Prepare(lane, 1, 1, TestModel, true).Scope.ProfanityLimit == "STRONG");
            Assert(adult.Length >= 3 && adult.Sum(lane => lane.Target) > 20 && tradeStrong,
                $"Expected multiple private adult categories and independent strong trade. Adult rows={adult.Sum(lane => lane.Target)}, trade STRONG={tradeStrong}.");
            True(adult.Select(lane => lane.Category).Distinct().Count() >= 3);
            True(adult.Select(lane => lane.AdultStyle).ToHashSet().IsSupersetOf(["vulgar_humor", "double_meanings", "adult_flirt", "intimate_conversation"]));
            var tradeAdult = adult.Where(lane => lane.Category == "lineage.commerce.bargaining").ToArray();
            Assert(tradeAdult.Length == 2, "Consenting fictional adults need distinct private trade innuendo and vulgar-humor lanes.");
            foreach (var lane in tradeAdult)
            {
                var privateTrade = OpenAiCampaignPrompts.Prepare(lane, 1, 1, TestModel, true).Scope.Style;
                True(privateTrade.Channel == "BOT_PRIVATE" && privateTrade.Relation == "TRUSTED" && privateTrade.AdultsConsenting
                    && privateTrade.Situation.Contains("продавец") && privateTrade.Situation.Contains("покупатель"));
                Throws(() => OpenAiCampaignPrompts.Prepare(lane, 1, 1, TestModel));
            }
            var publicTrade = plan.Lanes.Single(lane => lane.Category == "lineage.commerce.bargaining"
                && lane.Mode == "REPLY" && lane.Register == "GENERAL");
            var publicTradeScope = OpenAiCampaignPrompts.Prepare(publicTrade, 1, 1, TestModel, true).Scope.Style;
            True(publicTradeScope.Channel == "TRADE" && !publicTradeScope.MatureRequested && !publicTradeScope.StrongProfanityRequested);
            var mix = plan.EditorialQuota;
            Equal(plan.Target, mix.General + mix.Strong + mix.Mature + mix.Triggers);
            True(mix.Mature < plan.Target / 50);
            Equal(0, campaign.PrepareOpenAi(settings with { MatureOptIn = false }, 100_000).EditorialQuota.Mature);
            True(plan.Lanes.SequenceEqual(campaign.LoadOpenAi(plan.Id).Lanes));
            Equal(3, plan.PromptStyleVersion);
            Equal("89f2a28eca7c4beefec6f708a541bbda80244ca7ca521ce1a1ffb5865bdf93e0", OpenAiCampaignPrompts.LegacyVersionHash);
            Equal("a93611b3498529bf7afa79f6d44b2486c8a4c34e95e2a164bf92fe7faf6140a0", plan.PromptHash);
            True(plan.PromptHash != OpenAiCampaignPrompts.LegacyVersionHash);
            var v2 = plan with { Id = Guid.NewGuid().ToString("N"), PromptStyleVersion = 2, PromptHash = OpenAiCampaignPrompts.LegacyVersionHash,
                Lanes = OpenAiCampaignPrompts.PilotLanes(true), Pilot = true };
            new PromptLabStore(workspace).SaveOpenAiPlan(v2);
            True(campaign.LoadOpenAi(v2.Id).Historical);
            Equal("HISTORICAL", campaign.OpenAiStatus(v2.Id).State);
            Throws(() => campaign.CheckOpenAi(v2.Id, campaign.OpenAiHash(v2.Id)));
            Throws(() => campaign.RequestOpenAiControl(v2.Id, "RUN"));

            var pairs = OpenAiCampaignPrompts.EditorialComparison(TestModel).ToArray();
            Equal(6, pairs.Length);
            foreach (var (lane, baseline, candidate) in pairs)
            {
                Equal(baseline.Post.MaxOutput, candidate.Post.MaxOutput);
                Equal(baseline.Scope.Style.Situation, candidate.Scope.Style.Situation);
                True(baseline.Scope.Style.Dialogue.SequenceEqual(candidate.Scope.Style.Dialogue));
                Equal(baseline.Scope.Style.Channel, candidate.Scope.Style.Channel);
                Equal(baseline.Scope.Style.Voice, candidate.Scope.Style.Voice);
                using var body = JsonDocument.Parse(candidate.Post.Body);
                using var input = JsonDocument.Parse(body.RootElement.GetProperty("input").GetString()!);
                Equal("PHANTOM", input.RootElement.GetProperty("NextSpeaker").GetString());
                var system = body.RootElement.GetProperty("instructions").GetString()!;
                True(system.Contains("кто владеет вещью") && system.Contains("кто дал команду") && system.Contains("кто упал"));
                True(system.Contains(candidate.Scope.Style.StyleId) && system.Contains(lane.Voice) && !system.Contains("только JSON"));
                if (lane.Register == "STRONG") True(!system.Contains("Мат: NONE") && system.Contains("Крепкий мат доступен"));
                if (lane.Register == "MATURE")
                {
                    Equal("intimate_conversation", candidate.Scope.Style.StyleId);
                    True(system.Contains("UNKNOWN/ANY") && !system.Contains("maturity=GENERAL"));
                    Throws(() => OpenAiCampaignPrompts.Prepare(lane, 3, 1, TestModel));
                    foreach (var bad in new[] { candidate.Scope.Style with { Channel = "ALL" },
                        candidate.Scope.Style with { AdultsConsenting = false }, candidate.Scope.Style with { StyleId = "fabricated" } })
                        Throws(() => PromptProfiles.Default.Validate(bad));
                }
            }
            var ordinary = pairs.Single(pair => pair.Lane.Id.StartsWith("pilot-ordinary"));
            Throws(() => OpenAiCampaignPrompts.CanonicalJsonl("NextSpeaker — игрок.\nТри слова.\nВот ещё слова.", ordinary.Lane, ordinary.Candidate.Scope));
            Throws(() => OpenAiCampaignPrompts.Prepare(ordinary.Lane with { Voice = "fake" }, 3, 1, TestModel, true));
            Throws(() => OpenAiCampaignPrompts.Prepare(ordinary.Lane with { Voice = "shy_observer" }, 3, 1, TestModel, true));
            var trigger = plan.Lanes.First(lane => lane.Mode == "TRIGGER");
            var preparedTrigger = OpenAiCampaignPrompts.Prepare(trigger, 1, 1, TestModel);
            True(preparedTrigger.Scope.Style.Voice == "ANY" && !preparedTrigger.Scope.Style.MatureRequested
                && !preparedTrigger.Scope.Style.StrongProfanityRequested);
            Throws(() => OpenAiCampaignPrompts.Prepare(trigger with { Register = "MATURE" }, 1, 1, TestModel, true));
            return Task.CompletedTask;
        });

        await TestAsync("OpenAI pauses at a durable 60 drafts and cold-resumes to one 200 draft batch in ten POSTs", async () =>
        {
            using var fixture = new Fixture();
            var root = Path.Combine(fixture.Root, "workspace");
            var ledger = Path.Combine(fixture.Root, "ledger");
            var settings = WorkerOpenAiSettings();
            var counts = new List<int>();
            Action? pause = null;
            using var wire = new HttpClient(new CampaignHttp(async (request, token) =>
            {
                var count = await ReadOpenAiTextCount(request, token);
                var text = WorkerOpenAiRows(counts.Count, count);
                counts.Add(count);
                if (counts.Count == 3) pause!();
                return CampaignResponse(text, 100, 0, 50);
            }));
            using var client = new OpenAiResponsesClient(wire);
            string id, hash;
            using (var first = new WorkspaceStore(root, fixture.Module))
            {
                var campaign = new CorpusCampaign(new PromptLabStore(first));
                id = campaign.PrepareOpenAi(settings, 100_000).Id;
                hash = campaign.OpenAiHash(id);
                var planId = id;
                pause = () => campaign.RequestOpenAiControl(planId, "PAUSE");
                using var worker = new CorpusCampaignWorker(first);
                await ThrowsAsync(() => worker.RunOpenAiAsync(id, hash, client, FakeCredential, settings.Budget, true, false, default));
                Equal(0, counts.Count);
                var stopped = await worker.RunOpenAiCore(id, hash, client, FakeCredential, settings.Budget,
                    true, false, default, postLimit: 10, testLedger: ledger);
                Equal("PAUSED", stopped.State);
                Equal(3, counts.Count);
                Equal(60L, stopped.Totals.Unique);
                Equal(60, stopped.InBatch);
                Equal(0L, stopped.Totals.CompletedBatches);
            }

            using var recovered = new WorkspaceStore(root, fixture.Module);
            using var resumedWorker = new CorpusCampaignWorker(recovered);
            var result = await resumedWorker.RunOpenAiCore(id, hash, client, FakeCredential, settings.Budget,
                true, true, default, postLimit: 7, testLedger: ledger);
            Equal(10, counts.Count);
            Assert(counts.All(count => count == 20), "A logical batch must use ten 20-row requests");
            Equal(200L, result.Totals.Generated);
            Equal(200L, result.Totals.Valid);
            Equal(200L, result.Totals.Unique);
            Equal(1L, result.Totals.CompletedBatches);
            Equal(0L, result.Totals.Reviewed);
            Equal(0L, result.Totals.Approved);
            var saved = new CorpusCampaign(new PromptLabStore(recovered));
            var logs = saved.OpenAiLog(id);
            Equal(10, logs.Select(entry => entry.Id).Distinct().Count());
            Assert(logs.All(entry => entry.Batch == 1 && entry.Requested == 20 && entry.State == "DRAFT_SAVED"),
                "Durable receipts must bind every request to the same completed logical batch");
            var rows = saved.OpenAiSamples(id, reveal: true).Concat(saved.OpenAiSamples(id, page: 1, reveal: true)).ToArray();
            Equal(200, rows.Select(row => row.Text).Distinct().Count());
            Assert(rows.All(row => row.State == "PENDING" && row.Grade == null), "Generated rows must await human review");
            Equal(200L, new CorpusCandidateIndex(recovered).Stats().Occurrences);
        });

        await TestAsync("OpenAI RECEIVED recovery preserves one charged POST and shares budget and duplicate checks", async () =>
        {
            using var fixture = new Fixture();
            var root = Path.Combine(fixture.Root, "first");
            var ledger = Path.Combine(fixture.Root, "ledger");
            var settings = WorkerOpenAiSettings();
            var expectedCost = settings.Price!.Cost(new(100, 0, 50));
            var posts = 0;
            using var wire = new HttpClient(new CampaignHttp(async (request, token) =>
            {
                var count = await ReadOpenAiTextCount(request, token);
                posts++;
                return CampaignResponse(WorkerOpenAiRows(0, count), 100, 0, 50);
            }));
            using var client = new OpenAiResponsesClient(wire);
            string id, hash;
            OpenAiBudget tight;
            using (var first = new WorkspaceStore(root, fixture.Module))
            {
                var campaign = new CorpusCampaign(new PromptLabStore(first));
                var plan = campaign.PrepareOpenAi(settings, 100_000);
                id = plan.Id;
                hash = campaign.OpenAiHash(id);
                var budgetPost = OpenAiCampaignPrompts.Prepare(plan.Lanes[0] with { Format = "TEXT" }, 20, 1, TestModel).Post;
                tight = settings.Budget with { DailyUsd = settings.Price.Reserve(budgetPost.MaxInput, budgetPost.MaxOutput) + expectedCost / 2m };

                using var reserved = new CorpusCampaignWorker(first, null, point =>
                {
                    if (point == "OPENAI_RESERVED") throw new IOException("Synthetic interruption before dispatch.");
                });
                var beforeDispatch = await reserved.RunOpenAiCore(id, hash, client, FakeCredential, tight,
                    true, false, default, testLedger: ledger);
                Equal("PAUSED_RESOURCE", beforeDispatch.State);
                Equal("PREPARED", campaign.OpenAiLog(id).Single().State);
                Equal(0, posts);

                using var interrupted = new CorpusCampaignWorker(first, null, point =>
                {
                    if (point == "OPENAI_RECEIVED") throw new IOException("Synthetic interruption after durable receipt.");
                });
                var paused = await interrupted.RunOpenAiCore(id, hash, client, FakeCredential, tight,
                    true, true, default, testLedger: ledger);
                Equal("PAUSED_RESOURCE", paused.State);
                Equal(1, campaign.OpenAiLog(id).Count(row => row.State == "RECEIVED"));
                Equal(1, campaign.OpenAiLog(id).Count(row => row.State == "NOT_DISPATCHED" && row.CostUsd == 0m));
                Equal(1, posts);
            }

            using (var other = new WorkspaceStore(Path.Combine(fixture.Root, "second"), fixture.Module))
            {
                var otherCampaign = new CorpusCampaign(new PromptLabStore(other));
                var plan = otherCampaign.PrepareOpenAi(settings, 100_000);
                using var worker = new CorpusCampaignWorker(other);
                var blocked = await worker.RunOpenAiCore(plan.Id, otherCampaign.OpenAiHash(plan.Id), client, FakeCredential, tight,
                    true, false, default, testLedger: ledger);
                Equal("PAUSED_BUDGET", blocked.State);
                Equal(0L, blocked.Totals.Requests);
                Equal(1, posts);
            }

            using var recovered = new WorkspaceStore(root, fixture.Module);
            var saved = new CorpusCampaign(new PromptLabStore(recovered));
            using var resumedWorker = new CorpusCampaignWorker(recovered);
            var resumed = await resumedWorker.RunOpenAiCore(id, hash, client, FakeCredential, tight,
                true, true, default, testLedger: ledger);
            Equal("PAUSED_BUDGET", resumed.State);
            Equal(20L, resumed.Totals.Unique);
            Equal(expectedCost, resumed.Totals.CostUsd);
            Equal(0m, resumed.Totals.HeldUsd);
            Equal(1, posts);
            Equal(1, saved.OpenAiLog(id).Count(row => row.State == "DRAFT_SAVED"));

            var duplicate = saved.PrepareOpenAi(settings, 100_000);
            var replay = await resumedWorker.RunOpenAiCore(duplicate.Id, saved.OpenAiHash(duplicate.Id), client, FakeCredential, settings.Budget,
                true, false, default, postLimit: 1, testLedger: ledger);
            Equal(2, posts);
            Equal(20L, replay.Totals.Valid);
            Equal(0L, replay.Totals.Unique);
            var rejected = saved.OpenAiSamples(duplicate.Id, reveal: true);
            Equal(20, rejected.Count);
            Assert(rejected.All(row => row.Diagnostics.Contains("EXACT_DUPLICATE", StringComparison.Ordinal)),
                "The second campaign must detect the existing workspace corpus");
        });

        await TestAsync("OpenAI matched pilot stays blind and requires manual versioned evidence before bulk", async () =>
        {
            using var fixture = new Fixture();
            using var workspace = new WorkspaceStore(Path.Combine(fixture.Root, "calibration"), fixture.Module);
            var ledger = Path.Combine(fixture.Root, "pilot-ledger");
            var campaign = new CorpusCampaign(new PromptLabStore(workspace));
            var settings = WorkerOpenAiSettings();
            var pilot = campaign.PrepareOpenAi(settings, 0, pilot: true);
            var bulk = campaign.PrepareOpenAi(settings, 100_000);
            True(!campaign.IsOpenAiQualityAccepted(bulk.Id));
            var posts = 0;
            using var wire = new HttpClient(new CampaignHttp(async (request, cancellation) =>
            {
                var scene = pilot.Lanes[posts];
                var prepared = OpenAiCampaignPrompts.Prepare(scene, 3, 1, TestModel);
                True((await request.Content!.ReadAsByteArrayAsync(cancellation)).SequenceEqual(prepared.Post.Body));
                // Identical texts in the two arms of one scene must not penalize whichever arm ran second.
                var plain = WorkerOpenAiRows(posts++ / 2, 3);
                var response = plain;
                if (scene.Format == "JSON")
                {
                    var lines = OpenAiCampaignPrompts.CanonicalJsonl(plain, scene with { Format = "TEXT" }, prepared.Scope)
                        .Split('\n', StringSplitOptions.RemoveEmptyEntries);
                    response = "{\"header\":" + lines[0] + ",\"records\":[" + string.Join(',', lines.Skip(1)) + "]}";
                }
                return CampaignResponse(response, 100, 0, 45);
            }));
            using var client = new OpenAiResponsesClient(wire);
            using var worker = new CorpusCampaignWorker(workspace);
            var view = await worker.RunOpenAiCore(pilot.Id, campaign.OpenAiHash(pilot.Id), client, FakeCredential, settings.Budget,
                paidConfirmed: true, resume: false, default, testLedger: ledger);
            Equal("CALIBRATION_COMPLETE", view.State);
            Equal(16, posts);
            Equal(48L, view.Totals.Unique);
            var blinded = campaign.OpenAiSamples(pilot.Id);
            Equal(48, blinded.Count);
            True(blinded.All(row => row.Format == "" && row.Model == "" && row.Grade == null));
            Throws(() => campaign.ApproveOpenAiQuality(pilot.Id, "TEXT", settings.QualityPercent, true));

            var grade = new OpenAiGrade(3, true, true, true, true, true, true, true, "Синтетическая оценка.");
            foreach (var row in blinded)
                campaign.GradeOpenAi(pilot.Id, row.Id, row.Number, grade);
            var reportPath = campaign.SaveOpenAiCalibrationReport(pilot.Id, confirmed: true);
            True(PathSafety.IsWithin(reportPath, workspace.Root));
            var approval = campaign.ApproveOpenAiQuality(pilot.Id, "TEXT", settings.QualityPercent, confirmed: true);
            Equal(TestModel, approval.ResolvedModel);
            Equal(48, approval.Reviewed);
            Equal(24, approval.Accepted);
            True(campaign.IsOpenAiQualityAccepted(bulk.Id));
            var revised = blinded[0];
            campaign.GradeOpenAi(pilot.Id, revised.Id, revised.Number, grade with { Note = "Изменённая оценка." });
            True(!campaign.IsOpenAiQualityAccepted(bulk.Id));
        });
    }

    private static OpenAiCampaignSettings WorkerOpenAiSettings() => new()
    {
        Model = TestModel, Price = OpenAiTariff.Known(TestModel), TariffConfirmed = true,
        Budget = new(16m, 1m, 2m, 10m, 4m, .1m), Format = "TEXT"
    };

    private static async Task<int> ReadOpenAiTextCount(HttpRequestMessage request, CancellationToken token)
    {
        Equal(HttpMethod.Post, request.Method);
        using var body = JsonDocument.Parse(await request.Content!.ReadAsStringAsync(token));
        Assert(!body.RootElement.TryGetProperty("text", out _), "Text-first requests must omit the structured schema");
        using var input = JsonDocument.Parse(body.RootElement.GetProperty("input").GetString()!);
        return input.RootElement.GetProperty("Count").GetInt32();
    }

    private static string WorkerOpenAiRows(int batch, int count)
    {
        string[] offers = ["топор", "веник", "стул", "рюкзак", "коврик", "чайник", "фонарь", "арбуз", "компас", "поднос",
            "крендель", "зонтик", "сапог", "котелок", "портфель", "гамак", "браслет", "фартук", "колокольчик", "шарф"];
        string[] wanted = ["табурет", "гитару", "баян", "карандаш", "валик", "чемодан", "подушку", "сковородку", "кактус", "зеркало"];
        return string.Join('\n', offers.Take(count).Select(offer => $"За {offer} {wanted[batch]}?"));
    }

    private static void CheckRequiredOpenAiFields(JsonElement schema)
    {
        if (schema.TryGetProperty("properties", out var properties))
        {
            var required = schema.GetProperty("required").EnumerateArray().Select(item => item.GetString()!).ToHashSet(StringComparer.Ordinal);
            Assert(!schema.GetProperty("additionalProperties").GetBoolean() && required.SetEquals(properties.EnumerateObject().Select(item => item.Name)),
                "Strict Structured Outputs requires every declared property");
            foreach (var property in properties.EnumerateObject()) CheckRequiredOpenAiFields(property.Value);
        }
        if (schema.TryGetProperty("items", out var itemSchema)) CheckRequiredOpenAiFields(itemSchema);
    }
}
