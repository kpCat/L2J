# REPORT128 — Living Party / PvE / PvP / PK

2026-11-05. `PARTIAL`, `FULL128=NO`. STOP128 после завершённых проверок и безопасного drain. TASK129 не начиналась.
Первый опубликованный снимок: 07:46–13:55 UTC, native1–15. Продолжение той же TASK128 явно разрешено пользователем;
окончательный снимок: 2026-11-05T18:13:38.358176+00:00. Остановка обусловлена архитектурным rendezvous blocker, не временем/токенами.
Пользователь отдельно разрешил Minecraft без фактических конфликтов и бесплатные путешествия всех managed Phantom.
Последнее требование заменяет прежнюю обязанность оплачивать Phantom travel; остальные покупки/снабжение остаются платными.

Прочитаны все10 документов пакета. Один Goal, одна TEST128, без R2/R3/reset/adopt.
GitHub baseline: `kpCat/L2J feature/phantom-world`, `6807ab3a7cc94522e3039214f73e7a93fc0f12e0`.
Независимый physical clone: `D:/Codex/TASK128-20261105/candidate`, без alternates/hardlinks.
Primary WIP109/HEAD `236b965a116bbc79cccbef78b04c628225a629cb` не изменялся.

| Gate | Результат и предел доказательства |
|---|---|
| A Party/logistics | NO full gate: NORMAL Party3 не наблюдалась. Settled supply/warehouse receipts сохранены; человеческие расходы не засчитаны как Phantom supply. Bot travel теперь бесплатен по явному требованию пользователя. |
| B rewarded PvE | PARTIAL: реальный solo native PvE был в3/6/9/13/19. C13/native19:9 damage,10893 EXP,1240 SP,2 loot sequences. Требуемые ≥2 kills до/≥1 после restart на финальном C15 не доказаны. |
| C Heal | NO: нет qualified native Heal товарища с MP/reuse и net HP witness. Buff/self-care/regen не засчитаны. |
| D PvP defense | PARTIAL: native9 Human134 hostile act → автономный Phantom3 damage → flagged human death; pvpKills0→1, pk0, karma0. Party defense не подтверждена. |
| E proactive PK | NO: независимая сцена Human136/Phantom6 выполнена без ожидания PvE. Native unflagged kill/PK count/positive karma не получены. Memory discovery исправлена; policy не смягчалась. |
| F durability | PARTIAL: sameDB/JAR20→21 и22→23; exact stored22→before23 rows, original identities/PvPcount, healthy STOP/STORE/postflight. Новый требуемый rewarded action после финального restart не доказан. |
| G logs | PARTIAL: свежие stdout/stderr/java/error всех запусков проверены. STOP12/16 исправлены; исходные qualification/capture failures сохранены. Отсутствие uncaught в финальных raw logs не объявлено полноценной игровой жизнью. |

## Production changes и regression

Переиспользованы существующие CAS/identity leases, NativeContext/Command/STORE guards, recoverKnown,
FarmSourceView cadence, stock NORMAL GK, commerce receipts, social cache/stripe/lifecycle и точечные PvP lookup.
JDK25/Ant; без новых dependencies, executor/timer-per-profile, DB schema, Geo/rates/karma policy changes.
Bounded exception по явному разрешению пользователя:36 code/test файлов + этот отчёт; независимый review всех delta.

1. Party: low-stock до формирования; quiet native leave освобождает supply; obsolete RECOVERING own-row → SOLO
   только под PHANTOM identity lease, newer SOLO leader/root и positive native absence. CurrentForce/INCONSISTENT защищены.
   World207 требует отдельного absence census256; native force (Party/CC) census bound144 не менялся; goal reads — существующий cache1sec.
2. Supply: terminal FAILED income.farm backoff допускает заработок в Party; active/unsettled/return fences сохранены.
3. Historical idle: exact no-effect idle принимает bound v2 vitality context. Stable Giran rawZ−3404/floor−3400
   доказан живым exact Teleporter/spawn/World/instance; FARM/ROUTE tolerance не расширялась.
4. Travel recovery: durable anchor_mismatch и insufficient_adena перепланируются существующим accepted-journey/CAS.
   Historical insufficient_adena теперь REPLAN, а не бесконечный RETRY. Старый wallet-aware filter проверен до free override;
   текущая политика оценивает Phantom leg как0, сохраняя ordinary native price APIs и raw catalog identity.
