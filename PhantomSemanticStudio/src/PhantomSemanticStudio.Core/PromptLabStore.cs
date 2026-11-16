using System.Text.Json;

namespace PhantomSemanticStudio.Core;

/// <summary>Owned workspace history. A saved prompt is never a corpus approval or server installation.</summary>
public sealed partial class PromptLabStore
{
    private readonly WorkspaceStore workspace;
    private readonly object sync = new();
    private const int MaxFileBytes = 32 * 1024 * 1024;
    private const string Folder = "labs/prompt-lab/";
    public string Root { get; }
    public string ProtectedSource => workspace.ProtectedRoot;
    public V4Workspace IsolatedV4() => new(Local("v4-workspace"), workspace.ProtectedRoot);
    public V4Registry EffectiveRegistry() => new(IsolatedV4().Categories(), V4Registry.Default.Voices, V4Registry.Default.Events);

    public PromptLabStore(WorkspaceStore workspace)
    {
        this.workspace = workspace;
        workspace.Check(); Root = PathSafety.ResolveRelative(workspace.Root, Folder.TrimEnd('/'));
        Directory.CreateDirectory(Root); PathSafety.AssertNoReparsePoints(Root);
    }

    internal static bool IsId(string value) => value != null && Guid.TryParseExact(value, "N", out _);
    private static void RequireId(string id) => V4Rules.Require(IsId(id), "PROMPT_LAB_ID");
    public string VersionPath(string id) { RequireId(id); return Local("versions/" + id + ".json"); }

    public PromptLabVersion SaveVersion(PromptLabVersion draft)
    {
        lock (sync)
        {
            draft.Validate(false, EffectiveRegistry());
            var parentId = draft.ParentId.Length > 0 ? draft.ParentId : draft.Id;
            var parent = parentId.Length == 0 ? null : GetVersion(parentId);
            var profileId = draft.ProfileId.Length > 0 ? draft.ProfileId : parent?.ProfileId ?? Guid.NewGuid().ToString("N");
            RequireId(profileId);
            V4Rules.Require(parent == null || parent.ProfileId == profileId, "PROMPT_LAB_PARENT_PROFILE_MISMATCH");
            if (parent?.Scenario.Components != null && draft.ReadyForGeneration)
                V4Rules.Require(draft.Scenario.Components?.FamilyId == parent.Scenario.Components.FamilyId, "HIERARCHY_NEW_FAMILY_REQUIRES_PROFILE_COPY");
            if (draft.Origin == "MODEL_PROPOSAL" && draft.ProposedFromRunId.Length > 0)
            {
                var existing = ListVersions(profileId).SingleOrDefault(v => v.Origin == "MODEL_PROPOSAL" && v.ProposedFromRunId == draft.ProposedFromRunId);
                if (existing != null)
                {
                    var expected = draft with { Id = existing.Id, ProfileId = profileId, ParentId = parentId, CreatedAtUtc = existing.CreatedAtUtc };
                    V4Rules.Require(JsonSerializer.Serialize(existing, PromptProfiles.CompactJson) == JsonSerializer.Serialize(expected, PromptProfiles.CompactJson), "PROMPT_LAB_PROPOSAL_SOURCE_DRIFT");
                    return existing;
                }
            }
            var profilePath = "profiles/" + profileId + ".json";
            var previous = File.Exists(Local(profilePath)) ? GetProfile(profileId) : null;
            V4Rules.Require(previous != null || parent == null, "PROMPT_LAB_PROFILE_MISSING");
            var version = draft with { Id = Guid.NewGuid().ToString("N"), ProfileId = profileId, ParentId = parentId, CreatedAtUtc = DateTimeOffset.UtcNow,
                ProposedFromRunId = draft.Origin == "MODEL_PROPOSAL" ? draft.ProposedFromRunId : "" };
            version.Validate(true);
            Write("versions/" + version.Id + ".json", "VERSION", version, false);
            Write(profilePath, "PROFILE", new PromptLabProfile(profileId, version.Name, version.Id, previous?.AcceptedVersionId ?? "", DateTimeOffset.UtcNow), true);
            return version;
        }
    }

    public PromptLabVersion CopyVersion(string id, string name)
    {
        var source = GetVersion(id);
        return SaveVersion(source with { Id = "", ParentId = "", ProfileId = "", Name = name, Origin = "COPY", FamilyGroupId = "" });
    }

