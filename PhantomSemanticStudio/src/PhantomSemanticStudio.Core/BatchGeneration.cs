using System.Text.Json;
using System.Text.Json.Serialization;

namespace PhantomSemanticStudio.Core;

// Private editorial targets. These values do not add Java personality filters or mature/profanity permissions.
[JsonUnmappedMemberHandling(JsonUnmappedMemberHandling.Disallow)]
public sealed record EditorialFacets
{
    [JsonPropertyName("aggression")] public int Aggression { get; init; }
    [JsonPropertyName("sarcasm")] public int Sarcasm { get; init; }
    [JsonPropertyName("humor")] public int Humor { get; init; }
    [JsonPropertyName("reserved")] public int Reserved { get; init; }
    [JsonPropertyName("swagger")] public int Swagger { get; init; }
    [JsonPropertyName("timidity")] public int Timidity { get; init; }
    [JsonPropertyName("romance")] public int Romance { get; init; }
    [JsonPropertyName("roughness")] public int Roughness { get; init; }
    [JsonPropertyName("cunning")] public int Cunning { get; init; }
    [JsonPropertyName("gossip")] public int Gossip { get; init; }
    public void Validate()
    {
        if (new[] { Aggression, Sarcasm, Humor, Reserved, Swagger, Timidity, Romance, Roughness, Cunning, Gossip }.Any(v => v is < 0 or > 100))
            throw new InvalidDataException("Редакционные характеристики должны быть в пределах 0–100.");
    }
}

[JsonUnmappedMemberHandling(JsonUnmappedMemberHandling.Disallow)]
public sealed record BatchEditorialMetadata
{
    public string Scope { get; init; } = "EDITORIAL_ONLY";
    public string JobId { get; init; } = "";
    public string ParametersFingerprint { get; init; } = "";
    public EditorialFacets Facets { get; init; } = new();
    public void Validate()
    {
        if (Scope != "EDITORIAL_ONLY" || !Guid.TryParseExact(JobId, "N", out _) || !BatchGenerationContract.IsSha(ParametersFingerprint) || Facets == null)
            throw new InvalidDataException("Повреждена редакционная подпись партии.");
        Facets.Validate();
    }
}

[JsonUnmappedMemberHandling(JsonUnmappedMemberHandling.Disallow)]
public sealed record BatchGenerationParameters
{
    public string Topic { get; init; } = "";
    public string Act { get; init; } = "";
    public string Band { get; init; } = "UNKNOWN";
    public string Register { get; init; } = "CASUAL";
    public string Gender { get; init; } = "ANY";
    public string Voice { get; init; } = "";
    public GenerationMode Mode { get; init; } = GenerationMode.TEMPLATE;
    public int TargetCount { get; init; } = 100;
    public string Instruction { get; init; } = "";
    public string Words { get; init; } = "";
    public EditorialFacets Facets { get; init; } = new();
    public void Validate(PackSnapshot? snapshot = null)
    {
        if (TargetCount is < 1 or > 500 || string.IsNullOrWhiteSpace(Topic) || Topic.Length > 256 || string.IsNullOrWhiteSpace(Act) || Act.Length > 256
            || TextRules.IsFunctionalAct(Act) || !CandidateValidator.Bands.Contains(Band) || !CandidateValidator.Registers.Contains(Register)
            || !CandidateValidator.Genders.Contains(Gender) || !CandidateValidator.Voices.Contains(Voice, StringComparer.Ordinal)
            || !Enum.IsDefined(Mode) || Mode == GenerationMode.PATTERN && Voice != "" || Facets == null
            || string.IsNullOrWhiteSpace(Instruction) || Instruction.Length > 3000 || Words == null || Words.Length > 600)
            throw new InvalidDataException("Проверьте разговорный scope, голос, задание до 3000 символов и размер партии 1–500.");
        Facets.Validate();
        if (snapshot != null && (!snapshot.Topics.Contains(Topic, StringComparer.Ordinal) || !snapshot.Acts.Contains(Act, StringComparer.Ordinal)
            || !snapshot.Entries.Any(e => e.Kind == "PATTERN" && e.Topic == Topic && e.Act == Act)))
            throw new InvalidDataException("Партия требует существующую разговорную связку topic/act.");
        if (snapshot != null && Voice != "" && !snapshot.Entries.Any(e => e.Kind == "TEMPLATE" && e.Act == Act && e.Voice == ""
            && e.Band == "UNKNOWN" && e.Register == "NEUTRAL" && e.Profanity == "NONE" && !e.Mature))
            throw new InvalidDataException("Для базового голоса нужен чистый универсальный fallback исходного act.");
    }
    public string Fingerprint() => TextRules.Hash(JsonSerializer.Serialize(this));
    internal string PromptInstruction() => Instruction + "\nEDITORIAL_ONLY: " + JsonSerializer.Serialize(Facets)
        + "; native base voice=" + (Voice.Length == 0 ? "universal" : Voice)
        + ". Сочетай интенсивности независимо только в формулировке. Это не игровые действия или native traits."
        + " roughness не разрешает мат/mature; romance только уместно и без adult. Не выдумывай достижения, личные факты, сделки или выполненные действия. Разнообразие, без повторов.";
}

