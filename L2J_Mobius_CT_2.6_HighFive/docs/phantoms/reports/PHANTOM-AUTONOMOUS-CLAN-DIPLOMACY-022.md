# PHANTOM-AUTONOMOUS-CLAN-DIPLOMACY-022

**28 ноября 2026, Europe/Chisinau. Статус BLOCKED.** Код собран и прошёл focused проверки. Установка остановлена на штатном STOP исходного GameServer. OWNER_QA открыт.

## Исходное состояние Bartz

- Ветка `feature/phantom-world`, HEAD, tracking ref и свежий GitHub совпали на `22804e966da87b9567e75f1d32420c58d1280af5`. Проверены ancestry baseline и STORE-фиксов `1101eb82533` и `b37eae1bc9`, восемь файлов пакета совпали с manifest по длине и SHA-256. Пакет находится под `docs` модуля High Five. Рабочее дерево содержит локальные изменения и неотслеживаемые файлы.
- `STATUS.cmd` и listener подтвердили GS 2024 на 7777, LS 10736 на 2106/9014 и MariaDB 18764 на 33331. Оба процесса Java имели видимые окна Mobius в пользовательской сессии. JDBC обоих серверов обращался к Bartz `l2jmobiush5_phantom_test_131`. Активный source `9e4364c4d98874fd537e4e4f913382aab4855ab0`, heap 12 GiB.
- В 12:23 SQL показал 5000 READY, 24 INCONSISTENT, 128 ordinary и 32 scouts с `online=2`, 200 лавок, один клан уровня 0, два участника, ноль войн и альянсов. В 12:38 состав клана вырос до десяти. Старый ночной срез с нулём кланов перестал описывать живое состояние.
- В 12:38:46 нативный census показал 128 ordinary в World, 126 с открытым допуском действий, 32 scouts, 160 занятых физических мест и 200 лавок. Календарный набор новых допусков за три чтения менялся от 0 до 31. В последнем срезе 30 профилей ожидали `ecology.preparation_capacity`, 11 — `presence.no_current_local_demand`. Registry содержит 32 dedicated scouts. На диске 203 GeoData и 166 learned TSV. Последний checkpoint перед STOP сообщил 328806 PROVEN и 5544 полных journeys.

| READY, 10+ | Факты допуска и основания |
|---|---|
| 2, L11 | Вне World, BACKGROUND, сохранённые HP=0 и DEAD, catchup COMPLETE. Предпочтение −2. |
| 9, L11 | ACTIVE, живой нативный владелец, HP 356/356 и открытый допуск. Предпочтение −2. |
| 17, L10 | ACTIVE, живой владелец, HP 333/333 и открытый допуск. Предпочтение 6. Уже лидер клана. |
| 25, L10 | Вне World, SLEEPING, сохранённый HP 20, READY/RUNNING, отставание экологии 162 минуты. Предпочтение −2. |
| 271, L10 | Вне World, BACKGROUND, сохранённый HP 309, READY/RUNNING, отставание 1013 минут. Предпочтение 3. |
| 1060, L10 | Вне World, SLEEPING, сохранённый HP 20, READY/RUNNING, отставание 177 минут. Предпочтение −3. |

У этих профилей истёкших или активных clan join/create penalties в SQL нет. Для основания нужно предпочтение от 4. Возможность приглашения определяется отдельно по native условиям, отношению и решению получателя. Профиль 7 исключён из кандидатов и подтверждён отдельно, 141343 EXP и 6875 SP.

В 12:27 среди offline READY/MANAGED с HP=0 все 1372 имели фоновое состояние DEAD. Catchup был COMPLETE у 205, PENDING у четырёх и RUNNING у 1163. Семь старых offline записей 4025, 4203, 4338, 4977, 4960, 4573 и 4577 находились в очереди с неполной начальной подготовкой. Принудительная загрузка отсутствовала. Нативный профиль 1203 оставался мёртвым с HP 0/183 в одной эпохе не менее 130 секунд. Его расписание было SLEEPING, а удержанный владелец ещё находился в World. Код удерживает труп и требует календарную доступность для обычного fallback. Причина конкретного отказа 1203 не попала в журнал. У контрольного 105 живой HP был 202/202 при сохранённых 131. Массовый дефицит recovery и необходимость ослабления календаря или guards этим срезом не установлены.

