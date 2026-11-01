# PHANTOM-LIVING-CYCLE-111

Дата: 01.11.2026. **PARTIAL_DELIVERED; FULL111=PARTIAL; ORDINARY_LOOP=INCONCLUSIVE.** Полный автономный native PvE lifecycle не подтверждён. Native primitive не проводился; PRIMITIVE_ONLY не объявлен.

## Source и поставляемая часть

Local/upstream/live перед работой и native G0: `feature/phantom-world`, `810d905acc3bbe46ce68e8fd037486921907a08f`.
Source получен через внешний `git archive`, без переноса WIP109. Code commit: `b91a42faafabd0d04f0b24d890510e25625cc730`.
Единственный новый GameServer JAR: SHA256 `85fa722eec70883f159df9f3d143e34e9a996683d8269c8239af620e4a489395`; обе игровые JVM использовали его неизменным.

Exact delivery allowlist, относительно High Five:

1. `java/org/l2jmobius/gameserver/phantoms/economy/PhantomAutonomousMarketProducer.java`.
2. `java/org/l2jmobius/gameserver/phantoms/economy/PhantomStoreService.java`.
3. `java/org/l2jmobius/gameserver/phantoms/background/PhantomHistoricalBackgroundService.java`.
4. `test/java/org/l2jmobius/tests/phantoms/PhantomLivingCycle111Test.java`.
5. `docs/phantoms/reports/PHANTOM-LIVING-CYCLE-111.md` — этот отчёт.

Реальная потребность/quote теперь может рассматриваться во время собственного фарма, до общего quiet. Движущийся farm допускается только при точной healthy AutoPlay session того же root; чужое движение этим не разрешается.
StoreService сохраняет конечный `market.rest.proposed` child без принятия travel ownership. Штатный AutoPlay завершает свой текущий target/threat/loot с неизменным сохранённым deadline.
После yield Market закрывает существующий Decision fence, повторно проверяет in-flight/persistence/root/quote/конкурирующих owners и выполняет admission CAS в существующем Goal codec. Только readback-confirmed admitted child становится recovery/travel owner.
Неоднозначный admission CAS не превращается в clean refusal: исключение сохраняет закрытый fence и останавливает Market admission. На холодном старте непринятое предложение отменяется существующим dormant cleanup, root не переписывается; поздний recovery также не принимает cold proposal.
Stale/expired live proposal очищается через существующий exact-row `discardRecovery`, с RETRY и cooldown. Legacy accepted/paid-resume и FAILED payment marker сохраняются.
Navigation, native WALK/NORMAL bypass, полный remaining-GK/BUY reserve и canonical arrival/STORE не заменялись. Новый AI, root store, SQL schema продукта, timer, provider или Player/Inventory writer не добавлены. Общая atomicity native writers не заявлена.

## Causal/writer audit

| Состояние | Владелец и граница |
|---|---|
| PvE root | Historical planner/Decision/GoalStateStore; revision/CAS; принятый intent защищает root |
| Потребность/child | Market → StoreService; actual stock/quote/root/object/class; finite TTL; отдельный durable proposal/admission reason |
| Текущий фарм | VisibleAutoPlay command gate, exact native owner/epoch, target/threat/loot; затем yield |
| Travel | Navigation request/result; Market ephemeral route; exact OwnedWalk cancellation, busy retention и factual-XYZ replan |
| Деньги/вещи | Stock Inventory/reward/commerce owners; NORMAL Npc bypass; FAILED terminal fence до неоднозначного платежа |
| Position/STORE | Background.captureVisibleArrival → PlayerNativeWork checkpoint → authority/CAS/publisher |
| Restart | Принятый child восстанавливается существующим recovery; proposal отменяется, uncertain payment не replay |

Независимый review нашёл P1: временное открытие recovery fence одновременно открывало Decision для Learning. Такой вариант исключён; подтверждение owner выполняется под закрытым fence после yield.
Review также закрыл P2 live-proposal cleanup. Self-audit воспроизвёл отдельный restart-дефект: proposal имел accepted reason. Добавлено durable различие и cold cancellation. Финальная независимая source-проверка: **P1=0/P2=0 для safe subset**, не для всего native lifecycle.

## Geo, G0 и единственная TEST111

