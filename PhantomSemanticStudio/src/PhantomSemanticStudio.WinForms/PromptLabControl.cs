using System.ComponentModel;
using System.Diagnostics;
using System.Globalization;
using System.Text.Json;
using PhantomSemanticStudio.Core;

namespace PhantomSemanticStudio.WinForms;

public partial class PromptLabControl : UserControl
{
    private PromptLabStore? store;
    private PromptLabSession? lab;
    private Func<StudioSettings>? settingsProvider;
    private Func<string>? keyProvider;
    private PromptLabVersion? current;
    private PromptLabRun? leftRun, rightRun;
    private BindingList<RowView> leftRows = new(), rightRows = new();
    private BindingList<ScopeView> scopeRows = new();
    private CancellationTokenSource? operation;
    private bool binding, dirty, feedbackDirty;
    private int activeScopeIndex;
    private PromptLabPackPreview? packPreview;
    private readonly Stopwatch elapsed = new();
    private sealed record Choice(string Id, string Label);
    public event EventHandler? BusyChanged;
    public bool Busy => operation != null;
    private PromptLabStore Store => store ?? throw new InvalidOperationException("Workspace лаборатории не открыт.");
    private PromptLabSession Lab => lab ?? throw new InvalidOperationException("Лаборатория не готова.");

    public PromptLabControl()
    {
        InitializeComponent();
    }

    public void SetContext(WorkspaceStore workspace, Func<StudioSettings> settings, Func<string> key)
    {
        store = new PromptLabStore(workspace); lab = new PromptLabSession(store); settingsProvider = settings; keyProvider = key;
        BindingContext ??= new BindingContext();
        txtModel.Text = settings().ModelId;
        colCategory.DisplayMember = "Label"; colCategory.ValueMember = "Id";
        colCategory.DataSource = Store.EffectiveRegistry().Categories.Select(c => new Choice(c.Key, c.Label + " · " + c.Key)).ToArray();
        colVoice.DisplayMember = "Label"; colVoice.ValueMember = "Id";
        colVoice.DataSource = new[] { new Choice("ANY", "Любой голос — запасной ответ") }.Concat(V4Registry.Default.Voices.Select(v => new Choice(v.Key, v.Label))).ToArray();
        colMode.DataSource = V4Registry.Modes.ToArray(); colRelation.DataSource = V4Registry.Relations.ToArray();
        colStyle.DataSource = PromptProfiles.Default.Styles.Select(s => s.Id).ToArray();
        colMaturity.DataSource = new[] { "GENERAL", "MATURE" }; colProfanity.DataSource = V4Registry.Profanities.ToArray();
        colChannel.DataSource = new[] { "ALL", "PARTY", "CLAN", "TRADE", "SHOUT", "TELL", "BOT_PRIVATE" };
        colStance.DataSource = new[] { "" }.Concat(V4Registry.Stances).ToArray(); colTone.DataSource = new[] { "" }.Concat(V4Registry.Tones).ToArray();
        colEvent.DataSource = new[] { "" }.Concat(V4Registry.Default.Events.Select(e => e.Code)).ToArray();
        cboProfiles.DisplayMember = cboVersions.DisplayMember = cboRuns.DisplayMember = "Label";
        cboProfiles.ValueMember = cboVersions.ValueMember = cboRuns.ValueMember = "Id";
        if (Store.ListProfiles().Count == 0)
        {
            foreach (var strategy in PromptProfiles.Strategies)
                Lab.CreateProfile(strategy switch { "situation-first" => "Ситуация и реакция", "persona-first" => "Характер и отношения", "dialogue-context" => "Живой диалог", _ => "Грубость и взрослый юмор" }, strategy, new PromptLabVersion().Scenario);
        }
        var existingStyles = Store.ListProfiles().Select(profile => Store.GetVersion(profile.SelectedVersionId).Scenario.Style.StyleId).ToHashSet(StringComparer.Ordinal);
        foreach (var style in new[] { "strong_profanity", "vulgar_humor", "double_meanings", "adult_flirt", "intimate_conversation" })
            if (!existingStyles.Contains(style)) Lab.CreateSpeechProfile(style);
        LoadProfiles(); UpdateActions();
    }

    private void LoadProfiles(string? profileId = null, string? versionId = null)
    {
        binding = true;
        try
        {
            var choices = Store.ListProfiles().Select(p => new Choice(p.Id, p.Name)).ToArray(); cboProfiles.DataSource = choices;
            if (profileId != null) cboProfiles.SelectedValue = profileId;
            else
            {
                var ordinary = choices.FirstOrDefault(choice =>
                {
                    var selected = Store.GetVersion(Store.GetProfile(choice.Id).SelectedVersionId);
                    return selected.ReadyForGeneration && selected.Scenario.Style.StyleId == "ordinary" && !selected.Scenario.Style.MatureRequested && !selected.Scenario.Style.StrongProfanityRequested;
                });
                if (ordinary != null) cboProfiles.SelectedValue = ordinary.Id;
            }
        }
        finally { binding = false; }
        if (cboProfiles.SelectedItem is Choice selected) LoadVersions(selected.Id, versionId);
    }

