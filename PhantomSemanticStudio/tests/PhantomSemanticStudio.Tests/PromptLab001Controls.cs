using System.Reflection;
using System.Diagnostics;
using PhantomSemanticStudio.Core;

internal static partial class Program
{
    private static int PromptLabControls(string[] args)
    {
        var studio = Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "../../../../../"));
        var path = args.Length > 1 ? Path.GetFullPath(args[1]) : Path.Combine(studio, "src/PhantomSemanticStudio.WinForms/bin/Release/net10.0-windows/PhantomSemanticStudio.dll");
        var assembly = Assembly.LoadFrom(path);
        Test("001 explicit workspace startup is design-safe and checks an isolated root without GUI or inference", () =>
        {
            using var f = new Fixture(); var root = Path.Combine(f.Root, "external laboratory");
            var type = assembly.GetType("PhantomSemanticStudio.WinForms.MainForm", true)!;
            True(type.GetConstructor([typeof(string)]) != null);
            using var defaultForm = (IDisposable)Activator.CreateInstance(type)!;
            using var selectedForm = (IDisposable)Activator.CreateInstance(type, [root])!;
            True(!Directory.Exists(root));
            var result = CheckWorkspace(["--workspace", root, "--check-workspace"]);
            Equal(0, result.ExitCode); True(result.Output.Contains(PathSafety.Canonical(root), StringComparison.Ordinal));
            Equal("workspace.lock", string.Join(",", Directory.GetFiles(root).Select(Path.GetFileName)));
            using var workspace = new WorkspaceStore(root, f.Module);
            Equal(2, CheckWorkspace(["--check-workspace", "--workspace", root]).ExitCode);
        });
        Test("001 explicit workspace startup rejects malformed arguments protected roots and smoke ambiguity without GUI", () =>
        {
            var type = assembly.GetType("PhantomSemanticStudio.WinForms.MainForm", true)!;
            True(type.GetConstructor([typeof(string)]) != null);
            using var f = new Fixture(); var root = Path.Combine(f.Root, "never created");
            foreach (var invalid in new string[][] {
                ["--workspace"], ["--check-workspace"], ["--workspace", "relative", "--check-workspace"],
                ["--workspace", root, "--workspace", root, "--check-workspace"],
                ["--workspace", root, "--unknown", "--check-workspace"],
                ["--workspace", root, "--check-workspace", "--check-workspace"] })
                Equal(2, CheckWorkspace(invalid).ExitCode);
            Equal(2, CheckWorkspace(["--workspace", root, "--check-workspace"], root).ExitCode);
            True(!Directory.Exists(root));
            var protectedRoot = Path.GetFullPath(Path.Combine(Path.GetDirectoryName(path)!, "../../../../../../L2J_Mobius_CT_2.6_HighFive"));
            Equal(2, CheckWorkspace(["--workspace", protectedRoot, "--check-workspace"]).ExitCode);
            var configured = Path.Combine(f.Root, "configured"); Directory.CreateDirectory(configured);
            File.WriteAllText(Path.Combine(configured, "settings.json"), System.Text.Json.JsonSerializer.Serialize(new StudioSettings { HighFiveRoot = configured }));
            Equal(2, CheckWorkspace(["--workspace", configured, "--check-workspace"]).ExitCode);
            True(!File.Exists(Path.Combine(configured, "workspace.lock")));
        });

        (int ExitCode, string Output) CheckWorkspace(string[] arguments, string? smokeRoot = null)
        {
            var start = new ProcessStartInfo("dotnet") { UseShellExecute = false, CreateNoWindow = true, RedirectStandardOutput = true, RedirectStandardError = true };
            start.ArgumentList.Add(path); foreach (var argument in arguments) start.ArgumentList.Add(argument);
            start.Environment.Remove("PSS_UI_SMOKE_WORKSPACE");
            if (smokeRoot != null) start.Environment["PSS_UI_SMOKE_WORKSPACE"] = smokeRoot;
            using var process = Process.Start(start)!;
            var output = process.StandardOutput.ReadToEndAsync(); var error = process.StandardError.ReadToEndAsync();
            if (!process.WaitForExit(15000)) { process.Kill(true); throw new InvalidOperationException("Workspace check did not terminate: " + string.Join(" | ", arguments)); }
            return (process.ExitCode, output.GetAwaiter().GetResult() + error.GetAwaiter().GetResult());
        }
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
            True(!(bool)Control("cboContextPresentation").Enabled);
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
                True((bool)Control("cboContextPresentation").Enabled);
                Control("txtCategoryInstruction").Text = "Только конкретная помощь без лозунгов.";
                Control("gridScope").Rows[0].Cells["colEmotion"].Value = "устал";
                Equal(0, (int)Control("cboContextPresentation").SelectedIndex);
                Control("cboContextPresentation").SelectedIndex = 1;
                True((bool)ui.TryClose());
            }
            var draft = store.GetDraft(version.Id)!; Equal("Только конкретная помощь без лозунгов.", draft.CategoryInstructions[0]);
            Equal("устал", draft.Scenario.Style.EmotionalState); Equal(hash, store.VersionHash(version.Id)); Equal(version.Id, store.GetProfile(version.ProfileId).AcceptedVersionId);
            Equal("READABLE_SCENE", draft.ContextPresentation); True(store.GetVersion(version.Id).ContextPresentation == null);
            lab.PrepareVersion(draft).Validate(false, store.EffectiveRegistry());
            using var restored = (IDisposable)Activator.CreateInstance(type)!; dynamic restoredUi = restored;
            restoredUi.SetContext(workspace, (Func<StudioSettings>)(() => new() { HighFiveRoot = f.Module }), (Func<string>)(() => ""));
            Equal(1, (int)restoredUi.Controls.Find("cboContextPresentation", true)[0].SelectedIndex);
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
