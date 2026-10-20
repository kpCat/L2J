using PhantomSemanticStudio.Core;

internal static partial class Program
{
    private static void Task062()
    {
        var repository = new DirectoryInfo(AppContext.BaseDirectory);
        while (repository != null && !Directory.Exists(Path.Combine(repository.FullName, ".git"))
            && !File.Exists(Path.Combine(repository.FullName, ".git"))) repository = repository.Parent;
        if (repository == null) throw new InvalidOperationException("TASK062 requires the source checkout.");
        var module = Path.Combine(repository.FullName, "L2J_Mobius_CT_2.6_HighFive");

        Test("TASK062 default source uses the sibling module regardless of cwd", () =>
        {
            var previous = Environment.CurrentDirectory;
            try
            {
                Environment.CurrentDirectory = Path.GetTempPath();
                var settings = new StudioSettings();
                Equal(module, settings.HighFiveRoot);
                True(File.Exists(Path.Combine(settings.HighFiveRoot, "build.xml")));
                settings.HighFiveRoot = Path.Combine(Path.GetTempPath(), "explicit-source062");
                Equal(Path.Combine(Path.GetTempPath(), "explicit-source062"), settings.HighFiveRoot);
            }
            finally { Environment.CurrentDirectory = previous; }
        });

        Test("TASK062 Studio workspace cannot write inside the shared repository", () =>
        {
            Equal(repository.FullName, PathSafety.RepositoryBoundary(module));
            var destination = Path.Combine(repository.FullName, "PhantomSemanticStudio", "artifacts", "forbidden-task062-workspace");
            True(!Directory.Exists(destination));
            Throws(() => { using var store = new WorkspaceStore(destination, module); });
            True(!Directory.Exists(destination));
        });
    }
}
