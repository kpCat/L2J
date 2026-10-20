# PSS-002 Implementation Plan

**Goal:** управляемая локальная Gemma, явный TEMPLATE/PATTERN/MIXED и честные UI gates.
**Architecture:** сохраняются HttpClient, strict JSON parser, WinForms Designer и атомарный WorkspaceStore.SaveSession. Диагностика содержит только фиксированные категории/русские инструкции; модель не получает tools и не генерирует код.
**Tech Stack:** .NET 10, C# 14, WinForms, System.Text.Json; без новых NuGet.
**Spec:** docs/tasks/PSS-002/GOAL.md и полный пакет PSS-002.

## Read-first и база

Прочитаны AGENTS.md, README_RU.md, DESIGN_RU.md, SOURCE_AUDIT_RU.md, BASELINE_VERIFICATION.md, PSS-001 final/ui/owned-files, весь пакет PSS-002, LmStudioClient/Models/CandidateValidator/WorkspaceStore/ReviewExporter, MainForm/Designer, Program/SourceSmoke, csproj/props и Build-Verify/Verify-Designer/New-ReviewBundle.
Дополнительных AGENTS выше root, code-map и отдельных pattern-файлов не найдено. Локальные аналоги: bounded SendAsync/ParseDrafts; clone-before-save; scoped lesson.ToRequest; атомарный SaveSession; существующий FakeHandler и SourceSmoke.

Локальный HEAD и удалённый main: 87755c86421c9a320c7bc1f5f7682fb13c95332e. Root проверен. Ветка master; origin fetch/push — https://github.com/kpCat/PhantomSemanticStudio.git. Начальный dirt: только восемь неизменяемых файлов пользовательского пакета PSS-002, разрешённых для публикации задачей. Первый sandbox ls-remote: connection blocked; разрешённый network escalation: exit 0, exact SHA.

## Global Constraints и решения

- L2J строго read-only; никаких Ant/JAR/server/DB/git в L2J.
- Constructor только InitializeComponent; layout/events/controls статически в Designer.
- UI по умолчанию TEMPLATE; старый GenerationRequest/lesson — MIXED согласно ARCHITECTURE/ACCEPTANCE.
- Один bounded JSON Schema POST, без fallback/retry/model substitution; batch проверяется полностью до записи DRAFT.
- REVIEW_ONLY JSON, Java NOT_RUN; source fingerprint/approval hash/peer checks сохраняются.
- Живой smoke: один GET и максимум два POST по 1–3 items, стоп при offline/auth/schema/mismatch.
- Выполнение inline основной моделью; повторного согласования нет по прямому запросу пользователя. Существующий root сохраняется; новые ветки/worktree не нужны.

## Review Focus / risk map

1. Server error body/exception может раскрыть token/prompt: фиксированная безопасная диагностика и redaction assertions.
2. Один неверный kind в партии: локальный reject всей партии; сохранённая session неизменна.
3. /models/JIT не доказывает inference: CHECKED_MODEL_LIST с явным ограничением.
4. Старые lessons/request: MIXED default, scope/baseline не меняются.
5. Unsaved editor/keyboard selection и DPI: интерактивные gates только при реальной доступности инструмента, иначе NOT_TESTED.

## Tasks / ledger

- [x] 1. Focused RED: 0 PASS/7 FAIL (реальные assertions), exit 1.
- [x] 2. Models/LmStudioClient: typed mode/diagnostic, safe messages, post-schema local validation; targeted GREEN 9/0, exit 0.
- [x] 3. Designer selector + MainForm diagnostics, README; SourceSmoke PSS-002 output/count2, без raw model output. Изолированный process-only UI smoke path внутри artifacts, обычные PathSafety guards сохранены.
- [x] 4. Source/live: import PASS, 65/65 stamps equal, GET refused → BLOCKED_LM/0 POST. UI startup PARTIAL, input/capture unavailable → full UI/DPI NOT_TESTED. Свежий reviewer: один Important sizing defect; static RED→GREEN 0px→62px. Финальная Build-Verify exit 0, 0 warnings/errors, 59/0.
- [x] 5. Две отдельные Cyrillic проверки PASS; final source equality 65/65; task package SHA/bytes unchanged; exact allowlist/index 29 paths; ZIP 87 files verified. Feature commit fb1f4a2cc252f2007bf46c7b31227d833852e7ba; normal push exit 0, remote SHA equal. Закрывающий metadata receipt и final ZIP proof — artifacts/PSS-002-checkpoint.json / PSS-002-zip-manifest.json. STOP.

## Bounded scope exception

Production/test: LmStudioClient.cs, Models.cs, MainForm.cs, MainForm.Designer.cs, tests/Program.cs, tests/SourceSmoke.cs (6). README и reports/task package — необходимые evidence, не новая подсистема. При необходимости один небольшой verification script для artifact/text/source guard. PackReader/CandidateValidator/WorkspaceStore/ReviewExporter/PathSafety и проекты остаются вне mutation scope без воспроизведённого дефекта.

Непроверенное на входе: текущие 50 assertions ещё не запускались; live LM, runtime UI, physical 100%/150% DPI, новый Designer round-trip и Java. PSS-001 USER_VERIFIED не переносится в PSS-002 PASS.

Ruling: HTTP 400 Schema-rejected проверяется POST, не GET — исправлен ошибочный setup focused test; production classification не ослаблена. Проверены оба контракта.
Ruling: дополнительный Verify-PSS002.ps1 нужен для воспроизведённого sizing regression и final source/text/ZIP guards; это единственный дополнительный targeted script. Windows PowerShell требует UTF-8 BOM; marker literals используют double quotes из-за low quotation mark в поисковых данных. Первые parser errors были ошибкой verifier setup, не production/build; исправлены до RED sizing evidence.
Ruling: UI capture/input не работают даже после bounded recovery; не добавляется самодельный Reflection/Win32 harness. Цена: runtime interaction и physical DPI gates остаются NOT_TESTED.
Final review: one Important fixed — instruction editor static RED→GREEN, final full suite 59/0. Critical/Minor findings нет. Reviewer не судил live UI/DPI/Designer/LM по source; эти ограничения отражены отдельно, Java вне scope. Независимый human review PENDING.
