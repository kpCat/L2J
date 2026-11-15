using System.Text.Json.Serialization;

namespace PhantomSemanticStudio.Core;

[JsonUnmappedMemberHandling(JsonUnmappedMemberHandling.Disallow)]
public sealed record PromptLabApiOptions
{
    public string Transport { get; init; } = "OPENAI_COMPAT";
    public string Reasoning { get; init; } = "off";
    public double Temperature { get; init; } = .7;
    public double TopP { get; init; } = .9;
    public int TopK { get; init; } = 40;
    public double? MinP { get; init; }
    public double RepeatPenalty { get; init; } = 1.05;
    public int MaxOutputTokens { get; init; } = 1536;
    public bool Stream { get; init; } = true;
    public int ConnectionTimeoutSeconds { get; init; } = 20;
    public int IdleTimeoutSeconds { get; init; } = 90;
    public int OverallTimeoutSeconds { get; init; } = 300;

    public void Validate()
    {
        V4Rules.Require(Transport is "OPENAI_COMPAT" or "LM_NATIVE" && Reasoning is "off" or "on" or "auto", "PROMPT_LAB_API_MODE");
        V4Rules.Require(double.IsFinite(Temperature) && Temperature is >= 0 and <= 1 && double.IsFinite(TopP) && TopP is > 0 and <= 1
            && TopK is >= 1 and <= 1000 && double.IsFinite(RepeatPenalty) && RepeatPenalty is >= 1 and <= 1.5
            && MaxOutputTokens is >= 128 and <= 8192, "PROMPT_LAB_SAMPLER");
        V4Rules.Require(!MinP.HasValue || Transport == "LM_NATIVE" && double.IsFinite(MinP.Value) && MinP.Value is >= 0 and <= 1, "PROMPT_LAB_MIN_P_NOT_SUPPORTED_BY_SELECTED_TRANSPORT");
        V4Rules.Require(ConnectionTimeoutSeconds is >= 1 and <= 120 && IdleTimeoutSeconds is >= 5 and <= 600
            && OverallTimeoutSeconds is >= 15 and <= 600 && ConnectionTimeoutSeconds <= OverallTimeoutSeconds
            && IdleTimeoutSeconds <= OverallTimeoutSeconds, "PROMPT_LAB_TIMEOUTS");
    }

    public V4GenerationRequest Apply(V4GenerationRequest scenario) => scenario with
    {
        Transport = Transport, Reasoning = Reasoning == "auto" ? "default" : Reasoning,
        CompatibleThinkingControl = Transport == "OPENAI_COMPAT" && Reasoning != "auto" ? "reasoning_effort" : "none",
        MaxOutputTokens = MaxOutputTokens, MinP = MinP,
        Sampler = new("prompt-lab", Temperature, TopP, TopK, RepeatPenalty, null)
    };
}

[JsonUnmappedMemberHandling(JsonUnmappedMemberHandling.Disallow)]
public sealed record PromptLabVersion
{
    public string Id { get; init; } = "";
    public string ProfileId { get; init; } = "";
    public string ParentId { get; init; } = "";
    public string Name { get; init; } = "Новый профиль";
    public string System { get; init; } = "Пиши живые русские игровые реплики с учётом ситуации и характера.";
    public string User { get; init; } = "Ответь на последний ход собеседника.";
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? ContextPresentation { get; init; }
    public string RefinerSystem { get; init; } = "Предложи редакцию промпта по фактическим репликам и отзывам редактора. Не принимай её автоматически.";
    public string Origin { get; init; } = "MANUAL";
    public string ProposedFromRunId { get; init; } = "";
    public DateTimeOffset CreatedAtUtc { get; init; }
    public PromptLabApiOptions Generation { get; init; } = new();
    public PromptLabApiOptions Refinement { get; init; } = new() { MaxOutputTokens = 4096 };
    public V4GenerationRequest Scenario { get; init; } = new()
    {
        Category = "social.friendship.support", Style = new() { Category = "social.friendship.support" }
    };
    public IReadOnlyList<V4GenerationRequest> AdditionalScenarios { get; init; } = [];
    public IReadOnlyList<string> CategoryInstructions { get; init; } = [];
    public IReadOnlyList<PromptLabFamilyPrompt> FamilyPrompts { get; init; } = [];
    public string FamilyGroupId { get; init; } = "";
    public bool ReadyForGeneration { get; init; } = true;
    public IReadOnlyList<PromptLabTaxonomySuggestion> CategorySuggestions { get; init; } = [];
    public string ProposalNote { get; init; } = "";
    public string Description { get; init; } = "";

