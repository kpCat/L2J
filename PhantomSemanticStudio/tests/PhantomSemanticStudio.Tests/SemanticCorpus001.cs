using System.Net;
using System.Text;
using System.Text.Json;
using PhantomSemanticStudio.Core;

internal static partial class Program
{
    private static async Task SemanticCorpus001()
    {
        Test("001 global index persists canonical duplicates and atomically rejects changed provenance on replay", () =>
        {
            using var f = new Fixture(); using var workspace = new WorkspaceStore(Path.Combine(f.Root, "index"), f.Module);
            var index = new CorpusCandidateIndex(workspace);
            var first = new CorpusIndexRecord(Guid.NewGuid().ToString("N"), new string('a', 64), 1, "social.friendship.support", "REPLY", "Пошли, разберёмся вместе.");
            var second = first with { SourceId = Guid.NewGuid().ToString("N"), Text = " ПОШЛИ,  разберёмся вместе. " };
            var added = index.Append([first, second]); Equal(1, added.NewTexts); Equal(2, added.NewOccurrences); Equal(1, added.ExactDuplicates);
            index = new CorpusCandidateIndex(workspace); Equal(1L, index.Stats().Texts); Equal(2L, index.Stats().Occurrences);
            Equal(2, index.Append([first, second]).Replayed); Equal(2L, index.Stats().Occurrences);
            var fresh = first with { SourceId = Guid.NewGuid().ToString("N"), Text = "Подожду тебя у входа." };
            Throws(() => index.Append([fresh, first with { Text = "Другой исходный текст." }]));
            Throws(() => index.Append([first with { Number = 2, SourceHash = new string('b', 64) }]));
            Equal(1L, index.Stats().Texts); Equal(2L, index.Stats().Occurrences);
            using (var db = CorpusStore.Connect(Path.Combine(workspace.Root, "labs/prompt-lab/corpus-index", V4Registry.Default.Hash + ".sqlite"), true))
            using (var revision = db.CreateCommand()) { revision.CommandText = "PRAGMA user_version=2"; revision.ExecuteNonQuery(); }
            Throws(() => new CorpusCandidateIndex(workspace));
        });
        Test("001 indexed shortlist finds permutations short exact text and repeated openings without semantic approval", () =>
        {
            using var f = new Fixture(); using var workspace = new WorkspaceStore(Path.Combine(f.Root, "index"), f.Module);
            var index = new CorpusCandidateIndex(workspace);
            CorpusIndexRecord Row(string text) => new(Guid.NewGuid().ToString("N"), new string('a', 64), 1, "social.friendship.support", "REPLY", text);
            index.Append([Row("Давай после рейда спокойно разберём этот косяк."), Row("Слышь, ты чё, опять налево свернул?"), Row("Ага.")]);
            True(index.Find("Давай спокойно разберём этот косяк после рейда.").Any(n => n.LexicalNearClone && !n.Exact));
            True(index.Find("Слышь, ты чё, этот лут совсем не берёшь?").Any(n => n.RepeatedOpening));
            True(index.Find("АГА.", 1).Single().Exact); True(index.Find("Ага.").All(n => n.Coverage == "COVERAGE_LIMITED_NOT_SEMANTIC_APPROVAL"));
            Throws(() => index.Find("Ага.", 65));
            using var cancelled = new CancellationTokenSource(); cancelled.Cancel();
            Throws(() => index.Append([Row("Ещё одна строка.")], cancelled.Token)); Equal(3L, index.Stats().Texts);
        });
        Test("001 campaign preparation freezes a 100K quota without issuing generation or approving records", () =>
        {
            using var f = new Fixture(); using var workspace = new WorkspaceStore(Path.Combine(f.Root, "campaign"), f.Module);
            var store = new PromptLabStore(workspace); var campaign = new CorpusCampaign(store);
            var missing = new CorpusCampaignSpec { ModelId = "instance-1", MaxRequests = 100000,
                Quotas = [new("support", Guid.NewGuid().ToString("N"), 0, 100000)] };
            Throws(() => campaign.Prepare(missing)); True(!Directory.Exists(Path.Combine(store.Root, "campaigns")));
            var fixture = CampaignProduction(store); var spec = missing with { Quotas = [new("support", fixture.Production.Id, 0, 100000)] };
            Throws(() => campaign.Prepare(spec with { MaxRequests = 99999 }));
            Throws(() => campaign.Prepare(spec with { ModelId = "unreviewed-model" }));
            Throws(() => campaign.Prepare(spec with { Quotas = [new("support", fixture.Production.Id, 1, 100000)] }));
            Throws(() => campaign.Prepare(spec with { Quotas = [new("support", fixture.Production.Id, 0, 50000), new("also-support", fixture.Production.Id, 0, 50000)] }));
            Throws(() => JsonSerializer.Deserialize<CorpusCampaignSpec>("{\"Schema\":\"pw-corpus-campaign-spec/1\",\"AutoApprove\":true}"));
            True(!Directory.Exists(Path.Combine(store.Root, "campaigns")));
            var plan = campaign.Prepare(spec); Equal("PREPARED_UNAPPROVED", plan.Status);
            Equal(100000, plan.AcceptedTarget); Equal(100000, plan.NominalMinimumRequests); Equal(65536, plan.ContextLimit);
            Equal(1, store.ListRuns().Count); Equal("social.friendship.support", plan.Coverage[0].Category);
            Equal("REPLY", plan.Coverage[0].Mode); Equal(100000, plan.Coverage[0].AcceptedTarget);
            var hash = campaign.PlanHash(plan.Id); var reopened = new CorpusCampaign(new PromptLabStore(workspace)).Check(plan.Id, hash);
            Equal(plan.Id, reopened.Id); Equal(hash, campaign.PlanHash(plan.Id));
            var changed = store.SaveVersion(store.GetVersion(fixture.Production.VersionId) with { Id = "", ParentId = fixture.Production.VersionId, System = "Другой текст." });
            store.AcceptVersion(changed.Id); Throws(() => campaign.Check(plan.Id, hash));
            Equal(1, store.ListRuns().Count); Equal(0, store.IsolatedV4().Stats().Total);
        });
        Test("001 campaign requires reviewed settings and scenario and detects feedback or plan drift", () =>
        {
            using var f = new Fixture(); using var workspace = new WorkspaceStore(Path.Combine(f.Root, "campaign"), f.Module);
            var store = new PromptLabStore(workspace); var campaign = new CorpusCampaign(store);
            var wrongSampler = CampaignProduction(store, differentSampler: true);
            var spec = new CorpusCampaignSpec { ModelId = "instance-1", MaxRequests = 100000,
                Quotas = [new("support", wrongSampler.Production.Id, 0, 100000)] };
            Throws(() => campaign.Prepare(spec)); True(!Directory.Exists(Path.Combine(store.Root, "campaigns")));
            var stalePrompt = CampaignProduction(store, differentPrompt: true);
            Throws(() => campaign.Prepare(spec with { Quotas = [new("stale-prompt", stalePrompt.Production.Id, 0, 100000)] }));
            var good = CampaignProduction(store, additionalScope: true);
            Throws(() => campaign.Prepare(spec with { Quotas = [new("unreviewed-scene", good.Production.Id, 1, 100000)] }));
            var plan = campaign.Prepare(spec with { Quotas = [new("support", good.Production.Id, 0, 100000)] });
            var hash = campaign.PlanHash(plan.Id); Throws(() => campaign.Check(plan.Id, new string('0', 64)));
            store.SaveFeedback(good.Run.Id, [new(1, good.Run.Rows[0].TextHash, "DISLIKE", "Пересмотр синтетической оценки.")], "Этот пример больше не подходит.");
            Throws(() => campaign.Check(plan.Id, hash));
            Equal(3, store.ListRuns().Count); Equal(0, store.IsolatedV4().Stats().Total);
        });
        Test("001 mixed script damage is editorially flagged while normal L2 tokens stay permitted", () =>
        {
            var request = new V4GenerationRequest { Category = "life.health.fatigue", Style = new() { Category = "life.health.fatigue" } };
            var entry = new V4Entry("fixture", "REPLY", request.Category, request.Category + ".reply", "Не боишься проспать весьs", "", "", "SUPPORTIVE", "CALM", ["UNKNOWN"], ["ANY"], new(), "NONE", "GENERAL", [], 1, "");
            True(CorpusQuality.Check([entry], request, []).Any(i => i.Code == "MIXED_SCRIPT_TOKEN_REVIEW"));
            True(!CorpusQuality.Check([entry with { Text = "Соберём PvP-пати после RB." }], request, []).Any(i => i.Code == "MIXED_SCRIPT_TOKEN_REVIEW"));
        });
        Test("001 comparative plans preserve selected endpoint and freeze batch budgets without HTTP", () =>
        {
            using var f = new Fixture(); var registry = Path.Combine(f.Module, "docs", "phantoms", "semantic", "v4"); Directory.CreateDirectory(registry);
            File.WriteAllText(Path.Combine(registry, "registry.tsv"), V4Registry.Default.CategoryTsv(), new UTF8Encoding(false));
            File.WriteAllText(Path.Combine(registry, "voices.tsv"), V4Registry.Default.VoiceTsv(), new UTF8Encoding(false));
            File.WriteAllText(Path.Combine(registry, "events.tsv"), V4Registry.Default.EventTsv(), new UTF8Encoding(false));
            var lab = new CorpusLab(Path.Combine(f.Root, "native-quality"), f.Module, PromptProfiles.Default);
            var plan = lab.CreatePlan(transport: "LM_NATIVE", ownerMaturePilotConsent: true, thinkingQuality: true, thinkingBudget: 4096);
            True(plan.Jobs.All(j => j.Request.Transport == "LM_NATIVE" && j.Request.CompatibleThinkingControl == "none"));
            True(plan.Jobs.All(j => j.Request.Count == 1 && j.Request.MaxOutputTokens == 4096));
            Equal(2, plan.Jobs.Count(j => j.Request.Reasoning == "off")); Equal(2, plan.Jobs.Count(j => j.Request.Reasoning == "on"));
            var batch = new CorpusLab(Path.Combine(f.Root, "batch-size"), f.Module, PromptProfiles.Default).CreatePlan(reasoning: "off", compatibleThinkingControl: "reasoning_effort", batchSizeProbe: true);
            Equal("BATCH_SIZE", batch.Kind); Equal(6, batch.Jobs.Length); Equal(42, batch.Jobs.Sum(j => j.Request.Count));
            True(batch.Jobs.All(j => !j.Request.Style.MatureRequested && j.Request.MaxOutputTokens == 3072));
            foreach (var group in batch.Jobs.GroupBy(j => j.Request.Category))
                True(group.Select(j => j.Request.Count).Order().SequenceEqual(new[] { 3, 6, 12 }));
            Throws(() => new CorpusLab(Path.Combine(f.Root, "bad-combination"), f.Module, PromptProfiles.Default).CreatePlan(thinking: true, batchSizeProbe: true));
            var matched = new CorpusLab(Path.Combine(f.Root, "matched-profiles"), f.Module, PromptProfiles.Default)
                .CreatePlan(transport: "OPENAI_COMPAT", reasoning: "off", compatibleThinkingControl: "reasoning_effort", ownerMaturePilotConsent: true, matchedProfileProbe: true);
            Equal("MATCHED_PROFILE_PROBE", matched.Kind); Equal(32, matched.Jobs.Length); Equal(128, matched.Jobs.Sum(j => j.Request.Count));
            foreach (var group in matched.Jobs.GroupBy(j => j.CaseId))
            { Equal(8, group.Count()); Equal(4, group.Select(j => j.Request.Strategy).Distinct().Count()); Equal(2, group.Select(j => j.Request.Sampler.Id).Distinct().Count()); }
            True(matched.Jobs.Any(j => j.Request.Style.StyleId == "roleplay_conflict"));
            True(matched.Jobs.Any(j => j.Request.Style.StyleId == "ordinary" && !j.Request.Style.StrongProfanityRequested));
        });
        Test("001 dialogue request names next phantom speaker and generic adult triggers cannot masquerade as coverage", () =>
        {
            var cases = V4CorpusGeneration.LoadPilotCases(Path.Combine(AppContext.BaseDirectory, "prompts", "corpus001"), PromptProfiles.Default, true, true);
            using var user = JsonDocument.Parse(V4CorpusGeneration.UserData(cases[0].Request));
            Equal("PHANTOM", user.RootElement.GetProperty("NextSpeaker").GetString());
            True(user.RootElement.GetProperty("QaOnly").GetBoolean());
            var dialogue = user.RootElement.GetProperty("Dialogue");
            Equal("user", dialogue[dialogue.GetArrayLength() - 1].GetProperty("role").GetString());
            var request = cases.First(c => c.Request.Mode == "TRIGGER").Request;
            var generic = new V4Entry("", "TRIGGER", request.Category, request.Category + ".reply", "О чём именно?", request.Category + ".observe", "NONE", "", "", [], [], new(), "NONE", "GENERAL", [], 1, "");
            True(CorpusQuality.Check([generic], request, []).Any(i => i.Code == "TRIGGER_TOO_GENERIC"));
        });
        Test("001 intimate vocabulary remains mature and sex-bound anatomy never enters universal V4", () =>
        {
            var request = new V4GenerationRequest { Category = "social.flirt.bold", Style = new() { Category = "social.flirt.bold" } };
            V4Entry Entry(string text) => new("", "REPLY", "social.flirt.bold", "social.flirt.bold.reply", text, "", "", "AMUSED", "CALM", ["UNKNOWN"], ["ANY"], new(), "NONE", "GENERAL", [], 1, "");
            True(CorpusQuality.Check([Entry("За девственность скидку не дают.")], request, []).Any(i => i.Code == "MATURE_MARKING_REVIEW"));
            True(CorpusQuality.Check([Entry("Мой член сегодня обсуждают всем кланом.")], request, []).Any(i => i.Code == "ANATOMY_RUNTIME_UNFILTERED"));
            True(!CorpusQuality.Check([Entry("Он член нашего клана.")], request, []).Any(i => i.Code.StartsWith("ANATOMY", StringComparison.Ordinal)));
            var gendered = request with { Style = request.Style with { SpeakerSex = "FEMALE", SpeakerVirginity = "VIRGIN" } };
            Throws(() => gendered.Validate(PromptProfiles.Default));
            var qa = gendered with { QaOnly = true };
            qa.Validate(PromptProfiles.Default);
            True(CorpusQuality.Check([Entry("Мой член сегодня обсуждают всем кланом.")], qa, []).Any(i => i.Code == "ANATOMY_CONTRADICTION"));
            True(CorpusQuality.Check([Entry("Я давно не девственница.")], qa, []).Any(i => i.Code == "PERSONAL_FACT_CONTRADICTION"));
            var intimate = V4CorpusGeneration.LoadPilotCases(Path.Combine(AppContext.BaseDirectory, "prompts", "corpus001"), PromptProfiles.Default, true, true);
            Equal(18, intimate.Count); True(intimate.All(c => c.Request.QaOnly)); True(intimate.Any(c => c.Request.Style.SpeakerVirginity == "VIRGIN"));
        });
        Test("001 shipped prompt strategies fit the bounded input budget including explicit record schema", () =>
        {
            var directory = Path.Combine(AppContext.BaseDirectory, "prompts", "corpus001"); var profiles = PromptProfiles.Default;
            var cases = V4CorpusGeneration.LoadPilotCases(directory, profiles, true).Concat(V4CorpusGeneration.LoadPilotCases(directory, profiles, true, true));
            var largest = 0;
            foreach (var item in cases)
                foreach (var strategy in PromptProfiles.Strategies)
                {
                    var request = item.Request with { Strategy = strategy };
                    var bytes = Encoding.UTF8.GetByteCount(profiles.StrategyText(strategy) + "\n" + profiles.LegacyRules(request.Style)) + Encoding.UTF8.GetByteCount(V4CorpusGeneration.UserData(request));
                    largest = Math.Max(largest, bytes); if (bytes > 16384) throw new AssertionFailure($"PROMPT_BUDGET {item.Id}/{strategy}: {bytes} bytes > 16384");
                }
            Console.WriteLine("PROMPT_INPUT_UPPER_BOUND_BYTES=" + largest);
        });
        await TestAsync("001 current LM Studio empty tool_calls is not a tool invocation; nonempty and malformed calls still reject", async () =>
        {
            using var f = new Fixture(); var pack = new PackReader().Load(f.Module);
            var request = new GenerationRequest("greeting", "greet.reply", "UNKNOWN", "NEUTRAL", "ANY", "Короткая реплика", "", 1, GenerationMode.TEMPLATE);
            foreach (var calls in new[] { "[]", "null" })
            {
                var node = System.Text.Json.Nodes.JsonNode.Parse(Envelope("TEMPLATE"))!;
                node["choices"]![0]!["message"]!["tool_calls"] = System.Text.Json.Nodes.JsonNode.Parse(calls);
                using var http = new HttpClient(new RecordingHandler(node.ToJsonString()));
                Equal(1, (await new LmStudioClient(http).GenerateAsync(new() { HighFiveRoot = f.Module }, "", pack, request, CancellationToken.None)).Count);
            }
            foreach (var calls in new[] { "[{}]", "{}", "\"invalid\"" })
            {
                var node = System.Text.Json.Nodes.JsonNode.Parse(Envelope("TEMPLATE"))!;
                node["choices"]![0]!["message"]!["tool_calls"] = System.Text.Json.Nodes.JsonNode.Parse(calls);
                using var http = new HttpClient(new RecordingHandler(node.ToJsonString()));
                await ThrowsAsync(() => new LmStudioClient(http).GenerateAsync(new() { HighFiveRoot = f.Module }, "", pack, request, CancellationToken.None));
            }
        });
        await TestAsync("001 legacy request uses contextual editorial rules rather than a blanket profanity/adult veto", async () =>
        {
            using var f = new Fixture(); var pack = new PackReader().Load(f.Module);
            var handler = new RecordingHandler(Envelope("TEMPLATE")); using var http = new HttpClient(handler);
            var settings = new StudioSettings { HighFiveRoot = f.Module };
            var result = await new LmStudioClient(http).GenerateAsync(settings, "", pack,
                new("greeting", "greet.reply", "UNKNOWN", "NEUTRAL", "ANY", "Короткое приветствие", "", 1, GenerationMode.TEMPLATE), CancellationToken.None);
            Equal(1, result.Count);
            using var payload = JsonDocument.Parse(handler.Payload!);
            var system = payload.RootElement.GetProperty("messages")[0].GetProperty("content").GetString()!;
            True(!system.Contains("В этой стартовой партии не создавай мат", StringComparison.Ordinal));
            True(system.Contains("GENERAL", StringComparison.Ordinal) && system.Contains("NONE", StringComparison.Ordinal));
        });
        await TestAsync("001 pilot restart never repeats committed requests and detects source drift without touching protected files", async () =>
        {
            using var f = new Fixture(); var registry = Path.Combine(f.Module, "docs", "phantoms", "semantic", "v4"); Directory.CreateDirectory(registry);
            File.WriteAllText(Path.Combine(registry, "registry.tsv"), V4Registry.Default.CategoryTsv(), new UTF8Encoding(false));
            File.WriteAllText(Path.Combine(registry, "voices.tsv"), V4Registry.Default.VoiceTsv(), new UTF8Encoding(false));
            File.WriteAllText(Path.Combine(registry, "events.tsv"), V4Registry.Default.EventTsv(), new UTF8Encoding(false));
            var labPath = Path.Combine(f.Root, "lab"); var lab = new CorpusLab(labPath, f.Module, PromptProfiles.Default);
            var plan = lab.CreatePlan(thinking: true); Equal(14, plan.Jobs.Length);
            var handler = new Corpus001Handler(); using var http = new HttpClient(handler);
            var settings = new StudioSettings { HighFiveRoot = f.Module, ModelId = "instance-1" };
            var one = await lab.RunAsync(new LmStudioClient(http), settings, "memory-only-key", "instance-1", 1, CancellationToken.None);
            Equal(1, one.CompletedRequests); Equal(1, handler.Posts);
            var reopened = new CorpusLab(labPath, f.Module, PromptProfiles.Default);
            var two = await reopened.RunAsync(new LmStudioClient(http), settings, "memory-only-key", "instance-1", 1, CancellationToken.None);
            Equal(2, two.CompletedRequests); Equal(2, handler.Posts); Equal(0, two.ApprovedRecords);
            var savedResult = Directory.GetFiles(Path.Combine(labPath, "results"), "*.json")[0]; var originalBytes = File.ReadAllBytes(savedResult);
            try { File.AppendAllText(savedResult, "\n"); Throws(() => reopened.GetStatus()); }
            finally { File.WriteAllBytes(savedResult, originalBytes); }
            True(!string.Join("", Directory.GetFiles(labPath, "*.json", SearchOption.AllDirectories).Select(File.ReadAllText)).Contains("memory-only-key", StringComparison.Ordinal));
            File.AppendAllText(Path.Combine(registry, "registry.tsv"), "drift\n");
            await ThrowsAsync(() => reopened.RunAsync(new LmStudioClient(http), settings, "", "instance-1", 1, CancellationToken.None)); Equal(2, handler.Posts);
        });
        Test("001 frozen pilot covers twelve leaves all voices and real events; strict wire rejects metadata scope escape", () =>
        {
            var directory = Path.Combine(AppContext.BaseDirectory, "prompts", "corpus001");
            var cases = V4CorpusGeneration.LoadPilotCases(directory, PromptProfiles.Default);
            Equal(40, cases.Count); Equal(12, cases.Select(c => c.Request.Category).Distinct().Count());
            Equal(24, cases.Where(c => c.Request.Mode == "REPLY").Select(c => c.Request.Style.Voice).Distinct().Count());
            Equal(6, cases.Select(c => c.Request.Style.Channel).Distinct().Count());
            Equal(4, cases.Count(c => c.Request.Mode == "INITIATIVE"));
            Equal(2, V4CorpusGeneration.LoadSamplers(directory).Count);
            True(cases.All(c => !c.Request.Style.MatureRequested && !c.Request.Style.OwnerMatureOptIn));
            var request = cases.First(c => c.Request.Mode == "REPLY").Request;
            var entry = new V4Entry("", "REPLY", request.Category, request.Category + ".reply", "Бля, ну и дела.", "", "", "AMUSED", "CALM", ["HOSTILE"], ["ANY"], new(), "NONE", "GENERAL", [], 1, "");
            var issues = CorpusQuality.Check([entry], request, []);
            True(issues.Any(i => i.Code == "RELATION_SCOPE")); True(issues.Any(i => i.Code == "PROFANITY_MARKING"));
            var repeated = entry with { Text = "Ну всё, точка победила." };
            True(CorpusQuality.Check([repeated], request, [repeated]).Any(i => i.Code == "EXACT_DUPLICATE"));
        });
        await TestAsync("001 malformed batches retain raw evidence, model substitution stops and frozen plan edits block before HTTP", async () =>
        {
            using var f = new Fixture(); var registry = Path.Combine(f.Module, "docs", "phantoms", "semantic", "v4"); Directory.CreateDirectory(registry);
            File.WriteAllText(Path.Combine(registry, "registry.tsv"), V4Registry.Default.CategoryTsv(), new UTF8Encoding(false));
            File.WriteAllText(Path.Combine(registry, "voices.tsv"), V4Registry.Default.VoiceTsv(), new UTF8Encoding(false));
            File.WriteAllText(Path.Combine(registry, "events.tsv"), V4Registry.Default.EventTsv(), new UTF8Encoding(false));
            var settings = new StudioSettings { HighFiveRoot = f.Module, ModelId = "instance-1" };
            var malformedBody = JsonSerializer.Serialize(new { model_instance_id = "instance-1", output = new[] { new { type = "message", content = "{\"header\":{},\"records\":[]}" } }, stats = new { input_tokens = 100, total_output_tokens = 20, reasoning_output_tokens = 0 } });
            var badPath = Path.Combine(f.Root, "malformed-lab"); var malformedLab = new CorpusLab(badPath, f.Module, PromptProfiles.Default); malformedLab.CreatePlan(thinking: true);
            using (var http = new HttpClient(new Corpus001Handler(malformedBody)))
            {
                var result = await malformedLab.RunAsync(new(http), settings, "", "instance-1", 1, CancellationToken.None);
                Equal(1, result.CompletedRequests); Equal(0, result.SchemaValidRequests); Equal(0, result.ApprovedRecords);
                var evidence = JsonSerializer.Deserialize<CorpusPilotOutcome>(File.ReadAllText(Directory.GetFiles(Path.Combine(badPath, "results"), "*.json")[0]))!;
                Equal(malformedBody, evidence.Response!.RawEnvelope); True(evidence.FailureCode.Length > 0);
            }
            var lostPath = Path.Combine(f.Root, "lost-lab"); var lostLab = new CorpusLab(lostPath, f.Module, PromptProfiles.Default); lostLab.CreatePlan(thinking: true);
            var lostHandler = new Corpus001Handler(malformedBody.Replace("instance-1", "different-instance")); using (var http = new HttpClient(lostHandler))
            {
                var result = await lostLab.RunAsync(new(http), settings, "", "instance-1", 2, CancellationToken.None);
                Equal("LOST_MODEL", result.Phase); Equal(1, lostHandler.Posts);
            }
            var driftPath = Path.Combine(f.Root, "plan-drift-lab"); var driftLab = new CorpusLab(driftPath, f.Module, PromptProfiles.Default); driftLab.CreatePlan(thinking: true);
            var planFile = Path.Combine(driftPath, "plan.json"); var node = System.Text.Json.Nodes.JsonNode.Parse(File.ReadAllText(planFile))!; node["Jobs"]!.AsArray().RemoveAt(0); File.WriteAllText(planFile, node.ToJsonString());
            var driftHandler = new Corpus001Handler(); using (var http = new HttpClient(driftHandler))
                await ThrowsAsync(() => driftLab.RunAsync(new(http), settings, "", "instance-1", 1, CancellationToken.None));
            Equal(0, driftHandler.Gets); Equal(0, driftHandler.Posts);
        });
        Test("001 inventory distinguishes downloaded keys from loaded instances and missing thinking telemetry stays unknown", () =>
        {
            const string compat = "{\"data\":[{\"id\":\"gemma-local\"},{\"id\":\"download-only\"}]}";
            const string native = "{\"models\":[{\"key\":\"gemma-local\",\"loaded_instances\":[{\"id\":\"instance-1\",\"config\":{\"context_length\":65536}}],\"capabilities\":{\"reasoning\":{\"allowed_options\":[\"off\",\"on\"]}}},{\"key\":\"download-only\",\"loaded_instances\":[]}]}";
            var inventory = LmStudioClient.ParseCorpusModels(compat, native);
            Equal(1, inventory.Loaded.Count); Equal("instance-1", inventory.Loaded[0].InstanceId);
            Equal(65536, inventory.Loaded[0].ContextLength);
            Equal("UNKNOWN", LmStudioClient.ReadCorpusTelemetry("{\"choices\":[]}", false, 1).ThinkingEvidence);
            Equal("ZERO_REPORTED_NOT_VERIFIED", LmStudioClient.ReadCorpusTelemetry("{\"stats\":{\"reasoning_output_tokens\":0}}", true, 1).ThinkingEvidence);
            Equal("REASONING_OBSERVED", LmStudioClient.ReadCorpusTelemetry("{\"stats\":{\"reasoning_output_tokens\":42}}", true, 1).ThinkingEvidence);
            Throws(() => LmStudioClient.ParseCorpusModels(compat, "{\"models\":[{\"key\":\"gemma-local\",\"loaded_instances\":[{\"id\":\"x\",\"config\":{\"context_length\":-1}}]}]}"));
        });
        await TestAsync("001 V4 generation uses loaded id, retains usage, rejects unsafe request before POST and refuses truncation", async () =>
        {
            var content = JsonSerializer.Serialize(new { header = new { schema = "pw-semantic-batch/1", category = "lineage.enchant.fail", mode = "REPLY", act = "lineage.enchant.fail.reply", language = "ru", count = 1 },
                records = new[] { new { text = "Ну всё, точка победила.", stance = "AMUSED" } } });
            var response = JsonSerializer.Serialize(new { model = "instance-1", choices = new[] { new { finish_reason = "stop", message = new { content } } }, usage = new { prompt_tokens = 30, completion_tokens = 60, completion_tokens_details = new { reasoning_tokens = 0 } } });
            var handler = new RecordingHandler(response); using var http = new HttpClient(handler);
            var request = new V4GenerationRequest { Category = "lineage.enchant.fail", Count = 1, Style = new() { Category = "lineage.enchant.fail", Situation = "Игрок сообщил о сломанной вещи" } };
            var loaded = new CorpusLoadedModel("gemma-local", "instance-1", 65536, ["off", "on"]);
            var generated = await new LmStudioClient(http).GenerateCorpusAsync(new(), "", loaded, request, PromptProfiles.Default, CancellationToken.None);
            Equal(0L, generated.Telemetry.ReasoningTokens); Equal("ZERO_REPORTED_NOT_VERIFIED", generated.Telemetry.ThinkingEvidence);
            Equal<long?>(null, generated.EffectiveSeed);
            using var payload = JsonDocument.Parse(handler.Payload!);
            Equal("instance-1", payload.RootElement.GetProperty("model").GetString());
            True(payload.RootElement.TryGetProperty("response_format", out _)); True(!payload.RootElement.TryGetProperty("context_length", out _));
            Throws(() => V4CorpusGeneration.ToJsonl("{\"header\":{},\"records\":[]}", request));
            var wire = V4CorpusGeneration.ToJsonl(generated.Content, request);
            True(wire.Contains("Ну всё, точка победила.", StringComparison.Ordinal));
            var root = Path.Combine(Path.GetTempPath(), "001-wire-" + Guid.NewGuid().ToString("N")); Directory.CreateDirectory(root);
            try { var file = Path.Combine(root, "batch.jsonl"); File.WriteAllText(file, wire, new UTF8Encoding(false)); True(new V4Workspace(Path.Combine(root, "workspace"), Path.Combine(root, "source")).Validate(file).Success); }
            finally { Directory.Delete(root, true); }
            await ThrowsAsync(() => new LmStudioClient(http).GenerateCorpusAsync(new(), "", loaded with { ContextLength = 2048 }, request, PromptProfiles.Default, CancellationToken.None));
            Equal(1, handler.Calls);
            using var truncated = new HttpClient(new RecordingHandler(response.Replace("\"stop\"", "\"length\"")));
            Equal("FINISH_REASON", (await new LmStudioClient(truncated).GenerateCorpusAsync(new(), "", loaded, request, PromptProfiles.Default, CancellationToken.None)).Rejection);
            var nativeResponse = JsonSerializer.Serialize(new { model_instance_id = "instance-1", output = new[] { new { type = "message", content } },
                stats = new { input_tokens = 30, total_output_tokens = 60, reasoning_output_tokens = 0 } });
            var nativeHandler = new RecordingHandler(nativeResponse); using var nativeHttp = new HttpClient(nativeHandler);
            var minP = new PromptLabApiOptions { Transport = "LM_NATIVE", MinP = .05 }.Apply(request);
            Equal("", (await new LmStudioClient(nativeHttp).GenerateCorpusAsync(new(), "", loaded, minP, PromptProfiles.Default, CancellationToken.None)).Rejection);
            using var nativePayload = JsonDocument.Parse(nativeHandler.Payload!);
            Equal(.05, nativePayload.RootElement.GetProperty("min_p").GetDouble());
            True(!nativePayload.RootElement.TryGetProperty("context_length", out _));
            await ThrowsAsync(() => new LmStudioClient(nativeHttp).GenerateCorpusAsync(new(), "", loaded, minP with { Transport = "OPENAI_COMPAT" }, PromptProfiles.Default, CancellationToken.None));
            Equal(1, nativeHandler.Calls);
        });
        await TestAsync("001 mature context requires explicit owner consent before any HTTP", async () =>
        {
            using var f = new Fixture(); var pack = new PackReader().Load(f.Module);
            var handler = new RecordingHandler(Envelope("TEMPLATE")); using var http = new HttpClient(handler);
            var requestJson = JsonSerializer.Serialize(new GenerationRequest("greeting", "greet.reply", "TRUSTED", "CASUAL", "ANY", "Взрослая двусмысленность", "", 1, GenerationMode.TEMPLATE));
            var style = "\"Style\":{\"StyleId\":\"adult_flirt\",\"Category\":\"social.flirt.bold\",\"Voice\":\"playful_tease\",\"Relation\":\"TRUSTED\",\"Channel\":\"TELL\",\"MatureRequested\":true,\"OwnerMatureOptIn\":false,\"AdultsConsenting\":true}";
            var denied = JsonSerializer.Deserialize<GenerationRequest>(requestJson[..^1] + "," + style + "}")!;
            await ThrowsAsync(() => new LmStudioClient(http).GenerateAsync(new() { HighFiveRoot = f.Module }, "", pack, denied, CancellationToken.None));
            Equal(0, handler.Calls);
            var allowed = JsonSerializer.Deserialize<GenerationRequest>((requestJson[..^1] + "," + style + "}").Replace("\"OwnerMatureOptIn\":false", "\"OwnerMatureOptIn\":true"))!;
            await ThrowsAsync(() => new LmStudioClient(http).GenerateAsync(new() { HighFiveRoot = f.Module }, "", pack, allowed, CancellationToken.None));
            Equal(0, handler.Calls);
            pack.Topics.Add("social.flirt.bold"); pack.Acts.Add("social.flirt.bold.reply");
            pack.Entries.Add(new PackEntry { Kind = "PATTERN", Topic = "social.flirt.bold", Act = "social.flirt.bold.reply", Text = "Синтетический проверенный взрослый scope" });
            allowed = allowed with { Topic = "social.flirt.bold", Act = "social.flirt.bold.reply" };
            Equal(1, (await new LmStudioClient(http).GenerateAsync(new() { HighFiveRoot = f.Module }, "", pack, allowed, CancellationToken.None)).Count);
            using var payload = JsonDocument.Parse(handler.Payload!);
            var system = payload.RootElement.GetProperty("messages")[0].GetProperty("content").GetString()!;
            True(system.Contains("MATURE", StringComparison.Ordinal) && system.Contains("playful_tease", StringComparison.Ordinal));
        });
    }

