# PHANTOM-DEDICATED-GEO-SCOUTS-008

## Аудит перед реализацией

**Статус** `CODE_READY / LIVE_NOT_RUN`. Владелец согласовал код и изолированный TEST после предварительного аудита. `OWNER_GATE=REQUIRED` для живой поставки.

- 23.11.2026 проверены локальная ветка и актуальный GitHub HEAD `feature/phantom-world`. Оба указывают на `29de041b12e603178434809694fc941ade9f5cc9`. Родитель локального HEAD — `36e2bbf6a018c28efe825e0a8bcf7e7588102aef`. Проверенные Java-исходники и `build.xml` без пользовательских правок.
- В checkout есть пользовательские INI, 86 путей learned и новый `PhantomSemanticStudio/src/PhantomSemanticStudio.WinForms/V4ImportForm.resx`. Пакет задачи локальный. Ни он, ни WIP в состав публикации не входят.
- На хосте найдены JDK 25.0.4.1, Ant 1.10.17 и MariaDB 11.4 с утилитами установки, выгрузки и восстановления. В поставке найдены 203 файла `.l2j`. Доступность конкретных стартовых точек потребует проверок в TEST.
- Проверка списка процессов и TCP на момент аудита не показала Java-процессов и слушателей на 2106, 7777, 33318, 33331. Порт 3308 слушал `mysqld` PID 7884. SQL-соединений не открывалось. Аудит live grant и пробы записи отложены до отдельного окна владельца.
- Сборки, Git-записи, запуск процессов, SQL и изменения рабочих данных на этапе A не выполнялись. Автоматический запуск живой поставки не входит в текущий Goal.

Контрольные SHA-256 локальных артефактов этапа A

| Артефакт | SHA-256 |
| --- | --- |
| `dist/libs/GameServer.jar` | `668F440B82C0E56D875872C138FC8C167297F87423070D068AC82BE5D7C7C3ED` |
| learned `physical-scout-witness.tsv` | `96001BA41DE19BB132C0416BDBD299C805A4D2B85A49910F98BA4C78EC0CC2D9` |
| learned `region-0_0.tsv` | `A5D7837BBDADEF4B8B2979972F8527585FEA3E15128E00FC0BDCE0C06405958B` |
| Studio `V4ImportForm.resx` | `4363CD7D5B8671C72442CE1A1BFC10D64EBD24B2D718B54BD4FCD025E4967298` |

### Владельцы и запись

| Граница | Наблюдение и требование к реализации |
| --- | --- |
| `accounts`, `characters` | `PhantomPopulationStore.prepareAccount/prepareCharacter` фиксируют intent и точную принадлежность, создают отключённый аккаунт и вызывают `Player.create`. Клиентский `CharacterCreate` использует тот же `CharInfoTable` для защиты имени. SQL-индекс `characters.char_name` допускает дубликаты. Перед CREATE и при возобновлении проверять все совпадения по имени и аккаунту, сохранять ожидаемый `charId`, отказывать при неоднозначности. Проверить движки таблиц TEST до опоры на транзакцию. |
| Обычные профили и reset | `PhantomProfileRepository` связывает персонажа с обычным профилем, `PhantomPopulationResetService` собирает identities по population-компоненту, а затем удаляет точные связанные ID. Служебный roster хранить отдельно от обеих таблиц профилей. Найденная связь с любым обычным профилем блокирует допуск. |
| Реальный вход и offline store | LoginServer запрещает вход отключённому аккаунту. Материализация также проверяет реестр identity, World и autosave перед загрузкой. `OfflineTraderTable` имеет собственный путь загрузки и записи. Чужой World-объект, real-login lease и запись offline trader блокируют служебный допуск. |
| Загрузка и запись native Player | `PhantomMaterializedPlayer` захватывает identity lease, подцепляет `PhantomNativeWorkScope` при `Player.load`, проверяет точный autosave owner, привязывает outbound и проверяет обе карты World. Обычная ветка вызывает quest login и auto-noblesse. Служебная ветка должна пропускать их и выполнять строгий штатный STORE с удержанием owner и permit при неопределённом результате. |
| Native action admission | У `Player.attachPhantomNativeActionAdmission` есть точная проверка `OwnerKind.PHANTOM`. Для `DEDICATED_SCOUT` потребуется отдельная проверка в этой точке. `PhantomExecutionAdmission` умеет выдавать exclusive idle permit с поколением. Служебные допуски выдавать только из отдельного реестра с публикацией проверенной durable identity и отзывом свежей работы при drain. |
| Обычный gameplay и capacity | `tryBeginPhysicalAdmission` защищает обычную population, но не является общим лимитом. Общий semaphore принадлежит `PhantomMaterializationService`. Вычитать dedicated entries из ordinary occupancy и резервировать ordinary target под тем же monitor, где захватывается permit. Считать реальных игроков при проверке общего World-бюджета. |
| Route learning | `PhantomPhysicalGeoScout` вызывает общий `submitScoutWalk` с низким приоритетом. `PhantomRouteLearning` пишет native witness перед увеличением `PROVEN`. Служебное размещение и смена региона сбрасывают текущую трассу. Для записи доказательства сохраняется требование точного объекта, epoch и receipt завершённого MOVE. |

