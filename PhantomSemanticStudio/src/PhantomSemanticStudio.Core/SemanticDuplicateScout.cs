using System.Text.Json;

namespace PhantomSemanticStudio.Core;

public sealed record SemanticNeighbor(string RefKey, string Text, string TextHash, string Kind, string Act,
    string Topic, string Band, string Register, string SourceFile, int SourceLine, double Score, bool IsPeer,
    bool Exact, int ContextPriority);
public sealed record SemanticShortlist(string CandidateFingerprint, string SourceFingerprint, string SourceStateFingerprint,
    string PeersFingerprint, string ShortlistFingerprint, int TotalConsidered, int ScopeConsidered,
    int MaxMatches, IReadOnlyList<SemanticNeighbor> Matches)
{
    public string Coverage => "COVERAGE_LIMITED";
}

public static class SemanticDuplicateScout
{
    public static bool IsEvidenceCurrent(Candidate candidate, PackSnapshot snapshot, IReadOnlyList<Candidate> peers,
        SemanticReviewEvidence evidence, CancellationToken token = default)
    {
        try { ValidateEvidence(evidence); return IsEvidenceCurrent(Search(snapshot, candidate, peers, evidence.MaxMatches, token), evidence); }
        catch (InvalidDataException) { return false; }
    }

    public static bool IsEvidenceCurrent(SemanticShortlist s, SemanticReviewEvidence evidence)
    {
        try
        {
            ValidateEvidence(evidence);
            if (evidence.CandidateFingerprint != s.CandidateFingerprint || evidence.SourceFingerprint != s.SourceFingerprint
                || evidence.SourceStateFingerprint != s.SourceStateFingerprint || evidence.PeersFingerprint != s.PeersFingerprint
                || evidence.ShortlistFingerprint != s.ShortlistFingerprint || evidence.TotalConsidered != s.TotalConsidered
                || evidence.ScopeConsidered != s.ScopeConsidered || evidence.Verdicts.Count != s.Matches.Count) return false;
            return s.Matches.All(m => evidence.Verdicts.Any(v => v.RefKey == m.RefKey && v.ReferencedTextHash == m.TextHash
                && v.Kind == m.Kind && v.SourceFile == m.SourceFile && v.SourceLine == m.SourceLine && v.IsPeer == m.IsPeer));
        }
        catch (InvalidDataException) { return false; }
    }
    public static Candidate WithCurrentEvidence(Candidate candidate, PackSnapshot snapshot, IReadOnlyList<Candidate> peers,
        SemanticReviewEvidence evidence, CancellationToken token = default)
    {
        token.ThrowIfCancellationRequested();
        if (!IsEvidenceCurrent(candidate, snapshot, peers, evidence, token))
            throw new InvalidDataException("STALE: кандидат, источник, peers или список сравнений изменились. Экспертиза не сохранена; повтор возможен только вручную.");
        var copy = candidate.Copy(); copy.SemanticReview = evidence with { Verdicts = Array.AsReadOnly(evidence.Verdicts.ToArray()) };
        token.ThrowIfCancellationRequested();
        return copy;
    }
    public static void ValidateEvidence(SemanticReviewEvidence evidence)
    {
        static bool Sha(string? s) => s is { Length: 64 } && s.All(c => c is >= '0' and <= '9' or >= 'a' and <= 'f');
        if (evidence == null || evidence.Status != "MODEL_ADVISORY_NOT_VERIFIED" || evidence.Coverage != "COVERAGE_LIMITED"
            || !Sha(evidence.CandidateFingerprint) || !Sha(evidence.SourceStateFingerprint) || !Sha(evidence.PeersFingerprint)
            || !Sha(evidence.ShortlistFingerprint) || string.IsNullOrWhiteSpace(evidence.SourceFingerprint) || evidence.SourceFingerprint.Length > 256
            || string.IsNullOrWhiteSpace(evidence.ModelId) || evidence.ModelId.Length > 256 || evidence.ModelId.Any(char.IsControl)
            || evidence.CreatedAtUtc == default || evidence.CreatedAtUtc.Offset != TimeSpan.Zero
            || evidence.MaxMatches is < 1 or > 12 || evidence.TotalConsidered < 1 || evidence.ScopeConsidered < 0
            || evidence.ScopeConsidered > evidence.TotalConsidered || evidence.Verdicts == null
            || evidence.Verdicts.Count is < 1 or > 12 || evidence.Verdicts.Count > evidence.MaxMatches || evidence.Verdicts.Count > evidence.TotalConsidered)
            throw new InvalidDataException("Повреждённое или слишком большое evidence экспертизы.");
        var keys = new HashSet<string>(StringComparer.Ordinal);
        foreach (var v in evidence.Verdicts)
        {
            if (v == null || string.IsNullOrWhiteSpace(v.RefKey) || v.RefKey.Length > 512 || v.RefKey.Any(char.IsControl)
                || !keys.Add(v.RefKey) || !Sha(v.ReferencedTextHash) || v.Kind is not ("PATTERN" or "TEMPLATE")
                || v.SourceFile == null || v.SourceFile.Length > 512 || v.SourceFile.Any(char.IsControl) || v.SourceLine < 0
                || v.IsPeer && (!v.RefKey.StartsWith("PEER|", StringComparison.Ordinal) || v.SourceFile.Length != 0 || v.SourceLine != 0)
                || !v.IsPeer && (!v.RefKey.StartsWith("SOURCE|" + v.Kind + "|", StringComparison.Ordinal) || v.SourceFile.Length == 0)
                || v.Relation is not ("SAME_MEANING" or "RELATED" or "DIFFERENT" or "UNSURE")
                || string.IsNullOrWhiteSpace(v.Reason) || v.Reason.Length > 240 || v.Reason.Any(char.IsControl))
                throw new InvalidDataException("Недопустимое решение в evidence экспертизы.");
        }
        if (JsonSerializer.SerializeToUtf8Bytes(evidence, WorkspaceStore.JsonOptions).Length > 32 * 1024)
            throw new InvalidDataException("Evidence экспертизы превышает 32 KiB.");
    }
    public static SemanticShortlist Search(PackSnapshot snapshot, Candidate candidate, IReadOnlyList<Candidate> peers,
        int maxMatches = 12, CancellationToken cancellationToken = default)
    {
        if (maxMatches is < 1 or > 12 || string.IsNullOrWhiteSpace(snapshot.Fingerprint)
            || candidate.Kind is not ("PATTERN" or "TEMPLATE") || string.IsNullOrWhiteSpace(candidate.Text)
            || candidate.Text.Length > 4000 || string.IsNullOrWhiteSpace(candidate.Id))
            throw new InvalidDataException("Нужны импортированный источник, сохранённый кандидат и предел 1–12.");
        cancellationToken.ThrowIfCancellationRequested();
        var normalized = TextRules.Normalize(candidate.Text);
        var grams = TextRules.Trigrams(normalized);
        var words = normalized.Split(' ', StringSplitOptions.RemoveEmptyEntries).ToHashSet(StringComparer.Ordinal);
        var matches = new List<SemanticNeighbor>();
        var keys = new HashSet<string>(StringComparer.Ordinal);
        var total = 0; var scope = 0;
        void Consider(string key, string text, string kind, string act, string topic, string band, string register, string file, int line, bool peer)
        {
            cancellationToken.ThrowIfCancellationRequested();
            if (!keys.Add(key)) throw new InvalidDataException("Неоднозначный reference ID в локальном поиске.");
            total++;
            var priority = (act == candidate.Act ? 2 : 0) + (kind == "PATTERN" && topic == candidate.Topic ? 1 : 0);
            if (act == candidate.Act && (kind != "PATTERN" || topic == candidate.Topic)) scope++;
            var other = TextRules.Normalize(text);
            var exact = normalized == other;
            var otherGrams = TextRules.Trigrams(other);
            var common = grams.Count(otherGrams.Contains);
            var union = grams.Count + otherGrams.Count - common;
            var lexical = union == 0 ? (exact ? 1 : 0) : (double)common / union;
            var otherWords = other.Split(' ', StringSplitOptions.RemoveEmptyEntries).ToHashSet(StringComparer.Ordinal);
            common = words.Count(otherWords.Contains); union = words.Count + otherWords.Count - common;
            var score = 0.6 * lexical + 0.4 * (union == 0 ? 0 : (double)common / union);
            if (!exact && score == 0) return;
            matches.Add(new(key, text, TextRules.Hash(text), kind, act, topic, band, register, file, line, score, peer, exact, priority));
            matches.Sort(Compare);
            if (matches.Count > maxMatches) matches.RemoveAt(maxMatches);
        }
        foreach (var e in snapshot.Entries)
        {
            cancellationToken.ThrowIfCancellationRequested();
            if (e.Kind == candidate.Kind)
                Consider("SOURCE|" + e.Kind + "|" + e.Id, e.Text, e.Kind, e.Act, e.Topic, e.Band, e.Register, e.SourceFile, e.SourceLine, false);
        }
        foreach (var p in peers)
        {
            cancellationToken.ThrowIfCancellationRequested();
            if (p.Id != candidate.Id && p.Status != "REJECTED" && p.Kind == candidate.Kind)
                Consider("PEER|" + p.Id, p.Text, p.Kind, p.Act, p.Topic, p.Band, p.Register, "", 0, true);
        }
        var peersHash = PeersFingerprint(candidate, peers);
        var sourceState = Hash(new
        {
            snapshot.Fingerprint,
            Files = snapshot.Files.OrderBy(f => f.RelativePath, StringComparer.Ordinal).ToArray(),
            Entries = snapshot.Entries.OrderBy(e => e.Kind, StringComparer.Ordinal).ThenBy(e => e.Id, StringComparer.Ordinal)
                .Select(e => new { e.Id, e.Kind, Hash = TextRules.Hash(e.Text), e.Act, e.Topic, e.Band, e.Register, e.SourceFile, e.SourceLine }).ToArray()
        });
        cancellationToken.ThrowIfCancellationRequested();
        return new(CandidateFingerprint(candidate), snapshot.Fingerprint, sourceState, peersHash,
            ShortlistFingerprint(matches, maxMatches, total, scope), total, scope, maxMatches, matches.AsReadOnly());
    }
    private static int Compare(SemanticNeighbor a, SemanticNeighbor b)
    {
        var result = b.Exact.CompareTo(a.Exact);
        if (result == 0) result = (b.Score + b.ContextPriority * 0.04).CompareTo(a.Score + a.ContextPriority * 0.04);
        if (result == 0) result = StringComparer.Ordinal.Compare(a.RefKey, b.RefKey);
        return result;
    }
    public static string CandidateFingerprint(Candidate c) => Hash(new
    { c.Id, c.Kind, c.Text, c.Act, c.Topic, c.Band, c.Register, c.Gender, c.SourceFingerprint });
    public static string PeersFingerprint(Candidate c, IReadOnlyList<Candidate> peers) => Hash(peers.Where(p => p.Id != c.Id && p.Status != "REJECTED")
        .OrderBy(p => p.Id, StringComparer.Ordinal).Select(p => new { p.Id, p.Status, Hash = CandidateFingerprint(p) }).ToArray());
    public static string ShortlistFingerprint(IReadOnlyList<SemanticNeighbor> matches, int limit, int total, int scope)
        => Hash(new { limit, total, scope, Matches = matches });
    private static string Hash<T>(T value) => TextRules.Hash(JsonSerializer.Serialize(value));
}
