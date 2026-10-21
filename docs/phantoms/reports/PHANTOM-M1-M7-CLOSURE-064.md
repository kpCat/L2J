# TASK064 — единая интеграция М1–М7

**Итог — PARTIAL; проверенный серверный срез, М1–М7 полностью не закрыты.** Начало2026-10-20 18:08:55 UTC, предел2026-10-21 06:08:55 UTC; последние90 минут оставлены для финализации, она начата раньше. Сжатие контекста срок не меняло. После нескольких независимых native сценариев и финального cumulative продолжение незакрытых вертикалей требует новых owner contracts либо пока недоказанных маршрутов; рискованные обходы не применены.

## Источники, изоляция и сборка

Baseline `fe2a24d389ed4af622cb14bc15a615b4b69e28f1`, ветка `feature/phantom-world`. Пакет фактически расположен в корневом `docs/phantoms/tasks/PHANTOM-M1-M7-CLOSURE-064`: все восемь документов прочитаны, семь исходных SHA совпали. Изменяемая ACCEPTANCE064 исключена из исходного manifest по контракту пакета.

Установленные Java25.0.4.1, Ant1.10.17, MariaDB11.4.13/127.0.0.1:3308 переиспользованы без обновления или перезапуска общего сервиса. Writes только в две новые схемы `l2jmobiush5_phantom_test_064`, `l2jmobiush5_phantom_release_064a`:124 таблицы/121 stock SQL, агрегат `2CD503128136E3E854F9EFF24236DD9F286CA58D9E950ACB9B2842E78B910E28`. Старые схемы не использовались как writable.

Старый checkout служил только источником чтения ресурсов; код и Semantic Pack — из нынешней ветки. Все203 GeoData-региона061 проверены и физически скопированы в две собственные среды. Из68 старых semantic XML65 отличаются только EOL; три варианта сохранены приватно, текущий pack ими не заменён. Защищённые057–061, клиент и чужие worktrees не запускались и не изменялись.

TEST root — `L2J_Mobius_CT_2.6_HighFive/.phantom-local/task064/runtime`, порты2244/7944/9244. Поставка — `D:/PhantomWorld/World-Integrated-064-candidate`,2144/7844/9144. Population12, ACTIVE6, cap16, FRESH age0, synthetic OFF; calendar/locality/admission guards сохранены. Ant/native/ordinary выполнялись последовательно.

Чистая committed source21:

| Поле | Значение |
|---|---|
| Code commit | `9e3d3c5998649b600d8724450ebee474530af656` |
| GameServer SHA256 | `CE6E7A3E0024B7C97CD273AF1A2E5146C0728B68980C4FC0A13ED0F48E982FA9` |
| LoginServer SHA256 | `E96A0C63B4A8FAE28325A8587ADF1A59150B7C6F1BEA192616985B1F66098FDF` |
| Source archive SHA256 | `2B69A4585BEAD2BE054E677F687D1A95967D50AD83CD80E5CBB5FC68049EA71E` |
| Test tree SHA256 | `A9658546FA280D3F7C1B6B567FF4A4742E72181482BF9C9B39E94E97E5B7DF89` |
| Build UTC | `2026-10-21T02:57:39.7416484Z` |

BUILD_INFO064 связывает archive/JAR/полный test tree. Exact root/schema/owner/ports/JDBC/JAR и wrong-root/schema/owner/tampered-JAR/test-tree/missing-receipt negatives проверены до native effects. Исторические native эпизоды имеют собственные EPISODE064/source/JAR/receipt; они не выдаются за повтор на source21.

## Итоговая матрица

| Milestone | Статус | Доказательство и граница |
|---|---|---|
| М1 | PARTIAL | Ordinary earned farm/commerce/save и sameDB сохранность; отдельный source20 continuation1/1. Полное G4 не доказано; G6 crash/Attach вне разрешённого scope. |
| М2 | SERVER_VERIFIED | Ограниченный native Party/consent/follow/assist/heal/buff/recharge/res/leave и HOLD/RESUME сохранён. |
| М3 | SERVER_VERIFIED | Directed RU commands, authority/social contexts и изоляция собственных TEST Player сохранены. |
| М4 | SERVER_VERIFIED | Поддержанный конфликт: native VICTORY→cooldown→regroup→следующая деятельность. Универсальные PvP-контексты не заявляются. |
| М5 | PARTIAL | Реальные роли/подготовка/SELF defense и гибель начальной волны minions; stock25199 victory/reward/regroup не достигнуты. |
| М6 | PARTIAL | Полный stock Kamaloka57 и дополняющие Rift lifecycle/death/logout/exit контроли. Автономный Rift/full clear и полная stock Pailaka battle не доказаны. |
| М7 | PARTIAL | Профессии/Noble, три earned-learning archetype, исчерпывающие class/learn/Trainer contracts. Законный NPC subclass и полное native покрытие топологий не закрыты. |

