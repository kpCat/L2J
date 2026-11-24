# PHANTOM-LIVING-FARM-NAVIGATION-010

24 ноября 2026, Europe/Chisinau. **CODE_READY / LIVE_REQUIRED**.

- Ветка `feature/phantom-world`. Исходный HEAD и фактический GitHub ref `db6b51f5999751ff4bec8d9a79da0a9d25572710`. Локальный remote-tracking ref перед работой отставал на три коммита и был предком исходного HEAD.
- Итоговый коммит исходников и тестов `e80193681044943a3b84f885548577c9215572d5`. Этот отчёт публикуется отдельным коммитом документации, чтобы зафиксировать точный SHA исходников сборки.
- Кандидат `D:/Temp/Codex/PhantomWorld/010/r02-delivery/GameServer.jar`, 12 557 392 байта. SHA-256 `FFA5A248557A683D18EE8CF797D525FF6E0897D6D0793A66FE099C5BAA082786`.
- Bartz выключен. Поставка и live-приёмка ожидают отдельного разрешения владельца.

## Изменения и воспроизведение

**Обычный travel.** `PhantomNavigationService` передаёт текущий learned snapshot штатному `L2jNavigationBackend` при `submitTravelPlan`. Общий источник WALK для прямой поездки, GK ingress и egress, Return и composite рассматривает directed PROVEN. Проверяются свежая геометрия, Z, instance, вода, препятствия, угрозы по контракту запроса, список отозванных рёбер, GeoData/zone generation и отмена. Начальный коннектор допускает фактическую посадку на `Geo Z + 5` с повторной проверкой GeoEngine. На тестовой геометрии старый обычный worker пропустил известный обход, а новый выбрал его. Обратный запрос совпал со штатным планом без learned. При двух доступных доказательствах learned используется, если требует не больше расстояния и точек пути. Отказ из-за времени не становится свидетельством невозможного WALK.

**Дальний WALK.** Если составной маршрут не выполнился и оставшийся бюджет допускает ходьбу, `PhantomVisibleFarmTravel` может продолжить через существующий `NativeWalkBudget`. Следующий этап начинается с фактической позиции после квитанции собственного native MOVE до предыдущего checkpoint. Сохраняются исходный дедлайн поездки, проверка owner и epoch, штатный capture/STORE и границы планировщиков.

**Профиль 27.** В сохранённом `dist/game/log/java0.log` от 24 ноября точный запрос `(-90856,246584,-3704) → (-84312,252645,-3664)` повторялся с `requestKind=EPISODE`, 17 nodes, 22 edges, 23 queries и 17 labels. Learned содержит входящее PROVEN от `(-91064,246808,-3600)` и ноль исходящих PROVEN, доступных штатным коннектором из исходной точки. На реальном тайле `17_25.l2j` в изоляции эпизод с GeoData и статическими XML-препятствиями вернул `NO_ROUTE_WITHIN_BUDGET`, 20 nodes, 27 edges, 26 queries и 20 labels. Отдельный обратный запрос к источнику входящего ребра вернул `NO_PATH_WITHIN_BUFFER`. Этот probe не включает live water/zone state и Player. Для неизменного повторного отказа ожидание растёт с 120 до 240, 480 и 960 секунд. Другая позиция, другой маршрут, новая геометрия или новая локальная learned-подсказка могут открыть поиск раньше. Существующий historical owner сохраняет возможность проверить иную ферму или штатный Return. Физический выход профиля из исходной точки остаётся открытым.

**Профиль 7.** Журнал от 23 ноября подтверждает `NO_SELECTABLE_NATIVE_FARM_TARGET` и `NO_USEFUL_NATIVE_FARM_PROGRESS`. Старые строки не содержат NPC ID и причин отбраковки. При застое новый лог указывает ожидаемый NPC, ревизию цели и счётчики по состоянию моба, ID, instance, занятости, награде, этажу, LoS, WALK, боевой пригодности и scope. При перепланировке после отказа ближайшая ферма допускается по наблюдаемому подходящему NPC того же ID внутри её зоны. Проверка под action lease ограничена 16 ближайшими кандидатами с разницей уровней до четырёх. Если локальных вариантов нет, планировщик рассматривает допустимые дальние источники. Fixture воспроизводит ложную локальную привязку с другим NPC на месте и ожидаемым NPC вне зоны. Вариант с нужным мобом в зоне остаётся первым, а исключение дальнего маршрута даёт отказ. Фактический бой, смерть моба и начисление EXP/SP остаются за штатным Player. Точный фильтр старого отказа профиля 7 и игровой результат проверяются на live.

## Точный scope

| Папка | Файлы |
| --- | --- |
| `java/…/phantoms/navigation` | `L2jNavigationBackend.java`, `PhantomNavigationService.java`, `PhantomRouteGraph.java`, `PhantomRouteLearning.java`, `PhantomWalkCorridorPlanner.java` |
| `java/…/phantoms/background` | `PhantomFarmRouteBackoff.java`, `PhantomHistoricalBackgroundPlanner.java`, `PhantomHistoricalBackgroundService.java`, `PhantomVisibleAutoPlay.java`, `PhantomVisibleFarmTravel.java` |
| `test/java/…/phantoms` | `background/PhantomFarmRouteBackoffTest.java`, `knowledge/PhantomFarm074Checks.java`, `navigation/PhantomRouteLearningChecks.java` |
| `docs/phantoms/reports` | `PHANTOM-LIVING-FARM-NAVIGATION-010.md` |

