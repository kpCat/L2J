# Cumulative High Five delivery contract

TASK060, 18.10.2026: текущая authority — [AUTHORITY060](tasks/PHANTOM-LIVING-PROGRESSION-060/AUTHORITY.md). Для связанной карьеры явно разрешены native WALK/Gatekeeper, NPC-снабжение, покупки в offline private stores, offline manufacture у гнома и собственные Pet/BabyPet. Эта адресная поправка заменяет прежний blanket запрет М8–М10 только для перечисленных зависимостей; полнота М8–М10 и масштабирование не объявляются. Защищённые057/058/059 и их release-состояние не меняются. Новые060 результаты требуют текущего server evidence; исторические таблицы ниже сохраняются.

TASK059, 18.10.2026: текущая authority — [AUTHORITY059](tasks/PHANTOM-M3-M7-CAMPAIGN-059/AUTHORITY.md), результаты — [RESULT059](reports/PHANTOM-M3-M7-CAMPAIGN-059.md). Исторические041–058 ниже сохраняются. М1 остаётся принятой базой разработки с открытым technical status и отложенными G4/G6/полной личной приёмкой; новых PASS этих gates нет.

| Этап059 | Server coverage | Личная приёмка |
|---|---|---|
| М2 | SERVER_VERIFIED в058 scope: current selected110/110 и native affected | OWNER_QA_PENDING |
| М3 | SERVER_VERIFIED: native HOLD/RESUME, адресная authority, truthful support, finite RU corpus, память и lifecycle | OWNER_QA_PENDING |
| М4 | PARTIAL: native PvP/PK/clan guards; полный retreat/regroup блокируется неподтверждённым Giran route | OWNER_QA_PENDING |
| М5 | PARTIAL: readiness, support/minions/claims, current Avatar; полный stock25199 остаётся FAIL/ABORTED | OWNER_QA_PENDING |
| М6 | PARTIAL: полный Kamaloka с natural closing; Rift native combat/fee/leave fix; auto-entry/timed/death/logout не закрыты | OWNER_QA_PENDING |
| М7 | PARTIAL: immediate-SP/CAS/races и explicit paid WALK journey проверены; auto-career/20–40–76/subclass/Noblesse открыты | OWNER_QA_PENDING |

Retention059: существующие party/conversation owners и PTY2 сохраняют native authority; `PhantomClassSkillLearningTransaction` сохраняет earned-SP fence и точные расходы; `L2jPhantomLearningJourney` использует существующие Decision/Navigation/Progression без второго AI pulse; `DimensionalRift.partyMemberExited` сохраняет native exit удаляемого участника. Полные FAIL и scope ограничений перечислены в RESULT059. Отдельная поставка059 не заменяет защищённые057/058; runtime ZIP при частичной М7 не создаётся.

TASK058, 16.10.2026: М2 SERVER_VERIFIED в объявленном native server scope, OWNER_QA_PENDING; М3–М7 PARTIAL с конкретными native результатами и открытыми границами. Продолжение разрешено AUTHORITY058; М1/G4/G6 остаются открытыми/отложенными. Финальная отдельная поставка ещё готовится. Полный актуальный перечень, JAR/provenance и история — [RESULT058](reports/PHANTOM-M2-M7-CAMPAIGN-058.md).

М3 TASK058 PARTIAL: ordinary real-led JOIN сохраняет подтверждённое совместное событие с character object ID; social receipt различает новые JVM invitations и точный callback retry. Native RED/GREEN, affected Party10/10/full М2 composition1/1 и ordinary sameDB restart → native delivered recall/изоляция/новый join/идемпотентный retry проверены. Полезные owners/helpers сохранены; весь command/refusal набор М3 ещё открыт. Независимый М4 использует проверенные М2 Party/identity и social-state boundaries, не неподтверждённые команды М3.

М5 TASK058 PARTIAL: raid current-rank refresh, native tank-only melee admission, NPC Aggression и ranged spacing интегрированы в существующий common combat. Focused8/8+runtime5/5+safety4/4; stock RaidBoss25199 native episode имеет actual mage damage/MP и tank агро10.1s, но tank death/незавершённый heal — FAIL. Победа/stock reward/regroup/recovery остаются открытыми, полного raid milestone claim нет. История не заменяется одним PASS helper с прежним слабым критерием.

