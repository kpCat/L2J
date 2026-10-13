# Cumulative High Five delivery contract

Этот контракт обязателен для каждой следующей задачи М1–М7. Принятые функции, конфиги, данные и полезные QoL сохраняются в обычной поставке. Private ops, cached JAR, debugger и forensic agent не могут быть единственным носителем игровой функции.

041 проверяет М1 и delivery. М1 остаётся OPEN до всех серверных gates и личного принятия. М2–М7 автоматически не запускаются. Классы будущего этапа не означают принятый результат. Zariche — DEFERRED_BY_USER, без расследования041.

Roadmap: [MILESTONES038](tasks/PHANTOM-M1-ACTION-LOOP-038/MILESTONES.md). Ранний контекст: [live-world ROADMAP](live-world/ROADMAP.md). При конфликте старых полномочий действует текущий утверждённый task; защита040a не снимается.

| Этап | Требуемый результат | Статус041 |
|---|---|---|
| М1 | Native materialize/move/farm/loot/recovery/death/soft exit/background/return; save/restart/crash; real client | В работе до gates и принятия |
| М2 | Companion: party, follow/assist/heal/buff/recharge/res, partychat | Будущий отдельный этап |
| М3 | Речь, характер, настроение, память, отношения, русский L2-сленг | Будущий отдельный этап |
| М4 | PvP/PK, группы/кланы, focus/defense/retreat, риск/месть | Будущий отдельный этап |
| М5 | Рейды, роли, сбор/regroup/death recovery | Будущий отдельный этап |
| М6 | Instances/Rift/Kamaloka: REAL+Phantom сохраняют группу/instance и штатный вход/выход | Будущий отдельный этап |
| М7 | Progression20/40/76, навыки, subclass/noblesse, долгая жизнь; полная cumulative поставка | Будущий отдельный этап |

М8–М10 roadmap не входят в автоматическое исполнение041/М7. Существующая экономика и QoL сохраняются.

## Retention map035–041

Production paths относительны java/org/l2jmobius/gameserver; Phantom suites — test/java/org/l2jmobius/tests/phantoms. Таблица перечисляет механизмы, не объявляет текущие041 проверки PASS.

| Механизм | Production owner | Проверка | Config/data/dist |
|---|---|---|---|
|035 full/overfull snapshot, stock inventory250|phantoms/background/L2jPhantomBackgroundAuthority, PhantomBackgroundState/Model/Transaction|PhantomInventoryCapture035Suite; native→SQL full save|Custom/PhantomPlayers.ini; SQL components; свежий GameServer.jar|
|036 earned descendants/drain, epoch/identity, callbacks|phantoms/player/PhantomNativeWorkScope, PhantomMaterializationService; model/actor/PlayerNativeWork|PhantomNativeCastDrain036Suite, PhantomCheckpointDrain025Suite, lifecycle/all-birth stop|Штатные native Player/ThreadPool; GameServer.jar; без нового WAL/лимитов|
|037 visible/background и recovery|phantoms/background/PhantomHistoricalBackgroundService, PhantomVisibleFarmTravel/AutoPlay|PhantomVisibleContinuity032Suite focus continuity037; PhantomFarmProjection030Suite; natural lifecycle|Tracked topology/knowledge/progression; геоданные явный внешний input|
|038 action/approach/target/continuation/capability|phantoms/actions/PhantomEngagementPlanner/Controller; existing navigation backend|PhantomActionLoop038Suite; две полные natural группы|Existing capability/catalog/config; оба свежих JAR и полный datapack|
|039 actual commit/CAS/earned handoff/recovery|phantoms/background/PhantomBackgroundTransaction/Service|PhantomM1Campaign039Suite; оба actual crash+recovery/idempotence|Optional witness выключен без subscriber; существующие SQL/data; не persistence protocol|
|040 AutoUse/caster, native recovery, passive focus/reconnect|PhantomEngagementPlanner; PhantomHistoricalBackgroundService; localplay/LocalPlayPilotService, LocalPlayM1Observation|PhantomM1Closeout040Suite, PhantomObserverHandoff040Suite, caster040; реальный gate|Custom/LocalPlayPilot.ini; runtime ownership manifest; Synthetic OFF release|
|041 goal owning commit lineage, guarded fixtures, ordinary delivery|phantoms/profile/PhantomProfileRepository; tools/phantom-runtime; LocalPlay-Pilot publication helper|PhantomM1Closure041Suite; rollback/observer failure negatives; clean package/Start-Status-Stop-Start|Tracked configs/datapack/SQL/libs/tools; секреты локально; actual final gates обязательны|
|Личные skills/cross-class/crystallization/SevenSigns/effects/party support|qol/PersonalCharacterQoLService|QoLPersonalSkills/Crystallization/SevenSignsAccess/EffectDuration/PartySupport suites; прежние личные gates сохраняются|Custom/PersonalCharacterQoL.ini; AllowedAccounts; полный data/HTML/skills|
|Premium/shop, quest relief/noblesse/progression|qol/PersonalPremiumQoLService, PersonalProgressionQoLService|QoLShop/LevelGap/EconomyProgressionClosure suites|PersonalPremiumQoL.ini, PersonalProgressionQoL.ini; полный datapack; rates не подменяются|
|Conversation/social/party/economy assets|Существующие Phantom semantic/social/affiliation/market owners|Existing targeted regression затронутой функции|Весь tracked dist/game/data/configs; не выключать молча и не объявлять будущий milestone принятым|

