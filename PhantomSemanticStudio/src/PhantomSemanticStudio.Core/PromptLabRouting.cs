using System.Text.Json;

namespace PhantomSemanticStudio.Core;

public sealed record PromptLabRouteSource(string RunId, string VersionHash, string FeedbackId, string FeedbackHash, string RunHash);
public sealed record PromptLabPackPreview(string Id, DateTimeOffset CreatedAtUtc, string RegistryHash,
    IReadOnlyList<PromptLabRouteSource> Sources, string JsonlPath, string JsonlHash, string ReviewHash,
    V4Report Validation, IReadOnlyList<string> Notes);
public sealed record PromptLabStage(V4BuildResult Build, V4VerifyResult Verification);
public sealed record PromptLabProduction(string Id, string VersionId, string VersionHash, string RegistryHash,
    PromptLabVersion Version, IReadOnlyList<PromptLabRouteSource> Evidence, DateTimeOffset CreatedAtUtc);

/// <summary>Explicit editorial promotion through the existing offline V4 compiler. Never installs a server pack.</summary>
public sealed class PromptLabRouting(PromptLabStore store)
{
    private static string Hash<T>(T value) => V4Rules.Hash(JsonSerializer.SerializeToUtf8Bytes(value, PromptProfiles.CompactJson));

    private PromptLabRouteSource ReviewSource(string runId)
    {
        var run = store.GetRun(runId); var version = store.GetVersion(run.VersionId);
        V4Rules.Require(run.Purpose == "GENERATION" && run.State == "COMPLETE" && run.Rows.Count > 0 && run.Rows.All(r => r.SchemaValid), "PACK_COMPLETE_GENERATION_REQUIRED");
        V4Rules.Require(store.GetProfile(version.ProfileId).AcceptedVersionId == version.Id, "PACK_ACCEPTED_PROMPT_REQUIRED");
        V4Rules.Require(!(run.Scenario ?? version.Scenario).QaOnly, "QA_ONLY_NOT_PRODUCTION");
        V4Rules.Require(run.RegistryHash == store.EffectiveRegistry().Hash && run.VersionHash == store.VersionHash(version.Id), "PACK_SOURCE_DRIFT");
        var feedback = store.LatestFeedback(runId) ?? throw new InvalidDataException("PACK_SAVED_RATINGS_REQUIRED");
        V4Rules.Require(feedback.Ratings.Any(r => r.Vote == "LIKE"), "PACK_LIKED_ROWS_REQUIRED");
        return new(runId, store.VersionHash(version.Id), feedback.Id, Hash(feedback), Hash(run));
    }