Versioned подробности — в `docs/phantoms/tasks/PHANTOM-M1-M7-CLOSURE-064/ACCEPTANCE064.tsv`. **OWNER_CLIENT_QA_DEFERRED** указан отдельно и не закрывает технические FAIL.

## М1: обычная жизнь и continuation

Ordinary03: actor268484890/profile5 самостоятельно купил SS1835×5 за40 аден у merchant30004, вернулся к фарму: World EXP385→420/SP22→24. STOP сохранил420/24/A19/SS1, четыре купленных SS израсходованы. Ordinary04/source12: новый native BUY1 за8, A19→11/SS1→2; возврат завершился travel_budget/LOCAL_FARM_UNAVAILABLE, нового EXP/SP нет. SameDB05 сохранил level3/420/24/A11/SS2 и прежний commerce receipt без повторной покупки.

Финальные ordinary06/07 на source21 прошли root START/STATUS/stock STOP и sameDB START из `C:/Windows`, с новыми PID/incarnation. В07 Game43884/startTicks639281501512195192 и Login51756/startTicks639281501489056740. Свежий observer02: populationAge20624ms/liveAge826ms, SHA256 `6CDF273B94184EC0535DC5DC123AFD0CA39F940E3F52B09F652F96053E8388C8`. После STOP07 SQL03:37:27UTC сохранил level3/EXP420/SP24/A11/SS2, XYZ−78552,249656,−3568 и прежнюю COMMITTED commerce receipt `680aac2c31a3ec994f0d9ac8fe42cdf7eeaec08902c61f112cea38a88c7eb1e2`. Нового расхода/покупки нет.

Полезное продолжение на текущем ordinary JAR не доказано: World0, profile5 SLEEPING/schedule_not_active до09:40UTC, presence.no_current_local_demand/native_context.required:coalesced. Другие calendar-online профили могли быть BACKGROUND. Это не объявлено общей неисправностью admission; календарь, demand, ресурсы и XYZ для PASS не менялись. Приватный capture helper сначала ошибочно считал population старше10s просроченным; raw rejection сохранён, guard приведён к фактическим30s population/1s live cadence (35s/5s bounds), PID/incarnation остаётся exact. Отчёт не выдаёт это за runtime fix.

Background02=5/5, recovery01=8/8, earned timers02=5/5, native phase02=1/1. Source13 producer03=2/3: A09 реальные damage/kill/reward/EXP70/SP2 и Q06 TimerHolder drain прошли; T continuation revision0 — неподходящая TEST85 у Gremlin1 без локальной альтернативы2km. TEST bindVisibleRecovery исправлен; невозможность этой fixture не объявлена production defect.

Новый `m1-local-continuation03` на committed source20=1/1. PREPARED ElvenFighter12 физически дошёл до старого Gremlin anchor; history вне decision callback опубликовала goal15/revision1 и matching catchup COMPLETE/1. Тот же Player/epoch/level12 нанёс exact damage4 существующему stock Red Keltir: HP75→71. Это continuation/native действие, не заработанный level12 или новый kill/EXP. Owned Store/drain/World/identity cleanup прошёл.

Raw INVALID_INPUT сохранены: конструктор Monster не гарантировал full HP; center-preflight ошибочно требовал стабильности anchor. Actual arrival46195,41458,−3504 был смещён на64 от центра и законно выбрал другой anchor того же20534. Контроль наблюдает максимум16 существующих full-HP NPC в фактической области плана. После boundary нет spawn/refill/XYZ/level writes; cleanup снимает только listeners.

## М2–М4

