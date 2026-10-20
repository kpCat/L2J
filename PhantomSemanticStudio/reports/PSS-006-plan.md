# PSS-006 — implementation plan

Goal: один checkpoint A/B/C по docs/tasks/PSS-006/GOAL.md; STOP после PSS-006.
Spec: весь пакет docs/tasks/PSS-006, особенно DESIGN, IMPLEMENTATION, ACCEPTANCE, CHAT_FORMAT_AND_PRIVACY, SAFETY_AND_GIT.
Execution: основная Codex, inline, RED/GREEN, один итоговый manual review gate. Готовый пользовательский пакет задаёт дизайн и разрешает выполнение; дополнительные design/plan approvals не вводятся.

## Read-first

Прочитаны root AGENTS, README_RU, DESIGN_RU, SOURCE_AUDIT_RU, BASELINE_VERIFICATION; PSS-005 final/ui/review, PSS-004/003 final; весь пакет PSS-006; Core SemanticDuplicateScout, WorkspaceStore, LmStudioClient, Models, PackReader, PathSafety, TextRules; MainForm/Designer, StageSelectionForm/Designer/resx; Program/Pss005, Build-Verify и Verify-Designer; csproj трёх проектов и Directory.Build.props.
Ancestor AGENTS и code-map/pattern-файлы не найдены; повторный поиск не нужен.
Аналоги: async RunAsync/Task.Run MainForm, atomic/path guards WorkspaceStore, explicit selection StageSelectionForm, partial console runner и настоящие STA public-control routes Pss005.
Required base = local HEAD = проверенный remote main: f92431aa5594a62210916e94bc6b617ec51f4bdc. Own root/оба URL origin подтверждены. Dirt: только десять входящих task files, сохранить без изменения. Первоначальный sandbox ls-remote не имел сети; разрешённый повтор exit 0.

## Constraints and file budget

.NET 10, C#14, WinForms standard static controls, constructors только InitializeComponent, шесть вкладок. L2J READ_ONLY; без Java/model lifecycle/tools/retries, approvals/XML/session contract без изменений. Нет review ZIP. Все corpus bytes private, synthetic fixtures only в Git.
Bounded exception >10 файлов прямо разрешён IMPLEMENTATION: узкие owners Core Models/LmStudioClient/SemanticDuplicateScout и новые CorpusStore/ChatCorpusImporter/CorpusLanguageTriage/CorpusQueries; MainForm/Designer, новые ChatCorpusForm/Designer/resx и csproj nesting; Core csproj + lock files для единственного pinned Microsoft.Data.Sqlite; tests Program/Pss006/Pss006Controls; Verify-PSS006, README, .gitignore и PSS-006 reports/task files. Другие owners frozen.
Непроверено до реализации: restore SQLite, executable RED/GREEN, large memory/cancel/query, live LM, physical UI/DPI, VS round-trip.

## A

- [ ] Настоящий STA regression synthetic source: saved selection latency, два быстрых переключения, cancel/close/error, unchanged session/approval. RED до изменения MainForm.
- [ ] Separate cancellation + selection version; immutable worker snapshots, no controls off UI thread. Pending/error/cancel STALE; current UI identity/edit/peers/source проверены до repaint. Один scout, overload evidence equality по вычисленному shortlist.
- [ ] Fake HTTP RED/GREEN: diagnostic categories envelope JSON/shape, finish, refusal, tools, empty, content JSON/schema/ref IDs. BAD_RESPONSE сохраняется, exception без inner/raw. Cold LM независим.

## B

- [ ] Contracts + fail-closed scaffolds, privacy/parser/query tests RED. ZipArchive readonly streaming без extraction. Reject traversal/duplicates/symlink/encryption/bomb; checked central+actual budgets, strict UTF8 and bounded lines. Private channels skip до insert; identities не хранятся. Aggregate metadata только ordinal entries/bytes/hash.
- [ ] Отдельная SQLite immutable corpus под workspace/corpora: own GUID partial -> finished, transaction, read-only fresh connections, integrity/version/hash checks, exclusive import lock, old DB unaffected. Dedup fingerprint normalized text+channel; original public text immutable, normalized отдельно; spam/PII/triage flags, no translation.
- [ ] Parameterized search/date/channel/language/length/duplicate/noise filters, page 100–200, deterministic sort, cancellation. Unknown channel пусто; corrupt DB fail closed. Synthetic small, negative archive controls, reopen, cancel/retry и >=100k large smoke.

