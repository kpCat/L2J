# PHANTOM-INTERREGIONAL-TRAVEL-CLOSURE-091

Дата: 25.10.2026. **TOTAL091=PARTIAL; FULL091 не закрыт; OWNER_QA_PENDING.**

Получена настоящая цепочка одного Orc80: native WALK из стартовой области → NPC30576 NORMAL index0 → списание **23000 Adena, 200000→177000** → Gludio **(-12787,122779,-3107), instance0**. Это native primitive proof в test fixture, а не закрытый автономный production farm goal. Human/Elf, remote farm/reward и durable paid resume не доказаны.

## Authority и read-first

Git root определён фактически; ветка `feature/phantom-world`. До правок local HEAD, tracking `origin/feature/phantom-world` и live GitHub равны `627bca947fd565ce18aa5cc85c7e1aa1288ccaef`. Tracked dirt отсутствовал. Все **9 файлов, перечисленных MANIFEST_SHA256.json**, совпали по размеру и SHA256; сам manifest исключён своим форматом. Пакет089 присутствует в HEAD. Пакет091 до задачи был untracked и сохранён без правок.

Прочитаны root/module AGENTS, README корня, релевантные master plan/current status/workflow/release/owner targets/config contract/task standard, пакеты091/089 и reports087/089/090. Прочитаны production planner/visible travel/normal GK/navigation/state codec/transaction/checkpoint/native authority, native Player/Creature/Teleporter/TeleportHolder/Geo/World API, source producers и ближайшие tests/wrappers. Дополнительные AGENTS в целевых parents/subdirectories, модульный README.md и отдельный актуальный travel pattern/code-map не найдены. Исторические C:-пути не использовались как authority.

Локальные аналоги: episodic WALK089; stock NORMAL bypass/fee/landing; owned visible checkpoint; exact isolated lane/bootstrap087. Java25/Ant, существующие Navigation/Topology/Background owners и отсутствие per-Player production threads сохранены. Studio, другие хроники, Quest/rates/native engine/SQL schema sources не изменены.

После первого native proof владелец уточнил ограниченный последовательный объём: не развивать длительные Elf Geo циклы, при архитектурной границе оставить PARTIAL; отдельно разрешил безопасный emergency fallback либо точку интеграции и последующий отдельный OwnerConfig commit. Новые настройки не применялись к TEST091. Исторические `TOTAL087=PARTIAL`, [TOTAL089=PARTIAL](PHANTOM-NATIVE-REGION-TRAVEL-089.md) и report090 не переписаны.

## Причинная граница и реальное изменение

| Участок | Наблюдение / результат |
|---|---|
| Native source → D1 graph | Starter NPC30006/30146/30576 существуют; certified runtime catalog остаётся54 legs/7 source NPC против256 NORMAL facts/26 source NPC. Новых background edges нет. |
| Historical selection | `PhantomHistoricalBackgroundPlanner.candidate()` по-прежнему требует certified route. Общий graph0 gate **не устранён**. |
| Visible execution | `PhantomVisibleFarmTravel.proposedStep()` остаётся связан с этим route; production caller episodic WALK **не подключён**. Native fixture вызывает существующую Navigation явно. |
| Локальный 3D frontier | RED показал: первая плоская успешная точка скрывала уже доказанный подъём. Добавлена сортировка projected candidates по расстоянию и различию Z перед native queries. Каждый сегмент по-прежнему требует native proof. Это ограниченное исправление порядка; универсальная иерархическая навигация и Elf этим не объявлены исправленными. |
| Durable journey | Production marker/INTENT/ARRIVED protocol **не реализован**; codec/state/transaction bytes и APIs не изменены. |

Production diff — пять добавленных строк в `PhantomWalkCorridorPlanner`. Не менялись128 nodes/512 edges/32 native queries/4096 labels,64 waypoints,2 workers,120s attempt, queue/cooldown. Новый bounded surface ordering не заменяет глобальный граф.

