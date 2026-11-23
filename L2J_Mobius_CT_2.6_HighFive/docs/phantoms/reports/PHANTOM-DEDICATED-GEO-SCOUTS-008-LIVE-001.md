# PHANTOM-DEDICATED-GEO-SCOUTS-008-LIVE-001

**Итог `BLOCKED` (`STOP_BLOCKED`).** Готовая 008 установлена на Bartz. Ступени OFF, Count1 и Count8 прошли запуск и штатное завершение. Count32 достиг 32 связанных native `Player` в World, но непрерывный наблюдаемый отрезок составил 186 с при пороге 300 с. При возврате к Count8 GameServer остался в `DRAINING`. Финальная игровая приёмка владельцем `REQUIRED`.

## Поставка и восстановление

- Источник `feature/phantom-world`, GitHub и локальный HEAD `b3b81db716d153e1f12fab2ef02a86ab4d5f288e`. Сборочный набор из 2 485 файлов проверен по Git blob ID. Для двух отличавшихся локальных INI/JSON сборка использовала совпадающие с Git экземпляры из прежнего 008 shadow. Ant 1.10.17 и Temurin 25.0.4.1, `ant.bat -f <shadow>/L2J_Mobius_CT_2.6_HighFive/build.xml jar`, exit 0. Манифест входов SHA-256 `6BD56365E63515B85DB7A788782521AB20F7E4DBA5CBF27461E956C1E6FCB178`.
- SHA-256 GameServer `2D779EF577D857E1D035B89433F449B155C4CD100FFAB3B853919BA999D49739`, LoginServer `0551947D30EAEA87B794C0CEF0979F36F49ACB3F6CA3256D54F4135324907C97`. Штатный launcher сверил обновлённые `BUILD_INFO.json` и `operator/config-hashes.json`. Проверены все 10 контрольных файлов. `jcmd VM.flags` подтвердил GS `-Xms2g -Xmx8g`, ZGC, LS `-Xms128m -Xmx256m`, ZGC. Оба Java-сервера запускались штатным launcher в видимых окнах.
- MariaDB `11.4.13`, `127.0.0.1:33331/l2jmobiush5_phantom_test_131`. Live PID, datadir, JDBC и права учётной записи подтверждены до миграции. Добавлена одна таблица `phantom_dedicated_scouts` InnoDB, 7 колонок и 5 уникальных индексов. После миграции OFF число служебных записей равно 0. Учётные записи и персонажей затем создавал штатный bootstrap GameServer.
- Единственная точка восстановления `D:\Backups\PhantomWorld\008-live-001`. Дамп восстановлен до поставки в изолированной схеме на D:. `CHECK TABLE` вернул `OK` по всем 152 таблицам. Повторный дамп побайтово совпал, SHA-256 `3DA48D0E1CBF824ED76EB90CDEACDAF88A871DA2907B28F197C585AD8AA4A285`. Там же сохранены прежние JAR, INI, JVM-параметры, learned и оба прежних файла хешей launcher.

## Live-ступени 23 ноября 2026

| Ступень | GS PID / uptime | Heap MiB, used/max | Bound / reserved / World / moving | Обычные online / магазины | ARRIVED / новые PROVEN / тайлы | PathFinding, очередь/пик | CPU процесса / события и сумма пауз ZGC | Stuck / failures / STOP |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| OFF | 5156 / 245 с | 3 350/8 192 | 0 / 0 / 0 / 0 | 2 / 200 | 0 / 0 / 0 служебных | `NOT_MEASURED` | 1,34% за 42 с / 49 и 0,614 мс | 0 / 0 служебных / штатный STOP |
| 1 | 13508 / 282 с | 4 324/8 192 | 1 / 1 / 1 / 0 в снимке, 1 наблюдался | 2 / 200 | 9 / 9 / 2 | 0/1 | 2,30% / 49 и 0,726 мс | 0 / 6 / 3 STORE, 1 offline, после flush 14 ARRIVED |
| 8 | 8908 / 161 с | 5 714/8 192 | 8 / 8 / 8 / 6 | 2 / 200 | 24 / 24 / 4 | 0/1 | 2,53% / 46 и 0,646 мс | 0 / 24 / 12 STORE, 8 offline, после flush 38 ARRIVED от 8 акторов на 8 тайлах |
| 32 | 2744 / 730 с перед STOP | 4 222/8 192, наблюдаемый пик 8 080 326 656 байт | 32 / 32 / 32 / 20 перед STOP, диапазон World 24–32 | 1 / 200 | 1 197 / 1 145 / 58 перед STOP, после flush 1 404 ARRIVED от 32 акторов на 65 тайлах | 0/1 | 1,96% перед STOP / 57 и 0,733 мс | 0 / 110 перед STOP / `STOP_BLOCKED`, 56 STORE за запуск до снимка drain |

