using System.Text;
using PhantomSemanticStudio.Core;

Console.OutputEncoding = new UTF8Encoding(false);
try { return Run(args); }
catch (Exception error) when (error is IOException or InvalidDataException or ArgumentException or UnauthorizedAccessException or InvalidOperationException or LmStudioException or HttpRequestException or OperationCanceledException)
{ Console.Error.WriteLine("ОШИБКА: " + error.Message); return 2; }

static int Run(string[] args)
{
    var options = new Dictionary<string, string>(StringComparer.Ordinal); var positional = new List<string>();
    var flags = new[] { "--dry-run", "--reviewed", "--mature", "--help", "--markdown", "--thinking", "--mature-pilot-consent", "--intimate-holdout", "--thinking-quality", "--batch-size-probe", "--matched-profile-probe" };
    var valued = new[] { "--workspace", "--source", "--category", "--persona", "--relation", "--parent", "--key", "--label", "--act", "--intent", "--proposal", "--hash", "--lab", "--model", "--transport", "--reasoning", "--thinking-control", "--limit", "--profiles", "--endpoint", "--model-file", "--thinking-budget" };
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
    if (positional.Count == 1 && positional[0] == "prompt-coverage")
    {
        var hierarchy = PromptHierarchy.Default;
        Console.WriteLine(System.Text.Json.JsonSerializer.Serialize(new { Schema = "pw-prompt-coverage/1", RegistryHash = V4Registry.Default.Hash,
            ConfigHash = hierarchy.Hash, CanonicalCategories = V4Registry.Default.Categories.Count, Families = hierarchy.Families.Select(f => new { f.Id, f.Label, f.Revision }),
            Rows = hierarchy.Coverage(V4Registry.Default), Note = "Назначение не подтверждает качество. Нужны отдельные family/category эксперименты и отзывы." },
            new System.Text.Json.JsonSerializerOptions { WriteIndented = true, Encoder = System.Text.Encodings.Web.JavaScriptEncoder.UnsafeRelaxedJsonEscaping }));
        return 0;
    }
    if (positional.Count == 1 && positional[0] == "corpus-inspect-gguf")
    {
        Console.WriteLine(System.Text.Json.JsonSerializer.Serialize(InspectGguf(Path.GetFullPath(Opt("--model-file"))),
            new System.Text.Json.JsonSerializerOptions { WriteIndented = true, Encoder = System.Text.Encodings.Web.JavaScriptEncoder.UnsafeRelaxedJsonEscaping }));
        return 0;
    }
    if (positional[0].StartsWith("corpus-", StringComparison.Ordinal))
    {
        if (positional.Count != 1) throw new InvalidDataException("Corpus-команда не принимает positional paths; используйте --lab и --source.");
        var sourcePath = Path.GetFullPath(Opt("--source"));
        var profiles = options.TryGetValue("--profiles", out var profilePath) ? new PromptProfiles(Path.GetFullPath(profilePath)) : PromptProfiles.Default;
        var lab = new CorpusLab(Path.GetFullPath(Opt("--lab")), sourcePath, profiles);
        if (positional[0] == "corpus-plan")
        {
            if (!int.TryParse(Opt("--thinking-budget", "2048"), out var thinkingBudget)) throw new InvalidDataException("Нужен целый thinking budget 128..8192.");
            var plan = lab.CreatePlan(options.ContainsKey("--thinking"), Opt("--transport", "OPENAI_COMPAT"), Opt("--reasoning", "default"), Opt("--thinking-control", "none"), options.ContainsKey("--mature-pilot-consent"), options.ContainsKey("--intimate-holdout"), options.ContainsKey("--thinking-quality"), thinkingBudget, options.ContainsKey("--batch-size-probe"), options.ContainsKey("--matched-profile-probe"));
            Console.WriteLine($"FROZEN_PLAN: {plan.Jobs.Length} запросов; {plan.Jobs.Sum(j => j.Request.Count)} кандидатов; registry={plan.RegistryHash}; profile={plan.ProfileHash}; approved=0. HTTP не вызывался."); return 0;
        }
        if (positional[0] == "corpus-status") { PrintCorpus(lab.GetStatus()); return 0; }
        if (positional[0] == "corpus-report") { Console.Write(lab.Report()); return 0; }
        var settings = new StudioSettings { HighFiveRoot = sourcePath, Endpoint = Opt("--endpoint", "http://127.0.0.1:1234/v1"), ModelId = Opt("--model"), TimeoutSeconds = 300 };
        using var http = LmStudioClient.CreateHttpClient(); var client = new LmStudioClient(http);
        // No command here calls a model-management endpoint. The operator starts the model manually.
        var key = Environment.GetEnvironmentVariable("LM_STUDIO_API_KEY") ?? "";
        using var cancel = new CancellationTokenSource();
        ConsoleCancelEventHandler cancelHandler = (_, e) => { e.Cancel = true; cancel.Cancel(); }; Console.CancelKeyPress += cancelHandler;
        try
        {
            if (positional[0] == "corpus-models")
            {
                var models = client.ProbeCorpusModelsAsync(settings, key, cancel.Token).GetAwaiter().GetResult();
                foreach (var m in models.Loaded) Console.WriteLine($"LOADED: key={m.ModelKey}; instance={m.InstanceId}; context={m.ContextLength}; reasoning options={string.Join(',', m.AllowedReasoning)}");
                if (models.Loaded.Count == 0) { Console.WriteLine("MODEL_OFFLINE: загруженных instance нет; скачанный model ID не является разрешением JIT-load."); return 1; }
                return 0;
            }
            if (positional[0] != "corpus-pilot") throw new InvalidDataException("Неизвестная corpus-команда.");
            var limit = int.Parse(Opt("--limit", "1"), System.Globalization.CultureInfo.InvariantCulture);
            var status = lab.RunAsync(client, settings, key, settings.ModelId, limit, cancel.Token, PrintCorpus).GetAwaiter().GetResult(); PrintCorpus(status);
            return status.Phase is "MODEL_OFFLINE_OR_CONNECTION_ERROR" or "TIMEOUT" or "CANCELLED" or "LOST_MODEL" or "INDETERMINATE_REQUEST_REVIEW_REQUIRED" ? 1 : 0;
        }
        finally { Console.CancelKeyPress -= cancelHandler; }
    }
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
static void PrintCorpus(CorpusPilotStatus status) => Console.WriteLine($"{status.Phase}: requests={status.CompletedRequests}/{status.PlannedRequests}; JSON-valid={status.SchemaValidRequests}; triage-clean records={status.TriageCleanRecords}; approved={status.ApprovedRecords}; thinking={status.ThinkingStatus}");
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
Phantom World v4 — offline JSONL и отдельная opt-in лаборатория локального LM Studio. Java/shell не запускаются.
corpus-plan --lab <new-owned-dir> --source <HighFive> [--thinking]
corpus-pilot --lab <dir> --source <HighFive> --model <exact-loaded-instance> [--limit 1]
corpus-status | corpus-report --lab <dir> --source <HighFive>
corpus-models --lab <dir> --source <HighFive> --model <owner-selected-id>
corpus-inspect-gguf --model-file <local.gguf>  Read-only GGUF v3 metadata/template; tensor weights не читаются.
Для corpus-plan: --transport OPENAI_COMPAT|LM_NATIVE --reasoning default|off|on
prompt-coverage — офлайн JSON-карта142категорий,20семейств и неподтверждённых quality gaps; модель не нужна.
--matched-profile-probe --mature-pilot-consent — 32 matched запроса: четыре стратегии × два sampler × PvP/ролевая бравада/спокойный контроль/взрослый неграфический юмор; по4 кандидата, cap2048.
--thinking-control none|reasoning_effort|chat_template_kwargs; --profiles <directory>.
--mature-pilot-consent — отдельное явное разрешение взрослой части синтетического приватного пилота; по умолчанию OFF.
--intimate-holdout — отдельные 18 интимных QA-сцен; персональный пол/биография не переносятся в универсальный runtime.
--thinking-quality --mature-pilot-consent — 4 OFF/ON сравнения, включая неграфический взрослый QA-контекст.
--thinking-budget 128..8192 — одинаковый output cap для OFF/ON (default 2048); выбранный --transport сохраняется.
--batch-size-probe — 6 сопоставимых запросов: два контекста × партии 3/6/12, cap=3072, без взрослой тематики.
HTTP только corpus-models/corpus-pilot, после ручного запуска владельцем. Модель не загружается программой.
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

static object InspectGguf(string file)
{
    // GGUF v3: https://github.com/ggml-org/ggml/blob/master/docs/gguf.md
    // A bounded read-only diagnostic, never a loader, model editor or tokenizer executor.
    PathSafety.AssertNoReparsePoints(file);
    using var stream = new FileStream(file, FileMode.Open, FileAccess.Read, FileShare.Read);
    using var reader = new BinaryReader(stream, new UTF8Encoding(false, true), true);
    const long headerCap = 64 * 1024 * 1024;
    void Bound(long length)
    { if (length < 0 || length > headerCap || stream.Position > Math.Min(stream.Length, headerCap) - length) throw new InvalidDataException("GGUF_METADATA_BOUNDS"); }
    string Text(int cap)
    {
        Bound(8); var length = reader.ReadUInt64();
        if (length > (ulong)cap) throw new InvalidDataException("GGUF_STRING_LIMIT");
        Bound((long)length); var bytes = reader.ReadBytes((int)length);
        if (bytes.Length != (int)length) throw new EndOfStreamException(); return new UTF8Encoding(false, true).GetString(bytes);
    }
    var sizes = new Dictionary<uint, int> { [0]=1,[1]=1,[2]=2,[3]=2,[4]=4,[5]=4,[6]=4,[7]=1,[10]=8,[11]=8,[12]=8 };
    object? Value(uint type, bool keep, int depth)
    {
        if (depth > 4) throw new InvalidDataException("GGUF_ARRAY_DEPTH");
        if (type == 8)
        {
            if (keep) return Text(4 * 1024 * 1024);
            Bound(8); var length = reader.ReadUInt64(); if (length > 4 * 1024 * 1024) throw new InvalidDataException("GGUF_STRING_LIMIT");
            Bound((long)length); stream.Seek((long)length, SeekOrigin.Current); return null;
        }
        if (type == 9)
        {
            Bound(12); var element = reader.ReadUInt32(); var count = reader.ReadUInt64();
            if (count > 2_000_000) throw new InvalidDataException("GGUF_ARRAY_LIMIT");
            if (sizes.TryGetValue(element, out var size)) { var bytes = checked((long)count * size); Bound(bytes); stream.Seek(bytes, SeekOrigin.Current); }
            else for (ulong i=0; i<count; i++) Value(element, false, depth+1);
            return keep ? new { ElementType = element, Count = count } : null;
        }
        if (!sizes.TryGetValue(type, out var scalarSize)) throw new InvalidDataException("GGUF_VALUE_TYPE");
        Bound(scalarSize);
        if (!keep) { stream.Seek(scalarSize, SeekOrigin.Current); return null; }
        return type switch
        {
            0 => reader.ReadByte(), 1 => reader.ReadSByte(), 2 => reader.ReadUInt16(), 3 => reader.ReadInt16(),
            4 => reader.ReadUInt32(), 5 => reader.ReadInt32(), 6 => reader.ReadSingle(), 7 => reader.ReadByte() switch { 0 => false, 1 => true, _ => throw new InvalidDataException("GGUF_BOOL") },
            10 => reader.ReadUInt64(), 11 => reader.ReadInt64(), 12 => reader.ReadDouble(), _ => throw new InvalidDataException("GGUF_VALUE_TYPE")
        };
    }
    if (reader.ReadUInt32() != 0x46554747) throw new InvalidDataException("GGUF_MAGIC_OR_ENDIAN_UNSUPPORTED");
    var version = reader.ReadUInt32(); if (version != 3) throw new InvalidDataException("GGUF_V3_REQUIRED");
    var tensors = reader.ReadUInt64(); var count = reader.ReadUInt64(); if (count > 4096 || tensors > 1_000_000) throw new InvalidDataException("GGUF_HEADER_COUNT");
    var metadata = new SortedDictionary<string, object?>(StringComparer.Ordinal); var seen = new HashSet<string>(StringComparer.Ordinal);
    for (ulong i=0; i<count; i++)
    {
        var key = Text(65535); if (!seen.Add(key)) throw new InvalidDataException("GGUF_DUPLICATE_KEY");
        Bound(4); var type = reader.ReadUInt32(); var keep = key.StartsWith("general.", StringComparison.Ordinal) || key.StartsWith("tokenizer.chat_template", StringComparison.Ordinal);
        var value = Value(type, keep, 0); if (keep) metadata.Add(key, value);
    }
    var headerBytes = stream.Position; var prefix = new byte[checked((int)headerBytes)]; stream.Position = 0; stream.ReadExactly(prefix);
    var template = metadata.TryGetValue("tokenizer.chat_template", out var item) ? item as string : null;
    return new { File = file, FileBytes = stream.Length, Version = version, Tensors = tensors, MetadataItems = count, MetadataBytes = headerBytes,
        MetadataPrefixSha256 = Convert.ToHexStringLower(System.Security.Cryptography.SHA256.HashData(prefix)),
        ChatTemplateSha256 = template == null ? null : Convert.ToHexStringLower(System.Security.Cryptography.SHA256.HashData(Encoding.UTF8.GetBytes(template))), Metadata = metadata };
}
