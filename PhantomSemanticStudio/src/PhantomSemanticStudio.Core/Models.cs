using System.Text.Json.Serialization;

namespace PhantomSemanticStudio.Core;

public sealed class StudioSettings
{
    public string HighFiveRoot { get; set; } = @"C:\Users\ZBook\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive";
    public string Endpoint { get; set; } = "http://127.0.0.1:1234/v1";
    public string ModelId { get; set; } = "gemma-4-26b-a4b-it-ultra-uncensored-heretic";
    public double Temperature { get; set; } = 0.7;
    public int MaxTokens { get; set; } = 4096;
    public int TimeoutSeconds { get; set; } = 180;
    // API-ключ намеренно отсутствует: UI передаёт его только в памяти процесса.
    public void Validate()
    {
        LmStudioClient.ValidateEndpoint(Endpoint);
        if (string.IsNullOrWhiteSpace(ModelId) || ModelId.Length > 256) throw new InvalidDataException("Некорректный идентификатор модели.");
        if (!double.IsFinite(Temperature) || Temperature < 0 || Temperature > 2 || MaxTokens < 128 || MaxTokens > 16384 || TimeoutSeconds < 15 || TimeoutSeconds > 600)
            throw new InvalidDataException("Параметры генерации выходят за допустимые пределы.");
        if (!Path.IsPathFullyQualified(HighFiveRoot)) throw new InvalidDataException("Нужен абсолютный путь к High Five.");
    }
}

public sealed record PackEntry
{
    public string Kind { get; init; } = "";
    public string Id { get; init; } = "";
    public string Text { get; init; } = "";
    public string Topic { get; init; } = "";
    public string Act { get; init; } = "";
    public string Band { get; init; } = "UNKNOWN";
    public string Register { get; init; } = "NEUTRAL";
    public string Profanity { get; init; } = "NONE";
    public bool Mature { get; init; }
    public int Priority { get; init; }
    public string Fact { get; init; } = "";
    public string Recall { get; init; } = "";
    public string SourceFile { get; init; } = "";
    public int SourceLine { get; init; }
}

public sealed record SourceFileStamp(string RelativePath, string Sha256, long Bytes);
public sealed record PackSnapshot
{
    public string ModuleRoot { get; init; } = "";
    public string DataRoot { get; init; } = "";
    // Это отпечаток импорта Studio, НЕ Java combinedHash.
    public string Fingerprint { get; init; } = "";
    public DateTimeOffset ImportedAtUtc { get; init; } = DateTimeOffset.UtcNow;
    public List<SourceFileStamp> Files { get; init; } = [];
    public List<PackEntry> Entries { get; init; } = [];
    public List<string> Topics { get; init; } = [];
    public List<string> Acts { get; init; } = [];
    public List<string> Warnings { get; init; } = [];
}

public sealed class Candidate
{
    public SemanticReviewEvidence? SemanticReview { get; set; }
    public Candidate Copy() => (Candidate)MemberwiseClone();
    public string Id { get; set; } = Guid.NewGuid().ToString("N");
    public string Kind { get; set; } = "TEMPLATE";
    public string Text { get; set; } = "";
    public string Topic { get; set; } = "";
    public string Act { get; set; } = "";
    public string Band { get; set; } = "UNKNOWN";
    public string Register { get; set; } = "CASUAL";
    public string Gender { get; set; } = "ANY";
    public string SourceFingerprint { get; set; } = "";
    public string ModelId { get; set; } = "";
    public string Instruction { get; set; } = "";
    public string Rationale { get; set; } = "";
    public DateTimeOffset CreatedAtUtc { get; set; } = DateTimeOffset.UtcNow;
    public string Status { get; set; } = "DRAFT";
    public string ApprovedFingerprint { get; set; } = "";
    public string ReviewNote { get; set; } = "";
    public DateTimeOffset? ReviewedAtUtc { get; set; }
}

public enum GenerationMode { TEMPLATE, PATTERN, MIXED }
public sealed record SemanticVerdict(string RefKey, string ReferencedTextHash, string Kind, string SourceFile,
    int SourceLine, bool IsPeer, string Relation, string Reason);
