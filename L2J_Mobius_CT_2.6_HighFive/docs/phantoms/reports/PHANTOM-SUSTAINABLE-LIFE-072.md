# PHANTOM-SUSTAINABLE-LIFE-072 — PARTIAL

Сквозной sustainable life gate **не закрыт**. Сохранённый Warrior20 действительно заработал EXP/SP/Adena, но SupplyJourney не принял экономику покупки D shots на выбранном расстоянии. Paid shot BUY, возврат с расходом оплаченных shots и paid-chain restart не доказаны. CP-покупка у Helvetia за обычную Adena подтверждена. Native PvP остановлен на preflight маршрута, до создания противника и нападения. Исторический `FULL071=PARTIAL` сохраняется.

Start: **2026-10-23 17:28:47 UTC**. Product freeze по authority: 21:28:47 UTC; hard stop: 22:28:47 UTC. Продуктовая работа фактически закончена в 18:46 UTC; финальная поставка и остановка выполняются досрочно. Итоговые время, HEAD и remote comparison — в финальном сообщении и private operational receipts.

## Native результат

Полная [матрица](PHANTOM-SUSTAINABLE-LIFE-072-data/ACCEPTANCE072.tsv), [исходные native строки с именами запусков](PHANTOM-SUSTAINABLE-LIFE-072-data/NATIVE072.tsv), [SHA исходных TSV](PHANTOM-SUSTAINABLE-LIFE-072-data/EVIDENCE_INDEX072.tsv), [preflight до создания персонажа](PHANTOM-SUSTAINABLE-LIFE-072-data/SCENE072.tsv).

| Gate | Итог | Фактическая граница |
|---|---|---|
| P0_SCENE | PARTIAL | Rewarded mobs и оба Geo-коридора найдены до birth; полезность закупки после короткого заработка не подтверждена |
| P0_EARNED | PARTIAL | 12 native kills, положительные EXP/SP/Adena; ни одного COMMITTED1463 BUY |
| P0_RESTART | PARTIAL | Точный актор сохранялся между JVM, но paid shot chain отсутствует |
| P1_CP_PAID | PASS | Native5591/5592 BUY у Helvetia, physical debit, durable receipt, use/reuse/Olympiad negatives |
| P1_PVP_SUPPLY | PARTIAL | Подходящая retreat-сцена не доказана в bounded search; физический PvP не начат |
| P1_PROACTIVE | PARTIAL | Focused policy/social contracts; настоящего SPOT warning→admission нет |
| P2_GEAR | PARTIAL | D129/352/2378 — объявленный fixture; earned paid gear acquisition отсутствует |
| AA_CP | OPTIONAL_FUTURE | Stock500 подтверждён; durable phantom multisell и оплаченная AA-покупка отсутствуют |
| CP_REPEAT20 | CONTRACT_PASS_NATIVE_NOT_RUN | 20 делегирований в одной fake-backend сессии; native20 uses не заявляются |
| X_EPIC_TIMER | SKIP_OPTIONAL_WITH_REASON | Приоритет hard gates и поставки; часы/события не менялись |
| SAFETY / DELIVERY | См. runtime evidence | Отдельные072 schemas/roots, committed-source final JAR, Main/restart/штатный STOP |
| OWNER_VISUAL | OWNER_VISUAL_PENDING | Клиент владельца не запускался |

### P0: точный сохранённый актор

`task072supply / Supply072`, actor **268480990**, profile **1**, Warrior20. Birth fixture: D129 Sword of Revolution, 352/2378 armor, EXP835862, SP0, Adena0, matching shots0, без surplus и buffs. Fixture equipment не считается earned gear.

До birth выбран spawned Tyra **30420**, list **3042000**, XYZ **(-61717,75401,-3352)** и Monster **20418**, level18, XYZ **(-63814,76746,-3256)**. Расстояние2493,12; Geo walk routes FOUND в обе стороны, 6/5 points. Native reward633EXP/28SP, native strict gap11. Base quote1463=14A, фактическая цена Tyra с50% tax — **21A**. Sword129 расходует **3 shots/charge**.

Связанные сохранённые эпизоды, без пересоздания актора:

