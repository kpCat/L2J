# RESULT_HANDOFF034

**TASK034=PARTIAL_VERIFIED. Repair=BLOCKED_DATA. M1=OPEN; REAL_FINAL=NOT_RUN.**

Первый причинный guard установлен и воспроизведён на actual GameServer до
Synthetic: profiles36/45 имеют native inventory252 при limit250. Их native work
уже завершён, но owned store отклоняет эти facts. Без безопасного FINALIZED
нельзя удалять Player, снимать lease или объявлять global drain успешным.
Разрешённого product fix с сохранением этого контракта не доказано. Production
и test source не изменялись; нового production Jar нет.

Required checkout/parent: `44139ca556ad3d5ac35309d5fe222269d3ada256`.
Исполненный R3 source: `51cf051b3d20fbb83e6c0e16ce8c7bcad7af52cb`.
Оба baseline runtime исполняли GameServer SHA-256
`EBC6918A38D634964028A171AFF62CB24926B5E7432040EB50B3A5E5CD620D46`.
LoginServer SHA-256
`5A1C686A7E63FFB99E1A75DFDBDE261D7D204F187EB484FB6CD757C72EC830AF`.
Task034 Java/build source hashes сохранены в private `FINAL034/SOURCE034_SHA256.tsv`;
exact-path production diff пуст. Report commit меняет только packet/report files.
Exact publication commit/local/remote SHA записывается после normal push в
external `PUBLICATION034.json` и финальном сообщении чата; это не новый Jar SHA.

## Исторический retained3: разные lifetimes и разные границы доказательств

Внешний033R raw использован только после полной SHA проверки: 25 metadata,
14698 raw и316 support rows. PID23688 — R3 GameServer. Таблица относится к
точным native lifetimes этого PID, а не к совпадению числа retained.

| Profile / objectId / epoch | firstObservedAt / pre-Synthetic | First exception / phase / hook | Latest captured exception | Pending native owner | World / AutoSave / lease | Before → after; причина; fix/test |
| --- | --- | --- | --- | --- | --- | --- |
| 36 /268484533 /403904657047000 | registerNanos403904714447200; YES | 2026-10-10T23:42:12.995217500Z; IllegalArgumentException: Invalid bounded background inventory.; NATIVE_STORE /PLAYER_STORE_ME; seq1 | Same seq1 in latest service census; final post-stop latest UNKNOWN | PhantomNativeWorkScope@593449aa; current, SEALED; outstanding0/timers0; INVENTORY_FLUSH | World=true and PHANTOM confirmed pre-Synthetic; AutoSave033R=UNKNOWN; lease retained=true in service census; token UNKNOWN | Actual materialize → FAILED retained; capture capacity252>250. Own034t reproduced. No safe permitted release/fix. |
| 45 /268484616 /403906003877600 | registerNanos403906073001000; YES | 2026-10-10T23:42:14.361240600Z; same exception; NATIVE_STORE /PLAYER_STORE_ME; seq1 | Same seq1 in latest service census; final post-stop latest UNKNOWN | PhantomNativeWorkScope@1deee304; current, SEALED; outstanding0/timers0; INVENTORY_FLUSH | World=true and PHANTOM confirmed pre-Synthetic; AutoSave033R=UNKNOWN; lease retained=true; token UNKNOWN | Actual materialize → FAILED retained; same capacity blocker. Own034t reproduced. No safe permitted release/fix. |
| 27 /268484461 /404179606830700 | registerNanos404179617908900; NO for this replacement epoch | Cleanup firstException/phase/hook=UNKNOWN; nativeFirstIncident empty in final native snapshot | Cleanup latest=UNKNOWN; native failure fields empty | Actual current native owner, DRAINING/permanent seal; outstanding1; timers0; `9692:cast-launch:RESERVED:EARNED:depth=2:ageMs=3459`; work sampleNanos404391086713600 | World/AutoSave/exact lease token at final033R stop=UNKNOWN; current native owner confirmed | Birth after terminal parent403903739619700; EXP29913→35655, SP6266→6926, native cycles36. Final reserved earned work blocks release. Its exact first cleanup incident and ticket future/dispatch cause remain unproved. No patch from a hypothesis. |

