using System.Globalization;
using System.Security.Cryptography;
using System.Text;
using System.Text.RegularExpressions;

namespace PhantomSemanticStudio.Core;

public static class TextRules
{
    // Приближённый перенос PhantomHumanizedCatalog.normalize: NFKC, ru lower,
    // ё→е, только буквы/цифры/{}, остальные знаки разделяют слова.
    // Java parity на supplementary Unicode требует отдельной проверки bridge.
    public static string Normalize(string? text)
    {
        var source = (text ?? "").Normalize(NormalizationForm.FormKC)
            .ToLower(CultureInfo.GetCultureInfo("ru-RU")).Replace('ё', 'е');
        var result = new StringBuilder(source.Length);
        var space = true;
        foreach (var rune in source.EnumerateRunes())
        {
            if (Rune.IsLetterOrDigit(rune) || rune.Value is '{' or '}')
            {
                result.Append(rune.ToString()); space = false;
            }
            else if (!space) { result.Append(' '); space = true; }
        }
        return result.ToString().Trim();
    }
    public static string Hash(string text) => Hash(Encoding.UTF8.GetBytes(text));
    public static string Hash(byte[] bytes) => Convert.ToHexString(SHA256.HashData(bytes)).ToLowerInvariant();
    public static int CodePoints(string text) => text.EnumerateRunes().Count();
    public static bool ValidPlaceholders(string text)
    {
        foreach (var token in new[] { "{name}", "{value}", "{memory}", "{interest}" }) text = text.Replace(token, "", StringComparison.Ordinal);
        return !text.Contains('{') && !text.Contains('}');
    }
    public static HashSet<string> Trigrams(string normalized)
    {
        var result = new HashSet<string>(StringComparer.Ordinal);
        for (var i = 0; i + 2 < normalized.Length; i++) result.Add(normalized.Substring(i, 3));
        return result;
    }
    public static double Similarity(string a, string b)
    {
        var left = Trigrams(Normalize(a)); var right = Trigrams(Normalize(b));
        if (left.Count == 0 || right.Count == 0) return Normalize(a) == Normalize(b) ? 1 : 0;
        var intersection = left.Count(right.Contains);
        return (double)intersection / (left.Count + right.Count - intersection);
    }
    public static bool IsFunctionalAct(string act) => act.StartsWith("support.", StringComparison.Ordinal)
        || act.StartsWith("identity.", StringComparison.Ordinal) || act.StartsWith("party.", StringComparison.Ordinal)
        || act.StartsWith("item.", StringComparison.Ordinal) || !act.Contains('.');
}