    private void LoadVersions(string profileId, string? versionId = null)
    {
        var profile = Store.GetProfile(profileId); binding = true;
        try
        {
            cboVersions.DataSource = Store.ListVersions(profileId).Select(v => new Choice(v.Id,
                v.CreatedAtUtc.ToLocalTime().ToString("dd.MM HH:mm:ss") + " · " + v.Id[..8] + (v.Id == profile.AcceptedVersionId ? " · принята" : v.Origin == "MODEL_PROPOSAL" ? " · предложение" : ""))).ToArray();
            cboVersions.SelectedValue = versionId ?? profile.SelectedVersionId;
        }
        finally { binding = false; }
        if (cboVersions.SelectedItem is Choice selected) LoadVersion(selected.Id);
    }

    private void LoadVersion(string id)
    {
        var saved = Store.GetVersion(id);
        if (current != null && current.ProfileId != saved.ProfileId) ClearResults();
        current = saved;
        var draft = Store.GetDraft(saved.Id) ?? Store.GetDraft(saved.ProfileId); var display = draft?.Id == id ? draft : saved;
        binding = true;
        try
        {
            txtName.Text = display.Name; txtSystem.Text = display.System; txtUser.Text = display.User;
            cboContextPresentation.SelectedIndex = display.ContextPresentation == "READABLE_SCENE" ? 1 : 0;
            txtDescription.Text = display.Description;
            colCategory.DataSource = Store.EffectiveRegistry().Categories.Select(c => new Choice(c.Key, c.Label + " · " + c.Key))
                .Concat(display.CategorySuggestions.Where(s => !Store.EffectiveRegistry().Categories.Any(c => c.Key == s.Key)).Select(s => new Choice(s.Key, "На утверждение: " + s.Label + " · " + s.Key))).ToArray();
            scopeRows = new(new[] { display.Scenario }.Concat(display.AdditionalScenarios).Select((scope, index) => ScopeView.From(scope,
                display.CategoryInstructions.Count > index ? display.CategoryInstructions[index] : null)).ToList()); gridScope.DataSource = scopeRows;
            activeScopeIndex = 0; LoadActiveScene();
            FillParameters(display.Generation, display.Refinement);
            var accepted = Store.GetProfile(saved.ProfileId).AcceptedVersionId;
            lblVersions.Text = "Открыта " + saved.Id[..8] + "; принята: " + (accepted.Length == 0 ? "нет" : accepted[..8]) + ". Экспериментальные реплики не утверждаются автоматически.";
            if (!saved.ReadyForGeneration) lblVersions.Text += " Требуется проверка плана партий.";
            lblSystem.Text = display.Scenario.Components is { } component ? "SYSTEM семейства " + component.FamilyId + " / " + component.FamilyRevision : "SYSTEM — характер и правила речи";
            if (display.CategorySuggestions.Count > 0) txtPackReport.Text = "Предложения категорий требуют отдельного утверждения:\r\n" + JsonSerializer.Serialize(display.CategorySuggestions, WorkspaceStore.JsonOptions);
            dirty = draft?.Id == id;
        }
        finally { binding = false; }
        LoadHistory(); UpdateActions();
    }

    private PromptLabVersion ReadEditor()
    {
        if (current == null || scopeRows.Count == 0) throw new InvalidDataException("Создайте или выберите профиль.");
        gridScope.EndEdit(); gridParameters.EndEdit(); SaveActiveScene(); var scopes = scopeRows.Select(r => r.ToRequest()).ToArray();
        return current with { Name = txtName.Text, System = txtSystem.Text, User = txtUser.Text, Scenario = scopes[0], AdditionalScenarios = scopes.Skip(1).ToArray(),
            ContextPresentation = cboContextPresentation.SelectedIndex == 1 ? "READABLE_SCENE" : null,
            CategoryInstructions = scopeRows.Select(row => row.CategoryInstruction).ToArray(), Generation = ReadOptions(1), Refinement = ReadOptions(2), Description = txtDescription.Text };
    }

    private void SaveActiveScene()
    {
        if (activeScopeIndex < 0 || activeScopeIndex >= scopeRows.Count) return;
        var row = scopeRows[activeScopeIndex]; row.Situation = txtSituation.Text; row.Dialogue = txtDialogue.Lines.Where(t => !string.IsNullOrWhiteSpace(t)).ToArray(); row.Adults = chkAdult.Checked;
        row.CategoryInstruction = txtCategoryInstruction.Text;
    }
    private void LoadActiveScene()
    {
        if (activeScopeIndex < 0 || activeScopeIndex >= scopeRows.Count) return;
        var previous = binding; binding = true;
        try { var row = scopeRows[activeScopeIndex]; txtSituation.Text = row.Situation; txtDialogue.Text = string.Join(Environment.NewLine, row.Dialogue); chkAdult.Checked = row.Adults;
            txtCategoryInstruction.Text = row.CategoryInstruction; txtCategoryInstruction.Enabled = row.IsComponent; }
        finally { binding = previous; }
    }
    private void Scope_SelectionChanged(object? sender, EventArgs e)
    {
        if (binding || gridScope.CurrentRow == null) return;
        SaveActiveScene(); activeScopeIndex = gridScope.CurrentRow.Index; LoadActiveScene();
    }

