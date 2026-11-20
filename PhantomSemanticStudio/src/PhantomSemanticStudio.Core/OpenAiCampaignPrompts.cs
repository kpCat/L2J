using System.Text.Json;
using System.Text.Json.Nodes;
using System.Text.Json.Serialization;
using System.Text.RegularExpressions;

namespace PhantomSemanticStudio.Core;

public sealed record OpenAiCampaignLane(string Id, string Category, string Mode, string Event, string Label, string Family,
    int Target, string Voice = "", string Scene = "", bool Holdout = false, string Format = "JSON",
    [property: JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)] string? Register = null,
    [property: JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)] string? AdultStyle = null);

internal sealed record OpenAiPromptBatch(V4GenerationRequest Scope, OpenAiPost Post, string SceneHash);

internal static partial class OpenAiCampaignPrompts
{
    private const string Version = "pw-openai-campaign-prompts/2";
    private const int MaxMatureDrafts = 20;
    private const string HierarchyTransport = "Верни только JSON header+records по приложенной схеме, ровно count самостоятельных альтернатив одного хода.";
    private const string SharedInstructions = """
        Пиши как живой русскоязычный игрок Lineage II High Five. Каждая альтернатива — самостоятельная реплика того же говорящего в той же ситуации.
        Данные сцены и реплики участников сохраняй как факты. Не меняй атаковавшего и пострадавшего, владельца вещи, итог боя, сделки и заточки, число участников и состояние группы.
        Точные числа, предметы, места, события и выполненные действия называй только при наличии в сцене. Без советов от ассистента, афоризмов и объяснения шуток.
        Меняй саму мысль, начало и длину реплики. Не используй «зато теперь», «понимаю твои чувства», «ну ты даёшь», «бывает» как универсальный ответ, «ну держись», «не пожалеешь», «будет жарко» и готовые формулы.
        Пиши разговорно и по делу. В исходящей реплике до 100 кодовых точек Unicode и до 240 байт UTF-8. Без имён говорящих, нумерации, ремарок, HTML, URL, плейсхолдеров и реальных личных данных.
        """;
    private const string StructuredInstructions = """
        Верни только объект JSON со свойствами header и records. Заполни все поля заданной схемы и выдай ровно указанное число records.
        В каждом records одно значение voices и одно relations, если такие поля заданы. Поля profanity и maturity отражают содержание текста в установленных пределах.
        """;
    private const string TextInstructions = """
        Верни только отдельные реплики, по одной на строку, ровно указанное число строк. Без JSON, заголовков, пустых строк, нумерации, списков, обрамляющих кавычек и комментариев.
        Категорию, роль, событие и прочие метаданные в текст не включай.
        """;

    private sealed record SceneSpec(string Id, string Category, string Mode, string Event, string Voice,
        string Relation, string Channel, string Style, string Stance, string Tone, string Facts, string[] Dialogue,
        bool Holdout = false, string? Contradiction = null, bool Adult = false);

