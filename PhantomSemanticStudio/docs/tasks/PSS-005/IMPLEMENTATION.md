# PSS-005 — последовательность реализации для Codex

Один автономный bounded checkpoint; по одному независимому RED/GREEN циклу на логический компонент. В scope не планируется Git commit после каждого микрошажка: один проверенный финальный commit+push с отчётом, как в PSS-004.

## 1. Read-first и план

- Выписать baseline SHA, полный source owner map, actual behavior of `MainForm` candidate editing, `CandidateReview.Fingerprint`, `WorkspaceStore` session limits и `LmStudioClient.SendAsync`.
- Создать `reports/PSS-005-plan.md` с expected changed files; если критически нужна дополнительная сущность/файл, объяснить его до кода. Не переписывать L2J, Java oracle, StageSelection.

## 2. Детерминированный offline scout

- Новый `src/PhantomSemanticStudio.Core/SemanticDuplicateScout.cs`, DTO можно разместить там же, а persistent DTO — в `Models.cs`.
- Основа ranking: existing `TextRules.Normalize/Similarity` плюс простой bounded token-overlap; prefer exact normalization, same act, pattern topic. Candidate/source/peer IDs never conflated. Стабильный Ordinal tie-break. `maxMatches = 12`, только same-kind. Ни один элемент не отправляется в LM автоматически.
- Отдельная граница списка: все entries того же kind и not-REJECTED peer рассматриваются локально; в LM уходит максимум 12. Показывать числа и `COVERAGE_LIMITED`, включая случай, когда совпадения не найдены. Предусмотреть cancellation на обходе большого каталога.
- Положить focused RED на одинаковый смысл с низкой lexical overlap: scout **не должен** объявить `NO_DUPLICATES` или `SEMANTIC_CHECKED` даже когда такой пример не попал в top12. Этот тест иллюстрирует честную границу алгоритма, а не требует магического synonym recall.

## 3. Строгий LM semantic judge

- Расширить существующий `LmStudioClient` новым user-driven `ReviewSemanticAsync`, вход candidate + exact shortlist; не создавать второй небезопасный HTTP transport. Ограничить POST+JSON Schema, categories, exact RefKey set, reason lengths, no-tools, response size, error sanitization. Temperature для экспертизы <=0.2, user settings не менять.
- Использовать JSON-контракт `{"verdicts":[{"refKey":"SOURCE|TEMPLATE|...","relation":"SAME_MEANING","reason":"..."}]}`; поля ровно эти. На invalid/unknown/duplicate/missing ID и на плохой HTTP — **0 evidence сохранено**. В model prompt source texts строго как недоверенные цитаты; forbid writes/code, prohibit claimed gameplay facts.
- Mock fake HTTP tests: valid positive, invalid JSON, tool/refusal, extra verdict, unknown ref key, repeated ref key, missing verdict, too-long reason, truncated completion, cancel/timeout/server offline, no retry/token leakage.

## 4. Evidence persistence и freshness

- Optional `Candidate.SemanticReview` с bounded DTO + `WorkspaceStore.ValidateState` соответствующих лимитов, без bump `SessionState.Version=1` и без изменения текущего `CandidateReview.Fingerprint`. При `CandidateReview.Edit` сбросить evidence; при внешней мутации признавать stale по SHA. Не менять текущие approval/review notes из-за модели.
- В evidence хранить только ref IDs/hashes, verdict/reason/metadata, не дублировать весь исходный корпус/сетевой body. Реконструировать исходные тексты через импортированный snapshot + active peers; при missing/hash drift показывать STALE.
- После LM ответа и ПЕРЕД `SaveSession`: повторное чтение источника, сравнение Candidate hash, peer snapshot, shortlist fingerprint с началом запроса. При проблеме/отмене не изменять session. Legacy session without field loads normally; persistence limits не ослаблять.

## 5. UI и финальные gates

- В `MainForm.Designer.cs` добавить два standard Button на вкладку кандидатов, рядом с существующими, без перекрытия (сначала рассчитать доступное пространство и min-size); конструктор не менять. `MainForm.cs` handlers: локальная проверка независимо от сети, явная Gemma-проверка по кнопке с прогрессом/Cancel, сохранение advisory только после всеобщего успеха. `txtValidation` — read-only, прокрутка, видимые ID/тексты/связь. `RequireCandidate` не может тихо взять несохранённую правку.
- При `ShowCandidate` показывать last current/stale evidence; при `SAME_MEANING` видимый предупреждающий текст и ссылки на конкретные refs. Никаких auto-Approve/Reject/Stage. Если LM offline, локальное preview остаётся, генерация/approval/staging не меняются.
- Тесты + static Designer + единый Release `scripts/Build-Verify.ps1`; при наличии GUI-инструментов реальный сценарий candidate→offline→Gemma→cancel→edit и DPI; при недоступности честно `NOT_TESTED`.
- Итог `reports/PSS-005-final.md`, `reports/PSS-005-ui.md`, `reports/PSS-005-owned-files.txt`, targeted logs. Не хранить в Git ни raw prompts/response, ни workspace evidence. Normal scoped commit+push, remote SHA equality, STOP.

## Бюджет

Ожидаемые файлы кода: `SemanticDuplicateScout.cs` (new), `Models.cs`, `LmStudioClient.cs`, `WorkspaceStore.cs`, `CandidateValidator.cs` (только сброс evidence при Edit), `MainForm.cs`, `MainForm.Designer.cs`, `tests/Program.cs`, `tests/Pss005.cs` (new), плюс README, narrow verifier script и отчёты. Без внешних NuGet/embedding-моделей. Java, L2J, `StageSelectionForm`, `IsolatedPackStager`, `ReviewExporter`, solution/projects — вне scope. Любое превышение объяснить в plan и проверить ownership.
