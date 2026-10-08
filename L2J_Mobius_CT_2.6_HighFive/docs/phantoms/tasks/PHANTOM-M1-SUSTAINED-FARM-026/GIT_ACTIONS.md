# GIT_ACTIONS026

Git-команды разрешены прямым запросом пользователя и TASK.md: bounded read/history/source guard, exact-path add/commit, normal push. Они выполнялись в собственном detached worktree; initial main inspection был только read-only. Main/foreign/kpCat history/branch/файлы не изменялись. Внутренние команды managed create_worktree не выдаются за shell-команды этой сессии.

Own root: `C:\Users\ZBook\.codex\worktrees\m1-sustained-026\L2J_Mobius`.
Required base: `6ebe1d93f4ee168cd8952f0416dc920f26c430ac`.
Frozen code R6: `cb2d9b08d96e4f2386836b40d5ae87ceba9fcf63`.

Применённые read-only формы команд (повторялись только для указанного scope):

```text
git status --short
git rev-parse HEAD
git rev-parse --show-toplevel
git rev-parse --git-dir
git rev-parse --git-common-dir
git config --get core.autocrlf
git remote -v
git worktree list --porcelain
git log --format="%H %s" 6ebe1d93f4ee168cd8952f0416dc920f26c430ac..HEAD
git log --format="%H" --name-only 6ebe1d93f4ee168cd8952f0416dc920f26c430ac..HEAD
git diff 6ebe1d93f4ee168cd8952f0416dc920f26c430ac --name-only
git diff 6ebe1d93f4ee168cd8952f0416dc920f26c430ac --stat
git diff --check
git diff --cached --check
git diff --cached --name-only
git diff --cached 6ebe1d93f4ee168cd8952f0416dc920f26c430ac --name-only
git diff --cached --stat
git ls-files --stage -z -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-SUSTAINED-FARM-026
git ls-remote --heads origin refs/heads/experiment/m1-candidate007-observe008
git merge-base --is-ancestor 6ebe1d93f4ee168cd8952f0416dc920f26c430ac HEAD
```

Scoped `git diff BASE -- <paths>` и `git diff R6 -- <paths>` использовались для просмотра конкретных изменённых source/test файлов из FINAL_SOURCE_ALLOWLIST.txt и проверки отсутствия product изменений после freeze. История и exact commit paths проверены относительно required base. Initial HEAD/parent inspection подтверждал required base и TASK025 parent; это read-only inspection, а не checkout main.

Existing Prepare024 adapter внутри own026 runtime применяет три exact read-only source exports, плюс code SHA read. `<module>` — собственный `L2J_Mobius_CT_2.6_HighFive`; точные population paths подтверждены существующим Prepare024 исходником и воспроизведены в OWNED_ASSET_GIT_READS.txt. Остальные304 stock assets/geodata копировались обычным файловым чтением/hash comparison, не304 командами git show.

Source commits (точные staged пути каждого commit в SOURCE_COMMIT_PATHS.txt; все из allowlist плюс FIX_LEDGER.md):

| Commit | Команда |
|---|---|
|13ddd45d78d0cfe3b61e1268e68aebbb0a048802|`git commit -m "phantom(task-026): continue lawful local farm after standpoint failure"`|
|b45db7ebb591e0c2680469e42649acd872a44c75|`git commit -m "phantom(task-026): bind death and reward evidence to exact native recipients"`|
|9b2f72955280ed1a8e67ee99eadca8868f34dfdd|`git commit -m "phantom(task-026): recover native MP without aborting earned casts"`|
|4ef248030365d4fc126d6bd3f8084daa54a3b37f|`git commit -m "phantom(task-026): retain exact farm session through planner reentry"`|
|3c7d71e68e6b997c450351593bdeca52e78e7be8|`git commit -m "test(phantoms): preserve lawful reward fixture prerequisites for task026"`|
|cb2d9b08d96e4f2386836b40d5ae87ceba9fcf63|`git commit -m "fix(phantoms): retire undamaged expired native target generations"`|

В каждом случае `git add -- <exact listed paths>` был ограничен собственными перечисленными source/test paths и FIX_LEDGER. Source frozen до final scenes. Финальная publication:

```text
git add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-SUSTAINED-FARM-026
git commit -m "docs(phantoms): seal task026 failed sustained farm evidence"
git push origin HEAD:refs/heads/experiment/m1-candidate007-observe008
```

Обычное exact-task staging включило все2879 файлов первого inventory, включая native `.properties` witnesses; forced add не применялся. FINAL_FORCE_ADDED_PATHS.txt фиксирует NONE. Финальные index guards проверяют task-only staged paths, source freeze, отсутствие secrets/binaries; `git ls-files --stage -z -- <exact task>` используется только для сравнения staged blob hashes с фактическими сохранёнными bytes. `git diff --cached --check -- <task md/ps1/observer/verifier paths>` подтвердил authored-document/tool whitespace. Raw logs сохраняются без нормализации. Remote HEAD проверяется `ls-remote` против собственного `rev-parse HEAD`; publication SHA сообщается в финальном чате. Commit не может включать свой собственный SHA, поэтому RESULT сохраняет frozen product SHA и ссылку на внешний verified publication receipt.

Task-local .gitattributes сохраняет package bytes без autocrlf преобразования, чтобы native receipt/observer/raw artifact SHA256 оставались точными при публикации. Ни root attributes, ни global/local git config не изменялись; `git config --get core.autocrlf` был read-only.

`git add .`, commit/push main, branch checkout, merge/rebase/reset/restore/clean/stash и force push не применялись. Старые contract024/025 и PLAY не изменялись. PR не создавался.