### План реализации

1. **Идентичность.** Ввести одну добавочную таблицу служебных слотов с уникальными slot, account, name и nullable unique `charId`. Сохранить маркер роли и intent до внешних эффектов. Привязать создание отключённого аккаунта к сохранённому intent с уникальным токеном, сохранив atomicity там, где обе записи используют проверенный InnoDB. Вызвать `Player.create` через подтверждённый аналог в `PhantomPopulationStore`. При рестарте подхватывать только проверенный этап того же intent. Повторное появление чужого или отсутствующего ранее привязанного `charId` переводит слот в отказ с причинами. Count ограничивает рабочие слоты, OFF оставляет durable roster.
2. **Допуск и STORE.** В `PhantomMaterializationService.java` добавить явный служебный вход с подтверждённой identity и отдельным owner. Переиспользовать общую проверку объекта, semaphore, cleanup и shutdown. В `PhantomMaterializedPlayer.java`, `PhantomIdentityLeaseRegistry.java` и точечных местах `Player.java` провести роль через существующий `PlayerNativeWorkScope` и отдельный execution grant. `PhantomSystem.java` включает контур только при выбранном режиме и останавливает его до общего native drain. Служебный контур держит место при retained cleanup и публикует `FAILED_RETAINED`. Занятые места учитываются атомарно, с резервом обычной цели и приоритетом обычных запросов.
3. **Разведка.** В `PhantomGeoTrainer.java` и `PhantomPhysicalGeoScout.java` добавить явно включаемый режим `DEDICATED`, Count до 32 и `ALWAYS`. Сохранить legacy-режим. Служебный контроллер выделяет по одному региону на актор, проверяет слой, NSWE, двери, воду, collision, зоны, монстров и instance 0 перед разрешённым стартом. Региональный курсор и история хранятся атомарно под GeoData fingerprint. Используется общий WALK и witness. При stuck ограничить число повторов, пройти назад по подтверждённому пути либо освободить задание и отложить район. Прогресс учитывается по уникальным маршрутам с receipt. Заявки первой получают существующие игровые контуры, у разведки ограничены очередь, частота запросов и выпуск новых акторов.
4. **Безопасность.** До World spawn установить служебный режим с допуском только необходимых действий. Проверить реальные входящие пути для PvP, обмена, party, offline store и наград, включая действия другого игрока и эффекты по площади. В `Player.java` использовать узкие проверки роли в подтверждённых общих точках этих действий. Уход из опасной зоны и повторное размещение после смерти проводить через native lifecycle. На перегрузке приостановить выдачу заданий, при устойчивом давлении освободить часть служебных мест со STORE. Отдельно проверить, что обычный gameplay сохраняется.
5. **Проверки и выпуск.** Добавить focused red→green в существующий Ant runner, затем один native smoke от рождения до фактического WALK и STORE. Использовать отдельную сборочную копию без нового worktree, собственный MariaDB datadir и loopback-порты после сверки PID, SQL schema и конфигурации. При зелёной сцене одного актора продолжить 4→8→16→24→32 с 8 ordinary. Подтвердить T0–T11 установленными в TEST_MATRIX доказательствами, включая cold restart и ≥20 разных разведчиков с ARRIVED в ≥8 регионах. Отдельно выполнить `phantom-navigation-core-test`, `phantom-world-coordination130-test` и `ant jar` в копии. Commit/push допустимы после green gate и проверки exact paths.