    public PromptLabFamilyGroup? FindFamilyGroup(string sourceVersionId)
    {
        RequireId(sourceVersionId);
        return File.Exists(Local("family-groups/" + sourceVersionId + ".json")) ? GetFamilyGroup(sourceVersionId) : null;
    }

    public PromptLabFamilyGroup GetFamilyGroup(string sourceVersionId)
    {
        RequireId(sourceVersionId); var group = Read<PromptLabFamilyGroup>("family-groups/" + sourceVersionId + ".json", "FAMILY_GROUP");
        V4Rules.Require(group.Id == sourceVersionId && group.SourceVersionHash == VersionHash(sourceVersionId) && group.CreatedAtUtc != default
            && group.VersionIds != null && group.VersionIds.Count is >= 1 and <= 16 && group.VersionIds.Distinct(StringComparer.Ordinal).Count() == group.VersionIds.Count,
            "PROMPT_LAB_FAMILY_GROUP");
        var versions = group.VersionIds!.Select(GetVersion).ToArray();
        V4Rules.Require(versions.All(v => v.FamilyGroupId == group.Id && v.Scenario.Components != null)
            && versions.Select(v => v.ProfileId).Distinct().Count() == versions.Length
            && versions.Select(v => v.Scenario.Components!.FamilyId).Distinct().Count() == versions.Length, "PROMPT_LAB_FAMILY_GROUP_MEMBERS");
        return group;
    }

    internal PromptLabFamilyGroup SaveFamilyGroup(string sourceVersionId, IReadOnlyList<string> versions)
    {
        lock (sync)
        {
            var existing = FindFamilyGroup(sourceVersionId); if (existing != null) return existing;
            var group = new PromptLabFamilyGroup(sourceVersionId, VersionHash(sourceVersionId), DateTimeOffset.UtcNow, versions);
            Write("family-groups/" + sourceVersionId + ".json", "FAMILY_GROUP", group, false); return GetFamilyGroup(sourceVersionId);
        }
    }

    public PromptLabVersion GetVersion(string id)
    {
        RequireId(id); var version = Read<PromptLabVersion>("versions/" + id + ".json", "VERSION");
        version.Validate(true); V4Rules.Require(version.Id == id, "PROMPT_LAB_VERSION_PATH_MISMATCH"); return version;
    }

    public string VersionHash(string id)
    {
        RequireId(id); _ = GetVersion(id);
        return V4Rules.Hash(CompactData("versions/" + id + ".json", "VERSION"));
    }

    public PromptLabProfile GetProfile(string id)
    {
        RequireId(id); var profile = Read<PromptLabProfile>("profiles/" + id + ".json", "PROFILE");
        if (profile.AcceptedVersionId == null) throw new InvalidDataException("PROMPT_LAB_PROFILE");
        V4Rules.Require(profile.Id == id && !string.IsNullOrWhiteSpace(profile.Name) && profile.Name.Length <= 120 && IsId(profile.SelectedVersionId)
            && (profile.AcceptedVersionId.Length == 0 || IsId(profile.AcceptedVersionId)), "PROMPT_LAB_PROFILE");
        V4Rules.Require(GetVersion(profile.SelectedVersionId).ProfileId == id
            && (profile.AcceptedVersionId.Length == 0 || GetVersion(profile.AcceptedVersionId).ProfileId == id), "PROMPT_LAB_PROFILE_REFERENCE");
        return profile;
    }

    public IReadOnlyList<PromptLabProfile> ListProfiles() => Files("profiles")
        .Select(file => GetProfile(Path.GetFileNameWithoutExtension(file))).OrderBy(p => p.Name, StringComparer.Ordinal).ToArray();

    public IReadOnlyList<PromptLabVersion> ListVersions(string profileId)
    {
        RequireId(profileId);
        return Files("versions").Select(file => GetVersion(Path.GetFileNameWithoutExtension(file)))
            .Where(v => v.ProfileId == profileId).OrderByDescending(v => v.CreatedAtUtc).ThenBy(v => v.Id, StringComparer.Ordinal).ToArray();
    }

