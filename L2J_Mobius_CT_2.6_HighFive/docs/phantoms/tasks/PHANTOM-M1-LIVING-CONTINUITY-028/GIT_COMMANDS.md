# Git028 authorization and exact commands

Git использован только по прямому TASK028 exception: initial bounded read/fetch,
own detached exact-base worktree, scope/provenance inspection, exact-file commits
и normal push в `experiment/m1-candidate007-observe008`. Main/foreign source не
переключались. Force/reset/clean/stash/rebase/merge/restore не выполнялись.

Точные исходные shell inputs с timestamps извлечены из local rollout только этого
чата в `evidence/GIT_COMMANDS_EXACT.json`. Другой rollout content не публикуется. Там
сохранены реальные arguments и exact path arrays, включая initial:

```text
git status --short
git branch --show-current
git rev-parse HEAD
git rev-parse --abbrev-ref --symbolic-full-name '@{u}'
git fetch origin experiment/m1-candidate007-observe008
git rev-parse origin/experiment/m1-candidate007-observe008
git ls-remote origin refs/heads/experiment/m1-candidate007-observe008
git worktree list --porcelain
```

Own `worktree add --detach` exact path/base, bounded `show`/`diff`/`ls-files`, все
exact `add`/`commit` и предыдущие normal `push` inputs даны в том же JSON; их
arguments не сокращены и не реконструированы из памяти.

Финальный source guard также вызывает конкретные read-only commands из own root:

```text
git diff --name-only 9aeb4ac6c52970372f97637d26a5eb54c760ed1a HEAD -- L2J_Mobius_CT_2.6_HighFive
git diff --name-only HEAD
git diff --cached --name-only
git show 9aeb4ac6c52970372f97637d26a5eb54c760ed1a:L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java
git rev-parse HEAD
```

Final publication stage выполняется по `FINAL_STAGING_ALLOWLIST.json`: каждый
entry имеет repo-relative exact path. Вызов для каждого entry — `git add --` с
одним literal path. Directory add и broad staging запрещены. Staged inventory
сверяется с allowlist до commit. Non-task sources на этот final publication stage
не включаются; tested production07c2 уже committed/pushed.

```text
git diff --cached --name-only
git -c core.whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol diff --cached --check -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LIVING-CONTINUITY-028/ :!L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LIVING-CONTINUITY-028/evidence/** :!L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LIVING-CONTINUITY-028/archives/**
git commit -m "docs(phantoms): publish continuity028 blocked evidence and handoff"
git rev-parse HEAD
git push origin HEAD:refs/heads/experiment/m1-candidate007-observe008
git ls-remote origin refs/heads/experiment/m1-candidate007-observe008
```

Эти final commands выполняются после encoding/privacy/archive/scope checks.
Whitespace check признаёт CR-at-EOL authored files; immutable raw evidence/archive
не форматируются. `-c` применяется только к одному invocation и не меняет root/
global/repository config. Первый обычный whitespace check дал technical CRLF RED,
raw files не переписывались.
Проверка remote HEAD и exact publication SHA сообщаются финально. Commit SHA в
самом отчёте не подменяет tested production SHA. `evidence/GIT_COMMANDS_EXACT.json` фиксирует
invocations до final publication; final фиксированный command block приведён выше.

Побайтная проверка observer/archive в index использовала ещё два exact reads:

```text
git show :L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LIVING-CONTINUITY-028/Contract028Observer.java
git show :L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LIVING-CONTINUITY-028/archives/RAW_EVIDENCE_028.zip
git ls-files --others --exclude-standard
git diff --cached --numstat -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LIVING-CONTINUITY-028/Contract028Observer.java
git diff --name-only HEAD -- L2J_Mobius_CT_2.6_HighFive/java L2J_Mobius_CT_2.6_HighFive/test L2J_Mobius_CT_2.6_HighFive/tools
git diff --name-only 07c2c1cc4036b47487c98e96943a39ce295a3920 HEAD -- L2J_Mobius_CT_2.6_HighFive/java L2J_Mobius_CT_2.6_HighFive/test L2J_Mobius_CT_2.6_HighFive/tools
```

Результаты: index bytes exact для обоих; non-task untracked0; production changes0;
live own Java0. Закрывающий task-only audit commit:

```text
git commit -m "docs(phantoms): close continuity028 publication audit"
git push origin HEAD:refs/heads/experiment/m1-candidate007-observe008
git ls-remote origin refs/heads/experiment/m1-candidate007-observe008
```

Его exact files снова определяет обновлённый FINAL_STAGING_ALLOWLIST, с тем же
`git add --` по одному literal path и staged/hash/whitespace guards. Новых Git
permissions и repository/global config mutations это не вводит.
