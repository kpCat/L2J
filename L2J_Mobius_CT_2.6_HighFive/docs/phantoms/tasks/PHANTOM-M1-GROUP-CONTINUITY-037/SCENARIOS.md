# SCENARIOS037 — покрытие связанной цепи

| ID | Проверка | Доказательство |
|---|---|---|
| S01 | Все 6 исторических ID036, exact4 input JSON, original evaluator | исходный 27 PASS/5 FAIL воспроизведён; ни один ID не потерян |
| S02 |173 first NATIVE_UNPROVEN | точная причина и clock; раньше первого sample≠новый отказ; missing≠NONE |
| S03 |absence/death/epoch transition vs FULL_SAVE | оригинальный continuity FAIL сохраняется, SQL вывод отдельно; календарь не патчить |
| S04 |подходящая цель→реальный hit→native reward→next target | baseline/current/target spawnGen, положительные counters без heals-as-work |
| S05 |temporary checkpoint PAUSED→RESUME, настоящая REVOKED | exact registrations обоих pools; closed new roots и чужой epoch не допускаются |
| S06 |unavailable target или locally outgrown→atomic replan→resume | old goal stopped, exact CAS/rebind/reload, настоящее новое действие; bounded attempts |
| S07 |mage MP low→native rest/stand→affordable skill | штатные MP/cost/regen;45/90/120 с не меняются; без бесконечной перерегистрации |
| S08 |два Player законно бьют одного NPC, target умер до cast impact | общая цель разрешена; no fake reward, no outstanding leak; обычный Player control |
| S09 |stale goal callback или epoch replaced | не двигает replacement, не сбрасывает old debt/unproven; native drain036 сохранён |
| S10 |две final natural группы по 6–8 на одном freeze | исходный CONTINUITY_V2 allPASS,2 primaries,360–420 с,all-ID/faults preserved |
| S11 |обе группы успешно сохранить/освободить | FINALIZED→World/AutoSave/lease absent; глобально retained=0, poolsLive до stop |
| S12 |035 full252 и Q15/native036 | original cap250/full hash/no unlawful slots; exactly-once quest award |

S04–S09 tests нужны по применимым изменяемым ветвям; неприменимая ветвь подтверждается
неизменным source + её существующей регрессией, не фиктивным новым тестом. S01/02/10/11/12
обязательны. Нет инъекции infinite MP, invul, altered NPC HP в product scenes.
Тестовые fixtures разрешены лишь на 037t под существующим точным DB guard.

Временные прерывания debugger допустимы в диагностическом probe, но делают затронутые
тайминги неподходящими для performance/deadline/continuity PASS. Финал без breakpoint.