[JsonUnmappedMemberHandling(JsonUnmappedMemberHandling.Disallow)]
public sealed record BatchJobState
{
    public int Version { get; init; } = 1;
    public string Id { get; init; } = Guid.NewGuid().ToString("N");
    public string Status { get; init; } = "READY";
    public BatchGenerationParameters Parameters { get; init; } = new();
    public string ParametersFingerprint { get; init; } = "";
    public string SourceModuleRoot { get; init; } = "";
    public string SourceFingerprint { get; init; } = "";
    public string SourceFilesFingerprint { get; init; } = "";
    public List<SourceFileStamp> SourceFiles { get; init; } = [];
    public string ModelId { get; init; } = "";
    public string SettingsFingerprint { get; init; } = "";
    public int Accepted { get; init; }
    // Successfully parsed items; HTTP attempts are counted separately, including failures/cancellation.
    public int Attempted { get; init; }
    public int Rejected { get; init; }
    public int Deduplicated { get; init; }
    public int RequestCount { get; init; }
    public int NextChunk { get; init; }
    public string LastDiagnostic { get; init; } = "";
    public DateTimeOffset CreatedAtUtc { get; init; } = DateTimeOffset.UtcNow;
}

internal static class BatchGenerationContract
{
    internal static bool IsSha(string? value) => value is { Length: 64 } && value.All(c => c is >= '0' and <= '9' or >= 'a' and <= 'f');
    internal static string FilesFingerprint(IEnumerable<SourceFileStamp> files) => TextRules.Hash(JsonSerializer.Serialize(files.OrderBy(f => f.RelativePath, StringComparer.Ordinal)));
    internal static string SettingsFingerprint(StudioSettings settings) => TextRules.Hash(JsonSerializer.Serialize(new
    { settings.HighFiveRoot, settings.Endpoint, settings.ModelId, settings.Temperature, settings.MaxTokens, settings.TimeoutSeconds }));
    internal static void ValidateJob(BatchJobState job)
    {
        if (job.Version != 1 || !Guid.TryParseExact(job.Id, "N", out _) || job.Parameters == null || job.SourceFiles == null
            // Existing Reader inventory: 4 base + 2 v2 + manifest + 64 segments + 6 custom.
            || job.SourceFiles.Count is < 1 or > 77 || !IsSha(job.SourceFingerprint) || !IsSha(job.SourceFilesFingerprint)
            || !IsSha(job.SettingsFingerprint) || !IsSha(job.ParametersFingerprint) || !Path.IsPathFullyQualified(job.SourceModuleRoot ?? "")
            || string.IsNullOrWhiteSpace(job.ModelId) || job.ModelId.Length > 256 || job.LastDiagnostic == null || job.LastDiagnostic.Length > 512
            || job.CreatedAtUtc == default || job.CreatedAtUtc.Offset != TimeSpan.Zero
            || job.Status is not ("READY" or "RUNNING" or "PAUSED" or "CANCELLED" or "COMPLETED" or "SHORTAGE" or "FAILED_JOB" or "BLOCKED_JOB"))
            throw new InvalidDataException("Повреждённый или неподдерживаемый batch job.");
        job.Parameters.Validate();
        var maxRequests = (job.Parameters.TargetCount + 19) / 20;
        if (job.Accepted < 0 || job.Attempted < 0 || job.Attempted > job.Parameters.TargetCount || job.Accepted + job.Rejected != job.Attempted
            || job.Rejected < 0 || job.Deduplicated < 0 || job.Deduplicated > job.Rejected || job.RequestCount < 0 || job.RequestCount > maxRequests
            || job.NextChunk != job.RequestCount || job.Attempted > Math.Min(job.Parameters.TargetCount, job.NextChunk * 20)
            || job.ParametersFingerprint != job.Parameters.Fingerprint()
            || job.SourceFiles.Any(f => f == null || !IsSha(f.Sha256) || f.Bytes is < 1 or > 1048576 || string.IsNullOrWhiteSpace(f.RelativePath))
            || job.SourceFiles.Select(f => f.RelativePath).Distinct(StringComparer.Ordinal).Count() != job.SourceFiles.Count
            || job.SourceFilesFingerprint != FilesFingerprint(job.SourceFiles)
            || job.SourceFingerprint != TextRules.Hash(string.Join("\n", job.SourceFiles.Select(f => f.RelativePath + ":" + f.Sha256)))
            || job.Status == "READY" && (job.RequestCount != 0 || job.Attempted != 0)
            || job.Status == "COMPLETED" && job.Accepted != job.Parameters.TargetCount
            || job.Status == "SHORTAGE" && (job.Accepted >= job.Parameters.TargetCount || job.RequestCount != maxRequests))
            throw new InvalidDataException("Счётчики, параметры или исходная подпись batch job повреждены.");
        foreach (var stamp in job.SourceFiles) PathSafety.ResolveRelative(job.SourceModuleRoot!, stamp.RelativePath);
    }
}

