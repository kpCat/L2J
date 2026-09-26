# LIVE-003B-SCALE-5000-RECOVERY

## Результат

**BLOCKED — SCALE_5000_DEADLINE_CREATION_RATE_BLOCKED.** Первичный no-creation дефект доказан RED-регрессией и исправлен. Новый persisted shell появился в первые минуты после запуска, но штатная двухслотовая creation pipeline создавала около 31 managed profile в минуту. К установленному 45-минутному сроку 5000 не достигнуты; 15-минутный soak не запускался. Следующие 10000 и LIVE-004/005 не начаты.

## Причина и изменение

`PhantomPopulationManager.start()` правильно удерживал reconcile, пока ecology inventory не готова. В `PhantomPopulationEcologyService.onPopulationPulse()` `publish()` последней восстановленной записи мог установить `_inventoryReady=true` внутри обработки, прежде чем end-of-pulse код считывал `before`. Сравнение видело true→true и не вызывало `reconcilePopulation()`. Target 5000 при 1280 восстановленных профилях оставался без новых shells.

Изменены только `java/org/l2jmobius/gameserver/phantoms/population/PhantomPopulationEcologyService.java` и `test/java/org/l2jmobius/tests/phantoms/PhantomPopulationEcologyGoal033Suite.java`: readiness запоминается на входе pulse, итоговый переход false→true по-прежнему вызывает callback вне ecology monitor. Fail-closed inventory gate, architecture, schema и private budgets не менялись. Использован существующий DB-free Goal033 `MemoryStore`/manager тестовый паттерн.

Новый тест восстановил два READY профиля с сохранёнными ecology rows, target=5 и creation limit=2. До исправления Goal033 suite: 16 passed / 1 failed, `Expected <4> but was <2>` (seed 33003300). После исправления: 17/17; также проверено, что no-op pulse не повторяет callback. Существующие тесты покрыли persisted restart, archive/replacement и Goal016 creation/reconciliation/lifecycle.

## Проверки и доставка

- `ant phantom-population-ecology-goal033-test`: RED 16/17 до исправления, GREEN 17/17 после.
- `ant phantom-population-reconciliation-test phantom-population-creation-test phantom-population-lifecycle-test phantom-historical-background-goal033a-test`: exit 0; historical 13/13, creation 6/6. `compile-tests` выполнен зависимостями этих targets. Полный `ant verify` не запускался по TASK.
- Первый запуск Ant в filesystem sandbox не достиг тестов: JDK получил `AccessDeniedException` на существующем HikariCP JAR. Тот же тест в разрешённом режиме собрался и дал требуемый RED, затем GREEN.
- `git diff --check` по двум code paths и staged diff: exit 0. Code commit `3f3fa07c5f242f1e2a9423a930e68ae085e36a7b` отправлен в `origin/feature/phantom-world`.
- Чистый detached worktree на exact code SHA, один `ant jar`: exit 0. Доставлен только private `GameServer.jar`, SHA-256 `C9947AC12C181705F9804ADE62E2C740E76FBF38B687EED605CB2D244CEF953D`. Предыдущий JAR SHA-256 `B0843527651ABF2CF12881F81ED4646382229E16191DA0350A6AD451A75F78B6` сохранён в `.phantom-local/backups/LIVE-003B-SCALE-5000-RECOVERY-20260926-175715/`. Временный worktree удалён.

## PLAY 5000 gate

До старта SELECT/SHOW: 1280 managed/linked READY, max profile ID/ordinal 1280, unique names/accounts 1280/1280, duplicates 0; ecology initial-complete/pending 871/409. Канонический `Start-LocalPlay.ps1 -Background` поднял owned Login PID 27700 и Game PID 28376. Первый новый shell — profile 1281, `created_at=2026-09-26 18:01:55.231`, linked character 268486600 к `18:01:58.603`; `early1` snapshot зафиксировал READY. Profile 1282 позже имел committed BackgroundState READY с anchor `generated.farm.f66a8a0231b2d83dac29cc4d.anchor` и позицией `(24496,9206,-3584)`. Runtime topology resolution нового профиля отдельно не подтверждён.

Минутный монитор с точной проверкой PID/портов, heap, threads, DB connections, counts, identity duplicates и fatal markers выполнил 51 sample с `18:01:54` до `18:46:14`. За 44.33 минуты 1282→2656 managed: 1374 новых, средний темп 30.99/min. На последнем sample 2656 managed / 2654 linked, имён/аккаунтов 2654/2654, duplicates 0. Пик heap 93.99% от 4096 MiB, не более двух подряд samples выше 90%; threads 160–163, DB connections 13–15 из 151, fatal markers 0. Экология и background продолжали работу. При оставшемся дефиците 2344 профиля 5000 gate не достигнут; 15-минутный soak отсутствует.