Source08 Commands+Authority1/1: delivered RU speech→native Party→WALK/follow/assist/chat; social contexts1/1. Source10 BusyHold3/3: stationary Cardinal97 heal1217:33/recharge/resurrection и HOLD/RESUME. Это серверные TEST Player без поддельного сетевого клиента.

Source12 TownOutcome08=1/1: реальные damage/defense/HELP/VICTORY/COOLDOWN, защищённый native FOLLOW1800units, затем полезный FOLLOW554units, та же Party, durable cooldown reload/save, без rescue. Eligibility/peace/party/instance/stale-owner negatives сохранены.

Giran SEARCH_LIMIT и Town07 retreat timeout сохранены. В последнем после реального retreat1950units rawZ−3000 расходился с native floor−3120. Общий floor fix: pure PvP7/7/navigation51/51 и native floor/corridor preflight. Успешный TownOutcome08 завершился VICTORY; actual retreat после fix им не проверен.

## М5: полный рейд

Stock25199/HP912634+четыре полных minion25200/25201, native Party9 legal79, обычная A/S экипировка, конечные SS/BSS/Spirit Ore. Восемь Phantom и один headless TEST Player; это не клиентская human QA. Level/gear/MP — PREPARED_INPUT до enrollment. Во время измерения нет прямых TEST/refill/stat/rates/reward/roster writes; native damage/heal/MP-cost происходят штатно. Исходные120s подготовки/90s feasibility/45min episode deadline сохранены.

Native SELF-guard source14=1/1: CAST110 и exact cancellation, wrong-skill/wrong-target не снимают чужой CAST. Raid12/source19 подтвердил110: PDef1733→5333/MDef1247→4487. Поздний MISSING объясняется native30s/reuse15min; CAST→ATTACK как причина ABORT не подтверждена, speculative fix не внедрён.

Общие исправления: learned Hierophant Focus1077/DeathWhisper1242/Acumen1085 и idle SELF tie-break после priority/actor/kind в существующем PartyTactics. Emergency/combat порядок и budgets сохранены. Runtime RED14/15→GREEN15/15; source19 подготовка уложилась в120s, все девять получили native эффекты, caster MAtkSpd599. Raid12 далее ABORTED: три minion убиты, последняя25200 имела55850HP.

Source21 устранил конкретный metadata gap: EE105 уже знает1401:11 на74, но owner видел только1217. Одна rule1401:1/rank1000 по Cardinal pattern: RED38/39→GREEN39/39. Native1401:11 — base80MP/power946/один Spirit Ore;1217:33 — base120MP/power472+HoT. Native costs не изменены.

Raid13/source21=0/1 ABORTED/raid.retreat.complete. Оба лечащих действительно CAST1401:11; heal/recharge/buff/dance/aggro TRUE, held346478ms. Начальная волна всех четырёх minions была убита; на214–334s liveMinions=[]; в последней выборке334s bossHP755929/912634, healerMP36/recharger79. Контрольный участник уже мёртв, позднее tank и DD тоже погибли. К моменту terminal снова наблюдались live25200; штатный respawn не отключался. Boss не умер, nativeLootComplete=false; victory/reward/regroup отсутствуют. DeathCause хранит первый общий death witness, поэтому её повтор под actor-specific ключами не считается точной причиной каждой поздней смерти.

Следующая архитектурная граница — sustain и покрытие уникальных buff variants: native Clarity1397 EE законна, но одно XML-правило не сработает в рейде при существующем single-broadest buff provider PP98/assignedSupport. Это не доказанная достаточная причина поражения и не готовый fix без решения coverage и его contracts. Stock Mana Elixir8633/skill2288:6 восстанавливает420MP, но existing auto-use выбирает continuous items, не имеет MP threshold, а его owned TickLease привязан к farm goal/revision/epoch. Готового owned raid admission/cancel/drain пути нет; глобальный MP writer/новый timer не добавлены. HP thresholds/forced recharge не менялись. Доказанного независимого blocker именно25199 для замены другим RaidBoss не установлено.

## М6

Kamaloka02/source09=1/1: template ID57, stock NPC18554 Tomlan Kamos level23/HP163178 убит за≈424s, все шесть живы/EXP/расход SS, native reuse, естественное5min closing, save/cleanup/cooldown, без второго reward.

