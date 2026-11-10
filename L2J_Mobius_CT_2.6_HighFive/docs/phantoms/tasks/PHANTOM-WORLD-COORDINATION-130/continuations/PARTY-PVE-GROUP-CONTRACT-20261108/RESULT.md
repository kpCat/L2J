# TASK130 — исполнение группового контракта

Статус `PARTIAL_SHARED_ROUTE_MODEL_SQL_GREEN / NATIVE_NOT_AUTHORIZED`. FULL130=false. TASK130–136 FULL до и после 0/7 → 0/7. Исходный WIP `8140167705e6a6e76f370e3aa772b50f909b4d37`. Сохранённый JAR собран из `ea0e202e26755c5ba79c096c42007e61cf3e1c5e`. Публикация только в `wip/coordination-130-preserved`.

## Read-first и сохранность

Прочитаны корневые AGENTS/README, CODEX_WORKFLOW_CONTRACT, PHANTOM_CURRENT_STATUS, исходные TASK130 GOAL/TASK/ACCEPTANCE/ADR-002 и все 13 содержательных файлов этого продолжения. Пакет фактически найден в корневом `docs/phantoms/...` пользовательского checkout. MANIFEST 13/13, SHA256SUMS 14/14; копия в этом clone побайтово совпадает. Родительские AGENTS выше исходного Git-root не найдены; дополнительных AGENTS/code-map/pattern-файлов в серверном scope не найдено.

Прочитаны planner, HistoricalBackgroundService, BackgroundAuthority/Service/Transaction/State, knowledge query, progression catalog/model, LogicalParty/Coordinator, native Party backend/selectors/coordinator и build.xml. Аналоги: PhantomFarm074Checks (immutable knowledge/topology/route), PhantomWorldCoordination130Suite (durable CAS/ownership), существующие PartyRoleMatcher и native Combat/Acquisition owners. Переиспользуются их contracts, а не новый engine.

Изоляция: независимый clone с GitHub на указанном HEAD. Попытка локального clone исторической partial-копии непригодна из-за отсутствующих promisor blobs; она не используется для реализации. Основной checkout, его stash, исторический clone и защищённые поставки не изменяются.

## План реализации (согласованный blueprint)

- [x] Одна pure Party FARM policy: уровни каждого ±4, пересечение 2–9, NPC до89; роли potential/verified различаются, legacy strings сохраняются.
- [x] PARTY-purpose во всех rendezvous entrypoints, target/spawn/anchor/route/fee fences; проверка общего источника до ограничения результатов, bounded durable discovery и CAS revalidation.
- [x] Native selectors и исполнение: административный лидер свободной роли, 2–9 без обязательного healer/третьего DD, существующий RoleMatcher, secondary contributions и spoiler evidence. Реальная native приёмка ниже остаётся открытой.
- [x] Реализован aggregate readiness на native snapshots и прежних owners; MODEL/source review PASS. Его фактическая Player/Monster проверка D03–D05 NOT_PROVEN.
- [x] RED→GREEN MODEL в существующем runner, SQL после G0, отдельный JVM recovery, таблица call-site coverage и негативных guards.
- [ ] Только после дешёвых GREEN и lawful preflight: одна clean JAR, parity, один GameServer; один sameDB/sameJAR restart лишь после полезной первой вертикали и postflight.
- [x] Независимый source review и fix pass, отдельные mojibake/escaped Cyrillic проверки, scoped source commit. Публикация/документальный seal выполняются отдельно.

Bounded scope exception: связанные seams в party/background, их существующие тесты и role XML могут суммарно превысить 10 файлов, как прямо требует исходный GOAL/этот blueprint. UI, схемы SQL, runtime policy/rates, stock Heal/Player, кланы/рейды/осады вне scope. Неурегулированные существенные компромиссы требуют владельца.

## Dependency map: Solo и Party

| Purpose | Owner/caller | Контракт |
|---|---|---|
| SOLO | planInitial/replan/replace/replanVisibleLocal → plan → viableTarget | Старые LEVEL_RADIUS=2, LOWER_LEVEL_TIERS=2,5,10, safety и offscreen receipts |
| PARTY discovery | previewLogical/VisibleRendezvous → rendezvousSources → knowledge/spawn/anchor/route | Member±4; без Solo viableTarget; не native-ready |
| PARTY revision/travel | planLogicalActivity/replanLogicalRendezvous/proposeLogicalActivity/graph/ingress/visible | Та же точная общая цель и policy, без скрытого Solo admission; не разрешение Background reward |
| Durable agreement | LogicalPartyCoordinator.discover/agree → repository CAS | 2–9, source/role/level/current повторно проверены; стабильные claims/groupId |
| Native projection | projectLogical/pulse/rendezvous/selectors → RoleMatcher → PartyCoordinator | Actual Player/skills/MP/lease, invite/accept, лидерство отдельно от puller |
| Native PvE | autonomousFarmTarget/groupViable → requestAutonomousFarm → Combat/tactics | Совокупная полезность и наблюдаемая угроза; без синтетических эффектов/награды |

## Проверки и затраты

JAR_BUILDS=0; GameServer.main=0; RESTART_BOOTS=0. Это счётчики данного продолжения, не исторических запусков. Были три неудачные headless catalog-проверки, фактически загружавшие stock SpawnData; они НЕ SQL-only и не являются доказательством native gate. Подробнее ниже.

## Согласованная модель угроз

Владелец согласовал: основной урон получает наблюдаемая фактическая AI-цель моба; Aggression не гарантирует удержания. Для остальных — резерв смены агрессии до6с, рассчитанный по характеристикам каждого реального моба. Угрозы собираются возле каждого участника, дубликаты исключаются до bounded overflow. Незанятый моб допускает безопасный pull без предварительно наблюдаемой агрессии. Неизвестная опасная цель даёт WAIT_TANK_AGGRO/WAIT_THREAT с причиной и nextDue5с; после60с ожидания существующий group owner переходит в DRAINING/REPLAN. Ожидание не выдаёт HP или право действия.

Хилер сохраняет отдельный support-owner; прогноз Heal не прибавляется к HP. Фактические HP/MP меняет только штатный skill. Guardian с Sacrifice и оружием продолжает давать один damage contribution. Combat cancellation повторно проверяет roster, band, цель и угрозы перед последующими действиями. Боеприпасы читаются через Inventory.findArrowForBow/findBoltForCrossBow, без equip/debit; бюджет учитывает целые выстрелы, MP и stock attack/reuse. Physical и magic skills используют существующий castEncounter и фактическую стоимость MP. Ни один участник не обязан победить в одиночку.

## Call-site coverage

| Production цепочка | Проверка в существующем runner | Результат |
|---|---|---|
| rendezvousSources / rendezvousSourcesForRole / exact peer source | partyContract130, widePartySources130 | Solo=false допускает Party; общий65-й источник найден; 71/81 вне band76 |
| replanLogicalRendezvous / proposeLogicalActivity / graph / ingress / visible | partyContract130 + полный PhantomFarm074Checks | Один PARTY-purpose; exact source/root/revision/route; offscreen native WALK не выдумывается |
| previewLogical/VisibleRendezvous → sourceMatching → durable discover | MODEL130 paged/current-root/source tests | Intersection до cap, CAS/current повторно; 5000 профилей остаются paged |
| planLogicalActivity → compatibleRole / current root | activity revision MODEL130; SQL2/3/9 | Canonical роли; legacy support/damage читаются совместимо, без rewrite |
| selectTeam/selectRendezvousTeam/projectLogical → RoleMatcher | roles14; lifecycle34 | 2–9, guardian/support administrator, один assignment на слот |
| requestAutonomousFarm → independent puller → Combat/tactics/support/loot | tactics10 + production-composition MODEL fixtures | Puller отдельно от native leader; healer owner сохранён; один loot owner |
| native source/band/target budget + encounter cancellation | MODEL52/source review | Source до cap8, реальный level-up recheck; actual Player/NPC execution NOT_PROVEN |
| groupViable → native planning model + observed threat/resource checks | pure reserve/whole-action/threat-union MODEL + review | Формулы/guards реализованы; D03–D05 на реальных Player/Monster NOT_PROVEN |
| ordinary Solo replan/remainsSuitable/Background | полный PhantomFarm074Checks; background-model10; SQL F7 | Прежние Solo range/viability и исторический receipt fence сохранены |

## Проверки и отрицательные guards

Финальный Ant: `phantom-world-coordination130-test phantom-party-role-vacancy-test phantom-party-tactics-test phantom-party-lifecycle-test`, exit0, 1м40с; 52/52 +14/14 +10/10 +34/34. Последний physical-skill fix перекомпилирован обычным javac25, exit0; затем повторены focused suites на окончательных class files. JAR не создавался.

Финальные18 DB-free suites: **298/298**, exit0: MODEL13052, Party roles14/tactics10/lifecycle34/performance2, Combat core72/ownership17/action-ownership37, PvP policy2/admission3/combat14/party-help1, Background model10, Commerce quote5/receipt15, Population schedule3, Presence0964/calendar0653. Отдельный полный PhantomFarm074Checks exit0: source preview, rendezvous, support travel, route exclusions, Solo fallback/admission. Ранее затронутые Decision39, Navigation51, canonical invitation6, state recovery15, party route9 также PASS; они не выдаются за новые native факты.

Первый RED зафиксировал скрытый Solo admission и роль leader=damage. Дополнительный RED guardian+Sacrifice дал2 вместо3 contributors, затем PASS. Lifecycle отрицательные fixtures обновлены под законную оставшуюся пару: занятый профиль исключён, прежний exact roster/invitation освобождён, новая пара не запрещена; sealed roster не расширяется поздним peer. Первые запуски четырёх старых suites с общим seed130 отказали в beforeAll; штатные seed140014/15001501/16001601 прошли. Это не product failures. Standalone planner обнаружил ещё одно старое ожидание Solo для явного Party API: заменено на Party-positive + отдельный ordinary Solo-negative, полный runner PASS.

| № | Отрицательный контракт | Evidence |
|---|---|---|
| 01 | roster1/10 | MODEL130 band |
| 02 | player0/86 | MODEL130 band |
| 03 | непересекающиеся уровни | MODEL130 band |
| 04 | NPC71/81 при76/76 | widePartySources130 |
| 05 | excluded target | Farm074 source/rendezvous |
| 06 | excluded route edge | Farm074 route exclusion |
| 07 | disconnected route | Farm074 negative |
| 08 | чужой instance | Farm074 negative |
| 09 | stale root до agreement | MODEL130 durable-root revalidation |
| 10 | частично проигранная formation CAS | MODEL130 conflicting formation |
| 11 | stale group generation/cursor | MODEL130 transition/recreation |
| 12 | route search cap не доказывает impossible | MODEL130 route-budget proof |
| 13 | Supply/Learning/Career/STORE/native intent | MODEL130 authority preparation guards |
| 14 | idle/неактивный intent и busy peer | lifecycle34 |
| 15 | stale/refused invitation не возвращает owner | lifecycle34 |
| 16 | Spoil без Sweeper не даёт spoiler slot | roles14 |
| 17 | healer не становится лишним DD | roles/tactics production composition |
| 18 | неизвестный horizon/прогноз Heal не даёт HP | reserve policy + source review; real Heal NOT_RUN |
| 19 | ближайшие8 чужих sources не скрывают9-й | MODEL130 native-target budget |
| 20 | один attacker в двух neighborhoods не скрывает5-й | MODEL130 threat-union overflow |
| 21 | дробный/неизвестный cast/shot не даёт ресурсы | MODEL130 whole-action budget |
| 22 | no-target WAIT не продлевается бесконечно | lifecycle finite60с DRAINING |
| 23 | F7 untagged mismatch / stale root / duplicate | SQL F7 PASS |

## TEST130 SQL, identity и сохранность

G0 выполнен неизменённым Invoke-G0IdentityAttestation.ps1: ordinary Windows owner/PID/socket evidence PASS. Его строка `G0=NOT_ACCEPTED` — только OS-половина; после неё отдельно проверены actual MariaDB11.4.13:3308, principal `phantom130_d930bd6491f5@127.0.0.1`, единственная рабочая schema `l2jmobiush5_phantom_test_130`, отсутствие чужих sessions. Существующие installation grants test/test_% не менялись и не использовались. Сервис MariaDB не перезапускался.

Existing Integration modes `group-pve`, `group-pve-resume`, `group-pve-close` в трёх отдельных JVM PASS: только14 новых SQL-only профилей, три группы2/3/9, canonical roles, CAS/duplicate-claim guard, exact root/role/cursor readback, затем закрыты только их собственные claims. Players=0, awards=0, native kills=0. Witness85/79/86 во всех трёх процессах: `aed12bb28f75f9b4bee1288ff46b5ae3fcebd6429e3b5ae00d0e51befe1e09ef`. Все DML адресованы вновь созданным ids, не старым native профилям.

Existing `authority-f7` SQL PASS на трёх новых fixtures: matched/empty commits, untagged mismatch OFFSCREEN_WAIT, duplicate IDEMPOTENT, stale root rejected, повтор не меняет EXP/SP. Первый запуск из cwd clone не нашёл XML experience/skill tree и отказал; корректный cwd собственного runtime прошёл. Первый набор из трёх неудачных новых fixtures оставлен для аудита, не удалялся. Никакие старые70/72 не пересоздавались и не обновлялись. F7 credits — SQL model fixture, не native kill evidence.

Штатный postflight exit0: protected207 EXACT, собственных runtime JVM0, listeners7960/9260/2260=0. Geo203 скопированы физически, 1,063,452,308байт; final seal проверяется отдельно. Основной checkout/stash и исторические поставки не менялись.

## Native blocker и ошибка preflight-инструмента

Read-only eligibility показал на момент проверки: только70 и72 ACTIVE, consent=true, без unsettled obligation/claim. 69 отказался от Party; 85/79/86 спят, 85/86 имеют прежние expired ASSEMBLING claims. Предпочтения, расписания и claims не менялись ради сцены.

Были **три headless catalog initializations** через существующий ProductionAuthorityFixture. Этот fixture вызывает SpawnData и инициализирует42407 stock spawns, поэтому прежняя гипотеза «это дешёвый SQL-only preflight с WORLD_SPAWNS=0» неверна. GameServer.main не вызывался, managed Player не materialized. Логи сохранены; такие запуски прекращены. Новый mode `group-pve-preflight` и helper полностью удалены из публикуемого кода, автоматических native повторов больше нет.

Headless knowledge=`de241a260f8331cf74d2905bdef42dd43453bb7ddbbaaed3cf1d8d407ea26a12`, saved native knowledge70=`41edd0ded7c34e30db4c844c4e5efdafd7ed3be5678a801fb8ffd32f9b05c999`; topology/progression/commerce совпали. Причина различия knowledge generation полностью не доказана; строгий native guard не ослаблялся, фиктивная hash migration не делалась. В каждом из трёх запусков полный SQL witness69/70/72/85/79/86 до/после совпал. Исторический authority recovery70/72 не переигрывался и не объявляется новой игрой.

**Открыты D03–D05 и Gate3/4/F8**: нет доказанного actual native aggregate DPS/MP/threat composition на final JAR, нет новой stock Party, damage, earned reward, Phantom Heal HP/MP, human Heal regression, graceful group STORE или полезного sameDB/sameJAR restart. Source-only review не заменяет эти проверки. Без согласованного lawful preflight дорогая lane не запускается.

