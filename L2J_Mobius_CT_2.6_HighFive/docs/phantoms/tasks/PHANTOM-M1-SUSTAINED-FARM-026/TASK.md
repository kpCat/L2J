# Полномочия и граница TASK026

Required base: `6ebe1d93f4ee168cd8952f0416dc920f26c430ac`.
Ветка публикации: `experiment/m1-candidate007-observe008`.
Production TASK025: `41692152ca6944176be8014c307702b7d2a0a6bc`; required base — его документирующий дочерний commit.
Только L2J_Mobius_CT_2.6_HighFive, Windows11, JDK25, Ant, MariaDB127.0.0.1:3308.

## Цель
Не «увеличить число убийств до первого зависания», а закрыть непрерывное выполнение
farm-миссии: работающие native действия, осмысленное восстановление ресурсов,
выход из локального навигационного тупика, доведение earned callbacks до завершения.
Параллельно разобрать relevant N02/S12 regression и измерить crash XYZ на правильной
границе. Подтверждённое сохранение TASK025 остаётся обязательной регрессией.

## Разрешено
- Новый isolated detached worktree
  `C:\Users\ZBook\.codex\worktrees\m1-sustained-026\L2J_Mobius`;
  при занятом пути первый свободный suffix. Branch checkout не обязателен.
- Read-only fetch/history; exact-path add/commit и normal push
  `git push origin HEAD:refs/heads/experiment/m1-candidate007-observe008`.
  Перед push проверить, что remote — предок HEAD. Не force, не merge/rebase вслепую.
- PLAY `l2jmobiush5_localplay3`: только SELECT/export. kpCat/main/foreign не менять.
- Старые contract024/025 и их evidence: только read/export, копии не править.
- Собственные новые clones `l2jmobiush5_localplay_contract026a`…`h`:
  создать через проверенный экспорт; ordinary server DML разрешён только внутри них.
  Исходный snapshot сохранять immutable. Никакой ручной починки product rows.
- Private TEST lane: переиспользовать существующий guard; новый exact-owned manifest
  для026, не менять checksum/metadata shared TEST ради запуска.
- Собственные start/graceful stop, один planned AFTER_NATIVE crash, при его PASS
  один AFTER_FINALIZE на отдельной clone. REALcount0 обязателен.
- Emergency stop только своих PID+start time+runtime после dumps и двух bounded
  graceful attempts. Он не даёт CLEANUP_PASS.
- Несколько связанных исправлений из SOURCE_MAP после собственных RED; нет лимита
  «четыре repair rounds». Но нет и разрешения на произвольную архитектуру.
- Тестовые малые seams для deterministic barriers; никакие gates не отключать.

## Запрещено
`git add .`, reset/clean/stash/rebase/force; чужие worktrees/JVM; правки PLAY;
новая схема БД; глобальная переработка Player/ThreadPool/EventDispatcher/боевой математики;
выдача HP/EXP/items фантомам ради natural PASS; reset incidents/counters;
подстановка anchor XYZ; пропуск сохранённой проблемной части cohort; M2/PvP/chat/gear.
Не стирать READ/WRITE guards для открывания «зависшего» owner.

## Срок и автономность
Целевой сеанс4–6ч, hard wall360мин от первой команды. До45мин — приоритетная
карта runtime-причин по retained025. До90мин — короткий full-server proof пути
исправления либо конкретная воспроизведённая граница. После270мин semantic freeze;
оставшиеся90мин — acceptance/restart/cleanup/report. Зелёный результат позволяет
закончить раньше; не надо ждать до360. Бюджет не обязывает завершать заведомо
некорректную реализацию. Опасный/outside-design путь остановить, безопасные независимые
проверки можно закончить без расширения scope.

Без ручного клиента: Synthetic, не fake REAL_LOGIN. Не спрашивать «в игре», «вышел»,
arm/worktree/phase/commit approval. При внешнем запрете инструментов — BLOCKED и
сохранить работу, не обходить запрет и не ждать часами ответа.
STOP_AUTHORITY=TASK026_CONTRACT. Один итог, без автоматического TASK027.
