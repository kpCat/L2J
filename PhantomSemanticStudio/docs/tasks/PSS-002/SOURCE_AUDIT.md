# PSS-002 — independent read-only source audit для входа в задачу

Актуальный публичный источник: `https://github.com/kpCat/PhantomSemanticStudio/tree/main`, проверенный HEAD `87755c86421c9a320c7bc1f5f7682fb13c95332e` (2026-10-08). Два публичных commits: `2a4f85af06938dd1aa7a4d9ff04b6f0238fbabcd` (source + tests) и `87755c86421c9a320c7bc1f5f7682fb13c95332e` (checkpoint report). В ветке есть `reports/PSS-001-final.md`, `.sln`, core/UI source и 50-case runner. Эти факты — из GitHub; локально тесты заново здесь не исполнялись.

## Проверенные места

`src/PhantomSemanticStudio.Core/LmStudioClient.cs`:
- `ListModelsAsync` -> `GET models`; `GenerateAsync` -> `POST chat/completions` с `response_format=json_schema` и выбранным `settings.ModelId`.
- один bounded response и strict parser, `tool_calls`/`function_call` отвергаются; `HttpClient` без proxy/redirect, endpoint loopback-only.
- запрос возвращает **kind** из модели, но пользователь сейчас не может жёстко выбрать PATTERN vs TEMPLATE; точка PSS-002.

`src/PhantomSemanticStudio.WinForms/MainForm.cs`:
- конструктор только `InitializeComponent()`; `CheckLm_Click` сообщает найден/не найден ID, но общие exception через `RunAsync` недостаточно информативны.
- `Generate_Click` валидирует и сохраняет партию, затем выводит DRAFT; no auto-approve; `Teach_Click` и `ApplyLesson_Click` уже есть.
- `CandidateSelection_Changed` перешёл на `CurrentCellChanged`/deferred `BeginInvoke`; UI regression по PSS-001 отчёту **не проверен интерактивно**.

`src/PhantomSemanticStudio.Core/ReviewExporter.cs`: `REVIEW_ONLY_NOT_SERVER_VALIDATED`, JSON ZIP, `DO_NOT_INSTALL.txt`, no XML. Сохранить без регрессии.

`reports/PSS-001-final.md`: actual imported humanized 65 files, 5310 PATTERN, 20963 TEMPLATE, source fingerprint recorded; 50 PASS/0 FAIL по отчёту; connection refused 127.0.0.1:1234; Designer USER_VERIFIED, UI 100%/150% NOT_TESTED; independent acceptance pending. Counts могут измениться в живом L2J, не hardcode.

## Первичные ссылки

- https://github.com/kpCat/PhantomSemanticStudio/blob/main/reports/PSS-001-final.md
- https://github.com/kpCat/PhantomSemanticStudio/blob/main/src/PhantomSemanticStudio.Core/LmStudioClient.cs
- https://github.com/kpCat/PhantomSemanticStudio/blob/main/src/PhantomSemanticStudio.WinForms/MainForm.cs
- https://github.com/kpCat/PhantomSemanticStudio/blob/main/src/PhantomSemanticStudio.Core/ReviewExporter.cs
- https://lmstudio.ai/docs/developer/openai-compat/models
- https://lmstudio.ai/docs/developer/openai-compat/structured-output

Документация LM Studio: `/v1/models` может показывать скачанные модели при Just-in-Time загрузке; наличие ID не гарантирует, что модель уже загружена. `json_schema` доступен для `/v1/chat/completions`, но качество формата зависит и от модели. Не обещать support конкретной сборки Gemma без реального smoke.
