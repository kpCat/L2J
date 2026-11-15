using System.Text.Json;
using System.Text.Json.Serialization;

namespace PhantomSemanticStudio.Core;

[JsonUnmappedMemberHandling(JsonUnmappedMemberHandling.Disallow)]
public sealed record CorpusSampler(string Id = "balanced", double Temperature = .7, double TopP = .90, int TopK = 40, double RepeatPenalty = 1.05, long? Seed = 13001);

[JsonUnmappedMemberHandling(JsonUnmappedMemberHandling.Disallow)]
public sealed record V4GenerationRequest
{
    public string Category { get; init; } = "";
    public string Mode { get; init; } = "REPLY";
    public string RequiredEvent { get; init; } = "";
    public int Count { get; init; } = 6;
    public string Strategy { get; init; } = "situation-first";
    public CorpusSampler Sampler { get; init; } = new();
    public GenerationStyleContext Style { get; init; } = new();
    public string Transport { get; init; } = "OPENAI_COMPAT";
    public string Reasoning { get; init; } = "default";
    public string CompatibleThinkingControl { get; init; } = "none";
    public int MaxOutputTokens { get; init; } = 1536;
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public double? MinP { get; init; }
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? DesiredStance { get; init; }
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? DesiredTone { get; init; }
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? ProfanityLimit { get; init; }
    public bool QaOnly { get; init; }
    public void Validate(PromptProfiles profiles, V4Registry? registry = null)
    {
        registry ??= V4Registry.Default;
        V4Rules.Require(registry.Categories.Any(c => c.Key == Category) && V4Registry.Modes.Contains(Mode)
            && Count is >= 1 and <= 20 && PromptProfiles.Strategies.Contains(Strategy) && Sampler != null && Style != null
            && Transport is "OPENAI_COMPAT" or "LM_NATIVE" && Reasoning is "default" or "off" or "on"
            && CompatibleThinkingControl is "none" or "reasoning_effort" or "chat_template_kwargs" && MaxOutputTokens is >= 128 and <= 8192, "GENERATION_REQUEST");
        var sampler = Sampler!;
        V4Rules.Require(!MinP.HasValue || Transport == "LM_NATIVE" && double.IsFinite(MinP.Value) && MinP.Value is >= 0 and <= 1, "MIN_P_TRANSPORT_OR_RANGE");
        V4Rules.Require(double.IsFinite(sampler.Temperature) && sampler.Temperature is >= 0 and <= 1 && double.IsFinite(sampler.TopP) && sampler.TopP is > 0 and <= 1
            && sampler.TopK is >= 1 and <= 1000 && double.IsFinite(sampler.RepeatPenalty) && sampler.RepeatPenalty is >= 1 and <= 1.5, "SAMPLER");
        V4Rules.Require((DesiredStance == null || Mode != "TRIGGER" && V4Registry.Stances.Contains(DesiredStance))
            && (DesiredTone == null || Mode != "TRIGGER" && V4Registry.Tones.Contains(DesiredTone))
            && (ProfanityLimit == null || V4Registry.Profanities.Contains(ProfanityLimit)), "REQUEST_METADATA_FILTER");
        profiles.Validate(Style!, registry); V4Rules.Require(Style!.Category == Category, "STYLE_CATEGORY_MISMATCH");
        V4Rules.Require(QaOnly || Style.SpeakerSex == "ANY" && Style.AddresseeSex == "ANY" && Style.SpeakerVirginity == "UNKNOWN", "V4_GENDER_OR_PERSONAL_FACT_FILTER_UNAVAILABLE");
        V4Rules.Require(Mode == "INITIATIVE" ? registry.Events.Any(e => e.Code == RequiredEvent) && InitiativeCompatible(Category, RequiredEvent) : RequiredEvent == "", "INITIATIVE_EVENT_SCOPE");
    }
    internal static bool InitiativeCompatible(string category, string ev) => ev == "pvp.help.received" ? category == "social.friendship.support"
        : ev is "pvp.attack.received" or "pvp.death.suffered" or "pvp.kill.caused" && category == "lineage.pvp.trash_talk";
}