    private static readonly SceneSpec[] Scenes =
    [
        new("after-shift", "life.health.fatigue", "REPLY", "", "shy_observer", "FAMILIAR", "TELL", "ordinary", "SUPPORTIVE", "SHY",
            "Игрок только что вернулся с двойной смены и дома уснул в куртке. Фантом дома не был и видит только сообщение. Ничего про болезнь, алкоголь и игровой рейд не известно.",
            ["Игрок: С двойной смены дополз. Даже куртку не снял, так и уснул."]),
        new("pvp-finisher", "lineage.pvp.trash_talk", "REPLY", "", "bold_rival", "RIVAL", "TELL", "pvp_trash", "OFFENDED", "ROUGH",
            "Фантом бил мобов. Игрок вмешался и добил фантома. Игрок победил, фантом проиграл. Игрок хвастается. Никакого состоявшегося реванша, помощников, дропа или смены уровня.",
            ["Игрок: Я тебя на мобах добил, а ты ещё споришь, кто тут тащит?"],
            Contradiction: @"\b(?:я тебя добил|ты бил мобов|ты на мобах фармил|я победил тебя|я тебя убил)\b"),
        new("broken-weapon", "lineage.enchant.fail", "REPLY", "", "dry_wit", "FAMILIAR", "CLAN", "ordinary", "AMUSED", "DRY",
            "Игрок сломал своё оружие при попытке заточить с +5 на +6. Фантом свою вещь не точил. Оружие сломано, размер компенсации и вид оружия неизвестны.",
            ["Игрок: Попробовал с пяти на шесть. Всё, пушки больше нет."],
            Contradiction: @"\b(?:моя пушка сломалась|я свою пушку сломал|заточил до \+?6|пушка цела)\b"),
        new("healer-mana", "lineage.party.buffs", "REPLY", "", "direct", "FAMILIAR", "PARTY", "ordinary", "NEUTRAL", "DIRECT",
            "Группа остановилась. Хилу не хватает маны, а у группы кончились бафы. Лидер предложил дождаться маны перед ребафом. Никто не умер, бой не начат.",
            ["Игрок: Стойте, хил досидит ману, потом ребафнемся и пойдём."],
            Contradiction: @"\b(?:хил умер|хил упал|мана полная|бафы уже обновили)\b"),
        new("own-mistake", "social.banter.rough", "TRIGGER", "", "", "FAMILIAR", "CLAN", "ordinary", "", "",
            "Следующее сообщение пишет игрок. Игрок спутал окна чата и отправил клану собственный список покупок. Он сам предлагает друзьям подколоть его за промах. Фантом список не писал и не отправлял.",
            [], true, @"\b(?:ты отправил|твой список покупок|вы отправили|ну и список ты)\b"),
        new("last-offer", "lineage.commerce.bargaining", "REPLY", "", "cautious_trader", "UNKNOWN", "TRADE", "ordinary", "NEUTRAL", "CALM",
            "Фантом продавец, игрок покупатель. Игрок уточнил последнее предложение: 55 тысяч адены, прежние 50 тысяч отменены. Предмет не назван, сделки и передачи денег не было.",
            ["Игрок: Я сперва 50 предложил. Исправляюсь, 55 тысяч, устроит?"],
            true, @"\b(?:уже продал|деньги (?:уже )?получил|за 50 отдаю|пятьдесят устраивает)\b"),
        new("pvp-cover", "social.friendship.support", "INITIATIVE", "pvp.help.received", "loyal_companion", "TRUSTED", "PARTY", "ordinary", "SUPPORTIVE", "WARM",
            "Собеседник прикрыл фантома, пока фантом выходил из PvP. Фантом получил помощь. Исход боя неизвестен. Входящего сообщения нет.",
            [], Contradiction: @"\b(?:я тебя прикрыл|ты без меня бы|я тебя спас|мы их убили|мы победили)\b"),
        new("shy-flirt", "social.flirt.bold", "REPLY", "", "playful_tease", "TRUSTED", "TELL", "light_flirt", "FLIRT", "PLAYFUL",
            "Двое вымышленных взрослых давно знакомы. Игрок заранее занял соседнее место и поддразнивает фантома. Они охотно флиртуют, свидания ещё не было. Лёгкий флирт без сексуального подтекста.",
            ["Игрок: Я тебе рядом место оставил. Чисто случайно, конечно."], true)
    ];

    private static readonly SceneSpec AdultScene = new("shy-flirt", "social.banter.double_meanings", "REPLY", "", "rough_joker",
        "TRUSTED", "BOT_PRIVATE", "double_meanings", "AMUSED", "PLAYFUL",
        "Двое вымышленных совершеннолетних добровольно шутят в личном чате. Игрок назвал удачный игровой подкол «грязным». Фантом отвечает неграфическим каламбуром с бытовым и взрослым прочтениями. Анатомия и биография участников не заданы.",
        ["Игрок: Это было грязно. Ты точно только про чат говорил?"], true, Adult: true);

