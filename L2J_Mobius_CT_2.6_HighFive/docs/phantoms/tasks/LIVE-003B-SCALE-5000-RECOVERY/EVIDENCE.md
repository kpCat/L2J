# EVIDENCE

Baseline for this continuation:
`03f538b5589b554347f27ea299c510690bf950b0`

Previous task result:
- LIVE-003: PARTIAL
- progression 15/15 GREEN
- locality/0D GREEN
- runtime 1280 GREEN
- runtime 5000 BLOCKED: `SCALE_CREATION_THROUGHPUT_BLOCKED`
- runtime 10000 NOT_STARTED

Previous live 0D proof:
- `worldMaterialized=7`
- `effectiveACTIVE=7`
- `localSignaled=20`
- profile 201 `ACTIVE/STABLE/WORK_DELIVERED`
- profile 201 `worldPresent=true`
- `nativeFailure=null`

Previous 5000 attempt:
- target changed only 1280 -> 5000;
- 45 minutes;
- managed/linked remained 1280/1280 throughout;
- no persisted new shell;
- unique names/accounts remained 1280/1280;
- no duplicate identities;
- no fatal/OOM/DB exhaustion;
- no three consecutive >90% heap samples;
- target remains 5000 after stop.

Authoritative previous outputs already in repo:
`docs/phantoms/tasks/LIVE-003-RUNTIME-SCALE-10000/STATE.md`
`docs/phantoms/tasks/LIVE-003-RUNTIME-SCALE-10000/LIVE-003-RUNTIME-SCALE-10000.md`
`docs/phantoms/tasks/LIVE-003-RUNTIME-SCALE-10000/EVIDENCE.md`
`docs/phantoms/tasks/LIVE-003-RUNTIME-SCALE-10000/LIVE003_SCALE_SUMMARY.tsv`

Do not spend quota replaying previous live 0D or progression proof.

## 2026-09-26 — RED → GREEN и ранний runtime gate

- Исходный HEAD `03f538b5589b554347f27ea299c510690bf950b0`, ветка `feature/phantom-world`. Несвязанные dirty/untracked файлы сохранены.
- DB-free регрессия `17-restored-inventory-readiness-starts-bounded-target-creation` восстановила два READY профиля с сохранёнными ecology rows, target=5, creation limit=2. До исправления `ant phantom-population-ecology-goal033-test` дал `16 passed, 1 failed`: `Expected <4> but was <2>`; inventory стала ready внутри первого pulse, но два допустимых shell не появились. Seed `33003300`.
- Причина: `publish()` обновлял `_inventoryReady` до true внутри обработки последней записи, а сравнение в конце `onPopulationPulse()` брало `before` уже после обработки. Переход false→true и callback `reconcilePopulation()` терялись. Менеджер сохранял fail-closed gate и не открывал target deficit.
- Исправление сохраняет readiness на входе pulse и сравнивает с итоговым состоянием; callback остаётся вне ecology monitor. Goal033 suite после исправления: `17/17`, включая exactly-once reconcile и отсутствие повтора на no-op pulse. `compile-tests`, Goal016 reconciliation/creation/lifecycle и Goal033A historical — exit 0. Две старые compiler deprecation warning остались без изменений.
- Code fix commit `3f3fa07c5f242f1e2a9423a930e68ae085e36a7b` запушен в `feature/phantom-world`; exact detached `ant jar` — exit 0. Новый private `GameServer.jar` SHA-256 `C9947AC12C181705F9804ADE62E2C740E76FBF38B687EED605CB2D244CEF953D`. Старый JAR SHA-256 `B0843527651ABF2CF12881F81ED4646382229E16191DA0350A6AD451A75F78B6` сохранён в `.phantom-local/backups/LIVE-003B-SCALE-5000-RECOVERY-20260926-175715/`. Detached build worktree удалён.
- До запуска read-only PLAY baseline: 1280 managed/linked READY, max profile ID и ordinal 1280, distinct names/accounts 1280/1280, duplicates 0, ecology initial-complete/pending 871/409. Канонический LocalPlay был STOPPED, порты закрыты; private target=5000, ActiveTarget=64, materialized cap=128, CreationInFlight=2, pulse=100 ms, boundaries=64, MaxScheduled=10000.
- Канонический `Start-LocalPlay.ps1 -Background` запустил Login PID 27700 и Game PID 28376. Первый новый persisted shell — profile 1281, `created_at=2026-09-26 18:01:55.231`; linked character 268486600 к `18:01:58.603`. В snapshot `early1` profile 1281 уже READY. Profile 1282 позже получил committed BackgroundState READY с anchor `generated.farm.f66a8a0231b2d83dac29cc4.anchor` и позицией `(24496,9206,-3584)`.
- Минутные SELECT/SHOW и safety samples лежат в `.phantom-local/logs/LIVE-003B-SCALE-5000-RECOVERY/`. На `18:12:34` — 1636 managed / 1634 linked, 1634 distinct names/accounts, duplicates 0, DB connections 15, Game threads 161, heap 81.84% от 4096 MiB, fatal markers 0. 5000 и 15-минутный soak ещё не достигнуты; runtime monitor продолжается до deadline `18:46:00+03:00` без tuning budgets.

## 2026-09-26 — финальный 5000 deadline и stop

- Monitor завершился по deadline `18:46:00+03:00` с `SCALE_CREATION_THROUGHPUT_BLOCKED`. Последний sample `18:46:14`: 2656 managed / 2654 linked, 2654 distinct names/accounts, duplicates 0; heap 85.55%, connections 15, threads 160, fatal 0. 51 sample с `18:01:54` до `18:46:14`: +1374 managed за 44.33 минуты, 30.99/min; peak heap 93.99%, максимум два подряд >90%; threads 160–163, connections 13–15 из 151, fatal samples 0. Exact owned PIDs/ports сохранялись.
- Полный PLAY snapshot после deadline: 2660 managed / 2658 linked, 2658 distinct names/accounts, duplicates 0, 2626 committed positions, population READY 2658 / INITIALIZING 2, ecology initial-complete/pending 955/1705, catchup COMPLETE/RUNNING/FAILED_REPLAN_REQUIRED 1256/643/727. Profile 1282 остаётся READY с committed anchor и позицией.
- Канонический `Stop-LocalPlay.ps1` остановил только Game PID 28376 и Login PID 27700. `Check-LocalPlay.ps1`: STOPPED, staleRecord=False, ports 2106/9014/7777 closed, private target 5000, ActiveTarget 64, cap 128, pulse 100 ms. Итоговый SELECT/SHOW после stop: 2668 managed / 2666 linked, 2666 distinct names/accounts, duplicates 0, 2632 committed positions, READY 2666 / CHARACTER_PRESENT 2. Между snapshot и stop завершились уже выполнявшиеся creation operations; профили не удалялись.
- Статус **BLOCKED — SCALE_5000_DEADLINE_CREATION_RATE_BLOCKED**: readiness edge исправлен и создание возобновилось, но 5000 managed+linked и 15-минутный soak не получены при неизменённых private budgets. В 51 sample разница managed-linked оставалась 2, согласуясь с CreationInFlight=2; точная задержка отдельных native creation stages не измерена. Никакого auto-tuning, PLAY direct DML/DDL, reset/reseed/delete, старта 10000 или LIVE-004/005 не было. Runtime topology resolution нового профиля отдельно не доказан.
