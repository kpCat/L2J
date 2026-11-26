# PHANTOM-LIVING-POPULATION-RECOVERY-018

26 ноября 2026, Europe/Chisinau.

**CODE_READY / DEPLOYED / LIVE_PARTIAL.** Пополнение восстановлено. Для устойчивого выполнения 5000/128/32 и доказательства широкого нативного развития данных пока недостаточно.

## Исходный Bartz

- Ветка `feature/phantom-world`, локальный HEAD и свежая ветка origin совпали с `18cc0d1cc80917ce91e01b7817d3ca655bbd4331`. Оба ancestry-запроса дали exit 0. Пакет 018 искался от корня High Five и совпал с манифестом по SHA-256 и длине для 7 из 7 файлов. Уже установленный r03 source `ffc38f09b612ea06893d2440c18b2ac34e8a5cf8` является предком. Рабочий checkout содержал прежние изменения конфигов, learned и прочие WIP. Сборка взяла tracked baseline и точные проверенные изменения 018.
- Перед вмешательством PID GS 16916 и LS 20168 подтверждены операторской записью, слушателями и видимыми окнами Mobius в сессии Windows 1. Bartz SQL PID 28664 слушал `127.0.0.1:33331`. JDBC и read-only SQL подтвердили `l2jmobiush5_phantom_test_131` и datadir `.phantom-local/logs/TASK131/sql-lanes/owner-play-002/sql-task131/data`. Рабочие цели из местного конфига составляли 5000/128/160/32, burst 0, режим scouts ALWAYS и heap 12 GiB.
- Bartz хранил 1745 связанных ordinary, из которых 1721 READY и 24 INCONSISTENT. У всех 24 причина `ownership.bootstrap_cleanup_exhausted`. У двух проверенных записей сохранялись связанный персонаж, root `population.bootstrap` и handoff REQUESTED. У профиля 7 в population.state статус READY. В живом срезе его historical readiness сообщил `catchup.renewal.background_state_invalid`. Прежнюю историческую защиту не изменяли.

| Время | Ordinary World / action admission | Scouts World | Лавки / люди | Calendar / eligible / admitted |
|---|---:|---:|---:|---:|
| около 19:43 | 138 / 138 | 0 | 200 / 0 | 1324 / 31 / 31 |
| 19:48:26 | 134 / 134 | 3 | 200 / 0 | 1262 / 0 / 0 |
| 19:59:33 | 122 / 122 | 16 | 200 / 0 | 1112 / 0 / 0 |

Нулевые eligible/admitted встречались одновременно с живыми допущенными actors. Эти поля обнуляются при устаревшем ecology snapshot. Рост EXP исходной когорты с 1 576 638 до 1 582 250 за 141 секунду подтверждён SQL. В 19:57 было 1 589 496 EXP, 120 154 SP, четыре персонажа уровня 10+, максимум 13 и 453 сохранённых персонажа с нулевым HP. Эти значения включают законное offscreen развитие.

## Причины и изменения