    private static readonly string[] SceneAngles =
    [
        "Зацепись за конкретное неудобство или мелкий промах в самой теме.",
        "Разыграй короткое уточнение или возражение по существу темы.",
        "Возьми небольшое бытовое или игровое затруднение из этой темы, сохранив её область.",
        "Передай реакцию на неожиданную, но правдоподобную деталь темы, без новых игровых фактов.",
        "Отреагируй на намерение собеседника. Не добавляй уже выполненное действие.",
        "Возьми частный случай темы в знакомом разговорном контексте. Уточняй неизвестное вместо подмены фактов.",
        "Сделай несколько разных ходов из одной ситуации: сухая реакция, простой вопрос или уместный подкол.",
        "Выбери чёткую реакцию на названный предмет темы. Не меняй область ради шутки."
    ];

    private static readonly Dictionary<string, string> EventFacts = new(StringComparer.Ordinal)
    {
        ["pvp.attack.received"] = "Соперник атаковал фантома. Итог боя и урон неизвестны.",
        ["pvp.death.suffered"] = "Фантом погиб в игровом PvP от соперника. Реванша и новой победы ещё не было.",
        ["pvp.help.received"] = "Собеседник помог фантому в PvP. Итог боя неизвестен.",
        ["pvp.kill.caused"] = "Фантом победил соперника в игровом PvP. Дроп и награда неизвестны."
    };

    private static readonly Dictionary<string, string> EventContradictions = new(StringComparer.Ordinal)
    {
        ["pvp.attack.received"] = @"\b(?:я первый напал|я начал драку|я на тебя напал)\b",
        ["pvp.death.suffered"] = @"\b(?:я тебя убил|я тебя добил|ты от меня умер|я победил тебя)\b",
        ["pvp.help.received"] = @"\b(?:я тебя прикрыл|я прикрыл тебя|я тебя спас)\b",
        ["pvp.kill.caused"] = @"\b(?:ты меня убил|ты меня добил|я от тебя умер|ты победил меня)\b"
    };

    private static readonly Regex Wrapper = new(
        @"\A(?:[-–—•*]\s|\d{1,3}[.)]\s*|(?:игрок|фантом|ответ|реплика|собеседник|героиня|assistant|user)\s*[:：]|[""«„“].*[""»”]\z)",
        RegexOptions.IgnoreCase | RegexOptions.CultureInvariant);

    private static V4Registry Registry => V4Registry.Default;
    private static PromptProfiles Profiles => PromptProfiles.Default;
    private static PromptHierarchy Hierarchy => PromptHierarchy.Default;
    internal static string MatureCategory => AdultScene.Category;
    private static string MatureLaneId => MatureCategory + ".reply.mature";

    internal static string LegacyVersionHash { get; } = Hash(new
    {
        Version, MaxMatureDrafts, HierarchyTransport, SharedInstructions, StructuredInstructions, TextInstructions,
        Scenes, AdultScene, SceneAngles, EventFacts, EventContradictions, Adapter = "OPENAI_RESPONSES", Wrapper = Wrapper.ToString(),
        RegistryHash = Registry.Hash, ProfileHash = Profiles.Hash, HierarchyHash = Hierarchy.Hash
    });

