using System.Text.Json;
using PhantomSemanticStudio.Core;

internal static partial class Program
{
    private static async Task<int> PromptLabQualityOperation(string[] args)
    {
        using var workspace = new WorkspaceStore(args[1], args[2]); var store = new PromptLabStore(workspace);
        using var lab = new PromptLabSession(store); var settings = new StudioSettings { HighFiveRoot = args[2], ModelId = args[3] };
        var key = Environment.GetEnvironmentVariable("LM_STUDIO_API_KEY") ?? "";
        async Task<PromptLabRun> Execute(string id)
        {
            var result = await lab.ResumeAsync(id, settings, key, args[3], CancellationToken.None, new PromptLabConsoleProgress(), requireExactInstance: true);
            Console.WriteLine($"QUALITY {result.Purpose} {result.Id} {result.State} {result.Failure}");
            foreach (var row in result.Rows) Console.WriteLine($"{row.Number}: {row.Text}");
            if (result.ProposedVersionId.Length > 0)
                Console.WriteLine(JsonSerializer.Serialize(store.GetVersion(result.ProposedVersionId), WorkspaceStore.JsonOptions));
            return result;
        }
        if (args[4] == "repair")
        {
            var queued = lab.QueueDescriptionRepair(args[5]); return (await Execute(queued.Id)).State == "COMPLETE" ? 0 : 1;
        }
        if (args[4] == "review-plan")
        {
            // Explicit agent technical review only; never owner acceptance, vote, import or production promotion.
            var planPath = Path.Combine(workspace.Root, "quality-reviewed-plan.json"); string[] ids;
            if (!File.Exists(planPath))
            {
                var group = lab.SplitFamilyPlan(args[5]);
                ids = group.VersionIds.Select(id => store.BeginRun(id).Id).ToArray();
                File.WriteAllText(planPath, JsonSerializer.Serialize(new { Source = args[5], Review = "AGENT_TECHNICAL_NOT_OWNER_REVIEW", Runs = ids }, WorkspaceStore.JsonOptions));
            }
            else
            {
                using var plan = JsonDocument.Parse(File.ReadAllText(planPath));
                V4Rules.Require(plan.RootElement.GetProperty("Source").GetString() == args[5], "QUALITY_REVIEW_SOURCE_DRIFT");
                ids = plan.RootElement.GetProperty("Runs").EnumerateArray().Select(v => v.GetString()!).ToArray();
            }
            foreach (var id in ids) if ((await Execute(id)).State != "COMPLETE") return 1;
            return 0;
        }
        if (args[4] == "verify")
        {
            var runs = store.ListRuns(); var versions = store.ListProfiles().SelectMany(p => store.ListVersions(p.Id)).ToArray();
            foreach (var run in runs) { _ = store.GetRun(run.Id); _ = store.GetTransport(run.Id); _ = store.ListFeedback(run.Id); }
            V4Rules.Require(store.ListProfiles().All(p => p.AcceptedVersionId.Length == 0), "QUALITY_OWNER_ACCEPTANCE_MUST_REMAIN_EMPTY");
            Console.WriteLine($"REOPEN_OK profiles={store.ListProfiles().Count}; versions={versions.Length}; runs={runs.Count}; complete={runs.Count(r => r.State == "COMPLETE")}; rejected={runs.Count(r => r.State == "REJECTED")}; ownerAcceptance=0; HTTP=0");
            return 0;
        }
        throw new InvalidDataException("Операция: repair, review-plan или verify.");
    }