1. `supply072-prepare-1792777789173.tsv`: 3 genuine kills20418, EXP835862→837761, SP0→84, Adena0→350; затем local700 target exhaustion. FAIL сохранён.
2. `supply072-continue-1792778264569.tsv`: четвёртый kill, EXP838394/SP112/A350; unsafe approach остановлен. FAIL сохранён.
3. `supply072-continue-1792778459868.tsv`: 8 kills суммарно, EXP840926/SP224/A893; SupplyJourney `supplies.stock.too_far_or_unavailable.income_farm`. Не выдано денег или shots.
4. `supply072-continue-1792780138102.tsv`: finite recovery до12 kills суммарно — 11×20418 и1×20034, EXP**843443**, SP**334**, Adena**1066**, shots**0**. Последняя позиция **(-64148,76943,-3264)**. Нативный бег включён обычным action lease; нет coordinate/resource writes. Child FAILED, root/deadlines сохранены.

Последний wallet1066 даёт available budget800 и38 D shots по21A. По неизменённой usefulPurchase policy примерно2880distance /159,5speed требуют19hit×3=57shots. Это вычисление из native snapshot и policy, **не оплаченная операция**. Нельзя назвать walking или source quote завершённой закупкой. Материалы1872/1867 заработаны native loot; conservative recipe/market reserve не обходился ради продажи. Продолжать бесконечный фарм, снижать reserve/полезность пути или выдавать ресурсы для PASS запрещено retry policy072; lane остановлена.

Минимальный незакрытый outcome: фактический matching1463 COMMITTED BUY из earned wallet через существующий SupplyJourney, native возврат, два rewarded kills с уменьшением оплаченного стека и продуктивный restart того же paid chain. Новая кампания и retained071 actor не используются.

### CP: оплаченная Adena-покупка

`task072cp / Supply072Cp`, actor **268480995**, profile **2**. Изначальный конечный fixture2000A/9Dshots/CP1/learned skills сохранён; после начала измерения ресурсы и координаты не исправлялись. Старые CP failures сохранены. Строка `cp_fixture` в старом runner ошибочно содержит литерал `task072cp2/Supply072Cp2`; фактические account/name проверены assertion на `task072cp/Supply072Cp`, actor/profile неизменны. Диагностический литерал исправлен в committed source; оригинальный TSV не переписан.

`supply072-cp-retained-1792780287015.tsv`: Helvetia **30081/object268469247**, list **3008100**, XYZ **(80518,147922,-3506)**; loaded Player **(80518,147986,-3504)**, dx0/dy64/dz2, **3D64,031 <250**, same instance. Walk surface стабильна. **LoS=false** честно записан: native `Npc.canInteract` и `L2jCommerceBackend.validateActor` не требуют LoS, их interaction guard сохранён.

- BUY5591×2: Adena2000→1560, items0→2, COMMITTED key `785949c8b6d2e098ebd5be8f1e222f1fafd8a0b39d20fd21cf7616731a54bb01`.
- BUY5592×1: Adena1560→1010, items0→1, COMMITTED key `c73538f406e21359da289b980321990da70fa9262047dc2f6ac5b83b410e9213`.
- Native5591 use: stack2→1, CP1→51, observed reuse500ms; immediate reuse оставил stack1. Оба5591/5592 в Olympiad rejected без debit.
- Wrong object, wrong NPC, insufficient budget — три native quote negatives без physical effects. Материализация завершилась `STOPPED`.

Это **NATIVE_PAID из объявленных fixture funds**, не earned economy. Покупки выполнены на committed JAR `DBDEF62224099713AEFE72EFA7532A905FADB291BA02FB784C33093DD070CD91`/source4be2596; последующие финальные изменения не меняют native BUY/CP handler. Финальный PvP preflight на итоговом JAR загрузил того же актора с paid5592 receipt без новой выдачи.

### PvP, AA и повторное CP

До opponent creation ищется обычная dry/non-peace/non-NoPvP сцена с Geo-путём к существующему safe anchor и обратно. Финальный `supply072-pvp-1792781295714.tsv`:192 bounded samples, 3 legal field candidates; два `NO_ROUTE_WITHIN_BUDGET`, один `SEARCH_LIMIT`. Эти статусы **не доказывают непроходимость мира**. Противник, нападение, skill/shots exhaustion, retreat/resupply не создавались и не заявляются PASS. PathFinding budgets не увеличены, отдельного AI/pathfinder нет.