- `PhantomPopulationManager` учитывает вышеуказанный linked quarantine отдельно от здоровой цели. Допуск нового независимого shell возможен при совпавших expected/actual/linked object ID и конкретной причине post-bootstrap cleanup. Остальные INCONSISTENT защищают allocator. Неопределённый исход создания shell закрывает новые создания до повторной проверки durable inventory при старте. Один claim сериализует reconcile, STOP ждёт его завершения, рост ограничен существующим inventory limit и пропускает проверки SQL-владельцев, которые нужны при сокращении.
- Идентификатор профиля выдаёт транзакция репозитория вместе с `population.state`. На Bartz подтверждены уникальные ключи profile ID, связанных character object ID и account login. Имя персонажа проверяется нативным поиском под общим монитором `CharInfoTable` перед `Player.create`. В БД `characters.char_name` индексирован без уникального ограничения. Столкновение аккаунтов или идентичности продолжает закрывать создание.
- `PhantomPopulationEcologyService` получает новый shell после его публикации в population manager. Первичная запись сохраняет планируемое назначение, а повторные reconcile больше не сбрасывают revision уже применённого startup plan. Новый карантин приостанавливает ecology и закрывает вход в ordinary work sink. Очистка bootstrap предшествует публикации обычного расписания.
- `L2jPhantomPartyBackend` сохраняет guard нативного отсутствия и все проверки ссылок на персонажа при handoff. Старый предел обхода 256 срабатывал при 322–338 игроках в World, включая 200 лавок. Предел для той же проверки увеличен до 512. Переполнение этого предела по-прежнему блокирует подтверждение.
- `PhantomSystem` и `PhantomMaterializationService` резервируют место для ALWAYS dedicated scouts в общей выдаче физических слотов. Раньше 137–138 ordinary уменьшали доступную ёмкость scouts уже при нулевом их числе. Теперь новый ordinary использует слот только внутри своей части общего cap. Повторный запрос существующего владельца сохраняет прежний результат. Проверки давления, World capacity, STORE, accepted effects, Party/SSQ, кланы и gear остаются в штатных путях.

## Проверки

- JDK 25 / Ant `jar compile-tests` в shadow, exit 0, владелец config 38/38. Первому запуску shadow не хватало двух Enchant XML. Входы добавлены из tracked baseline. До реализации `phantom-population-throughput-test` обнаружил обе новые ошибки, 3/5, exit 1. Финальный `PhantomTestLauncher population-throughput 16001601` дал 6/6, exit 0. Проверены локальный карантин, неизвестный commit, конфликт идентичности и конкурентный refill под STOP. Отдельные `PhantomDedicatedScoutChecks` и `PhantomDedicatedScoutRegionChecks` дали exit 0.
- `PhantomTestLauncher world-coordination130 130` после добавления точных tracked Party/Topology/GK входов дал 79/81, exit 1. Ошибки `native-no-itinerary-drains-before-stock-release` и `sequential-gatekeeper-keeps-the-exact-party-through-an-observation-gap` воспроизвелись с теми же сообщениями при подстановке пяти исходных production-классов. `population-ecology-goal033 33003300` дал 39/40, exit 1 из-за проверки исторического default OFF в актуальном tracked config. Focus `continuity113` дал 5/5, exit 0. Полный `ant verify` и TEST с БД не запускались.
- Использованы существующие `PhantomPopulationSuite`, `MemoryStore`, контракты continuity113 и dedicated scouts. Начальный read-set расширен на транзакцию `PhantomProfileRepository.createWithComponent`, `CharacterCreate`, нативную проверку `L2jPhantomPartyBackend.withUnmaterializedNativeAbsence`, цепочку Goal publication в решение и handoff и участки `Shutdown` для проверки фактического STOP.

## Поставка

- Код r01 `ce09fa0287b70e8fd9c04ed5c4965e4cf4c040ca`. GameServer SHA-256 `9E0EBE9B341B3DE63658AA41DE3AF9E6979F3423C74BC49969F98E4B45C508D0`. В live заменены только `dist/libs/GameServer.jar` и `dist/BUILD_INFO.json`.
- Первый штатный STOP закрыл новые допуски, но исчерпал отдельные окна ecology и materialization. Последний срез этого прохода показал ещё 113 владельцев из 132. Штатный retry того же GS завершил drain в 20:03:32. До замены SQL33331 подтвердил `online=2` = 0, `background.owned-store` = 0 и 1745 связанных ordinary. PID LS 20168 и SQL 28664 продолжили работу.
- Адресный откат находится в `D:\Backups\PhantomWorld\PHANTOM-LIVING-POPULATION-RECOVERY-018\r01`. Предыдущие JAR `FA62AE8DF6D819890FAF83BE0CEE6C93BDF65B46074BF0A728F0A1A5858A92F2`, BUILD_INFO `4365883CB068B2567FDD3F37C79A5017FD3C6649D10E53363CB0D95643D8C83C`, SHA256.json и RESTORE.txt проверены. Откат устанавливает пару файлов после штатного drain и сохраняет записанный прогресс, learned и Geo.
- `dist/START.cmd` и JDBC preflight дали exit 0. GS PID 25336 запущен в 20:03:42 и зарегистрирован как Bartz к 20:04:49. SHA-256 нового BUILD_INFO `A1B3C19E0802FEB06A9ED41373931238DFC184E4A81D9E8C47463DFA81843CFD`. Оба окна Mobius видимы в сессии 1, слушатели GS7777, LS2106/9014 и SQL33331 подтверждены. Хеши LoginServer, `PhantomPlayers.ini`, `PhantomGeoTrainer.ini` и `game/java.cfg` совпали до и после.

