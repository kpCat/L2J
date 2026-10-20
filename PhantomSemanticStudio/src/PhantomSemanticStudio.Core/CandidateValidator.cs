namespace PhantomSemanticStudio.Core;

public static class CandidateValidator
{
    public static readonly string[] Bands = ["UNKNOWN", "NEUTRAL", "FAMILIAR", "TRUSTED", "RIVAL", "TENSE", "HOSTILE"];
    public static readonly string[] Registers = ["NEUTRAL", "CASUAL"];
    public static readonly string[] Genders = ["ANY", "FEMALE", "MALE"];

    public static List<ValidationIssue> Validate(Candidate candidate, PackSnapshot snapshot, IEnumerable<Candidate> peers)
    {
        var issues = new List<ValidationIssue>();
        void Error(string code, string message) => issues.Add(new(IssueSeverity.Error, code, message));
        void Warn(string code, string message) => issues.Add(new(IssueSeverity.Warning, code, message));
        Warn("SEMANTIC_NOT_CHECKED", "Смысловые повторы, грамматика, условия применимости и полнота profanity/mature проверки не проверены. Требуется ручное ревью; XML не экспортируется.");
        if (candidate.SourceFingerprint != snapshot.Fingerprint) Error("SOURCE_MISMATCH", "Кандидат создан для другой версии исходного пака. Нужно повторное ревью, автоматической перепривязки нет.");
        if (candidate.Kind is not ("PATTERN" or "TEMPLATE")) Error("KIND", "Разрешены только PATTERN/TEMPLATE.");
        if (!snapshot.Acts.Contains(candidate.Act, StringComparer.Ordinal)) Error("UNKNOWN_ACT", "Act отсутствует в импортированном каталоге.");
        if (TextRules.IsFunctionalAct(candidate.Act)) Error("FUNCTIONAL_ACT", "Игровые и identity-действия не редактируются через генератор разговоров.");
        if (!snapshot.Topics.Contains(candidate.Topic, StringComparer.Ordinal)) Error("UNKNOWN_TOPIC", "Тема отсутствует в исходном каталоге.");
        if (!snapshot.Entries.Any(x => x.Kind == "PATTERN" && x.Act == candidate.Act && x.Topic == candidate.Topic))
            Error("TOPIC_ACT", "Связка topic/act ещё не существует. Новую связку нужно отдельно проектировать и проверять.");
        if (!Bands.Contains(candidate.Band) || !Registers.Contains(candidate.Register) || !Genders.Contains(candidate.Gender)) Error("ENUM", "Неизвестный пол, стиль или уровень отношений.");
        var limit = candidate.Kind == "PATTERN" ? 256 : 180;
        if (string.IsNullOrWhiteSpace(candidate.Text) || TextRules.CodePoints(candidate.Text) > limit) Error("LENGTH", $"Нужен текст длиной 1–{limit} символов.");
        if (candidate.Text.Any(c => char.IsControl(c))) Error("CONTROL", "Управляющие символы и переносы строк в реплике запрещены.");
        if (candidate.Text.Contains("```") || candidate.Text.Contains("<?xml", StringComparison.OrdinalIgnoreCase) || candidate.Text.Contains("<script", StringComparison.OrdinalIgnoreCase)) Error("CODE", "Ожидалась реплика, а не код/разметка.");
        if (!TextRules.ValidPlaceholders(candidate.Text)) Error("PLACEHOLDER", "Неизвестная подстановка.");
        // В starter нет автора новых fact/recall правил: их нельзя случайно потерять при компиляции.
        if (candidate.Kind == "PATTERN" && (candidate.Text.Contains('{') || candidate.Text.Contains('}'))) Error("PATTERN_PLACEHOLDER", "Новые patterns с памятью/подстановками требуют отдельной задачи runtime-проверки.");
        if (candidate.Kind == "TEMPLATE" && (candidate.Text.Contains("{memory}") || candidate.Text.Contains("{value}"))) Warn("CONTEXT_VALUE", "Проверьте, откуда эта ветка получает {memory}/{value}; отображение может оказаться пустым.");
        if (candidate.Gender != "ANY") Warn("GENDER_RUNTIME", "У обычных шаблонов v3 нет фильтра пола: кандидат сохранится для ревью, но не попадёт даже в предложенный XML.");
        var normalized = TextRules.Normalize(candidate.Text);
        var sameKind = snapshot.Entries.Where(x => x.Kind == candidate.Kind).ToList();
        var peerList = peers.Where(x => x.Id != candidate.Id && x.Status != "REJECTED" && x.Kind == candidate.Kind).ToList();
        var exact = sameKind.FirstOrDefault(x => TextRules.Normalize(x.Text) == normalized);
        var peerExact = peerList.FirstOrDefault(x => TextRules.Normalize(x.Text) == normalized);
        if (exact != null) Error("EXACT_DUPLICATE", $"Точный нормализованный повтор: {exact.Id} ({exact.SourceFile}:{exact.SourceLine}).");
        if (peerExact != null) Error("EXACT_DUPLICATE", "Такой текст уже есть среди кандидатов: " + peerExact.Id);
        if (exact == null && peerExact == null && normalized.Length >= 8)
        {
            // Это ЛЕКСИЧЕСКОЕ сходство, не embedding/смысловая экспертиза.
            var nearby = sameKind.Select(x => (x.Id, x.Text, Score: TextRules.Similarity(candidate.Text, x.Text)))
                .Concat(peerList.Select(x => (x.Id, x.Text, Score: TextRules.Similarity(candidate.Text, x.Text))))
                .Where(x => x.Score >= 0.72).OrderByDescending(x => x.Score).Take(3);
            foreach (var match in nearby) Warn("NEAR_DUPLICATE", $"Лексическое сходство {match.Score:P0}: [{match.Id}] {match.Text}");
        }
        if (candidate.Text.Contains("я всегда готов", StringComparison.OrdinalIgnoreCase) || candidate.Text.Contains("отличный вопрос", StringComparison.OrdinalIgnoreCase))
            Warn("CLICHE", "Похоже на шаблонную речь ассистента, а не игрового персонажа.");
        if (candidate.Text.Contains("телепортировал", StringComparison.OrdinalIgnoreCase) || candidate.Text.Contains("выдал тебе", StringComparison.OrdinalIgnoreCase) || candidate.Text.Contains("бафнул", StringComparison.OrdinalIgnoreCase))
            Warn("ACTION_CLAIM", "Текст может ложно сообщать об игровом действии. Сверьте вручную; модель не владеет состоянием игры.");
        var ownNew = peerList.Count(x => x.Status != "REJECTED") + 1;
        if (sameKind.Count + ownNew > (candidate.Kind == "PATTERN" ? 8192 : 32768)) Error("CAPACITY", "Кандидаты превышают действующий лимит v3. Автоматически повышать лимиты нельзя.");
        return issues;
    }
}

