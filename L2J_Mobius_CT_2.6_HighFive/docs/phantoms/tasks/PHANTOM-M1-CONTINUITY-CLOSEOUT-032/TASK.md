# Полномочия и границы TASK032

## Что одобряется отправкой /goal

Это инженерная задача, а не только диагностический spike. Разрешены связанные fixes
в разрешённых методах, native tests, несколько коротких runtime probes, сборки,
собственные clone DB и финальные серверные проверки. Новый найденный guard внутри
этой цепочки сам по себе не требует нового ZIP и не является причиной остановки.

Лимит480мин — верхняя граница, не план обязательного расхода. 4–6ч — ориентир, не ETA.
В начале записать START_UTC, FREEZE_UTC=START+360мин, END_UTC=START+480мин.
Раз в60–90мин короткий checkpoint: новый факт/изменение, фактический результат,
следующий проверяемый вопрос, elapsed и расход запусков. Никакого ожидания ответа.

## Не менять смысл M1

Продукт: native Player самостоятельно фармит, восстанавливается и переживает переходы
visible/background и сохранение. Общие цели законны; запрет «один моб — один бот»
не вводить. Не подстраивать механику под Gremlin/конкретные profileId.
Пауза/перепланирование — состояние с конечным выходом, не автоматический PASS.
НЕ добавлять M2 party/chat, raid/clan/PvP AI, профессии, новую экономику или C#.

## Защиты

PLAY и retained DB031 — только SELECT/export; own clones разрешены.
Не SQL-heal, не reset DEAD/INCONSISTENT, не искусственно завершать catchup/receipt,
не менять EXP/SP/items/vitality/XYZ ради совпадения. Native mechanics и RNG неизменны.
Никакого inline SQL/I/O/wait внутри ordinary FINALIZED/native hook.
Не переносить SQL на тот же checkpoint через другой callback/lock.
Не сбрасывать evidence overflow/useful debt, не поднимать deadlines/capacity ради PASS.
Earned callbacks не replay/cancel/complete по возрасту. Epoch/CAS/ownership сохраняются.

## Право на работу с процессами

Разрешены start, reuse и graceful stop ТОЛЬКО своих runtime032 с точными PID,
start-time, command line, DB manifest и кодом. Не останавливать все java.exe.
REALcount>0 — не управлять персонажем и не выключать такую чужую сессию; сохранить
boundary. Не ждать пользовательского logout: свои ночные lanes должны быть Synthetic.
До двух planned fault windows на отдельных собственных clones; повтор только если
первый был INVALID из-за исправленного setup, а не чтобы получить удачный результат.
Emergency exact-owned force разрешён только после dumps, двух bounded graceful
попыток и проверки отсутствия REAL; это CLEANUP_FAIL, не здоровый shutdown.
MariaDB и посторонние приложения не выключать.

## Остановка

STOP_AUTHORITY=TASK032_CONTRACT. Причины: закончены все gates; истёк deadline;
доказана safety/outside-scope boundary; непреодолимый platform gate. Не приписывать STOP
пользователю, если он его не писал. Не отправлять три повторных одинаковых BLOCKED.
Одна итоговая публикация с честными частичными результатами.
