# PSS-008 — финальный отчёт и что отправить пользователю

**НЕ делать review ZIP/source ZIP.** GitHub public `main` — источник ревью кода. Не прикреплять реальные чаты, DB, XML, локальные user history, proofs with sensitive raw text или модельный ответ.

Create and commit task-owned:

- `reports/PSS-008-plan.md`: read-first, exact HEAD/source contract verified, initial user dirt, source paths, affected owners, A/B/C plan, source lock and scope.
- `reports/PSS-008-final.md`: statuses A/B/C; Release build command/exit/warnings/errors/PASS count; focused RED→GREEN/negative gates; 65 source SHA/bytes before/after read-only; Java separate Ant/probe/negative exact actual statuses + new source proof if run; capture/harness limitations; content not runtime parity; live LM status and whether actual POST; no installation; changed files and deferred findings; Git branch/base/commit/push/remote SHA; exact summary of actual test status not synthetic claims as real.
- `reports/PSS-008-ui.md`: static/STA/physical DPI100/150/VS Designer statuses and exact operator checklist. Correct UI state for disabled buttons/confirmation dialogs.
- `reports/PSS-008-owned-files.txt`: exact staged index allowlist including task-package files/reports; preserve user dirt; no uncommitted source ZIP.
- `scripts/Verify-PSS008.ps1` optional safe guards and focused tests; `README_RU.md` updated to reflect actual features and limitations.

No dependency/Java/DB/server/client edits in L2J. Java scratch/output belongs only to `workspace/v3-proposals`; release folder to `workspace/release-candidates`; both ignored. If oracle missing, label BLOCKED_JAVA; don't manufacture `PASS_JAVA_STAGED_V3` or release output. If GUI unavailable, `NOT_TESTED` (STA/static not physical).

Git process: validate exact own root/remote/base, stage exact paths, check index, ordinary commit(s), `git push origin HEAD:refs/heads/main` non-force, compare remote/public SHA with local HEAD and status clean. Can use separate commits after A/B/C if all linear normal commits; no reset/amend/rebase/force. In BLOCKED/FAILED commit stable partial work + reports, no false DONE and no follow-on work.

**STOP AFTER PSS-008.** The next interaction is independent review by assistant through public GitHub, not an automatic PSS009. At that point ask user to actually open WinForms and try a conversation, live LM small test when user chooses to load model, VS Designer round-trip and DPI; only then decide if any small fix task is needed.