    public void Validate(bool stored, V4Registry? registry = null)
    {
        if (Id == null || ProfileId == null || ParentId == null || System == null || User == null || RefinerSystem == null)
            throw new InvalidDataException("PROMPT_LAB_NULL_FIELDS");
        if (stored) V4Rules.Require(PromptLabStore.IsId(Id) && PromptLabStore.IsId(ProfileId) && CreatedAtUtc != default, "PROMPT_LAB_VERSION_ID");
        V4Rules.Require(ParentId.Length == 0 || PromptLabStore.IsId(ParentId), "PROMPT_LAB_PARENT_ID");
        V4Rules.Require(ProposedFromRunId != null && (ProposedFromRunId.Length == 0 || PromptLabStore.IsId(ProposedFromRunId)), "PROMPT_LAB_PROPOSAL_RUN_ID");
        V4Rules.Require(!string.IsNullOrWhiteSpace(Name) && Name.Length <= 120 && !Name.Any(char.IsControl)
            && Origin is "MANUAL" or "MODEL_PROPOSAL" or "COPY", "PROMPT_LAB_VERSION_METADATA");
        CheckPrompt(System, 12 * 1024); CheckPrompt(User, 12 * 1024); CheckPrompt(RefinerSystem, 8 * 1024);
        V4Rules.Require(ContextPresentation is null or "READABLE_SCENE", "PROMPT_LAB_CONTEXT_PRESENTATION");
        V4Rules.Require(V4Rules.Utf8.GetByteCount(System) + V4Rules.Utf8.GetByteCount(User) <= 16 * 1024, "PROMPT_LAB_EDITOR_TEXT_BUDGET");
        V4Rules.Require(Generation != null && Refinement != null && Scenario != null, "PROMPT_LAB_OPTIONS_REQUIRED");
        Generation!.Validate(); Refinement!.Validate();
        V4Rules.Require(AdditionalScenarios != null && AdditionalScenarios.Count <= 15 && AdditionalScenarios.All(s => s != null), "PROMPT_LAB_SCENARIOS");
        V4Rules.Require(CategoryInstructions != null && (CategoryInstructions.Count == 0 || CategoryInstructions.Count == AdditionalScenarios!.Count + 1)
            && CategoryInstructions.All(text => text != null && V4Rules.Utf8.GetByteCount(text) <= 1600), "PROMPT_LAB_CATEGORY_INSTRUCTIONS");
        V4Rules.Require(FamilyGroupId != null && (FamilyGroupId.Length == 0 || PromptLabStore.IsId(FamilyGroupId)), "PROMPT_LAB_FAMILY_GROUP_ID");
        V4Rules.Require(FamilyPrompts != null && FamilyPrompts.Count <= 8, "PROMPT_LAB_FAMILY_PROMPTS");
        foreach (var family in FamilyPrompts!) { V4Rules.Require(family != null, "PROMPT_LAB_FAMILY_PROMPT"); family!.Validate(); }
        V4Rules.Require(FamilyPrompts.Select(f => f.FamilyId).Distinct(StringComparer.Ordinal).Count() == FamilyPrompts.Count, "PROMPT_LAB_FAMILY_PROMPT_COLLISION");
        if (!stored && ReadyForGeneration)
        {
            var scopes = new[] { Scenario! }.Concat(AdditionalScenarios!).ToArray();
            foreach (var scenario in scopes) Generation.Apply(scenario).Validate(PromptProfiles.Default, registry);
            if (scopes.Any(s => s.Components != null))
                V4Rules.Require(scopes.All(s => s.Components != null && s.Components.FamilyId == Scenario!.Components?.FamilyId && s.Components.FamilyInstruction == System),
                    "HIERARCHY_SPLIT_FAMILIES_OR_SYNC_EDITOR_REQUIRED");
        }
    }

