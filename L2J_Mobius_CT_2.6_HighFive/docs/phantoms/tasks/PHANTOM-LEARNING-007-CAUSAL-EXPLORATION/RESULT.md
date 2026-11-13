# RESULT007 — TRAIN exploration и learned inference

`/goal PHANTOM-LEARNING-007-CAUSAL-EXPLORATION`

**G0–G7 VERIFIED; G8 ожидает публикации/readback. Final manual gate REQUIRED.** Игровой verdict — **INCONCLUSIVE**. Новая модель остаётся DRAFT11; automatic promotion не выполнялась.

Реальное изменение тактики подтверждено при exploration OFF:6paid Solo divergences выбирают другой DAMAGE skill,1Party divergence выбирает другую законную цель того же HEAL1401. Это самостоятельные решения V3, отдельно от случайного TRAIN exploration. Основной EVAL содержит 4fair Solo+4fair Party пары, AB/BA2+2 на режим. Stock-stock controls:1fair пара на режим,0divergences.

|Факт|Результат|
|---|---|
|TRAIN|24balanced A/B blocks,48real episodes;16 поздних TRAIN slots NOT_RUN_BUDGET|
|TRAIN exploration|epsilon.25, максимум 1opportunity/episode;6paid alternatives,5admitted gradient alternatives|
|Dataset|58matched paid samples;19TRAIN gradients,8TUNING,8AUDIT|
|Native budget|5/8starts;58.835/90min;collection45.514/60min|
|Independent offline|Solo8/12TUNING/AUDIT observations,5/5different at margin;Party4/4,2/2different|
|Actual EVAL|16completed arms;6Solo+1Party paid divergences;4useful paid HEAL на learned стороне, только 1 из них neural intervention|
|Negative controls|4completed stock-stock arms;2fair pairs;0policy divergences|
|Optimizer|Adam9 817updates/172epochs → Adam11 855/191; selected checkpoint3epochs, terminal continuation19epochs|
|Controlled fault|реальный halt73 до COMMIT;model10 отсутствует после rollback;frozenrun восстановлен;duplicate/repack1062 rollback|
|Application markers|58atomic markers,19gradient;secondFreezeNONE;EVAL/control training state unchanged|

DRAFT11 SHA `7de6f5b6bd51f71a8bfce143422139e6b18da44cf81e6ea81f91728b0da206d0`. Adam SHA `f13d2f73ed7d0f3ffd38534b8ad413966b64cf7ba7c980a6ade2318da019d74d`. Target SHA `d021f8c6fe2f352d2895a406436c849f94dcfeea57ed539e2cccd241e0132d84`. Parent — verified terminal Adam9, веса `dc82041d…`; selected DRAFT9 к moments не присоединялись. Accepted1/2, старые DRAFT/checkpoints/terminal contract и historical EVAL сохранены.

Соло пример G5:1230 вместо stock1339,1397HPdamage/69MP, margin.031359166, event893182bf…, exact native receipt28323126…, NATIVE_KILL. Party пример:1401:11 на другого ally,2431.7817usefulHP/80MP, margin.037259743, event15b58305…, receiptc6cc0306…. Полные owner/actor/target epochs, legal set, модель/SHA, HP/MP write times и terminal facts находятся в EVIDENCE и архиве.

Advantage: U=.70terminalReward+.30clip(2*paidHP/targetMaxHP-paidMP/500-effectNanos/1e10,-1,1). Baseline — только TRAIN A той же physical/legal cell, без собственного matched block, минимум 3 других A. Target=clip(4*(U-baseline),-1,1), только реально paid TRAIN B action. Weight=min(4,1/(K*p)), нормализация mean только TRAIN. A не получает gradient; все actions одного episode сохраняют split. TUNING выбирает checkpoint; AUDIT — admission/report, без настройки objective. Старый terminal reward не изменён.

