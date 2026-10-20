using System.Diagnostics;
using System.Text.Json;
using PhantomSemanticStudio.Core;

internal static partial class Program
{
    private static int Pss006Interactive()
    {
        var studio = Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "../../../../../"));
        var assembly = System.Reflection.Assembly.LoadFrom(Path.Combine(studio, "src/PhantomSemanticStudio.WinForms/bin/Release/net10.0-windows/PhantomSemanticStudio.dll"));
        var type = assembly.GetType("PhantomSemanticStudio.WinForms.ChatCorpusForm", true)!;
        var application = System.Reflection.Assembly.Load(assembly.GetReferencedAssemblies().Single(a => a.Name == "System.Windows.Forms")).GetType("System.Windows.Forms.Application")!;
        using var f = new Fixture();
        using var workspace = new WorkspaceStore(Path.Combine(studio, "artifacts/PSS-006/interactive-" + Guid.NewGuid().ToString("N")), f.Module);
        new CorpusStore(workspace).Import(CorpusZip(f.Root, ("synthetic.log", CorpusLines(Enumerable.Range(0, 205).Select(i =>
            $"[01.01.22 00:00:01] SHOUT [PlayerA] Синтетическая публичная реплика {i:D4}").ToArray()))));
        using var disposable = (IDisposable)Activator.CreateInstance(type)!; dynamic form = disposable; form.SetWorkspace(workspace);
        Console.WriteLine("INTERACTIVE_SYNTHETIC_ONLY; realChat/model/sourceCalls=0");
        var run = application.GetMethods().Single(m => m.Name == "Run" && m.GetParameters().Length == 1 && m.GetParameters()[0].ParameterType.Name == "Form");
        run.Invoke(null, [form]); Console.WriteLine("INTERACTIVE_CLOSED; manual/DPI/VS statuses recorded separately"); return 0;
    }
    private static int Pss006Controls()
    {
        var studio = Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "../../../../../"));
        var assembly = System.Reflection.Assembly.LoadFrom(Path.Combine(studio, "src/PhantomSemanticStudio.WinForms/bin/Release/net10.0-windows/PhantomSemanticStudio.dll"));
        var type = assembly.GetType("PhantomSemanticStudio.WinForms.MainForm", true)!;
        var doEvents = System.Reflection.Assembly.Load(assembly.GetReferencedAssemblies().Single(a => a.Name == "System.Windows.Forms"))
            .GetType("System.Windows.Forms.Application")!.GetMethod("DoEvents")!;
        dynamic Control(dynamic ui, string name) => ui.Controls.Find(name, true)[0];
        void Pump(dynamic ui)
        {
            var timer = Stopwatch.StartNew();
            do { doEvents.Invoke(null, null); Thread.Sleep(5); }
            while (!(bool)ui.IsDisposed && (bool)Control(ui, "btnCancel").Enabled && timer.Elapsed < TimeSpan.FromSeconds(20));
            if (!(bool)ui.IsDisposed) True(!Control(ui, "btnCancel").Enabled);
            doEvents.Invoke(null, null);
        }
        using var f = new Fixture();
        var segment = Path.Combine(f.Module, "dist/game/data/phantoms/conversation/humanized/v3/segments/test.xml");
        File.WriteAllText(segment, "<humanizedV3ConversationSegment version=\"3\">" + string.Concat(Enumerable.Range(0, 7500).Select(i =>
            $"<template id=\"large{i}\" act=\"greet.reply\" text=\"Привет, друг номер {i}\"/>")) + "</humanizedV3ConversationSegment>");
        var pack = new PackReader().Load(f.Module);
        var first = CandidateOf(pack, "Привет, товарищ!"); first.Id = "first";
        var second = CandidateOf(pack, "Друг, привет!"); second.Id = "second";
        var third = CandidateOf(pack, "Зимняя дорога."); third.Id = "third";
        var peers = new List<Candidate> { third, first, second };
        foreach (var candidate in new[] { first, second })
        {
            var shortlist = SemanticDuplicateScout.Search(pack, candidate, peers);
            using var fake = new HttpClient(new RecordingHandler(SemanticEnvelope(SemanticBody(shortlist))));
            candidate.SemanticReview = new LmStudioClient(fake).ReviewSemanticAsync(new StudioSettings { HighFiveRoot = f.Module }, "", candidate, shortlist, default).GetAwaiter().GetResult();
        }
        var root = Path.Combine(studio, "artifacts/PSS-006/controls-" + Guid.NewGuid().ToString("N"));
        using (var setup = new WorkspaceStore(root, f.Module))
        { setup.SaveSettings(new StudioSettings { HighFiveRoot = f.Module }); setup.SaveSession(new SessionState { Candidates = peers }); }
        var previous = Environment.GetEnvironmentVariable("PSS_UI_SMOKE_WORKSPACE");
        Environment.SetEnvironmentVariable("PSS_UI_SMOKE_WORKSPACE", root);
        using var form = (IDisposable)Activator.CreateInstance(type)!; dynamic ui = form;
        try
        {
            ui.Show(); Pump(ui); Control(ui, "btnImport").PerformClick(); Pump(ui);
            Control(ui, "tabs").SelectedTab = Control(ui, "tabCandidates");
            Test("PSS-006 A actual saved selection responsive latest identity cancel error close", () =>
            {
                var bytes = File.ReadAllBytes(Path.Combine(root, "session.json"));
                dynamic grid = Control(ui, "gridCandidates");
                var elapsed = Stopwatch.StartNew(); grid.CurrentCell = grid.Rows[1].Cells[0]; doEvents.Invoke(null, null); elapsed.Stop();
                Console.WriteLine($"SYNTHETIC_SELECTION_DISPATCH_MS={elapsed.ElapsedMilliseconds}; SOURCE_TEMPLATES=7500");
                True(elapsed.ElapsedMilliseconds < 150); True(Control(ui, "btnCancel").Enabled);
                grid.CurrentCell = grid.Rows[2].Cells[0]; doEvents.Invoke(null, null);
                grid.CurrentCell = grid.Rows[0].Cells[0]; Pump(ui);
                True(((string)Control(ui, "lblSelected").Text).StartsWith("third"));
                True(!((string)Control(ui, "txtValidation").Text).Contains("Кандидат first"));
                grid.CurrentCell = grid.Rows[1].Cells[0]; doEvents.Invoke(null, null);
                Control(ui, "btnCancel").PerformClick(); Pump(ui);
                True(((string)Control(ui, "txtValidation").Text).Contains("STALE"));
                grid.CurrentCell = grid.Rows[0].Cells[0]; Pump(ui);
                var original = File.ReadAllBytes(segment);
                try
                {
                    File.WriteAllBytes(segment, [0xff, 0x00]);
                    grid.CurrentCell = grid.Rows[1].Cells[0]; Pump(ui);
                    True(((string)Control(ui, "txtValidation").Text).Contains("STALE"));
                }
                finally { File.WriteAllBytes(segment, original); }
                grid.CurrentCell = grid.Rows[0].Cells[0]; Pump(ui);
                grid.CurrentCell = grid.Rows[1].Cells[0]; doEvents.Invoke(null, null);
                ui.Close(); doEvents.Invoke(null, null);
                True(bytes.SequenceEqual(File.ReadAllBytes(Path.Combine(root, "session.json"))));
            });
        }
        finally { ui.Close(); Environment.SetEnvironmentVariable("PSS_UI_SMOKE_WORKSPACE", previous); }
        Test("PSS-006 C real corpus controls import page explicit20 filter cancel session unchanged", () =>
        {
            var corpusType = assembly.GetType("PhantomSemanticStudio.WinForms.ChatCorpusForm", true)!;
            using var workspace = new WorkspaceStore(Path.Combine(root, "corpus"), f.Module);
            workspace.SaveSession(new SessionState()); var bytes = File.ReadAllBytes(Path.Combine(workspace.Root, "session.json"));
            var zip = CorpusZip(f.Root, ("synthetic.log", CorpusLines(Enumerable.Range(0, 205).Select(i =>
                $"[01.01.22 00:00:01] SHOUT [PlayerA] public synthetic {i:D4}").ToArray())));
            using var explorer = (IDisposable)Activator.CreateInstance(corpusType)!; dynamic corpus = explorer;
            corpus.SetWorkspace(workspace); corpus.Show(); Pump(corpus);
            Control(corpus, "txtArchive").Text = zip; Control(corpus, "btnImport").PerformClick(); Pump(corpus);
            dynamic list = Control(corpus, "listRows"); Equal(100, (int)list.Items.Count);
            True(((string)Control(corpus, "lblStats").Text).Contains("205"));
            for (var i = 0; i < 20; i++) list.Items[i].Checked = true;
            list.Items[20].Checked = true; Equal(20, (int)list.CheckedItems.Count);
            True(((string)Control(corpus, "txtSelected").Text).Contains("SOURCE_MATERIAL_ONLY"));
            Control(corpus, "txtSearch").Text = "no-match"; Control(corpus, "btnSearch").AccessibilityObject.DoDefaultAction(); Pump(corpus);
            Equal(0, (int)list.Items.Count); True(((string)Control(corpus, "lblSelected").Text).Contains("20"));
            Control(corpus, "txtSearch").Clear(); Control(corpus, "btnSearch").PerformClick(); Pump(corpus);
            Control(corpus, "btnNext").PerformClick(); Pump(corpus); Equal(100, (int)list.Items.Count);
            True(bytes.SequenceEqual(File.ReadAllBytes(Path.Combine(workspace.Root, "session.json"))));
            foreach (var size in new[] { (object)corpus.MinimumSize, (object)corpus.Size })
            {
                corpus.Size = (dynamic)size; doEvents.Invoke(null, null);
                foreach (var name in new[] { "listRows", "txtSelected", "btnCancel", "btnSearch", "btnNext" })
                { dynamic control = Control(corpus, name); True(control.Parent.ClientRectangle.Contains(control.Bounds)); }
                True(!Control(corpus, "listRows").Bounds.IntersectsWith(Control(corpus, "txtSelected").Bounds));
            }
            var large = LargeCorpusZip(f.Root); Control(corpus, "txtArchive").Text = large;
            Control(corpus, "btnImport").PerformClick(); doEvents.Invoke(null, null); True(Control(corpus, "btnCancel").Enabled);
            Control(corpus, "btnCancel").PerformClick(); Pump(corpus);
            Equal(1, new CorpusStore(workspace).List().Count);
            Control(corpus, "btnImport").PerformClick(); doEvents.Invoke(null, null); corpus.Close(); Pump(corpus); corpus.Close();
        });
        Console.WriteLine($"CONTROL_CONTRACT_RESULT: {passed} PASS; {failed} FAIL; physical UI/DPI/VS NOT_TESTED");
        return failed == 0 ? 0 : 1;
    }
}
