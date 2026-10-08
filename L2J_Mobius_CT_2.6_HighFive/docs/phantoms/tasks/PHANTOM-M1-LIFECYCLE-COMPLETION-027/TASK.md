# Разрешение и ограничения TASK027

## Рабочая среда
Только High Five. JDK25/Ant/Windows; loopback MariaDB 127.0.0.1:3308.
Main checkout `C:\Users\ZBook\L2J_Mobius` и foreign diff не менять.
Новый isolated worktree разрешён:
`C:\Users\ZBook\.codex\worktrees\m1-lifecycle-027\L2J_Mobius`.
Если каталог занят — первый свободный suffix. Если branch занята — detached exact base.
Пакет из main checkout разрешено только читать/копировать в task-worktree.

В первой read-only проверке: remote ref, exact HEAD, worktree status, актуальный AGENTS.
При конфликте exact base с новым remote не rebase/reset/force: публикация BLOCKED_BASE.
Не выполнять будущие обновления remote автоматически после первого base pin.

## Заранее разрешённые действия
- Существующие READ/SELECT/export PLAY и старых contract024/025/026; старые clones immutable.
- Собственные clones `l2jmobiush5_localplay_contract027a` .. `contract027h`:
  создание/import своего проверенного export, существующий scoped native TEST gate,
  legitimate native gameplay, store/recovery и backup. Никакого DML-лечения продукта.
- Новый own runtime, собственные порты/JVM, штатный start/stop, existing Synthetic observer.
- До двух planned crashes на отдельных own clone, REALcount=0, только существующие
  exact fault windows AFTER_NATIVE / AFTER_FINALIZE после controlled persistence PASS.
- Emergency stop только своих exact PID + native start-time + runtime-path после двух
  bounded graceful attempts и сохранения dumps/SQL/receipts; никогда как CLEANUP_PASS.
- Exact-path add/commit + normal push в заданную experiment-ветку при любом исходе.
  Detached push `HEAD:refs/heads/experiment/m1-candidate007-observe008`, без force.
- Обычные исходники commit после проверок и clean build из committed SHA. Failed candidate
  публиковать с FAILED/BLOCKED и без утверждения о пригодности runtime.

## Запреты
`git add .`, reset/clean/stash/rebase/force, смена feature/phantom-world, затрагивание
чужого worktree/процесса/БД. kpCat не изменять. TestAdmin — только копия в own clone.
Никаких fake REAL_LOGIN, UI login, QoL AllowedAccounts как разрешения Pilot.
Не править shared TEST checksum/guard/schema metadata для обхода отказа.

Нельзя менять battle formulas, native damage/loot/EXP правила, ThreadPool architecture,
EventDispatcher listener traversal, GeoEngine/geodata/XML каталоги, SQL schema.
Не увеличивать phase deadlines/target caps для PASS, не сбрасывать evidence/incident,
не завершать RESERVED ticket по возрасту, не replay неизвестный заработанный callback.
Нельзя возвращать HP/EXP/items/XYZ из логов/счётчиков или подставлять anchor coordinates.
Нельзя reseed/reset исходные product clones после неуспешной сцены ради следующего PASS.

## Без ночных вопросов
Разрешения по этапам уже заданы. При занятых чужих портах применять поддержанный
alternate private port set; если невозможно — безопасный BLOCKED_ENVIRONMENT.
На системное требование дополнительного permission не отвечать обходом защиты.
При ненужном повторном UI-вопросе не стоять в ожидании пользователя: использовать
вышеописанный вариант либо завершить с сохранённым ограничением.

## Бюджет и output
Max360min; semantic freeze270min; последние30min обязательный cleanup/report reserve.
Нет лимита «четыре fixes» или «восемь starts». Но каждый новый fix имеет причинный RED;
одинаковый failed run без изменения гипотезы/наблюдения не повторять.
Не копировать по 100 файлов инструментария. Reuse025/026; максимум4 новых runtime
скрипта и один диагностический helper. Артефакты только task-dir/private ops027.
Секреты, JAR, DB dumps, геодату, JFR и чужие данные не коммитить.
Отчёт один финальный, STOP_AUTHORITY=TASK027_CONTRACT. Следующую задачу не начинать.