5. STOP: superseded same-object/newer-epoch recovery worker без pending STORE/baseline завершается REPLAN;
   foreign/older/ambiguous остаются fenced. Commerce finishStop idempotent при уже STOPPED, admission/counters сохранены.
6. Free travel: WALK/GK/RETURN_GK, historical/visible farm, composite navigation, supply, market, quest-instance callers
   используют explicit phantomFee/phantomMaximumFee0. Shop/warehouse/craft/ordinary player fees сохранены.
   Native waiver требует same World Player, headless session, current attached Phantom owner, running native-work ticket,
   PHANTOM/RESERVED identity lease и повторной проверки owner. Имя/GM/account/bot flag не дают льготу.
7. Receipt migration: только old unpaid TELEPORT PREPARED/COMMITTING + EXACT_BEFORE, current ACTIVE exact goal/revision,
   fresh native quote, same destination/resources и CAS → new PREPARED fee0. Следующие вызовы SUCCESS/IDEMPOTENT.
   Уже списанный FIRST_EFFECT_ONLY завершает старый destination без второго debit/refund. Zero-fee noop-first-effect распознаётся точно.
   Tariff hash участвует в topology authority; старые catchup plans проходят штатный stale recovery.
   NativeContext переаттестуется existing owner, вместо маскирования отключением debit.
8. PK discovery: immutable own memory query с existing authority/TTL/STOP; max24 memories → distinct subjects до cap4;
   Context заполняет только оставшиеся10 exact owner slots после party/farm. Реальные attackers первыми в native cap32.
   socialSubject выводится из одного captured Counterpart и сравнивается с исходным expected memory subject; Snapshot запрещает split identity.
   Редкий stable half-hour roll, disposition, kill-memory veto, locality/risk/peace/consent и pair budgets не менялись.

Focused RED→GREEN: supply/backoff/orphan/cadence/v2 idle/GK floor/recovery/STOP; insufficient classification;
free ordinary-vs-bot price/owner fences/old receipt second SUCCESS + third IDEMPOTENT; PK discovery/attacker slots/identity split.
C14 regression86/86 (Party28/14/7/10, supply11, commerce9/7), quote5/5, DB-free114 and pricing/owner/city097/098/110 models PASS.
C15 reviewed regression41/41 across11 existing Social/PvP suites; compile-tests/jar exit0.
Protected historical DB suites и общий ant verify не запускались. Model PASS не считается native gameplay PASS.
Independent `/root/review_party128`: C11–C14 delta и финальные PK6 P1=0/P2=0; найденный identity P2 исправлен и повторно проверен.
Reviewer runtime не запускал и native SUCCESS не утверждал. Private Minecraft boundary/human helpers/capture bound также reviewed.

## Native campaign

Глобальный CUT3: `2026-11-05T10:11:21.035268500Z`. После него нет ручных Phantom HP/items/EXP/SP/skills/
party/goals/position/attack/karma/PK writes. SQL evidence — guarded SELECT; игровое состояние меняет production server.