Чужие изменения в локальных INI и untracked-файлах сохранены. Исходники GeoEngine, native Player, конфигурация, SQL, learned TSV, HumanRoute 007 и разведчики вне опубликованного diff. Старые пути и `:3308` в документации относятся к прежней топологии. Живая схема Bartz на `:33331` не использовалась.

## Адресные проверки

| Команда или проверка | Exit | Результат |
| --- | --- | --- |
| `ant -f <scratch>/HighFive/build.xml -Dsrc=<module>/java jar`, JDK 25.0.4.1 / Ant 1.10.17 | 0 | 2 450 Java-файлов, штатный owner verifier 38/38, сборка 31 с |
| `javac` адресных runner-классов против кандидата | 0 | Тесты запускались с собранным JAR в classpath |
| `PhantomRouteLearningChecks <scratch-fixture> --geo-fixture` | 0 | Обычный submit, directed обход, контроль обратного запроса, +5 только на входе, время против физического отказа, STOP/readback |
| `PhantomRouteLearningChecks --snapshot010 <scratch-copy> <GeoData>` | 0 | Родной loader прочитал 160 regions, 52 117 PROVEN и 6 626 CANDIDATE. GeoData fingerprint совпал. У всех 164 TSV в копии SHA-256 совпал до и после |
| `PhantomFarmRouteBackoffTest` | 0 | Координаты профиля 27, повторная пауза, независимая ферма, истечение, owner/epoch и география |
| `PhantomFarm074Checks --native-target010` | 0 | Выбор по NPC ID и позиции в зоне, допустимый дальний вариант, отказ при исключении пути |
| `PhantomTravel091Test` и `PhantomFarmRouteRecoveryTest` | 0, 0 | Checkpoint, лимиты, угрозы, новый frontier и обязательный поворот |
| `HumanRoute007Test` | 0 | Trace, arrival, authenticated refusal и zone controls |
| `PhantomTravelGeoProbe --episode` и `--waypoints` | 0, 0 | Два описанных настоящих геометрических отказа. SQL и акторы отсутствовали |
| Общий `compile-tests` из рабочей копии | 1 | Локальный untracked `PhantomParty109PvpChecks.java` обращается к отсутствующим методам. Адресные runner-классы собраны отдельно |

Оба новых контроля навигации и 120-секундного повтора воспроизвели дефект до правок. Native budgets остались 128 nodes, 512 edges, 32 PathFinding queries и 4096 labels. Одна композиция допускает до четырёх learned probes и 128 проверок их сегментов. Используется существующий ячеечный индекс. Пики очереди, CPU и heap на живом сервере не измерялись.

- Mojibake-маркеры в изменённых файлах проверены. Совпадений нет.
- Escaped Cyrillic в изменённых файлах проверен. Совпадений нет.

## Поставка и уборка

- Сборочный run `D:/Temp/Codex/PhantomWorld/010/r01` занимал 88 668 983 байта перед уборкой. Удалено 76 111 591 байт. В `r02-delivery` оставлены только проверенный JAR и маркер владения. Активных процессов задачи и блокеров уборки нет.
- Рабочий предыдущий GameServer.jar доступен и сверён по SHA-256 `009B606220CA21DD95CD3EA3129B9FB4621864C3FDD52CB1ED7BE2E5953BE47E`. Новый backup для DB-free задачи не создавался. Перед согласованной заменой потребуется адресная точка возврата текущего JAR. Доступ к restore-proof исторической SQL-копии 008 в этой сессии не проверен.
- После отдельного разрешения нужна одна штатная поставка этого кандидата и запуск официального Bartz с тем же datadir, Count8 и 8 GiB/ZGC. Сверяются 200 магазинов, 33 ordinary profiles, 32 scout identities, learned и HumanRoute 007. Для `LIVE_FULL` в одном Bartz нужны два разных ordinary профиля с наблюдаемым MOVE → ATTACK → DAMAGE → KILL → EXP/SP и продолжением, плюс один фактический межрегиональный native WALK. Отдельно фиксируются исходы профилей 27 и 7.

Git использован по разрешению задачи. Аудит выполнен командами `git symbolic-ref --short HEAD`, `git branch --show-current`, `git rev-parse HEAD`, `git rev-parse --show-toplevel refs/remotes/origin/feature/phantom-world`, `git merge-base HEAD refs/remotes/origin/feature/phantom-world`, `git status --short --branch`, `git status --porcelain=v1` и `git ls-remote --exit-code origin refs/heads/feature/phantom-world`. Scope проверялся командами `git diff --cached --name-only`, `git diff --stat -- <точные пути>` и `git -c core.whitespace=cr-at-eol diff --check -- <точные пути>` с сохранением отслеживаемых CRLF. Публикация выполняется через `git add -- <allowlist>`, `git commit --only … -- <allowlist>` и обычный `git push origin HEAD:refs/heads/feature/phantom-world`.
