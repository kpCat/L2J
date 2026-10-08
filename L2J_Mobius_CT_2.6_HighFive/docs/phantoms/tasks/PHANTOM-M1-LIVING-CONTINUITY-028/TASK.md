# TASK028 — разрешение и границы

## Результат для пользователя
Персонажи самостоятельно фармят, отдыхают, меняют неудачное действие и продолжают игру;
при уходе наблюдателя корректно переходят в фон, при возвращении снова живут в World.
Не допускаются вечный idle/retry, искусственные награды, вмешательство в чужие runtime,
потеря earned state и подделка приёмки. M2 и новая классовая/PvP/party тактика не входят.

## База и рабочая копия
- Required remote HEAD `9aeb4ac6c52970372f97637d26a5eb54c760ed1a`; опубликованный production027 `141295edc5c78d41d03b360043cc1ec6a149aa6d`.
- Branch `experiment/m1-candidate007-observe008`.
- Own worktree `C:\Users\ZBook\.codex\worktrees\m1-continuity-028\L2J_Mobius`;
  если путь занят, первый свободный suffix. Detached exact base разрешён, если branch
  занят. Не переключать основной `C:\Users\ZBook\L2J_Mobius` и чужие worktree.
- Начальный fetch/status/ref read разрешён. Если remote изменился относительно base,
  не переносить патч вслепую: сохранить FACTUAL_BASE_MISMATCH и завершить без runtime.
- Task-файлы из main разрешено читать/копировать в own task-dir, foreign diff не stage.
- Локальные AGENTS читать. Этот пользовательский TASK явно разрешает перечисленные
  exact-path commit/push/own runtime операции; запреты выше scope не отменяются.

## Время
360 минут wall-clock от первой команды, включая завершение. Модельная/platform пауза
не обнуляет лимит. К минуте90 нужен короткий full-server proof control+sampling.
После270мин не начинать semantic patch; последние90мин — frozen acceptance/cleanup.
Нет искусственного лимита 4 repairs/8 starts. Каждое исправление имеет свой RED;
тот же неизменённый RED не гонять ради случайного успеха. Два разных fixes одной
гипотезы без продвижения → записать опровержение, не расширять её бесконтрольно.

## Разрешённый runtime
- PLAY `l2jmobiush5_localplay3`: только SELECT/export. TEST — существующий guard/restore.
- Старые night023/contract024..027 и данные027: только read/export, не исправлять.
- Новые собственные `l2jmobiush5_localplay_contract028a`..`h`, конфигурация и runtime
  в own `.phantom-local/contract028*/`. Fresh из PLAY или копия retained027 для
  воспроизведения; происхождение clone и SHA256 export фиксировать.
- Target1280, active8, maxMaterialized8, maxScheduled10000. Не менять population
  schedule/уровни/HP/NPC respawn/drop ради нужного количества участников или успеха.
- Штатный GameServer, активный NPC AI, AutoPlay/AutoUse/PlayerAI, native геодата.
- Existing Synthetic observer, отдельная clone-копия TestAdmin, REAL count0.
  TestAdmin100 и четыре GM overrides=False. kpCat и QoL AllowedAccounts не менять.
- Разрешены bounded setup teleport ТОЛЬКО synthetic observer до baseline,
  безопасные MOVE_SELF во время отдельного away/return. Phantom не двигать вручную.
- Никаких fake REAL_LOGIN, UI automation, arm и вопроса «в игре».

## Остановка и безопасность
Own start/graceful stop и проверенные stage hooks разрешены. По умолчанию в конце
свои JVM STOPPED. Сначала нормальный shutdown027 и доказательство COMPLETE до pools.
Не завершать shared MariaDB/чужие Java. Emergency force только exact PID+start-time+
runtime после двух bounded graceful attempts, сохранения dumps и REALcount0;
такой исход всегда CLEANUP_FAIL. Planned crash здесь не нужен: не повторять027
crash gates автоматически. Если доказанный новый дефект требует crash, это вне TASK028.
Не писать в characters/items для лечения. Не очищать pending receipts/native incidents.

## Публикация
Разрешены bounded git read/fetch, worktree add --detach, exact-file add/commit и
`git push origin HEAD:refs/heads/experiment/m1-candidate007-observe008` без force.
Запрещены git add ., reset/clean/stash/rebase/force и root/global Git config changes.
Commit/push результата обязательны и при BLOCKED/FAILED. Non-fast-forward: сохранить
коммит и точный конфликт, не переписывать remote. Не publish credentials, JAR,
геодату, полные DB dumps/JFR/личные accounts. Raw evidence immutable; отдельный
authored UTF-8/mojibake и escaped-Cyrillic guards. Финальный staging allowlist.

## STOP
Новый in-scope subreason сам по себе не требует следующего TASK. Но неизвестная
безопасность, необходимость переписать ThreadPool/Player/transactions или выйти за
SOURCE_MAP → BLOCKED с доказательством. В конце один GREEN/BLOCKED/FAILED,
STOP_AUTHORITY=TASK028_CONTRACT. Отсутствие ответа пользователя не причина остановки.
Не запускать следующую задачу автоматически. Отказ платформы не обходить.