Возможный процессный компромисс для решения владельца: объединить real-Player часть D и native E в одной существующей lane. Единственный GameServer bootstrap сначала выполняет строгую pre-CUT catalog/role/source/Geo инспекцию; затем lawful materialization/stock Party, а actual aggregate readiness проверяется существующим backend до первого Combat действия. При отказе — штатный STOP без повторов. Hash/resource/owner guards не ослабляются. Это меняет требование пакета «доказать состав до bootstrap»; самостоятельно этот переход не разрешён. До решения — PARTIAL, JAR0/GS0/restart0. Альтернатива — сохранить исходный порядок и сначала предоставить безопасный отдельный native integration proof D.

## Review, scope и публикация

Два независимых read-only source review и узкий повторный review ресурсов. Исправлены: isAttackDisabled во время собственного swing; guardian+Sacrifice; source послеcap8; threat union по всему roster; ammo/MP/reuse; physical skill accounting. Известных открытых production P1/P2 по проверенному source scope нет; это не утверждение native gate PASS.

Source scope:18 файлов (12 production Java,5 test Java,1role XML), +776/−119; перечислены в source commit. Документы:15 неизменных входных файлов пакета, этот RESULT и append в существующие report/evidence. Все прочие подсистемы и stock Heal/Player/схема SQL вне diff. TASK131 native spoil/sweep остаётся следующим этапом; здесь только совместимый role/source seam. TASK132–136 NOT_STARTED, FULL0/7.

- mojibake-маркеры в изменённых файлах проверены:36 source/test/data/docs файлов,0 совпадений.
- escaped Cyrillic в изменённых файлах проверены:36 source/test/data/docs файлов,0 совпадений.

Финальный seal: MANIFEST13/13, SHA256SUMS14/14,0 mismatches; исходные15 файлов пакета сохранены. Evidence JSON parse PASS. Geo203/1,063,452,308байт — все SHA совпали; повторный штатный OS postflight protected207 EXACT/JVM0/listeners0. Raw результаты и exact allowlists находятся в собственном `task130/evidence`.

Проверка diff использует per-command `-c core.whitespace=cr-at-eol`, чтобы штатные CRLF не считались trailing whitespace; реальные хвостовые пробелы не игнорируются. Глобальный Git config не менялся. Документальный commit: `git add --pathspec-from-file=../evidence/docs-paths.txt`; `git commit -m "Record TASK130 group PvE contract evidence and native gate"`. Обычная публикация: `git push origin HEAD:refs/heads/wip/coordination-130-preserved`; затем `git rev-parse HEAD`, `git status --short`, `git ls-remote --heads https://github.com/kpCat/L2J.git wip/coordination-130-preserved feature/phantom-world wip/task129-preserved-20261106`. Конкретный итоговый HEAD сообщается после сверки remote, не подставляется в собственный commit задним числом.

Git разрешён прямым заданием: independent clone, bounded inspection, scoped commits и обычный WIP push. Использованные команды/формы: `git ls-remote --heads https://github.com/kpCat/L2J.git wip/coordination-130-preserved feature/phantom-world wip/task129-preserved-20261106`; `git clone --no-hardlinks --single-branch --branch wip/coordination-130-preserved <historical-source> <unused-source>` (promisor checkout failed); `git clone --depth 1 --single-branch --branch wip/coordination-130-preserved https://github.com/kpCat/L2J.git <implementation>`; `git rev-parse HEAD`, `git symbolic-ref --short HEAD`, `git status --short`; `git diff --stat -- <18 exact paths>`, `git -c core.whitespace=cr-at-eol diff --check -- <exact paths>`, `git add --pathspec-from-file=../evidence/source-paths.txt`, `git diff --cached --name-only`, `git -c core.whitespace=cr-at-eol diff --cached --check -- <exact paths>`; `git commit -m "Implement TASK130 group-only farm admission and native party readiness"`. В independent clone у read/write команд временный `-c safe.directory=<absolute implementation>`; failed `git diff --check --pathspec-from-file=...` был неподдержанным синтаксисом и ничего не изменил. Main checkout использовался только для разрешённого bounded branch/HEAD/dirt чтения. Не выполнялись clean/reset/stash/force/merge/rebase/restore.

## Follow-up: условная Heal-приёмка в observer

После публикации a7cff727a проверена оставшаяся native chain, без запуска JVM native lane. Выявлен старый test-observer barrier: первая vertical всегда требовала Heal, включая состав без хилера. В существующем PhantomCoordination130Native условие вынесено в маленький acceptance predicate: actual Party + новый credited group kill обязательны всегда; actual Heal HP/MP обязательны для первой vertical с canonical support.healer; useful restart по-прежнему требует новой полезной Party activity. Требование хилера сохраняется, если он присутствовал в roster хотя бы на одном шаге наблюдения.

Изменены только два существующих test Java и append отчётов. Production commit f9edb77a4 не менялся, игровые owners/формулы также. Одна focused MODEL проверка защищает no-healer positive, отсутствие Party/kill, неполные Heal HP/MP и пустой restart. Компиляция javac25 exit0, MODEL130 **53/53**, exit0; актуальная сумма прежних18 DB-free suites — **299/299**. Никакая из этих policy-проверок не считается доказательством actual Heal. При native запуске всё ещё требуется вручную сверить фактические cast/target/HP/MP traces; stock Heal и его dormant test observer не изменялись.

Native gate D/E остаётся прежним и ожидает ответа владельца. JAR0/GameServer0/restart0; новых SQL, headless catalog initializations или materializations в follow-up нет. Scoped commit: `git commit -m "Align TASK130 native acceptance with optional healer roster"`; обычный `git push origin HEAD:refs/heads/wip/coordination-130-preserved`, с повторной сверкой protected refs.

## 2026-11-09 — согласованная D+E lane

Владелец согласовал D03–D05 и E в одном GameServer bootstrap с обязательной проверкой до CUT, native readiness до первого Combat, штатным STOP при отказе и restart только после полезной первой вертикали. Перед подготовкой локальный и удалённый WIP совпали на `b3232a45f5b263940396f0a8ff6a88edc49502ab`.

Перед первым pull действующий Party backend проверяет реальный Heal на получателе, которого моб атакует либо которого выбрали для безопасного начала боя. Проверяются изученный уровень навыка, MP, reuse, cast conditions, принадлежность к штатной Party, дистанция и Geo. Начальная недоступность Heal в составе с хилером даёт `WAIT_GROUP_HEAL`. Прогноз HP рассчитывается с нулевым вкладом будущего Heal. Во время боя gate повторно проверяет фактическую агрессию и доступность поддержки, включая нормальный cooldown после её применения.

Ограниченный native trace разделяет D03 (DPS, длительность, состав, ресурсы), D04 (фактическая цель, угрозы, резерв 6 секунд) и D05 (кастер, получатель, доступный навык, нулевой HP credit). Для Heal используется существующий stock HP-restored packet с положительной суммой. Headless outbound сохраняет только последний receipt текущего native owner получателя. Observer связывает его с замеченным cast, native target list и MP debit того же кастера. Поддержаны одиночные и групповые Heal. Принудительное размещение human witness удалено. F8 использует отдельный обычный cast на сохранённом месте существующего TEST130 human. Pre-CUT authority mismatch отклоняется до вызова восстановления baseline.

На момент подготовки 70 и 72 проходят расписание `SLEEPING → BACKGROUND → ACTIVE`. По read-only SQL и production-календарю ближайшее ожидаемое общее ACTIVE начинается в 14:17 по Кишинёву. Перед bootstrap требуется свежая read-only сверка фактической готовности обоих участников. Ранее подготовленные SQL 2/3/9 и F7 PASS сохранены. Итоговый повторный targeted MODEL/Party/Combat batch 237/237, stock F8/evidence suite 14/14. JAR и native evidence записываются отдельно.

## Итог согласованной native-попытки

В 14:17 по Кишинёву read-only TEST130 подтвердил `eligible=[70, 72]`, `ACTIVE`, согласие, отсутствие блокирующих обязательств и active claims. Оба актёра уровня 76 на сохранённом якоре `generated.farm.38520d85788548d726048a27.anchor`. Общий интервал 72–80. MANIFEST 13/13, SHA256SUMS 14/14. Перед сборкой targeted MODEL/Party/Combat 237/237 и stock F8/evidence MODEL 14/14. Одна clean Ant JAR с `ea0e202e2` за 23 секунды. SHA-256 `a71ad97ce44cb9afcff51c8d5e6416d2426453c9b7a8cd0a04398fdf60879d27`, 11 335 480 байт. Побайтово проверены все 4742 class entries без пропусков, лишних файлов или расхождений.

В 14:17:50 запущен единственный GameServer PID 69048. Сервер загрузился за 29 секунд с principal `phantom130_d930bd6491f5@127.0.0.1` и схемой `l2jmobiush5_phantom_test_130`. Pre-CUT инспекция профиля 70 остановилась на `NATIVE130_PREFLIGHT_AUTHORITY_HASH_70`. Сохранённый knowledge `41edd0ded7c34e30db4c844c4e5efdafd7ed3be5678a801fb8ffd32f9b05c999`, загруженный `42423479059829b87030574239bd692cb93e5e2ec8e5e974baaf95825ced5b93`. Topology, progression и commerce совпали. Роль в pre-CUT отчёте `pending.native_role`. Runtime `operatorEnable` вернул `START_FAILED` до создания Phantom owner. Проверку exact hash я поставил перед прежним canonical recovery, чтобы исключить recapture при несовпадении authority. Это решение остановило попытку до проверки штатного восстановления. Подкомпонент, изменивший knowledge hash, на этой попытке не измерен.

| Игровая проверка | Фактический результат |
|---|---|
| До CUT | `PREFLIGHT_AUTHORITY_HASH_70`. Common sources, stock NPC и маршруты в этой попытке не дошли до проверки |
| D03 | NOT_RUN. Нет materialized Player и фактического aggregate DPS/MP/ammo |
| D04 | NOT_RUN. Нет pull, наблюдаемой цели моба, замера угроз или переключения агрессии |
| D05 | NOT_RUN. Нет штатного Heal cast, HP-restored receipt и MP debit. Human F8 на этом JAR также NOT_RUN |
| Native Party, бой, reward, STORE | 0 новых подтверждённых игровых результатов |
| SameDB/sameJAR restart | 0. Условие полезной первой вертикали не выполнено |

Stock STOP завершился в 14:18:38, Hikari закрыт, master config вернулся к `False`. Обёртка получила exit 0 от stock shutdown, результат теста зафиксирован `native130.failed.69048`. CUT и agreement receipts отсутствуют. Postflight подтвердил protected207 exact, Geo203 exact, собственные JVM/listeners 0. Повторный read-only SQL прошёл с тем же principal, а 14 наблюдаемых строк eligibility/claims/positions совпали с preflight. Это выборочная SQL сверка. Stock GameServer при shutdown записал штатное состояние своих менеджеров в TEST130. Фикстурные правки HP/MP/EXP/SP, предметов, позиций и Goal не выполнялись.

Evidence seal `approved-native-attempt-manifest.json` содержит SHA-256 12 файлов. SHA-256 самого manifest `ddaff76e8ed7667d39dda8ad35be9317a0db9ab4ac413820c047855727620931`. В `task130/runtime` сохранены `native-20261109-121750.stdout.log`, `.stderr.log`, `.process.json`, `.exit.json` и markers started/failed. В `task130/evidence` сохранены JAR parity и OS/SQL postflight. Счётчики 1 clean JAR / 1 GameServer / 0 restart. Native budget израсходован. FULL130=false. TASK130 PARTIAL, TASK131–136 не запускались, FULL 0/7.

Команды этапа: `git rev-parse HEAD`, `git symbolic-ref --short HEAD`, `git status --short`, `git ls-remote --heads origin wip/coordination-130-preserved feature/phantom-world wip/task129-preserved-20261106`, `git diff --stat -- <scope>`, `git -c core.whitespace=cr-at-eol diff --check -- <scope>`, `git add --pathspec-from-file=../evidence/approved-native-final-paths.txt`, `git diff --cached --name-only`, `git -c core.whitespace=cr-at-eol diff --cached --check -- <scope>`, `git commit -m "Verify native TASK130 combat readiness and Heal evidence"`, `git push origin HEAD:refs/heads/wip/coordination-130-preserved`. Docs seal публикуется отдельным scoped commit и таким же WIP push. Сборка `ant -f L2J_Mobius_CT_2.6_HighFive/build.xml jar`. Запуск `Test-Coordination130.ps1 -Action native -RuntimeRoot <owned runtime> -Fixture original`, postflight `Test-Coordination130.ps1 -Action postflight -RuntimeRoot <owned runtime>`.

## Разбор pre-CUT и подготовка runner после отказа

Read-only сравнение обоих runtime-датапаков по SHA-256 охватило 25 497 файлов. Единственная разница в `phantoms/party/high-five-party-roles-v1.xml`. Knowledge builder читает другие источники. В архивном JAR `16699c6c…` и текущем `a71ad97c…` побайтово совпали 59 class entries из knowledge package и stock SpawnData, SpawnTable, Spawn, DayNightSpawnManager. Архивный старт 8 ноября в 20:12 загрузил 685 night creatures. Текущий старт 9 ноября в 14:18 загрузил 232 day creatures. Оба загрузили 42 407 stock spawns, 10 461 NPC и 21 custom NPC. Совпадение исходных файлов и class entries сужает поиск до загруженных фактов. Для attribution конкретного подкомпонента нужны subhashes, которых эти логи не содержат. Сверки сохранены в `authority-runtime-datapack-compare.json` и `authority-compiled-catalogue-compare.json`.

Исправлен test runner. До подготовки он пишет исходный authority и сверяет topology, progression и commerce. Известное расхождение knowledge допускается до существующего `preparePreflightAuthority`, который использует штатный canonical recovery. После подготовки требуется exact hash всех компонентов. SQL witness сравнивает все столбцы characters за исключением `online`, `onlinetime`, `lastaccess`, а также полные rows items и character_skills. Виталы, позиция, XP/SP, предметы и навыки входят в сверку. Goal, Catchup и прочие защищённые компоненты сохраняют прежние fences. Любое расхождение блокирует CUT.

Правка затрагивает test runner и отчёт. `javac --release 25` для runner и MODEL130 53/53 прошли. Provenance сохранённого JAR `a71ad97ce44cb9afcff51c8d5e6416d2426453c9b7a8cd0a04398fdf60879d27` повторно подтверждён. Исправленный runner подготовлен для отдельного решения владельца по дополнительному bootstrap на этом JAR. Игровая приёмка остаётся открытой, счётчики 1/1/0.

Git-публикация scope через `git add --pathspec-from-file=../evidence/authority-precut-recovery-paths.txt`, `git diff --cached --name-only`, `git -c core.whitespace=cr-at-eol diff --cached --check -- <scope>`, `git commit -m "Restore guarded TASK130 pre-CUT authority recovery"`, `git push origin HEAD:refs/heads/wip/coordination-130-preserved`.

## 2026-11-09 — результат дополнительного GameServer

Исходный WIP `f472c16a7b83f89de21511b0c90ba591f42e7dd3` подтверждён локально и на GitHub. Затем опубликован runner-only `7aac8ace409b5a2585806501fb31948c831edcf6`. Сверка `java`, `dist/game` и `build.xml` с исходником JAR `ea0e202e2` не выявила различий. Clean JAR сохранил SHA-256 `a71ad97ce44cb9afcff51c8d5e6416d2426453c9b7a8cd0a04398fdf60879d27` и подтверждённую ранее parity 4742/4742. Новых JAR 0.

Только два файла runner компилировались `javac --release 25 -encoding UTF-8` против этого JAR. На bootstrap оба класса загружены из `build/phantom-test/bin`. SHA-256 Native `b3844f16639b263ed0d83f80887532ae2fab292c932d443d96eb3259dcbd818b`, Integration `05ccea14fcfb55bcbe62466f600b73b9934a8028fafd9f172d6d9a9d1289b66b`. `javap -c -p` показал `emitAuthority` (599), `preparePreflightAuthority` (1033), `inspectAuthority` (1357). Финальный MODEL130 53/53, exit 0. Targeted MODEL/Party/Combat 237/237, F8 static 14/14, SQL 2/3/9 и F7 ранее прошли для того же production.

