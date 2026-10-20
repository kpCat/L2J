# PSS-002 — направленный тест-план

Расширить существующий `tests/PhantomSemanticStudio.Tests/Program.cs` в том же console runner; НЕ заменять runner и не добавлять NuGet-пакеты ради тестов. Сохранять красный тест/поправку (red/green evidence) для реальных дефектов. Именовать тесты так, чтобы можно было понять из отчёта предмет проверки.

## Подключение LM / ошибки

- GET models 200 с ожидаемым id; list empty/mismatch; downloaded/JIT id не считается доказательством inference.
- GET connection refused -> actionable `SERVER_OFFLINE`; HTTP 401/403 -> auth, HTTP 404 -> endpoint path; 500 -> сервисная ошибка, не auto retry.
- Malformed JSON, duplicate keys (если проверяется JSON models), >1MiB response -> fail-closed, без выдачи модели как проверенной.
- Loopback allowed `localhost`, `127.0.0.1`, `[::1]` если корректно; blocked credentials/hostname-tricks/query/redirect/proxy-to-remote.
- Timeout/cancel: токен уважен; 0 hidden retry, 0 скрытых смен model ID, токен не отображается в diagnostic.

## Выбор типа и генерация

- TEMPLATE_ONLY: request schema ограничивает enum до TEMPLATE; valid TEMPLATE passes; PATTERN reply rejected *locally*.
- PATTERN_ONLY: аналогично, TEMPLATE rejected.
- MIXED: оба допустимы; `Legacy GenerationRequest` без type сохраняет прежнее поведение; lesson scope не сбрасывается.
- `json_schema` без tool/function calls; завершение `finish_reason=stop`, строго choices[0].message.content string; любой tools/multi-choice/length/refusal invalid.
- Разрешён только JSON с точным top-level `items` и `kind,text,reason` на item: без duplicate/unknown keys/fenced/empty/truncated и Unicode mojibake/escaped Cyrillic.
- Вся batch валидируется перед side effects; результат 1–20, без частичного сохранения; sample 2 items with duplicate text must be marked by CandidateValidator and remain DRAFT, а не быть принятым.
- Ошибка ответа после уже существующего session: saved candidates, lessons и approval остаются побайтово или семантически неизменны; exporter всё ещё review-only.

## UI regression

- Designer показывает шесть вкладок и добавленный type selector; повторное сохранение в Designer не удаляет event handlers.
- CandidateSelection via keyboard: unsaved edit, Save/Discard/Cancel restore correct selected candidate and text; tab switch/close cannot silently lose changes.
- Import/preview/teach/apply without actual LM; generated drafts via fake handler or manually staged fixtures; no changes to real local workspace.
- Inspect shrink/scale at 100% and 150% if possible; automated bounds useful but not sufficient as visual DPI proof. Screenshots not required if capture unavailable, document exact blocker.

## Source/ZIP/Git

- Negative path guards still reject workspace inside L2J, symlink/junction source, traversal manifest.
- Source drift between import and export blocks review ZIP; approved candidate edit resets hash. XML remains absent.
- No writes in L2J: evidence per-file SHA before/after.
- Dry fake tests consume no paid/remote APIs. Real live max 1 GET + 2 POST only if API already ready.

**Отчёт**: абсолютные команды, exit codes, exact PASS/FAIL, screenshots/limitations, count of changed files, hash/PUSH. Ничего не отмечать PASS по одному чтению source.
