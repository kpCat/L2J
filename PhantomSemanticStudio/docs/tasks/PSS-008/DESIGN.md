# PSS-008 — архитектура одной большой завершающей задачи

## Цель пользователя

Studio должна развивать диалоги живых фантомов High Five, используя подготовленные/одобренные фразы из Semantic Pack и локальный корпус чатов, **не редактируя сервер бесконтрольно**. После A/B/C владелец видит пробелы качества, собирает маленькое точное улучшение v3 и может получить проверенную Java-загрузчиком offline-кандидатуру плюс письменную инструкцию ручного выпуска/отката.

## Три независимых, связанных результата

```
Read-only High Five humanized v1/v2/v3/custom
    → PackSnapshot with SHA/provenance
    → A: coverage, capacity, duplicates/holes (ADVISORY/NOT_JAVA_PARITY)
                           ↓ explicit manual selection
Existing CURRENT APPROVED Candidate IDs + exact declared v3 segment pair
    → B: preflight + two consents + shadow copy/hash + append-only v3 XML
    → workspace/v3-proposals/<GUID>/module/dist/game/data/phantoms/... + receipt
       STAGED_V3_UNVALIDATED / Java NOT_RUN / NOT_FOR_INSTALLATION
                           ↓ separate explicit operator action, NO GUI shell
    → C: Java25 + Ant target in isolated copied module, loadV3(...,true), negative fixtures
    → oracle proof tied to exact staged file SHA / source SHA / selected IDs
       PASS_JAVA_STAGED_V3 / JAVA_CONTENT_ONLY_NOT_RUNTIME_READY
                           ↓ third explicit human release review
    → workspace/release-candidates/<GUID>/{proposed,backup,release-manifest.json,CHECKLIST.txt}
       OFFLINE_HANDOFF_NOT_INSTALLED; L2J unchanged
```

## Hard boundaries

- No installed output, no runnable installer, no `Process.Start`/Java/PowerShell/Git from product GUI; scripts operated manually under Studio own scratch only.
- A never alters candidates, lessons, approvals, source. B never alters custom, v1/v2, functional semantic, manifest or other segments in source; all staged-only writes limited to exactly selected existing segment pair. C never treats Java content PASS as functional/social/runtime parity. **Source forever read-only.**
- Distinct locations from PSS003 `workspace/proposals/<id>` custom staging; new `workspace/v3-proposals/<id>` specifically for v3; operator scripts may support these exact new paths, but must not weaken PSS003 path policy or permit arbitrary directory input.
- `PackReader` approximates Java; Java is final schema/index validator. Scope input is editorial only. Gender!=ANY, placeholders, mature/profanity, unknown act/topic, claims of performed gameplay, invalid IDs/dup texts — reject entire release batch, no skipping.
- Existing user-private chat data, approved text and dialogues never appear in tracked files. Models not required for A/B/C. If live Gemma unavailable, report `BLOCKED_LM`, but don't block deterministic work.
- Avoid heavy full-product rewrite, new framework, new NuGet dependencies, new daemon. Use existing System.Text.Json, System.Xml.Linq, SQLite 10.0.12 only where already justified.

## Ownership and interface guidance

Create separate narrow owners (exact final names may vary only if documented in `PSS-008-plan.md`):
- `src/PhantomSemanticStudio.Core/PackCoverageAnalyzer.cs` — pure bounded summary/group/diff rules, immutable result.
- `src/PhantomSemanticStudio.Core/IsolatedV3ProposalStager.cs` — pure preflight/plan + own physical shadow writer and receipt; reuse checked primitives from PSS003 if safe, don't broadly refactor existing custom stager.
- `src/PhantomSemanticStudio.Core/V3ReleaseHandoff.cs` — offline candidate manifest/checklist gate requiring verified stage/proof, no server destination paths.
- `src/PhantomSemanticStudio.WinForms/PackQualityForm.cs + .Designer.cs + .resx` — quality UI.
- `src/PhantomSemanticStudio.WinForms/V3ProposalForm.cs + .Designer.cs + .resx` if meaningful for exact segment selection and two-step consent; reuse `StageSelectionForm` rather than cloning it.
- `scripts/Test-PSS008-V3-Java.ps1`, optionally `scripts/java/Pss008V3CatalogProbe.java` — separate operator oracle; do not relabel PSS003 custom-only probe as v3.
- Existing console runner: `tests/PhantomSemanticStudio.Tests/Pss008.cs`, focused `--pss-008-a|b|c`, optional STA route, `scripts/Verify-PSS008.ps1`.

Prefer 12–20 production/test changed paths; document bounded exception with three independent connected modules. A/B/C each own focused RED/GREEN and no new dependency. No code regeneration/reformatting of Designer wholesale.

## UX rule

The GUI must always distinguish **Source** vs **Studio suggestion** vs **Java content proof** vs **manual deployment**, with permanent labels `NOT_INSTALLED`, `NOT_JAVA_RUNTIME_PARITY`. A successful Java content test never causes automatic copy/deploy. Product WinForms source compiles & opens in Visual Studio Designer; manual physical acceptance remains separate.
