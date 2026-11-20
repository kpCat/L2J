using System.Text.Json;
using System.Text.Json.Serialization;

namespace PhantomSemanticStudio.Core;

[JsonUnmappedMemberHandling(JsonUnmappedMemberHandling.Disallow)]
public sealed record CorpusCampaignQuota(string Id, string ProductionId, int ScenarioIndex, int AcceptedTarget);

[JsonUnmappedMemberHandling(JsonUnmappedMemberHandling.Disallow)]
public sealed record CorpusCampaignSpec
{
    public string Schema { get; init; } = "pw-corpus-campaign-spec/1";
    public string ModelId { get; init; } = "";
    public string Endpoint { get; init; } = "http://127.0.0.1:1234/v1";
    public int MaxRequests { get; init; }
    public IReadOnlyList<CorpusCampaignQuota> Quotas { get; init; } = [];
}

public sealed record CorpusCampaignProfilePin(string ProductionId, string ProductionHash, string VersionId, string VersionHash);
public sealed record CorpusCampaignCoverage(string QuotaId, string ProductionId, int ScenarioIndex, string Category, string Mode,
    string RequiredEvent, string Voice, string Relation, string StyleId, string ProfanityLimit, bool MatureRequested, int BatchCount, int AcceptedTarget);
public sealed record CorpusCampaignPlan(string Id, DateTimeOffset CreatedAtUtc, string Status, int ContextLimit, string RegistryHash, string CoreHash, string PromptResourceHash,
    CorpusCampaignSpec Spec, IReadOnlyList<CorpusCampaignProfilePin> Profiles, IReadOnlyList<CorpusCampaignCoverage> Coverage,
    int AcceptedTarget, int NominalMinimumRequests);

/// <summary>Offline preparation only. A frozen quota plan cannot issue requests, approve records or install a pack.</summary>
public sealed partial class CorpusCampaign(PromptLabStore store)
{
    private const int MaxSpecBytes = 4 * 1024 * 1024;
    private static string CoreHash() => V4Rules.Hash(File.ReadAllBytes(typeof(CorpusCampaign).Assembly.Location));
    private static string Hash<T>(T data) => V4Rules.Hash(JsonSerializer.SerializeToUtf8Bytes(data, PromptProfiles.CompactJson));
    public static CorpusCampaignSpec ReadSpec(string path)
    {
        try { return JsonSerializer.Deserialize<CorpusCampaignSpec>(V4Rules.ReadBytes(PathSafety.Canonical(path), MaxSpecBytes),
            PromptProfiles.ConfigJson) ?? throw new InvalidDataException("CAMPAIGN_SPEC_EMPTY"); }
        catch (JsonException error) { throw new InvalidDataException("CAMPAIGN_SPEC_JSON: " + error.Message, error); }
    }

