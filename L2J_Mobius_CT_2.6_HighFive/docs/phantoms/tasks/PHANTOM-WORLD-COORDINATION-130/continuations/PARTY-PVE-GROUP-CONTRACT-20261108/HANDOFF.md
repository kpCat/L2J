# TASK130 Phantom World — передача после native №23

Состояние на 10 ноября 2026 года, Europe/Chisinau. Продолжать существующую TASK130. Следующий игровой рубеж — штатная добыча, полный самостоятельный STORE с нулём native Player и полезное действие после sameDB/sameFINALJAR restart. Сейчас **TASK130 PARTIAL, TASK130–136 FULL 0/7**.

## Что прочитать в новом диалоге

1. Этот HANDOFF и последние разделы [RESULT.md](RESULT.md), особенно разбор №22. Первичное соглашение хранится в [TASK.md](../../TASK.md) и [исходной приёмке A–G](../../ACCEPTANCE.md).
2. В этом каталоге прочитать [START-HERE.txt](START-HERE.txt), [GOAL.txt](GOAL.txt), [ARCHITECTURE.md](ARCHITECTURE.md), [IMPLEMENTATION-BLUEPRINT.md](reference/IMPLEMENTATION-BLUEPRINT.md), [SOURCE_MAP.md](SOURCE_MAP.md), [ACCEPTANCE.md](ACCEPTANCE.md), [TEST_PLAN.md](TEST_PLAN.md), [RISKS.md](RISKS.md), [WORKFLOW.md](WORKFLOW.md), [SAFETY.md](SAFETY.md) и [ROADMAP.md](ROADMAP.md). Перед передачей сверены 13 файлов по MANIFEST и 14 записей SHA256SUMS. HANDOFF и накопительный RESULT находятся вне исходного манифеста.
3. Применить уточнения владельца из раздела о безопасности ниже. Числа native-бюджета в первоначальном MANIFEST отражают начало продолжения. Текущий остаток приведён здесь.

## Git и исходники

