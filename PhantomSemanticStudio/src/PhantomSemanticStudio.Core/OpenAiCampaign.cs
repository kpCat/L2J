using System.Text.Json;
using System.Text.Json.Serialization;

namespace PhantomSemanticStudio.Core;

public sealed record OpenAiCampaignSettings
{
    public string Model { get; init; } = OpenAiTariff.SuggestedModel;
    public OpenAiTariff? Price { get; init; } = OpenAiTariff.Known(OpenAiTariff.SuggestedModel);
    public bool TariffConfirmed { get; init; }
    public OpenAiBudget Budget { get; init; } = new();
    public int Microbatch { get; init; } = 20;
    public string Format { get; init; } = "JSON";
    public bool MatureOptIn { get; init; }
    public int QualityPercent { get; init; } = 70;

    public void Validate(bool paid = false)
    {
        OpenAiResponsesClient.ValidateModel(Model);
        Budget.Validate();
        V4Rules.Require(Microbatch is >= 5 and <= 20 && Format is "JSON" or "TEXT"
            && QualityPercent is >= 50 and <= 100, "OPENAI_CAMPAIGN_SETTINGS");
        if (Price != null)
        {
            Price.Validate();
            V4Rules.Require(Price.Model == Model, "OPENAI_PRICE_MODEL");
        }
        if (paid) V4Rules.Require(TariffConfirmed && Price != null, "OPENAI_PRICE_REQUIRED");
    }
}

public sealed record OpenAiCampaignPlan(string Id, DateTimeOffset CreatedAtUtc, string RegistryHash, string PromptHash,
    bool Pilot, OpenAiCampaignSettings Settings, IReadOnlyList<OpenAiCampaignLane> Lanes)
{
    public const int BatchSize = 200;
    public const int PilotMaxPosts = 20;
    public int PromptStyleVersion { get; init; } = 2;

    [JsonIgnore]
    public bool Historical => PromptStyleVersion == 2 && PromptHash == OpenAiCampaignPrompts.LegacyVersionHash;

    [JsonIgnore]
    public OpenAiEditorialQuota EditorialQuota => new(
        Lanes.Where(lane => lane.Register == OpenAiCampaignPrompts.General && lane.Mode != "TRIGGER").Sum(lane => lane.Target),
        Lanes.Where(lane => lane.Register == OpenAiCampaignPrompts.Strong).Sum(lane => lane.Target),
        Lanes.Where(lane => lane.Register == OpenAiCampaignPrompts.Mature).Sum(lane => lane.Target),
        Lanes.Where(lane => lane.Mode == "TRIGGER").Sum(lane => lane.Target));

    public int Target => Lanes.Sum(lane => lane.Target);
    public int MinimumPosts => Lanes.Sum(lane =>
    {
        if (Pilot) return 1;
        var division = Math.DivRem(lane.Target, BatchSize, out var tail);
        return division * Ceiling(BatchSize, Settings.Microbatch) + Ceiling(tail, Settings.Microbatch);
    });

    private static int Ceiling(int rows, int divisor) => (rows + divisor - 1) / divisor;
}

public sealed record OpenAiEditorialQuota(int General, int Strong, int Mature, int Triggers);

public sealed record OpenAiGrade(int Score, bool Facts, bool Roles, bool Voice, bool Natural, bool Category,
    bool AdultConsent, bool Distinct, string Note)
{
    public bool Pass => Score >= 2 && Facts && Roles && Voice && Natural && Category && AdultConsent && Distinct;
    internal void Validate() => V4Rules.Require(Score is >= 0 and <= 3 && Note is { Length: <= 1000 }
        && !Note.Any(c => char.IsControl(c) && c is not ('\r' or '\n' or '\t'))
        && !OpenAiResponsesClient.HasSecret(Note, "\0"), "OPENAI_GRADE_INVALID");
}

public sealed record OpenAiQualityApproval(string PilotId, string PlanHash, string EvidenceHash, string Model, string ResolvedModel,
    string Format, string RegistryHash, string PromptHash, bool MatureOptIn, int QualityPercent,
    DateTimeOffset ApprovedAtUtc, int Reviewed, int Accepted);

public sealed record OpenAiCampaignTotals(long Requests = 0, long Generated = 0, long Valid = 0, long Unique = 0,
    long Reviewed = 0, long Approved = 0, long CompletedBatches = 0, decimal CostUsd = 0, decimal HeldUsd = 0);