Предлагаемое ограниченное исключение для scope свыше десяти файлов охватывает один служебный контур. В него входят перечисленные точки Java-интеграции, новые классы roster и региональных назначений, добавочная SQL-схема, тесты существующего runner, `build.xml` и этот отчёт. Точный список файлов фиксируется перед реализацией после проверки точек входа safety и SQL. Рабочий `PhantomGeoTrainer.ini` уже изменён пользователем. Значения по умолчанию задаются в конфигурационном reader, оператор получает INI-фрагмент в отчёте. Новых зависимостей нет.

### Реализованный контур

Исходный `PhantomPopulationStore` послужил аналогом для birth saga, `PhantomMaterializedPlayer` для жизненного цикла, `PhantomPhysicalGeoScout` для native доказательств. Прочитаны пакет задачи, ближайшие `AGENTS.md`, корневой `README.md`, актуальный статус, workflow, efficiency standard, релевантные разделы master plan и package standard, точки навигации, materialization, bootstrap, reset и native admission, DDL и Ant targets. Локальный module `AGENTS.md` существует вне HEAD. Карта интеграции есть в `SOURCE_MAP.md`. Отдельный pattern-файл для этого Goal не найден.

- Служебные имена `GeoScout01`–`GeoScout32`. Слоты хранятся в `phantom_dedicated_scouts`. Для служебной подготовки выбран native уровень 76 с каноническим опытом и HP/MP. Игровые предметы, навыки и автогранты отсутствуют.
- Региональные станции назначаются из доступных GeoData-тайлов по кругу с накопленным приоритетом. Актор меняет район через STORE и новое штатное появление на проверенной точке. Пройденные ноги остаются в общем журнале маршрутов. Текущий WALK ограничен арендованным тайлом.
- Служебные рождения инициируются по одному за раз. Dedicated не использует burst-резерв ordinary. При длительном превышении 90% heap, 80% CPU процесса или HIGH scheduler pressure контроллер приостанавливает новые WALK и освобождает служебные места со STORE. Место на World оставляет отдельный резерв живого входа.
- В штатном `STORE` подтверждается обновление ровно одной знакомой строки с точными account/name и `online=0`. При неопределённой native очистке сохраняются lease и permit. Штатные пути отключения и входа распознают удержанного служебного владельца.

### Доказательства TEST

TEST использует MariaDB 11.4.13 `127.0.0.1:33608/l2jmobiush5_phantom_test_008`, GS 17968, LS 19268 и межсерверный канал 18068. Сверены PID, datadir, локальные listeners, grants и запрет чтения чужих схем. GeoData203 скопирована в отдельный runtime с точной проверкой SHA-256 каждого файла. Логи и JSON-доказательства находятся в `.phantom-local/logs/PHANTOM-DEDICATED-GEO-SCOUTS-008/`.

