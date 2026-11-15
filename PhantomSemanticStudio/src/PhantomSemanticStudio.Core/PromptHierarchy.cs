using System.Collections.ObjectModel;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace PhantomSemanticStudio.Core;

public sealed record PromptFamilyExample(string Category, string User, string Reply, string Source, string Status);
public sealed record PromptFamilyDefinition(string Id, string Label, string Revision, string Instruction, IReadOnlyList<PromptFamilyExample> Examples);
public sealed record PromptCoverageRow(string Category, string Label, string FamilyId, string FamilyRevision, bool SpecializedCategory, string QualityStatus);
public sealed record PromptFamilySnapshot(string Schema, string FamilyId, string FamilyRevision, string Category, string Act, string Intent,
    string Mode, string CommonContract, string ModeInstruction, string FamilyInstruction, string CategoryInstruction,
    string StyleInstruction, string StyleFingerprint, IReadOnlyList<PromptFamilyExample> Examples, string ConfigHash, string RegistryHash, string FunctionalHash);

/// <summary>Offline component library. Saved snapshots never look up newer resource text during replay.</summary>
public sealed class PromptHierarchy
{
    [JsonUnmappedMemberHandling(JsonUnmappedMemberHandling.Disallow)]
    private sealed record Selector(string? Category, string? Prefix, string Family);
    [JsonUnmappedMemberHandling(JsonUnmappedMemberHandling.Disallow)]
    private sealed record Document(string Schema, string CommonContract, Dictionary<string, string> ModeInstructions,
        PromptFamilyDefinition[] Families, Selector[] CategoryRules, Dictionary<string, string> CategoryBriefs, string[] ExperimentalCategories);
    private readonly Document document;
    public IReadOnlyList<PromptFamilyDefinition> Families { get; }
    public string Hash { get; }
    public static PromptHierarchy Default { get; } = Parse(V4Rules.Utf8.GetString(V4Rules.ReadBytes(
        Path.Combine(AppContext.BaseDirectory, "prompts", "corpus001", "families.v1.json"), 128 * 1024)));

    private PromptHierarchy(Document document, string source)
    {
        this.document = document; Hash = V4Rules.Hash(V4Rules.Utf8.GetBytes(source));
        Families = Array.AsReadOnly(document.Families.Select(f => f with { Examples = Array.AsReadOnly(f.Examples.ToArray()) }).ToArray());
    }

    public static PromptHierarchy Parse(string source)
    {
        V4Rules.Require(V4Rules.Utf8.GetByteCount(source) <= 128 * 1024, "HIERARCHY_CONFIG_BYTES");
        var value = JsonSerializer.Deserialize<Document>(source, PromptProfiles.ConfigJson) ?? throw new InvalidDataException("HIERARCHY_EMPTY");
        V4Rules.Require(value.Schema == "pw-prompt-hierarchy/1" && value.Families != null && value.Families.Length is >= 12 and <= 32
            && value.CategoryRules != null && value.CategoryRules.Length is > 0 and <= 512 && value.CategoryBriefs != null
            && value.ExperimentalCategories != null && value.ModeInstructions != null, "HIERARCHY_CONFIG_SHAPE");
        Text(value.CommonContract, 2048);
        V4Rules.Require(value.ModeInstructions!.Keys.Order().SequenceEqual(V4Registry.Modes.Order()), "HIERARCHY_MODES");
        foreach (var mode in value.ModeInstructions!.Values) Text(mode, 1600);
        foreach (var family in value.Families!)
        {
            V4Rules.Require(family != null && family.Id != null && V4Rules.Tag(family.Id) && family.Revision != null && family.Revision.Length is > 0 and <= 32
                && family.Examples != null && family.Examples.Count <= 2, "HIERARCHY_FAMILY");
            Text(family!.Label, 240); Text(family.Instruction, 2400);
            foreach (var example in family.Examples!) ValidateExample(example);
        }
        V4Rules.Require(value.Families.Select(f => f.Id).Distinct(StringComparer.Ordinal).Count() == value.Families.Length, "HIERARCHY_FAMILY_COLLISION");
        V4Rules.Require(value.ExperimentalCategories!.All(c => c != null && V4Rules.Key(c))
            && value.ExperimentalCategories!.Distinct(StringComparer.Ordinal).Count() == value.ExperimentalCategories!.Length, "HIERARCHY_EXPERIMENTAL");
        var knownCategories = V4Registry.Default.Categories.Select(c => c.Key).Concat(value.ExperimentalCategories!).ToArray();
        foreach (var selector in value.CategoryRules!)
            V4Rules.Require(selector != null && value.Families.Any(f => f.Id == selector.Family)
                && ((selector.Category != null && selector.Prefix == null && knownCategories.Contains(selector.Category))
                    || (selector.Prefix != null && selector.Category == null && selector.Prefix.EndsWith('.') && V4Rules.Key(selector.Prefix.TrimEnd('.'))
                        && knownCategories.Any(c => c.StartsWith(selector.Prefix, StringComparison.Ordinal)))), "HIERARCHY_SELECTOR");
        V4Rules.Require(value.CategoryRules.Select(s => s.Category ?? s.Prefix).Distinct(StringComparer.Ordinal).Count() == value.CategoryRules.Length, "HIERARCHY_SELECTOR_COLLISION");
        foreach (var brief in value.CategoryBriefs!)
        {
            V4Rules.Require(V4Registry.Default.Categories.Any(c => c.Key == brief.Key) || value.ExperimentalCategories.Contains(brief.Key), "HIERARCHY_BRIEF_CATEGORY");
            Text(brief.Value, 1600);
        }
        var result = new PromptHierarchy(value, source); _ = result.Coverage(V4Registry.Default); return result;
    }

