using System.Globalization;
using PhantomSemanticStudio.Core;

namespace PhantomSemanticStudio.WinForms;

public partial class ChatCorpusForm : Form
{
    private CorpusStore? corpusStore;
    private CancellationTokenSource? operation;
    private bool binding;
    private int page;
    private long total;
    private string activeCorpus = "";
    private readonly Dictionary<string, CorpusRecord> selected = new(StringComparer.Ordinal);
    private bool transferMode;
    private long selectionVersion;
    public IReadOnlyList<SanitizedCorpusExcerpt> SelectedExcerpts { get; private set; } = Array.AsReadOnly(Array.Empty<SanitizedCorpusExcerpt>());
    public ChatCorpusForm()
    {
        InitializeComponent();
    }
    public void SetWorkspace(WorkspaceStore workspace) => corpusStore = new CorpusStore(workspace);
    public void SetTransferMode(bool enabled) { transferMode = enabled; btnTransfer.Enabled = enabled && selected.Count > 0; }
    private CorpusStore Store => corpusStore ?? throw new InvalidOperationException("Workspace корпуса не открыт.");
    private CorpusMetadata? CurrentCorpus => cmbCorpus.SelectedItem as CorpusMetadata;
    private async void Corpus_Shown(object? sender, EventArgs e) => await RunAsync(RefreshCorporaAsync);
    private async Task RefreshCorporaAsync(CancellationToken token)
    {
        var id = CurrentCorpus?.Id;
        var list = await Task.Run(() => Store.List(token), token); token.ThrowIfCancellationRequested();
        binding = true;
        try { cmbCorpus.DataSource = list.ToList(); cmbCorpus.SelectedItem = list.FirstOrDefault(c => c.Id == id) ?? list.LastOrDefault(); }
        finally { binding = false; }
        SwitchCorpus(); await QueryAsync(token);
    }
    private void SwitchCorpus()
    {
        var id = CurrentCorpus?.Id ?? "";
        if (id == activeCorpus) return;
        activeCorpus = id; page = 0; selected.Clear(); UpdateSelection(); listRows.Items.Clear(); txtDetails.Clear();
    }
    private async void Corpus_Changed(object? sender, EventArgs e)
    {
        if (binding || operation != null) return;
        SwitchCorpus(); await RunAsync(QueryAsync);
    }
    private void Browse_Click(object? sender, EventArgs e)
    {
        using var dialog = new OpenFileDialog { Filter = "Архив чатов (*.zip)|*.zip", CheckFileExists = true, Multiselect = false, Title = "Приватный локальный архив чатов" };
        if (dialog.ShowDialog(this) == DialogResult.OK) txtArchive.Text = dialog.FileName;
    }
    private async void Import_Click(object? sender, EventArgs e)
    {
        var path = txtArchive.Text;
        await RunAsync(async token =>
        {
            var progress = new Progress<CorpusProgress>(p =>
            {
                if (!IsDisposed && operation != null) lblStatus.Text = $"Импорт: {p.Lines:N0} строк, {p.Bytes:N0} bytes. Приватные каналы не сохраняются.";
            });
            var result = await Task.Run(() => Store.Import(path, token, progress), token);
            token.ThrowIfCancellationRequested(); await RefreshCorporaAsync(token);
            binding = true; try { cmbCorpus.SelectedItem = ((List<CorpusMetadata>)cmbCorpus.DataSource!).Single(c => c.Id == result.Id); }
            finally { binding = false; }
            SwitchCorpus(); await QueryAsync(token);
            lblStatus.Text = "Импорт завершён локально. SOURCE_MATERIAL_ONLY; обучение и генерация не запускались.";
        });
    }
    private async void Refresh_Click(object? sender, EventArgs e) => await RunAsync(RefreshCorporaAsync);
    private CorpusQuery ReadQuery() => new()
    {
        Search = txtSearch.Text.Trim(), Channel = cmbChannel.SelectedIndex <= 0 ? "" : cmbChannel.Text,
        Language = cmbLanguage.SelectedIndex <= 0 ? "" : cmbLanguage.Text,
        FromDate = dateFrom.Checked ? dateFrom.Value.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture) : "",
        ToDate = dateTo.Checked ? dateTo.Value.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture) : "",
        MinLength = (int)nudMinLength.Value, MaxLength = (int)nudMaxLength.Value,
        Duplicates = cmbDuplicates.SelectedIndex switch { 1 => "ALL", 2 => "ONLY", _ => "EXCLUDE" },
        Noise = cmbNoise.SelectedIndex switch { 1 => "ALL", 2 => "ONLY", _ => "EXCLUDE" }, Page = page
    };
    private async Task QueryAsync(CancellationToken token)
    {
        var corpus = CurrentCorpus; if (corpus == null) { lblStats.Text = "Корпус ещё не импортирован."; return; }
        var query = ReadQuery(); var results = await Task.Run(() => Store.Query(corpus.Id, query, token), token);
        token.ThrowIfCancellationRequested(); total = results.Total;
        binding = true; listRows.BeginUpdate();
        try
        {
            listRows.Items.Clear();
            foreach (var row in results.Rows)
            {
                var item = new ListViewItem(row.Timestamp) { Tag = row, Checked = selected.ContainsKey(row.Id) };
                item.SubItems.Add(row.Channel); item.SubItems.Add(row.Language); item.SubItems.Add(row.Preview);
                listRows.Items.Add(item);
            }
        }
        finally { listRows.EndUpdate(); binding = false; }
        lblStats.Text = $"Строк: {corpus.Lines:N0}; public: {corpus.Public:N0}; private SKIPPED: {corpus.PrivateSkipped:N0}; ошибки: {corpus.MalformedSkipped:N0}.\r\n"
            + $"Filtered: {corpus.Filtered:N0}; дубли: {corpus.Duplicates:N0}; мусор: {corpus.Noise:N0}. Найдено: {total:N0}; страница {page + 1}; показано {results.Rows.Count}/100.";
    }
    private async void Search_Click(object? sender, EventArgs e) { if (operation != null) return; page = 0; await RunAsync(QueryAsync); }
    private async void Previous_Click(object? sender, EventArgs e) { if (operation != null || page == 0) return; page--; await RunAsync(QueryAsync); }
    private async void Next_Click(object? sender, EventArgs e) { if (operation != null || (page + 1L) * 100 >= total) return; page++; await RunAsync(QueryAsync); }
    private void Row_Checked(object? sender, ItemCheckEventArgs e)
    {
        if (binding || listRows.Items[e.Index].Tag is not CorpusRecord row) return;
        operation?.Cancel();
        if (e.NewValue == CheckState.Checked)
        {
            if (selected.Count == 20) { e.NewValue = CheckState.Unchecked; lblStatus.Text = "Предел 20 фрагментов. Снимите отметку вручную перед новым выбором."; return; }
            selected[row.Id] = row;
        }
        else selected.Remove(row.Id);
        UpdateSelection();
    }
    private void UpdateSelection()
    {
        selectionVersion++;
        chkTransferReviewed.Checked = false;
        btnTransfer.Enabled = transferMode && selected.Count > 0 && operation == null;
        lblSelected.Text = $"Выбрано явно: {selected.Count}/20 • SOURCE_MATERIAL_ONLY";
        txtSelected.Text = string.Join("\r\n\r\n", selected.Values.Select(r => $"SOURCE_MATERIAL_ONLY • {activeCorpus}/{r.Id}\r\n{r.Timestamp} ({"timezone unspecified"}) • {r.Channel}\r\n{r.Preview}"));
    }
    private void Row_Selected(object? sender, EventArgs e)
    {
        if (binding || listRows.SelectedItems.Count == 0 || listRows.SelectedItems[0].Tag is not CorpusRecord row) return;
        txtDetails.Text = $"SOURCE_MATERIAL_ONLY • entry {row.Entry}, line {row.Line} • {row.Language}: {row.LanguageReason}\r\n"
            + $"Дубликат: {row.Duplicate}; мусор: {row.Noise}; возможная персональная информация: {row.PossiblePii}. Обезличивание приблизительное.\r\n{row.Preview}";
    }
    private void ClearSelection_Click(object? sender, EventArgs e)
    {
        operation?.Cancel();
        selected.Clear(); binding = true; try { foreach (ListViewItem item in listRows.Items) item.Checked = false; }
        finally { binding = false; } UpdateSelection();
    }
    private void Cancel_Click(object? sender, EventArgs e) => operation?.Cancel();
    private async void Transfer_Click(object? sender, EventArgs e)
    {
        if (!transferMode || !chkTransferReviewed.Checked || CurrentCorpus == null || selected.Count is < 1 or > 20)
        { lblStatus.Text = "Проверьте полный sanitized preview справа и явно разрешите перенос 1–20 public фрагментов. LM не вызывается."; return; }
        var corpus = CurrentCorpus; var rows = selected.Values.ToArray(); var version = selectionVersion;
        IReadOnlyList<SanitizedCorpusExcerpt>? transfer = null;
        await RunAsync(async token =>
        {
            var fresh = await Task.Run(() => Store.List(token).Single(c => c.Id == corpus.Id), token);
            token.ThrowIfCancellationRequested();
            if (version != selectionVersion || !chkTransferReviewed.Checked) throw new OperationCanceledException(token);
            if (fresh.DatasetHash != corpus.DatasetHash) throw new InvalidDataException("STALE: корпус изменён.");
            transfer = CorpusDialogueBridge.Detach(fresh, rows, chkTransferReviewed.Checked);
        });
        if (transfer == null || version != selectionVersion || !chkTransferReviewed.Checked) return;
        SelectedExcerpts = transfer; DialogResult = DialogResult.OK; Close();
    }
    private async Task RunAsync(Func<CancellationToken, Task> action)
    {
        if (operation != null) return;
        using var cancel = new CancellationTokenSource(); operation = cancel;
        btnCancel.Enabled = true; btnImport.Enabled = false; btnBrowse.Enabled = false; cmbCorpus.Enabled = false;
        btnSearch.Enabled = false; btnPrevious.Enabled = false; btnNext.Enabled = false; btnRefresh.Enabled = false; listRows.Enabled = false;
        btnTransfer.Enabled = false; chkTransferReviewed.Enabled = false;
        lblStatus.Text = "Локальная операция… отмена доступна.";
        try { await action(cancel.Token); }
        catch (OperationCanceledException) { lblStatus.Text = "Отменено. Прежний committed корпус сохранён; partial не опубликован."; }
        catch (Exception ex) when (ex is CorpusException or IOException or InvalidDataException or InvalidOperationException or UnauthorizedAccessException)
        {
            lblStatus.Text = ex is CorpusException corpus ? corpus.Code switch
            {
                "BLOCKED_RESOURCE" => "BLOCKED_RESOURCE: разделите логи. Пределы: 500 entries, 64 MiB на лог, 256 MiB суммарно, 3 млн строк, сжатие до 100:1.",
                "BLOCKED_LINE" => "BLOCKED_LINE: строка превышает 16 KiB. Подготовьте отдельный корректный ZIP; исходный архив сохранён.",
                "UNSAFE_ARCHIVE" => "UNSAFE_ARCHIVE: нужны плоские .log без шифрования, ссылок, вложенных путей, ZIP64 и разделения по дискам.",
                "CORRUPT_ARCHIVE" => "CORRUPT_ARCHIVE: структура или CRC архива повреждены. Получите корректную копию; исходный ZIP сохранён.",
                "CORRUPT_INDEX" => "CORRUPT_INDEX: индекс повреждён или несовместим. Импортируйте исходный ZIP как новый корпус; прежний не перезаписывается.",
                "INVALID_QUERY" => "INVALID_QUERY: проверьте диапазон дат, длины и страницу.",
                _ => "BLOCKED: импорт не завершён. Проверьте UTF-8 и доступ к файлу. Исходный ZIP и прежние корпуса сохранены."
            } : "BLOCKED: локальная операция не завершена. Проверьте доступ к файлу; исходный ZIP и прежние корпуса сохранены.";
            selected.Clear(); UpdateSelection(); listRows.Items.Clear(); txtDetails.Clear();
        }
        finally
        {
            operation = null; btnCancel.Enabled = false; btnImport.Enabled = true; btnBrowse.Enabled = true; cmbCorpus.Enabled = true;
            btnSearch.Enabled = true; btnPrevious.Enabled = page > 0; btnNext.Enabled = (page + 1L) * 100 < total; btnRefresh.Enabled = true; listRows.Enabled = true;
            btnTransfer.Enabled = transferMode && selected.Count > 0; chkTransferReviewed.Enabled = true;
        }
    }
    private void Corpus_Closing(object? sender, FormClosingEventArgs e)
    {
        if (operation != null) { operation.Cancel(); e.Cancel = true; lblStatus.Text = "Отмена запрошена; закройте после завершения операции."; }
        else if (DialogResult != DialogResult.OK) SelectedExcerpts = Array.AsReadOnly(Array.Empty<SanitizedCorpusExcerpt>());
    }
}