М6 TASK058 PARTIAL: stock Kamaloka57 entry/guards/600s native combat, все4 живы, без victory/reward/reuse; native Rift entry/fee/room/identity, combat classification открыт. FestivalManager normal shutdown больше не игнорирует interrupt во всех8 wait; actual native signup→обычный shutdown1/1, affected common7/7/PvP4/4. Полный instance/Rift milestone не объявлен; исходные FAIL/invalid inputs сохранены.

Rift TASK058: confirmed stock RiftInvader наследует Monster; knowledge copying adapter теперь сохраняет MONSTER. Native RED→actual room4/21668 MONSTER/common combat/HP2863→0; native knowledge parity22/22. Human death/no EXP оставляют весь сценарий FAIL/PARTIAL, полного timed Rift/автовхода/награды claim нет.

TASK058 current common slice: native pending-teleport dematerialize RED→GREEN1/1 с ordinary human control; PvP bounded walking/current enhanced damage/mixed defense3/3, focused5/5/common7/7; Fighter0 earned20/gear/save/paid1→2/separate field effect/reload1/1, native knowledge22/22 и affected full М2 composition1/1. М7 PARTIAL: durable learning требует сохранённого SP; профессии/subclass/noblesse quests и автономный путь из города не заявлены. Все исходные FAIL остаются в evidence.

TASK058 support addition: существующий progression rule теперь учитывает изученный Cardinal Greater Heal1217, сохраняя Balance Life. Native current1217:101 actual effect/cost1/1 и affected role matrix3/3; stock source/learned/MP/target guards сохранены, product grant отсутствует. Полного raid claim нет.

## Актуальная поправка владельца TASK058 от 16.10.2026

М4 TASK058 PARTIAL: native actual Player damage facts (32 ID/30s, no timer/SQL), current PvP rank и canonical NO_PVP guard сохранены в Player/PlayerStatus/Combat/Party owners. Native3/3 проверили enhanced retaliation и mixed-party defense/evidence revoke; affected М2 support3/3 и common boundary7/7. Один исторический normal teardown FAIL остаётся открытым риском; полного PvP/retreat/clans milestone claim нет. Exact orphan repair action ограничен одним failed058t fixture327 с отсутствующим персонажем, не cumulative/release.

Утверждённый [AUTHORITY058](tasks/PHANTOM-M2-M7-CAMPAIGN-058/AUTHORITY.md) заменяет прежний запрет перехода к М2–М7 до всех gates М1. Исторические OPEN/FAIL и результаты 041–057 сохраняются. Новые этапы различают SERVER_VERIFIED и OWNER_ACCEPTED; OWNER_QA_PENDING не блокирует следующую серверную разработку. Поправка не снимает защиту M1-057-candidate, других сред и ограничения платформы.