| Проверка | Результат |
| --- | --- |
| RED штатного reader | `phantom-dedicated-geo-scouts-008-test`, exit 1, legacy граница 16 блокирует запрос DEDICATED 32. |
| GREEN focused | `phantom-dedicated-geo-scouts-008-test`, exit 0. Дневное окно ALWAYS на 32, граница LEGACY, аренда, backoff, восстановление курсора и отклонение чужого fingerprint. |
| Соседние регрессии | `phantom-navigation-core-test` и `phantom-world-coordination130-test`, exit 0. На финальных классах повторены штатные entrypoints `navigation-core` 51/51 и `world-coordination130` 81/81. Оба focused entrypoint финальных классов тоже exit 0. |
| Сборка после review | `ant jar` в отдельной копии, exit 0. `final-jar.log`. |
| Первый native gate | `proof-count1-stock-pressure-and-walk.json`. Один подтверждённый native ARRIVED в журнале, один новый PROVEN, один одновременно materialized, 2 STORE в ходе регулирования нагрузки. 200 независимых offline merchants были включены в исходном TEST-пресете. |
| Cold и OFF | `proof-count1-stock-stopped.json`, `proof-count1-controlled-cold.json`, `proof-off-enabled-ordinary.json`. Штатный STOP, `online=0`, идентичный слот после холодной загрузки, новые native ARRIVED. При OFF один dormant scout и восемь ordinary headless. |
| Атомарность bootstrap | В TEST временный trigger запретил binding слота 32. После ошибки roster содержал 31 bound и один intent версии 0. Слот 32 не оставил аккаунт и персонажа. Триггер снят, intent остался для штатного retry. |
| Нагрузка на 4 GiB | Подъём дошёл до 31 разведчика в World и 8 ordinary до снятия тестового отказа. При 3 976 200 192 из 4 294 967 296 байт used сработал pressure gate. Снимок после разгрузки показал 28 active и 8 ordinary. Зафиксированы 74 checksum-подтверждённых ARRIVED от 26 scouts в 26 регионах. |
| Штатный retry слота 32 | `proof-count32-4g-after-retry.json`. Тот же durable intent связал единственный аккаунт и персонажа. 32 bound, 32 native HEADLESS в World, 29 moving и 8 ordinary. |
| Окно 10 мин 16,94 с | `proof-count32-4g-after-retry.json`, `proof-count32-soak-midpoint.json`, `proof-count32-soak-finish-moving.json`. В World 32 → 32 → 31, moving 29 → 27 → 26, ordinary 8 → 8 → 8. В конце окна 1 384 подтверждённых ARRIVED от всех 32 разведчиков в 92 из 203 георегионов, 1 383 новых PROVEN. Подтверждённые переходы между тайлами отсутствуют. |
| STOP во время WALK | `proof-count32-soak-stopped.json`. Штатный shutdown, exit 0. Разведчиков в World 0, moving 0, у всех 32 `online=0`. Ротация, backoff и завершение дали 110 STORE в ходе всего запуска. Хеш roster одинаков до и после. Итоговый witness содержит 1 392 ARRIVED после финального flush. |
| Cold Count4 и коллизия ownership | `proof-count4-slot1-fail-closed.json` и `proof-count4-slot1-recovered.json`. В TEST слот 1 с изменённым disabled accessLevel `-2` остался offline. Три других актора продолжили WALK. После восстановления `-1` тот же слот подключился штатно. В World 4, ещё 28 dormant, хеш всех 32 identities прежний. Оба JVM завершены штатно. |
| Backup/restore и остановка TEST | `test-backup-restore.json`, `test-scope-final.json`, `test-final-byte-provenance.json`. В отдельную схему того же изолированного MariaDB восстановлены 153 таблицы, 32 bound, 248 аккаунтов и 248 персонажей. Канонический повторный dump байт-в-байт совпал, SHA-256 `482A4331DFBA56E100D3FCB6D38088F2EAB7997DB078121F84C63593946EFB8E`. Собственные GS, LS и SQL завершены штатно. Сигнатура защищённых портов и все 203 файла GeoData совпали с исходными. |

SHA-256 JAR для начатого native прогона. GameServer `BB67A15C94C62BA106B1806C625F190A5E0A5E175B04A283DF211CDD15BD3AD7`. LoginServer `598DC5140144A124E4F99E4CFAE44B49C40B304A61832E66D45DA6D3DBC63024`.

