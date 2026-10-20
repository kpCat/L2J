# Непересекающиеся зоны

L2J: READ_ONLY. Studio source: разработка кода. Studio workspace: кандидаты/замечания/settings/review ZIP. Эти три зоны не объединять.

Проверки source-before/source-after читают только разрешённые файлы humanized/manifest, не собирают/не останавливают сервер и не копируют live DB.

Запреты программы: auto-install, auto-approve, uncontrolled overwrite, Process.Start/shell/Java/Ant/Git/DB, tools модели, динамический C#/Java/PowerShell/XML от модели, произвольные output paths, silently increasing hard bounds, silently stripping gender/persona restrictions.

Проверка junction/symlink — практический guard, не гарантия против OS-level гонки с злонамеренным другим процессом. Не писать «100% безопасно» или «невозможно сломать». Безопасность обеспечивается прежде всего отсутствием пути публикации в L2J.

API bearer token нельзя сохранять в settings/session/лог/ZIP/Git. Не сохранять полные приватные чаты пользователя в review. Исключить workspace, outputs моделей, bin/obj/.vs, caches, binaries и секреты из code-review ZIP.

Самостоятельный Git root программы проверять `git rev-parse --show-toplevel`. Если он равен L2J или его родителю — не git add/commit: вывести BLOCKED_WRONG_GIT_ROOT. Новый `.git` можно создать только в точном root Studio, если он отсутствует и не вложен в другой Git root.
При собственном origin, указывающем на kpCat/L2J, push запрещён. Без origin вернуть review ZIP. Remote не создавать/не менять без отдельного запроса.
