namespace PhantomSemanticStudio.Core;

public sealed class SanitizedCorpusExcerpt
{
    public string CorpusId { get; }
    public string CorpusFingerprint { get; }
    public string RowId { get; }
    public string SourceHash { get; }
    public string PreviewHash { get; }
    public string Channel { get; }
    public string Timestamp { get; }
    public string Timezone => "UNSPECIFIED";
    public string Language { get; }
    public string Preview { get; }
    public bool PossiblePii { get; }
    public string Status => "SOURCE_MATERIAL_ONLY";
    internal SanitizedCorpusExcerpt(CorpusMetadata corpus, CorpusRecord row, string preview)
    {
        CorpusId = corpus.Id; CorpusFingerprint = corpus.DatasetHash; RowId = row.Id; SourceHash = row.Fingerprint;
        Preview = preview; PreviewHash = TextRules.Hash(preview); Channel = row.Channel; Timestamp = row.Timestamp;
        Language = row.Language; PossiblePii = row.PossiblePii;
    }
}
public sealed class ReviewedCorpusText
{
    public SanitizedCorpusExcerpt Source { get; }
    public string Text { get; }
    public string ReviewedHash { get; }
    public string LanguageOverride { get; }
    internal ReviewedCorpusText(SanitizedCorpusExcerpt source, string text, string language)
    { Source = source; Text = text; ReviewedHash = TextRules.Hash(text); LanguageOverride = language; }
}
public static class CorpusDialogueBridge
{
    public static IReadOnlyList<SanitizedCorpusExcerpt> Detach(CorpusMetadata metadata, IReadOnlyList<CorpusRecord> rows, bool transferConfirmed)
    {
        if (!transferConfirmed || rows.Count is < 1 or > 20 || !Hex(metadata.Id, 32) || !Hex(metadata.DatasetHash, 64)
            || rows.Select(r => r.Id).Distinct(StringComparer.Ordinal).Count() != rows.Count)
            throw new InvalidDataException("Нужно явное подтверждение полного preview 1–20 public фрагментов одного корпуса.");
        var detached = new List<SanitizedCorpusExcerpt>(rows.Count);
        foreach (var row in rows)
        {
            if (!ChatCorpusImporter.PublicChannels.Contains(row.Channel, StringComparer.Ordinal) || !Hex(row.Id, 64) || !Hex(row.Fingerprint, 64)
                || row.Language is not ("CYRILLIC" or "MIXED" or "UNKNOWN" or "EN_OR_OTHER" or "LATIN_TRANSLIT_CANDIDATE"))
                throw new InvalidDataException("Непубличный канал или повреждённая ссылка корпуса.");
            var preview = row.Preview; DialogueLabSession.ValidateText(preview, 4000, false);
            detached.Add(new(metadata, row, preview));
        }
        return detached.AsReadOnly();
    }
    public static ReviewedCorpusText Review(SanitizedCorpusExcerpt source, string editedSanitizedText, string languageOverride)
    {
        ValidateSource(source); DialogueLabSession.ValidateText(editedSanitizedText, 1024, true);
        if (languageOverride is not ("RU_TRANSLIT" or "OTHER_LANGUAGE" or "UNKNOWN")
            || CorpusLanguageTriage.Scrub(editedSanitizedText) != editedSanitizedText)
            throw new InvalidDataException("Выберите языковую гипотезу и вручную замаскируйте оставшиеся контакты/PII.");
        return new(source, editedSanitizedText, languageOverride);
    }
    internal static void ValidateSource(SanitizedCorpusExcerpt source)
    {
        if (!Hex(source.CorpusId, 32) || !Hex(source.RowId, 64) || !Hex(source.CorpusFingerprint, 64) || !Hex(source.SourceHash, 64)
            || source.PreviewHash != TextRules.Hash(source.Preview) || !ChatCorpusImporter.PublicChannels.Contains(source.Channel, StringComparer.Ordinal))
            throw new InvalidDataException("Повреждённый SOURCE_MATERIAL_ONLY preview.");
    }
    private static bool Hex(string text, int length) => text.Length == length && text.All(c => c is >= '0' and <= '9' or >= 'a' and <= 'f');
}
public sealed class CorpusTransformRequest
{
    public string Id { get; } = Guid.NewGuid().ToString("N");
    public string SourceFingerprint { get; }
    public string RequestedAction { get; }
    public IReadOnlyList<ReviewedCorpusText> Items { get; }
    internal long Revision { get; }
    internal CorpusTransformRequest(string source, string action, IReadOnlyList<ReviewedCorpusText> items, long revision)
    { SourceFingerprint = source; RequestedAction = action; Items = Array.AsReadOnly(items.ToArray()); Revision = revision; }
}
public sealed record CorpusSuggestion(string RefKey, string ReviewedTextHash, string Operation, string ProposedRussianText,
    string LanguageAssessment, string Reason, int Confidence);
