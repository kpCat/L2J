# Git045 — разрешение и exact команды

Прямой user /goal, TASK045 sections5/18 и HANDOFF разрешили read-first Git,
exact-path ordinary commits и normal non-force push. OWN всегда
C:/Users/ZBook/.codex/worktrees/phantom-dev/L2J_Mobius. HEAD detached сохранён.

Вызваны read-only формы (для bounded input/scope/review):

- git rev-parse --show-toplevel
- git rev-parse HEAD
- git status --short --untracked-files=normal
- git status --short --untracked-files=no
- git branch --show-current
- git rev-parse --abbrev-ref --symbolic-full-name @{upstream} — отказ: detached HEAD
- git remote -v
- git ls-remote origin refs/heads/experiment/m1-candidate007-observe008
- git diff --check
- git diff --stat
- git diff --name-only
- git diff --cached --check
- git diff --cached --stat
- git diff --cached --name-only
- git diff --cached --name-only — также перед exact add, чтобы исключить foreign staged
- git check-attr text eol whitespace -- L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java
- git show a6fde1df1b1785b93e2248a20786c61878e05428:L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java — byte-preserving EOL splice
- git show HEAD:L2J_Mobius_CT_2.6_HighFive/dist/game/data/phantoms/population/high-five-population-v1.xml
- git show HEAD:L2J_Mobius_CT_2.6_HighFive/dist/game/data/phantoms/population/high-five-population-v2.xml
- git show HEAD:L2J_Mobius_CT_2.6_HighFive/dist/game/data/phantoms/population/high-five-ecology-v1.xml
- git show a6fde1df1b1785b93e2248a20786c61878e05428:<каждый из12 exact protectedProduct paths в evidence/PRESERVATION045.json> — unchanged normalized byte comparison
- git diff -- <exact four Java/TEST paths из evidence/SOURCE_ALLOWLIST045.txt>; также отдельные пары producer+PopulationSuite и SchedulerSuite+TEST environment
- git diff -- <exact045 SignalSnapshot045.java> — helper review
- git diff --binary -- <exact four Java/TEST paths> — первоначальный локальный patch
- git diff a6fde1df1b1785b93e2248a20786c61878e05428 --unified=0 --binary -- <тот же exact four paths> — compact patch без whitespace context

Final scope дополнительно использует только diff/status/ls-remote формы выше;
Java/TEST equality against native source0a074 проверяется exact four paths.
Все actual path expansions входят в allowlists/receipts, не broad glob.

Publication:

1. git add -- <39 строк SOURCE_ALLOWLIST045.txt плюс SOURCE_ALLOWLIST045.txt/SOURCE_GATE045.json>
2. git commit -m "phantom(task-045): separate calendar activity from native locality"
3. git push origin HEAD:refs/heads/experiment/m1-candidate007-observe008
4. git add -- <Observe032.ps1,SignalSnapshot045.java,Run-Native045.ps1,PRECONDITIONS045.md,Adapt045.py внутри task045>
5. git commit -m "phantom(task-045): measure ordinary background due without extending sessions"
6. git push origin HEAD:refs/heads/experiment/m1-candidate007-observe008
7. Final git add -- <exact final ALLOWLIST045.txt, только remaining task/helper/result paths>
8. Final ordinary git commit и тот же normal git push; argv/exit/HEAD/remote/clean — local publication receipt.

Первый add отказал на PowerShell array concatenation; staging не изменилось.
Следующий staged diff-check отказал на context whitespace нового patch-file;
patch пересоздан --unified=0, exact add повторён. Отказы сохранены в отчёте.
Первый sandbox ls-remote отказал в network; elevated read-only повтор подтвердил
BASE. Sandbox Git иногда отвергал ownership; вместо global safe.directory config
использовалось разрешённое elevated выполнение в явном OWN.

Не вызывались force/reset/restore/clean/rebase/merge/amend или изменение ветки.
Old untracked, DB/raw/JAR/secrets не staged, не удалены и не включены в review ZIP.