    public PromptLabPackPreview Preview(IReadOnlyList<string> runIds)
    {
        V4Rules.Require(runIds.Count is >= 1 and <= 64 && runIds.Distinct(StringComparer.Ordinal).Count() == runIds.Count, "PACK_RUN_SELECTION");
        var registry = store.EffectiveRegistry(); var sources = runIds.Select(ReviewSource).ToArray();
        var id = Guid.NewGuid().ToString("N"); var notes = new List<string>();
        foreach (var source in sources)
        {
            var run = store.GetRun(source.RunId); var version = store.GetVersion(run.VersionId);
            var request = (run.ApiOptions ?? version.Generation).Apply(run.Scenario ?? version.Scenario);
            var jsonl = V4CorpusGeneration.ToJsonl(run.Response!.Content, request, registry);
            // Reconstruct from the hash-protected response; an edited candidates.jsonl is not a review source.
            var all = jsonl.Split('\n', StringSplitOptions.RemoveEmptyEntries);
            V4Rules.Require(all.Length == run.Rows.Count + 1, "PACK_ROW_COUNT");
            var feedback = store.LatestFeedback(run.Id)!;
            var lines = new List<string>();
            foreach (var rating in feedback.Ratings.Where(r => r.Vote == "LIKE").OrderBy(r => r.Number))
            {
                var row = run.Rows.Single(r => r.Number == rating.Number);
                V4Rules.Require(row.TextHash == rating.TextHash, "PACK_RATING_DRIFT");
                lines.Add(all[rating.Number]);
                if (row.Diagnostics.Length > 0) notes.Add(run.Id[..8] + ":" + row.Number + " — " + string.Join(", ", row.Diagnostics));
            }
            var header = JsonSerializer.Deserialize<Dictionary<string, JsonElement>>(all[0])!;
            header["count"] = JsonSerializer.SerializeToElement(lines.Count);
            store.SavePackJsonl(id, run.Id, JsonSerializer.Serialize(header, PromptProfiles.CompactJson) + "\n" + string.Join('\n', lines) + "\n");
        }
        var path = store.PackBatchRoot(id); var validation = store.IsolatedV4().Import(path, true);
        var entries = validation.Entries.Where(e => e.Mode != "TRIGGER").ToArray();
        foreach (var bucket in entries.SelectMany(e => e.Relations.Select(relation => (Entry: e, Relation: relation)))
            .GroupBy(x => (x.Entry.Category, x.Entry.Mode, x.Entry.Stance, x.Entry.RequiredEvent, x.Relation)))
            if (!bucket.Any(x => x.Entry.Voices.Contains("ANY") && x.Entry.Facets.Count == 0 && x.Entry.Profanity == "NONE"
                && x.Entry.Maturity == "GENERAL" && x.Entry.Tone is not ("ROUGH" or "SARCASTIC" or "PLAYFUL")))
                notes.Add("В этой выборке нет спокойного fallback: " + bucket.Key + ". Проверьте покрытие всего workspace перед сборкой.");
        notes.Add("Смысловая уместность и исполнение речевого акта требуют вашей проверки. LIKE и схема не доказывают их автоматически.");
        var hash = Hash(new { registry = registry.Hash, sources, validation.InputHash });
        var preview = new PromptLabPackPreview(id, DateTimeOffset.UtcNow, registry.Hash, sources, path,
            validation.InputHash, hash, validation, notes.AsReadOnly());
        store.SavePackPreview(preview); return preview;
    }

    public V4Report Import(string previewId, string expectedReviewHash, bool meaningReviewed)
    {
        V4Rules.Require(meaningReviewed, "PACK_EXPLICIT_MEANING_REVIEW_REQUIRED");
        var preview = store.GetPackPreview(previewId);
        V4Rules.Require(preview.ReviewHash == expectedReviewHash && preview.RegistryHash == store.EffectiveRegistry().Hash, "PACK_STALE_REVIEW");
        foreach (var source in preview.Sources) V4Rules.Require(ReviewSource(source.RunId) == source, "PACK_FEEDBACK_OR_SOURCE_CHANGED");
        V4Rules.Require(store.IsolatedV4().InputHash(preview.JsonlPath) == preview.JsonlHash, "PACK_JSONL_CHANGED");
        V4Rules.Require(preview.Validation.Success, "PACK_VALIDATION_REQUIRED");
        var result = store.IsolatedV4().Import(preview.JsonlPath, false, preview.Validation.InputHash);
        store.SavePackImport(preview, result); return result;
    }

    public PromptLabStage BuildAndVerify()
    {
        var workspace = store.IsolatedV4(); var build = workspace.Build(); var verified = workspace.Verify(build.StagePath);
        V4Rules.Require(verified.Success, "PACK_STAGE_VERIFY_FAILED: " + string.Join("; ", verified.Diagnostics.Select(d => d.Message)));
        return new(build, verified);
    }

    public PromptLabProduction Promote(string versionId, IReadOnlyList<string> reviewedRunIds, bool confirmed)
    {
        V4Rules.Require(confirmed && reviewedRunIds.Count is >= 1 and <= 64, "PRODUCTION_EXPLICIT_REVIEW_REQUIRED");
        var version = store.GetVersion(versionId); version.Validate(false, store.EffectiveRegistry());
        V4Rules.Require(store.GetProfile(version.ProfileId).AcceptedVersionId == version.Id, "PRODUCTION_ACCEPTED_VERSION_REQUIRED");
        var sources = reviewedRunIds.Distinct(StringComparer.Ordinal).Select(ReviewSource).ToArray();
        V4Rules.Require(sources.All(s => store.GetRun(s.RunId).VersionId == versionId), "PRODUCTION_EVIDENCE_VERSION");
        var production = new PromptLabProduction(Guid.NewGuid().ToString("N"), versionId, store.VersionHash(versionId),
            store.EffectiveRegistry().Hash, version, sources, DateTimeOffset.UtcNow);
        store.SaveProduction(production); return production;
    }