Profiles36/45 already occur in earlier033R base census with different epochs
396432670211100 /396433860383600 and the same inventory exception. The R3
first incident stack identifies producer:
`PopulationEcologyService.beginNextWindow → HistoricalBackgroundService.begin
→ refreshCanonicalBaseline → materialize/dematerialize → MaterializedPlayer.cleanup
→ Player.storeMe → owned prepareStore → L2jPhantomBackgroundAuthority.capture
→ InventoryFacts.<init>`.
Это новый startup/catch-up materialization над inherited data, а не persisted
Java lease и не Synthetic gameplay error.

Initial final-scene participants123/328/375/437/492/601 have their own DETACHED
receipts with outstanding0/timers0. Их lifetimes не совпадают ни с36/45, ни с
replacement27. Scene033R runId `795d7107-0503-44bd-85e2-a6083e999f67` использует
Synthetic object268492939 и setupProfile27; setupProfile не равен initial cohort.

Последний033R native owner file содержит именно36/45/27, а stock log сообщает
final retained3. Exact final live service snapshot/typed failure для27 не сохранён.
Поэтому это согласованное сопоставление native owners с aggregate retained3,
а не атомарно доказанная финальная `_activeByProfile` таблица. Work detail cached:
ageMs3459 относится к sampleNanos404391086713600, не к file404396162199900.
Log фиксирует initial FAILED/retained5, final FAILED/retained3 и ThreadPool stop
за миллисекунды. Возможный collateral cutoff27 остаётся гипотезой; future due time,
первый отказ и точный producer reservation не установлены.

## Actual baseline-vs-new, без SQL-heal

| Own run / diagnostic scene ID | DB source / exact process | Result on old R3 Jar |
| --- | --- | --- |
| 034a /B01_PRE_SYNTHETIC_CENSUS; STARTUP032a-30768787e08447aa93acf328be5e60db | Own034a cloned read-only033rc; Game16604 /ticks639272820459491429; Login28340 /ticks639272820442220716 | Pre-Synthetic retained0. Profiles36/45 durable MATERIALIZED without World/native owner; one ordinary36 probe B02 returned BACKGROUND_RECONCILIATION_BLOCKED before load. This is a negative guard, not causal RED. |
| 034a /B04_STOCK_STOP_A | Same incarnation and Jar above | **PASS baseline stop only:** typed DONE/COMPLETE, materialization STOPPED/retained0, background STOPPED/operations0/leases0/transactions0, OS stock STOPPED. Does not rehabilitate36/45 or count as new-Jar task GREEN. |
| 034t /B05_EARLIER_SOURCE_CENSUS_T; STARTUP032t-3a32a76da5874121b15674b4ff6ba94c | Own034t cloned read-only032a; Game29348 /ticks639272825678917597; Login33652; exact role record archived | **PASS causal RED reproduction:** before any Synthetic, active retained2; same NATIVE_STORE exception. Exact capture facts below. |
| 034t /B06_STOCK_STOP_T | Same incarnation and Jar above | Typed DRAIN_PLAYERS/FAILED, materialization FAILED/retained2; stock Game/Login STOPPED. FAILED is preserved in global samples; no COMPLETE receipt. |

| Own retained owner | First incident | Latest at final sampled stop | Exact identity and native state |
| --- | --- | --- | --- |
| 36 /268484533 /413992015958800 | 2026-10-11T02:30:20.322434900Z, seq1; same NATIVE_STORE/PLAYER_STORE_ME exception | 2026-10-11T02:32:45.022786300Z, seq4; same exception | PHANTOM/RESERVED token255; World=true, AutoSave=true, clientNONE, pendingStore=false; SEALED/current/outstanding0/timers0; retained before and after typed failure |
| 45 /268484616 /413993041020700 | 2026-10-11T02:30:21.346836800Z, seq1; same NATIVE_STORE/PLAYER_STORE_ME exception | 2026-10-11T02:32:45.023786200Z, seq4; same exception | PHANTOM/RESERVED token316; World=true, AutoSave=true, clientNONE, pendingStore=false; SEALED/current/outstanding0/timers0; retained before and after typed failure |

