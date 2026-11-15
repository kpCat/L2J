# План исполнения PHANTOM-LEARNING-014

Статус: утверждён владельцем15.11.2026 до изменения исходников, exact scope6. Дополнительные условия подтверждения: полные phase timings, только conservative immutable deny, никакой выдачи HP/MP/skills, максимум2readiness/20min и2direct causal fixes, GS только послеG3, buff audit-only. Исполнение inline, без новых веток, checkout и subagents. Основание: TASK.md, PLAN.md и GIT_AND_SAFETY.md пакета Goal014.

Цель: измерить и устранить стоимость подготовки непригодных TRAIN-кандидатов, затем доказать полный законный cohort и выполнить разрешённую native-часть только после G3.

Архитектура: существующие Director, native adapter, acquired inventory и канонические владельцы. Java25/Ant и Windows PowerShell5.1; без зависимостей, нового scheduler, кэширования mutable admission или изменения production mechanics.

## Прочитано и найденные аналоги

- Корневой AGENTS.md, модульный Agents.md, README.md, CODEX_WORKFLOW_CONTRACT.md, TASK_PACKAGE_STANDARD.md, ограниченные релевантные разделы master plan/current status.
- Все девять обязательных документов Goal014, F3_RESULT/F3_EVIDENCE/COMMANDS013, R1_RESULT012.
- PhantomAutonomousTrainingNative: prepareSources, discoverSources, planCells, admitHeadlessCell, collectEpisode; PhantomLearning001ShadowNative.Runtime: prepareAcquired, recoverAcquired, prepareAcquiredOutcome.
- PhantomSkillInventory: acquired/capture/eligibility/category; существующие inventory/shortlist/eligibility contracts в AutonomousTrainingChecks.
- Локальные паттерны: immutable versioned receipts, System.nanoTime для elapsed, существующий coarse process resources receipt; native pre-CUT A/B parity и fresh incarnations; scoped cooperative drain и held-handle exit.
- Buff maintenance: PhantomPartyTactics.addMaintenance; дальнейший аудит backend/effect authority и существующих тестов только read-only.
- README и docs найдены. Дополнительные AGENTS в прочитанных test/tools/docs деревьях не найдены. Отдельный code-map/pattern-файл в переданном пакете не указан и не использован.

## Свежие факты G0 и baseline

- GitHub feature HEAD и SOURCE004/TEST006: 42d6b675a9cf57faa4df8a74cc52a8b4adda97fa. SOURCE004 tracked clean. TEST006 имеет прежнюю untracked .phantom-local; она сохраняется.
- MAIN: 7e997636dbac9d5e1754e00c793cd6fac858b0c6, прежняя база согласно F3. MAIN не синхронизировать и не перезаписывать.
- Canonical TEST Preflight exit0: owner, tools, свободные порты, Geo203/protected207. Canonical cold Export exit0; пять fresh SELECT:183 строки, SHA256 9A9C2D0EE9A2B8F37DA5DD6BCF625334B76BE96FE9B76E22490ED20B4DA70E7D — exact F3.
- TEST2, DRAFT14, Adam903/223 подтверждены fresh checkpoint; selected.bin и terminal-adam.bin совпали с опубликованными SHA. Apps48 входят в exact SQL equality. Automatic OFF.
- F3 upper baseline992298ms/CPU181.484375s: шесть завершённых roster→shared-source интервалов около134.6s; четыре из них закончились отказами HEAL offers или Party MP. Это измеренная граница prepareAcquired, а не доказанное распределение по её внутренним фазам. Ещё одна успешная рука заняла около62.6s; две Solo руки — менее0.1s. Full G1 пока не объявляется.

## Exact source scope

Разрешить необходимость правок только в следующих шести существующих путях относительно модуля:

1. test/java/org/l2jmobius/tests/phantoms/PhantomAutonomousTrainingNative.java — bounded phase receipts, консервативная ранняя отрицательная проверка после доказанного causal path, полный финальный native recheck.
2. test/java/org/l2jmobius/tests/phantoms/PhantomLearning001ShadowNative.java — раздельная телеметрия materialization, anchor travel, recovery и roster formation; минимальное исправление только при доказанном источнике задержки.
3. test/java/org/l2jmobius/gameserver/phantoms/learning/AutonomousTrainingChecks.java — focused contracts skill version/target/shortlist и раннего отказа, когда его контракт определён измерением.
4. tools/phantom-runtime/training/Training.ps1 — bounded014 session accounting, startup/drain timings и существующие admission integration; без второго Director.
5. tools/phantom-runtime/training/Test-Training.ps1 — 1–3 focused behavioral groups для session budget/exit/admission guard.
6. build.xml — только при необходимости exact focused DB-free target.

