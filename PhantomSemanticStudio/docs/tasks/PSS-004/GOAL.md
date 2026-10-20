# PSS-004 — точечный выбор партии для изолированного XML staging

## Контекст и модель

**Репозиторий ТОЛЬКО приложения:** `https://github.com/kpCat/PhantomSemanticStudio.git`.
Рабочий root: `C:\Users\ZBook\PhantomSemanticStudio\` (локальная ветка может называться `master`), назначение push: **`origin/main`**.
**Required base (проверенный GitHub HEAD):** `edc41128270c14578b9edf748a3c8bc0c6196623`.
Модель Codex: основная coding-модель, **reasoning High**, новый диалог, самостоятельная PSS-004 без повторного согласования. Завершить только после точных тестов, отчёта, обычных commit + non-force push и проверки remote SHA. Не начинать PSS-005.

## Почему именно эта задача

PSS-003 успешно реализовала изолированный `IsolatedPackStager.Create(store, snapshot, allPeers, selectedIds, selectionConfirmed, editorialConfirmed, token)` и внешний Java content-gate; PSS-003 report: 69/69 C# PASS, Java stage PASS по опубликованным evidence (новые тесты пока не выполнялись этой задачей). **Независимый просмотр обнаружил конкретный UX/flow-дефект в текущем `MainForm.StageXml_Click`: приложение всегда передаёт ВСЕ `Status==APPROVED` IDs и при их количестве >20 вообще отказывает.** С накоплением сотен/тысяч одобрений невозможно собрать небольшую осознанную партию, не меняя approvals в хранилище.

Это задача о **выборе ровно нужных кандидатов для одного Stage**, не об установке XML, увеличении лимитов, semantic dedup или доработке Java. В будущем semantic duplicate screening будет отдельным этапом. Текущее `SEMANTIC_NOT_CHECKED` не скрывать.

## Пользовательский результат

На вкладке «Экспорт для ревью» кнопка изолированного XML-предложения открывает **обычное модальное окно WinForms выбора партии**.

- Показывает все текущие `Status == APPROVED` кандидаты, включая пометку для неактуального approval; отображает ID, PATTERN/TEMPLATE, topic/act, gender/band/register, короткий текст, отметку редактора и текущую/устаревшую принадлежность baseline. Можно искать/фильтровать по ID, тексту, виду и теме.
- Никаких выбранных по умолчанию: пользователь **сам** отмечает от 1 до 20 кандидатов. Одобрений может быть более 20 и даже до максимума 5000 в `SessionState`; выбранная партия остаётся до 20.
- `CheckedListBox` или другой стандартный Designer-authored контроль поддерживает отмеченные ID при изменении фильтра (не по индексу строки). Справа/снизу всегда виден **полный список всех выбранных ID с исходным текстом**; никакой скрытый selected элемент не пропадает при фильтрации.
- Кнопка «Создать предложение» доступна только для ненулевого корректного набора ≤20 актуально одобренных ID. При stale approval, лишнем/повторном/несуществующем ID — понятная ошибка и запрет продолжения. Нет автоснятия stale, автодобавления APPROVED и пропуска несовместимого выбранного кандидата.
- После закрытия модального окна стадия **снова** проверяет весь выбранный набор и актуальность source/approval через уже существующий `IsolatedPackStager.Create`. Сохраняются два отдельных решения — подтверждение точного списка и редакционная аттестация (default No); Cancel на любом шаге = 0 новых finished stage, 0 изменений session/approval.
- XML создаётся только в `workspace/proposals/<id>/module/...`, старый receipt остаётся `STAGED_UNVALIDATED / Java NOT_RUN`, никаких файлов L2J и runtime нет. Старый пользовательский `REVIEW_ONLY JSON ZIP` — другая функция, её не менять; Codex **не создаёт review/source ZIP**.

## Архитектура / минимальный scope

1. Создать `src/PhantomSemanticStudio.Core/StageBatchSelection.cs`: чистый детерминированный метод, например `SelectExactIds(IReadOnlyList<Candidate> allPeers, IEnumerable<string> requestedIds, int maximum = 20) -> IReadOnlyList<string>`; проверяет длину [1;20], полное соответствие существующим `APPROVED` + `CandidateReview.IsCurrent`, уникальность ID (Ordinal), не-null, возвращает **только** сортированные точные ID (Ordinal). Не возвращает `Candidate` в UI по mutable reference и не записывает файлы. Гарантии Java/schema/dedup в этом методе не заявлять — authority остаётся Stager.
2. Создать `src/PhantomSemanticStudio.WinForms/StageSelectionForm.cs`, `StageSelectionForm.Designer.cs`, `StageSelectionForm.resx`. **Конструктор новой формы — только `InitializeComponent();`.** После создания вызывается отдельный `SetCandidates(...)` / `InitializeSelection(...)` с копиями кандидатов, snapshot fingerprint и текущими review flags. В Designer — только явные стандартные controls, properties, events, layout: `TextBox` фильтра, `CheckedListBox` кандидатов (или `ListView` с checkboxes), подробный `TextBox` выбранных, счётчик, `OK` и `Cancel`. Не создавать UI в цикле в Designer; в runtime binding вне конструктора можно перебрать данные. Без новых NuGet.
3. `MainForm.StageXml_Click`: перед операцией открыть модальное окно и получить exact IDs, а не `session.Candidates.Where(...).Select(...).ToArray()`. Повторно выполнить `StageBatchSelection.SelectExactIds(session.Candidates, selectedIds)` непосредственно перед Stager; две прежние confirmation dialogs сохранить и показать полный список, без замены осмысленного текста на только count. Не выставлять `selectionConfirmed/editorialConfirmed = true` без реального ручного решения.
4. При необходимости добавить `Compile Update` / `EmbeddedResource Update` для нового `.Designer.cs` / `.resx` в `src/PhantomSemanticStudio.WinForms/PhantomSemanticStudio.WinForms.csproj`, чтобы Visual Studio 2026 дизайнер показывал вложенные файлы формы. **`MainForm()` тоже остаётся только `InitializeComponent()`**.
5. Тесты: отдельная `tests/PhantomSemanticStudio.Tests/Pss004.cs`, вызов `--pss-004` через существующий `Program.cs` (partial class). Проверить чистую модель выбора и реальный `IsolatedPackStager.Create` с подмножеством на искусственном source fixture, 20/21, stale, cancel, peer drift, no implicit all. Для UI добавить статический `scripts/Verify-PSS004.ps1` (Designer patterns, два независимых текстовых прохода, безопасный source guard), плюс честное ручное UI evidence.
6. Обновить только необходимую operator-документацию `README_RU.md`, отчёты задачи, task inventory. Не вводить SQLite/provider/перестройку слоя хранения или массовые refactor.

**Предпочтительный бюджет:** ~7–10 production/test файлов, отчёты отдельно. Если обнаружен иной серьёзный дефект, сначала воспроизвести targeted regression; не превращать PSS-004 в широкий рефакторинг.

## Обязательно до исправлений

Прочитать и сверить фактические source на exact текущем HEAD, а не только сообщение о GREEN:
- `AGENTS.md`, `README_RU.md`, `docs/DESIGN_RU.md`, `docs/SOURCE_AUDIT_RU.md`;
- `reports/PSS-003-final.md`, `reports/PSS-003-owned-files.txt`, `reports/PSS-003-tests-green.txt`, `reports/PSS-003-java-summary.json`;
- `docs/tasks/PSS-004/{SOURCE_AUDIT,ARCHITECTURE,TEST_CASES,ACCEPTANCE,SAFETY_AND_GIT}.md`;
- `src/PhantomSemanticStudio.Core/{Models,CandidateValidator,WorkspaceStore,IsolatedPackStager,PathSafety}.cs`;
- `src/PhantomSemanticStudio.WinForms/{MainForm,MainForm.Designer}.cs`, `.csproj`, `tests/PhantomSemanticStudio.Tests/{Program,Pss003}.cs`, `scripts/{Build-Verify,Verify-Designer}.ps1`.

Перед кодом: `git status --porcelain=v1 -uall`, HEAD, branch, own repo root, fetch/push URL, `git ls-remote origin refs/heads/main`; сверить SHA `edc41128270c14578b9edf748a3c8bc0c6196623`. Сохранять user dirt и task-package bytes.

## Порядок и ограничения

1. Read-first, короткий `reports/PSS-004-plan.md` с найденной проблемой и точным touched scope.
2. RED targeted tests для selection (из более 20 approved явно взять 1–3), stale, duplicate, cancel, filter selection model; затем minimal implementation и GREEN.
3. Новый Form Designer + MainForm integration без потери прежних подтверждений и без silent selecting.
4. Focused suite, static designer/UTF-8/guard, один финальный `scripts/Build-Verify.ps1`, доступный UI smoke; **не запускать Java/Ant автоматически из GUI**. Для PSS-004 исторический PSS-003 Java PASS не равен повторному Java PASS.
5. Честные report/inventory/status, `git diff --check`, точный stage, обычный commit/push собственных файлов в `origin/main`, дистанционная SHA проверка; **STOP**.

## Неприкосновенные границы

`C:\Users\ZBook\L2J_Mobius\` и весь High Five **READ_ONLY**; никаких Ant/Java test/scratch/temp, Git, DB, сервера, клиента, write/rename/copy-destination туда. Без LM Studio auto-start и без генерации кода моделью. Не модифицировать `IsolatedPackStager.cs`, `ReviewExporter.cs`, `CandidateValidator.cs`, `WorkspaceStore.cs` или Java-оператор **без конкретного RED теста на дефект**, согласованного со scope; цель — выбор партии, не изменение данных. Никаких новых runtime gender/mature фильтров, новых topic/act, автоматических корректировок, XML публикации/применения, AI-автоодобрения.

## Результат

- `reports/PSS-004-plan.md`, `reports/PSS-004-final.md`, `reports/PSS-004-ui.md`, `reports/PSS-004-owned-files.txt`, targeted stdout/evidence без пользовательского сырого чата/token.
- C# Release build, исходные тесты PSS-001/002/003 + новые, static Designer и при возможности реальный VS Designer/100%/150% DPI: фактические выходы и самостоятельные статусы.
- Actual source import/stamps если выполнялось (только чтение); не выдавать report-цифры 65/65 за новый тест, если его не запускали.
- Git origin/main exact commit + remote SHA proof. Никаких review ZIP и исходников в архиве Codex.
- Действующий игровой Semantic Pack не меняется. Задача завершена только checkpoint PSS-004.