G0 OS identity и SQL provenance подтвердили MariaDB PID 8692, principal `phantom130_d930bd6491f5@127.0.0.1` и схему `l2jmobiush5_phantom_test_130`. Geo203 и protected207 совпали до запуска. Read-only SQL подтвердил ACTIVE, consent, отсутствие блокирующих obligations и claims у 70/72. В режиме `authority` canonical Player STORE/readback принят. Witness отклонил семь запрещённых изменений unmanaged SQL-фикстуры 974 — HP, MP, позицию, EXP, SP, предмет и навык. Goal/Catchup/child и pending-intent отрицательные проверки прошли. Полные witnesses шести managed-профилей до и после SQL-пробы совпали. GameServer в этой пробе 0.

Bootstrap `native-20261109-150835`, PID 54332, запущен единожды в `original`. Канонический recovery через штатного владельца выполнил STORE/readback для 70 и 72, с нулём продвинутых исторических интервалов. Оба knowledge hash обновились с `41edd0ded7c34e30db4c844c4e5efdafd7ed3be5678a801fb8ffd32f9b05c999` до текущего `13f7469d0a8018c54ffc42b776bd54e80b900661635309a518306bd61cdcb0a9`. Topology, progression и commerce совпали до и после. Причина различий knowledge между GameServer остаётся **UNKNOWN**. Разница day/night в логах не устанавливает причину.

Полные `preflight130.54332.before.txt` и `.after.txt` сохранены вне Git, оба по 101 791 байту. SHA-256 соответственно `3bd1006c125ffb4bf15967352b992f959bdca17c15f71606261389b378853477` и `62f335c808464225da4a4a369f197bfbd13c77691f6e2aa58b68723ee3fb89c7`. Изменились только lifecycle-столбцы штатного Player STORE и строки `background.state`/`background.native-context` профилей 70/72. Goal/Catchup, прочие компоненты, HP/MP, EXP/SP, позиции, предметы и навыки совпали. Никаких фикстурных изменений шести профилей и ослабления F7/Background. Evidence manifest `second-native-attempt-manifest.json`, SHA-256 `a67146f71cd6ba9339d4153e83d43c409d4c9ed95fd7106b5f90672cbab5a09b`.

| Стадия | Результат |
|---|---|
| A | MODEL PASS. Group-only ±4, пересечение и 2–9. Solo regression PASS. |
| B | MODEL PASS. В native pre-CUT planner вернул по 53 PARTY-source для 70/72. |
| C | Isolated SQL PASS, включая separate-JVM 2/3/9 и F7. Canonical authority recovery прошёл на текущем JAR. |
| D01/D02/D06/D07 | Native gameplay checks NOT_RUN. Уровень первого кандидата 75 входит в [72,80], его live spawn и фактическая цель не проверялись. |
| D03 | NOT_RUN. Нет игрового roster snapshot, замера совокупного DPS, HP/MP и ammo. |
| D04 | NOT_RUN. Нет pull, фактического target/AI наблюдения или проверки 6-секундного резерва в бою. |
| D05 | NOT_RUN. Нет native Heal, подтверждённого HP effect или MP debit. Final-JAR human F8 NOT_RUN. |
| E | Pre-CUT REFUSED. Новый SQL LogicalParty, игровая materialization, stock Party, урон, kills, earned EXP/SP/loot, Heal и игровой STORE — 0 подтверждённых. Native Spoil и restart NOT_RUN. |

Точный отказ `NATIVE130_NO_LAWFUL_FEASIBLE_SCENE`. Проверен единственный кандидат `21320@generated.farm.4475282a85a97a4b396b967b.anchor`, Hot Springs Yeti, уровень 75. От сохранённой позиции профиля 70 в Wall of Argos `(170156,-36726,-3376)` до Hot Springs `(149140,-108937,-2536)` около 75 200 единиц. Native route query вернул `NO_PATH_WITHIN_SEARCH`, `searchLimits=UNKNOWN`. Stock-spawn проверка этого кандидата и физическая проверка остальных кандидатов не выполнялись.

Штатный STOP завершился в 17:09:21 EET, exit 0 от shutdown. Маркер `native130.failed.54332` фиксирует отказ. CUT и новый durable claim отсутствуют. Postflight exit 0 подтвердил собственные JVM/listeners 0, protected207 и Geo203 exact, тот же TEST130 principal и прежнюю пригодность/claims. Штатные менеджеры сервера сохранили своё состояние в TEST130 при STOP. Итого за продолжение **1 clean JAR / 2 GameServer bootstrap / 0 restart**. TASK130 PARTIAL, TASK131–136 NOT_STARTED, FULL 0/7 → 0/7. Native-повторы исчерпаны.

### Системная причина отказа и пересмотр приёмки

Runner передаёт физическую проверку в `discover(...).filter(physical)`. Координатор в MemoryStore возвращает первый логический agreement. Отказ внешнего фильтра отбрасывает его и завершает весь preflight. Этот дефект runner я пропустил. MODEL покрывал отказ при отсутствии физического proof, но не случай «первый отказ, второй допустим». Native лог доказывает ровно одну Geo-попытку и не позволяет судить о доступности остальных источников или боеспособности roster.

Вторая граница проходит между logical route и физическим исполнением. Production planner допускает graph itinerary и ingress. Runner запрашивает единый PathFinding от сохранённой позиции до конечного FARM-якоря. Такой запрос не подтверждает и не опровергает весь штатный itinerary. Существующий уровень доказательства `NO_PATH_WITHIN_SEARCH` следует сохранять для конкретного коннектора. Нет оснований переводить его в глобальную недостижимость.

Дешёвый положительный контроль для пересмотренной проверки есть в production XML. Обе сохранённые позиции уже лежат на якоре `generated.farm.38520d85788548d726048a27.anchor` в Wall of Argos. Его spawn polygon `godard26_2516_28` содержит NPC 21300/21301/21302 уровней 72/72/73. Это кандидат каталога внутри пересечения [72,80]. Live spawn, Geo-коннектор, authoritative source admission и Combat ещё предстоит доказать.

1. До любого нового решения о native сделать bounded read-only candidate inventory от реального SQL anchor через production loader. Использовать существующий hermetic `PhantomTravelGeoProbe` для коннекторов Geo203, без GameServer и SpawnData. Отдельно записать local/graph/paid legs, статус маршрута и факт configured spawn. Подтверждение live spawn оставить World.
2. В существующем MODEL закрепить два случая. Первый общий источник недоступен, второй должен пройти с теми же профилями. При исчерпании бюджета runner выдаёт typed WAIT с причиной и nextDue. Сверить witness, чтобы отклонённый кандидат не записал durable agreement. Пропускать физический admission до выбора единственного agreement в рамках штатных page/cap, без фиксирования конкретного NPC.
3. Перед запросом любой будущей native-приёмки предъявить минимум один реально проверенный коннектор к законному общему источнику и доказательство, что runner проверяет фактический itinerary по шагам. В самой приёмке live spawn, законная materialization и stock Party предшествуют D03–D05, затем полезный kill, earned rewards, штатный Heal при наличии хилера, STORE и clean STOP. Этот пересмотр зафиксирован как план. Изменения production/runner после отказа не вносились.

Git выполнялся в независимом clone по явному разрешению на bounded source audit и scoped WIP push. Команды с `-c safe.directory=<independent clone>` — `git rev-parse HEAD`, `git status --short`, `git rev-list --count 8140167705e6a6e76f370e3aa772b50f909b4d37..HEAD`, `git ls-remote --heads origin feature/phantom-world wip/task129-preserved-20261106 wip/coordination-130-preserved`, `git ls-remote --exit-code origin refs/heads/wip/coordination-130-preserved`, `git diff --stat`, `git diff --name-only 8140167705e6a6e76f370e3aa772b50f909b4d37 HEAD`, `git diff --name-only ea0e202e26755c5ba79c096c42007e61cf3e1c5e HEAD -- L2J_Mobius_CT_2.6_HighFive/java L2J_Mobius_CT_2.6_HighFive/dist/game L2J_Mobius_CT_2.6_HighFive/build.xml`, `git diff -- <два файла runner>`, `git -c core.whitespace=cr-at-eol diff --check -- <scope>`, `git add -- <два файла runner>`, `git diff --cached --name-only`, `git commit -m "Verify TASK130 canonical recovery witness before native lane"`, `git push origin HEAD:refs/heads/wip/coordination-130-preserved`. Отчёт фиксируется отдельным `git add -- <три файла отчёта>` и `git commit -m "Record TASK130 route preflight refusal and acceptance strategy"` с тем же WIP push. Перед отчётным коммитом 7 новых коммитов от исходного WIP.

Mojibake-маркеры в изменённых файлах проверены. Совпадений нет.

Escaped Cyrillic в изменённых файлах проверен. Совпадений нет.

## Geo-проверка контрольного источника после STOP

На WIP `2d986705bc81914ff43237a8e7272402ba66c561` существующий `PhantomTravelGeoProbe` запущен в режимах `--heights` и `--waypoints` против того же JAR и Geo203. Оба процесса завершились с exit 0. В журналах инициализации есть GeoEngine и PathFinding. GameServer, DatabaseFactory, SpawnData, SpawnTable, DoorData и FenceData не инициализировались. Проверка читала Geo203 и статические XML столкновений, без SQL и изменений персонажей.

Высота сохранённого якоря 70/72 `(170156,-36726,-3376)` совпала с Geo. Четыре контрольные точки подтверждены внутри штатного Wall of Argos spawn polygon `godard26_2516_28`. Для каждой получены `DIRECT_VALIDATED` и `STATIC_XML_CLEAR`, длины 191, 518, 565 и 287 единиц. XML задаёт NPC 21300 (уровень 72, 2), 21301 (72, 3) и 21302 (73, 1). Все входят в [72,80].

Запрос к тому же Hot Springs якорю вернул `NO_PATH`. Отдельная диагностика `DIRECT_BLOCKED_PATHFINDER_OUT_OF_RANGE` показала размер буфера 9090 при настроенном максимуме 500 для единого прямого запроса. Это объясняет ограничение этой проверки. Допустимый itinerary из нескольких сегментов к Hot Springs не проверялся.

Четыре коннектора дают положительный Geo-контроль для сценария «дальний кандидат отклонён, локальный продолжает проверку». Остаётся проверить admission локального источника в фактическом production planner, живой spawn, D03–D05 и игровую вертикаль. Runner и production не менялись. Причина изменения knowledge hash остаётся UNKNOWN. Счётчики JAR/bootstrap/restart по-прежнему 1/2/0. FULL130=false, TASK130–136 FULL 0/7.

Внешний evidence seal `task130/evidence/argos-geo-readonly/manifest.json` имеет SHA-256 `6d8d597016ce67d14987512441f54cdf1e3051a437d6d4278d478d9212976e4b`. Он привязывает вывод probe к Geo203 manifest, production XML, probe inputs и class-init logs. В Git публикуется только результат. Команды аудита `git rev-parse HEAD`, `git status --short`, `git diff -- <два файла отчёта>`, `git -c core.whitespace=cr-at-eol diff --check -- <два файла отчёта>`, `git add -- <два файла отчёта>`, `git diff --cached --name-only`, `git -c core.whitespace=cr-at-eol diff --cached --check -- <два файла отчёта>`, `git commit -m "Record TASK130 isolated Argos connector proof"`, `git push origin HEAD:refs/heads/wip/coordination-130-preserved`, `git ls-remote --exit-code origin refs/heads/wip/coordination-130-preserved`. Все Git-команды разрешены для bounded audit и scoped WIP публикации.

Mojibake-маркеры в изменённых файлах проверены. Совпадений нет.

Escaped Cyrillic в изменённых файлах проверен. Совпадений нет.

## Сверка одного источника между preflight, Scheduler и Combat

Read-only XML сверка 15 topology shards нашла один FARMING_AREA для `godard26_2516_28`. Совпали путь `data/spawns/Goddard/WallOfArgos.xml`, инстанс 0, Z-границы и порядок всех пяти вершин. Единственный FARMING-якорь совпал с сохранённым `generated.farm.38520d85788548d726048a27.anchor`, без ограничения `npcId`. Квадрат максимальной дистанции до вершины — 2 605 000, то есть примерно 1614 единиц по XY. Это внутри 2000 для `PhantomGameKnowledgeBuilder.exactTerritoryNode`. Маршрут из сохранённого якоря к тому же якорю по `PhantomNormalGatekeeperTravel.route` — пустой itinerary. Evidence `argos-geo-readonly/topology-correlation.log`, SHA-256 `cd78f50758848a9d619b8985cab840e0bdf360239e4f398fb6adf7d3d1b5ea06`. В сохранённом native stdout только первые четыре из 53 sources, поэтому точное присутствие локального NPC@anchor в выдаче planner остаётся непроверенным. Заново production loader не запускался, поскольку `L2jTopologyValidationBackend.spawns` инициализирует SpawnData.

Сверка call sites уточнила критерии пересмотренной приёмки.

| Seam | Фактическое различие | Риск |
|---|---|---|
| Состав кандидатов | Runner вызывает `rendezvousSources`. Scheduler через `previewLogicalRendezvous` вызывает `rendezvousSourcesForRole`, где SPOILER может получить другой порядок. | Один и тот же набор roster facts может дать разные источники. Для 70/72 роль SPOILER в этой lane не проверялась. |
| Выбор agreement | Runner отбрасывает результат `discover` внешним Geo-фильтром. Scheduler вызывает `discover` со штатными predicates. После CUT runner требует `CURRENT_ORDINARY_SOURCE_MATCHES_PREFLIGHT`. | Простой перебор второго источника в runner оставляет возможность, что Scheduler запишет первый. Контракт требует связать pre-CUT proof и фактический source. |
| Проверка live target | В preflight и `ACTUAL_SHARED_TARGET` жёстко задан радиус 1200. Production `partyNativeTarget` использует Combat 2000, ограничение по Z и проверку связи spawn/anchor. | Живой моб в этом же polygon может находиться между 1200 и 1614 единицами от якоря. Источник может пройти production и ошибочно провалить runner. Одна лишь замена 1200 на 2000 не восстанавливает остальные условия production. |

Следующая дешёвая проверка должна связать штатный порядок role-aware planner, itinerary и фактический NPC@anchor до CUT. Сначала можно сохранить решение Scheduler и проверить его первый источник по штатным сегментам. Общий bounded Geo-допуск до CAS в Scheduler затрагивает архитектуру logical discovery и требует решения владельца. Любой вариант должен различать NO_PATH отдельного коннектора и полный отказ itinerary, применять тот же production target predicate и сохранять `WAIT_REPLAN` для расхождения source, как требует ARCHITECTURE. Продуктовый код и runner не менялись. Новых SQL, JVM GameServer, JAR и restart нет.

Точные Git-команды этого дополнения — `git rev-parse HEAD`, `git status --short`, `git diff -- <RESULT.md>`, `git -c core.whitespace=cr-at-eol diff --check -- <RESULT.md>`, `git add -- <RESULT.md>`, `git diff --cached --name-only`, `git -c core.whitespace=cr-at-eol diff --cached --check -- <RESULT.md>`, `git commit -m "Audit TASK130 preflight and scheduler source parity"`, `git push origin HEAD:refs/heads/wip/coordination-130-preserved`, `git ls-remote --exit-code origin refs/heads/wip/coordination-130-preserved`. Разрешение ограничено scoped WIP audit и публикацией.

