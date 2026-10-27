using System.Text;

namespace PhantomSemanticStudio.Core;

public static class V4Passport
{
    public static string Create(V4Registry registry)
    {
        var b = new StringBuilder("# Паспорт независимой генерации Phantom World v4\n\n");
        b.Append("Контракт `pw-semantic-batch/1`. Этот файл самодостаточен: прикрепите только его в новом чате ChatGPT. Версия реестра: `")
            .Append(registry.Hash).Append("`. Паспорт создаётся из того же машинного реестра и допустимых значений, что проверяет импортёр.\n\n");
        b.Append("## Инструкция для нового ChatGPT\n\n")
            .Append("По простому русскому запросу пользователя выбери одну точную leaf-категорию из таблицы ниже и нужный режим. Например: «200 смешных ответов про завышенные цены на эпик» → `hybrid.trade.absurd_offers`, REPLY. «200 фраз игрока про дорогой эпик, которые надо понимать» → та же категория, TRIGGER. «20 благодарностей за полученную помощь» → `social.friendship.support`, INITIATIVE, событие `pvp.help.received`. Если подходящих категорий несколько, уточни смысл. Если категории нет, верни предложение новой категории для отдельного review; не объявляй его готовым импортируемым файлом.\n\n")
            .Append("Создай настоящий скачиваемый файл `.jsonl`: строгий UTF-8 без BOM, переводы строк LF, завершающий LF. Один HEADER и ровно запрошенное число отдельных JSON объектов. Не возвращай только fenced Markdown, не добавляй комментарии, пустые строки после HEADER, объяснения или ID внутрь файла. Если чат не умеет создавать вложения, прямо сообщи об этом ограничении; не выдавай текстовый блок за созданный файл. До передачи проверь существование файла, JSON каждой строки, count, отсутствие дублей и разнообразие. Итоговый файл всё равно проверит локальный импортёр, гарантию за него не выдумывай.\n\n")
            .Append("Один файл — одна категория и один режим. HEADER содержит ровно `schema,category,mode,act,language,count`. schema=`pw-semantic-batch/1`, language=`ru`, act бери из таблицы категории для любого режима. count — целое 1..10000 (обычно 100..300), равно числу записей. Не придумывай серийные номера, act, intent, голоса или события.\n\n")
            .Append("## Режимы и поля\n\n")
            .Append("TRIGGER — входящая фраза реального игрока, которую следует распознавать; не произносится фантомом. Обязательны `text,intent`; intent бери из таблицы категории. Допустимы только дополнительные `tags,ambiguity`. ambiguity: ").Append(Join(V4Registry.Ambiguities)).Append("; по умолчанию NONE. Распознавание точное после NFKC, нижнего регистра и свёртки пробелов; это не языковая модель. Functional команды имеют приоритет.\n\n")
            .Append("REPLY — возможный исходящий ответ. Обязательны `text,stance`. Допустимы только дополнительные `tone,relations,voices,facets,profanity,maturity,tags,weight`.\n\n")
            .Append("INITIATIVE — исходящая фраза только по подтверждённому событию существующего владельца игры. Обязательны `text,stance,requiredEvent`; дополнительные поля как у REPLY. Только коды событий из таблицы ниже. Нельзя выдавать таймер, фразу игрока, выдуманную добычу или награду за событие. Одни REPLY не обучают пониманию входящих сообщений; для него нужен отдельный TRIGGER batch.\n\n")
            .Append("stance: ").Append(Join(V4Registry.Stances)).Append(".\n\n")
            .Append("tone: ").Append(Join(V4Registry.Tones)).Append("; по умолчанию CALM.\n\n")
            .Append("relations: массив из ").Append(Join(V4Registry.Relations)).Append("; по умолчанию [UNKNOWN].\n\n")
            .Append("voices: массив точных ключей из таблицы голосов либо [ANY]; ANY не смешивать с другими. По умолчанию [ANY]. Не создавай отдельную копию общего корпуса на каждый голос.\n\n")
            .Append("facets: объект с любым подмножеством ключей ").Append(Join(V4Registry.FacetKeys)).Append("; целые пороги 0..100. По умолчанию {} (без порога).\n\n")
            .Append("profanity: ").Append(Join(V4Registry.Profanities)).Append("; по умолчанию NONE. maturity: ").Append(Join(V4Registry.Maturities)).Append("; по умолчанию GENERAL. weight: целое 1..10, по умолчанию 1. tags: необязательный массив 1..16 разных ASCII ключей `[a-z][a-z0-9_.-]{0,63}`, например irony,bargain,refusal. Если тегов нет, поле опусти. relations/voices также 1..16 неповторяющихся элементов. Никаких null или лишних полей.\n\n")
            .Append("Включи в запрошенное число строк безопасный fallback для каждой исходящей комбинации category/mode/stance/requiredEvent и отношений: voices [ANY], без facets, profanity NONE, maturity GENERAL, tone CALM (либо DRY/WARM/SHY/FORMAL/DIRECT). Обычный спокойный ответ уже подходит; один fallback может перечислять все допустимые relations. Без него файл можно принять для редактирования, но build целого пака будет отклонён: редкий подходящий голос или контекст не должен теряться в большом корпусе. Не повышай запрошенный count ради fallback.\n\n")
            .Append("Runtime выполняет ограниченный индексированный подбор, учитывает недавние ответы и может промолчать, если безопасные неповторённые варианты исчерпаны. Это не обещание полного перебора всех потенциально подходящих фраз.\n\n")
            .Append("## Текст и безопасность\n\n")
            .Append("Пиши естественно по-русски, прямой читаемой кириллицей. Допускаются русские/украинские и латинские буквы, обычная пунктуация, ASCII цифры. TRIGGER: 1..1000 UTF-16 символов, только буквальный входящий текст, без placeholders. REPLY/INITIATIVE: не более 100 Unicode codepoints и 240 UTF-8 bytes после подстановки. Только в REPLY/INITIATIVE разрешён placeholder `{name}`; заложи до 64 символов и 192 bytes для имени. Проще писать без placeholders и нейтрально по полу. Нельзя TAB/CR/LF внутри text, управляющие/скрытые символы, испорченный Unicode, HTML/XML/скрипты, обратные кавычки и обратный слеш, URL, credentials или куски исходников. Строка JSON не может превышать 16 KiB, файл 8 MiB, глубина JSON 8. Дубли ключей JSON запрещены.\n\n")
            .Append("`{name}` означает подтверждённое имя самого фантома, не имя игрока-собеседника. Не используй эту подстановку как обращение к чужому персонажу.\n\n")
            .Append("Уникальность проверяется глобально после Unicode NFKC, нижнего регистра и свёртки пробелов. Внутри batch дубль отклоняет файл. Повторный импорт тех же данных даёт ноль новых записей; противоречивые метаданные для того же текста отклоняются. Не делай разнообразие простой нумерацией одинаковой фразы: варьируй речевую цель, лексику и ситуацию. Лексическая близость вызывает предупреждение для человека, не автоматическое утверждение о тождестве смысла.\n\n")
            .Append("Мат и грубый игровой юмор допустимы как размеченный художественный текст; intensity собственного голоса не означает терпимость к чужим оскорблениям. Сексуальный юмор требует MATURE; неизвестное согласие реального игрока не выдумывай. Угрозы в реальной жизни не одобряй и не усиливай: возможны SET_BOUNDARY, DISENGAGE, REFUSE. Шутка про покупку не совершает покупку, не меняет деньги, квесты или инвентарь. stance — намерение реплики, не прямое право менять social state. Не утверждай реальные имена/цены/предметы/места/факты, если их нет в подтверждённом контексте. Общие ответы должны иметь безопасный нейтральный вариант ANY/UNKNOWN/NONE/GENERAL.\n\n")
            .Append("## Пример готового файла\n\nНиже только учебное представление; пользователю передай настоящий файл.\n\n```jsonl\n")
            .Append("{\"schema\":\"pw-semantic-batch/1\",\"category\":\"hybrid.trade.absurd_offers\",\"mode\":\"REPLY\",\"act\":\"hybrid.trade.absurd_offers.reply\",\"language\":\"ru\",\"count\":1}\n")
            .Append("{\"text\":\"За такой лот ещё полгорода попроси.\",\"stance\":\"AMUSED\",\"tone\":\"PLAYFUL\",\"tags\":[\"irony\"]}\n```\n\n")
            .Append("## Реестр leaf-категорий\n\n").Append(CategoryTable(registry))
            .Append("\n## Голоса и независимые личные границы\n\nЧисла 0..100: терпимость к мату, личным оскорблениям, сексуальному юмору, стиль конфликта, порог доверия. Это независимые параметры, не разрешение использовать такую речь. Legacy: calm/open/direct/empathetic. Последний столбец содержит десять style facets.\n\n")
            .Append("| Ключ | Название | Legacy | Мат | Оскорбления | Пошлость | Конфликт | Доверие | Facets |\n|---|---|---|---:|---:|---:|---:|---:|---|\n");
        foreach (var v in registry.Voices) b.Append($"| {v.Key} | {v.Label} | {v.Legacy} | {v.ProfanityTolerance} | {v.InsultTolerance} | {v.SexualHumorTolerance} | {v.ConflictStyle} | {v.TrustThreshold} | {V4Rules.Facets(v.Facets)} |\n");
        b.Append("\n## Подтверждённые события\n\nИсточник: PhantomPvpSocialBridge.EventKind → штатная память Social owner; только недавнее непогашенное событие с evidence, а не текстовое обещание.\n\n| requiredEvent | Значение |\n|---|---|\n");
        foreach (var e in registry.Events) b.Append($"| {e.Code} | {e.Label} |\n");
        b.Append("\n## После генерации\n\nИмпортируй файл в offline Studio или CLI: проверить → просмотреть → принять в отдельный workspace → build → verify. Это создаёт immutable stage, не устанавливает его в сервер. Java content consumer проверяется отдельно; C# preview приблизителен. Для нового leaf оператор отдельно делает taxonomy propose → review хеша → approve → export-passport; исходники Java не нужны. Этот паспорт описывает доступный реестр, не обещает, что все категории уже имеют TRIGGER/REPLY/INITIATIVE coverage.\n");
        return b.ToString();
    }
    public static string CategoryIndex(V4Registry registry) => "# Категории Phantom World v4\n\nСгенерировано из машинного реестра `" + registry.Hash
        + "`. Добавление leaf проходит отдельный reviewed proposal. Зарегистрированная категория не означает заполненный корпус; проверяйте coverage трёх режимов отдельно.\n\n" + CategoryTable(registry);
    private static string CategoryTable(V4Registry registry)
    {
        var b = new StringBuilder("| Категория | Русское название | act для HEADER | intent для TRIGGER |\n|---|---|---|---|\n");
        foreach (var c in registry.Categories) b.Append($"| {c.Key} | {c.Label} | {c.Act} | {c.Intent} |\n"); return b.ToString();
    }
    private static string Join(IEnumerable<string> values) => string.Join(", ", values.Select(v => "`" + v + "`"));
}
