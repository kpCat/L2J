using System.ComponentModel;
using System.Globalization;
using PhantomSemanticStudio.Core;

namespace PhantomSemanticStudio.WinForms;

public partial class OpenAiCampaignForm : Form
{
    private const int BulkPageSize = 100;
    private const int MaxPage = 20000;
    private WorkspaceStore? workspace;
    private CorpusCampaign? campaign;
    private OpenAiKeyVault? vault;
    private CancellationTokenSource? operation;
    private OpenAiCampaignPlan? selectedPlan;
    private OpenAiCampaignView? selectedView;
    private BindingList<QuotaRow>? quotas;
    private bool binding;
    private bool ready;
    private bool keySaved;
    private bool qualityAccepted;
    private bool controlPending;
    private bool controlRequested;
    private bool closeRequested;
    private bool runnerReady;
    private string? runningId;
    private string? selectedHash;
    private string? reviewedPilotId;
    private string modelForTariff = "";
    private OpenAiTariff? shownTariff;
    private int page;
    private int sampleCount;
    private string bulkLog = "";

    public OpenAiCampaignForm()
    {
        InitializeComponent();
    }

    internal void SetWorkspace(WorkspaceStore workspace, OpenAiKeyVault? vault = null)
    {
        this.workspace = workspace;
        this.vault = vault;
    }

    protected override async void OnShown(EventArgs e)
    {
        base.OnShown(e);
        if (workspace is not { } owner || ready) return;
        AttachInputHandlers();
        await RunAsync(lblProviderStatus, async token =>
        {
            var session = await Task.Run(() =>
            {
                var api = new CorpusCampaign(new PromptLabStore(owner));
                var settings = api.OpenAiSettings();
                settings.Validate();
                return (Api: api, Settings: settings, History: api.OpenAiHistory());
            }, token);
            campaign = session.Api;
            ApplySettings(session.Settings, updateBudget: true);
            chkDryRun.Checked = true;
            txtNote.MaxLength = 1000;
            BindHistory(session.History, null);
            ready = true;
            await RefreshKeyAsync(token);
        });
    }

    private void AttachInputHandlers()
    {
        txtKey.TextChanged += Key_Changed;
        foreach (var field in new[] { nudInputUsd, nudCachedUsd, nudOutputUsd, nudCacheWriteUsd, nudExtendedWriteUsd })
            field.ValueChanged += Tariff_Changed;
        foreach (var field in new[] { nudGrant, nudReserve, nudPilot, nudCampaign, nudDaily, nudMonthly, nudRequest, nudGoal, nudMicro, nudQualityThreshold })
            field.ValueChanged += Input_Changed;
        chkDryRun.CheckedChanged += Input_Changed;
        chkMature.CheckedChanged += Input_Changed;
        chkConfirmTariff.CheckedChanged += Input_Changed;
        cboFormat.SelectedIndexChanged += Input_Changed;
        gridPlan.CellValueChanged += Input_Changed;
        gridPlan.CellValidating += PlanCell_Validating;
        foreach (var check in new[] { chkFacts, chkRoles, chkVoice, chkNatural, chkCategory, chkConsent, chkDistinct })
            check.CheckedChanged += Input_Changed;
        nudScore.ValueChanged += Input_Changed;
        txtNote.TextChanged += Input_Changed;
    }

    private void Key_Changed(object? sender, EventArgs e)
    {
        lblProviderStatus.Text = "Проверка доступа для текущего ключа не запускалась.";
        UpdateActions();
    }

    private void Tariff_Changed(object? sender, EventArgs e)
    {
        if (binding) return;
        chkConfirmTariff.Checked = false;
        UpdateActions();
    }

    private void Input_Changed(object? sender, EventArgs e)
    {
        if (!binding) UpdateActions();
    }

    private void Model_Changed(object? sender, EventArgs e)
    {
        if (binding) return;
        var model = cboModel.Text.Trim();
        if (model == modelForTariff) return;
        binding = true;
        try
        {
            modelForTariff = model;
            ShowTariff(OpenAiTariff.Known(model), confirmed: false);
        }
        finally { binding = false; }
        lblProviderStatus.Text = "Проверка доступа для текущей модели не запускалась.";
        UpdateActions();
    }

    private void ApplySettings(OpenAiCampaignSettings settings, bool updateBudget)
    {
        binding = true;
        try
        {
            var changedModel = modelForTariff != settings.Model;
            if (!cboModel.Items.Contains(OpenAiTariff.SuggestedModel))
                cboModel.Items.Add(OpenAiTariff.SuggestedModel);
            if (!cboModel.Items.Contains(settings.Model))
                cboModel.Items.Add(settings.Model);
            cboModel.Text = settings.Model;
            modelForTariff = settings.Model;
            if (changedModel) lblProviderStatus.Text = "Проверка доступа для текущей модели не запускалась.";
            ShowTariff(settings.Price, settings.TariffConfirmed);
            chkMature.Checked = settings.MatureOptIn;
            nudMicro.Value = settings.Microbatch;
            cboFormat.SelectedIndex = settings.Format == "TEXT" ? 1 : 0;
            nudQualityThreshold.Value = settings.QualityPercent;
            if (!updateBudget) return;
            nudGrant.Value = settings.Budget.GrantUsd;
            nudReserve.Value = settings.Budget.ReserveUsd;
            nudPilot.Value = settings.Budget.PilotUsd;
            nudCampaign.Value = settings.Budget.CampaignUsd;
            nudDaily.Value = settings.Budget.DailyUsd;
            nudMonthly.Value = settings.Budget.MonthlyUsd;
            nudRequest.Value = settings.Budget.RequestUsd;
        }
        finally { binding = false; }
    }

