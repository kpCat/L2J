# TASK117 — Giran Living Economy

Статус: **PARTIAL_CITY / NATIVE_REQUIRED**, не FULL SUCCESS. Безопасный исправленный subset; gate B не закрыт.
Дата: 03.11.2026. OWNER_VISUAL_PENDING учитывается отдельно.
Baseline live GitHub `kpCat/L2J`, `feature/phantom-world`: `a7cb9a7c2bd4ced33ed693c4d6f3d22f809981a5`;
parent `2b729ebed628d538fef8b8dae124d511658965c4`. SHA содержащего этот отчёт scoped commit и результат push — в финальном ответе.

## Read-first и сохранность

Полностью прочитаны TASK, ACCEPTANCE, EVIDENCE, GOAL, HANDOFF, PRODUCT_DECISIONS и SOURCE_MAP пакета117;
root AGENTS.md, module Agents.md, CODEX_WORKFLOW_CONTRACT и PHANTOM_CODEX_EFFICIENCY_STANDARD.
Также прочитаны root README, PHANTOM_CURRENT_STATUS, TASK_PACKAGE_STANDARD, bounded master plan/readme.txt/build.xml;
bounded reports098/110/114/115/116 и заданные production seams. Устаревшие пути/credentials в Agents не использовались.
Вышестоящие AGENTS.md и каталог `.agents` не найдены; модульного README.md нет, используется readme.txt.
Расширения чтения: StoreService lifecycle/CAS, System admission wiring, LocalityEnvelope, существующие City098/Routes110/
Supply071/LivingCycle111/OwnerWorld075 и private115 G0/probe/observer/version protocols — для конкретных safety/test границ.
Большие production файлы обычно читались по symbols/ranges; несколько неудачно широких выводов были обрезаны.
Это потеря эффективности, а не доказательство чтения полного вывода; позднее использованы нужные bounded ranges/XML attributes.
Аналоги: настоящие planner/codec fixtures098, package-private seams111 и существующий native115 observer/G0.
Стек сохранён: Temurin25.0.4.1, Ant1.10.17, MariaDB11.4.13, Geo203; новых зависимостей/schema продукта/AI нет.

Основной checkout имеет HEAD `236b965a116bbc79cccbef78b04c628225a629cb` и уникальный dirty WIP109.
Его branch/HEAD/файлы не обновлялись. Отдельная физическая source-копия создана `clone --no-hardlinks` из exact116 HEAD;
существующие worktrees только читались. До начала сохранены текущие SHA-256/размеры1222 protected файлов, включая WIP и task117.
Ни один уникальный WIP-файл не заменялся/не переносился. Runtime пишет только в собственный внешний TEST117 stage.
Приватные evidence/source доступны только двум локальным execution identities и SYSTEM; сам runtime stage — owner+SYSTEM.
MAIN/PLAY, TEST109–116, release057–061, исходная Geo, сервис MariaDB, UAC/firewall и клиент не изменялись.

## Исправленный причинный blocker

Source-review выявил два P1 на одной цепочке: `freeDestination` требовал отсутствия human PREWARM даже для native WALK,
а `consider` применял тот же quiet recovery guard к только что прибывшему обычному city child.
Удалён только human exclusion из destination: PeaceZone/NoStore/Geo/World/spacing/reservation остаются.
В `consider` наблюдаемая торговля разрешена только для bound COMPLETED city child с `quote.expires`.
Null/legacy/proposed/ACTIVE/FAILED, stale root, foreign actor, старый/истёкший quote исключения не получают.
Сохранены actual identity/class/epoch, root/revision, fresh stock/price/need/funds, action admission, CAS и STORE.
`beforeWorldSpawn` по-прежнему требует unobserved; native GK сохраняет обе visibility fences, arm-before-payment и receipt.
Новых durable/runtime writers, workers/timers/caches и способов начисления денег/предметов нет.

Focused test добавлен в существующий same-package `PhantomLivingCycle111Test` с настоящими admitted/arrived/codec fixtures.
При прежнем production predicate, выделенном без изменения семантики, получен RED1:
`TASK117_OBSERVED_BOUND_CITY_ARRIVAL_BLOCKED`. После narrow fix — GREEN0, все прежние111 checks сохранены.
Это **POLICY/CODEC_VERIFIED**, не DECISION/NATIVE proof. Малого готового DB-free seam для World/Geo destination нет;
сохранность остальных guards проверена exact source review. Независимый review текущего diff: P1=0/P2=0.

