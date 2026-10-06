# Использование Git — TASK019

Разрешение: прямой objective пользователя и GIT.md TASK019 разрешают read/history, isolated worktree, exact-path add/commit и normal push. Основной checkout не переключался; пользовательские изменения не включаются. Локальная experiment-ветка занята другим dirty worktree, поэтому новая изоляция имеет detached HEAD на required base; remote experiment-ветка уже на этом base.

Выполненные команды read-first/scope inspection (повторные одинаковые вызовы сгруппированы):

```text
git branch --show-current
git rev-parse HEAD
git status --porcelain=v1
git status --porcelain=v1 -uno
git worktree list --porcelain
git rev-parse experiment/m1-candidate007-observe008
git log -6 --oneline d153de95fd0fd8b678f975964179aeba85c57336
git log -3 --oneline experiment/m1-candidate007-observe008
git -C C:\Users\ZBook\.codex\worktrees\m1-observe-008\L2J_Mobius status --porcelain=v1 -uno
git rev-parse --abbrev-ref experiment/m1-candidate007-observe008@{upstream}
git merge-base --is-ancestor experiment/m1-candidate007-observe008 d153de95fd0fd8b678f975964179aeba85c57336
git -c safe.directory=C:/Users/ZBook/.codex/worktrees/m1-observe-008/L2J_Mobius -C C:\Users\ZBook\.codex\worktrees\m1-observe-008\L2J_Mobius status --porcelain=v1 -uno
git ls-remote origin refs/heads/experiment/m1-candidate007-observe008
git -C C:\Users\ZBook\.codex\worktrees\m1-visible-farm-019\L2J_Mobius diff --name-only HEAD -- L2J_Mobius_CT_2.6_HighFive/java L2J_Mobius_CT_2.6_HighFive/test L2J_Mobius_CT_2.6_HighFive/build.xml
```

Первый status внешнего старого worktree отклонён Git dubious ownership; выполнен только per-command safe.directory, global config не менялся. Upstream локальной experiment-ветки не настроен. Remote SHA отдельно подтверждён: d153de95fd0fd8b678f975964179aeba85c57336.

Создание разрешённой изоляции:

```text
git worktree add --detach C:\Users\ZBook\.codex\worktrees\m1-visible-farm-019\L2J_Mobius d153de95fd0fd8b678f975964179aeba85c57336
```

Команды, выполненные переиспользованными Prepare-Runtime.ps1 / Restore-Private-Catalogs.py для идентичности source и pinned catalogs:

```text
git -C C:\Users\ZBook\.codex\worktrees\m1-visible-farm-019\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive rev-parse HEAD
git show HEAD:L2J_Mobius_CT_2.6_HighFive/dist/game/data/phantoms/population/high-five-population-v1.xml
git show HEAD:L2J_Mobius_CT_2.6_HighFive/dist/game/data/phantoms/population/high-five-population-v2.xml
git show HEAD:L2J_Mobius_CT_2.6_HighFive/dist/game/data/phantoms/population/high-five-ecology-v1.xml
```

Online0/save и graceful stop подтверждены. Основной artifact commit и normal push выполнены; remote SHA=010b3bed2f61736837942fb4c4a77e66ff49d2bf. Private runtime, credentials, бинарники и исходники других хроник не входят в artifact scope.

## Exact artifact verification перед commit

Выполнено: staged scope ровно23 exact paths, production/test/build diff отсутствует; evidence/save/stop и обе encoding-проверки PASS.

```text
git add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019/ACCEPTANCE.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019/DIAGNOSIS.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019/FIX.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019/GIT.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019/GOAL.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019/HANDOFF.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019/MODEL.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019/PACKAGE_MANIFEST.json L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019/PLAN.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019/RESULT_TEMPLATE.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019/TESTS.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019/VISIBLE_DIAGNOSIS.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019/EVIDENCE019A.json L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019/RESULT.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019/PROGRESS.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019/GIT_USAGE.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019/BASE_BUILD.log L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019/GRACEFUL_STOP.log L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019/STOP_VERIFY.log L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019/LOGOUT_BEFORE_STOP.tsv L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019/LOGOUT_AFTER_STOP.tsv L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019/ARTIFACT_ALLOWLIST.json L2J_Mobius_CT_2.6_HighFive/docs/phantoms/reports/PHANTOM-M1-VISIBLE-FARM-019.md
git diff --cached --name-only
git diff --name-only HEAD
git diff --name-only HEAD -- L2J_Mobius_CT_2.6_HighFive/java L2J_Mobius_CT_2.6_HighFive/test L2J_Mobius_CT_2.6_HighFive/build.xml
git diff --check
git diff --cached --check
git diff --cached --stat
git diff --cached -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019 L2J_Mobius_CT_2.6_HighFive/docs/phantoms/reports/PHANTOM-M1-VISIBLE-FARM-019.md
```

Большой diff был truncated; evidence JSON и середина task package отдельно дочитаны через git diff --cached -- с перечисленными exact paths. Дополнительный receipt commit после первого normal push фиксирует фактически полученный основной artifact SHA и результат push; production changes остаются0.

## Commit и normal push

Основной artifact SHA: 010b3bed2f61736837942fb4c4a77e66ff49d2bf. Первый normal push PASS; ls-remote совпал с SHA. Дополнительный receipt commit имеет только четыре metadata/doc paths, чтобы сохранить фактический основной SHA и результат push. Итоговый receipt SHA и remote verification приводятся в финальном сообщении.

```text
git commit -m 'phantom(task-019): record blocked visible farm diagnosis' -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019/ACCEPTANCE.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019/DIAGNOSIS.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019/FIX.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019/GIT.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019/GOAL.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019/HANDOFF.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019/MODEL.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019/PACKAGE_MANIFEST.json L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019/PLAN.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019/RESULT_TEMPLATE.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019/TESTS.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019/VISIBLE_DIAGNOSIS.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019/EVIDENCE019A.json L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019/RESULT.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019/PROGRESS.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019/GIT_USAGE.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019/BASE_BUILD.log L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019/GRACEFUL_STOP.log L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019/STOP_VERIFY.log L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019/LOGOUT_BEFORE_STOP.tsv L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019/LOGOUT_AFTER_STOP.tsv L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019/ARTIFACT_ALLOWLIST.json L2J_Mobius_CT_2.6_HighFive/docs/phantoms/reports/PHANTOM-M1-VISIBLE-FARM-019.md
git rev-parse HEAD
git push origin HEAD:refs/heads/experiment/m1-candidate007-observe008
git ls-remote origin refs/heads/experiment/m1-candidate007-observe008
git add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019/RESULT.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019/PROGRESS.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019/GIT_USAGE.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/reports/PHANTOM-M1-VISIBLE-FARM-019.md
git diff --cached --name-only
git diff --name-only HEAD
git diff --check
git diff --cached --check
git diff --cached -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019/RESULT.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019/PROGRESS.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019/GIT_USAGE.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/reports/PHANTOM-M1-VISIBLE-FARM-019.md
git commit -m 'docs(phantoms): record task019 publication receipt' -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019/RESULT.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019/PROGRESS.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019/GIT_USAGE.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/reports/PHANTOM-M1-VISIBLE-FARM-019.md
git push origin HEAD:refs/heads/experiment/m1-candidate007-observe008
git ls-remote origin refs/heads/experiment/m1-candidate007-observe008
git status --porcelain=v1 -uno
git diff --name-only d153de95fd0fd8b678f975964179aeba85c57336 HEAD
```
