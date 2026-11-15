using System.Text;
using System.Text.Json;
using System.Net;
using PhantomSemanticStudio.Core;

internal static partial class Program
{
    private static async Task<int> PromptLabCompareModesLive(string[] args)
    {
        if (args.Length != 5) throw new InvalidDataException("Нужны workspace, source, exact model и baseline run.");
        using var workspace = new WorkspaceStore(args[1], args[2]); var store = new PromptLabStore(workspace); using var session = new PromptLabSession(store);
        var settings = new StudioSettings { HighFiveRoot = args[2], ModelId = args[3] }; var key = Environment.GetEnvironmentVariable("LM_STUDIO_API_KEY") ?? "";
        foreach (var mode in new[] { "off", "auto", "on" })
        {
            var revision = store.ListRuns().Where(r => r.Purpose == "REFINEMENT" && r.State == "COMPLETE" && r.BaselineRunId == args[4] && r.ApiOptions?.Reasoning == mode)
                .OrderByDescending(r => r.CreatedAtUtc).First();
            var run = await session.CompareAsync(args[4], revision.ProposedVersionId, settings, key, args[3], CancellationToken.None, new PromptLabConsoleProgress());
            Console.WriteLine("COMPARE_REFINE_MODE " + mode + " " + run.Id + " " + run.State + " " + run.Failure);
            foreach (var row in run.Rows) Console.WriteLine(row.Number + ": " + row.Text + " [" + string.Join(',', row.Diagnostics) + "]");
            if (run.State == "INDETERMINATE") return 1;
        }
        return 0;
    }
    private static async Task<int> PromptLabDescriptionLive(string[] args)
    {
        if (args.Length != 4) throw new InvalidDataException("Нужны новый workspace, source и exact model.");
        var root = PathSafety.Canonical(args[1]); if (Directory.Exists(root)) throw new InvalidDataException("Нужен новый workspace.");
        using var workspace = new WorkspaceStore(root, args[2]); var store = new PromptLabStore(workspace); using var lab = new PromptLabSession(store);
        var version = store.SaveVersion(new() { Name = "Технический live из описания, без owner approval", Generation = new() { Transport = "OPENAI_COMPAT" },
            Refinement = new() { Transport = "OPENAI_COMPAT", MaxOutputTokens = 4096 } });
        var settings = new StudioSettings { HighFiveRoot = args[2], ModelId = args[3] }; var key = Environment.GetEnvironmentVariable("LM_STUDIO_API_KEY") ?? "";
        var described = await lab.DescribeAsync(version.Id,
            "Нужны ровно две отдельные пробные партии по две фразы. Первая: грубый, но не истеричный соперник в PvP злится после поражения и обещает реванш в игре; крепкий мат допустим, без реальных данных и офлайн-действий. "
            + "Вторая: знакомый спокойно и по-человечески реагирует на усталость игрока после рабочей смены, без морали и советов из психологии. REPLY в обеих партиях, maturity GENERAL, категории из реестра, новые категории не нужны.",
            settings, key, args[3], CancellationToken.None, new PromptLabConsoleProgress());
        Console.WriteLine("DESCRIPTION " + described.State + " " + described.Failure);
        if (described.State != "COMPLETE") return 1;
        var proposal = store.GetVersion(described.ProposedVersionId);
        foreach (var scope in new[] { proposal.Scenario }.Concat(proposal.AdditionalScenarios)) Console.WriteLine("PLAN " + scope.Category + " / " + scope.Mode + " / " + scope.Style.StyleId);
        // Synthetic technical review only. No accepted prompt, LIKE rating, corpus import or production promotion.
        var reviewed = store.SaveVersion(proposal with { Id = "", ParentId = proposal.Id, Origin = "MANUAL", ProposedFromRunId = "", ReadyForGeneration = true });
        var runs = await lab.GenerateExperimentAsync(reviewed.Id, settings, key, args[3], CancellationToken.None, new PromptLabConsoleProgress());
        foreach (var run in runs) { Console.WriteLine("BATCH " + run.State + " " + run.Id + " " + run.Failure); foreach (var row in run.Rows) Console.WriteLine(row.Number + ": " + row.Text); }
        File.WriteAllText(Path.Combine(root, "description-evidence.json"), JsonSerializer.Serialize(new { Description = described.Id, Proposal = proposal.Id, Reviewed = reviewed.Id,
            SyntheticReview = true, Runs = runs.Select(r => new { r.Id, r.ExperimentId, r.State, r.Scenario, r.Failure, Usage = r.Response?.Telemetry }), Approved = 0, Imported = 0 }, WorkspaceStore.JsonOptions), new UTF8Encoding(false));
        return runs.Count == proposal.AdditionalScenarios.Count + 1 && runs.All(r => r.State == "COMPLETE") ? 0 : 1;
    }