Mojibake-маркеры в изменённых файлах проверены. Совпадений нет.

Escaped Cyrillic в изменённых файлах проверен. Совпадений нет.

## Согласованный общий допуск маршрутов до CAS

Вариант 2 согласован владельцем. Bounded scope охватывает 13 Java-файлов и этот отчёт. Четыре связанных файла добавлены после независимого ревью для атомарных read guards в Party CAS и общих нормализованных GK endpoints. Переиспользованы role-aware `previewLogicalRendezvous`, `PhantomLogicalPartyCoordinator`, topology snapshot, `PhantomNormalGatekeeperTravel`, `PhantomVisibleFarmTravel` и штатный WALK episode planner.

- Runner и Scheduler читают managed-профили из одного production store, строят кандидатов в `PhantomHistoricalBackgroundService.previewLogicalRendezvous` и вызывают один `previewDiscovery`. Он выбирает exact `npcId@anchorId` и roster до SQL-записи. Scheduler использует штатный CAS с атомарными Goal, Background, Catchup и claim guards. NativeContext проверяется в той же транзакции как точная строка COMPLETED или точное отсутствие строки. Runner выполняет read-only выбор. Первые 64 неподходящих маршрута не расходуют лимит принятых источников. PARTY использует полный индекс зон NPC и сохраняет exact source при предложении Goal. Graph proposal использует проверенный itinerary от committed anchor, а альтернативный ingress требует native path.
- Структурный itinerary использует сохранённую позицию, топологию и NORMAL GK catalog. WALK за штатным лимитом и неавторизованный ingress не допускаются. Ingress от точки в авторитетной области помечен отдельно и требует физического подтверждения. Отсутствие графа не превращается в доказанную невозможность маршрута. Для `WAIT_ROSTER`, `WAIT_COMMON_ROUTE` и `WAIT_DISCOVERY_BUDGET` назначается следующая проверка через 1000 мс. Курсоры сохраняют движение по страницам.
- Отдельный физический этап runner проходит ingress, WALK, подход к GK и выход из landing. Используются штатная доступность GK, те же нормализованные endpoints и bounded WALK episodes. От канонической точки FARM для живого моба применяется production `partyNativeTarget` и общий диапазон уровней. При отказе физического proof сохраняется причина на выбранном источнике. Маркер `NATIVE130_PREFLIGHT_PASS` требует совпадения сохранённого решения Scheduler по источнику, лидеру и roster.

**RED→GREEN.** Исходный код пропускал WALK 110 000 при штатном лимите 100 000 и помечал ingress от несвязанной позиции как reachable. На финальном javac overlay MODEL130 **56/56**, Party lifecycle **34/34**, Party tactics **10/10**, Combat action ownership **37/37**, Combat ownership **17/17**, Navigation core **51/51**, stock F8/evidence **14/14**. Все семь команд exit 0, всего 219 проверок. Отдельный Solo/Party planner runner также exit 0. Смена маршрута до CAS не создала durable claims. Контроли подтверждают следующий лидерский слот после bounded wait, точный ingress при устаревшем ярлыке, локальный WALK при отклонённом графовом шаге и локальный источник после 64 отказов. Сверка 137 XML с production типами MONSTER/RIFT_INVADER показала максимум 1408 шаблонов в полосе ±4 при ёмкости 1856. По текущему корпусу восьми страниц достаточно.

**SQL.** G0 подтвердил MariaDB PID 8692. Principal и схема совпали с TEST130. В отдельных JVM `group-pve-route`, `group-pve-route-resume`, `group-pve-route-close` для roster 2/3/9 read-only preview и Scheduler CAS выбрали `20101@farm.fixture` с одинаковым составом. Первый `20102@farm.unconfirmed` не получил claims или Goal-записей. CAS отклонил устаревший Goal guard и появившийся после preview NativeContext без частичных claims. Временные guard rows удалены только у выделенной unmanaged-фикстуры. Точный readback после отдельного JVM прошёл, fixture claims закрыты. Полный witness 69/70/72/85/79/86 совпал во всех трёх запусках, SHA-256 `6b26936df5430bde6490b708a51d38e98b75f367634e31311ede8c9628a7c61c`. `authority-f7` exit 0. Read-only `recover` подтвердил exact managed page `[69,70,72,79,85,86]` и доступную пару 70/72.

**Geo.** Wall of Argos остаётся контрольным примером. Пустой itinerary к сохранённому якорю и 17 шагов к Hot Springs подтверждены существующим Java route fixture с конфигом production. В Hot Springs диагностированы 16 WALK и один NORMAL GK. Отдельный Geo-probe принял 17 из 18 WALK/GK-коннекторов с runtime-лимитами. Последний сырой маршрут вернул `ROUTE_BUDGET_EXCEEDED`. Существующий episode planner дошёл до точной конечной точки двумя эпизодами 51+11 waypoints. Оба нормализованных GK-коннектора, включая landing Z −2563, дали `DIRECT_VALIDATED`. Это герметическое доказательство геометрии с XML collision, без water/live NPC/Player проверки. 9300 Adena — stock quote в XML. Штатный Phantom-тариф на этот GK равен 0. Списаний в пробе не было.

**Граница native.** Доказана эквивалентность выбора при одинаковых входных facts, включая реальные SQL CAS и source/roster readback на фикстурах. Точный живой источник для 70/72 и текущее production-решение не проверены. После STOP нет runtime SpawnTable/knowledge snapshot. Сохранённый JAR относится к старому production, а новый JAR и bootstrap в этом срезе запрещены. A–C в пределах MODEL/SQL/Geo PASS. D03, D04, D05, human F8 и E gameplay NOT_RUN. Причина предыдущего изменения knowledge hash остаётся UNKNOWN. Счётчики 1 JAR / 2 bootstrap / 0 restart. TASK130 PARTIAL, TASK131–136 NOT_STARTED, FULL 0/7.

Geo203 и protected207 совпали. TEST130 JVM/listeners 0. SHA-256 существующего JAR остался `a71ad97ce44cb9afcff51c8d5e6416d2426453c9b7a8cd0a04398fdf60879d27`. Локальный evidence seal `shared-party-route/manifest.json` SHA-256 `d44d154e6ed7f5ee9fdc7184132251f9c6e3f3923dffb1e8a5e3ff6bc1f220e2`. Seal маршрута Hot Springs `hot-springs-itinerary/manifest.json` SHA-256 `322898ee1fecd9fa28033717c85d3601a64e5fb2223beb7769964890331a3ff9`.

Git использовался в разрешённом независимом clone. Выполнены `git rev-parse HEAD`, `git status --short`, `git diff --stat`, `git diff -- <точный scope>`, `git -c core.whitespace=cr-at-eol diff --check -- <точный scope>`, `git add -- <13 Java и RESULT.md>`, `git diff --cached --name-only`, `git -c core.whitespace=cr-at-eol diff --cached --check -- <точный scope>`, `git commit -m "Share TASK130 bounded party route admission before agreement"`, `git push origin HEAD:refs/heads/wip/coordination-130-preserved`, `git ls-remote --exit-code origin refs/heads/wip/coordination-130-preserved`.

Mojibake-маркеры в изменённых файлах проверены. Совпадений нет.

Escaped Cyrillic в изменённых файлах проверен. Совпадений нет.

## Отказ несовместимого launcher до bootstrap

`Test-Coordination130.ps1 -Action artifact-check -RuntimeRoot <owned runtime>` читает JAR и оба runner-класса из фактического classpath. `javap` подтверждает origin test-классов в `build/phantom-test/bin` и точные JVM-дескрипторы двух accessor-вызовов `PlanningInspection`. Этот же gate стоит в `native` и `restart` до создания G0 request, копирования JAR и `Start-Process`. Проверка linkage ограничена требуемыми символами. Полная сверка нового JAR с исходниками остаётся отдельным условием будущей native-попытки.

RED вернул `ParameterArgumentValidationError`, потому что read-only режима в launcher не было. После правки сохранённые JAR и runner дали ожидаемый exit 1 с `TEST130_SHARED_ROUTE_LINKAGE_REFUSED` для `jar.decision`, `runner.decision`, `jar.managedPartyPage` и `runner.managedPartyPage`. Имена каталогов G0 и SHA-256 всех файлов process/exit совпали до и после. Отдельная проверка актуального javac overlay подтвердила оба объявления и вызова с точными дескрипторами. Логи `shared-party-route/launcher-artifact-red.log`, `launcher-artifact-green.log` и `launcher-artifact-guard-verdict.log` сохранены вне Git.

При расхождении runtime eligibility текущий runner сравнивает реально выбранные Scheduler source и roster после CAS. Отказ в этой точке ещё не доказывает отсутствие новых claims. Штатное решение Scheduler для живой пары 70/72, D03–D05, фактические Party/PvE и STORE остаются непроверенными. В этом срезе изменений игрового кода, SQL-запусков, JAR-сборок, GameServer и restart не было. Общие счётчики 1/2/0 и FULL 0/7.

Git ограничен независимым WIP. Команды с `-c safe.directory=<independent clone>` — `git rev-parse HEAD`, `git status --short`, `git diff -- <launcher и RESULT.md>`, `git -c core.whitespace=cr-at-eol diff --check -- <launcher и RESULT.md>`, `git add -- <launcher и RESULT.md>`, `git diff --cached --name-only`, `git -c core.whitespace=cr-at-eol diff --cached --check -- <launcher и RESULT.md>`, `git commit -m "Reject stale TASK130 artifacts before native launch"`, `git push origin HEAD:refs/heads/wip/coordination-130-preserved`, `git ls-remote --exit-code origin refs/heads/wip/coordination-130-preserved`.

## Повторная проверка роли перед CAS

`PhantomLogicalPartyCoordinator.commitDiscovery` повторно проверяет durable readiness всех выбранных участников. Транзакционные guards проверяют версии Goal, Background, Catchup и NativeContext, а потенциальная роль зависит также от актуальных изученных навыков. При изменении роли штатный coordinator отказывается от CAS и Scheduler назначает `WAIT_DISCOVERY_CHANGED` с повторной проверкой через 1000 мс. Повторное чтение роли выполняется только для уже выбранного состава из 2–9 участников.

В целевом MODEL SQL-компоненты остались прежними, а проверка роли между preview и commit перестала проходить. RED воспроизвёл ошибочный commit, 56/57 и exit 1. GREEN отказал без группы и claims. После восстановления актуальной роли прошёл обычный guarded commit. MODEL130 57/57, Party lifecycle 34/34, Party tactics 10/10, все exit 0. Проверка использовала отдельные javac class files и существующий test launcher. SQL, JAR, GameServer и restart в этом срезе не запускались.

Проверка startup выявила отдельное несовпадение входов. Runner фильтрует по `requiresMaterialization()`, а штатный Scheduler берёт календарно online READY-профили без BUSY. Штатный Presence считает `BACKGROUND` online. Совпадение общей функции выбора само по себе не гарантирует совпадение roster при этих условиях. Согласование проверки штатного предложения до CAS остаётся открытым. Native D03–D05 и E остаются NOT_RUN, счётчики 1/2/0.

## Групповой вклад заклинания за пределами Solo cast limit

D03 использовал длительность `PhantomBackgroundModel.castEncounter` для оценки вклада каждого кастера. Если кастеру требовалось больше 256 кастов для одиночной победы, Solo-симуляция возвращала `cast_limit` и нулевую длительность, из-за которой Party пропускала его вклад. `L2jPhantomPartyBackend` теперь использует общую оценку урона за один каст и интервала его повторения. Бюджет MP считается по числу действий за прогнозируемое групповое время. Сохраняются проверки допустимости магического эффекта, стоимости действий, наблюдаемой угрозы и фактической готовности. Solo сохраняет прежние формулы, предел 256, проверки ресурсов и результат симуляции.

RED в существующем MODEL runner повторил старый способ расчёта, 57/58, exit 1. GREEN использовал ту же оценку одного каста, что и Party backend. В контрольном сценарии Solo по-прежнему получает `cast_limit`, а восемь кастеров дают 72 DPS суммарно, прогноз около 41,7 с и 42 каста на участника при достаточном MP. MODEL130 58/58, Background MODEL 10/10, Party lifecycle 34/34, Party tactics 10/10, Combat action ownership 37/37. Все exit 0, всего 149. Изменённые Java-классы скомпилированы в отдельный overlay. Реальный D03 на Player и Monster, D04, D05 и игровая вертикаль E остаются NOT_RUN. SQL, JAR, GameServer и restart в этом срезе не запускались.

Существующий DB-free сценарий `PhantomOffscreen092BChecks combat` прошёл на прежних и новых классах. Строки Solo witness точно совпали, включая 11 кастов за 53 669 мс, 1947 MP, урон, drain и остаток HP. Логи `shared-party-route/cast-solo-092b-baseline.log` и `cast-solo-092b-current.log` сохранены вне Git.

## Одно штатное предложение перед CAS

Согласованный вариант использует существующий `PhantomAutonomousFarmParty` и его coordinator. Runner передаёт в `PlanningInspection.partyOffer` только проверку. Он получает точные `profileId`, роли, уровни, `npcId@anchorId` и SQL guards предложения. Отдельный вызов выбора Runner удалён. Каталог, роль, расписание, consent и ownership проверяются штатным владельцем. Календарный BACKGROUND учитывается в логическом выборе. До регистрации Presence проверяются сохранённое расписание, состояние и действующие busy owners. Обычный Scheduler дополнительно проверяет текущий зарегистрированный Presence. Поддержанные predecessor catalog проходят тот же production loader.

Runner проверяет именно предложенный состав, общий диапазон уровней, WALK/GK/ingress по сегментам, отсутствие Player в World и настоящий stock spawn. Физический отказ сохраняет точные source, roster и причину. Владелец пробует следующий структурно допустимый источник в том же порядке. Лимит восемь физических проверок на окно, общий deadline Runner 30 секунд. `WAIT_PHYSICAL_PROOF` и `WAIT_PHYSICAL_PROOF_BUDGET` содержат `nextDue`. Обычные тики Scheduler вызывают тот же discovery без физической проверки и сохраняют fair bounded paging.

После успешного proof передаётся тот же объект `Discovery`. Владелец повторно проверяет уровень, роль, consent, расписание, маршрут и блокирующие obligations. Goal, Background, Catchup, Population и имеющийся NativeContext сверяются внутри штатного CAS, как и версии claims. При изменении guard транзакция оставляет группу и claims без изменений, startup отказывает с `REPLAN_DISCOVERY_CHANGED`. Сверка SQL readback и маркеры agreement/CUT стоят после успешного CAS и перед первым тиком. Разрешённый канонический recovery остаётся отдельным этапом с before/after witness. Restart читает существующий groupId, без нового formation CAS.

**Дешёвое доказательство.** MODEL130 60/60, Party lifecycle 34/34, Party tactics 10/10, Population schedule 3/3, Living Presence 3/3, Combat action ownership 37/37 и F8 static 14/14. В MODEL подтверждены тот же объект предложения после физического отказа первой цели, защита consent и предел восьми попыток без claims. В TEST130 на размерах 2/3/9 первый `20102@farm.unconfirmed` структурно прошёл и был отклонён физическим callback, после чего тот же владелец проверил и записал `20101@farm.fixture` с тем же groupId и roster. Изменение реальной версии отдельной fixture-строки между proof и CAS отклонено без группы, claims и изменения Goal. Отдельные JVM подтвердили exact readback и штатно закрыли claims. F7 PASS. Полный witness managed-профилей 69/70/72/85/79/86 совпал во всех трёх SQL запусках с прежним SHA-256 `6b26936df5430bde6490b708a51d38e98b75f367634e31311ede8c9628a7c61c`.

