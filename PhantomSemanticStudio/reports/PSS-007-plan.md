# PSS-007 Implementation Plan

> Исполнение в текущем диалоге: superpowers:executing-plans, test-driven-development и verification-before-completion. Повторное согласование отменено прямым запросом пользователя. Spec: docs/tasks/PSS-007/GOAL.md и весь неизменяемый пакет PSS-007.

**Goal:** отдельная лаборатория разговора с каталогом, необязательный наставник и ограниченный публичный корпусный мост.
**Architecture:** чистая Core-сессия оборачивает существующий PackPreview, сохраняет provenance и атомарно принимает актуальный результат. Новые partial методы LmStudioClient используют прежний loopback транспорт и strict envelope. Designer форма получает зависимости после конструктора, читает источник в worker и сохраняет только по явному действию.
**Tech Stack:** .NET 10, C# 14, WinForms, System.Text.Json, существующий Microsoft.Data.Sqlite 10.0.12. Новых пакетов нет.

Ниже сохранён исходный pre-implementation checklist. Фактическое исполнение и итоговые статусы находятся в Execution ledger и final report; незакрытая публикация в tracked тексте — только precommit-состояние.

## Read-first / scope

Прочитаны AGENTS.md (выше project root файлов нет), README_RU.md, docs/DESIGN_RU.md, SOURCE_AUDIT_RU.md, reports/BASELINE_VERIFICATION.md, PSS-006-final.md, весь PSS-007 и csproj/Directory.Build.props. Code-map/pattern-файлы в inventory не найдены; повторный поиск не нужен. Аналоги: PackPreview/PackReader; LmStudioClient exact fields/SendAsync; ChatCorpusForm и StageSelectionForm Designer/selection; MainForm scoped lessons и versioned async; Pss006Controls/console runner.

Required initial HEAD и remote main: 8a583e5bc872d3f61b933f217baa985af5425e5f. Fetch/push origin: https://github.com/kpCat/PhantomSemanticStudio.git. Dirty before: только docs/tasks/PSS-007/, сохранить bytes. Sandbox network ls-remote отказал в соединении; разрешённый outside-sandbox read-only ls-remote подтвердил base.

Bounded exception (>10 файлов): единая явно заказанная задача A/B/C; только exact inventory reports/PSS-007-owned-files.txt. Никакой смены стека, Java/публикации XML, auto approve/learning, model lifecycle/retry, private corpus в Git. Frozen: reader, TextRules, PathSafety, WorkspaceStore, CandidateValidator, staging/export, старые reports/tasks, solution/props и dependencies. csproj меняется только для nesting новой формы.

## Review focus

- Отмена между worker и render: input сохраняется, repeat queue не изменяется до commit.
- Новый turn/mode/source: старый mentor/result не принимается; pending clarification одна.
- Public preview всё ещё может содержать PII: отдельный просмотр, ручное маскирование и независимый consent перед каждым corpus POST.
- Placeholder/functional/memory reply: отсутствие Java ownership отмечено явно; подставленный текст не выдаётся за исходный каталог.
- Save/Discard/Cancel при note edit, Clear/Close: сохранение требует точного существующего scope и source; default история эфемерна.

## A: Pack-only

- [ ] Добавить Pss007.cs / --pss-007-a с executable RED для отсутствующей сессии, matched IDs/literal source reply/no match/unsupported, scope/history/200 bound/cancel/source drift.
- [ ] DialogueLab.cs: WorldScope, immutable trace/proposal, PrepareTurn + CommitTurn, copied snapshot/repeat inspector, revision guards; максимум 200 turn, input<=1024, match<=256.
- [ ] Минимально PackPreview: copy repeat queue для transactional prepare, сохранять matched functional IDs без reply. Java semantics не расширять.
- [ ] DialogueLabForm.cs/.Designer.cs/.resx, constructor only InitializeComponent; MainForm один entry в tabChat; WinForms csproj nesting. GREEN A + STA public control три turn и cancel.

## B: Mentor and notes

- [ ] Executable RED --pss-007-b: OFF zero POST, strict question/JSON/negative envelope; source/turn/mode binding, max20, pending/confirm, explicit lesson validation.
- [ ] DialogueMentor.cs: typed advice/tickets, bounded editable context<=10 turns/8KiB, text safety/UTF8, session consent/call budget; LmStudioClient.Dialogue.cs reuses SendAsync/CompletionText/ExactFields. Existing class становится partial; старые методы неизменны.
- [ ] Form отдельные MENTOR/EDITOR_NOTE, explicit request (opt-in ambiguous opportunity показана), confirm world, editable note и scoped save. Default OFF. DialogueLabStore.cs explicit private atomic version1 bounded save under labs, existing workspace lock/paths/source guards; без API token и автосохранения.
- [ ] GREEN B, fake HTTP strict/cancel/error/stale; SessionState v1 и approvals unchanged кроме явного scoped lesson append.

## C: Corpus bridge

