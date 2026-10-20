using PhantomSemanticStudio.Core;

namespace PhantomSemanticStudio.WinForms;

public partial class MainForm : Form
{
    private WorkspaceStore? store;
    private HttpClient? http;
    private LmStudioClient? lm;
    private StudioSettings settings = new();
    private SessionState session = new();
    private PackSnapshot? snapshot;
    private readonly PackReader reader = new();
    private readonly PackPreview preview = new();
    private CancellationTokenSource? operation;
    private bool busy;
    private bool ready;
    private PreviewResult? lastPreview;
    private string lastInput = "";
    private Candidate? editingCandidate;
    private bool bindingCandidates;
    private bool restoringSelection;
    private bool selectionPending;
    private bool showingCandidate;
    private CancellationTokenSource? evidenceRefresh;
    private long evidenceSelectionVersion;
    private sealed record LessonChoice(EditorialLesson Lesson, string Label);

    // Важно для Visual Studio Designer: здесь нет IO, сервисов, DI или построения controls.
    public MainForm()
    {
        InitializeComponent();
    }

    private WorkspaceStore Store => store ?? throw new InvalidOperationException("Workspace не открыт.");
    private PackSnapshot Snapshot => snapshot ?? throw new InvalidOperationException("Сначала импортируйте Semantic Pack на вкладке Настройки.");
    private LmStudioClient Model => lm ?? throw new InvalidOperationException("LM Studio-клиент ещё не создан.");
    private sealed record Choice(string Key, string Label);
    private sealed record CandidateRow(Candidate Data, string State, string Kind, string Topic, string Gender, string Text);
    private sealed record LibraryRow(PackEntry Data, string Kind, string Id, string Act, string Text);
    private static string Key(ComboBox combo) => combo.SelectedItem is Choice choice ? choice.Key : combo.SelectedItem?.ToString() ?? "";
    private static string GenderLabel(string key) => key switch { "FEMALE" => "Женский", "MALE" => "Мужской", _ => "Без ограничения" };
    private static string KindLabel(string key) => key switch { "PATTERN" => "Вход", "TEMPLATE" => "Ответ", "ALIAS" => "Алиас", _ => "Лексика" };
    private Candidate? SelectedCandidate => (gridCandidates.CurrentRow?.DataBoundItem as CandidateRow)?.Data;