    public void SelectVersion(string id) => SetSelection(id, false);
    public void AcceptVersion(string id) { V4Rules.Require(GetVersion(id).ReadyForGeneration, "PLAN_REVIEW_REQUIRED"); SetSelection(id, true); }

    public PromptLabRun BeginRun(string versionId, string purpose = "GENERATION", string baselineRunId = "", string inputContext = "",
        PromptLabApiOptions? apiOptions = null, V4GenerationRequest? scenario = null, string experimentId = "")
    {
        lock (sync)
        {
            var version = GetVersion(versionId); V4Rules.Require(purpose is "GENERATION" or "REFINEMENT" or "DESCRIPTION", "PROMPT_LAB_PURPOSE");
            V4Rules.Require(purpose != "GENERATION" || version.ReadyForGeneration, "PLAN_REVIEW_REQUIRED");
            var baseline = baselineRunId.Length > 0 ? GetRun(baselineRunId) : null;
            var options = apiOptions ?? (purpose == "GENERATION" ? version.Generation : version.Refinement); options.Validate();
            scenario ??= version.Scenario;
            if (purpose == "GENERATION") options.Apply(scenario).Validate(PromptProfiles.Default, EffectiveRegistry());
            V4Rules.Require(inputContext != null && V4Rules.Utf8.GetByteCount(inputContext) <= 16384, "PROMPT_LAB_INPUT_CONTEXT");
            experimentId = experimentId.Length > 0 ? experimentId : baseline?.ExperimentId.Length > 0 ? baseline.ExperimentId : Guid.NewGuid().ToString("N"); RequireId(experimentId);
            var run = new PromptLabRun { Id = Guid.NewGuid().ToString("N"), VersionId = versionId, VersionHash = VersionHash(versionId),
                Purpose = purpose, BaselineRunId = baselineRunId, CreatedAtUtc = DateTimeOffset.UtcNow, ApiOptions = options, Scenario = scenario,
                InputContext = inputContext!, ExperimentId = experimentId, RegistryHash = EffectiveRegistry().Hash, RequiresTransportBinding = true };
            Write("runs/" + run.Id + "/request.json", "RUN_REQUEST", run, false); return run;
        }
    }

    public void MarkIssued(string id)
    {
        lock (sync)
        {
            var run = GetRun(id); V4Rules.Require(run.State == "QUEUED", "PROMPT_LAB_ALREADY_ISSUED");
            Write("runs/" + id + "/issued.json", "RUN_ISSUED", run with { State = "ISSUED" }, false);
        }
    }

    public PromptLabRun GetRun(string id)
    {
        RequireId(id); var folder = "runs/" + id + "/";
        var request = Read<PromptLabRun>(folder + "request.json", "RUN_REQUEST");
        V4Rules.Require(request.Id == id && request.State == "QUEUED" && request.VersionHash == VersionHash(request.VersionId), "PROMPT_LAB_RUN_VERSION");
        var outcome = File.Exists(Local(folder + "outcome.json")) ? Read<PromptLabRun>(folder + "outcome.json", "RUN_OUTCOME")
            : File.Exists(Local(folder + "issued.json")) ? Read<PromptLabRun>(folder + "issued.json", "RUN_ISSUED") : request;
        RequireSameRun(request, outcome); return outcome;
    }

    public IReadOnlyList<PromptLabRun> ListRuns(string? profileId = null)
    {
        if (profileId != null) RequireId(profileId);
        var folder = Local("runs"); if (!Directory.Exists(folder)) return [];
        return Directory.EnumerateDirectories(folder).Select(path => GetRun(Path.GetFileName(path)))
            .Where(r => profileId == null || GetVersion(r.VersionId).ProfileId == profileId).OrderByDescending(r => r.CreatedAtUtc).ToArray();
    }