    private void ShowTariff(OpenAiTariff? tariff, bool confirmed)
    {
        shownTariff = tariff;
        nudInputUsd.Value = tariff?.InputUsd ?? 0;
        nudCachedUsd.Value = tariff?.CachedUsd ?? 0;
        nudOutputUsd.Value = tariff?.OutputUsd ?? 0;
        nudCacheWriteUsd.Value = tariff?.CacheWriteUsd ?? 0;
        nudExtendedWriteUsd.Value = tariff?.ExtendedCacheWriteUsd ?? 0;
        chkConfirmTariff.Checked = confirmed;
        lblTariff.Text = tariff == null ? "Тариф, USD за 1 млн токенов"
            : $"Тариф, USD за 1 млн токенов · {OpenAiTariff.Tier} · {tariff.Checked:yyyy-MM-dd}";
        chkConfirmTariff.AccessibleDescription = tariff == null ? OpenAiTariff.PricingPage
            : $"{tariff.Source} · {tariff.Checked:yyyy-MM-dd} · {OpenAiTariff.Tier}";
    }

    private OpenAiBudget ReadBudget() => new(nudGrant.Value, nudReserve.Value, nudPilot.Value, nudCampaign.Value,
        nudDaily.Value, nudRequest.Value, nudMonthly.Value);

    private string SelectedFormat => cboFormat.SelectedIndex == 1 ? "TEXT" : "JSON";

    private OpenAiCampaignSettings ReadSettings()
    {
        var model = cboModel.Text.Trim();
        OpenAiTariff? tariff = null;
        if (chkConfirmTariff.Checked || nudInputUsd.Value != 0 || nudCachedUsd.Value != 0 || nudOutputUsd.Value != 0
            || nudCacheWriteUsd.Value != 0 || nudExtendedWriteUsd.Value != 0)
        {
            tariff = new OpenAiTariff(model, nudInputUsd.Value, nudCachedUsd.Value, nudOutputUsd.Value,
                nudCacheWriteUsd.Value, nudExtendedWriteUsd.Value, DateOnly.FromDateTime(DateTime.UtcNow), OpenAiTariff.PricingPage);
            if (shownTariff != null && SameRates(shownTariff, tariff)) tariff = shownTariff;
        }
        return new OpenAiCampaignSettings
        {
            Model = model, Price = tariff, TariffConfirmed = chkConfirmTariff.Checked, Budget = ReadBudget(),
            Microbatch = (int)nudMicro.Value, Format = SelectedFormat, MatureOptIn = chkMature.Checked,
            QualityPercent = (int)nudQualityThreshold.Value
        };
    }

    private static bool SameRates(OpenAiTariff? first, OpenAiTariff? second) =>
        first == second || first != null && second != null && first.Model == second.Model
            && first.InputUsd == second.InputUsd && first.CachedUsd == second.CachedUsd && first.OutputUsd == second.OutputUsd
            && first.CacheWriteUsd == second.CacheWriteUsd && first.ExtendedCacheWriteUsd == second.ExtendedCacheWriteUsd;

    private async Task RefreshKeyAsync(CancellationToken token)
    {
        try
        {
            vault ??= await Task.Run(() => new OpenAiKeyVault(workspace!), token);
            keySaved = await Task.Run(vault.Exists, token);
            lblKeyStatus.Text = keySaved ? "Ключ сохранён для текущего пользователя Windows." : "Сохранённого ключа нет.";
        }
        catch (Exception error) when (IsExpected(error))
        {
            keySaved = false;
            lblKeyStatus.Text = SafeDiagnostic(error);
        }
    }

    private string ReadKey(string enteredKey) => enteredKey.Length == 0
        ? vault?.Read() ?? throw new InvalidDataException("OPENAI_KEY_MISSING")
        : enteredKey;

    private async void SaveKey_Click(object? sender, EventArgs e)
    {
        var key = txtKey.Text;
        if (!btnSaveKey.Enabled) return;
        await RunAsync(lblKeyStatus, async token =>
        {
            await Task.Run(() => vault!.Save(key), token);
            txtKey.Clear();
            await RefreshKeyAsync(token);
        });
    }

    private async void DeleteKey_Click(object? sender, EventArgs e)
    {
        if (!btnDeleteKey.Enabled || !Confirm("Удалить сохранённый ключ из локального хранилища Studio?")) return;
        await RunAsync(lblKeyStatus, async token =>
        {
            await Task.Run(vault!.Delete, token);
            txtKey.Clear();
            lblProviderStatus.Text = "Проверка доступа для текущего ключа не запускалась.";
            await RefreshKeyAsync(token);
        });
    }

    private async void Check_Click(object? sender, EventArgs e)
    {
        if (!btnCheck.Enabled) return;
        var key = txtKey.Text;
        var model = cboModel.Text.Trim();
        await RunAsync(lblProviderStatus, async token =>
        {
            var models = await Task.Run(async () =>
            {
                var secret = ReadKey(key);
                if (model == secret) throw new InvalidDataException("OPENAI_MODEL_INVALID");
                using var client = new OpenAiResponsesClient();
                return await client.ModelsAsync(secret, token);
            }, token);
            token.ThrowIfCancellationRequested();
            binding = true;
            cboModel.BeginUpdate();
            try
            {
                cboModel.Items.Clear();
                cboModel.Items.Add(OpenAiTariff.SuggestedModel);
                foreach (var available in models.Take(200))
                    if (!cboModel.Items.Contains(available)) cboModel.Items.Add(available);
                cboModel.Text = model;
            }
            finally
            {
                cboModel.EndUpdate();
                binding = false;
            }
            lblProviderStatus.Text = models.Contains(model, StringComparer.Ordinal)
                ? "Каталог получен. Точный ID модели доступен. Платный пилот этой проверкой не проверен."
                : "Каталог получен. Точный ID модели отсутствует в списке. Платный пилот не проверен.";
        });
    }

