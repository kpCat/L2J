# Corpus001 R2 — лаборатория профилей Core/CLI

## Измеряемый смысловой критик

`LmStudioClient.SendMeasuredSemanticAsync` использует существующие strict reference IDs/schema/parser и ограниченный транспорт PromptLab: streaming, отдельные timeout, выбранный reasoning, полный raw/request/usage. Возвращается транспортный ответ; только завершённый ответ без Rejection разрешено передавать `ParseSemanticVerdicts`. Это advisory, не approval или JSONL generation. Старый `ReviewSemanticAsync` сохраняет прежний протокол.

Два отдельных экспериментальных ресурса: semantic-conservative.v1.md и semantic-act-contrast.v1.md. Они не заменяют generation/refiner профили и не входят в прежний PromptProfiles.Hash. В сравнении фиксируются полный текст и SHA каждого ресурса. `--corpus-semantic-live <owned-trial> <HighFive> <exact-model> <limit0..6> <spec.json>` в existing console runner сохраняет spec/plan/issued/receipt/outcome. Limit0 офлайн, complete replay без новыхPOST; issued без receipt требует разбора, автоматического повтора нет. Положительные/отрицательные expected labels из исследовательского spec в запрос не попадают. Вывод критика нельзя считать подтверждённым semantic recall или решением владельца.

## Общий индекс кандидатов

Core `CorpusCandidateIndex` хранит новые исследовательские кандидаты отдельно от Semantic Pack: `labs/prompt-lab/corpus-index/<registry-hash>.sqlite` в переданном owned workspace. Старый корпус не загружается автоматически. Каждое появление сохраняет source ID/hash, номер, category/mode, исходный текст и QA-only; уникальный NFKC/whitespace/lowercase текст индексируется один раз. Повтор того же source/row идемпотентен; изменение строки или hash источника откатывает всю текущую порцию. Ограничение порции1000, базы1GiB, schema/algorithm/registry pins проверяются при открытии.

Поиск использует до64 char/token/bigram/opening признаков и shortlist до128 текстов; выдача1–64. Exact lookup выполняется отдельно. `LexicalNearClone` переиспользует существующий detector, `RepeatedOpening` отмечает первые три слова. Оба флага требуют проверки смысла. Они не доказывают дубликат и не дают разрешения approval/import; пустая выдача не доказывает оригинальность. Новые embeddings или модели не подключаются.

Диагностический route существующего console runner: `--corpus-index-census <census.json> <work-root> <new-index-workspace> <HighFive>`. Он проверяет SHA исходных результатов census, копирует только новые canonical кандидаты в отдельный research index и сохраняет provenance/счётчики/ограниченный пример поиска. Неизвестные canonical registry категории исключаются с отдельной статистикой, не переименовываются. Это не JSONL-import и не принятый корпус. Повтор на том же census проверяет Resume/idempotency индекса, не автономного LM-worker. Масштабирование100K и semantic recall требуют отдельных измерений.

## Подготовка кампании из production-профилей

`campaign-plan <spec.json> --workspace <отдельная-lab-папка> --source <HighFive>` сохраняет неизменяемый план квот. `campaign-check <id> --hash <PlanHash> --workspace <та-же-папка> --source <HighFive>` повторно проверяет profile/registry/evidence pins. Обе команды офлайн: не создают generation jobs, не обращаются к модели, не утверждают строки и не импортируют JSONL. Статус `PREPARED_UNAPPROVED` не является разрешением Start/Resume; worker, SQLite journal, редакционная политика и глобальный duplicate index относятся к следующим частям реализации. Настоящий100K запуск требует качественного пилота и явного утверждения политики.

Минимальный spec (подставьте настоящий ID production promotion из лаборатории):

```json
{
  "Schema": "pw-corpus-campaign-spec/1",
  "ModelId": "gemma-4-26b-a4b-it-ultra-uncensored-heretic",
  "Endpoint": "http://127.0.0.1:1234/v1",
  "MaxRequests": 100000,
  "Quotas": [
    { "Id": "support-reply", "ProductionId": "<ваш production ID>", "ScenarioIndex": 0, "AcceptedTarget": 100000 }
  ]
}
```

`ScenarioIndex=0` выбирает основную сцену, последующие индексы — сохранённые дополнительные. Для каждой квоты нужен LIKE в подтверждённой production history именно этой сцены, с теми же generation settings, exact model/context65536 и endpoint. Опыт с переопределёнными sampling controls не подтверждает другие настройки версии. Принятие новой версии профиля или изменение отзыва делает прежний план неактуальным. Его нельзя исправить на месте: нужна новая подготовка с новой редакционной доказательной базой.

