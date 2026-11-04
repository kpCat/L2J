# TASK121 — living career: PARTIAL_PRODUCT, работа продолжается

Дата: 3–4 ноября 2026. `FULL121` не достигнут. Доставлены узкие source-изменения выбора gear, допуска canonical idle recovery и диагностики travel; native вертикаль целиком не подтверждена. Закрытие TASK121 и следующий TASK122 не объявляются.

## Актуальный срез после native14

Это продолжающаяся TASK121. Ниже сохранены предыдущие срезы с их прежними OPEN/FAILED; текущий факт JVM0/ports0/SQL0 подтверждён postflight14, он не превращает старый FAILED STOP2 в успешный. Полученное позднее разрешение на однократный stock Shutdown PID67120 не переносилось на другие JVM: к этому моменту прежний PID уже завершён через отдельно проверенный STOP-only recovery bridge.

Source `1b49a30e42` ограниченно исправил legacy paid-travel proof при изменении только knowledge hash: остальные три hash и generation должны совпасть; чистый planner использует копию state с текущим knowledge, а leases/raw claim/goal/CAS проверяются по исходному persisted state. После повторного доказательства positive-fee native leg применяется существующий native baseline/STORE и exact CAS replan. Request/seed/from/target/cursor/interval/generation сохранены. Generic transaction classifiers и zero-delta policy не ослаблены. Причина самого изменения knowledge hash не установлена; loader включает также loot-policy fingerprint, поэтому spawn-only причина не заявляется.

Native13 на JAR `05705789d1dccad2884696dd35dd68ffca00ef07aca18a617f6a9d06f8075d9d` восстановил тот же31: READY, catchup RUNNING, revision2→3, World object268481075/epoch565663317595800. После native GK Giran→Gludio фактическая позиция(-12787,122779,-3107), Adena5000000→4967520: GK29000 и реальная offline покупка1061×20@174 за3480. EXP/SP и reward counters не изменились. SELECT после STOP подтвердил прежние request7f47bf67…/seed8038516844009245509/from=cursor29896062/target29896065/interval0/generation6440428012163538279; committedAnchor тогда ещё giran.city.center. Healthy STOP/STORE13/run/postflight exit0.

Прежний human observer268488702 оказался dead/HP0/level1, хотя identity-current был true; locality13 не подтверждена. Его не воскрешали и не исправляли. До CUT14 отдельно подготовлен новый TEST-only human Own121View14/object268481076, class2/level85/fullHP5906, не GM и не invulnerable, source-backed gear/passives, cash0/SP0. Полный digest всех30 phantom identities до/после `6af8f333ef853da34500a524d3f6a0eb00a87ab3fb20c221fee275ebe829bcea` совпал. PREPARED_NOT_EARNED; after-CUT birth/regrant запрещены. Исторический observer13.txt ошибочно содержит preCut=true, хотя CUT13 записан до GameServer; raw сохранён, label не используется как доказательство. Новый observer14 receipt разделяет AFTER_CUT setup и BEFORE_CUT preparation.

Native14 — same-JAR/public-seal restart13 с новым заранее подготовленным human fixture, а не полностью одинаковые условия. Тот же31, epoch567645093344700/revision4; humanLocal=true/censusEligible1, observer жив. Подтверждён durable `PRIVATE_STORE_BUY` operation042614f72eedea4e91ebda2e97a67c4de3b2a0bcbd3c45af16198ca9cecf5d54: B-grade12193 Blue Wolf Gaiters×1, цена960278, seller268488155/stack268488163. Audit COMMITTED/SUCCESS, transferred960278; item сохранён в INVENTORY, экипировка и бой в B пока не доказаны. Cash4967520→3949242 дополнительно включает native GK58000. Seed-funded покупка не считается честным заработком.

Census14 зафиксировал причинный цикл: `travel.arrival_capture_pending` вместе с `supplies.finish_current_target`, session/autoPlay=false, owner OPEN/current, COMMON_GUARDS_CLEAR, no attack/move/cast/party/store/dead и native checkpoint FINALIZED. Actual damage/kill/reward/loot/farm-cycle/EXP/SP=0. После устойчивого witness ожидание прекращено: healthy STOP/STORE14/run exit0; postflight14 PASS, ownSessions0/JVM0/ports0. Inventory SHA95ae8293910ff52d616f5b83bc819899d1a736f19d78536ce6191652468e23a2, offline SHA354c93e17187669b7a96d6906994d0db3c70deb4332f7f73ebdabbd0fd835b2c.

Source `05ee49d490` извлёк только drain уже существующего exact PendingStore для preparation: прежние currentGoal/owner/World/epoch/anchor/Geo/STORE gates сохранены, null anchor fail-closed, новый travel segment/advance не вызывается. Успешный intermediate capture разрешает yield, сохраняя Attempt/start/deadline/counters. Review обнаружил гонку появления pending после первого false read: отдельный focused RED затем GREEN, unconditional recheck и guard до journey-null. Итоговый review P1/P2=0. Focused preparation121 и полный itinerary suite exit0; existing living111/117 и idle112 exit0 (один ошибочный direct-main package дал только ClassNotFound, затем подтверждён правильный package). Ant compile-tests+jar exit0/66s, JAR `ea2d5d4c6686d6914810076865ba30b4379e038d9cf17645c978066e1fe9dfe1`; четыре craft fingerprint classes побайтово неизменны. Эти DB-free tests защищают orchestration, не доказывают actual native STORE/equip.

Promotion `3e4936b5414942918bc881a2de344f02` завершена с fullSHA29122 и control CAS, gameplay/profile/receipt/grant writes0. Source-repair15 на существующем31 без regrant подтвердил **native B-equip**: object268488163/item12193 в gear, `supplies.equipment.owned_equipped`; persisted SQL после здорового STOP/STORE15 содержит PAPERDOLL. Cash3949242 и прежний COMMITTED B-buy не изменились, повторной оплаты не было. Runtime committed position теперь Gludio(-12787,122779,-3107). После этого возник новый устойчивый `maintenance.native.requested` для продажи прежних C-гетр382, вместе с `supplies.finish_current_target`/arrival pending; damage/reward/EXP/SP по-прежнему0. Ожидание прекращено после witness. Run/postflight15 exit0, ownSessions0/JVM0/ports0; inventory SHAef2c91c05c19656ab40c448015c8ff900bde9c325cc0e038399b31823125d8df, offline hash неизменён. Same-JAR restart16 начат для проверки сохранённого B-equip и recovery из фактического Gludio: тот же JAR ea2d… и public input seala6133eb4fb1b31adf69d64b8a06bbb3a63cf8c9018f4aa097f17f260705895a2, без новых fixtures.

Текущий scope13/outsideScope0; mojibake27 и escaped Cyrillic6 по13 файлам —0 matches. B-покупка и B-equip подтверждены отдельно, бой/новые награды в B и полная вертикаль ещё не доказаны. A-grade, earned farm/restock, warehouse и native craft остаются OPEN. TASK121 продолжается.

Независимый pre-CUT32 topology probe не создавал Player/profile и не выдавал предметы. Исправлен диагностический фильтр passive NPC: `isAggressive`, а не aggroRange==0 (у passive XML бывает range1000). Полный перебор дал минимальный NG1835 farm/shop chord30140.34 и D1463 chord53680.42. NG пару нельзя выдавать за D-shop; ElfKnight source speed143.75 и8min supply budget — только preflight estimate, не native route witness. Нового earned32 ещё нет. Stem1864 с reserve160 — MARKET_RESERVE, не легальная NPC liquidation; fixture Stem не будет засчитан как заработок.

## Native11 и текущий source scope13

До CUT11 подготовлен новый profile31/object268481075, Own121GearFreshB, Gladiator2/level54/EXP63907911/SP0, cash5000000, исходная C-экипировка и source-backed расходники. Всё PREPARED_NOT_EARNED. Существующие29 identities сохранили полный digest6489c839…; generation1/ordinal30, canonical late.v3 ACTIVE, SQL readback expertise/items/skills и PRE-CUT READY/COMPLETED proof PASS. Preparation SHA65c79216673f28ba071eb336aafb79d47d66f44e9d22bc0d947f4b17b9c2622b, manifest SHA05d59e3485d4c28346d9b5f727d90753ce8cf3a9c55f01beeb40a7e703a940de. SELECT до CUT нашёл24 реальных B SELL/PACKAGE_SELL lines с owned inventory stock и ценой<5млн; подходящий upgrade этим не гарантирован. Первый stock query ошибочно трактовал offline item как templateId; отказ был до birth, исправлен по stock OfflineTraderTable на objectId JOIN. Старые actors не исправлялись.

