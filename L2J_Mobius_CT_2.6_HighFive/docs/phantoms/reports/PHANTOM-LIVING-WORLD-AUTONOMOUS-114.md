# PHANTOM-LIVING-WORLD-AUTONOMOUS-114

Статус: native SUCCESS. Финальная связанная версия доказала NORMAL PvE → STORE → sameDB restart → новое полезное PvE.

Baseline: `236b965a116bbc79cccbef78b04c628225a629cb`.
Независимый candidate: `D:\Codex\worktrees\phantom-living-world-114\L2J-phantom-world`.
Исходный mixed checkout не редактировался. Его WIP109 и прочие уникальные файлы сохранены.
Task package прочитан из исходного checkout; historical task/report artifacts не переписывались.

## Безопасный scope и G0

- Единственная созданная БД: `l2jmobiush5_phantom_test_114`; повторного provision/reset/reseed не было.
- Installation ID: `7fa6e3bf-8c48-4dff-8432-d7c9f9f46641`.
- Полный первоначальный G0: опубликованная автоматическая privileged OS-аттестация,
  exact MariaDB 11.4.13 PID/creation/binary/config/datadir, SQL metadata/grants,
  отсутствие foreign sessions, disjoint private shadow, sealed source/JAR, Geo203.
- OS identity PASS отдельно не использовался как разрешение на SQL.
- Создание: 121 stock SQL-файл, 203 statement; 18 stock DROP TABLE пропущены.
- Authority row фиксирует initial source/inputs; subsequent native versions не меняют initial authority.
- Перед каждым run/post: fresh attestation, exact DB identity, foreign JVM/TCP/SQL guards,
  private ACL/physical input seal и exact hashes изменённых входов/JAR.
- После остановки: own JVM/listener отсутствуют, global foreign SQL sessions=0, characters online=0.
- Loopback game port 17777; population24/active8/materialized8; merchant bootstrap OFF.
  Calendar, native rates, Geo, combat/loot rules не подменялись.
- Один законный pre-cut test-human без GameClient создаёт только current local demand.
  Phantom XYZ/goals/EXP/SP/items/Adena не инъектировались; ACTIVE не форсировался.

## Production-причины и изменения

Девять production Java-файлов, два test-файла и этот отчёт: bounded exception из 12 файлов
для одной причинной lifecycle-цепочки; новые подсистемы и тестовые среды не создавались.

1. `PhantomBackgroundSimulationPolicy`, `PhantomNativeContext`, `PhantomHistoricalBackgroundService`:
   после штатного historical level-up бит128 запрещал rewards, но completed PNC больше
   не запрашивал canonical refresh. Теперь exact-owner native re-attestation возобновляет
   cursor; reward permission, pending STORE и claim/goal/CAS guards сохранены.
2. `CreatureStatus`, `PlayerNativeEvidence`: passive REGEN ошибочно продолжался при настоящем
   native движении и истекал. Нужны moving + наблюдаемое смещение XYZ; застрявший MOVE_TO
   без смещения сохраняет прежний horizon. PHASE_DEADLINE и sticky invalidity не ослаблены.
3. `PhantomVisibleFarmTravel`: completed search budget ошибочно считался transient service
   failure; после трёх попыток навсегда оставался exhausted. Теперь завершается точный
   unusable route attempt; queue/backpressure остаются transient.
4. Там же: actual-origin local WALK использует существующие native budget/checkpoints
   и production localWalkDestinations. Новая близкая farm-цель не требует сначала возвращаться
   в недоступный старый committed camp. Factual arrival подтверждается только native movement/STORE.
5. `PhantomSystem` и travel owner: preparation yield раньше останавливал только AutoPlay;
   старая farm ROUTE-фаза переживала передачу движения supply journey. Общий yield теперь
   закрывает точную route phase, сохраняя исходный attempt/deadline.
6. Historical owner: у temporary NATIVE_CONTEXT_HANDOFF отсутствовал выход после useful
   native cycle. Добавлен exact claim/epoch/proof guarded yield → quiet non-combat STORE.
   Cursor/rewards здесь не изменяются; pending history продолжает штатные interval transactions.
   Native14 подтвердил STORE и завершение history штатными interval commits.