## Изменения и границы записи

- `PhantomSystem` и `PhantomClanService` проходят вращающееся окно до 32 текущих владельцев. Поиск лидера отделён от приглашений. Фильтр кандидата применяется дальше первой восьмёрки. Сроки, бюджет Scheduler и обычный farm root сохранены.
- `PhantomClanService` и `L2jPhantomClanBackend` используют устойчивое подтверждённое свидетельство конфликта за ресурсы, гистерезис отношений и оценку цены. DECLARE выполняет уполномоченный managed-лидер. STOP адресован только его направленной записи. PEACE проверяет решение получателя в отдельном ходе, обе стороны и точный снимок направлений. Для двух wars завершение одного направления оставляет второе отдельным. Устаревшие решения не восстанавливаются из предложения после рестарта. Сохранены native create/join/leave и проверка membership epoch альянса.
- `ClanWarService`, `ClanSocialRepository` и `Clan` используют существующий ordered fence, проверку полномочий, точный warId, сверку памяти и адресного SQL-чтения. Ручные packet/GM, reply, restore и retirement используют прежних владельцев SQL create/delete. Признак роспуска синхронизирован с той же границей. Схема, SQL inserts/deletes и `clan.organization` v1/v2 сохранены. Подготовленный эффект использует CAS. Неопределённый effect без точного ack удерживает персональную клановую запись для сверки и не переигрывает мутацию. Внешний warId не становится квитанцией 022.
- `PhantomPvpContext`, `PhantomPvpService` и `PhantomCombatService` проверяют обе канонические записи войны, членство и допуск перед добровольным PvP и перед нативным действием. Используются действующие владельцы Party, defensive PvP, farm yield, STORE и подготовки. Наблюдение локальной угрозы союзнику работает в существующем бюджете целей. Межклановый executor помощи остаётся `NOT_READY`. Штатная защита другого игрока подтверждает угрозу через Party, отдельное право из принадлежности к альянсу не введено.

## Приёмка

| Критерий | Статус и свидетельство |
|---|---|
| K0 | `LIVE_OBSERVED` для клана, кандидатов, допусков и разделения текущего и сохранённого HP. Причина отдельных долгих recovery подтверждена частично, общий writer восстановления отделён от 022. |
| K1 | `CODE_READY / TEST_OBSERVED` для полного окна и совместимости с farm root. Рост клана 2→10 наблюдался до установки, на версии 021. Точная новая цепочка визита на Bartz ещё не наблюдалась. |
| K2 | `CODE_READY / TEST_OBSERVED`. DECLARE, отдельное согласие на PEACE, отказ, потеря предложения при рестарте, выбор лидера за первыми восемью и STOP без удаления встречной войны. Live wars и альянсы отсутствовали в срезе. |
| K3 | `CODE_READY / TEST_OBSERVED`. CP2: потеря подтверждения, конкурирующая ручная запись, CAS и прежние версии codec. Отдельный TEST33332 и полная native SQL-серия не запускались, штатные writer statements сохранены. |
| K4 | `TEST_OBSERVED` для текущей war authority и PvP. Помощь вне Party: наблюдение, `executor NOT_READY`. Live бой по войне не наблюдался. |
| K5 | `STOP_BLOCKED / NOT_DEPLOYED`. Ниже приведены точное условие и сохранённые данные. |
| K6 | OWNER_QA открыт. |

