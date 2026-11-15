using System.Text.Json;
using System.Text.Json.Serialization;

namespace PhantomSemanticStudio.Core;

[JsonUnmappedMemberHandling(JsonUnmappedMemberHandling.Disallow)]
public sealed record GenerationStyleContext
{
    public string StyleId { get; init; } = "ordinary";
    public string Category { get; init; } = "";
    public string Voice { get; init; } = "ANY";
    public string Relation { get; init; } = "UNKNOWN";
    public string Channel { get; init; } = "ALL";
    public string SpeakerSex { get; init; } = "ANY";
    public string AddresseeSex { get; init; } = "ANY";
    public string SpeakerVirginity { get; init; } = "UNKNOWN";
    public bool StrongProfanityRequested { get; init; }
    public bool MatureRequested { get; init; }
    public bool OwnerMatureOptIn { get; init; }
    public bool AdultsConsenting { get; init; }
    public string Situation { get; init; } = "";
    public string[] Dialogue { get; init; } = [];
}

[JsonUnmappedMemberHandling(JsonUnmappedMemberHandling.Disallow)]
public sealed record CorpusStylePreset(string Id, string Instruction, bool AllowsStrong, bool AllowsMature);

/// <summary>Versioned authoring resources. They grant no runtime permissions and never alter model load settings.</summary>
public sealed class PromptProfiles
{
    public static IReadOnlyList<string> Strategies { get; } = Array.AsReadOnly(new[] { "situation-first", "persona-first", "dialogue-context", "mature-rough" });
    private static readonly string[] ResourceNames = ["common.v1.md", "situation-first.v1.md", "persona-first.v1.md", "dialogue-context.v1.md", "mature-rough.v1.md", "styles.v1.json", "refiner.v1.md"];
    private readonly Dictionary<string, string> resources = new(StringComparer.Ordinal);
    public IReadOnlyList<CorpusStylePreset> Styles { get; }
    public string Hash { get; }
    public string DirectoryPath { get; }
    internal IReadOnlyDictionary<string, string> Resources => resources;
    internal static readonly JsonSerializerOptions ConfigJson = new()
    { PropertyNameCaseInsensitive = true, Encoder = System.Text.Encodings.Web.JavaScriptEncoder.UnsafeRelaxedJsonEscaping, MaxDepth = 24, WriteIndented = true };
    internal static readonly JsonSerializerOptions CompactJson = new(ConfigJson) { WriteIndented = false };
    public static PromptProfiles Default { get; } = new(Path.Combine(AppContext.BaseDirectory, "prompts", "corpus001"));

    public PromptProfiles(string directory)
    {
        directory = PathSafety.Canonical(directory);
        DirectoryPath = directory;
        foreach (var name in ResourceNames)
            resources.Add(name, V4Rules.Utf8.GetString(V4Rules.ReadBytes(PathSafety.ResolveRelative(directory, name), 64 * 1024)));
        Styles = Array.AsReadOnly((JsonSerializer.Deserialize<CorpusStylePreset[]>(resources["styles.v1.json"], ConfigJson) ?? throw new InvalidDataException("STYLES_EMPTY")));
        V4Rules.Require(Styles.Count is >= 5 and <= 32 && Styles.Select(s => s.Id).Distinct(StringComparer.Ordinal).Count() == Styles.Count
            && Styles.All(s => V4Rules.Tag(s.Id) && s.Instruction.Length is > 0 and <= 3000), "STYLE_CONFIG");
        Hash = V4Rules.Hash(V4Rules.Utf8.GetBytes(string.Concat(ResourceNames.Select(n => n + "\n" + resources[n] + "\n"))));
    }