Bounded exception:9 public файлов по одной цепочке economy/return/offline. Дополнительные3 файла необходимы для
обнаруженного пользователем crash-loop admission; это не отдельный goal или housekeeping. Exact allowlist относительно High Five:

- `java/org/l2jmobius/gameserver/phantoms/economy/PhantomAutonomousMarketProducer.java`;
- `test/java/org/l2jmobius/tests/phantoms/PhantomLivingCycle111Test.java`;
- `java/org/l2jmobius/gameserver/phantoms/background/PhantomVisibleAutoPlay.java`;
- `java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatPolicy.java`;
- `test/java/org/l2jmobius/tests/phantoms/PhantomPveReadiness093Suite.java`;
- `java/org/l2jmobius/gameserver/phantoms/economy/PhantomMerchantBootstrap.java`;
- `java/org/l2jmobius/gameserver/phantoms/economy/PhantomOfflineMerchantService.java`;
- `test/java/org/l2jmobius/tests/phantoms/PhantomMerchant099Checks.java`;
- этот отчёт.

Native2 выявил настоящий RETURN blocker: selected target оставался merchant30002/object268462798; полные HP129/129,
MP138/138 давали resource EXPIRED до штатной очистки target. Новый primitive classifier в существующем CombatPolicy
проверяет PvE viability только живого Monster. Timers/threat/busy/attack permission/owner/epoch/CAS не изменены.
Existing093 regression: RED69/70 merchant ALLOW→EXPIRED; GREEN70/70, nonviable Monster EXPIRED/low-resource REST сохранены.
Native3 после исправления: тот же NORMAL3 получил3 rewarded kills,+315EXP/+30SP; merchant target штатно очищен.

По сообщению пользователя проверен основной GUI error log: native3 имел928 повторов `Invalid bounded merchant stock`.
Причина: birth stock не учитывал cumulative ledger128 kinds. Новый read-only capacity guard считает distinct union
issued/pending/candidate до подбора stock; final reserve/атомарный role+ledger commit и128/8/4096-byte/schema1 сохранены.
099 regression воспроизвёл точное исключение RED, затем GREEN; existing-kind reserve потребляет прежние finite quotas.
При отсутствии подходящего stock остаётся законный недобор target. Отдельный shutdown RuntimeException Own117View
вызван premature online=false приватного observer; helper исправлен по existing Population102 stock deleteMe pattern.
Это не изменение Player/ThreadPool. Старые native runs не считаются чистой приёмкой. Теперь main error0.log входит в gate.

## SQL/G0 и единственная среда

Единственная схема: `l2jmobiush5_phantom_test_117`, installation UUID `aa8dfaf8-3df7-494f-af91-750d467a5eea`.
Stage: `%TEMP%/Phantom-Native-Life-117-aa8dfaf8-3df7-494f-af91-750d467a5eea`.
Полный G0 перед CREATE: свежий published privileged OS receipt, PID/FILETIME/binary/config/loopback3308/datadir/version,
SQL full metadata visibility/grants, foreign sessions0, absent117, protected1222, disjoint physical ACL/UUID/owner,
sealed source/JAR/input29091/Geo203 и stock SQL plan. OS identity отдельно не считалась полным G0.
CREATE выполнен после `g0.full.pass`:121 stock SQL-файл,203 statement,18 DROP пропущены; adopt/reset/reseed=0.
Перед каждым native/postflight повторяются OS/process/protected/ownership/input guards и проверка live baseline перед start.
Shadow overrides: loopback17777, population24/active8/materialized8, merchant target/hard cap24.
Сохранены ordinary market cap2, merchant lines20, цены/финансирование, rates/Premium/Alt+B/Owner38/enchant/QoL/Geo настройки.
Merchant seed/funding конечен и классифицируется OWNER_PROVISIONED; это не native заработок обычного NORMAL.

## Native evidence и границы

Private receipt family: `%TEMP%/phantom-giran-economy-117/`; samples/life/inventory/stores/STORE — в `stage/private`.
Flight recorder эффективной policy выключен: flight log не получен и не объявляется evidence.
Baseline diagnostic JAR: `44f06da50152d844a05f6cdf0dee4913c5fd18793574c4f464eb2e8db5a6d7f2`.
Промежуточные JAR: `2199e4696c269fb8b80d9280a39c60ea1835a9b877ad713e9803a5a05d45756d` (city visibility)
и `23faeb5911af3e477e2c50647b9702b3466bfffb152c7cd3a5d1ecd960d091be` (resource target).
Final JAR: `7674c27298e4d8d4cf6f0d7f428cbe738665193d863275756f631c6eead94871` (merchant capacity).
Каждая переустановка только exact изменённых Java/test/JAR после STOP/postflight предыдущего запуска, byte backups/SHA сохранены;
initial DB authority/inputs receipt не переписывались. Диагностические успехи старого JAR не заменяют final pair.