Все три снимка окна сделаны на одном GameServer с `-Xms2g -Xmx4g`. Heap used 3 640 655 872 → 3 816 816 640 → 3 737 124 864 байт. В конце committed 4 102 029 312 и max 4 294 967 296 байт. CPU процесса 1,15 → 2,00 → 0,83%. Счётчик GC 229 → 303 → 381. Наблюдаемый максимум очереди PathFinding 1, штатный лимит workers 2. Среднее время ожидания в очереди не измерялось. Обычный TEST включал восемь физических фантомов. Независимые offline merchants отключены в контрольном окне, их fixture-строки сохранены.

SHA-256 полного набора из 32 служебных идентичностей `e465e32a935ee713f6e3ecc193d0ae47310655fb6e9852593ce78d96363fa453`. В TEST нет пересечения с обычными профилями, клиентского login, служебных строк предметов, навыков или квестов.

TEST INI для Count32. `PhantomGeoTrainer.ini` SHA-256 `F2905FF51220B7BB2E86E0FA8A93A1DCBC34F7845688E1EF25F3AF29CE722A8B`. `PhantomPlayers.ini` SHA-256 `D9D70EED97E698A50209DD3EF059BD5D9EB6A399D0D93E1B66C0DF54D5607A7E`.

| Gate | Статус и границы свидетельства |
| --- | --- |
| T0 | PASS. OFF на том же JAR c включёнными ordinary, без новых служебных identities, активных scouts и записи служебного статуса. |
| T1 | PASS. 32 unique slot/account/name/charId, disabled login, accessLevel персонажей 0, пересечение с обычными профилями 0. |
| T2 | PASS. Откат native birth после принудительного отказа при binding, тот же intent успешно привязан при retry, cold restart сохранил identity. Аварийное завершение ОС во время commit отдельно не моделировалось. |
| T3 | PASS. Последовательное заполнение по одному актору до 32 одновременно в World с 8 ordinary. Один общий cap 48. Пороги 4, 8, 16, 24 пройдены при заполнении без отдельных стационарных прогонов каждого порога. |
| T4 | PASS. Отдельный реестр и execution owner. Нет зависимости от READY, ordinary schedule или исторического catch-up. Обычные фантомы TEST сохраняют свои identities и физический бюджет. |
| T5 | PASS. 203 кандидата, 116 накопленных подтверждённых высадок в разных тайлах к концу окна. Аренда, backoff и checkpoint проверены отдельно. Наличие .l2j само по себе не даёт разрешение на высадку. |
| T6 | PASS по native MOVE и ARRIVED. 32 разных исполнителя, 92 разных тайла, 1 383 новых PROVEN за окно. Служебные высадки отделены от CHECKPOINT/WALK доказательств. Cross-tile WALK `NOT_RUN`, ноги ограничены арендованным регионом. |
| T7 | PASS по общему worker budget и реакции на heap pressure. Отдельное распределение времени ожидания в очереди `NOT_RUN`. |
| T8 | PARTIAL. Ошибка ownership в cold Count4 доказала fail closed при несовпадении disabled accessLevel. Проверены нулевые строки items/skills/quests и отсутствие game login. Отдельные реальные PvP-входы, смерть NPC и вода проверены по коду и focused seed checks. Отдельные native сцены для них `NOT_RUN`. |
| T9 | PASS по движущимся участникам и штатному STOP до offline=32, cold restart и меньшему Count. Искусственная потеря связи с SQL внутри terminal STORE `NOT_RUN`. |
| T10 | PASS по focused, навигации, координации и изолированному JAR. Общий `ant verify` с зависимостями от общей базы не запускался. |
| T11 | TEST restore PASS. LIVE_NOT_RUN. Живое окно, проверка grants на Bartz, репетиция его backup и применение миграции требуют отдельного разрешения. |

### Точная область исходников

Под `java/org/l2jmobius/gameserver/` изменены только следующие файлы.