## После установки

| Время | READY | Ordinary World / action admission | Scouts World / moving |
|---|---:|---:|---:|
| 20:05:53–54 | 1735 | 6 / 6 | 2 / 2 |
| 20:06:35–56 | 1739 | 11 / 11 | 4 / 3 |
| 20:10:48–50 | 1756 | 15 / 15 | 11 / 10 |
| 20:12:51 | 1764 | 15 / 15 | 9 / — |
| 20:14:56–57 | 1773 | 18 / 18 | 3 / 4 |
| 20:16:06–07 | 1778 | 19 / 19 | 2 / 2 |

World и движущиеся scouts измеряются независимо. В срезе 20:14:57 scout успел сменить состояние между двумя чтениями.

- **P1.** В 20:12:51 operator snapshot подтвердил target 5000, 1790 population entries, 1764 READY, 24 INCONSISTENT, 0 retired и `deficitBlocked=false`. В 20:06:35 все 18 новых READY уже освободили bootstrap root. Прежний набор 24 quarantine сохранял SHA-256 `a5a8961f428d51d26f7dcd94a59bfe7fefce6547d5b8ad727632f53f38f6899f`. `background.owned-store` в проверенных SQL-срезах равнялся 0.
- **P2.** Первый ограничивающий допуск — ecology/readiness. В 20:09:38 у 632 из 1721 исходного READY закончился initial catch-up, но только у 28 сохранённый курсор был в пределах минуты от текущего времени. У 1225 отставание превышало час. Из 1744 внутренних записей catch-up 1272 были RUNNING, 39 PENDING, 433 COMPLETE. В 20:12:51 оставались 113 ordinary мест до цели, из 13 запросов на materialization 4 ждали `ecology.preparation_capacity`, 9 не имели текущего local demand. В подготовке 8, ожидали 248, старейшее ожидание 477 с. Обычная очередь ecology содержала 1724 записи. Сохранённые курсоры и accepted effects не перепрыгивали.
- **P3.** Для обычного профиля 114, object 268500600, epoch 324652884454700, совпали убитая, вознаграждённая и settled цель `268483761/0/1`. Зафиксированы damage=2, kill=1, reward=1, cycle=1, +315 EXP и +10 SP. У 98 наблюдались 4 события damage, DEATH_RECOVERY и затем REGEN после смены epoch. Более широкий SQL-прирост когорты включает исторические начисления. Полная нативная цепочка у трёх разных ordinary и новые уровни 20/40 в этом окне не подтверждены.
- **P4.** Режим ALWAYS и 32 настроенных dedicated подтверждены. В 20:10:50 в этой сессии уже было 17 materializations, 215 завершённых нативных отрезков и 136 новых доказанных маршрутов. Присутствие менялось. В срезах наблюдались `ROUTE_BUDGET_EXCEEDED`, `BLOCKED_BEFORE_ISSUE` и временный выход из маршрута при нулевом дефиците выделенных слотов. Отключений защиты маршрутов или давления не делали.
- **P5/P6.** 25-секундный baseline JFR дал средний CPU процесса 3,4%, 30-секундный r01 профиль — 4,9%. В обоих отрезках отсутствуют события GC-пауз. На r01 подтверждён heap max 12 288 MiB, used 4642 MiB в стартовом срезе. В 30-секундной записи 3453 события SocketRead относились к 33331, максимальная длительность 15,7 мс. Около 10,1 с из 11,7 с суммарного зафиксированного socket read пришлось на ecology worker. На конкретном срезе основной scheduler был NORMAL, ready/due/capacity — 0/178/10000. Давление и ожидание наблюдались в контуре historical preparation. Для 012–017 проверены затронутые контракты и штатный restart. Отдельные большие кампании Party/SSQ/clan/gear оставлены за пределами окна.