Rift — два дополняющих контроля. Lifecycle06/source09=1/1: consent/READY/once fee/Party/instance/combat EXP, реальный timer507s room3→6, все четыре живы, canonical disband/return/claim release/save. NativePull07/source15=1/1: managed logout освободил World/identity/Party3; ordinary Player attacks создали native hate, victim HP1542→0/dead=true/native dead-list=true; canonical leave вернул четверых в waiting, room и четыре timer owner освобождены, native Store/SQL/rematerialization identity проверены.

При initial boss room9 один stock Guardian31873 дал законную альтернативу: native WALK/canInteract/handler changeriftroom, room9→6, once fee и timer ownership сохранены. RND/room/XYZ/timer поля не переписывались. Room6 содержит12 stock NPC, но фактическое incoming/hate доказано только для некоторых. Старые departure04/pressure05 FAIL сохранены. Это bounded SERVER_NATIVE control; сетевой logout, автономный entry/full multiroom clear не заявляются.

Pailaka green04/source09=2/2: Q128 callbacks/reward/idempotency, ambient/earned controls и clean owned drain. Kill fixtures заранее HP1; full stock battle не доказана. Старый earned-timer FAIL сохранён, Q401 не возвращён обязательным career gate.

## М7 и навигация

Professions03/source09=6/6:20/40/76/save/resume/Noble80, Dwarf/Female Kamael, native race/sex/tree/index guards. PREPARED near-level, не заработанный1→85. Source08 Fighter/Dwarf и source09 HumanMage — каждый1/1 actual farm→earned SP→paid learning→native return→новый kill/EXP/SP/saveLoad. SP49/309/239 подготовлены на один ниже стоимости50/310/240. Loadout13/13, finite low-funds1/1.

Source21 progression-parity39/39 отдельно от маршрутов:43,749 learn records(level/SP/NPC/FS/items/prerequisites),21,218 career×sex combinations,103 NPC-learnable классов с совместимыми Trainer templates. Три archetype не объявлены универсальностью. Production empty-first/second Trainer approach early-exit исправлен в существующем owner; max3 trainers×17 approaches/deadline сохранены. Focused RED37/38→GREEN38/38,49 availability/start combinations/exact NPC/index/point/intent/reset.

| Native топология | Покрытие |
|---|---|
| Местный Trainer | Fighter/Dwarf/HumanMage paid-learning/return прошли; HumanMage Trainer30032/outbound+inbound FOUND. |
| Соседний город | Независимый завершённый intercity native маршрут не доказан. |
| GK | LivingGK01=0/1: за8min нет двух transfers, route_budget_exceeded. |
| Удалённый регион | Весь supplier itinerary не подтверждён; preflight farm↔GK недостаточен. |
| Обход препятствия | Async path/arrival есть, но прямой corridor не объявлен obstacle-detour PASS. |
| Недоступная цель | Bounded contracts сохранены; geometry01=0/2 не достиг intended GK/water boundary. |

ElvenMage25:32 compatible Trainer spawns/72 eligible nodes/32 farm anchors;47 реально запрошенных out/in pairs только nearest30157/30158 и один30112 approach,43 primary buffer hints>500. Результаты1 FOUND/SEARCH_LIMIT,8 SEARCH_LIMIT/FOUND,15 SEARCH_LIMIT/NO_ROUTE_WITHIN_BUDGET,23 SEARCH_LIMIT/SEARCH_LIMIT;0 timeout/0 both FOUND,118 approach points too_far. Это narrow TEST sample/local buffer+budget; global unreachable/blocked Geo и связь с production early-exit не доказаны. GK/global alternative здесь не выполнялись.

LivingGK01 suppliers —31952 Pele/Schuttgart и30254 Harmony/Dion. Level4 main GK бесплатен; недостаток аден не объявлен причиной. Preflight только farm↔GK30540 не доказывал весь маршрут. Точная причина выбора/срыва остаётся открытой, неизменённый эпизод не повторялся.

SEARCH_LIMIT — bounded buffer/query/edge/label limit; NO_ROUTE_WITHIN_BUDGET — неизвестен подтверждённый маршрут в данном поиске. Они не доказывают заблокированную геометрию. Timeout(DEADLINE_EXPIRED), Geo/water/height отказ и GK/fee/cooldown требуют отдельных фактов. Существующие global topology/GK/anchor owners и короткие native Geo/PathFinding сохранены; второго pathfinder/слепой teleToLocation после NO_PATH нет.

