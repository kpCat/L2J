using System.Text.Json;
using System.Text.Json.Serialization;

namespace PhantomSemanticStudio.Core;

[JsonUnmappedMemberHandling(JsonUnmappedMemberHandling.Disallow)]
public sealed record CorpusCampaignContext(string Id, string QuotaId, string Situation, IReadOnlyList<string> Dialogue);

[JsonUnmappedMemberHandling(JsonUnmappedMemberHandling.Disallow)]
public sealed record CorpusCampaignPolicySpec
{
    public string Schema { get; init; } = "pw-campaign-policy-spec/1";
    public string CampaignId { get; init; } = "";
    public string PlanHash { get; init; } = "";
    public string Name { get; init; } = "Редакционная политика";
    public string Revision { get; init; } = "1";
    public string Rubric { get; init; } = "pw-campaign-editorial/1";
    public string CriticSystem { get; init; } = "";
    public PromptLabApiOptions Critic { get; init; } = new() { Temperature = .1, TopP = 1, RepeatPenalty = 1, MaxOutputTokens = 4096 };
    public int MaxEditorialRequests { get; init; } = 1000000;
    public IReadOnlyList<CorpusCampaignContext> Contexts { get; init; } = [];
}

public sealed record CorpusCampaignPolicy(string Id, DateTimeOffset CreatedAtUtc, CorpusCampaignPolicySpec Spec);
[JsonUnmappedMemberHandling(JsonUnmappedMemberHandling.Disallow)]
public sealed record CorpusCampaignSampleGrade(string QuotaId, string RunId, int Number, string TextHash,
    int Naturalness, int ContextAccuracy, bool HasCriticalIssue, string Comment);
public sealed record CorpusCampaignSamplePin(CorpusCampaignSampleGrade Grade, string RunHash, string FeedbackHash);
public sealed record CorpusCampaignPolicyApproval(string PolicyId, string PolicyHash, DateTimeOffset ApprovedAtUtc,
    IReadOnlyList<CorpusCampaignSamplePin> Samples);

/// <summary>Explicit owner policy approval. Proposals and model scores cannot call or replace this decision.</summary>
public sealed class CorpusCampaignPolicies(PromptLabStore store)
{
    private static string Hash<T>(T value) => V4Rules.Hash(JsonSerializer.SerializeToUtf8Bytes(value, PromptProfiles.CompactJson));
    private static bool Label(string value) => value is { Length: >= 1 and <= 64 } && value.All(c => char.IsAsciiLetterOrDigit(c) || c is '_' or '-');
    public static CorpusCampaignPolicySpec ReadSpec(string path)
    {
        try { return JsonSerializer.Deserialize<CorpusCampaignPolicySpec>(V4Rules.ReadBytes(PathSafety.Canonical(path), 4 * 1024 * 1024), PromptProfiles.ConfigJson)
            ?? throw new InvalidDataException("CAMPAIGN_POLICY_EMPTY"); }
        catch (JsonException error) { throw new InvalidDataException("CAMPAIGN_POLICY_JSON: " + error.Message, error); }
    }
    public static IReadOnlyList<CorpusCampaignSampleGrade> ReadSample(string path)
    {
        try
        {
            var bytes = V4Rules.ReadBytes(PathSafety.Canonical(path), 4 * 1024 * 1024); using var document = JsonDocument.Parse(bytes);
            V4Rules.Require(document.RootElement.ValueKind == JsonValueKind.Array, "CAMPAIGN_SAMPLE_ARRAY");
            foreach (var row in document.RootElement.EnumerateArray())
                V4Rules.JsonKeys(row, ["QuotaId", "RunId", "Number", "TextHash", "Naturalness", "ContextAccuracy", "HasCriticalIssue", "Comment"], []);
            return JsonSerializer.Deserialize<CorpusCampaignSampleGrade[]>(bytes, PromptProfiles.ConfigJson) ?? throw new InvalidDataException("CAMPAIGN_SAMPLE_EMPTY");
        }
        catch (JsonException error) { throw new InvalidDataException("CAMPAIGN_SAMPLE_JSON: " + error.Message, error); }
    }

