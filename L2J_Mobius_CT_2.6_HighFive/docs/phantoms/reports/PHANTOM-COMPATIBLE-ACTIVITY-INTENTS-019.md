# PHANTOM-COMPATIBLE-ACTIVITY-INTENTS-019

27 ноября 2026, Кишинёв. Поставка и наблюдения относятся к Bartz33331, схема `l2jmobiush5_phantom_test_131`.

**PARTIAL по live-приёмке. CODE_READY и DEPLOYED подтверждены. LIVE_OBSERVED для запуска, населения, изменения сохранённого прогресса и восстановления saved HP. LIVE_NOT_OBSERVED для кланового визита и полной цепочки native kill → reward → STORE. OWNER_QA ожидает владельца.**

## Исходный срез без перезапуска

- Ветка `feature/phantom-world`, локальный HEAD и свежий GitHub SHA `32702a2c7fdd3ae196d0af73d1492702587f9f88`. Ancestry совпала, семь файлов пакета прошли проверку размера и SHA-256. Рабочий r14 был собран из `44a8d48b6e6a923175954cbfca0859b54ef4d4d4`. GS 29900, LS 20168, MariaDB 28664. JDK показал ZHeap 10 134 MiB из 12 288 MiB.
- В 10:15 и 10:21 повторное SQL-чтение показало 5 024 связанных профиля, 128 `online=2`, 699 saved HP=0 и ноль сочетаний HP=0 с `online=2`. Суммы EXP и SP оба раза составляли 2 730 200 и 212 010. 4 694 связей были уровня 1–3, включая 24 quarantine уровня 1. Из четырёх профилей уровня 10+ все четыре были офлайн, 9 и 17 имели saved HP=0. Профиль 7 оставался офлайн с 141 343 EXP и 6 875 SP. Эти факты описывают сохранённые записи. Точное состояние здоровья в World и получение recovery claim для этих офлайн-профилей не установлены.
- К 10:39 на r14 зафиксированы 5000 READY, 128 ordinary и 31 из 32 зарезервированных scouts по SQL, 200 сохранённых лавок, 24 quarantine и 0 pending STORE. EXP вырос на 9 260, весь наблюдаемый прирост показал профиль 1060. Суммарный SP вырос на 300. У профиля 38 сохранённый SP уменьшился на 150, квитанция конкретной операции отсутствует. При штатном drain r14 и последующем стартовом срезе сумма достигла 2 782 721 EXP и 215 840 SP. Этот прирост относится к r14.
- 1 340 сообщений `GOAL_ABSENT` относились к 1 340 разным профилям. Во всех стоял `OWNED_STORE_OPTIONAL_LINEAGE_REFUSED`. Ветка `PhantomBackgroundService.prepareStore` выбирает native-only STORE, когда одновременно отсутствуют загруженная цель и предыдущее background-состояние. Она сохраняет проверки native owner и readback. Пример 4368 остался связан и сохранён с HP 107. Причинной связи между этими предупреждениями и потерей активного farm root в проверенном окне не найдено.
- При разборе 178 предупреждений farm stall у 147 причиной был `NO_SELECTABLE_NATIVE_FARM_TARGET`, у 31 — `NO_USEFUL_NATIVE_FARM_PROGRESS`. Для 25 и 1060 видны `WRONG_NPC` и `VIABILITY` у фактической выборки целей. У 538 повторялся `NO_PATH_WITHIN_SEARCH` из одной точки, включая альтернативные якоря и конечный backoff. 40 drop warnings отражали вызовы `Creature` с идентификаторами выпавших предметов. Повторное предупреждение само по себе не устанавливает неудачную выдачу награды или сбой STORE. Общая причина медленного развития всей когорты и доля каждого вида простоя не установлены.

## Изменения