SQLite выбран вместо нового BCL индекса: disk-backed dedup/sort/filter/counts дают проверяемый bounded RAM и transaction. Одна direct dependency Microsoft.Data.Sqlite, exact version 10.0.12 по NuGet; transitive native SQLite закрепляется lock files. Cache 8 MiB, temp_store MEMORY (приватные SQL temp не попадают в системный TEMP), page <=200, import byte/line limits из spec. Fingerprint/time indexes и dedup без NOT IN уменьшают временные наборы; 100k/реальный миллион проверяются отдельно. API проверены в официальных Microsoft docs: https://learn.microsoft.com/en-us/dotnet/standard/data/sqlite/bulk-insert и database-errors; package https://www.nuget.org/packages/Microsoft.Data.Sqlite/10.0.12. Restore является gate, не предположением.

## C and closure

- [ ] Static form + MainForm button, no seventh tab. RED static/control workflow, затем GREEN. OpenFileDialog, progress/cancel, committed listing, search/paging, explicit <=20 public scrubbed excerpts SOURCE_MATERIAL_ONLY. Cross-corpus selection clear, no LM/candidate/approval/session writes.
- [ ] Existing 88 + focused tests, Release Build-Verify, old/new Designer, separate encoding scans, frozen-owner/security/exact inventory checks. Review whole change with fresh reviewer; fix substantive findings RED/GREEN.
- [ ] Independent live/DPI/VS/actual import/Java statuses, final/UI reports. Exact add/commit, nonforce origin HEAD:refs/heads/main только после remote base recheck; SHA proof и own tree status. No PSS-007.

Review focus: actual streaming budgets vs declared ZIP metadata; private text absent physical DB bytes; cancellation before publish; late saved-evidence repaint; corrupt index and provenance selection. Reports never contain actual raw chat.

## Execution ledger

Preflight A -> C: selection cancellation отдельная от import operation; общий UI может открыть corpus только после ready.
Preflight B -> C: immutable corpus ID/row refs, query <=200, selected <=20; no SessionState objects. Решения соответствуют spec.

2026-10-08 closure: A HTTP 0/1→1/0; actual STA latency 277→76 ms, latest/cancel/close/error unchanged-session PASS. B contract 0/4→7/0; CRC 6/1→GREEN; C missing-form 1/1→2/0 real controls. Fresh reviewer 0 Critical/2 Important/2 Minor: native SQL cancellation вместо no-op Cancel и bounded central directory до Entries реализованы по 7/2→9/0. Minor safe whitelist UI errors и temp MEMORY rationale исправлены; [review rulings](PSS-006-review.md).

Final Release Build-Verify 98/0, 0 warning/error, exit0; locked restore exit0; existing PSS005 controls3/0. Actual readonly ZIP import1,050,356lines/685,498public/336,473private SKIPPED, final46.660s, sampled47,366,144privatebytes; archiveSHA unchanged; newprocess read-back100/100/TELL0. Modelcalls0; lms ps empty → BLOCKED_LM, no lifecycle commands. JavaNOT_RUN; L2J content not read, protected path attributes only, source writes0.

Static Designer/security/package/two encoding gates PASS. Physical UI/DPI/VS NOT_TESTED: targetable synthetic window, two Computer Use capture timeouts; existing unrelated VS workspace не менялся. Manual gate REQUIRED, HANDOFF_REVIEW_PENDING. Exact69paths:24source/test/script/README/ignore,10unchangedtaskfiles,35reports/evidence. Final nonforce Git closure отдельно подтверждается handoff+localignoredreceipt; STOP PSS006, PSS007 не начат.