Both own failed owners have mutableCount6, trackedObjects246 (243 INVENTORY,
3 PAPERDOLL), totalItems/nonQuestItems252, limit250, load281301/max7866000.
`compactCapacity=false` specifically because usedSlots252>maximumSlots250;
246<275, tracked inventory243<250, paperdoll3<25, and nonnegative scalar bounds
are otherwise satisfied. Calling existing read-only capture on these exact SEALED
owners reproduced the exception without changing Player state or DB.

`captureOwnedInventory` produces usedSlots249 by counting INVENTORY only;
native authority capture uses full `getSize()`252; stock non-quest capacity also
counts252. Thus the inherited projection and actual native capture disagree at
capacity. Origins of all accumulated item rows were not audited; no claim of
external/user corruption or a recent033R navigation regression is justified.
Background representation/model boundaries are outside conditional lifecycle
write paths. Shrinking inventory, weakening bound, bypassing owned store or
releasing unfinished retention would violate this task. No such change was made.

## Acceptance

| Gate | Actual result |
| --- | --- |
| 0 SOURCE | Exact required base/remote matched before work; no production/build/test edits; R3 Jar hashes verified; report-only exact allowlist. Publication requires normal push/read-back of containing commit. |
| 1 CAUSAL_IDENTITY | PARTIAL: exact36/45 identity+producer+first incident proven;27 native identity/work proven, first/final service incident UNKNOWN. Active entries and archived incidents are separate. Own034t archived cleanup incidents have finished=false and corresponding live entries. |
| 2 CAUSAL_RED | PASS_REPRODUCED, own034t/B05, old Jar EBC6918A…D46; exact full hash above. Ordinary034a admission rejection does not count. |
| 3 CAUSAL_GREEN | BLOCKED_DATA; safe allowed product repair not proven; NOT_RUN on new Jar. |
| 4 NATURAL_LIFETIME | NOT_RUN034: no stable new Jar/product fix. Historical27 earned facts are forensic reference, not034 acceptance. |
| 5 CLEAN_GLOBAL_DRAIN | FAIL on contaminated baseline034t: typed FAILED/retained2 despite OS STOPPED. Old-Jar034a COMPLETE is only control evidence. |
| 6 MANDATORY_REGRESSION | NOT_RUN: production unchanged; user permits93/93 and11/11 only after fix on stable new Jar. No inherited test result promoted to034 PASS. |
| 7 EVIDENCE_AND_CLEANUP | Private directory and ZIP individually SHA verified; own processes0; DB retained. Exact commit+normal push and local=remote read-back are final publication step, recorded separately after commit. |

Actual start2026-10-11T02:09:10Z. First GameServer02:20:45Z, within60minutes.
Semantic decision02:34Z; bounded diagnosis ended early because no permitted
data-safe repair was established. No blind scene to consume time: starts2/4,
production Jar-builds0/3, Synthetic scenes0/2. No crash72/73, away/return, sameDB
restart, main/PLAY/foreign JVM mutation, force, SQL-heal or DB deletion.

Changed public files: exact packet10+MANIFEST import, PROGRESS034,
RESULT_HANDOFF034, CLEANUP034, EVIDENCE_INDEX034 and FINAL_ALLOWLIST034.
Read-first documents and reused analogs are recorded in PROGRESS034.
TASK.md is absent from verified packet; SOURCE_MAP.tsv exists. No substitute API
or unconfirmed architecture was invented. No next task/Goal/Slice was launched.