    private PromptLabVersion SaveEditor()
    {
        if (!dirty && current != null) return current;
        var draft = Lab.PrepareVersion(ReadEditor()); var saved = Store.SaveVersion(draft with { Id = "", ParentId = current!.Id, Origin = "MANUAL", ProposedFromRunId = "" });
        DiscardEditorDraft(current!); dirty = false; current = saved; LoadProfiles(saved.ProfileId, saved.Id); return saved;
    }
    private void DiscardEditorDraft(PromptLabVersion version)
    {
        Store.DiscardDraft(version.Id);
        if (Store.GetDraft(version.ProfileId)?.Id == version.Id) Store.DiscardDraft(version.ProfileId);
    }

    private void FillParameters(PromptLabApiOptions generation, PromptLabApiOptions refinement)
    {
        gridParameters.Rows.Clear();
        var labels = new[] { "API: OPENAI_COMPAT / LM_NATIVE", "Reasoning: off / on / auto", "Температура", "top_p", "top_k", "min_p — только native, пусто = не передавать", "Repeat penalty", "Выходные токены, включая reasoning", "Подключение, секунд", "Idle timeout, секунд", "Overall timeout, секунд", "Streaming: true / false" };
        var left = Values(generation); var right = Values(refinement);
        for (var i = 0; i < labels.Length; i++) gridParameters.Rows.Add(labels[i], left[i], right[i]);
    }

    private static string[] Values(PromptLabApiOptions options) => [options.Transport, options.Reasoning, Number(options.Temperature), Number(options.TopP), options.TopK.ToString(),
        options.MinP.HasValue ? Number(options.MinP.Value) : "", Number(options.RepeatPenalty), options.MaxOutputTokens.ToString(), options.ConnectionTimeoutSeconds.ToString(),
        options.IdleTimeoutSeconds.ToString(), options.OverallTimeoutSeconds.ToString(), options.Stream.ToString().ToLowerInvariant()];
    private static string Number(double value) => value.ToString("0.###", CultureInfo.InvariantCulture);
    private PromptLabApiOptions ReadOptions(int column)
    {
        string Value(int row) => Convert.ToString(gridParameters.Rows[row].Cells[column].Value, CultureInfo.InvariantCulture)?.Trim() ?? "";
        double Double(int row) => double.TryParse(Value(row).Replace(',', '.'), NumberStyles.Float, CultureInfo.InvariantCulture, out var result) ? result : throw new InvalidDataException("Неверное число: " + gridParameters.Rows[row].Cells[0].Value);
        int Integer(int row) => int.TryParse(Value(row), out var result) ? result : throw new InvalidDataException("Нужно целое число: " + gridParameters.Rows[row].Cells[0].Value);
        var options = new PromptLabApiOptions { Transport = Value(0), Reasoning = Value(1), Temperature = Double(2), TopP = Double(3), TopK = Integer(4),
            MinP = Value(5).Length == 0 ? null : Double(5), RepeatPenalty = Double(6), MaxOutputTokens = Integer(7), ConnectionTimeoutSeconds = Integer(8),
            IdleTimeoutSeconds = Integer(9), OverallTimeoutSeconds = Integer(10), Stream = bool.TryParse(Value(11), out var stream) ? stream : throw new InvalidDataException("Streaming: true или false.") };
        options.Validate(); return options;
    }

    private void LoadHistory()
    {
        if (current == null) return;
        binding = true;
        try { cboRuns.DataSource = Store.ListRuns(current.ProfileId).Select(r => new Choice(r.Id, r.CreatedAtUtc.ToLocalTime().ToString("dd.MM HH:mm:ss") + " · " + r.Purpose + " · " + r.State + " · " + r.Id[..8])).ToArray(); }
        finally { binding = false; }
    }