public sealed record SemanticReviewEvidence
{
    public string Status { get; init; } = "MODEL_ADVISORY_NOT_VERIFIED";
    public string Coverage { get; init; } = "COVERAGE_LIMITED";
    public string CandidateFingerprint { get; init; } = "";
    public string SourceFingerprint { get; init; } = "";
    public string SourceStateFingerprint { get; init; } = "";
    public string PeersFingerprint { get; init; } = "";
    public string ShortlistFingerprint { get; init; } = "";
    public string ModelId { get; init; } = "";
    public DateTimeOffset CreatedAtUtc { get; init; } = DateTimeOffset.UtcNow;
    public int TotalConsidered { get; init; }
    public int ScopeConsidered { get; init; }
    public int MaxMatches { get; init; } = 12;
    public IReadOnlyList<SemanticVerdict> Verdicts { get; init; } = Array.Empty<SemanticVerdict>();
}
public sealed record GenerationRequest(string Topic, string Act, string Band, string Register, string Gender, string Instruction, string Words, int Count, GenerationMode Mode = GenerationMode.MIXED);
public enum LmDiagnosticCode { CHECKED_MODEL_LIST, MODEL_NOT_LISTED, SERVER_OFFLINE, CONNECTION_ERROR, TIMEOUT, CANCELLED, ENDPOINT, AUTH, HTTP_ERROR, SCHEMA_REJECTED, BAD_RESPONSE, INVALID_SETTINGS, INVALID_REQUEST }
public sealed record LmDiagnostic(LmDiagnosticCode Code, string Message, string FailureCategory = "")
{
    public override string ToString() => Code + ": " + (FailureCategory.Length == 0 ? "" : "[" + FailureCategory + "] ") + Message;
}
// Не сохраняет исходное исключение: оно может содержать token, prompt или ответ сервера.
public sealed class LmStudioException(LmDiagnostic diagnostic) : Exception(diagnostic.ToString())
{
    public LmDiagnostic Diagnostic { get; } = diagnostic;
}
public sealed record DraftItem(string Kind, string Text, string Reason);
public enum IssueSeverity { Warning, Error }
public sealed record ValidationIssue(IssueSeverity Severity, string Code, string Message);
public sealed class SessionState
{
    public int Version { get; set; } = 1;
    public List<Candidate> Candidates { get; set; } = [];
    public List<string> Lessons { get; set; } = [];
    public List<EditorialLesson> ScopedLessons { get; set; } = [];
}
public sealed record EditorialLesson
{
    public string Id { get; init; } = Guid.NewGuid().ToString("N");
    public string Text { get; init; } = "";
    public string Topic { get; init; } = "";
    public string Act { get; init; } = "";
    public string Band { get; init; } = "UNKNOWN";
    public string Register { get; init; } = "NEUTRAL";
    public string Gender { get; init; } = "ANY";
    public string SourceFingerprint { get; init; } = "";
    public DateTimeOffset CreatedAtUtc { get; init; } = DateTimeOffset.UtcNow;
    public GenerationRequest ToRequest(PackSnapshot snapshot, bool reviewedBaseline)
    {
        if (string.IsNullOrWhiteSpace(Text) || Text.Length > 4000 || TextRules.IsFunctionalAct(Act)
            || !snapshot.Entries.Any(e => e.Kind == "PATTERN" && e.Topic == Topic && e.Act == Act)
            || !CandidateValidator.Bands.Contains(Band) || !CandidateValidator.Registers.Contains(Register) || !CandidateValidator.Genders.Contains(Gender))
            throw new InvalidDataException("Замечание имеет недопустимый scope. Выберите существующую разговорную тему и проверьте параметры.");
        if (SourceFingerprint != snapshot.Fingerprint && !reviewedBaseline)
            throw new InvalidDataException("Замечание относится к другому или неизвестному baseline. Нужно явное новое ревью.");
        return new GenerationRequest(Topic, Act, Band, Register, Gender, Text, "", 6);
    }
}
public enum PreviewStatus { NO_PACK_MATCH, PACK_CATALOG_APPROXIMATE, FUNCTIONAL_OR_MEMORY_UNSUPPORTED, TEMPLATE_CONTEXT_UNAVAILABLE, NO_ELIGIBLE_TEMPLATE }
public sealed record PreviewResult(bool Matched, string Text, string PatternId, string TemplateId, string Topic, string Act, string Note)
{
    // Matched retains the original pattern-found contract; Status describes whether a reply is available.
    public PreviewStatus Status { get; init; } = Matched && Text.Length > 0 ? PreviewStatus.PACK_CATALOG_APPROXIMATE : PreviewStatus.NO_PACK_MATCH;
}