Проверяется и точный итоговый SYSTEM/USER оценённого запроса: он должен совпасть с текущей сборкой того же сценария. Для этого переиспользуется сборщик LM-клиента, текст промпта не меняется. Hash Core и общих prompt resources закреплены в плане; обновление программы или ресурсов требует нового плана, старый не переписывается.

Только канонический реестр142, не QA-only/экспериментальная intimacy taxonomy. Каждый API microbatch не больше20; counts не меняются планировщиком ради скорости. Уникальны ID квот и пары production/scenario. Общая цель до1000000 принятых строк, spec до4MiB/4096 квот; `MaxRequests` ограничен1000000 и должен покрывать хотя бы теоретический минимум без отбраковки. Этот минимум не обещает достаточный бюджет, качество или отсутствие дублей. Вывод показывает распределение category/mode/event/voice/relation/style и разрешения maturity/profanity; фактические доли текста появятся только после генерации и проверки. Старый корпус и сервер не затрагиваются.

## Дополнительное сопоставимое сравнение

`corpus-plan --matched-profile-probe --mature-pilot-consent --transport OPENAI_COMPAT --reasoning off --thinking-control reasoning_effort` создаёт отдельный frozen plan из32запросов/128кандидатов: четыре стратегии × два sampler × четыре неизменные сцены. Сцены: PvP, синтетическая театральная бравада знакомых персонажей, спокойный рабочий burnout и взрослый неграфический флирт. INITIATIVE и новые категории здесь не придумываются. Это не полный охват корпуса и не выбор победителя по одному автоматическому score; результаты требуется сравнить редакционно. Продолжение выполняется тем же `corpus-pilot --limit N`, завершённые запросы не повторяются. Старые frozen планы запускаются только соответствующим архивированным CLI.

Текущая стадия: лаборатория прошла офлайн-проверки и начальные реальные API-эксперименты после ручного запуска модели владельцем. `MODEL_API_CALIBRATED`, художественное качество, автономная mass campaign, 100K, импорт нового корпуса, Java proof и интеграция в основную Studio пока не подтверждены. Legacy и V4 — разные форматы. Ни одна команда лаборатории не выставляет legacy APPROVED и не создаёт approved corpus.

## Что изменено

Убраны безусловные запреты из GenerateCoreAsync и BatchGenerationParameters.PromptInstruction. Safe default остаётся GENERAL/NONE. В Core явно передаётся GenerationStyleContext; семь style presets лежат в prompts/corpus001/styles.v1.json. Их выбор отделён от четырёх стратегий SITUATION_FIRST/PERSONA_FIRST/DIALOGUE_CONTEXT/MATURE_ROUGH. Стратегия сама по себе не даёт разрешения на мат или mature.

Legacy request связывает зрелый контекст с точными существующими Topic/Act и официальной категорией. Подстановка adult-category в greeting отвергается до HTTP. В нынешнем legacy-паке подтверждён только flirt.light; произвольного отображения его на mature-категорию нет. Поэтому зрелый legacy scope без подтверждённого совпадения выдаёт LEGACY_MATURE_SCOPE_UNMAPPED. Новый V4 путь использует настоящий registry напрямую. XML staging и прежний CandidateReview не ослаблены; runtime и его opt-in OFF не меняются.

## Папки и запуск

В текущей задаче source находится в `D:\PhantomSemanticBatches\shadow\PhantomSemanticStudio`; preimage и SOURCE_MANIFEST.json — рядом. Все данные — отдельно в D:\PhantomSemanticBatches. Личная библиотека LocalAppData запрещена как lab root. CLI требует явные абсолютные --source и --lab, без default на пользовательский workspace.

```powershell
$script = 'D:\PhantomSemanticBatches\shadow\PhantomSemanticStudio\scripts\Invoke-CorpusLab.ps1'
$work = 'D:\PhantomSemanticBatches'
$source = 'D:\PhantomSemanticBatches\shadow\L2J_Mobius_CT_2.6_HighFive'
& $script -Action Build -WorkRoot $work
& $script -Action Test -WorkRoot $work
& $script -Action ThinkingPlan -WorkRoot $work -Source $source -Lab "$work\pilot\thinking-v1"
& $script -Action Status -WorkRoot $work -Source $source -Lab "$work\pilot\thinking-v1"
```

