# Передача контекста для завершения PHANTOM-LEARNING-001

## Дополнение от 11 ноября 2026 года

**SUCCESS в scope Solo и автономного Party PvE.** Реальный цикл обучения и применения завершён. Подготовка контекста для PHANTOM-LEARNING-002 выполнена, следующий goal не начат.

## Идентичность и сохранность

- GitHub [wip/phantom-learning](https://github.com/kpCat/L2J/tree/wip/phantom-learning).
- База `c5b81f89ce02030eff8441395b0411137a110b2f`, предыдущий опубликованный HEAD `23c7b3377a771acb75e5c65fa8ef30a993d8f25a`. Итоговый SHA сверяется через `git rev-parse --verify HEAD` и GitHub readback.
- Независимый clone `D:/Codex/worktrees/phantom-learning-001-source`. Пользовательский `D:/Codex/repos/L2J-phantom-world` использовался read-only.
- Защищённый feature HEAD `faec72161f9e4f6231764e17d6f2637a4db080af`, TASK130 HEAD `c5b81f89ce02030eff8441395b0411137a110b2f`.
- Приватный корень `D:/Codex/worktrees/phantom-learning-001-source/.phantom-local/learning001` исключён из staging. Он содержит datadir, Geo203, protected207, исходные журналы и веса. История ранних попыток сохранена в отчёте и отдельных контурах.
- Собственные endpoints MariaDB `127.0.0.1:33317`, GameServer `127.0.0.1:17961`, исходящий Login `127.0.0.1:19261`. После финального readback все свободны, owned JVM и SQL остановлены.

## Действующая модель cycle19

| Поле | Значение |
|---|---|
| Model / algorithm / status | 2 / 2 / ACCEPTED, NATIVE |
| SHA-256 весов | `a628f4811d9dfb257c117bfc8e0b14ac122ac8fc6b7233cd1d31121c88102777` |
| JAR SHA-256 | `e4de040cfa6e33fa478592138d9aed95230f231df88fe40f73cb3f48a3b18a61` |
| Feature schema | `53a0b97cd4c771a2d2a93b46829273f312e0dfd77626989e92b9b52ae2dc1d34` |
| Skill catalog | `e46709db95ce8e92f4b4f392b5c468d147c6ddb11ab6c773cc48f3702bc6474a` |
| Training hash | `9d6267cb3f0b9e081e68a66464edf65a82938adc7cc304240c908e35d4709393` |
| Размер / выбранная эпоха | 12 500 Б / 3 |
| TRAIN / TUNING / independent AUDIT | 18 / 8 / 10 |
| AUDIT MSE / TRAIN-mean MSE | 0,05747961 / 0,07203633 |

V2 обучает ту же MLP на остатке относительно среднего TRAIN, сохраняет центр и масштаб вместе с весами и возвращает исходную шкалу reward. V1 читается по старому формату 12 484 Б. Прежняя ACCEPTED v1, исходные 36 SHADOW-строк и последующие ON-события сохранены. Финально **47 уникальных EXACT_CAST_HP**, 24 Solo и 23 Party, две ACCEPTED модели.

## Причинные evidence

| Scope | Успешная холодная попытка | Event key | Штатный результат |
|---|---|---|---|
| PARTY | `4531202ea167464eb60b44832a97f3ac` | `7b814364b7d6fdcda727ebc21ab673a66433b9f6144c86fd9781811ea7396acf` | 1230:28 → 1339:1, 1203 HP |
| PARTY | та же | `e9ba0cc32b444a30f2fb1c14a1b770be14846038e4c405f95d5ac3f2981d5fc4` | 1230:28 → 1339:1, 1408 HP |
| SOLO | `cc532865f5aa472dbbc1d1a468abec15` | `2e075e88e836d58a07b8c9243e22e5fd36416ca9be107327f7db66aca4ae2d46` | 1230:28 → 1339:1, 1563 HP |

Приватные launch logs `cycle19-algo2-party-release-on.log`, `cycle19-algo2-solo-drain-on.log` включают OS socket/PID/FILETIME, точный cold SQL SHA, GAME_STOP, независимый `ON_EVENT_SQL_PASS` и SQL_STOP. Каталоги `cycle19/attempts/<id>` содержат native stdout/stderr, CUT и `on.complete`. В Party evaluated=6, preferredDifferent=2, applied=2. В Solo 1/1/1, overBudget=0 и unknown=0 в обоих.

Первый v2 Party и первый v2 Solo также дали точные SQL-подтверждённые HP, но wrapper отказал при завершении. В Party ожиданию drain требовался обычный coordinator pulse. Для конечного Solo после quota используется canonical drain/store после native terminal. Бой ограничен 12 секундами, начинается от 90% HP и в диапазоне обоих навыков. Первый Solo v1 ранее привёл к смерти. Восстановление проведено штатным Resurrection 1016 и WALK с leases, потеря EXP оставила уровень 77. Все эти исходы сохранены.

## Проверки

`cycle19-algo2-verified-compile.log` подтверждает compile-tests, exit 0. Targeted `cycle19-algo2-verified-*.log` подтверждают Learning 4, Solo Combat 72, ownership 37, Party tactics 13, coordination 77, PvP 14, Support 9, Supply071 11, commerce receipt 15 и F8 14, всего **266/266**. F7 strict receipt и itinerary прошли отдельным entrypoint без SQL replay.

`cycle19-algo2-stock-heal.log` подтверждает ordinary unmanaged Player Heal 1401:11, HP 866,40 → 1886,73, MP 2756,00 → 2689,48, EXP/SP без изменений. Новый получатель 268435465 подготовлен до отдельного `stock-heal-cut.tsv`. Исходные learning-актёры не получали искусственного HP. Native Heal контроль выполнен без GameServer.

`cycle19-algo2-final-read-native.log` и `cycle19-algo2-final-read.log` подтверждают финальные данные, SQL digest и STOP после Heal. Второй лог измеряет реальные ACCEPTED веса на 128×4 подготовленных векторах, p50/p95/p99 6,6/7,3/8,0 мкс, 24,1 Б/op, CPU 203,125 мс, payload 262 144 Б, heap 51 534 048 Б до и после. Полный native feature/selection путь измерен отдельно, Solo 2,3223 мс, Party среднее 0,5194 мс и максимум 1,9177 мс.

Не оценены причинный прирост native DPS/возврата политики, полный native SQL replay F7, отдельный PvP/PK эпизод и длительное OFF/ON CPU сравнение. Наблюдённые предпочтения v2 направлены к Fire Vortex. Выборка мала, классы и NPC ограничены, парных одинаковых полных состояний с двумя исполненными действиями нет.

Независимый read-only review текущего production/test diff и первичных evidence не выявил P1/P2. Generic promotion сравнивает loss со случайной v1 initialization, превосходство над constant baseline автоматически не требует. Для текущего checkpoint v2 превосходство отдельно проверено в обоих scope до promotion.

## Повторное чтение

Из корня High Five можно выполнить read-only по игровым данным, с временным запуском только собственной SQL. Параметры pin обязательны.

```powershell
$fixture = 'tools/phantom-runtime/Invoke-Learning001Fixture.ps1'
$jar = 'e4de040cfa6e33fa478592138d9aed95230f231df88fe40f73cb3f48a3b18a61'
$model = 'a628f4811d9dfb257c117bfc8e0b14ac122ac8fc6b7233cd1d31121c88102777'
powershell -NoProfile -File $fixture -Action read-native -Lane cycle19
powershell -NoProfile -File $fixture -Action read -Lane cycle19 -JarSha $jar -Version 2 -ModelSha $model
powershell -NoProfile -File $fixture -Action audit-shadow -Lane cycle19 -JarSha $jar -Version 2 -ModelSha $model
```

При необходимости повторного ON сначала выполнить targeted preflight по фактическому сохранённому состоянию. `recover-audit -ProofScope SOLO|PARTY` использует штатные leases, WALK, rest и при необходимости Resurrection. `on -ProofScope SOLO|PARTY` создаёт новую идентичность попытки, проверяет pin и точные SQL события своего scope. Полная приёмка требует обоих scope с тем же checkpoint. `stage` и `provision-sql` относятся к новой схеме. Исторические datadir и одноразовый `stock-heal` сохраняются.

## Контекст PHANTOM-LEARNING-002

Общая policy уже находится в существующем CombatService. Следующий task может использовать versioned encoder, bounded exact evidence, offline trainer и MariaDB checkpoint. Нужны отдельно утверждённые цели и критерии для более разнообразных классов/целей, контекстного изменения предпочтений и оценки возврата. Training Director, PvP и осады в 001 не реализованы. Исторический TASK130 сохраняет свой отдельный статус.

## Git

Git-команды разрешены владельцем для независимого clone, scoped commits и обычного push только learning-ветки. В продолжении использованы `git status --short --branch`, `git rev-parse --verify HEAD`, `git symbolic-ref --short HEAD`, `git diff --stat`, `git diff --numstat -- <точные пути>`, `git diff -- <точные пути>`, `git -c core.whitespace=cr-at-eol diff --check`. Для stage используются `git add -- @paths`, `git diff --cached --name-only --relative`, `git -c core.whitespace=cr-at-eol diff --cached --check`. Массив paths содержит ровно 11 изменённых файлов текущего diff в High Five, два production learning-класса, пять learning test-классов, fixture launcher и три отчётных документа.

Команда коммита `git commit -m 'Complete native neural learning cycle in Solo and Party'`. Проверка предка `git merge-base --is-ancestor 23c7b3377a771acb75e5c65fa8ef30a993d8f25a HEAD`. Публикация `git push origin HEAD:refs/heads/wip/phantom-learning`. До и после неё `git ls-remote origin refs/heads/wip/coordination-130-preserved refs/heads/feature/phantom-world refs/heads/wip/phantom-learning` сверяет обе защищённые ветки и exact learning HEAD.

Начальная изоляция ранее выполнена через `git clone --no-checkout --single-branch --depth 1 --branch wip/coordination-130-preserved -c core.longpaths=true` и `git switch --no-track --create wip/phantom-learning c5b81f89ce02030eff8441395b0411137a110b2f`. Предыдущие bounded Git-команды описаны в истории отчёта и коммитов этой ветки.

- Mojibake-маркеры в изменённых файлах проверены полным списком.
- Escaped Cyrillic в изменённых файлах проверены Unicode и XML шаблонами.

## Сохранённые результаты предыдущих этапов


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

11. В `cycle19` сохранены полный SHADOW `740845916a6d4ffcb491519f0dec3d46`, **36 уникальных событий до ON**, 18 TRAIN, 8 TUNING, 10 независимых AUDIT, по два парных контекста в Solo и Party. `cycle19-train.log` и `cycle19-promote.log` подтверждают MSE относительно среднего TRAIN в обоих scope и ACCEPTED v1 с SHA `e206d84bed2ccccee031a64809ded06dfcef622293f2102936fde13d6eb78c86`. Холодная попытка `e86dc693617f40eab22df96bfa46f68b` подтвердила SHA, четыре штатных Solo-решения без смены навыка и один `VICTORY`. Второй бой закончился `TIMEOUT`, затем повторными `LOW_HP_STOPPED` и гибелью профиля 8. Итоговые `cycle19-final-read-native.log` и `cycle19-final-read.log` подтверждают 39 уникальных событий, одну модель и чистый STOP. Party ON ещё не проверена в этой схеме.
12. Перед повтором проверить сохранённое состояние профиля 8 и возможность обычного восстановления через существующие native owners. Для Solo перепроверить допуск из восстановленной позиции и возможность безопасного отхода через штатную геометрию до холодного старта. `safeSoloOpening` допускает fallback по входящему урону за 12 секунд, тогда как реальная сессия может длиться 45 секунд. Сверить этот предел с исходом `TIMEOUT` и журналом отходов. Сохранить модель, SHADOW, обе предыдущие схемы и все журналы. Выбор модели и атрибуцию продолжать на штатном CombatService, критерий конечного успеха тот же.

## Команды

`audit-shadow` сверяет исходную SHADOW-выборку по training hash принятой модели и печатает MSE постоянного среднего TRAIN, MSE модели, число актёров и контекстов с двумя навыками. Для `cycle18` в Party оба актёра применяли оба навыка, но ни один не дал оба навыка в одном аудиторском контексте. В `cycle19` runner требует пары по каждому проверяемому актёру AUDIT. Новый DRAFT печатает те же показатели до принятия.

Из корня модуля выполнить дешёвый gate перед JAR.

```powershell
ant -f build.xml phantom-learning001-test
ant -f build.xml jar
```

Для штатных suite с игровыми XML рабочий каталог `dist/game`. Имена и seeds указаны в отчёте. Runner использует тестовые классы из `compile-tests`, production-классы из аттестованного JAR и собственную loopback MariaDB. После успешного SHADOW отдельные вызовы запускают и останавливают только SQL. В `$version` и `$sha` указываются значения из отчёта DRAFT.

```powershell
$fixture = 'tools/phantom-runtime/Invoke-Learning001Fixture.ps1'
$jar = 'cdbe0615240aca65de85d94dfbcf3c7666ce88240fc86710c43fd3c4c0bc69b6'
$lane = 'cycle20' # Пример для новой схемы. cycle18 и cycle19 сохраняются.
$previous = Join-Path (Split-Path $PWD -Parent) '.phantom-local/learning001/cycle19'
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

Для `cycle19` подготовка уже выполнена. Её отдельные логи находятся в приватном корне, `cycle19-prepare-actors.log`, `cycle19-check-capacity.log` и `cycle19-check-party.log`. Текущие попытки сохраняются отдельно в `cycle19/attempts`. При возобновлении использовать тот же datadir и новый идентификатор попытки. Шаги `stage` и `provision-sql` относятся только к ещё не созданной схеме.

Для диагностики принятой v1 используется `powershell -NoProfile -File $fixture -Action read -Lane cycle18 -JarSha $jar -Version 1 -ModelSha 0675bacb74aac678c10038c37fdc5db6fde5a56961f36c72810d78b54a6bc1ec`. Она читает сохранённые reward и прогнозы по навыку и версии политики. Model v1 проверяется по сохранённому `cycle18-post-resume-coverage.log`, поскольку последующий `coverage` включает новые NATIVE-строки, собранные после обучения.

Для проверки сохранённых строк использовать `powershell -NoProfile -File $fixture -Action read-native -Lane native`. Параметр `-Lane` выбирает конкретный сохранённый datadir. Синтетический checkpoint 2 из прежней SQL-only схемы отклоняется при promote.

## Git и границы

Проверка исходника и публикации использует `git ls-remote https://github.com/kpCat/L2J.git refs/heads/wip/coordination-130-preserved refs/heads/feature/phantom-world refs/heads/wip/phantom-learning`. Для локального исходника выполнены `git rev-parse --show-toplevel`, `git rev-parse --verify` для исходного ref и commit, `git worktree list`, `git remote -v` и `git for-each-ref`. В независимом clone использованы `git clone --no-checkout --single-branch --depth 1 --branch wip/coordination-130-preserved -c core.longpaths=true`, `git switch --no-track --create wip/phantom-learning c5b81f89ce02030eff8441395b0411137a110b2f`, `git symbolic-ref --short HEAD`, `git rev-parse --verify HEAD`, `git status --short --branch`, `git for-each-ref`, `git ls-tree`, `git ls-files --eol` и `git diff` для проверки scope и CRLF.

Staging сделан через `git add -- <точный allowlist>`, проверен `git diff --cached --name-only`, `git diff --cached --stat` и `git -c core.whitespace=cr-at-eol diff --cached --check`. `git commit` и `git push origin HEAD:refs/heads/wip/phantom-learning` разрешены владельцем для этой ветки. Артефакты сборки, приватная SQL-папка, модель и логи остались локальными.

В продолжении сверены `git symbolic-ref --short HEAD`, `git rev-parse --verify HEAD`, `git status --short --branch`, `git log -4 --oneline`, `git ls-files --eol -- <точные пути>`, `git diff --numstat`, `git diff --stat`, `git diff --name-only <база> HEAD -- <защищённые пути>`, `git -c core.whitespace=cr-at-eol diff --check` и `git merge-base --is-ancestor <предыдущий GitHub HEAD> HEAD`. Scope staging сверялся по `git diff --cached --name-only --relative`. GitHub SHA повторно сверены через `git ls-remote origin refs/heads/wip/coordination-130-preserved refs/heads/feature/phantom-world refs/heads/wip/phantom-learning`. Команды использовались для сверки базы, ограниченного staging, публикации и контроля защищённых refs.
