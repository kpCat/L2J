using PhantomSemanticStudio.Core;

namespace PhantomSemanticStudio.WinForms;

public partial class StageSelectionForm : Form
{
    private const int MaximumVisible = 500;
    private List<Candidate> peers = [];
    private List<SelectionRow> rows = [];
    private readonly HashSet<string> checkedIds = new(StringComparer.Ordinal);
    private bool binding;
    private string sourceFingerprint = "";
    private sealed record SelectionRow(Candidate Data, bool CurrentApproval, bool CurrentBaseline);

    public StageSelectionForm()
    {
        InitializeComponent();
    }

    public IReadOnlyList<string> SelectedIds { get; private set; } = Array.AsReadOnly(Array.Empty<string>());
    public string SelectedSummary => txtSelected.Text;

    public void SetCandidates(IReadOnlyList<Candidate> candidates, string fingerprint)
    {
        if (candidates == null || candidates.Any(c => c == null) || string.IsNullOrWhiteSpace(fingerprint))
            throw new InvalidDataException("Нужны кандидаты и отпечаток импортированного источника.");
        peers = candidates.Select(c => c.Copy()).ToList();
        sourceFingerprint = fingerprint;
        rows = peers.Where(c => c.Status == "APPROVED").OrderBy(c => c.Id, StringComparer.Ordinal)
            .Select(c => new SelectionRow(c, CandidateReview.IsCurrent(c), c.SourceFingerprint == sourceFingerprint)).ToList();
        checkedIds.Clear(); SelectedIds = Array.AsReadOnly(Array.Empty<string>());
        txtFilter.Clear(); BindRows(); UpdateSelection();
    }

    // Separate exact-list decision, with a scrollable full preview and default No.
    // Reuses Designer-authored controls; no runtime UI construction or filesystem access.
    public void ConfirmExactSelection(IReadOnlyList<string> ids)
    {
        var exact = ValidateSelection(ids);
        checkedIds.Clear(); checkedIds.UnionWith(exact);
        txtFilter.Clear(); BindRows(); UpdateSelection();
        txtFilter.Enabled = false; listCandidates.Enabled = false;
        Text = "Подтверждение точного списка";
        lblInstructions.Text = "Проверьте ВСЕ выбранные ID и исходные тексты справа. Одна несовместимая запись блокирует всю партию. Создать отдельное предложение?";
        btnCreate.Text = "Да, создать предложение"; btnCancel.Text = "Нет";
        AcceptButton = btnCancel;
    }

    private IReadOnlyList<string> ValidateSelection(IEnumerable<string> ids)
    {
        var exact = StageBatchSelection.SelectExactIds(peers, ids);
        foreach (var id in exact)
            if (peers.Single(c => c.Id == id).SourceFingerprint != sourceFingerprint)
                throw new InvalidDataException("Устаревший baseline: " + id + ". Импортируйте источник и выполните новое ручное ревью.");
        return exact;
    }

    private void Filter_TextChanged(object? sender, EventArgs e) => BindRows();

    private void BindRows()
    {
        var query = txtFilter.Text.Trim();
        var found = rows.Where(r => Matches(r.Data, query)).ToList();
        binding = true; listCandidates.BeginUpdate();
        try
        {
            listCandidates.Items.Clear();
            foreach (var row in found.Take(MaximumVisible))
            {
                var c = row.Data;
                var item = new ListViewItem(c.Id) { Tag = row, Checked = checkedIds.Contains(c.Id) };
                item.SubItems.Add(c.Kind); item.SubItems.Add(c.Topic + " / " + c.Act);
                item.SubItems.Add(c.Gender + " / " + c.Band + " / " + c.Register);
                item.SubItems.Add(c.Text.Length > 80 ? c.Text[..80] + "…" : c.Text);
                item.SubItems.Add(ReviewState(row) + " / " + BaselineState(row) + " / " + c.ReviewNote);
                listCandidates.Items.Add(item);
            }
        }
        finally { listCandidates.EndUpdate(); binding = false; }
        lblAvailable.Text = $"APPROVED: {rows.Count}; найдено: {found.Count}; показано: {Math.Min(found.Count, MaximumVisible)} (первые {MaximumVisible}). Поиск не снимает отметки.";
        txtDetails.Text = "Выберите строку для полного текста, отметки редактора и baseline. Используйте горизонтальную прокрутку для всех столбцов.";
    }