    private static int CorpusIndexCensus(string[] args)
    {
        V4Rules.Require(args.Length == 5, "INDEX_CENSUS_ARGS: census.json workRoot new-index-workspace source");
        var input = PathSafety.Canonical(args[1]); var work = PathSafety.Canonical(args[2]); var root = PathSafety.Canonical(args[3]);
        V4Rules.Require(PathSafety.IsWithin(input, work), "INDEX_CENSUS_INPUT_SCOPE");
        var marker = PathSafety.ResolveRelative(root, "index-census-input.sha256");
        V4Rules.Require(!Directory.Exists(root) || File.Exists(marker), "INDEX_CENSUS_NEW_WORKSPACE_REQUIRED");
        var bytes = V4Rules.ReadBytes(input, 16 * 1024 * 1024); var censusHash = V4Rules.Hash(bytes);
        if (File.Exists(marker)) V4Rules.Require(File.ReadAllText(marker) == censusHash, "INDEX_CENSUS_REVISION_DRIFT");
        using var document = JsonDocument.Parse(bytes); var census = document.RootElement;
        V4Rules.Require(V4Rules.String(census, "Schema") == "pw-cross-trial-census/1", "INDEX_CENSUS_SCHEMA");
        var sources = census.GetProperty("Sources").EnumerateArray().ToDictionary(s => V4Rules.String(s, "Path"), s => V4Rules.String(s, "Sha256"), StringComparer.Ordinal);
        foreach (var source in sources) V4Rules.Require(FileSha(PathSafety.ResolveRelative(work, source.Key)) == source.Value, "INDEX_CENSUS_SOURCE_DRIFT");
        var categories = V4Registry.Default.Categories.Select(c => c.Key).ToHashSet(StringComparer.Ordinal);
        var records = new List<CorpusIndexRecord>(); var excluded = new Dictionary<string, int>(); var sourceIds = new Dictionary<string, string>();
        foreach (var row in census.GetProperty("Records").EnumerateArray())
        {
            var source = V4Rules.String(row, "Source"); var hash = V4Rules.String(row, "SourceSha256");
            V4Rules.Require(sources.TryGetValue(source, out var verified) && verified == hash, "INDEX_CENSUS_UNVERIFIED_ROW");
            var category = V4Rules.String(row, "Category");
            if (!categories.Contains(category)) { excluded[category] = excluded.GetValueOrDefault(category) + 1; continue; }
            var id = V4Rules.Hash(V4Rules.Utf8.GetBytes(source + "\n" + hash)); sourceIds[id] = source;
            records.Add(new(id, hash, row.GetProperty("Number").GetInt32(), category, V4Rules.String(row, "Mode"), V4Rules.String(row, "Text"), row.GetProperty("QaOnly").GetBoolean()));
        }
        using var workspace = new WorkspaceStore(root, PathSafety.Canonical(args[4]));
        if (!File.Exists(marker)) { using var file = new FileStream(marker, FileMode.CreateNew, FileAccess.Write, FileShare.None); file.Write(Encoding.ASCII.GetBytes(censusHash)); file.Flush(true); }
        var index = new CorpusCandidateIndex(workspace); var timer = System.Diagnostics.Stopwatch.StartNew(); var added = 0; var replayed = 0;
        foreach (var batch in records.Chunk(300)) { var result = index.Append(batch); added += result.NewOccurrences; replayed += result.Replayed; }
        timer.Stop(); var stats = index.Stats(); var expectedUnique = records.Select(r => V4Rules.Normalize(r.Text)).Distinct(StringComparer.Ordinal).Count();
        V4Rules.Require(stats.Texts == expectedUnique && stats.Occurrences == records.Count, "INDEX_CENSUS_COUNTS_MISMATCH");
        const string query = "Слышь, ты чё, совсем ебанулся? Лево и право — это для тебя слишком сложно, блядь?";
        var matches = index.Find(query).Select(n => new { Match = n, SourceFile = sourceIds[n.Source.SourceId] }).ToArray();
        var report = new { Schema = "pw-corpus-index-census/1", Status = "RESEARCH_INDEX_ONLY", CensusHash = censusHash, VerifiedSourceFiles = sources.Count,
            InputRows = census.GetProperty("Records").GetArrayLength(), IndexedRows = records.Count, ExpectedUnique = expectedUnique,
            ExactExtraOccurrences = records.Count - expectedUnique, ExcludedNonCanonical = excluded, stats, AddedOccurrences = added, Replayed = replayed,
            AppendWallSeconds = timer.Elapsed.TotalSeconds, Query = query, Matches = matches,
            DatabaseBytes = new FileInfo(Path.Combine(root, "labs/prompt-lab/corpus-index", stats.RegistryHash + ".sqlite")).Length,
            Approved = 0, Imported = 0, SemanticRecall = "NOT_PROVEN; bounded lexical retrieval only" };
        var output = PathSafety.ResolveRelative(root, "index-census-" + Guid.NewGuid().ToString("N") + ".json");
        workspace.Check(); File.WriteAllText(output, JsonSerializer.Serialize(report, WorkspaceStore.JsonOptions), new UTF8Encoding(false));
        Console.WriteLine($"INDEX_CENSUS_PASS rows={records.Count}; unique={stats.Texts}; exactExtra={records.Count - expectedUnique}; added={added}; replayed={replayed}; report={output}; approved=0"); return 0;
    }

