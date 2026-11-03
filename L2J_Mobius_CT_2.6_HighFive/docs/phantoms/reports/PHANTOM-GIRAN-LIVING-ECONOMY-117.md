# TASK117 — Giran Living Economy

Статус: **PARTIAL_CITY / WEALTH_NOT_PROVEN**, не FULL SUCCESS.
Дата: 03.11.2026. OWNER_VISUAL_PENDING отдельно. TASK118 не запускалась.

Обычный NORMAL2 доказал native farm → собственную потребность → оплаченную NPC-покупку →
возвращение → новые native rewards. SameDB/sameJAR restart10 сохранил того же персонажа и
продолжил оплаченные улучшения экипировки и дал ещё11 native rewarded kills.
Физический Giran visit с мотивом и paid trade не доказан. Долгосрочное богатство также не доказано.
Последнее указание владельца: сначала осмысленное предложение, затем нужный конкретный маршрут;
отсутствие выгодного Giran-предложения фиксировать честно.

## Baseline, read-first и сохранность

Начальный live GitHub kpCat/L2J feature/phantom-world:
`a7cb9a7c2bd4ced33ed693c4d6f3d22f809981a5`, parent
`2b729ebed628d538fef8b8dae124d511658965c4`.
Уже опубликован безопасный subset `33251ecb6c39c5a3f297dbe1fb98a28303f7e7db`, parent a7cb9a7.
Это собственная публикация TASK117; continuation проверяет live33251 и останавливается при внешнем drift.
Следующий scoped commit/push и remote SHA указываются в финальном ответе после проверки.

Полностью прочитаны семь файлов TASK/ACCEPTANCE/EVIDENCE/GOAL/HANDOFF/PRODUCT_DECISIONS/SOURCE_MAP,
root AGENTS.md, module Agents.md, CODEX_WORKFLOW_CONTRACT, PHANTOM_CODEX_EFFICIENCY_STANDARD.
Прочитаны README/readme.txt, PHANTOM_CURRENT_STATUS, TASK_PACKAGE_STANDARD, bounded master plan,
build.xml, ближайшие reports098/110/114/115/116 и ответственные production seams.
Вышестоящие AGENTS.md и .agents не найдены; модульного README.md нет.
Аналоги: существующие City098/Routes110/Supply071/Living111, AutoPlay owner/ActionLease,
конечный merchant Ledger099 и private115 G0/observer/version protocol.

Основной checkout HEAD236b965a116bbc79cccbef78b04c628225a629cb с dirty WIP109 не изменялся.
1222 protected текущих preimages проверяются SHA-256/размером в G0/postflight.
Использована одна отдельная физическая source-копия exact116 HEAD, clone --no-hardlinks;
runtime — единственная собственная TEST117:
`l2jmobiush5_phantom_test_117`, UUID `aa8dfaf8-3df7-494f-af91-750d467a5eea`.
MAIN/PLAY, TEST109–116, release057–061, клиент, исходная Geo и общая MariaDB не изменялись.
Runtime ACL owner+SYSTEM, физические пути, Geo203/1063452308 bytes, inputs29091.
JDK25.0.4.1, Ant1.10.17, MariaDB11.4.13, SQL loopback3308, собственный GS17777.
Shadow cohort24/active8/materialized8/merchant target24 — разрешённый test scope;
public owner rates, Geo, enchant, market policy, конфиги и secrets не менялись.

## Проверки и исправления

Первый настоящий DB-free causal filter: life-scenarios116 seed11611601 32/32,
Supply071 seed11711701 7/7, targeted City098 и Routes110 — exit0.
Нативный запуск начат только после полного privileged G0, full metadata/grants/foreignSessions0,
PID/FILETIME/binary/config/datadir identity и отсутствующей own117.
CREATE был ровно один: stock121 SQL files/203 statements;18 DROP пропущены.
Нет adopt/reset/reseed/drop чужой либо существующей БД.

Проверенные production изменения одной commerce/return цепочки:

1. City visibility: lawful native WALK не отменяется из-за human PREWARM; observed trading допускается
   только для свежего bound COMPLETED city child. GK/beforeWorldSpawn/NoStore/Geo/spacing/CAS fences сохранены.
   Existing111 actual-policy RED→GREEN; это не native Giran proof.
