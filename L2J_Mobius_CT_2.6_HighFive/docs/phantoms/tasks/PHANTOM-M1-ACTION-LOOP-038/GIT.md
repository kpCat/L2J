# GIT038 — разрешение при отправке пользователем GOAL

BASE: `aa63dafb79e19a531b5620af195764c823bd9539`.
Remote: `experiment/m1-candidate007-observe008`.
OWN: существующий detached phantom-dev; MAIN только для чтения.

READ: status,rev-parse,worktree list,diff,show,log,ls-remote,ls-files,check-ignore,merge-base.
Сначала HEAD/remote равны BASE. При mismatch — BLOCKED_BASE, без silent switch/reset.
Не требуется обновлять одноимённую локальную ветку в старом worktree: push из detached разрешён.
Git blob из SOURCE_MAP сравнивать с BASE:path через Git, не самодельной LF-only нормализацией.

WRITE только в OWN, literal approved paths:
```
git -C OWN add -- EXACT_LITERAL_PATHS
git -c core.whitespace=cr-at-eol -C OWN diff --cached --check
git -C OWN diff --cached --name-only
git -C OWN commit -m "TASK038: <фактическое изменение>"
git -C OWN push origin HEAD:refs/heads/experiment/m1-candidate007-observe008
```
OWN/EXACT_LITERAL_PATHS — обозначения, не готовые аргументы; подставить проверенные значения.
Фактические команды/аргументы/UTC и результаты сохранить. Candidate commit перед clean build;
финальный report commit не меняет Java после freeze. Remote advance — не force/merge,
а сохранённый local commit и точная граница отказа публикации.

Запрещены `git add .`, add-A, широкие wildcards, force-add ignored, reset, clean, stash,
restore, rebase, amend, force push, new worktree, удаление веток/worktree, prune и merge.
Main/foreign не изменять. Global config не менять. Scoped safe.directory допустим только
для установленного собственного каталога при реальной проблеме ownership.

SOURCE_MAP — максимальный разрешённый scope, не приказ изменить все файлы. Не больше10
production путей (включая новые) из этого списка. Дополнительные два или три класса ради
удобства не создавать: nested records/существующие types предпочтительнее.
Task-family038 — отдельное bounded исключение для документов и helpers, не для production.
Private ops/raw/config/secrets не stage. UTF-8/EOL сохранить, отдельно проверить mojibake и
escaped Cyrillic. Любой результат требует exact commit+normal push, кроме реального
конфликта/отказа доступа; отказ публикации не скрывать.
