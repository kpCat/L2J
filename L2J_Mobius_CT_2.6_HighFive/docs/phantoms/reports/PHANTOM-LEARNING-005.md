# PHANTOM-LEARNING-005 — результат

**PARTIAL. FULL005_EVALUATOR не достигнут; игровое преимущество не доказано.**

Реализованы отдельный EVAL005 профиль существующего Director, writer-free V1 observation, строгий admission принятых V1 №1 / V3 №2, comparator фактических условий, immutable ledger и offline отчёт. Party DAMAGE остаётся V1 SHADOW в обеих ветвях; V3 ранжирует только HEAL. DRAFT8, trainer и автоматическое promotion не запускались.

Основная кампания eval005d: один объявленный бюджет 4 пар на режим, без повторного поиска победителя. Ранние smoke005a и smoke005b сохранены отдельными версиями; их несопоставимые пары не включены в fair sample.

| Режим | Arms | Fair completed pairs | Incomplete pairs | Unmatched | Native divergences в fair pairs | Вердикт |
|---|---:|---:|---:|---:|---:|---|
| SOLO_DAMAGE_V1 | 8 | 2 | 2 | 0 | 3 | INCONCLUSIVE |
| PARTY_HEAL_V3 | 8 | 1 | 2 | 1 | 0 | NO_POLICY_DIVERGENCE |

Все неуспехи входят в denominator attempts. Native kill и точные выбранные HP receipts не считаются полным Party damage. EXP/SP публикуются также отдельно по персонажам в terminal.json; один NPC kill дедуплицируется по objectId/spawnGeneration/instance. Loot, полные расходы и полная смертность без доказательства остаются UNKNOWN, а не нулём.

Для исходов EVIDENCE_REJECTED терминальный native kill/HEAL может быть подтверждён отдельно, но итоговая квалификация evaluator консервативно INCOMPLETE; оригинальные terminal.json сохранены. Это ограничение текущей реализации, не доказательство отсутствия игрового эффекта.

Pre-CUT: fresh native actors/обычный Spawn, не более 4 eligibility admissions нормального NPC с журналом champion/HP/XY; штатный RNG и spawn config сохранены. Координаты NPC нормализованы native teleport до CUT. Для Party использованы объявленные45/90 расстояния, Geo-проверка и штатное headless teleport completion; начальные травмы заданы только до CUT. После CUT нет setup, HP/MP assignments, принудительных skills или readiness override.

Ранний Solo12s sampling timeout был исправлен только для005 до основной серии на deadline encounter. Ни один ранний arm не переписан. Раннее G0 исключение явно разрешено пользователем и описано в BASELINE005_EXCEPTION.md: старый manifest и207 protected файлов не менялись.

## Проверки и сохранность

Canonical compile-tests/jar exit0;28 focused contracts;143/143 affected regression assertions;F7 exit0 (DB_CONNECTIONS=0, ACTORS=0, SQL_REPLAY=NOT_RUN);F8 14/14. Проверки выполнены на окончательном source/JAR без пересборки во время основной серии.

Реальный STOP и fresh Director/evaluator-worker RESUME: immutable arm/pair/frame SHA и summary не изменились. Завершённый native budget штатно не запускался повторно; это не заявляется как второй GameServer battle run. Свои процессы остановлены, свои порты свободны.

Mojibake-маркеры и escaped Cyrillic проверяются двумя отдельными final scope checks; точные результаты в COMMANDS.md. Входной17-file package сохраняет исходные hashes.

## G0–G8

- G0: PASS_WITH_EXPLICIT_BASELINE005_EXCEPTION; publication checked separately
- G1: PASS_ACCEPTED1_ACCEPTED2_ONLY; DRAFT8_NOT_ACTIVE
- G2: REQUIRED_NOT_MET
- G3: PARTIAL_NO_POLICY_DIVERGENCE_IN_PARTY_HEAL_V3
- G4: PASS_NATIVE_OUTCOMES_AND_UNKNOWN_MASKS_RETAINED; at least one completed matched pair per mode
- G5: PASS_INCONCLUSIVE
- G6: PASS_COLD_RESUME_IMMUTABLE_LEDGER; no completed native rerun
- G7: PASS_TARGETED_CHECKS_AND_MEASURED_NATIVE_COST_SCOPE
- G8: SCOPED_DELIVERY; remote SHA attestation in DELIVERY.json and final receipt

## Измеренная стоимость

PROCESS_METADATA включает весь соответствующий GameServer JVM (bootstrap, подготовку, encounter, drain), не только inference. workingSetBytes=null после exit не заменяется нулём; peakWorkingSetBytes — отдельный пик. Ниже фактические значения и доступные p50/p95/p99 policy timings; короткая серия не доказывает устойчивость на8 часах.

- Attempt 596531dec6b34558ac59ae341b4d59cc: `{"pid":85252,"startedUtc":"2026-11-12T12:22:11.0076316Z","sampledUtc":"2026-11-12T12:25:00.7778297Z","elapsedMillis":169770,"cpuSeconds":122.046875,"workingSetBytes":null,"peakWorkingSetBytes":4687896576,"measurement":"PROCESS_METADATA","exitObserved":true,"attemptId":"596531dec6b34558ac59ae341b4d59cc","finishedUtc":"2026-11-12T12:25:00.7843806Z"}`
- Attempt a01c2d3820cd4cd8886d29cd987fa984: `{"pid":73504,"startedUtc":"2026-11-12T12:07:00.2354235Z","sampledUtc":"2026-11-12T12:10:56.8369542Z","elapsedMillis":236602,"cpuSeconds":138.09375,"workingSetBytes":null,"peakWorkingSetBytes":4599164928,"measurement":"PROCESS_METADATA","exitObserved":true,"attemptId":"a01c2d3820cd4cd8886d29cd987fa984","finishedUtc":"2026-11-12T12:10:56.8429541Z"}`
- Attempt e1c678899287430dbcc938ba95ad563d: `{"pid":20828,"startedUtc":"2026-11-12T12:30:50.5868675Z","sampledUtc":"2026-11-12T12:43:05.3251545Z","elapsedMillis":734738,"cpuSeconds":208.0625,"workingSetBytes":null,"peakWorkingSetBytes":4687106048,"measurement":"PROCESS_METADATA","exitObserved":true,"attemptId":"e1c678899287430dbcc938ba95ad563d","finishedUtc":"2026-11-12T12:43:05.3321439Z"}`

Наблюдаемый throughput основной JVM: 78.40 terminal arms/час, включая bootstrap/preparation/drain. Линейная оценка40пар/80arms: 1.02 часа при тех же условиях, без гарантий future fair n и стабильности; смерти, SKIP и rejected evidence уменьшают число fair observations. Это оценка загрузки evaluator, не обещание статистической мощности.

Подробные individual deltas/медианы/разброс/bootstrap CI: [основной native отчёт](../tasks/PHANTOM-LEARNING-005/evidence/native/eval005d/report.md). При n<20 вывод о значимом преимуществе запрещён. Все source keys, actor epochs, личные EXP/SP, latency/performance scopes доступны в EVIDENCE.json и evidence/.

Осталось для FULL: минимум4 честных completed pairs каждого режима, реальное V3 HEAL решение B, отличное от stock, и итоговый review всех gates. Следующий игровой goal не начат. Изменения безопасно публикуются как PARTIAL; дальнейшие испытания требуют отдельного объявленного бюджета и анализа native причин, а не перебора ради победы.
