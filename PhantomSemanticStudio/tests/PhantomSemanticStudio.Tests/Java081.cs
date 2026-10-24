using System.Security.Cryptography;
using System.Text;
using System.Text.Json.Nodes;
using PhantomSemanticStudio.Core;

internal static partial class Program
{
    private static void Java081()
    {
        Test("081 isolated proof accepts only the audited production dependency closure", () =>
        {
            SourceFileStamp[] inputs =
            [
                new("java/org/l2jmobius/gameserver/phantoms/conversation/humanized/PhantomHumanizedCatalog.java", "493d1010e653f66b2e1f3407914735a4ce5a370f8dd7d0d5349fbccb0fb76ef0", 1),
                new("java/org/l2jmobius/gameserver/model/actor/enums/player/PlayerClass.java", "9678b970528f17192114669ad96b8538add396ec251f4fbc4476d57c1e64a2cf", 1),
                new("java/org/l2jmobius/gameserver/model/actor/enums/creature/Race.java", "7cd9a7ba39b483b93c6fd47b1ef4572dc526fb0a06419bf918fafc823c5cbc0b", 1)
            ];
            V3ReleaseHandoff.ValidateIsolatedInventory(inputs);
            Throws(() => V3ReleaseHandoff.ValidateIsolatedInventory(inputs[..2]));
            Throws(() => V3ReleaseHandoff.ValidateIsolatedInventory([.. inputs, new("build.xml", new string('a', 64), 1)]));
            Throws(() => V3ReleaseHandoff.ValidateIsolatedInventory([inputs[0], inputs[1], inputs[1]]));
            Throws(() => V3ReleaseHandoff.ValidateIsolatedInventory([inputs[0] with { Sha256 = new string('a', 64) }, inputs[1], inputs[2]]));
            Throws(() => V3ReleaseHandoff.ValidateIsolatedInventory([inputs[0] with { Bytes = 1048577 }, inputs[1], inputs[2]]));
        });
    }

    private static int Java081Proof(string[] args)
    {
        if (args.Length != 2) throw new ArgumentException("--java081-proof <genuine synthetic081 proof>");
        var proof = PathSafety.Canonical(args[1]); var oracle = Path.GetDirectoryName(proof)!;
        var stage = Path.GetDirectoryName(oracle)!; var workspace = Path.GetDirectoryName(Path.GetDirectoryName(stage))!;
        var receipt = V3ProposalContract.ReadStage(workspace, stage);
        var originalProof = File.ReadAllBytes(proof); var body = JsonNode.Parse(originalProof)!;
        Equal(2, body["Version"]!.GetValue<int>()); Equal("NOT_RUN", body["Ant"]!.GetValue<string>());
        // The existing route restricts operator actions to the exact three synthetic fixture text hashes.
        Equal(0, Pss008Handoff(["--pss-008-handoff", proof, "--synthetic-forgery-negative"]));
        using (var store = new WorkspaceStore(workspace, receipt.SourceModule))
        {
        Test("081 authentic isolated proof rejects copied production source drift", () =>
        {
            var source = Path.Combine(oracle, "module/java/org/l2jmobius/gameserver/model/actor/enums/player/PlayerClass.java");
            var saved = File.ReadAllBytes(source);
            try { File.AppendAllText(source, "\n// synthetic081 drift\n"); Throws(() => V3ReleaseHandoff.InspectProof(store, stage, proof)); }
            finally { File.WriteAllBytes(source, saved); }
            Equal(TextRules.Hash(originalProof), V3ReleaseHandoff.InspectProof(store, stage, proof));
        });
        Test("081 authenticated proof cannot relabel isolated compilation as an Ant run", () =>
        {
            var attestationPath = Path.Combine(oracle, "java-attestation.json"); var saved = File.ReadAllBytes(attestationPath);
            var key = File.ReadAllBytes(Path.Combine(workspace, "java-attestations", receipt.StageId + ".key"));
            try
            {
                body["AntExitCode"] = 0; body["Ant"] = "Apache Ant forged";
                File.WriteAllText(proof, body.ToJsonString()); var hash = TextRules.Hash(File.ReadAllBytes(proof));
                var attestation = JsonNode.Parse(saved)!; attestation["ProofSha256"] = hash;
                attestation["Mac"] = Convert.ToHexString(HMACSHA256.HashData(key, Encoding.UTF8.GetBytes("PSS008_NATIVE_JAVA_V2\n" + receipt.StageId + "\n" + hash))).ToLowerInvariant();
                File.WriteAllText(attestationPath, attestation.ToJsonString());
                Throws(() => V3ReleaseHandoff.InspectProof(store, stage, proof));
            }
            finally { CryptographicOperations.ZeroMemory(key); File.WriteAllBytes(proof, originalProof); File.WriteAllBytes(attestationPath, saved); }
            Equal(TextRules.Hash(originalProof), V3ReleaseHandoff.InspectProof(store, stage, proof));
        });
        }
        Equal(0, Pss008Handoff(["--pss-008-handoff", proof, "--synthetic-operator-review"]));
        Console.WriteLine($"RESULT: {passed} PASS; {failed} FAIL"); return failed == 0 ? 0 : 1;
    }
}
