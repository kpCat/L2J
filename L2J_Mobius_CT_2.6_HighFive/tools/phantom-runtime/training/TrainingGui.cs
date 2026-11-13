using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Text;
using System.Threading.Tasks;
using System.Web.Script.Serialization;
using System.Windows.Forms;

namespace PhantomTraining
{
    internal static class Program
    {
        [STAThread]
        private static void Main(string[] args)
        {
            if (args.Length != 2) throw new ArgumentException("Запустите TRAIN_GUI.cmd из собственной TEST-копии.");
            Application.EnableVisualStyles();
            var window = new TrainingForm(); window.Configure(args[0], args[1]); Application.Run(window);
        }
    }
    // This window is a CLI client. The existing broker owns the detached Director.
    public partial class TrainingForm : Form
    {
        private string script;
        private string root;
        private Process command;
        private Process status;
        private Task<string> commandOutput;
        private Task<string> commandError;
        private Task<string> statusOutput;
        private Task<string> statusError;
        private readonly JavaScriptSerializer serializer = new JavaScriptSerializer();
        public TrainingForm() { InitializeComponent(); }
        public void Configure(string scriptPath, string installation)
        {
            script = Path.GetFullPath(scriptPath);
            root = Path.GetFullPath(installation);
            if (!File.Exists(script) || root.IndexOf('"') >= 0 || script.IndexOf('"') >= 0) throw new ArgumentException("Некорректный путь к тренажёру.");
            pathLabel.Text = root;
            startButton.Click += delegate { Send("Start"); };
            pauseButton.Click += delegate { Send("Pause"); };
            stopButton.Click += delegate { Send("Stop"); };
            resumeButton.Click += delegate { Send("Resume"); };
            refreshButton.Click += delegate { PollStatus(); };
            exportButton.Click += delegate { Send("Export"); };
            refreshTimer.Tick += delegate { Tick(); };
            Shown += delegate { PollStatus(); refreshTimer.Start(); };
            FormClosed += delegate
            {
                refreshTimer.Stop();
                // Disposing a Process handle does not terminate CLI, broker or native workers.
                if (command != null) command.Dispose();
                if (status != null) status.Dispose();
            };
        }
        private Process Launch(string action, bool json)
        {
            var info = new ProcessStartInfo(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.System), @"WindowsPowerShell\v1.0\powershell.exe"));
            info.Arguments = "-NoProfile -ExecutionPolicy Bypass -File \"" + script + "\" -Action " + action + " -Root \"" + root + "\" -Autonomous" + (json ? " -Json" : "");
            info.UseShellExecute = false;
            info.CreateNoWindow = true;
            info.RedirectStandardOutput = true;
            info.RedirectStandardError = true;
            info.StandardOutputEncoding = Encoding.UTF8;
            info.StandardErrorEncoding = Encoding.UTF8;
            return Process.Start(info);
        }
        private void Send(string action)
        {
            if (command != null) return;
            try
            {
                command = Launch(action, false);
                commandOutput = command.StandardOutput.ReadToEndAsync();
                commandError = command.StandardError.ReadToEndAsync();
                SetCommandButtons(false);
                messageLabel.Text = "Команда выполняется: " + Caption(action);
            }
            catch (Exception error) { messageLabel.Text = "Не удалось передать команду: " + error.Message; }
        }
        private void PollStatus()
        {
            if (status != null || script == null) return;
            try
            {
                status = Launch("Status", true);
                statusOutput = status.StandardOutput.ReadToEndAsync();
                statusError = status.StandardError.ReadToEndAsync();
            }
            catch (Exception error) { stateLabel.Text = "Состояние недоступно: " + error.Message; }
        }
        private void Tick()
        {
            if (command != null && command.HasExited && commandOutput.IsCompleted && commandError.IsCompleted)
            {
                messageLabel.Text = command.ExitCode == 0 ? "Команда принята. Обновляю состояние." : "Команда завершилась с ошибкой; подробности ниже.";
                logBox.AppendText(commandOutput.Result + Environment.NewLine + commandError.Result + Environment.NewLine);
                command.Dispose(); command = null; SetCommandButtons(true);
            }
            if (status != null && status.HasExited && statusOutput.IsCompleted && statusError.IsCompleted)
            {
                try
                {
                    if (status.ExitCode != 0) throw new InvalidOperationException(statusError.Result.Trim());
                    var facts = serializer.Deserialize<Dictionary<string, object>>(statusOutput.Result.Trim());
                    stateLabel.Text = "Состояние: " + State(FactText(facts, "phase")) + " · Director " + (Convert.ToBoolean(facts["directorLive"]) ? "работает" : "остановлен");
                    var report = new StringBuilder();
                    report.AppendLine("Причина: " + FactText(facts, "reason"));
                    report.AppendLine("Проверено CLI: " + FactText(facts, "sampledUtc"));
                    var director = Object(facts, "director");
                    if (director != null) report.AppendLine("CPU Director, сек: " + FactText(director, "cpuSeconds") + " · RAM собственных процессов, байт: " + FactText(director, "ownedWorkingSetBytes") + " · свободный диск, байт: " + FactText(director, "freeDiskBytes"));
                    var journal = Object(facts, "autonomous");
                    if (journal != null)
                    {
                        report.AppendLine("Начало текущего сеанса: " + FactText(journal, "sessionStartedUtc"));
                        report.AppendLine("Цикл: " + FactText(journal, "cycle") + " · завершено: " + FactText(journal, "completedCycles") + " · fit: " + FactText(journal, "fits"));
                        report.AppendLine("Запуски GameServer: " + FactText(journal, "starts") + " · native, мс: " + FactText(journal, "nativeMillis"));
                        report.AppendLine("Последний checkpoint: " + FactText(journal, "lastCheckpoint"));
                        report.AppendLine("Улучшение TUNING: " + (Convert.ToBoolean(journal["improved"]) ? "наблюдалось; это не native verdict" : "не подтверждено"));
                    }
                    var checkpoint = Object(facts, "checkpoint");
                    if (checkpoint != null)
                    {
                        report.AppendLine("Рабочая модель TEST: " + FactText(checkpoint, "modelVersion") + " · Adam updates: " + FactText(checkpoint, "optimizerUpdates") + " · эпохи: " + FactText(checkpoint, "completedEpochs"));
                        report.AppendLine("Принятая TEST-модель: " + FactText(checkpoint, "acceptedTestVersion") + " · автоматического продвижения нет");
                    }
                    var episodes = Object(facts, "episodes");
                    if (episodes != null) report.AppendLine("Попыток текущего cohort: " + FactText(episodes, "attempted") + " · завершённых: " + FactText(episodes, "completed") + " · проигранных: " + FactText(episodes, "failed") + " · settled: " + FactText(episodes, "settled") + " · paid effects: " + FactText(episodes, "paidEffects"));
                    report.AppendLine("TRAIN / TUNING / AUDIT / EVAL хранятся раздельно. Неизвестные метрики показываются как «нет данных».");
                    detailsBox.Text = report.ToString();
                }
                catch (Exception error) { stateLabel.Text = "Не удалось подтвердить состояние"; messageLabel.Text = error.Message; }
                finally { status.Dispose(); status = null; }
            }
            PollStatus();
        }
        private void SetCommandButtons(bool enabled)
        { startButton.Enabled = pauseButton.Enabled = stopButton.Enabled = resumeButton.Enabled = exportButton.Enabled = enabled; }
        private static Dictionary<string, object> Object(Dictionary<string, object> facts, string key)
        { object value; return facts.TryGetValue(key, out value) ? value as Dictionary<string, object> : null; }
        private static string FactText(Dictionary<string, object> facts, string key)
        { object value; return facts.TryGetValue(key, out value) && value != null ? Convert.ToString(value) : "нет данных"; }
        private static string Caption(string value)
        { switch (value) { case "Start": return "Учиться"; case "Pause": return "Пауза"; case "Stop": return "Стоп"; case "Resume": return "Продолжить"; case "Export": return "Экспорт"; default: return "Состояние"; } }
        private static string State(string value)
        {
            switch (value)
            {
                case "IDLE": return "Ожидание"; case "PREFLIGHT": return "Предварительная проверка";
                case "STARTING": case "NATIVE_BOOT": return "Запуск"; case "BUILDING": return "Сборка";
                case "SQL_PREPARE": return "Подготовка SQL"; case "COLLECTING": case "NATIVE": return "Сбор опыта";
                case "FITTING": return "Обучение"; case "TUNING": return "Проверка TUNING"; case "AUDITING": return "Независимая оценка";
                case "CHECKPOINTING": return "Сохранение checkpoint"; case "PAUSED": return "Пауза"; case "STOPPED": return "Остановлен";
                case "STOPPING": case "DRAINING": return "Безопасная остановка"; case "FAILED": return "Ошибка";
                case "RESOURCE_LIMIT": return "Лимит ресурсов"; case "INSUFFICIENT_EVIDENCE": return "Недостаточно независимых данных"; case "STALE": return "Состояние устарело";
                default: return value;
            }
        }
    }
}
