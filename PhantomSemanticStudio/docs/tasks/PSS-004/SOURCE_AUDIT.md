# PSS-004 — аудит существующих владельцев (прочитано в GitHub)

**Checked base:** `kpCat/PhantomSemanticStudio`, `main`, SHA `edc41128270c14578b9edf748a3c8bc0c6196623`. Дальше Codex обязан сверить свой локальный checkout.

- `src/PhantomSemanticStudio.WinForms/MainForm.cs`, метод `StageXml_Click` (около строк 415–439):
  - `ids = session.Candidates.Where(c => c.Status == "APPROVED").Select(c => c.Id).ToArray();`
  - `ids.Length > 20` → отказ даже когда пользователь хотел один/два кандидата;
  - `txtExportLog` и два `MessageBox` показывают полный набор, но нет реального UI выбора подмножества.
  - текущий `RunAsync` блокирует вкладки во время I/O и передаёт `session.Candidates` + `ids` в Stager.
- `src/PhantomSemanticStudio.Core/IsolatedPackStager.cs` уже имеет идеальный интерфейс для этой задачи: `Create(store, snapshot, allPeers, selectedIds, selectionConfirmed, editorialConfirmed, cancellationToken)`. Внутри отдельно проверяются uniqueness и вся выбранная партия, сверяются source SHA и approval, stage создаётся только в `workspace/proposals`. Менять этот класс для UI selection **не требуется**.
- `CandidateReview.IsCurrent(c)` проверяет `APPROVED`, непустой `ReviewNote`, время и хэш всех важных полей. `Candidate.Status` может быть старым/изменённым: `Status==APPROVED` **не тождественно** `IsCurrent`.
- `SessionState.Candidates` допускает до 5000 элементов в `WorkspaceStore`. UI default не должен выбираться автоматически; окно должно выдерживать тысячи записей за счёт bounded отображения/фильтрации, а не 5000 синхронных вызовов `CandidateValidator`.
- `MainForm` WinForms / .NET 10; формы имеют стандартный `MainForm.Designer.cs`/`.resx`, `MainForm()` = только `InitializeComponent();`; `Verify-Designer.ps1` проверяет 6 вкладок и безопасность `InitializeComponent`. Новая модальная форма обязана быть совместима с Visual Studio Designer; нельзя выносить статическую компоновку в runtime.
- `ReviewExporter` — старая отдельная функция JSON ZIP для пользователя; **её не переписывать** из-за нового selection UI. Никаких review/source ZIP для задач Codex: это отдельное правило `AGENTS.md`.
- `reports/PSS-003-final.md`: опубликованы 69/0 console checks, отдельная реальная Java content проверка в физическом shadow, 65/65 source SHA/bytes по отчёту. Это evidence прежнего checkpoint, **не** повторная проверка PSS-004.

## Что изменить

- Новый pure Core `StageBatchSelection` (точный ручной список ID).
- Новое модальное стандартное WinForms окно (только selection/presentation).
- Минимальный вызов нового окна перед existing stage, без модификации authoritаtive stager.
- Точечные tests/Designer guard/docs.

## Не делать

Не добавлять `semantic/embedding` дедуп под видом доказанной проверки, не менять Java runtime, v1/v2/v3/custom и игровое содержимое, не расширять max 20, не создавать автостейджинг и автопубликацию. Эта задача не решает отдельную будущую проблему семантического сходства.
