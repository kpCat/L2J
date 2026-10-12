# PLAN038 — implementation, а не новый обзор
> Исполнитель: executing-plans inline, без субагентов. Дизайн: DESIGN.md.

## Review focus
Старый target/spawn; двойное управление AutoPlay/route; законный cast после смерти цели;
смена этажа/instance; временная queue/checkpoint pause vs постоянный отказ. Все покрыты S03–S12.

### 1. Read-first и bounded baseline (0–45мин)
- [ ] Проверить exact BASE/OWN/remote, MANIFEST и protected blobs. Без новой worktree.
- [ ] Прочитать RESEARCH_DECISION/SOURCE_FACTS и фактические registry/controller аналоги.
- [ ] Проверить SHA TASK037-review.zip и извлечь только final-report/tail357/253/raw receipts.
  Исторический357 может не появиться в новом календаре; не force materialize и не менять DB.
- [ ] Построить короткий class map: кто выбирает target, кто выдаёт MOVE, кто следит за progress,
  кто закрывает action. Точный current callpath до stock AI, не аудит всего сервера.
- [ ] В native TEST сначала RED: допустимый NPC за непрямым препятствием не получает
  подхода по production entry; и/или stale terminal blocked при fresh READY. Premise
  route existence независимо подтверждается existing native search. Невалидный setup не RED.
- [ ] К60мин runtime probe либо valid native RED+готовая integration point. При отсутствии
  нужной геодаты сформулировать конкретный input blocker, не строить новый mesh.

### 2. Общая opportunity/continuation интеграция
- [ ] Создать минимальные Planner/Controller contract; новые файлы не дублируют scheduler,
  skill engine, generation или persistence. Существующие types предпочитать новым.
- [ ] Domain eligibility отделить от direct action geometry, shortlist bounded8.
- [ ] Сначала reuse stock PlayerAI range movement; добавить async corridor только для
  действительно пропущенного/застрявшего подхода. Exact action position на правильном слое.
- [ ] Тот же planner использовать в local arrival и active farm, чтобы две подсистемы
  не делали противоречащие выводы. Нет hardcode actor/zone/NPC ID в production.
- [ ] Интегрировать single writer/yield с existing TickAdmission и existing decision flow.
- [ ] После каждого связанного изменения RED→GREEN+затронутые negatives. Не ждать нового
  пользовательского задания, если следующий отказ в этом же approved callpath.

### 3. Законное продолжение
- [ ] Revalidate target/owner/goal перед MOVE/ATTACK/CAST; pending reply не действует на replacement.
- [ ] При смерти цели: native earned завершается, выбор новой/stock Sweep корректен.
- [ ] Блокировка/нет прогресса: новый участок или другая цель в bounded budget, typed outcome.
- [ ] Проверить capability seam по двум известным native действиям (melee и offensive skill).
  Не писать класс-specific rotations/party AI. SELF-buff/loot/Sweep не регрессируют.
- [ ] Три geometry cases S06–S08 + перенос того же кода на вторую factual геометрию.

### 4. Runtime freeze и финальные сцены
- [ ] До финального freeze проверить direct/obstructed/target-gone/stale reply/route busy
  на native fixture, а не выдать чистый contract helper за native integration.
- [ ] Exact candidate commit + normal push; clean Ant build из этого source, tests Jar-first.
- [ ] Две независимые natural группы6–8 по380с на одном source/Jar/config/catalog.
  До счётчиков зафиксировать выбор локали/primaries, сохранить всех enrolled.
- [ ] Снять named action transitions и реальные damage/EXP/SP/loot. Frozen scenes без debugger,
  pointclick, ручного назначения моба или synthetic-команд внутри окна.
- [ ] При current FAIL не расходовать последнюю сцену на случайную новую группу: нужен
  causal факт и fix или завершение с сохранённым blocker. Новому Jar нужна новая final pair.

### 5. Проверки и доставка
- [ ] На final Jar: current matrix93/93+lifecycle11/11+native0369/9+Q15+inventory035 и
  focused action/native/nav/continuity037. Accepted old failures не скрывать бесконечными rerun.
- [ ] FULL_SAVE всех enrolled lifetime и здоровый global drain отдельно от OS0.
  Поздняя SQL версия сама по себе не corruption и не PASS: exact receipt lineage.
- [ ] Один RESULT_HANDOFF038, короткий evidence index/GIT log/cleanup, reviewed diff.
- [ ] Own JVM stop; сохраняем worktree/DB, selected private archive SHA; exact-path commit/push.
  Base/forward conflict не force, не merge MAIN, не запуск следующего task.
