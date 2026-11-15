# COMMANDS015

Git разрешён прямым заданием015 и уведомлением о параллельной Studio-интеграции: bounded audit, scoped commits, сохранение feature history и ordinary non-force push. Новый Goal/ветки/worktrees/clones/PR не создавались. clean/reset/stash/rebase/force push/broad restore не применялись.

## Точные Git argv

Для Source004 общий префикс каждой команды ниже: `git -c safe.directory=D:/Codex/repos/L2J-learning-004 -C D:/Codex/repos/L2J-learning-004`. Его `-c` действует только на один вызов, глобальный git config не менялся.

```text
rev-parse --show-toplevel
symbolic-ref --short HEAD
rev-parse HEAD
status --short
worktree list --porcelain
fetch origin feature/phantom-world
rev-parse origin/feature/phantom-world
for-each-ref --format=%(refname) %(objectname) refs/heads/feature/phantom-world refs/remotes/origin/feature/phantom-world
rev-parse HEAD origin/feature/phantom-world
diff --name-only 9a4495b9dbe24f53cfe7357d43be801fd2067512 origin/feature/phantom-world -- L2J_Mobius_CT_2.6_HighFive AGENTS.md .gitattributes
diff --name-only HEAD origin/feature/phantom-world -- L2J_Mobius_CT_2.6_HighFive AGENTS.md .gitattributes
merge-base --is-ancestor HEAD origin/feature/phantom-world
merge --ff-only origin/feature/phantom-world
commit -m "fix(phantoms): treat whole-fight Party MP forecast as risk"
rev-parse HEAD HEAD^
```

В `for-each-ref` format передавался одним quoted аргументом `'--format=%(refname) %(objectname)'`. Commands status/fetch/rev-parse повторялись на фактических gates; результаты — PROGRESS.md. Remote5cff6b19efc и пустой diff server/root paths позволили fast-forward без конфликтов. Code commit91109ae9268908a4e384763befe2dee2834ddec3 имеет Studio parent5cff6b19efc140700af8950255c4dc9c13206c96.

В MAIN working directory `D:/Codex/repos/L2J-phantom-world` выполнялись bare `git rev-parse --show-toplevel`, `git symbolic-ref --short HEAD`, `git rev-parse HEAD`, `git status --short`, `git worktree list --porcelain`, а также exact docs `git diff --check -- <IMPLEMENTATION_PLAN.md PROGRESS.md>` / `git status --short -- <те же два полных module-relative пути>`. MAIN HEAD7e997636dbac9d5e1754e00c793cd6fac858b0c6 и прежний dirt сохранены. В TEST read-only inventory использовал тот же Source prefix с обоими `learning-004` заменёнными на `learning-006`: rev-parse root/HEAD, symbolic-ref, status. Первые Source/TEST проверки без ephemeral safe.directory отказали dubious ownership; повтор с локальным `-c` успешен. История/права глобально не менялись.

Exact path vector CODE8 для `diff [--stat|--check|--cached --stat] --`, `add --` и reviewer `diff [--stat] 9a4495b9dbe24f53cfe7357d43be801fd2067512 --`:

```text
L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/party/L2jPhantomPartyBackend.java
L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/gameserver/phantoms/learning/AutonomousTrainingChecks.java
L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomAutonomousTrainingNative.java
L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomLearning001ShadowNative.java
L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomPartySuite.java
L2J_Mobius_CT_2.6_HighFive/tools/phantom-runtime/training/Training.ps1
L2J_Mobius_CT_2.6_HighFive/tools/phantom-runtime/training/Test-Training.ps1
L2J_Mobius_CT_2.6_HighFive/build.xml
```

Первоначальный exact diff-stat до Runtime изменения содержал тот же vector без PhantomLearning001ShadowNative.java. Отдельно повторялся `diff --check -- L2J_Mobius_CT_2.6_HighFive/build.xml` после устранения CR только в новых строках. Reviewer выполнял только rev-parse/symbolic-ref/status и exact diff этих восьми paths.

Публикационная последовательность после code commit: `add -- <только Task015 package + report015 + COMBAT-AI-ROADMAP.md>`, `diff --cached --check -- <те же exact doc paths>`, `commit -m "docs(phantoms): record Goal015 MP proof and unsealed TRAIN diagnosis"`, fresh `fetch origin feature/phantom-world`, `rev-parse HEAD HEAD^ origin/feature/phantom-world`, `merge-base --is-ancestor origin/feature/phantom-world HEAD`, `push origin feature/phantom-world`, `rev-parse HEAD`, `status --short`, `ls-tree -r HEAD -- <exact published package/report/roadmap/CODE8>`. Exact doc vector и terminal hashes фиксируются внешним publication/readback receipt. Это разрешённые операции публикации; новые Studio commits при появлении не перезаписываются. GitHub connector независимо читает branch, commit/parent, compare и полные file bodies.