После deadline снят полный PLAY snapshot, затем `Stop-LocalPlay.ps1` остановил только owned Game/Login. `Check-LocalPlay.ps1`: STOPPED, staleRecord=False, ports 2106/9014/7777 closed. Финальный SELECT/SHOW после stop: 2668 managed / 2666 linked, 2666 distinct names/accounts, duplicates 0, 2632 committed positions; два профиля находятся в обычном незавершённом creation lifecycle. Небольшой рост между deadline snapshot и stop — уже выполнявшиеся creation операции. Все профили оставлены в PLAY.

Подробные read-only snapshots и minute samples: `.phantom-local/logs/LIVE-003B-SCALE-5000-RECOVERY/`; компактный ряд — `LIVE003B_RUNTIME_5000.tsv`. PLAY direct DML/DDL, reset/reseed, profile deletion не выполнялись. Guarded TEST DB для этой DB-free регрессии не использовалась; migration нет. Private target остаётся 5000; ActiveTarget 64, materialized cap 128, MaxScheduled 10000, CreationInFlight 2, pulse 100 ms, boundaries 64 и остальные параметры не менялись.

## Ограничение и остановка

Первоначальный `SCALE_CREATION_THROUGHPUT_BLOCKED` имел доказанную причину отсутствия reconcile и устранён. Оставшийся blocker — измеренная скорость штатного bounded creation lifecycle при неизменном CreationInFlight=2: в каждом минутном sample разница managed-linked равнялась двум, а средняя скорость составила 30.99/min. Точный вклад отдельных native initialization стадий в задержку не измерялся; ускорение без tuning budgets потребовало бы отдельной доказательной задачи. LIVE-003B заканчивается здесь по требованию STOP, без 10000 и новых Goals.

Ветка: `feature/phantom-world`. Code commit/push: `3f3fa07c5f242f1e2a9423a930e68ae085e36a7b`, successful. Exact-path отчётный commit/push выполняется после проверки этих файлов; его SHA и результат — в финальном ответе.

mojibake-маркеры в изменённых файлах проверены: 0 совпадений.

escaped Cyrillic в изменённых файлах проверены: 0 совпадений.

Git-команды использовались по прямому разрешению TASK на exact-path code/report commit/push и detached clean build, а также по обязательному initial status из локального `AGENTS.md`. Команды ограничены этой проверкой и task-owned файлами; несвязанные dirty/untracked файлы не добавлялись. Точные команды (повторные вызовы той же команды не дублируются):

```text
git rev-parse HEAD
git branch --show-current
git status --short
git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}'
git diff --check -- L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/population/PhantomPopulationEcologyService.java L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomPopulationEcologyGoal033Suite.java
git diff -- L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/population/PhantomPopulationEcologyService.java L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomPopulationEcologyGoal033Suite.java
git add -- L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/population/PhantomPopulationEcologyService.java L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomPopulationEcologyGoal033Suite.java
git diff --cached --check
git diff --cached --stat
git diff --cached --name-only
git diff --cached -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/LIVE-003B-SCALE-5000-RECOVERY/EVIDENCE.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/LIVE-003B-SCALE-5000-RECOVERY/STATE.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/LIVE-003B-SCALE-5000-RECOVERY/LIVE-003B-SCALE-5000-RECOVERY.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/LIVE-003B-SCALE-5000-RECOVERY/LIVE003B_RUNTIME_5000.tsv
git commit -m "phantom(live-003b): reconcile restored ecology readiness edge"
git push origin feature/phantom-world
git rev-parse --git-dir
git rev-parse --git-common-dir
git rev-parse --show-superproject-working-tree
git check-ignore -v L2J_Mobius_CT_2.6_HighFive/.phantom-local/build-LIVE-003B
git worktree add --detach L2J_Mobius_CT_2.6_HighFive/.phantom-local/build-LIVE-003B 3f3fa07c5f242f1e2a9423a930e68ae085e36a7b
git worktree list --porcelain
git worktree remove --force L2J_Mobius_CT_2.6_HighFive/.phantom-local/build-LIVE-003B
git status --short -- L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/population/PhantomPopulationEcologyService.java L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomPopulationEcologyGoal033Suite.java L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/LIVE-003B-SCALE-5000-RECOVERY
git status --short -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/LIVE-003B-SCALE-5000-RECOVERY
git add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/LIVE-003B-SCALE-5000-RECOVERY/EVIDENCE.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/LIVE-003B-SCALE-5000-RECOVERY/STATE.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/LIVE-003B-SCALE-5000-RECOVERY/LIVE-003B-SCALE-5000-RECOVERY.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/LIVE-003B-SCALE-5000-RECOVERY/LIVE003B_RUNTIME_5000.tsv
git commit -m "phantom(live-003b): record blocked scale gate"
git push origin feature/phantom-world
```