| Путь | Изменение |
| --- | --- |
| `phantoms/PhantomSystem.java` | Подключение служебного контура и распознавание владельца при остановке. |
| `phantoms/player/PhantomDedicatedScoutRoster.java`, `PhantomIdentityLeaseRegistry.java`, `PhantomMaterializationService.java`, `PhantomMaterializedPlayer.java` | Durable identity, принадлежность, общий физический лимит, action grant и STORE. |
| `phantoms/navigation/PhantomDedicatedScoutRegions.java`, `PhantomGeoTrainer.java`, `PhantomPhysicalGeoScout.java`, `PhantomRouteLearning.java` | Безопасные стартовые точки, региональная аренда, pacing, native receipt и checkpoint. |
| `model/actor/Player.java`, `model/skill/Skill.java` | Каноническое рождение и загрузка с проверкой владельца, строгая запись служебного Player, ограничение игровых взаимодействий. |
| `model/clan/ClanInvitationService.java`, `model/groups/PartyInvitationService.java`, `services/DirectTradeService.java`, `network/clientpackets/RequestSendPost.java` | Охранные проверки служебной роли для клана, группы, обмена и почты. |
| `LoginServerThread.java`, `network/GameClient.java`, `network/clientpackets/CharacterSelect.java` | Защита при коллизии входа и корректный реплей клиентских сессий. |

Плюс `build.xml`, `dist/db_installer/sql/game/phantom_dedicated_scouts.sql`, два focused теста `PhantomDedicatedScoutChecks.java` и `PhantomDedicatedScoutRegionChecks.java` в пакете native navigation и этот отчёт. Всего 23 файла. TEST-скрипты, вывод Ant, runtime INI, witness, checkpoints, GeoData и JAR хранятся в изолированных локальных каталогах.

Проверка diff учитывает CRLF (`core.whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol`). Контрольные SHA-256 локального JAR, witness, региона и Studio из этапа A совпали при повторной проверке.

- Mojibake-маркеры в изменённых файлах проверены, совпадений нет.
- Escaped Cyrillic в изменённых файлах проверена, совпадений нет.

### Учёт выполнения

Начальный аудит по 12 первичным файлам READ_SET дополнен локальным AGENTS, рабочими документами, схемами accounts/characters/profiles, точками native Player и LoginServer, offline store, trade/party/clan/mail, Skill и штатным shutdown. Дополнительные чтения проверяли конкретные пути записи и обхода owner. До первого изменения выполнено шесть репозиторных поисков. Запуск полного `ant verify` и проверка release-артефактов вне этой задачи не выполнялись.

В изолированной копии выполнены RED конфигурации, GREEN focused, интеграционная компиляция, целевые navigation/coordination и две сборки `jar`. Второй JAR понадобился после исправления точек fallback при login и shutdown и до начала зачётного native TEST. Четыре штатных Java entrypoint финальных классов прошли повторно. Региональный тест отдельно подтвердил RED при отсутствующем классе и GREEN после реализации. Общих Ant verify 0, глобальных статических verify 0. Первый TEST preset содержал несовместимые `MaxScheduledPhantomProfiles=8` и `MaxMaterializedPhantoms=48`, поэтому stock config отключил Phantom. Для следующего старта исправлен только TEST preset. Начальный restore helper получил неподдерживаемый флаг dump, затем ложный ненулевой результат импорта с пустым stderr. Результат сверили по точным данным и идентичному повторному dump.

Снимок Goal перед публикацией показал 1 188 869 учтённых токенов и 3 073 с. Превышение обычного бюджета связано с параллельным аудитом пересекающихся native writers, отдельным ревью shutdown/login, изоляцией стабильного TEST и серией холодных прогонов с отрицательными контролями и измерительным окном.

### Git

