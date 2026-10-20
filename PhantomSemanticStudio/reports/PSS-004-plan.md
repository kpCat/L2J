# PSS-004 — план и журнал выполнения

**Goal:** ручной выбор ровно 1–20 кандидатов из любого числа APPROVED для одного изолированного stage.
**Spec:** `docs/tasks/PSS-004/GOAL.md`, `ARCHITECTURE.md`, `TEST_CASES.md`, `ACCEPTANCE.md`, `SAFETY_AND_GIT.md`.
**Execution:** основная Codex, inline по superpowers:executing-plans; самостоятельное выполнение уже разрешено task package. Один обычный итоговый commit, non-force push, затем STOP.

## Read-only pass

Required base = local HEAD = remote main: `edc41128270c14578b9edf748a3c8bc0c6196623`. Local branch `master`; root и fetch/push origin проверены, только `https://github.com/kpCat/PhantomSemanticStudio.git`. Initial dirt: девять неизменённых файлов `docs/tasks/PSS-004/`. Manifest восьми payload файлов проверен; отдельные initial SHA/bytes всех девяти и неприкосновенного production scope сохранены в игнорируемом `artifacts/PSS-004/readonly-stamps.json`. Sandbox ls-remote exit 1 (сеть); разрешённый повтор exit 0.

Прочитаны root AGENTS, README_RU, DESIGN_RU, SOURCE_AUDIT_RU, BASELINE_VERIFICATION, PSS-003 final/inventory/GREEN/Java summary, весь PSS-004 package; Models, CandidateValidator (включая CandidateReview), WorkspaceStore, PathSafety, IsolatedPackStager, MainForm и Designer/resx, три csproj, Directory.Build.props, Program/Pss003, Build-Verify/Verify-Designer и локальные verifiers. Ancestor AGENTS, code-map/pattern-файлы не найдены. Отдельного CandidateReview.cs нет: класс определён в CandidateValidator.cs.

Корень дефекта: `StageXml_Click` получает ВСЕ APPROVED и затем блокирует >20. Existing Stager уже принимает exact IDs; его менять не требуется. Локальные аналоги: `Candidate.Copy` + current approval; explicit MainForm Designer; default-No confirmations/RunAsync; StageFixture/console partial runner; раздельные text/package guards Verify-PSS003.

## Ограничения и touched scope

.NET 10, C#14, standard WinForms, без NuGet. Constructors только InitializeComponent. Все static controls/layout/events в Designer, runtime binding отдельно. Нет изменений Stager, exporter, validator, storage, PathSafety, Java operator, MainForm Designer/resx, solution и props. L2J READ_ONLY; actual source route не нужен для синтетического subset regression. Java/LM не запускаются. STAGED_UNVALIDATED / NOT_RUN и SEMANTIC_NOT_CHECKED сохраняются.

Точный production/test/script scope (9 файлов; README отдельно):

- новый `src/PhantomSemanticStudio.Core/StageBatchSelection.cs`;
- новые `src/PhantomSemanticStudio.WinForms/StageSelectionForm.cs`, `.Designer.cs`, `.resx`;
- `src/PhantomSemanticStudio.WinForms/MainForm.cs` — только StageXml_Click;
- `src/PhantomSemanticStudio.WinForms/PhantomSemanticStudio.WinForms.csproj` — nesting новой формы;
- новый `tests/PhantomSemanticStudio.Tests/Pss004.cs`;
- `tests/PhantomSemanticStudio.Tests/Program.cs` — focused route и ordinary suite;
- новый `scripts/Verify-PSS004.ps1`;
- `README_RU.md` — operator selection и checkpoint.

Отдельно reports/evidence и девять исходных task files. Не вводить другую artifact family или subsystem. Final exact inventory в `reports/PSS-004-owned-files.txt`.

## Шаги и проверяемые интерфейсы

