# PSS-006 — BIG CHECKPOINT A: надёжность Studio + большой приватный корпус игровых чатов

## Миссия

Это **первая из трёх укрупнённых задач** PSS-006 → PSS-007 → PSS-008. Цель этого checkpoint — превратить существующую Studio в устойчивый редактор и дать пользователю возможность отдельно открыть свой `chat.zip` (примерно 68 log entries / 67 MB в распакованном виде), безопасно импортировать, фильтровать и выбирать реальные образцы речи. **Не генерировать миллион кандидатов и не записывать ничего в L2J.**

Required published parent: `kpCat/PhantomSemanticStudio` `origin/main` **f92431aa5594a62210916e94bc6b617ec51f4bdc**. Рабочий root `C:\Users\ZBook\PhantomSemanticStudio\` (собственный репозиторий, локальная ветка может называться master; push только `HEAD:refs/heads/main`). URL origin (fetch/push) **строго** `https://github.com/kpCat/PhantomSemanticStudio.git`. Новый диалог Codex, основная coding-модель, reasoning **HIGH**. Не ждать дополнительных согласований для уже оговорённого read-only scope. Не начинать PSS-007.

## Read-first и сохранение user dirt

1. `AGENTS.md`, `README_RU.md`, `docs/DESIGN_RU.md`, `docs/SOURCE_AUDIT_RU.md`; `reports/PSS-005-final.md`, `reports/PSS-005-ui.md`, `reports/PSS-005-review.md`, PSS-004/003 отчёты, текущие тесты.
2. Прочитать `src/PhantomSemanticStudio.Core/{SemanticDuplicateScout,WorkspaceStore,LmStudioClient,Models,PackReader,PathSafety,TextRules}.cs`, `src/PhantomSemanticStudio.WinForms/{MainForm,MainForm.Designer,StageSelectionForm}.cs`, `tests/PhantomSemanticStudio.Tests/{Program,Pss005}.cs` и `scripts/Build-Verify.ps1`, `scripts/Verify-Designer.ps1`.
3. Проверить собственный git root/HEAD, точный origin, remote main, отсутствие чужих изменений. Входящий task ZIP создаёт untracked `docs/tasks/PSS-006/*`; это **не** user dirt для удаления и не разрешение на `git add .`. Если база и remote разошлись, **BLOCKED_BASE**, без force/rebase/reset.
4. Записать read-first и список затрагиваемых файлов в `reports/PSS-006-plan.md` до реализации. Тесты RED до продуктивных исправлений. Не переносить исторический `LIVE_LM BAD_RESPONSE` в новый PASS.

## Три внутренних checkpoints (НЕ три отдельных диалога)

### A — устранить блокирующий UI freshness refresh и локализовать LM BAD_RESPONSE

- Проверить гипотезу по PSS-005: `MainForm.ShowCandidate` синхронно `reader.Load` + `SemanticDuplicateScout.Search` + `IsEvidenceCurrent` (который повторно считает shortlist). Прежде чем исправлять, воспроизвести на synthetic/actual bounded corpus, измерить задержку UI и зафиксировать RED.
- Убрать тяжёлый I/O / пересчёт из UI handler, **не прятать STALE**. Cancellable background computation, generation/selection identity tokens, результаты только для актуальной выбранной записи, source/approval check прежде чем показывать CURRENT; stale/error/cancel → безопасный NOT_CURRENT. UI responsivity и минимум лишнего повторного сканирования доказать targeted test. `Cancel` должен работать.
- `BAD_RESPONSE` от живой Gemma в PSS-005 — **неизвестная конкретная причина**. Добавить безопасную диагностическую классификацию (например, refusal, finish reason, empty content, JSON syntax, schema/ID mismatch) **без печати raw response, prompts, токена, персональных данных**. Нельзя молча убирать строгий JSON-контракт, подменять модель, включать бесконтрольные retries или автоматически выгружать модель. Если модель уже поднята пользователем — максимум один preflight GET и два явно разрешённых small POST; если нет — BLOCKED_LM, остальные пункты продолжаются. Fake HTTP не равен live.

### B — потоковый безопасный импорт chat.zip в ОТДЕЛЬНОЕ локальное хранилище

