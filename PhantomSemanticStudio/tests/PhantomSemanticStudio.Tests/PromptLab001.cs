using System.Text;
using System.Text.Json;
using System.Net;
using PhantomSemanticStudio.Core;

internal static partial class Program
{
    private static int PromptLabPilotCensus(string[] args)
    {
        if (args.Length != 3) throw new InvalidDataException("Нужны frozen lab и новый output JSON рядом с ним.");
        var root = PathSafety.Canonical(args[1]); var output = PathSafety.Canonical(args[2]);
        if (File.Exists(output) || Path.GetDirectoryName(output) != Path.GetDirectoryName(root)) throw new InvalidDataException("Только новый отчёт рядом с lab.");
        var options = new JsonSerializerOptions { PropertyNameCaseInsensitive = true };
        var outcomes = Directory.GetFiles(Path.Combine(root, "results"), "*.json").Select(path => JsonSerializer.Deserialize<CorpusPilotOutcome>(File.ReadAllText(path), options)!).ToArray();
        var rows = outcomes.SelectMany(outcome => outcome.Entries.Select((entry, index) => new { outcome.JobId, outcome.CaseId, outcome.Request.Strategy, Sampler = outcome.Request.Sampler.Id,
            Number = index + 1, entry.Text, entry.Profanity, entry.Maturity, entry.Stance, entry.Tone, Indicators = CorpusQuality.MeasureSpeech([entry.Text]),
            FrozenFlags = outcome.Issues.Where(i => i.Record == index + 1 || i.Record == 0).Select(i => i.Code).ToArray() })).ToArray();
        string Normalize(string value) => System.Text.RegularExpressions.Regex.Replace(value.Normalize(NormalizationForm.FormKC), @"\s+", " ").Trim().ToLowerInvariant();
        var summaries = outcomes.GroupBy(o => new { o.Request.Strategy, Sampler = o.Request.Sampler.Id }).Select(group => new
        {
            group.Key, Requests = group.Count(), SchemaValid = group.Count(o => o.SchemaValid), Rows = group.Sum(o => o.Entries.Length),
            OutputTokens = group.Sum(o => o.Response?.Telemetry.OutputTokens ?? 0), ReasoningTokens = group.Sum(o => o.Response?.Telemetry.ReasoningTokens ?? 0),
            WallSeconds = group.Sum(o => o.Response?.Telemetry.WallSeconds ?? 0), Indicators = CorpusQuality.MeasureSpeech(group.SelectMany(o => o.Entries).Select(e => e.Text)),
            LabelStrong = group.SelectMany(o => o.Entries).Count(e => e.Profanity == "STRONG"), LabelMature = group.SelectMany(o => o.Entries).Count(e => e.Maturity == "MATURE")
        }).ToArray();
        var duplicates = rows.GroupBy(r => Normalize(r.Text)).Where(g => g.Count() > 1).Select(g => new { Text = g.First().Text, Occurrences = g.Select(x => new { x.JobId, x.Number }).ToArray() }).ToArray();
        var report = new { Status = "READ_ONLY_FROZEN_OUTCOME_REANALYSIS_NOT_APPROVAL", Requests = outcomes.Length, Rows = rows.Length,
            OriginalAssemblies = outcomes.Select(o => o.AssemblyHash).Distinct().ToArray(), AnalyzerAssembly = Convert.ToHexStringLower(System.Security.Cryptography.SHA256.HashData(File.ReadAllBytes(typeof(CorpusQuality).Assembly.Location))),
            ExactDuplicateExtraRows = duplicates.Sum(g => g.Occurrences.Length - 1), Summaries = summaries, Duplicates = duplicates, Records = rows };
        File.WriteAllText(output, JsonSerializer.Serialize(report, WorkspaceStore.JsonOptions), new UTF8Encoding(false));
        Console.WriteLine("CENSUS requests=" + outcomes.Length + "; rows=" + rows.Length + "; exact duplicate extras=" + report.ExactDuplicateExtraRows + "; approved=0"); return 0;
    }