| Наблюдение native1 | Проверенный факт | Граница |
|---|---|---|
| NORMAL22 | object268484612, class10/level3, epoch491110545529900, root2708093140276295190 | Настоящий World Player; observer не является actor |
| Paid NPC BUY22 | NPC30004/object268462800/list3000400, item3947×60,2400A; COMMITTED rev0;5078/0→2678/60;XYZ−84343,240597,−3720 | Talking Island, не Giran; initial5000+historical78 не native earnings |
| Return/rewards22 | Возврат к полю,5 новых native rewarded kills,+1218EXP/+140SP; stock60→49 | Реальное продолжение; не доказывает Giran |
| NORMAL3 | object268484369, epoch491193843190000, root5277150124167947492; тот же BUY3947×60,2400A,COMMITTED | Собственная identity/root/revision, не synthetic receipt |
| Return/rewards3 | 6 native rewarded kills,+522EXP/+60SP; новая оплаченная equipment7 | Historical начальные609EXP/70SP отделены |
| Giran stores | Физические offline Player SELL/BUY/MFG; ранний срез14SELL/7BUY/2MFG | OPEN/цена не объявляются paid trade |
| MFG identity | objects268484359 и268484555; реальные recipe/fee lists, World XYZ/PHANTOM lease; client=null | Customer payment/manufacture не наблюдались |
| Native1 finish | штатные STORE/STOP/exit0/postflight PASS; pending catchup0; identities/inventory сохранены | До production fix; только диагностическая эпоха |

Final7674 native4: NORMAL3/object268484369, class10/level6, epoch497109374157700, root5277150124167947492;
03.11.2026 09:41:59 UTC, XYZ−88699,241385,−3560:7 новых native kills/rewards,+3570EXP/+340SP, всего6066EXP/600SP.
Native4 штатно STORE/STOP/exit0/postflight PASS. Native5 на той же DB/JAR сохранил identity/items/receipts и24 stores,
но начал с HP8.9/176: длительное восстановление → sticky PHASE_DEADLINE → LOCAL_FARM_UNAVAILABLE; новых native rewards0.
Его EXP7851/SP770/Adena682 включают historical catchup и не засчитываются native результатом рестарта.
STOP/exit0/postflight5 PASS. Гипотеза native6 о здоровых сохранённых vitals опровергнута: начальные HP8.912675/176,
MP69.010984/171 повторились; epoch498344026106200, native rewards0 и PHASE_DEADLINE. EXP9426/SP920/Adena764 — не native credit.
Native6 штатно STORE/STOP/exit0/postflight PASS, game6.exit0 10:04:10UTC и postflight6.pass 10:04:34UTC, тот же final7674.
Native4–6 error0.log0bytes, SHA256 `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`, включая cleanup.
Это исправляет наблюдавшийся exception-loop, но не закрывает gameplay restart. Source review подтверждает существующий
конечный REGEN horizon120s/sticky proof debt и tests022: новый малый дефект для безопасного RED→GREEN не доказан.
Debt/timers/caps/HP не сбрасывались. Полное восстановление live vitals нельзя объявлять сохранением native snapshot.

Фактический ordinary Giran motive/route/arrival, paid commerce там и последующий RETURN ещё не доказаны.
Настоящий NPC BUY на другом острове не закрывает City gate; присутствие Гирана только в topology/quote не считается поездкой.
Координаты/follow/телепортации labelled test-human не являются travel evidence фантома. После cut NORMAL не задавались XP/SP/items/Adena/goal/XYZ.

Route finding: обычные travel owners видят54 сертифицированных D1 GK legs, без исходящего Talking Island leg.
Stock Roxxy30006 NORMAL0→Gludin имеет catalog/base fee18000A, фактический native debit не проверен.
Effective MaxFreeTeleportLevel=40 сохранён: production maximumFee для main class level6 возвращает0;
кошелёк NORMAL3 менее1000A сам по себе не доказывает недоступность поездки. Экономическая причина поездки не наблюдалась.
`observeNativeLeg` используется career owner, но raw leg имеет пустой toAnchorId: без arrival/checkpoint контракта
его включение в ordinary market может списать fee и затем сломать checkpoint. Исторические D1/provenance не подделывались.
Это source compatibility finding; native отказ именно на Roxxy не наблюдался. Hardcoded mustVisitGiran не добавлялся.
Не найден ordinary earned-surplus producer для продажи offline BUY; наличие generic executor не доказывает его вызов.
Новый выбор города/сеть маршрутов/экономический стимул без доказанной ошибки не внедрялись.

