namespace PhantomSemanticStudio.Core;

/// <summary>Инспектор humanized-каталога. Не воспроизводит gameplay/identity/social ownership Java.</summary>
public sealed class PackPreview
{
    private readonly Queue<string> recent = new();
    internal PackPreview Copy()
    {
        var copy = new PackPreview();
        foreach (var id in recent) copy.recent.Enqueue(id);
        return copy;
    }
    public void Reset() => recent.Clear();
    public PreviewResult Reply(PackSnapshot snapshot, string input, string band, string register)
    {
        if (TextRules.CodePoints(input) > 256) return Missing("Вход длиннее 256 символов.");
        var text = TextRules.Normalize(input);
        var aliases = snapshot.Entries.Where(e => e.Kind == "ALIAS").ToDictionary(e => e.Id, e => e.Text, StringComparer.Ordinal);
        text = string.Join(" ", text.Split(' ', StringSplitOptions.RemoveEmptyEntries).Select(t => aliases.GetValueOrDefault(t, t)));
        string captured = "";
        var match = snapshot.Entries.Where(e => e.Kind == "PATTERN").OrderByDescending(e => e.Priority)
            .ThenByDescending(e => TextRules.Normalize(e.Text).Length).ThenBy(e => e.Id, StringComparer.Ordinal)
            .FirstOrDefault(e => Match(TextRules.Normalize(e.Text), text, out captured));
        if (match == null) return Missing("Совпадение не найдено. Исправление можно оформить кандидатом; автоматического расширения нет.");
        if (TextRules.IsFunctionalAct(match.Act))
            return Result(match, PreviewStatus.FUNCTIONAL_OR_MEMORY_UNSUPPORTED, "Это функциональная/identity-ветка. Её проверяет сервер, а не этот инспектор.");
        if (match.Fact.Length > 0 || match.Recall.Length > 0)
            return Result(match, PreviewStatus.FUNCTIONAL_OR_MEMORY_UNSUPPORTED, "Ветка использует память. Точный ответ здесь не симулируется.");
        // Alias matching must not replace the value captured by this same branch in the original input.
        var capturedFromInput = Match(TextRules.Normalize(match.Text), TextRules.Normalize(input), out var inputCapture) && inputCapture == captured;
        var eligible = snapshot.Entries.Where(e => e.Kind == "TEMPLATE" && e.Act == match.Act && !e.Mature && e.Profanity == "NONE")
            .Where(e => e.Band == "UNKNOWN" || e.Band == band)
            .Where(e => e.Register == "NEUTRAL" || e.Register == "CASUAL" && register == "CASUAL")
            .OrderBy(e => e.Id, StringComparer.Ordinal).ToArray();
        if (eligible.Length == 0)
            return Result(match, PreviewStatus.NO_ELIGIBLE_TEMPLATE, "Нет чистого шаблона для выбранных отношений и стиля.");
        var safe = eligible.Select(e => (Entry: e, Text: SafeRender(e.Text, capturedFromInput ? inputCapture : "")))
            .Where(e => e.Text != null).ToArray();
        if (safe.Length == 0)
            return Result(match, PreviewStatus.TEMPLATE_CONTEXT_UNAVAILABLE, "Подходящие шаблоны требуют недоступного контекста или содержат неподдержанные подстановки.");
        var responseIndex = Array.FindIndex(safe, e => !recent.Contains(e.Entry.Id));
        var repeated = responseIndex < 0;
        var response = safe[repeated ? 0 : responseIndex];
        recent.Enqueue(response.Entry.Id); while (recent.Count > 8) recent.Dequeue();
        var note = "ПРИБЛИЖЁННЫЙ ПРЕДПРОСМОТР: без functional-first, social gates, persona, Java selector и постоянной памяти. Пол не фильтруется.";
        if (repeated) note += " REPEAT_FALLBACK: все безопасные шаблоны недавно показаны; повтор существующей реплики.";
        return Result(match, PreviewStatus.PACK_CATALOG_APPROXIMATE, note, response.Entry, response.Text!);
    }
    private static string? SafeRender(string template, string captured)
    {
        if (string.IsNullOrWhiteSpace(template)) return null;
        var output = template;
        if (template.Contains("{value}", StringComparison.Ordinal))
        {
            // Only the matched bounded input fragment is known. Aliases may match but must not invent values.
            if (captured.Length == 0 || TextRules.CodePoints(captured) > 256 || captured.Contains('{') || captured.Contains('}')) return null;
            output = template.Replace("{value}", captured, StringComparison.Ordinal);
        }
        return output.Contains('{') || output.Contains('}') ? null : output;
    }
    private static PreviewResult Result(PackEntry pattern, PreviewStatus status, string note, PackEntry? template = null, string text = "")
        => new(true, text, pattern.Id, template?.Id ?? "", pattern.Topic, pattern.Act, status + ": " + note) { Status = status };
    private static bool Match(string pattern, string text, out string value)
    {
        value = "";
        if (!pattern.Contains("{value}")) return pattern == text;
        if (pattern.IndexOf("{value}", StringComparison.Ordinal) != pattern.LastIndexOf("{value}", StringComparison.Ordinal)) return false;
        if (pattern.StartsWith("{value}", StringComparison.Ordinal))
        {
            var suffix = pattern[7..]; if (suffix.Length == 0 || !text.EndsWith(suffix, StringComparison.Ordinal)) return false;
            value = text[..^suffix.Length].Trim(); return value.Length > 0;
        }
        if (pattern.EndsWith("{value}", StringComparison.Ordinal))
        {
            var prefix = pattern[..^7]; if (prefix.Length == 0 || !text.StartsWith(prefix, StringComparison.Ordinal)) return false;
            value = text[prefix.Length..].Trim(); return value.Length > 0;
        }
        return false;
    }
    private static PreviewResult Missing(string reason) => new(false, "", "", "", "", "", reason);
}
