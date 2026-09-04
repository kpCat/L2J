# Phantom World: быстрый локальный запуск (High Five)

Все команды выполняются из корня `L2J_Mobius_CT_2.6_HighFive`. Shipped-конфиг безопасно выключен; включение делается только явным копированием preset.

## 1. Что уже умеет текущий release

Goal030 принят: 20 доменов release matrix покрыты, pending-доменов нет. Phantom World создаёт durable профиль, аккаунт и обычного `Player`, использует общий Scheduler/Decision pipeline, World, combat, progression, economy, party, rift, PvP, raid, social/conversation и clan owners. Поддержаны restart, operator status/trace, `drain`, `disable` и безопасный двухфазный Phantom-only reset/reseed.

## 2. Что НЕ входит в текущий release

Не реализованы living population ecology/gameplay knobs, siege AI, автоматизация class quests, Kamaloka и Pailaka. Quest acquisition catalog покрывает только ограниченные kill/collection drop subsets Q102/Q152; это не generic whitelist quest adapter и не quest solver. Полный исходный master-plan vision шире принятого Goal030 slice. Текущие настройки и отложенные Goal033 knobs перечислены в `docs/phantoms/PHANTOM_OPERATOR_TUNING_RU.md`.

## 3. Требования: JDK, Ant, MariaDB, jars, data и geodata

Нужны JDK 25 с заданным `JAVA_HOME`, Apache Ant 1.10.x и доступная MariaDB из `dist\login\config\Database.ini` и `dist\game\config\Database.ini`. Проверяемые артефакты: `dist\libs\LoginServer.jar`, `dist\libs\GameServer.jar`, 19 файлов в `dist\game\data\phantoms\`. Geodata лежит в `dist\game\data\geodata\`: при её отсутствии серверный fallback допустим, но навигация считается `DEGRADED`, и preflight выдаёт warning.

Проверьте инструменты:

```powershell
java -version
ant -version
```

Если `ant` не находится в `PATH`, во всех командах ниже замените `ant` на `.\.phantom-local\apache-ant-1.10.17\bin\ant.bat`.

## 4. Fresh DB setup

1. Убедитесь, что имена и реквизиты fresh Login/Game DB записаны в `dist\login\config\Database.ini` и `dist\game\config\Database.ini`.
2. Если база с таким именем уже существует, сначала сделайте внешний backup средствами MariaDB.
3. Запустите штатный installer из его рабочей папки:

```powershell
Push-Location .\dist\db_installer
cscript.exe //nologo .\DatabaseInstaller.vbs
Pop-Location
```

4. В installer выберите установку Login и Game DB с реквизитами из двух config-файлов. Для действительно fresh DB разрешено создание базы. Не выбирайте reset для базы, данные которой нужно сохранить.
5. Installer применит все `.sql` в `dist\db_installer\sql\game\` по имени; Phantom-порядок: `phantom_profiles.sql`, `phantom_reservations.sql`, `phantom_reservations_checkpoint2.sql`.

## 5. Existing DB schema check/upgrade

Сначала выполните read-only проверку:

```powershell
ant phantom-local-play-preflight
```

Если указано `SCHEMA_*_MISSING`, сделайте backup существующей Game DB, снова запустите `dist\db_installer\DatabaseInstaller.vbs` и выберите **Install on Existing Database**, а не reset/delete. Это штатный route: он повторно проходит весь каталог `sql/game`, поэтому backup обязателен. Phantom SQL idempotent (`CREATE ... IF NOT EXISTS`); отдельного competing installer нет. После явного operator action повторите preflight. Server startup и preflight схему не меняют.

## 6. Build: точная команда

```powershell
ant jar
```

Успех означает наличие обоих файлов в `dist\libs\`. Если preflight сообщает `RUNTIME_ARTIFACTS_MISSING`, сначала выполните эту команду.
## 7. Как безопасно применить local-play preset

Сначала сохраните текущий файл вне tracked config, затем скопируйте versioned пример:

```powershell
New-Item -ItemType Directory -Force .\.phantom-local | Out-Null
Copy-Item .\dist\game\config\Custom\PhantomPlayers.ini .\.phantom-local\PhantomPlayers.ini.before-local-play -Force
Copy-Item .\docs\phantoms\examples\PhantomPlayers.local-play.ini .\dist\game\config\Custom\PhantomPlayers.ini -Force
ant phantom-local-play-preflight
```

Ожидаются `PRESET_RUNNABLE`, `DATA_PACKS_READY`, `PHANTOM_SCHEMA_READY`. Точный применённый preset даёт `PASS_WITH_WARNINGS`, потому что runtime config локально включён; это напоминание вернуть safe config после игры. Значения preset: enabled, population 10, ACTIVE 5, materialization cap 32. Пароли preflight не печатает, DB проверяет только read-only metadata.

## 8. Как запустить LoginServer и GameServer стандартными .bat

После PASS/PASS_WITH_WARNINGS откройте два процесса в таком порядке:

```powershell
Start-Process .\dist\login\LoginServer.bat
Start-Process .\dist\game\GameServer.bat
```

Оба `.bat` сами переходят в свою папку. Сначала дождитесь готовности LoginServer, затем GameServer. Не запускайте второй экземпляр GameServer на той же DB.

## 9. Какие startup строки и состояния ожидать

В GameServer после загрузки World/data/scripts/managers и offline owners должна появиться секция `Phantom World`. Ошибка `Phantom World failed to start` означает, что сервер не готов к local play. Нормальный итог: GameServer сообщает общий `Started`, а `//phantom status` показывает `runtime=RUNNING`, `scheduler=RUNNING`, `decision=RUNNING`, `runtimeConfigured=true`; после заполнения population значение `active` должно стать больше нуля и не превышать 5.