## Clean build и самостоятельная поставка

JDK25, Ant не ниже1.8.2 (041:1.10.17), Windows PowerShell5.1; локальная MariaDB/MySQL как prerequisite. Dependencies tracked в dist/libs; Game/Login JAR создаются заново jar target. Существующие лицензированные .l2j геоданные не вGit: explicit GeoDataRoot, SHA256, независимая копия. Hermetic build не заявляется.

Из High Five module после exact-path commit source/tools/config:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools\phantom-runtime\Build-Package.ps1 -OutputRoot C:\Users\ZBook\PhantomWorld\M1-041 -StageRoot .phantom-local\release041\clean-final -JavaHome 'C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot' -Ant C:\Tools\apache-ant-1.10.17\bin\ant.bat -GeoDataRoot '<существующие разрешённые геоданные>' -DatabaseName l2jmobiush5_phantom_test_041final -DatabaseConfigRoot '<локальный041final config input>' -GamePort 7792 -ClientLoginPort 2122 -ControlPort 9032 -ObserverObjectId 268492939
```

Builder делает git archive HEAD только High Five в новом staging, ant jar compile-tests на tracked input. Не выполняет default cleanup и не импортирует private classes/cached JAR. Оба output paths должны отсутствовать. БД готовится отдельно только в разрешённом новом namespace; builder не создаёт/лечит/пересоздаёт schemas.

START/STOP/STATUS.cmd вызывают tools/Runtime.ps1 относительно своего каталога, CWD независим. Start: namespace/JDBC/code/JAR/ownership/ports, затем Login→Game; повторный Start идемпотентен. Stop: recorded incarnation и actual JVM runtime/datapack/schema; штатный Shutdown, без force fallback. tools/runtime-shutdown.jar — stock operator call только при STOP, не startup forensic/fault agent. Status read-only: actual freshness отдельно от ручной приёмки. Codex/.codex/ops для работы не нужны.

Ключи/конфигурирование: [START_HERE_RU.md](../../dist/START_HERE_RU.md). Source change требует build; config change — штатного restart. Новые041 порты фактически проверяются перед запуском. Стандартные2106/7777/9014 и040a защищены.

BUILD_INFO фиксирует source/tracked archive/JAR/helper/geo hashes. Local Database.ini/runtime.local.ini/registration identity имеют private ACL; секреты не вGit/review/runtime ZIP. В ZIP template и provisioning-инструкция; настроенный локальный release работает без Codex. Update не перезаписывает слепо конфиги/БД.

## Каждая следующая задача М2–М7

Новая принятая функция добавляется в retention map: actual owner→focused regression→config/data/SQL→ordinary dist. Cumulative clean compile/package и проверка затронутых прежних функций обязательны. Private-only реализация не принимается. Exact-path commit и normal non-force publication; история/gates не переписываются. Deferred/unverified функции перечисляются честно.

После принятия М7 выполнить, а не снова обещать:

1. Единый descendant принятых М1–М7, full clean compile/package, config/data/SQL, regression и интеграционная проверка.
2. Версионированный готовый release-каталог и отдельный runtime ZIP: свежие Game/Login/libs/datapack/configs/tools, русская инструкция, provenance/known issues. Без secrets/testGM/private raw/fault-agent.
3. На машине пользователя самостоятельные Start/Status/штатный Stop, конфигурирование, preserve-progress restart и безопасное update с сохранением своих БД/настроек; prerequisites явно.
4. Реально принятые «плюшки» чата/пати/экономики/карьеры; классы/флаги не игровой результат. Нереализованное не READY.
5. Окончательный каталог/стандартные порты согласовать отдельно. Успех041/М7 не снимает защиту040a.

Личный041 final gate REQUIRED до явного решения пользователя. M1=CLOSED/READY_FOR_M2=true до него запрещены; автоматический следующий task запрещён.
