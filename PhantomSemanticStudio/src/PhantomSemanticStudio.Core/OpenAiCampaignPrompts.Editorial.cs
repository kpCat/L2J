using System.Text.Json;
using System.Text.Json.Serialization;
using System.Text.RegularExpressions;

namespace PhantomSemanticStudio.Core;

internal static partial class OpenAiCampaignPrompts
{
    internal const int EditorialVersion = 3;
    internal const string General = "GENERAL", Strong = "STRONG", Mature = "MATURE";
    private const string EditorialRevision = "pw-openai-campaign-prompts/3";

    [JsonUnmappedMemberHandling(JsonUnmappedMemberHandling.Disallow)]
    private sealed record MatureRule(int Percent, string[] Styles);

    [JsonUnmappedMemberHandling(JsonUnmappedMemberHandling.Disallow)]
    private sealed record EditorialPolicy(string Schema, string Direction, string Facts, string General, string Mature, string Strong,
        Dictionary<string, string> Directions, Dictionary<string, int> StrongFamilies, Dictionary<string, MatureRule> MatureCategories,
        int StrongVoiceMinimum, int MatureVoiceMinimum);

    private static readonly Lazy<(EditorialPolicy Policy, string Hash)> EditorialResource = new(LoadEditorial);
    private static EditorialPolicy Editorial => EditorialResource.Value.Policy;
    private static readonly Regex PromptLeak = new(
        @"\b(?:nextspeaker|profanitylimit|generationstylecontext|system prompt|системный (?:промпт|инструктаж)|заданной схеме)\b",
        RegexOptions.IgnoreCase | RegexOptions.CultureInvariant);

    private static readonly SceneSpec[] ComparisonScenes =
    [
        new("pvp-holdout", "lineage.pvp.trash_talk", "REPLY", "", "bold_rival", "RIVAL", "TELL", "pvp_trash", "AMUSED", "ROUGH",
            "Фантом выиграл дуэль. Игрок проиграл и говорит, что сетка решила исход. Никто не звал помощь, реванша ещё не было.",
            ["Игрок: Без сетки ты бы эту дуэль не вывез."], true,
            @"\b(?:ты меня (?:убил|добил)|я слил дуэль|ты выиграл дуэль|я проиграл)\b"),
        new("trade-holdout", "lineage.commerce.bargaining", "REPLY", "", "skeptical_realist", "UNKNOWN", "TELL", "ordinary", "NEUTRAL", "DRY",
            "Фантом продаёт кристаллы. Игрок просит скидку за всю пачку. Фантом ещё не согласился. Деньги и товар не передавались, цена не названа.",
            ["Игрок: Всю пачку возьму. Скинешь за опт?"], true,
            @"\b(?:я у тебя купил|деньги (?:уже )?получил|я (?:уже )?продал|ты (?:уже )?купил)\b"),
        new("ordinary", "social.banter.rough", "REPLY", "", "rough_joker", "FAMILIAR", "TELL", "ordinary", "AMUSED", "PLAYFUL",
            "Игрок перепутал банки и посыпал свою кашу солью вместо сахара. Фантом только прочитал об этом. Они дружески подкалывают друг друга.",
            ["Игрок: Сахар кончился. Зато теперь знаю, в какой банке соль."],
            Contradiction: @"\b(?:я посолил|я сыпанул|моя каша|я тебе посолил)\b"),
        new("flirt", "social.flirt.bold", "REPLY", "", "playful_tease", "TRUSTED", "TELL", "light_flirt", "FLIRT", "PLAYFUL",
            "Двое знакомых вымышленных взрослых охотно флиртуют. Игрок не вышел из чата и намекнул, что ждёт фантома. Встречи и обещания свидания не было.",
            ["Игрок: Я вообще выходить собирался. Но ты появился, так что мешай дальше."]),
        new("intimacy", "life.love.dating", "REPLY", "", "romantic_dreamer", "TRUSTED", "BOT_PRIVATE", "double_meanings", "CURIOUS", "WARM",
            "Двое вымышленных совершеннолетних согласились поговорить наедине о близости и темпе сближения. Адресат описал своё предпочтение, у фантома ещё есть выбор ответить. Биография, пол и состоявшаяся близость не заданы.",
            ["Игрок: Мне нравится, когда не торопятся и сначала спрашивают. А тебе как комфортнее?"], true, Adult: true),
        new("party", "lineage.party.mistakes", "REPLY", "", "loyal_companion", "FAMILIAR", "PARTY", "ordinary", "NEUTRAL", "DIRECT",
            "Игрок случайно подтянул второй пак мобов и уже остановился. Фантом рядом; кто лидер, неизвестно. Хил просит не расходиться. Никто ещё не упал.",
            ["Игрок: Второй пак мой косяк. Хил сказал кучно стоять, я на месте."],
            Contradiction: @"\b(?:я (?:случайно )?заагрил|хил (?:умер|упал)|я пати[- ]лидер)\b")
    ];