## 10. Как зайти клиентом

Клиент в репозиторий не входит. Используйте уже настроенный High Five client того же протокола: его login host должен указывать на запущенный LoginServer. Войдите обычным аккаунтом, выберите/создайте персонажа и зайдите в мир. Для operator-команд нужен персонаж с GM access level, в котором разрешена команда `admin_phantom`.

## 11. Как проверить //phantom status

В GM-чате выполните:

```text
//phantom status
```

Нормальный local-play результат:

- `configured=true`, `operatorMode=AUTO` или `ENABLED`, `desiredRunning=true`;
- `runtimeConfigured=true`, `runtime=RUNNING`;
- `scheduler=RUNNING`, `decision=RUNNING`;
- `active` в диапазоне 1..5 после bootstrap, `activePeak` не выше 32;
- `shutdownFailures=0` в устойчивом состоянии.

`ready`, `due`, `accepted` являются рабочими очередями и могут быть нулевыми в момент снимка. Для видимости найдите Phantom рядом с населёнными зонами после bootstrap; durable population создаётся асинхронно.

## 12. Как включить runtime оператором после drain/disable

Если `PhantomPlayers.ini` всё ещё содержит enabled preset, но текущий процесс был остановлен командой оператора, выполните:

```text
//phantom enable
//phantom status
```

Нормальный control result — `STARTED` либо `ALREADY_RUNNING`. `CONFIG_DISABLED` означает, что config был загружен с `EnablePhantomSystem=False`; измените файл и перезапустите GameServer, потому что config не перечитывается этой командой.
## 13. Как выключить: drain -> disable

Перед остановкой GameServer выполните:

```text
//phantom drain
//phantom status
//phantom disable
//phantom status
```

Если первый drain попал в активную операцию и вернул `SHUTDOWN_FAILED`, ownership не потерян: повторите `//phantom drain` до `DRAINED`, затем `//phantom disable`. Повторные команды idempotent (`ALREADY_DRAINED`, `ALREADY_DISABLED`). Финальное состояние: `runtimeConfigured=false`, Scheduler/Decision `STOPPED`, active 0.

## 14. Как вернуть shipped safe config

После drain/disable остановите GameServer и восстановите backup:

```powershell
Copy-Item .\.phantom-local\PhantomPlayers.ini.before-local-play .\dist\game\config\Custom\PhantomPlayers.ini -Force
Select-String -Path .\dist\game\config\Custom\PhantomPlayers.ini -Pattern 'EnablePhantomSystem|PhantomPopulationTarget|PhantomPopulationActiveTarget'
ant phantom-local-play-preflight
```

Должны быть `False`, `0`, `0` и `SHIPPED_CONFIG_SAFE`. Удалять durable DB rows для выключения не нужно.

## 15. Troubleshooting

