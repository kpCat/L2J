# PSS-003 — детерминированная матрица тестов

Добавить focused console coverage в существующий test runner (можно отдельный режим `--pss-003`) и оставить все PSS-001/PSS-002 проверки зелёными. Каждый отрицательный тест должен проверять ожидаемый error/status И отсутствие finished stage/source mutations. Не использовать production DB/GameServer.

### C# staging

1. **Positive 2 items**: fixture with approved PATTERN and TEMPLATE, exact topic/act, Gender ANY, no placeholders, distinct text. После отдельного явного editorial-confirm callback: 65 исходных fixture stamps unchanged, оба custom XML изменены только в isolated stage, одна уникальная XML ID на candidate, новый stage reloadable C# PackReader, originals без изменений; receipt с exact IDs/hashes.
2. **Approval**: DRAFT/REJECTED, мутировавший ApprovedFingerprint, изменённый ReviewNote/текст и missing ReviewedAtUtc → отказ, no output. Selection без explicit confirmation → отмена/no output. Один неверный candidate в пачке из двух → fail entire batch, не молча skip.
3. **Source drift**: source content изменился перед стартом или во время file-copy/hash recheck; скопированное не совпало с snapshots → fail/cleanup, без попытки перепривязки. Прежняя approval history не затёрта.
4. **Duplicate & ID**: text equals existing normalized (case, punctuation, ё/е), duplicate across selected peers, existing ID collision, mismatched act/topic, wrong kind/unknown functional act → fail. Stable IDs deterministic; 2 runs same source+selected IDs have equal XML semantics even if independent stage location differs.
5. **Scope safety**: Gender FEMALE/MALE, placeholders, profanity/mature risk/unsupported runtime action, incorrect XML snippet, control char, XML injection, `override=true` attempt and traversal/path/junction/reparse → blocked. Raw unsafe text never interpreted as XML markup.
6. **Java bounds**: oversized UTF-8 Cyrillic TEMPLATE (e.g. 130 Cyrillic chars >240 bytes), PATTERN normalized >160 UTF16 chars, custom file >65536 bytes, overfull catalog, count cap -> no stage, no truncation, no auto limit changes.
7. **Preservation**: existing custom override and comments roundtrip; every staged non-target file SHA == source bytes; staged changed XML remains parseable strict UTF-8. Cancellation/IO failure midcopy or write cleans only its partial folder; no unrelated outputs deleted.
8. **UI/static designer**: standard Designer controls, constructor-only InitializeComponent, no runtime BuildUi/loops/IO in InitializeComponent; one-button operator consent, refusal no stage. Actual UI/DPI/Designer gate separately PASS/NOT_TESTED — console assertion isn't physical visual evidence.
9. **No-effect on old flows**: generation/modes/failed LM do not produce XML stages; old `ReviewExporter` JSON-only remains; `WorkspaceStore` session+approval unchanged after stage success/failure. Model API cannot inject file paths or XML.

### Java oracle

10. **Baseline shadow:** Java Ant target and explicit Java `loadV3(shadowDataRoot,true)` on unmodified fixture copy (or copy of user source) pass, with counts/hash evidence (no write to original High Five).
11. **Positive staged:** Java actual load of staged custom overlays succeeds, counters match expected deltas, distinct combined hash vs baseline, exact customer IDs/act/topic visible by a safe focused assertion; reproducible with no DB. If not proven → not PASS.
12. **Negative Java:** corrupt staged XML in a second isolated test copy (duplicate ID, missing act or oversize) must return non-zero/fail-closed; **не** менять production. Also test that report cannot say `PASS_JAVA` after skipped/failed oracle.
13. **No Java environment:** missing JDK25/Ant/libs must return `BLOCKED_JAVA` and safe staged status only, without auto-downloading dependencies or claiming server-ready. Java scripts must reject shadow outside Studio Root / symlink / source root.

### Completion commands (adjust exact runner invocation to project)

- `dotnet run --project tests/PhantomSemanticStudio.Tests -c Release -- --pss-003` focused; collect red→green evidence.
- `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\Build-Verify.ps1` once final aggregate after fixes.
- `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\Verify-Designer.ps1` static guard.
- `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\Test-PSS003-Java.ps1 -StageRoot <isolated-test-stage>` **только если** detached build safety provably succeeds; give command, JDK/Ant version, native exit, counters, output roots. Use harmless test fixture candidates, not real LM user text.
- `git diff --check` and staged exact-path scope; check source SHA before/after for all 65 local humanized files if available.