    private static async Task PromptLabQualityRecoveryTests(string fixturePath)
    {
        Test("recovery exact saved Gemma proposal rejects invented category with field, value and expected domain", () =>
        {
            var body = File.ReadAllText(fixturePath);
            try { PromptLabAuthoring.ParseProposal(body, V4Registry.Default); throw new Exception("Expected rejection"); }
            catch (InvalidDataException error)
            {
                Console.WriteLine("SAVED_RESPONSE_DIAGNOSTIC " + error.Message);
                True(error.Message.Contains("batches[0].category", StringComparison.Ordinal));
                True(error.Message.Contains("lineage.craft.enchant.fail", StringComparison.Ordinal));
                True(error.Message.Contains("lineage.enchant.fail", StringComparison.Ordinal));
            }
        });
        Test("recovery canonical category preserves exact intent and rejects ambiguous or malformed fields", () =>
        {
            var body = File.ReadAllText(fixturePath).Replace("lineage.craft.enchant.fail", "lineage.enchant.fail", StringComparison.Ordinal);
            var proposal = PromptLabAuthoring.ParseProposal(body, V4Registry.Default);
            Equal("lineage.enchant.fail", proposal.Batches.Single().ToRequest().Category);
            Equal(4, proposal.Batches.Single().Count);
            foreach (var (field, value) in new (string, object?)[] { ("category", "сломанная вещь"), ("mode", "reply"), ("maturity", "adult"),
                ("voice", "гном"), ("relation", "FRIEND"), ("profanity", "YES"), ("count", 0), ("count", "4"), ("dialogue", null), ("requiredEvent", "pvp.kill.caused") })
            {
                var node = System.Text.Json.Nodes.JsonNode.Parse(body)!;
                node["batches"]![0]![field] = JsonSerializer.SerializeToNode(value);
                try { PromptLabAuthoring.ParseProposal(node.ToJsonString(PromptProfiles.CompactJson), V4Registry.Default); throw new Exception("Expected rejection: " + field); }
                catch (InvalidDataException error) { True(error.Message.Contains("batches[0]." + field, StringComparison.Ordinal)); }
            }
            var missing = System.Text.Json.Nodes.JsonNode.Parse(body)!; missing["batches"]![0]!.AsObject().Remove("category");
            var extra = System.Text.Json.Nodes.JsonNode.Parse(body)!; extra["batches"]![0]!["extra"] = "неизвестное поле";
            var duplicate = body.Replace("\"category\":", "\"category\":\"lineage.enchant.fail\",\"category\":", StringComparison.Ordinal);
            foreach (var malformed in new[] { missing.ToJsonString(PromptProfiles.CompactJson), extra.ToJsonString(PromptProfiles.CompactJson), duplicate })
            {
                var diagnostic = PromptLabAuthoring.ProposalDiagnostic(malformed, V4Registry.Default);
                True(diagnostic.Contains("batches[0].", StringComparison.Ordinal)); True(diagnostic.Contains("ожидается", StringComparison.Ordinal));
            }
            foreach (var (field, value) in new[] { ("system", "Русская инструкция достаточной длины: " + new string('я', 6200)), ("system", proposal.System + (char)0), ("user", "") })
            {
                var node = System.Text.Json.Nodes.JsonNode.Parse(body)!; node[field] = value;
                var diagnostic = PromptLabAuthoring.ProposalDiagnostic(node.ToJsonString(PromptProfiles.CompactJson), V4Registry.Default);
                if (!diagnostic.Contains("$." + field, StringComparison.Ordinal) || !diagnostic.Contains("ожидается", StringComparison.Ordinal))
                    throw new AssertionFailure($"Expected field diagnostic for {field}, actual: {diagnostic}");
            }
            try { new V4GenerationRequest { Category = "lineage.craft.enchant.fail" }.Validate(PromptProfiles.Default); throw new Exception("Expected rejection"); }
            catch (InvalidDataException error) { True(error.Message.Contains("Category", StringComparison.Ordinal)); True(error.Message.Contains("lineage.craft.enchant.fail", StringComparison.Ordinal)); }
        });
        await TestAsync("recovery one durable correction preserves original response and cannot loop or repeat POST after reopen", async () =>
        {
            using var f = new Fixture(); using var workspace = new WorkspaceStore(Path.Combine(f.Root, "workspace"), f.Module);
            var store = new PromptLabStore(workspace); var body = File.ReadAllText(fixturePath);
            var version = store.SaveVersion(new()); var source = store.BeginRun(version.Id, "DESCRIPTION", inputContext: "Хочу живые русские реплики фантомов в Lineage II High Five. Первая ситуация: игрок неудачно заточил дорогую вещь, а его приятель-гном язвительно над ним подшучивает. Стиль грубый, разговорный, с уместным крепким матом и настоящим игровым сленгом. Персонаж должен звучать как живой игрок, а не литературный NPC. Никаких фраз вроде «я сейчас пошучу», «в плане заточки» и нравоучений. Нужны четыре разных коротких ответа фантома.");
            store.MarkIssued(source.Id);
            store.SaveTransport(source.Id, new(new(new("{}", "{}", body, new(10, 10, 0, null, null, 1), "", 200, null, null), "original", "SYSTEM", "USER", true), "instance-1", 65536, "http://127.0.0.1:1234/v1"));
            using var handler = new RecoveryHandler(body.Replace("lineage.craft.enchant.fail", "lineage.enchant.fail", StringComparison.Ordinal));
            using var lab = new PromptLabSession(store, _ => new HttpClient(handler, false));
            source = await lab.ResumeAsync(source.Id, new(), "", "instance-1", CancellationToken.None);
            Equal("REJECTED", source.State); Equal(0, handler.Posts);
            var original = File.ReadAllBytes(Path.Combine(store.Root, "runs", source.Id, "outcome.json"));
            var repair = lab.QueueDescriptionRepair(source.Id);
            Equal("QUEUED", repair.State); Equal(source.Id, repair.BaselineRunId); Equal(0, handler.Posts);
            Equal(repair.Id, lab.QueueDescriptionRepair(source.Id).Id);
            var result = await lab.ResumeAsync(repair.Id, new(), "", "instance-1", CancellationToken.None);
            Equal("COMPLETE", result.State); Equal(1, handler.Posts);
            True(handler.Body.Contains("batches[0].category", StringComparison.Ordinal));
            True(handler.Body.Contains("lineage.craft.enchant.fail", StringComparison.Ordinal));
            True(!store.GetVersion(result.ProposedVersionId).ReadyForGeneration);
            Throws(() => lab.QueueDescriptionRepair(result.Id));
            using var reopened = new PromptLabSession(new PromptLabStore(workspace), _ => new HttpClient(handler, false));
            Equal(repair.Id, reopened.QueueDescriptionRepair(source.Id).Id);
            Equal("COMPLETE", (await reopened.ResumeAsync(repair.Id, new(), "", "instance-1", CancellationToken.None)).State);
            Equal(1, handler.Posts); True(original.SequenceEqual(File.ReadAllBytes(Path.Combine(store.Root, "runs", source.Id, "outcome.json"))));
        });
        await TestAsync("recovery invalid JSON explains the model format failure preserves evidence and never repeats the POST", async () =>
        {
            foreach (var content in new[] { "```json\n{\"records\":[]}\n```", "{" })
            {
                using var f = new Fixture(); using var workspace = new WorkspaceStore(Path.Combine(f.Root, "workspace"), f.Module);
                var store = new PromptLabStore(workspace); var version = store.SaveVersion(new());
                using var handler = new RecoveryHandler(content); using var lab = new PromptLabSession(store, _ => new HttpClient(handler, false));
                var run = await lab.GenerateAsync(version.Id, new() { HighFiveRoot = f.Module }, "", "instance-1", CancellationToken.None);
                Equal("REJECTED", run.State); Equal(content, run.Response!.Content); Equal(0, run.Rows.Count);
                True(run.Failure.Contains("JSON", StringComparison.Ordinal));
                True(run.Failure.Contains("сохранён в истории", StringComparison.Ordinal));
                True(run.Failure.Contains("новый запрос", StringComparison.Ordinal));
                if (content.StartsWith("```", StringComparison.Ordinal)) True(run.Failure.Contains("Markdown", StringComparison.Ordinal));
                True(store.GetTransport(run.Id) != null); Equal(1, handler.Posts);
                Equal("REJECTED", (await lab.ResumeAsync(run.Id, new() { HighFiveRoot = f.Module }, "", "instance-1", CancellationToken.None)).State);
                Equal(1, handler.Posts); Equal(1, store.ListVersions(version.ProfileId).Count);
            }
        });
    }

    private sealed class RecoveryHandler(string proposal) : HttpMessageHandler
    {
        public int Posts { get; private set; }
        public string Body { get; private set; } = "";
        protected override async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken token)
        {
            if (request.Method == HttpMethod.Get)
                return new(System.Net.HttpStatusCode.OK) { Content = new StringContent(request.RequestUri!.AbsolutePath == "/v1/models" ? "{\"data\":[{\"id\":\"instance-1\"}]}"
                    : "{\"models\":[{\"key\":\"gemma-local\",\"type\":\"llm\",\"loaded_instances\":[{\"id\":\"instance-1\",\"config\":{\"context_length\":65536}}]}]}", System.Text.Encoding.UTF8, "application/json") };
            Posts++; Body = await request.Content!.ReadAsStringAsync(token);
            return new(System.Net.HttpStatusCode.OK) { Content = new StringContent(PromptLabCompatSse(proposal, "stop", true), System.Text.Encoding.UTF8, "text/event-stream") };
        }
    }
}