- `PhantomNativeEquipmentPolicy.java` учитывает пригодность оружия и основных частей брони классу и прежний порог существенного долга грейда. Защита зачарования и специальных эффектов остаётся в проверке замены. `L2jPhantomSupplyJourney.java` убирает свежий `income.farm` из исполнимых предложений. При отсутствии законного источника или денег сохраняется обычный кандидат фарма или другое доступное занятие. Уже принятая supply-запись проходит прежнее завершение.
- `PhantomClanService.java` предлагает FOUND, DEVELOP_L1 и CREATE_ALLIANCE по прежним требованиям к уровню, полномочиям, бюджету, peer и cooldown. `L2jPhantomClanBackend.java` проверяет тот же guard подготовки для оплаты уровня и требует точный живой экземпляр мастера в области взаимодействия и видимости. Приглашения и ответы идут через существующий короткий ход 017.
- `PhantomClanVisit.java` добавляет конечный кандидат к существующему DecisionEngine. Рассматриваются точные spawn факты семи подклассов VillageMaster. Выбор среди короткого списка учитывает проверенное время маршрутов. Шаги проходят через штатную навигацию, WALK, бесплатный native Gatekeeper и проверку угрозы. Return со свитком в этот срез не включён, чтобы маршрут не вводил ещё один расходуемый эффект. Окно предложения задано через действующий `phantoms.clan.discoverySeconds`, по умолчанию четыре интервала по 60 секунд.
- `PhantomBackgroundDecision.java` ранжирует обычное ожидание farm travel как фарм. Приоритеты recovery и pending STORE сохраняются. `PhantomVisibleAutoPlay.java` освобождает точный запрос уступки, если визит отменён. `PlayerAI.java` даёт визиту режим немедленного MOVE, который не сохраняется для позднего выполнения при блокировке или прерывании toggle-кастом. Обычный MOVE сохраняет прежнюю очередь.
- `PhantomSystem.java` связывает актуальный и принятый native intent с materialization, Decision, Party yield, ACTUAL_ATTACK и STOP. Проверяются actor, class, activity generation, execution stamp и исходная ревизия. При несовпадении или уходе NPC управление возвращается через существующий planner от фактической позиции. Клановый визит не создаёт новую таблицу, компонент профиля или root. После restart предложение строится заново, принятые клановые эффекты подтверждают прежние native writers и receipts.
- Допустимое расширение охвата до 12 файлов исходников и тестов связано с общим трактом владения и двумя регрессиями. Тесты добавлены в `PhantomClanGoal027Checkpoint1Suite.java` и `PhantomPickup109Checks.java`, новый DB-free selector — в `PhantomTestLauncher.java`. Изолированная сборка использовала tracked inputs. Локальные правки `VillageMaster.java` и `ClanMember.java` исключены по blob SHA, как и рабочие конфиги, learned и другие файлы владельца.

## Сборка и проверки

- JDK 25, `ant -q -f <shadow>/build.xml jar compile-tests`, итог exit 0. Проверки owner profile 38/38, XML/XSD PASS. Компиляция тестов содержит пять прежних предупреждений JDK.
- Launcher из shadow `dist/game` проверил `compatible-activity-intents-019` (seed 19001902) 1/1, создание и recruitment 027 CP1 (27002701) 4/4 и 3/3, CP2 (27002702) 11/11, `equipment068-policy` (68006801) 10/10. Scheduler, scheduler-local-priority, Decision Core и Navigation Core (20260725001) дали 38/38, 3/3, 42/42 и 51/51. Все итоговые exit code равны 0.
- `PhantomPickup109Checks clan-visit-move019` прошёл реальный `PlayerAI` без JDBC и World. Контроль, добавленный после ревью, воспроизвёл повтор MOVE после toggle на промежуточной сборке. `PhantomShutdown103Checks --expired-accepted-poll018` подтвердил принятый точный STORE и отказ для чужого или неподтверждённого. Оба exit 0.
- Первые тестовые попытки выявили недостающие файлы компактного shadow и неверный cwd для DB-зависимого `server-shutdown-handoff`. DB-зависимая проверка не запускалась в рабочей конфигурации. Для DB-free проверок добавлены штатные read-only fixtures, применён корректный cwd. Прямое прохождение маршрута к мастеру клиентом, совместный живой PvP/Party handoff и полный DB-зависимый suite остаются непроверенными.
- Mojibake-маркеры в изменённых файлах проверены, совпадений нет.
- Escaped Cyrillic в изменённых файлах проверен отдельно, совпадений нет.

## Остановка и установка

