# TASK126 — PARTIAL; STOP126

Дата: 2026-11-04. Полноценная rewarded Party3 по всем A–F **не достигнута**.
Baseline GitHub `kpCat/L2J`, `feature/phantom-world`: `b4d21f51946ea5df06071bd14a0f647b0866faac`.
Работа выполнена в отдельном physical no-hardlinks clone; основной checkout с WIP109 оставлен неизменным.
Прочитаны GOAL.txt, TASK.md и все восемь Markdown-файлов пакета. R2/R3 не создавались; TASK127 NOT_STARTED.

## Native acceptance на финальном JAR

| Gate | Вердикт | Подтверждённый результат / raw evidence |
|---|---|---|
| A: PARTY | PASS | В №4 и №5 три distinct NORMAL World Players, штатные INVITE→ACCEPT: delivered=2, accepted=2 в каждом JVM. Leader+DPS/Bishop healer/DPS, реальные stock roster и claims. `P:party4/5.log`, `party-claims4/5.log`, `party-diagnostic4/5.log`. |
| B: PVE | OPEN | Native rewarded monster kills=0; EXP/SP/loot gain=0. `farm-admission4/5.log`: ready=true, selected.empty, 47 samples в каждом окне. Нужных ≥2+1 kills нет. |
| C: HEAL | OPEN | Native buffs и MP costs наблюдались; qualifying learned Heal с доказанным HP outcome не наблюдался. Body of Avatar/regen не засчитаны как Heal. `P:skills4/5.log`, `party4/5.log`. |
| D: DEFENSE | NOT_EXECUTED | №2 отказал до атаки: hostile human был нелокален. После штатного walking в №3 подготовленный human стал dead; причина смерти UNKNOWN. В №4/5 он уже dead; hostile-command.issued отсутствует. Phantom targets/attacks не выдавались. `P:failure2.log`, `enemy3/4/5.log`. |
| E: PK/KARMA | OPEN | Настоящей Player PK-смерти от phantom нет; pkCount, PvP count, karma всех трёх=0. Положительный PK witness или выдуманный policy refusal не заявляются. `P:stored4/5.tsv`. |
| F: RESTART | PARTIAL / gate OPEN | №4→№5 — та же TEST DB/JAR/data/config/helpers; before5=stored4 byte-for-byte, кроме UTC header. IDs/gear/skills/EXP/SP/claims сохранены, roster восстановлен через новые штатные invites. Но нового rewarded kill нет; чистый безошибочный LOAD также не доказан. `P:before5.tsv`, `stored4/5.tsv`, `store-native4/5.log`, `stop4/5.txt`. |

Финальный JAR SHA-256: `8920d698fd1feef34814c6cdc8a30589a1b69906be2eed0cc02f99d58c463498`.
Profiles/objects: `1/268480990` class2, `2/268481012` class16, `3/268481033` class3; level55.
GroupID: `DF0BBB536DC6A73A026C21491017ECA581CB7C554D66DF70F15885FDEFA3580D`.
Claims generations: №4 3→5, №5 5→7 с lifecycle/STOP reconciliation; socket Party не подменялась сохранённой фикцией.
World epochs №4: `626583959621300 / 626584313419000 / 626584647151900`.
World epochs №5: `627093328611500 / 627094373008300 / 627094712052100`.
EXP всех трёх остаётся `70853089`, SP=0, wallet=5000; rewards и loot не приписывались background counters.

## PRE-CUT и пять запусков

G0: privileged OS identity attestation; MariaDB localhost:3308 с проверенным PID/image/datadir/grants/full session visibility, foreignSessions0.
Создана только новая `l2jmobiush5_phantom_test_126`: stock121 SQL files/203 statements, 18 historical DROP исключены.
UUID: `bc5c30db-030e-450f-81ed-3daeec24dfef`; stage owner+SYSTEM ACL, exact source/JAR/SQL authority seals.
До первого GameServer сохранены PRE-CUT_SCENE_READY, PRE-CUT_ROLES_READY, fixtures/readiness и witness-preflight.
Три обычных NORMAL shell Player с matching farm.background/ACTIVE calendars, legal B equipment/shots, изученным native Heal; Party/claims до CUT отсутствовали.
Stock target20573 у `74697,-1273,-3648`: EXP/SP/HP/Geo/LOS/path и роли проверены game-free. Leader wound был PREPARED, не combat damage.
Два distinct ordinary disabled-login human: neutral observer и aggressor, без GM/invulnerability. Первоначально живые и локальные.
HP consumables1061 не были подготовлены: действительный low-stock intent увёл акторов в supply journey. Риск handoff этого journey с Party preflight не выявил.
После CUT: fixture SQL writes=0, grants=0, ручных phantom HP/MP/XYZ/goal/Party/target/karma edits=0; принудительной phantom materialization нет.
Humans двигались только через stock MOVE_TO. Geo PASS 215/209 route waypoints не доказывает безопасность от damage; смерть aggressor не приписана phantom.

