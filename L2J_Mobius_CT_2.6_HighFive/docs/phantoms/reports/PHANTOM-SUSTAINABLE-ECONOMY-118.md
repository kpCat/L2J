# TASK118 — устойчивая экономика и оплачиваемый запас

Результат: **CONFIG_PERSISTED / POLICY_VERIFIED / DECISION_VERIFIED / PARTIAL_ECONOMY / WEALTH_NOT_PROVEN**.
Native оплаты, earned-loot NPC sales и sameDB/sameJAR restart подтверждены; полная устойчивая экономика не объявляется SUCCESS.
Финальное указание владельца: **Adena amount ×5, chance ×1**. Оно заменяет ×10 из исходного пакета.

## База и сохранность

Live GitHub `kpCat/L2J`, `feature/phantom-world` перед изменениями:
`a2e12e5bfb12e8d7ff01e855d9048cf3805c7f8d`, parent `33251ecb6c39c5a3f297dbe1fb98a28303f7e7db`.
Основной checkout остаётся на `236b965a116bbc79cccbef78b04c628225a629cb` с уникальным WIP109.
Одна отдельная физическая копия создана через `clone --no-hardlinks --single-branch` из проверенной117 source-копии.
Никаких update/stage/stash/restore/reset/clean/branch/rebase/force действий в основном checkout.
1222 прежних protected preimages совпали по SHA-256/размеру; G0 дополнительно защищает шесть файлов TASK118, всего1228.
MAIN/PLAY, TEST109–117, releases057–061, клиент, исходная Geo и сервис MariaDB не изменялись.

Прочитаны все шесть файлов пакета118, root/module AGENTS, workflow/efficiency/status и pinned report117.
Дополнительно — README/readme.txt, TASK_PACKAGE_STANDARD, bounded master plan, build/OwnerConfig contracts и exact production symbols.
Вышестоящие AGENTS отсутствуют; report117 отсутствует в старом main и прочитан из pinned GitHub.
Расширение узкого READ_SET обосновано владельцем: OwnerConfig/batch, три supply owners, existing quote/loot/retention methods,
focused suites и существующий private G0/native117 protocol; другие хроники не исследовались.

## Отдельная поставка конфигурации