- Первый штатный запрос GS-only на r14 исчерпал `deadline:population_ecology`. Scouts завершили drain, в промежуточном SQL оставались 128 `online=2` и 0 pending STORE. Штатный повтор возобновил фазу, GS 29900 завершился с exit 0 в 10:50:34. SQL33331 подтвердил 0/0, 5000 READY и 24 quarantine. Общие 5 025 строк `phantom_profiles` включают отдельный merchant-профиль 1 без `population.state` и связанного персонажа. Контроль установки привязан к статусам 5000/24.
- Первый JAR `472A9D6A6A5FF29E03A4901F664F8BB9F8A9C148FFA713612A952B53B7AF6D92` с commit `47b051a81bdaf1426abca1ea400742c40d1b76b3` остановился на старте Phantom World до 7777. В composition новый кандидат регистрировался после `seal`. Регистрацию кандидата и обработчика разнесли по их штатным фазам. Неуспешный GS 23132 штатно завершился в 10:56:45, request и process exit 0.
- Исправленный GS JAR `D325195E933469BD83B4CAC37B02776A9AE3885BFE3D61B4C7E17BE34CCBE592`, `BUILD_INFO.json` `7066DC27324F1733582C222127772BB464C27377904D7D055936938ACC8872F7`, источник `de62549a8609badac5a3e24f97486614a4c04cca`. Установка повторно прошла 0/0, 5000/24 и JDBC preflight. Трогались только `dist/libs/GameServer.jar` и `dist/BUILD_INFO.json`. SQL только read-only, MAIN3308 вне операций.

## Bartz после поставки

`START.cmd` exit 0. GS 23232 зарегистрировался на LoginServer как Bartz в 11:00:25, 7777 loopback. LS 20168 держит 2106/9014, MariaDB 28664 держит 33331. Заключительный owner STATUS подтвердил оба процесса и источник `de62549a860`. В Windows подтверждены окна `Mobius - GameServer` и `Mobius - LoginServer`. JDK сообщил max 12 288 MiB и used 7 266 MiB в одном срезе.

| Время | READY | Ordinary / scouts по SQL `online=2` | Saved HP=0 всего / online | Сохранённые EXP / SP |
|---|---:|---:|---:|---:|
| 11:01:23, прогрев | 5000 | 47 / 32 | 703 / 7 | 2 798 693 / 217 070 |
| 11:02:52 | 5000 | 128 / 32 | 684 / 3 | 2 808 565 / 217 840 |
| 11:05:49 | 5000 | 128 / 32 | 686 / 0 | 2 819 120 / 218 905 |

- В 11:02:16 SystemPanel зафиксировал 360 подключённых объектов. Журнал подтвердил загрузку 200 offline traders. В обоих срезах полного cap держались 24 quarantine с digest `deca56000a2f758be0706af4beb6bfedad9dbbdbdd90e074e058a3a4cc692394` и 0 pending STORE. Профиль 7 сохранил исходный EXP, SP и HP. Декодирование его `background.state` после поставки подтвердило `INCONSISTENT`, SHA-256 `00BD631A0A523C9BC4BD835444AC94E4B3ADF826E5D39BB0DC3F9A1C5D6DFBBB`. Хеши LoginServer.jar, java.cfg и двух Phantom INI совпали. На месте 166 learned TSV с тем же списком имён, запись знаний scouts продолжилась.
- За 2 мин 57 с полного cap прирост составил 10 555 EXP и 1 065 SP на 5000 READY. У восьми наблюдавшихся ordinary (117, 147, 274, 278, 279, 282, 286, 291) EXP, SP и сохранённые позиции совпали в обоих срезах. Без квитанций источник общего прироста и фактические доли фарма, пути и ожидания для этой восьмёрки не установлены. У 1525, 1612 и 1712 сохранённый HP вырос с 0 до 84, 90 и 81 при `online=2` в обоих срезах.
- В журнале исправленного запуска к 11:05:49 было 60 `GOAL_ABSENT` прежнего optional-lineage типа, один `NO_USEFUL_NATIVE_FARM_PROGRESS` у 412, три `NO_PATH_WITHIN_SEARCH` и ноль SEVERE/ERROR. У 538 повторился отказ маршрута в новой ревизии, сохранённый EXP остался 609. Проверки геодаты и боевой пригодности сохранены. Короткое окно после запуска и ночной baseline имеют разную экспозицию, снижение частоты предупреждений этим не доказано.
- На обеих полных выборках кандидатов уровня 10+ среди ordinary `online=2` было 0. В БД 0 кланов. Поход, основание, оплата уровня и создание альянса в этом естественном окне не наблюдались.

