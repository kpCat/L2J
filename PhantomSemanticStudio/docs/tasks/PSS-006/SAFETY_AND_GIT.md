# PSS-006 — immutable boundaries + Git

## Protected locations

`C:\Users\ZBook\L2J_Mobius\` and all descendants including `L2J_Mobius_CT_2.6_HighFive`, Java/XML/v3/custom, `.phantom-local`, geodata, server, DB, logs, Git — **READ_ONLY**. No direct or temporary writes, no Ant/JAR/client/server activity there. Do not change permission/config or alter `origin` of L2J.

`chat.zip` provided by user's friend contains private third-party communication. Never commit/upload the ZIP, extracted logs, DB/index, chat usernames, raw chat text, API tokens, private prompts or evidence responses to `kpCat/PhantomSemanticStudio` or external tools. All sample fixtures in tracked sources must be synthetic. Private original resides only user-selected path and protected local workspace; no automated upload, indexing of private channels, or network processing. If code uses any model, it may receive only explicitly selected, previewed, scrubbed text and only in future PSS-007; importer itself calls NO MODEL.

## Preserve existing architecture

No auto publication, install, apply, source overwrite, generated Java/C#, or model tools. `IsolatedPackStager` remains staging only; approvals and review hashes unchanged; strict LM Schema/no tools/no redirect/loopback only. User's existing workspace must not be deleted/migrated destructively. No broad refactor of other task features just to fit importer. Keep normal WinForms Designer and no loops in InitializeComponent.

## Own Git policy

- Before work check `git rev-parse --show-toplevel`, `HEAD`, `git status --porcelain=v1 -uall`, `origin fetch/push URL`, `git ls-remote origin refs/heads/main` == f92431aa5594a62210916e94bc6b617ec51f4bdc. Do not trample incoming untracked task files or any other user dirt. Local branch may remain `master`.
- Allow only ordinary `git add -- <exact task-owned files>`, `git diff --cached --check`, `git commit` for GREEN/internal checkable deliverables, `git push origin HEAD:refs/heads/main` **without force** (only verified `https://github.com/kpCat/PhantomSemanticStudio.git`). No `git add .`, reset, clean, stash, rebase, amend, force, checkout, branch rewrite or Git commands in L2J. If remote changed: `BLOCKED_REMOTE`, no force.
- GitHub public review is by exact commit/diff/final evidence. **NO review ZIP/source ZIP, no invoking `scripts/New-ReviewBundle.ps1`.** Separate user-function JSON REVIEW_ONLY ZIP persists unchanged.
- Reports include only aggregate synthetic stats, test paths/exit codes, sha and status; never real corpus lines/names. `artifacts/`, runtime `.db` and imported sources remain ignored or outside Git. Staging allowlist MUST be audited against these limits.
- Last step always report + exact inventory + local commit(s) + nonforce push + remote SHA equality + own tree status. If BLOCKED/FAILED, preserve safely passing changes and tests, report unable gates, do not mislabel as GREEN. STOP, do not start PSS-007.