Geo203 заново прочитан существующим hermetic probe. Четыре локальных Argos-коннектора 191/518/565/287 дали `VALID_DIRECT` и `DIRECT_VALIDATED` без GameServer. Прямой контроль на 75 км остался вне буфера и не используется для заключения о многосегментном маршруте. `javap` в новом overlay подтвердил вызов `partyOffer` в Runner, отсутствие отдельного `previewDiscovery` в Runner и штатный owner CAS до `PhantomScheduler.start`. Launcher на сохранённых артефактах ожидаемо отказал с `TEST130_SHARED_ROUTE_LINKAGE_REFUSED`, включая `jar.partyOffer`. Postflight подтвердил protected207, точные хэши Geo203, отсутствие JVM и слушателей TEST130.

Ограничение доказательства. SQL physical callback использует управляемые результаты, а Geo-проба видит только герметическую геометрию и статические коллизии. Фактические живой spawn, нулевой World на новом JAR, Party, D03–D05, игровые rewards и STORE ещё требуют native-приёмки. Расход в этом срезе 0 JAR / 0 GameServer / 0 restart, общий 1/2/0. Причина прежнего изменения knowledge hash UNKNOWN. TASK130 PARTIAL, TASK131–136 NOT_STARTED, FULL 0/7.

## Боевой witness для одного воплощения NPC

Runner теперь учитывает действующий штатный состав Party и точный объект моба в общем диапазоне уровней. Применяется production-проверка `partyNativeTarget` для текущего `npcId@anchorId`. На живом мобе сохраняются начальный HP, stock damage и native evidence текущих owners. Подтверждение требует смерти и снижения HP того же спавна, наблюдаемого прироста урона участника, точного kill receipt и завершённого reward settlement для двух разных получателей. EXP/SP берутся как дельты одного award на том же owner epoch. Support может получить награду без собственного урона. Смена stock Party, состава, поколения группы или источника отбрасывает незавершённое наблюдение. Быстрый бой или перезаписанный receipt могут потребовать следующего наблюдаемого боя для доказательства.

RED воспроизвёл ложный зачёт урона от предыдущего поколения спавна, MODEL130 60/61, exit 1. GREEN подтвердил отказ для старого урона и неоднозначной награды, а также точные дельты 40 EXP/4 SP атакующему и 20 EXP/2 SP support в модельном контроле. MODEL130 61/61, Party lifecycle 34/34, Combat action ownership 37/37 и F8/native evidence 14/14, всего 146. Это модельные величины, игрового зачёта не было. `javap` подтвердил происхождение overlay Runner и точный вызов `exactGroupRewards`. Launcher отклонил сохранённый Runner с `runner.exactGroupRewards` в списке отсутствующих вызовов до старта. JAR, SQL и GameServer в этом срезе не запускались.

## D04 учитывает характеристики каждого атакующего

Party брала защиту участника относительно основной цели и переиспользовала её против всех дополнительных мобов. Теперь для каждой пары моб–участник читаются атака моба против этого участника и защита участника против этого моба. Прежняя формула входящего урона применяется непосредственно к нужному интервалу. Для фактической жертвы это оставшееся время боя, для остальных действует ранее согласованный резерв до 6 секунд. Фактические получатели дополнительных угроз перечисляются в D04 witness. Solo сохраняет прежние входы и формулу.

RED воспроизвёл ошибочную оценку 2520 вместо 3600 в контроле с разной защитой против основной и дополнительной угроз. GREEN подтвердил 3600, а при смене цели дополнительной угрозы на этого участника вклад этой угрозы вырос до 9000. MODEL130 62/62, Background MODEL 10/10, Party tactics 10/10, Combat core 72/72, Combat action ownership 37/37, всего 191, все exit 0. Solo 092B дал точно прежний witness. SQL, JAR, GameServer и restart в этом срезе не запускались.

Открытый вопрос D04. Прямой штатный `doCast` может использовать получателей `MagicUseTask`, пока AI хранит прежний `attackTarget`. Согласование read-only наблюдения фактических получателей и отказа при неподтверждённой угрозе находится отдельно. Фактические D03–D05 и E остаются NOT_RUN.

## D04 читает актуальные цели штатного MagicUseTask

Реализован согласованный read-only снимок обеих cast slots. Он фиксирует identity task, опубликованный список, фазу и признак штатного native lifetime. Список копируется с пределом 16 целей для D04. Перед подтверждением риска сверяются те же task, фаза, identity списка и его содержимое. Launch/hit представляют ещё возможное воздействие, finalizer не считается новым ударом. Код выбора целей, исполнения каста, AI и формулы сервера сохраняются.

Одиночный враждебный каст относит риск к своему фактическому получателю, AoE и simultaneous cast учитывают всех подтверждённых Party получателей. Прогноз использует характеристики конкретного моба и защиту каждого участника. Неустановленная или изменившаяся угроза связанного моба даёт `WAIT_THREAT` с причиной и objectId в существующем цикле Combat cancellation и повторной проверки. Подтверждённые посторонние касты без атаки на группу не расходуют бюджет угроз. Прежний `attackTarget` сам по себе не заменяет список целей активного враждебного каста. Прогноз по текущим получателям не является записью нанесённого урона.

MODEL-контроли проверили одиночную цель, AoE, simultaneous cast, чужого получателя, изменение списка на месте и через `setTargets`, смену фазы, stale task, переполнение, отсутствие списка, отсутствие native lifetime и допуск idle. MODEL130 64/64, Combat core 72/72, Combat action ownership 37/37, Party tactics 10/10, Background MODEL 10/10, F8/native evidence 14/14. Все exit 0, всего 207. Реальные D03–D05 и E остаются NOT_RUN. SQL, JAR, GameServer и restart в этом срезе не запускались.

## D05 предпочитает доступный Heal для конкретного получателя

В автономном Party FARM тактика Heal использует ту же проверку получателя, что и `readyFarmHeal` перед боем. Изученный вариант сверяется по рангу, MP, charges, штатным условиям каста, списку целей, дальности и Geo. Если есть готовый для этого получателя Heal, он имеет приоритет над вариантом, который сейчас недоступен. При отсутствии немедленного варианта штатный support-owner по-прежнему может обработать законный подход. Обычные Party-режимы сохраняют прежний порядок, stock `useMagic` по-прежнему выполняет каст и списывает ресурсы.

MODEL зафиксировал точного получателя и выбор доступного Heal вместо более высокого по рангу недоступного варианта. Контроль вне FARM сохранил прежнее ранжирование. Party tactics 11/11, lifecycle 34/34, MODEL130 64/64, Combat action ownership 37/37, F8/native evidence 14/14, всего 160, все exit 0. Это проверка пути выбора, а не свидетельство живого Heal. SQL, JAR, GameServer и restart в этом срезе не запускались.

Mojibake-маркеры в изменённых файлах проверены. Совпадений нет.

Escaped Cyrillic в изменённых файлах проверен. Совпадений нет.

## Приёмочный результат и штатный STOP проверяются отдельно

В сохранённой попытке PID 54332 штатный STOP вернул exit 0 после `NATIVE130_FAILURE`. Это воспроизвело ошибку прежнего итогового кода launcher без нового запуска. Теперь launcher сохраняет exit JVM, выполняет проверку процессов, портов и protected-файлов и сверяет результат игровой вертикали с тем же PID и SHA-256 JAR. Проверка требует финальный PASS, подтверждение STORE, закрытие пула и штатное завершение GameServer. Runner публикует завершение только после отсоединения владельца Phantom World.

Перед условным restart тот же валидатор проверяет последнюю native-попытку. Отдельный атомарный маркер разрешает только одну попытку запуска restart. Проверка выполняется до G0 и запуска JVM. Обязательные проверки Geo203, SQL и разрешение владельца на native остаются отдельными условиями процедуры.

Сохранённая ошибка с exit 0 отклонена с `TEST130_VERTICAL_FAILED`, Windows PowerShell exit 1. Изолированный файловый контроль принял полную пару результата и STOP, отклонил другой JAR и отсутствие подтверждения stock save. Read-only postflight прошёл. Тестовый Runner собран отдельно, `javap` подтвердил проверку drain до публикации завершения, MODEL130 64/64. Production-исходники не менялись. SQL, сборка JAR, GameServer и restart в этом срезе не запускались.

## Приоритет общего источника перед физической проверкой

Сохранённый native stdout PID 54332 содержит 53 источника у каждого из 70/72 и одинаковые первые четыре позиции. Все четыре относятся к NPC 21320 в Hot Springs. Расстояния ниже вычислены по координатам штатных якорей от сохранённой позиции 70/72. Длины прямых дают геометрическую оценку расстояния между точками.

| Позиция в сохранённом списке | Источник | Прямая 3D |
|---|---|---:|
| 1 | `21320@generated.farm.4475282a85a97a4b396b967b.anchor` | 75 212 |
| 2 | `21320@generated.farm.5241a753fc2261cfc117ac7b.anchor` | 78 845 |
| 3 | `21320@generated.farm.5503deaff7039892e29f17da.anchor` | 79 686 |
| 4 | `21320@generated.farm.7ac8a91de59dc0378a93f4bc.anchor` | 77 805 |
| 5–8 | UNKNOWN. В сохранённом stdout только первые четыре | — |

Для первого источника ранее подтверждена структура 16 WALK и 1 NORMAL GK с configured quote 9300 и действующим Phantom tariff 0. Прямая проверка той попытки вернула `NO_PATH_WITHIN_SEARCH`. Для 21300/21301/21302 в Wall of Argos сохранённый старт 70/72 совпадает с якорем `generated.farm.38520d85788548d726048a27.anchor`. Статические коннекторы проверены. Позиции этих трёх источников в полном старом списке и точные первые восемь общих предложений текущего production catalog неизвестны. Канонический backend читает `SpawnTable` после загрузки спавнов, а в архиве нет полного снимка. XML подтверждает настроенные источники и маршруты, live source и общую сортировку всего runtime-каталога ещё требуется доказать.

Owner теперь сначала включает ограниченное окно ближайших FARMING-якорей из существующих индексов и сохраняет глобальные страницы как fallback. Из общего окна до 64 источников он ранжирует предложения с учётом штатного структурного itinerary всего состава. Локальный WALK приоритетен. Сравниваются ходьба с ingress и GK-коннекторами, длительность по графу, число NORMAL GK и действующая цена. Runner и Scheduler используют один порядок и один CAS. Физическая проверка остаётся read-only. Из 30 секунд сцены один кандидат получает до 3 секунд и максимум половину оставшегося времени. Просроченный результат отбрасывается, а прерванный worker прекращает дальнейшие сегменты.

RED на MODEL отправил в proof восемь дальних источников и пропустил локальный `20008@farm.page.8` на позиции 9. GREEN проверил его первым и сохранил то же предложение через штатный CAS. Дополнительные проверки подтвердили учёт GK у второго участника и отказ просроченного proof с доступным бюджетом для следующего. В SQL размеры 2/3/9 выбрали `20101@farm.fixture` с исходной позиции 9 на позиции proof 1. Версия, изменившаяся после proof, отклонена, claims отсутствовали до CAS. Отдельная JVM прочитала тот же groupId, тестовые claims штатно закрыты. F7 прошёл, хэш шести managed-профилей совпал.

MODEL130 67/67, Party lifecycle 34/34, Party tactics 11/11, Navigation 51/51, Combat ownership 37/37, Background 10/10 и F8 static 14/14, всего 224. Контроль Solo 092B совпал. Geo probe подтвердил четыре локальных коннектора как `DIRECT_VALIDATED`, Geo203 и protected207 прошли повторную сверку.

Позиции 5–8 текущего production catalog и точная позиция Argos остаются блокером дешёвого допуска. В этом срезе JAR и GameServer не запускались. Полезных групповых убийств и наград 0. D03–D05 и игровая вертикаль E остаются NOT_RUN. Причина прежнего изменения knowledge hash UNKNOWN.

## Native-итерация на 4bd0afa56 и восстановление после STOP

Владелец разрешил получить фактический каталог из GameServer. Clean JAR на `4bd0afa564b02d59557bf7dd92e05659ab08cd81` имел SHA-256 `bc921eaaffdc77088cfdd88db739459ff623fa7996812858551c2061687058b7`. Все 4753 класса совпали побайтово с выходом компиляции. Совпали четыре runtime-зависимости, происхождение и необходимые вызовы Runner подтверждены через `javap`.

GameServer PID 71080 подтвердил на первом физическом предложении `21300@generated.farm.38520d85788548d726048a27.anchor` в Wall of Argos. Оба участника уже находились на якоре, структурный itinerary содержал 0 переходов. Из 11 записей `SpawnTable` фильтр источника нашёл 3 живых подходящих NPC поблизости. Geo ingress до объекта 268453529 вернул `FOUND` с одной точкой. Позиция этого источника в полном прежнем списке, отсортированном по уровню, остаётся UNKNOWN.

Штатный Party owner записал agreement `650D36A438202CBD6F168254129EAE867C7725F793368550B2F2C188144D51E2`, generation 1, с нулём native Player на границе CAS. Профиль 70 получил роль `frontline.guardian`, профиль 72 — `support.healer` с проверенным learned Heal. Scheduler перевёл обоих под `execution.party`. Группа достигла `BACKGROUND/WAIT_DEMAND`.

Новый `farm.background` для каждого участника был опубликован штатной execution-передачей. Хэши двух Catchup `PENDING` совпали с соответствующими `goal.publication.companion.0`. Временная постановка Goal publication в очередь выбрасывала общий `ConcurrentModificationException`. Historical Background переводил его в `CONFLICT/catchup.claim.conflict`, а Ecology терминально останавливал профиль до принятия законного `PENDING`. Исправление выделяет тип только для уже поставленной в очередь публикации и возвращает `RETRY/catchup.claim.publication_pending`. Проверки действительного конфликта сохраняются.

Согласованный ограниченный startup recovery повторно читает точные claims и даёт существующим Execution, Ecology и Party owners завершить восстановление. Пока gate закрыт, обычный и локальный Scheduler dispatch удерживаются. Действующая Party проходит proof собственного состава и источника. Для истёкшей Party применяются штатные `DRAINING`, восстановление execution и guarded release. Новый agreement публикуется после физической проверки и readback CAS. При отмене proof новый CAS закрыт.

Первая попытка завершилась по `NATIVE130_FINITE_EVIDENCE_DEADLINE`. Stock STOP и postflight прошли. Launcher вернул 1, несмотря на штатный exit JVM 0. Игровая материализация группы, stock Party, D03–D05, убийства, Heal, награды и групповой STORE не доказаны. Изменение knowledge hash между GameServer остаётся UNKNOWN.

В реальной изолированной TEST130 SQL воспроизведены точный `PENDING` с двумя claims и прежний `CONFLICT/catchup.claim.conflict`. С исправлением первый и повторный точный запрос дают `RETRY`, перекрывающееся окно и отличающаяся ожидающая публикация отклонены. Штатная execution-передача опубликовала тот же Catchup с cursor 101, target 105, intervalOrdinal 0 и без наград. Отдельная JVM прочитала тот же действующий groupId, generation и версии, отказала устаревшей версии. Истёкшие claims освобождены через штатный CAS после доказанной передачи и восстановления обоих execution owners. Повторное освобождение не записало второй результат. Перед и после SQL сценариев полные строки шести managed-профилей совпали.

Recovery MODEL проверяет отказ по исключению callback, ожидание и единственное открытие общего и локального Scheduler. Целевые тесты Ecology подтверждают принятие точного осиротевшего окна и отказ чужой идентичности. Полная историческая suite Ecology даёт одинаковые 39/40 на baseline и текущих классах. Единственный отказ проверяет старое требование к master-флагу дистрибутива. Runtime TEST130 отдельно запускается с выключенным master-флагом.