    private static async Task<int> PromptLabRefineModesLive(string[] args)
    {
        if (args.Length is < 5 or > 6) throw new InvalidDataException("Нужны workspace, source, exact model и baseline run; опционально один режим.");
        using var workspace = new WorkspaceStore(args[1], args[2]); var store = new PromptLabStore(workspace); using var lab = new PromptLabSession(store);
        var original = store.GetRun(args[4]); var version = store.GetVersion(original.VersionId);
        var settings = new StudioSettings { HighFiveRoot = args[2], ModelId = args[3] }; var key = Environment.GetEnvironmentVariable("LM_STUDIO_API_KEY") ?? "";
        var modes = args.Length == 6 ? new[] { args[5] } : new[] { "off", "auto", "on" };
        foreach (var mode in modes)
        {
            var run = await lab.RefineAsync(original.Id, settings, key, args[3], CancellationToken.None, new PromptLabConsoleProgress(),
                version.Refinement with { Transport = "OPENAI_COMPAT", Reasoning = mode, MaxOutputTokens = 4096 });
            Console.WriteLine("REFINE_MODE " + mode + " " + run.Id + " " + run.State + " " + run.Failure);
            Console.WriteLine(JsonSerializer.Serialize(run.Response?.Telemetry, WorkspaceStore.JsonOptions));
        }
        return 0;
    }
    private static async Task<int> PromptLabLive(string[] args)
    {
        if (args.Length is < 4 or > 6) throw new InvalidDataException("Нужны новый workspace, source и exact loaded model; опционально transport генерации и отдельный transport улучшения.");
        var root = PathSafety.Canonical(args[1]); var source = PathSafety.Canonical(args[2]);
        if (Directory.Exists(root) || File.Exists(root)) throw new InvalidDataException("Для live proof нужен новый отдельный workspace.");
        var transport = args.Length >= 5 ? args[4] : "LM_NATIVE";
        var refineTransport = args.Length == 6 ? args[5] : transport;
        using var workspace = new WorkspaceStore(root, source); var store = new PromptLabStore(workspace); using var lab = new PromptLabSession(store);
        var scenario = new V4GenerationRequest { Category = "lineage.party.mistakes", Count = 3, Style = new()
        {
            Category = "lineage.party.mistakes", Voice = "rough_sensitive", Relation = "FAMILIAR", Channel = "PARTY", StyleId = "rough_humor", StrongProfanityRequested = true,
            Situation = "Сопартиец поспешил вперёд, признал ошибку и остановился. Не продолжай унижать его.",
            Dialogue = ["Игрок: Не дождался вас, мой косяк.", "Фантом: Так и понял.", "Игрок: Сейчас подожду, больше не бегу."]
        } };
        var version = store.SaveVersion(new() { Name = "Реальный технический цикл, без художественного approval", Scenario = scenario,
            System = PromptProfiles.Default.Resources["dialogue-context.v1.md"], User = "Три разных самостоятельных ответа на последний ход игрока.",
            RefinerSystem = PromptProfiles.Default.Resources["refiner.v1.md"],
            Generation = new() { Transport = transport, MinP = transport == "LM_NATIVE" ? .05 : null, Reasoning = "off", Stream = true },
            Refinement = new() { Transport = refineTransport, Reasoning = "off", MaxOutputTokens = 4096, Stream = true } });
        var settings = new StudioSettings { HighFiveRoot = source, ModelId = args[3] };
        var key = Environment.GetEnvironmentVariable("LM_STUDIO_API_KEY") ?? ""; var progress = new PromptLabConsoleProgress();
        var generated = await lab.GenerateAsync(version.Id, settings, key, args[3], CancellationToken.None, progress);
        Print("V1", generated);
        if (generated.State != "COMPLETE") return 1;
        store.SaveFeedback(generated.Id, [new(1, generated.Rows[0].TextHash, "NONE", "SYNTHETIC_TEST_FEEDBACK: проверка сохранения комментария, не оценка владельца.")],
            "SYNTHETIC_TEST_FEEDBACK: проверь разнообразие начал и точность реакции на последнее сообщение; сохрани короткую разговорную речь. Это технический тест цепочки, не художественная приёмка.");
        var refined = await lab.RefineAsync(generated.Id, settings, key, args[3], CancellationToken.None, progress);
        Print("REFINEMENT", refined);
        if (refined.State != "COMPLETE") return 1;
        var compared = await lab.CompareAsync(generated.Id, refined.ProposedVersionId, settings, key, args[3], CancellationToken.None, progress);
        Print("V2", compared);
        var accepted = store.GetProfile(version.ProfileId).AcceptedVersionId;
        var evidence = new { Status = compared.State, Model = args[3], Transport = transport, RefinementTransport = refineTransport, Context = generated.ModelContext,
            InitialVersion = version.Id, ProposedVersion = refined.ProposedVersionId, GenerationRun = generated.Id, RefinementRun = refined.Id, ComparisonRun = compared.Id,
            SyntheticFeedback = true, OwnerAcceptance = "NOT_REQUESTED_NOT_GRANTED", AcceptedVersion = accepted,
            GeneratedRecords = generated.Rows.Count + compared.Rows.Count, CorpusApproved = 0, Imported = 0, ServerInstalled = false,
            V1Usage = generated.Response?.Telemetry, RefineUsage = refined.Response?.Telemetry, V2Usage = compared.Response?.Telemetry,
            MinPRequested = version.Generation.MinP, EffectiveMinP = (double?)null };
        File.WriteAllText(Path.Combine(root, "live-workflow-evidence.json"), JsonSerializer.Serialize(evidence, WorkspaceStore.JsonOptions), new UTF8Encoding(false));
        Console.WriteLine("LIVE_WORKFLOW_EVIDENCE " + Path.Combine(root, "live-workflow-evidence.json"));
        return compared.State == "COMPLETE" && accepted.Length == 0 ? 0 : 1;

        static void Print(string name, PromptLabRun run)
        {
            Console.WriteLine(name + " " + run.State + " run=" + run.Id + "; failure=" + run.Failure);
            foreach (var row in run.Rows) Console.WriteLine(row.Number + ": " + row.Text + " [" + string.Join(',', row.Diagnostics) + "]");
        }
    }