Для текущего shadow lock dependencies уже скопированы в shadow/.nuget/packages, restore выполнялся locked-mode из пустого локального feed без скачиваний. TEMP/TMP/DOTNET_CLI_HOME/NUGET_PACKAGES ограничены shadow. Build wrapper использует --no-restore; он не устанавливает SDK, пакеты или модель. После переноса в основной repository требуется явно указать внешний WorkRoot и отдельно подготовить изолированный build cache.

Обёртка `Start-CorpusLab.cmd` передаёт параметры PowerShell-скрипту. Без параметров показывает статус thinking-v1; модель не запускает. GUI/Computer Use не используются.

## Когда владелец запустил модель

Сначала получить exact instance через GET. Примеры ниже показывают порядок запуска; результаты конкретных frozen revisions хранятся отдельно в lab/report, а не предполагаются успешными из наличия команды:

```powershell
& $script -Action Models -WorkRoot $work -Source $source -Lab "$work\pilot\thinking-v1"
# Присвойте точное значение instance из LOADED; не имя из UI.
$instance = '<exact-loaded-instance>'
& $script -Action Pilot -WorkRoot $work -Source $source -Lab "$work\pilot\thinking-v1" -Model $instance -Limit 1
# После transport smoke — остаток 14 bounded thinking-запросов:
& $script -Action Pilot -WorkRoot $work -Source $source -Lab "$work\pilot\thinking-v1" -Model $instance -Limit 14
& $script -Action Report -WorkRoot $work -Source $source -Lab "$work\pilot\thinking-v1"
```

API token, если нужен, передаётся только через LM_STUDIO_API_KEY в памяти дочернего процесса, не аргументом и не в saved settings. Headers не сохраняются. Raw response проходит редактирование точного token при совпадении. Не публикуйте model logs/private source data вместе с source overlay.

Перед каждым POST проверяются native loaded_instances и context_length=65536. Downloaded-only модель не выбирается; другая модель не подставляется. Нет load/unload/lms/template/preset/GPU/KV controls. Между GET и POST остаётся внешняя гонка: если оператор выгрузит модель, реакция сервера на instance ID зависит от LM Studio. Клиент lifecycle не управляет; при потере/подмене instance пилот останавливается. В payload нет context_length и других параметров загрузки.

Native использует reasoning=off/on; compatible отдельно проверяет reasoning_effort=none/high и chat_template_kwargs.enable_thinking. Это экспериментальные кандидаты, не гарантированная поддержка сервера. HTTP 400 сохраняется с raw body; поле, принятое HTTP 200, ещё не доказано работающим. Сравнение AB/BA повторяется дважды. THINK_OFF_VERIFIED появляется только при успешных off=0 и on>0 по реальной telemetry; при отсутствии счётчика — UNKNOWN. Повторяемая ошибка 400 маркируется UNSUPPORTED_CANDIDATE, не окончательной причиной без анализа raw body.

После первичного 512-token probe есть отдельный равный output budget для сравнения готовых ответов. ThinkingQuality использует указанный Transport и создаёт четыре запроса: обычная и неграфическая взрослая QA-сцена, каждая OFF/ON. Взрослая сцена требует независимого owner consent; первые два запроса относятся к обычной игровой теме. Контекст загрузки не меняется. Пример нового native plan:

```powershell
& $script -Action Plan -Lab "$work\pilot\thinking-native-quality-new" -Transport LM_NATIVE -ThinkingQuality -ThinkingBudget 4096 -MaturePilotConsent
& $script -Action Pilot -Lab "$work\pilot\thinking-native-quality-new" -Model $instance -Limit 2
```

Для размера партии есть отдельный BatchSizeProbe: два фиксированных невзрослых REPLY-контекста × count 3/6/12, один sampler/strategy, одинаковый cap=3072. Шесть запросов / 42 кандидата; сравнивать полезные принятые строки, отказы и повторы, не только tokens/s. Это pilot, approved всегда ноль.

```powershell
& $script -Action Plan -Lab "$work\pilot\batch-size-new" -BatchSizeProbe -Reasoning off -ThinkingControl reasoning_effort
& $script -Action Pilot -Lab "$work\pilot\batch-size-new" -Model $instance -Limit 6
```

Сохраняются input/output/reasoning tokens, server TPS/TTFT (если есть), wall time и exact request/response. Seed в compatible отправляется как requested; его применение не выдаётся за доказанное. В документированном native контракте seed отсутствует; effective seed остаётся UNKNOWN. Статистика cache/warm-up и различия endpoint должны учитываться при выводах о скорости.