    internal static IReadOnlyList<OpenAiCampaignLane> PlanLanes(int total, IReadOnlyDictionary<string, int>? categoryTargets = null, bool mature = false)
    {
        var categories = Registry.Categories;
        V4Rules.Require(total >= categories.Count && total <= V4Rules.MaxRecords, "OPENAI_CATEGORY_TOTAL");
        if (categoryTargets != null)
        {
            V4Rules.Require(categoryTargets.Count == categories.Count
                && categoryTargets.Keys.All(key => categories.Any(category => category.Key == key))
                && categories.All(category => categoryTargets.TryGetValue(category.Key, out var quota) && quota > 0)
                && categoryTargets.Values.Sum(quota => (long)quota) == total, "OPENAI_CATEGORY_QUOTAS");
        }

        var result = new List<OpenAiCampaignLane>();
        for (var i = 0; i < categories.Count; i++)
        {
            var category = categories[i];
            var family = Hierarchy.FamilyFor(category.Key).Id;
            var target = categoryTargets?[category.Key] ?? Share(total, categories.Count, i);
            var events = Registry.Events.Where(item => V4GenerationRequest.InitiativeCompatible(category.Key, item.Code)).ToArray();
            var initiative = Math.Min(Math.Max(0, target - 2), events.Length == 0 ? 0 : Math.Max(events.Length, target / 6));
            var trigger = target - initiative >= 2 ? Math.Max(1, (target - initiative) / 3) : 0;
            var reply = target - initiative - trigger;
            var adult = mature && category.Key == MatureCategory && reply > 1 ? Math.Min(MaxMatureDrafts, Math.Max(1, reply / 10)) : 0;

            Add("REPLY", "", reply - adult);
            if (adult > 0)
                result.Add(new(MatureLaneId, category.Key, AdultScene.Mode, AdultScene.Event, category.Label, family, adult,
                    AdultScene.Voice, AdultScene.Facts));
            Add("TRIGGER", "", trigger);
            for (var e = 0; e < events.Length; e++)
                Add("INITIATIVE", events[e].Code, Share(initiative, events.Length, e));

            void Add(string mode, string eventCode, int quota)
            {
                if (quota == 0) return;
                var id = category.Key + "." + (eventCode.Length > 0 ? eventCode : mode.ToLowerInvariant());
                result.Add(new(id, category.Key, mode, eventCode, category.Label, family, quota));
            }
        }
        V4Rules.Require(result.Sum(lane => lane.Target) == total && result.Select(lane => lane.Id).Distinct(StringComparer.Ordinal).Count() == result.Count,
            "OPENAI_LANE_QUOTAS");
        return result.AsReadOnly();
    }

    internal static IReadOnlyList<OpenAiCampaignLane> PilotLanes(bool mature)
    {
        var pilot = new List<OpenAiCampaignLane>();
        foreach (var original in Scenes)
        {
            var scene = mature && original.Id == AdultScene.Id ? AdultScene : original;
            var category = Registry.Categories.Single(category => category.Key == scene.Category);
            var formats = pilot.Count % 4 == 0 ? new[] { "JSON", "TEXT" } : new[] { "TEXT", "JSON" };
            foreach (var format in formats)
                pilot.Add(new(PilotId(scene, format), scene.Category, scene.Mode, scene.Event, category.Label,
                    Hierarchy.FamilyFor(scene.Category).Id, 3, scene.Voice, scene.Facts, scene.Holdout, format));
        }
        return pilot.AsReadOnly();
    }

    internal static OpenAiPromptBatch Prepare(OpenAiCampaignLane lane, int count, int logicalBatch, string model, bool mature = false)
        => lane.Register == null ? PrepareLegacy(lane, count, logicalBatch, model, mature)
            : PrepareEditorial(lane, count, logicalBatch, model, mature);