    public void CompleteRun(PromptLabRun outcome)
    {
        lock (sync)
        {
            RequireId(outcome.Id); var folder = "runs/" + outcome.Id + "/";
            var original = Read<PromptLabRun>(folder + "request.json", "RUN_REQUEST"); RequireSameRun(original, outcome);
            V4Rules.Require(outcome.CompletedAtUtc.HasValue && outcome.State is "COMPLETE" or "REJECTED" or "CANCELLED" or "INDETERMINATE", "PROMPT_LAB_TERMINAL_STATE");
            if (outcome.Rows == null || outcome.Failure == null || outcome.Rows.Count > 20 || outcome.Rows.Any(r => r == null) || outcome.Rows.Select(r => r.Number).Distinct().Count() != outcome.Rows.Count)
                throw new InvalidDataException("PROMPT_LAB_ROWS");
            foreach (var row in outcome.Rows)
                V4Rules.Require(row != null && row.Number is >= 1 and <= 20 && row.Text != null && row.Text.Length <= 10000
                    && row.TextHash == TextRules.Hash(row.Text) && row.Diagnostics != null, "PROMPT_LAB_ROW_HASH");
            if (outcome.State == "COMPLETE")
            {
                V4Rules.Require(File.Exists(Local(folder + "issued.json")) && outcome.ModelId.Length > 0 && outcome.ModelContext == 65536
                    && outcome.Response != null && outcome.Response.Rejection.Length == 0 && outcome.Failure.Length == 0, "PROMPT_LAB_NO_COMPLETE_WITHOUT_MODEL_RESPONSE");
                if (outcome.Purpose == "GENERATION")
                    V4Rules.Require(outcome.Rows.Count == (outcome.Scenario ?? GetVersion(outcome.VersionId).Scenario).Count && outcome.Rows.All(r => r.SchemaValid), "PROMPT_LAB_NO_PARTIAL_SUCCESS");
                else V4Rules.Require(GetVersion(outcome.ProposedVersionId).ParentId == outcome.VersionId, "PROMPT_LAB_PROPOSAL_PARENT");
            }
            Write(folder + "outcome.json", "RUN_OUTCOME", outcome, false);
        }
    }

    public PromptLabFeedback SaveFeedback(string runId, IReadOnlyList<PromptLabRating> ratings, string batchComment)
    {
        lock (sync)
        {
            var run = GetRun(runId);
            V4Rules.Require(run.CompletedAtUtc.HasValue && batchComment != null && batchComment.Length <= 8000
                && ratings != null && ratings.Count <= run.Rows.Count && ratings.All(r => r != null) && ratings.Select(r => r.Number).Distinct().Count() == ratings.Count, "PROMPT_LAB_FEEDBACK_SCOPE");
            foreach (var rating in ratings!)
            {
                var row = run.Rows.SingleOrDefault(r => r.Number == rating.Number);
                V4Rules.Require(row != null && row.TextHash == rating.TextHash && rating.Vote is "NONE" or "LIKE" or "DISLIKE"
                    && rating.Comment != null && rating.Comment.Length <= 2000, "PROMPT_LAB_FEEDBACK_ROW_HASH");
            }
            var runHash = V4Rules.Hash(CompactData("runs/" + runId + "/outcome.json", "RUN_OUTCOME"));
            var feedback = new PromptLabFeedback(Guid.NewGuid().ToString("N"), runId, runHash, DateTimeOffset.UtcNow, ratings.ToArray(), batchComment!);
            Write("runs/" + runId + "/feedback/" + feedback.Id + ".json", "FEEDBACK", feedback, false); return feedback;
        }
    }

    public IReadOnlyList<PromptLabFeedback> ListFeedback(string runId)
    {
        var run = GetRun(runId); if (!run.CompletedAtUtc.HasValue) return [];
        var hash = V4Rules.Hash(CompactData("runs/" + runId + "/outcome.json", "RUN_OUTCOME"));
        return Files("runs/" + runId + "/feedback").Select(path =>
        {
            var feedback = Read<PromptLabFeedback>("runs/" + runId + "/feedback/" + Path.GetFileName(path), "FEEDBACK");
            V4Rules.Require(feedback.RunId == runId && feedback.RunHash == hash && IsId(feedback.Id), "PROMPT_LAB_FEEDBACK_RUN_HASH"); return feedback;
        }).OrderByDescending(f => f.CreatedAtUtc).ToArray();
    }

    public PromptLabFeedback? LatestFeedback(string runId) => ListFeedback(runId).FirstOrDefault();