| Gate | Итог |
|---|---|
| I1 | Существующее native приглашение и неизменность farm root подтверждены fixture 027 и 019 |
| I2 | Приоритет подготовки и снятие пустого `income.farm` реализованы. Equipment068 10/10. Переключение между экипировкой и живым визитом не наблюдалось |
| I3 | Предложение вне NPC range, отказ до arrival, stale root и одноразовая native запись подтверждены через backend fixture. Live визит не наблюдался |
| I4 | DB-free MOVE и STOP подтверждены. Живой конфликт с Party, PvP и clan receipt остаётся непроверенным |
| I5 | PARTIAL. Сохранённый прогресс и восстановление HP видны. Точная цепочка native kill → reward → STORE и причина повторного отказа маршрута 538 остаются открытыми |
| I6 | Два полных среза 5000/128/32, cap 160, 200 лавок, 24 quarantine, heap 12 GiB, прежний профиль 7 и learned подтверждены |

## Откат

Точка восстановления `D:\Backups\PhantomWorld\PHANTOM-COMPATIBLE-ACTIVITY-INTENTS-019\r01`, 12,7 MB. Пара r14 сверена побайтово по SHA-256.

| Файл | SHA-256 | Назначение |
|---|---|---|
| `GameServer.jar` | `88711038992919A2CE34C2D2035C8FFD4AE791D291A1C656023BD23EDE56DBC9` | `dist/libs/GameServer.jar` |
| `BUILD_INFO.json` | `ABC927FF847F44A204A4F7E342B60969DDAFF5752650853A883611888ED11CB2` | `dist/BUILD_INFO.json` |

Порядок восстановления указан в `RESTORE.txt`. Применить штатный GS-only `RequestShutdown` к подтверждённому владельцу Bartz, дождаться выхода GS и SQL33331 0/0. Восстановить два файла в указанные места, проверить хеши, вызвать `dist/START.cmd` с прежними LS и SQL. Игровые данные, конфиги и learned используются с текущего диска.

## Публикация и очистка

- Для проверки baseline использованы `git symbolic-ref --short HEAD`, `git rev-parse HEAD`, `git rev-parse refs/remotes/origin/feature/phantom-world`, `git config --get remote.origin.url`, `git remote get-url origin`, `git status --short --untracked-files=normal`, `git fetch origin`, `git ls-remote --exit-code origin refs/heads/feature/phantom-world`, `git merge-base --is-ancestor 32702a2c7fdd3ae196d0af73d1492702587f9f88 HEAD` и тот же ancestry-check для remote-tracking ref. Первый запрос из sandbox не имел сети. Повтор с разрешённым доступом подтвердил baseline.
- Для изоляции и контроля охвата использованы `git status --short` с точными путями, `git status --short --untracked-files=no -- <пути входов>`, `git ls-tree -r HEAD -- <пути входов>`, `git ls-tree -r --name-only HEAD -- <пути входов>`, `git diff -- <точные пути>`, `git -c core.whitespace=cr-at-eol diff --check -- <точные пути>` и тот же `diff --cached --check` для новых файлов. Scope источника 12 перечисленных файлов. Совпадение SHA исходника shadow с checkout 12/12.
- Публикация выполняется через `git add -- <точные новые файлы>`, `git commit --only -m <subject> -- <точные пути>` и обычный `git push origin HEAD:refs/heads/feature/phantom-world`. Окончательный readback ветки передаётся вместе с результатом. Локальные конфиги, JAR, learned, task package и чужой dirt исключены.
- Удалён принадлежащий 019 scratch `D:\Temp\Codex\PhantomWorld\PHANTOM-COMPATIBLE-ACTIVITY-INTENTS-019\r01`. Проверены маркер, канонический путь, отсутствие reparse points, процессов и открытых файлов. Удалены 10 595 файлов, 146 156 815 байт. Scratch-остатков и cleanup-блокеров нет. Сохранены четыре файла rollback объёмом 12 697 953 байта. Тестовые процессы завершились. GS/LS и рабочая MariaDB оставлены включёнными. 020 не начат.