- UI выбирает файл через стандартный `OpenFileDialog`; **не** предполагать, что `/mnt/data/chat.zip` доступен Windows/Codex, и **не** включать raw chat.zip в task ZIP/Git. Импорт без автоматического запуска LM Studio/модели.
- Read-only `ZipArchive` streaming entry by entry, **без извлечения файлов на диск**. Ограничения архива, непонятные имена/формат, ZipSlip, символические ссылки, шифрование, zip-bomb, лимиты строк/длины/байт, недопустимый UTF-8, cancellation — fail closed. Сохранение только в `workspace/corpora/<corpus-id>.partial` → final atomic commit (или другой проверенный транзакционный механизм). Новый импорт не перезаписывает старый; при сбое нет наполовину видимого индекса.
- Парсить `[dd.MM.yy HH:mm:ss] CHANNEL [speaker] message` и `[... ] TELL [sender -> receiver] message`, а также разные file-name suffix. Считать ошибки парсинга, никогда не путать текст сообщения с заголовками. В `TELL`/`FRIENDTELL` (включая вариации регистра) **по умолчанию ничего не индексировать и не сохранять**. Если когда-либо добавляется opt-in приватных каналов — отдельная будущая задача с explicit privacy design; сейчас НЕ добавлять. Удалять/pseudonymize speaker nicknames, не сохранять raw identities в индексе. Запрет raw PII в логах/Git.
- Тексты из публичных каналов тоже могут содержать PII: попытки обнаружить email, phone, IP, URLs и скрыть их в видимом превью; это best-effort, не гарантия полного обезличивания. Raw messages локально, с меткой «возможна персональная информация», никогда в Git. Источник корпуса только corpus reference; нельзя распространять личные сообщения через prompts без явного показа пользователю.
- Language triage **без автоматической потери текста**: CYRILLIC, LATIN_TRANSLIT_CANDIDATE, EN_OR_OTHER, MIXED, UNKNOWN, с причиной/неопределённостью; русский транслит ≠ английский, иностранные языки не превращать механически в кириллицу. Оригинал immutable; normalized/translation proposal — отдельные явные поля, НЕ автоматически записывать как достоверный русский текст.
- Удаление повторов/спама с counts, детерминированный hash normalized text+channel/scope, разделение raw/filtered, распределения по channels/lengths/language. Не создавать кандидатов, approvals, XML, Java tasks из импорта. **Не использовать** `session.json` для ~1.05 млн chat-lines: существующий лимит 16 MiB/5000 candidates сохраняется. Отдельный disk-backed индекс (SQLite допустим как единственный аргументированно необходимый и закреплённый NuGet dependency при доступном restore; иначе bounded BCL-only indexing). Выбор index/data-layer обосновать тестами и бюджетом, без масштабного переписывания WorkspaceStore.

### C — интерфейс «Корпус чатов», безопасная выборка и готовность к PSS-007

- Отдельное обычное WinForms окно `ChatCorpusForm.cs/.Designer.cs/.resx`, кнопка открытия в MainForm, **сохранять шесть вкладок и existing Designer**. Все controls объявлены статически в Designer. Конструкторы только `InitializeComponent()`. Никаких циклов, async, файловой работы или построения controls в InitializeComponent. Использовать пагинацию/виртуализацию (100–200 результатов/страница), поиск и фильтры: канал, дата, язык, длина, дубли, мусор, транслит/неоднозначность. Счётчики и прогресс. Большой корпус не должен биндинговаться целиком и блокировать UI.
- Показывать только 1–20 **явно выбранных** публичных сообщений для последующего редакционного использования. Возможность отметить и посмотреть обезличенные фрагменты; статус `SOURCE_MATERIAL_ONLY`, **не** кандидат и не approved. **Не** автоматически передавать текст в Gemma на этапе импорта и не превращать в Semantic Pack.
- Две локальные demo fixture routes: small synthetic log с TELL/FRIENDTELL, Latin/Cyrillic/translit/long/duplicate; large synthetic ~100k–1m lines (или потоковый масштабный smoke), с ограничением памяти, cancel/retry, no-Git/no-source writes. При отсутствии пользовательского chat.zip тестировать синтетику и пометить `ACTUAL_CHAT_IMPORT_NOT_RUN` — не выдумывать результат.

## Exit gates

Минимум: focused RED/GREEN A/B/C, реальные safe archive negative controls, deterministic counters, negative privacy fixtures, MainForm+ChatCorpusForm static Designer, WinForms public control tests, `scripts/Build-Verify.ps1` Release, existing 88 ordinary PASS without regression, source read-only fingerprint if source accessed, exact inventory, two separate mojibake and escaped Cyrillic scans. Live LM, physical UI/DPI и VS round-trip **отдельные статусы**, никакой фальшивый GREEN.

## Git/завершение

Допустимы 2–4 нормальных **локальных** промежуточных коммита по checkpoints после green gates, и один итоговый non-force push **только** `origin HEAD:refs/heads/main` в собственный Studio repo после сверки удалённой базы. Если не удалось завершить всё — BLOCKED c честным отчётом, безопасные completed gates коммитить/пушить по правилам SAFETY_AND_GIT, не выдавать частичный результат за GREEN.

Публичные `reports/PSS-006-plan.md`, `reports/PSS-006-final.md`, `reports/PSS-006-ui.md`, `reports/PSS-006-owned-files.txt` с безопасной агрегированной статистикой, **без сырого содержимого архивов, ников, реплик и приватных данных**. Никаких review ZIP/source ZIP и вызовов `New-ReviewBundle.ps1`. Проверяем по публичному GitHub. **STOP после PSS-006**, PSS-007 не начинать.
