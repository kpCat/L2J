# Git043 — scope and commands

Разрешение: прямой пользовательский `/goal`, TASK043 mandatory input audit/§18,
HANDOFF043 Git safety и exact-path публикация. OWN остаётся detached; команды
не меняли branch/history вне обычных новых commit. Нет reset/restore/clean/force/merge/rebase.

Все команды исполнялись для
`C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius`.
Read-only проверка входа: `git status --short`,
`git status --short --untracked-files=no`, `git rev-parse HEAD`,
`git rev-parse --abbrev-ref HEAD`, `git rev-parse --abbrev-ref '@{upstream}'` (detached upstream отсутствует;
failed result не использован как evidence),
`git ls-remote origin refs/heads/experiment/m1-candidate007-observe008`.
Начальный sandbox network failure не принят за проверку remote; authorized normal
ls-remote подтвердил BASE до namespace creation.

Immutable config inventory normal helpers использовал read-only:

```text
git -C C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius show HEAD:L2J_Mobius_CT_2.6_HighFive/dist/game/data/phantoms/population/high-five-population-v1.xml
git -C C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius show HEAD:L2J_Mobius_CT_2.6_HighFive/dist/game/data/phantoms/population/high-five-population-v2.xml
git -C C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius show HEAD:L2J_Mobius_CT_2.6_HighFive/dist/game/data/phantoms/population/high-five-ecology-v1.xml
```

Эти три точных show и runner HEAD записаны в private `.phantom-local/ops043/GIT043.jsonl`.
Они читали baseline config bytes, не меняли product configuration. Три inherited
helper `git show` вышли за буквальный перечень read-only команд HANDOFF043
(status/HEAD/ls-remote/diff): это process deviation, а не разрешённая mutation.
Дальнейшая публикация использует только перечисленные exact команды.
Semantic reuse commands (точные argv в evidence/G2_REUSE043.json):

```text
git diff --name-only 92400bebb9126ec4364ac57b05f8c453fe42690d 9a378d0e8984b3673b41538a9f5f9c9823507317 -- L2J_Mobius_CT_2.6_HighFive/java L2J_Mobius_CT_2.6_HighFive/dist/game/config L2J_Mobius_CT_2.6_HighFive/dist/game/data/phantoms
git diff --name-only 9a378d0e8984b3673b41538a9f5f9c9823507317 -- L2J_Mobius_CT_2.6_HighFive/java L2J_Mobius_CT_2.6_HighFive/dist/game/config L2J_Mobius_CT_2.6_HighFive/dist/game/data/phantoms
git diff 9a378d0e8984b3673b41538a9f5f9c9823507317 -- L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java
```

Пробная module-relative команда
`git diff --stat -- L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java`
не использовалась для semantic PASS;
повторная repo-relative проверка имеет явные пути выше. Preparatory one-off read calls
не имели общего argv журнала; этот документ не заявляет его полноту задним числом.

Publisher exact argv/exitCode записывает в PUBLICATION1_043.json/GIT_COMMANDS043.json
и final DELIVERY043.json. Команды publisher:

```text
git rev-parse HEAD
git ls-remote origin refs/heads/experiment/m1-candidate007-observe008
git diff --cached --name-only
git diff --name-only
git diff 9a378d0e8984b3673b41538a9f5f9c9823507317 -- L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java
git add --pathspec-from-file=C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\.phantom-local\ops043\publication-pathspec043.txt
git -c core.whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol diff --cached --check -- . :(exclude)L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-SOFT-HANDOFF-043/SOURCE_DIFF043.patch :(exclude)L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-SOFT-HANDOFF-043/proposals/ops043/Stop-ExactOwnedGracefully.ps1
git commit -m "phantom(task-043): preserve bounded native handoff proofs and gates"
git commit -m "phantom(task-043): verify final review publication receipts"
git commit -m "phantom(task-043): correct exact inspection command inventory"
git push origin HEAD:refs/heads/experiment/m1-candidate007-observe008
git status --porcelain=v1 --untracked-files=no
```

Каждому publisher argv добавляется `git -C` exact OWN. Первый commit содержит exact
reviewed inventory, второй — final reports/ZIP/publication receipt без amend/force.
Final post-push receipt остаётся local вне собственного ZIP/commit, чтобы не заявлять
несуществующий self-referential final hash. HEAD=remote проверяется после каждого push.

Первый phase1 прекратился до commit на diff --cached --check (exit2): captured
PowerShell failure traces A1/A2/B1/D1 содержат generated trailing blank EOF. Повторный
read-only exact diff --cached --check показал эти четыре строки. В окончательной
команде добавлены четыре exact :(exclude) для immutable episode-failure.txt;
исходные trace bytes не редактировались. Остальные staged paths прошли guard.
Никакой rollback/reset index/history не выполнялся; resume допускает только exact allowlist.

После второго push argv сверены с actual command execution metadata текущего chat:
вариант upstream/HEAD и module-relative diff записаны буквально. Это третий обычный
scoped audit-only commit; final post-push receipt — DELIVERY_AUDIT043.json.