Geo найден по exact TASK098 receipt: `%LOCALAPPDATA%/Phantom-City-Life-098-cac52c61-ffe6-40de-b2b8-6694bdd7c40b/`.
203 `.l2j` сверены с историческим `inputs.tsv`; только tiles скопированы в новый physical owner+SYSTEM shadow. Source/target SHA всех203 повторно совпали после кампании. Старый stage не менялся; его SQL/JAR/credentials не копировались.
Новая среда: `%TEMP%/Phantom-Living-Cycle-111-acd4f6ae-4650-455b-8130-a7e40920aceb/`; UUID совпадает с suffix; `PathFinding=2`, `GeoDataPath=./data/geodata/`, cwd=`dist/game`. Оба настоящих boot сообщили `GeoEngine: Loaded 203 regions.` Это не доказательство выбранного полного пути.
G0: source/JAR/29074 native inputs, физические paths/SID/private ACL, Geo203, отсутствие чужих JVM/game TCP/SQL sessions, metadata visibility и schema absence проверены до создания.
Создана ровно одна отсутствовавшая `l2jmobiush5_phantom_test_111`: 121 pinned stock SQL-файл,203 statements;18 stock DROP пропущены; исполненных DROP/DELETE/TRUNCATE/adopt/reseed0. Own authority table содержит UUID/source/input pins.
Private create-only SQL/ACL guard переиспользован из102 с новым111 namespace. Read-only wrapper запускал штатный GameServer, canonical PopulationStore и обычный scheduler; он не управлял actor, goal, NPC, inventory или XYZ.
Shadow-only diff: population5000→12; ACTIVE128→4; materialized cap128→4; scheduler profiles/pulse128→16; offline merchant bootstrap True→False; game bind0.0.0.0:7777→127.0.0.1:17777; отдельный private DB URL/pool4. Источники/OwnerConfig, рейты, funding/loot/progression rules не менялись.

## Фактическая native кампания

Две игровые JVM, одна DB и один JAR: первый обычный Main →240s bounded sampling →stock STOP/exit0 →SQL/JVM/TCP postflight →новый Main на той же DB →90s sampling →stock STOP/exit0.
Main создал12 READY identities через canonical creation saga. Предварительно готовый actor не требовался; fixture actors, goal injection, GM, выдача денег/уровней/предметов и relocation отсутствовали.
Deterministic selection следовала TASK predicate и profileId order: scheduler ACTIVE + World/current owner + ACTIVE PvE root, затем actual need/funds/route. Уже первые обязательные native predicates не дали кандидата: в288 первых и108 restart snapshots все12 были SLEEPING/World-cohort=false, Market child=NONE. Cost/quote selection поэтому не выполнялся; отсутствие мотива/дороги как универсальный факт не заявляется.
Сохранены все12 profileId и goalId. Revisions изменились: обычные farm roots получили revision1, профили9/11 перешли в `idle.background.history / planner.target_or_route.absent`. Во втором окне их revisions выросли58→1081 и64→1072. Конкретная причинная трасса такого частого idle update не закрыта; это нельзя засчитать как доказанный конечный полезный replan.
Inventory/component hashes между остановками изменились; финальные characters имеют levels2–3 и суммарно60454 persisted Adena. Это состояние обычного runtime/background, не agent grants и не доказательство native earned activity. Индивидуальные причинные receipts этих изменений не получены.
Selected ordinary object/epoch/route/NPC/canInteract/actual fee/debit/arrival: **NOT_PROVEN**. Измеренных native поездок и платежей0; новые private stores0. PvP/PK не форсировались, отсутствие конфликта не считается ошибкой.
World observations — read-only cohort cuts раз в10s; краткие промежуточные появления не исключены. Observer не создавался, known-list/client visibility — NOT_RUN; owner client QA остаётся отдельно.
Оба stock STOP: `Initial subsystem drain ... stopped=true` (11/13ms), successful shutdown5s, actual exit0/0. Финал: own JVM0, TCP17777=0, global foreign SQL sessions0, characters online0;12 characters/profiles,84 components, offline stores/items0. Среда/DB сохранены intact, дальнейших запусков нет.

| Gate | Результат этой задачи |
|---|---|
| A Geo/G0 | PASS для inputs/isolation/bootstrap; физическая дорога не подтверждена |
| B ordinary causal actor | INCONCLUSIVE: нет qualifying ACTIVE World actor в bounded sampling |
| C native physical travel/payment | NOT_PROVEN; PRIMITIVE_ONLY не проводился |
| D полезное arrival/continuation | NOT_PROVEN; background progress не подменяет native loop |
| E restart | PASS identity/goalId и clean sameDB/sameJAR boot/STOP; полный owned native STORE/полезное продолжение NOT_PROVEN |
| F World witness |396 read-only cohort rows с World=false; observer/known-list/client NOT_RUN |
| G safety/finite recovery | DB-free contracts PASS; частый idle revision update и native concurrency остаются OPEN |
| H safe delivery | Independent subset review0; fresh build/regression/scope/text guards PASS; publication receipt ниже |