    private async void SaveSettings_Click(object? sender, EventArgs e)
    {
        if (!btnSaveSettings.Enabled) return;
        var settings = ReadSettings();
        await RunAsync(lblProviderStatus, async token =>
        {
            await Task.Run(() => campaign!.SaveOpenAiSettings(settings), token);
            lblProviderStatus.Text = "Настройки OpenAI сохранены в частном workspace.";
        });
    }
    private void PlanCell_Validating(object? sender, DataGridViewCellValidatingEventArgs e)
    {
        if (binding || e.RowIndex < 0 || e.ColumnIndex < 0 || gridPlan.Columns[e.ColumnIndex].DataPropertyName != nameof(QuotaRow.Target)) return;
        var row = gridPlan.Rows[e.RowIndex];
        e.Cancel = !int.TryParse(Convert.ToString(e.FormattedValue, CultureInfo.CurrentCulture),
            NumberStyles.Integer, CultureInfo.CurrentCulture, out var target) || target is < 1 or > 1000000;
        row.ErrorText = e.Cancel ? "Введите целую квоту от 1 до 1 000 000." : "";
    }

    private bool QuotasEdited => quotas?.Any(row => row.Target != row.OriginalTarget) == true;

    private async void Plan_Click(object? sender, EventArgs e)
    {
        if (!btnPlan.Enabled || !gridPlan.EndEdit()) return;
        var settings = ReadSettings();
        var total = (int)nudGoal.Value;
        var overrides = QuotasEdited ? quotas!.ToDictionary(row => row.Category, row => row.Target, StringComparer.Ordinal) : null;
        await RunAsync(lblCampaignStatus, async token =>
        {
            if (overrides != null && overrides.Values.Sum(target => (long)target) != total)
                throw new InvalidDataException("OPENAI_CATEGORY_QUOTAS");
            var plan = await Task.Run(() => campaign!.PrepareOpenAi(settings, total, overrides), token);
            await RefreshHistoryAsync(plan.Id, token);
            await SelectPlanAsync(plan.Id, applySettings: false, token);
        });
    }

    private void BindHistory(IReadOnlyList<OpenAiCampaignPlan> plans, string? id)
    {
        binding = true;
        try
        {
            var choices = plans.Select(plan => new HistoryItem(plan.Id,
                $"{(plan.Pilot ? "Пилот" : "Кампания")} · {plan.CreatedAtUtc.ToLocalTime():g} · {plan.Id[..8]}")).ToList();
            cboHistory.DataSource = choices;
            cboHistory.SelectedIndex = choices.FindIndex(choice => choice.Id == id);
        }
        finally { binding = false; }
    }

    private async Task RefreshHistoryAsync(string? id, CancellationToken token)
    {
        var plans = await Task.Run(campaign!.OpenAiHistory, token);
        BindHistory(plans, id);
    }

    private async Task SelectPlanAsync(string id, bool applySettings, CancellationToken token)
    {
        var snapshot = await Task.Run(() =>
        {
            var plan = campaign!.LoadOpenAi(id);
            var hash = campaign.OpenAiHash(id);
            var view = campaign.OpenAiStatus(id);
            return (Plan: plan, Hash: hash, View: view, Accepted: !plan.Pilot && campaign.IsOpenAiQualityAccepted(id));
        }, token);
        selectedPlan = snapshot.Plan;
        selectedHash = snapshot.Hash;
        qualityAccepted = snapshot.Accepted;
        if (applySettings)
        {
            ApplySettings(snapshot.Plan.Settings, updateBudget: false);
            if (!snapshot.Plan.Pilot && snapshot.Plan.Target >= nudGoal.Minimum && snapshot.Plan.Target <= nudGoal.Maximum)
                nudGoal.Value = snapshot.Plan.Target;
        }
        page = 0;
        sampleCount = 0;
        bulkLog = "";
        if (reviewedPilotId != id) reviewedPilotId = null;
        BindSamples([], null);
        lblReviewStatus.Text = "Обновите результаты выбранного плана.";
        RenderProgress(snapshot.View, rebind: true);
    }

    private async void Pilot_Click(object? sender, EventArgs e)
    {
        if (!btnPilot.Enabled) return;
        var settings = ReadSettings();
        var enteredKey = txtKey.Text;
        await RunAsync(lblCampaignStatus, async token =>
        {
            settings.Validate(paid: true);
            var maximum = settings.Budget.Remaining(true, 0, 0, 0, 0, 0);
            if (!Confirm($"Запустить платный A/B-пилот OpenAI для {settings.Model}?\r\n"
                + $"До {OpenAiCampaignPlan.PilotMaxPosts} запросов. Предел по введённым лимитам до {maximum:N4} USD.\r\n"
                + $"Категории для взрослых: {(settings.MatureOptIn ? "включены" : "выключены")}.\r\n"
                + "После получения черновиков потребуется слепая ручная оценка.")) return;
            var plan = await Task.Run(() => campaign!.PrepareOpenAi(settings, 0, pilot: true), token);
            await RefreshHistoryAsync(plan.Id, token);
            await SelectPlanAsync(plan.Id, applySettings: false, token);
            await RunPaidAsync(plan, selectedHash!, settings.Budget, enteredKey, resume: false, token);
        });
    }

    private async void Start_Click(object? sender, EventArgs e)
    {
        if (btnStart.Enabled) await RunExistingAsync(resume: false);
    }

    private async void Resume_Click(object? sender, EventArgs e)
    {
        if (btnResume.Enabled) await RunExistingAsync(resume: true);
    }

    private async Task RunExistingAsync(bool resume)
    {
        if (!gridPlan.EndEdit() || selectedPlan is not { } plan || selectedHash is not { } hash || !MatchesPlan(plan)) return;
        var budget = ReadBudget();
        var enteredKey = txtKey.Text;
        var action = resume ? "Продолжить" : "Запустить";
        var kind = plan.Pilot ? "A/B-пилот" : "кампанию";
        var strategy = plan.Pilot ? "слепое сравнение двух форматов" : plan.Settings.Format;
        var maximum = budget.Remaining(plan.Pilot, 0, 0, 0, 0, 0);
        await RunAsync(lblCampaignStatus, async token =>
        {
            plan.Settings.Validate(paid: true);
            budget.Validate();
            if (!Confirm($"{action} платную работу OpenAI ({kind}) для {plan.Settings.Model}?\r\n"
                + $"Стратегия: {strategy}. Верхняя граница по введённым лимитам {maximum:N4} USD.\r\n"
                + "Доступная сумма зависит от предыдущих расходов в журнале Studio.\r\n"
                + $"Категории для взрослых: {(plan.Settings.MatureOptIn ? "включены" : "выключены")}.")) return;
            await RunPaidAsync(plan, hash, budget, enteredKey, resume, token);
        });
    }

