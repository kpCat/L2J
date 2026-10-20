# PSS-006 — что Codex возвращает пользователю

- `reports/PSS-006-final.md`: exact baseline/final SHA, parents, actual git push/remote result, independent status for A/B/C, list of source/test files changed, brief architecture decisions, limitation disclosure.
- `reports/PSS-006-plan.md`: read-first root/owners/impact, code review, acceptance coverage, local index decision and dependency budget.
- `reports/PSS-006-ui.md`: actual standard WinForms/STA controls, interactive UI, physical DPI 100/150, VS Designer 2026 round-trip separate explicit PASS/NOT_TESTED with evidence, manual actions where needed.
- `reports/PSS-006-owned-files.txt`: exact committed task-owned file inventory (including prepared task docs), no user dirt, private corpora, outputs or credentials.
- Tests: real focused RED/GREEN logs, one final `scripts/Build-Verify.ps1` summary, privacy-negative controls, cold/missing LM results, sample/large synthetic import results and peak memory if measured. Invalid case must fail tests rather than swallowed assertion. No secret/raw chat values in any stdout.
- Public GitHub commit link and verified `origin/main` SHA; Code reviewer checks actual diff, release evidence, no L2J writes. No review ZIP.
- What user can do after PSS-006: launch Studio, click «Корпус чатов», choose original `chat.zip` from a private location, import/index it (without private conversations), search public game/chat phrases, identify possible translit, select examples for later PSS-007. Gemma does **not** train itself automatically, and source Semantic Pack remains untouched.
- Stop without PSS-007 implementation; request next task after independent review.