- `DB_UNAVAILABLE`: проверьте, что MariaDB запущена и `dist\game\config\Database.ini` содержит рабочий host/port/database; preflight не выводит пароль.
- `SCHEMA_TABLE_MISSING`, `SCHEMA_INDEX_MISSING`, `SCHEMA_CONSTRAINT_MISSING`: сделайте backup и используйте раздел 5; имя отсутствующего объекта указано точно.
- `DATA_MISSING`/ошибка catalog hash: восстановите названный versioned XML/TSV из `dist\game\data\phantoms\`; не подменяйте hash вручную.
- `SHIPPED_CONFIG_SAFE` и population 0: это нормальный безопасный checkout; примените раздел 7.
- `PRESET_INVALID`: проверьте связь `ACTIVE <= population <= scheduled` и `ACTIVE <= materialized`; проще снова скопировать versioned preset.
- `GEODATA_DEGRADED`: запуск поддержан, но сложная навигация хуже; geodata не входит в репозиторий и не должна коммититься.
- `runtime=FAILED` или `SHUTDOWN_FAILED`: сначала повторите drain, затем проверьте status; не запускайте второй owner поверх retained runtime.
- population 10, но visible ACTIVE нет: подождите bootstrap, проверьте `active`, `scheduler`, `decision`; ACTIVE-фантомы находятся в игровом мире, но не обязаны появляться рядом с конкретным GM.
- `RUNTIME_ARTIFACTS_MISSING`: выполните `ant jar`.

## 16. Как не потерять и не продублировать durable identities при restart

1. Перед restart выполните bounded `//phantom drain` до `DRAINED` и убедитесь, что `runtimeConfigured=false`.
2. Остановите GameServer штатно; LoginServer можно оставить работающим.
3. Не удаляйте `phantom_profiles`, `phantom_profile_components`, связанные `characters`/`accounts` и не запускайте DB reset.
4. Запустите `dist\game\GameServer.bat` снова. Population восстанавливает те же profile ID, character object ID и reserved account; не создавайте параллельный GameServer.
5. Проверьте `//phantom status`. Для полного выключения после drain используйте disable и верните safe config из раздела 14.

## 17. Как безопасно сбросить только Phantom population

Reset — явная operator-команда, а не startup flag. Сначала выполните read-only preview:

```text
//phantom reset preview
```

Preview показывает:

- exact число Phantom profiles, characters и reserved accounts;
- категории и row counts, которые будут удалены или безопасно detached;
- mail/history и другие world effects, которые сохраняются;
- `snapshotHash`;
- blockers;
- одноразовый `TOKEN`, действующий 120 секунд, только если операция безопасна.

Если список `blocked` не пуст, token не выдаётся и confirm невозможен. Исправьте указанное shared состояние штатным владельцем: например, завершите item auction, clan/wedding/cursed-weapon lifecycle. Не удаляйте shared rows вручную.

Reset без нового населения:

```text
//phantom reset confirm <TOKEN>
```

Reset и немедленный reseed по уже загруженному preset:

```text
//phantom reset confirm <TOKEN> reseed
```

Отмена preview:

```text
//phantom reset cancel
```

Confirm принимает только текущий snapshot и один раз. Wrong, expired, cancelled, already-used или stale token ничего не удаляет. Перед транзакцией runtime проходит canonical drain; `SHUTDOWN_FAILED` означает полный отказ без reset mutation.

`RESET_COMPLETE` означает, что старые Phantom profiles/characters/accounts удалены. `RESET_RESEEDED` означает, что существующий PopulationManager начал создавать новое население. `RESET_CONFIG_DISABLED` означает, что reset успешно завершён, но reseed не запускался, потому что загружен `EnablePhantomSystem=False`. Для изменения preset остановите сервер, измените config и перезапустите его: команда не перечитывает файл.

Reset не является «машиной времени»: он не отбирает предметы у людей, не откатывает завершённые сделки, mail, PvP/форумную историю и законные изменения мира. Только доказанное private Phantom state удаляется; безопасные bilateral relations detach, неоднозначная shared ownership блокирует всю операцию.

Automated Goal032 tests используют только guarded throwaway DB `127.0.0.1:3308/l2jmobiush5_phantom_test`. Ни server startup, ни preflight, ни эти tests не изменяют production DB автоматически; production reset происходит только после GM preview и explicit confirm.