Startup разрешает Ecology завершить сохранённое окно и удерживает новые календарные окна до физического proof. Это важно для TEST130, где сохранённые окна отставали примерно на 33 часа. Существующий тест восстановления до и после durable commit теперь проверяет, что дополнительное календарное время обрабатывается только после открытия gate. Scope этого среза ограничен 11 файлами существующих owners, их целевых тестов, Runner и этого отчёта. Конфигурация, маршруты, боевые формулы и SQL schema не менялись.

Read-only preflight TEST130 подтвердил G0 PID 8692, точный SQL principal и provenance, Geo203 и protected207, отсутствие своего JVM и слушателей. У обоих исходных участников level 76, расписание ACTIVE, consent true, незавершённых STORE или commerce нет. Сохранённая группа к этому моменту прошла свой срок assembly. Оба PENDING по-прежнему содержат прежние requestId и нулевой intervalOrdinal. SQL F7 прошёл из каталога собственного runtime. Снимки до и после F7 совпали по всем 98 строкам managed-профилей и их компонентов.

## Второй bootstrap на 74b109f63

Clean JAR SHA-256 `46e0437859939756df1f5f1a60159dd6d4d1f41d29cac220bc2e05642627cbc9` собран на `74b109f63e5d695f972e9661b927a06bc06c9fcb`, tree `5192c37cb69ddeba784a641870e877eece5e8ba1`. Совпали 4755 из 4755 классов и четыре зависимости. Отдельно скомпилированы 468 тестовых исходников. `javap` и GameServer подтвердили происхождение Runner из фиксированного test bin. MODEL на этом JAR прошёл 67/67, целевой Ecology 6/6. Перед сборкой на тех же исходниках Scheduler 37/37, local priority 3/3, Party lifecycle 34/34 и F8 static 14/14.

PID 52352 начал recovery и прошёл прежний `catchup.claim.conflict`. Оба claim исходной группы того же groupId и generation 1 сменили `active=true` на `active=false`. Обе execution-строки стали `execution.solo ACTIVE`, generation 8, row 25. Сохранились прежние Catchup requestId и Goal ID. Для 69 и morning 85/79/86 изменились только версии execution-компонентов.

| Профиль | Фактический итог штатного recovery |
|---|---|
| 70 | `FAILED_REPLAN_REQUIRED/model.resource_reserve`. Окно 29902196–29902200, cursor 29902196, intervalOrdinal 0. EXP 931275828, SP 0 и 50000 адены сохранились. |
| 72 | `COMPLETE`. Окно 29902245–29902260 завершило 15 интервалов с тем же requestId. Сохранённый Background `DEAD`. EXP 931275828 → 925934810, SP 0 → 26920, адена 50000 → 106010. Эти изменения записал штатный Background owner в рамках принятого окна. |

Второй запуск остановился по `NATIVE130_STARTUP_RECOVERY_DEADLINE` до нового предложения источника или CAS. A–C подтверждены MODEL, SQL и Geo. По D сохранились предыдущие сведения о ролях и learned Heal, полный D01–D02 и фактические D03–D05 остаются открыты. По E первая попытка доказала точную LogicalParty с 0 native Player. Материализация состава для игры, stock Party, damage, kill, групповой EXP/SP/loot, Heal, групповой STORE и RESTART не достигнуты.

GameServer штатно отключил и сохранил игроков, закрыл Hikari и вернул 0. Launcher классифицировал вертикаль как `TEST130_VERTICAL_FAILED`, exit 1. Postflight подтвердил SQL provenance, восстановление master-флага в False, protected207 и Geo203. Осталось 0 своих JVM и слушателей. Сохранены полные `startup130.52352.before.txt` и `startup130.52352.after.txt`, raw stdout/stderr и read-only SQL обоих сцен. Текущий knowledge hash для 70/72 стал `602265cad18db18964affe26a182dd4f0e6238941848f17559db9cffe49fb770`. Причина отличия между запусками UNKNOWN.

Системное препятствие находится на переходе от recovery к formation. Штатное истечение прежней группы возвращает execution в Solo. Historical Background затем обрабатывает индивидуальное окно. Он сохраняет `model.resource_reserve` как терминальный `FAILED_REPLAN_REQUIRED`, а Ecology перестаёт ставить это окно в очередь. Startup ждёт готовности двух участников перед proof. Таким образом, индивидуальный исход фонового окна сейчас закрывает допуск к групповой проверке. Точная внутренняя ветка resource reserve в raw evidence отсутствует.

Для пересмотра приёмки требуется отдельно доказать безопасное владение точным сохранённым окном и готовность к первому боевому действию. Разрешённый штатный native-context handoff может быть применим после отдельной проверки причины, сохранности requestId, cursor, receipts и восстановления умершего через существующего owner. Новый CAS и Combat по-прежнему требуют своих proof и guards. Дешёвый сценарий должен воспроизвести просроченную группу, терминальный Solo reserve и DEAD, подтвердить отсутствие повторной выплаты и пройти до законной проверки состава.

Первая версия изолированного SQL fixture оставила тестовые claims 1083/1084 после того, как штатная проверка отклонила освобождение без подтверждённого execution checkpoint второго участника. Эти профили не входят в managed census. Исправленный отдельный fixture v2 доказал обе передачи и закрыл свои claims штатно. Raw evidence первого отказа сохранён.

Расход текущей итерации 2 clean JAR, 2 GameServer bootstrap, 0 restart. Сборки заняли 25 и 21 с, процессы GameServer жили 330 и 287 с. Всего для продолжения 3 clean JAR, 4 bootstrap, 0 restart. TASK130 остаётся PARTIAL. TASK131–136 остаются NOT_STARTED, FULL 0/7 → 0/7.

## Изолированная первая Party на JAR 46e04378

Перед подготовкой подтверждён удалённый WIP `9feff25c915129c715f8fd2d2bf82ab540f7ca05`. Запуск использовал WIP `c9223736119c65396391396ff17d0654bf97c12d` с шестью изменёнными файлами test runner и инструментов стенда. Production `java` и `dist/game` совпали с исходником прежнего JAR `74b109f63e5d695f972e9661b927a06bc06c9fcb`. Повторно проверены хэш JAR `46e0437859939756df1f5f1a60159dd6d4d1f41d29cac220bc2e05642627cbc9`, исходная parity 4755/4755 классов, четыре зависимости и test runner linkage. GameServer подтвердил происхождение загруженных классов.

Стенд использовал собственную MariaDB 11.4.13 PID 47472, `127.0.0.1:33306`, без регистрации службы. G0 подтвердил PID, время создания, образ, конфигурацию и локальный TCP. Штатно загружены 123 SQL-скрипта. SQL-контроль проверил principal и grants выделенной схемы `l2jmobiush5_phantom_test_130_first_party`. Дешёвый RED выявил расхождение timezone БД и JDBC. GREEN с UTC и штатным повторным созданием двух ещё не использованных компонентов Ecology до CUT дал COMPLETE с исходными EXP/SP. В исходной TEST130 не было пары с готовым историческим окном и согласием. Её состояния сохранены как отдельная регрессия.

**TEST FIXTURE до CUT.** Штатный lifecycle создал Duelist (профиль 1, класс 88) и Cardinal (профиль 3, класс 97), уровень 76 у обоих. Исходные EXP 931275828, SP 0 и 50000 адены на персонажа, объявленная экипировка и изученный Heal 1011:18 у Cardinal относятся к подготовке. Начальные HP/MP 4987/1479 и 3119/2480 соответственно. Оба были размещены на каноническом FARM-якоре (16323, −248197, −9576). Это решение дало короткий ingress и одновременно поместило ещё не собранный состав в населённую мобами зону. Оба согласия, календаря и отсутствие блокирующих obligations подтверждены до CUT. После CUT применялись штатные владельцы и игровые действия.

| Уровень проверки | Фактический результат |
|---|---|
| A–C, MODEL/SQL/Geo | Ранее принятые результаты сохранены. Целевой `visible-itinerary068` 23/23 проверил цепочку Population/Ecology/Presence, включая ambient demand и ограждения admission. SQL подтвердил исходную готовность обоих и сохранность регрессии. |
| NATIVE, источник и состав | Первый физически проверенный выбор production owner — `21254@generated.farm.0570910db17d9f20a9d86883.anchor`, Tomb Guard 75. Исходный ранг в старом полном списке по уровням для этого состава не записан. Загруженный SpawnTable вернул 28 spawn-записей, три живых подходящих NPC, Geo ingress `FOUND` до объекта 268442535. Оба стартовали на якоре с itinerary из нуля сегментов. Тот же exact состав и источник записаны штатным CAS, groupId `B89EC81C72B9BCDF77FEA4CE6B06FB4196505071BCFE43503BFAA29F55DDEEB7`, generation 1. |
| D01–D02 | Подтверждены фактические классы, уровни и материализация обоих. Полный stock Party roster и его боевые capabilities не приняты. |
| D03 | Совокупные боевые DPS и ресурсы в native Party не проверены. |
| D04 | Групповой guard угрозы до боевого действия не достигнут. У обоих зафиксирована смерть до stock invite/accept. Урон по конкретным получателям и источник этого урона не аттестованы. |
| D05 | Heal был изучен на исходной фикстуре. Штатный Heal и соответствующий расход MP в группе не зафиксированы. |
| E, NATIVE | При нуле людей в World штатный admission материализовал 1 из 2 к 10.1 с и 2 из 2 к 20.1 с. Оба восстановились после смерти штатным путём, в 90.2 с группа перешла в ASSEMBLING. Stock Party 0, групповой damage/kill 0, earned group EXP/SP/loot 0, Heal 0, групповой STORE не достигнут. |
| RESTART | Условие полезной первой вертикали не выполнено. SameDB/sameJAR restart 0. |

На 20.1 с у Cardinal HP 0, на 40.2 с HP обоих 0. После respawn оба находились в точке (116737, 76738, −2728), без движения, каста или Party. С 90.2 по 150.3 с сохранялся `ASSEMBLING:WAIT_NATIVE_ARRIVAL`. На этом участке у Duelist повторялся `rendezvous.prepare.authority_renewal`, у Cardinal зарегистрированы `rendezvous.prepare.local_replan:planner.rendezvous.travel_unavailable` и затем cooldown. Внутренний отказ ветки authority renewal и конкретный непройденный коннектор по этому trace неизвестны. У Cardinal postflight Background прочитал EXP 925930202, SP 27140 и 105040 адены после штатной обработки исторического окна. Это изменения Background, принятых native групповых выплат нет.

В текущей цепочке `PhantomAutonomousFarmParty.advanceOffer` доводит участников до принятия цели и маршрута или точной встречи с уже принятой целью перед вызовом `formNativeOffer`. Проверка источника подтверждает живого NPC и ingress, но не проверяет безопасное состояние группы до invite. После death recovery подготовка общего маршрута по-прежнему зависит от точного native checkpoint и допустимого пути от новой позиции. В этом запуске оба участника уже находились вместе, но условия принятия маршрута не выполнились. Недостающая способность — безопасно перевести материализованный состав через первую встречу и последующее совместное движение, в том числе после штатного respawn. Приёмка этой границы должна отдельно проверять безопасную точку сбора, актуальный checkpoint Goal/authority после respawn и штатный itinerary от фактической позиции до сохранённого источника. Нужны те же Party, Presence, Background и Travel owners с обычными invite/accept, Geo/GK и Combat guards.

Runner зафиксировал `NATIVE130_IDLE_ASSEMBLY_DIAGNOSED`. Stock Shutdown отключил и сохранил игроков, закрыл Hikari, JVM exit 0, launcher exit 1. SQL postflight показал одну LogicalParty ASSEMBLING и два durable logical claims, execution обоих остался у той же группы, незавершённых prepared STORE нет. Обычное сохранение при STOP подтверждено отдельно от группового STORE. Сохранены `preflight130.38300.before.txt`, `preflight130.38300.after.txt`, `agreement130.38300`, native trace, stdout/stderr и SQL postflight. Canonical authority recovery прошёл проверку conservation. Текущий knowledge `49c693a538f086641114737866cbb4cf0397729d1747dfb6d9a5d828f1433119`. Причина различий knowledge hash между GameServer остаётся UNKNOWN.

Read-only postflight исходных сцен сравнил 50 + 48 точных отпечатков managed-профилей и компонентов. Совпали оба набора Geo203 и protected207. После штатного SQL shutdown PID 47472 и listener 33306 отсутствуют, исходный listener 3308 по-прежнему PID 8692.

В этой итерации 0 clean JAR, 1 GameServer bootstrap, 0 restart. Всего для продолжения 3 clean JAR, 5 bootstrap, 0 restart. TASK130 остаётся PARTIAL, новый продуктовый результат — полный native roster из автономной LogicalParty при нуле людей в World и штатное восстановление участников. TASK131–136 остаются NOT_STARTED, FULL 0/7 → 0/7. Правило среднего уровня группы отложено по решению владельца, текущая проверка использовала ±4 для каждого участника.

## Штатный сбор и совместный WALK из Годдарда

Итерация началась с проверенного GitHub WIP `5181a86093ac439e278d35f3ca6134be4c32f14f` в независимом clone. В production штатные invite/accept отделены от достижения FARM и личного Goal. После точного согласия и подтверждённого stock roster существующий Party route owner получает цель из LogicalParty. Он ведёт участников по WALK/GK, сверяет эпохи и строит маршрут от текущих позиций после respawn. GK требует отдельного native допуска, точного баланса и подтверждения посадки каждого. Защита Solo travel при stock Party сохранена. При cold startup существующие owners восстанавливают прежнее владение до физической проверки. Новое ещё не занятое окно Ecology допускает штатную материализацию при подтверждённом спросе действующей LogicalParty. Открытые и завершённые исторические запросы сохраняют своих владельцев и идентификаторы.

**TEST FIXTURE до CUT.** В собственном экземпляре MariaDB 11.4.13 на `127.0.0.1:33307` и схеме `l2jmobiush5_phantom_test_130_rendezvous` через штатный lifecycle подготовлены Duelist 88, profile 1, Player 268480990 и Cardinal 97, profile 3, Player 268481003. Оба стартовали в штатной точке Годдарда (148024, −55281, −2728) на уровне 76, с 931275828 EXP, 0 SP и 50000 адены на каждого. Экипировка, изученные способности, согласие и расписание входили в исходное состояние. После CUT ручных изменений игровых данных не было.

| Запуск | JAR | Реальный результат |
|---|---|---|
| 1, PID 75124 | 1 | Exact LogicalParty и stock Party из двух настоящих Player в Годдарде. Cardinal впервые материализовался уже мёртвым после обычного Background и штатно воскрес в городе. Затем вступил в Party с реальной позиции respawn. Исторические изменения Cardinal, включая SP 23980 и адену 111265, относятся к Background. Продолжение остановилось после изменения revision личного Goal лидера. |
| 2, PID 16864 | 2 | `NATIVE130_STARTUP_RECOVERY_DEADLINE`. SQL показал старое состояние `MATERIALIZED` без native Player. Startup проверял readiness раньше штатного восстановления. |
| 3, PID 49084 | 3 | Canonical recovery и совпадение сохранённой группы. Geo подтвердил структурные коннекторы к якорю. Проверка подхода к одному живому NPC завершилась `SEARCH_LIMIT`. STOP до допуска. |
| 4, PID 79004 | 3 | Тот же состав и источник, актуальные Geo-коннекторы к якорю и живой NPC. Проверка непосредственного подхода к мобу оставлена существующему Combat owner для уже доказанного соглашения. Новое фоновое окно задержало обе материализации до истечения соглашения. |
| 5, PID 73732 | 4 | Новый законный CAS после истечения прежнего соглашения. Два живых Player, stock Party, штатный WALK обоих к Gatekeeper, `walk_arrived`. Затем `NATIVE130_PARTY_DRAINED_WITHOUT_USEFUL_ACTION` и чистый STOP. |

