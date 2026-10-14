# HANDOFF043 — небольшой результат, сохраняющий М1 для М2–М7

## Обязательные файлы в этом же task family

`CHECKPOINT043.md`: actual START/DEADLINE, code/JAR/config/evaluator, own namespace, current G2 baseline source, B2 factual diagnosis, prospectively frozen positive preconditions, episode start/stop and exact data paths.

`RESULT043.md`: сверху ясно сказать `G4 PASS` или `G4 FAIL/NOT_VERIFIED` с объяснением: исходный native work, legitimate active combat, locality departed, terminal/commit, actual background producer+elapsed, same-profile native return. Указать G2 current/old status и P4 conservation043. Не скрывать другие OPEN.

`GATES043.tsv`: P0–P6; короткие source refs/actual run identity; `M1=OPEN`, `READY_FOR_M2=false`. Документами нельзя конвертировать B2 FAIL в PASS.

`REVIEW043.md`: exact source/test diff, каждый причинный product fix RED→GREEN→native, unaffected stock regressions, поимённые изменённые source paths. Если product не менялся — explicit `NO_PRODUCT_EDITS`. Сдержанная adversarial self-review без бесконечного evidence recursion.

`TASK043-review.zip`: небольшой архив task results/source diff/manifest/typed receipts и ограниченных proof slices. Full raw/DB/copy old041/042 ZIP исключить, оставить exact local path/SHA. Повторно открыть ZIP и проверить duplicate/path traversal/CRC/SHA каждого member.

## Git и безопасность

Read-only status/HEAD/ls-remote/diff, затем только exact-path staging, ordinary commit/non-force push, проверить local=remote и tracked-clean. Не выполнять `reset`, `clean`, `restore`, `force`, `merge`, `rebase`, `worktree add/remove`, prefix-wide add и удаление raw. Если чужой staged/tracked конфликт — сохранить и остановить affected write.

Собственные JVM штатно остановить только exact-owned normal path; база043/raw сохранить. Никаких crash, forced kills, client probing/injection/WSAConnect, входов/портов protected040a. Сохранить исходные `GAME/JAR` и конфиги (не переписывать старую самостоятельную поставку). Проверить mojibake и escaped Cyrillic отдельно; UTF-8, обычный EOL проектного Java файла и BOM для новых WinPS5.1 helper при необходимости.

После завершения TASK043 не запускать следующую задачу и М2 автоматически. Если получен G4 PASS, следующий независимый шаг — M1 G3/G5 normal save/restart, затем G6 в отдельном допускаемом тестовом контуре и отдельная личная приёмка. Ни одна из этих будущих проверок не заявляется закрытой заранее.
