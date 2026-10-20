# PSS-008 — безопасность, запреты и Git

## Absolute no-write / model scope

- L2J tree entire `C:\Users\ZBook\L2J_Mobius\` is READ_ONLY, including `.git`, phantom-local, build, Java, XML/manifest/custom, servers/DB/geodata/configs. No write/temp/rename/delete/copy-back. Java/Ant only in Studio own physical copied scratch. No git in L2J at all. Source-only audit read of exact files/hashes permitted.
- Raw private `artifacts/private-input/chat.zip`, `.db/.db-*`, `corpora`, nicks, messages, review content, API tokens, prompts, local stage/proof/backup, raw model output must NEVER be committed, uploaded, logged in public reports, copied to network or sent to Gemma automatically. Use synthetic tests and aggregate-only reports. Do not rely solely on `.gitignore`: exact staged path allowlist and secret/PII negative scan before commit.
- GUI/Model no Process.Start/shell/Java/Ant/Git/writable L2J; no tool/function calls from model, no auto retries/auto load/unload/server start, no auto-approve, auto-publish, auto-repair or elevated changes. No installer / .bat / .ps1 that performs copy to L2J. Test scripts need exact own-root verification. Do not modify Windows global DPI or security/VS settings without user consent.
- Two B consents and third C release handoff consent default NO; no silent partial acceptance, no auto skipping incompatible candidates, no fake approval, no migration of legacy approvals, no silent source rebase. In a collision/traversal/unknown loader schema case, fail closed.
- Strict UTF-8 file IO with direct Cyrillic text; no mojibake, \u04xx escaped Cyrillic or HTML entities for user-facing Russian. Run independent checks. Use a Unicode-safe codepage on PowerShell 5.1 scripts (BOM if needed), don't change system locale.
- WinForms Designer `.cs/.Designer.cs/.resx`, `.csproj` nesting correct; parameterless form ctor only `InitializeComponent`, no runtime build UI/loops/IO in designer method.

## Git closure

1. Before: own root, HEAD, user dirt, expected base & exactly `origin` fetch/push URL. `origin/main` must match `0344c9c0671d53a7bb757db7a92b1dfd03246e9c`, otherwise STOP `BLOCKED_REMOTE`, do not reset/rebase/force/merge. `git ls-remote` with permission only Studio origin.
2. During: allow normal local commits for independent A/B/C checkpoints with explicit exact file allowlist; no `git add .`, no mass format/rewrite, no `reset`, `clean`, `stash`, `checkout`, `rebase`, `merge`, `amend`, `force`, `branch` rewrites. Keep user changes untouched.
3. After: `reports/PSS-008-owned-files.txt` exact inventory of task package, changed sources, tests, reports. Stage exact paths, verify index against inventory; `git diff --cached --check`; independent read-only review of touched production code and user safety; normal commit(s) and non-force `git push origin HEAD:refs/heads/main`, verify local SHA == `git ls-remote origin refs/heads/main` == public GitHub commit, tree clean.
4. Never push to `kpCat/L2J`; no review ZIP/source ZIP/binary/SQLite, even in private branch. Safe commit for BLOCKED/FAILED documents, no broken code as GREEN.
5. If source build/Ant oracle unavailable due to environment: preserve safe own-root outputs and tests, mark BLOCKED_JAVA not PASS; focus on independent A/B. No Docker/install SDK/remote download to compensate without prior user approval.