Использованы `git status --short --branch`, `git status --short --untracked-files=no` и scoped `git status --short --untracked-files=all -- <allowlist>`, `git rev-parse HEAD`, `git rev-parse --show-toplevel HEAD^`, `git rev-parse --verify HEAD`, `git rev-parse refs/remotes/origin/feature/phantom-world`, `git symbolic-ref --short HEAD`, `git remote -v`, `git worktree list`, `git for-each-ref --format='%(refname:short) %(objectname) %(subject)' refs/heads`, `git ls-tree HEAD -- L2J_Mobius_CT_2.6_HighFive/AGENTS.md`, `git ls-remote origin refs/heads/feature/phantom-world`. `git archive` экспортировал только разрешённые каталоги подтверждённого HEAD в отдельную сборочную копию. Для контроля scope использованы `git diff --check -- <allowlist>`, `git diff --stat -- <allowlist>` и `git diff --cached --name-only`.

Публикация текущего набора использует `git add -- <allowlist>`, `git -c core.whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol diff --cached --check`, `git commit --only -m "phantom(geo-scouts-008): add dedicated native scout roster" -- <allowlist>`, `git push origin HEAD:refs/heads/feature/phantom-world` и readback того же ref. Это scoped изменение запрошено владельцем. Родитель публикации `29de041b12e603178434809694fc941ade9f5cc9`. Сохранены локальные пользовательские правки и все существующие worktrees.

## Порядок поставки после зелёной приёмки и согласия владельца

1. Подтвердить точные PID и корни Bartz, версии JDK и MariaDB, схему, порт 33331, типы таблиц, права приложения и владельца окна. Убедиться, что Bartz GS/LS остановлены. Сохранить текущие JAR, INI и learned вместе с контрольными SHA-256. Снять SQL backup и проверить восстановление в отдельном экземпляре MariaDB. Проверить восстановленные таблицы и количество строк. Для migration-пользователя и runtime-пользователя использовать раздельные закрытые файлы параметров клиента.
2. На Bartz с флагом OFF применить только добавочную `dist/db_installer/sql/game/phantom_dedicated_scouts.sql`. Сначала выполнить read-only проверку `SELECT @@port, DATABASE(), @@datadir` тем же клиентом с явными `--host=127.0.0.1 --port=33331 --database=<подтверждённая-схема-Bartz>`. Сверить с подтверждённым PID и datadir. Применить файл только с migration-учётной записью. Проверить уникальные ограничения, InnoDB и доступ runtime к roster, `accounts`, `characters` и охранным чтениям. Проверить отсутствие коллизий со слотами 01–32, отключённость существующих служебных аккаунтов и отсутствие связи с обычными профилями. Аккаунты и персонажей создаёт GameServer через штатный bootstrap.
3. Из проверенного SHA собрать `ant -f <изолированная-копия>/L2J_Mobius_CT_2.6_HighFive/build.xml jar`. Сверить оба SHA-256, установить только согласованные бинарники из сборочной копии. Сохранить рабочие INI и learned. Проверить сервер с OFF, затем штатно остановить. Применить следующую настройку только в согласованном окне.

```ini
EnableRouteLearning = True
EnablePhysicalGeoScouts = True
PhysicalGeoScoutMode = DEDICATED
PhysicalGeoScoutSchedule = ALWAYS
PhysicalGeoScoutCount = 1
```

4. Запустить Bartz в согласованном окне. Проверить одного служебного native Player и хотя бы один earned `ARRIVED`, затем штатно остановить и сверить `STORE`. Поднимать Count до 4, 8, 16, 24 и 32 при наличии ресурсов. Для ordinary target 8 и 32 scout нужно минимум 40 общих мест в `MaxMaterializedPhantoms`. Burst-места остаются обычной нагрузке. Проверять heap, GC, CPU, PathFinding, обычную численность и запас для людей на каждой ступени. Размер heap менять отдельным решением владельца.
5. Для отключения дождаться `STOPPED`, проверить `materialized=0`, `FAILED_RETAINED` и `online=0` для служебных ID. Затем поставить `EnablePhysicalGeoScouts=False` и запустить сервер штатно. Для бинарного отката после того же STOP вернуть подтверждённые JAR/INI. Служебные записи и накопленные маршруты сохраняются. Не удалять roster и не выполнять восстановление SQL поверх живого экземпляра.