JDK 25.0.4.101 и Ant 1.10.17. Исходники компилировались из основного checkout по списку зафиксированных 2457 Java-файлов. Два локальных WIP, `VillageMaster.java` и `ClanMember.java`, участвовали только как точные blob baseline, хеши пользовательских файлов сверены. Ant собрал 572 test sources, OwnerProfile 38/38, XML/XSD PASS. Использованы `phantom-clan-checkpoint2-goal027-test` 14/14, `phantom-clan-alliance-membership-proof-goal027e-test` 9/9, `phantom-pvp-goal025a-core-test` 8/8, `phantom-pvp-party-help-test` 1/1, `phantom-pvp-admission-test` 3/3 и `phantom-pvp-goal025a-combat-regression-test` 20/20. Финальный Ant с `jar` завершился с кодом 0. RED на JAR 021 воспроизвёл отсутствие автоматического DECLARE и выпадение authority CLAN_WAR. Первая интеграционная попытка выявила, что запрос альянса мешал устойчивому STOP против текущего противника. Исправленный focused прогон зелёный. Полный `ant verify` исключён из-за DB-маршрутов и объёма.

## Установка и откат

Штатный `RequestShutdown` для исходного GS 2024 принят с кодом 0 в 12:41:30. Начальный drain превысил срок в `population_ecology`. Затем нативная materialization завершилась с нулём удержанных владельцев. Финальный STOP в 12:42:03 был отклонён на `ordinary_death`: `profile=2054`, `object=268540633`, `epoch=30488155729800`, `recovery.accepted_control_conflict`, `nativeReturned=true`, `deadlineExpired=true`. `_firstFailure` в установленной 021 держит эту ошибку, штатный повтор до доказанного разрешения причины запрещён. В 12:42–12:43 SQL подтвердил нули `online=2` и pending STORE, 5000 READY, 24 INCONSISTENT, 200 лавок, клан из десяти и сохранный профиль 7. Для 2054 SQL показал 1212 EXP, 105 SP, HP 29/156 и online=0. Эти строки сами по себе не содержат точной квитанции к указанной эпохе. LS и Bartz SQL продолжили работать.

В 12:45:44 адресная read-only трассировка GS2024 подтвердила две разные нативные квитанции. Control эпохи `30218599640800` содержит exact STORE на 3,851 с после её дедлайна. Для эпохи из защёлки `30488155729800` подтверждён нативный `CLEANUP_STORE` того же объекта с тем же execution stamp, владелец отсутствует. Дедлайн новой эпохи в памяти не сохранился. Прежний terminal control блокировал согласование следующей эпохи с `recovery.accepted_control_conflict`.

`PhantomBackgroundService` удаляет такой control только при доказанном позднем STORE прежнего объекта, эпохи и stamp. Следующая эпоха должна либо владеть уже принятым нативным возвратом и проходить его действующий допуск, либо иметь точный своевременный STORE. Ревью выявило ветку, где новый STORE успевает до опроса. RED воспроизвёл её, затем `PhantomLivingWorld114Checks --ordinary-goal129` прошёл на итоговом JAR с положительными и отрицательными проверками предиката. Исправление предотвращает повтор этого конфликта. Уже запущенная 021 сохраняет защёлку и требует отдельного решения владельца.

В 12:55 readback подтвердил видимые окна GS2024 и LS10736, SQL18764 и исходную пару `dist`. `STATUS.cmd` сообщает о живом процессе GS, при этом его внутреннее состояние Phantom остаётся `FAILED`. В Bartz 0 ordinary и 0 scouts с `online=2`, 0 pending STORE и 200 сохранённых лавок. Рабочим игровым сервером этот режим не считается. Thread dump GS не содержит сообщения о JVM deadlock. Запрос к InnoDB lock waits не выполнен из-за отсутствия привилегии PROCESS. Отдельный TEST процесс не запускался.

