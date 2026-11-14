namespace PhantomTraining
{
    public partial class TrainingForm
    {
        private System.ComponentModel.IContainer components;
        private System.Windows.Forms.Label pathLabel;
        private System.Windows.Forms.Label stateLabel;
        private System.Windows.Forms.Label messageLabel;
        private System.Windows.Forms.Button startButton;
        private System.Windows.Forms.Button pauseButton;
        private System.Windows.Forms.Button stopButton;
        private System.Windows.Forms.Button resumeButton;
        private System.Windows.Forms.Button refreshButton;
        private System.Windows.Forms.Button exportButton;
        private System.Windows.Forms.TextBox detailsBox;
        private System.Windows.Forms.TextBox logBox;
        private System.Windows.Forms.Timer refreshTimer;
        protected override void Dispose(bool disposing)
        { if (disposing && components != null) components.Dispose(); base.Dispose(disposing); }
        private void InitializeComponent()
        {
            components = new System.ComponentModel.Container();
            var layout = new System.Windows.Forms.TableLayoutPanel();
            var buttons = new System.Windows.Forms.FlowLayoutPanel();
            pathLabel = new System.Windows.Forms.Label(); stateLabel = new System.Windows.Forms.Label(); messageLabel = new System.Windows.Forms.Label();
            startButton = new System.Windows.Forms.Button(); pauseButton = new System.Windows.Forms.Button(); stopButton = new System.Windows.Forms.Button();
            resumeButton = new System.Windows.Forms.Button(); refreshButton = new System.Windows.Forms.Button(); exportButton = new System.Windows.Forms.Button();
            detailsBox = new System.Windows.Forms.TextBox(); logBox = new System.Windows.Forms.TextBox(); refreshTimer = new System.Windows.Forms.Timer(components);
            SuspendLayout();
            layout.Dock = System.Windows.Forms.DockStyle.Fill; layout.Padding = new System.Windows.Forms.Padding(14); layout.ColumnCount = 1; layout.RowCount = 6;
            layout.RowStyles.Add(new System.Windows.Forms.RowStyle(System.Windows.Forms.SizeType.Absolute, 30));
            layout.RowStyles.Add(new System.Windows.Forms.RowStyle(System.Windows.Forms.SizeType.Absolute, 42));
            layout.RowStyles.Add(new System.Windows.Forms.RowStyle(System.Windows.Forms.SizeType.Absolute, 52));
            layout.RowStyles.Add(new System.Windows.Forms.RowStyle(System.Windows.Forms.SizeType.Percent, 72));
            layout.RowStyles.Add(new System.Windows.Forms.RowStyle(System.Windows.Forms.SizeType.Absolute, 38));
            layout.RowStyles.Add(new System.Windows.Forms.RowStyle(System.Windows.Forms.SizeType.Percent, 28));
            pathLabel.Dock = System.Windows.Forms.DockStyle.Fill; pathLabel.AutoEllipsis = true; pathLabel.Text = "Тренажёр боевого ИИ · собственная TEST-установка";
            stateLabel.Dock = System.Windows.Forms.DockStyle.Fill; stateLabel.Text = "Проверяю состояние…"; stateLabel.Font = new System.Drawing.Font("Segoe UI", 14F, System.Drawing.FontStyle.Bold);
            buttons.Dock = System.Windows.Forms.DockStyle.Fill; buttons.WrapContents = false; buttons.AutoScroll = true;
            startButton.Text = "Учиться"; startButton.Size = new System.Drawing.Size(112, 38);
            pauseButton.Text = "Пауза"; pauseButton.Size = new System.Drawing.Size(100, 38);
            stopButton.Text = "Стоп"; stopButton.Size = new System.Drawing.Size(100, 38);
            resumeButton.Text = "Продолжить"; resumeButton.Size = new System.Drawing.Size(130, 38);
            refreshButton.Text = "Состояние"; refreshButton.Size = new System.Drawing.Size(118, 38);
            exportButton.Text = "Экспорт"; exportButton.Size = new System.Drawing.Size(100, 38);
            buttons.Controls.Add(startButton); buttons.Controls.Add(pauseButton); buttons.Controls.Add(stopButton); buttons.Controls.Add(resumeButton); buttons.Controls.Add(refreshButton); buttons.Controls.Add(exportButton);
            detailsBox.Dock = System.Windows.Forms.DockStyle.Fill; detailsBox.Multiline = true; detailsBox.ReadOnly = true; detailsBox.ScrollBars = System.Windows.Forms.ScrollBars.Vertical; detailsBox.BackColor = System.Drawing.SystemColors.Window;
            detailsBox.Text = "Интерфейс можно закрыть: обучение продолжает работать в отдельном процессе. Для остановки нажмите «Стоп».";
            messageLabel.Dock = System.Windows.Forms.DockStyle.Fill; messageLabel.AutoEllipsis = true; messageLabel.Text = "Изменения модели сохраняются в TEST. MAIN/PLAY не изменяются.";
            logBox.Dock = System.Windows.Forms.DockStyle.Fill; logBox.Multiline = true; logBox.ReadOnly = true; logBox.ScrollBars = System.Windows.Forms.ScrollBars.Both; logBox.WordWrap = false;
            layout.Controls.Add(pathLabel, 0, 0); layout.Controls.Add(stateLabel, 0, 1); layout.Controls.Add(buttons, 0, 2); layout.Controls.Add(detailsBox, 0, 3); layout.Controls.Add(messageLabel, 0, 4); layout.Controls.Add(logBox, 0, 5);
            refreshTimer.Interval = 2500;
            AutoScaleDimensions = new System.Drawing.SizeF(96F, 96F); AutoScaleMode = System.Windows.Forms.AutoScaleMode.Dpi;
            Font = new System.Drawing.Font("Segoe UI", 10F); ClientSize = new System.Drawing.Size(930, 670); MinimumSize = new System.Drawing.Size(840, 560);
            Text = "Phantom World — автономное обучение TEST"; StartPosition = System.Windows.Forms.FormStartPosition.CenterScreen; Controls.Add(layout);
            ResumeLayout(false);
        }
    }
}