    private CorpusCampaignPlan Validate(CorpusCampaignPolicySpec spec)
    {
        V4Rules.Require(spec != null && spec.Schema == "pw-campaign-policy-spec/1" && spec.Rubric == "pw-campaign-editorial/1"
            && spec.Name is { Length: >= 1 and <= 120 } && Label(spec.Revision) && spec.Critic != null && spec.Contexts != null
            && spec.Contexts.Count <= 4096 && spec.MaxEditorialRequests is >= 1 and <= 1000000, "CAMPAIGN_POLICY_SHAPE");
        V4Rules.Require(spec!.CriticSystem is { Length: > 0 } && V4Rules.Utf8.GetByteCount(spec.CriticSystem) <= 8192, "CAMPAIGN_CRITIC_SYSTEM");
        spec.Critic!.Validate(); V4Rules.Require(spec.Critic.MaxOutputTokens >= 512, "CAMPAIGN_CRITIC_OUTPUT_RESERVE");
        V4Rules.Require(JsonSerializer.SerializeToUtf8Bytes(spec, PromptProfiles.CompactJson).Length <= 4 * 1024 * 1024, "CAMPAIGN_POLICY_SIZE");
        var plan = new CorpusCampaign(store).Check(spec.CampaignId, spec.PlanHash);
        var pool = spec.Contexts!;
        V4Rules.Require(pool.All(c => c != null && Label(c.Id) && plan.Spec.Quotas.Any(q => q.Id == c.QuotaId)
            && !string.IsNullOrWhiteSpace(c.Situation) && c.Dialogue != null)
            && pool.Select(c => c.Id).Distinct(StringComparer.Ordinal).Count() == pool.Count, "CAMPAIGN_CONTEXT_IDS");
        foreach (var quota in plan.Spec.Quotas)
        {
            var contexts = pool.Where(c => c.QuotaId == quota.Id).ToArray();
            V4Rules.Require(quota.AcceptedTarget <= 300 || contexts.Length >= 2, "CAMPAIGN_CONTEXT_POOL_REQUIRED");
            V4Rules.Require(contexts.Select(c => Hash(new { Situation = V4Rules.Normalize(c.Situation), Dialogue = c.Dialogue.Select(V4Rules.Normalize).ToArray() }))
                .Distinct(StringComparer.Ordinal).Count() == contexts.Length, "CAMPAIGN_CONTEXT_DUPLICATE");
            var scope = BaseScope(quota);
            foreach (var context in contexts)
            {
                var applied = ApplyContext(scope, context); applied.Validate(PromptProfiles.Default, store.EffectiveRegistry());
                var version = store.GetProduction(quota.ProductionId).Version;
                var prompt = LmStudioClient.ComposePromptLabGeneration(version with { Scenario = applied }, store.EffectiveRegistry());
                LmStudioClient.ValidatePromptLabBudget(prompt.System, prompt.User, prompt.Request, 65536);
            }
        }
        return plan;
    }

    private V4GenerationRequest BaseScope(CorpusCampaignQuota quota)
    {
        var version = store.GetProduction(quota.ProductionId).Version;
        return new[] { version.Scenario }.Concat(version.AdditionalScenarios).ElementAt(quota.ScenarioIndex);
    }
    private static V4GenerationRequest ApplyContext(V4GenerationRequest scope, CorpusCampaignContext context)
        => scope with { Style = scope.Style with { Situation = context.Situation, Dialogue = context.Dialogue.ToArray() } };
    internal bool MatchesScope(CorpusCampaignPolicy policy, CorpusCampaignQuota quota, V4GenerationRequest scope)
    {
        var seed = BaseScope(quota);
        return Hash(scope) == Hash(seed) || policy.Spec.Contexts.Where(c => c.QuotaId == quota.Id).Any(c => Hash(ApplyContext(seed, c)) == Hash(scope));
    }

