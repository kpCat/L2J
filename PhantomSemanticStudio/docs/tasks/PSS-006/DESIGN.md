# PSS-006 — архитектура и границы ответственности

## Почему задача крупнее прежних

Переходим к ТРЁМ большим задачам, но с независимыми внутренними gates, чтобы локализовать ошибки без перезапуска всего контекста:

- **PSS-006:** UX/no-freeze и LM diagnostics, крупный private chat.zip importer, индекс/поиск/фильтры/ручной preview.
- **PSS-007:** отдельная лаборатория разговоров — строгий pack-only режим, явное distinction «приближённая C# симуляция» vs Java-proven test-only oracle, context game/real/mixed/auto, локальная Gemma как ВНЕШНИЙ советник с уточняющими вопросами, управляемая память/обучающие сценарии, явное использование отобранных public corpus snippets для генерации DRAFT.
- **PSS-008:** отчёты о покрытии, рекомендации по пробелам, безопасный масштабируемый v3 segment proposal в scratch с Java content gates, регрессионные диалоги, ручной релизный handoff/rollback. **НЕТ** auto-install в L2J. Реальный runtime gender/persona gating может потребовать ОТДЕЛЬНОГО проекта/разрешения L2J; нельзя обещать реализовать его тремя Studio-only задачами.

Если безопасность или acceptance gate требует дополнительной задачи, это лучше, чем объединённый сломанный GREEN. PSS-007/PSS-008 task ZIP готовятся после публичного ревью предыдущего commit, чтобы required base был настоящим.

## Существующие компоненты: не дублировать

- `WorkspaceStore`: JSON настроек/сессии с 16 MiB/5000 candidates; **не** corpus database.
- `PackReader`: read-only humanized import, не Java parity.
- `SemanticDuplicateScout`: lexical bounded shortlist + stale evidence; PSS-005 bug risk: UI sync read/scout recursion.
- `LmStudioClient`: loopback-only strict JSON, one POST, no retry, no remote/tools/file access; `BAD_RESPONSE` не диагностирован до конкретной причины.
- `IsolatedPackStager`: XML в отдельном workspace; никакой write-to-L2J.
- `StageSelectionForm`: ручной выбор 1–20 approved для staging; не использовать его для chat corpus, где сообщения не approved.

## Data flow для PSS-006

```text
user-selected local chat.zip (untrusted, private)
  -> bounded ZipArchive stream (no general extraction)
  -> parse/skip private channels before persistence
  -> nick anonymization + sensitive-data warnings
  -> immutable message + language triage + normalized fingerprint
  -> isolated transactional corpus index under LocalAppData workspace
  -> paged read-only WinForms explorer
  -> explicit, small operator-curated sample selection (NOT a candidate)
```

Все изменения происходят в Studio workspace или собственных исходниках Studio. Не копировать `chat.zip` в Git root, не отправлять сырой текст в отчёты, модель, облако или L2J. Доступ к SQLite/индексу параметризован, input untrusted. Размер corpus не влияет на импорт существующего v3.

## Относительно чужих данных

Чаты могут включать посторонних людей, адреса, ссылки, ники и сообщения, не предназначенные для публичной републикации. Данные предназначены исключительно для личного локального анализа; на их основе генерировать оригинальные перефразированные предложения, не дословно публиковать длинные чужие беседы. Private channels не сохранять; channel names могут иметь варианты регистра. Кнопки импорта и поиска никогда не вызывают Gemma автоматически. Режим массового перевода миллиона строк **не входит** в PSS-006 (дорого, ошибка контекста, privacy): только идентификация и предварительный транслит-кандидат при явном запросе, оригинал неизменен.

## Архитектурные варианты индекса

Предпочтительно отдельная SQLite DB с индексами channel/date/type и параметризованными запросами, транзакционная запись и FTS только если проверено локально. Разрешено добавить **одну** закреплённую по точной версии зависимость `Microsoft.Data.Sqlite`, если среда позволяет воспроизводимый Restore без поломки 88 прежних тестов. Если dependency недоступна, BCL-only chunked file index допустим только с доказанными memory/cancel/page/atomicity tests. Кодекс фиксирует выбор в plan, не разносит корпус по `SessionState.Candidates` и не вводит гигантский in-memory List в WinForms.

Для локального DB owner: `CorpusStore` (private data/index lifecycle), `ChatCorpusImporter` (zip stream/limits/privacy), `CorpusQueryService` (page/filter/stats), `CorpusLanguageTriage` (conservative heuristics). Имена уточнить после read-first с минимальными изменениями existing owners. Сервис `CorpusImportCoordinator` может обеспечить single-flight import/cancellation, без timers/background automation.
