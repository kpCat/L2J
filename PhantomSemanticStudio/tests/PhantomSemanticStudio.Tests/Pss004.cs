using System.Text.Json;
using PhantomSemanticStudio.Core;

internal static partial class Program
{
    private static void Pss004()
    {
        Test("PSS-004 empty selection never defaults to 25 500 or 5000 approvals", () =>
        {
            foreach (var count in new[] { 25, 500, 5000 }) SelectionFails(SelectionPeers(count), []);
        });
        Test("PSS-004 exact Ordinal subset includes last approval and preserves inputs", () =>
        {
            var peers = SelectionPeers(100);
            var before = JsonSerializer.Serialize(peers, WorkspaceStore.JsonOptions);
            var requested = new List<string> { peers[99].Id, peers[1].Id, peers[24].Id };
            var ids = StageBatchSelection.SelectExactIds(peers, requested);
            Equal("00000000000000000000000000000002,00000000000000000000000000000019,00000000000000000000000000000064", string.Join(",", ids));
            requested.Clear(); Equal(3, ids.Count);
            if (ids is IList<string> mutable) Throws(() => mutable[0] = "changed");
            Equal(before, JsonSerializer.Serialize(peers, WorkspaceStore.JsonOptions));
            var casePeers = SelectionPeers(2); casePeers[0].Id = "A"; casePeers[1].Id = "a";
            foreach (var c in casePeers) Seal(c);
            Equal("A,a", string.Join(",", StageBatchSelection.SelectExactIds(casePeers, ["a", "A"])));
            SelectionFails(casePeers, ["a", "a"]);
        });
        Test("PSS-004 exactly 20 accepted and 21 rejected without truncation", () =>
        {
            var peers = SelectionPeers(25);
            Equal(20, StageBatchSelection.SelectExactIds(peers, peers.Take(20).Select(c => c.Id)).Count);
            SelectionFails(peers, peers.Take(21).Select(c => c.Id));
        });
        Test("PSS-004 malformed IDs and duplicate peers fail the entire selection", () =>
        {
            var peers = SelectionPeers(25); var valid = peers[0].Id;
            foreach (var bad in new[] { "missing", "", " ", null, valid }) SelectionFails(peers, [valid, bad!]);
            SelectionFails(peers, null!); SelectionFails(null!, [valid]);
            var duplicate = peers[24].Copy(); peers.Add(duplicate);
            SelectionFails(peers, [valid]);
            peers.RemoveAt(25); peers.Add(null!); SelectionFails(peers, [valid]);
        });
        Test("PSS-004 stale fields DRAFT and REJECTED block all selected IDs", () =>
        {
            foreach (var change in new Action<Candidate>[]
            {
                c => c.Text += "!", c => c.ReviewNote += "!", c => c.Gender = "FEMALE",
                c => c.ReviewedAtUtc = null, c => c.SourceFingerprint = "drift",
                c => c.Status = "DRAFT", c => c.Status = "REJECTED"
            })
            {
                var peers = SelectionPeers(25); change(peers[24]);
                SelectionFails(peers, [peers[0].Id, peers[24].Id]);
                Equal(1, StageBatchSelection.SelectExactIds(peers, [peers[0].Id]).Count);
                True(CandidateReview.IsCurrent(peers[0]));
            }
        });
        Test("PSS-004 real stage selects only 2 of 25 approvals XML receipt source and session preserved", () =>
        {
            using var f = StageFixture(); using var store = new WorkspaceStore(Path.Combine(f.Root, "studio"), f.Module);
            var pack = new PackReader().Load(f.Module); var peers = SelectionPeers(25, pack);
            store.SaveSession(new SessionState { Candidates = peers, Lessons = ["Сохранённое замечание"] });
            var before = File.ReadAllBytes(Path.Combine(store.Root, "session.json"));
            var ids = StageBatchSelection.SelectExactIds(peers, [peers[24].Id, peers[23].Id]);
            var stage = new IsolatedPackStager().Create(store, pack, peers, ids, true, true);
            var staged = new PackReader().Load(stage.ModuleRoot);
            Equal("STAGED_UNVALIDATED", stage.Status);
            Equal(3, staged.Entries.Count(e => e.Kind == "PATTERN")); Equal(3, staged.Entries.Count(e => e.Kind == "TEMPLATE"));
            var added = staged.Entries.Where(e => e.Id.StartsWith("pss.", StringComparison.Ordinal)).ToArray();
            Equal(2, added.Length);
            True(added.Any(e => e.Id == "pss.p.00000000000000000000000000000019" && e.Text == peers[24].Text));
            True(added.Any(e => e.Id == "pss.t.00000000000000000000000000000018" && e.Text == peers[23].Text));
            using var receipt = JsonDocument.Parse(File.ReadAllBytes(Path.Combine(stage.Root, "receipt.json")));
            Equal("NOT_RUN", receipt.RootElement.GetProperty("JavaStatus").GetString());
            Equal(1, receipt.RootElement.GetProperty("AddedPatterns").GetInt32());
            Equal(1, receipt.RootElement.GetProperty("AddedTemplates").GetInt32());
            Equal("00000000000000000000000000000018,00000000000000000000000000000019",
                string.Join(",", receipt.RootElement.GetProperty("Candidates").EnumerateArray().Select(c => c.GetProperty("Id").GetString())));
            True(pack.Files.SequenceEqual(new PackReader().Load(f.Module).Files));
            True(before.SequenceEqual(File.ReadAllBytes(Path.Combine(store.Root, "session.json"))));
            True(peers.All(CandidateReview.IsCurrent));
        });
        Test("PSS-004 consent cancellation and invalid subset leave zero stages and unchanged session", () =>
        {
            using var f = StageFixture(); using var store = new WorkspaceStore(Path.Combine(f.Root, "studio"), f.Module);
            var pack = new PackReader().Load(f.Module); var peers = SelectionPeers(25, pack);
            store.SaveSession(new SessionState { Candidates = peers });
            var before = File.ReadAllBytes(Path.Combine(store.Root, "session.json"));
            var ids = StageBatchSelection.SelectExactIds(peers, [peers[24].Id]);
            BlockStage("CONSENT", store, pack, peers, selection: false, ids: ids.ToArray());
            BlockStage("CONSENT", store, pack, peers, editorial: false, ids: ids.ToArray());
            using var cancel = new CancellationTokenSource(); cancel.Cancel();
            try { new IsolatedPackStager().Create(store, pack, peers, ids, true, true, cancel.Token); throw new AssertionFailure("Cancellation expected"); }
            catch (OperationCanceledException) { }
            SelectionFails(peers, []); SelectionFails(peers, peers.Take(21).Select(c => c.Id));
            NoStages(store);
            True(pack.Files.SequenceEqual(new PackReader().Load(f.Module).Files));
            True(before.SequenceEqual(File.ReadAllBytes(Path.Combine(store.Root, "session.json"))));
            True(peers.All(CandidateReview.IsCurrent));
        });
        Test("PSS-004 stager rechecks approval source drift peers and incompatible selected item", () =>
        {
            using var f = StageFixture(); using var store = new WorkspaceStore(Path.Combine(f.Root, "studio"), f.Module);
            var pack = new PackReader().Load(f.Module); var peers = SelectionPeers(25, pack);
            var ids = StageBatchSelection.SelectExactIds(peers, [peers[24].Id]);
            var changed = peers.Select(c => c.Copy()).ToList(); changed[24].Text += "!";
            BlockStage("APPROVAL", store, pack, changed, ids: ids.ToArray());
            changed = peers.Select(c => c.Copy()).ToList(); changed[0].Text = changed[24].Text;
            BlockStage("EXACT_DUPLICATE", store, pack, changed, ids: ids.ToArray());
            changed = peers.Select(c => c.Copy()).ToList(); changed[24].Gender = "FEMALE"; Seal(changed[24]);
            var genderIds = StageBatchSelection.SelectExactIds(changed, ids);
            BlockStage("GENDER", store, pack, changed, ids: genderIds.ToArray());
            File.AppendAllText(f.Segment, "\n"); BlockStage("SOURCE_DRIFT", store, pack, peers, ids: ids.ToArray());
            True(peers.All(CandidateReview.IsCurrent));
        });
    }

