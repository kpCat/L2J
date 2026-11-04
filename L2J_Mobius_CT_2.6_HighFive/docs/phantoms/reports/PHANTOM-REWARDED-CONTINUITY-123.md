# TASK123 — rewarded continuity

Статус: **PARTIAL; FULL123=NO. STOP123. TASK124 не запускалась.**

На исходном JAR получены 2 native rewarded kills и ещё 2 после sameDB/sameJAR restart.
Их EXP/SP и итоговые SQL snapshots подтверждены. Полный gate не закрыт:
в исходной паре отсутствовал actual consume witness для soulshots;
финальная пара с исправленной диагностикой не получила новых PvE rewards за bounded окна.
Продуктовый repair сохранения или автономного rewarded restart не объявляется.

## Baseline, scope и сохранность

GitHub `kpCat/L2J`, `feature/phantom-world`, baseline `c18e1d014a85d4c8a891d2ac439a4a1ae804116c` проверен live до изменений и каждым host G0.
Новый физический clone `phantom-task123-20261104`, `clone --no-hardlinks --single-branch --branch feature/phantom-world`.
Основной checkout HEAD236b965 с WIP109 оставлен read-only; прежний отдельный source123 также только читался.
1 235 protected preimages совпали; MAIN/PLAY, старые TEST/DB, releases057–061, клиент и MariaDB service не менялись.
Original Geo203 и отдельная физическая копия проверены; shared users/grants не изменялись.

Exact public allowlist, пути относительно High Five:

1. `java/org/l2jmobius/gameserver/model/actor/PlayerNativeEvidence.java`
2. `java/org/l2jmobius/gameserver/model/item/instance/Item.java`
3. `test/java/org/l2jmobius/tests/phantoms/PhantomNativeEvidenceContinuation022Suite.java`
4. `test/java/org/l2jmobius/gameserver/phantoms/player/PhantomPickup109Checks.java`
5. `docs/phantoms/reports/PHANTOM-REWARDED-CONTINUITY-123.md`

Другие хроники, UI, schema/migrations, экономика, ownership/epoch/CAS/receipt и навигационная физика не изменены.
OwnerConfig288, premium, публичные конфиги и Adena57 amount5/chance1 сохранены.

## Read-first и writers

Прочитаны все документы пакета/GOAL/manifest, root/module AGENTS, README/readme, workflow/efficiency/current status,
bounded master plan/package standard/build.xml и только final reports118/121/122: 22 обязательных входных файла.
AGENTS выше проекта и в docs/phantoms/tasks не найдены; module README.md отсутствует, имеется readme.txt.
Локальные аналоги: native118 Orc farm; create-only bootstrap/G0/native122; existing native evidence и DB-free Managed Player109.
Дополнительные чтения были ограничены causal symbols writers, stock item/SoulShots, receipt codecs и native learning при SP spend.
Полные классы >1000 строк и другие хроники не читались; точный счётчик repository searches не вёлся.

| Область | Реальный writer / ограничитель |
|---|---|
| EXP/SP/level/death | Player/PlayerStat, native reward target/evidence; skill learning — отдельная skill+SP SQL transaction |
| Items/Adena | Stock Item/PlayerInventory и Commerce; фактический item count сериализует существующий Item._dbLock |
| Native STORE | Player.store, NativeWork checkpoint, OwnedStoreBoundary PREPARE/native SQL/finalize и full inventory hash |
| Background | BackgroundTransaction profile/character/components/items locks, canonical comparison/CAS/commit witness |
| Historical handoff | Exact claim/root/revision/generation и native epoch; исторический reward не считается native kill |
| Admission | Population admittedIds, Ecology admittedPreparation и physical materialization permits — разные gates |
| Combat/retry/STOP | Existing AutoPlay/learning/supply action owners; accepted work drain, retained receipts и stock Shutdown |

`synchronized(Player)` как общая граница не вводился. Доказанного overwrite native progress не установлено.

## PRE-CUT и G0

Полный G0 PASS до CREATE: privileged OS image/FILETIME/SID/SCM/command/TCP identity, source/stage disjoint,
owner+SYSTEM ACL, exact JDBC3308/MariaDB11.4.13/physical datadir, полная metadata visibility/grants,
foreign sessions0, schema absent, свободный17783, protected hashes и Geo203.
Только `l2jmobiush5_phantom_test_123`, UUID `0c6eab3f-ad8f-4ff3-a718-5b71dbfa851f`; один CREATE.
Stock plan121 SQL files/203 statements;18 DROP пропущены. Старые схемы не очищались и не reseed.

OwnerConfig Apply/Verify/profile validation — штатные build gates; полный ConfigLoader использован game-free bootstrap.
Production parser: enabled/diagnostics=true, population1/active1/cap8, UTC; XP3/SP5, Adena5/1.
Canonical READY/ACTIVE до17:53Z 04.11.2026, профиль1/object268480990/class44/classIndex0.
PREPARED_NOT_EARNED: Orc Fighter level3/EXP363/SP0/Adena5000, No-grade equipment/consumables, HP186/186.
Anchor `population.farming.orc.20535`, position -49106,-115854,-160.
Stock enabled OrcStarting.xml NPC20535 Prairie Keltir level2/EXP70/SP2; native hasGeo/canMove/canSee подтверждены.
Observer268481135: disabled-login ordinary human, not GM, not invulnerable, HP5756/5756; locality сохранена PRE-CUT.
После CUT не было grants, ручных координат/HP/progress, forced admission/spawn фантома или replay.