Изменение каждого пути не обязательно. Остальные два исходных разрешённых пути (Scenarios и AutonomousTrainingV1) не включены: прямой необходимости пока нет. Новые docs/evidence/report/ROADMAP — отдельный разрешённый task scope. Исходные task documents и история013 не переписываются.

## Порядок и проверки

- [ ] Сохранить G0 receipt: Git baseline, exact source hashes, pointer12 и historical budgets; подготовить побайтовые backups меняемых существующих файлов вне checkout с SHA256.
- [ ] Добавить coarse telemetry в существующий native путь: UTC+monotonic elapsed; phase/count/result, source/class/family/scope, A/B; никаких high-rate snapshots в Git. Сохранить private preparation-cost014.json и sanitized aggregate.
- [ ] Один baseline readiness session, общий максимум480s от старта SQL/readiness до подтверждённого выхода и cleanup. Существующий cooperative drain, резерв cleanup; никаких GS. Не запускать с непроверенным deadline/exit contract.
- [ ] Назвать measured dominant phase и denominator отказов. Если причинной информации недостаточно — остановиться с PARTIAL, без пробного изменения игровых правил.
- [ ] Зафиксировать focused behavioral RED на измеренном causal path. Применить первое минимальное исправление только в exact scope. Immutable canonical impossibility можно проверять раньше; MP/HP/cooldown/owner/Geo и физические факты повторно проверять native до CUT.
- [ ] Второе прямое исправление допускается только на подтверждённом causal path. Третий независимый blocker означает PARTIAL. Не увеличивать timeout, cap и не ослаблять3cells/2classes/Solo+Party/2legal skills.
- [ ] Повторный readiness session — не более720s за вычетом фактического перерасхода первой; общий wall лимит1200s включая startup и cleanup. Session ledger additive и immutable; исторические013 issued/result не менять. Если baseline закончился раньше, оставшийся лимит всё равно не превышает1200s суммарно.
- [ ] G3 требует новый полностью sealed cohort, уникальные обе A/B руки, exact physical parity и требуемое покрытие. Одна рука или admission-only artifact не являются достаточным PASS.
- [ ] Read-only skill audit: learned/level/enchant/classIndex, unsupported families, deterministic >4 rotation, friendly HEAL/enemy DAMAGE, target/MP/cooldown/conditions/Geo/LOS/native owner. Existing contracts не выдавать за actual native effect. Если обнаружен source defect вне allowlist — STOP и описать варианты.
- [ ] Read-only buff audit с таблицей PASS/FAIL/UNKNOWN/NOT_SUPPORTED: mage/fighter/hybrid/Party, missing/expiry/healthy, songs/dances, stacking, MP, heal priority. Production buff/party/effect code и XML не редактировать; значимую ошибку вынести на согласование.
- [ ] Только при полном G3 и приемлемом skill/buff safety выполнить один bounded collection: максимум3 GS/60native-min. При достаточных новых lawful TRAIN/TUNING/AUDIT разрешён один FIT и cold restore. Иначе FIT0 и точная причина. EVAL012 не использовать для обучения; улучшение без независимой EVAL не объявлять.
- [ ] Последовательно targeted Ant013/012/011/009/008/007/JAR и необходимые focused PS5.1 tests; DB-free buff checks использовать существующие. ant verify не запускать.
- [ ] Cold postflight: five SELECT equality до FIT, точный учёт законных новых записей после native, protected207/Geo203, MAIN/PLAY, own exits/ports/OFF; две отдельные encoding проверки.
- [ ] Публикация exact task files в SOURCE004/feature: scoped commit, обычный non-force push; независимый GitHub HEAD/parents/exact diff/full-body source+evidence readback. Manual gate014 REQUIRED; следующую задачу не начинать.

## Что ещё не проверено

Внутренняя стоимость recovery/materialization, pointer12 fresh hash, полный frozen budgets inventory, actual skill-target effects, buff recipient matrix, полный TRAIN admission и native/FIT остаются непроверенными на момент предложения. G1–G8 не объявлены пройденными. Новых readiness sessions/GS/FIT в Goal014 пока0/0/0.

Подтверждение запрашивалось по явным требованиям пользовательского AGENTS: для крупной задачи предложить план и дождаться подтверждения; TASK014 разрешает direct fixes после утверждённого плана. Подтверждение получено до исходных правок. Последующие шаги выполняются самостоятельно в утверждённых пределах.
