# PSS-010 implementation plan and ledger

Goal/spec: docs/tasks/PSS-010/GOAL.md and complete task package.
Execution: primary Codex inline, real RED/GREEN, one independent review.
Read: AGENTS, README_RU, DESIGN_RU, SOURCE_AUDIT_RU, baseline/PSS009 reports,
PSS007 review/A, PackPreview, DialogueLab, Models, TextRules, PackReader,
Send_Click/Preview_Click, projects/props, runner/Fixture and local verifiers.
Absent: ancestor AGENTS, README.md, code-map and separate pattern files.
Reuse: console Test/Equal/Throws, Fixture/PackReader, inspector Copy/Commit,
exact allowlist and separate UTF-8/mojibake/escaped Cyrillic checks.

Constraints: .NET10/C#14, WinForms unchanged, no new dependency. Required
HEAD/origin/main/remote = 9d5399e65c6c260ea2026c2da006cb48ba7bffce; master retained.
User .sln SHA256 = 4aa3a62b295b89b435bbad62ea34a67b0eb3447f52a89c927d711138af786442,
never write/stage. Designer/resx and all user dirt preserved; L2J read-only.
No model PACK, fictional placeholders, approvals, XML, Java or review ZIP.

Bounded artifact exception: ten unchanged incoming task files, five code/test
owners, README_RU, one verifier and required reports/stdout; one coupled bug
and its evidence, no additional product subsystem. Final exact owned inventory.

- [x] Program.cs/Pss010.cs: public Core baseline RED for greet/mood, captured value raw/render mismatch, honest no-match.
- [x] PackPreview.cs/Models.cs: additive explicit Status, ordered safe rendering, eligible literals/known bounded value, repeat fallback.
- [x] DialogueLab.cs: consume explicit inspector status/text, provenance/transaction/source guards retained; пвп advisory only.
- [x] Same route GREEN: unavailable/functional/Fact/Recall/filter/capture/repeat/cancel/stale/source/session preservation.
- [x] Narrow actual v1 probe: exactly two XML inputs read-only; physical copy only in ignored Studio artifacts; before/after SHA/bytes.
- [x] First-only negative mutation: runtime RED, restore in finally, final GREEN and Release Build-Verify.
- [x] Legacy PSS007 A/B/C, STA007, PSS009 layout and available static regressions.
- [x] Reports/README/verifier: exact privacy/package/encoding/scope checks and honest physical UI/VS/LM/Java statuses.
- [ ] Independent exact-diff review, required fixes, exact add, ordinary commit, non-force push origin HEAD:refs/heads/main, remote/public SHA; STOP.

Review focus: safe literals not shadowed; unknown captures never fabricated;
only displayed IDs consume queue; mature/profanity/band/register gates retained;
stale/cancel never commit speculative state.
Ruling: fully specified user task authorizes execution/publication on current
master; no extra plan approval/worktree/skill workspace/extra commits. Reports
are the persistent ledger. Physical UI/VS/live LM/Java remain separate limits.

Final: independent reviewer Critical0/Important1/Minor1. Capture provenance fixed via RED10/1 -> GREEN11/0; bounded verifier output-directory precondition fixed and negative workflow passed. No deferred findings. Publication transaction follows this report commit; exact SHA checks are returned in final handoff.