Замеры CPU на включённых ступенях взяты из статуса JVM. ZGC измерен по накопительным счётчикам пауз `jcmd PerfCounter.print`. Очередь PathFinding в обычных снимках 0, наблюдаемый пик 1, настроено 2 worker. Возраст и распределение latency `NOT_MEASURED`. Пороги 4, 16, 24 и 32 зафиксированы по SQL `online=2` при автоматическом заполнении одного запуска. Отдельные снимки runtime подтверждают World и moving. Первое окно наблюдения прервалось из-за ошибки вычитания дат в наблюдателе. Измерение начато заново на том же PID, кратковременные снижения численности обнуляли пятиминутное окно. В проверенном 12-минутном окне статус обновился 113 раз. При `GAMEPLAY_LOAD_HIGH` с heap выше 90% давление дошло до 6 и часть разведчиков штатно уступила ресурсы. Настроенная цель обычной популяции 8, наблюдалось 2 при OFF/1/8 и 1 при 32. На Count32 в World было 233 `Player` при лимите 2 000 и встроенном резерве 8 мест для клиентов. Игровой вход человеком `NOT_RUN`.

## Блокировка возврата на Count8

Штатный `Runtime.ps1 -Action Stop` передал запрос с exit 0, но launcher вернул `OWNER_PLAY_STOCK_STOP_INCOMPLETE`. GameServer исчерпал срок на `deadline:physical_scout_walk`, сообщил о 16 оставшихся materialization и отложил выход, сохранив workers и SQL. В `PhantomSystem` попытка с `StopOutcome.FAILED` сохраняет отрицательный результат даже после разрешённого позднего cleanup, а `Shutdown.DrainProgress` требует подтверждённого drain. Ожидаемый штатный STOP на Count32 не подтверждён. Без подтверждения `STORE` смена уровня заблокирована защитой задачи.

- Последний снимок `DRAINING`, `moving=0`. SQL показал 32 связанные служебные пары, 17 `online=0`, 15 `online=2`, 0 клиентских служебных сессий, 0 совпадений с обычными профилями. Сохранено 200 магазинов с прежними владельцами и товарами, 33 прежних профиля. Возврат на Count8 и его новый запуск `NOT_RUN`.
- Текущие слушатели `127.0.0.1`: GS PID 2744 на 7777 (отложенный STOP), LS PID 1740 на 2106/9014, MariaDB PID 12576 на 33331. Наличие слушателя GS в `DRAINING` не подтверждает рабочий игровой режим. SQL и LS оставлены для защиты незавершённого `STORE`.
- Learned показал 3 488 `PROVEN` после flush и 119 регионов общего накопленного состояния. Все 90 исходных путей learned присутствуют. GeoData 203 файла, fingerprint `46b8d2d574d0b6c48dcd84a5163c2953d227f9f36207bf769cbf26ead0b6a80c`. Подписанный payload WALK SHA-256 `6ca140fb0548f0068c7b3d422ea01ff1db5acb06c7cef6af82c48cf4d6789f29`. Межтайловый native WALK, игровые сцены воды, гибели и PvP `NOT_RUN`. `HumanRoute 007` остаётся предметом отдельной ручной приёмки владельца.

## Scope, журнал и уборка

Рабочие изменения ограничены двумя JAR, `PhantomGeoTrainer.ini`, временным подъёмом `MaxMaterializedPhantoms` с 32 до 48 в `PhantomPlayers.ini`, `game/java.cfg`, двумя контрольными файлами launcher, миграцией 008 и штатными runtime-записями. Count32 остаётся в конфиге до подтверждённого STOP. Контрольная проверка не выявила изменений исходников Java, Studio, GeoData, сторонних поставок или контрольных файлов launcher вне согласованного scope.

- Scope исходников проверен `git rev-parse`, `git symbolic-ref`, ограниченными `git status`, `git ls-tree` и `git diff -- <PhantomPlayers.ini или OwnerConfig.json>`. Сборка использовала список точных blob ID из `git ls-tree -r -l <pinned-head> -- <входы сборки>`. Публикация ограничена настоящим отчётом в `feature/phantom-world`.
- Основные команды runtime: `Invoke-Task131Sql.ps1 -Action Stop/Start -LaneId owner-play-002`, `Runtime.ps1 -Action Start/Stop -RuntimeRoot <Bartz dist>`, `jcmd <GS PID> VM.flags`, `GC.heap_info`, `PerfCounter.print`, `netstat -ano -p tcp`. Пробный restore и сборка с Ant завершились с exit 0. Заключительная попытка штатного STOP Count32 завершилась с exit 1.
- Временный restore `D:\Temp\Codex\PhantomWorld\008-live-001\pre-toxkoa-pqja`: удалено 199 441 089 байт. Сборочный shadow `D:\Temp\Codex\PhantomWorld\008-live-001\build-toxygl-c0nq`: удалено 81 102 417 байт. Отдельных рабочих процессов сборки и restore не осталось. Runtime-каталог `D:\Temp\Codex\PhantomWorld\008-live-001\sql-toxp99-d5jt` оставлен активным MariaDB и JVM, последнее измерение 131 301 байт. Управляемые файлы в C: Temp не создавались.
- Защищённые материалы в `D:\Backups\PhantomWorld\008-live-001\operator`: компактная квитанция `Bartz-008-live-result.json` и один `count32-physical-scout-witness.tsv`, всего 802 105 байт. SHA-256 полного witness `76D8B02948D8543417BA82DE3DF956E0A52EC742833A9C25ECCE371191FA880E`. Точка восстановления сохранена для владельца.

- Mojibake-маркеры в изменённых текстовых файлах проверены, совпадений в области правок нет.
- Escaped Cyrillic в изменённых текстовых файлах проверена, совпадений в области правок нет.
