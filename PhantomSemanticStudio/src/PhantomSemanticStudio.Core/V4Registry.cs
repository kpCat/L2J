using System.Collections.ObjectModel;

namespace PhantomSemanticStudio.Core;

/// <summary>The embedded data is the single authoring/passport/compiled registry source.</summary>
public sealed class V4Registry
{
    public static IReadOnlyList<string> Roots { get; } = Array.AsReadOnly(new[] { "lineage", "life", "hybrid", "social" });
    public static IReadOnlyList<string> FacetKeys { get; } = Array.AsReadOnly(new[]
        { "aggression", "sarcasm", "humor", "reserved", "swagger", "timidity", "romance", "roughness", "cunning", "gossip" });
    public static IReadOnlyList<string> Stances { get; } = Array.AsReadOnly(new[]
        { "AMUSED", "NEUTRAL", "OFFENDED", "SET_BOUNDARY", "SUPPORTIVE", "AGGRESSIVE_BANTER", "DISENGAGE", "CURIOUS", "FLIRT", "REFUSE" });
    public static IReadOnlyList<string> Relations { get; } = Array.AsReadOnly(new[] { "UNKNOWN", "NEUTRAL", "FAMILIAR", "TRUSTED", "RIVAL", "TENSE", "HOSTILE" });
    public static IReadOnlyList<string> Tones { get; } = Array.AsReadOnly(new[] { "CALM", "PLAYFUL", "DRY", "ROUGH", "WARM", "SARCASTIC", "SHY", "FORMAL", "DIRECT" });
    public static IReadOnlyList<string> Modes { get; } = Array.AsReadOnly(new[] { "TRIGGER", "REPLY", "INITIATIVE" });
    public static IReadOnlyList<string> Ambiguities { get; } = Array.AsReadOnly(new[] { "NONE", "POSSIBLE_JOKE", "POSSIBLE_THREAT" });
    public static IReadOnlyList<string> Profanities { get; } = Array.AsReadOnly(new[] { "NONE", "MILD", "STRONG" });
    public static IReadOnlyList<string> Maturities { get; } = Array.AsReadOnly(new[] { "GENERAL", "MATURE" });
    public static V4Registry Default { get; } = ReadSeed();
    public IReadOnlyList<V4Category> Categories { get; }
    public IReadOnlyList<V4Voice> Voices { get; }
    public IReadOnlyList<V4Event> Events { get; }
    public string Hash => V4Rules.Hash(V4Rules.Utf8.GetBytes(CategoryTsv() + VoiceTsv() + EventTsv()));

    public V4Registry(IEnumerable<V4Category> categories, IEnumerable<V4Voice> voices, IEnumerable<V4Event> events)
    {
        Categories = Array.AsReadOnly(categories.OrderBy(c => c.Key, StringComparer.Ordinal).ToArray());
        Voices = Array.AsReadOnly(voices.OrderBy(v => v.Key, StringComparer.Ordinal).ToArray());
        Events = Array.AsReadOnly(events.OrderBy(e => e.Code, StringComparer.Ordinal).ToArray());
        V4Rules.Require(Categories.Count is > 0 and <= 10000 && Voices.Count is >= 24 and <= 1024 && Events.Count is > 0 and <= 1024, "REGISTRY_BOUNDS");
        V4Rules.Require(Categories.Select(c => c.Key).Distinct().Count() == Categories.Count
            && Categories.Select(c => c.Act).Distinct().Count() == Categories.Count
            && Categories.Select(c => c.Intent).Distinct().Count() == Categories.Count, "REGISTRY_COLLISION");
        foreach (var c in Categories)
        {
            V4Rules.Require(V4Rules.Key(c.Key) && c.Key.Split('.').Length >= 3 && Roots.Contains(c.Key.Split('.')[0])
                && V4Rules.Key(c.Act) && V4Rules.Key(c.Intent) && c.Act.StartsWith(c.Key + ".", StringComparison.Ordinal)
                && c.Intent.StartsWith(c.Key + ".", StringComparison.Ordinal) && c.Act != c.Intent, "CATEGORY_REFERENCE");
            V4Rules.Label(c.Label);
            V4Rules.Require(!Categories.Any(other => other.Key.StartsWith(c.Key + ".", StringComparison.Ordinal)), "LEAF_HAS_CHILDREN");
        }
        V4Rules.Require(Voices.Select(v => v.Key).Distinct().Count() == Voices.Count && Events.Select(e => e.Code).Distinct().Count() == Events.Count, "REGISTRY_COLLISION");
        foreach (var v in Voices)
        {
            V4Rules.Require(V4Rules.Tag(v.Key) && new[] { "calm", "open", "direct", "empathetic" }.Contains(v.Legacy)
                && new[] { v.ProfanityTolerance, v.InsultTolerance, v.SexualHumorTolerance, v.ConflictStyle, v.TrustThreshold }.All(i => i is >= 0 and <= 100)
                && v.Facets.Count == 10 && v.Facets.All(f => FacetKeys.Contains(f.Key) && f.Value is >= 0 and <= 100), "VOICE_REGISTRY");
            V4Rules.Label(v.Label);
        }
        foreach (var e in Events) { V4Rules.Require(V4Rules.Key(e.Code), "EVENT_REGISTRY"); V4Rules.Label(e.Label); }
    }
    public string CategoryTsv() => string.Concat(Categories.Select(c => $"{c.Key}\t{c.Label}\t{c.Act}\t{c.Intent}\n"));
    public string VoiceTsv() => string.Concat(Voices.Select(v => $"{v.Key}\t{v.Label}\t{v.Legacy}\t{v.ProfanityTolerance}\t{v.InsultTolerance}\t{v.SexualHumorTolerance}\t{v.ConflictStyle}\t{v.TrustThreshold}\t{V4Rules.Facets(v.Facets)}\n"));
    public string EventTsv() => string.Concat(Events.Select(e => $"{e.Code}\t{e.Label}\n"));
    internal static V4Registry Parse(string categories, string voices, string events)
    {
        static string[][] Rows(string text, int columns) => text.Split('\n', StringSplitOptions.RemoveEmptyEntries).Select(line =>
        { var fields = line.Split('\t'); V4Rules.Require(fields.Length == columns && !line.Contains('\r'), "REGISTRY_TSV"); return fields; }).ToArray();
        return new(Rows(categories, 4).Select(f => new V4Category(f[0], f[1], f[2], f[3])),
            Rows(voices, 9).Select(f => new V4Voice(f[0], f[1], f[2], V4Rules.Integer(f[3], 0, 100), V4Rules.Integer(f[4], 0, 100),
                V4Rules.Integer(f[5], 0, 100), V4Rules.Integer(f[6], 0, 100), V4Rules.Integer(f[7], 0, 100),
                new ReadOnlyDictionary<string, int>(V4Rules.ParseFacets(f[8])))),
            Rows(events, 2).Select(f => new V4Event(f[0], f[1])));
    }
    private static V4Registry ReadSeed()
    {
        static string Resource(string name)
        {
            using var stream = typeof(V4Registry).Assembly.GetManifestResourceStream("V4." + name)!;
            using var reader = new StreamReader(stream, V4Rules.Utf8, false); return reader.ReadToEnd();
        }
        return Parse(Resource("registry.tsv"), Resource("voices.tsv"), Resource("events.tsv"));
    }
}
