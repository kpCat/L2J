# PSS-002 — управляемая генерация через LM Studio и закрытие UI-гейтов

## Контекст и статус

Это самостоятельная задача для **kpCat/PhantomSemanticStudio** (C#/.NET 10, WinForms), НЕ для kpCat/L2J.
Рабочий путь: `C:\Users\ZBook\PhantomSemanticStudio\`.
Точная проверенная удалённая база на 2026-10-08: `origin/main` SHA **`87755c86421c9a320c7bc1f5f7682fb13c95332e`**.
Репозиторий: `https://github.com/kpCat/PhantomSemanticStudio.git`.
PSS-001: implementation **BLOCKED**, финальная сборка 0/0, консольные тесты 50 PASS/0 FAIL согласно отчёту, Designer USER_VERIFIED, LM Studio BLOCKED_LM (connection refused), полное UI/DPI NOT_TESTED. **Это данные отчёта, а не собственная повторная проверка задачей PSS-002.**

Модель Codex: основная кодовая модель; reasoning **High**. Работа в новом диалоге, без запуска следующей задачи, без повторного согласования. Окончание: commit+push в собственный `origin/main` либо честный BLOCKED с отчётом и безопасным commit+попыткой non-force push.

## Точный результат

После PSS-002 владелец программы сможет:
1. В настройках получить **понятный статус локального LM Studio**, проверить ровно выбранный model ID, исправить настройки вручную, а не гадать по сетевому исключению.
2. В конструкторе **явно выбрать тип генерации**: `Ответ (TEMPLATE)`, `Входная фраза (PATTERN)` либо `Смешанный (MIXED)`. Значение по умолчанию — TEMPLATE; пользователь сам запускает ограниченную партию. Поддержка существующих сохранённых замечаний/кандидатов обязательна.
3. Получить от Gemma строго JSON-кандидатов с темой/act/полом/отношением/регистром из запроса. Результаты сохраняются **только как DRAFT** в workspace, с причиной и диагностикой; ошибки/отмена/timeout/недопустимый JSON ничего не записывают.
4. Пройти доступные UI-маршруты WinForms без потери правок, в том числе выбор строки клавиатурой/смена вкладки и сохранение замечаний; иметь честные отдельные результаты 100%/150% DPI.
5. По-прежнему экспортировать **только REVIEW_ONLY JSON ZIP**: НИКАКОГО серверного XML, Java-ready статуса, изменения High Five или автоматической установки.

## Обязательное чтение перед кодом

- `AGENTS.md`, `README_RU.md`, `docs/DESIGN_RU.md`, `docs/SOURCE_AUDIT_RU.md`.
- `reports/PSS-001-final.md` и `reports/PSS-001-ui.md` (включая ограничения), `reports/PSS-001-owned-files.txt`.
- `docs/tasks/PSS-002/{SOURCE_AUDIT,ARCHITECTURE,ACCEPTANCE,TEST_CASES,SAFETY_AND_GIT}.md`.
- `src/PhantomSemanticStudio.Core/{LmStudioClient,Models,CandidateValidator,WorkspaceStore,ReviewExporter}.cs`, `src/PhantomSemanticStudio.WinForms/{MainForm,MainForm.Designer}.cs`, `tests/PhantomSemanticStudio.Tests/{Program,SourceSmoke}.cs`, `scripts/{Build-Verify,Verify-Designer,New-ReviewBundle}.ps1`.
- Сначала `git status --porcelain=v1 -uall`, `git rev-parse HEAD`, `git branch --show-current`, `git remote -v`, `git ls-remote origin refs/heads/main` (после безопасной конфигурации remote). Проверить границу собственного git root и required base, не перетирать user changes.

## Scope implementation (порядок)

### 1. PSS-001 read-first audit и защита от регрессий
- Сверить публично опубликованный exact baseline и реальные тесты, найти только затрагиваемые дефекты. Не доверять 50 PASS без чтения relevant assertions.
- Не превращать DONE/PASS в утверждение о живом Gemma или Java. Зафиксировать read-first и risk map в `reports/PSS-002-plan.md`.

### 2. Локальный LM Studio — диагностика и безопасность
- Сохранить текущий OpenAI-compatible контракт `GET /v1/models` и `POST /v1/chat/completions` с `response_format=json_schema`.
- Различать: сервер не запущен / connection refused, timeout/cancel, неверный локальный endpoint/HTTP 404, HTTP 401/403, malformed/oversize JSON, model ID not listed, успешная проверка каталога (НЕ доказательство загруженной модели или качества генерации).
- Понятные **русские** сообщения и краткие шаги «LM Studio → Developer → API Server → Start», затем точный ID; не заявлять server/model ready только по существованию скачанной модели при JIT loading.
- Допустим один **явный пользовательский** вызов генерации после отдельной проверки/или по кнопке Generate; программа НЕ запускает и не загружает LM Studio/model сама, не делает auto-retries, не переназначает model id, не предлагает удалённый API.
- Ошибки не должны выводить/логировать API token, тела пользовательского prompt/ответов (кроме явного экрана редактора); token — только память процесса, никогда ZIP/Git/settings.
- При недоступной реальной LM Studio закрыть mock HTTP gates, записать `BLOCKED_LM` с точной причиной и продолжить остальные независимые проверки. Никаких поддельных ответов модели.

### 3. Явный тип генерации и качественный review-only результат
- Добавить **static Designer-authored** выбор `TEMPLATE`, `PATTERN`, `MIXED` в конструктор. По умолчанию TEMPLATE (пользователь прежде всего улучшает речь). Не ломать существующий выбор topic/act/band/register/gender/count.
- Добавить режим в `GenerationRequest` обратносуместимым способом (например, необязательный последний параметр или отдельный typed field с дефолтом). Сохранённые старые уроки/сессии читаются без миграции; всегда проверять enum.
- Для TEMPLATE/PATTERN JSON Schema и проверка **после** ответа требуют выбранный kind у каждого item; MIXED разрешает оба. Любое нарушение — fail-closed и **0 кандидатов в workspace**, без частичной записи. Не доверять schema как единственному фильтру, валидировать локально.
- Сообщать количество добавленных, блокирующих замечаний, тип/модель и что это `DRAFT`; сохранённые кандидаты могут иметь warnings, но автоматического APPROVED/REJECTED нет.
- Улучшить объяснение duplicate findings только при необходимости для оператора, **не внедрять semantic/embedding дедуп под видом доказанной проверки**. Exact и lexical остаются отдельно, semantic NOT_CHECKED до отдельной задачи.
- Не ослаблять source fingerprint, peer conflicts, approved review hash, Cancel, лимиты 1–20, no-tools, JSON-strict, no-XML.

### 4. UI и DPI без разрушения дизайнера
- `MainForm` сохраняет split `.cs/.Designer.cs/.resx`; конструктор только `InitializeComponent()`; controls/events/layout описаны явно в Designer (НИКАКИХ loops/factories/IO/async/LINQ в InitializeComponent).
- Доказать доступные реальные UI-переходы: import → library → dialogue preview/lesson → generation → candidate edit/save/discard/cancel/approval → export, с повторной проверкой source drift. Достаточно smoke в безопасном workspace; не генерировать много данных и не менять user state.
- Проверить 100% и 150% DPI и уменьшение окна на реальной Windows-машине, **если UI automation/capture доступна**. Не менять глобальный масштаб Windows без явного user consent; допускается отдельный test process с заданным DPI awareness и осмысленными измерениями, но **не выдавать его за полноценный физический тест 150%**. При ограничениях инструмента честно `NOT_TESTED`, приложить инструкции оператору и доступные факты.
- Для исправлений layout не выносить controls в runtime BuildUI, не убирать свойства Designer.

## Ограничение scope и бюджета

Предпочтительно 6–10 изменённых production/test файлов (обоснованное расширение только с записью в plan). Scope: `LmStudioClient.cs`, `Models.cs`, `MainForm.cs`, `MainForm.Designer.cs`, `tests/Program.cs`, при необходимости `tests/SourceSmoke.cs`, `README_RU.md`, docs/reports/targeted scripts.
Нельзя менять `PackReader`, `ReviewExporter`, `WorkspaceStore`, `CandidateValidator`, `PathSafety` без воспроизведённого конкретного бага и отдельного targeted test; лучше оставить неизменными. Нельзя расширять Java, L2J, бэкенд, миграции, вводить SQLite/новые NuGet пакеты, создавать background generation, автоматизировать работу клиента L2.

Одна targeted test iteration на логическую правку, в конце одна финальная `scripts/Build-Verify.ps1`. Никаких массовых LLM batch/stress/full L2J Ant. Живой LM: до 1 GET preflight и до 2 POST **только если доступен** и на маленьком 1–3 items; остановиться после первого connection refused, auth failure, schema unsupported или mismatch. Ни автоматического повторения, ни автоматического увеличения лимитов.

## Выходные артефакты

- `reports/PSS-002-plan.md` (read-first, SHA, decision, touched scope).
- `reports/PSS-002-final.md` — реализация GREEN/BLOCKED/FAILED с real commands/exits/PASS-count, статусами LM/Designer/UI/DPI отдельно, original-source SHA equality, warnings, changed files, Git branch/base/commit/push/remote verification.
- `reports/PSS-002-ui.md` — UI routes + DPI evidence, NOT_TESTED честно.
- `reports/PSS-002-owned-files.txt` — полный точный список task-owned изменений, без user dirt.
- `artifacts/PhantomSemanticStudio-review-PSS-002-<date>.zip` через существующий `scripts/New-ReviewBundle.ps1`, проверить отсутствие workspace/token/binaries и manifest содержимого; ZIP локальный, не в Git.
- Только normal commit(ы) и **normal non-force push в `https://github.com/kpCat/PhantomSemanticStudio.git`, ветку `main`**, с последующей проверкой удалённого SHA.

**STOP после PSS-002.** Не начинать Java oracle, PSS-003, XML-публикацию, изменения L2J. Дальше — независимое ревью PSS-002 и отдельный этап Java-validator/isolated candidate pack.