    private static void Text(string? value, int bytes) => V4Rules.Require(!string.IsNullOrWhiteSpace(value) && V4Rules.Utf8.GetByteCount(value) <= bytes
        && !value.Any(c => char.IsControl(c) && c is not ('\r' or '\n' or '\t')), "HIERARCHY_TEXT");

    public PromptFamilyDefinition FamilyFor(string category)
    {
        V4Rules.Require(category != null && (V4Registry.Default.Categories.Any(c => c.Key == category) || document.ExperimentalCategories.Contains(category)), "HIERARCHY_UNKNOWN_CATEGORY");
        var exact = document.CategoryRules.SingleOrDefault(s => s.Category == category);
        var selected = exact ?? document.CategoryRules.Where(s => s.Prefix != null && category!.StartsWith(s.Prefix, StringComparison.Ordinal))
            .OrderByDescending(s => s.Prefix!.Length).FirstOrDefault();
        V4Rules.Require(selected != null, "HIERARCHY_CATEGORY_UNMAPPED: " + category);
        return Families.Single(f => f.Id == selected!.Family);
    }

    public IReadOnlyList<PromptCoverageRow> Coverage(V4Registry registry) => registry.Categories.Select(category =>
    {
        var family = FamilyFor(category.Key);
        return new PromptCoverageRow(category.Key, category.Label, family.Id, family.Revision, document.CategoryBriefs.ContainsKey(category.Key), "UNCONFIRMED");
    }).ToArray();