    private static void SelectionFails(IReadOnlyList<Candidate> peers, IEnumerable<string> ids)
    {
        try { StageBatchSelection.SelectExactIds(peers, ids); throw new AssertionFailure("Invalid selection must fail closed"); }
        catch (InvalidDataException) { }
    }

    // Run via dotnet exec --runtimeconfig <Studio WindowsDesktop runtimeconfig> <tests.dll>.
    // Public control operations exercise actual ItemCheck/TextChanged/Click handlers on STA.
    // This is a control contract test, not a human click/DPI/VS Designer claim.
    private static int Pss004Controls()
    {
        var studio = Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "../../../../../"));
        var assembly = System.Reflection.Assembly.LoadFrom(Path.Combine(studio, "src/PhantomSemanticStudio.WinForms/bin/Release/net10.0-windows/PhantomSemanticStudio.dll"));
        var formType = assembly.GetType("PhantomSemanticStudio.WinForms.StageSelectionForm", throwOnError: true)!;
        dynamic NewForm() => Activator.CreateInstance(formType)!;
        dynamic Control(dynamic form, string name) => form.Controls.Find(name, true)[0];
        Test("PSS-004 actual controls empty default and bounded 5000 approval view", () =>
        {
            foreach (var count in new[] { 25, 500, 5000 })
            {
                using var form = (IDisposable)NewForm(); dynamic ui = form;
                ui.SetCandidates(SelectionPeers(count), "synthetic-selection-baseline"); ui.Show();
                Equal(0, ((IReadOnlyList<string>)ui.SelectedIds).Count);
                True(!Control(ui, "btnCreate").Enabled);
                Equal(Math.Min(count, 500), (int)Control(ui, "listCandidates").Items.Count);
                Equal("", (string)Control(ui, "txtSelected").Text);
                ui.Close();
            }
        });
        Test("PSS-004 actual ItemCheck keeps hidden IDs full preview and checkbox state through filters", () =>
        {
            using var form = (IDisposable)NewForm(); dynamic ui = form;
            var peers = SelectionPeers(25); ui.SetCandidates(peers, "synthetic-selection-baseline"); ui.Show();
            dynamic list = Control(ui, "listCandidates"); dynamic filter = Control(ui, "txtFilter"); dynamic selected = Control(ui, "txtSelected");
            list.Items[0].Checked = true; list.Items[24].Checked = true;
            True(((string)selected.Text).Contains(peers[0].Id) && ((string)selected.Text).Contains(peers[24].Id));
            True(((string)selected.Text).Contains(peers[0].Text) && ((string)selected.Text).Contains(peers[24].Text));
            filter.Text = "выбора 25"; Equal(1, (int)list.Items.Count); True(list.Items[0].Checked);
            True(((string)selected.Text).Contains(peers[0].Id));
            list.Items[0].Checked = false; True(!((string)selected.Text).Contains(peers[24].Id));
            filter.Text = ""; True(list.Items[0].Checked); True(!list.Items[24].Checked);
            Equal("Выбрано: 1 / 20", (string)Control(ui, "lblCount").Text);
            // Caller mutation cannot alter the copied content/review shown by this dialog.
            peers[0].Text = "Caller drift"; True(!((string)selected.Text).Contains("Caller drift"));
            ui.Close(); Equal(0, ((IReadOnlyList<string>)ui.SelectedIds).Count); Equal("Cancel", ui.DialogResult.ToString());
        });
        Test("PSS-004 actual controls stale baseline and 21 checks block without silent unchecking", () =>
        {
            using var form = (IDisposable)NewForm(); dynamic ui = form;
            var peers = SelectionPeers(25); var stale = peers[24].Copy(); stale.Id = new string('d', 32); Seal(stale); stale.Text += "!"; peers.Add(stale);
            var baseline = peers[24].Copy(); baseline.Id = new string('e', 32); baseline.SourceFingerprint = "old"; Seal(baseline); peers.Add(baseline);
            ui.SetCandidates(peers, "synthetic-selection-baseline"); ui.Show();
            dynamic list = Control(ui, "listCandidates"); dynamic create = Control(ui, "btnCreate");
            list.Items[0].Checked = true; True(create.Enabled);
            list.Items[25].Checked = true; True(list.Items[25].Checked); True(!create.Enabled);
            True(((string)Control(ui, "txtSelected").Text).Contains("СТАРОЕ ОДОБРЕНИЕ"));
            list.Items[25].Checked = false; list.Items[26].Checked = true; True(!create.Enabled);
            True(((string)Control(ui, "txtError").Text).Contains("baseline"));
            ui.SetCandidates(SelectionPeers(25), "synthetic-selection-baseline"); Equal(0, (int)list.CheckedItems.Count);
            for (var i = 0; i < 20; i++) list.Items[i].Checked = true;
            True(create.Enabled); list.Items[20].Checked = true;
            Equal(21, (int)list.CheckedItems.Count); True(!create.Enabled);
            list.Items[20].Checked = false; True(create.Enabled);
            ui.Close();
        });
        Test("PSS-004 actual confirmation full exact texts default No and explicit OK returns detached IDs", () =>
        {
            var peers = SelectionPeers(25); var before = JsonSerializer.Serialize(peers, WorkspaceStore.JsonOptions);
            using (var form = (IDisposable)NewForm())
            {
                dynamic ui = form; ui.SetCandidates(peers, "synthetic-selection-baseline");
                ui.ConfirmExactSelection(new[] { peers[24].Id, peers[0].Id }); ui.Show();
                True(!Control(ui, "txtFilter").Enabled && !Control(ui, "listCandidates").Enabled);
                True(ReferenceEquals((object)ui.AcceptButton, (object)Control(ui, "btnCancel")));
                True(((string)ui.SelectedSummary).Contains(peers[24].Text) && ((string)ui.SelectedSummary).Contains(peers[0].Text));
                Control(ui, "btnCancel").PerformClick(); Equal("Cancel", ui.DialogResult.ToString());
                Equal(0, ((IReadOnlyList<string>)ui.SelectedIds).Count);
            }
            using (var form = (IDisposable)NewForm())
            {
                dynamic ui = form; ui.SetCandidates(peers, "synthetic-selection-baseline");
                ui.ConfirmExactSelection(new[] { peers[24].Id, peers[0].Id }); ui.Show();
                Control(ui, "btnCreate").PerformClick(); Equal("OK", ui.DialogResult.ToString());
                var ids = (IReadOnlyList<string>)ui.SelectedIds;
                Equal("00000000000000000000000000000001,00000000000000000000000000000019", string.Join(",", ids));
            }
            Equal(before, JsonSerializer.Serialize(peers, WorkspaceStore.JsonOptions));
        });
        Console.WriteLine($"CONTROL_CONTRACT_RESULT: {passed} PASS; {failed} FAIL; interactive UI/DPI/VS NOT_TESTED");
        return failed == 0 ? 0 : 1;
    }

    private static List<Candidate> SelectionPeers(int count, PackSnapshot? pack = null)
    {
        pack ??= new PackSnapshot { Fingerprint = "synthetic-selection-baseline" };
        var peers = Enumerable.Range(1, count).Select(i =>
        {
            var c = CandidateOf(pack, "Искусственная реплика для выбора " + i);
            c.Id = i.ToString("x32"); c.Kind = i % 2 == 1 ? "PATTERN" : "TEMPLATE";
            Seal(c); return c;
        }).ToList();
        return peers;
    }

    // Synthetic UI inputs only; never imports or writes actual L2J or the user's workspace.
    private static int Pss004UiFixture(string[] args)
    {
        if (args.Length != 2) throw new ArgumentException("--pss-004-ui-fixture <new root under Studio artifacts>");
        var studio = Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "../../../../../"));
        var root = PathSafety.Canonical(args[1]);
        if (!PathSafety.IsWithin(root, Path.Combine(studio, "artifacts/PSS-004")) || Directory.Exists(root))
            throw new InvalidDataException("UI fixture requires a new directory under Studio artifacts/PSS-004.");
        PathSafety.AssertDisjoint(@"C:\Users\ZBook\L2J_Mobius", root);
        // Keep this owned synthetic temp fixture alive for the external GUI process.
        // A source inside Studio's Git root would correctly protect the whole repository.
        var fixture = StageFixture(); var source = fixture.Module;
        var pack = new PackReader().Load(source); var peers = SelectionPeers(25, pack);
        var stale = CandidateOf(pack, "Изменённая после одобрения fixture"); stale.Id = new string('d', 32); Seal(stale); stale.Text += "!"; peers.Add(stale);
        var baseline = CandidateOf(pack, "Fixture другого baseline"); baseline.Id = new string('e', 32); baseline.SourceFingerprint = "old-baseline"; Seal(baseline); peers.Add(baseline);
        using var store = new WorkspaceStore(Path.Combine(root, "workspace"), source);
        store.SaveSettings(new StudioSettings { HighFiveRoot = source });
        store.SaveSession(new SessionState { Candidates = peers });
        Console.WriteLine("SYNTHETIC_UI_FIXTURE: " + root); Console.WriteLine("SYNTHETIC_SOURCE: " + source);
        Console.WriteLine("APPROVED=27; current baseline=25; stale=1; old baseline=1; no actual L2J import");
        return 0;
    }
}
