using System.Diagnostics;
using System.Net;
using System.Reflection;
using System.Text;
using System.Text.Json;
using PhantomSemanticStudio.Core;

internal static partial class Program
{
    private static int Batch081Controls()
    {
        var studio = Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "../../../../../"));
        var assembly = Assembly.LoadFrom(Path.Combine(studio, "src/PhantomSemanticStudio.WinForms/bin/Release/net10.0-windows/PhantomSemanticStudio.dll"));
        var forms = Assembly.Load(assembly.GetReferencedAssemblies().Single(a => a.Name == "System.Windows.Forms"));
        var application = forms.GetType("System.Windows.Forms.Application")!;
        var doEvents = application.GetMethod("DoEvents")!;
        var context = forms.GetType("System.Windows.Forms.WindowsFormsSynchronizationContext")!;
        context.GetProperty("AutoInstall")!.SetValue(null, false);
        SynchronizationContext.SetSynchronizationContext((SynchronizationContext)Activator.CreateInstance(context)!);
        var type = assembly.GetType("PhantomSemanticStudio.WinForms.MainForm", true)!;
        dynamic Control(dynamic ui, string name) => ui.Controls.Find(name, true)[0];
        void PumpUntil(Func<bool> complete)
        {
            var timer = Stopwatch.StartNew();
            while (!complete() && timer.Elapsed < TimeSpan.FromSeconds(20)) { doEvents.Invoke(null, null); Thread.Sleep(5); }
            True(complete()); doEvents.Invoke(null, null);
        }
        SessionState Saved(string root) => JsonSerializer.Deserialize<SessionState>(File.ReadAllBytes(Path.Combine(root, "session.json")))!;
        dynamic Open(string root, string source, LmStudioClient model)
        {
            dynamic ui = Activator.CreateInstance(type)!;
            type.GetField("settings", BindingFlags.Instance | BindingFlags.NonPublic)!.SetValue((object)ui, new StudioSettings { HighFiveRoot = source });
            ui.Show(); PumpUntil(() => !(bool)Control(ui, "btnCancel").Enabled);
            Control(ui, "btnImport").PerformClick(); PumpUntil(() => !(bool)Control(ui, "btnCancel").Enabled);
            type.GetField("lm", BindingFlags.Instance | BindingFlags.NonPublic)!.SetValue((object)ui, model);
            Control(ui, "tabs").SelectedTab = Control(ui, "tabGenerate");
            // The batch group is below the existing single-request constructor in its scrollable page.
            Control(ui, "layoutGenerate").ScrollControlIntoView(Control(ui, "grpBatchGeneration"));
            return ui;
        }
        Test("081 real STA pause checkpoint reopen resume preserves exact drafts and editorial profile", () =>
        {
            using var fixture = new Fixture(); var pack = new PackReader().Load(fixture.Module);
            var root = Path.Combine(studio, "artifacts/081/controls-" + Guid.NewGuid().ToString("N"));
            using (var setup = new WorkspaceStore(root, fixture.Module)) setup.SaveSettings(new StudioSettings { HighFiveRoot = fixture.Module });
            var previous = Environment.GetEnvironmentVariable("PSS_UI_SMOKE_WORKSPACE");
            Environment.SetEnvironmentVariable("PSS_UI_SMOKE_WORKSPACE", root);
            try
            {
                using var first = new Batch081UiHandler(0); using var firstHttp = new HttpClient(first);
                dynamic ui = Open(root, fixture.Module, new LmStudioClient(firstHttp));
                using (var disposable = (IDisposable)ui)
                {
                    Equal(6, (int)Control(ui, "tabs").TabPages.Count);
                    Equal(20m, (decimal)Control(ui, "nudCount").Maximum);
                    Equal(500m, (decimal)Control(ui, "nudBatchTarget").Maximum);
                    foreach (var facet in new[] { "Aggression", "Sarcasm", "Humor", "Reserved", "Swagger", "Timidity", "Romance", "Roughness", "Cunning", "Gossip" })
                    {
                        Equal(0m, (decimal)Control(ui, "nudFacet" + facet).Minimum);
                        Equal(100m, (decimal)Control(ui, "nudFacet" + facet).Maximum);
                        Control(ui, "nudFacet" + facet).Value = 10;
                    }
                    True(Control(ui, "btnGenerate").Bottom < Control(ui, "grpBatchGeneration").Top);
                    Control(ui, "cmbRegister").SelectedValue = "NEUTRAL";
                    Control(ui, "cmbVoice").SelectedValue = "direct";
                    Control(ui, "nudBatchTarget").Value = 45;
                    Control(ui, "nudFacetSarcasm").Value = 75; Control(ui, "nudFacetSwagger").Value = 70;
                    Control(ui, "nudFacetHumor").Value = 40;
                    Control(ui, "btnBatchStart").PerformClick(); PumpUntil(() => first.Calls == 1);
                    True(!Control(ui, "grpRequest").Enabled && Control(ui, "btnBatchPause").Enabled);
                    Control(ui, "tabs").SelectedTab = Control(ui, "tabSettings");
                    Equal("tabGenerate", (string)Control(ui, "tabs").SelectedTab.Name);
                    Control(ui, "btnBatchPause").PerformClick();
                    // An actual UI message is processed while the HTTP task is pending.
                    ui.Text = "STA remains responsive"; doEvents.Invoke(null, null); Equal("STA remains responsive", (string)ui.Text);
                    first.Release(); PumpUntil(() => !(bool)Control(ui, "btnCancel").Enabled);
                    var paused = Saved(root); Equal("PAUSED", paused.BatchJob!.Status); Equal(20, paused.BatchJob.Accepted);
                    Equal(75, paused.BatchJob.Parameters.Facets.Sarcasm); Equal(10, paused.BatchJob.Parameters.Facets.Gossip);
                    Equal(20, paused.Candidates.Count); True(paused.Candidates.All(c => c.Status == "DRAFT" && c.Voice == "direct" && c.ApprovedFingerprint == ""));
                    True(Control(ui, "btnBatchResume").Enabled); ui.Close();
                }
                var checkpointIds = Saved(root).Candidates.Select(c => c.Id).ToArray();
                using var second = new Batch081UiHandler(20); using var secondHttp = new HttpClient(second);
                dynamic reopened = Open(root, fixture.Module, new LmStudioClient(secondHttp));
                using (var disposable = (IDisposable)reopened)
                {
                    Equal(45m, (decimal)Control(reopened, "nudBatchTarget").Value);
                    Equal(75m, (decimal)Control(reopened, "nudFacetSarcasm").Value);
                    Equal(70m, (decimal)Control(reopened, "nudFacetSwagger").Value);
                    Equal("direct", (string)Control(reopened, "cmbVoice").SelectedValue);
                    Control(reopened, "btnBatchResume").PerformClick(); PumpUntil(() => second.Calls == 1);
                    second.Release(); PumpUntil(() => !(bool)Control(reopened, "btnCancel").Enabled);
                    var complete = Saved(root); Equal("COMPLETED", complete.BatchJob!.Status); Equal(45, complete.BatchJob.Accepted);
                    Equal(2, second.Calls); Equal(45, complete.Candidates.Count);
                    True(complete.Candidates.Take(20).Select(c => c.Id).SequenceEqual(checkpointIds));
                    True(complete.Candidates.All(c => c.Status == "DRAFT" && c.ApprovedFingerprint == ""));
                    Control(reopened, "btnToCandidates").PerformClick();
                    Control(reopened, "txtCandidateText").Text += " Сегодня."; Control(reopened, "btnSaveCandidate").PerformClick();
                    PumpUntil(() => !(bool)Control(reopened, "btnCancel").Enabled);
                    Equal(complete.BatchJob.Id, Saved(root).BatchJob!.Id); reopened.Close();
                }
                True(pack.Files.SequenceEqual(new PackReader().Load(fixture.Module).Files));
            }
            finally { Environment.SetEnvironmentVariable("PSS_UI_SMOKE_WORKSPACE", previous); }
        });
        Test("081 real STA close cancels delayed HTTP before disposal and retains committed session", () =>
        {
            using var fixture = new Fixture(); var pack = new PackReader().Load(fixture.Module);
            var root = Path.Combine(studio, "artifacts/081/controls-" + Guid.NewGuid().ToString("N"));
            using (var setup = new WorkspaceStore(root, fixture.Module)) setup.SaveSettings(new StudioSettings { HighFiveRoot = fixture.Module });
            var previous = Environment.GetEnvironmentVariable("PSS_UI_SMOKE_WORKSPACE");
            Environment.SetEnvironmentVariable("PSS_UI_SMOKE_WORKSPACE", root);
            try
            {
                using var handler = new Batch081UiHandler(0); using var http = new HttpClient(handler);
                dynamic ui = Open(root, fixture.Module, new LmStudioClient(http));
                using var disposable = (IDisposable)ui;
                Control(ui, "cmbRegister").SelectedValue = "NEUTRAL";
                Control(ui, "btnBatchStart").PerformClick(); PumpUntil(() => handler.Calls == 1);
                ui.Close(); True(!ui.IsDisposed); PumpUntil(() => !(bool)Control(ui, "btnCancel").Enabled);
                Equal("CANCELLED", Saved(root).BatchJob!.Status); Equal(0, Saved(root).Candidates.Count);
                Equal(1, handler.Calls); ui.Close(); True(ui.IsDisposed);
                True(pack.Files.SequenceEqual(new PackReader().Load(fixture.Module).Files));
            }
            finally { Environment.SetEnvironmentVariable("PSS_UI_SMOKE_WORKSPACE", previous); }
        });
        Console.WriteLine($"RESULT: {passed} PASS; {failed} FAIL");
        if (failed != 0) foreach (var failure in Failures) Console.WriteLine(failure);
        return failed == 0 ? 0 : 1;
    }

    private sealed class Batch081UiHandler(int offset) : HttpMessageHandler
    {
        private readonly TaskCompletionSource<bool> firstResponse = new(TaskCreationOptions.RunContinuationsAsynchronously);
        private int calls;
        public int Calls => Volatile.Read(ref calls);
        public void Release() => firstResponse.TrySetResult(true);
        protected override async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            var call = Interlocked.Increment(ref calls);
            if (call == 1) await firstResponse.Task.WaitAsync(cancellationToken);
            using var payload = JsonDocument.Parse(await request.Content!.ReadAsStringAsync(cancellationToken));
            var count = payload.RootElement.GetProperty("response_format").GetProperty("json_schema").GetProperty("schema")
                .GetProperty("properties").GetProperty("items").GetProperty("maxItems").GetInt32();
            var content = JsonSerializer.Serialize(new { items = Enumerable.Range(offset + (call - 1) * 20, count)
                .Select(i => new { kind = "TEMPLATE", text = "Тихий вечер оставил место разговору номер " + i, reason = "Синтетическая проверка очереди" }) });
            return new(HttpStatusCode.OK) { Content = new StringContent(JsonSerializer.Serialize(new
            { choices = new[] { new { finish_reason = "stop", message = new { content } } } }), Encoding.UTF8, "application/json") };
        }
    }
}