Promotion5d0a56a10ca14f60a43a41dd01e36da2 запечатала JAR36b6b4073ede2d226c7d7ea385c5914545448236ed3b9265bdb7a137f8fdd532, roster30/cap8 и public input seal481895d80dd7531329513dc520eb64940733b017d1ffd5ed23290e6882c881cf. Native11 CUT2026-11-04T03:55:26.198780400Z записан до GameServer после gates, start snapshot=точный B31 manifest. Observer setup объявлен AFTER_CUT, phantom fixture writes после CUT0. Review исправил private recorder slot guard:31 watched первым, active/watched обязателен до CUT; TTL120s не гарантирует всё startup-окно. Actual controller predicates до ready BLOCKED, после ready GREEN; foreign/replay/unbound запрещены.

Native11 causal witness flight seq42: profile31, HISTORICAL_TRAVEL/STATE_CONFLICT, requireSimulationContext/sourceLine2693, adenaDelta=-29000, leg.508c6f61d235328d874037cf, goal2727399689550555210/rev2. Затем READY_ECOLOGY_DUE/transaction.state_conflict, World/rewards/gear purchases0. Окно завершено после устойчивого witness: healthy STOP/STORE11, run/postflight exit0, JVM0/listeners0/SQL0. Inventory hash03f8acce… до/после совпал. Checkpoint11 сохраняет FAILED_REPLAN_REQUIRED, request7f47bf67…, cursor29896062/target29896065, interval0 и прежний failureReason; его вручную не меняли. Paid-travel cause теперь доказана для31; прежний read-only probe30 этого не доказывал. FULL121 не достигнут.

Explicit bounded exception: текущий exact public scope **13 файлов** (11 existing+2 new). К прежним12 добавлен только background/PhantomBackgroundService.java для причинно подтверждённого paid travel в той же background/recovery family. Candidate передаёт positive-fee mutated travel existing native handoff до создания paid command. Transaction zero-delta/fee guards сохранены. Для прежнего FAILED/transaction.state_conflict требуется повторно доказать READY+bound COMPLETED context/current raw claim/goal/generation/positive-fee legal leg, отсутствие owned store/offscreen debt и BACKGROUND lease. Legacy probe не вызывает commit, сохраняет FAILED payload/debt/cursor и отказывает VERIFY_PENDING до write-side reconcile. Generic STATE_CONFLICT classifiers не ослаблены.

Native11 — causal RED. Extracted actual old travel gate дал focused RED exit1, затем GREEN; existing112 full planning/load5000 PASS. Review выявил второй P2 до native: RUNNING-only generation helper всегда отвергал FAILED. Реальный extracted composite дал отдельный RED; исправлен только legacy predicate с exact hash/knowledge/topology comparison, общий canResumeGeneration unchanged. Positive/negative focused GREEN. Tests проверяют predicates и codec immutability; runtime leases, SQL writes и materialization ими не симулируются. Native GREEN ещё NOT_RUN; сборка и review следующего candidate продолжаются.

Ранние earned31 preflight attempts остановились до begun/birth/grants: лишнее productive20min условие, неподходящий evening.v3 после02UTC и искусственный12k anchor/shop cutoff. Отказы сохранены. Следующий earned actor только32+, сначала diagnostic count funnel/nearest actual source facts; холодный catalog не доказывает отсутствие native farm. Ни rare drop ожиданий, ни выдачи фиктивной выручки не было.

Ответ владельца о stock Shutdown относится исключительно к прежнему PID67120. К его получению этот PID уже был штатно завершён после доказанного STORE; разрешение не переносилось на новые JVM, первоначальный STOP2 остаётся FAILED.

Source commit c9b565b231: paid native handoff и focused regressions, только3 Java/test paths. Final candidate JAR14e479dd44139315d5c472fc210fa8c20276bc6942bddbe7654f458d1709a931; повторная Ant compile-tests/jar exit0/41s после review fix, OwnerConfig70files/288overrides changed0/conflicts0/rules38PASS. Focused paid/legacy GREEN, existing112 load5000/32 и living111/117 mains exit0; четыре craft fingerprint classes unchanged. Неверное первое имя package для111 остановилось ClassNotFound до тестов; исправлено по actual package economy, реальный main PASS. Scope13/outside0/diffcheck PASS; mixed-EOL guard сначала отказал, затем нормализованы только added hunks3файлов с byte preimages/SHA и normalized-text parity. Mojibake27 и escaped Cyrillic6 проверены раздельно, matches0. Это промежуточный scope snapshot; final exact allowlist будет проверен дважды после итоговых правок отчёта.

Promotion59d69486ca1e4a0ba9e2154d98918997 запечатала source repair JAR14e/controller и полный29115-file manifest, только authority input CAS, actor/receipt/debt writes0. Сцена12 запущена на том же31 без regrant, её readiness связана exact SHA checkpoint11/cut11/flight11/game11.exit0. Это SOURCE_REPAIR, не same-JAR restart11. Будущая13 допускается только после healthy12 с тем же JAR/public input seal. Состояние на данном checkpoint: native12 RUNNING, native GREEN/rewards/gear ещё не подтверждены. Работа TASK121 продолжается, TASK122 не запускалась.

Native12 итог: selected31 снова READY_ECOLOGY_DUE/transaction.state_conflict, World/rewards/gear purchases0; устойчивое окно прекращено обычным STOP, run/postflight12 exit0, healthy STORE, JVM0/listeners0/SQL0. Inventory03f8acce… и offline4d287d… unchanged. Checkpoint12 сохраняет exact FAILED31/request/cursor/interval/reason. Одновременно обновлённый RUNNING27 содержит knowledge hashb5d99f7a3d9317c5e69f55caa7db5dbeec2e740c20dccf14205877ce144259cc, тогда как FAILED31 всё ещё5e05667dd85915c9d7020f69f86927193187a9d46575ad9da6670ac30185f50d; topology/progression/commerce и generation1/1 совпадают. Knowledge drift достаточно для отказа exact legacy guard; native GREEN12 не заявляется. Новый шаг требует текущего positive-fee route proof и штатного canonical/CAS recovery без generic failure reset. Бесполезное повторение12 не запускается. Исходная future13 same-JAR пара пока OPEN.

## Дополнение после source checkpoint7e8f9fb826 (срез до native11)

Последующий срез native6–10 и source checks (работа продолжается, FULL121 не заявлен):