    internal PromptLabProduction ValidateProduction(string productionId)
    {
        var production = store.GetProduction(productionId); var version = store.GetVersion(production.VersionId);
        V4Rules.Require(production.RegistryHash == store.EffectiveRegistry().Hash && production.VersionHash == store.VersionHash(version.Id)
            && production.Version != null && Hash(production.Version) == Hash(version), "PRODUCTION_SNAPSHOT_DRIFT");
        version.Validate(true, store.EffectiveRegistry());
        V4Rules.Require(production.Evidence is { Count: >= 1 and <= 64 }
            && production.Evidence.All(e => e != null) && production.Evidence.Select(e => e.RunId).Distinct(StringComparer.Ordinal).Count() == production.Evidence.Count,
            "PRODUCTION_EVIDENCE_REQUIRED");
        foreach (var source in production.Evidence)
            V4Rules.Require(ReviewSource(source.RunId) == source && store.GetRun(source.RunId).VersionId == version.Id, "PRODUCTION_EVIDENCE_DRIFT");
        return production;
    }
}

public sealed partial class PromptLabStore
{
    internal string PackBatchRoot(string id) { RequireId(id); return Local("packs/" + id + "/batches"); }
    internal string SavePackJsonl(string id, string runId, string content)
    {
        RequireId(id); RequireId(runId); var path = Local("packs/" + id + "/batches/" + runId + ".jsonl"); Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        var bytes = V4Rules.Utf8.GetBytes(content); V4Rules.Require(bytes.Length <= V4BatchReader.MaxFileBytes, "PACK_BYTES_LIMIT");
        var temporary = path + ".tmp";
        try
        {
            using (var file = new FileStream(temporary, FileMode.CreateNew, FileAccess.Write, FileShare.None)) { file.Write(bytes); file.Flush(true); }
            workspace.Check(); PathSafety.AssertNoReparsePoints(path); File.Move(temporary, path); return path;
        }
        finally { if (File.Exists(temporary)) { PathSafety.AssertNoReparsePoints(temporary); File.Delete(temporary); } }
    }
    internal void SavePackPreview(PromptLabPackPreview preview) => Write("packs/" + preview.Id + "/preview.json", "PACK_PREVIEW", preview, false);
    public PromptLabPackPreview GetPackPreview(string id)
    {
        RequireId(id); var preview = Read<PromptLabPackPreview>("packs/" + id + "/preview.json", "PACK_PREVIEW");
        V4Rules.Require(preview.Id == id && PathSafety.Canonical(preview.JsonlPath) == PackBatchRoot(id), "PACK_PREVIEW_PATH"); return preview;
    }
    internal void SavePackImport(PromptLabPackPreview preview, V4Report result) => Write("packs/" + preview.Id + "/import-" + Guid.NewGuid().ToString("N") + ".json",
        "PACK_IMPORT", new { preview.ReviewHash, result, At = DateTimeOffset.UtcNow }, false);
    internal void SaveProduction(PromptLabProduction production) => Write("production/" + production.Id + ".json", "PRODUCTION", production, false);
    public PromptLabProduction GetProduction(string id)
    {
        RequireId(id); var production = Read<PromptLabProduction>("production/" + id + ".json", "PRODUCTION");
        V4Rules.Require(production.Id == id, "PRODUCTION_ID"); return production;
    }
    public IReadOnlyList<PromptLabProduction> ListProduction() => Files("production")
        .Select(file => Read<PromptLabProduction>("production/" + Path.GetFileName(file), "PRODUCTION")).OrderByDescending(p => p.CreatedAtUtc).ToArray();
}
