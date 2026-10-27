using System.Globalization;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.RegularExpressions;

namespace PhantomSemanticStudio.Core;

internal static class V4Rules
{
    internal static readonly UTF8Encoding Utf8 = new(false, true);
    internal const int MaxRecords = 1_000_000;
    internal static string Hash(byte[] bytes) => Convert.ToHexStringLower(SHA256.HashData(bytes));
    internal static byte[] ReadBytes(string path, long cap)
    {
        PathSafety.AssertNoReparsePoints(path); using var input = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.Read);
        Require(input.Length <= cap && input.Length <= int.MaxValue, "FILE_BYTES"); var bytes = new byte[(int)input.Length]; input.ReadExactly(bytes); return bytes;
    }
    internal static string Normalize(string text) => Clean(text).ToLowerInvariant();
    internal static string Clean(string text) => Regex.Replace(text.Normalize(NormalizationForm.FormKC), @"\s+", " ").Trim();
    internal static bool Key(string value) => Regex.IsMatch(value, @"\A[a-z][a-z0-9_]*(?:\.[a-z][a-z0-9_]*){1,10}\z", RegexOptions.CultureInvariant) && value.Length <= 192;
    internal static bool Tag(string value) => Regex.IsMatch(value, @"\A[a-z][a-z0-9_.-]{0,63}\z", RegexOptions.CultureInvariant);
    internal static void Require(bool condition, string code) { if (!condition) throw new InvalidDataException(code); }
    internal static int Integer(string value, int min, int max)
    { Require(Regex.IsMatch(value, @"\A[0-9]{1,10}\z") && int.TryParse(value, out _), "INTEGER"); var number = int.Parse(value, CultureInfo.InvariantCulture); Require(number >= min && number <= max, "INTEGER_RANGE"); return number; }
    internal static void Label(string text) { Require(text.Length is > 0 and <= 160, "LABEL"); SafeText(text); }
    internal static bool OutboundLength(string text)
    {
        var placeholders = (text.Length - text.Replace("{name}", "", StringComparison.Ordinal).Length) / 6;
        return text.EnumerateRunes().Count() + placeholders * 58 <= 100 && Utf8.GetByteCount(text) + placeholders * 186 <= 240;
    }
    internal static void SafeText(string text)
    {
        Require(text.Length is > 0 and <= 1000, "TEXT_LENGTH");
        Utf8.GetBytes(text);
        foreach (var rune in text.EnumerateRunes())
        {
            var category = Rune.GetUnicodeCategory(rune);
            Require(category is not (UnicodeCategory.Control or UnicodeCategory.Format or UnicodeCategory.PrivateUse or UnicodeCategory.OtherNotAssigned or UnicodeCategory.Surrogate)
                && rune.Value != 0xfffd, "UNSAFE_UNICODE");
            if (Rune.IsLetter(rune)) Require(rune.Value is >= 0x0041 and <= 0x005a or >= 0x0061 and <= 0x007a or >= 0x0410 and <= 0x044f
                or 0x0401 or 0x0451 or 0x0404 or 0x0454 or 0x0406 or 0x0456 or 0x0407 or 0x0457 or 0x0490 or 0x0491, "UNSUPPORTED_LETTER");
            Require(!Rune.IsDigit(rune) || rune.Value is >= 48 and <= 57, "UNSUPPORTED_DIGIT");
        }
        Require(!text.Contains('<') && !text.Contains('>') && !text.Contains('`') && !text.Contains('\\'), "MARKUP_OR_SCRIPT");
        var unbound = text.Replace("{name}", "", StringComparison.Ordinal);
        Require(!unbound.Contains('{') && !unbound.Contains('}'), "PLACEHOLDER");
        Require(!Regex.IsMatch(text, @"(?i)(?:https?://|file://|[a-z]:\\|password\s*[:=]|api[_-]?key\s*[:=]|bearer\s+[a-z0-9]|-----BEGIN .*PRIVATE KEY)"), "CREDENTIAL_OR_SOURCE");
    }
    internal static string Facets(IEnumerable<KeyValuePair<string, int>> values) => string.Join(',', values.OrderBy(p => p.Key, StringComparer.Ordinal).Select(p => p.Key + "=" + p.Value.ToString(CultureInfo.InvariantCulture)));
    internal static SortedDictionary<string, int> ParseFacets(string value)
    {
        var result = new SortedDictionary<string, int>(StringComparer.Ordinal);
        foreach (var facet in value.Split(',', StringSplitOptions.RemoveEmptyEntries))
        { var f = facet.Split('='); Require(f.Length == 2 && V4Registry.FacetKeys.Contains(f[0]) && !result.ContainsKey(f[0]), "FACET"); result.Add(f[0], Integer(f[1], 0, 100)); }
        return result;
    }
    internal static string Wire(V4Entry e) => string.Join('\t', e.Id, e.Mode, e.Category, e.Act, e.Text, e.Intent, e.Ambiguity, e.Stance, e.Tone,
        string.Join(',', e.Relations), string.Join(',', e.Voices), Facets(e.Facets), e.Profanity, e.Maturity, string.Join(',', e.Tags), e.Weight.ToString(CultureInfo.InvariantCulture), e.RequiredEvent);
    internal static string Id(V4Entry e) => Hash(Utf8.GetBytes("pw-semantic-entry/1\n" + Wire(e with { Id = "", Text = Normalize(e.Text) })));
    internal static byte[] ReplyDigest(V4Entry e) => SHA256.HashData(Utf8.GetBytes($"{e.Mode}\t{e.Category}\t{e.Stance}\t{e.RequiredEvent}"));
    internal static byte[] TriggerDigest(V4Entry e) => SHA256.HashData(Utf8.GetBytes(Normalize(e.Text)));
    internal static void JsonKeys(JsonElement element, string[] required, string[] optional)
    {
        Require(element.ValueKind == JsonValueKind.Object, "JSON_OBJECT"); var names = new HashSet<string>(StringComparer.Ordinal);
        foreach (var property in element.EnumerateObject()) Require(names.Add(property.Name) && (required.Contains(property.Name) || optional.Contains(property.Name)), "JSON_FIELDS");
        Require(required.All(names.Contains), "REQUIRED_FIELD");
    }
    internal static string String(JsonElement e, string name) { var p = e.GetProperty(name); Require(p.ValueKind == JsonValueKind.String, "FIELD_TYPE"); return p.GetString()!; }
    internal static string Choice(JsonElement e, string name, IReadOnlyList<string> choices, string fallback = "")
    { var value = e.TryGetProperty(name, out _) ? String(e, name) : fallback; Require(choices.Contains(value), "ENUM_" + name); return value; }
}