    public PromptFamilySnapshot Freeze(V4GenerationRequest request, PromptProfiles profiles, V4Registry registry,
        string? familyInstruction = null, string? categoryInstruction = null)
    {
        request.Validate(profiles, registry);
        var category = registry.Categories.Single(c => c.Key == request.Category); var family = FamilyFor(category.Key);
        var style = request.Style; var preset = profiles.Styles.Single(p => p.Id == style.StyleId);
        var voice = style.Voice == "ANY" ? null : registry.Voices.Single(v => v.Key == style.Voice);
        var actor = request.Mode == "TRIGGER" ? "Говорит игрок, не переносить на него биографию фантома."
            : "Голос " + style.Voice + ": " + (voice == null ? "спокойный общий ответ" : voice.Label + "; facets=" + JsonSerializer.Serialize(voice.Facets, PromptProfiles.CompactJson))
                + "; отношения=" + style.Relation + "; stance=" + (request.DesiredStance ?? "по ситуации") + "; tone=" + (request.DesiredTone ?? "по ситуации") + ".";
        var styleText = actor + "\nСтиль " + preset.Id + ": " + preset.Instruction
            + "\nМат: " + (request.ProfanityLimit ?? (style.StrongProfanityRequested ? "разрешён по контексту" : "NONE"))
            + "; maturity=" + (style.MatureRequested ? "MATURE: только подтверждённые согласные взрослые, без эротических сцен" : "GENERAL") + ".";
        if (!string.IsNullOrWhiteSpace(style.EmotionalState)) styleText += "\nЭмоциональное состояние: " + style.EmotionalState + ".";
        if (style.MatureRequested || style.SpeakerSex != "ANY" || style.AddresseeSex != "ANY" || style.SpeakerVirginity != "UNKNOWN")
            styleText += "\nПол говорящего=" + style.SpeakerSex + ", адресата=" + style.AddresseeSex + ", опыт=" + style.SpeakerVirginity
                + ". UNKNOWN/ANY не задаёт анатомию или биографию. В импортируемом V4 нет фильтра этих персональных фактов; не выдумывай их.";
        var brief = categoryInstruction ?? (document.CategoryBriefs.TryGetValue(category.Key, out var specialized) ? specialized : "Тема: " + category.Label + ". Реагируй именно на неё, сохраняя заданную игровую или бытовую область.");
        Text(brief, 1600); Text(familyInstruction ?? family.Instruction, 2400);
        // The example contract has player/reply pairs, not event-backed initiative examples.
        var examples = Array.AsReadOnly(request.Mode == "INITIATIVE" ? [] : family.Examples.Where(e => e.Category.Length == 0 || e.Category == category.Key).ToArray());
        var snapshot = new PromptFamilySnapshot("pw-prompt-components/1", family.Id, family.Revision, category.Key, category.Act, category.Intent, request.Mode,
            document.CommonContract, document.ModeInstructions[request.Mode], familyInstruction ?? family.Instruction, brief, styleText,
            StyleFingerprint(request), examples, Hash, registry.Hash, "");
        return snapshot with { FunctionalHash = FunctionalHash(snapshot) };
    }

    public static string StyleFingerprint(V4GenerationRequest request) => V4Rules.Hash(JsonSerializer.SerializeToUtf8Bytes(new
    { style = request.Style with { Situation = "", Dialogue = [] }, request.DesiredStance, request.DesiredTone, request.ProfanityLimit }, PromptProfiles.CompactJson));

    private static string FunctionalHash(PromptFamilySnapshot snapshot) => V4Rules.Hash(JsonSerializer.SerializeToUtf8Bytes(
        snapshot with { ConfigHash = "", RegistryHash = "", FunctionalHash = "" }, PromptProfiles.CompactJson));

    public static void ValidateSnapshot(PromptFamilySnapshot snapshot)
    {
        V4Rules.Require(snapshot != null && snapshot.Schema == "pw-prompt-components/1" && snapshot.FamilyId != null && V4Rules.Tag(snapshot.FamilyId)
            && snapshot.Category != null && V4Rules.Key(snapshot.Category) && snapshot.Act != null && V4Rules.Key(snapshot.Act) && snapshot.Intent != null && V4Rules.Key(snapshot.Intent)
            && V4Registry.Modes.Contains(snapshot.Mode) && snapshot.Examples != null && snapshot.Examples.Count <= 2, "HIERARCHY_SNAPSHOT_SHAPE");
        Text(snapshot!.FamilyRevision, 32);
        V4Rules.Require(IsHash(snapshot.ConfigHash) && IsHash(snapshot.RegistryHash) && IsHash(snapshot.StyleFingerprint) && IsHash(snapshot.FunctionalHash), "HIERARCHY_SNAPSHOT_METADATA");
        V4Rules.Require(snapshot.Mode != "INITIATIVE" || snapshot.Examples!.Count == 0, "HIERARCHY_INITIATIVE_EXAMPLE_CONTRACT_UNAVAILABLE");
        foreach (var example in snapshot.Examples!)
        { ValidateExample(example); V4Rules.Require(example.Category == "" || example.Category == snapshot.Category, "HIERARCHY_EXAMPLE_CATEGORY_SCOPE"); }
        V4Rules.Require(snapshot.FunctionalHash == FunctionalHash(snapshot), "HIERARCHY_SNAPSHOT_HASH");
        Text(snapshot.CommonContract, 2048); Text(snapshot.ModeInstruction, 1600); Text(snapshot.FamilyInstruction, 2400);
        Text(snapshot.CategoryInstruction, 1600); Text(snapshot.StyleInstruction, 6000);
    }

