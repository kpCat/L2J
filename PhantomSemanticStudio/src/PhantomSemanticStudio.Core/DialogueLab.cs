using System.Text;

namespace PhantomSemanticStudio.Core;

public enum WorldScope { AUTO, GAME, REAL, MIXED, UNKNOWN }
public sealed record DialogueTurn(string Id, string UserText, string InputWorldHint, string WorldHint, bool Ambiguous,
    string PackStatus, string PatternId, string TemplateId, string Act, string Topic, string PackFingerprint,
    string PackText, string Notes);
public sealed record DialogueMessage(string Role, string Text, string TurnId);
public sealed class DialogueProposal
{
    public DialogueTurn Turn { get; }
    internal Guid SessionId { get; }
    internal long Revision { get; }
    internal PackPreview Inspector { get; }
    internal DialogueProposal(DialogueTurn turn, Guid sessionId, long revision, PackPreview inspector)
    { Turn = turn; SessionId = sessionId; Revision = revision; Inspector = inspector; }
}

/// <summary>Эфемерная редакционная сессия. Ответы только из read-only инспектора, не Java runtime.</summary>
public sealed partial class DialogueLabSession
{
    private readonly object sync = new();
    private readonly Guid sessionId = Guid.NewGuid();
    private readonly PackSnapshot baseline;
    private readonly List<DialogueTurn> turns = [];
    private readonly List<DialogueMessage> messages = [];
    private PackPreview inspector = new();
    private WorldScope world = WorldScope.AUTO;
    private long revision;
    private string sourceStatus = "CURRENT_SOURCE";
    private int noteCount;
    public DialogueLabSession(PackSnapshot snapshot)
    {
        if (string.IsNullOrWhiteSpace(snapshot.Fingerprint) || snapshot.Entries.Count == 0)
            throw new InvalidDataException("Нужен успешно импортированный Semantic Pack.");
        baseline = snapshot with { Files = snapshot.Files.ToList(), Entries = snapshot.Entries.ToList(),
            Topics = snapshot.Topics.ToList(), Acts = snapshot.Acts.ToList(), Warnings = snapshot.Warnings.ToList() };
    }
    public IReadOnlyList<DialogueTurn> Turns { get { lock (sync) return Array.AsReadOnly(turns.ToArray()); } }
    public IReadOnlyList<DialogueMessage> Messages { get { lock (sync) return Array.AsReadOnly(messages.ToArray()); } }
    public string SourceStatus { get { lock (sync) return sourceStatus; } }
    public string World { get { lock (sync) return world.ToString(); } }
    public long Revision { get { lock (sync) return revision; } }
    public string PackFingerprint => baseline.Fingerprint;
    public string Transcript
    {
        get
        {
            lock (sync)
            {
                var text = string.Join("\r\n\r\n", messages.Select(m => m.Role + "\r\n" + m.Text));
                return text.Length <= 100000 ? text : "[Начало скрыто: ограничение отображения; сессия до 200 реплик]\r\n" + text[^99000..];
            }
        }
    }
    public void SetWorld(string mode)
    {
        if (!Enum.TryParse<WorldScope>(mode, false, out var next) || next == WorldScope.UNKNOWN || !Enum.IsDefined(next))
            throw new InvalidDataException("Выберите AUTO, GAME, REAL или MIXED.");
        lock (sync) { if (world == next) return; world = next; Invalidate(); }
    }
    private void Invalidate()
    {
        revision++;
        InvalidateMentor();
        InvalidateCorpus();
    }
    // B adds pending advice; A has no network or persistence dependency.
    partial void InvalidateMentor();
    partial void ClearMentor();
    partial void InvalidateCorpus();
    partial void ClearCorpus();
    public void MarkSourceStale()
    {
        lock (sync) { sourceStatus = "STALE_SOURCE"; Invalidate(); }
    }
    public void AssertCurrent(PackSnapshot current)
    {
        lock (sync)
        {
            if (sourceStatus != "CURRENT_SOURCE" || current.Fingerprint != baseline.Fingerprint
                || !current.Files.SequenceEqual(baseline.Files))
            {
                sourceStatus = "STALE_SOURCE"; Invalidate();
                throw new InvalidDataException("STALE_SOURCE: источник изменён. Закройте лабораторию и импортируйте пак заново.");
            }
        }
    }
    public DialogueProposal PrepareTurn(string input, string band, string register, CancellationToken token = default)
    {
        token.ThrowIfCancellationRequested();
        ValidateText(input, 1024, false);
        if (!CandidateValidator.Bands.Contains(band) || !CandidateValidator.Registers.Contains(register))
            throw new InvalidDataException("Недопустимые отношения или стиль.");
        PackPreview preview; long capturedRevision; WorldScope scope; string[] history;
        lock (sync)
        {
            if (sourceStatus != "CURRENT_SOURCE") throw new InvalidDataException("STALE_SOURCE: требуется новый импорт.");
            if (turns.Count >= 200) throw new InvalidDataException("Предел 200 реплик. Очистите локальную историю явно.");
            preview = inspector.Copy(); capturedRevision = revision; scope = world;
            history = turns.TakeLast(10).Select(t => t.UserText).ToArray();
        }
        var (hint, ambiguous) = InferWorld(input, history, scope);
        var reply = preview.Reply(baseline, input, band, register);
        var pattern = baseline.Entries.FirstOrDefault(e => e.Kind == "PATTERN" && e.Id == reply.PatternId);
        var template = baseline.Entries.FirstOrDefault(e => e.Kind == "TEMPLATE" && e.Id == reply.TemplateId);
        var matched = reply.Status == PreviewStatus.PACK_CATALOG_APPROXIMATE && reply.Text.Length > 0;
        var status = reply.Status.ToString();
        var notes = "NOT_JAVA_RUNTIME_PARITY / WORLD_ADVISORY_ONLY. " + reply.Note;
        if (pattern != null) notes += $" Priority={pattern.Priority}; pattern source={pattern.SourceFile}:{pattern.SourceLine}.";
        if (template != null) notes += $" Template source={template.SourceFile}:{template.SourceLine}.";
        if (reply.Status == PreviewStatus.FUNCTIONAL_OR_MEMORY_UNSUPPORTED) notes += " Identity, игровые действия и память не симулируются.";
        token.ThrowIfCancellationRequested();
        var turn = new DialogueTurn(Guid.NewGuid().ToString("N"), input, scope.ToString(), hint.ToString(), ambiguous,
            status, reply.PatternId, reply.TemplateId, reply.Act, reply.Topic, baseline.Fingerprint, matched ? reply.Text : "", notes);
        return new(turn, sessionId, capturedRevision, preview);
    }
    public DialogueTurn CommitTurn(DialogueProposal proposal, PackSnapshot current, CancellationToken token = default)
    {
        lock (sync)
        {
            token.ThrowIfCancellationRequested(); AssertCurrent(current);
            if (proposal.SessionId != sessionId || proposal.Revision != revision || turns.Count >= 200)
                throw new InvalidDataException("STALE: реплика или редакционный контекст изменились; результат не принят.");
            var turn = proposal.Turn; inspector = proposal.Inspector; turns.Add(turn); Invalidate();
            messages.Add(new("YOU / ВЫ", turn.UserText, turn.Id));
            messages.Add(new("PACK / ПАК", turn.PackText.Length == 0 ? "[" + turn.PackStatus + "]" : turn.PackText, turn.Id));
            return turn;
        }
    }
    public void AddEditorNote(string text)
    {
        ValidateText(text, 4000, true);
        lock (sync)
        {
            if (noteCount >= 100) throw new InvalidDataException("Предел 100 локальных заметок.");
            messages.Add(new("EDITOR_NOTE / ЗАМЕТКА", text, turns.LastOrDefault()?.Id ?? "")); noteCount++; Invalidate();
        }
    }
    public void Clear()
    {
        lock (sync) { turns.Clear(); messages.Clear(); inspector.Reset(); world = WorldScope.AUTO; noteCount = 0; Invalidate(); ClearMentor(); ClearCorpus(); }
    }
    internal static void ValidateText(string text, int max, bool multiline)
    {
        if (string.IsNullOrWhiteSpace(text) || text.Length > max || text.Any(c => char.IsControl(c) && !(multiline && c is '\r' or '\n' or '\t')))
            throw new InvalidDataException("Пустой, слишком длинный текст или управляющие символы.");
        try { _ = new UTF8Encoding(false, true).GetByteCount(text); }
        catch (EncoderFallbackException) { throw new InvalidDataException("Некорректный Unicode текст."); }
    }
    private static (WorldScope, bool) InferWorld(string input, string[] history, WorldScope mode)
    {
        if (mode != WorldScope.AUTO) return (mode, false);
        static WorldScope Detect(string text)
        {
            var words = TextRules.Normalize(text).Split(' ');
            var game = words.Any(w => w is "рейд" or "антарас" or "antharas" or "дроп" or "фарм" or "pvp" or "пвп" or "рейдбосс" || w.StartsWith("рейд", StringComparison.Ordinal));
            var real = words.Any(w => w.StartsWith("начальник", StringComparison.Ordinal) || w.StartsWith("работ", StringComparison.Ordinal) || w is "офис" or "зарплата");
            return game && real ? WorldScope.MIXED : game ? WorldScope.GAME : real ? WorldScope.REAL : WorldScope.UNKNOWN;
        }
        var detected = Detect(input);
        var boss = TextRules.Normalize(input).Split(' ').Any(w => w is "босс" or "босса" or "boss");
        if (detected == WorldScope.UNKNOWN && boss)
            detected = history.Reverse().Select(Detect).FirstOrDefault(w => w != WorldScope.UNKNOWN, WorldScope.UNKNOWN);
        return (detected, boss && detected == WorldScope.UNKNOWN);
    }
}
