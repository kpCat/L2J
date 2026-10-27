namespace PhantomSemanticStudio.Core;

public sealed record V4Category(string Key, string Label, string Act, string Intent);
public sealed record V4Voice(string Key, string Label, string Legacy, int ProfanityTolerance, int InsultTolerance,
    int SexualHumorTolerance, int ConflictStyle, int TrustThreshold, IReadOnlyDictionary<string, int> Facets);
public sealed record V4Event(string Code, string Label);
public sealed record V4Entry(string Id, string Mode, string Category, string Act, string Text, string Intent,
    string Ambiguity, string Stance, string Tone, string[] Relations, string[] Voices,
    SortedDictionary<string, int> Facets, string Profanity, string Maturity, string[] Tags, int Weight, string RequiredEvent);
public sealed record V4Diagnostic(string Severity, string Code, string File, int Line, string Message)
{
    public override string ToString() => $"{Severity} {Code}: {File}:{Line}: {Message}";
}
public sealed record V4Report(int Accepted, int Duplicates, IReadOnlyList<V4Entry> Entries, IReadOnlyList<V4Diagnostic> Diagnostics)
{
    public bool Success => Diagnostics.All(d => d.Severity != "ERROR");
    public string InputHash { get; init; } = "";
}
public sealed record V4BuildResult(string StagePath, string ManifestHash, int Records);
public sealed record V4VerifyResult(bool Success, string ManifestHash, int Records, IReadOnlyList<V4Diagnostic> Diagnostics);
public sealed record V4Coverage(string Category, int IncomingTriggers, int Responses, int Initiatives);
public sealed record V4Stats(int Total, IReadOnlyList<V4Coverage> Coverage);
public sealed record V4ProposalResult(string Path, string Hash);
