# PHANTOM-PVE-FARM-UNBLOCK-001

21 ноября 2026 г. **PATCH_READY_OWNER_RESTART_REQUIRED**.

Parent — `7e6319457f14e55906c14e9a599307da730b13ee`. Ветка — `feature/phantom-world`. SHA опубликованного коммита и подтверждение GitHub приведены в итоговом ответе. [Сравнение ветки с исходным HEAD](https://github.com/kpCat/L2J/compare/7e6319457f14e55906c14e9a599307da730b13ee...feature/phantom-world).

## Свидетельства Bartz

Рабочий GameServer зарегистрировался как Bartz в 22:39:03 EET. PID, время старта и SHA-256 рабочего GameServer.jar совпали с `maintenance-evidence.json` от исходного HEAD. `post-start.tsv` и `final-observation.tsv` из сохранённого набора наблюдения совпадают побайтово. У 32 профилей за 19 минут сохранённый прирост EXP, SP и числа предметов равен нулю. Это проверка сохранённого состояния. Один только этот результат не определяет, выполнялись ли атаки между записями.

Из архивного снимка `after.sql` от 22:36:52, перед запуском, штатными кодеками извлечены три контрольных профиля. Компонент execution у всех зафиксирован в фазе `ACTIVE` с владельцем `execution.solo`. Архивная фаза владельца и сохранённая позиция не устанавливают текущий native epoch.

| profileId / objectId | Goal, revision, статус | Сохранённая позиция, состояние, цель | Сохранённые EXP/SP до и после |
| --- | --- | --- | --- |
| 2 / 268484425 | 7389718760877495460, r3, ACTIVE | READY, `(-56161,-113607,-672)`, checkpoint совпадает с `generated.farm.eda8543a4ab4d30e028f9b97.anchor`, NPC 18342 | 348/40 → 348/40 |
| 3 / 268484476 | 8865119378336487911, r2, ACTIVE | READY, `(-90610,248293,-3568)`, `idle.background.history`, `generated.farm.432802d3c2901d9686cb1651.anchor` | 609/70 → 609/70 |
| 4 / 268484625 | 874512183384095612, r3, ACTIVE | DEAD, `(24496,9206,-3584)`, `idle.background.history`, `generated.farm.f66a8a0231b2d83dac29cc4d.anchor` | 342/30 → 342/30 |

Граница с подтверждённым дефектом допуска — **Arrival → AutoPlay**. В `bartz-pve-samples.json` в **22:42:41.037584452 EET** стек показывает `PhantomBackgroundDecision.lambda$bindVisibleLife$2:124` (ветка `ARRIVED`) → `PhantomVisibleAutoPlay.start:190` → `PhantomSystem.lambda$start$17:594` → `PhantomMaterializationRetentionPolicy.permitsNewActions:123` → `retentionFacts:4214`. Номера строк относятся к parent. Вход в этот предикат следует после проверки актуальной цели, ActionLease и отсутствия PvP hold. Результат `ARRIVED` мог быть получен по уже доступной локальной позиции. Стек не доказывает прохождение полного маршрута.

Прежний допуск требовал текущую или недавнюю близость человека, видимость для человека либо реальную группу. В `accounting0.log` последняя запись предшествует старту этой JVM (22:28:56). `EnableLocalPlayPilot`, auto-attach и synthetic human выключены. Путь Solo travel отсекает состоящего в группе Player. В таком контексте удержание актёра по `ACTIVE_ACTION` и действующий `AMBIENT_TARGET` могли законно довести его до места, но давали отказ на старте фарма. `PhantomPopulationActiveTarget=8` и штатный `publishAmbientTargets` разрешают обычную материализацию с этой квотой без человека. `EnableAutoPlay=False` относится к обычному клиентскому режиму. В `AutoPlayTaskManager` этот флаг не блокирует уже зарегистрированную PhantomPolicy.

JFR содержит выборки вызовов, без identity или возвращённого результата. Три строки таблицы показывают сохранённые состояния и различия между профилями. Связать с ними точный JFR-вызов, native epoch и фактический route result нельзя. Отдельные `NO_PATH`, `STUCK` или отказы выбора цели для других профилей остаются открытыми. В 1 413 execution samples и 4 314 native samples за 90 секунд не найдено стека начисления за смерть NPC. Это отсутствие положительного свидетельства. Перезапуск и повторное длительное наблюдение не проводились.

## Изменение

Точный scope относительно High Five:

- `java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java` — production AutoPlay принимает действующий `AMBIENT_TARGET` того же профиля при открытом календаре и актуальном `admitted`. Допуск популяции читается после получения demand. Повторные проверки при регистрации и новых действиях используют тот же предикат.
- `java/org/l2jmobius/gameserver/phantoms/background/PhantomVisibleAutoPlay.java` — для `HISTORICAL_IDLE` сохранён прежний предикат до и после получения lease. Существующие конструкторы сохранены.
- `test/java/org/l2jmobius/gameserver/phantoms/PhantomOrdinaryFarmAdmissionChecks.java` — адресный DB-free тест.
- `docs/phantoms/reports/PHANTOM-PVE-FARM-UNBLOCK-001.md` — этот отчёт.

Переиспользованы `currentPhysicalDemand`, `PhantomPhysicalDemand.ordinaryAdmission` и тот же допуск популяции, что в `PhantomReconcileFirstActivityPort`. Сохранены проверки PvP, party, store, владельца, epoch, актуальной цели и command gate. Используются прежние arrival proof, AutoPlay/AutoUse, native combat и STORE. Новых настроек, фоновых задач и начислений нет. Независимое ревью проверило привязку и отозванный допуск. Два замечания устранены и перепроверены.

Перед правкой прочитаны восемь документов задачи, корневые инструкции и README, модульный `Agents.md`, `PHANTOM_CURRENT_STATUS`, `CODEX_WORKFLOW_CONTRACT`, `TASK_PACKAGE_STANDARD`, релевантные разделы master plan и отчёты R2/R3, 092C и 115. Проверены ближайшие реализации travel, retention, штатных AutoPlay/AutoUse и `build.xml`. README.md модуля, отдельные AGENTS по дереву задачи и локальный code map для этого участка отсутствуют. Код-карта задачи партий не относится к Solo scope.

## Проверки и сохранность

Команды ниже используют `S` как временную копию модуля, `B` как отдельный выход сборки и `CP` как classpath с `B/bin`, классами и ресурсами существующих тестов и библиотеками `S/dist/libs`. Ни один Ant target не использовал живой `dist` как назначение.

| Команда или тест | Результат |
| --- | --- |
| `ant -f S/build.xml -Dbuild=B -Dlibs=S/dist/libs jar` | JDK 25.0.4.1 / Ant 1.10.17, exit 0, 2 435 исходников, 38/38 owner rules. SHA-256 кандидата GameServer.jar `B58E1CE02009950C1B03BAD389B459978D7B6ECDFB2B53CDBEF21778E1BCB84C`. |
| `javac --release 25 -encoding UTF-8 -cp CP -d <focused-output> <PhantomOrdinaryFarmAdmissionChecks.java>` | exit 0. |
| `java -cp <исходный GameServer.jar и focused-test CP> org.l2jmobius.gameserver.phantoms.PhantomOrdinaryFarmAdmissionChecks baseline` | RED, exit 1, assertion об отказе ambient Solo без наблюдателя. В режиме baseline вызывается реальный retention guard из JAR указанного HEAD. |
| `java -cp <focused-output и CP кандидата> org.l2jmobius.gameserver.phantoms.PhantomOrdinaryFarmAdmissionChecks` | GREEN, exit 0. Положительный допуск, прежний human-local путь и отказы при PvP, потере допуска, закрытом календаре, отсутствующем, чужом, истёкшем и групповом demand. Отдельная проверка отзыва допуска во время получения demand. Тест сценария не регистрирует реального Player в AutoPlay. |
| `java -Xmx3g -cp CP org.l2jmobius.tests.phantoms.PhantomOwnerPlay002R3Checks <путь к исходному модулю>` | Финальный exit 0, `OWNER_PLAY_002R3_NORMAL_SCOPE_PASS`, GeoData 203 региона только на чтение, SQL-пул выключен. |
| `java -Dphantom.module.root=S -Dphantom.test.reports=<изолированный каталог> -cp CP org.l2jmobius.tests.phantoms.PhantomTestLauncher <mode> 20260725001` | На кандидате до уточнения границы historical idle: `activity-scheduler` 37/37, `navigation-core` 51/51, `visible-itinerary068` 23/23, все exit 0. |

Первичная сборка в sandbox получила `AccessDeniedException` на чтении временных входов Java. Повтор прошёл с разрешённым доступом к изолированному каталогу. Первый прогон scheduler и первый R3 завершились ошибкой при отсутствии fixture-файлов. Адресные повторы с этими файлами прошли. Существующий `visible-itinerary068` содержит общий DB-free assert receipt hash с меткой TASK130. Явные TASK130, N01 4/4, V4 TRAIN и полный `ant verify` не запускались.

GameServer, LoginServer и SQL33331 сохранили PID 4020, 29876 и 32328, те же времена старта и порты. SHA-256 обоих рабочих JAR и Studio WIP совпали до и после. Побайтово сверены 92 рабочих конфиг-файла с исходной копией, отличий ноль. Нет подключения к SQL33331, замены JAR, STOP/START или изменений в Studio. Локальный пакет задачи и пользовательские WIP исключены из публикации.

- Mojibake-маркеры во всех изменённых файлах проверены.
- Escaped Cyrillic и XML-ссылки на кириллицу во всех изменённых файлах проверены отдельно.

## Git и следующий шаг

Git использован по задаче для сверки baseline, точного scope и публикации. Использованы `git rev-parse --show-toplevel`, `git symbolic-ref --short HEAD`, `git rev-parse HEAD refs/remotes/origin/feature/phantom-world`, `git rev-parse HEAD`, `git remote get-url origin`, `git ls-remote origin refs/heads/feature/phantom-world`, `git status --short -- <scoped paths>`, `git diff -- <exact production paths>`, `git diff --stat -- <exact PhantomSystem.java>`, `git diff --cached --name-only`, `git -c core.whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol diff --check -- <exact paths>` и соответствующая проверка `--cached`. Для публикации предусмотрены `git add -- <4 файла scope>`, `git diff --cached -- <4 файла scope>`, `git commit -m "fix(phantom): admit ambient solo farm after arrival" -- <4 файла scope>`, `git push origin HEAD:refs/heads/feature/phantom-world`. GitHub сравнивается по parent, текущей ветке и точному набору файлов. Запуск `ls-remote` в sandbox получил сетевой отказ, чтение GitHub подтверждено через коннектор.

**Native proof — NOT_RUN.** После независимой сверки опубликованного GitHub diff владельцу требуется отдельно разрешить развёртывание. Приёмка на одном бывшем проблемном профиле должна связать `profileId/objectId/epoch/goal` с `ARRIVED`, регистрацией AutoPlay, нативными DAMAGE/KILL и последующим STORE, подтверждающим EXP/SP и добычу. Super Geo Trainer остаётся следующим отдельным заданием.
