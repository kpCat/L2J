# GIT_POLICY013 — никаких новых WIP

Remote `https://github.com/kpCat/L2J`; только `feature/phantom-world`, исходный проверенный SHA `52aac9e0274a63906a9fe5053611b8daf1aac778` (проверить свежесть!). `master` не менять.

- Основной `D:\Codex\repos\L2J-phantom-world` может быть dirty/на старом HEAD; **не переключать/не обнулять** и не удалять уникальные данные.
- Existing `L2J-learning-004` для source, `L2J-learning-006` для runtime. Никаких новых clone/worktree/branch, если нет отдельно доказанной необходимости и разрешения.
- Пакет из ZIP может оказаться в старом/грязном checkout: переносить только exact task directory в source004, сначала manifest SHA. Не переносить туда весь `docs` или временные raw SQL.
- До записи: `git remote -v`, `fetch`, `status`, `branch`, `worktree list`, `merge-base`, exact changes. Исторические отчёты/zip/models не переписывать.
- Явное разрешение на exact scoped `commit` и обычный `git push origin feature/phantom-world` ТОЛЬКО этой задачи. Не делать `reset --hard`, `clean`, `stash` всего repo, rebase, squash, force push, `git add .`, удаление branches/stash/worktrees.
- Private `.phantom-local`, SQL, credentials, actor snapshots, real model binaries, Geo, logs огромных размеров не добавлять в Git. Public evidence — обезличенные hash/metrics/small receipts.
- Отчёт: exact prior/final SHA, full changed source filenames+size/hashes, regressions, status, scoped push, независимый GitHub readback. Проверить все UTF-8 и кириллицу (mojibake/escaped Cyrillic).
