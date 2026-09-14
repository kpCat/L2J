# L2-QOL-005 evidence report

Дата: 2026-09-14

Branch: `feature/phantom-world`

Required parent: `58672daf0bcd008c7c8b9c022c40671873ad89ce`

Результат: **SUCCESS**

## Scope и read-first

До правок полностью прочитан task package L2-QOL-005 (`TASK.md`, `ACCEPTANCE.md`, `CONTEXT.md`, `RIFT_PARTY_POLICY.md`, `SOURCE_AUDIT.md`, `TEST_PLAN.md`, manifest и launcher), корневой `README.md`, module `readme.txt`, Personal QoL status/roadmap/operator guide, отчёт QOL-004, текущие config/service/test/build owners и все найденные Seven Signs, Catacomb/Necropolis, Rift и Mammon gates. Отдельных on-disk `AGENTS.md`, code-map и pattern-файлов не найдено; применены инструкции из task prompt.

Переиспользованы существующие `PersonalCharacterQoLConfig`, `PersonalCharacterQoLService`, strict shipped-OFF/legacy-default parsing, real-Player allowlist и headless-session exclusion. Production edit ограничен player-specific admission/ejection predicates и personal-only ссылкой из исходной страницы Dawn/Dusk Priest в штатный `HuntingGroundsTeleport`. Seven Signs state, Rift engine и combat spawn owners не заменялись.

Bounded exception к обычному лимиту 8–10 файлов необходим из-за обязательного полного census: один central policy подключён к нескольким уже существующим раздельным входам, relog/ejection owners и двум симметричным priest renderers. Это одна subsystem и один product outcome; broad refactor отсутствует.

## Реализация и границы

- `EnablePersonalSevenSignsAccess=False` поставляется выключенным; отсутствие ключа в legacy QOL-004 config также означает `False`.
- Допуск получает только allowlisted real `Player` при включённых master и subfeature. Empty/nonmatching allowlist, обычный Player и headless Phantom остаются stock.
- Central policy разрешает только personal регистрацию/cabal/winner predicate. Он не вызывает Seven Signs mutation API и не меняет выбранный seal, contributions, score, festival или winner.
- Для Catacomb/Necropolis сохранены глобальные winner и owner нужного seal. Personal Player получает исходную hunting-ground ссылку во всех периодах, проходит конечный `DungeonGatekeeper`, не выбрасывается period task или relog проверкой только из-за cabal.
- Summon Friend и wedding destination оценивают именно перемещаемого Player; eligibility одного party member не распространяется на другого.
- Mammon Merchant/Blacksmith остаются глобально spawned только штатным `spawnSevenSignsNPC()` при native period/winner/seal условиях. Если NPC существует, personal Player может пройти только его player-specific winner/cabal check; multisell, inventory и Ancient Adena economy не менялись.
- Rift admission, first room, timed/manual jumps, spawn cadence, respawn, room occupancy, party/leader/min-size/capacity/fragments, timers, cleanup и ejection не имеют Seven Signs period/cabal dependency. Поэтому production Rift code не изменён.
- Feature OFF возвращает точные исходные boolean predicates; stock Seven Signs spawn lifecycle остаётся владельцем special/event NPC.

## Полный gate и spawn census

