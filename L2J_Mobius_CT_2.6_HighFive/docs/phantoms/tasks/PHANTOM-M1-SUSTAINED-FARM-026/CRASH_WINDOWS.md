# Crash/recovery — не позднее чтение координат

Проблема025: latest observed state version5817 уже после recovery и ordinary native
работы. Поэтому строгая проверка original AFTER XYZ на позднем SELECT не определяет
writer. Повтор idempotent SELECT тоже не локализует его.

До planned crash:
- exact owned PID/start/runtime/SHA, REALcount0, собственная clone;
- только один natural earned actor, exact receipt/epoch/after native witness;
- attach observer до старта recovery, не после первого synthetic prelude;
- узнать точку возврата resolver/подтверждённого commit до публикации actor.

Actual process crash AFTER_NATIVE (не throw вместо crash) → новый process:
- selected profile ordinary admission удерживается test-only barrier; transactional
  recovery работает своим штатным путём;
- witness capture получает exact recovered argument сразу после known commit, pending
  и owned receipt уже resolved; никаких DB/Player locks во время внешнего ожидания;
- сравнить native(after), full items/skills, vitality, identity, XYZ; сохранить отдельный
  RECOVERY_COMMIT_EXACT_PASS;
- отпустить barrier, отдельно увидеть первый legitimate movement/respawn/restore writer;
- сохранить POST_RECOVERY_NATIVE_CONTINUATION, потом graceful stop;
- actual second process restart, idempotence exact до progression.

При mismatch до admission — это реальный R root, разрешён own native RED и narrow fix
по SOURCE_MAP. Нельзя заменять expected actual coordinate на anchor или SQL result.
При совпадении early witness и lawful later move прежний late-XYZ failure объяснить
как недоопределённое измерение; прежний raw RED сохранить, не переписывать результат025.

AFTER_FINALIZE optional только после этого PASS, другая clone. Всего2planned crashes,
оба в retained evidence, no forced kill посторонних процессов. Если финальная проверка
не влезла — NOT_RUN. Никаких200restarts ради «исправления» одного receipt.
