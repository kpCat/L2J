# Передача контекста для завершения PHANTOM-LEARNING-001

LEARNING-001 имеет статус **PARTIAL**. Базовый SHA `c5b81f89ce02030eff8441395b0411137a110b2f`, реализация `518318c89c6937c80818b4035df6b0fc9523f9ce`. Ветка [wip/phantom-learning](https://github.com/kpCat/L2J/tree/wip/phantom-learning). Локальный clone `D:/Codex/worktrees/phantom-learning-001-source`. Путь пользователя `D:/Codex/repos/L2J-phantom-world` оставался read-only.

## Состояние native-вертикали

1. Открыть [отчёт](../../reports/PHANTOM-LEARNING-001.md), [архитектуру](ARCHITECTURE.md) и [ограничения](SAFETY.md). Сверить HEAD learning-ветки и обе защищённые ветки.
2. Приватный корень `D:/Codex/worktrees/phantom-learning-001-source/.phantom-local/learning001` содержит отдельные datadir, Geo203, манифесты, SQL readback и исходные журналы попыток. Контуры по `cycle11` включительно доступны для probe и readback. Итоги по ним и аттестованный JAR указаны в отчёте.
3. Bind fix `9fe1c1b1ac70fef8c7e8f2f1e9eef888a2d33a05` подтверждён focused-тестом и системным listener `127.0.0.1:17961`. Launcher принимает точный SHA JAR и разрешает бой после аттестации PID и сокета. Повторный запуск получает новый каталог `attempts/<uuid>` с отдельными CUT и STOP. Владелец снял прежние количественные лимиты, каждый запуск проверяет конкретный этап.
4. В `cycle2` Solo записал оба навыка и потребил `LOW_HP_STOPPED`. NPC добил актёра после завершения Combat claim. Runner использует штатный retreat owner и отдых, отбирает Solo-цели с учётом соседних NPC и начинает с автономной Party. В `cycle3` состав блокировали необязательные Heal. В `cycle4` штатный состав сформировался, а `WAIT_TANK_AGGRO` не дал начать бой. В `cycle5` Antelope не прошёл штатный прогноз даже с Phoenix Knight. `cycle7` использует канонический источник Hames Orc Shaman. GameServer загружается с глобальным PhantomSystem OFF, затем runner поднимает штатные Materialization, Combat, Navigation, Progression и Party owners для своей группы.
5. RED и GREEN в `cycle7-observation-red.log` и `cycle7-observation-green.log` подтвердили исправление. Чтение Progression работает внутри наблюдения Party и сохраняет запрет вложенного ACTION. В `cycle7` танк погиб при ожидании TUNING. В `cycle8` состав сохранился, а точный gate выдал `WAIT_GROUP_DAMAGE`, 11,708 DPS против 4158 HP. `cycle9` добавил штатного DPS-помощника с одним моделируемым Prominence. Все три headless-прогноза прошли. В GameServer TRAIN принял три приглашения, но бой остановился на `WAIT_GROUP_SOURCE`. Отдельный SQL readback подтвердил 0 примеров и 0 моделей. Оба изученных атакующих заклинания сохранены у шести учебных актёров.
6. `cycle10` отклонил исходный Hames Orc Shaman по spawn-зоне Overlord и квестового NPC 27214 по штатной награде. `cycle11-survey.log` фиксирует 464 обычные цели и отказ до GameServer. Проверены точный полигон spawn, Geo и радиус до 2000. Оба контура остановили SQL. Боевые pulses берутся из `PhantomSchedulerPulseMillis`, SQL опрашивается раз в секунду. Этот cadence ещё не проверен в GameServer.
7. В `cycle12-approach-survey.log` проверены 97 точных источников с точками внутри FARM-полигонов и в пределах 300 от якоря. После снятия ограничения fixture в 6000 HP найден канонический якорь Hot Springs Antelope на 8317 HP. План и ёмкость прошли. Штатный прогноз отказал по `WAIT_TANK_AGGRO`, 5480 входящего HP против 4556 HP танка за 32,3 секунды. `cycle13-roster-check-party.log` подтвердил, что тот же риск проходит со штатным Major Heal.
   В `cycle13` штатная Party сформировалась и провела бой. Попытка `112610e686e9479ab6a2b48f2f6eec8f` содержит `PARTY_FARM_LOOT_END`, восемь замеров Party и `unknown=6`. В `cycle15` журнал `LEARNING_CAST_REJECTED` подтвердил `effects/execution-lease`, `publish/no-exact-hp` и отдельный `publish/receipt-owner` уже после доказанного прямого HP. RED и GREEN подтвердили исправление квитанции при неизменном точном составе. SQL readback `cycle13`–`cycle15` вернул по 0 примеров и моделей.
8. В `cycle16-read-native.log` сохранено первое точное **PARTY / 1230 / 1553 HP**. В `cycle16-coverage.log` один TRAIN, TUNING и AUDIT пусты. Сохранить схему и попытку `8a3e4df271cb4332a52ebec0a78c5397`. После первого боя `WAIT_GROUP_SOURCE` совпал с угрозой 21320 вне источника 21318. В `cycle17` обследование нашло 20 Changed Creation с Geo203, для которых штатный planner не подтвердил точного источника. Штатный Ghost of a Doorman 21798 в Imperial Tomb прошёл выбор источника и ёмкость. Базовый Prominence помощника на magic level 74 не прошёл `WAIT_GROUP_DAMAGE` для цели уровня 76. В `cycle18` fixture подготовил помощника со штатным Prominence +1 до CUT. Все три headless-состава получили точную цель. Свежий SHADOW использует JAR с тем же SHA и изолированную схему `cycle18`.
9. Рабочая схема `cycle18` содержит 35 уникальных NATIVE-примеров до ON. `cycle18-post-resume-coverage.log` хранит 16 TRAIN, 9 TUNING и 10 независимых AUDIT. `cycle18-train.log` и `cycle18-promote.log` фиксируют DRAFT и ACCEPTED v1, независимый MSE `1,590303635782161 → 0,05059066793980769`. SHA весов `0675bacb74aac678c10038c37fdc5db6fde5a56961f36c72810d78b54a6bc1ec`. Новый JVM подтвердил холодное чтение этой версии. В попытке `ebaa10a445b24ab6b2f75021d68957c4` Party выполнила десять обычных действий, `preferredDifferent=0` и `applied=0`. SQL-диагностика `cycle18-score-diagnostic.log` зафиксировала отсечение оценки у 20 из 35 учебных событий и диапазон прогнозов 0,88143–0,89009 на сохранённых ON-векторах Party.
10. В отдельной попытке `829e589cf0dd48c3a7f70cadce525aa3` Solo проверен первым. Три победы, семь решений, `preferredDifferent=0` и `applied=0`. Итоговые `cycle18-final-read-native.log` и `cycle18-final-read.log` подтвердили 51 уникальный пример, единственную ACCEPTED v1 и чистый STOP. Перед следующим GameServer доказать полезное различие оценок в независимых контекстах для обоих навыков и scope. Нужны реальные касты с отличающимися наблюдаемыми результатами, включая примеры ниже верхней границы reward. Сохранить v1, исходные 35 строк по training hash и последующие ON-строки. Диагностика оценки второй законной альтернативы не создаёт training evidence без каста. Сравнивать с простым базовым прогнозом и проверять преимущество выбора в независимых контекстах до принятия новой версии. Штатные пороги, права, leases, HP-атрибуция и награды остаются в силе. Нативный gate требует совпадения `event_key` журнала и SQL для изменившегося каста в каждом scope. Полные native F7/F8, обычный Heal и PvP/PK остаются отдельными проверками. PHANTOM-LEARNING-002 ждёт закрытия 001.

## Команды

Из корня модуля выполнить дешёвый gate перед JAR.

```powershell
ant -f build.xml phantom-learning001-test
ant -f build.xml jar
```

Для штатных suite с игровыми XML рабочий каталог `dist/game`. Имена и seeds указаны в отчёте. Runner использует тестовые классы из `compile-tests`, production-классы из аттестованного JAR и собственную loopback MariaDB. После успешного SHADOW отдельные вызовы запускают и останавливают только SQL. В `$version` и `$sha` указываются значения из отчёта DRAFT.

```powershell
$fixture = 'tools/phantom-runtime/Invoke-Learning001Fixture.ps1'
$jar = 'cdbe0615240aca65de85d94dfbcf3c7666ce88240fc86710c43fd3c4c0bc69b6'
$lane = 'cycle19' # Для новой схемы. Действующий cycle18 не переинициализировать.
$previous = Join-Path (Split-Path $PWD -Parent) '.phantom-local/learning001/cycle18'
powershell -NoProfile -File $fixture -Action stage -Lane $lane -GeoSource (Join-Path $previous 'game/geodata') -GeoManifest (Join-Path $previous 'geo203.json') -ProtectedManifest (Join-Path $previous 'protected207.json')
powershell -NoProfile -File $fixture -Action provision-sql -Lane $lane
powershell -NoProfile -File $fixture -Action survey -Lane $lane
powershell -NoProfile -File $fixture -Action prepare-actors -Lane $lane
powershell -NoProfile -File $fixture -Action plan-scenes -Lane $lane
powershell -NoProfile -File $fixture -Action check-capacity -Lane $lane
powershell -NoProfile -File $fixture -Action check-party -Lane $lane -JarSha $jar
powershell -NoProfile -File $fixture -Action shadow -Lane $lane -JarSha $jar
powershell -NoProfile -File $fixture -Action coverage -Lane $lane -JarSha $jar
powershell -NoProfile -File $fixture -Action train -Lane $lane -JarSha $jar
powershell -NoProfile -File $fixture -Action promote -Lane $lane -JarSha $jar -Version $version -ModelSha $sha
powershell -NoProfile -File $fixture -Action on -Lane $lane -JarSha $jar -Version $version -ModelSha $sha
```

Для диагностики принятой v1 используется `powershell -NoProfile -File $fixture -Action read -Lane cycle18 -JarSha $jar -Version 1 -ModelSha 0675bacb74aac678c10038c37fdc5db6fde5a56961f36c72810d78b54a6bc1ec`. Она читает сохранённые reward и прогнозы по навыку и версии политики. Model v1 проверяется по сохранённому `cycle18-post-resume-coverage.log`, поскольку последующий `coverage` включает новые NATIVE-строки, собранные после обучения.

Для проверки сохранённых строк использовать `powershell -NoProfile -File $fixture -Action read-native -Lane native`. Параметр `-Lane` выбирает конкретный сохранённый datadir. Синтетический checkpoint 2 из прежней SQL-only схемы отклоняется при promote.

## Git и границы

Проверка исходника и публикации использует `git ls-remote https://github.com/kpCat/L2J.git refs/heads/wip/coordination-130-preserved refs/heads/feature/phantom-world refs/heads/wip/phantom-learning`. Для локального исходника выполнены `git rev-parse --show-toplevel`, `git rev-parse --verify` для исходного ref и commit, `git worktree list`, `git remote -v` и `git for-each-ref`. В независимом clone использованы `git clone --no-checkout --single-branch --depth 1 --branch wip/coordination-130-preserved -c core.longpaths=true`, `git switch --no-track --create wip/phantom-learning c5b81f89ce02030eff8441395b0411137a110b2f`, `git symbolic-ref --short HEAD`, `git rev-parse --verify HEAD`, `git status --short --branch`, `git for-each-ref`, `git ls-tree`, `git ls-files --eol` и `git diff` для проверки scope и CRLF.

Staging сделан через `git add -- <точный allowlist>`, проверен `git diff --cached --name-only`, `git diff --cached --stat` и `git -c core.whitespace=cr-at-eol diff --cached --check`. `git commit` и `git push origin HEAD:refs/heads/wip/phantom-learning` разрешены владельцем для этой ветки. Артефакты сборки, приватная SQL-папка, модель и логи остались локальными.

В продолжении сверены `git symbolic-ref --short HEAD`, `git rev-parse --verify HEAD`, `git status --short --branch`, `git log -4 --oneline`, `git ls-files --eol -- <точные пути>`, `git diff --numstat`, `git diff --stat`, `git diff --name-only <база> HEAD -- <защищённые пути>`, `git -c core.whitespace=cr-at-eol diff --check` и `git merge-base --is-ancestor <предыдущий GitHub HEAD> HEAD`. Scope staging сверялся по `git diff --cached --name-only --relative`. GitHub SHA повторно сверены через `git ls-remote origin refs/heads/wip/coordination-130-preserved refs/heads/feature/phantom-world refs/heads/wip/phantom-learning`. Команды использовались для сверки базы, ограниченного staging, публикации и контроля защищённых refs.
