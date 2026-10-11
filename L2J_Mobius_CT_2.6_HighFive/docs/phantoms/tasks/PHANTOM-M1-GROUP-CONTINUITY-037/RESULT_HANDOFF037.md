# RESULT_HANDOFF037

TASK_RESULT=FAILED_GROUP_CONTINUITY. M1=OPEN; REAL_FINAL=NOT_RUN.
STOP_AUTHORITY=TASK037_CONTRACT. Следующая задача не запущена; disk audit PAUSED.

Две обязательные группы дали8/8 и7/8 CONTINUITY_V2 PASS. Третья диагностическая группа
дала4/8. GREEN_GROUP_CONTINUITY не достигнут: unresolved tail357 не скрыт новым cohort.
Лимиты full GameServer starts6/6 и full scenes3/3 исчерпаны. Новый Jar потребовал бы
новую пару сцен; продолжение за этой границей запрещено контрактом. Никаких новых fixes
без доступной приёмки, исправлений evaluator, heals, SQL substitutions или fourth scene.

BASE=0b47eea99c0d7d6190abc1eaa6de70996ca6dba9.
Committed tested source=7fafb0673c0d9ad87e6b417d3cd3876b61732045.
J1 GameServer SHA256=492e5bf953da4a42218a88d8c8049b4f98b7f9c5f3253d29d375d4b8d74f6f4c.
Production builds1/4. Работа начата2026-10-11T16:31:32Z; финальные JVM остановлены до18:39Z.
Модель GPT-6.1 Sol, Very High; субагентов0; существующий phantom-dev сохранён.
Remote experiment/m1-candidate007-observe008. Final publication SHA связывается внешним
publication037.json после exact-path commit и normal push, без повторного изменения Jar.

Frozen source/Jar/config/catalog/observer/evaluator зафиксированы до measurements в
FROZEN037_J1.json:88 config files на каждую базу, исключена только различающаяся DB authority;
173 catalog files. Observer source D2312DD4B74192D6A18817D4C9510462497360C8655EBB3AC3973C63D0CCB0A1;
agent FBDD9EA354BD79A3B329924C084701FE918B06808DB7C42851034FB9E697DCA6.
Evaluator pinned blob c441ecc621dc5936ba3b2ea7f8e9f6e6f74eb164 неизменен.
Полные hashes, scripts provenance, config/catalog fingerprints — в private receipts.

## Историческая группа036

Original archive SHA256=99cc1cfba46b98cdca949ce6a07fa1d6d9efe26aba9b5a3af4fde0434252abe8,
58,539,855 bytes,7,819 entries. Выборочное извлечение/hash/traversal checks выполнены.
Неизменный evaluator воспроизвёл все6 исходных строк. Forensics helper15/15 PASS.

| ID | Object / epoch | Исходный результат и first cause boundary | Классификация и предел доказательства |
|---|---|---|---|
|27|268484461 /457715115727600|PASS;46 cycles; gap27.916с;tail6|Контроль: весь lifetime, earned progress, FULL_SAVE.|
|28|268484462 /457715135780600|FAIL lineage/cycles/tail; first absent63.7648022с;1 cycle|LIFETIME: последнее sampled состояние alive; prewarm=false, region delta[2,3,0]; presence.no_current_local_demand записан64.8743с. Не считать эту позднюю запись точным timestamp удаления.|
|40|268484566 /457715172606200|FAIL lineage/tail;first absent93.4491214с;2 cycles|LIFETIME:alive,prewarm=false,delta[1,3,0];presence.no_current_local_demand записан102.9705584с.|
|57|268485321 /457715193072800|FAIL lineage/cycles/tail;first absent92.3212668с;1 cycle|LIFETIME:alive,prewarm=false,delta[1,3,0]. Точный межкадровый producer удаления UNKNOWN.|
|106|268485746 /457715210629400|FAIL lineage/tail;first absent92.3212668с;2 cycles|LIFETIME:alive,prewarm=false,delta[1,3,0]. Точный producer UNKNOWN.|
|173|268486289 /457715257617300|FAIL lineage, NATIVE_UNPROVEN,cycles/tail/debt;0 cycles|GAMEPLAY+LIFETIME: first PHASE_DEADLINE sampled92.3212668с, firstNanos457838258453300;REGEN since457718258444400,deadline457838258444400=120с. MP достаточна для1177/1, damage0. Причина отсутствия полезной атаки и точный producer удаления UNKNOWN.|

