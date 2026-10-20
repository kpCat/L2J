# PSS-002 — проверяемые критерии приёмки

## Functional / safety (все обязательны)

1. `dotnet build PhantomSemanticStudio.sln -c Release --nologo` exit 0, **0 compiler errors/warnings** после всех исправлений.
2. `dotnet run --project tests/PhantomSemanticStudio.Tests -c Release --no-build` exit 0 и точный PASS/FAIL; regression PSS-001 **не ниже 50/0**, новые собственные тесты реально выполняются.
3. `scripts/Verify-Designer.ps1` exit 0; `MainForm` constructor только `InitializeComponent()`. Если есть VS Designer: открыть форму, сохранить, закрыть, переоткрыть, screenshot/описание — отдельно от static PASS.
4. Явный выбор type в UI меняет требуемый JSON Schema + локальную проверку: один TEMPLATE режим отклоняет PATTERN; один PATTERN режим отклоняет TEMPLATE; MIXED допускает оба. Defaut UI TEMPLATE, fallback старых `GenerationRequest` — MIXED; сохранённые scoped lessons не ломаются.
5. Положительный fake HTTP ответ создаёт 1–3 DraftItems; единая UI persistence транзакция создаёт DRAFT и **никогда APPROVED**. Некорректная/незавершённая/лишняя/дублированная структура, exception, timeout, cancellation, HTTP error дают **0 новых candidates и прежний session state**.
6. Ни API token, ни приватный prompt/response, ни credentials не записаны в settings/report/review zip/Git (не печатать даже обрезанный token). Redaction tests where relevant.
7. Проверки/экспорт исходников делают no-write в L2J. Для фактически использованных 65 humanized файлов или актуальной версии источника собрать полный SHA/bytes before/after; равенство всех фактически прочитанных source files.
8. `ReviewExporter` оставляет только `REVIEW_ONLY_NOT_SERVER_VALIDATED`, `DO_NOT_INSTALL.txt`, JSON. Ни XML patch, ни java readiness, ни запись в L2J. Проверить zip содержимое.
9. Проверить Git remote/HEAD, отсутствуют чужие staged changes; разрешён только `kpCat/PhantomSemanticStudio` main. Commit + обычный push без force; проверенный удалённый SHA совпадает с локальным HEAD. Если auth/network блокирует, честный `BLOCKED_PUSH`, не другой remote.
10. `mojibake` и escaped Cyrillic проверки в изменённых UTF-8 файлах — отдельно; `git diff --check` PASS.

## Диагностика и interaction

- Отдельные fake HTTP негативные cases: connection refused, timeout/cancel, 401/403, 404, HTTP 500, malformed/oversize response, missing model id, visible JIT model not proven loaded; no retry/no fallback.
- В UI должны быть понятные русские инструкции для operator: где включить сервер, как посмотреть model id. Проверка `/models` **не делает POST**.
- UI smoke: Settings/Import, Library filter and selection, Preview/Teach/ApplyLesson, explicit generation, Candidate editor with Save/Discard/Cancel, Review/Export; tests не должны менять реальный пользовательский workspace с кандидатами (изолировать smoke state).
- DPI: 100%/150% real visual evidence when available; otherwise record **NOT_TESTED** (explicit), no false GREEN. Keyboard navigation/resize/clipping/out-of-bounds evidence отдельно. Не менять системный DPI молча.

## Live LM — независимый статус

Если локальный API доступен и указанная модель отвечает, выполнить bounded `GET /v1/models` и не более двух осознанных `POST /v1/chat/completions` с 1–3 items. Точные model id / число/статусы / JSON pass указать без токена и приватного содержимого. Если сервер не запущен, **BLOCKED_LM**, это внешнее ограничение, не довод менять модель/автоматически поднимать сервер/лгать о реальной генерации.

## Итоговое состояние

Implementation `GREEN` только если все **локально выполнимые** gates реальны, UI/DPI недоступный инструмент отмечен отдельно; не выдавать внешние BLOCKED_LM или UI NOT_TESTED за PASS. Общий checkpoint `BLOCKED` если обязательные acceptance gates реально не закрыты. Independent acceptance = `PENDING_INDEPENDENT_REVIEW` до просмотра опубликованного commit.
