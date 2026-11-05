# REPORT128 — Living Party / PvE / PvP / PK

Дата: 2026-11-05. `PARTIAL`, `FULL128=NO`. STOP128; TASK129 не начиналась.
Окно исполнения: 07:46–13:55 UTC (~6ч9мин на момент закрытия отчёта); final publication после этого снимка.
Завершение расширено только для исправления STOP12 и обязательного restart/STORE/publication; новых gameplay starts после15 нет.
Codex tokens:1547081 (goal tool,13:55 UTC; пользователь явно разрешил превышение токенов).

Прочитаны все 10 документов пакета TASK128. Один Goal, одна TEST128, без R2/R3.
Live baseline `kpCat/L2J feature/phantom-world`: `6807ab3a7cc94522e3039214f73e7a93fc0f12e0`.
Физический source clone: `D:/Codex/TASK128-20261105/candidate`, без alternates/hardlinks.
Основной checkout с WIP109 (`236b965a116bbc79cccbef78b04c628225a629cb`) оставлен без записи.

| Gate | Результат и предел доказательства |
|---|---|
| A Party/logistics | NO: автономная NORMAL Party3 не наблюдалась. Исправлены supply handoff и orphan core; реальные расходы людей не являются снабжением фантомов. |
| B rewarded PvE | PARTIAL: реальные solo native kill/reward/settled tuples/EXP/SP в9 и13. Финальный JAR14/15 не подтвердил требуемые ≥2 до и ≥1 после restart. |
| C Heal | NO: нет qualified native Heal раненого товарища с MP/reuse и net HP witness. Buff/self-care не засчитаны. |
| D PvP defense | PARTIAL: native9 Human134 stock hostile action → автономный Phantom3 damage → flagged human death; pvpKills 0→1, pk=0, karma=0. Party defense не подтверждена. |
| E proactive PK | NO: отдельная сцена Human136→Phantom6 независимо от PvE выполнена; unflagged human kill фантомом/PK count/karma не получены. |
| F durability | NO full gate: sameDB/JAR14→15 и original objects/PvP count сохранены;15 qualification FAIL, нового useful action нет. Healthy STOP/STORE и observed postflight не подменяют этот отказ. |
| G logs | PARTIAL: финальный audit отделён от rotated logs. STOP12 исправлен;14 handled maxHP cleanup,15 uncaught qualification guard и product stalls не объявлены gameplay PASS. |

## Исправления и локальные паттерны

Переиспользованы PartyCoordinator CAS/identity lease, native supply journey, existing FarmSourceView cadence,
NativeContext/Command transaction guards, HistoricalService recoverKnown и exact stock NORMAL gatekeeper.
JDK25/Ant, существующая архитектура High Five; без новых зависимостей/слоёв, без изменений Geo/rates/karma policy.
Bounded exception: 12 файлов кода/проверок + этот отчёт; дополнительные причины обнаружены в той же TEST128.

1. `PhantomAutonomousFarmParty`, `PhantomPartyCoordinator`, `PhantomPartyBackend`, `L2jPhantomPartyBackend`:
   production low-stock фиксируется до Party gate; quiet native leave освобождает supply owner.
   Obsolete RECOVERING orphan восстанавливает только собственный SOLO row под существующей PHANTOM identity lease,
   при более новом SOLO leader/изменившемся root и положительном доказательстве отсутствия native core.
   INCONSISTENT/currentForce не ослаблены; absence census256 отдельно от native party bound144, выше256 отказ.
   Новые чтения goal ограничены существующим секундным FarmSourceView cache.
2. `L2jPhantomSupplyJourney`: terminal FAILED income.farm backoff допускает заработок в Party;
   unsettled/active/return ownership остаётся заблокированным.
3. `PhantomBackgroundTransaction`: exact canonical HISTORICAL_IDLE с текущим bound v2 context
   допускает no-effect продвижение исторического курсора при vitality6, сохраняя запрет gameplay effects,
   проверки points/identity/rules/state и отсутствие owned STORE. FARM этим исключением не разрешается.
4. `L2jPhantomBackgroundAuthority`: GATEKEEPER tolerance0 допускает стабильный Geo floor только с exact
   живым Teleporter NPC/spawn/rawXYZ/World identity/instance proof. Исправляет Giran raw−3404/floor−3400.
   FARM/ROUTE tolerance и native arrival XY/floor+5/exact fee debit сохранены; Geo/metadata не менялись.
5. `PhantomHistoricalBackgroundService`: durable travel.anchor_mismatch перепланируется существующим CAS-путём.
   Курсор/interval/rewards не выдаются классификацией; следующая transaction заново доказывает маршрут.
6. `PhantomBackgroundService`: stale recovery control того же objectId с более новым epoch, без pending STORE/baseline,
   завершается REPLAN перед deadline. Завершается только старый worker; новая lifetime/receipts/failure latch не меняются.
   Foreign/older/ambiguous owner остаётся INCONSISTENT. Закрыт найденный reviewer P2 delayed-callback deadline ordering.