Все пять — за source prewarm envelope World.SHIFT_BY=11 (максимальный region delta2;
couldKnow1). Native-death-control исходной сцены observedDeaths=0; календарь оставался online.
Это физическая потеря observer locality, а не доказанная смерть или календарный departure.
Не утверждается точное60с retention совпадение. FULL_SAVE оригинальных6/6 отдельно проверен;
native epoch/counter corruption, pending store и SQL corruption до departure не наблюдались.
Отсутствие этих ID в final037 не превращает их исходные FAIL в PASS.

## Связанный fix и причинное доказательство

Изменён один production файл: PhantomVisibleAutoPlay.java. Stop/replan отзывает registrations,
но сохраняет debt в inactive Session одного Player+epoch. Новый goal/revision не обновляет
useful clock; новый Player/epoch не наследует старый debt. Terminal lifecycle забывает Session.
Native evidence, ownership/store/drain guards, core AutoPlay/AutoUse и inventory035 неизменны.

Current causal probe148: epoch466898325989400,damage0; usefulSince был сброшен
466951570425400→467042072712100. Это доказанный adapter defect, не универсальное объяснение036.
G37 same-goal stop/resume иG38 factual local replan/CAS: valid RED0/2, GREEN2/2 на J1.
Проверены registry resume, отсутствие fake damage, исходный90с debt expiry, terminal release.
Первый G38 fixture получил правильный foreign-write rejection; это INVALID setup сохранён.
Full0328/8 PASS. Ближайшие existing negative routesS05–S09 включены в текущую matrix/native/resource
регрессию. Касты на общую цель, умершую до impact, не запрещены и не считаются fake reward.

## Final scenes: полный denominator

Natural premeasurement rule — ENROLLMENT_RULE037.md; primaries66+native caster253 до measurements.
Orc ingress(-50845,-113715,-168), native pre-World resolver, без ручной подмены admission,
readiness, calendars, HP/MP/EXP/SP/XYZ/gear. Original evaluator problems=[] во всех трёх сценах.
NativeFirstUnprovenReason=NONE в финальных текущих frames; это не отменяет остальные faults.

| Scene / seconds | ID (primary*) | Cycles | Max useful gapс /tail120 reward | CONTINUITY_V2 |
|---|---|---|---|---|
|037b /381.5388731|66*;166;170;253*;271;323;357;392|15;29;23;16;24;15;25;14|78.818/3;30.645/7;39.878/8;49.310/4;39.878/8;37.632/4;78.818/3;42.706/4|8/8 PASS|
|037c /381.4656675|66*;166;253*;271;323;335;341;357|26;20;17;24;22;19;26;21|22.912/7;71.448/6;83.333/2;52.206/6;40.757/3;31.730/4;39.755/7;118.193/0|7/8 PASS;357 FAIL TAIL120_REWARD+USEFUL_PROGRESS_DEBT|
|037c4 /381.202835|253*;466*;470;566;571;584;645;687|0;19;9;19;25;10;23;25|Per-epoch tables в unchanged raw;253 gap379.236/tail0; changing epochs не суммируются в один lifetime|4/8 PASS;253 cycles/tail/debt;470/584 lineage;566 lineage+tail|

Точные objects/epochs, per-segment damage/kill/reward/target/EXP/SP deltas, gaps, tails,
rest45 и fault clocks находятся в исходных continuity-v2.json/all-samples.json и
final-report-data037.json. Legacy-strict также сохранён: он не заменяет CONTINUITY_V2.

У357 вC native damage прекратился после21 complete cycles. Premain trace285–330с:
ATTACK+movement без damage, затем IDLE. Repair30с и replan90с не сбросили debt; mapper корректно
сохранил FAIL. Native read-only reload:load385641/max12213000,disabled=false; overload не причина.
Конкретный producer tail357 остаётся UNKNOWN. Loot-only starvation не подтверждён trace.