    private void ShowRun(PromptLabRun run, bool right)
    {
        var owner = Store.GetVersion(run.VersionId);
        if (owner.ProfileId != current?.ProfileId) { FlushDraft(); LoadProfiles(owner.ProfileId, owner.Id); }
        var feedback = Store.LatestFeedback(run.Id);
        var rows = new BindingList<RowView>(run.Rows.Select(row => new RowView(row, feedback?.Ratings.SingleOrDefault(r => r.Number == row.Number))).ToList());
        binding = true;
        try
        {
            var metrics = PromptLabSession.MeasureRows(run.Rows);
            var speech = CorpusQuality.MeasureSpeech(run.Rows.Select(r => r.Text));
            var label = run.State + " · " + run.VersionId[..8] + " · " + metrics.Records + " строк · дубли " + metrics.ExactDuplicates + "/" + metrics.NearDuplicatePairs
                + " · разнообразие " + metrics.TokenDiversity.ToString("P0") + " · отклонено " + run.Rows.Count(r => !r.SchemaValid)
                + " · мат: " + speech.StrongIndicators + " лекс. / " + run.Rows.Count(r => r.Profanity == "STRONG") + " меток";
            if (right) { rightRun = run; rightRows = rows; gridV2.DataSource = rows; txtFeedbackV2.Text = feedback?.BatchComment ?? ""; lblV2.Text = "V2: " + label; }
            else { rightRun = null; rightRows = new(); gridV2.DataSource = null; txtFeedbackV2.Clear(); lblV2.Text = "V2 — сравнение";
                leftRun = run; leftRows = rows; gridV1.DataSource = rows; txtFeedbackV1.Text = feedback?.BatchComment ?? ""; lblV1.Text = "V1: " + label; }
            txtHistory.Text = "Run " + run.Id + "\r\n" + run.Purpose + " / " + run.State + "\r\n" + run.Failure + "\r\nModel: " + run.ModelId
                + "\r\nНазначение: " + run.Scenario?.Category + " / " + run.Scenario?.Mode + "; experiment: " + run.ExperimentId
                + (run.Scenario?.Components is { } component ? "\r\nСемейство: " + component.FamilyId + " / " + component.FamilyRevision + "; hash: " + component.FunctionalHash : "")
                + "\r\n" + JsonSerializer.Serialize(run.Response?.Telemetry, WorkspaceStore.JsonOptions)
                + "\r\nФактические параметры:\r\n" + JsonSerializer.Serialize(run.ApiOptions, WorkspaceStore.JsonOptions)
                + $"\r\nЛексическое разнообразие {metrics.TokenDiversity:P1}. Исполнение речевого акта и естественность требуют вашей оценки."
                + "\r\n\r\nФактический SYSTEM:\r\n" + run.EffectiveSystem + "\r\n\r\nФактический USER:\r\n" + run.EffectiveUser;
            if (run.Purpose == "GENERATION" && run.Response?.Content.Length > 0)
            {
                try
                {
                    using var document = JsonDocument.Parse(run.Response.Content);
                    string Value(JsonElement row, string key, string fallback) => row.TryGetProperty(key, out var value) ? value.GetString() ?? fallback : fallback;
                    var distribution = document.RootElement.GetProperty("records").EnumerateArray().GroupBy(row => Value(row, "profanity", "NONE") + "/" + Value(row, "maturity", "GENERAL"));
                    txtHistory.AppendText("\r\n\r\nМетки profanity/maturity: " + string.Join("; ", distribution.Select(g => g.Key + ": " + g.Count()))
                        + ". Наличие метки не доказывает наличие мата или взрослой темы в тексте.");
                }
                catch (Exception error) when (error is JsonException or InvalidOperationException or KeyNotFoundException) { }
            }
        }
        finally { binding = false; }
        tabsLab.SelectedTab = tabResults; UpdateActions();
    }

    private void SaveFeedback()
    {
        gridV1.EndEdit(); gridV2.EndEdit();
        Save(leftRun, leftRows, txtFeedbackV1.Text);
        if (rightRun?.Id != leftRun?.Id) Save(rightRun, rightRows, txtFeedbackV2.Text);
        feedbackDirty = false;
        void Save(PromptLabRun? run, BindingList<RowView> rows, string comment)
        {
            if (run?.CompletedAtUtc.HasValue != true) return;
            var ratings = rows.Select(r => r.Rating()).ToArray(); var previous = Store.LatestFeedback(run.Id);
            if (previous?.BatchComment == comment && previous.Ratings.SequenceEqual(ratings)) return;
            Store.SaveFeedback(run.Id, ratings, comment); packPreview = null; chkMeaning.Checked = false;
        }
    }

    private void ClearResults()
    {
        var previous = binding; binding = true;
        try { leftRun = rightRun = null; leftRows = new(); rightRows = new(); gridV1.DataSource = gridV2.DataSource = null;
            txtFeedbackV1.Clear(); txtFeedbackV2.Clear(); lblV1.Text = "V1 — исходная партия"; lblV2.Text = "V2 — сравнение"; feedbackDirty = false; }
        finally { binding = previous; }
    }

    public void CancelCurrentOperation() => operation?.Cancel();
    public bool TryClose()
    {
        if (Busy) { CancelCurrentOperation(); lblStatus.Text = "Отмена запрошена. Дождитесь сохранения результата."; return false; }
        return Safe(FlushDraft);
    }

