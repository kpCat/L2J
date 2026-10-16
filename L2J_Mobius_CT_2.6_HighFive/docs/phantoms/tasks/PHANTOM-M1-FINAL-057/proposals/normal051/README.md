# TASK057: проверенные normal051 capture sources

Это точные source snapshots работающей test-only обвязки TASK057. Они не входят в самостоятельную поставку и не являются новым launcher. Runtime-пути, schema, owner, PID/incarnation, JAR и normal-only guards сохранены. G6 здесь не реализуется.

Исходный private root: `D:\Codex\worktrees\phantom-dev\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\.phantom-local\contract051a` (`P`). Все шесть файлов побайтно сопоставлены с реально проверенными оригиналами:

| Snapshot | Исходный путь относительно P | SHA-256 |
|---|---|---|
| Contract032Observer.java | ops/Contract032Observer.java | 411FA766B2F424B60F666BC846D285AA7DE9F8A1D270AC044EAABBE797E637A4 |
| Control032.ps1 | ops/Control032.ps1 | 050FEB8FBCCCAE1BA3BF2D1637788FF92286CFB0FD45E1391986DBFDCCE47A20 |
| ObserveRestart057.ps1 | ops/ObserveRestart057.ps1 | 33DAFF66D89959DE6406BE5A636F94CE1BFDDDB2075ADB63B9491A346F6079DF |
| CompareRestartsFinal057.py | ops/task057/CompareRestartsFinal057.py | 6F185EE73BB39E63B116F018751687A4EBB1A68D17C8FC421DE96DC7C4E9C5BA |
| PrepareRestartsFinal057.py | ops/task057/PrepareRestartsFinal057.py | F6563CBEBC457FDA291997B6B799B825F6759122C880E550A58BFB8D715F4938 |
| ReadLoadHeight050.java | ops/task057/ReadLoadHeight050.java | EDE5FC7A78D1BB4A4BEF0BD2BDA01556A23E92360B7C210BB46FB8163C164410 |

Локальный `.gitattributes` сохраняет bytes шести source snapshots без преобразования переводов строк, чтобы SHA оставались проверяемыми после checkout.

В точных проверенных private originals есть пять trailing-space строк: Contract032Observer.java624/1031/1061 и ObserveRestart057.ps1 9/379. Они сохранены как ограниченное provenance-исключение; whitespace cleanup изменил бы source SHA. Новые README/отчёт/attributes проверяются отдельно без этого исключения.

Для воспроизведения нужны именно исходные private locations, сохранённые raw/SQL и прежние `ops/validation` зависимости. Python validators используют положение файла для определения P; запуск копии непосредственно из этой папки не является принятой процедурой. Существующие immutable outputs не удалять и не перезаписывать.

Изменения normal recorder:

- Bounded FIFO экспортируется отдельно от медленных read-only telemetry snapshots. Пределы остались прежними: ring512, buffer16MiB, disk512MiB. SQL/I/O в ordinary native hooks не добавлены.
- `CommitScopeFile` принимает только точный prior `RUN[1-8]-057-registered-ids.txt` внутри P: уникальные положительные IDs, максимум1280. Эта read-only подписка устанавливается до boot producers. Native cohort guard1..8 сохранён.
- Normal-only отказ для crash/startup-recovery сохранён. Не применять этот recorder к057x или чужим runtime.

`ObserveRestart057.ps1` переиспользует исходный Probe60..150s, два настоящих stock MOVE и прежние guards. Это G5 useful-continuation capture, а не замена G2 CONTINUITY_V2. Старый RESTART6_057 с уже DEAD10600 и отказом epoch guard сохранён; RESTART6B_057 заранее объявлен отдельно на canonical observer origin.

G5 проверяет весь frozen scope, включая нематериализовавшиеся профили, полные CAS chains, первый actual native load до первой regen, exact MP, full items/skills/fields и отсутствие повторных canonical receipts. Historical RUN3 FAIL остаётся FAIL; его отдельные hash-verified REGISTER facts используются только для проверки предшествующих epochs. Семь различий Z разрешены лишь после независимого подтверждения существующего stock GeoEngine.getHeight правила.

Проверено на gameplay source `9cce0ff72b819a60e30a85a23f2f16df30bf3391`, JAR `0D640DCFF0BBF59D3512EFBE1B50A5131186F07120993059FC8C2AF0B5C3D4B0`, Java25 и PowerShell7.6.5: strict039 RUN4/5/6 PASS; G5 boundaries4→5→6 PASS. Отдельные штатные controls standalone поставки проверены в Windows PowerShell5.1.

Source snapshots не означают G4/G6 PASS. Итоговый статус находится в `docs/phantoms/reports/PHANTOM-M1-FINAL-057.md` и единственном migration057 checkpoint. М1 OPEN, М2 не разрешён.
