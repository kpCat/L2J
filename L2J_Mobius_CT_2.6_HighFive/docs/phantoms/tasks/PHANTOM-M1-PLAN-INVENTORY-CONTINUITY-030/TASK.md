# TASK030 — разрешения и конечная граница

Модель: GPT-6.1 Sol, reasoning Very High. Новый Codex-чат, без субагентов.
Максимум240мин wall-clock, первые75мин до первого full-server probe. После150мин не
начинать новые semantic-исправления; оставшиеся90мин — frozen tests/runtime/cleanup/report.
Если продуктовый scope ещё не закрыт, это FAILED/BLOCKED с точным статусом, не вопрос.
Нормальные связанные исправления разрешены, счётчик repair rounds не ограничивает их.
Не повторять неизменённый RED, не тратить время на прежние неизвестные U1/U2 без нового evidence.

## Разрешённый workspace и данные
- Required base758ee295518be5b9abba0f757cc240782bfd2987, только High Five.
- Только normal push в experiment/m1-candidate007-observe008.
- Isolated worktree C:\Users\ZBook\.codex\worktrees\m1-plan-inventory-030\L2J_Mobius;
  первый свободный suffix разрешён. Detached exact base разрешён при занятой ветке.
- Main C:\Users\ZBook\L2J_Mobius, foreign diffs/worktrees, kpCat — не изменять.
- PLAY l2jmobiush5_localplay3 и все old clones — SELECT/export only.
- Own DB l2jmobiush5_localplay_contract030a..h, MariaDB127.0.0.1:3308;
  создавать только новые имена, manifest устанавливает единственного владельца.
- Shared guarded TEST — существующий gate/restore. Не менять shared metadata/checksum;
  при недоступности использовать собственную disposable clone с существующим native fixture.
- Никаких UPDATE для лечения INCONSISTENT/DEAD/HP/EXP/vitality, сброса request/receipt/clock.
  Legitimate application transactions разрешены только на own clone.
- Fresh clone из неизменённого исходного экспорта разрешена после фикса producer. Это не
  замена forensic старой clone; оба lineage отражаются отдельно.

## Runtime без пользователя
Existing Synthetic observer + настоящий GameServer/stock NPC. Не fake REAL_LOGIN.
Нет обязательных «в игре», «вышел», arm, UI automation или выбора модели инструментами.
Own TestAdmin-копия только для observer. Его setup teleport до заморозки сцены разрешён
по доказанной актуальной позиции/геодате; самого Phantom не перемещать и не управлять им.
Runtime1280 READY / target1280 / active8 / maxMaterialized8 / maxScheduled10000;
не снижать population или retirement gates. Synthetic525s/watchdog30s/sequence400 неизменны.
План сессии обязан помещаться в этот TTL, иначе разделить сессии до их запуска.

Start/graceful stop собственных exact PID+start-time+runtime разрешены. Не путать process
exit с healthy drain. До двух planned crashes только на выделенных own clones, REALcount0,
с backup и точной native fault boundary. Emergency force только своих exact PID после
dumps и двух bounded graceful attempts; force никогда не GREEN. Чужие PID/DB не трогать.

## Git и публикация
Разрешены fetch/read, создание isolated worktree, exact-path add/commit/normal push,
включая task-only BLOCKED/FAILED report. Запрещены git add ., reset, clean, stash,
rebase, force push, изменение feature/phantom-world. При remote advance не rebase:
сохранить локальный результат и BLOCKED_BASE_MOVED. Невозможный платформенный permission
не обходить. Scope определяется SOURCE_MAP; exact allowlist перед stage.

## Цель и STOP
Scope: ordinary FARM/HISTORICAL_FARM mutation footprint, проверяемое natural admission,
streaming receipt collector, и уже разрешённые связанные handoff/projection boundaries.
Не писать новый combat engine, не менять stock managers, не упрощать V2/legacy evaluator.
При новом доказанном нарушении за пределами модели — безопасный BLOCKED, без ожидания.
Итог один: GREEN/BLOCKED/FAILED; STOP_AUTHORITY=TASK030_CONTRACT. M1 не закрывать лишь
потому, что TASK030 GREEN. Следующую задачу автоматически не запускать.
