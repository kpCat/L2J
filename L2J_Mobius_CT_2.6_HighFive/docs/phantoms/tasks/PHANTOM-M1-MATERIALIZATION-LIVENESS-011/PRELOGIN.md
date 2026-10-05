# Observe011 — ручной вход REQUIRED, M1 OPEN

CODE_SHA=8457b90723e3c8ff6b419080bb2f84bfd80d6638
ENGINEERING_PUSH=PASS
ECOLOGY_LIVENESS_TEST=18/18
AFFECTED_REGRESSION=6/6
BUILD=PASS
USER_MANUAL_LOGIN=REQUIRED
REAL_LOGIN_OWNER=REQUIRED
PILOT_AUTOATTACH=REQUIRED
ARM_CODE_USED=false
MATERIALIZATION_90S=NOT_OBSERVED
AUTOPLAY_5_CYCLES=NOT_OBSERVED
M1=OPEN

Fresh clone `l2jmobiush5_localplay_observe011` создан из read-only single-transaction
export PLAY `l2jmobiush5_localplay3` на127.0.0.1:3308. До CREATE имени не было.
Dump проверен на отсутствие CREATE/DROP DATABASE и USE. Triggers/routines/events
не экспортируются. CREATE/import только в новом clone. Characters10002,
profiles10000, components50000; TestAdmin268492939 online0 до startup.
Пароль только в process environment с finally cleanup, persistent credential file
не создавался. Все304 original runtime file hashes после подготовки совпали.

Private runtime:
`C:/Users/ZBook/L2J_Mobius/L2J_Mobius_CT_2.6_HighFive/.phantom-local/observe011/runtime`.
Actual runtime JAR config loaders: enabled=true, Population1280, Active8,
MaxMaterialized8, MaxScheduled10000; Pilot=True, AutoAttach=True,
allowlist TestAdmin, Synthetic=False. EffectiveConfig validator exit0.
Runtime JAR hashes совпадают с собранными experiment worktree JAR:
GameServer `8CE3EF5FDB4478657DBFEB41D56BCE83EDCEC1EDEB684FA8109A3444F991E92C`;
LoginServer `CC5001A4E55EFE7EDCD0E97743EBEC955FADD257A9EF554C4FDFD8AEB2565A7B`.

Login PID17672, startTicks639268223896438280.
Game PID14472, startTicks639268223913994953.
RuntimeId3b35fb3340b8bb48f8c843f5621e29a5e23d3c8b4c9ed404b862f98ecf5e48b8.
Ownership verified; ports2106/9014 Login17672,7777 Game14472. CONFIG PASS.
Pilot OFF, runActive=false, actorObjectId=null; arm.properties отсутствует.
Phantom World startup section21:40:26, mailbox enabled21:40:42,
Server loaded57s / Registered on login as Server1 Bartz21:40:48
(Europe/Chisinau, 2026-10-05). error0.log bytes0.

## Prelogin incidents

Startup завершён, но нельзя заявлять отсутствие Phantom runtime ошибок.
Java logger пишет в captured stderr. Уже до human login наблюдаются bounded cleanup
incidents AFTER_IDENTITY_CLAIM, admittedActions0:

- object268485428,268486429,268486622,268486696,268487378:
  `Historical native context attestation did not complete under the exact claim:
  NATIVE_CONTEXT_REQUIRED;refresh=CAPTURE_INVENTORY_OR_AUTOGET`.
  Stack: BackgroundService.afterPlayerLoad1540 -> historical.refreshCanonicalBaseline864
  -> begin237 -> ecology.beginNextWindow1073.
- object268494662,268494936: `Loaded Player differs from committed background state`,
  maxHp/maxMp/maxCp mismatch, refresh stateDEAD/hashMatch=false; afterPlayerLoad1532.

Эти boundary не исправлялись. Prelogin acquisition attempts не считаются настоящей
natural world materialization и не заменяют90s gate после IN_GAME/REAL_LOGIN/ARMED_IDLE.
После входа максимум90s на worldPresent=true/objectId>0/epoch>0, иначе точный boundary
и STOP. При успехе только passive10–15m и пять farm cycles, без gameplay fixes.

## Cleanup и команды

Пользователь явно разрешил остановить previous observe010 pair5956/19584.
Перед stop проверены exact runtime markers/startTicks/ownership/ports;
штатный Stop-LocalPlay.ps1 завершил оба PID, глобального taskkill не было.
Порты переданы новой паре. Observe011 остаётся RUNNING для ручного gate.

Private task011 tooling переиспользует Prepare-Runtime.ps1 и EffectiveConfig.java
task010. В preparation script меняются только private root, clone011, ephemeral
password delivery; runtime product configs соответствуют CONFIG_OVERRIDES.tsv.
Ошибочно изменённое manifest account metadata возвращено к original validated value;
QoL AllowedAccounts/product config не менялись. После этого CONFIG PASS.

Git-команды разрешены пользователем/GIT.md task011. Engineering точные команды
перечислены в ENGINEERING.md; additional prelogin publication:
git diff --check; git status --short --untracked-files=no;
git add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-MATERIALIZATION-LIVENESS-011/PRELOGIN.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-MATERIALIZATION-LIVENESS-011/CONFIG_OVERRIDES.tsv;
git diff --cached --check; git diff --cached --name-only;
git commit -m 'docs(phantoms): record observe011 prelogin gate';
git push origin HEAD:refs/heads/experiment/m1-candidate007-observe008.

mojibake-маркеры в изменённых файлах проверены: 0.
escaped Cyrillic в изменённых файлах проверены: 0.

Сервер готов. Войди вручную TestAdmin и напиши "в игре".
