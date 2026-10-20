# Safety / Git contract PSS-003

## Source write embargo

Protected local directory: `C:\Users\ZBook\L2J_Mobius\` (любой файл, включая High Five, `.phantom-local`, data, Java, jars, configs, server and DB). Protected remote: `https://github.com/kpCat/L2J` вся хроника. Ни одного filesystem write, Ant build, git checkout/commit/push, test DB provision, `git clean/reset` и даже test logs внутри этой области. В `kpCat/L2J` допустимо только чтение ограниченных контрактных файлов через GitHub. Реальная Java validation — строго **в отдельной физической копии внутри Studio workspace**; нет junction/symlink на protected paths.

## Local app security

- GUI не содержит `Process.Start`/запуск shell/Ant/Java/Git и не импортирует новые зависимости. Java oracle выполняется отдельно с явно запущенным operator скриптом, физически из isolated root.
- Model API локальный, no tools/function execution, no retries. Не допускать модельный текст как путь, исполняемое выражение, команду или XML вне стандартного serializer.
- Java script должен иметь narrow explicit allowlist read/copy paths, source fingerprint guard before+after; copied files не содержат DB configs, secrets, `.git`, user personal profile, worktree secrets. Предел копирования и дискового бюджета фиксируется в отчёте; если не удаётся изолировать, `BLOCKED_JAVA`.
- Контент-аудит не включает raw prompt/token/model response в Git/logs; fake fixtures допустимы.
- Обычный экспорт REVIEW_ONLY JSON внутри приложения не запрещён. Но **перестать создавать review ZIP от Codex** и убрать это требование из AGENTS.md. Отчёты и код проверяет ассистент только по GitHub.

## Git

1. Read-only `git status --porcelain=v1 -uall`, `git rev-parse HEAD`, `git remote -v`, `git ls-remote origin refs/heads/main` и repo-root. Remote обязан быть `https://github.com/kpCat/PhantomSemanticStudio.git`, не L2J. Expected upstream SHA exact `60d48348687467c3724ae2bb010ef62987c58d4e`.
2. Task ZIP добавляет `docs/tasks/PSS-003/*` в рабочую директорию: их присутствие допускается как единственные подготовленные untracked files. Любой другой user dirt сохранить; не stage пользовательское. Не `git add .`, only exact paths; index/diff check.
3. После tests и report обычный commit `feat(pss): stage humanized XML in isolated workspace` (или честный blocked checkpoint); ordinary `git push origin HEAD:refs/heads/main`, remote SHA verified equal. Если origin/main изменился — BLOCKED_REMOTE_DIVERGENCE, no force/no rebase/no silent merge. При BLOCKED безопасная работа и evidence всё равно коммитятся/пушатся, если fast-forward сохраняется.
4. Не нужен source archive/review ZIP. Не запускать `scripts/New-ReviewBundle.ps1`. Local artifacts от Java/staging остаются ignored; в Git только код/tests/docs/sanitized report/evidence.
5. По завершении остановиться; не переходить к PSS-004, Java runtime gender integration, production XML publish, серверным тестам.
