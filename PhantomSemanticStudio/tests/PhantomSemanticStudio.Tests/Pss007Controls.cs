using System.Diagnostics;
using PhantomSemanticStudio.Core;

internal static partial class Program
{
    // Test-only, owner-scoped MessageBox commands; no foreground/global keyboard automation.
    [System.Runtime.InteropServices.DllImport("user32.dll")]
    private static extern IntPtr GetLastActivePopup(IntPtr owner);
    [System.Runtime.InteropServices.DllImport("user32.dll", EntryPoint = "SendMessageW")]
    private static extern IntPtr SendMessage(IntPtr window, uint message, UIntPtr command, IntPtr parameter);
    private static int Pss007Controls()
    {
        var studio = Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "../../../../../"));
        var assembly = System.Reflection.Assembly.LoadFrom(Path.Combine(studio, "src/PhantomSemanticStudio.WinForms/bin/Release/net10.0-windows/PhantomSemanticStudio.dll"));
        var type = assembly.GetType("PhantomSemanticStudio.WinForms.DialogueLabForm");
        var application = System.Reflection.Assembly.Load(assembly.GetReferencedAssemblies().Single(a => a.Name == "System.Windows.Forms")).GetType("System.Windows.Forms.Application")!;
        var doEvents = application.GetMethod("DoEvents")!;
        var contextType = System.Reflection.Assembly.Load(assembly.GetReferencedAssemblies().Single(a => a.Name == "System.Windows.Forms"))
            .GetType("System.Windows.Forms.WindowsFormsSynchronizationContext")!;
        // DoEvents is a temporary message loop; keep an explicit STA context across its teardown.
        contextType.GetProperty("AutoInstall")!.SetValue(null, false);
        SynchronizationContext.SetSynchronizationContext((SynchronizationContext)Activator.CreateInstance(contextType)!);
        dynamic Control(dynamic ui, string name) => ui.Controls.Find(name, true)[0];
        void RespondToNotePrompt(dynamic ui, string button, uint choice)
        {
            var timerType = contextType.Assembly.GetType("System.Windows.Forms.Timer")!;
            using var clock = (IDisposable)Activator.CreateInstance(timerType)!; dynamic timer = clock; var prompted = false;
            var owner = (IntPtr)ui.Handle;
            EventHandler respond = (_, _) =>
            {
                var popup = GetLastActivePopup(owner);
                if (popup == IntPtr.Zero || popup == owner) return;
                timer.Stop(); prompted = true; SendMessage(popup, 0x0111, (UIntPtr)choice, IntPtr.Zero);
            };
            timerType.GetEvent("Tick")!.AddEventHandler(clock, respond); timer.Interval = 100; timer.Start();
            Control(ui, button).AccessibilityObject.DoDefaultAction(); timer.Stop(); True(prompted);
        }
        void Pump(dynamic ui)
        {
            var timer = Stopwatch.StartNew();
            do { doEvents.Invoke(null, null); Thread.Sleep(5); }
            while (!(bool)ui.IsDisposed && (bool)Control(ui, "btnCancel").Enabled && timer.Elapsed < TimeSpan.FromSeconds(20));
            if (!(bool)ui.IsDisposed) True(!Control(ui, "btnCancel").Enabled);
            doEvents.Invoke(null, null);
        }
        using var f = new Fixture(); var pack = new PackReader().Load(f.Module);
        Test("PSS-007 A real STA lab roles trace three turns cancel input and layout", () =>
        {
            True(type != null);
            using var disposable = (IDisposable)Activator.CreateInstance(type!)!; dynamic ui = disposable;
            ui.SetPack(pack); ui.Show(); doEvents.Invoke(null, null);
            True(!Control(ui, "chkMentor").Checked); Equal("AUTO", (string)Control(ui, "cmbWorld").Text);
            foreach (var input in new[] { "привет", "неизвестная реплика", "доброго вечера" })
            {
                Control(ui, "txtInput").Text = input; Control(ui, "btnSend").AccessibilityObject.DoDefaultAction(); Pump(ui);
                Console.WriteLine($"SYNTHETIC_A_TURN count={Control(ui, "listTurns").Items.Count}; status={Control(ui, "lblStatus").Text}");
            }
            Equal(3, (int)Control(ui, "listTurns").Items.Count);
            var transcript = (string)Control(ui, "txtTranscript").Text;
            True(transcript.Contains("YOU / ВЫ") && transcript.Contains("PACK / ПАК") && transcript.Contains("Привет!") && transcript.Contains("NO_PACK_MATCH"));
            True(((string)Control(ui, "txtTrace").Text).Contains("NOT_JAVA_RUNTIME_PARITY"));
            Control(ui, "txtInput").Text = "привет"; Control(ui, "btnSend").AccessibilityObject.DoDefaultAction(); Control(ui, "btnCancel").AccessibilityObject.DoDefaultAction(); Pump(ui);
            Equal("привет", (string)Control(ui, "txtInput").Text); Equal(3, (int)Control(ui, "listTurns").Items.Count);
            Control(ui, "btnClear").AccessibilityObject.DoDefaultAction(); Equal(0, (int)Control(ui, "listTurns").Items.Count);
            foreach (var size in new[] { (object)ui.MinimumSize, (object)ui.Size })
            {
                ui.Size = (dynamic)size; doEvents.Invoke(null, null);
                foreach (var name in new[] { "txtTranscript", "txtTrace", "txtInput", "btnSend", "btnClear", "btnCancel" })
                {
                    dynamic c = Control(ui, name);
                    if (!c.Parent.ClientRectangle.Contains(c.Bounds)) throw new AssertionFailure($"Outside parent: {name}; bounds={c.Bounds}; parent={c.Parent.ClientRectangle}");
                }
                if (Control(ui, "txtTranscript").Bounds.IntersectsWith(Control(ui, "txtTrace").Bounds)) throw new AssertionFailure("Transcript overlaps trace");
            }
            ui.Close();
        });
        Test("PSS-007 B STA separate opt-in preview confirmation lesson and private history", () =>
        {
            True(type != null);
            using var ws = new WorkspaceStore(Path.Combine(f.Root, "controls-b"), f.Module);
            var approved = CandidateOf(pack, "Сейчас можно поговорить спокойно."); CandidateReview.Approve(approved, pack, [], "Проверено");
            ws.SaveSession(new SessionState { Candidates = [approved] }); var candidateBytes = System.Text.Json.JsonSerializer.Serialize(approved);
            using var handler = new RecordingHandler(SemanticEnvelope(MentorBody())); using var http = new HttpClient(handler);
            using var disposable = (IDisposable)Activator.CreateInstance(type!)!; dynamic ui = disposable;
            ui.SetPack(pack); ui.SetWorkspace(ws); ui.Show(); doEvents.Invoke(null, null);
            SynchronizationContext.SetSynchronizationContext((SynchronizationContext)Activator.CreateInstance(contextType)!);
            True(ui.Controls.Find("btnAskMentor", true).Length == 1);
            ui.SetModel(new LmStudioClient(http), new StudioSettings { HighFiveRoot = f.Module }, "");
            Control(ui, "txtInput").Text = "босс"; Control(ui, "btnSend").AccessibilityObject.DoDefaultAction(); Pump(ui); Equal(0, handler.Calls);
            Control(ui, "tabsLab").SelectedTab = Control(ui, "tabMentor");
            Control(ui, "chkMentor").Checked = true; Control(ui, "btnMentorPreview").AccessibilityObject.DoDefaultAction(); Pump(ui);
            Control(ui, "btnAskMentor").AccessibilityObject.DoDefaultAction(); Pump(ui); Equal(0, handler.Calls);
            Control(ui, "chkContextReviewed").Checked = true; Control(ui, "btnAskMentor").AccessibilityObject.DoDefaultAction(); Pump(ui); Equal(1, handler.Calls);
            True(((string)Control(ui, "txtAdvice").Text).Contains("MENTOR_ADVISORY"));
            Control(ui, "cmbConfirmWorld").SelectedItem = "REAL"; Control(ui, "txtClarification").Text = "О начальнике на работе.";
            Control(ui, "btnConfirm").AccessibilityObject.DoDefaultAction(); Pump(ui); Equal("REAL", (string)Control(ui, "cmbWorld").Text);
            Control(ui, "txtNote").Text = "Различать рейд и начальника в редакционной теме.";
            Control(ui, "chkScopeReviewed").Checked = true; Control(ui, "btnSaveLesson").AccessibilityObject.DoDefaultAction(); Pump(ui);
            Equal(1, ws.LoadSession().ScopedLessons.Count); Equal(candidateBytes, System.Text.Json.JsonSerializer.Serialize(ws.LoadSession().Candidates.Single()));
            True(!Directory.Exists(Path.Combine(ws.Root, "labs")));
            Control(ui, "btnSaveHistory").AccessibilityObject.DoDefaultAction(); Pump(ui);
            Equal(1, Directory.GetFiles(Path.Combine(ws.Root, "labs"), "*.json").Length); Equal(1, handler.Calls); ui.Close();
        });
        Test("PSS-007 C STA explicit transfer separate consent manual mask suggestion to scoped lesson", () =>
        {
            try
            {
            var corpusType = assembly.GetType("PhantomSemanticStudio.WinForms.ChatCorpusForm", true)!;
            using var ws = new WorkspaceStore(Path.Combine(f.Root, "controls-c"), f.Module); ws.SaveSession(new SessionState());
            var store = new CorpusStore(ws); var metadata = store.Import(CorpusZip(f.Root, ("synthetic.log", CorpusLines(
                "[01.01.22 00:00:01] SHOUT [PlayerA] privet user@example.test", "[01.01.22 00:00:02] PARTY [PlayerB] kak dela"))));
            var dbPath = Path.Combine(ws.Root, "corpora", metadata.Id, "corpus.db"); var hash = TextRules.Hash(File.ReadAllBytes(dbPath));
            using var explorer = (IDisposable)Activator.CreateInstance(corpusType)!; dynamic corpus = explorer;
            corpus.SetWorkspace(ws); SynchronizationContext.SetSynchronizationContext((SynchronizationContext)Activator.CreateInstance(contextType)!); corpus.Show(); Pump(corpus);
            Equal(2, (int)Control(corpus, "listRows").Items.Count);
            True(corpus.Controls.Find("btnTransfer", true).Length == 1); corpus.SetTransferMode(true);
            Control(corpus, "listRows").Items[0].Checked = true; Control(corpus, "listRows").Items[1].Checked = true;
            Control(corpus, "btnTransfer").AccessibilityObject.DoDefaultAction(); Pump(corpus); Equal(0, (int)corpus.SelectedExcerpts.Count);
            Control(corpus, "chkTransferReviewed").Checked = true; Control(corpus, "btnTransfer").AccessibilityObject.DoDefaultAction(); Pump(corpus);
            var snippets = (IReadOnlyList<SanitizedCorpusExcerpt>)corpus.SelectedExcerpts; Equal(2, snippets.Count);
            Console.WriteLine("SYNTHETIC_C_STAGE transfer=2");
            using var disposable = (IDisposable)Activator.CreateInstance(type!)!; dynamic ui = disposable;
            ui.SetWorkspace(ws); ui.SetPack(pack); SynchronizationContext.SetSynchronizationContext((SynchronizationContext)Activator.CreateInstance(contextType)!); ui.Show();
            True(ui.Controls.Find("btnTranslate", true).Length == 1); ui.SetExcerpts(snippets);
            var reviewed = snippets.Select((s, i) => CorpusDialogueBridge.Review(s, i == 0 ? "privet [маска редактора]" : "kak dela", "UNKNOWN")).ToArray();
            var modelLab = new DialogueLabSession(pack); var request = modelLab.BeginCorpusTransform(reviewed, true, true, "RESTORE_RU_TRANSLIT");
            using var handler = new RecordingHandler(SemanticEnvelope(CorpusAdviceBody(request))); using var http = new HttpClient(handler);
            ui.SetModel(new LmStudioClient(http), new StudioSettings { HighFiveRoot = f.Module }, "");
            Control(ui, "tabsLab").SelectedTab = Control(ui, "tabCorpus");
            dynamic list = Control(ui, "listExcerpts"); list.Items[0].Checked = true; list.Items[1].Checked = true; list.Items[0].Selected = true; doEvents.Invoke(null, null);
            Control(ui, "txtShareText").Text = "privet [маска редактора]";
            Control(ui, "btnTranslate").AccessibilityObject.DoDefaultAction(); Pump(ui); Equal(0, handler.Calls);
            Control(ui, "chkPiiReviewed").Checked = true; Control(ui, "chkShareCorpus").Checked = true;
            Control(ui, "btnTranslate").AccessibilityObject.DoDefaultAction(); Pump(ui); Equal(1, handler.Calls);
            Console.WriteLine($"SYNTHETIC_C_STAGE calls={handler.Calls}; status={Control(ui, "lblStatus").Text}");
            True(!Control(ui, "chkShareCorpus").Checked && !Control(ui, "chkPiiReviewed").Checked && !Control(ui, "chkMentor").Checked);
            Equal(2, (int)Control(ui, "listSuggestions").Items.Count); Control(ui, "listSuggestions").SelectedIndex = 0;
            Control(ui, "txtNote").Text = "Несохранённая заметка корпуса.";
            RespondToNotePrompt(ui, "btnUseSuggestion", 2); Equal("Несохранённая заметка корпуса.", (string)Control(ui, "txtNote").Text);
            RespondToNotePrompt(ui, "btnUseSuggestion", 7);
            Control(ui, "chkScopeReviewed").Checked = true; Control(ui, "btnSaveLesson").AccessibilityObject.DoDefaultAction(); Pump(ui);
            Equal(1, ws.LoadSession().ScopedLessons.Count); Equal(0, ws.LoadSession().Candidates.Count);
            Equal(hash, TextRules.Hash(File.ReadAllBytes(dbPath))); True(!handler.Payload!.Contains("user@example.test") && !handler.Payload.Contains("PlayerA")); ui.Close();
            }
            catch (Exception ex) { Console.WriteLine("SYNTHETIC_C_STACK=" + ex.StackTrace); throw; }
        });
        Test("PSS-007 review scope consent expires after band or register changes", () =>
        {
            using var ws = new WorkspaceStore(Path.Combine(f.Root, "review-scope"), f.Module); ws.SaveSession(new SessionState());
            using var disposable = (IDisposable)Activator.CreateInstance(type!)!; dynamic ui = disposable;
            ui.SetPack(pack); ui.SetWorkspace(ws); ui.Show(); doEvents.Invoke(null, null);
            Control(ui, "tabsLab").SelectedTab = Control(ui, "tabMentor");
            Control(ui, "txtNote").Text = "Проверить тему перед сохранением.";
            foreach (var name in new[] { "cmbBand", "cmbRegister" })
            {
                Control(ui, "chkScopeReviewed").Checked = true; Control(ui, name).SelectedIndex = 1;
                Control(ui, "btnSaveLesson").AccessibilityObject.DoDefaultAction(); Pump(ui);
                Equal(0, ws.LoadSession().ScopedLessons.Count); True(!Control(ui, "chkScopeReviewed").Checked);
            }
            Control(ui, "chkScopeReviewed").Checked = true; Control(ui, "btnSaveLesson").AccessibilityObject.DoDefaultAction(); Pump(ui);
            Equal(1, ws.LoadSession().ScopedLessons.Count); ui.Close();
        });
        Test("PSS-007 review revoking mentor and corpus consent during preflight prevents POST", () =>
        {
            using var ws = new WorkspaceStore(Path.Combine(f.Root, "review-consent"), f.Module);
            var store = new CorpusStore(ws); var metadata = store.Import(CorpusZip(f.Root, ("synthetic.log", CorpusLines("[01.01.22 00:00:01] SHOUT [PlayerA] privet"))));
            var snippets = CorpusDialogueBridge.Detach(metadata, store.Query(metadata.Id, new CorpusQuery()).Rows, true);
            using var handler = new RecordingHandler(SemanticEnvelope(MentorBody())); using var http = new HttpClient(handler);
            using var disposable = (IDisposable)Activator.CreateInstance(type!)!; dynamic ui = disposable;
            ui.SetPack(pack); ui.SetWorkspace(ws); ui.SetModel(new LmStudioClient(http), new StudioSettings { HighFiveRoot = f.Module }, "");
            ui.Show(); doEvents.Invoke(null, null);
            Control(ui, "txtInput").Text = "босс"; Control(ui, "btnSend").AccessibilityObject.DoDefaultAction(); Pump(ui);
            Control(ui, "tabsLab").SelectedTab = Control(ui, "tabMentor"); Control(ui, "chkMentor").Checked = true;
            Control(ui, "btnMentorPreview").AccessibilityObject.DoDefaultAction(); Pump(ui);
            Control(ui, "chkContextReviewed").Checked = true; Control(ui, "btnAskMentor").AccessibilityObject.DoDefaultAction();
            Control(ui, "chkContextReviewed").Checked = false; Pump(ui);
            ui.SetExcerpts(snippets); Control(ui, "listExcerpts").Items[0].Checked = true;
            foreach (var name in new[] { "chkPiiReviewed", "chkShareCorpus" })
            {
                Control(ui, "chkPiiReviewed").Checked = true; Control(ui, "chkShareCorpus").Checked = true;
                Control(ui, "btnTranslate").AccessibilityObject.DoDefaultAction(); Control(ui, name).Checked = false; Pump(ui);
            }
            Equal(0, handler.Calls); ui.Close();
        });
        Test("PSS-007 review suggestion insertion Cancel preserves dirty editor note", () =>
        {
            using var ws = new WorkspaceStore(Path.Combine(f.Root, "review-note"), f.Module); ws.SaveSession(new SessionState());
            using var handler = new RecordingHandler(SemanticEnvelope(MentorBody())); using var http = new HttpClient(handler);
            using var disposable = (IDisposable)Activator.CreateInstance(type!)!; dynamic ui = disposable;
            ui.SetPack(pack); ui.SetWorkspace(ws); ui.SetModel(new LmStudioClient(http), new StudioSettings { HighFiveRoot = f.Module }, ""); ui.Show(); doEvents.Invoke(null, null);
            Control(ui, "txtInput").Text = "босс"; Control(ui, "btnSend").AccessibilityObject.DoDefaultAction(); Pump(ui);
            Control(ui, "tabsLab").SelectedTab = Control(ui, "tabMentor"); Control(ui, "chkMentor").Checked = true;
            Control(ui, "btnMentorPreview").AccessibilityObject.DoDefaultAction(); Pump(ui);
            Control(ui, "chkContextReviewed").Checked = true; Control(ui, "btnAskMentor").AccessibilityObject.DoDefaultAction(); Pump(ui);
            Control(ui, "txtNote").Text = "Моя несохранённая редакционная заметка.";
            RespondToNotePrompt(ui, "btnUseMentorNote", 2); Equal("Моя несохранённая редакционная заметка.", (string)Control(ui, "txtNote").Text);
            RespondToNotePrompt(ui, "btnUseMentorNote", 7); Equal(0, ws.LoadSession().ScopedLessons.Count);
            Control(ui, "txtNote").Text = "Сохранить именно эту редакционную заметку."; Control(ui, "chkScopeReviewed").Checked = true;
            RespondToNotePrompt(ui, "btnUseMentorNote", 6); Pump(ui);
            Equal("Сохранить именно эту редакционную заметку.", ws.LoadSession().ScopedLessons.Single().Text);
            True(((string)Control(ui, "txtNote").Text).Contains("Различать контекст"));
            Control(ui, "txtNote").Clear(); ui.Close();
        });
        Test("PSS-007 review clear selection during corpus preflight revokes transfer", () =>
        {
            var corpusType = assembly.GetType("PhantomSemanticStudio.WinForms.ChatCorpusForm", true)!;
            using var ws = new WorkspaceStore(Path.Combine(f.Root, "review-transfer"), f.Module);
            new CorpusStore(ws).Import(CorpusZip(f.Root, ("synthetic.log", CorpusLines("[01.01.22 00:00:01] SHOUT [PlayerA] privet"))));
            using var explorer = (IDisposable)Activator.CreateInstance(corpusType)!; dynamic corpus = explorer;
            corpus.SetWorkspace(ws); corpus.SetTransferMode(true); corpus.Show(); Pump(corpus);
            Control(corpus, "listRows").Items[0].Checked = true; Control(corpus, "chkTransferReviewed").Checked = true;
            Control(corpus, "btnTransfer").AccessibilityObject.DoDefaultAction(); Control(corpus, "btnClearSelection").AccessibilityObject.DoDefaultAction(); Pump(corpus);
            Equal(0, (int)corpus.SelectedExcerpts.Count); True(!(bool)corpus.IsDisposed); corpus.Close();
        });
        Console.WriteLine($"CONTROL_CONTRACT_RESULT: {passed} PASS; {failed} FAIL; physical UI/DPI/VS NOT_TESTED");
        return failed == 0 ? 0 : 1;
    }
}