TUNING MSE .40014272→.10534861; AUDIT .01445076 против constant .00289108 — хуже constant. Это не доказательство тактического интеллекта. G4 проверял ranking и реальные features, G5 — independent native execution.

Медиана B−A времени:Solo−2.756s,95%bootstrap CI[−5.536,+4.308]s;Party−0.889s,CI[−10.080,+4.069]s. Обе CI включают 0, n=4/режим. RNG, stochastic state и paid-selection bias не устранены. Policy-cell baseline не означает exact decision-state matching.28held-out observations коррелированы внутри 16episodes. Причинное превосходство не доказано.

Полные death counts, loot, adena, consumables, whole-party damage/all-action MP остаются UNKNOWN. Canonical kill/EXP/SP и selected-action paid effects отделены от net end-state HP/MP, включающего regeneration. Все 64native collection episodes завершены, но 3 не дали selection,1join rejected,2 не дали eligible terminal actions; они сохранены, не повторялись и не получили target.

Архитектура: существующие Combat/Party/native dispatch owners; default explorationOFF, TRAIN explicit opt-in only. HEAL emergency/safeToRank,5ms,margin.025,cooldown2000ms сохранены. Module Java25/Ant; новые providers/движки/UI не подключались. Source scope17files+14deliverypaths, bounded exception подтверждён PLAN.

Проверки: canonical compile-tests/jar и 007focused4groups; old004model3groups,00528assertions,006R01–R07; exact SQL additive2tables,12historical-table digest unchanged. Review:Critical0/Important1,guard fix RED→GREEN,all4groupsGREEN. Native experiment использовал frozen JAR63bbf752…; validation-only source fix требует paid gradient alternative и проверен независимо до actual fit. Final canonical verification/encoding/scope фиксируются в EVIDENCE.

CPU/RAM: в collection segment2 CPU621.328s/1683.520s wall, peakWorkingSet4.377GiB;segment3 CPU258.328s/923.712s,peak4.361GiB. Это process CPU-seconds и process peak, не machine load. Optimizer compute1.412s. Полные native/worker resources и per-episode policy timing/fallback census в архиве; cold5ms overruns приводили к fallback. Throughput64real episodes/45.514native minutes≈1.41episode/min. После поздних проверок показатели всех 5starts доступны в EVIDENCE/archive.

Исходный WIP006R1 HEAD3bfdce560a24882519a3358d405bfe4b99bb25de сохранён как parent; feature/master/MAIN/PLAY/чужие SQL/worktrees не менялись. Git использован по прямому разрешению 007 и bounded root contract; точные команды и allowlist — [COMMANDS](COMMANDS.md), [SCOPE_ALLOWLIST](SCOPE_ALLOWLIST.txt). Commit/push/readback ещё требуются на этой стадии подготовки.

007 расходует задачу№1 из 13 после 006R1;осталось 12 до пересмотра. Исторические 001–006R1 —7 заданий. Новый бюджет 7–13 задач;PARTIAL и исправительные задачи также расходуют его. Проценты готовности не назначаются. Следующий Goal не начат.

Полные evidence: [EVIDENCE](EVIDENCE.json), [архив](artifacts/readfirst007.zip), [PROGRESS с rulings](PROGRESS.md), [EXPERIMENT](EXPERIMENT.md). Название архива сохранено из approved exact allowlist; он содержит read-first, native, optimizer и final validation evidence.

- mojibake-маркеры в изменённых файлах проверены;
- escaped Cyrillic в изменённых файлах проверены.


Final verification: canonical007+JAR exit0; old004/005/006 и 006R1 focused routes exit0; SQL/cold/ZIP/source scope PASS. Own TEST STOPPED generation25; native/SQL/worker/Director отсутствуют,203/207/ports preflight PASS. CRLF распознаны как EOL через временный git -c core.whitespace; blank-at-eol/blank-at-eof/space-before-tab включены, Git config не изменён.