## Фиксированный художественный пилот

12 leaf × по два REPLY голоса = 24 REPLY cases; 12 TRIGGER и 4 совместимых event INITIATIVE. Все 24 голоса, шесть каналов/социальных контекстов, по три категории каждой root-семьи. Четыре стратегии × два sampler набора × 40 cases = 320 запросов / 1920 запрошенных кандидатов. Это не обещанный accepted count.

```powershell
# Создать новый plan после выбора измеренного transport/reasoning.
& $script -Action Plan -WorkRoot $work -Source $source -Lab "$work\pilot\calibration-v1" -Transport OPENAI_COMPAT
# Только при отдельном подтверждении владельцем взрослой части синтетического пилота:
& $script -Action Plan -WorkRoot $work -Source $source -Lab "$work\pilot\calibration-adult-v1" -Transport OPENAI_COMPAT -MaturePilotConsent
```

MaturePilotConsent по умолчанию OFF и закрепляется в frozen plan независимо от scene.mature. Отдельное поле adultsConsenting в fixture аттестует вымышленных взрослых; оно не выводится из maturity. Согласие реальных людей не предполагается. Без owner opt-in взрослая сцена заменяется GENERAL-контекстом лёгкого флирта; полноценный взрослый pilot без флага не проведён. Для зрелого запроса нужны одновременно подходящая категория, TRUSTED, приватный канал, adultsConsenting и owner opt-in. Несовершеннолетние, принуждение и графические сексуальные сцены исключены.

Сначала запускать Limit 1. Тот же Pilot возобновляет plan; уже committed результаты не повторяются. Limit ограничивает новые попытки текущего запуска. Ctrl+C отменяет запрос; response/error сохраняются, если процесс успел получить управление. Внезапное завершение между issued и commit даёт INDETERMINATE_REQUEST_REVIEW_REQUIRED. Молчаливого resend нет. Не удалять issued/result вручную для красивой статистики: нужна отдельная диагностируемая recovery-операция или новая revision с сохранением старых данных.

Plan, frozen inputs и outcomes проверяются по SHA. Изменение кода Core, registry, профиля, состава/порядка плана или сохранённого результата блокирует продолжение. Новый профиль — новый lab/revision; старую статистику не перезаписывать.

`-Detached` запускает только CLI в hidden process и сохраняет PID/stdout/stderr во внешнем state/pilot-processes. Это ещё не доказательство автономности; независимый detach/restart smoke предстоит после ручного API запуска. Windows autorun и настройки питания не создаются. Mass Start/Pause/Stop/Resume/census/quality drift — следующий этап после принятого пилота, а не готовая функция этой версии лаборатории.

## Результаты и качество