    private sealed class PromptLabConsoleProgress : IProgress<PromptLabProgress>
    {
        private string previous = ""; private double last;
        public void Report(PromptLabProgress value)
        {
            if (value.Stage == previous && value.ElapsedSeconds - last < 5) return;
            previous = value.Stage; last = value.ElapsedSeconds;
            Console.WriteLine($"PROGRESS {value.Stage}; seconds={value.ElapsedSeconds:F1}; bytes={value.ReceivedBytes}; messageChars={value.MessageCharacters}; reasoningChars={value.ReasoningCharacters}");
        }
    }

    private static async Task PromptLab001()
    {
        Test("001 prompt versions remain immutable and a proposed revision never replaces the accepted version", () =>
        {
            using var f = new Fixture(); using var workspace = new WorkspaceStore(Path.Combine(f.Root, "workspace"), f.Module);
            var lab = new PromptLabStore(workspace);
            var first = lab.SaveVersion(new() { Name = "Живой разговор", System = "Говори коротко и по делу.", User = "Игрок устал после смены." });
            lab.AcceptVersion(first.Id);
            var before = File.ReadAllBytes(lab.VersionPath(first.Id));
            var second = lab.SaveVersion(first with { Id = "", ParentId = first.Id, System = "Ответь как усталый знакомый.", Origin = "MODEL_PROPOSAL" });
            Equal(first.Id, lab.GetProfile(first.ProfileId).AcceptedVersionId);
            True(before.SequenceEqual(File.ReadAllBytes(lab.VersionPath(first.Id))));
            lab.AcceptVersion(second.Id); lab.AcceptVersion(first.Id);
            Equal(first.Id, lab.GetProfile(first.ProfileId).AcceptedVersionId);
            Equal(2, lab.ListVersions(first.ProfileId).Count);
            Equal(first.System, new PromptLabStore(workspace).GetVersion(first.Id).System);
            var copy = lab.CopyVersion(first.Id, "Другой характер");
            True(copy.ProfileId != first.ProfileId); Equal(first.User, copy.User);
            lab.SaveDraft(first.ProfileId, first with { System = "", User = "Неоконченное замечание" });
            Equal("", new PromptLabStore(workspace).GetDraft(first.ProfileId)!.System);
            Equal("Неоконченное замечание", lab.GetDraft(first.ProfileId)!.User);
            Equal(first.System, lab.GetVersion(first.Id).System);
        });
        Test("001 prompt history rejects corrupt hashes and unsupported API options", () =>
        {
            using var f = new Fixture(); using var workspace = new WorkspaceStore(Path.Combine(f.Root, "workspace"), f.Module);
            var lab = new PromptLabStore(workspace);
            var version = lab.SaveVersion(new() { Name = "Проверка", System = "Короткая реакция.", User = "Случай из игры." });
            var path = lab.VersionPath(version.Id);
            File.WriteAllText(path, File.ReadAllText(path).Replace("Короткая реакция.", "Чужая редакция."), new UTF8Encoding(false));
            Throws(() => lab.GetVersion(version.Id)); Throws(() => lab.GetVersion("../settings"));
            Throws(() => new PromptLabApiOptions { Transport = "OPENAI_COMPAT", MinP = .05 }.Validate());
            new PromptLabApiOptions { Transport = "LM_NATIVE", MinP = .05, Reasoning = "auto" }.Validate();
        });
        Test("001 feedback pins exact run text and interrupted issued work cannot become a completed batch", () =>
        {
            using var f = new Fixture(); using var workspace = new WorkspaceStore(Path.Combine(f.Root, "workspace"), f.Module);
            var lab = new PromptLabStore(workspace);
            var version = lab.SaveVersion(new() { Name = "Отзывы", System = "Короткая реакция.", User = "Последняя реплика." });
            var pending = lab.BeginRun(version.Id); lab.MarkIssued(pending.Id);
            Equal("ISSUED", new PromptLabStore(workspace).GetRun(pending.Id).State);
            var row = new PromptLabRow(1, "С этим рандомом спорить бесполезно.", TextRules.Hash("С этим рандомом спорить бесполезно."), false, ["REVIEW_ONLY"]);
            lab.CompleteRun(pending with { State = "REJECTED", Rows = [row], Failure = "EDITORIAL_FIXTURE", CompletedAtUtc = DateTimeOffset.UtcNow });
            var first = lab.SaveFeedback(pending.Id, [new(1, row.TextHash, "LIKE", "Звучит по-человечески.")], "Нужны более разные реакции.");
            lab.SaveFeedback(pending.Id, [new(1, row.TextHash, "DISLIKE", "Слишком общий ответ.")], "Уточнил оценку.");
            var restored = new PromptLabStore(workspace);
            Equal("Уточнил оценку.", restored.LatestFeedback(pending.Id)!.BatchComment);
            Equal(2, restored.ListFeedback(pending.Id).Count);
            True(restored.ListFeedback(pending.Id).Any(r => r.Id == first.Id));
            Throws(() => lab.SaveFeedback(pending.Id, [new(1, "wrong", "LIKE", "")], ""));
            Throws(() => lab.CompleteRun(pending with { State = "COMPLETE", CompletedAtUtc = DateTimeOffset.UtcNow }));
        });
        await TestAsync("001 native streaming requires final aggregate and preserves explicit prompts sampling and reasoning", async () =>
        {
            const string content = "{\"header\":{\"schema\":\"pw-semantic-batch/1\",\"category\":\"social.friendship.support\",\"act\":\"social.friendship.support.reply\",\"mode\":\"REPLY\",\"count\":1,\"language\":\"ru\"},\"records\":[{\"text\":\"Пошли, разберёмся вместе.\",\"stance\":\"SUPPORTIVE\"}]}";
            var version = new PromptLabVersion { System = "Ответь как давний знакомый.", User = "Скажи одну живую фразу.", Scenario = new() { Category = "social.friendship.support", Count = 1, Style = new() { Category = "social.friendship.support" } },
                Generation = new() { Transport = "LM_NATIVE", Stream = true, MinP = .05 } };
            var loaded = new CorpusLoadedModel("gemma-local", "instance-1", 65536, ["off", "on"]);
            foreach (var complete in new[] { true, false })
            {
                var handler = new RecordingHandler(PromptLabSse(content, complete), mediaType: "text/event-stream"); using var http = new HttpClient(handler);
                var result = await new LmStudioClient(http).GeneratePromptLabAsync(new(), "", loaded, version, CancellationToken.None);
                using var payload = JsonDocument.Parse(handler.Payload!);
                Equal(true, payload.RootElement.GetProperty("stream").GetBoolean());
                Equal(.05, payload.RootElement.GetProperty("min_p").GetDouble());
                Equal("off", payload.RootElement.GetProperty("reasoning").GetString());
                True(payload.RootElement.GetProperty("system_prompt").GetString()!.Contains(version.System, StringComparison.Ordinal));
                True(payload.RootElement.GetProperty("input").GetString()!.Contains(version.User, StringComparison.Ordinal));
                True(!payload.RootElement.TryGetProperty("context_length", out _));
                if (complete) { Equal("", result.Response.Rejection); Equal(content, result.Response.Content); Equal(0L, result.Response.Telemetry.ReasoningTokens); }
                else { True(result.Response.Rejection.Length > 0); Equal("", result.Response.Content); }
                Equal(1, handler.Calls);
            }
            using var failedHttp = new HttpClient(new RecordingHandler(PromptLabSse(content, true, true), mediaType: "text/event-stream"));
            True((await new LmStudioClient(failedHttp).GeneratePromptLabAsync(new(), "", loaded, version, CancellationToken.None)).Response.Rejection.Length > 0);
        });
        await TestAsync("001 prompt refinement has independent sampling and reasoning and returns a proposal only", async () =>
        {
            var proposed = JsonSerializer.Serialize(new { system = "Говори проще, меняй намерение каждой реплики.", user = "Ответь на текущую ситуацию.", explanation = "Убрал повторяющуюся конструкцию." });
            var version = new PromptLabVersion { Generation = new() { Reasoning = "off", Temperature = .7 },
                Refinement = new() { Transport = "LM_NATIVE", Reasoning = "on", Temperature = .4, MaxOutputTokens = 4096 } };
            var handler = new RecordingHandler(PromptLabSse(proposed, true), mediaType: "text/event-stream"); using var http = new HttpClient(handler);
            var result = await new LmStudioClient(http).RefinePromptLabAsync(new(), "", new("gemma-local", "instance-1", 65536, ["off", "on"]), version,
                "{\"batchFeedback\":\"Повторяется начало фраз.\",\"examples\":[]}", CancellationToken.None);
            Equal("", result.Response.Rejection);
            var revision = LmStudioClient.ParsePromptLabRevision(result.Response.Content);
            Equal("Говори проще, меняй намерение каждой реплики.", revision.System);
            using var payload = JsonDocument.Parse(handler.Payload!);
            Equal("on", payload.RootElement.GetProperty("reasoning").GetString()); Equal(.4, payload.RootElement.GetProperty("temperature").GetDouble());
            Equal(4096, payload.RootElement.GetProperty("max_output_tokens").GetInt32());
            Equal("off", version.Generation.Reasoning);
            Throws(() => LmStudioClient.ParsePromptLabRevision("{\"system\":\"\",\"user\":\"\",\"explanation\":\"\"}"));
        });
        await TestAsync("001 compatible stream retains usage and model scope and refuses length and missing terminator", async () =>
        {
            const string content = "{\"header\":{},\"records\":[]}";
            var version = new PromptLabVersion(); var loaded = new CorpusLoadedModel("gemma-local", "instance-1", 65536, ["off", "on"]);
            foreach (var pair in new[] { ("stop", true), ("length", true), ("stop", false) })
            {
                var overhead = pair == ("stop", true) ? ":" + new string(' ', 1100000) + "\n\n" : "";
                var handler = new RecordingHandler(overhead + PromptLabCompatSse(content, pair.Item1, pair.Item2), mediaType: "text/event-stream");
                using var http = new HttpClient(handler);
                var result = await new LmStudioClient(http).GeneratePromptLabAsync(new(), "", loaded, version, CancellationToken.None);
                if (pair == ("stop", true)) { Equal("", result.Response.Rejection); Equal(content, result.Response.Content); Equal(0L, result.Response.Telemetry.ReasoningTokens); }
                else True(result.Response.Rejection.Length > 0);
                using var payload = JsonDocument.Parse(handler.Payload!);
                Equal("none", payload.RootElement.GetProperty("reasoning_effort").GetString());
                True(!payload.RootElement.TryGetProperty("min_p", out _)); Equal(1, handler.Calls);
            }
        });
        await TestAsync("001 durable generate feedback refine compare cycle keeps acceptance explicit and completed resume sends nothing", async () =>
        {
            using var f = new Fixture(); using var workspace = new WorkspaceStore(Path.Combine(f.Root, "workspace"), f.Module);
            var store = new PromptLabStore(workspace);
            var version = store.SaveVersion(new() { Name = "Полный цикл", System = "Отвечай коротко по текущей ситуации.", User = "Одна реакция знакомого.",
                Scenario = new() { Category = "social.friendship.support", Count = 1, Style = new() { Category = "social.friendship.support" } },
                Generation = new() { Transport = "LM_NATIVE" }, Refinement = new() { Transport = "LM_NATIVE" } });
            store.AcceptVersion(version.Id);
            var handler = new PromptLabWorkflowHandler(); using var session = new PromptLabSession(store, _ => new HttpClient(handler, false));
            var settings = new StudioSettings { HighFiveRoot = f.Module };
            var generated = await session.GenerateAsync(version.Id, settings, "", "instance-1", CancellationToken.None);
            Equal("COMPLETE", generated.State); Equal(1, generated.Rows.Count);
            store.SaveFeedback(generated.Id, [new(1, generated.Rows[0].TextHash, "LIKE", "Живой ответ.")], "Добавь разнообразия, сохрани краткость.");
            var refined = await session.RefineAsync(generated.Id, settings, "", "instance-1", CancellationToken.None);
            Equal("COMPLETE", refined.State); True(refined.ProposedVersionId.Length > 0);
            Equal(version.Id, store.GetProfile(version.ProfileId).AcceptedVersionId);
            var compared = await session.CompareAsync(generated.Id, refined.ProposedVersionId, settings, "", "instance-1", CancellationToken.None);
            Equal("COMPLETE", compared.State); Equal(generated.Id, compared.BaselineRunId); Equal(3, handler.Posts);
            Equal(compared.Id, (await session.ResumeAsync(compared.Id, settings, "", "instance-1", CancellationToken.None)).Id);
            Equal(3, handler.Posts);
            Equal("COMPLETE", new PromptLabStore(workspace).GetRun(compared.Id).State);
            var recovering = store.BeginRun(version.Id, "REFINEMENT", generated.Id, "{}"); store.MarkIssued(recovering.Id);
            var proposal = JsonSerializer.Serialize(new { system = "Ещё одна редакция.", user = "Короткий ответ по сцене.", explanation = "Проверка восстановления." });
            var durable = new PromptLabResponse(new("{}", "{}", proposal, new(300, 80, 0, null, null, 1), "", 200, null, null), "synthetic-receipt", "Редактор", "Отзыв");
            store.SaveTransport(recovering.Id, new(durable, "instance-1", 65536, settings.Endpoint));
            var existingProposal = store.SaveVersion(version with { Id = "", ParentId = version.Id, Origin = "MODEL_PROPOSAL", ProposedFromRunId = recovering.Id, System = "Ещё одна редакция.", User = "Короткий ответ по сцене." });
            Equal(existingProposal.Id, (await session.ResumeAsync(recovering.Id, settings, "", "instance-1", CancellationToken.None)).ProposedVersionId);
            Throws(() => store.SaveVersion(existingProposal with { Id = "", Scenario = existingProposal.Scenario with { Count = 2 } }));
            Equal(3, store.ListVersions(version.ProfileId).Count); Equal(3, handler.Posts);
            var routing = new PromptLabRouting(store);
            var preview = routing.Preview([generated.Id]);
            Throws(() => routing.Promote(version.Id, [generated.Id], false));
            var production = routing.Promote(version.Id, [generated.Id], true);
            Equal(version.Id, production.VersionId); Equal(1, new PromptLabStore(workspace).ListProduction().Count);
            Equal(1, preview.Validation.Entries.Count);
            Throws(() => routing.Import(preview.Id, preview.ReviewHash, false));
            var imported = routing.Import(preview.Id, preview.ReviewHash, true);
            Equal(1, imported.Accepted); Equal(1, store.IsolatedV4().Stats().Total);
            var stage = routing.BuildAndVerify(); True(stage.Verification.Success); Equal(1, stage.Build.Records);
            Equal(0, routing.Import(preview.Id, preview.ReviewHash, true).Accepted);
            store.SaveFeedback(generated.Id, [new(1, generated.Rows[0].TextHash, "DISLIKE", "Передумал.")], "Пересмотр.");
            Throws(() => routing.Import(preview.Id, preview.ReviewHash, true));
            Throws(() => routing.Preview([compared.Id]));
        });
        Test("001 description proposal uses existing categories and event bindings and remains unready until explicit review", () =>
        {
            var body = JsonSerializer.Serialize(new { system = "Разговаривай по ситуации, короткими русскими фразами. Учитывай характер, отношения и последний ход собеседника, меняй способы реакции.", user = "Дай самостоятельные короткие реплики.", explanation = "Две разные темы.",
                batches = new[] {
                    new { category = "lineage.enchant.fail", mode = "REPLY", voice = "ANY", relation = "FAMILIAR", style = "ordinary", maturity = "GENERAL", profanity = "NONE", stance = "NEUTRAL", tone = "CALM", channel = "ALL", count = 3, requiredEvent = "", situation = "Игрок сломал вещь.", dialogue = new[] { "Игрок: Ну всё, сломал." } },
                    new { category = "life.work.burnout", mode = "TRIGGER", voice = "ANY", relation = "NEUTRAL", style = "ordinary", maturity = "GENERAL", profanity = "NONE", stance = "", tone = "", channel = "ALL", count = 3, requiredEvent = "", situation = "Устал после работы.", dialogue = new[] { "Игрок: Еле живой после смены." } } },
                newCategories = Array.Empty<object>() });
            var proposal = PromptLabAuthoring.ParseProposal(body, V4Registry.Default);
            Equal(2, proposal.Batches.Count); Equal("NEUTRAL", proposal.Batches[0].ToRequest().DesiredStance);
            Throws(() => PromptLabAuthoring.ParseProposal(body.Replace("lineage.enchant.fail", "lineage.unknown.fake"), V4Registry.Default));
            Throws(() => PromptLabAuthoring.ParseProposal(body.Replace("\"mode\":\"REPLY\"", "\"mode\":\"INITIATIVE\""), V4Registry.Default));
            using var f = new Fixture(); using var workspace = new WorkspaceStore(Path.Combine(f.Root, "workspace"), f.Module);
            var store = new PromptLabStore(workspace);
            var pending = store.SaveVersion(new() { Name = "Из описания", System = proposal.System, User = proposal.User,
                Scenario = proposal.Batches[0].ToRequest(), AdditionalScenarios = proposal.Batches.Skip(1).Select(b => b.ToRequest()).ToArray(), ReadyForGeneration = false });
            Throws(() => store.AcceptVersion(pending.Id));
            Equal(1, store.GetVersion(pending.Id).AdditionalScenarios.Count);
        });
        await TestAsync("001 description stays editable and approved multi-mode plan saves separate batches with a common experiment", async () =>
        {
            using var f = new Fixture(); using var workspace = new WorkspaceStore(Path.Combine(f.Root, "workspace"), f.Module);
            var store = new PromptLabStore(workspace); var handler = new PromptLabWorkflowHandler(true);
            using var session = new PromptLabSession(store, _ => new HttpClient(handler, false));
            var version = store.SaveVersion(new() { Name = "Описание", Generation = new() { Transport = "LM_NATIVE" }, Refinement = new() { Transport = "LM_NATIVE" } });
            var settings = new StudioSettings { HighFiveRoot = f.Module };
            var result = await session.DescribeAsync(version.Id, "Усталость и короткая поддержка.", settings, "", "instance-1", CancellationToken.None);
            Equal("COMPLETE", result.State); var proposed = store.GetVersion(result.ProposedVersionId); True(!proposed.ReadyForGeneration);
            Throws(() => store.BeginRun(proposed.Id));
            var reviewed = store.SaveVersion(proposed with { Id = "", ParentId = proposed.Id, Origin = "MANUAL", ProposedFromRunId = "", ReadyForGeneration = true });
            var runs = await session.GenerateExperimentAsync(reviewed.Id, settings, "", "instance-1", CancellationToken.None);
            Equal(2, runs.Count); foreach (var run in runs) Equal("COMPLETE", run.Failure.Length > 0 ? run.State + ": " + run.Failure : run.State);
            True(runs[0].ExperimentId.Length > 0); Equal(runs[0].ExperimentId, runs[1].ExperimentId);
            Equal(2, (await session.ResumeExperimentAsync(runs[0].Id, settings, "", "instance-1", CancellationToken.None)).Count); Equal(3, handler.Posts);
            store.AcceptVersion(reviewed.Id);
            foreach (var run in runs) store.SaveFeedback(run.Id, [new(1, run.Rows[0].TextHash, "LIKE", "Смысл проверен в фикстуре.")], "");
            var refined = await session.RefineAsync(runs[1].Id, settings, "", "instance-1", CancellationToken.None);
            Equal("COMPLETE", refined.State); var revision = store.GetVersion(refined.ProposedVersionId);
            Equal(reviewed.Scenario.Category, revision.Scenario.Category); Equal(reviewed.Scenario.Mode, revision.Scenario.Mode);
            Equal(reviewed.AdditionalScenarios[0].Mode, revision.AdditionalScenarios[0].Mode);
            var routing = new PromptLabRouting(store); var preview = routing.Preview(runs.Select(r => r.Id).ToArray()); True(preview.Validation.Success);
            Equal(2, Directory.GetFiles(preview.JsonlPath, "*.jsonl").Length); Equal(2, routing.Import(preview.Id, preview.ReviewHash, true).Accepted);
            True(routing.BuildAndVerify().Verification.Success); Equal(reviewed.Id, store.GetProfile(version.ProfileId).AcceptedVersionId);
        });
        await TestAsync("001 interrupted generation stops the queued experiment and resume cannot conceal an indeterminate POST", async () =>
        {
            using var f = new Fixture(); using var workspace = new WorkspaceStore(Path.Combine(f.Root, "workspace"), f.Module);
            var store = new PromptLabStore(workspace); var version = store.SaveVersion(new() { Generation = new() { Transport = "LM_NATIVE" }, AdditionalScenarios = [new PromptLabVersion().Scenario] });
            var handler = new PromptLabWorkflowHandler(drop: true); using var session = new PromptLabSession(store, _ => new HttpClient(handler, false));
            var runs = await session.GenerateExperimentAsync(version.Id, new(), "", "instance-1", CancellationToken.None);
            Equal(1, runs.Count); Equal("INDETERMINATE", runs[0].State); Equal(1, handler.Posts);
            var resumed = await session.ResumeExperimentAsync(runs[0].Id, new(), "", "instance-1", CancellationToken.None);
            Equal(1, resumed.Count); Equal(1, handler.Posts); Equal(1, store.ListRuns().Count(r => r.State == "QUEUED"));
        });
        Test("001 native rows cannot broaden the requested persona relation maturity or event", () =>
        {
            var category = V4Registry.Default.Categories.Single(c => c.Key == "lineage.pvp.trash_talk");
            var request = new V4GenerationRequest { Category = category.Key, Count = 1, Mode = "INITIATIVE", RequiredEvent = "pvp.attack.received",
                Style = new() { Category = category.Key, Voice = "bold_rival", Relation = "RIVAL", StyleId = "pvp_trash" } };
            string Body(string voice, string relation, string maturity, string eventCode) => JsonSerializer.Serialize(new { header = new { schema = "pw-semantic-batch/1", category = category.Key, mode = "INITIATIVE", act = category.Act, language = "ru", count = 1 },
                records = new[] { new { text = "Ну всё, реванш за мной.", stance = "NEUTRAL", voices = new[] { voice }, relations = new[] { relation }, maturity, requiredEvent = eventCode } } });
            Throws(() => V4CorpusGeneration.ToJsonl(Body("ANY", "RIVAL", "GENERAL", request.RequiredEvent), request));
            Throws(() => V4CorpusGeneration.ToJsonl(Body("bold_rival", "TRUSTED", "GENERAL", request.RequiredEvent), request));
            Throws(() => V4CorpusGeneration.ToJsonl(Body("bold_rival", "RIVAL", "MATURE", request.RequiredEvent), request));
            Throws(() => V4CorpusGeneration.ToJsonl(Body("bold_rival", "RIVAL", "GENERAL", "pvp.kill.caused"), request));
        });
    }

