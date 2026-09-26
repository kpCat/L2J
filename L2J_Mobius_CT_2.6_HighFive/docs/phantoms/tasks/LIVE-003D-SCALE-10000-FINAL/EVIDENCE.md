# LIVE-003D — финальный runtime evidence

## Исходный gate

- Ветка `feature/phantom-world`, начальный HEAD `5cfc38b1ea14197050d23872e573e111849a074e`.
- LocalPlay до изменения: Login/Game `STOPPED`, `staleRecord=False`, порты 2106/9014/7777 закрыты. Штатный `Check-LocalPlay.ps1` дал `CONFIG PASS` и `Population=5000`.
- Read-only PLAY snapshot `play-baseline-*.tsv`: `l2jmobiush5_localplay3`, 5000 managed, 5000 linked, 5000 READY (`04`), уникальных имён и аккаунтов по 5000, дубликатов 0/0, max profile ID 5000. `background.state=4978`, `background.catchup=4978`, `population.ecology=5000`. DB connections 1/151 при остановленном LocalPlay.
- Принятый private GameServer.jar: SHA-256 `9FD64574CADF7628110684E79A457E1D88462B23F0FFA50AC625275147A0636F`; production build в этой задаче не запускался. Посторонние изменения рабочего дерева не использованы.

## Private target

- Backup до изменения: `artifacts/local-play/runtime/backup/LIVE-003D-SCALE-10000-FINAL/PhantomPlayers.ini` и `local-play.json`.
- В `artifacts/local-play/runtime/game/config/Custom/PhantomPlayers.ini` изменена ровно строка `PhantomPopulationTarget = 5000` на `10000`; в private `local-play.json` — ровно `populationTarget: 5000` на `10000`. Побайтовое сравнение с backup после одной подстановки: `True` для обоих файлов.
- Инвентаризация SHA-256 всех private `game/config`, `login/config`, обоих runtime JAR и manifest: `private-hashes-before.tsv` и `private-hashes-after.tsv` отличаются только двумя названными файлами. ActiveTarget=64, MaxMaterialized=128, MaxScheduled=10000, CreationInFlight=2, pulse=100 ms, boundaries=64 не изменялись.
- После изменения штатный checker: `CONFIG PASS`, `Population=10000`, оба процесса `STOPPED`, порты закрыты.

## Запуск и измерение

- Канонический `Start-LocalPlay.ps1 -Background`: Login PID 14096, Game PID 24844. `Check-LocalPlay.ps1`: Login владеет 2106/9014, Game владеет 7777, `staleRecord=False`.
- GameServer: `Server loaded in 37 seconds` и `Registered on login as Server 1: Bartz` в `2026-09-26T20:51:31.679+03:00`. Это начало 45-минутного ramp window; deadline `21:36:31.679+03:00`.
- Применён read-only монитор по локальному образцу LIVE-003C: `Invoke-PlayReadSnapshot.ps1`, `Sample-10000.ps1`, `Monitor-10000.ps1` в `.phantom-local/logs/LIVE-003D-SCALE-10000-FINAL/`. SQL ограничен `SELECT`/`SHOW`; минутный ряд включает identity, READY, unfinished, background/catchup/ecology, heap, threads, DB, fatal markers и PID/ports.

## Ramp, soak и финальный stop

- **BLOCKED: `SCALE_10000_DEADLINE_BLOCKED`.** В точке 45 минут, `2026-09-26T21:36:31+03:00`, read-only sample показал 9326 managed / 9324 linked, 9325 READY и 1 незавершённый state на момент отдельных SELECT, уникальных имён и аккаунтов по 9324, дубликатов 0/0. До цели недоставало 674 managed и 676 linked. Target 10000/10000 не подтверждён, поэтому время достижения и 15-минутный soak отсутствуют.
- От исходных 5000 до deadline прибавилось 4326 managed за 45 минут: 96,13 managed/min. Для сравнения, принятый LIVE-003C показал 202,82 managed/min на своём участке. В текущем ряду в `21:07:00` было 6946 managed, в `21:21:10` — 8130, в `21:36:31` — 9326: на поздней части ramp темп ниже раннего. Причина снижения внутри runtime не доказана; бюджеты и production-код не менялись.
- `LIVE003D_RUNTIME_10000.tsv`: 49 read-only samples, включая отдельный первый и далее примерно минутный ряд. Heap min/max 80,32–93,75% от 4096 MiB, максимальная серия `>90%` — 2 samples; Game threads 160–164; DB connections 13–15 из 151; fatal/OOM markers 0; duplicate names/accounts 0/0 на каждом sample. Owned PID/ports подтверждались в каждом sample.
- Background committed positions выросли с 4978 до 8938 к deadline; catchup также 8938. Ecology rows выросли с 5000 до 9326. Эти данные подтверждают forward progress, но не полное покрытие background.
- По blocker штатный `Stop-LocalPlay.ps1` остановил только Game PID 24844 и Login PID 14096. Последующий `Check-LocalPlay.ps1`: оба `STOPPED`, `staleRecord=False`, 2106/9014/7777 закрыты, `CONFIG PASS`, private target оставлен 10000.
- Post-stop read-only PLAY snapshot `play-post-stop-*.tsv`: 9352 managed, 9350 linked, 9350 READY, 2 state `00`, max profile ID 9352, уникальных имён/аккаунтов 9350/9350, дубликатов 0/0; `background.state=8961`, `background.catchup=8961`, `population.ecology=9352`; DB connections 1/151. Создание продвинулось во время stop после deadline sample; это не засчитывается в 45-минутный gate. Все профили сохранены, прямых PLAY DML/DDL/reset/reseed/delete не было.
- Финальная повторная SHA-256 инвентаризация `private-hashes-final.tsv` полностью совпала с `private-hashes-after.tsv` по 90 файлам. GameServer.jar по-прежнему `9FD64574CADF7628110684E79A457E1D88462B23F0FFA50AC625275147A0636F`; иных private config/hash drift нет.
