# PSS-002 — safety и Git, обязательный контракт

## Полная зона read-only

`C:\Users\ZBook\L2J_Mobius\` включая `L2J_Mobius_CT_2.6_HighFive`, все Java/XML/INI/SQL/config/custom/semantic/manifest, базы, геодату, `.phantom-local`, GameServer/client, L2J Git.

**Нельзя** туда записывать, менять файлы, создавать build/test/tmp/log, делать checkout/reset/merge/stash/commit/push, запускать Ant/JAR/server/DB или автоматически исполнять модельные outputs. Допустимо только читать уже существующие 65 humanized файлов для evidence, точечный Java loader/source contract и read-only SHA. Не клонировать/скачивать L2J в project repo, не вводить сетевые действия с игровым сервером.

## Собственный Git root и remote

- `C:\Users\ZBook\PhantomSemanticStudio\` — единственное место для новой задачи и git действий.
- Проверено до запуска: GitHub `kpCat/PhantomSemanticStudio`, `main` SHA `87755c86421c9a320c7bc1f5f7682fb13c95332e`.
- Локальная branch PSS-001 могла называться `master` при root commit; **не нужно переименовывать или reset** ради отправки. Проверить `git rev-parse HEAD`, branch и remote before writing. Expected baseline exact HEAD `87755c8...`; если HEAD/remote продвинулись, проверить ancestry/dirty самостоятельно, при несовместимости остановить writes с `BLOCKED_BASE`, не force/rebase.
- `origin` отсутствует: пользователь **явно разрешил** создать `origin` **только** как `https://github.com/kpCat/PhantomSemanticStudio.git` после проверки exact repo и разрешений. `origin` указывает на другую репу: `BLOCKED_REMOTE`, не менять чужой URL. Ни под каким видом `kpCat/L2J`.
- После read-first допустим `git fetch origin main` только в Studio repo; он не затрагивает L2J. Без checkout/reset/rebase для исправления конфликта. Проверить fast-forward. Не коммитить чужие файлы и dirty state.
- Git status/read-only команды допустимы; staging только **точные** task-owned paths, НЕ `git add .`, `git add -A`, blanket staging. `git diff --cached --name-only` против file allowlist. Commit обычный (не amend). Push: `git push origin HEAD:refs/heads/main` **non-force**, не включать `--force`/`--force-with-lease`.
- После пуша проверить `git rev-parse HEAD` и `git ls-remote origin refs/heads/main`; должны совпасть. Если push/auth/net недоступен, честный `BLOCKED_PUSH` и локальный commit+ZIP+отчёт; без обходного remote.
- Не коммитить `artifacts/`, `bin/`, `obj/`, `.vs/`, workspace, токены, дампы реальных запросов, пользователя/session/config. Задача и отчёт в Git разрешены.

## Safety invariant

Текущий сервер не меняется даже при одобрении кандидатов. Никаких overwrite, auto-install, auto-approve, signed XML, Java-verified status. `ReviewExporter.Export` только JSON review. Следующий Java oracle/publish — другая задача, после независимого ревью.

## Failure policy

- `GREEN`: локально доказанные requirements, с отдельными status живой LM/UI.
- `BLOCKED`: UI/DPI/LM/integration gate не проверен или commit/push заблокирован; оформить **честный** отчёт и безопасный commit/push попытку, не стирать непроверенный production code ради зелёного статуса.
- `FAILED`: тесты красные, build сломан, data source изменён; revert only **own exact changes** safely if necessary without destructive git commands, retain report. Не сообщать SUCCESS.

Завершение: `reports/PSS-002-final.md`, `reports/PSS-002-ui.md`, review ZIP, exact diff/commit/remote proof, STOP.