/// <summary>Bounded serial LM orchestration; every candidate/cursor checkpoint is one existing atomic session write.</summary>
public sealed class BatchGenerationService(WorkspaceStore store, SessionState session, LmStudioClient client)
{
    private int running;
    private volatile bool pauseRequested;
    private volatile bool cancelRequested;
    private readonly object cancellationLock = new();
    private CancellationTokenSource? activeCancellation;
    public BatchJobState? Current => session.BatchJob;

    public BatchJobState Start(PackSnapshot snapshot, StudioSettings settings, BatchGenerationParameters parameters, bool confirmReplacement = false)
    {
        if (Volatile.Read(ref running) != 0) throw new InvalidOperationException("Партия уже выполняется.");
        if (session.BatchJob != null && !confirmReplacement) throw new InvalidDataException("Для новой партии нужно явно подтвердить замену сохранённого job.");
        pauseRequested = false; cancelRequested = false;
        settings.Validate(); parameters.Validate(snapshot); CheckSourceSettings(snapshot, settings); AssertCurrentSources(snapshot);
        if (session.Candidates.Count + parameters.TargetCount > 5000) throw new InvalidDataException("Недостаточно места в очереди: предел 5000 кандидатов. Разделите партию явно.");
        var job = new BatchJobState
        {
            Parameters = parameters with { Facets = parameters.Facets with { } }, ParametersFingerprint = parameters.Fingerprint(),
            SourceModuleRoot = snapshot.ModuleRoot, SourceFingerprint = snapshot.Fingerprint,
            SourceFiles = snapshot.Files.ToList(), SourceFilesFingerprint = BatchGenerationContract.FilesFingerprint(snapshot.Files),
            ModelId = settings.ModelId, SettingsFingerprint = BatchGenerationContract.SettingsFingerprint(settings)
        };
        Commit(job, []); return job;
    }

    public void RequestPause() => pauseRequested = true;
    public void RequestCancel()
    {
        cancelRequested = true;
        lock (cancellationLock) activeCancellation?.Cancel();
    }