| Start | JAR | Окно / исход |
|---|---|---|
| 1 | `6fbd685d…913b26e` | Ready20:27:44Z, 4min. Supply walking, native Party2 в конце; productWitness=false; clean STOP/STORE/postflight. |
| 2 | тот же | Ready20:37:47Z, ~2min. Native Party3; maxMP2324/2224 incident; hostile legality FAIL до команды. Process exit0 не означает acceptance PASS; оригинальный failure2.log сохранён. |
| 3 | `3fecaf68…f2d7fd` | Ready21:24:17Z, 4min. Повторные HP/maxHP3321/3033 LOAD отказы; bounded capture переполнен. Conservation FAIL сохранён; clean STOP и observed postflight. |
| 4 | финальный | Ready21:37:25Z, 4min. Первый maxHp3321/3033 + hashMatch=false incident реален; затем native retry и Party3. Rewards/Heal/hostile witness отсутствуют. Clean STOP/STORE/postflight. |
| 5 | тот же финальный | Ready21:45:54Z, 4min. Native Party3 и buffs; rewards=0, productWitness=false. Original LOAD incident archive не снимался целиком, отсутствие ошибок не заявляется. Clean STOP/STORE/postflight. |

Actual GameServer starts=5/5; LoginServer starts=0. Все STOP: drained=true, stopFailed=false.
Перед каждым следующим JVM совпадают предыдущий stored и новый before SQL ledger (кроме UTC header), в том числе при смене candidate.
№4/5 capture завершился без capture.failed; полнота LOAD incident archive не подтверждена. Это не доказывает все gameplay gates.
Stock startup создавал offline merchants только внутри новой TEST126 DB; они не считаются phantom cohort или loot recipients.

## Исправления и оставшиеся причины

1. Assigned farm support больше не забирает ASSIST_TARGET/PROTECT_MEMBER offense owner у своего support cast. Damage members сохраняют combat; самостоятельная PvP defense остаётся отдельным существующим owner.
2. ENGAGE PARTY_DEFENSE отменяет owned combat при утрате/истечении/смене exact counterpart authority. Уже terminal result не перезаписывается; обычные другие causes сохранены.
3. Pre-STORE cleanup больше не снимает learned passives: иначе Boost Mana213 терял +100MAX_MP и STORE записывал2224 вместо2324. После STORE все эффекты завершаются под существующим SEALED teardown, затем stock deleteMe.
4. Phantom load теперь повторяет пропущенную stock login коррекцию `_originalHp/Mp/Cp` из SQL после restoreEffects, до admission/World. Body of Avatar1311 иначе повторял onStart healing при каждом LOAD. Exact managed owner/current ticket/pre-World/once/dead guards обязательны; ordinary login не менялся.

Это production stock lifecycle completion, не harness-вставка HP. Background authority/hash guards, XML skills/items, rates и PK rules не ослаблялись.
Открытый связанный P2: Party recruitment/recovery допускает cohort с accepted paused maintain.supplies; Party блокирует supply resume и solo return travel.
Farm orchestration выбирает только local targets (радиус2000); исходный field теперь ~15.1km от Party. Group return route не запрашивается; seed _travelContinuations сам по себе недостаточен, solo travel запрещает isInParty.
Для уже восстановленной Party нужен явный handoff существующим supply/group-route owners. Unsafe обход этих запретов и ручной возврат исключены; эта часть не исправлена и блокирует FULL.
Открытый restart gap: active buff сохраняется через pre-cleanup effect snapshot, canonical maxima снятого active buff ниже; refresh maxima отклоняется при изменившемся knowledge hash.
Причина knowledge hash change — UNKNOWN. Проверенные writers сортируют факты; выдуманный nondeterminism fix не внесён. Финальный JAR не объявляется полностью устранившим все console incidents.
Client visibility QA и 5000/128 soak — PENDING; не запускались.

## Проверки, review и scope