Prepared058 subclass switch/save/rematerialize сохранён. Законное NPC enrollment не закрыто: VillageMaster требует network GameClient, class-switch Store — корректный checkpoint/writer bridge. Fake client/обходов нет; Noble не засчитан как subclass.

## Проверки и поставка

Исправления используют текущие High Five owners: support lease/deadline/coverage, owned attack→aggro, minion focus, readiness SHA256, strict learned SELF defense, idle support order, native metadata, Trainer alternatives, PvP floor, ambient NPC-only producers и opt-in graceful shutdown через existing shared ThreadPool. Второй scheduler/store/navigation engine, LLM или зависимость сервера от Studio не добавлены.

Frozen111/23 modes: `test/resources/phantoms/task064/baseline111.tsv`, SHA `E0EA9F3EEF0B1E5652F981EBAB3D2292552A63FA6935809939B8630B552CE691`. Финальный source21 cumulative:23 modes,120/120 PASS, все111 исходных case ID сохранены; progression-final21=39/39. Source21 Ant unit66/66/content3/3 exit0; expected failure-control harness self-test не является gameplay PASS. Native FAIL сохраняются отдельно.

`D:/PhantomWorld/World-Integrated-064-candidate` передан физически STOPPED: оба JVM штатно завершились, все шесть TASK064 ports свободны; STATUS хранит STALE_RECORD с pid0, это не запущенный процесс. Корневые START/STATUS/STOP работают из другой рабочей папки. Свежие `START_HERE_RU.md`/`START_HERE_064_RU.md`, `TASK064_REPORT.md`/`ACCEPTANCE064.tsv`, BUILD_INFO и SHA-selected evidence расположены рядом. Приватный `task064-evidence-final01` содержит final cumulative, frozen111, native EPISODE/source/JAR/receipt mapping, PASS и FAIL, ordinary SQL/observer/status и preservation checks. Исторические059–061 руководства обозначены как исторические. Код поставки —9e3d3c5998649b600d8724450ebee474530af656; финальный doc-only commit сохраняет тот же HighFive tree.

Финальные проверки: исходный PACKAGE_SHA2567/7; Game/Login/receipt и четыре pinned data-файла в обеих средах12/12; protected inputs45/45, source Geo203/203, собственные физические копии406/406. Проверки root/schema/owner/JDBC/ports/JAR выполнены существующим Runtime064 guard. Старые protected файлы и manifest не переписаны, общая MariaDB не перезапускалась. Exact allowlist67 файлов проверяется как по committed baseline→HEAD inventory, так и по текущему dirty scope; вне allowlist0. Итоговые SHA/evidence сверены после доставки.

- Mojibake-маркеры в изменённых файлах проверены:67 разрешённых файлов и четыре доставленных руководства/результата, совпадений0.
- Escaped Cyrillic в изменённых файлах проверены отдельно:те же71 файл, совпадений0.

Git использовался по прямому разрешению TASK064 и владельца: `git symbolic-ref --short HEAD`, `git rev-parse HEAD`, `git rev-parse <commit>:L2J_Mobius_CT_2.6_HighFive`, `git status --short`/`--porcelain --untracked-files=all`, `git worktree list`, `git for-each-ref`, `git merge-base`, `git diff -- <exact paths>`, `git -c core.whitespace=cr-at-eol diff --check`, `git -c core.quotePath=false ls-tree -r <baseline-or-HEAD>`, committed `git archive --format=zip --output=<own-source.zip> <commit> -- <HighFive build/java/test/dist/tools>`, `git add -- <exact paths>`, содержательные `git commit -m <message>`, только normal `git push origin feature/phantom-world`, `git ls-remote origin refs/heads/feature/phantom-world`. Эти команды проверяют baseline/scope, создают pinned сборку и публикуют разрешённую поставку. Force/reset/clean/rebase/stash/history rewrite не использовались.

Приватные configs/credentials/GeoData/JAR/SQL не публикуются в Git. Raw FAIL/logs/EPISODE064/build/SQL/commerce receipts сохранены в `.phantom-local/task064`; выбранные доказательства с SHA доставляются рядом с candidate. OWNER_CLIENT_QA_DEFERRED отделён от технических границ G4/G6/raid/Rift/subclass/native topology.
