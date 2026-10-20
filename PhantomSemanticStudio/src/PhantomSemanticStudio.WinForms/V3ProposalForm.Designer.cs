#nullable enable
namespace PhantomSemanticStudio.WinForms;

partial class V3ProposalForm
{
    private System.Windows.Forms.TableLayoutPanel layoutRoot = null!;
    private System.Windows.Forms.Panel contentRoot = null!;
    private System.ComponentModel.IContainer? components = null;
    protected override void Dispose(bool disposing)
    { if (disposing) components?.Dispose(); base.Dispose(disposing); }
    #region Windows Form Designer generated code
    private void InitializeComponent()
    {
        lblSource = new Label(); lblPair = new Label(); cmbPair = new ComboBox(); btnSelect = new Button(); txtPreview = new TextBox();
        chkSelection = new CheckBox(); chkEditorial = new CheckBox(); btnStage = new Button();
        btnProof = new Button(); txtProof = new TextBox(); chkRelease = new CheckBox(); btnRelease = new Button(); btnCancel = new Button(); lblStatus = new Label();
        layoutRoot = new System.Windows.Forms.TableLayoutPanel();
        contentRoot = new System.Windows.Forms.Panel();
        layoutRoot.SuspendLayout();
        contentRoot.SuspendLayout();
        SuspendLayout();
        layoutRoot.Name = "layoutRoot";
        layoutRoot.Size = new System.Drawing.Size(980, 810);
        layoutRoot.Dock = System.Windows.Forms.DockStyle.Fill;
        layoutRoot.AutoScroll = true;
        layoutRoot.ColumnCount = 1;
        layoutRoot.RowCount = 1;
        layoutRoot.ColumnStyles.Add(new System.Windows.Forms.ColumnStyle(System.Windows.Forms.SizeType.AutoSize));
        layoutRoot.RowStyles.Add(new System.Windows.Forms.RowStyle(System.Windows.Forms.SizeType.AutoSize));
        layoutRoot.Margin = new System.Windows.Forms.Padding(0);
        contentRoot.Name = "contentRoot";
        contentRoot.Size = new System.Drawing.Size(980, 810);
        contentRoot.MinimumSize = new System.Drawing.Size(980, 810);
        contentRoot.Dock = System.Windows.Forms.DockStyle.Fill;
        contentRoot.Margin = new System.Windows.Forms.Padding(0);
        contentRoot.Padding = new System.Windows.Forms.Padding(0);

        lblSource.Name = "lblSource"; lblSource.Location = new System.Drawing.Point(16, 12); lblSource.Size = new System.Drawing.Size(948, 54);
        lblSource.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right; lblSource.Text = "SOURCE / STAGED_V3_UNVALIDATED / NOT_INSTALLED / NOT_RUNTIME_PARITY";
        lblPair.Name = "lblPair"; lblPair.Location = new System.Drawing.Point(16, 74); lblPair.Size = new System.Drawing.Size(300, 24);
        lblPair.Text = "Точная существующая пара manifest";
        cmbPair.Name = "cmbPair"; cmbPair.Location = new System.Drawing.Point(324, 70); cmbPair.Size = new System.Drawing.Size(360, 28);
        cmbPair.DropDownStyle = ComboBoxStyle.DropDownList; cmbPair.TabIndex = 0;
        btnSelect.Name = "btnSelect"; btnSelect.Location = new System.Drawing.Point(700, 68); btnSelect.Size = new System.Drawing.Size(264, 34);
        btnSelect.Text = "Выбрать 1–20 APPROVED"; btnSelect.TabIndex = 1; btnSelect.Enabled = false;
        txtPreview.Name = "txtPreview"; txtPreview.Location = new System.Drawing.Point(16, 114); txtPreview.Size = new System.Drawing.Size(948, 198);
        txtPreview.Anchor = AnchorStyles.Top | AnchorStyles.Bottom | AnchorStyles.Left | AnchorStyles.Right;
        txtPreview.Multiline = true; txtPreview.ReadOnly = true; txtPreview.ScrollBars = ScrollBars.Both; txtPreview.WordWrap = false; txtPreview.TabIndex = 2;
        chkSelection.Name = "chkSelection"; chkSelection.Location = new System.Drawing.Point(16, 324); chkSelection.Size = new System.Drawing.Size(948, 30);
        chkSelection.Anchor = AnchorStyles.Bottom | AnchorStyles.Left | AnchorStyles.Right; chkSelection.Checked = false;
        chkSelection.Text = "Подтверждаю ВСЕ exact ID, полные тексты и пути выбранной пары выше."; chkSelection.TabIndex = 3;
        chkEditorial.Name = "chkEditorial"; chkEditorial.Location = new System.Drawing.Point(16, 358); chkEditorial.Size = new System.Drawing.Size(948, 52);
        chkEditorial.Anchor = AnchorStyles.Bottom | AnchorStyles.Left | AnchorStyles.Right; chkEditorial.Checked = false;
        chkEditorial.Text = "Отдельно подтверждаю смысл/истинность, универсальный пол, отсутствие мата, adult, памяти, placeholders\r\nи обещаний выполненных игровых действий. НЕ ДЛЯ УСТАНОВКИ; Java проверяется отдельно."; chkEditorial.TabIndex = 4;
        btnStage.Name = "btnStage"; btnStage.Location = new System.Drawing.Point(16, 422); btnStage.Size = new System.Drawing.Size(350, 34);
        btnStage.Anchor = AnchorStyles.Bottom | AnchorStyles.Left; btnStage.Text = "Создать только изолированное v3 предложение"; btnStage.Enabled = false; btnStage.TabIndex = 5;
        btnProof.Name = "btnProof"; btnProof.Location = new System.Drawing.Point(384, 422); btnProof.Size = new System.Drawing.Size(290, 34);
        btnProof.Anchor = AnchorStyles.Bottom | AnchorStyles.Left; btnProof.Text = "Открыть готовый Java proof (JSON)"; btnProof.Enabled = false; btnProof.TabIndex = 6;
        txtProof.Name = "txtProof"; txtProof.Location = new System.Drawing.Point(16, 468); txtProof.Size = new System.Drawing.Size(948, 174);
        txtProof.Anchor = AnchorStyles.Bottom | AnchorStyles.Left | AnchorStyles.Right;
        txtProof.Multiline = true; txtProof.ReadOnly = true; txtProof.ScrollBars = ScrollBars.Both; txtProof.WordWrap = false; txtProof.TabIndex = 7;
        txtProof.Text = "Java NOT_RUN. GUI не запускает Java/Ant/shell. Offline handoff заблокирован без exact native proof.";
        chkRelease.Name = "chkRelease"; chkRelease.Location = new System.Drawing.Point(16, 650); chkRelease.Size = new System.Drawing.Size(948, 50);
        chkRelease.Anchor = AnchorStyles.Bottom | AnchorStyles.Left | AnchorStyles.Right; chkRelease.Checked = false;
        chkRelease.Text = "Третье отдельное согласие: просмотрены exact файлы/hash/native evidence. Подтверждаю только offline folder\r\nс proposed/backup/checklist. NOT_INSTALLED; Java content PASS не означает готовность к игровому runtime."; chkRelease.TabIndex = 8;
        btnRelease.Name = "btnRelease"; btnRelease.Location = new System.Drawing.Point(16, 708); btnRelease.Size = new System.Drawing.Size(350, 34);
        btnRelease.Anchor = AnchorStyles.Bottom | AnchorStyles.Left; btnRelease.Text = "Подготовить offline handoff с backup"; btnRelease.Enabled = false; btnRelease.TabIndex = 9;
        btnCancel.Name = "btnCancel"; btnCancel.Location = new System.Drawing.Point(384, 708); btnCancel.Size = new System.Drawing.Size(180, 34);
        btnCancel.Anchor = AnchorStyles.Bottom | AnchorStyles.Left; btnCancel.Text = "Отменить"; btnCancel.Enabled = false; btnCancel.TabIndex = 10;
        lblStatus.Name = "lblStatus"; lblStatus.Location = new System.Drawing.Point(16, 754); lblStatus.Size = new System.Drawing.Size(948, 40);
        lblStatus.Anchor = AnchorStyles.Bottom | AnchorStyles.Left | AnchorStyles.Right; lblStatus.Text = "Исходный L2J строго READ_ONLY. Установки нет.";
        contentRoot.Controls.Add(lblSource); contentRoot.Controls.Add(lblPair); contentRoot.Controls.Add(cmbPair); contentRoot.Controls.Add(btnSelect); contentRoot.Controls.Add(txtPreview);
        contentRoot.Controls.Add(chkSelection); contentRoot.Controls.Add(chkEditorial); contentRoot.Controls.Add(btnStage); contentRoot.Controls.Add(btnProof); contentRoot.Controls.Add(txtProof);
        contentRoot.Controls.Add(chkRelease); contentRoot.Controls.Add(btnRelease); contentRoot.Controls.Add(btnCancel); contentRoot.Controls.Add(lblStatus);
        Shown += Proposal_Shown; FormClosing += Proposal_Closing; cmbPair.SelectedIndexChanged += Pair_Changed;
        btnSelect.Click += Select_Click; btnStage.Click += Stage_Click; btnProof.Click += Proof_Click; btnRelease.Click += Release_Click; btnCancel.Click += Cancel_Click;
        chkSelection.CheckedChanged += Consent_Changed; chkEditorial.CheckedChanged += Consent_Changed; chkRelease.CheckedChanged += Consent_Changed;
        AutoScaleDimensions = new System.Drawing.SizeF(96F, 96F); AutoScaleMode = AutoScaleMode.Dpi;
        ClientSize = new System.Drawing.Size(980, 810); MinimumSize = new System.Drawing.Size(760, 420);
        AutoScroll = false; Name = "V3ProposalForm"; Text = "v3 предложение и offline handoff — НЕ УСТАНОВКА"; StartPosition = FormStartPosition.CenterParent;
        layoutRoot.Controls.Add(contentRoot, 0, 0);
        Controls.Add(layoutRoot);
        contentRoot.ResumeLayout(false);
        layoutRoot.ResumeLayout(false);
        layoutRoot.PerformLayout();
        ResumeLayout(false); PerformLayout();
    }
    #endregion
    private Label lblSource = null!, lblPair = null!, lblStatus = null!;
    private ComboBox cmbPair = null!;
    private TextBox txtPreview = null!, txtProof = null!;
    private CheckBox chkSelection = null!, chkEditorial = null!, chkRelease = null!;
    private Button btnSelect = null!, btnStage = null!, btnProof = null!, btnRelease = null!, btnCancel = null!;
}
