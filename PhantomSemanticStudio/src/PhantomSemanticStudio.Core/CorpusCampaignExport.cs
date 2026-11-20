using System.Security.Cryptography;
using System.Text.Json;

namespace PhantomSemanticStudio.Core;

public sealed record CorpusCampaignExportFile(string Name, string Sha256, string ProvenanceHash, int Records, string Category, string Mode, string Act);
public sealed record CorpusCampaignExportResult(string Id, string State, string Path, string InputHash, int Records, int DeferredTailRows, IReadOnlyList<CorpusCampaignExportFile> Files);
internal sealed record CorpusCampaignExportPin(string TaskId, string RunId, string QuotaId, int Number, string TextHash, string DecisionHash, string JsonLineHash);
internal sealed record CorpusCampaignExportManifest(string Id, string CampaignId, string PlanHash, string PolicyId, string ApprovalHash, string RegistryHash,
    string SourcesHash, string InputHash, int Records, int DeferredTailRows, IReadOnlyList<CorpusCampaignExportFile> Files);
internal sealed record CorpusCampaignVerifiedRow(CorpusCampaignAcceptedRow Source, CorpusCampaignExportPin Pin, V4GenerationRequest Scope);

/// <summary>Explicit approved-only export and official isolated V4 compilation. No server installation.</summary>
public sealed class CorpusCampaignExport(WorkspaceStore workspace)
{
    private readonly PromptLabStore store = new(workspace);
    private static string Hash<T>(T value) => V4Rules.Hash(JsonSerializer.SerializeToUtf8Bytes(value, PromptProfiles.CompactJson));
    private static string SourcesHash(IEnumerable<CorpusCampaignExportPin> pins)
    {
        using var hash = IncrementalHash.CreateHash(HashAlgorithmName.SHA256);
        foreach (var pin in pins) { hash.AppendData(JsonSerializer.SerializeToUtf8Bytes(pin, PromptProfiles.CompactJson)); hash.AppendData([10]); }
        return Convert.ToHexString(hash.GetHashAndReset()).ToLowerInvariant();
    }
    private IReadOnlyList<CorpusCampaignVerifiedRow> VerifiedRows(string campaignId, string planHash, string policyId, string approvalHash)
    {
        var policies = new CorpusCampaignPolicies(store); _ = policies.CheckApproved(policyId, approvalHash); var policy = policies.Get(policyId);
        V4Rules.Require(policy.Spec.CampaignId == campaignId && policy.Spec.PlanHash == planHash, "CAMPAIGN_EXPORT_SCOPE");
        var plan = new CorpusCampaign(store).Check(campaignId, planHash);
        var status = CorpusCampaignControl.Status(workspace.Root, workspace.ProtectedRoot, campaignId, planHash);
        var journal = new CorpusCampaignJournal(workspace, campaignId, planHash); journal.CheckPolicy(policyId, approvalHash);
        var rows = journal.ApprovedRows(); V4Rules.Require(rows.Count == status.Approved, "CAMPAIGN_EXPORT_ORPHAN_APPROVAL");
        var index = new CorpusCandidateIndex(workspace); var result = new List<CorpusCampaignVerifiedRow>();
        var checkedTasks = new Dictionary<string, (PromptLabRun Run, string[] Lines, CorpusEditorialDecision Decision)>();
        foreach (var row in rows)
        {
            if (!checkedTasks.TryGetValue(row.TaskId, out var checkedTask))
            {
                var run = store.GetRun(row.RunId); var version = store.GetVersion(run.VersionId); var scope = run.Scenario ?? version.Scenario;
                var quota = plan.Spec.Quotas.Single(q => q.Id == row.QuotaId);
                V4Rules.Require(run.VersionId == store.GetProduction(quota.ProductionId).VersionId && run.State == "COMPLETE"
                    && run.ExperimentId == row.TaskId && policies.MatchesScope(policy, quota, scope), "CAMPAIGN_EXPORT_RUN_SCOPE");
                var snapshot = store.GetCampaignDraftSnapshot(campaignId, row.TaskId) ?? throw new InvalidDataException("CAMPAIGN_EXPORT_SNAPSHOT_REQUIRED");
                var job = store.ReadCampaignEditorial<CorpusEditorialJob>(campaignId, row.TaskId, policyId, "request", "EDITORIAL_REQUEST") ?? throw new InvalidDataException("CAMPAIGN_EXPORT_JOB_REQUIRED");
                var receipt = store.ReadCampaignEditorial<CorpusEditorialReceipt>(campaignId, row.TaskId, policyId, "receipt", "EDITORIAL_RECEIPT") ?? throw new InvalidDataException("CAMPAIGN_EXPORT_RECEIPT_REQUIRED");
                var decision = store.ReadCampaignEditorial<CorpusEditorialDecision>(campaignId, row.TaskId, policyId, "decision", "EDITORIAL_DECISION") ?? throw new InvalidDataException("CAMPAIGN_EXPORT_DECISION_REQUIRED");
                V4Rules.Require(snapshot.RunId == run.Id && snapshot.RunHash == Hash(run) && job.RunHash == snapshot.RunHash && job.TaskId == row.TaskId
                    && job.CampaignId == campaignId && job.PolicyId == policyId && job.ApprovalHash == approvalHash && job.RunId == run.Id
                    && Hash(job) == receipt.JobHash && receipt.JobHash == decision.JobHash && decision.ReceiptHash == Hash(receipt)
                    && receipt.Reply.TransportComplete && receipt.Reply.Response.Rejection.Length == 0 && Hash(decision) == row.DecisionHash
                    && job.Batch.GenerationSystem == run.EffectiveSystem && job.Batch.GenerationUser == run.EffectiveUser, "CAMPAIGN_EXPORT_EVIDENCE_DRIFT");
                var replay = CorpusCampaignEditorial.Decide(Hash(job), Hash(receipt), snapshot, LmStudioClient.ParseCorpusEditorial(receipt.Reply.Response.Content, job.Batch), scope);
                V4Rules.Require(Hash(replay) == row.DecisionHash, "CAMPAIGN_EXPORT_DECISION_MISMATCH");
                checkedTask = (run, V4CorpusGeneration.ToJsonl(run.Response!.Content, version.Generation.Apply(scope), store.EffectiveRegistry()).Split('\n', StringSplitOptions.RemoveEmptyEntries), decision);
                checkedTasks.Add(row.TaskId, checkedTask);
            }
            V4Rules.Require(checkedTask.Run.Id == row.RunId && row.Number > 0 && row.Number < checkedTask.Lines.Length && checkedTask.Lines[row.Number] == row.JsonLine
                && checkedTask.Decision.Rows.Any(r => r.Number == row.Number && r.State == "APPROVED")
                && index.HasApproval(row.RunId, row.Number, row.DecisionHash, approvalHash), "CAMPAIGN_EXPORT_ROW_NOT_APPROVED");
            using var wire = JsonDocument.Parse(row.JsonLine); V4Rules.Require(V4Rules.String(wire.RootElement, "text") == row.Text, "CAMPAIGN_EXPORT_TEXT_DRIFT");
            result.Add(new(row, new(row.TaskId, row.RunId, row.QuotaId, row.Number, TextRules.Hash(row.Text), row.DecisionHash, V4Rules.Hash(V4Rules.Utf8.GetBytes(row.JsonLine))), checkedTask.Run.Scenario!));
        }
        return result;
    }