2. Combat после торговли: living NPC-merchant ошибочно попадал в resource viability как PvE target.
   Теперь viability относится к living Monster; низкие ресурсы по-прежнему требуют REST.
   Existing093 RED69/70→GREEN70/70.
3. Merchant stock: cumulative128 kinds при очередном birth вызывали Invalid bounded merchant stock.
   Учитывается distinct union issued/pending/new kinds до reserve, role8 и все finite quotas/CAS сохранены.
   Existing099 exact exception RED→GREEN; лимит/schema не повышались.
4. Early REST: canonical historical idle может сесть через существующий AutoPlay/ActionLease/player.sitDown
   после проверки claim и свежих native quiet/threat/HP/epoch фактов.
   Нет fake farm Session, HP projection, продления phase deadline или сброса proof debt.
   Existing093 RED70/71→GREEN71/71; Native7 и Native9 прошли реальный sit→farm.
5. Supply WALK: после отсутствия полного коридора переиспользуется существующий submitWalkEpisode/
   NativeWalkBudget, с общим original-start budget, exact physical checkpoints и прежним durable journey.
   Промежуточный endpoint не объявляется merchant arrival. Existing111 RED→GREEN; Native8/9 реально прошли WALK.
6. Strict offline income: существующий Multiparty StoreSell/PrivateStoreService подключён к продаже
   разрешённого disposable surplus действующему BUY Player. Реальные count/hash/price/capital и
   PhantomLootDispositionPolicy повторно проверяются; starter/need/learned recipe/global craft reserves сохранены.
   Snapshot READY/PHANTOM-owned BUY берётся до native-list lock. World/global player scan не добавлен.
   Новый income допускает только WALK и штатные GK с maximumFee0: paid income travel UNSUPPORTED.
   Это закрывает найденный review P2 min-fee estimate vs native time-first debit без нового fee-ledger.
   Positive POLICY/CODEC проверены; paid StoreSell и полный native selector-positive НЕ наблюдались.
7. Оценка Supply использует существующие certified D1 paths до4GK и maximum fees.
   Это admission estimate, не Geo/native route proof; исходный GK catalog54 не расширен.

Read-only review исправленного e4 subset: P1/P2=0.
Последний affected gate после exact source возврата: 11632/0717/City098/Routes110/Living111 exit0.
Ранее для той же reviewed source: combat71, historical-idle-visible0686, Merchant099,
Travel089/091 — exit0. Общий ant verify с DB-проверками не запускался.
Optional OwnerWorld075: исправленная cwd-попытка7/8; оставшийся obsolete source-marker тест
падает так же на baseline, вне изменённого scope. Не объявляется GREEN.

## Native evidence и ограничения атрибуции

Текущий финальный production JAR:
`e4e63f7438b2b2ed2ba46850acef4bd283cc0a13491f0cc99d5e78a124e47d10`.
Все14 Java source/test файлов и JAR source-копии побайтово совпали с sealed runtime Native9.
После этого JAR не пересобирался. Native9 и10 используют ту же DB и тот же JAR.

| Runs | Наблюдение |
|---|---|
| 1–3 | Диагностика; main error-log обнаружен поздно. Run3:928 merchant stock exceptions. Не clean acceptance. |
| 4 | JAR7674, NORMAL3:7 rewards/+3570EXP/+340SP; error-log пуст. |
| 5–6 | Тот же7674; native rewards0, finite REGEN PHASE_DEADLINE. Полезный restart не доказан. |
| 7 | JAR3038, NORMAL4: early REST,4 rewards420EXP40SP; затем реальный cliff-route отказ. |
| 8 | JARf243, selected с начала NORMAL5:7 rewards609EXP70SP. Вторичный NORMAL4 дошёл в DarkElfTown, купил и вернулся; его новых rewards8 нет. Акторы не объединяются. |
| 9 | JAR e4, NORMAL2:13 rewards1098EXP126SP, paid1835, возврат, ещё6 rewarded kills. |
| 10 | Тот же e4/DB, тот же NORMAL2, новый epoch; сохранены деньги/43shots, новые paid gear/equip, затем11 rewards957EXP110SP. |

