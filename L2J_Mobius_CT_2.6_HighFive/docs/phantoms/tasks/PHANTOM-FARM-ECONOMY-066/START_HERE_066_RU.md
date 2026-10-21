# Candidate066 — автономная поставка High Five

Статус кампании: **CORE066=PARTIAL**. Связанный Main dwarf gate не закрыт; focused native доказательства не заменяют эту сцену. Личная клиентская приёмка — DEFERRED, вход владельца для завершения кампании не требуется.

Выполнять из каталога candidate, содержащего этот файл:

```powershell
.\START.cmd
.\STATUS.cmd
.\STOP.cmd
```

START запускает собственные LoginServer/Main скрыто. STOP использует штатный shutdown и проверяет PID/incarnation; force stop не предусмотрен. На момент передачи собственные JVM остановлены. Для запуска необходимы уже имеющиеся JDK25 и локальная MariaDB3308.

Привязка: `BUILD_INFO066.json` и `SOURCE_BUILD_INFO066.json` содержат полный code commit, SHA-256 whole JAR, source archive и test tree. `FINAL_PUBLICATION066.json` фиксирует проверенный live HEAD ветки `feature/phantom-world`; он может отличаться report-only commits от code commit. `CONFIG066.tsv` и `GEODATA066.tsv` фиксируют конфигурацию и203 физических GeoData.

Только собственная БД `l2jmobiush5_phantom_release_066a`; порты2146/7846/9146. Population48, FRESH/age0, active target32, soft materialization16 + bounded burst16, физический ceiling32. Данные уже созданы; повторная инициализация/импорт не требуются.

Пассивная автономная точка `Task066View`, synthetic OFF, actualNetworkSession=false. Она не является client login или игровым контроллером. Учётные данные находятся только в private local файле; не включать private, credentials или БД в публичный архив.

Полная матрица: `docs/phantoms/tasks/PHANTOM-FARM-ECONOMY-066/ACCEPTANCE066.tsv` в исходном checkout. Итоговый отчёт: `docs/phantoms/reports/PHANTOM-FARM-ECONOMY-066.md`. Исторические057–065 и MAIN/PLAY не запускать этими средствами.
