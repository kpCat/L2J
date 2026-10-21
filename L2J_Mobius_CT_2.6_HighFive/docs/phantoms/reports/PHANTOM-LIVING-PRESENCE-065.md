# TASK065 — живое присутствие

Статус этой промежуточной записи: IN_PROGRESS; CORE065 ещё не оценён. Работа начата 2026-10-21 06:04:58 UTC, рискованные эксперименты заканчиваются не позднее 11:04:58 UTC, общий предел 12:04:58 UTC. Финальная версия этого же отчёта заменит промежуточный статус.

Baseline/live origin `feature/phantom-world`: `5892949f89aa81e191705e67754aa1fd348186b6`. Все восемь исходных SHA пакета совпали. Переданные task package и новый roadmap — единственный исходный dirt. Исторический roadmap v5 и старые результаты сохраняются без переоценки.

OWN — независимый текущий checkout; Java25.0.4.1/Ant1.10.17/MariaDB11.4.13 переиспользованы без изменения инфраструктуры. Read-only preflight: JVM=0; новые порты test2245/7945/9245 и release2145/7845/9145 свободны; candidate065 отсутствовал. Хеши 922 protected файлов, 203 GeoData-регионов и 605 таблиц в 122 защищённых схемах сохранены приватно в `.phantom-local/task065`. Старые среды не запускались.

После проверки отсутствия созданы только `l2jmobiush5_phantom_test_065` и `l2jmobiush5_phantom_release_065a`: по 124 таблицы, 121 stock SQL script, schema aggregate `2CD503128136E3E854F9EFF24236DD9F286CA58D9E950ACB9B2842E78B910E28`. Сервис, users/grants и старые схемы не менялись. Credentials находятся только в приватных новых065 конфигурациях.

Календарный RED: 3 теста, 1 PASS/2 FAIL; morning=480 ACTIVE минут, uncovered phase minute `2026-10-24T05:45:00Z`. Versioned v3 сохраняет v1/v2 bytes и прежние schedule IDs, для новых профилей выбирает три перекрывающиеся 14-часовые смены; первый GREEN 3/3. Старый v2 authority дополнительно проверяется на точное совпадение шаблонов, массовая миграция отсутствует. Native restart ещё не является доказанным.

Operator control RED: exact065 JDBC отвергался старым064 binding; после адресного opt-in065 общий transport сохранил отрицательные064 проверки и получил GREEN. Первое исполнение теста выявило ошибку компиляции объявления `throws`, она исправлена до RED; это не product defect.

Предварительная retention-причина: `reclaimable()` требует `RECENT_HUMAN`, поэтому давно невидимый свободный materialized owner не даёт preparation slot. Production retention пока не менялась: сначала требуется причинный native RED. Новые тесты и инструменты не являются PASS всей TASK065.

Чтение: root/module AGENTS, README, полный065 пакет; bounded workflow/current status/efficiency/master/release/task standard и раздел М1/сохранность064. Исходные аналоги: scheduler retention/reclamation tests, real headless fixtures/companions, native pendingLoad composition, stock SQL runner, Runtime064/Assemble064/Build064. Дополнительных AGENTS выше корня и в docs chain нет; отдельные code-map/pattern файлы не найдены. Read set расширен для доказательства native ownership, календарной совместимости и exact065 binding.

Bounded exception: одна задача включает связанные production owners, focused tests, exact065 инструменты и delivery/report, поэтому суммарно более 10 файлов; другие подсистемы не входят в scope. Новые planner/AI/scheduler/thread/SQL состояния не добавляются.

Полные логи/receipts и короткий progress ledger — `.phantom-local/task065`. Финальные команды, exit codes, Git SHA/push, preservation и STOPPED-поставка будут записаны после их фактического выполнения. До этого P1–P9 не объявлены PASS; owner client QA отдельно DEFERRED.