**Точный источник в пятом запуске.** Production owner выбрал Hot Springs Yeti 75, `21320@generated.farm.5241a753fc2261cfc117ac7b.anchor`, с первой физической проверки в текущем ранжировании. Исходная позиция в старом порядке по уровням в этом запуске не записана. Загруженный SpawnTable показал 22 записи, 4 живых подходящих моба и Geo ingress `FOUND` до NPC 268452014. Предложение Runner и CAS того же owner совпали по источнику, составу и версиям. С нуля native Player создана группа `629DCCE81FE54379B9D549EB76B24393F5282435A564D532A55238530169E751`, generation 2. Preflight подтвердил маршрут с настоящей веткой GK `leg.872c4a16bdd379d147b68e83`. Тариф 0 относится к preflight штатной политики фантомов. Фактической оплаты и посадки не зафиксировано.

**Фактические этапы приёмки.** Ранее принятые A–C пакета остаются MODEL/SQL/Geo. В D01–D02 подтверждены реальные классы, уровни 76/75, состав и назначения `damage.melee`/ `support.healer`, с learned Heal 1011 у Cardinal. Для D03 нет native расчёта совокупного DPS и ресурсов перед боем. Для D04 нет наблюдений угрозы и шестисекундного резерва в native бою. Для D05 есть доступность навыка и нативные бафы, эффект HP и расход MP именно от Heal не зафиксированы.

| Игровой рубеж A–E | Пятый запуск |
|---|---|
| A, stock Party | Два настоящих живых Player приняли штатные приглашения до выхода к споту. Лидер 268480990. Начальные позиции лидера (148024, −55281, −2728) и участника (146144, −57900, −2976). |
| B, WALK/GK | Cardinal пришёл к лидеру от позиции respawn. Затем оба дошли до (147966, −55228, −2728). К 110,4 с оба были в точке, подтверждение `walk_arrived` появилось на 291,0 с. GK и прибытие к FARM не подтверждены. |
| C, PvE | Групповой урон, убийство и earned EXP/SP/loot не зафиксированы. SQL после STOP сохранил EXP/SP обоих с прежними значениями. |
| D, Heal | Реальный групповой Heal с квитанцией HP и расходом MP не достигнут. |
| E, STORE и restart | Канонический STORE сохранил реальные позиции обоих. Обе операции `COMPLETED`, подготовленных STORE нет. Execution вернулся к Solo, durable LogicalParty осталась `BACKGROUND:WAIT_DEMAND`. Условие restart после полезной вертикали не достигнуто. |

В точке GK плановые касты занимали независимых участников одновременно. В raw trace видны Duelist Spirit 297 и бафы Cardinal 1078/1085, `cast-launch`, устойчивый `REGROUPING` и неизменные координаты. Route owner ждёт окончания кастов, а Party tactics в том же pulse допускает следующий idle buff. После окончания WALK точная причина финального drain до GK в записи не выделена. Checkpoint `c51a347cdefebda0978a65f060da30c4d4cea3f8` откладывает только idle maintenance до фактического прибытия. Combat maintenance, срочные Heal и Resurrect доступны через штатного support owner. Целевой RED на JAR4 воспроизвёл лишний каст, 68/69. GREEN на отдельной headless-компиляции существующего owner прошёл 69/69, включая срочный Heal в MODEL. Этот checkpoint отсутствует в JAR4. Неиспользованный шестой bootstrap остался без актуального production JAR. Native допуск этой правки, GK и дальнейший бой открыты.

Четыре clean JAR сверены с точными исходниками, 4758 классов и 4 runtime dependencies в каждой сборке. Последний испытанный JAR `8AB6B6B01C71836369E7BC9D475D90AF44AF8BA2F8C2F9C745C24DC9A2DD4AA0` соответствует `47717b7e5430c19029a9b0d9e59b92754d35c9c4`. Bytecode linkage и runtime class origin runner подтверждены. Целевые проверки на JAR4 прошли `world-coordination130` 69/69, `party-route` 9/9 и `visible-itinerary068` 23/23. Израсходовано 4 clean JAR, 5 GameServer bootstrap, 0 restart. Всего для продолжения 7 clean JAR, 10 bootstrap, 0 restart.

Исходная TEST130 совпала по 50 + 48 read-only отпечаткам. Оба комплекта Geo203 и protected207 совпали по SHA-256. Все пять JVM завершились штатно с exit 0. Свой SQL PID 34864 завершён штатно, свои порты 33307, 7962, 9262 и 2262 свободны, исходный 3308 принадлежит прежнему PID 8692. Сохранены raw stdout/stderr, before/after authority, Goal/Catchup/conservation witnesses, соглашения, журналы G0 и postflight. Для пятого запуска ключевые файлы `native-20261109-212952.stdout.log`, `native130.trace.73732.tsv`, `agreement130.73732`, `startup130.73732.before.txt`, `startup130.73732.after.txt` в `runtime-rendezvous` и `native-5-postflight-sql.log` в `evidence/safe-rendezvous-5181a860`.

Причина изменения knowledge hash между запусками UNKNOWN. Причина смерти в прежнем trace PID 38300 UNKNOWN. Состояние Cardinal при первом запуске этой итерации отдельно подтверждено Background, но конкретный источник смертельного урона неизвестен. F7/F8 и требования conservation не ослаблялись.

Будущая игровая приёмка включает приглашение человеком в Гиране с правдивыми ответами о классе и уровне, приглашение встреченного во время Solo фантома с законным завершением или передачей его действия, штатные права лидера и совместное движение. География сама по себе не ограничивает invite/accept. Автономный сбор и WALK подтверждены, разговор и эти сценарии с человеком отдельно не проверялись. Политика среднего уровня группы отложена.

TASK130 остаётся PARTIAL с доказанным переходом от материализации к stock Party и совместному WALK. TASK131–136 остаются NOT_STARTED, FULL 0/7.

## Платный GK, прибытие к споту и первая native атака после 37102600

Базовый удалённый WIP `37102600b5866fb67a9bd908cee19ec7cc57d840` проверен перед работой и перед native-запусками. В JAR вошёл ранее опубликованный `c51a347c`. Последний production commit `8e3246c6b708819a2b73e4a7999fd08954b791ad` соответствует clean JAR SHA-256 `D689DAC740E8838059113AFCF887341D82641D35D3EFB934A0989794747A9BD0`. Parity сверена побайтово для 4759 классов и четырёх зависимостей. После production commit изменились test runner и этот отчёт. В `java`, `dist` и `build.xml` расхождений с исходниками JAR нет. Происхождение исполняемого Runner и порядок его проверок подтверждены по runtime и bytecode.

### Реальные игровые результаты

| Игровой рубеж | Проверенный результат |
|---|---|
| A. Stock Party | В №16 двое живых Player вступили в Party в Годдарде. В №13 персонаж с сохранённой смертью штатно воскрес, затем состоялся invite/accept между участниками на расстоянии около 54 км. В №22 та же сохранённая LogicalParty `34005A63C1630BC39B12F294D97B8005C81BD246B9A83F9A05A52A28D92E7ED2`, generation 1, восстановилась без второго formation CAS. Duelist, Hell Knight и Cardinal приняли штатные приглашения и вошли в Party до выхода из Годдарда. |
| B. WALK, GK, прибытие | В №16 каждый из двух участников заплатил 9 300 аден Gatekeeper 31275, с 50 000 до 40 700. Оба появились в `(144880, -113468, -2563, 0)`, вместе прошли до `(146876, -112049, -2344, 0)` и получили штатное подтверждение прибытия. В №19 после штатного respawn оба заново прошли раздельные маршруты под одним Party route owner и заплатили по 9 300, с 40 700 до 31 400. В №22 подтверждена только оплата и посадка лидера, с 50 000 до 40 700. |
| C. Групповой PvE | В №18 первый штатный удар по живому Hot Springs Yeti, objectId 268451374, увеличил `damageSequence` с 0 до 1. После появления двух дополнительных угроз guard вернул `WAIT_THREAT`. Убийство, заработанные EXP/SP и loot не зафиксированы. №20 остановил открытие боя по `WAIT_TANK_AGGRO` до нового удара. |
| D. Heal | У Cardinal подтверждён learned Heal 1011:18. В отдельном headless fixture штатный `UseMagic` восстановил 48,0249 HP при расходе 5 MP. Это исходная контрольная проверка, а не Heal автономной Party в PvE. Боевая квитанция Heal с эффектом HP и расходом MP отсутствует. |
| E. STORE, STOP и restart | После реального WALK/GK SQL подтвердил канонические STORE и позиции. В №22 все три STORE завершились `COMPLETED`, подготовленных записей не осталось. JVM вышла с кодом 0, launcher классифицировал вертикаль как FAILED и вернул 1. Условие для sameDB/sameFINALJAR restart ещё не достигнуто. |

**Источник.** Штатный owner выбрал Hot Springs Yeti, NPC 21320, level 75, `21320@generated.farm.5241a753fc2261cfc117ac7b.anchor`, ветка `leg.872c4a16bdd379d147b68e83`. В №16 это первый физически проверенный кандидат текущего структурного ранжирования. Его позиция в прежней сортировке по близости уровня неизвестна. SpawnTable дал 22 записи, три подходящих живых моба и FOUND на коннекторах в №16. В №22 тот же состав и источник прошли proof четырёх подходящих живых мобов и ранее доказанного маршрута. На старте все новые участники были level 76, в пределах ±4 от цели. Источник и профили production-кодом не закреплялись.

**D03–D05 исходного пакета.** В №18 D03 использовал реальные уровни, экипировку, HP/MP и stock-состав, прогноз 155,8966 DPS и 54,2539 с до убийства. D04 перед открытием учёл реального выбранного puller, отсутствие наблюдаемых получателей до начала боя и резерв 6 с. После открытия две дополнительные угрозы привели к отказу продолжать. В D05 проверен learned Heal и stock cast 9 098 мс, прогнозное лечение зачислило 0 HP. Эти проверки readiness и cancellation наблюдались в native; полный D03–D05 с безопасным завершением боя, заработанными результатами и настоящим групповым Heal остаётся открытым. В №18 оба участника умерли и штатно потеряли EXP с 931275828 до 925775828. Точный источник последнего урона не установлен. В №10 наблюдался штатный Invocation с параличом и снижением PDef у Cardinal перед смертью, причинность смертельного урона не установлена. Причина изменения knowledge hash между GameServer остаётся UNKNOWN.

### Владение маршрутом и последняя остановка

Исправления переиспользуют текущие Party, Navigation, Combat и support owners. GK проверяет точный stock-состав, текущую authority, NPC, стоимость, баланс до и после и фактическую посадку каждого участника. Разошедшиеся участники используют маршрут с собственных позиций в том же owner. Квитанция лидера не считается прибытием состава. Исправлены выбор канонической адены для idle-профилей и приоритет первого безопасного pull над idle maintenance. Threat учитывает stock faction/minion assist перед открытием и фактических получателей после него. Readiness использует Combat-owned правила выбора навыка и сохраняет точную причину ограниченного `REPLAN_*`. Solo, F7/F8, CAS и правила игровых расходов сохранены.

Точная причина старого drain после 291-й секунды в №5 по доступному trace остаётся UNKNOWN. В №16 WALK и GK закончились, затем фактически сработал `WAIT_NATIVE_RETENTION_ENDED`; первая проверка pull ещё не прошла из-за последовательных idle maintenance. В №20 зафиксирован уже иной переход `DRAINING:REPLAN_TANK_AGGRO`, затем штатный `BACKGROUND` и `REPLAN_NO_COMMON_SOURCE`.

В №22 PID 68640 в 02:21:02 Europe/Chisinau записал первую точную квитанцию GK. На 110,376 с все трое находились в World и stock Party, admission оставался действующим. Лидер был в Hot Springs, двое оставались в Годдарде. К 120,392 с остался один native Player, stock Party в World исчезла, native claims всех троих стали `SOLO`. В том же интервале штатный planner опубликовал лидеру revision 1 личного Goal на NPC 21352. На 02:21:37 LogicalParty перешла из `NATIVE_ACTIVE` в `DRAINING` с `WAIT_NATIVE_PARTY_LOST`, до истечения срока соглашения. Нет receipt GK у Hell Knight и Cardinal, отказа по стоимости, `PARTY_GK_FAILED` или сообщения о срыве маршрута. Первый вызов выгрузки или смены ownership внутри этого десятисекундного интервала trace не различает.

Недостающая способность для этого сценария — удержать весь подтверждённый stock-состав и его действующий route owner между индивидуальными посадками, даже если участники временно находятся по разные стороны GK. В исходниках `retainsAutonomousFarmTravel` защищает от логического drain, а штатные gates публикации Goal, native lifetime и демотирования используют отдельные краткоживущие доказательства. Нужно воспроизвести выпадение доказательства сразу после первой посадки и отделить временную потерю снимка от фактического прекращения владения. На этой границе нужны события с причиной, epoch и точным group/generation из Party, Execution и lifecycle owners. Короткий структурный WALK до FARM в пределах 35 с среди девяти проверенных канонических точек старта не найден. Результат ограничен проверенным набором и не доказывает отсутствие маршрутов в мире.

### TEST FIXTURE, расход и сохранность

Для второй и третьей сцен использованы отдельные disposable MariaDB 11.4.13 с собственным SQL principal на 127.0.0.1:33307 и штатной установкой схемы. В guardian fixture штатное создание дало Duelist 88, Hell Knight 91 и Cardinal 97. До CUT документированы level 76, 931275828 EXP, 0 SP, 50 000 аден, stock learned skills и экипировка, включая Imperial Crusader у Hell Knight. Все стартовали живыми в канонической точке Годдарда `(148024, -55281, -2728)`. После CUT данные меняли штатные owners. В №22 у всех сохранились исходные EXP/SP, у двух оставшихся в городе сохранилось по 50 000 аден. SQL подтвердил завершённый STORE лидера в Hot Springs `(148233, -111329, -2343)` с 40 700 аден. Отдельные исходные сцены сохранены как регрессии.

По MODEL на последнем JAR и актуальных тестовых классах `world-coordination130` 71/71, `party-tactics` 12/12 и `visible-itinerary068` 23/23. Отдельно прошли проверки F7 с тегированной и нетегированной authority, SQL-квитанций и составов 2, 3 и 9 без native Player. A–C исходного пакета остаются подтверждёнными MODEL/SQL/Geo. Эти результаты отдельно от NATIVE и RESTART.

Дополнительный расход с `37102600` — 12/12 clean JAR, 16/18 actual GameServer bootstrap, 0/1 условных restart. Всего за продолжение 19 clean JAR, 26 actual bootstrap и 0 restart. №7 завершился в launcher до `GameServer.main`. Headless fixture также не вызывал `GameServer.main`. Автопроверка один раз отклонила запуск №19 из-за подсчёта по номерам попыток. Сверка process receipts и raw stderr подтвердила фактический расход, после чего запуск прошёл разрешение. Два оставшихся bootstrap сохранены до конкретного исправления, которое на текущей паре исходников и JAR ещё не испытано.

Postflight повторно сверил все Geo203 и protected207, все собственные JVM и игровые порты закрыты. Исходная TEST130 и morning-профили совпали по 50 + 48 SQL-отпечаткам. Собственная guardian MariaDB PID 37612 остановлена штатно. Исходная MariaDB на 3308 осталась PID 8692. MAIN/PLAY/TEST129, пользовательский checkout, stash и чужие процессы не использовались для записи.

