# PHANTOM-AUTONOMOUS-LIVING-WORLD-014

25 ноября 2026, Europe/Chisinau. Bartz, `feature/phantom-world`. **CODE_READY / LIVE_PARTIAL**.

## Причины и исправление

- Профили 3 и 4 были календарно ACTIVE с устаревшей исторической базой и просроченными заданиями `maintain.supplies` в состоянии ACTIVE. Историческое обновление ждало завершения заданий, а supply resume по истечении суточного срока уже не запрашивал Player. Ecology переводила рабочий слот в SLEEPING, поэтому владелец execution не получал рабочий тик для регистрации. Ограниченный ambient-обход теперь регистрирует календарно ACTIVE профили до окончательного допуска. Существующий SOLO recovery проверяет отсутствие native-владельца, завершает принятые эффекты и фиксирует просроченное задание в том же checkpoint.
- Ambient-подготовка использует календарный ACTIVE. Действующий ambient-сигнал обновляется для допущенного в мир ordinary. Бюджет новых запросов расходуют только принятые scheduler-сигналы. Human locality, точные исторические запросы и принятые journeys сохраняют собственные каналы допуска.
- Профиль 31 восстановился после смерти, затем применил штатный Return и снова остановился в подготовке. После подтверждённой посадки планировщик сначала рассматривает обычный маршрут от фактической позиции, включая граф 013. Локальный проверяемый WALK остаётся запасным вариантом. Проверки точной native-квитанции и checkpoint действуют на публикации плана.
- Пассивный HumanRoute status показывает стадию подготовки, решение и короткую нативную выборку в пределах существующего приватного интерфейса. Настройки расписания, экономики и поиска Party сохранены. Изменение приоритета кандидатов по равному score осталось за рамками исправления: причиной наблюдавшегося простоя оказались допуск и подготовка.

## Наблюдение Bartz

- На исходной сборке 013 после восстановления Bartz наблюдались четыре ordinary в мире при цели 8. Проверка сохранённых шаблонов и фаз дала 16 календарно ACTIVE из 32 связанных профилей в 06:16. На итоговой сборке записи 3 и 4 перешли в `FAILED` с причиной `supplies.execution.expired`, после чего оба профиля вошли в мир и начали двигаться.
- В одной native-сессии профиля 4 зафиксированы MOVE, три события DAMAGE, один KILL и принятая награда +210 EXP/+10 SP. Профиль 8 в своей native-сессии двигался, нанёс урон, убил моба, получил +105 EXP/+10 SP и снова начал движение. Источник — пассивные HumanRoute-квитанции с совпадающими `killed`, `rewarded` и `settled`. SQL-срез использован для census, его прирост EXP/SP не засчитан как native reward.
- В 06:27 в мире восемь ordinary, восемь активных scouts и 200 лавок. Уровни связанных ordinary 2–13. Scout checkpoint восстановлен при запуске и затем сохранён. На месте 165 learned TSV и 203 файла GeoData. Scouts зафиксировали 48 новых PROVEN и два региональных перехода в текущем сеансе.
- Длинный ordinary WALK, следующий боевой цикл для обоих профилей и сбор автономной Party остаются без подтверждения. Для уровней 1–39 штатная вероятность автономного поиска Party равна 0. У профиля 31 новый `farm.background` после Return перешёл к снабжению. После перезапуска его действующая подготовка ждала `ecology.preparation_capacity`. Критерии 011/012/013 сохраняют прежний статус, игровая приёмка остаётся за владельцем.

## Проверки и поставка

- JDK 25.0.4.1, Ant 1.10.17, `jar` и owner-profile 38/38. Первую ошибку компиляции в чтении `SignalResult` исправили по сигнатуре scheduler. На итоговой сборке прошли `PhantomSupply074Checks`, `PhantomOrdinaryFarmAdmissionChecks` и `PhantomFarmRouteRecoveryTest --idle-farm011`, без SQL-фикстуры.
- На старте задачи Bartz оказался остановлен. SQL-журнал зафиксировал `CTRL_CLOSE_EVENT`, GameServer потерял БД при сохранении лавки. Штатный SQL-инструмент подтвердил отсутствие прежнего процесса по аттестации. Та же MariaDB запустилась на существующем datadir. Завершение STORE того сеанса остаётся неподтверждённым.
- Два последующих плановых STOP подтвердили `stopped=true`, `characters.online=2` в количестве 0 и 0 записей `background.owned-store`. MariaDB Bartz 33331 сохранила PID 28664, MAIN3308 сохранила PID 8692. Тестовых серверов и миграций не было.
- Точка возврата [D:/Backups/PhantomWorld/PHANTOM-AUTONOMOUS-LIVING-WORLD-014](D:/Backups/PhantomWorld/PHANTOM-AUTONOMOUS-LIVING-WORLD-014) содержит исходные `GameServer.jar`, `BUILD_INFO.json`, контрольные суммы и инструкцию. При откате используются текущие SQL, learned и checkpoints.
- Bartz оставлен запущенным. Оба окна Mobius подтверждены. GS PID 1552 на 7777, LS PID 20168 на 2106/9014. Установленный источник `23446ee35a02d3d5f8b3c2bcfb43e909e606e3d5`, SHA-256 GameServer `AE446B014BDD6BBB1EA660E6CA71AC6744E2CC09E4E0A411D581D29AA89E2204`, BUILD_INFO `DFF8E14EDD7F6588C1EFA86B8B322F975D95AFAD84BCA13A2F4DD04EB30D63DF`. LoginServer использует прежний JAR.
- Из [scratch r01](D:/Temp/Codex/PhantomWorld/PHANTOM-AUTONOMOUS-LIVING-WORLD-014/r01) удалено 83 349 996 байт сборки и проверок. Входной tar удалён после распаковки. Scratch-файлов, тестовых процессов и блокеров уборки нет. Работающие Bartz и обе SQL сохранены.

## Область изменений и Git

- Source commits `8969770cfe172c2bee2254bdfb104972e5c6c3d6` и `23446ee35a02d3d5f8b3c2bcfb43e909e606e3d5`. Allowlist состоит из `PhantomSystem.java`, `L2jPhantomSupplyJourney.java`, `PhantomHistoricalBackgroundService.java`, `HumanRouteService.java`, `PhantomSupply074Checks.java` и этого отчёта. Дополнительное чтение `PhantomExecutionControlPort`, `PhantomSupplyIntentStore` и `PhantomVisibleFarmTravel` понадобилось для проверки регистрации владельца, атомарного checkpoint и продолжения после Return.
- Для сверки baseline и scope использованы `git rev-parse`, `git symbolic-ref --short HEAD`, `git merge-base`, `git remote get-url origin`, `git status`, `git ls-remote [--exit-code] origin refs/heads/feature/phantom-world` и адресные `git diff`, включая `--cached --quiet`, `--stat` и `-c core.whitespace=cr-at-eol diff --check`. Для сборки использован `git archive --format=tar --output=<scratch>/build-input.tar HEAD -- <входы High Five>`. Запись по разрешению задачи ограничена адресным `git add -- <отчёт>`, `git commit --only -m <сообщение> -- <точные пути>` и обычным `git push origin HEAD:refs/heads/feature/phantom-world`. Итоговый HEAD сверяется с GitHub после публикации.
- Mojibake-маркеры в изменённых файлах проверены.
- Escaped Cyrillic в изменённых файлах проверены.
