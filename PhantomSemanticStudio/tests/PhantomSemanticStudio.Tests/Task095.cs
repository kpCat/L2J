using System.Text;
using PhantomSemanticStudio.Core;

internal static partial class Program
{
    private static void Task095()
    {
        var root = Path.Combine(Path.GetTempPath(), "pw095-" + Guid.NewGuid().ToString("N"));
        var source = Path.Combine(root, "source"); var workspace = Path.Combine(root, "workspace");
        Directory.CreateDirectory(source); Directory.CreateDirectory(Path.Combine(root, "input"));
        var utf8 = new UTF8Encoding(false, true);
        string Batch(string record, string mode = "REPLY", int count = 1) =>
            "{\"schema\":\"pw-semantic-batch/1\",\"category\":\"hybrid.trade.absurd_offers\",\"mode\":\"" + mode
            + "\",\"act\":\"hybrid.trade.absurd_offers.reply\",\"language\":\"ru\",\"count\":" + count + "}\n" + record + "\n";
        const string reply = "{\"text\":\"За такой лот ещё полгорода попроси.\",\"stance\":\"AMUSED\"}";
        var file = Path.Combine(root, "input", "one.jsonl");
        void Write(string data) => File.WriteAllText(file, data, utf8);
        try
        {
            Test("TASK095 strict JSONL rejects malformed metadata and unsafe Unicode", () =>
            {
                var store = new V4Workspace(workspace, source); Write(Batch(reply));
                True(store.Validate(file).Success);
                var negatives = new[] {
                    Batch("{\"text\":\"" + string.Concat(Enumerable.Repeat("🙂", 61)) + "\",\"stance\":\"NEUTRAL\"}"),
                    Batch("{\"text\":\"Привет, {name}.\",\"intent\":\"hybrid.trade.absurd_offers.observe\"}", "TRIGGER"),
                    "\uFEFF" + Batch(reply), Batch(reply).Replace("\n", "\r\n"), "```json\n" + Batch(reply),
                    Batch(reply, count: 2), Batch(reply.Replace("AMUSED", "HAPPY")), Batch(reply).Replace("absurd_offers\",", "unknown_leaf\","),
                    Batch(reply.Replace("\"stance\"", "\"unknown\"")), Batch(reply.Replace("\"text\":", "\"text\":\"лишний\",\"text\":")),
                    Batch(reply.Replace("полгорода", "{secret}")), Batch(reply.Replace("полгорода", "<script>")),
                    Batch(reply.Replace("полгорода", "\\u0000")), Batch(reply.Replace("полгорода", "\\ud800")),
                    Batch(reply.Replace("}", ",\"voices\":[\"invented\"]}")),
                    Batch(reply.Replace("}", ",\"facets\":{\"invented\":10}}")),
                    Batch(reply.Replace("}", ",\"weight\":11}")),
                    Batch("{\"text\":\"Победа!\",\"stance\":\"AMUSED\",\"requiredEvent\":\"invented.kill\"}", "INITIATIVE") };
                foreach (var bad in negatives) { Write(bad); True(!store.Validate(file).Success); }
                File.WriteAllBytes(file, [0xff, 0xfe]); True(!store.Validate(file).Success);
                Write(Batch(reply.Replace("AMUSED", "HAPPY"))); Equal(2, store.Validate(file).Diagnostics[0].Line);
                Write(Batch(reply).Replace("\"REPLY\"", "\"UNKNOWN_MODE\"")); Equal(1, store.Validate(file).Diagnostics[0].Line);
                Write("\uFEFF" + Batch(reply)); Equal(1, store.Validate(file).Diagnostics[0].Line);
                Write(Batch(reply + "\n" + reply, count: 2)); True(!store.Validate(file).Success);
                Throws(() => new V4Workspace(Path.Combine(source, "forbidden"), source));
                var actualRepository = PathSafety.RepositoryBoundary(AppContext.BaseDirectory);
                if (Directory.Exists(Path.Combine(actualRepository, ".git")) || File.Exists(Path.Combine(actualRepository, ".git")))
                    Throws(() => new V4Workspace(Path.Combine(actualRepository, "forbidden-task095-workspace"), source));
            });
            Test("TASK095 import is idempotent, cross-file atomic and stage verification catches tampering", () =>
            {
                var store = new V4Workspace(workspace, source); Write(Batch(reply));
                var dry = store.Import(file, true); Equal(1, dry.Accepted); Equal(0, store.Stats().Total);
                var imported = store.Import(file); True(imported.Success); Equal(1, imported.Accepted);
                var repeated = store.Import(file); Equal(0, repeated.Accepted); Equal(1, repeated.Duplicates);
                var reviewed = store.Import(file, true); Write(Batch(reply.Replace("полгорода", "крепость")));
                True(!store.Import(file, false, reviewed.InputHash).Success); Equal(1, store.Stats().Total);
                Write(Batch(reply));
                var first = store.Build(); True(store.Verify(first.StagePath).Success);
                Equal(first.ManifestHash, store.Build().ManifestHash);
                var second = Path.Combine(root, "input", "two.jsonl");
                File.WriteAllText(second, Batch(reply.Replace("полгорода", "весь материк")), utf8);
                Write("{}\n"); True(!store.Import(Path.GetDirectoryName(file)!).Success); Equal(1, store.Stats().Total);
                Write(Batch(reply.Replace("За такой", "За  такой"))); Equal(1, store.Import(file).Duplicates);
                var idx = Path.Combine(first.StagePath, "replies.idx"); var bytes = File.ReadAllBytes(idx); bytes[0] ^= 1; File.WriteAllBytes(idx, bytes);
                True(!store.Verify(first.StagePath).Success);
            });
            Test("TASK095 contextual buckets require a deterministic safe fallback", () =>
            {
                foreach (var restriction in new[] { ",\"voices\":[\"calm\"]", ",\"facets\":{\"humor\":80}", ",\"tone\":\"PLAYFUL\"", ",\"profanity\":\"MILD\"", ",\"maturity\":\"MATURE\"" })
                {
                    var store = new V4Workspace(Path.Combine(root, Guid.NewGuid().ToString("N")), source);
                    Write(Batch(reply.Replace("}", restriction + "}")));
                    True(store.Import(file).Success);
                    Throws(() => store.Build());
                    Write(Batch(reply.Replace("полгорода", "недвижимость")));
                    True(store.Import(file).Success);
                    True(store.Verify(store.Build().StagePath).Success);
                }
            });
            Test("TASK095 reviewed taxonomy approval is hash bound and passport uses the same registry", () =>
            {
                var store = new V4Workspace(Path.Combine(root, "taxonomy"), source);
                var proposal = store.Propose("hybrid.trade", "hybrid.trade.test_offer", "проверочный торг", "hybrid.trade.test_offer.reply", "hybrid.trade.test_offer.observe");
                Throws(() => store.Approve(proposal.Path, new string('0', 64), true));
                Throws(() => store.Approve(proposal.Path, proposal.Hash, false));
                store.Approve(proposal.Path, proposal.Hash, true);
                True(store.Categories().Any(c => c.Key == "hybrid.trade.test_offer"));
                var passport = Path.Combine(root, "taxonomy", "passport.md"); store.ExportPassport(passport);
                True(File.ReadAllText(passport).Contains("hybrid.trade.test_offer", StringComparison.Ordinal));
                True(V4Registry.Default.Voices.Count >= 24); Equal(10, V4Registry.FacetKeys.Count);
                Throws(() => store.Build());
                var deep = store.Propose("hybrid.trade", "hybrid.trade.new_group.owner_leaf", "вложенная тема", "hybrid.trade.new_group.owner_leaf.reply", "hybrid.trade.new_group.owner_leaf.observe");
                store.Approve(deep.Path, deep.Hash, true);
                True(store.Categories().Any(c => c.Key == "hybrid.trade.new_group.owner_leaf"));
                var newBranch = store.Propose("life", "life.gardening.flowers", "сад и цветы", "life.gardening.flowers.reply", "life.gardening.flowers.observe");
                store.Approve(newBranch.Path, newBranch.Hash, true);
                True(store.Categories().Any(c => c.Key == "life.gardening.flowers"));
                var stale = store.Propose("hybrid.trade", "hybrid.trade.stale_leaf", "устаревшее предложение", "hybrid.trade.stale_leaf.reply", "hybrid.trade.stale_leaf.observe");
                var fresh = store.Propose("hybrid.trade", "hybrid.trade.fresh_leaf", "новая тема", "hybrid.trade.fresh_leaf.reply", "hybrid.trade.fresh_leaf.observe");
                store.Approve(fresh.Path, fresh.Hash, true); Throws(() => store.Approve(stale.Path, stale.Hash, true));
                Throws(() => store.Propose("hybrid.trade.absurd_offers", "hybrid.trade.absurd_offers.child", "нельзя расширять leaf", "hybrid.trade.absurd_offers.child.reply", "hybrid.trade.absurd_offers.child.observe"));
            });
        }
        finally { if (Directory.Exists(root)) Directory.Delete(root, true); }
    }
}