    public void SaveTransport(string runId, PromptLabTransportReceipt receipt)
    {
        lock (sync)
        {
            var run = GetRun(runId); V4Rules.Require(run.State == "ISSUED", "PROMPT_LAB_TRANSPORT_RUN_STATE");
            var requestHash = V4Rules.Hash(CompactData("runs/" + runId + "/request.json", "RUN_REQUEST"));
            if (receipt.RunId != null || receipt.VersionHash != null || receipt.RequestHash != null)
                V4Rules.Require(receipt.RunId == runId && receipt.VersionHash == run.VersionHash && receipt.RequestHash == requestHash, "PROMPT_LAB_TRANSPORT_BINDING");
            Write("runs/" + runId + "/transport.json", "TRANSPORT", receipt with { RunId = runId, VersionHash = run.VersionHash, RequestHash = requestHash }, false);
        }
    }

    public PromptLabTransportReceipt? GetTransport(string runId)
    {
        RequireId(runId); var relative = "runs/" + runId + "/transport.json";
        if (!File.Exists(Local(relative))) return null;
        var receipt = Read<PromptLabTransportReceipt>(relative, "TRANSPORT");
        var request = Read<PromptLabRun>("runs/" + runId + "/request.json", "RUN_REQUEST");
        var requestHash = V4Rules.Hash(CompactData("runs/" + runId + "/request.json", "RUN_REQUEST"));
        if (request.RequiresTransportBinding || receipt.RunId != null || receipt.VersionHash != null || receipt.RequestHash != null)
            V4Rules.Require(receipt.RunId == runId && receipt.VersionHash == request.VersionHash
                && receipt.RequestHash == requestHash, "PROMPT_LAB_TRANSPORT_BINDING");
        // Legacy provenance is attached in memory; the archived receipt remains unchanged.
        return receipt with { RunId = runId, VersionHash = request.VersionHash, RequestHash = requestHash };
    }

    public string SaveCandidateJsonl(string runId, string jsonl)
    {
        RequireId(runId); _ = GetRun(runId); var path = Local("runs/" + runId + "/candidates.jsonl");
        var bytes = V4Rules.Utf8.GetBytes(jsonl); V4Rules.Require(bytes.Length <= V4BatchReader.MaxFileBytes, "PROMPT_LAB_JSONL_SIZE");
        if (File.Exists(path)) { V4Rules.Require(File.ReadAllBytes(path).SequenceEqual(bytes), "PROMPT_LAB_JSONL_DRIFT"); return path; }
        var temporary = Local("runs/" + runId + "/candidates-" + Guid.NewGuid().ToString("N") + ".tmp");
        try
        {
            using (var output = new FileStream(temporary, FileMode.CreateNew, FileAccess.Write, FileShare.None)) { output.Write(bytes); output.Flush(true); }
            workspace.Check(); PathSafety.AssertNoReparsePoints(path); File.Move(temporary, path, false); return path;
        }
        finally { if (File.Exists(temporary)) { PathSafety.AssertNoReparsePoints(temporary); File.Delete(temporary); } }
    }

    public void SaveDraft(string key, PromptLabVersion draft)
    {
        V4Rules.Require(key == "new" || IsId(key), "PROMPT_LAB_DRAFT_KEY");
        // Incomplete editor text must survive a restart even when it is not valid for model inference yet.
        V4Rules.Require(draft.Name != null && draft.System != null && draft.User != null && draft.RefinerSystem != null
            && draft.Name.Length <= 1000 && draft.System.Length <= 32000 && draft.User.Length <= 32000 && draft.RefinerSystem.Length <= 16000, "PROMPT_LAB_DRAFT_SIZE");
        lock (sync) Write("drafts/" + key + ".json", "DRAFT", draft, true);
    }

    public PromptLabVersion? GetDraft(string key)
    {
        V4Rules.Require(key == "new" || IsId(key), "PROMPT_LAB_DRAFT_KEY");
        var relative = "drafts/" + key + ".json";
        return File.Exists(Local(relative)) ? Read<PromptLabVersion>(relative, "DRAFT") : null;
    }

    public void DiscardDraft(string key)
    {
        V4Rules.Require(key == "new" || IsId(key), "PROMPT_LAB_DRAFT_KEY");
        lock (sync)
        {
            var path = Local("drafts/" + key + ".json"); if (File.Exists(path)) { PathSafety.AssertNoReparsePoints(path); File.Delete(path); }
        }
    }