    private static OpenAiPromptBatch PrepareLegacy(OpenAiCampaignLane lane, int count, int logicalBatch, string model,
        bool mature, SceneSpec? fixedScene = null)
    {
        V4Rules.Require(count is >= 1 and <= 20 && count <= lane.Target && logicalBatch >= 0
            && lane.Format is "JSON" or "TEXT", "OPENAI_BATCH_SCOPE");
        var category = Registry.Categories.SingleOrDefault(category => category.Key == lane.Category)
            ?? throw new InvalidDataException("OPENAI_CATEGORY_SCOPE");
        var family = Hierarchy.FamilyFor(category.Key);
        V4Rules.Require(lane.Family == family.Id && lane.Label == category.Label, "OPENAI_CATEGORY_SCOPE");

        var scene = fixedScene ?? FindScene(lane, mature);
        var trigger = lane.Mode == "TRIGGER";
        var voice = trigger ? "ANY" : lane.Voice.Length > 0 ? lane.Voice : VoiceFor(lane, logicalBatch);
        var situation = scene?.Facts ?? CampaignSituation(lane, logicalBatch);
        var roughVoice = Registry.Voices.SingleOrDefault(item => item.Key == voice)?.ProfanityTolerance >= 75;
        var (preset, relation, channel, defaultStance) = CampaignStyle(lane, trigger, roughVoice);
        var strong = roughVoice && (scene?.Style ?? preset) is "pvp_trash" or "rough_humor" or "double_meanings";
        if (scene == null && channel == "TELL")
            situation += " Разговор идёт в личном игровом чате.";
        var scope = new V4GenerationRequest
        {
            Category = category.Key, Mode = lane.Mode, RequiredEvent = lane.Event, Count = count,
            Transport = "OPENAI_RESPONSES",
            MaxOutputTokens = 384 + count * 160,
            Strategy = scene?.Adult == true ? "mature-rough" : "dialogue-context",
            DesiredStance = scene?.Stance is { Length: > 0 } stance ? stance : defaultStance,
            DesiredTone = scene?.Tone is { Length: > 0 } tone ? tone : null,
            ProfanityLimit = strong ? "STRONG" : "NONE",
            Style = new GenerationStyleContext
            {
                Category = category.Key, Voice = voice, Relation = scene?.Relation ?? relation, Channel = scene?.Channel ?? channel,
                StyleId = scene?.Style ?? preset, Situation = situation, Dialogue = scene?.Dialogue.ToArray() ?? [],
                StrongProfanityRequested = strong, MatureRequested = scene?.Adult == true,
                OwnerMatureOptIn = scene?.Adult == true && mature, AdultsConsenting = scene?.Adult == true
            }
        };
        var components = Hierarchy.Freeze(scope, Profiles, Registry);
        scope = scope with { Components = components };
        var hierarchy = PromptHierarchy.Compose(components);
        V4Rules.Require(hierarchy.Contains(HierarchyTransport, StringComparison.Ordinal), "OPENAI_PROMPT_TRANSPORT_DRIFT");
        hierarchy = hierarchy.Replace(HierarchyTransport, "", StringComparison.Ordinal);
        var common = hierarchy + "\n\n" + SharedInstructions + "\n"
            + (scope.Style.MatureRequested
                ? "Допущен только неграфический взрослый юмор между согласными вымышленными совершеннолетними в личном чате."
                : "Реплики GENERAL, без сексуального подтекста.");

        var input = new
        {
            category = category.Key, category.Label, category.Act, category.Intent, scope.Mode,
            scope.RequiredEvent, scope.Count, NextSpeaker = trigger ? "PLAYER" : "PHANTOM",
            Voice = voice, scope.Style.Relation, scope.Style.Channel, scope.DesiredStance, scope.DesiredTone, scope.ProfanityLimit,
            Facts = situation, Dialogue = scope.Style.Dialogue
        };
        var schema = lane.Format == "JSON" ? StrictSchema(scope) : null;
        var instructions = common + "\n\n" + (schema == null ? TextInstructions : StructuredInstructions);
        var post = OpenAiResponsesClient.Prepare(model, instructions, JsonSerializer.Serialize(input, PromptProfiles.CompactJson),
            scope.MaxOutputTokens, schema);
        return new(scope, post, Hash(new { input, Instructions = common }));
    }