Final offline срез:15SELL/7BUY/2MANUFACTURE, все World Player, client=null, headlessSession=false, PHANTOM leases.
SELL268484273:XYZ82440,148860,−3464, например item12148/object268484276×1 по69634A.
BUY268484282:XYZ82040,148700,−3248, капитал50100000A OWNER_PROVISIONED;3947×100 по36A, реальные20 buy lines.
MFG268484359:XYZ82280,148780,−3464, recipes58:2340/59:2648/60:1748/61:3540/66:296/67:184A;
MFG268484555:XYZ81960,148860,−3464, recipes110:1636/111:1433/112:1433/113:4368/114:2704/115:13900A.
Каждый MFG имеет100000A finite provision. Paid manufacture/продажа BUY-покупателю/покупка SELL-клиентом не наблюдались.

| Gate | Результат |
|---|---|
| A | POLICY/CODEC_VERIFIED: required fast checks GREEN; end-to-end ordinary city DECISION не доказан |
| B | NATIVE_REQUIRED: NORMAL в Гиране не доказан |
| C | PARTIAL: реальные оплаченные NPC BUY/RETURN/rewards на Talking Island, на диагностических JAR; связанного Giran cycle нет |
| D | NATIVE_VERIFIED:24 физических offline stores BUY/SELL/MFG; оплаченные клиентские операции UNKNOWN |
| E | G0/STORE/STOP/SQL/protected PASS; final sameDB/sameJAR4→5→6 есть, новые useful rewards после рестарта не доказаны; NATIVE_REQUIRED |
| Visual | OWNER_VISUAL_PENDING; автоматические World samples не заменяют визуальную приёмку владельца |

## Проверки и промежуточные отказы

- compile-tests host: exit0,31s,5 прежних warnings; OwnerProfile38/XML/quest verifier PASS.
- Четыре `ant -f L2J_Mobius_CT_2.6_HighFive/build.xml jar`: exit0,25/21/20/24s. Ant запуски последовательны.
- После последнего production fix: life-scenarios116 seed11611601:32/32; supply071 seed11711701:7/7;
  combat-core seed11711701:70/70; City098, Routes110, LivingCycle111+117 и Merchant099+117 GREEN, exit0.
- Focused checks запускались после причинных изменений; общий DB-containing `ant verify` не запускался.
- Optional owner075: сначала4/8 из-за неправильного cwd; один исправленный game-cwd retry7/8.
  Остался исторический `ordinary-shop-rest-trip-keeps-root-and-rejects-paid-replay`: source substring end marker отсутствует
  уже в exact baseline116 (index−1), StringIndexOutOfBounds. Старый unrelated test не изменён; all-tests-green не заявляется.
- Setup: PS7 ACL overload отсутствует; Set-Acl denied SeSecurityPrivilege; bounded icacls grant собственной директории выполнен.
  Sandbox verifier/build AccessDenied; тот же неизменённый verifier host38 PASS. Никакие G0 guards не отключались.
- Git host status собственной sandbox-owned clone один раз отказал dubious ownership; global safe.directory не менялся.

## Ресурсы, Git и финальные guards

Native starts6, одна DB; первые3 — диагностические,4–6 — final7674. Native elapsed по resources1–5:
1647967/1184463/994069/388065/672436ms; native6=288992ms, всего5175992ms (86.27min).
Peak heap1–5:4067342432 bytes; peak threads136; JVM Xmx4g. Geo203=1063452308bytes, только физическая собственная копия.
Суммарные Ant jar90s; compile-tests31s. Final Java diff8файлов:120 insertions/8 deletions (production5,test3); report отдельно.
Private helpers14 активных:6PS+5Java+3Python, единственная native115 family; immutable Run117 preimage отдельно.
Включены G0/SQL/Geo/seal/version/observer/encoding guard, нового AI/DSL/экономики нет. Observer Java38226bytes;
SQL probe22295bytes; все active helper files суммарно103595bytes. Это заметный частный test overhead.
Поздняя проверка GUI error log стоила дополнительного diagnostic run; wrapper-only monitoring был недостаточен, исправлен gate.
Полные логи не публикуются, secrets/Geo/binaries не stage. Денежная стоимость/модельные внутренние ресурсы недоступны.
Git разрешён TASK117 для live baseline/local inventory/scope и scoped commit/обычного non-force push.
Основной checkout: read-only rev-parse/symbolic-ref/status/worktree list и ls-remote; собственная clone: exact diff/status и publication.
На resource snapshot перед публикацией:875893 tokens/8921s по goal tool; это не лимит, финальный счётчик — в ответе.
Повторы чтения/обрезанный вывод и поздний main-error check увеличили стоимость; полного CPU-time/денежного счётчика нет.