**ARCHITECTURE_REVIEW_REQUIRED — foreground goal + checkpoint operation identity.** Complete `PhantomTravelStrategy.Candidate` нельзя использовать как неподтверждённое намерение. Goal parser допускает лишь прежние constraints. `captureVisibleArrival()`/`VisibleCheckpoint` идентифицируют checkpoint через profile/object/epoch/goal/revision/anchor; для INTENT и ARRIVED на одной lineage нужны разные phase/operation identities, иначе повтор key нельзя считать новым сохранением. Один лишь route-absent fallback или journal-файл создаст ложный proof либо риск повторной оплаты. Под уточнённый ограниченный объём непроверенный paid persistence не добавлялся.

Конкретное предлагаемое продолжение в тех же owners: отдельное typed foreground intent с tier `FOREGROUND_NATIVE_PENDING`; optional marker в canonical `background.state`, сохранённый всеми шестью production copy owners; phase-specific owned checkpoint key; INTENT до native action вне ActionLease, ARRIVED вместе с настоящим owned snapshot; ambiguous INTENT fail closed. Historical background остаётся только certified. Нужны codec/copy/CAS/restart focused contracts до следующего production debit. Это предложение, **не IMPLEMENTED/PASS и не новая автоматически начатая задача**.

## Один guarded TEST091

`R=<repo>`, `M=R/L2J_Mobius_CT_2.6_HighFive`, `S=<private TEMP>/own091`, `C=S/source`, `W=M/tools/phantom-runtime/Task091.ps1`. Physical shadow вне Git root, owner/SYSTEM private ACL, reparse guards, SHA-checked source/copy inputs,203 Geo tiles read-only из ранее разрешённого068 Geo source. В начальной копии28806 module files. Credentials/owner UUID/private config не публикуются.

Read-only preflight без default catalog подтвердил отсутствие **l2jmobiush5_phantom_test_091** и namespace CREATE privilege. Четыре DB-free negatives: protected078 URL, неверный UUID, source config вместо test config, foreign087 native flag — отказ до SQL. Bootstrap повторно проверил отсутствие/foreign connections:123 canonical scripts,18 absent `DROP IF EXISTS` пропущены; **DROP/TRUNCATE/DELETE sent=0**. Schema authority task91 и private installation identity проверены. Canonical SQL digest `8699acdd436d29f5666ccc501e6b8d9a09265f064fc16ab7a7166be04c84aea2`.

Создан один настоящий native Player: Orc, class115 Dominator, level80, profile1, object268480990, access0. Native ingress `population.ingress.orc.01`. Начальные EXP/skills/S-grade items и200000 Adena — **TEST_FIXTURE**, не earned. После receipt не было выдач/смены класса/уровня/ручных awards. Source GK выбран из actual stock spawned NPC + NORMAL TeleporterData; координаты не подменялись готовым маршрутом. `setXYZInvisible` использован только в первоначальной подготовке ingress, до измерения.

| Phase | Physical evidence | Wallet / outcome |
|---|---|---|
| Initial JVM41864,06:05:32–06:08:37 UTC | Настоящие MOVE_TO; sampled5385 units. Сценарий преждевременно останавливал Player за34–46 units до waypoint; следующий прямой сегмент не имел proof. Exit1 `NATIVE_SEGMENT_PROOF`. |200000, debit0; orderly cleanup |
| Единственный causal continuation, JVM41992,06:23:35–06:26:51 UTC | Тот же persisted actor и все objectId/count/equipped items, class/level/wallet подтверждены. XY продолжения(-51402,-113434); Z стал-168 вместо последнего sampled-156 при native cleanup/restore. Это не exact-XYZ paid resume. После исправления сценарий ждёт окончания native MOVE_TO, не останавливает его по radius.3 episodes, sampled8197 units, native path/segment validation и progress tracker. |200000 до GK, без reseed/repair |
| Source arrival | Реальный Player в(-45264,-112512,-240), instance0; `Npc.canInteract=true`. | Quote не засчитан как debit |
| Native action | `PhantomNativeTravelActions.gatekeeper` → stock `Npc.onBypassFeedback("teleport NORMAL 0")`; NPC30576, native XML town/30576.xml. |**ARRIVED, fee23000;200000→177000**, actual landing(-12787,122779,-3107) |

Всего одна схема/кампания, один созданный Player, один контролируемый unpaid sameDB continuation после конкретного дефекта сценария. Второго reseed/campaign/Elf Geo sweep нет. Each WALK request сохранял прежние native limits; fixture ограничена16 episodes/40000 sampled units/120s movement на JVM. Реально6 submitted episodes, sampled суммарно13582 units. Один общий test loop не является новым production navigation engine.