    internal static string CanonicalJsonl(string text, OpenAiCampaignLane lane, V4GenerationRequest scope)
    {
        V4Rules.Require(lane.Category == scope.Category && lane.Mode == scope.Mode && lane.Event == scope.RequiredEvent
            && lane.Format is "JSON" or "TEXT" && V4Rules.Utf8.GetByteCount(text) <= V4BatchReader.MaxFileBytes, "OPENAI_RESPONSE_SCOPE");
        var category = Registry.Categories.Single(item => item.Key == scope.Category);
        if (lane.Format == "JSON")
        {
            using var document = JsonDocument.Parse(text, new JsonDocumentOptions { MaxDepth = 8, AllowDuplicateProperties = false });
            var canonical = V4CorpusGeneration.ToJsonl(text, scope, Registry);
            foreach (var row in document.RootElement.GetProperty("records").EnumerateArray())
                ValidateReplica(V4Rules.String(row, "text"), scope);
            return canonical;
        }

        var normalized = text.Replace("\r\n", "\n", StringComparison.Ordinal);
        if (normalized.EndsWith('\n')) normalized = normalized[..^1];
        var lines = normalized.Split('\n');
        V4Rules.Require(lines.Length == scope.Count, "OPENAI_TEXT_COUNT");
        var records = new List<Dictionary<string, object>>(lines.Length);
        foreach (var line in lines)
        {
            ValidateReplica(line, scope);
            var row = new Dictionary<string, object>(StringComparer.Ordinal) { ["text"] = line };
            if (scope.Mode == "TRIGGER")
            {
                row["intent"] = category.Intent;
                row["ambiguity"] = "NONE";
            }
            else
            {
                var speech = CorpusQuality.MeasureSpeech([line]);
                row["stance"] = scope.DesiredStance ?? "NEUTRAL";
                row["tone"] = scope.DesiredTone ?? "CALM";
                row["relations"] = new[] { scope.Style.Relation };
                row["voices"] = new[] { scope.Style.Voice };
                row["profanity"] = speech.StrongIndicators > 0 ? "STRONG" : speech.MildIndicators > 0 ? "MILD" : "NONE";
                row["maturity"] = scope.Style.MatureRequested ? "MATURE" : "GENERAL";
                if (scope.Mode == "INITIATIVE") row["requiredEvent"] = scope.RequiredEvent;
            }
            records.Add(row);
        }
        var response = new
        {
            header = new { schema = "pw-semantic-batch/1", category = category.Key, mode = scope.Mode, act = category.Act, language = "ru", count = scope.Count },
            records
        };
        return V4CorpusGeneration.ToJsonl(JsonSerializer.Serialize(response, PromptProfiles.CompactJson), scope, Registry);
    }

    internal static IReadOnlyList<CorpusQualityIssue> SceneIssues(OpenAiCampaignLane lane, V4GenerationRequest scope, IReadOnlyList<V4Entry> entries)
    {
        var scene = lane.Register == null ? FindScene(lane, scope.Style.MatureRequested) : EditorialScene(lane, scope.Style.MatureRequested);
        var contradiction = scene?.Contradiction ?? EventContradictions.GetValueOrDefault(lane.Event);
        if (contradiction == null) return [];
        return entries.Select((entry, index) => (entry, index))
            .Where(row => Regex.IsMatch(row.entry.Text, contradiction, RegexOptions.IgnoreCase | RegexOptions.CultureInvariant))
            .Select(row => new CorpusQualityIssue("SCENE_FACT_CONTRADICTION", row.index + 1,
                "Возможная подмена роли, события или факта закреплённой сцены. Проверьте полный контекст."))
            .ToArray();
    }

    private static object StrictSchema(V4GenerationRequest scope)
    {
        var schema = JsonSerializer.SerializeToNode(V4CorpusGeneration.ResponseSchema(scope, Registry), PromptProfiles.CompactJson)!;
        var recordFields = schema["properties"]!["records"]!["items"]!["properties"]!.AsObject();
        recordFields.Remove("tags");
        recordFields.Remove("weight");
        Normalize(schema);
        return schema;

        static void Normalize(JsonNode node)
        {
            if (node is not JsonObject obj) return;
            foreach (var keyword in new[] { "minLength", "maxLength", "minItems", "maxItems", "minimum", "maximum", "pattern" })
                obj.Remove(keyword);
            if (obj["properties"] is JsonObject fields)
            {
                obj["required"] = JsonSerializer.SerializeToNode(fields.Select(field => field.Key).ToArray());
                foreach (var field in fields) Normalize(field.Value!);
            }
            if (obj["items"] is JsonObject items) Normalize(items);
        }
    }