Точные семейства выполненных команд (пути source/module определены фактически):
`ant -f <module>/build.xml compile-tests`, четыре `ant -f <module>/build.xml jar`;
`java -cp <source>/build/phantom-test/bin;<module>/dist/libs/* org.l2jmobius.tests.phantoms.PhantomTestLauncher`
с аргументами `life-scenarios116 11611601`, `supply071 11711701`, `combat-core 11711701`;
тем же classpath main `org.l2jmobius.tests.phantoms.PhantomCityLife098Test`,
`org.l2jmobius.gameserver.phantoms.navigation.PhantomLivingRoutes110Test`,
`org.l2jmobius.gameserver.phantoms.economy.PhantomLivingCycle111Test`,
`org.l2jmobius.tests.phantoms.PhantomMerchant099Checks <module>/dist/game/config/Custom/PhantomMerchants.ini`.
Focused `javac` old-predicate/green пары использовали тот же existing runner; RED exit1/GREEN exit0, без новых test проектов.
`powershell.exe -NoProfile -ExecutionPolicy Bypass -File <private>/Run117.ps1 -Mode provision` один раз,
`-Mode run -Iteration 1..6` и `-Mode post -Iteration 1..6` последовательно; exit0 означает safety/STOP, не gameplay SUCCESS.

SQL final6:characters49/profiles25/components218/offline stores24/lines261; native population managed24 и1 newcomer.
Offline rows hash4=5=6: `261ca849b8f343e63a3d844fa57cc00c84e907b1c74568b07a550dc502d83630`.
Merchant/inventory hashes менялись законно при finite provision/catchup; общей byte-identical inventory не заявляется.
Final cleanup: foreign JVM/SQL0, charactersOnline0, materialization/navigation/combat actors/workers/leases0, штатный drain.

Git использовался по прямому разрешению TASK117: `rev-parse HEAD`, `rev-parse <baseline>^`,
`rev-parse --abbrev-ref @{upstream}`, `rev-parse --git-dir`, `rev-parse --git-common-dir`,
`symbolic-ref --short HEAD`, `status --short --branch`, `status --porcelain --untracked-files=all -- <protected paths>`,
`worktree list --porcelain`, `ls-remote origin refs/heads/feature/phantom-world`,
`clone --no-hardlinks --single-branch <exact116-local> <own-source>`,
`diff -- <exact scoped files>`, `diff --stat -- <exact scoped files>`, `diff --check -- <exact scoped files>`.
Publication: `add -- <exact9 paths>`, `diff --cached --name-only`, `diff --cached --check`, `diff --cached --stat`,
`diff --cached -- <exact9 paths>`, `commit -m "TASK117: guard native city continuation and finite merchant stock"`,
обычный `push https://github.com/kpCat/L2J.git HEAD:refs/heads/feature/phantom-world`; local116 origin не используется для push.
Для host own-source допускается только per-command `-c safe.directory=<exact-own-source>`, global config не меняется.
Изменения main/history/reset/clean/stash/rebase/force/stage-all отсутствуют; task/historical reports не переписаны.
Final scope9/private exclusion/protected1222 и live baseline проверяются перед commit/push; результат публикации — в ответе.
Combined source review: code P1/P2=0; P2 отчёта о base/effective GK fee проверен по production/config и исправлен.

- mojibake-маркеры в изменённых файлах проверены: отдельный27-marker guard,9 файлов.
- escaped Cyrillic в изменённых файлах проверены: отдельный6-pattern guard,9 файлов.

Незакрыто: motivated NORMAL Giran arrival/paid Giran commerce/return, final restart useful rewards;
native customer BUY/SELL/MFG; OWNER_VISUAL_PENDING. Новый foreground raw-GK arrival/checkpoint contract не подменён
выдуманным anchor или расширением исторической provenance. PARTIAL — разрешённый TASK117 безопасный subset, не принятие A–E.
TASK118 не запускать.
