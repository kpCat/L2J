# Git safety / publication032

Base `df6ee99c71a4c8b07ad05545b200e113d3b0d3c9`, only `experiment/m1-candidate007-observe008` normal push.
Сначала remote read и source identity. Unexpected concurrent remote advance не лечить
rebase/force. Если base изменён другим исполнителем — остановить writes и сохранить
BASE_MOVED diagnosis; не стартовать параллельное изменение.

Own isolated worktree may be detached exactbase. Основной/занятый checkout не менять.
Allowed: status/diff/log/show/rev-parse/ls-remote/fetch, worktree add exact new path,
exact-path add/commit, normal `push origin HEAD:refs/heads/experiment/m1-candidate007-observe008`.
No git add ., reset,clean,stash,rebase,amend,force; no чужой diff или VS tooling edits.

Before every publication: перечислить concrete changed source/test/task paths, review
no private raw/credentials/JFR/SQL dump, diff-check preserving originalEOL/encoding,
раздельный mojibake и escaped Cyrillic guard. Task raw evidence localimmutable.

Не normalizе огромные Java/build files. В diff только semantic участок/комментарий.
Если собственный предыдущий experimental fix опровергнут, exact contextual edit
сохраняющий чужой код, отдельный ordinary commit; не history rewrite.

Фиксировать runtime sourceSHA отдельно от reportSHA; report-only updates допустимы.
Final commit+normalpush даже при BLOCKED/FAILED. Если платформа блокирует публикацию,
честный PUBLICATION_BLOCKED, не обход. Команды сохранить точно без passwords.
