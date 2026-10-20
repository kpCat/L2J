# PSS-009 final

GREEN_LAYOUT_IMPLEMENTATION for the operator's final scope: standard windows and maximized Full HD1920x1080 at100%, default font. All six tabs are normally arranged and content stretches with the window. User explicitly excluded other scale/font testing. No further goal started.

Required initial HEAD/origin/main/remote main and parent: cddf9a65ffc5b052c110b264c4fe3973f8e6a37c. Sole origin https://github.com/kpCat/PhantomSemanticStudio.git; local master preserved. Publication identity is the exact commit containing this report, checked local/remote/public after the authorized commit/push and recorded in the final response (a commit cannot embed its own SHA).

Root cause runtime-proven: five pages lacked an initial1252x712 size. Default200x100 was used during anchoring, producing +1052/+612 errors already before Show. RED32 inaccessible controls; changing only five Size properties first yielded GREEN1/0. See layout-red and UI evidence.

Final fix: explicit early page/content dimensions; per-page Designer-native1x1 AutoSize TableLayoutPanel Dock.Fill with a scalable minimum-sized content Panel Dock.Fill; same root layout for the five separate forms. Original Anchor properties retained, conversation also stretches Right. Native scrolling provides access below the minimum content extent. Existing captions/control names/events preserved. ChatCorpus clear/statistics overlap fixed. Constructors still only InitializeComponent; no runtime builder/resize code/new dependency.

Checks on final code:

- MAIN_TAB_LAYOUT_PASS: all6 first/repeated forward/reverse, normal/maximized/restored, actual recursive Bounds and parent/ancestor geometry, focus/hit/scroll, four library columns, synthetic long editor and growth assertions.
- MODAL_LAYOUT_PASS: StageSelection, DialogueLab (all3 pages), ChatCorpus, PackQuality, V3Proposal; normal/maximized/restored and content growth. New route7 PASS/0 FAIL, exit0.
- Final Release Build-Verify:122 existing console PASS/0 FAIL;0 warnings/0 errors. Fresh no-incremental compile also0/0.
- Legacy STA004/005/006/007/008:4/3/2/7/2 PASS, all0 FAIL, exit0; C# legacy tests unchanged. Four static verifiers updated only exact Designer parent names; action/handler safety checks retained.
- Negative: single missing tabLibrary.Size rejected statically; original pre-fix Main Designer rejected by live route with32 failures after fresh compilation. Final bytes restored in finally with matching SHA. Final Build-Verify and7/0 route rerun after restoration.
- Static Designer/ctor/handler/resx/nesting/scalable-minimum/ownership PASS; UTF-8 PASS; mojibake-маркеры в изменённых файлах проверены — PASS; escaped Cyrillic в изменённых файлах проверены — PASS; exact scope/privacy/package gates PASS35/35.

PHYSICAL_UI_NOT_TESTED; PHYSICAL_DPI_NOT_TESTED; VS_DESIGNER_NOT_TESTED. Native desktop/capture APIs could not initialize due sandbox helper failure; no running VS instance found. Live controls on actual96dpi1920x1080 desktop are measured, but physical screenshot/click and VS open/save/reopen are not claimed. Manual checklist in PSS-009-ui.md. LM NOT_RUN; Java NOT_RUN; no L2J access or writes.

Operator-reported Label.OnPaint Parameter is not valid came from the test process while a Font had been prematurely disposed. Harness lifetime corrected and ThrowException mode set before handles; final route does not change fonts. Continued popup runs were not accepted as GREEN. Raw operator JIT text remains private.

Exact inventory35 files:12 unchanged task documents,6 Designers,2 runner files,5 verifiers,10 reports/evidence files. Core/LM/Semantic Pack/Java/DB/runtime and all private artifacts excluded. No review archive. Incoming user PhantomSemanticStudio.sln remains modified with original SHA2564aa3a62b295b89b435bbad62ea34a67b0eb3447f52a89c927d711138af786442; excluded from staging. Owned scope must be clean after commit; full tree intentionally retains that user dirt.

Git used only in Studio under explicit task authorization. Commands: rev-parse --show-toplevel / HEAD / origin/main / HEAD^; branch --show-current; status --porcelain=v1 -uall; remote -v / get-url origin / get-url --push origin; ls-remote origin refs/heads/main; diff --stat / exact Designer paths / --name-only <base>; ls-files --others --exclude-standard; diff --cached --check / --name-only / --stat (against required base in scope guard); exact add -- <owned inventory>; ordinary commit -m "fix(pss): restore WinForms tab layouts and DPI reachability"; nonforce push origin HEAD:refs/heads/main; show exact commit metadata/inventory after commit. No force/reset/clean/stash/rebase/amend/branch checkout/broad add or Git in L2J.