    private void New_Click(object? sender, EventArgs e) => Safe(() => { FlushDraft(); var created = Lab.CreateProfile("Новый профиль", "dialogue-context", new PromptLabVersion().Scenario); LoadProfiles(created.ProfileId, created.Id); });
    private void Copy_Click(object? sender, EventArgs e) => Safe(() => { FlushDraft(); var version = SaveEditor(); var copy = Store.CopyVersion(version.Id, version.Name + " — копия"); LoadProfiles(copy.ProfileId, copy.Id); });
    private void Save_Click(object? sender, EventArgs e) => Safe(() => { SaveEditor(); lblStatus.Text = "Новая неизменяемая версия сохранена."; });
    private void Family_Click(object? sender, EventArgs e) => Safe(() =>
    {
        FlushDraft(); var draft = ReadEditor(); var scope = new[] { draft.Scenario }.Concat(draft.AdditionalScenarios).ElementAt(activeScopeIndex);
        var created = Lab.CreateFamilyProfile(scope, draft.Name + " — семейство"); LoadProfiles(created.ProfileId, created.Id);
        lblStatus.Text = "Создан независимый профиль выбранного семейства. Исходный профиль и его принятая версия сохранены.";
    });
    private void Accept_Click(object? sender, EventArgs e) => Safe(() => { var version = SaveEditor(); Store.AcceptVersion(version.Id); LoadProfiles(version.ProfileId, version.Id); lblStatus.Text = "Принят только промпт. Реплики и импорт требуют отдельного решения."; });
    private void ApplyPlan_Click(object? sender, EventArgs e) => Safe(() =>
    {
        if (current!.FamilyPrompts.Count > 0 || !current.ReadyForGeneration && txtDescription.Text.Length > 0) { SplitEditorPlan(); return; }
        var draft = ReadEditor() with { Id = "", ParentId = current!.Id, Origin = "MANUAL", ProposedFromRunId = "", ReadyForGeneration = true };
        var saved = Store.SaveVersion(Lab.PrepareVersion(draft)); DiscardEditorDraft(current!); dirty = false; LoadProfiles(saved.ProfileId, saved.Id);
        lblStatus.Text = "План проверен и закреплён: " + (saved.AdditionalScenarios.Count + 1) + " отдельных партий. Теперь можно генерировать.";
    });
    private void SplitFamilies_Click(object? sender, EventArgs e) => Safe(SplitEditorPlan);
    private void SplitEditorPlan()
    {
        var draft = ReadEditor() with { Id = "", ParentId = current!.Id, Origin = "MANUAL", ProposedFromRunId = "", ReadyForGeneration = false, FamilyGroupId = "" };
        var source = Store.SaveVersion(draft); var group = Lab.SplitFamilyPlan(source.Id); var first = Store.GetVersion(group.VersionIds[0]);
        DiscardEditorDraft(current!); dirty = false; LoadProfiles(first.ProfileId, first.Id);
        lblStatus.Text = "Создано независимых профилей: " + group.VersionIds.Count + ". Проверьте SYSTEM каждого семейства; генерация запускается отдельно.";
    }
    private void Profile_Changed(object? sender, EventArgs e) { if (!binding && !Busy) Safe(() => { FlushDraft(); if (cboProfiles.SelectedItem is Choice choice) LoadVersions(choice.Id); }); }
    private void Version_Changed(object? sender, EventArgs e) { if (!binding && !Busy) Safe(() => { FlushDraft(); if (cboVersions.SelectedItem is Choice choice) { Store.SelectVersion(choice.Id); LoadVersion(choice.Id); } }); }
    private void Editor_Changed(object? sender, EventArgs e) { if (!binding) { dirty = true; draftTimer.Start(); UpdateActions(); } }
    private void Inputs_CellEndEdit(object? sender, DataGridViewCellEventArgs e) => Editor_Changed(sender, EventArgs.Empty);
    private void Feedback_Changed(object? sender, EventArgs e) { if (!binding) { feedbackDirty = true; draftTimer.Start(); UpdateActions(); } }
    private void Results_CellEndEdit(object? sender, DataGridViewCellEventArgs e) => Feedback_Changed(sender, EventArgs.Empty);
    private void Results_CellContentClick(object? sender, DataGridViewCellEventArgs e)
    {
        if (Busy || e.RowIndex < 0 || e.ColumnIndex < 0 || sender is not DataGridView grid || grid.Rows[e.RowIndex].DataBoundItem is not RowView row) return;
        var name = grid.Columns[e.ColumnIndex].Name; if (name is not ("like" or "dislike")) return;
        row.Vote = name == "like" ? "LIKE" : "DISLIKE"; grid.Refresh(); feedbackDirty = true; Safe(SaveFeedback); UpdateActions();
    }
    private void DraftTimer_Tick(object? sender, EventArgs e) { draftTimer.Stop(); if (!Busy) Safe(FlushDraft); }
    private void FlushDraft() { if (dirty && current != null) Store.SaveDraft(current.Id, ReadEditor()); if (feedbackDirty) SaveFeedback(); }
    private void Cancel_Click(object? sender, EventArgs e) => CancelCurrentOperation();
    private void ViewRun_Click(object? sender, EventArgs e) => Safe(() => { FlushDraft(); if (cboRuns.SelectedItem is Choice choice) ShowRun(Store.GetRun(choice.Id), false); });
    private void AddScope_Click(object? sender, EventArgs e) => Safe(() =>
    {
        SaveActiveScene(); if (scopeRows.Count >= 16) throw new InvalidDataException("Не более 16 партий в эксперименте.");
        var source = scopeRows[Math.Clamp(activeScopeIndex, 0, scopeRows.Count - 1)];
        scopeRows.Add(ScopeView.From(source.ToRequest(), source.CategoryInstruction)); Editor_Changed(sender, e);
    });
    private void RemoveScope_Click(object? sender, EventArgs e) => Safe(() =>
    {
        if (scopeRows.Count <= 1) throw new InvalidDataException("Оставьте хотя бы одну партию.");
        var previous = binding; binding = true;
        try { scopeRows.RemoveAt(Math.Clamp(activeScopeIndex, 0, scopeRows.Count - 1)); activeScopeIndex = 0; }
        finally { binding = previous; }
        LoadActiveScene(); Editor_Changed(sender, e);
    });
    private void Taxonomy_Click(object? sender, EventArgs e) => Safe(() =>
    {
        if (current == null) return; FlushDraft(); var workspace = Store.IsolatedV4();
        foreach (var category in current.CategorySuggestions)
        {
            if (workspace.Categories().Any(c => c.Key == category.Key)) continue;
            var proposal = workspace.Propose(category.Parent, category.Key, category.Label, category.Act, category.Intent);
            if (MessageBox.Show(this, category.Label + "\r\n" + category.Key + "\r\nact: " + category.Act + "\r\nintent: " + category.Intent
                + "\r\n" + category.Reason + "\r\nSHA: " + proposal.Hash + "\r\n\r\nУтвердить только в отдельном реестре лаборатории?", "Предложение категории", MessageBoxButtons.YesNo, MessageBoxIcon.Question) == DialogResult.Yes)
                workspace.Approve(proposal.Path, proposal.Hash, true);
        }
        if (current != null) LoadVersion(current.Id); lblStatus.Text = "Реестр лаборатории проверен. События GameServer не добавлялись.";
    });
    private string[] SelectedPackRuns()
    {
        var selected = rightRun ?? leftRun ?? (cboRuns.SelectedItem is Choice choice ? Store.GetRun(choice.Id) : throw new InvalidDataException("Выберите партию."));
        if (!chkExperiment.Checked || selected.ExperimentId.Length == 0) return [selected.Id];
        return Store.ListRuns().Where(r => r.ExperimentId == selected.ExperimentId && r.State == "COMPLETE" && r.Purpose == "GENERATION"
            && Store.LatestFeedback(r.Id)?.Ratings.Any(x => x.Vote == "LIKE") == true).Select(r => r.Id).ToArray();
    }
    private void Route_Click(object? sender, EventArgs e) => Safe(() =>
    {
        SaveFeedback(); packPreview = new PromptLabRouting(Store).Preview(SelectedPackRuns()); chkMeaning.Checked = false;
        txtPackReport.Text = "Только LIKE из принятых версий. Выборка " + packPreview.Id + "\r\nSHA review: " + packPreview.ReviewHash
            + "\r\nJSONL: " + packPreview.JsonlPath + "\r\nПартий: " + packPreview.Sources.Count + "; строк: " + packPreview.Validation.Entries.Count
            + "\r\n" + string.Join("\r\n", packPreview.Validation.Entries.GroupBy(x => x.Category + " / " + x.Mode + " / " + x.Act).Select(g => g.Key + ": " + g.Count()))
            + "\r\n\r\n" + string.Join("\r\n", packPreview.Validation.Diagnostics) + "\r\n" + string.Join("\r\n", packPreview.Notes);
        tabsLab.SelectedTab = tabPack; UpdateActions();
    });
    private void ImportPack_Click(object? sender, EventArgs e) => Safe(() =>
    {
        var preview = packPreview ?? throw new InvalidDataException("Сначала выполните предпросмотр.");
        var report = new PromptLabRouting(Store).Import(preview.Id, preview.ReviewHash, chkMeaning.Checked);
        txtPackReport.AppendText("\r\nИмпорт в отдельный workspace: " + report.Accepted + "; дубли: " + report.Duplicates + "\r\n" + string.Join("\r\n", report.Diagnostics));
        chkMeaning.Checked = false;
    });
    private void BuildPack_Click(object? sender, EventArgs e) => Safe(() =>
    {
        var stage = new PromptLabRouting(Store).BuildAndVerify(); txtPackReport.AppendText("\r\nV4-stage проверен: " + stage.Build.StagePath
            + "\r\nSHA: " + stage.Build.ManifestHash + "; записей: " + stage.Build.Records + "\r\nНа GameServer не установлен.");
    });
    private void Production_Click(object? sender, EventArgs e) => Safe(() =>
    {
        SaveFeedback(); var version = SaveEditor(); var runs = SelectedPackRuns();
        if (MessageBox.Show(this, "Закрепить принятую версию и оценки как профиль для последующей массовой генерации?\r\nЭто не импорт и не утверждение новых реплик.",
            "Production-профиль", MessageBoxButtons.YesNo, MessageBoxIcon.Question) != DialogResult.Yes) return;
        var production = new PromptLabRouting(Store).Promote(version.Id, runs, true);
        lblStatus.Text = "Production-профиль сохранён: " + production.Id; tabsLab.SelectedTab = tabPack;
    });
    private void Meaning_Changed(object? sender, EventArgs e) => UpdateActions();

