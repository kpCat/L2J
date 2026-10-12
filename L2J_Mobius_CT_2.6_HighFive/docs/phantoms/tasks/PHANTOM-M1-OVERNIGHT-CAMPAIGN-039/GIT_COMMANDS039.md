# GIT_COMMANDS039

Git использовался по прямому /goal и GIT.md039, только OWN phantom-dev; MAIN read-only.
Реальные argv/UTC/exit/stdout/stderr находятся в OUT/J1-git039.json…J4-git039.json
и OUT/FINAL-git039.json. OUT определён в EVIDENCE_INDEX039. Их literal allowlists
сохранены до stage; private raw/Jar/secrets/zip и unrelated untracked не stage.

Точные формы использованных bounded read commands (OWN указан literal в receipts):

```text
git -c safe.directory=C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius -C <OWN> status --short
git -C <OWN> rev-parse HEAD
git -C <OWN> ls-remote origin refs/heads/experiment/m1-candidate007-observe008
git -C <OWN> diff 8bf4df23a23854b9ff57c13b83a8ab34f07aa4c2 HEAD --name-only
git -C <OWN> diff <exact source checkpoint> -- <literal reviewed paths>
git -C <OWN> show <exact base>:<literal protected/source path>
git -C <OWN> -c core.whitespace=cr-at-eol diff --check
git -C <OWN> -c core.whitespace=cr-at-eol diff --cached --check
git -C <OWN> diff --cached --name-only
```

Command-local safe.directory встречается в read inventory под sandbox identity; global
config не менялся. Полные literal argv в receipts имеют приоритет над этой краткой
таблицей форм. Непротоколированные ранние reads подтверждены tool transcript/input receipt.

Exact writes для J1/J2/J3/J4/FINAL, с разными предварительно сохранёнными literal paths:

```text
git -C <OWN> add -- <paths from LABEL-allowlist039.txt>
git -C <OWN> commit -m <actual checked block message in LABEL-git039.json>
git -C <OWN> push origin HEAD:refs/heads/experiment/m1-candidate007-observe008
git -C <OWN> rev-parse HEAD
git -C <OWN> ls-remote origin refs/heads/experiment/m1-candidate007-observe008
```

Published code checkpoints:4c4ca498abc8f224289d7bfd0b45397379620cfe;
7e64c4f5aca0740e966858b4f43920b9abbe96e7;
34cf907f9050e96fe5165c8ab7df13a8eb861b8c;
09e5f38a6fe3f2295f0c0040ab55c1c914c3234e. Final docs SHA/local=remote выдаёт
FINAL-publication039.json после push. Каждый production Jar строился из committed
checkpoint; финальные docs не меняют code blobs.

Не выполнялись reset/clean/stash/rebase/amend/restore/prune/merge/force push,
создание/удаление branch/worktree и global Git config. Exact-path scope verification,
normal non-force push и local=remote являются required delivery039.