    public async Task<BatchJobState> RunAsync(PackSnapshot snapshot, StudioSettings settings, BatchGenerationParameters parameters,
        string apiKey, IProgress<BatchJobState>? progress = null, CancellationToken token = default)
    {
        if (Interlocked.CompareExchange(ref running, 1, 0) != 0) throw new InvalidOperationException("Одновременно допускается только один запрос партии.");
        BatchJobState? inFlight = null;
        CancellationTokenSource? runCancellation = null;
        try
        {
            // StudioSettings is mutable in the settings form: pin one copy for this explicit run.
            settings = new StudioSettings
            {
                HighFiveRoot = settings.HighFiveRoot, Endpoint = settings.Endpoint, ModelId = settings.ModelId,
                Temperature = settings.Temperature, MaxTokens = settings.MaxTokens, TimeoutSeconds = settings.TimeoutSeconds
            };
            var job = session.BatchJob ?? throw new InvalidDataException("Нет сохранённой партии.");
            BatchGenerationContract.ValidateJob(job);
            try
            {
                settings.Validate(); parameters.Validate(snapshot); CheckSourceSettings(snapshot, settings);
                if (job.ParametersFingerprint != parameters.Fingerprint() || job.SettingsFingerprint != BatchGenerationContract.SettingsFingerprint(settings)
                    || job.SourceModuleRoot != snapshot.ModuleRoot || job.SourceFingerprint != snapshot.Fingerprint
                    || job.SourceFilesFingerprint != BatchGenerationContract.FilesFingerprint(snapshot.Files))
                    throw new InvalidDataException("Drift");
                AssertCurrentSources(snapshot);
            }
            catch (Exception e) when (e is InvalidDataException or IOException or UnauthorizedAccessException)
            { return Stop(job, "BLOCKED_JOB", "SOURCE_MODEL_SETTINGS_OR_PARAMETERS_DRIFT: создайте новую партию с явным подтверждением.", progress); }
            if (job.Status is not ("READY" or "RUNNING" or "PAUSED")) { progress?.Report(job); return job; }
            var cancel = runCancellation = CancellationTokenSource.CreateLinkedTokenSource(token);
            lock (cancellationLock) activeCancellation = cancel;
            job = job with { Status = "RUNNING", LastDiagnostic = "" }; Commit(job, []); progress?.Report(job);
            var maximumRequests = (parameters.TargetCount + 19) / 20;
            while (job.NextChunk < maximumRequests)
            {
                if (cancelRequested || cancel.IsCancellationRequested) return Stop(job, "CANCELLED", "CANCELLED: принятые ранее DRAFT сохранены.", progress);
                if (pauseRequested) return Stop(job, "PAUSED", "PAUSED: продолжение требует ручного Resume.", progress);
                try { AssertCurrentSources(snapshot); }
                catch (Exception e) when (e is InvalidDataException or IOException or UnauthorizedAccessException)
                { return Stop(job, "BLOCKED_JOB", "SOURCE_DRIFT: исходные bytes изменились; нужна новая партия.", progress); }
                var requested = Math.Min(20, parameters.TargetCount - job.NextChunk * 20);
                var issued = job with { RequestCount = job.RequestCount + 1, NextChunk = job.NextChunk + 1 };
                inFlight = issued;
                List<DraftItem> drafts;
                try
                {
                    drafts = await client.GenerateAsync(settings, apiKey, snapshot,
                        new(parameters.Topic, parameters.Act, parameters.Band, parameters.Register, parameters.Gender,
                            parameters.PromptInstruction(), parameters.Words, requested, parameters.Mode), cancel.Token).ConfigureAwait(false);
                }
                catch (LmStudioException e)
                {
                    var cancelled = e.Diagnostic.Code == LmDiagnosticCode.CANCELLED || cancel.IsCancellationRequested || cancelRequested;
                    return Stop(issued, cancelled ? "CANCELLED" : "FAILED_JOB", e.Diagnostic.ToString(), progress);
                }
                if (cancelRequested || cancel.IsCancellationRequested) return Stop(issued, "CANCELLED", "CANCELLED: текущий ответ не принят.", progress);
                try { AssertCurrentSources(snapshot); }
                catch (Exception e) when (e is InvalidDataException or IOException or UnauthorizedAccessException)
                { return Stop(issued, "BLOCKED_JOB", "SOURCE_DRIFT: ответ текущего запроса не принят.", progress); }

                var accepted = new List<Candidate>(); var rejected = 0; var duplicates = 0;
                var normalized = snapshot.Entries.Where(e => e.Kind is "PATTERN" or "TEMPLATE").Select(e => TextRules.Normalize(e.Text))
                    .Concat(session.Candidates.Select(c => TextRules.Normalize(c.Text))).ToHashSet(StringComparer.Ordinal);
                foreach (var item in drafts)
                {
                    var candidate = new Candidate
                    {
                        Id = TextRules.Hash(job.Id + ":" + job.NextChunk + ":" + accepted.Count + ":" + rejected)[..32],
                        Kind = item.Kind, Text = item.Text, Topic = parameters.Topic, Act = parameters.Act,
                        Band = parameters.Band, Register = parameters.Register, Gender = parameters.Gender,
                        Voice = item.Kind == "TEMPLATE" ? parameters.Voice : "", SourceFingerprint = job.SourceFingerprint,
                        ModelId = job.ModelId, Instruction = parameters.PromptInstruction(), Rationale = item.Reason, Status = "DRAFT",
                        EditorialMetadata = new() { JobId = job.Id, ParametersFingerprint = job.ParametersFingerprint, Facets = parameters.Facets with { } }
                    };
                    var peers = session.Candidates.Concat(accepted).ToList();
                    var shortlist = SemanticDuplicateScout.Search(snapshot, candidate, peers, 3, cancel.Token);
                    var duplicate = normalized.Contains(TextRules.Normalize(candidate.Text)) || shortlist.Matches.Any(m => m.Exact);
                    var issues = CandidateValidator.Validate(candidate, snapshot, peers);
                    if (duplicate || issues.Any(i => i.Severity == IssueSeverity.Error))
                    { rejected++; if (duplicate) duplicates++; continue; }
                    if (issues.Any(i => i.Code is "NEAR_DUPLICATE" or "ACTION_CLAIM"))
                        candidate.Rationale += " [WARN: лексическое сходство или игровой факт; ручное ревью, NOT_SEMANTIC_VERIFIED.]";
                    accepted.Add(candidate); normalized.Add(TextRules.Normalize(candidate.Text));
                }
                if (cancelRequested || cancel.IsCancellationRequested) return Stop(issued, "CANCELLED", "CANCELLED: текущий пакет не принят.", progress);
                try { AssertCurrentSources(snapshot); }
                catch (Exception e) when (e is InvalidDataException or IOException or UnauthorizedAccessException)
                { return Stop(issued, "BLOCKED_JOB", "SOURCE_DRIFT: текущий пакет не принят.", progress); }
                job = issued with
                {
                    Accepted = job.Accepted + accepted.Count, Attempted = job.Attempted + drafts.Count,
                    Rejected = job.Rejected + rejected, Deduplicated = job.Deduplicated + duplicates
                };
                if (job.Accepted == parameters.TargetCount) job = job with { Status = "COMPLETED" };
                else if (job.NextChunk == maximumRequests) job = job with { Status = "SHORTAGE", LastDiagnostic = "SHORTAGE: принятых уникальных DRAFT меньше цели; автоматических повторов нет." };
                Commit(job, accepted); inFlight = null; progress?.Report(job);
            }
            return job;
        }
        catch (OperationCanceledException)
        { return Stop(inFlight ?? session.BatchJob!, "CANCELLED", "CANCELLED: текущий пакет не принят.", progress); }
        finally
        {
            lock (cancellationLock) { activeCancellation = null; runCancellation?.Dispose(); }
            Volatile.Write(ref running, 0);
        }
    }