## Проверки, evidence и ограничения

`ant -f <private>/readonly-build.xml -Dbuild=<private>/final-build compile-tests jar`: exit0,34s;2380 production/453 tests,5 прежних warnings. Archived config preserved; OwnerProfile38 PASS/READ_ONLY; Apply на source не выполнялся. Один final GameServer JAR; stock Ant также создаёт LoginServer/DatabaseInstaller outputs, в Git их нет.
Финальный `Regression111.ps1`:9/9 runners exit0 —111(4 groups),110 reserve/continuation,098,097,NormalGatekeeperPure,Travel091,NavigationCore51/51 seed11111101,BackgroundModel10/10 seed15001501. Это DB-free проверки, не native parity.
Первый RED — отсутствующий cause guard (API-contract, не behavioral concurrency proof). Отдельный behavioral RED `COLD_PROPOSAL_MASQUERADES_AS_ACCEPTED_TRAVEL` →GREEN после phase fix. Native races в тестах не воспроизводились.
Промежуточно: sandbox Ant verifier ACL refusal; owner compile выявил restricted identifier `yield` в тесте, исправлен до RED. Общий `ant verify`0: historical DB routes не открывались. Ant calls3 (2 early failures,1 final); standalone javac focused cycles; focused runner executions15. Дополнительные SQL/input probes не являются GameServer boot.
Private evidence: `%TEMP%/phantom-living-cycle-111/` — PLAN, baseline.tar,539 preimages, code/delivery manifests, Ant/RED/GREEN/regression logs, publication receipts. Native stage `private/` — owner/Geo/native pins, overrides, SQL plan/create, game1/2 logs/exit receipts,396 samples, postflight1/2 и final-sql logs; stock logger в staged `dist/game/log/`. Секретов/Geo/runtime artifacts в Git нет.
Initial READ_SET12; разрешённые additional System/PopulationStore/NormalGK/Background capture/Task102Lane. Bounded extensions: AutoPlay/HistoricalService для handoff; Decision/BackgroundDecision/Learning wrapper/delegate для review; build.xml; Task102Authority/DatabaseProbe и GameServer/Shutdown/LocalRuntimeShutdown/config keys для G0/STOP. Широких repository-wide searches0; exact-file symbol/range reads расширены по этим blocker-причинам. Предковых AGENTS выше root и module README.md не найдено; generic readme.txt не использовался вместо TASK.
Scope guard:2962 archive source/test/config/topology inputs, exact5 delivery paths,539 existing user-file SHA. WIP109 и прочие user files сохранены; старые reports/task packages не переписывались, PK/C2/social hunks не переносились. Основной mixed checkout не является проверенным source candidate.
Mojibake-маркеры в изменённых файлах проверены: PASS, полный27-marker set.
Escaped Cyrillic в изменённых файлах проверены: PASS, все6 regex, включая XML формы.
Два финальных byte-identical guard runs и scoped Git whitespace check фиксируются в private receipts.
Usage перед публикацией:451591 tokens/3047s; target400k превышен из-за owner/restart audit, исправления admission boundary и новой sealed native программы. Новых независимых кампаний, серий или дополнительной сборки после native нет.

## Публикация и STOP

Code commit `b91a42faafabd0d04f0b24d890510e25625cc730`; второй commit содержит только этот отчёт. Code publication: **PASS**, обычный non-force push в feature/phantom-world, live SHA совпал с code commit.
Git разрешён TASK111/root policy. Использованы `rev-parse --show-toplevel/HEAD/@{upstream}`, `symbolic-ref --short HEAD`, `status --short/--porcelain=v1 --untracked-files=all`, `ls-remote origin refs/heads/feature/phantom-world`, `archive --format=tar <baseline> AGENTS.md L2J_Mobius_CT_2.6_HighFive`, scoped `read-tree`, `hash-object -w --stdin --no-filters`, `update-index --add --cacheinfo`, `write-tree`, `commit-tree -p`, exact-path cached `diff --check/--name-only`, ordinary `push`, expected-old-value `update-ref` только feature/phantom-world и его tracking ref. Temporary index отделён от mixed checkout; существующие source bytes не заменяются.
Фактические final SHA/live verification/push result сохраняются в `publication.json` и финальном ответе; private index backup сохранён. master/experiment/другие worktrees не менялись; force/reset/stash/clean/rebase/merge не выполнялись.
**STOP.** FULL111 остаётся PARTIAL. Native actor admission, physical полезная поездка и bounded полезный replan требуют отдельного подтверждения; TASK111R/TEST111b/TASK112 автоматически не начинаются.
