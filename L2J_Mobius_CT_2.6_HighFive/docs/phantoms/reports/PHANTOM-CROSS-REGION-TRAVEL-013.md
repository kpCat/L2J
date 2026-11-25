# PHANTOM-CROSS-REGION-TRAVEL-013

**CODE_READY / LIVE_OBSERVED (scouts) / LIVE_NOT_OBSERVED (ordinary WALK)**, 25 ноября 2026, Europe/Chisinau.

## Реализация

- Локальный chord, лимит 64 точек и 120 секунд ограничивали всю поездку. Теперь `PhantomRouteGraph.itinerary` ищет направленную цепочку по существующим shards, допускает обход, соединяет фактические концы через GeoEngine и выделяет пограничный разрыв для scouts.
- `L2jNavigationBackend`, `PhantomNavigationService` и `PhantomTravelStrategy.walkSections` выпускают ограниченные WALK-участки с отдельной проверкой геометрии. Дальний путь может включать один действующий GK или Return. Штатный короткий композитный planner сохраняет существующие цепочки транспорта и ограничения затрат.
- `PhantomVisibleFarmTravel` хранит исходное намерение, выдаёт следующий участок после точной native-квитанции и перестраивает путь с текущей позиции при блокировке. Общий срок намерения ограничен 90 минутами, локальные окна и stuck-guard действуют отдельно. Сохранены отмена точного MOVE, recovery и завершение принятого STORE.
- `PhantomPartyCoordinator` и `PhantomPartyRouteCoordinator` используют те же WALK-участки для фермерского маршрута группы. Один запрос на группу, конечная цель сохраняется, `ARRIVED` выпускается после прибытия movers к конечной точке. Состав, лидер и поколение проверяются штатным владельцем.
- `PhantomDedicatedScoutRegions` и `PhantomPhysicalGeoScout` допускают узкий переход в один загруженный соседний тайл на активной lease. Отметку PROVEN по-прежнему даёт фактическая native-квитанция. Learned TSV, checkpoint и число scouts сохранены.

## Проверки и Bartz

- Изолированный Ant `jar` на JDK 25 завершился с exit 0. Направленная цепочка более 100k units и 64 точек прошла проверку обхода, бюджетов участков, запрета обратного ребра, исключения и отмены. `PhantomDedicatedScoutRegionChecks` и `PhantomTravel091Test` прошли. `party-route` прошёл 9/9 после восстановления единой точки подачи.
- Штатный STOP предыдущего Bartz подтвердил `Initial subsystem drain completed, stopped=true`. LS/GS завершились, MariaDB Bartz на 33331 сохранила PID 20356. MAIN3308 и TEST вне операции.
- Bartz зарегистрирован под штатным именем и работает с Mobius GUI. GameServer PID 19732 слушает 7777, LoginServer PID 24800 слушает 2106/9014. Загружены Geo203 и 200 лавок. Восстановлены 165 learned TSV и scout checkpoint, fingerprint сохранён.
- В коротком окне зафиксированы 8 активных scouts, 4 направленных перехода с сохранённой native-квитанцией и рост PROVEN с 61 807 до 61 833. Обычный дальний WALK в этом окне не наблюдался, счётчик ordinary physical slots показал 0. Отсутствие новой блокировки ordinary и native-поведение Party остаются непроверенными. Статусы 011 L4/L5 и 012 остаются открытыми.

## Поставка, область изменений и уборка

- Исходники установленной сборки `3577f1853db11847d6a839652c80768ef4fbca4d`. SHA‑256 GameServer `C300CF2278ECD5D58920F781CE3D65D09176C3B5BBC2E7A61C299FD2341F224F`, LoginServer `E30A5CAE1536134D5F266FD91CDEA1DA4BFD9DF5002431A16AC0EA9BB9A4B501`, BUILD_INFO `996B99E63E43DF004C2C06397C55735746B4FE7C6E7FA9C2A3656A7D3FFCDC51`.
- Точка возврата `D:/Backups/PhantomWorld/013` содержит только точные бинарники, манифест и файлы GUI/launcher с `SHA256.json` и `RESTORE.txt`. В рабочем dist оставлена установленная пара. Из принадлежащего задаче `D:/Temp/Codex/PhantomWorld/013/r01` удалено 74 765 185 байт. Тестовых процессов, оставленных scratch-файлов и блокеров уборки нет.
- Scope Git ограничен десятью указанными Java-файлами, существующими `PhantomRouteLearningChecks`, `PhantomDedicatedScoutRegionChecks` и этим отчётом. Локальные `Interface.ini`, pins и guarded launcher обновлены только для GUI Bartz и сохранения работающей MariaDB при обслуживании.
- Для сверки и выкладки использованы `git symbolic-ref --short HEAD`, `git rev-parse HEAD`, `git merge-base --is-ancestor <baseline> HEAD`, `git status`, точные `git diff` / `git ls-tree`, `git ls-remote origin refs/heads/feature/phantom-world`. `git archive` использован для точных входов изолированной сборки. Запись ограничена `git commit --only -- <точные пути>`, адресным `git add` отчёта и обычным `git push origin HEAD:refs/heads/feature/phantom-world`.
- Mojibake-маркеры в изменённых файлах проверены.
- Escaped Cyrillic в изменённых файлах проверены.