7. `PhantomPopulationEcologyService`: native prerequisite попадал на historical retry-backoff
   и терял bounded preparation grant, без которого не мог продолжить принятый journey.
   Исправлена selection classification; slots/caps и backoff самих historical probes сохранены.
8. Там же: live owner полностью исключался из work queue, из-за чего существующая ветвь
   calendar-only commit не выполнялась и admission снова становился ecology_fenced.
   Теперь live owner без pending history получает только штатный calendar commit, без model rewards.
9. `PhantomMaterializationService` и travel owner: bounded события существующего выключаемого
   FlightRecorder различают NORMAL/HISTORICAL_BASELINE/HANDOFF и planned/issued native moves.

## Проверенная причинная последовательность

- Native1: historical level2 freeze; native2 после refresh cursor продолжился.
- Native2: ложный REGEN deadline при native WALK; после исправления движения proof чистый.
- Native3/4: NORMAL World для 6/21 доказан; search_limit блокировал farm после supply.
- Native5–11: локализация маршрута. Реальные BUY с COMMITTED receipts, native WALK,
  сохранение identity/inventory; нельзя считать это доказательством PvE.
- Native10/11: actual Geo position не в воде; до Gremlin18342 raw stock PathFinding=NULL,
  до других реальных low-level Monsters есть пути1/2/5. Отсутствие spawn опровергнуто:
  World содержит43586 Monsters, десять живых Gremlin в штатном лагере.
- Native12: profiles6/21 по одному native rewarded kill, EXP+105/SP+10 и loot1 каждый.
  Purpose HANDOFF, поэтому NORMAL success этим не закрыт.
- Native12: profile3 ROUTE deadline во время другого supply owner — causal RED для yield fix.
- Native13: profile3 epoch424328037762500, native kill2/reward2, EXP+1155/SP+110,
  loot3/farmCycle1, proof NONE/overflow=false. Награды6/21 пережили restart.
- Native13: frozen historical claim при useful HANDOFF — causal RED для completion fix.
- Native14: handoff3 → STORE → COMPLETE/current history; cursor6 также текущий.
- Native15: два NORMAL-профиля получили native EXP/SP/loot; restart16 выявил preparation/backoff deadlock.
- Existing ecology case18 дополнен одним assertion после batch demand: behavioral RED1/3PASS → GREEN4/4.
- Native17 подтвердил NORMAL continuation, но выявил live-calendar queue exclusion.
- Existing case14 дополнен фактическими live/release demand edges: RED1/4PASS → GREEN5/5.

## Финальная связанная версия: native18 → sameDB native19

Один JAR SHA256: `d47f00dae52ba56e84ef56c33362bdf9ef47e1f2654d97f072b7718f0271182b`.
Между18/19 source/JAR/harness не менялись, БД не пересоздавалась.
NORMAL provenance подтверждён `MATERIALIZE_SUCCESS=NORMAL/WORLD`, same object/owner/epoch;
измерены current human demand, ACTIVE/eligible/admitted и calendar cursor=requested horizon.

| Профиль / objectId | NORMAL epoch18 | Native18: damage/kill/reward; EXP/SP; loot |
|---|---|---|
| 6 / 268484316 | 426518054220500 | 2/2/2; +210/+20; 1 |
| 21 / 268484447 | 426547151208900 | 2/2/2; +210/+20; 13 |

Goal6: `356429146626418412`, revision27; goal21: `7245102585214954384`, revision74.
После штатного STORE/restart сохранены object IDs и earned progress6=2394EXP/240SP,
21=2814EXP/280SP, инвентарь, position и принятые child journeys.
В native19 profile21 NORMAL epoch`426971637513700`, тот же goal/revision75:
damage4/kill4/reward4/farmCycle3, native EXP+420/SP+40, loot3;
итог3234EXP/320SP — точное увеличение от сохранённого baseline, без повторного начисления.
Proof обоих финальных запусков: `overflow=false`, `firstUnprovenReason=NONE`.
Loot counters — реальные pickup events, не число выданных harness предметов.
COMMITTED commerce receipts используют прежние operation keys и conservation facts;
излишки Tunic426 продавались через native SELL, а не выдавались или повторно покупались.

