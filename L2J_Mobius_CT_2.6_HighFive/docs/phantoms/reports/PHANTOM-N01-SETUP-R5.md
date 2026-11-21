# PHANTOM-N01-SETUP-R5 — BLOCKED_SETUP_R5

21.11.2026. Штатные SQL Start и отдельный Attest прошли на новой lane `n01-setup-r5`. Дополнительная проверка остановила цепочку до Java с `R5_ATTEST_IDENTITY_MISMATCH`. Профиль 12 и четыре сцены остаются непроверенными. TEST OFF подтверждён.

## Что произошло

- SQL на `127.0.0.1:33331`, схема `l2jmobiush5_phantom_test_131`, principal `task131_runtime@127.0.0.1`, PID 3912, запуск `2026-11-21T10:31:22.6202967Z`. Attest подтвердил 152 таблицы, пустые accounts и characters, отказ чтения чужой системной таблицы. Datadir расположен в собственной `sql-lanes/n01-setup-r5/sql-task131/data`.
- Дополнительная PowerShell-проверка сравнила `Win32_Process.CreationDate` с точным `FILETIME` из штатной аттестации без нормализации. Точный `FILETIME` R5 заканчивается на семь 100-нс тиков сверх целой микросекунды. Read-only проверка на текущей оболочке подтвердила, что WMI отбрасывает эти тики. Штатные Attest и Stop согласовались по PID, полному `FILETIME` и командной строке.
- Штатный Stop завершил собственный процесс по `OWN_PROCESS_HANDLE_EXIT` в `2026-11-21T10:31:36.6090681Z`. PID 3912 исчез. После Stop порты 2106, 33331, 33332 и 33333 свободны. Shared 3308 сохранила PID 7664 и время создания, PLAY Preview SQL на 33318 сохранила PID 20756 и время создания.

## Приёмка

| Gate | Итог | Доказательство |
| --- | --- | --- |
| R5-G0 | GO | HEAD `e4f0042f17c90b2815c3b76fa559717125331581`, parent `0d2ac555dfde3b4b1e5ab2fd6cfeca834e769d7e`. Новая lane свободна. Физический TEST runtime, master OFF, SHA JAR, обычный токен и свободные TEST-порты сверены. Один целевой javac JDK 25 завершился с кодом 0. |
| R5-G1 | Штатный SQL подтверждён | Один Start и один отдельный Attest. Дополнительная проверка вернула `R5_ATTEST_IDENTITY_MISMATCH`. |
| R5-G2/G3 | NOT_RUN | Java SETUP не вызван. Четыре SETUP-артефакта отсутствуют. READY, сцены, респавн, сохранённые навыки и worldPlayers не измерялись. |
| R5-G4 | TEST OFF | Штатный Stop, `off.json` из R5, отсутствие процесса и TEST listeners. До и после совпали protected207, Geo203 в источнике и TEST, training10, checkpoint216 и latch Goal024. |
| R5-G5 | Отчёт | Область публикации ограничена этим файлом и [RESULT.json](PHANTOM-N01-SETUP-R5-RESULT.json). SHA-256 сняты с собственных оригиналов R5. |

Счётчики Start/Attest/Java SETUP/Stop — 1/1/0/1. GS, LS, бой и обучение V4 — 0. Исходники и конфиги не менялись. Studio WIP сохранён. Закрытые оригиналы и ACL R4 не затронуты. Статус TASK131 остаётся PARTIAL131.

STOP.
