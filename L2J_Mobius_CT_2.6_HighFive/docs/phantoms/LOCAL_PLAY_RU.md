# Phantom World: локальный игровой выпуск

Этот сценарий собирает High Five из текущих исходников, создаёт полностью новый локальный мир в отдельной БД и копирует `dist` в приватный runtime. Исходные `Database.ini`, `hexid.txt`, SQL и shipped-конфиги не изменяются. Существующая БД `l2jmobiush5` защищена и этим workflow не выбирается, не очищается и не изменяется.

## Быстрый старт

Из PowerShell в каталоге `L2J_Mobius_CT_2.6_HighFive`:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\phantom-local-play\Build-FreshLocalPlay.ps1
```

Команда выполняет свежий `ant -q jar`, доказывает отсутствие БД `l2jmobiush5_localplay`, создаёт её без overwrite/reset, штатным `DatabaseInstaller` устанавливает login+game schema, штатным `GameServerRegister` создаёт новый server ID 1/hexid и применяет preset `Lively` только в `artifacts\local-play\runtime`. Затем:

```text
artifacts\local-play\runtime\CHECK_LOCAL_PLAY.cmd
artifacts\local-play\runtime\START_LOCAL_PLAY.cmd
```

После готовности обоих серверов подключите обычный High Five client к своей локальной конфигурации сервера. На экране входа используйте аккаунт `localplayer` и любой новый пароль. В staging-копии включён штатный `AutoCreateAccounts=True`, поэтому LoginServer создаст новый аккаунт при первом успешном входе. Скрипты сами аккаунты не создают и БД не изменяют.

Перед запуском можно проверить пакет:

```text
artifacts\local-play\runtime\CHECK_LOCAL_PLAY.cmd
```

Остановка:

```text
artifacts\local-play\runtime\STOP_LOCAL_PLAY.cmd
```

Stop-скрипт работает только с PID и временем старта процессов, записанными этим runtime. Сначала он пытается закрыть окно процесса, затем останавливает только подтверждённый PID. Глобальный `taskkill /F java.exe` не используется.

## Preset населения

| Preset | Durable/scheduled target | ACTIVE target | Materialized cap | Назначение |
|---|---:|---:|---:|---|
| `Balanced` | 120 | 24 | 32 | Более лёгкий локальный режим |
| `Lively` | 160 | 32 | 48 | Рекомендуемый режим по умолчанию |
| `Stress` | 240 | 48 | 64 | Верхний локальный профиль для наблюдения нагрузки |

Пример выбора:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\phantom-local-play\Build-FreshLocalPlay.ps1 -Preset Balanced
```

Во всех трёх вариантах scheduler pulse остаётся `100 ms`, budget — `128 profiles/pulse`, ecology включена с `LIVING`, Humanized Semantic v3 и custom overlay включены. Autonomous BUY+SELL private market остаётся включённым по принятому `PhantomMarket.ini`; MANUFACTURE не открывается бесплатно и не переопределяется.

32 ACTIVE на весь мир High Five не означают 32 персонажа возле игрока. Population scheduler распределяет их по миру и активности. Этот выпуск не добавляет искусственный teleport-follow или фальшивую плотность рядом с клиентом.

## Другой аккаунт и Personal QoL

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\phantom-local-play\Build-FreshLocalPlay.ps1 -Account myaccount
```

Имя должно соответствовать `[a-z0-9_-]`, длина — не более 45 символов. Builder записывает его только в staging `AllowedAccounts` и включает принятые Personal QoL: cross-class skills, crystallization, Seven Signs access, party support, positive effect duration policy, premium storefront и quest over-level relief. Множители длительности остаются принятыми нейтральными `1.0`, потому что выпуск не придумывает новый баланс. `EnableServerWideAutoNoblesse=True` также включён, но этот конкретный принятый switch глобален для runtime, а не ограничен account allowlist.

## Mature и Diagnostics

По умолчанию оба режима выключены. Явное включение:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\phantom-local-play\Build-FreshLocalPlay.ps1 -Mature
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\phantom-local-play\Build-FreshLocalPlay.ps1 -Diagnostics
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\phantom-local-play\Build-FreshLocalPlay.ps1 -Mature -Diagnostics
```

Diagnostics предназначен для bounded trace, а не для постоянного подробного логирования.

## База данных и секреты