public static class CandidateReview
{
    public static string Fingerprint(Candidate c) => TextRules.Hash(System.Text.Json.JsonSerializer.Serialize(new
    {
        c.Id, c.Kind, c.Text, c.Topic, c.Act, c.Band, c.Register, c.Gender, c.SourceFingerprint, c.ModelId, c.Instruction, c.Rationale,
        c.ReviewNote, c.ReviewedAtUtc
    }));
    public static bool IsCurrent(Candidate c) => c.Status == "APPROVED" && !string.IsNullOrWhiteSpace(c.ReviewNote)
        && c.ReviewedAtUtc != null && c.ApprovedFingerprint == Fingerprint(c);
    public static void Approve(Candidate c, PackSnapshot snapshot, IEnumerable<Candidate> peers, string note)
    {
        var issues = CandidateValidator.Validate(c, snapshot, peers);
        if (issues.Any(x => x.Severity == IssueSeverity.Error)) throw new InvalidDataException("Нельзя одобрить: " + string.Join("; ", issues.Where(x => x.Severity == IssueSeverity.Error).Select(x => x.Message)));
        if (string.IsNullOrWhiteSpace(note)) throw new InvalidDataException("Введите короткую отметку ручной проверки. Автоодобрения нет.");
        c.Status = "APPROVED"; c.ReviewNote = note.Trim(); c.ReviewedAtUtc = DateTimeOffset.UtcNow; c.ApprovedFingerprint = Fingerprint(c);
    }
    public static void Edit(Candidate c, string text)
    {
        c.SemanticReview = null;
        c.Text = text.Trim(); c.Status = "DRAFT"; c.ApprovedFingerprint = ""; c.ReviewedAtUtc = null; c.ReviewNote = "";
    }
    public static void Reject(Candidate c)
    {
        c.Status = "REJECTED"; c.ApprovedFingerprint = ""; c.ReviewedAtUtc = DateTimeOffset.UtcNow;
    }
}
