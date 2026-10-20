namespace PhantomSemanticStudio.Core;

public static class StageBatchSelection
{
    public const int MaxItems = 20;

    public static IReadOnlyList<string> SelectExactIds(IReadOnlyList<Candidate> allPeers, IEnumerable<string> selectedIds)
    {
        if (allPeers == null || selectedIds == null) throw new InvalidDataException("Нужны кандидаты и явный список выбранных ID.");
        var requested = new HashSet<string>(StringComparer.Ordinal);
        foreach (var id in selectedIds)
        {
            if (string.IsNullOrWhiteSpace(id) || !requested.Add(id))
                throw new InvalidDataException("Выбранный ID пуст или повторяется. Партия отклонена целиком.");
            if (requested.Count > MaxItems) throw new InvalidDataException("Можно выбрать не более 20 кандидатов. Партия не сокращается автоматически.");
        }
        if (requested.Count == 0) throw new InvalidDataException("Отметьте от 1 до 20 кандидатов вручную.");
        var peers = new Dictionary<string, Candidate>(StringComparer.Ordinal);
        foreach (var c in allPeers)
            if (c == null || string.IsNullOrWhiteSpace(c.Id) || !peers.TryAdd(c.Id, c))
                throw new InvalidDataException("Повреждённый или повторный ID в списке кандидатов. Партия отклонена целиком.");
        foreach (var id in requested)
        {
            if (!peers.TryGetValue(id, out var candidate)) throw new InvalidDataException("Выбранный ID не существует: " + id);
            if (!CandidateReview.IsCurrent(candidate)) throw new InvalidDataException("Одобрение отсутствует или устарело: " + id + ". Нужно новое ручное ревью.");
        }
        // Detached ID snapshot: caller cannot mutate selection or receive mutable Candidate references.
        return Array.AsReadOnly(requested.Order(StringComparer.Ordinal).ToArray());
    }
}