    private static async Task<int> PromptLabIntimacyLive(string[] args)
    {
        if (args.Length != 6 || args[4] != "--mature-pilot-consent" || args[5] != "--approved-intimacy-category" || Directory.Exists(args[1]))
            throw new InvalidDataException("Нужны новый workspace, source, exact model и оба явных флага согласия/утверждённой экспериментальной категории.");
        using var workspace = new WorkspaceStore(args[1], args[2]); var store = new PromptLabStore(workspace); using var lab = new PromptLabSession(store);
        var registry = store.IsolatedV4(); var proposal = registry.Propose("life.love", "life.love.intimacy", "разговоры о близости и интимных предпочтениях", "life.love.intimacy.reply", "life.love.intimacy.observe");
        registry.Approve(proposal.Path, proposal.Hash, true);
        var seed = lab.CreateSpeechProfile("intimate_conversation"); var scope = seed.Scenario with { Count = 4, ProfanityLimit = "STRONG", Style = seed.Scenario.Style with
        {
            OwnerMatureOptIn = true, AdultsConsenting = true, StrongProfanityRequested = true,
            Situation = "Согласные вымышленные взрослые в доверенном приватном разговоре прямо обсуждают интимные предпочтения и стеснение. Это не знакомства, не флирт и не шутка. "
                + "Без эротической сцены, описаний действий, выдуманной анатомии и опыта говорящего. Отвечай предметно на уже названную тему.",
            Dialogue = ["Игрок: Мне в сексе важнее нежность, чем вся эта показная уверенность. Но сказать об этом неловко: вдруг засмеёшься?"]
        } };
        var settings = new StudioSettings { HighFiveRoot = args[2], ModelId = args[3] }; var key = Environment.GetEnvironmentVariable("LM_STUDIO_API_KEY") ?? "";
        var results = new List<object>(); var samplers = V4CorpusGeneration.LoadSamplers(Path.Combine(AppContext.BaseDirectory, "prompts", "corpus001"));
        for (var arm = 0; arm < samplers.Count; arm++)
            for (var index = 0; index < PromptProfiles.Strategies.Count; index++)
            {
                var strategy = PromptProfiles.Strategies[(index + arm * 2) % PromptProfiles.Strategies.Count]; var sampler = samplers[arm];
                var version = lab.CreateProfile("Интимный разговор · " + strategy + " · " + sampler.Id, strategy, scope);
                version = store.SaveVersion(version with { Id = "", ParentId = version.Id,
                    System = version.System + "\nОтдельный профиль прямого интимного разговора: " + PromptProfiles.Default.Styles.Single(s => s.Id == "intimate_conversation").Instruction,
                    User = "Четыре самостоятельных человеческих реакции на конкретное признание. Не подменяй разговор флиртом, обещанием поговорить или шуткой.",
                    Generation = new() { Temperature = sampler.Temperature, TopP = sampler.TopP, TopK = sampler.TopK, RepeatPenalty = sampler.RepeatPenalty } });
                var run = await lab.GenerateAsync(version.Id, settings, key, args[3], CancellationToken.None, new PromptLabConsoleProgress());
                Console.WriteLine("INTIMACY " + strategy + "/" + sampler.Id + " " + run.State + " " + run.Failure);
                foreach (var row in run.Rows) Console.WriteLine(row.Number + ": " + row.Text + " [" + string.Join(',', row.Diagnostics) + "]");
                results.Add(new { Strategy = strategy, Sampler = sampler.Id, Run = run.Id, run.State, run.Failure, Usage = run.Response?.Telemetry,
                    Indicators = CorpusQuality.MeasureSpeech(run.Rows.Select(r => r.Text)), SeedRequested = (int?)null });
                if (run.State == "INDETERMINATE") return 1;
            }
        File.WriteAllText(Path.Combine(args[1], "intimacy-comparison-evidence.json"), JsonSerializer.Serialize(new { CategoryApproval = proposal, OwnerApprovedRegistryOnly = true,
            Profiles = results, CorpusApproved = 0, Imported = 0, ServerRegistryChanged = false }, WorkspaceStore.JsonOptions), new UTF8Encoding(false)); return 0;
    }
    private static async Task<int> PromptLabMultiturnLive(string[] args)
    {
        if (args.Length != 4 || Directory.Exists(args[1])) throw new InvalidDataException("Нужны новый workspace, source и exact model.");
        using var workspace = new WorkspaceStore(args[1], args[2]); var store = new PromptLabStore(workspace); using var lab = new PromptLabSession(store);
        var version = store.SaveVersion(new() { Name = "Связанные ходы торга: частный технический эксперимент", System = "Ты игрок-продавец. Реагируй на последнее предложение покупателя и изменения условий. "
            + "Не соглашайся автоматически, не выдумывай совершённую сделку. Помни только явно переданную историю. Это независимый очередной ход, не набор реплик сцены.",
            User = "Один короткий естественный ответ продавца.", Generation = new() { MaxOutputTokens = 1024 },
            Scenario = new() { Category = "lineage.commerce.bargaining", Count = 1, Style = new() { Category = "lineage.commerce.bargaining", Voice = "dry_wit", Channel = "TELL",
                Situation = "Синтетический торг игроков за неназванный меч. Продавец просит100тысяч адены; предмет и цифры заданы только для этой сцены, это не рыночный факт. Сделка ещё не заключена." } } });
        var players = new[] { "За меч сотку? Давай восемьдесят и мои старые перчатки.", "Перчатки я всё-таки оставлю себе. Только адена: девяносто.",
            "Ты же не решил, что я про старое предложение? Я про последнее.", "Нет, передумал. Сделки не будет." };
        var settings = new StudioSettings { HighFiveRoot = args[2], ModelId = args[3] }; var key = Environment.GetEnvironmentVariable("LM_STUDIO_API_KEY") ?? "";
        var traces = new List<object>();
        foreach (var mode in new[] { "FULL_LAST_6", "LAST_USER_ONLY" })
        {
            var experiment = Guid.NewGuid().ToString("N"); var history = new List<string>();
            for (var turn = 0; turn < players.Length; turn++)
            {
                history.Add("Игрок: " + players[turn]);
                var context = mode == "FULL_LAST_6" ? history.TakeLast(6).ToArray() : new[] { history[^1] };
                var queued = store.BeginRun(version.Id, scenario: version.Scenario with { Style = version.Scenario.Style with { Dialogue = context } }, experimentId: experiment);
                var run = await lab.ResumeAsync(queued.Id, settings, key, args[3], CancellationToken.None, new PromptLabConsoleProgress());
                Console.WriteLine("SCENE " + mode + " turn=" + (turn + 1) + " " + run.State + " " + run.Failure);
                if (run.State != "COMPLETE") return 1;
                var answer = run.Rows.Single().Text; history.Add("Фантом: " + answer); Console.WriteLine("PLAYER " + players[turn]); Console.WriteLine("PHANTOM " + answer);
                traces.Add(new { Mode = mode, Scene = experiment, Turn = turn + 1, Run = run.Id, Player = players[turn], Answer = answer, SentContext = context, Usage = run.Response?.Telemetry });
            }
        }
        File.WriteAllText(Path.Combine(args[1], "scene-research.json"), JsonSerializer.Serialize(new { Schema = "PRIVATE_SCENE_RESEARCH_ONLY", Turns = traces,
            Selection = "SINGLE_SCHEMA_VALID_CANDIDATE_TECHNICAL_CHOICE_NOT_OWNER_APPROVAL", RuntimeLinkedDialogue = false, Approved = 0, Imported = 0 }, WorkspaceStore.JsonOptions), new UTF8Encoding(false));
        return 0;
    }

