using PhantomSemanticStudio.Core;

namespace PhantomSemanticStudio.WinForms;

public partial class DialogueLabForm : Form
{
    private PackSnapshot? pack;
    private DialogueLabSession? session;
    private WorkspaceStore? workspace;
    private CancellationTokenSource? operation;
    private long viewVersion;
    private bool binding;
    private LmStudioClient? model;
    private StudioSettings? modelSettings;
    private string apiKey = "";
    private long mentorPreviewRevision = -1;
    private MentorAdvice? lastAdvice;
    private bool noteDirty;
    private IReadOnlyList<SanitizedCorpusExcerpt> excerpts = Array.Empty<SanitizedCorpusExcerpt>();
    private readonly HashSet<string> corpusSelected = new(StringComparer.Ordinal);
    private readonly Dictionary<string, string> corpusEdits = new(StringComparer.Ordinal);
    private readonly Dictionary<string, string> corpusLanguage = new(StringComparer.Ordinal);
    private CorpusTransformResult? lastCorpusResult;
    public DialogueLabForm()
    {
        InitializeComponent();
    }
    public void SetWorkspace(WorkspaceStore store) => workspace = store;
    public void SetModel(LmStudioClient client, StudioSettings settings, string key)
    {
        model = client; modelSettings = settings; apiKey = key; chkMentor.Enabled = true;
        UpdateActions();
    }
    public void SetExcerpts(IReadOnlyList<SanitizedCorpusExcerpt> selected)
    {
        if (operation != null || selected.Count is < 1 or > 20 || selected.Select(s => s.CorpusId + "/" + s.RowId).Distinct(StringComparer.Ordinal).Count() != selected.Count
            || selected.Any(s => s.Status != "SOURCE_MATERIAL_ONLY" || s.PreviewHash != TextRules.Hash(s.Preview)))
            throw new InvalidDataException("Нужны 1–20 явно подтверждённых sanitized public фрагментов.");
        excerpts = Array.AsReadOnly(selected.ToArray()); corpusSelected.Clear(); corpusEdits.Clear(); corpusLanguage.Clear();
        binding = true;
        try
        {
            listExcerpts.Items.Clear();
            foreach (var excerpt in excerpts)
            {
                corpusEdits[excerpt.RowId] = excerpt.Preview;
                corpusLanguage[excerpt.RowId] = excerpt.Language == "LATIN_TRANSLIT_CANDIDATE" ? "RU_TRANSLIT" : excerpt.Language == "EN_OR_OTHER" ? "OTHER_LANGUAGE" : "UNKNOWN";
                var item = new ListViewItem(excerpt.RowId[..8]) { Tag = excerpt };
                item.SubItems.Add(excerpt.Language); item.SubItems.Add(excerpt.Channel); listExcerpts.Items.Add(item);
            }
            txtExcerpt.Text = string.Join("\r\n\r\n", excerpts.Select(e => $"{e.Status} • {e.CorpusId}/{e.RowId}\r\n{e.Timestamp} / timezone {e.Timezone} • {e.Language}\r\n{e.Preview}"));
            txtShareText.Clear();
        }
        finally { binding = false; }
        CorpusChanged(); UpdateCorpusPreview(); tabsLab.SelectedTab = tabCorpus; UpdateActions();
    }
    public void SetPack(PackSnapshot snapshot)
    {
        if (operation != null) throw new InvalidOperationException("Дождитесь отмены текущей операции.");
        pack = snapshot; session = new DialogueLabSession(snapshot);
        lblSource.Text = "PACK_CATALOG_APPROXIMATE / NOT_JAVA_RUNTIME_PARITY\r\nSource fingerprint: " + snapshot.Fingerprint;
        binding = true;
        try
        {
            cmbAct.Items.Clear(); cmbAct.Items.AddRange(snapshot.Acts.Where(a => !TextRules.IsFunctionalAct(a)
                && snapshot.Entries.Any(p => p.Kind == "PATTERN" && p.Act == a)).ToArray());
            if (cmbAct.Items.Count > 0) cmbAct.SelectedIndex = 0;
            FillTopics();
        }
        finally { binding = false; }
        btnSend.Enabled = true; btnClear.Enabled = true; Render();
    }
    private DialogueLabSession Lab => session ?? throw new InvalidOperationException("Сначала импортируйте Semantic Pack.");
    private PackSnapshot Pack => pack ?? throw new InvalidOperationException("Нет импортированного источника.");
    private async Task<PackSnapshot> ReadCurrentAsync(CancellationToken token)
    {
        try
        {
            var current = await Task.Run(() => new PackReader().Load(Pack.ModuleRoot, token), token);
            token.ThrowIfCancellationRequested(); Lab.AssertCurrent(current); return current;
        }
        catch (Exception ex) when (ex is IOException or InvalidDataException or UnauthorizedAccessException)
        { Lab.MarkSourceStale(); throw new InvalidDataException("STALE_SOURCE: источник не прошёл повторное чтение. Требуется новый импорт."); }
    }
    private async void Send_Click(object? sender, EventArgs e)
    {
        var input = txtInput.Text; var band = cmbBand.Text; var register = cmbRegister.Text; var version = viewVersion;
        await RunAsync(async token =>
        {
            await ReadCurrentAsync(token);
            var proposal = await Task.Run(() => Lab.PrepareTurn(input, band, register, token), token);
            var current = await ReadCurrentAsync(token); EnsureView(version, token);
            Lab.CommitTurn(proposal, current, token);
            binding = true; try { txtInput.Clear(); } finally { binding = false; }
            Render(); lblStatus.Text = Lab.Turns.Last().Ambiguous
                ? "Контекст UNKNOWN: возможен вопрос отдельному наставнику после opt-in. Ответ пака не заменяется."
                : "Каталог проверен. WORLD_ADVISORY_ONLY; Java runtime не выполнялся.";
        });
    }
    private void EnsureView(long version, CancellationToken token)
    {
        token.ThrowIfCancellationRequested();
        if (IsDisposed || Disposing || version != viewVersion) throw new OperationCanceledException(token);
    }
    private void Input_Changed(object? sender, EventArgs e)
    {
        if (binding) return;
        viewVersion++; operation?.Cancel(); session?.InvalidateContext(); MarkAdviceStale();
    }
    private void Context_Changed(object? sender, EventArgs e)
    {
        if (binding) return;
        chkScopeReviewed.Checked = false;
        viewVersion++; operation?.Cancel();
        session?.SetWorld(cmbWorld.Text);
        session?.InvalidateContext(); MarkAdviceStale();
    }
    private void Turn_Selected(object? sender, EventArgs e)
    {
        if (listTurns.SelectedItem is not DialogueTurn turn) { txtTrace.Clear(); return; }
        txtTrace.Text = $"{turn.PackStatus}\r\nPatternId: {turn.PatternId}\r\nTemplateId: {turn.TemplateId}\r\nAct: {turn.Act}\r\nTopic: {turn.Topic}\r\n"
            + $"Input context: {turn.InputWorldHint}; hypothesis: {turn.WorldHint}\r\nFingerprint: {turn.PackFingerprint}\r\n\r\n{turn.Notes}";
    }
    private void Render()
    {
        if (session == null) return;
        txtTranscript.Text = Lab.Transcript;
        listTurns.DisplayMember = "UserText"; listTurns.DataSource = Lab.Turns.ToList();
        listTurns.SelectedIndex = listTurns.Items.Count - 1; Turn_Selected(this, EventArgs.Empty);
    }
    private async void Clear_Click(object? sender, EventArgs e)
    {
        if (operation != null) { operation.Cancel(); return; }
        if (!await ResolveNoteAsync()) return;
        Lab.Clear(); viewVersion++;
        binding = true; try { cmbWorld.SelectedIndex = 0; chkMentor.Checked = false; } finally { binding = false; }
        txtMentorPreview.Clear(); txtAdvice.Clear(); txtClarification.Clear(); lastAdvice = null;
        chkContextReviewed.Checked = false; mentorPreviewRevision = -1;
        Render(); UpdateActions(); lblStatus.Text = "Эфемерная история очищена. Source gate и лимит model calls сохраняются.";
    }
    private void Cancel_Click(object? sender, EventArgs e) { viewVersion++; operation?.Cancel(); }
    private async Task RunAsync(Func<CancellationToken, Task> action)
    {
        if (operation != null) return;
        using var cancel = new CancellationTokenSource(); operation = cancel;
        btnCancel.Enabled = true; btnSend.Enabled = false; btnClear.Enabled = false; UpdateActions();
        lblStatus.Text = "Локальная операция… отмена сохраняет ввод.";
        try { await action(cancel.Token); }
        catch (OperationCanceledException) { lblStatus.Text = "CANCELLED / STALE: результат не принят; ввод сохранён."; }
        catch (LmStudioException ex) { lblStatus.Text = "BLOCKED_LM / " + ex.Diagnostic; }
        catch (Exception ex) when (ex is InvalidDataException or IOException or UnauthorizedAccessException or InvalidOperationException)
        { lblStatus.Text = "BLOCKED: " + (Lab.SourceStatus == "STALE_SOURCE" ? "STALE_SOURCE — требуется новый импорт." : "Проверьте текст, scope и лимиты; ничего не применено."); }
        finally { operation = null; btnCancel.Enabled = false; btnSend.Enabled = session != null && Lab.SourceStatus == "CURRENT_SOURCE"; btnClear.Enabled = session != null; UpdateActions(); }
    }
    private async void Lab_Closing(object? sender, FormClosingEventArgs e)
    {
        if (operation != null) { operation.Cancel(); e.Cancel = true; lblStatus.Text = "Отмена запрошена; закройте после завершения операции."; return; }
        if (!noteDirty) return;
        e.Cancel = true;
        if (await ResolveNoteAsync()) Close();
    }
    private void Mentor_Changed(object? sender, EventArgs e)
    {
        if (binding || session == null) return;
        viewVersion++; operation?.Cancel(); Lab.SetMentorEnabled(chkMentor.Checked); chkContextReviewed.Checked = false;
        MarkAdviceStale(); UpdateActions();
    }
    private void MarkAdviceStale()
    {
        if (session?.AdviceStatus == "STALE" && txtAdvice.TextLength > 0 && !txtAdvice.Text.StartsWith("STALE", StringComparison.Ordinal))
            txtAdvice.Text = "STALE — контекст изменён; совет не применяется.\r\n" + txtAdvice.Text;
        if (session?.CorpusAdviceStatus == "STALE" && txtCorpusAdvice.TextLength > 0 && !txtCorpusAdvice.Text.StartsWith("STALE", StringComparison.Ordinal))
            txtCorpusAdvice.Text = "STALE — фрагменты/контекст изменены; предложение не применяется.\r\n" + txtCorpusAdvice.Text;
        UpdateActions();
    }
    private async void MentorPreview_Click(object? sender, EventArgs e)
    {
        await RunAsync(async token =>
        {
            await ReadCurrentAsync(token);
            binding = true; try { txtMentorPreview.Text = Lab.MentorPreview(); } finally { binding = false; }
            mentorPreviewRevision = Lab.Revision; chkContextReviewed.Checked = false;
            lblStatus.Text = "Проверьте и замаскируйте точный контекст (до 10 реплик / 8 KiB). POST ещё не выполнен.";
        });
    }
    private void MentorPreview_Changed(object? sender, EventArgs e)
    {
        if (binding) return;
        chkContextReviewed.Checked = false; viewVersion++; operation?.Cancel();
    }
    private void ReviewPermission_Changed(object? sender, EventArgs e)
    {
        if (binding || operation == null || sender is not CheckBox { Checked: false }) return;
        viewVersion++; operation.Cancel(); session?.InvalidateContext(); MarkAdviceStale();
    }
    private async void AskMentor_Click(object? sender, EventArgs e)
    {
        var context = txtMentorPreview.Text; var version = viewVersion;
        await RunAsync(async token =>
        {
            if (model == null || modelSettings == null || mentorPreviewRevision != Lab.Revision)
                throw new InvalidDataException("BLOCKED_LM: подготовьте актуальный preview и настройте модель.");
            await ReadCurrentAsync(token); EnsureView(version, token);
            var request = Lab.BeginMentor(context, chkContextReviewed.Checked, true);
            try
            {
                var result = await model.AdviseDialogueAsync(modelSettings, apiKey, request, token);
                var current = await ReadCurrentAsync(token); EnsureView(version, token); Lab.AcceptMentor(result, current, token);
                lastAdvice = result.Advice;
                txtAdvice.Text = "MENTOR_ADVISORY_NOT_VERIFIED\r\n" + result.Advice.Question + "\r\n"
                    + string.Join("\r\n", result.Advice.Interpretations) + "\r\n" + result.Advice.Reason + "\r\n" + result.Advice.EditorialSuggestion;
                Render(); lblStatus.Text = "Один запрос MENTOR. Подтвердите интерпретацию отдельно; исходный ввод и ответы каталога сохранены.";
            }
            catch { Lab.FailMentor(request); throw; }
            finally { binding = true; try { chkContextReviewed.Checked = false; } finally { binding = false; } }
        });
    }
    private async void Confirm_Click(object? sender, EventArgs e)
    {
        var interpretation = cmbConfirmWorld.Text; var answer = txtClarification.Text; var version = viewVersion;
        await RunAsync(async token =>
        {
            var current = await ReadCurrentAsync(token); EnsureView(version, token);
            Lab.ConfirmClarification(interpretation, answer, current);
            binding = true; try { cmbWorld.SelectedItem = Lab.World; } finally { binding = false; }
            viewVersion++; Render(); lblStatus.Text = "Подтверждён только WORLD_ADVISORY_ONLY. Реплики и approval не изменены.";
        });
    }
    private void DiscardMentor_Click(object? sender, EventArgs e)
    {
        viewVersion++; operation?.Cancel(); Lab.DiscardMentor(); txtAdvice.Clear(); lastAdvice = null; UpdateActions();
    }
    private async void UseMentorNote_Click(object? sender, EventArgs e)
    {
        if (operation != null || lastAdvice == null || Lab.AdviceStatus is "STALE" or "NONE" or "BLOCKED_LM") return;
        var advice = lastAdvice; var version = viewVersion;
        if (!await ResolveNoteAsync() || version != viewVersion || !ReferenceEquals(advice, lastAdvice)
            || Lab.AdviceStatus is not ("MENTOR_ADVISORY" or "CONFIRMED_EDITOR_CONTEXT")) return;
        txtNote.Text = advice.EditorialSuggestion;
    }
    private void Note_Changed(object? sender, EventArgs e)
    {
        if (binding) return;
        noteDirty = txtNote.TextLength > 0; chkScopeReviewed.Checked = false;
    }
    private void FillTopics()
    {
        cmbTopic.Items.Clear();
        if (pack == null) return;
        cmbTopic.Items.AddRange(pack.Entries.Where(p => p.Kind == "PATTERN" && p.Act == cmbAct.Text)
            .Select(p => p.Topic).Distinct(StringComparer.Ordinal).Order(StringComparer.Ordinal).ToArray());
        if (cmbTopic.Items.Count > 0) cmbTopic.SelectedIndex = 0;
    }
    private void Act_Changed(object? sender, EventArgs e) { FillTopics(); chkScopeReviewed.Checked = false; }
    private void Scope_Changed(object? sender, EventArgs e) => chkScopeReviewed.Checked = false;
    private void AddNote_Click(object? sender, EventArgs e)
    {
        try { Lab.AddEditorNote(txtNote.Text); Render(); lblStatus.Text = "EDITOR_NOTE локальна; scoped lesson ещё не сохранён."; }
        catch (InvalidDataException) { lblStatus.Text = "Заметка пустая, превышает лимит или содержит управляющие символы."; }
    }
    private async void SaveLesson_Click(object? sender, EventArgs e) => await SaveLessonAsync();
    private async Task<bool> SaveLessonAsync()
    {
        var saved = false; var text = txtNote.Text; var topic = cmbTopic.Text; var act = cmbAct.Text; var band = cmbBand.Text;
        var register = cmbRegister.Text; var gender = cmbGender.Text; var reviewed = chkScopeReviewed.Checked; var version = viewVersion;
        await RunAsync(async token =>
        {
            if (workspace == null || !reviewed) throw new InvalidDataException("Проверьте точный текст, source и scope перед сохранением.");
            var current = await ReadCurrentAsync(token); EnsureView(version, token);
            if (!chkScopeReviewed.Checked) throw new InvalidDataException("Проверка scope отозвана.");
            var lesson = Lab.CreateLesson(text, topic, act, band, register, gender, current);
            workspace.Check(); PathSafety.AssertDisjoint(PathSafety.RepositoryBoundary(Pack.ModuleRoot), workspace.Root);
            var state = workspace.LoadSession(); token.ThrowIfCancellationRequested();
            var next = new SessionState { Candidates = state.Candidates, Lessons = state.Lessons, ScopedLessons = state.ScopedLessons.Append(lesson).ToList() };
            workspace.SaveSession(next); noteDirty = false; saved = true;
            lblStatus.Text = "Scoped lesson сохранён явно. Кандидаты и approval не менялись; генерация/XML не запускались.";
        });
        return saved;
    }
    private async Task<bool> ResolveNoteAsync()
    {
        if (!noteDirty) return true;
        var answer = MessageBox.Show(this, "Есть редакционная правка. Сохранить scoped lesson с выбранными параметрами? Для сохранения нужен checkbox проверки scope.",
            "Сохранить / Отбросить / Отмена", MessageBoxButtons.YesNoCancel, MessageBoxIcon.Question, MessageBoxDefaultButton.Button3);
        if (answer == DialogResult.Cancel) return false;
        if (answer == DialogResult.Yes) return await SaveLessonAsync();
        binding = true; try { txtNote.Clear(); noteDirty = false; } finally { binding = false; }
        return true;
    }
    private async void SaveHistory_Click(object? sender, EventArgs e)
    {
        if (!await ResolveNoteAsync()) return;
        await RunAsync(async token =>
        {
            if (workspace == null) throw new InvalidDataException("Workspace не открыт.");
            var current = await ReadCurrentAsync(token);
            await Task.Run(() => DialogueLabStore.Save(workspace, Lab, current, token), token);
            lblStatus.Text = "PRIVATE_EDITOR_HISTORY сохранена явно в собственном workspace/labs. API token не сохраняется.";
        });
    }
    private void UpdateActions()
    {
        if (IsDisposed || Disposing) return;
        var idle = operation == null && session != null && session.SourceStatus == "CURRENT_SOURCE";
        btnMentorPreview.Enabled = idle; btnAskMentor.Enabled = idle && model != null && chkMentor.Checked;
        btnConfirm.Enabled = idle && session?.PendingClarification != null; btnDiscardMentor.Enabled = idle;
        btnUseMentorNote.Enabled = idle && lastAdvice != null && session?.AdviceStatus is "MENTOR_ADVISORY" or "CONFIRMED_EDITOR_CONTEXT";
        btnSaveLesson.Enabled = idle && workspace != null; btnSaveHistory.Enabled = idle && workspace != null;
        btnAddNote.Enabled = idle; txtNote.ReadOnly = !idle; cmbAct.Enabled = idle; cmbTopic.Enabled = idle; cmbGender.Enabled = idle;
        btnChooseCorpus.Enabled = idle && workspace != null; btnTranslate.Enabled = idle && model != null;
        btnUseSuggestion.Enabled = idle && session?.CorpusAdviceStatus == "SOURCE_MATERIAL_ADVISORY_NOT_VERIFIED" && lastCorpusResult != null;
    }
    private async void ChooseCorpus_Click(object? sender, EventArgs e)
    {
        if (operation != null || workspace == null) return;
        using var form = new ChatCorpusForm(); form.SetWorkspace(workspace); form.SetTransferMode(true);
        if (form.ShowDialog(this) == DialogResult.OK) SetExcerpts(form.SelectedExcerpts);
        await Task.CompletedTask;
    }
    private SanitizedCorpusExcerpt? SelectedExcerpt => listExcerpts.SelectedItems.Count == 0 ? null : listExcerpts.SelectedItems[0].Tag as SanitizedCorpusExcerpt;
    private string OverrideKey => cmbLanguageOverride.SelectedIndex switch { 1 => "RU_TRANSLIT", 2 => "OTHER_LANGUAGE", _ => "UNKNOWN" };
    private void Excerpt_Selected(object? sender, EventArgs e)
    {
        var excerpt = SelectedExcerpt; if (binding || excerpt == null) return;
        binding = true;
        try
        {
            txtShareText.Text = corpusEdits[excerpt.RowId];
            cmbLanguageOverride.SelectedIndex = corpusLanguage[excerpt.RowId] switch { "RU_TRANSLIT" => 1, "OTHER_LANGUAGE" => 2, _ => 0 };
        }
        finally { binding = false; }
    }
    private void Excerpt_Checked(object? sender, ItemCheckEventArgs e)
    {
        if (binding || listExcerpts.Items[e.Index].Tag is not SanitizedCorpusExcerpt excerpt) return;
        if (e.NewValue == CheckState.Checked)
        {
            if (corpusSelected.Count >= 3) { e.NewValue = CheckState.Unchecked; lblStatus.Text = "Предел 3 фрагмента на один запрос. Снимите отметку вручную."; return; }
            corpusSelected.Add(excerpt.RowId);
        }
        else corpusSelected.Remove(excerpt.RowId);
        CorpusChanged(); UpdateCorpusPreview();
    }
    private void CorpusText_Changed(object? sender, EventArgs e)
    {
        if (binding || SelectedExcerpt is not { } excerpt) return;
        corpusEdits[excerpt.RowId] = txtShareText.Text; corpusLanguage[excerpt.RowId] = OverrideKey;
        CorpusChanged(); UpdateCorpusPreview();
    }
    private void Transform_Changed(object? sender, EventArgs e) { if (!binding) CorpusChanged(); }
    private void CorpusChanged()
    {
        viewVersion++; operation?.Cancel(); session?.InvalidateContext();
        chkPiiReviewed.Checked = false; chkShareCorpus.Checked = false; MarkAdviceStale();
    }
    private void UpdateCorpusPreview()
    {
        txtModelPreview.Text = string.Join("\r\n\r\n", excerpts.Where(e => corpusSelected.Contains(e.RowId)).Select((e, i) =>
            $"e{i + 1} • {corpusLanguage[e.RowId]} • SOURCE_MATERIAL_ONLY\r\n{corpusEdits[e.RowId]}"));
    }
    private async void Translate_Click(object? sender, EventArgs e)
    {
        var version = viewVersion;
        var action = cmbTransform.Text;
        await RunAsync(async token =>
        {
            if (model == null || modelSettings == null) throw new InvalidDataException("BLOCKED_LM: модель не настроена.");
            var reviewed = excerpts.Where(e => corpusSelected.Contains(e.RowId))
                .Select(e => CorpusDialogueBridge.Review(e, corpusEdits[e.RowId], corpusLanguage[e.RowId])).ToArray();
            await ReadCurrentAsync(token); EnsureView(version, token);
            var request = Lab.BeginCorpusTransform(reviewed, chkPiiReviewed.Checked, chkShareCorpus.Checked, action);
            try
            {
                var result = await model.TransformCorpusAsync(modelSettings, apiKey, request, token);
                var current = await ReadCurrentAsync(token); EnsureView(version, token); Lab.AcceptCorpusTransform(result, current, token);
                lastCorpusResult = result; listSuggestions.DisplayMember = "RefKey"; listSuggestions.DataSource = result.Suggestions.ToList();
                Suggestion_Selected(this, EventArgs.Empty);
                lblStatus.Text = "Один отдельный corpus POST. AI-предложения только черновые заметки; corpus/пак не изменены.";
            }
            catch { Lab.FailCorpusTransform(request); throw; }
            finally { binding = true; try { chkPiiReviewed.Checked = false; chkShareCorpus.Checked = false; } finally { binding = false; } }
        });
    }
    private void Suggestion_Selected(object? sender, EventArgs e)
    {
        if (listSuggestions.SelectedItem is not CorpusSuggestion suggestion) { txtCorpusAdvice.Clear(); return; }
        txtCorpusAdvice.Text = $"SOURCE_MATERIAL_ADVISORY_NOT_VERIFIED / {Lab.CorpusAdviceStatus}\r\n{suggestion.RefKey} / reviewed hash {suggestion.ReviewedTextHash}\r\n"
            + $"{suggestion.Operation} • {suggestion.LanguageAssessment} • confidence {suggestion.Confidence}/100 (мнение AI)\r\n{suggestion.ProposedRussianText}\r\n{suggestion.Reason}";
    }
    private async void UseSuggestion_Click(object? sender, EventArgs e)
    {
        if (operation != null || Lab.CorpusAdviceStatus != "SOURCE_MATERIAL_ADVISORY_NOT_VERIFIED" || lastCorpusResult == null || listSuggestions.SelectedItem is not CorpusSuggestion suggestion) return;
        var result = lastCorpusResult; var version = viewVersion;
        if (!await ResolveNoteAsync() || version != viewVersion || !ReferenceEquals(result, lastCorpusResult)
            || !ReferenceEquals(suggestion, listSuggestions.SelectedItem) || Lab.CorpusAdviceStatus != "SOURCE_MATERIAL_ADVISORY_NOT_VERIFIED") return;
        var index = result.Suggestions.ToList().FindIndex(s => s.RefKey == suggestion.RefKey);
        if (index < 0) return;
        var source = result.Request.Items[index];
        txtNote.Text = $"SOURCE_MATERIAL_ONLY / AI_ADVISORY_NOT_VERIFIED\r\n{source.Source.CorpusId}/{source.Source.RowId}\r\n"
            + $"Source hash: {source.Source.SourceHash}; reviewed hash: {source.ReviewedHash}\r\nПроверенная цитата: {source.Text}\r\n"
            + $"{suggestion.Operation}: {suggestion.ProposedRussianText}\r\n{suggestion.Reason}";
        tabsLab.SelectedTab = tabMentor;
    }
}