7. Проверки: существующие `PhantomPartySuite`, `PhantomSupply071Suite`, `PhantomLivingWorld114Checks`.

RED→GREEN: supply handoff26/27→27/27; terminal backoff10/11→11/11; orphan27/28→28/28;
cadence8192 лишних goal reads→исходный bounded count; v2 idle assertion→PASS;
gatekeeper canonicalization compile RED→PASS; durable mismatch assertion→PASS;
native12 shutdown poison→superseded-control regression PASS с foreign/older/STORE/baseline negatives.
Финальный compile-tests и jar: exit0. Focused70/70: lifecycle28, recovery14, routes7, tactics10, supply07111.
DB-free LivingWorld114Checks: PASS. Protected historical DB suites/общий ant verify не запускались.
Независимый review всех12 source/test файлов: P1=0, P2=0; последний обнаруженный P2 исправлен; runtime SUCCESS reviewer не утверждал.

## Native кампания и причинная прогрессия

Все запуски одной TEST128, без reset/adopt. Глобальный CUT3: `2026-11-05T10:11:21Z`.
После него нет ручных Phantom HP/items/EXP/party/goals/позиции/attack/karma/PK writes.
Границы SQL — guarded SELECT; изменения игрового состояния выполнял production server.

| JVM | Причина и результат |
|---|---|
| 1–2 | Pre-CUT qualification/catchup/death/locality выявили исходные блокировки; без CUT, healthy drain/STORE, observed postflight; исходные FAIL сохранены. |
| 3 | Первый CUT; solo native19890 EXP/2480 SP/3 loot sequences; NORMAL exit0/postflight. |
| 4 | Исправление независимого scene gate; CUT не достигнут; healthy STOP/observed postflight. |
| 5 | Observer load + null observerLease в finally; PhantomSystem уже drained. Exact-PID stock Shutdown через guarded JavaAttach, без force kill; emergency postflight. Обычный STOP5 receipt отсутствует, FAIL не переименован в PASS. |
| 6 | Solo native73949 EXP/9339 SP/7 loot sequences; World207 выявил неверный absence census144; normal STOP/postflight. |
| 7 | Новый census: реальный actor4 obsolete orphan → SOLO/none/cursor current; healer2 context fence ещё блокировал; normal STOP/postflight. |
| 8 | SameJAR; scene observation guard и stock human economy/locality; CUT отсутствует, healthy observed postflight. |
| 9 | SameJAR; независимые PvP/PK сцены, solo rewarded PvE; real PvP kill3; normal STOP/postflight. |
| 10 | SameJAR durability PvPcount3=1; independent unflagged human return; настоящий paid NORMAL GK; PK не случился; normal STOP/postflight. |
| 11 | Кандидат6: healer2 historical cursor current/admitted WARM/none; persisted route mismatch3 обнаружил отдельную recovery причину.15 native damage,0 reward; normal STOP/STORE/exit0/postflight, audit630files. |
| 12 | Кандидат8:2 native damage,0 reward; route3 hard mismatch сменился native owner/recontext. STOP FAIL из-за latched stale recovery1, exit1. Public materialization.shutdown сохранил owner1 и освободил count0; затем public System retry + stock Shutdown. Emergency postflight PASS; исходный FAIL сохранён. |
| 13 | Кандидат9:1 matching settled native kill,13515 EXP/1715 SP/1 loot; нормальные STOP/STORE/exit0/postflight. Native human drop/pickup20k conserved. Pre-native guard refusal сохранён отдельно: это не дополнительный GameServer start. |
| 14 | Финальный кандидат10:0 observed damage/reward после CUT; handled canonical maxHP cleanup1, новый native epoch; нормальные STOP/STORE/exit0/postflight. |
| 15 | SameJAR10/DB: no CUT,0 native actions, qualification FAIL.1/3 native_context.required;5 preparation_capacity;2/4/6 WARM. Healthy STOP/STORE/JVM exit0, original failure сохранён, game15.exit0 success marker отсутствует; observed postflight PASS. |

Native9:245 damage events,10 matching kill/reward/settled target tuples,145605 EXP/18655 SP.
19 raw loot sequences не объявлены все PvE loot: среди событий возможны native death drops.
Native10 после своего local CUT:7 damage events, без qualified нового settled kill; SQL EXP delta включает history.
Party3 samples/qualified Heal до native11:0. Предыдущие кандидаты не подменяют gate финального JAR.
Во всех финальных сценах Party3 не собрана. Healer2 остался READY/WARM с текущим курсором, отдельной farm locality;
native group arrival/совместный бой не доказаны. Нейтральный human остался в Oren: native путь к GK не завершился.
Подготовка/локальность Party lane не достигла полноценной совместной qualification; независимые D/E выполнялись без неё.