Native9: NORMAL/WORLD profile2/object268484316, class44, epoch508776837076600,
root8649908324164858117. Начало12:54:06Z: level2/EXP348/SP40/Adena5047.
До торговли7 native rewards, кошелёк5119.
Receipt `88efb22492a699a8429d56f89bbaa979b353af182e8543d0ca2f54c6c4c10895`:
root/child revision2, NPC30561/object268462872/list3056100, BUY1835×60 за480A,
COMMITTED5119/0→4639/60, World(-44184,-115464,-216), instance0.
Затем фактический return к PvE и ещё6 kills/+522EXP/+60SP/+74A.
Итог13 rewards/+1098EXP/+126SP; level4/EXP1446/SP166.
Всё это Orc area, не Giran и не координаты FOLLOW-наблюдателя.

Native9 STOP/exit0/postflight PASS, UTC13:07:49Z; error9.log0 bytes.
Restart10 восстановил profile2/object268484316, новый epoch514553377367400,
Adena4713 и43shots: старые480A не списались повторно.
Новые COMMITTED: item253×1/678A (4713→4035), item22×1/2150A (4035→1885),
item29×1/1345A (1885→540), item35×1/32A (540→508), item48×1/32A.
Предметы реально появились/экипировались; это новые операции/revisions, не replay старого receipt.
После покупок/возвращения получены11 новых native rewards957EXP110SP, native unproven=NONE.
Root8649908324164858117 сохранён, revision7/farm.background, targetNPC18342 — законное перепланирование.
Final STORE: level4/EXP2403/SP276, cash577, тот же stack1835/object268488478 count22.
Экипированы253/22/29/35/48; weapon253 — Spiked Gloves по stock ItemData.
STOP/exit0/postflight10 PASS UTC14:51:20Z; archived error10.log0 bytes, own/foreign sessions0, World/JVM0.
Сохранены24 cohort identities; старый receipt9 присутствует как история, не новая оплата.

Первый старт10 fail-closed до JDBC/GameServer: истёк локальный6h owner expiresUtc.
После нового явного указания владельца продолжать тот же scope продлён только expiresUtc;
тот же UUID/schema/SID/JAR/initial inputs, byte preimage/SHA сохранены, expiry check не отключён.
Первый File.Replace с null backup-path не применился; предварительный receipt сохранён как failed,
затем запись/readback проверены. SQL authority/db.created не менялись, полный G0 повторён.
Это не изменение игрового времени, rates или данных персонажей.

## Экономика: факты вместо заявления о богатстве

Rates.ini source==runtime: XP3/SP5; death amount10/chance8, spoil amount20/chance10.
Для item57 overrides amount1/chance1 **заменяют**, а не дополняют global10/8.
NpcTemplate использует if/else per-item selection; stat/champion/level-gap modifiers идут отдельно.
PremiumLootPolicy.eligible исключает native-managed NORMAL, несмотря на EnablePremiumSystem=True.
Индивидуальные per-kill champion/stat modifiers отдельно не измерены; base override1/1 доказан.

Native9 balance NORMAL2:

| Категория | Adena / stock |
|---|---:|
| Исходный seed | 5000A |
| Исторический earned до native9 | 47A |
| Новый native-earned9 | +146A:72 до покупки +74 после |
| Реальная закупка1835 | −480A,60шт по8A |
| Финальный cash9 | 4713A, delta−334A |
| Осталось1835 | 43шт;17 израсходовано |

По закупочной стоимости расход17shots=136A;43 остатка=344A.
Cash+stock cost basis даёт +10A относительно5047, **не cash profit и не liquidation/fair value**.
После покупки6 kills дали74A при расходе136A:−62A по этой оценке.
В native10 заработано101A, paid gear4237A:4713+101−4237=577.
Израсходовано ещё21shots, осталось22; стоимость расхода168A против101A native income.
Совместно9+10:24 native rewarded kills,2055EXP/236SP,247A gross native income;
38 shots расхода по8A=304A, operating book result **−57A** до оценки gear/inventory по fair/liquidation.
Полная cash identity:5047+247−480−4237=577. Начальный seed и historical47A отдельно.
Cash+gear acquisition4237+остатокshots176=4990 против исходных5047;
это отрицательный результат даже по закупочной оценке, не доказательство богатства.
Долгосрочный положительный доход и постепенное богатство НЕ доказаны; обнаружен отрицательный короткий участок.
Новые gear10 в основном оплачены сохранённым seed; не называются полностью native-earned развитием.
NPC sales, доход от offline BUY, spoil и quests у NORMAL2 в9 не наблюдались.
Merchant seed/provision, historical earned и native-earned не смешиваются.