public static class V4CorpusGeneration
{
    private static readonly JsonSerializerOptions WireJson = new() { Encoder = System.Text.Encodings.Web.JavaScriptEncoder.UnsafeRelaxedJsonEscaping };
    public static IReadOnlyList<CorpusSampler> LoadSamplers(string directory)
    {
        var samplers = JsonSerializer.Deserialize<CorpusSampler[]>(V4Rules.ReadBytes(PathSafety.ResolveRelative(directory, "samplers.v1.json"), 64 * 1024), PromptProfiles.ConfigJson)
            ?? throw new InvalidDataException("SAMPLERS_EMPTY");
        V4Rules.Require(samplers.Length is >= 2 and <= 3 && samplers.Select(s => s.Id).Distinct().Count() == samplers.Length, "SAMPLER_GRID");
        return Array.AsReadOnly(samplers);
    }
    public static IReadOnlyList<CorpusPilotCase> LoadPilotCases(string directory, PromptProfiles profiles, bool ownerMaturePilotConsent = false, bool intimateOnly = false)
    {
        var fixture = JsonSerializer.Deserialize<PilotFixture>(V4Rules.ReadBytes(PathSafety.ResolveRelative(directory, "pilot-cases.v1.json"), 128 * 1024), PromptProfiles.ConfigJson)
            ?? throw new InvalidDataException("PILOT_EMPTY");
        V4Rules.Require(fixture.Version == 1 && fixture.Synthetic && fixture.Scenes.Length == 12 && fixture.Initiatives.Length == 4, "PILOT_FIXTURE");
        var cases = new List<CorpusPilotCase>();
        V4Rules.Require(!intimateOnly || ownerMaturePilotConsent && fixture.IntimateScenes is { Length: 6 }, "INTIMATE_QA_CONSENT_OR_FIXTURE");
        foreach (var scene in intimateOnly ? fixture.IntimateScenes! : fixture.Scenes)
        {
            V4Rules.Require(V4Rules.Tag(scene.Id) && scene.Voices.Length == 2 && scene.Dialogue.Length is >= 2 and <= 6, "PILOT_SCENE");
            GenerationStyleContext Style(string voice) => new() { StyleId = scene.StyleId, Category = scene.Category, Voice = voice, Relation = scene.Relation, Channel = scene.Channel,
                StrongProfanityRequested = scene.Strong, MatureRequested = scene.Mature && ownerMaturePilotConsent, OwnerMatureOptIn = ownerMaturePilotConsent,
                SpeakerSex = scene.SpeakerSex, AddresseeSex = scene.AddresseeSex,
                SpeakerVirginity = scene.SpeakerVirginity,
                AdultsConsenting = scene.AdultsConsenting, Situation = scene.Mature && !ownerMaturePilotConsent ? "GENERAL: только лёгкий флирт без сексуального подтекста. " + scene.Dialogue[^1] : scene.Situation, Dialogue = scene.Dialogue };
            foreach (var voice in scene.Voices) cases.Add(new(scene.Id + ".reply." + voice, new() { Category = scene.Category, Style = Style(voice), QaOnly = intimateOnly }));
            cases.Add(new(scene.Id + ".trigger", new() { Category = scene.Category, Mode = "TRIGGER", Style = Style("ANY"), QaOnly = intimateOnly }));
        }
        foreach (var initiative in intimateOnly ? [] : fixture.Initiatives)
        {
            var source = cases.First(c => c.Id.StartsWith(initiative.SceneId + ".reply.", StringComparison.Ordinal)).Request;
            cases.Add(new(initiative.SceneId + "." + initiative.RequiredEvent, source with { Mode = "INITIATIVE", RequiredEvent = initiative.RequiredEvent,
                Style = source.Style with { Situation = initiative.Situation, Dialogue = [] } }));
        }
        foreach (var item in cases) item.Request.Validate(profiles);
        if (intimateOnly) { V4Rules.Require(cases.Count == 18 && cases.All(c => c.Request.QaOnly && c.Request.Style.MatureRequested), "INTIMATE_QA_COVERAGE"); return cases.AsReadOnly(); }
        V4Rules.Require(cases.Count == 40 && cases.Select(c => c.Id).Distinct().Count() == cases.Count
            && cases.Select(c => c.Request.Category).Distinct().Count() == 12
            && cases.Where(c => c.Request.Mode == "REPLY").Select(c => c.Request.Style.Voice).Order(StringComparer.Ordinal).SequenceEqual(V4Registry.Default.Voices.Select(v => v.Key).Order(StringComparer.Ordinal))
            && cases.Select(c => c.Request.Style.Channel).Distinct().Count() == 6, "PILOT_COVERAGE");
        return cases.AsReadOnly();
    }
    internal static string UserData(V4GenerationRequest request, V4Registry? registry = null)
    {
        var category = (registry ?? V4Registry.Default).Categories.Single(c => c.Key == request.Category);
        var style = JsonSerializer.SerializeToNode(request.Style, WireJson)!.AsObject(); style.Remove(nameof(GenerationStyleContext.Dialogue));
        var dialogue = request.Style.Dialogue.Select(line =>
        {
            var colon = line.IndexOf(':'); var label = colon >= 0 ? line[..colon].Trim() : "Игрок";
            var role = label.StartsWith("Фантом", StringComparison.Ordinal) || label.StartsWith("Героиня", StringComparison.Ordinal) ? "assistant" : "user";
            return new { role, content = colon >= 0 ? line[(colon + 1)..].Trim() : line };
        }).ToArray();
        return JsonSerializer.Serialize(new
        {
            Contract = "pw-semantic-batch/1", category = category.Key, category.Act, category.Intent, request.Mode, request.Count, request.RequiredEvent,
            Style = style, request.QaOnly, NextSpeaker = request.Mode == "TRIGGER" ? "PLAYER" : "PHANTOM", Dialogue = dialogue,
            ExactResponseContract = ResponseSchema(request, registry),
            Instruction = request.Mode == "TRIGGER" ? "Создай самостоятельные входы ИГРОКА с ясной семантикой категории. Runtime распознаёт буквальный текст без этой истории; общий ответ вроде 'давай' не подходит."
                : "Следующим говорит ФАНТОМ. Ответь на последний ход user. Если просят шутку — выдай саму шутку, а не обещание пошутить. Метаданные — строго заданный scope."
        }, WireJson);
    }