```text
M1_TECHNICAL_STATUS=OPEN
M1_DEVELOPMENT_BASE=ACCEPTED_WITH_DEFERRED_GATES
G4=DEFERRED_BY_OWNER_NOT_PROVEN
G6=BLOCKED_PLATFORM_DEFERRED
FULL_OWNER_ACCEPTANCE=DEFERRED_BY_OWNER
FORWARD_WORK_M2_M7=AUTHORIZED
```

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
|058 companion request, invitation composition и ordinary FARM goal takeover (часть М2)|PhantomConversationService/ExecutionService, L2jPhantomConversationExecutionPort, PartyDecision/Coordinator, native PartyInvitationService|companion-request058; companion-composition058; companion-farm-takeover058; semantic/context/codec, native conversation и execution ownership regression|Tracked RU semantic/conversation data; обычный dist; full follow/support/lifecycle и OWNER_QA пока открыты|
|058 real→Phantom invitation consent (часть М2)|PhantomRealPartyInvitationPolicy, PartyCoordinator, native PartyInvitationService|companion-real-invitation058; campaign058-ownership policy contracts; party-server-integration|high-five-real-party-invitation-v1.xml; PhantomSystem composition; real-led follow/support ещё не проверены|
|058 real-led automatic buff и native leave (часть М2)|PartyCoordinator real-led reconcile; existing Tactics/CombatService/L2jCombatBackend/native Player cast|companion-real-support058 actual effect/MP/pin release; party-tactics; post002-support; incoming invitation affected|Existing support/progression catalogs и policy; следование, assist и heal/recharge/res matrix остаются открытыми|
|058 heal/recharge/res role effects (часть М2)|Те же PartyCoordinator/Tactics и native support handlers; current learned ProgressionService|companion-role-matrix058: HP heal, MP recharge, native res request→TEST answer→stand-up, caster MP costs; buff affected|Existing canonical class/skill catalogs; tracked native helper; TEST role preparation не natural career и не проверка под атакой|
|058 lost mixed membership release (часть М2)|PartyCoordinator existing claim/goal CAS/native tactical leases|companion-mixed-lifecycle058 RED/GREEN; real invitation personal-FARM negative; native support affected|Group/generation-scoped lease keys; own core party goal→ABANDONED; unrelated personal goal retained; multi-Phantom handoff/follow ещё открыты|
|058 canonical leader handoff (часть М2)|PartyCoordinator observed leader→existing commitMembership→fresh claims; native PartyInvitationService|companion-leader-handoff058 RED/GREEN: 3 transfers, former-leader rejection, expel/pin/goal release; Party affected|Generation advances and old route cancelled; active cast/attack handoff и multi-Phantom follow ещё не доказаны|
|058 native geometry fixture input (часть М2 проверки)|Existing native GeoEngine/config; exact058t headless environment|campaign058-native-geo1/1; ownership/config/geo bounds6/6; role matrix with actual geometry3/3|Read-only external region17_25 copied only to own fixture; product/bootstrap unchanged; movement/obstacle proof ещё открыт|
|058 native per-follower companion route (часть М2)|PartyCoordinator real-led pulse; existing RouteCoordinator/NavigationService/CombatService ownership|companion-follow058 RED/GREEN actual XYZ/path around obstacle; party-route5/5 generation guards; campaign058-ownership7/7; native role matrix3/3|high-five-party-follow-v1.xml, strict immutable bounds; no new timer/teleport; moving leader/multiple actors/assist/full sequence ещё открыты|
|058 three companions/native walking leader (часть М2 проверки)|Тот же product follow; existing headless Player.create/materialization/owned cleanup|companion-moving-follow058:3 actual followers, human native walk, membership,9 successful computed paths, max sampled step26; single follower affected|Exact058t-only max2 extra fixtures, owner/root/config/catalog recheck; прежние lanes не расширены; assist/reward/full sequence ещё открыты|
|058 last-human logout and same-character rejoin (часть М2)|PartyCoordinator reconcile→canonical backend self-leave→existing scoped mixed release|companion-departure058 leave baseline PASS; logout causal RED/GREEN with3 actors; same character canonical rejoin; route5/5/tactics2/2/native Party affected|Only formerly mixed group with no remaining REAL; latest group/generation and current native roster guarded; native all-Phantom autonomous groups retained|
|058 optional native waypoint fixture proof|Existing PhantomTravelGeoProbe→PhantomGeoValidationRules runtime route contract|--waypoints on6 prior inputs: unchanged summary/runtime SHA256,7 native points for verified N/S path; no DB connection|Opt-in extra TSV only, default CLI/height mode preserved; ordinary tracked test helper, not a runtime controller|
|058 role-aware native assist and completed actions (часть М2)|PartyCoordinator exact owned CombatService sessions; current Progression capabilities; native target preflight; existing common executor|companion-assist058 final1/1:3roles nativewalk,2stock kills, actual EXP twice, native damage/MP/arrows,6sessions; focused action/preflight RED2/3→GREEN3/3; native support3/3/Party10/10 affected|No new AI/timer/reward path; native cast/attack completes before next command; friendly/invalid target cannot churn follow; tank aggro/range/advanced skills/full speech composition/current JAR ещё открыты|
|058 current learned NPC aggression and live party heal (часть М2)|Existing CombatService owner→L2jCombatBackend native CAST; actual learned AGGRESSION effect; Party tank basic melee via existing content session|companion-npc-aggro058 causal RED→GREEN1/1: native28:49 reuse, hate0→13754, target held26s, native heal/MP, full stock NPC kill/earned EXP; focused safety/owner5/5|Strict normal Monster only, same-party victim guard, no hate edits/GM grants; current learned rank rather than catalog level; Raid/ranged spacing/full composition ещё открыты|
|058 current learned offense/support rank (часть М2/SKILLS)|Shared CombatCapabilityResolver and ProgressionCapabilityEvaluator→native exact known skill; existing party support owner|learned-skills058 RED0/2→GREEN2/2 actual1339:101 damage/cost and1013:101 recharge/effect/cost; current class requery; focused idle refresh6/6; capability-runtime40/40|Catalog remains minimum capability authority, actual rank/cost/reuse/items checked; normal session refresh only between native actions; gear/subclass/restart coverage ещё открыто|
|058 bounded mage/bow native spacing and current bow gear (часть М2)|Same normal CombatService actor lease→native MOVE_TO/CAST/ATTACK; actual skill/weapon range and GeoEngine|companion-ranged-spacing058 causal close-range RED→native movement/actions/rewards GREEN; current bow removal/re-equip; focused busy/moving/low-MP owner contract|No parallel writer/timer/teleport;16 candidates/4 moves/5s, loaded geometry/LOS; exact target+destination cleanup; ordinary NPC scope, full composition/restart ещё открыты|
|058 isolated packaging/runtime profile|Existing Build-Package/Runtime/DB preflight/normal shutdown; exact cumulative/release owner bindings|PowerShell7/7 + Java7/7 wrong-root/schema/role/owner/port negatives before JDBC/process actions; Campaign058 builder ValidateOnly|Exact058a roots/schemas/ports;32 population/8 ACTIVE/16 materialized; stock1x rates; Synthetic OFF; TestAdmin,kpCat autoattach; clean JAR/runtime smoke остаётся обязательным|
|058 persistent native infrastructure and one-time stock bootstrap|Separate PersistentLane→StrictSqlScriptRunner/native infrastructure; mutable fixture initializer explicitly refuses058a|Ownership RED8/9→GREEN10/10, fixture rejection before pool; one stock121-script install/124 tables, exact fresh empty-data verification and own registration1|No reset/reimport/users/grants; stock registration2 retained, registration-only continuation after wrong empty-table expectation; full native world setup/current JAR ещё требуется|
|058 persistent declared TEST scene|Actual ordinary GameServer.jar; native PopulationStore createShell/advanceCreation/READY and Player persistence; separate persistent lane|32 canonical level1 births, six named TEST profiles, five declared class80/learned/stock gear fixture preparations, non-GM TestAdmin10; frozen seed/plan and actual identity/position receipt|No natural M7 claim, no in-episode grants; old query-unavailable setup failed before actor creation and continued only with empty data/same plan; actual game smoke/composition/restart остаются открытыми|
|058 full companion native composition (часть М2)|Existing ingress/conversation/execution/DecisionEngine→canonical Party→same navigation/combat owners|companion-full-composition0581/1 on actual ordinary GameServer.jar, exact source location asserted; addressed reply SENT, explicit TEST accept, human leadership,3 roles/obstacle path/2 stock rewards/native partychat delivery4|Existing loopback TEST GameClient helper reused, no UI/login claim or Synthetic guard changes; support role matrix separate; earned FARM/resume and full persistence still required|
|058 ordinary persistence witness|Existing Runtime Start/Status/normal Stop; separate read-only PersistentSnapshot exact058a guard|Ordinary sameDB restart/save; immutable identities33/profiles32 and declared TEST state7/skills378/items68 exact hashes preserved; native pools closed and JVM absent|Initial overbroad equality FAIL retained: other population progressed/received stock items; no fixture reset; Synthetic START guard and OS ACL refusal retained, no privileges/guards changed; World presence not inferred from SQL|
|058 earned FARM/party/same-goal resume (часть М2)|Existing VisibleAutoPlay/AutoUse native policies + DecisionEngine goal + canonical incoming invitation/PartyCoordinator background fence|companion-farm-resume0581/1 on ordinary JAR:4 full stock21111, EXP before party3075966164→3075974800, party3075984882, same goal58005825:1 resumed→3075993518; both old registrations false in party|Canonical mapped stock territory/anchor/loaded geometry; invite during actual native attack; no in-episode grants or rewards; source input failures and later cleanup FAIL retained; only post-measurement TEST earned adena cleanup adapted, conservation guard unchanged|
|058 silent conversation cooldown and active handoff (часть М2)|Existing ConversationService null-response persistence branch; native Party transfer/reconcile; actual single-JAR provenance for Player/Combat/Party/Conversation|Focused native RED extra outbound plan→GREEN2/2; full native composition1/1 with live cast handoff, all3 member generations refreshed, two stock rewards and exactly one elected PARTY query/SENT reply; immediate repeat silent; affected native conversation5/5 and pure5/5|Cooldown/session/recent observation retained without outbound/debug message; no new timer/schema/controller; Game37DEF4FEEC355FAB1A0179C62DEFF7A80F2E0F5A885D59B7BCED9DD6C1AC1F8C; old replaced conversation-goal UNCERTAIN1 retained/no replay;9 sessions match declared generation changes|
|058 native M2 boundary controls|Existing canonical PartyInvitationService, native subclass switch/materialization, support effect/readiness kernel; exact058t fixture account/seed guards|Invitation2/2 actual9-member Party rejects10th; refusal/expiry/replay/stale/foreign caller no membership; learned4/4 includes actual subclass9→main94→native reload1339:101; Song349 actual effect/cost/rebuff120s and wrong/dead/instance/MP/reuse/range negatives1/1|Additional8 actors only seed58005827/058t, original companion2 limit retained; no real-login or natural subclass/M7 claim. Old1356 helper failures/one cleanup incident retained; final349 episode uses verified native input, natural cast/reuse waits, no abort or cleanup warning|

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

