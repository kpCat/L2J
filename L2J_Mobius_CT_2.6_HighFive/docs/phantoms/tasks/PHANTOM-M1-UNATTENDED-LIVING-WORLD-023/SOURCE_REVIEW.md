# Frozen implementation self-review

Production SHA: 1b71464a9e09a80dad890e2bfd39df0f3b5d3b96.
Production tree: 801590cd9bee413ecfdb3a19b759de0537a33cde.
Self-review выполнен inline: пользователь запретил субагентов.

| Контракт | Реализация и проверка | Ограничение |
|---|---|---|
| Fresh native participants | Exact Player/owner/epoch snapshot, all admission roots до первого native write; OPEN/SEALED/ordinary и three-actor native checks GREEN | Не доказана произвольная concurrent party churn на каждой stock writer строке |
| Delayed earned work | Старый generic captured-parent fence сохранён; fresh boundary только в прямых skill/hit/reward writers | Новое API не распространяется на произвольные delayed callbacks |
| Failed/draining published earned | Уже опубликованный EARNED может завершиться; newcomer не получает failed owner | Тест запрещённого partial writer и late failed earned drain GREEN |
| Cast identity | Exact MagicUseTask reference в двух stock cast slots; старый due callback не abort новый cast | Вторичное исключение самого abortCast отдельно не fault-injected |
| Reward evidence | Stock aggro/damage/reward formulas сохранены; actual HP contributors наблюдают death | Frozen reward roster включает party/group owners, которые могут оказаться вне reward range; возможен избыточный defer, safety не обойдена |
| Resource phase | Только actual positive offense закрывает bounded rest gap; ticks/pause не продлевают debt | Full-server сцена сохранила новый PHASE_DEADLINE; continuous gate FALSE |
| DEAD recovery | Exact COMPLETE RecoveryClaim, owned historical baseline refresh, текущие hashes и goal revision | Не выдаёт EXP/items и не выполняет ручной revive; guarded H15 GREEN |
| Shutdown | Ecology begin/finish, bounded10s wait вне system monitors, затем original configured drain | Full-server final drain проверяется отдельно; unit8/8 не заменяет retained0 |

Frozen build PASS. Source scope/diff/UTF8 guards PASS. Final dynamic6/6, closure9/9,
timers5/5, owned store3/3 PASS. Own causal RED retained for each repair round.
Stock active NPC, original scheduler и no fake REAL_LOGIN подтверждены fixtures/runtime.

Unresolved: composed aggregate has two INVALID stock-worker holds and after-all
infrastructure-thread failure. Это relevant M1 evidence, не KNOWN_UNRELATED_DEBT.
Existing TEST process-crash runner rejects schema metadata mismatch before fault fixture;
shared TEST schema metadata/guard не менялись. Real clone crash проверяется отдельно.
Natural SceneA: five actors retained, both primary fixed before baseline, all tail120=0.
SceneB: deterministic other factual spawn-area setup accepted, but only1 candidate after60s;
полный второй360–420s gate не состоялся. No scene thresholds relaxed/no failed actors hidden.

Actual SceneA residual cooperative failure: profiles411/663 recorded
NATIVE_EARNED_RECIPIENT_NOT_CAPTURED inside native NPC retaliation:
owned HitTask → Attackable.addDamage → AttackableAI.onActionAttacked/thinkAttack →
Creature.doAttack → strict runCombat/captured-parent fence. Thus lawful fixture GREEN
does not establish complete production native cooperative safety. Guard was not weakened.
Four allowed repair rounds exhausted; no fifth semantic repair or silent waiver.

Full frozen b stop retained5: exactAnchor rejects native position for281/459;
1176 native store commits EXP/SP, then POST_STORE topology register rejects NOT_RUNNING.
411/663 already had native incident. Four actual live totals exceed saved canonical rows.
Repeated b/c empty drain completes, but cannot erase earlier earned-store failures.
Real c crash completed exact-owned with dumps/no REAL; same-DB restart works, but selected
READY/MATERIALIZED/PENDING states and no nearby resumed actors leave full recovery unproved.
TEST final read-only audit: no recent profiles/harness rows or known primary/observer chars.
All task-owned JVMs stopped; original304 runtime files preserved; no emergency force.
