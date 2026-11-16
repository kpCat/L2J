using PhantomSemanticStudio.Core;

namespace PhantomSemanticStudio.WinForms;

internal static class Program
{
    [STAThread]
    private static int Main(string[] args)
    {
        string? workspace = null; var checkWorkspace = false;
        try
        {
            for (var i = 0; i < args.Length; i++)
            {
                if (args[i] == "--workspace" && workspace == null && i + 1 < args.Length)
                    workspace = PathSafety.Canonical(args[++i]);
                else if (args[i] == "--check-workspace" && !checkWorkspace) checkWorkspace = true;
                else throw new InvalidDataException("Допустимо: --workspace <абсолютный путь> [--check-workspace]. Аргументы нельзя повторять.");
            }
            if (checkWorkspace && workspace == null) throw new InvalidDataException("Для проверки нужен явный --workspace; workspace по умолчанию не открывается.");
            if (workspace != null && !string.IsNullOrEmpty(Environment.GetEnvironmentVariable("PSS_UI_SMOKE_WORKSPACE")))
                throw new InvalidDataException("Явный --workspace нельзя совмещать с PSS_UI_SMOKE_WORKSPACE.");
            if (checkWorkspace)
            {
                using var store = WorkspaceStore.OpenConfigured(workspace!, new StudioSettings().HighFiveRoot);
                var settings = store.LoadSettings(); settings.Validate(); store.ProtectSource(settings.HighFiveRoot);
                store.LoadSession();
                Console.WriteLine("WORKSPACE_OK: " + store.Root);
                return 0;
            }
        }
        catch (Exception ex) when (ex is InvalidDataException or IOException or ArgumentException or UnauthorizedAccessException or System.Text.Json.JsonException)
        {
            Console.Error.WriteLine("Workspace не открыт: " + ex.Message);
            return 2;
        }
        ApplicationConfiguration.Initialize();
        if (workspace == null && string.IsNullOrEmpty(Environment.GetEnvironmentVariable("PSS_UI_SMOKE_WORKSPACE")))
        {
            using var picker = new FolderBrowserDialog
            {
                Description = "Выберите workspace Studio с вашими профилями и историей. Данные из других каталогов не переносятся.",
                UseDescriptionForTitle = true,
                InitialDirectory = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "PhantomSemanticStudio"),
                ShowNewFolderButton = true
            };
            if (picker.ShowDialog() != DialogResult.OK) return 0;
            workspace = PathSafety.Canonical(picker.SelectedPath);
        }
        Application.Run(workspace == null ? new MainForm() : new MainForm(workspace));
        return 0;
    }
}