    private BatchJobState Stop(BatchJobState job, string status, string diagnostic, IProgress<BatchJobState>? progress)
    {
        var stopped = job with { Status = status, LastDiagnostic = diagnostic[..Math.Min(512, diagnostic.Length)] };
        Commit(stopped, []);
        if (status == "PAUSED") pauseRequested = false;
        if (status == "CANCELLED") cancelRequested = false;
        progress?.Report(stopped); return stopped;
    }
    private void Commit(BatchJobState job, IReadOnlyList<Candidate> accepted)
    {
        var projected = new SessionState
        {
            Version = session.Version, Candidates = session.Candidates.Concat(accepted).ToList(),
            Lessons = session.Lessons, ScopedLessons = session.ScopedLessons, BatchJob = job
        };
        store.SaveSession(projected);
        // File.Move succeeded: only now expose accepted peers or the advanced cursor to the caller.
        session.Candidates = projected.Candidates; session.BatchJob = job;
    }
    private static void CheckSourceSettings(PackSnapshot snapshot, StudioSettings settings)
    {
        if (!PathSafety.Canonical(settings.HighFiveRoot).Equals(PathSafety.Canonical(snapshot.ModuleRoot), OperatingSystem.IsWindows() ? StringComparison.OrdinalIgnoreCase : StringComparison.Ordinal))
            throw new InvalidDataException("Настройки и импорт относятся к разным исходным каталогам.");
    }
    private static void AssertCurrentSources(PackSnapshot snapshot)
    {
        var current = new PackReader().Load(snapshot.ModuleRoot);
        if (current.Fingerprint != snapshot.Fingerprint || !current.Files.SequenceEqual(snapshot.Files))
            throw new InvalidDataException("Исходные bytes или состав импортированного пака изменились.");
    }
}