    private async Task RunPaidAsync(OpenAiCampaignPlan plan, string hash, OpenAiBudget budget, string enteredKey,
        bool resume, CancellationToken token)
    {
        runningId = plan.Id;
        runnerReady = false;
        controlRequested = false;
        var progress = new Progress<OpenAiCampaignView>(current =>
        {
            if (runningId != plan.Id || IsDisposed) return;
            runnerReady = current.State == "RUNNING";
            RenderProgress(current, rebind: false);
            UpdateActions();
        });
        try
        {
            lblCampaignStatus.Text = "Открывается журнал кампании…";
            var owner = workspace!;
            var result = await Task.Run(async () =>
            {
                var key = ReadKey(enteredKey);
                using var client = new OpenAiResponsesClient();
                using var worker = new CorpusCampaignWorker(owner);
                return await worker.RunOpenAiAsync(
                    plan.Id, hash, client, key, budget, paidConfirmed: true, resume, token, progress);
            }, token);
            RenderProgress(result, rebind: false);
            if (!closeRequested)
            {
                tabsCampaign.SelectedTab = tabResults;
                await ReadReviewAsync(0, null, token);
            }
        }
        finally
        {
            runningId = null;
            runnerReady = false;
            controlRequested = false;
        }
    }

    private async void Pause_Click(object? sender, EventArgs e)
    {
        if (btnPause.Enabled) await RequestControlAsync("PAUSE");
    }

    private async void Stop_Click(object? sender, EventArgs e)
    {
        if (btnStop.Enabled && Confirm("Остановить кампанию после завершения текущего запроса?"))
            await RequestControlAsync("STOP");
    }

    private async Task RequestControlAsync(string command)
    {
        if (runningId is not { } id || !runnerReady || controlPending || controlRequested) return;
        controlPending = true;
        UpdateActions();
        try
        {
            await Task.Run(() => campaign!.RequestOpenAiControl(id, command));
            controlRequested = true;
            lblCampaignStatus.Text = command == "PAUSE"
                ? "Пауза запрошена. Текущий запрос будет завершён."
                : "Остановка запрошена. Текущий запрос будет завершён.";
        }
        catch (Exception error) when (IsExpected(error))
        {
            lblCampaignStatus.Text = SafeDiagnostic(error);
            if (closeRequested) operation?.Cancel();
        }
        finally
        {
            controlPending = false;
            UpdateActions();
            FinishClose();
        }
    }
    private async void History_Changed(object? sender, EventArgs e)
    {
        if (binding || !ready || cboHistory.SelectedItem is not HistoryItem choice || selectedPlan?.Id == choice.Id) return;
        await RunAsync(lblReviewStatus, async token =>
        {
            await SelectPlanAsync(choice.Id, applySettings: true, token);
            await ReadReviewAsync(0, null, token);
        });
    }

    private async void Results_Click(object? sender, EventArgs e)
    {
        if (!btnResults.Enabled) return;
        var selected = selectedPlan?.Id;
        await RunAsync(lblReviewStatus, async token =>
        {
            await RefreshHistoryAsync(selected, token);
            if (selected == null)
            {
                lblReviewStatus.Text = "Выберите кампанию из истории.";
                return;
            }
            await ReadReviewAsync(page, SelectedSample, token);
        });
    }

    private async void Previous_Click(object? sender, EventArgs e)
    {
        if (btnPrevious.Enabled)
            await RunAsync(lblReviewStatus, token => ReadReviewAsync(page - 1, null, token));
    }

    private async void Next_Click(object? sender, EventArgs e)
    {
        if (btnNext.Enabled)
            await RunAsync(lblReviewStatus, token => ReadReviewAsync(page + 1, null, token));
    }

    private async Task ReadReviewAsync(int requestedPage, OpenAiSample? keep, CancellationToken token)
    {
        if (selectedPlan is not { } plan) return;
        var review = await Task.Run(() =>
        {
            var rows = campaign!.OpenAiSamples(plan.Id, requestedPage, reveal: false);
            var journal = plan.Pilot ? "" : FormatBulkLog(campaign.OpenAiLog(plan.Id, 12));
            return (Rows: rows, Journal: journal, View: campaign.OpenAiStatus(plan.Id),
                Accepted: !plan.Pilot && campaign.IsOpenAiQualityAccepted(plan.Id));
        }, token);
        if (requestedPage > page && review.Rows.Count == 0)
        {
            sampleCount = 0;
            lblReviewStatus.Text = "Достигнут конец результатов.";
            return;
        }
        page = requestedPage;
        sampleCount = review.Rows.Count;
        bulkLog = review.Journal;
        qualityAccepted = review.Accepted;
        RenderProgress(review.View, rebind: false);
        BindSamples(review.Rows, keep);
        lblReviewStatus.Text = plan.Pilot
            ? $"Слепая оценка A/B. Сохранено оценок: {review.View.Totals.Reviewed} из {review.Rows.Count}."
            : $"Страница {page + 1}. Строк на странице: {sampleCount}. Одобрено в кампании: {review.View.Totals.Approved}.";
        await ShowSampleAsync(token);
    }

