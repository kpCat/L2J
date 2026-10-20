namespace PhantomSemanticStudio.Core;

public sealed record CorpusTriage(string Language, string Reason);
public sealed record CorpusEntryStamp(int Ordinal, long Bytes, string Sha256);
public sealed record CorpusMetadata
{
    public int Version { get; init; } = 1;
    public string Id { get; init; } = "";
    public string ArchiveHash { get; init; } = "";
    public string DatasetHash { get; init; } = "";
    public long Lines { get; init; }
    public long Public { get; init; }
    public long PrivateSkipped { get; init; }
    public long MalformedSkipped { get; init; }
    public long Duplicates { get; init; }
    public long Noise { get; init; }
    public long Filtered { get; init; }
    public IReadOnlyList<CorpusEntryStamp> Entries { get; init; } = [];
    public IReadOnlyDictionary<string, long> Channels { get; init; } = new Dictionary<string, long>();
    public IReadOnlyDictionary<string, long> Languages { get; init; } = new Dictionary<string, long>();
    public IReadOnlyDictionary<string, long> Lengths { get; init; } = new Dictionary<string, long>();
    public override string ToString() => $"{Id[..8]} • public {Public:N0} • filtered {Filtered:N0}";
}
public sealed record CorpusRecord(string Id, int Entry, long Line, string Timestamp, string Channel, string Original,
    string Normalized, string Fingerprint, string Language, string LanguageReason, bool PossiblePii, bool Noise, bool Duplicate)
{
    public string Preview => CorpusLanguageTriage.Scrub(Original);
    public string Status => "SOURCE_MATERIAL_ONLY";
}
public sealed record CorpusQuery
{
    public string Search { get; init; } = "";
    public string Channel { get; init; } = "";
    public string Language { get; init; } = "";
    public string FromDate { get; init; } = "";
    public string ToDate { get; init; } = "";
    public int MinLength { get; init; }
    public int MaxLength { get; init; } = 4000;
    public string Duplicates { get; init; } = "EXCLUDE";
    public string Noise { get; init; } = "EXCLUDE";
    public int Page { get; init; }
    public int PageSize { get; init; } = 100;
}
public sealed record CorpusPage(long Total, IReadOnlyList<CorpusRecord> Rows);
public sealed record CorpusProgress(long Bytes, long Lines);
public sealed class CorpusException(string code) : Exception(code + ": импорт или индекс корпуса отклонён. Исходный ZIP и прежние корпуса сохранены.")
{
    public string Code { get; } = code;
}