- Native6, JARce85: exact27 восстановлен в Oren; `LOCAL_FARM_UNAVAILABLE`, без новых WALK/rewards. Healthy STOP/STORE6, exit0/postflight6 PASS. Native7, JARc063: exact29, level51, восстановлен на фактической позиции; `NATIVE_RECIPE_DEFERRED_LEVEL`, READY/NOT_REVERIFIED, rv1. SELECT-only сравнение с ledger5 подтвердило неизменные receipt payload/rv1 и book61/62/115, type1/index0: новых grants нет. Native7 healthy STOP/STORE, exit0/postflight PASS. Полного recovery-to-farm этим не доказано.
- Native8, JARbc04: ограниченный visible replan witness установил отсутствие допустимого ingress-кандидата в исходных12k. Source geometry от фактической позиции29: ближайший Gludio arrival —14023.5; это Gludio, не Dion. Native8 без движения/rewards, healthy STOP/STORE/postflight. Повтор того же пустого окна не запускался.
- Scoped commits e2b1d3e1dc/fa01dc0de7: bounded replan diagnostics и interrupted ingress только при известной canonical committed lineage. Existing factual departure имеет приоритет. Проверяются ближайшие16 existing anchors, same instance, canonical proof, contiguous onward route≤64, исключённые steps и доступность всех GK fees. Переиспользованы native local corridor/navigation/budget; нет position/anchor/debt writes. Поиск ingress использует существующий maximumRouteDistance100k, local chord12k и существующие deadlines/route/waypoint ceilings сохранены. Review P2 на фактическую Geo-standpoint длину закрыт guard до создания walk budget. Extracted old helper RED→GREEN, full itinerary/historical-idle exit0, final P1/P2=0.
- Native9 на sealed JAR `74d284f5247a58b06ca5293ecd375a2952968cb283c1d2296ef5f5b5692ba15c`: exact29/object268481370/epoch552794228121700 выполнил18 фактических `VISIBLE_NATIVE_MOVE` от(-1699,114211,-3664) к Gludio, последние visible координаты(-12376,123192,-3104). Затем optional C-gear supply intent item356 к NPC30088/list3008800 Giran получил отказ полного navigation plan `ROUTE_BUDGET_EXCEEDED`, включил WALK fallback и продолжал физическое движение до `supplies.deadline`; до farm/rewards дело не дошло. Cached navigation refusal не был terminal причиной: life9 01:10:45 уже содержит `supplies.native.walking`, moving=true и observed(-12216,123358,-3099); 01:18:39 — observed(-1930,116185,-3614). Committed ingress capture не подтверждён. Финальная фактическая позиция и сменившийся goal сохранены checkpoint9; нет оснований объявлять параллельных native owners. Healthy STOP/STORE9, exit0/postflight9, online0/JVM0/globalSQL0 и protected/Geo parity PASS. A_WORLD/A_RESTART/B_EARNED/B/A gear/C_CRAFT/warehouse остаются OPEN.
- C_OFFLINE сопоставлен полностью: profile27 и29 реально купили у seller268485148 item1061×20 по174, каждый3480 Adena. PRIVATE_STORE_BUY operations указаны ниже. Seller cash100000→103480→106960, stack268485155 count100→80→60; buyer objects268492229 и268491404 по20. Средства PREPARED, не earnings; это Healing Potion, не gear. Persisted состояние и восстановленный seller после restart подтверждены.
- До следующего CUT начата отдельная fresh craft/warehouse fixture30/object268481074, class57/level44/SP0/cash100000, source-backed skills/C gear, recipe115 и BOM. Только WH содержит1464×600; один source-backed surplus1864×161 с reserve160 позволяет native deposit1. Все эти исходные assets — PREPARED_NOT_EARNED. Реальный MFG seller268485259, recipe115, fee27511 подтверждён SELECT до подготовки. Подготовка остановилась на `PREP121_FRESH_SAFE_NATIVE_TOWN` до goal/catchup/ecology enrollment и CUT. Grants не повторялись; one-shot begun и failure log сохранены. Native launch заблокирован до отдельного точного reconciliation/readback/roster seal. Причина town-проверки исследуется, fixture receipt не считается native warehouse/craft.

- Причина private town preparation доказана: `Player.revalidateZone` возвращается без WorldRegion, хотя настоящий Geo floor Giran=-3464, ZoneManager подтверждает TownZone/PeaceZone, water отсутствует, PeaceZoneMode0. PRE_CUT-only helper использовал реальные `ZoneRegion.revalidateZones` callbacks. Однократный no-grant reconciliation30 сравнил74 item rows,31 skills, exact char/book и full raw birth/SQL preimage до Player.load; прежние28 chars/skills/items/profiles/book/components сохранили digest `9caff15eac23b5c78e490daaddc774622cf820cac5738c9e217524283b2c33fc`. Новый raw receipt честно является CURRENT_PRECUT_PIN, не восстановленным исходным binary backup. Reconciliation/SQL readback/postflight exit0, preparation passSHA `c978611cac2fdd58491a44baa5cb287a452b4249e827806d74c2b37c89caa719`, manifestSHA `3ac62329d3ab14dcc633ef35c6d9c69ae95ff2b3906b0e91c784cfd94cbbe1d6`. Native roster29/cap8 запечатан CAS promotion3067be0912f043d6b241bdb41ff65d41; public JAR74d неизменён. Actual controller predicates до ready отвергали старт, после ready разрешили только10, foreign/replay/missingprior отказаны; identity union ровно28+30.
- Native10: exact selected30 календарно ACTIVE, humanLocal=true, но повторно `READY_ECOLOGY_DUE / transaction.state_conflict`, nativeWorld/rewards/warehouse/MFG0. Окно завершено сразу после устойчивого causal witness, штатный STOP/STORE10, exit0/postflight10 PASS; inventory hash до/после `2628d3738792dbe098c961c9a5b04919af8a1a505ef6365c3b14bad3b7c5a0af` совпал. Native ordinary recipe progression reward68/69 — не MFG и не готовый product60. SELECT-only decoded read после STOP: background READY rv3, native context COMPLETED и точно связан со state rv3/hash; catchup FAILED_REPLAN_REQUIRED rv3/cursor29895966/target29895973/interval0, goalrev2 npc20833/DevilsIsle anchor. Cold recovery сам по себе не сломан. Общий `transaction.state_conflict` не переводится в native handoff и не сбрасывается вручную. Первый diagnostic read остановился codeSource guard до JDBC; второй использовал sealed stage authority first и PASS. Точный reject site ещё NOT_PROVEN.
- Source review нашёл отдельный whole-WALK deadline defect: после failed full plan fallback принимал chord≤100k, а затем проверял только очередной короткий checkpoint. Native9 chord95972/remaining475391ms требует минимум speed201.88; actual speed в тогдашнем witness отсутствует, поэтому связь именно9 условная. Новый focused case95k@125 и короткий checkpoint дал actual old-code RED, затем guard до budget creation/admit дал GREEN. Full living111/117 и itinerary mains PASS, Ant compile-tests/jar exit0/50s; candidate JAR `a8e730b5645d7473d5204303ecc806c608023e17a31f43b7e9f44c027a903fb9`. Deadlines/ceilings не увеличены. Native GK leg Gludio→Giran существует, maximum fee29000 при cash4967520; какой physical ingress/egress proof отвергнут — NOT_PROVEN. Этот candidate ещё не запускался native.

Текущий source checkpoint `cc5a2ffcc6`, включая `dac5d72322` whole-WALK deadline и `cc5a2ffcc6` bounded transaction refusal witness. Последний candidate JAR `36b6b4073ede2d226c7d7ea385c5914545448236ed3b9265bdb7a137f8fdd532`, compile-tests/jar exit0/66s, recorder3/3 и career/deadline checks exit0. Четыре craft fingerprint класса побайтово идентичны прежнему460; existing receipt compatibility не менялась. Этот JAR native ещё не запускался; stage пока74d.

Explicit bounded exception: текущий exact public scope **12 файлов** (10 existing+2 new). К прежним11 добавлен только `java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundTransaction.java` для требуемого причинного witness `transaction.state_conflict` внутри той же recovery/background family. Hook после rollback/release IDs в !commitAttempted ветке ничего не меняет в outcome/SQL/CAS/leases; opt-in FlightRecorder содержит status/action/method/AdenaDelta/leg и отдельные numeric sourceLine/goal/revision. Stack search≤8 frames, ring/watch/session/string bounds прежние; длинный leg может быть обрезан reason96. Строка указывает gate, не каждый boolean составного условия. Review P1/P2=0. Scope4306e3d5…/outsideScope0, отдельные27 mojibake и6 escaped patterns —0. Первый diffcheck отказал на22 добавленных CRLF в mixed-EOL Transaction; исправлены только added hunks с byte backup/SHA и normalized_text_identical, следующий scope PASS.

Read-only reconstruction30 после native10 сохранила полный all-managed digest `6489c8390af5f9d876512cd98b6ffeae5f971c65730d155b9b89873813d1badb`; Player.load/commit/GS0. Она **не доказала paid-travel cause**: exactAuthority=false, cold knowledge de241a… против runtime62715b…, остальные3 hashes совпали; advance=NO_ROUTE/fee0/empty edge. Backend читает registered SpawnTable; GameServer SpawnData initialization включает actual NPC spawn, поэтому его не добавляли под claim READ_ONLY. Для следующего native окна нужен actual catalog/refusal witness. Generic STATE_CONFLICT не ослаблен и failed30 вручную не переписан.