    private static (PromptLabProduction Production, PromptLabRun Run) CampaignProduction(PromptLabStore store, bool differentSampler = false, bool additionalScope = false, bool differentPrompt = false)
    {
        var scope = new V4GenerationRequest { Category = "social.friendship.support", Count = 1, Style = new() { Category = "social.friendship.support" } };
        var version = store.SaveVersion(new() { Name = "Синтетический contract fixture, не owner approval", Scenario = scope,
            AdditionalScenarios = additionalScope ? [scope with { Style = scope.Style with { Situation = "Другая сцена, её ещё не оценивали." } }] : [] });
        var options = differentSampler ? version.Generation with { Temperature = .25 } : version.Generation;
        var prompt = LmStudioClient.ComposePromptLabGeneration(version with { Generation = options }, store.EffectiveRegistry());
        var run = store.BeginRun(version.Id, apiOptions: options); store.MarkIssued(run.Id);
        const string text = "Пошли, разберёмся вместе.";
        var content = JsonSerializer.Serialize(new { header = new { schema = "pw-semantic-batch/1", category = "social.friendship.support",
            mode = "REPLY", act = "social.friendship.support.reply", language = "ru", count = 1 }, records = new[] { new { text, stance = "SUPPORTIVE" } } });
        _ = V4CorpusGeneration.ToJsonl(content, version.Scenario, store.EffectiveRegistry());
        store.CompleteRun(run with { State = "COMPLETE", CompletedAtUtc = DateTimeOffset.UtcNow, ModelId = "instance-1", ModelContext = 65536,
            EffectiveSystem = differentPrompt ? "Старый фактически оценённый SYSTEM." : prompt.System, EffectiveUser = prompt.User,
            Endpoint = "http://127.0.0.1:1234/v1", Response = new("{}", "{}", content, new(100, 20, 0, null, null, 1), "", 200, null, null),
            Rows = [new(1, text, TextRules.Hash(text), true, [])] });
        run = store.GetRun(run.Id); store.AcceptVersion(version.Id);
        store.SaveFeedback(run.Id, [new(1, run.Rows[0].TextHash, "LIKE", "Синтетическая проверка контракта.")], "Не оценка владельца и не реальный модельный опыт.");
        return (new PromptLabRouting(store).Promote(version.Id, [run.Id], true), run);
    }

    private sealed class Corpus001Handler(string? postResponse = null) : HttpMessageHandler
    {
        public int Posts { get; private set; }
        public int Gets { get; private set; }
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken token)
        {
            string body;
            if (request.Method == HttpMethod.Get)
            { Gets++; body = request.RequestUri!.AbsolutePath == "/v1/models" ? "{\"data\":[{\"id\":\"instance-1\"}]}"
                    : "{\"models\":[{\"key\":\"gemma-local\",\"type\":\"llm\",\"loaded_instances\":[{\"id\":\"instance-1\",\"config\":{\"context_length\":65536}}]}]}";
            }
            else { Posts++; body = postResponse ?? "{\"error\":{\"message\":\"synthetic unsupported reasoning\"}}"; }
            return Task.FromResult(new HttpResponseMessage(request.Method == HttpMethod.Post && postResponse == null ? HttpStatusCode.BadRequest : HttpStatusCode.OK) { Content = new StringContent(body, Encoding.UTF8, "application/json") });
        }
    }
}