    private async void Models_Click(object? sender, EventArgs e) => await RunAsync(async token =>
    {
        using var http = LmStudioClient.CreateHttpClient(20); var inventory = await new LmStudioClient(http).ProbeCorpusModelsAsync(settingsProvider!(), keyProvider!(), token);
        lblStatus.Text = inventory.Loaded.Count == 0 ? "MODEL_OFFLINE: вручную запустите модель и сервер LM Studio." : string.Join("; ", inventory.Loaded.Select(m => m.InstanceId + " · context " + m.ContextLength
            + " · native reasoning: " + (m.AllowedReasoning.Length == 0 ? "UNKNOWN" : string.Join('/', m.AllowedReasoning))));
        if (inventory.Loaded.Count == 1 && (txtModel.Text.Length == 0 || inventory.Loaded[0].ModelKey == txtModel.Text)) txtModel.Text = inventory.Loaded[0].InstanceId;
    });
    private async void Generate_Click(object? sender, EventArgs e) => await RunAsync(async token =>
    {
        var version = SaveEditor(); IReadOnlyList<PromptLabRun> results;
        if (ReferenceEquals(sender, btnGenerateFamilies))
        {
            var group = Store.GetFamilyGroup(version.FamilyGroupId);
            var selected = group.VersionIds.Select(Store.GetVersion).Select(member => member.ProfileId == version.ProfileId ? version.Id : Store.GetProfile(member.ProfileId).SelectedVersionId).ToArray();
            results = await Lab.GenerateFamilyExperimentAsync(selected, settingsProvider!(), keyProvider!(), txtModel.Text, token, Progress());
        }
        else results = await Lab.GenerateExperimentAsync(version.Id, settingsProvider!(), keyProvider!(), txtModel.Text, token, Progress());
        if (results.Count > 0) ShowRun(results[^1], false);
        txtPackReport.Text = "Эксперимент сохранён по отдельным категориям и режимам:\r\n" + string.Join("\r\n", results.GroupBy(r => (r.Scenario?.Category ?? "") + " / " + (r.Scenario?.Mode ?? "")).Select(g => g.Key + ": " + g.Sum(r => r.Rows.Count) + " строк; запросов " + g.Count()));
        LoadHistory();
    });
    private async void Describe_Click(object? sender, EventArgs e) => await RunAsync(async token =>
    {
        var version = SaveEditor(); var result = await Lab.DescribeAsync(version.Id, txtDescription.Text, settingsProvider!(), keyProvider!(), txtModel.Text, token, Progress());
        if (result.ProposedVersionId.Length > 0) { var proposed = Store.GetVersion(result.ProposedVersionId); LoadProfiles(proposed.ProfileId, proposed.Id); tabsLab.SelectedTab = tabScope; }
        lblStatus.Text = result.Failure.Length == 0 ? "Предложение сохранено. Проверьте партии; подтверждение плана создаст независимые профили семейств для редактирования SYSTEM/USER." : result.Failure; LoadHistory();
    });
    private async void Refine_Click(object? sender, EventArgs e) => await RunAsync(async token =>
    {
        SaveFeedback(); var source = rightRun ?? leftRun ?? throw new InvalidDataException("Сначала получите и оцените партию."); var selected = SaveEditor();
        var result = await Lab.RefineAsync(source.Id, settingsProvider!(), keyProvider!(), txtModel.Text, token, Progress(), selected.Refinement, selected.Id);
        if (result.ProposedVersionId.Length > 0) { if (rightRun != null) ShowRun(rightRun, false);
            var proposed = Store.GetVersion(result.ProposedVersionId); LoadProfiles(proposed.ProfileId, proposed.Id); tabsLab.SelectedTab = tabPrompt; }
        lblStatus.Text = result.State + ": " + (result.Failure.Length == 0 ? "Предложена новая версия; принятую версию не менял." : result.Failure); LoadHistory();
    });
    private async void Compare_Click(object? sender, EventArgs e) => await RunAsync(async token =>
    {
        var version = SaveEditor(); var baseline = leftRun ?? throw new InvalidDataException("Выберите исходную партию.");
        var result = await Lab.CompareAsync(baseline.Id, version.Id, settingsProvider!(), keyProvider!(), txtModel.Text, token, Progress()); ShowRun(result, true); LoadHistory();
    });
    private async void Resume_Click(object? sender, EventArgs e) => await RunAsync(async token =>
    {
        if (cboRuns.SelectedItem is not Choice choice) throw new InvalidDataException("Выберите эксперимент в истории.");
        var results = await Lab.ResumeExperimentAsync(choice.Id, settingsProvider!(), keyProvider!(), txtModel.Text, token, Progress());
        if (results.Count > 0) ShowRun(results[^1], false); LoadHistory();
    });

