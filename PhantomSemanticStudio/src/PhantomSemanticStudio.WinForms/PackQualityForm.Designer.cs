#nullable enable
namespace PhantomSemanticStudio.WinForms;

partial class PackQualityForm
{
    private System.Windows.Forms.TableLayoutPanel layoutRoot = null!;
    private System.Windows.Forms.Panel contentRoot = null!;
    private System.ComponentModel.IContainer? components = null;
    protected override void Dispose(bool disposing)
    { if (disposing) components?.Dispose(); base.Dispose(disposing); }

    #region Windows Form Designer generated code
    private void InitializeComponent()
    {
        lblSource = new Label(); lblFilters = new Label(); cmbTopic = new ComboBox(); cmbAct = new ComboBox();
        cmbBand = new ComboBox(); cmbRegister = new ComboBox(); cmbSource = new ComboBox();
        listRows = new ListView(); colKind = new ColumnHeader(); colTopic = new ColumnHeader(); colAct = new ColumnHeader();
        colBand = new ColumnHeader(); colRegister = new ColumnHeader(); colSource = new ColumnHeader();
        colCount = new ColumnHeader(); colClean = new ColumnHeader(); colDiversity = new ColumnHeader(); colWarning = new ColumnHeader();
        txtDetails = new TextBox(); lblStatus = new Label(); btnAnalyze = new Button(); btnCancel = new Button();
        layoutRoot = new System.Windows.Forms.TableLayoutPanel();
        contentRoot = new System.Windows.Forms.Panel();
        layoutRoot.SuspendLayout();
        contentRoot.SuspendLayout();
        SuspendLayout();
        layoutRoot.Name = "layoutRoot";
        layoutRoot.Size = new System.Drawing.Size(980, 730);
        layoutRoot.Dock = System.Windows.Forms.DockStyle.Fill;
        layoutRoot.AutoScroll = true;
        layoutRoot.ColumnCount = 1;
        layoutRoot.RowCount = 1;
        layoutRoot.ColumnStyles.Add(new System.Windows.Forms.ColumnStyle(System.Windows.Forms.SizeType.AutoSize));
        layoutRoot.RowStyles.Add(new System.Windows.Forms.RowStyle(System.Windows.Forms.SizeType.AutoSize));
        layoutRoot.Margin = new System.Windows.Forms.Padding(0);
        contentRoot.Name = "contentRoot";
        contentRoot.Size = new System.Drawing.Size(980, 730);
        contentRoot.MinimumSize = new System.Drawing.Size(980, 730);
        contentRoot.Dock = System.Windows.Forms.DockStyle.Fill;
        contentRoot.Margin = new System.Windows.Forms.Padding(0);
        contentRoot.Padding = new System.Windows.Forms.Padding(0);

        lblSource.Name = "lblSource"; lblSource.Location = new System.Drawing.Point(16, 12); lblSource.Size = new System.Drawing.Size(948, 60);
        lblSource.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right;
        lblSource.Text = "SOURCE / ADVISORY / NOT_RUNTIME_PARITY / NOT_INSTALLED";
        lblFilters.Name = "lblFilters"; lblFilters.Location = new System.Drawing.Point(16, 78); lblFilters.Size = new System.Drawing.Size(948, 26);
        lblFilters.Text = "Фильтры: topic                    act                               band                        register                   source";
        cmbTopic.Name = "cmbTopic"; cmbTopic.Location = new System.Drawing.Point(16, 108); cmbTopic.Size = new System.Drawing.Size(180, 28);
        cmbTopic.DropDownStyle = ComboBoxStyle.DropDownList; cmbTopic.TabIndex = 0;
        cmbAct.Name = "cmbAct"; cmbAct.Location = new System.Drawing.Point(206, 108); cmbAct.Size = new System.Drawing.Size(235, 28);
        cmbAct.DropDownStyle = ComboBoxStyle.DropDownList; cmbAct.TabIndex = 1;
        cmbBand.Name = "cmbBand"; cmbBand.Location = new System.Drawing.Point(451, 108); cmbBand.Size = new System.Drawing.Size(165, 28);
        cmbBand.DropDownStyle = ComboBoxStyle.DropDownList; cmbBand.TabIndex = 2;
        cmbRegister.Name = "cmbRegister"; cmbRegister.Location = new System.Drawing.Point(626, 108); cmbRegister.Size = new System.Drawing.Size(165, 28);
        cmbRegister.DropDownStyle = ComboBoxStyle.DropDownList; cmbRegister.TabIndex = 3;
        cmbSource.Name = "cmbSource"; cmbSource.Location = new System.Drawing.Point(801, 108); cmbSource.Size = new System.Drawing.Size(145, 28);
        cmbSource.DropDownStyle = ComboBoxStyle.DropDownList; cmbSource.TabIndex = 4;
        listRows.Name = "listRows"; listRows.Location = new System.Drawing.Point(16, 150); listRows.Size = new System.Drawing.Size(948, 320);
        listRows.Anchor = AnchorStyles.Top | AnchorStyles.Bottom | AnchorStyles.Left | AnchorStyles.Right;
        listRows.View = View.Details; listRows.FullRowSelect = true; listRows.MultiSelect = false; listRows.HideSelection = false; listRows.TabIndex = 5;
        colKind.Text = "Kind"; colKind.Width = 85; colTopic.Text = "Topic входа"; colTopic.Width = 140;
        colAct.Text = "Act"; colAct.Width = 190; colBand.Text = "Band"; colBand.Width = 100; colRegister.Text = "Register"; colRegister.Width = 100;
        colSource.Text = "Source"; colSource.Width = 70; colCount.Text = "Count"; colCount.Width = 70; colClean.Text = "Clean"; colClean.Width = 70;
        colDiversity.Text = "Разных"; colDiversity.Width = 70; colWarning.Text = "Advisory"; colWarning.Width = 270;
        listRows.Columns.AddRange(new ColumnHeader[] { colKind, colTopic, colAct, colBand, colRegister, colSource, colCount, colClean, colDiversity, colWarning });
        txtDetails.Name = "txtDetails"; txtDetails.Location = new System.Drawing.Point(16, 482); txtDetails.Size = new System.Drawing.Size(948, 150);
        txtDetails.Anchor = AnchorStyles.Bottom | AnchorStyles.Left | AnchorStyles.Right; txtDetails.Multiline = true;
        txtDetails.ReadOnly = true; txtDetails.ScrollBars = ScrollBars.Both; txtDetails.WordWrap = false; txtDetails.TabIndex = 6;
        lblStatus.Name = "lblStatus"; lblStatus.Location = new System.Drawing.Point(16, 640); lblStatus.Size = new System.Drawing.Size(948, 30);
        lblStatus.Anchor = AnchorStyles.Bottom | AnchorStyles.Left | AnchorStyles.Right; lblStatus.Text = "Исходники, approvals и корпус не изменяются.";
        btnAnalyze.Name = "btnAnalyze"; btnAnalyze.Location = new System.Drawing.Point(16, 678); btnAnalyze.Size = new System.Drawing.Size(280, 34);
        btnAnalyze.Anchor = AnchorStyles.Bottom | AnchorStyles.Left; btnAnalyze.Text = "Рассчитать карту и ёмкость"; btnAnalyze.Enabled = false; btnAnalyze.TabIndex = 7;
        btnCancel.Name = "btnCancel"; btnCancel.Location = new System.Drawing.Point(310, 678); btnCancel.Size = new System.Drawing.Size(180, 34);
        btnCancel.Anchor = AnchorStyles.Bottom | AnchorStyles.Left; btnCancel.Text = "Отменить"; btnCancel.Enabled = false; btnCancel.TabIndex = 8;
        contentRoot.Controls.Add(lblSource); contentRoot.Controls.Add(lblFilters); contentRoot.Controls.Add(cmbTopic); contentRoot.Controls.Add(cmbAct); contentRoot.Controls.Add(cmbBand);
        contentRoot.Controls.Add(cmbRegister); contentRoot.Controls.Add(cmbSource); contentRoot.Controls.Add(listRows); contentRoot.Controls.Add(txtDetails); contentRoot.Controls.Add(lblStatus);
        contentRoot.Controls.Add(btnAnalyze); contentRoot.Controls.Add(btnCancel);
        cmbTopic.SelectedIndexChanged += Filter_Changed; cmbAct.SelectedIndexChanged += Filter_Changed; cmbBand.SelectedIndexChanged += Filter_Changed;
        cmbRegister.SelectedIndexChanged += Filter_Changed; cmbSource.SelectedIndexChanged += Filter_Changed;
        listRows.ColumnClick += Sort_Click; listRows.SelectedIndexChanged += Row_Selected;
        btnAnalyze.Click += Analyze_Click; btnCancel.Click += Cancel_Click; FormClosing += Quality_Closing;
        AutoScaleDimensions = new System.Drawing.SizeF(96F, 96F); AutoScaleMode = AutoScaleMode.Dpi;
        ClientSize = new System.Drawing.Size(980, 730); MinimumSize = new System.Drawing.Size(760, 420);
        AutoScroll = false; Name = "PackQualityForm"; Text = "Качество и покрытие Semantic Pack — advisory"; StartPosition = FormStartPosition.CenterParent;
        layoutRoot.Controls.Add(contentRoot, 0, 0);
        Controls.Add(layoutRoot);
        contentRoot.ResumeLayout(false);
        layoutRoot.ResumeLayout(false);
        layoutRoot.PerformLayout();
        ResumeLayout(false); PerformLayout();
    }
    #endregion
    private Label lblSource = null!, lblFilters = null!, lblStatus = null!;
    private ComboBox cmbTopic = null!, cmbAct = null!, cmbBand = null!, cmbRegister = null!, cmbSource = null!;
    private ListView listRows = null!;
    private ColumnHeader colKind = null!, colTopic = null!, colAct = null!, colBand = null!, colRegister = null!, colSource = null!, colCount = null!, colClean = null!, colDiversity = null!, colWarning = null!;
    private TextBox txtDetails = null!;
    private Button btnAnalyze = null!, btnCancel = null!;
}