Для проверенных начальных SKU Orc NPC выгоднее текущего Giran SELL:
1/13/308:678A против712A;35/48:32 против34;41:48 против51;28:81 против86.
Native Product/CatalogLoader/baseTax Orc15% подтверждены source:
Jakal30558/list3055800(item1/13)/3055801(item308), Kunai30559/list3055900(item28/48/35/41).
Native10 действительно заплатил32A за35 и48 против Giran34A для каждого.
Поэтому игроку не навязывался Giran и не менялись цены/ассортимент ради gate.
В gear branch offline рассматривается только при пустом NPC choice; это ограничение будущего сравнения,
но текущая более выгодная SAME-SKU Giran пара не обнаружена.

## Торговцы и границы quantity/craft

В own117 наблюдались24 настоящих offline Players:15 SELL/7 BUY/2 MFG, instance0 в Giran,
finite inventory/capital/list, clientNull/identity/store ownership; captest24, а не заявленные250.
Native4–10 error-logs пусты, stop/restart сохранял offline rows24; Native9/10 lines262.
OPEN/цена/MFG menu не объявляются paid trade. Оплаченный customer у этих offline stores не наблюдался.
Final10:15SELL/7BUY/2MFG; persisted offline hash9=10
ae2c8361384748ac66b9d238384f5c37a230e4e6bd6627057c9159158a4c45ed.
Legal spawn/NoStore/observed-rotation fences не менялись. Отдельный визуальный прогон клиента в Giran не выполнен.

Quantity source-map:
- dist/game/data/phantoms/life/high-five-autonomous-supply-v1.xml: targetHits60, lowWater3,
  minimumAdenaReserve500, retry30s, journey8min; policy допускает targetHits≤100.
- commerce/L2jPhantomSupplyJourney:369 targetStock=min600/perHit×targetHits;
  mobilePurchase:737 count≤600, capacity/weight, движение и load≤60%; NPC-source parse:1477.
- PhantomAutonomousSupplyPolicy:33–39 reserve — savings target, НЕ ROI/anti-bankruptcy guarantee;
  полезность поездки не является полноценным farm-duration/cost-per-reward planner.
- PhantomInventoryMaintenanceIntent:21/31: до4 операций и count≤600.
  Существуют последовательные maintenance SELL после COMMITTED, а не общий bulk-shot purchase loop.
- PhantomCommerceReceipt.OperationRequest не имеет общего cap600;
  CommerceService:390–403 и backend требуют exact count/price×count/hash/conservation.

60/600 не повышались. Текущую native цепочку они не остановили, но для долгого/high-level фарма
тысячи shots/сотни-тысячи Blessed требуют отдельных подтверждённых последовательных партий
или согласованного архитектурного решения, не механического изменения константы.

PhantomLootDispositionPolicy сохраняет starter/need/learned recipe/unknown/enchanted/equipped,
crystal/recipe/craft reserve; warehouse и native commerce остаются владельцами операций.
OfflineSupply/acquisition planner имеет BOM/manufacture quotes и success-rate inputs;
автономное сравнение всех keep/buy-BOM/learn-recipe/craft/60%-risk/buy-finished вариантов,
самостоятельное производство гномами и положительный ROI этим прогоном НЕ доказаны.
Это source-map ограничений, не новая микрозадача и не запуск TASK118.

## Отклонённые эксперименты, scope, ресурсы

До последнего уточнения владельца source-only audit нашёл missing Orc30576/return legs в54 D1
и исследовал foreground catalog155/finite preselection. Это начато до подтверждённого Giran quote.
По указанию владельца работу остановили: три source файла и private audit сохранены побайтово/SHA,
затем exact пути возвращены из sealed native9. Ни новый catalog, ни preselection/hop-index
не попали в JAR/runtime/публикацию. Их fixture/source результаты не выдаются за native evidence.
Слишком широкие/обрезанные чтения и эта ненужная ветка увеличили расход; скрывать это нельзя.