- Итоговый `ant jar` с кодом 0 на `6b6c36cf4097e941b153031e38a5759f1b4b02c9`. На этой паре повторно выполнены все 55 focused проверок и отдельная recovery проверка. SHA-256 GameServer `50FAA2D4B86A1F82335F272CC0CB2BB411499CF33761A68D9FE8304A13933E7D`, BUILD_INFO `5B4B5BA1A151EA1C5C4145A03A13117FB29973E33E999BE3E6A458DE62823E91`. Установка, live smoke 022 и контрольный STOP/START кандидата ожидают разрешения блокировки.
- Точный откат в `D:\Backups\PhantomWorld\PHANTOM-AUTONOMOUS-CLAN-DIPLOMACY-022\r01`. Четыре файла, 12 855 267 байт, включая RESTORE.txt и SHA256SUMS.txt. Проверены GameServer `7F9D9013B29FA2B6988B21A0E46091190D0E7739CC53A6FA70DE5B610034A729` и BUILD_INFO `28805AF22CEF5084C0EEE95CEADCE2D6B9D62397AD983F77118C266F38B6C3BF`. План отката сохраняет штатное состояние SQL и learned.
- Scratch `D:\Temp\Codex\PhantomWorld\PHANTOM-AUTONOMOUS-CLAN-DIPLOMACY-022\r01`. Удалено 58 824 015 байт промежуточной Ant-сборки, 7075 файлов. Осталось около 15,4 МБ, включая точную пару `candidate\GameServer.jar` и `candidate\BUILD_INFO.json`, stdout focused suites, действующие штатные скрипты и адресные свидетельства STOP. Диагностические JAR удерживаются GS2024 до выхода JVM. MAIN3308, принудительный выход, HotSwap и прямые SQL-записи игровых данных не применялись.

## Scope и Git

Исходные чтения из READ_SET, README, инструкции корня и модуля, контекст и manifest дополнены ограниченными диапазонами `Clan`, `PhantomCombatService`, `PhantomPresenceRegistry`, `PhantomOrdinaryDeathRecovery`, `PhantomBackgroundService` и `Shutdown`. Это границы роспуска, PvP-действия, допуска и наблюдаемого STOP. Обоснованный scope составляет 10 production-файлов, 3 focused fixtures и этот отчёт. Пакет, пользовательские конфиги, WIP, learned, живой dist и SQL-данные исключены из commit.

Git-проверки включили `git symbolic-ref --short HEAD`, `git rev-parse HEAD refs/remotes/origin/feature/phantom-world`, `git remote get-url origin`, `git status --short --branch` и `git status --porcelain=v1 --untracked-files=normal`. Сверены `git merge-base --is-ancestor <baseline или STORE SHA> HEAD`, `git ls-remote --exit-code origin refs/heads/feature/phantom-world` и `git -c gc.auto=0 fetch --no-tags --no-write-fetch-head origin refs/heads/feature/phantom-world:refs/remotes/origin/feature/phantom-world`. Точные build inputs отобраны через `git ls-tree -r --name-only <baseline> -- <java, test/java, test/resources>` и два `git show <baseline>:<WIP path>`. Scope проверен через `git diff --name-only <baseline> <source>`, `git -c core.whitespace=cr-at-eol diff [--cached] --check -- <точные пути>`, `git add -- <точные пути>`, `git commit --only -m <subject> -- <точные пути>` и `git diff-tree --no-commit-id --name-only -r HEAD`. Код оформлен в `c1e0a0d02cf1b34c91e1d3bf6adef4702b6c3390) и `6b6c36cf4097e941b153031e38a5759f1b4b02c9` в разрешённой ветке.

`git -c credential.interactive=never push origin HEAD:refs/heads/feature/phantom-world` завершился с кодом 0. Независимый `ls-remote --exit-code` вернул source `6b6c36cf4097e941b153031e38a5759f1b4b02c9`. Отчёт публикуется отдельно от сборки.

- Mojibake-маркеры в изменённых файлах проверены.
- Escaped Cyrillic в изменённых файлах проверен отдельно.
