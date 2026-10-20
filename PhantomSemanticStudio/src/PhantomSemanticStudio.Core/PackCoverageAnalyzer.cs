namespace PhantomSemanticStudio.Core;

public sealed record CoverageRow(string Kind, string Topic, string Act, string Band, string Register,
    string Source, int Count, int Clean, int Diversity, string Warning, string Provenance);
public sealed record CapacityRow(string Name, long Used, long Limit)
{
    public long Remaining => Limit - Used;
    public string Status => Used > Limit ? "CAPACITY_EXCEEDED" : Remaining <= Math.Max(1, Limit / 20) ? "CAPACITY_WARNING" : "WITHIN_CAPACITY";
}
public sealed record PackCoverage(string SourceFingerprint, IReadOnlyList<CoverageRow> Rows,
    IReadOnlyList<CapacityRow> Capacities, int LexicalComparisons)
{
    public string Status => "ADVISORY / NOT_RUNTIME_PARITY / NOT_SEMANTIC_VERIFIED";
}

public static class PackCoverageAnalyzer
{
    public static PackCoverage Analyze(PackSnapshot snapshot, CancellationToken token = default)
    {
        token.ThrowIfCancellationRequested();
        if (snapshot.Entries.Count > 50000 || snapshot.Files.Count > 100 || snapshot.Topics.Count > 50000 || snapshot.Acts.Count > 50000 || snapshot.Entries.Any(e => e.Text.Length > 4000))
            throw new InvalidDataException("Анализ ограничен 50000 записями / 100 файлами / 4000 символами текста.");
        var entries = snapshot.Entries.OrderBy(e => e.Kind, StringComparer.Ordinal).ThenBy(e => e.Id, StringComparer.Ordinal)
            .Select(e => { token.ThrowIfCancellationRequested(); return (Entry: e, Normalized: TextRules.Normalize(e.Text)); }).ToArray();
        var rows = new List<CoverageRow>(); var capacities = new List<CapacityRow>();
        static bool Clean(PackEntry e) => e.Kind == "TEMPLATE" && !e.Mature && e.Profanity == "NONE";
        static string Source(PackEntry e) => e.SourceFile.Contains("/custom/", StringComparison.Ordinal) ? "custom"
            : e.SourceFile.Contains("/v3/", StringComparison.Ordinal) ? "v3" : e.SourceFile.Contains("-v2.", StringComparison.Ordinal) ? "v2" : "v1";
        static string Trace(PackEntry e) => e.Id + " • " + e.SourceFile + ":" + e.SourceLine;
        var answers = entries.Where(e => Clean(e.Entry)).GroupBy(e => e.Entry.Act)
            .ToDictionary(g => g.Key, g => g.Select(e => e.Normalized).Distinct(StringComparer.Ordinal).Count(), StringComparer.Ordinal);
        var patternsByTopic = entries.Where(e => e.Entry.Kind == "PATTERN").ToLookup(e => e.Entry.Topic, StringComparer.Ordinal);
        var patternsByAct = entries.Where(e => e.Entry.Kind == "PATTERN").ToLookup(e => e.Entry.Act, StringComparer.Ordinal);
        var byAct = entries.Where(e => e.Entry.Kind is "PATTERN" or "TEMPLATE").ToLookup(e => e.Entry.Act, StringComparer.Ordinal);
        foreach (var topic in snapshot.Topics.Distinct(StringComparer.Ordinal))
        {
            token.ThrowIfCancellationRequested(); var group = patternsByTopic[topic]; var count = group.Count();
            rows.Add(new("TOPIC", topic, "", "", "", "declared", count, 0, group.Select(e => e.Normalized).Distinct(StringComparer.Ordinal).Count(),
                count == 0 ? "MISSING_PATTERN" : "MATCHED_PATTERN", "PackSnapshot.Topics; template topic отсутствует."));
        }
        foreach (var act in snapshot.Acts.Distinct(StringComparer.Ordinal))
        {
            token.ThrowIfCancellationRequested(); var group = byAct[act]; var patterns = patternsByAct[act].Count(); var clean = group.Count(e => Clean(e.Entry));
            rows.Add(new("ACT", "", act, "", "", "declared", group.Count(), clean, answers.GetValueOrDefault(act),
                patterns == 0 ? clean == 0 ? "MISSING_PATTERN / MISSING_TEMPLATE" : "MISSING_PATTERN / ORPHAN_TEMPLATE"
                    : clean == 0 ? "MISSING_TEMPLATE" : "MATCHED_PATTERN_AND_CLEAN_TEMPLATE", "PackSnapshot.Acts; act coverage не доказывает runtime parity."));
        }
        foreach (var group in entries.Where(e => e.Entry.Kind is "PATTERN" or "TEMPLATE").GroupBy(e =>
            (e.Entry.Kind, Topic: e.Entry.Kind == "PATTERN" ? e.Entry.Topic : "", e.Entry.Act,
                Band: e.Entry.Kind == "TEMPLATE" ? e.Entry.Band : "", Register: e.Entry.Kind == "TEMPLATE" ? e.Entry.Register : "", Origin: Source(e.Entry))))
        {
            token.ThrowIfCancellationRequested();
            var k = group.Key; var count = group.Count(); var clean = group.Count(e => Clean(e.Entry));
            var diversity = group.Where(e => k.Kind == "PATTERN" || Clean(e.Entry)).Select(e => e.Normalized).Distinct(StringComparer.Ordinal).Count();
            var availability = k.Kind == "PATTERN" ? answers.GetValueOrDefault(k.Act) : diversity;
            rows.Add(new(k.Kind, k.Topic, k.Act, k.Band, k.Register, k.Origin, count, clean, diversity,
                (k.Kind == "TEMPLATE" && !patternsByAct[k.Act].Any() ? "ORPHAN_TEMPLATE / MISSING_PATTERN; " : "")
                    + (availability == 0 ? "MISSING_TEMPLATE" : availability < 3 ? "LOW_DIVERSITY" : ""), Trace(group.First().Entry)));
        }
        foreach (var group in entries.Where(e => e.Entry.Kind is "PATTERN" or "TEMPLATE")
            .GroupBy(e => (e.Entry.Kind, e.Normalized)).Where(g => g.Count() > 1).OrderBy(g => g.Key.Kind, StringComparer.Ordinal)
            .ThenBy(g => g.Key.Normalized, StringComparer.Ordinal).Take(1000))
        {
            token.ThrowIfCancellationRequested(); var first = group.First().Entry;
            rows.Add(new("EXACT", first.Kind == "PATTERN" ? first.Topic : "", first.Act, "", "", "mixed", group.Count(), 0, 1,
                "EXACT_NORMALIZED_DUPLICATE", string.Join("\n", group.Take(12).Select(e => Trace(e.Entry)))));
        }
        // Deterministic 256-entry sample and eight neighbours, never an all-pairs search.
        var sample = entries.Where(e => e.Entry.Kind is "PATTERN" or "TEMPLATE").OrderBy(e => e.Normalized, StringComparer.Ordinal)
            .ThenBy(e => e.Entry.Kind, StringComparer.Ordinal).ThenBy(e => e.Entry.Id, StringComparer.Ordinal).Take(256).ToArray();
        var comparisons = 0; var warnings = 0;
        for (var i = 0; i < sample.Length; i++)
            for (var j = i + 1; j < Math.Min(sample.Length, i + 9); j++)
            {
                token.ThrowIfCancellationRequested(); comparisons++;
                if (sample[i].Entry.Kind != sample[j].Entry.Kind || sample[i].Normalized == sample[j].Normalized || warnings >= 100) continue;
                if (TextRules.Similarity(sample[i].Normalized, sample[j].Normalized) < 0.72) continue;
                var e = sample[i].Entry; warnings++;
                rows.Add(new("LEXICAL", e.Kind == "PATTERN" ? e.Topic : "", e.Act, e.Band, e.Register, Source(e), 2, 0, 0,
                    "NEAR_DUPLICATE_POSSIBLE / NOT_SEMANTIC_VERIFIED", Trace(e) + "\n" + Trace(sample[j].Entry)));
            }
        foreach (var (kind, limit) in new[] { ("PATTERN", 8192), ("TEMPLATE", 32768), ("ALIAS", 2048), ("PROFANITY", 1024) })
            capacities.Add(new(kind, entries.Count(e => e.Entry.Kind == kind), limit));
        var v3 = snapshot.Files.Where(f => f.RelativePath.Contains("/humanized/v3/", StringComparison.Ordinal)).OrderBy(f => f.RelativePath, StringComparer.Ordinal).ToArray();
        capacities.Add(new("V3_SEGMENTS", v3.Count(f => f.RelativePath.Contains("/segments/", StringComparison.Ordinal)), 64));
        capacities.Add(new("V3_BYTES_INCLUDING_MANIFEST", v3.Sum(f => f.Bytes), 33554432));
        capacities.AddRange(v3.Select(f => new CapacityRow(f.RelativePath, f.Bytes, f.RelativePath.EndsWith("/manifest.xml", StringComparison.Ordinal) ? 262144 : 1048576)));
        capacities.AddRange(entries.Where(e => e.Entry.Kind == "PATTERN").GroupBy(e => PatternBucket(e.Normalized))
            .Select(g => new CapacityRow("PATTERN_BUCKET|" + g.Key, g.Count(), 256)));
        capacities.AddRange(entries.Where(e => e.Entry.Kind == "TEMPLATE" && e.Entry.Profanity == "NONE")
            .GroupBy(e => (e.Entry.Act, e.Entry.Band, e.Entry.Register, e.Entry.Mature))
            .Select(g => new CapacityRow($"TEMPLATE_BUCKET|{g.Key.Act}|{g.Key.Band}|{g.Key.Register}|{g.Key.Mature}", g.Count(), 4096)));
        token.ThrowIfCancellationRequested();
        return new(snapshot.Fingerprint, Array.AsReadOnly(rows.OrderBy(r => r.Kind, StringComparer.Ordinal).ThenBy(r => r.Topic, StringComparer.Ordinal)
            .ThenBy(r => r.Act, StringComparer.Ordinal).ThenBy(r => r.Band, StringComparer.Ordinal).ThenBy(r => r.Register, StringComparer.Ordinal)
            .ThenBy(r => r.Source, StringComparer.Ordinal).ThenBy(r => r.Provenance, StringComparer.Ordinal).ToArray()),
            Array.AsReadOnly(capacities.OrderBy(c => c.Name, StringComparer.Ordinal).ToArray()), comparisons);
    }

    internal static string PatternBucket(string normalized)
    {
        var marker = normalized.IndexOf("{value}", StringComparison.Ordinal);
        if (marker < 0) return "EXACT|" + normalized;
        var literal = normalized.Replace("{value}", "", StringComparison.Ordinal).Trim().Split(' ', StringSplitOptions.RemoveEmptyEntries);
        return marker == 0 ? "SUFFIX|" + literal.LastOrDefault() : "PREFIX|" + literal.FirstOrDefault();
    }
}