Опубликован config-only commit [`e04acc6804e011ff43fe0ea5122e4cf99111cad2`](https://github.com/kpCat/L2J/commit/e04acc6804e011ff43fe0ea5122e4cf99111cad2).
Обычный non-force push и live HEAD подтверждены до изменения gameplay. ×10 никогда не коммитился и не публиковался.
Изменены только `dist/game/config/Rates.ini` и `OwnerConfig.json`.
Rates побайтово равен исходному, кроме `DropAmountMultiplierByItemId` ID57 `1→5`; chance ID57 остаётся1.
287 прежних override records сохранены по всем пяти полям/типам; добавлен ровно один string override с baseValue1/value5 полного списка.
Штатный Save также классифицировал два уже существующих `# OwnerConfig: public` crafting INI; новых overrides для них нет.
OwnerProfile38.tsv/OwnerProfilePreserved.properties и остальные88 конфигурационных файлов побайтово сохранены.
Save→Apply→Verify, идемпотентные Apply/Verify, повторная compile-tests и post-build Verify прошли; финальная jar-сборка также применяет профиль.
Config-only target1→5 восстановлен штатным Apply из Ant: changed1, затем повторные Apply/Verify changed0; post-build source Verify PASS.
Ручной откат SOURCE к1 после commit HEAD5 корректно отвергнут unsaved SOURCE guard; восстановлена точная собственная preimage5.
Эта защита от потери несохранённых изменений сохранена; такой SOURCE challenge не объявляется успешным автоматическим восстановлением.

Существующий `SaveOwnerConfig.bat` не менялся, дубликат не создавался.
Реальный batch из другого cwd дал Save/Verify `changed=0`, exit0; existing `OwnerConfigChecks.ps1` прошёл6 scenarios,
включая copied real wrapper в пути с кириллицей/пробелами, success/savefail/verifyfail, rollback и privacy.
При обоих отказах wrapper возвращает nonzero и не печатает сообщение об успехе. Настоящий GUI double-click отдельно не наблюдался.
Обнаружен реальный Ant/Windows PowerShell дефект: inherited PSModulePath мешал загрузке Security/Set-Acl при фактической записи.
В существующем OwnerConfig импорт привязан к `$PSHOME/Modules` с обязательным восстановлением environment в finally; ACL не ослаблен.
Новый child regression с затеняющим foreign Security module дал RED, затем GREEN; реальный Ant target restore также GREEN.
Это bounded exception ещё на2 существующих файла tools/core+checks, всего11 public paths; дубликата батника или config framework нет.

## Планирование и владельцы операций

`PhantomAutonomousSupplyPolicy` рассчитывает запас из native charges/action, native attack/cast interval и конечного существующего journey window.
Сохраняются minimum targetHits и верхняя граница существующего child:4×600, без нового timer/config/schema.
Quoted price, actual wallet после return fees/earmarks/reserve и полный вес до60% ограничивают план; меньшая полезная партия не отбрасывается целиком.
Journey использует штатный `inventory.maintenance`: до четырёх BUY по≤600 у выбранного NPC, каждый с отдельной revision/Commerce receipt.
Перед новым BUY повторно проверяются compatibility, mobility/capacity/weight, wallet/reserve и возврат; PREPARED/COMMITTING остаётся в прежнем reconcile path.
Весь эффект выполняет прежний Commerce owner через quiescence/CAS/receipt. Нового AI, кошелька, ledger, worker, global World scan или бесплатных ресурсов нет.
Оригинальные field/binding/deadline и restart counter-rebind fences сохранены; single-operation/future-equipment/potion paths используют прежний cap600.
Starter/need/learned-recipe/BOM/crystal/rare/equipped/enchant protections существующего LootDisposition не ослаблялись ради продажи.
Поездка в Гиран и продажа ценных материалов не навязываются.

## DB-free экономика ×1→×5

Проверены настоящие `PhantomRateAwareLootFairValue`, `PhantomPrivateMarketPricingAuthority`, `PhantomOfflineMerchantService.allocateBuyLines`.
Источник — native XML/drop copier и enabled-spawn membership; quotation geometry fixture-only, World/NPC supplier/paid trade не создавались.
Для четырёх paired sources raw opportunity fair вырос ровно×5 при неизменных остальных rates.

| Материал | Raw fair1→5 | BUY1→5 | SELL1→5 | NPC liquidation |
|---|---:|---:|---:|---:|
| Stem1864 | 1.065→5.326 | 50→50 | 53→53 | 50 |
| Varnish1865 | 2.055→10.273 | 100→100 | 105→105 | 100 |
| Animal Bone1872 | 1.538→7.692 | 75→75 | 79→79 | 75 |

Это NPC liquidation floors, поэтому механическое×5 цен неверно; offline BUY без премии не оправдывает дорогу.
Gear1 без живого NPC supplier: source fair1386→6930, ask1456→7277; BUY запрещён как nonstackable.
Это source-only quotation, не цена выгодной доступной покупки: настоящий stock NPC ceiling и альтернативная цена проверяются при runtime quote.
Known-NPC corridor contract сохранил bid ниже acquisition и не ниже liquidation; unfunded BUY не превратился в доход.
1835/1917 в таком source snapshot UNPRICED, доступность/цена не выдумывались.
При одинаковом test wallet100000 allocator в обеих моделях выделил3 kinds/300units/reserve22500.
Базовый merchant capital100000/identity и global30000000 не менялись.
Отдельный existing market provision: BUY grant50000000/cooldown1800s, lifetime250000000/identity, global10000000000; эти limits также не менялись.
Это конечное OWNER_PROVISIONED финансирование продавцов, не заработок NORMAL и не источник бесплатных средств для него.
`PhantomMarket.ini`/`PhantomMerchants.ini` публично не изменялись; изменение цен/капиталов не потребовалось по этой проверке.
Наличие quote не является реальной ликвидностью; NPC sale доказана ниже, реальные offline BUY и manufacture не доказаны.

## Проверки и промежуточные отказы

Baseline: life-scenarios116 seed11611601 —32/32; Supply071 —7/7; City098/Living111 PASS; market quote —6/6.
Два новых Supply071 контракта: RED на отсутствии planning API при focused compilation, затем GREEN9/9 после реализации.
Это не runtime RED старого600-cap; конкретный старый causal blocker установлен source review.
Focused cases:1920 fast physical/120 slow caster,4×600 cap, poor28 units, weight5 units, no free/overweight order, codec/binding/deadline/revision conservation.
После последней production правки:11632/0719/City098/Living111/Merchant10711cases PASS.
Независимый exact six-artifact commerce review и отдельный two-file OwnerConfig review: P1=0/P2=0; native им не подменяется.
`ant jar` —PASS26s; final JAR `3aa70ae7a8a0d1911e9b77f726d59ef97d0f1d46dd0aa262dc7d87b95f6879c8`.
Общий `ant verify` с чужими DB gates не запускался.

Initial Save внутри sandbox получил Java AccessDenied; readonly privileged verifier прошёл38rules/XML.
Владелец явно разрешил повтор Save вне sandbox. Git helper дополнительно потребовал process-only exact safe.directory из-за разных Windows identities.
Global Git trust не менялся. После разрешения и exact process trust Save прошёл.
Три ранних Ant source-drift challenges для×10 остановились до компиляции; поздний raw diagnostic выявил Set-Acl module-autoload failure.
Первая попытка explicit manifest import также RED из-за nested module resolution; ограничение поиска только на время импорта дало GREEN.
Сохранённые×10 и финальные×5 сборки/post-Verify прошли; после tools fix настоящая compile-tests PASS25s. Отказы не скрыты.
Первый Living111 запуск ошибся FQN, market-quote — cwd; исправлены orchestration args, продуктовый код ради них не менялся.

## Единственная native среда

Full G0 PASS до CREATE: privileged PID/creation/image/command/SID/ACL identity, 1228 protected hashes,
physical source/stage disjoint,203Geo, sealed29094 native inputs, exact JAR, full SQL visibility/grants, foreignSessions0, port3308/datadir/MariaDB11.4.13 pins.
Schema `l2jmobiush5_phantom_test_118`, UUID `af36b853-4b02-4f36-a9ae-7f1113ea817c`; ровно один CREATE, stock121files/203statements,18DROP пропущены.
Stage owner+SYSTEM ACL; GameServer17778,24cohort/8active/24finite offline merchants — private test overrides.
После CREATE и до первого GameServer обновлён только private read-only receipt observer: сохранены5preimages/SHA,
изменены2private source/class inputs, их seal обновлён; module/Geo/JAR/DB authority неизменны. Свежие runtime G0 guards сохранены.
Native1 начат на финальном×5. NORMAL2/object268484968 выбран существующим calendar_ACTIVE_ready_target predicate.
Initial materialized EXP348/SP40/Adena5235:5000seed и235historical отдельно от нового native заработка.
В native1 NORMAL2 реально оплатил NPC30561/list3056100 BUY1835×332 за2656A:
receipt `d7dd265d303e7fef693a98080476f9967cc0794e65627fe9cc0c8e12f4c23a89`, COMMITTED rev2,5235/0→2579/332.
Это первая закупка, оплаченная исходным кошельком, не доказательство earned-only финансирования или native4-batch серии.
Native1: вернулся к фарму, новый rewarded kill дал +87EXP/+10SP/+105A; final level3/EXP435/SP50.
Cash2684=5235+105−2656; paid stock object268517638 сохранил328 из332, расход4×8=32A.
Короткий operating result +73A=105−32; оставшиеся328 имеют purchase cost basis2624A, это оплаченный запас, не заработанный лут.
Новых материалов/gear equity, NPC proceeds, offline sale proceeds и travel fee в этом отрезке не доказано.
Стартовые предметы сохранены; native sale/warehouse/craft не форсировались ради приёмки.
Native1 graceful STORE/exit0/postflight PASS:24 checkpoint identities, World0/online0/foreign SQL0, runtime error0bytes, protected1228 unchanged.
Native2 после свежего G0 восстановил того же NORMAL2/cash2684/stock328, без reseed; новый reward +210EXP/+10SP и10 Pants28.
Из них один был экипирован; девять genuine excess objects268488309–317 выбраны прежним native maintenance SELL.
Native3 продолжил этот уже выбранный intent после STOP/postflight2; это новая проверка earned sale, не повтор первой закупки.
В итоге проданы все10 заработанных Pants28, objects268488308–317, по35A: NPC31424/list3142400, proceeds350A.
Все17 уникальных COMMITTED operations NORMAL2:7 BUY и10 SELL; старые receipts при restart повторно не засчитывались.
Финальный cash770=5000seed+235historical+105new native Adena+350NPC proceeds−2656shots−2264gear; offline proceeds0.
Paid shot object268517638:332→323, расход9×8=72A; remaining stock cost basis2584A. Starter stock/исходные items сохранены.
Купленные gear253/21/29/41/35/48 имеют cost basis2264A; это расход капитала, не earned gear и не доход.
Короткий operating result +383A=105+350−72, наблюдаемые travel/bank fees0; непроданного нового лута/материалов0.
Reference NPC liquidation cash+new gear+remaining paid shots =2724A, delta−2511 к initial5235; это оценка, не исполненные продажи.
Cost basis и NPC liquidation различаются; +383 не доказывает долгосрочную прибыль или отсутствие depreciation/spread.
В native3 подтверждены продажи/новые paid gear; нового reward после последней продажи до STOP не было, supply preparation ещё шёл.
Все3 фазы graceful STORE/exit0/postflight PASS,24 checkpoint identities, World/online/SQL sessions0, error logs0bytes, protected1228 unchanged.
После JAR исправлено только test receipt fixture: amount — total cost, не unit price;0719/9, recipe-cost4/4, craft-preconditions3 PASS.
Sealed native test/tool sources старее последних test/tools правок; production Java/data/JAR неизменны. Полная source-tree parity не заявляется.

## Финальная приёмка

C0 CONFIG_PERSISTED; C1 POLICY_VERIFIED; actual decisions и native332 BUY/10 earned SELL подтверждены.
C2 реальный лут/выручка подтверждены; ценных craft materials в измеренном луте не было, warehouse/BOM retention остаётся policy evidence.
C3 native332 вместо фиксированных60 и расход оплаченного stock подтверждены; native>600/4-batch и earned-only refill не доказаны.
C4 sameDB/sameJAR с новым reward после restart PASS; native1/2 rewards и native3 sales разделены, after-sale reward не доказан.
Нативные offline trade, dwarf craft и полная длительная rentability не доказаны. Общий gate PARTIAL_ECONOMY, не SUCCESS.
Final whole-scope independent review P1=0/P2=0; exact allowlist/whitespace/две encoding проверки PASS. STOP118; TASK119 не начат.

## LoginServer и диагностическая сводка

Для этой headless campaign LoginServer/клиентский вход не требовался; второй сервер не запускался.
Из summary исключены ровно1150 записей:575 exact INFO Connecting и575 exact WARNING unavailable/retry.
Whitelist проверяет целиком logger, severity, body и отсутствие continuation; suffix/stack trace/protocol/auth/checksum/disconnect не исключаются.
Raw java0/1/2.log сохранены побайтово, SHA/размеры записаны private receipt; непарсируемые/continuation строки сохраняются в diagnostics.
Все13 прочих WARNING видимы:3 отсутствующих optional custom-buylists directory,4 visible-farm stalls,6 штатных shutdown events.
В частности NO_USEFUL_NATIVE_FARM_PROGRESS/NO_SELECTABLE_NATIVE_FARM_TARGET не скрыты как Login noise; иных Login events0.
Predicate и multiline parser regression PASS; независимый P2 по continuation исправлен и повторно reviewed, новых P1/P2=0.
Для будущего реального client-login нужны собственные изолированные LS+GS после полного G0:DB/ports/process owners/conflicts;
после проверки — штатный STOP обоих, exit codes, свободные порты/SQL0, чужие процессы не трогать. Публичный log framework не добавлен.

## Scope и воспроизводимость

Разрешённый public scope11: Rates.ini/OwnerConfig.json;3 commerce Java owners;3 existing/new Java test files;
OwnerConfig.ps1/OwnerConfigChecks.ps1; этот отчёт. Первые2 уже опубликованы отдельно, final gameplay/tools/report scope9.
Private G0/native protocol и diagnostics не входят в commit; protected main/package/history не переписываются.
Git явно разрешён TASK118 и владельцем для pinned baseline, WIP/scope/diff, двух scoped commits и обычного push.
Использованные формы: rev-parse/symbolic-ref/status/worktree list/for-each-ref/ls-tree/merge-base и exact-path diff/diff --check;
ls-remote origin refs/heads/feature/phantom-world; clone --no-hardlinks --single-branch; OwnerConfig helper rev-parse/show exact revision:path.
Запись: add -- <exact paths>, commit -m <task118 subject>, push https://github.com/kpCat/L2J.git HEAD:refs/heads/feature/phantom-world.
Никаких force/reset/clean/stash/merge/rebase/global Git config; safe.directory только process-local для собственной source-копии.
Exact финальный SHA и post-push live HEAD сообщаются в ответе и private Git receipt, без самоссылки commit внутри собственного отчёта.
Mojibake-маркеры в изменённых файлах проверены:0, полный набор27.
Escaped Cyrillic в изменённых файлах проверены:0, шесть regex.

Pre-publication goal snapshot:931739 tokensUsed/8726s; это счётчик инструмента до final review/Git, стоимость USD не сообщается.
Известный расход: один checkout/stage/DB;3 native starts;3 успешных compile-tests и1 final jar,4 source-drift build refusals до javac.
DB-free suite passes15, отдельно2 первоначальных FQN/cwd ошибки; focused RED compilation1; полный ant verify0.
OwnerConfig runner4 starts:2 PASS (старые5/final6),2 RED; private root helpers20, включая diagnostics/evidence, не20 игровых движков.
Начальный GitHub fetch_commit ошибочно вывел около96k tokens diff; этот лишний расход признаётся, цена/точное число поисков не выдумываются.