PK цепочка: native9 Human136 one stock onForcedAttack→Phantom6 ACTUAL_ATTACK/ENGAGE и настоящий damage;
human fled3150, дождался фактического flag0 и вернулся. Phantom6 успел уйти примерно3.2км;
production encounter COOLDOWN `pvp.combat.timeout`, proactiveEngagements0, WARN отсутствовал.
Дальнейший human погиб до qualified unflagged contact. Нельзя утверждать, что policy признала legal PK,
или придумать иной guard: фактически не дошли до proactive decision/kill. pk=0, karma=0.
Human9 return adapter отказал по dead/stage guard до действий; exception сохранён.
Human10 Town restart/paid NORMAL30177:15/finite native potions не выдавали ресурсов фантомам.
Продажа людьми собственных armor2475 (309400A), покупка1061 и fee5200 — только человеческая экономика.

## Safety, logs, доставка

G0: owner-approved Windows OS/SCM/JDBC attestation, MariaDB11.4.13 PID8692 port3308;
схема `l2jmobiush5_phantom_test_128`, UUID `94015cfe-3141-4b57-b294-82a9d871b8ae`, game port17789.
Stock schema created once:121 SQL files/203 statements,18 DROP omitted. Старые БД не использовались для записи.
Geo203 — независимые физические копии, original SHA и2234 protected files сохранены.
MAIN/PLAY/OwnerConfig/primary WIP109/старые worktrees не менялись.
Foreign Minecraft/TLauncher остановили G0 перед postflight7; пользователь закрыл их сам; fresh Java0 G0 PASS.
Чужие процессы не завершались, force kill отсутствует. Полные private logs/credentials в Git не включены.

Log audit: stdout/stderr/stock java/error всех15 запусков сохраняются в hashed snapshots. Известные группы: optional buylists/custom,
LoginServer reconnect (LS lane не запускалась), stock shutdown warnings, реальные death drops;
materialization reconciliation и farm stalls — actionable product failures, не harmless PASS.
Historical5 uncaught null observerLease исправлен; historical9 refused human Attach exception не скрыт.
Historical12 OWN128_FAILED_STOP_NO_FORCE и background.recovery.control_owner_changed сохранены, не PASS.
Native14 cleanup exception: maxHp3226/2946, refreshSUCCESS/nonAtomic, отказ первого load и следующий native epoch;
без восстановления buffs/ручного HP. Stock error0 пуст; это handled diagnostic, а не отсутствие всех product problems.
Только текущие raw stderr:14 cleanupIncident1/uncaught0/loginReconnect43/optionalBuylist1/stopFailure0;
15 cleanupIncident0/uncaught1 (qualification guard)/loginReconnect38/optionalBuylist1/stopFailure0.
Final GameServer SHA256: `790ff15f09f883cf84dc0477a1ae424acc3a90cf969f77c68a81b78799105728` (14/15).
Final LoginServer SHA256: `955591ce6ae28ca5c228853a16993cc6c9d18004ad9d5bbbfdb0b4bdb78cfe87`.
Final15: STOP drained=true/stopFailed=false, stored15 readback, fresh G0 JVM0/listeners0,
guarded postflight ownSessions0/all characters offline/DDL_DML0. Archive15:834 SHA-verified files.

Артефакты: evidence root `C:/Users/ZBook/.codex/visualizations/2026/11/05/01a1aaac-a34e-7933-9651-3d276674a62b`;
`logs128/runN`, `log-auditN.json`, `candidate10-128.json`, `summary128-current.json`, `summary128-globalcut.json`, guarded scope pairs.
Исходные preimages вне checkout проверены SHA256; предыдущие JAR/input/helper seals сохранены.
mojibake-маркеры в изменённых файлах проверены: all27 PASS, включая report.
escaped Cyrillic в изменённых файлах проверены: all6 PASS, включая report.
Два byte-identical final scope/encoding checks: exact13 allowlist, source/stage/JAR parity,2234 protected SHA PASS.

Git использован по прямому разрешению TASK128: `clone --no-hardlinks --single-branch --branch feature/phantom-world`,
`ls-remote https://github.com/kpCat/L2J.git refs/heads/feature/phantom-world`, `rev-parse HEAD`,
`symbolic-ref --short HEAD`, `status --short --untracked-files=all`, bounded exact-path `diff`,
`diff --check -- <allowlist>`, `add -- <exact files>`, `diff --cached --check -- <exact files>`,
`diff --cached --stat -- <exact files>`, `commit -m <scoped TASK128 title>`,
`push origin HEAD:refs/heads/feature/phantom-world`. Ни force, ни reset/clean/stash/rebase/master/default writes.
EOL diagnostics: `config --get core.autocrlf`, `check-attr text eol -- <Authority>`, `diff --unified=0 -- <exact background files>`.
Code commits/non-force pushes: `88b835404c0abd183e29fd824517a300a8812e33`, `fb39357ffc21d706a9f37e5fc571e2d3d006b5b9`.
Третий code commit/push: `7b3b5873d83bc4fb13581b0b20ddb4402e9e4276` (superseded recovery/STOP).
Каждый push проверен fresh ls-remote == local HEAD. Report публикуется отдельным exact-path commit;
окончательный receipt/remote parity в evidence `final-publication128.json`. FULL128=NO; STOP128, без TASK129.