| JVM | Причина и фактический результат |
|---|---|
| 1–2 | Pre-CUT qualification/catchup/death/locality; no CUT; healthy drain/STORE/observed postflight, original FAIL retained. |
| 3 | First CUT, solo19890 EXP/2480 SP/3 loot; normal STOP/postflight. |
| 4 | Independent scene gate; no CUT, healthy observed postflight. |
| 5 | Observer load/null lease finally failure; Phantom уже drained. Exact-PID stock Shutdown, no force kill; emergency postflight, original FAIL. |
| 6 | Solo73949 EXP/9339 SP/7 loot; World207 census defect found; normal STOP/postflight. |
| 7 | Obsolete orphan4 → SOLO/none/cursor current; healer2 context fence; normal STOP/postflight. |
| 8 | No CUT; observation guard, stock human sale/paidGK; healthy observed postflight. |
| 9 | Separate PvP/PK scenes: actual PvP kill3;245 damage,10 matching kill/reward/settled tuples,145605 EXP/18655 SP; normal STOP/postflight. |
| 10 | SameJAR: PvPcount3=1 durable;7 damage after local CUT,0 reward; normal STOP/postflight. |
| 11 | Healer2 catchup current/WARM;15 damage,0 reward; normal STOP/STORE/postflight. |
| 12 | Route mismatch recovery;2 damage,0 reward; STOP poisoned by stale control. Public materialization STORE count0/System retry/stock Shutdown; emergency postflight, original FAIL. |
| 13 |1 matching settled solo kill,13515 EXP/1715 SP/1 loot; normal STOP/postflight. Human20k stock drop/pickup conserved. Prior guard refusal is not extra GameServer boot. |
| 14–15 | C10:14 handled maxHP3226/2946 cleanup/new epoch,0 reward;15 no-CUT qualification FAIL. Both healthy STOP/STORE/JVM0;15 only observed postflight, no fake success marker. |
| 16 | C10 + Minecraft: healer2 actual native full body; human paidGK5200. STOP deadline:commerce; public quiescent STORE count0 then retry/stock Shutdown; emergency postflight, original FAIL. |
| 17 | C11 idempotent STOP; human136 stock GK9400, observer5200; actual2/3/5,3 natural death,0 damage/reward; normal STOP/postflight. |
| 18 | C12 wallet planner;0 damage/reward. Passive retained1/available7 disproved capacity leak; historical RETRY classification identified; normal STOP/postflight. |
| 19 | C13: actual6 native farming,9 damage/10893 EXP/1240 SP/2 loot; no Party/Heal/PK; normal STOP/postflight. |
| 20 | C14: human stock GK5700 confirmed. Catchup3 cursor29897986→29898329; общий capture867 records/2MB, capture guard FAIL; healthy STOP/STORE/observed postflight, original FAIL. |
| 21 | SameC14: raw capture bounded64MB, no sampling.15 damage,0 native reward; actual deaths/stock recovery, no Party/Heal/PK; normal STOP/postflight. |
| 22 | C15:0 damage/0 EXP/0 SP/0 loot, settled tuples0;catchup6 current, human stockGK5700; no Party/Heal/PK; normal STOP/STORE/exit0/postflight. |
| 23 | SameC15/DB:0 damage/0 EXP/0 SP/0 loot, settled tuples0;exact stored22→before23 boundary; no Party/Heal/PK; normal STOP/STORE/exit0/postflight. |

Native9 raw19 loot sequences не объявлены все PvE: возможны реальные death drops. SQL EXP/history не подменяет native counters.
Финальный C15: native22:damage0,EXP0,SP0,settled0; native23:damage0,EXP0,SP0,settled0. Party3/qualified Heal/PK witnesses отсутствуют.
В22 profile6 самостоятельно достиг committedCursor=requestedHorizon29898355; BACKGROUND из-за schedule_not_active,
а не прежнего insufficient_adena/native-context catchup fence. Это useful historical continuation, не native rewarded kill.
В sample23 в18:10 UTC cursors1/3/4/6 current;5 остаётся transaction.native_context_required, healer2 WARM/ecology fence.
Оставшиеся fences/отсутствие совместного боя не объявлены исправленной полноценной жизнью.

PK causal history: native9 human136 one stock forced attack → Phantom6 ACTUAL_ATTACK/ENGAGE/реальный damage;
human fled3150/unflagged return, Phantom ушёл~3.2км; COOLDOWN pvp.combat.timeout, proactive0.
В22 настоящая attack memory сохранена. Read-only formula inference для окна996611:roll5932/threshold300, admission=false;
это не native policy witness. Реальные locality/death/schedule guards также мешали. Roll/clock/subject не подбирались ради PASS.
Обычный Human136 заплатил5700 (NORMAL30080:10) в20/22; native town restart/finite potions не выдавали ничего Phantom.

## Причина STOP128 и варианты

Для произвольной разнесённой когорты нужен owned rendezvous/bootstrap до первой native Party.
Текущий selectTeam требует local DD core; pending remote JOIN требует уже существующую native Party/majority.
Own-root arrival ведёт участников к разным farm anchors. Простое снятие1200/перестановка barrier не доказывает quiet travel/STORE handoff.
Формирование в городе дополнительно требует group-owned departure: SOLO travel исключает Party-member, group farming ищет местных mobs.
Reviewer подтвердил source dependencies; решение остановиться по архитектурному условию пользователя принимает исполнитель.
Уже local core на общей farm position допускает более узкий existing form/invite handoff; такой core в финальных сценах не подтверждён.
Combat/consent1200 не ослаблялись.
Вариант продолжения той же TASK128: согласовать protocol с common destination, exact roots/epoch/pending claims,
ожиданием текущих accepted owners, отменой только собственного нового route, irreversible-owner fences и recovery/restart contract.
Альтернатива — принять PARTIAL и текущие полезные fixes.
Повторять те же сцены без этого решения/менять редкую PK policy ради результата нецелесообразно.