public sealed record CorpusTransformResult(CorpusTransformRequest Request, IReadOnlyList<CorpusSuggestion> Suggestions);

public sealed partial class DialogueLabSession
{
    private CorpusTransformRequest? activeCorpus;
    private IReadOnlyList<CorpusSuggestion> corpusSuggestions = Array.Empty<CorpusSuggestion>();
    private string corpusAdviceStatus = "NONE";
    public IReadOnlyList<CorpusSuggestion> CorpusSuggestions { get { lock (sync) return corpusSuggestions; } }
    public string CorpusAdviceStatus { get { lock (sync) return corpusAdviceStatus; } }
    partial void InvalidateCorpus()
    {
        if (activeCorpus != null || corpusSuggestions.Count > 0) corpusAdviceStatus = "STALE";
        activeCorpus = null;
    }
    partial void ClearCorpus()
    { activeCorpus = null; corpusSuggestions = Array.Empty<CorpusSuggestion>(); corpusAdviceStatus = "NONE"; }
    public CorpusTransformRequest BeginCorpusTransform(IReadOnlyList<ReviewedCorpusText> items, bool piiReviewed, bool sharingConsent, string requestedAction)
    {
        lock (sync)
        {
            if (!piiReviewed || !sharingConsent || sourceStatus != "CURRENT_SOURCE" || activeCorpus != null || activeMentor != null
                || items.Count is < 1 or > 3 || items.Select(i => i.Source.CorpusId + "/" + i.Source.RowId).Distinct(StringComparer.Ordinal).Count() != items.Count
                || requestedAction is not ("RESTORE_RU_TRANSLIT" or "TRANSLATE_TO_RUSSIAN" or "LEAVE_AS_IS"))
                throw new InvalidDataException("BLOCKED_LM: выберите 1–3 фрагмента, проверьте PII и отдельно разрешите точный локальный запрос.");
            foreach (var item in items)
            {
                CorpusDialogueBridge.Review(item.Source, item.Text, item.LanguageOverride);
                if (item.ReviewedHash != TextRules.Hash(item.Text)) throw new InvalidDataException("STALE: проверенный текст изменён.");
            }
            ReserveModelCall(); activeCorpus = new(baseline.Fingerprint, requestedAction, items, revision); return activeCorpus;
        }
    }
    public void AcceptCorpusTransform(CorpusTransformResult result, PackSnapshot current, CancellationToken token = default)
    {
        lock (sync)
        {
            token.ThrowIfCancellationRequested(); AssertCurrent(current);
            if (!ReferenceEquals(result.Request, activeCorpus) || result.Request.Revision != revision || result.Request.SourceFingerprint != baseline.Fingerprint)
                throw new InvalidDataException("STALE: выбранные фрагменты/контекст/source изменились.");
            LmStudioClient.ValidateCorpusSuggestions(result.Suggestions, result.Request);
            corpusSuggestions = Array.AsReadOnly(result.Suggestions.ToArray()); activeCorpus = null; corpusAdviceStatus = "SOURCE_MATERIAL_ADVISORY_NOT_VERIFIED";
        }
    }
    public void FailCorpusTransform(CorpusTransformRequest request)
    { lock (sync) { if (ReferenceEquals(request, activeCorpus)) { activeCorpus = null; corpusAdviceStatus = "BLOCKED_LM"; } } }
}