    public CorpusCampaignPolicy Propose(CorpusCampaignPolicySpec spec)
    {
        if (spec == null) throw new InvalidDataException("CAMPAIGN_POLICY_EMPTY");
        if (spec.CriticSystem == "")
        {
            var path = Path.Combine(Path.GetDirectoryName(typeof(CorpusCampaignPolicies).Assembly.Location)!, "prompts", "corpus001", "editorial.v1.md");
            spec = spec with { CriticSystem = V4Rules.Utf8.GetString(V4Rules.ReadBytes(path, 8192)) };
        }
        _ = Validate(spec);
        var frozen = JsonSerializer.Deserialize<CorpusCampaignPolicySpec>(JsonSerializer.SerializeToUtf8Bytes(spec, PromptProfiles.CompactJson), PromptProfiles.ConfigJson)!;
        var policy = new CorpusCampaignPolicy(Guid.NewGuid().ToString("N"), DateTimeOffset.UtcNow, frozen);
        store.SaveCampaignPolicy(policy); return policy;
    }
    public string PolicyHash(string id) => store.CampaignPolicyHash(id);
    public string ApprovalHash(string id) => store.CampaignApprovalHash(id);
    public CorpusCampaignPolicy Get(string id)
    {
        var policy = store.GetCampaignPolicy(id); V4Rules.Require(policy.Id == id && policy.CreatedAtUtc != default, "CAMPAIGN_POLICY_ID");
        _ = Validate(policy.Spec); return policy;
    }

    public V4GenerationRequest Scope(string policyId, string quotaId, long sequence)
    {
        V4Rules.Require(sequence >= 0, "CAMPAIGN_CONTEXT_SEQUENCE"); var policy = Get(policyId);
        var plan = new CorpusCampaign(store).Check(policy.Spec.CampaignId, policy.Spec.PlanHash);
        var quota = plan.Spec.Quotas.SingleOrDefault(q => q.Id == quotaId) ?? throw new InvalidDataException("CAMPAIGN_CONTEXT_QUOTA");
        var contexts = policy.Spec.Contexts.Where(c => c.QuotaId == quotaId).ToArray(); var slot = (int)(sequence % (contexts.Length + 1));
        return slot == 0 ? BaseScope(quota) : ApplyContext(BaseScope(quota), contexts[slot - 1]);
    }

    private IReadOnlyList<CorpusCampaignSamplePin> ValidateSamples(CorpusCampaignPolicy policy, IReadOnlyList<CorpusCampaignSampleGrade> grades)
    {
        V4Rules.Require(grades is { Count: >= 50 and <= 1000 } && grades.All(g => g != null && PromptLabStore.IsId(g.RunId)
            && g.Number is >= 1 and <= 20 && g.Naturalness is >= 3 and <= 5 && g.ContextAccuracy is >= 3 and <= 5
            && !g.HasCriticalIssue && g.Comment is { Length: <= 2000 })
            && grades.Select(g => (g.RunId, g.Number)).Distinct().Count() == grades.Count, "CAMPAIGN_OWNER_SAMPLE_REQUIRED");
        var plan = new CorpusCampaign(store).Check(policy.Spec.CampaignId, policy.Spec.PlanHash);
        V4Rules.Require(plan.Spec.Quotas.All(q => grades.Any(g => g.QuotaId == q.Id))
            && grades.All(g => plan.Spec.Quotas.Any(q => q.Id == g.QuotaId)), "CAMPAIGN_OWNER_SAMPLE_COVERAGE");
        foreach (var group in grades.GroupBy(g => g.QuotaId))
            V4Rules.Require(group.Average(g => g.Naturalness) >= 4 && group.Average(g => g.ContextAccuracy) >= 4, "CAMPAIGN_OWNER_SAMPLE_QUALITY");
        var pins = new List<CorpusCampaignSamplePin>(); var texts = new HashSet<string>(StringComparer.Ordinal);
        foreach (var grade in grades)
        {
            var quota = plan.Spec.Quotas.Single(q => q.Id == grade.QuotaId); var production = store.GetProduction(quota.ProductionId);
            var run = store.GetRun(grade.RunId); var version = store.GetVersion(run.VersionId); var scope = run.Scenario ?? version.Scenario;
            V4Rules.Require(run.VersionId == production.VersionId && run.VersionHash == production.VersionHash && run.State == "COMPLETE"
                && run.Purpose == "GENERATION" && run.RegistryHash == plan.RegistryHash && MatchesScope(policy, quota, scope)
                && run.ModelId == plan.Spec.ModelId && run.ModelContext == 65536 && run.Endpoint.TrimEnd('/') == plan.Spec.Endpoint.TrimEnd('/')
                && run.ApiOptions != null && Hash(run.ApiOptions) == Hash(version.Generation), "CAMPAIGN_OWNER_SAMPLE_SCOPE");
            var prompt = LmStudioClient.ComposePromptLabGeneration(version with { Scenario = scope }, store.EffectiveRegistry());
            V4Rules.Require(run.EffectiveSystem == prompt.System && run.EffectiveUser == prompt.User, "CAMPAIGN_OWNER_SAMPLE_PROMPT");
            var row = run.Rows.SingleOrDefault(r => r.Number == grade.Number);
            var feedback = store.LatestFeedback(run.Id) ?? throw new InvalidDataException("CAMPAIGN_OWNER_SAMPLE_FEEDBACK");
            V4Rules.Require(row != null && row.SchemaValid && row.TextHash == grade.TextHash && texts.Add(V4Rules.Normalize(row.Text))
                && feedback.Ratings.Any(r => r.Number == grade.Number && r.TextHash == grade.TextHash && r.Vote == "LIKE"), "CAMPAIGN_OWNER_SAMPLE_LIKE_OR_DUPLICATE");
            // Revalidate actual response bytes; serialized row flags alone are not schema proof.
            var wire = V4CorpusGeneration.ToJsonl(run.Response!.Content, version.Generation.Apply(scope), store.EffectiveRegistry());
            using var parsed = JsonDocument.Parse(wire.Split('\n', StringSplitOptions.RemoveEmptyEntries)[grade.Number]);
            V4Rules.Require(parsed.RootElement.GetProperty("text").GetString() == row!.Text, "CAMPAIGN_OWNER_SAMPLE_TEXT");
            pins.Add(new(grade, Hash(run), Hash(feedback)));
        }
        return pins;
    }

