# ACCEPTANCE064 — законченные игровые факты, не количество коммитов

**Обязательны:** source/JAR/schema/episode identity, исходные TEST preconditions, actual native effects, durable conservation и exit codes. Финальные состояния каждой строки: `SERVER_VERIFIED`, `PARTIAL`, `BLOCKED`, `NOT_RUN`, плюс отдельное `OWNER_CLIENT_QA_DEFERRED`. Старые accepted claims можно reuse только при проверенной неизменности соответствующего owner/data; текущие проблемы требуют новых current-JAR доказательств. Сохранить отрицательные результаты.

| ID | Проверяемый законченный результат | Не засчитывать |
|---|---|---|
| PREP-ENV | Java25/Ant/MariaDB и GeoData инвентаризированы, текущий Git source отделён от старых приватных runtime assets, owned64 DB/ports/JAR/config exact validated | Отсутствие файлов в Git названо потерей БД; старый релиз просто скопирован как новый |
| M1-LIFE | ordinary Main без test controller: живой `Player` в World, farm→reward→другая выбранная полезная native операция→return→ещё полезные действия; real stop/sameDB restart; корректные history/goal | Только SQL XYZ, stale observer, TEST launcher, SAVE без нового действия |
| M1-LIFECYCLE | World↔Background→World, bounded death/respawn/admission, no double reward, owned work settled; G4/G6 различены | Запрещённый forced crash или G6 «PASS» из normal STOP |
| M2-PARTY | Фактические consent/invite→native Party→follow/assist/heal/buff/recharge/res roles→leave→independent farm (historical valid reuse + affected suite) | Только зарегистрированный invitation или all-phantom party как network-human QA |
| M3-SOCIAL | Native address/delivery/HOLD/RESUME, truthfulness, scoped recall after restart; current affected controls | Generated phrase без доставки, произвольное обещание игры |
| M4-CONFLICT | Собственные eligible actors: legal PvP/PK target, flag/karma/ally/safe negatives, real damage/defense/focus, bounded retreat/regroup/end conflict→new action | Только Player HP damage без восстановления/исхода |
| M5-RAID | Native RaidBoss25199 (или заранее объявленный поддержанный альтернативный), actual full victory/stock reward/conservation + coordinated roles/healing/minions/regroup→new action | Karik вместо RaidBoss, HP частично сняли, fake loot, смерть всех |
| M6-KAMA | Сохранён полный Kamaloka native entry, victory, reward/reuse, natural closing/exit, affected regression | Вход или начало damage без конечного instance state |
| M6-RIFT | Native supported Rift: ready/fee/consent/entry, совместная Party/instance, combat/EXP, room/timer/exit/disband/death/logout/reuse; legal negatives | Fee уплачен, но группа осталась снаружи; неизвестный room выдан XY |
| M6-PAILAKA | Pailaka earned-timer/quit/stop clean drain либо честно суженный residual blocker без эффекта на другие instances | Оставшиеся 4 таймера названы прошедшей проверкой |
| M7-CAREER | Canonical Phantom-only 20/40/76/Noble, lawful race/branch/sex/index, saved/load, actual skills, post-class farm/decision | Подготовленный80 представлен как нафармленный; Noblesse заменяет subclass |
| M7-LIVING | Разные роли (DD/mage/tank/heal/dwarf/Kamael), skill/SP/stock, соответствующее gear, выбор solo/party после оценки риска, no infinite out-of-stock loop | Один гремлин, fake supply или манипуляция SP в ходе сцены |
| M7-SUBCLASS | Supported native legal subclass transfer/save/return либо `PARTIAL` с конкретным отсутствующим owner | Раздел назван PASS, потому что появился Noble |
| KEEP-111 | Все исходные 111 case IDs не исчезли, affected common/party/social/combat/lifecycle regressions с верными JAR и exit | Изменение состава так, чтобы убрать прежний RED; чужой артефакт за текущий |
| DELIVERY | Кандидат64 с distinct ports/schema/ownership/JAR checksum, реальный START→STATUS→STOP→sameDB START→STOP, final STOPPED; committed/pushed source, HEAD=remote | Голый исходный `dist` объявлен готовым сервером |

**Степень покрытия:** полное серверное закрытие M1–M7 возможно только при реально прошедших применимых обязательных строках (G6 при объективно недоступном разрешённом испытании остаётся отдельным DEFERRED, не подменяется PASS). OWNER QA через настоящий клиент отложено владельцем и не блокирует **доступную** серверную работу. При объёме слишком широком для 12ч допускается `PARTIAL` с конкретными непрошедшими цепочками, но не подмена критериев, не добавление ещё 12ч без запроса.

Для всех успешных native эпизодов по действиям `Player`, `Party`, `Monster`, `Quest/Instance` записать: source SHA / JAR SHA / owned schema / actor IDs и роль / prepared facts / момент START / actual events / after facts / STOP/save boundary / negative controls. Не сохранять пароль или полный SQL дамп в Git.
