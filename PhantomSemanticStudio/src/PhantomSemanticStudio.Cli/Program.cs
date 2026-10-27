using System.Text;
using PhantomSemanticStudio.Core;

Console.OutputEncoding = new UTF8Encoding(false);
try { return Run(args); }
catch (Exception error) when (error is IOException or InvalidDataException or ArgumentException or UnauthorizedAccessException or InvalidOperationException)
{ Console.Error.WriteLine("ОШИБКА: " + error.Message); return 2; }

static int Run(string[] args)
{
    var options = new Dictionary<string, string>(StringComparer.Ordinal); var positional = new List<string>();
    var flags = new[] { "--dry-run", "--reviewed", "--mature", "--help", "--markdown" };
    var valued = new[] { "--workspace", "--source", "--category", "--persona", "--relation", "--parent", "--key", "--label", "--act", "--intent", "--proposal", "--hash" };
    for (var i = 0; i < args.Length; i++)
    {
        var arg = args[i];
        if (!arg.StartsWith("--", StringComparison.Ordinal)) { positional.Add(arg); continue; }
        if (flags.Contains(arg)) { if (!options.TryAdd(arg, "true")) throw new InvalidDataException("Опция повторена: " + arg); }
        else if (valued.Contains(arg) && i + 1 < args.Length && !args[i + 1].StartsWith("--", StringComparison.Ordinal))
        { if (!options.TryAdd(arg, args[++i])) throw new InvalidDataException("Опция повторена: " + arg); }
        else throw new InvalidDataException("Неизвестная опция или отсутствует значение: " + arg);
    }
    string Opt(string key, string? fallback = null) => options.TryGetValue(key, out var value) ? value : fallback ?? throw new InvalidDataException("Нужна опция " + key);
    string Input() => positional.Count == 2 ? Path.GetFullPath(positional[1]) : throw new InvalidDataException("Укажите один путь к файлу/папке.");
    if (positional.Count == 0 || options.ContainsKey("--help")) { Help(); return 0; }
    var workspace = Path.GetFullPath(Opt("--workspace", Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "PhantomSemanticStudio", "workspace", "v4")));
    var source = Opt("--source", FindSource());
    if (source.Length == 0) throw new InvalidDataException("Укажите --source <абсолютный путь к L2J>; workspace не должен пересекаться с репозиторием.");
    var store = new V4Workspace(workspace, Path.GetFullPath(source));
    switch (positional[0])
    {
        case "validate": return Report(store.Validate(Input()));
        case "import": return Report(store.Import(Input(), options.ContainsKey("--dry-run")));
        case "build":
            var build = store.Build(); Console.WriteLine($"STAGED_V4_UNVALIDATED: {build.StagePath}\nManifest SHA256: {build.ManifestHash}\nЗаписей: {build.Records}; установка в сервер не выполнялась."); return 0;
        case "verify":
            var verified = store.Verify(Input()); foreach (var d in verified.Diagnostics) Console.WriteLine(d);
            Console.WriteLine($"{(verified.Success ? "PASS_V4_INTEGRITY" : "REJECTED")}: {verified.Records}; {verified.ManifestHash}"); return verified.Success ? 0 : 1;
        case "preview":
            var entry = store.Preview(Opt("--category"), Opt("--persona", "calm"), Opt("--relation", "UNKNOWN"), options.ContainsKey("--mature"));
            Console.WriteLine("C# APPROXIMATE_PREVIEW; настоящий Java consumer проверяется отдельно."); Console.WriteLine(entry == null ? "NO_MATCH / безопасное молчание" : $"{entry.Stance}: {entry.Text}"); return 0;
        case "stats":
            var stats = store.Stats(); Console.WriteLine("Всего: " + stats.Total);
            Console.WriteLine("category\tincoming triggers\tresponses\tinitiatives");
            foreach (var c in stats.Coverage) Console.WriteLine($"{c.Category}\t{c.IncomingTriggers}\t{c.Responses}\t{c.Initiatives}"); return 0;
        case "categories":
            if (options.ContainsKey("--markdown")) { Console.Write(V4Passport.CategoryIndex(new V4Registry(store.Categories(), V4Registry.Default.Voices, V4Registry.Default.Events))); return 0; }
            foreach (var c in store.Categories()) Console.WriteLine($"{c.Key}\t{c.Label}\t{c.Act}\t{c.Intent}"); return 0;
        case "export-passport":
            var target = Input(); store.ExportPassport(target); Console.WriteLine("Паспорт: " + target); return 0;
        case "taxonomy" when positional.Count == 2 && positional[1] == "propose":
            var proposal = store.Propose(Opt("--parent"), Opt("--key"), Opt("--label"), Opt("--act"), Opt("--intent"));
            Console.WriteLine($"REVIEW_REQUIRED\n{proposal.Path}\nSHA256: {proposal.Hash}\nПроверьте parent, category, label, act и intent до approve."); return 0;
        case "taxonomy" when positional.Count == 2 && positional[1] == "approve":
            store.Approve(Path.GetFullPath(Opt("--proposal")), Opt("--hash"), options.ContainsKey("--reviewed")); Console.WriteLine("Категория принята в workspace; выполните build/verify/export-passport. Сервер не изменён."); return 0;
        default: throw new InvalidDataException("Неизвестная команда. Используйте --help.");
    }
}
static int Report(V4Report report)
{
    foreach (var d in report.Diagnostics) Console.WriteLine(d);
    Console.WriteLine($"{(report.Success ? "VALID" : "REJECTED")}: новых {report.Accepted}, дублей {report.Duplicates}, проверено {report.Entries.Count}; input SHA256 {report.InputHash}");
    return report.Success ? 0 : 1;
}
static string FindSource()
{
    for (var directory = new DirectoryInfo(AppContext.BaseDirectory); directory != null; directory = directory.Parent)
    {
        var candidate = Path.Combine(directory.FullName, "L2J_Mobius_CT_2.6_HighFive");
        if (File.Exists(Path.Combine(candidate, "build.xml"))) return candidate;
    }
    return "";
}
static void Help() => Console.WriteLine("""
Phantom World v4 — offline JSONL, без LM Studio/Java/shell/network.
validate <file|folder>     Проверить JSONL и ссылки.
import <file|folder> [--dry-run]  Принять атомарно; повтор идемпотентен.
build                      Создать отдельный immutable stage.
verify <stage>             Проверить manifest, hashes, records и indexes.
preview --category <key> [--persona calm] [--relation UNKNOWN] [--mature]
stats | categories         Coverage по трём режимам или полный реестр.
export-passport <workspace/file.md>  Один самодостаточный паспорт.
taxonomy propose --parent <key> --key <new.leaf> --label <русское имя> --act <act> --intent <intent>
taxonomy approve --proposal <path> --hash <reviewed SHA256> --reviewed
Общие опции в любой позиции: --workspace <absolute> --source <absolute L2J>.
Workspace по умолчанию: LOCALAPPDATA/PhantomSemanticStudio/workspace/v4.
Коды выхода: 0 успешно, 1 данные отклонены, 2 ошибка операции.
""");