## Safety, logs, delivery

G0: Windows OS/SCM/JDBC attestation; MariaDB11.4.13 PID8692 port3308; only `l2jmobiush5_phantom_test_128`;
UUID `94015cfe-3141-4b57-b294-82a9d871b8ae`, game17789. Stock schema created once121 files/203 statements,18 DROP omitted.
Geo203 — physical copies;2234 protected SHA сохранены. MAIN/PLAY/OwnerConfig/WIP109/старые БД/worktrees не менялись.
Свежие полные G0/lease перед native; финальный lease истекал18:23:15 UTC. Final23:drained=true/stopFailed=false; stored23 readback; fresh G0 TEST_JVM0/reservedListeners0/ownSessions0/charactersOffline/DDL_DML0.
Ранний blanket G0 остановил postflight7, пользователь закрыл Minecraft сам. По следующему явному разрешению guard сужен:
exact image/main/cwd/javaHome/gameDir/SHA/start identity + TTL120sec receipt + SQL/TEST TCP/protected-path disjoint checks;
unknown/conflicting JVM fail closed. Minecraft/TLauncher не останавливались/не менялись;16–23 выполнялись параллельно с ними.

Все stdout/stderr/java/error каждого запуска archived/hashed (`logs128/runN`, `log-auditN.json`).
Optional custom buylists, LS reconnect (LoginServer не запускался), stock shutdown/death drops отделены от actionable stalls/cleanup/STOP.
Rotated cumulative warnings не приписывались новому запуску. Fresh final stderr: 22:error0=0B,errors0,cleanup0,stopFailure0,farmStall0,LSreconnect101,optionalBuylist1; 23:error0=0B,errors0,cleanup0,stopFailure0,farmStall0,LSreconnect101,optionalBuylist1.
Диагностический инцидент: reused C11 verifier перезаписал3 private C11-final test logs; исходные bytes не восстановлены/не выдуманы.
Native receipts/SQL snapshots/archives/source не затронуты; original C11 logs и последующие C12–C15 checks сохранены.
Подробности: `diagnostic-log-integrity128.json`. Native20 capture FAIL не переименован в PASS.

Final GameServer SHA256: `289f801122dfebfa14aebfa916da73dc3c051a9386b281158dd8afaa5231bfbe`.
Final LoginServer SHA256: `3726103da2288d229093991c9d8c4c4740bc0dffd23d5da34e7833daed9f4e52`.
Evidence: `C:/Users/ZBook/.codex/visualizations/2026/11/05/01a1aaac-a34e-7933-9651-3d276674a62b`;
candidate15/seals/preimages, both summary128 files, exact scope pairs, continuation publication receipt; private logs/credentials вне Git.
mojibake-маркеры в изменённых файлах проверены: все27 маркеров по final allowlist37 файлов — PASS.
escaped Cyrillic в изменённых файлах проверены: все6 patterns по тому же final allowlist37 файлов — PASS.
Два финальных byte-identical scope/encoding receipts: exact37 allowlist, source/stage/JAR parity и2234 protected SHA; final-scope-continuation128-a/b.json.

Git разрешён явно TASK128: physical clone; ls-remote; rev-parse/symbolic-ref/status; exact-path diff/diff --check;
exact-path add/diff --cached --check/commit; ordinary `push origin HEAD:refs/heads/feature/phantom-world`.
Ни force, ни reset/clean/stash/rebase/master/default writes. Полные argv текущих commits: `continuation-code-commits128.json`;
финальной публикации: `final-publication-continuation128.json`; scope/hash commands сохранены в Final-Scope128.py и receipts.
Initial source commits:88b835404c0abd183e29fd824517a300a8812e33,fb39357ffc21d706a9f37e5fc571e2d3d006b5b9,
7b3b5873d83bc4fb13581b0b20ddb4402e9e4276; first report000108db1105d4ed9245c6c45a27f4cdf40f78a2 сохранён.
Continuation code commits:`3a73f087c0e946c6e6350e5f8874d158bfee0d30`,`52cf04ddabda9610166d37fe77a80c5ae4142556`.
Final commit/ordinary-push/fresh remote parity и полные argv: `final-publication-continuation128.json`. FULL128=NO; STOP128, без TASK129.