Private harness authority обновлена только после healthy STOP10/postflight/fullG0 и old/new inputs+SQL probes: operation `64b448c02f96484d9dcca9387829a4d1`, expiry02:55:24UTC→08:34:22UTC 4ноября. Старый lease был действующим; изменено только expiresUtc через archived owner bytes/SHA file-CAS/atomic replace/readback. SQL_UPDATE/profile/receipt/gameplay deadline writes0, sourceHead3e immutable. Это bounded ownership renewal собственной остановленной TEST121 при разрешённой владельцем длительной работе, не изменение сроков native journeys. GitHub pin7e8 пока сохранён; publication ниже будет обновлена после свежей проверки live HEAD.

Ниже старого среза сохранены failed attempts и первоначальный safety blocker. Его **текущее состояние изменилось**: PID67120 штатно завершён после доказанного native STORE, без hard kill и без обхода leases/CAS. Ранее заданный вопрос об аварийном stock Shutdown утратил необходимость.

- Scoped source commit `80d1f23f0943784e6531efe4abf458515b97780d`: terminal ordinary-death failure проверяется после accepted control/background quiescence и drain остальных actors; FAILED не превращается в healthy stop. В existing103 fixture добавлен `--stop121`. Causal RED old code: `STOP121_TERMINAL_FAILURE_CUT_OFF_ACCEPTED_CONTROL`, exit1; GREEN exit0. Existing103 dispatch3/3, включая закрытие admissions при expired deadline, PASS. Partial actors/DB0/World0 отделены от native доказательства.
- Независимо проверенный own121 global ADMIN STOP bridge `08855c39341144418ab806edc2527b5d`: exact прежние PID/start/Main/UUID/JAR/schema/ACL, immutable FAILED/WAIT_ACCEPTED_CONTROL, original expired deadline, ordinary closing/accepted0/running0, background STOPPING/firstRecoveryFailure empty/full quiescence, exact holder2/object/epoch и one-shot operator lock. Reflection только читала guards. Existing `requestShutdown(originalDeadline)` закрыл roots без нового cleanup dispatch; последующий existing FAILED legacy cleanup выполнил STORE со своим штатным внутренним budget. Original StopProgress/outcome/deadline сохранены; field writes/instrumentation отсутствуют.
- Actual bridge receipt: `healthyReturn=false`, configured=false,retained0; observer cleanup и checkpoint2 выполнены. Затем разрешённый stock Shutdown. Wrapper Native2 terminal exit1, исходные `cleanup2.failed` и `stop2.txt drained=false` сохранены; `game2.exit0` не создан. Checkpoint2 SHA `773e582e002b13c0e1453fc3d0729767450d714a3f820fdbf6b48bd44bd3a5a1`, bridge receipt SHA `c61a6a565d9a624a52a318e3261954a2ddf4f896406278b13322739a80ebd603`, bridge JAR SHA `0a4d5fcd073ffc32dc536bf9ff35a2a0d4622e4d04a3491c786ffdcef4c5b85f`.
- Fresh full G0 и read-only SQL/FinalSQL после bridge PASS: JVM0/listeners0/SQL0, protected1235 drift0, original Geo203 parity. Создан только отдельный `postflight2.recovered.pass`; исходное native окно2 осталось FAILED. Controller допускает только следующий старт с pinned supplemental receipt/checkpoint/log hashes, а не подделанным game2 exit0.
- В controller review найден и исправлен ещё1 P2: FAILED может возникнуть и исчезнуть через legacy cleanup между snapshots. Actual DB-free configured-runtime regression дал RED `FALSE_RETURN_CLEANUP_WAS_MARKED_HEALTHY`, затем GREEN. Каждый stop step читает before/after и сохраняет любой false return как failed; runner отвергает `stopN.failed`, даже если cleanup успешно освободил configured instance.
- Native3 на JAR `d39c34c1338bc0bac3c86f5a7bb5ea0a88727bdfe71a8edd440b26222e5e5a5a`: exact actor26 восстановил HP/MP до554/554 и175/175, затем phaseNONE,rewards0. Goal canonical idle4; catchup всё окно RUNNING,cursor29895735,target29895750,interval0. `permitsDecision=true`, reason `pre_visible.historical_idle_claim`. Healthy STOP/STORE3, exit0 и отдельный postflight3 PASS; original failed2 не переписан.
- Source-proven цикл ожидания: early idle gate требовал COMPLETE, тогда как exact native foreground handoff допускает decisions при RUNNING, а его завершение требует farmCycle. Scoped commit `dc7f7ad0bc03952fd22214e6a61c143b53661a85` допускает RUNNING **только** через existing exactForegroundHandoff; current claim/component/goal/object/epoch/generation/CAS/publication fences сохранены. Focused `--idle-handoff121` RED→GREEN, COMPLETE не вызывает дополнительный owner query, PENDING/FAILED отвергаются; real `withPlan` сохраняет request/from/target/cursor/interval/seed/generation. Full1125000/32 и recovery121 PASS. Source review P1/P2=0; full SQL publication и native reward этим pure test не доказаны.
- Native4 на sealed JAR `460a10e0ba9007cb96d71f37b5d1546ec70d575035065b18dd030e220e69205d` прошёл прежний early gate дальше, но actor26 при full vitals получил `LOCAL_FARM_UNAVAILABLE`, без moves/rewards. Это не доказательство физической непроходимости. Окно остановлено без повторного пустого ожидания; healthy STOP/STORE4, exit0/postflight4 PASS. A_WORLD/A_RESTART остаются OPEN; sameJAR повтор пустого actor26 не запускался.
- Независимая сцена5 на JAR460 завершена: exact prepared actor27 погиб от native aggro, level52→51. Первая native запись HP2744/3038,MP703/771,combat=true; это не доказательство дефекта fixture vitals. Реальные NPC BUY item356×1 за1966800, equip object268492449; SELL старых355/382 за247750/154850. Все три COMMITTED, но356 Full Plate Armor — **C-grade**, а старые вещи PREPARED: B-grade и заработанный доход этим не доказаны. Финальный кошелёк3368320. Остальные native расходы требуют ledger-разбора, разницу кошелька нельзя целиком объявлять GK fees.
- Полный SELECT-only economy ledger5 нашёл реальные PRIVATE_STORE_BUY COMMITTED: profile27 operation2b267f9cf441d50cb22546a2b9d64c6d3a1d8a251648b5077b2a1c9fc690c45e и profile29 bdef0b3d3d543c9be447ef3fc22f02d587973367663fd574f0d363073fa77910, по3480 Adena. Предмет/intent/conservation ещё сопоставляются; это реальные offline операции, не доказательство B/A gear. Только CommerceReceiptStore watcher был недостаточен для полного учёта multiparty effects.
- В travel diagnostics5 получен конкретный native RED: actor27 exact owner/world=true, current82971,53207,-1483,0 против route.destination/arrival82971,53207,-1488,0, native_move_rejected при zero-XY MOVE. Дополнительный witness29: current-12787,122779,-3107 против canonical-3112. Stock PhantomNativeTravelActions.gatekeeper сам ожидает Geo height+5. Scoped commit82e5f29ada принимает radius0 arrival только при exact XY/instance, положительном Zdiff5 и GeoHeight==canonicalZ; никаких position writes, общей Z tolerance или timeout changes. Old predicate focused RED exit1, narrow fix и full itinerary GREEN exit0; review P1/P2=0. Final FARMING usable-position guard не ослаблялся; native GREEN ещё pending.
- STOP5 штатный: drained=true,stopFailed=false,configured=false,retained0; observer удалён, checkpoint записан, stock shutdown завершён, JVM0. Но окно5 **FAILED**, поскольку runtime-error5 содержит native ordinary recipe held29. Game5.exit0/postflight5.pass не созданы. Original runtime error SHA89a6d19298f5d1ef01ea1c2d3818026136d09ab26db40ee5eff1eacbaad0a48f; checkpoint5 SHA8333f29bd72b9502ee4606a5121d0a9fe82ae7de60d2e305177bd017abfd9662. Свежие G0/SELECT-only postflight, online0 и globalSQL0 подтверждены отдельно. Distinct postflight5.runtime-failed.pass разрешает только следующее независимое окно6 с семью exact original hashes, без переименования failed в PASS. Controller guard PASS, review P1/P2=0.
- Ledger доказал receipt29 ordinary.craft.recipe.v1.0.6,rv1,READY/PROGRESSION_RECIPE_BONUS,class57,index0,level52,ranks6/6,recipes61/62. Текущий native level51,skills172:6/1320:6; book61/62/115,type1/index0. PRE_CUT115 отличается от native ordinary rewards61/62. Узкий fail-closed defer в PhantomOrdinaryCraftRecipes сохраняет source/READY/object/account/class/index/ranks guards, не меняет receipt/book/quota/_seen и пишет bounded NOT_REVERIFIED event. Existing verifier/cache policy применяется на допустимом уровне; повторный verifier в том же JVM не гарантируется существующим _seen. PhantomNativeCraftRecipes.class не изменён: его self-bytecode fingerprint иначе инвалидировал бы receipts. Actual sameDB admission/readback regression ещё pending.
- Latest candidate JAR ce85f57d16e9858817367355a731033ed908b29febd8364aaee292074f9d7014; compile-tests/jar и focused landing/craft101/recovery/idle-handoff/stop/equipment checks exit0. Promotion e8a59b5ad0c1443f8eb330fc8c40c0f6 выполнил только control metadata CAS и остановился до GS на старом exact runner JAR pin460. Исходный FAILED input-gate сохранён; helper-only correction на exact ce85 проходит отдельное полное seal/validation, без gameplay/receipt/DB updates. Это ошибка private orchestration, не native acceptance.