| gate_id | feature | owner/symbol | trigger | stock predicate | stock deny/eject | personal bypass | retained conditions | test evidence | classification |
|---|---|---|---|---|---|---|---|---|---|
| CAT-01 | priest route | `DawnPriest.showChatWindow` | talk | page varies by period/player cabal/winner/seal | several stock pages omit hunting link | personal-only link appended to selected stock page | all stock page actions and state remain | source contract + four-period matrix | `CHANGE_REQUIRED` |
| CAT-02 | priest route | `DuskPriest.showChatWindow` | talk | symmetric Dusk page selection | several stock pages omit hunting link | same bounded personal link | all stock page actions and state remain | source contract + four-period matrix | `CHANGE_REQUIRED` |
| CAT-03 | route list | `HuntingGroundsTeleport.onTalk` | priest script bypass | player cabal must be non-null | `dawn_tele-no`/`dusk_tele-no` | personal registration predicate | native destination HTML; actual seal discount uses real cabal/seal | source contract + identity/policy cases | `CHANGE_REQUIRED` |
| CAT-04 | Necropolis entry | `DungeonGatekeeper.onBypassFeedback(necro)` | destination bypass | registered; during validation winner and Avarice owner | no teleport/stock message | personal player predicate only | global winner + Avarice owner, teleporter holder | source contract + four-period matrix | `CHANGE_REQUIRED` |
| CAT-05 | Catacomb entry | `DungeonGatekeeper.onBypassFeedback(cata)` | destination bypass | registered; during validation winner and Gnosis owner | no teleport/stock message | personal player predicate only | global winner + Gnosis owner, teleporter holder | source contract + four-period matrix | `CHANGE_REQUIRED` |
| CAT-06 | inner Avarice room | `GatekeeperSpirit.onAdvEvent` | spirit teleport | global winner/Avarice owner + player cabal | deny page | personal player predicate only | global winner and Avarice owner | exact source wiring | `CHANGE_REQUIRED` |
| CAT-07 | Summon Friend | `CallPc.canStart` | target summoned into 7s dungeon | target registered/winner cabal | summoning blocked | target evaluated independently | all other summon restrictions/costs | source contract | `CHANGE_REQUIRED` |
| CAT-08 | wedding teleport | `Wedding.teleport` | active Player moves to partner | active Player registered/winner cabal | teleport denied | active Player evaluated independently | all wedding/Rift/zone/combat restrictions | source contract | `CHANGE_REQUIRED` |
| CAT-09 | delayed ejection | `SevenSigns.teleLosingCabalFromDungeons` | period transition | current dungeon Player has required registration/winner | town teleport, flag clear | eligible personal Player skipped | GM and exact stock path for everyone else | source contract + mutation snapshot | `CHANGE_REQUIRED` |
| CAT-10 | relog/reconnect | `Player.onPlayerEnter` | character enters world in 7s dungeon | registration or current winner by phase | town teleport, flag clear | central personal predicate | stock GM and nonpersonal behavior | source contract + four-period continued matrix | `CHANGE_REQUIRED` |
| CAT-11 | Oracle destination/return | `OracleTeleport` | town/oracle/temple actions | route has no additional cabal gate | native quest/item/level/Adena outcomes | none | native state, fees, return and dungeon flag | audited source | `NO_CHANGE_REQUIRED_ALREADY_COMPATIBLE` |
| SPAWN-CAT-01 | normal population load | 14 files under `data/spawns/Catacombs`; `SpawnData.load` | server spawn initialization | every list enabled; generic `data/spawns` loader | none by Seven Signs period | none | 1,716 normal attackable declarations | all-file census in periods 0/1/2/3 | `NO_CHANGE_REQUIRED_ALREADY_COMPATIBLE` |
| SPAWN-CAT-02 | normal respawn | `Spawn.init/decreaseCount`; `RespawnTaskManager` | initial spawn and death/decay | positive per-entry respawn delay | respawn only if disabled/count full | none | stock 122–146s data and native scheduling | representative actual spawn/decay/respawn + all-entry check | `NO_CHANGE_REQUIRED_ALREADY_COMPATIBLE` |
| SPAWN-CAT-03 | special/event NPC | `SevenSigns.spawnSevenSignsNPC` | Seven Signs period change/startup | native period, winner and seal predicates | special NPC unspawned outside phase | none | Mammon, preacher/orator, crests and event NPC remain stock | source negative assertions | `OUT_OF_SCOPE_GLOBAL_WORLD_STATE` |
| RIFT-01 | waiting-room route | `OracleTeleport` `Dimensional`/`zigurratDimensional` | oracle/ziggurat event | no cabal/period predicate | native level/quest/fragment/Adena handling | none | all native costs and quest state | source dependency-absence contract | `NO_CHANGE_REQUIRED_ALREADY_COMPATIBLE` |
| RIFT-02 | Rift poster/teleporter | `OracleTeleport.onTalk` | talk IDs 31494–31507 | native level, quest count, fragment presence | stock HTML | none | fragment remains item 7079 | actual focused prerequisites | `NO_CHANGE_REQUIRED_ALREADY_COMPATIBLE` |
| RIFT-03 | bypass routing | `Rift.onCommand` | enter/change/exit | delegates to native manager/session | cheat handler outside session | none | native command ownership | source contract | `NO_CHANGE_REQUIRED_ALREADY_COMPATIBLE` |
| RIFT-04 | session start | `DimensionalRiftManager.start` | `enterrift` | party, leader, not already in Rift, min size, capacity, every member waiting, every member fragments | stock pages/no session | none | item 7079 native amount and debit for every member | real two-Player start + negative controls | `NO_CHANGE_REQUIRED_ALREADY_COMPATIBLE` |
| RIFT-05 | first combat room | `DimensionalRift` constructor/`createSpawnTimer` | valid start | free non-waiting room selected | no session if start failed | none | native random/boss room, initial timer | actual first-room full population | `NO_CHANGE_REQUIRED_ALREADY_COMPATIBLE` |
| RIFT-06 | combat spawn data | `DimensionalRiftManager.loadSpawns`; `DimensionalRift.xml` | manager initialization | type/room/mob/count/delay only; no Seven Signs phase | malformed data rejected/logged | none | native delay/count/locations | dependency-absence source contract | `NO_CHANGE_REQUIRED_ALREADY_COMPATIBLE` |
| RIFT-07 | room spawn/respawn | `DimensionalRiftRoom.spawn/unspawn` | room timer/leave | `doSpawn(false)` + `startRespawn`; leave stops/deletes | no spawn before timer | none | native cadence and respawn delay | actual first/subsequent populations, respawn-enabled assertion | `NO_CHANGE_REQUIRED_ALREADY_COMPATIBLE` |
| RIFT-08 | timed jump/end | `DimensionalRift.createTeleporterTimer` | room timer | jump count and remaining rooms | waiting-room ejection/end | none | timing, jump cap, room cleanup | source contract | `NO_CHANGE_REQUIRED_ALREADY_COMPATIBLE` |
| RIFT-09 | manual jump | `DimensionalRift.manualTeleport` | `changeriftroom` | active session, leader, max jumps | stock deny page/no jump | none | room unspawn, occupancy, timer reset | real nonleader denial + leader next-room population | `NO_CHANGE_REQUIRED_ALREADY_COMPATIBLE` |
| RIFT-10 | manual exit | `DimensionalRift.manualExitRift` | `exitrift` | active session and leader | nonleader denied | none | all members waiting, room/session cleanup | source contract | `NO_CHANGE_REQUIRED_ALREADY_COMPATIBLE` |
| RIFT-11 | low-member/death exit | `partyMemberExited`/`usedTeleport` | party loss or revive teleport | remaining active members below min | waiting-room return + `killRift` | none | min size, quest cleanup, room unspawn | actual stock two-member cleanup | `NO_CHANGE_REQUIRED_ALREADY_COMPATIBLE` |
| RIFT-12 | party packet restrictions | `RequestOustPartyMember`/`RequestWithDrawalParty` | kick/leave packet | cannot leave/kick while active and not revived in waiting room | stock message | none | native party/session consistency | audited source | `NO_CHANGE_REQUIRED_ALREADY_COMPATIBLE` |
| RIFT-13 | Party callback | `Party.removePartyMember` | native disband/removal | calls active Rift owner | session owner handles low count | none | native party ordering | actual cleanup case | `NO_CHANGE_REQUIRED_ALREADY_COMPATIBLE` |
| RIFT-14 | relog safety | `EnterWorld` | reconnect inside combat Rift zone | active room location check | return to waiting room | none | no synthetic session recovery/cabal | source contract | `NO_CHANGE_REQUIRED_ALREADY_COMPATIBLE` |
| MAMMON-01 | special spawn | `SevenSigns.spawnSevenSignsNPC` | startup/period transition | validation period + global winner equals Avarice/Gnosis owner | no Merchant/Blacksmith spawn | none | exact global lifecycle | source negative assertions | `OUT_OF_SCOPE_GLOBAL_WORLD_STATE` |
| MAMMON-02 | Merchant access | `Npc.showChatWindow`, ID 31113 | interact | global winner/Avarice owner + player winner cabal | stock denial/message | personal player predicate only | NPC must already exist; economy unchanged | four bounded branches asserted | `CHANGE_REQUIRED` |
| MAMMON-03 | Blacksmith access | `Npc.showChatWindow`, ID 31126 | interact | global winner/Gnosis owner + player winner cabal | stock denial/message | personal player predicate only | NPC must already exist; economy unchanged | four bounded branches asserted | `CHANGE_REQUIRED` |
| MAMMON-04 | trade execution | generic Link/Multisell owners | HTML bypass | native item/currency/list rules | stock denial/debit rules | none | Ancient Adena, inventory and multisell untouched | no service economy/spawn dependency | `NO_CHANGE_REQUIRED_ALREADY_COMPATIBLE` |
| FESTIVAL-01 | Festival combat/event state | `SevenSignsFestival` | festival lifecycle | separate native cabal/event state | stock | none | scores, rewards and festival spawns untouched | mutation snapshot/source audit | `OUT_OF_SCOPE_GLOBAL_WORLD_STATE` |