За 12 минут после регистрации Bartz появились 57 новых READY и ещё два shell в работе. Между 20:05:53 и 20:16:07 прирост составил 43 READY, около 4,2 в минуту. До здоровой цели осталось 3222. Линейная экстраполяция этого короткого окна даёт около 13 часов, при условии сохранения темпа, календаря и пропускной способности readiness. Этот расчёт не подтверждает достижение цели. Окно завершено на живом поступательном росте. Следующий причинный этап для ordinary — очередь historical preparation, для scouts — потери на безопасном выборе, прерывании и ротации маршрута. Резерв capacity оставался доступен.

С 20:07:34 до 20:14:57 среди тех же 1745 исходных ID у 13 выросли EXP и SP, у 10 вырос уровень. Сохранено +10 603 EXP, +1050 SP и +11 суммарных уровней. В заключительном срезе у новой когорты было 1647 EXP, 180 SP и максимум уровня 3. У старой когорты максимум 13 и 461 запись с нулевым HP. Dedicated scouts завершили 378 нативных отрезков и записали 277 новых подтверждений в этом GS. Цели 128 ordinary и 32 одновременных scouts в окне после рестарта остаются открытыми.

## Scope, публикация и уборка

- Пять production Java-файлов, существующий тест `PhantomPopulationSuite.java` и этот отчёт составляют публикацию 018. Остальной WIP исключён. Mojibake-маркеры в изменённых файлах проверены отдельно, совпадений нет.
- Escaped Cyrillic в изменённых файлах проверен отдельным поиском, совпадений нет.
- По полномочиям пакета 018 использованы `git remote get-url origin`, `git symbolic-ref --short HEAD`, `git rev-parse HEAD refs/remotes/origin/feature/phantom-world`, адресные `git --no-optional-locks status`, `git fetch origin`, `git ls-remote --symref origin HEAD refs/heads/feature/phantom-world`, `git ls-remote --exit-code origin refs/heads/feature/phantom-world`, `git merge-base --is-ancestor <baseline/source> <head/ref>`, `git ls-tree [-r] --name-only HEAD -- <точные входы>`, `git archive --format=tar --output=<scratch file> <baseline> -- <точные входы>`, `git diff` по точному scope и `git -c core.whitespace=cr-at-eol diff [--cached] --check -- <scope>`. Для записи использованы `git commit --only` по шести Java-путям, `git add -- <этот отчёт>`, `git commit --only` по отчёту и обычный `git push origin HEAD:refs/heads/feature/phantom-world`. Точный итоговый SHA ветки приводится в ответе вместе со свежей remote квитанцией.
- В scratch `D:\Temp\Codex\PhantomWorld\PHANTOM-LIVING-POPULATION-RECOVERY-018\r01` было 204 125 677 байт. Убраны сборки, архивы, JFR, временные TSV и логи. Windows сохраняет read handle на `probe\snapshot018.jar` до выхода текущего GS. Оставлены этот файл 4555 байт и адресный маркер 296 байт, восстановленный после первой попытки уборки. Разовый probe завершил запись, без фоновых задач или трансформации классов. Повторный рестарт ради 4,8 КБ не выполнялся. Долгосрочный откат состоит из 4 файлов, 12 688 029 байт.
- Bartz оставлен запущенным с видимыми GS и LS. Активных TEST, сборок или сессий JFR 018 нет. SQL-наблюдения шли только через Bartz33331. Учёт 019 не запускался.
