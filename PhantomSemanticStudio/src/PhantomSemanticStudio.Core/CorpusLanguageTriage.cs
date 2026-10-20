using System.Text.RegularExpressions;

namespace PhantomSemanticStudio.Core;

public static class CorpusLanguageTriage
{
    private static readonly Regex Sensitive = new(@"https?://\S+|www\.\S+|[\w.+-]+@[\w.-]+\.[a-zA-Z]{2,}|\b(?:\d{1,3}\.){3}\d{1,3}\b|(?<!\w)\+?\d[\d ()-]{7,}\d(?!\w)", RegexOptions.CultureInvariant, TimeSpan.FromMilliseconds(100));
    public static CorpusTriage Classify(string text)
    {
        var cyrillic = text.Any(c => c is >= 'А' and <= 'я' or 'Ё' or 'ё' or 'І' or 'і' or 'Ї' or 'ї' or 'Є' or 'є' or 'Ґ' or 'ґ');
        var latin = text.Any(c => c is >= 'a' and <= 'z' or >= 'A' and <= 'Z');
        if (cyrillic && latin) return new("MIXED", "Кириллица и латиница; язык не доказан.");
        if (cyrillic) return new("CYRILLIC", "Кириллица; конкретный язык не доказан.");
        if (!latin) return new("UNKNOWN", "Недостаточно букв для определения языка.");
        var words = TextRules.Normalize(text).Split(' ');
        if (words.Any(w => w is "privet" or "spasibo" or "pozhaluysta") || words.Contains("kak") && words.Contains("dela"))
            return new("LATIN_TRANSLIT_CANDIDATE", "Возможный русский транслит; требуется ручная проверка, перевода нет.");
        if (words.Any(w => w is "znayu" or "ponimayu" or "hochu" or "kto")) return new("UNKNOWN", "Неоднозначная латиница; возможен транслит или иной язык.");
        return new("EN_OR_OTHER", "Латиница: английский или другой язык; автоматической конвертации нет.");
    }
    public static string Scrub(string text) => Sensitive.Replace(text, "[скрыто: возможные личные данные]");
}