    private async void MainForm_Shown(object? sender, EventArgs e)
    {
        await RunAsync("Открываю собственный workspace…", async token =>
        {
            var workspace = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "PhantomSemanticStudio", "workspace");
            // Изолированный UI smoke: process-only override, только artifacts собственной source-сборки.
            var smokeWorkspace = Environment.GetEnvironmentVariable("PSS_UI_SMOKE_WORKSPACE");
            if (!string.IsNullOrEmpty(smokeWorkspace))
            {
                var artifacts = Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "../../../../../artifacts"));
                if (!PathSafety.IsWithin(smokeWorkspace, artifacts)) throw new InvalidDataException("UI smoke workspace должен быть внутри artifacts Studio.");
                workspace = smokeWorkspace;
            }
            store = new WorkspaceStore(workspace, settings.HighFiveRoot);
            settings = Store.LoadSettings(); settings.Validate(); Store.ProtectSource(settings.HighFiveRoot);
            session = await Task.Run(Store.LoadSession, token);
            http = LmStudioClient.CreateHttpClient(); lm = new LmStudioClient(http);
            FillSettings(); FillChoices(); BindCandidates(); BindLessons();
            tabs.SelectedTab = tabSettings; ready = true;
            statusLabel.Text = "L2J только для чтения. Настройки готовы; импорт запускается вручную.";
        });
        if (!ready) Close();
    }
    private void FillChoices()
    {
        cmbBand.DisplayMember = "Label"; cmbBand.ValueMember = "Key";
        cmbBand.DataSource = new[]
        {
            new Choice("UNKNOWN", "Незнакомы"), new Choice("NEUTRAL", "Нейтральные"), new Choice("FAMILIAR", "Знакомые"),
            new Choice("TRUSTED", "Доверенные"), new Choice("RIVAL", "Соперники"), new Choice("TENSE", "Напряжённые"), new Choice("HOSTILE", "Враждебные")
        };
        cmbGender.DisplayMember = "Label"; cmbGender.ValueMember = "Key";
        cmbGender.DataSource = new[] { new Choice("ANY", "Без ограничения"), new Choice("FEMALE", "Женский — нужен runtime"), new Choice("MALE", "Мужской — нужен runtime") };
        cmbRegister.DisplayMember = "Label"; cmbRegister.ValueMember = "Key";
        cmbRegister.DataSource = new[] { new Choice("CASUAL", "Разговорный"), new Choice("NEUTRAL", "Нейтральный") };
    }
    private void FillSettings()
    {
        txtSourcePath.Text = settings.HighFiveRoot; txtEndpoint.Text = settings.Endpoint; txtModel.Text = settings.ModelId;
        nudTemperature.Value = (decimal)settings.Temperature; nudTokens.Value = settings.MaxTokens; nudTimeout.Value = settings.TimeoutSeconds;
        txtWorkspace.Text = Store.Root;
    }
    private StudioSettings ReadSettings() => new()
    {
        HighFiveRoot = txtSourcePath.Text.Trim(), Endpoint = txtEndpoint.Text.Trim(), ModelId = txtModel.Text.Trim(),
        Temperature = (double)nudTemperature.Value, MaxTokens = (int)nudTokens.Value, TimeoutSeconds = (int)nudTimeout.Value
    };
    private void ResetSnapshot()
    {
        CancelEvidenceRefresh();
        snapshot = null; cmbAct.Items.Clear(); cmbTopic.Items.Clear(); gridLibrary.DataSource = null;
        lblSnapshot.Text = "Источник изменён. Нужен новый импорт; старые кандидаты автоматически не перепривязываются.";
        preview.Reset(); lastPreview = null;
    }
    private async void SaveSettings_Click(object? sender, EventArgs e)
    {
        await RunAsync("Сохраняю настройки Studio…", _ =>
        {
            var updated = ReadSettings(); updated.Validate();
            var sourceChanged = !Path.GetFullPath(updated.HighFiveRoot).Equals(Path.GetFullPath(settings.HighFiveRoot), StringComparison.OrdinalIgnoreCase);
            Store.SaveSettings(updated); settings = updated;
            if (sourceChanged) ResetSnapshot();
            txtSettingsResult.Text = "Сохранены только настройки Studio. API token не сохраняется. L2J не изменён.";
            return Task.CompletedTask;
        });
    }
    private void BrowseSource_Click(object? sender, EventArgs e)
    {
        using var dialog = new FolderBrowserDialog { Description = "Выберите L2J_Mobius_CT_2.6_HighFive (только чтение)", UseDescriptionForTitle = true };
        if (Directory.Exists(txtSourcePath.Text)) dialog.InitialDirectory = txtSourcePath.Text;
        if (dialog.ShowDialog(this) == DialogResult.OK) txtSourcePath.Text = dialog.SelectedPath;
    }
    private async void Import_Click(object? sender, EventArgs e)
    {
        await RunAsync("Читаю humanized v1/v2/v3/custom, исходники не меняются…", async token =>
        {
            var updated = ReadSettings(); updated.Validate();
            PathSafety.AssertDisjoint(PathSafety.RepositoryBoundary(updated.HighFiveRoot), Store.Root);
            var imported = await Task.Run(() => reader.Load(updated.HighFiveRoot, token), token);
            token.ThrowIfCancellationRequested();
            Store.SaveSettings(updated); Store.SaveSnapshot(imported); settings = updated; snapshot = imported;
            var acts = imported.Acts.Where(a => !TextRules.IsFunctionalAct(a)).ToArray();
            cmbAct.Items.Clear(); cmbAct.Items.AddRange(acts); if (acts.Length > 0) cmbAct.SelectedIndex = 0;
            var patterns = imported.Entries.Count(x => x.Kind == "PATTERN"); var templates = imported.Entries.Count(x => x.Kind == "TEMPLATE");
            lblSnapshot.Text = $"Импорт: {patterns:N0} входных фраз, {templates:N0} ответов, {imported.Files.Count} файлов • {imported.Fingerprint[..16]}";
            txtSettingsResult.Text = lblSnapshot.Text + Environment.NewLine + string.Join(Environment.NewLine, imported.Warnings.Take(4));
            preview.Reset(); lastPreview = null; BindLibrary(); BindCandidates();
            statusLabel.Text = "Импорт завершён. Это инспекция Studio, не подтверждение Java-validator.";
            tabs.SelectedTab = tabGenerate;
        });
    }
    private void Act_Changed(object? sender, EventArgs e)
    {
        cmbTopic.Items.Clear(); if (snapshot == null) return;
        var topics = snapshot.Entries.Where(p => p.Kind == "PATTERN" && p.Act == Key(cmbAct)).Select(p => p.Topic).Distinct(StringComparer.Ordinal).Order(StringComparer.Ordinal).ToArray();
        cmbTopic.Items.AddRange(topics); if (topics.Length > 0) cmbTopic.SelectedIndex = 0;
    }
    private async void CheckLm_Click(object? sender, EventArgs e)
    {
        await RunAsync("Проверяю локальный LM Studio…", async token =>
        {
            var diagnostic = await Model.CheckModelAsync(ReadSettings(), txtApiKey.Text, token);
            txtSettingsResult.Text = diagnostic.ToString();
            statusLabel.Text = diagnostic.ToString();
        });
    }
    private async void Generate_Click(object? sender, EventArgs e)
    {
        await RunAsync("Gemma создаёт черновики. Автоматического одобрения нет…", async token =>
        {
            var pack = Snapshot; var configured = ReadSettings();
            var mode = cmbMode.SelectedIndex switch { 0 => GenerationMode.TEMPLATE, 1 => GenerationMode.PATTERN, 2 => GenerationMode.MIXED, _ => throw new InvalidDataException("Выберите тип генерации.") };
            var request = new GenerationRequest(Key(cmbTopic), Key(cmbAct), Key(cmbBand), Key(cmbRegister), Key(cmbGender), txtInstruction.Text.Trim(), txtWords.Text.Trim(), (int)nudCount.Value, mode);
            var drafts = await Model.GenerateAsync(configured, txtApiKey.Text, pack, request, token);
            var additions = drafts.Select(d => new Candidate
            {
                Kind = d.Kind, Text = d.Text, Topic = request.Topic, Act = request.Act, Band = request.Band,
                Register = request.Register, Gender = request.Gender, SourceFingerprint = pack.Fingerprint,
                ModelId = configured.ModelId, Instruction = request.Instruction + "\nЛексика: " + request.Words, Rationale = d.Reason
            }).ToList();
            var all = session.Candidates.Concat(additions).ToList();
            var invalid = await Task.Run(() => additions.Count(c => CandidateValidator.Validate(c, pack, all).Any(i => i.Severity == IssueSeverity.Error)), token);
            token.ThrowIfCancellationRequested();
            var next = new SessionState { Candidates = all, Lessons = session.Lessons, ScopedLessons = session.ScopedLessons }; Store.SaveSession(next); session = next;
            BindCandidates(additions.FirstOrDefault()?.Id); tabs.SelectedTab = tabCandidates;
            var summary = $"DRAFT: добавлено {additions.Count}; с блокирующими замечаниями: {invalid}. Тип: {mode}; модель: {configured.ModelId}. Требуется ручное ревью; никто не одобрен автоматически.";
            statusLabel.Text = summary; txtValidation.Text = summary + "\r\n\r\n" + txtValidation.Text;
        });
    }
    private void ToCandidates_Click(object? sender, EventArgs e) => tabs.SelectedTab = tabCandidates;
    private void BindLibrary()
    {
        if (snapshot == null) return;
        var query = txtSearch.Text.Trim();
        var found = snapshot.Entries.Where(x => query.Length == 0 || x.Text.Contains(query, StringComparison.OrdinalIgnoreCase)
            || x.Id.Contains(query, StringComparison.OrdinalIgnoreCase) || x.Act.Contains(query, StringComparison.OrdinalIgnoreCase)).ToList();
        gridLibrary.DataSource = found.Take(1000).Select(x => new LibraryRow(x, KindLabel(x.Kind), x.Id, x.Act, x.Text)).ToList();
        lblLibraryStats.Text = $"Найдено: {found.Count:N0}; показаны первые {Math.Min(1000, found.Count):N0}. Всего: {snapshot.Entries.Count:N0}. Источник только для чтения.";
    }
    private void Search_Click(object? sender, EventArgs e) => BindLibrary();
    private void LibrarySelection_Changed(object? sender, EventArgs e)
    {
        if (gridLibrary.CurrentRow?.DataBoundItem is not LibraryRow row) return;
        var item = row.Data;
        txtLibraryDetails.Text = $"{item.Id}\r\n{item.SourceFile}:{item.SourceLine}\r\nAct: {item.Act}; topic: {item.Topic}; band: {item.Band}; register: {item.Register}; profanity: {item.Profanity}; mature: {item.Mature}\r\nFact/recall: {item.Fact}/{item.Recall}\r\n\r\n{item.Text}";
    }
    private void BindCandidates(string? selectedId = null)
    {
        var rows = session.Candidates.Select(c => new CandidateRow(c,
            CandidateReview.IsCurrent(c) ? "Одобрен" : c.Status == "REJECTED" ? "Отклонён" : "Черновик / ревью",
            KindLabel(c.Kind), c.Topic, GenderLabel(c.Gender), c.Text)).ToList();
        bindingCandidates = true;
        try
        {
            gridCandidates.DataSource = rows;
            if (selectedId != null)
            {
                var index = rows.FindIndex(r => r.Data.Id == selectedId);
                if (index >= 0) gridCandidates.CurrentCell = gridCandidates.Rows[index].Cells[0];
            }
            lblCandidates.Text = $"Кандидатов: {rows.Count}; актуально одобрено: {session.Candidates.Count(CandidateReview.IsCurrent)}. Правка отменяет одобрение.";
        }
        finally { bindingCandidates = false; }
        ShowCandidate(SelectedCandidate);
    }
    private void CandidateSelection_Changed(object? sender, EventArgs e)
    {
        if (bindingCandidates || restoringSelection || selectionPending || SelectedCandidate?.Id == editingCandidate?.Id || !IsHandleCreated) return;
        // CurrentCellChanged уже видит новую строку; restore/rebind нельзя делать внутри её смены.
        selectionPending = true;
        BeginInvoke(new Action(() =>
        {
            selectionPending = false;
            if (!IsDisposed && !bindingCandidates) ChangeCandidateSelection();
        }));
    }
    private void ChangeCandidateSelection()
    {
        var candidate = SelectedCandidate;
        if (candidate?.Id == editingCandidate?.Id) return;
        if (candidate?.Id != editingCandidate?.Id && !ResolvePendingEdit())
        {
            restoringSelection = true;
            try
            {
                foreach (DataGridViewRow row in gridCandidates.Rows)
                    if ((row.DataBoundItem as CandidateRow)?.Data.Id == editingCandidate?.Id) { gridCandidates.CurrentCell = row.Cells[0]; break; }
            }
            finally { restoringSelection = false; }
            return;
        }
        ShowCandidate(candidate);
    }
    private void ShowCandidate(Candidate? candidate)
    {
        CancelEvidenceRefresh();
        showingCandidate = true;
        editingCandidate = candidate;
        txtCandidateText.Text = candidate?.Text ?? ""; txtReviewNote.Text = candidate?.ReviewNote ?? "";
        showingCandidate = false;
        lblSelected.Text = candidate == null ? "Выберите кандидата." : $"{candidate.Id} • {candidate.Act} • {candidate.Band} / {candidate.Register}";
        txtValidation.Text = candidate == null ? "Нет выбранного кандидата." : "Нажмите «Проверить».\r\n\r\nОбоснование модели (не проверка):\r\n" + candidate.Rationale;
        if (candidate?.SemanticReview != null)
        {
            if (snapshot == null) { txtValidation.Text = "STALE: источник ещё не импортирован. Сохранённый совет не считается действующим."; return; }
            txtValidation.Text = "STALE: актуальность сохранённого совета проверяется в фоне…";
            var cancel = new CancellationTokenSource(); evidenceRefresh = cancel; btnCancel.Enabled = true;
            _ = RefreshSavedEvidenceAsync(candidate.Copy(), snapshot, session.Candidates.Select(p => p.Copy()).ToList(), evidenceSelectionVersion, cancel);
        }
    }
    private void CancelEvidenceRefresh()
    {
        evidenceSelectionVersion++; evidenceRefresh?.Cancel(); evidenceRefresh = null;
        if (!IsDisposed) btnCancel.Enabled = busy;
    }
    private async Task RefreshSavedEvidenceAsync(Candidate candidate, PackSnapshot baseline, IReadOnlyList<Candidate> peers,
        long version, CancellationTokenSource cancel)
    {
        bool MayPaint() => !IsDisposed && !Disposing && !cancel.IsCancellationRequested && version == evidenceSelectionVersion
            && ReferenceEquals(snapshot, baseline) && editingCandidate?.Id == candidate.Id && SelectedCandidate?.Id == candidate.Id
            && txtCandidateText.Text.Trim() == candidate.Text && txtReviewNote.Text.Trim() == candidate.ReviewNote
            && SemanticDuplicateScout.CandidateFingerprint(editingCandidate) == SemanticDuplicateScout.CandidateFingerprint(candidate)
            && editingCandidate.ApprovedFingerprint == candidate.ApprovedFingerprint && editingCandidate.Status == candidate.Status
            && SemanticDuplicateScout.PeersFingerprint(candidate, session.Candidates) == SemanticDuplicateScout.PeersFingerprint(candidate, peers);
        try
        {
            var shortlist = await Task.Run(() =>
            {
                var fresh = reader.Load(baseline.ModuleRoot, cancel.Token);
                return SemanticDuplicateScout.Search(fresh, candidate, peers, candidate.SemanticReview!.MaxMatches, cancel.Token);
            }, cancel.Token);
            if (MayPaint()) txtValidation.Text = FormatSemanticShortlist(candidate, shortlist, candidate.SemanticReview,
                SemanticDuplicateScout.IsEvidenceCurrent(shortlist, candidate.SemanticReview!));
        }
        catch (Exception ex) when (ex is OperationCanceledException or IOException or InvalidDataException or UnauthorizedAccessException
            or System.Xml.XmlException or System.Text.DecoderFallbackException)
        {
            if (!IsDisposed && !Disposing && version == evidenceSelectionVersion && editingCandidate?.Id == candidate.Id)
                txtValidation.Text = "STALE: актуальность не подтверждена или проверка отменена. Сохранённый совет не считается действующим.";
        }
        finally
        {
            if (ReferenceEquals(evidenceRefresh, cancel)) { evidenceRefresh = null; if (!IsDisposed && !Disposing) btnCancel.Enabled = busy; }
            cancel.Dispose();
        }
    }
    private void SemanticEditor_Changed(object? sender, EventArgs e)
    {
        if (!showingCandidate && editingCandidate != null
            && (txtCandidateText.Text.Trim() != editingCandidate.Text || txtReviewNote.Text.Trim() != editingCandidate.ReviewNote))
            txtValidation.Text = "STALE: есть несохранённая правка. Прежняя экспертиза не относится к тексту редактора; сначала сохраните/разрешите правку.";
    }
    private static string FormatSemanticShortlist(Candidate c, SemanticShortlist s, SemanticReviewEvidence? evidence = null, bool current = false)
    {
        var output = new System.Text.StringBuilder();
        output.AppendLine("COVERAGE_LIMITED — лексический поиск не доказывает отсутствие смысловых повторов.");
        output.AppendLine($"Кандидат {c.Id} • {c.Kind} / {c.Topic} / {c.Act}\r\n{c.Text}");
        output.AppendLine($"Рассмотрено: {s.TotalConsidered}; в scope: {s.ScopeConsidered}; shortlist: {s.Matches.Count} / {s.MaxMatches}; НЕ передано модели: {s.TotalConsidered - s.Matches.Count}.");
        output.AppendLine(evidence == null ? "Проверка смысла: НЕ ПРОВОДИЛАСЬ для этого списка. Отправка только отдельной кнопкой."
            : current ? $"MODEL_ADVISORY_NOT_VERIFIED — совет модели {evidence.ModelId}; {evidence.CreatedAtUtc:u}. Не допуск к XML."
            : "STALE: прежний совет модели не считается действующим. Требуется новая явная экспертиза.");
        foreach (var m in s.Matches)
        {
            output.AppendLine($"\r\n{m.RefKey}\r\n{m.Kind} / {m.Topic} / {m.Act} / {m.Band} / {m.Register}; лексическая оценка: {m.Score:F3}");
            output.AppendLine(m.IsPeer ? "Активный кандидат workspace" : $"{m.SourceFile}:{m.SourceLine}");
            // Pathological source strings stay bounded on screen, with explicit truncation.
            output.AppendLine(m.Text.Length <= 4000 ? m.Text : m.Text[..4000] + "… [показаны первые 4000 символов]");
            if (current && evidence != null)
            {
                var verdict = evidence.Verdicts.Single(v => v.RefKey == m.RefKey);
                output.AppendLine($"Совет: {verdict.Relation} — {verdict.Reason}");
                if (verdict.Relation == "SAME_MEANING") output.AppendLine("ВНИМАНИЕ: модель предполагает смысловой повтор. Решение принимает редактор вручную.");
            }
        }
        if (s.Matches.Count == 0) output.AppendLine("Лексически близких примеров не найдено. Синонимы могут быть пропущены; нулевая вероятность дубля не доказана.");
        return output.ToString();
    }
    private async void FindSimilar_Click(object? sender, EventArgs e)
    {
        if (busy || !ResolvePendingEdit()) return;
        await RunAsync("Ищу лексически похожие записи локально…", async token =>
        {
            var c = RequireCandidate().Copy(); var pack = Snapshot;
            var peers = session.Candidates.Select(p => p.Copy()).ToList();
            var fresh = await Task.Run(() => reader.Load(pack.ModuleRoot, token), token);
            var shortlist = await Task.Run(() => SemanticDuplicateScout.Search(fresh, c, peers, cancellationToken: token), token);
            token.ThrowIfCancellationRequested();
            var current = c.SemanticReview != null && SemanticDuplicateScout.IsEvidenceCurrent(shortlist, c.SemanticReview);
            txtValidation.Text = FormatSemanticShortlist(c, shortlist, c.SemanticReview, current);
            statusLabel.Text = "COVERAGE_LIMITED: локальный поиск завершён; сетевых запросов нет.";
        });
    }
    private async void SemanticReview_Click(object? sender, EventArgs e)
    {
        if (busy || !ResolvePendingEdit()) return;
        await RunAsync("Один запрос Gemma: только рекомендация по ограниченному списку…", async token =>
        {
            var c = RequireCandidate().Copy(); var pack = Snapshot; var configured = ReadSettings();
            if (!PathSafety.Canonical(configured.HighFiveRoot).Equals(pack.ModuleRoot, StringComparison.OrdinalIgnoreCase))
                throw new InvalidDataException("Источник в настройках изменён. Сначала импортируйте его; автоматической перепривязки нет.");
            var peers = session.Candidates.Select(p => p.Copy()).ToList();
            var shortlist = await Task.Run(() => SemanticDuplicateScout.Search(pack, c, peers, cancellationToken: token), token);
            txtValidation.Text = FormatSemanticShortlist(c, shortlist);
            if (shortlist.Matches.Count == 0) { statusLabel.Text = "COVERAGE_LIMITED: список пуст; модель не вызывалась, смысловые повторы не исключены."; return; }
            SemanticReviewEvidence evidence;
            try { evidence = await Model.ReviewSemanticAsync(configured, txtApiKey.Text, c, shortlist, token); }
            catch (LmStudioException ex)
            {
                txtValidation.AppendText("\r\nBLOCKED_LM: новая экспертиза не сохранена. " + ex.Diagnostic);
                statusLabel.Text = "BLOCKED_LM: " + ex.Diagnostic; txtSettingsResult.Text = ex.Diagnostic.ToString();
                MessageBox.Show(this, ex.Diagnostic.ToString(), "LM Studio — диагностика", MessageBoxButtons.OK, MessageBoxIcon.Warning); return;
            }
            // One source reread and full hash check immediately before synchronous atomic save.
            var fresh = await Task.Run(() => reader.Load(pack.ModuleRoot, token), token);
            token.ThrowIfCancellationRequested();
            var updated = SemanticDuplicateScout.WithCurrentEvidence(RequireCandidate(), fresh, session.Candidates, evidence, token);
            token.ThrowIfCancellationRequested(); SaveCandidateVersion(updated); BindCandidates(updated.Id);
            statusLabel.Text = "MODEL_ADVISORY_NOT_VERIFIED / COVERAGE_LIMITED. Совет сохранён; актуальность показана в отчёте, решение остаётся ручным.";
        });
    }
    private bool ResolvePendingEdit()
    {
        if (editingCandidate == null || (txtCandidateText.Text.Trim() == editingCandidate.Text && txtReviewNote.Text.Trim() == editingCandidate.ReviewNote)) return true;
        var answer = MessageBox.Show(this, "Есть несохранённая правка текста или отметки ревью.\r\nДа — сохранить (одобрение отменится). Нет — отбросить. Отмена — остаться.",
            "Сохранить / Отбросить / Отмена", MessageBoxButtons.YesNoCancel, MessageBoxIcon.Question, MessageBoxDefaultButton.Button3);
        if (answer == DialogResult.Cancel) return false;
        if (answer == DialogResult.No) { ShowCandidate(editingCandidate); return true; }
        try
        {
            var updated = editingCandidate.Copy(); CandidateReview.Edit(updated, txtCandidateText.Text); updated.ReviewNote = txtReviewNote.Text.Trim();
            SaveCandidateVersion(updated); BindCandidates(SelectedCandidate?.Id);
            return true;
        }
        catch (Exception ex) { MessageBox.Show(this, ex.Message, "Правка не сохранена", MessageBoxButtons.OK, MessageBoxIcon.Warning); return false; }
    }
    private void SaveCandidateVersion(Candidate updated)
    {
        var next = new SessionState { Candidates = session.Candidates.Select(c => c.Id == updated.Id ? updated : c).ToList(), Lessons = session.Lessons, ScopedLessons = session.ScopedLessons };
        Store.SaveSession(next); session = next;
    }
    private void Tabs_Selecting(object? sender, TabControlCancelEventArgs e)
    {
        if (!busy && tabs.SelectedTab == tabCandidates && e.TabPage != tabCandidates && !ResolvePendingEdit()) e.Cancel = true;
    }
    private Candidate RequireCandidate(bool requireSavedText = true)
    {
        var c = SelectedCandidate ?? throw new InvalidOperationException("Выберите кандидата.");
        if (requireSavedText && txtCandidateText.Text.Trim() != c.Text) throw new InvalidOperationException("Сначала сохраните правку текста. Проверяется и одобряется только сохранённая версия.");
        return c;
    }
    private static string FormatIssues(IReadOnlyList<ValidationIssue> issues)
        => issues.Count == 0 ? "Блокирующих структурных/лексических замечаний нет.\r\nЭто не доказательство смысловой правильности, фактов и грамматики. Прочитайте текст вручную."
            : string.Join("\r\n\r\n", issues.Select(i => $"{(i.Severity == IssueSeverity.Error ? "БЛОК" : "ПРОВЕРИТЬ")} [{i.Code}] {i.Message}"));
    private async void SaveCandidate_Click(object? sender, EventArgs e)
    {
        await RunAsync("Сохраняю правку только в workspace…", _ =>
        {
            var c = RequireCandidate(false).Copy(); CandidateReview.Edit(c, txtCandidateText.Text); c.ReviewNote = txtReviewNote.Text.Trim(); SaveCandidateVersion(c); BindCandidates(c.Id);
            statusLabel.Text = "Правка сохранена. Прежнее одобрение аннулировано."; return Task.CompletedTask;
        });
    }
    private async void Validate_Click(object? sender, EventArgs e)
    {
        await RunAsync("Проверяю структуру и повторы…", async token =>
        {
            var c = RequireCandidate(); var pack = Snapshot;
            var issues = await Task.Run(() => CandidateValidator.Validate(c, pack, session.Candidates), token);
            txtValidation.Text = FormatIssues(issues);
        });
    }
    private async void Approve_Click(object? sender, EventArgs e)
    {
        await RunAsync("Проверяю кандидата перед ручным одобрением…", async token =>
        {
            var c = RequireCandidate().Copy(); var pack = Snapshot; var note = txtReviewNote.Text.Trim();
            var issues = await Task.Run(() => CandidateValidator.Validate(c, pack, session.Candidates), token);
            txtValidation.Text = FormatIssues(issues);
            if (issues.Any(i => i.Severity == IssueSeverity.Error)) throw new InvalidDataException("Есть блокирующие замечания. Кандидат не одобрен.");
            if (note.Length == 0) throw new InvalidDataException("Запишите отметку ручной проверки.");
            var question = "Одобрить этот текст для пакета РЕВЬЮ?\r\n\r\n" + c.Text + "\r\n\r\nПредупреждений: " + issues.Count + ". Это не установка на сервер.";
            if (MessageBox.Show(this, question, "Ручное одобрение", MessageBoxButtons.YesNo, MessageBoxIcon.Question, MessageBoxDefaultButton.Button2) != DialogResult.Yes) return;
            await Task.Run(() => CandidateReview.Approve(c, pack, session.Candidates, note), token);
            SaveCandidateVersion(c); BindCandidates(c.Id); txtValidation.Text = FormatIssues(issues);
            statusLabel.Text = "Одобрено для ревью. Исходный Semantic Pack не изменён.";
        });
    }
    private async void Reject_Click(object? sender, EventArgs e)
    {
        await RunAsync("Отклоняю кандидата…", _ =>
        {
            if (!ResolvePendingEdit()) return Task.CompletedTask;
            var c = RequireCandidate(false).Copy(); CandidateReview.Reject(c); SaveCandidateVersion(c); BindCandidates(c.Id); return Task.CompletedTask;
        });
    }
    private async void Preview_Click(object? sender, EventArgs e)
    {
        await RunAsync("Проверка по каталогу — без вызова Gemma…", async token =>
        {
            var pack = Snapshot; var input = txtChatInput.Text.Trim();
            if (input.Length == 0) return;
            var band = Key(cmbBand); var register = Key(cmbRegister);
            var reply = await Task.Run(() => preview.Reply(pack, input, band, register), token);
            lastPreview = reply; lastInput = input;
            txtConversation.AppendText($"Вы: {input}\r\nПак: {(reply.Text.Length == 0 ? "[нет ответа]" : reply.Text)}\r\nШаблоны: {reply.PatternId} → {reply.TemplateId}\r\n{reply.Note}\r\n\r\n");
            if (txtConversation.TextLength > 100000) txtConversation.Text = txtConversation.Text[^80000..];
            txtChatInput.Clear();
        });
    }
    private async void Teach_Click(object? sender, EventArgs e)
    {
        await RunAsync("Сохраняю замечание как редакционное задание…", _ =>
        {
            if (lastPreview == null || string.IsNullOrWhiteSpace(txtCorrection.Text)) throw new InvalidOperationException("Сначала проверьте реплику и напишите замечание.");
            var lesson = $"Вход игрока: {lastInput}\nОтвет пака: {lastPreview.Text}\nЗамечание редактора: {txtCorrection.Text.Trim()}\nПредложи новые PATTERN/TEMPLATE для выбранной темы; не код и не новые игровые действия.";
            if (lesson.Length > 4000) throw new InvalidDataException("Замечание с контекстом длиннее 4000 символов.");
            var scoped = new EditorialLesson { Text = lesson, Topic = Key(cmbTopic), Act = Key(cmbAct), Band = Key(cmbBand), Register = Key(cmbRegister), Gender = Key(cmbGender), SourceFingerprint = Snapshot.Fingerprint };
            if (Snapshot.Entries.Any(e => e.Kind == "PATTERN" && e.Act == lastPreview.Act && e.Topic == lastPreview.Topic))
                scoped = scoped with { Act = lastPreview.Act, Topic = lastPreview.Topic };
            scoped.ToRequest(Snapshot, false);
            var next = new SessionState { Candidates = session.Candidates, Lessons = session.Lessons, ScopedLessons = session.ScopedLessons.Append(scoped).ToList() };
            Store.SaveSession(next); session = next;
            BindLessons(scoped.Id);
            statusLabel.Text = "Замечание сохранено с scope. Нажмите «Применить выбранное», чтобы перенести его в конструктор.";
            return Task.CompletedTask;
        });
    }
    private void ClearChat_Click(object? sender, EventArgs e)
    {
        preview.Reset(); lastPreview = null; lastInput = ""; txtConversation.Clear(); txtCorrection.Clear();
    }
    private void BindLessons(string? selectedId = null)
    {
        var choices = session.ScopedLessons.Select(l => new LessonChoice(l, $"{l.CreatedAtUtc:yyyy-MM-dd} • {l.Topic} / {l.Act} • {l.Band} / {l.Gender}"))
            .Concat(session.Lessons.Select((text, index) => new LessonChoice(new EditorialLesson { Id = "legacy-" + index, Text = text }, $"Старое замечание {index + 1} — scope и baseline неизвестны"))).ToList();
        cmbLessons.DisplayMember = "Label"; cmbLessons.DataSource = choices;
        if (selectedId != null) cmbLessons.SelectedItem = choices.FirstOrDefault(c => c.Lesson.Id == selectedId);
        LessonSelection_Changed(this, EventArgs.Empty);
    }
    private void LessonSelection_Changed(object? sender, EventArgs e)
    {
        if (cmbLessons.SelectedItem is not LessonChoice choice) { txtLessonDetails.Text = "Сохранённых замечаний пока нет."; return; }
        var l = choice.Lesson;
        txtLessonDetails.Text = $"Тема: {l.Topic}; act: {l.Act}; отношения: {l.Band}; стиль: {l.Register}; пол: {l.Gender}\r\nBaseline: {(l.SourceFingerprint.Length == 0 ? "неизвестен — нужно новое ревью" : l.SourceFingerprint)}\r\n\r\n{l.Text}";
    }
    private async void ApplyLesson_Click(object? sender, EventArgs e)
    {
        await RunAsync("Переношу одно выбранное замечание в конструктор…", _ =>
        {
            var pack = Snapshot;
            var lesson = (cmbLessons.SelectedItem as LessonChoice)?.Lesson ?? throw new InvalidOperationException("Выберите сохранённое замечание.");
            if (lesson.SourceFingerprint.Length == 0)
                lesson = lesson with { Topic = Key(cmbTopic), Act = Key(cmbAct), Band = Key(cmbBand), Register = Key(cmbRegister), Gender = Key(cmbGender) };
            var reviewed = false;
            if (lesson.SourceFingerprint != pack.Fingerprint)
            {
                var question = $"Baseline замечания изменён или неизвестен. Повторно проверьте текст и scope:\r\n{lesson.Topic} / {lesson.Act}; {lesson.Band} / {lesson.Register} / {lesson.Gender}\r\n\r\n{lesson.Text}\r\n\r\nПрименить как новое редакционное задание? Исходное замечание и его baseline сохраняются.";
                reviewed = MessageBox.Show(this, question, "Новое ревью замечания", MessageBoxButtons.YesNo, MessageBoxIcon.Question, MessageBoxDefaultButton.Button2) == DialogResult.Yes;
                if (!reviewed) return Task.CompletedTask;
            }
            var request = lesson.ToRequest(pack, reviewed);
            cmbAct.SelectedItem = request.Act; cmbTopic.SelectedItem = request.Topic;
            cmbBand.SelectedValue = request.Band; cmbRegister.SelectedValue = request.Register; cmbGender.SelectedValue = request.Gender;
            txtInstruction.Text = request.Instruction; tabs.SelectedTab = tabGenerate;
            statusLabel.Text = "Выбранное замечание применено только к этому заданию. Генерация и каждое одобрение запускаются вручную.";
            return Task.CompletedTask;
        });
    }
    private async void CheckSource_Click(object? sender, EventArgs e)
    {
        await RunAsync("Повторно читаю исходные файлы…", async token =>
        {
            var pack = Snapshot; var fresh = await Task.Run(() => reader.Load(pack.ModuleRoot, token), token);
            txtExportLog.Text = fresh.Fingerprint == pack.Fingerprint
                ? "Отпечаток исходника не изменился. Java-validator по-прежнему NOT_RUN."
                : "ИСТОЧНИК ИЗМЕНИЛСЯ. Экспорт блокируется. Нужен новый импорт и пересмотр кандидатов.";
        });
    }
    private async void Export_Click(object? sender, EventArgs e)
    {
        await RunAsync("Собираю отдельный пакет ревью…", async token =>
        {
            var pack = Snapshot;
            if (MessageBox.Show(this, "Создать отдельный ZIP одобренных кандидатов?\r\nЭто НЕ готовое обновление сервера и НЕ установка в L2J.", "Экспорт для ревью", MessageBoxButtons.YesNo, MessageBoxIcon.Question, MessageBoxDefaultButton.Button2) != DialogResult.Yes) return;
            var path = await Task.Run(() => new ReviewExporter().Export(Store, pack, session.Candidates), token);
            txtExportLog.Text = "REVIEW_ONLY / NOT_SERVER_VALIDATED\r\nСоздан пакет ревью:\r\n" + path + "\r\n\r\nJSON и замечания; XML не экспортируется. Java-validator NOT_RUN. Не распаковывать этот пакет поверх игрового сервера.";
            statusLabel.Text = "Экспорт для ревью создан отдельно от L2J.";
        });
    }
    private void CopyWorkspace_Click(object? sender, EventArgs e)
    {
        try { Clipboard.SetText(Store.Root); statusLabel.Text = "Путь workspace скопирован."; }
        catch (Exception ex) { MessageBox.Show(this, ex.Message, "Буфер обмена", MessageBoxButtons.OK, MessageBoxIcon.Warning); }
    }
    private async void StageXml_Click(object? sender, EventArgs e)
    {
        if (!ResolvePendingEdit()) return;
        await RunAsync("Подготавливаю XML только в отдельном workspace…", async token =>
        {
            var pack = Snapshot;
            using var selection = new StageSelectionForm();
            selection.SetCandidates(session.Candidates, pack.Fingerprint);
            if (selection.ShowDialog(this) != DialogResult.OK) return;
            var ids = selection.SelectedIds;
            txtExportLog.Text = "Выбранная партия (все ID и исходные тексты):\r\n\r\n" + selection.SelectedSummary;
            // Scrollable exact-list confirmation: even 20 full texts remain available, default No.
            using var confirmation = new StageSelectionForm();
            confirmation.SetCandidates(session.Candidates, pack.Fingerprint);
            confirmation.ConfirmExactSelection(ids);
            var selectionConfirmed = confirmation.ShowDialog(this) == DialogResult.OK;
            if (!selectionConfirmed) return;
            var editorial = "Я отдельно проверил каждый выбранный текст и подтверждаю:\r\n\r\n"
                + "• нет мата и взрослого содержания;\r\n• нет оборотов, подходящих только одному полу;\r\n"
                + "• нет ложных обещаний игровых действий, лута, телепортов или несуществующей памяти;\r\n"
                + "• понимаю: НЕ ДЛЯ УСТАНОВКИ; Java и игровой runtime этим действием не проверяются.\r\n\r\nПодтвердить?";
            var editorialConfirmed = MessageBox.Show(this, editorial, "Отдельная редакционная аттестация", MessageBoxButtons.YesNo, MessageBoxIcon.Warning, MessageBoxDefaultButton.Button2) == DialogResult.Yes;
            if (!editorialConfirmed) return;
            var exactIds = StageBatchSelection.SelectExactIds(session.Candidates, ids);
            var result = await Task.Run(() => new IsolatedPackStager().Create(Store, pack, session.Candidates, exactIds, selectionConfirmed, editorialConfirmed, token), token);
            txtExportLog.Text = $"{result.Status} / Java NOT_RUN\r\nНЕ ДЛЯ УСТАНОВКИ\r\n\r\n{result.Root}\r\n\r\n"
                + "Создана отдельная физическая копия humanized-файлов с предложением custom XML. Исходный Semantic Pack не изменён. Java проверяется отдельно операторским скриптом; approval и session сохранены.";
            statusLabel.Text = "STAGED_UNVALIDATED. XML-предложение только в workspace, НЕ ДЛЯ УСТАНОВКИ.";
        });
    }
    private void Cancel_Click(object? sender, EventArgs e) { operation?.Cancel(); evidenceRefresh?.Cancel(); }
    private void PackQuality_Click(object? sender, EventArgs e)
    {
        if (!ready || busy) return;
        if (snapshot == null) { statusLabel.Text = "Сначала импортируйте Semantic Pack."; return; }
        using var form = new PackQualityForm(); form.SetPack(Snapshot); form.ShowDialog(this);
    }
    private void V3Proposal_Click(object? sender, EventArgs e)
    {
        if (!ready || busy || !ResolvePendingEdit()) return;
        if (snapshot == null) { statusLabel.Text = "Сначала импортируйте Semantic Pack."; return; }
        using var form = new V3ProposalForm(); form.SetContext(Store, Snapshot, session.Candidates); form.ShowDialog(this);
    }
    private void ChatCorpus_Click(object? sender, EventArgs e)
    {
        if (!ready || busy) return;
        using var form = new ChatCorpusForm(); form.SetWorkspace(Store); form.SetTransferMode(snapshot != null);
        if (form.ShowDialog(this) != DialogResult.OK || snapshot == null) return;
        using var lab = new DialogueLabForm(); lab.SetWorkspace(Store); lab.SetPack(Snapshot);
        lab.SetModel(Model, ReadSettings(), txtApiKey.Text); lab.SetExcerpts(form.SelectedExcerpts); lab.ShowDialog(this);
        session = Store.LoadSession(); BindLessons();
    }
    private void DialogueLab_Click(object? sender, EventArgs e)
    {
        if (!ready || busy) return;
        if (snapshot == null) { statusLabel.Text = "Сначала импортируйте Semantic Pack на вкладке Настройки."; return; }
        using var form = new DialogueLabForm(); form.SetWorkspace(Store); form.SetPack(Snapshot);
        form.SetModel(Model, ReadSettings(), txtApiKey.Text); form.ShowDialog(this);
        session = Store.LoadSession(); BindLessons();
    }
    private async Task RunAsync(string caption, Func<CancellationToken, Task> action)
    {
        if (busy) return;
        CancelEvidenceRefresh();
        busy = true; operation = new CancellationTokenSource(); tabs.Enabled = false; btnCancel.Enabled = true; UseWaitCursor = true; statusLabel.Text = caption;
        try { await action(operation.Token); }
        catch (LmStudioException ex)
        {
            statusLabel.Text = ex.Diagnostic.ToString();
            txtSettingsResult.Text = ex.Diagnostic.ToString();
            MessageBox.Show(this, ex.Diagnostic.ToString(), "LM Studio — диагностика", MessageBoxButtons.OK, MessageBoxIcon.Warning);
        }
        catch (OperationCanceledException) { statusLabel.Text = "Операция отменена или истёк таймаут. Автоматического повтора нет."; }
        catch (Exception ex)
        {
            statusLabel.Text = "Операция не завершена: " + ex.Message;
            MessageBox.Show(this, ex.Message, "Phantom Semantic Studio", MessageBoxButtons.OK, MessageBoxIcon.Warning);
        }
        finally
        {
            operation.Dispose(); operation = null; busy = false; tabs.Enabled = true; btnCancel.Enabled = evidenceRefresh != null; UseWaitCursor = false;
        }
    }
    private void MainForm_FormClosing(object? sender, FormClosingEventArgs e)
    {
        if (busy) { operation?.Cancel(); e.Cancel = true; statusLabel.Text = "Отмена запрошена. Закройте окно повторно после завершения операции."; return; }
        e.Cancel = !ResolvePendingEdit();
        if (!e.Cancel) CancelEvidenceRefresh();
    }
    private void MainForm_FormClosed(object? sender, FormClosedEventArgs e)
    {
        CancelEvidenceRefresh(); operation?.Dispose(); http?.Dispose(); store?.Dispose();
    }
}
