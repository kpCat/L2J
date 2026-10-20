using System.Text;
using System.Text.Json;
using PhantomSemanticStudio.Core;

internal static partial class Program
{
    private static void Pss010()
    {
        using var f = new Pss010Fixture();
        var pack = new PackReader().Load(f.Module);
        Pss010UserPhrases(pack, "synthetic v1");
        Pss010Safety(pack, f);
        Test("PSS-010 captured value raw/render contract prefix and suffix", () =>
        {
            foreach (var pattern in new[] { "люблю {value}", "{value} мне нравится" })
            {
                var input = pattern.StartsWith("люблю", StringComparison.Ordinal) ? "Люблю КНИГИ!" : "КНИГИ мне нравится";
                var scoped = pack with { Entries = [
                    new PackEntry { Kind = "PATTERN", Id = "value.pattern", Text = pattern, Act = "interest.share", Topic = "interest" },
                    new PackEntry { Kind = "TEMPLATE", Id = "value.01", Text = "Интерес: {value}.", Act = "interest.share" }
                ] };
                var preview = new PackPreview().Reply(scoped, input, "UNKNOWN", "NEUTRAL");
                Equal("Интерес: книги.", preview.Text);
                var turn = Pss010Turn(new DialogueLabSession(scoped), scoped, input);
                Console.WriteLine($"VALUE {turn.PackStatus} {turn.PatternId}/{turn.TemplateId} text={turn.PackText}");
                Equal("PACK_CATALOG_APPROXIMATE", turn.PackStatus); Equal(preview.Text, turn.PackText);
                Equal(input, turn.UserText); Equal("value.01", turn.TemplateId);
            }
        });
    }
    private static void Pss010Safety(PackSnapshot pack, Pss010Fixture f)
    {
        Test("PSS-010 unavailable and unknown placeholders retain pattern without fake values", () =>
        {
            foreach (var text in new[] { "Имя: {name}", "Интерес: {interest}", "Память: {memory}", "Неизвестно: {unknown}", "Значение: {value}", "", "Текст {" })
            {
                var scoped = pack with { Entries = [pack.Entries.Single(e => e.Id == "greeting.hello"),
                    new PackEntry { Kind = "TEMPLATE", Id = "only.01", Act = "greet.reply", Text = text }] };
                var preview = new PackPreview().Reply(scoped, "привет", "UNKNOWN", "NEUTRAL");
                var turn = Pss010Turn(new DialogueLabSession(scoped), scoped, "привет");
                Equal("TEMPLATE_CONTEXT_UNAVAILABLE", turn.PackStatus); Equal("", preview.Text); Equal("", turn.PackText);
                Equal("greeting.hello", turn.PatternId); Equal("greet.reply", turn.Act); Equal("greeting", turn.Topic);
                Equal("", turn.TemplateId); True(preview.Matched);
            }
        });
        Test("PSS-010 functional identity Fact Recall stay unsupported even with literals", () =>
        {
            foreach (var p in new[] {
                new PackEntry { Act = "support.help" }, new PackEntry { Act = "identity.name" },
                new PackEntry { Act = "greet.reply", Fact = "interest" }, new PackEntry { Act = "greet.reply", Recall = "interest" } })
            {
                var pattern = p with { Kind = "PATTERN", Id = "blocked.pattern", Text = "проверка", Topic = "greeting" };
                var scoped = pack with { Entries = [pattern, new PackEntry { Kind = "TEMPLATE", Id = "blocked.literal", Act = p.Act, Text = "Буквальный текст." }] };
                var preview = new PackPreview().Reply(scoped, "проверка", "UNKNOWN", "NEUTRAL");
                var turn = Pss010Turn(new DialogueLabSession(scoped), scoped, "проверка");
                Equal("FUNCTIONAL_OR_MEMORY_UNSUPPORTED", turn.PackStatus); Equal("", turn.PackText); Equal("", preview.Text);
                Equal(pattern.Id, turn.PatternId); Equal("", turn.TemplateId);
            }
        });
        Test("PSS-010 band register mature profanity and no eligible template gates", () =>
        {
            var pattern = pack.Entries.Single(e => e.Id == "greeting.hello");
            var neutral = new PackEntry { Kind = "TEMPLATE", Id = "gated.09", Act = "greet.reply", Text = "Нейтральный ответ." };
            var scoped = pack with { Entries = [pattern,
                neutral with { Id = "gated.01", Mature = true }, neutral with { Id = "gated.02", Profanity = "MILD" },
                neutral with { Id = "gated.03", Band = "FAMILIAR", Register = "CASUAL", Text = "Ответ знакомому." },
                neutral with { Id = "gated.04", Register = "UNKNOWN_STYLE" }, neutral] };
            Equal("gated.09", Pss010Turn(new DialogueLabSession(scoped), scoped, "привет").TemplateId);
            Equal("gated.09", Pss010Turn(new DialogueLabSession(scoped), scoped, "привет", "UNKNOWN", "CASUAL").TemplateId);
            Equal("gated.09", Pss010Turn(new DialogueLabSession(scoped), scoped, "привет", "FAMILIAR", "NEUTRAL").TemplateId);
            Equal("gated.03", Pss010Turn(new DialogueLabSession(scoped), scoped, "привет", "FAMILIAR", "CASUAL").TemplateId);
            scoped = scoped with { Entries = scoped.Entries.Where(e => e.Id != "gated.09").ToList() };
            var turn = Pss010Turn(new DialogueLabSession(scoped), scoped, "привет");
            Equal("NO_ELIGIBLE_TEMPLATE", turn.PackStatus); Equal("greeting.hello", turn.PatternId); Equal("", turn.PackText);
        });
        Test("PSS-010 capture never invents aliases values or leaves placeholder tokens", () =>
        {
            foreach (var input in new[] { "люблю {name}", "люблю {value}", "люблю", "люблю {unknown}" })
            {
                var scoped = pack with { Entries = [
                    new PackEntry { Kind = "PATTERN", Id = "capture.pattern", Text = "люблю {value}", Act = "interest.share" },
                    new PackEntry { Kind = "TEMPLATE", Id = "capture.01", Text = "Интерес: {value}.", Act = "interest.share" }] };
                var turn = Pss010Turn(new DialogueLabSession(scoped), scoped, input);
                Equal("", turn.PackText); True(turn.PackStatus != "PACK_CATALOG_APPROXIMATE");
            }
            var aliased = pack with { Entries = [
                new PackEntry { Kind = "ALIAS", Id = "книги", Text = "выдуманный интерес" },
                new PackEntry { Kind = "PATTERN", Id = "alias.pattern", Text = "люблю {value}", Act = "interest.share" },
                new PackEntry { Kind = "TEMPLATE", Id = "alias.01", Text = "Интерес: {value}.", Act = "interest.share" }] };
            Equal("", Pss010Turn(new DialogueLabSession(aliased), aliased, "люблю книги").PackText);
            Equal("", new PackPreview().Reply(aliased, "люблю книги", "UNKNOWN", "NEUTRAL").Text);
            var shadowed = pack with { Entries = [
                new PackEntry { Kind = "ALIAS", Id = "я", Text = "книги" },
                new PackEntry { Kind = "PATTERN", Id = "shadow.pattern", Text = "книги {value}", Act = "interest.share" },
                new PackEntry { Kind = "TEMPLATE", Id = "shadow.01", Text = "Интерес: {value}.", Act = "interest.share" }] };
            // A transformed capture occurring elsewhere in input is still not the actual pattern-branch capture.
            Equal("", new PackPreview().Reply(shadowed, "книги я", "UNKNOWN", "NEUTRAL").Text);
            Equal("TEMPLATE_CONTEXT_UNAVAILABLE", Pss010Turn(new DialogueLabSession(shadowed), shadowed, "книги я").PackStatus);
        });
        Test("PSS-010 repeat fallback only displayed ids and transactional cancel stale queue", () =>
        {
            var scoped = pack with { Entries = pack.Entries.Append(new PackEntry { Kind = "TEMPLATE", Id = "greet.03", Act = "greet.reply", Text = "Рад встрече." }).ToList() };
            var lab = new DialogueLabSession(scoped);
            var abandoned = lab.PrepareTurn("привет", "UNKNOWN", "NEUTRAL");
            Equal("greet.02", abandoned.Turn.TemplateId); Equal(0, lab.Turns.Count);
            using var cancel = new CancellationTokenSource(); cancel.Cancel();
            Throws(() => lab.CommitTurn(abandoned, scoped, cancel.Token)); Equal(0, lab.Turns.Count);
            Equal("greet.02", Pss010Turn(lab, scoped, "привет").TemplateId);
            Throws(() => lab.CommitTurn(abandoned, scoped));
            var second = lab.PrepareTurn("привет", "UNKNOWN", "NEUTRAL"); Equal("greet.03", second.Turn.TemplateId);
            lab.SetWorld("GAME"); Throws(() => lab.CommitTurn(second, scoped));
            Equal("greet.03", Pss010Turn(lab, scoped, "привет").TemplateId);
            var repeat = Pss010Turn(lab, scoped, "привет");
            Equal("greet.02", repeat.TemplateId); Equal("PACK_CATALOG_APPROXIMATE", repeat.PackStatus);
            True(repeat.Notes.Contains("REPEAT_FALLBACK", StringComparison.Ordinal));
            lab.Clear(); Equal("greet.02", Pss010Turn(lab, scoped, "привет").TemplateId);
            var inspector = new PackPreview();
            var unavailable = scoped with { Entries = scoped.Entries.Where(e => e.Kind != "TEMPLATE" || e.Id == "greet.01").ToList() };
            Equal("", inspector.Reply(unavailable, "привет", "UNKNOWN", "NEUTRAL").Text);
            Equal("greet.02", inspector.Reply(scoped, "привет", "UNKNOWN", "NEUTRAL").TemplateId);
        });
        Test("PSS-010 source drift cancels commit fingerprint and files independently", () =>
        {
            foreach (var changed in new[] { pack with { Fingerprint = new string('a', 64) }, pack with { Files = [] } })
            {
                var lab = new DialogueLabSession(pack); var proposal = lab.PrepareTurn("привет", "UNKNOWN", "NEUTRAL");
                Throws(() => lab.CommitTurn(proposal, changed)); Equal(0, lab.Turns.Count); Equal("", lab.Transcript);
                Equal("STALE_SOURCE", lab.SourceStatus); Throws(() => lab.PrepareTurn("привет", "UNKNOWN", "NEUTRAL"));
            }
        });
        Test("PSS-010 dialogue does not alter source session approvals or model roles", () =>
        {
            using var ws = new WorkspaceStore(Path.Combine(f.Root, "workspace"), f.Module);
            var candidate = CandidateOf(pack, "Сегодня есть время поговорить."); CandidateReview.Approve(candidate, pack, [], "Ручная проверка");
            ws.SaveSession(new SessionState { Candidates = [candidate] });
            var before = File.ReadAllBytes(Path.Combine(ws.Root, "session.json")); var source = JsonSerializer.Serialize(pack);
            var files = Directory.GetFiles(f.Root, "*", SearchOption.AllDirectories).Order(StringComparer.Ordinal).ToArray();
            var lab = new DialogueLabSession(pack);
            foreach (var input in new[] { "привет", "как дела", "меня слили в пвп" }) Pss010Turn(lab, pack, input);
            Equal(source, JsonSerializer.Serialize(pack)); Equal(pack.Fingerprint, new PackReader().Load(f.Module).Fingerprint);
            True(before.SequenceEqual(File.ReadAllBytes(Path.Combine(ws.Root, "session.json")))); True(CandidateReview.IsCurrent(candidate));
            True(files.SequenceEqual(Directory.GetFiles(f.Root, "*", SearchOption.AllDirectories).Order(StringComparer.Ordinal)));
            True(lab.Messages.All(m => m.Role is "YOU / ВЫ" or "PACK / ПАК"));
            Console.WriteLine("MODEL_OFF POST=0; no transport attached; source/session/approval unchanged");
        });
    }
    private static int Pss010Source(string[] args)
    {
        if (args.Length != 2) throw new ArgumentException("--pss-010-source <HighFive module root>");
        var module = PathSafety.Canonical(args[1]); PathSafety.AssertNoReparsePoints(module);
        var data = Path.Combine(module, "dist/game/data/phantoms");
        var paths = new[] { "semantic/humanized/high-five-ru-humanized-semantic-v1.xml", "conversation/humanized/high-five-ru-humanized-conversation-v1.xml" };
        using var f = new Pss010Fixture();
        var before = new List<SourceFileStamp>();
        foreach (var relative in paths)
        {
            var bytes = File.ReadAllBytes(PathSafety.ResolveRelative(data, relative));
            if (bytes.Length is < 1 or > 262144) throw new InvalidDataException("Unexpected v1 input size.");
            before.Add(new SourceFileStamp(relative, TextRules.Hash(bytes), bytes.Length)); f.Write(relative, bytes);
        }
        try
        {
            var pack = new PackReader().Load(f.Module);
            Console.WriteLine("ACTUAL_V1_ONLY; two exact XML byte copies; no full 65-file catalog or Java parity claim");
            Pss010UserPhrases(pack, "actual readonly v1");
        }
        finally
        {
            foreach (var stamp in before)
            {
                var bytes = File.ReadAllBytes(PathSafety.ResolveRelative(data, stamp.RelativePath));
                Equal(stamp.Bytes, (long)bytes.Length); Equal(stamp.Sha256, TextRules.Hash(bytes));
                Console.WriteLine($"SOURCE_UNCHANGED {stamp.RelativePath} bytes={stamp.Bytes} sha256={stamp.Sha256}");
            }
        }
        Console.WriteLine($"RESULT: {passed} PASS; {failed} FAIL"); return failed == 0 ? 0 : 1;
    }
    private static void Pss010UserPhrases(PackSnapshot pack, string label)
    {
        foreach (var c in new[] {
            (Input: "привет", Pattern: "greeting.hello", Template: "greet.02", Text: "Привет! Как ты сегодня?"),
            (Input: "как дела", Pattern: "mood.ask", Template: "mood.share.02", Text: "Неплохо, спасибо. А у тебя как?") })
        {
            Test($"PSS-010 {label} {c.Input} safe second template public Core flow", () =>
            {
                var preview = new PackPreview().Reply(pack, c.Input, "UNKNOWN", "NEUTRAL");
                var turn = Pss010Turn(new DialogueLabSession(pack), pack, c.Input);
                Console.WriteLine($"TRACE {c.Input} preview={preview.PatternId}/{preview.TemplateId} text={preview.Text}; lab={turn.PackStatus} {turn.PatternId}/{turn.TemplateId} text={turn.PackText}");
                Equal("PACK_CATALOG_APPROXIMATE", turn.PackStatus);
                Equal(c.Pattern, turn.PatternId); Equal(c.Template, turn.TemplateId); Equal(c.Text, turn.PackText);
                Equal(c.Template, preview.TemplateId); Equal(c.Text, preview.Text); True(preview.Matched);
                Equal(pack.Fingerprint, turn.PackFingerprint);
                True(turn.Notes.Contains("NOT_JAVA_RUNTIME_PARITY", StringComparison.Ordinal));
                True(turn.Notes.Contains("WORLD_ADVISORY_ONLY", StringComparison.Ordinal));
            });
        }
        Test($"PSS-010 {label} legitimate no-match and advisory Cyrillic pvp", () =>
        {
            var turn = Pss010Turn(new DialogueLabSession(pack), pack, "меня слили в пвп");
            Console.WriteLine($"TRACE {turn.UserText} lab={turn.PackStatus} {turn.PatternId}/{turn.TemplateId} world={turn.WorldHint} text={turn.PackText}");
            Equal("NO_PACK_MATCH", turn.PackStatus); Equal("", turn.PackText);
            Equal("", turn.PatternId); Equal("", turn.TemplateId); Equal("GAME", turn.WorldHint);
            Equal("меня слили в пвп", turn.UserText);
        });
    }
    private static DialogueTurn Pss010Turn(DialogueLabSession lab, PackSnapshot pack, string input, string band = "UNKNOWN", string register = "NEUTRAL")
        => lab.CommitTurn(lab.PrepareTurn(input, band, register), pack);
    private sealed class Pss010Fixture : IDisposable
    {
        public string Root { get; } = Path.Combine(Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "../../../../../")), "artifacts/PSS-010/fixture-" + Guid.NewGuid().ToString("N"));
        public string Module => Path.Combine(Root, "source");
        public Pss010Fixture()
        {
            Directory.CreateDirectory(Module);
            File.WriteAllText(Path.Combine(Module, ".git"), "synthetic source boundary");
            Write("semantic/humanized/high-five-ru-humanized-semantic-v1.xml", Encoding.UTF8.GetBytes("""
                <humanizedSemanticPack version="1"><patterns>
                <pattern id="greeting.hello" topic="greeting" act="greet.reply" phrase="привет" priority="900"/>
                <pattern id="mood.ask" topic="mood" act="mood.share" phrase="как дела" priority="850"/>
                </patterns></humanizedSemanticPack>
                """));
            Write("conversation/humanized/high-five-ru-humanized-conversation-v1.xml", Encoding.UTF8.GetBytes("""
                <humanizedConversationPack version="1"><templates>
                <template id="greet.01" act="greet.reply" text="Привет, {name}. Рад тебя видеть."/>
                <template id="greet.02" act="greet.reply" text="Привет! Как ты сегодня?"/>
                <template id="mood.share.01" act="mood.share" text="В целом спокойно. Сегодня хочется поговорить про {interest}."/>
                <template id="mood.share.02" act="mood.share" text="Неплохо, спасибо. А у тебя как?"/>
                </templates></humanizedConversationPack>
                """));
            Write("conversation/humanized/high-five-ru-persona-v1.xml", Encoding.UTF8.GetBytes("<humanizedPersonaPack version=\"1\"><interests/></humanizedPersonaPack>"));
            Write("semantic/humanized/high-five-ru-humanized-corpus-v1.tsv", Encoding.UTF8.GetBytes("case\tinput\n"));
        }
        public void Write(string relative, byte[] bytes)
        {
            var path = PathSafety.ResolveRelative(Path.Combine(Module, "dist/game/data/phantoms"), relative);
            Directory.CreateDirectory(Path.GetDirectoryName(path)!); File.WriteAllBytes(path, bytes);
        }
        public void Dispose()
        {
            var owned = Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "../../../../../artifacts/PSS-010"));
            if (!PathSafety.IsWithin(Root, owned) || Root == owned) throw new InvalidDataException("Fixture outside owned root.");
            Directory.Delete(Root, true);
        }
    }
}