На этапе native6 exact public scope был11 файлов, bounded exception для причинно связанных native failures: прежние7, PhantomSystem.java, PhantomShutdown103Checks.java, PhantomVisibleItinerary068Checks.java и PhantomOrdinaryCraftRecipes.java. Это9 existing+2 new artifacts; unrelated subsystems/configs не менялись. Source commits80d1/dc7/82e тогда были локальными; live GitHub pin7e8f9fb826 сохранялся для G0. Перед native6 scope11/outsideScope0 и отдельные27 mojibake/6 escaped patterns дали0 matches. Текущий12-file scope указан выше; финальные scope/encoding/publication receipts будут обновлены после следующих сцен.

Диагностические отказы продолжения сохранены: первый ant compile без внешнего sandbox write-доступа остановлен OwnerConfig conflict, elevated orchestration повтор с теми же overrides дал changed0/conflicts0. Первый G0 вызван из PowerShell7 и остановился до JDBC (контракт требует5.1); штатный WindowsPowerShell5.1 PASS. Первый ручной SQL postflight остановлен exact cwd guard до JDBC; корректный stage/dist/game invocation PASS. Никакой из этих отказов не обходился снятием guards.

## Сохранённый первоначальный срез7e8f9fb826

## Текущий safety blocker

В собственной TEST121 штатный STOP окна2 завершился FAILED до `DRAIN_PLAYERS` из-за sticky ошибки ordinary death recovery профиля26. Последующий отдельно проверенный ADMIN STOP-only запрос также вернул false. Stock Shutdown, hard kill, field patches, освобождение leases вручную и подмена receipts не выполнялись.

Read-only ADMIN snapshot установил оставшийся holder: **profile2 / object268484956 / epoch541153814445700**, ACTIVE, World present, identity lease retained, cleanupPhase NONE, action admission OPEN. `nativeOwnerOutstanding=0`, `nativeOwnerPendingTimers=1`, owner OPEN, permanentSeal=false; `failedCleanupReady=false`. Это другой actor, не погибший26. Тип единственного timer этим snapshot не установлен.

Существующая FAILED-ветвь `PhantomSystem.shutdownClaimed` допускает legacy cleanup лишь после `failedCleanupReady()`. Ordinary death failure возникает раньше нормального закрытия native roots остальных actors. Получен причинный stop witness, но безопасная перестановка lifecycle-фаз с focused regression в этот source-набор не включена. Зависимый native restart не запускался.

Пользователю отправлен safety choice: оставить JVM для ручного разбора либо разрешить штатный stock Shutdown только TEST121 с сохранённым FAIL и риском неполного STORE. Пока разрешения нет, второй вариант не выполняется. Это реальный safety conflict обязательных STOP/STORE/no-force-fallback контрактов, а не лимит времени или количества исправлений.

**Exit G0 НЕ PASS:** own JVM PID67120 остаётся; JVM0/listeners0/SQL0 и clean STOP/STORE окна2 не доказаны. Никакая строка ниже не заменяет этот gate.

## Baseline, isolation и read-first

- GitHub `kpCat/L2J`, ветка публикации `feature/phantom-world`, ожидаемый и проверенный baseline `3e340e45c4c2aed42b7eca5202440b0a8817901d`.
- Source commits: gear `9c9788a1f674c741c844616294265a26810b79c9`; recovery/diagnostics `af93083ea8519a3acef5d3b141aa724d27683d1d`. Source review P1/P2=0. Оба опубликованы ordinary non-force push, live remote HEADaf93083e подтверждён; отчёт публикуется отдельным scoped commit.
- Защищённый рабочий checkout: `D:\Codex\repos\L2J-phantom-world`, локальный HEAD `236b965a116bbc79cccbef78b04c628225a629cb` и существующий dirty WIP109. Не использован как baseline source и не изменялся.
- Собственная физическая source-копия: `D:\Codex\worktrees\phantom-living-career121-source\L2J-phantom-world`, detached descendant baseline3e. Native app worktree creation не смог найти этот ref в защищённом checkout; применён no-hardlinks clone от read-only source120 с exact checkout3e и sparse HighFive. Чужой source120 не менялся.
- Собственный stage: `%LOCALAPPDATA%\Temp\Phantom-Native-Life-121-a078ca51-5673-47c4-9ced-c69e534c87e9`; owner `TASK121_NATIVE_LIFE`, UUID `a078ca51-5673-47c4-9ced-c69e534c87e9`, owner+SYSTEM private ACL, physical/disjoint paths, без reparse/hardlink reuse Geo.
- Единственная новая схема: `l2jmobiush5_phantom_test_121`, существующий MariaDB endpoint3308; configured GameServer endpoint только127.0.0.1:17781. `new GameServer()` создаёт native World в headless helper, полный `GameServer.main`/LocalRuntimeShutdown transport не вызывался. LoginServer и клиент не запускались.
- OS attestation: MariaDB service, PID8692, `C:\Program Files\MariaDB 11.4\bin\mysqld.exe`, version11.4.13; runtime image через `PrivilegedReceipt/QueryFullProcessImageNameW`. Перед CREATE/GS проверены SQL identity/datadir/server id, metadata visibility/grants, global quiescence и отсутствие посторонних JVM/listeners/sessions. Сервис/конфигурация общей MariaDB не менялись.
- CREATE121 выполнен один раз: 121 stock SQL files, 203 statements, 18 DROP исключены, foreign writes0. Повторного bootstrap/reset/import существующей схемы не было.
- Защищённый manifest1235 неоднократно проверен: drift0. Это parity именно объявленного manifest, не утверждение о полном побайтовом сканировании всех чужих файлов.
- Original Geo203, 1,063,452,308 bytes: отдельная физическая копия и SHA parity; originals не менялись. Защищённые release057–061, TEST109–120, MAIN/PLAY и чужие worktrees не запускались и не менялись.

Прочитаны все8 файлов пакета121: TASK, GOAL, HANDOFF, SOURCE_MAP, PRODUCT_DECISIONS, SCENARIOS, ACCEPTANCE, EVIDENCE; root/module AGENTS, current status, workflow/efficiency и обязательные релевантные master-plan/package/release contracts. Initial READ_SET —13 документов по SOURCE_MAP; далее bounded symbol/range reads. Пакет121 читался из рабочего checkout; его отсутствие в sparse baseline-копии не обходилось добавлением/переписыванием task package.

Локальные аналоги: recovery admission в `PhantomBackgroundService.recoveryGoalCurrent` и `recoverOwned`; `PhantomEarnedMarket119Checks`/`PhantomOfflineSupply.AcquisitionQuote`; `PhantomRuntimeFlightRecorder`/его suite. Для TEST-only setup переиспользованы private119 helpers, stock headless infrastructure и canonical Player/skill/recipe APIs, с новыми121 namespace/UUID/guards. Старые raw119/120 были только указателями, не результатами121.

