# L2-FINAL-PLAY-001-HF2 — isolated fresh local-play database

## Status

`SUCCESS`

## Summary

Добавлен one-command workflow `tools/phantom-local-play/Build-FreshLocalPlay.ps1`. Он строит private runtime, fail-closed проверяет новое имя БД, атомарно создаёт только отсутствующую БД, штатным `DatabaseInstaller.jar` устанавливает login+game schema, штатным `GameServerRegister` создаёт новую identity/hexid, проверяет freshness и только после полного успеха выставляет `FRESH_LOCAL_PROVISIONED`.

Защищённые source DB: `l2jmobiush5` из обоих source `Database.ini`, `l2jmobiush5_phantom_test` и системные схемы. Финальный рабочий новый мир: `l2jmobiush5_localplay3`.

Имена `l2jmobiush5_localplay` и `l2jmobiush5_localplay2` были созданы этим Goal во время двух fail-closed диагностических итераций до финального успеха. По контракту workflow они не удалялись и повторно не использовались: первая осталась после headless-incompatible запуска installer, вторая — после слишком строгой stderr-проверки успешно выполненного register. Их можно удалить вручную после отдельной проверки; автоматический cleanup запрещён.

## Canonical audit and decisions

- `DatabaseInstaller` console option 1 сначала вызывает login, затем game installation.
- Для каждого типа текущая реализация читает только непосредственные `*.sql`, сортирует имена `String.CASE_INSENSITIVE_ORDER`, пропускает пустые/`--` строки и выполняет накопленный statement при строке с завершающим `;`.
- Canonical `CREATE DATABASE` не содержит `IF NOT EXISTS`. Guard повторяет эту атомарную semantics после отдельного metadata absence check, поэтому race с уже существующей схемой завершается ошибкой, а не reuse.
- Stock installer запускается из private runtime, получает host/port/login/password/name через redirected stdin и устанавливает оба набора SQL. Любой `[ERROR]`, process failure или отсутствие success marker блокирует provisioning.
- Canonical `gameservers.sql` создаёт seed ID 2. Stock `GameServerRegister` удаляет ID 2, генерирует `HexUtil.generateHexBytes(16)`, регистрирует ID 1 и пишет canonical properties `hexid.txt`; файл переносится только в private `game/config/hexid.txt`.
- Game/Login `Interface.ini` в private runtime переводятся в console mode. Это делает startup errors наблюдаемыми и не изменяет source configs.
- `Start-LocalPlay`/`Check-LocalPlay` принимают `FRESH_LOCAL_PROVISIONED` только при exact совпадении обеих runtime JDBC DB names с `manifest.databaseName`. Старые fail-closed статусы сохранены.
- `Stop-LocalPlay` сохраняет PID record при access-denied вместо ложного сообщения «уже не запущен».

## Changed files

- `tools/phantom-local-play/Build-FreshLocalPlay.ps1`;
- `tools/phantom-local-play/FreshLocalPlayDatabaseGuard.java`;
- `tools/phantom-local-play/Build-LocalPlay.ps1`;
- `tools/phantom-local-play/Start-LocalPlay.ps1`;
- `tools/phantom-local-play/Check-LocalPlay.ps1`;
- `tools/phantom-local-play/Stop-LocalPlay.ps1`;
- `docs/phantoms/LOCAL_PLAY_RU.md`;
- этот отчёт.

Product Java, SQL/schema, source `Database.ini`, source hexid и другие хроники не изменялись. Bounded exception к ориентиру 8 файлов — сам отчёт является восьмым файлом; независимые artifact families не добавлены.

## Database and freshness evidence

- canonical table inventory: `122/122` tables present;
- до первого server startup пусты `18/18` player-state tables, включая `accounts`, `characters`, items/variables/quests, clan/offline/private-store и все шесть Phantom profile/economy tables;
- `localplayer` отсутствовал после provisioning и после no-login smoke;
- `gameservers` после provisioning содержит ровно одну строку: server ID 1, hexid совпадает с private runtime file;
- manifest: `databaseConfig=FRESH_LOCAL_PROVISIONED`, `databaseName=l2jmobiush5_localplay3`, без login/password;
- оба private runtime JDBC URL указывают только на exact `l2jmobiush5_localplay3`.