    private IProgress<PromptLabProgress> Progress() => new Progress<PromptLabProgress>(value =>
    {
        if (!IsDisposed) lblStatus.Text = value.Stage + $" · {value.ElapsedSeconds:F1} с · {value.ReceivedBytes} байт · ответ {value.MessageCharacters} знаков · reasoning {value.ReasoningCharacters} знаков";
    });
    private async Task RunAsync(Func<CancellationToken, Task> action)
    {
        if (Busy || lab == null) return;
        if (feedbackDirty && !Safe(SaveFeedback)) return;
        using var cancel = new CancellationTokenSource(); operation = cancel; elapsed.Restart(); UpdateActions(); BusyChanged?.Invoke(this, EventArgs.Empty);
        try { await action(cancel.Token); }
        catch (Exception error) when (error is InvalidDataException or IOException or JsonException or LmStudioException or HttpRequestException or OperationCanceledException or InvalidOperationException or UnauthorizedAccessException or ArgumentException)
        { lblStatus.Text = error is OperationCanceledException ? "Отменено. Завершённые результаты и ввод сохранены." : error.Message; }
        finally { operation = null; elapsed.Stop(); UpdateActions(); BusyChanged?.Invoke(this, EventArgs.Empty); }
    }
    private bool Safe(Action action)
    {
        try { action(); return true; }
        catch (Exception error) when (error is InvalidDataException or IOException or JsonException or InvalidOperationException or UnauthorizedAccessException or ArgumentException)
        { lblStatus.Text = error.Message; return false; }
    }
    private void UpdateActions()
    {
        var ready = lab != null && current != null; var idle = !Busy;
        btnGenerate.Enabled = ready && idle && current!.ReadyForGeneration; btnModels.Enabled = lab != null && idle; btnSave.Enabled = ready && idle; btnAccept.Enabled = ready && idle && current!.ReadyForGeneration;
        btnDescribe.Enabled = ready && idle; btnApplyPlan.Enabled = ready && idle;
        btnAddScope.Enabled = btnRemoveScope.Enabled = btnTaxonomy.Enabled = ready && idle;
        btnFamily.Enabled = ready && idle;
        btnSplitFamilies.Enabled = ready && idle; btnGenerateFamilies.Enabled = ready && idle && current!.FamilyGroupId.Length > 0;
        btnRoute.Enabled = ready && idle && (leftRun != null || rightRun != null); btnBuildPack.Enabled = lab != null && idle;
        btnImportPack.Enabled = idle && packPreview?.Validation.Success == true && chkMeaning.Checked;
        btnProduction.Enabled = ready && idle && (leftRun != null || rightRun != null);
        btnNew.Enabled = lab != null && idle; btnCopy.Enabled = ready && idle; btnCompare.Enabled = ready && idle && leftRun != null;
        btnRefine.Enabled = ready && idle && (leftRun != null || rightRun != null); btnCancel.Enabled = Busy; btnResume.Enabled = ready && idle && cboRuns.Items.Count > 0;
        cboProfiles.Enabled = cboVersions.Enabled = !Busy; txtModel.Enabled = !Busy;
        cboContextPresentation.Enabled = ready && idle;
        txtSystem.ReadOnly = txtUser.ReadOnly = txtName.ReadOnly = txtSituation.ReadOnly = txtDialogue.ReadOnly = Busy;
        txtDescription.ReadOnly = txtFeedbackV1.ReadOnly = txtFeedbackV2.ReadOnly = Busy;
        txtCategoryInstruction.ReadOnly = Busy;
        gridV1.ReadOnly = gridV2.ReadOnly = Busy; chkAdult.Enabled = !Busy; cboRuns.Enabled = btnViewRun.Enabled = idle;
        gridScope.Enabled = gridParameters.Enabled = !Busy;
    }
    private void Lab_Disposed(object? sender, EventArgs e) { operation?.Cancel(); lab?.Dispose(); }