Actual focused RED: healer offense owner; passive MAX_MP removal; skipped final SEALED passive disposal; replayed restore-time healing. Все четыре затем GREEN.
PvP authority predicate проверен шестью focused cases; existing-baseline runtime RED для него не заявляется.
Финальный focused set: Party tactics10/lifecycle23/state-recovery14, PvP corrective6/policy2/combat13/party-help1/restart1, Combat72 — PASS.
Existing native console runner: restore126, cleanup126, consume123, delivery-ordinary, reentry — PASS; DB=false/WorldPublished0.
Existing Store109: lineage positive4/negative7, native persistence5 — PASS. Поддержка STORE nonzero-karma не расширялась без реального PK witness.
Ant compile-tests=8 (7 successful, 1 compile failure с неверным FQN fixture); standalone ant jar=3, все successful. Ant verify=0 по task DB-safety contract.
Launcher suites=28 runs: 27 GREEN +1 expected RED; successful standalone console runs=12; отдельные intended RED сохранены.
Дополнительные setup failures: fixture access lazy DB initialization fail-closed, wrong passive scope, heap512m OOM, неверное имя launcher mode; это не product RED. Исправлены/перезапущены только нужные routes; raw logs сохранены.
Independent exact-delta review: P1=0/P2=0 после исправления найденного SEALED cleanup P2; новый restore exact3 повторно P1=0/P2=0.
Это оценка изменённого кода; открытый product P2 и runtime gaps выше не скрываются как review PASS всей системы.

Exact allowlist — восемь code/test файлов плюс этот отчёт (bounded9):
`java/org/l2jmobius/gameserver/model/actor/Player.java`;
`java/org/l2jmobius/gameserver/model/actor/holders/creature/EffectList.java`;
`java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java`;
`java/org/l2jmobius/gameserver/phantoms/pvp/PhantomPvpService.java`;
`java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializedPlayer.java`;
`test/java/org/l2jmobius/tests/phantoms/PhantomPartySuite.java`;
`test/java/org/l2jmobius/gameserver/phantoms/pvp/PhantomPvpCorrectiveSuite.java`;
`test/java/org/l2jmobius/gameserver/phantoms/player/PhantomPickup109Checks.java`.
Новые AI, threads, schema, dependencies, публичные configs и Studio не добавлялись. Общие Player/EffectList затронуты только доказанными lifecycle causes.
Final guard: exact9, original protected1235, original/copied Geo203 и runtime29087; два byte-identical receipts `E:verifier-final1.json`, `verifier-final2.json`. Всего3 guard runs:1 до doc review +2 финальных.
Mojibake-маркеры в изменённых файлах проверены (полный27-marker набор).
Escaped Cyrillic в изменённых файлах проверены (все6 regex, отдельно от mojibake).

## Сохранность, raw evidence, telemetry и публикация

MAIN/PLAY, release057–061, старые TEST DB, WIP109, OwnerConfig и общая MariaDB configuration не изменялись; protected preimages/Geo SHA сохранены.
Финальный postflight5: own JVM0/listeners0/SQL sessions0/online0, SELECT-only; SQL service identity неизменна. Никакого force kill/lease reset/DB reseed.
P = owner-only stage UUID выше, каталог private; E = приватный companion task126. Полные local hyperlinks находятся в E:EVIDENCE_INDEX.md, не публикуются вместе с DB secrets.
Manifest `E:evidence-index.json` (81 raw artifacts), SHA-256 `445e24008c9f3529e81d02042bc3fe449bf45b82d6500a099a4cf285042408eb`.
Exact SQL/identity/capture/build/review pointers: `E:audit-summary.json`, `test-run-ledger.json`, `candidate3-bindings.json`, `INDEPENDENT_REVIEW126.md`; P:before/stored/stop/postflight1–5.
Первичное чтение: task package, AGENTS/README/status/workflow/efficiency; расширение — ближайшие Party/PvP/Combat/STORE/native effects owners и private122/123/125F аналоги. Количества opened files/search calls не инструментированы и не выдуманы.
Goal telemetry на снимке отчёта: 1,423,263 tokens; 6,659sec (~1h51min). Итоговый closure receipt хранится отдельно после публикации.
Превышение500k связано с native isolation/STORE writer audits, двумя реальными restart failures и независимыми review; были также избыточные diagnostic outputs. Токены не являются продуктовым критерием успеха.
Code commits: `21cffba4d0118ef457ec9f9c76b3d34925ae57c7`, `881b6f3a5ad52d8464f3107281a8fd0b0c6ca336`.
Код опубликован обычным non-force push в feature/phantom-world (`b4d21f5→881b6f3`); отчёт публикуется отдельным scoped commit, final remote receipt — E:publication-receipt.json.
Git разрешён user/task§17: physical clone; bounded rev-parse/symbolic-ref/status/worktree inventory/ls-tree/merge-base/exact diff; live `git ls-remote https://github.com/kpCat/L2J.git refs/heads/feature/phantom-world`.
Запись: `git add -- <exact4paths>` и `git commit -m ...` дважды; отчёт — exact1path; `git push https://github.com/kpCat/L2J.git HEAD:refs/heads/feature/phantom-world`. Force/rebase/reset/clean/stash/add-all/master не применялись.

STOP126; TASK127 NOT_STARTED. FULL126 не заявлен.