- GitHub — [kpCat/L2J, WIP](https://github.com/kpCat/L2J/tree/wip/coordination-130-preserved). На входе проверен `6cfb974daa2565117691bb8236a9a887f363e61f`. На момент записи HANDOFF опубликованный и повторно прочитанный GitHub **code HEAD** — `0bd3b33dfbd8168e24a3776237ef2ad5928e8d13`. Следующий commit добавляет только этот HANDOFF. Точный HEAD документа проверяется после push и передаётся в итоговом сообщении. Для новой сессии сверить `git rev-parse HEAD` с `git ls-remote --exit-code origin refs/heads/wip/coordination-130-preserved` и `git status --short --branch --untracked-files=normal`.
- Основной независимый clone — `C:/Users/ZBook/.codex/visualizations/2026/11/08/01a1bcb8-c716-7f61-b2d8-269b39d0da2e/task130/implementation`. Перед коммитом HANDOFF он чист, незакоммиченных исходников и untracked-файлов по Git нет. Сборочные артефакты и evidence лежат вне tracked scope. Runtime clone №23 отдельно закреплён на `1d12b282ec31d4f8d0d7b44e21e8912458ad89ab` ради воспроизводимости. Основной пользовательский checkout `D:/Codex/repos/L2J-phantom-world` и stash не использовались для правок.
- Все новые commits в хронологическом порядке:

| Commit | Назначение |
|---|---|
| `a30b4aea2c45762aa708f62c87c89430fcbb22df` | Сохранение native Party ownership на последовательном GK и bounded observation, точные события перед release/transfer/dematerialize, production-composition MODEL. **Production source JAR17.** |
| `5c6697985bf72c821eafd35b74241b4a0de8dc21` | TEST runner фиксирует законные стартовые позиции до CUT. |
| `eb50ce71ad0a662ef7aceced4056eb2334def9eb` | TEST runner добавляет явную подготовку общего rendezvous после штатного исходного history. |
| `1d12b282ec31d4f8d0d7b44e21e8912458ad89ab` | TEST runner подтверждает размещение через точный канонический SQL witness. На этом HEAD скомпилированы test-классы №23. Java production, dist и build.xml совпадают с `a30b4aea2`. |
| `0bd3b33dfbd8168e24a3776237ef2ad5928e8d13` | **Новые production-правки**, назначение владельца добычи по принятому бою, снятие устаревшего farm WAIT и проверка STORE прежнего native epoch. Четыре файла вместе с focused MODEL. В JAR17 их ещё нет. |

## Что доказано в реальном GameServer №23

Native receipt `native-20261110-092203`, PID **68036**. GameServer загрузил штатный SpawnTable. Production owner после bounded physical proof принял цель **Hot Springs Yeti 21320**, NPC level 75, `21320@generated.farm.5241a753fc2261cfc117ac7b.anchor`. Это первая физически подтверждённая проверенная заявка данной сцены. В штатном CAS при нуле Player записана LogicalParty `8DB61D4AEEF95C7C9E4829C79D113DFB1C6C967140309548ECCB1CD390230CE6`, generation 1. По readback группа и все три claims соответствовали фактическому составу. В production не закреплялись NPC или персонажи.

| Профиль | Native objectId | Фактическая роль | Исходное состояние TEST FIXTURE |
|---|---:|---|---|
| 1 | 268480990 | Duelist class 88, DD и stock leader | 76, 931 275 828 EXP, 0 SP, 50 000 аден |
| 3 | 268481003 | Hell Knight class 91, frontline guardian | 76, те же EXP/SP и адена |
| 4 | 268481022 | Cardinal class 97, support healer | 76, те же EXP/SP и адена, штатный learned Heal |

Персонажи подготовлены до CUT через production creation и lifecycle. Согласие и расписание проверены, проверены реальные экипировка, skills, HP/MP и роли. Для одного участника, чей законный Background до CUT изменил исходную точку, test runner с явным `TEST_FIXTURE` вернул его к общему безопасному старту в Годдарде через native placement и канонический STORE. Позиция была единственным намеренным исключением из проверки, остальные защищённые игровые данные сверены перед и после. Это подготовка сцены. Отдельная предыдущая disposable сцена `gk-shared-start-pve` отвергнута консервационным guard и сохранена со своими raw-данными, в №23 она не использовалась. После CUT игровой прогресс и ресурсы меняли штатные owners.

| Этап | Подтверждённое игровое событие |
|---|---|
| Formation | К 50,267 с в World три живых Player в одной штатной stock Party после invite/accept. Stock leader 268480990. Native Party ID `3535F0CB1C05CDD9D678DDE85544DC488227788B546A56A3B3107C69D499EAAD` отличается от durable LogicalParty ID. Stock rendezvous около `(148024, -55281, -2728)`. |
| WALK и GK | Группа начала рядом со штатным GK. Party route owner подтвердил WALK checkpoint, затем три индивидуальных перехода `leg.872c4a16bdd379d147b68e83`. Профиль 1 в 11:24:19, профиль 3 в 11:24:29, профиль 4 в 11:24:39. У каждого **50 000 → 40 700**, списано **9 300 аден**, landing `(144880, -113468, -2563, instance 0)`. До последней квитанции не наблюдалось перевода в Solo или DRAIN. Все receipts относятся к одной LogicalParty generation 1. После GK зафиксирован WALK от landing к FARM, к выборке 140,606 с маршрут `party.route.farm.arrived`. |
| D03 | Перед боем записаны текущие роли, HP/MP, PDef/MDef, оружие, ресурсы и вклады. Начальный effective group DPS ≈229,25, прогноз длительности ≈36,9 с, healer не требовался как DD. В фактическом бою прогноз пересчитывался. |
| D04 | До начала idle Yeti допущен с плановой первой целью guardian. Во время боя фактическая цель атак — guardian 268481003. Сохранялся 6-секундный резерв и проверки неизвестных угроз, видны законные WAIT. |
| D05 / Heal | Снимок готовности показал Heal `1011:18` с `projectedHpCredit=0`. В бою Cardinal реально применил `1217:33` к Hell Knight. HP в выборке **3499,28 → 4690,83**, MP кастера **2480 → 2384**. Штатный heal receipt 1 записал 1550 восстановленных HP; разность выборок HP включает другие события боя. |
| Kill и EXP/SP | Yeti objectId **268451374**, instance 0, spawnGeneration 1. HP **8458 → 0**, damage от Duelist и Hell Knight, штатная награда всем троим, **+54 547 EXP и +9 592 SP каждому**. Это один точный групповой kill из native receipts, а не Background roll. |
| STORE и STOP | После убийства в 11:26:30 LogicalParty перешла в DRAIN с `REPLAN_THREAT`. В выборке 210,784 с был уже BACKGROUND, хотя два участника оставались в World. При STOP все три native STORE завершились `COMPLETED`, prepared=false. Durable agreement и три claims сохранены. STOP завершился в 11:31:18, GameServer exit 0, stock save и закрытие Hikari подтверждены. |

Наблюдатель не получил `NATIVE130_EARNED_LOOT`. Сам факт отсутствия receipt не показывает, выпал ли допустимый предмет. Стоковые дропы вероятностные. На предметах, роллах и конфигурации ничего не подстраивалось. Из-за отсутствия loot и нуля World до STOP runner завершился `NATIVE130_FINITE_EVIDENCE_DEADLINE`, launcher exit 1. `native130.complete` и разрешающий restart `native130.persisted` не созданы. Это настоящий полезный бой с EXP/SP и Heal, приёмка полной вертикали пока открыта.

После DRAIN у профилей 1 и 3 SQL readback показал level 75 и 925 830 375 EXP, у профиля 4 — level 76 и 931 330 375 EXP. У всех SP 9592 и адена 40 700. Профили 1/3 сохранились у Годдарда, профиль 4 в Hot Springs. Использовать эти значения как реальные результаты канонического STORE. Причину изменения прогресса и позиции не выводить по одному финальному SQL-снимку. Точный первый callback потери owner в №22 остаётся неизвестным. Причина расхождения knowledge hash между прежними GameServer также **UNKNOWN**.

## Реализация и проверенные границы

**GK, `a30b4aea2`.** Односекундные FarmSourceView и rich roster остаются входными условиями действий. Отдельное retention-подтверждение существующего FarmJourney использует exact LogicalParty ID/generation, stock leader и roster, native epochs и execution owner. Действует до исходного 8-минутного route deadline. Неполное наблюдение даёт `WAIT_PARTY_JOURNEY_OBSERVATION` с повтором через 5 с. Настоящий logout, смена epoch/generation/owner или истечение срока отзывают hold. После первой оплаты нельзя подтвердить другого участника, повторно списать деньги или публиковать личный FARM Goal на основании неполного снимка. Задействованы точные trace события `PARTY_GK_ARRIVED`, `PARTY_RELEASE`, `PARTY_EXECUTION_REQUEST`, `PARTY_DEMATERIALIZE_REQUEST`, `PARTY_LOGICAL_DRAIN`. Модель на трёх участниках RED 71/72 и GREEN 72/72. №23 подтвердил все три последовательные посадки с сохранённым ownership.

**Loot и retirement, `0bd3b33d`.** Один владелец bounded native pickup закрепляется по фактически принятому Combat session для этого target в текущей stock Party, включая PROTECT/ASSIST, когда плановый puller занят. В конце освобождается резерв, выводится `PARTY_FARM_LOOT_END` с профилем, target и terminal result при наличии. Приём Combat обнуляет устаревший farm WAIT, но guard угроз перед последующими действиями остаётся. Старый код в MODEL принимал бой защитника и завершал победу в TERMINAL вместо LOOTING. RED 12/13, GREEN 13/13. Это исправление доказанного пробела. В №23 отсутствовал terminal trace новой версии, поэтому не приписывать ему точную причину отсутствия дропа.

После stock leave и execution transfer в Solo завершённый rendezvous receipt связывает прежний native epoch, actor, source и Goal с тем же LogicalParty DRAIN. Пока ожидается каноническое отсутствие, production work sink и ordinary decision admission не начинают новый личный фарм при отсутствии человека рядом. Сам execution handoff и штатное завершение принятых работ сохраняются. Другой native epoch, отдельный Goal, права human Party и конечные сроки сохраняют свою силу. В RED transferred actor считался сохранённым, LogicalParty уходила в BACKGROUND. GREEN держит DRAIN до точного отсутствия через штатную границу и сохраняет случай действительно независимого Solo actor. Новая проверка прошла вместе с world-coordination130 72/72, party-tactics 13/13, party-lifecycle 34/34 и party-route 9/9. Чистая JAR и native на `0bd3b33d` **ещё не выполнялись**. Переход всех трёх через штатный самостоятельный STORE, фактическая добыча и restart на новой версии остаются обязательными.

## Карта исходников

Пути относительно `L2J_Mobius_CT_2.6_HighFive/`. Имена методов привязаны к checkpoint `0bd3b33d`.

| Владелец | Файл и точка входа |
|---|---|
| Durable Party и CAS | `java/org/l2jmobius/gameserver/phantoms/party/PhantomLogicalPartyCoordinator.java` — `previewDiscovery`, `commitDiscovery`, `transition`, `knownGroupFor`, `recoverAfter`. `PhantomLogicalParty.java` — `requiresSoloExecution`, фазы и expiry. |
| Scheduler проекции | `party/PhantomAutonomousFarmParty.java` — `logicalPulse`, `projectLogical`, `advanceNativePartyRoute`, `waitForFarm`, `fightSupersedesWait`, `recoverColdLogicalParty`. Здесь граница NATIVE_ACTIVE → DRAINING → BACKGROUND. |
| Native Party и маршрут | `party/PhantomPartyCoordinator.java` — `reconcile`, `refreshFarmIntent`, `currentCommittedNativeLifetime`, `retainsFarmJourney`, `farmJourneyProof`, `requestAutonomousFarmRoute`, `advanceFarmJourney`, `awaitsFarmStore`, `drainAutonomousFarm`, `releaseAutonomousFarm`. `party/PhantomPartyRouteCoordinator.java` — stock follow, WALK и GK через Combat route lease. `navigation/PhantomNativeTravelActions.java` — `partyGatekeeper`, stock quote, оплата и landing. `PhantomVisibleFarmTravel` остаётся владельцем Solo, штатный запрет Solo travel в Party сохранён. |
| Цель, агрессия и урон | `party/L2jPhantomPartyBackend.java` — `farmJourneyRosterCurrent`, `autonomousFarmReady`, `autonomousFarmTarget`, `autonomousFarmEncounterReady`, `groupViable`, `threatVictims`. `PhantomPartyFarmEligibility` — общий диапазон и targets штатного MagicUseTask. `PhantomPartyTactics.plan` и `PhantomPartyCoordinator.dispatchTacticalCombat`, `releaseTacticalCombat`, `requestAutonomousFarm` — границы тактики и claims. |
| Combat и добыча | `combat/PhantomCombatService.java` — `startContentSession`, `process`, `processLoot`, `consumeTerminal`. `combat/L2jCombatBackend.java` — `lootCandidates`, `pickUp`, `observeLoot`, `eligibleLoot`. `combat/PhantomNativeLootControl.java` — штатные bounds и проверки права подбора. Ядро `java/org/l2jmobius/gameserver/model/actor/Attackable.java` — `calculateRewards`, `doItemDrop`, stock drop. `Player.java` — `nativeLootBefore`, `recordNativeLoot`. `PlayerNativeEvidence.java` — `TargetReceipts`, `pickupReceipt`, `snapshot`. Не менять drop rate или distribution ради результата. |
| Execution, Background и lifecycle | `PhantomSystem.java` — `desiredExecutionOwner`, `partyRetainsNativeLifetime`, `awaitingLogicalPartyStore`, `logicalGroupMayDrain`, `prepareExecutionOwner`, `executionNativeBoundary`, `safeSoftReclamation`, `requestLogicalWake`, `currentPhysicalDemand`, `recoverStartupParty`. `decision/PhantomExecutionControlPort.java` — `onPulse`, `materializationLifecycle`, goal publication priority. `decision/PhantomGoalStateStore.java` — штатная публикация Goal. `background/PhantomHistoricalBackgroundService.java` — `prepareCurrentAuthority`, `prepareVisibleDecision`, accepted Party root. `background/PhantomBackgroundService.java` — `beforeStore`, `afterStore`, F7 and canonical SQL witness. `PhantomScheduler.java` — `requestIdleRetirement`. `player/PhantomMaterializationService.java` — `dematerialize`, `requestDematerialize` и канонический cleanup. |
| Harness | `test/java/org/l2jmobius/tests/phantoms/PhantomPartySuite.java` — GK observation gap, defensive loot, released Source actor. `PhantomCoordination130Native.java` — `preflightWitness`, `observe`, `exactGroupRewards`, `persisted`, runner origin и gate для restart. `PhantomCoordination130Integration.java` — собственная схема, SQL provenance и read-only probe. `tools/phantom-runtime/Test-Coordination130.ps1`, `Assert-Coordination130Outcome.ps1`, `Invoke-Coordination130FirstPartySql.ps1` — упаковка, G0, запуск, stock STOP и postflight. |

## Артефакты, G0 и бюджет

- Корень данных и свидетельств — `C:/Users/ZBook/.codex/visualizations/2026/11/08/01a1bcb8-c716-7f61-b2d8-269b39d0da2e/task130`. Сцена №23 — `gk-continuity-pve`, с дочерними `implementation`, `runtime-rendezvous`, `sql-rendezvous`, `evidence`. Целевые MODEL, SQL и launcher logs — `evidence/gk-ownership-6cfb974d` от общего корня.
- **JAR17 SHA-256 `8C1CACA1A879521CD9FD67CD0FBFF5ABA849106602BE5CF10C1F1ADD618B2195`**, production commit `a30b4aea2c45762aa708f62c87c89430fcbb22df`. Точный архив №23 — `gk-continuity-pve/runtime-rendezvous/libs/GameServer.8C1CACA1A879521CD9FD67CD0FBFF5ABA849106602BE5CF10C1F1ADD618B2195.jar`. JDK25 + Ant, 36 с. Source/JAR parity проверена для 4761 class entries по SHA каждого файла и 4 runtime dependencies. Runner origin из `gk-continuity-pve/implementation/build/phantom-test-rendezvous/bin` проверен в `javap` и на native. Для следующей native нужна новая clean JAR с `0bd3b33d` или его проверенным потомком.
- Собственная MariaDB 11.4.13, **127.0.0.1:33307**, схема `l2jmobiush5_phantom_test_130_rendezvous`, отдельный scoped principal и локальные секреты. Прежний экземпляр №23 PID **18924**, config SHA `5944156135C9965BC373D9BC356DDAD9FB88AD6D7D89593A3A84892A7FD73122`. Штатно остановлен после SQL readback и повторного G0. Нет своих JVM или listeners на 33307/7962/9262/2262. Исходный TEST130 `l2jmobiush5_phantom_test_130` на 3308 остаётся у прежнего PID **8692**. Не запускать native в исходной схеме и не трогать старые регрессионные сцены. Файлы с учётными данными не публиковать.
- При запуске собственной MariaDB использовать `Invoke-Coordination130FirstPartySql.ps1 -Action start -Lane rendezvous` в новом собственном контуре. Для точного существующего контура предусмотрен `-Action resume`. Затем `-Action attest`, точные PID/FILETIME, owner, образ и config hash, собственный datadir, loopback и отсутствие registered service. Существующий `PhantomCoordination130Integration prepare` загружает штатный schema inventory только в свежую собственную схему. `probe` читает scoped principal/schema, grants, чужие сессии, consent, расписание, claims, Goal, Catchup и STORE. Native launcher дополнительно проверяет source/runner linkage, config equality, Geo и protected manifest. После STOP запустить `Test-Coordination130.ps1 -Action postflight -Lane rendezvous -RuntimeRoot <owned runtime>`, сверить Geo203 по размеру и SHA каждого файла, protected207 и SQL provenance. Только затем завершать собственный SQL через штатный `-Action stop`.
- Разрешение после `6cfb974d`: использовано **1/2 clean JAR, 1/4 фактических GameServer bootstrap, 0/1 условных restart**. Остаток **1 clean JAR, 3 bootstrap, 1 restart после PASS**. Общий расход продолжения **20 clean JAR, 27 фактических GameServer, 0 restart**. Headless fixture/SQL не входили в GameServer budget. В этой передаче не расходовать следующий запуск.

### SHA-256 основных свидетельств

Все пути в таблице относительно указанного выше корня. Raw не включён в Git.

| Каталог и файл | SHA-256 |
|---|---|
| `gk-continuity-pve/runtime-rendezvous/native-20261110-092203.stdout.log` | `750BE24260479CFB655667229DAC33CCAE74B15F975E113A6D28B2FD0FF39F27` |
| `gk-continuity-pve/runtime-rendezvous/native-20261110-092203.stderr.log` | `96B43DC92395ADE2D2F153D34BDC026F92B563BCD4722DD7B98EBE5966EB2454` |
| `gk-continuity-pve/runtime-rendezvous/native-20261110-092203.process.json` | `5ABCEE10E65E4E838EBF8F5FA13F0781705A4766D45D954C8A58A1AA23F20F40` |
| `gk-continuity-pve/runtime-rendezvous/native-20261110-092203.exit.json` | `2A7E8EEA66BADED2608C7239BBCB6C4142AF3C18328ED69016CDB1FB6ADB3F27` |
| `gk-continuity-pve/runtime-rendezvous/native130.trace.native-20261110-092203.68036.tsv` | `FA830FC1218F96890598699141314930100AAA4E42BC1E4BBC03D1C78B11F826` |
| `gk-continuity-pve/runtime-rendezvous/agreement130.native-20261110-092203.68036` | `70F7BBCDC4997C77731343DDE6B78E6EDE9330431DE91E7BB8DFCFE64C9FC5EA` |
| `gk-continuity-pve/runtime-rendezvous/preflight130.native-20261110-092203.68036.before.txt` | `F87AC00893653D70F5F82EAC87AAF7185A561EA981183E9F2418EBB509A7A0CF` |
| `gk-continuity-pve/runtime-rendezvous/preflight130.native-20261110-092203.68036.after.txt` | `DA405F1E69AA0C4BD79FE47ACDED84F18A7F6222EE645D449036C1F41949F965` |
| `gk-continuity-pve/runtime-rendezvous/human130-heal.tsv` | `31BDBF88C1C900B36C30D6000DA2A7722A35C361E6E2797ECD9D7AC9FDF058E3` |
| `gk-continuity-pve/runtime-rendezvous/log/java0.log` | `538AD3137F4155725C7471FDF45E1B1EA096C1279DC62BD1EE317ABAAF5271DB` |
| `gk-continuity-pve/runtime-rendezvous/log/error0.log` | `E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855` |
| `gk-continuity-pve/runtime-rendezvous/geo203.json` | `A8A4B73BC7B53BED28F545CD889F489492B585B369F8E84988F8DB8A91C973D5` |
| `gk-continuity-pve/evidence/protected-pre-native.json` | `9777AB48BC8D14BDEE77D4ACA8E7E650CB099F03CA33FEA108D5CDB941C59DCD` |
| `evidence/gk-ownership-6cfb974d/healthy-shared-start-staging.log` | `523E9741C3AD0FFD6F6426D4BA856F4795840CCEFABD6496F956AE5491768CF5` |
| `evidence/gk-ownership-6cfb974d/jar17-provenance.json` | `20B05321E70C6A85480E9EAEB36A9433FF171C8C6A77E1F170819CA1D2E7D117` |
| `evidence/gk-ownership-6cfb974d/native-23-launcher.log` | `C6001DA0D18809724FAACBFEAA5A60BCE25491CFAD8179A29D4F160595CA2133` |
| `evidence/gk-ownership-6cfb974d/native-23-post-sql.log` | `F24552A23652DBA9AE717C7701A03F17115CEED84AE99553F57696DDA3E647F8` |
| `evidence/gk-ownership-6cfb974d/handoff-postflight.log` | `C92E8E6A2C2FD4217148336043A2B6E54E3C74E47980EC3CF759A3671934FC7B` |
| `evidence/gk-ownership-6cfb974d/red-defense-loot.log` | `C3A51E979E9DE696C5CF0DE812AC480653E545EF0026626EF05FC445EEC18DE7` |
| `evidence/gk-ownership-6cfb974d/red-party-store-projection.log` | `65000F6FC43FC6A896DF49BFCE08222D072C52DB20BCBEDDB1442F7BA0AE9B66` |
| `evidence/gk-ownership-6cfb974d/green-post-gk-party-tactics.log` | `A76D3EC993B97E5096DAB6AFDBB4E8BC3C522AA8872CC2F5A0CD654EA3E65084` |
| `evidence/gk-ownership-6cfb974d/green-post-gk-world-coordination130.log` | `A2051BB940CF5DCB796BCE72C2F8477098FAEAA367A7A786174F12AEE1472F75` |

## Safety и сохранение игрового контракта

- Только независимый clone и собственный изолированный TEST130. MAIN/PLAY/TEST129, исходная TEST130 на 3308 и профили 70/72/85/86 сохраняются регрессией. Не менять основной checkout, stash, Geo203, protected207, чужие процессы и runtime. Никаких `clean/reset --hard`, force push, переписывания истории или массового staging. Scoped обычный push только в `wip/coordination-130-preserved`.
- G0 и before/after authority, Goal/Catchup, conservation witnesses обязательны. Каноническое восстановление разрешено через существующих владельцев. Не менять напрямую SQL, requestId, курсоры, receipts, Goal, ожидаемый hash или Background ради успешного результата. F7 сохраняет fail-closed на untagged несовпадающем expectedAfterHash, stale/corrupt/replay и дубли. Последняя source-правка не меняла F7. В №23 проходили exact pre-CUT readback и scoped post-STOP SQL. Отдельный полный SQL прогон F7 после `0bd3b33d` не запускался. F8 — штатный ordinary Player Heal до CUT (прибавка HP 48,0249, −5 MP) и реальный Party Heal в №23. Stock формулы Heal не изменены.
- Исходные TEST FIXTURE значения только до CUT, с прозрачным baseline и production creation/STORE. После CUT никакого ручного восстановления HP/MP/EXP/SP, смерти, вещей, skills, позиции, Goal или выдачи наград. Не отключать агрессию, PvE, PvP, Combat guard, GK fee, CAS, consent, ownership, Scheduler, Population/Ecology или Geo ради PASS.
- Текущая Party принимает 2–9 с гибкими ролями TANK/SUPPORT/DD/SPOILER, пересечение диапазонов ±4 для каждого участника. Использовать суммарную готовность и реальные роли. Будущие средние уровни и фарм всех обычных мобов спота отложены. Solo farm invariants сохраняются.
- Создание stock Party, встреча и путешествие самостоятельны. Безопасный rendezvous предпочтителен, создавать штатную Party можно в любой допустимой локации. Stock remote invite не блокируется искусственной привязкой к FARM или городу. При человеческом приглашении переиспользовать `PhantomRealPartyInvitationPolicy`, native invite/accept, consent и штатные права лидера. Возможность пригласить соло-бота и честные сведения о классе и уровне остаются контрактом дальнейшей приёмки, проверенных полноценных разговорных сценариев здесь нет. После смерти штатный respawn и replan от реальной позиции.
- Сохранять обязательный threat guard перед существенным Combat действием. D04 использует фактических получателей атак/кастов, характеристики конкретных NPC и защиту каждого получателя. Резерв 6 с относится к реальной возможной смене цели. Отсутствие агрессии у idle NPC допускает безопасное начало. При неизвестной или опасной угрозе bounded WAIT/REPLAN с конкретной причиной и nextDue. Поддержка через отдельный owner, только фактические HP/MP. Штатные индивидуальные расходы GK и подтверждение landing сохраняются.

## Следующая инженерная итерация

1. Сверить GitHub, открыть exact diff от `a30b4aea2` до `0bd3b33d`, взять зелёные MODEL из checkpoint. Проверить изоляцию, порт/схему, protected manifests и расписание. Оставить №23 и исходную TEST130 как регрессии. Для первого вертикального PASS предпочтительна отдельная свежая disposable сцена, так как №23 завершился с разнесёнными позициями и отдельными Goal после DRAIN. Подготовить законных DD, танка и хилера до CUT. Использовать существующий test runner и production owner. Выбор состава, маршрута и NPC остаётся за штатным planner. Если используется существующая сцена, разрешён только штатный recovery, без правок уже сохранённой истории.
2. Потратить оставшуюся clean JAR на тот же production checkpoint или следующий проверенный причинный fix. В собственном checkout собрать штатным JDK25/Ant. Не повторять `owner-config-apply` в контуре, где он отказал по quest baseline. Использовать изолированный чистый build input, как для JAR17, и доказать class inventory, побайтовую parity, dependency SHA, runner bytecode и G0. Не использовать JAR17 как новый final, поскольку в нём нет `0bd3b33d`.
3. После G0 сделать один целевой bootstrap и идти до stock Party → travel → нового kill → stock loot receipt и Heal → естественное снятие demand, DRAIN и отсутствие всего прежнего native roster при сохранённом durable groupId. Сопоставить `PARTY_FARM_LOOT_END` с реальной добычей, terminal и stock наблюдением. При неполученной добыче различить отсутствие выпадения, недопустимого кандидата, отказ pickup, отсутствие witness и отмену ownership. При задержке STORE сверить `PARTY_RELEASE`, `PARTY_EXECUTION_REQUEST`, `PARTY_DEMATERIALIZE_REQUEST`, completed rendezvous и точный epoch. Личные действия Solo не должны стартовать раньше законного STORE бывшего Party epoch. Не смягчать F7/F8 или Combat guard. Повторный bootstrap только после установленной причины, конкретного исправления и targeted RED→GREEN в остатке бюджета.
4. При полной первой вертикали и clean STOP использовать один sameDB/sameFINALJAR restart через `Test-Coordination130.ps1 -Action restart`. Existing wrapper требует соответствия процесса, JAR, `native130.complete`, `native130.persisted` и stock STOP. Проверить тот же durable groupId, сохранённые значения, claims и новые native receipts полезного действия. Затем штатный STOP и postflight. FULL130 допустим только после проверки исходных A–G и дополнения к приёмке.

## TASK130–136

| TASK | Статус | Продуктовый рубеж |
|---|---|---|
| 130 | **PARTIAL** | Доказаны durable LogicalParty при нуле World, native материализация, stock invite/accept, совместный WALK, три оплаченных GK, реальный групповой Yeti kill, EXP/SP, боевой Heal, канонический STORE при STOP. A–C приняты в пределах MODEL/SQL/Geo, D03–D05 показаны в №23. Открыты native loot receipt, самостоятельный полный STORE после ухода demand и полезное действие после restart на final source/JAR. |
| 131 | NOT_STARTED | Расширенная жизнь Party, групповой spoil/sweep и дальнейшие support/PvP сценарии. |
| 132 | NOT_STARTED | Кланы, альянсы, войны и полномочия лидера. |
| 133 | NOT_STARTED | Рейды, Rift и инстансы. |
| 134 | NOT_STARTED | Осады. |
| 135 | NOT_STARTED | Территориальные войны. |
| 136 | NOT_STARTED | Олимпиада. |

**FULL 0/7 → 0/7.** Новый факт этой итерации — вся тройка прошла последовательный платный GK в одной Party и затем штатно получила групповые EXP/SP и Heal. Native-регрессия следующего checkpoint ещё впереди.

## Журнал Git и проверка текста

Для проверки и публикации этого checkpoint применялись `git rev-parse HEAD`, `git status --short --branch --untracked-files=normal`, `git log -6 --format='%H %s'`, `git diff --name-only 6cfb974daa2565117691bb8236a9a887f363e61f HEAD`, `git diff --quiet a30b4aea2c45762aa708f62c87c89430fcbb22df HEAD -- <java, dist и build.xml в runtime clone>`, `git diff --check -- <точные пути>`, `git add -- <точные пути>`, `git diff --cached --check -- <точные пути>`, `git commit -m 'Settle TASK130 defensive loot and native party retirement'`, `git commit -m 'Document TASK130 native handoff after run 23'`, `git merge-base --is-ancestor <проверенный remote SHA> HEAD`, `git ls-remote --exit-code origin refs/heads/wip/coordination-130-preserved` и `git push origin HEAD:refs/heads/wip/coordination-130-preserved`. Чтение, сборка, commits и публикация ограничены независимыми clones и явно разрешённым WIP.

- Mojibake-маркеры в изменённых файлах проверены отдельным сканированием.
- Escaped Cyrillic, включая XML numeric references, в изменённых файлах проверен отдельным сканированием.