    private static string PromptLabSse(string content, bool complete, bool error = false)
    {
        string Event(string type, object data) => "event: " + type + "\ndata: " + JsonSerializer.Serialize(data) + "\n\n";
        var stream = Event("chat.start", new { type = "chat.start", model_instance_id = "instance-1" })
            + Event("message.delta", new { type = "message.delta", content });
        if (error) stream += Event("error", new { type = "error", error = new { type = "internal_error", message = "synthetic interruption" } });
        if (complete) stream += Event("chat.end", new { type = "chat.end", result = new { model_instance_id = "instance-1", output = new[] { new { type = "message", content } }, stats = new { input_tokens = 300, total_output_tokens = 80, reasoning_output_tokens = 0 } } });
        return stream;
    }

    private static string PromptLabCompatSse(string content, string finish, bool done)
    {
        string Data(object item) => "data: " + JsonSerializer.Serialize(item) + "\n\n";
        return Data(new { model = "gemma-local", choices = new[] { new { index = 0, delta = new { role = "assistant", content }, finish_reason = (string?)null } } })
            + Data(new { model = "gemma-local", choices = new[] { new { index = 0, delta = new { }, finish_reason = finish } } })
            + Data(new { model = "gemma-local", choices = Array.Empty<object>(), usage = new { prompt_tokens = 300, completion_tokens = 80, completion_tokens_details = new { reasoning_tokens = 0 } } })
            + (done ? "data: [DONE]\n\n" : "");
    }

