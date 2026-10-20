using System.Text;
using System.Xml;
using System.Xml.Linq;

namespace PhantomSemanticStudio.Core;

/// <summary>Только чтение известных humanized-файлов. Не интерпретирует настройки живого сервера.</summary>
public sealed class PackReader
{
    private const int MaxFile = 1024 * 1024;
    private const int MaxTotal = 32 * 1024 * 1024;
    private static readonly UTF8Encoding StrictUtf8 = new(false, true);
    private static readonly string[] BaseFiles =
    [
        "semantic/humanized/high-five-ru-humanized-semantic-v1.xml",
        "conversation/humanized/high-five-ru-humanized-conversation-v1.xml",
        "conversation/humanized/high-five-ru-persona-v1.xml",
        "semantic/humanized/high-five-ru-humanized-corpus-v1.tsv"
    ];
    private static readonly string[] CustomFiles =
    [
        "semantic/custom/my-ru-aliases.xml", "semantic/custom/my-slang.xml", "semantic/custom/my-social-topics.xml",
        "conversation/custom/my-phrases.xml", "conversation/custom/my-profanity.xml", "conversation/custom/my-mature-dialogue.xml"
    ];
    public PackSnapshot Load(string moduleRoot, CancellationToken cancellationToken = default)
    {
        var module = PathSafety.Canonical(moduleRoot);
        PathSafety.AssertNoReparsePoints(module);
        var root = Path.Combine(module, "dist", "game", "data", "phantoms");
        if (!Directory.Exists(root)) throw new DirectoryNotFoundException("Не найден dist/game/data/phantoms. Выберите корень High Five, не весь L2J.");
        var files = new List<SourceFileStamp>(); var entries = new Dictionary<string, PackEntry>(StringComparer.Ordinal);
        var topics = new HashSet<string>(StringComparer.Ordinal); var acts = new HashSet<string>(StringComparer.Ordinal);
        var warnings = new List<string>(); long totalBytes = 0;
        var pathsRead = new HashSet<string>(StringComparer.Ordinal);

        XDocument? Read(string relative, bool parse = true, int limit = MaxFile)
        {
            cancellationToken.ThrowIfCancellationRequested();
            if (!pathsRead.Add(relative)) throw new InvalidDataException("Повтор файла в manifest: " + relative);
            var path = PathSafety.ResolveRelative(root, relative);
            using var stream = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.Read);
            if (stream.Length < 1 || stream.Length > limit || (totalBytes += stream.Length) > MaxTotal)
                throw new InvalidDataException("Превышен лимит объёма пакета: " + relative);
            var bytes = new byte[checked((int)stream.Length)]; stream.ReadExactly(bytes);
            var text = StrictUtf8.GetString(bytes);
            files.Add(new SourceFileStamp(relative, TextRules.Hash(bytes), bytes.Length));
            if (!parse) return null;
            using var xml = XmlReader.Create(new StringReader(text.TrimStart('\uFEFF')), new XmlReaderSettings
            {
                DtdProcessing = DtdProcessing.Prohibit, XmlResolver = null,
                MaxCharactersInDocument = MaxFile * 2, MaxCharactersFromEntities = 0,
                IgnoreComments = false
            });
            return XDocument.Load(xml, LoadOptions.SetLineInfo);
        }
        void Parse(string relative, XDocument doc, bool custom)
        {
            var documentRoot = doc.Root ?? throw new InvalidDataException("XML без корня: " + relative);
            var version = (string?)documentRoot.Attribute("version");
            if (version is not ("1" or "2" or "3")) throw new InvalidDataException("Неподдерживаемая версия XML: " + relative);
            var expectedRoot = relative switch
            {
                "semantic/custom/my-ru-aliases.xml" => "aliases",
                "semantic/custom/my-slang.xml" => "slang",
                "semantic/custom/my-social-topics.xml" => "socialTopics",
                "conversation/custom/my-phrases.xml" => "phrases",
                "conversation/custom/my-profanity.xml" => "profanity",
                "conversation/custom/my-mature-dialogue.xml" => "matureDialogue",
                _ => documentRoot.Name.LocalName
            };
            if (documentRoot.Name != expectedRoot) throw new InvalidDataException("Неверный корень custom XML: " + relative);
            foreach (var element in documentRoot.Descendants())
            {
                cancellationToken.ThrowIfCancellationRequested();
                string A(string key, string fallback = "") => ((string?)element.Attribute(key) ?? fallback).Trim();
                var tag = element.Name.LocalName;
                if (element.Attribute("override") is { } overrideAttribute && (!custom || overrideAttribute.Value is not ("true" or "false")))
                    throw new InvalidDataException("Недопустимый override: " + relative);
                // identity aliases/classes — отдельная Java-модель: здесь не выдаём их за social patterns.
                string kind;
                if (tag == "pattern" && element.Attribute("act") != null && element.Attribute("phrase") != null) kind = "PATTERN";
                else if (tag == "template" && element.Attribute("act") != null) kind = "TEMPLATE";
                else if (tag == "alias" && element.Attribute("from") != null && element.Attribute("to") != null) kind = "ALIAS";
                else if (tag == "entry" && element.Attribute("level") != null) kind = "PROFANITY";
                else
                {
                    if (element.Parent == documentRoot || tag is "pattern" or "template" or "alias" or "entry")
                        if (tag is not ("patterns" or "templates" or "aliases" or "profanity"))
                            warnings.Add($"Неинтерпретируемый элемент {tag}: {relative}:{((IXmlLineInfo)element).LineNumber}. Контракт Studio неполный; Java-validator NOT_RUN.");
                    continue;
                }
                var id = kind == "ALIAS" ? TextRules.Normalize(A("from")) : A("id");
                if (id.Length == 0) throw new InvalidDataException("Запись без ID: " + relative);
                var item = new PackEntry
                {
                    Id = id, Kind = kind, Text = A(kind == "PATTERN" ? "phrase" : kind == "ALIAS" ? "to" : "text"),
                    Act = A("act", A("acts")), Topic = A("topic"), Band = A("band", "UNKNOWN"), Register = A("register", "NEUTRAL"),
                    Profanity = A("profanity", A("level", "NONE")),
                    Mature = A("mature") == "true" || (string?)documentRoot.Attribute("mature") == "true" || relative.EndsWith("my-mature-dialogue.xml", StringComparison.Ordinal),
                    Priority = int.TryParse(A("priority"), out var priority) ? priority : 0,
                    Fact = A("fact"), Recall = A("recall"), SourceFile = relative,
                    SourceLine = ((IXmlLineInfo)element).HasLineInfo() ? ((IXmlLineInfo)element).LineNumber : 0
                };
                var key = kind + ":" + id; var exists = entries.ContainsKey(key);
                var replace = custom && A("override") == "true";
                if (exists != replace) throw new InvalidDataException(exists ? "Коллизия ID без явного override: " + id : "Не найден объект override: " + id);
                entries[key] = item;
                if (item.Topic.Length > 0) topics.Add(item.Topic);
                if (item.Act.Length > 0 && kind != "PROFANITY") acts.Add(item.Act);
            }
        }
        foreach (var relative in BaseFiles)
        {
            var doc = Read(relative, relative.EndsWith(".xml", StringComparison.Ordinal), 262144);
            if (doc != null) Parse(relative, doc, false);
        }
        var v2 = new[] { "semantic/humanized/high-five-ru-humanized-semantic-v2.xml", "conversation/humanized/high-five-ru-humanized-conversation-v2.xml" };
        var v2Count = v2.Count(p => File.Exists(PathSafety.ResolveRelative(root, p)));
        if (v2Count == 1) throw new InvalidDataException("Неполная пара humanized v2.");
        if (v2Count == 2) foreach (var relative in v2) Parse(relative, Read(relative, true, 262144)!, false);
        const string manifest = "semantic/humanized/v3/manifest.xml";
        if (File.Exists(PathSafety.ResolveRelative(root, manifest)))
        {
            var doc = Read(manifest, true, 262144)!;
            if (doc.Root?.Name != "humanizedV3Manifest" || (string?)doc.Root.Attribute("version") != "3") throw new InvalidDataException("Неподдерживаемый manifest v3.");
            foreach (var topic in doc.Root.Element("topics")?.Elements("topic") ?? []) topics.Add((string?)topic.Attribute("key") ?? "");
            foreach (var act in doc.Root.Element("acts")?.Elements("act") ?? []) acts.Add((string?)act.Attribute("key") ?? "");
            var segments = doc.Root.Element("segments")?.Elements("segment").ToList() ?? throw new InvalidDataException("В manifest нет segments.");
            if (segments.Count is < 1 or > 64) throw new InvalidDataException("Лимит 64 сегмента v3.");
            foreach (var segment in segments)
            {
                var kind = (string?)segment.Attribute("kind"); var relative = (string?)segment.Attribute("path") ?? "";
                if (!(kind == "SEMANTIC" && relative.StartsWith("semantic/humanized/v3/segments/", StringComparison.Ordinal)
                    || kind == "CONVERSATION" && relative.StartsWith("conversation/humanized/v3/segments/", StringComparison.Ordinal))
                    || !relative.EndsWith(".xml", StringComparison.Ordinal)) throw new InvalidDataException("Неверный kind/path сегмента v3.");
                var segmentDoc = Read(relative)!;
                var expected = kind == "SEMANTIC" ? "humanizedV3SemanticSegment" : "humanizedV3ConversationSegment";
                if (segmentDoc.Root?.Name != expected) throw new InvalidDataException("Неверный корень сегмента v3.");
                Parse(relative, segmentDoc, false);
            }
        }
        else warnings.Add("Manifest v3 отсутствует: импортированы только имеющиеся v1/v2 и custom.");
        foreach (var relative in CustomFiles)
            if (File.Exists(PathSafety.ResolveRelative(root, relative))) Parse(relative, Read(relative, true, 65536)!, true);
        var items = entries.Values.ToList();
        if (items.Count(x => x.Kind == "PATTERN") > 8192 || items.Count(x => x.Kind == "TEMPLATE") > 32768 || items.Count(x => x.Kind == "ALIAS") > 2048 || items.Count(x => x.Kind == "PROFANITY") > 1024)
            throw new InvalidDataException("Превышены текущие лимиты humanized v3.");
        foreach (var group in items.Where(x => x.Kind is "TEMPLATE" or "PATTERN").GroupBy(x => x.Kind + ":" + TextRules.Normalize(x.Text)).Where(g => g.Count() > 1).Take(30))
            warnings.Add("Дубликат в исходнике: " + string.Join(", ", group.Select(x => x.Id)));
        warnings.Add("Это импорт/инспекция Studio, не полный Java-validator. Existing custom включён; активность серверных INI-флагов не проверяется.");
        topics.Remove(""); acts.Remove("");
        return new PackSnapshot
        {
            ModuleRoot = module, DataRoot = root, Files = files, Entries = items,
            Topics = topics.Order(StringComparer.Ordinal).ToList(), Acts = acts.Order(StringComparer.Ordinal).ToList(), Warnings = warnings,
            Fingerprint = TextRules.Hash(string.Join("\n", files.Select(x => x.RelativePath + ":" + x.Sha256)))
        };
    }
}