    public static string ToJsonl(string content, V4GenerationRequest request, V4Registry? registry = null)
    {
        V4Rules.Require(V4Rules.Utf8.GetByteCount(content) <= V4BatchReader.MaxFileBytes, "CONTENT_BYTES");
        using var doc = JsonDocument.Parse(content, new JsonDocumentOptions { MaxDepth = 8 });
        V4Rules.JsonKeys(doc.RootElement, ["header", "records"], []);
        var header = doc.RootElement.GetProperty("header"); V4Rules.JsonKeys(header, ["schema", "category", "mode", "act", "language", "count"], []);
        var category = (registry ?? V4Registry.Default).Categories.Single(c => c.Key == request.Category);
        V4Rules.Require(V4Rules.String(header, "schema") == "pw-semantic-batch/1" && V4Rules.String(header, "language") == "ru"
            && V4Rules.String(header, "category") == request.Category && V4Rules.String(header, "mode") == request.Mode
            && V4Rules.String(header, "act") == category.Act && header.GetProperty("count").GetInt32() == request.Count, "RESPONSE_HEADER_MISMATCH");
        var records = doc.RootElement.GetProperty("records"); V4Rules.Require(records.ValueKind == JsonValueKind.Array && records.GetArrayLength() == request.Count, "RESPONSE_COUNT");
        foreach (var record in records.EnumerateArray())
        {
            if (request.Mode != "TRIGGER")
            {
                bool Only(string key, string fallback, string expected) => record.TryGetProperty(key, out var values)
                    ? values.ValueKind == JsonValueKind.Array && values.GetArrayLength() == 1 && values[0].GetString() == expected : fallback == expected;
                V4Rules.Require(Only("voices", "ANY", request.Style.Voice), "RESPONSE_VOICE_SCOPE");
                V4Rules.Require(Only("relations", "UNKNOWN", request.Style.Relation), "RESPONSE_RELATION_SCOPE");
                V4Rules.Require(request.Style.MatureRequested || !record.TryGetProperty("maturity", out var maturity) || maturity.GetString() == "GENERAL", "RESPONSE_MATURITY_SCOPE");
                V4Rules.Require(request.Style.StrongProfanityRequested || !record.TryGetProperty("profanity", out var level) || level.GetString() == "NONE", "RESPONSE_PROFANITY_SCOPE");
                V4Rules.Require(request.Mode != "INITIATIVE" || record.TryGetProperty("requiredEvent", out var eventCode) && eventCode.GetString() == request.RequiredEvent, "RESPONSE_EVENT_SCOPE");
            }
            V4Rules.Require(request.DesiredStance == null || record.TryGetProperty("stance", out var stance) && stance.GetString() == request.DesiredStance, "RESPONSE_STANCE_SCOPE");
            V4Rules.Require(request.DesiredTone == null || record.TryGetProperty("tone", out var tone) && tone.GetString() == request.DesiredTone, "RESPONSE_TONE_SCOPE");
            if (request.ProfanityLimit != null && record.TryGetProperty("profanity", out var profanity))
                V4Rules.Require(request.ProfanityLimit == "STRONG" || profanity.GetString() == "NONE" || request.ProfanityLimit == "MILD" && profanity.GetString() == "MILD", "RESPONSE_PROFANITY_SCOPE");
        }
        return JsonSerializer.Serialize(header, WireJson) + "\n" + string.Concat(records.EnumerateArray().Select(r => JsonSerializer.Serialize(r, WireJson) + "\n"));
    }

