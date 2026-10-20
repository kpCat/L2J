using System.Diagnostics;
using PhantomSemanticStudio.Core;

internal static partial class Program
{
    private static int Pss008Controls()
    {
        var studio = Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "../../../../../"));
        var assembly = System.Reflection.Assembly.LoadFrom(Path.Combine(studio, "src/PhantomSemanticStudio.WinForms/bin/Release/net10.0-windows/PhantomSemanticStudio.dll"));
        var forms = System.Reflection.Assembly.Load(assembly.GetReferencedAssemblies().Single(a => a.Name == "System.Windows.Forms"));
        var application = forms.GetType("System.Windows.Forms.Application")!; var doEvents = application.GetMethod("DoEvents")!;
        var context = forms.GetType("System.Windows.Forms.WindowsFormsSynchronizationContext")!;
        context.GetProperty("AutoInstall")!.SetValue(null, false);
        SynchronizationContext.SetSynchronizationContext((SynchronizationContext)Activator.CreateInstance(context)!);
        dynamic Control(dynamic ui, string name) => ui.Controls.Find(name, true)[0];
        void Pump(dynamic ui)
        {
            var timer = Stopwatch.StartNew();
            do { doEvents.Invoke(null, null); Thread.Sleep(5); }
            while (!(bool)ui.IsDisposed && (bool)Control(ui, "btnCancel").Enabled && timer.Elapsed < TimeSpan.FromSeconds(20));
            if (!(bool)ui.IsDisposed) True(!Control(ui, "btnCancel").Enabled);
        }
        Test("PSS-008 A real STA quality analysis filters cancel and resize without mutation", () =>
        {
            var type = assembly.GetType("PhantomSemanticStudio.WinForms.PackQualityForm"); True(type != null);
            using var f = new Fixture(); var pack = new PackReader().Load(f.Module); var original = System.Text.Json.JsonSerializer.Serialize(pack);
            using var disposable = (IDisposable)Activator.CreateInstance(type!)!; dynamic ui = disposable;
            ui.SetPack(pack); ui.Show(); doEvents.Invoke(null, null);
            True(Control(ui, "txtDetails").ReadOnly); True(((string)Control(ui, "lblSource").Text).Contains("NOT_RUNTIME_PARITY"));
            Control(ui, "btnAnalyze").PerformClick(); Pump(ui); True(ui.Result != null); True(Control(ui, "listRows").Items.Count > 0);
            Control(ui, "cmbSource").SelectedIndex = 1; Control(ui, "cmbSource").SelectedIndex = 0;
            var result = ui.Result; Control(ui, "btnAnalyze").PerformClick(); Control(ui, "btnCancel").PerformClick(); Pump(ui);
            True(ReferenceEquals(result, (object?)ui.Result)); Equal(original, System.Text.Json.JsonSerializer.Serialize(pack));
            ui.Size = ui.MinimumSize; doEvents.Invoke(null, null);
            True(Control(ui, "listRows").Bottom <= Control(ui, "txtDetails").Top);
            True(Control(ui, "txtDetails").Bottom < Control(ui, "btnAnalyze").Top); ui.Close();
        });
        Test("PSS-008 B real STA separate proposal exact pair default NO gates and layout", () =>
        {
            var type = assembly.GetType("PhantomSemanticStudio.WinForms.V3ProposalForm"); True(type != null);
            using var f = new Fixture(); using var store = new WorkspaceStore(Path.Combine(f.Root, "studio"), f.Module);
            var pack = new PackReader().Load(f.Module); var peers = StageCandidates(pack);
            using var disposable = (IDisposable)Activator.CreateInstance(type!)!; dynamic ui = disposable;
            ui.SetContext(store, pack, peers); ui.Show(); Pump(ui);
            True(!Control(ui, "chkSelection").Checked && !Control(ui, "chkEditorial").Checked && !Control(ui, "chkRelease").Checked);
            Equal(-1, (int)Control(ui, "cmbPair").SelectedIndex); True(!Control(ui, "btnStage").Enabled && !Control(ui, "btnRelease").Enabled);
            Control(ui, "cmbPair").SelectedIndex = 0;
            True(((string)Control(ui, "txtPreview").Text).Contains("semantic/humanized/v3/segments/test.xml"));
            Control(ui, "chkSelection").Checked = true; Control(ui, "chkEditorial").Checked = true;
            True(!Control(ui, "btnStage").Enabled); // still no manually selected IDs
            Control(ui, "cmbPair").SelectedIndex = -1; True(!Control(ui, "chkSelection").Checked && !Control(ui, "chkEditorial").Checked);
            ui.Size = ui.MinimumSize; doEvents.Invoke(null, null);
            True(Control(ui, "txtPreview").Bottom < Control(ui, "chkSelection").Top);
            True(Control(ui, "txtProof").Bottom < Control(ui, "chkRelease").Top);
            // Exercise the real modal selection controls, never replace selection with a test-only setter.
            dynamic timer = Activator.CreateInstance(forms.GetType("System.Windows.Forms.Timer")!)!;
            Exception? selectionFailure = null; var ticks = 0; var selected = false;
            EventHandler choose = (_, _) =>
            {
                dynamic open = application.GetProperty("OpenForms")!.GetValue(null)!;
                foreach (dynamic dialog in open)
                {
                    if (((object)dialog).GetType().Name != "StageSelectionForm") continue;
                    try
                    {
                        var list = Control(dialog, "listCandidates"); Equal(2, (int)list.Items.Count);
                        list.Items[0].Checked = true; list.Items[1].Checked = true;
                        True(Control(dialog, "btnCreate").Enabled);
                        Control(dialog, "btnCreate").PerformClick(); selected = true;
                    }
                    catch (Exception ex) { selectionFailure = ex; dialog.Close(); }
                    timer.Stop(); return;
                }
                if (++ticks >= 50) { selectionFailure = new AssertionFailure("Selection dialog did not appear"); timer.Stop(); }
            };
            timer.Interval = 50; timer.Tick += choose; timer.Start();
            try { Control(ui, "btnSelect").PerformClick(); }
            finally { timer.Stop(); ((IDisposable)timer).Dispose(); }
            if (selectionFailure != null) throw selectionFailure; True(selected);
            True(!Control(ui, "chkSelection").Checked && !Control(ui, "chkEditorial").Checked);
            True(((string)Control(ui, "txtPreview").Text).Contains(peers[0].Text));
            Control(ui, "cmbPair").SelectedIndex = 0;
            Control(ui, "chkSelection").Checked = true; Control(ui, "chkEditorial").Checked = true;
            True(Control(ui, "btnStage").Enabled);
            Control(ui, "btnStage").PerformClick(); Control(ui, "chkEditorial").Checked = false; Pump(ui);
            True(ui.StageRoot == null); NoV3Stages(store);
            Control(ui, "chkSelection").Checked = true; Control(ui, "chkEditorial").Checked = true;
            Control(ui, "btnStage").PerformClick(); Pump(ui);
            True(ui.StageRoot != null); Equal(2, V3ProposalContract.ReadStage(store.Root, (string)ui.StageRoot).Candidates.Count);
            True(!Control(ui, "chkSelection").Checked && !Control(ui, "chkEditorial").Checked && !Control(ui, "chkRelease").Checked);
            True(((string)Control(ui, "txtProof").Text).Contains("Java NOT_RUN"));
            True(!Control(ui, "btnRelease").Enabled); True(pack.Files.SequenceEqual(new PackReader().Load(f.Module).Files)); ui.Close();
        });
        Console.WriteLine($"RESULT: {passed} PASS; {failed} FAIL"); return failed == 0 ? 0 : 1;
    }
}