Построены три причинные карты: A — VisibleFarmTravel→historical goal/recovery→materialization/stop; B — LootDisposition→maintenance→Commerce/warehouse/offline BUY; C — need→NPC/offline SELL/MFG→quote/reserves→native receipt/equip. Существующие owners и sole paid writers сохранены; новый AI, scheduler, durable writer или слой providers не добавлены.

JDK25/Ant; public population5000/visible128 сохранены. Только private TEST overrides: сначала24, затем28 identities, active8/materialized8, diagnosticsTrue и headless GUIFalse. Public OwnerConfig, Rates, Merchant/Market/Geo/life data не менялись. Adena item57 amount5/chance1 сохранены; публичные DeathDrop/Spoil rates не менялись. Никакого forced Giran, увеличения deadline или лимита600 ради PASS.

## Изменённые public artifacts — exact allowlist7

Все пути ниже относительно `L2J_Mobius_CT_2.6_HighFive/`.

| Путь | Изменение и предел доказательства |
|---|---|
| `java/org/l2jmobius/gameserver/phantoms/commerce/PhantomOfflineSupply.java` | Package-private equipment comparator использует существующий delivered unit cost, recipe material/risk и distance tie. |
| `java/org/l2jmobius/gameserver/phantoms/commerce/L2jPhantomSupplyJourney.java` | Offline gear candidate рассматривается и при доступном NPC; сравнение с NPC price+round-trip fees и fresh authoritative quote. Owned gear priority, reserves, leases, payment/CAS owners сохранены. |
| `java/org/l2jmobius/gameserver/phantoms/background/PhantomHistoricalBackgroundService.java` | Три recovery admission gates используют existing exact-current contract вместо farm-only; ACTIVE и canonical idle validation сохранены. |
| `java/org/l2jmobius/gameserver/phantoms/background/PhantomVisibleFarmTravel.java` | Opt-in bounded retry events до clearRoute: current/issued/actual movement, route/arrival, owner boolean, goal/revision/epoch/request/timing. Compact XYZI и captured issued Z/instance. Движение/отмена/deadline/retry policy не менялись. |
| `test/java/org/l2jmobius/gameserver/phantoms/commerce/PhantomCareer121Checks.java` | Новый focused main: три synthetic cost contrast groups. Не native stock/payment/equip test. |
| `test/java/org/l2jmobius/gameserver/phantoms/background/PhantomLivingWorld112Checks.java` | Existing main получил focused `--recovery121`: canonical idle/farm, foreign/null/terminal и same-profile changed revision refusal. |
| `docs/phantoms/reports/PHANTOM-LIVING-CAREER-121.md` | Этот отчёт. |

Итого: 5 существующих файлов изменены, 2 новых; public config/datapack/SQL/build/infrastructure files не изменены. Private scripts/raw/DB/credentials/JAR не входят в commit. Scope меньше8–10 файлов; расширение на stop subsystem без regression не включено.

## Причинные RED→GREEN и review

**Gear:** старый gate рассматривал offline gear только при отсутствии NPC. Synthetic comparator RED на более дешёвом допустимом offline предложении при NPC наличии; GREEN после comparator fix. Production wiring проверен чтением и независимым diff review, не synthetic execution полного equipmentProposal. Проверены fees, material basis,60% risk, distance ties и absent quote. Это source/policy доказательство; native offline paid upgrade не доказан.

**Recovery:** native окно2 воспроизвело `recovery.control_baseline:catchup.native_recovery.goal_mismatch` для canonical historical idle. `recoverOwned` уже поддерживал такой idle, а три historical admission gates допускали только farm. Focused RED отверг valid idle; GREEN переиспользует `recoveryGoalCurrent`, включая exact equality, ACTIVE и visiblePredecessor. Claim status/rowVersion/payload, generation, saved component и object/epoch fences не ослаблены. Native GREEN нового JAR пока NOT_RUN.

**Travel:** причина STUCK120 не установлена, STUCK fixed не заявляется. Первоначальные route.toString() в retry diagnostics могли обрезаться лимитом48; заменены compact XYZI (максимум47 символов для4 int). Три события не атомарны, owner представлен boolean без полного token/evidence identity; recorder expiry/ring overflow могут терять события. Это подготовка witness, не доказательство физической непроходимости или recovery.

Независимый reviewer проверил exact diff6 source/test files: P1/P2=0. Отдельно проверены stop-only helper и read-only snapshot helper. В последнем reviewer обнаружил P2/API ошибку вызова diagnosticScalars у интерфейса Owner **до compilation/execution**; исправлено через existing `instanceof PhantomNativeWorkScope` аналог, compilation PASS. Review не засчитывается как native acceptance.

Private restart acceptance helper также прошёл RED→GREEN: phase после restart больше не может выбрать другого actor вместо exact preferred identity. Этот helper test не доказывает состоявшийся restart.

Review отчёта выявил1 P2: слишком широкий B_RESERVES/POLICY PASS из budget tests. Claim исправлен на OPEN/NOT_PROVEN; успешные source/DB-free checks не переносятся на неподтверждённые native gates.

## TEST fixtures и CUT provenance

Перед CUT подготовлены четыре реальные Player/Phantom identities. Все уровни, skills, начальные предметы, recipes, деньги и позиции — **PREPARED_NOT_EARNED**; grants/позиционирование не объявляются добычей, SP-paid learning или WALK.

| Profile/object | Назначение | Class/level | EXP/SP при fixture preparation | Seed cash | Legal initial anchor XYZ |
|---|---|---|---|---|---|
|26/268480990|Earn|1/20|835862/0|0|`generated.farm.187426bb30110951b41e0ba9.anchor`, -46177,210132,-5080|
|27/268481180|B gear|2/52|51262490/0|5,000,000|`generated.farm.4193b9085223359f9148e155.anchor`,56877,170300,-4976|
|28/268481770|A mage|12/61|146308200/0|15,000,000|`generated.farm.2d848c4c64b764a5c6703a13.anchor`,66628,111182,-3688|
|29/268481370|Dwarf craft|57/52|51262490/0|5,000,000|anchor как27|

Dwarf подготовлен с official recipe115/scroll2222/product60/rank6/100%, реальным MFG skill и BOM `{1459=95,1887=7,1890=21,1892=4,1977=10,2131=48}`. Recipe/BOM подготовлены, native craft outcome не заявляется. Initial gear брали из real NPC buy offers и official RecipeData products; это provenance разрешённых fixture assets, не market stock или native purchase.

Fixture attempts сохранены, не скрыты:

1. Authority drift до создания fixtures: population initialization hash использует timezone id `UTC`, не ZoneOffset id `Z`. Исправлен helper, не production timezone/config.
2. Создан26, planner target/route absent; enrollment ещё не выполнен. Не восстановленные vitals перед planning исправлены до CUT; разрешены bounded authoritative anchors, все placements помечены PREPARED.
3.26/27 завершены;28 остановился на отсутствии подходящего source-backed B weapon в первоначально слишком узком NPC-only setup. Повторных grants завершённым identities не было.
4. Точный empty partial28 продолжен через official recipe product catalog,29 подготовлен; все4 сохранены/readback. Hash старых retained rows до/после continuation совпал: `61047f78e9906874d0230c55487d87e6fb27831a2e9ce9c8ea7fa3758960550a`.

После каждого принятого CUT не было grants, reposition Phantom, fake kills, direct gameplay calls, state/debt reset или receipt patches. Follow/catchup телепортировал только маркированного non-GM observer; это не Phantom travel evidence.

Ограничения setup: из-за промежутка между enrollment26 и первым native observation исторический catchup уже изменил EXP/SP/loot/HP. Поэтому fixture EXP835862→первое native842999/SP545 и появившийся до observation loot НЕ EARNED_NATIVE окна2. На первом observed CUT cash0. Следующая попытка должна фиксировать этот baseline своевременно, без сброса debts.

Warehouse pressure setup содержал ограничение128 slots при native limit250, то есть не гарантировал порог80%; несколько grade shot stacks также не гарантировали shortage для withdraw. Pre-cut bank transfer item22083×600 сохранён как FIXTURE, не warehouse outcome. После CUT эти условия не «докручивались». Для S4 потребуется отдельный корректный pre-cut fixture, после разрешённого safety recovery; текущий S4 не принят.