## Period/playability matrix

| Seven Signs period | personal entry route | continued/relog policy | Cat/Nec normal combat population | Rift first/jump population |
|---|---|---|---|---|
| `PERIOD_COMP_RECRUITING` (0) | personal priest link + registration exception | personal registration exception | same 14 enabled generic lists; normal respawn | independent of period |
| `PERIOD_COMPETITION` (1) | registration exception | personal registration exception | same 1,716 attackable declarations; normal respawn | independent of period |
| `PERIOD_COMP_RESULTS` (2) | registration exception | personal winner-ejection exception | same generic population; actual representative respawn | independent of period |
| `PERIOD_SEAL_VALIDATION` (3) | personal player predicate; global winner/seal owner retained | personal winner-ejection exception | same generic population; special NPC lifecycle remains separate | independent of period |

## Test evidence

Qualified candidate: `C:\Users\ZBook\L2J_Mobius\.qol005-candidate`; clean clone at required parent with exact QOL-005 overlay. Candidate-only SQL files were byte-materialized from the unchanged operator checkout so the existing schema manifest matched across Windows EOL. The two unchanged canonical semantic inputs were also byte-materialized for existing hash parity (`high-five-ru-semantic-v1.xml`: `16C749B9E...E66A18`; `high-five-ru-corpus-v1.tsv`: `2B7676BC...F935E`). These validation-only copies are not product changes.