    public CorpusCampaignExportResult Export(string campaignId, string planHash, string policyId, string approvalHash, bool flushTail = false)
    {
        var verified = VerifiedRows(campaignId, planHash, policyId, approvalHash);
        var chunks = new List<CorpusCampaignVerifiedRow[]>(); var deferred = 0;
        foreach (var group in verified.GroupBy(r => (r.Scope.Category, r.Scope.Mode)).OrderBy(g => g.Key.Category, StringComparer.Ordinal).ThenBy(g => g.Key.Mode, StringComparer.Ordinal))
            foreach (var chunk in group.Chunk(300)) { if (chunk.Length >= 100 || flushTail) chunks.Add(chunk); else deferred += chunk.Length; }
        if (chunks.Count == 0) return new("", "WAITING_FOR_FULL_BATCH", "", "", 0, deferred, []);
        var sourcesHash = SourcesHash(chunks.SelectMany(c => c).Select(r => r.Pin));
        var id = Hash(new { campaignId, planHash, policyId, approvalHash, sourcesHash, deferred, flushTail })[..32];
        var files = new List<CorpusCampaignExportFile>();
        foreach (var chunk in chunks)
        {
            var scope = chunk[0].Scope; var category = store.EffectiveRegistry().Categories.Single(c => c.Key == scope.Category);
            var name = "f" + files.Count.ToString("D5") + ".jsonl";
            var content = JsonSerializer.Serialize(new { schema = "pw-semantic-batch/1", category = scope.Category, mode = scope.Mode, act = category.Act, language = "ru", count = chunk.Length }, PromptProfiles.CompactJson)
                + "\n" + string.Join('\n', chunk.Select(r => r.Source.JsonLine)) + "\n";
            var data = V4Rules.Utf8.GetBytes(content); var pins = chunk.Select(r => r.Pin).ToArray();
            store.SaveCampaignExportBytes(id, name, data); store.SaveCampaignExportProvenance(id, name, pins);
            files.Add(new(name, V4Rules.Hash(data), Hash(pins), chunk.Length, scope.Category, scope.Mode, category.Act));
        }
        var root = store.CampaignExportRoot(id); var validation = store.IsolatedV4().Import(root, true);
        V4Rules.Require(validation.Success && validation.Entries.Count == chunks.Sum(c => c.Length), "CAMPAIGN_EXPORT_V4_INVALID: " + string.Join(';', validation.Diagnostics.Select(d => d.Code)));
        var manifest = new CorpusCampaignExportManifest(id, campaignId, planHash, policyId, approvalHash, store.EffectiveRegistry().Hash,
            sourcesHash, validation.InputHash, chunks.Sum(c => c.Length), deferred, files);
        var previous = store.ReadCampaignExportManifest(id);
        if (previous == null) store.SaveCampaignExportManifest(manifest); else V4Rules.Require(Hash(previous) == Hash(manifest), "CAMPAIGN_EXPORT_MANIFEST_DRIFT");
        return new(id, "READY_FOR_EXPLICIT_IMPORT", root, validation.InputHash, manifest.Records, deferred, files);
    }

