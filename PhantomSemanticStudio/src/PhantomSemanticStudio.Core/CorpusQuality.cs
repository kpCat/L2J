using System.Text.RegularExpressions;

namespace PhantomSemanticStudio.Core;

public sealed record CorpusQualityIssue(string Code, int Record, string Detail);
public sealed record CorpusSpeechIndicators(int Records, int StrongIndicators, int MildIndicators, int AdultIndicators, int IntimacyTopicIndicators);

/// <summary>Conservative editorial triage. No score here is a human judgement or an approval.</summary>
public static class CorpusQuality
{
    private static readonly Regex Strong = new(@"(?i)\b(?:бля(?:д|т|\b)|пизд|(?:вы|до|за|на|от|по|про|раз|у)?[её]б(?:а|е|ё|и|л|н|у|ы)|наху|нахер|ниху|оху|аху|(?:не)?хуй|хули\b|хуя|хуе)", RegexOptions.CultureInvariant);
    private static readonly Regex Mild = new(@"(?i)\b(?:блин|чёрт|черт|хрен|фигня|задолбал)\b", RegexOptions.CultureInvariant);
    private static readonly Regex Sexual = new(@"(?i)\b(?:секс|трах|оргазм|раздевай|постель|постели|девствен|целк|сиськ|оральн|анальн|пизд(?:а|у|ы|е|ой|ою|ам|ами|ах)\b)", RegexOptions.CultureInvariant);
    private static readonly Regex IntimacyTopic = new(@"(?i)\b(?:близост|нежност|предпочтен|желани|границ|интим|смущ|стыд)", RegexOptions.CultureInvariant);
    private static readonly Regex FirstMaleBody = new(@"(?i)\b(?:мой|моего|моему|моим|моём)\s+член\b|\bу меня\s+(?:есть\s+)?член\b", RegexOptions.CultureInvariant);
    private static readonly Regex FirstFemaleBody = new(@"(?i)\b(?:моя|мою|моей|мои|моих)\s+(?:пизд|сиськ)|\bу меня\s+(?:есть\s+)?(?:пизд|сиськ)", RegexOptions.CultureInvariant);
    private static readonly Regex SecondMaleBody = new(@"(?i)\b(?:твой|твоего|твоему|твоим|твоём)\s+член\b|\bу тебя\s+(?:есть\s+)?член\b", RegexOptions.CultureInvariant);
    private static readonly Regex SecondFemaleBody = new(@"(?i)\b(?:твоя|твою|твоей|твои|твоих)\s+(?:пизд|сиськ)|\bу тебя\s+(?:есть\s+)?(?:пизд|сиськ)", RegexOptions.CultureInvariant);
    private static readonly Regex Cliche = new(@"(?i)(понимаю (?:твои|ваши) чувства|неприятно, когда|важно помнить|следует учитывать|не стоит расстраиваться|как (?:ии|языковая модель)|отличный вопрос)", RegexOptions.CultureInvariant);
    private static readonly Regex MixedScript = new(@"\b(?=[A-Za-zА-Яа-яЁё0-9]*[A-Za-z])(?=[A-Za-zА-Яа-яЁё0-9]*[А-Яа-яЁё])[A-Za-zА-Яа-яЁё0-9]+\b", RegexOptions.CultureInvariant);
    public static CorpusSpeechIndicators MeasureSpeech(IEnumerable<string> texts)
    {
        var count = 0; var strong = 0; var mild = 0; var adult = 0; var intimate = 0;
        foreach (var text in texts) { count++; if (Strong.IsMatch(text)) strong++; if (Mild.IsMatch(text)) mild++; if (Sexual.IsMatch(text)) adult++; if (IntimacyTopic.IsMatch(text)) intimate++; }
        return new(count, strong, mild, adult, intimate);
    }
    public static IReadOnlyList<CorpusQualityIssue> Check(IReadOnlyList<V4Entry> entries, V4GenerationRequest request, IReadOnlyList<V4Entry> peers)
    {
        var issues = new List<CorpusQualityIssue>();
        if (request.Style.StyleId == "strong_profanity" && entries.Count > 0 && MeasureSpeech(entries.Select(e => e.Text)).StrongIndicators * 2 < entries.Count)
            issues.Add(new("STRONG_PROFILE_DENSITY_REVIEW", 0, "Профиль крепкого мата: меньше половины строк содержат лексический индикатор. Метка STRONG не заменяет фактический текст."));
        var previous = peers.ToList();
        for (var i = 0; i < entries.Count; i++)
        {
            var e = entries[i]; void Flag(string code, string detail) => issues.Add(new(code, i + 1, detail));
            if (request.QaOnly) Flag("QA_ONLY_NO_RUNTIME_GENDER_FILTER", "Отдельная QA-сцена, не approved V4 corpus.");
            if (e.Category != request.Category || e.Mode != request.Mode || e.RequiredEvent != request.RequiredEvent) Flag("GENERATION_SCOPE", "Категория, mode или event отличаются от frozen request.");
            if (e.Mode == "TRIGGER" && !e.Category.StartsWith("social.conversation.", StringComparison.Ordinal) && !e.Category.StartsWith("social.greeting.", StringComparison.Ordinal))
            {
                var trigger = Regex.Replace(V4Rules.Normalize(e.Text), @"[\p{P}\p{S}]", "").Replace('ё', 'е').Trim();
                if (new[] { "давай", "о чем именно", "я не против", "давай я не против", "поболтаем так поболтаем", "смотря о чем", "у тебя есть идеи", "ладно", "хорошо" }.Contains(trigger))
                    Flag("TRIGGER_TOO_GENERIC", "Буквальный вход без контекста не указывает на эту категорию.");
            }
            if (e.Mode != "TRIGGER")
            {
                if (e.Relations.Any(r => r != request.Style.Relation)) Flag("RELATION_SCOPE", "Модель расширила или потеряла разрешённое отношение.");
                if (e.Voices.Any(v => v != request.Style.Voice)) Flag("VOICE_SCOPE", "Голос не совпадает с заданной альтернативой.");
                if (!request.Style.MatureRequested && e.Maturity != "GENERAL") Flag("MATURE_SCOPE", "MATURE не разрешён в этом запросе.");
                if (!request.Style.StrongProfanityRequested && e.Profanity != "NONE") Flag("PROFANITY_SCOPE", "Ругань не разрешена в этой порции.");
                if (Strong.IsMatch(e.Text) && e.Profanity != "STRONG" || Mild.IsMatch(e.Text) && e.Profanity == "NONE")
                    Flag("PROFANITY_MARKING", "Лексический индикатор ругани не соответствует метке; нужен редактор.");
                if (Sexual.IsMatch(e.Text) && e.Maturity != "MATURE") Flag("MATURE_MARKING_REVIEW", "Возможный взрослый подтекст без MATURE; индикатор не доказывает смысл.");
                if (e.Maturity == "MATURE" && !Sexual.IsMatch(e.Text) && !(request.Style.StyleId == "intimate_conversation" && IntimacyTopic.IsMatch(e.Text)))
                    Flag("MATURE_SUBSTANCE_REVIEW", "Нет ожидаемого лексического индикатора профиля; смысл требует редактора. Двусмысленность и флирт не обязаны содержать прямую сексуальную лексику.");
                if (e.Profanity != "NONE" && request.Style.Channel is not ("TELL" or "BOT_PRIVATE"))
                    Flag("JAVA_PUBLIC_PROFANITY_BLOCKED", "Текущий Java consumer допускает мат только в приватном согласном контексте; публичная запись недоступна.");
                if (e.Stance is "FLIRT" or "AGGRESSIVE_BANTER" && (request.Style.Channel is not ("TELL" or "BOT_PRIVATE") || request.Style.Relation is not ("TRUSTED" or "FAMILIAR")))
                    Flag("JAVA_STANCE_CONTEXT_BLOCKED", "Этот stance доступен в текущем Java только приватно и при FAMILIAR/TRUSTED с согласием.");
                var bodyText = Regex.Replace(e.Text, @"(?i)\bчлен(?:а|ом|у|е)?\s+(?:клана|группы|совета|пати)\b", "участник");
                var firstMale = FirstMaleBody.IsMatch(bodyText); var firstFemale = FirstFemaleBody.IsMatch(bodyText);
                var secondMale = SecondMaleBody.IsMatch(bodyText); var secondFemale = SecondFemaleBody.IsMatch(bodyText);
                if (firstMale || firstFemale || secondMale || secondFemale)
                    Flag("ANATOMY_RUNTIME_UNFILTERED", "Персональная анатомия не может безопасно отбираться текущим универсальным V4 runtime.");
                if (request.Style.SpeakerSex == "FEMALE" && firstMale || request.Style.SpeakerSex == "MALE" && firstFemale
                    || request.Style.AddresseeSex == "FEMALE" && secondMale || request.Style.AddresseeSex == "MALE" && secondFemale)
                    Flag("ANATOMY_CONTRADICTION", "Анатомическая атрибуция противоречит явно заданной вымышленной сцене.");
                var saysVirgin = Regex.IsMatch(e.Text, @"(?i)\bя\s+(?:(?:ещё|всё ещё)\s+)?(?:девственница|целка)\b");
                var saysExperienced = Regex.IsMatch(e.Text, @"(?i)(?:я|у меня).{0,32}(?:давно не девствен|не девственниц|уже был секс|первый раз уже|не первый раз)");
                if (saysVirgin || saysExperienced) Flag("PERSONAL_FACT_RUNTIME_UNFILTERED", "Личный факт нельзя привязать к персонажу через текущий V4 wire.");
                if (request.Style.SpeakerVirginity == "VIRGIN" && saysExperienced || request.Style.SpeakerVirginity == "NOT_VIRGIN" && saysVirgin)
                    Flag("PERSONAL_FACT_CONTRADICTION", "Реплика противоречит явно заданной биографии вымышленного совершеннолетнего персонажа.");
            }
            if (Cliche.IsMatch(e.Text)) Flag("GENERIC_AI_STYLE", "Возможная шаблонная или наставническая формулировка.");
            if (MixedScript.IsMatch(e.Text)) Flag("MIXED_SCRIPT_TOKEN_REVIEW", "В одном слове смешаны кириллица и латиница. Возможно повреждение текста или намеренный игровой сленг; требуется просмотр.");
            var normalized = V4Rules.Normalize(e.Text);
            foreach (var peer in previous)
            {
                if (V4Rules.Normalize(peer.Text) == normalized) { Flag("EXACT_DUPLICATE", peer.Id); break; }
                if (normalized.Length >= 16 && NearClone(e.Text, peer.Text)) { Flag("NEAR_DUPLICATE_REVIEW", peer.Id); break; }
            }
            previous.Add(e);
        }
        return issues.AsReadOnly();
    }
    public static bool NearClone(string first, string second)
    {
        static HashSet<string> Tokens(string text) => Regex.Matches(V4Rules.Normalize(text), @"[\p{L}\p{N}]+")
            .Select(m => m.Value).Where(t => t.Length > 2).ToHashSet(StringComparer.Ordinal);
        static HashSet<string> Grams(string text)
        { var clean = Regex.Replace(V4Rules.Normalize(text), @"[^\p{L}\p{N}]", ""); return Enumerable.Range(0, Math.Max(0, clean.Length - 2)).Select(i => clean.Substring(i, 3)).ToHashSet(StringComparer.Ordinal); }
        var a = Tokens(first); var b = Tokens(second);
        if (a.Count >= 3 && a.SetEquals(b)) return true;
        var x = Grams(first); var y = Grams(second);
        return a.Count >= 3 && b.Count >= 3 && a.Intersect(b).Count() >= 3 && x.Union(y).Count() > 0
            && (double)x.Intersect(y).Count() / x.Union(y).Count() >= .82;
    }
}