    private static async Task<int> PromptLabSpeechProfilesLive(string[] args)
    {
        if (args.Length != 5 || args[4] != "--mature-pilot-consent" || Directory.Exists(args[1])) throw new InvalidDataException("Нужны новый workspace, source, exact model и явный --mature-pilot-consent.");
        using var workspace = new WorkspaceStore(args[1], args[2]); var store = new PromptLabStore(workspace); using var lab = new PromptLabSession(store);
        var settings = new StudioSettings { HighFiveRoot = args[2], ModelId = args[3] }; var key = Environment.GetEnvironmentVariable("LM_STUDIO_API_KEY") ?? "";
        var results = new List<object>();
        foreach (var style in new[] { "strong_profanity", "vulgar_humor", "double_meanings", "adult_flirt", "intimate_conversation" })
        {
            var draft = lab.CreateSpeechProfile(style);
            if (draft.CategorySuggestions.Count > 0)
            {
                Console.WriteLine("PROFILE " + style + " PENDING_EXPLICIT_TAXONOMY_APPROVAL");
                results.Add(new { Style = style, Version = draft.Id, State = "PENDING_TAXONOMY_APPROVAL" }); continue;
            }
            var player = style switch { "strong_profanity" => "Да блядь, опять отлетел от рандома. Ну и вечер!", "vulgar_humor" => "Я на свидании так громко пукнул, что музыка как будто выключилась. Ну скажи что-нибудь неприлично смешное.",
                "double_meanings" => "У тебя всё про заточку. А если я скажу, что у меня тоже инструмент требует внимания?", "adult_flirt" => "Я не за покупками пришёл. Просто хотел, чтобы ты задержалась рядом.",
                _ => "Мне неловко говорить, что мне нравится в сексе. Боюсь, что ты засмеёшься." };
            var reviewed = store.SaveVersion(draft with { Id = "", ParentId = draft.Id, ReadyForGeneration = true, Scenario = draft.Scenario with { Count = 4,
                Style = draft.Scenario.Style with { OwnerMatureOptIn = draft.Scenario.Style.MatureRequested, AdultsConsenting = draft.Scenario.Style.MatureRequested,
                    Situation = draft.Scenario.Style.Situation + " Синтетический пилот: согласные взрослые персонажи. На прямую тему отвечай предметно, не описывай стиль ответа.", Dialogue = ["Игрок: " + player] } } });
            var run = await lab.GenerateAsync(reviewed.Id, settings, key, args[3], CancellationToken.None, new PromptLabConsoleProgress());
            Console.WriteLine("PROFILE " + style + " " + run.State + " " + run.Failure);
            foreach (var row in run.Rows) Console.WriteLine(row.Number + ": " + row.Text + " [" + string.Join(',', row.Diagnostics) + "]");
            results.Add(new { Style = style, Run = run.Id, run.State, run.Failure, Usage = run.Response?.Telemetry,
                Indicators = CorpusQuality.MeasureSpeech(run.Rows.Select(r => r.Text)), LabelStrong = run.Rows.Count(r => r.Profanity == "STRONG") });
            if (run.State == "INDETERMINATE") break;
        }
        File.WriteAllText(Path.Combine(args[1], "separate-profile-evidence.json"), JsonSerializer.Serialize(new { Profiles = results, OwnerApproved = 0, Imported = 0 }, WorkspaceStore.JsonOptions), new UTF8Encoding(false));
        return 0;
    }
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
        var familyPlan = args.Length == 5 && args[4] == "--families";
        if (args.Length != 4 && !familyPlan) throw new InvalidDataException("Нужны новый workspace, source и exact model; опционально --families.");
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
        var proposedScopes = new[] { proposal.Scenario }.Concat(proposal.AdditionalScenarios).ToArray();
        if (proposedScopes.Length != 2 || proposedScopes.Any(s => s.Count != 2 || s.Mode != "REPLY" || s.Style.MatureRequested))
        { Console.WriteLine("PLAN_SCOPE_REVIEW_REQUIRED: ожидались ровно две GENERAL REPLY-партии по две строки; генерация не начата."); return 1; }
        foreach (var scope in new[] { proposal.Scenario }.Concat(proposal.AdditionalScenarios)) Console.WriteLine("PLAN " + scope.Category + " / " + scope.Mode + " / " + scope.Style.StyleId);
        // Synthetic technical review only. No accepted prompt, LIKE rating, corpus import or production promotion.
        IReadOnlyList<PromptLabRun> runs; IReadOnlyList<string> reviewedIds; var groupId = "";
        if (familyPlan)
        {
            var group = lab.SplitFamilyPlan(proposal.Id); groupId = group.Id; reviewedIds = group.VersionIds;
            runs = await lab.GenerateFamilyExperimentAsync(reviewedIds, settings, key, args[3], CancellationToken.None, new PromptLabConsoleProgress());
        }
        else
        {
            var reviewed = store.SaveVersion(proposal with { Id = "", ParentId = proposal.Id, Origin = "MANUAL", ProposedFromRunId = "", ReadyForGeneration = true });
            reviewedIds = [reviewed.Id]; runs = await lab.GenerateExperimentAsync(reviewed.Id, settings, key, args[3], CancellationToken.None, new PromptLabConsoleProgress());
        }
        foreach (var run in runs) { Console.WriteLine("BATCH " + run.State + " " + run.Id + " " + run.Failure); foreach (var row in run.Rows) Console.WriteLine(row.Number + ": " + row.Text); }
        File.WriteAllText(Path.Combine(root, "description-evidence.json"), JsonSerializer.Serialize(new { Description = described.Id, Proposal = proposal.Id, ReviewedVersions = reviewedIds, FamilyGroup = groupId,
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
        var hierarchy = args[^1] == "--hierarchy"; if (hierarchy) args = args[..^1];
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
        var draft = new PromptLabVersion { Name = "Реальный технический цикл, без художественного approval", Scenario = scenario,
            System = PromptProfiles.Default.Resources["dialogue-context.v1.md"], User = "Три разных самостоятельных ответа на последний ход игрока.",
            RefinerSystem = PromptProfiles.Default.Resources["refiner.v1.md"],
            Generation = new() { Transport = transport, MinP = transport == "LM_NATIVE" ? .05 : null, Reasoning = "off", Stream = true },
            Refinement = new() { Transport = refineTransport, Reasoning = "off", MaxOutputTokens = 4096, Stream = true } };
        if (hierarchy)
        {
            var frozen = PromptHierarchy.Default.Freeze(scenario, PromptProfiles.Default, store.EffectiveRegistry());
            draft = draft with { System = frozen.FamilyInstruction, Scenario = scenario with { Components = frozen } };
        }
        var version = store.SaveVersion(draft);
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
            Hierarchy = hierarchy, Components = version.Scenario.Components,
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
        Test("001 speech profiles separate coarse language vulgar humour innuendo flirting and intimate discussion", () =>
        {
            using var f = new Fixture(); using var workspace = new WorkspaceStore(Path.Combine(f.Root, "workspace"), f.Module);
            var store = new PromptLabStore(workspace); using var session = new PromptLabSession(store);
            var profiles = new[] { "strong_profanity", "vulgar_humor", "double_meanings", "adult_flirt", "intimate_conversation" }.Select(session.CreateSpeechProfile).ToArray();
            Equal(5, profiles.Select(p => p.Scenario.Style.StyleId).Distinct().Count());
            True(profiles[0].ReadyForGeneration && profiles[0].Scenario.Style.StrongProfanityRequested);
            True(profiles.Skip(1).All(p => !p.ReadyForGeneration && !p.Scenario.Style.OwnerMatureOptIn));
            True(profiles.Single(p => p.Scenario.Style.StyleId == "intimate_conversation").Scenario.Style.Situation.Contains("соглас", StringComparison.Ordinal));
            Equal(1, profiles.Single(p => p.Scenario.Style.StyleId == "intimate_conversation").CategorySuggestions.Count);
            Equal("social.banter.rough", profiles.Single(p => p.Scenario.Style.StyleId == "vulgar_humor").Scenario.Category);
            var vulgar = profiles.Single(p => p.Scenario.Style.StyleId == "vulgar_humor").Scenario;
            Throws(() => vulgar.Validate(PromptProfiles.Default));
            (vulgar with { Style = vulgar.Style with { OwnerMatureOptIn = true, AdultsConsenting = true } }).Validate(PromptProfiles.Default);
            var usage = CorpusQuality.MeasureSpeech(["Ебать, опять мимо.", "Ну и пиздец.", "Хули ты ждал?", "Он хулиган.", "Я член клана."]);
            Equal(3, usage.StrongIndicators); Equal(0, usage.AdultIndicators);
            var intimacy = CorpusQuality.MeasureSpeech(["Мне важна нежность в близости."]);
            Equal(0, intimacy.AdultIndicators); Equal(1, intimacy.IntimacyTopicIndicators);
        });
        Test("001 hierarchy covers all canonical categories and composes only the selected family", () =>
        {
            var hierarchy = PromptHierarchy.Default;
            Equal(20, hierarchy.Families.Count);
            var coverage = hierarchy.Coverage(V4Registry.Default);
            Equal(142, coverage.Count); Equal(142, coverage.Select(row => row.Category).Distinct().Count());
            True(coverage.All(row => row.QualityStatus == "UNCONFIRMED"));
            var request = new V4GenerationRequest { Category = "lineage.commerce.bargaining", Count = 1, Style = new() { Category = "lineage.commerce.bargaining" } };
            var frozen = hierarchy.Freeze(request, PromptProfiles.Default, V4Registry.Default);
            Equal("commerce_trade", frozen.FamilyId);
            var text = PromptHierarchy.Compose(frozen);
            True(text.Contains("СЕМЕЙСТВО commerce_trade", StringComparison.Ordinal));
            True(!text.Contains("СЕМЕЙСТВО intimacy", StringComparison.Ordinal));
            True(!text.Contains("девствен", StringComparison.OrdinalIgnoreCase));
            True(Encoding.UTF8.GetByteCount(text) < 8192);
            Throws(() => hierarchy.Freeze(request with { Category = "life.love.intimacy", Style = request.Style with { Category = "life.love.intimacy" } }, PromptProfiles.Default, V4Registry.Default));
            Throws(() => hierarchy.FamilyFor("social.friendship.typo"));
        });
        Test("001 frozen family remains unchanged when an unrelated family resource is edited", () =>
        {
            var source = File.ReadAllText(Path.Combine(AppContext.BaseDirectory, "prompts", "corpus001", "families.v1.json"));
            var first = PromptHierarchy.Parse(source);
            var request = new V4GenerationRequest { Category = "lineage.commerce.bargaining", Count = 1, Style = new() { Category = "lineage.commerce.bargaining" } };
            var before = first.Freeze(request, PromptProfiles.Default, V4Registry.Default);
            var node = System.Text.Json.Nodes.JsonNode.Parse(source)!;
            var other = node["families"]!.AsArray().Single(item => item!["id"]!.GetValue<string>() == "wellbeing_rest")!;
            other["instruction"] = other["instruction"]!.GetValue<string>() + " Не навязывай совет.";
            var after = PromptHierarchy.Parse(node.ToJsonString()).Freeze(request, PromptProfiles.Default, V4Registry.Default);
            Equal(before.FunctionalHash, after.FunctionalHash); Equal(PromptHierarchy.Compose(before), PromptHierarchy.Compose(after));
            True(before.ConfigHash != after.ConfigHash);
            var changedActor = request with { Style = request.Style with { EmotionalState = "устал, отвечает сухо" } };
            var rebound = first.Rebind(before, changedActor, PromptProfiles.Default, V4Registry.Default, before.FamilyInstruction);
            True(rebound.FunctionalHash != before.FunctionalHash); Equal(before.CommonContract, rebound.CommonContract);
            Equal(before.CategoryInstruction, rebound.CategoryInstruction);
            (changedActor with { Components = rebound }).Validate(PromptProfiles.Default);
            node["categoryRules"]!.AsArray().First(item => item!["category"] != null)!["category"] = "life.love.typo";
            Throws(() => PromptHierarchy.Parse(node.ToJsonString()));
        });
        await TestAsync("001 component generation sends its frozen family and rejects changed actor scope before HTTP", async () =>
        {
            var scope = new V4GenerationRequest { Category = "social.friendship.support", Count = 1, Style = new() { Category = "social.friendship.support" } };
            var frozen = PromptHierarchy.Default.Freeze(scope, PromptProfiles.Default, V4Registry.Default);
            var version = new PromptLabVersion { System = frozen.FamilyInstruction, Scenario = scope with { Components = frozen }, Generation = new() { Transport = "LM_NATIVE" } };
            var body = "{\"header\":{},\"records\":[]}"; var handler = new RecordingHandler(PromptLabSse(body, true), mediaType: "text/event-stream");
            using var http = new HttpClient(handler); var client = new LmStudioClient(http); var loaded = new CorpusLoadedModel("gemma-local", "instance-1", 65536, ["off", "on"]);
            var result = await client.GeneratePromptLabAsync(new(), "", loaded, version, CancellationToken.None);
            Equal("", result.Response.Rejection); True(result.EffectiveSystem.Contains("СЕМЕЙСТВО friendship_support", StringComparison.Ordinal));
            True(!result.EffectiveSystem.Contains("Corpus001 / общий контракт / v1.3", StringComparison.Ordinal));
            await ThrowsAsync(() => client.GeneratePromptLabAsync(new(), "", loaded, version with { Scenario = version.Scenario with { Style = version.Scenario.Style with { EmotionalState = "раздражён" } } }, CancellationToken.None));
            Equal(1, handler.Calls);
            Throws(() => PromptHierarchy.Compose(frozen with { FamilyInstruction = "Изменено без нового hash." }));
            Throws(() => PromptHierarchy.Compose(frozen with { ConfigHash = null!, RegistryHash = null! }));
        });
        Test("001 hierarchy trigger reference check detects copying the player example", () =>
        {
            const string category = "social.friendship.support";
            var node = System.Text.Json.Nodes.JsonNode.Parse(File.ReadAllText(Path.Combine(AppContext.BaseDirectory, "prompts", "corpus001", "families.v1.json")))!;
            var family = node["families"]!.AsArray().Single(item => item!["id"]!.GetValue<string>() == "friendship_support")!;
            var example = new PromptFamilyExample(category, "Поможешь, а то совсем выдохся?", "Пошли, разберёмся.", "editorial-test-fixture", "EXPERIMENTAL");
            family["examples"] = JsonSerializer.SerializeToNode(new[] { example }, new JsonSerializerOptions { PropertyNamingPolicy = JsonNamingPolicy.CamelCase });
            var request = new V4GenerationRequest { Category = category, Mode = "TRIGGER", Count = 1, Style = new() { Category = category } };
            var snapshot = PromptHierarchy.Parse(node.ToJsonString()).Freeze(request, PromptProfiles.Default, V4Registry.Default);
            var leaf = V4Registry.Default.Categories.Single(c => c.Key == category);
            var entry = new V4Entry("fixture", "TRIGGER", category, leaf.Act, example.User, leaf.Intent, "NONE", "", "", [], [], new(), "NONE", "GENERAL", [], 1, "");
            True(PromptHierarchy.CheckExampleEcho(snapshot, [entry]).Any(issue => issue.Code == "REFERENCE_ECHO_REVIEW"));
            True(!PromptHierarchy.Compose(snapshot).Contains(example.Reply, StringComparison.Ordinal));
        });
        await TestAsync("001 family refinement changes only its own frozen profile and cross-family edits require a copy", async () =>
        {
            using var f = new Fixture(); using var workspace = new WorkspaceStore(Path.Combine(f.Root, "workspace"), f.Module);
            var store = new PromptLabStore(workspace); var handler = new PromptLabWorkflowHandler(); using var lab = new PromptLabSession(store, _ => new HttpClient(handler, false));
            var first = lab.CreateFamilyProfile(new() { Category = "social.friendship.support", Count = 1, Style = new() { Category = "social.friendship.support" } });
            first = store.SaveVersion(first with { Id = "", ParentId = first.Id, Generation = new() { Transport = "LM_NATIVE" }, Refinement = new() { Transport = "LM_NATIVE" } });
            var other = lab.CreateFamilyProfile(new() { Category = "lineage.commerce.bargaining", Count = 1, Style = new() { Category = "lineage.commerce.bargaining" } });
            store.AcceptVersion(first.Id); store.AcceptVersion(other.Id); var otherHash = store.VersionHash(other.Id);
            var generated = await lab.GenerateAsync(first.Id, new(), "", "instance-1", CancellationToken.None); Equal("COMPLETE", generated.State);
            store.SaveFeedback(generated.Id, [new(1, generated.Rows[0].TextHash, "LIKE", "Сохрани конкретный речевой ход.")], "Не добавляй универсальные утешения.");
            var refined = await lab.RefineAsync(generated.Id, new(), "", "instance-1", CancellationToken.None); Equal("COMPLETE", refined.State);
            var proposal = store.GetVersion(refined.ProposedVersionId); Equal(proposal.System, proposal.Scenario.Components!.FamilyInstruction);
            var comparison = await lab.CompareAsync(generated.Id, proposal.Id, new(), "", "instance-1", CancellationToken.None);
            Equal("COMPLETE", comparison.State); True(comparison.EffectiveSystem.Contains(proposal.System, StringComparison.Ordinal));
            True(!comparison.EffectiveSystem.Contains(first.System, StringComparison.Ordinal));
            var edited = lab.PrepareVersion(proposal with { System = "Говори сухо, без универсальных утешений.",
                CategoryInstructions = ["Предложи конкретную помощь, не подменяй её обещаниями."],
                Scenario = proposal.Scenario with { Style = proposal.Scenario.Style with { EmotionalState = "устал" } } });
            Equal(edited.System, edited.Scenario.Components!.FamilyInstruction);
            Equal(edited.CategoryInstructions[0], edited.Scenario.Components.CategoryInstruction);
            edited.Validate(false, store.EffectiveRegistry());
            var savedEdit = store.SaveVersion(edited with { Id = "", ParentId = proposal.Id, Origin = "MANUAL", ProposedFromRunId = "" });
            var again = await lab.RefineAsync(generated.Id, new(), "", "instance-1", CancellationToken.None, sourceVersionId: savedEdit.Id);
            Equal("COMPLETE", again.State); True(again.EffectiveUser.Contains(savedEdit.System, StringComparison.Ordinal));
            Equal(first.Id, store.GetProfile(first.ProfileId).AcceptedVersionId); Equal(other.Id, store.GetProfile(other.ProfileId).AcceptedVersionId); Equal(otherHash, store.VersionHash(other.Id));
            Throws(() => store.SaveVersion(first with { Id = "", ParentId = first.Id, Scenario = other.Scenario, System = other.System }));
        });
        Test("001 changing category within a family replaces a stale brief and preserves an explicit new override", () =>
        {
            using var f = new Fixture(); using var workspace = new WorkspaceStore(Path.Combine(f.Root, "workspace"), f.Module);
            var store = new PromptLabStore(workspace); using var lab = new PromptLabSession(store);
            var first = lab.CreateFamilyProfile(new() { Category = "social.banter.rough", Style = new() { Category = "social.banter.rough" } });
            var scope = first.Scenario with { Category = "social.banter.double_meanings", Style = first.Scenario.Style with { Category = "social.banter.double_meanings" } };
            var edited = lab.PrepareVersion(first with { Scenario = scope, CategoryInstructions = [first.Scenario.Components!.CategoryInstruction] });
            True(edited.Scenario.Components!.CategoryInstruction != first.Scenario.Components.CategoryInstruction);
            Equal(PromptHierarchy.Default.Freeze(scope with { Components = null }, PromptProfiles.Default, store.EffectiveRegistry()).CategoryInstruction, edited.CategoryInstructions[0]);
            var custom = lab.PrepareVersion(first with { Scenario = scope, CategoryInstructions = ["Дай короткую фразу с двумя прочтениями, без пояснения каламбура."] });
            Equal("Дай короткую фразу с двумя прочтениями, без пояснения каламбура.", custom.Scenario.Components!.CategoryInstruction);
        });
        await TestAsync("001 mixed description splits into independent family profiles with durable shared membership", async () =>
        {
            using var f = new Fixture(); using var workspace = new WorkspaceStore(Path.Combine(f.Root, "workspace"), f.Module);
            var store = new PromptLabStore(workspace); var handler = new PromptLabWorkflowHandler(familyGeneration: true); using var lab = new PromptLabSession(store, _ => new HttpClient(handler, false));
            var source = store.SaveVersion(new() { ReadyForGeneration = false, System = "Исходный общий текст сохраняется как история, но не копируется в каждое семейство.",
                Generation = new() { Transport = "LM_NATIVE" }, Scenario = new PromptLabVersion().Scenario with { Count = 1 },
                AdditionalScenarios = [new() { Category = "lineage.commerce.bargaining", Count = 1, Style = new() { Category = "lineage.commerce.bargaining" } }],
                FamilyPrompts = [new("friendship_support", "Предложи конкретную помощь без нравоучений.", "Ответь на последний ход игрока."),
                    new("commerce_trade", "Делай конкретный встречный торг, учитывай цену.", "Дай самостоятельные варианты ответа в торге.")] });
            var originalHash = store.VersionHash(source.Id);
            var group = lab.SplitFamilyPlan(source.Id); Equal(2, group.VersionIds.Count);
            var children = group.VersionIds.Select(store.GetVersion).ToArray(); Equal(2, children.Select(v => v.ProfileId).Distinct().Count());
            True(children.All(v => v.FamilyGroupId == source.Id && v.ReadyForGeneration));
            Equal(2, children.Select(v => v.Scenario.Components!.FamilyId).Distinct().Count());
            foreach (var child in children) { child.Validate(false, store.EffectiveRegistry()); Equal("", store.GetProfile(child.ProfileId).AcceptedVersionId); }
            Equal(source.FamilyPrompts[0].System, children.Single(v => v.Scenario.Components!.FamilyId == "friendship_support").System);
            Equal(originalHash, store.VersionHash(source.Id)); Equal(group.Id, new PromptLabStore(workspace).GetFamilyGroup(source.Id).Id);
            Equal(3, store.ListProfiles().Count); Equal(group.VersionIds[0], lab.SplitFamilyPlan(source.Id).VersionIds[0]); Equal(3, store.ListProfiles().Count);
            var runs = await lab.GenerateFamilyExperimentAsync(group.VersionIds, new(), "", "instance-1", CancellationToken.None);
            Equal(2, runs.Count); True(runs.All(r => r.State == "COMPLETE")); Equal(1, runs.Select(r => r.ExperimentId).Distinct().Count());
            foreach (var run in runs) True(run.EffectiveSystem.Contains(store.GetVersion(run.VersionId).System, StringComparison.Ordinal));
            Equal(2, handler.Posts); Equal(2, (await lab.ResumeExperimentAsync(runs[0].Id, new(), "", "instance-1", CancellationToken.None)).Count); Equal(2, handler.Posts);
            Throws(() => lab.SplitFamilyPlan(store.SaveVersion(source with { Id = "", ParentId = source.Id,
                FamilyPrompts = [new("pvp_conflict", "Чужое семейство не относится к плану.", "Ответь по ситуации.")] }).Id));
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
            var node = System.Text.Json.Nodes.JsonNode.Parse(body)!;
            node["familyPrompts"] = JsonSerializer.SerializeToNode(new[] { new PromptLabFamilyPrompt("crafting_enchant", "Отреагируй на сломанную заточку без выдуманных потерь.", "Дай разные короткие реакции."),
                new PromptLabFamilyPrompt("work_money", "Усталость после смены: конкретно, без офисных афоризмов.", "Ответь на последний ход.") }, new JsonSerializerOptions { PropertyNamingPolicy = JsonNamingPolicy.CamelCase });
            Equal(2, PromptLabAuthoring.ParseProposal(node.ToJsonString(), V4Registry.Default).FamilyPrompts!.Count);
            node["familyPrompts"]![0]!["familyId"] = "pvp_conflict";
            Throws(() => PromptLabAuthoring.ParseProposal(node.ToJsonString(), V4Registry.Default));
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

    private sealed class PromptLabWorkflowHandler(bool description = false, bool drop = false, bool familyGeneration = false) : HttpMessageHandler
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
                else if (description && Posts == 4 || !description && Posts is 2 or 4) reply = JsonSerializer.Serialize(new { system = "Меняй цели коротких реакций.", user = "Ответь на последний ход.", explanation = "Сохранил краткость." });
                if (familyGeneration)
                {
                    var category = Posts == 1 ? "social.friendship.support" : "lineage.commerce.bargaining";
                    reply = JsonSerializer.Serialize(new { header = new { schema = "pw-semantic-batch/1", category, act = V4Registry.Default.Categories.Single(c => c.Key == category).Act, mode = "REPLY", count = 1, language = "ru" },
                        records = new[] { new { text = Posts == 1 ? "Пошли, разберёмся вместе." : "За восемьдесят отдам, ниже не пойду.", stance = "NEUTRAL", tone = "CALM" } } });
                }
                body = PromptLabSse(reply, !drop);
            }
            return Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK) { Content = new StringContent(body, Encoding.UTF8, type) });
        }
    }
}
