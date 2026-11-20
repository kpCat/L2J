using System.Diagnostics;
using System.Drawing;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Runtime.Versioning;
using PhantomSemanticStudio.Core;

internal static partial class Program
{
    [SupportedOSPlatform("windows")]
    private static int OpenAiCampaignControls(string? previewDirectory)
    {
        var studio = Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "../../../../../"));
        var artifact = Path.Combine(studio, "src/PhantomSemanticStudio.WinForms/bin/Release/net10.0-windows/PhantomSemanticStudio.dll");
        var assembly = Assembly.LoadFrom(artifact);
        var forms = Assembly.Load(assembly.GetReferencedAssemblies().Single(reference => reference.Name == "System.Windows.Forms"));
        var application = forms.GetType("System.Windows.Forms.Application", true)!;
        var dpi = application.GetMethod("SetHighDpiMode")!;
        dpi.Invoke(null, [Enum.Parse(dpi.GetParameters()[0].ParameterType, "PerMonitorV2")]);
        application.GetMethod("EnableVisualStyles")!.Invoke(null, null);
        var drawing = Assembly.Load("System.Drawing.Common");
        var fontType = drawing.GetType("System.Drawing.Font", true)!;
        using var font = (IDisposable)Activator.CreateInstance(fontType, ["Segoe UI", 10f])!;
        application.GetMethod("SetDefaultFont")!.Invoke(null, [font]);
        var formType = assembly.GetType("PhantomSemanticStudio.WinForms.OpenAiCampaignForm", true)!;
        var messageLoop = application.GetMethod("DoEvents")!;
        void Pump() => messageLoop.Invoke(null, null);

        foreach (var scale in new[] { 100, 150 })
        {
            Test($"OpenAI displayed WinForms workflow and layout at {scale}% dimensions", () =>
            {
                using var fixture = new Fixture();
                var root = Path.Combine(fixture.Root, "campaign-workspace");
                using var workspace = new WorkspaceStore(root, fixture.Module);
                var vault = new OpenAiKeyVault(workspace, Path.Combine(fixture.Root, "isolated-secrets"));
                using var disposable = (IDisposable)Activator.CreateInstance(formType)!;
                dynamic ui = disposable;
                dynamic Control(string name) => ui.Controls.Find(name, true)[0];
                formType.GetMethod("SetWorkspace", BindingFlags.Instance | BindingFlags.NonPublic)!
                    .Invoke(disposable, [workspace, vault]);
                ui.Show();
                PumpUntil(() => (bool)Control("btnPlan").Enabled);
                True((bool)Control("txtKey").UseSystemPasswordChar && ((string)Control("txtKey").Text).Length == 0);
                True((bool)Control("txtEndpoint").ReadOnly);
                Equal(OpenAiResponsesClient.Endpoint, (string)Control("txtEndpoint").Text);
                True((bool)Control("chkDryRun").Checked && !(bool)Control("chkMature").Checked);
                True(!(bool)Control("btnStart").Enabled);
                var monitorDpi = (int)ui.DeviceDpi;
                var targetDpi = scale * 96 / 100;
                SetOpenAiTestDpi((IntPtr)ui.Handle, (Rectangle)ui.Bounds, monitorDpi, targetDpi);
                Equal(targetDpi, (int)ui.DeviceDpi);
                ui.PerformLayout();

                dynamic tabs = Control("tabsCampaign");
                tabs.SelectedIndex = 1;
                Pump();
                Control("btnPlan").PerformClick();
                PumpUntil(() => (int)Control("gridPlan").Rows.Count == 142 && (bool)Control("btnPlan").Enabled);
                Equal(142, (int)Control("gridPlan").Rows.Count);
                True(!(bool)Control("btnStart").Enabled && !(bool)Control("btnPause").Enabled);
                var history = new CorpusCampaign(new PromptLabStore(workspace)).OpenAiHistory();
                Equal(1, history.Count);
                Equal(100_000, history.Single().Target);
                Equal(0L, new CorpusCampaign(new PromptLabStore(workspace)).OpenAiStatus(history.Single().Id).Totals.Requests);
                ValidateBounds(Control("gridPlan"));
                ValidateBounds(Control("btnPlan"));
                Preview(ui, drawing, scale, "campaign");

                tabs.SelectedIndex = 0;
                Pump();
                ValidateBounds(Control("txtKey"));
                ValidateBounds(Control("cboModel"));
                Preview(ui, drawing, scale, "settings");

                tabs.SelectedIndex = 2;
                Pump();
                ValidateBounds(Control("gridSamples"));
                ValidateBounds(Control("txtDetails"));
                Preview(ui, drawing, scale, "review");
                Console.WriteLine($"FORM requested={scale}% deviceDpi={ui.DeviceDpi} client={ui.ClientSize} monitorDpi={monitorDpi}; native DPI event");
                ui.Close();
                Pump();
                True(!vault.Exists());
            });
        }

        Console.WriteLine($"RESULT: {passed} PASS; {failed} FAIL");
        return failed == 0 ? 0 : 1;

        void PumpUntil(Func<bool> completed)
        {
            var timer = Stopwatch.StartNew();
            do { Pump(); Thread.Sleep(10); }
            while (!completed() && timer.Elapsed < TimeSpan.FromSeconds(12));
            True(completed());
        }

        static void ValidateBounds(dynamic element)
        {
            Rectangle bounds = element.Bounds;
            Rectangle parent = element.Parent.DisplayRectangle;
            if (!(bool)element.Visible || bounds.Width < 40 || bounds.Height < 20 || !parent.IntersectsWith(bounds))
                throw new AssertionFailure($"Unreachable {element.Name} bounds={bounds} parent={parent}");
        }

        void Preview(dynamic ui, Assembly drawingLibrary, int scale, string tab)
        {
            if (previewDirectory == null) return;
            var bitmapType = drawingLibrary.GetType("System.Drawing.Bitmap", true)!;
            var imageFormat = drawingLibrary.GetType("System.Drawing.Imaging.ImageFormat", true)!;
            using var bitmap = (IDisposable)Activator.CreateInstance(bitmapType, [(int)ui.Width, (int)ui.Height])!;
            ui.DrawToBitmap((dynamic)bitmap, new Rectangle(0, 0, (int)ui.Width, (int)ui.Height));
            var path = Path.Combine(previewDirectory, $"openai-{scale}-{tab}.png");
            bitmapType.GetMethod("Save", [typeof(string), imageFormat])!.Invoke(bitmap, [path, imageFormat.GetProperty("Png")!.GetValue(null)]);
        }
    }

    [DllImport("user32.dll", EntryPoint = "SendMessageW")]
    private static extern IntPtr SendOpenAiDpiMessage(IntPtr window, uint message, UIntPtr wParam, IntPtr lParam);

    private static void SetOpenAiTestDpi(IntPtr window, Rectangle bounds, int previousDpi, int requestedDpi)
    {
        if (previousDpi == requestedDpi) return;
        // Exercise the actual PerMonitorV2 window handler without changing any global display settings.
        const uint wmDpiChanged = 0x02e0;
        var suggested = Marshal.AllocHGlobal(sizeof(int) * 4);
        try
        {
            Marshal.WriteInt32(suggested, 0, bounds.Left);
            Marshal.WriteInt32(suggested, 4, bounds.Top);
            Marshal.WriteInt32(suggested, 8, bounds.Left + bounds.Width * requestedDpi / previousDpi);
            Marshal.WriteInt32(suggested, 12, bounds.Top + bounds.Height * requestedDpi / previousDpi);
            _ = SendOpenAiDpiMessage(window, wmDpiChanged, (UIntPtr)(uint)(requestedDpi | requestedDpi << 16), suggested);
        }
        finally { Marshal.FreeHGlobal(suggested); }
    }
}