## Проверки и воспроизводимость

JDK25.0.4.1, Ant1.10.17. Все Ant-запуски последовательные, защищённая поставка не затрагивалась.
Использован существующий private build override только для сохранения archived owner configs.

- `ant -f <private>/readonly-build.xml -Dbuild=<private>/build-v12 compile-tests jar`: PASS40s.
- `PhantomLivingWorld114Checks`: PASS6 focused sections; без DB/World.
- Один итоговый `Regression114.ps1` на v12: contracts114 PASS, itinerary068 PASS,
  evidence0229/9, ecology continuity1135/5 включая5000 identities и два найденных дефекта,
  background-model10/10, presence112 calendar/admission/demand/TTL negatives PASS; exit0.
- `native-context-handoff` DB fixture suite не запускалась: меняет персонажей и нарушила бы native campaign.
- `ant verify` не запускался: содержит DB-проверки вне разрешённого targeted scope.
- Existing `Run114.ps1 run/post -Iteration N`: iterations1–19 exit0/postflight PASS.
  Финальный19: own JVM/listener отсутствуют, global foreign sessions0, characters online0.
  Source promotion — прежний exact-hash path. После19 новых native запусков нет.
- Приватный evidence root: `%TEMP%\phantom-living-world-114`;
  stage: `%TEMP%\Phantom-Native-Life-114-7fa6e3bf-8c48-4dff-8432-d7c9f9f46641`.
  Сырые credentials, SQL grants, длинные логи и generated runtime не публикуются.

## Ресурсы и оставшиеся gates

Фактический Goal counter после native19 PvE: 786364 токена, 10575 секунд.
Расход чрезмерный; повторные routing-гипотезы до точной geometry-диагностики были неэффективны.
После уточнения пользователя новые scripts/wrappers/validators/environments не добавлялись;
переиспользовались существующие private инструменты: 5 Java, 12 scripts, 1 XML; новых сред после initial TEST114 нет.
Один standard JDK `jstat -gcutil` для exact own PID: GC1.256s за233s,
CPU155.84s/233s=0.669 ядра с startup; working set4.74GB, threads162.
35 существующих main18 samples: heap2.74–4.05GB; restart19 heap3.82GB/threads135,
overloadNORMAL, queueReady0/queueRejected0/shutdownFailures0, ecology persistenceWrites204.
Короткое наблюдение не показало явного runaway; physical cohort24 не является native soak5000.

Exact-source self-review: P1=0/P2=0; pending history не выполняется одновременно с World owner.
Исходные86 пересекающихся preimages проверены SHA256: mismatch0; защищённые runtime/DB не изменялись.
Финальный artifact scope guard: ровно9 production Java +2 test-файла +этот отчёт; чужих изменений нет.
Whitespace guard exit0. Scoped publication выполняется вместе с этим отчётом.
Git разрешён TASK114 и прямым пользовательским разрешением на scoped commits/обычный push.
Использованы bounded `rev-parse`, `symbolic-ref`, `status`, `worktree list`, `for-each-ref`,
`ls-remote origin refs/heads/feature/phantom-world` и exact-path `diff`.
Публикация: `git add -- <12 exact paths>`, `git commit -m "Fix autonomous phantom lifecycle and restart (TASK114)"`,
`git push origin HEAD:refs/heads/feature/phantom-world`; итоговый commit SHA сообщается в финальном ответе.
`git -c core.whitespace=cr-at-eol diff --check -- <exact paths>` учитывает проектный CRLF без изменения Git config.
Mojibake-маркеры в изменённых файлах проверены: полный набор, совпадений нет.
Escaped Cyrillic в изменённых файлах проверены отдельно: Unicode/XML patterns, совпадений нет.
TASK115 не запускался.