    private static readonly Lazy<string> EditorialHash = new(() =>
    {
        var routes = EditorialLanes(100_000, null, true);
        var previews = routes.GroupBy(lane => (lane.Family, lane.Mode, lane.Register, lane.AdultStyle))
            .Select(group => group.First()).Concat(EditorialPilotLanes(true))
            .Select(lane => PrepareEditorial(lane, 1, 1, OpenAiTariff.SuggestedModel, true).Post.Hash)
            .Concat(EditorialPilotLanes(false).Select(lane => PrepareEditorial(lane, 1, 1, OpenAiTariff.SuggestedModel, false).Post.Hash))
            .ToArray();
        return Hash(new
        {
            EditorialRevision, Resource = EditorialResource.Value.Hash, LegacyVersionHash, routes, previews, ComparisonScenes,
            VoiceDirections = Registry.Voices.Select(voice => VoiceDirection(voice, true)).ToArray(), PromptLeak = PromptLeak.ToString()
        });
    });

    internal static string VersionHash => EditorialHash.Value;

    private static (EditorialPolicy, string) LoadEditorial()
    {
        var bytes = V4Rules.ReadBytes(PathSafety.ResolveRelative(Profiles.DirectoryPath, "openai-editorial.v3.json"), 32 * 1024);
        var policy = JsonSerializer.Deserialize<EditorialPolicy>(bytes, ProfilesJson()) ?? throw new InvalidDataException("OPENAI_EDITORIAL_CONFIG");
        bool Family(string id) => Hierarchy.Families.Any(family => family.Id == id);
        V4Rules.Require(policy.Schema == "pw-openai-editorial/3" && policy.StrongVoiceMinimum is >= 50 and <= 90
            && policy.MatureVoiceMinimum is >= 30 and <= 60
            && new[] { policy.Direction, policy.Facts, policy.General, policy.Strong, policy.Mature }.All(item => item is { Length: > 0 and <= 1600 })
            && policy.Directions.All(rule => Family(rule.Key) && rule.Value is { Length: > 0 and <= 1000 })
            && policy.StrongFamilies.All(rule => Family(rule.Key) && rule.Value is >= 1 and <= 40)
            && policy.MatureCategories.All(rule => Registry.Categories.Any(category => category.Key == rule.Key)
                && rule.Value.Percent is >= 1 and <= 30 && rule.Value.Styles is { Length: > 0 and <= 5 }
                && rule.Value.Styles.All(id => Profiles.Styles.Any(preset => preset.Id == id && preset.AllowsMature))),
            "OPENAI_EDITORIAL_CONFIG");
        // The same checked-in policy must keep its identity after Git converts line endings.
        var canonicalText = V4Rules.Utf8.GetString(bytes).Replace("\r\n", "\n", StringComparison.Ordinal);
        return (policy, V4Rules.Hash(V4Rules.Utf8.GetBytes(canonicalText)));

        static JsonSerializerOptions ProfilesJson() => new(PromptProfiles.ConfigJson) { AllowDuplicateProperties = false };
    }

    internal static IReadOnlyList<OpenAiCampaignLane> EditorialLanes(int total, IReadOnlyDictionary<string, int>? categoryTargets, bool mature)
    {
        var result = new List<OpenAiCampaignLane>();
        foreach (var lane in PlanLanes(total, categoryTargets))
        {
            var rule = mature && lane.Mode == "REPLY" ? Editorial.MatureCategories.GetValueOrDefault(lane.Category) : null;
            var adult = rule == null ? 0 : lane.Target * rule.Percent / 100;
            var strong = lane.Mode != "TRIGGER" ? (lane.Target - adult) * Editorial.StrongFamilies.GetValueOrDefault(lane.Family) / 100 : 0;
            result.Add(lane with { Register = General, Target = lane.Target - strong - adult });
            if (strong != 0) result.Add(lane with { Id = lane.Id + ".strong", Register = Strong, Target = strong });
            if (adult > 0 && rule != null)
            {
                for (var i = 0; i < rule.Styles.Length; i++)
                {
                    var quota = Share(adult, rule.Styles.Length, i);
                    if (quota == 0) continue;
                    var style = rule.Styles[i];
                    result.Add(lane with { Id = lane.Id + "." + style + ".mature", Register = Mature, Target = quota, AdultStyle = style });
                }
            }
        }
        V4Rules.Require(result.Sum(lane => lane.Target) == total && result.All(lane => lane.Target > 0)
            && result.Select(lane => lane.Id).Distinct(StringComparer.Ordinal).Count() == result.Count, "OPENAI_LANE_QUOTAS");
        return result.AsReadOnly();
    }