public sealed record OpenAiQuotaProgress(string Category, string Label, string Family, string Modes, int Target,
    long Generated, long Valid, long Unique, long Reviewed, long Approved);

public sealed record OpenAiCampaignView(string State, string Detail, OpenAiCampaignTotals Totals,
    IReadOnlyList<OpenAiQuotaProgress> Quotas, string CurrentLane = "", int CurrentBatch = 0,
    int InBatch = 0, int BatchTarget = 0, decimal ForecastUsd = 0, decimal LocalAvailableUsd = 0);

public sealed record OpenAiSample(string Id, int Number, string Category, string Mode, string Scene, bool Holdout, string Text, string State,
    string Diagnostics, OpenAiGrade? Grade, string Format, string Model, string PromptHash, string SceneHash,
    OpenAiTokenUsage? Usage, decimal? RequestCostUsd, string JsonLine);

public sealed record OpenAiCampaignRequestLog(string Id, string Lane, int Batch, int Requested, string State,
    string PromptHash, string SceneHash, string Model, OpenAiTokenUsage? Usage, decimal ReservedUsd, decimal? CostUsd, DateTimeOffset AtUtc);

public sealed partial class CorpusCampaign
{
    public OpenAiCampaignSettings OpenAiSettings() => store.LoadOpenAiSettings();

    public void SaveOpenAiSettings(OpenAiCampaignSettings settings)
    {
        settings.Validate();
        store.SaveOpenAiSettings(settings);
    }

    public OpenAiCampaignPlan PrepareOpenAi(OpenAiCampaignSettings settings, int total,
        IReadOnlyDictionary<string, int>? categoryTargets = null, bool pilot = false)
    {
        settings.Validate();
        var lanes = pilot ? OpenAiCampaignPrompts.EditorialPilotLanes(settings.MatureOptIn)
            : OpenAiCampaignPrompts.EditorialLanes(total, categoryTargets, settings.MatureOptIn);
        var plan = new OpenAiCampaignPlan(Guid.NewGuid().ToString("N"), DateTimeOffset.UtcNow, V4Registry.Default.Hash,
            OpenAiCampaignPrompts.VersionHash, pilot, settings, lanes) { PromptStyleVersion = OpenAiCampaignPrompts.EditorialVersion };
        store.SaveOpenAiPlan(plan);
        return plan;
    }

    public IReadOnlyList<OpenAiCampaignPlan> OpenAiHistory() => store.ListOpenAiPlans();

    public OpenAiCampaignPlan LoadOpenAi(string id)
    {
        var plan = store.ReadOpenAiPlan(id);
        V4Rules.Require(plan.RegistryHash == V4Registry.Default.Hash &&
            (plan.Historical && plan.Lanes.All(lane => lane.Register == null)
                || plan.PromptStyleVersion == OpenAiCampaignPrompts.EditorialVersion && plan.PromptHash == OpenAiCampaignPrompts.VersionHash
                && plan.Lanes.All(lane => lane.Register is OpenAiCampaignPrompts.General or OpenAiCampaignPrompts.Strong or OpenAiCampaignPrompts.Mature)),
            "OPENAI_PLAN_DRIFT");
        return plan;
    }

    internal OpenAiCampaignPlan CheckOpenAi(string id, string expectedHash)
    {
        var plan = LoadOpenAi(id);
        V4Rules.Require(OpenAiHash(id) == expectedHash, "OPENAI_PLAN_DRIFT");
        RequireCurrent(plan);
        plan.Settings.Validate();
        return plan;
    }

    public string OpenAiHash(string id) => store.OpenAiPlanHash(id);

    private CorpusCampaignJournal OpenAiJournal(string id) => new(store.OwnerWorkspace, id, OpenAiHash(id), LoadOpenAi(id).Historical);

    private static void RequireCurrent(OpenAiCampaignPlan plan) => V4Rules.Require(!plan.Historical, "OPENAI_HISTORICAL_READ_ONLY");

    public OpenAiCampaignView OpenAiStatus(string id)
    {
        var plan = LoadOpenAi(id);
        if (plan.Historical && !File.Exists(CorpusCampaignControl.PathFor(store.OwnerWorkspace.Root, store.ProtectedSource, id, OpenAiHash(id))))
            return new("HISTORICAL", "Архив промптов v2. Журнал запросов отсутствует.", new(), []);
        var status = OpenAiJournal(id).OpenAiView(plan);
        if (plan.Historical)
            return status with { State = "HISTORICAL", Detail = "Архив промптов v2 доступен для просмотра и редакционного экспорта." };
        var used = new OpenAiSpendLedger(store.OwnerWorkspace).Spent(id, DateTimeOffset.UtcNow);
        var remaining = plan.Settings.Budget.Remaining(plan.Pilot, used.Campaign, used.Campaign, used.Daily, used.Account, used.Monthly);
        return status with { LocalAvailableUsd = remaining };
    }

