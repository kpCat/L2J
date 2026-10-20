using PhantomSemanticStudio.Core;

namespace PhantomSemanticStudio.WinForms;

public partial class V3ProposalForm : Form
{
    private WorkspaceStore? store;
    private PackSnapshot? pack;
    private IReadOnlyList<Candidate> peers = [];
    private IReadOnlyList<string> selectedIds = [];
    private CancellationTokenSource? operation;
    private long version;
    private bool binding;
    private string? currentStage;
    private string? proofPath;
    private string? proofHash;
    public string? StageRoot => currentStage;
    public V3ProposalForm()
    {
        InitializeComponent();
    }
    public void SetContext(WorkspaceStore workspace, PackSnapshot snapshot, IReadOnlyList<Candidate> candidates)
    {
        if (operation != null) throw new InvalidOperationException("Дождитесь отмены операции.");
        store = workspace; pack = snapshot; peers = candidates; selectedIds = []; currentStage = null; proofPath = null; proofHash = null;
        lblSource.Text = "SOURCE: " + snapshot.Fingerprint + "\r\nSTAGED_V3_UNVALIDATED / Java NOT_RUN / NOT_INSTALLED / NOT_RUNTIME_PARITY";
        ResetConsents(); UpdateActions();
    }
    private WorkspaceStore Store => store ?? throw new InvalidOperationException("Workspace не открыт.");
    private PackSnapshot Pack => pack ?? throw new InvalidOperationException("Нет импортированного источника.");
    private async void Proposal_Shown(object? sender, EventArgs e)
    {
        if (pack == null) return;
        await RunAsync(async token =>
        {
            var pairs = await Task.Run(() => V3ProposalContract.GetPairs(Pack, token), token); token.ThrowIfCancellationRequested();
            binding = true; try { cmbPair.Items.Clear(); cmbPair.Items.AddRange(pairs.ToArray()); cmbPair.SelectedIndex = -1; } finally { binding = false; }
            lblStatus.Text = "Выберите вручную существующую пару manifest и точные 1–20 CURRENT APPROVED.";
        });
    }
    private void Select_Click(object? sender, EventArgs e)
    {
        if (operation != null || pack == null) return;
        using var selection = new StageSelectionForm(); selection.SetCandidates(peers, Pack.Fingerprint);
        if (selection.ShowDialog(this) != DialogResult.OK) return;
        selectedIds = StageBatchSelection.SelectExactIds(peers, selection.SelectedIds); ResetConsents(); RenderSelection();
    }
    private void Pair_Changed(object? sender, EventArgs e)
    { if (binding) return; version++; operation?.Cancel(); ResetConsents(); RenderSelection(); }
    private void ResetConsents()
    {
        binding = true; try { chkSelection.Checked = false; chkEditorial.Checked = false; chkRelease.Checked = false; } finally { binding = false; }
        UpdateActions();
    }
    private void RenderSelection()
    {
        var pair = cmbPair.SelectedItem as V3SegmentPair;
        txtPreview.Text = (pair == null ? "Точная пара ещё не выбрана." : $"Category: {pair.Category}\r\n{pair.SemanticPath}\r\n{pair.ConversationPath}")
            + "\r\n\r\n" + string.Join("\r\n\r\n", selectedIds.Select(id => peers.Single(c => c.Id == id)).Select(c => $"{c.Id} / {c.Kind}\r\n{c.Topic} / {c.Act} / {c.Band} / {c.Register} / {c.Gender}\r\n{c.Text}"));
        UpdateActions();
    }
    private void Consent_Changed(object? sender, EventArgs e)
    { if (binding) return; version++; operation?.Cancel(); UpdateActions(); }
    private async void Stage_Click(object? sender, EventArgs e)
    {
        if (!chkSelection.Checked || !chkEditorial.Checked || cmbPair.SelectedItem is not V3SegmentPair pair || selectedIds.Count == 0) return;
        var ids = selectedIds.ToArray(); var captured = version;
        await RunAsync(async token =>
        {
            var stage = await Task.Run(() => new IsolatedV3ProposalStager().Create(Store, Pack, peers, ids, pair, true, true, token), token);
            token.ThrowIfCancellationRequested(); if (captured != version) return;
            currentStage = stage.Root; proofPath = null; proofHash = null; ResetConsents();
            txtProof.Text = "Java NOT_RUN. Отдельно вручную запустите scripts/Test-PSS008-V3-Java.ps1 -StageRoot с этим finished path.\r\n" + stage.Root;
            lblStatus.Text = "STAGED_V3_UNVALIDATED / НЕ ДЛЯ УСТАНОВКИ. Source/session не изменены.";
        });
    }
    private async void Proof_Click(object? sender, EventArgs e)
    {
        if (operation != null) return;
        using var dialog = new OpenFileDialog { Title = "Выберите immutable Java proof из workspace/v3-proposals/id/oracle-id", Filter = "Java proof (java-validation.json)|java-validation.json", CheckFileExists = true };
        if (dialog.ShowDialog(this) != DialogResult.OK) return;
        var path = dialog.FileName; var stage = Path.GetDirectoryName(Path.GetDirectoryName(path))!;
        proofPath = null; proofHash = null; chkRelease.Checked = false;
        await RunAsync(async token =>
        {
            var hash = await Task.Run(() => V3ReleaseHandoff.InspectProof(Store, stage, path, token), token); token.ThrowIfCancellationRequested();
            var receipt = await Task.Run(() => V3ProposalContract.ReadStage(Store.Root, stage, token), token); token.ThrowIfCancellationRequested();
            var preview = "PASS_JAVA_STAGED_V3 / JAVA_CONTENT_ONLY_NOT_RUNTIME_READY\r\nNOT_INSTALLED\r\nProof SHA: " + hash
                + "\r\nSource fingerprint: " + receipt.SourceFingerprint + "\r\n\r\n" + string.Join("\r\n", receipt.StagedFiles.Where(f => receipt.SourceFiles.Single(s => s.RelativePath == f.RelativePath) != f)
                    .Select(f => $"{f.RelativePath}\r\nOriginal SHA: {receipt.SourceFiles.Single(s => s.RelativePath == f.RelativePath).Sha256}\r\nProposed SHA: {f.Sha256} ({f.Bytes} bytes)"));
            token.ThrowIfCancellationRequested();
            currentStage = stage; proofPath = path; proofHash = hash; txtProof.Text = preview;
            lblStatus.Text = "Проверьте exact files/hashes и отдельно подтвердите только offline handoff.";
        });
    }
    private async void Release_Click(object? sender, EventArgs e)
    {
        if (!chkRelease.Checked || currentStage == null || proofPath == null || proofHash == null) return;
        var stage = currentStage; var path = proofPath; var hash = proofHash; var captured = version;
        await RunAsync(async token =>
        {
            var result = await Task.Run(() => V3ReleaseHandoff.Prepare(Store, stage, path, hash, true, token), token);
            token.ThrowIfCancellationRequested(); if (captured != version) return;
            ResetConsents(); txtProof.Text += "\r\n\r\nOFFLINE_HANDOFF_NOT_INSTALLED\r\n" + result;
            lblStatus.Text = "Только proposed/backup + manifest/checklist. Установки нет; нужен будущий отдельный ручной процесс.";
        });
    }
    private void UpdateActions()
    {
        btnSelect.Enabled = operation == null && pack != null; cmbPair.Enabled = operation == null;
        btnStage.Enabled = operation == null && selectedIds.Count > 0 && cmbPair.SelectedItem is V3SegmentPair && chkSelection.Checked && chkEditorial.Checked;
        btnProof.Enabled = operation == null && store != null;
        btnRelease.Enabled = operation == null && currentStage != null && proofPath != null && proofHash != null && chkRelease.Checked;
    }
    private async Task RunAsync(Func<CancellationToken, Task> action)
    {
        if (operation != null) return; var job = new CancellationTokenSource(); operation = job; btnCancel.Enabled = true; UpdateActions();
        try { await action(job.Token); }
        catch (OperationCanceledException) { lblStatus.Text = "Отменено. Без автоматического повтора."; }
        catch (Exception ex) { proofPath = null; proofHash = null; chkRelease.Checked = false; lblStatus.Text = "Операция отклонена: " + ex.Message; }
        finally { job.Dispose(); operation = null; if (!IsDisposed) { btnCancel.Enabled = false; UpdateActions(); } }
    }
    private void Cancel_Click(object? sender, EventArgs e) { version++; operation?.Cancel(); }
    private void Proposal_Closing(object? sender, FormClosingEventArgs e) { version++; if (operation != null) { operation.Cancel(); e.Cancel = true; } }
}