    internal static object ResponseSchema(V4GenerationRequest request, V4Registry? registry = null)
    {
        var category = (registry ?? V4Registry.Default).Categories.Single(c => c.Key == request.Category);
        static object Choice(params string[] values) => new { type = "string", @enum = values };
        static object ArrayOf(object item) => new { type = "array", minItems = 1, maxItems = 16, items = item };
        var header = new { type = "object", additionalProperties = false, required = new[] { "schema", "category", "mode", "act", "language", "count" },
            properties = new { schema = Choice("pw-semantic-batch/1"), category = Choice(category.Key), mode = Choice(request.Mode), act = Choice(category.Act), language = Choice("ru"), count = new { type = "integer", @enum = new[] { request.Count } } } };
        var fields = new Dictionary<string, object> { ["text"] = new { type = "string", minLength = 1, maxLength = request.Mode == "TRIGGER" ? 1000 : 100 } };
        if (request.Mode == "TRIGGER") { fields["intent"] = Choice(category.Intent); fields["ambiguity"] = Choice(V4Registry.Ambiguities.ToArray()); }
        else
        {
            fields["stance"] = request.DesiredStance == null ? Choice(V4Registry.Stances.ToArray()) : Choice(request.DesiredStance);
            fields["tone"] = request.DesiredTone == null ? Choice(V4Registry.Tones.ToArray()) : Choice(request.DesiredTone);
            fields["relations"] = ArrayOf(Choice(request.Style.Relation)); fields["voices"] = ArrayOf(Choice(request.Style.Voice));
            fields["profanity"] = !request.Style.StrongProfanityRequested || request.ProfanityLimit == "NONE" ? Choice("NONE")
                : request.ProfanityLimit == "MILD" ? Choice("NONE", "MILD") : Choice(V4Registry.Profanities.ToArray());
            fields["maturity"] = request.Style.MatureRequested ? Choice("GENERAL", "MATURE") : Choice("GENERAL");
            fields["weight"] = new { type = "integer", minimum = 1, maximum = 10 };
            if (request.Mode == "INITIATIVE") fields["requiredEvent"] = Choice(request.RequiredEvent);
        }
        fields["tags"] = ArrayOf(new { type = "string", pattern = "^[a-z][a-z0-9_.-]{0,63}$" });
        var record = new { type = "object", additionalProperties = false,
            required = request.Mode == "TRIGGER" ? new[] { "text", "intent" } : request.Mode == "INITIATIVE" ? new[] { "text", "stance", "requiredEvent" } : new[] { "text", "stance" }, properties = fields };
        return new { type = "object", additionalProperties = false, required = new[] { "header", "records" }, properties = new { header, records = new { type = "array", minItems = request.Count, maxItems = request.Count, items = record } } };
    }
}

public sealed record CorpusPilotCase(string Id, V4GenerationRequest Request);
[JsonUnmappedMemberHandling(JsonUnmappedMemberHandling.Disallow)]
internal sealed record PilotScene(string Id, string Category, string[] Voices, string Relation, string Channel, string StyleId, bool Strong, bool Mature, string Situation, string[] Dialogue, bool AdultsConsenting = false, string SpeakerSex = "ANY", string AddresseeSex = "ANY", string SpeakerVirginity = "UNKNOWN");
[JsonUnmappedMemberHandling(JsonUnmappedMemberHandling.Disallow)]
internal sealed record PilotInitiative(string SceneId, string RequiredEvent, string Situation);
[JsonUnmappedMemberHandling(JsonUnmappedMemberHandling.Disallow)]
internal sealed record PilotFixture(int Version, bool Synthetic, PilotScene[] Scenes, PilotInitiative[] Initiatives, PilotScene[]? IntimateScenes = null);

[JsonUnmappedMemberHandling(JsonUnmappedMemberHandling.Disallow)]
public sealed record CorpusPilotJob(string Id, string CaseId, V4GenerationRequest Request);
[JsonUnmappedMemberHandling(JsonUnmappedMemberHandling.Disallow)]
public sealed record CorpusPilotPlan(string Schema, string Kind, string RegistryHash, string ProfileHash, string AssemblyHash, string FixtureHash, string SamplersHash, bool OwnerMaturePilotConsent, CorpusPilotJob[] Jobs);
public sealed record CorpusPilotOutcome(string JobId, string CaseId, V4GenerationRequest Request, string RegistryHash, string ProfileHash, string AssemblyHash,
    DateTimeOffset RecordedAtUtc, CorpusGenerationResult? Response, bool SchemaValid, V4Entry[] Entries, CorpusQualityIssue[] Issues, string FailureCode);
public sealed record CorpusPilotStatus(string Phase, int PlannedRequests, int CompletedRequests, int SchemaValidRequests, int TriageCleanRecords, int ApprovedRecords, string ThinkingStatus);