Использовалась только уже подготовленная allowlisted test DB `127.0.0.1:3308/l2jmobiush5_phantom_test` с пользователем `l2j_phantom_test`. Production `l2jmobiush5` не читалась и не проверялась; `prepare-phantom-test-db` не запускался.

- `ant -q qol-seven-signs-access-test`: **PASS**, 5/5, seed `1005001`; shipped OFF/legacy/fail-closed, real-only identity, no Seven Signs mutation, all four period admission/relog/continued-play paths, all 14 Cat/Nec lists and 1,716 attackable declarations, actual representative respawn, native Rift prerequisites/debits/first room/jump/subsequent room/cleanup, complete source census and Mammon boundary.
- `ant -q qol-005-affected-test`: **PASS**, `BUILD SUCCESSFUL`, 13 минут 9 секунд; QOL-001–004 and affected reward/rate/quest/acquisition/background/commerce/default/DB-guard coverage.
- `ant -q qol-005-freeze-test`: **PASS**, `BUILD SUCCESSFUL`, 1 минута 7 секунд; retained historical chain and Goal039 static/documentation. `Java Result: 2` is the expected negative control inside the successful aggregate.
- fresh `ant verify`: **PASS**, `BUILD SUCCESSFUL`, 30 минут 0 секунд.
- standalone `ant -q jar`: **PASS**, `BUILD SUCCESSFUL`, 16 секунд.
- `jar tf`: **PASS** for both artifacts. `GameServer.jar`: 9,030,358 bytes, 4,325 entries, SHA-256 `DF685CFA2AFA5FECA68845EE74961ECBBCB32741E2ECC20ADE74DF89B22EA6E3`, contains `PersonalCharacterQoLService.class`. `LoginServer.jar`: 313,194 bytes, 200 entries, SHA-256 `309F8098BF5B80CCAA42F4ED3764AFD9277AA0197D8BF7F26CA12654D665AE97`.

Первые candidate attempts были diagnostic-only: sandbox ACL blocked `Path.toRealPath`, clean-clone SQL EOL differed from the existing manifest, а первый focused run обнаружил неверное ожидание теста о stock two-member party-disband ordering. Product guards не ослаблялись; запуск перенесён в disposable candidate с process-local Git safe-directory only, SQL inputs byte-matched to the existing manifest, а assertion исправлен в соответствии с `Party.removePartyMember`/`DimensionalRift.partyMemberExited`.

Commit subject: `qol(005): add personal Seven Signs access`.

Final scope audit: task-owned implementation/test/docs paths only. Task package, `.phantom-local`, candidate materialization, build output, JAR and unrelated user changes не входят в staging.

Mojibake-маркеры в 21 изменённом файле проверены отдельно: **совпадений нет**.

Escaped Cyrillic в 21 изменённом файле проверен отдельно по всем шести обязательным regex-паттернам: **совпадений нет**.

Control characters в 21 изменённом файле проверены отдельно (разрешены только tab/CR/LF): **совпадений нет**.