    private static void CheckPrompt(string value, int bytes)
    {
        V4Rules.Require(!string.IsNullOrWhiteSpace(value) && V4Rules.Utf8.GetByteCount(value) <= bytes
            && !value.Any(c => char.IsControl(c) && c is not ('\r' or '\n' or '\t')), "PROMPT_LAB_PROMPT_TEXT");
    }
}

[JsonUnmappedMemberHandling(JsonUnmappedMemberHandling.Disallow)]
public sealed record PromptLabProfile(string Id, string Name, string SelectedVersionId, string AcceptedVersionId, DateTimeOffset UpdatedAtUtc);

[JsonUnmappedMemberHandling(JsonUnmappedMemberHandling.Disallow)]
public sealed record PromptLabRow(int Number, string Text, string TextHash, bool SchemaValid, string[] Diagnostics,
    string Profanity = "UNKNOWN", string Maturity = "UNKNOWN", string Stance = "", string Tone = "");

[JsonUnmappedMemberHandling(JsonUnmappedMemberHandling.Disallow)]
public sealed record PromptLabRating(int Number, string TextHash, string Vote, string Comment);

[JsonUnmappedMemberHandling(JsonUnmappedMemberHandling.Disallow)]
public sealed record PromptLabFeedback(string Id, string RunId, string RunHash, DateTimeOffset CreatedAtUtc,
    IReadOnlyList<PromptLabRating> Ratings, string BatchComment);

[JsonUnmappedMemberHandling(JsonUnmappedMemberHandling.Disallow)]
public sealed record PromptLabRun
{
    public string Id { get; init; } = "";
    public string VersionId { get; init; } = "";
    public string VersionHash { get; init; } = "";
    public string BaselineRunId { get; init; } = "";
    public string ExperimentId { get; init; } = "";
    public PromptLabApiOptions? ApiOptions { get; init; }
    public V4GenerationRequest? Scenario { get; init; }
    public string InputContext { get; init; } = "";
    public string RegistryHash { get; init; } = "";
    public string Purpose { get; init; } = "GENERATION";
    public string State { get; init; } = "QUEUED";
    public DateTimeOffset CreatedAtUtc { get; init; }
    public DateTimeOffset? CompletedAtUtc { get; init; }
    public string ModelId { get; init; } = "";
    public int? ModelContext { get; init; }
    public string Endpoint { get; init; } = "";
    public string EffectiveSystem { get; init; } = "";
    public string EffectiveUser { get; init; } = "";
    public string ProposedVersionId { get; init; } = "";
    public string Failure { get; init; } = "";
    public IReadOnlyList<PromptLabRow> Rows { get; init; } = [];
    public CorpusGenerationResult? Response { get; init; }
    public string RawStream { get; init; } = "";
}

public sealed record PromptLabProgress(string Stage, double ElapsedSeconds, long ReceivedBytes, long MessageCharacters, long ReasoningCharacters);
public sealed record PromptLabResponse(CorpusGenerationResult Response, string RawStream, string EffectiveSystem, string EffectiveUser, bool TransportComplete = false);
public sealed record PromptLabRevisionProposal(string System, string User, string Explanation);
public sealed record PromptLabTransportReceipt(PromptLabResponse Reply, string ModelId, int ModelContext, string Endpoint);
public sealed record PromptLabMetrics(int Records, int ExactDuplicates, int NearDuplicatePairs, double TokenDiversity);