- [ ] 1. RED selection + static flow. `StageBatchSelection.SelectExactIds(IReadOnlyList<Candidate>, IEnumerable<string>) -> IReadOnlyList<string>`, `MaxItems=20`: empty из 25/500/5000; exact subset с последним ID; 20/21; null/empty/unknown/duplicate IDs; duplicate peers; stale fields; DRAFT/REJECTED; detached result/input preservation. API scaffold fail-closed NotImplemented до GREEN, negative tests требуют InvalidDataException. Static verifier на старом StageXml_Click обязан отклонить implicit all-selection.
- [ ] 2. GREEN Core. Fail whole batch, Ordinal uniqueness/order, read-only detached ID snapshot, без файлов и Candidate references. Real StageFixture с 25 APPROVED: 2 selected в XML и receipt; source stamps/session bytes равны; confirmations/cancel, peer duplicates, stale selected и drift блокируют весь stage. Focused command: `dotnet run --project tests/PhantomSemanticStudio.Tests -c Release -- --pss-004`.
- [ ] 3. Modal form/integration. `SetCandidates(IReadOnlyList<Candidate>, string sourceFingerprint)` копирует peers; initial empty HashSet IDs, scrollable APPROVED rows с cached review/baseline flags, поиск ID/text/kind/topic/act. Полный selected preview всегда независим от фильтра. Stale/baseline/capacity ошибки явно блокируют продолжение. `SelectedIds` — detached read-only snapshot после Core validation, DialogResult OK только после проверки. После modal exact IDs повторно проверяются перед Stager. Два прежних default-No confirmations; полный ID/text summary в modal и export log/подтверждении.
- [ ] 4. Verification. Новый static Designer/flow/text/package/readonly guard; existing Verify-Designer; один final Release Build-Verify (включая старые 69 tests). Доступный Windows UI через computer-use на synthetic workspace, без live source/LM/Java. VS round-trip и физический 100%/150% DPI — только наблюдённые статусы; иначе NOT_TESTED.
- [ ] 5. Fresh read-only code review, exact inventory/reports. `git diff --check`, exact index allowlist, remote still base, обычный commit и `git push origin HEAD:refs/heads/main`; проверить SHA equality и clean status. Final SHA evidence после commit в ignored artifacts и handoff. STOP PSS-004.

## Review focus

1. ItemCheck использует NewValue, rebind guard не меняет checked IDs; filter не теряет скрытые выбранные.
2. Baseline mismatch может иметь current content approval: отдельная блокировка в modal, authoritative повтор в Stager.
3. 21-й check не снимает существующие 20, invalid выбранный не пропускается молча.
4. Большие review notes/тексты не теряются в selected preview и точном подтверждении; 5000 rows без массового CandidateValidator.
5. Cancel/крестик/No в обоих confirmations: no finished stage/session/approval mutation; ResolvePendingEdit остаётся прежним.

Pre-flight: form и MainForm потребляют один Core exact-ID contract; tests вызывают тот же contract и существующий Stager. Shared interfaces согласованы с task. Ruling: task instructions задают текущий root/master и один final commit; дополнительные worktrees/branches, промежуточные commits и superpowers scratch scripts не используются. Цена ошибки: ошибочный scope/index; компенсируется final exact allowlist и initial stamps.

Непроверено до реализации: реальная новая форма, UI/DPI/VS round-trip, текущий Java/LM, повторный actual L2J import. PSS-003 evidence не является новым PASS.

## Журнал результата

- Task 1–2 complete: compileable fail-closed scaffold, scoped RED 0/8 → GREEN 8/0, static legacy flow RED exit 1. Initial sandbox restore без output прерван; no-restore использовал существующие assets без dependency изменений. Core и real stage subset/source/session checks PASS.
- Task 3 complete: form/integration + static Designer exit 0. Первый verifier запуск выявил PS5 UTF-8 BOM и newline-operator проблему; исправлен собственный скрипт, не product guards.
- Ruling: первое exact-list consent стало отдельной прокручиваемой readonly modal формой с default No; иначе MessageBox не гарантирует доступ к 20 полным текстам. Второе editorial MessageBox сохранено. Цена ошибки — изменение UX; независимый reviewer подтвердил соответствие task intent.
- Task 4 complete: actual STA controls 4/0; temporary filter-reset mutation RED 2/2 → restored GREEN 4/0. Final штатный Release Build-Verify exit 0, 77/0 ordinary checks, warnings/errors 0. Интерактивный input/screenshot недоступен после bounded recovery, NOT_TESTED UI/DPI/VS; применён разрешённый control-contract fallback без нового test project и production smoke API.
- Ruling: synthetic source остаётся в собственном temp, workspace внутри artifacts. Copy source внутрь Studio Git root корректно отвергнут RepositoryBoundary. Guards не ослабляются. Цена ошибки — невалидная fixture; повторная корректная подготовка exit 0, session SHA/bytes unchanged и zero proposals после UI attempt.
- Final fresh read-only review: 0 Critical/Important; deferred Minor — отдельный long-note >32767 preview regression отсутствует, truncation в production не найден. Полные findings/rulings: PSS-004-review.md.
- Task 5: exact inventory/text/readonly scope, commit/push/remote SHA выполняются после отчётов; фактический checkpoint handoff фиксируется после commit без amend.