    private void BindSamples(IReadOnlyList<OpenAiSample> rows, OpenAiSample? keep)
    {
        binding = true;
        try
        {
            gridSamples.DataSource = rows.Select(row => new SampleRow(row)).ToList();
            ConfigureColumn(gridSamples, nameof(SampleRow.Category), "Категория", 88);
            ConfigureColumn(gridSamples, nameof(SampleRow.Mode), "Роль", 66);
            ConfigureColumn(gridSamples, nameof(SampleRow.Preview), "Реплика", 110);
            ConfigureColumn(gridSamples, nameof(SampleRow.State), "Состояние", 76);
            ConfigureColumn(gridSamples, nameof(SampleRow.Score), "Оценка", 55);
            if (gridSamples.Columns[nameof(SampleRow.Sample)] is { } internalData) internalData.Visible = false;
            gridSamples.ClearSelection();
            if (rows.Count == 0) return;
            var index = keep == null ? -1 : rows.ToList().FindIndex(row => row.Id == keep.Id && row.Number == keep.Number);
            var firstColumn = gridSamples.Columns.GetFirstColumn(DataGridViewElementStates.Visible);
            if (firstColumn == null) return;
            var displayRow = gridSamples.Rows[Math.Max(0, index)];
            gridSamples.CurrentCell = displayRow.Cells[firstColumn.Index];
            displayRow.Selected = true;
        }
        finally { binding = false; }
    }

    private OpenAiSample? SelectedSample => gridSamples.SelectedRows.Count == 1
        && gridSamples.SelectedRows[0].DataBoundItem is SampleRow item ? item.Sample : null;

    private async void Sample_Changed(object? sender, EventArgs e)
    {
        if (binding || !ready || operation != null || selectedPlan == null) return;
        await RunAsync(lblReviewStatus, ShowSampleAsync);
    }

    private async Task ShowSampleAsync(CancellationToken token)
    {
        var sample = SelectedSample;
        LoadGrade(sample?.Grade);
        if (sample == null || selectedPlan is not { } plan)
        {
            txtDetails.Text = bulkLog;
            return;
        }
        var scene = await Task.Run(() => campaign!.OpenAiSampleScene(plan.Id, sample.Id, sample.Number), token);
        var details = $"{sample.Text}\r\n\r\n{scene.ReplaceLineEndings("\r\n")}\r\n\r\nСостояние: {PresentState(sample.State)}";
        if (sample.Holdout) details += "\r\nКонтрольная сцена.";
        if (!plan.Pilot && sample.Diagnostics.Length > 0) details += $"\r\nДиагностика: {sample.Diagnostics}";
        if (!plan.Pilot && bulkLog.Length > 0) details += "\r\n\r\n" + bulkLog;
        txtDetails.Text = details;
    }

    private void LoadGrade(OpenAiGrade? saved)
    {
        nudScore.Value = saved?.Score ?? 0;
        txtNote.Text = saved?.Note ?? "";
        chkFacts.Checked = saved?.Facts ?? false;
        chkRoles.Checked = saved?.Roles ?? false;
        chkVoice.Checked = saved?.Voice ?? false;
        chkNatural.Checked = saved?.Natural ?? false;
        chkCategory.Checked = saved?.Category ?? false;
        chkConsent.Checked = saved?.AdultConsent ?? false;
        chkDistinct.Checked = saved?.Distinct ?? false;
    }

    private OpenAiGrade ReadGrade() => new((int)nudScore.Value, chkFacts.Checked, chkRoles.Checked, chkVoice.Checked,
        chkNatural.Checked, chkCategory.Checked, chkConsent.Checked, chkDistinct.Checked, txtNote.Text);

    private async void Grade_Click(object? sender, EventArgs e)
    {
        if (!btnSaveGrade.Enabled || selectedPlan is not { } plan || SelectedSample is not { } sample) return;
        var grade = ReadGrade();
        var key = txtKey.Text;
        await RunAsync(lblReviewStatus, async token =>
        {
            if (key.Length >= 8 && grade.Note.Contains(key, StringComparison.Ordinal))
                throw new InvalidDataException("OPENAI_GRADE_INVALID");
            await Task.Run(() => campaign!.GradeOpenAi(plan.Id, sample.Id, sample.Number, grade), token);
            if (plan.Pilot) reviewedPilotId = null;
            await ReadReviewAsync(page, sample, token);
        });
    }

    private async void ApproveRow_Click(object? sender, EventArgs e)
    {
        if (!btnApproveRow.Enabled || selectedPlan is not { } plan || SelectedSample is not { } sample
            || !Confirm("Одобрить эту реплику с сохранённой ручной оценкой для экспорта на ревью?")) return;
        await RunAsync(lblReviewStatus, async token =>
        {
            await Task.Run(() => campaign!.ApproveOpenAiRow(plan.Id, sample.Id, sample.Number, confirmed: true), token);
            await ReadReviewAsync(page, sample, token);
        });
    }

    private async void ApproveProfile_Click(object? sender, EventArgs e)
    {
        if (!btnApproveProfile.Enabled || selectedPlan is not { } plan) return;
        var format = SelectedFormat;
        var threshold = (int)nudQualityThreshold.Value;
        if (!Confirm($"Принять калибровку для формата {format} и порога качества {threshold}%?\r\n"
            + "Решение относится к модели, версии промпта и настройке взрослых категорий выбранного пилота.")) return;
        await RunAsync(lblReviewStatus, async token =>
        {
            var approval = await Task.Run(() => campaign!.ApproveOpenAiQuality(plan.Id, format, threshold, confirmed: true), token);
            lblReviewStatus.Text = $"Профиль {approval.Format} принят. Проверено: {approval.Reviewed}. Принято для выбранного формата: {approval.Accepted}.";
        });
    }

    private async void Export_Click(object? sender, EventArgs e)
    {
        if (!btnExport.Enabled || selectedPlan is not { } plan) return;
        if (plan.Pilot)
        {
            if (!Confirm("Открыть отчёт A/B и раскрыть в нём форматы после завершённого слепого ревью?")) return;
            await RunAsync(lblReviewStatus, async token =>
            {
                var report = await Task.Run(() =>
                {
                    var path = campaign!.SaveOpenAiCalibrationReport(plan.Id, confirmed: true);
                    using var file = new StreamReader(path);
                    var buffer = new char[262144];
                    var count = file.ReadBlock(buffer, 0, buffer.Length);
                    return new string(buffer, 0, count);
                }, token);
                reviewedPilotId = plan.Id;
                txtDetails.Text = report.ReplaceLineEndings("\r\n");
                lblReviewStatus.Text = "Отчёт A/B сохранён в частном workspace. Формат победителя выберите на вкладке «Кампания».";
            });
            return;
        }
        if (!Confirm($"Сохранить для ревью страницу {page + 1} списка одобренных реплик?\r\n"
            + "Экспорт нумерует одобренные реплики отдельно от общего списка результатов.")) return;
        await RunAsync(lblReviewStatus, async token =>
        {
            var path = await Task.Run(() => campaign!.ExportOpenAi(plan.Id, page, confirmed: true), token);
            lblReviewStatus.Text = "Пакет для ревью сохранён в частном workspace.";
            txtDetails.Text = path;
        });
    }