## Native окна и экономический ledger

**Окно1, baseline JAR:** 24 managed identities; наблюдения UTC21:01:35–21:08:28, sampled elapsed max472697ms. Selected NORMAL2/object268484956; у него no rewarded kills, no movement STUCK witness. Другой profile16 имел реальный native kill/reward sequence1, EXP87→174/SP10→20. Это baseline native reward, не доказательство recovery выбранного actor или новой policy. STOP/STORE1, exit0 и postflight1 PASS; identities preserved24. Final SQL snapshot1:224 adena stacks,total2,520,125,630; значительная merchant provision, не earnings.

**Окно2, candidate e3a7:** same DB, другой JAR и4 новых fixtures, поэтому не qualifies sameJAR restart. Selected26; наблюдения UTC21:53:10–22:03:04, sampled elapsed max649782ms. Native damage/kill/reward sequences26 все0. Он был атакован NPC и погиб; EXP842999→828670, level20→19, SP545 и cash0. Canonical native recovery перенёс его в Gludin(-83520,150560,-3120), затем admission вернул goal_mismatch. Recovery26 полезного farm не доказал; sticky failure привёл к stop blocker выше.

Параллельно profile2 прошёл реальные paid NPC операции через existing Commerce:

|UTC/operationKey|Операция|Cash before→after|Native facts|
|---|---|---|---|
|21:55:43,`7e8543a61cda54ae18ef872eabf1e395cea910d84ba79cc278cef1922678ba83`|BUY item1835×332,amount2656,NPC30561/list3056100|5235→2579|COMMITTED,rowVersion2,count332|
|22:01:05,`d6723616df7cef8f61c5b2db839d97a03040368ef7cf1049076102b1f72b6e73`|BUY item253×1,amount678,NPC30558/list3055800|2579→1901|COMMITTED,rowVersion5; equipped object268521468/item253 в live inventory|

|Actor/window|EARNED_NATIVE cash|REALIZED_NPC|REALIZED_OFFLINE|SPENT_PURCHASE|SPENT_SHOTS|FEES|STOCK_COST_BASIS / FIXTURE_ASSET|
|---|---|---|---|---|---|---|---|
|26/window2|0 подтверждено|0|0|0 зафиксировано|0 зафиксировано|0 зафиксировано|Подготовленный D stock/gear; background loot до observation исключён|
|2/window2|0 подтверждено|0|0|2656 shots+678 gear=3334|Native consumption cost после покупки не доказан|В этих2 receipts0; полный transport ledger не доказан|332 paid shots cost2656,gear678; source cash5235,не earned-only|
|27/28/29|NOT_MEASURED|NOT_RUN|NOT_RUN|NOT_RUN|NOT_RUN|NOT_RUN|Seed cash25,000,000 суммарно,gear/recipe/BOM/warehouse stock PREPARED|

`5235-2656-678=1901` подтверждается recorded ConservationFacts и live wallet. После этих покупок profile2 native kill/reward sequences0. Не называю это earned refill, прибыльным циклом, B/A upgrade или полноценным restart durability. Liquidation estimate/остаточная стоимость всего stock не вычислены без свежих подтверждённых quotes; отсутствующие числа не заменены нулём прибыли.

Открытые200 native merchants и их provisioned capital — finite bootstrap, не paid offline trade. Для121 не найден captured COMMITTED player↔offline merchant/MFG receipt. Не выдаю отсутствие такой receipt за NO_PROFITABLE_OFFER для всех подготовленных mid-grade сцен: эти сцены ещё не запускались.

## Acceptance matrix

|Gate|Статус|
|---|---|
|G0 before CREATE/GS|PASS на выполненных стартах; final exit G0 BLOCKED|
|A_POLICY STUCK/native owner|OPEN: retry diagnostics доставлены; физическая причина STUCK не доказана|
|Canonical idle recovery admission|SOURCE_GREEN; native reproduction RED, исправленный native GREEN NOT_RUN|
|A_WORLD / A_RESTART|NOT_PASSED / BLOCKED; квалифицированных sameDB/sameJAR restart0|
|B_EARNED|NOT_PASSED; baseline другой actor reward1 и window2 NPC purchases не составляют closed earned cycle|
|B_RESERVES / warehouse|Native gate OPEN / NOT_PROVEN; existing loot category/surplus smoke PASS отдельно:275 D/C parts и6 contrasts; deposit/withdraw NOT_RUN|
|C_GEAR|SOURCE selector GREEN; native B/A paid equip+charged reward NOT_RUN|
|C_OFFLINE|NOT_PROVEN; OPEN merchants не COMMITTED player trade|
|C_CRAFT|PREPARED dwarf/recipe/BOM; native craft/restart outcome NOT_RUN|
|D scale/lifecycle|DB-free idle5000 PASS; actual128 visible/load/complete stop2 NOT_PROVEN|
|Owner visual QA|PENDING; human client login отсутствовал|
|FULL121|NO; PARTIAL_PRODUCT с реально доставленными source-модулями|

Независимые source/negative/diagnostic milestones продолжались при native blocker. Остальные native сцены требуют того же полного G0 и только одной owned121 schema; запуск их поверх незавершённого holder нарушил бы safety contract. Другую TEST DB в обход blocker не создавали.

## Проверки и фактические отказы

Приведённые команды сборки и DB-free проверок выполнялись из собственной source-копии; Ant последовательно. Native/helper commands выполнялись в private stage. Общий `ant verify` и старые native DB suites не запускались вместо targeted gate.

```powershell
ant -f .\L2J_Mobius_CT_2.6_HighFive\build.xml compile-tests
java -Dphantom.module.root=<source-module> -Dphantom.test.reports=<own-evidence> -cp <build/bin;build/phantom-test/bin;dist/libs/*> org.l2jmobius.tests.phantoms.PhantomTestLauncher life-scenarios116 121121
# Аналогично supply071 и post001-recipe-cost с seed121121.
java -cp <same-classpath> org.l2jmobius.tests.phantoms.PhantomRuntimeFlightRecorderSuite
java -cp <same-classpath> org.l2jmobius.gameserver.phantoms.background.PhantomLivingWorld112Checks --recovery121
java -cp <same-classpath> org.l2jmobius.gameserver.phantoms.background.PhantomLivingWorld112Checks
java -cp <same-classpath> org.l2jmobius.gameserver.phantoms.commerce.PhantomCareer121Checks
java -cp <same-classpath> org.l2jmobius.gameserver.phantoms.commerce.PhantomEarnedMarket119Checks
# Из source-module/dist/game, без GameServer/DatabaseFactory init:
java -cp <same-classpath> org.l2jmobius.tests.phantoms.PhantomLootDisposition068Checks
ant -f .\L2J_Mobius_CT_2.6_HighFive\build.xml jar
```

Exact expanded orchestration сохранена в private `Verify121.ps1`, результаты `final-*.log`. Recorder direct main запускался из module directory. Его suite отсутствует в mapping PhantomTestLauncher: первоначальный guessed `runtime-flight-recorder` mode дал exit2; исправлено на существующий direct main,3/3 PASS, уже прошедшие suite не повторялись.

|Проверка|Результат|
|---|---|
|Final compile-tests|exit0,28s,5 прежних warnings (ExtendedOpenOption×3,runFinalization×2)|
|life-scenarios116|exit0,32/32|
|supply071|exit0,9/9|
|post001-recipe-cost|exit0,4/4|
|Flight recorder direct|exit0,3/3|
|Recovery121 admission|exit0, focused group; DB0/actors0|
|Idle112 full|exit0;5000×121min:605000 planning calls,10083 probes,threadDelta0;32×1441min:46112 calls,768 probes|
|Equipment121|exit0,3 synthetic groups|
|Market119 selector|exit0,5 synthetic contrasts|
|LootDisposition068 existing smoke|exit0; actual1000 recipe catalog,275 D/C key parts не SELL_NPC,6 valuable/cheap/shared/full-bank/unknown contrasts; category/surplus policy, не native warehouse transaction|
|Final jar|exit0,20s после EOL-only repair|
|Native1/postflight1|exit0/PASS|
|Native2 cleanup|FAILED `DRAIN_BLOCKED_NO_FORCED_KILL`; wrapper ещё ждёт JVM|
|Separate ADMIN STOP-only|exit1,agent loaded,`DRAIN_REMAINS_BLOCKED`; stock Shutdown not called|
|Read-only ADMIN holder snapshot|exit0; exact holder2,one pending timer|
|Protected1235|drift0; read-only повторная проверка|
|Final STOP/JVM0/listeners0/SQL0|BLOCKED, не выдан PASS|

