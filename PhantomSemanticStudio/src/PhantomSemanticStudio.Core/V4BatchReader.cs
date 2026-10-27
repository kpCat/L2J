using System.Text;
using System.Text.Json;

namespace PhantomSemanticStudio.Core;

internal static class V4BatchReader
{
    internal const long MaxFileBytes = 8 * 1024 * 1024;
    internal static V4Report Read(string path, V4Registry registry)
    {
        var entries = new List<V4Entry>(); var diagnostics = new List<V4Diagnostic>(); var line = 0; var inputHash = ""; byte[] bytes = [];
        try
        {
            path = PathSafety.Canonical(path); PathSafety.AssertNoReparsePoints(path);
            using var stream = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.Read);
            V4Rules.Require(stream.Length is > 0 and <= MaxFileBytes, "FILE_BYTES");
            bytes = new byte[(int)stream.Length]; stream.ReadExactly(bytes); line = 1;
            inputHash = V4Rules.Hash(bytes);
            V4Rules.Require(!(bytes.Length >= 3 && bytes[0] == 0xef && bytes[1] == 0xbb && bytes[2] == 0xbf), "UTF8_BOM");
            var text = V4Rules.Utf8.GetString(bytes); V4Rules.Require(!text.Contains('\r') && !text.Contains('\0'), "LF_OR_CONTROL");
            V4Rules.Require(text.EndsWith('\n'), "FINAL_LF");
            var lines = text.Split('\n'); var header = false; var count = 0; string mode = "", category = "", act = "";
            var seen = new HashSet<string>(StringComparer.Ordinal); line = 0;
            foreach (var raw in lines.Take(lines.Length - 1))
            {
                line++; V4Rules.Require(V4Rules.Utf8.GetByteCount(raw) <= 16 * 1024, "LINE_BYTES");
                if (string.IsNullOrWhiteSpace(raw)) { V4Rules.Require(!header, "EMPTY_RECORD"); continue; }
                using var json = JsonDocument.Parse(raw, new JsonDocumentOptions { MaxDepth = 8, CommentHandling = JsonCommentHandling.Disallow, AllowTrailingCommas = false });
                var record = json.RootElement;
                if (!header)
                {
                    V4Rules.JsonKeys(record, ["schema", "category", "mode", "act", "language", "count"], []);
                    V4Rules.Require(V4Rules.String(record, "schema") == "pw-semantic-batch/1" && V4Rules.String(record, "language") == "ru", "SCHEMA_LANGUAGE");
                    category = V4Rules.String(record, "category"); var found = registry.Categories.SingleOrDefault(c => c.Key == category);
                    if (found == null) throw new InvalidDataException("UNKNOWN_CATEGORY: " + string.Join("; ", registry.Categories
                        .OrderByDescending(c => CommonPrefix(category, c.Key)).Take(3).Select(c => c.Key + " — " + c.Label)));
                    act = V4Rules.String(record, "act"); V4Rules.Require(act == found.Act, "ACT_REFERENCE");
                    mode = V4Rules.Choice(record, "mode", V4Registry.Modes);
                    V4Rules.Require(record.GetProperty("count").TryGetInt32(out count) && count is >= 1 and <= 10000, "COUNT_RANGE"); header = true; continue;
                }
                V4Rules.Require(entries.Count < 10000, "COUNT_CAP");
                var e = ReadRecord(record, registry, category, act, mode);
                V4Rules.Require(seen.Add(V4Rules.Normalize(e.Text)), "DUPLICATE_TEXT"); entries.Add(e);
            }
            V4Rules.Require(header && entries.Count == count, "COUNT_MISMATCH");
        }
        catch (Exception ex) when (ex is InvalidDataException or JsonException or DecoderFallbackException or EncoderFallbackException or IOException or UnauthorizedAccessException or InvalidOperationException or ArgumentException)
        {
            if (ex is DecoderFallbackException decoder) line = 1 + bytes.Take(Math.Clamp(decoder.Index, 0, bytes.Length)).Count(b => b == 10);
            diagnostics.Add(new("ERROR", line == 0 ? "FILE_REJECTED" : "BATCH_REJECTED", path, line,
                line == 0 ? "Ошибка файла до чтения строк: " + ex.Message : ex.Message));
        }
        return new(diagnostics.Count == 0 ? entries.Count : 0, 0, diagnostics.Count == 0 ? entries.AsReadOnly() : [], diagnostics.AsReadOnly()) { InputHash = inputHash };
    }
    private static int CommonPrefix(string a, string b) { var length = Math.Min(a.Length, b.Length); var i = 0; while (i < length && a[i] == b[i]) i++; return i; }
    private static V4Entry ReadRecord(JsonElement record, V4Registry registry, string category, string act, string mode)
    {
        var trigger = mode == "TRIGGER";
        V4Rules.JsonKeys(record, trigger ? ["text", "intent"] : mode == "INITIATIVE" ? ["text", "stance", "requiredEvent"] : ["text", "stance"],
            trigger ? ["tags", "ambiguity"] : ["tone", "relations", "voices", "facets", "profanity", "maturity", "tags", "weight"]);
        var raw = V4Rules.String(record, "text"); V4Rules.SafeText(raw); var text = V4Rules.Clean(raw); V4Rules.SafeText(text);
        V4Rules.Require(!trigger || !text.Contains('{'), "TRIGGER_PLACEHOLDER");
        var facets = new SortedDictionary<string, int>(StringComparer.Ordinal);
        if (record.TryGetProperty("facets", out var f))
        {
            V4Rules.Require(f.ValueKind == JsonValueKind.Object, "FACETS_TYPE");
            foreach (var pair in f.EnumerateObject())
            { V4Rules.Require(V4Registry.FacetKeys.Contains(pair.Name) && !facets.ContainsKey(pair.Name) && pair.Value.TryGetInt32(out _), "FACET"); var n = pair.Value.GetInt32(); V4Rules.Require(n is >= 0 and <= 100, "FACET_RANGE"); facets.Add(pair.Name, n); }
        }
        var intent = trigger ? V4Rules.String(record, "intent") : "";
        V4Rules.Require(!trigger || registry.Categories.Single(c => c.Key == category).Intent == intent, "INTENT_REFERENCE");
        var requiredEvent = mode == "INITIATIVE" ? V4Rules.String(record, "requiredEvent") : "";
        V4Rules.Require(mode != "INITIATIVE" || registry.Events.Any(e => e.Code == requiredEvent), "EVENT_REFERENCE");
        var weight = 1;
        if (record.TryGetProperty("weight", out var w)) V4Rules.Require(w.TryGetInt32(out weight) && weight is >= 1 and <= 10, "WEIGHT");
        var e = new V4Entry("", mode, category, act, text, intent,
            trigger ? V4Rules.Choice(record, "ambiguity", V4Registry.Ambiguities, "NONE") : "",
            trigger ? "" : V4Rules.Choice(record, "stance", V4Registry.Stances), trigger ? "" : V4Rules.Choice(record, "tone", V4Registry.Tones, "CALM"),
            trigger ? [] : Array(record, "relations", V4Registry.Relations, ["UNKNOWN"]),
            trigger ? [] : Array(record, "voices", registry.Voices.Select(v => v.Key).Prepend("ANY").ToArray(), ["ANY"]), facets,
            trigger ? "NONE" : V4Rules.Choice(record, "profanity", V4Registry.Profanities, "NONE"),
            trigger ? "GENERAL" : V4Rules.Choice(record, "maturity", V4Registry.Maturities, "GENERAL"), Array(record, "tags", null, []), weight, requiredEvent);
        V4Rules.Require(!e.Voices.Contains("ANY") || e.Voices.Length == 1, "ANY_VOICE_EXCLUSIVE");
        V4Rules.Require(trigger || V4Rules.OutboundLength(text), "OUTBOUND_MAX_100");
        return e with { Id = V4Rules.Id(e) };
    }
    private static string[] Array(JsonElement record, string name, IReadOnlyList<string>? allowed, string[] fallback)
    {
        if (!record.TryGetProperty(name, out var a)) return fallback;
        V4Rules.Require(a.ValueKind == JsonValueKind.Array && a.GetArrayLength() is >= 1 and <= 16, "ARRAY_" + name);
        var result = new HashSet<string>(StringComparer.Ordinal);
        foreach (var item in a.EnumerateArray())
        {
            V4Rules.Require(item.ValueKind == JsonValueKind.String, "ARRAY_TYPE"); var value = item.GetString()!;
            V4Rules.Require((allowed?.Contains(value) ?? V4Rules.Tag(value)) && result.Add(value), "ARRAY_VALUE_" + name);
        }
        return result.Order(StringComparer.Ordinal).ToArray();
    }
}