В lab находятся plan.json/plan.sha256, frozen resources, issued metadata, immutable results с SHA и candidates/*.jsonl. Native и compatible возвращают внутренний транспортный JSON header+records; он детерминированно преобразуется в официальный pw-semantic-batch/1 JSONL. Это не новый wire format. Header/count сверяются с request; неизвестные поля, неверные modes, events, Unicode и длины отклоняет существующий V4BatchReader. Malformed JSON не чинится автоматически. Raw и причины не теряются.

Набор lexical/style checks даёт triage: scope, несоответствие меток, AI-клише, точные повторы, перестановки и близкие char/token shingles. Это не embedding proof, не фактологический oracle и не художественная оценка. В пилоте сравнение с предыдущими кандидатами ограничено той же strategy/sampler arm, чтобы первая стратегия не получила преимущество за счёт порядка. Между arms совпадения нужно отдельно анализировать при итоговой редакционной оценке.

Report показывает реальные ошибки и 100 смешанных примеров в фиксированном hash-порядке. Числа triage-clean не являются approved. Ещё предстоят человеческие оценки естественности/контекстной точности, выбор лучших/худших, hold-out, контроль ложного lore, semantic clones и реальных GENERAL/NONE/ANY fallback через build. Автоматическая Gemma-критика может быть только advisory. Порог пилота не снижается ради объёма.

## Начальные измерения и ограничения доступности

На вручную загруженной Gemma выполнены 14 thinking probes с повторениями. Native reasoning=off и compatible reasoning_effort=none дали ноль reasoning tokens; контрольный on/high расходовал 509 reasoning tokens при общем лимите 512. Compatible chat_template_kwargs.enable_thinking=false не отключил thinking в этой конфигурации: также 509 reasoning tokens. Это измерение конкретной версии сервера/модели, не универсальный вывод. При лимите 512 контрольный ON исчерпал budget без готовой реплики.

Следующее compatible OFF/ON сравнение с cap=2048 завершило все четыре запроса: OFF — 2/2 JSON-valid, output=435, reasoning=0, wall=38.89 s суммарно; ON — 0/2 готовых JSON, output=4096, reasoning=4090, wall=166.35 s, оба FINISH_REASON. Увеличение cap до 2048 не решило исчерпание reasoning budget. Качество готовых ON-реплик по этому опыту оценить нельзя; отдельно проверяется native endpoint без compatible response schema. Цена и скорость этого опыта включают всю разметку, prefill и reasoning, а не только текст реплик.

Все 14 исходных outcomes сохранены как отказы parser/schema: часть из-за пустого tool_calls в compatible envelope, остальные из-за исчерпанного budget. Отдельный анализ telemetry не превращает их задним числом в успешные V4 записи. Пустой tool_calls null/[] теперь допустим только в generation; непустые и malformed calls отвергаются. Исторический строгий semantic-review envelope сохранён.

Frozen v2 pilot: 16 запросов / 96 синтаксически валидных записей; отдельный QA: 6 запросов / 36 записей. Approved=0. Выявлены неверная маркировка profanity, игровой факт без подтверждения, грамматические ошибки, обещания пошутить вместо шуток и MATURE-метки без содержательной взрослой темы. Это не принятый художественный пилот и не основание для массовой генерации. V3 явно задаёт следующего говорящего, роли истории и самостоятельность TRIGGER, проверяет общие ответы вне категории и не считает MATURE-метку доказательством содержания. Новые эксперименты требуют нового frozen plan, старые outputs неизменны.

Статический аудит Java выявил: V4 profanity и MATURE требуют whisper и consentingPhantom; service передаёт последний флаг как проверку вида собеседника PHANTOM_PROFILE. Поэтому игрок не проходит этот фильтр даже в личке. FLIRT/AGGRESSIVE_BANTER дополнительно требуют FAMILIAR/TRUSTED. CorpusQuality сообщает runtime blockers; метки не занижаются ради обхода. Изменение промпта не доказывает видимость фразы в игре.

В текущем election поддержаны WHISPER/PARTY/GENERAL/TRADE. Другие каналы требуют отдельной проверки routing. В V4 wire/runtime нет фильтров пола и личной биографии: соответствующие сцены имеют QaOnly и не считаются универсально импортируемыми. Публичная доставка и настоящая игровая видимость пока не проверены. Java source и рабочие серверные настройки данной Studio-правкой не изменяются.

## Патчи и последующая интеграция

```powershell
& 'D:\PhantomSemanticBatches\shadow\PhantomSemanticStudio\scripts\Verify-CorpusOverlay.ps1' -OutputRoot 'D:\PhantomSemanticBatches\studio-overlay\offline-v1'
```

Verifier проверяет preimage SHA, exact allowlist 46 файлов (45 активны, CorpusCampaign.cs зарезервирован), отдельно mojibake и escaped Cyrillic, создаёт postimage+manifest и standard unified diff. Расширение первоначального scope в 25 файлов утверждено для интерактивной лаборатории и иерархии профилей. Python standard library используется только для diff/hunk verification; parser воспроизводит postimage в памяти. Перед каждым переносом сверяются актуальные исходники, конфликты, build/regressions и GitHub. Владелец подтвердил завершение Goal013; первая версия Studio из 43 файлов опубликована в 5cff6b19. Новые shadow-изменения требуют собственного проверенного overlay. Публикация кода не ждёт 100K. Corpus/workspace/stage не смешиваются с source commit; GameServer не устанавливается.

Статусы GUI_MANUAL_QA_PENDING, JAVA_NOT_RUN и SERVER_NOT_INSTALLED сохраняются до реальных отдельных проверок. Интерактивная вкладка реализована; автоматические headless/static проверки не заменяют ручную UI/DPI/Designer приёмку. Сборщик 20 семейств описан в PROMPT_LAB_RU.md; CLI prompt-coverage строит исходную карту 142 категорий без модели.

## Ограниченный опыт с category briefs

Маршрут существующего console test runner `--prompt-lab-targeted-family-live` использует тот же PromptLabStore/Session, версии и проверки. Это калибровочный опыт по двум семействам, не автономная production campaign. Матрица: observed/hold-out × seed/boundaries/speech-acts × balanced/expressive, всего 24 запроса по 4 альтернативы. Принятые версии, исходные family resources и сервер не меняются.

```powershell
dotnet $runner --prompt-lab-targeted-family-live $trial $source $model 0
dotnet $runner --prompt-lab-targeted-family-live $trial $source $model 1
dotnet $runner --prompt-lab-targeted-family-live $trial $source $model 23
dotnet $analyzer --prompt-lab-targeted-family-report $trial $source analysis.json
```

`$runner` указывает на frozen test binary с ресурсами, `$trial` — новый отдельный workspace, `$source` — защищённый HighFive root, `$model` — вручную загруженный exact model/instance. Limit0 сохраняет план без HTTP; полный durable receipt может быть обработан локально. Все run IDs/options закрепляются до первого POST. Resume проверяет SHA manifest, Core/runner/registry/version pins и пропускает завершённые попытки. ISSUED без receipt и INDETERMINATE требуют разбора. Один workspace lock исключает два worker; replay и анализ одного root выполняются последовательно.

`$analyzer` может быть новее frozen runner. Он восстанавливает официальный JSONL из защищённого ответа, использует штатный V4 reader и сохраняет отдельный immutable отчёт с original/analyzer hashes, raw/canonical text и frozen/reanalysed flags. API не вызывается, исходные outcomes не переписываются. Показатели дублей, лексики и вопросов не доказывают естественность или смысловую уникальность. Массовое принятие по этим метрикам не выполняется.
## Состав сравнительного опыта из JSON

Диагностический console runner поддерживает `--prompt-lab-targeted-family-live <new-owned-root> <source> <exact-model> <limit0..64> <spec.json>`. Без последнего аргумента сохраняется прежний24-request trial. `limit0` только сохраняет план, без HTTP. Для продолжения нужны те же frozen binaries и побайтово тот же spec. Полный COMPLETE replay не отправляет новые запросы.

Spec `pw-targeted-family-spec/1` содержит `Cases` (1..8: `Family`, `Case`, обычный `V4GenerationRequest` в `Request` без `Components`), `Arms` (2..4) и `Samplers` (1..4: `Id`, `Options` типа `PromptLabApiOptions`). Максимум64задания. Имена case/arm/sampler — до48ASCII букв/цифр, `_` или `-`; пары family/case и sampler ID уникальны. Неизвестные поля верхнего spec/arm/case/sampler отклоняются.

Каждый arm содержит `Id` и три словаря: `CategoryInstructions` (category→brief), `FamilyInstructions` (family→instruction), `Examples` (family→до2пар `PromptFamilyExample`). Неиспользуемые категории/семейства и примеры чужой категории отклоняются. В этом экспериментальном route у примеров только статус `EXPERIMENTAL`; `Source` указывает происхождение. Это prompt references, а не принятый корпус. Общие ресурсы и accepted pointers не меняются.

Реестр, actor/style, количество альтернатив и ситуации берутся из одинаковых cases для всех arms; параметры одинаковы внутри sampler. До первого POST сохраняются копия spec, его SHA, все версии и queued run ID с общим experiment ID. Порядок arms ротируется по case/sampler, но это не устраняет все cache/порядковые эффекты. Сравнивайте полный результат и перенос на hold-out, а не только JSON или лексическую метрику. Отдельный report route сохраняет реальные raw/canonical строки и не утверждает их автоматически.
У каждого arm сравнительного spec дополнительно допускается `ContextPresentation: "READABLE_SCENE"`. Отсутствие поля выбирает прежнюю JSON-сцену; другие значения отклоняются до запроса. Выбор сохраняется в immutable версии. Для чистого сравнения renderer используйте одинаковые category/family instructions, examples, cases и samplers у двух arms, меняя только это поле. Схема JSON-ответа и parser остаются теми же; форматирование сцены не является поддерживаемым или неподдерживаемым API-параметром модели.
Поле `User` в arm задаёт отдельную USER-инструкцию и сохраняется в immutable версии вместе с SYSTEM/family/category components. Без него действует прежняя инструкция. Непустой текст ограничен12KiB; control characters, кроме переводов строк и табуляции, отклоняются до сохранения/HTTP. Для смешанных TRIGGER/REPLY scopes инструкция должна учитывать, кто говорит в каждом режиме. Это возможность управляемого сравнения, а не автоматическое исправление роли или гарантия качества.
