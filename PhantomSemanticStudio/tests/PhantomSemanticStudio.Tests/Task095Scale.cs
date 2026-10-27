using System.Diagnostics;
using System.Text;
using System.Text.Json;
using PhantomSemanticStudio.Core;

internal static partial class Program
{
    private static int Task095Scale(string[] args)
    {
        if (args.Length != 3) throw new ArgumentException("--task095-scale <external-root> <protected-source>");
        var output = PathSafety.Canonical(args[1]);
        PathSafety.AssertDisjoint(output, args[2]);
        if (Directory.Exists(output)) throw new InvalidDataException("Scale root должен быть новым.");
        Directory.CreateDirectory(output);
        var category = V4Registry.Default.Categories.Single(c => c.Key == "hybrid.trade.absurd_offers");
        foreach (var count in new[] { 200, 10000, 100000 })
        {
            var root = Path.Combine(output, count.ToString(System.Globalization.CultureInfo.InvariantCulture));
            var batches = Path.Combine(root, "input");
            Directory.CreateDirectory(batches);
            for (var batch = 0; batch < count / 200; batch++)
            {
                using var writer = new StreamWriter(Path.Combine(batches, $"reply-{batch:D4}.jsonl"), false, new UTF8Encoding(false));
                writer.NewLine = "\n";
                writer.WriteLine(JsonSerializer.Serialize(new { schema = "pw-semantic-batch/1", category = category.Key, mode = "REPLY", act = category.Act, language = "ru", count = 200 }, WorkspaceStore.JsonOptions).Replace("\r", "").Replace("\n", ""));
                for (var index = batch * 200; index < (batch + 1) * 200; index++)
                    writer.WriteLine(JsonSerializer.Serialize(new { text = $"Проверочный ответ для рынка номер {index}.", stance = "NEUTRAL" }, WorkspaceStore.JsonOptions).Replace("\r", "").Replace("\n", ""));
            }
            File.WriteAllText(Path.Combine(batches, "trigger.jsonl"),
                JsonSerializer.Serialize(new { schema = "pw-semantic-batch/1", category = category.Key, mode = "TRIGGER", act = category.Act, language = "ru", count = 1 }, WorkspaceStore.JsonOptions).Replace("\r", "").Replace("\n", "") + "\n"
                + JsonSerializer.Serialize(new { text = "Проверка масштабирования.", intent = category.Intent }, WorkspaceStore.JsonOptions).Replace("\r", "").Replace("\n", "") + "\n", new UTF8Encoding(false));
            var workspace = new V4Workspace(Path.Combine(root, "workspace"), args[2]);
            var timer = Stopwatch.StartNew();
            var imported = workspace.Import(batches);
            var importMs = timer.Elapsed.TotalMilliseconds;
            if (!imported.Success || imported.Accepted != count + 1)
                throw new InvalidDataException(string.Join("\n", imported.Diagnostics.Take(5)));
            timer.Restart();
            var built = workspace.Build();
            var buildMs = timer.Elapsed.TotalMilliseconds;
            timer.Restart();
            var verified = workspace.Verify(built.StagePath);
            var verifyMs = timer.Elapsed.TotalMilliseconds;
            if (!verified.Success || verified.Records != count + 1) throw new InvalidDataException("SCALE_VERIFY_FAILED");
            var indexBytes = new FileInfo(Path.Combine(built.StagePath, "replies.idx")).Length + new FileInfo(Path.Combine(built.StagePath, "triggers.idx")).Length;
            var row = new { responses = count, records = count + 1, importMs, buildMs, verifyMs, rowsPerSecond = (count + 1) * 1000 / importMs, indexBytes, stage = built.StagePath, manifestHash = built.ManifestHash };
            File.WriteAllText(Path.Combine(root, "metrics.json"), JsonSerializer.Serialize(row, WorkspaceStore.JsonOptions), new UTF8Encoding(false));
            Console.WriteLine(JsonSerializer.Serialize(row));
        }
        return 0;
    }
}
