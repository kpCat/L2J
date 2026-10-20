using System.Globalization;
using System.Text;
using System.Text.Json;
using System.Text.RegularExpressions;
using System.Xml;
using System.Xml.Linq;

namespace PhantomSemanticStudio.Core;

public sealed class IsolatedV3ProposalStager
{
    public IsolatedStage Create(WorkspaceStore store, PackSnapshot snapshot, IReadOnlyList<Candidate> allPeers,
        IReadOnlyList<string> ids, V3SegmentPair pair, bool selectionConfirmed, bool editorialConfirmed, CancellationToken token = default)
    {
        V3ProposalContract.Require(selectionConfirmed && editorialConfirmed, "CONSENT"); token.ThrowIfCancellationRequested();
        V3ProposalContract.CheckPaths(store, snapshot.ModuleRoot);
        var current = V3ProposalContract.Current(snapshot, token);
        var peers = allPeers.Select(c => c.Copy()).ToList(); var peersHash = PeerHash(peers);
        var exact = StageBatchSelection.SelectExactIds(peers, ids); var selected = exact.Select(id => peers.Single(c => c.Id == id)).ToList();
        V3ProposalContract.Require(V3ProposalContract.GetPairs(current, token).Contains(pair), "BLOCKED_SCOPE");
        Validate(current, peers, selected, pair, token);
        var targets = new Dictionary<string, byte[]>(StringComparer.Ordinal);
        foreach (var (kind, path) in new[] { ("PATTERN", pair.SemanticPath), ("TEMPLATE", pair.ConversationPath) })
        {
            var batch = selected.Where(c => c.Kind == kind).ToArray(); if (batch.Length > 0) targets.Add(path, Compile(current, path, batch));
        }
        var v3Bytes = current.Files.Where(f => f.RelativePath.Contains("/humanized/v3/", StringComparison.Ordinal)).Sum(f => f.Bytes)
            + targets.Sum(t => t.Value.Length - current.Files.Single(f => f.RelativePath == t.Key).Bytes);
        V3ProposalContract.Require(v3Bytes <= 33554432, "V3_TOTAL_BYTES");
        var parent = PathSafety.ResolveRelative(store.Root, "v3-proposals"); var id = Guid.NewGuid().ToString("N");
        var partial = PathSafety.ResolveRelative(parent, id + ".partial"); var finished = PathSafety.ResolveRelative(parent, id); var ownsPartial = false;
        try
        {
            Directory.CreateDirectory(parent); PathSafety.AssertNoReparsePoints(parent); Directory.CreateDirectory(partial); ownsPartial = true;
            var module = PathSafety.ResolveRelative(partial, "module"); var data = PathSafety.ResolveRelative(module, "dist/game/data/phantoms");
            foreach (var stamp in current.Files)
            {
                token.ThrowIfCancellationRequested(); V3ProposalContract.CheckPaths(store, current.ModuleRoot);
                var bytes = V3ProposalContract.ReadStamped(current, stamp.RelativePath); var path = PathSafety.ResolveRelative(data, stamp.RelativePath);
                V3ProposalContract.WriteNew(path, bytes); V3ProposalContract.ReadStamped(path, stamp);
            }
            foreach (var target in targets) { token.ThrowIfCancellationRequested(); var path = PathSafety.ResolveRelative(data, target.Key); File.WriteAllBytes(path, target.Value); }
            var staged = new PackReader().Load(module, token);
            foreach (var stamp in current.Files.Where(s => !targets.ContainsKey(s.RelativePath)))
                V3ProposalContract.Require(staged.Files.Single(s => s.RelativePath == stamp.RelativePath) == stamp, "PRESERVATION");
            var receipt = new V3StageReceipt
            {
                StageId = id, SelectionConfirmed = true, EditorialConfirmed = true, SourceModule = current.ModuleRoot, SourceFingerprint = current.Fingerprint,
                Pair = pair, SourceFiles = current.Files.ToList(), StagedFiles = staged.Files.ToList(),
                Candidates = selected.Select(c => new V3CandidateStamp(c.Id, XmlId(c), c.Kind, c.Act, c.Topic, c.Band, c.Register, c.ApprovedFingerprint, TextRules.Hash(c.Text))).ToList(),
                BaselinePatterns = current.Entries.Count(e => e.Kind == "PATTERN"), BaselineTemplates = current.Entries.Count(e => e.Kind == "TEMPLATE"),
                AddedPatterns = selected.Count(c => c.Kind == "PATTERN"), AddedTemplates = selected.Count(c => c.Kind == "TEMPLATE")
            };
            V3ProposalContract.Require(staged.Files.Count == current.Files.Count && staged.Entries.Count == current.Entries.Count + selected.Count
                && staged.Entries.Count(e => e.Kind == "PATTERN") == receipt.BaselinePatterns + receipt.AddedPatterns
                && staged.Entries.Count(e => e.Kind == "TEMPLATE") == receipt.BaselineTemplates + receipt.AddedTemplates, "PRESERVATION");
            V3ProposalContract.WriteNew(PathSafety.ResolveRelative(partial, "receipt.json"), JsonSerializer.SerializeToUtf8Bytes(receipt, WorkspaceStore.JsonOptions));
            V3ProposalContract.Current(snapshot, token); token.ThrowIfCancellationRequested();
            V3ProposalContract.Require(PeerHash(allPeers) == peersHash && exact.All(id => CandidateReview.IsCurrent(allPeers.Single(c => c.Id == id))), "APPROVAL_DRIFT");
            V3ProposalContract.CheckPaths(store, current.ModuleRoot); PathSafety.AssertNoReparsePoints(partial); PathSafety.AssertNoReparsePoints(finished);
            token.ThrowIfCancellationRequested(); Directory.Move(partial, finished); return new(finished, Path.Combine(finished, "module"), "STAGED_V3_UNVALIDATED");
        }
        catch { if (ownsPartial) V3ProposalContract.RemoveOwnPartial(parent, partial); throw; }
    }
    internal static string XmlId(Candidate c) => "pss.v3." + (c.Kind == "PATTERN" ? "p." : "t.") + c.Id;
    private static string PeerHash(IEnumerable<Candidate> peers) => TextRules.Hash(JsonSerializer.Serialize(peers.OrderBy(c => c.Id, StringComparer.Ordinal)));
    private static void Validate(PackSnapshot pack, List<Candidate> peers, List<Candidate> selected, V3SegmentPair pair, CancellationToken token)
    {
        var semantic = V3ProposalContract.ReadSegment(V3ProposalContract.ReadStamped(pack, pair.SemanticPath), "SEMANTIC");
        var conversation = V3ProposalContract.ReadSegment(V3ProposalContract.ReadStamped(pack, pair.ConversationPath), "CONVERSATION");
        V3ProposalContract.Require((string?)conversation.Root!.Attribute("mature") == "false", "BLOCKED_SCOPE");
        var manifest = V3ProposalContract.ReadXml(V3ProposalContract.ReadStamped(pack, V3ProposalContract.Manifest));
        var topics = manifest.Root!.Element("topics")!.Elements("topic").Select(e => (string)e.Attribute("key")!).ToHashSet(StringComparer.Ordinal);
        var acts = manifest.Root.Element("acts")!.Elements("act").Select(e => (string)e.Attribute("key")!).ToHashSet(StringComparer.Ordinal);
        foreach (var c in selected)
        {
            token.ThrowIfCancellationRequested();
            V3ProposalContract.Require(CandidateReview.IsCurrent(c) && Regex.IsMatch(c.Id, "^[0-9a-f]{32}$"), "APPROVAL_ID");
            V3ProposalContract.Require(c.Gender == "ANY" && topics.Contains(c.Topic) && acts.Contains(c.Act) && V3ProposalContract.Key(c.Act) && V3ProposalContract.Key(c.Topic), "BLOCKED_SCOPE");
            V3ProposalContract.Require(semantic.Descendants("pattern").Any(e => (string?)e.Attribute("topic") == c.Topic && (string?)e.Attribute("act") == c.Act)
                && conversation.Descendants("template").Any(e => (string?)e.Attribute("act") == c.Act && (string?)e.Attribute("profanity") == "NONE" && (string?)e.Attribute("mature") != "true"), "BLOCKED_SCOPE");
            V3ProposalContract.Require(c.Text.IndexOfAny(['{', '}', '<', '>']) < 0 && !c.Text.Contains("```") && !c.Text.EnumerateRunes().Any(r => Rune.GetUnicodeCategory(r) is
                UnicodeCategory.Control or UnicodeCategory.Format or UnicodeCategory.LineSeparator or UnicodeCategory.ParagraphSeparator or UnicodeCategory.Surrogate
                or UnicodeCategory.OtherNotAssigned or UnicodeCategory.PrivateUse or UnicodeCategory.MathSymbol), "TEXT_SCOPE");
            var normalized = TextRules.Normalize(c.Text);
            V3ProposalContract.Require(normalized.Length > 0 && (c.Kind == "PATTERN" ? normalized.Length <= 160 : V3ProposalContract.Utf8.GetByteCount(c.Text) <= 240), "JAVA_LENGTH");
            V3ProposalContract.Require(!pack.Entries.Any(e => e.Id == XmlId(c)), "ID_COLLISION");
            V3ProposalContract.Require(!Regex.IsMatch(normalized, "\\b(рад|рада|готов|готова|был|была|пошел|пошла|сделал|сделала|телепортировал|бафнул|выдал|секс|порно)\\b", RegexOptions.CultureInvariant), "EDITORIAL_RISK");
            V3ProposalContract.Require(!pack.Entries.Where(e => e.Kind == "PROFANITY").Any(e => TextRules.Normalize(e.Text) is { Length: > 0 } bad
                && (" " + normalized + " ").Contains(" " + bad + " ", StringComparison.Ordinal)), "EDITORIAL_RISK");
            var issues = CandidateValidator.Validate(c, pack, peers);
            V3ProposalContract.Require(!issues.Any(i => i.Severity == IssueSeverity.Error || i.Code == "ACTION_CLAIM"), "CANDIDATE_VALIDATION");
        }
        var report = PackCoverageAnalyzer.Analyze(pack with { Entries = pack.Entries.Concat(selected.Select(c => new PackEntry
            { Kind = c.Kind, Id = XmlId(c), Text = c.Text, Topic = c.Kind == "PATTERN" ? c.Topic : "", Act = c.Act, Band = c.Band, Register = c.Register })).ToList() }, token);
        V3ProposalContract.Require(!report.Capacities.Any(c => c.Status == "CAPACITY_EXCEEDED"), "JAVA_CAPACITY");
    }
    private static byte[] Compile(PackSnapshot pack, string path, Candidate[] selected)
    {
        var kind = selected[0].Kind == "PATTERN" ? "SEMANTIC" : "CONVERSATION";
        var doc = V3ProposalContract.ReadSegment(V3ProposalContract.ReadStamped(pack, path), kind); var original = new XDocument(doc);
        var parent = doc.Root!.Element(kind == "SEMANTIC" ? "patterns" : "templates"); V3ProposalContract.Require(parent != null, "BLOCKED_SCOPE");
        foreach (var c in selected)
        {
            var element = new XElement(c.Kind == "PATTERN" ? "pattern" : "template", new XAttribute("id", XmlId(c)));
            if (c.Kind == "PATTERN") element.Add(new XAttribute("topic", c.Topic)); element.Add(new XAttribute("act", c.Act));
            if (c.Kind == "PATTERN") element.Add(new XAttribute("phrase", c.Text), new XAttribute("salience", 0), new XAttribute("ttlMinutes", 0), new XAttribute("priority", 500));
            else element.Add(new XAttribute("band", c.Band), new XAttribute("register", c.Register), new XAttribute("profanity", "NONE"), new XAttribute("text", c.Text));
            if (parent!.LastNode is XText trailing && string.IsNullOrWhiteSpace(trailing.Value))
                trailing.AddBeforeSelf(new XText("\n\t\t"), element);
            else parent.Add(new XText("\n\t\t"), element);
        }
        using var output = new MemoryStream();
        using (var writer = XmlWriter.Create(output, new XmlWriterSettings { Encoding = V3ProposalContract.Utf8, Indent = false, NewLineHandling = NewLineHandling.None, CloseOutput = false })) doc.Save(writer);
        var bytes = output.ToArray(); var roundtrip = V3ProposalContract.ReadSegment(bytes, kind);
        var roundParent = roundtrip.Root!.Element(kind == "SEMANTIC" ? "patterns" : "templates")!;
        foreach (var c in selected) { var added = roundParent.Elements().Single(e => (string?)e.Attribute("id") == XmlId(c)); added.PreviousNode!.Remove(); added.Remove(); }
        V3ProposalContract.Require(XNode.DeepEquals(original, roundtrip), "PRESERVATION"); return bytes;
    }
}