`first-payment.intent` — только one-shot test boundary с CREATE_NEW; **не canonical durable marker, не proof production idempotence**. Первый процесс не дошёл до него; continuation запрещён при его наличии. Saved interrupted **paid** journey/new-JVM continuation не выполнялись. Current paid Player сохранён стандартной dematerialization; earn/reward не заявляются.

## Проверки и receipts

JDK Temurin25.0.4.1+1-LTS; Ant1.10.17. Ant только последовательно в S; root `build/` и source `dist/libs` не использованы как outputs.

| Команда / route | Exit / факт |
|---|---|
| package SHA + branch/local/tracking/live |0;9/9 payload files, baseline627bca947fd |
| source OwnerConfig.ps1 -Action Verify |0;68 files/272 overrides/conflicts0/changed0 |
| W Prepare, Refresh, Compile |0; physical SHA copy, byte-checked preimage backups; fresh full then justified incremental compile-tests |
| Focused RED → frontier correction → Focused GREEN |1→0; flat-first assertion реально упала; одна synthetic surface-choice группа, native actions0 |
| Preflight / Configure / Guards / Bootstrap |0/0/0/0; source config immutable; separate private TEST config |
| Native / ContinueNative |1/0; causal endpoint defect then actual WALK/paid arrival above |
| Connections после native |0; `TASK091_CONNECTIONS_ZERO` |
| Fresh Focused / Travel089 / WalkCorridor064 / NormalGatekeeperPure |0/0/0/0 |
| PhantomTestLauncher navigation-core20260725001 |0;51/51. Первый CLI launch ошибся в группировке-D argument до runner; исправленный запуск PASS. |
| ant -f C/build.xml -Dbuild=S/build -Dlibs=S/jar-libs jar |0;22s; vendor libraries copied byte-exact, JAR outputs separate from pinned source libs |

GameServer.jar SHA256 `6be6379bf8d50b2f3d5166fd69d3cb58a31b44332a629207f6df2188d6e98546`; LoginServer.jar `b7aa763c3e23ea6d48b0dcb063e79813262e63875b1ceeaaf9a17c96ac550adc`. Native proof использовал fresh class directories перед final packaging, а не запуск нового JAR/server/client. Пользовательская поставка/ZIP не создавались.

Final source SHA256:

| Owner | SHA256 |
|---|---|
| PhantomWalkCorridorPlanner.java |a3eca8986c4f66108d2dfa0c38d6dc124a3b002bf84bd1522acabd1b8ce92482 |
| PhantomHeadlessPlayerTestEnvironment.java |328da7534a72051d57524e6e4d2b435468886c51b416aadd115e5b8c91bb9edf |
| PhantomTravel091Test.java |8a04cfe56d417951c3729608468d0306f46a98122c8bd9b4338a40fe9350eb66 |
| PhantomTravel091Native.java |40600d1fa12485027bc25796062354ccee23891d644b7c9cf151ce6a230acc92 |
| PhantomTask091Lane.java |2c6dbd9af2fb4da7dc3036bd16c7b3dc11784f130baa39141d5160c90b97dc67 |
| interregional_travel091.py |edc3eae5aa0b34d9910dad002b34fdc85a42dff9fb6809c965f876dc06f81a38 |
| Task091.ps1 |bc84e21698e1cd91d207d5ec97f1d2ddd6b530547bdcac600cb93b664a4dc906 |

Native-run inputs.json digest `75c4dac32678b97875810f0a2f9dc3184fced5bd303888952ca000a718959cef`; first-walk TSV `fa803b2815297de72aab05fd3eb603f4097ab9b5b5a2e9dd2fb344d475b815db`; continued-walk TSV `fe29d39f17f984eda8ec09d60e86407db7c51920f5d551f87720f48955cb411f`; actual-payment TSV `5520c32568d4a44a6c56363108c46e1ba4ea5b7b4180318b74a2700050cb1e86`. Previous source/class/pin/owner receipts retained in SHA-checked own backups.