    private static void ValidateReplica(string line, V4GenerationRequest scope)
    {
        V4Rules.SafeText(line);
        V4Rules.Require(line == line.Trim() && !Wrapper.IsMatch(line) && !line.Contains('{')
            && line.Any(letter => letter is >= 'а' and <= 'я' or >= 'А' and <= 'Я' or 'ё' or 'Ё'), "OPENAI_REPLICA_FORMAT");
        V4Rules.Require(!PromptLeak.IsMatch(line), "OPENAI_REPLICA_PROMPT_LEAK");
        V4Rules.Require(scope.Mode == "TRIGGER" || V4Rules.OutboundLength(line), "OUTBOUND_MAX_100");
        var speech = CorpusQuality.MeasureSpeech([line]);
        V4Rules.Require(scope.Style.StrongProfanityRequested || speech.StrongIndicators + speech.MildIndicators == 0, "OPENAI_REPLICA_PROFANITY");
        V4Rules.Require(scope.Style.MatureRequested || speech.AdultIndicators == 0, "OPENAI_REPLICA_MATURITY");
    }

    private static SceneSpec? FindScene(OpenAiCampaignLane lane, bool mature)
    {
        if (lane.Id == MatureLaneId)
        {
            V4Rules.Require(mature && Matches(AdultScene) && !lane.Holdout && lane.Target is >= 1 and <= MaxMatureDrafts,
                "OPENAI_MATURE_SCENE_SCOPE");
            return AdultScene;
        }
        if (!lane.Id.StartsWith("pilot-", StringComparison.Ordinal)) return null;
        var scenes = mature ? Scenes.Where(scene => scene.Id != AdultScene.Id).Append(AdultScene) : Scenes;
        var scene = scenes.SingleOrDefault(scene => lane.Id == PilotId(scene, lane.Format));
        V4Rules.Require(scene != null && Matches(scene) && scene.Holdout == lane.Holdout, "OPENAI_PILOT_SCENE_SCOPE");
        return scene;

        bool Matches(SceneSpec expected) => expected.Category == lane.Category && expected.Mode == lane.Mode && expected.Event == lane.Event
            && expected.Voice == lane.Voice && expected.Facts == lane.Scene;
    }

    private static string CampaignSituation(OpenAiCampaignLane lane, int logicalBatch)
    {
        if (lane.Scene.Length > 0) return lane.Scene;
        if (lane.Mode == "INITIATIVE") return EventFacts[lane.Event];
        var angle = SceneAngles[logicalBatch % SceneAngles.Length];
        return lane.Mode == "TRIGGER"
            ? $"Игрок первым пишет о теме «{lane.Label}». Его смысл должен распознаваться из самой реплики без переписки. {angle}"
            : $"Игрок только что заговорил с фантомом на тему «{lane.Label}». Это обычный игровой чат. {angle}";
    }

    private static (string Preset, string Relation, string Channel, string? Stance) CampaignStyle(
        OpenAiCampaignLane lane, bool trigger, bool roughVoice)
    {
        if (trigger) return ("ordinary", "UNKNOWN", "ALL", null);
        return lane.Category switch
        {
            "lineage.pvp.trash_talk" when roughVoice => ("pvp_trash", "RIVAL", "TELL", null),
            "social.banter.rough" when roughVoice => ("rough_humor", "FAMILIAR", "TELL", null),
            "social.flirt.light" or "social.flirt.bold" or "life.love.flirting" => ("light_flirt", "FAMILIAR", "TELL", "FLIRT"),
            "social.flirt.rejection" => ("light_flirt", "FAMILIAR", "TELL", "REFUSE"),
            "social.friendship.support" => ("ordinary", "FAMILIAR", "PARTY", "SUPPORTIVE"),
            _ => ("ordinary", "UNKNOWN", "ALL", null)
        };
    }

    private static string VoiceFor(OpenAiCampaignLane lane, int logicalBatch)
    {
        var voices = Registry.Voices;
        var offset = V4Rules.Utf8.GetBytes(lane.Id).Sum(character => (int)character) % voices.Count;
        return voices[(offset + logicalBatch % voices.Count) % voices.Count].Key;
    }

    private static int Share(int total, int parts, int index) => total / parts + (index < total % parts ? 1 : 0);
    private static string PilotId(SceneSpec scene, string format) => "pilot-" + scene.Id + "-" + format.ToLowerInvariant();
    private static string Hash<T>(T value) => V4Rules.Hash(JsonSerializer.SerializeToUtf8Bytes(value, PromptProfiles.CompactJson));
}
