# PSS-005 — критерии приёмки

| Проверка | Условие PASS |
|---|---|
| Required base | Исходный Studio HEAD/remote main `5d1aedaed38d048e3313c0d8be7d5f81f4db2df2`; no unauthorized dirt reset |
| Offline scout | same-kind весь imported pack + non-rejected peers, deterministic top<=12, provenance и counts; **явный** `COVERAGE_LIMITED` |
| LM advisory | exactly user-driven one bounded POST; strict JSON Schema/local parser, exact reference IDs, sanitize/no tools/no retries. Model analysis не считается доказанным при fake HTTP |
| Persistence | bounded optional evidence (legacy session accepted), hash-bound freshness, stale on changed source/candidate/peers, no automatic approvals, old approval SHA unaffected |
| Error preservation | HTTP/timeout/cancel/source drift/invalid evidence => zero partial persistence; existing session survives |
| WinForms | две обычные Designer кнопки, user sees specific candidate texts+reference IDs and uncertainty; six tabs, `MainForm()` only InitializeComponent, no overlapping controls per available static/runtime evidence |
| C# tests | targeted RED→GREEN, final `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Build-Verify.ps1` exit 0, no regressions/warnings/errors; all counts real |
| Input safety | весь L2J read-only, no Java/Ant/server/DB/live XML, no changed PSS003/PSS004 stager, untouched existing authoring/export/review |
| Text | mojibake markers и escaped Cyrillic проверены двумя отдельными проходами, real UTF-8 Cyrillic |
| Git | normal scoped commit+non-force push только `kpCat/PhantomSemanticStudio` `origin/main`, remote SHA == local SHA, clean working tree либо честный BLOCKED |

**Самостоятельные статусы:** `LOCAL_SCOUT_PASS/BLOCKED`, `SEMANTIC_MOCK_PASS/BLOCKED`, `LIVE_LM_PASS/BLOCKED_LM/NOT_RUN`, `DESIGNER_STATIC_PASS/BLOCKED`, `VS_DESIGNER_PASS/NOT_TESTED`, `UI_PASS/PARTIAL/NOT_TESTED`, `DPI_100/DPI_150 PASS/NOT_TESTED`, `JAVA_NOT_RUN`, `SERVER_XML_NOT_PUBLISHED`. Нельзя подменять один статус другим.

**Критично:** PSS-005 не обеспечивает полную семантическую дедупликацию 20k+ фраз; нет embedding coverage и запрета стейджинга по мнению модели. Это первая ручная экспертиза похожих записей. `CandidateValidator.SEMANTIC_NOT_CHECKED` и `STAGED_UNVALIDATED` не повышать до проверенного статуса.
