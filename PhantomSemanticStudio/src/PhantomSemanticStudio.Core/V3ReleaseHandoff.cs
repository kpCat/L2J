using System.Text.Json;
using System.Text.RegularExpressions;
using System.Security.Cryptography;
using System.Text;

namespace PhantomSemanticStudio.Core;

public static class V3ReleaseHandoff
{
    private sealed record JavaAttestation(int Version, string StageId, string ProofSha256, string Mac);
    public static string InspectProof(WorkspaceStore store, string stageRoot, string proofPath, CancellationToken token = default)
    { store.Check(); var receipt = V3ProposalContract.ReadStage(store.Root, stageRoot, token); return VerifyProof(receipt, stageRoot, proofPath, token); }

    public static string Prepare(WorkspaceStore store, string stageRoot, string proofPath, string reviewedProofHash,
        bool operatorConfirmed, CancellationToken token = default)
    {
        V3ProposalContract.Require(operatorConfirmed, "RELEASE_CONSENT"); token.ThrowIfCancellationRequested(); store.Check();
        var receipt = V3ProposalContract.ReadStage(store.Root, stageRoot, token);
        V3ProposalContract.Require(VerifyProof(receipt, stageRoot, proofPath, token) == reviewedProofHash, "PROOF_REVIEW_HASH");
        var source = new PackReader().Load(receipt.SourceModule, token); var staged = new PackReader().Load(Path.Combine(stageRoot, "module"), token);
        var paths = receipt.Candidates.Select(c => c.Kind == "PATTERN" ? receipt.Pair.SemanticPath : receipt.Pair.ConversationPath).Distinct(StringComparer.Ordinal).Order(StringComparer.Ordinal).ToArray();
        var payload = paths.Select(path => (Path: path, Original: V3ProposalContract.ReadStamped(source, path), Proposed: V3ProposalContract.ReadStamped(staged, path))).ToArray();
        var drive = new DriveInfo(Path.GetPathRoot(store.Root)!); V3ProposalContract.Require(drive.AvailableFreeSpace >= 16 * 1024 * 1024, "RELEASE_DISK");
        var parent = PathSafety.ResolveRelative(store.Root, "release-candidates"); var id = Guid.NewGuid().ToString("N");
        var partial = PathSafety.ResolveRelative(parent, id + ".partial"); var final = PathSafety.ResolveRelative(parent, id); var ownsPartial = false;
        try
        {
            store.Check(); PathSafety.AssertNoReparsePoints(parent); Directory.CreateDirectory(parent); Directory.CreateDirectory(partial); ownsPartial = true;
            foreach (var item in payload)
            {
                token.ThrowIfCancellationRequested();
                V3ProposalContract.WriteNew(PathSafety.ResolveRelative(partial, "proposed/" + item.Path), item.Proposed);
                V3ProposalContract.WriteNew(PathSafety.ResolveRelative(partial, "backup/" + item.Path), item.Original);
                V3ProposalContract.Require(File.ReadAllBytes(PathSafety.ResolveRelative(partial, "backup/" + item.Path)).SequenceEqual(item.Original)
                    && File.ReadAllBytes(PathSafety.ResolveRelative(partial, "proposed/" + item.Path)).SequenceEqual(item.Proposed), "RELEASE_COPY_HASH");
            }
            var manifest = new { Version = 1, Status = "OFFLINE_HANDOFF_NOT_INSTALLED", RuntimeStatus = "JAVA_CONTENT_ONLY_NOT_RUNTIME_READY", StageId = receipt.StageId,
                JavaProofSha256 = reviewedProofHash, ExpectedSourceFingerprint = receipt.SourceFingerprint,
                Files = payload.Select(p => new { RelativePath = p.Path, OriginalSha256 = TextRules.Hash(p.Original), OriginalBytes = p.Original.Length,
                    ProposedSha256 = TextRules.Hash(p.Proposed), ProposedBytes = p.Proposed.Length }).ToArray() };
            V3ProposalContract.WriteNew(PathSafety.ResolveRelative(partial, "release-manifest.json"), JsonSerializer.SerializeToUtf8Bytes(manifest, WorkspaceStore.JsonOptions));
            V3ProposalContract.WriteNew(PathSafety.ResolveRelative(partial, "CHECKLIST_RU.txt"), V3ProposalContract.Utf8.GetBytes(
                "OFFLINE_HANDOFF_NOT_INSTALLED\nJAVA_CONTENT_ONLY_NOT_RUNTIME_READY\n\n"
                + "Это только offline предложение. Программа ничего не устанавливает. Java content gate не доказывает социальное поведение, игровые действия или качество речи.\n"
                + "Для отдельного будущего ручного решения требуется новое разрешение владельца и окно обслуживания.\n"
                + "1. Повторно сверить актуальные source SHA, fingerprint, exact ID и Java proof hash с manifest. При drift остановиться.\n"
                + "2. Проверить все предложенные тексты, смысл, пол, profanity/mature и scope.\n"
                + "3. Подготовить независимую резервную копию текущих файлов, сверить byte/hash и возможность восстановления.\n"
                + "4. Только в отдельной будущей задаче владелец решает ручное применение и повторную content/runtime проверку. Здесь ничего не применять.\n"
                + "5. План отката: остановить ручное изменение, восстановить отдельно проверенные исходные backup bytes, сверить SHA и повторить проверки.\n"
                + "Этот каталог не содержит установщика, исполняемых команд или разрешения на публикацию.\n"));
            token.ThrowIfCancellationRequested(); store.Check();
            var finalReceipt = V3ProposalContract.ReadStage(store.Root, stageRoot, token);
            V3ProposalContract.Require(VerifyProof(finalReceipt, stageRoot, proofPath, token) == reviewedProofHash, "PROOF_DRIFT");
            PathSafety.AssertNoReparsePoints(partial); PathSafety.AssertNoReparsePoints(final); token.ThrowIfCancellationRequested(); Directory.Move(partial, final); return final;
        }
        catch { if (ownsPartial) V3ProposalContract.RemoveOwnPartial(parent, partial); throw; }
    }
    private static string VerifyProof(V3StageReceipt receipt, string stageRoot, string proofPath, CancellationToken token)
    {
        token.ThrowIfCancellationRequested(); var stage = PathSafety.Canonical(stageRoot); var path = PathSafety.Canonical(proofPath); var oracle = Path.GetDirectoryName(path)!;
        V3ProposalContract.Require(Path.GetDirectoryName(oracle) == stage && Regex.IsMatch(Path.GetFileName(oracle), "^oracle-[0-9a-f]{32}$") && Path.GetFileName(path) == "java-validation.json", "PROOF_PATH");
        PathSafety.AssertNoReparsePoints(path);
        V3ProposalContract.Require(new FileInfo(path).Length is > 0 and <= 262144, "PROOF_SIZE");
        var proofHash = TextRules.Hash(File.ReadAllBytes(path));
        var attestation = V3ProposalContract.ReadJson<JavaAttestation>(PathSafety.ResolveRelative(oracle, "java-attestation.json"));
        V3ProposalContract.Require(attestation.Version == 1 && attestation.StageId == receipt.StageId && attestation.ProofSha256 == proofHash && V3ProposalContract.Sha(attestation.Mac), "PROOF_ATTESTATION");
        // An honest local operator attestation, not protection from the OS owner reading its key.
        // Key stays outside oracle/export and is never part of source, receipt, logs or Git.
        var workspace = Path.GetDirectoryName(Path.GetDirectoryName(stage))!;
        var keyPath = PathSafety.ResolveRelative(workspace, "java-attestations/" + receipt.StageId + ".key");
        V3ProposalContract.Require(new FileInfo(keyPath).Length == 32, "PROOF_KEY");
        var key = File.ReadAllBytes(keyPath);
        try
        {
            var payload = Encoding.UTF8.GetBytes("PSS008_NATIVE_JAVA_V1\n" + receipt.StageId + "\n" + proofHash);
            V3ProposalContract.Require(CryptographicOperations.FixedTimeEquals(HMACSHA256.HashData(key, payload), Convert.FromHexString(attestation.Mac)), "PROOF_AUTHENTICATION");
        }
        finally { CryptographicOperations.ZeroMemory(key); }
        var proof = V3ProposalContract.ReadJson<JsonElement>(path);
        string S(string name) => proof.GetProperty(name).GetString() ?? "";
        foreach (var (file, hashKey) in new[] { ("Test-PSS008-V3-Java.ps1", "ScriptHash"), ("Pss008V3CatalogProbe.java", "BridgeHash") })
        {
            var trusted = PathSafety.ResolveRelative(AppContext.BaseDirectory, "operator/" + file);
            V3ProposalContract.Require(File.Exists(trusted) && new FileInfo(trusted).Length <= 1048576 && TextRules.Hash(File.ReadAllBytes(trusted)) == S(hashKey), "UNTRUSTED_ORACLE_TOOLS");
        }
        V3ProposalContract.Require(proof.GetProperty("Version").GetInt32() == 1 && S("JavaStatus") == "PASS_JAVA_STAGED_V3" && S("Status") == "JAVA_CONTENT_ONLY_NOT_RUNTIME_READY"
            && S("StageId") == receipt.StageId && S("ReceiptHash") == TextRules.Hash(File.ReadAllBytes(PathSafety.ResolveRelative(stage, "receipt.json")))
            && S("SourceFingerprint") == receipt.SourceFingerprint && proof.GetProperty("SourceEqual").GetBoolean()
            && proof.GetProperty("AntExitCode").GetInt32() == 0 && proof.GetProperty("ProbeExitCode").GetInt32() == 0 && proof.GetProperty("CompileExitCode").GetInt32() == 0
            && proof.GetProperty("NegativeExitCode").GetInt32() == 3 && proof.GetProperty("SchemaNegativeExitCode").GetInt32() == 3 && S("Scratch") == oracle, "PROOF_STATUS");
        var stages = proof.GetProperty("StageFiles").Deserialize<List<SourceFileStamp>>()!; var sources = proof.GetProperty("SourceFiles").Deserialize<List<SourceFileStamp>>()!;
        V3ProposalContract.Require(stages.SequenceEqual(receipt.StagedFiles) && sources.SequenceEqual(receipt.SourceFiles) && S("Jdk").StartsWith("javac 25.", StringComparison.Ordinal)
            && S("Ant").StartsWith("Apache Ant", StringComparison.Ordinal), "PROOF_INPUTS");
        foreach (var (relative, hash) in new[] { ("operator.ps1", S("ScriptHash")), ("Pss008V3CatalogProbe.java", S("BridgeHash")), ("input-inventory.json", S("InputInventoryHash")), ("expected-ids.tsv", S("ExpectedIdsSha256")) })
        {
            V3ProposalContract.Require(V3ProposalContract.Sha(hash), "PROOF_INPUT_HASH"); var file = PathSafety.ResolveRelative(oracle, relative);
            V3ProposalContract.Require(new FileInfo(file).Length <= 1048576 && TextRules.Hash(File.ReadAllBytes(file)) == hash, "PROOF_INPUT_HASH");
        }
        var inventory = V3ProposalContract.ReadJson<List<SourceFileStamp>>(PathSafety.ResolveRelative(oracle, "input-inventory.json"), 1048576);
        V3ProposalContract.Require(inventory.Count is > 0 and <= 4000 && inventory.Count == proof.GetProperty("CopyFiles").GetInt32()
            && inventory.Sum(f => f.Bytes) == proof.GetProperty("CopyBytes").GetInt64() && inventory.Sum(f => f.Bytes) <= 268435456
            && inventory.Select(f => f.RelativePath).Distinct(StringComparer.Ordinal).Count() == inventory.Count, "ORACLE_INVENTORY");
        V3ProposalContract.Require(inventory.Any(f => f.RelativePath == "build.xml" && f.Sha256 == "048a16cd53f694d85803b16af631e14fb20c0c3b49f400a749a328d572c63114")
            && inventory.Any(f => f.RelativePath == "java/org/l2jmobius/gameserver/phantoms/conversation/humanized/PhantomHumanizedCatalog.java" && f.Sha256 == "b7876a52a487bc1e1bc469c9c71b47de805b31993e589b4be7ca55182c640970"), "UNAUDITED_ORACLE_BUILD");
        foreach (var stamp in inventory)
        {
            token.ThrowIfCancellationRequested(); var relative = stamp.RelativePath;
            V3ProposalContract.Require(relative == "build.xml" || (relative.StartsWith("java/", StringComparison.Ordinal) || relative.StartsWith("test/java/", StringComparison.Ordinal)) && relative.EndsWith(".java", StringComparison.Ordinal)
                || relative.StartsWith("test/resources/", StringComparison.Ordinal) && (relative.EndsWith(".sql", StringComparison.Ordinal) || relative.EndsWith(".tsv", StringComparison.Ordinal) || relative == "test/resources/phantoms/scenarios/harness-smoke.properties")
                || relative.StartsWith("dist/libs/", StringComparison.Ordinal) && relative.EndsWith(".jar", StringComparison.Ordinal) && !Regex.IsMatch(relative, "(GameServer|LoginServer)\\.jar$|-sources\\.jar$"), "ORACLE_ALLOWLIST");
            var file = PathSafety.ResolveRelative(receipt.SourceModule, relative); using var stream = new FileStream(file, FileMode.Open, FileAccess.Read, FileShare.Read);
            V3ProposalContract.Require(stream.Length == stamp.Bytes && stamp.Bytes is > 0 and <= 268435456 && CorpusStore.HashStream(stream, token) == stamp.Sha256, "ORACLE_SOURCE_DRIFT");
        }
        var logs = proof.GetProperty("Logs").Deserialize<List<SourceFileStamp>>()!;
        string[] expectedLogs = ["ant-content.txt", "probe.txt", "negative.txt", "negative-schema.txt", "probe-compile.txt", "java-version.txt", "ant-version.txt"];
        V3ProposalContract.Require(logs.Count == expectedLogs.Length && logs.Select(l => l.RelativePath).Order().SequenceEqual(expectedLogs.Order()), "PROOF_LOGS");
        foreach (var log in logs) { token.ThrowIfCancellationRequested(); V3ProposalContract.ReadStamped(PathSafety.ResolveRelative(oracle, log.RelativePath), log); }
        var evidence = S("ProbeEvidence");
        var match = Regex.Match(evidence, "^PASS_JAVA_STAGED_V3 baselinePatterns=(\\d+) baselineTemplates=(\\d+) stagedPatterns=(\\d+) stagedTemplates=(\\d+) checkedIds=(\\d+) baselineHash=([0-9a-f]{64}) stagedHash=([0-9a-f]{64}) negative=DUPLICATE_AND_SCHEMA_REJECTED$");
        V3ProposalContract.Require(match.Success && int.Parse(match.Groups[1].Value) == receipt.BaselinePatterns && int.Parse(match.Groups[2].Value) == receipt.BaselineTemplates
            && int.Parse(match.Groups[3].Value) == receipt.BaselinePatterns + receipt.AddedPatterns && int.Parse(match.Groups[4].Value) == receipt.BaselineTemplates + receipt.AddedTemplates
            && int.Parse(match.Groups[5].Value) == receipt.Candidates.Count && match.Groups[6].Value != match.Groups[7].Value, "PROOF_COUNTERS");
        V3ProposalContract.Require(File.ReadAllLines(PathSafety.ResolveRelative(oracle, "probe.txt")).Count(line => line == evidence) == 1
            && File.ReadAllText(PathSafety.ResolveRelative(oracle, "negative.txt")).Contains("REJECTED_V3_NEGATIVE", StringComparison.Ordinal)
            && File.ReadAllText(PathSafety.ResolveRelative(oracle, "negative-schema.txt")).Contains("REJECTED_V3_NEGATIVE", StringComparison.Ordinal)
            && File.ReadAllText(PathSafety.ResolveRelative(oracle, "ant-content.txt")).Contains("BUILD SUCCESSFUL", StringComparison.Ordinal), "PROOF_NATIVE_LOGS");
        token.ThrowIfCancellationRequested(); V3ProposalContract.Require(TextRules.Hash(File.ReadAllBytes(path)) == proofHash, "PROOF_DRIFT"); return proofHash;
    }
}