    private static string FormatBulkLog(IReadOnlyList<OpenAiCampaignRequestLog> log) =>
        log.Count == 0 ? "" : "Последние запросы\r\n" + string.Join("\r\n\r\n", log.Select(item =>
            $"{item.AtUtc:yyyy-MM-dd HH:mm:ss} UTC · {PresentState(item.State)} · {item.Model}\r\n"
            + $"Партия {item.Batch}, запрошено {item.Requested}, расход {item.CostUsd?.ToString("N6") ?? "не подтверждён"} USD.\r\n"
            + (item.Usage is { } usage ? $"Токены · вход {usage.Input}, кэш {usage.Cached}, выход {usage.Output}.\r\n" : "")
            + $"Промпт {item.PromptHash}\r\nСцена {item.SceneHash}"));
    private bool MatchesPlan(OpenAiCampaignPlan plan)
    {
        var settings = ReadSettings();
        return plan.Settings.Model == settings.Model && plan.Settings.Format == settings.Format
            && plan.Settings.Microbatch == settings.Microbatch && plan.Settings.QualityPercent == settings.QualityPercent
            && plan.Settings.MatureOptIn == settings.MatureOptIn && plan.Settings.TariffConfirmed && settings.TariffConfirmed
            && SameRates(plan.Settings.Price, settings.Price)
            && (plan.Pilot || plan.Target == (int)nudGoal.Value && !QuotasEdited);
    }

    private bool CanSpend(bool pilot) => !chkDryRun.Checked && (keySaved || txtKey.TextLength > 0)
        && ReadBudget().Remaining(pilot, 0, 0, 0, 0, 0) > 0;

    private bool SafeModelInput => cboModel.Text.Length > 0
        && (txtKey.TextLength == 0 || cboModel.Text.Trim() != txtKey.Text);

    private static bool CanResume(string state) => state is "RUNNING" or "STOPPED" or "CALIBRATION_INCOMPLETE_BUDGET"
        || state.StartsWith("PAUSED", StringComparison.Ordinal)
            && state is not ("PAUSED_INDETERMINATE" or "PAUSED_SECURITY_REDACTED" or "PAUSED_REFUSAL");

    private void UpdateActions()
    {
        var idle = ready && operation == null && !controlPending && !closeRequested;
        var plan = selectedPlan;
        var view = selectedView;
        var hasKey = keySaved || txtKey.TextLength > 0;
        var sample = SelectedSample;
        var operating = runningId != null && runnerReady && !controlPending && !controlRequested && !closeRequested;
        var mayRunPlan = idle && plan != null && view != null && MatchesPlan(plan) && SafeModelInput
            && CanSpend(plan.Pilot) && (plan.Pilot || qualityAccepted);
        var editablePlan = idle && plan is { Pilot: false } && view?.State == "READY";
        settingsLayout.Enabled = idle;
        campaignOptions.Enabled = idle;
        campaignModes.Enabled = idle;
        cboHistory.Enabled = idle;
        gridPlan.ReadOnly = !editablePlan;
        gridSamples.Enabled = idle;
        gradeLayout.Enabled = idle && sample != null;
        gradeChecks.Enabled = gradeLayout.Enabled;
        btnCheck.Enabled = idle && hasKey && SafeModelInput;
        btnSaveKey.Enabled = idle && vault != null && txtKey.TextLength > 0;
        btnDeleteKey.Enabled = idle && keySaved;
        btnSaveSettings.Enabled = idle && SafeModelInput;
        btnPlan.Enabled = idle && SafeModelInput;
        btnPilot.Enabled = idle && SafeModelInput && chkConfirmTariff.Checked && CanSpend(pilot: true);
        btnStart.Enabled = mayRunPlan && view!.State == "READY";
        btnResume.Enabled = mayRunPlan && CanResume(view!.State);
        btnPause.Enabled = operating;
        btnStop.Enabled = operating;
        btnResults.Enabled = idle;
        btnPrevious.Enabled = idle && plan is { Pilot: false } && page > 0;
        btnNext.Enabled = idle && plan is { Pilot: false } && page < MaxPage && sampleCount == BulkPageSize;
        btnSaveGrade.Enabled = idle && sample != null;
        btnApproveProfile.Enabled = idle && plan is { Pilot: true } && reviewedPilotId == plan.Id
            && view?.State == "CALIBRATION_COMPLETE";
        btnApproveRow.Enabled = idle && plan is { Pilot: false } && sample is { State: "PENDING", Grade.Pass: true }
            && sample.Grade == ReadGrade();
        btnExport.Text = plan is { Pilot: true } ? "Отчёт A/B" : "Экспорт для ревью";
        btnExport.Enabled = idle && plan != null && view != null && (plan.Pilot
            ? view.State == "CALIBRATION_COMPLETE" && view.Totals.Reviewed >= 30
            : view.Totals.Approved > (long)page * BulkPageSize);
    }