Первый cached diff check нашёл лишние пустые строки EOF в copied test lane, exit2. Они убраны двумя bounded trims; final incremental compile exit0. SHA256 `PhantomTask091Lane.class` до/после **одинаков**: `4cb97191771cc5b7a54a0077f693f6643c8637d4ffcbef70b66b2ed73f5d36af`. Native execution source lane до EOF-trim имела hash1667a4e500d7348f6a642397fb218c8f6ab382e8147557af98b14b74b0728bc5; после byte-checked refresh final inputs.json `1c1a6dfe71f5ac816e4839cec0ed8a41117e7c65d7a843cb929a45d58835ec71`. Повторного native Player не запускали; production/JAR bytes не менялись.

## Acceptance091

| Gate | Result |
|---|---|
| P0 safety/baseline |PASS для выполненной isolated scope;203 Geo/new schema/guards/config090 preservation. Protected078 contents не читались для hash audit. |
| P1 native source → production plan |OPEN: native source verified, common graph0/foreground admission не реализованы. |
| P2 physical first mile |ORC_NATIVE_PRIMITIVE_VERIFIED; Human/Elf/Giran control NOT_RUN. Не full H/E/O PASS. |
| P3 paid GK |ONE_FAMILY_NATIVE_VERIFIED,1 nonzero debit23000; требуется2 families, gate OPEN. |
| P4 remote farm |NOT_RUN; нет selected autonomous farm goal, native attacks, HP deltas, earned XP/SP/Adena/items. |
| P5 paid restart/idempotence |NOT_RUN; unpaid actor restore не заменяет paid marker/resume. |
| P6 safety/performance |Four namespace guards + focused route bounds verified; actual insufficient funds/siege/karma/stale goal/epoch/death/Party/Store/payment replay/codec marker negatives NOT_RUN. |
| P7 build/delivery |COMPILE/JAR/FOCUSED_VERIFIED; exact scoped PARTIAL publication, final SHA/parity receipts — в итоговом сообщении. |

`IMPLEMENTED`: bounded surface ordering и test-only proof/admission tooling. `FOCUSED_VERIFIED`: перечисленные routes. `SOURCE_GEO_VERIFIED`: новых standalone source-only Geo campaigns нет. `NATIVE_VERIFIED`: только один Player primitive WALK/GK/payment/arrival. `OWNER_QA_PENDING` сохраняется.

Process wall185.0s и195.6s включает input attestation/native infrastructure, не является pathfinding benchmark. Native movement sampled distance приведена выше; configured heap cap4GiB. Native query totals, actual peak heap/GC/CPU, p50/p95 и gameplay-scale128/5000 **NOT_MEASURED/NOT_RUN**. Никакой массовой материализации/soak нет.

## Emergency policy и STOP

Аварийная телепортация разрешена владельцем как bounded product fallback, но **не реализована и не использована**. Точная точка интеграции — existing `PhantomVisibleFarmTravel.replanEdge()/retryOrFail()` после конечного native отказа и перед terminal route failure. Нужны same-instance intermediate ROUTE/town anchor, current Geo/NSWE/door/Seven Signs proof, отсутствие реальных observers у обоих концов, idle/no combat/PvP/Party/store/protected-work owner, отдельные finite retry/cooldown/counters и truthful FALLBACK tier. Простая смена XYZ после SEARCH_LIMIT этих guards не доказывает. Цена GK/конечный farm arrival не обходятся; World Navigation Atlas не добавлялся.

## Scope и публикация

Source audit для отдельно разрешённого Premium config: `Attackable` и `Party` применяют premium XP/SP поверх native reward; `Quest.addExpAndSp` применяет premium quest XP/SP и затем `Rates.ini` quest multipliers. Grouped/ungrouped drop в `NpcTemplate` используют один `PremiumLootPolicy.itemMultiplier/combinedChance`; Spoil использует аналогичные chance/amount ветви. Server/champion rates остаются снаружи premium bonus, не заменяются cap. Runtime winnings/loot после новых значений **NOT_RUN**.

Общий chance cap применяется даже при `premium=false`: `min(cap,1×nativeItemBonus)`. Поэтому разрешённая смена3→5 может увеличить native drop/spoil bonus обычного nonpremium Player с bonus>3; при bonus1 ничего не меняет. Premium ordinary chance5 с native bonus2 ограничится5, не10. Cap не ограничивает количество предметов. Это не выдача подписки. `PremiumLootPolicy.eligible` и `PremiumManager` продолжают исключать managed/headless Phantom; их Java не изменяется.