    private (CorpusCampaignProfilePin[] Pins, CorpusCampaignCoverage[] Coverage, int Target, int Minimum) Validate(CorpusCampaignSpec spec)
    {
        if (spec == null || spec.Schema != "pw-corpus-campaign-spec/1" || spec.Quotas is not { Count: >= 1 and <= 4096 }
            || spec.MaxRequests is < 1 or > 1000000) throw new InvalidDataException("CAMPAIGN_SPEC_SHAPE");
        V4Rules.Require(JsonSerializer.SerializeToUtf8Bytes(spec, PromptProfiles.CompactJson).Length <= MaxSpecBytes, "CAMPAIGN_SPEC_SIZE");
        new StudioSettings { Endpoint = spec.Endpoint, ModelId = spec.ModelId }.Validate();
        var registry = store.EffectiveRegistry();
        V4Rules.Require(registry.Hash == V4Registry.Default.Hash, "CAMPAIGN_CANONICAL_REGISTRY_REQUIRED");
        V4Rules.Require(spec.Quotas.All(q => q != null && q.Id is { Length: >= 1 and <= 64 }
            && q.Id.All(c => char.IsAsciiLetterOrDigit(c) || c is '_' or '-') && PromptLabStore.IsId(q.ProductionId)
            && q.ScenarioIndex is >= 0 and <= 15 && q.AcceptedTarget is >= 1 and <= 1000000)
            && spec.Quotas.Select(q => q.Id).Distinct(StringComparer.Ordinal).Count() == spec.Quotas.Count
            && spec.Quotas.Select(q => (q.ProductionId, q.ScenarioIndex)).Distinct().Count() == spec.Quotas.Count, "CAMPAIGN_QUOTAS");
        var target = spec.Quotas.Sum(q => (long)q.AcceptedTarget);
        V4Rules.Require(target <= 1000000, "CAMPAIGN_TARGET_LIMIT");
        var routing = new PromptLabRouting(store);
        var productions = spec.Quotas.Select(q => q.ProductionId).Distinct(StringComparer.Ordinal)
            .ToDictionary(id => id, routing.ValidateProduction, StringComparer.Ordinal);
        var coverage = new List<CorpusCampaignCoverage>(); long minimum = 0;
        foreach (var quota in spec.Quotas)
        {
            var production = productions[quota.ProductionId]; var version = production.Version;
            var scopes = new[] { version.Scenario }.Concat(version.AdditionalScenarios).ToArray();
            V4Rules.Require(quota.ScenarioIndex < scopes.Length, "CAMPAIGN_SCENARIO_INDEX");
            var scope = scopes[quota.ScenarioIndex]; version.Generation.Apply(scope).Validate(PromptProfiles.Default, registry);
            V4Rules.Require(!scope.QaOnly && scope.Count is >= 1 and <= 20, "CAMPAIGN_PRODUCTION_SCOPE");
            var evidence = production.Evidence.Select(e => store.GetRun(e.RunId)).Where(r => Hash(r.Scenario ?? version.Scenario) == Hash(scope)).ToArray();
            V4Rules.Require(evidence.Length > 0, "CAMPAIGN_REVIEWED_SCENARIO_REQUIRED");
            var prompt = LmStudioClient.ComposePromptLabGeneration(version with { Scenario = scope }, registry);
            V4Rules.Require(evidence.Any(r => r.ApiOptions != null && Hash(r.ApiOptions) == Hash(version.Generation)
                && r.ModelId == spec.ModelId && r.ModelContext == 65536 && r.Endpoint.TrimEnd('/') == spec.Endpoint.TrimEnd('/')
                && r.EffectiveSystem == prompt.System && r.EffectiveUser == prompt.User),
                "CAMPAIGN_REVIEWED_SETTINGS_MODEL_REQUIRED");
            minimum += (quota.AcceptedTarget + (long)scope.Count - 1) / scope.Count;
            coverage.Add(new(quota.Id, quota.ProductionId, quota.ScenarioIndex, scope.Category, scope.Mode, scope.RequiredEvent,
                scope.Style.Voice, scope.Style.Relation, scope.Style.StyleId, scope.ProfanityLimit ?? "UNSPECIFIED", scope.Style.MatureRequested, scope.Count, quota.AcceptedTarget));
        }
        V4Rules.Require(spec.MaxRequests >= minimum, "CAMPAIGN_REQUEST_BUDGET_TOO_SMALL");
        return (productions.Values.OrderBy(p => p.Id, StringComparer.Ordinal).Select(p => new CorpusCampaignProfilePin(p.Id, Hash(p), p.VersionId, p.VersionHash)).ToArray(),
            coverage.ToArray(), (int)target, (int)minimum);
    }

    public CorpusCampaignPlan Prepare(CorpusCampaignSpec spec)
    {
        var checkedPlan = Validate(spec);
        var plan = new CorpusCampaignPlan(Guid.NewGuid().ToString("N"), DateTimeOffset.UtcNow, "PREPARED_UNAPPROVED", 65536,
            store.EffectiveRegistry().Hash, CoreHash(), PromptProfiles.Default.Hash,
            spec with { Quotas = spec.Quotas.ToArray() }, checkedPlan.Pins, checkedPlan.Coverage, checkedPlan.Target, checkedPlan.Minimum);
        store.SaveCampaignPlan(plan); return plan;
    }

    public string PlanHash(string id) => store.CampaignPlanHash(id);

    public CorpusCampaignPlan Check(string id, string expectedHash)
    {
        var plan = store.GetCampaignPlan(id);
        V4Rules.Require(store.CampaignPlanHash(id) == expectedHash, "CAMPAIGN_PLAN_HASH");
        V4Rules.Require(plan.Status == "PREPARED_UNAPPROVED" && plan.ContextLimit == 65536 && plan.CreatedAtUtc != default
            && plan.RegistryHash == store.EffectiveRegistry().Hash && plan.CoreHash == CoreHash()
            && plan.PromptResourceHash == PromptProfiles.Default.Hash, "CAMPAIGN_PLAN_DRIFT");
        var current = Validate(plan.Spec);
        V4Rules.Require(Hash(current.Pins) == Hash(plan.Profiles) && Hash(current.Coverage) == Hash(plan.Coverage)
            && current.Target == plan.AcceptedTarget && current.Minimum == plan.NominalMinimumRequests, "CAMPAIGN_PIN_DRIFT");
        return plan;
    }
}

public sealed partial class PromptLabStore
{
    internal void SaveCampaignPlan(CorpusCampaignPlan plan) { RequireId(plan.Id); Write("campaigns/" + plan.Id + "/plan.json", "CORPUS_CAMPAIGN_PLAN", plan, false); }
    internal CorpusCampaignPlan GetCampaignPlan(string id)
    {
        RequireId(id); var plan = Read<CorpusCampaignPlan>("campaigns/" + id + "/plan.json", "CORPUS_CAMPAIGN_PLAN");
        V4Rules.Require(plan.Id == id, "CAMPAIGN_PLAN_ID"); return plan;
    }
    internal string CampaignPlanHash(string id) { _ = GetCampaignPlan(id); return V4Rules.Hash(CompactData("campaigns/" + id + "/plan.json", "CORPUS_CAMPAIGN_PLAN")); }
}
