using System.Net;
using System.Text;
using System.Text.Json;
using System.Diagnostics;
using PhantomSemanticStudio.Core;

internal static partial class Program
{
    private static async Task CorpusCampaign001()
    {
        await TestAsync("001 draft campaign connects frozen production queue durable runs and global index without approval", async () =>
        {
            using var f = new Fixture(); using var workspace = new WorkspaceStore(Path.Combine(f.Root, "worker"), f.Module);
            var (plan, hash) = CampaignWorkerPlan(workspace, 3);
            using var handler = new CampaignMockHandler();
            using (var worker = new CorpusCampaignWorker(workspace, _ => new HttpClient(handler, false)))
            {
                var status = await worker.RunAsync(plan.Id, hash, 3, CancellationToken.None);
                Equal(3, handler.Posts); Equal(3L, status.SchemaValid); Equal(3L, status.Pending); Equal(0L, status.Approved);
                Equal("BUDGET_EXHAUSTED", status.State);
            }
            using (var worker = new CorpusCampaignWorker(workspace, _ => new HttpClient(handler, false)))
                Equal(3L, (await worker.RunAsync(plan.Id, hash, 3, CancellationToken.None)).SchemaValid);
            Equal(3, handler.Posts); Equal(3L, new CorpusCandidateIndex(workspace).Stats().Occurrences);
            var outside = CorpusCampaignControl.Status(workspace.Root, f.Module, plan.Id, hash);
            Equal(3L, outside.Pending); Equal(0L, outside.Approved); Equal(0L, outside.Reviewed);
        });
        await TestAsync("001 draft campaign recovers orphan runs receipts and index checkpoints without duplicate POST or self evidence", async () =>
        {
            foreach (var crashPoint in new[] { "RUN_CREATED", "GENERATION_SAVED", "INDEX_APPENDED" })
            {
                using var f = new Fixture(); using var workspace = new WorkspaceStore(Path.Combine(f.Root, "worker"), f.Module);
                var (plan, hash) = CampaignWorkerPlan(workspace, 1); using var handler = new CampaignMockHandler(); var crashed = false;
                using (var worker = new CorpusCampaignWorker(workspace, _ => new HttpClient(handler, false), point =>
                    { if (!crashed && point == crashPoint) { crashed = true; throw new IOException("Synthetic crash " + point); } }))
                    await ThrowsAsync(() => worker.RunAsync(plan.Id, hash, 1, CancellationToken.None));
                True(crashed);
                using (var worker = new CorpusCampaignWorker(workspace, _ => new HttpClient(handler, false)))
                {
                    var recovered = await worker.RunAsync(plan.Id, hash, 1, CancellationToken.None);
                    Equal(1L, recovered.SchemaValid); Equal(1L, recovered.Pending); Equal(0L, recovered.Rejected);
                }
                Equal(1, handler.Posts); Equal(1, new PromptLabStore(workspace).ListRuns().Count(r => r.ExperimentId != "" && r.Id != new PromptLabStore(workspace).ListProduction()[0].Evidence[0].RunId));
                Equal(1L, new CorpusCandidateIndex(workspace).Stats().Occurrences);
            }
        });
        await TestAsync("001 detached mock worker survives launcher exit and acknowledges independent pause resume stop", async () =>
        {
            using var f = new Fixture(); var root = Path.Combine(f.Root, "detached worker");
            CorpusCampaignPlan plan; string hash;
            using (var workspace = new WorkspaceStore(root, f.Module))
            {
                (plan, hash) = CampaignWorkerPlan(workspace, 10);
                _ = new CorpusCampaignJournal(workspace, plan.Id, hash);
                var cli = Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "../../../../../src/PhantomSemanticStudio.Cli/bin/Release/net10.0/PhantomSemanticStudio.Cli.dll"));
                using var check = StartCampaignProcess(cli, ["campaign-status", plan.Id, "--hash", hash, "--workspace", root, "--source", f.Module]);
                await check.WaitForExitAsync().WaitAsync(TimeSpan.FromSeconds(15)); Equal(0, check.ExitCode);
            }
            foreach (var command in new[] { "PAUSE", "STOP" })
            {
                var receipt = Path.Combine(root, "child-" + command + ".txt");
                using (var launcher = StartCampaignProcess(typeof(Program).Assembly.Location,
                    ["--campaign-mock-launch", root, f.Module, plan.Id, hash, receipt, command == "STOP" ? "resume" : "start"]))
                { await launcher.WaitForExitAsync().WaitAsync(TimeSpan.FromSeconds(15)); Equal(0, launcher.ExitCode); }
                using var child = Process.GetProcessById(int.Parse(File.ReadAllText(receipt)));
                try
                {
                    var expectedPosts = command == "PAUSE" ? 1 : 2;
                    await CampaignWait(() => File.Exists(Path.Combine(root, "mock-posts.txt")) && File.ReadAllLines(Path.Combine(root, "mock-posts.txt")).Length >= expectedPosts);
                    True(!child.HasExited); Throws(() => { using var busy = new WorkspaceStore(root, f.Module); });
                    var pending = CorpusCampaignControl.Request(root, f.Module, plan.Id, hash, command); Equal(command, pending.Desired);
                    Equal((long)expectedPosts, pending.Issued);
                    await child.WaitForExitAsync().WaitAsync(TimeSpan.FromSeconds(15)); Equal(0, child.ExitCode);
                    var status = CorpusCampaignControl.Status(root, f.Module, plan.Id, hash);
                    Equal(command == "STOP" ? "STOPPED" : "PAUSED", status.State);
                    Equal((long)expectedPosts, status.SchemaValid); Equal(0L, status.Approved);
                    Equal(expectedPosts, File.ReadAllLines(Path.Combine(root, "mock-posts.txt")).Length);
                }
                finally { if (!child.HasExited) { child.Kill(true); await child.WaitForExitAsync(); } }
            }
        });
        await TestAsync("001 uncertain generation is counted conservatively and never reissued on resume", async () =>
        {
            using var f = new Fixture(); using var workspace = new WorkspaceStore(Path.Combine(f.Root, "worker"), f.Module);
            var (plan, hash) = CampaignWorkerPlan(workspace, 2); using var handler = new CampaignMockHandler(failPost: true);
            using var worker = new CorpusCampaignWorker(workspace, _ => new HttpClient(handler, false));
            var first = await worker.RunAsync(plan.Id, hash, 2, CancellationToken.None);
            Equal("PAUSED_INDETERMINATE", first.State); Equal(1L, first.Issued); Equal(0L, first.SchemaValid);
            var resumed = await worker.RunAsync(plan.Id, hash, 2, CancellationToken.None, resume: true);
            Equal("PAUSED_INDETERMINATE", resumed.State); Equal(1, handler.Posts); Equal(1L, resumed.Attempts);
        });
        await TestAsync("001 worker does not substitute a loaded model-key alias for its frozen instance", async () =>
        {
            using var f = new Fixture(); using var workspace = new WorkspaceStore(Path.Combine(f.Root, "worker"), f.Module);
            var (plan, hash) = CampaignWorkerPlan(workspace, 1); using var handler = new CampaignMockHandler(wrongInstance: true);
            using var worker = new CorpusCampaignWorker(workspace, _ => new HttpClient(handler, false));
            var status = await worker.RunAsync(plan.Id, hash, 1, CancellationToken.None);
            Equal(0, handler.Posts); Equal("PAUSED_MODEL_OFFLINE", status.State); Equal(0L, status.SchemaValid);
        });
        await TestAsync("001 production launcher preflight rejects a linked process log directory without launching", async () =>
        {
            using var f = new Fixture(); var root = Path.Combine(f.Root, "launcher workspace"); CorpusCampaignPlan plan; string hash;
            using (var workspace = new WorkspaceStore(root, f.Module)) (plan, hash) = CampaignWorkerPlan(workspace, 1);
            var script = Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "../../../../../scripts/Start-CorpusCampaign.ps1"));
            async Task<(int ExitCode, string Output)> Check()
            {
                var info = new ProcessStartInfo("powershell.exe") { UseShellExecute = false, CreateNoWindow = true, RedirectStandardOutput = true, RedirectStandardError = true };
                foreach (var arg in new[] { "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script, "-CampaignId", plan.Id, "-PlanHash", hash, "-Workspace", root, "-Source", f.Module, "-CheckOnly" }) info.ArgumentList.Add(arg);
                using var process = Process.Start(info)!; var output = process.StandardOutput.ReadToEndAsync(); var error = process.StandardError.ReadToEndAsync();
                await process.WaitForExitAsync().WaitAsync(TimeSpan.FromSeconds(15)); return (process.ExitCode, await output + await error);
            }
            var valid = await Check(); if (valid.ExitCode != 0) throw new InvalidOperationException("Launcher preflight: " + valid.Output);
            var logs = Path.Combine(root, "labs/prompt-lab/campaigns", plan.Id, "processes"); True(!Directory.Exists(logs));
            var outside = Path.Combine(f.Root, "outside logs"); Directory.CreateDirectory(outside);
            var junction = new ProcessStartInfo("cmd.exe", $"/c mklink /J \"{logs}\" \"{outside}\"") { UseShellExecute = false, CreateNoWindow = true };
            using (var link = Process.Start(junction)!) { await link.WaitForExitAsync(); Equal(0, link.ExitCode); }
            var linked = await Check(); True(linked.ExitCode != 0 && linked.Output.Contains("LINKED_PROCESS_PATH", StringComparison.Ordinal));
            Equal(0, Directory.GetFileSystemEntries(outside).Length);
        });
    }

    private static Process StartCampaignProcess(string runner, string[] arguments)
    {
        var info = new ProcessStartInfo("dotnet") { UseShellExecute = false, CreateNoWindow = true };
        info.ArgumentList.Add(runner); foreach (var argument in arguments) info.ArgumentList.Add(argument);
        return Process.Start(info) ?? throw new IOException("MOCK_ONLY process did not start.");
    }
    private static async Task CampaignWait(Func<bool> condition)
    {
        var timer = Stopwatch.StartNew();
        while (!condition()) { if (timer.Elapsed > TimeSpan.FromSeconds(15)) throw new IOException("MOCK_ONLY worker condition timeout."); await Task.Delay(30); }
    }
    private static async Task<int> CampaignMockProcess(string[] args)
    {
        if (args.Length != 7) throw new ArgumentException("MOCK_ONLY process arguments.");
        if (args[0] == "--campaign-mock-launch")
        {
            using var process = StartCampaignProcess(typeof(Program).Assembly.Location,
                ["--campaign-mock-worker", .. args.Skip(1)]);
            File.WriteAllText(args[5], process.Id.ToString()); return 0;
        }
        using var workspace = new WorkspaceStore(args[1], args[2]);
        using var handler = new CampaignMockHandler(Path.Combine(args[1], "mock-posts.txt"));
        using var worker = new CorpusCampaignWorker(workspace, _ => new HttpClient(handler, false));
        await worker.RunAsync(args[3], args[4], 10, CancellationToken.None, resume: args[6] == "resume");
        return 0;
    }

    private static (CorpusCampaignPlan Plan, string Hash) CampaignWorkerPlan(WorkspaceStore workspace, int requests)
    {
        var store = new PromptLabStore(workspace); var fixture = CampaignProduction(store); var campaign = new CorpusCampaign(store);
        var plan = campaign.Prepare(new() { ModelId = "instance-1", MaxRequests = requests,
            Quotas = [new("support", fixture.Production.Id, 0, requests)] });
        return (plan, campaign.PlanHash(plan.Id));
    }

    private sealed class CampaignMockHandler(string? durablePosts = null, bool failPost = false, bool wrongInstance = false) : HttpMessageHandler
    {
        public int Posts { get; private set; }
        protected override async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken token)
        {
            token.ThrowIfCancellationRequested(); string body; var media = "application/json";
            if (request.Method == HttpMethod.Get) body = request.RequestUri!.AbsolutePath == "/v1/models" ? "{\"data\":[{\"id\":\"instance-1\"}]}"
                : "{\"models\":[{\"key\":\"gemma-local\",\"type\":\"llm\",\"loaded_instances\":[{\"id\":\"instance-1\",\"config\":{\"context_length\":65536}}]}]}";
            else
            {
                Posts++; media = "text/event-stream"; var ordinal = Posts;
                if (failPost) throw new HttpRequestException("MOCK_ONLY uncertain POST connection lost.");
                if (durablePosts != null)
                {
                    File.AppendAllText(durablePosts, "MOCK_ONLY POST\n"); ordinal = File.ReadAllLines(durablePosts).Length;
                    await Task.Delay(1200, token);
                }
                var content = JsonSerializer.Serialize(new { header = new { schema = "pw-semantic-batch/1", category = "social.friendship.support", mode = "REPLY", act = "social.friendship.support.reply", language = "ru", count = 1 },
                    records = new[] { new { text = "Разберём этот косяк вместе, заход " + ordinal + ".", stance = "SUPPORTIVE", tone = "CALM" } } });
                body = PromptLabCompatSse(content, "stop", true);
            }
            if (wrongInstance && request.Method == HttpMethod.Get && request.RequestUri!.AbsolutePath != "/v1/models")
                body = body.Replace("\"key\":\"gemma-local\"", "\"key\":\"instance-1\"", StringComparison.Ordinal).Replace("\"id\":\"instance-1\"", "\"id\":\"instance-1:2\"", StringComparison.Ordinal);
            return new HttpResponseMessage(HttpStatusCode.OK) { Content = new StringContent(body, Encoding.UTF8, media) };
        }
    }
}