После разрешённого startup Phantom scheduler начал population: `phantom_profiles=34`, `characters=34`. Это post-start persisted population, а не пользовательский login/account.

## Verification

- PowerShell parser: PASS для Build-Fresh/Build/Start/Check/Stop;
- Java helper compile: PASS;
- protected source DB rejection: PASS (`l2jmobiush5`);
- system DB rejection: PASS (`mysql`);
- target absence gate + atomic create: PASS;
- existing final target rejection без runtime rewrite: PASS;
- stock `DatabaseInstaller` login+game: PASS;
- schema/freshness: PASS, `122` canonical tables и `18` empty player-state tables;
- stock `GameServerRegister`: PASS, единственный ID 1 и matching private hexid;
- `Check-LocalPlay`: `CONFIG PASS` / `FRESH_LOCAL_PROVISIONED`;
- sanitized ZIP: PASS — `DB_CONFIG_REQUIRED`, marker присутствует, login/password пусты, fresh DB metadata отсутствует;
- bounded no-login startup вне filesystem sandbox: PASS, Login 9014 и Game 7777 открыты, server ID 1 зарегистрирован;
- current login/game `error0.log`: 0 bytes;
- Phantom startup: section `Phantom World` пройдена, GameServer loaded in 36 seconds, population evidence `34/34`;
- safe Stop: PASS, точные PID остановлены, 9014/7777 закрыты;
- `localplayer` после smoke: отсутствует;
- full `ant verify`: не запускался;
- `prepare-phantom-test-db`: не запускался.

Первый sandboxed startup выявил JDK 25 `AccessDeniedException` при dynamic compiler close `HikariCP-7.0.2.jar`; из-за GUI mode main exception удерживался AWT threads и выглядел как timeout. Private console mode сделал причину наблюдаемой. Разрешённый bounded запуск вне filesystem sandbox прошёл за 36 секунд без этого environment restriction.

## Source integrity and secrets

- source login `Database.ini` SHA-256 остался `A82F8E99D33EADC54DE48F8C7DC424C02BE6E10A0C437A6655C24FF02967E646`;
- source game `Database.ini` SHA-256 остался `705429541BE51687FF3EDC8688C32CE1095E9460FE438838CB6FDC9EF79EB22A`;
- source game hexid SHA-256 остался `7E9F147A7DAD8C83864E0FD51260A17D52BDB23E05EA28F9263BAF9BDB4E6A3D`;
- все canonical login/game SQL hashes проверялись workflow до/после и остались byte-identical;
- password не передавался в CLI, не печатался и не записывался в manifest/Git/ZIP.

## Build note

`ant -q jar` завершал target как `BUILD SUCCESSFUL`, но внутри sandbox JDK 25 печатал тот же close-time `AccessDeniedException` для HikariCP. Финальные `LoginServer.jar`/`GameServer.jar` существуют, совпадают между source dist и runtime по hash и успешно прошли real-stack startup вне sandbox. Full verify намеренно не выполнялся.

## Git

- required parent: `70c03a30a52d729cacdfd24a3944c1eea4b3d92c`;
- branch: `feature/phantom-world`;
- commit subject: `release: add isolated fresh local-play database`;
- commit SHA: см. финальный handoff (самоссылка SHA внутри собственного commit невозможна);
- push: normal non-force, результат и remote equality фиксируются в финальном handoff.

Git-команды использовались, потому что TASK явно требует exact parent, один commit, push и remote equality. Несвязанные modified/untracked пользовательские файлы сохранены и в commit не включаются.

## First manual login

1. Запустить `artifacts\local-play\runtime\CHECK_LOCAL_PLAY.cmd`.
2. Запустить `artifacts\local-play\runtime\START_LOCAL_PLAY.cmd`.
3. В клиенте использовать login `localplayer` и любой новый пароль.
4. Stock `AutoCreateAccounts=True` создаст первый пользовательский аккаунт только при этом ручном входе.
5. Для остановки использовать только `STOP_LOCAL_PLAY.cmd`.