    private static void RequireSameRun(PromptLabRun expected, PromptLabRun actual)
        => V4Rules.Require(actual.Id == expected.Id && actual.VersionId == expected.VersionId && actual.VersionHash == expected.VersionHash
            && actual.Purpose == expected.Purpose && actual.BaselineRunId == expected.BaselineRunId && actual.CreatedAtUtc == expected.CreatedAtUtc
            && actual.ExperimentId == expected.ExperimentId && actual.InputContext == expected.InputContext && actual.RegistryHash == expected.RegistryHash
            && actual.RequiresTransportBinding == expected.RequiresTransportBinding
            && JsonSerializer.Serialize(actual.ApiOptions, PromptProfiles.CompactJson) == JsonSerializer.Serialize(expected.ApiOptions, PromptProfiles.CompactJson)
            && JsonSerializer.Serialize(actual.Scenario, PromptProfiles.CompactJson) == JsonSerializer.Serialize(expected.Scenario, PromptProfiles.CompactJson), "PROMPT_LAB_RUN_SCOPE");

    private void SetSelection(string id, bool accept)
    {
        lock (sync)
        {
            var version = GetVersion(id); var profile = GetProfile(version.ProfileId);
            Write("profiles/" + profile.Id + ".json", "PROFILE", profile with
            {
                SelectedVersionId = id, AcceptedVersionId = accept ? id : profile.AcceptedVersionId, UpdatedAtUtc = DateTimeOffset.UtcNow
            }, true);
        }
    }

    private IEnumerable<string> Files(string folder)
    {
        var path = Local(folder); return Directory.Exists(path) ? Directory.EnumerateFiles(path, "*.json", SearchOption.TopDirectoryOnly) : [];
    }

    private string Local(string relative)
    {
        workspace.Check(); PathSafety.AssertNoReparsePoints(Root);
        return PathSafety.ResolveRelative(workspace.Root, Folder + relative);
    }

    private byte[] CompactData(string relative, string expectedKind)
    {
        var path = Local(relative);
        using var document = JsonDocument.Parse(V4Rules.ReadBytes(path, MaxFileBytes), new JsonDocumentOptions { MaxDepth = 32 });
        var root = document.RootElement; V4Rules.JsonKeys(root, ["version", "kind", "sha256", "data"], []);
        V4Rules.Require(root.GetProperty("version").GetInt32() == 1 && V4Rules.String(root, "kind") == expectedKind, "PROMPT_LAB_HISTORY_FORMAT");
        // Hash the saved JSON value, so future optional CLR fields cannot silently rewrite historical hashes.
        var bytes = JsonSerializer.SerializeToUtf8Bytes(root.GetProperty("data"), PromptProfiles.CompactJson);
        V4Rules.Require(V4Rules.Hash(bytes) == V4Rules.String(root, "sha256"), "PROMPT_LAB_HISTORY_HASH"); return bytes;
    }

    private T Read<T>(string relative, string kind) => JsonSerializer.Deserialize<T>(CompactData(relative, kind), PromptProfiles.ConfigJson)
        ?? throw new InvalidDataException("PROMPT_LAB_HISTORY_EMPTY");

    private void Write<T>(string relative, string kind, T data, bool replace)
    {
        var payload = JsonSerializer.SerializeToElement(data, PromptProfiles.CompactJson);
        var hash = V4Rules.Hash(JsonSerializer.SerializeToUtf8Bytes(payload, PromptProfiles.CompactJson));
        var bytes = JsonSerializer.SerializeToUtf8Bytes(new { version = 1, kind, sha256 = hash, data = payload }, WorkspaceStore.JsonOptions);
        V4Rules.Require(bytes.Length <= MaxFileBytes, "PROMPT_LAB_HISTORY_SIZE");
        var destination = Local(relative); var directory = Path.GetDirectoryName(destination)!;
        Directory.CreateDirectory(directory); PathSafety.AssertNoReparsePoints(directory);
        var temporary = Local(relative + ".tmp-" + Guid.NewGuid().ToString("N"));
        try
        {
            using (var output = new FileStream(temporary, FileMode.CreateNew, FileAccess.Write, FileShare.None)) { output.Write(bytes); output.Flush(true); }
            workspace.Check(); PathSafety.AssertNoReparsePoints(destination); File.Move(temporary, destination, replace);
        }
        finally { if (File.Exists(temporary)) { PathSafety.AssertNoReparsePoints(temporary); File.Delete(temporary); } }
    }
}