Первый diff whitespace check выявил CRLF на добавленных строках mixed-EOL files. Исправлены только добавленные строки, старые EOL сохранены; outside-checkout preimages и normalized-text equality проверены. Semantic source не менялся, затем выполнен jar build. Whitespace checks и постоянный Git config не менялись.

## SHA и raw provenance

|Артефакт|SHA256|
|---|---|
|Baseline native1 GameServer.jar|`7c3738c14c21bd9db01e4d1c3353f736b72fea345a826b37e31b5d2c05ee0f94`|
|Active sealed native2 GameServer.jar|`e3a7259b3e9047a369d7ea330b541a386211cce0614519ff550327b15a793ba2`|
|Final source GameServer.jar, NOT_PROMOTED|`710a0955dd926bc8bc40ce98a50f1ea8e2714052cf7744e590c0920432d91c4b`|
|Native2 full input manifest|`8d8ebf04c9aaab1627751c8757d3c83680d286e53dcd6971f26684dc008561f2`|
|Public input seal owner field|`5738bf3fb74f85427a95334e066524e8fa2639bc2693a8f958dfe4bbca45cd5a`|
|Native2 owner.properties|`b882c1e2c9bf02be494eaf1e365f6d0b9e8ef5410c6ab187bfc4c0a39975ad72`|
|fixtures121.complete.tsv|`6afa7c4b5d6fb055e7b185f0d5128b2ca61bc0f816c82e6990c7aa2ccafb9329`|
|Stop-only agent JAR|`7f9b3283d354d675bd83652b546df9452eb22d7b8c2e32e61f19c14832ae68e4`|
|Read-only snapshot agent JAR|`7dee6836f3dda622419b742e28a12777f79d84b340b4fdaef224640567b8bc9f`|

Full manifests содержат per-file config/data/Geo/helpers hashes, а не только JAR. Без нового G0/promotion final source JAR не подменял работающий sealed e3a7.

Private raw: `%LOCALAPPDATA%\Temp\phantom-living-career-121` (plans, helper sources, build/test/RED/GREEN/reviews/summary/repair receipts); stage `private` (authority/seals, fixture attempts/manifests, samples/inventory/skills/census/life/flight/payments/resources, stop snapshots). До повторного prep старый log архивировался и SHA сверялся. Stage/public seal promotion использовал immutable backups, fail-closed pending и single-row own121 authority input-hash CAS; game/profile/receipt rows этим CAS не патчились. Native/input helper reseal не считался игровым событием.

ADMIN STOP op `e783efa03b06408ba7ad4cd9ad4fbe6d`; read-only op `f78180bb6c0a40f084117612c7f63444`. Instrumentation/redefine/retransform/field mutation отсутствуют. Оба helpers — отдельные121 копии existing operator pattern, не startup/fault agent и не носители игровой функции.

## Затраты, Git и оставшаяся работа

Native starts2, повторный sameDB start1 с changedJAR; qualified sameJAR restart0; fixture preparation invocations4. Summed sampled native elapsed18.71min, без startup/shutdown и времени оставшегося FAILED JVM. Heap peaks window1=4,052,964,472 bytes /136 threads; window2=4,057,434,936 /132. Это samples, не профилирование и не доказательство production capacity.

Login9014 reconnect noise: для headless окна2 ровно135 connect +135 unavailable строк в captured prefix до main finish failure. После failure живой JVM продолжает писать noise; он не вырезался из raw и не включён в это фиксированное число. Window1 работал с GUI, private wrapper console не покрывает весь GameServer log; число его retry не заявляется0.

На cost snapshot перед source publication goal metadata показывала976973 tokens и6576s (следующая clock receipt UTC22:35:54). Это промежуточная фактическая usage, не заданный лимит; денежная стоимость через tools не предоставлена. Дополнительные source/fixture/authority/object-store проверки увеличили расход. Полный счётчик distinct прочитанных файлов и rg invocations не велся, точное число не выдумывается. Initial READ_SET13; public changes7, новые public artifacts2. Много времени ушло на защиту authority/fixtures и разбор отказов; это не native farm soak и не прокачка1→52.

Git использовался по bounded root/module разрешению для baseline/scope и по явному TASK121 разрешению для собственных source commits/ordinary push. Рабочий root read-only; ветки master/default/experiment не изменены. Доставка только descendant baseline3e в `feature/phantom-world`, без force/history rewrite. Ниже перечислены формы выполненных команд; exact пути приведены в allowlist и isolation выше:

- Read-only: `git rev-parse HEAD`, `git rev-parse --is-shallow-repository`, `git symbolic-ref`, `git status --short`, `git status --porcelain=v1 -uall`, `git worktree list`, `git for-each-ref`, `git ls-tree -r --name-only HEAD .idea`, `git ls-tree -r -l --full-tree HEAD`, `git ls-remote https://github.com/kpCat/L2J.git refs/heads/feature/phantom-world`.
- Exact diffs: `git diff -- <exact source paths>`, `git diff --numstat -- <exact source paths>`, `git diff --unified=0 -- <one exact source path>`, `git diff --check -- <allowlist>`, `git diff --cached --check -- <commit paths>`.
- Own clone setup: `git clone --no-hardlinks --single-branch --no-checkout <source120> <source121>`, own sparse-checkout HighFive и detached checkout exact3e. Root/foreign refs при этом не писались.
- Own object-store repair: первый commit выявил absent `.idea` blobs; bounded current tree inventory нашёл554 missing blob references. Один exact blob получен GitHub `fetch_blob`, ещё99 raw и426 normalized immutable blobs восстановлены из read-only root files **только при совпадении ожидаемого Git blob SHA**. Использован `git hash-object -w --stdin` только в clone121; реальные root/WIP bytes и tree не менялись. Повторяющиеся references разделяют object; это не554 разных записанных файлов. Обхода/удаления `.idea` или Account_Manager не было. Commit failures сохранены; после repair missing0.
- Автоматическая Git maintenance при двух commits отдельно сообщила `unable to read 39c24010ebe80ca7df56e40e304c8be1214d736d` / geometric repack failed. Commits завершились exit0; historical clone completeness не объявляется PASS. Репозиторная история и чужие object stores не исправлялись. Для оставшегося report commit auto maintenance подавляется только command-scoped настройками, без постоянного config change; whitespace/safety checks не отключаются.
- Scoped staging/commits: `git add -- <exact three gear paths>`, `git commit -m 'TASK121 compare offline equipment with delivered NPC cost' -- <same three paths>`; аналогично exact3 recovery/diagnostic paths с message `TASK121 accept canonical idle recovery and capture travel retries`; report отдельно. `add .`, Stage All, clean/reset/stash/force/restore не использовались.
- Source publication: `git push https://github.com/kpCat/L2J.git HEAD:refs/heads/feature/phantom-world`, exit0; remote3e→af93083e подтверждён последующим `git ls-remote`. Before-push exact scope guard7 PASS, outsideScope0.

- mojibake-маркеры в изменённых файлах проверены:27 markers,7 файлов,0 matches.
- escaped Cyrillic в изменённых файлах проверены:6 patterns,7 файлов,0 matches.

Final exact allowlist receipts сохраняются в private evidence; полный baseline/current tree inventory проверяет отсутствие committed paths вне allowlist, кроме проверки current dirt. Из-за native safety blocker успешная source publication не означает FULL121 или final G0 PASS.

Продолжение только TASK121: решить safe stop holder2 без снятия fences, затем новый full G0, promotion final exact JAR и targeted recovery/independent B/C/D scenes с correct pre-cut fixtures; для sameJAR restart нужен тот же exact actor и новые rewards. Native stop fix сначала требует focused lifecycle regression. TASK122 не запускать. Owner visual gate остаётся pending.