    internal static IReadOnlyList<OpenAiCampaignLane> EditorialPilotLanes(bool mature)
    {
        var result = new List<OpenAiCampaignLane>();
        foreach (var scene in EditorialPilotScenes(mature))
        {
            var formats = result.Count % 4 == 0 ? new[] { "JSON", "TEXT" } : new[] { "TEXT", "JSON" };
            foreach (var format in formats) result.Add(EditorialLane(scene, format));
        }
        return result.AsReadOnly();
    }

    private static IEnumerable<SceneSpec> EditorialPilotScenes(bool mature) =>
        ComparisonScenes.Where(scene => !scene.Adult || mature)
            .Concat(Scenes.Where(scene => scene.Id is "own-mistake" or "pvp-cover" || !mature && scene.Id == "after-shift"));

    private static OpenAiCampaignLane EditorialLane(SceneSpec scene, string format)
    {
        var category = Registry.Categories.Single(item => item.Key == scene.Category);
        var register = scene.Adult ? Mature : scene.Id is "pvp-holdout" or "trade-holdout" or "ordinary" ? Strong : General;
        return new(PilotId(scene, format), category.Key, scene.Mode, scene.Event, category.Label, Hierarchy.FamilyFor(category.Key).Id,
            3, scene.Voice, scene.Facts, scene.Holdout, format, register, scene.Adult ? Editorial.MatureCategories[scene.Category].Styles[0] : null);
    }

    internal static IEnumerable<(OpenAiCampaignLane Lane, OpenAiPromptBatch Baseline, OpenAiPromptBatch Candidate)> EditorialComparison(string model)
    {
        foreach (var scene in ComparisonScenes)
        {
            var lane = EditorialLane(scene, "TEXT");
            yield return (lane, PrepareLegacy(lane with { Register = null, AdultStyle = null }, 3, 1, model, scene.Adult, scene),
                Prepare(lane, 3, 1, model, mature: true));
        }
    }

    private static SceneSpec? EditorialScene(OpenAiCampaignLane lane, bool mature)
    {
        if (!lane.Id.StartsWith("pilot-", StringComparison.Ordinal))
        {
            V4Rules.Require(lane.Scene.Length == 0 && lane.Voice.Length == 0 && !lane.Holdout, "OPENAI_EDITORIAL_LANE_SCOPE");
            return null;
        }
        var scene = EditorialPilotScenes(mature).SingleOrDefault(item => PilotId(item, lane.Format) == lane.Id);
        V4Rules.Require(scene != null && EditorialLane(scene, lane.Format) == lane, "OPENAI_PILOT_SCENE_SCOPE");
        return scene;
    }

