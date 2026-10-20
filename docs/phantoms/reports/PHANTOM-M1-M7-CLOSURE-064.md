# TASK064 — текущий checkpoint

STATUS: IN_PROGRESS. Это промежуточный checkpoint; серверное закрытие М1–М7 пока не заявлено.

Начало: 2026-10-20 18:08:55 UTC. Deadline: 2026-10-21 06:08:55 UTC; финальный резерв начинается не позднее04:38:55 UTC.

Baseline/local/live remote: `fe2a24d389ed4af622cb14bc15a615b4b69e28f1`, `feature/phantom-world`. Все7 SHA256 пакета совпали. Пакет фактически расположен в корневом `docs/phantoms/tasks/PHANTOM-M1-M7-CLOSURE-064`, его исходные bytes сохранены.

Обнаружены Java25.0.4.1, Ant1.10.17, MariaDB11.4.13 на локальном3308. Обе новые схемы064 отсутствовали; подключений к защищённым release-схемам не было. Инвентаризированы203 обычных GeoData-региона061 с SHA каждого; их файлы и credentials остаются приватными. Старые source/release/DB не изменялись.

Подготовлена точная064 fixture binding в существующем test infrastructure, с сохранением058 guards. Focused RED отказал на новом root; GREEN допускает только собственные root/config/owner/schema и bounded pool4.

Для обычного START/STOP добавлен выключенный по умолчанию local operator transport: проверка физического root, schema, JAR, PID incarnation и deadline; затем штатный shutdown. Attach и force fallback отсутствуют. Используется существующий shared ThreadPool, без gameplay scheduler/owner. Три pure contract-проверки прошли; отдельные WinPS5.1 проверки root/schema/owner/port/JDBC/JAR прошли. Первая попытка WinPS обнаружила отсутствие UTF-8 BOM у нового кириллического скрипта; исправлено, исходный FAIL сохранён.

Production/test compilation и JAR прошли. Два прежних warning о `System.runFinalization()` сохранены. Bootstrap, ordinary/native gameplay, affected111 и конечная поставка пока NOT_RUN.

Приватный план/ledger/raw: `L2J_Mobius_CT_2.6_HighFive/.phantom-local/task064`. Финальные матрица, статусы, команды, hashes и ограничения будут записаны здесь после выполнения оставшегося разрешённого scope.

Git используется по прямому разрешению пользователя и AUTHORITY064: bounded branch/HEAD/status/ancestry/live-remote/diff inspection; последующие exact-path commits и normal push разрешены. Force/reset/clean/rebase/amend не выполнялись.