    private sealed class PromptLabWorkflowHandler(bool description = false, bool drop = false) : HttpMessageHandler
    {
        public int Posts { get; private set; }
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken token)
        {
            string body; var type = "application/json";
            if (request.Method == HttpMethod.Get)
                body = request.RequestUri!.AbsolutePath == "/v1/models" ? "{\"data\":[{\"id\":\"instance-1\"}]}"
                    : "{\"models\":[{\"key\":\"gemma-local\",\"type\":\"llm\",\"loaded_instances\":[{\"id\":\"instance-1\",\"config\":{\"context_length\":65536}}],\"capabilities\":{\"reasoning\":{\"allowed_options\":[\"off\",\"on\"]}}}]}";
            else
            {
                Posts++; type = "text/event-stream";
                var reply = "{\"header\":{\"schema\":\"pw-semantic-batch/1\",\"category\":\"social.friendship.support\",\"act\":\"social.friendship.support.reply\",\"mode\":\"REPLY\",\"count\":1,\"language\":\"ru\"},\"records\":[{\"text\":\"Пошли, разберёмся вместе.\",\"stance\":\"SUPPORTIVE\",\"tone\":\"CALM\"}]}";
                if (description && Posts == 1)
                {
                    var scopes = new[] { new PromptLabPlannedBatch("social.friendship.support", "REPLY", "ANY", "UNKNOWN", "ordinary", "GENERAL", "NONE", "SUPPORTIVE", "CALM", "ALL", 1, "", "Игрок устал.", ["Игрок: Выдохся."]),
                        new PromptLabPlannedBatch("social.friendship.support", "TRIGGER", "ANY", "UNKNOWN", "ordinary", "GENERAL", "NONE", "", "", "ALL", 1, "", "Игрок просит поддержки.", ["Игрок: Поможешь?"]) };
                    reply = JsonSerializer.Serialize(new { system = "Короткая конкретная русская речь. Учитывай ситуацию, последний ход и характер. Меняй реакции, не пересказывай шутку вместо самой шутки.", user = "Соблюдай назначение партии.", explanation = "Два режима.", batches = scopes, newCategories = Array.Empty<object>() }, new JsonSerializerOptions { PropertyNamingPolicy = JsonNamingPolicy.CamelCase });
                }
                else if (description && Posts == 3) reply = JsonSerializer.Serialize(new { header = new { schema = "pw-semantic-batch/1", category = "social.friendship.support",
                    act = V4Registry.Default.Categories.Single(c => c.Key == "social.friendship.support").Act, mode = "TRIGGER", count = 1, language = "ru" },
                    records = new[] { new { text = "Помоги, а то я совсем выдохся.", intent = V4Registry.Default.Categories.Single(c => c.Key == "social.friendship.support").Intent } } });
                else if (description && Posts == 4 || !description && Posts == 2) reply = JsonSerializer.Serialize(new { system = "Меняй цели коротких реакций.", user = "Ответь на последний ход.", explanation = "Сохранил краткость." });
                body = PromptLabSse(reply, !drop);
            }
            return Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK) { Content = new StringContent(body, Encoding.UTF8, type) });
        }
    }
}