    private static OpenAiPromptBatch PrepareEditorial(OpenAiCampaignLane lane, int count, int logicalBatch, string model, bool mature)
    {
        V4Rules.Require(count is >= 1 and <= 20 && count <= lane.Target && logicalBatch >= 0 && lane.Format is "JSON" or "TEXT"
            && lane.Register is General or Strong or Mature, "OPENAI_BATCH_SCOPE");
        var category = Registry.Categories.SingleOrDefault(item => item.Key == lane.Category) ?? throw new InvalidDataException("OPENAI_CATEGORY_SCOPE");
        var family = Hierarchy.FamilyFor(category.Key);
        V4Rules.Require(lane.Family == family.Id && lane.Label == category.Label, "OPENAI_CATEGORY_SCOPE");
        var scene = EditorialScene(lane, mature);
        var trigger = lane.Mode == "TRIGGER";
        var strong = lane.Register == Strong;
        var adult = lane.Register == Mature;
        Editorial.MatureCategories.TryGetValue(lane.Category, out var adultRule);
        V4Rules.Require((!trigger || !strong && !adult)
            && (!strong || Editorial.StrongFamilies.ContainsKey(family.Id))
            && (adult ? mature && lane.Mode == "REPLY" && adultRule != null && adultRule.Styles.Contains(lane.AdultStyle)
                && (scene == null || scene.Adult) : lane.AdultStyle == null),
            "OPENAI_EDITORIAL_PERMISSION");

        var voice = trigger ? "ANY" : scene?.Voice ?? (strong || adult ? EditorialVoice(lane, logicalBatch, adult) : VoiceFor(lane, logicalBatch));
        var voiceData = Registry.Voices.SingleOrDefault(item => item.Key == voice);
        V4Rules.Require((trigger || voiceData != null)
            && (!strong || voiceData!.ProfanityTolerance >= Editorial.StrongVoiceMinimum)
            && (!adult || voiceData!.SexualHumorTolerance >= Editorial.MatureVoiceMinimum), "OPENAI_EDITORIAL_VOICE_SCOPE");

        var (preset, relation, channel, stance) = CampaignStyle(lane, trigger, false);
        if (!trigger)
        {
            channel = family.Id switch
            {
                "pvp_conflict" => "TELL", "commerce_trade" => "TRADE", "party_coordination" => "PARTY",
                "clan_community" => "CLAN", "banter_humor" => "TELL", _ => channel
            };
            relation = family.Id switch
            {
                "pvp_conflict" => "RIVAL", "party_coordination" or "clan_community" or "banter_humor" => "FAMILIAR", _ => relation
            };
        }
        if (strong)
        {
            channel = "TELL";
            preset = family.Id switch
            {
                "pvp_conflict" => "pvp_trash",
                "commerce_trade" => "roleplay_conflict",
                "banter_humor" or "party_coordination" or "clan_community" => "rough_humor",
                _ => "strong_profanity"
            };
        }
        if (adult)
        {
            channel = "BOT_PRIVATE";
            relation = "TRUSTED";
            preset = lane.AdultStyle!;
        }
        var facts = scene?.Facts ?? CampaignSituation(lane, logicalBatch);
        string[] dialogue = scene?.Dialogue ?? [];
        var adultTrade = adult && category.Key == "lineage.commerce.bargaining";
        if (adult && scene == null)
            (facts, dialogue) = adultTrade ? PrivateTradeSituation(logicalBatch) : AdultSituation(preset, lane.Label, logicalBatch);
        channel = scene?.Channel ?? channel;
        relation = scene?.Relation ?? relation;
        V4Rules.Require(!strong || channel is "TELL" or "BOT_PRIVATE", "OPENAI_EDITORIAL_PRIVATE_PROFANITY");

        var scope = new V4GenerationRequest
        {
            Category = category.Key, Mode = lane.Mode, RequiredEvent = lane.Event, Count = count,
            Transport = "OPENAI_RESPONSES", MaxOutputTokens = 384 + count * 160,
            Strategy = adult ? "mature-rough" : "dialogue-context",
            DesiredStance = scene?.Stance is { Length: > 0 } sceneStance ? sceneStance : stance,
            DesiredTone = scene?.Tone is { Length: > 0 } sceneTone ? sceneTone : null,
            ProfanityLimit = strong ? Strong : "NONE",
            Style = new GenerationStyleContext
            {
                Category = category.Key, Voice = voice, Relation = relation, Channel = channel, StyleId = preset,
                Situation = facts, Dialogue = dialogue, StrongProfanityRequested = strong, MatureRequested = adult,
                OwnerMatureOptIn = adult && mature, AdultsConsenting = adult && (scene?.Adult ?? true)
            }
        };
        var components = Hierarchy.Freeze(scope, Profiles, Registry);
        scope = scope with { Components = components };
        var hierarchy = PromptHierarchy.ComposeComponents(components, includeExamples: false)
            .Replace(HierarchyTransport, "", StringComparison.Ordinal);
        var instructions = string.Join("\n\n", new[]
        {
            Editorial.Direction, Editorial.Facts, hierarchy, Editorial.Directions.GetValueOrDefault(family.Id, ""),
            voiceData == null ? "" : VoiceDirection(voiceData, strong),
            strong ? Editorial.Strong : "", adult ? Editorial.Mature : Editorial.General,
            adultTrade ? "Зацепись за цену, ценность вещи или выбор условия. Уместен наглый двусмысленный намёк или пошлая шутка об условиях. Оставь свободу отказа и два прочтения. Согласие на шутки не подтверждает согласие на близость, цену или сделку." : "",
            "До 100 кодовых точек Unicode и 240 байт UTF-8 в исходящем сообщении. Без имён говорящих, ремарок, URL и личных данных."
        }.Where(part => part.Length != 0));
        var input = JsonSerializer.Serialize(new
        {
            category = category.Key, category.Label, category.Act, category.Intent, scope.Mode, scope.RequiredEvent, scope.Count,
            NextSpeaker = trigger ? "PLAYER" : "PHANTOM", Voice = voice, Relation = relation, Channel = channel,
            scope.DesiredStance, scope.DesiredTone, scope.ProfanityLimit, Facts = facts, Dialogue = dialogue
        }, PromptProfiles.CompactJson);
        var schema = lane.Format == "JSON" ? StrictSchema(scope) : null;
        var post = OpenAiResponsesClient.Prepare(model, instructions + "\n\n" + (schema == null ? TextInstructions : StructuredInstructions),
            input, scope.MaxOutputTokens, schema);
        return new(scope, post, Hash(new { input, Instructions = instructions }));
    }