    private void RenderProgress(OpenAiCampaignView view, bool rebind)
    {
        selectedView = view;
        binding = true;
        try
        {
            if (rebind || quotas == null)
            {
                quotas = new BindingList<QuotaRow>(view.Quotas.Select(quota => new QuotaRow(quota)).ToList());
                gridPlan.DataSource = quotas;
                ConfigureColumn(gridPlan, nameof(QuotaRow.Label), "Категория", 120);
                ConfigureColumn(gridPlan, nameof(QuotaRow.Category), "Ключ категории", 120);
                ConfigureColumn(gridPlan, nameof(QuotaRow.Family), "Семейство", 80);
                ConfigureColumn(gridPlan, nameof(QuotaRow.Modes), "Роли", 80);
                ConfigureColumn(gridPlan, nameof(QuotaRow.Target), "Квота", 55, editable: true);
                ConfigureColumn(gridPlan, nameof(QuotaRow.Generated), "Получено", 65);
                ConfigureColumn(gridPlan, nameof(QuotaRow.Valid), "Валидно", 62);
                ConfigureColumn(gridPlan, nameof(QuotaRow.Unique), "Уникально", 75);
                ConfigureColumn(gridPlan, nameof(QuotaRow.Reviewed), "Проверено", 75);
                ConfigureColumn(gridPlan, nameof(QuotaRow.Approved), "Одобрено", 69);
                if (gridPlan.Columns[nameof(QuotaRow.OriginalTarget)] is { } hidden) hidden.Visible = false;
            }
            else
            {
                var progress = view.Quotas.ToDictionary(quota => quota.Category, StringComparer.Ordinal);
                foreach (var row in quotas)
                    if (progress.TryGetValue(row.Category, out var updated)) row.Progress = updated;
                quotas.ResetBindings();
            }
        }
        finally { binding = false; }
        var totals = view.Totals;
        var plan = selectedPlan;
        var kind = plan?.Pilot == true ? "A/B-пилот" : $"Кампания {plan?.Settings.Format}";
        var forecast = plan?.Settings.Price == null ? "нет тарифа" : $"{view.ForecastUsd:N4} USD";
        var batch = view.CurrentBatch == 0 ? "" : $" · Партия {view.CurrentBatch}: {view.InBatch}/{view.BatchTarget}";
        var division = Math.DivRem(OpenAiCampaignPlan.BatchSize, plan?.Settings.Microbatch ?? 20, out var tail);
        var structure = plan?.Pilot == true
            ? $"План пилота {plan.MinimumPosts} × 3 · предел {OpenAiCampaignPlan.PilotMaxPosts} запросов."
            : $"Логическая партия {OpenAiCampaignPlan.BatchSize} · запросы {division} × {plan?.Settings.Microbatch ?? 20}"
                + (tail > 0 ? $" + {tail}" : "") + " · 24 голоса V4.";
        var percent = plan?.Target > 0 ? 100m * totals.Unique / plan.Target : 0m;
        lblCampaignStatus.Text = $"{kind} · {PresentState(view.State)}{batch}\r\n"
            + structure + "\r\n"
            + $"Цель {plan?.Target:N0} ({percent:F1}%) · Запросы {totals.Requests:N0} · Получено {totals.Generated:N0} · "
            + $"Валидно {totals.Valid:N0} · Уникально {totals.Unique:N0} · Проверено {totals.Reviewed:N0} · "
            + $"Одобрено {totals.Approved:N0} · Завершено партий {totals.CompletedBatches:N0}\r\n"
            + $"По токенам {totals.CostUsd:N6} USD · Зарезервировано {totals.HeldUsd:N6} USD · "
            + $"Доступно в Studio {view.LocalAvailableUsd:N4} USD · Прогноз {forecast}.";
        if (plan is { Pilot: false } && !qualityAccepted)
            lblCampaignStatus.Text += "\r\nПлатный запуск требует принятого профиля для этой стратегии.";
    }

    private static void ConfigureColumn(DataGridView grid, string name, string caption, int minimum, bool editable = false)
    {
        if (grid.Columns[name] is not { } column) return;
        column.HeaderText = caption;
        column.MinimumWidth = minimum;
        column.ReadOnly = !editable;
        column.SortMode = DataGridViewColumnSortMode.NotSortable;
    }

    private bool Confirm(string message) => MessageBox.Show(this, message, "Кампания OpenAI", MessageBoxButtons.YesNo,
        MessageBoxIcon.Question, MessageBoxDefaultButton.Button2) == DialogResult.Yes;

    private async Task RunAsync(Label status, Func<CancellationToken, Task> action)
    {
        if (operation != null || controlPending || closeRequested) return;
        using var cancel = new CancellationTokenSource();
        operation = cancel;
        UpdateActions();
        try { await action(cancel.Token); }
        catch (OperationCanceledException) { status.Text = "Операция отменена."; }
        catch (Exception error) when (IsExpected(error)) { status.Text = SafeDiagnostic(error); }
        finally
        {
            operation = null;
            UpdateActions();
            FinishClose();
        }
    }

    private async void FormClosing_Handler(object? sender, FormClosingEventArgs e)
    {
        if (operation == null && !controlPending)
        {
            txtKey.Clear();
            return;
        }
        e.Cancel = true;
        if (closeRequested) return;
        closeRequested = true;
        UpdateActions();
        if (runningId != null && runnerReady)
        {
            if (!controlPending && !controlRequested) await RequestControlAsync("PAUSE");
        }
        else operation?.Cancel();
    }

    private void FinishClose()
    {
        if (!closeRequested || operation != null || controlPending || IsDisposed) return;
        closeRequested = false;
        BeginInvoke((Action)Close);
    }

    private static bool IsExpected(Exception error) => error is IOException or InvalidDataException or UnauthorizedAccessException
        or InvalidOperationException or ArgumentException or FormatException or System.Text.Json.JsonException or System.Data.Common.DbException;