Выявлены и исправлены две небольшие причины в прежних owners/data: retreat безусловно предпочитал RESPawn ближайшему CITY_CENTER; authored Giran city center Z-3350 имел tolerance0 при настоящем GeoHeight-3464. Теперь сравниваются два уже найденных nearest anchors, а только Giran center имеет tolerance128; разрешение реальной поверхности выполняет существующий walkSurface. Native preflight после исправления пола дошёл до route проверки, но route proof не получил. Это исправление не выдаётся за native retreat PASS.

Stock `data/multisell/500.xml`: **5591=240 AA5575**, **5592=600 AA5575**. Владельцы —22 IDs жрецов Dawn/Dusk, включая31078–31091/31168–31169/31692–31695/31997–31998; **Pushkin30300 отсутствует**, Mammon не подставлялся. Dawn/Dusk HTML зависит от cabal/периода/победителя; stock bypass идёт через `MultisellData.separateAndSend`, prepared native exchange проверяет list/object/NPC/instance/3D, inventory capacity/weight и реальные ingredient counts. Штатные item5591/5592 используют2166 level1/2,50/200CP и500ms reuse.

`PhantomCommerceCatalogLoader` читает MultisellOffer, но `PhantomCommerceReceipt.OperationKind` и `L2jCommerceBackend.quote` не имеют MULTISELL/ingredient conservation/reconciliation. Durable AA supply поэтому требует отдельной операции в прежнем commerce owner и её replay tests; просто вызвать exchange и назвать его receipt нельзя. AA/Seal Stones не выдавались, текущие measured accounts не имеют AA; Seven Signs/календарь/Маммон/Pushkin не менялись. По уточнению владельца это **не blocker072**, самостоятельный необязательный будущий пункт.

CombatService каждый pulse ниже30%CP выбирает ready stock и вызывает native handler; per-fight cap1–2 отсутствует. Новый focused `pvp-cp.03` проверяет20 делегирований с шагом500ms и fake stock30→10 в одной сессии. Это **contract evidence**, не native бой с20банками. Native текущий paid stock мал; пополнение для такого доказательства не выполнялось. Обычный SupplyJourney CP target увеличен10→**25**, low-water3 и реальные budget/risk/reserve guards сохранены. AA-путь этим не появился.

### Systemic farm selection

Read-only audit охватил `PhantomVisibleAutoPlay.selectableTarget`, exact npcId permit branch, `farm.background`, historical suitability/replan, acquisition source search и VisibleFarmTravel. Existing `PhantomHistoricalBackgroundPlanner.LEVEL_RADIUS=2` уже допускает мобов выше на1–2; общего запрета `playerLevel >= mobLevel` нет. У ordinary engagement отсутствовал native reward admission. Добавлен `rewardingFarmTarget`, использующий настоящие `Npc.getExpReward/getSpReward` и strict configured gap. Pinned npcId branch также проверяет reward. Defensive targets/viable и pending-kill semantics сохранены; mobs+1/+2 явно покрыты focused contract.

Existing scheduler проецирует live level и перепроверяет suitability; при recovery вызывается `replanVisibleLocal`. Его replacement ограничен локальным районом примерно2км, **дальнего topology fallback в этой ветке нет**. Это точный оставшийся игровой gap для смены локации при исчерпании rewardable целей; новый planner в072 не строился. `SEARCH_LIMIT` в VisibleFarmTravel остаётся transient `ROUTE_BUDGET_EXCEEDED`, а не доказательством плохой геометрии. Радиус5000 используется только short native fixture; нового глобального5000-ограничения нет. HP/MP/shot viability и экономические budgets не удалялись; общее оптимальное сравнение всех доходов/рисков мира не заявляется.

### Gear и доставка

Item129 — D, minimum expertise level персонажа20, reference price**1 074 000A**, native shot charge3; настоящий source, например buylist3008400. Earned wallet1066 не покрывает покупку. Надетый fixture нельзя продавать/покупать обратно ради earned acquisition. `P2_GEAR=PARTIAL_GRADE_DEBT`.

Final source **5b61a8a541a1319a9c20075c0904929ee890d8de**; [полный committed build receipt](PHANTOM-SUSTAINABLE-LIFE-072-data/SOURCE_BUILD_INFO072.json). Game JAR **011D5891A52AE5AA351824463608844FD4EC0C5E1C0818DD999DD8CD99681D1B**; Login JAR **E5B71CE8ABFE69969EF80C036D1A4A8F015A55E25AF12201B40E3219F84E29FD**. JDK25.0.4.101, Ant1.10.17, `jar compile-tests` exit0/41s. Всего было3 whole builds — две предварительные и одна итоговая после причинных изменений; опубликован один final artifact set. Неизменившийся продукт ради документов не пересобирался.