Третья сцена диагностическая, на том же J1. До START66 и166 стали DEAD после сохранения/
фонового продолжения. Они не лечились. Setup66 отклонён;326 — lowest READY в той же точке
по сохранённому census, до результатов. Один ошибочный вызов с Away-only параметром исправлен;
перед новой START подтверждено retained=[]; все финальные identities новые.357 admission
не включил, поэтому третья сцена не доказывает устранение его причины.
Дополнительный bounded read-only Tail037 sampler всех8 disclosed, frozen collector не менялся.
253 остаётся IDLE/travel.native_segment_obstructed:160 same-epoch reads, рядом alive farm NPC;
107 reads имеют see+move=true хотя начал с geo=false. Это препятствие текущего travel path,
а не доказательство постоянной недоступности всех NPC. Exact path/recovery producer UNKNOWN.
470/584/566 имеют реальные native deaths и последующие новые epochs (native-death-control.tsv).
Counters не сброшены в том же epoch. Death/lineage FAIL не подавлены.

## Conserver и регрессии

037b и037c: FULL_SAVE8/8 каждую; sealed native arguments, inventory hash/full payload/native
context/SQL совпали, FINALIZED→DETACHED, World/AutoSave/lease отсутствуют, outstanding/timers0.
262 и301 native births соответственно: все terminal, RUN_END drained/dropped0.
36/45 в обеих сценах: полные252=249 INVENTORY+3 PAPERDOLL,limit250, exact четырёхсторонний
REGISTER/PREPARED/FINALIZED/SQL hash; native reload обеих stopped DB2/2, literal Jar-first.

037c4:934 births все terminal; global DONE/COMPLETE retained0 при ещё живых shared pools;
observer pending/overflow/refs/buffer0,4113/4113 exported. Terminal всех original8 подтверждён.
FULL_SAVE=NOT_VERIFIED: исходный verifier FAIL на566 SQL payload parity. Prepared version8292,
ожидаемое sealed commit8293, post-read SQL8294. Native payload self-consistent;7/8 SQL payload
совпали. Позднюю SQL версию не подставляли вместо sealed receipt. Точный producer изменения
между sealed save и post-read UNKNOWN; это не доказанная corruption и не объявленный PASS.

На одном frozen committed J1: matrix93/93 (18 routes); lifecycle02711/11;native0369/9;
Q15 1/1;inventory0356/6;projection4/4;capacity2/2;model9/9;SQL11/11;compact2/2.
27 provenance records имеют тот же Jar SHA. Focused0372/2 и full0328/8 отдельно PASS.
Initial capture/fixture/compile/authority-guard failures и все repeats перечислены в evidence index.

| Gate | Итог |
|---|---|
|G0|PASS: exact base/remote, source/packet/input checks, existing own worktree|
|G1|PARTIAL: все historical ID классифицированы, precise UNKNOWN сохранены|
|G2|PASS для единственной исправленной debt ветви; полного gameplay исправления не заявлено|
|G3|PASS037b|
|G4|FAIL037c; дополнительная037c4 тоже FAIL|
|G5|PASS037b/c;037c4 FULL_SAVE NOT_VERIFIED, independent drain/terminal PASS|
|G6|PASS current J1|
|G7|Cleanup PASS; exact commit/push/archive binding — external publication receipt|

Own JVM0, четыре037 DB/worktree/MariaDB сохранены; force stops/deletions0.
Публичный scope — один production файл, два test файла и собственная task-family bounded exception.
Git использован по прямому разрешению user/GIT.md; полный журнал GIT_COMMANDS037.md.
Mojibake-маркеры в изменённых файлах проверены; финальная hash receipt — final-scope037.json.
Escaped Cyrillic в изменённых файлах проверены отдельно в той же receipt.
Архив TASK037-review.zip:113467888 bytes, SHA256502aa769ddebc410e33003f14e825f0ef7a9561838188938af50509e26bb0509;
24713 ZIP entries проверены по size/SHA. Точный final publication binding — publication037.json.