    public void RequestOpenAiControl(string id, string command)
    {
        RequireCurrent(LoadOpenAi(id));
        _ = OpenAiJournal(id);
        _ = CorpusCampaignControl.Request(store.OwnerWorkspace.Root, store.ProtectedSource, id, OpenAiHash(id), command);
    }

    public IReadOnlyList<OpenAiCampaignRequestLog> OpenAiLog(string id, int limit = 120) =>
        OpenAiJournal(id).OpenAiLog(Math.Clamp(limit, 1, 300));

    public IReadOnlyList<OpenAiSample> OpenAiSamples(string id, int page = 0, bool reveal = false)
    {
        var plan = LoadOpenAi(id);
        var journal = OpenAiJournal(id);
        var rows = journal.OpenAiSamples(plan, page);
        return !plan.Pilot || reveal ? rows : rows.Select(row => row with
        { Format = "", Model = "", PromptHash = "", SceneHash = "", Usage = null, RequestCostUsd = null, JsonLine = "" }).ToArray();
    }

    public void GradeOpenAi(string id, string task, int row, OpenAiGrade grade)
    {
        RequireCurrent(LoadOpenAi(id));
        grade.Validate();
        OpenAiJournal(id).OpenAiGrade(task, row, grade);
    }

    public void ApproveOpenAiRow(string id, string task, int row, bool confirmed)
    {
        var plan = LoadOpenAi(id);
        RequireCurrent(plan);
        V4Rules.Require(confirmed && !plan.Pilot, "OPENAI_MANUAL_ROW_APPROVAL_REQUIRED");
        OpenAiJournal(id).ApproveOpenAiRow(task, row);
    }

    public OpenAiQualityApproval ApproveOpenAiQuality(string id, string format, int threshold, bool confirmed)
    {
        V4Rules.Require(confirmed && format is "JSON" or "TEXT" && threshold is >= 50 and <= 100,
            "OPENAI_OWNER_QUALITY_APPROVAL_REQUIRED");
        var plan = CheckOpenAi(id, OpenAiHash(id));
        V4Rules.Require(plan.Pilot, "OPENAI_PILOT_REQUIRED");
        var evidence = CheckOpenAiEvidence(plan, format, threshold);
        var resolved = OpenAiJournal(id).OpenAiResolvedModel();
        V4Rules.Require(resolved.Length != 0, "OPENAI_CALIBRATION_MODEL_UNOBSERVED");
        var accepted = new OpenAiQualityApproval(id, OpenAiHash(id), evidence.Hash, plan.Settings.Model, resolved,
            format, plan.RegistryHash, plan.PromptHash, plan.Settings.MatureOptIn, threshold, DateTimeOffset.UtcNow,
            evidence.Reviewed, evidence.Accepted);
        store.SaveOpenAiApproval(accepted);
        return accepted;
    }

    public bool IsOpenAiQualityAccepted(string id)
    {
        var plan = LoadOpenAi(id);
        if (plan.Pilot || plan.Historical) return false;
        try { RequireOpenAiApproval(plan); return true; }
        catch (Exception error) when (error is IOException or InvalidDataException) { return false; }
    }

    internal OpenAiQualityApproval RequireOpenAiApproval(OpenAiCampaignPlan plan)
    {
        var approval = store.ReadOpenAiApproval();
        V4Rules.Require(approval != null && !string.IsNullOrEmpty(approval.ResolvedModel) && approval.Model == plan.Settings.Model
            && approval.Format == plan.Settings.Format
            && approval.MatureOptIn == plan.Settings.MatureOptIn && approval.QualityPercent == plan.Settings.QualityPercent
            && approval.RegistryHash == plan.RegistryHash && approval.PromptHash == plan.PromptHash, "OPENAI_BULK_LOCKED");
        var pilot = CheckOpenAi(approval!.PilotId, approval.PlanHash);
        V4Rules.Require(pilot.Pilot && CheckOpenAiEvidence(pilot, approval.Format, approval.QualityPercent).Hash == approval.EvidenceHash,
            "OPENAI_QUALITY_EVIDENCE_DRIFT");
        return approval;
    }