## Build и DB-free gates

Из SOURCE004, последовательно для Ant:

```powershell
ant -f .\L2J_Mobius_CT_2.6_HighFive\build.xml phantom-party-mp015-test
ant -f .\L2J_Mobius_CT_2.6_HighFive\build.xml phantom-learning015-test
ant -f .\L2J_Mobius_CT_2.6_HighFive\build.xml jar
ant -f .\L2J_Mobius_CT_2.6_HighFive\build.xml phantom-humanized-v3-content-validate
powershell -NoProfile -ExecutionPolicy Bypass -File .\L2J_Mobius_CT_2.6_HighFive\tools\phantom-runtime\training\Test-Training.ps1 -Readiness015
powershell -NoProfile -ExecutionPolicy Bypass -File .\L2J_Mobius_CT_2.6_HighFive\tools\phantom-runtime\training\Test-Training.ps1 -Readiness014
powershell -NoProfile -ExecutionPolicy Bypass -File .\L2J_Mobius_CT_2.6_HighFive\tools\phantom-runtime\training\Test-Training.ps1 -Headless013
```

RED/missing-contract/behavioral-wiring/worker-cap/director/mode/batch tests запускались этими же scoped routes. Ожидаемые AssertionError/Require failures и отдельные fixture/compile ошибки различены в PROGRESS/REVIEW_RESULT. Финальные exit0, raw logs сохранены вне checkout. Общий ant verify, Studio build и unrelated product smokes не запускались.

## TEST lifecycle — ровно два readiness

Working directory TEST006, own root `D:/Codex/repos/L2J-learning-006/.phantom-local/learning002/train`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\L2J_Mobius_CT_2.6_HighFive\tools\phantom-runtime\training\Training.ps1 -Action Preflight -Autonomous -Root D:/Codex/repos/L2J-learning-006/.phantom-local/learning002/train
powershell -NoProfile -ExecutionPolicy Bypass -File .\L2J_Mobius_CT_2.6_HighFive\tools\phantom-runtime\training\Training.ps1 -Action Prepare -Autonomous -Readiness015Session 1 -Root D:/Codex/repos/L2J-learning-006/.phantom-local/learning002/train
powershell -NoProfile -ExecutionPolicy Bypass -File .\L2J_Mobius_CT_2.6_HighFive\tools\phantom-runtime\training\Training.ps1 -Action Prepare -Autonomous -Readiness015Session 2 -Root D:/Codex/repos/L2J-learning-006/.phantom-local/learning002/train
powershell -NoProfile -ExecutionPolicy Bypass -File .\L2J_Mobius_CT_2.6_HighFive\tools\phantom-runtime\training\Training.ps1 -Action Stop -Autonomous -Root D:/Codex/repos/L2J-learning-006/.phantom-local/learning002/train
powershell -NoProfile -ExecutionPolicy Bypass -File .\L2J_Mobius_CT_2.6_HighFive\tools\phantom-runtime\training\Training.ps1 -Action Export -Autonomous -Root D:/Codex/repos/L2J-learning-006/.phantom-local/learning002/train
```

Preflight/Export повторялись для G0/postflight. Export не readiness и не боевой запуск: canonical SQL start/stop с ровно5readonly SELECT, отдельные before/after hashes. Windows PowerShell Security module импортирован явно для штатного Get-Acl. Первый G0 Source-script/TEST-root вызов корректно отказал EXACT_OWN_ROOT_REQUIRED; далее использовался только собственный TEST script, guard не обходился.

Внешняя evidence directory: `C:/Users/ZBook/.codex/visualizations/2026/11/15/01a1dfeb-ba30-74c0-b541-8642c3feb2ec`. Там Sync-Test015.ps1/Configure-Readiness015.ps1 выполнили только exact8 byte backups/SHA и canonical OFF config writer; Read-Safety.ps1 и Invoke-ColdLineage.ps1 использовали существующие Owner guards. Private credentials и SQL TSV не помещены в Git. S1/S2 terminal receipts и опубликованные aggregate hashes связаны в EVIDENCE.json. Никаких GameServer Start/Run/FIT/EVAL команд015 нет.