Focused итогового JAR: **81/81** по9 нужным suites; runtime ownership guard PASS/DB_WRITES0/PROCESS_ACTIONS0. Отдельный retreat regression RED9/10 → GREEN10/10; [focused summary](PHANTOM-SUSTAINABLE-LIFE-072-data/FOCUSED072.txt). Cleanup учитывает неидемпотентный navigation.finishStop; materialization обязана вернуть STOPPED, ошибки owners не скрывают cleanup остальных. Два ограниченных read-only source reviews не нашли оставшихся Important; native hard gaps остаются видимы.

Own candidate **D:\PhantomWorld\Sustainable-Life-072-candidate** и TEST **.phantom-local/task072/runtime-test**, каждый203 физических Geo regions с SHA-проверкой. Только schemas `l2jmobiush5_phantom_test_072` / `l2jmobiush5_phantom_release_072`, economy071 migration неизменна, authority.task_id72. Используется один собственный installationUUID задачи072, привязанный к двум разным schema/root/runtimeId; hexid созданы отдельно для TEST/RELEASE. Credentials/hexid/DB backups не публикуются. Источник Geo067 только читался.

Первые assembly attempts остановились до создания root: Windows PowerShell5 и PowerShell7 по-разному сортировали `auction.sql/auction_bid.sql`. Archive/source bytes совпали; теперь tree hash использует ordinal-сортировку и отвергает reparse до спуска. PS5/7 дали один hash; повторная assembly нового source прошла. Guard не обходился, старые receipts не переписывались.

Штатный operator: `START.cmd`, `STATUS.cmd`, `STOP.cmd` внутри candidate, либо его `tools/Runtime072.ps1 -Action Start|Status|Stop -RuntimeRoot <exact-root>`. Bindings проверяют whole JAR/tool/source/Geo SHA, schema/UUID/ports/process incarnation. Итоговые [Main/restart/STOP receipts](PHANTOM-SUSTAINABLE-LIFE-072-data/RUNTIME072.md) содержат actual durations и DB/JVM/listeners0. Это server evidence; visual gate остаётся OWNER_VISUAL_PENDING. Public ZIP пропущен: настроенный candidate уже существует, credentials и1.2GB не загружаются в Git.

### Scope, проверки и Git

Начальный local/remote feature HEAD точно **031ed5a3b73a44ba31d71d041a897a4f9247f16e**. Root/module AGENTS, workflow/release/status и весь072 package прочитаны; module README отсутствует. Переиспользованы071 environment/native/ownership аналоги. Bounded exception более10files ограничена owner families из IMPLEMENTATION072 и новым report-data; точный [allowlist](PHANTOM-SUSTAINABLE-LIFE-072-data/SOURCE_PATHS072.txt). Защищённый untracked068V не staged; TASK071 actor/DB/root и старые releases/client не изменялись. Protected hash receipts — в safety evidence.

- mojibake-маркеры в изменённых файлах проверены;
- escaped Cyrillic в изменённых файлах проверены.

Git использовался по прямой authority задачи для HEAD/scope/committed archive/normal commit/push. Команды: `git symbolic-ref --short HEAD`, `git rev-parse HEAD`, `git status --porcelain`/`--short`, `git ls-remote origin refs/heads/feature/phantom-world`, `git diff --check -- <exact allowlist paths>`, `git diff --stat <baseline> HEAD -- <exact allowlist paths>`, `git archive --format=zip --output=<own072.zip> <exact source SHA> -- <explicit module subtrees/test paths>`, `git add -- <exact own paths>`, `git commit -m <message>`, `git push origin HEAD:refs/heads/feature/phantom-world`. No add., reset/clean/stash/rebase/force, no master/default mutation, no worktrees. Финальный нормальный docs commit и проверенный remote HEAD сообщены в финале.

Automatic approval review отклонил прежнее перечисление private071. Доступ не обходился: Prepare072 использовал разрешённый repo Database.ini, не печатая credentials. Этот отказ не блокировал own072; сохранность071 не объявляется доказанной чтением его закрытых данных.