    private static string EditorialVoice(OpenAiCampaignLane lane, int batch, bool adult)
    {
        var voices = Registry.Voices.Where(voice => adult ? voice.SexualHumorTolerance >= Editorial.MatureVoiceMinimum
            : voice.ProfanityTolerance >= Editorial.StrongVoiceMinimum).ToArray();
        var offset = V4Rules.Utf8.GetBytes(lane.Id).Sum(character => (int)character) % voices.Length;
        return voices[(offset + batch % voices.Length) % voices.Length].Key;
    }

    private static string VoiceDirection(V4Voice voice, bool strong)
    {
        string Facet(string name, string high, string low) => voice.Facets[name] >= 60 ? high : voice.Facets[name] <= 30 ? low : "";
        return string.Join(" ", new[]
        {
            Facet("reserved", "Держи короткую дистанцию, не заполняй паузы.", "Можно ответить с ходу и чуть развернуть мысль."),
            Facet("timidity", "Сомневайся и оставляй собеседнику инициативу.", ""),
            Facet("swagger", "Дай слышать уверенность и самолюбие.", ""),
            Facet("aggression", "Допустим прямой выпад по игровому поступку.", "Не ищи ссоры без причины."),
            Facet("sarcasm", "Сарказм сухой, без пояснения интонации.", ""),
            Facet("humor", "Можно рискнуть неожиданным комическим поворотом.", ""),
            Facet("romance", "В уместной сцене покажи внимание личной деталью.", ""),
            Facet("cunning", "Дай собеседнику самому заметить ловушку в условии.", ""),
            Facet("gossip", "В общении зацепись за известный собеседнику контекст, не добавляй слухов.", ""),
            strong && voice.ProfanityTolerance >= 80 && voice.Facets["roughness"] >= 70
                ? "Резкость у этого голоса привычная и уверенная." : ""
        }.Where(part => part.Length != 0));
    }

    private static (string Facts, string[] Dialogue) AdultSituation(string preset, string label, int batch)
    {
        var input = preset switch
        {
            "intimate_conversation" => batch % 2 == 0 ? "Мне нравится сначала обсудить границы. Что для тебя важно?"
                : "Я немного смущаюсь, когда всё решают за меня. Тебе проще сказать прямо?",
            "adult_flirt" => batch % 2 == 0 ? "Ты так внимательно слушаешь. Это у нас ещё просто разговор?"
                : "Я тоже флиртую, если что. Просто посмотрим, кому первому станет неловко.",
            _ => batch % 2 == 0 ? "Ну а теперь попробуй повторить это так, чтобы я не услышал второй смысл."
                : "Вот ты сейчас нарочно оставляешь мне место для пошлой шутки?"
        };
        return ($"Двое вымышленных совершеннолетних в доверенном личном чате явно согласились обсудить тему «{label}» в этом ключе. "
            + "Пол, прошлый опыт, согласие на конкретное действие и совершившиеся интимные события не установлены.", ["Игрок: " + input]);
    }

    private static (string Facts, string[] Dialogue) PrivateTradeSituation(int batch) =>
        ("Фантом — продавец, игрок — покупатель. Они торгуются об игровой вещи в личном чате. "
            + "Оба вымышленные совершеннолетние и явно согласны на пошлые шутки. "
            + "Сделка ещё не заключена, вещи и деньги не передавались. Название вещи и цена неизвестны.",
            batch % 2 == 0 ? ["Игрок: Если заберу всё, чем удивишь? Можешь с подколом, у нас тут не аукцион."]
                : ["Игрок: Цену можно обсудить. Только не стесняйся, я сегодня и пошлый ответ выдержу."]);
}
