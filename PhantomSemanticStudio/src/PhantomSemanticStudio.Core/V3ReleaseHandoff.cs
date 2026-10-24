using System.Text.Json;
using System.Text.RegularExpressions;
using System.Security.Cryptography;
using System.Text;

namespace PhantomSemanticStudio.Core;

public static class V3ReleaseHandoff
{
    private sealed record JavaAttestation(int Version, string StageId, string ProofSha256, string Mac);
    private sealed record JavaToolStamp(string Path, string Sha256, long Bytes);
    private static readonly IReadOnlyDictionary<string, string> IsolatedInputs = new Dictionary<string, string>(StringComparer.Ordinal)
    {
        ["java/org/l2jmobius/gameserver/phantoms/conversation/humanized/PhantomHumanizedCatalog.java"] = "01a08536ab4445562dcaebe7aa7704617602a47c65611bcf1d2e849393eddb05",
        ["java/org/l2jmobius/gameserver/model/actor/enums/player/PlayerClass.java"] = "9678b970528f17192114669ad96b8538add396ec251f4fbc4476d57c1e64a2cf",
        ["java/org/l2jmobius/gameserver/model/actor/enums/creature/Race.java"] = "7cd9a7ba39b483b93c6fd47b1ef4572dc526fb0a06419bf918fafc823c5cbc0b"
    };
    internal static void ValidateIsolatedInventory(IReadOnlyList<SourceFileStamp> inventory)
    {
        V3ProposalContract.Require(inventory.Count == IsolatedInputs.Count && inventory.Select(f => f.RelativePath).Distinct(StringComparer.Ordinal).Count() == inventory.Count
            && inventory.All(f => IsolatedInputs.TryGetValue(f.RelativePath, out var hash) && hash == f.Sha256 && f.Bytes is > 0 and <= 1048576), "UNAUDITED_ISOLATED_CATALOG");
    }
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
        var proof = V3ProposalContract.ReadJson<JsonElement>(path);
        var version = proof.GetProperty("Version").GetInt32(); var isolated = version is 2 or 3;
        V3ProposalContract.Require(version is 1 or 2 or 3 && (receipt.Version == 3 ? version == 3 : version < 3), "PROOF_VERSION");
        var attestation = V3ProposalContract.ReadJson<JavaAttestation>(PathSafety.ResolveRelative(oracle, "java-attestation.json"));
        V3ProposalContract.Require(attestation.Version == version && attestation.StageId == receipt.StageId && attestation.ProofSha256 == proofHash && V3ProposalContract.Sha(attestation.Mac), "PROOF_ATTESTATION");
        // An honest local operator attestation, not protection from the OS owner reading its key.
        // Key stays outside oracle/export and is never part of source, receipt, logs or Git.
        var workspace = Path.GetDirectoryName(Path.GetDirectoryName(stage))!;
        var keyPath = PathSafety.ResolveRelative(workspace, "java-attestations/" + receipt.StageId + ".key");
        V3ProposalContract.Require(new FileInfo(keyPath).Length == 32, "PROOF_KEY");
        var key = File.ReadAllBytes(keyPath);
        try
        {
            var payload = Encoding.UTF8.GetBytes("PSS008_NATIVE_JAVA_V" + version + "\n" + receipt.StageId + "\n" + proofHash);
            V3ProposalContract.Require(CryptographicOperations.FixedTimeEquals(HMACSHA256.HashData(key, payload), Convert.FromHexString(attestation.Mac)), "PROOF_AUTHENTICATION");
        }
        finally { CryptographicOperations.ZeroMemory(key); }
        string S(string name) => proof.GetProperty(name).GetString() ?? "";
        foreach (var (file, hashKey) in new[] { ("Test-PSS008-V3-Java.ps1", "ScriptHash"), ("Pss008V3CatalogProbe.java", "BridgeHash") })
        {
            var trusted = PathSafety.ResolveRelative(AppContext.BaseDirectory, "operator/" + file);
            V3ProposalContract.Require(File.Exists(trusted) && new FileInfo(trusted).Length <= 1048576 && TextRules.Hash(File.ReadAllBytes(trusted)) == S(hashKey), "UNTRUSTED_ORACLE_TOOLS");
        }
        V3ProposalContract.Require(S("JavaStatus") == "PASS_JAVA_STAGED_V3" && S("Status") == "JAVA_CONTENT_ONLY_NOT_RUNTIME_READY"
            && S("StageId") == receipt.StageId && S("ReceiptHash") == TextRules.Hash(File.ReadAllBytes(PathSafety.ResolveRelative(stage, "receipt.json")))
            && S("SourceFingerprint") == receipt.SourceFingerprint && proof.GetProperty("SourceEqual").GetBoolean()
            && (isolated ? S("ExecutionMode") == (version == 3 ? "ISOLATED_CATALOG_FACET_V1" : "ISOLATED_CATALOG_V1") && S("Ant") == "NOT_RUN" && proof.GetProperty("AntExitCode").ValueKind == JsonValueKind.Null && !proof.TryGetProperty("AntCommand", out _)
                : proof.GetProperty("AntExitCode").GetInt32() == 0)
            && proof.GetProperty("ProbeExitCode").GetInt32() == 0 && proof.GetProperty("CompileExitCode").GetInt32() == 0
            && proof.GetProperty("NegativeExitCode").GetInt32() == 3 && proof.GetProperty("SchemaNegativeExitCode").GetInt32() == 3 && S("Scratch") == oracle, "PROOF_STATUS");
        var stages = proof.GetProperty("StageFiles").Deserialize<List<SourceFileStamp>>()!; var sources = proof.GetProperty("SourceFiles").Deserialize<List<SourceFileStamp>>()!;
        V3ProposalContract.Require(stages.SequenceEqual(receipt.StagedFiles) && sources.SequenceEqual(receipt.SourceFiles) && S("Jdk").StartsWith("javac 25.", StringComparison.Ordinal)
            && (isolated || S("Ant").StartsWith("Apache Ant", StringComparison.Ordinal)), "PROOF_INPUTS");
        foreach (var (relative, hash) in new[] { ("operator.ps1", S("ScriptHash")), ("Pss008V3CatalogProbe.java", S("BridgeHash")), ("input-inventory.json", S("InputInventoryHash")), ("expected-ids.tsv", S("ExpectedIdsSha256")) })
        {
            V3ProposalContract.Require(V3ProposalContract.Sha(hash), "PROOF_INPUT_HASH"); var file = PathSafety.ResolveRelative(oracle, relative);
            V3ProposalContract.Require(new FileInfo(file).Length <= 1048576 && TextRules.Hash(File.ReadAllBytes(file)) == hash, "PROOF_INPUT_HASH");
        }
        var inventory = V3ProposalContract.ReadJson<List<SourceFileStamp>>(PathSafety.ResolveRelative(oracle, "input-inventory.json"), 1048576);
        V3ProposalContract.Require(inventory.Count is > 0 and <= 4000 && inventory.Count == proof.GetProperty("CopyFiles").GetInt32()
            && inventory.Sum(f => f.Bytes) == proof.GetProperty("CopyBytes").GetInt64() && inventory.Sum(f => f.Bytes) <= 268435456
            && inventory.Select(f => f.RelativePath).Distinct(StringComparer.Ordinal).Count() == inventory.Count, "ORACLE_INVENTORY");
        // TASK063 audited db375d6 baseline; exact CRLF/LF bytes, never an arbitrary build or catalog.
        if (isolated) ValidateIsolatedInventory(inventory);
        else V3ProposalContract.Require(inventory.Any(f => f.RelativePath == "build.xml" && (f.Sha256 is "f6bebf5452655de3e08d8941134307650b830c2d0c1f31ded1cc1d0f2bd5fdc1" or "dafce95145d8d722138dfa16515aac0271110cd80254c204e308a03f78ecddca"))
            && inventory.Any(f => f.RelativePath == "java/org/l2jmobius/gameserver/phantoms/conversation/humanized/PhantomHumanizedCatalog.java" && (f.Sha256 is "ef0361e344a39e3c6c0e6f2b5f5cb4b4d4c82924df1b9fe956ea983cccc7e840" or "a33ec0bbac8407430a695c443722c76140f39abed18d1629e78cc82388d1b335"
                or "493d1010e653f66b2e1f3407914735a4ce5a370f8dd7d0d5349fbccb0fb76ef0" or "01a08536ab4445562dcaebe7aa7704617602a47c65611bcf1d2e849393eddb05")), "UNAUDITED_ORACLE_BUILD");
        // TASK082 adds only the reviewed facet catalog; historical 063/080 pins and Ant ownership/write guards remain intact.
        var expectedRows = receipt.Candidates.Select(c => string.Join("\t", c.XmlId, c.Kind, c.Act, c.Topic, c.TextHash,
            c.Kind == "PATTERN" ? receipt.Pair.SemanticPath : receipt.Pair.ConversationPath, c.Voice, c.Band, c.Register)
            + (version == 3 ? "\t" + c.Facet + "\t" + c.MinIntensity?.ToString(System.Globalization.CultureInfo.InvariantCulture) : ""));
        if (version == 3) expectedRows = expectedRows.Prepend("PSS008_FACET_SPEC_V1");
        V3ProposalContract.Require(File.ReadAllLines(PathSafety.ResolveRelative(oracle, "expected-ids.tsv")).SequenceEqual(expectedRows), "PROOF_VOICE_SPEC");
        foreach (var stamp in inventory)
        {
            token.ThrowIfCancellationRequested(); var relative = stamp.RelativePath;
            V3ProposalContract.Require(relative == "build.xml" || (relative.StartsWith("java/", StringComparison.Ordinal) || relative.StartsWith("test/java/", StringComparison.Ordinal)) && relative.EndsWith(".java", StringComparison.Ordinal)
                || relative.StartsWith("test/resources/", StringComparison.Ordinal) && (relative.EndsWith(".sql", StringComparison.Ordinal) || relative.EndsWith(".tsv", StringComparison.Ordinal)
                    || relative is "test/resources/phantoms/scenarios/harness-smoke.properties" or "test/resources/phantoms/party-state-v1.base64"
                        or "test/resources/phantoms/M1TimerBootstrap.java" or "test/resources/phantoms/M1Q266Bootstrap.java" or "test/resources/phantoms/M1NativeEvidenceContract.java")
                || relative.StartsWith("dist/libs/", StringComparison.Ordinal) && relative.EndsWith(".jar", StringComparison.Ordinal) && !Regex.IsMatch(relative, "(GameServer|LoginServer)\\.jar$|-sources\\.jar$"), "ORACLE_ALLOWLIST");
            var file = PathSafety.ResolveRelative(receipt.SourceModule, relative); using var stream = new FileStream(file, FileMode.Open, FileAccess.Read, FileShare.Read);
            V3ProposalContract.Require(stream.Length == stamp.Bytes && stamp.Bytes is > 0 and <= 268435456 && CorpusStore.HashStream(stream, token) == stamp.Sha256, "ORACLE_SOURCE_DRIFT");
            if (isolated) V3ProposalContract.ReadStamped(PathSafety.ResolveRelative(oracle, "module/" + relative), stamp);
        }
        if (isolated)
        {
            var tools = proof.GetProperty("JavaTools").Deserialize<List<JavaToolStamp>>()!;
            V3ProposalContract.Require(tools.Count == 2 && Path.GetFileName(tools[0].Path) == "java.exe" && Path.GetFileName(tools[1].Path) == "javac.exe"
                && Path.GetDirectoryName(tools[0].Path) == Path.GetDirectoryName(tools[1].Path), "PROOF_JDK_TOOLS");
            foreach (var tool in tools)
            {
                V3ProposalContract.Require(Path.IsPathFullyQualified(tool.Path) && V3ProposalContract.Sha(tool.Sha256), "PROOF_JDK_TOOLS");
                PathSafety.AssertNoReparsePoints(tool.Path); using var input = new FileStream(tool.Path, FileMode.Open, FileAccess.Read, FileShare.Read);
                V3ProposalContract.Require(input.Length == tool.Bytes && tool.Bytes is > 0 and <= 1048576 && CorpusStore.HashStream(input, token) == tool.Sha256, "PROOF_JDK_DRIFT");
            }
            var empty = PathSafety.ResolveRelative(oracle, "empty"); var bin = PathSafety.ResolveRelative(oracle, "probe-bin");
            var expectedCompile = new[] { tools[1].Path, "--release", "25", "-encoding", "UTF-8", "-proc:none", "-implicit:none", "-sourcepath", empty, "-classpath", empty, "-d", bin }
                .Concat(inventory.Select(f => PathSafety.ResolveRelative(oracle, "module/" + f.RelativePath))).Append(PathSafety.ResolveRelative(oracle, "Pss008V3CatalogProbe.java"));
            V3ProposalContract.Require(proof.GetProperty("CompileCommand").Deserialize<string[]>()!.SequenceEqual(expectedCompile)
                && !Directory.EnumerateFileSystemEntries(empty).Any(), "PROOF_ISOLATED_COMPILE");
            var expectedProbe = new[] { tools[0].Path, "-cp", bin, "Pss008V3CatalogProbe", oracle, PathSafety.ResolveRelative(oracle, "baseline"),
                PathSafety.ResolveRelative(oracle, "module/dist/game/data/phantoms"), PathSafety.ResolveRelative(oracle, "negative"), PathSafety.ResolveRelative(oracle, "expected-ids.tsv"),
                receipt.BaselinePatterns.ToString(System.Globalization.CultureInfo.InvariantCulture), receipt.BaselineTemplates.ToString(System.Globalization.CultureInfo.InvariantCulture),
                receipt.AddedPatterns.ToString(System.Globalization.CultureInfo.InvariantCulture), receipt.AddedTemplates.ToString(System.Globalization.CultureInfo.InvariantCulture) };
            V3ProposalContract.Require(proof.GetProperty("ProbeCommand").Deserialize<string[]>()!.SequenceEqual(expectedProbe), "PROOF_ISOLATED_RUN");
        }
        var logs = proof.GetProperty("Logs").Deserialize<List<SourceFileStamp>>()!;
        string[] expectedLogs = isolated ? ["probe.txt", "negative.txt", "negative-schema.txt", "probe-compile.txt", "java-version.txt"]
            : ["ant-content.txt", "probe.txt", "negative.txt", "negative-schema.txt", "probe-compile.txt", "java-version.txt", "ant-version.txt"];
        V3ProposalContract.Require(logs.Count == expectedLogs.Length && logs.Select(l => l.RelativePath).Order().SequenceEqual(expectedLogs.Order()), "PROOF_LOGS");
        var verifiedLogs = new Dictionary<string, string>(StringComparer.Ordinal);
        foreach (var log in logs)
        {
            token.ThrowIfCancellationRequested();
            var bytes = V3ProposalContract.ReadStamped(PathSafety.ResolveRelative(oracle, log.RelativePath), log);
            verifiedLogs.Add(log.RelativePath, V3ProposalContract.Utf8.GetString(bytes).TrimStart('\uFEFF'));
        }
        var probeLines = verifiedLogs["probe.txt"].Split('\n').Select(line => line.TrimEnd('\r')).ToArray();
        foreach (var c in receipt.Candidates.Where(c => c.Kind == "TEMPLATE" && c.Voice != "" && (c.Facet ?? "") == ""))
            V3ProposalContract.Require(probeLines.Count(line => line == $"VOICE_SELECT id={c.XmlId} voice={c.Voice} positive=CHECKED wrongVoice=CHECKED universal=CHECKED") == 1, "PROOF_VOICE_SELECT");
        if (version == 3) foreach (var c in receipt.Candidates.Where(c => c.Facet != ""))
            V3ProposalContract.Require(probeLines.Count(line => line == $"FACET_SELECT id={c.XmlId} facet={c.Facet} minIntensity={c.MinIntensity} positive=CHECKED legacy=CHECKED threshold={(c.MinIntensity == 0 ? "ZERO_ALWAYS_ELIGIBLE" : "CHECKED")} wrongFacet={(c.MinIntensity == 0 ? "NOT_APPLICABLE" : "CHECKED")} voice={(c.Voice == "" ? "UNVOICED" : "CHECKED")} deterministic=CHECKED") == 1, "PROOF_FACET_SELECT");
        var evidence = S("ProbeEvidence");
        var match = Regex.Match(evidence, "^PASS_JAVA_STAGED_V3 baselinePatterns=(\\d+) baselineTemplates=(\\d+) stagedPatterns=(\\d+) stagedTemplates=(\\d+) checkedIds=(\\d+) baselineHash=([0-9a-f]{64}) stagedHash=([0-9a-f]{64}) negative=DUPLICATE_AND_SCHEMA_REJECTED$");
        V3ProposalContract.Require(match.Success && int.Parse(match.Groups[1].Value) == receipt.BaselinePatterns && int.Parse(match.Groups[2].Value) == receipt.BaselineTemplates
            && int.Parse(match.Groups[3].Value) == receipt.BaselinePatterns + receipt.AddedPatterns && int.Parse(match.Groups[4].Value) == receipt.BaselineTemplates + receipt.AddedTemplates
            && int.Parse(match.Groups[5].Value) == receipt.Candidates.Count && match.Groups[6].Value != match.Groups[7].Value, "PROOF_COUNTERS");
        V3ProposalContract.Require(probeLines.Count(line => line == evidence) == 1
            && verifiedLogs["negative.txt"].Contains("REJECTED_V3_NEGATIVE", StringComparison.Ordinal)
            && verifiedLogs["negative-schema.txt"].Contains("REJECTED_V3_NEGATIVE", StringComparison.Ordinal)
            && (isolated ? probeLines.Count(line => line == "NORMALIZED_DUPLICATE distinctId=CHECKED unicodeNormalization=CHECKED rejected=CHECKED") == 1
                : verifiedLogs["ant-content.txt"].Contains("BUILD SUCCESSFUL", StringComparison.Ordinal)), "PROOF_NATIVE_LOGS");
        token.ThrowIfCancellationRequested(); V3ProposalContract.Require(TextRules.Hash(File.ReadAllBytes(path)) == proofHash, "PROOF_DRIFT"); return proofHash;
    }
}