Scope с `37102600` составляет 18 production-файлов, 6 тестовых, 3 инструмента стенда и этот отчёт. Срезы коммитов разделены по owner и подтверждённому дефекту. `git diff --check` прошёл. Проверки mojibake-маркеров и escaped Cyrillic выполнены отдельно по всему изменённому scope, совпадений нет. Для Git-проверки и разрешённой пользователем публикации использованы `git rev-parse HEAD`, `git status --short --branch --untracked-files=normal`, `git log --reverse --format='%h %s' 37102600..HEAD`, `git diff --name-only 37102600 --`, `git diff --check 37102600 -- <точные пути>`, `git diff --quiet 8e3246c6 -- <java, dist, build.xml>`, `git add -- <точные пути>`, `git diff --cached --check`, `git commit -m`, `git ls-remote --exit-code origin refs/heads/wip/coordination-130-preserved` и обычный `git push origin HEAD:refs/heads/wip/coordination-130-preserved`. Для изолированных копий использованы `git clone --no-hardlinks --single-branch --branch wip/coordination-130-preserved` и `git pull --ff-only` в собственных clone.

Для №22 сохранены `native-20261110-001841.stdout.log`, `native-20261110-001841.stderr.log`, `native130.trace.native-20261110-001841.68640.tsv`, agreement и before/after authority, Goal/Catchup и conservation witnesses в `guardian-pve/runtime-rendezvous`. SHA-256 trace `AA150B63337CD35C5BFA0F79430672CBD43DF7DFFEF763E3325E445484921EC1`. Канонический SQL readback `guardian-pve/evidence/native-22-postflight-sql.log`, SHA-256 `3E4B5D32D2DEE8D2FF006FAA11447CA37C17B862721FB7617E1C7E7C842F777E`.

TASK130 остаётся PARTIAL с подтверждёнными LogicalParty, stock Party, платным GK для пары, совместным прибытием, первым native ударом и штатным STORE. TASK131 (расширенная Party, PvP/PK и Spoil), TASK132 (кланы), TASK133 (рейды, Rift и инстансы), TASK134 (осады), TASK135 (территориальные войны) и TASK136 (Олимпиада) остаются NOT_STARTED. FULL 0/7. Сценарии человека в Гиране и во время Solo-фарма, правдивые ответы в чате и штатное следование за лидером сохраняются в контракте отдельной игровой приёмки. Политика среднего уровня отложена до завершения текущей приёмки.

## 2026-11-10 — проверка production checkpoint `0bd3b33d`

GitHub WIP и независимый checkout совпали на `de3377d385c39627bdbb1fc1844ed4d0346c11f8`. Между `0bd3b33dfbd8168e24a3776237ef2ad5928e8d13` и стартовым HEAD добавлен только HANDOFF. Штатный JDK 25 / Ant `jar` завершился exit 0 за 22 с. В `java`, `dist` и `build.xml` производственный исходник соответствует `0bd3b33d`. GameServer SHA-256 `B5EB774B7707922B9660C34BB31DACB10A90CAFA6BEFE8FEF753207434B2384E`. Проверены инвентарь и побайтовая идентичность 4 761 class entries и четыре runtime dependency SHA. `javap` и native stdout подтвердили происхождение runner. Первый native использовал `PhantomCoordination130Native.class` SHA-256 `0E0E86A32C36E0A8D3E0050B186BC07906B93DCAD87DFE8844ACE9A386B52208`. Точные исходники и байткод этой версии сохранены вне checkout в `short-history/evidence/first-native-runner`.

**До CUT.** В первой отдельной сцене headless fixture дважды остановился на `model.time_budget` с неизменным курсором танка 6 из 14. В этом пути `PhantomBackgroundModel.evaluate` использует предел 60 секунд игрового времени на batch, а `PhantomBackgroundService` возвращает RETRY без продвижения, если batch не меняет состояние. Конкретная длительность действия в остановившемся batch неизвестна. Сохранены обе квитанции отказа и весь SQL-контур. В новой disposable-сцене тот же production Ecology выбрал для fixture короткое начальное продуктивное окно, до четырёх минут. У всех троих history COMPLETE, расписание ACTIVE, consent=true, claims ещё отсутствовали, STORE context COMPLETED. Все были level 76, 931 275 828 EXP, 0 SP и 50 000 аден, на штатной точке сбора Годдарда `(148024, -55281, -2728)`. Headless fixture и read-only preflight завершились exit 0.

| Рубеж | Свидетельство текущей JAR |
|---|---|
| LogicalParty и CAS | Native `native-20261110-102347`, PID 41272. При 0 World создана LogicalParty `22C8D0AD61D5EB72319D7EE903B9C442BE2ACC391DAA3DED36917964A92D9F77`, generation 1. Штатный planner выбрал Yeti 21320, `21320@generated.farm.5241a753fc2261cfc117ac7b.anchor`. SQL post-STOP показал одну logical group и три claims. |
| Native Party и маршрут | Duelist 268480990, Hell Knight 268481003 и Cardinal 268481022 вошли в stock Party `E77B0D612EB639ED9F38833862AB29C5FD3D3455CE20E4DED2359EF53762C068` через штатное invite/accept. В 12:25:42, 12:25:52 и 12:26:02 Europe/Chisinau соответственно прошли GK `leg.872c4a16bdd379d147b68e83`. Каждый заплатил 9 300 аден и прибыл в `(144880, -113468, -2563, instance 0)`. Затем зафиксирован WALK до FARM и повторные перегруппировки. |
| PvE и threat | D03 для Yeti 268451380 показал прогноз 229,25 DPS и 36,89 с. D04 зафиксировал плановую первую цель guardian, stock opening assist 268451377 и резерв 6 с. У guardian подтверждён Combat session для 268451380, в 12:26:58 он завершился `CANCELLED` с нулём pickup attempts. Наблюдался реальный урон танка, у Duelist `damageSequence=0`. Во время этой вертикали не зафиксированы подтверждённые групповой kill, EXP/SP или loot receipt. Отсутствие дропа в отсутствие kill не оценивается. |
| Heal | Cardinal применил `1217:33` к Hell Knight. HP в выборках 3779,2837 → 4095,4191, отдельная штатная квитанция зафиксировала 516 восстановленных HP. MP 2480 → 2384. F8 ordinary Player прошёл до CUT на той же JAR, эффект 48,0249 HP и расход 5 MP. |
| DRAIN и прежние epochs | После смерти танка в 12:28:14 группа перешла в `DRAINING:WAIT_PARTY_JOURNEY_OWNER_LOST`, затем последовали stock release и передача Execution. Штатный cleanup запросил STORE хилера в 12:29:03 и танка в 12:29:07 для прежних epochs. Затем появились новые epochs у этих двух профилей. На последней выборке 471,374 с лидер оставался в своём прежнем epoch `1104611977688400`, в World присутствовали все три профиля. Полный естественный ноль World и возврат logical group в BACKGROUND остаются открытыми. |
| STOP и SQL | `NATIVE130_FINITE_EVIDENCE_DEADLINE`. Stock STOP и Hikari close завершились, JVM exit 0, launcher exit 1. Post-STOP все три контекста STORE `COMPLETED`, prepared=false. XP/SP по профилям 1/3/4 соответственно 931 275 828/0, 925 775 828/0, 925 775 828/0. У всех 40 700 аден. Сохранена та же группа с тремя claims в DRAINING. |
| Restart | NOT_RUN. `native130.complete` и `native130.persisted` отсутствуют. |

В этой попытке не установлено, какая первая проверка отмены прервала Combat, и какое условие удерживало последний старый epoch лидера. Наблюдаемая последовательность отделяет эти вопросы от факта смерти и последующего `WAIT_PARTY_JOURNEY_OWNER_LOST`. Следующий дешёвый проход должен сверить конкретные причины tactical admission/cancellation в `PhantomPartyCoordinator.dispatchTacticalCombat` и `L2jPhantomPartyBackend.autonomousFarmEncounterReady`, затем причину RETIRE_IDLE и владельца повторного физического допуска в Scheduler, Presence и Background. Для STORE нужен regression с двумя законно завершёнными epochs и одним удержанным, с проверкой нового допуска и сохранением human, native journey и in-flight guards. Повтор с той же сцены и тем же production поведением не обоснован. Для native-проверки будущего production-исправления после RED→GREEN потребуется минимально одна дополнительная clean JAR. В оставшемся бюджете есть два bootstrap и один условный restart.

**MODEL и SQL.** RED проверки наблюдателя 72/73, подтверждённый stock no-drop блокировал EXP/SP. GREEN 73/73 на исходном и усиленном runner. `party-tactics` 13/13, `party-lifecycle` 34/34, `party-route` 9/9, `background-model` 10/10, `combat-action-ownership` 37/37, `party-canonical-invitation` 6/6. Ранний запуск Ant MODEL из checkout прошёл world 72/72 и остановился на relative `statBonus.xml` в tactics. Из штатного `dist/game` существующий runner завершил все указанные MODEL exit 0. F7 проверен отдельным `authority-f7` на той же JAR в новой пустой схеме `l2jmobiush5_phantom_test_130_first_party`, 127.0.0.1:33306, SQL exit 0. Совпавший и пустой hash применены, нетегированное несовпадение вернуло OFFSCREEN_WAIT, дубль идемпотентен, stale root отклонён, EXP/SP неизменны. Проверка stale logical preparation signal также прошла.

**Scope и ревью.** Изменены два существующих Java runner, один focused MODEL и существующий PowerShell launcher. Аудит stock item log ограничен TEST130, привязывает CREATE к точному reward target и PickupReceipt по source item, счётчику и epoch. После ревью усилены fail-closed на несовпадении stock log и проверка bytecode linkage. Проверка первого точного encounter остаётся строгой, чтобы поздний kill не скрывал непроверенный pickup первого. Новый аудит проверен MODEL и bytecode, его положительное срабатывание остаётся NATIVE OPEN. P1 текущей вертикали — незавершённый естественный DRAIN. P2 первой pre-CUT сцены — повторяемая остановка Catchup на лимите модели. Изменения игровых механик, rate и production policy в scope этой публикации отсутствуют.

**Сохранность.** G0 проверил владельца, PID, FILETIME, loopback, образ, datadir, отсутствие registered service и grants только своей схемы. Native SQL PID 25120, config SHA `E56631E6CAFC6BE936E0A0BFCD21B9CB7BDB0D158064630F21E4310851CA701D`. После STOP сверены 203 geo-файла по размеру и SHA и все 207 protected files. Собственные SQL PID 51736, 25120 и 63664 остановлены штатно. Игровые listeners и JVM закрыты. Исходный listener 3308 остался PID 8692. После CUT игру изменяли только штатные owners.

| Свидетельство в `short-history` | SHA-256 |
|---|---|
| `runtime-rendezvous/native-20261110-102347.stdout.log` | `006632567C2C543D0C3EC9177C6CA8F38086D0E297B9A875D67B9D30CA595AF8` |
| `runtime-rendezvous/native-20261110-102347.stderr.log` | `C7B2BA8F983BE3C6FA4CF1C512CD9D5D931948339AB4F66C7A2EBD10ACC4688F` |
| `runtime-rendezvous/native130.trace.native-20261110-102347.41272.tsv` | `3E1354A4A44C044B783F48D83F80974039F1C54E9C074450867791455718F0F0` |
| `runtime-rendezvous/agreement130.native-20261110-102347.41272` | `94049E810C90E2ECB2BD0910A5AFB17FE89737B98C7C57D170BC4514DB3C195B` |
| `runtime-rendezvous/preflight130.native-20261110-102347.41272.before.txt` | `655EE9393E141DCD6F0FBF5F96581CB7E7EEDD2607500E9F41B6F7DA4015C341` |
| `runtime-rendezvous/preflight130.native-20261110-102347.41272.after.txt` | `F35C6C2C39F07A7B2D182A8BE01ADEA02D84E8391E6502F88B78F0B9091C3F38` |
| `evidence/sql-precut.log` | `376C78ED75CCA705A9F431CF45A9C43577A295E38424325EDB8444B112364FBE` |
| `evidence/sql-poststop.log` | `B7399ABA5CF5470E9E9768BFC127BEB46D415A4F4048EC218B8D268FD8C9F6FD` |
| `evidence/f7-sql.log` | `546BC72EBEFEC808301412F3061306014237AC4606CC42D570C5774F86FFA6C9` |
| `runtime-rendezvous/geo203.json` | `A8A4B73BC7B53BED28F545CD889F489492B585B369F8E84988F8DB8A91C973D5` |
| `evidence/protected-pre-native.json` | `9777AB48BC8D14BDEE77D4ACA8E7E650CB099F03CA33FEA108D5CDB941C59DCD` |

Бюджет текущей итерации **1/1 clean JAR, 1/3 фактических GameServer bootstrap, 0/1 условных restart**. Native длился около 8 мин 50 с. Headless подготовки и SQL не вызывали GameServer.main. Новые полезные действия на этой JAR включают три платных GK и один боевой Heal. Kill/EXP/SP из №23 остаются историческим доказательством. A–C имеют прежние MODEL/SQL/Geo результаты и текущие точечные regressions. D и E открыты. F7/F8 прошли текущие targeted проверки. G содержит неподтверждённый полный native gate и открытый P1 DRAIN.

### Git и проверки текста

Разрешение на bounded inspection, независимые clones и scoped публикацию в WIP дано текущей целью. Git использовался в собственных clones. Для чтения старого clone из sandbox применялся command-local `-c safe.directory=<exact clone>`.

- Для начальной сверки выполнены `git rev-parse HEAD`, `git rev-parse HEAD --git-dir --git-common-dir`, `git symbolic-ref --short HEAD`, `git status --short`, `git status --short --branch --untracked-files=normal`, `git remote get-url origin`, `git ls-remote https://github.com/kpCat/L2J.git refs/heads/wip/coordination-130-preserved` и `git ls-remote --exit-code origin refs/heads/wip/coordination-130-preserved`.
- Для двух независимых контуров использованы `git clone --no-local --depth 20 --single-branch --branch wip/coordination-130-preserved <source> <destination>` и `git remote set-url origin https://github.com/kpCat/L2J.git`. Обе копии закреплены на стартовом HEAD.
- Для scope выполнены `git diff --stat`, `git diff --stat 0bd3b33d… de3377d3…`, `git diff --name-status a30b4aea2… 0bd3b33d…`, `git diff --unified=2 a30b4aea2… 0bd3b33d… -- <три production и два test пути checkpoint>`, `git diff -- <пути runner>`, `git diff --name-only`, `git diff --check -- <точные пути>`, `git diff --quiet 0bd3b33d… -- <java, dist, build.xml>`. При разборе отказа сборки прочитано `git config --show-origin --get core.autocrlf`.
- Публикация использует `git add -- <четыре файла runner и этот RESULT>`, `git diff --cached --name-only`, `git diff --cached --check -- <точные пути>`, `git commit -m "Record TASK130 final-candidate native and SQL evidence"`, `git merge-base --is-ancestor de3377d3… HEAD` и `git push origin HEAD:refs/heads/wip/coordination-130-preserved`. Для финальной проверки HEAD применяются `git rev-parse HEAD` и повторный `git ls-remote --exit-code origin refs/heads/wip/coordination-130-preserved`.
- Mojibake-маркеры в изменённых файлах проверены отдельно.
- Escaped Cyrillic и XML numeric references в изменённых файлах проверены отдельно.

**TASK130 PARTIAL, TASK131–136 NOT_STARTED, FULL 0/7.**
