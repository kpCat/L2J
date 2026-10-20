using System.Diagnostics;
using System.Drawing;
using System.Reflection;
using PhantomSemanticStudio.Core;

internal static partial class Program
{
    // Public live-control geometry; private workspaces and all mutations stay in ignored fixtures.
    private static int Pss009Layout(string[] args)
    {
        var studio = Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "../../../../../"));
        var assembly = Assembly.LoadFrom(Path.Combine(studio, "src/PhantomSemanticStudio.WinForms/bin/Release/net10.0-windows/PhantomSemanticStudio.dll"));
        var forms = Assembly.Load(assembly.GetReferencedAssemblies().Single(a => a.Name == "System.Windows.Forms"));
        var application = forms.GetType("System.Windows.Forms.Application")!;
        var exceptionMode = application.GetMethods().Single(m => m.Name == "SetUnhandledExceptionMode" && m.GetParameters().Length == 1);
        exceptionMode.Invoke(null, [Enum.Parse(exceptionMode.GetParameters()[0].ParameterType, "ThrowException")]);
        var dpiMethod = application.GetMethod("SetHighDpiMode")!;
        dpiMethod.Invoke(null, [Enum.Parse(dpiMethod.GetParameters()[0].ParameterType, "PerMonitorV2")]);
        application.GetMethod("EnableVisualStyles")!.Invoke(null, null);
        var screen = forms.GetType("System.Windows.Forms.Screen")!.GetProperty("PrimaryScreen")!.GetValue(null)!;
        Console.WriteLine($"SCREEN bounds={screen.GetType().GetProperty("Bounds")!.GetValue(screen)} workArea={screen.GetType().GetProperty("WorkingArea")!.GetValue(screen)}; physical visual observation NOT_TESTED");
        var fontType = Assembly.Load("System.Drawing.Common").GetType("System.Drawing.Font")!;
        using var defaultFont = (IDisposable)Activator.CreateInstance(fontType, new object[] { "Segoe UI", 10f })!;
        application.GetMethod("SetDefaultFont")!.Invoke(null, [defaultFont]);
        var context = forms.GetType("System.Windows.Forms.WindowsFormsSynchronizationContext")!;
        context.GetProperty("AutoInstall")!.SetValue(null, false);
        SynchronizationContext.SetSynchronizationContext((SynchronizationContext)Activator.CreateInstance(context)!);
        var doEvents = application.GetMethod("DoEvents")!;
        void Pump() { doEvents.Invoke(null, null); }
        dynamic Find(dynamic ui, string name) => ui.Controls.Find(name, true)[0];
        void ResetScroll(dynamic parent)
        {
            if (((object)parent).GetType().GetProperty("AutoScroll")?.GetValue(parent) is true) parent.AutoScrollPosition = Point.Empty;
            foreach (dynamic child in parent.Controls) ResetScroll(child);
        }
        var root = Path.Combine(studio, "artifacts/PSS-009/run-" + Guid.NewGuid().ToString("N"));
        var source = Path.Combine(root, "source");
        Directory.CreateDirectory(source);
        // Synthetic source boundary only; not a Git repository or a Git command.
        File.WriteAllText(Path.Combine(source, ".git"), "synthetic source boundary");
        var workspace = Path.Combine(root, "workspace");
        using (var setup = new WorkspaceStore(workspace, source))
            setup.SaveSettings(new StudioSettings { HighFiveRoot = source, Endpoint = "http://127.0.0.1:1/v1" });
        var previous = Environment.GetEnvironmentVariable("PSS_UI_SMOKE_WORKSPACE");
        Environment.SetEnvironmentVariable("PSS_UI_SMOKE_WORKSPACE", workspace);
        try
        {
            Test("PSS-009 MainForm first and repeated tab selection actual parent bounds", () =>
            {
                using var disposable = (IDisposable)Activator.CreateInstance(assembly.GetType("PhantomSemanticStudio.WinForms.MainForm", true)!)!;
                dynamic ui = disposable;
                dynamic tabs = Find(ui, "tabs");
                void Dump(string transition)
                {
                    Console.WriteLine($"TRANSITION {transition} formClient={ui.ClientSize} dpi={ui.DeviceDpi} fontHeight={ui.Font.Height} display={tabs.DisplayRectangle}");
                    foreach (dynamic page in tabs.TabPages)
                        Console.WriteLine($"PAGE {page.Name} client={page.ClientRectangle} bounds={page.Bounds}");
                    foreach (var name in new[] { "grpRequest", "btnGenerate", "btnPreview", "btnPackQuality", "gridLibrary", "btnSemanticReview", "btnV3Proposal", "btnChatCorpus" })
                    {
                        dynamic c = Find(ui, name);
                        Console.WriteLine($"CONTROL {name} bounds={c.Bounds} parent={c.Parent.ClientRectangle}");
                    }
                }
                Dump("constructed");
                ui.Show();
                var wait = Stopwatch.StartNew();
                do { Pump(); Thread.Sleep(5); }
                while ((bool)Find(ui, "btnCancel").Enabled && wait.Elapsed < TimeSpan.FromSeconds(15));
                True(!ui.IsDisposed && !Find(ui, "btnCancel").Enabled);
                Dump("shown");
                var failures = new List<string>();
                void Check(dynamic parent)
                {
                    foreach (dynamic c in parent.Controls)
                    {
                        if (!(bool)c.Visible) continue;
                        Rectangle bounds = c.Bounds, client = parent.ClientRectangle;
                        if (!client.Contains(bounds))
                            failures.Add($"{c.Name} bounds={bounds} parent={parent.Name}:{client}");
                        Check(c);
                    }
                }
                for (var pass = 0; pass < 2; pass++)
                    for (var step = 0; step < 6; step++)
                    {
                        tabs.SelectedIndex = pass == 0 ? step : 5 - step;
                        ui.PerformLayout(); Pump();
                        dynamic page = tabs.SelectedTab;
                        Console.WriteLine($"SELECT pass={pass + 1} page={page.Name} client={page.ClientRectangle}");
                        Check(page);
                    }
                ui.Close();
                foreach (var failure in failures.Distinct()) Console.WriteLine("GEOMETRY_FAIL " + failure);
                if (failures.Count > 0) throw new AssertionFailure($"Unreachable live controls: {failures.Distinct().Count()}");
            });

            if (!args.Contains("--baseline"))
            {
            // Operator scope: only default window and maximized Full HD at unchanged100% DPI/font.
            Test("PSS-009 main default maximize restore and responsive geometry", () =>
            {
                using var disposable = (IDisposable)Activator.CreateInstance(assembly.GetType("PhantomSemanticStudio.WinForms.MainForm", true)!)!;
                dynamic ui = disposable; ui.Show();
                var wait = Stopwatch.StartNew();
                do { Pump(); Thread.Sleep(5); } while ((bool)Find(ui, "btnCancel").Enabled && wait.Elapsed < TimeSpan.FromSeconds(15));
                True(!ui.IsDisposed && !Find(ui, "btnCancel").Enabled);
                Find(ui, "txtInstruction").Text = string.Join(Environment.NewLine, Enumerable.Repeat("Длинная синтетическая строка для проверки редактора без личных данных.", 30));
                var failures = new List<string>();
                var stateType = ((object)ui.WindowState).GetType();
                foreach (var state in new[] { "Normal", "Maximized", "Normal" })
                {
                    ui.WindowState = (dynamic)Enum.Parse(stateType,state); ui.PerformLayout(); Pump();
                    CheckAllTabs(ui, failures, "state=" + state);
                    if (state == "Maximized")
                    {
                        True((int)Find(ui,"gridLibrary").Width > 1210 && (int)Find(ui,"gridLibrary").Height > 350);
                        True((int)Find(ui,"grpRequest").Width > 764 && (int)Find(ui,"txtConversation").Width > 784);
                        Console.WriteLine($"GROW main grid={Find(ui,"gridLibrary").Size} request={Find(ui,"grpRequest").Size} conversation={Find(ui,"txtConversation").Size}");
                    }
                }
                ui.Close();
                Finish(failures);
            });
            foreach (var formName in new[] { "StageSelectionForm", "DialogueLabForm", "ChatCorpusForm", "PackQualityForm", "V3ProposalForm" })
                Test("PSS-009 " + formName + " all controls default maximize restore geometry", () =>
                {
                    using var store = new WorkspaceStore(Path.Combine(root, formName), source);
                    using var disposable = (IDisposable)Activator.CreateInstance(assembly.GetType("PhantomSemanticStudio.WinForms." + formName, true)!)!;
                    dynamic ui = disposable;
                    if (formName == "ChatCorpusForm") ui.SetWorkspace(store);
                    if (formName == "StageSelectionForm") ui.SetCandidates(SelectionPeers(1), "synthetic-selection-baseline");
                    ui.Show(); Pump();
                    var wait = Stopwatch.StartNew();
                    do { Pump(); Thread.Sleep(5); } while ((bool)Find(ui, "btnCancel").Enabled && formName == "ChatCorpusForm" && wait.Elapsed < TimeSpan.FromSeconds(15));
                    var failures = new List<string>();
                    CheckAllTabs(ui, failures, "first");
                    var stateType = ((object)ui.WindowState).GetType();
                    foreach (var state in new[] { "Maximized", "Normal" })
                    {
                        ui.WindowState = (dynamic)Enum.Parse(stateType,state); ui.PerformLayout(); Pump();
                        CheckAllTabs(ui, failures, "state=" + state);
                        if (state == "Maximized")
                        {
                            var target = formName switch { "StageSelectionForm" => "listCandidates", "DialogueLabForm" => "tabsLab", "ChatCorpusForm" => "listRows", "PackQualityForm" => "listRows", _ => "txtPreview" };
                            var design = formName switch { "StageSelectionForm" => new Size(650,282), "DialogueLabForm" => new Size(1256,690), "ChatCorpusForm" => new Size(800,318), "PackQualityForm" => new Size(948,320), _ => new Size(948,198) };
                            dynamic grown = Find(ui,target); True((int)grown.Width > design.Width && (int)grown.Height > design.Height);
                            Console.WriteLine($"GROW {formName}/{target} actual={grown.Size} design={design}");
                        }
                    }
                    ui.Close(); Finish(failures);
                });

            }
            void Finish(List<string> failures)
            {
                foreach (var failure in failures.Distinct().Take(50)) Console.WriteLine("GEOMETRY_FAIL " + failure);
                if (failures.Count > 0) throw new AssertionFailure($"Layout defects: {failures.Distinct().Count()}");
            }
            void CheckAllTabs(dynamic ui, List<string> failures, string scenario)
            {
                var tabName = ((object)ui).GetType().Name == "MainForm" ? "tabs" :
                    ((object)ui).GetType().Name == "DialogueLabForm" ? "tabsLab" : null;
                if (tabName == null) { CheckGeometry(ui, ui, failures, scenario); return; }
                dynamic tabs = Find(ui, tabName);
                var first = new Dictionary<string, Rectangle>();
                for (var pass = 0; pass < 2; pass++)
                    for (var step = 0; step < (int)tabs.TabCount; step++)
                    {
                        tabs.SelectedIndex = pass == 0 ? step : (int)tabs.TabCount - 1 - step; Pump();
                        dynamic page = tabs.SelectedTab;
                        Rectangle display = tabs.DisplayRectangle, pageBounds = page.Bounds;
                        if (pageBounds != display) failures.Add($"Page/display mismatch {page.Name} {scenario}: {pageBounds}/{display}");
                        CheckGeometry(ui, page, failures, scenario);
                        ResetScroll(page); Pump();
                        void Snapshot(dynamic parent)
                        {
                            foreach (dynamic c in parent.Controls)
                            {
                                if (!(bool)c.Visible || string.IsNullOrEmpty((string)c.Name)) continue;
                                Rectangle b = c.Bounds;
                                if (pass == 0) first[(string)c.Name] = b;
                                else if (first[(string)c.Name] != b) failures.Add($"Repeated selection drift {c.Name} {scenario}");
                                if (((object)c).GetType().Name is "GroupBox" or "Panel" or "TableLayoutPanel") Snapshot(c);
                            }
                        }
                        Snapshot(page);
                    }
                CheckGeometry(ui, ui, failures, scenario);
                Console.WriteLine($"MATRIX {((object)ui).GetType().Name} {scenario} outer={ui.Size} dpi={ui.DeviceDpi} tabs={(int)tabs.TabCount} defects={failures.Count}");
            }
            void CheckGeometry(dynamic form, dynamic parent, List<string> failures, string scenario, bool recurse = true)
            {
                var children = new List<object>();
                foreach (dynamic c in parent.Controls) if ((bool)c.Visible) children.Add((object)c);
                for (var i = 0; i < children.Count; i++)
                {
                    dynamic c = children[i];
                    if (((object)parent).GetType().GetProperty("AutoScroll")?.GetValue(parent) is true) { parent.AutoScrollPosition = Point.Empty; Pump(); }
                    Rectangle b = c.Bounds, client = parent.ClientRectangle;
                    bool scroll = ((object)parent).GetType().GetProperty("AutoScroll")?.GetValue(parent) is true;
                    if (scroll) { client = parent.DisplayRectangle; client = new Rectangle(client.X - (int)parent.Padding.Left, client.Y - (int)parent.Padding.Top, client.Width + (int)parent.Padding.Horizontal, client.Height + (int)parent.Padding.Vertical); }
                    if (!client.Contains(b))
                        failures.Add($"{((object)form).GetType().Name}/{c.Name} {scenario} bounds={b} parent={parent.Name}:{client}");
                    if ((string)c.Name == "gridLibrary")
                    {
                        if ((int)c.Columns.Count != 4) failures.Add("Library column count changed");
                        foreach (dynamic column in c.Columns)
                        {
                            Rectangle header = c.GetColumnDisplayRectangle((int)column.Index, false);
                            if (!(bool)column.Visible || header.Width <= 0 || header.Left < 0 || header.Right > (int)c.ClientSize.Width)
                                failures.Add($"Library column inaccessible {column.Index} {scenario}: {header}");
                        }
                    }
                    for (var j = i + 1; j < children.Count; j++)
                    {
                        dynamic other = children[j];
                        if (b.IntersectsWith((Rectangle)other.Bounds))
                            failures.Add($"Overlap {((object)form).GetType().Name}/{c.Name}/{other.Name} {scenario}: {b}/{other.Bounds}");
                    }
                    if (recurse && (int)c.Controls.Count > 0 && ((object)c).GetType().Name is "GroupBox" or "Panel" or "TableLayoutPanel") CheckGeometry(form, c, failures, scenario);
                    if (((object)c).GetType().Name is not ("GroupBox" or "Panel" or "TableLayoutPanel" or "TabControl" or "TabPage" or "Label" or "StatusStrip") && (bool)c.TabStop)
                    {
                        dynamic? scrollParent = c.Parent;
                        while (scrollParent is not null && ((object)scrollParent).GetType().GetProperty("AutoScroll")?.GetValue(scrollParent) is not true)
                            scrollParent = scrollParent.Parent;
                        if (scrollParent is not null)
                        {
                            scrollParent.ScrollControlIntoView(c); Pump();
                            // Buttons must be wholly reachable; wide text/list controls must expose their origin.
                            var origin = (Point)scrollParent.PointToClient(c.PointToScreen(Point.Empty));
                            var reachable = new Rectangle(origin, (Size)c.Size);
                            if (((object)c).GetType().Name == "Button")
                            {
                                if (!((Rectangle)scrollParent.ClientRectangle).Contains(reachable))
                                    failures.Add($"Scroll cannot reach button {c.Name} {scenario}: {reachable}/{scrollParent.ClientRectangle}");
                            }
                            else if (!((Rectangle)scrollParent.ClientRectangle).Contains(origin))
                                failures.Add($"Scroll cannot reach control origin {c.Name} {scenario}");
                        }
                        if (scrollParent is not null && ((object)c).GetType().Name != "Button")
                        {
                            // Wide editors/lists must expose both ends via the real scroll viewport.
                            foreach (var edge in new[] { Point.Empty, new Point((int)c.ClientSize.Width - 1, (int)c.ClientSize.Height - 1) })
                            {
                                var p = (Point)scrollParent.PointToClient(c.PointToScreen(edge));
                                Point old = scrollParent.AutoScrollPosition;
                                var wanted = new Point(Math.Max(0, p.X - old.X - (int)scrollParent.ClientSize.Width / 2),
                                    Math.Max(0, p.Y - old.Y - (int)scrollParent.ClientSize.Height / 2));
                                scrollParent.AutoScrollPosition = wanted; Pump();
                                p = (Point)scrollParent.PointToClient(c.PointToScreen(edge));
                                if (!((Rectangle)scrollParent.ClientRectangle).Contains(p))
                                    failures.Add($"Control edge unreachable {c.Name} {scenario}: edge={edge} mapped={p} wanted={wanted} actualScroll={scrollParent.AutoScrollPosition} display={scrollParent.DisplayRectangle} client={scrollParent.ClientRectangle}");
                            }
                        }
                        if ((bool)c.Enabled && (bool)c.CanFocus)
                        {
                            var focused = (bool)c.Focus(); Pump();
                            if (!(bool)c.ContainsFocus) failures.Add($"Keyboard focus unavailable {c.Name} {scenario}: Focus={focused} Visible={c.Visible} Enabled={c.Enabled} CanFocus={c.CanFocus} active={form.ActiveControl?.Name}");
                        }
                        if (((object)c).GetType().Name == "Button")
                        {
                            var center = new Point((int)c.Left + (int)c.Width / 2, (int)c.Top + (int)c.Height / 2);
                            var screenCenter = (Point)c.PointToScreen(new Point((int)c.Width / 2, (int)c.Height / 2));
                            dynamic? ancestor = c.Parent;
                            while (ancestor is not null)
                            {
                                if (!((Rectangle)ancestor.ClientRectangle).Contains((Point)ancestor.PointToClient(screenCenter)))
                                    failures.Add($"Ancestor clips button {c.Name}/{ancestor.Name} {scenario}");
                                ancestor = ancestor.Parent;
                            }
                            if (!ReferenceEquals((object?)c.Parent.GetChildAtPoint(center), (object)c))
                                failures.Add($"Button hit test obstructed {c.Name} {scenario}");
                        }
                    }
                }
            }

        }
        finally
        {
            Environment.SetEnvironmentVariable("PSS_UI_SMOKE_WORKSPACE", previous);
        }
        Console.WriteLine($"LAYOUT_CONTRACT_RESULT: {passed} PASS; {failed} FAIL; PHYSICAL_UI_NOT_TESTED; PHYSICAL_DPI_NOT_TESTED; VS_DESIGNER_NOT_TESTED");
        return failed == 0 ? 0 : 1;
    }
}
