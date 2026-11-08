# TASK130 — исполнение группового контракта

Статус: PARTIAL_SOURCE_SQL / NATIVE_GATE_REQUIRED. FULL130=false; TASK130–136 FULL до/после: 0/7 → 0/7, TASK130 PARTIAL. Исходный WIP HEAD: `8140167705e6a6e76f370e3aa772b50f909b4d37`. Production source commit: `f9edb77a4d09316bf10e42cae5cb8811e26642ac`. Первый опубликованный checkpoint: `a7cff727a86fe61c4a5049f7f5440fc05620e8be`; ниже отдельный test-observer follow-up. Публикация разрешена только в `wip/coordination-130-preserved`.

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
