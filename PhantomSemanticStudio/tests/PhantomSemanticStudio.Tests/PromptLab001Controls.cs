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
            True((bool)Control("txtCategoryInstruction").Multiline);
            True(!(bool)Control("btnFamily").Enabled);
            True(!(bool)Control("btnGenerateFamilies").Enabled);
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
                Equal(9, (int)Control("cboProfiles").Items.Count); Equal(12, (int)Control("gridParameters").Rows.Count);
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
        Test("001 family control restores separate category and emotional-state drafts without touching the accepted version", () =>
        {
            using var f = new Fixture(); using var workspace = new WorkspaceStore(Path.Combine(f.Root, "workspace"), f.Module);
            var store = new PromptLabStore(workspace); using var lab = new PromptLabSession(store);
            var version = lab.CreateFamilyProfile(new PromptLabVersion().Scenario, "000 свой профиль семейства"); store.AcceptVersion(version.Id); var hash = store.VersionHash(version.Id);
            var type = assembly.GetType("PhantomSemanticStudio.WinForms.PromptLabControl", true)!;
            using (var disposable = (IDisposable)Activator.CreateInstance(type)!)
            {
                dynamic ui = disposable; ui.SetContext(workspace, (Func<StudioSettings>)(() => new() { HighFiveRoot = f.Module }), (Func<string>)(() => ""));
                dynamic Control(string name) => ui.Controls.Find(name, true)[0];
                True((bool)Control("txtCategoryInstruction").Enabled);
                Control("txtCategoryInstruction").Text = "Только конкретная помощь без лозунгов.";
                Control("gridScope").Rows[0].Cells["colEmotion"].Value = "устал";
                True((bool)ui.TryClose());
            }
            var draft = store.GetDraft(version.Id)!; Equal("Только конкретная помощь без лозунгов.", draft.CategoryInstructions[0]);
            Equal("устал", draft.Scenario.Style.EmotionalState); Equal(hash, store.VersionHash(version.Id)); Equal(version.Id, store.GetProfile(version.ProfileId).AcceptedVersionId);
            lab.PrepareVersion(draft).Validate(false, store.EffectiveRegistry());
        });
        Test("001 existing laboratory gains separate speech presets without changing a user profile or repeating migration", () =>
        {
            using var f = new Fixture(); using var workspace = new WorkspaceStore(Path.Combine(f.Root, "workspace"), f.Module);
            var store = new PromptLabStore(workspace); var original = store.SaveVersion(new() { Name = "Свой рабочий профиль", System = "Коротко, по ситуации." });
            store.AcceptVersion(original.Id); var hash = store.VersionHash(original.Id);
            var type = assembly.GetType("PhantomSemanticStudio.WinForms.PromptLabControl", true)!;
            for (var restart = 0; restart < 2; restart++)
            {
                using var disposable = (IDisposable)Activator.CreateInstance(type)!; dynamic ui = disposable;
                ui.SetContext(workspace, (Func<StudioSettings>)(() => new() { HighFiveRoot = f.Module }), (Func<string>)(() => ""));
                Equal(6, store.ListProfiles().Count); Equal(hash, store.VersionHash(original.Id)); Equal(original.Id, store.GetProfile(original.ProfileId).AcceptedVersionId);
            }
        });
        Console.WriteLine($"RESULT: {passed} PASS; {failed} FAIL; no windows shown or computer-use actions");
        return failed == 0 ? 0 : 1;
    }
}