Обе per-ID строки сохраняются byte-for-byte: Adena57 — premium chance2/amount2; rare entries chance1/amount1:6656 Earring of Antharas,6657 Necklace of Valakas,6658 Ring of Baium,6659 Earring of Zaken,6660 Ring of Queen Ant,6661 Earring of Orfen,6662 Ring of Core,8191 Necklace of Frintezza,10170 Baylor's Earring,10314 Ring of Beleth. Имена сверены с native item XML. Эти исключения имеют приоритет над ordinary premium5; server/champion/native bonuses и собственные roll/amount rules остаются действующими. Фактические новые INI/metadata, Save/Apply результаты и hashes фиксируются отдельным дополнением к owner config contract после nav publication.

Bounded exception18 paths одной travel-задачи:7 связанных source/test/tool files,1 report,10 неизменённых task input documents. Exact source paths: `java/.../navigation/PhantomWalkCorridorPlanner.java`; `test/java/org/l2jmobius/tests/phantoms/{PhantomHeadlessPlayerTestEnvironment,PhantomTravel091Test,PhantomTravel091Native,PhantomTask091Lane}.java`; `test/interregional_travel091.py`; `tools/phantom-runtime/Task091.ps1`; этот report; все10 exact filenames исходного пакета091. Никаких иных tracked изменений.

Git разрешён /goal, TASK091 и дополнением владельца. Inspection: `git rev-parse --show-toplevel`, `git symbolic-ref --short HEAD`, `git rev-parse HEAD`, `git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}'`, `git rev-parse '@{upstream}'`, `git for-each-ref --format='%(refname) %(objectname)'` для двух feature refs, `git ls-tree HEAD --` для exact089/091 package paths, `git status --porcelain=v1 --untracked-files=all` начально и затем `--untracked-files=no/normal` bounded scope, exact `git diff --numstat --`/`git diff --`. Первый sandbox `git ls-remote origin refs/heads/feature/phantom-world` отказал по сети; host read-only повтор exit0. Один начальный all-untracked listing оказался чрезмерным; повторных обходов огромных старых untracked не было.

Delivery использует только exact allowlist A: `git diff --check -- A`, CRLF-aware вариант при необходимости; `git add -- A`, `git diff --cached --check -- A`, `--name-only/--stat/--raw --no-abbrev` и full exact cached diff. Commit subject: `phantom(task-091): advance native travel with open gameplay gates`; `git push origin HEAD:refs/heads/feature/phantom-world`, затем local/upstream/live parity. Создающий commit не содержит собственного SHA; фактические SHA/push/clean и post-publication receipts фиксируются в итоговом сообщении.

Source Rates.ini `RateQuestReward=50`, остальные090 values и все272 overrides при native phase неизменны; source Verify changed0. Новые утверждённые Phantom/Premium settings выполняются **только после этой публикации**, отдельным configuration commit и staged Apply/Verify; не переоценивают native evidence091 и не запускают5000 profiles/200 stores/128 Player.

Own native JVM41864/41992 завершены, ThreadPool/Hikari закрыты штатно; exact091 connections0 проверены. Схема091/собственные private artifacts сохранены, не DROP/reset. Protected078, старые DB/JVM/worktrees/MAIN/PLAY/client не изменены; protected hashes не заявлены проверенными. Старые пользовательские untracked не stage/clean/move; unreadable ACL contents не обходились. Новых Git worktrees, history edits, force push нет. **STOP: следующая задача не запускается.**

Pre-publication exact scope/strict UTF-8:18 files, outside tracked0, package9/9 SHA unchanged. Первая объединённая shell-команда завершилась exit1 только потому, что Get-Process не нашёл java/javaw; preceding scope/encoding и OwnerConfig Verify завершились успешно. Это не test failure.

- mojibake-маркеры в изменённых файлах проверены:18 files, полный набор27 markers,0 совпадений.
- escaped Cyrillic в изменённых файлах проверены:18 files,6 regex patterns,0 совпадений.