    public V4Report Import(string exportId, string expectedInputHash, bool meaningReviewed)
    {
        V4Rules.Require(meaningReviewed, "CAMPAIGN_EXPORT_EXPLICIT_IMPORT_REQUIRED");
        var manifest = store.ReadCampaignExportManifest(exportId) ?? throw new InvalidDataException("CAMPAIGN_EXPORT_MANIFEST_REQUIRED");
        V4Rules.Require(manifest.Id == exportId && manifest.InputHash == expectedInputHash && manifest.RegistryHash == store.EffectiveRegistry().Hash, "CAMPAIGN_EXPORT_HASH");
        var current = VerifiedRows(manifest.CampaignId, manifest.PlanHash, manifest.PolicyId, manifest.ApprovalHash).ToDictionary(r => (r.Pin.TaskId, r.Pin.Number));
        var allPins = new List<CorpusCampaignExportPin>();
        foreach (var file in manifest.Files)
        {
            var data = V4Rules.ReadBytes(store.CampaignExportFilePath(exportId, file.Name), V4BatchReader.MaxFileBytes);
            var pins = store.ReadCampaignExportProvenance(exportId, file.Name);
            V4Rules.Require(V4Rules.Hash(data) == file.Sha256 && Hash(pins) == file.ProvenanceHash && pins.Count == file.Records
                && pins.All(p => current.TryGetValue((p.TaskId, p.Number), out var row) && row.Pin == p), "CAMPAIGN_EXPORT_FILE_OR_SOURCE_DRIFT");
            allPins.AddRange(pins);
        }
        V4Rules.Require(allPins.Count == manifest.Records && SourcesHash(allPins) == manifest.SourcesHash, "CAMPAIGN_EXPORT_PROVENANCE_DRIFT");
        var root = store.CampaignExportRoot(exportId); var dry = store.IsolatedV4().Import(root, true, expectedInputHash);
        V4Rules.Require(dry.Success, "CAMPAIGN_EXPORT_DRY_RUN_REJECTED: " + string.Join(';', dry.Diagnostics.Select(d => d.Code)));
        var result = store.IsolatedV4().Import(root, false, expectedInputHash);
        store.SaveCampaignExportImport(exportId, new { ManifestHash = Hash(manifest), result, AtUtc = DateTimeOffset.UtcNow }); return result;
    }
    public PromptLabStage BuildAndVerify()
    {
        var build = store.IsolatedV4().Build(); var verification = store.IsolatedV4().Verify(build.StagePath);
        V4Rules.Require(verification.Success, "CAMPAIGN_STAGE_VERIFY_FAILED"); return new(build, verification);
    }
}

