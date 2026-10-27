using PhantomSemanticStudio.Core;

namespace PhantomSemanticStudio.WinForms;

public partial class V4ImportForm : Form
{
    private V4Workspace? workspace;
    private string workspaceRoot = "";
    private string? validatedFingerprint;
    private V4ProposalResult? proposal;
    private bool busy;
    public bool Busy => busy;
    public string CurrentStage { get; private set; } = "";
    private V4Workspace Workspace => workspace ?? throw new InvalidOperationException("Workspace не открыт.");
    private sealed record Choice(string Key, string Label)
    {
        public override string ToString() => Label;
    }

    public V4ImportForm()
    {
        InitializeComponent();
    }

    public void SetContext(string root, string protectedSource)
    {
        if (busy) throw new InvalidOperationException("Дождитесь завершения операции.");
        workspaceRoot = PathSafety.Canonical(root);
        workspace = new V4Workspace(workspaceRoot, protectedSource);
        RefreshCategories();
        cmbVoice.Items.Clear();
        cmbVoice.Items.AddRange(V4Registry.Default.Voices.Select(v => new Choice(v.Key, v.Label)).ToArray());
        cmbVoice.SelectedItem = cmbVoice.Items.Cast<Choice>().First(v => v.Key == "calm");
        cmbRelation.SelectedIndex = 0;
        UpdateActions();
    }
    private void RefreshCategories()
    {
        cmbCategory.Items.Clear();
        cmbCategory.Items.AddRange(Workspace.Categories().Select(c => new Choice(c.Key, c.Label + " — " + c.Key)).ToArray());
        if (cmbCategory.Items.Count > 0) cmbCategory.SelectedIndex = 0;
    }
    private void File_Click(object? sender, EventArgs e)
    {
        using var dialog = new OpenFileDialog { Filter = "Файл ChatGPT (*.jsonl)|*.jsonl", CheckFileExists = true };
        if (dialog.ShowDialog(this) == DialogResult.OK) txtInput.Text = dialog.FileName;
    }
    private void Folder_Click(object? sender, EventArgs e)
    {
        using var dialog = new FolderBrowserDialog { Description = "Папка с JSONL batch-файлами", UseDescriptionForTitle = true };
        if (dialog.ShowDialog(this) == DialogResult.OK) txtInput.Text = dialog.SelectedPath;
    }
    private void Input_Changed(object? sender, EventArgs e)
    {
        validatedFingerprint = null;
        UpdateActions();
    }
    private async void Validate_Click(object? sender, EventArgs e)
    {
        var input = txtInput.Text;
        validatedFingerprint = null;
        await RunAsync(async () =>
        {
            var report = await Task.Run(() => Workspace.Import(input, dryRun: true));
            ShowReport(report);
            if (report.Success) validatedFingerprint = report.InputHash;
        });
    }
    private async void Accept_Click(object? sender, EventArgs e)
    {
        if (validatedFingerprint == null) return;
        var expected = validatedFingerprint;
        var input = txtInput.Text;
        validatedFingerprint = null;
        await RunAsync(async () =>
        {
            var report = await Task.Run(() => Workspace.Import(input, false, expected));
            ShowReport(report);
            CurrentStage = "";
        });
    }
    private void ShowReport(V4Report report)
    {
        if (report.Entries.Count > 0)
            cmbCategory.SelectedItem = cmbCategory.Items.Cast<Choice>().First(c => c.Key == report.Entries[0].Category);
        txtReport.Text = $"{(report.Success ? "ПРОВЕРЕНО" : "ОТКЛОНЕНО")}\r\nНовых записей: {report.Accepted}; дубли: {report.Duplicates}\r\n"
            + string.Join("\r\n", report.Diagnostics.Take(1000))
            + "\r\n\r\n" + string.Join("\r\n", report.Entries.GroupBy(e => (e.Category, e.Mode)).Select(g => $"{g.Key.Category} / {g.Key.Mode}: {g.Count()}"))
            + "\r\n\r\nПримеры для редакционного просмотра:\r\n" + string.Join("\r\n", report.Entries.Take(12).Select(e => $"{e.Mode} / {e.Stance}: {e.Text}"))
            + "\r\n\r\nЛокальная проверка не подтверждает художественное качество и понимание произвольного текста.";
    }
    private async void Build_Click(object? sender, EventArgs e)
    {
        await RunAsync(async () =>
        {
            var built = await Task.Run(Workspace.Build);
            var verified = await Task.Run(() => Workspace.Verify(built.StagePath));
            if (!verified.Success) throw new InvalidDataException(string.Join("\r\n", verified.Diagnostics));
            CurrentStage = built.StagePath;
            var stats = await Task.Run(Workspace.Stats);
            txtReport.Text = $"STAGED / LOCAL_VERIFY_PASS / JAVA_NOT_RUN / NOT_INSTALLED\r\n{built.StagePath}\r\nManifest SHA256: {built.ManifestHash}\r\nВсего: {stats.Total}\r\n"
                + $"Зарегистрировано категорий: {stats.Coverage.Count}. Ниже категории с импортированным содержимым:\r\n"
                + string.Join("\r\n", stats.Coverage.Where(c => c.IncomingTriggers + c.Responses + c.Initiatives > 0).Select(c => $"{c.Category}: входы={c.IncomingTriggers}, ответы={c.Responses}, инициативы={c.Initiatives}"));
        });
    }
    private async void Passport_Click(object? sender, EventArgs e)
    {
        await RunAsync(async () =>
        {
            var folder = PathSafety.ResolveRelative(workspaceRoot, "passports");
            Directory.CreateDirectory(folder);
            var destination = PathSafety.ResolveRelative(folder, "CHATGPT_GENERATION_RU-" + Guid.NewGuid().ToString("N") + ".md");
            await Task.Run(() => Workspace.ExportPassport(destination));
            txtReport.Text = "Актуальный единый паспорт сохранён. Прикрепите этот файл к новому чату ChatGPT:\r\n" + destination;
        });
    }
    private async void Preview_Click(object? sender, EventArgs e)
    {
        if (cmbCategory.SelectedItem is not Choice category || cmbVoice.SelectedItem is not Choice voice) return;
        var relation = cmbRelation.Text;
        await RunAsync(async () =>
        {
            var selected = await Task.Run(() => Workspace.Preview(category.Key, voice.Key, relation));
            txtReport.Text = "C# preview — редакционный пример; настоящая runtime-проверка выполняется отдельно Java consumer.\r\n"
                + (selected == null ? "Нет допустимого ответа в выбранном контексте." : $"{selected.Category} / {selected.Stance} / {selected.Id}\r\n{selected.Text}");
        });
    }
    private void Proposal_Changed(object? sender, EventArgs e)
    {
        proposal = null;
        chkReviewed.Checked = false;
        UpdateActions();
    }
    private void Reviewed_Changed(object? sender, EventArgs e) => UpdateActions();
    private async void Propose_Click(object? sender, EventArgs e)
    {
        var parent = txtParent.Text; var key = txtCategoryKey.Text; var label = txtCategoryLabel.Text;
        proposal = null; chkReviewed.Checked = false;
        await RunAsync(async () =>
        {
            proposal = await Task.Run(() => Workspace.Propose(parent, key, label, key + ".reply", key + ".observe"));
            txtReport.Text = $"ПРЕДЛОЖЕНИЕ — ещё не зарегистрировано\r\nРодитель: {parent}\r\nKey: {key}\r\nНазвание: {label}\r\nAct: {key}.reply\r\nIntent: {key}.observe\r\nSHA256: {proposal.Hash}\r\nПроверьте смысл и отсутствие дублирующей категории, затем отметьте подтверждение и зарегистрируйте.";
        });
    }
    private async void Approve_Click(object? sender, EventArgs e)
    {
        if (proposal == null || !chkReviewed.Checked) return;
        var reviewed = proposal;
        proposal = null; chkReviewed.Checked = false;
        await RunAsync(async () =>
        {
            await Task.Run(() => Workspace.Approve(reviewed.Path, reviewed.Hash, reviewed: true));
            RefreshCategories();
            validatedFingerprint = null;
            txtReport.Text = "Категория зарегистрирована в workspace. Экспортируйте обновлённый паспорт. Для running игры нужны отдельные build/verify и операторское подключение.";
        });
    }
    private void UpdateActions()
    {
        if (IsDisposed || btnAccept == null) return;
        inputRow.Enabled = actions.Enabled = previewRow.Enabled = taxonomy.Enabled = !busy;
        btnAccept.Enabled = !busy && validatedFingerprint != null;
        btnApprove.Enabled = !busy && proposal != null && chkReviewed.Checked;
    }
    private async Task RunAsync(Func<Task> action)
    {
        if (busy) return;
        busy = true; UpdateActions();
        try { await action(); }
        catch (Exception ex) when (ex is IOException or InvalidDataException or ArgumentException or UnauthorizedAccessException or InvalidOperationException)
        {
            validatedFingerprint = null;
            txtReport.Text = "Операция отклонена: " + ex.Message;
        }
        finally { busy = false; if (!IsDisposed) UpdateActions(); }
    }
    private void Import_FormClosing(object? sender, FormClosingEventArgs e)
    {
        if (busy) { e.Cancel = true; txtReport.AppendText("\r\nДождитесь завершения атомарной операции, затем закройте окно."); }
    }
}
