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
            async Task<(int ExitCode, string Output)> Check(string policyId = "", string approvalHash = "")
            {
                var info = new ProcessStartInfo("powershell.exe") { UseShellExecute = false, CreateNoWindow = true, RedirectStandardOutput = true, RedirectStandardError = true };
                foreach (var arg in new[] { "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script, "-CampaignId", plan.Id, "-PlanHash", hash, "-Workspace", root, "-Source", f.Module, "-CheckOnly" }) info.ArgumentList.Add(arg);
                if (policyId.Length > 0) foreach (var arg in new[] { "-PolicyId", policyId, "-ApprovalHash", approvalHash }) info.ArgumentList.Add(arg);
                using var process = Process.Start(info)!; var output = process.StandardOutput.ReadToEndAsync(); var error = process.StandardError.ReadToEndAsync();
                await process.WaitForExitAsync().WaitAsync(TimeSpan.FromSeconds(15)); return (process.ExitCode, await output + await error);
            }
            var valid = await Check(); if (valid.ExitCode != 0) throw new InvalidOperationException("Launcher preflight: " + valid.Output);
            string proposedId, proposedHash;
            using (var workspace = new WorkspaceStore(root, f.Module))
            {
                var policies = new CorpusCampaignPolicies(new(workspace)); var proposed = policies.Propose(new() { CampaignId = plan.Id, PlanHash = hash });
                proposedId = proposed.Id; proposedHash = policies.PolicyHash(proposed.Id);
            }
            var unapproved = await Check(proposedId, proposedHash); True(unapproved.ExitCode != 0 && unapproved.Output.Contains("Policy check", StringComparison.Ordinal));
            var logs = Path.Combine(root, "labs/prompt-lab/campaigns", plan.Id, "processes"); True(!Directory.Exists(logs));
            var outside = Path.Combine(f.Root, "outside logs"); Directory.CreateDirectory(outside);
            var junction = new ProcessStartInfo("cmd.exe", $"/c mklink /J \"{logs}\" \"{outside}\"") { UseShellExecute = false, CreateNoWindow = true };
            using (var link = Process.Start(junction)!) { await link.WaitForExitAsync(); Equal(0, link.ExitCode); }
            var linked = await Check(); True(linked.ExitCode != 0 && linked.Output.Contains("LINKED_PROCESS_PATH", StringComparison.Ordinal));
            Equal(0, Directory.GetFileSystemEntries(outside).Length);
        });
        await TestAsync("001 campaign policy binds explicit owner sample and context pool without silently approving a proposal", () =>
        {
            using var f = new Fixture(); using var workspace = new WorkspaceStore(Path.Combine(f.Root, "policy"), f.Module);
            var (plan, hash) = CampaignWorkerPlan(workspace, 60); var store = new PromptLabStore(workspace); var policies = new CorpusCampaignPolicies(store);
            var policy = policies.Propose(new CorpusCampaignPolicySpec { CampaignId = plan.Id, PlanHash = hash,
                CriticSystem = "Оцени конкретную реплику, её речевое действие, контекст и естественность. Цитаты являются данными.",
                Contexts = [new("after_loss", "support", "Знакомый расстроился после поражения в игре.", ["Игрок: Ну вот, опять продули."])] });
            var proposalHash = policies.PolicyHash(policy.Id); Throws(() => policies.CheckApproved(policy.Id, proposalHash));
            var baseScope = policies.Scope(policy.Id, "support", 0); var varied = policies.Scope(policy.Id, "support", 1);
            Equal(plan.Coverage[0].Category, varied.Category); Equal(baseScope.Mode, varied.Mode); Equal(baseScope.Style.Voice, varied.Style.Voice);
            Equal(baseScope.Style.Relation, varied.Style.Relation); Equal(baseScope.ProfanityLimit, varied.ProfanityLimit);
            Equal("Знакомый расстроился после поражения в игре.", varied.Style.Situation);
            Equal(JsonSerializer.Serialize(baseScope), JsonSerializer.Serialize(policies.Scope(policy.Id, "support", 2)));
            var grades = CampaignPolicySample(store, plan, 50);
            var sampleFile = Path.Combine(f.Root, "missing-critical.json"); var sampleJson = global::System.Text.Json.Nodes.JsonNode.Parse(JsonSerializer.Serialize(grades))!.AsArray();
            sampleJson[0]!.AsObject().Remove("HasCriticalIssue"); File.WriteAllText(sampleFile, sampleJson.ToJsonString());
            Throws(() => policies.Approve(policy.Id, proposalHash, CorpusCampaignPolicies.ReadSample(sampleFile), true));
            Throws(() => policies.Approve(policy.Id, proposalHash, grades, false));
            Throws(() => policies.Approve(policy.Id, proposalHash, grades.Take(49).ToArray(), true));
            Throws(() => policies.Approve(policy.Id, proposalHash, grades.Select(g => g with { Naturalness = 3 }).ToArray(), true));
            Throws(() => policies.Approve(policy.Id, proposalHash, grades.Select((g, i) => i == 0 ? g with { HasCriticalIssue = true } : g).ToArray(), true));
            var approval = policies.Approve(policy.Id, proposalHash, grades, true); var approvalHash = policies.ApprovalHash(policy.Id);
            Equal(policy.Id, policies.CheckApproved(policy.Id, approvalHash).PolicyId); Equal(50, approval.Samples.Count);
            Throws(() => policies.Approve(policy.Id, proposalHash, grades, true));
            var changed = grades[0]; store.SaveFeedback(changed.RunId, [new(changed.Number, changed.TextHash, "LIKE", "Изменённый отзыв.")], "Новая редакция оценки.");
            Throws(() => policies.CheckApproved(policy.Id, approvalHash));
            Equal(proposalHash, policies.PolicyHash(policy.Id));
            return Task.CompletedTask;
        });
        await TestAsync("001 editorial parser requires every bound candidate and rejects malformed duplicate and score claims", () =>
        {
            var input = new CorpusEditorialBatch(new() { Category = "social.friendship.support", Count = 1, Style = new() { Category = "social.friendship.support" } },
                [new("c1", "Пошли, помогу тебе разобраться.", [], [new("n1", "Пойдём, разберёмся вместе.")])]);
            var valid = JsonSerializer.Serialize(new { records = new[] { new { id = "c1", naturalness = 4, context = 5, character = 4, grammar = 5,
                act = true, profile = true, lore = "NONE", maturity = "GENERAL", profanity = "NONE", duplicate = "NONE", duplicateOf = Array.Empty<string>(), uncertain = false, reason = "Короткая поддержка в ответ на неудачу." } } });
            Equal(1, LmStudioClient.ParseCorpusEditorial(valid, input).Count);
            foreach (var invalid in new[] { "{\"records\":[]}", valid.Replace("\"c1\"", "\"c2\""), valid.Replace("\"naturalness\":4", "\"naturalness\":6"),
                valid.Replace("\"act\":true", "\"act\":\"true\""), valid.Replace("\"duplicateOf\":[]", "\"duplicateOf\":[\"n1\"]"),
                valid.Replace("\"NONE\",\"duplicateOf\":[]", "\"SAME\",\"duplicateOf\":[\"missing\"]"), valid[..^1] })
                Throws(() => LmStudioClient.ParseCorpusEditorial(invalid, input));
            return Task.CompletedTask;
        });
        await TestAsync("001 editorial acceptance does not treat absent TRIGGER profanity metadata as a ban on actual mat", () =>
        {
            var scope = new V4GenerationRequest { Category = "social.banter.rough", Mode = "TRIGGER", Count = 1, ProfanityLimit = "STRONG",
                Style = new() { Category = "social.banter.rough", StyleId = "strong_profanity", Voice = "rough_joker", Relation = "FAMILIAR", Channel = "BOT_PRIVATE", StrongProfanityRequested = true } };
            scope.Validate(PromptProfiles.Default);
            var text = "Подъеби меня за этот бардак, блядь.";
            var line = JsonSerializer.Serialize(new { text });
            var snapshot = new CorpusCampaignDraftSnapshot("run", "hash", "scope", "content", [new(1, text, line, [], false, [])]);
            var verdict = new CorpusEditorialVerdict("c1", 4, 5, 4, 5, true, true, "NONE", "GENERAL", "STRONG", "NONE", [], false, "Игрок просит грубо подколоть его самого.");
            Equal("APPROVED", CorpusCampaignEditorial.Decide("job", "receipt", snapshot, [verdict], scope).Rows[0].State);
            Equal("EDITORIAL_REJECTED", CorpusCampaignEditorial.Decide("job", "receipt", snapshot, [verdict], scope with { Mode = "REPLY" }).Rows[0].State);
            return Task.CompletedTask;
        });
        await TestAsync("001 approved policy drives varied generation editorial receipts and accepted quotas without touching draft mode", async () =>
        {
            using var f = new Fixture(); using var workspace = new WorkspaceStore(Path.Combine(f.Root, "editorial"), f.Module);
            var (plan, hash) = CampaignWorkerPlan(workspace, 3); var (policy, approvalHash) = CampaignApprovedPolicy(workspace, plan, hash);
            using var handler = new CampaignMockHandler(); using var worker = new CorpusCampaignWorker(workspace, _ => new HttpClient(handler, false));
            var status = await worker.RunAsync(plan.Id, hash, 3, CancellationToken.None, policyId: policy.Id, approvalHash: approvalHash);
            Equal(3L, status.Approved); Equal(3L, status.Reviewed); Equal(0L, status.Pending); Equal("TARGET_REACHED", status.State);
            Equal(3, handler.Posts); Equal(3, handler.EditorialPosts); Equal(3L, status.EditorialIssued);
            var savedStore = new PromptLabStore(workspace); var generated = savedStore.ListRuns().First(r => r.ExperimentId != "");
            var reviewJob = savedStore.ReadCampaignEditorial<CorpusEditorialJob>(plan.Id, generated.ExperimentId, policy.Id, "request", "EDITORIAL_REQUEST")!;
            Equal(generated.EffectiveUser, reviewJob.Batch.GenerationUser);
            Equal(generated.EffectiveSystem, reviewJob.Batch.GenerationSystem);
            Equal(3, new PromptLabStore(workspace).ListRuns().Where(r => r.ExperimentId != "").Select(r => r.Scenario!.Style.Situation).Distinct().Count());
            Equal(3L, (await worker.RunAsync(plan.Id, hash, 3, CancellationToken.None, policyId: policy.Id, approvalHash: approvalHash)).Approved);
            Equal(3, handler.Posts); Equal(3, handler.EditorialPosts);
        });
        await TestAsync("001 editorial raw and global approval reservation recover without another model request", async () =>
        {
            foreach (var fault in new[] { "EDITORIAL_SAVED", "APPROVAL_RESERVED" })
            {
                using var f = new Fixture(); using var workspace = new WorkspaceStore(Path.Combine(f.Root, "editorial"), f.Module);
                var (plan, hash) = CampaignWorkerPlan(workspace, 1); var (policy, approvalHash) = CampaignApprovedPolicy(workspace, plan, hash); var crashed = false;
                using var handler = new CampaignMockHandler();
                using (var worker = new CorpusCampaignWorker(workspace, _ => new HttpClient(handler, false), point =>
                    { if (!crashed && point == fault) { crashed = true; throw new IOException("MOCK_ONLY crash " + fault); } }))
                    await ThrowsAsync(() => worker.RunAsync(plan.Id, hash, 1, CancellationToken.None, policyId: policy.Id, approvalHash: approvalHash));
                True(crashed);
                using var recovered = new CorpusCampaignWorker(workspace, _ => new HttpClient(handler, false));
                Equal(1L, (await recovered.RunAsync(plan.Id, hash, 1, CancellationToken.None, policyId: policy.Id, approvalHash: approvalHash)).Approved);
                Equal(1, handler.Posts); Equal(1, handler.EditorialPosts);
            }
        });
        await TestAsync("001 pause and unknown editorial marker stop the next POST without converting pending rows to approval", async () =>
        {
            foreach (var mode in new[] { "PAUSE", "EDITORIAL_ISSUED" })
            {
                using var f = new Fixture(); using var workspace = new WorkspaceStore(Path.Combine(f.Root, "editorial"), f.Module);
                var (plan, hash) = CampaignWorkerPlan(workspace, 1); var (policy, approvalHash) = CampaignApprovedPolicy(workspace, plan, hash);
                using var handler = new CampaignMockHandler();
                using (var worker = new CorpusCampaignWorker(workspace, _ => new HttpClient(handler, false), point =>
                {
                    if (mode == "PAUSE" && point == "GENERATION_SAVED") CorpusCampaignControl.Request(workspace.Root, f.Module, plan.Id, hash, "PAUSE");
                    if (mode == "EDITORIAL_ISSUED" && point == mode) throw new IOException("MOCK_ONLY unknown editorial POST.");
                }))
                {
                    if (mode == "PAUSE")
                    {
                        var paused = await worker.RunAsync(plan.Id, hash, 1, CancellationToken.None, policyId: policy.Id, approvalHash: approvalHash);
                        Equal("PAUSED", paused.State); Equal(0L, paused.Approved); Equal(0, handler.EditorialPosts);
                    }
                    else await ThrowsAsync(() => worker.RunAsync(plan.Id, hash, 1, CancellationToken.None, policyId: policy.Id, approvalHash: approvalHash));
                }
                using var resume = new CorpusCampaignWorker(workspace, _ => new HttpClient(handler, false));
                var result = await resume.RunAsync(plan.Id, hash, 1, CancellationToken.None, resume: true, policyId: policy.Id, approvalHash: approvalHash);
                if (mode == "PAUSE") Equal(1L, result.Approved);
                else { Equal("PAUSED_EDITORIAL_INDETERMINATE", result.State); Equal(0L, result.Approved); Equal(1L, result.Pending); Equal(0, handler.EditorialPosts); }
                Equal(1, handler.Posts);
            }
        });
        await TestAsync("001 global approved-text reservation is idempotent source-bound and atomic on partial failure", () =>
        {
            using var f = new Fixture(); using var workspace = new WorkspaceStore(Path.Combine(f.Root, "claims"), f.Module); var index = new CorpusCandidateIndex(workspace);
            var hash = new string('a', 64); var decision = new string('b', 64); var approval = new string('c', 64);
            var a = new string('1', 32); var b = new string('2', 32); var c = new string('3', 32); var d = new string('4', 32);
            index.Append([new(a, hash, 1, "social.friendship.support", "REPLY", "Пошли, помогу тебе."),
                new(b, hash, 1, "social.friendship.support", "REPLY", "Пошли, помогу тебе."),
                new(c, hash, 1, "social.friendship.support", "REPLY", "Ты пока отдохни, я разберусь."),
                new(d, hash, 1, "social.friendship.support", "REPLY", "Ты пока отдохни, я разберусь.")], CancellationToken.None);
            Equal(1, index.ReserveApprovals(a, hash, decision, approval, [1], CancellationToken.None).Count);
            Equal(1, index.ReserveApprovals(a, hash, decision, approval, [1], CancellationToken.None).Count);
            Equal(0, index.ReserveApprovals(b, hash, decision, approval, [1], CancellationToken.None).Count);
            Throws(() => index.ReserveApprovals(c, hash, decision, approval, [1, 3], CancellationToken.None));
            Equal(1, index.ReserveApprovals(d, hash, decision, approval, [1], CancellationToken.None).Count);
            return Task.CompletedTask;
        });
        await TestAsync("001 campaign export excludes pending requires explicit tail and uses official isolated import build verify", async () =>
        {
            using var f = new Fixture(); using var workspace = new WorkspaceStore(Path.Combine(f.Root, "export"), f.Module);
            var (plan, hash) = CampaignWorkerPlan(workspace, 2); var (policy, approvalHash) = CampaignApprovedPolicy(workspace, plan, hash);
            using var handler = new CampaignMockHandler(uncertainEditorialNumber: 2); using var worker = new CorpusCampaignWorker(workspace, _ => new HttpClient(handler, false));
            var status = await worker.RunAsync(plan.Id, hash, 2, CancellationToken.None, policyId: policy.Id, approvalHash: approvalHash);
            Equal(1L, status.Approved); Equal(1L, status.Pending);
            var export = new CorpusCampaignExport(workspace);
            var wait = export.Export(plan.Id, hash, policy.Id, approvalHash); Equal(0, wait.Records); Equal(1, wait.DeferredTailRows);
            var ready = export.Export(plan.Id, hash, policy.Id, approvalHash, flushTail: true); Equal(1, ready.Records);
            Equal(1, ready.Files.Count); True(ready.Path.StartsWith(workspace.Root, StringComparison.OrdinalIgnoreCase));
            var replay = export.Export(plan.Id, hash, policy.Id, approvalHash, flushTail: true); Equal(ready.Id, replay.Id); Equal(ready.InputHash, replay.InputHash);
            Throws(() => export.Import(ready.Id, "wrong", true)); Throws(() => export.Import(ready.Id, ready.InputHash, false));
            Equal(1, export.Import(ready.Id, ready.InputHash, true).Accepted); Equal(0, export.Import(ready.Id, ready.InputHash, true).Accepted);
            var stage = export.BuildAndVerify(); True(stage.Verification.Success); Equal(1, stage.Build.Records);
            Equal(1L, CorpusCampaignControl.Status(workspace.Root, f.Module, plan.Id, hash).Approved);
            var file = Directory.GetFiles(ready.Path, "*.jsonl").Single(); File.AppendAllText(file, "broken\n");
            Throws(() => export.Import(ready.Id, ready.InputHash, true));
        });
        await TestAsync("001 CLI exposes immutable policy proposal explicit approval and check without inference", async () =>
        {
            using var f = new Fixture(); var root = Path.Combine(f.Root, "CLI policy"); var specPath = Path.Combine(f.Root, "policy.json"); var samplePath = Path.Combine(f.Root, "sample.json");
            using (var workspace = new WorkspaceStore(root, f.Module))
            {
                var (plan, hash) = CampaignWorkerPlan(workspace, 1);
                File.WriteAllText(specPath, JsonSerializer.Serialize(new CorpusCampaignPolicySpec { CampaignId = plan.Id, PlanHash = hash }, WorkspaceStore.JsonOptions));
                File.WriteAllText(samplePath, JsonSerializer.Serialize(CampaignPolicySample(new(workspace), plan, 50), WorkspaceStore.JsonOptions));
            }
            var common = new[] { "--workspace", root, "--source", f.Module };
            var proposed = await CampaignCli(["campaign-policy-propose", specPath, .. common]);
            if (proposed.ExitCode != 0) throw new InvalidOperationException("CLI propose: " + proposed.Output);
            using var proposal = JsonDocument.Parse(proposed.Output); var id = proposal.RootElement.GetProperty("PolicyId").GetString()!; var policyHash = proposal.RootElement.GetProperty("PolicyHash").GetString()!;
            True((await CampaignCli(["campaign-policy-approve", id, "--hash", policyHash, "--sample", samplePath, .. common])).ExitCode != 0);
            var approved = await CampaignCli(["campaign-policy-approve", id, "--hash", policyHash, "--sample", samplePath, "--reviewed", .. common]);
            if (approved.ExitCode != 0) throw new InvalidOperationException("CLI approve: " + approved.Output);
            using var accepted = JsonDocument.Parse(approved.Output); var approvalHash = accepted.RootElement.GetProperty("ApprovalHash").GetString()!;
            Equal(0, (await CampaignCli(["campaign-policy-check", id, "--hash", approvalHash, .. common])).ExitCode);
        });
        await TestAsync("001 quality milestones are immutable and repeated uncertain editorial batches pause production", async () =>
        {
            using var f = new Fixture();
            using (var workspace = new WorkspaceStore(Path.Combine(f.Root, "milestone"), f.Module))
            {
                var (plan, hash) = CampaignWorkerPlan(workspace, 2); var (policy, approvalHash) = CampaignApprovedPolicy(workspace, plan, hash);
                using var handler = new CampaignMockHandler(); using var worker = new CorpusCampaignWorker(workspace, _ => new HttpClient(handler, false));
                await worker.RunAsync(plan.Id, hash, 2, CancellationToken.None, policyId: policy.Id, approvalHash: approvalHash);
                var journal = new CorpusCampaignJournal(workspace, plan.Id, hash);
                var quality = new CorpusCampaignEditorial(workspace, _ => new HttpClient(handler, false), null, qualityInterval: 1);
                True(!quality.CheckQuality(journal, plan, policy, approvalHash));
                var file = Directory.GetFiles(Path.Combine(new PromptLabStore(workspace).Root, "campaigns", plan.Id, "quality-milestones"), "*.json").Single();
                var before = File.ReadAllBytes(file); True(!quality.CheckQuality(journal, plan, policy, approvalHash));
                True(before.SequenceEqual(File.ReadAllBytes(file))); Equal(2, handler.EditorialPosts);
            }
            using (var workspace = new WorkspaceStore(Path.Combine(f.Root, "regression"), f.Module))
            {
                var (plan, hash) = CampaignWorkerPlan(workspace, 10); var (policy, approvalHash) = CampaignApprovedPolicy(workspace, plan, hash);
                using var handler = new CampaignMockHandler(uncertainEditorialNumber: -2); using var worker = new CorpusCampaignWorker(workspace, _ => new HttpClient(handler, false));
                var result = await worker.RunAsync(plan.Id, hash, 10, CancellationToken.None, policyId: policy.Id, approvalHash: approvalHash);
                Equal("PAUSED_QUALITY_REGRESSION", result.State); Equal(8, handler.Posts); Equal(8L, result.Pending); Equal(0L, result.Approved);
            }
        });
        await TestAsync("001 workspace rejects a repository target before writes even when supplied source points elsewhere", () =>
        {
            using var f = new Fixture(); var repository = Path.Combine(f.Root, "host repository"); Directory.CreateDirectory(repository);
            File.WriteAllText(Path.Combine(repository, ".git"), "gitdir: fixture-only-marker\n");
            var root = Path.Combine(repository, "never-created");
            Throws(() => { using var unsafeWorkspace = new WorkspaceStore(root, f.Module); });
            True(!Directory.Exists(root)); return Task.CompletedTask;
        });
        await TestAsync("001 editorial research freezes sources without HTTP recovers receipts and refuses unknown issued markers", async () =>
        {
            using var f = new Fixture(); string runPath, versionPath, versionId;
            using (var source = new WorkspaceStore(Path.Combine(f.Root, "origin"), f.Module))
            {
                var store = new PromptLabStore(source); var fixture = CampaignProduction(store); versionId = fixture.Production.VersionId;
                runPath = Path.GetRelativePath(f.Root, Path.Combine(store.Root, "runs", fixture.Run.Id, "outcome.json"));
                versionPath = Path.GetRelativePath(f.Root, store.VersionPath(versionId));
            }
            var specPath = Path.Combine(f.Root, "spec.json");
            File.WriteAllText(specPath, JsonSerializer.Serialize(new { Schema = "pw-editorial-critic-trial/1", ModelId = "instance-1", Endpoint = "http://127.0.0.1:1234/v1",
                System = "MOCK_ONLY редактор.", Options = new PromptLabApiOptions(), Cases = new[] { new { Id = "support", RunPath = runPath,
                    RunSha256 = FileSha(Path.Combine(f.Root, runPath)), VersionPath = versionPath, VersionSha256 = FileSha(Path.Combine(f.Root, versionPath)),
                    ExpectedAct = new Dictionary<string, bool> { ["c1"] = true }, Records = 1, Category = "social.friendship.support", Mode = "REPLY" } },
                Jobs = new[] { new { Id = "one", CaseId = "support", Reasoning = "off" } }, AnnotationSource = "MOCK_ONLY_NOT_OWNER_GOLD", Note = "Contract fixture." }, WorkspaceStore.JsonOptions));
            using var handler = new CampaignMockHandler(); var root = Path.Combine(f.Root, "unknown");
            string[] Args(string target, string limit) => ["--corpus-editorial-live", target, f.Module, f.Root, "instance-1", limit, specPath];
            Equal(0, await CorpusEditorialLive(Args(root, "0"), _ => new HttpClient(handler, false)));
            var planPath = Path.Combine(root, "plan.json"); var before = File.ReadAllBytes(planPath);
            Equal(0, await CorpusEditorialLive(Args(root, "0"), _ => new HttpClient(handler, false))); True(before.SequenceEqual(File.ReadAllBytes(planPath)));
            Equal(0, handler.Gets); Equal(0, handler.EditorialPosts);
            using (var plan = JsonDocument.Parse(before))
            {
                var data = plan.RootElement.GetProperty("data"); var job = data.GetProperty("Jobs")[0]; var jobId = job.GetProperty("Id").GetString()!;
                var issue = new { JobId = jobId, PlanHash = V4Rules.Hash(JsonSerializer.SerializeToUtf8Bytes(data, PromptProfiles.CompactJson)),
                    Model = "instance-1", Context = 65536, BatchHash = job.GetProperty("BatchHash").GetString(), AtUtc = DateTimeOffset.UtcNow };
                var folder = Path.Combine(root, "jobs", jobId); Directory.CreateDirectory(folder);
                File.WriteAllText(Path.Combine(folder, "issued.json"), JsonSerializer.Serialize(new { sha256 = V4Rules.Hash(JsonSerializer.SerializeToUtf8Bytes(issue, PromptProfiles.CompactJson)), data = issue }, WorkspaceStore.JsonOptions));
            }
            Equal(3, await CorpusEditorialLive(Args(root, "1"), _ => new HttpClient(handler, false))); Equal(0, handler.Gets);
            var completed = Path.Combine(f.Root, "complete"); Equal(0, await CorpusEditorialLive(Args(completed, "1"), _ => new HttpClient(handler, false)));
            Equal(1, handler.EditorialPosts); True(!handler.ExpectedLabelsLeaked);
            Equal(0, await CorpusEditorialLive(Args(completed, "1"), _ => new HttpClient(handler, false))); Equal(1, handler.EditorialPosts);
            File.AppendAllText(Path.Combine(f.Root, runPath), " ");
            await ThrowsAsync(() => CorpusEditorialLive(Args(completed, "0"), _ => new HttpClient(handler, false))); Equal(1, handler.EditorialPosts);
        });
        await TestAsync("001 controls reject a newly repository-owned journal without acquiring workspace lock or changing its bytes", () =>
        {
            using var f = new Fixture(); var parent = Path.Combine(f.Root, "future repository"); var root = Path.Combine(parent, "data");
            using var workspace = new WorkspaceStore(root, f.Module); var (plan, hash) = CampaignWorkerPlan(workspace, 1);
            _ = new CorpusCampaignJournal(workspace, plan.Id, hash);
            var journal = Path.Combine(new PromptLabStore(workspace).Root, "campaigns", plan.Id, "journal.sqlite"); var before = FileSha(journal);
            File.WriteAllText(Path.Combine(parent, ".git"), "fixture-only boundary\n");
            Throws(() => CorpusCampaignControl.Request(root, f.Module, plan.Id, hash, "STOP")); Equal(before, FileSha(journal)); return Task.CompletedTask;
        });
        await TestAsync("001 full editorial preflight rejects prompt overflow before issued and can pause again without a POST", async () =>
        {
            var edge = new CorpusEditorialBatch(
                new() { Category = "social.friendship.support", Count = 1, Style = new() { Category = "social.friendship.support" } },
                [new("c1", "Разберёмся вместе.", [],
                    [new("n1", new string('a', 1000), Context: new string('b', 2000)),
                     new("n2", new string('c', 1000), Context: new string('d', 2000))])],
                new string('x', 12000), new string('y', 12000));
            LmStudioClient.ValidateCorpusEditorialInput(edge);
            Throws(() => LmStudioClient.ComposeCorpusEditorial(edge, new(), new string('z', 8192)));
            using var f = new Fixture(); using var workspace = new WorkspaceStore(Path.Combine(f.Root, "budget"), f.Module);
            var (plan, hash) = CampaignWorkerPlan(workspace, 2); var store = new PromptLabStore(workspace); var policies = new CorpusCampaignPolicies(store);
            var policy = policies.Propose(new() { CampaignId = plan.Id, PlanHash = hash, CriticSystem = new string('x', 8192),
                Contexts = [new("large", "support", new string('я', 1000), Enumerable.Range(0, 4).Select(_ => "Игрок: " + new string('я', 990)).ToArray())] });
            policies.Approve(policy.Id, policies.PolicyHash(policy.Id), CampaignPolicySample(store, plan, 50), true); var approvalHash = policies.ApprovalHash(policy.Id);
            using var handler = new CampaignMockHandler(); using var worker = new CorpusCampaignWorker(workspace, _ => new HttpClient(handler, false));
            var status = await worker.RunAsync(plan.Id, hash, 2, CancellationToken.None, policyId: policy.Id, approvalHash: approvalHash);
            Equal("PAUSED_EDITORIAL_CONTEXT_LIMIT", status.State); Equal(2, handler.Posts); Equal(1, handler.EditorialPosts); Equal(1L, status.EditorialIssued);
            var large = store.ListRuns().Single(r => r.ExperimentId != "" && r.Scenario!.Style.Situation.Length > 0);
            True(!File.Exists(Path.Combine(store.Root, "campaigns", plan.Id, "tasks", large.ExperimentId, "editorial", policy.Id, "issued.json")));
            var resumed = await worker.RunAsync(plan.Id, hash, 2, CancellationToken.None, resume: true, policyId: policy.Id, approvalHash: approvalHash);
            Equal("PAUSED_EDITORIAL_CONTEXT_LIMIT", resumed.State); Equal(2, handler.Posts); Equal(1, handler.EditorialPosts);
        });
    }

    [global::System.Text.Json.Serialization.JsonUnmappedMemberHandling(global::System.Text.Json.Serialization.JsonUnmappedMemberHandling.Disallow)]
    private sealed record EditorialLiveCaseSpec(string Id, string RunPath, string RunSha256, string VersionPath, string VersionSha256,
        Dictionary<string, bool> ExpectedAct, int Records, string Category, string Mode);
    [global::System.Text.Json.Serialization.JsonUnmappedMemberHandling(global::System.Text.Json.Serialization.JsonUnmappedMemberHandling.Disallow)]
    private sealed record EditorialLiveJobSpec(string Id, string CaseId, string Reasoning);
    [global::System.Text.Json.Serialization.JsonUnmappedMemberHandling(global::System.Text.Json.Serialization.JsonUnmappedMemberHandling.Disallow)]
    private sealed record EditorialLiveSpec(string Schema, string ModelId, string Endpoint, string System, PromptLabApiOptions Options,
        EditorialLiveCaseSpec[] Cases, EditorialLiveJobSpec[] Jobs, string AnnotationSource, string Note);
    private sealed record EditorialLiveCase(string Id, string RunHash, string VersionHash, CorpusEditorialBatch Batch);
    private sealed record EditorialLiveJob(string Id, string Label, string CaseId, string Reasoning, string BatchHash);
    private sealed record EditorialLivePlan(EditorialLiveSpec Spec, string SpecHash, string CoreHash, string RunnerHash, EditorialLiveCase[] Cases, EditorialLiveJob[] Jobs);
    private sealed record EditorialLiveIssue(string JobId, string PlanHash, string Model, int Context, string BatchHash, DateTimeOffset AtUtc);
    private sealed record EditorialLiveReceipt(string JobId, string PlanHash, string Model, int Context, string BatchHash, PromptLabResponse Reply);
    private sealed record EditorialLiveOutcome(string State, string Failure, IReadOnlyList<CorpusEditorialVerdict> Verdicts);

    private static async Task<int> CorpusEditorialLive(string[] args, Func<int, HttpClient>? httpFactory = null)
    {
        V4Rules.Require(args.Length == 7 && int.TryParse(args[5], out var count) && count is >= 0 and <= 24, "EDITORIAL_TRIAL_ARGS");
        var limit = int.Parse(args[5]); var root = PathSafety.Canonical(args[1]); var source = PathSafety.Canonical(args[2]); var work = PathSafety.Canonical(args[3]);
        var specBytes = V4Rules.ReadBytes(PathSafety.Canonical(args[6]), 128 * 1024); var specHash = V4Rules.Hash(specBytes);
        var spec = JsonSerializer.Deserialize<EditorialLiveSpec>(specBytes, PromptProfiles.ConfigJson) ?? throw new InvalidDataException("EDITORIAL_TRIAL_SPEC");
        V4Rules.Require(spec.Schema == "pw-editorial-critic-trial/1" && spec.ModelId == args[4] && spec.Cases is { Length: >= 1 and <= 16 }
            && spec.Jobs is { Length: >= 1 and <= 24 } && spec.Options != null && spec.AnnotationSource is { Length: > 0 and <= 200 }
            && !string.IsNullOrWhiteSpace(spec.System) && V4Rules.Utf8.GetByteCount(spec.System) <= 8192, "EDITORIAL_TRIAL_SHAPE");
        spec.Options!.Validate(); new StudioSettings { HighFiveRoot = source, Endpoint = spec.Endpoint, ModelId = spec.ModelId }.Validate();
        bool Label(string value) => value is { Length: >= 1 and <= 64 } && value.All(c => char.IsAsciiLetterOrDigit(c) || c is '_' or '-');
        V4Rules.Require(spec.Cases.All(c => c != null && Label(c.Id) && c.ExpectedAct != null && c.Records is >= 1 and <= 20)
            && spec.Cases.Select(c => c.Id).Distinct().Count() == spec.Cases.Length
            && spec.Jobs.All(j => j != null && Label(j.Id) && spec.Cases.Any(c => c.Id == j.CaseId) && j.Reasoning is "off" or "auto" or "on")
            && spec.Jobs.Select(j => j.Id).Distinct().Count() == spec.Jobs.Length, "EDITORIAL_TRIAL_IDS");
        string Hash<T>(T value) => V4Rules.Hash(JsonSerializer.SerializeToUtf8Bytes(value, PromptProfiles.CompactJson));
        (T Value, string Hash) Lab<T>(string relative, string expectedSha, string kind)
        {
            var path = PathSafety.ResolveRelative(work, relative.Replace('\\', '/')); var bytes = V4Rules.ReadBytes(path, 4 * 1024 * 1024);
            V4Rules.Require(V4Rules.Hash(bytes) == expectedSha, "EDITORIAL_TRIAL_SOURCE_DRIFT");
            using var envelope = JsonDocument.Parse(bytes); var item = envelope.RootElement; V4Rules.JsonKeys(item, ["version", "kind", "sha256", "data"], []);
            var data = JsonSerializer.SerializeToUtf8Bytes(item.GetProperty("data"), PromptProfiles.CompactJson); var hash = V4Rules.Hash(data);
            V4Rules.Require(item.GetProperty("version").GetInt32() == 1 && V4Rules.String(item, "kind") == kind && V4Rules.String(item, "sha256") == hash, "EDITORIAL_TRIAL_LAB_ENVELOPE");
            return (JsonSerializer.Deserialize<T>(data, PromptProfiles.ConfigJson) ?? throw new InvalidDataException("EDITORIAL_TRIAL_LAB_EMPTY"), hash);
        }
        EditorialLiveCase LoadCase(EditorialLiveCaseSpec item)
        {
            var (run, runHash) = Lab<PromptLabRun>(item.RunPath, item.RunSha256, "RUN_OUTCOME");
            var (version, versionHash) = Lab<PromptLabVersion>(item.VersionPath, item.VersionSha256, "VERSION"); var scope = run.Scenario ?? version.Scenario;
            V4Rules.Require(run.State == "COMPLETE" && run.Purpose == "GENERATION" && run.VersionId == version.Id && run.VersionHash == versionHash
                && run.ModelId == spec.ModelId && run.ModelContext == 65536 && run.RegistryHash == V4Registry.Default.Hash
                && scope.Category == item.Category && scope.Mode == item.Mode && scope.Count == item.Records && run.Rows.Count == item.Records, "EDITORIAL_TRIAL_SOURCE_SCOPE");
            var lines = V4CorpusGeneration.ToJsonl(run.Response!.Content, (run.ApiOptions ?? version.Generation).Apply(scope)).Split('\n', StringSplitOptions.RemoveEmptyEntries);
            var candidates = run.Rows.OrderBy(r => r.Number).Select((row, index) =>
            {
                using var wire = JsonDocument.Parse(lines[index + 1]);
                V4Rules.Require(row.Number == index + 1 && row.SchemaValid && V4Rules.String(wire.RootElement, "text") == row.Text, "EDITORIAL_TRIAL_SOURCE_ROW");
                return new CorpusEditorialCandidate("c" + row.Number, row.Text, row.Diagnostics, []);
            }).ToArray();
            V4Rules.Require(item.ExpectedAct.Keys.All(id => candidates.Any(c => c.Id == id)), "EDITORIAL_TRIAL_EXPECTED_IDS");
            var batch = new CorpusEditorialBatch(scope, candidates, run.EffectiveSystem, run.EffectiveUser); LmStudioClient.ValidateCorpusEditorialInput(batch);
            _ = LmStudioClient.ComposeCorpusEditorial(batch, spec.Options, spec.System);
            return new(item.Id, runHash, versionHash, batch);
        }
        var cases = spec.Cases.Select(LoadCase).ToArray();
        using var workspace = new WorkspaceStore(root, source);
        string Local(string name) { workspace.Check(); return PathSafety.ResolveRelative(root, name); }
        void Save<T>(string name, T value)
        {
            var target = Local(name); Directory.CreateDirectory(Path.GetDirectoryName(target)!);
            var data = JsonSerializer.SerializeToElement(value, PromptProfiles.CompactJson);
            var bytes = JsonSerializer.SerializeToUtf8Bytes(new { sha256 = Hash(data), data }, WorkspaceStore.JsonOptions);
            var temporary = Local(name + ".tmp-" + Guid.NewGuid().ToString("N"));
            try { using (var sink = new FileStream(temporary, FileMode.CreateNew, FileAccess.Write, FileShare.None)) { sink.Write(bytes); sink.Flush(true); } workspace.Check(); PathSafety.AssertNoReparsePoints(target); File.Move(temporary, target, false); }
            finally { if (File.Exists(temporary)) { PathSafety.AssertNoReparsePoints(temporary); File.Delete(temporary); } }
        }
        T Read<T>(string name)
        {
            using var envelope = JsonDocument.Parse(V4Rules.ReadBytes(Local(name), 32 * 1024 * 1024)); var item = envelope.RootElement;
            V4Rules.JsonKeys(item, ["sha256", "data"], []); var data = item.GetProperty("data"); V4Rules.Require(Hash(data) == V4Rules.String(item, "sha256"), "EDITORIAL_TRIAL_ARTIFACT_HASH");
            return JsonSerializer.Deserialize<T>(data.GetRawText(), PromptProfiles.ConfigJson) ?? throw new InvalidDataException("EDITORIAL_TRIAL_ARTIFACT_EMPTY");
        }
        EditorialLivePlan plan;
        if (!File.Exists(Local("plan.json")))
        {
            var jobs = spec.Jobs.Select(j => new EditorialLiveJob(Guid.NewGuid().ToString("N"), j.Id, j.CaseId, j.Reasoning, Hash(cases.Single(c => c.Id == j.CaseId).Batch))).ToArray();
            plan = new(spec, specHash, FileSha(typeof(LmStudioClient).Assembly.Location), FileSha(typeof(Program).Assembly.Location), cases, jobs); Save("plan.json", plan);
        }
        else plan = Read<EditorialLivePlan>("plan.json");
        V4Rules.Require(plan.SpecHash == specHash && Hash(plan.Spec) == Hash(spec) && plan.CoreHash == FileSha(typeof(LmStudioClient).Assembly.Location)
            && plan.RunnerHash == FileSha(typeof(Program).Assembly.Location) && Hash(plan.Cases) == Hash(cases) && plan.Jobs.Length == spec.Jobs.Length
            && plan.Jobs.All(j => PromptLabStore.IsId(j.Id)) && plan.Jobs.Select(j => j.Id).Distinct().Count() == plan.Jobs.Length, "EDITORIAL_TRIAL_REVISION_DRIFT");
        for (var i = 0; i < plan.Jobs.Length; i++)
        { var j = plan.Jobs[i]; var original = spec.Jobs[i]; V4Rules.Require(j.Label == original.Id && j.CaseId == original.CaseId && j.Reasoning == original.Reasoning && j.BatchHash == Hash(cases.Single(c => c.Id == j.CaseId).Batch), "EDITORIAL_TRIAL_JOB_DRIFT"); }
        var planHash = Hash(plan);
        EditorialLiveIssue Issue(EditorialLiveJob job)
        {
            var issue = Read<EditorialLiveIssue>("jobs/" + job.Id + "/issued.json");
            V4Rules.Require(issue.JobId == job.Id && issue.PlanHash == planHash && issue.Model == spec.ModelId && issue.Context == 65536
                && issue.BatchHash == job.BatchHash && issue.AtUtc != default, "EDITORIAL_TRIAL_ISSUED_BINDING"); return issue;
        }
        PromptLabResponse Receipt(EditorialLiveJob job)
        {
            _ = Issue(job); var receipt = Read<EditorialLiveReceipt>("jobs/" + job.Id + "/receipt.json");
            V4Rules.Require(receipt.JobId == job.Id && receipt.PlanHash == planHash && receipt.Model == spec.ModelId && receipt.Context == 65536
                && receipt.BatchHash == job.BatchHash && receipt.Reply.EffectiveSystem == spec.System, "EDITORIAL_TRIAL_RECEIPT_BINDING");
            using var request = JsonDocument.Parse(receipt.Reply.Response.RequestJson);
            V4Rules.Require(request.RootElement.GetProperty("model").GetString() == spec.ModelId, "EDITORIAL_TRIAL_REQUEST_MODEL"); return receipt.Reply;
        }
        EditorialLiveOutcome Outcome(PromptLabResponse reply, EditorialLiveJob job)
        {
            if (!reply.TransportComplete || reply.Response.HttpStatus != 200 || reply.Response.Rejection.Length > 0)
                return new(reply.TransportComplete ? "REJECTED" : "INDETERMINATE", reply.Response.Rejection.Length > 0 ? reply.Response.Rejection : "HTTP_OR_TRANSPORT_INCOMPLETE", []);
            try { return new("COMPLETE", "", LmStudioClient.ParseCorpusEditorial(reply.Response.Content, cases.Single(c => c.Id == job.CaseId).Batch)); }
            catch (Exception error) when (error is InvalidDataException or JsonException or InvalidOperationException or FormatException or KeyNotFoundException) { return new("REJECTED", error.GetType().Name, []); }
        }
        // Validate every existing operation before allowing any new request, including later queue members.
        foreach (var job in plan.Jobs)
        {
            var folder = "jobs/" + job.Id + "/"; var issued = File.Exists(Local(folder + "issued.json")); var received = File.Exists(Local(folder + "receipt.json"));
            if (issued) _ = Issue(job);
            if (received)
            {
                var expected = Outcome(Receipt(job), job);
                if (File.Exists(Local(folder + "outcome.json"))) V4Rules.Require(Hash(Read<EditorialLiveOutcome>(folder + "outcome.json")) == Hash(expected), "EDITORIAL_TRIAL_OUTCOME_DRIFT");
                else Save(folder + "outcome.json", expected);
                if (expected.State == "INDETERMINATE") { Console.WriteLine("STOP_INDETERMINATE " + job.Id); return 3; }
            }
            else if (issued || File.Exists(Local(folder + "outcome.json"))) { Console.WriteLine("STOP_INDETERMINATE " + job.Id); return 3; }
        }
        using var cancellation = new CancellationTokenSource(); ConsoleCancelEventHandler cancel = (_, e) => { e.Cancel = true; cancellation.Cancel(); }; Console.CancelKeyPress += cancel;
        var attempts = 0; var stopped = false;
        try
        {
            foreach (var job in plan.Jobs)
            {
                var folder = "jobs/" + job.Id + "/"; if (File.Exists(Local(folder + "outcome.json")) || attempts >= limit) continue;
                V4Rules.Require(Hash(spec.Cases.Select(LoadCase).ToArray()) == Hash(plan.Cases), "EDITORIAL_TRIAL_SOURCE_DRIFT");
                var options = spec.Options with { Reasoning = job.Reasoning }; var key = Environment.GetEnvironmentVariable("LM_STUDIO_API_KEY") ?? "";
                var settings = new StudioSettings { HighFiveRoot = source, Endpoint = spec.Endpoint, ModelId = spec.ModelId, TimeoutSeconds = options.OverallTimeoutSeconds };
                using var overall = CancellationTokenSource.CreateLinkedTokenSource(cancellation.Token); overall.CancelAfter(TimeSpan.FromSeconds(options.OverallTimeoutSeconds));
                using var http = httpFactory?.Invoke(options.ConnectionTimeoutSeconds) ?? LmStudioClient.CreateHttpClient(options.ConnectionTimeoutSeconds); var client = new LmStudioClient(http);
                CorpusLoadedModel? loaded;
                try { loaded = (await client.ProbeCorpusModelsAsync(settings, key, overall.Token)).Loaded.SingleOrDefault(m => m.InstanceId == spec.ModelId && m.ContextLength == 65536); }
                catch (Exception error) when (error is HttpRequestException or LmStudioException or OperationCanceledException or InvalidDataException) { Console.WriteLine("MODEL_OFFLINE_NO_POST " + error.GetType().Name); stopped = true; break; }
                if (loaded == null) { Console.WriteLine("MODEL_OFFLINE_NO_POST exactinstance/context65536"); stopped = true; break; }
                try { LmStudioClient.PreflightCorpusEditorial(settings, key, loaded, cases.Single(c => c.Id == job.CaseId).Batch, options, spec.System); overall.Token.ThrowIfCancellationRequested(); }
                catch (Exception error) when (error is InvalidDataException or OperationCanceledException) { Console.WriteLine("PREFLIGHT_NO_POST " + error.GetType().Name); stopped = true; break; }
                Save(folder + "issued.json", new EditorialLiveIssue(job.Id, planHash, loaded.InstanceId, loaded.ContextLength, job.BatchHash, DateTimeOffset.UtcNow)); attempts++;
                PromptLabResponse reply;
                try
                {
                    reply = await client.SendCorpusEditorialAsync(settings, key, loaded, cases.Single(c => c.Id == job.CaseId).Batch, options, spec.System, overall.Token, new PromptLabConsoleProgress());
                    Save(folder + "receipt.json", new EditorialLiveReceipt(job.Id, planHash, loaded.InstanceId, loaded.ContextLength, job.BatchHash, reply));
                }
                catch (Exception error) { Save(folder + "indeterminate.json", new { Type = error.GetType().Name, AtUtc = DateTimeOffset.UtcNow }); stopped = true; break; }
                var outcome = Outcome(reply, job); Save(folder + "outcome.json", outcome);
                Console.WriteLine("EDITORIAL " + job.Label + " " + outcome.State + " " + job.Id);
                foreach (var verdict in outcome.Verdicts) Console.WriteLine(verdict.Id + " act=" + verdict.Act + " natural=" + verdict.Naturalness + " context=" + verdict.Context + ": " + verdict.Reason);
                if (outcome.State == "INDETERMINATE" || cancellation.IsCancellationRequested || outcome.Failure.Contains("TIMEOUT", StringComparison.Ordinal)) { stopped = true; break; }
            }
        }
        finally { Console.CancelKeyPress -= cancel; }
        var outcomes = plan.Jobs.Where(j => File.Exists(Local("jobs/" + j.Id + "/outcome.json"))).Select(j => new { Job = j,
            Outcome = Read<EditorialLiveOutcome>("jobs/" + j.Id + "/outcome.json"), Usage = Receipt(j).Response.Telemetry }).ToArray();
        Save("summary-" + Guid.NewGuid().ToString("N") + ".json", new { PlanHash = planHash, Outcomes = outcomes, Attempts = attempts, Stopped = stopped, Approved = 0, Note = "RESEARCH_ONLY_NOT_OWNER_GOLD_NOT_NEW_GENERATION" });
        Console.WriteLine($"EDITORIAL_STATE terminal={outcomes.Length}; pending={plan.Jobs.Length - outcomes.Length}; newAttempts={attempts}; approved=0"); return stopped ? 3 : 0;
    }

    private static async Task<(int ExitCode, string Output)> CampaignCli(string[] arguments)
    {
        var runner = Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "../../../../../src/PhantomSemanticStudio.Cli/bin/Release/net10.0/PhantomSemanticStudio.Cli.dll"));
        var info = new ProcessStartInfo("dotnet") { UseShellExecute = false, CreateNoWindow = true, RedirectStandardOutput = true, RedirectStandardError = true };
        info.ArgumentList.Add(runner); foreach (var argument in arguments) info.ArgumentList.Add(argument);
        using var process = Process.Start(info)!; var output = process.StandardOutput.ReadToEndAsync(); var error = process.StandardError.ReadToEndAsync();
        await process.WaitForExitAsync().WaitAsync(TimeSpan.FromSeconds(15)); return (process.ExitCode, await output + await error);
    }

    private static (CorpusCampaignPolicy Policy, string ApprovalHash) CampaignApprovedPolicy(WorkspaceStore workspace, CorpusCampaignPlan plan, string planHash)
    {
        var store = new PromptLabStore(workspace); var policies = new CorpusCampaignPolicies(store);
        var policy = policies.Propose(new() { CampaignId = plan.Id, PlanHash = planHash, CriticSystem = "MOCK_ONLY редактор контракта, не реальная калибровка.",
            Contexts = [new("loss", "support", "Знакомый проиграл в игре.", ["Игрок: Продули."]), new("error", "support", "Знакомый допустил ошибку.", ["Игрок: Я ошибся."])] });
        policies.Approve(policy.Id, policies.PolicyHash(policy.Id), CampaignPolicySample(store, plan, 50), true);
        return (policy, policies.ApprovalHash(policy.Id));
    }

    private static IReadOnlyList<CorpusCampaignSampleGrade> CampaignPolicySample(PromptLabStore store, CorpusCampaignPlan plan, int count)
    {
        var quota = plan.Spec.Quotas[0]; var production = store.GetProduction(quota.ProductionId); var seed = store.GetRun(production.Evidence[0].RunId);
        var grades = new List<CorpusCampaignSampleGrade>();
        for (var i = 0; i < count; i++)
        {
            var run = store.BeginRun(seed.VersionId); store.MarkIssued(run.Id); var text = "Синтетическая строка для contract sample номер " + i + ".";
            var content = JsonSerializer.Serialize(new { header = new { schema = "pw-semantic-batch/1", category = "social.friendship.support", mode = "REPLY", act = "social.friendship.support.reply", language = "ru", count = 1 },
                records = new[] { new { text, stance = "SUPPORTIVE", tone = "CALM" } } });
            store.CompleteRun(run with { State = "COMPLETE", CompletedAtUtc = DateTimeOffset.UtcNow, ModelId = seed.ModelId, ModelContext = seed.ModelContext,
                EffectiveSystem = seed.EffectiveSystem, EffectiveUser = seed.EffectiveUser, Endpoint = seed.Endpoint,
                Response = seed.Response! with { Content = content }, Rows = [new(1, text, TextRules.Hash(text), true, [])] });
            store.SaveFeedback(run.Id, [new(1, TextRules.Hash(text), "LIKE", "MOCK_ONLY contract fixture, не оценка владельца.")], "Только проверка контракта.");
            grades.Add(new(quota.Id, run.Id, 1, TextRules.Hash(text), 5, 5, false, "MOCK_ONLY, не настоящий корпус."));
        }
        return grades;
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

    private sealed class CampaignMockHandler(string? durablePosts = null, bool failPost = false, bool wrongInstance = false, int uncertainEditorialNumber = -1) : HttpMessageHandler
    {
        public int Posts { get; private set; }
        public int EditorialPosts { get; private set; }
        public int Gets { get; private set; }
        public bool ExpectedLabelsLeaked { get; private set; }
        protected override async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken token)
        {
            token.ThrowIfCancellationRequested(); string body; var media = "application/json";
            if (request.Method == HttpMethod.Get) Gets++;
            if (request.Method == HttpMethod.Get) body = request.RequestUri!.AbsolutePath == "/v1/models" ? "{\"data\":[{\"id\":\"instance-1\"}]}"
                : "{\"models\":[{\"key\":\"gemma-local\",\"type\":\"llm\",\"loaded_instances\":[{\"id\":\"instance-1\",\"config\":{\"context_length\":65536}}]}]}";
            else
            {
                var requestText = await request.Content!.ReadAsStringAsync(token);
                if (requestText.Contains("CORPUS_EDITORIAL", StringComparison.Ordinal))
                {
                    ExpectedLabelsLeaked |= requestText.Contains("ExpectedAct", StringComparison.Ordinal) || requestText.Contains("AnnotationSource", StringComparison.Ordinal);
                    EditorialPosts++; using var payload = JsonDocument.Parse(requestText);
                    var input = payload.RootElement.GetProperty("messages")[1].GetProperty("content").GetString()!.Split("\nТочная схема ответа:\n", StringSplitOptions.None)[0];
                    using var context = JsonDocument.Parse(input);
                    var verdicts = context.RootElement.GetProperty("candidates").EnumerateArray().Select(c => new { id = c.GetProperty("Id").GetString(),
                        naturalness = 5, context = 5, character = 5, grammar = 5, act = true, profile = true, lore = "NONE", maturity = "GENERAL", profanity = "NONE",
                        duplicate = "NONE", duplicateOf = Array.Empty<string>(), uncertain = uncertainEditorialNumber == -2 || EditorialPosts == uncertainEditorialNumber, reason = "MOCK_ONLY contract verdict." }).ToArray();
                    return new(HttpStatusCode.OK) { Content = new StringContent(PromptLabCompatSse(JsonSerializer.Serialize(new { records = verdicts }), "stop", true), Encoding.UTF8, "text/event-stream") };
                }
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
