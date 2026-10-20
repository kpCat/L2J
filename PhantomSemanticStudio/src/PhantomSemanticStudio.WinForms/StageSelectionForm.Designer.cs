#nullable enable
namespace PhantomSemanticStudio.WinForms;

partial class StageSelectionForm
{
    private System.Windows.Forms.TableLayoutPanel layoutRoot = null!;
    private System.Windows.Forms.Panel contentRoot = null!;
    private System.ComponentModel.IContainer? components = null;

    protected override void Dispose(bool disposing)
    {
        if (disposing) components?.Dispose();
        base.Dispose(disposing);
    }

    #region Windows Form Designer generated code
    private void InitializeComponent()
    {
        lblInstructions = new System.Windows.Forms.Label();
        lblFilter = new System.Windows.Forms.Label();
        txtFilter = new System.Windows.Forms.TextBox();
        lblAvailable = new System.Windows.Forms.Label();
        listCandidates = new System.Windows.Forms.ListView();
        colId = new System.Windows.Forms.ColumnHeader();
        colKind = new System.Windows.Forms.ColumnHeader();
        colScope = new System.Windows.Forms.ColumnHeader();
        colStyle = new System.Windows.Forms.ColumnHeader();
        colText = new System.Windows.Forms.ColumnHeader();
        colReview = new System.Windows.Forms.ColumnHeader();
        txtDetails = new System.Windows.Forms.TextBox();
        lblSelected = new System.Windows.Forms.Label();
        txtSelected = new System.Windows.Forms.TextBox();
        lblCount = new System.Windows.Forms.Label();
        txtError = new System.Windows.Forms.TextBox();
        lblSafety = new System.Windows.Forms.Label();
        btnCreate = new System.Windows.Forms.Button();
        btnCancel = new System.Windows.Forms.Button();
        layoutRoot = new System.Windows.Forms.TableLayoutPanel();
        contentRoot = new System.Windows.Forms.Panel();
        layoutRoot.SuspendLayout();
        contentRoot.SuspendLayout();
        SuspendLayout();
        layoutRoot.Name = "layoutRoot";
        layoutRoot.Size = new System.Drawing.Size(1120, 780);
        layoutRoot.Dock = System.Windows.Forms.DockStyle.Fill;
        layoutRoot.AutoScroll = true;
        layoutRoot.ColumnCount = 1;
        layoutRoot.RowCount = 1;
        layoutRoot.ColumnStyles.Add(new System.Windows.Forms.ColumnStyle(System.Windows.Forms.SizeType.AutoSize));
        layoutRoot.RowStyles.Add(new System.Windows.Forms.RowStyle(System.Windows.Forms.SizeType.AutoSize));
        layoutRoot.Margin = new System.Windows.Forms.Padding(0);
        contentRoot.Name = "contentRoot";
        contentRoot.Size = new System.Drawing.Size(1120, 780);
        contentRoot.MinimumSize = new System.Drawing.Size(1120, 780);
        contentRoot.Dock = System.Windows.Forms.DockStyle.Fill;
        contentRoot.Margin = new System.Windows.Forms.Padding(0);
        contentRoot.Padding = new System.Windows.Forms.Padding(0);

        // lblInstructions
        lblInstructions.Name = "lblInstructions";
        lblInstructions.Location = new System.Drawing.Point(16, 12);
        lblInstructions.Size = new System.Drawing.Size(1088, 44);
        lblInstructions.Anchor = System.Windows.Forms.AnchorStyles.Top | System.Windows.Forms.AnchorStyles.Left | System.Windows.Forms.AnchorStyles.Right;
        lblInstructions.Text = "Отметьте вручную от 1 до 20 кандидатов для одного XML-предложения. Никто не выбран по умолчанию. Полный список выбранных справа сохраняется при поиске.";
        lblInstructions.TabIndex = 0;
        // lblFilter
        lblFilter.Name = "lblFilter";
        lblFilter.Location = new System.Drawing.Point(16, 62);
        lblFilter.Size = new System.Drawing.Size(650, 24);
        lblFilter.Text = "Поиск по ID, тексту, PATTERN/TEMPLATE, теме или act";
        lblFilter.TabIndex = 1;
        // txtFilter
        txtFilter.Name = "txtFilter";
        txtFilter.Location = new System.Drawing.Point(16, 90);
        txtFilter.Size = new System.Drawing.Size(650, 26);
        txtFilter.Anchor = System.Windows.Forms.AnchorStyles.Top | System.Windows.Forms.AnchorStyles.Left | System.Windows.Forms.AnchorStyles.Right;
        txtFilter.MaxLength = 256;
        txtFilter.TabIndex = 2;
        txtFilter.TextChanged += Filter_TextChanged;
        // lblAvailable
        lblAvailable.Name = "lblAvailable";
        lblAvailable.Location = new System.Drawing.Point(16, 126);
        lblAvailable.Size = new System.Drawing.Size(650, 44);
        lblAvailable.Anchor = System.Windows.Forms.AnchorStyles.Top | System.Windows.Forms.AnchorStyles.Left | System.Windows.Forms.AnchorStyles.Right;
        lblAvailable.Text = "APPROVED: 0; поиск не снимает отметки.";
        lblAvailable.TabIndex = 3;
        // listCandidates
        listCandidates.Name = "listCandidates";
        listCandidates.Location = new System.Drawing.Point(16, 178);
        listCandidates.Size = new System.Drawing.Size(650, 282);
        listCandidates.Anchor = System.Windows.Forms.AnchorStyles.Top | System.Windows.Forms.AnchorStyles.Bottom | System.Windows.Forms.AnchorStyles.Left | System.Windows.Forms.AnchorStyles.Right;
        listCandidates.CheckBoxes = true;
        listCandidates.FullRowSelect = true;
        listCandidates.HideSelection = false;
        listCandidates.MultiSelect = false;
        listCandidates.View = System.Windows.Forms.View.Details;
        listCandidates.UseCompatibleStateImageBehavior = false;
        listCandidates.TabIndex = 4;
        colId.Text = "ID";
        colId.Width = 270;
        colKind.Text = "Вид";
        colKind.Width = 100;
        colScope.Text = "Тема / act";
        colScope.Width = 190;
        colStyle.Text = "Пол / отношения / стиль";
        colStyle.Width = 230;
        colText.Text = "Текст";
        colText.Width = 300;
        colReview.Text = "Одобрение / baseline / редактор";
        colReview.Width = 400;
        listCandidates.Columns.AddRange(new System.Windows.Forms.ColumnHeader[] { colId, colKind, colScope, colStyle, colText, colReview });
        listCandidates.ItemCheck += Candidates_ItemCheck;
        listCandidates.SelectedIndexChanged += Candidates_SelectedIndexChanged;
        // txtDetails
        txtDetails.Name = "txtDetails";
        txtDetails.Location = new System.Drawing.Point(16, 474);
        txtDetails.Size = new System.Drawing.Size(650, 166);
        txtDetails.Anchor = System.Windows.Forms.AnchorStyles.Bottom | System.Windows.Forms.AnchorStyles.Left | System.Windows.Forms.AnchorStyles.Right;
        txtDetails.Multiline = true;
        txtDetails.ReadOnly = true;
        txtDetails.ScrollBars = System.Windows.Forms.ScrollBars.Both;
        txtDetails.MaxLength = 0;
        txtDetails.Text = "Выберите строку для просмотра полного текста и отметки редактора.";
        txtDetails.TabIndex = 5;
        // lblSelected
        lblSelected.Name = "lblSelected";
        lblSelected.Location = new System.Drawing.Point(684, 62);
        lblSelected.Size = new System.Drawing.Size(420, 24);
        lblSelected.Anchor = System.Windows.Forms.AnchorStyles.Top | System.Windows.Forms.AnchorStyles.Right;
        lblSelected.Text = "Все выбранные ID и исходные тексты";
        lblSelected.TabIndex = 6;
        // txtSelected
        txtSelected.Name = "txtSelected";
        txtSelected.Location = new System.Drawing.Point(684, 90);
        txtSelected.Size = new System.Drawing.Size(420, 550);
        txtSelected.Anchor = System.Windows.Forms.AnchorStyles.Top | System.Windows.Forms.AnchorStyles.Bottom | System.Windows.Forms.AnchorStyles.Right;
        txtSelected.Multiline = true;
        txtSelected.ReadOnly = true;
        txtSelected.ScrollBars = System.Windows.Forms.ScrollBars.Both;
        txtSelected.MaxLength = 0;
        txtSelected.TabIndex = 7;
        // lblCount
        lblCount.Name = "lblCount";
        lblCount.Location = new System.Drawing.Point(16, 654);
        lblCount.Size = new System.Drawing.Size(180, 28);
        lblCount.Anchor = System.Windows.Forms.AnchorStyles.Bottom | System.Windows.Forms.AnchorStyles.Left;
        lblCount.Text = "Выбрано: 0 / 20";
        lblCount.TabIndex = 8;
        // txtError
        txtError.Name = "txtError";
        txtError.Location = new System.Drawing.Point(202, 650);
        txtError.Size = new System.Drawing.Size(902, 56);
        txtError.Anchor = System.Windows.Forms.AnchorStyles.Bottom | System.Windows.Forms.AnchorStyles.Left | System.Windows.Forms.AnchorStyles.Right;
        txtError.Multiline = true;
        txtError.ReadOnly = true;
        txtError.ScrollBars = System.Windows.Forms.ScrollBars.Vertical;
        txtError.MaxLength = 0;
        txtError.Text = "Отметьте от 1 до 20 кандидатов вручную.";
        txtError.TabIndex = 9;
        // lblSafety
        lblSafety.Name = "lblSafety";
        lblSafety.Location = new System.Drawing.Point(16, 718);
        lblSafety.Size = new System.Drawing.Size(604, 46);
        lblSafety.Anchor = System.Windows.Forms.AnchorStyles.Bottom | System.Windows.Forms.AnchorStyles.Left | System.Windows.Forms.AnchorStyles.Right;
        lblSafety.Text = "НЕ ДЛЯ УСТАНОВКИ • STAGED_UNVALIDATED / Java NOT_RUN\r\nSEMANTIC_NOT_CHECKED. Источник L2J только для чтения.";
        lblSafety.TabIndex = 10;
        // btnCreate
        btnCreate.Name = "btnCreate";
        btnCreate.Location = new System.Drawing.Point(632, 720);
        btnCreate.Size = new System.Drawing.Size(292, 40);
        btnCreate.Anchor = System.Windows.Forms.AnchorStyles.Bottom | System.Windows.Forms.AnchorStyles.Right;
        btnCreate.Text = "Создать предложение";
        btnCreate.Enabled = false;
        btnCreate.UseVisualStyleBackColor = true;
        btnCreate.TabIndex = 11;
        btnCreate.Click += Create_Click;
        // btnCancel
        btnCancel.Name = "btnCancel";
        btnCancel.Location = new System.Drawing.Point(940, 720);
        btnCancel.Size = new System.Drawing.Size(164, 40);
        btnCancel.Anchor = System.Windows.Forms.AnchorStyles.Bottom | System.Windows.Forms.AnchorStyles.Right;
        btnCancel.Text = "Отмена";
        btnCancel.DialogResult = System.Windows.Forms.DialogResult.Cancel;
        btnCancel.UseVisualStyleBackColor = true;
        btnCancel.TabIndex = 12;
        AcceptButton = btnCreate;
        CancelButton = btnCancel;
        AutoScaleDimensions = new System.Drawing.SizeF(7F, 17F);
        AutoScaleMode = System.Windows.Forms.AutoScaleMode.Font;
        Font = new System.Drawing.Font("Segoe UI", 10F);
        ClientSize = new System.Drawing.Size(1120, 780);
        MinimumSize = new System.Drawing.Size(760, 420);
        StartPosition = System.Windows.Forms.FormStartPosition.CenterParent;
        ShowInTaskbar = false;
        MinimizeBox = false;
        MaximizeBox = false;
        Name = "StageSelectionForm";
        Text = "Выбор партии XML-предложения";
        contentRoot.Controls.Add(lblInstructions);
        contentRoot.Controls.Add(lblFilter);
        contentRoot.Controls.Add(txtFilter);
        contentRoot.Controls.Add(lblAvailable);
        contentRoot.Controls.Add(listCandidates);
        contentRoot.Controls.Add(txtDetails);
        contentRoot.Controls.Add(lblSelected);
        contentRoot.Controls.Add(txtSelected);
        contentRoot.Controls.Add(lblCount);
        contentRoot.Controls.Add(txtError);
        contentRoot.Controls.Add(lblSafety);
        contentRoot.Controls.Add(btnCreate);
        contentRoot.Controls.Add(btnCancel);
        FormClosing += Selection_FormClosing;
        AutoScroll = false;
        layoutRoot.Controls.Add(contentRoot, 0, 0);
        Controls.Add(layoutRoot);
        contentRoot.ResumeLayout(false);
        layoutRoot.ResumeLayout(false);
        layoutRoot.PerformLayout();
        ResumeLayout(false);
        PerformLayout();
    }
    #endregion

    private System.Windows.Forms.Label lblInstructions = null!;
    private System.Windows.Forms.Label lblFilter = null!;
    private System.Windows.Forms.TextBox txtFilter = null!;
    private System.Windows.Forms.Label lblAvailable = null!;
    private System.Windows.Forms.ListView listCandidates = null!;
    private System.Windows.Forms.ColumnHeader colId = null!;
    private System.Windows.Forms.ColumnHeader colKind = null!;
    private System.Windows.Forms.ColumnHeader colScope = null!;
    private System.Windows.Forms.ColumnHeader colStyle = null!;
    private System.Windows.Forms.ColumnHeader colText = null!;
    private System.Windows.Forms.ColumnHeader colReview = null!;
    private System.Windows.Forms.TextBox txtDetails = null!;
    private System.Windows.Forms.Label lblSelected = null!;
    private System.Windows.Forms.TextBox txtSelected = null!;
    private System.Windows.Forms.Label lblCount = null!;
    private System.Windows.Forms.TextBox txtError = null!;
    private System.Windows.Forms.Label lblSafety = null!;
    private System.Windows.Forms.Button btnCreate = null!;
    private System.Windows.Forms.Button btnCancel = null!;
}
