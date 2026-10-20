# PSS-005 — архитектура и доказательные границы

## Что измеряем, а что нет

`TextRules.Normalize`, триграммы и токены дают **лексическую близость**, а не семантическое равенство. Ответ Gemma даёт **модельное мнение** на **неполной выборке**. Даже если все показанные пары `DIFFERENT`, статус остаётся `COVERAGE_LIMITED / MODEL_ADVISORY_NOT_VERIFIED`; фраза «повторов нет» запрещена в UI и отчётах.

Существующие факты: `PackReader.Load` выдаёт `PackSnapshot` из v1/v2/v3/custom с `Entries`, `Fingerprint` и provenance `SourceFile:SourceLine`; он не равен Java validator. `CandidateValidator` уже отклоняет exact repeats и показывает lexical warnings, `CandidateReview` привязан к содержимому approval, `WorkspaceStore` сохраняет `SessionState` атомарно и ограничен 16 MiB. `LmStudioClient` безопасно работает с loopback HTTP/JSON Schema, а `MainForm` — с шестью Designer-вкладками. В PSS-004 `StageBatchSelection` и `IsolatedPackStager` не требуют смыслового отчёта и остаются без изменений.

## Предлагаемые компоненты (точные названия подстроить только если найдётся лучший существующий owner)

**Core — `SemanticDuplicateScout` (новый файл):**
- `SemanticShortlist Search(PackSnapshot snapshot, Candidate candidate, IReadOnlyList<Candidate> peers, int maxMatches = 12, CancellationToken cancellationToken = default)`; чистая функция, без API/IO.
- Типы `SemanticNeighbor` (stable RefKey, Text, Act, Topic, Band, Register, Kind, SourceFile, SourceLine, Score, IsPeer), `SemanticShortlist` (SourceFingerprint, CandidateFingerprint, PeersFingerprint, ShortlistFingerprint, TotalConsidered, ScopeConsidered, Matches, `COVERAGE_LIMITED`).
- Отбор только того же Kind, исключая самого кандидата и REJECTED peers; точные совпадения сохранять независимо от act. Ближайшие сортировать стабильным составным ключом: нормализованный exact first, сходство (триграммы/токены), контекст act/topic, RefKey Ordinal. Разные act не скрывать полностью. Отсутствие выдачи не значит отсутствие смысловых совпадений.
- Нельзя передавать model весь исходный корпус; `maxMatches` 1..12, общий `TotalConsidered` отражает всё просмотренное. Снимок reference ID должен различать `SOURCE|<Kind>|<ID>` и `PEER|<CandidateId>`; одноимённые records не сливать.
- Реальное число 5 310 PATTERN / 20 963 TEMPLATE — из исторического PSS-003 импорта; в PSS-005 считать по новому `snapshot`, не захардкодить.

**Core — `LmStudioClient` (доработка существующего безопасного HTTP-owner):**
- Добавить `ReviewSemanticAsync(StudioSettings settings, string apiKey, Candidate candidate, SemanticShortlist shortlist, CancellationToken token)`.
- Один `POST /v1/chat/completions` с точным `settings.ModelId`, `response_format.type=json_schema`, `stream=false`, `temperature` низкая и фиксированная для экспертизы (0.1–0.2, НЕ менять настройки пользователя). Все тексты source, peers и кандидата — недоверенные цитаты, а не инструкции.
- Strict JSON объект `verdicts`: точно один элемент на каждый RefKey из shortlist, в каждом только `refKey`, `relation`, `reason`. Статусы `SAME_MEANING`, `RELATED`, `DIFFERENT`, `UNSURE`. `reason` 1..240 символов; неизвестные IDs, лишние или пропущенные записи, duplicate JSON keys, JSON fence, unknown labels, tools/function_call, finish_reason != stop, отказ — fail whole analysis, не сохранять частично.
- Тот же localhost-only, no redirects/proxy, bounded 1 MiB response, cancel/timeout, sanitized diagnostics без server/error body. Можно минимально извлечь общий parser response envelope, но не переписывать общую генерацию и не ослаблять её тесты.

**Workspace — `Models.cs` / `WorkspaceStore.cs` / `CandidateValidator.cs`:**
- Добавить **необязательное** поле `Candidate.SemanticReview` (`null` для старых сессий). Evidence включает CandidateFingerprint (текст/scope), SourceFingerprint, PeersFingerprint, ShortlistFingerprint, exact RefKey/referencedTextHash/relation/reason, ModelId/UTC. Фиксированный верхний предел: <=12 verdicts, <=240 chars/reason, <=256 chars/modelId, ограниченный суммарный размер. Отдельных файлов с полными prompt/model body не сохранять.
- Сохранять через существующий `WorkspaceStore.SaveSession`, где проверять форму/bounds evidence перед сериализацией; старую `SessionState.Version=1` не ломать. `CandidateReview.Edit` явно сбрасывает evidence; прочие изменения могут сделать evidence stale без изменения approval. `CandidateReview.Fingerprint` **не менять**, иначе старые APPROVED станут недействительны.
- В `SemanticDuplicateScout.IsEvidenceCurrent` или отдельном helper пересчитать candidate/source/peers/shortlist SHA; **не** доверять дате модели или одному candidate ID. При изменении исходных 65 файлов/новых peers/edited text нельзя показывать старое evidence как действующее. Не дописывать `SEMANTIC_CHECKED` в validation issues, не менять `IsolatedPackStager`.

**WinForms — `MainForm.cs` / `MainForm.Designer.cs`:**
- Кнопки «Найти похожие (локально)» и «Оценить смысл (Gemma)» на вкладке кандидатов. Обычные WinForms Designer-authored Button + event handlers, сохранив существующую высоту `txtValidation`, размеры редактора, tab order и resize. Конструктор **только** `InitializeComponent()`; шесть вкладок сохраняются. Не создавать controls в runtime.
- Локальный поиск работает при отключённой LM Studio, выводит RefKey, исходный текст, kind/act/source и lexical score, `рассмотрено N; отправится модели M/ N; проверка смысла: НЕ ПРОВОДИЛАСЬ`.
- Второй обработчик делает тот же shortlist, один явный POST; перед записью evidence перечитывает source через `PackReader.Load` и пересчитывает snapshot/current candidate/peers; на mismatch или cancel не сохраняет ни evidence, ни approvals. Ошибка LM оставляет локальный shortlist на экране и сообщает `BLOCKED_LM`, не подменяет успешным verdict.
- Отображать статус **совета модели**, а не «дубликаты гарантированно удалены». `SAME_MEANING` — видимое предупреждение для человека, который может редактировать/отклонять кандидата обычными контролами, но **никаких автоматических действий**. При выборе кандидата показывать сохранённый актуальный отчёт или явный `STALE`.

## Время/лимиты

Одна локальная проверка за раз. На экран до 12 фраз; если import содержит тысячи — вычислять только для selected Candidate, не для всех 5 000. Не строить глобальный дорогой pairwise O(N²); не выполнять фоновый скан/автомодель по таймеру, не загружать вторую модель. Ограничить UI output и рассматривать cancel. Логи не содержат prompt, outputs или персональных сведений.

## Не включать в этот checkpoint

- Смысловые гарантии по всему корпусу, embedding индексы, онлайн/автопроверку каждого generated draft, auto-reject или автоматический `override`, новые act/topic, редакцию Java или server-ready XML.
- Автоматическое блокирование **старых** staging approval при отсутствии Gemma: модель может быть недоступна. Позже отдельная задача может ввести редакционное разрешение конкретных вероятных дублей; **PSS-005 только показывает и сохраняет advisory**.
