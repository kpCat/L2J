using System.Text;
using System.Text.Encodings.Web;
using System.Text.Json;

namespace PhantomSemanticStudio.Core;

public sealed class WorkspaceStore : IDisposable
{
    public static readonly JsonSerializerOptions JsonOptions = new()
    {
        WriteIndented = true, Encoder = JavaScriptEncoder.UnsafeRelaxedJsonEscaping, MaxDepth = 32
    };
    private const int MaxStateBytes = 16 * 1024 * 1024;
    private readonly FileStream exclusiveLock;
    public string Root { get; }
    public string ProtectedRoot { get; private set; }
    public WorkspaceStore(string root, string sourceModule)
    {
        Root = PathSafety.Canonical(root); ProtectedRoot = PathSafety.RepositoryBoundary(sourceModule);
        PathSafety.AssertDisjoint(ProtectedRoot, Root);
        Directory.CreateDirectory(Root);
        PathSafety.AssertNoReparsePoints(Root);
        exclusiveLock = new FileStream(Path.Combine(Root, "workspace.lock"), FileMode.OpenOrCreate, FileAccess.ReadWrite, FileShare.None);
    }
    public void ProtectSource(string module)
    {
        var source = PathSafety.RepositoryBoundary(module);
        PathSafety.AssertDisjoint(source, Root); ProtectedRoot = source;
    }
    public StudioSettings LoadSettings() => Read<StudioSettings>("settings.json") ?? new StudioSettings();
    public SessionState LoadSession()
    {
        var state = Read<SessionState>("session.json") ?? new SessionState();
        ValidateState(state);
        return state;
    }
    private static void ValidateState(SessionState state)
    {
        if (state.Version != 1 || state.Candidates == null || state.Lessons == null || state.ScopedLessons == null
            || state.Candidates.Count > 5000 || state.Lessons.Count + state.ScopedLessons.Count > 500)
            throw new InvalidDataException("Неподдерживаемый или слишком большой workspace.");
        if (state.Candidates.Any(c => c == null || c.Text == null || c.Act == null || c.Topic == null || string.IsNullOrWhiteSpace(c.Id)
            || c.Kind == null || c.Band == null || c.Register == null || c.Gender == null || c.SourceFingerprint == null
            || c.ModelId == null || c.Instruction == null || c.Rationale == null || c.Status == null || c.ReviewNote == null || c.ApprovedFingerprint == null)
            || state.Candidates.Select(c => c.Id).Distinct(StringComparer.Ordinal).Count() != state.Candidates.Count)
            throw new InvalidDataException("Повреждённые данные кандидатов.");
        foreach (var candidate in state.Candidates)
            if (candidate.SemanticReview != null) SemanticDuplicateScout.ValidateEvidence(candidate.SemanticReview);
        if (state.Lessons.Any(l => l == null || l.Length > 4000) || state.ScopedLessons.Any(l => l == null
            || string.IsNullOrWhiteSpace(l.Text) || l.Text.Length > 4000 || l.Id == null || l.Topic == null || l.Act == null
            || l.Band == null || l.Register == null || l.Gender == null || l.SourceFingerprint == null))
            throw new InvalidDataException("Повреждённая история замечаний.");
    }
    public void SaveSettings(StudioSettings settings) { settings.Validate(); ProtectSource(settings.HighFiveRoot); Write("settings.json", settings); }
    public void SaveSession(SessionState state)
    {
        ValidateState(state);
        Write("session.json", state);
    }
    public void SaveSnapshot(PackSnapshot snapshot) => Write("source-snapshot.json", new
    {
        snapshot.ModuleRoot, snapshot.Fingerprint, snapshot.ImportedAtUtc, snapshot.Files,
        Patterns = snapshot.Entries.Count(e => e.Kind == "PATTERN"),
        Templates = snapshot.Entries.Count(e => e.Kind == "TEMPLATE"), snapshot.Warnings
    });
    public string GetExportPath()
    {
        Check(); var folder = PathSafety.ResolveRelative(Root, "exports");
        Directory.CreateDirectory(folder); PathSafety.AssertNoReparsePoints(folder);
        return PathSafety.ResolveRelative(Root, "exports/review-" + DateTime.UtcNow.ToString("yyyyMMdd-HHmmss") + "-" + Guid.NewGuid().ToString("N")[..8] + ".zip");
    }
    public void Check() => PathSafety.AssertDisjoint(ProtectedRoot, Root);
    private T? Read<T>(string relative)
    {
        var path = PathSafety.ResolveRelative(Root, relative);
        if (!File.Exists(path)) return default;
        if (new FileInfo(path).Length > MaxStateBytes) throw new InvalidDataException("Слишком большой файл workspace: " + relative);
        return JsonSerializer.Deserialize<T>(File.ReadAllBytes(path), JsonOptions);
    }
    private void Write<T>(string relative, T data)
    {
        Check(); var destination = PathSafety.ResolveRelative(Root, relative);
        var bytes = JsonSerializer.SerializeToUtf8Bytes(data, JsonOptions);
        if (bytes.Length > MaxStateBytes) throw new InvalidDataException("Превышен предел файла workspace 16 MiB.");
        var temporary = PathSafety.ResolveRelative(Root, relative + ".tmp-" + Guid.NewGuid().ToString("N"));
        try
        {
            using (var stream = new FileStream(temporary, FileMode.CreateNew, FileAccess.Write, FileShare.None))
            { stream.Write(bytes); stream.Flush(true); }
            Check(); File.Move(temporary, destination, true);
        }
        finally { if (File.Exists(temporary)) File.Delete(temporary); }
    }
    public void Dispose() => exclusiveLock.Dispose();
}