## Адресная поставка TASK060

Утверждённый пакет PHANTOM-LIVING-PROGRESSION-060 разрешает существующую058a, exact058t и новый `D:\PhantomWorld\Living-060-candidate`/`l2jmobiush5_phantom_release_060a`. Финальная БД — один consistent stopped export058a; повторный bootstrap/reset/reimport058a запрещён. Самостоятельные START/STATUS/штатный STOP, current regression и sameDB restart обязательны; итог STOPPED. Login2106 только при свободном endpoint и остановленных защищённых057/058/059, иначе2140; Game7840/control9140. Защищённые candidate/БД/процессы и клиент не меняются.

Конкретное расширение scope: NPC shots, offline private buy/manufacture, штатные GK и собственные Pet/BabyPet как зависимости карьеры; без массовой торговой сети. Точное покрытие, классы, сценарии и не закрытые требования указываются в одном RESULT060 и матрице060. М1/G4/G6 остаются deferred; полная М7 не следует из selected PASS. По прямому решению владельца060 review ZIP не создаётся. Сохраняются preimages, реальные FAIL и quarantine; Git — exact-path содержательные commits и normal non-force push по AUTHORITY060§7.

## Адресная поправка TASK061 — 2026-10-19

Утверждённый PHANTOM-WORLD-CAREER-061 разрешает продолжить exact058t/058a и создать только `D:\PhantomWorld\World-Career-061-candidate`/`l2jmobiush5_phantom_release_061a`: один consistent stopped export058a, без reset/bootstrap/reimport058a и без061t/061a test-схем. Login2106 при свободном порте и остановленных защищённых057–060, иначе2141; Game7841/control9141. Обязательны обычный Main, actual жизнь и native самостоятельная профессия, текущие START/STATUS/штатный STOP и sameDB resume; техническое присутствие не переносится на OWNER_QA_PENDING. Исходные111caseIDs и затронутые М2/М3/supply/SP/lifecycle/memory сохраняются. Один RESULT061/матрица, конечные058a и061 STOPPED, exact-path commits и normal non-force push/HEAD=remote; review ZIP отсутствует. CORE061=PASS лишь по всем обязательным строкам ACCEPTANCE061. М1/G4/G6 deferred и полный М7 не выводятся из одного пути. Защищённые релизы/БД/персонажи/процессы, клиент, общая MariaDB и старые orphan/quarantine остаются неприкосновенными.
