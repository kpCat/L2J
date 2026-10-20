using PhantomSemanticStudio.Core;

namespace PhantomSemanticStudio.WinForms;

public partial class PackQualityForm : Form
{
    private PackSnapshot? pack;
    private CancellationTokenSource? operation;
    private long version;
    private bool binding;
    private int sortColumn;
    private bool descending;
    public PackCoverage? Result { get; private set; }

    public PackQualityForm()
    {
        InitializeComponent();
    }

    public void SetPack(PackSnapshot snapshot)
    {
        version++; operation?.Cancel(); Result = null;
        pack = snapshot with { Files = snapshot.Files.ToList(), Entries = snapshot.Entries.ToList() };
        lblSource.Text = "SOURCE / ADVISORY / NOT_RUNTIME_PARITY / NOT_INSTALLED\r\n" + pack.Fingerprint;
        binding = true;
        try
        {
            Fill(cmbTopic, pack.Topics);
            Fill(cmbAct, pack.Acts); Fill(cmbBand, CandidateValidator.Bands); Fill(cmbRegister, CandidateValidator.Registers);
            Fill(cmbSource, ["v1", "v2", "v3", "custom", "mixed", "declared"]);
        }
        finally { binding = false; }
        listRows.Items.Clear(); btnAnalyze.Enabled = true;
    }
    private static void Fill(ComboBox combo, IEnumerable<string> values)
    { combo.Items.Clear(); combo.Items.Add("Все"); combo.Items.AddRange(values.Where(v => v.Length > 0).Distinct().Order(StringComparer.Ordinal).ToArray()); combo.SelectedIndex = 0; }
    private async void Analyze_Click(object? sender, EventArgs e)
    {
        if (pack == null || operation != null) return;
        var expected = pack; var currentVersion = ++version; var job = new CancellationTokenSource(); operation = job;
        btnCancel.Enabled = true; btnAnalyze.Enabled = false; lblStatus.Text = "Читаю SHA и рассчитываю bounded карту…";
        try
        {
            var report = await Task.Run(() =>
            {
                var current = new PackReader().Load(expected.ModuleRoot, job.Token);
                if (current.Fingerprint != expected.Fingerprint || !current.Files.SequenceEqual(expected.Files)) throw new InvalidDataException("STALE_SOURCE: нужен новый импорт.");
                return PackCoverageAnalyzer.Analyze(current, job.Token);
            }, job.Token);
            job.Token.ThrowIfCancellationRequested();
            if (version != currentVersion || IsDisposed || Disposing) return;
            Result = report; BindRows(); lblStatus.Text = "A_LOCAL_QUALITY_PASS / NOT_SEMANTIC_VERIFIED; лексический sample ≤256, сравнений " + report.LexicalComparisons;
            txtDetails.Text = "TOPIC/ACT: все объявленные scopes, включая нулевое покрытие. MISSING_PATTERN: нет pattern; ORPHAN_TEMPLATE: ответ без pattern для act.\r\n"
                + "MISSING_TEMPLATE: нет clean ответа для act. LOW_DIVERSITY: меньше 3 разных clean ответов.\r\n"
                + "NEAR_DUPLICATE_POSSIBLE: ограниченное лексическое сравнение, не смысловое доказательство.\r\n"
                + "Band — фильтр выбора, не владелец персонажа; topic у TEMPLATE и runtime gender отсутствуют. Social/functional/history не симулируются.\r\n\r\n"
                + string.Join("\r\n", report.Capacities.Where(c => !c.Name.Contains("_BUCKET|", StringComparison.Ordinal) || c.Status != "WITHIN_CAPACITY")
                    .Select(c => $"{c.Name}: {c.Used}/{c.Limit}; осталось {c.Remaining}; {c.Status}"));
        }
        catch (OperationCanceledException) { if (!IsDisposed) lblStatus.Text = "Отменено. Предыдущий результат сохранён."; }
        catch (Exception ex) { if (!IsDisposed) lblStatus.Text = "Анализ не завершён: " + ex.Message; }
        finally { job.Dispose(); operation = null; if (!IsDisposed) { btnCancel.Enabled = false; btnAnalyze.Enabled = true; } }
    }
    private void Filter_Changed(object? sender, EventArgs e) { if (!binding) BindRows(); }
    private void BindRows()
    {
        if (Result == null) return;
        static bool Match(ComboBox c, string value) => c.SelectedIndex <= 0 || c.Text == value;
        listRows.BeginUpdate();
        try
        {
            listRows.Items.Clear();
            foreach (var r in Result.Rows.Where(r => Match(cmbTopic, r.Topic) && Match(cmbAct, r.Act)
                && Match(cmbBand, r.Band) && Match(cmbRegister, r.Register) && Match(cmbSource, r.Source)))
            {
                var item = new ListViewItem(r.Kind) { Tag = r };
                item.SubItems.AddRange([r.Topic, r.Act, r.Band, r.Register, r.Source, r.Count.ToString(), r.Clean.ToString(), r.Diversity.ToString(), r.Warning]);
                listRows.Items.Add(item);
            }
            listRows.Sort();
        }
        finally { listRows.EndUpdate(); }
    }
    private sealed class RowComparer(int column, bool descending) : System.Collections.IComparer
    {
        public int Compare(object? a, object? b)
        {
            var left = ((ListViewItem)a!).SubItems[column].Text; var right = ((ListViewItem)b!).SubItems[column].Text;
            var result = column is >= 6 and <= 8 ? int.Parse(left).CompareTo(int.Parse(right)) : StringComparer.Ordinal.Compare(left, right);
            return descending ? -result : result;
        }
    }
    private void Sort_Click(object? sender, ColumnClickEventArgs e)
    { descending = sortColumn == e.Column && !descending; sortColumn = e.Column; listRows.ListViewItemSorter = new RowComparer(sortColumn, descending); }
    private void Row_Selected(object? sender, EventArgs e)
    {
        if (listRows.SelectedItems.Count == 1 && listRows.SelectedItems[0].Tag is CoverageRow r)
            txtDetails.Text = $"{r.Warning}\r\nKind {r.Kind}, topic {r.Topic}, act {r.Act}, {r.Band}/{r.Register}, source {r.Source}\r\n"
                + $"Count {r.Count}; clean {r.Clean}; distinct normalized {r.Diversity}\r\n\r\n{r.Provenance}\r\n\r\nADVISORY / NOT_RUNTIME_PARITY / NOT_SEMANTIC_VERIFIED";
    }
    private void Cancel_Click(object? sender, EventArgs e) { version++; operation?.Cancel(); }
    private void Quality_Closing(object? sender, FormClosingEventArgs e) { version++; if (operation != null) { operation.Cancel(); e.Cancel = true; } }
}