    private sealed class ScopeView
    {
        public string Category { get; set; } = ""; public string Mode { get; set; } = "REPLY"; public string Voice { get; set; } = "ANY";
        public string Relation { get; set; } = "UNKNOWN"; public string Style { get; set; } = "ordinary"; public string Maturity { get; set; } = "GENERAL";
        public string Profanity { get; set; } = "NONE"; public string Channel { get; set; } = "ALL"; public int Count { get; set; } = 6; public string Event { get; set; } = "";
        public string Stance { get; set; } = ""; public string Tone { get; set; } = ""; public string Emotion { get; set; } = "";
        public string CategoryInstruction { get; set; } = "";
        public bool IsComponent => template.Components != null;
        public string Situation { get; set; } = ""; public string[] Dialogue { get; set; } = []; public bool Adults { get; set; }
        private V4GenerationRequest template = new();
        public static ScopeView From(V4GenerationRequest request, string? brief = null) => new() { Category = request.Category, Mode = request.Mode, Voice = request.Style.Voice,
            Relation = request.Style.Relation, Style = request.Style.StyleId, Maturity = request.Style.MatureRequested ? "MATURE" : "GENERAL",
            Profanity = request.ProfanityLimit ?? (request.Style.StrongProfanityRequested ? "STRONG" : "NONE"), Channel = request.Style.Channel, Count = request.Count, Event = request.RequiredEvent,
            Stance = request.DesiredStance ?? "", Tone = request.DesiredTone ?? "", Situation = request.Style.Situation, Dialogue = request.Style.Dialogue,
            Adults = request.Style.OwnerMatureOptIn && request.Style.AdultsConsenting, Emotion = request.Style.EmotionalState ?? "",
            CategoryInstruction = brief ?? request.Components?.CategoryInstruction ?? "", template = request };
        public V4GenerationRequest ToRequest() => template with { Category = Category, Mode = Mode, Count = Count, RequiredEvent = Mode == "INITIATIVE" ? Event : "",
            DesiredStance = Mode == "TRIGGER" || Stance.Length == 0 ? null : Stance, DesiredTone = Mode == "TRIGGER" || Tone.Length == 0 ? null : Tone, ProfanityLimit = Profanity,
            Style = template.Style with { Category = Category, Voice = Voice, Relation = Relation, StyleId = Style, Channel = Channel, StrongProfanityRequested = Profanity != "NONE",
                MatureRequested = Maturity == "MATURE", OwnerMatureOptIn = Adults, AdultsConsenting = Adults, Situation = Situation, Dialogue = Dialogue,
                EmotionalState = string.IsNullOrWhiteSpace(Emotion) ? null : Emotion } };
    }
    private sealed class RowView(PromptLabRow row, PromptLabRating? rating)
    {
        public int Number => row.Number; public string Text => row.Text; public string Vote { get; set; } = rating?.Vote ?? "NONE";
        public string VoteLabel => Vote switch { "LIKE" => "Нравится", "DISLIKE" => "Не нравится", _ => "Без оценки" };
        public string Comment { get; set; } = rating?.Comment ?? "";
        public string Flags => row.Profanity + "/" + row.Maturity + " " + row.Stance + "/" + row.Tone + (row.Diagnostics.Length == 0 ? "" : " · " + string.Join(", ", row.Diagnostics));
        public PromptLabRating Rating() => new(row.Number, row.TextHash, Vote, Comment);
    }
}
