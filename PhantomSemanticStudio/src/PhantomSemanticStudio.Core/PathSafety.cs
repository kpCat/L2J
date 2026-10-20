namespace PhantomSemanticStudio.Core;

public static class PathSafety
{
    private static StringComparison Comparison => OperatingSystem.IsWindows() ? StringComparison.OrdinalIgnoreCase : StringComparison.Ordinal;
    public static string Canonical(string path)
    {
        if (string.IsNullOrWhiteSpace(path) || !Path.IsPathFullyQualified(path)) throw new InvalidDataException("Путь должен быть абсолютным.");
        return Path.TrimEndingDirectorySeparator(Path.GetFullPath(path));
    }
    public static bool IsWithin(string path, string parent)
    {
        var p = Canonical(path); var root = Canonical(parent);
        return p.Equals(root, Comparison) || p.StartsWith(Path.EndsInDirectorySeparator(root) ? root : root + Path.DirectorySeparatorChar, Comparison);
    }
    public static void AssertDisjoint(string source, string destination)
    {
        if (IsWithin(source, destination) || IsWithin(destination, source))
            throw new InvalidDataException("Рабочий каталог и исходный L2J не должны пересекаться — запись заблокирована.");
        AssertNoReparsePoints(source); AssertNoReparsePoints(destination);
    }
    public static void AssertNoReparsePoints(string path)
    {
        string? current = Canonical(path);
        while (current != null)
        {
            // Проверка существующих предков закрывает обычные junction/symlink пути.
            // Это не OS sandbox против враждебной подмены каталогов в момент записи.
            try
            {
                var attrs = File.GetAttributes(current);
                if ((attrs & FileAttributes.ReparsePoint) != 0) throw new InvalidDataException("Ссылки/junction в пути не разрешены: " + current);
            }
            catch (FileNotFoundException) { }
            catch (DirectoryNotFoundException) { }
            var parent = Path.GetDirectoryName(current);
            current = parent == current ? null : parent;
        }
    }
    public static string ResolveRelative(string root, string relative)
    {
        if (string.IsNullOrWhiteSpace(relative) || relative.Contains('\\') || relative.Contains(':') || relative.Contains('\0')
            || Path.IsPathRooted(relative) || relative.Split('/').Any(x => x is "" or "." or ".."))
            throw new InvalidDataException("Недопустимый относительный путь: " + relative);
        var resolved = Path.GetFullPath(Path.Combine(root, relative.Replace('/', Path.DirectorySeparatorChar)));
        if (!IsWithin(resolved, root)) throw new InvalidDataException("Путь вышел за пределы каталога.");
        AssertNoReparsePoints(resolved);
        return resolved;
    }
    public static string RepositoryBoundary(string module)
    {
        var path = Canonical(module);
        for (var d = new DirectoryInfo(path); d != null; d = d.Parent)
            if (Directory.Exists(Path.Combine(d.FullName, ".git")) || File.Exists(Path.Combine(d.FullName, ".git"))) return d.FullName;
        // Для указанной пользователем структуры защищаем весь родительский L2J_Mobius.
        var parent = Directory.GetParent(path);
        return parent != null && parent.Name.Equals("L2J_Mobius", StringComparison.OrdinalIgnoreCase) ? parent.FullName : path;
    }
}