    private static bool IsHash(string? value) => value is { Length: 64 } && value.All(c => c is >= '0' and <= '9' or >= 'a' and <= 'f');
    private static void ValidateExample(PromptFamilyExample? example)
    {
        V4Rules.Require(example != null && example.Category != null && (example.Category == "" || V4Rules.Key(example.Category))
            && example.Status is "EXPERIMENTAL" or "OWNER_CONFIRMED", "HIERARCHY_EXAMPLE");
        Text(example!.User, 1000); Text(example.Reply, 500); Text(example.Source, 400);
        V4Rules.Require(V4Rules.OutboundLength(example.Reply), "HIERARCHY_EXAMPLE_OUTBOUND");
    }

    public static string Compose(PromptFamilySnapshot snapshot)
    {
        ValidateSnapshot(snapshot);
        var result = snapshot.CommonContract + "\n\n" + snapshot.ModeInstruction + "\n\nСЕМЕЙСТВО " + snapshot.FamilyId + "/" + snapshot.FamilyRevision + ":\n"
            + snapshot.FamilyInstruction + "\n\nКАТЕГОРИЯ " + snapshot.Category + ":\n" + snapshot.CategoryInstruction + "\n\n" + snapshot.StyleInstruction;
        foreach (var example in snapshot.Examples) result += snapshot.Mode == "TRIGGER"
            ? "\n\nПример входа игрока, не текст для копирования: " + example.User
            : "\n\nПример хода, не текст для копирования. Игрок: " + example.User + " Ответ: " + example.Reply;
        V4Rules.Require(V4Rules.Utf8.GetByteCount(result) <= 12 * 1024, "HIERARCHY_COMPOSED_BYTES"); return result;
    }

    public static PromptFamilySnapshot Revise(PromptFamilySnapshot snapshot, string familyInstruction, string? categoryInstruction = null)
    {
        ValidateSnapshot(snapshot); Text(familyInstruction, 2400);
        if (categoryInstruction != null) Text(categoryInstruction, 1600);
        var next = snapshot with { FamilyInstruction = familyInstruction, CategoryInstruction = categoryInstruction ?? snapshot.CategoryInstruction, FunctionalHash = "" };
        return next with { FunctionalHash = FunctionalHash(next) };
    }

    public PromptFamilySnapshot Rebind(PromptFamilySnapshot snapshot, V4GenerationRequest request, PromptProfiles profiles,
        V4Registry registry, string familyInstruction, string? categoryInstruction = null)
    {
        ValidateSnapshot(snapshot);
        var sameCategory = snapshot.Category == request.Category;
        var sameMode = snapshot.Mode == request.Mode;
        var next = Freeze(request with { Components = null }, profiles, registry, familyInstruction,
            categoryInstruction ?? (sameCategory ? snapshot.CategoryInstruction : null));
        V4Rules.Require(next.FamilyId == snapshot.FamilyId, "HIERARCHY_FAMILY_CHANGE_REQUIRES_COPY");
        // Keep the saved base and unchanged components, even if bundled defaults have since evolved.
        next = next with { CommonContract = snapshot.CommonContract, FamilyRevision = snapshot.FamilyRevision,
            ConfigHash = snapshot.ConfigHash, ModeInstruction = sameMode ? snapshot.ModeInstruction : next.ModeInstruction,
            StyleInstruction = next.StyleFingerprint == snapshot.StyleFingerprint ? snapshot.StyleInstruction : next.StyleInstruction,
            Examples = sameCategory && sameMode ? snapshot.Examples : next.Examples, FunctionalHash = "" };
        next = next with { FunctionalHash = FunctionalHash(next) };
        ValidateSnapshot(next); return next;
    }

    public static IReadOnlyList<CorpusQualityIssue> CheckExampleEcho(PromptFamilySnapshot snapshot, IReadOnlyList<V4Entry> entries)
    {
        ValidateSnapshot(snapshot); var issues = new List<CorpusQualityIssue>();
        for (var index = 0; index < entries.Count; index++)
            if (snapshot.Examples.Select(e => snapshot.Mode == "TRIGGER" ? e.User : e.Reply)
                .Any(reference => V4Rules.Normalize(reference) == V4Rules.Normalize(entries[index].Text) || CorpusQuality.NearClone(reference, entries[index].Text)))
                issues.Add(new("REFERENCE_ECHO_REVIEW", index + 1, "Текст повторяет выбранный пример; это не доказательство новой естественной реплики."));
        return issues.AsReadOnly();
    }
}
