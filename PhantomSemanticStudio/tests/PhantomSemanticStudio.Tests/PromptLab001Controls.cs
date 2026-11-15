using System.Reflection;
using PhantomSemanticStudio.Core;

internal static partial class Program
{
    private static int PromptLabControls(string[] args)
    {
        var studio = Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "../../../../../"));
        var path = args.Length > 1 ? Path.GetFullPath(args[1]) : Path.Combine(studio, "src/PhantomSemanticStudio.WinForms/bin/Release/net10.0-windows/PhantomSemanticStudio.dll");
        var assembly = Assembly.LoadFrom(path);
        Test("001 prompt laboratory is a headless design-safe control with separate editors readable grids and disabled inference before context", () =>
        {
            var type = assembly.GetType("PhantomSemanticStudio.WinForms.PromptLabControl", true)!;
            using var disposable = (IDisposable)Activator.CreateInstance(type)!; dynamic ui = disposable;
            dynamic Control(string name) => ui.Controls.Find(name, true)[0];
            True((bool)Control("txtSystem").Multiline); True((bool)Control("txtUser").Multiline);
            True(!ReferenceEquals((object)Control("txtSystem"), (object)Control("txtUser")));
            True(!(bool)Control("btnGenerate").Enabled); True(!(bool)Control("btnRefine").Enabled);
            True(!(bool)Control("gridV1").AllowUserToAddRows); True(!(bool)Control("gridV2").AllowUserToAddRows);
            True((int)Control("gridV1").Columns.Count >= 3); True((int)Control("gridV2").Columns.Count >= 3);
            True(((string)Control("btnCompare").Text).Contains("сравнить", StringComparison.OrdinalIgnoreCase));
        });
        Test("001 prompt control binds isolated history and restores an unfinished system draft without showing a window", () =>
        {
            using var f = new Fixture(); using var workspace = new WorkspaceStore(Path.Combine(f.Root, "workspace"), f.Module);
            var type = assembly.GetType("PhantomSemanticStudio.WinForms.PromptLabControl", true)!;
            using (var disposable = (IDisposable)Activator.CreateInstance(type)!)
            {
                dynamic ui = disposable;
                ui.SetContext(workspace, (Func<StudioSettings>)(() => new() { HighFiveRoot = f.Module }), (Func<string>)(() => ""));
                dynamic Control(string name) => ui.Controls.Find(name, true)[0];
                Equal(4, (int)Control("cboProfiles").Items.Count); Equal(12, (int)Control("gridParameters").Rows.Count);
                True((bool)Control("btnGenerate").Enabled);
                Control("txtSystem").Text = "Незавершённая ручная редакция";
                var selected = (string)Control("cboVersions").SelectedValue;
                var store = new PromptLabStore(workspace); var old = store.GetVersion(selected);
                store.SaveVersion(old with { Id = "", ParentId = old.Id, System = "Другая сохранённая версия." });
                // Rebinding uses only the public context lifecycle; no button click or window is involved.
                True((bool)ui.TryClose());
                True(store.GetDraft(selected) != null);
                store.SelectVersion(selected);
            }
            using (var disposable = (IDisposable)Activator.CreateInstance(type)!)
            {
                dynamic ui = disposable;
                ui.SetContext(workspace, (Func<StudioSettings>)(() => new() { HighFiveRoot = f.Module }), (Func<string>)(() => ""));
                Equal("Незавершённая ручная редакция", (string)ui.Controls.Find("txtSystem", true)[0].Text);
            }
        });
        Console.WriteLine($"RESULT: {passed} PASS; {failed} FAIL; no windows shown or computer-use actions");
        return failed == 0 ? 0 : 1;
    }
}
