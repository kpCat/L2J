# TASK024 publication
Runtime/source frozen SHA `ca3cbef9c03b695dcb9f84a734c5535b4ac72027` уже normal-pushed в `experiment/m1-candidate007-observe008`.
Final documentation/evidence publish uses exact TASK024 path only; no new semantic source.
The final documentation commit is the branch tip containing this file; its hash is verified by final tool receipt and user report, avoiding a self-referential hash inside the same commit.

Exact final commands, in own worktree:
```text
git diff --name-only 819e3cea5baa64e6c429e450c8fc296874e37d1c --
git diff --name-only ca3cbef9c03b695dcb9f84a734c5535b4ac72027 -- L2J_Mobius_CT_2.6_HighFive/java
git status --porcelain=v1 --untracked-files=all
git diff --check -- <exact changed source and executable/documentation paths; immutable raw evidence excluded>
git add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-RUNTIME-CONTRACTS-024
git diff --cached --name-only
git diff --cached --check -- <exact executable/documentation paths; immutable raw evidence excluded>
git commit -m "Record TASK024 failed server gates and exact lifecycle evidence"
git push origin HEAD:refs/heads/experiment/m1-candidate007-observe008
git rev-parse HEAD
git ls-remote origin refs/heads/experiment/m1-candidate007-observe008
git status --porcelain=v1 --untracked-files=all
```
Scope validation requires every staged path inside TASK024, production11 unchanged from frozen ca3, all own roles/listeners stopped, and separate encoding checks. A remote advance is not merged/forced: preserve local commit and report PUBLISH_BLOCKED.

Raw evidence newline preservation: nested TASK024 .gitattributes uses evidence/** -text and raw log/dump -text. No global Git setting changed. Exact additional staging: git add --renormalize -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-RUNTIME-CONTRACTS-024. Eight exact git cat-file blob :<crash evidence path> calls compare index bytes against disk and receipt SHA256; see FINAL_AUDIT.json for full paths/commands/results.
Final audit helper exact Git commands are recorded in FINAL_AUDIT.json, including status, diff, cached diff and cat-file; this extends GIT_COMMANDS.md.

Verified bulk publication: commit9004227a60bdd4e03efda4e6b56384728ddb867f normal-pushed; ls-remote exact equality checked20:28:52UTC. PUBLICATION_VERIFIED.json records that immutable bulk receipt; final documentation-only commit is verified separately in final tool output.
Initial automatic approval review rejected unverified destination/large evidence payload. GitHub authenticated owner/admin/push and bounded credential/account-dump scan were checked; same normal push accepted and succeeded. No indirect upload, force or removed evidence.
Exact final receipt commit: git commit -m "Record verified TASK024 publication and safety checks"; normal git push origin HEAD:refs/heads/experiment/m1-candidate007-observe008; final git rev-parse HEAD / git ls-remote / git status --porcelain=v1 --untracked-files=all.
