# Git / worktree

Required base `0205d04bc7763fafcbb776e6da8887c1f2912d8b`, target `experiment/m1-candidate007-observe008`.
Основной `C:\Users\ZBook\L2J_Mobius` и остальные worktrees/diffs — FOREIGN.
Разрешён новый `C:\Users\ZBook\.codex\worktrees\m1-native-farm-022\L2J_Mobius`.

Read-only fetch/rev-parse/status/worktree list/history разрешены. Если branch занята,
создать detached worktree от exact base. Не переключать чужой branch.
Task-пакет разрешено скопировать из main checkout в isolated worktree точным task-путём,
без переноса иных uncommitted files. Никакого git add . /reset/clean/stash/rebase/force.

Явное разрешение данной задачи на writes: только перечисленные source/test paths и
TASK022 артефакты, exact-path add/commit и normal push
`git push origin HEAD:refs/heads/experiment/m1-candidate007-observe008`.
Это узкое разрешение на task-owned Git writes при обычном repo read-only правиле;
чужие файлы остаются read-only. Реальные недоступные permissions не обходить.

Перед каждым push проверить remote. При другом HEAD не force/rebase: зафиксировать
конкурирующий SHA и STOP publication conflict. До первого fix допустим только initial
base; далее разрешены собственные committed descendants в этом же TASK022.

Перед commit: exact allowlist, diff --check, сравнение semantic diff с SOURCE_MAP,
mojibake/escaped Cyrillic проверки. Генерируемые логи не исправлять ради encoding guard;
отдельно маркировать. Все пути команд и exit codes — GIT_USAGE.md, stdout Git не засорять
полным многомегабайтным diff.

Commit/push обязательны при GREEN/BLOCKED/FAILED для безопасного task-owned результата.
Если safe patch не принят — evidence-only commit, candidate.patch только как непринятый
артефакт, не deploy. Финальный report commit после source commit допустим; отдельный
commit ради записи собственного SHA не делать. Итоговый remoteSHA в сообщении.
