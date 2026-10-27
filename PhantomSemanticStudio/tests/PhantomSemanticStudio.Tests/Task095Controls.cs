using System.Reflection;
using System.Diagnostics;
using System.Text;
using PhantomSemanticStudio.Core;

internal static partial class Program
{
    private static int Task095Controls()
    {
        var studio = Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "../../../../../"));
        var assembly = Assembly.LoadFrom(Path.Combine(studio, "src/PhantomSemanticStudio.WinForms/bin/Release/net10.0-windows/PhantomSemanticStudio.dll"));
        var forms = Assembly.Load(assembly.GetReferencedAssemblies().Single(a => a.Name == "System.Windows.Forms"));
        var doEvents = forms.GetType("System.Windows.Forms.Application")!.GetMethod("DoEvents")!;
        var context = forms.GetType("System.Windows.Forms.WindowsFormsSynchronizationContext")!;
        context.GetProperty("AutoInstall")!.SetValue(null, false);
        SynchronizationContext.SetSynchronizationContext((SynchronizationContext)Activator.CreateInstance(context)!);
        dynamic Control(dynamic ui, string name) => ui.Controls.Find(name, true)[0];
        void Pump(dynamic ui)
        {
            var timer = Stopwatch.StartNew();
            do { doEvents.Invoke(null, null); Thread.Sleep(5); }
            while ((bool)ui.Busy && timer.Elapsed < TimeSpan.FromSeconds(30));
            True(!(bool)ui.Busy);
        }
        Test("095 offline form validates before explicit accept and builds external stage", () =>
        {
            var root = Path.Combine(Path.GetTempPath(), "Pss095-ui-" + Guid.NewGuid().ToString("N"));
            var source = Path.GetFullPath(Path.Combine(studio, ".."));
            Directory.CreateDirectory(root);
            var input = Path.Combine(root, "chatgpt.jsonl");
            File.WriteAllText(input, "{\"schema\":\"pw-semantic-batch/1\",\"category\":\"hybrid.trade.absurd_offers\",\"mode\":\"REPLY\",\"act\":\"hybrid.trade.absurd_offers.reply\",\"language\":\"ru\",\"count\":1}\n{\"text\":\"За такой лот ещё рассрочку на три осады попроси.\",\"stance\":\"AMUSED\"}\n", new UTF8Encoding(false));
            using var disposable = (IDisposable)Activator.CreateInstance(assembly.GetType("PhantomSemanticStudio.WinForms.V4ImportForm", true)!)!;
            dynamic ui = disposable;
            // Constructor has no source/workspace I/O; context is supplied explicitly.
            ui.SetContext(Path.Combine(root, "workspace"), source);
            ui.Show(); doEvents.Invoke(null, null);
            True(!Control(ui, "btnAccept").Enabled);
            Control(ui, "txtInput").Text = input;
            Control(ui, "btnValidate").PerformClick(); Pump(ui);
            Console.WriteLine("GUI_VALIDATE " + ((string)Control(ui, "txtReport").Text).Replace("\r\n", " | "));
            True(Control(ui, "btnAccept").Enabled);
            True(((string)Control(ui, "txtReport").Text).Contains("hybrid.trade.absurd_offers", StringComparison.Ordinal));
            Control(ui, "btnAccept").PerformClick(); Pump(ui);
            True(((string)Control(ui, "txtReport").Text).Contains("1", StringComparison.Ordinal));
            Control(ui, "btnBuild").PerformClick(); Pump(ui);
            True(!string.IsNullOrEmpty((string)ui.CurrentStage));
            True(PathSafety.IsWithin((string)ui.CurrentStage, Path.Combine(root, "workspace")));
            var drawing = Assembly.Load("System.Drawing.Common");
            using (var bitmap = (IDisposable)Activator.CreateInstance(drawing.GetType("System.Drawing.Bitmap")!, (int)ui.ClientSize.Width, (int)ui.ClientSize.Height)!)
            {
                ui.DrawToBitmap((dynamic)bitmap, new System.Drawing.Rectangle(0, 0, (int)ui.ClientSize.Width, (int)ui.ClientSize.Height));
                var screenshot = Path.Combine(root, "v4-import.png");
                ((dynamic)bitmap).Save(screenshot, (dynamic)drawing.GetType("System.Drawing.Imaging.ImageFormat")!.GetProperty("Png")!.GetValue(null)!);
                Console.WriteLine("UI_SNAPSHOT " + screenshot);
            }
            Control(ui, "txtInput").Text = input + ".changed";
            True(!Control(ui, "btnAccept").Enabled);
            ui.Close();
        });
        Test("095 offline form requires explicit hash-bound taxonomy review", () =>
        {
            var root = Path.Combine(Path.GetTempPath(), "Pss095-taxonomy-ui-" + Guid.NewGuid().ToString("N"));
            using var disposable = (IDisposable)Activator.CreateInstance(assembly.GetType("PhantomSemanticStudio.WinForms.V4ImportForm", true)!)!;
            dynamic ui = disposable;
            ui.SetContext(root, Path.GetFullPath(Path.Combine(studio, "..")));
            ui.Show(); doEvents.Invoke(null, null);
            Control(ui, "txtParent").Text = "hybrid.trade";
            Control(ui, "txtCategoryKey").Text = "hybrid.trade.owner_reviewed";
            Control(ui, "txtCategoryLabel").Text = "Проверенная владельцем тема";
            True(!Control(ui, "btnApprove").Enabled);
            Control(ui, "btnPropose").PerformClick(); Pump(ui);
            True(((string)Control(ui, "txtReport").Text).Contains("SHA256", StringComparison.Ordinal));
            True(!Control(ui, "btnApprove").Enabled);
            Control(ui, "chkReviewed").Checked = true;
            True(Control(ui, "btnApprove").Enabled);
            Control(ui, "btnApprove").PerformClick(); Pump(ui);
            True(((string)Control(ui, "txtReport").Text).Contains("зарегистрирована", StringComparison.Ordinal));
            var workspace = new V4Workspace(root, Path.GetFullPath(Path.Combine(studio, "..")));
            True(workspace.Categories().Any(c => c.Key == "hybrid.trade.owner_reviewed"));
            ui.Close();
        });
        Console.WriteLine($"RESULT: {passed} PASS; {failed} FAIL");
        foreach (var failure in Failures) Console.WriteLine(failure);
        return failed == 0 ? 0 : 1;
    }
}