    private static bool Matches(Candidate c, string query) => query.Length == 0
        || c.Id.Contains(query, StringComparison.OrdinalIgnoreCase) || c.Text.Contains(query, StringComparison.OrdinalIgnoreCase)
        || c.Kind.Contains(query, StringComparison.OrdinalIgnoreCase) || c.Topic.Contains(query, StringComparison.OrdinalIgnoreCase)
        || c.Act.Contains(query, StringComparison.OrdinalIgnoreCase);

    private void Candidates_ItemCheck(object? sender, ItemCheckEventArgs e)
    {
        if (binding || listCandidates.Items[e.Index].Tag is not SelectionRow row) return;
        if (e.NewValue == CheckState.Checked) checkedIds.Add(row.Data.Id);
        else checkedIds.Remove(row.Data.Id);
        // ItemCheck fires before the control changes state: NewValue + ID is authoritative.
        UpdateSelection();
    }

    private void Candidates_SelectedIndexChanged(object? sender, EventArgs e)
    {
        if (binding || listCandidates.SelectedItems.Count == 0) return;
        if (listCandidates.SelectedItems[0].Tag is SelectionRow row) txtDetails.Text = Describe(row);
    }

    private void UpdateSelection()
    {
        lblCount.Text = $"Выбрано: {checkedIds.Count} / {StageBatchSelection.MaxItems}";
        txtSelected.Text = string.Join("\r\n\r\n", rows.Where(r => checkedIds.Contains(r.Data.Id)).Select(Describe));
        btnCreate.Enabled = false;
        try
        {
            ValidateSelection(checkedIds);
            txtError.Text = "Партия выбрана явно. Совместимость XML и source повторно проверит Stager; Java NOT_RUN.";
            btnCreate.Enabled = true;
        }
        catch (InvalidDataException ex) { txtError.Text = ex.Message; }
    }

    private static string ReviewState(SelectionRow row) => row.CurrentApproval ? "АКТУАЛЬНОЕ ОДОБРЕНИЕ" : "СТАРОЕ ОДОБРЕНИЕ — переодобрите";
    private static string BaselineState(SelectionRow row) => row.CurrentBaseline ? "ТЕКУЩИЙ BASELINE" : "УСТАРЕВШИЙ BASELINE";
    private static string Describe(SelectionRow row)
    {
        var c = row.Data;
        return $"{c.Id}\r\n{c.Kind} • {c.Topic} / {c.Act} • {c.Gender} / {c.Band} / {c.Register}\r\n"
            + $"{ReviewState(row)}; {BaselineState(row)}\r\nBaseline: {c.SourceFingerprint}\r\n"
            + $"Редактор: {c.ReviewNote}\r\n\r\n{c.Text}";
    }

    private void Create_Click(object? sender, EventArgs e)
    {
        try
        {
            SelectedIds = ValidateSelection(checkedIds);
            DialogResult = DialogResult.OK;
            Close();
        }
        catch (InvalidDataException ex) { SelectedIds = Array.AsReadOnly(Array.Empty<string>()); btnCreate.Enabled = false; txtError.Text = ex.Message; }
    }

    private void Selection_FormClosing(object? sender, FormClosingEventArgs e)
    {
        if (DialogResult != DialogResult.OK) { DialogResult = DialogResult.Cancel; SelectedIds = Array.AsReadOnly(Array.Empty<string>()); }
    }
}