Рекомендуемый fresh workflow:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\phantom-local-play\Build-FreshLocalPlay.ps1
```

По умолчанию создаётся только новая `l2jmobiush5_localplay`. Другое безопасное имя можно задать через `-DatabaseName`, например `-DatabaseName l2jmobiush5_localplay2`. Имена исходных БД из обоих source `Database.ini`, `l2jmobiush5_phantom_test` и системные `mysql`, `information_schema`, `performance_schema`, `sys` запрещены. Если target уже существует, команда завершается до сборки и ничего в нём не меняет; автоматического drop/reset нет. При частичной ошибке runtime остаётся fail-closed, а созданную БД нужно проверить и удалить вручную либо выбрать новое имя.

DB login/password читаются из существующих локальных конфигов только в память и передаются Java-процессам через stdin. Они не записываются в manifest, командную строку, документацию или sanitized ZIP. После provisioning manifest получает `FRESH_LOCAL_PROVISIONED` и точное безопасное имя БД; `CHECK_LOCAL_PLAY.cmd` и `START_LOCAL_PLAY.cmd` дополнительно сверяют это имя с обоими private runtime `Database.ini`.

Canonical installer сначала применяет login SQL, затем game SQL; внутри каждого каталога файлы идут по имени без учёта регистра, как в текущем `DatabaseInstaller`. Freshness gate до первого запуска подтверждает пустые `accounts`, `characters`, clan/offline/private-store и все Phantom runtime/profile tables. Автоматический workflow не создаёт `localplayer`: первый аккаунт появляется только после ручного входа благодаря штатному `AutoCreateAccounts=True`.

Старый `Build-LocalPlay.ps1` оставлен как explicit existing-DB/fail-closed путь:

Private runtime может содержать копии существующих `dist\login\config\Database.ini` и `dist\game\config\Database.ini`. Builder не выводит логин или пароль и не проверяет соединение. Поэтому обычная сборка всегда fail-closed: даже полностью заполненные скопированные конфиги получают статус `COPIED_PRIVATE_UNVERIFIED`, создаётся `DB_CONFIG_REQUIRED.txt`, а `Start-LocalPlay.ps1` отказывается запускать Java.

Если `l2jmobiush5` действительно является вашей намеренной локальной/private gameplay DB, пересоберите runtime с явным подтверждением человека:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\phantom-local-play\Build-LocalPlay.ps1 -ConfirmExistingDatabaseForLocalPlay
```

Switch означает явное acknowledgement скопированной существующей конфигурации, но не является автоматическим доказательством безопасности базы. Только при наличии URL/Login/Password в обоих `Database.ini` manifest получает `USER_CONFIRMED_EXISTING`, marker не создаётся, и startup разрешается. Иначе замените оба staging `Database.ini` на non-production DB и пересоберите/пометьте runtime поддержанным workflow. Sanitized ZIP всегда остаётся `DB_CONFIG_REQUIRED` и не является startable.

Fresh workflow не запускает `prepare-phantom-test-db` и никогда не использует `l2jmobiush5` как target. Если нужен shareable архив без credentials:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\phantom-local-play\Build-LocalPlay.ps1 -SanitizedZip
```

Для fresh workflow используйте тот же switch: `Build-FreshLocalPlay.ps1 -SanitizedZip`. Архив всё равно получает `DB_CONFIG_REQUIRED`, пустые `Login`/`Password` и не получает статус `FRESH_LOCAL_PROVISIONED`.

Архив `artifacts\local-play\L2J-H5-Phantom-LocalPlay-sanitized.zip` содержит пустые `Login`/`Password` в обоих Database.ini и marker `DB_CONFIG_REQUIRED.txt`. Приватный runtime и ZIP защищены `.gitignore` и не должны коммититься.

## Логи, проверка и остановка

- LoginServer logs: `artifacts\local-play\runtime\login\log`.
- GameServer logs: `artifacts\local-play\runtime\game\log`.
- PID records: `artifacts\local-play\runtime\local-play\pids`.
- Effective manifest без секретов: `artifacts\local-play\runtime\local-play.json`.

`CHECK_LOCAL_PLAY.cmd` сверяет manifest с staging-конфигами, наличие JAR, записанные PID и локальные порты. Он никогда не печатает DB credentials.

Если сервер не стартует, сначала проверьте `CHECK_LOCAL_PLAY.cmd`: он показывает только безопасный DB status и завершает readiness с ошибкой для `COPIED_PRIVATE_UNVERIFIED`/`DB_CONFIG_REQUIRED`. Для обычной остановки используйте только `STOP_LOCAL_PLAY.cmd`.

## Как изменить численность позже

Предпочтительный путь — заново запустить builder с другим `-Preset`: runtime воспроизводимо пересоздаётся из чистой копии `dist`. Для ручной локальной настройки меняйте только staging `game\config\Custom\PhantomPlayers.ini` и сохраняйте правила:

- `PhantomPopulationActiveTarget <= MaxMaterializedPhantoms`;
- `PhantomPopulationActiveTarget <= PhantomPopulationTarget`;
- `PhantomPopulationTarget <= MaxScheduledPhantomProfiles`;
- `PhantomSchedulerPulseMillis=100` оставлять без доказанной необходимости менять;
- поднимать scheduler/DB budgets только после наблюдаемого backlog.

После изменения нужен restart GameServer. Исходный `dist\game\config\Custom\PhantomPlayers.ini` не трогайте.

## Custom Semantic v3 overlay

Пользовательский слой расположен в staging-копии `game\data\phantoms\conversation\humanized\custom\` и загружается после core v3. Редактируйте только предусмотренные `my-*.xml`, сохраняя их schema/ID и валидный UTF-8. Затем перезапустите GameServer. Для изменения custom semantic XML перекомпиляция Java не нужна; новый builder нужен только если требуется снова получить чистую staging-копию.

Core v3 остаётся функционально-first: он не является open-domain LLM и не должен придумывать биографию, общую историю или физическую сцену. Mature остаётся отдельным explicit opt-in.

## Ожидаемое масштабирование

Стоимость durable target в основном относится к scheduler state и DB/disk footprint. Основная CPU/RAM/world/known-list стоимость растёт с ACTIVE/materialized числом, поэтому `Stress` тяжелее `Lively`, а `Lively` тяжелее `Balanced`. Scheduler использует общий bounded pulse и не создаёт thread на каждого Phantom. Точные проценты производительности не заявляются: на конкретной машине смотрите RSS/CPU, status/backlog/overload warnings и логи, прежде чем повышать цели.

Исходные `.ini`, русские комментарии и shipped-safe defaults остаются нетронутыми; staging можно удалить и сгенерировать заново в любое время.