/// <summary>Owned offline/live prompt laboratory. This is not the 100K campaign or an approval service.</summary>
public sealed class CorpusLab
{
    private readonly string root;
    private readonly string sourceModule;
    private readonly PromptProfiles profiles;
    private static readonly string AssemblyHash = V4Rules.Hash(File.ReadAllBytes(typeof(CorpusLab).Assembly.Location));
    public CorpusLab(string directory, string protectedSource, PromptProfiles profiles)
    {
        root = PathSafety.Canonical(directory); sourceModule = PathSafety.Canonical(protectedSource); this.profiles = profiles;
        Guard();
    }
    private void Guard()
    {
        PathSafety.AssertDisjoint(PathSafety.RepositoryBoundary(sourceModule), root);
        PathSafety.AssertDisjoint(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "PhantomSemanticStudio"), root);
        for (var dir = new DirectoryInfo(AppContext.BaseDirectory); dir != null; dir = dir.Parent)
            if (File.Exists(Path.Combine(dir.FullName, "PhantomSemanticStudio.sln"))) { PathSafety.AssertDisjoint(dir.FullName, root); break; }
        PathSafety.AssertNoReparsePoints(root);
    }
    private string Local(string path) { Guard(); return PathSafety.ResolveRelative(root, path); }
    private FileStream Lock()
    {
        Guard();
        if (Directory.Exists(root))
            V4Rules.Require(Directory.EnumerateFileSystemEntries(root).All(p => new[] { ".lab.lock", "plan.json", "plan.sha256", "frozen", "issued", "results", "candidates", "model-pin.json", "REPORT.md" }.Contains(Path.GetFileName(p))), "LAB_UNOWNED_CONTENT");
        Directory.CreateDirectory(root); return new FileStream(Local(".lab.lock"), FileMode.OpenOrCreate, FileAccess.ReadWrite, FileShare.None);
    }
    private string RegistryHash()
    {
        var path = Path.Combine(sourceModule, "docs", "phantoms", "semantic", "v4");
        string Read(string name) => V4Rules.Utf8.GetString(V4Rules.ReadBytes(PathSafety.ResolveRelative(path, name), 4 * 1024 * 1024));
        var hash = V4Registry.Parse(Read("registry.tsv"), Read("voices.tsv"), Read("events.tsv")).Hash;
        V4Rules.Require(hash == V4Registry.Default.Hash, "REGISTRY_DRIFT"); return hash;
    }
    private static byte[] Serialize<T>(T value) => JsonSerializer.SerializeToUtf8Bytes(value, PromptProfiles.ConfigJson);
    private void Immutable(string path, byte[] data)
    {
        var target = Local(path); Directory.CreateDirectory(Path.GetDirectoryName(target)!);
        if (File.Exists(target)) { V4Rules.Require(V4Rules.ReadBytes(target, 16 * 1024 * 1024).SequenceEqual(data), "IMMUTABLE_ARTIFACT_CONFLICT"); return; }
        var temporary = target + ".partial-" + Guid.NewGuid().ToString("N");
        try { File.WriteAllBytes(temporary, data); File.Move(temporary, target, false); }
        finally { if (File.Exists(temporary)) File.Delete(temporary); }
    }
    private CorpusPilotPlan ReadPlan()
    {
        var planBytes = V4Rules.ReadBytes(Local("plan.json"), 16 * 1024 * 1024);
        V4Rules.Require(V4Rules.Hash(planBytes) == V4Rules.Utf8.GetString(V4Rules.ReadBytes(Local("plan.sha256"), 128)).Trim(), "FROZEN_PLAN_HASH_MISMATCH");
        var plan = JsonSerializer.Deserialize<CorpusPilotPlan>(planBytes, PromptProfiles.ConfigJson) ?? throw new InvalidDataException("PLAN_EMPTY");
        V4Rules.Require(plan.Schema == "pw-corpus-pilot/1" && plan.RegistryHash == RegistryHash() && plan.ProfileHash == profiles.Hash
            && plan.AssemblyHash == AssemblyHash && plan.Jobs.Length is > 0 and <= 400 && plan.Jobs.Select(j => j.Id).Distinct().Count() == plan.Jobs.Length, "PLAN_SOURCE_PROFILE_DRIFT");
        var fixture = V4Rules.ReadBytes(Local("frozen/pilot-cases.v1.json"), 128 * 1024);
        var samplers = V4Rules.ReadBytes(Local("frozen/samplers.v1.json"), 64 * 1024);
        V4Rules.Require(V4Rules.Hash(fixture) == plan.FixtureHash && V4Rules.Hash(samplers) == plan.SamplersHash, "FROZEN_INPUT_DRIFT");
        foreach (var job in plan.Jobs)
        {
            job.Request.Validate(profiles);
            V4Rules.Require(job.Id == JobId(job.CaseId, job.Request, plan.ProfileHash, plan.RegistryHash), "JOB_FINGERPRINT_MISMATCH");
        }
        return plan;
    }
    private static string JobId(string caseId, V4GenerationRequest request, string profileHash, string registryHash)
        => V4Rules.Hash(Serialize(new { caseId, request, profileHash, registryHash }));

    public CorpusPilotPlan CreatePlan(bool thinking = false, string transport = "OPENAI_COMPAT", string reasoning = "default", string compatibleThinkingControl = "none", bool ownerMaturePilotConsent = false, bool intimateHoldout = false, bool thinkingQuality = false, int thinkingBudget = 2048, bool batchSizeProbe = false)
    {
        using var held = Lock(); var registry = RegistryHash();
        V4Rules.Require(!thinking || !intimateHoldout, "PLAN_KIND_CONFLICT");
        V4Rules.Require(!thinkingQuality || !thinking && !intimateHoldout && ownerMaturePilotConsent, "THINKING_QUALITY_CONSENT_OR_KIND");
        V4Rules.Require(thinkingBudget is >= 128 and <= 8192 && (!batchSizeProbe || !thinking && !thinkingQuality && !intimateHoldout), "COMPARATIVE_PLAN_SCOPE");
        var cases = V4CorpusGeneration.LoadPilotCases(profiles.DirectoryPath, profiles, ownerMaturePilotConsent, intimateHoldout); var samplers = V4CorpusGeneration.LoadSamplers(profiles.DirectoryPath);
        var jobs = new List<CorpusPilotJob>();
        void Add(string id, V4GenerationRequest request)
        { request.Validate(profiles); jobs.Add(new(JobId(id, request, profiles.Hash, registry), id, request)); }
        if (batchSizeProbe)
        {
            var budgetCase = cases.First(c => c.Request.Mode == "REPLY" && c.Request.Category == "life.money.budget");
            foreach (var item in new[] { cases[0], budgetCase })
                foreach (var count in new[] { 3, 6, 12 })
                    Add("batch-size." + item.Id + "." + count, item.Request with { Count = count, MaxOutputTokens = 3072,
                        Transport = transport, Reasoning = reasoning, CompatibleThinkingControl = compatibleThinkingControl, Sampler = samplers[0] });
        }
        else if (thinkingQuality)
        {
            var adult = V4CorpusGeneration.LoadPilotCases(profiles.DirectoryPath, profiles, true, true)[0];
            foreach (var item in new[] { cases[0], adult })
                foreach (var mode in new[] { "off", "on" })
                    Add("quality-thinking." + item.Id + "." + mode, item.Request with { Count = 1, MaxOutputTokens = thinkingBudget, Transport = transport, Reasoning = mode, CompatibleThinkingControl = transport == "OPENAI_COMPAT" ? "reasoning_effort" : "none", Sampler = samplers[0] });
        }
        else if (thinking)
        {
            var probe = cases[0].Request with { Count = 1, MaxOutputTokens = 512, Sampler = samplers[0] };
            for (var repeat = 0; repeat < 2; repeat++)
            {
                foreach (var mode in repeat == 0 ? new[] { "off", "on" } : new[] { "on", "off" })
                    Add($"thinking.native.{mode}.{repeat}", probe with { Transport = "LM_NATIVE", Reasoning = mode });
                Add("thinking.compat.default." + repeat, probe);
                foreach (var control in new[] { "reasoning_effort", "chat_template_kwargs" })
                    foreach (var mode in repeat == 0 ? new[] { "off", "on" } : new[] { "on", "off" })
                        Add($"thinking.compat.{control}.{mode}.{repeat}", probe with { Reasoning = mode, CompatibleThinkingControl = control });
            }
        }
        else
        {
            for (var arm = 0; arm < PromptProfiles.Strategies.Count * samplers.Count; arm++)
                for (var i = 0; i < cases.Count; i++)
                    Add(cases[i].Id, cases[i].Request with { Strategy = PromptProfiles.Strategies[(arm + i) % PromptProfiles.Strategies.Count], Sampler = samplers[(arm / PromptProfiles.Strategies.Count + i) % samplers.Count],
                        Transport = transport, Reasoning = reasoning, CompatibleThinkingControl = compatibleThinkingControl });
        }
        var fixture = V4Rules.ReadBytes(PathSafety.ResolveRelative(profiles.DirectoryPath, "pilot-cases.v1.json"), 128 * 1024);
        var samplerBytes = V4Rules.ReadBytes(PathSafety.ResolveRelative(profiles.DirectoryPath, "samplers.v1.json"), 64 * 1024);
        var plan = new CorpusPilotPlan("pw-corpus-pilot/1", batchSizeProbe ? "BATCH_SIZE" : thinkingQuality ? "THINKING_QUALITY" : thinking ? "THINKING" : intimateHoldout ? "INTIMATE_QA" : "PILOT", registry, profiles.Hash, AssemblyHash, V4Rules.Hash(fixture), V4Rules.Hash(samplerBytes), ownerMaturePilotConsent, jobs.ToArray());
        Immutable("frozen/pilot-cases.v1.json", fixture); Immutable("frozen/samplers.v1.json", samplerBytes);
        foreach (var resource in profiles.Resources) Immutable("frozen/prompts/" + resource.Key, V4Rules.Utf8.GetBytes(resource.Value));
        var planBytes = Serialize(plan); Immutable("plan.json", planBytes); Immutable("plan.sha256", V4Rules.Utf8.GetBytes(V4Rules.Hash(planBytes) + "\n")); return plan;
    }

    private CorpusPilotOutcome[] Outcomes(CorpusPilotPlan plan)
    {
        var path = Local("results"); if (!Directory.Exists(path)) return [];
        return Directory.GetFiles(path, "*.json").Order(StringComparer.Ordinal).Select(p =>
        {
            var bytes = V4Rules.ReadBytes(p, 4 * 1024 * 1024);
            V4Rules.Require(V4Rules.Hash(bytes) == V4Rules.Utf8.GetString(V4Rules.ReadBytes(p + ".sha256", 128)).Trim(), "RESULT_HASH_MISMATCH");
            var outcome = JsonSerializer.Deserialize<CorpusPilotOutcome>(bytes, PromptProfiles.ConfigJson) ?? throw new InvalidDataException("RESULT_EMPTY");
            var job = plan.Jobs.SingleOrDefault(j => j.Id == outcome.JobId);
            V4Rules.Require(job != null && outcome.CaseId == job.CaseId && Path.GetFileNameWithoutExtension(p) == job.Id
                && Serialize(outcome.Request).SequenceEqual(Serialize(job.Request)) && outcome.ProfileHash == plan.ProfileHash
                && outcome.RegistryHash == plan.RegistryHash && outcome.AssemblyHash == plan.AssemblyHash, "RESULT_SCOPE_DRIFT");
            return outcome;
        }).ToArray();
    }

    public async Task<CorpusPilotStatus> RunAsync(LmStudioClient client, StudioSettings settings, string apiKey, string exactModel, int maxNewRequests, CancellationToken token, Action<CorpusPilotStatus>? progress = null)
    {
        V4Rules.Require(maxNewRequests is >= 1 and <= 400, "REQUEST_LIMIT");
        using var held = Lock(); var plan = ReadPlan(); var committed = Outcomes(plan).ToList(); var issued = 0;
        foreach (var job in plan.Jobs)
        {
            token.ThrowIfCancellationRequested(); if (committed.Any(o => o.JobId == job.Id)) continue;
            if (issued >= maxNewRequests) break;
            if (File.Exists(Local("issued/" + job.Id + ".json"))) return Status(plan, committed, "INDETERMINATE_REQUEST_REVIEW_REQUIRED");
            V4Rules.Require(RegistryHash() == plan.RegistryHash, "REGISTRY_DRIFT");
            var inventory = await client.ProbeCorpusModelsAsync(settings, apiKey, token).ConfigureAwait(false);
            var selected = inventory.Loaded.Where(m => m.InstanceId == exactModel || m.ModelKey == exactModel).ToArray();
            V4Rules.Require(selected.Length == 1, "EXACT_LOADED_INSTANCE_REQUIRED"); var loaded = selected[0];
            V4Rules.Require(loaded.ContextLength == 65536, "OWNER_CONTEXT_MISMATCH_EXPECTED_65536");
            Immutable("model-pin.json", Serialize(new { settings.Endpoint, loaded.ModelKey, loaded.InstanceId, loaded.ContextLength }));
            Immutable("issued/" + job.Id + ".json", Serialize(new { job, plan.RegistryHash, plan.ProfileHash, ModelInventory = inventory, IssuedAtUtc = DateTimeOffset.UtcNow }));
            CorpusGenerationResult? response = null; var entries = Array.Empty<V4Entry>(); var schemaValid = false; var failure = ""; var issues = Array.Empty<CorpusQualityIssue>();
            try
            {
                response = await client.GenerateCorpusAsync(settings, apiKey, loaded, job.Request, profiles, token).ConfigureAwait(false);
                failure = response.Rejection;
                if (failure.Length == 0)
                {
                    var wire = V4CorpusGeneration.ToJsonl(response.Content, job.Request); var candidatePath = "candidates/" + job.Id + ".jsonl";
                    Immutable(candidatePath, V4Rules.Utf8.GetBytes(wire)); var report = V4BatchReader.Read(Local(candidatePath), V4Registry.Default);
                    schemaValid = report.Success; entries = report.Entries.ToArray();
                    if (!report.Success) failure = string.Join("; ", report.Diagnostics.Select(d => d.Message));
                    var sameArm = committed.Where(o => o.Request.Strategy == job.Request.Strategy && o.Request.Sampler.Id == job.Request.Sampler.Id).SelectMany(o => o.Entries).ToArray();
                    issues = CorpusQuality.Check(entries, job.Request, sameArm).ToArray();
                }
            }
            catch (Exception error) when (error is IOException or InvalidDataException or JsonException or InvalidOperationException or ArgumentException or HttpRequestException or OperationCanceledException)
            {
                failure = error is OperationCanceledException ? token.IsCancellationRequested ? "CANCELLED" : "TIMEOUT" : error is HttpRequestException ? "MODEL_OFFLINE_OR_CONNECTION_ERROR" : error is InvalidDataException ? error.Message : error.GetType().Name;
                if (apiKey.Length > 0) failure = failure.Replace(apiKey, "[REDACTED_API_TOKEN]", StringComparison.Ordinal);
            }
            var outcome = new CorpusPilotOutcome(job.Id, job.CaseId, job.Request, plan.RegistryHash, plan.ProfileHash, plan.AssemblyHash, DateTimeOffset.UtcNow, response, schemaValid, entries, issues, failure);
            var outcomeBytes = Serialize(outcome);
            Immutable("results/" + job.Id + ".json", outcomeBytes);
            Immutable("results/" + job.Id + ".json.sha256", V4Rules.Utf8.GetBytes(V4Rules.Hash(outcomeBytes) + "\n")); committed.Add(outcome); issued++;
            progress?.Invoke(Status(plan, committed, "PILOT_RUNNING"));
            if (failure is "MODEL_OFFLINE_OR_CONNECTION_ERROR" or "TIMEOUT" or "CANCELLED" or "LOST_MODEL") return Status(plan, committed, failure);
        }
        return Status(plan, committed, committed.Count == plan.Jobs.Length ? "PILOT_COMPLETE_EDITORIAL_REVIEW_REQUIRED" : "PILOT_PAUSED");
    }

    private static CorpusPilotStatus Status(CorpusPilotPlan plan, IReadOnlyList<CorpusPilotOutcome> outcomes, string phase)
    {
        string Thinking(string transport, string control)
        {
            var off = outcomes.Where(o => o.Request.Transport == transport && o.Request.CompatibleThinkingControl == control && o.Request.Reasoning == "off").ToArray();
            var on = outcomes.Where(o => o.Request.Transport == transport && o.Request.CompatibleThinkingControl == control && o.Request.Reasoning == "on").ToArray();
            if (off.Length < 2 || on.Length < 2) return "UNKNOWN";
            if (off.All(o => o.FailureCode == "HTTP_400") && on.All(o => o.FailureCode == "HTTP_400")) return "UNSUPPORTED_CANDIDATE";
            if (off.Concat(on).Any(o => o.FailureCode.Length > 0)) return "UNKNOWN";
            if (off.All(o => o.Response?.Telemetry.ReasoningTokens == 0) && on.All(o => o.Response?.Telemetry.ReasoningTokens > 0)) return "THINK_OFF_VERIFIED";
            if (off.All(o => o.Response?.Telemetry.ReasoningTokens > 0) && on.All(o => o.Response?.Telemetry.ReasoningTokens > 0)) return "OFF_IGNORED_OR_UNSUPPORTED";
            return "UNKNOWN";
        }
        var thinking = plan.Kind == "THINKING" ? "native=" + Thinking("LM_NATIVE", "none") + "; compatible_effort=" + Thinking("OPENAI_COMPAT", "reasoning_effort") + "; compatible_template=" + Thinking("OPENAI_COMPAT", "chat_template_kwargs") : "SEE_SEPARATE_THINKING_EXPERIMENT";
        return new(phase, plan.Jobs.Length, outcomes.Count, outcomes.Count(o => o.SchemaValid), outcomes.Where(o => o.SchemaValid && o.Issues.Length == 0).Sum(o => o.Entries.Length), 0, thinking);
    }
    public CorpusPilotStatus GetStatus()
    { using var held = Lock(); var plan = ReadPlan(); var outcomes = Outcomes(plan); return Status(plan, outcomes, outcomes.Length == plan.Jobs.Length ? "PILOT_COMPLETE_EDITORIAL_REVIEW_REQUIRED" : "PILOT_PAUSED"); }
    public string Report()
    {
        using var held = Lock(); var plan = ReadPlan(); var outcomes = Outcomes(plan); var status = Status(plan, outcomes, "REPORT_ONLY");
        var text = new System.Text.StringBuilder("# Реальный пилот Corpus001\n\n");
        text.AppendLine("Это статистика API и автоматического triage, не художественная приёмка и не approved корпус.\n");
        text.AppendLine(JsonSerializer.Serialize(status, PromptProfiles.ConfigJson));
        text.AppendLine("\n| Strategy / sampler / transport / reasoning | Requests | JSON valid | Triage flags | Output tokens | Reasoning tokens | Wall seconds |\n|---|---:|---:|---:|---:|---:|---:|");
        foreach (var group in outcomes.GroupBy(o => $"{o.Request.Strategy}/{o.Request.Sampler.Id}/{o.Request.Transport}/{o.Request.CompatibleThinkingControl}:{o.Request.Reasoning}"))
            text.AppendLine($"| {group.Key} | {group.Count()} | {group.Count(o => o.SchemaValid)} | {group.Sum(o => o.Issues.Length)} | {Sum(group.Select(o => o.Response?.Telemetry.OutputTokens))} | {Sum(group.Select(o => o.Response?.Telemetry.ReasoningTokens))} | {group.Sum(o => o.Response?.Telemetry.WallSeconds ?? 0):F2} |");
        text.AppendLine("\n## Неудачи и причины\n");
        foreach (var group in outcomes.Where(o => o.FailureCode.Length > 0).GroupBy(o => o.FailureCode)) text.AppendLine($"- {group.Key}: {group.Count()}");
        text.AppendLine("\n## Смешанные реальные примеры для редактора\n\nПорядок фиксирован по SHA job/record, не по предпочтению автора. Флаги — эвристики; числовые оценки естественности должен дать редактор.\n");
        foreach (var sample in outcomes.SelectMany(o => o.Entries.Select((e, i) => new { Outcome = o, Entry = e, Index = i + 1 }))
            .OrderBy(x => V4Rules.Hash(V4Rules.Utf8.GetBytes(x.Outcome.JobId + ":" + x.Index)), StringComparer.Ordinal).Take(100))
            text.AppendLine($"- [{sample.Outcome.JobId[..12]}:{sample.Index}] {sample.Entry.Mode} / {sample.Entry.Category} / {string.Join(',', sample.Entry.Voices)} / {string.Join(',', sample.Entry.Relations)} / {sample.Entry.Profanity}/{sample.Entry.Maturity}: «{sample.Entry.Text}». Сцена: {sample.Outcome.Request.Style.Situation} Флаги: {string.Join(',', sample.Outcome.Issues.Where(i => i.Record == sample.Index).Select(i => i.Code))}");
        return text.ToString();
        static string Sum(IEnumerable<long?> values) { var items = values.ToArray(); return items.Any(v => !v.HasValue) ? "UNKNOWN/PARTIAL" : items.Sum(v => v ?? 0).ToString(System.Globalization.CultureInfo.InvariantCulture); }
    }
}