Первый ACL helper не загрузил PowerShell.Security: ACL исправлен до G0/SQL.
Два game-free farm refusals выявили ошибку helper: TERRITORY_POLYGON законно имеет x=y=z=0.
Исправлена проверка реальной точки внутри territory; не менялись Geo/спавны/NPC rewards.
Journal owner-only ACL и witness проверены до GameServer. Все preflight ошибки сохранены; GameServer на ошибочной fixture не запускался.

## Четыре native окна

Во всех окнах один профиль1/object268480990/class44/index0, одна DB, одинаковые публичные configs/data.
Initial input seal29084 files. Полный raw и hashes находятся в private stage; LoginServer не запускался.

| Boot | JAR / события | Финальный SQL level / EXP / SP / Adena / SS1835 | STOP |
|---|---|---|---|
| 1 | JAR1; 2 kills, +420EXP/+20SP, +120A pickups | 3 / 783 / 20 / 5120 / 594 | healthy/STORE/Java exit0/postflight PASS |
| 2 | SAME JAR1; 2 новых kills, +2310EXP/+110SP | 5 / 3513 / 150 / 5678 / 576 | healthy/STORE/Java exit0; wrapper exit1; observed postflight PASS |
| 3 | JAR2; 0 kills; проданы прежние8 Shirts21+3 Pants28, затем native return WALK | 5 / 3513 / 150 / 6231 / 576 | healthy/STORE/exit0/postflight PASS |
| 4 | SAME JAR2; 0 kills; return completed, выбрано autonomous learning | 5 / 3513 / 30 / 6231 / 576 | healthy/STORE/exit0/postflight PASS |

JAR1 SHA256 `1ce2c77cd40b5dcaa9bcc736a047cd6d175d7b76eef1c7f1e370f420a4e3e7a6`.
JAR2 SHA256 `61c15614fcb6ad6d067a5943e3987fe14dc1c036fe9b1a2aa60e0ab8116c6ab0`.
Внутри каждой пары совпали JAR и input/helper seals; перед JAR2 сохранены byte preimages и SHA.
Creation authority/manifest не переписывались; новый runtime manifest заменяет только2 production sources,1 test и JAR.
Последний DB-free integration test добавлен после native; runtime source-tree parity этого test-файла не заявляется.
После native исправлен только CRLF добавленного Item.java hunk; preimage/normalized equality и exact Item.class equality с JAR проверены.
Каждое полезное observation window ограничено120s. Пятый запуск и увеличение cap/timeouts не выполнялись.

## Accounting и исходные FAIL

`before2 == stored1` по SQL identity/progress/inventory/components; before4 соответствует stored3.
BOOT2 native targets268458798/268458907 — настоящие NPC20535 из READY map; deltas210/10 и2100/100.
Final native epoch/snapshot, canonicalChecked/nativeAfterMatched/nativeContextMatched и SQL согласованы.
Новый historical interval29896470→29896471 и receipt087326de9fa4196a7eec5e7676c5859124b894dbc655d818935196deb36b2991
дали отдельно +420EXP/+20SP/+110A/8 Shirts21/10 Coal1870; receipt отсутствует в BOOT1, replay не обнаружен.
BOOT2 native pickups:3 Pants28,8 Shirts21,100 Coal1870; final Coal110/Pants3/Shirts8.
Cash:5000+120 native pickup+110 historical+8×56 sale=5678.
Для8 sales имеются7 прямых COMMITTED receipts и1 exact durable source.index/currentAmount revision7→8 advance;
такой advance допускается только после exact matching COMMITTED (L2jPhantomSupplyJourney), а не по равенству сумм.

BOOT2 original `CONSERVATION_CAPTURE_FAILED`/wrapper exit1 сохранён, `game2.exit0` не создавался повторно.
Четыре missing captures относятся к no-op PRE-WORLD tx6/7; decoded before/after progress/full inventory hash равны.
Tx2/3 были captured; final tx9/10 visible epoch586611807244800 полностью captured. Это дефект observer scope, не FAILED STOP.
Raw ledger2 naive conservation flags сохранены отдельно от объяснённого accounting; его jvmExit=false означает отсутствие wrapper marker.

BOOT3 +553A соответствует прежним8 Shirts21 и3 Pants28; новых earned rewards нет.
BOOT4 SP150→90→30 атрибутирован двум Iron Punch29:1/29:2 по60SP через actual learning goal/commit transitions
и stock class catalog/transaction postconditions. Прямой LearningEvidence callback не был установлен.
Второй learning intent остаётся ACTIVE, completed не объявляется. Death/penalty не наблюдались, expBeforeDeath=0.
В BOOT4 зафиксирован learning.skill.committed; финальный intent29:2 ACTIVE / step.retry_exhausted, выбран autonomous.learning; PvE за120s не возобновилось.

