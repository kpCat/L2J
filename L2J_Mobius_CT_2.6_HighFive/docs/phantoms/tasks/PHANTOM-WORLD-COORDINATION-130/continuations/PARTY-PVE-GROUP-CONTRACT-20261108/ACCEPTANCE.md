# Приёмка продолжения TASK130 — реальные product gates

Это дополнение к существующему `docs/phantoms/tasks/PHANTOM-WORLD-COORDINATION-130/ACCEPTANCE.md`; исходные требования A–G не удалять. Пакет **не автоматически меняет** статус FULL130 и не разрешает запуск TASK131.

## Gate 1: CONTRACT / MODEL
- Утверждённый ±4 Group FARM для всего состава, 2–9; Solo invariants unchanged.
- Один явный PARTY source/search path во всех перечисленных downstream methods, без индивидуального Solo viableTarget; NPC/route/Geo/GK/Goal fences сохраняются.
- Role semantics: guardian/healer/DD/spoiler и multi-role contribution; class name недостаточен для native-ready; административный лидер может быть tank/support; 2-персонажная Party допустима, если боеспособна.
- Deterministic red→green для исходного blocker70/72 и минимум 16 отрицательных тестов, включая stale owner и no-free-heal/aggro.
- Полная проверка старого MODEL и отсутствие regression старой Solo/Background/Commerce/Presence/PK, F7/F8. Не меняется core stock Heal.

## Gate 2: SQL/ownership
- LogicalParty durable 0 World, groupId/leader/goal/roles/cursors survive separate-JVM restart. CAS group claims exactly once; 2/3/9, conflict, stale, inventory bounded.
- No phantom awards for scene-prep. Existing TEST130 receipts and completed historical authority exact, no duplicate EXP/SP/loot/cursor; Supply/Learning/Career/Commerce/STORE obligations fail closed.
- Existing bounded Scheduler only, no new global worker or per-pulse full scan 5000; 250 offline merchants disjoint.

## Gate 3: REAL NATIVE GameServer, final clean JAR
- At least two live legitimate managed Player in actual stock Party via invite/accept with actual leader; group objective from authoritative targets in common ±4 band; physical Geo movement, no teleport.
- At least one actual group monster encounter, real damage and earned EXP/SP or loot, attributable to members of current Party; observer verifies actual stock event, not mock/offscreen/reward roll.
- If healer is used in final native acceptance: genuine Phantom Heal with real HP effect and MP debit; human Heal regression. No claim from simply seeing a learned skill.
- No forced Party membership for humans; no eviction of Party/Store/trade/inflight; native guardian aggro only after real cast/AI, no free mitigation.
- Graceful no-World dematerialization/STORE retains durable group (not only cast-off Player object), receipts do not double.

## Gate 4: RESTART
- First vertical and exact postflight PASS, then separate GameServer start SAME DB+SAME JAR; new useful Party action (not just a loaded group or empty startup) + exact saved totals and claims, no native duplication.

## Gate 5: Publication/quality
- Source-JAR parity, signed/hashed manifests, normal stock STOP, protected207 exact, Geo203, no stale JVM/listeners/foreign TEST SQL; production P1/P2=0, F7/F8 verified.
- Scoped regular commit/push ONLY WIP until all original TASK130 A–G pass; feature remains untouched otherwise.
- Report actual cost/product delta; finish with independently verifiable HEAD and changed paths.

## честный статус
- `MODEL/SQL PASS + NATIVE 0` => PARTIAL. Native Party without reward => PARTIAL. Group reward without safe STORE/restart => PARTIAL. Restart only => PARTIAL.
- Source/SQL authority recovery for 70/72 ALREADY PROVEN in previous iteration: не записывать заново как «новая полезная игра».
- `TASK130 FULL` только когда original A–G + this companion acceptance are satisfied on final source/JAR.
- Native Spoil/Sweeper — ценное расширение, но его полная групповая интеграция остаётся TASK131, если не получена реальная самостоятельная доказанная штатная цепочка.