- [ ] Executable RED --pss-007-c: detached public preview, 1–20 transfer consent, <=3 separately reviewed masked LM texts, strict source keyed results.
- [ ] CorpusDialogueBridge.cs immutable sanitized references, hash/channel/category/manual override and request consent; reuse CorpusLanguageTriage scrub. No Original at outbound boundary.
- [ ] ChatCorpusForm static transfer button + independent review checkbox; return snapshots only after explicit action, cancel returns empty. Lab shows full selected preview, manual category, exact editable model preview and per-request consent; explicit translation button; AI note only.
- [ ] GREEN C, immutable DB/source/session/approval stamps, fake outbound privacy, stale/cancel, preserved old corpus controls.

## Final verification/publication

- [ ] Release scripts/Build-Verify.ps1, >=98 old checks + targeted A/B/C; separate STA --pss-007-controls and relevant old controls.
- [ ] scripts/Verify-PSS007.ps1: all Designer/ctors/handlers/resx/nesting, strict UTF8; separate mojibake and escaped Cyrillic passes; task SHA/bytes, exact allowlist/frozen/privacy inventory and staged guard.
- [ ] Reports PSS-007-final.md / ui.md / owned-files.txt + sanitized logs. LIVE_LM only actual operator evidence; no lifecycle; physical UI/DPI/VS separately NOT_TESTED if unavailable. Java NOT_RUN, NOT_SERVER_READY.
- [ ] Final fresh-context whole-change review; address critical/important with focused RED/GREEN. No review ZIP.
- [ ] Exact add/ordinary commit/non-force push origin HEAD:refs/heads/main after base recheck; local/remote/public SHA proof and clean inventory. STOP before PSS-008.

## Execution ledger

Pre-flight shared interfaces A→B: turn/revision/source ticket; B→C: shared strict transport and max20 session call budget; C→UI: sanitized immutable selected references. No conflicting ownership. Ruling: work in requested own root on existing master — user explicitly requires this base and HEAD:refs/heads/main publication; local branch retained, no worktree/branch mutation commands.
Ruling: save plan/ledger in requested report, private synthetic outputs under ignored artifacts/PSS-007; skill scratch scripts would add unrelated workflow files. No additional approval gates, per explicit user instruction.
При initial pass не были проверены new implementation/tests, live LM, physical UI/DPI/VS, optional actual corpus read. Финальные статусы ниже; initial note не proof.

Task A: Core RED0/3→GREEN3/0; STA RED0/1→GREEN1/0. Synthetic source/approval/session unchanged. Layout RED caught missing initial TabPage.Size; fixed in Designer. Task B: Core RED0/3→GREEN3/0; STA RED1/1→GREEN2/0. AB ordinary regression104/0 (98 prior groups). Task C: Core RED0/3→GREEN3/0; STA RED2/1→GREEN3/0. Full source/corpus/session hashes preserved; transfer2, no-consent0 POST, consent1 POST, explicit scoped lesson only.
Ruling: STA harness uses explicit WinFormsSynchronizationContext with AutoInstall=false — DoEvents tears down its temporary message loop; existing public Accessibility action invokes Click directly. Initial harness crash was not a product PASS. Microsoft primary source: https://github.com/dotnet/winforms/blob/main/src/System.Windows.Forms/System/Windows/Forms/WindowsFormsSynchronizationContext.cs and ButtonBase.ButtonBaseAccessibleObject.cs. Costs if wrong: unreliable automated UI evidence, therefore physical UI/DPI/VS remain separate gates.
Ruling: mentor requests are manual after reviewed context preview, including ambiguous turns — avoids silent text transfer; opportunity is shown on UNKNOWN. No automatic per-turn POST. Draft candidate is optional; implemented explicit validated scoped lesson route. Private history save is export-only, no automatic restore/migration.
Final review read-only на fresh gpt-6-astra HIGH: Critical0/Important4; one fix pass, STA RED3/4→GREEN7/0, own clarification atomicity RED2/1→GREEN3/0. ResolveNote reuse и current permission/version guards, без новых production layers. Final Build-Verify107/0, warning/error0; STA7/0; old004/005/006 controls4/0,3/0,2/0. Pss006Controls Pump добавляет IsDisposed guard по ближайшему Pss007Controls аналогу; первый IndexOutOfRange не объявлен продуктовым GREEN, точная исходная строка не доказана диагностическим повтором.
Final static/UTF8/two Cyrillic/privacy/task SHA/exact scope PASS66 paths. lms ps=[]: BLOCKED_LM, model lifecycle0/POST0. Actual corpus NOT_RUN; physical UI/DPI/VS NOT_TESTED, один operator gate REQUIRED. Java NOT_RUN/NOT_SERVER_READY. Ruling: finishing-a-development-branch integration menu отменён прямым пользовательским выбором exact non-force origin/main; не делаем branch/PR/worktree cleanup. Actual SHA/remote/public/clean proof возвращается после push в handoff/ignored checkpoint. STOP008.