Исходные shots600→594→576 сохранены точно, но actual consume events в BOOT1/2 не записаны: B/D all-items gate NOT_PROVEN.
В BOOT3/4 shots не расходовались, positive native GameServer consume witness на JAR2 НЕ доказан.
Поэтому ни A–E целиком, ни FULL123 не считаются PASS.

## Проверенное исправление и проверки

Stock Item.changeCount исключает NONE из обычного item log; SoulShots использует именно NONE.
Добавлено только opt-in наблюдение actual locked decrement через существующий pickup observer, без нового writer/persistence/scheduler.
Exact actor/epoch/current RUNNING ticket; CANCELLABLE допустим, EARNED-only не заявляется.
Callback exception не меняет count, reward/loot counters или native owner; disabled observer не выделяет event array.
Terminal stack exhaustion обходит changeCount: общее terminal-consumption coverage не заявляется.

- Native evidence: RED9/11 (2 новых контракта), GREEN/final11/11, seed22002203.
- Actual ItemProcessType.NONE hook на existing unpublished Managed Player: baseline Item RED, final GREEN; DB/World/GameServer0.
- Life11632/32, Supply0719/9, population handoff6/6, Farming lifecycle2/2 — final GREEN.
- Commerce receipt/replay9/9, seed140014; paid-travel121/recovery121 и controlled STOP121 — PASS, DB-free.
- Private no-op capture contract:8 unchanged PRE-WORLD records accepted,4 changed-without-capture rejected; SQL0/World0.
- `ant compile-tests`:4 successful runs; standalone JAR builds2, все exit0; final owner profile38rules/288overrides сохранён.
- Общий `ant verify`/чужие DB suites: NOT_RUN по task, targeted gates не подменялись.
- Ошибки orchestration сохранены: первый Java -D parsing и один неверный commerce seed; corrected commands GREEN.

Независимый source review P1/P2=0 для bounded patch. Независимый evidence review не разрешил FULL при missing shots/PvE.
Final scope/encoding/protected/Geo guards и publication receipt сохраняются в evidence.
- mojibake-маркеры в изменённых файлах проверены: полный набор27,5files.
- escaped Cyrillic в изменённых файлах проверены:6patterns,5files.

## Доставка и ограничения

Private evidence: `%LOCALAPPDATA%/Temp/phantom-task123-20261104`; stage `Phantom-Rewarded-Continuity-123-0c6eab3f-ad8f-4ff3-a718-5b71dbfa851f`.
Сохранены original FAIL, seals/preimages, raw logs, G0/postflight, native/store/SQL/goal receipts, RED/GREEN и decoded ledgers.
Final post4: configured=false, retained entries0, observer released, own JVM/17783/SQL sessions0. Force kill/reset не применялись.
Git использован по прямому TASK123 разрешению: status --short/--porcelain=v1 -uall, rev-parse, symbolic-ref, for-each-ref,
ls-remote exact feature ref, clone --no-hardlinks, exact-path diff --stat/--check/--, scoped add/commit и non-force push HEAD:refs/heads/feature/phantom-world.
Процессный exact safe.directory у reviewer не меняет global trust. Main checkout Git использован только read-only для baseline/dirt inventory.
Code commit `d9ef092c6e9d36b475513f2f7ee9b289ae642bb1` содержит только4 code/test paths; report-only commit публикуется отдельно.
Exact Git invocations также включали `git rev-parse --show-toplevel HEAD`, `git rev-parse HEAD`, `git symbolic-ref --short HEAD`,
`git for-each-ref --format='%(refname:short) %(upstream:short)' refs/heads/feature/phantom-world`, `git ls-tree -r -z <baseline|HEAD>`.
`git diff --stat -- <code4>`, `git diff --check [<baseline>] -- <allowlist>`, `git diff --cached --check -- <report>` используют только перечисленные пути.
`git -c maintenance.auto=false -c gc.auto=0 commit --only -m "phantom(task-123): observe exact owned item consumption" -- <code4>` — scoped code commit.
`git add -- <report>` и scoped report-only commit не включают другие файлы. `git push https://github.com/kpCat/L2J.git HEAD:refs/heads/feature/phantom-world` — только non-force.
`git ls-remote https://github.com/kpCat/L2J.git refs/heads/feature/phantom-world` выполняется до push и после; source baseline должен оставаться exact c18e1d.
В обозначениях code4/report/allowlist используются первые4/пятый/все5 точных путей выше; в tool calls переданы явные пути.
Final publication SHA/result записывается в delivery receipt. Ни branch/reset/restore/clean/stash/force, ни изменение master/default не выполнялись.
Usage checkpoint перед публикацией:716119 tokens/4070s (~67.8min); стоимость инструментами не предоставляется.
>500k обусловлены PRE-CUT fixture corrections, независимым event accounting и второй парой после доказанного instrumentation gap.
Итог: безопасный диагностический fix и честный частичный native результат. Следующий Goal/Slice не начат.
