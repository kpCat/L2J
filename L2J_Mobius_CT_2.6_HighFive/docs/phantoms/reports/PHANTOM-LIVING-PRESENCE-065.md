# TASK065 — живое присутствие

Статус этой промежуточной записи: SOURCE_GREEN / ORDINARY_PENDING; CORE065 ещё не оценён. Работа начата 2026-10-21 06:04:58 UTC, рискованные эксперименты заканчиваются не позднее 11:04:58 UTC, общий предел 12:04:58 UTC. Финальная версия этого же отчёта заменит промежуточный статус.

Baseline/live origin `feature/phantom-world`: `5892949f89aa81e191705e67754aa1fd348186b6`. Все восемь исходных SHA пакета совпали. Переданные task package и новый roadmap — единственный исходный dirt. Исторический roadmap v5 и старые результаты сохраняются без переоценки.

OWN — независимый текущий checkout; Java25.0.4.1/Ant1.10.17/MariaDB11.4.13 переиспользованы без изменения инфраструктуры. Read-only preflight: JVM=0; новые порты test2245/7945/9245 и release2145/7845/9145 свободны; candidate065 отсутствовал. Хеши 922 protected файлов, 203 GeoData-регионов и 605 таблиц в 122 защищённых схемах сохранены приватно в `.phantom-local/task065`. Старые среды не запускались.

После проверки отсутствия созданы только `l2jmobiush5_phantom_test_065` и `l2jmobiush5_phantom_release_065a`: по 124 таблицы, 121 stock SQL script, schema aggregate `2CD503128136E3E854F9EFF24236DD9F286CA58D9E950ACB9B2842E78B910E28`. Сервис, users/grants и старые схемы не менялись. Credentials находятся только в приватных новых065 конфигурациях.

Календарный RED: 3 теста, 1 PASS/2 FAIL; morning=480 ACTIVE минут, uncovered phase minute `2026-10-24T05:45:00Z`. Versioned v3 сохраняет v1/v2 bytes и прежние schedule IDs, для новых профилей выбирает три перекрывающиеся 14-часовые смены; первый GREEN 3/3. Старый v2 authority дополнительно проверяется на точное совпадение шаблонов, массовая миграция отсутствует. Native restart ещё не является доказанным.

Operator control RED: exact065 JDBC отвергался старым064 binding; после адресного opt-in065 общий transport сохранил отрицательные064 проверки и получил GREEN. Первое исполнение теста выявило ошибку компиляции объявления `throws`, она исправлена до RED; это не product defect.

Причинный native RED на исходном JAR: cap1 занят давно невидимым idle owner, входящий local profile остаётся CAPACITY_REACHED после 120 pulses/12s. Retention ошибочно требовал RECENT_HUMAN. Исправлены существующие retention/reconcile/scheduler/materialization owners: exact epoch/requester, повторная проверка persisted goal ownership, callback-free idle seal под native action monitor и существующая demotion grace. Single preparation slot получает ограниченную ротацию существующего focus.

Focused GREEN разработки: scheduler34/34, handoff16/16, calendar3/3. Native dev02:3/3; old6/208359941869300 World0 → new7/208365124679700 World1 при cap1, 19pulses/1899ms. Wrong incarnation, снятый requester demand, ActionLease, настоящие Party/Store, earned ticket и persistence checkpoint сохранены. Первый dev01 наткнулся на гонку fixture до ACTIVE admission и ошибку повторного вызова one-shot finishStop в await; исправлена только подготовка/ожидание теста, raw RED/ошибки сохранены.

Независимое read-only ревью не нашло оставшихся Critical/Important/Minor. Найденный Important exact-path guard устранён в Assemble/Runtime/Java/PersistentLane: candidate допускается только в разрешённом PhantomWorld, вложенный protected064 и repo-local путь отвергаются. Финальная committed source build и ordinary sameDB episode ещё предстоят.

Чтение: root/module AGENTS, README, полный065 пакет; bounded workflow/current status/efficiency/master/release/task standard и раздел М1/сохранность064. Исходные аналоги: scheduler retention/reclamation tests, real headless fixtures/companions, native pendingLoad composition, stock SQL runner, Runtime064/Assemble064/Build064. Дополнительных AGENTS выше корня и в docs chain нет; отдельные code-map/pattern файлы не найдены. Read set расширен для доказательства native ownership, календарной совместимости и exact065 binding.

Bounded exception: одна задача включает связанные production owners, focused tests, exact065 инструменты и delivery/report, поэтому суммарно более 10 файлов; другие подсистемы не входят в scope. Новые planner/AI/scheduler/thread/SQL состояния не добавляются.

Полные логи/receipts и короткий progress ledger — `.phantom-local/task065`. Финальные команды, exit codes, Git SHA/push, preservation и STOPPED-поставка будут записаны после их фактического выполнения. До этого P1–P9 не объявлены PASS; owner client QA отдельно DEFERRED.
