# GIT037 — прямое разрешение в границах задачи

Пользователь одобряет exact-path commit+normal push при любом исходе.
READ:worktree list,rev-parse,status,diff,show,log,ls-remote,merge-base --is-ancestor,
ls-files/check-ignore, локальные исходники и remote pinned data. Не менять global git config.
BASE=`0b47eea99c0d7d6190abc1eaa6de70996ca6dba9`.
Remote=`experiment/m1-candidate007-observe008`; worktree существующий detachedphantom-dev.

Сначала OWN HEAD и remote HEAD должны равняться BASE. MAIN имеет свою ветку и чужие diff:
не переключать,не fetch/reset там,не merge. BASE mismatch→BLOCKED_BASE, не silent switch.
Импортировать task037 packet из MAIN только после проверки MANIFEST; новые task files не
делают production dirty. Чужие untracked/ignored files не stage/удалять.

Разрешено:
`git -C OWN add -- <проверенный literal список source/test/task037 paths>`
`git -c core.whitespace=cr-at-eol -C OWN diff --cached --check`
`git -C OWN diff --cached --name-only`
`git -C OWN commit -m "TASK037: <фактическое изменение>"`
`git -C OWN push origin HEAD:refs/heads/experiment/m1-candidate007-observe008`
`git -C OWN rev-parse HEAD`
`git -C OWN ls-remote origin refs/heads/experiment/m1-candidate007-observe008`
OWN/angle-brackets выше — описание команды; заменить реальными аргументами из own manifest.

Никаких add.,add-A,broad wildcard,force add ignored runtime,force push,reset,clean,stash,rebase,
amend,prune,merge,branch/worktree delete. Новые worktree не создавать. Normalpush conflict→сохранить
commit и report, не перезаписывать чужую remote историю. Публикационный отказ не маскировать.

До freeze записать точный stage allowlist и diff; protected035/036blobs сравнить с BASE.
Task-family docs/tools — bounded exception к малому числу файлов, только своя folder;
это не разрешение расширять production scope. Не более семи перечисленных conditional production paths за задачу;
при большей доказанной необходимости STOP_SCOPE, не новаядесятка.
Final logs/helper/archive — private external; публичные отчётыбез secrets/полных config/SQLdumps.
Исходные переносы строк и UTF-8 сохранять; отдельно mojibake/escapedCyrillic, не массовый formatter.

Один final RESULT_HANDOFF037/EVIDENCE_INDEX037/CLEANUP037 и GIT_COMMANDS037.md с полными
фактическими командами/аргументами. Final SHA можно связать внешним publication.json после push,
не создавать бесконечные коммиты подтверждения. Reusable worktree оставить.