    private static string SafeDiagnostic(Exception error) => error.Message switch
    {
        "OPENAI_KEY_INVALID" => "Проверьте формат ключа OpenAI.",
        "OPENAI_KEY_MISSING" => "Укажите ключ OpenAI.",
        "OPENAI_KEY_UNREADABLE" => "Сохранённый ключ недоступен для текущего пользователя Windows.",
        "OPENAI_KEY_ACL_UNSAFE" or "OPENAI_KEY_PATH_UNSAFE" => "Локальное хранилище ключа не прошло проверку безопасности.",
        "OPENAI_KEY_STORAGE_FAILED" or "OPENAI_KEY_PROTECT_FAILED" => "Не удалось обновить локальное хранилище ключа.",
        "AUTH" => "OpenAI отказал в доступе. Проверьте ключ и права проекта.",
        "BILLING" => "OpenAI сообщил об ограничении оплаты или квоты.",
        "HTTP_400" => "OpenAI отклонил параметры запроса.",
        "RATE_LIMIT" or "RATE_LIMIT_STOP" => "OpenAI ограничил частоту запросов.",
        "OPENAI_MODELS_EMPTY" => "Доступный каталог моделей пуст. Платный пилот не проверен.",
        "OPENAI_MODELS_UNAVAILABLE" or "OPENAI_RESPONSE_LIMIT" or "INDETERMINATE" => "Не удалось проверить каталог. Платный пилот не проверен.",
        "OPENAI_MODEL_INVALID" or "OPENAI_PRICE_MODEL" => "Проверьте точный ID модели и тариф для неё.",
        "OPENAI_PRICE_REQUIRED" => "Введите и подтвердите тариф OpenAI для выбранной модели.",
        "OPENAI_BUDGET_INVALID" => "Проверьте суммы локального гранта, резерва и лимитов.",
        "OPENAI_CATEGORY_TOTAL" => "Цель должна покрывать все категории по меньшей мере одной репликой.",
        "OPENAI_CATEGORY_QUOTAS" => "Все квоты должны быть положительными, а их сумма должна равняться цели.",
        "OPENAI_PLAN_DRIFT" or "OPENAI_QUALITY_EVIDENCE_DRIFT" => "План или калибровка изменились. Проверьте и выберите подходящий профиль.",
        "OPENAI_BULK_LOCKED" => "Платная кампания требует принятой калибровки для этой модели и стратегии.",
        "OPENAI_CALIBRATION_INCOMPLETE" or "OPENAI_NEED_30_BLIND_GRADES" => "Завершите пилот и внесите не менее 30 слепых оценок.",
        "OPENAI_QUALITY_BELOW_THRESHOLD" => "Формат не прошёл порог или проверки фактов, ролей и контрольных сцен.",
        "OPENAI_GRADE_INVALID" => "Проверьте оценку и комментарий. Комментарий ограничен 1000 символами.",
        "OPENAI_ROW_NEEDS_POSITIVE_REVIEW" or "OPENAI_ROW_NOT_APPROVABLE" => "Для одобрения нужна сохранённая положительная оценка валидной реплики.",
        "OPENAI_NO_APPROVED_ROWS" => "На этой странице списка одобренных реплик нет.",
        "OPENAI_EXPORT_V4_INVALID" => "Экспортированный пакет не прошёл локальную проверку V4.",
        "OPENAI_RECEIPT_SCOPE_DRIFT" => "Факты сцены не совпадают с версией, записанной в журнале.",
        _ => "Операция не выполнена. Проверьте настройки и доступ к частному workspace."
    };

    private static string PresentState(string state) => state switch
    {
        "READY" => "План готов",
        "RUNNING" => "Выполняется",
        "PAUSED" => "Пауза",
        "STOPPED" => "Остановлено",
        "TARGET_REACHED" => "Цель достигнута",
        "CALIBRATION_COMPLETE" => "Пилот завершён, требуется ручная оценка",
        "CALIBRATION_INCOMPLETE_BUDGET" or "PAUSED_BUDGET" => "Запрос превышает оставшийся бюджет",
        "CALIBRATION_INCOMPLETE_OUTPUT" => "В части сцен нет валидных уникальных реплик",
        "CALIBRATION_INCOMPLETE_LIMIT" => "Достигнут предел запросов пилота",
        "PAUSED_INDETERMINATE" => "Пауза. Требуется сверка запроса и расхода в кабинете OpenAI",
        "PAUSED_RATE_LIMIT" or "PAUSED_RATE_LIMIT_STOP" or "RATE_LIMIT" or "RATE_LIMIT_STOP" => "Ограничена частота запросов",
        "PAUSED_QUALITY_REGRESSION" => "Пауза. Качество ниже принятого порога",
        "PAUSED_RESOURCE" => "Пауза. Проверьте хранилище и свободное место",
        "PAUSED_AUTH" or "AUTH" => "Доступ отклонён",
        "PAUSED_BILLING" or "BILLING" => "Ограничение оплаты или квоты",
        "PAUSED_HTTP_400" or "HTTP_400" => "Параметры запроса отклонены",
        "PAUSED_REFUSAL" or "REFUSAL" => "OpenAI отказался выдать результат",
        "PAUSED_SECURITY_REDACTED" or "SECURITY_REDACTED" => "Ответ остановлен проверкой безопасности",
        "PENDING" => "Черновик",
        "REJECTED" => "Отклонено валидатором",
        "APPROVED" => "Одобрено для ревью",
        "ISSUED" => "Запрос отправлен",
        "RECEIVED" => "Ответ получен",
        "DRAFT_SAVED" => "Черновики сохранены",
        "INVALID_OUTPUT" => "Невалидный ответ",
        _ => "Требуется проверка журнала"
    };

    private sealed record HistoryItem(string Id, string Caption)
    {
        public override string ToString() => Caption;
    }

    private sealed class QuotaRow(OpenAiQuotaProgress quota)
    {
        internal OpenAiQuotaProgress Progress { get; set; } = quota;
        public string Category => Progress.Category;
        public string Label => Progress.Label;
        public string Family => Progress.Family;
        public string Modes => Progress.Modes;
        public int OriginalTarget => Progress.Target;
        public int Target { get; set; } = quota.Target;
        public long Generated => Progress.Generated;
        public long Valid => Progress.Valid;
        public long Unique => Progress.Unique;
        public long Reviewed => Progress.Reviewed;
        public long Approved => Progress.Approved;
    }

    private sealed record SampleRow(OpenAiSample Sample)
    {
        public string Category => Sample.Category;
        public string Mode => Sample.Mode;
        public string Preview => Sample.Text;
        public string State => PresentState(Sample.State);
        public int? Score => Sample.Grade?.Score;
    }
}