Bounded exception:15 public файлов одной causal commerce/return цепочки,14 Java/test + этот отчёт.
Разрешённый Java prefix: java/org/l2jmobius/gameserver/phantoms/:
PhantomSystem; background/{PhantomVisibleAutoPlay,PhantomHistoricalBackgroundService,PhantomVisibleFarmTravel};
combat/PhantomCombatPolicy; commerce/{L2jPhantomSupplyJourney,PhantomOfflineSupply,PhantomSupplyIntentStore};
economy/{PhantomAutonomousMarketProducer,PhantomMerchantBootstrap,PhantomOfflineMerchantService}.
Tests: PhantomLivingCycle111Test, PhantomPveReadiness093Suite, PhantomMerchant099Checks.
PhantomCompositeTravelPlanner, configs, schemas, Geo, binaries, secrets и private helpers исключены из public diff.

JAR builds8, wall time177s; один earlyREST pre-review build отброшен и не установлен.
Физических source checkout1, собственных DB1, native runs1–10; pre-DB expiry failure не является game run.
Measured native wall sum10623019ms =2h57m3.019s; run9=844900ms, run10=1206876ms.
Peak recorded heap4067342432bytes (около3.79GiB), max threads136, JVM -Xmx4g.
14 активных private helpers переиспользуют115 protocol; архивные эксперименты не активны.
Goal snapshot перед публикацией:2129942 tokens/26106s (7h15m6s), без заданных hard time/token limits.
Это счётчик Goal, не invoice; денежная стоимость API и total CPU seconds недоступны.
Финальный счётчик после commit/push — в ответе, без выдуманной USD-суммы.

Git разрешён текущей задачей для baseline/scope/publication. Использованы команды с bounded путями:
rev-parse HEAD / <baseline>^ / --abbrev-ref @{upstream} / --git-dir / --git-common-dir;
symbolic-ref --short HEAD; status --short --branch; status --short --untracked-files=no;
status --porcelain --untracked-files=all -- <protected paths>; worktree list --porcelain;
for-each-ref; ls-tree; merge-base; ls-remote <canonical-or-origin> refs/heads/feature/phantom-world;
clone --no-hardlinks --single-branch <exact116-local> <own-source>;
diff [--stat|--check] [33251ecb] -- <exact paths>, review diff --unified=2 33251ecb -- <exact paths>.
Изолированная sandbox-owned copy использует per-command -c safe.directory=<own-source> -C <own-source>;
global safe.directory не менялся. Publication: add -- <exact9, затем exact12 paths>;
diff --cached --name-only / --check / --stat / -- <exact paths>;
commit -m "TASK117: guard native city continuation and finite merchant stock" для33251;
commit -m "TASK117: preserve native commerce return and same-JAR restart" для следующего subset.
Не выполнялись clean/reset--hard/stash/force checkout/add./force push/rebase/history rewrite.
Source origin локальный, поэтому публикация — только явный https://github.com/kpCat/L2J.git
HEAD:refs/heads/feature/phantom-world после fresh remote/scope/secrets/review gates.

- Mojibake-маркеры в изменённых файлах проверены:27 markers,15 exact paths, PASS.
- Escaped Cyrillic в изменённых файлах проверены:6 regex,15 exact paths, PASS.
- Native10/postflight PASS; финальные staged scope/secrets/review/remote gates выполняются перед push.

## Итоговые gates

| Gate | Результат |
|---|---|
| A causal DB-free | PASS, уровни POLICY/CODEC/native в отчёте разделены |
| B мотивированный physical Giran | NATIVE_REQUIRED; выгодного текущего предложения не установлено |
| C paid commerce → return → new rewards | PASS, NORMAL2 в Orc area |
| D настоящие offline BUY/SELL/MFG | World/identity/stock/restart PASS; paid customer/визуальный клиент NOT_RUN |
| E sameDB/sameJAR restart/safety | PASS9→10, новые11 rewards, STORE, STOP/postflight, no replay |
| Wealth growth / bulk shots / полный craft ROI | NOT_PROVEN; наблюдён operating book−57A, лимиты/source-map сохранены |

Честный результат — PARTIAL_CITY. Выгодная поездка в Giran и положительная долгосрочная экономика
не выдумываются. Проверенный безопасный subset публикуется; текущая работа заканчивается на TASK117,
без создания микрозадач, новых ресурсов и без запуска TASK118.