    public void Validate(GenerationStyleContext context, V4Registry? registry = null)
    {
        registry ??= V4Registry.Default;
        if (context == null || context.Dialogue == null || context.Situation == null || context.Category == null)
            throw new InvalidDataException("STYLE_CONTEXT");
        var style = Styles.SingleOrDefault(s => s.Id == context.StyleId) ?? throw new InvalidDataException("UNKNOWN_STYLE");
        V4Rules.Require(V4Registry.Relations.Contains(context.Relation) && new[] { "ALL", "SHOUT", "PARTY", "CLAN", "TRADE", "TELL", "BOT_PRIVATE" }.Contains(context.Channel)
            && new[] { "ANY", "FEMALE", "MALE" }.Contains(context.SpeakerSex) && new[] { "ANY", "FEMALE", "MALE" }.Contains(context.AddresseeSex)
            && new[] { "UNKNOWN", "VIRGIN", "NOT_VIRGIN" }.Contains(context.SpeakerVirginity)
            && (context.Voice == "ANY" || registry.Voices.Any(v => v.Key == context.Voice))
            && context.Situation.Length <= 2000 && context.Dialogue.Length <= 6
            && context.Dialogue.All(t => t != null && t.Length is > 0 and <= 1000)
            && (!context.StrongProfanityRequested || style.AllowsStrong), "STYLE_SCOPE");
        if (context.Category.Length > 0) V4Rules.Require(registry.Categories.Any(c => c.Key == context.Category), "STYLE_CATEGORY");
        if (context.MatureRequested)
        {
            var adultCategory = context.Category is "social.banter.double_meanings" or "social.flirt.bold" or "social.boundaries.sexual_humor"
                or "hybrid.trade.romantic_barter" || context.Category.StartsWith("life.love.", StringComparison.Ordinal);
            V4Rules.Require(style.AllowsMature && context.OwnerMatureOptIn && context.AdultsConsenting && context.Relation == "TRUSTED"
                && context.Channel is "TELL" or "BOT_PRIVATE" && adultCategory, "MATURE_CONTEXT_NOT_AUTHORIZED");
        }
    }

    public string LegacyRules(GenerationStyleContext? context, V4Registry? registry = null)
    {
        if (context == null) return "Профиль ordinary: по умолчанию GENERAL/NONE. Если стиль не подтверждён отдельно, обычный разговор без мата и взрослого подтекста. "
            + "Это значение по умолчанию, а не запрет для других явно разрешённых редакционных профилей. Новые правила памяти и подстановки не создавай.";
        registry ??= V4Registry.Default; Validate(context, registry);
        var preset = Styles.Single(s => s.Id == context.StyleId);
        var voice = registry.Voices.SingleOrDefault(v => v.Key == context.Voice);
        return "Контекстный профиль: " + preset.Id + ". " + preset.Instruction + "\nГолос: " + context.Voice + "; relation=" + context.Relation + "; channel=" + context.Channel
            + "; пол говорящего=" + context.SpeakerSex + "; пол собеседника=" + context.AddresseeSex + ". ANY не задаёт анатомию; не угадывай её по голосу. "
            + "Девственность говорящего=" + context.SpeakerVirginity + ". UNKNOWN — отсутствие факта, не разрешение придумать биографию. Сохраняй явно заданный статус; уверенный флирт не доказывает опыт. "
            + ". " + (voice == null ? "ANY — нейтральный запасной ответ без смешения 24 характеров." : JsonSerializer.Serialize(voice, CompactJson))
            + "\n" + (context.StrongProfanityRequested ? "Разрешён осмысленный STRONG: крепкий мат как эмоция/подкол по ситуации; не добавляй ругань ради квоты. " : "Интенсивность NONE: без крепкой и мягкой ругани в этой порции. ")
            + (context.MatureRequested ? "Разрешён MATURE: взрослые добровольные двусмысленности и неграфический флирт в подтверждённой приватной ситуации. " : "Maturity GENERAL: без сексуального подтекста в этой порции. ")
            + "Личные границы голоса независимы от roughness. Никаких несовершеннолетних в сексуальном контексте, принуждения, реальных угроз или графических описаний секса. "
            + "Не утверждай согласие реального игрока. Не изображай ассистента. Profile SHA256=" + Hash;
    }

    public void ValidateLegacy(GenerationStyleContext context, string topic, string act, string band, string gender = "ANY")
    {
        Validate(context);
        V4Rules.Require(context.Relation == band, "STYLE_RELATION_MISMATCH");
        V4Rules.Require(context.SpeakerSex == "ANY" || context.SpeakerSex == gender, "STYLE_SPEAKER_SEX_MISMATCH");
        // A V4 adult label must not grant permission to an unrelated legacy topic.
        if (context.MatureRequested)
            V4Rules.Require(context.Category == topic && V4Registry.Default.Categories.Any(c => c.Key == topic && c.Act == act), "LEGACY_MATURE_SCOPE_UNMAPPED");
    }

    internal string StrategyText(string strategy)
    {
        V4Rules.Require(Strategies.Contains(strategy), "UNKNOWN_PROMPT_STRATEGY");
        return resources["common.v1.md"] + "\n" + resources[strategy + ".v1.md"];
    }
}