public sealed partial class PromptLabStore
{
    internal string CampaignExportRoot(string id) { RequireId(id); return Local("campaign-exports/" + id + "/batches"); }
    internal string CampaignExportFilePath(string id, string name)
    {
        V4Rules.Require(name is { Length: 12 } && name.StartsWith('f') && name.EndsWith(".jsonl", StringComparison.Ordinal) && name.AsSpan(1, 5).ToArray().All(char.IsAsciiDigit), "CAMPAIGN_EXPORT_FILE_NAME");
        return PathSafety.ResolveRelative(CampaignExportRoot(id), name);
    }
    internal void SaveCampaignExportBytes(string id, string name, byte[] data) =>
        SaveCampaignFile(CampaignExportFilePath(id, name), data);

    private void SaveCampaignFile(string path, byte[] data)
    {
        V4Rules.Require(data.Length <= V4BatchReader.MaxFileBytes, "CAMPAIGN_EXPORT_FILE_LIMIT");
        if (File.Exists(path)) { V4Rules.Require(V4Rules.Hash(V4Rules.ReadBytes(path, V4BatchReader.MaxFileBytes)) == V4Rules.Hash(data), "CAMPAIGN_EXPORT_BYTES_CHANGED"); return; }
        Directory.CreateDirectory(Path.GetDirectoryName(path)!); PathSafety.AssertNoReparsePoints(path);
        var temporary = path + ".tmp-" + Guid.NewGuid().ToString("N");
        try { using (var file = new FileStream(temporary, FileMode.CreateNew, FileAccess.Write, FileShare.None)) { file.Write(data); file.Flush(true); } workspace.Check(); PathSafety.AssertNoReparsePoints(path); File.Move(temporary, path, false); }
        finally { if (File.Exists(temporary)) { PathSafety.AssertNoReparsePoints(temporary); File.Delete(temporary); } }
    }
    private string CampaignExportMetadata(string id, string leaf) { RequireId(id); return "campaign-exports/" + id + "/" + leaf; }
    internal void SaveCampaignExportManifest(CorpusCampaignExportManifest manifest) => Write(CampaignExportMetadata(manifest.Id, "manifest.json"), "CAMPAIGN_EXPORT", manifest, false);
    internal CorpusCampaignExportManifest? ReadCampaignExportManifest(string id)
    { var name = CampaignExportMetadata(id, "manifest.json"); return File.Exists(Local(name)) ? Read<CorpusCampaignExportManifest>(name, "CAMPAIGN_EXPORT") : null; }
    internal void SaveCampaignExportProvenance(string id, string name, IReadOnlyList<CorpusCampaignExportPin> pins)
    {
        _ = CampaignExportFilePath(id, name); var path = CampaignExportMetadata(id, "provenance/" + name + ".json");
        if (File.Exists(Local(path))) V4Rules.Require(JsonSerializer.Serialize(pins, PromptProfiles.CompactJson) == JsonSerializer.Serialize(Read<IReadOnlyList<CorpusCampaignExportPin>>(path, "CAMPAIGN_EXPORT_PROVENANCE"), PromptProfiles.CompactJson), "CAMPAIGN_EXPORT_PROVENANCE_CHANGED");
        else Write(path, "CAMPAIGN_EXPORT_PROVENANCE", pins, false);
    }
    internal IReadOnlyList<CorpusCampaignExportPin> ReadCampaignExportProvenance(string id, string name)
    { _ = CampaignExportFilePath(id, name); return Read<IReadOnlyList<CorpusCampaignExportPin>>(CampaignExportMetadata(id, "provenance/" + name + ".json"), "CAMPAIGN_EXPORT_PROVENANCE"); }
    internal void SaveCampaignExportImport<T>(string id, T data) => Write(CampaignExportMetadata(id, "imports/" + Guid.NewGuid().ToString("N") + ".json"), "CAMPAIGN_EXPORT_IMPORT", data, false);
}
