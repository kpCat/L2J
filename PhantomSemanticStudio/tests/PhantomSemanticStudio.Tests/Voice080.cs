using System.Text.Json;
using System.Xml.Linq;
using PhantomSemanticStudio.Core;

internal static partial class Program
{
    private static void Voice080()
    {
        Test("080 V3 voiced source remains editable and stageable", () =>
        {
            using var f = new Fixture();
            var file = Path.Combine(f.Module, "dist/game/data/phantoms/conversation/humanized/v3/segments/test.xml");
            File.WriteAllText(file, File.ReadAllText(file).Replace("<template ", "<template voice=\"calm\" "));
            var raw = File.ReadAllText(file).Replace("text=\"Добрый", "text=\"  Добрый"); File.WriteAllText(file, raw);
            var pack = new PackReader().Load(f.Module);
            Equal(1, V3ProposalContract.GetPairs(pack).Count);
            var entry = pack.Entries.First(e => e.SourceFile.EndsWith("v3/segments/test.xml") && e.Kind == "TEMPLATE");
            Equal("calm", entry.Voice); True(entry.Text.StartsWith("  Добрый", StringComparison.Ordinal));
            True(entry.Act == "greet.reply" && entry.Band == "UNKNOWN" && entry.Register == "NEUTRAL" && entry.SourceLine > 0);
            foreach (var attribute in new[] { "voice=\"CALM\"", "voice=\"unknown\"", "voice=\"\"", "VOICE=\"calm\"", "voices=\"calm\"" })
            {
                File.WriteAllText(file, raw.Replace("voice=\"calm\"", attribute));
                Throws(() => V3ProposalContract.GetPairs(new PackReader().Load(f.Module)));
            }
            File.WriteAllText(file, raw); var sourceHash = pack.Files.Single(s => s.RelativePath == entry.SourceFile).Sha256;
            Equal(TextRules.Hash(File.ReadAllBytes(file)), sourceHash);
            Throws(() => V3ProposalContract.GetPairs(pack with { Entries = pack.Entries.Where(e => e.Kind != "TEMPLATE" || e.Voice != "").ToList() }));
            foreach (var voice in new[] { "direct", "" })
            {
                f.SetCustom($"<phrases version=\"1\"><template id=\"legacy.voice\" act=\"greet.reply\" voice=\"{voice}\" text=\"Запрещённые метаданные\"/></phrases>");
                Throws(() => new PackReader().Load(f.Module));
            }
            f.SetCustom("<phrases version=\"1\"/>");
            File.WriteAllText(f.Segment, File.ReadAllText(f.Segment).Replace("<pattern ", "<pattern voice=\"direct\" "));
            Throws(() => new PackReader().Load(f.Module));
        });
        Test("080 old JSON universal strict voice and review invalidation", () =>
        {
            using var f = new Fixture(); var pack = new PackReader().Load(f.Module); var c = StageCandidates(pack)[1];
            var oldJson = JsonSerializer.Serialize(c).Replace("\"Voice\":\"\",", "");
            var old = JsonSerializer.Deserialize<Candidate>(oldJson)!; Equal("", old.Voice);
            foreach (var voice in CandidateValidator.Voices)
            {
                var copy = c.Copy(); copy.Voice = voice;
                True(!CandidateValidator.Validate(copy, pack, [copy]).Any(i => i.Severity == IssueSeverity.Error));
            }
            foreach (var voice in new[] { "DIRECT", "direct ", "invented", null })
            {
                var copy = c.Copy(); copy.Voice = voice!;
                True(CandidateValidator.Validate(copy, pack, [copy]).Any(i => i.Code == "VOICE" && i.Severity == IssueSeverity.Error));
            }
            var pattern = StageCandidates(pack)[0]; pattern.Voice = "calm";
            True(CandidateValidator.Validate(pattern, pack, [pattern]).Any(i => i.Code == "VOICE"));
            var approved = c.ApprovedFingerprint; var advice = SemanticDuplicateScout.CandidateFingerprint(c);
            c.Voice = "direct"; True(!CandidateReview.IsCurrent(c)); True(advice != SemanticDuplicateScout.CandidateFingerprint(c));
            CandidateReview.Edit(c, c.Text, "calm"); Equal("DRAFT", c.Status); Equal("", c.ApprovedFingerprint); True(c.ReviewedAtUtc == null && c.SemanticReview == null);
            CandidateReview.Approve(c, pack, [c], "Повторная ручная проверка голоса"); True(CandidateReview.IsCurrent(c)); True(c.ApprovedFingerprint != approved);
            using var store = new WorkspaceStore(Path.Combine(f.Root, "workspace"), f.Module);
            store.SaveSession(new SessionState { Candidates = [c] }); Equal("calm", store.LoadSession().Candidates.Single().Voice);
            var bad = c.Copy(); bad.Voice = "DIRECT"; Throws(() => store.SaveSession(new SessionState { Candidates = [bad] }));
            File.WriteAllBytes(Path.Combine(store.Root, "session.json"), JsonSerializer.SerializeToUtf8Bytes(new SessionState { Candidates = [bad] }));
            Throws(() => store.LoadSession());
            Throws(() => new IsolatedPackStager().Create(store, pack, [c], [c.Id], true, true));
        });
        Test("080 mixed V3 voice XML receipt exact hashes and stale guards", () =>
        {
            using var f = new Fixture(); using var store = new WorkspaceStore(Path.Combine(f.Root, "workspace"), f.Module);
            var pack = new PackReader().Load(f.Module); var peers = StageCandidates(pack);
            peers[1].Voice = "direct"; CandidateReview.Approve(peers[1], pack, peers, "Голос проверен вручную");
            var generic = CandidateOf(pack, "Вечерняя тропинка уходит к далёкому маяку."); generic.Id = new string('3', 32);
            peers.Add(generic); CandidateReview.Approve(generic, pack, peers, "Универсальный ответ проверен вручную");
            var pair = V3ProposalContract.GetPairs(pack).Single();
            var stage = new IsolatedV3ProposalStager().Create(store, pack, peers, peers.Select(c => c.Id).ToArray(), pair, true, true);
            var receipt = V3ProposalContract.ReadStage(store.Root, stage.Root); Equal(2, receipt.Version);
            Equal("direct", receipt.Candidates.Single(c => c.Id == peers[1].Id).Voice);
            var xml = XDocument.Load(Path.Combine(stage.ModuleRoot, "dist/game/data/phantoms", pair.ConversationPath));
            Equal("direct", (string)xml.Descendants("template").Single(e => (string?)e.Attribute("id") == "pss.v3.t." + peers[1].Id).Attribute("voice")!);
            True(xml.Descendants("template").Single(e => (string?)e.Attribute("id") == "pss.v3.t." + generic.Id).Attribute("voice") == null);
            foreach (var stamp in pack.Files.Where(s => s.RelativePath != pair.SemanticPath && s.RelativePath != pair.ConversationPath))
                Equal(stamp, receipt.StagedFiles.Single(s => s.RelativePath == stamp.RelativePath));
            var receiptPath = Path.Combine(stage.Root, "receipt.json"); var original = File.ReadAllBytes(receiptPath);
            var tampered = receipt with { Candidates = receipt.Candidates.Select(c => c.Id == peers[1].Id ? c with { Voice = "calm" } : c).ToList() };
            File.WriteAllBytes(receiptPath, JsonSerializer.SerializeToUtf8Bytes(tampered, WorkspaceStore.JsonOptions));
            Throws(() => V3ProposalContract.ReadStage(store.Root, stage.Root)); File.WriteAllBytes(receiptPath, original);
            peers[1].Voice = "empathetic";
            Throws(() => new IsolatedV3ProposalStager().Create(store, pack, peers, [peers[1].Id], pair, true, true));
            True(pack.Files.SequenceEqual(new PackReader().Load(f.Module).Files));
            File.AppendAllText(f.Segment, "\n"); Throws(() => V3ProposalContract.ReadStage(store.Root, stage.Root));
        });
    }
}
