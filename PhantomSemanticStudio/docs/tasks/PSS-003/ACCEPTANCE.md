# PSS-003 — acceptance gates

## Обязательные локальные критерии

- C# Release solution build exit 0 без новых warnings/errors; existing PSS-001/002 regressions + new focused suites exit 0. No tests silently disabled or hidden by broad catches.
- Подготовка staged copy из утверждённых кандидатов с отдельным **явным согласием**, deterministic valid IDs/strict XML/Java bounds, no hidden skip or implicit profanity/mature/gender downgrade.
- Все вводные humanized source files byte-equal before/after; source fingerprint reread, source drift blocks. Staging in Studio workspace ONLY; no auto install, no touching L2J. Existing original v1/v2/v3/manifest unchanged in stage; custom XML changes only in shadow; source custom originals unchanged too.
- Failure atomic: missing approval/consent, bad candidate, file size/cap, permission, cancellation leaves no misleading finished stage.
- Strict path/reparse tests; no code execution from LM or user input; no secret/raw prompt in Git/evidence.
- `AGENTS.md` updated: no code-review ZIP workflow, GitHub main is sole source of truth for independent diff review.
- WinForms remains Visual Studio Designer friendly. Physical UI/DPI may be NOT_TESTED, never inferred from static checks.
- Evidence report names exact staging status, actual Java status, exact Java command/paths/exit, source SHA/bytes equality, path+owner tests, new PASS count, actual diff. A code-only change cannot be called valid server content.
- Exact non-force push to `kpCat/PhantomSemanticStudio` main; remote SHA verified; no writes to L2J.

## Статусы

- `STAGED_UNVALIDATED`: C# stage formed, Java not run; safe output but **not server-ready**.
- `BLOCKED_JAVA`: cannot execute Java oracle safely (missing JDK25/Ant/libs/scratch constraints); console implementation may still PASS, checkpoint **BLOCKED** for Java-parity.
- `FAILED_JAVA`: Java rejected XML or targeted tests; stage not eligible for further use. Do not call this GREEN.
- `PASS_JAVA_STAGED`: actual isolated Java catalog load, custom enabled, count/source integrity proven; still `NOT_SERVER_READY` until future explicit release-review & runtime proof.
- `BLOCKED_LM`, `UI_NOT_TESTED`, `DPI_NOT_TESTED`: carried separately from PSS-002; no repeat retries if offline. They don't need to block C# staging tests but remain open gates for whole application.

## Definition of DONE

Only call PSS-003 implementation GREEN when all applicable C# gates pass and at least the **test-fixture staged XML** has an actual successful Java load in the shadow root with verified inputs. If Java unavailable, report `BLOCKED_JAVA` honestly while preserving safe implementation; don't invent a Java green or publish XML to server. PSS-003 is NEVER a production-install milestone.

No **review ZIP** output required or allowed. New task docs themselves are installed from a small user-provided **task ZIP** — not part of Codex's review evidence. Always use GitHub commit and report links.