    public CorpusCampaignPolicyApproval Approve(string policyId, string expectedPolicyHash, IReadOnlyList<CorpusCampaignSampleGrade> grades, bool confirmed)
    {
        V4Rules.Require(confirmed, "CAMPAIGN_EXPLICIT_OWNER_POLICY_APPROVAL_REQUIRED"); var policy = Get(policyId);
        V4Rules.Require(PolicyHash(policyId) == expectedPolicyHash, "CAMPAIGN_POLICY_HASH");
        var samples = ValidateSamples(policy, grades);
        var approval = new CorpusCampaignPolicyApproval(policyId, expectedPolicyHash, DateTimeOffset.UtcNow, samples);
        store.SaveCampaignApproval(approval); return approval;
    }
    public CorpusCampaignPolicyApproval CheckApproved(string policyId, string expectedApprovalHash)
    {
        var policy = Get(policyId); var approval = store.GetCampaignApproval(policyId);
        V4Rules.Require(ApprovalHash(policyId) == expectedApprovalHash && approval.PolicyId == policyId && approval.PolicyHash == PolicyHash(policyId)
            && approval.ApprovedAtUtc != default && approval.Samples != null, "CAMPAIGN_APPROVAL_HASH");
        var current = ValidateSamples(policy, approval.Samples!.Select(s => s.Grade).ToArray());
        V4Rules.Require(Hash(current) == Hash(approval.Samples), "CAMPAIGN_OWNER_SAMPLE_DRIFT"); return approval;
    }
}

public sealed partial class PromptLabStore
{
    private string CampaignPolicyName(string id, string leaf) { RequireId(id); return "campaign-policies/" + id + "/" + leaf; }
    internal void SaveCampaignPolicy(CorpusCampaignPolicy policy) => Write(CampaignPolicyName(policy.Id, "policy.json"), "CAMPAIGN_POLICY", policy, false);
    internal CorpusCampaignPolicy GetCampaignPolicy(string id) => Read<CorpusCampaignPolicy>(CampaignPolicyName(id, "policy.json"), "CAMPAIGN_POLICY");
    internal string CampaignPolicyHash(string id) => V4Rules.Hash(CompactData(CampaignPolicyName(id, "policy.json"), "CAMPAIGN_POLICY"));
    internal void SaveCampaignApproval(CorpusCampaignPolicyApproval approval) => Write(CampaignPolicyName(approval.PolicyId, "approval.json"), "CAMPAIGN_POLICY_APPROVAL", approval, false);
    internal CorpusCampaignPolicyApproval GetCampaignApproval(string id) => Read<CorpusCampaignPolicyApproval>(CampaignPolicyName(id, "approval.json"), "CAMPAIGN_POLICY_APPROVAL");
    internal string CampaignApprovalHash(string id) => V4Rules.Hash(CompactData(CampaignPolicyName(id, "approval.json"), "CAMPAIGN_POLICY_APPROVAL"));
}
