# Group Continuity037 — Implementation Plan

> Исполнителю: использовать executing-plans, systematic-debugging, TDD и verification.
> Без субагентов. Это согласованный новый TASK, не продолжение старого 036.

**Goal:** два current full-group continuity PASS + полный save/drain без ослабления правил.
**Architecture:** existing Phantom decision/visible control поверх stock AutoPlay/AutoUse.
**Tech:** Java25,Ant,MariaDB127.0.0.1:3308,Windows11; Python для read-only forensic index.
**Spec:** DESIGN.md; временные пределы и authority — GOAL.md/GIT.md.

## Review Focus
Неполный sample vs истинный first fault (S02); календарный выход vs SQL loss (S03);
пауза checkpoint vs навсегда отозванная policy (S05); cooperative kill после смены цели (S08);
успешный replan, после которого executor не возобновляется (S06).

### 1. Проверенные исходные данные и причинная матрица (до 30–45 мин)
- [ ] Проверить base/remote/registration/manifest и читать module AGENTS однократно.
- [ ] Проверить SHA036 архива; извлечь 4 input JSON + официальный result + relevant raw.
- [ ] Запустить tools/test_cohort_forensics037.py, затем tools/cohort_forensics037.py.
- [ ] Сверить originalEvaluation с сохранённым continuity-v2.json по faults/segments/pass.
  Обёртка лишь индекс; evaluator и inputs неизменны. Записать snapshot/output SHA.
- [ ] Создать FAULT_MATRIX037.tsv для 27/28/40/57/106/173: baseline native identity, first
  absence/dead/pending/nativeUnproven, last positive progress, first guard, expected producer.
  Неустановленное UNKNOWN, не назначать общий root всем 5. Сохранить 27 как контроль.

### 2. Один текущий causal probe, не новая длинная слепая сцена
- [ ] На new037a из проверенного prestate подготовить полную JVM/observer с pre-World setup.
- [ ] До 60 мин запустить первый короткий probe при наличии входных данных; native casts остаются
  штатными. Наблюдать до 150 с, чтобы пересечь известные 45/90/120 с окна при необходимости.
- [ ] Для 173 снять exact current.nativeFirstUnprovenReason/time и предшествующую фазу.
  Для остальных получить первый реальный guard/transition, а не только итог PHASE_DEADLINE.
- [ ] Если исходный ID сейчас законно offline, не ждать час: доказать исходную классификацию
  из 036 и воспроизвести тот же state-machine переход на owned native fixture. Final scene
  остаётся natural, без forced materialize. Новые ID не заменяют исторические строки.

### 3. Последовательные связанные исправления
- [ ] Для каждого общего producer написать причинный RED на старой реализации. Reuse fixtures
  Handoff/VisibleContinuity032/Sustained026/NativeCast036. Setup failure = INVALID.
- [ ] Внести минимальную правку из conditional SOURCE_MAP, связать с first guard и сценарием.
  Включить последующее возобновление, не только устранение исходного false.
- [ ] Проверить GREEN, negative epoch/owner, preserved resource/debt/evidence, cooperative path.
  Не запускать 93 каждый раз; focused+affected routes достаточно до freeze.
- [ ] После двух неудачных одинаковых гипотез использовать debugger/trace другое место;
  после трёх без причинного улучшения остановить эту ветвь как architectural boundary.
  Не добавлять четвёртый обход поверх неподтверждённых изменений.

### 4. Замороженная продуктовая проверка
- [ ] Записать exact changed-path ledger, проверить scope/encoding; exact-path commit+normal push.
- [ ] Собрать чистый Jar из committed own HEAD. Метаданные source/scripts/config/evaluator/observer
  и SHA файла Jar должны совпасть с runtime и classpath тестов.
- [ ] Выполнить две финальные natural сцены b/c по 360–420 с (цель 380), 6–8 ID и 2 primaries.
  All-ID enrollment до счётчиков. Между ними никаких fixes/config edits.
- [ ] Неизменённый CONTINUITY_V2: все enrolled PASS, включая native evidence и хвост 120 с.
  При FAIL причина и вся группа остаются; можно исправлять в оставшемся semantic budget,
  но новая сборка требует новой пары в пределах общего лимита 3 full scenes.
- [ ] Каждая завершённая сцена: FULL_SAVE всех lifetime и whole-global healthy drain перед
  stock pool stop. Повторная загрузка сохранённого inventory — существующий короткий regression.

### 5. Проверка, публикация, безопасное завершение
- [ ] На final committed Jar:93/93+11/11, native0369/9,Q15,inventory0356/6 и затронутые
  projection/capacity/model/SQL tests; никаких PASS от старого Jar.
- [ ] При неизменном seed flaky FAIL не перезапускать до бесконечного PASS: один изолированный
  repeat+один полный repeat допустимы, причину и исходные FAIL сохранить; unexplained semantic
  regression запрещает GREEN037.
- [ ] RESULT_HANDOFF037 + EVIDENCE_INDEX037 + CLEANUP037; source diff, matrices, all cohorts,
  решения по пяти историческим ID; архив с size/SHA, без secrets/fullDB/runtime копий.
- [ ] Own Game/Login/Synthetic graceful stop, typed completion отдельно от OS0.
  Сохранить worktree/DB. Exact normal push, local=remote; никаких confirmation-циклов.
