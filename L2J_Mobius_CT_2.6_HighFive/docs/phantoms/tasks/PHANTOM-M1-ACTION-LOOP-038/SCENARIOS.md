# SCENARIOS038 — переносимый механизм, не особые357/253

| ID | Сценарий | Что доказать |
|---|---|---|
|S01|Direct normal monster|native поведение не ухудшилось; target/actor keys exact|
|S02|Domain-valid NPC без direct LOS/хода, known native обход есть|discovery не теряет цель; route→action position→настоящий native hit|
|S03|Цель погибла до нового offensive root|новый root не выдаётся; следующий eligible target либо stock corpse action|
|S04|Цель погибла после accepted cast|native законно завершает/no-op; нет fake reward, ни вечного pending|
|S05|После blocked stand-point Player уже имеет fresh legal action|старый failed путь не закрывает свежую opportunity; guards/anchor receipt сохранены|
|S06|Угол/ограда/U-shaped обход|путь может временно удаляться от target; MOVE не сквозь obstacle|
|S07|Длиннее одной direct local-segment задачи, узкий коридор|повторно используем существующий corridor/search; factual waypoint progress|
|S08|Одинаковые XY, другая Z/этаж; different instance negative|нет ложного ARRIVED/attack; слой и instance не смешаны|
|S09|Дверь/цель изменилась после path result|fresh validity; own route replan или безопасный отказ, не stale MOVE|
|S10|PLAYER/epoch/goal/revision/target generation заменены|old result discard; no writes/registration in replacement|
|S11|AutoPlay+AutoUse+route готовы одновременно|один controller writer; await не блокирует shared worker; only exact target|
|S12|Checkpoint/queue busy/root revoked|PAUSED≠REVOKED; earned finish; one deadline; no busy-loop|
|S13|Два Phantom атакуют одного моба|cooperation разрешена; skill may lose race без выдачи fake rewards|
|S14|Melee vs actual known magic range/resources|общий action-position contract; нет forced fists/heal resource injection|
|S15|Native pickup и доступный native Sweep|ни corpse,ни item не теряются из-за attack-only global predicate|
|S16|Sameepoch stop/replan/arrival|original useful debt037 не обновляется от MOVE/запуска controller|
|S17|Три неудачных local варианта/срок попытки|существующий bounded higher replan; не бесконечная смена revision|
|S18|Natural A/B и global stop|all enrolled, native counters, FULL_SAVE/drain; mode/request/session refs released|

S02/S06/S07 native геометрия на фактических geodata из own runtime; независимая проверка
наличия пути ДО assertions. Тестовые NPC/позиции только через существующий TEST gate,
не модификация actual natural группы. S08 negative не требует внедрения instance gameplay.
S13/S14 минимум два actual native caster/weapon lifetimes, без заявления «все классы готовы».
History357/253: требуется таблица before/after с exact evidence, либо UNKNOWN/not reproduced.
S18 не отменяет отрицательные исходы ранних попыток. Death/rebirth отдельная реальность,
а не лицензия исключать ID из stable CONTINUITY_V2.