    private (string Hash, int Reviewed, int Accepted) CheckOpenAiEvidence(OpenAiCampaignPlan pilot, string format, int threshold)
    {
        var journal = OpenAiJournal(pilot.Id);
        V4Rules.Require(journal.Status().State == "CALIBRATION_COMPLETE", "OPENAI_CALIBRATION_INCOMPLETE");
        var grades = journal.OpenAiSamples(pilot, 0).Where(row => row.Grade != null).ToArray();
        V4Rules.Require(grades.Length >= 30 && grades.Select(row => row.Format).Distinct().Count() == 2,
            "OPENAI_NEED_30_BLIND_GRADES");
        var selected = grades.Where(row => row.Format == format).ToArray();
        var accepted = selected.Count(row => row.Grade!.Pass && row.State != "REJECTED");
        var acceptedHoldout = selected.Where(row => row.Holdout && row.Grade!.Pass && row.State != "REJECTED");
        V4Rules.Require(selected.Length >= 15 && selected.Where(row => row.Holdout).Select(row => row.SceneHash).Distinct().Count() >= 2
            && acceptedHoldout.Select(row => row.SceneHash).Distinct().Count() >= 2
            && (!pilot.Settings.MatureOptIn || acceptedHoldout.Any(row => pilot.Lanes.Any(lane =>
                lane.Holdout && lane.Register == OpenAiCampaignPrompts.Mature && lane.Category == row.Category && lane.Scene == row.Scene)))
            && accepted * 100 >= threshold * selected.Length && selected.All(row => row.Grade!.Facts && row.Grade.Roles),
            "OPENAI_QUALITY_BELOW_THRESHOLD");
        var evidence = V4Rules.Hash(JsonSerializer.SerializeToUtf8Bytes(grades, PromptProfiles.CompactJson));
        return (evidence, grades.Length, accepted);
    }
}

public sealed partial class PromptLabStore
{
    internal WorkspaceStore OwnerWorkspace => workspace;
    private const string OpenAiSettingsName = "openai-settings.json";
    private const string OpenAiApprovalName = "openai-quality-approval.json";

    internal OpenAiCampaignSettings LoadOpenAiSettings() => File.Exists(Local(OpenAiSettingsName))
        ? Read<OpenAiCampaignSettings>(OpenAiSettingsName, "OPENAI_CAMPAIGN_SETTINGS") : new();

    internal void SaveOpenAiSettings(OpenAiCampaignSettings settings) =>
        Write(OpenAiSettingsName, "OPENAI_CAMPAIGN_SETTINGS", settings, true);

    private static string OpenAiPlanName(string id)
    {
        RequireId(id);
        return "campaigns/" + id + "/plan.json";
    }

    internal void SaveOpenAiPlan(OpenAiCampaignPlan plan) => Write(OpenAiPlanName(plan.Id), "CORPUS_CAMPAIGN_OPENAI_PLAN", plan, false);
    internal OpenAiCampaignPlan ReadOpenAiPlan(string id) => Read<OpenAiCampaignPlan>(OpenAiPlanName(id), "CORPUS_CAMPAIGN_OPENAI_PLAN");
    internal string OpenAiPlanHash(string id) => V4Rules.Hash(CompactData(OpenAiPlanName(id), "CORPUS_CAMPAIGN_OPENAI_PLAN"));

    internal IReadOnlyList<OpenAiCampaignPlan> ListOpenAiPlans()
    {
        var directory = Local("campaigns");
        if (!Directory.Exists(directory)) return [];
        var plans = new List<OpenAiCampaignPlan>();
        foreach (var child in Directory.EnumerateDirectories(directory))
        {
            var id = Path.GetFileName(child);
            if (!IsId(id)) continue;
            try { plans.Add(ReadOpenAiPlan(id)); }
            catch (Exception error) when (error is FileNotFoundException or InvalidDataException or JsonException) { }
        }
        return plans.OrderByDescending(plan => plan.CreatedAtUtc).ToArray();
    }

    internal void SaveOpenAiApproval(OpenAiQualityApproval approval) =>
        Write(OpenAiApprovalName, "OPENAI_QUALITY_APPROVAL", approval, true);

    internal OpenAiQualityApproval? ReadOpenAiApproval() => File.Exists(Local(OpenAiApprovalName))
        ? Read<OpenAiQualityApproval>(OpenAiApprovalName, "OPENAI_QUALITY_APPROVAL") : null;
}
